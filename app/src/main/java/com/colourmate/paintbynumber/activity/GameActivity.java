package com.colourmate.paintbynumber.activity;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.adapter.ColorListAdapter;
import com.colourmate.paintbynumber.ads.AdController;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.ads.RewardedAdListener;
import com.colourmate.paintbynumber.databinding.ActivityGameBinding;
import com.colourmate.paintbynumber.model.game.ColorItem;
import com.colourmate.paintbynumber.model.game.GameView;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.util.AchievementManager;
import com.colourmate.paintbynumber.util.BackgroundThemeManager;
import com.colourmate.paintbynumber.util.DataManager;
import com.colourmate.paintbynumber.util.GameSaver;
import com.colourmate.paintbynumber.util.LogUtils;
import com.colourmate.paintbynumber.util.SoundUtils;
import com.colourmate.paintbynumber.util.TimeLapseRecorder;
import com.colourmate.paintbynumber.viewmodel.GameViewModel;
import com.colourmate.paintbynumber.views.WrapContentLinearLayoutManager;
import androidx.core.content.res.ResourcesCompat;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.github.florent37.viewanimator.ViewAnimator;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.krtkush.lineartimer.LinearTimer;
import io.github.krtkush.lineartimer.LinearTimerView;

public class GameActivity extends AppCompatActivity implements LinearTimer.TimerListener {

    private static final String EXTRA_LEVEL = "extraLevel";
    private static final String PREF_TOOL_TIPS_SHOWN = "game_tool_tips_shown";

    // ViewModel
    private GameViewModel viewModel;

    // Binding
    private ActivityGameBinding binding;

    // Views (only those needing direct manipulation)
    private GameView gameView;
    private RecyclerView colorsRecyclerView;
    private LinearTimerView timerView;
    private RelativeLayout timerLayout;
    private LinearTimer linearTimer;
    private ObjectAnimator progressAnimator;
    private OnBackPressedCallback onBackPressedCallback;
    private boolean isBackHandlingStarted = false;
    private boolean isZenAnimating = false;
    private ObjectAnimator zenPulseAnimator;

    // Tool buttons
    private View btnToolBrush, btnToolBomb, btnToolBucket, btnToolLoupe;
    private View btnToolRadar, btnToolSurge, btnToolWand, btnToolMagnet;

    public static Intent newIntent(Context context, Level level) {
        Intent intent = new Intent(context, GameActivity.class);
        intent.putExtra(EXTRA_LEVEL, level);
        return intent;
    }

    /**
     * Set by the Daily Challenge screen (a yyyy-MM-dd key). Finishing the picture then records
     * that day as done — before 2026-10-14 nothing ever called
     * {@code DailyChallengeManager.markTodayCompleted}, so the streak could never grow.
     */
    public static final String EXTRA_DAILY_DATE = "daily_date";

