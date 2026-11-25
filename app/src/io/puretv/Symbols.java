package io.puretv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Material Symbols Rounded icons (Apache 2.0), rendered by tools/make_symbols.py as white PNGs in assets/symbols. Drawn
 * like text: `pointSize` is the size of the font next to the icon, and the icon is centered on that font's cap height.
 */
final class Symbols {
    private Symbols() {}

    /**
     * PNG pixels per em of `pointSize` (a 96 pt × 1.5 px/pt reference scale). tools/make_symbols.py renders a 172 px
     * em, so an icon's em is drawn at ~1.2 × `pointSize`.
     */
    private static final float PX_PER_POINT = 96 * 1.5f;
    private static final Map<String, Bitmap> CACHE = new HashMap<>();
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final RectF RECT = new RectF();

    static Bitmap get(Context c, String name) {
        synchronized (CACHE) {
            if (CACHE.containsKey(name)) return CACHE.get(name);
            Bitmap b = null;
            if ("network".equals(name) || "shortcut_edit".equals(name) || "memory".equals(name) || "remote".equals(name)) {
                b = localIcon(name);
            } else if (LINE_ICONS.contains(name)) {
                b = lineIcon(name);
            } else try (InputStream in = c.getAssets().open("symbols/" + name + ".png")) {
                b = BitmapFactory.decodeStream(in);
                if (b != null) {
                    b = b.extractAlpha();
                    b.setHasMipMap(true);
                }
            } catch (IOException ignored) {
            }
            CACHE.put(name, b);
            return b;
        }
    }

    /** Line icons drawn here for the settings rows (no font file needed): photo, text weight, layout, lock and so on. */
    private static final java.util.Set<String> LINE_ICONS = new java.util.HashSet<>(java.util.Arrays.asList(
        "photo", "weight", "layout", "lock", "power", "palette", "clock", "pin", "thermo", "bluetooth", "mic", "storage",
        "globe", "backup", "restore", "guide", "info", "document", "bolt", "hidden", "wifi_settings", "screen"));

