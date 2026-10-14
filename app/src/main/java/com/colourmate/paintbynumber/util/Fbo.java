package com.colourmate.paintbynumber.util;

import android.opengl.GLES20;

public class Fbo extends GLES20 {
    int fbo;
    int height;
    int texture;
    int width;

    public int getFbo() {
        return this.fbo;
    }

    public int getTexture() {
        return this.texture;
    }

    public int takeTexture() {
        int temp = texture;
        texture = -1;
        return temp;
    }

    public Fbo(int width, int height) {
        this.width = width;
        this.height = height;

        this.texture = createTexture(width, height);
        this.fbo = createFrameBuffer(width, height, this.texture);
    }

    public void release() {
        if (fbo != -1) {
            GLES20.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            fbo = -1;
        }
        if (texture != -1) {
            GLES20.glDeleteTextures(1, new int[]{texture}, 0);
            texture = -1;
        }
    }


    private int createTexture(int width, int height) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
        );

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        return texId;
    }


    private int createFrameBuffer(int width, int height, int textureId) {
        int[] fbos = new int[1];
        GLES20.glGenFramebuffers(1, fbos, 0);
        int fboId = fbos[0];

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                textureId,
                0
        );

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            throw new RuntimeException("Framebuffer is not complete: " + Integer.toHexString(status));
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        return fboId;
    }

    public void bind(boolean clear) {
        GLES20.glViewport(0, 0, width, height);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);

        if (clear) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        }
    }
}
