package io.puretv;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Built-in functions only; never parses app IDs, arbitrary intents, URLs or shell commands. */
final class QuickShortcuts {
    private QuickShortcuts() { }
    static final String[] BUILT_INS = QuickIds.ALL;

    static HomeApps.Item item(Context c, String id) {
        switch (id) {
            case "all": return HomeApps.action(HomeApps.ALL_APPS, id, Ui.tr("Todos os Apps", "All Apps"), "apps");
            case "airplay": return HomeApps.action(HomeApps.AIRPLAY, id, "AirPlay", "tv");
            case "status": return HomeApps.action(HomeApps.DEVICE_STATUS, id, Ui.tr("Estado do dispositivo", "Device status"), "network");
            case "settings": return HomeApps.action(HomeApps.SETTINGS, id, Ui.tr("Ajustes do Início", "Home Settings"), "settings");
            case "tv-settings": return HomeApps.action(HomeApps.ANDROID_SETTINGS, id, Ui.tr("Ajustes da TV", "TV Settings"), "tv_settings");
            case "network": return HomeApps.action(HomeApps.NETWORK_SETTINGS, id, Ui.tr("Ajustes de Wi-Fi", "Wi-Fi settings"), "network");
            case "display": return HomeApps.action(HomeApps.DISPLAY_SETTINGS, id, Ui.tr("Tela e rede", "Display & Network"), "settings");
            default: return null;
        }
    }
    static List<HomeApps.Item> items(Context c) {
        List<HomeApps.Item> out = new ArrayList<>();
        for (String id : Prefs.quickShortcuts()) {
            HomeApps.Item item = item(c, id);
            if (item != null) out.add(item);
        }

        return out;
    }
    static HomeApps.Item editor() {
        return HomeApps.action(HomeApps.EDIT_SHORTCUTS, "edit-shortcuts", Ui.tr("Editar tela inicial", "Edit home screen"), "shortcut_edit");
    }
    static String id(HomeApps.Item item) {
        if (item.kind == HomeApps.APP || item.kind == HomeApps.ALL_APPS) return null;
        return ShortcutOrder.valid(item.key) ? item.key : null;
    }
}
