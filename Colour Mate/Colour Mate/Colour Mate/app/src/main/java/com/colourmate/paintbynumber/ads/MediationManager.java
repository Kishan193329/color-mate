package com.colourmate.paintbynumber.ads;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.io.Serializable;

/**
 * Manager class that handles mediation between different ad management
 * strategies.
 * Routes ad calls to either the mediation system (M1) or legacy system (V1)
 * based on Firebase configuration.
 */
public class MediationManager {

    // Constants
    private static final String DB_VERSION = "Mediation";
    private static final String MEDIATION_VERSION_M1 = "M1";
    private static final String MEDIATION_VERSION_V1 = "V1";
    private static final long NETWORK_RETRY_DELAY_MS = 3000L;

    // State
    private static StartUpListener mStartupCompleted;

    // ==================== Initialization ====================

    /**
     * Initialize mediation system and determine which ad strategy to use
     * 
     * @param activity Current activity
     * @param listener Startup completion listener
     */
    public static void initializeMediation(Activity activity, StartUpListener listener) {
        mStartupCompleted = listener;
        checkInternetConnection(activity);
    }

    /**
     * Launch application with appropriate ad manager based on mediation
     * configuration
     * 
     * @param activity Current activity
     * @param listener Startup completion listener
     */
    public static void launchApplication(Activity activity, StartUpListener listener) {
        if (isMediationEnabled(activity)) {
            AdsManagerMediation.launchApplication(activity, listener);
        } else {
            AdsManager.launchApplication(activity, listener);
        }
    }

    // ==================== Network Check ====================

    private static void checkInternetConnection(Activity activity) {
        if (isNetworkAvailable(activity)) {
            loadFirebaseData(activity, mStartupCompleted);
        } else {
            // Retry after delay if no network
            new Handler().postDelayed(() -> {
                if (mStartupCompleted != null) {
                    mStartupCompleted.onCompleted();
                }
            }, NETWORK_RETRY_DELAY_MS);
        }
    }

    private static boolean isNetworkAvailable(Activity activity) {
        ConnectivityManager connectivityManager = (ConnectivityManager) activity.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null
                && activeNetworkInfo.isAvailable()
                && activeNetworkInfo.isConnected();
    }

    // ==================== Firebase Configuration ====================

    private static void loadFirebaseData(final Context context, final StartUpListener listener) {
        DatabaseReference mediationRef = FirebaseDatabase.getInstance()
                .getReference()
                .child(DB_VERSION);

        mediationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    MediationModel model = dataSnapshot.getValue(MediationModel.class);
                    if (model != null) {
                        AppPreference.setString(context, Keys.KEY_MEDIATION, model.priority);
                    }
                }

                // Ensure IAP connection starts and attempts to query details
                IAPManager.getInstance(context).startConnection();
                IAPManager.getInstance(context).queryAllProductDetails();

