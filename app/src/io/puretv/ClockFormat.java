package io.puretv;

/** Pure format rules. Automatic preserves the previous language-dependent display. */
final class ClockFormat {
    private ClockFormat() {}
    static long minuteDelay(long now){return 60_000L-Math.floorMod(now,60_000L);}
    static String normalize(String value) {
        return "12".equals(value) || "24".equals(value) ? value : "auto";
    }
    static boolean twelve(String mode, boolean english) {
        return "12".equals(mode) || "auto".equals(normalize(mode)) && english;
    }
    static String clock(String mode, boolean english) {
        if ("12".equals(mode)) return "h:mm a";
        return twelve(mode, english) ? "h:mm" : "HH:mm";
    }
    static String detail(String mode, boolean english) {
        return twelve(mode, english) ? "h:mm a" : "H:mm";
    }
    static String hour(String mode, boolean english) {
        return twelve(mode, english) ? "h a" : "HH:mm";
    }
}
