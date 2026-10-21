package com.colourmate.paintbynumber.ads;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.SplashActivity;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import com.google.firebase.database.DataSnapshot;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;

/**
 * Main Ads Manager for handling all ad operations.
 * Manages App Open, Interstitial, Banner, and Native ads using Google AdMob.
 */
public class AdsManager {

    // Constants
    private static final String TAG = "AdsManager";
    public static final String DB_VERSION = "V1";
    private static final long AD_EXPIRATION_TIME_HOURS = 4L;
    // State variables
    public static int adCounter = 0;
    public static int adShowed = 0;
    public static boolean isInterstitialVisible = false;

    /**
     * True from the moment a launch ad is requested until it has been shown and dismissed (or has
     * failed). The splash waits on this so it cannot navigate to Home while the ad is still on its
     * way — which is what made the ad appear ON TOP of Home instead of before it.
     */
    private static volatile boolean splashAdInFlight = false;

    /**
     * True once {@link #startLaunchAd} has taken responsibility for the launch ad.
     *
     * <p>There were two independent launch-ad paths: this one, fired at t=0 from the splash, and
     * the older config-driven {@code showSplashAd()} further down. Both ran, so the user saw TWO
     * app-open ads before reaching Home. This flag makes {@code showSplashAd()} stand down — the
     * launch ad now has exactly one owner.
     */
    private static volatile boolean launchAdHandled = false;

    /**
     * When our last full-screen ad finished, on the elapsed-realtime clock.
     *
     * <p>The app-open observer fires on every foreground transition, including the one that happens
     * when the user dismisses one of our own full-screen ads. That is how a second app-open ad
     * landed three seconds after the launch ad. Ads are not allowed to stack: nothing full-screen
     * may follow within {@link #FULL_SCREEN_AD_COOLDOWN_MS}.
     */
    private static volatile long lastFullScreenAdEndedAt = 0L;
    // 20s → 60s (2026-10-14): the foreground app-open ad was landing a few seconds after an
    // interstitial whenever the user flicked to another app and back. A minute between
    // full-screen ads is the gentler cadence, and costs little — the user is still in the app.
    private static final long FULL_SCREEN_AD_COOLDOWN_MS = 60_000L;

    /** Records that a full-screen ad just closed. */
    static void markFullScreenAdEnded() {
        lastFullScreenAdEndedAt = android.os.SystemClock.elapsedRealtime();
    }

    /** True while we are inside the quiet window after a full-screen ad. */
    static boolean inFullScreenAdCooldown() {
        return lastFullScreenAdEndedAt > 0L
                && android.os.SystemClock.elapsedRealtime() - lastFullScreenAdEndedAt
                        < FULL_SCREEN_AD_COOLDOWN_MS;
    }
    private static boolean isStartInterViewed = false;

    // Managers and listeners
    private static AppOpenAd appOpenAd = null;
    private static AppOpenManager mAppOpenManager;
    private static StartUpListener mStartupCompleted;

    // ==================== Application Launch ====================

    /**
     * Initialize ads and launch application
     */
    public static void launchApplication(Activity activity, StartUpListener startUpListener) {
        resetCounters();
        mStartupCompleted = startUpListener;

        // MobileAds.initialize() does disk and network work on whatever thread calls it, and
        // calling it from the main thread cost ~2.7s of dropped frames during launch (measured
        // 2026-07-30). Google's own guidance is to initialise off the main thread. The preloads
        // must go back to the main thread — loading an ad is a main-thread API.
        new Thread(() -> MobileAds.initialize(activity.getApplicationContext(), status ->
                activity.runOnUiThread(() -> {
                    // Warm the first interstitial while the user is still on the splash, so the
                    // one that matters — their first picture — opens without a spinner.
                    preloadInterstitial(activity);
                    scheduleRewardedPreloads(activity);
                })), "ads-init").start();

        IAPManager.getInstance(activity).startConnection();
        checkInternetConnection(activity);
    }

    /**
     * How long the rewarded formats wait before they are warmed up.
     *
     * <p>Long enough to clear the splash, the app-open ad and the home screen's first frame.
     */
    private static final long REWARDED_PRELOAD_DELAY_MS = 6000L;

