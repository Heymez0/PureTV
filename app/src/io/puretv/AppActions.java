package io.puretv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

/**
 * Uninstall and Force stop from an app's long-press menu. Uninstall is only offered for apps the person installed and
 * always goes through Android's own confirmation. Force stop uses the TV's ADB grant (custom home mode), never with a
 * new prompt, and never for the packages HomeMode protects (picture, sound, inputs, remote, Android itself).
 */
final class AppActions {
    private AppActions() {}

    /** Installed by the person (not part of the TV's system image), and not PureTV itself. */
    static boolean canUninstall(Context c, String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName())) return false;
        try {
            ApplicationInfo info = c.getPackageManager().getApplicationInfo(pkg, 0);
            return (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    static boolean uninstall(Activity a, String pkg) {
        try {
            a.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** Custom home mode (the TV already allows this app's ADB key) and a package that may be stopped. */
    static boolean canForceStop(Context c, String pkg) {
        return pkg != null && !pkg.equals(c.getPackageName()) && HomeMode.mayStop(pkg)
                && HomeMode.custom(c);
    }

    /** Background thread. True when Android stopped the app. */
    static boolean forceStop(Context c, String pkg) {
        if (!WatchNextRules.packageName(pkg) || !canForceStop(c, pkg)) return false;
        try (Adb adb = Adb.connect(c, -1, null)) {
            adb.shell("am force-stop " + pkg);
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            AppLog.w("force stop: " + e.getMessage());
            return false;
        }
    }
}
