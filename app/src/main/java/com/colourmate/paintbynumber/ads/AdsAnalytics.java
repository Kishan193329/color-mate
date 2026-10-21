package com.colourmate.paintbynumber.ads;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Firebase Analytics helper for tracking ad events across all ad networks.
 * Logs ad impressions, clicks, loads, failures, revenue, and rewarded events.
 *
 * <p>
 * This class provides a centralized analytics layer for the ads module,
 * making it easy to track ad performance across AdMob, Facebook Audience
 * Network, and AdX.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * long startTime = AdsAnalytics.startLoadTimer();
 * // ... load ad ...
 * AdsAnalytics.logAdLoaded(context, "ADMOB", "interstitial", "ca-app-pub-xxx", startTime);
 * AdsAnalytics.logAdLoadFailed(context, "FB", "banner", "placement_id", 1001, "No fill");
 * AdsAnalytics.logAdRevenue(context, "ADMOB", "interstitial", "ca-app-pub-xxx", 0.01, "USD");
 * </pre>
 */
public final class AdsAnalytics {

    private static final String TAG = "AdsAnalytics";

    // ==================== Event Names ====================

    public static final String EVENT_AD_IMPRESSION = "ad_impression";
    public static final String EVENT_AD_CLICK = "ad_click";
    public static final String EVENT_AD_LOADED = "ad_loaded";
    public static final String EVENT_AD_LOAD_FAILED = "ad_load_failed";
    public static final String EVENT_AD_SHOW_FAILED = "ad_show_failed";
    public static final String EVENT_AD_CLOSED = "ad_closed";
    public static final String EVENT_AD_OPENED = "ad_opened";
    public static final String EVENT_AD_REQUEST = "ad_request";
    public static final String EVENT_AD_REWARDED = "ad_rewarded";
    public static final String EVENT_AD_REVENUE = "ad_revenue";
    public static final String EVENT_AD_RETRY = "ad_retry";

    // ==================== Parameter Keys ====================

    public static final String PARAM_AD_NETWORK = "ad_network";
    public static final String PARAM_AD_TYPE = "ad_type";
    public static final String PARAM_AD_UNIT_ID = "ad_unit_id";
    public static final String PARAM_AD_PLACEMENT = "ad_placement";
    public static final String PARAM_ERROR_CODE = "error_code";
    public static final String PARAM_ERROR_MESSAGE = "error_message";
    public static final String PARAM_LOAD_TIME_MS = "load_time_ms";
    public static final String PARAM_REVENUE_VALUE = "value";
    public static final String PARAM_REVENUE_CURRENCY = "currency";
    public static final String PARAM_RETRY_COUNT = "retry_count";
    public static final String PARAM_REWARD_TYPE = "reward_type";
    public static final String PARAM_REWARD_AMOUNT = "reward_amount";

    // ==================== Ad Network Names ====================

    public static final String NETWORK_ADMOB = "ADMOB";
    public static final String NETWORK_FACEBOOK = "FACEBOOK";
    public static final String NETWORK_ADX = "ADX";

    // ==================== Ad Type Names ====================

    public static final String TYPE_INTERSTITIAL = "interstitial";
    public static final String TYPE_BANNER = "banner";
    public static final String TYPE_NATIVE = "native";
    public static final String TYPE_APP_OPEN = "app_open";
    public static final String TYPE_REWARDED = "rewarded";
    public static final String TYPE_REWARDED_INTERSTITIAL = "rewarded_interstitial";
    public static final String TYPE_SPLASH_INTERSTITIAL = "splash_interstitial";
    public static final String TYPE_SPLASH_APP_OPEN = "splash_app_open";

    // ==================== Placement Names ====================

    public static final String PLACEMENT_SPLASH = "splash";
    public static final String PLACEMENT_IN_APP = "in_app";
    public static final String PLACEMENT_EXIT = "exit";
    public static final String PLACEMENT_BACK = "back";

    // Private constructor to prevent instantiation
    private AdsAnalytics() {
        throw new AssertionError("Cannot instantiate AdsAnalytics class");
    }

    // ==================== Core Logging Methods ====================

