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

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The optional AirPlay receiver, installed on request: download from PureTV's release, check, then Android's own
 * install confirmation. OK starts each step; nothing happens on arrows or in the background.
 */
public class AirPlayInstallActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SettingsPage page;
    private SettingsPage.Data data;
    private String installed = "", status = "";
    private boolean working, alive = true, registered;
    private int lastPercent = -1;

    private final BroadcastReceiver result = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            int code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (code == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (AirPlayInstall.systemConfirmation(c, confirm)) {
                    try { startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
                    catch (ActivityNotFoundException | SecurityException ignored) { }
                }
                finished(Ui.tr("Android couldn't show the install confirmation."));
                return;
            }
            AirPlayInstall.clean(getApplicationContext());
            finished(code == PackageInstaller.STATUS_SUCCESS ? Ui.tr("Installed. Open the receiver once to choose its name.")
                    : code == PackageInstaller.STATUS_FAILURE_ABORTED ? Ui.tr("Installation cancelled.")
                    : Ui.trf("Installation failed: {error}", "error", String.valueOf(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))));
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Watchdog.install(this);
        Ui.init(this);
        data = new SettingsPage.Data(Ui.tr("AirPlay receiver"), Ui.tr("Optional: mirror an iPhone, iPad or Mac"), "tv",
                new SettingsPage.Row(Ui.tr("Install AirPlay receiver"), direction -> start(), false),
                new SettingsPage.Row(Ui.tr("Allow installs from PureTV"), direction -> allowInstalls(), false),
                new SettingsPage.Row(Ui.tr("Open AirPlay receiver"), direction -> openReceiver(), false));
        page = new SettingsPage(this, data, this::update);
        setContentView(page);
        AirPlayInstall.listen(this, result, AirPlayInstall.STATUS);
        registered = true;
    }

    private boolean allowedToInstall() { return getPackageManager().canRequestPackageInstalls(); }

    private void update() {
        data.rows[0].value = working ? status : installed.isEmpty() ? Ui.tr("Not installed") : installed;
        data.rows[1].value = allowedToInstall() ? Ui.tr("On") : Ui.tr("Off");
        data.rows[2].value = "";
        data.hint = (status.isEmpty() || working ? "" : status + "\n\n")
                + Ui.tr("PureTV downloads the receiver (about 9 MB) from its own GitHub release, checks that it is the PureTV receiver signed like this app, and then Android asks you to confirm the installation.") + "\n\n"
                + Ui.tr("The receiver is a separate GPL-3.0 app; its complete source is in the same release. It runs only after you turn AirPlay on.");
    }

    private void start() {
        if (working) return;
        if (!allowedToInstall()) { status = Ui.tr("First allow installs from PureTV, then press OK again."); allowInstalls(); return; }
        working = true;
        lastPercent = -1;
        status = Ui.tr("Downloading…");
        page.changed();
        final Context app = getApplicationContext();
        io.execute(() -> {
            try {
                File apk = AirPlayInstall.download(app, (done, total) -> {
                    int percent = total > 0 ? (int) (done * 100 / total) : -1;
                    if (percent != lastPercent && percent % 5 == 0) {
                        lastPercent = percent;
                        main.post(() -> { if (alive && working) { status = Ui.trf("Downloading… {n}%", "n", percent); page.changed(); } });
                    }
                });
                main.post(() -> { if (alive) { status = Ui.tr("Checking the receiver…"); page.changed(); } });
                String version = AirPlayInstall.verify(app, apk);
                main.post(() -> { if (alive) { status = Ui.trf("Installing {version}…", "version", version); page.changed(); } });
                AirPlayInstall.install(app, apk);
            } catch (Exception e) {
                AirPlayInstall.clean(app);
                String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                main.post(() -> finished(Ui.trf("Couldn't install the receiver: {error}", "error", error)));
            }
        });
    }

    private void finished(String text) {
        if (!alive) return;
        working = false;
        status = text;
        installed = AirPlayReceiver.themedVersion(this);
        page.changed();
        page.announceForAccessibility(text);
    }

    private void allowInstalls() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException | SecurityException e) {
            status = Ui.tr("Open TV Settings → Apps → Special app access → Install unknown apps, and allow PureTV.");
            page.changed();
        }
    }

    private void openReceiver() {
        if (installed.isEmpty() || !AirPlayReceiver.open(this)) {
            status = Ui.tr("Install the receiver first.");
            page.changed();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        installed = AirPlayReceiver.themedVersion(this);
        page.resume();
    }

    @Override protected void onPause() { page.pause(); super.onPause(); }

    @Override protected void onDestroy() {
        alive = false;
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
        if (registered) unregisterReceiver(result);
        page.pause();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) { return page.onKey(event) || super.dispatchKeyEvent(event); }
}
