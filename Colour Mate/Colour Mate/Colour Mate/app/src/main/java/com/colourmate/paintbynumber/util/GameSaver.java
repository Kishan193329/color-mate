package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

//https://www.behance.net/gallery/205509651/Blush-Painting-by-number-Mobile-App-UXUI-Design?tracking_source=search_projects|paint+by+numbers+app&l=0
//https://www.behance.net/gallery/216470275/Color-by-number-mobile-app-UXUI-Design?tracking_source=search_projects|paint+by+numbers+app&l=54


public class GameSaver {
    private static final String BUTTON_MUSIC = "is_buttonmusic_enabled";
    private static final String KEY_HINT = "no_of_hint";
    private static final String KEY_LOW_RES = "low_res_version";
    private static final String KEY_MUSIC = "is_music_enabled";
    private static final String KEY_SOUND = "is_sound_enabled";
    private static final String KEY_COLOR_PROGRESS = "is_color_progress_enabled";
    private static final String KEY_SMART_ZOOM = "is_smart_zoom_enabled";
    private static final String PREFS = "coloring_book_prefs";
    private static final String KEY_PARTICLES = "is_particles_enabled";
    private static final String KEY_SPARK_ANIM = "is_spark_anim_enabled";
    private static final String KEY_PARTICLE_STYLE = "particle_style";

    public static boolean isLowRes(Context context) {
        return getPrefs(context).getBoolean(KEY_LOW_RES, false);
    }

    public static void setLowRes(Context context) {
        getPrefs(context).edit().putBoolean(KEY_LOW_RES, true).apply();
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS, 0);
    }

    public static void setSoundEnabled(Context context, boolean z) {
        getPrefs(context).edit().putBoolean(KEY_SOUND, z).apply();
    }

    public static boolean isSoundEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SOUND, true);
    }

    public static void setMusicEnabled(Context context, boolean z) {
        getPrefs(context).edit().putBoolean(KEY_MUSIC, z).apply();
    }

    public static boolean isMusicEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_MUSIC, true);
    }

    public static void setButtonMusicEnabled(Context context, boolean z) {
        getPrefs(context).edit().putBoolean(BUTTON_MUSIC, z).apply();
    }

    public static boolean isButtonMusicEnabled(Context context) {
        return getPrefs(context).getBoolean(BUTTON_MUSIC, true);
    }

    public static void setDescreseHint(Context context) {
        if (getHint(context) > 0) {
            getPrefs(context).edit().putInt(KEY_HINT, getHint(context) - 1).apply();
        }
    }

    public static void setIncreseHint(Context context, int i) {
        getPrefs(context).edit().putInt(KEY_HINT, getHint(context) + i).apply();
    }

    public static int getHint(Context context) {
        return getPrefs(context).getInt(KEY_HINT, 3);
    }

    public static void setColorProgressEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_COLOR_PROGRESS, enabled).apply();
    }

    public static boolean isColorProgressEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_COLOR_PROGRESS, true);
    }

    public static void setSmartZoomEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SMART_ZOOM, enabled).apply();
    }

    public static boolean isSmartZoomEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SMART_ZOOM, false);
    }

    public static void setSparkAnimEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SPARK_ANIM, enabled).apply();
    }

    public static boolean isSparkAnimEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SPARK_ANIM, false);
    }
    public static void setParticlesEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_PARTICLES, enabled).apply();
    }

    /**
     * ON by default (2026-07-22). The splash was already implemented but shipped switched off,
     * so nobody ever saw it — and the owner asked for exactly this effect (roadmap item 8).
     * Players who dislike it can still turn it off in Settings.
     */
    public static boolean isParticlesEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_PARTICLES, true);
    }

    public static void setParticleStyle(Context context, int style) {
        getPrefs(context).edit().putInt(KEY_PARTICLE_STYLE, style).apply();
    }

    public static int getParticleStyle(Context context) {
        return getPrefs(context).getInt(KEY_PARTICLE_STYLE, 2); // 0: Star, 1: Butterfly, 2: Random
    }
}
