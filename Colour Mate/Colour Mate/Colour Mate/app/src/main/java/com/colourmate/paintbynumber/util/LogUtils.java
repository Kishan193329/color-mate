package com.colourmate.paintbynumber.util;

import android.util.Log;
//import com.colourmate.paintbynumber.BuildConfig;

/**
 * Centralized logging utility to control logs across the application.
 * Logs are only printed if the app is in DEBUG mode.
 */
public class LogUtils {

    private static final boolean DEBUG = false/*BuildConfig.DEBUG*/;

    public static void d(String tag, String message) {
        if (DEBUG) {
            Log.d(tag, message != null ? message : "null");
        }
    }

    public static void e(String tag, String message) {
        if (DEBUG) {
            Log.e(tag, message != null ? message : "null");
        }
    }

    public static void e(String tag, String message, Throwable tr) {
        if (DEBUG) {
            Log.e(tag, message != null ? message : "null", tr);
        }
    }

    public static void i(String tag, String message) {
        if (DEBUG) {
            Log.i(tag, message != null ? message : "null");
        }
    }

    public static void w(String tag, String message) {
        if (DEBUG) {
            Log.w(tag, message != null ? message : "null");
        }
    }

    public static void v(String tag, String message) {
        if (DEBUG) {
            Log.v(tag, message != null ? message : "null");
        }
    }
}
