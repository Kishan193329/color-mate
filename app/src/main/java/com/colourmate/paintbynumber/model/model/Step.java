package com.colourmate.paintbynumber.model.model;

/**
 * Represents a single coloring action (one filled coordinate).
 * Used to save and restore user progress.
 */
public class Step {

    /** X coordinate in bitmap space */
    private int x;

    /** Y coordinate in bitmap space */
    private int y;

    // -------------------- CONSTRUCTOR --------------------

    public Step(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // -------------------- GETTERS / SETTERS --------------------

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

    // -------------------- DEBUG --------------------

    @Override
    public String toString() {
        return "Step{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
