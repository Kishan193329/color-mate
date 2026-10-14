package com.colourmate.paintbynumber.ads;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import com.colourmate.paintbynumber.R;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.app.Dialog;

/**
 * Central controller for global ad state and shared UI components.
 * Unifies logic between legacy AdsManager and new AdsManagerMediation.
 */
public class AdController {

    /**
     * Holistic check to see if ads should be displayed.
     * Considers both remote feature flags and local premium status.
     */
    public static boolean shouldShowAds(Context context) {
        // 1. Check if user has purchased "Remove Ads"
        if (AppPreference.getBoolean(context, Keys.KEY_IS_PREMIUM, false)) {
            return false;
        }

        // 2. Check if ads are enabled globally from Firebase
        return AppPreference.getBoolean(context, Keys.PLAY_VIDEO, true);
    }

    /**
     * Shared confirmation dialog for rewarded ads.
     */
    public static void showRewardConfirmation(Activity activity, Runnable onAccept, Runnable onCancel) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        
        String title = AppPreference.getString(activity, Keys.KEY_REWARD_CONFIRM_TITLE, "Watch Video?");
        String msg = AppPreference.getString(activity, Keys.KEY_REWARD_CONFIRM_MSG,
                "Watch a short video to get your reward.");

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.book_two_btn_dialog);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(lp);
        }

        TextView tvTitle = dialog.findViewById(R.id.dialog_title);
        TextView tvMsg = dialog.findViewById(R.id.dialog_text);
        View btnCancel = dialog.findViewById(R.id.btn_left_btn);
        View btnAccept = dialog.findViewById(R.id.iv_right_btn);
        TextView tvAccept = dialog.findViewById(R.id.tv_right_btn);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvMsg != null) tvMsg.setText(msg);
        if (tvAccept != null) tvAccept.setText("Watch");

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                dialog.dismiss();
                if (onCancel != null) onCancel.run();
            });
        }

        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                v.setEnabled(false);
                if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                    AdsManagerMediation.showLoadingDialog((androidx.appcompat.app.AppCompatActivity) activity);
                }
                if (onAccept != null) onAccept.run();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    /**
     * Makes a full-screen ad dialog actually cover the screen.
     *
     * <p>A Dialog gets its own Window, and that window does NOT inherit the activity's
     * edge-to-edge layout. So the sheet stopped at the status bar and left a strip of the screen
     * underneath showing through above it — the home header was visible over the top of the
     * loading screen. Pushing the dialog behind the system bars fixes it, and since the sheet is
     * light, the bar icons have to be dark.
     */
    public static void makeFullScreen(Dialog dialog) {
        if (dialog == null) return;

        Window window = dialog.getWindow();
        if (window == null) return;

        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }

    /**
     * The banner slot ships with a "Loading Ads" spinner inside it, and the ad loaders only replace
     * that placeholder once an ad actually arrives. If a request never comes back — no fill, a dead
     * network, a mediation chain still retrying — the placeholder stays on screen for good and reads
     * as a broken ad. Give it a deadline: if nothing real has been put in the slot by then, hide it.
     *
     * <p>Posted on the container itself, so it is dropped automatically when the screen goes away.
     */
    public static void hideBannerSlotIfEmpty(final View slot, final ViewGroup container,
            long timeoutMs) {
        if (container == null) {
            return;
        }
        container.postDelayed(() -> {
            if (containsAd(container)) {
                return;
            }
            (slot != null ? slot : container).setVisibility(View.GONE);
        }, timeoutMs);
    }

    private static boolean containsAd(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof com.google.android.gms.ads.AdView
                    || child instanceof com.google.android.gms.ads.nativead.NativeAdView) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if back-navigation ads are allowed.
     */
    public static boolean isBackAdEnabled(Context context) {
        // Always false since the Play Disruptive Ads strike (2026-10-14): an ad on Back is the
        // textbook "unexpected" placement, and the remote switch must not be able to re-enable it.
        return false;
    }
}
