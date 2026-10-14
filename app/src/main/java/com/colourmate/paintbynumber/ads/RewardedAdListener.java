package com.colourmate.paintbynumber.ads;

/**
 * Listener interface for rewarded ad events.
 * Provides callbacks for different states of rewarded ad playback and completion.
 *
 * <p>Rewarded ads are full-screen video ads that users watch in exchange for in-app rewards.
 * This interface allows the app to respond to user interactions and ad states.</p>
 *
 * @see com.google.android.gms.ads.rewarded.RewardedAd
 */
public interface RewardedAdListener {

    /**
     * Called when the user has successfully watched the rewarded ad and earned the reward.
     * Implement this method to grant the user their reward (coins, lives, premium content, etc.).
     *
     * <p>This is called after the user completes watching the video ad.</p>
     */
    void onRewardGranted();

    /**
     * Called when the rewarded ad is not available or failed to load.
     * Implement this method to handle cases where the ad cannot be shown.
     *
     * <p>This might happen due to:</p>
     * <ul>
     *   <li>No ad inventory available</li>
     *   <li>Network connectivity issues</li>
     *   <li>Ad unit not configured properly</li>
     * </ul>
     */
    void onRewardNull();

    /**
     * Called when the rewarded ad starts playing.
     * Implement this method to show loading indicators or pause game state.
     *
     * <p>This is called when the ad begins to play, before completion.</p>
     */
    void onRewardProgress();

    /**
     * Called when the rewarded ad is shown to the user.
     * Use this to dismiss loading dialogs.
     */
    default void onAdShow() {}

    /**
     * Called when the user closes the ad before completion (skips the video).
     * Implement this method to handle cases where the user doesn't earn the reward.
     *
     * <p>The user will not receive any reward if they skip the video before completion.</p>
     */
    void onVideoSkipped();
}