package com.colourmate.paintbynumber.model.game;

import androidx.annotation.NonNull;

public class Size {
    public float height;
    public float width;

    public Size(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public Size() {
    }

    @NonNull
    public String toString() {
        return "Size[" + this.width + ", " + this.height + "]";
    }
}
