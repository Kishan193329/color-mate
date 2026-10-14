package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.colourmate.paintbynumber.GlobalApplication;

import java.util.Map;

/**
 * Abstract class to manage in-app purchases and subscriptions.
 */
public abstract class PurchaseManager {

    public static final String TAG = "PurchaseManager";

    // Singleton instance
    private static final PurchaseManager instance = new GooglePlayAdapter();

    /**
     * Binds the purchase manager (initialize connections if needed).
     */
    public abstract void bind();

    /**
     * Checks if the user has an active subscription.
     */
    public abstract boolean hasSubscription();

    /**
     * Requests product details for the given product IDs.
     */
    public abstract Map<String, ProductResponse> requestProductDetails(String[] productIds);

    /**
     * Returns the last purchased product name, if any.
     */
    public String getPurchasedProductName() {
        return null;
    }

    /**
     * Handles the result from a purchase activity.
     */
    public void onRequestBuyResult(int requestCode, Intent data) {
        // Default implementation does nothing
    }

    /**
     * Returns the application context.
     */
    public Context getContext() {
        return GlobalApplication.getAppContext();
    }

    /**
     * Logs messages with the class tag.
     */
    public static void log(String message) {
        Log.d(TAG, message);
    }

    /**
     * Returns the singleton instance of the PurchaseManager.
     */
    public static PurchaseManager getInstance() {
        return instance;
    }

    /**
     * Data class representing a product response.
     */
    public static class ProductResponse {
        public int error;
        public String freeTrialPeriod;
        public String title;

        @NonNull
        @Override
        public String toString() {
            return title;
        }
    }
}
