package io.puretv;

import android.app.ActivityManager;
import android.content.Context;
import java.util.Locale;
import android.os.StatFs;

/** One read-only snapshot on opening status; no polling or process inspection. */
final class ResourceStatus {
    private ResourceStatus() {}
    static final class Snapshot {
        long storageAvailable = -1, storageTotal = -1, ramAvailable = -1, ramTotal = -1;
    }
    static Snapshot read(Context context) {
        Snapshot out = new Snapshot();
        try {
            StatFs disk = new StatFs(context.getFilesDir().getAbsolutePath());
            out.storageAvailable = disk.getAvailableBytes(); out.storageTotal = disk.getTotalBytes();
        } catch (IllegalArgumentException | SecurityException ignored) {}
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memory);
                out.ramAvailable = memory.availMem; out.ramTotal = memory.totalMem;
            }
        } catch (RuntimeException ignored) {}
        return out;
    }
    static String value(Context context, long available, long total) {
        if (available < 0 || total <= 0) return "—";
        Locale locale = Ui.locale();
        return LocaleFormats.bytes(Math.min(available, total), locale) + " " + Ui.tr("disponíveis", "available")
                + " / " + LocaleFormats.bytes(total, locale);
    }
}
