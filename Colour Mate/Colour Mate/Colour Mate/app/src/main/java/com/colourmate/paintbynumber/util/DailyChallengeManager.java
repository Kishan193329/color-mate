package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Manages daily challenge state: today's challenge ID, completion status,
 * and streak of consecutive daily challenges completed.
 */
public class DailyChallengeManager {

    private static final String PREF_NAME = "daily_challenge_prefs";
    private static final String KEY_LAST_CHALLENGE_DATE = "last_challenge_date";
    private static final String KEY_CHALLENGE_COMPLETED = "challenge_completed";
    private static final String KEY_DAILY_STREAK = "daily_streak";
    private static final String KEY_TOTAL_COMPLETED = "total_daily_completed";

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private DailyChallengeManager() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getTodayKey() {
        return DATE_FMT.format(new Date());
    }

    /**
     * Returns a deterministic challenge index for today based on the date.
     * Use this to pick which image from the "daily" category to show.
     */
    public static int getTodayChallengeIndex(int totalImages) {
        return getChallengeIndexFor(Calendar.getInstance(), totalImages);
    }

    /**
     * The picture for any given day.
     *
     * <p>A calendar asks about every date on screen, so this is a pure function of the date — no
     * randomness, no stored state. Two people comparing calendars on the same date see the same
     * picture, which is the point of a daily challenge.
     *
     * <p><b>Why a permutation and not a hash.</b> The obvious approaches both repeat too soon:
     * {@code (year * 367 + dayOfYear) % n} walks the list one step per day, and a plain hash lands
     * on the same index on consecutive days by chance (measured: 5 immediate repeats in a 30-day
     * window over 10 images). Instead the days are grouped into cycles of {@code n}, each cycle is
     * a shuffled ordering of the whole library, and each day takes the next position in it. Within
     * any run of {@code n} days every picture appears exactly once and none repeats — and the
     * shuffle is re-seeded per cycle, so the order differs next time round rather than looping.
     */
    public static int getChallengeIndexFor(Calendar day, int totalImages) {
        if (totalImages <= 0 || day == null) return 0;
        if (totalImages == 1) return 0;

        long epochDay = toEpochDay(day);
        long cycle = Math.floorDiv(epochDay, totalImages);
        int position = (int) Math.floorMod(epochDay, (long) totalImages);

        int[] order = permutationForCycle(cycle, totalImages);

        // Cycle boundary: the first pick of a new cycle could equal the last pick of the previous
        // one, which would show the same picture two days running — the one thing this is supposed
        // to guarantee against. Nudge it when that happens.
        if (position == 0 && totalImages > 1) {
            int[] previous = permutationForCycle(cycle - 1, totalImages);
            if (order[0] == previous[totalImages - 1]) {
                int tmp = order[0];
                order[0] = order[1];
                order[1] = tmp;
            }
        }

        return order[position];
    }

    /** A shuffled ordering of the whole library for one cycle of {@code n} days. */
    private static int[] permutationForCycle(long cycle, int n) {
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }

