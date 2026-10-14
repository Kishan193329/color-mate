package com.colourmate.paintbynumber.ads;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

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

import java.util.Date;

/**
 * Utility class for managing Google AdMob advertisements.
 * Handles App Open, Interstitial, Banner, and Native ads.
 */
public class GoogleAdsUtils {

    // Constants
    private static final long AD_EXPIRATION_TIME_MS = 4 * 60 * 60 * 1000; // 4 hours in milliseconds

    // State variables
    private static AppOpenAd appOpenAd = null;
    private static RewardedAd mRewardedAd = null;
    private static RewardedInterstitialAd mRewardedInterstitialAd = null;
    private static boolean isStartInterViewed = false;

    public static boolean isRewardedAdLoaded(Context context) {
        if (!isAdsEnabled(context)) return false;
        if (mRewardedAd != null) return true;
        if (!AppPreference.getBoolean(context, Keys.KEY_REWARD_ADS_INTER, true)) {
            return mRewardedInterstitialAd != null;
        }
        return false;
    }

    // ==================== Initialization ====================

    /**
     * Initialize Google Mobile Ads SDK
     */
    public static void initialize(Context context) {
        MobileAds.initialize(context.getApplicationContext(), initializationStatus -> {
            // Preload ads after initialization
            preloadRewardedAd(context);
            preloadRewardedInterstitialAd(context);
        });
    }

    // ==================== Splash Ads ====================

    /**
     * Show App Open ad on splash screen
     */
    public static void showSplashAppOpen(final Context context, final AdLoadingListener listener) {
        String adUnitId = AppPreference.getString(context, Keys.KEY_ID_SPLASH_APP_OPEN, "");

        if (adUnitId.isEmpty()) {
            listener.OnAdFailedToLoad();
            return;
        }

        AdsAnalytics.logAdRequest(context, AdsAnalytics.NETWORK_ADMOB,
                AdsAnalytics.TYPE_SPLASH_APP_OPEN, adUnitId);
        final long loadStartTime = AdsAnalytics.startLoadTimer();
        AppOpenAd.load(
                context,
                adUnitId,
                createAdRequest(),
                createSplashAppOpenCallback(context, adUnitId, loadStartTime, listener));
    }

    private static AppOpenAd.AppOpenAdLoadCallback createSplashAppOpenCallback(
            final Context context, final String adUnitId,
            final long loadStartTime, final AdLoadingListener listener) {
        return new AppOpenAd.AppOpenAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                AdsManagerMediation.isInterstitialVisible = false;
                AdsAnalytics.logAdLoadFailed(context, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_SPLASH_APP_OPEN, adUnitId,
                        loadAdError.getCode(), loadAdError.getMessage());
                listener.OnAdFailedToLoad();
                Log.i("TAG######", "onAdFailedToLoad: " + loadAdError);
            }

