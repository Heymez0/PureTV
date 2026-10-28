package io.puretv;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Screensaver photos from the TV itself: the pictures Android has indexed on its storage and on USB drives. Read-only,
 * only while the screensaver runs and only after you turn it on in Appearance (Android asks for the photos permission).
 * Nothing is copied, uploaded or kept: the screensaver decodes one photo at a time, as with the catalogue photos.
 */
final class LocalPhotos {
    private LocalPhotos() {}

    static final String KIND = "photo";
    private static final int LIMIT = 300, MIN_WIDTH = 1280;

    @SuppressWarnings("deprecation")
    static String permission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    static boolean permitted(Context c) {
        return c.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Up to 300 landscape photos at least 1280 px wide, newest first, then shuffled; screenshots are left out. As
     * screensaver picks (kind "photo", the path as the image, no caption). Background thread.
     */
    @SuppressWarnings("deprecation")
    static List<Recs.Pick> list(Context c) {
        List<Recs.Pick> out = new ArrayList<>();
        if (!Prefs.screensaverPhotos() || !permitted(c)) return out;
        String[] columns = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT};
        try (Cursor cursor = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns,
                null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            for (int scanned = 0; cursor != null && scanned < 2000 && out.size() < LIMIT && cursor.moveToNext(); scanned++) {
                String path = cursor.getString(0);
                int width = cursor.isNull(1) ? 0 : cursor.getInt(1), height = cursor.isNull(2) ? 0 : cursor.getInt(2);
                if (path == null || path.toLowerCase(java.util.Locale.ROOT).contains("/screenshots/")) continue;
                // Unknown sizes (some USB indexes) are kept: the decoder checks them anyway.
                if (width > 0 && height > 0 && (width < MIN_WIDTH || width < height)) continue;
                File f = new File(path);
                if (!f.isFile() || !f.canRead()) continue;
                Recs.Pick p = new Recs.Pick();
                p.id = out.size();
                p.kind = KIND;
                p.title = "";
                p.backdrop = path;
                out.add(p);
            }
        } catch (RuntimeException e) {
            AppLog.w("screensaver photos: " + e.getMessage());
        }
        Collections.shuffle(out);
        for (int i = 0; i < out.size(); i++) out.get(i).id = i;
        return out;
    }
}
