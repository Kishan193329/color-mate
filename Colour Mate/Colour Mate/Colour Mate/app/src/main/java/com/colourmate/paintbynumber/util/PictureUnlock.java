package com.colourmate.paintbynumber.util;

import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.colourmate.paintbynumber.ads.AppPreference;
import com.colourmate.paintbynumber.ads.Keys;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.ads.RewardedAdListener;
import com.colourmate.paintbynumber.model.model.Level;

/**
 * The gate in front of our tiered pictures (admin → Tier). "premium" costs coins, "reward" costs
 * one rewarded video, "free" costs nothing — and Premium subscribers skip the gate entirely.
 * An unlock is kept for good, so a picture is never charged twice.
 */
public final class PictureUnlock {

    private PictureUnlock() {}

    public static boolean isLocked(android.content.Context a, Level level) {
        return level != null && level.isColorForge() && level.isPaidTier()
                && !AppPreference.getBoolean(a, Keys.KEY_IS_PREMIUM, false)
                && !CoinManager.isUnlocked(a, level.getFileName());
    }

    /** "🪙 150" or "▶ Ad" on a locked tile's chip. False — and nothing touched — when unlocked. */
    public static boolean showBadge(android.widget.TextView chip, Level level) {
        if (chip == null || !isLocked(chip.getContext(), level)) return false;
        chip.setText("reward".equals(level.getTier()) ? "▶ Ad" : "🪙 " + CoinManager.PREMIUM_PICTURE_COST);
        chip.setVisibility(android.view.View.VISIBLE);
        return true;
    }

    /** Runs {@code open} straight away, or once the player has paid for the picture. */
    public static void open(AppCompatActivity a, Level level, Runnable open) {
        if (!isLocked(a, level)) { open.run(); return; }
        if ("reward".equals(level.getTier())) { unlockWithVideo(a, level, open); return; }

        int cost = CoinManager.PREMIUM_PICTURE_COST;
        int have = CoinManager.balance(a);
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle("Premium picture")
                .setMessage("Unlock it for " + cost + " coins — it stays yours.\nYou have " + have + " coins.")
                .setNegativeButton("Not now", null);
        if (have >= cost) {
            b.setPositiveButton("Unlock · " + cost + " 🪙", (d, w) -> {
                if (CoinManager.trySpend(a, cost)) {
                    CoinManager.markUnlocked(a, level.getFileName());
                    open.run();
                }
            });
        } else {
            b.setPositiveButton("Get coins", (d, w) -> CoinSheet.show(a, null));
        }
        b.show();
    }

    private static void unlockWithVideo(AppCompatActivity a, Level level, Runnable open) {
        MediationManager.showRewardedAd(a, new RewardedAdListener() {
            @Override public void onRewardGranted() {
                CoinManager.markUnlocked(a, level.getFileName());
                a.runOnUiThread(open);
            }
            // No video to show: let them in rather than lock them out over an ad-network gap.
            @Override public void onRewardNull() { a.runOnUiThread(open); }
            @Override public void onRewardProgress() { }
            @Override public void onVideoSkipped() { }
        });
    }
}
