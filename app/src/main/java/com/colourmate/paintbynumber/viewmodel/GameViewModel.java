package com.colourmate.paintbynumber.viewmodel;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.ads.AppPreference;
import com.colourmate.paintbynumber.ads.Keys;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.model.api.UrlConstants;
import com.colourmate.paintbynumber.model.game.ColorItem;
import com.colourmate.paintbynumber.model.game.GameView;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.util.AchievementManager;
import com.colourmate.paintbynumber.util.BackgroundThemeManager;
import com.colourmate.paintbynumber.util.DataManager;
import com.colourmate.paintbynumber.util.GameSaver;
import com.colourmate.paintbynumber.util.LogUtils;
import com.colourmate.paintbynumber.util.TimeLapseRecorder;
import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import com.downloader.Priority;
import com.downloader.Progress;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class GameViewModel extends AndroidViewModel {

    private static final String TAG = "GameViewModel";
    public static final String COLOR_NUMBER_PATH = "MyColorByNumber";
    private static final int HINT_COOLDOWN_MS = 20000;
    private static final int TIMER_INTERVAL_MS = 60000;
    private static final int TIMER_DURATION_MS = 10000;

    // ─── LiveData fields ───

    // Loading state
    private final MutableLiveData<LoadingState> loadingState = new MutableLiveData<>(LoadingState.IDLE);
    private final MutableLiveData<Integer> downloadProgress = new MutableLiveData<>(0);
    private final MutableLiveData<String> downloadProgressText = new MutableLiveData<>("0 %");
    /** True when the picture must be fetched; false when it is already on disk. */
    private final MutableLiveData<Boolean> downloadRequired = new MutableLiveData<>();

    // Game state
    private final MutableLiveData<BitmapPair> bitmapsReady = new MutableLiveData<>();
    private final MutableLiveData<List<ColorItem>> colorPalette = new MutableLiveData<>();
    private final MutableLiveData<Integer> selectedColor = new MutableLiveData<>();
    private final MutableLiveData<ColorProgressUpdate> colorProgress = new MutableLiveData<>();
    private final MutableLiveData<Integer> colorFinished = new MutableLiveData<>();
    private final MutableLiveData<Boolean> gameFinished = new MutableLiveData<>(false);

    // UI state
    private final MutableLiveData<Boolean> zenMode = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> autoFillMode = new MutableLiveData<>(false);
    private final MutableLiveData<String> hintCounterText = new MutableLiveData<>();
    private final MutableLiveData<HintAnimationType> hintAnimation = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showMemoryError = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showSlowNetwork = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> timerActive = new MutableLiveData<>(false);
    private final MutableLiveData<BackgroundThemeManager.BgTheme> activeTheme = new MutableLiveData<>();
    private final MutableLiveData<GameView.GameTool> activeTool = new MutableLiveData<>(GameView.GameTool.NONE);
    private final MutableLiveData<Integer> brushRadius = new MutableLiveData<>(1);

    // Navigation events (SingleLiveEvent pattern)
    private final SingleLiveEvent<NavigationEvent> navigationEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> toastMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showBgThemeDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showBrushSettingsDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showRewardedAd = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showInterstitialAd = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showBackInterstitial = new SingleLiveEvent<>();
    private final SingleLiveEvent<SavedImageResult> savedImageResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Integer> animateColorPosition = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> startTimerEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<GameView.GameTool> requestToolAd = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> requestAutoFillAd = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> requestZenModeAd = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> requestBgThemeAd = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> requestHintAd = new SingleLiveEvent<>();

    // Internal state
    private Level currentLevel;
    private File baseImageFile;
    private File coloredImageFile;
    private int firstDownloadId = 0;
    private int secondDownloadId = 0;
    private boolean canShowHintAd = true;
    private boolean isTimerActiveInternal = false;
    private Timer periodicTimer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TimeLapseRecorder timeLapseRecorder;
    private long sessionStartTime;
    private String beforeImagePath;
    private GameView.GameTool currentActiveTool = GameView.GameTool.NONE;
    private boolean isPremium = false;

    public GameViewModel(@NonNull Application application) {
        super(application);
        isPremium = AppPreference.getBoolean(application, Keys.KEY_IS_PREMIUM, false);
        sessionStartTime = System.currentTimeMillis();
        timeLapseRecorder = new TimeLapseRecorder();
    }

    // ─── Getters for LiveData ───

    public LiveData<LoadingState> getLoadingState() {
        return loadingState;
    }

    public LiveData<Integer> getDownloadProgress() {
        return downloadProgress;
    }

    public LiveData<String> getDownloadProgressText() {
        return downloadProgressText;
    }

    public LiveData<Boolean> getDownloadRequired() {
        return downloadRequired;
    }

    public LiveData<BitmapPair> getBitmapsReady() {
        return bitmapsReady;
    }

    public LiveData<List<ColorItem>> getColorPalette() {
        return colorPalette;
    }

    public LiveData<Integer> getSelectedColor() {
        return selectedColor;
    }

    public LiveData<ColorProgressUpdate> getColorProgress() {
        return colorProgress;
    }

    public LiveData<Integer> getColorFinished() {
        return colorFinished;
    }

    public LiveData<Boolean> getGameFinished() {
        return gameFinished;
    }

    // public LiveData<Void> getRequestAutoFillAd() {
    // return requestAutoFillAd;
    // }

    public LiveData<Void> getRequestZenModeAd() {
        return requestZenModeAd;
    }

    public LiveData<Void> getRequestBgThemeAd() {
        return requestBgThemeAd;
    }

    public LiveData<Boolean> getZenMode() {
        return zenMode;
    }

    public LiveData<Boolean> getAutoFillMode() {
        return autoFillMode;
    }

    public LiveData<String> getHintCounterText() {
        return hintCounterText;
    }

    public LiveData<HintAnimationType> getHintAnimation() {
        return hintAnimation;
    }

    public LiveData<Boolean> getShowMemoryError() {
        return showMemoryError;
    }

    public LiveData<Boolean> getShowSlowNetwork() {
        return showSlowNetwork;
    }

    public LiveData<Boolean> getTimerActive() {
        return timerActive;
    }

    public LiveData<BackgroundThemeManager.BgTheme> getActiveTheme() {
        return activeTheme;
    }

    public LiveData<GameView.GameTool> getActiveTool() {
        return activeTool;
    }

    public LiveData<Integer> getBrushRadius() {
        return brushRadius;
    }

    public SingleLiveEvent<NavigationEvent> getNavigationEvent() {
        return navigationEvent;
    }

    public SingleLiveEvent<String> getToastMessage() {
        return toastMessage;
    }

    public SingleLiveEvent<Void> getShowBgThemeDialog() {
        return showBgThemeDialog;
    }

    public SingleLiveEvent<Void> getShowBrushSettingsDialog() {
        return showBrushSettingsDialog;
    }

    public SingleLiveEvent<Void> getShowRewardedAd() {
        return showRewardedAd;
    }

    public SingleLiveEvent<Void> getShowInterstitialAd() {
        return showInterstitialAd;
    }

    public SingleLiveEvent<Void> getShowBackInterstitial() {
        return showBackInterstitial;
    }

    public SingleLiveEvent<SavedImageResult> getSavedImageResult() {
        return savedImageResult;
    }

    public SingleLiveEvent<Integer> getAnimateColorPosition() {
        return animateColorPosition;
    }

    public SingleLiveEvent<Void> getStartTimerEvent() {
        return startTimerEvent;
    }

    public SingleLiveEvent<GameView.GameTool> getRequestToolAd() {
        return requestToolAd;
    }

    public SingleLiveEvent<Void> getRequestAutoFillAd() {
        return requestAutoFillAd;
    }

    public SingleLiveEvent<Void> getRequestHintAd() {
        return requestHintAd;
    }

    public Level getCurrentLevel() {
        return currentLevel;
    }

    public TimeLapseRecorder getTimeLapseRecorder() {
        return timeLapseRecorder;
    }

    public boolean isPremium() {
        return isPremium;
    }

    public long getSessionStartTime() {
        return sessionStartTime;
    }

    // ─── Initialization ───

    public void initialize(Level level) {
        this.currentLevel = level;
        updateHintCounter();
        loadActiveTheme();

        if (level != null) {
            timeLapseRecorder.resumeRecording(getApplication(), level.getFileName());
        }

        AchievementManager.recordModeTried(getApplication(), "number");
    }

    // ─── Level Loading ───

    public void requestLevelData() {
        if (currentLevel == null)
            return;

        loadingState.setValue(LoadingState.LOADING);

        File imageDirectory = new File(
                GlobalApplication.getAppContext().getFilesDir(), "color_images");
        if (!imageDirectory.exists())
            imageDirectory.mkdirs();

        String baseFileName = currentLevel.getFileName();
        String coloredFileName = currentLevel.getFileName();

        if (GameSaver.isLowRes(GlobalApplication.getAppContext())) {
            baseFileName += ".small";
            coloredFileName += ".small";
        }

        String baseImageName = baseFileName + ".png";
        String coloredImageName = coloredFileName + ".colored.png";

        baseImageFile = new File(imageDirectory, baseImageName);
        coloredImageFile = new File(imageDirectory, coloredImageName);

        boolean needsDownload = !baseImageFile.exists() || !coloredImageFile.exists();
        // Tell the screen whether there is anything to report progress ON. Without this it ran a
        // 4-second fake crawl even for a picture already on disk, which loads in a fraction of
        // that — so the bar inched along and then snapped to full.
        downloadRequired.postValue(needsDownload);

        if (needsDownload) {
            downloadLevelImages(imageDirectory, baseImageName, coloredImageName);
        } else {
            onLevelImagesLoaded(baseImageFile, coloredImageFile);
        }
    }

    /**
     * "New 2026" levels live on our own server, everything else on ionicsolutions.
     * Both expose the same {@code thumb/{category}/{file}} shape.
     */
    private String imagesRoot() {
        if (currentLevel != null && currentLevel.isColorForge()) {
            String base = ColorForgeApi.baseUrl(getApplication());
            if (!base.isEmpty()) return base + "thumb/";
        }
        return UrlConstants.IMAGES_URL;
    }

    private void downloadLevelImages(File directory, String baseImageName, String coloredImageName) {
        String imagesRoot = imagesRoot();
        String baseImageUrl = imagesRoot
                + currentLevel.getCategory() + "/" + baseImageName;
        String coloredImageUrl = imagesRoot
                + currentLevel.getCategory() + "/" + coloredImageName;

        firstDownloadId = PRDownloader.download(
                baseImageUrl, directory.getAbsolutePath(), baseImageName)
                .setPriority(Priority.IMMEDIATE)
                .build()
                .setOnProgressListener(progress -> updateDownloadProgress(progress, 0))
                .setOnStartOrResumeListener(() -> showSlowNetwork.postValue(false))
                .start(new OnDownloadListener() {
                    @Override
                    public void onDownloadComplete() {
                        if (coloredImageFile.exists()) {
                            onLevelImagesLoaded(baseImageFile, coloredImageFile);
                        } else {
                            downloadSecondImage(
                                    coloredImageUrl,
                                    directory.getAbsolutePath(),
                                    coloredImageName);
                        }
                    }

                    @Override
                    public void onError(Error error) {
                        if (error.isServerError() || error.isConnectionError()) {
                            downloadSecondImage(
                                    baseImageUrl,
                                    directory.getAbsolutePath(),
                                    baseImageName);
                        } else {
                            showSlowNetwork.postValue(true);
                        }
                    }
                });
    }

    private void downloadSecondImage(String url, String directory, String filename) {
        if (firstDownloadId != 0)
            PRDownloader.cancel(firstDownloadId);

        secondDownloadId = PRDownloader.download(url, directory, filename)
                .setPriority(Priority.IMMEDIATE)
                .build()
                .setOnProgressListener(progress -> updateDownloadProgress(progress, 50))
                .start(new OnDownloadListener() {
                    @Override
                    public void onDownloadComplete() {
                        showSlowNetwork.postValue(false);
                        onLevelImagesLoaded(baseImageFile, coloredImageFile);
                    }

                    @Override
                    public void onError(Error error) {
                        if (error.isServerError() || error.isConnectionError()) {
                            downloadSecondImage(url, directory, filename);
                        } else {
                            showSlowNetwork.postValue(true);
                        }
                    }
                });
    }

    private void updateDownloadProgress(Progress progress, int offset) {
        if (progress.totalBytes <= 0)
            return;

        // Two files, half the bar each: base image 0-50, colour data 50-100.
        int percentage = (int) (((progress.currentBytes * 100f) / progress.totalBytes) / 2f) + offset;
        percentage = Math.max(0, Math.min(100, percentage));

        // The bar and the label must show the same number. They used to disagree badly: the label
        // showed the real percentage while the bar was remapped onto 75-99, so the very first
        // progress packet slammed the bar to three-quarters while the text still read "2 %".
        downloadProgressText.postValue(percentage + " %");
        downloadProgress.postValue(percentage);
    }

    private void onLevelImagesLoaded(File baseImage, File coloredImage) {
        if (secondDownloadId != 0)
            PRDownloader.cancel(secondDownloadId);

        saveBeforeImage(baseImage);
        startPeriodicTimer();
        loadBitmaps(baseImage, coloredImage);
    }

    private void loadBitmaps(File baseImage, File coloredImage) {
        new Thread(() -> {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;

                Bitmap baseBitmap = BitmapFactory.decodeStream(
                        new FileInputStream(baseImage), null, options);
                Bitmap coloredBitmap = BitmapFactory.decodeStream(
                        new FileInputStream(coloredImage), null, options);

                if (baseBitmap == null) {
                    loadingState.postValue(LoadingState.ERROR);
                    return;
                }

                if (coloredBitmap != null) {
                    bitmapsReady.postValue(new BitmapPair(baseBitmap, coloredBitmap));
                    loadingState.postValue(LoadingState.LOADED);
                } else {
                    showMemoryError.postValue(true);
                    loadingState.postValue(LoadingState.MEMORY_ERROR);
                }
            } catch (OutOfMemoryError e) {
                System.gc();
                showMemoryError.postValue(true);
                loadingState.postValue(LoadingState.MEMORY_ERROR);
            } catch (Exception e) {
                LogUtils.e(TAG, "Error loading bitmaps", e);
                loadingState.postValue(LoadingState.ERROR);
            }
        }).start();
    }

    // ─── Color Management ───

    public void onColorClicked(ColorItem colorItem) {
        if (colorItem.isFinished())
            return;
        selectedColor.setValue(colorItem.getColor());
    }

    public void onColorProgress(int color, int progress) {
        colorProgress.postValue(new ColorProgressUpdate(color, progress));
    }

    public void onColorFinished(int color) {
        colorFinished.postValue(color);
    }

    public void setColorPalette(List<ColorItem> colors) {
        colorPalette.setValue(colors);
    }

    public void onLongClickColorSelect(int color) {
        selectedColor.setValue(color);
    }

    // ─── Hint Management ───

    public void updateHintCounter() {
        if (isPremium) {
            hintCounterText.setValue("∞");
            return;
        }
        int hints = GameSaver.getHint(getApplication());
        hintCounterText.setValue(hints == 0 ? "AD" : String.valueOf(hints));
    }

    public void onUseHint(boolean hintShown) {
        if (isPremium)
            return; // Handled directly in activity for GameView

        if (!hintShown) {
            startHintCooldown();
            requestHintAd.call();
        }
    }

    public void onHintAdWatched() {
        onHintAdWatched(1);
    }

    public void onHintAdWatched(int hintsAwarded) {
        GameSaver.setIncreseHint(getApplication(), hintsAwarded);
        hintAnimation.setValue(HintAnimationType.SCALE);
        updateHintCounter();

        String message = hintsAwarded == 1
                ? "You earned 1 hint!"
                : "You earned " + hintsAwarded + " hints!";
        toastMessage.setValue(message);
    }

    private void startHintCooldown() {
        canShowHintAd = false;
        handler.postDelayed(() -> canShowHintAd = true, HINT_COOLDOWN_MS);
    }

    // ─── Zen Mode ───

    public void toggleZenMode() {
        boolean currentState = Boolean.TRUE.equals(zenMode.getValue());
        // if (!currentState && !isPremium) {
        // requestZenModeAd.call();
        // return;
        // }

        boolean newMode = !currentState;
        zenMode.setValue(newMode);
        toastMessage.setValue(
                newMode ? "Zen Mode ON – tap icon to exit" : null);
    }

    public void onZenModeAdWatched() {
        zenMode.setValue(true);
        toastMessage.setValue("Zen Mode ON – tap icon to exit");
    }

    // ─── Auto-Fill ───

    public void toggleAutoFill() {
        boolean currentState = Boolean.TRUE.equals(autoFillMode.getValue());
        if (!currentState && !isPremium) {
            requestAutoFillAd.call();
            return;
        }

        boolean newState = !currentState;
        autoFillMode.setValue(newState);
        toastMessage.setValue(
                newState
                        ? "Auto-Fill ON – tap a cell to fill entire color"
                        : "Auto-Fill OFF – tap fills one area");
    }

    public void onAutoFillAdWatched() {
        autoFillMode.setValue(true);
        toastMessage.setValue("Auto-Fill ON – tap a cell to fill entire color");
    }

    // ─── Tool Management ───

    public void toggleTool(GameView.GameTool tool) {
        if (currentActiveTool == tool) {
            currentActiveTool = GameView.GameTool.NONE;
            activeTool.setValue(GameView.GameTool.NONE);
            toastMessage.setValue(getToolName(tool) + " OFF");
        } else {
            if (!isPremium) {
                requestToolAd.setValue(tool);
                return;
            }
            currentActiveTool = tool;
            activeTool.setValue(tool);
            toastMessage.setValue(getToolName(tool) + " ON");
        }
    }

    public void onToolAdWatched(GameView.GameTool tool) {
        currentActiveTool = tool;
        activeTool.setValue(tool);
        toastMessage.setValue(getToolName(tool) + " ON");
    }

    public void onBrushLongClick() {
        showBrushSettingsDialog.call();
    }

    /**
     * Called when GameView self-deactivates a tool (one-shot tools, expired surge,
     * etc.)
     * Resets ViewModel state to match.
     */
    public void resetTool() {
        currentActiveTool = GameView.GameTool.NONE;
        activeTool.setValue(GameView.GameTool.NONE);
        autoFillMode.setValue(false);
    }

    public void setBrushRadius(int radius) {
        brushRadius.setValue(radius);
    }

    private String getToolName(GameView.GameTool tool) {
        switch (tool) {
            case BRUSH:
                return "Turbo Brush";
            case BOMB:
                return "Color Bomb";
            case BUCKET:
                return "Paint Bucket";
            case LOUPE:
                return "Magnifier";
            case RADAR:
                return "Radar Scanner";
            case SURGE:
                return "Chroma Surge";
            case MAGIC_WAND:
                return "Magic Wand";
            case MAGNET:
                return "Area Magnet";
            default:
                return "Tool";
        }
    }

    // ─── Background Theme ───

    public void loadActiveTheme() {
        activeTheme.setValue(BackgroundThemeManager.getActiveTheme(getApplication()));
    }

    public void onBgThemeClicked() {
        // if (!isPremium) {
        // requestBgThemeAd.call();
        // return;
        // }
        showBgThemeDialog.call();
    }

    public void onBgThemeAdWatched() {
        showBgThemeDialog.call();
    }

    public void setActiveTheme(String themeId) {
        BackgroundThemeManager.setActiveTheme(getApplication(), themeId);
        loadActiveTheme();
    }

    // ─── Game Finish ───

    public void onGameFinished() {
        if (com.colourmate.paintbynumber.util.ZenChallengeManager
                .isChallengeActive(getApplication())) {
            com.colourmate.paintbynumber.util.ZenChallengeManager
                    .completeChallenge(getApplication());
        }
        gameFinished.setValue(true);
    }

    public void saveCompletedImage(Bitmap completedImage) {
        if (currentLevel == null || completedImage == null)
            return;

        try {
            DataManager.getInstance().finishLevel(currentLevel);

            String[] pathParts = currentLevel.getFileName().split("/");
            String filename = pathParts[pathParts.length - 1];

            File saveDirectory = new File(
                    getApplication().getFilesDir(), COLOR_NUMBER_PATH);
            saveDirectory.mkdirs();

            File imageFile = new File(saveDirectory, filename + ".png");
            FileOutputStream outputStream = new FileOutputStream(imageFile);
            completedImage.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();

            AchievementManager.recordImageCompleted(getApplication());
            long elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000;
            AchievementManager.recordTimeSpent(getApplication(), (int) elapsed);

            if (timeLapseRecorder != null) {
                timeLapseRecorder.stopRecording();
                timeLapseRecorder.save(getApplication());
            }

            savedImageResult.postValue(
                    new SavedImageResult(imageFile.getAbsolutePath(), beforeImagePath));

        } catch (Exception e) {
            toastMessage.postValue("Error saving image");
        } catch (OutOfMemoryError e) {
            toastMessage.postValue("Out of memory");
        }
    }

    // ─── Timer ───

    private void startPeriodicTimer() {
        periodicTimer = new Timer();
        periodicTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                isTimerActiveInternal = true;
                timerActive.postValue(true);
                startTimerEvent.postValue(null);
            }
        }, TIMER_INTERVAL_MS, TIMER_INTERVAL_MS);
    }

    public void onTimerComplete() {
        isTimerActiveInternal = false;
        timerActive.postValue(false);
    }

    public void onTimerHint() {
        if (isTimerActiveInternal) {
            startHintCooldown();
            showInterstitialAd.call();
        }
    }

    // ─── Back Navigation ───

    public void onBackPressed(boolean isLoading) {
        if (isLoading) {
            navigationEvent.setValue(NavigationEvent.FINISH);
            return;
        }
        showBackInterstitial.call();
    }

    public void onBackAdComplete() {
        navigationEvent.setValue(NavigationEvent.FINISH);
    }

    // ─── Time-Lapse ───

    public void resumeRecording() {
        if (timeLapseRecorder != null && currentLevel != null) {
            if (!timeLapseRecorder.isRecording()) {
                timeLapseRecorder.resumeRecording(getApplication(), currentLevel.getFileName());
            }
        }
    }

    public void pauseRecording() {
        if (timeLapseRecorder != null) {
            timeLapseRecorder.stopRecording();
            timeLapseRecorder.save(getApplication());
        }
    }

    // ─── Before Image ───

    private void saveBeforeImage(File baseImageFile) {
        try {
            File beforeDir = new File(getApplication().getFilesDir(), "before_images");
            beforeDir.mkdirs();
            String[] pathParts = currentLevel.getFileName().split("/");
            String filename = pathParts[pathParts.length - 1];
            File dest = new File(beforeDir, filename + ".png");

            if (!dest.exists()) {
                FileInputStream fis = new FileInputStream(baseImageFile);
                FileOutputStream fos = new FileOutputStream(dest);
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) > 0)
                    fos.write(buf, 0, len);
                fos.flush();
                fos.close();
                fis.close();
            }
            beforeImagePath = dest.getAbsolutePath();
        } catch (Exception e) {
            LogUtils.e(TAG, "Error saving before image", e);
        }
    }

    public String getBeforeImagePath() {
        return beforeImagePath;
    }

    // ─── Cleanup ───

    @Override
    protected void onCleared() {
        super.onCleared();
        if (periodicTimer != null) {
            periodicTimer.cancel();
            periodicTimer = null;
        }
        handler.removeCallbacksAndMessages(null);
        PRDownloader.cancelAll();
    }

    // ─── Inner Classes / Enums ───

    public enum LoadingState {
        IDLE, LOADING, LOADED, ERROR, MEMORY_ERROR
    }

    public enum HintAnimationType {
        SCALE, BOUNCE, SLIDE, ROTATE
    }

    public enum NavigationEvent {
        FINISH, SHARE
    }

    public static class BitmapPair {
        public final Bitmap baseBitmap;
        public final Bitmap coloredBitmap;

        public BitmapPair(Bitmap base, Bitmap colored) {
            this.baseBitmap = base;
            this.coloredBitmap = colored;
        }
    }

    public static class ColorProgressUpdate {
        public final int color;
        public final int progress;

        public ColorProgressUpdate(int color, int progress) {
            this.color = color;
            this.progress = progress;
        }
    }

    public static class SavedImageResult {
        public final String imagePath;
        public final String beforeImagePath;

        public SavedImageResult(String imagePath, String beforeImagePath) {
            this.imagePath = imagePath;
            this.beforeImagePath = beforeImagePath;
        }
    }
}