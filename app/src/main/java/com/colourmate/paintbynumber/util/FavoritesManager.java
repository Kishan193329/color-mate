package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages user-bookmarked images. Stores favorited image filenames
 * in SharedPreferences for lightweight persistence.
 */
public class FavoritesManager {

    private static final String PREF_NAME = "favorites_prefs";
    private static final String KEY_FAVORITES = "favorite_items";
    private static final String KEY_FAVORITES_COUNT = "favorites_count";

    private FavoritesManager() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isFavorite(Context ctx, String fileName) {
        return getFavoriteSet(ctx).contains(fileName);
    }

    public static void toggleFavorite(Context ctx, String fileName) {
        if (isFavorite(ctx, fileName)) {
            removeFavorite(ctx, fileName);
        } else {
            addFavorite(ctx, fileName);
        }
    }

    public static void addFavorite(Context ctx, String fileName) {
        Set<String> set = getFavoriteSet(ctx);
        set.add(fileName);
        saveFavoriteSet(ctx, set);
    }

    public static void removeFavorite(Context ctx, String fileName) {
        Set<String> set = getFavoriteSet(ctx);
        set.remove(fileName);
        saveFavoriteSet(ctx, set);
    }

    public static List<String> getFavoriteList(Context ctx) {
        return new ArrayList<>(getFavoriteSet(ctx));
    }

    public static int getFavoriteCount(Context ctx) {
        return getFavoriteSet(ctx).size();
    }

    public static void clearAll(Context ctx) {
        prefs(ctx).edit()
                .remove(KEY_FAVORITES)
                .putInt(KEY_FAVORITES_COUNT, 0)
                .apply();
    }

    private static Set<String> getFavoriteSet(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    private static void saveFavoriteSet(Context ctx, Set<String> set) {
        prefs(ctx).edit()
                .putStringSet(KEY_FAVORITES, set)
                .putInt(KEY_FAVORITES_COUNT, set.size())
                .apply();
    }
}
