package io.puretv;

/**
 * PureTV's colours in one place, taken from its icon: obsidian surfaces, paper-white ink and one gold accent. The home
 * screen, the settings, the popups and the welcome guide all read these, so a control looks the same wherever it is.
 */
final class Look {
    private Look() {}

    /** The accent: what is on, selected or in progress. Never the focus, which stays a plain bright surface. */
    static final int GOLD = 0xFFD2AB55, GOLD_DEEP = 0xFFB0822C;
    /** Text and symbols drawn on gold. */
    static final int ON_GOLD = 0xFF1B1710;

    /** Ink on a light surface, and paper: the bright surface and the ink on a dark one. */
    static final int INK = 0xFF1D1D1F, PAPER = 0xFFF5F5F7;
    /** Secondary text on a light and on a dark surface. */
    static final int DIM_ON_LIGHT = 0xFF5B5B62, DIM_ON_DARK = 0xFFA9A9AE;

    /** Status: connected or working, needs attention, and not available. */
    static final int GOOD = 0xFF30D158, WARN = 0xFFFF9F0A, MUTED = 0xFFAEAEB2;

    /** `a` moved towards `b` by `f` (0 to 1), channel by channel, alpha included. */
    static int mix(int a, int b, float f) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }
}