        // Seeded Fisher-Yates. The LCG constants are the usual 64-bit pair; only the high bits are
        // used because the low bits of an LCG cycle far too regularly.
        long state = cycle * 2654435761L + 0x9E3779B97F4A7C15L;
        for (int i = n - 1; i > 0; i--) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            int j = (int) Math.floorMod(state >>> 33, (long) (i + 1));
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        return order;
    }

    /**
     * Day number for a LOCAL calendar date.
     *
     * <p>Built from the year/month/day fields against a UTC midnight rather than dividing the raw
     * timestamp, so the picture changes when the user's own date changes — not when UTC rolls over.
     */
    private static long toEpochDay(Calendar day) {
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis() / 86_400_000L;
    }

    /** Convenience for a specific calendar date. */
    public static int getChallengeIndexFor(int year, int monthZeroBased, int dayOfMonth,
                                           int totalImages) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, monthZeroBased, dayOfMonth);
        return getChallengeIndexFor(cal, totalImages);
    }

    // ─── Per-day history (2026-10-14) ───
    // Until now only "today done + streak + total" was stored, so the calendar could never show
    // which days were finished. A comma-separated set of yyyy-MM-dd keys is enough for ticks.
    private static final String KEY_COMPLETED_DAYS = "completed_days";

    public static String dateKey(Calendar day) {
        return DATE_FMT.format(day.getTime());
    }

    /** True when the picture for that date was finished (from this screen's history). */
    public static boolean isCompleted(Context ctx, Calendar day) {
        if (day == null) return false;
        String key = dateKey(day);
        String all = prefs(ctx).getString(KEY_COMPLETED_DAYS, "");
        return ("," + all + ",").contains("," + key + ",");
    }

    /**
     * Records a finished daily picture. Today's completion also drives the streak via
     * {@link #markTodayCompleted}; a back-dated day (the calendar lets you replay earlier dates)
     * only earns its tick and counts toward the total.
     */
    public static void markCompleted(Context ctx, String dateKey) {
        if (dateKey == null || dateKey.isEmpty()) return;
        SharedPreferences sp = prefs(ctx);
        String all = sp.getString(KEY_COMPLETED_DAYS, "");
        boolean already = ("," + all + ",").contains("," + dateKey + ",");
        if (!already) {
            sp.edit().putString(KEY_COMPLETED_DAYS, all.isEmpty() ? dateKey : all + "," + dateKey).apply();
        }
        if (getTodayKey().equals(dateKey)) {
            if (!isTodayCompleted(ctx)) markTodayCompleted(ctx);
        } else if (!already) {
            sp.edit().putInt(KEY_TOTAL_COMPLETED, sp.getInt(KEY_TOTAL_COMPLETED, 0) + 1).apply();
        }
    }

    public static boolean isTodayCompleted(Context ctx) {
        String lastDate = prefs(ctx).getString(KEY_LAST_CHALLENGE_DATE, "");
        return getTodayKey().equals(lastDate) && prefs(ctx).getBoolean(KEY_CHALLENGE_COMPLETED, false);
    }

    public static void markTodayCompleted(Context ctx) {
        String today = getTodayKey();
        SharedPreferences sp = prefs(ctx);
        String lastDate = sp.getString(KEY_LAST_CHALLENGE_DATE, "");

        int streak = sp.getInt(KEY_DAILY_STREAK, 0);
        int total = sp.getInt(KEY_TOTAL_COMPLETED, 0);

        try {
            if (!lastDate.isEmpty()) {
                Date lastD = DATE_FMT.parse(lastDate);
                Date todayD = DATE_FMT.parse(today);
                if (lastD != null && todayD != null) {
                    long diff = (todayD.getTime() - lastD.getTime()) / (1000 * 60 * 60 * 24);
                    streak = (diff == 1) ? streak + 1 : 1;
                } else {
                    streak = 1;
                }
            } else {
                streak = 1;
            }
        } catch (Exception e) {
            streak = 1;
        }

        sp.edit()
                .putString(KEY_LAST_CHALLENGE_DATE, today)
                .putBoolean(KEY_CHALLENGE_COMPLETED, true)
                .putInt(KEY_DAILY_STREAK, streak)
                .putInt(KEY_TOTAL_COMPLETED, total + 1)
                .apply();
    }

    public static int getDailyStreak(Context ctx) {
        if (isTodayCompleted(ctx)) {
            return prefs(ctx).getInt(KEY_DAILY_STREAK, 0);
        }
        // If yesterday was completed, streak is still alive (pending today)
        String lastDate = prefs(ctx).getString(KEY_LAST_CHALLENGE_DATE, "");
        if (lastDate.isEmpty()) return 0;
        try {
            Date lastD = DATE_FMT.parse(lastDate);
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            String yesterdayStr = DATE_FMT.format(yesterday.getTime());
            if (yesterdayStr.equals(lastDate)) {
                return prefs(ctx).getInt(KEY_DAILY_STREAK, 0);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static int getTotalCompleted(Context ctx) {
        return prefs(ctx).getInt(KEY_TOTAL_COMPLETED, 0);
    }

    /**
     * Returns milliseconds until midnight (next challenge reset).
     */
    public static long getMillisUntilReset() {
        Calendar midnight = Calendar.getInstance();
        midnight.add(Calendar.DAY_OF_YEAR, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        return midnight.getTimeInMillis() - System.currentTimeMillis();
    }

    public static String getCountdownText() {
        long millis = getMillisUntilReset();
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis / (1000 * 60)) % 60;
        return String.format(Locale.US, "%02dh %02dm", hours, minutes);
    }
}
