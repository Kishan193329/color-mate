package com.colourmate.paintbynumber.model.reveal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The photo-reveal board: pick a number, its regions light up, tap them and each morphs from the
 * flat colour into the original photograph.
 *
 * <p>Gestures use the platform detectors so they behave like the By Number game:
 * <ul>
 *   <li>single tap → reveal the nearest region of the selected colour (very forgiving, no zoom needed)</li>
 *   <li>drag (one finger, when zoomed) → pan</li>
 *   <li>pinch (two fingers) → zoom in / out</li>
 *   <li>double-tap or long-press → auto-select the colour of the region under the finger</li>
 * </ul>
 */
public class RevealView extends View {

    public interface Listener {
        void onRegionRevealed(int number);

        /** Double-tap / long-press picked a region's colour — update the palette selection. */
        void onNumberPicked(int number);

        void onFinished();

        /** Every fill, with the region — multiplayer sends co-op fills on from here. */
        default void onRegionFilled(RevealLevel.Region region, boolean byFriend) { }
    }

    /** False while a race is counting down: the board still pans and zooms, nothing fills. */
    private boolean fillEnabled = true;

    public void setFillEnabled(boolean enabled) {
        fillEnabled = enabled;
    }

    /**
     * Fills many regions at once with no animation — what a co-op friend joining late receives:
     * everything the others filled before they arrived.
     */
    public void revealSilently(int[] regionIds) {
        if (level == null || regionIds == null || regionIds.length == 0) return;
        boolean any = false;
        for (int id : regionIds) {
            RevealLevel.Region region = level.regionsById.get(id);
            if (region == null || region.revealed) continue;
            level.markRevealed(region);
            if (level.vector == null) level.paintRegion(region, 1f);
            any = true;
            if (listener != null) listener.onRegionRevealed(region.number);
        }
        if (!any) return;
        if (level.vector == null) {
            highlightDirty = true;
            pushPixels();
        } else {
            requestArt();
        }
        invalidate();
        if (listener != null && level.isFinished()) listener.onFinished();
    }

    /**
     * A co-op friend filled a region: fill it here too, with the same spreading animation and a
     * small burst in that friend's colour so it reads as theirs.
     */
    public void revealByFriend(int regionId, int friendColour) {
        if (level == null) return;
        RevealLevel.Region region = level.regionsById.get(regionId);
        if (region == null || region.revealed) return;
        level.markRevealed(region);
        clearHighlightRegion(region);
        floodFill(region, region.cx, region.cy);
        for (int i = 0; i < 10; i++) {
            particles.add(new Particle(offsetX + region.cx * scale, offsetY + region.cy * scale,
                    friendColour, random.nextInt(2), random));
        }
        postInvalidateOnAnimation();
        if (listener != null) {
            listener.onRegionRevealed(region.number);
            listener.onRegionFilled(region, true);
            if (level.isFinished()) listener.onFinished();
        }
    }

    private static final long FILL_MS = 420;          // flood-fill duration (ease-out, 300–500ms)
    private static final float MAX_SCALE_FACTOR = 12f;
    /**
     * Zoom (as a multiple of fit) past which the vector line is drawn instead of the raster.
     * Below this the 1254px bitmap still has more detail than the screen can show, so there is
     * nothing to gain and a path with thousands of points to avoid.
     */
    private static final float VECTOR_BORDER_FROM = 1.3f;

    /** Snap radius while sweep-painting — far tighter than a tap, a stroke aims well. */
    private static final float SWEEP_TOLERANCE_DP = 10f;

    /** One-finger drag paints (it landed on the selected colour) instead of panning. */
    private boolean paintSweep;
    private static final float TAP_TOLERANCE_DP = 28f;  // how far a tap may miss and still fill

    /** By Number's neutral grey for "these are the ones to fill now" (COLOR_HIGHLIGHTED). */
    private static final int COLOR_HIGHLIGHTED = 0xFFCCCCCC;

    private RevealLevel level;
    private Listener listener;
    private int selectedNumber = 1;

    private Bitmap displayBitmap;
    private Bitmap highlightBitmap;
    private int[] highlightPixels;
    private boolean highlightDirty = true;

    private float scale = 1f, fitScale = 1f, offsetX, offsetY;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    /**
     * Filled, anti-aliased: the exporter traces the ink as shapes, not as stroked outlines.
     *
     * <p>It also carries a hairline stroke. The region map was built from the RAW line art, but
     * the vector is a smoothed trace of it, so the two disagree by a fraction of a pixel — and at
     * high zoom the flat fill underneath peeked out along the ink as a staircase. Stroking the
     * same path grows the ink by half the stroke width, which covers that seam. The width is set
     * per frame in path units, so it stays a constant thickness on screen at any zoom.
     */
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Height of whatever floats over the bottom of the board — the palette strip. */
    private float labelBottomInset = 0f;

