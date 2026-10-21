package com.colourmate.paintbynumber.model.diamond;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The diamond-painting board.
 *
 * <p>Interaction follows the genre: <b>one finger paints</b> (drag to place a run of beads),
 * <b>two fingers pan and zoom</b>. Only cells that actually want the selected colour accept a
 * bead, so sloppy dragging is safe — that is the whole appeal of diamond painting.
 *
 * <p>Drawing is plain Canvas per cell with culling to the visible range, which is what §4 Phase 2
 * predicted would be enough: there are no regions and no flood fill here, so even a 220×220 board
 * only ever draws the few hundred cells on screen.
 */
public class DiamondView extends View {

    public interface Listener {
        /** A bead (or a run of them) was placed. */
        void onBeadsPlaced(int colourNumber, int placedOfColour, int totalOfColour);

        void onLevelFinished();
    }

    private static final float MIN_SCALE_FACTOR = 1f;    // never smaller than "fits the screen"
    private static final float MAX_SCALE_FACTOR = 8f;
    /** Below this cell size the numbers are unreadable, so we stop drawing them. */
    private static final float NUMBER_MIN_CELL_PX = 22f;

    // Same palette as the web build (diamond_builder._PLAY_HTML) so the app and the browser
    // version of a picture look like the same product.
    private static final int PAPER = 0xFFEFE9DD;
    private static final int INK = 0xA840382E;
    private static final int INK_SELECTED = 0xFF2A241C;
    private static final int SOCKET_RING = 0x29463C30;

    /** Beads are pre-rendered once per colour at this size, then blitted scaled. */
    private static final int BEAD_CACHE_PX = 48;

    private DiamondLevel level;
    private Listener listener;

    private int selectedColour = 1;

    // View transform: board pixel = (cell * scale) with offset
    private float scale = 1f;
    private float fitScale = 1f;
    private float offsetX;
    private float offsetY;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beadPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cellRect = new RectF();

    /** One pre-rendered glossy bead per palette number — built lazily, reused every frame. */
    private Bitmap[] beadCache;

    // Touch state
    private static final int MODE_NONE = 0, MODE_PAINT = 1, MODE_TRANSFORM = 2;
    private int touchMode = MODE_NONE;
    private float lastTouchX, lastTouchY;
    private float lastSpacing;
    private int lastPaintedX = -1, lastPaintedY = -1;

    public DiamondView(Context context) {
        super(context);
        init();
    }

    public DiamondView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setLevel(DiamondLevel level) {
        this.level = level;
        this.beadCache = null;
        requestLayout();
        fitToScreen();
        invalidate();
    }

    public DiamondLevel getLevel() {
        return level;
    }

    public void setSelectedColour(int colourNumber) {
        this.selectedColour = colourNumber;
        invalidate();
    }

    public int getSelectedColour() {
        return selectedColour;
    }

    // ─── Layout / transform ───

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // The banner ad below the board loads seconds after the board is already up and resizes
        // this view. Rerunning fitToScreen() there threw away whatever the player had zoomed into.
        // Keep how far they had zoomed and only recompute what "fitted" means.
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

    /** Recomputes what "fitted" means for the current view size, without moving the board. */
    private void recomputeFit() {
        if (level == null || getWidth() == 0 || getHeight() == 0) return;
        fitScale = Math.min((float) getWidth() / level.cols, (float) getHeight() / level.rows);
    }

    /** Scale so the whole board fits, and centre it. */
    public void fitToScreen() {
        if (level == null || getWidth() == 0 || getHeight() == 0) return;

        recomputeFit();
        scale = fitScale;
        offsetX = (getWidth() - level.cols * scale) / 2f;
        offsetY = (getHeight() - level.rows * scale) / 2f;
        invalidate();
    }

    private void clampTransform() {
        float minScale = fitScale * MIN_SCALE_FACTOR;
        float maxScale = fitScale * MAX_SCALE_FACTOR;
        scale = Math.max(minScale, Math.min(maxScale, scale));

        float boardW = level.cols * scale;
        float boardH = level.rows * scale;

        // Centre while the board is smaller than the view, otherwise keep it covering the view.
        offsetX = boardW <= getWidth()
                ? (getWidth() - boardW) / 2f
                : Math.max(getWidth() - boardW, Math.min(0, offsetX));
        offsetY = boardH <= getHeight()
                ? (getHeight() - boardH) / 2f
                : Math.max(getHeight() - boardH, Math.min(0, offsetY));
    }

