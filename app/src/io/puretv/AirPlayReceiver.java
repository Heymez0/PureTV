package io.puretv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Package presence is not a claim that the receiver is listening or visible to another device. */
final class AirPlayReceiver {
    static final String THEMED = "io.puretv.airplay";
    static final String ORIGINAL = "io.github.jqssun.airplay";
    private AirPlayReceiver() { }
    private static String receiver(Context context) {
        try { context.getPackageManager().getPackageInfo(THEMED, 0); return THEMED; }
        catch (PackageManager.NameNotFoundException | SecurityException e) { return ORIGINAL; }
    }
    private static Intent intent(Context context) {
        return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(receiver(context), ORIGINAL + ".MainActivity"));
    }
    static Intent decorate(Context context, Intent intent) {
        if (intent == null || intent.getComponent() == null || !THEMED.equals(intent.getComponent().getPackageName())) return intent;
        return intent.putExtra("puretv_sky_top", 0xFF302A23).putExtra("puretv_sky_bottom", 0xFF111113);
    }
    static String version(Context context) {
        try {
            if (intent(context).resolveActivity(context.getPackageManager()) == null) return "";
            String name = context.getPackageManager().getPackageInfo(receiver(context), 0).versionName;
            return name == null ? "" : name;
        } catch (PackageManager.NameNotFoundException | SecurityException e) { return ""; }
    }
    static boolean open(Activity activity) {
        try { activity.startActivity(decorate(activity, intent(activity))); return true; }
        catch (ActivityNotFoundException | SecurityException e) { return false; }
    }
}
