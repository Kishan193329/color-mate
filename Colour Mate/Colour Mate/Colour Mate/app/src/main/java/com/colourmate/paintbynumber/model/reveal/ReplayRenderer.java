package com.colourmate.paintbynumber.model.reveal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.SparseArray;

/**
 * The finish replay: the blank page, then the player's fills in the order they made them, then
 * the outline fading away to leave the picture alone — the time-lapse the owner's reference game
 * shows when a picture is done.
 *
 * <p>Works on a small copy of the picture (long side {@code maxSide}), so a frame is a few
 * array copies and one bitmap upload. Frames are produced in order: {@link #frame(int)} must be
 * called with rising indices (or after {@link #reset()}).
 */
public final class ReplayRenderer {

    public static final int FPS = 30;
    private static final float FILL_SEC = 7.0f;   // the fills
    private static final float FADE_SEC = 1.0f;   // the outline fading out
    private static final float HOLD_SEC = 1.0f;   // the finished picture, alone

    public final int width, height;
    private final int[] blank, finished, pixels;
    /** Per filled region (in fill order): the indices of its pixels in the small copy. */
    private final int[][] regionPixels;
    private final int[] underInk;
    private boolean inkFilled;
    private final Bitmap ink, frame;
    private final Paint inkPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect inkRect;
    private int applied, lastFrame = -1;

    public ReplayRenderer(RevealLevel level, int maxSide) {
        float s = Math.min(1f, maxSide / (float) Math.max(level.width, level.height));
        // even sizes: H.264 encoders refuse odd ones
        width = Math.max(2, Math.round(level.width * s) & ~1);
        height = Math.max(2, Math.round(level.height * s) & ~1);

        Bitmap full = Bitmap.createBitmap(level.original, level.width, level.height, Bitmap.Config.ARGB_8888);
        Bitmap small = Bitmap.createScaledBitmap(full, width, height, true);
        full.recycle();
        finished = new int[width * height];
        small.getPixels(finished, 0, width, 0, 0, width, height);
        small.recycle();

        blank = new int[width * height];
        for (int i = 0; i < blank.length; i++) {
            if (!level.sketchStart) {
                blank[i] = Color.WHITE;
            } else {
                // the same light grey sketch start.jpg is: 255 - (255 - L) * 0.5
                int c = finished[i];
                int lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
                int g = 255 - (255 - lum) / 2;
                blank[i] = Color.rgb(g, g, g);
            }
        }

        // which filled region owns each small pixel (nearest sample of the id map)
        int[] order = level.fillOrder();
        SparseArray<Integer> slot = new SparseArray<>();
        for (int i = 0; i < order.length; i++) slot.put(order[i], i);
        int[] count = new int[order.length];
        int[] owner = new int[width * height];
        for (int y = 0; y < height; y++) {
            int sy = Math.min(level.height - 1, (int) ((y + 0.5f) / s));
            for (int x = 0; x < width; x++) {
                int sx = Math.min(level.width - 1, (int) ((x + 0.5f) / s));
                Integer k = slot.get(level.idMap[sy * level.width + sx]);
                int o = k == null ? -1 : k;
                owner[y * width + x] = o;
                if (o >= 0) count[o]++;
            }
        }
        // pixels no filled region owns (under the outline) go in with the fade, or the picture
        // would be left with white cracks where the lines were
        int rest = 0;
        for (int o : owner) if (o < 0) rest++;
        regionPixels = new int[order.length][];
        for (int i = 0; i < order.length; i++) regionPixels[i] = new int[count[i]];
        underInk = new int[rest];
        int[] fill = new int[order.length];
        int r = 0;
        for (int i = 0; i < owner.length; i++) {
            int o = owner[i];
            if (o >= 0) regionPixels[o][fill[o]++] = i;
            else underInk[r++] = i;
        }

        ink = level.border;
        inkRect = new Rect(0, 0, width, height);
        pixels = new int[width * height];
        frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        reset();
    }

    public int frameCount() {
        return Math.round((FILL_SEC + FADE_SEC + HOLD_SEC) * FPS);
    }

    public void reset() {
        System.arraycopy(blank, 0, pixels, 0, pixels.length);
        applied = 0;
        lastFrame = -1;
        inkFilled = false;
    }

    /** Draws frame {@code index} into {@code c} (sized {@link #width} x {@link #height}). */
    public void draw(int index, Canvas c) {
        if (index < lastFrame) reset();
        lastFrame = index;

        float t = index / (float) FPS;
        // fills ease in: a slow start reads as "the page is blank", then it speeds up
        float f = Math.min(1f, t / FILL_SEC);
        int target = Math.round(regionPixels.length * (f * f * (3 - 2 * f)));
        while (applied < target) {
            for (int p : regionPixels[applied]) pixels[p] = finished[p];
            applied++;
        }
        if (!inkFilled && t >= FILL_SEC) {
            for (int p : underInk) pixels[p] = finished[p];
            inkFilled = true;
        }
        frame.setPixels(pixels, 0, width, 0, 0, width, height);
        c.drawBitmap(frame, 0f, 0f, null);

        float fade = Math.max(0f, Math.min(1f, (t - FILL_SEC) / FADE_SEC));
        if (ink != null && fade < 1f) {
            inkPaint.setAlpha(Math.round(255 * (1f - fade)));
            c.drawBitmap(ink, null, inkRect, inkPaint);
        }
    }

    public void release() {
        frame.recycle();
    }
}
