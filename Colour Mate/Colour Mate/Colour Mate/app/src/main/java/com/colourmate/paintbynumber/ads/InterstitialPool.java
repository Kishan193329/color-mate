package com.colourmate.paintbynumber.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/**
 * Keeps one interstitial warm so showing it is instant.
 *
 * <p>The app used to request an interstitial at the moment it wanted to show one, behind a
 * "Loading Ads…" sheet with a 5 second timeout. Three things were wrong with that:
 *
 * <ul>
 *   <li><b>Slow.</b> Every single interstitial cost a full network round trip while the user sat
 *       and watched a spinner.</li>
 *   <li><b>Lost impressions.</b> A load slower than the timeout was abandoned — the request was
 *       paid for and the impression thrown away.</li>
 *   <li><b>Worse: an ad could appear out of nowhere.</b> On timeout the app continued, but the load
 *       kept running and still called {@code show()} when it finished — so an interstitial could
 *       land on top of the drawing screen seconds after the user had moved on. That is a bad
 *       experience and exactly the kind of unexpected interstitial AdMob's policies forbid.</li>
 * </ul>
 *
 * <p>Now the ad is fetched ahead of time and simply shown when needed, and a late arrival is
 * <em>kept for next time</em> instead of being fired at the user or thrown away.
 *
 * <p>Everything here runs on the main thread — the Google Mobile Ads SDK requires load and show to
 * be called there — but the work itself is asynchronous, so nothing blocks the UI.
 */
public final class InterstitialPool {

    private static final String TAG = "InterstitialPool";

    /**
     * Google expires interstitials about an hour after they are fetched; a stale ad shows nothing
     * and earns nothing. Retire ours earlier so it is never a close call.
     */
    private static final long AD_TTL_MS = 50 * 60 * 1000L;

    /** Backoff for failed loads: no-fill is common and hammering it wastes battery and quota. */
    private static final long[] RETRY_DELAYS_MS = {10_000L, 30_000L, 120_000L};
    /**
     * How many times a failed load is retried before the pool gives up until something asks again.
     *
     * <p>The delay used to be clamped at two minutes but the retry itself never stopped, so a
     * session with no fill kept requesting for as long as the app lived — including while it sat
     * in the background where no impression was ever possible. Requests that cannot become
     * impressions drag the match rate down and are exactly the "excessive ad requests" AdMob asks
     * apps not to make. After this many tries the pool waits for a real trigger instead: the app
     * coming back to the foreground, an ad being shown, or the config arriving all call preload().
     */
    private static final int MAX_RETRIES = 4;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static InterstitialAd cachedAd;
    private static long cachedAtElapsed;         // SystemClock: immune to wall-clock changes
    private static boolean loading;
    private static int consecutiveFailures;
    private static String cachedForUnitId;

    private InterstitialPool() {
    }

    /** True when an ad is cached and still inside its lifetime. */
    @MainThread
    public static boolean isReady() {
        if (cachedAd == null) return false;
        if (SystemClock.elapsedRealtime() - cachedAtElapsed > AD_TTL_MS) {
            Log.d(TAG, "Cached interstitial expired — discarding");
            cachedAd = null;                     // never show an expired ad: it cannot pay
            return false;
        }
        return true;
    }

    /**
     * The app came back to the foreground. Clears the give-up state from a run of failed loads and
     * warms the pool again, so a user who was offline earlier is not left without an ad for the
     * rest of the session — without the pool having polled in the background to get there.
     */
    @MainThread
    public static void onAppForegrounded(Context context) {
        if (context == null || !AdController.shouldShowAds(context)) return;
        consecutiveFailures = 0;
        if (!isReady() && !loading && cachedForUnitId != null) {
            preload(context, cachedForUnitId);
        }
    }

