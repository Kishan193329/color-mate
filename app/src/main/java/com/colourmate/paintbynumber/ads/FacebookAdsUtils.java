package com.colourmate.paintbynumber.ads;

import android.content.Context;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Facebook Audience Network — REMOVED 2026-10-14, kept as a no-op stub.
 *
 * <p>No Facebook app id or unit id has ever been configured (Firebase {@code V1} carries none and
 * {@code network_order} is empty), so the two Facebook SDKs only ever cost ~5 MB of APK and a
 * ContentProvider initialisation on every cold start. The dependencies are gone from
 * {@code app/build.gradle}; this class keeps the signatures the mediation code calls so the
 * network can be skipped at runtime exactly as a no-fill would be. To bring FAN back: restore the
 * dependencies and this file from git.
 */
public class FacebookAdsUtils {

    private static void fail(AdLoadingListener listener) {
        if (listener != null) listener.OnAdFailedToLoad();
    }

    public static boolean isRewardedAdLoaded(Context context) {
        return false;
    }

    public static void initialize(Context context) {
        // nothing to initialise
    }

    public static void showSplashAppOpen(Context context, AdLoadingListener listener) {
        fail(listener);
    }

    public static void showSplashInterstitial(Context context, final AdLoadingListener listener) {
        fail(listener);
    }

    public static void showInterstitial(AppCompatActivity activity, final AdLoadingListener listener) {
        fail(listener);
    }

    public static void showInterstitialOpen(AppCompatActivity activity, final AdLoadingListener listener) {
        fail(listener);
    }

    public static void showBackInterstitial(AppCompatActivity activity, AdLoadingListener listener) {
        fail(listener);
    }

    public static void showRewardedAd(final AppCompatActivity activity, final RewardedAdListener listener) {
        if (listener != null) listener.onRewardNull();
    }

    public static void preloadRewardedAd(final Context context) {
        // nothing to preload
    }

    public static void getBannerAd(AppCompatActivity activity, AdLoadingListener listener) {
        fail(listener);
    }

    public static void getBannerAd(AppCompatActivity activity, final ViewGroup container,
                                   final AdLoadingListener listener) {
        fail(listener);
    }

    public static void getNativeAd(AppCompatActivity activity, boolean isLarge, AdLoadingListener listener) {
        fail(listener);
    }

    public static void getNativeAd(final AppCompatActivity activity, final ViewGroup container,
                                   final boolean isLarge, final AdLoadingListener listener) {
        fail(listener);
    }

    public static void loadNativeAd(Context context, final AdLoadingListener listener) {
        fail(listener);
    }
}
