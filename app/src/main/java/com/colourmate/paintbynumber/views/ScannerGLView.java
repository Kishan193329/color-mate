package com.colourmate.paintbynumber.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.colourmate.paintbynumber.util.GLContextFactory;
import com.colourmate.paintbynumber.util.RotationGestureDetector;
import com.sumoing.recolor.scanner.Scanner;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A GLSurfaceView for the image scanner feature. It handles rendering, touch events for panning,
 * zooming, and rotation of the image being scanned.
 */
public class ScannerGLView extends GLSurfaceView implements RotationGestureDetector.OnRotationGestureListener {

    public static final String TAG = "ScannerGLView";

    private float angle;
    private Bitmap bitmapToScan = null;
    private float lastX;
    private float lastY;
    private boolean isFromCamera;
    private boolean isInScale;
    private boolean isEraseMode = false; // Track current erase/paint mode
    private ScannerGLViewListener listener = null;
    private float previousAngle;
    private Scanner scanner = null;
    private final RotationGestureDetector rotationDetector;
    private final ScaleGestureDetector scaleDetector;

    /**
     * Listener for events from the ScannerGLView.
     */
    public interface ScannerGLViewListener {
        /**
         * Called when the scanner needs to move to the next stage of processing.
         */
        void gotoNextStage();

        /**
         * Called to update the UI with the scanner's busy state.
         *
         * @param isScanning    True if the scanner is busy with a scanning step.
         * @param isProcessing  True if the scanner is busy with a processing step.
         */
        void onScanningStateChanged(boolean isScanning, boolean isProcessing);

        /**
         * Called when the scanning process fails.
         */
        void onScanningFailed();

        /**
         * Called when the scanning process succeeds.
         *
         * @param overlayImage The resulting overlay image from the scanner.
         * @param indexImage   The resulting index image from the scanner.
         */
        void onScanningSuccess(Bitmap overlayImage, Bitmap indexImage);

        /**
         * Called to update the UI with the current undo state.
         *
         * @param canUndo True if an undo operation is available.
         */
        void onUndoStateUpdated(boolean canUndo);
    }

