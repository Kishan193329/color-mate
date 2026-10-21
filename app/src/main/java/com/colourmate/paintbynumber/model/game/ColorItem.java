package com.colourmate.paintbynumber.model.game;

import android.util.Log;

/**
 * Represents a color item in the color-by-number game.
 * Contains the color value, associated number, and completion status.
 */
public class ColorItem {
    private final int color;
    private final int number;
    private boolean finished;
    private int progress; // Progress percentage (0-100)
    private int totalAreas; // Total areas for this color
    private int completedAreas; // Completed areas for this color

    /**
     * Constructs a new ColorItem with the specified color and number.
     *
     * @param color  the color value (typically an ARGB integer)
     * @param number the number associated with this color
     */
    public ColorItem(int color, int number) {
        this.color = color;
        this.number = number;
        this.finished = false;
        this.progress = 0;
        this.totalAreas = 0;
        this.completedAreas = 0;
    }

    /**
     * Gets the color value.
     *
     * @return the color value as an integer
     */
    public int getColor() {
        return color;
    }

    /**
     * Gets the number associated with this color.
     *
     * @return the number
     */
    public int getNumber() {
        return number;
    }

    /**
     * Checks if this color has been finished/completed.
     *
     * @return true if finished, false otherwise
     */
    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
        if (finished) {
            this.progress = 100;
            this.completedAreas = this.totalAreas; // Ensure completedAreas matches totalAreas
        }
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
        if (this.progress >= 100) {
            this.finished = true;
        }
    }

    public int getTotalAreas() {
        return totalAreas;
    }

    public void setTotalAreas(int totalAreas) {
        this.totalAreas = totalAreas;
        if (this.completedAreas > 0) {
            updateProgress();
        }
    }
    public int getCompletedAreas() {
        return completedAreas;
    }

    public void setCompletedAreas(int completedAreas) {
        this.completedAreas = Math.min(completedAreas, this.totalAreas);
        updateProgress();
    }

    public void incrementCompletedAreas() {
        if (this.completedAreas < this.totalAreas) {
            this.completedAreas++;
            updateProgress();
        }
    }


    private void updateProgress() {
        if (totalAreas > 0) {
            // Calculate progress as a percentage
            float percentage = (completedAreas * 100.0f) / totalAreas;
            this.progress = Math.min(100, (int) Math.ceil(percentage));

            // Mark as finished if all areas completed
            if (this.completedAreas >= this.totalAreas) {
                this.finished = true;
                this.progress = 100;
            }

            Log.d("ColorItem", "Progress updated: " + completedAreas + "/" + totalAreas +
                    " = " + progress + "%, finished: " + finished);
        } else {
            this.progress = 0;
        }
    }

}
