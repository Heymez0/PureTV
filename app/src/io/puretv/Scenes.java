package io.puretv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

/**
 * Landscape photos behind the home screen's clock (Settings → Appearance → Background photo). Five bundled photos,
 * Wikimedia Commons featured pictures under CC BY-SA, credited on screen and in the notices. Two have a "front": the
 * rocks cut out of the photo, drawn over the clock so it sits behind them.
 * Everything here is decoded on a background thread; drawing only uses the prepared bitmaps.
 */
final class Scenes {
    static final String AUTO = "auto", CLASSIC = "classic";
    /** The clock's digit weights offered in Settings → Clock and weather → Clock weight. */
    static final int[] WEIGHTS = {300, 400, 600};

    static final class Scene {
        final String id, name, author, licence;
        /** Where the clock's box starts, in the 1920×1080 space (its baseline is 259 lower at 300 px). */
        final int clockTop;
        /** A night photo: chosen after sunset in Automatic, and its digits are lifted more. */
        final boolean night;
        /** The front cut-out's place in the 1920×1080 space; fw = 0 without one. */
        final int fx, fy, fw, fh;
        Scene(String id, String name, String author, String licence, int clockTop, boolean night, int fx, int fy, int fw, int fh) {
            this.id = id; this.name = name; this.author = author; this.licence = licence; this.clockTop = clockTop;
            this.night = night; this.fx = fx; this.fy = fy; this.fw = fw; this.fh = fh;
        }
        boolean hasFront() { return fw > 0; }
        /** The on-screen credit (CC BY-SA asks for the author and the licence). */
        String credit() { return author + " · " + licence; }
        /** Each name literally, so tools/translate.py finds it. */
        String label() {
            switch (id) {
                case "clouds": return Ui.tr("Sea of clouds");
                case "castle": return Ui.tr("Eilean Donan Castle");
                case "peaks": return Ui.tr("Misty peaks");
                case "milkyway": return Ui.tr("Milky Way");
                default: return Ui.tr("Lighthouse at night");
            }
        }
    }

    static final Scene[] ALL = {
        new Scene("clouds", "Sea of clouds", "Dmytro Balkhovitin", "CC BY-SA 4.0", 190, false, 360, 283, 1200, 190),
        new Scene("castle", "Eilean Donan Castle", "Diliff", "CC BY-SA 3.0", 112, false, 0, 0, 0, 0),
        new Scene("peaks", "Misty peaks", "Carlos Perez Couto", "CC BY-SA 3.0", 112, false, 0, 0, 0, 0),
        new Scene("milkyway", "Milky Way", "EduardoMSNeves", "CC BY-SA 4.0", 280, true, 360, 459, 1200, 104),
        new Scene("lighthouse", "Lighthouse at night", "Antoine Lamielle", "CC BY-SA 4.0", 112, true, 0, 0, 0, 0),
    };

    private Scenes() {}

    /** The choices in Settings, in order: Automatic, each photo, Classic colours. */
    static String[] choices() {
        String[] out = new String[ALL.length + 2];
        out[0] = AUTO;
        for (int i = 0; i < ALL.length; i++) out[i + 1] = ALL[i].id;
        out[out.length - 1] = CLASSIC;
        return out;
    }

    /** A stored clock weight, or 300 when it isn't one of WEIGHTS. */
    static int weight(String value) {
        for (int allowed : WEIGHTS) if (Integer.toString(allowed).equals(value)) return allowed;
        return WEIGHTS[0];
    }

    static String normalize(String value) {
        if (AUTO.equals(value) || CLASSIC.equals(value)) return value;
        return byId(value) != null ? value : AUTO;
    }

    static Scene byId(String id) {
        for (Scene s : ALL) if (s.id.equals(id)) return s;
        return null;
    }

    /** What a choice is called in Settings. */
    static String label(String choice) {
        if (AUTO.equals(choice)) return Ui.tr("Automatic");
        if (CLASSIC.equals(choice)) return Ui.tr("Classic colours");
        Scene s = byId(choice);
        return s == null ? "" : s.label();
    }

    /**
     * The photo for a choice right now; null for Classic colours. Automatic takes a new day photo each day and one of
     * the night photos after sunset (`dark`), so the same photo stays all day or all night.
     */
    static Scene pick(String choice, boolean dark, long now) {
        choice = normalize(choice);
        if (CLASSIC.equals(choice)) return null;
        if (!AUTO.equals(choice)) return byId(choice);
        int count = 0;
        for (Scene s : ALL) if (s.night == dark) count++;
        long day = Math.floorDiv(now + java.util.TimeZone.getDefault().getOffset(now), 86_400_000L);
        int want = (int) Math.floorMod(day, (long) Math.max(1, count));
        for (Scene s : ALL) if (s.night == dark && want-- == 0) return s;
        return ALL[0];
    }

    /** A photo decoded and prepared for the home screen. */
    static final class Loaded {
        final Scene scene;
        final Bitmap photo, glow, front;
        final Backdrop glass;
        Loaded(Scene scene, Bitmap photo, Bitmap glow, Bitmap front, Backdrop glass) {
            this.scene = scene; this.photo = photo; this.glow = glow; this.front = front; this.glass = glass;
        }
    }

    /** Background thread only. `width`: the screen's width (the photo is decoded at most at 1920). */
    static Loaded load(Context c, Scene s, int width) throws IOException {
        Bitmap photo = decode(c, "scenes/" + s.id + ".webp", width >= 1600 ? 1 : 2);
        Bitmap glow = Backdrop.glow(photo, s.night);
        Backdrop glass = Backdrop.scenery(photo);   // reads the photo; it stays usable
        Bitmap front = s.hasFront() ? decode(c, "scenes/" + s.id + "-front.webp", 1) : null;
        return new Loaded(s, Backdrop.gpu(photo), glow, front == null ? null : Backdrop.gpu(front), glass);
    }

    /** A small copy for previews (Settings, setup): 480×270. */
    static Bitmap thumb(Context c, Scene s) {
        try {
            return decode(c, "scenes/" + s.id + ".webp", 4);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static final java.util.Map<String, Bitmap> THUMBS = new java.util.HashMap<>();

    /** The preview of a photo, kept while the process lives (five photos at 480×270: about 2.6 MB). */
    static synchronized Bitmap cachedThumb(Context c, Scene s) {
        Bitmap b = THUMBS.get(s.id);
        if (b == null || b.isRecycled()) {
            b = thumb(c, s);
            if (b != null) THUMBS.put(s.id, b);
        }
        return b;
    }

    /** The preview if it is already decoded (drawing never decodes), else null. */
    static synchronized Bitmap peekThumb(Scene s) {
        Bitmap b = THUMBS.get(s.id);
        return b == null || b.isRecycled() ? null : b;
    }

    private static Bitmap decode(Context c, String asset, int sample) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = c.getAssets().open(asset)) {
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (b == null) throw new IOException("unreadable " + asset);
            return b;
        }
    }
}
