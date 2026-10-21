package com.colourmate.paintbynumber.ads;

import android.content.Context;
import android.content.SharedPreferences;

import com.colourmate.paintbynumber.BuildConfig;

/**
 * Utility class for managing SharedPreferences operations.
 * Provides type-safe methods for storing and retrieving preference values.
 */
public class AppPreference {

    // Constants
    private static final int PREFERENCE_MODE = Context.MODE_PRIVATE;
    private static final String KEY_PIN_SET = "key_set_pin";

    // ==================== SharedPreferences Access ====================

    /**
     * Get SharedPreferences instance
     *
     * @param context Application context
     * @return SharedPreferences instance
     */
    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(context.getPackageName(), PREFERENCE_MODE);
    }

    /**
     * Get SharedPreferences.Editor instance
     *
     * @param context Application context
     * @return SharedPreferences.Editor instance
     */
    private static SharedPreferences.Editor getEditor(Context context) {
        return getPreferences(context).edit();
    }

    // ==================== String Operations ====================

    /**
     * Get string value from preferences
     *
     * @param context      Application context
     * @param key          Preference key
     * @param defaultValue Default value if key doesn't exist
     * @return Stored string value or default value
     */
    public static String getString(Context context, String key, String defaultValue) {
        String testId = debugAdUnitId(key);
        if (testId != null) {
            return testId;
        }
        return getPreferences(context).getString(key, defaultValue);
    }

    /**
     * In a debug build the manifest carries Google's TEST AdMob app id, while the ad unit ids
     * pushed down from Firebase belong to the real publisher account. AdMob refuses to fill a unit
     * that belongs to a different publisher than the app id, so on a debug build every request
     * failed and no ad ever appeared. Serve Google's official test units instead, which match the
     * test app id and always fill.
     *
     * <p>Release builds are untouched — they use the real app id (enforced in build.gradle) with
     * the real units from Firebase.
     *
     * @return the test unit id to use, or null when the key is not an AdMob unit / not a debug build
     */
    private static String debugAdUnitId(String key) {
        if (!BuildConfig.DEBUG || key == null) {
            return null;
        }
        switch (key) {
            case Keys.KEY_ID_BANNER:
                return "ca-app-pub-3940256099942544/6300978111";
            case Keys.KEY_ID_INTERSTITIAL:
            case Keys.KEY_ID_START_INTERSTITIAL:
            case Keys.KEY_ID_SPLASH:
                return "ca-app-pub-3940256099942544/1033173712";
            case Keys.KEY_ID_NATIVE:
                return "ca-app-pub-3940256099942544/2247696110";
            case Keys.KEY_ID_REWARDED:
                return "ca-app-pub-3940256099942544/5224354917";
            case Keys.KEY_ID_REWARDED_INTER:
                return "ca-app-pub-3940256099942544/5354046379";
            case Keys.KEY_ID_APP_OPEN:
            case Keys.KEY_ID_SPLASH_APP_OPEN:
                return "ca-app-pub-3940256099942544/9257395921";
            default:
                return null;
        }
    }

    /**
     * Save string value to preferences
     *
     * @param context Application context
     * @param key     Preference key
     * @param value   Value to store
     */
    public static void setString(Context context, String key, String value) {
        getEditor(context)
                .putString(key, value)
                .apply();
    }

    // ==================== Boolean Operations ====================

    /**
     * Get boolean value from preferences
     *
     * @param context      Application context
     * @param key          Preference key
     * @param defaultValue Default value if key doesn't exist
     * @return Stored boolean value or default value
     */
    public static boolean getBoolean(Context context, String key, boolean defaultValue) {
        return getPreferences(context).getBoolean(key, defaultValue);
    }

    /**
     * Save boolean value to preferences
     *
     * @param context Application context
     * @param key     Preference key
     * @param value   Value to store
     */
    public static void setBoolean(Context context, String key, boolean value) {
        getEditor(context)
                .putBoolean(key, value)
                .apply();
    }

    // ==================== Integer Operations ====================

    /**
     * Get integer value from preferences
     *
     * @param context      Application context
     * @param key          Preference key
     * @param defaultValue Default value if key doesn't exist
     * @return Stored integer value or default value
     */
    public static int getInt(Context context, String key, int defaultValue) {
        return getPreferences(context).getInt(key, defaultValue);
    }

    /**
     * Save integer value to preferences
     *
     * @param context Application context
     * @param key     Preference key
     * @param value   Value to store
     */
    public static void setInt(Context context, String key, int value) {
        getEditor(context)
                .putInt(key, value)
                .apply();
    }

    // ==================== Long Operations ====================

    /**
     * Get long value from preferences
     *
     * @param context      Application context
     * @param key          Preference key
     * @param defaultValue Default value if key doesn't exist
     * @return Stored long value or default value
     */
    public static long getLong(Context context, String key, long defaultValue) {
        return getPreferences(context).getLong(key, defaultValue);
    }

    /**
     * Save long value to preferences
     *
     * @param context Application context
     * @param key     Preference key
     * @param value   Value to store
     */
    public static void setLong(Context context, String key, long value) {
        getEditor(context)
                .putLong(key, value)
                .apply();
    }

    // ==================== Float Operations ====================

    /**
     * Get float value from preferences
     *
     * @param context      Application context
     * @param key          Preference key
     * @param defaultValue Default value if key doesn't exist
     * @return Stored float value or default value
     */
    public static float getFloat(Context context, String key, float defaultValue) {
        return getPreferences(context).getFloat(key, defaultValue);
    }

    /**
     * Save float value to preferences
     *
     * @param context Application context
     * @param key     Preference key
     * @param value   Value to store
     */
    public static void setFloat(Context context, String key, float value) {
        getEditor(context)
                .putFloat(key, value)
                .apply();
    }

    // ==================== Special Keys ====================

    /**
     * Check if PIN is set
     *
     * @param context Application context
     * @return true if PIN is set, false otherwise
     */
    public static boolean isPinSet(Context context) {
        return getBoolean(context, KEY_PIN_SET, false);
    }

    /**
     * Set PIN status
     *
     * @param context Application context
     * @param isSet   true if PIN is set, false otherwise
     */
    public static void setIsPinSet(Context context, boolean isSet) {
        setBoolean(context, KEY_PIN_SET, isSet);
    }

    // ==================== Utility Operations ====================

    /**
     * Check if a key exists in preferences
     *
     * @param context Application context
     * @param key     Preference key
     * @return true if key exists, false otherwise
     */
    public static boolean contains(Context context, String key) {
        return getPreferences(context).contains(key);
    }

    /**
     * Remove a key from preferences
     *
     * @param context Application context
     * @param key     Preference key to remove
     */
    public static void remove(Context context, String key) {
        getEditor(context)
                .remove(key)
                .apply();
    }

    /**
     * Clear all preferences
     *
     * @param context Application context
     */
    public static void clear(Context context) {
        getEditor(context)
                .clear()
                .apply();
    }

    /**
     * Clear all preferences except specified keys
     *
     * @param context    Application context
     * @param keysToKeep Keys to preserve
     */
    public static void clearExcept(Context context, String... keysToKeep) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        // Save values to keep
        Object[] valuesToKeep = new Object[keysToKeep.length];
        for (int i = 0; i < keysToKeep.length; i++) {
            if (prefs.contains(keysToKeep[i])) {
                valuesToKeep[i] = prefs.getAll().get(keysToKeep[i]);
            }
        }

        // Clear all
        editor.clear();

        // Restore saved values
        for (int i = 0; i < keysToKeep.length; i++) {
            if (valuesToKeep[i] != null) {
                String key = keysToKeep[i];
                Object value = valuesToKeep[i];

                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                }
            }
        }

        editor.apply();
    }
}