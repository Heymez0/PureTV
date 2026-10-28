package io.puretv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

/**
 * "On TV start": opens the chosen app, Live TV or input once per TV start, when the home screen first appears (it is
 * the TV's home screen, so no accessibility service is needed). Only in the first minutes after the start, never after
 * an app update or a crash restart; Back returns to the home screen. A locked app asks for the PIN as usual.
 */
final class StartupLaunch {
    private StartupLaunch() {}

    private static final String KEY = "startupBoot";
    private static boolean checked;

    private static int bootCount(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static SharedPreferences ui(Context c) {
        return c.getSharedPreferences("home-ui", Context.MODE_PRIVATE);
    }

    /** A choice made now applies from the next start, not to this one. */
    static void markCurrentStart(Context c) {
        int boot = bootCount(c);
        if (boot > 0) ui(c).edit().putInt(KEY, boot).apply();
    }

    /** Called when the home screen is resumed. */
    static void check(Activity a, View from) {
        if (checked) return;    // a TV start can't happen while this process lives: one check per process
        checked = true;
        String choice = Prefs.startup();
        int boot = bootCount(a), last = ui(a).getInt(KEY, 0);
        if (boot <= 0 || boot == last) return;
        ui(a).edit().putInt(KEY, boot).apply();    // once per start, even if the choice can't open
        if (!FeatureRules.launchAtStart(choice, boot, last, SystemClock.elapsedRealtime())) return;
        AppLog.w("on TV start: " + (choice.startsWith(FeatureRules.APP) ? "app" : choice.startsWith(FeatureRules.INPUT) ? "input" : choice));
        if (choice.equals(FeatureRules.LIVE)) {
            open(a, from, HomeApps.action(HomeApps.LIVE_TV, "live", Ui.tr("Live TV"), "tv"));
        } else if (choice.startsWith(FeatureRules.APP)) {
            String pkg = choice.substring(FeatureRules.APP.length());
            String label = pkg;
            try {
                PackageManager pm = a.getPackageManager();
                label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
            open(a, from, HomeApps.app(pkg, label));
        } else {
            final String key = choice.substring(FeatureRules.INPUT.length());
            final Context app = a.getApplicationContext();
            new Thread(() -> {
                HomeApps.Item found = null;
                try {
                    for (HomeApps.Item input : TvInputCatalog.hdmi(app, true)) if (key.equals(input.key)) { found = input; break; }
                } catch (RuntimeException ignored) { }
                final HomeApps.Item input = found;
                a.runOnUiThread(() -> { if (input != null && !a.isFinishing()) open(a, from, input); });
            }, "startup-input").start();
        }
    }

    private static void open(Activity a, View from, HomeApps.Item item) {
        if (!HomeApps.open(a, item, from, 0, 0, Math.max(1, from.getWidth()), Math.max(1, from.getHeight())))
            AppLog.w("on TV start: couldn't open " + item.label);
    }
}
