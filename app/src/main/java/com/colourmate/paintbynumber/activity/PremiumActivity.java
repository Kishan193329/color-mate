package com.colourmate.paintbynumber.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.android.billingclient.api.ProductDetails;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.ads.AppPreference;
import com.colourmate.paintbynumber.ads.IAPManager;
import com.colourmate.paintbynumber.ads.Keys;

public class PremiumActivity extends AppCompatActivity {

    private static final String TAG = "PremiumActivity";

    /** When true, closing this screen starts {@link MainActivity} (opened right after splash). */
    public static final String EXTRA_LAUNCHED_FROM_SPLASH = "launched_from_splash";

    private boolean launchedFromSplash;

    private LinearLayout cardMonthly, cardYearly;
    private TextView tvYearlyNote, tvPopularBadge;
    private TextView tvMonthlyPrice, tvYearlyPrice, btnContinue;
    /** Yearly is the recommended plan, carries the badge, and leads — so it starts selected. */
    private boolean isYearlySelected = true;

    private IAPManager iapManager;
    private IAPManager.PurchaseListener purchaseListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchedFromSplash = getIntent().getBooleanExtra(EXTRA_LAUNCHED_FROM_SPLASH, false);

        setContentView(R.layout.activity_premium);

        setupTransparentStatusBar();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closePremiumScreen();
            }
        });
        initViews();
        buildFeatureList();
        loadHeroArt();
        setupListeners();
        updatePlanSelection();
        initSubscriptionPlans();
    }

    /** After splash flow: go to Main. From Main: only finish. */
    private void closePremiumScreen() {
        if (launchedFromSplash) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        finish();
    }

    private void setupTransparentStatusBar() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        // Premium is the app's one dark screen, so the system bars have to flip with it:
        // light icons on top, and a dark navigation bar instead of a white strip under the CTA.
        window.setNavigationBarColor(getResources().getColor(R.color.cm_paper));
        WindowCompat.setDecorFitsSystemWindows(window, false);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        // The screen now sits on the same paper ground as Home, so both bars take dark icons.
        // Get this pair wrong and the clock disappears — it has been wrong in both directions on
        // this screen already, once when it was dark and once when it was light.
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        View topArea = findViewById(R.id.btn_close);
        if (topArea != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topArea, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                if (params != null) {
                    int extra = (int) (12 * getResources().getDisplayMetrics().density + 0.5f);
                    params.topMargin = bars.top + extra;
                    v.setLayoutParams(params);
                }
                return insets;
            });
        }
    }

    /**
     * Introduces the emblem.
     *
     * <p>There is no bitmap to fetch any more — the mark is a vector set in the layout, so there is
     * nothing to decode off the main thread and nothing to wait for. (It used to be a 1024x1536
     * PNG loaded through Glide.) What is left is the part worth keeping: the mark arrives rather
     * than simply being there, which is what made the screen feel deliberate.
     */
    private void loadHeroArt() {
        android.widget.ImageView hero = findViewById(R.id.iv_premium_hero);
        if (hero == null) return;

        hero.setAlpha(0f);
        hero.setScaleX(0.82f);
        hero.setScaleY(0.82f);
        hero.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(60)
                .setDuration(520)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f))
                .start();
    }

    private void initViews() {
        cardMonthly = findViewById(R.id.card_monthly);
        cardYearly = findViewById(R.id.card_yearly);
        tvMonthlyPrice = findViewById(R.id.tv_monthly_price);
        tvYearlyPrice = findViewById(R.id.tv_yearly_price);
        btnContinue = findViewById(R.id.btn_continue);
        tvYearlyNote = findViewById(R.id.tv_yearly_note);
        tvPopularBadge = findViewById(R.id.tv_popular_badge);
    }

    private void setupListeners() {
        findViewById(R.id.btn_close).setOnClickListener(v -> closePremiumScreen());

        View.OnClickListener restoreClick = v -> {
            Toast.makeText(this, "Restoring purchases...", Toast.LENGTH_SHORT).show();
            if (iapManager != null) {
                iapManager.queryPurchases();
            }
        };
        View restoreFooter = findViewById(R.id.tv_restore_footer);
        if (restoreFooter != null) {
            restoreFooter.setOnClickListener(restoreClick);
        }

        cardMonthly.setOnClickListener(v -> {
            if (!isYearlySelected) return;
            isYearlySelected = false;
            updatePlanSelection();
        });

        cardYearly.setOnClickListener(v -> {
            if (isYearlySelected) return;
            isYearlySelected = true;
            updatePlanSelection();
        });

        btnContinue.setOnClickListener(v -> {
            if (iapManager == null) return;
            if (isYearlySelected) {
                iapManager.purchaseYearly(this);
            } else {
                iapManager.purchaseMonthly(this);
            }
        });

    }

    private void initSubscriptionPlans() {
        tvMonthlyPrice.setText("...");
        tvYearlyPrice.setText("...");

        iapManager = IAPManager.getInstance(this);

        setExistingPrices();

        purchaseListener = new IAPManager.PurchaseListener() {
            @Override
            public void onProductDetailsLoaded(IAPManager.PlanType planType,
                                               ProductDetails details) {
                runOnUiThread(() -> {
                    String price = iapManager.getFormattedPrice(planType);
                    if (price == null) return;

                    switch (planType) {
                        case MONTHLY:
                            tvMonthlyPrice.setText(price);
                            Log.d(TAG, "Monthly price set: " + price);
                            break;
                        case YEARLY:
                            tvYearlyPrice.setText(price);
                            Log.d(TAG, "Yearly price set: " + price);
                            break;
                    }
                    updateContinueButtonText();
                    updateYearlyValue();
                });
            }

            @Override
            public void onPurchaseSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(PremiumActivity.this,
                            "Premium activated! Ads removed.", Toast.LENGTH_LONG).show();
                    closePremiumScreen();
                });
            }

            @Override
            public void onPurchaseFailed(String error) {
                runOnUiThread(() ->
                        Toast.makeText(PremiumActivity.this,
                                error, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onPremiumStatusChecked(boolean isPremium) {
                runOnUiThread(() -> {
                    if (isPremium) {
                        Toast.makeText(PremiumActivity.this,
                                "You already have premium!", Toast.LENGTH_SHORT).show();
                        closePremiumScreen();
                    }
                });
            }
        };

        iapManager.addListener(purchaseListener);
        iapManager.startConnection();

        if (AppPreference.getBoolean(this, Keys.KEY_IS_PREMIUM, false)) {
            Toast.makeText(this, "You already have premium!", Toast.LENGTH_SHORT).show();
            closePremiumScreen();
        }
    }


    /**
     * Spells out what the yearly plan actually costs per month, and how much it saves against
     * paying monthly — computed from the real Play prices, so it stays correct in every currency
     * and every time the owner changes a price in the console.
     */
    private void updateYearlyValue() {
        if (iapManager == null) return;

        long yearlyMicros = iapManager.getPriceAmountMicros(IAPManager.PlanType.YEARLY);
        long monthlyMicros = iapManager.getPriceAmountMicros(IAPManager.PlanType.MONTHLY);
        String currency = iapManager.getPriceCurrencyCode(IAPManager.PlanType.YEARLY);

        if (yearlyMicros <= 0L) {
            if (tvYearlyNote != null) tvYearlyNote.setVisibility(View.GONE);
            return;
        }

        if (tvYearlyNote != null) {
            String perMonth = formatMicros(yearlyMicros / 12L, currency);
            tvYearlyNote.setText(getString(R.string.premium_per_month, perMonth));
            tvYearlyNote.setVisibility(View.VISIBLE);
        }

        // The badge only claims a saving when there genuinely is one.
        if (tvPopularBadge != null) {
            long twelveMonths = monthlyMicros * 12L;
            if (monthlyMicros > 0L && twelveMonths > yearlyMicros) {
                int percent = (int) Math.round(100.0 * (twelveMonths - yearlyMicros) / twelveMonths);
                tvPopularBadge.setText(getString(R.string.premium_saving, percent));
            }
            tvPopularBadge.setVisibility(View.VISIBLE);
        }
    }

    private String formatMicros(long micros, String currencyCode) {
        double amount = micros / 1_000_000.0;
        try {
            java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance();
            if (currencyCode != null && !currencyCode.isEmpty()) {
                nf.setCurrency(java.util.Currency.getInstance(currencyCode));
            }
            return nf.format(amount);
        } catch (Exception e) {
            return String.format(java.util.Locale.getDefault(), "%.2f", amount);
        }
    }


    /** The four things someone is actually buying, each with a turquoise confirmation tick. */
    private void buildFeatureList() {
        LinearLayout container = findViewById(R.id.premium_features);
        if (container == null) return;
        container.removeAllViews();

        String[] features = {
                getString(R.string.premium_feature_ads),
                getString(R.string.premium_feature_artworks),
                getString(R.string.premium_feature_hints),
                getString(R.string.premium_feature_early)
        };

        float density = getResources().getDisplayMetrics().density;
        int tick = Math.round(20 * density);
        int gap = Math.round(12 * density);
        int rowGap = Math.round(10 * density);

        for (int i = 0; i < features.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowParams.topMargin = rowGap;
            row.setLayoutParams(rowParams);

            android.widget.ImageView check = new android.widget.ImageView(this);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(tick, tick);
            checkParams.rightMargin = gap;
            check.setLayoutParams(checkParams);
            check.setBackgroundResource(R.drawable.cm_premium_tick);
            check.setImageResource(R.drawable.ic_cm_check);
            check.setPadding(Math.round(4 * density), Math.round(4 * density),
                    Math.round(4 * density), Math.round(4 * density));

            TextView label = new TextView(this);
            label.setText(features[i]);
            label.setTextColor(getResources().getColor(R.color.cm_ink));
            label.setTextSize(14f);
            label.setIncludeFontPadding(false);
            label.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(
                    this, R.font.quicksand_medium));

            row.addView(check);
            row.addView(label);
            container.addView(row);
        }
    }

    private void setExistingPrices() {
        String mPrice = iapManager.getFormattedPrice(IAPManager.PlanType.MONTHLY);
        if (mPrice != null) {
            tvMonthlyPrice.setText(mPrice);
        }

        String yPrice = iapManager.getFormattedPrice(IAPManager.PlanType.YEARLY);
        if (yPrice != null) {
            tvYearlyPrice.setText(yPrice);
        }

        // Details are often already cached from the splash query, in which case the listener below
        // never fires. Everything derived from a price has to be refreshed here too — the per-month
        // line AND the button, which otherwise sits on its "Continue" fallback forever.
        updateYearlyValue();
        updateContinueButtonText();
    }

    private void updatePlanSelection() {
        if (isYearlySelected) {
            cardYearly.setBackgroundResource(R.drawable.bg_premium_plan_row_peach);
            cardMonthly.setBackgroundResource(R.drawable.bg_premium_plan_row_outline);
        } else {
            cardMonthly.setBackgroundResource(R.drawable.bg_premium_plan_row_peach);
            cardYearly.setBackgroundResource(R.drawable.bg_premium_plan_row_outline);
        }
        updateContinueButtonText();
    }

    /**
     * The button states the commitment it is about to make.
     *
     * <p>"Continue" tells the buyer nothing — they have to look back up at the cards to remember
     * what they are agreeing to and at what price. Naming the plan and the amount on the button
     * removes that doubt at the exact moment it matters.
     */
    private void updateContinueButtonText() {
        if (btnContinue == null) return;

        String price = iapManager == null ? null : iapManager.getFormattedPrice(
                isYearlySelected ? IAPManager.PlanType.YEARLY : IAPManager.PlanType.MONTHLY);

        if (price == null || price.isEmpty()) {
            btnContinue.setText(R.string.premium_continue);      // prices not in yet
            return;
        }

        btnContinue.setText(getString(
                isYearlySelected ? R.string.premium_cta_yearly : R.string.premium_cta_monthly,
                price));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (iapManager != null && purchaseListener != null) {
            iapManager.removeListener(purchaseListener);
        }
    }
}
