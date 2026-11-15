package io.puretv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The look shared by every settings page, the quick panel, the popups and the welcome guide: the Ambient backdrop,
 * panels lit from above, one focus (a bright surface with a soft shadow, the same on every screen) and the gold
 * switch. Static and inexpensive: each call is a few rounded rectangles; the backdrop is one stretched bitmap.
 */
final class SettingsSurface {
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shadows shadows = new Shadows();
    private final Matrix matrix = new Matrix();
    // Unit-height gradients placed over each panel by a matrix: nothing is allocated while drawing.
    private final LinearGradient sheenShader = new LinearGradient(0, 0, 0, 1,
            new int[] {0x16FFFFFF, 0x06FFFFFF, 0x00FFFFFF}, new float[] {0, 0.5f, 1}, Shader.TileMode.CLAMP);
    private final LinearGradient rimOnDark = new LinearGradient(0, 0, 0, 1, 0x4DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP);
    private final LinearGradient rimOnLight = new LinearGradient(0, 0, 0, 1, 0xF2FFFFFF, 0x1F000000, Shader.TileMode.CLAMP);
    private final RectF screen = new RectF(), box = new RectF();
    private Bitmap backdrop;
    boolean dark = true;
    /** Text; secondary text; the focused surface and the text on it; hairlines; the fill of a well (icon box, field). */
    int ink = Look.PAPER, dim = Look.DIM_ON_DARK, focusFill, focusInk, focusDim, line, well;
    private int phase = -1;
    private boolean ready;
    private float k = 1;

