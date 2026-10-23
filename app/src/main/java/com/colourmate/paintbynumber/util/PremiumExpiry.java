package com.colourmate.paintbynumber.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.android.billingclient.api.Purchase;

import java.util.Calendar;

/**
 * "Your Premium ends in 3 days." Play tells the app the subscription's product, its original
 * purchase time and whether it will renew; from that the current period's end is a date, and
 * the app says so once a day during the last {@link #WARN_DAYS} days — before the plan ends,
 * never after the ads have already come back.
 */
public final class PremiumExpiry {

    private static final String PREFS = "premium_sub";
    private static final int WARN_DAYS = 4;

    private PremiumExpiry() {}

    public static void remember(Context c, Purchase p) {
        String product = p.getProducts().isEmpty() ? "" : p.getProducts().get(0);
        prefs(c).edit().putString("product", product).putLong("since", p.getPurchaseTime())
                .putBoolean("renews", p.isAutoRenewing()).apply();
    }

    public static void forget(Context c) {
        prefs(c).edit().remove("product").remove("since").remove("renews").apply();
    }

    /** End of the period the subscriber is in now, or 0 when there is no subscription. */
    public static long periodEnd(Context c) {
        SharedPreferences s = prefs(c);
        long since = s.getLong("since", 0);
        if (since == 0) return 0;
        int field = s.getString("product", "").contains("year") ? Calendar.YEAR : Calendar.MONTH;
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(since);
        long now = System.currentTimeMillis();
        while (end.getTimeInMillis() <= now) end.add(field, 1);
        return end.getTimeInMillis();
    }

    public static void maybeNotify(Activity a) {
        long end = periodEnd(a);
        if (end == 0) return;
        long days = (end - System.currentTimeMillis() + 86_399_000L) / 86_400_000L;
        if (days > WARN_DAYS) return;
        SharedPreferences s = prefs(a);
        String today = String.valueOf(System.currentTimeMillis() / 86_400_000L);
        if (today.equals(s.getString("warned", ""))) return;
        s.edit().putString("warned", today).apply();

        boolean renews = s.getBoolean("renews", true);
        String when = days <= 1 ? "tomorrow" : "in " + days + " days";
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle("Premium")
                .setNegativeButton("OK", null);
        if (renews) {
            b.setMessage("Your Premium plan renews " + when + ". Thanks for colouring with us!");
        } else {
            b.setMessage("Your Premium plan ends " + when + ". Renew to keep ads off and every picture unlocked.")
             .setPositiveButton("Keep Premium", (d, w) -> a.startActivity(new Intent(Intent.ACTION_VIEW,
                     Uri.parse("https://play.google.com/store/account/subscriptions?sku="
                             + s.getString("product", "") + "&package=" + a.getPackageName()))));
        }
        b.show();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
