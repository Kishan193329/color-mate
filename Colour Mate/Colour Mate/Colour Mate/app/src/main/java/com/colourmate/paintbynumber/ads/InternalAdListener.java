package com.colourmate.paintbynumber.ads;

/**
 * Listener interface for internal ad completion events.
 * Provides a simple callback when an ad operation is complete or closed.
 *
 * <p>This is a simplified listener typically used for interstitial ads
 * where you only need to know when the ad is dismissed, regardless of
 * whether it was shown successfully or failed to load.</p>
 *
 * <p>This is a functional interface and can be used with lambda expressions:</p>
 * <pre>
 * AdsManager.showInterstitial(activity, () -> {
 *     // Ad closed, continue app flow
 *     navigateToNextScreen();
 * });
 * </pre>
 *
 * @see AdLoadingListener for more detailed ad lifecycle callbacks
 */
@FunctionalInterface
public interface InternalAdListener {

    /**
     * Called when an ad operation is complete.
     * This is invoked in the following scenarios:
     * <ul>
     *   <li>User closes/dismisses the ad</li>
     *   <li>Ad fails to load</li>
     *   <li>Ad is not available</li>
     *   <li>Ads are disabled</li>
     * </ul>
     *
     * <p>In all cases, this signals that the app should continue
     * with its normal flow after the ad attempt.</p>
     */
    void onAdClosed();
}