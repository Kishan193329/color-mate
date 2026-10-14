package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class StreakManager {
    private static final String PREF_NAME = "streak_prefs";
    private static final String KEY_LAST_LOGIN = "last_login";
    private static final String KEY_STREAK_COUNT = "streak_count";
    private static final String KEY_HIGHEST_STREAK = "highest_streak";

    public static void recordLogin(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastLogin = prefs.getLong(KEY_LAST_LOGIN, 0);
        int currentStreak = prefs.getInt(KEY_STREAK_COUNT, 0);

        Calendar today = Calendar.getInstance();
        Calendar last = Calendar.getInstance();
        last.setTimeInMillis(lastLogin);

        if (isSameDay(today, last)) {
            return; // Already recorded today
        }

        if (isYesterday(today, last)) {
            currentStreak++;
        } else {
            currentStreak = 1; // Streak broken
        }

        int highest = prefs.getInt(KEY_HIGHEST_STREAK, 0);
        if (currentStreak > highest) {
            highest = currentStreak;
        }

        prefs.edit()
                .putLong(KEY_LAST_LOGIN, today.getTimeInMillis())
                .putInt(KEY_STREAK_COUNT, currentStreak)
                .putInt(KEY_HIGHEST_STREAK, highest)
                .apply();
    }

    public static int getStreakCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastLogin = prefs.getLong(KEY_LAST_LOGIN, 0);
        Calendar last = Calendar.getInstance();
        last.setTimeInMillis(lastLogin);
        Calendar today = Calendar.getInstance();

        if (isSameDay(today, last) || isYesterday(today, last)) {
            return prefs.getInt(KEY_STREAK_COUNT, 0);
        } else {
            return 0; // Streak timed out
        }
    }

    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean isYesterday(Calendar today, Calendar last) {
        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        return isSameDay(yesterday, last);
    }
}
