package com.colourmate.paintbynumber.model.book;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;

/**
 * Free colouring of one line-art page (our Colour Book): pick any colour, tap a shape, it fills.
 *
 * <p>On load the page is split once into shapes — connected areas of light pixels, walls being the
 * dark line art — so a tap is a lookup, not a flood fill, and undo is one shape's old colour. The
 * line art is drawn over the colours with MULTIPLY, so its anti-aliased edges stay clean.
 */
public class BookView extends View {

    public interface Listener {
        void onFilled();
    }

    private static final int WALL_LUMA = 160;      // darker than this = line art
    private static final float MAX_ZOOM = 10f;
    private static final int MAX_UNDO = 40;

    private Bitmap lineArt;            // the page, opaque
    private Bitmap colourLayer;        // what the player has painted
    private int width, height;
    private int[] label;               // shape id per pixel, 0 = wall
    private int[] shapeColour;         // per shape, 0 = unpainted
    private int[] bboxL, bboxT, bboxR, bboxB;
    private int shapes;

    private int brush = 0xFFFF6B6B;
    private Listener listener;
    private final ArrayDeque<int[]> undo = new ArrayDeque<>();   // {shape, previous colour}

    private final Matrix matrix = new Matrix();
    private float scale = 1f, fitScale = 1f, offsetX, offsetY;
    private boolean pinching;
    private final Paint plain = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint multiply = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    public BookView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        multiply.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float target = Math.max(fitScale, Math.min(fitScale * MAX_ZOOM, scale * d.getScaleFactor()));
                float ratio = target / scale;
                offsetX = d.getFocusX() - (d.getFocusX() - offsetX) * ratio;
                offsetY = d.getFocusY() - (d.getFocusY() - offsetY) * ratio;
                scale = target;
                clamp();
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!pinching) fillAt(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                offsetX -= dx;
                offsetY -= dy;
                clamp();
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                fitToScreen();
                return true;
            }
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setBrush(int colour) {
        brush = colour | 0xFF000000;
    }

    /** Splits the page into shapes. Heavy for a big page: call off the UI thread, then show(). */
    public static Prepared prepare(Bitmap page) {
        int w = page.getWidth(), h = page.getHeight();
        int[] px = new int[w * h];
        page.getPixels(px, 0, w, 0, 0, w, h);
        int[] lab = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int luma = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
            lab[i] = luma < WALL_LUMA ? 0 : -1;
        }
        int next = 1;
        int[] stack = new int[w * h];
        java.util.ArrayList<int[]> boxes = new java.util.ArrayList<>();
        boxes.add(new int[]{0, 0, 0, 0});
        for (int start = 0; start < lab.length; start++) {
            if (lab[start] != -1) continue;
            int id = next++;
            int l = w, t = h, r = 0, b = 0, sp = 0;
            stack[sp++] = start;
            lab[start] = id;
            while (sp > 0) {
                int p = stack[--sp];
                int x = p % w, y = p / w;
                if (x < l) l = x;
                if (x > r) r = x;
                if (y < t) t = y;
                if (y > b) b = y;
                if (x > 0 && lab[p - 1] == -1) { lab[p - 1] = id; stack[sp++] = p - 1; }
                if (x < w - 1 && lab[p + 1] == -1) { lab[p + 1] = id; stack[sp++] = p + 1; }
                if (y > 0 && lab[p - w] == -1) { lab[p - w] = id; stack[sp++] = p - w; }
                if (y < h - 1 && lab[p + w] == -1) { lab[p + w] = id; stack[sp++] = p + w; }
            }
            boxes.add(new int[]{l, t, r, b});
        }
        return new Prepared(page, lab, next, boxes);
    }

    public static final class Prepared {
        final Bitmap page;
        final int[] label;
        final int shapes;
        final java.util.List<int[]> boxes;

        Prepared(Bitmap page, int[] label, int shapes, java.util.List<int[]> boxes) {
            this.page = page;
            this.label = label;
            this.shapes = shapes;
            this.boxes = boxes;
        }
    }

    public void show(Prepared p, @Nullable String saved) {
        lineArt = p.page;
        width = p.page.getWidth();
        height = p.page.getHeight();
        label = p.label;
        shapes = p.shapes;
        shapeColour = new int[shapes];
        bboxL = new int[shapes]; bboxT = new int[shapes]; bboxR = new int[shapes]; bboxB = new int[shapes];
        for (int i = 0; i < shapes; i++) {
            int[] bb = p.boxes.get(i);
            bboxL[i] = bb[0]; bboxT[i] = bb[1]; bboxR[i] = bb[2]; bboxB[i] = bb[3];
        }
        colourLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        colourLayer.eraseColor(Color.WHITE);
        restore(saved);
        fitToScreen();
    }

    // ─── Painting ───

    private void fillAt(float vx, float vy) {
        if (label == null) return;
        int x = (int) ((vx - offsetX) / scale), y = (int) ((vy - offsetY) / scale);
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int s = label[y * width + x];
        if (s <= 0) s = nearestShape(x, y, Math.max(3, (int) (12 / scale)));
        if (s <= 0 || shapeColour[s] == brush) return;
        undo.push(new int[]{s, shapeColour[s]});
        if (undo.size() > MAX_UNDO) undo.removeLast();
        paint(s, brush);
        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        if (listener != null) listener.onFilled();
    }

    /** A tap that lands on the line itself means the shape next to it. */
    private int nearestShape(int x, int y, int r) {
        int best = 0, bestD = Integer.MAX_VALUE;
        for (int yy = Math.max(0, y - r); yy <= Math.min(height - 1, y + r); yy++) {
            for (int xx = Math.max(0, x - r); xx <= Math.min(width - 1, x + r); xx++) {
                int s = label[yy * width + xx];
                if (s <= 0) continue;
                int d = (xx - x) * (xx - x) + (yy - y) * (yy - y);
                if (d < bestD) { bestD = d; best = s; }
            }
        }
        return best;
    }

    private void paint(int s, int colour) {
        shapeColour[s] = colour;
        int l = bboxL[s], t = bboxT[s], w = bboxR[s] - l + 1, h = bboxB[s] - t + 1;
        int[] px = new int[w * h];
        colourLayer.getPixels(px, 0, w, l, t, w, h);
        int fill = colour == 0 ? Color.WHITE : colour;
        for (int yy = 0; yy < h; yy++) {
            int row = (t + yy) * width + l;
            for (int xx = 0; xx < w; xx++) {
                if (label[row + xx] == s) px[yy * w + xx] = fill;
            }
        }
        colourLayer.setPixels(px, 0, w, l, t, w, h);
        invalidate();
    }

    public boolean undo() {
        int[] last = undo.poll();
        if (last == null) return false;
        paint(last[0], last[1]);
        return true;
    }

    public int paintedPercent() {
        if (shapes <= 1) return 0;
        int done = 0;
        for (int i = 1; i < shapes; i++) if (shapeColour[i] != 0) done++;
        return Math.round(done * 100f / (shapes - 1));
    }

    /** "shape:colour,..." — only painted shapes, small enough for preferences. */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        if (shapeColour == null) return "";
        for (int i = 1; i < shapes; i++) {
            if (shapeColour[i] == 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(i).append(':').append(Integer.toHexString(shapeColour[i]));
        }
        return sb.toString();
    }

    private void restore(@Nullable String saved) {
        if (saved == null || saved.isEmpty()) return;
        for (String part : saved.split(",")) {
            int colon = part.indexOf(':');
            if (colon <= 0) continue;
            try {
                int s = Integer.parseInt(part.substring(0, colon));
                int c = (int) Long.parseLong(part.substring(colon + 1), 16);
                if (s > 0 && s < shapes) paint(s, c);
            } catch (NumberFormatException ignored) {
                // a corrupt entry; keep the rest
            }
        }
    }

    /** The finished page at full size: colours with the line art on top. */
    public Bitmap render() {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(colourLayer, 0, 0, plain);
        c.drawBitmap(lineArt, 0, 0, multiply);
        return out;
    }

    // ─── View ───

    public void fitToScreen() {
        if (lineArt == null || getWidth() == 0) return;
        fitScale = Math.min(getWidth() / (float) width, getHeight() / (float) height);
        scale = fitScale;
        offsetX = (getWidth() - width * scale) / 2f;
        offsetY = (getHeight() - height * scale) / 2f;
        invalidate();
    }

    private void clamp() {
        float vw = width * scale, vh = height * scale;
        offsetX = vw <= getWidth() ? (getWidth() - vw) / 2f : Math.min(0, Math.max(getWidth() - vw, offsetX));
        offsetY = vh <= getHeight() ? (getHeight() - vh) / 2f : Math.min(0, Math.max(getHeight() - vh, offsetY));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fitToScreen();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) pinching = false;
        if (e.getPointerCount() > 1) pinching = true;
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (lineArt == null) return;
        matrix.setScale(scale, scale);
        matrix.postTranslate(offsetX, offsetY);
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawBitmap(colourLayer, matrix, plain);
        canvas.drawBitmap(lineArt, matrix, multiply);
        canvas.restoreToCount(save);
    }
}
