package com.colourmate.paintbynumber.ads;

/**
 * Constants class containing SharedPreferences keys for the ad manager.
 * All keys are immutable and organized by category.
 *
 * <p>
 * The "_506" suffix is used for versioning and preventing key collisions
 * when migrating from previous versions.
 * </p>
 */
public final class Keys {

    // ==================== User Preferences ====================

    /**
     * User's premium status (boolean)
     * If true, all ads are suppressed regardless of other settings.
     */
    public static final String KEY_IS_PREMIUM = "key_is_premium";

    /**
     * Product ID for removing ads in Google Play Console.
     */
    public static final String KEY_PRODUCT_REMOVE_ADS = "key_product_remove_ads";

    /**
     * Google Play Base64-encoded RSA public key for purchase verification.
     * Found in: Play Console → Your App → Monetization setup → Licensing
     * Used for: Server-side receipt validation (not needed client-side with Billing
     * Library v5+)
     */

    // Private constructor to prevent instantiation
    private Keys() {
        throw new AssertionError("Cannot instantiate Keys class");
    }

    // ==================== Ad Network Configuration ====================

    /**
     * Mediation configuration key - determines which ad system to use (V1 or M1)
     */
    public static final String KEY_MEDIATION = "key_enc_key";

    /**
     * Comma-separated list of ad networks in priority order (e.g., "ADMOB,FB,ADX")
     */
    public static final String KEY_NETWORK_ORDER = "key_network_order";

    // ==================== Google AdMob Ad Unit IDs ====================

    /**
     * AdMob App Open ad unit ID
     */
    public static final String KEY_ID_APP_OPEN = "key_id_app_open";

    /**
     * AdMob Splash screen ad unit ID
     */
    public static final String KEY_ID_SPLASH = "key_id_splash";

    /**
     * AdMob Splash App Open ad unit ID
     */
    public static final String KEY_ID_SPLASH_APP_OPEN = "key_id_splash_app_open";

    /**
     * AdMob Start Interstitial ad unit ID (first launch)
     */
    public static final String KEY_ID_START_INTERSTITIAL = "key_id_start_interstitial";

    /**
     * AdMob Interstitial ad unit ID (regular)
     */
    public static final String KEY_ID_INTERSTITIAL = "key_id_interstitial";

    /**
     * AdMob Banner ad unit ID
     */
    public static final String KEY_ID_BANNER = "key_id_banner";

    /**
     * AdMob Native ad unit ID
     */
    public static final String KEY_ID_NATIVE = "key_id_native";

    /**
     * AdMob Rewarded ad unit ID
     */
    public static final String KEY_ID_REWARDED = "key_id_rewarded";

    /**
     * AdMob Rewarded Interstitial ad unit ID
     */
    public static final String KEY_ID_REWARDED_INTER = "key_id_rewarded_inter";

    // ==================== Facebook Audience Network Ad Unit IDs
    // ====================

    /**
     * Facebook App ID for SDK initialization
     */
    public static final String KEY_FACEBOOK_APP_ID = "key_facebook_app_id";

    /**
     * Facebook App Token for SDK initialization
     */
    public static final String KEY_FACEBOOK_APP_TOKEN = "key_facebook_app_token";

    /**
     * Facebook App Open ad unit ID
     */
    public static final String KEY_FB_ID_APP_OPEN = "key_fb_id_app_open";

    /**
     * Facebook Splash screen ad unit ID
     */
    public static final String KEY_FB_ID_SPLASH = "key_fb_id_splash";

    /**
     * Facebook Splash App Open ad unit ID
     */
    public static final String KEY_FB_ID_SPLASH_APP_OPEN = "key_fb_id_splash_app_open";

    /**
     * Facebook Start Interstitial ad unit ID (first launch)
     */
    public static final String KEY_FB_ID_START_INTERSTITIAL = "key_fb_id_start_interstitial";

    /**
     * Facebook Interstitial ad unit ID (regular)
     */
    public static final String KEY_FB_ID_INTERSTITIAL = "key_fb_id_interstitial";

    /**
     * Facebook Banner ad unit ID
     */
    public static final String KEY_FB_ID_BANNER = "key_fb_id_banner";

    /**
     * Facebook Native ad unit ID
     */
    public static final String KEY_FB_ID_NATIVE = "key_fb_id_native";

    /**
     * Facebook Rewarded Video ad unit ID
     */
    public static final String KEY_FB_ID_REWARDED = "key_fb_id_rewarded";

    // ==================== AdX (Google Ad Exchange) Ad Unit IDs
    // ====================

    /**
     * AdX App Open ad unit ID
     */
    public static final String KEY_ADX_ID_APP_OPEN = "key_adx_id_app_open";

    /**
     * AdX Splash screen ad unit ID
     */
    public static final String KEY_ADX_ID_SPLASH = "key_adx_id_splash";

    /**
     * AdX Splash App Open ad unit ID
     */
    public static final String KEY_ADX_ID_SPLASH_APP_OPEN = "key_adx_id_splash_app_open";

