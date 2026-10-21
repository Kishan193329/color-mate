package com.colourmate.paintbynumber.model.reveal;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws region numbers the way a polished colour-by-number does: sparse, never overlapping, always
 * inside their region, sized to the region, thinning out at low zoom and appearing as you zoom in.
 *
 * <p>Geometry comes from the backend: each region's {@code cx,cy} is its pole of inaccessibility
 * (most-interior point) and {@code radius} its inscribed radius, so a number centred at {@code cx,cy}
 * and small enough to sit inside that circle is guaranteed to stay within the cell.
 *
 * <p>Every draw: cull off-screen and too-small labels, size each, then place them in priority order
 * (selected colour first, then largest) rejecting any that would collide with one already placed
 * (screen-space, via a spatial hash grid). Unselected labels are faded so the chosen colour stands
 * out. All tunables live in {@link Config}.
 */
public class SmartLabelRenderer {

    /** Every threshold in one place — no scattered magic numbers. Values are in dp unless noted. */
    public static class Config {
        // Matched to the By Number game exactly (owner: "by number ma jem che size/style aj apply kar"):
        // textSize = inscribed radius × scale × 0.8 (TEXT_SCALE_FACTOR), hidden below 12dp
        // (min_text_size), capped at 60dp (max_text_size); default font, not bold, black.
        public float minFontDp = 11f;         // labels below this are hidden
        // By Number feeds its formula an alpha-channel size HINT (usually modest); our inscribed
        // radius is ~2× that, so we keep By Number's 0.8 factor but cap at a By-Number-like 24dp
        // instead of the raw 60dp ceiling — otherwise big regions render oversized numbers.
        public float maxFontDp = 24f;
        public float fontFillFactor = 0.8f;   // By Number TEXT_SCALE_FACTOR
        public float twoDigitScale = 1.0f;    // By Number does not shrink by digit count…
        public float threeDigitScale = 1.0f;  // …the fit-inside-region check below is our only safety
        public float fitFraction = 0.92f;     // glyph half-diagonal must stay within this × radius
        public float minSafeRadiusDp = 6f;    // (subsumed by the 12dp min-font gate; kept as a floor)
        // Numbers only appear once the user has zoomed in past this multiple of the fit scale — at the
        // default full view the picture stays clean (owner: "pele thi j number shu kam, zoom kare tyare j").
        public float minZoomRatioToShow = 1.6f;
        public float collisionPaddingDp = 1.5f;
        public int selectedAlpha = 255;
        // All numbers are solid black (owner: "black colour hovo pde"); selection is shown by the
        // grey region highlight (COLOR_HIGHLIGHTED), like By Number — not by fading the labels.
        public int unselectedAlpha = 255;
        public int textColor = 0xFF000000;    // By Number MEASURED_STATE_MASK — black, simple (not bold)
        /** Halo thickness as a fraction of the font size; 0 turns it off. */
        public float haloWidthFactor = 0.30f;
        public int haloColor = 0xFFFFFFFF;
        public float offscreenMarginDp = 24f;
    }

    private final Config cfg;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Stroked in the paper colour under each glyph so a digit never sinks into the line art. */
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();

    // Reused across frames to avoid per-frame allocation churn.
    private final List<Candidate> candidates = new ArrayList<>();
    private final Map<Long, List<float[]>> grid = new HashMap<>();

    private final Comparator<Candidate> priority = (a, b) -> {
        if (a.selected != b.selected) return a.selected ? -1 : 1;   // selected colour wins
        return Float.compare(b.safeR, a.safeR);                     // then bigger regions
    };

