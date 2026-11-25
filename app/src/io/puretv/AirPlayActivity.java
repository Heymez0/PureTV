package io.puretv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

/**
 * Settings → Connections → AirPlay: everything about AirPlay in one place (on/off, keep on, the receiver app and the
 * connection help). PureTV does not run an AirPlay server itself; the separately installed receiver does.
 */
public class AirPlayActivity extends Activity {
    private SettingsPage page;
    private AirPlayControl control;
    private SettingsPage.Data data;
    private String receiverVersion = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Watchdog.install(this);
        Ui.init(this);
        control = new AirPlayControl(this, () -> { if (page != null) page.changed(); });
        SettingsPage.Row on = new SettingsPage.Row("AirPlay", direction -> control.toggle());
        SettingsPage.Row always = new SettingsPage.Row(Ui.tr("Keep AirPlay on"), direction -> control.toggleAlways());
        always.hint = Ui.tr("On: AirPlay starts with the TV and stays on, keeping the processor awake. Off: it runs only after you turn it on and stops after 15 minutes without a sender.");
        SettingsPage.Row receiver = new SettingsPage.Row(Ui.tr("AirPlay receiver"),
                direction -> startActivity(new Intent(this, AirPlayInstallActivity.class)));
        receiver.hint = Ui.tr("Install or update the optional receiver from PureTV's release. It runs only after you turn AirPlay on.");
        SettingsPage.Row help = new SettingsPage.Row(Ui.tr("AirPlay connection help"),
                direction -> startActivity(new Intent(this, DeviceStatusActivity.class).putExtra("airplay_help", true)));
        data = new SettingsPage.Data("AirPlay", Ui.tr("Espelhe seu dispositivo Apple", "Mirror your Apple device"), "tv",
                on.icon("tv"), always.icon("power"), receiver.icon("restore"), help.icon("guide"));
        data.crumb = SettingsPage.crumb("Connections");
        page = new SettingsPage(this, data, this::update);
        setContentView(page);
    }

    private void update() {
        data.rows[0].value = control.value();
        data.rows[0].toggle = control.switchState();
        data.rows[1].value = control.alwaysValue();
        data.rows[1].toggle = control.alwaysState();
        data.rows[2].value = receiverVersion.isEmpty() ? Ui.tr("Not installed") : receiverVersion;
        data.hint = Ui.tr("Conecte esta TV e seu dispositivo Apple à mesma rede Wi-Fi.",
                "Connect this TV and your Apple device to the same Wi-Fi network.") + "\n"
                + Ui.tr("iPhone / iPad: Central de Controle → Espelhar a Tela → escolha esta TV.",
                "iPhone / iPad: Control Center → Screen Mirroring → select this TV.");
    }

    @Override protected void onResume() {
        super.onResume();
        receiverVersion = AirPlayReceiver.themedVersion(this);
        page.resume();
        control.resume();
    }

    @Override protected void onPause() { control.pause(); page.pause(); super.onPause(); }
    @Override protected void onDestroy() { page.pause(); super.onDestroy(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return page.onKey(event) || super.dispatchKeyEvent(event); }
}
