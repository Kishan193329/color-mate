package com.colourmate.paintbynumber.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.bumptech.glide.Priority;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.ads.AppPreference;
import com.colourmate.paintbynumber.ads.IAPManager;
import com.colourmate.paintbynumber.ads.Keys;
import com.colourmate.paintbynumber.ads.AdController;
import com.colourmate.paintbynumber.ads.AdConsent;
import com.colourmate.paintbynumber.ads.AdsManager;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.databinding.ActivitySplashBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallState;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.ActivityResult;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;


public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int UPDATE_REQUEST_CODE = 100;
    private static final long SPLASH_SAFETY_TIMEOUT_MS = 8000L;

    /**
     * How long the splash will wait for ads / IAP / Firebase before going ahead without them.
     *
     * <p>Measured 2026-07-30: that chain took ~8s on a warm device, and the splash sat there for
     * all of it — 15.5s from tap to a usable Home screen. Nothing on Home needs the ad SDK to be
     * ready; it keeps initialising in the background and the first ad simply arrives a moment
     * later. Waiting was costing the user far more than a preloaded ad is worth.
     */
    /**
     * How long the splash waits for initialisation before going on without it.
     *
     * <p>Two values, because there are two different things being waited for. With ads off (or a
     * premium user) nothing is coming, so 2.5s is plenty. With ads on, the launch ad cannot even be
     * requested until the Firebase config lands with the unit ids — so a 2.5s deadline consistently
     * beat the ad to the finish and Home won the race. The ad then had nowhere to go but on top of
     * Home, which is the behaviour being fixed.
     *
     * <p>This is a deliberate trade: an ad-showing launch is slower than one without.
     */
    private static final long INIT_DEADLINE_MS = 2500L;
    private static final long INIT_DEADLINE_WITH_ADS_MS = 7000L;

    /** How long the splash will hold for a launch ad, and how often it re-checks. */
    private static final long MAX_AD_WAIT_MS = 12_000L;
    /** How many times navigation may be postponed because an ad is on screen (x AD_WAIT_POLL_MS). */
    private static final int MAX_NAV_DEFERRALS = 60;      // 15 s
    private int adDeferrals = 0;
    private static final long AD_WAIT_POLL_MS = 250L;
    private long waitedForAdMs = 0L;

    /** Set when the launch ad has been shown and dismissed, or there was none to show. */
    private boolean launchAdFinished = false;

    /** Cold launches between paywall showings — 1 means the 1st, 4th, 7th launch, and so on. */
    private static final int PAYWALL_EVERY_N_LAUNCHES = 3;

    private static final String KEY_LAUNCH_COUNT = "launch_count";
    private static final int HIGH_PRIORITY_UPDATE = 4;

    /**
     * How long the brand stays up before the splash is willing to leave. The GIF this replaced
     * held the screen for 9.5s whether or not anything still needed doing; the intro reads in
     * well under two.
     */
    private static final long INTRO_HOLD_MS = 1750L;


    private ActivitySplashBinding binding;

    // App Update
    private AppUpdateManager appUpdateManager;
    private InstallStateUpdatedListener installStateUpdatedListener;

    // Navigation Control
    private boolean isNavigated = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Both must be true before navigating
    private boolean introFinished = false;
    private boolean initCompleted = false;


    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupStatusBar();
        loadMark();

        // The WebView is built AFTER the native splash has drawn, not during onCreate.
        // Measured 2026-08-13: constructing it inline pushed the first frame out by roughly a
        // second — the app was still showing the system icon at 1.6s. Posting it costs the scene
        // one frame and costs the user nothing.
        if (USE_WEB_SPLASH) {
            // Two splashes were playing back to back: the native one animated in, then the
            // animated page faded over the top of it. The native one is now silent unless the
            // page fails to arrive — see fallBackToNativeSplash().
            binding.getRoot().post(this::startWebSplash);
        } else {
            showLoadingIndicators();
            playIntro();
        }

        // Consent first — AdMob serves EEA/UK users nothing until a form has been shown, and
        // requesting an ad before the answer is in wastes the request. Outside those regions the
        // callback returns immediately, so nobody else waits. It always calls back, in every
        // outcome, so the launch ad can never be stranded.
        AdConsent.gather(this, canRequestAds -> {
            if (isFinishing() || isDestroyed()) return;
            if (!canRequestAds) {
                // The user refused. There will be no ad, so do not make navigation wait for one.
                launchAdFinished = true;
                tryNavigate();
                return;
            }
            // The launch ad uses the unit ids cached from the last run, so it is ready while the
            // splash is still on screen. Navigation waits for it.
            AdsManager.startLaunchAd(this, () -> {
                launchAdFinished = true;
                tryNavigate();
            });
        });

        // Start initialization in parallel
        mainHandler.postDelayed(() -> {
            initAppUpdateManager();
            checkForAppUpdate();
        }, 300);

        // Don't let a slow ad/IAP handshake hold the whole app hostage — but never cut in front of
        // the launch ad. If one is on screen or ready to show, the splash waits for it, so the ad
        // is always seen BEFORE Home rather than landing on top of it.
        mainHandler.postDelayed(this::onInitDeadline, INIT_DEADLINE_MS);

        // Safety timeout
        mainHandler.postDelayed(() -> {
            if (!isNavigated) {
                if (AdsManager.isFullScreenAdPending()) {
                    // An ad is up. Let it finish — its dismissal drives navigation.
                    Log.d(TAG, "Safety timeout reached but the launch ad is on screen — waiting");
                    return;
                }
                Log.w(TAG, "Splash safety timeout reached – forcing navigation");
                launchAdFinished = true;
                navigateAfterSplash();
            }
        }, SPLASH_SAFETY_TIMEOUT_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPendingUpdate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == UPDATE_REQUEST_CODE) handleUpdateResult(res);
    }

    // ==================== INTRO ANIMATION ====================

    /**
     * Springs the brand in: the mark first, then the wordmark, then the tagline, then the loading
     * row — each overlapping the last so it reads as one gesture rather than four.
     *
     * <p>When it lands, {@link #introFinished} opens the navigation gate. The splash never waits
     * on the animation beyond that: if initialisation is slower, the paint keeps drifting until it
     * is done, and if initialisation is faster, we still hold the brand for a beat so the app does
     * not flash past.
     */
    /**
     * Puts the app icon on the splash.
     *
     * <p>ic_icon.png is 560x560 in a density-less {@code drawable/} folder, which Android treats as
     * mdpi — via {@code android:src} it would be upscaled to the device density and decoded on the
     * main thread while the first frame is being built (~11 MB on a 480dpi phone). Glide decodes it
     * off-thread at the size actually displayed.
     */
    private void loadMark() {
        if (binding == null) return;
        int size = Math.round(116 * getResources().getDisplayMetrics().density);
        com.bumptech.glide.Glide.with(this)
                .load(R.drawable.ic_icon)
                .override(size, size)
                .into(binding.splashIcon);
    }

    // ─── Animated splash (WebView) ───────────────────────────────────────────

    /**
     * Set false to ship the plain native splash. Kept as one switch because this is on the
     * cold-start path — the screen whose launch time was measured down from 15.5s to ~7s
     * (CLAUDE.md §8.5aj) — and it is the only piece that can put time back.
     */
    private static final boolean USE_WEB_SPLASH = true;

    /**
     * If the page has not painted by now, stop waiting for it and keep the native splash.
     * Measured on the test phone: WebView start-up plus this page takes roughly 0.9-1.2s.
     */
    private static final long SCENE_GIVE_UP_MS = 2500L;

    /** Hard ceiling on the animation, in case a slow device drags it out. */
    private static final long SCENE_MAX_MS = 8000L;

    private boolean sceneShown = false;

    /**
     * Brings up the owner's animated splash over the native one.
     *
     * <p>Additive by design: the native splash paints first and stays underneath, and this only
     * fades in once the page reports it has drawn a frame. A device with no WebView, or one that
     * is simply slow, therefore falls back to a finished-looking splash rather than a blank frame.
     *
     * <p>The page drives the hand-off: it calls {@code Android.onSplashDone()} when its animation
     * finishes, which is what opens the navigation gate. That interface name and method are the
     * page's, not ours — do not rename them here without editing splash.html too.
     */
    private void startWebSplash() {
        if (!USE_WEB_SPLASH || binding == null || binding.splash3d == null) return;

        final android.webkit.WebView web = binding.splash3d;
        try {
            web.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            web.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);

            android.webkit.WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setAllowFileAccess(false);   // the page only needs its own bundled assets
            settings.setDomStorageEnabled(false);
            settings.setBlockNetworkLoads(true);  // nothing here is remote; make that a guarantee

            web.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void onSplashReady() {
                    runOnUiThread(() -> revealWebSplash());
                }

                @android.webkit.JavascriptInterface
                public void onSplashDone() {
                    runOnUiThread(() -> {
                        introFinished = true;
                        // tryNavigate(), NOT navigateAfterSplash(): the launch ad may be on screen
                        // or about to be, and only tryNavigate() waits for it. Calling the latter
                        // here opened the next screen behind a live app-open ad.
                        tryNavigate();
                    });
                }
            }, "Android");

            web.loadUrl("file:///android_asset/splash3d/splash.html");

            // A page that never reports back must not hold the screen — and because the native
            // splash is no longer playing on its own, this is also what has to bring it out.
            web.postDelayed(this::fallBackToNativeSplash, SCENE_GIVE_UP_MS);

            web.postDelayed(() -> {
                if (!introFinished) {
                    introFinished = true;
                    tryNavigate();          // same gate — never step over the launch ad
                }
            }, SCENE_MAX_MS);
        } catch (Throwable t) {
            // Some devices have no WebView provider at all; the splash must still work.
            Log.w(TAG, "Animated splash unavailable, falling back to the native one", t);
            fallBackToNativeSplash();
        }
    }

    /**
     * Runs the native splash after all, when the animated page did not turn up in time.
     *
     * <p>This is the only thing keeping a WebView-less or very slow device from staring at a bare
     * gradient, so it must stay even though it never runs on a healthy launch.
     */
    private void fallBackToNativeSplash() {
        if (sceneShown || isFinishing() || isDestroyed() || binding == null) return;
        sceneShown = true;                       // stops a late page from fading in over this

        if (binding.splash3d != null) binding.splash3d.setVisibility(android.view.View.GONE);
        showLoadingIndicators();
        playIntro();
    }

    private void revealWebSplash() {
        if (sceneShown || binding == null || binding.splash3d == null) return;
        sceneShown = true;

        binding.splash3d.setVisibility(android.view.View.VISIBLE);
        binding.splash3d.animate().alpha(1f).setDuration(260).start();

        // The page is a complete splash of its own — mark, wordmark and all — so the native one
        // underneath would only show through as a double image.
        if (binding.splashPaint != null) binding.splashPaint.animate().alpha(0f).setDuration(260).start();
        if (binding.splashMark != null) binding.splashMark.animate().alpha(0f).setDuration(260).start();
        if (binding.splashWordmark != null) binding.splashWordmark.animate().alpha(0f).setDuration(260).start();
        if (binding.splashTagline != null) binding.splashTagline.animate().alpha(0f).setDuration(260).start();
        if (binding.splashLoading != null) binding.splashLoading.animate().alpha(0f).setDuration(260).start();
    }

    private void playIntro() {
        if (binding == null) return;

        binding.splashMark.setScaleX(0.68f);
        binding.splashMark.setScaleY(0.68f);
        binding.splashMark.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(80).setDuration(640)
                .setInterpolator(new OvershootInterpolator(2.1f))
                .withEndAction(this::breatheMark)      // then keep it quietly alive
                .start();

        binding.splashWordmark.setTranslationY(dp(20));
        binding.splashWordmark.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(300).setDuration(520)
                .setInterpolator(new OvershootInterpolator(1.4f))
                .start();

        binding.splashTagline.animate()
                .alpha(1f)
                .setStartDelay(520).setDuration(420)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        binding.splashLoading.animate()
                .alpha(1f)
                .setStartDelay(680).setDuration(360)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        startTrackSweep();

        mainHandler.postDelayed(() -> {
            introFinished = true;
            tryNavigate();
        }, INTRO_HOLD_MS);
    }

    /**
     * A slow swell on the brand mark once it has landed.
     *
     * <p>The splash can sit for a couple of seconds while the launch ad loads. Without this the
     * whole screen freezes the moment the intro finishes, which reads as the app having hung; the
     * drifting paint alone is too subtle to carry it. Two seconds up, two seconds down, 3% — felt
     * rather than noticed.
     */
    private void breatheMark() {
        if (binding == null || isNavigated || isFinishing()) return;

        binding.splashMark.animate()
                .scaleX(1.03f).scaleY(1.03f)
                .setStartDelay(0).setDuration(2000)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (binding == null || isNavigated || isFinishing()) return;
                    binding.splashMark.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(2000)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(this::breatheMark)
                            .start();
                })
                .start();
    }

    /** The loading bar: a highlight sweeping the track, restarted until the splash is done. */
    private void startTrackSweep() {
        if (binding == null) return;
        final View bar = binding.progressBar;
        final View track = binding.splashTrack;

        track.post(() -> {
            if (binding == null || isNavigated) return;
            float distance = track.getWidth() - bar.getWidth();
            if (distance <= 0) return;

            bar.setTranslationX(-bar.getWidth());
            bar.animate()
                    .translationX(track.getWidth())
                    .setDuration(1250)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .withEndAction(() -> {
                        if (!isNavigated) startTrackSweep();
                    })
                    .start();
        });
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }


    // ==================== INITIALIZATION ====================

    private void setupStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // The splash ground is now light paper, so the system bars need DARK icons. They were
        // white for the old violet gradient, which would leave the clock invisible.
        androidx.core.view.WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }

    private void initAppUpdateManager() {
        appUpdateManager = AppUpdateManagerFactory.create(this);
        setupInstallStateListener();
    }

    // ==================== APP UPDATE (same as before) ====================

    private void setupInstallStateListener() {
        installStateUpdatedListener = this::onInstallStateUpdated;
    }

    private void onInstallStateUpdated(InstallState state) {
        switch (state.installStatus()) {
            case InstallStatus.DOWNLOADED:
                showUpdateDownloadedSnackbar();
                break;
            case InstallStatus.INSTALLED:
                unregisterUpdateListener();
                break;
            case InstallStatus.FAILED:
                Log.e(TAG, "Update installation failed");
                initializeAds();
                break;
            case InstallStatus.DOWNLOADING:
                int progress = (int) ((state.bytesDownloaded() * 100)
                        / state.totalBytesToDownload());
                Log.d(TAG, "Downloading update: " + progress + "%");
                break;
        }
    }

    private void checkForAppUpdate() {
        if (appUpdateManager == null) {
            initializeAds();
            return;
        }

        appUpdateManager.getAppUpdateInfo()
                .addOnSuccessListener(this::handleUpdateInfo)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Update check failed: " + e.getMessage());
                    initializeAds();
                });
    }

    private void handleUpdateInfo(AppUpdateInfo info) {
        switch (info.updateAvailability()) {
            case UpdateAvailability.UPDATE_AVAILABLE:
                handleAvailableUpdate(info);
                break;
            case UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS:
                resumeUpdate(info);
                break;
            default:
                initializeAds();
                break;
        }
    }

    private void handleAvailableUpdate(AppUpdateInfo info) {
        if (info.updatePriority() >= HIGH_PRIORITY_UPDATE) {
            startImmediateUpdate(info);
        } else if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            startFlexibleUpdate(info);
        } else if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            startImmediateUpdate(info);
        } else {
            initializeAds();
        }
    }

    private void startImmediateUpdate(AppUpdateInfo info) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                    info, AppUpdateType.IMMEDIATE, this, UPDATE_REQUEST_CODE);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Immediate update failed: " + e.getMessage());
            initializeAds();
        }
    }

    private void startFlexibleUpdate(AppUpdateInfo info) {
        try {
            registerUpdateListener();
            appUpdateManager.startUpdateFlowForResult(
                    info, AppUpdateType.FLEXIBLE, this, UPDATE_REQUEST_CODE);
            initializeAds();
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Flexible update failed: " + e.getMessage());
            initializeAds();
        }
    }

    private void resumeUpdate(AppUpdateInfo info) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                    info, AppUpdateType.IMMEDIATE, this, UPDATE_REQUEST_CODE);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Resume update failed: " + e.getMessage());
            initializeAds();
        }
    }

    private void checkPendingUpdate() {
        if (appUpdateManager == null) return;
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(info -> {
            if (info.installStatus() == InstallStatus.DOWNLOADED)
                showUpdateDownloadedSnackbar();
            if (info.updateAvailability()
                    == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
                resumeUpdate(info);
        });
    }

    private void handleUpdateResult(int resultCode) {
        switch (resultCode) {
            case RESULT_OK:
                Log.d(TAG, "Update accepted");
                break;
            case RESULT_CANCELED:
                Log.d(TAG, "Update cancelled");
                initializeAds();
                break;
            case ActivityResult.RESULT_IN_APP_UPDATE_FAILED:
                Log.e(TAG, "Update failed");
                initializeAds();
                break;
            default:
                initializeAds();
                break;
        }
    }

    private void showUpdateDownloadedSnackbar() {
        View root = binding.getRoot();
        if (root == null) return;
        Snackbar.make(root, "Update downloaded. Restart to apply.",
                        Snackbar.LENGTH_INDEFINITE)
                .setAction("RESTART", v -> {
                    if (appUpdateManager != null) appUpdateManager.completeUpdate();
                })
                .setActionTextColor(getResources().getColor(R.color.hint_text_color))
                .show();
    }

    private void registerUpdateListener() {
        if (appUpdateManager != null && installStateUpdatedListener != null)
            appUpdateManager.registerListener(installStateUpdatedListener);
    }

    private void unregisterUpdateListener() {
        if (appUpdateManager != null && installStateUpdatedListener != null)
            appUpdateManager.unregisterListener(installStateUpdatedListener);
    }

    // ==================== ADS INITIALIZATION ====================

    private void initializeAds() {
        if (isNavigated) return;
        updateLoadingText(getString(R.string.splash_preparing));
        checkPurchaseStatusThenInit();
    }

    private void checkPurchaseStatusThenInit() {
        final boolean[] done = {false};

        mainHandler.postDelayed(() -> {
            if (!done[0]) {
                done[0] = true;
                Log.w(TAG, "IAP check timed out");
                proceedToAds();
            }
        }, 4000);

        IAPManager iap = IAPManager.getInstance(this);
        iap.addListener(new IAPManager.PurchaseListener() {
            @Override
            public void onPremiumStatusChecked(boolean isPremium) {
                if (done[0]) return;
                done[0] = true;
                iap.removeListener(this);
                Log.d(TAG, "IAP premium → " + isPremium);
                proceedToAds();
            }

            @Override public void onPurchaseSuccess() { }
            @Override public void onPurchaseFailed(String error) {
                if (done[0]) return;
                done[0] = true;
                iap.removeListener(this);
                Log.w(TAG, "IAP check failed: " + error);
                proceedToAds();
            }
            @Override public void onProductDetailsLoaded(
                    IAPManager.PlanType t,
                    com.android.billingclient.api.ProductDetails d) { }
        });
        iap.startConnection();
    }

    private void proceedToAds() {
        if (com.colourmate.paintbynumber.ads.AppPreference.getBoolean(
                this, com.colourmate.paintbynumber.ads.Keys.KEY_IS_PREMIUM, false)) {
            onInitCompleted();
            return;
        }
        updateLoadingText(getString(R.string.splash_almost_ready));
        MediationManager.launchApplication(this, this::onInitCompleted);
    }

    private void onInitCompleted() {
        initCompleted = true;
        tryNavigate();
    }

    /**
     * Navigate only when BOTH conditions are met:
     * 1. the brand intro has been on screen long enough to be seen
     * 2. Initialization (ads/IAP) completed
     */
    private void tryNavigate() {
        if (!introFinished || !initCompleted || !launchAdFinished) {
            return;
        }

        // The launch ad owns the screen until it is dismissed. openHomeScreen() -> onCompleted()
        // fires when it closes, which brings us back here.
        if (waitedForAdMs < MAX_AD_WAIT_MS && AdsManager.isFullScreenAdPending()) {
            waitedForAdMs += AD_WAIT_POLL_MS;
            mainHandler.postDelayed(this::tryNavigate, AD_WAIT_POLL_MS);
            return;
        }

        navigateAfterSplash();
    }

    /**
     * Initialisation took longer than {@link #INIT_DEADLINE_MS}. Proceed — unless the launch ad is
     * in play, in which case waiting is exactly what we want.
     */
    private void onInitDeadline() {
        if (initCompleted || isNavigated) {
            return;
        }
        Log.w(TAG, "Init still running past the launch deadline — continuing without it");
        initCompleted = true;
        tryNavigate();
    }

    // ==================== UI HELPERS ====================

    private void showLoadingIndicators() {
        binding.splashLoading.setVisibility(View.VISIBLE);
    }

    private void hideLoadingIndicators() {
        binding.splashLoading.setVisibility(View.GONE);
    }

    private void updateLoadingText(String text) {
        if (binding != null && binding.ads != null) {
            binding.ads.setText(text);
        }
    }

    // ==================== NAVIGATION ====================

    /**
     * After splash: premium users go to Main; others open Premium first, then Main when Premium closes.
     */
    private void navigateAfterSplash() {
        if (isNavigated) return;
        // Last line of defence, so a future call site cannot reintroduce the same bug: never
        // leave the splash while a full-screen ad is up. Bounded, so a stuck ad state can still
        // not trap the user on the splash for ever.
        if (adDeferrals < MAX_NAV_DEFERRALS && AdsManager.isFullScreenAdPending()) {
            adDeferrals++;
            mainHandler.postDelayed(this::navigateAfterSplash, AD_WAIT_POLL_MS);
            return;
        }
        isNavigated = true;
        hideLoadingIndicators();

        // First launch: the 18+ / mature-content consent gate comes before anything else.
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("age_consent_v1", false)) {
            startActivity(new Intent(this, ConsentActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
            return;
        }

        if (AppPreference.getBoolean(this, Keys.KEY_IS_PREMIUM, false) || !shouldShowPaywallNow()) {
            startMainClearingTask();
            return;
        }

        Intent intent = new Intent(this, PremiumActivity.class);
        intent.putExtra(PremiumActivity.EXTRA_LAUNCHED_FROM_SPLASH, true);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    /**
     * The paywall opens on the first launch and every third one after that.
     *
     * <p>Owner's call, 2026-07-30. Premium plus the two ad screens behind it were most of what was
     * left of a 15.5s launch; on the other two launches the player now lands on Home straight from
     * the splash. Premium is still reachable any time from the coins chip and Settings, so this
     * costs exposure, not access.
     */
    private boolean shouldShowPaywallNow() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int launches = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launches).apply();
        return launches % PAYWALL_EVERY_N_LAUNCHES == 1;
    }

    private void startMainClearingTask() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // ==================== CLEANUP ====================

    private void cleanup() {
        unregisterUpdateListener();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
