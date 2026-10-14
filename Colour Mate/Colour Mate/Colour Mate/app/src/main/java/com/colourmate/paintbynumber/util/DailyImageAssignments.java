package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

/**
 * Which picture is pinned to which date.
 *
 * <p>Two layers decide what a date shows:
 *
 * <ol>
 *   <li>an explicit assignment made here — the admin picked this picture for this date;</li>
 *   <li>otherwise the automatic rotation in {@link DailyChallengeManager}, which already gives
 *       every date a different picture.</li>
 * </ol>
 *
 * <p>So the calendar is never empty and never repeats, and any date can still be overridden by
 * hand. Assignments are stored by date key rather than by index, so they survive the library
 * growing or being reordered — an index would silently point at a different picture the next time
 * the catalogue changed.
 */
public final class DailyImageAssignments {

    private static final String PREFS = "daily_image_assignments";

    private DailyImageAssignments() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Canonical key for a date: yyyy-MM-dd, so it sorts and reads sensibly. */
    public static String keyFor(Calendar date) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1,
                date.get(Calendar.DAY_OF_MONTH));
    }

    /** The picture pinned to this date, or null when it follows the automatic rotation. */
    public static String get(Context context, Calendar date) {
        return prefs(context).getString(keyFor(date), null);
    }

    public static boolean isAssigned(Context context, Calendar date) {
        return get(context, date) != null;
    }

    public static void assign(Context context, Calendar date, String imageId) {
        if (imageId == null || imageId.isEmpty()) {
            clear(context, date);
            return;
        }
        prefs(context).edit().putString(keyFor(date), imageId).apply();
    }

    /** Removes the override; the date goes back to the automatic rotation. */
    public static void clear(Context context, Calendar date) {
        prefs(context).edit().remove(keyFor(date)).apply();
    }

    public static int assignedCount(Context context) {
        return prefs(context).getAll().size();
    }

    public static void clearAll(Context context) {
        prefs(context).edit().clear().apply();
    }
}
