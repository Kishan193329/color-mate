package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Centralized stats tracker that aggregates data from AchievementManager,
 * StreakManager, and DailyChallengeManager into a single dashboard view.
 * Also tracks per-day coloring activity for the calendar heat map.
 */
public class StatsManager {

    private static final String PREF_NAME = "stats_prefs";
    private static final String KEY_COLOR_USAGE_PREFIX = "color_used_";
    private static final String KEY_DAILY_ACTIVITY_PREFIX = "day_activity_";
    private static final String KEY_TOTAL_SESSIONS = "total_sessions";
    private static final String KEY_FIRST_USE_DATE = "first_use_date";
    private static final String KEY_WEEKLY_GOAL = "weekly_goal";
    private static final String KEY_WEEKLY_PROGRESS = "weekly_progress";
    private static final String KEY_WEEKLY_RESET_DATE = "weekly_reset_date";

    private static final SimpleDateFormat DAY_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat WEEK_FMT =
            new SimpleDateFormat("yyyy-ww", Locale.US);

    private StatsManager() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ─── Session Tracking ───

    public static void recordSession(Context ctx) {
        int sessions = prefs(ctx).getInt(KEY_TOTAL_SESSIONS, 0);
        SharedPreferences.Editor editor = prefs(ctx).edit();
        editor.putInt(KEY_TOTAL_SESSIONS, sessions + 1);

        if (!prefs(ctx).contains(KEY_FIRST_USE_DATE)) {
            editor.putString(KEY_FIRST_USE_DATE, DAY_FMT.format(new Date()));
        }
        editor.apply();
    }

    public static int getTotalSessions(Context ctx) {
        return prefs(ctx).getInt(KEY_TOTAL_SESSIONS, 0);
    }

    // ─── Daily Activity Heat Map ───

    public static void recordDailyActivity(Context ctx) {
        String today = DAY_FMT.format(new Date());
        String key = KEY_DAILY_ACTIVITY_PREFIX + today;
        int count = prefs(ctx).getInt(key, 0);
        prefs(ctx).edit().putInt(key, count + 1).apply();
    }

    public static int getDayActivity(Context ctx, String dateStr) {
        return prefs(ctx).getInt(KEY_DAILY_ACTIVITY_PREFIX + dateStr, 0);
    }

    /**
     * Returns activity counts for the last N days.
     * Key = "yyyy-MM-dd", Value = count.
     */
    public static Map<String, Integer> getActivityMap(Context ctx, int days) {
        Map<String, Integer> map = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < days; i++) {
            String dateStr = DAY_FMT.format(cal.getTime());
            int count = prefs(ctx).getInt(KEY_DAILY_ACTIVITY_PREFIX + dateStr, 0);
            if (count > 0) map.put(dateStr, count);
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return map;
    }

    // ─── Color Usage Tracking ───

    public static void recordColorUsed(Context ctx, int color) {
        String hex = String.format("#%06X", (0xFFFFFF & color));
        String key = KEY_COLOR_USAGE_PREFIX + hex;
        int count = prefs(ctx).getInt(key, 0);
        prefs(ctx).edit().putInt(key, count + 1).apply();
    }

    public static List<ColorStat> getTopColors(Context ctx, int limit) {
        Map<String, ?> all = prefs(ctx).getAll();
        List<ColorStat> colors = new ArrayList<>();

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(KEY_COLOR_USAGE_PREFIX)) {
                String hex = entry.getKey().substring(KEY_COLOR_USAGE_PREFIX.length());
                int count = (Integer) entry.getValue();
                colors.add(new ColorStat(hex, count));
            }
        }

        colors.sort((a, b) -> Integer.compare(b.count, a.count));
        return colors.subList(0, Math.min(limit, colors.size()));
    }

    // ─── Weekly Goal ───

    public static void setWeeklyGoal(Context ctx, int goal) {
        prefs(ctx).edit().putInt(KEY_WEEKLY_GOAL, goal).apply();
    }

    public static int getWeeklyGoal(Context ctx) {
        return prefs(ctx).getInt(KEY_WEEKLY_GOAL, 5);
    }

    public static void incrementWeeklyProgress(Context ctx) {
        checkWeeklyReset(ctx);
        int progress = prefs(ctx).getInt(KEY_WEEKLY_PROGRESS, 0);
        prefs(ctx).edit().putInt(KEY_WEEKLY_PROGRESS, progress + 1).apply();
    }

    public static int getWeeklyProgress(Context ctx) {
        checkWeeklyReset(ctx);
        return prefs(ctx).getInt(KEY_WEEKLY_PROGRESS, 0);
    }

    private static void checkWeeklyReset(Context ctx) {
        String currentWeek = WEEK_FMT.format(new Date());
        String savedWeek = prefs(ctx).getString(KEY_WEEKLY_RESET_DATE, "");
        if (!currentWeek.equals(savedWeek)) {
            prefs(ctx).edit()
                    .putInt(KEY_WEEKLY_PROGRESS, 0)
                    .putString(KEY_WEEKLY_RESET_DATE, currentWeek)
                    .apply();
        }
    }

    // ─── Aggregated Stats ───

    public static String getFormattedTime(long totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + "s";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    public static String getUserLevel(Context ctx) {
        int images = AchievementManager.getImagesCompleted(ctx);
        if (images >= 100) return "Legend";
        if (images >= 50) return "Master";
        if (images >= 25) return "Expert";
        if (images >= 10) return "Artist";
        if (images >= 5) return "Learner";
        return "Beginner";
    }

    public static int getUserLevelProgress(Context ctx) {
        int images = AchievementManager.getImagesCompleted(ctx);
        if (images >= 100) return 100;
        int[] thresholds = {5, 10, 25, 50, 100};
        for (int t : thresholds) {
            if (images < t) return (int) ((images / (float) t) * 100);
        }
        return 100;
    }

    public static String getDaysSinceFirstUse(Context ctx) {
        String first = prefs(ctx).getString(KEY_FIRST_USE_DATE, null);
        if (first == null) return "0";
        try {
            Date firstDate = DAY_FMT.parse(first);
            if (firstDate == null) return "0";
            long diff = System.currentTimeMillis() - firstDate.getTime();
            long days = diff / (1000 * 60 * 60 * 24);
            return String.valueOf(Math.max(1, days));
        } catch (Exception e) {
            return "1";
        }
    }

    // ─── Models ───

    public static class ColorStat {
        public final String hex;
        public final int count;

        public ColorStat(String hex, int count) {
            this.hex = hex;
            this.count = count;
        }
    }
}
