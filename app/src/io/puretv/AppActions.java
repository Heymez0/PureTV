package io.puretv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

/**
 * Uninstall from an app's long-press menu: only offered for apps the person installed, and always through Android's
 * own confirmation.
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
}