            @Override
            public void onAdLoaded(@NonNull AppOpenAd ad) {
                super.onAdLoaded(ad);
                appOpenAd = ad;
                AdsAnalytics.logAdLoaded(context, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_SPLASH_APP_OPEN, adUnitId, loadStartTime);
                appOpenAd.setFullScreenContentCallback(createAppOpenContentCallback(context, adUnitId, listener));
                appOpenAd.show((Activity) context);
            }
        };
    }

    private static FullScreenContentCallback createAppOpenContentCallback(
            final Context context, final String adUnitId, final AdLoadingListener listener) {
        return new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                AdsManagerMediation.isInterstitialVisible = false;
                AdsAnalytics.logAdClosed(context, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_SPLASH_APP_OPEN, adUnitId);
                appOpenAd = null;
                listener.OnAdClosed();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                AdsManagerMediation.isInterstitialVisible = false;
                AdsAnalytics.logAdShowFailed(context, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_SPLASH_APP_OPEN, adUnitId,
                        adError.getCode(), adError.getMessage());
                listener.OnAdFailedToLoad();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                AdsManagerMediation.isInterstitialVisible = true;
                AdsAnalytics.logAdImpression(context, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_SPLASH_APP_OPEN, adUnitId);
            }
        };
    }

    /**
     * Show Interstitial ad on splash screen
     */
    public static void showSplashInterstitial(final Context context, final AdLoadingListener listener) {
        String adUnitId = AppPreference.getString(context, Keys.KEY_ID_SPLASH, "");

        if (adUnitId.isEmpty()) {
            listener.OnAdFailedToLoad();
            return;
        }

        AdsAnalytics.logAdRequest(context, AdsAnalytics.NETWORK_ADMOB,
                AdsAnalytics.TYPE_SPLASH_INTERSTITIAL, adUnitId);
        final long loadStartTime = AdsAnalytics.startLoadTimer();
        InterstitialAd.load(
                context,
                adUnitId,
                createAdRequest(),
                createSplashInterstitialCallback(context, adUnitId, loadStartTime, listener));
    }

    private static InterstitialAdLoadCallback createSplashInterstitialCallback(
            final Context context, final String adUnitId,
            final long loadStartTime, final AdLoadingListener listener) {
        return new InterstitialAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                AdsManagerMediation.isInterstitialVisible = false;
                AdsAnalytics.logAdLoadFailed(context, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_SPLASH_INTERSTITIAL, adUnitId,
                        loadAdError.getCode(), loadAdError.getMessage());
                listener.OnAdFailedToLoad();
            }

            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                AdsAnalytics.logAdLoaded(context, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_SPLASH_INTERSTITIAL, adUnitId, loadStartTime);
                interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        AdsManagerMediation.isInterstitialVisible = false;
                        AdsAnalytics.logAdClosed(context, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_SPLASH_INTERSTITIAL, adUnitId);
                        listener.OnAdClosed();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        AdsManagerMediation.isInterstitialVisible = false;
                        AdsAnalytics.logAdShowFailed(context, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_SPLASH_INTERSTITIAL, adUnitId,
                                adError.getCode(), adError.getMessage());
                        listener.OnAdClosed();
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        AdsManagerMediation.isInterstitialVisible = true;
                        AdsAnalytics.logAdImpression(context, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_SPLASH_INTERSTITIAL, adUnitId);
                    }
                });
                interstitialAd.show((Activity) context);
            }
        };
    }

    // ==================== Interstitial Ads ====================

    /**
     * Show interstitial ad with counter logic
     */
    public static void showInterstitial(final AppCompatActivity activity, final AdLoadingListener listener) {
        AdsManagerMediation.adCounter++;

        if (!isAdsEnabled(activity)) {
            listener.OnAdClosed();
            return;
        }

        if (!shouldShowInterstitial(activity)) {
            listener.OnAdClosed();
            return;
        }

        AdsManagerMediation.adCounter = 0;

        if (hasReachedAdLimit(activity)) {
            listener.OnAdClosed();
            return;
        }

        String adUnitId = getInterstitialAdUnitId(activity);
        if (adUnitId.isEmpty()) {
            listener.OnAdFailedToLoad();
            return;
        }

        listener.OnAdStartLoading();
        loadAndShowInterstitial(activity, adUnitId, listener);
    }

    /**
     * Show interstitial ad without counter logic (for app open scenarios)
     */
    public static void showInterstitialOpen(final AppCompatActivity activity, final AdLoadingListener listener) {
        if (!isAdsEnabled(activity)) {
            listener.OnAdClosed();
            return;
        }

        if (hasReachedAdLimit(activity)) {
            listener.OnAdClosed();
            return;
        }

        String adUnitId = getInterstitialAdUnitId(activity);
        if (adUnitId.isEmpty()) {
            listener.OnAdFailedToLoad();
            return;
        }

        listener.OnAdStartLoading();
        loadAndShowInterstitial(activity, adUnitId, listener);
    }

    /**
     * Show back button interstitial if enabled
     */
    public static void showBackInterstitial(AppCompatActivity activity, AdLoadingListener listener) {
        // Back-press interstitials are permanently off (Play Disruptive Ads strike, 2026-10-14).
        if (listener != null) listener.OnAdClosed();
    }

    private static void loadAndShowInterstitial(final AppCompatActivity activity,
            final String adUnitId,
            final AdLoadingListener listener) {
        AdsAnalytics.logAdRequest(activity, AdsAnalytics.NETWORK_ADMOB,
                AdsAnalytics.TYPE_INTERSTITIAL, adUnitId);
        final long loadStartTime = AdsAnalytics.startLoadTimer();
        InterstitialAd.load(
                activity.getApplicationContext(),
                adUnitId,
                createAdRequest(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        AdsManagerMediation.isInterstitialVisible = false;
                        AdsAnalytics.logAdLoadFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_INTERSTITIAL, adUnitId,
                                loadAdError.getCode(), loadAdError.getMessage());
                        listener.OnAdFailedToLoad();
                    }

                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        AdsAnalytics.logAdLoaded(activity, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_INTERSTITIAL, adUnitId, loadStartTime);
                        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                AdsManagerMediation.isInterstitialVisible = false;
                                AdsAnalytics.logAdClosed(activity, AdsAnalytics.NETWORK_ADMOB,
                                        AdsAnalytics.TYPE_INTERSTITIAL, adUnitId);
                                listener.OnAdClosed();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                AdsManagerMediation.isInterstitialVisible = false;
                                AdsAnalytics.logAdShowFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                                        AdsAnalytics.TYPE_INTERSTITIAL, adUnitId,
                                        adError.getCode(), adError.getMessage());
                                listener.OnAdFailedToLoad();
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                AdsManagerMediation.adShowed++;
                                AdsManagerMediation.isInterstitialVisible = true;
                                AdsAnalytics.logAdImpression(activity, AdsAnalytics.NETWORK_ADMOB,
                                        AdsAnalytics.TYPE_INTERSTITIAL, adUnitId);
                                listener.OnAdShow();
                            }
                        });
                        interstitialAd.show(activity);
                    }
                });
    }

    private static boolean shouldShowInterstitial(Context context) {
        int showCount = AppPreference.getInt(context, Keys.KEY_SHOWICON, 3);
        if (showCount == 0) {
            return false;
        }

        int adCounter = AdsManagerMediation.adCounter;
        return adCounter % showCount == 0 || adCounter > showCount;
    }

    private static boolean hasReachedAdLimit(Context context) {
        int allowedCount = AppPreference.getInt(context, Keys.KEY_ALLOWED_PLAY, 10);
        return AdsManagerMediation.adShowed >= allowedCount;
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

    /**
     * Load and show a rewarded ad (AdMob).
     * The {@link RewardedAdListener#onRewardGranted()} callback is fired only
     * when the user finishes watching the video and earns the reward.
     *
     * @param activity the host activity
     * @param listener rewarded ad event callbacks
     */
    public static void showRewardedAd(final AppCompatActivity activity,
            final RewardedAdListener listener) {
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

        // Use preloaded ad if available
        if (mRewardedAd != null) {
            final RewardedAd adToShow = mRewardedAd;
            mRewardedAd = null; // Consume the ad
            showGoogleRewardedAd(activity, adToShow, adUnitId, listener);
            preloadRewardedAd(activity); // Start preloading for the next time
            return;
        }

        // No preloaded ad → load on demand with progress
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
                        AdsAnalytics.logAdLoadFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_REWARDED, adUnitId,
                                loadAdError.getCode(), loadAdError.getMessage());
                        listener.onRewardNull();
                    }

                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        AdsAnalytics.logAdLoaded(activity, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_REWARDED, adUnitId, loadStartTime);
                        showGoogleRewardedAd(activity, rewardedAd, adUnitId, listener);
                        preloadRewardedAd(activity);
                    }
                });
    }

    private static void showGoogleRewardedAd(final AppCompatActivity activity,
                                             final RewardedAd ad,
                                             final String adUnitId,
                                             final RewardedAdListener listener) {
        final boolean[] rewardGranted = { false };
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                AdsManagerMediation.isInterstitialVisible = false;
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
                listener.onAdShow();
            }
        });

        ad.show(activity, rewardItem -> {
            rewardGranted[0] = true;
            AdsAnalytics.logAdRewarded(activity, AdsAnalytics.NETWORK_ADMOB,
                    adUnitId, rewardItem.getType(), rewardItem.getAmount());
        });
    }

    /**
     * Start background loading of a rewarded ad.
     */
    public static void preloadRewardedAd(final Context context) {
        if (!isAdsEnabled(context) || mRewardedAd != null) return;

        String id = AppPreference.getString(context, Keys.KEY_ID_REWARDED, "");
        if (id.isEmpty()) return;

        final long start = AdsAnalytics.startLoadTimer();
        RewardedAd.load(context, id, createAdRequest(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                mRewardedAd = ad;
                AdsAnalytics.logAdLoaded(context, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_REWARDED, id, start);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                mRewardedAd = null;
            }
        });
    }

    /**
     * Load and show a rewarded interstitial ad (AdMob).
     */
    public static void showRewardedInterstitialAd(final AppCompatActivity activity,
                                                    final RewardedAdListener listener) {
        if (!isAdsEnabled(activity)) {
            listener.onRewardNull();
            return;
        }

        String id = AppPreference.getString(activity, Keys.KEY_ID_REWARDED_INTER, "");
        if (id.isEmpty()) id = AppPreference.getString(activity, Keys.KEY_ID_REWARDED, "");
        final String adUnitId = id;

        if (adUnitId.isEmpty()) {
            listener.onRewardNull();
            return;
        }

        if (mRewardedInterstitialAd != null) {
            final RewardedInterstitialAd adToShow = mRewardedInterstitialAd;
            mRewardedInterstitialAd = null;
            showGoogleRewardedInterstitialAd(activity, adToShow, adUnitId, listener);
            preloadRewardedInterstitialAd(activity);
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
                        AdsAnalytics.logAdLoadFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_REWARDED_INTERSTITIAL, adUnitId,
                                loadAdError.getCode(), loadAdError.getMessage());
                        listener.onRewardNull();
                    }

                    @Override
                    public void onAdLoaded(@NonNull RewardedInterstitialAd rewardedInterstitialAd) {
                        AdsAnalytics.logAdLoaded(activity, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_REWARDED_INTERSTITIAL, adUnitId, loadStartTime);
                        showGoogleRewardedInterstitialAd(activity, rewardedInterstitialAd, adUnitId, listener);
                        preloadRewardedInterstitialAd(activity);
                    }
                });
    }

    private static void showGoogleRewardedInterstitialAd(final AppCompatActivity activity,
                                                        final RewardedInterstitialAd ad,
                                                        final String adUnitId,
                                                        final RewardedAdListener listener) {
        final boolean[] rewardGranted = { false };
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                AdsManagerMediation.isInterstitialVisible = false;
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
                listener.onAdShow();
            }
        });

        ad.show(activity, rewardItem -> {
            rewardGranted[0] = true;
            AdsAnalytics.logAdRewarded(activity, AdsAnalytics.NETWORK_ADMOB,
                    adUnitId, rewardItem.getType(), rewardItem.getAmount());
        });
    }

    public static void preloadRewardedInterstitialAd(final Context context) {
        if (!isAdsEnabled(context) || mRewardedInterstitialAd != null) return;

        String id = AppPreference.getString(context, Keys.KEY_ID_REWARDED_INTER, "");
        if (id.isEmpty()) id = AppPreference.getString(context, Keys.KEY_ID_REWARDED, "");
        if (id.isEmpty()) return;

        final String adUnitId = id;
        final long start = AdsAnalytics.startLoadTimer();
        RewardedInterstitialAd.load(context, adUnitId, createAdRequest(), new RewardedInterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedInterstitialAd ad) {
                mRewardedInterstitialAd = ad;
                AdsAnalytics.logAdLoaded(context, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_REWARDED_INTERSTITIAL, adUnitId, start);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                mRewardedInterstitialAd = null;
            }
        });
    }

    // ==================== Banner Ads ====================

    /**
     * Load and display banner ad in default container
     */
    public static void getBannerAd(AppCompatActivity activity, AdLoadingListener listener) {
        ViewGroup container = activity.findViewById(R.id.container_banner);
        getBannerAd(activity, container, listener);
    }

    /**
     * Load and display banner ad in specified container
     */
    public static void getBannerAd(final AppCompatActivity activity, final ViewGroup container,
            final AdLoadingListener listener) {
        if (!isAdsEnabled(activity)) {
            hideContainer(container);
            return;
        }

        final String adUnitId = AppPreference.getString(activity, Keys.KEY_ID_BANNER, "");
        if (adUnitId.isEmpty()) {
            hideContainer(container);
            listener.OnAdFailedToLoad();
            return;
        }

        AdsAnalytics.logAdRequest(activity, AdsAnalytics.NETWORK_ADMOB,
                AdsAnalytics.TYPE_BANNER, adUnitId);
        final long loadStartTime = AdsAnalytics.startLoadTimer();

        final AdView adView = new AdView(activity);
        adView.setAdSize(getAdaptiveBannerAdSize(activity));
        adView.setAdUnitId(adUnitId);
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                AdsAnalytics.logAdLoadFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_BANNER, adUnitId,
                        loadAdError.getCode(), loadAdError.getMessage());
                listener.OnAdFailedToLoad();
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                AdsAnalytics.logAdLoaded(activity, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_BANNER, adUnitId, loadStartTime);
                container.setVisibility(View.VISIBLE);

                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.gravity = android.view.Gravity.CENTER;

                container.removeAllViews();
                container.addView(adView, layoutParams);
                listener.OnAdClosed();
            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                AdsAnalytics.logAdClick(activity, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_BANNER, adUnitId);
            }

            @Override
            public void onAdImpression() {
                super.onAdImpression();
                AdsAnalytics.logAdImpression(activity, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_BANNER, adUnitId);
            }
        });
        adView.loadAd(createAdRequest());
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

    /**
     * Load native ad in default container
     */
    public static void getNativeAd(AppCompatActivity activity, boolean isLarge, AdLoadingListener listener) {
        ViewGroup container = isLarge
                ? activity.findViewById(R.id.container_native_view)
                : activity.findViewById(R.id.container_native_item);
        getNativeAd(activity, container, isLarge, listener);
    }

    /**
     * Load and display native ad
     */
    public static void getNativeAd(AppCompatActivity activity, final ViewGroup container,
            boolean isLarge, final AdLoadingListener listener) {
        if (!isAdsEnabled(activity)) {
            container.setVisibility(View.GONE);
            return;
        }

        // Try to use cached native ad first
        if (!AdsManagerMediation.nativeAds.isEmpty()) {
            displayCachedNativeAd(activity, container, isLarge, listener);
            return;
        }

        // Load new native ad
        loadAndDisplayNativeAd(activity, container, isLarge, listener);
    }

    private static void displayCachedNativeAd(AppCompatActivity activity, ViewGroup container,
            boolean isLarge, AdLoadingListener listener) {
        NativeAd nativeAd = (NativeAd) AdsManagerMediation.nativeAds.remove(0);
        NativeAdView nativeAdView = inflateNativeAdView(activity, isLarge);

        populateNativeAd(nativeAd, nativeAdView, activity);

        container.removeAllViews();
        container.addView(nativeAdView);
        listener.OnAdClosed();
    }

    private static void loadAndDisplayNativeAd(AppCompatActivity activity, final ViewGroup container,
            final boolean isLarge, final AdLoadingListener listener) {
        String adUnitId = AppPreference.getString(activity, Keys.KEY_ID_NATIVE, "");

        if (adUnitId.isEmpty()) {
            listener.OnAdFailedToLoad();
            return;
        }

        AdsAnalytics.logAdRequest(activity, AdsAnalytics.NETWORK_ADMOB,
                AdsAnalytics.TYPE_NATIVE, adUnitId);
        new AdLoader.Builder(activity, adUnitId)
                .forNativeAd(nativeAd -> {
                    AdsAnalytics.logAdLoaded(activity, AdsAnalytics.NETWORK_ADMOB,
                            AdsAnalytics.TYPE_NATIVE, adUnitId);
                    NativeAdView nativeAdView = inflateNativeAdView(activity, isLarge);
                    populateNativeAd(nativeAd, nativeAdView, activity);

                    container.removeAllViews();
                    container.addView(nativeAdView);
                    listener.OnAdClosed();
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        AdsAnalytics.logAdLoadFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_NATIVE, adUnitId,
                                loadAdError.getCode(), loadAdError.getMessage());
                        listener.OnAdFailedToLoad();
                        container.setVisibility(View.GONE);
                    }
                })
                .withNativeAdOptions(new NativeAdOptions.Builder().build())
                .build()
                .loadAd(createAdRequest());
    }

    /**
     * Preload native ad for caching
     */
    public static void loadNativeAd(Context context, final AdLoadingListener listener) {
        String adUnitId = AppPreference.getString(context, Keys.KEY_ID_NATIVE, "");

        if (adUnitId.isEmpty()) {
            listener.OnAdFailedToLoad();
            return;
        }

        AdsAnalytics.logAdRequest(context, AdsAnalytics.NETWORK_ADMOB,
                AdsAnalytics.TYPE_NATIVE, adUnitId);
        new AdLoader.Builder(context, adUnitId)
                .forNativeAd(nativeAd -> {
                    AdsAnalytics.logAdLoaded(context, AdsAnalytics.NETWORK_ADMOB,
                            AdsAnalytics.TYPE_NATIVE, adUnitId);
                    AdsManagerMediation.nativeAds.add(nativeAd);
                    int preloadLimit = AppPreference.getInt(context, Keys.KEY_PRELOAD_LIMIT, 2);

                    if (AdsManagerMediation.nativeAds.size() < preloadLimit) {
                        listener.OnAdFailedToLoad(); // Trigger loading more
                    }
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        AdsAnalytics.logAdLoadFailed(context, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_NATIVE, adUnitId,
                                loadAdError.getCode(), loadAdError.getMessage());
                        listener.OnAdFailedToLoad();
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

        // Register the ad
        adView.setNativeAd(nativeAd);
    }

    // ==================== Helper Methods ====================

    private static AdRequest createAdRequest() {
        return new AdRequest.Builder().build();
    }

    private static boolean isAdsEnabled(Context context) {
        return AdController.shouldShowAds(context);
    }

    private static void hideContainer(ViewGroup container) {
        container.removeAllViews();
        container.setVisibility(View.GONE);
    }

    // ==================== App Open Ad Manager ====================

    /**
     * Manages App Open ads with lifecycle awareness.
     * Shows ads when app comes to foreground.
     */
    public static class AppOpenManager implements Application.ActivityLifecycleCallbacks, LifecycleObserver {

        public static boolean isOpenGoogleAd = true;
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

        // ==================== Ad Loading ====================

        public void fetchAd() {
            if (isAdAvailable()) {
                return;
            }

            String adUnitId = AppPreference.getString(myApplication, Keys.KEY_ID_APP_OPEN, "");
            if (adUnitId.isEmpty()) {
                return;
            }

            AdsAnalytics.logAdRequest(myApplication, AdsAnalytics.NETWORK_ADMOB,
                    AdsAnalytics.TYPE_APP_OPEN, adUnitId);
            appOpenLoadStartTime = AdsAnalytics.startLoadTimer();
            AppOpenAd.load(
                    myApplication,
                    adUnitId,
                    createAdRequest(),
                    createAppOpenAdLoadCallback());
        }

        private long appOpenLoadStartTime = 0;

        private AppOpenAd.AppOpenAdLoadCallback createAppOpenAdLoadCallback() {
            return new AppOpenAd.AppOpenAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    Log.i("TAG######", "onAdFailedToLoad: " + loadAdError);
                    AdsAnalytics.logAdLoadFailed(myApplication, AdsAnalytics.NETWORK_ADMOB,
                            AdsAnalytics.TYPE_APP_OPEN, "",
                            loadAdError.getCode(), loadAdError.getMessage());
                    super.onAdFailedToLoad(loadAdError);
                }

                @Override
                public void onAdLoaded(@NonNull AppOpenAd ad) {
                    super.onAdLoaded(ad);
                    appOpenAd = ad;
                    AdsAnalytics.logAdLoaded(myApplication, AdsAnalytics.NETWORK_ADMOB,
                            AdsAnalytics.TYPE_APP_OPEN, "", appOpenLoadStartTime);
                    appOpenAd.setFullScreenContentCallback(createFullScreenContentCallback());
                    loadTime = new Date().getTime();
                }
            };
        }

        private FullScreenContentCallback createFullScreenContentCallback() {
            return new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    AdsManagerMediation.isInterstitialVisible = false;
                    AdsAnalytics.logAdClosed(myApplication, AdsAnalytics.NETWORK_ADMOB,
                            AdsAnalytics.TYPE_APP_OPEN, "");
                    appOpenAd = null;
                    isShowingAd = false;
                    fetchAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    Log.i("TAG######", "onAdFailedToShowFullScreenContent: " + adError);
                    AdsAnalytics.logAdShowFailed(myApplication, AdsAnalytics.NETWORK_ADMOB,
                            AdsAnalytics.TYPE_APP_OPEN, "",
                            adError.getCode(), adError.getMessage());
                    AdsManagerMediation.isInterstitialVisible = false;
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    AdsManagerMediation.isInterstitialVisible = true;
                    isShowingAd = true;
                    AdsAnalytics.logAdImpression(myApplication, AdsAnalytics.NETWORK_ADMOB,
                            AdsAnalytics.TYPE_APP_OPEN, "");
                }
            };
        }

        // ==================== Ad Availability ====================

        public boolean isAdAvailable() {
            return appOpenAd != null && wasLoadTimeLessThanNHoursAgo();
        }

        private boolean wasLoadTimeLessThanNHoursAgo() {
            long currentTime = new Date().getTime();
            long elapsedTime = currentTime - loadTime;
            return elapsedTime < AD_EXPIRATION_TIME_MS;
        }

        // ==================== Ad Display ====================

        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        public void onStart() {
            showAdIfAvailable();
        }

        public void showAdIfAvailable() {
            // Skip if currently showing an interstitial
            if (AdsManagerMediation.isInterstitialVisible) {
                return;
            }

            if (!isOpenGoogleAd) {
                isOpenGoogleAd = true;
                return;
            }

            // Skip if on splash screen
            if (isSplashActivity()) {
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

        // ==================== Activity Lifecycle Callbacks ====================

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
            // Not needed
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
            // Not needed
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            // Not needed
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
            // Not needed
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            currentActivity = null;
        }
    }
}