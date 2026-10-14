package com.colourmate.paintbynumber.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.util.AssetHelper;
import com.colourmate.paintbynumber.util.Constants;
import com.colourmate.paintbynumber.util.Fbo;
import com.colourmate.paintbynumber.util.Fbo3d;
import com.colourmate.paintbynumber.util.GLContextFactory;
import com.google.gson.JsonObject;
import com.sumoing.recolor.editor.Editor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A {@link GLSurfaceView} responsible for rendering and exporting the final artwork.
 * It handles both 2D and 3D drawing effects, touch input for manipulation, and final image generation.
 */
public class ExportView extends GLSurfaceView implements Constants {
    public static final String TAG = "ExportView";

    private JsonObject pendingLook;
    private JsonObject pendingLut;
    private JsonObject pendingOutline;

    private boolean is3D;
    private float lastX;
    private float lastY;
    private long startTime;

    private final ExportRenderer glRenderer;
    private final ScaleGestureDetector scaleDetector;


    public ExportView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.scaleDetector = new ScaleGestureDetector(context, new PinchAndScaleListener());
        this.glRenderer = new ExportRenderer();
        init(context, true, 16, 0);
    }

    private void init(Context context, boolean translucent, int depth, int stencil) {
        setPreserveEGLContextOnPause(true);
        if (translucent) {
            getHolder().setFormat(-3); // PixelFormat.TRANSLUCENT
        }

        GLContextFactory.setupContext(this, true, depth, stencil);
        this.is3D = Library.mCurrentDrawing.mItem.is3DModel();
        setRenderer(this.glRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        requestRender();
    }

    /**
     * Shuffles the current visual effect, if applicable (for 2D effects).
     */
    public void shuffleEffect() {
        if (!this.is3D) {
            queueEvent(() -> {
                Editor.effectLayerShuffle();
                requestRender();
            });
            requestRender();
        }
    }

    /**
     * Sets a new "look" to be applied on the next render pass.
     */
    public void setLook(JsonObject look) {
        this.pendingLook = look;
        requestRender();
    }

    /**
     * Sets a new LUT (Look-Up Table) to be applied on the next render pass.
     */
    public void setLut(JsonObject lut) {
        this.pendingLut = lut;
        requestRender();
    }

    /**
     * Sets a new outline color to be applied on the next render pass.
     */
    public void setOutline(JsonObject outline) {
        this.pendingOutline = outline;
        requestRender();
    }

    /**
     * Applies the pending LUT to the 2D effect layer.
     */
    private void applyLut() {
        float power = pendingLut.has("default_power") ? pendingLut.get("default_power").getAsFloat() : 1.0f;
        if (pendingLut.has("path")) {
            Editor.effectLayerSetLut(power, pendingLut.get("path").getAsString());
        }
        this.pendingLut = null;
    }

    /**
     * Applies the pending outline color to the 2D effect layer.
     */
    private void applyOutline() {
        if (pendingOutline.has("color")) {
            String colorString = pendingOutline.get("color").getAsString();
            if (!colorString.startsWith("#")) {
                colorString = "#" + colorString;
            }
            Editor.effectLayerSetOutline(Color.parseColor(colorString));
        }
        this.pendingOutline = null;
    }

    /**
     * Applies the pending look to the drawing, for either 2D or 3D effects.
     */
    private void applyLook() {
        Log.d(TAG, "Applying new look...");
        if (is3D) {
            applyLook3D();
        } else {
            applyLook2D();
        }
        this.pendingLook = null;
        this.startTime = System.currentTimeMillis();
    }

    private void applyLook2D() {
        Editor.effectLayerUnload();
        if (pendingLook.has("layersdef")) {
            Editor.effectLayerLoad(GlobalApplication.getAppContext().getAssets(), pendingLook.get("layersdef").getAsString());
        }
    }

    private void applyLook3D() {
        String shattr = pendingLook.has("shattr") ? pendingLook.get("shattr").getAsString() : "";
        String light = pendingLook.has("light") ? pendingLook.get("light").getAsString() : "";
        String camera = pendingLook.has("camera") ? pendingLook.get("camera").getAsString() : "";
        String type = pendingLook.has("type") ? pendingLook.get("type").getAsString() : "image";
        String vsh = pendingLook.has("vsh") ? pendingLook.get("vsh").getAsString() : "";
        String fsh = pendingLook.has("fsh") ? pendingLook.get("fsh").getAsString() : "";
        float projection = pendingLook.has("projection") ? pendingLook.get("projection").getAsFloat() : -1234.0f;

        Editor.exportLoadEffect(GlobalApplication.getAppContext().getAssets(), getWidth(), getHeight(),
                camera, light, type, Library.mCurrentDrawing.mItem.config3D.default_rotation_angle,
                Library.mCurrentDrawing.mItem.config3D.default_scaling, projection, shattr, vsh, fsh);
    }

    /**
     * Renders the final high-resolution image to a bitmap, with a watermark.
     *
     * @return The final rendered bitmap, or null if an error occurs.
     */
    public Bitmap renderFinal() {
        try {
            final int finalSize = 2048;
            final ByteBuffer buffer = ByteBuffer.allocateDirect(finalSize * finalSize * 4);
            buffer.order(ByteOrder.nativeOrder());
            buffer.position(0);

            final CountDownLatch latch = new CountDownLatch(1);
            queueEvent(() -> {
                glRenderer.renderFinalFrame(buffer, finalSize);
                latch.countDown();
            });

            latch.await();

            Bitmap bitmap = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            renderWatermark(bitmap);
            return bitmap;

        } catch (OutOfMemoryError | InterruptedException e) {
            Log.e(TAG, "Failed to render final image", e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void renderWatermark(Bitmap bitmap) {
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (scaleDetector.isInProgress()) {
            return true; // Scale gesture is handling this touch event
        }

        final float x = event.getX();
        final float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (is3D) {
                    Editor.exportSingleTouchpointEvent(0, 0.0f, 0.0f);
                    requestRender();
                }
                lastX = x;
                lastY = y;
                break;

            case MotionEvent.ACTION_UP:
                if (is3D) {
                    Editor.exportSingleTouchpointEvent(2, 0.0f, 0.0f);
                    requestRender();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (!Float.isNaN(lastX) && !Float.isNaN(lastY)) {
                    float dx = x - lastX;
                    float dy = y - lastY;
                    if (is3D) {
                        Editor.exportSingleTouchpointEvent(1, dx, dy);
                    } else {
                        Editor.effectLayerPan(dx / getWidth(), dy / getHeight());
                    }
                    requestRender();
                }
                lastX = x;
                lastY = y;
                break;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    /**
     * The {@link Renderer} for the ExportView.
     */
    private class ExportRenderer extends GLES20 implements Renderer {
        private final int clearColor;

        ExportRenderer() {
            this.clearColor = ContextCompat.getColor(getContext(), R.color.finalize_background);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.d(TAG, "onSurfaceCreated");
            if (is3D) {
                Editor.exportPrepare(GlobalApplication.getAppContext().getAssets(), 0, 0);
            }
            glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.d(TAG, "onSurfaceChanged: " + width + "x" + height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            // Apply pending changes if any exist
            if (pendingLook != null) applyLook();
            if (pendingLut != null) applyLut();
            if (pendingOutline != null) applyOutline();

            // Set the clear color and clear the buffers
            float r = ((clearColor >> 16) & 0xFF) / 255.0f;
            float g = ((clearColor >> 8) & 0xFF) / 255.0f;
            float b = ((clearColor) & 0xFF) / 255.0f;
            gl.glClearColor(r, g, b, 1.0f);
            gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Set viewport and draw
            glViewport(0, 0, getWidth(), getHeight());
            if (is3D) {
                Editor.exportDraw(getWidth(), getHeight(), false);
            } else {
                float elapsedTime = ((float) (System.currentTimeMillis() - startTime) / 1000.0f) % 12.0f;
                render2D(elapsedTime, false);
            }
        }

        private void render2D(float time, boolean isFinal) {
            Editor.effectLayerRender(time, getWidth(), getHeight(), isFinal);
        }

        /**
         * Renders the final high-resolution frame into the provided ByteBuffer.
         */
        void renderFinalFrame(ByteBuffer buffer, int size) {
            if (is3D) {
                renderFinal3D(buffer, size);
            } else {
                renderFinal2D(buffer, size);
            }
        }

        private void renderFinal3D(ByteBuffer buffer, int size) {
            Fbo3d fbo = new Fbo3d(size, size);
            fbo.bind(true);
            Editor.exportDraw(size, size, true);
            GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, 0);
            fbo.release();
        }

        private void renderFinal2D(ByteBuffer buffer, int size) {
            Fbo fbo = new Fbo(size, size);
            fbo.bind(true);
            render2D(0.0f, true);
            GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            fbo.release();
        }
    }

    /**
     * Gesture listener for pinch-to-zoom and panning gestures.
     */
    private class PinchAndScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            if (is3D) {
                Editor.exportPinch(detector.getScaleFactor(), focusX, focusY);
                Editor.exportPan(focusX - lastX, focusY - lastY);
            }
            lastX = focusX;
            lastY = focusY;
            requestRender();
            return true;
        }

        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            Log.d(TAG, "onScaleBegin");
            lastX = detector.getFocusX();
            lastY = detector.getFocusY();
            return true;
        }

        @Override
        public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
            Log.d(TAG, "onScaleEnd");
            lastX = Float.NaN;
            lastY = Float.NaN;
        }
    }
}