    /**
     * Get FirebaseAnalytics instance
     */
    private static FirebaseAnalytics getAnalytics(Context context) {
        return FirebaseAnalytics.getInstance(context.getApplicationContext());
    }

    /**
     * Log a custom ad event with parameters.
     * Silently catches exceptions so ad analytics never crashes the app.
     */
    private static void logEvent(Context context, String eventName, Bundle params) {
        try {
            getAnalytics(context).logEvent(eventName, params);
            Log.d(TAG, "Event logged: " + eventName + " | " + (params != null ? params.toString() : ""));
        } catch (Exception e) {
            Log.e(TAG, "Failed to log event: " + eventName, e);
        }
    }

    // ==================== Timer Utility ====================

    /**
     * Start a load timer. Call this before requesting an ad.
     * Pass the returned value to
     * {@link #logAdLoaded(Context, String, String, String, long)}
     * to automatically calculate and log the load time.
     *
     * @return current time in milliseconds
     */
    public static long startLoadTimer() {
        return System.currentTimeMillis();
    }

    // ==================== Ad Impression Events ====================

    /**
     * Log when an ad is shown/displayed to the user.
     *
     * @param context  Application context
     * @param network  Ad network (e.g., NETWORK_ADMOB, NETWORK_FACEBOOK)
     * @param adType   Ad type (e.g., TYPE_INTERSTITIAL, TYPE_BANNER)
     * @param adUnitId Ad unit ID
     */
    public static void logAdImpression(Context context, String network, String adType,
            String adUnitId) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        logEvent(context, EVENT_AD_IMPRESSION, params);
    }

    /**
     * Log when an ad is shown with placement info.
     */
    public static void logAdImpression(Context context, String network, String adType,
            String adUnitId, String placement) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        params.putString(PARAM_AD_PLACEMENT, placement);
        logEvent(context, EVENT_AD_IMPRESSION, params);
    }

    // ==================== Ad Load Events ====================

    /**
     * Log when an ad request is initiated.
     */
    public static void logAdRequest(Context context, String network, String adType,
            String adUnitId) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        logEvent(context, EVENT_AD_REQUEST, params);
    }

    /**
     * Log when an ad is successfully loaded.
     */
    public static void logAdLoaded(Context context, String network, String adType,
            String adUnitId) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        logEvent(context, EVENT_AD_LOADED, params);
    }

    /**
     * Log when an ad is successfully loaded with measured load time.
     * Pass {@link #startLoadTimer()} result as {@code loadStartTime}.
     *
     * @param loadStartTime value returned from {@link #startLoadTimer()} before the
     *                      ad request
     */
    public static void logAdLoaded(Context context, String network, String adType,
            String adUnitId, long loadStartTime) {
        long loadTimeMs = System.currentTimeMillis() - loadStartTime;
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        params.putLong(PARAM_LOAD_TIME_MS, loadTimeMs);
        logEvent(context, EVENT_AD_LOADED, params);
    }

    /**
     * Log when an ad fails to load (e.g., network error, no fill).
     */
    public static void logAdLoadFailed(Context context, String network, String adType,
            String adUnitId) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        logEvent(context, EVENT_AD_LOAD_FAILED, params);
    }

    /**
     * Log when an ad fails to load with error details.
     */
    public static void logAdLoadFailed(Context context, String network, String adType,
            String adUnitId, int errorCode, String errorMessage) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        params.putInt(PARAM_ERROR_CODE, errorCode);
        params.putString(PARAM_ERROR_MESSAGE, truncate(errorMessage, 100));
        logEvent(context, EVENT_AD_LOAD_FAILED, params);
    }

    /**
     * Log when an already-loaded ad fails to show (e.g., activity destroyed,
     * ad expired). This is a separate event from {@link #logAdLoadFailed}.
     */
    public static void logAdShowFailed(Context context, String network, String adType,
            String adUnitId, int errorCode, String errorMessage) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        params.putInt(PARAM_ERROR_CODE, errorCode);
        params.putString(PARAM_ERROR_MESSAGE, truncate(errorMessage, 100));
        logEvent(context, EVENT_AD_SHOW_FAILED, params);
    }

    // ==================== Ad Interaction Events ====================

    /**
     * Log when an ad is clicked by the user.
     */
    public static void logAdClick(Context context, String network, String adType,
            String adUnitId) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        logEvent(context, EVENT_AD_CLICK, params);
    }

    /**
     * Log when an ad is closed/dismissed by the user.
     */
    public static void logAdClosed(Context context, String network, String adType,
            String adUnitId) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        logEvent(context, EVENT_AD_CLOSED, params);
    }

    /**
     * Log when an ad is opened (e.g., full-screen content shown).
     */
    public static void logAdOpened(Context context, String network, String adType,
            String adUnitId) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        logEvent(context, EVENT_AD_OPENED, params);
    }

    // ==================== Revenue & Reward Events ====================

    /**
     * Log estimated ad revenue earned from an impression.
     * Use this inside {@code onPaidEvent} or equivalent callbacks
     * for precise revenue tracking in Firebase.
     *
     * @param network  Ad network name
     * @param adType   Ad type
     * @param adUnitId Ad unit ID
     * @param value    Estimated revenue amount
     * @param currency ISO 4217 currency code (e.g., "USD")
     */
    public static void logAdRevenue(Context context, String network, String adType,
            String adUnitId, double value, String currency) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        params.putDouble(PARAM_REVENUE_VALUE, value);
        params.putString(PARAM_REVENUE_CURRENCY, currency);
        logEvent(context, EVENT_AD_REVENUE, params);
    }

    /**
     * Log when a rewarded ad successfully grants a reward to the user.
     *
     * @param network      Ad network name
     * @param adUnitId     Ad unit ID
     * @param rewardType   Type of reward (e.g., "coins", "lives")
     * @param rewardAmount Amount of reward earned
     */
    public static void logAdRewarded(Context context, String network, String adUnitId,
            String rewardType, int rewardAmount) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, TYPE_REWARDED);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        params.putString(PARAM_REWARD_TYPE, rewardType);
        params.putInt(PARAM_REWARD_AMOUNT, rewardAmount);
        logEvent(context, EVENT_AD_REWARDED, params);
    }

    // ==================== Mediation & Diagnostics ====================

    /**
     * Log a retry attempt — called when an ad failed and is being re-requested.
     *
     * @param retryCount number of retries so far (1-based)
     */
    public static void logAdRetry(Context context, String network, String adType,
            String adUnitId, int retryCount) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_NETWORK, network);
        params.putString(PARAM_AD_TYPE, adType);
        params.putString(PARAM_AD_UNIT_ID, adUnitId);
        params.putInt(PARAM_RETRY_COUNT, retryCount);
        logEvent(context, EVENT_AD_RETRY, params);
    }

    /**
     * Log mediation fallback — when one network fails and falls through to the
     * next.
     */
    public static void logMediationFallback(Context context, String failedNetwork,
            String nextNetwork, String adType) {
        Bundle params = new Bundle();
        params.putString("failed_network", failedNetwork);
        params.putString("next_network", nextNetwork);
        params.putString(PARAM_AD_TYPE, adType);
        logEvent(context, "ad_mediation_fallback", params);
    }

    /**
     * Log the ad network order being used.
     */
    public static void logNetworkOrder(Context context, String networkOrder) {
        Bundle params = new Bundle();
        params.putString("network_order", networkOrder);
        logEvent(context, "ad_network_order_set", params);
    }

    /**
     * Log when ads are disabled remotely (e.g., via remote config).
     */
    public static void logAdsDisabled(Context context) {
        logEvent(context, "ads_disabled", new Bundle());
    }

    /**
     * Log when ad limit is reached for the session.
     */
    public static void logAdLimitReached(Context context, int totalShown) {
        Bundle params = new Bundle();
        params.putInt("total_shown", totalShown);
        logEvent(context, "ad_limit_reached", params);
    }

    /**
     * Log when frequency cap is hit and an ad is skipped.
     */
    public static void logAdFrequencyCapped(Context context, String adType) {
        Bundle params = new Bundle();
        params.putString(PARAM_AD_TYPE, adType);
        logEvent(context, "ad_frequency_capped", params);
    }

    // ==================== Helper Methods ====================

    /**
     * Truncate string to max length for Firebase parameter values (max 100 chars).
     */
    private static String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
