package io.puretv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Backs up and restores the choices made on the TV: layout, shelves, shortcuts, sources, city, theme, clock and remote
 * buttons. Never viewing history, hidden-content keys, API/ADB keys or the HOME/Original state. Values are checked
 * against an allowlist on the way in, so an edited or foreign file can only set ordinary preferences.
 * The file sits in the app's own storage (no permission). With the TV's ADB already allowed (custom home mode), a
 * copy also goes to Download, which survives uninstalling the app and can be copied to a computer.
 */
final class SettingsBackup {
    private SettingsBackup() {}

    static final String FORMAT = "puretv-settings", NAME = "PureTV-settings.json";
    static final String SHARED = "/sdcard/Download/" + NAME;
    static final int VERSION = 1, MAX_BYTES = 256 * 1024;

    // MARK: - Files (Android)

    static File local(Context c) {
        File dir = c.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, NAME);
    }

    /** Writes the backup; returns true when the shared Download copy was also written. */
    static boolean save(Context c) throws IOException {
        File file = local(c);
        if (file == null) throw new IOException("storage unavailable");
        JSONObject root = new JSONObject();
        try {
            root.put("format", FORMAT).put("version", VERSION).put("saved", System.currentTimeMillis());
            JSONObject values = new JSONObject();
            for (Map.Entry<String, Object> e : BackupRules.clean(Prefs.backup()).entrySet()) {
                values.put(e.getKey(), e.getValue() instanceof Set ? new JSONArray((Set<?>) e.getValue()) : e.getValue());
            }
            root.put("settings", values);
            if (RemoteKeys.supported()) {
                JSONObject keys = new JSONObject();
                for (int code : Prefs.KEYS) keys.put(Integer.toString(code), Prefs.keyApp(code));
                root.put("keys", keys);
            }
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(root.toString(1).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
        if (!tmp.renameTo(file)) throw new IOException("could not replace the backup");
        return share(c, file.getPath(), SHARED, false);
    }

    /** A checked backup, ready to apply on the main thread (Prefs.restore). */
    static final class Loaded {
        final Map<String, Object> values;
        final Map<Integer, String> keys;
        final long saved;
        Loaded(Map<String, Object> values, Map<Integer, String> keys, long saved) {
            this.values = values; this.keys = keys; this.saved = saved;
        }
    }

    /** Reads the newest backup (a newer Download copy replaces the app's own first). Background thread. */
    static Loaded load(Context c) throws IOException {
        String text = null;
        File file = local(c);
        if (file != null && share(c, SHARED, file.getPath(), true)) AppLog.w("settings backup: using the Download copy");
        if (file != null && file.isFile() && file.length() <= MAX_BYTES) {
            try (FileInputStream in = new FileInputStream(file)) {
                text = new String(BoundedInput.read(in, MAX_BYTES), StandardCharsets.UTF_8);
            }
        }
        if (text == null) throw new java.io.FileNotFoundException();
        try {
            JSONObject root = new JSONObject(text);
            if (!FORMAT.equals(root.optString("format")) || root.optInt("version") != VERSION) throw new IOException("not a PureTV backup");
            JSONObject values = root.getJSONObject("settings");
            Map<String, Object> raw = new HashMap<>();
            for (java.util.Iterator<String> it = values.keys(); it.hasNext(); ) {
                String key = it.next();
                Object v = values.get(key);
                if (v instanceof JSONArray) {
                    Set<String> set = new LinkedHashSet<>();
                    for (int i = 0; i < ((JSONArray) v).length(); i++) set.add(((JSONArray) v).optString(i));
                    v = set;
                }
                raw.put(key, v);
            }
            Map<String, Object> keysRaw = new HashMap<>();
            JSONObject keys = root.optJSONObject("keys");
            if (keys != null) for (java.util.Iterator<String> it = keys.keys(); it.hasNext(); ) {
                String key = it.next();
                keysRaw.put(key, keys.optString(key));
            }
            return new Loaded(BackupRules.clean(raw), RemoteKeys.supported() ? BackupRules.cleanKeys(keysRaw, Prefs.KEYS) : new HashMap<>(),
                    root.optLong("saved"));
        } catch (org.json.JSONException e) {
            throw new IOException("not a PureTV backup");
        }
    }

    /** Time of the app's own backup, or 0. */
    static long savedAt(Context c) {
        File file = local(c);
        return file != null && file.isFile() ? file.lastModified() : 0;
    }

    private static final Pattern PATH = Pattern.compile("/[\\w/.\\-]+");

    /**
     * Copies between the app's storage and Download with the TV's shell. Only in custom home mode, where the TV already
     * allows our ADB key, and never with a new prompt; fixed commands, paths checked first. `newer`: only a newer file.
     */
    static boolean share(Context c, String from, String to, boolean newer) {
        if (!PATH.matcher(from).matches() || !PATH.matcher(to).matches()) return false;
        if (!HomeMode.custom(c)) return false;
        String copy = "cp " + from + " " + to + " && echo copied";
        String command = newer ? "[ -f " + from + " ] && { [ ! -f " + to + " ] || [ " + from + " -nt " + to + " ]; } && " + copy
                : "mkdir -p /sdcard/Download && " + copy;
        try (Adb adb = Adb.connect(c, -1, null)) {
            return adb.shell(command).contains("copied");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
