package com.colourmate.paintbynumber.OpenGL;

/**
 * Represents the current painting progress: how many regions
 * have been painted versus the total number of paintable regions.
 */
public class PaintingStatus {

    private final int totalRegions;
    private final int paintedRegions;

    public PaintingStatus(int totalRegions, int paintedRegions) {
        this.totalRegions = totalRegions;
        this.paintedRegions = paintedRegions;
    }

    public int getPaintedRegions() {
        return paintedRegions;
    }

    public int getTotalRegions() {
        return totalRegions;
    }

    /**
     * @return painting progress as a float from 0.0 to 1.0
     */
    public float getProgress() {
        return totalRegions == 0 ? 0f : (float) paintedRegions / totalRegions;
    }

    /**
     * @return painting progress in percentage (0–100)
     */
    public int getProgressPercent() {
        return Math.round(getProgress() * 100f);
    }
}