    void size(Context c, int width, int height) {
        k = Math.min(width / 1920f, height / 1080f);
        screen.set(0, 0, width, height);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1, 1.5f * k));
        shade.setMaskFilter(new BlurMaskFilter(BLUR * k, BlurMaskFilter.Blur.NORMAL));
        ready = false;
        refresh(c);
    }

    private static final float BLUR = 26;

    /** Follows the theme and the time of day. Call outside drawing; true when the colours changed. */
    boolean refresh(Context c) {
        long now = System.currentTimeMillis();
        boolean next = HomeData.get(c).isNight(now);
        int nextPhase = Ambient.phase(now);
        if (ready && dark == next && phase == nextPhase) return false;
        dark = next;
        phase = nextPhase;
        ready = true;
        ink = dark ? Look.PAPER : Look.INK;
        dim = dark ? Look.DIM_ON_DARK : Look.DIM_ON_LIGHT;
        focusFill = dark ? Look.PAPER : 0xFF28282D;
        focusInk = dark ? 0xFF1C1C1E : Look.PAPER;
        focusDim = dark ? 0xFF505056 : 0xFFD4D4DC;
        line = dark ? 0x1FFFFFFF : 0x1A000000;
        well = dark ? 0x17FFFFFF : 0x12000000;
        backdrop = Ambient.get(dark, phase);
        return true;
    }

    void draw(Canvas c, int width, int height) {
        if (backdrop == null) { c.drawColor(dark ? 0xFF111113 : 0xFFF3F3F5); return; }
        screen.set(0, 0, width, height);
        c.drawBitmap(backdrop, null, screen, image);
    }

    /** A page's panel: translucent over the backdrop, with a soft shadow below it. */
    void panel(Canvas c, RectF r, float radius) {
        surface(c, r, radius, dark ? 0xB8121216 : 0xBFFFFFFF, dark ? 0x59000000 : 0x1F000000, 14);
    }

    /** A popup over another screen: nearly opaque, lifted higher. */
    void popup(Canvas c, RectF r, float radius) {
        surface(c, r, radius, dark ? 0xF418181C : 0xF7F8F6F2, dark ? 0x8C000000 : 0x38000000, 20);
    }

    private void surface(Canvas c, RectF r, float radius, int color, int shadow, float drop) {
        box.set(r);
        box.offset(0, drop * k);
        shade.setColor(shadow);
        shadows.draw(c, box, radius, BLUR * k, shade);
        fill.setColor(color);
        c.drawRoundRect(r, radius, radius, fill);
        boolean plain = Prefs.lightweight();
        if (dark && !plain) {
            sheen.setShader(over(sheenShader, r));
            c.drawRoundRect(r, radius, radius, sheen);
        }
        if (plain) {
            rim.setShader(null);
            rim.setColor(dark ? 0x33FFFFFF : 0x26000000);
        } else {
            rim.setShader(over(dark ? rimOnDark : rimOnLight, r));
            rim.setAlpha(255);
        }
        c.drawRoundRect(r, radius, radius, rim);
    }

    /** The focus, the same everywhere: the bright surface with a soft shadow; `amount` (0 to 1) fades it in. */
    void focus(Canvas c, RectF r, float radius, float amount) {
        if (amount <= 0.01f) return;
        box.set(r);
        box.offset(0, 8 * k * amount);
        shade.setColor(Ui.alpha(dark ? 0x73000000 : 0x40000000, amount));
        shadows.draw(c, box, radius, BLUR * k, shade);
        fill.setColor(Ui.alpha(focusFill, amount));
        c.drawRoundRect(r, radius, radius, fill);
    }

    /** A well: the quiet fill behind an icon, a key or a text field; `focused` (0 to 1) when it sits on the focus. */
    void well(Canvas c, RectF r, float radius, float focused) {
        fill.setColor(Look.mix(well, dark ? 0x1A000000 : 0x26FFFFFF, focused));
        c.drawRoundRect(r, radius, radius, fill);
    }

    /** A hairline from `left` to `right` at `y`; `alpha` (0 to 1) fades it. */
    void hairline(Canvas c, float left, float right, float y, float alpha) {
        if (alpha <= 0.01f) return;
        fill.setColor(Ui.alpha(line, alpha));
        c.drawRect(left, y, right, y + Math.max(1, k), fill);
    }

    /** Width of the switch drawn by toggle(), in pixels. */
    float toggleWidth() {
        return 54 * k;
    }

    /**
     * A switch whose right edge is at `right`, centred on `cy`: gold when on. `enabled` false dims it (the state is
     * changing); `focused` (0 to 1) is how much of the bright focus surface it sits on.
     */
    void toggle(Canvas c, float right, float cy, boolean on, boolean enabled, float focused) {
        float w = toggleWidth(), h = 30 * k;
        box.set(right - w, cy - h / 2, right, cy + h / 2);
        int off = Look.mix(dark ? 0xFF56565C : 0xFFB6B3AC, dark ? 0xFFC2C2C8 : 0xFF63636A, focused);
        fill.setColor(on ? enabled ? Look.GOLD : Look.mix(Look.GOLD, off, 0.5f) : off);
        c.drawRoundRect(box, h / 2, h / 2, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(on ? box.right - h / 2 : box.left + h / 2, cy, h / 2 - 3.5f * k, fill);
    }

    /** A progress track from `left` to `right` centred on `cy`, filled in gold up to `part` (0 to 1; below 0: empty). */
    void meter(Canvas c, float left, float right, float cy, float part, float focused) {
        float h = 6 * k;
        box.set(left, cy - h / 2, right, cy + h / 2);
        fill.setColor(Look.mix(dark ? 0xFF4B4B50 : 0xFFCFCCC6, dark ? 0xFFC9C9CE : 0xFF5E5E64, focused));
        c.drawRoundRect(box, h / 2, h / 2, fill);
        if (part <= 0) return;
        box.right = left + (right - left) * Math.min(1, part);
        fill.setColor(Look.mix(Look.GOLD, Look.GOLD_DEEP, focused * (dark ? 1 : 0)));
        c.drawRoundRect(box, h / 2, h / 2, fill);
    }

    private LinearGradient over(LinearGradient unit, RectF r) {
        matrix.setScale(1, Math.max(1, r.height()));
        matrix.postTranslate(0, r.top);
        unit.setLocalMatrix(matrix);
        return unit;
    }
}
