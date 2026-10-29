package io.puretv;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

/**
 * Runs the mode switches in the background (HomeMode). As a job, the system keeps the app's main process running until
 * the switch finishes, even with the screen showing something else.
 */
public class ModeJob extends JobService {
    static final String APPLY = "apply", VERIFY = "verify", REVERT = "revert", MEMORY = "memory", REMOTE = "remote",
            ASSISTANT = "assistant", UPDATE = "update", COMPILE = "compile", OPTIMIZE = "optimize";

    @Override public boolean onStartJob(JobParameters params) {
        PersistableBundle extras = params.getExtras();
        final String action = extras.getString("action", "");
        final String mode = extras.getString("mode", "");
        final Context app = getApplicationContext();
        new Thread(() -> {
            try {
                Prefs.refreshLanguage();   // chosen in :home; this process may have read it long ago
                switch (action) {
                    case APPLY: HomeMode.applyNow(app, mode); break;
                    case VERIFY: HomeMode.verifyNow(app); break;
                    case REVERT: HomeMode.revertHomeNow(app); break;
                    case MEMORY: HomeMode.measureMemoryNow(app, mode); break;
                    case REMOTE: HomeMode.ensureRemoteServiceNow(app); break;
                    case ASSISTANT: HomeMode.assistantNow(app); break;
                    case UPDATE: AppUpdate.runJob(app); break;
                    case COMPILE: HomeMode.compileNow(app); break;
                    case OPTIMIZE: HomeMode.optimizeAppsNow(app); break;
                    default: break;
                }
            } catch (Throwable t) {
                AppLog.w("job " + action + " failed: " + t);
            }
            jobFinished(params, false);
        }, "home-mode-" + action).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }

    /**
     * A fixed job ID per action. The same ID replaces a pending job, so two actions must never share one (a hash could:
     * then a boot check would silently cancel a switch). The values are the ones the hash gave, kept for existing jobs.
     */
    private static int id(String action) {
        switch (action) {
            case APPLY: return 1230;
            case VERIFY: return 1495;
            case REVERT: return 1412;
            case MEMORY: return 1671;
            case REMOTE: return 1874;
            case ASSISTANT: return 1318;
            case UPDATE: return 1950;
            case COMPILE: return 1789;
            case OPTIMIZE: return 1856;
            default: throw new IllegalArgumentException(action);
        }
    }

    /**
     * The daily update check (AppUpdate), kept across restarts. On Wi-Fi or Ethernet only; installing automatically
     * also waits for the TV to be idle, so an update never interrupts what is on screen. Off cancels it.
     */
    static void scheduleUpdates(Context c) {
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        String mode = Prefs.updateMode();
        if (InstallRules.OFF.equals(mode)) { scheduler.cancel(id(UPDATE)); return; }
        boolean idle = InstallRules.AUTO.equals(mode);
        android.app.job.JobInfo pending = scheduler.getPendingJob(id(UPDATE));
        if (pending != null && pending.isPeriodic() && pending.isRequireDeviceIdle() == idle) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putString("action", UPDATE);
        extras.putString("mode", "");
        scheduler.schedule(new JobInfo.Builder(id(UPDATE), new ComponentName(c, ModeJob.class)).setExtras(extras)
                .setPeriodic(24 * 60 * 60_000L).setPersisted(true).setRequiresDeviceIdle(idle)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).build());
    }

    /** Drops a pending job of this action (a running one finishes on its own). */
    static void cancel(Context c, String action) {
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.cancel(id(action));
    }

    /** Schedules a job (one pending per action: a new request replaces the old one). */
    static void schedule(Context c, String action, String mode, long delayMs) {
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putString("action", action);
        extras.putString("mode", mode == null ? "" : mode);
        JobInfo.Builder job = new JobInfo.Builder(id(action), new ComponentName(c, ModeJob.class)).setExtras(extras);
        if (delayMs > 0) job.setMinimumLatency(delayMs);
        job.setOverrideDeadline(delayMs + 2_000);
        scheduler.schedule(job.build());
    }
}
