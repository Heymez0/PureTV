package io.puretv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Installs the optional AirPlay receiver for you: downloads it from PureTV's own GitHub release, checks that it is
 * io.puretv.airplay signed with the same certificate as this launcher (the one its quick control needs), then hands it
 * to Android's installer, which asks you to confirm. Only when you choose it; nothing runs in the background.
 * The receiver stays a separate GPL-3.0 app with its complete source in the same release.
 */
final class AirPlayInstall {
    private AirPlayInstall() {}

    static final String STATUS = "io.puretv.AIRPLAY_INSTALL_STATUS";
    /** Signature permission: only this app (its installer PendingIntent) can deliver an install result to it. */
    static final String STATUS_PERMISSION = "io.puretv.permission.INSTALL_STATUS";
    private static final long MAX_BYTES = InstallRules.MAX_BYTES;

    /**
     * Listens for the installer's result. Before Android 13 a runtime receiver is open to every app, so it also requires
     * the signature permission: another app can't fake a result (and with it, an activity for PureTV to start).
     */
    static void listen(android.app.Activity a, android.content.BroadcastReceiver receiver, String action) {
        android.content.IntentFilter filter = new android.content.IntentFilter(action);
        if (android.os.Build.VERSION.SDK_INT >= 33) a.registerReceiver(receiver, filter, STATUS_PERMISSION, null, Context.RECEIVER_NOT_EXPORTED);
        else a.registerReceiver(receiver, filter, STATUS_PERMISSION, null);
    }

    /** The installer's "confirm" screen, and only that: an activity of a system app, never one of PureTV's own. */
    static boolean systemConfirmation(Context c, Intent confirm) {
        if (confirm == null || (confirm.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) != 0) return false;
        android.content.pm.ResolveInfo r = c.getPackageManager().resolveActivity(confirm, 0);
        if (r == null || r.activityInfo == null || c.getPackageName().equals(r.activityInfo.packageName)) return false;
        return (r.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    interface Progress { void update(long done, long total); }

    /** Downloads into the app's cache (bounded size, release hosts only: InstallRules). Background thread. */
    static File download(Context c, Progress progress) throws IOException {
        return download(c, "airplay", InstallRules.URL, InstallRules.RECEIVER_ASSET, progress);
    }

    /** The same download for any release asset (updates use it too): cache/<folder>/<name>. */
    static File download(Context c, String folder, String url, String name, Progress progress) throws IOException {
        File dir = new File(c.getCacheDir(), folder);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no space for the download");
        File apk = new File(dir, name), part = new File(dir, name + ".part");
        String next = url;
        HttpURLConnection connection = null;
        try {
            for (int redirect = 0; ; redirect++) {
                if (next == null || !InstallRules.allowed(next)) throw new IOException("unexpected download address");
                connection = (HttpURLConnection) new URL(next).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(20_000);
                connection.setRequestProperty("User-Agent", "PureTV");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400 && redirect < InstallRules.MAX_REDIRECTS) {
                    next = InstallRules.follow(next, connection.getHeaderField("Location"));
                    connection.disconnect();
                    connection = null;
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                long total = connection.getContentLengthLong();
                if (total > MAX_BYTES) throw new IOException("the download is too large");
                long done = 0;
                byte[] buffer = new byte[64 * 1024];
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        done += n;
                        if (done > MAX_BYTES) throw new IOException("the download is too large");
                        out.write(buffer, 0, n);
                        if (progress != null) progress.update(done, total);
                    }
                    out.getFD().sync();
                }
                if (total > 0 && done != total) throw new IOException("the download was interrupted");
                if (!part.renameTo(apk)) throw new IOException("could not save the download");
                return apk;
            }
        } finally {
            if (connection != null) connection.disconnect();
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        }
    }

    /** The downloaded receiver's version, after checking its package and certificate against this launcher. */
    @SuppressWarnings("deprecation")
    static String verify(Context c, File apk) throws IOException {
        PackageInfo info = verify(c, apk, AirPlayReceiver.THEMED);
        return info.versionName == null ? "" : info.versionName;
    }

    /** The right package, signed with another key: a lasting property of that file, not a network fault. */
    static final class SignedDifferently extends IOException {
        final PackageInfo info;
        SignedDifferently(String message, PackageInfo info) { super(message); this.info = info; }
    }

    /** The downloaded APK's package info, after checking its package name and that it is signed like this launcher. */
    @SuppressWarnings("deprecation")
    static PackageInfo verify(Context c, File apk, String pkg) throws IOException {
        PackageManager pm = c.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        if (info == null || !pkg.equals(info.packageName)) throw new IOException(Ui.tr("The download isn't the expected PureTV app."));
        try {
            Signature[] ours = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            if (info.signatures == null || ours == null || !sameSigners(ours, info.signatures)) throw new SecurityException();
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            throw new SignedDifferently(AirPlayReceiver.THEMED.equals(pkg)
                    ? Ui.tr("This copy of PureTV and the receiver are signed differently, so PureTV couldn't control it. Install both from the same release.")
                    : Ui.tr("The update is signed differently from this PureTV, so Android wouldn't accept it."), info);
        }
        return info;
    }

    static boolean sameSigners(Signature[] a, Signature[] b) {
        return new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b)));
    }

    /** Hands the checked file to Android's installer; the result arrives as a STATUS broadcast to this app. */
    static void install(Context c, File apk) throws IOException {
        install(c, apk, AirPlayReceiver.THEMED, STATUS);
    }

    /** `status`: the broadcast this app receives with the result (each screen has its own). */
    static void install(Context c, File apk, String pkg, String status) throws IOException {
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (OutputStream out = session.openWrite("PureTV-AirPlay.apk", 0, apk.length()); InputStream in = new FileInputStream(apk)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                session.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            Intent result = new Intent(status).setPackage(c.getPackageName());
            session.commit(PendingIntent.getBroadcast(c, id, result, flags).getIntentSender());
        } catch (IOException | RuntimeException e) {
            installer.abandonSession(id);
            throw e;
        }
    }

    /** Removes the downloaded file (after the installer has its own copy, or on failure). */
    static void clean(Context c) {
        File[] files = new File(c.getCacheDir(), "airplay").listFiles();
        if (files != null) for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
