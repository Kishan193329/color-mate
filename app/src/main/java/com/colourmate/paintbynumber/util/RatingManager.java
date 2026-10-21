package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.TimeUnit;

/**
 * Decides when to ask the user for a Play Store rating.
 *
 * <p>The rules, all measured in real time rather than launch counts:
 * <ul>
 *   <li>Never on the first day — the first prompt comes <b>3 days</b> after the first launch.</li>
 *   <li>Dismissed, or closed without rating → ask again after <b>7 days</b>, not sooner.</li>
 *   <li>Rated → never ask again.</li>
 * </ul>
 *
 * <p>State lives in its own SharedPreferences file so clearing ad or game preferences cannot
 * accidentally resurrect the prompt for someone who already rated.
 *
 * <p>All timing is pure and takes {@code now} as a parameter, so the rules can be exercised
 * without waiting three days.
 */
public final class RatingManager {

    private static final String PREFS = "rating_prefs";

    private static final String KEY_FIRST_LAUNCH = "first_launch_ms";
    private static final String KEY_LAST_SHOWN = "last_shown_ms";
    private static final String KEY_RATED = "rated";

    static final long FIRST_DELAY_MS = TimeUnit.DAYS.toMillis(3);
    static final long RETRY_INTERVAL_MS = TimeUnit.DAYS.toMillis(7);

    private RatingManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Records the first launch if this is it. Safe to call on every launch — it only ever writes
     * once, so the 3-day clock starts at the real first run and never restarts.
     */
    public static void recordFirstLaunchIfNeeded(Context context, long now) {
        SharedPreferences p = prefs(context);
        long first = p.getLong(KEY_FIRST_LAUNCH, 0L);

        // A first-launch stamp in the future means the device clock moved backwards after we wrote
        // it. Left alone, the 3-day wait could become months. Re-anchor to now.
        if (first == 0L || first > now) {
            p.edit().putLong(KEY_FIRST_LAUNCH, now).apply();
        }
    }

    /** True when the prompt is due: past the initial delay, outside the retry window, not rated. */
    public static boolean shouldShow(Context context, long now) {
        SharedPreferences p = prefs(context);

        if (p.getBoolean(KEY_RATED, false)) {
            return false;
        }

        long first = p.getLong(KEY_FIRST_LAUNCH, 0L);
        if (first == 0L) {
            return false;                       // first launch not recorded yet — nothing is due
        }
        if (now - first < FIRST_DELAY_MS) {
            return false;                       // still inside the 3-day grace period
        }

        long lastShown = p.getLong(KEY_LAST_SHOWN, 0L);
        if (lastShown == 0L) {
            return true;                        // never asked, and the 3 days are up
        }
        // A "last shown" in the future is again a clock change; treat it as due rather than
        // locking the prompt away for good.
        if (lastShown > now) {
            return true;
        }
        return now - lastShown >= RETRY_INTERVAL_MS;
    }

    /** Call when the dialog is actually put on screen — starts the 7-day retry window. */
    public static void markShown(Context context, long now) {
        prefs(context).edit().putLong(KEY_LAST_SHOWN, now).apply();
    }

    /** Call when the user goes through with rating. The prompt never appears again. */
    public static void markRated(Context context) {
        prefs(context).edit().putBoolean(KEY_RATED, true).apply();
    }

    public static boolean hasRated(Context context) {
        return prefs(context).getBoolean(KEY_RATED, false);
    }

    /** Test hook: wipes the stored state so the flow can be exercised from the beginning. */
    public static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }
}
