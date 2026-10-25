package io.puretv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.view.KeyEvent;

/** Opens the separately installed receiver; Home screen does not run an AirPlay server. */
public class AirPlayActivity extends Activity {
    private SettingsPage page;
    private AirPlayControl control;
    private SettingsPage.Data data;
    private String error = "";
    private String receiverVersion="";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Watchdog.install(this);
        Ui.init(this);
        control=new AirPlayControl(this,()->{if(page!=null)page.changed();});
        SettingsPage.Row network = new SettingsPage.Row(Ui.tr("Rede", "Network"), null, false);
        network.network = true;
        data = new SettingsPage.Data("AirPlay", Ui.tr("Espelhe seu dispositivo Apple", "Mirror your Apple device"), "tv",
                new SettingsPage.Row("AirPlay", direction -> control.toggle(), false),
                new SettingsPage.Row(Ui.tr("Abrir receptor AirPlay", "Open AirPlay Receiver"), direction -> openReceiver(), false),
                new SettingsPage.Row(Ui.tr("Receptor", "Receiver"), null, false), network);
        page = new SettingsPage(this, data, this::update);
        setContentView(page);
    }

    private boolean available() { return !AirPlayReceiver.version(this).isEmpty(); }

    private void update() {
        String version = receiverVersion;
        data.rows[0].value=control.value();
        data.rows[2].value = version == null || version.isEmpty() ? Ui.tr("Não instalado", "Not installed") : version;
        NetworkStatus.Snapshot snapshot = page.networkSnapshot();
        SettingsPage.Row network = data.rows[3];
        network.state = snapshot.state;
        network.transport = snapshot.transport;
        network.color = snapshot.state == NetworkStatus.ONLINE ? 0xFF30D158
                : snapshot.state == NetworkStatus.LOCAL ? 0xFFFF9F0A : 0xFFAEAEB2;
        network.value = snapshot.transport.isEmpty() ? NetworkStatus.label(snapshot) : snapshot.transport;
        if (!snapshot.addresses.isEmpty()) network.value += " · " + snapshot.addresses;
        data.hint = (!error.isEmpty() ? error + "\n\n" : version.isEmpty() ? missing() + "\n\n" : "")
                + Ui.tr("Conecte esta TV e seu dispositivo Apple à mesma rede Wi-Fi.",
                "Connect this TV and your Apple device to the same Wi-Fi network.") + "\n\n"
                + Ui.tr("iPhone / iPad: Central de Controle → Espelhar a Tela → escolha esta TV.",
                "iPhone / iPad: Control Center → Screen Mirroring → select this TV.") + "\n\n"
                + Ui.tr("Mac: Central de Controle → Espelhamento de Tela → escolha esta TV.",
                "Mac: Control Center → Screen Mirroring → select this TV.") + "\n\n"
                + Ui.tr("Vídeos protegidos podem não ser espelhados.", "Protected video may not mirror.") + "\n"
                + Ui.tr("O Android 9 não oferece saída de áudio do sistema para o HomePod.",
                "HomePod system audio output is not supported on Android 9.");
    }

    private void openReceiver() {
        if (!available()) { error = missing(); page.announceForAccessibility(error); return; }
        try {
            error = "";
            if (!AirPlayReceiver.open(this)) throw new ActivityNotFoundException();
        } catch (ActivityNotFoundException | SecurityException e) {
            error = Ui.tr("Não foi possível abrir o receptor AirPlay. Verifique a instalação na TV.",
                    "Could not open AirPlay Receiver. Check its installation on this TV.");
            page.announceForAccessibility(error);
        }
    }

    private String missing() {
        return Ui.tr("O receptor AirPlay não está instalado. Instale-o nesta TV e volte aqui.",
                "AirPlay Receiver is not installed. Install it on this TV, then return here.");
    }
    @Override protected void onResume() { super.onResume();receiverVersion=AirPlayReceiver.version(this);page.resume();control.resume(); }
    @Override protected void onPause() { control.pause(); page.pause(); super.onPause(); }
    @Override protected void onDestroy() { page.pause(); super.onDestroy(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return page.onKey(event) || super.dispatchKeyEvent(event);
    }
}