    public ScannerGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        GLContextFactory.sharedContext = EGL10.EGL_NO_CONTEXT;
        setPreserveEGLContextOnPause(true);
        initialize(context, true, 0, 0);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        rotationDetector = new RotationGestureDetector(this);
    }

    private void initialize(Context context, boolean translucent, int depth, int stencil) {
        setPreserveEGLContextOnPause(true);
        if (translucent) {
            getHolder().setFormat(-3); // PixelFormat.TRANSLUCENT
        }
        GLContextFactory.setupContext(this, translucent, depth, stencil);
        setRenderer(new Renderer());
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    /**
     * Starts the scanning process for a given bitmap.
     *
     * @param bitmap       The bitmap to be scanned.
     * @param fromCamera   True if the bitmap is from the camera, false otherwise.
     * @param viewListener The listener for scanner events.
     */
    public void startScanning(Bitmap bitmap, boolean fromCamera, ScannerGLViewListener viewListener) {
        if (this.bitmapToScan == null && this.scanner == null) {
            this.bitmapToScan = bitmap;
            this.isFromCamera = fromCamera;
            this.listener = viewListener;
            this.isEraseMode = false; // Reset erase mode on new scan
            requestRender();
        } else {
            Log.w(TAG, "Scanner busy, resetting before starting new scan");
            queueEvent(() -> {
                releaseScannerResources();
                this.bitmapToScan = bitmap;
                this.isFromCamera = fromCamera;
                this.listener = viewListener;
                this.isEraseMode = false;
                requestRender();
            });
        }
    }

    /**
     * Resets the scanner state.
     */
    public void resetScanner() {
        isEraseMode = false;
        queueEvent(this::releaseScannerResources);
    }

    /**
     * To be called when scanning is complete to retrieve the resulting images.
     */
    public void onScanningDone() {
        queueEvent(() -> {
            if (scanner == null) return;
            try {
                Bitmap overlayImage = scanner.getOverlayImage();
                Bitmap indexImage = scanner.getIndexImage();
                releaseScannerResources();
                if (overlayImage != null && indexImage != null && listener != null) {
                    listener.onScanningSuccess(overlayImage, indexImage);
                } else {
                    if (overlayImage != null && !overlayImage.isRecycled()) overlayImage.recycle();
                    if (indexImage != null && !indexImage.isRecycled()) indexImage.recycle();
                    if (listener != null) {
                        listener.onScanningFailed();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onScanningDone", e);
                releaseScannerResources();
                if (listener != null) {
                    listener.onScanningFailed();
                }
            }
        });
        requestRender();
    }

    /**
     * Accepts the current crop and moves to the next stage.
     */
    public void acceptCrop() {
        proceedToNextStage();
    }

    /**
     * Toggles the eraser mode.
     * Native scanner stages:
     *   3 = crop mode
     *   4 = erase mode (brush erases background)
     *   5 = paint mode (brush restores/paints back)
     *
     * @param isEnabled True to enable eraser mode (stage 4), false for paint mode (stage 5).
     */
    public void toggleEraser(boolean isEnabled) {
        isEraseMode = isEnabled;
        if (isEnabled) {
            setMode(4); // Erase mode in native scanner
        } else {
            setMode(5); // Paint-back mode in native scanner (NOT 3, which is crop)
        }
    }

    /**
     * Sets the type of scanner to be used.
     * @param type The scanner type identifier.
     */
    public void setScannerType(final int type) {
        queueEvent(() -> {
            if (scanner != null) {
                scanner.setScannerType(type);
            }
        });
        requestRender();
    }

    /**
     * Undoes the last operation.
     */
    public void undo() {
        queueEvent(() -> {
            if (scanner != null && listener != null) {
                listener.onUndoStateUpdated(scanner.undo());
                requestRender();
            }
        });
        requestRender();
    }

    private void setMode(final int mode) {
        queueEvent(() -> {
            if (scanner != null) {
                scanner.setMode(mode);
            }
        });
        requestRender();
    }

    /**
     * Checks if the scanner is currently in line drawing mode.
     * @return True if in line drawing mode, false otherwise.
     */
    public boolean isLineDrawing() {
        try {
            if (scanner == null) {
                return false;
            }
            return scanner.isLineDrawing();
        } catch (Exception e) {
            Log.e(TAG, "Error checking isLineDrawing", e);
            return false;
        }
    }

    private void proceedToNextStage() {
        queueEvent(() -> {
            if (scanner == null) return;
            try {
                int nextStage = scanner.nextStage();
                if (nextStage == 2) {
                    Log.d(TAG, "Scanning failed at nextStage: " + nextStage);
                    releaseScannerResources();
                    if (listener != null) {
                        listener.onScanningFailed();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in proceedToNextStage", e);
                releaseScannerResources();
                if (listener != null) {
                    listener.onScanningFailed();
                }
            }
        });
        requestRender();
    }

    /**
     * Releases resources used by the scanner and bitmap.
     */
    private synchronized void releaseScannerResources() {
        if (bitmapToScan != null && !bitmapToScan.isRecycled()) {
            bitmapToScan.recycle();
        }
        bitmapToScan = null;
        if (scanner != null) {
            try {
                Scanner.scannerDealloc();
            } catch (Exception e) {
                Log.e(TAG, "Error deallocating scanner", e);
            }
            scanner = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scanner == null) {
            return false;
        }

        try {
            rotationDetector.onTouchEvent(event);
            angle = rotationDetector.getAngleRadians();
            scaleDetector.onTouchEvent(event);
            previousAngle = angle;

            if (scaleDetector.isInProgress() || event.getActionIndex() != 0) {
                return true;
            }

            final float x = event.getX(0);
            final float y = event.getY(0);
            final boolean eraseMode = isEraseMode; // Capture current state for lambda

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = x;
                    lastY = y;
                    queueEvent(() -> {
                        if (scanner != null) {
                            scanner.panEvent(getWidth(), getHeight(), x, y, 1, eraseMode);
                        }
                    });
                    requestRender();
                    break;
                case MotionEvent.ACTION_UP:
                    isInScale = false;
                    queueEvent(() -> {
                        if (scanner == null) return;
                        scanner.panEvent(getWidth(), getHeight(), x, y, 3, eraseMode);
                        if (listener != null) {
                            listener.onUndoStateUpdated(scanner.canUndo());
                        }
                    });
                    requestRender();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!isInScale) {
                        queueEvent(() -> {
                            if (scanner != null) {
                                scanner.panEvent(getWidth(), getHeight(), x, y, 2, eraseMode);
                            }
                        });
                    }
                    lastX = x;
                    lastY = y;
                    requestRender();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    isInScale = false;
                    queueEvent(() -> {
                        if (scanner != null) {
                            scanner.panEvent(getWidth(), getHeight(), x, y, 0, eraseMode);
                        }
                    });
                    requestRender();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onTouchEvent", e);
        }
        return true;
    }

    @Override
    public void onRotation(RotationGestureDetector rotationGestureDetector) {
        // This is required by the interface but the logic is handled in onTouchEvent
    }

    /**
     * The renderer for the GLSurfaceView.
     */
    class Renderer implements GLSurfaceView.Renderer {
        @Override
        public void onDrawFrame(GL10 gl) {
            try {
                gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
                gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
                gl.glViewport(0, 0, getWidth(), getHeight());
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

                if (scanner == null && bitmapToScan != null) {
                    scanner = new Scanner();
                    boolean success = scanner.startProcessing(bitmapToScan, isFromCamera);
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

                    if (bitmapToScan != null && !bitmapToScan.isRecycled()) {
                        bitmapToScan.recycle();
                    }
                    bitmapToScan = null;

                    if (!success) {
                        Log.d(TAG, "Scanning failed during startProcessing");
                        releaseScannerResources();
                        if (listener != null) {
                            listener.onScanningFailed();
                        }
                    }
                    requestRender();
                } else if (scanner != null) {
                    int step = scanner.step((float) getWidth(), (float) getHeight());
                    boolean isScanning = (step & 1) != 0;
                    boolean isProcessing = (step & 2) != 0;
                    boolean needsRender = (step & 4) != 0;
                    boolean isStageComplete = (step & 8) != 0;

                    if (listener != null) {
                        listener.onScanningStateChanged(isScanning, isProcessing);
                    }

                    if (isStageComplete) {
                        if (listener != null) {
                            listener.gotoNextStage();
                        }
                    }
                    if (needsRender) {
                        requestRender();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onDrawFrame", e);
            }
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.d(TAG, "onSurfaceCreated");
            Scanner.scannerDealloc();
            scanner = null;
            gl.glDisable(GL10.GL_DITHER);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.d(TAG, "onSurfaceChanged: " + width + "x" + height);
        }
    }

    /**
     * Gesture listener for scaling operations.
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            final float focusX = detector.getFocusX();
            final float focusY = detector.getFocusY();
            final float scaleFactor = detector.getScaleFactor();
            final float angleDelta = previousAngle - angle;

            queueEvent(() -> {
                if (scanner == null) return;
                scanner.pinchEvent(getWidth(), getHeight(), focusX, focusY, scaleFactor, 2);
                scanner.panEvent(getWidth(), getHeight(), focusX, focusY, 2, false);
                scanner.rotateEvent(focusX, focusY, angleDelta);
            });
            requestRender();
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            isInScale = true;
            previousAngle = angle;
            final float focusX = detector.getFocusX();
            final float focusY = detector.getFocusY();
            final float scaleFactor = detector.getScaleFactor();
            final float angleDelta = previousAngle - angle;

            queueEvent(() -> {
                if (scanner == null) return;
                scanner.pinchEvent(getWidth(), getHeight(), focusX, focusY, scaleFactor, 1);
                scanner.panEvent(getWidth(), getHeight(), focusX, focusY, 1, false);
                scanner.rotateEvent(focusX, focusY, angleDelta);
            });
            requestRender();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            isInScale = false;
            final float focusX = detector.getFocusX();
            final float focusY = detector.getFocusY();
            final float scaleFactor = detector.getScaleFactor();

            queueEvent(() -> {
                if (scanner == null) return;
                scanner.pinchEvent(getWidth(), getHeight(), focusX, focusY, scaleFactor, 0);
                scanner.panEvent(getWidth(), getHeight(), focusX, focusY, 0, false);
            });
            requestRender();
            super.onScaleEnd(detector);
        }
    }
}