    // ─── Drawing ───

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (level == null || getWidth() == 0) return;

        // Only the cells actually on screen.
        int firstX = Math.max(0, (int) Math.floor((-offsetX) / scale));
        int firstY = Math.max(0, (int) Math.floor((-offsetY) / scale));
        int lastX = Math.min(level.cols - 1, (int) Math.ceil((getWidth() - offsetX) / scale));
        int lastY = Math.min(level.rows - 1, (int) Math.ceil((getHeight() - offsetY) / scale));

        // The board is "paper" the picture is stuck onto, exactly like the web build.
        canvas.drawRect(offsetX, offsetY,
                offsetX + level.cols * scale, offsetY + level.rows * scale,
                paperPaint());

        boolean drawNumbers = scale >= NUMBER_MIN_CELL_PX;
        if (drawNumbers) numberPaint.setTextSize(scale * 0.4f);

        for (int y = firstY; y <= lastY; y++) {
            float top = offsetY + y * scale;

            for (int x = firstX; x <= lastX; x++) {
                float left = offsetX + x * scale;
                int number = level.colourAt(x, y);
                if (number == 0) continue;

                cellRect.set(left, top, left + scale, top + scale);

                if (level.isPlaced(x, y)) {
                    Bitmap bead = bead(number);
                    if (bead != null) canvas.drawBitmap(bead, null, cellRect, beadPaint);
                } else {
                    drawSocket(canvas, number, number == selectedColour, drawNumbers);
                }
            }
        }
    }

    private Paint paperPaint() {
        cellPaint.setColor(PAPER);
        cellPaint.setStyle(Paint.Style.FILL);
        return cellPaint;
    }

    /**
     * An empty bead slot: a faint wash of the target colour so the picture reads even unplaced,
     * a socket ring, and the colour number. The selected colour is pushed forward — stronger wash,
     * coloured ring, darker number — so the next beads to place stand out.
     */
    private void drawSocket(Canvas canvas, int number, boolean isSelected, boolean drawNumbers) {
        int colour = level.palette[number];
        float radius = cellRect.width() / 2f;
        float cx = cellRect.centerX(), cy = cellRect.centerY();

        cellPaint.setStyle(Paint.Style.FILL);
        cellPaint.setColor(colour);
        cellPaint.setAlpha(isSelected ? 107 : 43);          // .42 / .17 in the web build
        canvas.drawRoundRect(cellRect.left + 1, cellRect.top + 1,
                cellRect.right - 1, cellRect.bottom - 1,
                cellRect.width() * 0.3f, cellRect.height() * 0.3f, cellPaint);
        cellPaint.setAlpha(255);

        ringPaint.setColor(isSelected ? colour : SOCKET_RING);
        ringPaint.setStrokeWidth(isSelected ? Math.max(1.6f, scale * 0.09f) : 1f);
        canvas.drawCircle(cx, cy, radius * 0.62f, ringPaint);

        if (isSelected) {
            ringPaint.setColor(0x8CFFFFFF);
            ringPaint.setStrokeWidth(1f);
            canvas.drawCircle(cx, cy, radius * 0.62f, ringPaint);
        }

        if (drawNumbers) {
            numberPaint.setColor(isSelected ? INK_SELECTED : INK);
            canvas.drawText(String.valueOf(number), cx,
                    cy + numberPaint.getTextSize() * 0.36f, numberPaint);
        }
    }

    /** Builds (once) the glossy bead for a palette number: radial shade + two speculars. */
    private Bitmap bead(int number) {
        if (level == null || number <= 0 || number >= level.palette.length) return null;

        if (beadCache == null) beadCache = new Bitmap[level.palette.length];
        if (beadCache[number] != null) return beadCache[number];

        int size = BEAD_CACHE_PX;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);

        int colour = level.palette[number];
        float r = size / 2f, cx = r, cy = r;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(
                cx - r * 0.32f, cy - r * 0.36f, r * 1.02f,
                new int[]{shade(colour, 62), colour, shade(colour, -42)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.93f, p);

        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.6f, size * 0.035f));
        p.setColor(0x29000000);
        c.drawCircle(cx, cy, r * 0.93f, p);

        // Highlights: a tilted ellipse top-left, a small dot bottom-right.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xB8FFFFFF);
        c.save();
        c.rotate((float) Math.toDegrees(-0.6), cx - r * 0.30f, cy - r * 0.34f);
        c.drawOval(cx - r * 0.30f - r * 0.20f, cy - r * 0.34f - r * 0.15f,
                cx - r * 0.30f + r * 0.20f, cy - r * 0.34f + r * 0.15f, p);
        c.restore();

        p.setColor(0x47FFFFFF);
        c.drawCircle(cx + r * 0.30f, cy + r * 0.32f, r * 0.11f, p);

        beadCache[number] = bitmap;
        return bitmap;
    }

    private static int shade(int colour, int delta) {
        return Color.rgb(
                Math.max(0, Math.min(255, Color.red(colour) + delta)),
                Math.max(0, Math.min(255, Color.green(colour) + delta)),
                Math.max(0, Math.min(255, Color.blue(colour) + delta)));
    }

    // ─── Touch ───

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (level == null) return super.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchMode = MODE_PAINT;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                lastPaintedX = lastPaintedY = -1;
                paintAt(event.getX(), event.getY());
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Second finger: stop painting, start pan/zoom.
                touchMode = MODE_TRANSFORM;
                lastSpacing = spacing(event);
                lastTouchX = midX(event);
                lastTouchY = midY(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if (touchMode == MODE_PAINT) {
                    paintAt(event.getX(), event.getY());
                } else if (touchMode == MODE_TRANSFORM && event.getPointerCount() >= 2) {
                    handleTransform(event);
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                touchMode = event.getPointerCount() <= 2 ? MODE_NONE : MODE_TRANSFORM;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchMode = MODE_NONE;
                lastPaintedX = lastPaintedY = -1;
                break;
        }
        return true;
    }

    private void handleTransform(MotionEvent event) {
        float spacing = spacing(event);
        float mx = midX(event), my = midY(event);

        if (lastSpacing > 0 && spacing > 0) {
            // Clamp the scale BEFORE moving the offsets. Deriving them from the raw pinch value
            // and clamping the scale afterwards left the two disagreeing, so at either zoom limit
            // the board lurched under the fingers every frame instead of simply stopping.
            float target = scale * (spacing / lastSpacing);
            float clamped = Math.max(fitScale * MIN_SCALE_FACTOR,
                    Math.min(fitScale * MAX_SCALE_FACTOR, target));
            if (clamped != scale) {
                float ratio = clamped / scale;
                // Zoom about the pinch midpoint so the board does not jump under the fingers.
                offsetX = mx - (mx - offsetX) * ratio;
                offsetY = my - (my - offsetY) * ratio;
                scale = clamped;
            }
        }

        offsetX += mx - lastTouchX;
        offsetY += my - lastTouchY;

        lastSpacing = spacing;
        lastTouchX = mx;
        lastTouchY = my;

        clampTransform();
        invalidate();
    }

    private void paintAt(float viewX, float viewY) {
        int x = (int) Math.floor((viewX - offsetX) / scale);
        int y = (int) Math.floor((viewY - offsetY) / scale);

        // A drag reports many events inside one cell — only act when the cell changes.
        if (x == lastPaintedX && y == lastPaintedY) return;
        lastPaintedX = x;
        lastPaintedY = y;

        if (!level.place(x, y, selectedColour)) return;

        invalidate();

        if (listener != null) {
            listener.onBeadsPlaced(selectedColour,
                    level.placedOf(selectedColour), level.totalOf(selectedColour));
            if (level.isFinished()) listener.onLevelFinished();
        }
    }

    private static float spacing(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    private static float midX(MotionEvent event) {
        return (event.getX(0) + event.getX(1)) / 2f;
    }

    private static float midY(MotionEvent event) {
        return (event.getY(0) + event.getY(1)) / 2f;
    }
}
