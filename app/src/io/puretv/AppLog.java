package io.puretv;

import android.util.Log;

/** The app's log (adb logcat -s PureTV). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        Log.i("PureTV", message);
    }
}
