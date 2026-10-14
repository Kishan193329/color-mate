package com.colourmate.paintbynumber.OpenGL;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;

import java.util.LinkedHashSet;

public class PaintingView extends GLSurfaceView {
    private final GestureDetectorCompat gestureDetector;
    private final CustomGestureListener gestureListener = new CustomGestureListener();
    private RectF lastTouch;
    private final OnPaintingEventListener onPaintingEventListener;
    private final PaintingRenderer renderer;
    private final ScaleGestureDetector scaleGestureDetector;
    private boolean triggeredAutoZoom;

    public class CustomGestureListener extends GestureDetector.SimpleOnGestureListener {
        public boolean isCurrentlyLongPressing;
        public boolean isCurrentlyScrolling;

        private CustomGestureListener() {
            this.isCurrentlyScrolling = false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {

            // Do not scroll if long-press painting is active
            if (isCurrentlyLongPressing) {
                return false;
            }

            renderer.onScroll(distanceX, distanceY);

            isCurrentlyScrolling = true;

            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent event) {

            // Only handle single-finger taps
            if (event.getPointerCount() == 1) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                renderer.onTap(x, y);
            }

            return true;
        }

        public void onLongPress(@NonNull MotionEvent motionEvent) {
            this.isCurrentlyLongPressing = true;
            super.onLongPress(motionEvent);
        }
    }

    private class CustomScaleGestureListener implements ScaleGestureDetector.OnScaleGestureListener {
        public boolean onScaleBegin(@NonNull ScaleGestureDetector scaleGestureDetector) {
            return true;
        }

        public void onScaleEnd(@NonNull ScaleGestureDetector scaleGestureDetector) {
        }

        private CustomScaleGestureListener() {
        }

        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {

            // Disable scaling while long-press painting
            if (gestureListener.isCurrentlyLongPressing) {
                return false;
            }

            float scaleFactor = detector.getScaleFactor();
            int focusX = (int) detector.getFocusX();
            int focusY = (int) detector.getFocusY();

            renderer.changeScaleFactor(scaleFactor, focusX, focusY);

            return true;
        }
    }

    public PaintingView(
            Context context,
            Bitmap image,
            String imageId,
            final OnPaintingEventListener listener) {
        super(context);

        // -------------------------------
        // GLSurfaceView Configuration
        // -------------------------------
        setZOrderOnTop(false);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);

        getHolder().setFormat(PixelFormat.RGBA_8888);
        setPreserveEGLContextOnPause(true);

        this.onPaintingEventListener = listener;

        // -------------------------------
        // Renderer Setup
        // -------------------------------
        this.renderer = new PaintingRenderer(
                context,
                this,
                image,
                imageId,
                listener);
        setRenderer(renderer);

        // 0 = RENDERMODE_CONTINUOUSLY (same as original code)
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // -------------------------------
        // Gesture Detection
        // -------------------------------
        gestureDetector = new GestureDetectorCompat(context, gestureListener);

        scaleGestureDetector = new ScaleGestureDetector(
                context,
                new CustomScaleGestureListener());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            scaleGestureDetector.setQuickScaleEnabled(false);
        }

        triggeredAutoZoom = true;

        // -------------------------------
        // Touch Handling
        // -------------------------------
        setOnTouchListener((view, event) -> {

            // Trigger zoom-in animation if required
            autoZoomIfNeeded();

            lastTouch = new RectF(event.getX(), event.getY(), 0f, 0f);

            // Detect pinch gestures
            if (event.getPointerCount() >= 2) {
                scaleGestureDetector.onTouchEvent(event);
            }

            // Detect tap, long-press, scroll
            gestureDetector.onTouchEvent(event);

            // Long-press painting
            if (gestureListener.isCurrentlyLongPressing) {
                onLongPress(event);
                listener.onLongPressEnable(lastTouch);
            } else {
                listener.onLongPressDisable();
            }

            // On finger UP
            if (event.getAction() == MotionEvent.ACTION_UP) {

                listener.onLongPressDisable();

                // If user was scrolling, reset inertial scroll
                if (gestureListener.isCurrentlyScrolling) {
                    postDelayed(() -> {
                        renderer.onScroll(0f, 0f);
                        gestureListener.isCurrentlyScrolling = false;
                    }, 50);
                }

                // Notify color selector to refresh group states
                listener.onNeedsToUpdateColors();
            }

            return true;
        });
    }

    public void invalidateAutoZoom() {
        if (checkEmpty()) {
            this.triggeredAutoZoom = false;
        }
    }

    public void autoZoomIfNeeded() {

        setRenderMode(RENDERMODE_WHEN_DIRTY); // was: 1

        if (triggeredAutoZoom) {
            return; // Already executed once
        }

        triggeredAutoZoom = true;

        new Thread(() -> {

            // Smooth zoom-in until scale reaches 3.0
            while (renderer.getScreenScale() < 3f) {

                float newScale = renderer.getScreenScale() + 0.1f;
                renderer.changeScaleFactorWithoutRequestRender(newScale);

                try {
                    Thread.sleep(10); // small delay for animation smoothness
                } catch (InterruptedException ignored) {
                }
            }

            // Restore original render mode (was: 0)
            setRenderMode(RENDERMODE_CONTINUOUSLY);

        }).start();
    }

    public void autoZoomOut() {

        setRenderMode(RENDERMODE_WHEN_DIRTY); // 1

        new Thread(() -> {

            // Smooth zoom-out animation
            while (renderer.getScreenScale() > 1f) {
                float newScale = renderer.getScreenScale() - 0.2f;

                renderer.changeScaleFactorWithoutRequestRender(newScale);

                try {
                    Thread.sleep(10); // small delay for smooth animation
                } catch (InterruptedException ignored) {
                }
            }

            // Final snap to exact 1x zoom
            renderer.changeScaleFactorWithoutRequestRender(1f);

            // Restore render mode
            setRenderMode(RENDERMODE_CONTINUOUSLY); // 0

            // UI callback MUST run on main thread
            new Handler(Looper.getMainLooper()).post(
                    onPaintingEventListener::goToShare);

        }).start();
    }

    public LinkedHashSet<Integer> colorSet() {
        return this.renderer.colorSet();
    }

    public void setSelectedGroup(int selectedGroup) {
        this.renderer.setSelectedGroup(selectedGroup);
    }

    public void autoPaintGroup(int groupId) {
        this.renderer.autoPaintGroup(groupId);
    }

    public PaintingStatus getPaintingStatus() {
        return this.renderer.getPaintingStatus();
    }

    public boolean checkCompleted(int i) {
        return this.renderer.checkComplete(i);
    }

    public boolean checkCompletedAll() {
        return this.renderer.checkCompleteAll();
    }

    public boolean checkEmpty() {
        return this.renderer.checkEmpty();
    }

    public void requestExport(
            boolean includeBase,
            boolean includePaintLayer,
            boolean includeNumbers,
            OnExportFinishedListener listener) {
        renderer.requestExport(includeBase, includePaintLayer, includeNumbers, listener);
    }

    private void onLongPress(MotionEvent event) {

        // Paint at the long-press position
        renderer.onTap((int) event.getX(), (int) event.getY());

        // If user lifts finger, stop long-press state
        if (event.getAction() == MotionEvent.ACTION_UP) {
            gestureListener.isCurrentlyLongPressing = false;
        }
    }
}
