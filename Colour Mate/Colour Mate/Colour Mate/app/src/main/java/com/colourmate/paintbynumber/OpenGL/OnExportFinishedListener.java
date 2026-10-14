package com.colourmate.paintbynumber.OpenGL;

import android.graphics.Bitmap;

/**
 * Callback for notifying when the export process has finished.
 *
 * @param base      The exported base image (may be null)
 * @param paint     The exported painted image (may be null)
 * @param combined  The fully combined final export (may be null)
 */
public interface OnExportFinishedListener {
    void onExportFinished(Bitmap base, Bitmap paint, Bitmap combined);
}

