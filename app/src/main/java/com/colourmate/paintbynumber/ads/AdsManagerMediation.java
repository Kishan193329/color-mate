package com.colourmate.paintbynumber.ads;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.colourmate.paintbynumber.R;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.util.ArrayList;

public class AdsManagerMediation {

    // Constants
    public static final String DB_VERSION = "M1";
    private static final String NETWORK_ADMOB = "ADMOB";
    private static final String NETWORK_FB = "FB";
    private static final String NETWORK_ADX = "ADX";
    private static final String NETWORK_NULL = "null";
    private static final long BANNER_RETRY_DELAY = 4000L;

    // Ad counters
    public static int adCounter = 0;
    public static int adShowed = 0;
    private static int adNativeCounter = -1;

    // State flags
    public static boolean isInterstitialVisible = false;

    // Ad cache
    public static ArrayList<Object> nativeAds = new ArrayList<>();

    // Network indices
    private static int networkIndex_Splash_AppOpen = -1;
    private static int networkIndex_Splash_Interstitial = -1;
    private static int networkIndex_Native = -1;
    public static int networkIndex_Banner = -1;
    public static int networkIndex_Interstitial = -1;
    private static int networkIndex_Interstitial1 = -1;
    private static int networkIndex_Rewarded = -1;

    // Managers and listeners
    private static StartUpListener mStartupCompleted = null;
    private static AdXAdsUtils.AppOpenManager mAdXAppOpenManager = null;
    private static GoogleAdsUtils.AppOpenManager mGoogleAppOpenManager = null;
    private static Dialog loadingAds = null;

    // ==================== Application Launch ====================

    public static void launchApplication(Activity activity, StartUpListener startUpListener) {
        resetCounters();
        mStartupCompleted = startUpListener;
        // Off the main thread — same reason as AdsManager.launchApplication().
        new Thread(() -> MobileAds.initialize(activity.getApplicationContext(), status -> {
        }), "ads-init-m1").start();
        IAPManager.getInstance(activity).startConnection();
        checkInternetConnection(activity);
    }

    private static void resetCounters() {
        adNativeCounter = -1;
        adCounter = 0;
        adShowed = 0;
    }

    // ==================== Network Connectivity ====================

    private static void checkInternetConnection(Activity activity) {
        if (isNetworkAvailable(activity)) {
            loadFirebaseData(activity);
        } else {
            mStartupCompleted.onCompleted();
        }
    }

    private static boolean isNetworkAvailable(Activity activity) {
        ConnectivityManager connectivityManager = (ConnectivityManager) activity.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isAvailable() && activeNetworkInfo.isConnected();
    }

    private static boolean checkIsInternetConnection(Activity activity) {
        return isNetworkAvailable(activity);
    }

    // ==================== Firebase Data Loading ====================