    /**
     * AdX Start Interstitial ad unit ID (first launch)
     */
    public static final String KEY_ADX_ID_START_INTERSTITIAL = "key_adx_id_start_interstitial";

    /**
     * AdX Interstitial ad unit ID (regular)
     */
    public static final String KEY_ADX_ID_INTERSTITIAL = "key_adx_id_interstitial";

    /**
     * AdX Banner ad unit ID
     */
    public static final String KEY_ADX_ID_BANNER = "key_adx_id_banner";

    /**
     * AdX Native ad unit ID
     */
    public static final String KEY_ADX_ID_NATIVE = "key_adx_id_native";

    /**
     * AdX Rewarded ad unit ID
     */
    public static final String KEY_ADX_ID_REWARDED = "key_adx_id_rewarded";

    /**
     * AdX Rewarded Interstitial ad unit ID
     */
    public static final String KEY_ADX_ID_REWARDED_INTER = "key_adx_id_rewarded_inter";

    // ==================== Ad Display Configuration ====================

    /**
     * Master switch to enable/disable all ads (boolean)
     */
    public static final String PLAY_VIDEO = "key_playVideo";

    /**
     * Enable ads on back button press (boolean)
     */
    public static final String ALLOW_BACK = "key_allow_back";

    /**
     * Show App Open ad on splash screen (boolean)
     */
    public static final String IS_SPLASH_OPEN = "key_is_splash_open";

    /**
     * Number of actions before showing interstitial ad (int)
     */
    public static final String KEY_SHOWICON = "key_id_showicon";

    /**
     * Number of actions before first interstitial ad (int)
     */
    public static final String KEY_START_COUNT = "key_id_start_count";

    /**
     * Maximum number of interstitial ads to show per session (int)
     */
    public static final String KEY_ALLOWED_PLAY = "key_allowed_play";

    // ==================== Native Ad Configuration ====================

    /**
     * Number of native ads to preload (int)
     */
    public static final String KEY_PRELOAD_LIMIT = "key_preload_limit";

    /**
     * Number of actions before showing native ad (int)
     */
    public static final String KEY_NATIVE_COUNTER = "key_native_counter";

    /**
     * Number of actions before first native ad (int)
     */
    public static final String KEY_NATIVE_START_COUNT = "key_native_start_count";

    // ==================== Dialog Configuration ====================

    /**
     * Show exit confirmation dialog with native ad (boolean)
     */
    public static final String KEY_IS_EXIT_DIALOG = "key_is_exit_dialog";

    /**
     * Show large native ad on exit dialog (boolean)
     */
    public static final String KEY_IS_EXIT_LARGE_NATIVE = "key_is_exit_large_native";

    // ==================== Feature Flags ====================

    /**
     * Show introduction/onboarding screens (boolean)
     */
    public static final String KEY_SHOW_INTRO_SCREENS = "key_show_intro_screen";

    /**
     * Show interstitial ad after intro screens (boolean)
     */
    public static final String KEY_SHOW_INTER_INTRO = "key_show_inter_intro";

    /**
     * Show native ad in intro screens (boolean)
     */
    public static final String KEY_SHOW_INTRO_NATIVE = "key_show_intro_native";

    /**
     * Show language selection screen (boolean)
     */
    public static final String KEY_SHOW_LANGUAGE = "key_show_language";

    /**
     * Show native ad in language selection (boolean)
     */
    public static final String KEY_SHOW_LANGUAGE_NATIVE = "key_show_language_native";

    /**
     * Show interstitial ad after language selection (boolean)
     */
    public static final String KEY_SHOW_LANGUAGE_INTER_INTRO = "key_show_language_inter_intro";

    /**
     * Rewarded ad type selector for AdMob and AdX (boolean).
     * true → show Rewarded Ad (RewardedAd)
     * false → show Rewarded Interstitial Ad (RewardedInterstitialAd)
     * Facebook always uses RewardedAd regardless of this flag.
     */
    public static final String KEY_REWARD_ADS_INTER = "key_reward_ads_inter";

    /**
     * Show confirmation dialog before rewarded ad (boolean)
     */
    public static final String KEY_SHOW_REWARD_CONFIRM = "key_show_reward_confirm";

    /**
     * Rewarded ad confirmation title (string)
     */
    public static final String KEY_REWARD_CONFIRM_TITLE = "key_reward_confirm_title";

    /**
     * Rewarded ad confirmation message (string)
     */
    public static final String KEY_REWARD_CONFIRM_MSG = "key_reward_confirm_msg";

    // ==================== App Information ====================

    /**
     * Privacy policy URL (string)
     */
    public static final String KEY_POLICY = "key_policy_url";

    /**
     * Developer email address (string)
     */
    public static final String KEY_DEV_EMAIL = "key_dev_email";

    /**
     * Developer name for Play Store search (string)
     */
    public static final String KEY_DEV_NAME = "key_dev_name";

    /**
     * Promotional/warning message in JSON format (string)
     */
    public static final String KEY_MSG = "key_cross_msg";

    /**
     * Remote version code for update checking (int)
     */
    public static final String KEY_VERSION_CODE = "key_app_version_code";
}