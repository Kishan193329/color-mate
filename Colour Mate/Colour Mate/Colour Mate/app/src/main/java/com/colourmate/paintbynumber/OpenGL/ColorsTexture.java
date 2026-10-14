package com.colourmate.paintbynumber.OpenGL;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.util.LinkedHashSet;

/**
 * Represents a texture containing colors for OpenGL rendering.
 */
public class ColorsTexture extends Texture {

    public ColorsTexture(int textureId, int width, int height, int texWidth, int texHeight) {
        super(textureId, width, height, texWidth, texHeight);
    }

    /**
     * Loads a ColorsTexture from a set of colors.
     *
     * @param colors A LinkedHashSet of color integers.
     * @return A ColorsTexture instance or null if texture generation fails.
     */
    public static ColorsTexture loadColorsTexture(LinkedHashSet<Integer> colors) {
        if (colors == null || colors.isEmpty()) return null;

        int[] texId = new int[1];
        GLES20.glGenTextures(1, texId, 0);
        if (texId[0] == 0) return null;

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

        final int size = 128;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[size * size];

        // Fill pixel array with colors
        Integer[] colorArray = colors.toArray(new Integer[0]);
        for (int i = 0; i < Math.min(colorArray.length, pixels.length); i++) {
            pixels[i] = colorArray[i];
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }

        return new ColorsTexture(texId[0], size, size, size, size);
    }
}
