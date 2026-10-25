package io.puretv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.View;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Original, unboxed clock/photo layout with the newer typefaces. Any key exits. */
final class DreamView extends View implements Choreographer.FrameCallback, HomeData.Listener, Recs.Listener {
    private static final long SHOW_MS = 30_000, FADE_MS = 3_000, FIRST_MS = 1_600;
    private static final float ZOOM = 0.1f;
    /** Photos 10% larger than the screen (the zoom goes up to 1.1 without upscaling). */
    private static final int PW = 2112, PH = 1188;
    private static final float MARGIN = 80, RIGHT = 1840, TITLE_W = 900, META_BASE = 1008;
    private static final float DATE_BASE=92,CLOCK_TOP=120;
    private static final double DRIFT_PERIOD_S = 600;

    /** One photo on screen: the image, its motion (zooming in or out, toward a corner) and its caption. */
    private static final class Slide {
        final Recs.Pick pick;
        final Bitmap image;
        final boolean zoomIn;
        final float panX, panY;
        final Recording caption = Recording.create("caption");
        long startedAt;

        Slide(Recs.Pick pick, Bitmap image, int n) {
            this.pick = pick;
            this.image = image;
            zoomIn = n % 2 == 0;
            // Toward one of the corners, alternating, without repeating the previous one.
            panX = (n / 2) % 2 == 0 ? 0.8f : -0.8f;
            panY = n % 3 == 0 ? -0.6f : 0.6f;
        }
    }

