package com.colourmate.paintbynumber.model.diamond;

import android.graphics.Color;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One diamond-painting picture, exactly as ColorForge's {@code diamond_builder.to_json()} emits it:
 *
 * <pre>
 * {"cols":80, "rows":80, "colours":24,
 *  "palette":["#rrggbb", …],
 *  "grid":[[1,2,3,…], …]}      // 1-based palette number per cell
 * </pre>
 *
 * <p>Much simpler than the colour-fill format — no regions, no flood fill. The grid is flattened to
 * a single {@code byte[]} (palette numbers are ≤ 255) so a 220×220 picture is ~48 KB instead of a
 * 48 000-entry boxed list, and {@link #placed} is the player's progress over the same indices.
 */
public class DiamondLevel {

    /** Cell has no bead yet. */
    public static final byte EMPTY = 0;

    public final int cols;
    public final int rows;
    /** ARGB colour per palette number; index 0 unused so palette[n] matches the grid's 1-based n. */
    public final int[] palette;
    /** Palette number per cell, row-major: grid[y * cols + x]. */
    public final byte[] grid;
    /** Player progress: same layout as grid, EMPTY or the palette number that was placed. */
    public final byte[] placed;

    private final int[] totalPerColour;
    private final int[] placedPerColour;
    private int placedTotal;

    private DiamondLevel(int cols, int rows, int[] palette, byte[] grid) {
        this.cols = cols;
        this.rows = rows;
        this.palette = palette;
        this.grid = grid;
        this.placed = new byte[grid.length];

        this.totalPerColour = new int[palette.length];
        this.placedPerColour = new int[palette.length];
        for (byte value : grid) {
            int n = value & 0xFF;
            if (n > 0 && n < totalPerColour.length) totalPerColour[n]++;
        }
    }

    public static DiamondLevel fromJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);

        int cols = root.getInt("cols");
        int rows = root.getInt("rows");

        JSONArray paletteJson = root.getJSONArray("palette");
        // +1 so palette[n] can be looked up with the grid's 1-based number directly.
        int[] palette = new int[paletteJson.length() + 1];
        palette[0] = Color.TRANSPARENT;
        for (int i = 0; i < paletteJson.length(); i++) {
            palette[i + 1] = Color.parseColor(paletteJson.getString(i));
        }

        JSONArray gridJson = root.getJSONArray("grid");
        byte[] grid = new byte[cols * rows];
        for (int y = 0; y < rows && y < gridJson.length(); y++) {
            JSONArray row = gridJson.getJSONArray(y);
            for (int x = 0; x < cols && x < row.length(); x++) {
                grid[y * cols + x] = (byte) row.getInt(x);
            }
        }

        return new DiamondLevel(cols, rows, palette, grid);
    }

    // ─── Play ───

    public int colourAt(int x, int y) {
        if (x < 0 || x >= cols || y < 0 || y >= rows) return 0;
        return grid[y * cols + x] & 0xFF;
    }

    public boolean isPlaced(int x, int y) {
        if (x < 0 || x >= cols || y < 0 || y >= rows) return false;
        return placed[y * cols + x] != EMPTY;
    }

    /**
     * Places a bead of {@code colourNumber} at (x, y) — only where the picture actually wants that
     * colour, which is what makes diamond painting forgiving: a wrong tap is simply ignored.
     *
     * @return true if this call filled a cell that was empty
     */
    public boolean place(int x, int y, int colourNumber) {
        if (x < 0 || x >= cols || y < 0 || y >= rows) return false;

        int index = y * cols + x;
        if (placed[index] != EMPTY) return false;
        if ((grid[index] & 0xFF) != colourNumber) return false;

        placed[index] = (byte) colourNumber;
        placedPerColour[colourNumber]++;
        placedTotal++;
        return true;
    }

    public int totalCells() {
        return grid.length;
    }

    public int placedCells() {
        return placedTotal;
    }

    public int totalOf(int colourNumber) {
        return colourNumber > 0 && colourNumber < totalPerColour.length
                ? totalPerColour[colourNumber] : 0;
    }

    public int placedOf(int colourNumber) {
        return colourNumber > 0 && colourNumber < placedPerColour.length
                ? placedPerColour[colourNumber] : 0;
    }

    public boolean isColourFinished(int colourNumber) {
        return totalOf(colourNumber) > 0 && placedOf(colourNumber) >= totalOf(colourNumber);
    }

    public boolean isFinished() {
        return placedTotal >= grid.length;
    }

    /** Number of palette entries (excluding the unused 0 slot). */
    public int colourCount() {
        return palette.length - 1;
    }

    // ─── Save / restore ───

    /** Progress as a compact string so it can go straight into SharedPreferences. */
    public String encodeProgress() {
        StringBuilder sb = new StringBuilder(placed.length);
        for (byte b : placed) {
            sb.append(b == EMPTY ? '.' : (char) ('0' + (b & 0xFF)));
        }
        return sb.toString();
    }

    public void restoreProgress(String encoded) {
        if (TextUtils.isEmpty(encoded) || encoded.length() != placed.length) return;

        for (int i = 0; i < placed.length; i++) {
            char c = encoded.charAt(i);
            if (c == '.') continue;

            int n = c - '0';
            if (n <= 0 || n >= palette.length) continue;
            if ((grid[i] & 0xFF) != n) continue;   // stale save against a different picture

            placed[i] = (byte) n;
            placedPerColour[n]++;
            placedTotal++;
        }
    }
}
