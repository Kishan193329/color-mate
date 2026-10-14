package com.colourmate.paintbynumber.model.model;

/**
 * Represents a single colorable point/area in the image.
 * Used to display the color number and to check fill completion.
 */
public class ColorArea {

    /** Actual ARGB color value */
    private int color;

    /** X coordinate in bitmap space */
    private int x;

    /** Y coordinate in bitmap space */
    private int y;

    /** Base text size used to draw color number */
    private int textSize;

    // -------------------- CONSTRUCTOR --------------------

    public ColorArea(int color, int x, int y, int textSize) {
        this.color = color;
        this.x = x;
        this.y = y;
        this.textSize = textSize;
    }

    // -------------------- GETTERS / SETTERS --------------------

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getTextSize() {
        return textSize;
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;
    }

    // -------------------- DEBUG --------------------

    @Override
    public String toString() {
        return "ColorArea{" +
                "color=" + color +
                ", x=" + x +
                ", y=" + y +
                ", textSize=" + textSize +
                '}';
    }
}
