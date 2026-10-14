package com.colourmate.paintbynumber.util;

import android.view.MotionEvent;

/**
 * Detects rotation gestures using two touch pointers.
 */
public class RotationGestureDetector {

    private int pointerId1 = -1;
    private int pointerId2 = -1;

    private float startX1, startY1;
    private float startX2, startY2;

    private float angle; // Current rotation angle in degrees
    private final OnRotationGestureListener listener;

    /**
     * Interface for receiving rotation events.
     */
    public interface OnRotationGestureListener {
        void onRotation(RotationGestureDetector detector);
    }

    public RotationGestureDetector(OnRotationGestureListener listener) {
        this.listener = listener;
    }

    /**
     * Returns the current rotation angle in degrees.
     */
    public float getAngle() {
        return angle;
    }

    /**
     * Returns the current rotation angle in radians.
     */
    public float getAngleRadians() {
        return (float) Math.toRadians(angle);
    }

    /**
     * Handles touch events to detect rotation gestures.
     *
     * @param event MotionEvent from the view
     * @return true if the event was handled
     */
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                pointerId1 = event.getPointerId(event.getActionIndex());
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                pointerId2 = event.getPointerId(event.getActionIndex());
                startX1 = event.getX(event.findPointerIndex(pointerId1));
                startY1 = event.getY(event.findPointerIndex(pointerId1));
                startX2 = event.getX(event.findPointerIndex(pointerId2));
                startY2 = event.getY(event.findPointerIndex(pointerId2));
                return true;

            case MotionEvent.ACTION_MOVE:
                if (pointerId1 == -1 || pointerId2 == -1) {
                    return true;
                }

                float currentX1 = event.getX(event.findPointerIndex(pointerId1));
                float currentY1 = event.getY(event.findPointerIndex(pointerId1));
                float currentX2 = event.getX(event.findPointerIndex(pointerId2));
                float currentY2 = event.getY(event.findPointerIndex(pointerId2));

                angle = calculateAngle(startX1, startY1, startX2, startY2, currentX1, currentY1, currentX2, currentY2);

                if (listener != null) {
                    listener.onRotation(this);
                }
                return true;

            case MotionEvent.ACTION_UP:
                pointerId1 = -1;
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                pointerId2 = -1;
                return true;

            case MotionEvent.ACTION_CANCEL:
                pointerId1 = -1;
                pointerId2 = -1;
                return true;

            default:
                return true;
        }
    }

    /**
     * Calculates the rotation angle between two lines formed by the start points and current points.
     *
     * @param startX1 First pointer start X
     * @param startY1 First pointer start Y
     * @param startX2 Second pointer start X
     * @param startY2 Second pointer start Y
     * @param currX1  First pointer current X
     * @param currY1  First pointer current Y
     * @param currX2  Second pointer current X
     * @param currY2  Second pointer current Y
     * @return rotation angle in degrees, in the range [-180, 180]
     */
    private float calculateAngle(float startX1, float startY1, float startX2, float startY2,
                                 float currX1, float currY1, float currX2, float currY2) {
        double angleStart = Math.atan2(startY1 - startY2, startX1 - startX2);
        double angleCurrent = Math.atan2(currY1 - currY2, currX1 - currX2);
        float angle = (float) Math.toDegrees(angleCurrent - angleStart) % 360f;

        if (angle < -180f) {
            angle += 360f;
        } else if (angle > 180f) {
            angle -= 360f;
        }

        return angle;
    }
}
