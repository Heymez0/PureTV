package io.puretv;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure rules for the buttons a person gives a job in Remote button test. A saved choice is "key=action" per line;
 * nothing here touches Android.
 */
final class RemoteKeyRules {
    static final String NONE = "", ALL = "all", QUICK = "quick", SETTINGS = "settings", APP = "app:";
    /** KEYCODE_ALL_APPS: the apps button of many TV remotes (Xiaomi's grid button). */
    static final int KEY_ALL_APPS = 284;
    static final int MAX = 16;

    private RemoteKeyRules() {}

    /**
     * OK, Back, the arrows, Home, power and volume always keep their job: a mistake there could leave the TV without a
     * way to move, confirm or leave.
     */
    static boolean remappable(int key) {
        switch (key) {
            case 3: case 4: case 111:                       // Home, Back, Escape
            case 19: case 20: case 21: case 22:             // arrows
            case 23: case 66: case 160: case 96: case 97:   // OK, Enter, and a controller's A and B
            case 24: case 25: case 164:                     // volume, mute
            case 26: case 223: case 224:                    // power, sleep, wake
                return false;
            default:
                return key > 0 && key < 10_000;
        }
    }

    /**
     * Keys Android acts on before a screen sees them (Home, the apps button, Settings). In Remote button test the key
     * service hands them to the test instead, so they can be tested too and Home doesn't leave.
     */
    static boolean shownInTest(int key) {
        return key == 3 || key == KEY_ALL_APPS || key == 176;
    }

    /** Only the known forms; anything else is "nothing". */
    static String action(String v) {
        if (v == null) return NONE;
        if (v.equals(ALL) || v.equals(QUICK) || v.equals(SETTINGS)) return v;
        if (v.startsWith(APP) && WatchNextRules.packageName(v.substring(APP.length()))) return v;
        return NONE;
    }

    static Map<Integer, String> parse(String text) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            int at = line.indexOf('=');
            if (at <= 0 || out.size() >= MAX) continue;
            int key;
            try { key = Integer.parseInt(line.substring(0, at).trim()); } catch (NumberFormatException e) { continue; }
            if (remappable(key)) out.put(key, action(line.substring(at + 1).trim()));
        }
        return out;
    }

    static String encode(Map<Integer, String> map) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<Integer, String> e : map.entrySet()) {
            if (!remappable(e.getKey()) || n++ >= MAX) continue;
            sb.append(e.getKey()).append('=').append(action(e.getValue())).append('\n');
        }
        return sb.toString();
    }

    /**
     * What the key does, or null when it is left to the TV. A saved choice wins (an empty one means "leave it");
     * without one, the remote's apps button opens All Apps, since the TV's own launcher no longer answers it.
     */
    static String resolve(Map<Integer, String> map, int key) {
        if (!remappable(key)) return null;
        String v = map.get(key);
        if (v != null) return v.isEmpty() ? null : v;
        return key == KEY_ALL_APPS ? ALL : null;
    }
}
