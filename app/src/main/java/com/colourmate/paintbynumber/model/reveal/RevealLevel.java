package com.colourmate.paintbynumber.model.reveal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

/**
 * A ColorForge "photo reveal" picture — the game the admin's review preview plays.
 *
 * <p>Tapping a region does not paint a flat colour and stop: it paints the region's colour and
 * then morphs into the <b>original photograph's</b> pixels, so a finished picture is the real
 * photo (see the backend's {@code reveal_game_builder.py}).
 *
 * <p>Built from four files a ColorForge bundle already ships:
 * <ul>
 *   <li>{@code original.jpg/png} — the photo, i.e. what a revealed region shows</li>
 *   <li>{@code id_map.png} — region id per pixel, encoded {@code id = R + G*256}</li>
 *   <li>{@code border.png} — the line art (drawn on top; black in the ALPHA channel)</li>
 *   <li>{@code regions.json} — {@code {w,h,regions:[{id,number,cx,cy,r,bbox}],palette:[{number,color}]}}</li>
 * </ul>
 *
 * <p>Deliberately separate from the flat {@code GameView} engine: the four existing tabs are paid
 * ionicsolutions content with no original photo, so they must keep working exactly as they do.
 */
public class RevealLevel {

    /** One tappable area of the picture. */
    public static class Region {
        public final int id;
        public final int number;
        public final int cx, cy;
        public final float radius;
        public final int left, top, right, bottom;
        /** Cached label string ("12") so the renderer never allocates per frame. */
        public final String label;
        public boolean revealed;

        Region(int id, int number, int cx, int cy, float radius, int[] bbox) {
            this.id = id;
            this.number = number;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.label = Integer.toString(number);
            // bbox is [x, y, width, height] (NOT [left, top, right, bottom]). Reading it as corners
            // made right<left for most regions, so paintRegion's loop never ran and the photo never
            // revealed even though the tap registered. Convert to inclusive corners here.
            this.left = bbox[0];
            this.top = bbox[1];
            this.right = bbox[0] + bbox[2];
            this.bottom = bbox[1] + bbox[3];
        }
    }

    public final int width;
    public final int height;

    /**
     * Region id per pixel (0 = line art / nothing to reveal).
     *
     * <p>A {@code char} because an id is {@code R + G*256} and so cannot exceed 65535 by
     * construction — which halves the array. At 1254x1254 that is 3.1 MB instead of 6.3 MB,
     * on a screen that already holds two more arrays the same size plus the border bitmap.
     */
    public final char[] idMap;
    /** The photograph, the source every reveal copies from. */
    public final int[] original;
    /** What is on screen right now — starts blank, ends up equal to {@link #original}. */
    public final int[] display;

    public final Bitmap border;
    /** The same line as a vector, when the picture has one. Null falls back to {@link #border}. */
    public final BorderVector borderVector;
    public final SparseArray<Region> regionsById = new SparseArray<>();
    public final Region[] regions;
    /** Palette colour per number (index 0 unused, so palette[n] matches the printed number). */
    public final int[] palette;

    private int revealedCount;
    private final int[] totalPerNumber;
    private final int[] revealedPerNumber;

    public interface Source {
        InputStream open(String name) throws Exception;
    }

    private RevealLevel(int width, int height, char[] idMap, int[] original, int[] start,
                        Bitmap border, BorderVector borderVector,
                        Region[] regions, int[] palette) {
        this.width = width;
        this.height = height;
        this.idMap = idMap;
        this.original = original;
        this.border = border;
        this.borderVector = borderVector;
        this.regions = regions;
        this.palette = palette;

        this.display = new int[original.length];
        // Unrevealed picture = paper, so the line art and numbers read cleanly — or, for a
        // Figma-SVG picture, start.jpg: a light grey sketch of its own colours, so the picture is
        // readable before anything is filled.
        if (start != null && start.length == display.length) {
            System.arraycopy(start, 0, display, 0, start.length);
        } else {
            java.util.Arrays.fill(display, Color.WHITE);
        }

        this.totalPerNumber = new int[palette.length];
        this.revealedPerNumber = new int[palette.length];
        for (Region region : regions) {
            regionsById.put(region.id, region);
            if (region.number > 0 && region.number < totalPerNumber.length) {
                totalPerNumber[region.number]++;
            }
        }
    }

