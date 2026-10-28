package io.puretv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

/**
 * Quick settings → Sleep timer: after 30 to 120 minutes the TV goes to sleep, as if the remote's power button was
 * pressed (the TV's own SLEEP key through the existing ADB grant, so custom home mode only; never a new prompt). A
 * notice comes one minute before; choosing Off or another length cancels the pending alarms. A restart clears it.
 */
final class SleepTimer {
    private SleepTimer() {}

    /** The lengths OK steps through, in minutes; 0 = off. */
    static final int[] MINUTES = {0, 30, 60, 90, 120};
    private static final long WARN_MS = 60_000;
    private static final String WARN = "io.puretv.SLEEP_WARN", SLEEP = "io.puretv.SLEEP_NOW";

    // Written in :home (quick settings) and read in the main process (the alarms): reread across processes.
    @SuppressWarnings("deprecation")
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sleep", Context.MODE_MULTI_PROCESS);
    }

    /** Custom home mode: the TV already allows this app's ADB key. */
    static boolean available(Context c) {
        return HomeMode.custom(c);
    }

    /** Wall-clock time the TV goes to sleep, or 0 when no timer runs. */
    static long endsAt(Context c) {
        long at = prefs(c).getLong("endsAt", 0);
        return at > System.currentTimeMillis() ? at : 0;
    }

    /** Whole minutes left (rounded up), 0 when off. */
    static int minutesLeft(Context c) {
        long at = endsAt(c);
        return at == 0 ? 0 : (int) ((at - System.currentTimeMillis() + 59_999) / 60_000);
    }

    /** OK on the row: the next length after the one running now (Off after the longest). */
    static int next(Context c) {
        int left = minutesLeft(c);
        for (int m : MINUTES) if (m > left) return m;
        return 0;
    }

    static void set(Context c, int minutes) {
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) { alarms.cancel(pending(c, WARN)); alarms.cancel(pending(c, SLEEP)); }
        if (minutes <= 0 || !available(c)) { clear(c); return; }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        prefs(c).edit().putLong("endsAt", at).commit();
        schedule(c, WARN, at - WARN_MS);
    }

    static void clear(Context c) {
        prefs(c).edit().remove("endsAt").commit();
    }

    private static PendingIntent pending(Context c, String action) {
        Intent intent = new Intent(c, Fire.class).setAction(action);
        return PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context c, String action, long at) {
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent p = pending(c, action);
        // Exact on Android 9-11; newer Android allows it only with a special access, so a few minutes' leeway there.
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
    }

    /** The alarms (main process, not exported): the notice, then sleep. */
    public static class Fire extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            Context c = context.getApplicationContext();
            long at = prefs(c).getLong("endsAt", 0);
            if (at == 0) return;   // turned off meanwhile
            if (WARN.equals(intent.getAction())) {
                schedule(c, SLEEP, at);
                Prefs.refreshLanguage();
                Toast.makeText(c, Ui.tr("The TV goes to sleep in 1 minute. Quick settings → Sleep timer turns it off."),
                        Toast.LENGTH_LONG).show();
            } else if (SLEEP.equals(intent.getAction())) {
                clear(c);
                final PendingResult result = goAsync();
                new Thread(() -> {
                    try (Adb adb = Adb.connect(c, -1, null)) {
                        adb.shell("input keyevent KEYCODE_SLEEP");
                    } catch (java.io.IOException | RuntimeException e) {
                        AppLog.w("sleep timer: " + e.getMessage());
                    } finally {
                        result.finish();
                    }
                }, "sleep-timer").start();
            }
        }
    }
}
