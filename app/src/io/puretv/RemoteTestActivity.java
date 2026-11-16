package io.puretv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shows what each remote button sends, and lets a button be given a job. Back does not leave (it is a button to test
 * too): holding it for two seconds does. A choice is only kept in this screen until Save changes; OK, Back and the
 * arrows can never be reassigned (RemoteKeyRules). No key history is stored.
 */
public class RemoteTestActivity extends Activity {
    private static final long EXIT_HOLD_MS = 2_000;
    private static final int INFO = 6, ASSIGN = 6, SAVE = 7;
    /** While this file is fresh, the key service lets every key through, so an assigned button can be tested too. */
    static final String MARKER = "remote-test";
    /** A key the key service took before Android could act on it (Home, the apps button): shown here. */
    private static final String KEY = "io.puretv.REMOTE_TEST_KEY";
    /** The marker is touched this often while the test is on screen; the key service trusts it for FRESH_MS only. */
    private static final long BEAT_MS = 3_000;
    static final long FRESH_MS = 10_000;
    private final Runnable beat = new Runnable() {
        @Override public void run() { touchMarker(); if (visible) main.postDelayed(this, BEAT_MS); }
    };

    /** From the key service (its own process): hands the key to the test on screen. Same app only (signature permission). */
    static void forward(android.content.Context c, KeyEvent event) {
        c.sendBroadcast(new android.content.Intent(KEY).setPackage(c.getPackageName()).putExtra("event", event),
                AirPlayInstall.STATUS_PERMISSION);
    }

