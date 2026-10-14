package com.colourmate.paintbynumber.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.colourmate.paintbynumber.R;

/**
 * Puts {@link RatingManager}'s decision on screen.
 *
 * <p>This is our own dialog rather than Play's in-app review sheet, for one reason that matters to
 * the rules: the Play API deliberately refuses to tell you whether the user actually rated, so
 * "rated → never ask again" cannot be implemented on top of it. Asking first, then handing off to
 * the Store, keeps the app in control of that answer.
 */
public final class RatingPrompt {

    private static final String TAG = "RatingPrompt";

    /** One prompt per process, however many times the host screen is resumed. */
    private static boolean shownThisSession = false;

    private RatingPrompt() {
    }

    /**
     * Shows the prompt if it is due. Cheap and safe to call whenever the user lands somewhere calm.
     *
     * @return true if the dialog was shown
     */
    public static boolean maybeShow(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        if (shownThisSession) return false;

        long now = System.currentTimeMillis();
        if (!RatingManager.shouldShow(activity, now)) return false;

        show(activity, now);
        return true;
    }

    private static void show(Activity activity, long now) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_rate_app);

        // Dismissing by back or by tapping outside counts as "not now" — the user said no by
        // walking away, and the 7-day window below already covers it.
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        LinearLayout stars = dialog.findViewById(R.id.ll_rate_stars);
        if (stars != null) {
            int size = Math.round(28 * activity.getResources().getDisplayMetrics().density);
            int gap = Math.round(3 * activity.getResources().getDisplayMetrics().density);
            for (int i = 0; i < 5; i++) {
                ImageView star = new ImageView(activity);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins(gap, 0, gap, 0);
                star.setLayoutParams(lp);
                star.setImageResource(R.drawable.ic_cm_star);
                star.setAlpha(0f);
                star.setScaleX(0.5f);
                star.setScaleY(0.5f);
                stars.addView(star);

                // Stars land one after another, so the row arrives as a gesture rather than a wall.
                star.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay(90L * i)
                        .setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f))
                        .start();
            }
        }

        View rateNow = dialog.findViewById(R.id.btn_rate_now);
        if (rateNow != null) {
            rateNow.setOnClickListener(v -> {
                // Treated as done: the app cannot see what the user types into the Play Store, and
                // sending someone back to a prompt they already acted on is the worse mistake.
                RatingManager.markRated(activity);
                openPlayStore(activity);
                dialog.dismiss();
            });
        }

        View later = dialog.findViewById(R.id.btn_rate_later);
        if (later != null) {
            later.setOnClickListener(v -> dialog.dismiss());
        }

        // Recorded when it is actually on screen, not when it was decided — so a dialog that never
        // appeared (activity finishing, window token gone) does not consume the 7-day window.
        try {
            dialog.show();
        } catch (Exception e) {
            Log.w(TAG, "Could not show rating dialog", e);
            return;
        }

        shownThisSession = true;
        RatingManager.markShown(activity, now);
    }

    private static void openPlayStore(Activity activity) {
        String pkg = activity.getPackageName();
        try {
            Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(market);
        } catch (ActivityNotFoundException e) {
            // No Play Store app (emulators, some devices) — the web listing still works.
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            } catch (Exception ignored) {
                Log.w(TAG, "No way to open the store listing", ignored);
            }
        }
    }
}
