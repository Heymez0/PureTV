package io.puretv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Edits only quick_shortcuts; the home shelf retains its existing editor and preference. */
public class ShortcutSettingsActivity extends Activity {
    private final List<String> ids = new ArrayList<>(), labels = new ArrayList<>();
    private List<String> order;
    private SettingsPage page;
    private SettingsPage.Data data;
    private boolean resumed, destroyed;
    private String notice = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Watchdog.install(this);
        Ui.init(this);
        order = Prefs.quickShortcuts();
        for (String id : QuickShortcuts.BUILT_INS) {
            ids.add(id);
            labels.add(QuickShortcuts.item(this, id).label);
        }
        rebuildPage(0);
    }

    private void rebuildPage(int focused) {
        if (page != null) page.pause();
        SettingsPage.Row[] rows = new SettingsPage.Row[ids.size()];
        for (int i = 0; i < rows.length; i++) {
            final String id = ids.get(i);
            rows[i] = new SettingsPage.Row(labels.get(i), "all".equals(id) ? null : direction -> toggle(id), false);
        }
        data = new SettingsPage.Data(Ui.tr("Editar atalhos", "Edit shortcuts"),
                Ui.tr("Atalhos da gaveta inferior", "Bottom drawer shortcuts"), "shortcut_edit", rows);
        page = new SettingsPage(this, data, this::update);
        page.select(focused);
        setContentView(page);
        if (resumed) page.resume();
    }

    private void toggle(String id) {
        if (ShortcutOrder.toggle(order, id)) { Prefs.setQuickShortcuts(order); notice = ""; }
        else notice = Ui.tr("Limite de 6 atalhos. Remova um antes de adicionar outro.",
                "Limit of 6 shortcuts. Remove one before adding another.");
    }
    private void update() {
        for (int i = 0; i < ids.size(); i++) {
            int place = order.indexOf(ids.get(i));
            data.rows[i].value = "all".equals(ids.get(i)) ? Ui.tr("Fixo", "Fixed") : place >= 0 ? "#" + (place + 1) : Ui.tr("Desligado", "Off");
        }
        data.hint = (notice.isEmpty() ? "" : notice + "\n\n") + order.size() + " / " + ShortcutOrder.MAX + " · "
                + Ui.tr("OK adds or removes a shortcut. To reorder, hold OK on a shortcut on Home and choose Move on shelf.") + "\n\n"
                + Ui.tr("Somente funções integradas. Todos os Apps fica na primeira posição.",
                "Only built-in functions can be added. All Apps stays in the first position.");
    }
    @Override protected void onResume() {
        super.onResume();
        order = Prefs.quickShortcuts();
        resumed = true;
        page.resume();
    }
    @Override protected void onPause() { resumed = false; page.pause(); super.onPause(); }
    @Override protected void onDestroy() { destroyed = true; page.pause(); super.onDestroy(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return page.onKey(event) || super.dispatchKeyEvent(event);
    }
}
