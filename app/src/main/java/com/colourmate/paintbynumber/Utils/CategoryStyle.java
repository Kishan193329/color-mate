package com.colourmate.paintbynumber.Utils;

import com.colourmate.paintbynumber.R;

/**
 * Maps a server category name onto the design system's icon + colour.
 *
 * <p>Categories arrive from the backend as plain strings with no id, order or icon (see CLAUDE.md
 * §3.2), so the look has to be derived here. Known names get their designed pairing; anything
 * unknown gets a stable pick from the same set — the same category always looks the same, and a
 * new category the backend invents tomorrow still arrives dressed.
 */
public final class CategoryStyle {

    private CategoryStyle() {
    }

    private static final int[] BALLS = {
            R.drawable.cm_ball_mandala, R.drawable.cm_ball_animals, R.drawable.cm_ball_nature,
            R.drawable.cm_ball_fantasy, R.drawable.cm_ball_characters, R.drawable.cm_ball_festivals,
            R.drawable.cm_ball_kids, R.drawable.cm_ball_ai
    };

    private static final int[] GLYPHS = {
            R.drawable.cm_glyph_mandala, R.drawable.cm_glyph_animals, R.drawable.cm_glyph_nature,
            R.drawable.cm_glyph_fantasy, R.drawable.cm_glyph_characters, R.drawable.cm_glyph_festivals,
            R.drawable.cm_glyph_kids, R.drawable.cm_glyph_ai
    };

    /** Index into {@link #BALLS}/{@link #GLYPHS} for a category name. */
    private static int indexOf(String category) {
        String key = category == null ? "" : category.trim().toLowerCase();

        if (key.contains("mandala")) return 0;
        if (key.contains("animal") || key.contains("pet") || key.contains("bird")) return 1;
        if (key.contains("nature") || key.contains("flower") || key.contains("garden")
                || key.contains("landscape")) return 2;
        if (key.contains("fantasy") || key.contains("magic") || key.contains("dragon")
                || key.contains("space")) return 3;
        if (key.contains("people") || key.contains("character") || key.contains("portrait")
                || key.contains("anime")) return 4;
        if (key.contains("festival") || key.contains("holiday") || key.contains("love")
                || key.contains("daily")) return 5;
        if (key.contains("kid") || key.contains("child") || key.contains("cartoon")
                || key.contains("food")) return 6;
        if (key.contains("ai") || key.contains("new")) return 7;

        // Unknown: hash the name so the choice is arbitrary but never changes for that name.
        return Math.abs(key.hashCode()) % BALLS.length;
    }

    public static int ballFor(String category) {
        return BALLS[indexOf(category)];
    }

    public static int glyphFor(String category) {
        return GLYPHS[indexOf(category)];
    }

    /** "animals" → "Animals". The backend sends lowercase keys; people read title case. */
    public static String labelFor(String category) {
        if (category == null || category.isEmpty()) return "";
        String trimmed = category.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1).toLowerCase();
    }
}