    public static void restartApplication(Context context) {
        try {
            Intent launchIntent = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());
            if (launchIntent != null) {
                Intent restartIntent = Intent.makeRestartActivityTask(
                        launchIntent.getComponent());
                context.startActivity(restartIntent);
                Runtime.getRuntime().exit(0);
            }
        } catch (Exception ignored) {
        }
    }

    // ─── Lifecycle ───

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        hideStatusBar();

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(GameViewModel.class);

        Level level = getIntent().getParcelableExtra(EXTRA_LEVEL);
        viewModel.initialize(level);

        initializeViews();
        setupBackNavigation();
        setupGameView();
        observeViewModel();

        if (AdController.shouldShowAds(this)) {
            MediationManager.getBannerAd(this);
        }

        viewModel.requestLevelData();

        if (GameSaver.isMusicEnabled(this)) {
            SoundUtils.playBackgroundMusic(GlobalApplication.getAppContext());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        hideStatusBar();
        if (GameSaver.isMusicEnabled(this)) {
            SoundUtils.playBackgroundMusic(GlobalApplication.getAppContext());
        } else {
            SoundUtils.stopBackgroundMusic();
        }
        viewModel.resumeRecording();
    }

    @Override
    public void onPause() {
        super.onPause();
        SoundUtils.stopBackgroundMusic();
        stopLinearTimerSafely();
        viewModel.pauseRecording();
    }

    @Override
    public void onDestroy() {
        try {
            if (progressAnimator != null && progressAnimator.isRunning()) {
                progressAnimator.cancel();
                progressAnimator = null;
            }
        } catch (Exception ignored) {
        }

        try {
            SoundUtils.stopBackgroundMusic();
        } catch (Exception ignored) {
        }
        try {
            if (gameView != null)
                gameView.cleanup();
        } catch (Exception ignored) {
        }

        gameView = null;
        colorsRecyclerView = null;
        linearTimer = null;
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            hideStatusBar();
    }

    // ─── Initialization ───

    private void initializeViews() {
        gameView = binding.gameView;
        colorsRecyclerView = binding.recyclerView;
        timerView = binding.linearTimer;
        timerLayout = binding.rlTimerView;

        binding.bookloading.setVisibility(VISIBLE);
        loadThumbnailPreview();
        hideGameUIDuringLoading();

        // Zen mode button
        binding.btnZenMode.setOnClickListener(v -> viewModel.toggleZenMode());
        // Background theme button
        binding.btnBgTheme.setOnClickListener(v -> viewModel.onBgThemeClicked());
        // Auto-fill button
        binding.btnAutoFill.setOnClickListener(v -> viewModel.toggleAutoFill());

        // Advanced tools
        initializeAdvancedTools();

        // Game view callbacks
        gameView.setTimeLapseRecorder(viewModel.getTimeLapseRecorder());
        gameView.setOnGameFinished(() -> viewModel.onGameFinished());
        gameView.setOnColorProgress(viewModel::onColorProgress);
        gameView.setOnColorFinished(viewModel::onColorFinished);
        gameView.setOnToolDeactivated(() -> viewModel.resetTool());
    }

    private void initializeAdvancedTools() {
        View icGameTool = findViewById(R.id.ic_game_tool);
        View ivTools = findViewById(R.id.IVTools);

        if (icGameTool != null && ivTools != null) {
            icGameTool.setVisibility(GONE);
            ivTools.setOnClickListener(v -> {
                if (icGameTool.getVisibility() == VISIBLE) {
                    com.github.florent37.viewanimator.ViewAnimator.animate(icGameTool)
                            .alpha(1, 0) // Alpha 1 to 0 for Hide
                            .translationX(0, 30) // Subtle slide out
                            .duration(300)
                            .onStop(() -> icGameTool.setVisibility(GONE))
                            .start();
                } else {
                    icGameTool.setVisibility(VISIBLE);
                    com.github.florent37.viewanimator.ViewAnimator.animate(icGameTool)
                            .alpha(0, 1) // Alpha 0 to 1 for Show
                            .translationX(30, 0)
                            .duration(300)
                            .start();
                }
            });
        }

        btnToolBrush = findViewById(R.id.btn_tool_brush);
        btnToolBomb = findViewById(R.id.btn_tool_bomb);
        btnToolBucket = findViewById(R.id.btn_tool_bucket);
        btnToolLoupe = findViewById(R.id.btn_tool_loupe);
        btnToolRadar = findViewById(R.id.btn_tool_radar);
        btnToolSurge = findViewById(R.id.btn_tool_surge);
        btnToolWand = findViewById(R.id.btn_tool_wand);
        btnToolMagnet = findViewById(R.id.btn_tool_magnet);

        setupToolButton(btnToolBrush, GameView.GameTool.BRUSH);
        setupToolButton(btnToolBomb, GameView.GameTool.BOMB);
        setupToolButton(btnToolBucket, GameView.GameTool.BUCKET);
        setupToolButton(btnToolLoupe, GameView.GameTool.LOUPE);
        setupToolButton(btnToolRadar, GameView.GameTool.RADAR);
        setupToolButton(btnToolSurge, GameView.GameTool.SURGE);
        setupToolButton(btnToolWand, GameView.GameTool.MAGIC_WAND);
        setupToolButton(btnToolMagnet, GameView.GameTool.MAGNET);

        if (btnToolBrush != null) {
            btnToolBrush.setOnLongClickListener(v -> {
                viewModel.onBrushLongClick();
                return true;
            });
        }
    }

    private void setupToolButton(View btn, GameView.GameTool tool) {
        if (btn != null) {
            btn.setOnClickListener(v -> viewModel.toggleTool(tool));
        }
    }

    private void setupGameView() {
        gameView.setOnColorFinished(color -> viewModel.onColorFinished(color));
        gameView.setOnColorProgress((color, progress) -> viewModel.onColorProgress(color, progress));
        gameView.setOnToolDeactivated(() -> viewModel.resetTool());
        gameView.initVibrator();

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        gameView.setEnableLongClickColorSelect(prefs.getBoolean("long_click", true));
        gameView.setSmartZoomEnabled(GameSaver.isSmartZoomEnabled(this));
    }

    private void setupBackNavigation() {
        onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                performBackAction();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    // ─── STEP 3: Add saveCompletedImageAndGetFile() method ───

    /**
     * Saves the completed image and returns the File.
     * Returns null if save failed.
     */
    private File saveCompletedImageAndGetFile() {
        try {
            DataManager.getInstance().finishLevel(viewModel.getCurrentLevel());
            Level done = viewModel.getCurrentLevel();
            if (done != null && com.colourmate.paintbynumber.util.CoinManager
                    .rewardCompletion(this, done.getFileName()) > 0) {
                runOnUiThread(() -> android.widget.Toast.makeText(this, "Picture finished: +"
                        + com.colourmate.paintbynumber.util.CoinManager.COMPLETE_COINS + " coins 🪙",
                        android.widget.Toast.LENGTH_SHORT).show());
            }
            String dailyDate = getIntent() != null ? getIntent().getStringExtra(EXTRA_DAILY_DATE) : null;
            if (dailyDate != null) {
                com.colourmate.paintbynumber.util.DailyChallengeManager.markCompleted(this, dailyDate);
            }
            Bitmap completedImage = gameView.getWinPreview();

            if (completedImage == null) {
                LogUtils.e("GameActivity", "getWinPreview returned null");
                return null;
            }

            Level level = viewModel.getCurrentLevel();
            String[] pathParts = level.getFileName().split("/");
            String filename = pathParts[pathParts.length - 1];

            File saveDirectory = new File(getFilesDir(), "MyColorByNumber");
            saveDirectory.mkdirs();

            File imageFile = new File(saveDirectory, filename + ".png");
            FileOutputStream outputStream = new FileOutputStream(imageFile);
            completedImage.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();

            // Track achievements
            AchievementManager.recordImageCompleted(this);
            long elapsed = (System.currentTimeMillis() - viewModel.getSessionStartTime()) / 1000;
            AchievementManager.recordTimeSpent(this, (int) elapsed);

            // Save time-lapse
            TimeLapseRecorder recorder = viewModel.getTimeLapseRecorder();
            if (recorder != null) {
                recorder.stopRecording();
                recorder.save(this);
            }

            LogUtils.d("GameActivity", "Image saved: " + imageFile.getAbsolutePath());
            return imageFile;

        } catch (Exception e) {
            LogUtils.e("GameActivity", "Error saving completed image", e);
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            return null;
        } catch (OutOfMemoryError e) {
            LogUtils.e("GameActivity", "OOM saving image", e);
            Toast.makeText(this, R.string.error_out_of_memory, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    // ─── ViewModel Observers ───

    private void observeViewModel() {
        // Loading state
        viewModel.getLoadingState().observe(this, state -> {
            switch (state) {
                case LOADED:
                    // Handled by bitmapsReady
                    break;
                case ERROR:
                    finish();
                    break;
                case MEMORY_ERROR:
                    binding.llMemoryError.setVisibility(VISIBLE);
                    break;
            }
        });

        // Nothing to download means there is no real progress to report, so the placeholder
        // animation should be a brief "opening" sweep rather than a four-second crawl.
        viewModel.getDownloadRequired().observe(this, required -> {
            if (Boolean.TRUE.equals(required)) {
                startFakeProgress();
            } else {
                startPreparingProgress();
            }
        });

        // Download progress
        viewModel.getDownloadProgress().observe(this, progress -> {
            if (progress == null) return;
            if (progressAnimator != null && progressAnimator.isRunning()) {
                progressAnimator.cancel();
            }
            // A progress bar that slides backwards reads as broken. Real download progress can
            // arrive below wherever the placeholder animation had reached, so only ever move on.
            if (progress > binding.pbLoadingHorizontal.getProgress()) {
                binding.pbLoadingHorizontal.setProgress(progress);
            }
        });

        viewModel.getDownloadProgressText().observe(this, text -> binding.tvProgressBar.setText(text));

        // Bitmaps ready
        viewModel.getBitmapsReady().observe(this, pair -> {
            if (isFinishing() || isDestroyed()) {
                if (pair.baseBitmap != null)
                    pair.baseBitmap.recycle();
                if (pair.coloredBitmap != null)
                    pair.coloredBitmap.recycle();
                return;
            }
            // Level init is async now — keep the loader up until the level is
            // actually drawable, otherwise the palette would be read empty.
            gameView.setLevel(viewModel.getCurrentLevel(),
                    pair.baseBitmap, pair.coloredBitmap, () -> {
                        if (isFinishing() || isDestroyed())
                            return;
                        finishLoadingProgress();
                        initializeColorPalette();
                        gameView.invalidate();
                    });
        });

        // Selected color
        viewModel.getSelectedColor().observe(this, color -> {
            if (gameView != null)
                gameView.selectColor(color);
            ColorListAdapter adapter = getColorAdapter();
            if (adapter != null) {
                adapter.setSelectedColor(color);
                updateActiveColorInViews(color);
            }
        });

        // Color progress
        viewModel.getColorProgress().observe(this, update -> {
            if (isFinishing() || isDestroyed())
                return;
            ColorListAdapter adapter = getColorAdapter();
            if (adapter == null)
                return;

            ColorItem colorItem = adapter.getColorItemByColor(update.color);
            if (colorItem != null && !colorItem.isFinished()) {
                int pos = adapter.getPositionOfColor(update.color);
                if (pos != -1) {
                    RecyclerView.ViewHolder vh = colorsRecyclerView.findViewHolderForAdapterPosition(pos);
                    if (vh instanceof ColorListAdapter.ColorViewHolder) {
                        ((ColorListAdapter.ColorViewHolder) vh).updateProgress(update.progress);
                    }
                }
            }
        });

        // Color finished
        viewModel.getColorFinished().observe(this, color -> {
            if (isFinishing() || isDestroyed())
                return;
            ColorListAdapter adapter = getColorAdapter();
            if (adapter == null)
                return;

            int pos = adapter.getPositionOfColor(color);
            adapter.finishColor(color);

            RecyclerView.ViewHolder vh = colorsRecyclerView.findViewHolderForAdapterPosition(pos);
            if (vh instanceof ColorListAdapter.ColorViewHolder) {
                ((ColorListAdapter.ColorViewHolder) vh).playFinishAnimation(() -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    adapter.removeColor(color);

                    int nextColor = adapter.getNextUnfinishedColor();
                    if (nextColor != -1) {
                        viewModel.onColorClicked(
                                adapter.getColorItemByColor(nextColor));
                    }

                    if (adapter.isGameFinished()) {
                        saveCompletedImage();
                    }
                });
            }
        });

        // Game finished
        // ✅ NEW CODE - REPLACE WITH THIS:
        viewModel.getGameFinished().observe(this, finished -> {
            if (!finished)
                return;

            // Daily challenge check
            boolean challengeActive = com.colourmate.paintbynumber.util.ZenChallengeManager
                    .isChallengeActive(this);
            if (challengeActive) {
                com.colourmate.paintbynumber.util.ZenChallengeManager
                        .completeChallenge(this);
                Toast.makeText(this,
                        R.string.daily_zen_challenge_completed,
                        Toast.LENGTH_LONG).show();
            }

            // ✅ Save completed image FIRST (needed for both buttons)
            handleGameFinished();
        });

        // Zen mode
        viewModel.getZenMode().observe(this, this::applyZenMode);

        // Auto-fill
        viewModel.getAutoFillMode().observe(this, enabled -> {
            if (gameView != null)
                gameView.setAutoFillMode(enabled);
            if (binding.btnAutoFill != null) {
                binding.btnAutoFill.setSelected(enabled);
                binding.btnAutoFill.animate().scaleX(1.2f).scaleY(1.2f)
                        .setDuration(100)
                        .withEndAction(() -> binding.btnAutoFill.animate()
                                .scaleX(1f).scaleY(1f).setDuration(100).start())
                        .start();
            }
        });

        // Hint counter
        viewModel.getHintCounterText().observe(this, text -> binding.hintCounter.setText(text));

        // Hint animation
        viewModel.getHintAnimation().observe(this, type -> {
            if (type == null)
                return;
            switch (type) {
                case SCALE:
                    animateHintCounterScale();
                    break;
                case BOUNCE:
                    animateHintCounterBounce();
                    break;
                case SLIDE:
                    animateHintCounterSlide();
                    break;
                case ROTATE:
                    animateHintCounterRotate();
                    break;
            }
        });

        // Toast
        viewModel.getToastMessage().observe(this, msg -> {
            if (msg != null)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        // Slow network
        viewModel.getShowSlowNetwork().observe(this, show -> binding.tvSlowNet.setVisibility(show ? VISIBLE : GONE));

        // Memory error
        viewModel.getShowMemoryError().observe(this, show -> {
            if (show)
                binding.llMemoryError.setVisibility(VISIBLE);
        });

        // Timer active
        viewModel.getTimerActive().observe(this, active -> timerLayout.setVisibility(active ? VISIBLE : GONE));

        // Start timer event
        viewModel.getStartTimerEvent().observe(this, v -> startLinearTimer());

        // Active theme
        viewModel.getActiveTheme().observe(this, theme -> {
            binding.getRoot().setBackgroundColor(theme.primaryColor);
            if (gameView != null)
                gameView.setBackgroundColor(theme.secondaryColor);
        });

        // Active tool
        viewModel.getActiveTool().observe(this, tool -> {
            deselectAllToolButtons();
            View toolBtn = getToolButton(tool);
            if (toolBtn != null) {
                toolBtn.setSelected(true);
                toolBtn.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120)
                        .withEndAction(() -> toolBtn.animate()
                                .scaleX(1f).scaleY(1f).setDuration(120).start())
                        .start();
            }
            if (gameView != null) {
                // If the user isn't premium, they get 4 uses per ad (Bucket, Bomb, Brush)
                int uses = (tool != GameView.GameTool.NONE && !viewModel.isPremium()) ? 4 : -1;
                gameView.setActiveTool(tool, uses);
            }
        });

        // Brush radius
        viewModel.getBrushRadius().observe(this, radius -> {
            if (gameView != null)
                gameView.setBrushRadius(radius);
        });

        // Background theme dialog
        viewModel.getShowBgThemeDialog().observe(this, v -> showBgThemeDialog());

        // Brush settings dialog
        viewModel.getShowBrushSettingsDialog().observe(this, v -> showBrushSettingsDialog());

        // Interstitial ad
        viewModel.getShowInterstitialAd().observe(this, v -> {
            // Used for timer hint
        });

        // Back interstitial
        viewModel.getShowBackInterstitial().observe(this, v -> {
            try {
                MediationManager.showBackInterstitial(this, () -> runOnUiThread(this::safeFinish));
            } catch (Exception e) {
                safeFinish();
            }
        });

        // Saved image result
        // viewModel.getSavedImageResult().observe(this, result ->
        // navigateToShareScreen(result.imagePath, result.beforeImagePath));

        // Tool Ad Request
        viewModel.getRequestToolAd().observe(this, tool -> MediationManager.showRewardedAd(this, new RewardedAdListener() {
            @Override
            public void onRewardGranted() {
                viewModel.onToolAdWatched(tool);
            }

            @Override
            public void onRewardNull() {

            }

            @Override
            public void onRewardProgress() {
            }

            @Override
            public void onVideoSkipped() {
            }
        }));

        viewModel.getRequestAutoFillAd().observe(this, v -> MediationManager.showRewardedAd(this, new RewardedAdListener() {
            @Override
            public void onRewardGranted() {
                viewModel.onAutoFillAdWatched();
            }

            @Override
            public void onRewardNull() {

            }

            @Override
            public void onRewardProgress() {
            }

            @Override
            public void onVideoSkipped() {
            }
        }));

        viewModel.getRequestZenModeAd().observe(this, v -> MediationManager.showRewardedAd(this, new RewardedAdListener() {
            @Override
            public void onRewardGranted() {
                viewModel.onZenModeAdWatched();
            }

            @Override
            public void onRewardNull() {

            }

            @Override
            public void onRewardProgress() {
            }

            @Override
            public void onVideoSkipped() {
            }
        }));

        viewModel.getRequestBgThemeAd().observe(this, v -> MediationManager.showRewardedAd(this, new RewardedAdListener() {
            @Override
            public void onRewardGranted() {
                viewModel.onBgThemeAdWatched();
            }

            @Override
            public void onRewardNull() {

            }

            @Override
            public void onRewardProgress() {
            }

            @Override
            public void onVideoSkipped() {
            }
        }));

        viewModel.getRequestHintAd().observe(this, v -> {
            // Coins first (2026-10-14): spend from the purse when it can cover a hint; the
            // rewarded video is the fallback for an empty purse, exactly as before.
            if (com.colourmate.paintbynumber.util.CoinManager.trySpend(this,
                    com.colourmate.paintbynumber.util.CoinManager.HINT_COST)) {
                viewModel.onHintAdWatched();
                return;
            }
            MediationManager.showRewardedAd(this, new RewardedAdListener() {
            @Override
            public void onRewardGranted() {
                viewModel.onHintAdWatched();
            }

            @Override
            public void onRewardNull() {

            }

            @Override
            public void onRewardProgress() {
            }

            @Override
            public void onVideoSkipped() {
            }
            });
        });

        // Animate color position
        viewModel.getAnimateColorPosition().observe(this, this::animateColorItem);
    }

    // ─── STEP 2: Add handleGameFinished() method ───

    /**
     * Saves the completed image first, then shows the finish dialog.
     * Both dialog buttons eventually navigate to Share Screen.
     */
    private void handleGameFinished() {
        // Save image and get the file
        final File savedImageFile = saveCompletedImageAndGetFile();

        if (savedImageFile == null) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // dialog.dismiss();
        navigateToShareScreen(savedImageFile);

        // new androidx.appcompat.app.AlertDialog.Builder(this)
        // .setTitle(R.string.masterpiece_finished_title)
        // .setMessage(R.string.masterpiece_finished_message)
        //
        // // ✅ Finish → Navigate to Share Screen
        // .setPositiveButton(R.string.btn_finish, (dialog, which) -> {
        // dialog.dismiss();
        // navigateToShareScreen(savedImageFile);
        // })
        //
        // // ✅ Timelapse → Show Replay → Close → Navigate to Share Screen
        // .setNeutralButton(R.string.btn_timelapse, (dialog, which) -> {
        // dialog.dismiss();
        // showTimeLapseReplay(savedImageFile);
        // })
        //
        // .setCancelable(false)
        // .show();
    }

    // ─── Color Palette ───

    private final ColorListAdapter.ColorClickListener colorClickListener = colorItem -> {
        if (!colorItem.isFinished()) {
            viewModel.onColorClicked(colorItem);
        }
    };

    private void initializeColorPalette() {
        List<ColorItem> colors = gameView.getColors();
        if (colors == null || colors.isEmpty()) {
            finish();
            return;
        }

        ColorListAdapter adapter = new ColorListAdapter(this, colors);
        adapter.setColorClickListener(colorClickListener);

        // Optimizations for smooth scrolling
        colorsRecyclerView.setItemViewCacheSize(20);
        colorsRecyclerView.setDrawingCacheEnabled(true);
        colorsRecyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        colorsRecyclerView.setLayoutManager(
                new WrapContentLinearLayoutManager(this, 0, false));
        colorsRecyclerView.setAdapter(adapter);
        adapter.setList(adapter.checkAllColor(colors));

        // Center on first color
        colorsRecyclerView.postDelayed(() -> {
            if (!isFinishing() && !colors.isEmpty()) {
                centerColorOnScreen(0);
            }
        }, 600);

        // Optimize overdraw
        getWindow().setBackgroundDrawable(null);
    }

    private void centerColorOnScreen(int position) {
        if (colorsRecyclerView == null)
            return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int itemWidth = (int) (getResources().getDisplayMetrics().density * 70); // Estimating color_item width
        int offset = (screenWidth / 2) - (itemWidth / 2);

        androidx.recyclerview.widget.LinearLayoutManager lm = (androidx.recyclerview.widget.LinearLayoutManager) colorsRecyclerView
                .getLayoutManager();
        if (lm != null) {
            lm.scrollToPositionWithOffset(position, offset);
        }
    }

    private ColorListAdapter getColorAdapter() {
        if (colorsRecyclerView == null)
            return null;
        return (ColorListAdapter) colorsRecyclerView.getAdapter();
    }

    // ─── Long-press color selection (called from GameView) ───

    public void onLongClickColorSelect(int color) {
        LogUtils.d("GameActivity", "onLongClickColorSelect: " + color);
        viewModel.onLongClickColorSelect(color);

        ColorListAdapter adapter = getColorAdapter();
        if (adapter == null)
            return;

        ColorItem colorItem = adapter.getColorItemByColor(color);
        if (colorItem == null || colorItem.isFinished())
            return;

        adapter.notifyDataSetChanged();

        int position = adapter.getPositionOfColor(color);
        if (position != -1) {
            colorsRecyclerView.post(() -> {
                colorsRecyclerView.smoothScrollToPosition(position);
                colorsRecyclerView.postDelayed(
                        () -> animateColorItem(position), 300);
            });
        }
    }

    // ─── UI Updates ───

    private void updateActiveColorInViews(int color) {
        if (colorsRecyclerView == null)
            return;
        for (int i = 0; i < colorsRecyclerView.getChildCount(); i++) {
            ColorListAdapter.ColorViewHolder holder = (ColorListAdapter.ColorViewHolder) colorsRecyclerView
                    .getChildViewHolder(colorsRecyclerView.getChildAt(i));
            holder.updateSelectedColor(color);
        }
    }

    private void animateColorItem(int position) {
        try {
            RecyclerView.ViewHolder holder = colorsRecyclerView.findViewHolderForAdapterPosition(position);

            if (holder == null) {
                ColorListAdapter adapter = getColorAdapter();
                for (int i = 0; i < colorsRecyclerView.getChildCount(); i++) {
                    View child = colorsRecyclerView.getChildAt(i);
                    RecyclerView.ViewHolder vh = colorsRecyclerView.getChildViewHolder(child);
                    if (vh instanceof ColorListAdapter.ColorViewHolder) {
                        ColorListAdapter.ColorViewHolder cvh = (ColorListAdapter.ColorViewHolder) vh;
                        if (cvh.colorItem != null && adapter != null
                                && adapter.getPositionOfColor(
                                cvh.colorItem.getColor()) == position) {
                            holder = vh;
                            break;
                        }
                    }
                }
            }

            if (holder != null && holder.itemView != null) {
                View itemView = holder.itemView;
                itemView.animate()
                        .scaleX(1.3f).scaleY(1.3f)
                        .setDuration(200)
                        .setInterpolator(new OvershootInterpolator())
                        .withEndAction(() -> itemView.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(200).start())
                        .start();

                ObjectAnimator alpha = ObjectAnimator.ofFloat(
                        itemView, "alpha", 1f, 0.6f, 1f);
                alpha.setDuration(400);
                alpha.start();
            }
        } catch (Exception e) {
            LogUtils.e("GameActivity", "Animation error", e);
        }
    }

    // ─── Zen Mode UI ───

    private void applyZenMode(boolean isZen) {
        if (binding.btnZenMode == null || isZenAnimating)
            return;

        isZenAnimating = true;

        // Pulse animation for Zen button to make it discoverable
        if (isZen) {
            if (zenPulseAnimator != null)
                zenPulseAnimator.cancel();
            zenPulseAnimator = ObjectAnimator.ofFloat(binding.btnZenMode, "alpha", 0.3f, 0.7f);
            zenPulseAnimator.setDuration(1200);
            zenPulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            zenPulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            zenPulseAnimator.start();
        } else {
            if (zenPulseAnimator != null) {
                zenPulseAnimator.cancel();
                zenPulseAnimator = null;
            }
        }

        binding.btnZenMode.setSelected(isZen);
        int vis = isZen ? GONE : VISIBLE;
        int duration = 500;

        // Animate UI elements
        List<View> viewsToAnimate = new ArrayList<>();
        viewsToAnimate.add(binding.backButton);
        viewsToAnimate.add(binding.rlHint);
        viewsToAnimate.add(binding.btnBgTheme);
        viewsToAnimate.add(binding.btnAutoFill);
        // viewsToAnimate.add(binding.colorrr); // Keep color list visible in Zen mode
        // per user request


        // Timer handling
        if (timerLayout != null && Boolean.TRUE.equals(viewModel.getTimerActive().getValue())) {
            viewsToAnimate.add(timerLayout);
        }

        // Ads handling (premium check)
        if (!viewModel.isPremium() && AdController.shouldShowAds(this)) {
            viewsToAnimate.add(binding.llAds);
        } else {
            binding.llAds.setVisibility(GONE);
        }

        for (View v : viewsToAnimate) {
            if (v == null)
                continue;
            if (isZen) {
                v.animate().alpha(0f).setDuration(duration)
                        .withEndAction(() -> {
                            v.setVisibility(GONE);
                            isZenAnimating = false;
                        }).start();
            } else {
                v.setVisibility(VISIBLE);
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(duration)
                        .withEndAction(() -> isZenAnimating = false)
                        .start();
            }
        }

        // System bars handling
        androidx.core.view.WindowInsetsControllerCompat ctrl = androidx.core.view.WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());

        if (isZen) {
            if (ctrl != null) {
                ctrl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                ctrl.setSystemBarsBehavior(
                        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            if (ctrl != null)
                ctrl.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars());
            hideStatusBar();
            binding.btnZenMode.animate().alpha(1f).setDuration(duration).start();
        }
    }

    // ─── Hint Methods ───

    public void onUseHint(View view) {
        if (gameView == null)
            return;

        if (viewModel.isPremium()) {
            gameView.showHint();
            return;
        }

        boolean hintShown = gameView.showHint();
        viewModel.onUseHint(hintShown);
    }

    public void animateHintCounterScale() {
        TextView tv = binding.hintCounter;
        tv.animate().scaleX(0.5f).scaleY(0.5f).alpha(0.3f).setDuration(150)
                .withEndAction(() -> {
                    viewModel.updateHintCounter();
                    tv.animate().scaleX(1.2f).scaleY(1.2f).alpha(1f).setDuration(200)
                            .withEndAction(() -> tv.animate()
                                    .scaleX(1f).scaleY(1f).setDuration(150).start())
                            .start();
                }).start();
    }

    private void animateHintCounterBounce() {
        TextView tv = binding.hintCounter;
        tv.animate().scaleX(0f).scaleY(0f).setDuration(100)
                .withEndAction(() -> {
                    viewModel.updateHintCounter();
                    tv.animate().scaleX(1.3f).scaleY(1.3f).setDuration(200)
                            .setInterpolator(new OvershootInterpolator())
                            .withEndAction(() -> tv.animate()
                                    .scaleX(1f).scaleY(1f).setDuration(100).start())
                            .start();
                }).start();
    }

    private void animateHintCounterSlide() {
        TextView tv = binding.hintCounter;
        tv.animate().translationY(-50f).alpha(0f).setDuration(200)
                .withEndAction(() -> {
                    viewModel.updateHintCounter();
                    tv.setTranslationY(50f);
                    tv.animate().translationY(0f).alpha(1f).setDuration(200).start();
                }).start();
    }

    private void animateHintCounterRotate() {
        TextView tv = binding.hintCounter;
        tv.animate().rotationY(90f).alpha(0.5f).setDuration(150)
                .withEndAction(() -> {
                    viewModel.updateHintCounter();
                    tv.setRotationY(-90f);
                    tv.animate().rotationY(0f).alpha(1f).setDuration(150).start();
                }).start();
    }

    // ─── Loading UI ───

    private void loadThumbnailPreview() {
        Level level = viewModel.getCurrentLevel();
        if (level == null)
            return;

        String thumbUrl = Manager.BASE_URL + "thumb/" + level.getCategory()
                + "/t/" + level.getFileName() + ".png";

        Glide.with(this).load(thumbUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(binding.ivLoadingPreview);

        binding.bookloading.setVisibility(GONE);
        // The placeholder sweep is started by the downloadRequired observer instead, which knows
        // whether there is a real download to wait for. Starting it here too ran two animators at
        // once on the same bar.
    }

    /**
     * A placeholder sweep for the moment before the first real download packet arrives.
     *
     * <p>Deliberately stops low and early. It used to run to 75 over four seconds, so the first
     * genuine progress update — which is usually a single-digit percentage — either jumped the bar
     * forward or would have dragged it back.
     */
    private void startFakeProgress() {
        startProgressAnimation(12, 900);
    }

    /** The picture is already on the device: only decoding is left, and that is quick. */
    private void startPreparingProgress() {
        startProgressAnimation(90, 700);
    }

    private void startProgressAnimation(int to, long durationMs) {
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }
        progressAnimator = ObjectAnimator.ofInt(binding.pbLoadingHorizontal, "progress",
                binding.pbLoadingHorizontal.getProgress(), to);
        progressAnimator.setDuration(durationMs);
        progressAnimator.setInterpolator(new AccelerateInterpolator(0.5f));
        progressAnimator.start();
    }

    private void hideGameUIDuringLoading() {
        View backBtn = findViewById(R.id.backButton);
        View hintBtn = findViewById(R.id.rl_hint);
        View colorPalette = findViewById(R.id.colorrr);
        View adsLayout = findViewById(R.id.ll_ads);

        if (backBtn != null)
            backBtn.setVisibility(GONE);
        binding.btnZenMode.setVisibility(GONE);
        binding.btnBgTheme.setVisibility(GONE);
        binding.btnAutoFill.setVisibility(GONE);
        if (hintBtn != null)
            hintBtn.setVisibility(GONE);
        if (colorPalette != null)
            colorPalette.setVisibility(GONE);
        if (adsLayout != null)
            adsLayout.setVisibility(GONE);
    }

    /** How long the bar takes to run up to 100% once the picture is actually drawable. */
    private static final int FINISH_FILL_MS = 220;
    /** Loader fade-out; the workspace fades in underneath it at the same time. */
    private static final int OVERLAY_FADE_MS = 180;
    /** Controls fading in. */
    private static final int UI_REVEAL_MS = 220;

    private void showGameUIAfterLoading() {
        boolean isPremium = viewModel.isPremium();

        View backBtn = findViewById(R.id.backButton);
        View hintBtn = findViewById(R.id.rl_hint);
        View colorPalette = findViewById(R.id.colorrr);
        View adsLayout = findViewById(R.id.ll_ads);

        List<View> views = new ArrayList<>();
        if (backBtn != null)
            views.add(backBtn);
        if (hintBtn != null)
            views.add(hintBtn);
        if (colorPalette != null)
            views.add(colorPalette);

        views.add(binding.btnZenMode);
        views.add(binding.btnBgTheme);
        views.add(binding.btnAutoFill);

        if (isPremium) {
            if (adsLayout != null)
                adsLayout.setVisibility(GONE);
        } else {
            if (AdController.shouldShowAds(this)) {
                if (adsLayout != null)
                    views.add(adsLayout);
            } else {
                if (adsLayout != null)
                    adsLayout.setVisibility(GONE);
            }
        }

        for (View v : views) {
            if (v != null) {
                v.setAlpha(0f);
                v.setVisibility(VISIBLE);
                v.animate().alpha(1f).setDuration(UI_REVEAL_MS).setStartDelay(0).start();
            }
        }

        showToolTipsIfFirstTime();
    }

    private void finishLoadingProgress() {
        if (binding.pbLoadingHorizontal == null)
            return;

        if (progressAnimator != null && progressAnimator.isRunning())
            progressAnimator.cancel();

        int current = binding.pbLoadingHorizontal.getProgress();
        if (current >= 100) {
            // Nothing left to fill — do not sit on a full bar for the sake of an animation.
            dismissLoadingOverlay();
            return;
        }

        ObjectAnimator finishAnim = ObjectAnimator.ofInt(
                binding.pbLoadingHorizontal, "progress", current, 100);
        finishAnim.setDuration(FINISH_FILL_MS);
        finishAnim.setInterpolator(new AccelerateInterpolator());
        // Chained to the fill, not to a stopwatch. The old code fired a 500ms postDelayed
        // alongside a 400ms animation, so the bar reached 100% and then simply sat there.
        finishAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                dismissLoadingOverlay();
            }
        });
        finishAnim.start();
    }

    /**
     * Takes the loading overlay away and brings the workspace in.
     *
     * <p>The two overlap deliberately: the controls start fading in while the loader is still
     * fading out, so the picture is reachable as soon as it is drawable. Run in sequence — which
     * is what this used to do, with a fixed delay in front of it — the wait after the bar hit 100%
     * added up to roughly 1.3 seconds of watching nothing happen.
     */
    private void dismissLoadingOverlay() {
        if (isFinishing() || isDestroyed() || binding.progressBar == null) return;

        showGameUIAfterLoading();
        binding.progressBar.animate()
                .alpha(0f)
                .setDuration(OVERLAY_FADE_MS)
                .withEndAction(() -> binding.progressBar.setVisibility(GONE))
                .start();
    }

    // ─── Game Save ───

    private void saveCompletedImage() {
        try {
            Bitmap completedImage = gameView.getWinPreview();
            viewModel.saveCompletedImage(completedImage);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
        } catch (OutOfMemoryError e) {
            Toast.makeText(this, R.string.error_out_of_memory, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void navigateToShareScreen(File imageFile) {
        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Level level = viewModel.getCurrentLevel();
        String beforePath = viewModel.getBeforeImagePath();

        Intent intent = new Intent(this, ArtworkShareActivity.class);
        intent.putExtra("path", imageFile.getAbsolutePath());
        if (level != null) {
            intent.putExtra("level_key", level.getFileName());
        }
        if (beforePath != null) {
            intent.putExtra("before_path", beforePath);
        }

        if (gameView != null) {
            gameView.zoomOutImage();

            // Bounce animation → Interstitial → Share Screen
            ViewAnimator.animate(gameView)
                    .scale(1f, 1.2f).duration(200)
                    .thenAnimate(gameView).scale(1.2f, 0.9f).duration(150)
                    .thenAnimate(gameView).scale(0.9f, 1.1f).duration(100)
                    .thenAnimate(gameView)
                    .scale(1.1f, 0f).alpha(1f, 0f).duration(400)
                    .interpolator(new AccelerateInterpolator())
                    .onStop(() -> {
                        MediationManager.showInterstitial(this, () -> {
                            try {
                                startActivity(intent);
                                overridePendingTransition(
                                        R.anim.slide_up_in, R.anim.fade_out);
                                finish();
                            } catch (OutOfMemoryError e) {
                                finish();
                            }
                        });
                    })
                    .start();
        } else {
            // No gameView available (safety)
            MediationManager.showInterstitial(this, () -> {
                try {
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out);
                    finish();
                } catch (OutOfMemoryError e) {
                    finish();
                }
            });
        }
    }

    // ─── Timer ───

    private void startLinearTimer() {
        runOnUiThread(() -> {
            timerLayout.setVisibility(VISIBLE);
            linearTimer = new LinearTimer.Builder()
                    .linearTimerView(timerView)
                    .duration(10000)
                    .timerListener(GameActivity.this)
                    .getCountUpdate(3, 1000)
                    .build();
            linearTimer.startTimer();
        });
    }

    @Override
    public void animationComplete() {
        runOnUiThread(() -> {
            stopLinearTimerSafely();
            viewModel.onTimerComplete();
        });
    }

    @Override
    public void onTimerReset() {
    }

    @Override
    public void timerTick(long millisRemaining) {
    }

    public void onTimerHint(View view) {
        viewModel.onTimerHint();
    }

    private void stopLinearTimerSafely() {
        if (linearTimer == null)
            return;
        try {
            linearTimer.pauseTimer();
        } catch (Throwable ignored) {
        }
        try {
            linearTimer.resetTimer();
        } catch (Throwable ignored) {
        }
        linearTimer = null;
        if (timerLayout != null)
            timerLayout.setVisibility(GONE);
    }

    // ─── Back Navigation ───

    private void performBackAction() {
        if (isFinishing() || isDestroyed() || isBackHandlingStarted)
            return;
        isBackHandlingStarted = true;

        boolean isLoading = binding.progressBar.getVisibility() == VISIBLE;
        viewModel.onBackPressed(isLoading);

        if (isLoading) {
            safeFinish();
        }
        // Otherwise showBackInterstitial LiveEvent handles it
    }

    private void safeFinish() {
        if (isFinishing() || isDestroyed())
            return;

        binding.bookloading.setVisibility(GONE);
        if (onBackPressedCallback != null)
            onBackPressedCallback.setEnabled(false);

        viewModel.pauseRecording();

        new Thread(() -> {
            try {
                com.downloader.PRDownloader.cancelAll();
            } catch (Exception ignored) {
            }
        }).start();

        stopLinearTimerSafely();

        if (Looper.myLooper() == Looper.getMainLooper()) {
            finish();
            overridePendingTransition(R.anim.slide_from_left, R.anim.slide_to_right);
        } else {
            runOnUiThread(() -> {
                finish();
                overridePendingTransition(R.anim.slide_from_left, R.anim.slide_to_right);
            });
        }
    }

    // ─── Tool Buttons ───

    public void deselectAllToolButtons() {
        if (btnToolBrush != null)
            btnToolBrush.setSelected(false);
        if (btnToolBomb != null)
            btnToolBomb.setSelected(false);
        if (btnToolBucket != null)
            btnToolBucket.setSelected(false);
        if (btnToolLoupe != null)
            btnToolLoupe.setSelected(false);
        if (btnToolRadar != null)
            btnToolRadar.setSelected(false);
        if (btnToolSurge != null)
            btnToolSurge.setSelected(false);
        if (btnToolWand != null)
            btnToolWand.setSelected(false);
        if (btnToolMagnet != null)
            btnToolMagnet.setSelected(false);
    }

    private View getToolButton(GameView.GameTool tool) {
        switch (tool) {
            case BRUSH:
                return btnToolBrush;
            case BOMB:
                return btnToolBomb;
            case BUCKET:
                return btnToolBucket;
            case LOUPE:
                return btnToolLoupe;
            case RADAR:
                return btnToolRadar;
            case SURGE:
                return btnToolSurge;
            case MAGIC_WAND:
                return btnToolWand;
            case MAGNET:
                return btnToolMagnet;
            default:
                return null;
        }
    }

    // ─── Dialogs ───

    private void showBgThemeDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_bg_themes);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(
                    new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setGravity(android.view.Gravity.BOTTOM);
            window.getAttributes().windowAnimations = R.style.DialogAnimationAds;
        }

        ImageView closeBtn = dialog.findViewById(R.id.iv_theme_close);
        TextView confirmBtn = dialog.findViewById(R.id.tv_theme_confirm);
        RecyclerView rvThemes = dialog.findViewById(R.id.rv_themes);

        rvThemes.setLayoutManager(
                new androidx.recyclerview.widget.GridLayoutManager(this, 3));

        List<BackgroundThemeManager.BgTheme> themes = BackgroundThemeManager.getAllThemes();
        final String[] selectedId = {BackgroundThemeManager.getActiveThemeId(this)};

        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(
                    @NonNull android.view.ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(
                        R.layout.item_bg_theme, parent, false);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(
                    @NonNull RecyclerView.ViewHolder h, int pos) {
                BackgroundThemeManager.BgTheme t = themes.get(pos);
                View preview = h.itemView.findViewById(R.id.v_color_preview);
                ImageView check = h.itemView.findViewById(R.id.iv_check);

                boolean isSelected = t.id.equals(selectedId[0]);

                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setShape(
                        android.graphics.drawable.GradientDrawable.RECTANGLE);
                shape.setCornerRadius(
                        getResources().getDisplayMetrics().density * 14);
                shape.setColor(t.primaryColor);
                float density = getResources().getDisplayMetrics().density;
                shape.setStroke(
                        isSelected ? (int) (2.5f * density) : (int) (1.5f * density),
                        isSelected ? 0xFF4CAF50 : 0x15000000);
                preview.setBackground(shape);
                check.setVisibility(isSelected ? VISIBLE : GONE);

                h.itemView.setOnClickListener(v -> {
                    selectedId[0] = t.id;
                    notifyDataSetChanged();
                });
            }

            @Override
            public int getItemCount() {
                return themes.size();
            }
        };

        rvThemes.setAdapter(adapter);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        confirmBtn.setOnClickListener(v -> {
            viewModel.setActiveTheme(selectedId[0]);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showBrushSettingsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_brush_settings);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(
                    new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setGravity(android.view.Gravity.BOTTOM);
            window.getAttributes().windowAnimations = R.style.DialogAnimationAds;
        }

        TextView tvSizeValue = dialog.findViewById(R.id.tv_brush_size_value);
        android.widget.SeekBar seekBar = dialog.findViewById(R.id.seekbar_brush_size);
        View previewDot = dialog.findViewById(R.id.brush_preview_dot);
        TextView btnDone = dialog.findViewById(R.id.btn_brush_done);

        int currentRadius = gameView != null ? gameView.getBrushRadius() : 1;
        tvSizeValue.setText(String.valueOf(currentRadius));
        seekBar.setProgress(currentRadius - 1);
        updateBrushPreview(previewDot, currentRadius);

        seekBar.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            android.widget.SeekBar sb, int progress, boolean fromUser) {
                        int radius = progress + 1;
                        tvSizeValue.setText(String.valueOf(radius));
                        viewModel.setBrushRadius(radius);
                        updateBrushPreview(previewDot, radius);
                    }

                    @Override
                    public void onStartTrackingTouch(android.widget.SeekBar sb) {
                    }

                    @Override
                    public void onStopTrackingTouch(android.widget.SeekBar sb) {
                    }
                });

        btnDone.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void updateBrushPreview(View dot, int radius) {
        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (radius * 2.5f * density);
        sizePx = Math.max((int) (8 * density), Math.min((int) (50 * density), sizePx));
        android.view.ViewGroup.LayoutParams lp = dot.getLayoutParams();
        lp.width = sizePx;
        lp.height = sizePx;
        dot.setLayoutParams(lp);
    }

    // ─── Time-Lapse Replay ───

    // ─── STEP 4: REPLACE showTimeLapseReplay() method ───

    // ❌ OLD: showTimeLapseReplay() - no parameter, no navigation after close
    // ✅ NEW: showTimeLapseReplay(File) - navigates to Share Screen on close

    /**
     * Shows time-lapse replay dialog.
     * When closed → navigates to Share Screen.
     */

    // ─── Helper: Bounce animation ───
    private void animateBounce(View view) {
        view.animate()
                .scaleX(1.3f).scaleY(1.3f)
                .setDuration(100)
                .withEndAction(() -> view.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
    }

    // ─── Helper: Pulse animation ───
    private void pulseView(View view) {
        view.animate()
                .alpha(0.5f)
                .setDuration(500)
                .withEndAction(() -> view.animate()
                        .alpha(1f)
                        .setDuration(500)
                        .start())
                .start();
    }

    // ─── Helper: Update button enabled/alpha state ───
    private void updateButtonAlpha(View button, boolean enabled) {
        if (button == null)
            return;
        button.setAlpha(enabled ? 1.0f : 0.3f);
        button.setEnabled(enabled);
    }

    // ─── Tool Tips (TapTargetView) ───

    private void showToolTipsIfFirstTime() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (prefs.getBoolean(PREF_TOOL_TIPS_SHOWN, false)) {
            return;
        }

        // Mark as shown only after sequence starts successfully
        prefs.edit().putBoolean(PREF_TOOL_TIPS_SHOWN, true).apply();

        // Ensure tools panel is visible before showing tips
        View icGameTool = findViewById(R.id.ic_game_tool);
        if (icGameTool != null && icGameTool.getVisibility() != VISIBLE) {
            icGameTool.setVisibility(VISIBLE);
            icGameTool.setAlpha(1f);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            buildToolTipSequence();
        }, 800);
    }

    private void buildToolTipSequence() {
        // The library ships a blue-grey (#37474F) that belongs to no part of this app. These are
        // the design system's own values: brand violet spotlight, white target, ink-tinted dim.
        int outerColor = 0xFF7A5CFF;          // cm_violet
        int targetCircleColor = 0xFFFFFFFF;   // cm_surface
        int titleTextColor = 0xFFFFFFFF;
        int descTextColor = 0xD9FFFFFF;       // 85% — the old 70% was thin on a saturated circle
        int dimColor = 0xB3201636;            // cm_ink at 70%, not neutral black

        List<TapTarget> targets = new ArrayList<>();

        // 1) Tools toggle button
        View ivTools = findViewById(R.id.IVTools);
        if (ivTools != null && ivTools.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(ivTools,
                    getString(R.string.tip_tools_title),
                    getString(R.string.tip_tools_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 2) Paint Bucket
        if (btnToolBucket != null && btnToolBucket.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(btnToolBucket,
                    getString(R.string.tip_bucket_title),
                    getString(R.string.tip_bucket_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 3) Chroma Surge
        if (btnToolSurge != null && btnToolSurge.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(btnToolSurge,
                    getString(R.string.tip_surge_title),
                    getString(R.string.tip_surge_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 4) Magic Wand
        if (btnToolWand != null && btnToolWand.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(btnToolWand,
                    getString(R.string.tip_wand_title),
                    getString(R.string.tip_wand_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 5) Smart Magnet
        if (btnToolMagnet != null && btnToolMagnet.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(btnToolMagnet,
                    getString(R.string.tip_magnet_title),
                    getString(R.string.tip_magnet_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 6) Turbo Brush
        if (btnToolBrush != null && btnToolBrush.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(btnToolBrush,
                    getString(R.string.tip_brush_title),
                    getString(R.string.tip_brush_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 7) Color Bomb
        if (btnToolBomb != null && btnToolBomb.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(btnToolBomb,
                    getString(R.string.tip_bomb_title),
                    getString(R.string.tip_bomb_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 8) Magnifier
        if (btnToolLoupe != null && btnToolLoupe.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(btnToolLoupe,
                    getString(R.string.tip_loupe_title),
                    getString(R.string.tip_loupe_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 9) Radar Scanner
        if (btnToolRadar != null && btnToolRadar.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(btnToolRadar,
                    getString(R.string.tip_radar_title),
                    getString(R.string.tip_radar_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 10) Hint button
        View hintBtn = findViewById(R.id.rl_hint);
        if (hintBtn != null && hintBtn.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(hintBtn,
                    getString(R.string.tip_hint_title),
                    getString(R.string.tip_hint_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 11) Zen Mode
        if (binding.btnZenMode != null && binding.btnZenMode.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(binding.btnZenMode,
                    getString(R.string.tip_zen_title),
                    getString(R.string.tip_zen_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 12) Background Theme
        if (binding.btnBgTheme != null && binding.btnBgTheme.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(binding.btnBgTheme,
                    getString(R.string.tip_bg_theme_title),
                    getString(R.string.tip_bg_theme_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        // 13) Auto-Fill
        if (binding.btnAutoFill != null && binding.btnAutoFill.getVisibility() == VISIBLE) {
            targets.add(createTapTarget(binding.btnAutoFill,
                    getString(R.string.tip_auto_fill_title),
                    getString(R.string.tip_auto_fill_desc),
                    outerColor, targetCircleColor, titleTextColor, descTextColor, dimColor));
        }

        if (targets.isEmpty()) return;

        new TapTargetSequence(this)
                .targets(targets)
                .continueOnCancel(true)
                .listener(new TapTargetSequence.Listener() {
                    @Override
                    public void onSequenceFinish() {
                        hideToolsPanel();
                    }

                    @Override
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                    }

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {
                        hideToolsPanel();
                    }
                })
                .start();
    }

    private void hideToolsPanel() {
        View icGameTool = findViewById(R.id.ic_game_tool);
        if (icGameTool != null) {
            icGameTool.setVisibility(GONE);
        }
    }

    private TapTarget createTapTarget(View view, String title, String description,
                                      int outerColor, int targetCircleColor,
                                      int titleTextColor, int descTextColor,
                                      int dimColor) {
        TapTarget target = TapTarget.forView(view, title, description)
                .outerCircleColorInt(outerColor)
                .outerCircleAlpha(0.94f)      // a touch of the screen shows through: less like a wall
                .targetCircleColorInt(targetCircleColor)
                .titleTextColorInt(titleTextColor)
                .descriptionTextColorInt(descTextColor)
                .dimColorInt(dimColor)
                .titleTextSize(19)
                .descriptionTextSize(14)
                .drawShadow(true)
                .cancelable(true)
                .tintTarget(false)             // the tool keeps its own colour, so it stays recognisable
                .transparentTarget(true)
                .targetRadius(42);

        // Quicksand, like the rest of the app. Without this the coach marks are the only place in
        // Colour Mate rendered in the system font.
        Typeface bold = ResourcesCompat.getFont(this, R.font.quicksand_bold);
        Typeface regular = ResourcesCompat.getFont(this, R.font.quicksand_regular);
        if (bold != null) target.textTypeface(bold);
        if (regular != null) target.descriptionTypeface(regular);

        return target;
    }

    // ─── Utility ───

    private void hideStatusBar() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowInsetsControllerCompat controller = androidx.core.view.WindowCompat
                .getInsetsController(
                        getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    // ─── Click handlers from XML ───

    public void onBackImage(View view) {
        performBackAction();
    }

    public void onZoomOut(View view) {
        if (gameView != null)
            gameView.zoomOutImage();
    }

    public void onClickRetry(View view) {
        performBackAction();
    }

    public void onClickOOM(View view) {
        restartApplication(getApplicationContext());
    }
}