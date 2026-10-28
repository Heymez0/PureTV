package io.puretv;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;

/**
 * Closes the apps that only wait in the background, on request (quick settings, Device and performance). Android
 * starts them again when they are opened. Never the app on screen, a foreground service (the AirPlay receiver while it
 * runs) or this home screen: Android only closes background processes.
 * With the TV's ADB already allowed (custom home mode) the shell closes them all; otherwise Android's own call for each
 * app this launcher can see. Background thread.
 */
final class FreeMemory {
    private FreeMemory() {}

    /** Bytes of memory available afterwards minus before (can be 0 or less: other apps start too). */
    static long run(Context c) {
        long before = available(c);
        boolean shell = false;
        if (HomeMode.custom(c)) {
            try (Adb adb = Adb.connect(c, -1, null)) {
                adb.shell("am kill-all");
                shell = true;
            } catch (java.io.IOException | RuntimeException e) {
                AppLog.w("free memory: " + e.getMessage());
            }
        }
        if (!shell) {
            ActivityManager manager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) for (String pkg : HomeApps.launchers(c).keySet()) {
                if (pkg.equals(c.getPackageName())) continue;
                try { manager.killBackgroundProcesses(pkg); } catch (RuntimeException ignored) {}
            }
        }
        SystemClock.sleep(1500);   // the system settles its free memory figure
        return available(c) - before;
    }

    private static long lastAuto = Long.MIN_VALUE / 2;
    private static volatile boolean autoRunning;

    /**
     * Automatic cleanup (off by default), a few seconds after the home screen came back: only when little memory is
     * left (FeatureRules.autoFree) and not more than once in 15 minutes. Quiet; the log records how much it freed.
     */
    static void autoCheck(Context c) {
        if (!Prefs.autoFreeMemory() || autoRunning) return;
        if (SystemClock.elapsedRealtime() - lastAuto < FeatureRules.AUTO_FREE_COOLDOWN_MS) return;
        final Context app = c.getApplicationContext();
        autoRunning = true;
        new Thread(() -> {
            try {
                ResourceStatus.Snapshot s = ResourceStatus.read(app);
                long now = SystemClock.elapsedRealtime();
                if (!FeatureRules.autoFree(s.ramAvailable, s.ramTotal, now - lastAuto)) return;
                lastAuto = now;
                AppLog.w("auto free memory: " + run(app) / (1024 * 1024) + " MB");
            } finally {
                autoRunning = false;
            }
        }, "auto-free-memory").start();
    }

    private static long available(Context c) {
        ResourceStatus.Snapshot s = ResourceStatus.read(c);
        return Math.max(0, s.ramAvailable);
    }

    /** "Freed 120 MB" or "Memory is already free". */
    static String result(long freed) {
        if (freed < 8L * 1024 * 1024) return Ui.tr("Memory is already free");
        return Ui.trf("Freed {size}", "size", LocaleFormats.bytes(freed, Ui.locale()));
    }
}