    public static RevealLevel load(Source source) throws Exception {
        JSONObject meta;
        try (InputStream in = source.open("regions.json")) {
            meta = new JSONObject(readAll(in));
        }

        int width = meta.getInt("w");
        int height = meta.getInt("h");

        JSONArray paletteJson = meta.getJSONArray("palette");
        int maxNumber = 0;
        for (int i = 0; i < paletteJson.length(); i++) {
            maxNumber = Math.max(maxNumber, paletteJson.getJSONObject(i).getInt("number"));
        }
        int[] palette = new int[maxNumber + 1];
        for (int i = 0; i < paletteJson.length(); i++) {
            JSONObject entry = paletteJson.getJSONObject(i);
            palette[entry.getInt("number")] = Color.parseColor(entry.getString("color"));
        }

        JSONArray regionsJson = meta.getJSONArray("regions");
        Region[] regions = new Region[regionsJson.length()];
        for (int i = 0; i < regionsJson.length(); i++) {
            JSONObject entry = regionsJson.getJSONObject(i);
            JSONArray bboxJson = entry.getJSONArray("bbox");
            int[] bbox = {bboxJson.getInt(0), bboxJson.getInt(1),
                    bboxJson.getInt(2), bboxJson.getInt(3)};
            regions[i] = new Region(entry.getInt("id"), entry.getInt("number"),
                    entry.getInt("cx"), entry.getInt("cy"),
                    (float) entry.optDouble("r", 12), bbox);
        }

        int[] original = readPixels(source, "original.jpg", "original.png", width, height);
        int[] idPixels = readPixels(source, "id_map.png", null, width, height);

        // id = R + G*256, straight from the backend's encoding.
        char[] idMap = new char[idPixels.length];
        for (int i = 0; i < idPixels.length; i++) {
            int pixel = idPixels[i];
            idMap[i] = (char) (((pixel >> 16) & 0xFF) + (((pixel >> 8) & 0xFF) << 8));
        }

        // Prefer the vector: the raster is 1254px and shows its own pixels once the player zooms
        // in. Older published items have no border.svg, so the PNG stays as the fallback and both
        // are kept — the raster is what gets drawn when the picture is at or near fit, where it is
        // indistinguishable and cheaper than a 5,000-point path.
        BorderVector borderVector = null;
        try (InputStream in = source.open("border.svg")) {
            borderVector = BorderVector.parse(in);
        } catch (Exception ignored) {
            // no vector for this picture — expected for anything published before it existed
        }

        Bitmap border;
        try (InputStream in = source.open("border.png")) {
            border = BitmapFactory.decodeStream(in);
        }

        // Optional: only Figma-SVG pictures have a sketch canvas; everything else starts on white.
        int[] start = null;
        try {
            start = readPixels(source, "start.jpg", null, width, height);
        } catch (Exception ignored) {
            // no start.jpg for this picture — expected
        }

        return new RevealLevel(width, height, idMap, original, start, border, borderVector,
                regions, palette);
    }

