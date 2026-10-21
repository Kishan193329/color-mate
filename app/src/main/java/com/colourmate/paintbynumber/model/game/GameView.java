package com.colourmate.paintbynumber.model.game;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.GameActivity;
import com.colourmate.paintbynumber.ads.AppPreference;
import com.colourmate.paintbynumber.ads.Keys;
import com.colourmate.paintbynumber.model.model.ColorArea;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.model.model.Step;
import com.colourmate.paintbynumber.util.DataManager;
import com.colourmate.paintbynumber.util.GameSaver;
import com.colourmate.paintbynumber.util.LogUtils;
import com.colourmate.paintbynumber.util.SoundUtils;
import com.colourmate.paintbynumber.util.TimeLapseRecorder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class GameView extends View {

    private static final String TAG = "GameView";

    // ─── Constants ───
    private static final int ADS_PER_LEVEL = 5;
    private static final int HINT_ANIMATION_DURATION_MS = 4000;
    private static final float HINT_TEXT_SCALE_FACTOR = 0.4f;
    private static final int MAX_HINT_ANIMATION_TIME_MS = 500;
    private static final float MAX_SCALE_FACTOR = 25.0f;
    private static final int TOUCH_MODE_DRAG = 0;
    private static final int TOUCH_MODE_ZOOM = 1;
    public static final int SHARE_IMAGE_SIZE = 1024;
    private static final float TEXT_SCALE_FACTOR = 0.8f;
    private static final int COLOR_TRANSPARENT = 0;
    private static final int COLOR_BLACK = -16777216;
    private static final int COLOR_HIGHLIGHTED = -3355444;

    // Modernise the old paid-content palette: a gentle saturation/brightness lift so dull, dated
    // colours (the owner's "old pink") read fresh and vibrant. Set false to ship the original colours.
    private static final boolean MODERN_COLOR_BOOST = true;

    private static final long LONG_CLICK_THRESHOLD = 500;
    private static final int SPARK_COUNT = 6;
    private static final float SPARK_RADIUS_DP = 12f;
    // 8 specks read as a fizzle rather than a splash; 14 fills the burst without costing
    // anything measurable (they are pooled and die within ~600 ms).
    private static final int PARTICLE_COUNT = 14;
    private static final int MAX_PARTICLES = 70;

    // ─── Tool Enum ───
    public enum GameTool {
        NONE, BRUSH, BOMB, BUCKET, LOUPE, RADAR, SURGE, MAGIC_WAND, MAGNET
    }

    // ─── Listeners ───
    public interface ColorFinishedListener {
        void onColorFinished(int colorValue);
    }

    public interface ColorProgressListener {
        void onColorProgress(int color, int progress);
    }

    public interface GameFinishedListener {
        void onGameFinished();
    }

    private ColorFinishedListener colorFinishedListener;
    private ColorProgressListener colorProgressListener;
    private GameFinishedListener gameFinishedListener;

    // ─── Tool Deactivation Callback ───
    public interface ToolDeactivatedListener {
        void onToolDeactivated();
    }
    private ToolDeactivatedListener toolDeactivatedListener;

    public void setOnToolDeactivated(ToolDeactivatedListener listener) {
        this.toolDeactivatedListener = listener;
    }

    private void notifyToolDeactivated() {
        activeTool = GameTool.NONE;
        if (toolDeactivatedListener != null) {
            new Handler(Looper.getMainLooper()).post(() -> toolDeactivatedListener.onToolDeactivated());
        }
    }

    // ─── Bitmaps ───
    private Bitmap blackLineBitmap;
    private Bitmap resultBitmap;

    // ─── Image Dimensions ───
    private int imageWidth;
    private int imageHeight;

    // ─── Pixel Arrays ───
    private int[] originalColorPixels;
    private int[] currentColorPixels;

    // ─── Screen Properties ───
    private int screenCenterX;
    private int screenCenterY;

    // ─── Scale & Position ───
    private float currentScale;
    private float initialScale;
    private float maxScale;
    private final Size imageOffset = new Size();

    // ─── Touch Handling ───
    private int touchMode;
    private boolean isDraggingImage;
    private float touchStartX;
    private float touchStartY;
    private float previousFingerSpacing;
    private float previousMidpointX;
    private float previousMidpointY;
    private final PointF zoomMidpoint = new PointF();

    // Long-click
    private boolean isPotentialLongClick = false;
    private float initialTouchX = 0;
    private float initialTouchY = 0;
    private ColorArea pendingLongClickArea = null;
    private final Handler longClickHandler = new Handler(Looper.getMainLooper());
    private Runnable longClickRunnable;

    // ─── Text Sizing ───
    private float minTextSize;
    private float maxTextSize;

    // ─── Game Data ───
    private Level currentLevel;
    private final List<ColorArea> colorAreas = new ArrayList<>();
    private ArrayList<ColorItem> colorItems;
    private int completedAreasCount;

    // Level init runs on a background thread; the view stays blank and ignores
    // touches until it finishes. Set on the UI thread only.
    private boolean levelReady = false;
    private volatile boolean levelInitCancelled = false;

    // ─── OPTIMIZATION: Spatial Index for Color Areas ───
    private SparseArray<List<ColorArea>> colorToAreasMap;     // color → areas
//    private int[][] spatialGrid;                                // grid-based lookup
//    private int spatialGridCellSize = 50;                       // pixels per cell
//    private int spatialGridCols;
//    private int spatialGridRows;

    // ─── OPTIMIZATION: Cached number strings ───
    private final SparseArray<String> numberStringCache = new SparseArray<>();

    // ─── OPTIMIZATION: Dirty region tracking ───

    // ─── OPTIMIZATION: Reusable flood fill queue ───
    private final Deque<Point> fillQueue = new ArrayDeque<>(1024);

    // ─── Feature Flags ───
    private boolean isLowResolutionMode;
    private boolean isShowingHint;
    private boolean shouldShowAd;
    private boolean enableLongClickColorSelect = true;
    private boolean autoFillMode = false;
    private boolean isSmartZoomEnabled = true;

    // ─── Advanced Tools State ───
    private GameTool activeTool = GameTool.NONE;
    private boolean isSurgeActive = false;
    private long surgeStartTime = 0;
    private static final long SURGE_DURATION = 15000;
    private int brushRadius = 5;
    private int remainingToolUses = -1; // -1 for unlimited

    // Magnifier
    private boolean isLoupeVisible = false;
    private final PointF loupePosition = new PointF();
    private static final float LOUPE_RADIUS = 120f;
    private static final float LOUPE_ZOOM = 2.5f;

    // Radar
    private final List<Point> radarHighlights = new ArrayList<>();
    private long radarTriggerTime = 0;
    private static final long RADAR_DURATION = 3000;

    private int currentSelectedColor = COLOR_BLACK;

    // Which colour is currently painted as COLOR_HIGHLIGHTED in currentColorPixels.
    private int highlightedColor;
    private boolean highlightApplied = false;

    // ─── Vibrator ───
    private Vibrator vibrator;

    // ─── TimeLapse ───
    private TimeLapseRecorder timeLapseRecorder;

    // ─── Ad Tracking ───
    private final Set<Integer> adTriggerProgressPoints = new HashSet<>();

    // ─── Drawing Tools (Reused - no allocation per frame) ───
    private final Paint textPaint = new Paint();
    private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surgeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect drawingRect = new Rect();

    // ─── OPTIMIZATION: Reusable Paint for particles ───
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path reusablePath = new Path();
    /** Reused by the magnifier each frame instead of allocating a Matrix per draw. */
    private final Matrix loupeMatrix = new Matrix();
    private final RectF reusableRectF = new RectF();

    // ─── Spark Animation ───
    private float sparkProgress = -1f;
    private float sparkScreenX;
    private float sparkScreenY;
    private int sparkColor;
    private ValueAnimator sparkAnimator;
    private final float[] sparkAngles = new float[SPARK_COUNT];

    // ─── Ripple Effect ───
    private float rippleProgress = -1f;
    private float rippleCenterX, rippleCenterY;
    private int rippleColor;
    private float rippleMaxRadius;
    private ValueAnimator rippleAnimator;

    // ─── Surge Glow ───
    private float surgeGlowAlpha = 0f;
    private ValueAnimator surgeGlowAnimator;

    // ─── Particles ───
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    // ─── OPTIMIZATION: Frame timing ───

    // ─── Particle Class (Optimized) ───
    private static class Particle {
        float x, y, vx, vy, alpha, size, rotation, rotationSpeed;
        int color, type;
        long lifeTime, startTime;

        void init(float x, float y, int color, int type, Random random) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.type = type;
            this.startTime = System.currentTimeMillis();
            this.lifeTime = 1000 + random.nextInt(1000);
            this.alpha = 1.0f;
            this.size = 15f + random.nextFloat() * 25f;
            float angle = random.nextFloat() * 2f * (float) Math.PI;
            float speed = 2f + random.nextFloat() * 5f;
            this.vx = (float) Math.cos(angle) * speed;
            this.vy = (float) Math.sin(angle) * speed - 2f;
            this.rotation = random.nextFloat() * 360f;
            this.rotationSpeed = (random.nextFloat() - 0.5f) * 10f;
        }

        boolean update() {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > lifeTime) return false;
            x += vx;
            y += vy;
            vy += 0.05f;
            rotation += rotationSpeed;
            alpha = 1.0f - (float) elapsed / lifeTime;
            return true;
        }
    }

    // ─── Particle Pool (Avoid GC) ───
    private final List<Particle> particlePool = new ArrayList<>();

    private Particle obtainParticle() {
        if (!particlePool.isEmpty()) {
            return particlePool.remove(particlePool.size() - 1);
        }
        return new Particle();
    }

    private void recycleParticle(Particle p) {
        if (particlePool.size() < 100) {
            particlePool.add(p);
        }
    }

    // ─── Constructors ───

    public GameView(Context context) {
        super(context);
        init();
    }

    public GameView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GameView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Enable hardware acceleration for this view
        setLayerType(LAYER_TYPE_HARDWARE, null);

        textPaint.setAntiAlias(true);
        textPaint.setFilterBitmap(true);
    }

    // ─── Public Setters ───

    public void setOnColorFinished(ColorFinishedListener listener) {
        this.colorFinishedListener = listener;
    }

    public void setOnColorProgress(ColorProgressListener listener) {
        this.colorProgressListener = listener;
    }

    public void setOnGameFinished(GameFinishedListener listener) {
        this.gameFinishedListener = listener;
    }

    public void setTimeLapseRecorder(TimeLapseRecorder recorder) {
        this.timeLapseRecorder = recorder;
    }

    public void setEnableLongClickColorSelect(boolean enable) {
        this.enableLongClickColorSelect = enable;
    }

    public void setAutoFillMode(boolean enabled) {
        this.autoFillMode = enabled;
    }

    public boolean isAutoFillMode() {
        return this.autoFillMode;
    }

    public void setSmartZoomEnabled(boolean enabled) {
        this.isSmartZoomEnabled = enabled;
    }

    public boolean isSmartZoomEnabled() {
        return this.isSmartZoomEnabled;
    }

    public void setBrushRadius(int radius) {
        this.brushRadius = Math.max(1, Math.min(20, radius));
    }

    public int getBrushRadius() {
        return this.brushRadius;
    }

    public void setActiveTool(GameTool tool) {
        setActiveTool(tool, -1);
    }

    public void setActiveTool(GameTool tool, int uses) {
        this.activeTool = tool;
        this.remainingToolUses = uses;

        switch (tool) {
            case RADAR:
                triggerRadarScanner();
                break;
            case SURGE:
                startChromaSurge();
                break;
            case MAGIC_WAND:
                useMagicWand();
                break;
            case MAGNET:
                useMagnet();
                break;
        }
        invalidate();
    }

    public GameTool getActiveTool() {
        return activeTool;
    }

    public void initVibrator() {
        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    // ─── Level Initialization ───

    /** Called on the UI thread once the level is fully built and drawable. */
    public interface LevelReadyListener {
        void onLevelReady();
    }

    public void setLevel(Level level, Bitmap blackLineBitmap, Bitmap colorBitmap) {
        setLevel(level, blackLineBitmap, colorBitmap, null);
    }

    public void setLevel(Level level, Bitmap blackLineBitmap, Bitmap colorBitmap,
                         LevelReadyListener readyListener) {
        if (this.currentLevel != null) return;

        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        this.isLowResolutionMode = GameSaver.isLowRes(getContext());
        this.currentLevel = level;

        if (this.timeLapseRecorder == null) {
            this.timeLapseRecorder = new TimeLapseRecorder();
        }

        this.blackLineBitmap = blackLineBitmap;
        this.imageWidth = blackLineBitmap.getWidth();
        this.imageHeight = blackLineBitmap.getHeight();

        this.minTextSize = getContext().getResources().getDimension(R.dimen.min_text_size);
        this.maxTextSize = getContext().getResources().getDimension(R.dimen.max_text_size);

        // OPTIMIZATION: the heavy part of level init (two full getPixels, the
        // per-pixel colour-area pass, the spatial index and the saved-progress
        // flood fills) used to run here on the UI thread and froze the app for
        // hundreds of ms — seconds on big pictures. It now runs on a background
        // thread; levelReady keeps the view blank and untouchable until done.
        new Thread(() -> {
            try {
                timeLapseRecorder.startRecording(level.getFileName());

                Bitmap result = Bitmap.createBitmap(
                        imageWidth, imageHeight, Bitmap.Config.ARGB_8888);

                initializeImageScaleAndPosition();

                int pixelCount = imageWidth * imageHeight;
                this.originalColorPixels = new int[pixelCount];
                this.currentColorPixels = new int[pixelCount];

                colorBitmap.getPixels(originalColorPixels, 0, imageWidth, 0, 0, imageWidth, imageHeight);
                colorBitmap.getPixels(currentColorPixels, 0, imageWidth, 0, 0, imageWidth, imageHeight);

                // Freshen the old paid-content palette into a modern, more vibrant one. Applied to
                // BOTH buffers identically (on this background thread) so the answer, the palette and
                // the fills stay perfectly in sync. Toggle with MODERN_COLOR_BOOST.
                if (MODERN_COLOR_BOOST) {
                    applyModernColorBoost(originalColorPixels);
                    applyModernColorBoost(currentColorPixels);
                }

                processPixelsAndCreateColorAreas();

                // Build spatial index AFTER color areas are created
                buildSpatialIndex();

                // Restore progress
                timeLapseRecorder.stopRecording();
                restoreSavedProgress();
                timeLapseRecorder.resumeRecording(getContext(), level.getFileName());

                // Paint the first frame here too, so the UI thread only gets a
                // ready-to-draw bitmap (same work updateResultImage() does).
                result.setPixels(currentColorPixels, 0, imageWidth,
                        0, 0, imageWidth, imageHeight);
                new Canvas(result).drawBitmap(blackLineBitmap, 0f, 0f, null);

                calculateAdTriggerPoints();

                // Pre-cache number strings
                precacheNumberStrings();

                post(() -> {
                    if (levelInitCancelled) {
                        result.recycle();
                        return;
                    }
                    this.resultBitmap = result;
                    this.levelReady = true;

                    // A colour can be chosen while the level is still building (the selected-colour
                    // LiveData fires early), and selectColor() bails out in that case. LiveData will
                    // not re-deliver the same value, so re-apply it here — without this nothing is
                    // ever highlighted and, since a fill needs COLOR_HIGHLIGHTED, taps do nothing.
                    if (currentSelectedColor != COLOR_BLACK) {
                        highlightApplied = false;
                        selectColor(currentSelectedColor);
                    }

                    if (readyListener != null) readyListener.onLevelReady();
                    invalidate();
                });

            } catch (OutOfMemoryError e) {
                Log.e(TAG, "OOM while creating bitmaps", e);
                handleMemoryError();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing level", e);
            }
        }).start();
    }

    // ─── OPTIMIZATION: Spatial Index ───

    private void buildSpatialIndex() {
        // Color → Areas map
        colorToAreasMap = new SparseArray<>();
        for (ColorArea area : colorAreas) {
            int color = area.getColor();
            List<ColorArea> list = colorToAreasMap.get(color);
            if (list == null) {
                list = new ArrayList<>();
                colorToAreasMap.put(color, list);
            }
            list.add(area);
        }

        // Grid-based spatial index for nearest-area search
//        spatialGridCols = (imageWidth / spatialGridCellSize) + 1;
//        spatialGridRows = (imageHeight / spatialGridCellSize) + 1;
        // Store indices into colorAreas list
        // Using a flat list approach: each cell stores start index in a separate list
        // For simplicity, store area index per cell
//        spatialGrid = null; // We'll use colorToAreasMap + KD-tree-lite approach instead

        LogUtils.d(TAG, "Built spatial index: " + colorToAreasMap.size()
                + " colors, " + colorAreas.size() + " areas");
    }

    // ─── OPTIMIZATION: Pre-cache number strings ───

    private void precacheNumberStrings() {
        if (colorItems == null) return;
        for (ColorItem item : colorItems) {
            int num = item.getNumber() + 1;
            numberStringCache.put(item.getColor(), String.valueOf(num));
        }
    }

    // ─── OPTIMIZATION: Optimized Flood Fill (Iterative with ArrayDeque) ───

    private void floodFillOptimized(int startX, int startY, int targetColor) {
        fillQueue.clear();
        fillQueue.add(new Point(startX, startY));

        while (!fillQueue.isEmpty()) {
            Point p = fillQueue.poll();
            if (p == null) continue;

            // Scanline fill for better performance
            int x = p.x;
            int y = p.y;

            // Find left boundary
            int leftX = x;
            while (leftX > 0) {
                int idx = (y * imageWidth) + (leftX - 1);
                if (currentColorPixels[idx] == targetColor
                        || originalColorPixels[idx] != targetColor) break;
                leftX--;
            }

            // Find right boundary
            int rightX = x;
            while (rightX < imageWidth - 1) {
                int idx = (y * imageWidth) + (rightX + 1);
                if (currentColorPixels[idx] == targetColor
                        || originalColorPixels[idx] != targetColor) break;
                rightX++;
            }

            // Fill the scanline
            boolean checkAbove = true;
            boolean checkBelow = true;

            for (int fillX = leftX; fillX <= rightX; fillX++) {
                int idx = (y * imageWidth) + fillX;
                currentColorPixels[idx] = targetColor;

                // Record for time-lapse
                if (timeLapseRecorder != null && timeLapseRecorder.isRecording()) {
                    timeLapseRecorder.recordStep(fillX, y, targetColor);
                }

                // Check pixel above
                if (y > 0) {
                    int aboveIdx = ((y - 1) * imageWidth) + fillX;
                    if (currentColorPixels[aboveIdx] != targetColor
                            && originalColorPixels[aboveIdx] == targetColor) {
                        if (checkAbove) {
                            fillQueue.add(new Point(fillX, y - 1));
                            checkAbove = false;
                        }
                    } else {
                        checkAbove = true;
                    }
                }

                // Check pixel below
                if (y < imageHeight - 1) {
                    int belowIdx = ((y + 1) * imageWidth) + fillX;
                    if (currentColorPixels[belowIdx] != targetColor
                            && originalColorPixels[belowIdx] == targetColor) {
                        if (checkBelow) {
                            fillQueue.add(new Point(fillX, y + 1));
                            checkBelow = false;
                        }
                    } else {
                        checkBelow = true;
                    }
                }
            }
        }
    }

    // ─── OPTIMIZATION: Batched Result Image Update ───

    private boolean resultImageDirty = false;

    private void markResultDirty() {
        resultImageDirty = true;
    }

    private void updateResultImageIfNeeded() {
        if (resultImageDirty && resultBitmap != null) {
            resultBitmap.setPixels(currentColorPixels, 0, imageWidth,
                    0, 0, imageWidth, imageHeight);
            Canvas canvas = new Canvas(resultBitmap);
            canvas.drawBitmap(blackLineBitmap, 0f, 0f, null);
            resultImageDirty = false;
        }
    }

    private void updateResultImage() {
        if (resultBitmap == null) return;
        resultBitmap.setPixels(currentColorPixels, 0, imageWidth,
                0, 0, imageWidth, imageHeight);
        Canvas canvas = new Canvas(resultBitmap);
        canvas.drawBitmap(blackLineBitmap, 0f, 0f, null);
    }

    // ─── Public API ───

    public List<ColorItem> getColors() {
        return this.colorItems;
    }

    public void selectColor(int colorValue) {
        this.currentSelectedColor = colorValue;
        if (!levelReady) return; // level still building on the background thread

        // The pass below is O(pixels) and rewrites the whole result bitmap. Re-selecting the
        // colour that is already highlighted cannot change anything, so skip it entirely.
        // (Tracked separately from currentSelectedColor, which is also set on the early return
        // above — otherwise a colour picked before the level was ready would never highlight.)
        if (highlightApplied && highlightedColor == colorValue) return;

        boolean hasHighlight = false;

        for (int i = 0; i < originalColorPixels.length; i++) {
            if (currentColorPixels[i] == COLOR_HIGHLIGHTED) {
                currentColorPixels[i] = COLOR_TRANSPARENT;
            }
            if (originalColorPixels[i] == colorValue
                    && currentColorPixels[i] == COLOR_TRANSPARENT) {
                currentColorPixels[i] = COLOR_HIGHLIGHTED;
                hasHighlight = true;
            }
        }

        highlightedColor = colorValue;
        highlightApplied = true;

        updateResultImage();
        invalidate();

        if (hasHighlight && isSmartZoomEnabled) {
            autoFocusNextColorArea();
        }
    }

    public void zoomOutImage() {
        initializeImageScaleAndPosition();
        invalidate();
    }

    public Bitmap getWinPreview() {
        return DataManager.getInstance().generatePreview(resultBitmap, SHARE_IMAGE_SIZE);
    }

    public Bitmap getOriginalBitmap() {
        if (blackLineBitmap == null) return null;
        Bitmap bmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        c.drawBitmap(blackLineBitmap, 0, 0, null);
        return bmp;
    }

    public boolean isShowAd() {
        return this.shouldShowAd;
    }

    public int getOverallProgress() {
        if (colorItems == null || colorItems.isEmpty()) return 0;
        int total = 0, completed = 0;
        for (ColorItem ci : colorItems) {
            total += ci.getTotalAreas();
            completed += ci.getCompletedAreas();
        }
        return total == 0 ? 0 : (int) ((completed * 100f) / total);
    }

    public ColorItem getColorItem(int color) {
        if (colorItems == null) return null;
        for (ColorItem item : colorItems) {
            if (item.getColor() == color) return item;
        }
        return null;
    }

    // ─── Hint System ───

    public boolean showHint() {
        if (GameSaver.getHint(getContext()) <= 0) return false;

        // OPTIMIZATION: Use colorToAreasMap for faster lookup
        for (ColorArea area : colorAreas) {
            int pixelIndex = area.getY() * imageWidth + area.getX();
            if (pixelIndex >= 0 && pixelIndex < currentColorPixels.length
                    && currentColorPixels[pixelIndex] == COLOR_HIGHLIGHTED) {
                animateHintToArea(area);

                if (!AppPreference.getBoolean(getContext(), Keys.KEY_IS_PREMIUM, false)) {
                    GameSaver.setDescreseHint(getContext());
                }

                if (getContext() instanceof GameActivity) {
                    ((GameActivity) getContext()).runOnUiThread(() ->
                            ((GameActivity) getContext()).animateHintCounterScale());
                }
                return true;
            }
        }
        return true;
    }

    // ─── Drawing (Optimized) ───

    @Override
    public void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (!levelReady || currentLevel == null || resultBitmap == null
                || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        // OPTIMIZATION: Batch result image update
        updateResultImageIfNeeded();

        // Core drawing
        drawScaledImage(canvas);
        drawNumberLabelsOptimized(canvas);

        // Tool overlays
        drawAdvancedToolsOverlays(canvas);

        // Animations (only if active)
        if (sparkProgress >= 0f) drawSparkAnimation(canvas);
        if (rippleProgress >= 0f) drawRippleEffect(canvas);
        if (isSurgeActive) drawSurgeGlow(canvas);
        if (!particles.isEmpty()) drawParticles(canvas);
    }

    private void drawScaledImage(Canvas canvas) {
        drawingRect.set(
                (int) imageOffset.width,
                (int) imageOffset.height,
                (int) (imageWidth * currentScale + imageOffset.width),
                (int) (imageHeight * currentScale + imageOffset.height));
        canvas.drawBitmap(resultBitmap, null, drawingRect, textPaint);
    }

    // ─── OPTIMIZATION: Optimized Number Label Drawing ───

    private void drawNumberLabelsOptimized(Canvas canvas) {
        textPaint.setColor(ViewCompat.MEASURED_STATE_MASK);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Calculate visible bounds in image coordinates
        float viewLeft = -imageOffset.width / currentScale;
        float viewTop = -imageOffset.height / currentScale;
        float viewRight = (getWidth() - imageOffset.width) / currentScale;
        float viewBottom = (getHeight() - imageOffset.height) / currentScale;

        // Add padding for text size
        float padding = maxTextSize / currentScale;
        viewLeft -= padding;
        viewTop -= padding;
        viewRight += padding;
        viewBottom += padding;

        for (ColorArea area : colorAreas) {
            // OPTIMIZATION: Quick bounds check before expensive calculations
            int areaX = area.getX();
            int areaY = area.getY();

            if (areaX < viewLeft || areaX > viewRight
                    || areaY < viewTop || areaY > viewBottom) {
                continue;
            }

            float textSize = area.getTextSize() * currentScale * TEXT_SCALE_FACTOR;

            if (textSize < minTextSize) continue;
            if (textSize > maxTextSize) textSize = maxTextSize;

            int pixelIndex = (areaY * imageWidth) + areaX;

            // Only draw if unfilled
            if (pixelIndex >= 0 && pixelIndex < currentColorPixels.length
                    && currentColorPixels[pixelIndex] != area.getColor()) {

                textPaint.setTextSize(textSize);

                float screenX = (areaX * currentScale) + imageOffset.width;
                float textVerticalOffset =
                        (textPaint.descent() + textPaint.ascent()) / 2f;
                float screenY = (areaY * currentScale) - textVerticalOffset
                        + imageOffset.height;

                // OPTIMIZATION: Use cached number strings
                String numStr = numberStringCache.get(area.getColor());
                if (numStr == null) {
                    numStr = String.valueOf(getColorNumber(area.getColor()) + 1);
                    numberStringCache.put(area.getColor(), numStr);
                }

                canvas.drawText(numStr, screenX, screenY, textPaint);
            }
        }
    }

    // ─── Touch Handling (Optimized) ───

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isShowingHint || currentLevel == null || !levelReady) {
            return super.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handleTouchDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                handleTouchMove(event);
                break;
            case MotionEvent.ACTION_UP:
                handleTouchRelease();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                handleSecondFingerDown(event);
                isPotentialLongClick = false;
                pendingLongClickArea = null;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                handlePointerUp(event);
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelLongClick();
                isDraggingImage = false;
                break;
        }
        return true;
    }

    private void handleTouchDown(MotionEvent event) {
        try {
            cancelLongClick();

            float touchX = event.getX();
            float touchY = event.getY();

            initialTouchX = touchX;
            initialTouchY = touchY;
            isPotentialLongClick = false;
            pendingLongClickArea = null;
            isDraggingImage = false;

            int imageX = (int) ((touchX - imageOffset.width) / currentScale);
            int imageY = (int) ((touchY - imageOffset.height) / currentScale);

            // Loupe
            if (activeTool == GameTool.LOUPE) {
                isLoupeVisible = true;
                loupePosition.set(touchX, touchY);
                invalidate();
            }

            // Check bounds
            if (imageX >= 0 && imageX < imageWidth
                    && imageY >= 0 && imageY < imageHeight) {

                int pixelIndex = (imageWidth * imageY) + imageX;

                // Tool-specific actions
                switch (activeTool) {
                    case BOMB:
                        if (useColorBomb(imageX, imageY)) {
                            decrementToolUses();
                        }
                        return;
                    case BUCKET:
                        if (useSmartBucket(imageX, imageY)) {
                            decrementToolUses();
                        }
                        return;
                    case BRUSH:
                        processBrushMove(imageX, imageY);
                        break;
                }

                if (isSurgeActive) {
                    processSurgeTap(imageX, imageY);
                }

                if (pixelIndex < 0 || pixelIndex >= currentColorPixels.length) {
                    touchStartX = touchX;
                    touchStartY = touchY;
                    touchMode = TOUCH_MODE_DRAG;
                    return;
                }

                // Tap on highlighted pixel
                if (currentColorPixels[pixelIndex] == COLOR_HIGHLIGHTED) {
                    handleColorAreaTap(imageX, imageY, pixelIndex);
                    return;
                }

                // Long-click detection
                if (enableLongClickColorSelect) {
                    ColorArea nearestArea = findNearestUnfilledAreaOptimized(imageX, imageY);
                    if (nearestArea != null) {
                        pendingLongClickArea = nearestArea;
                        isPotentialLongClick = true;

                        longClickRunnable = () -> {
                            if (isPotentialLongClick && pendingLongClickArea != null) {
                                triggerLongClick();
                            }
                        };
                        longClickHandler.postDelayed(longClickRunnable, LONG_CLICK_THRESHOLD);
                    }
                }
            }

            touchStartX = touchX;
            touchStartY = touchY;
            touchMode = TOUCH_MODE_DRAG;

        } catch (Exception e) {
            Log.e(TAG, "Error in handleTouchDown", e);
        }
    }

    private void handleTouchMove(MotionEvent event) {
        float touchX = event.getX();
        float touchY = event.getY();

        float dx = Math.abs(touchX - initialTouchX);
        float dy = Math.abs(touchY - initialTouchY);
        float totalMovement = (float) Math.sqrt(dx * dx + dy * dy);

        // Brush mode: prioritize painting
        if (activeTool == GameTool.BRUSH && touchMode == TOUCH_MODE_DRAG) {
            cancelLongClick();
            int imageX = (int) ((touchX - imageOffset.width) / currentScale);
            int imageY = (int) ((touchY - imageOffset.height) / currentScale);
            processBrushMove(imageX, imageY);
            return;
        }

        // Loupe mode
        if (activeTool == GameTool.LOUPE && touchMode == TOUCH_MODE_DRAG) {
            cancelLongClick();
            loupePosition.set(touchX, touchY);
            invalidate();
            return;
        }

        if (isPotentialLongClick) {
            if (totalMovement > getLongClickCancelThreshold()) {
                cancelLongClick();
                isDraggingImage = true;
            }
            return;
        }

        if (!isDraggingImage && totalMovement > getDragThreshold()) {
            isDraggingImage = true;
        }

        if (isDraggingImage) {
            if (touchMode == TOUCH_MODE_DRAG) {
                handleDragMove(touchX, touchY);
            } else if (touchMode == TOUCH_MODE_ZOOM) {
                handleZoomMove(event);
            }
            invalidate();
        }
    }

    private void handleTouchRelease() {
        if (longClickRunnable != null) {
            longClickHandler.removeCallbacks(longClickRunnable);
            longClickRunnable = null;
        }
        isPotentialLongClick = false;
        pendingLongClickArea = null;
        isDraggingImage = false;
        isLoupeVisible = false;

        // Final brush save when finger is lifted
        if (activeTool == GameTool.BRUSH && brushFillCountSinceLastSave > 0) {
            brushFillCountSinceLastSave = 0;
            lastBrushSaveTime = System.currentTimeMillis();

            updateCompletedAreasForColor(currentSelectedColor);
            updateResultImage();
            SoundUtils.playSound(GlobalApplication.getAppContext(), SoundUtils.GameSound.TAP);
            new SavePreviewTask().savePreview(currentLevel, resultBitmap);

            // Decrement uses for Brush (1 per stroke/activation)
            decrementToolUses();
            invalidate();
        }

        touchStartX = 0f;
        touchStartY = 0f;
    }

    private void handleSecondFingerDown(MotionEvent event) {
        cancelLongClick();
        if (event.getPointerCount() >= 2) {
            touchMode = TOUCH_MODE_ZOOM;
            previousFingerSpacing = calculateFingerSpacing(event);
            calculateZoomMidpoint(zoomMidpoint, event);
            previousMidpointX = zoomMidpoint.x;
            previousMidpointY = zoomMidpoint.y;
        }
    }

    private void handlePointerUp(MotionEvent event) {
        // When one finger is lifted, if one finger still remains, switch to dragging
        if (event.getPointerCount() == 2) {
            touchMode = TOUCH_MODE_DRAG;
            // The finger that's NOT being lifted becomes the new touch start
            int index = (event.getActionIndex() == 0) ? 1 : 0;
            touchStartX = event.getX(index);
            touchStartY = event.getY(index);
        }
    }

    // ─── OPTIMIZATION: Faster Nearest Area Search ───

    private ColorArea findNearestUnfilledAreaOptimized(int imageX, int imageY) {
        ColorArea nearestArea = null;
        float minDistSq = Float.MAX_VALUE; // Use squared distance to avoid sqrt

        float baseRadius = 80f;
        float maxSearchRadius = baseRadius / currentScale * initialScale;
        maxSearchRadius = Math.max(50f, Math.min(150f, maxSearchRadius));
        float maxDistSq = maxSearchRadius * maxSearchRadius;

        // OPTIMIZATION: Only iterate over areas of the currently selected color first
        if (currentSelectedColor != COLOR_BLACK && colorToAreasMap != null) {
            List<ColorArea> selectedColorAreas = colorToAreasMap.get(currentSelectedColor);
            if (selectedColorAreas != null) {
                nearestArea = searchAreasForNearest(
                        selectedColorAreas, imageX, imageY, maxDistSq);
                if (nearestArea != null) return nearestArea;
            }
        }

        // Fallback: search all areas
        for (ColorArea area : colorAreas) {
            int areaPixelIndex = (area.getY() * imageWidth) + area.getX();
            if (areaPixelIndex < 0 || areaPixelIndex >= currentColorPixels.length) continue;

            if (currentColorPixels[areaPixelIndex] != area.getColor()) {
                float dxA = imageX - area.getX();
                float dyA = imageY - area.getY();
                float distSq = dxA * dxA + dyA * dyA;

                if (distSq < maxDistSq && distSq < minDistSq) {
                    minDistSq = distSq;
                    nearestArea = area;
                }
            }
        }

        return nearestArea;
    }

    private ColorArea searchAreasForNearest(List<ColorArea> areas,
                                            int imageX, int imageY,
                                            float maxDistSq) {
        ColorArea nearest = null;
        float minDistSq = maxDistSq;

        for (ColorArea area : areas) {
            int pixelIndex = (area.getY() * imageWidth) + area.getX();
            if (pixelIndex < 0 || pixelIndex >= currentColorPixels.length) continue;

            if (currentColorPixels[pixelIndex] != area.getColor()) {
                float dx = imageX - area.getX();
                float dy = imageY - area.getY();
                float distSq = dx * dx + dy * dy;

                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    nearest = area;
                }
            }
        }

        return nearest;
    }

    // ─── Color Area Tap Handling ───

    private void handleColorAreaTap(int x, int y, int pixelIndex) {
        int targetColor = originalColorPixels[pixelIndex];

        startSparkAnimation(x, y, targetColor);

        if (autoFillMode) {
            if (GameSaver.isParticlesEnabled(getContext())) {
                spawnParticles(x, y, targetColor);
            }
            autoFillAllAreas(targetColor);
            return;
        }

        // OPTIMIZATION: Use optimized flood fill
        floodFillOptimized(x, y, targetColor);
        if (GameSaver.isParticlesEnabled(getContext())) {
            spawnParticles(x, y, targetColor);
        }

        completedAreasCount++;

        ColorItem targetColorItem = getColorItem(targetColor);
        if (targetColorItem != null) {
            // OPTIMIZATION: Use colorToAreasMap for faster counting
            int completedCount = countCompletedAreasForColor(targetColor);
            targetColorItem.setCompletedAreas(completedCount);

            if (colorProgressListener != null) {
                colorProgressListener.onColorProgress(
                        targetColor, targetColorItem.getProgress());
            }
        }

        if (adTriggerProgressPoints.contains(completedAreasCount)) {
            shouldShowAd = true;
        }

        // Save step
        int saveX = isLowResolutionMode ? x * 2 : x;
        int saveY = isLowResolutionMode ? y * 2 : y;
        DataManager.getInstance().saveStep(currentLevel, new Step(saveX, saveY));

        updateResultImage();
        SoundUtils.playSound(GlobalApplication.getAppContext(), SoundUtils.GameSound.TAP);
        new SavePreviewTask().savePreview(currentLevel, resultBitmap);

        // Check color completion
        if (targetColorItem != null && isColorCompleted(targetColor)) {
            targetColorItem.setFinished(true);
            startRippleAnimation(sparkScreenX, sparkScreenY, targetColor, 1.5f);

            if (colorFinishedListener != null) {
                colorFinishedListener.onColorFinished(targetColor);
            }

            if (isGameFinished()) {
                handleGameCompletion();
            }
        }

        invalidate();
    }

    // ─── OPTIMIZATION: Fast Color Area Counting ───

    private int countCompletedAreasForColor(int targetColor) {
        List<ColorArea> areas = colorToAreasMap != null
                ? colorToAreasMap.get(targetColor) : null;

        if (areas == null) {
            // Fallback to full scan
            int count = 0;
            for (ColorArea area : colorAreas) {
                if (area.getColor() == targetColor) {
                    int idx = (area.getY() * imageWidth) + area.getX();
                    if (idx >= 0 && idx < currentColorPixels.length
                            && currentColorPixels[idx] == targetColor) {
                        count++;
                    }
                }
            }
            return count;
        }

        int count = 0;
        for (ColorArea area : areas) {
            int idx = (area.getY() * imageWidth) + area.getX();
            if (idx >= 0 && idx < currentColorPixels.length
                    && currentColorPixels[idx] == targetColor) {
                count++;
            }
        }
        return count;
    }

    // ─── Auto Fill ───

    private void autoFillAllAreas(int targetColor) {
        ColorItem targetColorItem = getColorItem(targetColor);

        int filledCount = 0;
        List<ColorArea> areas = colorToAreasMap != null
                ? colorToAreasMap.get(targetColor) : null;

        if (areas == null) return;

        for (ColorArea area : areas) {
            int areaPixelIndex = (area.getY() * imageWidth) + area.getX();
            if (areaPixelIndex < 0 || areaPixelIndex >= currentColorPixels.length) continue;
            if (currentColorPixels[areaPixelIndex] == targetColor) continue;

            floodFillOptimized(area.getX(), area.getY(), targetColor);
            completedAreasCount++;
            filledCount++;

            int saveX = isLowResolutionMode ? area.getX() * 2 : area.getX();
            int saveY = isLowResolutionMode ? area.getY() * 2 : area.getY();
            DataManager.getInstance().saveStep(currentLevel, new Step(saveX, saveY));

            if (adTriggerProgressPoints.contains(completedAreasCount)) {
                shouldShowAd = true;
            }
        }

        if (filledCount == 0) return;

        if (targetColorItem != null) {
            targetColorItem.setCompletedAreas(targetColorItem.getTotalAreas());
            if (colorProgressListener != null) {
                colorProgressListener.onColorProgress(targetColor, 100);
            }
        }

        updateResultImage();
        SoundUtils.playSound(GlobalApplication.getAppContext(), SoundUtils.GameSound.TAP);
        new SavePreviewTask().savePreview(currentLevel, resultBitmap);

        if (targetColorItem != null) {
            targetColorItem.setFinished(true);
            startRippleAnimation(getWidth() / 2f, getHeight() / 2f, targetColor, 0.8f);
            if (colorFinishedListener != null) {
                colorFinishedListener.onColorFinished(targetColor);
            }
            if (isGameFinished()) handleGameCompletion();
        }

        invalidate();
        setAutoFillMode(false);
        notifyToolDeactivated();
    }

    // ─── Smart Zoom ───

    public void autoFocusNextColorArea() {
        if (colorToAreasMap == null) return;

        List<ColorArea> areas = colorToAreasMap.get(currentSelectedColor);
        if (areas == null) return;

        for (ColorArea area : areas) {
            int pixelIndex = area.getY() * imageWidth + area.getX();
            if (pixelIndex >= 0 && pixelIndex < currentColorPixels.length
                    && currentColorPixels[pixelIndex] == COLOR_HIGHLIGHTED) {
                smoothZoomTo(area, 600);
                break;
            }
        }
    }

    private void smoothZoomTo(ColorArea targetArea, long duration) {
        if (targetArea == null) return;

        final float startScale = currentScale;
        final float startOffsetX = imageOffset.width;
        final float startOffsetY = imageOffset.height;

        float calculatedScale = (minTextSize + maxTextSize) / 2f
                / (targetArea.getTextSize() * TEXT_SCALE_FACTOR);
        final float targetScale = Math.max(
                initialScale * 2f, Math.min(maxScale * 0.8f, calculatedScale));

        final float targetOffsetX = getWidth() / 2f - (targetArea.getX() * targetScale);
        final float targetOffsetY = getHeight() / 2f - (targetArea.getY() * targetScale);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(
                new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            currentScale = startScale + (targetScale - startScale) * progress;
            imageOffset.width = startOffsetX + (targetOffsetX - startOffsetX) * progress;
            imageOffset.height = startOffsetY + (targetOffsetY - startOffsetY) * progress;
            invalidate();
        });
        animator.start();
    }

    // ─── Drag & Zoom ───

    private void handleDragMove(float touchX, float touchY) {
        float newOffsetX = imageOffset.width + (touchX - touchStartX);
        float newOffsetY = imageOffset.height + (touchY - touchStartY);

        if (isOffsetWithinBounds(newOffsetX, newOffsetY)) {
            imageOffset.width = newOffsetX;
            imageOffset.height = newOffsetY;
            touchStartX = touchX;
            touchStartY = touchY;
        }
    }

    private void handleZoomMove(MotionEvent event) {
        if (event.getPointerCount() < 2) return;

        float currentSpacing = calculateFingerSpacing(event);
        if (currentSpacing == 0f) return;

        calculateZoomMidpoint(zoomMidpoint, event);

        // Pan: track midpoint movement between two fingers
        float midDx = zoomMidpoint.x - previousMidpointX;
        float midDy = zoomMidpoint.y - previousMidpointY;

        // Zoom: pinch scale
        float scaleFactor = currentSpacing / previousFingerSpacing;
        float newScale = currentScale * scaleFactor;

        if (newScale > initialScale && newScale < maxScale) {
            currentScale = newScale;
            imageOffset.width = zoomMidpoint.x
                    - ((zoomMidpoint.x - imageOffset.width) * scaleFactor);
            imageOffset.height = zoomMidpoint.y
                    - ((zoomMidpoint.y - imageOffset.height) * scaleFactor);
        }

        // Apply pan offset on top of zoom
        float newOffsetX = imageOffset.width + midDx;
        float newOffsetY = imageOffset.height + midDy;
        if (isOffsetWithinBounds(newOffsetX, newOffsetY)) {
            imageOffset.width = newOffsetX;
            imageOffset.height = newOffsetY;
        }

        previousFingerSpacing = currentSpacing;
        previousMidpointX = zoomMidpoint.x;
        previousMidpointY = zoomMidpoint.y;
    }

    private float calculateFingerSpacing(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0f;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void calculateZoomMidpoint(PointF midpoint, MotionEvent event) {
        midpoint.set(
                (event.getX(0) + event.getX(1)) / 2f,
                (event.getY(0) + event.getY(1)) / 2f);
    }

    private boolean isOffsetWithinBounds(float offsetX, float offsetY) {
        if (offsetX > screenCenterX || offsetY > screenCenterY) return false;
        float imageRightEdge = (imageWidth * currentScale) + offsetX;
        float imageBottomEdge = (imageHeight * currentScale) + offsetY;
        return imageRightEdge >= screenCenterX && imageBottomEdge >= screenCenterY;
    }

    // ─── Color Completion Checks ───

    private boolean isColorCompleted(int colorValue) {
        List<ColorArea> areas = colorToAreasMap != null
                ? colorToAreasMap.get(colorValue) : null;

        if (areas != null) {
            for (ColorArea area : areas) {
                int idx = (area.getY() * imageWidth) + area.getX();
                if (idx >= 0 && idx < currentColorPixels.length
                        && currentColorPixels[idx] != colorValue) {
                    return false;
                }
            }
            return true;
        }

        // Fallback
        for (ColorArea area : colorAreas) {
            if (area.getColor() == colorValue) {
                int idx = (area.getY() * imageWidth) + area.getX();
                if (currentColorPixels[idx] != colorValue) return false;
            }
        }
        return true;
    }

    private boolean isGameFinished() {
        for (ColorItem ci : colorItems) {
            if (!ci.isFinished()) return false;
        }
        return true;
    }

    // ─── Advanced Tools ───

    private boolean useColorBomb(int x, int y) {
        int radius = 15;
        boolean filled = false;
        int lastColor = 0;

        int minX = Math.max(0, x - radius);
        int maxX = Math.min(imageWidth - 1, x + radius);
        int minY = Math.max(0, y - radius);
        int maxY = Math.min(imageHeight - 1, y + radius);
        int radiusSq = radius * radius;

        for (int i = minX; i <= maxX; i++) {
            for (int j = minY; j <= maxY; j++) {
                if ((i - x) * (i - x) + (j - y) * (j - y) <= radiusSq) {
                    int index = (j * imageWidth) + i;
                    int correctColor = originalColorPixels[index];
                    if (correctColor != COLOR_BLACK
                            && currentColorPixels[index] != correctColor) {
                        fillSinglePixel(i, j, correctColor);
                        lastColor = correctColor;
                        filled = true;
                    }
                }
            }
        }

        if (filled) {
            hapticForTool(GameTool.BOMB);
            startSparkAnimation(x, y, lastColor);

            // Save step so progress is restored on reopen
            int saveX = isLowResolutionMode ? x * 2 : x;
            int saveY = isLowResolutionMode ? y * 2 : y;
            DataManager.getInstance().saveStep(currentLevel, new Step(saveX, saveY));

            updateCompletedAreasForAllColors();
            updateResultImage();
            SoundUtils.playSound(GlobalApplication.getAppContext(), SoundUtils.GameSound.TAP);
            new SavePreviewTask().savePreview(currentLevel, resultBitmap);
            invalidate();
        }
        return filled;
    }

    private boolean useSmartBucket(int x, int y) {
        int index = (y * imageWidth) + x;
        if (index < 0 || index >= originalColorPixels.length) return false;

        int targetColor = originalColorPixels[index];
        if (targetColor != COLOR_BLACK && currentColorPixels[index] != targetColor) {
            floodFillOptimized(x, y, targetColor);
            completedAreasCount++;
            hapticForTool(GameTool.BUCKET);
            startSparkAnimation(x, y, targetColor);

            int saveX = isLowResolutionMode ? x * 2 : x;
            int saveY = isLowResolutionMode ? y * 2 : y;
            DataManager.getInstance().saveStep(currentLevel, new Step(saveX, saveY));

            updateCompletedAreasForColor(targetColor);
            updateResultImage();
            SoundUtils.playSound(GlobalApplication.getAppContext(), SoundUtils.GameSound.TAP);
            new SavePreviewTask().savePreview(currentLevel, resultBitmap);
            invalidate();
            return true;
        }
        return false;
    }

    private void decrementToolUses() {
        if (remainingToolUses > 0) {
            remainingToolUses--;
            if (remainingToolUses == 0) {
                notifyToolDeactivated();
                Toast.makeText(getContext(), "Tool charges finished!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), remainingToolUses + " charges left!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ─── Brush debounce fields ───
    private long lastBrushSaveTime = 0;
    private static final long BRUSH_SAVE_INTERVAL_MS = 500;
    private int brushFillCountSinceLastSave = 0;

    private void processBrushMove(int x, int y) {
        int targetColor = currentSelectedColor;
        if (targetColor == COLOR_BLACK) return;

        boolean filled = false;
        int minX = Math.max(0, x - brushRadius);
        int maxX = Math.min(imageWidth - 1, x + brushRadius);
        int minY = Math.max(0, y - brushRadius);
        int maxY = Math.min(imageHeight - 1, y + brushRadius);
        int radiusSq = brushRadius * brushRadius;

        for (int i = minX; i <= maxX; i++) {
            for (int j = minY; j <= maxY; j++) {
                if ((i - x) * (i - x) + (j - y) * (j - y) <= radiusSq) {
                    int idx = (j * imageWidth) + i;
                    if (originalColorPixels[idx] == targetColor
                            && currentColorPixels[idx] != targetColor) {
                        fillSinglePixel(i, j, targetColor);
                        filled = true;
                    }
                }
            }
        }

        if (filled) {
            hapticForTool(GameTool.BRUSH);
            brushFillCountSinceLastSave++;

            // Debounced save & progress update to avoid excessive I/O during fast swiping
            long now = System.currentTimeMillis();
            if (now - lastBrushSaveTime > BRUSH_SAVE_INTERVAL_MS) {
                lastBrushSaveTime = now;
                brushFillCountSinceLastSave = 0;

                // Save step for restore
                int saveX = isLowResolutionMode ? x * 2 : x;
                int saveY = isLowResolutionMode ? y * 2 : y;
                DataManager.getInstance().saveStep(currentLevel, new Step(saveX, saveY));

                // Update completion tracking
                updateCompletedAreasForColor(targetColor);

                // Save preview
                updateResultImage();
                new SavePreviewTask().savePreview(currentLevel, resultBitmap);
            }

            markResultDirty();
            invalidate();
        }
    }

    private void processSurgeTap(int x, int y) {
        if (!isSurgeActive) return;
        if (System.currentTimeMillis() - surgeStartTime > SURGE_DURATION) {
            isSurgeActive = false;
            notifyToolDeactivated();
            Toast.makeText(getContext(), "Chroma Surge Expired!", Toast.LENGTH_SHORT).show();
            return;
        }

        int index = (y * imageWidth) + x;
        if (index < 0 || index >= originalColorPixels.length) return;

        int correctColor = originalColorPixels[index];
        if (correctColor != COLOR_BLACK && currentColorPixels[index] != correctColor) {
            floodFillOptimized(x, y, correctColor);
            completedAreasCount++;
            startSparkAnimation(x, y, correctColor);

            int saveX = isLowResolutionMode ? x * 2 : x;
            int saveY = isLowResolutionMode ? y * 2 : y;
            DataManager.getInstance().saveStep(currentLevel, new Step(saveX, saveY));

            updateCompletedAreasForColor(correctColor);
            updateResultImage();
            SoundUtils.playSound(GlobalApplication.getAppContext(), SoundUtils.GameSound.TAP);
            new SavePreviewTask().savePreview(currentLevel, resultBitmap);
            invalidate();
        }
    }

    private void useMagicWand() {
        int targetColor = currentSelectedColor;
        if (targetColor == COLOR_BLACK) {
            Toast.makeText(getContext(), "Select a color first!", Toast.LENGTH_SHORT).show();
            notifyToolDeactivated();
            return;
        }

        autoFillAllAreas(targetColor);
        hapticForTool(GameTool.MAGIC_WAND);

        if (GameSaver.isParticlesEnabled(getContext())) {
            for (int i = 0; i < 3; i++) {
                spawnParticles(
                        getWidth() / 2f + (random.nextFloat() - 0.5f) * 200,
                        getHeight() / 2f + (random.nextFloat() - 0.5f) * 200,
                        targetColor);
            }
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            notifyToolDeactivated();
        }, 500);
    }

    private void useMagnet() {
        int targetColor = currentSelectedColor;
        if (targetColor == COLOR_BLACK) {
            Toast.makeText(getContext(), "Select a color first!", Toast.LENGTH_SHORT).show();
            notifyToolDeactivated();
            return;
        }

        autoFocusNextColorArea();
        performVibration(80);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            notifyToolDeactivated();
        }, 300);
    }

    private void startChromaSurge() {
        isSurgeActive = true;
        surgeStartTime = System.currentTimeMillis();

        if (surgeGlowAnimator != null) surgeGlowAnimator.cancel();
        surgeGlowAnimator = ValueAnimator.ofFloat(0.2f, 0.7f);
        surgeGlowAnimator.setDuration(800);
        surgeGlowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        surgeGlowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        surgeGlowAnimator.addUpdateListener(anim -> {
            surgeGlowAlpha = (float) anim.getAnimatedValue();
            invalidate();
        });
        surgeGlowAnimator.start();

        Toast.makeText(getContext(),
                "Chroma Surge Active! Tap any color for 15s!",
                Toast.LENGTH_SHORT).show();
    }

    private void triggerRadarScanner() {
        radarTriggerTime = System.currentTimeMillis();
        radarHighlights.clear();

        int targetColor = currentSelectedColor;
        if (targetColor == COLOR_BLACK) return;

        // OPTIMIZATION: Use colorToAreasMap
        List<ColorArea> areas = colorToAreasMap != null
                ? colorToAreasMap.get(targetColor) : null;

        if (areas == null) return;

        List<Point> available = new ArrayList<>();
        for (ColorArea area : areas) {
            int idx = (area.getY() * imageWidth) + area.getX();
            if (idx >= 0 && idx < currentColorPixels.length
                    && currentColorPixels[idx] != targetColor) {
                available.add(new Point(area.getX(), area.getY()));
            }
        }

        Collections.shuffle(available);
        for (int i = 0; i < Math.min(5, available.size()); i++) {
            radarHighlights.add(available.get(i));
        }

        Toast.makeText(getContext(),
                "Radar: " + radarHighlights.size() + " areas found!",
                Toast.LENGTH_SHORT).show();
        invalidate();
    }

    // ─── Progress Updates ───

    private void updateCompletedAreasForColor(int targetColor) {
        ColorItem colorItem = getColorItem(targetColor);
        if (colorItem == null) return;

        int completedCount = countCompletedAreasForColor(targetColor);
        colorItem.setCompletedAreas(completedCount);

        if (colorProgressListener != null) {
            colorProgressListener.onColorProgress(
                    targetColor, colorItem.getProgress());
        }

        if (isColorCompleted(targetColor)) {
            colorItem.setFinished(true);
            if (colorFinishedListener != null) {
                colorFinishedListener.onColorFinished(targetColor);
            }
            if (isGameFinished()) handleGameCompletion();
        }
    }

    private void updateCompletedAreasForAllColors() {
        for (ColorItem ci : colorItems) {
            int color = ci.getColor();
            int completedCount = countCompletedAreasForColor(color);
            ci.setCompletedAreas(completedCount);

            if (colorProgressListener != null) {
                colorProgressListener.onColorProgress(color, ci.getProgress());
            }

            if (isColorCompleted(color) && !ci.isFinished()) {
                ci.setFinished(true);
                startRippleAnimation(
                        getWidth() / 2f, getHeight() / 2f, color, 0.8f);
                if (colorFinishedListener != null) {
                    colorFinishedListener.onColorFinished(color);
                }
            }
        }

        if (isGameFinished()) handleGameCompletion();
    }

    // ─── Game Completion ───

    private void handleGameCompletion() {
        if (timeLapseRecorder != null && timeLapseRecorder.isRecording()) {
            timeLapseRecorder.stopRecording();
            timeLapseRecorder.save(getContext());
        }
        if (gameFinishedListener != null) {
            gameFinishedListener.onGameFinished();
        }
    }

    // ─── Animation Drawing ───

    private void startSparkAnimation(int imageX, int imageY, int color) {
        if (!GameSaver.isSparkAnimEnabled(getContext())) return;

        sparkScreenX = (imageX * currentScale) + imageOffset.width;
        sparkScreenY = (imageY * currentScale) + imageOffset.height;
        sparkColor = color;

        float baseAngle = (float) (Math.random() * 360);
        for (int i = 0; i < SPARK_COUNT; i++) {
            sparkAngles[i] = baseAngle + (360f / SPARK_COUNT) * i
                    + (float) (Math.random() * 20 - 10);
        }

        if (sparkAnimator != null && sparkAnimator.isRunning()) sparkAnimator.cancel();

        sparkAnimator = ValueAnimator.ofFloat(0f, 1f);
        sparkAnimator.setDuration(280);
        sparkAnimator.setInterpolator(
                new android.view.animation.DecelerateInterpolator(1.5f));
        sparkAnimator.addUpdateListener(anim -> {
            sparkProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        sparkAnimator.addListener(new SimpleAnimatorListener() {
            @Override
            public void onEnd() {
                sparkProgress = -1f;
            }
        });
        sparkAnimator.start();
    }

    private void drawSparkAnimation(Canvas canvas) {
        float density = getContext().getResources().getDisplayMetrics().density;
        float maxDist = SPARK_RADIUS_DP * density;
        int alpha = (int) (255 * (1f - sparkProgress));

        sparkPaint.setStyle(Paint.Style.FILL);

        // Center dot
        float centerSize = 3f * density * (1f - sparkProgress);
        sparkPaint.setColor(Color.WHITE);
        sparkPaint.setAlpha(alpha);
        canvas.drawCircle(sparkScreenX, sparkScreenY, centerSize, sparkPaint);

        // Particles
        float particleSize = 1.8f * density * (1f - sparkProgress * 0.6f);
        for (int i = 0; i < SPARK_COUNT; i++) {
            float angle = (float) Math.toRadians(sparkAngles[i]);
            float dist = maxDist * sparkProgress;

            float px = sparkScreenX + (float) Math.cos(angle) * dist;
            float py = sparkScreenY + (float) Math.sin(angle) * dist;

            sparkPaint.setColor(i % 2 == 0 ? sparkColor : Color.WHITE);
            sparkPaint.setAlpha(alpha);
            canvas.drawCircle(px, py, particleSize, sparkPaint);
        }
    }

    private void startRippleAnimation(float x, float y, int color,
                                      float radiusMultiplier) {
        rippleCenterX = x;
        rippleCenterY = y;
        rippleColor = color;
        rippleMaxRadius = Math.max(getWidth(), getHeight()) * radiusMultiplier;

        if (rippleAnimator != null && rippleAnimator.isRunning())
            rippleAnimator.cancel();

        rippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        rippleAnimator.setDuration(1200);
        rippleAnimator.setInterpolator(
                new android.view.animation.DecelerateInterpolator());
        rippleAnimator.addUpdateListener(anim -> {
            rippleProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        rippleAnimator.addListener(new SimpleAnimatorListener() {
            @Override
            public void onEnd() {
                rippleProgress = -1f;
            }
        });
        rippleAnimator.start();
    }

    private void drawRippleEffect(Canvas canvas) {
        float currentRadius = rippleMaxRadius * rippleProgress;

        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeWidth(20f * (1f - rippleProgress));
        ripplePaint.setColor(rippleColor);
        ripplePaint.setAlpha((int) (150 * (1f - rippleProgress)));

        canvas.drawCircle(rippleCenterX, rippleCenterY, currentRadius, ripplePaint);
    }

    private void drawSurgeGlow(Canvas canvas) {
        if (System.currentTimeMillis() - surgeStartTime > SURGE_DURATION) {
            isSurgeActive = false;
            if (surgeGlowAnimator != null) surgeGlowAnimator.cancel();
            notifyToolDeactivated();
            invalidate();
            return;
        }

        surgeGlowPaint.setStyle(Paint.Style.STROKE);
        surgeGlowPaint.setStrokeWidth(40f);
        surgeGlowPaint.setColor(0xFFE91E63);
        surgeGlowPaint.setAlpha((int) (255 * surgeGlowAlpha));

        canvas.drawRect(20, 20, getWidth() - 20, getHeight() - 20, surgeGlowPaint);
    }

    private void drawAdvancedToolsOverlays(Canvas canvas) {
        if (isLoupeVisible && activeTool == GameTool.LOUPE) {
            drawMagnifier(canvas);
        }

        if (System.currentTimeMillis() - radarTriggerTime < RADAR_DURATION) {
            drawRadarHighlights(canvas);
        }
    }

    // ─── OPTIMIZATION: Particle Drawing with Pool ───

    private void drawParticles(Canvas canvas) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle particle = it.next();
            if (!particle.update()) {
                it.remove();
                recycleParticle(particle);
                continue;
            }

            particlePaint.setColor(particle.color);
            particlePaint.setAlpha((int) (particle.alpha * 255));

            canvas.save();
            canvas.translate(particle.x, particle.y);
            canvas.rotate(particle.rotation);

            if (particle.type == 0) {
                drawStar(canvas, particlePaint, particle.size / 2);
            } else {
                drawPetal(canvas, particlePaint, particle.size / 2);
            }

            canvas.restore();
        }

        if (!particles.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }

    private void drawStar(Canvas canvas, Paint paint, float radius) {
        reusablePath.reset();
        float innerRadius = radius / 2.5f;
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(i * 72 - 90);
            reusablePath.lineTo(
                    (float) Math.cos(angle) * radius,
                    (float) Math.sin(angle) * radius);
            angle = Math.toRadians(i * 72 - 90 + 36);
            reusablePath.lineTo(
                    (float) Math.cos(angle) * innerRadius,
                    (float) Math.sin(angle) * innerRadius);
        }
        reusablePath.close();
        canvas.drawPath(reusablePath, paint);
    }

    private void drawPetal(Canvas canvas, Paint paint, float radius) {
        reusableRectF.set(-radius / 2, -radius, radius / 2, radius);
        canvas.drawOval(reusableRectF, paint);
    }

    private void spawnParticles(float x, float y, int color) {
        int style = GameSaver.getParticleStyle(getContext());
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            int type;
            if (style == 0) type = 0;
            else if (style == 1) type = 1;
            else type = random.nextInt(2);

            Particle p = obtainParticle();
            p.init(x, y, color, type, random);
            particles.add(p);
        }

        if (particles.size() > MAX_PARTICLES) {
            List<Particle> removed = new ArrayList<>(
                    particles.subList(0, particles.size() - MAX_PARTICLES));
            particles.subList(0, particles.size() - MAX_PARTICLES).clear();
            for (Particle r : removed) recycleParticle(r);
        }
    }

    // ─── Magnifier Drawing ───

    private void drawMagnifier(Canvas canvas) {
        if (resultBitmap == null) return;

        float x = loupePosition.x;
        float y = loupePosition.y;

        reusablePath.reset();
        reusablePath.addCircle(x, y, LOUPE_RADIUS, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(reusablePath);

        // Reused, not allocated: this runs on every frame the loupe is on screen.
        loupeMatrix.reset();
        loupeMatrix.postTranslate(-x, -y);
        loupeMatrix.postScale(LOUPE_ZOOM, LOUPE_ZOOM);
        loupeMatrix.postTranslate(x, y);

        canvas.concat(loupeMatrix);
        drawScaledImage(canvas);
        drawNumberLabelsOptimized(canvas);
        canvas.restore();

        // Frame
        Paint p = particlePaint; // Reuse
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF424242);

        float handleLength = LOUPE_RADIUS * 1.5f;
        float handleWidth = 24f;
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(45);
        reusableRectF.set(LOUPE_RADIUS - 5, -handleWidth / 2,
                LOUPE_RADIUS + handleLength, handleWidth / 2);
        canvas.drawRoundRect(reusableRectF, 12, 12, p);

        p.setColor(0xFF9E9E9E);
        canvas.drawRect(LOUPE_RADIUS - 5, -handleWidth / 2,
                LOUPE_RADIUS + 15, handleWidth / 2, p);
        canvas.restore();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(12);
        p.setColor(0xFFCCCCCC);
        canvas.drawCircle(x, y, LOUPE_RADIUS + 2, p);

        p.setStrokeWidth(3);
        p.setColor(0xFF333333);
        canvas.drawCircle(x, y, LOUPE_RADIUS, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setAlpha(100);
        reusableRectF.set(x - LOUPE_RADIUS + 20, y - LOUPE_RADIUS + 10,
                x + LOUPE_RADIUS - 20, y + LOUPE_RADIUS - 100);
        canvas.drawArc(reusableRectF, 210, 120, false, p);
    }

    private void drawRadarHighlights(Canvas canvas) {
        Paint p = particlePaint; // Reuse
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setColor(Color.CYAN);

        float timeP = (float) (System.currentTimeMillis() % 1000) / 1000f;
        float radius = 10f + 20f * timeP;
        p.setAlpha((int) (255 * (1f - timeP)));

        for (Point pt : radarHighlights) {
            float sx = (pt.x * currentScale) + imageOffset.width;
            float sy = (pt.y * currentScale) + imageOffset.height;
            canvas.drawCircle(sx, sy, radius * currentScale, p);
        }

        if (System.currentTimeMillis() - radarTriggerTime < RADAR_DURATION) {
            postInvalidateOnAnimation();
        }
    }

    // ─── Vibration ───

    public void performVibration() {
        performVibration(100);
    }

    public void performVibration(long duration) {
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(
                            duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
            } catch (Exception e) {
                Log.e(TAG, "Vibration error", e);
            }
        }
    }

    public void performPatternVibration(long[] pattern, int[] amplitudes, int repeat) {
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                            pattern, amplitudes, repeat));
                } else {
                    vibrator.vibrate(pattern, repeat);
                }
            } catch (Exception e) {
                Log.e(TAG, "Pattern vibration error", e);
            }
        }
    }

    private void hapticForTool(GameTool tool) {
        switch (tool) {
            case BRUSH:
                performVibration(30);
                break;
            case BOMB:
                performPatternVibration(
                        new long[]{0, 50, 100, 150},
                        new int[]{0, 255, 0, 255}, -1);
                break;
            case BUCKET:
            case MAGIC_WAND:
                performPatternVibration(
                        new long[]{0, 80, 50, 80},
                        new int[]{0, 200, 0, 200}, -1);
                break;
            default:
                performVibration(60);
                break;
        }
    }

    // ─── Long Click ───

    private void cancelLongClick() {
        isPotentialLongClick = false;
        pendingLongClickArea = null;
        if (longClickRunnable != null) {
            longClickHandler.removeCallbacks(longClickRunnable);
            longClickRunnable = null;
        }
        longClickHandler.removeCallbacksAndMessages(null);
    }

    private void triggerLongClick() {
        if (pendingLongClickArea == null) return;

        final int colorToSelect = pendingLongClickArea.getColor();
        isPotentialLongClick = false;
        pendingLongClickArea = null;

        performVibration();
        selectColor(colorToSelect);

        post(() -> {
            try {
                if (getContext() instanceof GameActivity) {
                    ((GameActivity) getContext()).onLongClickColorSelect(colorToSelect);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error notifying activity", e);
            }
        });

        try {
            SoundUtils.playSound(
                    GlobalApplication.getAppContext(), SoundUtils.GameSound.TAP);
        } catch (Exception ignored) {}
    }

    private float getDragThreshold() {
        return 15f * getContext().getResources().getDisplayMetrics().density;
    }

    private float getLongClickCancelThreshold() {
        return 45f * getContext().getResources().getDisplayMetrics().density;
    }

    // ─── Initialization Helpers ───

    private void initializeImageScaleAndPosition() {
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();

        screenCenterX = dm.widthPixels / 2;
        screenCenterY = dm.heightPixels / 2;

        Size screenSize = new Size(dm.widthPixels, dm.heightPixels);
        Size imageSize = new Size(imageWidth, imageHeight);
        Size targetSize = new Size(
                dm.widthPixels * 0.9f, dm.heightPixels * 0.9f);

        initialScale = PolygonUtils.getScale(imageSize, targetSize);
        currentScale = initialScale;
        maxScale = initialScale * MAX_SCALE_FACTOR;

        Size scaledSize = new Size(
                imageWidth * currentScale, imageHeight * currentScale);
        PolygonUtils.getOffset(scaledSize, screenSize, imageOffset);
    }

    /**
     * A gentle "modern" pass over a colour buffer: lift saturation and brightness a touch so dull,
     * dated palette colours read fresh and vibrant. Near-neutral pixels (white paper, black lines,
     * greys) are left untouched, and identical colours map identically, so the region/number count is
     * unchanged. Runs on the level-load background thread.
     */
    private void applyModernColorBoost(int[] pixels) {
        float[] hsv = new float[3];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int alpha = c >>> 24;
            if (alpha == 0) continue;                       // transparent
            Color.colorToHSV(c, hsv);
            if (hsv[1] < 0.05f) continue;                   // greys / white paper / black lines
            hsv[1] = Math.min(1f, hsv[1] * 1.32f + 0.05f);  // richer, fresher
            hsv[2] = Math.min(1f, hsv[2] * 1.03f);          // a hair brighter
            pixels[i] = (alpha << 24) | (Color.HSVToColor(hsv) & 0x00FFFFFF);
        }
    }

    private void processPixelsAndCreateColorAreas() {
        Set<Integer> uniqueColors = new HashSet<>();
        Map<Integer, List<ColorArea>> colorAreasMap = new HashMap<>();

        for (int i = 0; i < currentColorPixels.length; i++) {
            int alpha = Color.alpha(originalColorPixels[i]);

            if (alpha > 0 && alpha < 255) {
                int resolvedColor = resolvePixelColor(i, alpha);

                if (resolvedColor != COLOR_BLACK) {
                    uniqueColors.add(resolvedColor);

                    int x = i % imageWidth;
                    int y = i / imageWidth;
                    int adjustedTextSize = isLowResolutionMode ? alpha / 2 : alpha;
                    ColorArea area = new ColorArea(resolvedColor, x, y, adjustedTextSize);

                    colorAreas.add(area);

                    colorAreasMap.computeIfAbsent(resolvedColor,
                            k -> new ArrayList<>()).add(area);
                }
            }

            if (currentColorPixels[i] != COLOR_BLACK) {
                currentColorPixels[i] = COLOR_TRANSPARENT;
            }
        }

        colorItems = new ArrayList<>();
        int colorNumber = 0;
        for (Integer color : uniqueColors) {
            ColorItem item = new ColorItem(color, colorNumber++);
            List<ColorArea> areas = colorAreasMap.get(color);
            int totalAreas = areas != null ? areas.size() : 0;
            item.setTotalAreas(totalAreas);
            colorItems.add(item);
        }
    }

    private int resolvePixelColor(int pixelIndex, int alpha) {
        int resolvedColor = COLOR_BLACK;

        if (pixelIndex > 0) {
            int leftColor = originalColorPixels[pixelIndex - 1];
            if (leftColor != COLOR_BLACK) {
                resolvedColor = leftColor;
                originalColorPixels[pixelIndex] = leftColor;
            }
        }
        if (pixelIndex < currentColorPixels.length - 1) {
            int rightColor = originalColorPixels[pixelIndex + 1];
            if (rightColor != COLOR_BLACK) {
                resolvedColor = rightColor;
                originalColorPixels[pixelIndex] = rightColor;
            }
        }
        if (pixelIndex >= imageWidth) {
            int topColor = originalColorPixels[pixelIndex - imageWidth];
            if (topColor != COLOR_BLACK) {
                resolvedColor = topColor;
                originalColorPixels[pixelIndex] = topColor;
            }
        }
        if (pixelIndex < currentColorPixels.length - imageWidth) {
            int bottomColor = originalColorPixels[pixelIndex + imageWidth];
            if (bottomColor != COLOR_BLACK) {
                resolvedColor = bottomColor;
                originalColorPixels[pixelIndex] = bottomColor;
            }
        }

        return resolvedColor;
    }

    private boolean fillSinglePixel(int x, int y, int targetColor) {
        if (x < 0 || x >= imageWidth || y < 0 || y >= imageHeight) return false;

        int pixelIndex = (y * imageWidth) + x;
        if (currentColorPixels[pixelIndex] == targetColor
                || originalColorPixels[pixelIndex] != targetColor) return false;

        currentColorPixels[pixelIndex] = targetColor;

        if (timeLapseRecorder != null && timeLapseRecorder.isRecording()) {
            timeLapseRecorder.recordStep(x, y, targetColor);
        }
        return true;
    }

    private void calculateAdTriggerPoints() {
        float progressPerAd = (float) colorAreas.size() / ADS_PER_LEVEL;
        for (int i = 1; i < ADS_PER_LEVEL; i++) {
            adTriggerProgressPoints.add(Math.round(i * progressPerAd));
        }
        adTriggerProgressPoints.add(colorAreas.size());
    }

    private void restoreSavedProgress() {
        List<Step> savedSteps = DataManager.getInstance().getSteps(currentLevel);

        for (Step step : savedSteps) {
            int x = step.getX();
            int y = step.getY();

            if (isLowResolutionMode) {
                x /= 2;
                y /= 2;
            }

            int pixelIndex = (y * imageWidth) + x;
            if (pixelIndex >= 0 && pixelIndex < currentColorPixels.length
                    && currentColorPixels[pixelIndex] == COLOR_TRANSPARENT) {
                floodFillOptimized(x, y, originalColorPixels[pixelIndex]);
                completedAreasCount++;
            }
        }

        // Update progress for all colors
        for (ColorItem colorItem : colorItems) {
            int color = colorItem.getColor();
            int completedCount = countCompletedAreasForColor(color);
            colorItem.setCompletedAreas(completedCount);

            if (isColorCompleted(color)) {
                colorItem.setFinished(true);
            }
        }
    }

    private int getColorNumber(int colorValue) {
        if (colorItems == null) return 0;
        for (int i = 0; i < colorItems.size(); i++) {
            if (colorValue == colorItems.get(i).getColor()) {
                return colorItems.get(i).getNumber();
            }
        }
        return 0;
    }

    private void animateHintToArea(ColorArea targetArea) {
        final float startOffsetX = imageOffset.width;
        final float startOffsetY = imageOffset.height;
        final float startScale = currentScale;

        final float targetOffsetX =
                getWidth() / 2f - targetArea.getX() * currentScale;
        final float targetOffsetY =
                getHeight() / 2f - targetArea.getY() * currentScale;

        float targetScale = minTextSize
                / (targetArea.getTextSize() * HINT_TEXT_SCALE_FACTOR);
        targetScale = Math.max(initialScale, Math.min(maxScale, targetScale));

        final float scaleMultiplier = targetScale / startScale;

        float dx = targetOffsetX - startOffsetX;
        float dy = targetOffsetY - startOffsetY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        long duration = Math.min(MAX_HINT_ANIMATION_TIME_MS,
                (long) (distance / HINT_ANIMATION_DURATION_MS * 1000));
        if (scaleMultiplier > 1.05f) duration = MAX_HINT_ANIMATION_TIME_MS;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            imageOffset.width = startOffsetX
                    + (targetOffsetX - startOffsetX) * progress;
            imageOffset.height = startOffsetY
                    + (targetOffsetY - startOffsetY) * progress;

            float currentScaleProgress = 1f + (scaleMultiplier - 1f) * progress;
            currentScale = startScale * currentScaleProgress;

            imageOffset.width = getWidth() / 2f
                    - ((getWidth() / 2f - imageOffset.width) * currentScaleProgress);
            imageOffset.height = getHeight() / 2f
                    - ((getHeight() / 2f - imageOffset.height) * currentScaleProgress);

            invalidate();
        });

        animator.addListener(new SimpleAnimatorListener() {
            @Override
            public void onStart() {
                isShowingHint = true;
            }

            @Override
            public void onEnd() {
                isShowingHint = false;
            }
        });

        animator.start();
    }

    private void handleMemoryError() {
        if (getContext() instanceof GameActivity) {
            ((GameActivity) getContext()).runOnUiThread(() ->
                    Toast.makeText(getContext(),
                            "Out of memory. Try closing other apps.",
                            Toast.LENGTH_LONG).show());
        }
    }

    // ─── Cleanup ───

    public void cleanup() {
        Log.d(TAG, "Cleaning up resources");

        // Stop a level init that is still running in the background from
        // publishing its result onto a torn-down view.
        levelInitCancelled = true;
        levelReady = false;
        highlightApplied = false;

        cancelLongClick();
        longClickHandler.removeCallbacksAndMessages(null);

        if (sparkAnimator != null) sparkAnimator.cancel();
        if (rippleAnimator != null) rippleAnimator.cancel();
        if (surgeGlowAnimator != null) surgeGlowAnimator.cancel();

        if (resultBitmap != null && !resultBitmap.isRecycled()) {
            resultBitmap.recycle();
        }
        resultBitmap = null;

        if (blackLineBitmap != null && !blackLineBitmap.isRecycled()) {
            blackLineBitmap.recycle();
        }
        blackLineBitmap = null;

        originalColorPixels = null;
        currentColorPixels = null;

        particles.clear();
        particlePool.clear();
        fillQueue.clear();
        radarHighlights.clear();
        numberStringCache.clear();

        if (colorToAreasMap != null) colorToAreasMap.clear();

        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelLongClick();
        longClickHandler.removeCallbacksAndMessages(null);
        if (sparkAnimator != null) {
            sparkAnimator.cancel();
            sparkAnimator = null;
        }
    }

    // ─── Simple Animator Listener (Reduces boilerplate) ───

    private static abstract class SimpleAnimatorListener
            implements Animator.AnimatorListener {
        public void onStart() {}
        public void onEnd() {}

        @Override
        public void onAnimationStart(@NonNull Animator a) { onStart(); }
        @Override
        public void onAnimationEnd(@NonNull Animator a) { onEnd(); }
        @Override
        public void onAnimationCancel(@NonNull Animator a) { onEnd(); }
        @Override
        public void onAnimationRepeat(@NonNull Animator a) {}
    }
}