package io.puretv;

/** Pure bounds and filtering shared with the regression tests. */
final class WatchNextRules {
    static final int SCAN_LIMIT = 240, ITEM_LIMIT = 24;
    private WatchNextRules() {}
    static boolean packageName(String value) {
        return value != null && value.length() <= 255 && value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }
    static boolean recent(int type, boolean visible, long at, long now) {
        // Continue and the next episode are useful here; watchlists and promotional/new-content rows are not history.
        return visible && (type == 0 || type == 1) && at > 0 && at <= now + 300_000L;
    }
    static String text(String value, int max) {
        if (value == null) return "";
        value = value.trim();
        return value.length() > max ? value.substring(0, max) : value;
    }
    static float progress(long position, long duration) {
        return duration > 0 && position > 0 ? Math.min(1f, (float) position / duration) : 0;
    }
}