    private static int[] readPixels(Source source, String name, String fallback,
                                    int width, int height) throws Exception {
        Bitmap bitmap = null;
        try {
            try (InputStream in = source.open(name)) {
                bitmap = BitmapFactory.decodeStream(in);
            }
        } catch (Exception ignored) {
            // try the fallback name below
        }
        if (bitmap == null && fallback != null) {
            try (InputStream in = source.open(fallback)) {
                bitmap = BitmapFactory.decodeStream(in);
            }
        }
        if (bitmap == null) throw new IllegalStateException("Missing " + name);

        if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, false);
            bitmap.recycle();
            bitmap = scaled;
        }

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.recycle();
        return pixels;
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toString("UTF-8");
    }

    // ─── Play ───

    public Region regionAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return null;
        return regionsById.get(idMap[y * width + x]);
    }

    /**
     * Writes the region into {@link #display}, blended {@code progress} of the way from its flat
     * palette colour to the photograph — that blend IS the morph the admin preview shows.
     */
    public void paintRegion(Region region, float progress) {
        int flat = region.number < palette.length ? palette[region.number] : Color.GRAY;

        for (int y = region.top; y <= region.bottom && y < height; y++) {
            int rowStart = y * width;
            for (int x = region.left; x <= region.right && x < width; x++) {
                int index = rowStart + x;
                if (idMap[index] != region.id) continue;

                display[index] = progress >= 1f ? original[index]
                        : blend(flat, original[index], progress);
            }
        }
    }

    /**
     * Progressive flood fill: reveal only the region's pixels within {@code radius} of the touch
     * point ({@code ox,oy}) — so the colour spreads outward from where the user tapped. Monotonic
     * (pixels only turn on) and clipped to {@code idMap==region.id}, so boundaries stay perfectly
     * clean, never leaking past the outline.
     */
    public void paintRegionProgressive(Region region, int ox, int oy, float radius) {
        float r2 = radius * radius;
        for (int y = Math.max(0, region.top); y <= region.bottom && y < height; y++) {
            int rowStart = y * width;
            float dy = y - oy;
            for (int x = Math.max(0, region.left); x <= region.right && x < width; x++) {
                int index = rowStart + x;
                if (idMap[index] != region.id) continue;
                float dx = x - ox;
                if (dx * dx + dy * dy <= r2) display[index] = original[index];
            }
        }
    }

    public void markRevealed(Region region) {
        if (region.revealed) return;

        region.revealed = true;
        revealedCount++;
        if (region.number > 0 && region.number < revealedPerNumber.length) {
            revealedPerNumber[region.number]++;
        }
    }

    private static int blend(int from, int to, float t) {
        return Color.rgb(
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    // ─── Palette swatches ───

    private Bitmap[] swatchCache;

    /**
     * A real crop of the photograph for a palette number — the web build ships these as
     * {@code swatches/<n>.png}; here they are cut from {@link #original} instead, so no extra
     * files have to be downloaded or bundled.
     *
     * <p>Cut from the biggest region of that number, since a large area is the most
     * representative and least likely to be mostly line art.
     */
    public Bitmap swatch(int number, int size) {
        if (number <= 0 || number >= palette.length) return null;
        if (swatchCache == null) swatchCache = new Bitmap[palette.length];
        if (swatchCache[number] != null) return swatchCache[number];

        Region biggest = null;
        long biggestArea = 0;
        for (Region region : regions) {
            if (region.number != number) continue;

            long area = (long) (region.right - region.left + 1) * (region.bottom - region.top + 1);
            if (area > biggestArea) {
                biggestArea = area;
                biggest = region;
            }
        }
        if (biggest == null) return null;

        // A square window on the region's centre, clamped inside the picture.
        int half = Math.max(4, Math.min(
                Math.min(biggest.right - biggest.left, biggest.bottom - biggest.top) / 2, 40));
        int cx = Math.max(half, Math.min(width - half - 1, biggest.cx));
        int cy = Math.max(half, Math.min(height - half - 1, biggest.cy));
        int side = half * 2;

        int[] crop = new int[side * side];
        for (int y = 0; y < side; y++) {
            System.arraycopy(original, (cy - half + y) * width + (cx - half), crop, y * side, side);
        }

        Bitmap bitmap = Bitmap.createBitmap(crop, side, side, Bitmap.Config.ARGB_8888);
        if (side != size) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size, size, true);
            bitmap.recycle();
            bitmap = scaled;
        }

        swatchCache[number] = bitmap;
        return bitmap;
    }

    // ─── Save / restore ───

    /** Just the revealed region ids — a few hundred numbers, small enough for prefs. */
    public String encodeProgress() {
        StringBuilder sb = new StringBuilder();
        for (Region region : regions) {
            if (!region.revealed) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(region.id);
        }
        return sb.toString();
    }

    /** Re-reveals saved regions and paints them straight to the photo (no morph on restore). */
    public void restoreProgress(String encoded) {
        if (encoded == null || encoded.isEmpty()) return;

        for (String part : encoded.split(",")) {
            try {
                Region region = regionsById.get(Integer.parseInt(part.trim()));
                // An id that is not in this picture means the save belongs to another one.
                if (region == null || region.revealed) continue;

                markRevealed(region);
                paintRegion(region, 1f);
            } catch (NumberFormatException ignored) {
                // skip a corrupt entry rather than lose the whole save
            }
        }
    }

    public int numberCount() {
        return palette.length - 1;
    }

    public int totalOf(int number) {
        return number > 0 && number < totalPerNumber.length ? totalPerNumber[number] : 0;
    }

    public int revealedOf(int number) {
        return number > 0 && number < revealedPerNumber.length ? revealedPerNumber[number] : 0;
    }

    public boolean isNumberFinished(int number) {
        return totalOf(number) > 0 && revealedOf(number) >= totalOf(number);
    }

    public int progressPercent() {
        return regions.length == 0 ? 0 : Math.round(revealedCount * 100f / regions.length);
    }

    public boolean isFinished() {
        return revealedCount >= regions.length;
    }
}
