package io.puretv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings → System and about → Updates: check PureTV's GitHub release now, install a checked newer build, and choose
 * the daily behaviour. OK starts each step; nothing runs on arrows. In custom home mode the install needs no prompt (the
 * TV's ADB grant); otherwise Android asks to confirm each app, as with the AirPlay receiver.
 */
public class UpdateActivity extends Activity {
    static final String STATUS = "io.puretv.UPDATE_STATUS";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SettingsPage page;
    private SettingsPage.Data data;
    private String status = "", current = "";
    private boolean working, alive = true;
    private List<AppUpdate.Found> found = new ArrayList<>();
    private int next;

    private final BroadcastReceiver result = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            int code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (code == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                @SuppressWarnings("deprecation") Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (AirPlayInstall.systemConfirmation(c, confirm)) {
                    try { startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
                    catch (ActivityNotFoundException | SecurityException ignored) { }
                }
                finished(Ui.tr("Android couldn't show the install confirmation."));
                return;
            }
            if (code != PackageInstaller.STATUS_SUCCESS) {
                forgetPending();
                finished(code == PackageInstaller.STATUS_FAILURE_ABORTED ? Ui.tr("Installation cancelled.")
                        : Ui.trf("Installation failed: {error}", "error", String.valueOf(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))));
                return;
            }
            installNext();   // the receiver is done: PureTV next (it ends this screen when it installs)
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Watchdog.install(this);
        Ui.init(this);
        try { current = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        data = new SettingsPage.Data(Ui.tr("Updates"), Ui.tr("From PureTV's GitHub releases"), "settings",
                new SettingsPage.Row(Ui.tr("Check for updates"), d -> check(), false),
                new SettingsPage.Row(Ui.tr("Install update"), d -> install(), false),
                new SettingsPage.Row(Ui.tr("Automatic updates"), d -> cycleMode(), true),
                new SettingsPage.Row(Ui.tr("Allow installs from PureTV"), d -> allowInstalls(), false));
        page = new SettingsPage(this, data, this::update);
        setContentView(page);
        AirPlayInstall.listen(this, result, STATUS);
    }

    private boolean quiet() { return AppUpdate.canInstallQuietly(this); }

    private static String modeLabel(String mode) {
        return InstallRules.AUTO.equals(mode) ? Ui.tr("Install automatically")
                : InstallRules.OFF.equals(mode) ? Ui.tr("Off") : Ui.tr("Notify");
    }

    private void update() {
        data.rows[0].value = working ? status : current;
        List<String> names = new ArrayList<>();
        for (AppUpdate.Found f : found) names.add(AppUpdate.label(f));
        data.rows[1].value = names.isEmpty() ? "—" : String.join(", ", names);
        data.rows[2].value = modeLabel(Prefs.updateMode());
        data.rows[3].value = quiet() ? Ui.tr("Not needed") : getPackageManager().canRequestPackageInstalls() ? Ui.tr("On") : Ui.tr("Off");
        String mode = Prefs.updateMode();
        data.hint = (status.isEmpty() || working ? "" : status + "\n\n")
                + (InstallRules.AUTO.equals(mode) ? (quiet()
                    ? Ui.tr("Once a day, while the TV is idle, a newer PureTV release is installed without a prompt; a notice tells you afterwards.")
                    : Ui.tr("Installing without a prompt needs PureTV as the default home screen; until then you get a notice and install here."))
                  : InstallRules.OFF.equals(mode) ? Ui.tr("Nothing is checked. Check for updates still works here.")
                  : Ui.tr("Once a day, a notice on the home screen tells you when a newer release is out."))
                + "\n\n" + Ui.tr("Only PureTV's own release is used, and only a newer build signed like this one; never a downgrade or another app. The AirPlay receiver is updated too when installed.");
    }

    private void check() {
        if (working) return;
        working = true; status = Ui.tr("Checking…"); found = new ArrayList<>(); page.changed();
        final Context app = getApplicationContext();
        io.execute(() -> {
            String text; List<AppUpdate.Found> newer = new ArrayList<>();
            try {
                AppUpdate.Result r = AppUpdate.check(app, (done, total) -> { });
                newer = r.newer;
                text = !newer.isEmpty() ? Ui.tr("An update is ready to install.")
                        : r.blocked.isEmpty() ? Ui.trf("Up to date ({version}).", "version", current) : AppUpdate.blockedText(r);
                if (!newer.isEmpty() && !r.blocked.isEmpty()) text += "\n" + AppUpdate.blockedText(r);
            } catch (Exception e) {
                AppUpdate.clean(app);
                text = Ui.trf("Couldn't check for updates: {error}", "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            final String message = text; final List<AppUpdate.Found> ready = newer;
            main.post(() -> { if (!alive) return; found = ready; finished(message); });
        });
    }

    private void install() {
        if (working) return;
        if (found.isEmpty()) { status = Ui.tr("Check for updates first."); page.changed(); return; }
        if (!quiet() && !getPackageManager().canRequestPackageInstalls()) {
            status = Ui.tr("First allow installs from PureTV, then press OK again."); allowInstalls(); return;
        }
        working = true; status = Ui.tr("Installing…"); next = 0; page.changed();
        if (quiet()) {
            final Context app = getApplicationContext(); final List<AppUpdate.Found> list = new ArrayList<>(found);
            io.execute(() -> {
                List<String> failed = new ArrayList<>();
                for (AppUpdate.Found f : list) {
                    if (f.pkg.equals(getPackageName())) remember(f);
                    if (!AppUpdate.installQuietly(app, f)) { failed.add(AppUpdate.label(f)); if (f.pkg.equals(getPackageName())) forgetPending(); }
                }
                AppUpdate.clean(app);
                final String message = failed.isEmpty() ? Ui.tr("Updated.") : Ui.trf("Couldn't install the update: {list}.", "list", String.join(", ", failed));
                main.post(() -> { if (alive) { found = new ArrayList<>(); finished(message); } });
            });
        } else {
            installNext();
        }
    }

    /** Android's installer, one app at a time, the receiver before PureTV. */
    private void installNext() {
        if (next >= found.size()) { AppUpdate.clean(this); found = new ArrayList<>(); finished(Ui.tr("Updated.")); return; }
        AppUpdate.Found f = found.get(next++);
        if (f.pkg.equals(getPackageName())) remember(f);
        try {
            AirPlayInstall.install(getApplicationContext(), f.apk, f.pkg, STATUS);
        } catch (Exception e) {
            forgetPending();
            finished(Ui.trf("Couldn't install the update: {list}.", "list", AppUpdate.label(f)));
        }
    }

    /** The new version writes the "Updated" notice (AppUpdate.afterUpdate): this process ends with the install. */
    private void remember(AppUpdate.Found f) {
        getSharedPreferences("updates", MODE_PRIVATE).edit().putLong("pendingCode", f.code).putString("pendingLabel", AppUpdate.label(f)).commit();
    }

    private void forgetPending() {
        getSharedPreferences("updates", MODE_PRIVATE).edit().remove("pendingCode").remove("pendingLabel").commit();
    }

    private void cycleMode() {
        String[] modes = InstallRules.MODES;
        int i = java.util.Arrays.asList(modes).indexOf(Prefs.updateMode());
        if (Prefs.setUpdateMode(modes[(i + 1) % modes.length])) ModeJob.scheduleUpdates(getApplicationContext());
        page.changed();
    }

    private void finished(String text) {
        if (!alive) return;
        working = false; status = text;
        page.changed(); page.announceForAccessibility(text);
    }

    private void allowInstalls() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException | SecurityException e) {
            status = Ui.tr("Open TV Settings → Apps → Special app access → Install unknown apps, and allow PureTV.");
            page.changed();
        }
    }

    @Override protected void onResume() { super.onResume(); page.resume(); }
    @Override protected void onPause() { page.pause(); super.onPause(); }
    @Override protected void onDestroy() {
        alive = false; io.shutdownNow(); main.removeCallbacksAndMessages(null);
        try { unregisterReceiver(result); } catch (IllegalArgumentException ignored) { }
        page.pause(); super.onDestroy();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return page.onKey(event) || super.dispatchKeyEvent(event); }
}
