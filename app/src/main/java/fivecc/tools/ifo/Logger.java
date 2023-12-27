package fivecc.tools.ifo;

import de.robv.android.xposed.XposedBridge;

public class Logger {
    private static final String TAG = "IFO";

    public static void d(String text) {
        XposedBridge.log("[" + TAG + "][D] " + text);
        // Log.d(TAG, text);
    }

    public static void e(String text, Throwable t) {
        XposedBridge.log("[" + TAG + "][E] " + text);
        XposedBridge.log(t);
        // Log.e(TAG, text, t);
    }

    public static void e(String text) {
        XposedBridge.log("[" + TAG + "][E] " + text);
        // Log.e(TAG, text);
    }

    public static void w(String text) {
        XposedBridge.log("[" + TAG + "][W] " + text);
        // Log.w(TAG, text);
    }
}
