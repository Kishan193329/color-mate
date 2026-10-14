package com.colourmate.paintbynumber.util;

import java.util.HashMap;
import java.util.Map;

/**
 * A stub adapter for Google Play in-app purchases.
 * This class currently provides placeholder implementations.
 */
public class GooglePlayAdapter extends PurchaseManager {

    private String lastPurchasedProduct;

    @Override
    public void bind() {
        // Initialize Google Play services or billing client here
    }

    @Override
    public boolean hasSubscription() {
        // Return true if a subscription is active
        return true;
    }

    @Override
    public Map<String, ProductResponse> requestProductDetails(String[] productIds) {
        // Return product details for the given product IDs
        return new HashMap<>();
    }

    @Override
    public String getPurchasedProductName() {
        return lastPurchasedProduct;
    }

    /**
     * Sets the last purchased product name.
     *
     * @param productName The product ID or name
     */
    public void setLastPurchasedProduct(String productName) {
        this.lastPurchasedProduct = productName;
    }
}
