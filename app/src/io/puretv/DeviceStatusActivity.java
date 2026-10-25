package io.puretv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;

/** Read-only Android status with a remote-friendly AirPlay guide, refreshed only while visible. */
public class DeviceStatusActivity extends Activity {
    private SettingsPage page;
    private SettingsPage.Data data;
    private boolean resumed, help;
    private String error = "";
    private String receiverVersion="";
    private ResourceStatus.Snapshot resources = new ResourceStatus.Snapshot();
    private int resourceGeneration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Watchdog.install(this);
        Ui.init(this);
        show(getIntent().getBooleanExtra("airplay_help", false));
    }
    private void show(boolean airplayHelp) {
        if (page != null) page.pause();
        help = airplayHelp;
        SettingsPage.Row network = new SettingsPage.Row(Ui.tr("Rede", "Network"), null, false);
        network.network = true;
        if (help) {
            data = new SettingsPage.Data(Ui.tr("Ajuda de conexão AirPlay", "AirPlay connection help"),
                    Ui.tr("Conecte e espelhe", "Connect and mirror"), "network",
                    network, new SettingsPage.Row(Ui.tr("Receptor", "Receiver"), null, false),
                    new SettingsPage.Row(Ui.tr("Abrir receptor AirPlay", "Open AirPlay Receiver"), direction -> openReceiver(), false),
                    new SettingsPage.Row(Ui.tr("Ajustes de Wi-Fi", "Wi-Fi settings"), direction -> openNetwork(), false));
        } else {
            data = new SettingsPage.Data(Ui.tr("Estado do dispositivo", "Device status"),
                    Ui.tr("Rede, memória e armazenamento", "Network, memory and storage"), "network",
                    network, new SettingsPage.Row("IP", null, false),
                    new SettingsPage.Row(Ui.tr("Armazenamento", "Storage"), null, false),
                    new SettingsPage.Row("RAM", null, false),
                    new SettingsPage.Row(Ui.tr("Receptor AirPlay", "AirPlay receiver"), null, false),
                    new SettingsPage.Row(Ui.tr("Ajuda de conexão AirPlay", "AirPlay connection help"), direction -> show(true), false),
                    new SettingsPage.Row(Ui.tr("Tela e rede", "Display & Network"),
                            direction -> startActivity(new Intent(this, DisplaySettingsActivity.class)), false),
                    new SettingsPage.Row(Ui.tr("Ajustes de Wi-Fi", "Wi-Fi settings"), direction -> openNetwork(), false));
        }
        page = new SettingsPage(this, data, this::update);
        setContentView(page);
        if (resumed) page.resume();
        if (resumed && !help) loadResources();
    }
    private void update() {
        NetworkStatus.Snapshot snapshot = page.networkSnapshot();
        SettingsPage.Row network = data.rows[0];
        network.state = snapshot.state;
        network.transport = snapshot.transport;
        network.color = snapshot.state == NetworkStatus.ONLINE ? 0xFF30D158
                : snapshot.state == NetworkStatus.LOCAL ? 0xFFFF9F0A : 0xFFAEAEB2;
        network.value = help ? snapshot.transport : NetworkStatus.label(snapshot)
                + (snapshot.transport.isEmpty() ? "" : " · " + snapshot.transport);
        if (help) network.value = (network.value.isEmpty() ? NetworkStatus.label(snapshot) : network.value)
                + (snapshot.addresses.isEmpty() ? "" : " · " + snapshot.addresses);
        else data.rows[1].value = snapshot.addresses.isEmpty() ? "—" : snapshot.addresses;
        String version = receiverVersion;
        data.rows[help ? 1 : 4].value = version.isEmpty() ? Ui.tr("Não instalado", "Not installed")
                : Ui.tr("Instalado", "Installed") + " · " + version;
        if (!help) {
            data.rows[2].value = ResourceStatus.value(this, resources.storageAvailable, resources.storageTotal);
            data.rows[3].value = ResourceStatus.value(this, resources.ramAvailable, resources.ramTotal);
        }
        String condition = snapshot.state == NetworkStatus.OFFLINE
                ? Ui.tr("A TV está sem rede. Abra os ajustes de Wi-Fi para conectar.",
                "This TV is offline. Open Wi-Fi settings to connect.")
                : snapshot.state == NetworkStatus.UNKNOWN ? Ui.tr("Verifique a conexão da TV nos ajustes de Wi-Fi.",
                "Check this TV's connection in Wi-Fi settings.") : "";
        String guide = help ? Ui.tr("1. Conecte o dispositivo Apple à mesma rede Wi-Fi.\n2. Abra o receptor e confirme que está iniciado.\n3. No iPhone, iPad ou Mac: Central de Controle → Espelhar a Tela → escolha o nome do receptor.\n4. Se solicitado, digite o PIN mostrado na TV.\n\nSe não aparecer: verifique a rede de convidados, VPN e o isolamento entre dispositivos no roteador.",
                "1. Connect your Apple device to the same Wi-Fi network.\n2. Open the receiver and check that it is started.\n3. On iPhone, iPad or Mac: Control Center → Screen Mirroring → select the receiver name.\n4. If asked for a PIN, enter the code shown on this TV.\n\nIf it is missing: check guest Wi-Fi, VPN and device isolation on the router.")
                : Ui.tr("Se o AirPlay não aparecer, abra a ajuda de conexão. O estado de execução do receptor é mostrado no próprio app.",
                "If AirPlay is missing, open connection help. The receiver app shows whether it is running.");
        data.hint = (error.isEmpty() ? "" : error + "\n\n") + (condition.isEmpty() ? "" : condition + "\n\n") + guide;
        if (!help) data.hint = Ui.tr("Disponível / total. Os valores são atualizados ao abrir esta tela.",
                "Available / total. Values refresh when you open this screen.") + "\n\n" + data.hint;
    }
    private void openReceiver() {
        error = AirPlayReceiver.open(this) ? "" : Ui.tr("Não foi possível abrir o receptor AirPlay. Verifique a instalação na TV.",
                "Could not open AirPlay Receiver. Check its installation on this TV.");
        if (!error.isEmpty()) page.announceForAccessibility(error);
    }
    private void openNetwork() {
        try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        catch (ActivityNotFoundException | SecurityException e) {
            error = Ui.tr("Não foi possível abrir os ajustes de Wi-Fi.", "Could not open Wi-Fi settings.");
            page.announceForAccessibility(error);
        }
    }
    @Override public void onBackPressed() { if (help) { error = ""; show(false); } else super.onBackPressed(); }
    private void loadResources() {
        final int generation = ++resourceGeneration;
        final android.content.Context context = getApplicationContext();
        new Thread(() -> {
            ResourceStatus.Snapshot snapshot = ResourceStatus.read(context);
            runOnUiThread(() -> {
                if (!resumed || help || generation != resourceGeneration) return;
                resources = snapshot;
                page.changed();
            });
        }, "device-status").start();
    }
    @Override protected void onResume() { super.onResume();receiverVersion=AirPlayReceiver.version(this);resumed = true; page.resume(); if (!help) loadResources(); }
    @Override protected void onPause() { resumed = false; resourceGeneration++; page.pause(); super.onPause(); }
    @Override protected void onDestroy() { page.pause(); super.onDestroy(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return page.onKey(event) || super.dispatchKeyEvent(event); }
}
