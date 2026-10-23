package com.colourmate.paintbynumber.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.colourmate.paintbynumber.util.PremiumExpiry;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IAPManager {

    private static final String TAG = "IAPManager";
    private static final int MAX_RETRY = 3;

    // ✅ NEW: Separate SKUs for Monthly & Yearly
    private static final String MONTHLY_SKU = "colour_mate_monthly";
    private static final String YEARLY_SKU = "colour_mate_yearly";

    // ✅ NEW: Set of all premium SKUs for easy checking
    private static final Set<String> PREMIUM_SKUS = new HashSet<>(Arrays.asList(
            MONTHLY_SKU, YEARLY_SKU
    ));

    private static volatile IAPManager instance;

    private final BillingClient billingClient;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ✅ NEW: Separate ProductDetails for each plan
    private ProductDetails monthlyDetails;
    private ProductDetails yearlyDetails;

    // Thread-safe counters/flags
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    /** Guards against asking Play for entitlements on every screen resume. */
    private static final long ENTITLEMENT_REFRESH_MIN_INTERVAL_MS = 60_000L;
    private volatile long lastEntitlementCheck = 0L;

    // Track premium result
    private volatile boolean inAppHasPremium = false;
    private volatile boolean subsHasPremium = false;
    private volatile boolean inAppChecked = false;
    private volatile boolean subsChecked = false;
    private volatile boolean queryFailed = false;

    private final List<PurchaseListener> listeners =
            Collections.synchronizedList(new ArrayList<>());

    // ─── Plan Type Enum ───────────────────────────────────────────────────────

    // ✅ NEW: Enum to identify plan type
    public enum PlanType {
        MONTHLY,
        YEARLY
    }

    // ─── Listener Interface ───────────────────────────────────────────────────

    public interface PurchaseListener {
        void onPurchaseSuccess();

        void onPurchaseFailed(String error);

        void onPremiumStatusChecked(boolean isPremium);

        // ✅ UPDATED: Include plan type in callback
        void onProductDetailsLoaded(PlanType planType, ProductDetails details);
    }

    // ─── PurchasesUpdatedListener ─────────────────────────────────────────────

    private final PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
        int code = billingResult.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            notifyFailed("Purchase cancelled by user");
        } else {
            notifyFailed("Purchase failed: " + billingResult.getDebugMessage());
        }
    };

    // ─── Constructor / Singleton ──────────────────────────────────────────────

    private IAPManager(Context context) {
        this.context = context.getApplicationContext();

        PendingPurchasesParams pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();

        this.billingClient = BillingClient.newBuilder(this.context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(pendingParams)
                .build();
    }

    public static IAPManager getInstance(Context context) {
        if (instance == null) {
            synchronized (IAPManager.class) {
                if (instance == null) {
                    instance = new IAPManager(context);
                }
            }
        }
        return instance;
    }

    // ─── Listener Management ──────────────────────────────────────────────────

    public void addListener(PurchaseListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(PurchaseListener listener) {
        listeners.remove(listener);
    }

    // ─── Connection Management ────────────────────────────────────────────────

    public void startConnection() {
        if (billingClient.isReady()) {
            onConnected();
            return;
        }
        if (!isConnecting.compareAndSet(false, true)) return;

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                isConnecting.set(false);
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    retryCount.set(0);
                    onConnected();
                } else {
                    Log.w(TAG, "Billing setup failed: " + result.getDebugMessage());
                    retryConnection();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                isConnecting.set(false);
                Log.w(TAG, "Billing service disconnected");
                retryConnection();
            }
        });
    }

    private void retryConnection() {
        int attempt = retryCount.incrementAndGet();
        if (attempt <= MAX_RETRY) {
            long delayMs = (long) Math.pow(2, attempt) * 1000L;
            Log.d(TAG, "Retrying connection in " + delayMs + "ms (attempt " + attempt + ")");
            mainHandler.postDelayed(this::startConnection, delayMs);
        } else {
            Log.e(TAG, "Max billing connection retries reached");
        }
    }

    private void onConnected() {
        Log.d(TAG, "Billing connected");
        resetPremiumCheckState();
        queryPurchases();
        queryAllProductDetails();
    }

    private void resetPremiumCheckState() {
        inAppHasPremium = false;
        subsHasPremium = false;
        inAppChecked = false;
        subsChecked = false;
        queryFailed = false;
    }

    // ─── Query: Purchases ─────────────────────────────────────────────────────

    public void queryPurchases() {
        resetPremiumCheckState();
        queryPurchasesForType(BillingClient.ProductType.INAPP);
        queryPurchasesForType(BillingClient.ProductType.SUBS);
    }

    private void queryPurchasesForType(String productType) {
        if (!billingClient.isReady()) return;

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            boolean ok = billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK;
            boolean hasPremium = false;
            boolean isSubs = productType.equals(BillingClient.ProductType.SUBS);

            if (ok) {
                for (Purchase p : purchases) {
                    if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                    // Every subscription we sell is Premium, and every one-time product that is
                    // not a coin pack is Premium (a lifetime purchase from an older version) — so a
                    // paying user keeps premium even if a product id was renamed since they bought.
                    boolean premium = isSubs || !isCoinPack(p);
                    if (!premium) continue;
                    hasPremium = true;
                    if (!p.isAcknowledged()) acknowledgePurchase(p);
                    if (isSubs) PremiumExpiry.remember(context, p);
                }
                if (isSubs && !hasPremium) PremiumExpiry.forget(context);
            }

            synchronized (this) {
                if (!ok) queryFailed = true;
                if (isSubs) { subsHasPremium = hasPremium; subsChecked = true; }
                else        { inAppHasPremium = hasPremium; inAppChecked = true; }

                if (inAppChecked && subsChecked) {
                    if (queryFailed) {
                        // Play did not answer properly — keep whatever premium state we already
                        // have. Revoking on a billing hiccup would take premium away from people
                        // who paid for it.
                        Log.w(TAG, "Purchase query failed — premium left as is");
                    } else {
                        updatePremiumStatus(inAppHasPremium || subsHasPremium);
                    }
                }
            }
        });
    }

    private static boolean isCoinPack(Purchase p) {
        for (String id : p.getProducts()) {
            if (com.colourmate.paintbynumber.util.CoinStore.coinsFor(id) > 0) return true;
        }
        return false;
    }

    // ─── Query: Product Details ───────────────────────────────────────────────

    // ✅ NEW: Query both monthly and yearly product details
    public void queryAllProductDetails() {
        queryProductDetailsForSku(MONTHLY_SKU, PlanType.MONTHLY);
        queryProductDetailsForSku(YEARLY_SKU, PlanType.YEARLY);
    }

    // ✅ NEW: Query product details for a specific SKU
    private void queryProductDetailsForSku(String sku, PlanType planType) {
        // Try SUBS first (primary), then INAPP as fallback
        queryProductDetailsForType(sku, BillingClient.ProductType.SUBS, planType);
        queryProductDetailsForType(sku, BillingClient.ProductType.INAPP, planType);
    }

    // ✅ UPDATED: Accept planType parameter
    private void queryProductDetailsForType(String sku, String type, PlanType planType) {
        if (!billingClient.isReady()) return;

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(sku)
                                .setProductType(type)
                                .build()
                ))
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, queryProductDetailsResult) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                List<ProductDetails> productDetailsList = queryProductDetailsResult.getProductDetailsList();
                if (productDetailsList != null && !productDetailsList.isEmpty()) {
                    synchronized (IAPManager.this) {
                        ProductDetails details = productDetailsList.get(0);

                        // ✅ Store in correct variable based on plan type
                        switch (planType) {
                            case MONTHLY:
                                if (monthlyDetails == null) {
                                    monthlyDetails = details;
                                    Log.d(TAG, "Monthly details loaded: " + getFormattedPrice(PlanType.MONTHLY));
                                    notifyProductDetailsLoaded(PlanType.MONTHLY, monthlyDetails);
                                }
                                break;

                            case YEARLY:
                                if (yearlyDetails == null) {
                                    yearlyDetails = details;
                                    Log.d(TAG, "Yearly details loaded: " + getFormattedPrice(PlanType.YEARLY));
                                    notifyProductDetailsLoaded(PlanType.YEARLY, yearlyDetails);
                                }
                                break;
                        }
                    }
                }
            }
        });
    }

    // ─── Purchase Flow ────────────────────────────────────────────────────────

    // ✅ NEW: Purchase by plan type
    public void purchasePlan(Activity activity, PlanType planType) {
        if (activity == null || activity.isFinishing()) {
            notifyFailed("Activity is not available");
            return;
        }

        ProductDetails details = getDetailsForPlan(planType);

        if (details == null) {
            notifyFailed("Product details not loaded for "
                    + planType.name() + ". Please try again.");
            return;
        }

        BillingFlowParams.ProductDetailsParams.Builder productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details);

        // Attach offer token for subscriptions.
        //
        // Was offers.get(0) — whichever offer Play happened to list first. A subscription usually
        // carries several (base plan, intro price, free trial), and blindly taking the first can
        // charge someone who was promised a trial, or pick an offer they are not eligible for.
        if (details.getProductType().equals(BillingClient.ProductType.SUBS)) {
            String token = pickBestOfferToken(details);
            if (token != null) {
                productParams.setOfferToken(token);
            }
        }

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                        Collections.singletonList(productParams.build()))
                .build();

        BillingResult launchResult = billingClient.launchBillingFlow(activity, flowParams);
        if (launchResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            notifyFailed("Could not launch billing flow: " + launchResult.getDebugMessage());
        }
    }

    // ✅ NEW: Convenience methods for monthly/yearly
    public void purchaseMonthly(Activity activity) {
        purchasePlan(activity, PlanType.MONTHLY);
    }

    public void purchaseYearly(Activity activity) {
        purchasePlan(activity, PlanType.YEARLY);
    }

    // ✅ KEPT for backward compatibility (defaults to yearly)
    public void purchaseRemoveAds(Activity activity) {
        purchaseYearly(activity);
    }

    // ─── Purchase Handling ────────────────────────────────────────────────────

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            // ✅ Check if purchase contains any of our premium SKUs
            boolean isPremiumPurchase = false;
            for (String sku : PREMIUM_SKUS) {
                if (purchase.getProducts().contains(sku)) {
                    isPremiumPurchase = true;
                    break;
                }
            }

            if (isPremiumPurchase) {
                updatePremiumStatus(true);
                notifySuccess();
                if (!purchase.isAcknowledged()) acknowledgePurchase(purchase);
            }
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase pending — waiting for completion");
        }
    }

    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.acknowledgePurchase(params, result -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged");
            } else {
                Log.w(TAG, "Acknowledge failed: " + result.getDebugMessage());
            }
        });
    }

    // ─── Helper: Get Details for Plan ─────────────────────────────────────────

    // ✅ NEW: Get ProductDetails by plan type
    private ProductDetails getDetailsForPlan(PlanType planType) {
        switch (planType) {
            case MONTHLY:
                return monthlyDetails;
            case YEARLY:
                return yearlyDetails;
            default:
                return null;
        }
    }

    // ✅ NEW: Get formatted price string for a plan
    public String getFormattedPrice(PlanType planType) {
        ProductDetails details = getDetailsForPlan(planType);
        if (details == null) return null;

        // Check subscription pricing first
        if (details.getSubscriptionOfferDetails() != null
                && !details.getSubscriptionOfferDetails().isEmpty()) {

            List<ProductDetails.SubscriptionOfferDetails> offers =
                    details.getSubscriptionOfferDetails();

            for (ProductDetails.SubscriptionOfferDetails offer : offers) {
                List<ProductDetails.PricingPhase> pricingPhases =
                        offer.getPricingPhases().getPricingPhaseList();
                if (!pricingPhases.isEmpty()) {
                    // Get the last phase (actual recurring price)
                    ProductDetails.PricingPhase phase =
                            pricingPhases.get(pricingPhases.size() - 1);
                    return phase.getFormattedPrice();
                }
            }
        }

        // Check one-time purchase pricing
        if (details.getOneTimePurchaseOfferDetails() != null) {
            return details.getOneTimePurchaseOfferDetails().getFormattedPrice();
        }

        return null;
    }

    // ✅ NEW: Get billing period text
    public String getBillingPeriodText(PlanType planType) {
        switch (planType) {
            case MONTHLY:
                return "/month";
            case YEARLY:
                return "/year";
            default:
                return "";
        }
    }

    // ✅ NEW: Check if specific plan details are loaded
    public boolean isMonthlyDetailsLoaded() {
        return monthlyDetails != null;
    }

    public boolean isYearlyDetailsLoaded() {
        return yearlyDetails != null;
    }

    public boolean areAllDetailsLoaded() {
        return monthlyDetails != null && yearlyDetails != null;
    }

    // ─── Status / Notifications ───────────────────────────────────────────────

    /**
     * Chooses which subscription offer to buy.
     *
     * <p>Prefers an offer that starts with a free phase (a trial), because that is what the screen
     * promises; otherwise falls back to the cheapest first-phase price, and finally to the first
     * offer Play returned. Google lists only offers the user is actually eligible for, so anything
     * here is safe to launch.
     */
    private String pickBestOfferToken(ProductDetails details) {
        List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
        if (offers == null || offers.isEmpty()) return null;

        String cheapestToken = null;
        long cheapestMicros = Long.MAX_VALUE;

        for (ProductDetails.SubscriptionOfferDetails offer : offers) {
            List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
            if (phases.isEmpty()) continue;

            long firstPhasePrice = phases.get(0).getPriceAmountMicros();
            if (firstPhasePrice == 0L) {
                return offer.getOfferToken();      // a free trial — exactly what we advertise
            }
            if (firstPhasePrice < cheapestMicros) {
                cheapestMicros = firstPhasePrice;
                cheapestToken = offer.getOfferToken();
            }
        }
        return cheapestToken != null ? cheapestToken : offers.get(0).getOfferToken();
    }

    /**
     * Re-checks entitlements against Play.
     *
     * <p>Needed because a subscription can end without the app being told: it lapses, the user
     * cancels from the Play Store, or a refund is issued. Without this the stored premium flag
     * stayed true until the billing connection happened to be rebuilt, so ads stayed off and paid
     * content stayed unlocked for someone who is no longer paying.
     *
     * <p>Throttled — Play does not need to be asked twice in the same minute.
     */
    public void refreshEntitlements() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastEntitlementCheck < ENTITLEMENT_REFRESH_MIN_INTERVAL_MS) return;
        lastEntitlementCheck = now;

        if (billingClient.isReady()) {
            queryPurchases();
        } else {
            startConnection();      // onBillingSetupFinished already queries once connected
        }
    }

    /**
     * First-phase price of a plan in micros (1,000,000 micros = one unit of currency), or 0 when
     * details have not loaded. Exposed so the paywall can compute a per-month equivalent and a
     * saving from the REAL Play prices rather than hardcoding numbers that would be wrong in every
     * country but one.
     */
    public long getPriceAmountMicros(PlanType planType) {
        ProductDetails details = getDetailsForPlan(planType);
        if (details == null) return 0L;

        if (BillingClient.ProductType.SUBS.equals(details.getProductType())) {
            List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
            if (offers == null || offers.isEmpty()) return 0L;
            for (ProductDetails.SubscriptionOfferDetails offer : offers) {
                List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
                for (ProductDetails.PricingPhase phase : phases) {
                    // Skip free trial phases — the recurring price is what we compare.
                    if (phase.getPriceAmountMicros() > 0L) return phase.getPriceAmountMicros();
                }
            }
            return 0L;
        }

        ProductDetails.OneTimePurchaseOfferDetails oneTime = details.getOneTimePurchaseOfferDetails();
        return oneTime != null ? oneTime.getPriceAmountMicros() : 0L;
    }

    /** ISO currency code for a plan, e.g. "INR" — empty when details have not loaded. */
    public String getPriceCurrencyCode(PlanType planType) {
        ProductDetails details = getDetailsForPlan(planType);
        if (details == null) return "";

        if (BillingClient.ProductType.SUBS.equals(details.getProductType())) {
            List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
            if (offers == null || offers.isEmpty()) return "";
            for (ProductDetails.SubscriptionOfferDetails offer : offers) {
                for (ProductDetails.PricingPhase phase : offer.getPricingPhases().getPricingPhaseList()) {
                    if (phase.getPriceAmountMicros() > 0L) return phase.getPriceCurrencyCode();
                }
            }
            return "";
        }

        ProductDetails.OneTimePurchaseOfferDetails oneTime = details.getOneTimePurchaseOfferDetails();
        return oneTime != null ? oneTime.getPriceCurrencyCode() : "";
    }

    private void updatePremiumStatus(boolean isPremium) {
        // Persist premium status to SharedPreferences so AdController can read it
        AppPreference.setBoolean(context, Keys.KEY_IS_PREMIUM, isPremium);
        if (isPremium) {
            // Ads stop being requested from here on, but one may already be sitting in the pool —
            // showing it after someone has just paid to remove ads is the worst possible moment.
            mainHandler.post(InterstitialPool::clear);
        }
        Log.d(TAG, "Premium status updated → isPremium=" + isPremium);
        notifyPremiumStatus(isPremium);
    }

    private void notifySuccess() {
        mainHandler.post(() -> {
            List<PurchaseListener> targets;
            synchronized (listeners) {
                targets = new ArrayList<>(listeners);
            }
            for (PurchaseListener l : targets) l.onPurchaseSuccess();
        });
    }

    private void notifyFailed(String error) {
        mainHandler.post(() -> {
            List<PurchaseListener> targets;
            synchronized (listeners) {
                targets = new ArrayList<>(listeners);
            }
            for (PurchaseListener l : targets) l.onPurchaseFailed(error);
        });
    }

    private void notifyPremiumStatus(boolean isPremium) {
        mainHandler.post(() -> {
            List<PurchaseListener> targets;
            synchronized (listeners) {
                targets = new ArrayList<>(listeners);
            }
            for (PurchaseListener l : targets) l.onPremiumStatusChecked(isPremium);
        });
    }

    // ✅ UPDATED: Include plan type
    private void notifyProductDetailsLoaded(PlanType planType, ProductDetails details) {
        mainHandler.post(() -> {
            List<PurchaseListener> targets;
            synchronized (listeners) {
                targets = new ArrayList<>(listeners);
            }
            for (PurchaseListener l : targets) {
                l.onProductDetailsLoaded(planType, details);
            }
        });
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    public void destroy() {
        if (billingClient.isReady()) {
            billingClient.endConnection();
        }
        listeners.clear();
        monthlyDetails = null;
        yearlyDetails = null;
        instance = null;
    }
}