    public SmartLabelRenderer(Config cfg, float density) {
        this.cfg = cfg;
        this.density = density;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);   // simple, not bold
        halo.setTextAlign(Paint.Align.CENTER);
        halo.setTypeface(Typeface.DEFAULT);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeJoin(Paint.Join.ROUND);
        halo.setColor(cfg.haloColor);
    }

    private float dp(float v) {
        return v * density;
    }

    private static final class Candidate {
        RevealLevel.Region region;
        float sx, sy, font, safeR;
        boolean selected;
    }

    /**
     * @param scale/offsetX/offsetY the image→screen transform (screen = image*scale + offset)
     * @param viewW/viewH           the canvas size for off-screen culling
     */
    public void draw(Canvas canvas, RevealLevel level, int selectedNumber,
                     float scale, float fitScale, float offsetX, float offsetY, int viewW, int viewH,
                     float bottomInset) {
        if (level == null) return;

        // Show numbers only once zoomed in — the full view stays clean.
        if (fitScale > 0f && scale < fitScale * cfg.minZoomRatioToShow) return;

        final float minFont = dp(cfg.minFontDp);
        final float maxFont = dp(cfg.maxFontDp);
        final float minSafeR = dp(cfg.minSafeRadiusDp);
        final float pad = dp(cfg.collisionPaddingDp);
        final float margin = dp(cfg.offscreenMarginDp);

        // The image's on-screen frame — a label must stay fully inside it (never in the margins).
        // Clip to the picture AND to the part of the view nothing is sitting on top of. The
        // palette strip floats over the bottom of the board, so a number placed there is half
        // covered — visible enough to look broken, not enough to read.
        final float frameL = Math.max(offsetX, 0f);
        final float frameT = Math.max(offsetY, 0f);
        final float frameR = Math.min(offsetX + level.width * scale, viewW);
        final float frameB = Math.min(offsetY + level.height * scale, viewH - bottomInset);

        // ── 1. Candidates: unfilled, on-screen, big enough at this zoom, and able to fit a glyph.
        candidates.clear();
        for (RevealLevel.Region r : level.regions) {
            if (r.revealed) continue;                                   // filled → no label
            float safeR = r.radius * scale;                             // inscribed radius, screen px
            if (safeR < minSafeR) continue;                             // too small at this zoom tier

            float sx = offsetX + r.cx * scale;
            float sy = offsetY + r.cy * scale;
            if (sx < -margin || sy < -margin || sx > viewW + margin || sy > viewH + margin) {
                continue;                                               // off-screen
            }

            float font = safeR * cfg.fontFillFactor;
            int digits = r.label.length();
            if (digits == 2) font *= cfg.twoDigitScale;
            else if (digits >= 3) font *= cfg.threeDigitScale;
            if (font > maxFont) font = maxFont;

            // Guarantee it stays inside the inscribed circle: shrink until the glyph's half-diagonal
            // fits, and drop it if that would push it below the minimum readable size.
            paint.setTextSize(font);
            paint.getTextBounds(r.label, 0, r.label.length(), bounds);
            float halfDiag = 0.5f * (float) Math.hypot(bounds.width(), bounds.height());
            float limit = safeR * cfg.fitFraction;
            if (halfDiag > limit && halfDiag > 0f) font *= limit / halfDiag;
            if (font < minFont) continue;

            Candidate c = new Candidate();
            c.region = r;
            c.sx = sx;
            c.sy = sy;
            c.font = font;
            c.safeR = safeR;
            c.selected = r.number == selectedNumber;
            candidates.add(c);
        }

        // ── 2. Place in priority order, rejecting collisions.
        Collections.sort(candidates, priority);
        grid.clear();
        float cell = maxFont + pad * 2f;
        paint.setColor(cfg.textColor);

        for (Candidate c : candidates) {
            paint.setTextSize(c.font);
            paint.getTextBounds(c.region.label, 0, c.region.label.length(), bounds);
            float hw = bounds.width() / 2f + pad;
            float hh = bounds.height() / 2f + pad;
            float l = c.sx - hw, t = c.sy - hh, rr = c.sx + hw, bb = c.sy + hh;

            if (l < frameL || t < frameT || rr > frameR || bb > frameB) continue;  // inside frame only
            if (collides(l, t, rr, bb, cell)) continue;
            addToGrid(l, t, rr, bb, cell);

            float baseline = c.sy - (paint.descent() + paint.ascent()) / 2f;

            // A halo, drawn as a thick stroke of the paper colour underneath the glyph.
            //
            // The numbers were plain black on a picture that is mostly black line work — over a
            // dense area a digit simply disappears into the strokes around it. Ringing it in white
            // gives it its own clear space without changing the number's own weight or colour.
            if (cfg.haloWidthFactor > 0f) {
                halo.setTextSize(c.font);
                halo.setStrokeWidth(c.font * cfg.haloWidthFactor);
                halo.setAlpha(c.selected ? cfg.selectedAlpha : cfg.unselectedAlpha);
                canvas.drawText(c.region.label, c.sx, baseline, halo);
            }

            paint.setAlpha(c.selected ? cfg.selectedAlpha : cfg.unselectedAlpha);
            canvas.drawText(c.region.label, c.sx, baseline, paint);
        }
        paint.setAlpha(255);
    }

    // ── Screen-space collision via a uniform spatial hash grid (near-O(n), holds 60 FPS). ──

    private boolean collides(float l, float t, float r, float b, float cell) {
        long cx0 = (long) Math.floor(l / cell), cx1 = (long) Math.floor(r / cell);
        long cy0 = (long) Math.floor(t / cell), cy1 = (long) Math.floor(b / cell);
        for (long cy = cy0; cy <= cy1; cy++) {
            for (long cx = cx0; cx <= cx1; cx++) {
                List<float[]> box = grid.get(key(cx, cy));
                if (box == null) continue;
                for (float[] o : box) {
                    if (l < o[2] && r > o[0] && t < o[3] && b > o[1]) return true;
                }
            }
        }
        return false;
    }

    private void addToGrid(float l, float t, float r, float b, float cell) {
        float[] rect = {l, t, r, b};
        long cx0 = (long) Math.floor(l / cell), cx1 = (long) Math.floor(r / cell);
        long cy0 = (long) Math.floor(t / cell), cy1 = (long) Math.floor(b / cell);
        for (long cy = cy0; cy <= cy1; cy++) {
            for (long cx = cx0; cx <= cx1; cx++) {
                long k = key(cx, cy);
                List<float[]> box = grid.get(k);
                if (box == null) {
                    box = new ArrayList<>(4);
                    grid.put(k, box);
                }
                box.add(rect);
            }
        }
    }

    private static long key(long x, long y) {
        return (x << 32) ^ (y & 0xffffffffL);
    }
}
