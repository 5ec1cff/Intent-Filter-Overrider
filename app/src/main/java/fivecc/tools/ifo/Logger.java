package fivecc.tools.ifo;

import android.util.Log;

public class Logger {
    private static final String TAG = "FIVECC-IFO";

    public static void d(String text) {
        // XposedBridge.log("[" + TAG + "] " + text);
        Log.d(TAG, text);
    }

    public static void e(String text, Throwable t) {
        // XposedBridge.log("[" + TAG + "] " + text);
        // XposedBridge.log(t);
        Log.e(TAG, text, t);
    }

    public static void e(String text) {
        // XposedBridge.log("[" + TAG + "] " + text);
        Log.e(TAG, text);
    }
}
