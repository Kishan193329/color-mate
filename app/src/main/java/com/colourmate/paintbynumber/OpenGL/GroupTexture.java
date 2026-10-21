package com.colourmate.paintbynumber.OpenGL;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.util.LinkedHashSet;

/**
 * Represents a group texture with color grouping for OpenGL.
 */
public class GroupTexture extends Texture {

    public LinkedHashSet<Integer> colorSet;
    private Bitmap rawData;

    public GroupTexture(int textureId, int width, int height, int texWidth, int texHeight,
            LinkedHashSet<Integer> colorSet, Bitmap rawData) {
        super(textureId, width, height, texWidth, texHeight);
        this.colorSet = colorSet;
        this.rawData = rawData.copy(Bitmap.Config.ARGB_8888, true);
    }

    /**
     * Loads a group texture from a bitmap by creating color groups.
     */
    public static GroupTexture loadColorsFromImage(Bitmap bitmap) {
        if (bitmap == null)
            return null;

        int[] texId = new int[1];
        GLES20.glGenTextures(1, texId, 0);
        if (texId[0] == 0)
            return null;

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        int texWidth = Texture.nextPowerOfTwo(bitmap.getWidth());
        int texHeight = Texture.nextPowerOfTwo(bitmap.getHeight());

        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        LinkedHashSet<Integer> colorSet = new LinkedHashSet<>();
        colorSet.add(0);

        Bitmap textureBitmap = Bitmap.createBitmap(texWidth, texHeight, Bitmap.Config.ARGB_8888);
        int[] texturePixels = new int[texWidth * texHeight];

        Integer[] colorArray;
        int colorIndex;

        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int color = pixels[y * bitmap.getWidth() + x];
                colorSet.add(color);

                colorArray = colorSet.toArray(new Integer[0]);
                colorIndex = 0;

                // Find color index in the set
                for (int i = 0; i < colorArray.length; i++) {
                    if (colorArray[i].equals(color)) {
                        colorIndex = i;
                        break;
                    }
                }

                texturePixels[y * texWidth + x] = Color.argb(255, (colorIndex % 128) * 2, (colorIndex / 128) * 2, 0);
            }
        }

        textureBitmap.setPixels(texturePixels, 0, texWidth, 0, 0, texWidth, texHeight);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);

        return new GroupTexture(texId[0], bitmap.getWidth(), bitmap.getHeight(), 128, 128, colorSet, textureBitmap);
    }

    /**
     * Loads a texture from a bitmap with optional filtering and wrap modes.
     */
    public static GroupTexture loadTexture(Bitmap bitmap, boolean linearFilter, boolean repeatWrap) {
        if (bitmap == null)
            return null;

        int[] texId = new int[1];
        GLES20.glGenTextures(1, texId, 0);
        if (texId[0] == 0)
            return null;

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);

        int filter = linearFilter ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
        int wrap = repeatWrap ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE;

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);

        int texWidth = Texture.nextPowerOfTwo(bitmap.getWidth());
        int texHeight = Texture.nextPowerOfTwo(bitmap.getHeight());
        Bitmap textureBitmap;

        if (texWidth == bitmap.getWidth() && texHeight == bitmap.getHeight()) {
            textureBitmap = bitmap;
        } else {
            textureBitmap = Bitmap.createBitmap(texWidth, texHeight, bitmap.getConfig());
            new Canvas(textureBitmap).drawBitmap(bitmap, 0, 0, null);
        }

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
        return new GroupTexture(texId[0], bitmap.getWidth(), bitmap.getHeight(), texWidth, texHeight, null,
                textureBitmap);
    }

    /**
     * Returns the color group index at a specific pixel.
     */
    public int getGroup(int x, int y) {
        int pixel = rawData.getPixel(x, y);
        return ((Color.red(pixel) | (Color.blue(pixel) << 2)) * 128) / 256;
    }

    /**
     * Paints a single pixel in the texture.
     */
    public void paintPixel(int x, int y, int colorIndex) {
        int red = (colorIndex % 128) * 2;
        int green = (colorIndex / 128) * 2;
        int argb = Color.argb(255, red, green, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glId);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, x, y, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                ByteBuffer.wrap(new byte[] { (byte) red, (byte) green, 0, (byte) 255 }));

        rawData.setPixel(x, y, argb);
    }

    /**
     * Efficiently paints all pixels belonging to a group.
     */
    public void autoPaintGroup(GroupTexture blueprint, int groupId) {
        int red = (groupId % 128) * 2;
        int green = (groupId / 128) * 2;
        int argb = Color.argb(255, red, green, 0);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (blueprint.getGroup(x, y) == groupId) {
                    rawData.setPixel(x, y, argb);
                }
            }
        }

        // Upload updated bitmap to GPU
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, rawData, 0);
    }

    public Bitmap getRawData() {
        return rawData;
    }
}
