package com.colourmate.paintbynumber.OpenGL;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManager;

import com.colourmate.paintbynumber.Utils.ImageManager;
import com.colourmate.paintbynumber.Utils.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PaintingRenderer implements GLSurfaceView.Renderer {

    private final Context context;
    private int currentGroup = 0;
    private int currentPaintStrike = 0;
    private final PaintingView glView;
    private int gridVBO = -1;
    private int gridVertexCount = 0;
    private final Bitmap image;
    private final String imageId;
    private final OnPaintingEventListener listener;
    private final float[] matrixProjection = new float[16];
    private final float[] matrixR2T = new float[16];
    private final float[] matrixView = new float[16];
    private final float[] matrixViewProjection = new float[16];
    private final int[] numbersCount = new int[10];
    private final int[] numbersVBO = new int[10];
    private int paintStrikeOffsetForTimelapse;
    private final Set<Integer> paintedGroupsSet = new HashSet<>();
    private final Vector2D pan = new Vector2D(0.0f, 0.0f);
    private int quadVBO = -1;
    private int r2tFramebuffer;
    private int screenHeight;
    private float screenRatio;
    private float screenScale = 1.0f;
    private int screenWidth;
    private Shader shader;

    private Texture textureBase;
    private GroupTexture textureBlueprint;
    private ColorsTexture textureColors;
    private Texture[] textureNumbers;
    private GroupTexture texturePaint;
    private int totalPaintStrike = 0;
    private float viewWindowHalfHeight;
    private float viewWindowHalfWidth;

    public enum RenderType {
        PAINTING,
        TIMELAPSE,
        EXPORT
    }

    /**
     * Returns the next power of two >= n.
     * For example:
     * - nextPowerOfTwo(5) = 8
     * - nextPowerOfTwo(16) = 16
     * - nextPowerOfTwo(1) = 1
     */
    private static int nextPowerOfTwo(int n) {

        if (n <= 1)
            return 1;

        n--; // move to next lower boundary
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;

        return n + 1;
    }

    public static class Vector2D {
        public float x;
        public float y;

        public Vector2D(float f, float f2) {
            this.x = f;
            this.y = f2;
        }
    }

    public PaintingRenderer(
            Context context,
            PaintingView glView,
            Bitmap image,
            String imageId,
            OnPaintingEventListener listener) {

        this.context = context;
        this.glView = glView;
        this.image = image;
        this.listener = listener;
        this.imageId = imageId;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        // ----------------------------
        // OpenGL state configuration
        // ----------------------------
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glClearColor(1f, 1f, 1f, 1f);

        if (image == null) {
            return;
        }

        // ----------------------------
        // Load base texture (blueprint background)
        // ----------------------------
        Bitmap mono = Utils.monocromaticImage(image);
        textureBase = Texture.loadTexture(mono, false, false);

        // ----------------------------
        // Load or initialize progress texture (paint layer)
        // ----------------------------
        Bitmap progress = ImageManager.getInstance().getProgressImage(imageId);

        if (progress != null) {
            texturePaint = GroupTexture.loadTexture(progress, false, false);
        } else {
            Bitmap empty = Bitmap.createBitmap(
                    image.getWidth(),
                    image.getHeight(),
                    Bitmap.Config.ARGB_8888);
            texturePaint = GroupTexture.loadTexture(empty, false, false);
        }

        // ----------------------------
        // Load blueprint groups & color sets
        // ----------------------------
        textureBlueprint = GroupTexture.loadColorsFromImage(image);
        textureColors = ColorsTexture.loadColorsTexture(textureBlueprint.colorSet);

        // ----------------------------
        // Load number textures (0–9)
        // ----------------------------
        textureNumbers = new Texture[10];
        Resources res = context.getResources();
        String pkg = context.getPackageName();

        for (int i = 0; i < 10; i++) {
            int id = res.getIdentifier("texturenumber" + i, "drawable", pkg);
            Bitmap bmp = Utils.decodePixelBitmapFromResource(context, id);
            textureNumbers[i] = Texture.loadTexture(bmp, false, false);
        }

        // ----------------------------
        // Create full-screen quad VBO
        // ----------------------------
        float uMax = (float) textureBase.width / textureBase.realWidth;
        float vMax = (float) textureBase.height / textureBase.realWidth;
        quadVBO = createQuadGPUBuffer(uMax, vMax);

        // ----------------------------
        // Create FBO for export/timelapse
        // ----------------------------
        initRenderToTexture(textureBase.width, textureBase.height);

        // ----------------------------
        // Generate number coordinate buffers
        // ----------------------------
        initNumbers();

        // ----------------------------
        // Load shader program
        // ----------------------------
        shader = new Shader(context);

        // ----------------------------
        // Configure timelapse paint strike frequency
        // ----------------------------
        long totalPaintable = countTotalPaintablePixels();
        paintStrikeOffsetForTimelapse = Math.max(1, (int) (totalPaintable / 180f));

        // ----------------------------
        // Generate grid VBO
        // ----------------------------
        gridVBO = initGrid();

        // ----------------------------
        // Notify listener on UI thread
        // ----------------------------
        if (listener != null) {
            new Handler(Looper.getMainLooper())
                    .post(listener::onReadyToPaint);
        }
    }

    /**
     * Counts how many pixels in the blueprint are paintable.
     * A pixel is paintable if its group != 0.
     *
     * @return number of paintable (non-zero group) pixels
     */
    private long countTotalPaintablePixels() {

        if (textureBlueprint == null) {
            return 0;
        }

        long count = 0;
        int width = textureBlueprint.width;
        int height = textureBlueprint.height;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {

                if (textureBlueprint.getGroup(x, y) != 0) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Checks whether a specific group (color) is fully completed.
     * That means: for all pixels where blueprint == groupId,
     * the painted pixel must also equal groupId.
     *
     * @param groupId the color/group number to check
     * @return true if fully painted, false otherwise
     */
    public boolean checkComplete(int groupId) {

        if (textureBlueprint == null || texturePaint == null) {
            return false;
        }

        int width = textureBlueprint.width;
        int height = textureBlueprint.height;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {

                int blueprintGroup = textureBlueprint.getGroup(x, y);
                int paintedGroup = texturePaint.getGroup(x, y);

                // If this pixel belongs to the requested group
                // but is NOT painted correctly → incomplete
                if (blueprintGroup == groupId && paintedGroup != groupId) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks whether the entire painting is fully completed.
     * A pixel is complete if:
     * paintedGroup(x, y) == blueprintGroup(x, y)
     */
    public boolean checkCompleteAll() {

        if (textureBlueprint == null || texturePaint == null) {
            return false;
        }

        int width = textureBlueprint.width;
        int height = textureBlueprint.height;

        // Scan all pixels
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {

                int blueprint = textureBlueprint.getGroup(x, y);
                int painted = texturePaint.getGroup(x, y);

                if (blueprint != painted) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks whether the painting is completely empty
     * (i.e., no painted pixel has a non-zero group).
     */
    public boolean checkEmpty() {

        int width = textureBlueprint.width;
        int height = textureBlueprint.height;

        // Scan all pixels
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {

                // If any pixel is painted (non-zero), it's NOT empty
                if (texturePaint.getGroup(x, y) != 0) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

        screenWidth = width;
        screenHeight = height;

        // Screen aspect ratio
        screenRatio = (float) width / (float) height;

        // GLES20.glViewport(0, 0, width, height);

        // Recompute camera + projection matrices
        updateMatrices();

        // Redraw frame
        glView.requestRender();
    }

    /**
     * Creates a GPU VBO containing a fullscreen quad with UV mapping.
     *
     * @param uMax Right-side U coordinate
     * @param vMax Bottom-side V coordinate
     * @return VBO id
     */
    private static int createQuadGPUBuffer(float uMax, float vMax) {

        // Quad: 4 vertices, each = (x, y, u, v) = 4 floats
        final int FLOATS_PER_VERTEX = 4;
        final int VERTEX_COUNT = 4;
        final int BUFFER_SIZE = FLOATS_PER_VERTEX * VERTEX_COUNT * Float.BYTES;

        // Allocate buffer
        FloatBuffer buffer = ByteBuffer
                .allocateDirect(BUFFER_SIZE)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        // Fullscreen quad (triangle strip order)
        buffer.put(new float[] {
                // X, Y, U, V
                -1f, 1f, 0f, 0f, // top-left
                -1f, -1f, 0f, vMax, // bottom-left
                1f, 1f, uMax, 0f, // top-right
                1f, -1f, uMax, vMax // bottom-right
        });

        buffer.position(0);

        // Create VBO
        int[] vbo = new int[1];
        GLES20.glGenBuffers(1, vbo, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);

        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                BUFFER_SIZE,
                buffer,
                GLES20.GL_STATIC_DRAW);

        // Unbind
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return vbo[0];
    }

    /**
     * Calculates the OpenGL point size for drawing numbers.
     * Depends on:
     * - texturePaint resolution
     * - current zoom level
     * - device screen width
     */
    private float pointSize() {

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null)
            return 1f;

        Display display = wm.getDefaultDisplay(); // still works on older devices
        Point screen = new Point();
        display.getSize(screen);

        float screenWidthPx = screen.x;

        // Original formula preserved exactly
        return (640f / texturePaint.width) * screenScale * (screenWidthPx / 1024f);
    }

    /**
     * Creates a VBO containing the grid vertex lines.
     * Returns the VBO ID.
     */
    private int initGrid() {

        gridVertexCount = 0;

        // -------------------------------
        // Allocate float buffer
        // Each vertex = 2 floats (x, y)
        // Max needed = (width*2 + height*2) vertices
        // -------------------------------
        int maxVertices = (textureBase.width * 2) + (textureBase.height * 2);
        FloatBuffer buffer = ByteBuffer
                .allocateDirect(maxVertices * 2 * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        float pixelStepX = 1f / textureBase.width;
        float pixelStepY = 1f / textureBase.height;

        // -------------------------------
        // 1. Generate vertical grid lines
        // -------------------------------
        for (int i = 1; i < textureBase.width * 2; i++) {

            int parity = i % 2; // even/odd

            float x = 1f - ((i + parity) * pixelStepX);
            float y = (parity == 0) ? -1f : 1f;

            buffer.put(x);
            buffer.put(y);

            gridVertexCount++;
        }

        // -------------------------------
        // 2. Generate horizontal grid lines
        // -------------------------------
        for (int j = 3; j < textureBase.height * 2; j++) {

            int parity = j % 2;

            float x = (parity == 0) ? -1f : 1f;
            float y = 1f - ((j - parity) * pixelStepY);

            buffer.put(x);
            buffer.put(y);

            gridVertexCount++;
        }

        buffer.position(0);

        // -------------------------------
        // Create and upload VBO
        // -------------------------------
        int[] vbo = new int[1];
        GLES20.glGenBuffers(1, vbo, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                buffer.capacity() * Float.BYTES,
                buffer,
                GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return vbo[0];
    }

    /**
     * Draws the grid lines over the canvas.
     */
    private void drawGrid() {

        final int FLOATS_PER_VERTEX = 2; // x, y

        int posAttrib = shader.getVertexPosAttrib();

        // Bind the VBO containing grid coordinates
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gridVBO);

        // Set MVP for grid rendering
        shader.setUniformMvp(matrixViewProjection);

        // Grid intensity or fade factor based on zoom
        float gridStrength = (screenScale - 1f) / 12f;
        shader.setGrid(gridStrength);

        // Enable attribute & configure pointer
        GLES20.glEnableVertexAttribArray(posAttrib);
        GLES20.glVertexAttribPointer(
                posAttrib,
                FLOATS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                0,
                0);

        // Draw lines (GL_LINES)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount);

        // Cleanup
        GLES20.glDisableVertexAttribArray(posAttrib);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void initNumbers() {

        final int DIGIT_COUNT = 10;
        final int FLOAT_SIZE = 4; // bytes per float
        final int COORDS_PER_POINT = 2; // x, y
        final int BUFFER_USAGE = GLES20.GL_STATIC_DRAW;

        for (int digit = 0; digit < DIGIT_COUNT; digit++) {

            // 1. Create VBO
            int[] vbo = new int[1];
            GLES20.glGenBuffers(1, vbo, 0);
            numbersVBO[digit] = vbo[0];

            // 2. Allocate buffer for coordinates
            int estimatedMaxSize = ((texturePaint.width * 512) * 32) / 8; // original formula kept

            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(estimatedMaxSize);
            byteBuffer.order(ByteOrder.nativeOrder());
            FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();

            numbersCount[digit] = 0;

            // Texture multipliers
            float baseTexStep = 1f / textureBase.width;
            float digitOffset = baseTexStep / 3f;
            float pixelStep = baseTexStep * 2f;

            // 3. Loop through the texture map
            for (int py = 0; py < texturePaint.height; py++) {
                for (int px = 0; px < texturePaint.width; px++) {

                    int group = textureBlueprint.getGroup(px, py);

                    int units = group % 10;
                    int tens = (group / 10) % 10;
                    int hundreds = (group / 100) % 10;

                    float offsetForTens = 0f;

                    // ====== CASE 1: Units digit placement ======
                    if ((units == digit && tens != 0) ||
                            (units == digit && hundreds != 0) ||
                            (tens == 0 && units != 0 && units == digit)) {

                        float x = ((px + 0.5f) * pixelStep) - 1f +
                                (((tens == 0 || hundreds != 0) ? 0f : digitOffset) +
                                        ((hundreds != 0) ? digitOffset * 2 : 0));

                        float y = 1f - (pixelStep * (py + 0.5f));

                        addNumberPoint(floatBuffer, digit, x, y);
                    }

                    // ====== CASE 2: Tens digit placement ======
                    if ((tens != 0 && tens == digit) ||
                            (tens == digit && hundreds != 0)) {

                        float x = ((px + 0.5f) * pixelStep) - 1f;

                        if (hundreds == 0) {
                            x -= digitOffset;
                        }

                        float y = 1f - (pixelStep * (py + 0.5f));

                        addNumberPoint(floatBuffer, digit, x, y);
                    }

                    // ====== CASE 3: Hundreds digit placement ======
                    if (hundreds != 0 && hundreds == digit) {

                        float x = (((px + 0.5f) * pixelStep) - 1f) - (digitOffset * 2f);
                        float y = 1f - (pixelStep * (py + 0.5f));

                        addNumberPoint(floatBuffer, digit, x, y);
                    }
                }
            }

            // 4. Upload buffer to GPU
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);

            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    floatBuffer.capacity() * FLOAT_SIZE,
                    floatBuffer,
                    BUFFER_USAGE);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    /** Helper to append data into the FloatBuffer */
    private void addNumberPoint(FloatBuffer buffer, int digit, float x, float y) {
        int index = numbersCount[digit] * 2;
        buffer.put(index, x);
        buffer.put(index + 1, y);
        numbersCount[digit]++;
    }

    private void drawNumbers() {

        final int DIGIT_COUNT = 10;
        final int FLOATS_PER_VERTEX = 2; // x, y
        final int DATA_TYPE = GLES20.GL_FLOAT;
        final int STRIDE = 0;

        for (int digit = 0; digit < DIGIT_COUNT; digit++) {

            int vboId = numbersVBO[digit];
            int vertexCount = numbersCount[digit];

            if (vboId == 0 || vertexCount == 0) {
                continue; // skip empty buffers
            }

            // Bind the VBO
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);

            // MVP matrix for point rendering
            shader.setUniformMvpPoint(matrixViewProjection);

            // Bind number texture for this digit
            shader.setNumbersTexture(textureNumbers[digit], pointSize());

            int vertexAttrib = shader.getVertexPosAttrib();

            // Enable vertex position attribute
            GLES20.glEnableVertexAttribArray(vertexAttrib);

            // Tell OpenGL how to read data from VBO
            GLES20.glVertexAttribPointer(
                    vertexAttrib,
                    FLOATS_PER_VERTEX,
                    DATA_TYPE,
                    false,
                    STRIDE,
                    0);

            // Draw points
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, vertexCount);

            // Cleanup
            GLES20.glDisableVertexAttribArray(vertexAttrib);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    private static float[] unproject(
            int screenX,
            int screenY,
            int viewportW,
            int viewportH,
            float[] modelMatrix,
            float[] projMatrix) {
        float[] output = new float[4];
        int[] viewport = { 0, 0, viewportW, viewportH };

        // depth = 0 → near plane
        GLU.gluUnProject(
                (float) screenX,
                (float) screenY,
                0f,
                modelMatrix, 0,
                projMatrix, 0,
                viewport, 0,
                output, 0);

        // Normalize by W component
        if (output[3] != 0) {
            output[0] /= output[3];
            output[1] /= output[3];
            output[2] /= output[3];
        }
        output[3] = 1f;

        return output;
    }

    public void setSelectedGroup(final int groupId) {

        // Ensure OpenGL operations run on the GL thread
        glView.queueEvent(() -> {

            // Update currently selected group
            currentGroup = groupId;

            // Compute UV coordinates for this group
            int u = Texture.UfromGroup(groupId, textureColors.width);
            int v = Texture.VfromGroup(groupId, textureColors.width);

            // Pass UV to shader
            shader.setSelectedGroup(u, v);

            // Trigger a new frame
            glView.requestRender();
        });
    }

    public void autoPaintGroup(final int groupId) {
        glView.queueEvent(() -> {
            if (texturePaint != null && textureBlueprint != null) {
                texturePaint.autoPaintGroup(textureBlueprint, groupId);

                // Track milestone if needed
                totalPaintStrike += 10;

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (listener != null) {
                        listener.onNeedsToUpdateColors();
                    }
                });
            }
            glView.requestRender();
        });
    }

    public void changeScaleFactor(final float scaleFactor, final int screenX, final int screenY) {

        glView.queueEvent(() -> {

            // World position before scaling
            Vector2D worldBefore = screenToWorld(screenX, screenY);

            // Apply zoom with clamping
            screenScale = Math.max(1.0f, Math.min(screenScale * scaleFactor, 12.0f));

            // Recalculate view boundaries & matrices
            updatePanLimits();
            updateMatrices();

            // World position after scaling
            Vector2D worldAfter = screenToWorld(screenX, screenY);

            // Adjust pan so zoom "anchors" at the touch point
            pan.x += (worldAfter.x - worldBefore.x);
            pan.y += (worldAfter.y - worldBefore.y);

            // Apply final limits and update matrices again
            updatePanLimits();
            updateMatrices();

            // Draw
            glView.requestRender();
        });
    }

    public float getScreenScale() {
        return this.screenScale;
    }

    public void changeScaleFactorWithoutRequestRender(final float targetScale) {

        glView.queueEvent(() -> {

            // Zoom pivot = screen center
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;

            // World position before zoom at pivot
            Vector2D worldBefore = screenToWorld(centerX, centerY);

            // Clamp scale between 1x and 12x
            screenScale = Math.max(1.0f, Math.min(targetScale, 12.0f));

            // Recalculate matrices & limits for new zoom
            updatePanLimits();
            updateMatrices();

            // World position after zoom
            Vector2D worldAfter = screenToWorld(centerX, centerY);

            // Adjust pan so zoom stays centered on the pivot
            pan.x += (worldAfter.x - worldBefore.x);
            pan.y += (worldAfter.y - worldBefore.y);

            // Apply limits again after pan adjustment
            updatePanLimits();
            updateMatrices();
        });
    }

    public void onTap(final int screenX, final int screenY) {

        glView.queueEvent(() -> {

            if (textureBase == null || textureBlueprint == null) {
                return;
            }

            // Convert tap → world → painting pixel
            Vector2D pixelPos = worldToPaintingPixel(screenToWorld(screenX, screenY));
            int px = (int) pixelPos.x;
            int py = (int) pixelPos.y;

            // Validate pixel bounds
            if (px < 0 || px >= textureBase.width || py < 0 || py >= textureBase.height) {
                return;
            }

            int paintGroup = texturePaint.getGroup(px, py); // current painted group
            int correctGroup = textureBlueprint.getGroup(px, py); // blueprint group

            // Only paint if allowed
            if (currentGroup == 0 || paintGroup != correctGroup) {

                // Paint the pixel
                texturePaint.paintPixel(px, py, currentGroup);

                boolean shouldCaptureTimelapseFrame = false;
                boolean shouldTriggerMilestone = false;

                // Timelapse frame logic
                if (currentGroup != 0 && currentGroup == correctGroup) {
                    currentPaintStrike++;
                    if (currentPaintStrike % paintStrikeOffsetForTimelapse == 0) {
                        shouldCaptureTimelapseFrame = true;
                    }
                }

                // Global strike count
                totalPaintStrike++;
                if (totalPaintStrike > 0 && totalPaintStrike % 20 == 0) {
                    shouldTriggerMilestone = true;
                }

                // Notify listener
                listener.onUserPaintedRegion(
                        currentGroup,
                        correctGroup,
                        shouldCaptureTimelapseFrame,
                        shouldTriggerMilestone);
            }

            // Request redraw frame
            glView.requestRender();
        });
    }

    public void onScroll(final float deltaX, final float deltaY) {

        glView.queueEvent(() -> {

            // World coordinates of the visible rectangle
            Vector2D topLeftWorld = screenToWorld((int) getScreenTopLeft().x,
                    (int) getScreenTopLeft().y);

            Vector2D bottomRightWorld = screenToWorld((int) getScreenBottomRight().x,
                    (int) getScreenBottomRight().y);

            // World width and height of viewport
            float worldWidth = bottomRightWorld.x - topLeftWorld.x;
            float worldHeight = topLeftWorld.y - bottomRightWorld.y;

            // Scroll pan offset (your original formula)
            pan.x -= (worldWidth / screenWidth) * worldWidth;
            pan.y += (worldHeight / screenHeight) * worldHeight;

            updatePanLimits();
            updateMatrices();

            glView.requestRender();
        });
    }

    private Vector2D getScreenTopLeft() {
        return new Vector2D(0.0f, 0.0f);
    }

    private Vector2D getScreenBottomRight() {
        return new Vector2D((float) this.screenWidth, (float) this.screenHeight);
    }

    private void updatePanLimits() {

        float limitX = 0f;
        float limitY = 0f;

        // Horizontal clamp logic
        if (viewWindowHalfWidth < 1f) {
            limitX = 1.01f - viewWindowHalfWidth;
        }

        // Vertical clamp logic
        if (viewWindowHalfHeight < 1.5f) {
            limitY = 1.51f - viewWindowHalfHeight;
        }

        // Clamp pan.x between [-limitX, +limitX]
        pan.x = Math.max(-limitX, Math.min(pan.x, limitX));

        // Clamp pan.y between [-limitY, +limitY]
        pan.y = Math.max(-limitY, Math.min(pan.y, limitY));
    }

    /**
     * Returns the set of all group colors used in the blueprint texture.
     */
    public LinkedHashSet<Integer> colorSet() {

        if (textureBlueprint == null) {
            return new LinkedHashSet<>();
        }

        return textureBlueprint.colorSet;
    }

    public void onDrawFrame(GL10 gl10) {
        renderToScreen();
    }

    private void drawPainting() {

        // Clear screen
        GLES20.glClearColor(1f, 1f, 1f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Draw grid lines
        drawGrid();

        // Draw numbers on cells
        drawNumbers();

        // Base layer rendering (textureBase)
        float baseAlpha = (12f - ((screenScale - 1f) * 5f)) / 12f;
        shader.setRegularTextureMode(textureBase, baseAlpha);
        drawBuffer(quadVBO);

        // Painting + blueprint + colors overlay
        shader.setPaintingMode(
                texturePaint,
                textureBlueprint,
                textureColors,
                1f,
                true,
                true);
        drawBuffer(quadVBO);

        // Ensure all commands finish
        GLES20.glFlush();
        GLES20.glFinish();
    }

    private void drawTimelapse() {

        // Clear screen
        GLES20.glClearColor(1f, 1f, 1f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Draw the base texture slightly faded (0.8 alpha)
        shader.setRegularTextureMode(textureBase, 0.8f);
        drawBuffer(quadVBO);

        // Draw painted areas (no overlay flags)
        shader.setPaintingMode(
                texturePaint,
                textureBlueprint,
                textureColors,
                1f,
                false, // disable number highlighting
                false // disable grid/highlight
        );
        drawBuffer(quadVBO);

        // Ensure GPU finishes drawing this frame
        GLES20.glFlush();
        GLES20.glFinish(); // can be removed for performance
    }

    /**
     * Renders the final export image:
     * 1. Clears screen
     * 2. Draws full base texture
     * 3. Draws painted result (no numbers, no grid)
     */
    private void drawExport() {

        // Clear the framebuffer
        GLES20.glClearColor(1f, 1f, 1f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Draw base texture at full opacity
        shader.setRegularTextureMode(textureBase, 1f);
        drawBuffer(quadVBO);

        // Draw painting (numbers disabled, highlight disabled)
        shader.setPaintingMode(
                texturePaint,
                textureBlueprint,
                textureColors,
                1f,
                true, // show correct painting logic
                false // disable highlight
        );
        drawBuffer(quadVBO);

        // Ensure GPU completes this frame (e.g., for screenshot export)
        GLES20.glFlush();
        GLES20.glFinish(); // safe for export, but slow for normal rendering
    }

    private void drawPainting(RenderType renderType) {
        switch (renderType) {
            case EXPORT:
                drawExport();
                return;
            case TIMELAPSE:
                drawTimelapse();
                return;
            default:
                drawPainting();

        }
    }

    /**
     * Renders the painting to the device screen (default framebuffer).
     */
    private void renderToScreen() {

        // Bind default framebuffer (screen)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // Set viewport to full screen
        GLES20.glViewport(0, 0, screenWidth, screenHeight);

        // Upload current MVP matrix to shader
        shader.setUniformMvp(matrixViewProjection);

        // Render the painting scene
        drawPainting(RenderType.PAINTING);
    }

    /**
     * Initializes a framebuffer object (FBO) for rendering into a texture.
     *
     * @param width  desired texture width
     * @param height desired texture height
     */
    private void initRenderToTexture(int width, int height) {

        // Allocate OpenGL object IDs
        int[] framebufferId = new int[1];
        int[] textureId = new int[1];
        int[] renderbufferId = new int[1];

        GLES20.glGenFramebuffers(1, framebufferId, 0);
        GLES20.glGenTextures(1, textureId, 0);
        GLES20.glGenRenderbuffers(1, renderbufferId, 0);

        r2tFramebuffer = framebufferId[0];
        int r2tRenderbuffer = renderbufferId[0];
        Texture r2tTexture = new Texture(textureId[0], width, height, width, height);

        // FBO textures need POT for older devices
        int potWidth = nextPowerOfTwo(width);
        int potHeight = nextPowerOfTwo(height);

        // ------------------------------
        // Configure texture
        // ------------------------------
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, r2tTexture.glId);

        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA, // internal format -> 6408
                potWidth,
                potHeight,
                0,
                GLES20.GL_RGBA, // format -> 6408
                GLES20.GL_UNSIGNED_BYTE, // type -> 5121
                null);

        // No wrapping (clamped)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // Bilinear filtering
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // ------------------------------
        // Configure renderbuffer (depth)
        // ------------------------------
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, r2tRenderbuffer);
        GLES20.glRenderbufferStorage(
                GLES20.GL_RENDERBUFFER,
                GLES20.GL_DEPTH_COMPONENT16, // 33189
                potWidth,
                potHeight);

        // ------------------------------
        // Attach texture + renderbuffer to FBO
        // ------------------------------
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, r2tFramebuffer);

        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0, // 36064
                GLES20.GL_TEXTURE_2D,
                r2tTexture.glId,
                0);

        GLES20.glFramebufferRenderbuffer(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_DEPTH_ATTACHMENT, // 36096
                GLES20.GL_RENDERBUFFER,
                r2tRenderbuffer);

        // ------------------------------
        // Check FBO completeness
        // ------------------------------
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("FBO not ready. Status: " + status);
        }

        // ------------------------------
        // Create orthographic projection for FBO rendering
        // ------------------------------
        float[] proj = new float[16];
        float[] view = new float[16];

        // Standard ortho: left, right, bottom, top, near, far
        Matrix.orthoM(proj, 0, -1f, 1f, 1f, -1f, 0.1f, 10f);

        // Camera at (0,0,1) looking at origin
        Matrix.setLookAtM(view, 0,
                0f, 0f, 1f,
                0f, 0f, 0f,
                0f, 1f, 0f);

        // Final MVP for this FBO
        Matrix.multiplyMM(matrixR2T, 0, proj, 0, view, 0);
    }

    /**
     * Renders the painting into the FBO and returns a Bitmap.
     */
    private Bitmap exportPainting(RenderType type) {

        if (textureBase == null)
            return null;

        int width = textureBase.width;
        int height = textureBase.height;

        // -------------------------------------
        // Bind FBO and setup viewport
        // -------------------------------------
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, r2tFramebuffer);
        GLES20.glViewport(0, 0, width, height);

        // Use FBO projection matrix
        shader.setUniformMvp(matrixR2T);

        // Draw the scene into texture
        drawPainting(type);

        // -------------------------------------
        // Read pixels
        // -------------------------------------
        ByteBuffer buffer = ByteBuffer
                .allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder());

        GLES20.glReadPixels(
                0, 0,
                width, height,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buffer);

        // -------------------------------------
        // Convert to ARGB and flip vertically
        // -------------------------------------
        int[] pixels = new int[width * height];

        int index = 0;
        byte[] row = new byte[width * 4];

        // OpenGL reads bottom row first → we flip while reading
        for (int y = 0; y < height; y++) {
            int flippedY = (height - 1 - y) * width; // destination row start

            buffer.position(y * width * 4);
            buffer.get(row, 0, width * 4);

            int p = 0;
            for (int x = 0; x < width; x++) {
                int r = row[p++] & 0xFF;
                int g = row[p++] & 0xFF;
                int b = row[p++] & 0xFF;
                int a = row[p++] & 0xFF;

                pixels[flippedY + x] = Color.argb(a, r, g, b);
            }
        }

        // -------------------------------------
        // Convert to bitmap
        // -------------------------------------
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    /**
     * Draws the quad VBO using the current shader.
     *
     * @param vboId The VBO ID containing interleaved vertex + UV data
     */
    private void drawBuffer(int vboId) {

        final int FLOAT_SIZE = 4;
        final int STRIDE = 4 * FLOAT_SIZE; // 16 bytes: (2 floats position + 2 floats UV)
        final int POSITION_OFFSET = 0;
        final int TEXCOORD_OFFSET = 2 * FLOAT_SIZE;

        int posAttrib = shader.getVertexPosAttrib();
        int uvAttrib = shader.getTexCoordAttrib();

        // Bind quad VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);

        // Enable attributes
        GLES20.glEnableVertexAttribArray(posAttrib);
        GLES20.glEnableVertexAttribArray(uvAttrib);

        // Position attribute (x, y)
        GLES20.glVertexAttribPointer(
                posAttrib,
                2,
                GLES20.GL_FLOAT,
                false,
                STRIDE,
                POSITION_OFFSET);

        // Texture coordinate attribute (u, v)
        GLES20.glVertexAttribPointer(
                uvAttrib,
                2,
                GLES20.GL_FLOAT,
                true, // normalized
                STRIDE,
                TEXCOORD_OFFSET);

        // Draw the quad (triangle strip)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Cleanup
        GLES20.glDisableVertexAttribArray(posAttrib);
        GLES20.glDisableVertexAttribArray(uvAttrib);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private Vector2D worldToPaintingPixel(Vector2D world) {
        float px = (textureBase.width * (world.x + 1f)) / 2f;
        float py = (textureBase.height * (1f - world.y)) / 2f; // y is inverted
        return new Vector2D(px, py);
    }

    /**
     * Converts world coordinates to pixel coords for click-testing.
     * NOTE: Uses a different (non-standard) Y-mapping than painting.
     */
    private Vector2D worldToClickPixel(Vector2D world) {
        float px = (textureBase.width * (world.x + 1f)) / 2f;
        float py = (textureBase.height * (world.y + 2f)) / 2f;
        return new Vector2D(px, py);
    }

    /**
     * Converts screen coordinates (pixels) to world coordinates (-1..+1)
     * using the current View + Projection matrices.
     *
     * @param screenX touch X position in screen pixels
     * @param screenY touch Y position in screen pixels
     */
    private Vector2D screenToWorld(int screenX, int screenY) {

        // Flip Y because OpenGL origin = bottom-left, screen origin = top-left
        int flippedY = screenHeight - screenY;

        float[] result = unproject(
                screenX,
                flippedY,
                screenWidth,
                screenHeight,
                matrixView,
                matrixProjection);

        return new Vector2D(result[0], result[1]);
    }

    /**
     * Updates projection, view, and combined MVP matrices based on:
     * - Zoom level (screenScale)
     * - Pan offset (pan.x, pan.y)
     * - Screen aspect ratio
     */
    private void updateMatrices() {

        // ------------------------------------------------------
        // 1. Compute visible window size based on zoom
        // ------------------------------------------------------
        float scale = screenScale;

        viewWindowHalfWidth = 1f / scale; // horizontal radius
        viewWindowHalfHeight = (1f / screenRatio) / scale; // vertical radius

        // ------------------------------------------------------
        // 2. Build orthographic projection matrix
        // ------------------------------------------------------
        Matrix.orthoM(
                matrixProjection, 0,
                -viewWindowHalfWidth, // left
                viewWindowHalfWidth, // right
                -viewWindowHalfHeight, // bottom
                viewWindowHalfHeight, // top
                0f, // near
                1f // far
        );

        // ------------------------------------------------------
        // 3. Build the camera view matrix
        // Camera sits at ( -pan.x, -pan.y, 1 )
        // Looking at ( -pan.x, -pan.y, 0 )
        // ------------------------------------------------------
        Matrix.setLookAtM(
                matrixView, 0,
                -pan.x, -pan.y, 1f, // eye
                -pan.x, -pan.y, 0f, // center
                0f, 1f, 0f // up
        );

        // ------------------------------------------------------
        // 4. Combine Projection * View into matrixViewProjection
        // No need to reset values manually — multiplyMM overwrites.
        // ------------------------------------------------------
        Matrix.multiplyMM(
                matrixViewProjection, 0,
                matrixProjection, 0,
                matrixView, 0);
    }

    public void requestExport(final boolean timelapse, final boolean rawData, final boolean export,
            final OnExportFinishedListener listener) {
        glView.queueEvent(() -> {
            Bitmap timelapseBmp = timelapse ? exportPainting(RenderType.TIMELAPSE) : null;
            Bitmap rawBmp = rawData ? texturePaint.getRawData() : null;
            Bitmap exportBmp = export ? exportPainting(RenderType.EXPORT) : null;

            if (listener != null)
                listener.onExportFinished(timelapseBmp, rawBmp, exportBmp);
        });
    }

    public PaintingStatus getPaintingStatus() {
        return new PaintingStatus(0, this.paintedGroupsSet.size());
    }
}
