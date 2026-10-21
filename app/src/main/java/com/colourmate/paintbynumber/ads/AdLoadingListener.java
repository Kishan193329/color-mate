package com.colourmate.paintbynumber.ads;

/**
 * Listener interface for ad loading and display lifecycle events.
 * Provides callbacks for different stages of ad loading, display, and dismissal.
 *
 * <p>This interface is used across all ad types (Interstitial, Banner, Native, App Open)
 * to provide a consistent callback mechanism for ad operations.</p>
 *
 * @see GoogleAdsUtils
 * @see FacebookAdsUtils
 * @see AdXAdsUtils
 */
public interface AdLoadingListener {

    /**
     * Called when an ad has been closed/dismissed by the user.
     * This is invoked after the user closes a full-screen ad (Interstitial, App Open)
     * or when a banner/native ad is successfully loaded and displayed.
     *
     * <p>For full-screen ads, this is the signal to resume app functionality.
     * For banner/native ads, this indicates successful loading.</p>
     */
    void OnAdClosed();

    /**
     * Called when an ad fails to load.
     * This can occur due to various reasons such as:
     * <ul>
     *   <li>No ad inventory available</li>
     *   <li>Network connectivity issues</li>
     *   <li>Invalid ad unit ID</li>
     *   <li>Ad request timeout</li>
     * </ul>
     *
     * <p>Implement this method to handle ad loading failures gracefully,
     * such as trying the next ad network in mediation or continuing without ads.</p>
     */
    void OnAdFailedToLoad();

    /**
     * Called when an ad is successfully displayed to the user.
     * For full-screen ads (Interstitial, App Open), this is called when the ad
     * becomes visible on screen.
     *
     * <p>This is the appropriate time to pause game state, stop animations,
     * or perform any other actions needed while an ad is showing.</p>
     */
    void OnAdShow();

    /**
     * Called when ad loading has started.
     * This is invoked immediately when an ad request is made, before the ad loads.
     *
     * <p>Use this callback to show loading indicators or prepare the UI
     * for the upcoming ad display.</p>
     */
    void OnAdStartLoading();
}