package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages canvas background themes for the coloring game view.
 * Users can pick a background color/pattern applied behind the coloring image.
 */
public class BackgroundThemeManager {

    private static final String PREF_NAME = "bg_theme_prefs";
    private static final String KEY_ACTIVE_THEME = "active_theme_id";

    private BackgroundThemeManager() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getActiveThemeId(Context ctx) {
        return prefs(ctx).getString(KEY_ACTIVE_THEME, "white");
    }

    public static void setActiveTheme(Context ctx, String themeId) {
        prefs(ctx).edit().putString(KEY_ACTIVE_THEME, themeId).apply();
    }

    public static BgTheme getActiveTheme(Context ctx) {
        String id = getActiveThemeId(ctx);
        for (BgTheme t : getAllThemes()) {
            if (t.id.equals(id)) return t;
        }
        return getAllThemes().get(0);
    }

    public static List<BgTheme> getAllThemes() {
        List<BgTheme> themes = new ArrayList<>();
        themes.add(new BgTheme("white", "White", 0xFFFFFFFF, 0xFFF5F5F5));
        themes.add(new BgTheme("cream", "Cream", 0xFFFFF8E7, 0xFFFAF0D7));
        themes.add(new BgTheme("light_gray", "Light Gray", 0xFFF0F0F0, 0xFFE8E8E8));
        themes.add(new BgTheme("warm_beige", "Warm Beige", 0xFFF5E6D3, 0xFFEBD5BE));
        themes.add(new BgTheme("cool_blue", "Cool Blue", 0xFFE8F4FD, 0xFFD0E8F7));
        themes.add(new BgTheme("soft_pink", "Soft Pink", 0xFFFDE8F0, 0xFFF8D0E0));
        themes.add(new BgTheme("mint", "Mint", 0xFFE8F8F5, 0xFFD0F0E8));
        themes.add(new BgTheme("lavender", "Lavender", 0xFFF0E6FF, 0xFFE0D0F5));
        themes.add(new BgTheme("dark", "Dark", 0xFF2D2D2D, 0xFF1A1A1A));
        themes.add(new BgTheme("night", "Night Mode", 0xFF1A1A2E, 0xFF0D0D1A));
        themes.add(new BgTheme("parchment", "Parchment", 0xFFF1E9D2, 0xFFE8DFC0));
        themes.add(new BgTheme("slate", "Slate", 0xFF708090, 0xFF5A6A78));
        return themes;
    }

    // ─── Theme Model ───

    public static class BgTheme {
        public final String id;
        public final String name;
        public final int primaryColor;
        public final int secondaryColor;

        public BgTheme(String id, String name, int primaryColor, int secondaryColor) {
            this.id = id;
            this.name = name;
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
        }

        public boolean isDark() {
            int r = (primaryColor >> 16) & 0xFF;
            int g = (primaryColor >> 8) & 0xFF;
            int b = primaryColor & 0xFF;
            return (r * 0.299 + g * 0.587 + b * 0.114) < 128;
        }
    }
}
