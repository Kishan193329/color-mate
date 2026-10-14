package com.colourmate.paintbynumber.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;

import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.util.Constants;
import com.colourmate.paintbynumber.util.GLContextFactory;
import com.sumoing.recolor.editor.Editor;

import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A {@link GLSurfaceView} for the main drawing canvas.
 * It handles rendering the artwork, user input for coloring and navigation,
 * and saving the drawing state.
 */
public class DrawingView extends GLSurfaceView {
    public static final String TAG = "DrawingView";

    private long drawingViewPtr;
    private long gluiPtr;

    private boolean isSavingState;
    private boolean isDrawingDataSet;
    private boolean isDrawingDataPending;

    private float currentScale;
    private final ScaleGestureDetector scaleDetector;

    public DrawingView(Context context) {
        this(context, null);
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "DrawingView created");
        GLContextFactory.sharedContext = EGL10.EGL_NO_CONTEXT;
        setPreserveEGLContextOnPause(true);
        this.scaleDetector = new ScaleGestureDetector(context, new PinchAndScaleListener());
        init(true, 16, 0);
        Log.d(TAG, "init done");
    }

    private void init(boolean translucent, int depth, int stencil) {
        if (translucent) {
            getHolder().setFormat(PixelFormat.TRANSLUCENT);
        }
        GLContextFactory.setupContext(this, translucent, depth, stencil);
        setRenderer(new DrawingRenderer());
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    /**
     * Releases the native resources associated with the current drawing.
     */
    private void releaseDrawing() {
        this.isDrawingDataPending = false;
        this.isDrawingDataSet = false;
        if (Editor.isNativeLoaded()) {
            Editor.gluiRelease();
        }
    }

    /**
     * Signals that a new drawing needs to be loaded into the view on the GL thread.
     */
    public void requestNewDrawing() {
        this.isDrawingDataSet = false;
        this.isDrawingDataPending = true;
        requestRender();
    }

    /**
     * Saves the current progress of the drawing. Does nothing if not complete.
     */
    public void saveProgress() {
        if (isDrawingDataSet()) {
            Editor.gluiSaveProgress(null);
        }
    }


    /**
     * Saves the final artwork and thumbnail to disk.
     *
     * @param releaseOnSave If true, native resources will be released after saving.
     */
    public void save(final boolean releaseOnSave) {
        Log.d(TAG, "save called");
        if (!isDrawingDataSet() || !Editor.gluiShouldSave()) {
            return;
        }

        try {
            Library.getInstance().saveColoredPicture();

            final CountDownLatch latch = new CountDownLatch(1);
            queueEvent(() -> {
                try {
                    isSavingState = true;
                    Bitmap thumbnail = Editor.gluiCreateThumb(Constants.THUMB_WIDTH);
                    if (thumbnail != null) {
                        try (FileOutputStream fos = new FileOutputStream(Library.mCurrentDrawing.mItem.getColoredPicture().getThumbFile())) {
                            thumbnail.compress(Bitmap.CompressFormat.WEBP, 65, fos);
                        }
                        thumbnail.recycle();
                    }
                    Editor.gluiSaveProgress(Library.mCurrentDrawing.mItem.getColoredPicture().getDocumentFile().getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save drawing state on GL thread.", e);
                } finally {
                    if (releaseOnSave) {
                        releaseDrawing();
                    } else {
                        isSavingState = false;
                    }
                    latch.countDown();
                }
            });

            latch.await();

        } catch (Exception e) {
            Log.e(TAG, "Save failed", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Checks if the drawing data has been set and is ready for interaction.
     */
    public boolean isDrawingDataSet() {
        return this.isDrawingDataSet;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isDrawingDataSet()) {
            Log.d(TAG, "Touch ignored, drawing not loaded yet");
            return true;
        }

        scaleDetector.onTouchEvent(event);
        if (scaleDetector.isInProgress()) {
            return true;
        }

        final float x = event.getX();
        final float y = event.getY();
        final int action = event.getAction();
        final int actionIndex = event.getActionIndex();

        long targetPtr = Editor.gluiHitCheck(gluiPtr, x, y) ? gluiPtr : drawingViewPtr;
        queueTouchEvent(targetPtr, action, actionIndex, x, y);

        return true;
    }

    private void queueTouchEvent(final long targetPtr, final int action, final int actionIndex, final float x, final float y) {
        queueEvent(() -> {
            int nativeAction = -1;
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    nativeAction = 0;
                    break; // Touch Begin
                case MotionEvent.ACTION_MOVE:
                    nativeAction = 1;
                    break; // Touch Move
                case MotionEvent.ACTION_UP:
                    nativeAction = 2;
                    break; // Touch End
                case MotionEvent.ACTION_CANCEL:
                    nativeAction = 3;
                    break; // Touch Cancel
            }

            if (nativeAction != -1) {
                Editor.gluiTouchEvent(targetPtr, nativeAction, actionIndex, x, y);
            }
        });
        requestRender();
    }

    /**
     * The {@link Renderer} for the DrawingView.
     */
    private class DrawingRenderer extends GLES20 implements Renderer {

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.d(TAG, "onSurfaceCreated");
            glDisable(GL_DITHER);
            gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.d(TAG, "onSurfaceChanged: " + width + "x" + height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (!Editor.isNativeLoaded()) {
                // Native libraries failed to load; clear screen and skip native calls
                gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
                gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                return;
            }

            if (isSavingState) {
                return;
            }

            if (drawingViewPtr != 0 && gluiPtr != 0 && isDrawingDataPending && !isDrawingDataSet) {
                setDrawingOnGLThread();
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            glViewport(0, 0, getWidth(), getHeight());
            gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            if (drawingViewPtr == 0 || gluiPtr == 0) {
                gluiPtr = Editor.gluiInit(getWidth(), getHeight());
                drawingViewPtr = Editor.drawingViewInit(getWidth(), getHeight());
                requestRender();
            } else {
                // The bitwise OR ensures both draw methods are called.
                boolean needsRender = (Editor.gluiDraw(drawingViewPtr, getWidth(), getHeight()) | Editor.gluiDraw(gluiPtr, getWidth(), getHeight())) == 1;
                if (needsRender) {
                    requestRender();
                }
            }
        }

        private void setDrawingOnGLThread() {
            String coloredDocumentPath = (Library.mCurrentDrawing.mItem.getColoredPicture() == null || Library.getInstance().isNewCopy)
                    ? null
                    : Library.mCurrentDrawing.mItem.getColoredPicture().getDocumentFile().getAbsolutePath();

            Editor.setDrawing(coloredDocumentPath,
                    Library.mCurrentDrawing.getArtLayer(),
                    Library.mCurrentDrawing.mIndex,
                    Library.mCurrentDrawing.mIndex2,
                    Library.mCurrentDrawing.getScanLayer(),
                    Library.mCurrentDrawing.mNormalmap,
                    Library.mCurrentDrawing.mControlmap,
                    Library.mCurrentDrawing.mEnvmap,
                    Library.mCurrentDrawing.mSkybackground,
                    Library.mCurrentDrawing.mTobj,
                    Library.mCurrentDrawing.mItem.config3D);
            isDrawingDataPending = false;
            isDrawingDataSet = true;
        }
    }

    /**
     * Gesture listener for pinch-to-zoom gestures.
     */
    private class PinchAndScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private static final int GESTURE_START = 4096;
        private static final int GESTURE_CHANGE = 4097;
        private static final int GESTURE_END = 4098;

        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            final float focusX = detector.getFocusX();
            final float focusY = detector.getFocusY();
            currentScale *= detector.getScaleFactor();
            queueEvent(() -> Editor.gluiGestureEvent(drawingViewPtr, GESTURE_CHANGE, focusX, focusY, 0.0f, currentScale));
            requestRender();
            return true;
        }

        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            final float focusX = detector.getFocusX();
            final float focusY = detector.getFocusY();
            currentScale = 1.0f;
            queueEvent(() -> {
                Editor.gluiTouchEvent(gluiPtr, 3, 0, 0.0f, 0.0f);
                Editor.gluiTouchEvent(drawingViewPtr, 3, 0, 0.0f, 0.0f);
                Editor.gluiGestureEvent(drawingViewPtr, GESTURE_START, focusX, focusY, 0.0f, currentScale);
            });
            requestRender();
            return true;
        }

        @Override
        public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
            final float focusX = detector.getFocusX();
            final float focusY = detector.getFocusY();
            queueEvent(() -> Editor.gluiGestureEvent(drawingViewPtr, GESTURE_END, focusX, focusY, 0.0f, currentScale));
            requestRender();
            super.onScaleEnd(detector);
        }
    }
}
