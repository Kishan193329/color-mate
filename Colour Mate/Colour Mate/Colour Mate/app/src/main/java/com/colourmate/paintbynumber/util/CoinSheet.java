package com.colourmate.paintbynumber.util;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.PremiumActivity;
import com.colourmate.paintbynumber.ads.AdController;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.ads.RewardedAdListener;

/**
 * The coin purse: balance, earn with a video, buy a pack, invite a friend, or go Premium.
 * One sheet for every screen that needs coins, so Home, a locked picture and the Hit card all
 * offer the same ways out. {@code onChange} runs whenever the balance moves.
 */
public final class CoinSheet {

    private CoinSheet() {}

    public static void show(AppCompatActivity a, Runnable onChange) {
        final Dialog sheet = new Dialog(a, R.style.FullWidthDialog);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sheet.setContentView(R.layout.dialog_coins);
        Window w = sheet.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView balance = sheet.findViewById(R.id.tv_coins_balance);
        Runnable paint = () -> {
            balance.setText(CoinManager.format(CoinManager.balance(a)));
            if (onChange != null) onChange.run();
        };
        paint.run();

        boolean adsOn = AdController.shouldShowAds(a);
        View watch = sheet.findViewById(R.id.btn_coins_watch);
        watch.setVisibility(adsOn ? View.VISIBLE : View.GONE);
        sheet.findViewById(R.id.btn_coins_premium).setVisibility(adsOn ? View.VISIBLE : View.GONE);
        watch.setOnClickListener(v -> watchForCoins(a, watch, paint));
        sheet.findViewById(R.id.btn_coins_premium).setOnClickListener(v -> {
            sheet.dismiss();
            a.startActivity(new Intent(a, PremiumActivity.class));
        });

        bindPacks(a, sheet, paint);
        bindReferral(a, sheet, paint);
        sheet.findViewById(R.id.btn_coins_close).setOnClickListener(v -> sheet.dismiss());
        sheet.setOnDismissListener(d -> CoinStore.get(a).setListener(null));
        sheet.show();
    }

    private static void watchForCoins(AppCompatActivity a, View watch, Runnable paint) {
        watch.setEnabled(false);
        MediationManager.showRewardedAd(a, new RewardedAdListener() {
            @Override public void onRewardGranted() {
                a.runOnUiThread(() -> {
                    CoinManager.add(a, CoinManager.REWARD_AD_COINS);
                    paint.run();
                    watch.setEnabled(true);
                    toast(a, "+" + CoinManager.REWARD_AD_COINS + " coins 🪙");
                });
            }
            // No video: nothing is granted (a free grant here would be farmable), the button returns.
            @Override public void onRewardNull() {
                a.runOnUiThread(() -> { watch.setEnabled(true); toast(a, "No video right now — try again in a moment"); });
            }
            @Override public void onRewardProgress() { }
            @Override public void onVideoSkipped() { a.runOnUiThread(() -> watch.setEnabled(true)); }
        });
    }

    private static void bindPacks(AppCompatActivity a, Dialog sheet, Runnable paint) {
        CoinStore store = CoinStore.get(a);
        int[] ids = {R.id.btn_pack_0, R.id.btn_pack_1, R.id.btn_pack_2, R.id.btn_pack_3};
        Runnable label = () -> {
            for (int i = 0; i < ids.length; i++) {
                String id = CoinStore.PACKS[i];
                String price = store.price(id);
                ((TextView) sheet.findViewById(ids[i])).setText(String.format(java.util.Locale.US,
                        "%,d 🪙\n%s", CoinStore.coinsFor(id), price == null ? "…" : price));
            }
        };
        label.run();
        store.setListener(() -> { label.run(); paint.run(); });
        for (int i = 0; i < ids.length; i++) {
            String id = CoinStore.PACKS[i];
            sheet.findViewById(ids[i]).setOnClickListener(v -> {
                if (!store.buy(a, id)) toast(a, "The store is not ready yet — try again in a moment");
            });
        }
    }

    private static void bindReferral(AppCompatActivity a, Dialog sheet, Runnable paint) {
        TextView share = sheet.findViewById(R.id.btn_ref_share);
        share.setOnClickListener(v -> Referral.myCode(a, r -> {
            if (r == null || !r.has("code")) { toast(a, "Could not reach the server — try again"); return; }
            String code = r.optString("code");
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                    "Colour with me on Colour Mate! Install it and enter my code " + code
                            + " for 100 free coins: https://play.google.com/store/apps/details?id=" + a.getPackageName());
            a.startActivity(Intent.createChooser(send, "Invite a friend"));
        }));

        EditText input = sheet.findViewById(R.id.et_ref_code);
        sheet.findViewById(R.id.btn_ref_redeem).setOnClickListener(v -> {
            String code = input.getText().toString().trim();
            if (code.isEmpty()) return;
            v.setEnabled(false);
            Referral.redeem(a, code, r -> {
                v.setEnabled(true);
                if (r == null) { toast(a, "Could not reach the server — try again"); return; }
                if (!r.optBoolean("ok")) { toast(a, r.optString("reason", "That code did not work")); return; }
                int welcome = r.optInt("welcome");
                CoinManager.add(a, welcome);
                paint.run();
                input.setText("");
                toast(a, "+" + welcome + " welcome coins 🪙");
            });
        });
    }

    private static void toast(AppCompatActivity a, String m) {
        Toast.makeText(a, m, Toast.LENGTH_SHORT).show();
    }
}