                launchApplication((Activity) context, listener);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Use default mediation on error
                launchApplication((Activity) context, listener);
            }
        });
    }

    // ==================== Ad Display Methods ====================

    /**
     * Show banner ad in default container
     */
    public static void getBannerAd(AppCompatActivity activity) {
        if (isMediationEnabled(activity)) {
            AdsManagerMediation.getBannerAd(activity);
        } else {
            AdsManager.getBannerAd(activity);
        }
    }

    /**
     * Show banner ad in specific container
     */
    public static void getBannerAd(AppCompatActivity activity, ViewGroup container) {
        if (isMediationEnabled(activity)) {
            resetBannerIndex();
            AdsManagerMediation.getBannerAd(activity, container);
        } else {
            AdsManager.getBannerAd(activity, container);
        }
    }

    /**
     * Show native ad in default container
     */
    public static void getNativeAd(AppCompatActivity activity, boolean isLarge) {
        if (isMediationEnabled(activity)) {
            AdsManagerMediation.getNativeAd(activity, isLarge);
        } else {
            AdsManager.getNativeAd(activity, isLarge);
        }
    }

    /**
     * Show native ad in specific container
     */
    public static void getNativeAd(AppCompatActivity activity, ViewGroup container, boolean isLarge) {
        if (isMediationEnabled(activity)) {
            AdsManagerMediation.getNativeAd(activity, container, isLarge);
        } else {
            AdsManager.getNativeAd(activity, container, isLarge);
        }
    }

    /**
     * Show interstitial ad
     */
    /** Warms the interstitial cache ahead of a moment where one may be shown. */
    public static void preloadInterstitial(Context context) {
        if (context == null) return;
        if (!isMediationEnabled(context)) {
            AdsManager.preloadInterstitial(context);
        }
        // The M1 mediation path keeps its own chain and preloads inside AdsManagerMediation.
    }

    public static void showInterstitial(AppCompatActivity activity, InternalAdListener listener) {
        if (isMediationEnabled(activity)) {
            resetInterstitialIndex();
            AdsManagerMediation.showInterstitial(activity, listener);
        } else {
            AdsManager.showInterstitial(activity, listener);
        }
    }

    /**
     * Show interstitial ad on app open
     */
    public static void showInterstitialOpen(AppCompatActivity activity, InternalAdListener listener) {
        if (isMediationEnabled(activity)) {
            AdsManagerMediation.showInterstitialOpen(activity, listener);
        } else {
            AdsManager.showInterstitialOpen(activity, listener);
        }
    }

    /**
     * Show interstitial ad on back button press
     */
    public static void showBackInterstitial(AppCompatActivity activity, InternalAdListener listener) {
        if (isMediationEnabled(activity)) {
            resetInterstitialIndex();
            AdsManagerMediation.showBackInterstitial(activity, listener);
        } else {
            AdsManager.showBackInterstitial(activity, listener);
        }
    }

    /**
     * Show a rewarded ad with mediation.
     * Routes to AdsManagerMediation (M1) or AdsManager (V1) based on configuration.
     *
     * <p>
     * The {@link RewardedAdListener#onRewardGranted()} callback fires only when
     * the user watches the full video ad and earns their reward.
     * </p>
     *
     * @param activity the host activity
     * @param listener rewarded ad lifecycle callbacks
     */
    public static void showRewardedAd(AppCompatActivity activity, RewardedAdListener listener) {
        if (isMediationEnabled(activity)) {
            AdsManagerMediation.showRewardedAd(activity, listener);
        } else {
            // Legacy path — fall back to AdMob directly
            AdsManager.showRewardedAd(activity, listener);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Show exit confirmation dialog
     */
    public static void showExitDialog(AppCompatActivity activity) {
        if (isMediationEnabled(activity)) {
            Utils.showExitDialog(activity);
        } else {
            AdsManager.Utils.showExitDialog(activity);
        }
    }

    /**
     * Check if app version is outdated
     */
    public static boolean isOldAppVersion(AppCompatActivity activity) {
        if (isMediationEnabled(activity)) {
            return Utils.isOldAppVersion(activity);
        } else {
            return AdsManager.Utils.isOldAppVersion(activity);
        }
    }

    /**
     * Open app rating page
     */
    public static void rateApp(Context context) {
        if (isMediationEnabled(context)) {
            Utils.rateApp(context);
        } else {
            AdsManager.Utils.rateApp(context);
        }
    }

    /**
     * Share app via other apps
     */
    public static void shareApp(Context context) {
        if (isMediationEnabled(context)) {
            Utils.shareApp(context);
        } else {
            AdsManager.Utils.shareApp(context);
        }
    }

    /**
     * Show more apps by developer
     */
    public static void moreApps(Context context) {
        if (isMediationEnabled(context)) {
            disableMediationAppOpenAds();
            Utils.moreApps(context);
        } else {
            disableLegacyAppOpenAds();
            AdsManager.Utils.moreApps(context);
        }
    }

    /**
     * Open privacy policy
     */
    public static void openPolicy(Context context) {
        if (isMediationEnabled(context)) {
            Utils.openPolicy(context);
        } else {
            AdsManager.Utils.openPolicy(context);
        }
    }

    /**
     * Open terms and conditions
     */
    public static void openTerms(Context context) {
        if (isMediationEnabled(context)) {
            disableMediationAppOpenAds();
            Utils.openTerms(context);
        } else {
            disableLegacyAppOpenAds();
            AdsManager.Utils.openTerms(context);
        }
    }

    /**
     * Send feedback email
     */
    public static void writeSuggestions(Context context) {
        if (isMediationEnabled(context)) {
            Utils.writeSuggestions(context);
        } else {
            AdsManager.Utils.writeSuggestions(context);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Check if mediation (M1) is enabled
     */
    private static boolean isMediationEnabled(Context context) {
        String mediationVersion = AppPreference.getString(context, Keys.KEY_MEDIATION, MEDIATION_VERSION_V1);
        return MEDIATION_VERSION_M1.equals(mediationVersion);
    }

    /**
     * Reset banner network index for mediation
     */
    private static void resetBannerIndex() {
        AdsManagerMediation.networkIndex_Banner = -1;
    }

    /**
     * Reset interstitial network index for mediation
     */
    private static void resetInterstitialIndex() {
        AdsManagerMediation.networkIndex_Interstitial = -1;
    }

    /**
     * Disable app open ads for mediation networks
     */
    private static void disableMediationAppOpenAds() {
        AdXAdsUtils.AppOpenManager.isOpenAdxAd = false;
        GoogleAdsUtils.AppOpenManager.isOpenGoogleAd = false;
    }

    /**
     * Disable app open ads for legacy system
     */
    private static void disableLegacyAppOpenAds() {
        AdsManager.AppOpenManager.isOpenAd = false;
    }

    // ==================== Model Classes ====================

    /**
     * Model for mediation configuration from Firebase
     */
    public static class MediationModel implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Mediation priority/version (e.g., "V1" for legacy, "M1" for mediation)
         */
        public String priority = MEDIATION_VERSION_V1;

        /**
         * Check if mediation is enabled
         */
        public boolean isMediationEnabled() {
            return MEDIATION_VERSION_M1.equals(priority);
        }

        @NonNull
        @Override
        public String toString() {
            return "MediationModel{" +
                    "priority='" + priority + '\'' +
                    ", mediation=" + isMediationEnabled() +
                    '}';
        }
    }
}