    private final android.content.BroadcastReceiver forwarded = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, android.content.Intent intent) {
            KeyEvent event = intent.getParcelableExtra("event");
            if (event == null || !visible) return;
            boolean first = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
            if (picking) { if (first && event.getKeyCode() == KeyEvent.KEYCODE_HOME) showTest(ASSIGN); return; }   // Home leaves the list, like Back
            if (first) record(event);
        }
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable exit = this::finish;
    private final Map<Integer, String> pending = new LinkedHashMap<>();
    private Map<Integer, String> saved = new LinkedHashMap<>();
    private final String[] info = {"—", "—", "—", "—", "—", "—"};
    private List<String[]> apps = Collections.emptyList();   // {package, name}
    private SettingsPage page;
    private SettingsPage.Data data;
    private int lastKey;          // the last button pressed that may be given a job (the arrows and OK move through this screen)
    private String lastName = "";
    private String note = "";
    private boolean picking, visible, alive = true;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Watchdog.install(this);
        Ui.init(this);
        saved = new LinkedHashMap<>(Prefs.keyMap());
        showTest(0);
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            final List<String[]> found = new ArrayList<>();
            try {
                for (String[] a : HomeApps.launchable(app)) if (!a[0].equals(app.getPackageName()) && !AirPlayReceiver.THEMED.equals(a[0])) found.add(a);
                Collections.sort(found, (a, b) -> a[1].toLowerCase(Locale.ROOT).compareTo(b[1].toLowerCase(Locale.ROOT)));
            } catch (RuntimeException ignored) { }
            main.post(() -> { if (alive) { apps = found; if (page != null) page.changed(); } });
        }, "remote-test-apps").start();
    }

    private void show(SettingsPage.Data next, Runnable update, int selected) {
        if (page != null) page.pause();
        data = next;
        page = new SettingsPage(this, data, update);
        page.select(selected);
        setContentView(page);
        if (visible) page.resume();
    }

    // MARK: - The test

    private void showTest(int selected) {
        picking = false;
        String[] names = {"Button", "Key code", "Scan code", "Input device", "Source", "Home screen action"};
        SettingsPage.Row[] rows = new SettingsPage.Row[INFO + 2];
        for (int i = 0; i < INFO; i++) rows[i] = new SettingsPage.Row(Ui.tr(names[i]), null);
        rows[ASSIGN] = new SettingsPage.Row(Ui.tr("Assign this button"), d -> assign());
        rows[SAVE] = new SettingsPage.Row(Ui.tr("Save changes"), d -> save());
        SettingsPage.Data test = new SettingsPage.Data(Ui.tr("Remote button test"), Ui.tr("Hold Back for two seconds to leave."), "remote", rows);
        test.footer = "";   // said once, in gold under the title
        test.urgent = true;
        test.hint = Ui.tr("Press any button to see what reaches PureTV. Back doesn't leave here, and while PureTV is the default home screen neither does Home; the apps button is shown too. Power, volume and voice may be handled by Android before the app sees them.");
        // How to give a button a job: shown on the two rows that do it, so the screen's own hint stays short enough to fit.
        rows[ASSIGN].hint = rows[SAVE].hint = Ui.tr("To give a button a job, press it, choose Assign this button, then Save changes. Nothing changes until you save. OK, Back and the arrows can't be reassigned. Assigned buttons work while PureTV is the default home screen.");
        show(test, () -> {
            for (int i = 0; i < INFO; i++) data.rows[i].value = info[i];
            data.rows[ASSIGN].value = lastKey == 0 ? "—"
                    : lastName + " → " + label(effective(lastKey)) + (pending.containsKey(lastKey) ? " · " + Ui.tr("not saved") : "");
            data.rows[SAVE].value = pending.isEmpty() ? note : Integer.toString(pending.size());
        }, selected);
    }

    private void record(KeyEvent event) {
        int key = event.getKeyCode();
        info[0] = KeyEvent.keyCodeToString(key).replace("KEYCODE_", "");
        if (RemoteKeyRules.remappable(key)) { lastKey = key; lastName = info[0]; }
        info[1] = Integer.toString(key);
        info[2] = Integer.toString(event.getScanCode());
        InputDevice device = event.getDevice();
        info[3] = device == null ? "—" : TvInputRules.clean(device.getName());
        info[4] = "0x" + Integer.toHexString(event.getSource());
        boolean controller = (event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        int action = TvRemoteRules.action(Prefs.television(), Prefs.tvRemote(), controller, key);
        info[5] = action >= TvRemoteRules.HDMI1 ? "HDMI " + (action - TvRemoteRules.HDMI1 + 1)
                : Ui.tr(action == TvRemoteRules.INPUTS ? "TV inputs" : action == TvRemoteRules.LIVE ? "Live TV" : action == TvRemoteRules.QUICK ? "Quick settings" : "System / unchanged");
        if (!pending.isEmpty() || key != KeyEvent.KEYCODE_DPAD_CENTER) note = "";
        page.changed();
    }

    // MARK: - Giving a button a job

    /** The job in this screen: the unsaved choice, else the saved one, else the default. "" = left to the TV. */
    private String effective(int key) {
        if (pending.containsKey(key)) return pending.get(key);
        String action = RemoteKeyRules.resolve(saved, key);
        return action == null ? RemoteKeyRules.NONE : action;
    }

    private String label(String action) {
        if (action.isEmpty()) return Ui.tr("Leave to the TV");
        if (action.equals(RemoteKeyRules.ALL)) return Ui.tr("Todos os Apps", "All Apps");
        if (action.equals(RemoteKeyRules.QUICK)) return Ui.tr("Quick settings");
        if (action.equals(RemoteKeyRules.SETTINGS)) return Ui.tr("Ajustes do Início", "Home Settings");
        String pkg = action.substring(RemoteKeyRules.APP.length());
        for (String[] app : apps) if (pkg.equals(app[0])) return app[1];
        return pkg;
    }

    private void assign() {
        if (lastKey == 0) { note = Ui.tr("Press a button first."); page.select(SAVE); page.changed(); return; }
        final int key = lastKey;
        List<String> actions = new ArrayList<>();
        actions.add(RemoteKeyRules.NONE);
        actions.add(RemoteKeyRules.ALL);
        actions.add(RemoteKeyRules.QUICK);
        actions.add(RemoteKeyRules.SETTINGS);
        for (String[] app : apps) actions.add(RemoteKeyRules.APP + app[0]);
        String current = effective(key);
        SettingsPage.Row[] rows = new SettingsPage.Row[actions.size()];
        int selected = 0;
        for (int i = 0; i < rows.length; i++) {
            final String action = actions.get(i);
            rows[i] = new SettingsPage.Row(label(action), d -> { pending.put(key, action); note = ""; showTest(SAVE); });
            rows[i].choose(action.equals(current));
            if (action.equals(current)) selected = i;
        }
        SettingsPage.Data picker = new SettingsPage.Data(lastName, Ui.tr("Assign this button"), "remote", rows);
        picker.footer = "";
        picker.hint = Ui.tr("OK chooses; the change is kept only after Save changes. Back returns without choosing.");
        picking = true;
        show(picker, () -> { }, selected);
    }

    private void save() {
        if (pending.isEmpty()) return;
        Map<Integer, String> merged = new LinkedHashMap<>(saved);
        merged.putAll(pending);
        // A choice that only repeats what the button does anyway needs no entry.
        merged.entrySet().removeIf(e -> e.getValue().isEmpty() && e.getKey() != RemoteKeyRules.KEY_ALL_APPS);
        if (merged.size() > RemoteKeyRules.MAX) { note = Ui.tr("Too many buttons. Set one back to Leave to the TV first."); page.changed(); return; }
        if (Prefs.setKeyMap(merged)) {
            saved = merged;
            pending.clear();
            note = Ui.tr("Saved.");
            HomeMode.ensureRemoteService(getApplicationContext(), 0);   // the key service, where this TV allows it
        } else note = Ui.tr("Couldn't save. Try again.");
        page.changed();
    }

    // MARK: - Keys

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (picking) {   // an ordinary list: Back goes back to the test
            if (key == KeyEvent.KEYCODE_BACK) { if (!down && !event.isCanceled()) showTest(ASSIGN); return true; }
            return page.onKey(event) || super.dispatchKeyEvent(event);
        }
        if (down && event.getRepeatCount() == 0) record(event);
        if (key == KeyEvent.KEYCODE_BACK) {
            if (down && event.getRepeatCount() == 0) { main.removeCallbacks(exit); main.postDelayed(exit, EXIT_HOLD_MS); }
            else if (!down) main.removeCallbacks(exit);
            return true;
        }
        // The arrows and OK also move through this screen; other keys still go on to Android, never swallowed by a test.
        return page.onKey(event) || super.dispatchKeyEvent(event);
    }

    private File marker() { return new File(getFilesDir(), MARKER); }

    private void touchMarker() {
        try { File f = marker(); if (!f.createNewFile()) //noinspection ResultOfMethodCallIgnored
            f.setLastModified(System.currentTimeMillis()); } catch (java.io.IOException | RuntimeException ignored) { }
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        AirPlayInstall.listen(this, forwarded, KEY);
        main.removeCallbacks(beat);
        beat.run();
        page.resume();
    }

    @Override protected void onPause() {
        visible = false;
        main.removeCallbacks(beat);
        try { unregisterReceiver(forwarded); } catch (IllegalArgumentException ignored) { }
        main.removeCallbacks(exit);
        //noinspection ResultOfMethodCallIgnored
        marker().delete();
        page.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        alive = false;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
