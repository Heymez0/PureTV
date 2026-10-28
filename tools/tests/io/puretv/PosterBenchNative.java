package io.puretv;

import android.graphics.Bitmap;
import android.os.Debug;
import java.io.ByteArrayOutputStream;

/**
 * Separate Android VM (tools/device_qa.py native): time and peak native-heap growth of one poster, from JPEG bytes to
 * the prepared cards, as the art thread does it. A measurement, not a pass/fail threshold; no app state or network.
 */
public final class PosterBenchNative {
    private static volatile long peak;
    private static volatile boolean sampling;

    static void ok(boolean value) { if (!value) throw new AssertionError(); }

    private static byte[] jpeg(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double u = (double) x / w, v = (double) y / h, s = Math.sin(u * 23) * Math.cos(v * 17) * 0.5 + 0.5;
                row[x] = 0xFF000000 | (int) (255 * (0.6 * u + 0.4 * s)) << 16 | (int) (255 * (0.5 * v + 0.5 * s * s)) << 8 | (int) (255 * (1 - s) * 0.8);
            }
            b.setPixels(row, 0, w, 0, y, w, 1);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 85, out);
        b.recycle();
        return out.toByteArray();
    }

    private static void settle() throws InterruptedException {
        for (int i = 0; i < 3; i++) { System.gc(); System.runFinalization(); Thread.sleep(150); }
    }

    public static void main(String[] args) throws Exception {
        int[][] sizes = {{1280, 720}, {1920, 1080}, {3840, 2160}, {1080, 1920}};
        for (int[] size : sizes) {
            byte[] bytes = jpeg(size[0], size[1]);
            long best = Long.MAX_VALUE, sum = 0, most = 0;
            int runs = 6;
            for (int run = -2; run < runs; run++) {
                settle();
                final long base = Debug.getNativeHeapAllocatedSize();
                peak = base;
                sampling = true;
                Thread sampler = new Thread(() -> {
                    while (sampling) {
                        long now = Debug.getNativeHeapAllocatedSize();
                        if (now > peak) peak = now;
                        try { Thread.sleep(0, 200_000); } catch (InterruptedException e) { return; }
                    }
                });
                sampler.start();
                long start = System.nanoTime();
                Bitmap decoded = ContinueWatching.decodeBytes(bytes);
                ok(decoded != null);
                int decodedW = decoded.getWidth(), decodedH = decoded.getHeight();
                Backdrop poster = Backdrop.make(decoded, true);
                long ms = (System.nanoTime() - start) / 1_000_000;
                sampling = false;
                sampler.join();
                ok(poster.wide.getWidth() == Backdrop.WIDE_W && poster.tall.getWidth() == Backdrop.TALL_W);
                for (Bitmap b : new Bitmap[] {poster.wide, poster.tall, poster.lightImage, poster.nightImage}) if (b != null) b.recycle();
                if (run < 0) continue;   // warm-up
                best = Math.min(best, ms);
                sum += ms;
                most = Math.max(most, peak - base);
                if (run == 0) System.out.printf("JPEG %dx%d decoded at %dx%d%n", size[0], size[1], decodedW, decodedH);
            }
            System.out.printf("JPEG %dx%d: decode+prepare min=%d ms avg=%d ms; peak native growth=%.1f MiB%n",
                    size[0], size[1], best, sum / runs, most / 1048576.0);
        }
        System.exit(0);
    }
}