    private static void loadFirebaseData(final Activity activity) {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference().child(DB_VERSION);

        databaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    AppSettingModel settings = dataSnapshot.getValue(AppSettingModel.class);
                    if (settings != null) {
                        saveAdSettings(activity, settings);
                        IAPManager.getInstance(activity).queryAllProductDetails();
                    }
                }
                applyGeoCounter(activity);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                applyGeoCounter(activity);
            }
        });
    }

    private static void saveAdSettings(Activity activity, AppSettingModel settings) {
        // Facebook settings
        AppPreference.setString(activity, Keys.KEY_FACEBOOK_APP_ID, settings.fb_appId);
        AppPreference.setString(activity, Keys.KEY_FACEBOOK_APP_TOKEN, settings.fb_appToken);

        // Google/AdMob settings
        AppPreference.setString(activity, Keys.KEY_ID_APP_OPEN, settings.id_google_appopen);
        AppPreference.setString(activity, Keys.KEY_ID_SPLASH, settings.id_google_splash);
        AppPreference.setString(activity, Keys.KEY_ID_SPLASH_APP_OPEN, settings.id_google_splash_open);
        AppPreference.setString(activity, Keys.KEY_ID_START_INTERSTITIAL, settings.id_google_start_inter);
        AppPreference.setString(activity, Keys.KEY_ID_INTERSTITIAL, settings.id_google_inter);
        AppPreference.setString(activity, Keys.KEY_ID_BANNER, settings.id_google_banner);
        AppPreference.setString(activity, Keys.KEY_ID_NATIVE, settings.id_google_native);
        AppPreference.setString(activity, Keys.KEY_ID_REWARDED, settings.id_google_rewarded);
        AppPreference.setString(activity, Keys.KEY_ID_REWARDED_INTER, settings.id_google_rewarded_inter);

        // Facebook Audience Network settings
        AppPreference.setString(activity, Keys.KEY_FB_ID_APP_OPEN, settings.id_fb_appopen);
        AppPreference.setString(activity, Keys.KEY_FB_ID_SPLASH, settings.id_fb_splash);
        AppPreference.setString(activity, Keys.KEY_FB_ID_SPLASH_APP_OPEN, settings.id_fb_splash_open);
        AppPreference.setString(activity, Keys.KEY_FB_ID_START_INTERSTITIAL, settings.id_fb_start_inter);
        AppPreference.setString(activity, Keys.KEY_FB_ID_INTERSTITIAL, settings.id_fb_inter);
        AppPreference.setString(activity, Keys.KEY_FB_ID_BANNER, settings.id_fb_banner);
        AppPreference.setString(activity, Keys.KEY_FB_ID_NATIVE, settings.id_fb_native);
        AppPreference.setString(activity, Keys.KEY_FB_ID_REWARDED, settings.id_fb_rewarded);

        // ADX settings
        AppPreference.setString(activity, Keys.KEY_ADX_ID_APP_OPEN, settings.id_adx_appopen);
        AppPreference.setString(activity, Keys.KEY_ADX_ID_SPLASH, settings.id_adx_splash);
        AppPreference.setString(activity, Keys.KEY_ADX_ID_SPLASH_APP_OPEN, settings.id_adx_splash_open);
        AppPreference.setString(activity, Keys.KEY_ADX_ID_START_INTERSTITIAL, settings.id_adx_start_inter);
        AppPreference.setString(activity, Keys.KEY_ADX_ID_INTERSTITIAL, settings.id_adx_inter);
        AppPreference.setString(activity, Keys.KEY_ADX_ID_BANNER, settings.id_adx_banner);
        AppPreference.setString(activity, Keys.KEY_ADX_ID_NATIVE, settings.id_adx_native);
        AppPreference.setString(activity, Keys.KEY_ADX_ID_REWARDED, settings.id_adx_rewarded);
        AppPreference.setString(activity, Keys.KEY_ADX_ID_REWARDED_INTER, settings.id_adx_rewarded_inter);

        // App settings
        AppPreference.setString(activity, Keys.KEY_DEV_EMAIL, settings.dev_email);
        AppPreference.setString(activity, Keys.KEY_DEV_NAME, settings.dev_name);
        AppPreference.setString(activity, Keys.KEY_POLICY, settings.policy_url);
        AppPreference.setString(activity, Keys.KEY_MSG, settings.msg);
        AppPreference.setString(activity, Keys.KEY_NETWORK_ORDER, settings.network_order);

        // Integer settings
        AppPreference.setInt(activity, Keys.KEY_VERSION_CODE, settings.version_code);
        AppPreference.setInt(activity, Keys.KEY_SHOWICON, settings.inter_count);
        AppPreference.setInt(activity, Keys.KEY_START_COUNT, settings.inter_start_count);
        AppPreference.setInt(activity, Keys.KEY_ALLOWED_PLAY, settings.total_inter);
        AppPreference.setInt(activity, Keys.KEY_PRELOAD_LIMIT, settings.native_preload_count);
        AppPreference.setInt(activity, Keys.KEY_NATIVE_COUNTER, settings.native_counter);
        AppPreference.setInt(activity, Keys.KEY_NATIVE_START_COUNT, settings.native_start_count);

        // Boolean settings
        AppPreference.setBoolean(activity, Keys.PLAY_VIDEO, settings.enable_ad);
        AppPreference.setBoolean(activity, Keys.ALLOW_BACK, settings.enable_back_ad);
        AppPreference.setBoolean(activity, Keys.IS_SPLASH_OPEN, settings.enable_splash_open);
        AppPreference.setBoolean(activity, Keys.KEY_IS_EXIT_DIALOG, settings.enable_exit_dialog);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_LANGUAGE_INTER_INTRO, settings.showLanguageInter);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_LANGUAGE_NATIVE, settings.showLanguageNative);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_LANGUAGE, settings.showLanguage);
        AppPreference.setBoolean(activity, Keys.KEY_IS_EXIT_LARGE_NATIVE, settings.showExitLargeNative);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_INTRO_SCREENS, settings.showIntro);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_INTER_INTRO, settings.showIntroInter);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_INTRO_NATIVE, settings.showIntroNative);
        AppPreference.setBoolean(activity, Keys.KEY_REWARD_ADS_INTER, settings.reward_ads_inter);
        AppPreference.setString(activity, Keys.KEY_PRODUCT_REMOVE_ADS, settings.remove_ads_sku);

        AppPreference.setBoolean(activity, Keys.KEY_SHOW_REWARD_CONFIRM, settings.show_reward_confirm);
        AppPreference.setString(activity, Keys.KEY_REWARD_CONFIRM_TITLE, settings.reward_confirm_title);
        AppPreference.setString(activity, Keys.KEY_REWARD_CONFIRM_MSG, settings.reward_confirm_msg);

        IAPManager.getInstance(activity).queryAllProductDetails();
    }

    private static void applyGeoCounter(Activity activity) {
        GoogleAdsUtils.initialize(activity);
        FacebookAdsUtils.initialize(activity);
        adCounter = AppPreference.getInt(activity, Keys.KEY_START_COUNT, 1);
        adNativeCounter = AppPreference.getInt(activity, Keys.KEY_NATIVE_START_COUNT, 1) - 1;

        // Log analytics for network configuration
        String networkOrder = AppPreference.getString(activity, Keys.KEY_NETWORK_ORDER, "");
        if (!networkOrder.isEmpty()) {
            AdsAnalytics.logNetworkOrder(activity, networkOrder);
        }
        if (!AppPreference.getBoolean(activity, Keys.PLAY_VIDEO, true)) {
            AdsAnalytics.logAdsDisabled(activity);
        }

        continueToApp(activity);
    }

    private static void continueToApp(Activity activity) {
        // Skip all ads if user has an active in-app purchase
        if (AppPreference.getBoolean(activity, Keys.KEY_IS_PREMIUM, false)) {
            openHomeScreen(activity);
            return;
        }
        if (AppPreference.getBoolean(activity, Keys.PLAY_VIDEO, true)) {
            showSplashAd(activity);
        } else {
            openHomeScreen(activity);
        }
    }

    private static void openHomeScreen(Context context) {
        // Play policy (Disruptive Ads strike, 2026-10-14): the forced "(Ad)" cross-promo dialog
        // that gated this step is gone — see Utils.showWarningMessage. Home opens unconditionally.
        mStartupCompleted.onCompleted();
    }

    // ==================== Splash Ads ====================

    private static void showSplashAd(Activity activity) {
        boolean useAdx = !AppPreference.getString(activity, Keys.KEY_ADX_ID_APP_OPEN, "").isEmpty();
        String networkOrder = AppPreference.getString(activity, Keys.KEY_NETWORK_ORDER, "");
        boolean isSplashOpen = AppPreference.getBoolean(activity, Keys.IS_SPLASH_OPEN, false);

        Log.i("TAG######", "showSplashAd-------useAdx----: " + useAdx);
        Log.i("TAG######", "showSplashAd-------networkOrder----: " + networkOrder);
        Log.i("TAG######", "showSplashAd-------isSplashOpen----: " + isSplashOpen);

        loadNativeAd(activity);
        initializeAppOpenManagers(activity, useAdx, networkOrder);

        if (!AdController.shouldShowAds(activity)) {
            openHomeScreen(activity);
            return;
        }

        // App Open format only on launch. An interstitial on app load is forbidden by AdMob's
        // placement policy and contributed to the 2026-10-14 Disruptive Ads strike.
        if (isSplashOpen) {
            executeSplashAppOpen(activity);
        } else {
            openHomeScreen(activity);
        }
    }

    private static void initializeAppOpenManagers(Activity activity, boolean useAdx, String networkOrder) {
        if (networkOrder.isEmpty()) {
            return;
        }

        String[] networks = networkOrder.split(",");
        for (String network : networks) {
            if (useAdx && network.equalsIgnoreCase(NETWORK_ADX) && mAdXAppOpenManager == null) {
                mAdXAppOpenManager = new AdXAdsUtils.AppOpenManager(activity);
            } else if (!useAdx && network.equalsIgnoreCase(NETWORK_ADMOB) && mGoogleAppOpenManager == null) {
                mGoogleAppOpenManager = new GoogleAdsUtils.AppOpenManager(activity);
            }
        }

        // Fallback initialization
        if (useAdx && mAdXAppOpenManager == null) {
            mAdXAppOpenManager = new AdXAdsUtils.AppOpenManager(activity);
        } else if (!useAdx && mGoogleAppOpenManager == null) {
            mGoogleAppOpenManager = new GoogleAdsUtils.AppOpenManager(activity);
        }
    }

    private static void executeSplashAppOpen(final Context context) {
        networkIndex_Splash_AppOpen++;
        String network = getNextAdNetwork(context, networkIndex_Splash_AppOpen);

        if (network.equalsIgnoreCase(NETWORK_NULL)) {
            networkIndex_Splash_AppOpen = -1;
            openHomeScreen(context);
            return;
        }

        if (network.isEmpty()) {
            openHomeScreen(context);
            return;
        }

        AdLoadingListener listener = createSplashAppOpenListener(context);

        switch (network.toUpperCase()) {
            case NETWORK_ADMOB:
                GoogleAdsUtils.showSplashAppOpen(context, listener);
                break;
            case NETWORK_FB:
                FacebookAdsUtils.showSplashAppOpen(context, listener);
                break;
            case NETWORK_ADX:
                AdXAdsUtils.showSplashAppOpen(context, listener);
                break;
            default:
                executeSplashAppOpen(context);
                break;
        }
    }

    private static AdLoadingListener createSplashAppOpenListener(final Context context) {
        return new AdLoadingListener() {
            @Override
            public void OnAdClosed() {
                networkIndex_Splash_AppOpen = -1;
                openHomeScreen(context);
            }

            @Override
            public void OnAdFailedToLoad() {
                executeSplashAppOpen(context);
            }

            @Override
            public void OnAdShow() {
            }

            @Override
            public void OnAdStartLoading() {
            }
        };
    }

    // (executeSplashInterstitial removed 2026-10-14 — no interstitial on app load, Play/AdMob policy.)

    // ==================== Interstitial Ads ====================

    public static void showInterstitial(final AppCompatActivity activity, final InternalAdListener listener) {
        if (!AdController.shouldShowAds(activity)) {
            if (listener != null) {
                listener.onAdClosed();
            }
            return;
        }

        if (!checkIsInternetConnection(activity)) {
            if (listener != null) {
                listener.onAdClosed();
            }
            return;
        }

        networkIndex_Interstitial++;
        String network = getNextAdNetwork(activity, networkIndex_Interstitial);

        if (network.equalsIgnoreCase(NETWORK_NULL)) {
            networkIndex_Interstitial = -1;
            dismissLoadingDialog();
            listener.onAdClosed();
            return;
        }

        if (network.isEmpty()) {
            dismissLoadingDialog();
            listener.onAdClosed();
            return;
        }

        AdLoadingListener adListener = createInterstitialListener(activity, listener);

        switch (network.toUpperCase()) {
            case NETWORK_ADMOB:
                GoogleAdsUtils.showInterstitial(activity, adListener);
                break;
            case NETWORK_FB:
                FacebookAdsUtils.showInterstitial(activity, adListener);
                break;
            case NETWORK_ADX:
                AdXAdsUtils.showInterstitial(activity, adListener);
                break;
            default:
                showInterstitial(activity, listener);
                break;
        }
    }

    private static AdLoadingListener createInterstitialListener(final AppCompatActivity activity,
            final InternalAdListener listener) {
        return new AdLoadingListener() {
            @Override
            public void OnAdClosed() {
                networkIndex_Interstitial = -1;
                dismissLoadingDialog();
                listener.onAdClosed();
            }

            @Override
            public void OnAdFailedToLoad() {
                int totalNetworks = getNextAdNetwork1(activity);
                if (totalNetworks == 1) {
                    if (adCounter != 0) {
                        adCounter--;
                    }
                } else if (totalNetworks - 1 != networkIndex_Interstitial) {
                    adCounter--;
                }
                showInterstitial(activity, listener);
            }

            @Override
            public void OnAdShow() {
                dismissLoadingDialog();
            }

            @Override
            public void OnAdStartLoading() {
                showLoadingDialog(activity, () -> {
                    networkIndex_Interstitial = -1; // Stop mediation
                    listener.onAdClosed();
                });
            }
        };
    }

    public static void showInterstitialOpen(final AppCompatActivity activity, final InternalAdListener listener) {
        if (!AdController.shouldShowAds(activity)) {
            if (listener != null) {
                listener.onAdClosed();
            }
            return;
        }

        if (!checkIsInternetConnection(activity)) {
            if (listener != null) {
                listener.onAdClosed();
            }
            return;
        }

        networkIndex_Interstitial1++;
        String network = getNextAdNetwork(activity, networkIndex_Interstitial1);

        if (network.equalsIgnoreCase(NETWORK_NULL)) {
            networkIndex_Interstitial1 = -1;
            dismissLoadingDialog();
            listener.onAdClosed();
            return;
        }

        if (network.isEmpty()) {
            dismissLoadingDialog();
            listener.onAdClosed();
            return;
        }

        AdLoadingListener adListener = createInterstitialOpenListener(activity, listener);

        switch (network.toUpperCase()) {
            case NETWORK_ADMOB:
                GoogleAdsUtils.showInterstitialOpen(activity, adListener);
                break;
            case NETWORK_FB:
                FacebookAdsUtils.showInterstitialOpen(activity, adListener);
                break;
            case NETWORK_ADX:
                AdXAdsUtils.showInterstitialOpen(activity, adListener);
                break;
            default:
                showInterstitialOpen(activity, listener);
                break;
        }
    }

    private static AdLoadingListener createInterstitialOpenListener(final AppCompatActivity activity,
            final InternalAdListener listener) {
        return new AdLoadingListener() {
            @Override
            public void OnAdClosed() {
                networkIndex_Interstitial1 = -1;
                dismissLoadingDialog();
                listener.onAdClosed();
            }

            @Override
            public void OnAdFailedToLoad() {
                showInterstitialOpen(activity, listener);
            }

            @Override
            public void OnAdShow() {
                dismissLoadingDialog();
            }

            @Override
            public void OnAdStartLoading() {
                showLoadingDialog(activity, () -> {
                    networkIndex_Interstitial1 = -1;
                    listener.onAdClosed();
                });
            }
        };
    }

    /**
     * Back-press interstitials are permanently off (Play Disruptive Ads strike, 2026-10-14) — an
     * ad because the user pressed Back is the textbook "unexpected" placement. The remote
     * {@code ALLOW_BACK} switch is ignored on purpose; callers simply continue.
     */
    public static void showBackInterstitial(AppCompatActivity activity, InternalAdListener listener) {
        if (listener != null) listener.onAdClosed();
    }

    // ==================== Rewarded Ads ====================

    /**
     * Show a rewarded ad with mediation across all configured networks.
     * Respects the network priority order from Firebase Remote Config.
     * Calls {@link RewardedAdListener#onRewardGranted()} only when the user earns
     * the reward.
     *
     * <p>
     * Usage example:
     * </p>
     * 
     * <pre>
     * AdsManagerMediation.showRewardedAd(activity, new RewardedAdListener() {
     *     {@literal @}Override public void onRewardGranted() { grantExtraLife(); }
     *     {@literal @}Override public void onRewardNull()    { showNoAdMessage(); }
     *     {@literal @}Override public void onRewardProgress() { showLoadingSpinner(); }
     *     {@literal @}Override public void onVideoSkipped()   { showSkipMessage(); }
     * });
     * </pre>
     *
     * @param activity the host activity
     * @param listener rewarded ad event callbacks
     */
    public static void showRewardedAd(final AppCompatActivity activity,
            final RewardedAdListener listener) {
        if (!AdController.shouldShowAds(activity)) {
            if (listener != null) {
                listener.onRewardGranted();
            }
            return;
        }

        if (AppPreference.getBoolean(activity, Keys.KEY_SHOW_REWARD_CONFIRM, true)) {
            AdController.showRewardConfirmation(activity, () -> executeRewardedAd(activity, listener),
                    listener::onRewardNull);
        } else {
            executeRewardedAd(activity, listener);
        }
    }

    private static void executeRewardedAd(final AppCompatActivity activity,
            final RewardedAdListener listener) {
        if (!checkIsInternetConnection(activity)) {
            dismissLoadingDialog();
            listener.onRewardNull();
            return;
        }

        networkIndex_Rewarded++;
        String network = getNextAdNetwork(activity, networkIndex_Rewarded);

        if (network.equalsIgnoreCase(NETWORK_NULL) || network.isEmpty()) {
            networkIndex_Rewarded = -1;
            dismissLoadingDialog();
            listener.onRewardNull();
            return;
        }

        // Show loading dialog only if the current network's ad is NOT preloaded
        boolean isAdPreloaded = false;
        switch (network.toUpperCase()) {
            case NETWORK_ADMOB:
                isAdPreloaded = GoogleAdsUtils.isRewardedAdLoaded(activity);
                break;
            case NETWORK_FB:
                isAdPreloaded = FacebookAdsUtils.isRewardedAdLoaded(activity);
                break;
            case NETWORK_ADX:
                isAdPreloaded = AdXAdsUtils.isRewardedAdLoaded(activity);
                break;
        }

        if (!isAdPreloaded) {
            showLoadingDialog(activity);
        }

        RewardedAdListener mediationListener = createRewardedListener(activity, listener);

        switch (network.toUpperCase()) {
            case NETWORK_ADMOB:
                GoogleAdsUtils.showRewardedAd(activity, mediationListener);
                break;
            case NETWORK_FB:
                FacebookAdsUtils.showRewardedAd(activity, mediationListener);
                break;
            case NETWORK_ADX:
                AdXAdsUtils.showRewardedAd(activity, mediationListener);
                break;
            default:
                executeRewardedAd(activity, listener);
                break;
        }
    }

    private static RewardedAdListener createRewardedListener(final AppCompatActivity activity,
            final RewardedAdListener listener) {
        return new RewardedAdListener() {
            @Override
            public void onRewardGranted() {
                networkIndex_Rewarded = -1;
                dismissLoadingDialog();
                listener.onRewardGranted();
            }

            @Override
            public void onRewardNull() {
                // Try the next network in mediation waterfall
                executeRewardedAd(activity, listener);
            }

            @Override
            public void onRewardProgress() {
                listener.onRewardProgress();
            }

            @Override
            public void onAdShow() {
                dismissLoadingDialog();
                if (listener != null) listener.onAdShow();
            }

            @Override
            public void onVideoSkipped() {
                networkIndex_Rewarded = -1;
                dismissLoadingDialog();
                listener.onVideoSkipped();
            }
        };
    }

    // ==================== Banner Ads ====================

    public static void getBannerAd(AppCompatActivity activity) {
        ViewGroup container = activity.findViewById(R.id.container_banner);
        getBannerAd(activity, container);
    }

    public static void getBannerAd(final AppCompatActivity activity, final ViewGroup container) {
        if (!checkIsInternetConnection(activity) || !isAdsEnabled(activity)) {
            container.setVisibility(View.GONE);
            return;
        }

        networkIndex_Banner++;
        String network = getNextAdNetwork(activity, networkIndex_Banner);

        Log.i("TAG###", "getBannerAd---network: " + network);

        if (network.equalsIgnoreCase(NETWORK_NULL) || network.isEmpty()) {
            networkIndex_Banner = -1;
            container.setVisibility(View.GONE);
            return;
        }

        AdLoadingListener listener = createBannerListener(activity, container);

        switch (network.toUpperCase()) {
            case NETWORK_ADMOB:
                GoogleAdsUtils.getBannerAd(activity, container, listener);
                break;
            case NETWORK_FB:
                FacebookAdsUtils.getBannerAd(activity, container, createFacebookBannerListener(activity, container));
                break;
            case NETWORK_ADX:
                AdXAdsUtils.getBannerAd(activity, container, listener);
                break;
            default:
                getBannerAd(activity, container);
                break;
        }
    }

    private static AdLoadingListener createBannerListener(final AppCompatActivity activity,
            final ViewGroup container) {
        return new AdLoadingListener() {
            @Override
            public void OnAdClosed() {
                networkIndex_Banner = -1;
            }

            @Override
            public void OnAdFailedToLoad() {
                if (getNextAdNetwork1(activity) == 1) {
                    container.setVisibility(View.GONE);
                } else {
                    getBannerAd(activity, container);
                }
            }

            @Override
            public void OnAdShow() {
            }

            @Override
            public void OnAdStartLoading() {
            }
        };
    }

    private static AdLoadingListener createFacebookBannerListener(final AppCompatActivity activity,
            final ViewGroup container) {
        return new AdLoadingListener() {
            @Override
            public void OnAdClosed() {
                networkIndex_Banner = -1;
            }

            @Override
            public void OnAdFailedToLoad() {
                if (getNextAdNetwork1(activity) == 1) {
                    networkIndex_Banner = -1;
                }
                new Handler().postDelayed(() -> getBannerAd(activity, container), BANNER_RETRY_DELAY);
            }

            @Override
            public void OnAdShow() {
            }

            @Override
            public void OnAdStartLoading() {
            }
        };
    }

    // ==================== Native Ads ====================

    public static void getNativeAd(AppCompatActivity activity, boolean isLarge) {
        ViewGroup container = isLarge
                ? activity.findViewById(R.id.container_native_view)
                : activity.findViewById(R.id.container_native_item);

        if (!nativeAds.isEmpty()) {
            getNativeAd(activity, container, isLarge);
        } else {
            getNativeOnDemandAd(activity, container, isLarge);
            networkIndex_Native = -1;
            loadNativeAd(activity);
        }
    }

    public static void getNativeAd(View view, AppCompatActivity activity, boolean isLarge) {
        ViewGroup container = isLarge
                ? activity.findViewById(R.id.container_native_view)
                : activity.findViewById(R.id.container_native_item);

        if (nativeAds.isEmpty()) {
            getNativeOnDemandAd(activity, container, isLarge);
            networkIndex_Native = -1;
            loadNativeAd(activity);
        } else {
            getNativeAd(activity, container, isLarge);
        }
    }

    public static void getNativeAd(final AppCompatActivity activity, ViewGroup container, boolean isLarge) {
        if (!checkIsInternetConnection(activity)) {
            container.setVisibility(View.GONE);
            return;
        }

        if (nativeAds.isEmpty()) {
            getNativeOnDemandAd(activity, container, isLarge);
            networkIndex_Native = -1;
            loadNativeAd(activity);
            return;
        }

        adNativeCounter++;

        if (!isAdsEnabled(activity)) {
            container.setVisibility(View.GONE);
            return;
        }

        int nativeCounter = AppPreference.getInt(activity, Keys.KEY_NATIVE_COUNTER, 1);
        if (nativeCounter == 0 || adNativeCounter % nativeCounter != 0) {
            container.setVisibility(View.GONE);
            return;
        }

        Object nativeAd = nativeAds.get(0);
        AdLoadingListener listener = createNativeAdListener(activity);

        if (nativeAd instanceof NativeAd) {
            GoogleAdsUtils.getNativeAd(activity, container, isLarge, listener);
        }
        // (Facebook native branch removed 2026-10-14 with the FAN SDK.)
    }

    private static AdLoadingListener createNativeAdListener(final AppCompatActivity activity) {
        return new AdLoadingListener() {
            @Override
            public void OnAdClosed() {
                networkIndex_Native = -1;
                loadNativeAd(activity);
            }

            @Override
            public void OnAdFailedToLoad() {
            }

            @Override
            public void OnAdShow() {
            }

            @Override
            public void OnAdStartLoading() {
            }
        };
    }

    public static void getNativeOnDemandAd(AppCompatActivity activity, ViewGroup container, boolean isLarge) {
        if (!checkIsInternetConnection(activity) || !isAdsEnabled(activity)) {
            container.setVisibility(View.GONE);
            return;
        }

        if (!nativeAds.isEmpty() || networkIndex_Native == -1) {
            return;
        }

        String network = getNextAdNetwork(activity, networkIndex_Native);
        adNativeCounter++;

        int nativeCounter = AppPreference.getInt(activity, Keys.KEY_NATIVE_COUNTER, 1);
        if (nativeCounter == 0 || adNativeCounter % nativeCounter != 0) {
            container.setVisibility(View.GONE);
            return;
        }

        if (network.isEmpty() || network.equals(NETWORK_NULL)) {
            container.setVisibility(View.GONE);
            return;
        }

        AdLoadingListener emptyListener = createEmptyAdLoadingListener();

        switch (network.toUpperCase()) {
            case NETWORK_ADMOB:
                GoogleAdsUtils.getNativeAd(activity, container, isLarge, emptyListener);
                break;
            case NETWORK_FB:
                FacebookAdsUtils.getNativeAd(activity, container, isLarge, emptyListener);
                break;
            case NETWORK_ADX:
                AdXAdsUtils.getNativeAd(activity, container, isLarge, emptyListener);
                break;
        }
    }

    private static void loadNativeAd(final Context context) {
        networkIndex_Native++;
        String network = getNextAdNetwork(context, networkIndex_Native);

        if (network.equalsIgnoreCase(NETWORK_NULL) || !isAdsEnabled(context)) {
            return;
        }

        int preloadLimit = AppPreference.getInt(context, Keys.KEY_PRELOAD_LIMIT, 2);
        if (nativeAds.size() >= preloadLimit) {
            networkIndex_Native = -1;
            return;
        }

        if (network.isEmpty()) {
            return;
        }

        AdLoadingListener listener = createNativeLoadListener(context);

        switch (network.toUpperCase()) {
            case NETWORK_ADMOB:
                GoogleAdsUtils.loadNativeAd(context, listener);
                break;
            case NETWORK_FB:
                FacebookAdsUtils.loadNativeAd(context, listener);
                break;
            case NETWORK_ADX:
                AdXAdsUtils.loadNativeAd(context, listener);
                break;
            default:
                loadNativeAd(context);
                break;
        }
    }

    private static AdLoadingListener createNativeLoadListener(final Context context) {
        return new AdLoadingListener() {
            @Override
            public void OnAdClosed() {
            }

            @Override
            public void OnAdFailedToLoad() {
                loadNativeAd(context);
            }

            @Override
            public void OnAdShow() {
            }

            @Override
            public void OnAdStartLoading() {
            }
        };
    }

    // ==================== Helper Methods ====================

    private static String getNextAdNetwork(Context context, int index) {
        String networkOrder = AppPreference.getString(context, Keys.KEY_NETWORK_ORDER, "");

        if (networkOrder.isEmpty()) {
            return NETWORK_NULL;
        }

        String[] networks = networkOrder.split(",");
        if (index >= networks.length) {
            return "";
        }

        return networks[index].trim();
    }

    private static int getNextAdNetwork1(Context context) {
        String networkOrder = AppPreference.getString(context, Keys.KEY_NETWORK_ORDER, "");

        if (networkOrder.isEmpty()) {
            return 0;
        }

        return networkOrder.split(",").length;
    }

    private static boolean isAdsEnabled(Context context) {
        return AdController.shouldShowAds(context);
    }

    public static void showLoadingDialog(AppCompatActivity activity) {
        showLoadingDialog(activity, null);
    }

    public static void showLoadingDialog(AppCompatActivity activity, final Runnable timeoutAction) {
        if (loadingAds == null) {
            loadingAds = new Dialog(activity, R.style.FullScreenDialogTheme);
            loadingAds.setContentView(R.layout.dialog_prepair_loading_ads);
            loadingAds.setCancelable(false);
            AdController.makeFullScreen(loadingAds);
            loadingAds.show();

            new Handler().postDelayed(() -> {
                if (loadingAds != null && loadingAds.isShowing()) {
                    dismissLoadingDialog();
                    if (timeoutAction != null) {
                        timeoutAction.run();
                    }
                }
            }, 5000);
        }
    }

    public static void dismissLoadingDialog() {
        if (loadingAds != null && loadingAds.isShowing()) {
            loadingAds.dismiss();
        }
        loadingAds = null;
    }

    private static AdLoadingListener createEmptyAdLoadingListener() {
        return new AdLoadingListener() {
            @Override
            public void OnAdClosed() {
            }

            @Override
            public void OnAdFailedToLoad() {
            }

            @Override
            public void OnAdShow() {
            }

            @Override
            public void OnAdStartLoading() {
            }
        };
    }
}