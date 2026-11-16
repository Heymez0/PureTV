package io.puretv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.File;

/**
 * The remote's extra buttons. Two kinds:
 * the app buttons on TCL TVs (the stock home screen opens the apps for Netflix, YouTube and Prime, and TCL Channel;
 * with it off the keys still arrive, so we open the app the person picked in Settings > Remote Buttons; on Vestel TVs
 * only the Netflix button is ours), and, on every TV, the buttons a person gave a job in Remote button test
 * (RemoteKeyRules), plus the remote's apps button: Android sends that one only to the TV's own launcher, which is off
 * in custom home mode, so without us it does nothing.
 * Some of these keys never reach an app's window; only the accessibility service's key filter (RemoteKeyService) sees them.
 */
final class RemoteKeys {
    private RemoteKeys() {}

    /** Some TCL remotes (BT_RC833) send TCL's own YouTube key instead of BUTTON_3: the same button. */
    private static final int KEY_YOUTUBE_TCL = 4065;

    /**
     * The key codes are the brand's own: on other brands they can be other buttons, so nothing here runs there.
     * Vestel builds TVs sold under other brands (F&U...), so BRAND varies: check the maker too.
     */
    static final boolean TCL = is("TCL"), VESTEL = is("Vestel");

    /** The brand app buttons (their own rows in Settings > Remote buttons). */
    static boolean supported() {
        return TCL || VESTEL;
    }

    /**
     * The key service is worth running on this TV: brand app buttons, a saved choice, or a remote with an apps button
     * (Xiaomi's remotes have one even when Android does not report the key before the first press).
     */
    static boolean wanted() {
        if (supported() || is("Xiaomi")) return true;
        try {
            return !Prefs.keyMap().isEmpty() || KeyCharacterMap.deviceHasKey(RemoteKeyRules.KEY_ALL_APPS);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean is(String maker) {
        return maker.equalsIgnoreCase(Build.MANUFACTURER) || maker.equalsIgnoreCase(Build.BRAND);
    }

    /** Remote button test is on screen: it must see every key, the assigned ones too. */
    private static boolean testing(Context c) {
        return testing(c, 10 * 60_000L);
    }

    /**
     * The test touches its marker every few seconds while it is on screen. Home is taken from Android only while that
     * is fresh: a test that crashed (its marker left behind) must never leave Home without a job for long.
     */
    private static boolean testing(Context c, long maxAge) {
        File f = new File(c.getFilesDir(), RemoteTestActivity.MARKER);
        long at = f.lastModified();
        return at != 0 && Math.abs(System.currentTimeMillis() - at) < maxAge;
    }

    /** Does the key's job on its first press. True if the key is ours and in use (it is then consumed). */
    static boolean onKey(Context c, KeyEvent e) {
        // BUTTON_3 is also a game controller's button: keys from a gamepad or a joystick are left alone.
        int source = e.getSource();
        if ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return false;
        if (RemoteKeyRules.shownInTest(e.getKeyCode()) && testing(c, RemoteTestActivity.FRESH_MS)) {
            RemoteTestActivity.forward(c, e);
            return true;
        }
        boolean first = e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0;
        String action = RemoteKeyRules.resolve(Prefs.keyMap(), e.getKeyCode());
        if (action != null) {
            if (testing(c)) return false;
            if (first) run(c, e.getKeyCode(), action);
            return true;
        }
        if (!supported()) return false;
        int keyCode = e.getKeyCode() == KEY_YOUTUBE_TCL ? Prefs.KEY_YOUTUBE : e.getKeyCode();
        boolean ours = false;
        for (int key : Prefs.KEYS) ours |= key == keyCode;
        if (!ours) return false;
        String pkg = Prefs.keyApp(keyCode);
        if (pkg.isEmpty()) return false;   // "Nothing": the key goes on as if we weren't here
        if (first) {
            Intent intent = launchIntent(c.getPackageManager(), pkg);
            AppLog.w("remote key " + e.getKeyCode() + " -> " + pkg + (intent == null ? " (not installed)" : ""));
            if (intent != null) c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    private static void run(Context c, int key, String action) {
        Intent intent;
        if (action.equals(RemoteKeyRules.ALL)) {
            // PureTV's own home screen with its All Apps open (also when another launcher is the default).
            intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(c.getPackageName()).putExtra("all_apps", true);
        } else if (action.equals(RemoteKeyRules.QUICK)) {
            intent = new Intent(c, QuickSettingsActivity.class);
        } else if (action.equals(RemoteKeyRules.SETTINGS)) {
            intent = new Intent(c, SettingsActivity.class);
        } else {
            intent = launchIntent(c.getPackageManager(), action.substring(RemoteKeyRules.APP.length()));
        }
        AppLog.w("remote key " + key + " -> " + (action.startsWith(RemoteKeyRules.APP) ? "app" : action) + (intent == null ? " (not installed)" : ""));
        if (intent == null) return;
        try {
            c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException failed) {
            AppLog.w("remote key " + key + ": " + failed.getClass().getSimpleName());
        }
    }

    /** The app's TV launcher, or its plain one (kept here, not in HomeApps, so the key process stays small). */
    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }
}
