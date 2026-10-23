package com.colourmate.paintbynumber.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.android.billingclient.api.ProductDetails;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.codersroute.flexiblewidgets.FlexibleSwitch;
import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.ads.Utils;
import com.colourmate.paintbynumber.databinding.ActivitySettingsBinding;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.adapter.LocalSongsAdapter;
import com.colourmate.paintbynumber.adapter.OnlineSongAdapter;
import com.colourmate.paintbynumber.ads.IAPManager;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.model.SongModel;
import com.colourmate.paintbynumber.util.GameSaver;
import com.colourmate.paintbynumber.util.SoundUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

import me.everything.android.ui.overscroll.OverScrollDecoratorHelper;

public class SettingsActivity extends BaseActivity {

    private ActivitySettingsBinding binding;
    private static final int PERMISSION_READ_STORAGE = 123;
    private final ArrayList<SongModel> serverMusicList = new ArrayList<>();

    private boolean isYearlySelected = true; // default yearly


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupStatusBar();

        MediationManager.getBannerAd(this);

        initToolbar();
        initScrollView();

        initNotificationSwitch();
        initMusicSwitch();
        initSelectMusic();
        init();
        initSoundSwitch();
        initLongClickSwitchColorkSwitch();
        initColorProgressSwitch();
        initSmartZoomSwitch();
        initSubscriptionPlans();
    }

    private void initSubscriptionPlans() {
        // ✅ Step 1: Set default placeholder prices
        binding.monthlyPrice.setText("...");
        binding.yearlyPrice.setText("...");

        // ✅ Step 2: Get IAPManager instance
        IAPManager iapManager = IAPManager.getInstance(this);

        // ✅ Step 3: If details already loaded, set prices immediately
        setExistingPrices(iapManager);

        // ✅ Step 4: Listen for price updates from Play Store
        iapManager.addListener(new IAPManager.PurchaseListener() {
            @Override
            public void onProductDetailsLoaded(IAPManager.PlanType planType,
                                               ProductDetails details) {
                runOnUiThread(() -> {
                    String price = iapManager.getFormattedPrice(planType);
                    if (price == null) return;

                    switch (planType) {
                        case MONTHLY:
                            binding.monthlyPrice.setText(price);
                            Log.d("IAP", "Monthly price set: " + price);
                            break;

                        case YEARLY:
                            binding.yearlyPrice.setText(price);
                            Log.d("IAP", "Yearly price set: " + price);
                            break;
                    }

                    // Refresh bottom button text with new price
                    updatePlanSelection();
                });
            }

            @Override
            public void onPurchaseSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this,
                            "🎉 Premium activated! Ads removed.", Toast.LENGTH_LONG).show();
                    hidePremiumSection();
                });
            }

            @Override
            public void onPurchaseFailed(String error) {
                runOnUiThread(() ->
                        Toast.makeText(SettingsActivity.this,
                                error, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onPremiumStatusChecked(boolean isPremium) {
                runOnUiThread(() -> {
                    if (isPremium) {
                        hidePremiumSection();
                    }
                });
            }
        });

        iapManager.startConnection();

        // Plan selection clicks
        binding.monthlyLl.setOnClickListener(v -> {
            if (!isYearlySelected) return;
            isYearlySelected = false;
            updatePlanSelection();
        });

        binding.yearlyLl.setOnClickListener(v -> {
            if (isYearlySelected) return;
            isYearlySelected = true;
            updatePlanSelection();
        });

        // Purchase button
        binding.purchaseLl.setOnClickListener(v -> {
            if (isYearlySelected) {
                iapManager.purchaseYearly(this);
            } else {
                iapManager.purchaseMonthly(this);
            }
        });

        // Check existing premium status immediately on open
        if (com.colourmate.paintbynumber.ads.AppPreference.getBoolean(
                this, com.colourmate.paintbynumber.ads.Keys.KEY_IS_PREMIUM, false)) {
            hidePremiumSection();
        }

        // Set initial selection UI
        updatePlanSelection();
    }

    /**
     * Hides the subscription plan card once premium is active.
     * Finds the closest common parent of plan rows.
     */
    private void hidePremiumSection() {
        // The section has its own container. Previously this hid monthlyLl.getParent(), which is
        // only the row holding the two price chips — a premium user was still shown the heading,
        // the pitch and a live "Subscribe now" button underneath it.
        if (binding.llPremiumSection != null) {
            binding.llPremiumSection.setVisibility(View.GONE);
            return;
        }

        View parent = (View) binding.monthlyLl.getParent();
        if (parent != null) {
            parent.setVisibility(View.GONE);
        } else {
            // Fallback: hide individual elements
            binding.monthlyLl.setVisibility(View.GONE);
            binding.yearlyLl.setVisibility(View.GONE);
            binding.purchaseLl.setVisibility(View.GONE);
            binding.selectedPlanInfo.setVisibility(View.GONE);
        }
    }

    private void setExistingPrices(IAPManager iapManager) {

        // Monthly price
        String mPrice = iapManager.getFormattedPrice(IAPManager.PlanType.MONTHLY);
        if (mPrice != null) {
            binding.monthlyPrice.setText(mPrice);
        }

        // Yearly price
        String yPrice = iapManager.getFormattedPrice(IAPManager.PlanType.YEARLY);
        if (yPrice != null) {
            binding.yearlyPrice.setText(yPrice);
        }

        updateYearlyValue(iapManager);
        updatePlanSelection();      // prices arrived — the CTA text depends on them
    }

    /**
     * Spells out the yearly plan's per-month cost and what it saves against paying monthly, using
     * the real Play prices so it stays correct in every currency and after any price change.
     */
    private void updateYearlyValue(IAPManager iapManager) {
        if (binding.tvYearlySub == null) return;

        long yearly = iapManager.getPriceAmountMicros(IAPManager.PlanType.YEARLY);
        long monthly = iapManager.getPriceAmountMicros(IAPManager.PlanType.MONTHLY);
        if (yearly <= 0L) {
            binding.tvYearlySub.setVisibility(View.GONE);
            return;
        }

        String currency = iapManager.getPriceCurrencyCode(IAPManager.PlanType.YEARLY);
        StringBuilder text = new StringBuilder(formatMicros(yearly / 12L, currency)).append("/mo");

        long twelveMonths = monthly * 12L;

        // The saving is NOT appended here any more. The badge above the card already says
        // "Save 17%", and printing it again two lines below said one thing twice.
        binding.tvYearlySub.setText(text.toString());
        binding.tvYearlySub.setVisibility(View.VISIBLE);

        if (binding.tvSettingsSaveBadge != null && monthly > 0L && twelveMonths > yearly) {
            int percent = (int) Math.round(100.0 * (twelveMonths - yearly) / twelveMonths);
            binding.tvSettingsSaveBadge.setText(getString(R.string.premium_saving, percent));
        }
    }

    private String formatMicros(long micros, String currencyCode) {
        double amount = micros / 1_000_000.0;
        try {
            java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance();
            if (currencyCode != null && !currencyCode.isEmpty()) {
                nf.setCurrency(java.util.Currency.getInstance(currencyCode));
            }
            return nf.format(amount);
        } catch (Exception e) {
            return String.format(java.util.Locale.getDefault(), "%.2f", amount);
        }
    }

    private void updatePlanSelection() {
        IAPManager iap = IAPManager.getInstance(this);

        // cm_plan_chip_* rather than bg_plan_*: the latter are white-on-violet and are still worn
        // by the Daily streak chip, which sits on a gradient. Sharing them would have meant fixing
        // this card by breaking that one.
        binding.yearlyLl.setBackgroundResource(isYearlySelected
                ? R.drawable.cm_plan_chip_selected : R.drawable.cm_plan_chip_unselected);
        binding.monthlyLl.setBackgroundResource(isYearlySelected
                ? R.drawable.cm_plan_chip_unselected : R.drawable.cm_plan_chip_selected);

        // The button states what it is about to charge, rather than a bare "Subscribe now" that
        // makes the buyer look back up at the cards to remember which plan is selected. The small
        // print underneath stays constant, so the two never disagree.
        IAPManager.PlanType selected = isYearlySelected
                ? IAPManager.PlanType.YEARLY : IAPManager.PlanType.MONTHLY;
        String price = iap.getFormattedPrice(selected);

        if (price == null || price.isEmpty()) {
            binding.price.setText(R.string.settings_subscribe);
        } else {
            binding.price.setText(getString(isYearlySelected
                    ? R.string.premium_cta_yearly : R.string.premium_cta_monthly, price));
        }

        binding.selectedPlanInfo.setText(R.string.premium_terms);
    }

    private void init() {

        binding.btnFavorites.setOnClickListener(v -> {
            startActivity(new Intent(this, FavoritesActivity.class));
            overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
        });
        binding.btnGameGuide.setOnClickListener(v -> {
            startActivity(new Intent(this, ToolsInfoActivity.class));
            overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
        });
        binding.btnRate.setOnClickListener(v -> Utils.rateApp(this));
        binding.btnShare.setOnClickListener(v -> Utils.shareApp(this));
        binding.btnPrivacy.setOnClickListener(v -> Utils.openPolicy(this));
        binding.btnFeedback.setOnClickListener(v -> sendFeedback());
    }

    private void rateApp() {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            // Play Store app not installed → open browser
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName()));
            startActivity(intent);
        }
    }

    private void shareApp() {
        String shareText = "Check out this amazing app!\n\n" +
                "https://play.google.com/store/apps/details?id=" + getPackageName();

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);

        startActivity(Intent.createChooser(intent, "Share App via"));
    }

    private void openPrivacyPolicy() {
        String privacyUrl = "https://your-privacy-policy-link.com";

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl));
        startActivity(intent);
    }

    private void sendFeedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // Only email apps

        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"admin@gyandatabit.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "App Feedback");
        intent.putExtra(Intent.EXTRA_TEXT,
                "App Version: " + getAppVersion() + "\n" +
                        "Device: " + android.os.Build.MODEL + "\n" +
                        "Android Version: " + android.os.Build.VERSION.RELEASE + "\n\n" +
                        "Write your feedback here:");

        try {
            startActivity(Intent.createChooser(intent, "Send Feedback"));
        } catch (Exception e) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    private String getAppVersion() {
        try {
            return getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private OnBackPressedCallback onBackPressedCallback;

    private void setupBackPressHandler() {
        onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    private void handleBackPress() {
        MediationManager.showBackInterstitial(this, () -> {
            // Disable callback temporarily to allow actual back navigation
            onBackPressedCallback.setEnabled(false);
            getOnBackPressedDispatcher().onBackPressed();
            onBackPressedCallback.setEnabled(true);
        });
    }


    /**
     * Runs the screen's own background up behind the status bar.
     *
     * <p>It used to paint the bar a flat white while the screen itself is the lilac paper ground,
     * leaving a white strip across the top. A flat colour cannot match anyway — the paper is a
     * gradient with a violet bloom in the corner — so the bar is made transparent and the layout
     * draws through it instead. The toolbar then takes the status-bar height as padding so the
     * title and back button are not sitting underneath the clock.
     *
     * <p>Icons are set dark, which is what a light ground needs.
     */
    private void setupStatusBar() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);

        final View toolbar = binding.toolbar;
        final int basePaddingTop = toolbar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), basePaddingTop + top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        // The ad strip sits at the very bottom, so it needs the navigation-bar inset for the same
        // reason — otherwise the gesture bar overlaps it.
        final View ads = binding.llAds;
        if (ads != null) {
            final int baseBottom = ads.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(ads, (v, insets) -> {
                int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                        v.getPaddingRight(), baseBottom + bottom);
                return insets;
            });
        }
    }

    private void initToolbar() {
        setupBackPressHandler();
        binding.RlBack.setOnClickListener(v -> handleBackPress());
    }

    private void initScrollView() {
        OverScrollDecoratorHelper.setUpOverScroll(binding.svSettings);
        binding.llGeneral.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void initNotificationSwitch() {
        FlexibleSwitch switchButton = binding.sbBtn;
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        switchButton.setChecked(prefs.getBoolean("notifications", true));

        switchButton.addOnStatusChangedListener(checked ->
                prefs.edit().putBoolean("notifications", checked).apply());

        binding.llGeneralNotifications.setOnClickListener(v -> {
            boolean newState = !switchButton.isChecked();
            switchButton.setChecked(newState);
            prefs.edit().putBoolean("notifications", newState).apply();
        });
    }

    private void initLongClickSwitchColorkSwitch() {
        FlexibleSwitch switchButton = binding.sbBtnLongClick;
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        switchButton.setChecked(prefs.getBoolean("long_click", true));

        switchButton.addOnStatusChangedListener(checked ->
                prefs.edit().putBoolean("long_click", checked).apply());

        binding.llGeneralLongClick.setOnClickListener(v -> {
            boolean newState = !switchButton.isChecked();
            switchButton.setChecked(newState);
            prefs.edit().putBoolean("long_click", newState).apply();
        });
    }

    private void initMusicSwitch() {
        FlexibleSwitch switchButton = binding.sbBtnMusic;
        switchButton.setChecked(GameSaver.isMusicEnabled(this));

        switchButton.addOnStatusChangedListener(checked -> {
            GameSaver.setMusicEnabled(this, checked);
            if (checked) {
                SoundUtils.playBackgroundMusic(GlobalApplication.getAppContext());
            } else {
                SoundUtils.stopBackgroundMusic();
            }
        });

        binding.llGeneralMusic.setOnClickListener(v -> {
            boolean newState = !switchButton.isChecked();
            switchButton.setChecked(newState);
            GameSaver.setMusicEnabled(this, newState);
            if (newState) {
                SoundUtils.playBackgroundMusic(GlobalApplication.getAppContext());
            } else {
                SoundUtils.stopBackgroundMusic();
            }
        });
    }

    private void initSoundSwitch() {
        FlexibleSwitch switchButton = binding.sbBtnSound;
        switchButton.setChecked(GameSaver.isSoundEnabled(this));

        switchButton.addOnStatusChangedListener(checked ->
                GameSaver.setSoundEnabled(this, checked));

        binding.llGeneralSound.setOnClickListener(v -> {
            boolean newState = !switchButton.isChecked();
            switchButton.setChecked(newState);
            GameSaver.setSoundEnabled(this, newState);
        });
    }

    private void initColorProgressSwitch() {
        FlexibleSwitch switchButton = binding.sbBtnColorProgress;
        switchButton.setChecked(GameSaver.isColorProgressEnabled(this));

        switchButton.addOnStatusChangedListener(checked ->
                GameSaver.setColorProgressEnabled(this, checked));

        binding.llGeneralColorProgress.setOnClickListener(v -> {
            boolean newState = !switchButton.isChecked();
            switchButton.setChecked(newState);
            GameSaver.setColorProgressEnabled(this, newState);
        });
    }



    private void initSmartZoomSwitch() {
        FlexibleSwitch switchButton = binding.sbBtnSmartZoom;
        switchButton.setChecked(GameSaver.isSmartZoomEnabled(this));

        switchButton.addOnStatusChangedListener(checked ->
                GameSaver.setSmartZoomEnabled(this, checked));

        binding.llGeneralSmartZoom.setOnClickListener(v -> {
            boolean newState = !switchButton.isChecked();
            switchButton.setChecked(newState);
            GameSaver.setSmartZoomEnabled(this, newState);
        });
    }


    private void initSelectMusic() {
        downloadServerMusic();
        binding.llSelectMusic.setOnClickListener(v -> showMusicDialog());
    }

    private void showMusicDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.dial_glcolor_sound);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        dialog.getWindow().setLayout(metrics.widthPixels, metrics.heightPixels);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        dialog.show();

        SoundUtils.stopBackgroundMusic();

        ListView musicList = dialog.findViewById(R.id.listSoundGL);
        ImageView musicToggle = dialog.findViewById(R.id.tvSoundGL);
        LinearLayout libraryButton = dialog.findViewById(R.id.llMusiclibGL);
        View progressView = dialog.findViewById(R.id.ivProgressGL);

        musicToggle.setImageResource(GameSaver.isMusicEnabled(this)
                ? R.drawable.music_on : R.drawable.music_off);

        if (serverMusicList.isEmpty()) {
            progressView.setVisibility(View.VISIBLE);
        } else {
            musicList.setAdapter(new OnlineSongAdapter(this, serverMusicList));
            progressView.setVisibility(View.GONE);
        }

        dialog.findViewById(R.id.ivClose).setOnClickListener(v -> {
            SoundUtils.stopSong();
            dialog.dismiss();
            if (GameSaver.isMusicEnabled(this)) {
                SoundUtils.playBackgroundMusic(GlobalApplication.getAppContext());
            }
        });

        musicToggle.setOnClickListener(v -> {
            SoundUtils.stopSong();
            boolean newState = !GameSaver.isMusicEnabled(this);
            GameSaver.setMusicEnabled(SettingsActivity.this, newState);
            binding.sbBtnMusic.setChecked(newState);
            musicToggle.setImageResource(newState ? R.drawable.music_on : R.drawable.music_off);
            if (newState) {
                SoundUtils.playBackgroundMusic(GlobalApplication.getAppContext());
            } else {
                SoundUtils.stopBackgroundMusic();
            }
        });

        libraryButton.setOnClickListener(v -> showLocalMusicDialog());
    }

    private void showLocalMusicDialog() {
        SoundUtils.stopSong();

        Dialog dialog = new Dialog(this);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.dial_glcolor_music);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        dialog.getWindow().setLayout(metrics.widthPixels, metrics.heightPixels);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        dialog.show();

        ArrayList<SongModel> deviceMusicList = new ArrayList<>();
        loadDeviceMusic(deviceMusicList);

        ListView musicList = dialog.findViewById(R.id.listMusiclibGL);
        musicList.setAdapter(new LocalSongsAdapter(this, deviceMusicList));

        dialog.findViewById(R.id.ivClose1GL).setOnClickListener(v -> {
            dialog.dismiss();
            SoundUtils.stopSong();
        });
    }

    private void downloadServerMusic() {
        serverMusicList.clear();

        StringRequest request = new StringRequest(Manager.serverMusic,
                response -> {
                    try {
                        JSONArray musicArray = new JSONObject(response).getJSONArray("data");
                        for (int i = 0; i < musicArray.length(); i++) {
                            String musicUrl = musicArray.getString(i);
                            String filename = new File(musicUrl).getName();
                            serverMusicList.add(new SongModel(filename, musicUrl));
                        }
                    } catch (JSONException ignored) {
                    }
                },
                error -> {
                }
        );

        Volley.newRequestQueue(this).add(request);
    }

    private void loadDeviceMusic(ArrayList<SongModel> deviceMusicList) {
        if (!checkStoragePermission()) return;

        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(musicUri, projection, selection, null, sortOrder);
            if (cursor == null || !cursor.moveToFirst()) {
                Toast.makeText(this, "No music found on device", Toast.LENGTH_LONG).show();
                return;
            }

            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            deviceMusicList.clear();

            do {
                String title = cursor.getString(titleCol);
                String path = cursor.getString(dataCol);
                if (title != null && path != null) {
                    deviceMusicList.add(new SongModel(title, path));
                }
            } while (cursor.moveToNext());

        } catch (Exception e) {
            Toast.makeText(this, "Error loading music: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_AUDIO}, PERMISSION_READ_STORAGE);
            return false;
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_READ_STORAGE);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied. Cannot access music files.",
                        Toast.LENGTH_LONG).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !shouldShowRequestPermissionRationale(permissions[0])) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Required")
                            .setMessage("Please enable storage permission in app settings to access music files.")
                            .setPositiveButton("Go to Settings", (d, w) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.fromParts("package", getPackageName(), null));
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        SoundUtils.stopSong();
        super.onDestroy();
    }
}
