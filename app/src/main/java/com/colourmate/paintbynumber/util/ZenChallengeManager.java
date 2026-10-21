package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Manages Daily Zen Challenges and rewards (extra hints).
 */
public class ZenChallengeManager {

    private static final String PREFS = "zen_challenge_prefs";
    private static final String KEY_LAST_CHALLENGE_DATE = "last_challenge_date";
    private static final String KEY_CHALLENGE_COMPLETED_TODAY = "challenge_completed_today";

    public static boolean isChallengeActive(Context context) {
        String today = getTodayString();
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !sp.getString(KEY_LAST_CHALLENGE_DATE, "").equals(today);
    }

    public static void completeChallenge(Context context) {
        String today = getTodayString();
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LAST_CHALLENGE_DATE, today).apply();
        
        // Reward: 2 Extra Hints
        GameSaver.setIncreseHint(context, 2);
    }

    public static boolean hasCompletedToday(Context context) {
        String today = getTodayString();
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_LAST_CHALLENGE_DATE, "").equals(today);
    }

    public static String getDailyChallengeLevelId() {
        // Deterministically pick a level based on date (example logic)
        String today = getTodayString();
        // Here you would normally return a specific level ID or use a hash
        // For demonstration, let's say "level_" + today's hash
        return "challenge_" + today.hashCode();
    }

    private static String getTodayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
