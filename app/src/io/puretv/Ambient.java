package io.puretv;

import android.graphics.Bitmap;

import java.util.Calendar;

/**
 * The backdrop behind the home screen (when no title's photo is shown), the settings and the welcome guide: a deep,
 * even surface lit by two soft lights, a warm one at the top right and a cool one at the bottom left, whose colours
 * follow the time of day. It is computed once into a small bitmap and stretched over the screen, so drawing it costs
 * the GPU one textured rectangle, on any TV.
 * Each pixel is dithered before it is rounded to 8 bits: a slow gradient across 1080 lines otherwise shows as bands
 * on a TV panel, and stretched, the dither reads as a fine grain instead.
 */
final class Ambient {
    private Ambient() {}

    static final int W = 320, H = 180;
    static final int MORNING = 0, DAY = 1, EVENING = 2, NIGHT = 3;

    // Per phase: the warm light's colour and strength, then the cool light's (strength in thousandths).
    private static final int[][] DARK = {
        {0xFFE9A26B, 240, 0xFF3D6BD6, 200},
        {0xFFD2AB55, 210, 0xFF2F7FB8, 190},
        {0xFFE0825A, 240, 0xFF6A4BC4, 210},
        {0xFFB98A3C, 190, 0xFF3A46B0, 210},
    };
    private static final int[][] LIGHT = {
        {0xFFFFC9A0, 360, 0xFFAFD3FF, 340},
        {0xFFFFE2A6, 340, 0xFFB5DBF5, 340},
        {0xFFFFBE9C, 360, 0xFFCDBBF5, 340},
        {0xFFF3D9A4, 300, 0xFFC3CBF2, 320},
    };
    private static final int DARK_TOP = 0xFF15151A, DARK_BOTTOM = 0xFF09090B;
    private static final int LIGHT_TOP = 0xFFF7F6F3, LIGHT_BOTTOM = 0xFFE6E4E0;

    private static Bitmap dark, light;
    private static int darkPhase = -1, lightPhase = -1;

    /** Morning from 5, day from 11, evening from 17, night from 21 (the TV's own clock). */
    static int phase(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        return phaseOf(c.get(Calendar.HOUR_OF_DAY));
    }

    static int phaseOf(int hour) {
        return hour >= 5 && hour < 11 ? MORNING : hour >= 11 && hour < 17 ? DAY : hour >= 17 && hour < 21 ? EVENING : NIGHT;
    }

    /**
     * The backdrop for a theme and phase. One bitmap is kept per theme and reused until the phase changes; call it
     * outside drawing (it computes the bitmap the first time).
     */
    static synchronized Bitmap get(boolean darkTheme, int phase) {
        Bitmap b = darkTheme ? dark : light;
        if (b != null && (darkTheme ? darkPhase : lightPhase) == phase) return b;
        int[] px = new int[W * H];
        fill(px, W, H, darkTheme, phase);
        if (b == null) b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, W, 0, 0, W, H);
        if (darkTheme) { dark = b; darkPhase = phase; }
        else { light = b; lightPhase = phase; }
        return b;
    }

    /** Under real memory pressure with nothing on screen: the bitmaps are made again when needed. */
    static synchronized void trim() {
        dark = light = null;
        darkPhase = lightPhase = -1;
    }

    /** The same backdrop as opaque ARGB pixels at any size (the glass shaders sample it at theirs). */
    static void fill(int[] out, int w, int h, boolean darkTheme, int phase) {
        int[] p = (darkTheme ? DARK : LIGHT)[Math.max(0, Math.min(3, phase))];
        int top = darkTheme ? DARK_TOP : LIGHT_TOP, bottom = darkTheme ? DARK_BOTTOM : LIGHT_BOTTOM;
        float tr = (top >> 16) & 255, tg = (top >> 8) & 255, tb = top & 255;
        float br = (bottom >> 16) & 255, bg = (bottom >> 8) & 255, bb = bottom & 255;
        float wr = (p[0] >> 16) & 255, wg = (p[0] >> 8) & 255, wb = p[0] & 255, ws = p[1] / 1000f;
        float cr = (p[2] >> 16) & 255, cg = (p[2] >> 8) & 255, cb = p[2] & 255, cs = p[3] / 1000f;
        float aspect = (float) w / h, vignette = darkTheme ? 0.34f : 0.07f;
        // Distances in units of the screen height: the warm light at the top right, the cool one at the bottom left.
        float wx = 0.84f * aspect, wy = 0.06f, wRadius = 1.05f, cx = 0.06f * aspect, cy = 1.02f, cRadius = 1.15f;
        int seed = 0x2545F491;
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h, sy = v;
            float r0 = tr + (br - tr) * v, g0 = tg + (bg - tg) * v, b0 = tb + (bb - tb) * v;
            int at = y * w;
            for (int x = 0; x < w; x++) {
                float sx = (x + 0.5f) / h;
                float r = r0, g = g0, b = b0;
                float dx = sx - wx, dy = sy - wy, d = (dx * dx + dy * dy) / (wRadius * wRadius);
                if (d < 1) {
                    float f = (1 - d) * (1 - d) * ws;
                    r += (wr - r) * f; g += (wg - g) * f; b += (wb - b) * f;
                }
                dx = sx - cx; dy = sy - cy; d = (dx * dx + dy * dy) / (cRadius * cRadius);
                if (d < 1) {
                    float f = (1 - d) * (1 - d) * cs;
                    r += (cr - r) * f; g += (cg - g) * f; b += (cb - b) * f;
                }
                // Darker towards the edges, where a TV panel is dimmer anyway: the middle reads as lit.
                float ex = sx / aspect - 0.5f, ey = sy - 0.5f, edge = Math.min(1, (ex * ex + ey * ey) * 2);
                float keep = 1 - vignette * edge * edge;
                seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
                float noise = ((seed >>> 8) & 0xFFFF) / 65536f;
                out[at + x] = 0xFF000000 | (channel(r * keep + noise) << 16) | (channel(g * keep + noise) << 8)
                        | channel(b * keep + noise);
            }
        }
    }

    private static int channel(float v) {
        int i = (int) v;
        return i < 0 ? 0 : i > 255 ? 255 : i;
    }
}
