package io.puretv;

/** Poster geometry in source pixels, so Backdrop never builds a full-screen intermediate. No Android types. */
final class PosterCrop {
    private PosterCrop() {}

    /**
     * The centered part of a w×h image that a `screen`-shaped cover crop shows, cut again to `ratio`:
     * {left, top, width, height} in the image's own pixels.
     */
    static float[] region(int w, int h, float screen, float ratio) {
        float rw = Math.min(w, h * screen), rh = rw / screen;
        float cw = Math.min(rw, rh * ratio), ch = cw / ratio;
        return new float[] {(w - cw) / 2, (h - ch) / 2, cw, ch};
    }

    /** Halving steps after the first filtered draw, so no single step shrinks by more than half. */
    static int halvings(float region, int size) {
        int n = 0;
        while (n < 16 && region > (long) Math.max(1, size) << (n + 1)) n++;
        return n;
    }
}
