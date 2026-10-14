package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks user progress toward achievements/badges.
 * Each achievement has a key, title, description, threshold,
 * and current progress stored in SharedPreferences.
 */
public class AchievementManager {

    private static final String PREF_NAME = "achievement_prefs";
    private static final String KEY_CELLS_FILLED = "stat_cells_filled";
    private static final String KEY_IMAGES_COMPLETED = "stat_images_completed";
    private static final String KEY_DAILY_CHALLENGES = "stat_daily_challenges";
    private static final String KEY_MODES_TRIED = "stat_modes_tried";
    private static final String KEY_FAVORITES_ADDED = "stat_favorites_added";
    private static final String KEY_SHARES_COUNT = "stat_shares";
    private static final String KEY_LOGIN_STREAK_BEST = "stat_login_streak_best";
    private static final String KEY_TOTAL_TIME_SEC = "stat_total_time_sec";

    private AchievementManager() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ─── Stat Increment Methods ───

    public static void recordCellFilled(Context ctx, int count) {
        long current = prefs(ctx).getLong(KEY_CELLS_FILLED, 0);
        prefs(ctx).edit().putLong(KEY_CELLS_FILLED, current + count).apply();
    }

    public static void recordImageCompleted(Context ctx) {
        int current = prefs(ctx).getInt(KEY_IMAGES_COMPLETED, 0);
        prefs(ctx).edit().putInt(KEY_IMAGES_COMPLETED, current + 1).apply();
    }

    public static void recordDailyChallenge(Context ctx) {
        int current = prefs(ctx).getInt(KEY_DAILY_CHALLENGES, 0);
        prefs(ctx).edit().putInt(KEY_DAILY_CHALLENGES, current + 1).apply();
    }

    public static void recordModeTried(Context ctx, String mode) {
        String key = KEY_MODES_TRIED + "_" + mode;
        prefs(ctx).edit().putBoolean(key, true).apply();
    }

    public static void recordFavoriteAdded(Context ctx) {
        int current = prefs(ctx).getInt(KEY_FAVORITES_ADDED, 0);
        prefs(ctx).edit().putInt(KEY_FAVORITES_ADDED, current + 1).apply();
    }

    public static void recordShare(Context ctx) {
        int current = prefs(ctx).getInt(KEY_SHARES_COUNT, 0);
        prefs(ctx).edit().putInt(KEY_SHARES_COUNT, current + 1).apply();
    }

    public static void recordLoginStreak(Context ctx, int streak) {
        int best = prefs(ctx).getInt(KEY_LOGIN_STREAK_BEST, 0);
        if (streak > best) {
            prefs(ctx).edit().putInt(KEY_LOGIN_STREAK_BEST, streak).apply();
        }
    }

    public static void recordTimeSpent(Context ctx, int seconds) {
        long current = prefs(ctx).getLong(KEY_TOTAL_TIME_SEC, 0);
        prefs(ctx).edit().putLong(KEY_TOTAL_TIME_SEC, current + seconds).apply();
    }

    // ─── Stat Getters ───

    public static long getCellsFilled(Context ctx) {
        return prefs(ctx).getLong(KEY_CELLS_FILLED, 0);
    }

    public static int getImagesCompleted(Context ctx) {
        return prefs(ctx).getInt(KEY_IMAGES_COMPLETED, 0);
    }

    public static int getDailyChallengesCompleted(Context ctx) {
        return prefs(ctx).getInt(KEY_DAILY_CHALLENGES, 0);
    }

    public static int getModesTried(Context ctx) {
        int count = 0;
        String[] modes = {"number", "color", "pixel", "scanner"};
        for (String m : modes) {
            if (prefs(ctx).getBoolean(KEY_MODES_TRIED + "_" + m, false)) count++;
        }
        return count;
    }

    public static int getSharesCount(Context ctx) {
        return prefs(ctx).getInt(KEY_SHARES_COUNT, 0);
    }

    public static int getBestLoginStreak(Context ctx) {
        return prefs(ctx).getInt(KEY_LOGIN_STREAK_BEST, 0);
    }

    // ─── Achievement Definitions ───

    public static List<Achievement> getAllAchievements(Context ctx) {
        List<Achievement> list = new ArrayList<>();

        int images = getImagesCompleted(ctx);
        long cells = getCellsFilled(ctx);
        int dailies = getDailyChallengesCompleted(ctx);
        int modes = getModesTried(ctx);
        int shares = getSharesCount(ctx);
        int streak = getBestLoginStreak(ctx);

        list.add(new Achievement("first_steps", "First Steps",
                "Complete your first image", "ic_achievement_first",
                "#FF6B3D", 1, images));

        list.add(new Achievement("color_enthusiast", "Color Enthusiast",
                "Complete 10 images", "ic_achievement_ten",
                "#4A7DFF", 10, images));

        list.add(new Achievement("master_painter", "Master Painter",
                "Complete 50 images", "ic_achievement_fifty",
                "#8B5CF6", 50, images));

        list.add(new Achievement("pixel_perfectionist", "Pixel Perfectionist",
                "Fill 1,000 cells", "ic_achievement_cells",
                "#10B981", 1000, (int) Math.min(cells, Integer.MAX_VALUE)));

        list.add(new Achievement("color_machine", "Color Machine",
                "Fill 10,000 cells", "ic_achievement_machine",
                "#F59E0B", 10000, (int) Math.min(cells, Integer.MAX_VALUE)));

        list.add(new Achievement("daily_warrior", "Daily Warrior",
                "Complete 7 daily challenges", "ic_achievement_daily",
                "#EF4444", 7, dailies));

        list.add(new Achievement("daily_champion", "Daily Champion",
                "Complete 30 daily challenges", "ic_achievement_champion",
                "#EC4899", 30, dailies));

        list.add(new Achievement("explorer", "Explorer",
                "Try all 4 game modes", "ic_achievement_explorer",
                "#14B8A6", 4, modes));

        list.add(new Achievement("social_butterfly", "Social Butterfly",
                "Share 5 artworks", "ic_achievement_share",
                "#6366F1", 5, shares));

        list.add(new Achievement("dedicated", "Dedicated",
                "Reach a 7-day login streak", "ic_achievement_streak",
                "#F97316", 7, streak));

        list.add(new Achievement("legend", "Legend",
                "Reach a 30-day login streak", "ic_achievement_legend",
                "#DC2626", 30, streak));

        list.add(new Achievement("century", "Century",
                "Complete 100 images", "ic_achievement_century",
                "#7C3AED", 100, images));

        return list;
    }

    public static int getUnlockedCount(Context ctx) {
        int count = 0;
        for (Achievement a : getAllAchievements(ctx)) {
            if (a.isUnlocked()) count++;
        }
        return count;
    }

    // ─── Achievement Model ───

    public static class Achievement {
        public final String id;
        public final String title;
        public final String description;
        public final String iconName;
        public final String color;
        public final int threshold;
        public final int progress;

        public Achievement(String id, String title, String description,
                           String iconName, String color, int threshold, int progress) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.iconName = iconName;
            this.color = color;
            this.threshold = threshold;
            this.progress = Math.min(progress, threshold);
        }

        public boolean isUnlocked() {
            return progress >= threshold;
        }

        public float getProgressFraction() {
            return threshold > 0 ? (float) progress / threshold : 0f;
        }

        public String getProgressText() {
            return progress + " / " + threshold;
        }
    }
}
