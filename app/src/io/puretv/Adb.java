package io.puretv;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * ADB client that talks to the TV itself (127.0.0.1:5555, open while debugging is on). It's what lets the app switch
 * the home screen and the screensaver, and turn off apps, without a computer. The first time, the TV asks
 * "Allow USB debugging?" and you accept with "Always allow". Only HomeMode uses it, and only with its own fixed
 * commands (nothing from outside); the settings backup also copies its own file to Download with an existing grant.
 * Protocol: https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/protocol.txt
 */
final class Adb implements Closeable {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257, STLS = 0x534c5453;
    private static final int VERSION = 0x01000001, MAX_DATA = 256 * 1024;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    /** Start of the PKCS#1 block of a SHA-1 signature (adbd treats the token as if it were the digest). */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    /** Notified when the TV is showing the permission prompt (so the screen can explain what to do). */
    interface Approval { void waiting(); }

    private static final class Message {
        int command, arg0, arg1;
        byte[] data;
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextLocalId = 1;
    private int maxPayload = 4096;

    private Adb(Socket socket) throws IOException {
        this.socket = socket;
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
    }

    /**
     * Connects and authenticates. If the TV doesn't know our key yet, waits up to `approvalMs` for you to accept; a
     * negative `approvalMs` never offers the key, so the TV shows no prompt (callers that only use an existing grant).
     */
    static Adb connect(Context c, int approvalMs, Approval approval) throws IOException {
        // Without a key there is no grant to use, and a key made here (another process) could replace the one the main
        // process is just offering to the TV.
        if (approvalMs < 0 && !new File(c.getFilesDir(), "adbkey").exists()) throw new IOException("the app's key isn't allowed on the TV");
        KeyPair keys = keys(c);
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
        } catch (IOException e) {
            s.close();
            throw new IOException("debugging is off on the TV (port 5555 closed)");
        }
        Adb adb;
        try {
            s.setSoTimeout(10_000);
            adb = new Adb(s);
        } catch (IOException | RuntimeException e) {
            s.close();
            throw e;
        }
        try {
            adb.send(CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.US_ASCII));
            boolean signed = false, offeredKey = false;
            while (true) {
                Message m;
                try {
                    m = adb.read();
                } catch (SocketTimeoutException e) {
                    throw new IOException(offeredKey ? "debugging permission wasn't accepted on the TV" : "the TV's ADB didn't respond");
                }
                if (m.command == CNXN) {
                    s.setSoTimeout(20_000);
                    // The TV's largest message (arg1); older adbd took 4 KB, anything odd falls back to that.
                    adb.maxPayload = m.arg1 >= 1024 ? Math.min(MAX_DATA, m.arg1) : 4096;
                    return adb;
                }
                if (m.command == STLS) throw new IOException("the TV asked for TLS (wireless debugging); use USB debugging");
                if (m.command != AUTH || m.arg0 != AUTH_TOKEN) throw new IOException("unexpected reply from ADB");
                if (!signed) {
                    adb.send(AUTH, AUTH_SIGNATURE, 0, sign(keys.getPrivate(), m.data));
                    signed = true;
                } else if (!offeredKey) {
                    if (approvalMs < 0) throw new IOException("the app's key isn't allowed on the TV");
                    // Unknown key: send the public one; the TV shows "Allow USB debugging?".
                    adb.send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBlob(keys.getPublic()));
                    offeredKey = true;
                    if (approval != null) approval.waiting();
                    s.setSoTimeout(approvalMs);
                } else {
                    throw new IOException("the TV rejected the app's key");
                }
            }
        } catch (IOException | RuntimeException e) {
            adb.close();
            throw e;
        }
    }

    /**
     * A command that may run longer than the usual 20 s without printing (compiling a large app took over 20 s on the
     * Mi Stick and timed out the rest of the run): waits up to `timeoutMs` for it, then restores the usual timeout.
     */
    String shell(String command, int timeoutMs) throws IOException {
        int usual = socket.getSoTimeout();
        socket.setSoTimeout(timeoutMs);
        try {
            return shell(command);
        } finally {
            socket.setSoTimeout(usual);
        }
    }

    /** Runs a command in the TV's shell and returns its output (stdout and stderr together). */
    String shell(String command) throws IOException {
        int local = nextLocalId++;
        send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        int remote = 0;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while (true) {
            Message m = read();
            if (m.arg1 != local) continue;
            if (m.command == OKAY) {
                remote = m.arg0;
            } else if (m.command == WRTE) {
                result.write(m.data);
                send(OKAY, local, m.arg0, null);
            } else if (m.command == CLSE) {
                if (remote == 0 && m.arg0 == 0) throw new IOException("ADB refused the command");
                send(CLSE, local, m.arg0, null);
                return result.toString("UTF-8");
            }
        }
    }

    /**
     * Copies a local file to the TV (the sync service's SEND, as `adb push` does), so `pm install` can read it. Only
     * AppUpdate uses it, for a checked APK, to a fixed path under /data/local/tmp.
     */
    void push(File file, String remote) throws IOException {
        int local = nextLocalId++;
        send(OPEN, local, 0, "sync:\0".getBytes(StandardCharsets.US_ASCII));
        int remoteId = 0;
        while (remoteId == 0) {
            Message m = read();
            if (m.arg1 != local) continue;
            if (m.command == OKAY) remoteId = m.arg0;
            else if (m.command == CLSE) throw new IOException("ADB refused the copy");
        }
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            byte[] spec = (remote + ",33188").getBytes(StandardCharsets.UTF_8);   // a regular file, mode 0644
            syncHeader(stream, "SEND", spec.length);
            stream.write(spec);
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];   // the sync service's largest DATA chunk
                int n;
                while ((n = in.read(buffer)) > 0) {
                    syncHeader(stream, "DATA", n);
                    stream.write(buffer, 0, n);
                    if (stream.size() >= maxPayload) stream = flushSync(local, remoteId, stream);
                }
            }
            syncHeader(stream, "DONE", (int) (System.currentTimeMillis() / 1000));
            flushSync(local, remoteId, stream);
            // The answer: "OKAY", or "FAIL" with a message.
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            while (reply.size() < 8) {
                Message m = read();
                if (m.arg1 != local) continue;
                if (m.command == WRTE) { reply.write(m.data); send(OKAY, local, remoteId, null); }
                else if (m.command == CLSE) break;
            }
            byte[] r = reply.toByteArray();
            String id = r.length >= 4 ? new String(r, 0, 4, StandardCharsets.US_ASCII) : "";
            if (!id.equals("OKAY")) {
                String detail = r.length > 8 ? new String(r, 8, r.length - 8, StandardCharsets.UTF_8) : "";
                throw new IOException("copy to the TV failed" + (detail.isEmpty() ? "" : ": " + detail.trim()));
            }
        } finally {
            send(CLSE, local, remoteId, null);
        }
    }

    private static void syncHeader(ByteArrayOutputStream out, String id, int value) {
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        b.put(id.getBytes(StandardCharsets.US_ASCII)).putInt(value);
        out.write(b.array(), 0, 8);
    }

    /** Sends the buffered stream in messages the TV accepts, each after the previous one is acknowledged. */
    private ByteArrayOutputStream flushSync(int local, int remoteId, ByteArrayOutputStream stream) throws IOException {
        byte[] all = stream.toByteArray();
        for (int at = 0; at < all.length; at += maxPayload) {
            int n = Math.min(maxPayload, all.length - at);
            byte[] chunk = new byte[n];
            System.arraycopy(all, at, chunk, 0, n);
            send(WRTE, local, remoteId, chunk);
            while (true) {
                Message m = read();
                if (m.arg1 != local) continue;
                if (m.command == OKAY) break;
                if (m.command == CLSE) throw new IOException("the TV closed the copy");
            }
        }
        return new ByteArrayOutputStream();
    }

    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    private Message read() throws IOException {
        byte[] header = new byte[24];
        in.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int length = b.getInt();
        b.getInt(); // checksum (newer adbd doesn't check it)
        int magic = b.getInt();
        if ((m.command ^ 0xffffffff) != magic) throw new IOException("invalid ADB message");
        if (length < 0 || length > 1024 * 1024) throw new IOException("ADB message too large");
        m.data = new byte[length];
        in.readFully(m.data);
        return m;
    }

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int length = data == null ? 0 : data.length;
        int sum = 0;
        if (data != null) for (byte x : data) sum += x & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24 + length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(length).putInt(sum).putInt(command ^ 0xffffffff);
        if (data != null) b.put(data);
        out.write(b.array());
        out.flush();
    }

    // MARK: - The app's key (kept in private storage; never goes to the log)

    private static synchronized KeyPair keys(Context c) throws IOException {
        File dir = c.getFilesDir();
        File privateFile = new File(dir, "adbkey"), publicFile = new File(dir, "adbkey.pub");
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (privateFile.exists() && publicFile.exists()) {
                try {
                    return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(readFile(publicFile))),
                            factory.generatePrivate(new PKCS8EncodedKeySpec(readFile(privateFile))));
                } catch (GeneralSecurityException | IOException e) {
                    // Corrupted file (the power went out mid-write): new key; the TV asks again.
                    AppLog.w("ADB key unreadable, generating a new one");
                }
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            writeFile(privateFile, pair.getPrivate().getEncoded());
            writeFile(publicFile, pair.getPublic().getEncoded());
            return pair;
        } catch (GeneralSecurityException e) {
            throw new IOException("ADB key: " + e.getMessage());
        }
    }

    private static byte[] sign(PrivateKey key, byte[] token) throws IOException {
        try {
            byte[] block = new byte[256];
            block[1] = 1;
            int separator = block.length - SHA1_PREFIX.length - token.length - 1;
            for (int i = 2; i < separator; i++) block[i] = (byte) 0xff;
            System.arraycopy(SHA1_PREFIX, 0, block, separator + 1, SHA1_PREFIX.length);
            System.arraycopy(token, 0, block, separator + 1 + SHA1_PREFIX.length, token.length);
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(block);
        } catch (GeneralSecurityException e) {
            throw new IOException("ADB signature: " + e.getMessage());
        }
    }

    /** Public key in adbd's format (RSAPublicKey struct in base64, plus a name). */
    private static byte[] publicKeyBlob(PublicKey key) {
        RSAPublicKey rsa = (RSAPublicKey) key;
        BigInteger n = rsa.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32));
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);
        b.putInt(n0inv.intValue());
        b.put(littleEndian(n, 256));
        b.put(littleEndian(rr, 256));
        b.putInt(rsa.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " puretv@tv\0";
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] littleEndian(BigInteger value, int size) {
        byte[] big = value.toByteArray();
        byte[] little = new byte[size];
        for (int i = 0; i < size && i < big.length; i++) little[i] = big[big.length - 1 - i];
        return little;
    }

    private static byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    /** Writes all or nothing: to a file alongside, synced to disk, and only then renamed to the real name. */
    private static void writeFile(File f, byte[] data) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
        }
        if (!tmp.renameTo(f)) throw new IOException("could not save " + f.getName());
    }
}
