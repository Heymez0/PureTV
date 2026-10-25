package io.puretv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** Visual preferences and a read-only connection snapshot in the existing settings theme. */
public class DisplaySettingsActivity extends Activity {
    private SettingsPage page;
    private SettingsPage.Data data;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Watchdog.install(this);
        Ui.init(this);
        SettingsPage.Row network = new SettingsPage.Row(Ui.tr("Rede", "Network"), null, false);
        network.network = true;
        data = new SettingsPage.Data(Ui.tr("Tela e rede", "Display & Network"),
                Ui.tr("Conexão, hora e aparência", "Connection, clock and appearance"), "settings",
                network, new SettingsPage.Row("IP", null, false),
                new SettingsPage.Row(Ui.tr("Formato da hora", "Clock format"), direction -> changeClock(direction), true),
                new SettingsPage.Row(Ui.tr("Modo leve", "Lightweight mode"),
                        direction -> Prefs.setLightweight(!Prefs.lightweight()), true));
        data.hint = Ui.tr("OK ou as setas mudam o valor.", "OK or the arrows change the value.") + "\n\n"
                + Ui.tr("O modo leve reduz os efeitos e as animações. Desligado mantém o visual original.",
                "Lightweight mode reduces effects and animation. Off keeps the original appearance.") + "\n\n"
                + Ui.tr("As mudanças se aplicam ao voltar ao início. Voltar retorna aos ajustes.",
                "Changes apply when you return home. Back returns to settings.");
        page = new SettingsPage(this, data, this::update);
        setContentView(page);
    }

    private void changeClock(int direction) {
        String mode = Prefs.clockFormat();
        int index = "12".equals(mode) ? 1 : "24".equals(mode) ? 2 : 0;
        String[] modes = {"auto", "12", "24"};
        Prefs.setClockFormat(modes[(index + direction + modes.length) % modes.length]);
    }

    private void update() {
        NetworkStatus.Snapshot snapshot = page.networkSnapshot();
        SettingsPage.Row network = data.rows[0];
        network.value = NetworkStatus.label(snapshot)
                + (snapshot.transport.isEmpty() ? "" : " · " + snapshot.transport);
        network.state = snapshot.state;
        network.transport = snapshot.transport;
        network.color = snapshot.state == NetworkStatus.ONLINE ? 0xFF30D158
                : snapshot.state == NetworkStatus.LOCAL ? 0xFFFF9F0A : 0xFFAEAEB2;
        data.rows[1].value = snapshot.addresses.isEmpty() ? "—" : snapshot.addresses;
        String mode = Prefs.clockFormat();
        data.rows[2].value = ("auto".equals(mode) ? Ui.tr("Automático", "Automatic")
                : "12".equals(mode) ? "12h" : "24h") + " · " + Ui.clock(System.currentTimeMillis());
        data.rows[3].value = Prefs.lightweight() ? Ui.tr("Ligado", "On") : Ui.tr("Desligado", "Off");
    }

    @Override protected void onResume() { super.onResume(); page.resume(); }
    @Override protected void onPause() { page.pause(); super.onPause(); }
    @Override protected void onDestroy() { page.pause(); super.onDestroy(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return page.onKey(event) || super.dispatchKeyEvent(event);
    }
}
