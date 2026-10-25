package io.puretv;

import java.util.ArrayList;
import java.util.List;

/** Bounded shortcut IDs; independent from the home shelf and remote-key mappings. */
final class ShortcutOrder {
    static final int MAX = 6;
    static final String DEFAULT = "all,airplay,status,settings,tv-settings";
    private ShortcutOrder() { }

    static boolean valid(String id) {
        return id != null && java.util.Arrays.asList(QuickIds.ALL).contains(id);
    }

    static List<String> read(String raw) {
        List<String> out = new ArrayList<>();
        out.add("all");
        if (raw == null || raw.length() > 2048) raw = DEFAULT;
        for (String id : raw.split(",")) {
            if (valid(id) && !out.contains(id) && out.size() < MAX) out.add(id);
        }
        return out;
    }

    static String encode(List<String> ids) { return String.join(",", read(String.join(",", ids))); }

    static boolean toggle(List<String> ids, String id) {
        if (!valid(id) || "all".equals(id)) return false;
        if (ids.remove(id)) return true;
        if (ids.size() >= MAX) return false;
        ids.add(id); return true;
    }

    static boolean move(List<String> ids, String id, int direction) {
        int at = ids.indexOf(id), next = at + Integer.signum(direction);
        if (at <= 0 || next <= 0 || next >= ids.size() || next == at) return false;
        java.util.Collections.swap(ids, at, next); return true;
    }
}