    /**
     * Warms the rewarded formats once the launch is over, instead of during it.
     *
     * <p>Measured 2026-08-09: five rewarded preloads fired in the same main-thread block as the
     * interstitial, the instant {@code MobileAds.initialize} returned — which is while the splash
     * is animating. Each {@code load()} pulls in a Play Services Dynamite module on the main
     * thread, and the launch showed a 2.4s frozen frame and 145 skipped frames.
     *
     * <p>Only the interstitial is wanted early — it fronts the user's first picture. Nothing can
     * ask for a rewarded ad in the first few seconds: reaching one takes several taps, and By
     * Images moved to interstitials in the first place (§8.5ai). So the cost is nothing and the
     * launch gets its main thread back.
     */
    private static void scheduleRewardedPreloads(Activity activity) {
        new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try {
                GoogleAdsUtils.preloadRewardedAd(activity);
                GoogleAdsUtils.preloadRewardedInterstitialAd(activity);
                AdXAdsUtils.preloadRewardedAd(activity);
                AdXAdsUtils.preloadRewardedInterstitialAd(activity);
                FacebookAdsUtils.preloadRewardedAd(activity);
            } catch (Exception e) {
                android.util.Log.w("AdsManager", "Deferred rewarded preload failed", e);
            }
        }, REWARDED_PRELOAD_DELAY_MS);
    }

    private static void resetCounters() {
        adCounter = 0;
        adShowed = 0;

        // launchAdHandled and splashAdInFlight are deliberately NOT reset here.
        //
        // launchApplication() runs *after* the splash has already called startLaunchAd(), so
        // clearing them here wiped the "the launch ad is already handled" flag a moment before the
        // legacy path checked it — which is precisely why two app-open ads appeared. Both flags
        // belong to the launch, which happens once per process, and static initialisation already
        // starts them false.
    }

    // ==================== Network Connectivity ====================

    private static void checkInternetConnection(Activity activity) {
        if (isNetworkAvailable(activity)) {
            loadAdConfig(activity);
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

    /**
     * Ad settings now come from the ColorForge admin, with Firebase kept as the fallback.
     *
     * <p>Nothing else in the ad stack changes: this still ends at {@link #saveAdSettings}, which
     * writes every value into SharedPreferences, and the rest of the app only ever reads those.
     * The server's JSON uses the same field names as {@link AdModel}, so it decodes straight into
     * it. If our server is unset, unreachable or returns something unusable we fall through to the
     * Firebase node exactly as before — a config server being down must never cost the app its ads.
     */
    private static void loadAdConfig(final Activity activity) {
        final String base = ColorForgeApi.hasExplicitServer(activity)
                ? ColorForgeApi.baseUrl(activity) : "";
        if (base.isEmpty()) {
            loadFirebaseData(activity);
            return;
        }
        new Thread(() -> {
            AdModel model = null;
            try {
                okhttp3.OkHttpClient c = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS).build();
                okhttp3.Request rq = new okhttp3.Request.Builder()
                        .url(ColorForgeApi.adsUrl(activity)).get().build();
                try (okhttp3.Response rs = c.newCall(rq).execute()) {
                    if (rs.isSuccessful() && rs.body() != null) {
                        model = new com.google.gson.Gson().fromJson(rs.body().string(), AdModel.class);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Ad config from the admin failed, falling back to Firebase", t);
            }
            final AdModel loaded = model;
            // An object with no unit ids at all is worse than useless — it would overwrite good
            // ids with blanks — so treat it as a miss and let Firebase answer.
            final boolean usable = loaded != null && (
                    notBlank(loaded.adIdInter) || notBlank(loaded.adIdBanner)
                            || notBlank(loaded.adIdRewarded) || notBlank(loaded.adIdNative)
                            || notBlank(loaded.adIdOpen));
            activity.runOnUiThread(() -> {
                if (!usable) {
                    loadFirebaseData(activity);
                    return;
                }
                Log.i(TAG, "Ad config loaded from the admin");
                saveAdSettings(activity, loaded);
                IAPManager.getInstance(activity).queryAllProductDetails();
                applyGeoCounter(activity);
                preloadInterstitial(activity);
            });
        }, "cf-ad-config").start();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static void loadFirebaseData(final Activity activity) {
        final DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference().child(DB_VERSION);

        databaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    AdModel adModel = dataSnapshot.getValue(AdModel.class);
                    if (adModel != null) {
                        saveAdSettings(activity, adModel);
                    }
                    IAPManager.getInstance(activity).queryAllProductDetails();
                }
                applyGeoCounter(activity);

                // The unit ids only exist once this config arrives, so the preload fired during
                // MobileAds init had nothing to request. Now it does.
                activity.runOnUiThread(() -> preloadInterstitial(activity));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                applyGeoCounter(activity);
            }
        });
    }

    private static void saveAdSettings(Activity activity, AdModel model) {
        // Ad Unit IDs
        AppPreference.setString(activity, Keys.KEY_ID_APP_OPEN, model.adIdOpen);
        AppPreference.setString(activity, Keys.KEY_ID_SPLASH, model.adIdSplash);
        AppPreference.setString(activity, Keys.KEY_ID_SPLASH_APP_OPEN, model.adIdSplashOpen);
        AppPreference.setString(activity, Keys.KEY_ID_START_INTERSTITIAL, model.adIdStart);
        AppPreference.setString(activity, Keys.KEY_ID_INTERSTITIAL, model.adIdInter);
        AppPreference.setString(activity, Keys.KEY_ID_BANNER, model.adIdBanner);
        AppPreference.setString(activity, Keys.KEY_ID_NATIVE, model.adIdNative);
        AppPreference.setString(activity, Keys.KEY_ID_REWARDED, model.adIdRewarded);
        AppPreference.setString(activity, Keys.KEY_ID_REWARDED_INTER, model.adIdRewardedInter);

        // App Info
        AppPreference.setString(activity, Keys.KEY_DEV_EMAIL, model.bDevEmail);
        AppPreference.setString(activity, Keys.KEY_DEV_NAME, model.bDevName);
        AppPreference.setString(activity, Keys.KEY_POLICY, model.bPolicy);
        AppPreference.setString(activity, Keys.KEY_MSG, model.bMsg);

        // Integer Settings
        AppPreference.setInt(activity, Keys.KEY_VERSION_CODE, model.bVersionCode);
        AppPreference.setInt(activity, Keys.KEY_SHOWICON, model.dAdsCount);
        AppPreference.setInt(activity, Keys.KEY_START_COUNT, model.dAdsStartCount);
        AppPreference.setInt(activity, Keys.KEY_ALLOWED_PLAY, model.dAllowedPlay);
        AppPreference.setInt(activity, Keys.KEY_PRELOAD_LIMIT, model.dPreloadLimit);

        // Boolean Settings
        AppPreference.setBoolean(activity, Keys.PLAY_VIDEO, model.dPlayVideo);
        AppPreference.setBoolean(activity, Keys.ALLOW_BACK, model.dAllowBack);
        AppPreference.setBoolean(activity, Keys.IS_SPLASH_OPEN, model.dIsSplashOpen);
        AppPreference.setBoolean(activity, Keys.KEY_IS_EXIT_DIALOG, model.dIsExitDialog);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_INTRO_NATIVE, model.dShowIntroNative);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_LANGUAGE, model.dShowLanguage);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_LANGUAGE_NATIVE, model.dShowLanguageNative);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_LANGUAGE_INTER_INTRO, model.dShowLanguageInter);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_INTER_INTRO, model.dShowLanguageInter);
        AppPreference.setBoolean(activity, Keys.KEY_IS_EXIT_LARGE_NATIVE, model.dShowExitLargeNative);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_INTRO_SCREENS, model.dShowIntro);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_INTER_INTRO, model.dShowIntroInter);
        AppPreference.setBoolean(activity, Keys.KEY_REWARD_ADS_INTER, model.reward_ads_inter);
        AppPreference.setBoolean(activity, Keys.KEY_SHOW_REWARD_CONFIRM, model.show_reward_confirm);
        AppPreference.setString(activity, Keys.KEY_REWARD_CONFIRM_TITLE, model.reward_confirm_title);
        AppPreference.setString(activity, Keys.KEY_REWARD_CONFIRM_MSG, model.reward_confirm_msg);
        AppPreference.setString(activity, Keys.KEY_PRODUCT_REMOVE_ADS, model.remove_ads_sku);

        // Refresh IAP product details if SKU changed
        IAPManager.getInstance(activity).queryAllProductDetails();

        applyGeoCounter(activity);
    }

    private static void applyGeoCounter(Activity activity) {
        adCounter = AppPreference.getInt(activity, Keys.KEY_START_COUNT, 1);
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
        // Play policy (Disruptive Ads strike, 2026-10-14): the remote "(Ad)" cross-promo dialog
        // that used to gate this step — non-dismissible, OK opened another app's store page and
        // killed this one — is gone. The home screen opens unconditionally.
        mStartupCompleted.onCompleted();
    }

    // ==================== Splash Ads ====================

    private static void showSplashAd(Activity activity) {
        if (!AdController.shouldShowAds(activity)) {
            openHomeScreen(activity);
            return;
        }

        if (mAppOpenManager == null) {
            mAppOpenManager = new AppOpenManager(activity);
        }

        MyAdsManager.loadNativeAd(activity);

        // startLaunchAd() already owns the launch ad — it fires at the start of the splash using
        // the cached unit ids, rather than waiting for this config round trip. Showing another one
        // here is what produced two app-open ads back to back.
        if (launchAdHandled) {
            openHomeScreen(activity);
            return;
        }

        // Only the App Open FORMAT may front the launch. The old fallback here was a full-screen
        // interstitial on app load, which AdMob's placement policy forbids outright and which is
        // part of what earned the Disruptive Ads strike (2026-10-14). No splash-open unit → no ad.
        if (AppPreference.getBoolean(activity, Keys.IS_SPLASH_OPEN, false)) {
            showSplashAppOpen(activity);
        } else {
            openHomeScreen(activity);
        }
    }

    private static void showSplashAppOpen(final Context context) {
        String adUnitId = AppPreference.getString(context, Keys.KEY_ID_SPLASH_APP_OPEN, "");

        if (isEmptyOrNull(adUnitId)) {
            openHomeScreen(context);
            return;
        }

        splashAdInFlight = true;

        AppOpenAd.load(
                context,
                adUnitId,
                createAdRequest(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        super.onAdFailedToLoad(loadAdError);
                        splashAdInFlight = false;
                        openHomeScreen(context);
                    }

                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        super.onAdLoaded(ad);
                        Activity host = (Activity) context;

                        // If the splash has already gone (safety timeout, or the user backed out),
                        // showing now would drop the ad on top of whatever replaced it. Let it go.
                        if (host.isFinishing() || host.isDestroyed()) {
                            splashAdInFlight = false;
                            return;
                        }

                        appOpenAd = ad;
                        appOpenAd.setFullScreenContentCallback(createSplashAppOpenCallback(context));
                        appOpenAd.show(host);
                    }
                });
    }

    private static FullScreenContentCallback createSplashAppOpenCallback(final Context context) {
        return new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                isInterstitialVisible = false;
                splashAdInFlight = false;
                appOpenAd = null;
                openHomeScreen(context);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                isInterstitialVisible = false;
                splashAdInFlight = false;
                openHomeScreen(context);
            }

            @Override
            public void onAdShowedFullScreenContent() {
                isInterstitialVisible = true;
            }
        };
    }

    // (showSplashInterstitial removed 2026-10-14 — an interstitial on app load is a Play/AdMob
    //  policy violation. The launch ad is the App Open format only: startLaunchAd / showSplashAppOpen.)

    // ==================== Public Ad Methods ====================

    /**
     * Warms the interstitial pool. Cheap and idempotent — it does nothing when an ad is already
     * cached or a request is in flight, so it is safe to call from any screen that is about to
     * lead somewhere an interstitial can appear.
     */
    /**
     * True while a full-screen ad (interstitial or app-open) is on screen, or an app-open ad is
     * loaded and about to be shown on the splash.
     *
     * <p>The splash uses this so it never navigates out from under an ad. Without it the launch
     * deadline could fire mid-handshake and start Home, and the app-open ad would then appear on
     * top of Home a moment later — the ad after the home screen instead of before it.
     */
    public static boolean isFullScreenAdPending() {
        if (isInterstitialVisible || splashAdInFlight) {
            return true;
        }
        return mAppOpenManager != null && mAppOpenManager.isShowingOrReady();
    }

    /**
     * Requests and shows the launch (app-open) ad, then calls {@code onDone}.
     *
     * <p>Why this exists: the original launch ad could only be requested after the Firebase config
     * arrived with the unit ids, so on a real device the sequence config → load → show regularly
     * ran past 7 seconds. The splash gave up first, Home appeared, and the ad landed on top of it.
     *
     * <p>The unit ids are already persisted from the previous run, so this asks for the ad at the
     * very start of the splash instead — in parallel with the config fetch rather than behind it.
     * The ad is usually ready in a second or two and is shown while the splash is still up.
     *
     * <p>{@code onDone} always runs exactly once: after dismissal, on failure, or immediately when
     * there is nothing to show. The caller never has to time it out.
     */
    public static void startLaunchAd(final Activity activity, final Runnable onDone) {
        launchAdHandled = true;      // from here on, showSplashAd() must not show its own

        final java.util.concurrent.atomic.AtomicBoolean finished =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable done = () -> {
            if (finished.compareAndSet(false, true)) {
                splashAdInFlight = false;
                markFullScreenAdEnded();
                if (onDone != null) onDone.run();
            }
        };

        if (!AdController.shouldShowAds(activity)) {
            done.run();
            return;
        }

        // Prefer the dedicated splash unit, fall back to the general app-open one.
        String adUnitId = AppPreference.getString(activity, Keys.KEY_ID_SPLASH_APP_OPEN, "");
        if (isEmptyOrNull(adUnitId)) {
            adUnitId = AppPreference.getString(activity, Keys.KEY_ID_APP_OPEN, "");
        }
        if (isEmptyOrNull(adUnitId)) {
            // First ever launch: no ids cached yet. Nothing to show, and that is fine.
            done.run();
            return;
        }

        splashAdInFlight = true;
        AppOpenAd.load(
                activity.getApplicationContext(),
                adUnitId,
                createAdRequest(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        done.run();
                    }

                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            done.run();          // splash already gone — do not show over Home
                            return;
                        }
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdShowedFullScreenContent() {
                                isInterstitialVisible = true;
                            }

                            @Override
                            public void onAdDismissedFullScreenContent() {
                                isInterstitialVisible = false;
                                done.run();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                isInterstitialVisible = false;
                                done.run();
                            }
                        });
                        ad.show(activity);
                    }
                });
    }

    public static void preloadInterstitial(Context context) {
        if (context == null || !AdController.shouldShowAds(context)) {
            return;
        }
        String unitId = MyAdsManager.getInterstitialAdUnitId(context);
        if (!isEmptyOrNull(unitId)) {
            InterstitialPool.preload(context, unitId);
        }
    }

    public static void showInterstitial(AppCompatActivity activity, InternalAdListener listener) {
        MyAdsManager.showInterstitial(activity, listener);
    }

    public static void showInterstitialOpen(AppCompatActivity activity, InternalAdListener listener) {
        MyAdsManager.showInterstitialOpen(activity, listener);
    }

    public static void showBackInterstitial(AppCompatActivity activity, InternalAdListener listener) {
        MyAdsManager.showBackInterstitial(activity, listener);
    }

    public static void showRewardedAd(AppCompatActivity activity, RewardedAdListener listener) {
        if (!AdController.shouldShowAds(activity)) {
            if (listener != null) {
                listener.onRewardGranted();
            }
            return;
        }

        if (AppPreference.getBoolean(activity, Keys.KEY_SHOW_REWARD_CONFIRM, true)) {
            AdController.showRewardConfirmation(activity, () -> MyAdsManager.showRewardedAd(activity, listener),
                    listener::onRewardNull);
        } else {
            MyAdsManager.showRewardedAd(activity, listener);
        }
    }

    public static void getBannerAd(AppCompatActivity activity) {
        ViewGroup container = activity.findViewById(R.id.container_banner);
        MyAdsManager.getBannerAd(activity, container);
    }

    public static void getBannerAd(AppCompatActivity activity, ViewGroup container) {
        MyAdsManager.getBannerAd(activity, container);
    }

    public static void getNativeAd(AppCompatActivity activity, boolean isLarge) {
        ViewGroup container = isLarge
                ? activity.findViewById(R.id.container_native_view)
                : activity.findViewById(R.id.container_native_item);
        MyAdsManager.getNativeAd(activity, container, isLarge);
    }

    public static void getNativeAd(AppCompatActivity activity, ViewGroup container, boolean isLarge) {
        MyAdsManager.getNativeAd(activity, container, isLarge);
    }

    // ==================== Helper Methods ====================

    private static AdRequest createAdRequest() {
        return new AdRequest.Builder().build();
    }

    private static boolean isEmptyOrNull(String str) {
        return str == null || str.isEmpty();
    }

    // ==================== Inner Classes ====================

    /**
     * Model class for ad configuration from Firebase
     */
    public static class AdModel implements Serializable {
        // Ad Unit IDs
        public String adIdOpen = "";
        public String adIdSplash = "";
        public String adIdSplashOpen = "";
        public String adIdStart = "";
        public String adIdInter = "";
        public String adIdBanner = "";
        public String adIdNative = "";
        public String adIdRewarded = "";
        public String adIdRewardedInter = "";

        // App Info
        public String bPolicy = "";
        public String bDevEmail = "";
        public String bDevName = "";
        public String bMsg = "";
        public int bVersionCode = 2;

        // Ad Settings
        public int dAdsCount = 3;
        public int dAdsStartCount = 1;
        public int dAllowedPlay = 25;
        public boolean dPlayVideo = true;
        public boolean dAllowBack = false;
        public boolean dIsSplashOpen = false;
        public boolean dIsExitDialog = true;
        public int dPreloadLimit = 2;

        // UI Settings
        public boolean dShowIntroNative = true;
        public boolean dShowExitLargeNative = false;
        public boolean dShowIntroInter = false;
        public boolean reward_ads_inter = false;
        public boolean dShowIntro = false;
        public boolean dShowLanguage = false;
        public boolean dShowLanguageNative = false;
        public boolean dShowLanguageInter = false;

        // Reward Confirmation
        public boolean show_reward_confirm = true;
        public String reward_confirm_title = "Watch Video?";
        public String reward_confirm_msg = "Watch a short video to get your reward.";

        // IAP Settings
        public String remove_ads_sku = "remove_ads_testing";
    }

    /**
     * Manages App Open ads with lifecycle awareness
     */
    public static class AppOpenManager implements Application.ActivityLifecycleCallbacks, LifecycleObserver {

        public static boolean isOpenAd = true;
        private static boolean isShowingAd = false;

        private final Application myApplication;
        private Activity currentActivity;
        private AppOpenAd appOpenAd = null;
        private long loadTime = 0;

        public AppOpenManager(Activity activity) {
            this.myApplication = activity.getApplication();
            myApplication.registerActivityLifecycleCallbacks(this);
            ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        }

        public void fetchAd() {
            if (isAdAvailable()) {
                return;
            }

            String adUnitId = AppPreference.getString(myApplication, Keys.KEY_ID_APP_OPEN, "");
            if (isEmptyOrNull(adUnitId)) {
                return;
            }

            AppOpenAd.load(
                    myApplication,
                    adUnitId,
                    createAdRequest(),
                    new AppOpenAd.AppOpenAdLoadCallback() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            super.onAdFailedToLoad(loadAdError);
                        }

                        @Override
                        public void onAdLoaded(@NonNull AppOpenAd ad) {
                            super.onAdLoaded(ad);
                            appOpenAd = ad;
                            appOpenAd.setFullScreenContentCallback(createFullScreenContentCallback());
                            loadTime = new Date().getTime();
                        }
                    });
        }

        private FullScreenContentCallback createFullScreenContentCallback() {
            return new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    isInterstitialVisible = false;
                    appOpenAd = null;
                    isShowingAd = false;
                    markFullScreenAdEnded();
                    fetchAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    isInterstitialVisible = false;
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    isInterstitialVisible = true;
                    isShowingAd = true;
                }
            };
        }

        /** Showing right now, or loaded and ready to be shown during the splash. */
        public boolean isShowingOrReady() {
            return isShowingAd || isAdAvailable();
        }

        public boolean isAdAvailable() {
            return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(AD_EXPIRATION_TIME_HOURS);
        }

        private boolean wasLoadTimeLessThanNHoursAgo(long hours) {
            long currentTime = new Date().getTime();
            long elapsedTime = currentTime - loadTime;
            long expirationTime = hours * 3600000; // Convert hours to milliseconds
            return elapsedTime < expirationTime;
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        public void onStart() {
            // Coming back to the front is the trigger that lets the interstitial pool try again
            // after it gave up on a run of no-fills. Retrying is only worth anything while
            // somebody is actually looking at the app, which is exactly now.
            InterstitialPool.onAppForegrounded(myApplication);
            showAdIfAvailable();
        }

        public void showAdIfAvailable() {
            // Use myApplication for preference checks - currentActivity can be null when
            // onStart() fires immediately upon adding the lifecycle observer
            if (!AdController.shouldShowAds(myApplication)) {
                return;
            }

            if (currentActivity == null) {
                return;
            }

            if (isInterstitialVisible || isSplashActivity()) {
                return;
            }

            // Do not follow one of our own full-screen ads with another. Without this, dismissing
            // the launch ad brings the app back to the foreground, this observer fires, and a
            // second app-open ad opens on top of the first one's exit.
            if (inFullScreenAdCooldown()) {
                return;
            }

            if (!isOpenAd) {
                isOpenAd = true;
                return;
            }

            if (isShowingAd || !isAdAvailable()) {
                fetchAd();
            } else {
                appOpenAd.show(currentActivity);
            }
        }

        private boolean isSplashActivity() {
            return currentActivity instanceof SplashActivity;
        }

        // Activity Lifecycle Callbacks
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            currentActivity = activity;
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            currentActivity = activity;
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            currentActivity = null;
        }
    }

    /**
     * Internal manager for ad operations
     */
    public static class MyAdsManager {

        private static final ArrayList<NativeAd> nativeAds = new ArrayList<>();

        // ==================== Banner Ads ====================

        private static void getBannerAd(AppCompatActivity activity, final ViewGroup container) {
            if (!checkIsInternetConnection(activity) || !isAdsEnabled(activity)) {
                hideContainer(container);
                return;
            }

            String adUnitId = AppPreference.getString(activity, Keys.KEY_ID_BANNER, "");
            if (isEmptyOrNull(adUnitId)) {
                hideContainer(container);
                return;
            }

            final AdView adView = new AdView(activity);
            adView.setAdSize(getAdaptiveBannerAdSize(activity));
            adView.setAdUnitId(adUnitId);
            // A banner keeps refreshing on its own for as long as it exists. Nothing was ever
            // destroying these, so a screen the user had left carried on requesting ads in the
            // background — wasted requests that can never become a viewable impression, plus the
            // battery and memory to serve them. Tie the AdView to the Activity that owns it.
            bindBannerToLifecycle(activity, adView, container);
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    hideContainer(container);
                }

                @Override
                public void onAdLoaded() {
                    super.onAdLoaded();
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT);
                    layoutParams.gravity = android.view.Gravity.CENTER;
                    container.removeAllViews();
                    container.addView(adView, layoutParams);
                }
            });
            adView.loadAd(createAdRequest());
        }

        /**
         * Pause the banner while its screen is not in front, resume when it returns, and destroy
         * it with the screen. Without this the AdView outlives the Activity: it holds the
         * Activity context (a leak) and keeps auto-refreshing off-screen, which spends requests
         * and battery on impressions nobody can see.
         */
        private static void bindBannerToLifecycle(final AppCompatActivity activity,
                final AdView adView, final ViewGroup container) {
            activity.getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
                @Override
                public void onPause(@NonNull androidx.lifecycle.LifecycleOwner owner) {
                    adView.pause();
                }

                @Override
                public void onResume(@NonNull androidx.lifecycle.LifecycleOwner owner) {
                    adView.resume();
                }

                @Override
                public void onDestroy(@NonNull androidx.lifecycle.LifecycleOwner owner) {
                    if (container != null) {
                        container.removeView(adView);
                    }
                    adView.destroy();
                    owner.getLifecycle().removeObserver(this);
                }
            });
        }

        private static AdSize getAdaptiveBannerAdSize(Context context) {
            Display display = ((AppCompatActivity) context).getWindowManager().getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);

            int widthPixels = displayMetrics.widthPixels;
            float density = displayMetrics.density;
            int adWidth = (int) (widthPixels / density);

            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth);
        }

        // ==================== Native Ads ====================

        public static void getNativeAd(AppCompatActivity activity, final ViewGroup container, boolean isLarge) {
            if (!checkIsInternetConnection(activity) || !isAdsEnabled(activity)) {
                hideContainer(container);
                return;
            }

            String adUnitId = AppPreference.getString(activity, Keys.KEY_ID_NATIVE, "");
            if (isEmptyOrNull(adUnitId)) {
                hideContainer(container);
                return;
            }

            if (nativeAds.isEmpty()) {
                loadAndDisplayNativeAd(activity, container, isLarge, adUnitId);
            } else {
                displayCachedNativeAd(activity, container, isLarge);
                loadNativeAd(activity);
            }
        }

        private static void displayCachedNativeAd(AppCompatActivity activity, ViewGroup container, boolean isLarge) {
            if (nativeAds.isEmpty()) {
                hideContainer(container);
                return;
            }

            NativeAd nativeAd = nativeAds.remove(0);
            NativeAdView nativeAdView = inflateNativeAdView(activity, isLarge);
            populateNativeAd(nativeAd, nativeAdView, activity);

            container.removeAllViews();
            container.addView(nativeAdView);
        }

        private static void loadAndDisplayNativeAd(final AppCompatActivity activity,
                                                   final ViewGroup container,
                                                   final boolean isLarge,
                                                   String adUnitId) {
            new AdLoader.Builder(activity, adUnitId)
                    .forNativeAd(nativeAd -> {
                        NativeAdView nativeAdView = inflateNativeAdView(activity, isLarge);
                        populateNativeAd(nativeAd, nativeAdView, activity);
                        container.removeAllViews();
                        container.addView(nativeAdView);
                    })
                    .withAdListener(new AdListener() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            hideContainer(container);
                        }
                    })
                    .withNativeAdOptions(new NativeAdOptions.Builder().build())
                    .build()
                    .loadAd(createAdRequest());
        }

        private static void loadNativeAd(final Context context) {
            String adUnitId = AppPreference.getString(context, Keys.KEY_ID_NATIVE, "");
            if (isEmptyOrNull(adUnitId)) {
                return;
            }

            new AdLoader.Builder(context, adUnitId)
                    .forNativeAd(nativeAd -> {
                        nativeAds.add(nativeAd);
                        int preloadLimit = AppPreference.getInt(context, Keys.KEY_PRELOAD_LIMIT, 2);
                        if (nativeAds.size() < preloadLimit) {
                            loadNativeAd(context);
                        }
                    })
                    .withAdListener(new AdListener() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            // Failed to load
                        }
                    })
                    .withNativeAdOptions(new NativeAdOptions.Builder().build())
                    .build()
                    .loadAd(createAdRequest());
        }

        private static NativeAdView inflateNativeAdView(AppCompatActivity activity, boolean isLarge) {
            int layoutId = isLarge ? R.layout.ads_unified : R.layout.ads_unified_item;
            return (NativeAdView) activity.getLayoutInflater().inflate(layoutId, null);
        }

        private static void populateNativeAd(NativeAd nativeAd, NativeAdView adView, AppCompatActivity activity) {
            // Set media view
            MediaView mediaView = adView.findViewById(R.id.ad_media);
            adView.setMediaView(mediaView);

            // Set other views
            adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
            adView.setBodyView(adView.findViewById(R.id.ad_body));
            adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
            adView.setIconView(adView.findViewById(R.id.ad_app_icon));

            // Populate headline
            TextView headlineView = (TextView) adView.getHeadlineView();
            if (headlineView != null) {
                headlineView.setText(nativeAd.getHeadline());
            }

            // Populate media
            if (adView.getMediaView() != null && nativeAd.getMediaContent() != null) {
                adView.getMediaView().setMediaContent(nativeAd.getMediaContent());
            }

            // Populate body
            View bodyView = adView.getBodyView();
            if (bodyView != null) {
                if (nativeAd.getBody() == null) {
                    bodyView.setVisibility(View.INVISIBLE);
                } else {
                    bodyView.setVisibility(View.VISIBLE);
                    ((TextView) bodyView).setText(nativeAd.getBody());
                }
            }

            // Populate call to action
            View ctaView = adView.getCallToActionView();
            if (ctaView != null) {
                if (nativeAd.getCallToAction() == null) {
                    ctaView.setVisibility(View.INVISIBLE);
                } else {
                    ctaView.setVisibility(View.VISIBLE);
                    ((Button) ctaView).setText(nativeAd.getCallToAction());
                }
            }

            // Populate icon
            View iconView = adView.getIconView();
            if (iconView != null) {
                if (nativeAd.getIcon() == null) {
                    iconView.setVisibility(View.GONE);
                } else {
                    ((ImageView) iconView).setImageDrawable(nativeAd.getIcon().getDrawable());
                    iconView.setVisibility(View.VISIBLE);
                }
            }

            adView.setNativeAd(nativeAd);
        }

        // ==================== Interstitial Ads ====================

        private static void showInterstitial(final AppCompatActivity activity, final InternalAdListener listener) {
            if (!checkIsInternetConnection(activity)) {
                listener.onAdClosed();
                return;
            }

            adCounter++;

            if (!isAdsEnabled(activity)) {
                listener.onAdClosed();
                return;
            }

            if (!shouldShowInterstitial(activity)) {
                listener.onAdClosed();
                return;
            }

            adCounter = 0;

            if (hasReachedAdLimit(activity)) {
                listener.onAdClosed();
                return;
            }

            String adUnitId = getInterstitialAdUnitId(activity);
            if (isEmptyOrNull(adUnitId)) {
                listener.onAdClosed();
                return;
            }

            loadAndShowInterstitial(activity, adUnitId, listener);
        }

        private static void showInterstitialOpen(final AppCompatActivity activity, final InternalAdListener listener) {
            if (!checkIsInternetConnection(activity) || !isAdsEnabled(activity)) {
                listener.onAdClosed();
                return;
            }

            if (hasReachedAdLimit(activity)) {
                listener.onAdClosed();
                return;
            }

            String adUnitId = getInterstitialAdUnitId(activity);
            if (isEmptyOrNull(adUnitId)) {
                listener.onAdClosed();
                return;
            }

            loadAndShowInterstitial(activity, adUnitId, listener);
        }

        /**
         * Back-press interstitials are permanently off (Play Disruptive Ads strike, 2026-10-14).
         * An ad that appears because the user pressed Back is the textbook "unexpected" placement.
         * The remote {@code ALLOW_BACK} switch is ignored on purpose so it cannot be re-enabled
         * from config; callers simply continue.
         */
        private static void showBackInterstitial(AppCompatActivity activity, InternalAdListener listener) {
            if (listener != null) listener.onAdClosed();
        }

        /**
         * Shows an interstitial from the warm pool.
         *
         * <p>Was: pop a "Loading Ads…" sheet, request an ad, wait up to 5s. That meant a spinner
         * before every interstitial, a thrown-away impression whenever the network was slow, and —
         * worst of all — the abandoned load still called {@code show()} when it finished, so an ad
         * could appear on top of whatever the user had moved on to.
         *
         * <p>Now: if an ad is warm it shows instantly with no sheet at all. If not, the user is not
         * made to wait — they continue immediately and an ad is fetched for the next opportunity.
         * No impression is lost either way, because a late arrival stays in the pool.
         */
        private static void loadAndShowInterstitial(final AppCompatActivity activity,
                                                    String adUnitId,
                                                    final InternalAdListener listener) {
            boolean shown = InterstitialPool.show(activity, adUnitId, () -> {
                isInterstitialVisible = false;
                if (listener != null) {
                    listener.onAdClosed();
                }
            });

            if (shown) {
                isInterstitialVisible = true;
                adShowed++;
                return;
            }

            // Nothing warm yet. Let the user through rather than holding them behind a spinner;
            // InterstitialPool.show() has already started fetching one for next time.
            if (listener != null) {
                listener.onAdClosed();
            }
        }

        private static FullScreenContentCallback createInterstitialContentCallback(
                final Dialog loadingDialog, final InternalAdListener listener,
                final Runnable timeoutRunnable, final Handler handler) {
            return new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    isInterstitialVisible = false;
                    dismissDialog(loadingDialog);
                    if (listener != null) {
                        listener.onAdClosed();
                    }
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    isInterstitialVisible = false;
                    dismissDialog(loadingDialog);
                    if (listener != null) {
                        listener.onAdClosed();
                    }
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    if (handler != null && timeoutRunnable != null) {
                        handler.removeCallbacks(timeoutRunnable);
                    }
                    dismissDialog(loadingDialog);
                    isInterstitialVisible = true;
                    adShowed++;
                }
            };
        }

        private static boolean shouldShowInterstitial(Context context) {
            int showCount = AppPreference.getInt(context, Keys.KEY_SHOWICON, 3);
            if (showCount == 0) {
                return false;
            }
            return adCounter % showCount == 0 || adCounter > showCount;
        }

        private static boolean hasReachedAdLimit(Context context) {
            int allowedCount = AppPreference.getInt(context, Keys.KEY_ALLOWED_PLAY, 10);
            return adShowed >= allowedCount;
        }

        private static String getInterstitialAdUnitId(Context context) {
            if (isStartInterViewed) {
                return AppPreference.getString(context, Keys.KEY_ID_INTERSTITIAL, "");
            }

            String startAdUnitId = AppPreference.getString(context, Keys.KEY_ID_START_INTERSTITIAL, "");
            if (startAdUnitId.isEmpty()) {
                startAdUnitId = AppPreference.getString(context, Keys.KEY_ID_INTERSTITIAL, "");
            }
            isStartInterViewed = true;
            return startAdUnitId;
        }

        // ==================== Rewarded Ads ====================
        public static void showRewardedAd(final AppCompatActivity activity,
                                          final RewardedAdListener listener) {

            if (!checkIsInternetConnection(activity) || !isAdsEnabled(activity)) {
                listener.onRewardNull();
                return;
            }

            if (!isAdsEnabled(activity)) {
                listener.onRewardNull();
                return;
            }

            String adUnitId = AppPreference.getString(activity, Keys.KEY_ID_REWARDED, "");
            if (adUnitId.isEmpty()) {
                listener.onRewardNull();
                return;
            }

            if (!AppPreference.getBoolean(activity, Keys.KEY_REWARD_ADS_INTER, true)) {
                showRewardedInterstitialAd(activity, listener);
                return;
            }

            AdsManagerMediation.showLoadingDialog(activity);
            listener.onRewardProgress();
            AdsAnalytics.logAdRequest(activity, AdsAnalytics.NETWORK_ADMOB,
                    AdsAnalytics.TYPE_REWARDED, adUnitId);
            final long loadStartTime = AdsAnalytics.startLoadTimer();

            RewardedAd.load(
                    activity,
                    adUnitId,
                    createAdRequest(),
                    new RewardedAdLoadCallback() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            AdsManagerMediation.dismissLoadingDialog();
                            AdsAnalytics.logAdLoadFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                                    AdsAnalytics.TYPE_REWARDED, adUnitId,
                                    loadAdError.getCode(), loadAdError.getMessage());
                            listener.onRewardNull();
                        }

                        @Override
                        public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                            AdsAnalytics.logAdLoaded(activity, AdsAnalytics.NETWORK_ADMOB,
                                    AdsAnalytics.TYPE_REWARDED, adUnitId, loadStartTime);

                            final boolean[] rewardGranted = {false};

                            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                                @Override
                                public void onAdDismissedFullScreenContent() {
                                    AdsManagerMediation.isInterstitialVisible = false;
                                    AdsManagerMediation.dismissLoadingDialog();
                                    AdsAnalytics.logAdClosed(activity, AdsAnalytics.NETWORK_ADMOB,
                                            AdsAnalytics.TYPE_REWARDED, adUnitId);
                                    if (rewardGranted[0]) {
                                        listener.onRewardGranted();
                                    } else {
                                        listener.onVideoSkipped();
                                    }
                                }

                                @Override
                                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                    AdsManagerMediation.isInterstitialVisible = false;
                                    AdsManagerMediation.dismissLoadingDialog();
                                    AdsAnalytics.logAdShowFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                                            AdsAnalytics.TYPE_REWARDED, adUnitId,
                                            adError.getCode(), adError.getMessage());
                                    listener.onRewardNull();
                                }

                                @Override
                                public void onAdShowedFullScreenContent() {
                                    AdsManagerMediation.isInterstitialVisible = true;
                                    AdsAnalytics.logAdImpression(activity, AdsAnalytics.NETWORK_ADMOB,
                                            AdsAnalytics.TYPE_REWARDED, adUnitId);
                                }
                            });

                            rewardedAd.show(activity, rewardItem -> {
                                rewardGranted[0] = true;
                                AdsAnalytics.logAdRewarded(activity, AdsAnalytics.NETWORK_ADMOB,
                                        adUnitId, rewardItem.getType(), rewardItem.getAmount());
                            });
                        }
                    });
        }

        public static void showRewardedInterstitialAd(final AppCompatActivity activity,
                                                      final RewardedAdListener listener) {
            if (!isAdsEnabled(activity)) {
                listener.onRewardNull();
                return;
            }

            String adUnitId = AppPreference.getString(activity, Keys.KEY_ID_REWARDED_INTER, "");
            if (adUnitId.isEmpty()) {
                listener.onRewardNull();
                return;
            }

            listener.onRewardProgress();
            AdsAnalytics.logAdRequest(activity, AdsAnalytics.NETWORK_ADMOB,
                    AdsAnalytics.TYPE_REWARDED_INTERSTITIAL, adUnitId);
            final long loadStartTime = AdsAnalytics.startLoadTimer();

            RewardedInterstitialAd.load(
                    activity,
                    adUnitId,
                    createAdRequest(),
                    new RewardedInterstitialAdLoadCallback() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            AdsManagerMediation.dismissLoadingDialog();
                            AdsAnalytics.logAdLoadFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                                    AdsAnalytics.TYPE_REWARDED_INTERSTITIAL, adUnitId,
                                    loadAdError.getCode(), loadAdError.getMessage());
                            listener.onRewardNull();
                        }

                        @Override
                        public void onAdLoaded(@NonNull RewardedInterstitialAd rewardedInterstitialAd) {
                            AdsAnalytics.logAdLoaded(activity, AdsAnalytics.NETWORK_ADMOB,
                                    AdsAnalytics.TYPE_REWARDED_INTERSTITIAL, adUnitId, loadStartTime);

                            final boolean[] rewardGranted = {false};

                            rewardedInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                                @Override
                                public void onAdDismissedFullScreenContent() {
                                    AdsManagerMediation.isInterstitialVisible = false;
                                    AdsManagerMediation.dismissLoadingDialog();
                                    AdsAnalytics.logAdClosed(activity, AdsAnalytics.NETWORK_ADMOB,
                                            AdsAnalytics.TYPE_REWARDED_INTERSTITIAL, adUnitId);
                                    if (rewardGranted[0]) {
                                        listener.onRewardGranted();
                                    } else {
                                        listener.onVideoSkipped();
                                    }
                                }

                                @Override
                                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                    AdsManagerMediation.isInterstitialVisible = false;
                                    AdsManagerMediation.dismissLoadingDialog();
                                    AdsAnalytics.logAdShowFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                                            AdsAnalytics.TYPE_REWARDED_INTERSTITIAL, adUnitId,
                                            adError.getCode(), adError.getMessage());
                                    listener.onRewardNull();
                                }

                                @Override
                                public void onAdShowedFullScreenContent() {
                                    AdsManagerMediation.isInterstitialVisible = true;
                                    AdsAnalytics.logAdImpression(activity, AdsAnalytics.NETWORK_ADMOB,
                                            AdsAnalytics.TYPE_REWARDED_INTERSTITIAL, adUnitId);
                                }
                            });

                            rewardedInterstitialAd.show(activity, rewardItem -> {
                                rewardGranted[0] = true;
                                AdsAnalytics.logAdRewarded(activity, AdsAnalytics.NETWORK_ADMOB,
                                        adUnitId, rewardItem.getType(), rewardItem.getAmount());
                            });
                        }
                    });
        }

        // Helper Methods
        private static Dialog createLoadingDialog(AppCompatActivity activity) {
            Dialog dialog = new Dialog(activity, R.style.FullScreenDialogTheme);
            dialog.setContentView(R.layout.dialog_prepair_loading_ads);
            dialog.setCancelable(false);
            AdController.makeFullScreen(dialog);
            return dialog;
        }

        private static void dismissDialog(Dialog dialog) {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }

        private static void hideContainer(ViewGroup container) {
            container.setVisibility(View.GONE);
        }

        private static boolean isAdsEnabled(Context context) {
            return AdController.shouldShowAds(context);
        }
    }

    /**
     * Utility methods for app operations
     */
    public static class Utils {


        public static void rateApp(Context context) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + context.getPackageName())));
            } catch (ActivityNotFoundException e) {
                context.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://play.google.com/store/apps/details?id=" + context.getPackageName())));
            }
        }

        public static void shareApp(Context context) {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name));
                String shareText = context.getString(R.string.app_name) +
                        "\nhttps://play.google.com/store/apps/details?id=" + context.getPackageName();
                intent.putExtra(Intent.EXTRA_TEXT, shareText);
                context.startActivity(Intent.createChooser(intent, "Share App using"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void moreApps(Context context) {
            String devName = AppPreference.getString(context, Keys.KEY_DEV_NAME, "");
            if (devName.isEmpty()) {
                Toast.makeText(context, "No more apps to show.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://search?q=pub:" + devName));
            context.startActivity(intent);
        }

        public static void openPolicy(Context context) {
            openUrl(context, AppPreference.getString(context, Keys.KEY_POLICY, ""),
                    "No policy URL found.");
        }

        public static void openTerms(Context context) {
            openPolicy(context);
        }

        private static void openUrl(Context context, String url, String errorMessage) {
            try {
                if (url.isEmpty()) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse(url));
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void writeSuggestions(Context context) {
            writeSuggestions(context, "");
        }

        public static void writeSuggestions(Context context, String extraText) {
            String email = AppPreference.getString(context, Keys.KEY_DEV_EMAIL, "");
            if (email.isEmpty()) {
                Toast.makeText(context, "No email found.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null));
            intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name));
            intent.putExtra(Intent.EXTRA_TEXT, extraText);
            context.startActivity(Intent.createChooser(intent, "Feedback"));
        }

        /**
         * Exit is a plain exit. The old version put a native ad inside the "do you want to exit?"
         * sheet — an ad shown on the way out of the app, which Play's Disruptive Ads policy names
         * explicitly (strike received 2026-10-14). No dialog, no ad: the double-back in
         * MainActivity is the confirmation.
         */
        public static void showExitDialog(final AppCompatActivity activity) {
            activity.finishAffinity();
        }

        public static boolean isOldAppVersion(final AppCompatActivity activity) {
            int savedVersion = AppPreference.getInt(activity, Keys.KEY_VERSION_CODE, 2);
            if (savedVersion <= 2) {
                return false;
            }

            new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog)
                    .setTitle("Update Your App")
                    .setMessage("New App Version is Available on Play Store, Please Update to access this app.")
                    .setCancelable(false)
                    .setPositiveButton("Update", (dialog, which) -> {
                        try {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id="
                                            + activity.getPackageName())));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(activity, "You don't have Google Play installed",
                                    Toast.LENGTH_LONG).show();
                        }
                        activity.finishAffinity();
                    })
                    .setNegativeButton("Exit", (dialog, which) -> activity.finishAffinity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();

            return true;
        }

        // showWarningMessage() — the remote-config "(Ad)" cross-promo dialog — was REMOVED on
        // 2026-10-14. It was non-dismissible, its only button opened another app's Play Store page
        // and then called finishAffinity(): a forced ad click gating use of the app, which is the
        // first example in Play's Disruptive Ads policy. Do not bring it back in any form.
    }


}