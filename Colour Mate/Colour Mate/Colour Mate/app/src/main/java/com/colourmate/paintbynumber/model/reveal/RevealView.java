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
        displayBitmap = Bitmap.createBitmap(level.width, level.height, Bitmap.Config.ARGB_8888);
        highlightBitmap = Bitmap.createBitmap(level.width, level.height, Bitmap.Config.ARGB_8888);
        highlightPixels = new int[level.width * level.height];
        highlightDirty = true;
        pushPixels();
        fitToScreen();
    }

    public void setSelectedNumber(int number) {
        this.selectedNumber = number;
        highlightDirty = true;
        invalidate();
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
        if (level == null || displayBitmap == null) return;

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
            if (level.isFinished()) listener.onFinished();
        }
    }

    /** True when the point lands exactly on an unrevealed region of the selected colour. */
    private boolean startsOnSelected(float viewX, float viewY) {
        if (level == null) return false;
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
            region = nearestRegion(x, y, tol, true);
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
            region = nearestRegion(x, y, tolerance(), false);
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
        RevealLevel.Region direct = level.regionAt(x, y);
        if (direct != null && !direct.revealed && direct.number == selectedNumber) return direct;
        return nearestRegion(x, y, tolerance(), true);
    }

    private RevealLevel.Region nearestRegion(int x, int y, int tol, boolean onlySelected) {
        int w = level.width, h = level.height;
        int x0 = Math.max(0, x - tol), x1 = Math.min(w - 1, x + tol);
        int y0 = Math.max(0, y - tol), y1 = Math.min(h - 1, y + tol);
        RevealLevel.Region best = null;
        long bestDist = Long.MAX_VALUE;
        for (int yy = y0; yy <= y1; yy++) {
            int row = yy * w;
            for (int xx = x0; xx <= x1; xx++) {
                int id = level.idMap[row + xx];
                if (id == 0) continue;
                RevealLevel.Region cand = level.regionsById.get(id);
                if (cand == null || cand.revealed) continue;
                if (onlySelected && cand.number != selectedNumber) continue;
                long dx = xx - x, dy = yy - y, dist = dx * dx + dy * dy;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = cand;
                }
            }
        }
        return best;
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
        float maxDist = 1f;                       // reach the farthest corner of the region by the end
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
