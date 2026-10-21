package com.colourmate.paintbynumber.activity;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.Utils.GifCacheManager;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.adapter.LibraryItemAdapter;
import com.colourmate.paintbynumber.ads.AppPreference;
import com.colourmate.paintbynumber.ads.IAPManager;
import com.colourmate.paintbynumber.ads.Keys;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.databinding.ActivityMainBinding;
import com.colourmate.paintbynumber.fragments.ColorFragment;
import com.colourmate.paintbynumber.fragments.DailyFragment;
import com.colourmate.paintbynumber.fragments.DiamondFragment;
import com.colourmate.paintbynumber.fragments.New2026Fragment;
import com.colourmate.paintbynumber.fragments.NumberFragment;
import com.colourmate.paintbynumber.fragments.PixelFragment;
import com.colourmate.paintbynumber.fragments.SavedFragment;
import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.model.CategoryModel;
import com.colourmate.paintbynumber.util.ColourColourArchPreference;
import com.colourmate.paintbynumber.util.DailyChallengeManager;
import com.colourmate.paintbynumber.util.RatingPrompt;
import com.colourmate.paintbynumber.util.StreakManager;
import com.colourmate.paintbynumber.views.BeautifulProgressDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.messaging.FirebaseMessaging;
import com.sumoing.recolor.library.LibraryItem;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import java.io.File;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;

    private static final String BACKGROUND_GIF_URL =
            "https://promptgptpro.ai/home_screen_animation.gif";

    private static final int PERMISSION_REQUEST_CODE = 1111;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 2222;

    private static WeakReference<MainActivity> instanceRef;

    private BeautifulProgressDialog progressDialog;
    private BroadcastReceiver broadcastReceiver;

    private final List<LibraryItemAdapter> adapters = new ArrayList<>();
    private final List<CategoryModel> categoryList = new ArrayList<>();

    private long backPressedTime;
    private Toast backToast;

    // ✅ GIF loading state
    private boolean isGifLoaded = false;
    private int gifLoadRetryCount = 0;
    private static final int MAX_GIF_RETRY = 2;

    // ✅ Fragment caching - KEY FIX for smooth navigation
    // Order must match the LinearLayouts in activity_main.xml and the arrays in
    // setupBottomNavigation(). NAV_NEW sits right after By Number — it is the same kind of
    // content, just served by ColorForge instead of ionicsolutions.
    private static final int NAV_NUMBER = 0;
    private static final int NAV_NEW = 1;
    private static final int NAV_DIAMOND = 2;
    private static final int NAV_COLOR = 3;
    private static final int NAV_PIXEL = 4;
    private static final int NAV_SAVED = 5;
    /** Daily Challenge as a tab (2026-10-14) — the calendar item in the bar, index 6 in the arrays. */
    private static final int NAV_DAILY = 6;

    private int currentNavIndex = -1;
    /** Which tab the app opens on. Decided once per launch — see chooseDefaultNav(). */
    private int defaultNavIndex = NAV_NUMBER;
    /**
     * New 2026 + Diamond tabs hidden on the owner's instruction (2026-10-14). The layout sets
     * both items gone; this flag keeps the default tab on By Number and stops any code path from
     * selecting a tab the bar no longer shows. Nothing is deleted — flip to true (and drop the two
     * visibility lines in activity_main.xml) to restore them.
     */
    private static final boolean colorForgeTabsEnabled = false;
    private Fragment activeFragment = null;

    // ✅ Cache fragments - no recreation on every click
    private NumberFragment numberFragment;
    private New2026Fragment new2026Fragment;
    private DiamondFragment diamondFragment;
    private ColorFragment colorFragment;
    private PixelFragment pixelFragment;
    private SavedFragment savedFragment;
    private DailyFragment dailyFragment;

    private View[] navItems;
    private View[] navIndicators;
    private TextView[] navLabels;
    private ImageView[] navIcons;

    private enum PendingAction {
        OPEN_NUMBER, OPEN_COLOR, OPEN_PIXELS, OPEN_IMPORT, OPEN_MY_WORKS, OPEN_PHOTO_TO_PIXEL
    }

    private PendingAction pendingAction = null;

    /** Ask MainActivity to open the By Number tab on a category: putExtra(EXTRA_NUMBER_CATEGORY). */
    public static final String EXTRA_NUMBER_CATEGORY = "open_number_category";

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupTransparentStatusBar();

        instanceRef = new WeakReference<>(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        askNotificationPermission();
        getFirebaseToken();
        loadCategories();
        Manager.getInstance().initialize();

        // Daily: hidden 2026-08-20, back 2026-10-14 — and since the same day a real TAB
        // (NAV_DAILY → DailyFragment) handled by setupBottomNavigation() like every other tab,
        // instead of an activity launched from a loose click listener. DailyCalendarActivity is
        // no longer reachable from the UI.

        // The coin icon inside the chip must do what the chip does — it used to open Premium on
        // its own, so a tap on the icon and a tap beside it went to different screens.
        binding.ivRemoveAds.setOnClickListener(v -> {
            bounce(binding.cmCoinsChip);
            showCoinsSheet();
        });
        // The whole gold chip is the target, not just the 15dp coin inside it. It is the coin
        // purse now (2026-10-14): shows the balance and opens the earn/premium sheet.
        binding.cmCoinsChip.setOnClickListener(v -> {
            bounce(binding.cmCoinsChip);
            showCoinsSheet();
        });
        refreshCoins();
        com.colourmate.paintbynumber.util.DailyReminder.schedule(this);
        applyGreeting();
        handleCategoryRequest(getIntent());

        // Home is the screen a picture is tapped from, so make sure an interstitial is warm by the
        // time that happens. No-op when one is already cached or in flight.
        MediationManager.preloadInterstitial(this);

        // Ask for a rating only once the user is actually looking at Home, and only when it is due
        // (3 days after install, then at most once a week — see RatingManager). The delay keeps it
        // clear of the splash/premium/ad chain that can still be settling at this point.
        binding.getRoot().postDelayed(() -> RatingPrompt.maybeShow(this), 2500);
        binding.ivSetting.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        // Saved is back in the bottom bar (nav_item_saved, 2026-10-14); the top-bar button is
        // gone in the layout but its listener is kept so the view stays harmless if re-shown.
        binding.ivSavedTop.setOnClickListener(v -> {
            bounce(binding.ivSavedTop);
            selectNavItem(NAV_SAVED);
        });

        // ✅ SETUP FRAGMENTS LAZILY - Fixes startup hang
        setupFragments();
        setupBottomNavigation();

        // Only on a real launch — not on a rotation, where replaying it would look like a glitch.
        if (savedInstanceState == null) {
            playIntroAnimation();

            // First run: say what the game is before dropping the player into a grid of pictures.
            if (WelcomeActivity.shouldShow(this)) {
                startActivity(WelcomeActivity.newIntent(this));
            }
        }

        // Defer heavy non-critical init to after first layout
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                // loadCategories() already ran at onCreate (line ~172); calling it again here
                // duplicated 7 network requests + spun a 2nd Volley queue on every cold start.
                // Background GIF (old meadow) disabled — we use the clean modern static background.
                // loadBackgroundGif();
            }
        }, 300);

        Library.getInstance().mLibraryItemsChanged = true;
    }

    // ==================== ✅ FRAGMENT CACHING - MAIN FIX ====================

    private void setupFragments() {
        FragmentManager fm = getSupportFragmentManager();

        // Just find existing fragments (after config change)
        numberFragment = (NumberFragment) fm.findFragmentByTag("number");
        new2026Fragment = (New2026Fragment) fm.findFragmentByTag("new2026");
        diamondFragment = (DiamondFragment) fm.findFragmentByTag("diamond");
        colorFragment = (ColorFragment) fm.findFragmentByTag("color");
        pixelFragment = (PixelFragment) fm.findFragmentByTag("pixel");
        savedFragment = (SavedFragment) fm.findFragmentByTag("saved");
        dailyFragment = (DailyFragment) fm.findFragmentByTag("daily");

        FragmentTransaction ft = fm.beginTransaction();

        // Which tab leads. The owner wants New 2026 first (§8.5c), but it was switched back to By
        // Number while New 2026 could only render "Coming soon" — an empty first screen is worse
        // than the paid tab. Now that New 2026 loads a real list, open on it whenever a ColorForge
        // server is configured, and fall back to By Number when there is none. Decided ONCE here
        // and reused by setupBottomNavigation(): if those two disagree the app opens on an empty
        // container.
        defaultNavIndex = colorForgeTabsEnabled && ColorForgeApi.hasExplicitServer(this)
                ? NAV_NEW : NAV_NUMBER;

        if (defaultNavIndex == NAV_NEW) {
            if (new2026Fragment == null) {
                new2026Fragment = new New2026Fragment();
                ft.add(R.id.fragment_container, new2026Fragment, "new2026");
            }
        } else if (numberFragment == null) {
            numberFragment = new NumberFragment();
            ft.add(R.id.fragment_container, numberFragment, "number");
        }

        // Hide any fragments that might already be there (config changes)
        // This ensures switchFragment(numberFragment) can properly 'show' it
        if (numberFragment != null) ft.hide(numberFragment);
        if (new2026Fragment != null) ft.hide(new2026Fragment);
        if (diamondFragment != null) ft.hide(diamondFragment);
        if (colorFragment != null) ft.hide(colorFragment);
        if (pixelFragment != null) ft.hide(pixelFragment);
        if (savedFragment != null) ft.hide(savedFragment);
        if (dailyFragment != null) ft.hide(dailyFragment);

        ft.commitNowAllowingStateLoss();

        activeFragment = null;
        currentNavIndex = -1;
    }

    /**
     * ✅ Switch fragment using show/hide - INSTANT, no reload
     */
    private void switchFragment(Fragment targetFragment, String tag) {
        if (targetFragment == null || targetFragment == activeFragment) return;

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        // ✅ Smother transition for show/hide
        ft.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);

        // Hide current
        if (activeFragment != null) {
            ft.hide(activeFragment);
        }

        // ✅ Lazy addition - add only when needed
        if (!targetFragment.isAdded()) {
            ft.add(R.id.fragment_container, targetFragment, tag);
        } else {
            ft.show(targetFragment);
        }

        ft.commitAllowingStateLoss();
        activeFragment = targetFragment;
    }

    // ==================== BOTTOM NAVIGATION ====================

    private void setupBottomNavigation() {
        navItems = new View[]{
                findViewById(R.id.nav_item_number),
                findViewById(R.id.nav_item_new),
                findViewById(R.id.nav_item_diamond),
                findViewById(R.id.nav_item_color),
                findViewById(R.id.nav_item_pixel),
                findViewById(R.id.nav_item_saved),      // NAV_SAVED = 5 (back in the bar, 2026-10-14)
                findViewById(R.id.nav_item_calendar),   // NAV_DAILY = 6 (a tab since 2026-10-14)
        };

        navIndicators = new View[]{
                findViewById(R.id.nav_indicator_number),
                findViewById(R.id.nav_indicator_new),
                findViewById(R.id.nav_indicator_diamond),
                findViewById(R.id.nav_indicator_color),
                findViewById(R.id.nav_indicator_pixel),
                findViewById(R.id.nav_indicator_saved),
                findViewById(R.id.nav_indicator_calendar),
        };

        navLabels = new TextView[]{
                findViewById(R.id.nav_label_number),
                findViewById(R.id.nav_label_new),
                findViewById(R.id.nav_label_diamond),
                findViewById(R.id.nav_label_color),
                findViewById(R.id.nav_label_pixel),
                findViewById(R.id.nav_label_saved),
                findViewById(R.id.nav_label_calendar),
        };

        navIcons = new ImageView[]{
                findViewById(R.id.iv_nav_number),
                findViewById(R.id.iv_nav_new),
                findViewById(R.id.iv_nav_diamond),
                findViewById(R.id.iv_nav_color),
                findViewById(R.id.iv_nav_pixel),
                findViewById(R.id.iv_nav_saved),
                findViewById(R.id.iv_nav_calendar),
        };

        for (int i = 0; i < navItems.length; i++) {
            if (navItems[i] == null) continue;
            if (!colorForgeTabsEnabled && (i == NAV_NEW || i == NAV_DIAMOND)) continue;
            final int index = i;
            navItems[i].setOnClickListener(v -> onNavItemClicked(index));
        }

        // Select default tab — chosen in setupFragments(), which added the matching fragment.
        selectNavItem(defaultNavIndex);
    }

    private void onNavItemClicked(int index) {
        if (index == currentNavIndex) {
            // Already here — no fragment switch, but the tap should still feel like it landed.
            playTapBounce(index);
            return;
        }
        selectNavItem(index);
    }

    /**
     * App-open intro (roadmap item 9): the title springs in like a game logo and the top-bar icons
     * follow it in. Kept short — this plays before every session, so it has to stay out of the way.
     */
    private void playIntroAnimation() {
        View title = binding.tvTitle;

        title.setAlpha(0f);
        title.setScaleX(0.72f);
        title.setScaleY(0.72f);
        title.setTranslationY(28f);
        title.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(90)
                .setDuration(620)
                .setInterpolator(new OvershootInterpolator(1.9f))
                .start();

        View[] icons = {binding.ivSavedTop, binding.ivSetting, binding.ivRemoveAds};
        for (int i = 0; i < icons.length; i++) {
            View icon = icons[i];
            if (icon == null) continue;

            icon.setAlpha(0f);
            icon.setScaleX(0.6f);
            icon.setScaleY(0.6f);
            icon.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(320 + i * 70L)   // staggered, right-to-left as laid out
                    .setDuration(420)
                    .setInterpolator(new OvershootInterpolator(2.4f))
                    .start();
        }
    }

    private void playTapBounce(int index) {
        if (navIcons == null || index < 0 || index >= navIcons.length) return;
        bounce(navIcons[index]);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    // ==================== COINS ====================

    private void refreshCoins() {
        if (binding == null || binding.tvCoins == null) return;
        binding.tvCoins.setText(com.colourmate.paintbynumber.util.CoinManager.format(
                com.colourmate.paintbynumber.util.CoinManager.balance(this)));
    }

    /**
     * The coin sheet: balance, "watch a video" to earn, and Premium as the way out of needing
     * coins at all. Premium users see the balance only — there is nothing to sell them.
     */
    private void showCoinsSheet() {
        final android.app.Dialog sheet = new android.app.Dialog(this, R.style.FullWidthDialog);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sheet.setContentView(R.layout.dialog_coins);
        Window w = sheet.getWindow();
        if (w != null) {
            w.setGravity(android.view.Gravity.BOTTOM);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        final TextView balance = sheet.findViewById(R.id.tv_coins_balance);
        final View watch = sheet.findViewById(R.id.btn_coins_watch);
        final View premium = sheet.findViewById(R.id.btn_coins_premium);
        final Runnable paint = () -> balance.setText(com.colourmate.paintbynumber.util.CoinManager.format(
                com.colourmate.paintbynumber.util.CoinManager.balance(this)));
        paint.run();

        boolean adsOn = com.colourmate.paintbynumber.ads.AdController.shouldShowAds(this);
        watch.setVisibility(adsOn ? View.VISIBLE : View.GONE);
        premium.setVisibility(adsOn ? View.VISIBLE : View.GONE);

        watch.setOnClickListener(v -> {
            watch.setEnabled(false);
            MediationManager.showRewardedAd(this, new com.colourmate.paintbynumber.ads.RewardedAdListener() {
                @Override public void onRewardGranted() {
                    runOnUiThread(() -> {
                        com.colourmate.paintbynumber.util.CoinManager.add(MainActivity.this,
                                com.colourmate.paintbynumber.util.CoinManager.REWARD_AD_COINS);
                        paint.run();
                        refreshCoins();
                        bounce(balance);
                        watch.setEnabled(true);
                        Toast.makeText(MainActivity.this, "+" + com.colourmate.paintbynumber.util.CoinManager.REWARD_AD_COINS
                                + " coins 🪙", Toast.LENGTH_SHORT).show();
                    });
                }
                // No video available: nothing is granted — coins are the one place a free grant
                // would be farmable — but the button must come back.
                @Override public void onRewardNull() {
                    runOnUiThread(() -> {
                        watch.setEnabled(true);
                        Toast.makeText(MainActivity.this, "No video right now — try again in a moment",
                                Toast.LENGTH_SHORT).show();
                    });
                }
                @Override public void onRewardProgress() { }
                @Override public void onVideoSkipped() { runOnUiThread(() -> watch.setEnabled(true)); }
            });
        });
        premium.setOnClickListener(v -> {
            sheet.dismiss();
            startActivity(new Intent(this, PremiumActivity.class));
        });
        sheet.findViewById(R.id.btn_coins_close).setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    /** Shared tap feedback for the nav icons and the top-bar Saved icon. */
    private void bounce(View view) {
        if (view == null) return;

        view.animate().cancel();
        view.setScaleX(0.7f);
        view.setScaleY(0.7f);
        view.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(3.5f))
                .start();
    }

    /**
     * ✅ Select nav item - uses show/hide, NOT replace
     */
    private void selectNavItem(int index) {
        currentNavIndex = index;
        updateNavVisuals(index);

        // ✅ Initialize and switch fragments lazily
        Fragment targetFragment;
        String tag;

        switch (index) {
            case NAV_NEW:
                if (new2026Fragment == null) new2026Fragment = new New2026Fragment();
                targetFragment = new2026Fragment;
                tag = "new2026";
                break;
            case NAV_DIAMOND:
                if (diamondFragment == null) diamondFragment = new DiamondFragment();
                targetFragment = diamondFragment;
                tag = "diamond";
                break;
            case NAV_COLOR:
                if (colorFragment == null) colorFragment = new ColorFragment();
                targetFragment = colorFragment;
                tag = "color";
                break;
            case NAV_PIXEL:
                if (pixelFragment == null) pixelFragment = new PixelFragment();
                targetFragment = pixelFragment;
                tag = "pixel";
                break;
            case NAV_DAILY:
                if (dailyFragment == null) dailyFragment = new DailyFragment();
                targetFragment = dailyFragment;
                tag = "daily";
                break;
            case NAV_SAVED:
                if (savedFragment == null) savedFragment = new SavedFragment();
                targetFragment = savedFragment;
                tag = "saved";
                // ✅ Refresh saved items only when switching to Saved tab
                if (savedFragment != null && savedFragment.isAdded()) {
                    savedFragment.refreshIfNeeded();
                }
                break;
            case NAV_NUMBER:
            default:
                if (numberFragment == null) numberFragment = new NumberFragment();
                targetFragment = numberFragment;
                tag = "number";
                break;
        }

        switchFragment(targetFragment, tag);
    }

    /** Loaded once. {@code ResourcesCompat.getFont} was being called twice on every single tap. */
    private Typeface navFont, navFontSelected;

    /** Which tab currently wears the pill, so only it and the new one need restyling. */
    private int styledNavIndex = -1;

    /**
     * Restyles the bottom bar for a new selection.
     *
     * <p>Measured 2026-08-09: tab switching was 78% janky with a 89ms median frame, and this was a
     * large part of it. Every tap rewrote the background, scale, alpha, colour, typeface and icon
     * of <em>all six</em> tabs — and {@code setTypeface} on a label forces the bar to re-measure,
     * so four untouched tabs were invalidating layout for nothing. Only two tabs ever change.
     */
    private void updateNavVisuals(int selectedIndex) {
        if (navFont == null) {
            navFont = ResourcesCompat.getFont(this, R.font.quicksand_regular);
            navFontSelected = ResourcesCompat.getFont(this, R.font.quicksand_semibold);
        }

        if (styledNavIndex == selectedIndex) return;

        if (styledNavIndex == -1) {
            // First run: nothing is styled yet, so every tab has to be put into a known state.
            for (int i = 0; i < navItems.length; i++) styleNavItem(i, i == selectedIndex);
        } else {
            styleNavItem(styledNavIndex, false);
            styleNavItem(selectedIndex, true);
        }
        styledNavIndex = selectedIndex;
    }

    /** Applies the selected / unselected look to one tab. Indices outside the bar are ignored. */
    private void styleNavItem(int i, boolean selected) {
        if (i < 0 || i >= navItems.length) return;
        {

            // Reference bar (2026-10-14): the pill IS the icon+label container — a soft, flat,
            // fully-rounded lilac-grey pill behind the selected tab, nothing behind the others.
            // Monochrome: the contrast comes from the dark filled icon + bold label, not colour.
            if (navIndicators[i] != null) {
                if (selected) {
                    navIndicators[i].setBackgroundResource(R.drawable.bg_nav_pill_selected);

                    // Tap feel: the pill pops in with an overshoot…
                    navIndicators[i].setScaleX(0.75f);
                    navIndicators[i].setScaleY(0.75f);
                    navIndicators[i].setAlpha(0f);

                    AnimatorSet anim = new AnimatorSet();
                    anim.playTogether(
                            ObjectAnimator.ofFloat(navIndicators[i], "scaleX", 0.75f, 1f),
                            ObjectAnimator.ofFloat(navIndicators[i], "scaleY", 0.75f, 1f),
                            ObjectAnimator.ofFloat(navIndicators[i], "alpha", 0f, 1f)
                    );
                    anim.setDuration(280);
                    anim.setInterpolator(new OvershootInterpolator(2.5f));
                    anim.start();
                } else {
                    navIndicators[i].setBackground(null);
                    navIndicators[i].setScaleX(1f);
                    navIndicators[i].setScaleY(1f);
                    navIndicators[i].setAlpha(1f);
                }
            }

            // …and the icon itself bounces, so the tap is felt even on the tab you re-tap.
            if (navIcons[i] != null) {
                navIcons[i].animate().cancel();
                if (selected) {
                    navIcons[i].setScaleX(0.5f);
                    navIcons[i].setScaleY(0.5f);
                    navIcons[i].animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(340)
                            .setInterpolator(new OvershootInterpolator(3.5f))
                            .start();
                } else {
                    navIcons[i].setScaleX(1f);
                    navIcons[i].setScaleY(1f);
                }
            }

            // Label — dark ink + semibold inside the pill, muted ink + regular otherwise.
            if (navLabels[i] != null) {
                navLabels[i].setTextColor(selected
                        ? getColor(R.color.cm_ink)
                        : getColor(R.color.cm_ink_2));
                navLabels[i].setTypeface(selected ? navFontSelected : navFont);
            }

            // Icons carry their own colour: *_select is filled white (it sits on the teal
            // indicator), *_unselect is a dark outline on the white bar.
            if (navIcons[i] != null) {
                switch (i) {
                    case NAV_NUMBER:
                        navIcons[i].setImageResource(selected
                                ? R.drawable.ic_home_select
                                : R.drawable.ic_home_unselect);
                        break;
                    case NAV_NEW:
                        navIcons[i].setImageResource(selected
                                ? R.drawable.ic_new_select
                                : R.drawable.ic_new_unselect);
                        break;
                    case NAV_DIAMOND:
                        navIcons[i].setImageResource(selected
                                ? R.drawable.ic_diamond_select
                                : R.drawable.ic_diamond_unselect);
                        break;
                    case NAV_COLOR:
                        navIcons[i].setImageResource(selected
                                ? R.drawable.ic_image_select
                                : R.drawable.ic_image_unselect);
                        break;
                    case NAV_PIXEL:
                        navIcons[i].setImageResource(selected
                                ? R.drawable.ic_grid_select
                                : R.drawable.ic_grid_unselect);
                        break;
                    case NAV_SAVED:
                        navIcons[i].setImageResource(selected
                                ? R.drawable.ic_saved_select
                                : R.drawable.ic_saved_unselect);
                        break;
                    case NAV_DAILY:
                        navIcons[i].setImageResource(selected
                                ? R.drawable.ic_calendar_select
                                : R.drawable.ic_calendar_unselect);
                        break;
                }
                // Monochrome (reference bar): filled dark icon when selected, muted outline
                // otherwise. navColorFor() is kept in case the coloured icons are wanted back.
                navIcons[i].setColorFilter(selected ? getColor(R.color.cm_ink) : getColor(R.color.cm_ink_2),
                        android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
    }

    /**
     * Opens the By Number tab on a named category when another screen asks for it. The fragment
     * holds the request until its own categories have loaded, so timing does not matter here.
     */
    private void handleCategoryRequest(Intent intent) {
        if (intent == null) return;
        String category = intent.getStringExtra(EXTRA_NUMBER_CATEGORY);
        if (category == null || category.isEmpty()) return;
        intent.removeExtra(EXTRA_NUMBER_CATEGORY);      // a rotation must not reopen it

        selectNavItem(NAV_NUMBER);
        if (numberFragment != null) {
            numberFragment.selectCategory(category);
        }
    }

    // ==================== STATUS BAR ====================

    private void setupTransparentStatusBar() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);

        View topBar = findViewById(R.id.top_bar);
        if (topBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(v.getPaddingLeft(), bars.top,
                        v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        View rootLayout = findViewById(R.id.main_content);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(navBars.left, v.getPaddingTop(),
                        navBars.right, navBars.bottom);
                return insets;
            });
        }
    }

    // ==================== BACKGROUND GIF ====================

    private void initBackgroundLayers() {
        if (binding.ivBackgroundStatic != null) {
            binding.ivBackgroundStatic.setVisibility(View.VISIBLE);
            binding.ivBackgroundStatic.setAlpha(1f);
            // Design-system ground: lilac paper with two faint paint blooms.
            binding.ivBackgroundStatic.setImageResource(R.drawable.cm_bg_paper);
        }

        if (binding.ivBackgroundGif != null) {
            binding.ivBackgroundGif.setVisibility(View.GONE);   // keep the clean static background
            binding.ivBackgroundGif.setAlpha(0f);
        }

        isGifLoaded = false;
        gifLoadRetryCount = 0;
    }

    /** Each bottom-nav tab's own colour (used when it is not the selected/white one). */
    /**
     * Time-of-day greeting. Small thing, but it is the difference between an app that opens and
     * an app that greets you — and it costs one string.
     */
    private void applyGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) {
            greeting = "Good morning";
        } else if (hour < 17) {
            greeting = "Good afternoon";
        } else if (hour < 21) {
            greeting = "Good evening";
        } else {
            greeting = "Winding down";
        }
        // The line under this one is the app's name, so the greeting must not repeat it —
        // "Good evening / Colour Mate" reads as the app greeting itself by name. Until there is a
        // real profile to draw a name from, the greeting carries the time of day and the brand
        // line simply says who the app is.
        binding.tvGreeting.setText(greeting);
    }

    /** Unselected tab tint — one paint per tab, straight from the design system's accent set. */
    private int navColorFor(int i) {
        if (i == NAV_NEW) return 0xFF37A9FF;       // sky
        if (i == NAV_DIAMOND) return 0xFFFFC24B;   // gold
        if (i == NAV_COLOR) return 0xFFFF6BA9;     // blossom
        if (i == NAV_PIXEL) return 0xFF21D4C2;     // turquoise
        if (i == NAV_SAVED) return 0xFFFFC24B;     // gold (free while Diamond is hidden)
        return 0xFF7A5CFF;                         // By Number — violet
    }

    /**
     * ivBackgroundGif is a full-screen centerCrop background, so the screen size is all the
     * detail it can show. It used to load with Target.SIZE_ORIGINAL, which kept every frame of
     * a looping GIF at the source resolution.
     */
    private int gifTargetWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int gifTargetHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private void loadBackgroundGif() {
        // Modern static home background is used — the old animated meadow GIF is disabled.
        if (true) return;
        Log.d(TAG, "loadBackgroundGif: checking cache...");

        File cachedGif = GifCacheManager.getCachedGif(this);

        if (cachedGif != null && !GifCacheManager.isUrlChanged(this, BACKGROUND_GIF_URL)) {
            Log.d(TAG, "GIF found in cache");
            loadGifIntoView(cachedGif);
        } else {
            Log.d(TAG, "GIF not cached, downloading...");
            downloadAndLoadGif();
        }
    }

    private void loadGifIntoView(File gifFile) {
        if (isFinishing() || isDestroyed()) return;
        if (binding == null || binding.ivBackgroundGif == null) return;

        Glide.with(getApplicationContext())
                .asGif()
                .load(gifFile)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(false)
                .override(gifTargetWidth(), gifTargetHeight())
                .listener(new RequestListener<GifDrawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e,
                                                @Nullable Object model,
                                                @NonNull Target<GifDrawable> target,
                                                boolean isFirstResource) {
                        Log.e(TAG, "GIF load failed", e);
                        handleGifLoadFailure();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull GifDrawable resource,
                                                   @Nullable Object model,
                                                   @NonNull Target<GifDrawable> target,
                                                   @NonNull DataSource dataSource,
                                                   boolean isFirstResource) {
                        Log.d(TAG, "GIF loaded! Source: " + dataSource);
                        isGifLoaded = true;
                        resource.setLoopCount(GifDrawable.LOOP_FOREVER);
                        resource.start();
                        runOnUiThread(() -> performCrossfade());
                        return false;
                    }
                })
                .into(binding.ivBackgroundGif);
    }

    private void downloadAndLoadGif() {
        if (isFinishing() || isDestroyed()) return;

        GifCacheManager.downloadAndCache(this, BACKGROUND_GIF_URL,
                new GifCacheManager.OnCacheCompleteListener() {
                    @Override
                    public void onCached(File gifFile) {
                        if (!isFinishing() && !isDestroyed()) {
                            loadGifIntoView(gifFile);
                        }
                    }

                    @Override
                    public void onFailed(String error) {
                        loadGifFromNetworkDirect();
                    }
                });
    }

    private void loadGifFromNetworkDirect() {
        if (isFinishing() || isDestroyed()) return;
        if (binding == null || binding.ivBackgroundGif == null) return;

        Glide.with(getApplicationContext())
                .asGif()
                .load(BACKGROUND_GIF_URL)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .override(gifTargetWidth(), gifTargetHeight())
                .listener(new RequestListener<GifDrawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e,
                                                @Nullable Object model,
                                                @NonNull Target<GifDrawable> target,
                                                boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull GifDrawable resource,
                                                   @Nullable Object model,
                                                   @NonNull Target<GifDrawable> target,
                                                   @NonNull DataSource dataSource,
                                                   boolean isFirstResource) {
                        isGifLoaded = true;
                        resource.setLoopCount(GifDrawable.LOOP_FOREVER);
                        resource.start();
                        runOnUiThread(() -> performCrossfade());
                        return false;
                    }
                })
                .into(binding.ivBackgroundGif);
    }

    private void handleGifLoadFailure() {
        gifLoadRetryCount++;
        if (gifLoadRetryCount <= MAX_GIF_RETRY) {
            GifCacheManager.clearCache(this);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    downloadAndLoadGif();
                }
            }, 1000);
        }
    }

    private void performCrossfade() {
        if (binding == null || isFinishing() || isDestroyed()) return;

        ImageView gifView = binding.ivBackgroundGif;
        ImageView staticView = binding.ivBackgroundStatic;

        if (gifView == null) return;

        gifView.setVisibility(View.VISIBLE);

        gifView.animate()
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        if (staticView != null && staticView.getVisibility() == View.VISIBLE) {
            staticView.animate()
                    .alpha(0f)
                    .setDuration(600)
                    .setStartDelay(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (binding != null && binding.ivBackgroundStatic != null) {
                                binding.ivBackgroundStatic.setVisibility(View.GONE);
                                binding.ivBackgroundStatic.setImageDrawable(null);
                            }
                        }
                    })
                    .start();
        }
    }

    // ==================== GIF LIFECYCLE ====================

    private void resumeGifIfNeeded() {
        if (binding == null || binding.ivBackgroundGif == null) return;

        Drawable drawable = binding.ivBackgroundGif.getDrawable();
        if (drawable instanceof GifDrawable) {
            GifDrawable gifDrawable = (GifDrawable) drawable;
            if (!gifDrawable.isRunning()) {
                gifDrawable.start();
            }
        } else if (!isGifLoaded) {
            loadBackgroundGif();
        }
    }

    private void pauseGif() {
        if (binding == null || binding.ivBackgroundGif == null) return;

        Drawable drawable = binding.ivBackgroundGif.getDrawable();
        if (drawable instanceof GifDrawable) {
            GifDrawable gifDrawable = (GifDrawable) drawable;
            if (gifDrawable.isRunning()) {
                gifDrawable.stop();
            }
        }
    }

    private void cleanupGif() {
        if (binding == null) return;

        if (binding.ivBackgroundGif != null) {
            Drawable drawable = binding.ivBackgroundGif.getDrawable();
            if (drawable instanceof GifDrawable) {
                ((GifDrawable) drawable).stop();
                ((GifDrawable) drawable).recycle();
            }
            binding.ivBackgroundGif.setImageDrawable(null);
        }

        if (binding.ivBackgroundStatic != null) {
            binding.ivBackgroundStatic.setImageDrawable(null);
        }
    }

    // ==================== LIFECYCLE ====================

    @Override
    protected void onResume() {
        super.onResume();

        resumeGifIfNeeded();

        // Coins (2026-10-14): the daily login bonus lands the first time Home is seen each day,
        // and the purse is re-read because a game screen may have spent from it.
        com.colourmate.paintbynumber.util.DailyReminder.recordAppOpen(this);
        int bonus = com.colourmate.paintbynumber.util.CoinManager.claimDailyBonusIfDue(this);
        refreshCoins();
        if (bonus > 0) {
            bounce(binding.cmCoinsChip);
            Toast.makeText(this, "Daily bonus: +" + bonus + " coins 🪙", Toast.LENGTH_SHORT).show();
        }

        // Re-check entitlements with Play. A subscription can end without the app hearing about it
        // — it lapses, the user cancels in the Play Store, or a refund is issued — and until this
        // ran, premium stayed switched on. Throttled to once a minute inside IAPManager.
        IAPManager.getInstance(this).refreshEntitlements();

        if (Library.getInstance().mLibraryItemsChanged) {
            refreshGrid();
            Library.getInstance().mLibraryItemsChanged = false;
        }

        if (currentNavIndex >= 0 && navIndicators != null) {
            updateNavVisuals(currentNavIndex);
        }

        registerLocalReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseGif();

        if (broadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupGif();

        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing dialog", e);
            }
            progressDialog = null;
        }

        if (Library.mCurrentDrawing != null) {
            Library.mCurrentDrawing.cancelDownload(this);
        }

        if (instanceRef != null && instanceRef.get() == this) {
            instanceRef.clear();
        }
    }

    // ==================== PERMISSIONS ====================

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.POST_NOTIFICATIONS)) {
                    showNotificationPermissionRationale();
                } else {
                    requestNotificationPermission();
                }
            }
        }
    }

    private void showNotificationPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Notification Permission")
                .setMessage("We need notification permission to send you important updates, "
                        + "new content alerts, and keep you informed about your coloring progress.")
                .setPositiveButton("Allow", (d, w) -> requestNotificationPermission())
                .setNegativeButton("Not Now", (d, w) -> d.dismiss())
                .setCancelable(false)
                .show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void getFirebaseToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            String token = task.getResult();
            Log.d(TAG, "FCM Token: " + token);
            saveTokenToPrefs(token);
        });
    }

    private void saveTokenToPrefs(String token) {
        getSharedPreferences("FCM_PREFS", MODE_PRIVATE)
                .edit().putString("fcm_token", token).apply();
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void subscribeToTopic(String topic) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic);
    }

    private void requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                    PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void runWithMediaPermission(PendingAction action) {
        pendingAction = action;
        if (hasMediaPermission()) {
            MediationManager.showInterstitial(this, this::runPendingAction);
        } else {
            requestMediaPermission();
        }
    }

    private void runPendingAction() {
        if (pendingAction == null) return;

        switch (pendingAction) {
            case OPEN_NUMBER:
                selectNavItem(NAV_NUMBER);
                break;
            case OPEN_COLOR:
                selectNavItem(NAV_COLOR);
                break;
            case OPEN_PIXELS:
                selectNavItem(NAV_PIXEL);
                break;
            case OPEN_MY_WORKS:
                selectNavItem(NAV_SAVED);
                break;
            case OPEN_IMPORT:
                Intent importIntent = new Intent(this, ScanProcessActivity.class);
                importIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivityForResult(importIntent, ScanProcessActivity.NEWPICTURESCANNED);
                overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
                break;
        }
        pendingAction = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                boolean mediaGranted = grantResults.length > 0;
                for (int r : grantResults) {
                    if (r != PackageManager.PERMISSION_GRANTED) {
                        mediaGranted = false;
                        break;
                    }
                }
                MediationManager.showInterstitial(this, this::runPendingAction);
                break;

            case NOTIFICATION_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    subscribeToTopic("all_users");
                } else {
                    showNotificationDisabledMessage();
                }
                break;
        }
    }

    private void showNotificationDisabledMessage() {
        Toast.makeText(this,
                "Notifications disabled. You can enable them in Settings.",
                Toast.LENGTH_LONG).show();
    }



    // ==================== NAVIGATION HELPERS ====================

    public static MainActivity getInstance() {
        return instanceRef != null ? instanceRef.get() : null;
    }

    public void handleBackPress() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            if (backToast != null) backToast.cancel();
            // Plain exit. The exit sheet with a native ad in it was removed on 2026-10-14 (Play
            // Disruptive Ads strike — "ads shown when the user exits"). The remote
            // KEY_IS_EXIT_DIALOG switch is ignored on purpose so it cannot come back from config.
            finishAffinity();
            return;
        } else {
            backToast = Toast.makeText(this,
                    "Press back again to exit", Toast.LENGTH_SHORT);
            backToast.show();
        }
        backPressedTime = System.currentTimeMillis();
    }

    @Override
    public void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
        handleCategoryRequest(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null || intent.getExtras() == null) return;

        Bundle extras = intent.getExtras();
        String type = extras.getString("type");

        if (type != null) {
            switch (type) {
                case "new_content":
                    break;
                case "promo":
                    break;
                case "update":
                    break;
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 50001) {
            if (resultCode == RESULT_OK && data != null && data.hasExtra("ScanOk")) {
                refreshGrid();
                if (ScanProcessActivity.libraryItem != null) {
                    startEditing(ScanProcessActivity.libraryItem, false);
                    ScanProcessActivity.libraryItem = null;
                }
            }
        }
    }

    // ==================== EDITING ====================

    /**
     * Start editing a library item.
     * Handles premium checks and rewarded ads.
     */
    public boolean startEditing(LibraryItem item, boolean makeCopy) {
        if (item == null) return false;

        Library.getInstance().isNewCopy = makeCopy;

        boolean isPremium = AppPreference.getBoolean(this, Keys.KEY_IS_PREMIUM, false);

        if (item.isFreeContent() || isPremium) {
            startDownload(item);
            return true;
        }

        if (Manager.getInstance().hasInternetConnection(this)) {
            // Interstitial, not rewarded: opening a picture is ordinary browsing, so it should
            // never depend on a video being available. onAdClosed() fires whether the ad showed,
            // failed or was skipped entirely — the picture always opens.
            MediationManager.showInterstitial(this, () -> startDownload(item));
            return true;
        } else {
            Snackbar.make(binding.mainContent, R.string.internet_conn_failed, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Retry", v -> refresh()).show();
            return false;
        }
    }

    // ==================== DOWNLOAD ====================

    public void startDownload(LibraryItem item) {
        Log.d(TAG, "start download " + item.getName());

        if (isFinishing() || isDestroyed()) return;

        // The newest tap wins.
        //
        // This used to `return` when a download was already in flight, so tapping a second picture
        // while the first was still loading did nothing at all — and then the FIRST picture opened.
        // From the user's side that reads as "I tapped B and A opened". Cancel whatever is in
        // flight and load what was actually tapped instead.
        //
        // Order matters: the old dialog's dismiss listener cancels Library.mCurrentDrawing, so both
        // the cancel and the dismiss have to happen while that field still points at the OLD
        // drawing — before the new one is assigned below.
        if (Library.mCurrentDrawing != null && Library.mCurrentDrawing.isDownloading()) {
            Library.mCurrentDrawing.cancelDownload(this);
        }
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing previous dialog", e);
            }
            progressDialog = null;
        }

        Library.mCurrentDrawing = new Library.Drawing();
        Library.mCurrentDrawing.mItem = item;

        // Identity of THIS tap. A download that gets superseded may still call back afterwards, and
        // it must not open a picture the user has already replaced.
        final Library.Drawing requested = Library.mCurrentDrawing;

        final AtomicBoolean downloadCompleted = new AtomicBoolean(false);

        progressDialog = new BeautifulProgressDialog(this,
                BeautifulProgressDialog.withLottie, "Please wait");
        progressDialog.setCancelable(true);

        progressDialog.setOnDismissListener(dialog -> {
            if (!downloadCompleted.get() && Library.mCurrentDrawing != null) {
                Library.mCurrentDrawing.cancelDownload(this);
            }
            progressDialog = null;
        });

        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                (dialog, which) -> {
                    if (Library.mCurrentDrawing != null) {
                        Library.mCurrentDrawing.cancelDownload(this);
                    }
                    dialog.cancel();
                    progressDialog = null;
                });

        progressDialog.show();

        Library.mCurrentDrawing.download(this,
                new Library.Drawing.OnDownloadCompletedListener() {
                    @Override
                    public void onCompleted() {
                        downloadCompleted.set(true);
                        runOnUiThread(() -> {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                    Log.e(TAG, "Error dismissing dialog", e);
                                } finally {
                                    progressDialog = null;
                                }
                            }

                            // A superseded download can still report success. If the user has since
                            // tapped a different picture, this callback belongs to the old one and
                            // must not open it over the new selection.
                            if (Library.mCurrentDrawing != requested) {
                                Log.d(TAG, "Ignoring completed download for superseded item "
                                        + item.getName());
                                return;
                            }

                            if (!isFinishing() && !isDestroyed()) {
                                MediationManager.showInterstitial(MainActivity.this, () -> {
                                    // Checked again on the far side of the ad: it is on screen for
                                    // seconds, which is plenty of time for the selection to change.
                                    if (Library.mCurrentDrawing != requested) {
                                        Log.d(TAG, "Selection changed during ad — not opening "
                                                + item.getName());
                                        return;
                                    }
                                    try {
                                        Intent intent = new Intent(MainActivity.this,
                                                DrawingActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                        // The drawing screen works off Library.mCurrentDrawing;
                                        // this rides along so it can prove it opened what was asked
                                        // for rather than whatever the global happens to hold.
                                        intent.putExtra(DrawingActivity.EXTRA_EXPECTED_ITEM,
                                                item.getName());
                                        startActivity(intent);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error starting activity", e);
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onFailed(String error) {
                        runOnUiThread(() -> {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                try {
                                    progressDialog.cancel();
                                } catch (Exception e) {
                                    Log.e(TAG, "Error canceling dialog", e);
                                } finally {
                                    progressDialog = null;
                                }
                            }

                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(MainActivity.this,
                                        "Download failed: " + error,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    // ==================== RECEIVER ====================

    private void registerLocalReceiver() {
        if (broadcastReceiver != null) return;

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Uri data = intent.getData();
                if (data == null) return;

                String scheme = data.getScheme();
                if ("url".equals(scheme)) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(intent.getDataString().substring(6))));
                } else if ("daily".equals(scheme) || "itemsChanged".equals(scheme)) {
                    refreshGrid();
                }
            }
        };

        IntentFilter filter = new IntentFilter("android.intent.action.VIEW");
        filter.addDataScheme("url");
        filter.addDataScheme("daily");
        filter.addDataScheme("itemsChanged");
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(broadcastReceiver, filter);
    }

    // ==================== REFRESH ====================

    public void refreshGrid() {
        refresh();
    }

    public void refresh() {
        for (LibraryItemAdapter adapter : adapters) {
            boolean hidden = adapter.getItemCount() <= 0;
            adapter.setHidden(hidden);
            adapter.notifyDataSetChanged();
        }
    }

    // ==================== CATEGORIES ====================

    private void loadCategories() {
        categoryList.add(new CategoryModel("love", Manager.LoveURL()));
        categoryList.add(new CategoryModel("animals", Manager.AnimalURL()));
        categoryList.add(new CategoryModel("flowers", Manager.FlowerURL()));
        categoryList.add(new CategoryModel("nature", Manager.NatureURL()));
        categoryList.add(new CategoryModel("mandalas", Manager.MandalaURL()));
        categoryList.add(new CategoryModel("people", Manager.PeopleURL()));
        categoryList.add(new CategoryModel("kids", Manager.KidURL()));

        RequestQueue requestQueue = Volley.newRequestQueue(this);

        for (int i = 0; i < categoryList.size(); i++) {
            final int finalI = i;
            String url = categoryList.get(i).getCatURL();

            StringRequest strReq = new StringRequest(
                    com.android.volley.Request.Method.GET, url,
                    response -> {
                        try {
                            JSONArray data = new JSONObject(response).getJSONArray("data");
                            String catName = categoryList.get(finalI).getCatName();
                            int length = data.length();

                            switch (catName) {
                                case "kids":
                                    ColourColourArchPreference.setTotalkids(this, length);
                                    break;
                                case "mandalas":
                                    ColourColourArchPreference.setTotalmandala(this, length);
                                    break;
                                case "flowers":
                                    ColourColourArchPreference.setTotalflower(this, length);
                                    break;
                                case "nature":
                                    ColourColourArchPreference.setTotalnature(this, length);
                                    break;
                                case "animals":
                                    ColourColourArchPreference.setTotalanimal(this, length);
                                    break;
                                case "love":
                                    ColourColourArchPreference.setTotallove(this, length);
                                    break;
                                case "people":
                                    ColourColourArchPreference.setTotalpeople(this, length);
                                    break;
                            }
                        } catch (JSONException e) {
                            Log.e("CategoryLoader", "JSON parse error", e);
                        }
                    },
                    error -> Log.d("Error", error.toString()));

            requestQueue.add(strReq);
        }
    }
}
