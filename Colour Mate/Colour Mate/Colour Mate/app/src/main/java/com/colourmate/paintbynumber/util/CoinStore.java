package com.colourmate.paintbynumber.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coin packs: consumable in-app products. The four product ids below must exist in Play Console
 * (Monetize → In-app products) with the prices set there; the app only shows what Play returns.
 *
 * <p>A pack is consumed before its coins are added, and each purchase token is remembered, so a
 * restart mid-purchase or a second delivery of the same purchase can never pay twice. Purchases
 * that finished while the app was closed are picked up on the next connect.
 */
public final class CoinStore {

    public interface Listener { void onChanged(); }

    /** product id → coins. Order is the order the sheet shows them. */
    public static final String[] PACKS = {"coins_500", "coins_1200", "coins_3500", "coins_8000"};
    private static final Map<String, Integer> COINS = new HashMap<>();
    static {
        COINS.put("coins_500", 500);
        COINS.put("coins_1200", 1200);
        COINS.put("coins_3500", 3500);
        COINS.put("coins_8000", 8000);
    }

    private static CoinStore instance;

    private final Context app;
    private final BillingClient client;
    private final Map<String, ProductDetails> details = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;

    public static synchronized CoinStore get(Context c) {
        if (instance == null) instance = new CoinStore(c.getApplicationContext());
        return instance;
    }

    private CoinStore(Context app) {
        this.app = app;
        client = BillingClient.newBuilder(app)
                .setListener((result, purchases) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                        for (Purchase p : purchases) handle(p);
                    }
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        connect();
    }

    public void setListener(Listener l) { listener = l; }

    public static int coinsFor(String productId) {
        Integer n = COINS.get(productId);
        return n == null ? 0 : n;
    }

    /** Play's localised price ("$0.99", "₹89"), or null until Play has answered. */
    public String price(String productId) {
        ProductDetails d = details.get(productId);
        return d == null || d.getOneTimePurchaseOfferDetails() == null ? null
                : d.getOneTimePurchaseOfferDetails().getFormattedPrice();
    }

    public boolean buy(Activity activity, String productId) {
        ProductDetails d = details.get(productId);
        if (d == null || !client.isReady()) { connect(); return false; }
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
                .build();
        return client.launchBillingFlow(activity, params).getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    private void connect() {
        if (client.isReady()) return;
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(@NonNull BillingResult r) {
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                loadDetails();
                recoverUnconsumed();
            }
            @Override public void onBillingServiceDisconnected() { }
        });
    }

    private void loadDetails() {
        List<QueryProductDetailsParams.Product> list = new ArrayList<>();
        for (String id : PACKS) {
            list.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id).setProductType(BillingClient.ProductType.INAPP).build());
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build(),
                (r, res) -> {
                    if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                    for (ProductDetails d : res.getProductDetailsList()) details.put(d.getProductId(), d);
                    notifyChanged();
                });
    }

    /** A pack bought while the app was killed, or whose consume never completed. */
    private void recoverUnconsumed() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP).build(),
                (r, purchases) -> { for (Purchase p : purchases) handle(p); });
    }

    private void handle(Purchase p) {
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        int coins = 0;
        for (String id : p.getProducts()) coins += coinsFor(id);
        if (coins == 0) return;                               // not a coin pack (e.g. premium)
        final int amount = coins * Math.max(1, p.getQuantity());
        final String token = p.getPurchaseToken();
        client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build(), (r, t) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            SharedPreferences seen = app.getSharedPreferences("coin_store", Context.MODE_PRIVATE);
            if (seen.getBoolean(token, false)) return;        // already paid out
            seen.edit().putBoolean(token, true).apply();
            CoinManager.add(app, amount);
            notifyChanged();
        });
    }

    private void notifyChanged() {
        main.post(() -> { if (listener != null) listener.onChanged(); });
    }
}
