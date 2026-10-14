package com.colourmate.paintbynumber.ads;

import java.io.Serializable;

/**
 * Model class for app settings retrieved from Firebase Remote Config.
 * Contains ad unit IDs, app configuration, and feature flags.
 */
public class AppSettingModel implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== Google AdMob Ad Unit IDs ====================

    /** AdMob App Open ad unit ID */
    public String id_google_appopen = "";

    /** AdMob Splash screen ad unit ID */
    public String id_google_splash = "";

    /** AdMob Splash App Open ad unit ID */
    public String id_google_splash_open = "";

    /** AdMob Start Interstitial ad unit ID (first launch) */
    public String id_google_start_inter = "";

    /** AdMob Interstitial ad unit ID (regular) */
    public String id_google_inter = "";

    /** AdMob Banner ad unit ID */
    public String id_google_banner = "";

    /** AdMob Native ad unit ID */
    public String id_google_native = "";

    /** AdMob Rewarded ad unit ID */
    public String id_google_rewarded = "";

    /** AdMob Rewarded Interstitial ad unit ID */
    public String id_google_rewarded_inter = "";

    // ==================== Facebook Audience Network Ad Unit IDs
    // ====================

    /** Facebook App Open ad unit ID */
    public String id_fb_appopen = "";

    /** Facebook Splash screen ad unit ID */
    public String id_fb_splash = "";

    /** Facebook Splash App Open ad unit ID */
    public String id_fb_splash_open = "";

    /** Facebook Start Interstitial ad unit ID (first launch) */
    public String id_fb_start_inter = "";

    /** Facebook Interstitial ad unit ID (regular) */
    public String id_fb_inter = "";

    /** Facebook Banner ad unit ID */
    public String id_fb_banner = "";

    /** Facebook Native ad unit ID */
    public String id_fb_native = "";

    /** Facebook Rewarded Video ad unit ID */
    public String id_fb_rewarded = "";

    // ==================== AdX (Google Ad Exchange) Ad Unit IDs
    // ====================

    /** AdX App Open ad unit ID */
    public String id_adx_appopen = "";

    /** AdX Splash screen ad unit ID */
    public String id_adx_splash = "";

    /** AdX Splash App Open ad unit ID */
    public String id_adx_splash_open = "";

    /** AdX Start Interstitial ad unit ID (first launch) */
    public String id_adx_start_inter = "";

    /** AdX Interstitial ad unit ID (regular) */
    public String id_adx_inter = "";

    /** AdX Banner ad unit ID */
    public String id_adx_banner = "";

    /** AdX Native ad unit ID */
    public String id_adx_native = "";

    /** AdX Rewarded ad unit ID */
    public String id_adx_rewarded = "";

    /** AdX Rewarded Interstitial ad unit ID */
    public String id_adx_rewarded_inter = "";

    // ==================== Facebook App Configuration ====================

    /** Facebook App ID */
    public String fb_appId = "";

    /** Facebook App Token */
    public String fb_appToken = "";

    // ==================== App Information ====================

    /** Privacy policy URL */
    public String policy_url = "";

    /** Developer email address */
    public String dev_email = "";

    /** Developer name for Play Store search */
    public String dev_name = "";

    /** Promotional/warning message in JSON format */
    public String msg = "";

    /** Remote version code for update checking */
    public int version_code = 2;

    // ==================== Ad Display Configuration ====================

    /** Number of actions before showing interstitial ad */
    public int inter_count = 3;

    /** Number of actions before first interstitial ad */
    public int inter_start_count = 1;

    /** Maximum number of interstitial ads to show per session */
    public int total_inter = 50;

    /** Master switch to enable/disable all ads */
    public boolean enable_ad = true;

    /** Enable ads on back button press */
    public boolean enable_back_ad = false;

    /** Show App Open ad on splash screen */
    public boolean enable_splash_open = false;

    /** Show exit confirmation dialog with native ad */
    public boolean enable_exit_dialog = true;

    // ==================== Native Ad Configuration ====================

    /** Number of native ads to preload */
    public int native_preload_count = 2;

    /** Number of actions before showing native ad */
    public int native_counter = 1;

    /** Number of actions before first native ad */
    public int native_start_count = 1;

    /** Show large native ad on exit dialog */
    public boolean showExitLargeNative = false;

    // ==================== Ad Network Mediation ====================

    /**
     * Comma-separated list of ad networks in priority order (e.g., "ADMOB,FB,ADX")
     */
    public String network_order = "";

    // ==================== Feature Flags ====================

    /** Show introduction/onboarding screens */
    public boolean showIntro = false;

    /** Show interstitial ad after intro screens */
    public boolean showIntroInter = true;

    /** Show native ad in intro screens */
    public boolean showIntroNative = false;

    /** Show language selection screen */
    public boolean showLanguage = false;

    /** Show native ad in language selection */
    public boolean showLanguageNative = false;

    /** Show interstitial ad after language selection */
    public boolean showLanguageInter = true;

    /**
     * Controls rewarded ad type for AdMob and AdX networks only.
     * true → show Rewarded Ad (RewardedAd)
     * false → show Rewarded Interstitial Ad (RewardedInterstitialAd)
     */
    /**
     * Check if should show rewarded ad type selector (legacy).
     */
    public boolean reward_ads_inter = true;

    /**
     * Product ID/SKU for removing ads from Google Play Console.
     */
    public String remove_ads_sku = "remove_ads_testing";

    /**
     * Show confirmation dialog before rewarded ad.
     */
    public boolean show_reward_confirm = true;

    /**
     * Rewarded ad confirmation title.
     */
    public String reward_confirm_title = "Watch Video?";

    /**
     * Rewarded ad confirmation message.
     */
    public String reward_confirm_msg = "Watch a short video to get your reward.";

    /**
     * Controls rewarded ad type for AdMob and AdX networks only (Legacy).
     * true → show Rewarded Ad (RewardedAd) — default
     * false → show Rewarded Interstitial Ad (RewardedInterstitialAd)
     * Note: Facebook Audience Network always shows Rewarded Ad (Rewarded
     * Interstitial not supported).
     */

    // ==================== Constructors ====================

    /**
     * Default constructor with default values
     */
    public AppSettingModel() {
        // Default values are already initialized
    }

    // ==================== Helper Methods ====================

    /**
     * Check if ads are enabled
     * 
     * @return true if ads are enabled
     */
    public boolean isAdsEnabled(android.content.Context context) {
        if (AppPreference.getBoolean(context, Keys.KEY_IS_PREMIUM, false)) {
            return false;
        }
        return enable_ad;
    }

    /**
     * Check if any Google AdMob ad unit is configured
     * 
     * @return true if at least one AdMob ID is set
     */
    public boolean hasGoogleAds() {
        return !id_google_appopen.isEmpty()
                || !id_google_inter.isEmpty()
                || !id_google_banner.isEmpty()
                || !id_google_native.isEmpty();
    }

    /**
     * Check if any Facebook ad unit is configured
     * 
     * @return true if at least one Facebook ID is set
     */
    public boolean hasFacebookAds() {
        return !id_fb_appopen.isEmpty()
                || !id_fb_inter.isEmpty()
                || !id_fb_banner.isEmpty()
                || !id_fb_native.isEmpty();
    }

    /**
     * Check if any AdX ad unit is configured
     * 
     * @return true if at least one AdX ID is set
     */
    public boolean hasAdXAds() {
        return !id_adx_appopen.isEmpty()
                || !id_adx_inter.isEmpty()
                || !id_adx_banner.isEmpty()
                || !id_adx_native.isEmpty();
    }

    /**
     * Check if mediation is configured
     * 
     * @return true if network order is specified
     */
    public boolean isMediationEnabled() {
        return !network_order.isEmpty();
    }

    /**
     * Get array of ad networks in priority order
     * 
     * @return Array of network names, or empty array if not configured
     */
    public String[] getNetworkOrder() {
        if (network_order.isEmpty()) {
            return new String[0];
        }
        return network_order.split(",");
    }

    /**
     * Check if should show interstitial ad based on counter
     * 
     * @param counter Current action counter
     * @return true if should show interstitial
     */
    public boolean shouldShowInterstitial(int counter) {
        if (!enable_ad || inter_count == 0) {
            return false;
        }
        return counter % inter_count == 0 || counter > inter_count;
    }

    /**
     * Check if should show native ad based on counter
     * 
     * @param counter Current action counter
     * @return true if should show native ad
     */
    public boolean shouldShowNative(int counter) {
        if (!enable_ad || native_counter == 0) {
            return false;
        }
        return counter % native_counter == 0;
    }

    /**
     * Check if interstitial ad limit is reached
     * 
     * @param shownCount Number of interstitials already shown
     * @return true if limit is reached
     */
    public boolean isInterstitialLimitReached(int shownCount) {
        return shownCount >= total_inter;
    }

    // ==================== Override Methods ====================

    @Override
    public String toString() {
        return "AppSettingModel{" +
                "enable_ad=" + enable_ad +
                ", version_code=" + version_code +
                ", inter_count=" + inter_count +
                ", network_order='" + network_order + '\'' +
                ", hasGoogleAds=" + hasGoogleAds() +
                ", hasFacebookAds=" + hasFacebookAds() +
                ", hasAdXAds=" + hasAdXAds() +
                '}';
    }
}