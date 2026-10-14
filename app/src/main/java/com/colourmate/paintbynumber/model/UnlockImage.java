package com.colourmate.paintbynumber.model;

import androidx.annotation.NonNull;

public class UnlockImage {
    String filename;
    boolean status = false;

    public UnlockImage(String imageName, boolean unlocked) {
        this.filename = imageName;
        this.status = unlocked;
    }

    public String getFilename() {
        return this.filename;
    }

    public boolean isStatus() {
        return this.status;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    @NonNull
    public String toString() {
        return "fileName: " + this.filename + ",status: " + this.status;
    }
}