    /** True while the process is actually in front of the user. */
    private static boolean isAppInForeground() {
        try {
            return androidx.lifecycle.ProcessLifecycleOwner.get().getLifecycle().getCurrentState()
                    .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);
        } catch (Throwable t) {
            return true;      // if we cannot tell, behave as before
        }
    }

    /**
     * Fetches an ad if there is not already one ready or in flight. Safe to call often — it is the
     * guard against duplicate and overlapping requests.
     */
    @MainThread
    public static void preload(final Context context, final String adUnitId) {
        if (context == null || adUnitId == null || adUnitId.isEmpty()) return;
        if (loading) return;                     // one request at a time, never two
        if (isReady() && adUnitId.equals(cachedForUnitId)) return;

        loading = true;
        final long startedAt = AdsAnalytics.startLoadTimer();
        AdsAnalytics.logAdRequest(context, AdsAnalytics.NETWORK_ADMOB,
                AdsAnalytics.TYPE_INTERSTITIAL, adUnitId);

        InterstitialAd.load(
                context.getApplicationContext(),
                adUnitId,
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        loading = false;
                        consecutiveFailures = 0;
                        cachedAd = ad;
                        cachedAtElapsed = SystemClock.elapsedRealtime();
                        cachedForUnitId = adUnitId;
                        AdsAnalytics.logAdLoaded(context, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_INTERSTITIAL, adUnitId, startedAt);
                        Log.d(TAG, "Interstitial ready");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        loading = false;
                        cachedAd = null;
                        AdsAnalytics.logAdLoadFailed(context, AdsAnalytics.NETWORK_ADMOB,
                                AdsAnalytics.TYPE_INTERSTITIAL, adUnitId,
                                error.getCode(), error.getMessage());

                        // Back off rather than retry immediately — a no-fill now is usually still a
                        // no-fill a second later, and repeated requests count against the app.
                        consecutiveFailures++;
                        if (consecutiveFailures > MAX_RETRIES) {
                            Log.d(TAG, "Interstitial failed " + consecutiveFailures
                                    + " times; waiting for a real trigger instead of retrying");
                            return;
                        }
                        if (!isAppInForeground()) {
                            // Nobody can see an ad right now, so a request cannot become an
                            // impression. onResume-driven preloads pick this up again.
                            Log.d(TAG, "Interstitial failed while backgrounded; not retrying");
                            return;
                        }
                        int attempt = Math.min(consecutiveFailures - 1, RETRY_DELAYS_MS.length - 1);
                        long delay = RETRY_DELAYS_MS[attempt];
                        Log.d(TAG, "Interstitial failed (" + error.getCode() + "); retry "
                                + consecutiveFailures + "/" + MAX_RETRIES + " in " + delay + "ms");
                        MAIN.postDelayed(() -> preload(context, adUnitId), delay);
                    }
                });
    }

    /**
     * Shows the cached ad if there is one.
     *
     * @return true if an ad was shown — the caller must then wait for {@code onDismiss} rather than
     *         continuing immediately.
     */
    @MainThread
    public static boolean show(final Activity activity, final String adUnitId,
                               @Nullable final Runnable onDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        if (!isReady()) {
            preload(activity, adUnitId);         // nothing to show — get one ready for next time
            return false;
        }

        final InterstitialAd ad = cachedAd;
        cachedAd = null;                          // consumed: an ad object may only be shown once

        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                AdsAnalytics.logAdImpression(activity, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_INTERSTITIAL, adUnitId);
            }

            @Override
            public void onAdClicked() {
                AdsAnalytics.logAdClick(activity, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_INTERSTITIAL, adUnitId);
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                // Shares the app-open cool-down clock, so an app-open ad cannot pile straight on
                // top of an interstitial the user has just closed.
                AdsManager.markFullScreenAdEnded();
                // Refill straight away: the next opportunity is usually seconds away, and this is
                // what makes the following interstitial instant instead of another spinner.
                preload(activity.getApplicationContext(), adUnitId);
                if (onDismiss != null) onDismiss.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                AdsAnalytics.logAdShowFailed(activity, AdsAnalytics.NETWORK_ADMOB,
                        AdsAnalytics.TYPE_INTERSTITIAL, adUnitId,
                        adError.getCode(), adError.getMessage());
                preload(activity.getApplicationContext(), adUnitId);
                if (onDismiss != null) onDismiss.run();   // never strand the user
            }
        });

        ad.show(activity);
        return true;
    }

    /** Drops the cached ad, e.g. when the user buys premium and ads must stop immediately. */
    @MainThread
    public static void clear() {
        cachedAd = null;
        consecutiveFailures = 0;
    }
}