    /**
     * The settings icons on a 172 px canvas with the same stroke as the local ones below (11 px, round caps), so they sit
     * with the Material Symbols around them. Drawn once into the alpha cache.
     */
    private static Bitmap lineIcon(String name) {
        Bitmap image = Bitmap.createBitmap(172, 172, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(image);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setStrokeWidth(11);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        android.graphics.Path path = new android.graphics.Path();
        RectF o = new RectF();
        // Thin outlines read smaller than the filled symbols beside them, so they are drawn a little larger.
        c.scale(1.16f, 1.16f, 86, 86);
        p.setStrokeWidth(10.5f);
        switch (name) {
            case "photo":
                c.drawRoundRect(30, 40, 142, 132, 18, 18, p);
                path.moveTo(36, 118); path.lineTo(72, 82); path.lineTo(98, 108); path.lineTo(114, 92); path.lineTo(138, 116);
                c.drawPath(path, p);
                c.drawCircle(110, 68, 9, p);
                break;
            case "weight":   // an "A" beside a bold bar: how heavy the type is
                path.moveTo(30, 136); path.lineTo(70, 38); path.lineTo(78, 38); path.lineTo(118, 136);
                c.drawPath(path, p);
                c.drawLine(48, 98, 100, 98, p);
                p.setStrokeWidth(18); c.drawLine(142, 66, 142, 136, p);
                break;
            case "layout":
                c.drawRoundRect(26, 34, 146, 138, 18, 18, p);
                c.drawLine(26, 104, 146, 104, p);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 3; i++) c.drawCircle(58 + i * 28, 121, 6, p);
                break;
            case "lock":
                c.drawRoundRect(40, 78, 132, 142, 16, 16, p);
                o.set(56, 30, 116, 96); c.drawArc(o, 180, 180, false, p);
                c.drawLine(56, 63, 56, 78, p); c.drawLine(116, 63, 116, 78, p);
                break;
            case "power":
                c.drawLine(86, 28, 86, 84, p);
                o.set(36, 40, 136, 140); c.drawArc(o, -60, 300, false, p);
                break;
            case "palette":
                o.set(28, 28, 144, 144); c.drawArc(o, 60, 300, false, p);
                path.moveTo(115, 136); path.cubicTo(98, 130, 98, 112, 114, 110); path.lineTo(128, 110); path.cubicTo(138, 110, 144, 100, 144, 86);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(62, 76, 9, p); c.drawCircle(86, 56, 9, p); c.drawCircle(112, 70, 9, p); c.drawCircle(58, 106, 9, p);
                break;
            case "clock":
                c.drawCircle(86, 86, 58, p);
                path.moveTo(86, 52); path.lineTo(86, 88); path.lineTo(110, 104);
                c.drawPath(path, p);
                break;
            case "pin":
                path.moveTo(86, 146); path.cubicTo(86, 146, 36, 102, 36, 72); path.cubicTo(36, 44, 58, 24, 86, 24);
                path.cubicTo(114, 24, 136, 44, 136, 72); path.cubicTo(136, 102, 86, 146, 86, 146);
                c.drawPath(path, p); c.drawCircle(86, 72, 15, p);
                break;
            case "thermo":
                o.set(70, 22, 102, 54); c.drawArc(o, 180, 180, false, p);
                c.drawLine(70, 38, 70, 104, p); c.drawLine(102, 38, 102, 104, p);
                o.set(56, 96, 116, 156); c.drawArc(o, -60, 300, false, p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(86, 126, 13, p);
                break;
            case "bluetooth":
                path.moveTo(52, 58); path.lineTo(116, 114); path.lineTo(86, 142); path.lineTo(86, 30); path.lineTo(116, 58); path.lineTo(52, 114);
                c.drawPath(path, p);
                break;
            case "mic":
                c.drawRoundRect(64, 24, 108, 102, 22, 22, p);
                o.set(42, 54, 130, 124); c.drawArc(o, 0, 180, false, p);
                c.drawLine(86, 124, 86, 146, p);
                break;
            case "storage":
                c.drawRoundRect(30, 40, 142, 78, 12, 12, p);
                c.drawRoundRect(30, 94, 142, 132, 12, 12, p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(122, 59, 6, p); c.drawCircle(122, 113, 6, p);
                break;
            case "globe":
                c.drawCircle(86, 86, 58, p);
                c.drawLine(28, 86, 144, 86, p);
                o.set(58, 28, 114, 144); c.drawOval(o, p);
                break;
            case "backup":
            case "restore":
                path.moveTo(50, 126); path.lineTo(40, 126); path.cubicTo(18, 126, 18, 90, 42, 88); path.cubicTo(44, 58, 86, 44, 104, 70);
                path.cubicTo(128, 62, 150, 82, 142, 104); path.cubicTo(156, 110, 150, 126, 136, 126); path.lineTo(122, 126);
                c.drawPath(path, p);
                if ("backup".equals(name)) { c.drawLine(86, 140, 86, 92, p); c.drawLine(68, 110, 86, 92, p); c.drawLine(104, 110, 86, 92, p); }
                else { c.drawLine(86, 92, 86, 140, p); c.drawLine(68, 122, 86, 140, p); c.drawLine(104, 122, 86, 140, p); }
                break;
            case "guide":
                path.moveTo(86, 46); path.cubicTo(66, 32, 44, 32, 26, 40); path.lineTo(26, 132); path.cubicTo(44, 124, 66, 124, 86, 138);
                path.cubicTo(106, 124, 128, 124, 146, 132); path.lineTo(146, 40); path.cubicTo(128, 32, 106, 32, 86, 46); path.lineTo(86, 138);
                c.drawPath(path, p);
                break;
            case "info":
                c.drawCircle(86, 86, 58, p);
                c.drawLine(86, 80, 86, 118, p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(86, 56, 7, p);
                break;
            case "document":
                path.moveTo(104, 26); path.lineTo(46, 26); path.lineTo(46, 146); path.lineTo(126, 146); path.lineTo(126, 48); path.close();
                c.drawPath(path, p);
                c.drawLine(66, 84, 106, 84, p); c.drawLine(66, 110, 106, 110, p);
                break;
            case "bolt":
                path.moveTo(98, 22); path.lineTo(48, 96); path.lineTo(84, 96); path.lineTo(74, 150); path.lineTo(124, 76); path.lineTo(88, 76); path.close();
                c.drawPath(path, p);
                break;
            case "hidden":
                path.moveTo(22, 86); path.cubicTo(52, 36, 120, 36, 150, 86); path.cubicTo(120, 136, 52, 136, 22, 86);
                c.drawPath(path, p); c.drawCircle(86, 86, 20, p);
                c.drawLine(36, 136, 136, 36, p);
                break;
            case "wifi_settings":
                for (int i = 0; i < 3; i++) {
                    float r = 26 + i * 25;
                    o.set(86 - r, 132 - r, 86 + r, 132 + r);
                    c.drawArc(o, 225, 90, false, p);
                }
                p.setStyle(Paint.Style.FILL); c.drawCircle(86, 130, 8, p);
                break;
            default:   // "screen": a TV
                c.drawRoundRect(22, 36, 150, 120, 16, 16, p);
                c.drawLine(60, 144, 112, 144, p);
                break;
        }
        image.setHasMipMap(true);
        return image;
    }

    /** Small local vector symbols rasterized once, using the same alpha-bitmap cache as the other icons. */
    private static Bitmap localIcon(String name) {
        Bitmap image = Bitmap.createBitmap(172, 172, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(image);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setStrokeWidth(11);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        if ("network".equals(name)) {
            RectF oval = new RectF();
            for (int i = 0; i < 3; i++) {
                float r = 26 + i * 23;
                oval.set(86 - r, 129 - r, 86 + r, 129 + r);
                c.drawArc(oval, 225, 90, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(86, 127, 7, p);
        } else if ("memory".equals(name)) {
            c.drawRoundRect(47,47,125,125,10,10,p);
            c.drawRect(68,68,104,104,p);
            for(int i=0;i<3;i++){
                float at=59+i*27;
                c.drawLine(at,30,at,47,p);c.drawLine(at,125,at,142,p);
                c.drawLine(30,at,47,at,p);c.drawLine(125,at,142,at,p);
            }
        } else if ("remote".equals(name)) {
            c.drawRoundRect(56, 22, 116, 150, 22, 22, p);
            c.drawCircle(86, 58, 13, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 4; i++) c.drawCircle(i % 2 == 0 ? 75 : 97, i < 2 ? 98 : 122, 6, p);
        } else {
            for (int i = 0; i < 3; i++) {
                float y = 48 + i * 38;
                c.drawLine(42, y, 130, y, p);
                c.drawCircle(i == 1 ? 105 : 65, y, 11, p);
            }
        }
        image.setHasMipMap(true);
        return image;
    }

    static float width(Context c, String name, float pointSize) {
        Bitmap b = get(c, name);
        return b == null ? 0 : b.getWidth() * pointSize / PX_PER_POINT;
    }

    /** Draws centered at (cx, cy) in the given color (lighter, translucent parts of the symbol stay lighter). */
    static void draw(Context c, Canvas canvas, String name, float cx, float cy, float pointSize, int color) {
        Bitmap b = get(c, name);
        if (b == null) return;
        synchronized (PAINT) {   // card loading (another thread) also draws symbols
            drawLocked(canvas, b, cx, cy, pointSize, color);
        }
    }

    private static void drawLocked(Canvas canvas, Bitmap b, float cx, float cy, float pointSize, int color) {
        float w = b.getWidth() * pointSize / PX_PER_POINT, h = b.getHeight() * pointSize / PX_PER_POINT;
        PAINT.setColor(color);
        RECT.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        canvas.drawBitmap(b, null, RECT, PAINT);
    }

    /** Weather symbol for an Open-Meteo WMO weather code. */
    static String weather(int code, boolean day) {
        if (code <= 1) return day ? "sunny" : "clear_night";
        if (code == 2) return day ? "partly_cloudy_day" : "partly_cloudy_night";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "foggy";
        if (code >= 51 && code <= 57) return "rainy";
        if (code == 65 || code == 67 || code == 82) return "rainy";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "snowflake";
        if (code >= 95) return "thunderstorm";
        return "rainy";
    }
}
