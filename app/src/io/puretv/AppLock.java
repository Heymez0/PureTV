package io.puretv;

import android.app.Activity;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.Calendar;

/**
 * A simple app lock: the apps chosen in Settings ask for a PIN when they are opened from PureTV (home screen, shelf,
 * other apps, recent content), always or only in the evening hours. It does not stop an app opened another way (TV
 * Settings, a remote's app button, another launcher): it is a household convenience, not a security boundary, and the
 * settings say so. The PIN is stored salted and hashed (FeatureRules) and never in the settings backup.
 */
final class AppLock {
    private AppLock() {}

    private static String passPackage;
    private static long passUntil;
    private static int failures;
    private static long lastFailAt;

    /** Locked right now: a PIN is set, the app is on the list and the time is inside the chosen hours. */
    static boolean locks(String pkg) {
        if (pkg == null || !Prefs.lockSet() || !Prefs.lockedApps().contains(pkg)) return false;
        Calendar now = Calendar.getInstance();
        return FeatureRules.lockedAt(Prefs.lockHours(), now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
    }

    /**
     * Before opening an app: true when the lock took over (the PIN dialog is up and `open` runs after the right PIN);
     * false to open now. The right PIN lets that one opening through.
     */
    static boolean intercept(Activity a, String pkg, String label, Runnable open) {
        if (!locks(pkg)) return false;
        if (pkg.equals(passPackage) && SystemClock.uptimeMillis() < passUntil) {
            passPackage = null;
            return false;
        }
        ask(a, Ui.trf("PIN to open {app}", "app", label), () -> {
            passPackage = pkg;
            passUntil = SystemClock.uptimeMillis() + 5_000;
            open.run();
        });
        return true;
    }

    /** Changes that could undo the lock (its settings, hiding, uninstalling, the default home): the PIN first. */
    static void confirm(Activity a, Runnable action) {
        if (!Prefs.lockSet()) action.run();
        else ask(a, Ui.tr("Enter the PIN"), action);
    }

    /** A new PIN, typed twice; `done` runs once it is saved. */
    static void choosePin(Activity a, Runnable done) {
        pinDialog(a, Ui.tr("New PIN (4 to 8 digits)"), first -> {
            if (!FeatureRules.validPin(first)) { toast(a, Ui.tr("Use 4 to 8 digits.")); return; }
            pinDialog(a, Ui.tr("Enter the new PIN again"), second -> {
                if (!first.equals(second)) { toast(a, Ui.tr("The PINs didn't match. Nothing changed.")); return; }
                Prefs.setLockPin(FeatureRules.storePin(first));
                toast(a, Ui.tr("PIN saved."));
                done.run();
            });
        });
    }

    private static void ask(Activity a, String title, Runnable ok) {
        long wait = FeatureRules.waitLeft(failures, lastFailAt, SystemClock.elapsedRealtime());
        if (wait > 0) {
            toast(a, Ui.trf("Too many wrong PINs. Try again in {n} s.", "n", (wait + 999) / 1000));
            return;
        }
        pinDialog(a, title, pin -> {
            if (FeatureRules.pinMatches(pin, Prefs.lockPin())) {
                failures = 0;
                ok.run();
            } else {
                failures++;
                lastFailAt = SystemClock.elapsedRealtime();
                toast(a, Ui.tr("Wrong PIN."));
            }
        });
    }

    /** The themed keypad (PinDialog); the remote's arrows and OK or its number keys fill it. */
    private static void pinDialog(Activity a, String title, PinDialog.Entered entered) {
        if (a.isFinishing()) return;
        new PinDialog(a, title, entered).show();
    }

    private static void toast(Activity a, String text) {
        Toast.makeText(a, text, Toast.LENGTH_SHORT).show();
    }

    /** The settings label for the hours. */
    static String hoursLabel(String hours) {
        if (FeatureRules.ALWAYS.equals(hours)) return Ui.tr("Always");
        return Ui.trf("From {hour}:00 to 7:00", "hour", hours.substring(0, hours.indexOf('-')));
    }
}
