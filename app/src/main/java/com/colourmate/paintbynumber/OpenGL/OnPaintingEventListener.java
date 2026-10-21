package com.colourmate.paintbynumber.OpenGL;

import android.graphics.RectF;

/**
 * Listener for all painting-related events from the PaintingView / PaintingRenderer.
 */
public interface OnPaintingEventListener {

    /**
     * Called when the app should navigate to the share screen.
     */
    void goToShare();

    /**
     * Called when a long press is no longer active.
     */
    void onLongPressDisable();

    /**
     * Called when a long press starts or continues.
     *
     * @param touchRect The region of the last touch during long press.
     */
    void onLongPressEnable(RectF touchRect);

    /**
     * Called when color UI elements need to refresh,
     * typically after the user lifts their finger.
     */
    void onNeedsToUpdateColors();

    /**
     * Called when the renderer has finished loading textures
     * and painting is ready to begin.
     */
    void onReadyToPaint();

    /**
     * Called when the user paints a pixel.
     *
     * @param selectedGroup  The currently selected color the user is using.
     * @param correctGroup   The blueprint's required color for that pixel.
     * @param shouldCapture  True if we capture a frame for timelapse.
     * @param shouldVibrate  True if this paint stroke should trigger haptic feedback.
     */
    void onUserPaintedRegion(int selectedGroup,
                             int correctGroup,
                             boolean shouldCapture,
                             boolean shouldVibrate);
}

