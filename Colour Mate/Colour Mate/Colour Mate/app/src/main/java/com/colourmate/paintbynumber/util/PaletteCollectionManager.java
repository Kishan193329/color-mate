package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages user-created color palette collections.
 * Palettes are stored as JSON in SharedPreferences.
 */
public class PaletteCollectionManager {

    private static final String PREF_NAME = "palette_collection_prefs";
    private static final String KEY_PALETTES = "palettes";
    private static final Gson gson = new Gson();

    private PaletteCollectionManager() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static List<ColorPalette> getAllPalettes(Context ctx) {
        String json = prefs(ctx).getString(KEY_PALETTES, null);
        if (json == null) return getDefaultPalettes();
        try {
            Type type = new TypeToken<ArrayList<ColorPalette>>() {}.getType();
            List<ColorPalette> list = gson.fromJson(json, type);
            return list != null ? list : getDefaultPalettes();
        } catch (Exception e) {
            return getDefaultPalettes();
        }
    }

    public static void savePalettes(Context ctx, List<ColorPalette> palettes) {
        prefs(ctx).edit().putString(KEY_PALETTES, gson.toJson(palettes)).apply();
    }

    public static void addPalette(Context ctx, ColorPalette palette) {
        List<ColorPalette> list = getAllPalettes(ctx);
        list.add(palette);
        savePalettes(ctx, list);
    }

    public static void updatePalette(Context ctx, ColorPalette palette) {
        List<ColorPalette> list = getAllPalettes(ctx);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(palette.id)) {
                list.set(i, palette);
                break;
            }
        }
        savePalettes(ctx, list);
    }

    public static void deletePalette(Context ctx, String paletteId) {
        List<ColorPalette> list = getAllPalettes(ctx);
        list.removeIf(p -> p.id.equals(paletteId));
        savePalettes(ctx, list);
    }

    public static ColorPalette getPaletteById(Context ctx, String paletteId) {
        for (ColorPalette p : getAllPalettes(ctx)) {
            if (p.id.equals(paletteId)) return p;
        }
        return null;
    }

    public static String getActivePaletteId(Context ctx) {
        return prefs(ctx).getString("active_palette", null);
    }

    public static void setActivePalette(Context ctx, String paletteId) {
        prefs(ctx).edit().putString("active_palette", paletteId).apply();
    }

    public static void clearActivePalette(Context ctx) {
        prefs(ctx).edit().remove("active_palette").apply();
    }

    private static List<ColorPalette> getDefaultPalettes() {
        List<ColorPalette> defaults = new ArrayList<>();

        defaults.add(new ColorPalette("default_sunset", "Sunset",
                new int[]{0xFFFF6B35, 0xFFF7C59F, 0xFFEFAF6B, 0xFFD94343, 0xFF8B2252,
                        0xFFFF8C42, 0xFFFFC857, 0xFFE8751A}));

        defaults.add(new ColorPalette("default_ocean", "Ocean",
                new int[]{0xFF006994, 0xFF0A9396, 0xFF94D2BD, 0xFF005F73, 0xFF0077B6,
                        0xFF00B4D8, 0xFF90E0EF, 0xFFCAF0F8}));

        defaults.add(new ColorPalette("default_forest", "Forest",
                new int[]{0xFF2D6A4F, 0xFF40916C, 0xFF52B788, 0xFF74C69D, 0xFF95D5B2,
                        0xFF1B4332, 0xFF344E41, 0xFFDAD7CD}));

        defaults.add(new ColorPalette("default_candy", "Candy",
                new int[]{0xFFFF69B4, 0xFFFF1493, 0xFFDA70D6, 0xFF9370DB, 0xFFBA55D3,
                        0xFFFF6EB4, 0xFFEE82EE, 0xFFDDA0DD}));

        defaults.add(new ColorPalette("default_earth", "Earth Tones",
                new int[]{0xFF8B4513, 0xFFA0522D, 0xFFD2B48C, 0xFFF5DEB3, 0xFF6B4226,
                        0xFFBC8F8F, 0xFF4A3728, 0xFFCDB79E}));

        return defaults;
    }

    // ─── Palette Model ───

    public static class ColorPalette {
        public String id;
        public String name;
        public int[] colors;
        public long createdAt;

        public ColorPalette() {}

        public ColorPalette(String id, String name, int[] colors) {
            this.id = id;
            this.name = name;
            this.colors = colors;
            this.createdAt = System.currentTimeMillis();
        }

        public static ColorPalette create(String name, int[] colors) {
            return new ColorPalette(UUID.randomUUID().toString(), name, colors);
        }
    }
}
