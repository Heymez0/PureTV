package io.puretv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * A small picture of the home screen with a background and a clock weight, for Settings and the setup guide: the photo
 * (or the classic colours), the clock at its place and weight, and the shelf and buttons as plain shapes. An
 * illustration only; the thumbnails come from Scenes' cache, made when the choice changes, never while drawing.
 */
final class ScenePreview {
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG), IMAGE = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final Paint TIME = new Paint(Paint.ANTI_ALIAS_FLAG), DATE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path CLIP = new Path();
    private static final RectF PART = new RectF();

    private ScenePreview() {}

    /** The photo for a choice as shown right now (Automatic: today's), or null for the classic colours. */
    static Bitmap image(Context c, String choice) {
        long now = System.currentTimeMillis();
        Scenes.Scene s = Scenes.pick(choice, HomeData.get(c).isNight(now), now);
        return s == null ? null : Scenes.cachedThumb(c, s);
    }

    /**
     * `photo`: from image() (null: the classic colours, `classic` drawn instead); `clockTop`: the clock's place in the
     * 1920×1080 space; `dark`: the surface around it is dark (the classic preview follows it).
     */
    static void draw(Context c, Canvas canvas, RectF box, Bitmap photo, Bitmap classic, int clockTop, int weight, boolean dark, String time) {
        float u = box.width() / 1920f, r = 22 * u * 3;
        canvas.save();
        CLIP.reset();
        CLIP.addRoundRect(box, r, r, Path.Direction.CW);
        canvas.clipPath(CLIP);
        boolean onPhoto = photo != null;
        if (onPhoto) canvas.drawBitmap(photo, null, box, IMAGE);
        else if (classic != null) canvas.drawBitmap(classic, null, box, IMAGE);
        else { FILL.setColor(dark ? 0xFF1C1C1E : 0xFFE9E7E2); canvas.drawRect(box, FILL); }
        int ink = onPhoto || dark ? 0xFFFFFFFF : 0xFF1D1D1F;
        // The shelf and its apps, the buttons and (on a photo) the weather pill: shapes only.
        piece(canvas, box, u, 128, 820, 1792, 1012, 64, Ui.alpha(onPhoto || dark ? 0xFFFFFFFF : 0xFF000000, 0.16f));
        for (int i = 0; i < 6; i++) piece(canvas, box, u, 170 + i * 272, 858, 170 + i * 272 + 224, 976, 22, Ui.alpha(ink, 0.34f));
        piece(canvas, box, u, 1432, 69, 1500, 137, 34, Ui.alpha(ink, 0.24f));
        piece(canvas, box, u, 1518, 69, 1586, 137, 34, Ui.alpha(ink, 0.24f));
        piece(canvas, box, u, 1604, 69, 1840, 137, 34, Ui.alpha(ink, 0.24f));
        piece(canvas, box, u, 1778, 75, 1834, 131, 28, Look.GOLD);
        if (onPhoto) piece(canvas, box, u, 80, 69, 470, 137, 34, Ui.alpha(ink, 0.24f));
        // The clock: centred on a photo, at the left in the classic layout (as on the home screen).
        TIME.setTypeface(Ui.display(weight));
        TIME.setColor(Ui.alpha(ink, onPhoto ? 0.95f : 1));
        if (onPhoto) {
            TIME.setTextSize(300 * u);
            TIME.setLetterSpacing(-0.025f);
            TIME.setTextAlign(Paint.Align.CENTER);
            TIME.setShadowLayer(18 * u, 0, 4 * u, 0x40000000);
            canvas.drawText(time, box.left + 960 * u, box.top + (clockTop + 259) * u, TIME);
            DATE.setColor(Ui.alpha(ink, 0.9f));
            piece(canvas, box, u, 840, clockTop - 34, 1080, clockTop - 14, 8, Ui.alpha(ink, 0.7f));
        } else {
            TIME.setTextSize(236 * u);
            TIME.setLetterSpacing(-0.035f);
            TIME.setTextAlign(Paint.Align.LEFT);
            TIME.clearShadowLayer();
            piece(canvas, box, u, 84, 292, 430, 316, 6, Ui.alpha(ink, 0.34f));
            canvas.drawText(time, box.left + 76 * u, box.top + 536 * u, TIME);
        }
        canvas.restore();
        FILL.setStyle(Paint.Style.STROKE);
        FILL.setStrokeWidth(Math.max(1, 3 * u));
        FILL.setColor(dark ? 0x33FFFFFF : 0x1F000000);
        canvas.drawRoundRect(box, r, r, FILL);
        FILL.setStyle(Paint.Style.FILL);
    }

    private static void piece(Canvas c, RectF box, float u, float l, float t, float r, float b, float radius, int color) {
        PART.set(box.left + l * u, box.top + t * u, box.left + r * u, box.top + b * u);
        FILL.setColor(color);
        c.drawRoundRect(PART, radius * u, radius * u, FILL);
    }
}
