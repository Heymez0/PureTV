package io.puretv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates from PureTV's own GitHub releases. Once a day (or on request) it reads the latest release, downloads PureTV
 * (and the AirPlay receiver, when installed) and keeps a file only when it is the expected package, signed like this
 * launcher and strictly newer than what is installed: never a downgrade, never another app. Then, by the choice in
 * Settings → System and about → Updates:
 * <ul>
 *   <li>Notify (default): a notice on the home screen; installing waits for you.</li>
 *   <li>Install automatically: in custom home mode (the TV already allows this app's ADB key) it installs while the TV
 *       is idle, with the TV's own `pm install`; otherwise it notifies.</li>
 *   <li>Off: nothing is checked.</li>
 * </ul>
 * Nothing else is sent: one unauthenticated request to GitHub's API and the release downloads (InstallRules).
 */
final class AppUpdate {
    private AppUpdate() {}

    /** One app that can be updated: what is installed and the checked newer file. */
    static final class Found {
        final String pkg, label, version;
        final long code;
        final File apk;
        Found(String pkg, String label, String version, long code, File apk) {
            this.pkg = pkg; this.label = label; this.version = version; this.code = code; this.apk = apk;
        }
    }

    static final class Result {
        String tag = "";
        final List<Found> newer = new ArrayList<>();
        /** Newer builds signed with another key: Android would refuse them over this install ("PureTV 3"). */
        final List<String> blocked = new ArrayList<>();
    }

    static String blockedText(Result r) {
        return Ui.trf("{list} is signed with a different key, so it can't be installed over this PureTV.",
                "list", String.join(", ", r.blocked));
    }

    private static final String REMOTE = "/data/local/tmp/puretv-update.apk";

    // MARK: - Check (background thread)

    /** The latest release and every installed PureTV app it has a newer, checked build of. */
    static Result check(Context c, AirPlayInstall.Progress progress) throws IOException {
        return check(c, latest(), progress);
    }

    static Result check(Context c, JSONObject release, AirPlayInstall.Progress progress) throws IOException {
        Result result = new Result();
        String tag = release.optString("tag_name");
        if (!InstallRules.tag(tag)) throw new IOException("unexpected release");
        result.tag = tag;
        String launcherUrl = null, receiverUrl = null;
        JSONArray assets = release.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String url = a.optString("browser_download_url");
            if (InstallRules.asset(url, InstallRules.LAUNCHER_ASSET) != null) launcherUrl = url;
            if (InstallRules.asset(url, InstallRules.RECEIVER_ASSET) != null) receiverUrl = url;
        }
        // The receiver first: the launcher's own update ends this app's processes.
        if (receiverUrl != null && installedCode(c, AirPlayReceiver.THEMED) >= 0) {
            Found f = fetch(c, result, receiverUrl, InstallRules.RECEIVER_ASSET, AirPlayReceiver.THEMED, Ui.tr("AirPlay receiver"), progress);
            if (f != null) result.newer.add(f);
        }
        if (launcherUrl != null) {
            Found f = fetch(c, result, launcherUrl, InstallRules.LAUNCHER_ASSET, c.getPackageName(), "PureTV", progress);
            if (f != null) result.newer.add(f);
        }
        return result;
    }

    /**
     * The checked newer build, or null. A build signed with another key isn't an error for the whole check (the other
     * app is still looked at): it is listed in `blocked` when newer, and skipped when not.
     */
    private static Found fetch(Context c, Result result, String url, String name, String pkg, String label,
            AirPlayInstall.Progress progress) throws IOException {
        File apk = AirPlayInstall.download(c, "updates", url, name, progress);
        PackageInfo info;
        try {
            info = AirPlayInstall.verify(c, apk, pkg);
        } catch (AirPlayInstall.SignedDifferently e) {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
            if (InstallRules.newer(e.info.getLongVersionCode(), installedCode(c, pkg)))
                result.blocked.add(label + (e.info.versionName == null ? "" : " " + e.info.versionName));
            return null;
        }
        long code = info.getLongVersionCode();
        if (!InstallRules.newer(code, installedCode(c, pkg))) {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
            return null;
        }
        return new Found(pkg, label, info.versionName == null ? "" : info.versionName, code, apk);
    }

    static long installedCode(Context c, String pkg) {
        try {
            return c.getPackageManager().getPackageInfo(pkg, 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return -1;
        }
    }

    /** GitHub's description of the latest release: this exact address, bounded, no redirect. */
    static JSONObject latest() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(InstallRules.LATEST_API).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "PureTV");
            int code = connection.getResponseCode();
            if (code == 404) throw new IOException(Ui.tr("No release is published yet."));
            if (code != 200) throw new IOException("GitHub HTTP " + code);
            try (InputStream in = connection.getInputStream()) {
                return new JSONObject(new String(BoundedInput.read(in, 512 * 1024), StandardCharsets.UTF_8));
            } catch (org.json.JSONException e) {
                throw new IOException("unexpected release");
            }
        } finally {
            connection.disconnect();
        }
    }

    // MARK: - Install

    /** Custom home mode: the TV already allows this app's ADB key, so it can install without a prompt. */
    static boolean canInstallQuietly(Context c) {
        return HomeMode.custom(c);
    }

    /**
     * Installs with the TV's shell (custom home mode, existing grant, never a new prompt). True when Android reported
     * success. Updating PureTV itself ends this process right after; the file is removed on the next check.
     */
    static boolean installQuietly(Context c, Found f) {
        try (Adb adb = Adb.connect(c, -1, null)) {
            adb.shell("rm -f " + REMOTE);
            adb.push(f.apk, REMOTE);
            String out = adb.shell("pm install -r " + REMOTE);
            adb.shell("rm -f " + REMOTE);
            if (!out.contains("Success")) AppLog.w("update " + f.pkg + ": " + out.trim());
            return out.contains("Success");
        } catch (IOException | RuntimeException e) {
            AppLog.w("update " + f.pkg + ": " + e.getMessage());
            return false;
        }
    }

    static void clean(Context c) {
        File[] files = new File(c.getCacheDir(), "updates").listFiles();
        if (files != null) for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    // MARK: - Daily job (main process, from ModeJob)

    static String label(Found f) {
        return f.label + " " + f.version;
    }

    static void runJob(Context c) {
        String mode = Prefs.updateMode();
        if (InstallRules.OFF.equals(mode)) return;
        SharedPreferences seen = c.getSharedPreferences("updates", Context.MODE_PRIVATE);
        boolean quiet = InstallRules.AUTO.equals(mode) && canInstallQuietly(c);
        Result result;
        try {
            JSONObject release = latest();
            // A release already handled (notified, installed, or not newer) isn't downloaded again.
            if (release.optString("tag_name").equals(seen.getString("tag", ""))) return;
            if (quiet) try (Adb adb = Adb.connect(c, -1, null)) { adb.shell("rm -f " + REMOTE); } catch (IOException ignored) { }
            result = check(c, release, null);
        } catch (IOException | RuntimeException e) {
            AppLog.w("update check: " + e.getMessage());
            clean(c);
            return;
        }
        List<String> failed = new ArrayList<>(), done = new ArrayList<>(), names = new ArrayList<>();
        for (Found f : result.newer) names.add(label(f));
        if (!result.newer.isEmpty() && quiet) {
            for (Found f : result.newer) {
                boolean self = f.pkg.equals(c.getPackageName());
                // PureTV's own update ends this process: the new version writes the notice (afterUpdate).
                if (self) seen.edit().putLong("pendingCode", f.code).putString("pendingLabel", label(f)).commit();
                if (installQuietly(c, f)) done.add(label(f));
                else { failed.add(label(f)); if (self) seen.edit().remove("pendingCode").remove("pendingLabel").commit(); }
            }
            if (!done.isEmpty()) HomeMode.notice(c, Ui.trf("Updated: {list}.", "list", String.join(", ", done)));
            if (!failed.isEmpty()) HomeMode.notice(c, Ui.trf("Couldn't install the update: {list}. Settings → System and about → Updates.",
                    "list", String.join(", ", failed)));
        } else if (!result.newer.isEmpty()) {
            HomeMode.notice(c, Ui.trf("Update available: {list}. Install it in Settings → System and about → Updates.",
                    "list", String.join(", ", names)));
        }
        // Said once per release: another key never changes for that file, so it isn't downloaded again tomorrow.
        if (!result.blocked.isEmpty()) HomeMode.notice(c, blockedText(result));
        // A failed install is tried again tomorrow; anything else is done for this release.
        if (failed.isEmpty()) seen.edit().putString("tag", result.tag).apply();
        clean(c);
    }

    /** After this app was updated (MY_PACKAGE_REPLACED, the new version): the notice the old process couldn't write. */
    static void afterUpdate(Context c) {
        SharedPreferences seen = c.getSharedPreferences("updates", Context.MODE_PRIVATE);
        long pending = seen.getLong("pendingCode", 0);
        if (pending <= 0) return;
        if (installedCode(c, c.getPackageName()) >= pending) {
            HomeMode.notice(c, Ui.trf("Updated: {list}.", "list", seen.getString("pendingLabel", "PureTV")));
        }
        seen.edit().remove("pendingCode").remove("pendingLabel").apply();
    }
}