    { borderPaint.setStyle(Paint.Style.FILL_AND_STROKE); }

    /** How much wider the drawn ink is than the traced outline, in screen pixels. */
    private static final float BORDER_SEAM_PX = 1.2f;
    private final RectF destRect = new RectF();

    private SmartLabelRenderer labelRenderer;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    // ── Colour particles that ride the flood-fill's leading edge (star + petal, like By Number) ──
    private static final int MAX_PARTICLES = 70;
    private final List<Particle> particles = new ArrayList<>();
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path particlePath = new Path();
    private final RectF particleRect = new RectF();
    private final Random random = new Random();

    public RevealView(Context context) {
        super(context);
        init();
    }

    public RevealView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        labelRenderer = new SmartLabelRenderer(new SmartLabelRenderer.Config(),
                getResources().getDisplayMetrics().density);

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureListener());
        gestureDetector.setIsLongpressEnabled(true);
    }

    // ─── Level ───

    /** Tell the board how much of its bottom edge is covered, so no number is placed there. */
    public void setLabelBottomInset(float px) {
        labelBottomInset = Math.max(0f, px);
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setLevel(RevealLevel level) {
        this.level = level;
        if (level.vector == null) {
            displayBitmap = Bitmap.createBitmap(level.width, level.height, Bitmap.Config.ARGB_8888);
            highlightBitmap = Bitmap.createBitmap(level.width, level.height, Bitmap.Config.ARGB_8888);
            highlightPixels = new int[level.width * level.height];
            highlightDirty = true;
            pushPixels();
        }
        fitToScreen();
        if (level.vector != null) {
            outlineColour = "soft".equals(level.look) ? 0xFFA3A3A3 : 0xFF000000;
            startPulse();
            requestArt();
        }
    }

    public void setSelectedNumber(int number) {
        this.selectedNumber = number;
        highlightDirty = true;
        invalidate();
    }

    /** Re-draw the picture from its revealed state — call after progress is restored. */
    public void refreshArt() {
        if (level != null && level.vector != null) requestArt();
        invalidate();
    }

    // ─── Vector engine (SVG pictures) ───
    //
    // An SVG picture used to play through the raster path: a 1254px id-map, border bitmap and
    // grey sketch, so its line went soft and blocky once zoomed and the selected number's grey
    // highlight vanished into the grey sketch. Here the picture is the SVG's own shapes:
    //  - outline ink always drawn, crisp at any zoom (the admin's black / soft look);
    //  - an unfilled shape is a light grey tone of its own colour, a filled one its true colour;
    //  - the selected number's unfilled shapes blink in a tint of that colour.
    // Thousands of shapes cannot be drawn every frame, so the picture is rendered into a
    // screen-sized bitmap on a worker thread whenever the camera settles, and while the player
    // pinches or pans that bitmap is simply moved with the fingers. A fill paints its own few
    // shapes straight into the bitmap, so tapping never waits for a full render.

    /** Seam cover between neighbouring shapes, in screen px — polygons meet, they do not overlap. */
    private static final float VEC_SEAM_PX = 0.9f;
    private static final long VEC_SETTLE_MS = 80;
    private static final int HIGHLIGHT_VIOLET = 0xFF7A5CFF;   // cm_violet

    private int outlineColour = 0xFF000000;
    private float lastFrameScale = -1f, lastFrameOX, lastFrameOY;
    private Bitmap artFront;
    private Canvas artFrontCanvas;
    private float artScale, artOX, artOY;
    private boolean artValid;
    private final android.graphics.Matrix artMatrix = new android.graphics.Matrix();
    private final Paint vecPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artBlit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private volatile boolean artRendering;
    private boolean artPending;
    /** Regions filled after a background render started — painted onto its result on arrival. */
    private final List<RevealLevel.Region> fillsDuringRender = new ArrayList<>();
    private ValueAnimator pulseAnim;
    private float pulse;
    private final Runnable artRunnable = this::renderArtAsync;

    { vecPaint.setStyle(Paint.Style.FILL_AND_STROKE); vecPaint.setStrokeJoin(Paint.Join.ROUND); }

    private boolean vectorMode() {
        return level != null && level.vector != null;
    }

    /** Light grey of a colour's own luminance — the unfilled look (same as the admin's sketch). */
    private static int tone(int c) {
        float l = 0.299f * android.graphics.Color.red(c) + 0.587f * android.graphics.Color.green(c)
                + 0.114f * android.graphics.Color.blue(c);
        int g = (int) (255 - (255 - l) * 0.5f);
        return 0xFF000000 | (g << 16) | (g << 8) | g;
    }

    private void startPulse() {
        if (pulseAnim != null) pulseAnim.cancel();
        pulseAnim = ValueAnimator.ofFloat(0f, 1f);
        pulseAnim.setDuration(1050);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnim.addUpdateListener(a -> {
            pulse = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnim != null) pulseAnim.cancel();
        removeCallbacks(artRunnable);
    }

    /** Debounced: a full render once the camera has stopped moving. */
    private void requestArt() {
        removeCallbacks(artRunnable);
        postDelayed(artRunnable, VEC_SETTLE_MS);
    }

    private void renderArtAsync() {
        if (!vectorMode() || getWidth() == 0 || getHeight() == 0) return;
        if (artRendering) {
            artPending = true;
            return;
        }
        artRendering = true;
        synchronized (fillsDuringRender) {
            fillsDuringRender.clear();
        }
        final int w = getWidth(), h = getHeight();
        final float s = scale, ox = offsetX, oy = offsetY;
        final RevealLevel lvl = level;
        new Thread(() -> {
            Bitmap bmp = null;
            try {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                drawArt(new Canvas(bmp), lvl, s, ox, oy, w, h);
            } catch (Throwable ignored) {
                // out of memory on a small phone: keep showing the previous frame
            }
            final Bitmap done = bmp;
            post(() -> {
                artRendering = false;
                if (done != null && lvl == level) {
                    Bitmap old = artFront;
                    artFront = done;
                    artFrontCanvas = new Canvas(done);
                    artScale = s;
                    artOX = ox;
                    artOY = oy;
                    artValid = true;
                    synchronized (fillsDuringRender) {
                        for (RevealLevel.Region r : fillsDuringRender) paintRegionInto(r, null, 0, 0, 0);
                        fillsDuringRender.clear();
                    }
                    if (old != null && old != done) old.recycle();
                }
                if (artPending) {
                    artPending = false;
                    renderArtAsync();
                }
                invalidate();
            });
        }, "vector-art").start();
    }

    /** The whole picture at one camera position. Worker thread: own Paint, read-only paths. */
    private void drawArt(Canvas c, RevealLevel lvl, float s, float ox, float oy, int w, int h) {
        VectorArt v = lvl.vector;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(VEC_SEAM_PX / s);
        c.drawColor(0xFFFFFFFF);
        c.save();
        c.translate(ox, oy);
        c.scale(s, s);
        float m = 2f / s;
        RectF vis = new RectF(-ox / s - m, -oy / s - m, (w - ox) / s + m, (h - oy) / s + m);
        drawShapes(c, p, lvl, vis, s);
        c.restore();
    }

    /**
     * Every shape touching {@code area}, in the SVG's own order. The order matters: these pictures
     * often lay a dark shape UNDER a lighter one (a moon's rim, the heart of a rose), so drawing
     * all colours first and all ink after put black discs on top of the picture.
     */
    private void drawShapes(Canvas c, Paint p, RevealLevel lvl, RectF area, float s) {
        VectorArt v = lvl.vector;
        float seam = VEC_SEAM_PX / s, inkSeam = 0.4f / s;
        for (int i = 0; i < v.paths.length; i++) {
            if (!RectF.intersects(area, v.bounds[i])) continue;
            int rid = v.regionOf[i];
            if (rid == 0) {
                p.setColor(outlineColour);
                p.setStrokeWidth(inkSeam);
            } else {
                RevealLevel.Region r = lvl.regionsById.get(rid);
                p.setColor(r != null && r.revealed ? v.colour[i] : tone(v.colour[i]));
                p.setStrokeWidth(seam);
            }
            c.drawPath(v.paths[i], p);
        }
    }

    /**
     * Re-draws the region's box of the picture bitmap from the current state (the region is
     * already marked revealed), in the SVG's own order so whatever lies on top stays on top;
     * with {@code radius} > 0 only within that distance of (ox, oy) — the flood fill's edge.
     */
    private void paintRegionInto(RevealLevel.Region region, @Nullable Path clip, float ox, float oy,
                                 float radius) {
        if (!artValid || artFrontCanvas == null || region == null) return;
        Canvas c = artFrontCanvas;
        c.save();
        c.translate(artOX, artOY);
        c.scale(artScale, artScale);
        RectF box = new RectF(region.left - 2, region.top - 2, region.right + 3, region.bottom + 3);
        c.clipRect(box);
        if (radius > 0) {
            if (clip == null) clip = new Path();
            clip.reset();
            clip.addCircle(ox, oy, radius, Path.Direction.CW);
            c.clipPath(clip);
        }
        drawShapes(c, vecPaint, level, box, artScale);
        c.restore();
    }

    private final Path fillClip = new Path();

    /** Vector flood fill: the region's true colours spread out from the touch point. */
    private void vectorFill(RevealLevel.Region region, int originX, int originY) {
        synchronized (fillsDuringRender) {
            if (artRendering) fillsDuringRender.add(region);
        }
        float maxDist = 1f;
        for (int cx = region.left; cx <= region.right; cx += Math.max(1, region.right - region.left)) {
            for (int cy = region.top; cy <= region.bottom; cy += Math.max(1, region.bottom - region.top)) {
                maxDist = Math.max(maxDist, (float) Math.hypot(cx - originX, cy - originY));
            }
        }
        final float md = maxDist + 2f;
        final int colour = (level.palette != null && region.number < level.palette.length)
                ? level.palette[region.number] : 0xFF9AA0A6;
        level.paintRegion(region, 1f);                  // keeps the saved artwork in step
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(FILL_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            float radius = (float) a.getAnimatedValue() * md;
            paintRegionInto(region, fillClip, originX, originY, radius);
            emitEdgeParticles(region, originX, originY, radius, colour);
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                paintRegionInto(region, null, 0, 0, 0);
                invalidate();
            }
        });
        animator.start();
    }

    /** Per frame: the picture bitmap moved with the camera, then the blinking parts to fill. */
    private void drawVector(Canvas canvas) {
        canvas.drawColor(0xFFFFFFFF);
        if (artValid && artFront != null) {
            float k = scale / artScale;
            artMatrix.setScale(k, k);
            artMatrix.postTranslate(offsetX - artOX * k, offsetY - artOY * k);
            canvas.drawBitmap(artFront, artMatrix, artBlit);
        }
        // The blink redraws every frame, so only a camera that actually MOVED since the last
        // frame may restart the settle timer — otherwise the timer would never get to fire.
        if (scale != lastFrameScale || offsetX != lastFrameOX || offsetY != lastFrameOY) {
            lastFrameScale = scale;
            lastFrameOX = offsetX;
            lastFrameOY = offsetY;
            if (!artValid || scale != artScale || offsetX != artOX || offsetY != artOY) requestArt();
        }

        // The parts to fill now blink in the app's violet. A tint of the colour itself was tried
        // first and measured invisible: a dark colour mixed toward white lands on the same grey as
        // the sketch around it (0 coloured pixels on screen for a dark-green number).
        VectorArt v = level.vector;
        vecPaint.setStyle(Paint.Style.FILL);
        vecPaint.setColor(HIGHLIGHT_VIOLET);
        vecPaint.setAlpha((int) (70 + 120 * pulse));
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        for (RevealLevel.Region r : level.regions) {
            if (r.revealed || r.number != selectedNumber) continue;
            int[] shapes = v.shapesOfRegion.get(r.id);
            if (shapes == null) continue;
            for (int i : shapes) canvas.drawPath(v.paths[i], vecPaint);
        }
        canvas.restore();
        vecPaint.setAlpha(255);
        vecPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    public int getSelectedNumber() {
        return selectedNumber;
    }

    private void pushPixels() {
        if (level == null || displayBitmap == null) return;
        displayBitmap.setPixels(level.display, 0, level.width, 0, 0, level.width, level.height);
    }

    // ─── Transform ───

    /**
     * The view is resized whenever the banner ad below it loads, fails or collapses — which happens
     * seconds AFTER the picture is already on screen. Rerunning fitToScreen() there threw away
     * whatever the player had zoomed into, which is what "zoom doesn't work" felt like. Keep how
     * far they had zoomed (their scale as a multiple of fit) and only recompute what fit means.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (level == null || w == 0 || h == 0) return;
        float zoomRatio = (fitScale > 0f && scale > 0f) ? scale / fitScale : 1f;
        recomputeFit();
        if (zoomRatio > 1.001f) {
            scale = fitScale * zoomRatio;
            clampTransform();
            invalidate();
        } else {
            fitToScreen();
        }
    }

    /** Recomputes what "fitted" means for the current view size, without moving the picture. */
    private void recomputeFit() {
        if (level == null || getWidth() == 0 || getHeight() == 0) return;
        fitScale = Math.min((float) getWidth() / level.width, (float) getHeight() / level.height);
    }

    // ─── Hint ───

    private RevealLevel.Region hintRegion;
    private float hintPulse;                 // 0..1, drives the ring
    private ValueAnimator hintAnim, zoomAnim;

    /** How much of the shorter side the hinted region should occupy once the camera settles. */
    private static final float HINT_FILL = 0.45f;
    private static final long HINT_MS = 2600L;
    /** Never hint at less than this multiple of fit — see the note in showHint(). */
    private static final float HINT_MIN_ZOOM = 2.4f;

    /**
     * Show the player where to tap next: glide the camera onto {@code region} and pulse a ring
     * around it.
     *
     * <p>Pointing at a shape without moving the camera is useless once the picture is zoomed in —
     * the hint is usually off screen. So the two belong together: the ring says WHICH shape, the
     * movement makes sure it can be seen.
     */
    public void showHint(RevealLevel.Region region) {
        if (level == null || region == null) return;
        hintRegion = region;

        float rw = Math.max(1, region.right - region.left);
        float rh = Math.max(1, region.bottom - region.top);
        float want = Math.min(getWidth() * HINT_FILL / rw, getHeight() * HINT_FILL / rh);
        // Zoom IN at least this much. At fit scale clampTransform() re-centres the whole picture,
        // so the camera physically cannot put a region in the middle — the ring would be pointed
        // at something off to the side, which is what the first version did. A hint that does not
        // move is not a hint.
        want = Math.max(want, fitScale * HINT_MIN_ZOOM);
        final float toScale = Math.max(fitScale, Math.min(fitScale * MAX_SCALE_FACTOR, want));
        final float fromScale = scale, fromX = offsetX, fromY = offsetY;
        final float cx = (region.left + region.right) / 2f, cy = (region.top + region.bottom) / 2f;

        if (zoomAnim != null) zoomAnim.cancel();
        zoomAnim = ValueAnimator.ofFloat(0f, 1f);
        zoomAnim.setDuration(420);
        zoomAnim.setInterpolator(new DecelerateInterpolator());
        zoomAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            scale = fromScale + (toScale - fromScale) * f;
            // centre the region: solve offset so its centre lands in the middle of the view
            float targetX = getWidth() / 2f - cx * scale;
            float targetY = getHeight() / 2f - cy * scale;
            offsetX = fromX + (targetX - fromX) * f;
            offsetY = fromY + (targetY - fromY) * f;
            clampTransform();
            invalidate();
        });
        zoomAnim.start();

        if (hintAnim != null) hintAnim.cancel();
        hintAnim = ValueAnimator.ofFloat(0f, 1f);
        hintAnim.setDuration(HINT_MS);
        hintAnim.addUpdateListener(a -> { hintPulse = (float) a.getAnimatedValue(); invalidate(); });
        hintAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator anim) {
                hintRegion = null;
                invalidate();
            }
        });
        hintAnim.start();
    }

    /** Draws the pulsing ring on the hinted shape. Three beats over the hint's lifetime. */
    private void drawHint(Canvas canvas) {
        if (hintRegion == null) return;
        float beat = (float) Math.abs(Math.sin(hintPulse * Math.PI * 3));
        float cx = offsetX + hintRegion.cx * scale;
        float cy = offsetY + hintRegion.cy * scale;
        float base = Math.max(hintRegion.radius * scale, dp(14));
        hintPaint.setStyle(Paint.Style.STROKE);
        hintPaint.setStrokeWidth(dp(3));
        hintPaint.setColor(0xFF5B4BFF);
        hintPaint.setAlpha((int) (90 + 150 * beat));
        canvas.drawCircle(cx, cy, base * (1.1f + 0.45f * beat), hintPaint);
    }


    public void fitToScreen() {
        if (level == null || getWidth() == 0 || getHeight() == 0) return;
        recomputeFit();
        scale = fitScale;
        offsetX = (getWidth() - level.width * scale) / 2f;
        offsetY = (getHeight() - level.height * scale) / 2f;
        invalidate();
    }

    private void clampTransform() {
        scale = Math.max(fitScale, Math.min(fitScale * MAX_SCALE_FACTOR, scale));
        float w = level.width * scale, h = level.height * scale;
        offsetX = w <= getWidth() ? (getWidth() - w) / 2f
                : Math.max(getWidth() - w, Math.min(0, offsetX));
        offsetY = h <= getHeight() ? (getHeight() - h) / 2f
                : Math.max(getHeight() - h, Math.min(0, offsetY));
    }

    // ─── Draw ───

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (level == null) return;
        if (vectorMode()) {
            drawVector(canvas);
            labelRenderer.draw(canvas, level, selectedNumber, scale, fitScale, offsetX, offsetY,
                    getWidth(), getHeight(), labelBottomInset);
            drawHint(canvas);
            if (!particles.isEmpty()) drawParticles(canvas);
            return;
        }
        if (displayBitmap == null) return;

        destRect.set(offsetX, offsetY,
                offsetX + level.width * scale, offsetY + level.height * scale);

        // 1) white paper + the photograph where regions are already revealed.
        canvas.drawBitmap(displayBitmap, null, destRect, bitmapPaint);

        // 2) the selected colour's unfilled regions in solid GREY — the "part highlight", exactly
        //    like the By Number game. Drawn UNDER the line art so the borders still read.
        if (highlightDirty) rebuildHighlight();
        if (highlightBitmap != null) {
            canvas.drawBitmap(highlightBitmap, null, destRect, bitmapPaint);
        }

        // 3) the black line art. Vector once the player has zoomed past the point where the
        //    1254px raster starts showing its own pixels; the bitmap below that, because at fit
        //    the two are indistinguishable and blitting beats a 5,000-point path every frame.
        if (level.borderVector != null && scale > fitScale * VECTOR_BORDER_FROM) {
            canvas.save();
            canvas.translate(offsetX, offsetY);
            float k = level.width * scale / level.borderVector.width;
            canvas.scale(k, k);
            // k converts path units to screen pixels, so dividing keeps the seam cover a constant
            // width on screen however far the player has zoomed.
            borderPaint.setStrokeWidth(BORDER_SEAM_PX / k);
            canvas.drawPath(level.borderVector.path, borderPaint);
            canvas.restore();
        } else if (level.border != null) {
            canvas.drawBitmap(level.border, null, destRect, bitmapPaint);
        }

        // 4) region numbers — only once zoomed in; sparse, non-overlapping, inside the frame.
        labelRenderer.draw(canvas, level, selectedNumber, scale, fitScale, offsetX, offsetY,
                getWidth(), getHeight(), labelBottomInset);

        drawHint(canvas);

        // 5) the fill-burst particles, on top of everything.
        if (!particles.isEmpty()) drawParticles(canvas);
    }

    /** Paints the selected colour's unfilled regions the neutral grey COLOR_HIGHLIGHTED (opaque),
     *  everything else transparent. Rebuilt only when the selection changes. */
    private void rebuildHighlight() {
        highlightDirty = false;
        if (level == null || highlightBitmap == null) return;
        int[] px = highlightPixels;
        char[] idMap = level.idMap;
        for (int i = 0; i < px.length; i++) {
            int id = idMap[i];
            if (id == 0) {
                px[i] = 0;
                continue;
            }
            RevealLevel.Region region = level.regionsById.get(id);
            px[i] = (region != null && !region.revealed && region.number == selectedNumber)
                    ? COLOR_HIGHLIGHTED : 0;
        }
        highlightBitmap.setPixels(px, 0, level.width, 0, 0, level.width, level.height);
    }

    // ─── Touch (platform detectors) ───

    /**
     * True once a second finger has touched down in the current gesture. A pinch usually ends with
     * the fingers lifting a moment apart, and GestureDetector reports that last lift as a single
     * tap — which revealed a region the player never meant to touch. The flag survives until the
     * next fresh ACTION_DOWN.
     */
    private boolean multiTouchGesture = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (level == null) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                multiTouchGesture = false;
                // Sweep-fill vs pan is decided by where the finger LANDS: on an unrevealed
                // region of the selected colour (exact pixel, no snapping — the intent must
                                // be unambiguous) the drag paints; anywhere else it pans, so one-finger
                // panning keeps working everywhere that is not paintable.
                paintSweep = startsOnSelected(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiTouchGesture = true;
                paintSweep = false;                 // a pinch is never a paint stroke
                break;
            default:
                break;
        }
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector d) {
            // Clamp the scale FIRST, then move the offsets by the ratio actually applied.
            // Computing the offsets from the raw pinch value and clamping the scale afterwards
            // left the two disagreeing, so at either zoom limit the picture lurched under the
            // fingers on every frame instead of simply stopping.
            float target = scale * d.getScaleFactor();
            float clamped = Math.max(fitScale, Math.min(fitScale * MAX_SCALE_FACTOR, target));
            if (clamped == scale) return true;          // already at the limit — hold still
            float ratio = clamped / scale;
            float focusX = d.getFocusX(), focusY = d.getFocusY();
            offsetX = focusX - (focusX - offsetX) * ratio;
            offsetY = focusY - (focusY - offsetY) * ratio;
            scale = clamped;
            clampTransform();
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;   // claim the stream so taps/scrolls are delivered
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (multiTouchGesture) return true;      // the tail of a pinch, not a tap
            revealAt(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
            if (scaleDetector.isInProgress()) return false;  // pinch owns two-finger moves
            if (paintSweep && !multiTouchGesture) {
                // Drag-to-fill: every region of the selected colour the finger passes over
                // fills, so fifteen small spots are one sweep instead of fifteen taps —
                                // the single biggest feel difference against the reference apps.
                sweepAt(e2.getX(), e2.getY());
                return true;
            }
            offsetX -= dx;
            offsetY -= dy;
            clampTransform();
            invalidate();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            pickNumberAt(e.getX(), e.getY());
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            pickNumberAt(e.getX(), e.getY());
            return true;
        }
    }

    // ─── Reveal / pick ───

    private void revealAt(float viewX, float viewY) {
        int x = (int) ((viewX - offsetX) / scale);
        int y = (int) ((viewY - offsetY) / scale);

        if (!fillEnabled) return;
        RevealLevel.Region region = findTarget(x, y);
        if (region == null) return;

        level.markRevealed(region);
        clearHighlightRegion(region);

        // Flood fill from the touch point when it's inside the region, else from the region's
        // interior point (the tap may have been snapped from just outside by the tolerance search).
        int ox = x, oy = y;
        if (level.regionAt(x, y) != region) {
            ox = region.cx;
            oy = region.cy;
        }
        floodFill(region, ox, oy);
        fillFeedback(region);

        if (listener != null) {
            listener.onRegionRevealed(region.number);
            listener.onRegionFilled(region, false);
            if (level.isFinished()) listener.onFinished();
        }
    }

    /** True when the point lands exactly on an unrevealed region of the selected colour. */
    private boolean startsOnSelected(float viewX, float viewY) {
        if (level == null || !fillEnabled) return false;
        int x = (int) ((viewX - offsetX) / scale);
        int y = (int) ((viewY - offsetY) / scale);
        RevealLevel.Region r = level.regionAt(x, y);
        return r != null && !r.revealed && r.number == selectedNumber;
    }

    /** One step of a paint sweep: fill the selected-colour region under the finger, with a
     *  much tighter snap than a tap — a moving finger passes CLOSE to what it means. */
    private void sweepAt(float viewX, float viewY) {
        int x = (int) ((viewX - offsetX) / scale);
        int y = (int) ((viewY - offsetY) / scale);
        RevealLevel.Region region = level.regionAt(x, y);
        if (region == null || region.revealed || region.number != selectedNumber) {
            int tol = Math.max(3, (int) (dp(SWEEP_TOLERANCE_DP) / Math.max(scale, 0.0001f)));
            region = level.nearestUnfilled(x, y, tol, selectedNumber, true);
        }
        if (region == null || region.revealed || region.number != selectedNumber) return;
        level.markRevealed(region);
        clearHighlightRegion(region);
        int ox = level.regionAt(x, y) == region ? x : region.cx;
        int oy = level.regionAt(x, y) == region ? y : region.cy;
        floodFill(region, ox, oy);
        fillFeedback(region);
        if (listener != null) {
            listener.onRegionRevealed(region.number);
            listener.onRegionFilled(region, false);
            if (level.isFinished()) listener.onFinished();
        }
    }

    /**
     * What a fill FEELS like beyond the pixels: the By Number tap sound (SoundUtils checks
     * the Settings toggle itself), a small haptic tick (the system honours its own setting),
          * and — when this fill finished its whole colour — a burst over the region, because
     * completing a colour is a milestone the game used to pass in silence.
     */
    private void fillFeedback(RevealLevel.Region region) {
        com.colourmate.paintbynumber.util.SoundUtils.playSound(
                getContext(), com.colourmate.paintbynumber.util.SoundUtils.GameSound.TAP);
        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        if (level.revealedOf(region.number) >= level.totalOf(region.number)) {
            int colour = (level.palette != null && region.number < level.palette.length)
                    ? level.palette[region.number] : 0xFF9AA0A6;
            for (int i = 0; i < 14; i++) {
                float ix = region.cx + (random.nextFloat() - 0.5f) * (region.right - region.left);
                float iy = region.cy + (random.nextFloat() - 0.5f) * (region.bottom - region.top);
                particles.add(new Particle(offsetX + ix * scale, offsetY + iy * scale, colour,
                        random.nextInt(2), random));
            }
            postInvalidateOnAnimation();
        }
    }

    /** Double-tap / long-press: adopt the colour of the region under the finger. */
    private void pickNumberAt(float viewX, float viewY) {
        int x = (int) ((viewX - offsetX) / scale);
        int y = (int) ((viewY - offsetY) / scale);

        RevealLevel.Region region = level.regionAt(x, y);
        if (region == null || region.revealed) {
            region = level.nearestUnfilled(x, y, tolerance(), 0, false);
        }
        if (region == null) return;
        if (region.number != selectedNumber) {
            setSelectedNumber(region.number);
            if (listener != null) listener.onNumberPicked(region.number);
        }
    }

    /** The unrevealed region of the SELECTED colour the tap meant — exact pixel, else nearest one
     *  within a forgiving radius (so you never have to zoom in to hit a small spot). */
    private RevealLevel.Region findTarget(int x, int y) {
        return level.tapTarget(x, y, selectedNumber, tolerance());
    }

    private int tolerance() {
        return Math.max(6, (int) (dp(TAP_TOLERANCE_DP) / Math.max(scale, 0.0001f)));
    }

    private void clearHighlightRegion(RevealLevel.Region region) {
        if (highlightBitmap == null || highlightPixels == null) return;
        int w = level.width, h = level.height;
        int left = Math.max(0, region.left), top = Math.max(0, region.top);
        int right = Math.min(w - 1, region.right), bottom = Math.min(h - 1, region.bottom);
        if (right < left || bottom < top) return;
        for (int yy = top; yy <= bottom; yy++) {
            int row = yy * w;
            for (int xx = left; xx <= right; xx++) {
                if (level.idMap[row + xx] == region.id) highlightPixels[row + xx] = 0;
            }
        }
        highlightBitmap.setPixels(highlightPixels, top * w + left, w,
                left, top, right - left + 1, bottom - top + 1);
    }

    /**
     * Progressive flood fill: the colour spreads out from the touch point across the region with an
     * ease-out radius, tiny colour particles riding the leading edge. Clipped to the region, so the
     * outline is never crossed. Finishes in {@link #FILL_MS} ms.
     */
    private void floodFill(RevealLevel.Region region, int originX, int originY) {
        if (vectorMode()) {
            vectorFill(region, originX, originY);
            return;
        }
        float maxDist = 1f;                      // reach the farthest corner of the region by the end
        for (int cx = region.left; cx <= region.right; cx += Math.max(1, region.right - region.left)) {
            for (int cy = region.top; cy <= region.bottom; cy += Math.max(1, region.bottom - region.top)) {
                maxDist = Math.max(maxDist, (float) Math.hypot(cx - originX, cy - originY));
            }
        }
        final float md = maxDist;
        final int colour = (level.palette != null && region.number < level.palette.length)
                ? level.palette[region.number] : 0xFF9AA0A6;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(FILL_MS);
        animator.setInterpolator(new DecelerateInterpolator());   // ease-out
        animator.addUpdateListener(a -> {
            float radius = (float) a.getAnimatedValue() * md;
            level.paintRegionProgressive(region, originX, originY, radius);
            pushPixels();
            emitEdgeParticles(region, originX, originY, radius, colour);
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                level.paintRegion(region, 1f);    // guarantee the whole region is filled
                pushPixels();
                invalidate();
            }
        });
        animator.start();
    }

    /** A couple of colour particles on the spreading edge, so the paint feels alive as it flows. */
    private void emitEdgeParticles(RevealLevel.Region region, int ox, int oy, float radius, int colour) {
        if (radius < dp(3)) return;
        for (int i = 0; i < 2; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            float ix = ox + (float) Math.cos(angle) * radius;
            float iy = oy + (float) Math.sin(angle) * radius;
            int px = (int) ix, py = (int) iy;
            if (px < 0 || py < 0 || px >= level.width || py >= level.height) continue;
            if (level.idMap[py * level.width + px] != region.id) continue;   // stay inside the region
            particles.add(new Particle(offsetX + ix * scale, offsetY + iy * scale, colour,
                    random.nextInt(2), random));
        }
        while (particles.size() > MAX_PARTICLES) particles.remove(0);
    }

    /** A finish-line burst: the fill particles, but across the whole picture at once. */
    public void celebrate() {
        if (level == null || level.palette.length < 2) return;
        for (int i = 0; i < 46; i++) {
            float ix = random.nextFloat() * level.width;
            float iy = random.nextFloat() * level.height;
            int colour = level.palette[1 + random.nextInt(Math.max(1, level.palette.length - 1))];
            particles.add(new Particle(offsetX + ix * scale, offsetY + iy * scale, colour,
                    random.nextInt(2), random));
        }
        postInvalidateOnAnimation();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    // ── Fill particles (star + petal), riding the flood-fill's leading edge ──

    private void drawParticles(Canvas canvas) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (!p.update()) {
                it.remove();
                continue;
            }
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int) (p.alpha * 255));
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.rotation);
            if (p.type == 0) drawStar(canvas, p.size / 2f);
            else drawPetal(canvas, p.size / 2f);
            canvas.restore();
        }
        if (!particles.isEmpty()) postInvalidateOnAnimation();
    }

    private void drawStar(Canvas canvas, float radius) {
        particlePath.reset();
        float inner = radius / 2.5f;
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(i * 72 - 90);
            particlePath.lineTo((float) Math.cos(a) * radius, (float) Math.sin(a) * radius);
            a = Math.toRadians(i * 72 - 90 + 36);
            particlePath.lineTo((float) Math.cos(a) * inner, (float) Math.sin(a) * inner);
        }
        particlePath.close();
        canvas.drawPath(particlePath, particlePaint);
    }

    private void drawPetal(Canvas canvas, float radius) {
        particleRect.set(-radius / 2f, -radius, radius / 2f, radius);
        canvas.drawOval(particleRect, particlePaint);
    }

    private static class Particle {
        float x, y, vx, vy, alpha, size, rotation, rotationSpeed;
        final int color, type;
        final long lifeTime, startTime;

        Particle(float x, float y, int color, int type, Random random) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.type = type;
            this.startTime = System.currentTimeMillis();
            this.lifeTime = 900 + random.nextInt(900);
            this.alpha = 1f;
            this.size = 14f + random.nextFloat() * 22f;
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
            alpha = 1f - (float) elapsed / lifeTime;
            return true;
        }
    }
}