    private final Paint fallbackBackground=new Paint();
    private Paint datePaint,clockPaint,weatherPaint,weatherDim,cornerDate,cornerClock;
    private final Rect clockInk=new Rect();
    private float cornerX,cornerY;
    private int themePrefs=-1;
    private final HomeData data;
    private final Recs recs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Recording content = Recording.create("clock");     // centered clock, replayed while drifting
    private final Recording chrome = Recording.create("corner");     // shared clock surface over a photo
    private final Matrix matrix = new Matrix();
    private final float[] placeA = new float[3], placeB = new float[3];
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint black = new Paint();
    private final Set<String> failed = new HashSet<>();     // Recs.key: a movie and a series can have the same ID
    private volatile boolean running;
    private boolean dirty = true, chromeDirty = true;
    private boolean fallback;       // the themed clock without a photo
    private boolean decoding;
    private volatile int generation;
    private int slides;
    private Future<?> photoTask;
    private Slide current, incoming, prepared;
    private long startedAt, firstAt, lastFrame;
    private int w, h;
    private float k = 1;
    private long minute = -1;
    private String clock = "", date = "";
    private Paint labelPaint, metaPaint;
    private TextPaint titlePaint;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            data.refreshIfStale();
            recs.refreshIfStale();
            recs.prefetchAll();
            dirty = true;
            chromeDirty = true;
            prepareNext();
            handler.postDelayed(this, 60_000);
        }
    };

    DreamView(Context c) {
        super(c);
        data = HomeData.get(c);
        recs = Recs.get(c);
        content.setUseCompositingLayer(true, null);
        black.setColor(0xFF000000);
        // Darken existing photo textures without another bitmap or full-screen rendering layer.
        photoPaint.setColorFilter(new PorterDuffColorFilter(0xFF6B6B6B,PorterDuff.Mode.MULTIPLY));
    }

    void start() {
        if (running) return;
        running = true;
        if (worker.isShutdown()) worker = Executors.newSingleThreadExecutor();
        startedAt = clock();
        minute = -1;
        lastFrame = 0;
        failed.clear();
        fallback = true;
        dirty=chromeDirty=true;themePrefs=-1;
        data.addListener(this);
        recs.addListener(this);
        data.refreshIfStale();
        recs.refreshIfStale();
        recs.prefetchAll();
        prepareNext();
        handler.postDelayed(tick, 60_000);
        Choreographer.getInstance().postFrameCallback(this);
        invalidate();
    }

    void stop() {
        if (!running) return;
        running = false;
        generation++;
        if(photoTask!=null){photoTask.cancel(true);photoTask=null;}
        decoding = false;
        Choreographer.getInstance().removeFrameCallback(this);
        handler.removeCallbacks(tick);
        data.removeListener(this);
        recs.removeListener(this);
        // The photos stay in GPU memory until the garbage collector runs: release them now (after the last frame).
        final java.util.Set<Bitmap> gone = new java.util.HashSet<>();
        for (Slide s : new Slide[] {current, incoming, prepared}) {
            if (s == null) continue;
            gone.add(s.image);
        }
        if (!gone.isEmpty()) handler.postDelayed(() -> { for (Bitmap b : gone) b.recycle(); }, 1000);
        current = incoming = prepared = null;
        slides = 0;
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
        worker.shutdownNow();
    }

    @Override public void onData() {
        dirty = true;
        chromeDirty = true;
        invalidate();
    }

    @Override public void onRecs() {
        if (running) prepareNext();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        k = w / 1920f;
        fallbackBackground.setShader(null);fallbackBackground.setColor(0xFF000000);
        datePaint=Ui.text(Ui.medium,36*k,0xFF939399);datePaint.setTextAlign(Paint.Align.CENTER);
        clockPaint=Ui.numbers(Ui.semibold,280*k,0xFFB8B8BE);clockPaint.setTextAlign(Paint.Align.CENTER);clockPaint.setLetterSpacing(-.01f);
        weatherPaint=Ui.numbers(Ui.semibold,30*k,0xFF9B9BA1);weatherDim=Ui.numbers(Ui.regular,28*k,0xFF78787F);
        cornerDate=Ui.numbers(Ui.medium,30*k,0xFF939399);cornerClock=Ui.numbers(Ui.semibold,200*k,0xFFB8B8BE);
        content.setPosition(0, 0, w, h);
        chrome.setPosition(0, 0, w, h);
        labelPaint=Ui.text(Ui.semibold,23*k,0xFF939399);
        titlePaint=new TextPaint(Ui.text(Ui.semibold,52*k,0xFFB8B8BE));
        metaPaint=Ui.text(Ui.medium,26*k,0xFF9B9BA1);
        for(Paint paint:new Paint[]{cornerDate,cornerClock,labelPaint,titlePaint,metaPaint})paint.setShadowLayer(18*k,0,2*k,0x66000000);
        minute=-1;
        dirty = true;
        chromeDirty = true;
        for (Slide s : new Slide[] {current, incoming, prepared}) if (s != null) recordCaption(s);
    }

    // MARK: - Photos

    /** Loads the next photo in the background (one at a time); once ready, it comes in at the right moment. */
    private void prepareNext() {
        if (!running || decoding || prepared != null) return;
        List<Recs.Pick> list = recs.showcase();
        if (list.isEmpty()) return;
        Slide last = incoming != null ? incoming : current;
        int start = 0;
        if (last != null) {
            for (int i = 0; i < list.size(); i++) {
                if (Recs.key(list.get(i)).equals(Recs.key(last.pick))) {
                    start = i + 1;
                    break;
                }
            }
        }
        Recs.Pick pick = null;
        for (int i = 0; i < list.size() && pick == null; i++) {
            Recs.Pick p = list.get((start + i) % list.size());
            if (!failed.contains(Recs.key(p))) pick = p;
        }
        if (pick == null) {
            if (current == null) fallback = true;
            return;
        }
        if (last != null && Recs.key(pick).equals(Recs.key(last.pick))) {
            // Only one photo: the same one again, with a different motion.
            prepared = newSlide(pick, last.image);
            return;
        }
        File file = recs.readyImage(pick);
        if (file == null) {
            failed.add(Recs.key(pick));   // the image is gone from disk: try the next one
            prepareNext();
            return;
        }
        decoding = true;
        final int gen = generation;
        final Recs.Pick chosen = pick;
        photoTask=worker.submit(() -> {
            Bitmap image = null;
            float[] light = new float[2];
            try {
                if(!photoWanted(gen))return;
                image = Backdrop.photo(file, PW, PH, light);
                if(!photoWanted(gen)){releasePhoto(image);return;}
                withScrims(image, light[0], light[1]);
                if(!photoWanted(gen)){releasePhoto(image);return;}
                image = Backdrop.gpu(image);
                if(!photoWanted(gen)){releasePhoto(image);return;}
            } catch (Exception | OutOfMemoryError e) {
                releasePhoto(image);image=null;
                AppLog.w("screensaver: photo for “" + chosen.title + "”: " + e.getMessage());
            }
            final Bitmap ready = image;
            handler.post(() -> {
                if (gen != generation) {
                    // The screensaver closed while the photo was loading: free the memory right away.
                    releasePhoto(ready);
                    return;
                }
                decoding = false;
                photoTask=null;
                if (ready == null) {
                    failed.add(Recs.key(chosen));
                    prepareNext();
                    return;
                }
                prepared = newSlide(chosen, ready);
                invalidate();
                Choreographer.getInstance().removeFrameCallback(this);
                Choreographer.getInstance().postFrameCallback(this);
            });
        });
    }
    private boolean photoWanted(int gen){return running&&gen==generation&&!Thread.currentThread().isInterrupted();}
    private static void releasePhoto(Bitmap image){
        if(image!=null&&!image.isRecycled())image.recycle();
    }

    private Slide newSlide(Recs.Pick pick, Bitmap image) {
        Slide s = new Slide(pick, image, slides++);
        if (w > 0) recordCaption(s);
        return s;
    }

    /** The prepared photo goes on screen when its turn comes (the first one right away; the others every 30 s). */
    private void advance(long up) {
        if (current == null) {
            if (prepared == null) return;
            current = prepared;
            prepared = null;
            current.startedAt = up;
            firstAt = up;
            prepareNext();
            return;
        }
        if (incoming == null && prepared != null && up >= current.startedAt + SHOW_MS) {
            incoming = prepared;
            prepared = null;
            incoming.startedAt = up;
            return;
        }
        if (incoming != null && up >= incoming.startedAt + FADE_MS) {
            Slide old = current;
            current = incoming;
            incoming = null;
            if (old.image != current.image) {
                // Once the last frame that uses them has been drawn.
                Bitmap gone = old.image;
                handler.postDelayed(() -> {
                    gone.recycle();
                }, 1000);
            }
            prepareNext();
        }
    }

    /** Photo position and scale now: zoom from 1 to 1.1 (or back) and a pan toward a corner, never showing an edge. */
    private void place(Slide s, long up, float[] out) {
        float u = Ui.clamp(((Prefs.lightweight() ? s.startedAt : up) - s.startedAt) / (float) (SHOW_MS + FADE_MS));
        float f = s.zoomIn ? u : 1 - u;
        float z = 1 + ZOOM * f;
        float rx = w * (z - 1) / 2, ry = h * (z - 1) / 2;
        out[0] = rx * (s.panX * f - 1);
        out[1] = ry * (s.panY * f - 1);
        out[2] = (float) w / s.image.getWidth() * z;
    }

    private void drawPhoto(Canvas c, Slide s, float[] place, float alpha) {
        if (alpha <= 0.004f) return;
        matrix.setScale(place[2], place[2]);
        matrix.postTranslate(place[0], place[1]);
        photoPaint.setAlpha(Math.round(255 * Math.min(1, alpha)));
        c.drawBitmap(s.image, matrix, photoPaint);
    }

    // MARK: - Frames: existing photo pacing, one-second drift steps without photos
    // Motion is slow (under half a pixel per frame). Measured on the TV: ~4 ms of GPU per frame of ours, but drawing
    // every frame, it only reaches ~50 fps (the system also composites the UI on the GPU); at 20 there's room to spare.

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if(current==null&&prepared==null){
            lastFrame=frameTimeNanos;invalidate();
            Choreographer.getInstance().postFrameCallbackDelayed(this,1000);return;
        }
        if (frameTimeNanos - lastFrame >= (Prefs.lightweight() ? 95_000_000L : 45_000_000L)) {
            lastFrame = frameTimeNanos;
            invalidate();
        }
        if (Prefs.lightweight()) Choreographer.getInstance().postFrameCallbackDelayed(this, 90);
        else Choreographer.getInstance().postFrameCallback(this);
    }

    /** The clock that drives the animations. */
    private long clock() {
        long up = SystemClock.uptimeMillis();
        return up;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (w == 0) return;
        long up = clock();
        long now = System.currentTimeMillis();
        boolean minuteChanged=now/60_000!=minute;
        if (minuteChanged || themePrefs!=Prefs.version) {
            minute = now / 60_000;
            clock = Ui.clock(now);
            date = Ui.date(now);
            fitClocks();
            dirty = true;
            chromeDirty = true;
        }
        themePrefs=Prefs.version;
        if (running) advance(up);

        float first = current == null ? 0 : fadeIn(up - firstAt, FIRST_MS);
        if (current == null || first < 1) {
            if (fallback) drawFallback(canvas, up, current == null ? 1 : 1 - first);
            else canvas.drawRect(0, 0, w, h, black);
        }
        if (current == null) return;
        if (fallback && first >= 1) fallback = false;

        place(current, up, placeA);
        float e = incoming == null ? 0 : fadeIn(up - incoming.startedAt, FADE_MS);
        drawPhoto(canvas, current, placeA, first);
        if (incoming != null) {
            place(incoming, up, placeB);
            drawPhoto(canvas, incoming, placeB, e);
        }

        if (chromeDirty) recordChrome();
        chrome.setAlpha(first);
        chrome.draw(canvas);
        drawCaptions(canvas, up);
    }

    private static float fadeIn(long elapsed, long duration) {
        float t = Ui.clamp(elapsed / (float) duration);
        return t * t * (3 - 2 * t);
    }

    /**
     * Black scrims where there is text, stronger where the photo is bright, painted once onto the photo itself (in the
     * background): each frame draws only the photo (the TV's GPU feels every full-screen layer). With the slow zoom,
     * they move a little along with the photo, which makes no difference to the text.
     */
    private static Bitmap withScrims(Bitmap photo, float lightCaption, float lightClock) {
        int w = photo.getWidth(), h = photo.getHeight();
        Canvas c = new Canvas(photo);
        Paint p = new Paint(Paint.DITHER_FLAG);
        float caption = scrimStrength(lightCaption, 0.5f), corner = scrimStrength(lightClock, 0.42f);
        p.setShader(new LinearGradient(0, h, 0, h * 0.42f,
                new int[] {black(0.55f * caption), black(0.33f * caption), black(0.11f * caption), 0},
                new float[] {0, 0.35f, 0.7f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.42f, w, h, p);
        RadialGradient around = new RadialGradient(0, h, w * 0.7f,
                new int[] {black(caption), black(0.55f * caption), black(0.15f * caption), 0},
                new float[] {0, 0.4f, 0.72f, 1}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(1, 0.62f, 0, h);
        around.setLocalMatrix(m);
        p.setShader(around);
        c.drawRect(0, h - w * 0.7f * 0.62f, w * 0.7f, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.3f, new int[] {black(0.3f), black(0.075f), 0},
                new float[] {0, 0.45f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.3f, p);
        float cx = w * 0.86f, cy = h * 0.15f;
        RadialGradient clockSide = new RadialGradient(cx, cy, w * 0.36f,
                new int[] {black(corner), black(0.6f * corner), black(0.15f * corner), 0},
                new float[] {0, 0.42f, 0.75f, 1}, Shader.TileMode.CLAMP);
        Matrix mc = new Matrix();
        mc.setScale(1, 0.72f, cx, cy);
        clockSide.setLocalMatrix(mc);
        p.setShader(clockSide);
        c.drawRect(w * 0.5f, 0, w, cy + w * 0.36f * 0.72f, p);
        return photo;
    }

    private static int black(float a) {
        return Math.round(Math.max(0, Math.min(1, a)) * 255) << 24;
    }

    private static float scrimStrength(float light, float base) {
        return Math.max(base - 0.1f, Math.min(0.78f, base + (light - 0.3f) * 0.9f));
    }

    // MARK: - Original corner placement, plain clock digits

    private void fitClocks(){
        clockPaint.setTextSize(280*k);cornerClock.setTextSize(200*k);
        float width=clockPaint.measureText(clock),available=w-160*k;
        if(width>available)clockPaint.setTextSize(clockPaint.getTextSize()*available/width);
        width=cornerClock.measureText(clock);available=Math.min(1000*k,w-160*k);
        if(width>available)cornerClock.setTextSize(cornerClock.getTextSize()*available/width);
        cornerClock.getTextBounds(clock,0,clock.length(),clockInk);
        cornerX=RIGHT*k-clockInk.right;cornerY=CLOCK_TOP*k-clockInk.top;
    }
    private void recordChrome(){
        Canvas c=chrome.beginRecording(w,h);
        try{
            float x=RIGHT*k;HomeData.Weather wx=data.weather;
            if(!Double.isNaN(wx.temp)&&Prefs.weather()){
                String temp=HomeData.degrees(wx.temp);float tw=cornerDate.measureText(temp);
                c.drawText(temp,x-tw,DATE_BASE*k,cornerDate);x-=tw+10*k;
                String symbol=Symbols.weather(wx.code,wx.isDay);float size=30*k,sw=Symbols.width(getContext(),symbol,size);
                Symbols.draw(getContext(),c,symbol,x-sw/2,DATE_BASE*k-Ui.halfCap(cornerDate),size,cornerDate.getColor());x-=sw+26*k;
            }
            c.drawText(date,x-cornerDate.measureText(date),DATE_BASE*k,cornerDate);
            c.drawText(clock,cornerX,cornerY,cornerClock);
        }finally{chrome.endRecording();}
        chromeDirty=false;
    }

    // MARK: - Photo caption (bottom left)

    private void recordCaption(Slide s) {
        Canvas c = s.caption.beginRecording(w, h);
        try {
            float x = MARGIN * k, metaBase = META_BASE * k, titleBase = metaBase - 58 * k;
            String title = s.pick.title == null ? "" : s.pick.title;
            StaticLayout t = StaticLayout.Builder.obtain(title, 0, title.length(), titlePaint, Math.round(TITLE_W * k))
                    .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false).build();
            float originY = titleBase - t.getLineBaseline(t.getLineCount() - 1);
            float capTop = originY + t.getLineBaseline(0) - Ui.cap(titlePaint);
            c.drawText(Recs.periodLabel(), x, capTop - 30 * k, labelPaint);
            c.save();
            c.translate(x - Ui.leftBearing(titlePaint, title), originY);
            t.draw(c);
            c.restore();
            if (s.pick.meta != null && !s.pick.meta.isEmpty()) c.drawText(s.pick.meta, x, metaBase, metaPaint);
        } finally {
            s.caption.endRecording();
        }
        s.caption.setPosition(0, 0, w, h);
    }

    /** The caption fades out fast; the new one rises in once the photo has partly changed (like the home screen). */
    private void drawCaptions(Canvas c, long up) {
        if (incoming == null) {
            float a = current.startedAt == firstAt ? fadeIn(up - firstAt - 700, 900) : 1;
            current.caption.setAlpha(a);
            current.caption.setTranslationY(14 * k * (1 - a));
            current.caption.draw(c);
            return;
        }
        long t = up - incoming.startedAt;
        float out = 1 - Ui.clamp(t / 500f);
        if (out > 0.004f) {
            current.caption.setAlpha(out);
            current.caption.setTranslationY(0);
            current.caption.draw(c);
        }
        float in = fadeIn(t - 1300, 900);
        if (in > 0.004f) {
            incoming.caption.setAlpha(in);
            incoming.caption.setTranslationY(14 * k * (1 - in));
            incoming.caption.draw(c);
        }
    }

    // MARK: - Original dark background and slow clock drift, without a panel

    private void drawFallback(Canvas canvas,long up,float alpha){
        float t=(up-startedAt)/1000f;
        canvas.drawRect(0,0,w,h,fallbackBackground);
        if (dirty) {
            Canvas rc = content.beginRecording(w, h);
            try {
                drawContent(rc);
            } finally {
                content.endRecording();
            }
            dirty = false;
        }
        double phase = 2 * Math.PI * t / DRIFT_PERIOD_S;
        content.setTranslationX((float) (40 * k * Math.sin(phase)));
        content.setTranslationY((float) (26 * k * Math.sin(2 * phase + 0.7)));
        content.setAlpha(alpha);
        content.draw(canvas);
    }

    private void drawContent(Canvas c) {
        float cx = w / 2f;
        float clockCap = Ui.cap(clockPaint);
        float clockBase = h * 0.5f + clockCap / 2 - 10 * k;
        c.drawText(date, cx, clockBase - clockCap - 34 * k, datePaint);
        c.drawText(clock, cx, clockBase, clockPaint);

        // Weather: symbol, temperature, high and low, centered under the clock.
        HomeData.Weather wx = data.weather;
        if (!Double.isNaN(wx.temp) && Prefs.weather()) {
            float base = clockBase + 76 * k;
            String symbol = Symbols.weather(wx.code, wx.isDay);
            String now = HomeData.degrees(wx.temp);
            String range = HomeData.highLow(wx, "   ");
            float sw = Symbols.width(getContext(), symbol, 32 * k);
            float width = sw + 12 * k + weatherPaint.measureText(now) + 18 * k + weatherDim.measureText(range);
            float x = cx - width / 2;
            Symbols.draw(getContext(), c, symbol, x + sw / 2, base - Ui.halfCap(weatherPaint), 32 * k, weatherPaint.getColor());
            x += sw + 12 * k;
            c.drawText(now, x, base, weatherPaint);
            x += weatherPaint.measureText(now) + 18 * k;
            c.drawText(range, x, base, weatherDim);
        }

    }
}
