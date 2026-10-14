package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.List;
import java.util.Random;

/**
 * Gives the picture grids a fresh arrangement once a day.
 *
 * <p>The galleries are served in a fixed order, so every launch and every pull-to-refresh showed
 * the same pictures in the same places. This hands out a <em>seed</em> that stays put for 24 hours:
 * the same seed produces the same arrangement, so refreshing or reopening the app inside the period
 * changes nothing, and once the period lapses a new seed is drawn and the grids look different.
 *
 * <p>The seed is written to disk the moment it is drawn, which is what makes the period survive a
 * restart — a seed derived from the clock alone (say, the day number) would be reproducible but
 * would also flip at midnight rather than 24 hours after the user actually started their day.
 *
 * <p>Everything here is deterministic given the seed. Nothing is stored per picture, so the
 * catalogue can grow, shrink or be re-paginated underneath it without leaving stale state behind.
 */
public final class DailyRotation {

    private static final String TAG = "DailyRotation";

    private static final String PREFS = "daily_rotation";
    private static final String KEY_SEED = "seed";
    private static final String KEY_ASSIGNED_AT = "assigned_at";

    /** How long one arrangement lasts. */
    private static final long PERIOD_MS = 24L * 60L * 60L * 1000L;

    private static final Object LOCK = new Object();

    /** Mirrors what is on disk, so a page of results does not hit SharedPreferences per item. */
    private static long cachedSeed;
    private static long cachedAssignedAt;

    private DailyRotation() {
    }

    /**
     * The seed in force right now, drawing a new one if the last has expired.
     *
     * <p>Safe to call from any thread — pages are parsed off the main thread.
     */
    public static long seed(Context context) {
        if (context == null) return 0L;

        synchronized (LOCK) {
            long now = System.currentTimeMillis();

            if (cachedAssignedAt == 0L) {
                SharedPreferences prefs = prefs(context);
                cachedSeed = prefs.getLong(KEY_SEED, 0L);
                cachedAssignedAt = prefs.getLong(KEY_ASSIGNED_AT, 0L);
            }

            if (!expired(cachedAssignedAt, now)) return cachedSeed;

            // Draw again, and insist it differs from the one just retired — otherwise a day could
            // land on the same arrangement by chance, which reads to the user as "nothing changed".
            long fresh = cachedSeed;
            Random random = new Random(now ^ (cachedSeed * 31L));
            for (int attempt = 0; attempt < 8 && fresh == cachedSeed; attempt++) {
                fresh = random.nextLong();
            }

            cachedSeed = fresh;
            cachedAssignedAt = now;
            prefs(context).edit()
                    .putLong(KEY_SEED, cachedSeed)
                    .putLong(KEY_ASSIGNED_AT, cachedAssignedAt)
                    .apply();

            Log.i(TAG, "New daily arrangement assigned; next in " + (PERIOD_MS / 3600000L) + "h");
            return cachedSeed;
        }
    }

    /**
     * True once the period has run out — or if the stamp is in the future, which means the device
     * clock moved backwards and the stored period can no longer be trusted to end.
     */
    private static boolean expired(long assignedAt, long now) {
        return assignedAt <= 0L || now < assignedAt || now - assignedAt >= PERIOD_MS;
    }

    /**
     * Rearranges {@code [from, to)} of two parallel lists, keeping them in step.
     *
     * <p>A slice rather than the whole list because the grids page: rearranging everything each
     * time a page arrived would shuffle pictures the user is already looking at. Shuffling only the
     * new arrivals keeps the view still while it grows.
     *
     * @param seed the arrangement to apply; the same seed always gives the same result
     */
    public static void shuffleRange(List<?> items, List<?> keys, int from, int to, long seed) {
        if (items == null || from < 0 || to > items.size() || to - from < 2) return;
        if (keys != null && keys.size() != items.size()) {
            // The two must stay aligned — a key that no longer belongs to its item would open the
            // wrong picture. If they have drifted, leave the order alone rather than guess.
            Log.w(TAG, "items/keys out of step (" + items.size() + "/" + keys.size() + ") — not shuffling");
            return;
        }

        // Fisher-Yates, seeded, so the arrangement is reproducible for the whole period.
        Random random = new Random(seed);
        for (int i = to - 1; i > from; i--) {
            int j = from + random.nextInt(i - from + 1);
            if (i == j) continue;
            swap(items, i, j);
            if (keys != null) swap(keys, i, j);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void swap(List<?> list, int i, int j) {
        ((List) list).set(i, ((List) list).set(j, list.get(i)));
    }

    /**
     * A seed for one page. Mixing in the list's path and where the page starts means two galleries
     * — and two pages of the same gallery — get different arrangements from the same daily seed,
     * instead of every list being shuffled identically.
     */
    public static long pageSeed(long dailySeed, String path, int startIndex) {
        long h = dailySeed;
        h ^= (path == null ? 0L : path.hashCode()) * 0x9E3779B97F4A7C15L;
        h ^= (startIndex + 1L) * 0xC2B2AE3D27D4EB4FL;
        return h;
    }

    /** Forces a new arrangement on the next request. Used by the developer options only. */
    public static void reset(Context context) {
        synchronized (LOCK) {
            cachedSeed = 0L;
            cachedAssignedAt = 0L;
            if (context != null) prefs(context).edit().clear().apply();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
