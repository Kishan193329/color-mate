package com.colourmate.paintbynumber.OpenGL;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES20;
import android.opengl.GLUtils;

public class Texture {

    public final int glId;
    public final int width;
    public final int height;
    public final int realWidth;   // power-of-two width used in GL
    public final int realHeight;  // power-of-two height used in GL

    public Texture(int glId, int width, int height, int realWidth, int realHeight) {
        this.glId = glId;
        this.width = width;
        this.height = height;
        this.realWidth = realWidth;
        this.realHeight = realHeight;
    }

    /**
     * Next power of two (for textures on older OpenGL ES devices).
     */
    public static int nextPowerOfTwo(int value) {
        int n = value - 1;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }

    /**
     * Loads a bitmap into an OpenGL texture with optional filtering and wrapping.
     *
     * @param bitmap Source bitmap
     * @param smooth true = GL_LINEAR, false = GL_NEAREST
     * @param repeat true = GL_REPEAT, false = GL_CLAMP_TO_EDGE
     */
    public static Texture loadTexture(Bitmap bitmap, boolean smooth, boolean repeat) {

        if (bitmap == null) return null;

        // Generate texture
        int[] texIds = new int[1];
        GLES20.glGenTextures(1, texIds, 0);

        if (texIds[0] == 0) {
            return null;
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0]);

        // Filtering
        int filter = smooth ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);

        // Wrapping
        int wrap = repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);

        // Power-of-two expansion
        int potW = nextPowerOfTwo(bitmap.getWidth());
        int potH = nextPowerOfTwo(bitmap.getHeight());

        Bitmap textureBitmap;

        if (potW != bitmap.getWidth() || potH != bitmap.getHeight()) {
            // Create POT padded bitmap
            textureBitmap = Bitmap.createBitmap(potW, potH, bitmap.getConfig());
            Canvas c = new Canvas(textureBitmap);
            c.drawBitmap(bitmap, 0f, 0f, null);
        } else {
            textureBitmap = bitmap;
        }

        // Upload texture
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);

        // Recycle temporary POT bitmap
        if (textureBitmap != bitmap) {
            textureBitmap.recycle();
        }

        return new Texture(texIds[0], bitmap.getWidth(), bitmap.getHeight(), potW, potH);
    }

    /**
     * Compute U coordinate offset from group index.
     */
    public static int UfromGroup(int group, int groupsPerRow) {
        return (group % groupsPerRow) * 2;
    }

    /**
     * Compute V coordinate offset from group index.
     */
    public static int VfromGroup(int group, int groupsPerRow) {
        return (group / groupsPerRow) * 2;
    }
}

