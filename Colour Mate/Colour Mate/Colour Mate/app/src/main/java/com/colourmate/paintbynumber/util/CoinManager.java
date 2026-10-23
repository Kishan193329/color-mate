package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

/**
 * The coin economy, v1 (2026-10-14). One SharedPreferences file, no server.
 *
 * <p>Why it exists: the header carried a gold coin chip that did nothing but open Premium, and
 * hints were "3 free, then a rewarded ad". Coins tie those together — a daily login bonus and
 * rewarded videos EARN coins, hints SPEND them, and the ad is the fallback when the purse is
 * empty. That gives a reason to open the app every day and a second, gentler way to monetise a
 * hint than a 30-second video every time.
 *
 * <p>Numbers are deliberately simple and are the dials: a new player starts with
 * {@link #START_BALANCE} (five hints), a day's login is worth {@link #DAILY_BONUS} (2.5 hints),
 * a video {@link #REWARD_AD_COINS} (1.5 hints).
 */
public final class CoinManager {

    private static final String PREFS = "coins";
    private static final String K_BALANCE = "balance";
    private static final String K_SEEDED = "seeded";
    private static final String K_LAST_BONUS_DAY = "last_bonus_day";

    public static final int START_BALANCE = 100;
    public static final int DAILY_BONUS = 50;
    public static final int REWARD_AD_COINS = 30;
    public static final int HINT_COST = 20;
    public static final int COMPLETE_COINS = 20;        // first finish of a picture
    public static final int PREMIUM_PICTURE_COST = 150; // a premium-tier picture, unlocked for good
    public static final int HIT_INSTANT_COST = 300;     // play the Hit picture before its free day

    private CoinManager() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** First touch: grant the starting purse and mark today as "bonus taken", so day one is not 150. */
    private static void seed(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean(K_SEEDED, false)) return;
        p.edit().putBoolean(K_SEEDED, true)
                .putInt(K_BALANCE, START_BALANCE)
                .putString(K_LAST_BONUS_DAY, dayKey())
                .apply();
    }

    public static int balance(Context c) {
        seed(c);
        return prefs(c).getInt(K_BALANCE, 0);
    }

    /** Adds (or, with a negative amount, removes) coins. Never goes below zero. Returns the new balance. */
    public static int add(Context c, int amount) {
        int next = Math.max(0, balance(c) + amount);
        prefs(c).edit().putInt(K_BALANCE, next).apply();
        return next;
    }

    /** Spends {@code cost} if affordable. False — and nothing changes — when it is not. */
    public static boolean trySpend(Context c, int cost) {
        int have = balance(c);
        if (have < cost) return false;
        prefs(c).edit().putInt(K_BALANCE, have - cost).apply();
        return true;
    }

    /**
     * Grants the daily login bonus once per calendar day (device local time). Returns the amount
     * granted, or 0 when today's was already taken — the caller uses that to decide whether to
     * celebrate.
     */
    public static int claimDailyBonusIfDue(Context c) {
        seed(c);
        String today = dayKey();
        SharedPreferences p = prefs(c);
        if (today.equals(p.getString(K_LAST_BONUS_DAY, ""))) return 0;
        p.edit().putString(K_LAST_BONUS_DAY, today).apply();
        add(c, DAILY_BONUS);
        return DAILY_BONUS;
    }

    /** +COMPLETE_COINS the first time a picture is finished — replaying it earns nothing. */
    public static int rewardCompletion(Context c, String fileName) {
        if (fileName == null || fileName.isEmpty()) return 0;
        SharedPreferences p = prefs(c);
        if (p.getBoolean("done_" + fileName, false)) return 0;
        p.edit().putBoolean("done_" + fileName, true).apply();
        add(c, COMPLETE_COINS);
        Referral.reportEarned(c, COMPLETE_COINS);   // the friend who invited this player gets 10%
        return COMPLETE_COINS;
    }

    public static boolean isUnlocked(Context c, String fileName) {
        return prefs(c).getBoolean("unlock_" + fileName, false);
    }

    public static void markUnlocked(Context c, String fileName) {
        prefs(c).edit().putBoolean("unlock_" + fileName, true).apply();
    }

    public static boolean canAfford(Context c, int cost) {
        return balance(c) >= cost;
    }

    /** "1.2k" past a thousand; the chip is small. */
    public static String format(int n) {
        if (n >= 1000) return String.format(Locale.US, "%.1fk", n / 1000f);
        return String.valueOf(n);
    }

    private static String dayKey() {
        Calendar cal = Calendar.getInstance();
        return String.format(Locale.US, "%04d%02d%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }
}
