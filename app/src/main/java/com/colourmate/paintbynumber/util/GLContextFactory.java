package com.colourmate.paintbynumber.util;

import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Helper class to manage OpenGL ES 2.0 context and configuration.
 */
public class GLContextFactory {

    private static final String TAG = "GLContextFactory";

    /** Shared EGL context to allow resource sharing between surfaces */
    public static EGLContext sharedContext = EGL10.EGL_NO_CONTEXT;

    /**
     * ConfigChooser selects an appropriate EGLConfig for GLSurfaceView.
     */
    public static class ConfigChooser implements GLSurfaceView.EGLConfigChooser {

        private static final int EGL_OPENGL_ES2_BIT = 4;
        private static final int[] DEFAULT_CONFIG_ATTRS = {
                12324, 8,  // Red size
                12323, 8,  // Green size
                12322, 8,  // Blue size
                12352, EGL_OPENGL_ES2_BIT, // Renderable type (OpenGL ES2)
                12344 // Termination
        };

        private final int redSize;
        private final int greenSize;
        private final int blueSize;
        private final int alphaSize;
        private final int depthSize;
        private final int stencilSize;

        private final int[] value = new int[1];

        public ConfigChooser(int red, int green, int blue, int alpha, int depth, int stencil) {
            this.redSize = red;
            this.greenSize = green;
            this.blueSize = blue;
            this.alphaSize = alpha;
            this.depthSize = depth;
            this.stencilSize = stencil;
        }

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] numConfigs = new int[1];
            egl.eglChooseConfig(display, DEFAULT_CONFIG_ATTRS, null, 0, numConfigs);
            int configCount = numConfigs[0];

            if (configCount <= 0) {
                throw new IllegalArgumentException("No EGL configs match configSpec");
            }

            EGLConfig[] configs = new EGLConfig[configCount];
            egl.eglChooseConfig(display, DEFAULT_CONFIG_ATTRS, configs, configCount, numConfigs);
            return chooseBestConfig(egl, display, configs);
        }

        private EGLConfig chooseBestConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs) {
            for (EGLConfig config : configs) {
                int depth = getConfigAttrib(egl, display, config, 12325, 0);
                int stencil = getConfigAttrib(egl, display, config, 12326, 0);

                if (depth >= depthSize && stencil >= stencilSize) {
                    int red = getConfigAttrib(egl, display, config, 12324, 0);
                    int green = getConfigAttrib(egl, display, config, 12323, 0);
                    int blue = getConfigAttrib(egl, display, config, 12322, 0);
                    int alpha = getConfigAttrib(egl, display, config, 12321, 0);

                    if (red == redSize && green == greenSize && blue == blueSize && alpha == alphaSize) {
                        printConfig(egl, display, config);
                        return config;
                    }
                }
            }
            return null;
        }

        private int getConfigAttrib(EGL10 egl, EGLDisplay display, EGLConfig config, int attribute, int defaultValue) {
            return egl.eglGetConfigAttrib(display, config, attribute, value) ? value[0] : defaultValue;
        }

        private void printConfig(EGL10 egl, EGLDisplay display, EGLConfig config) {
            // Optionally log EGLConfig attributes for debugging
            Log.d(TAG, "Selected EGLConfig: " + config);
        }
    }

    /**
     * ContextFactory creates and destroys EGLContext for OpenGL ES 2.0.
     */
    public static class ContextFactory implements GLSurfaceView.EGLContextFactory {
        private static final int EGL_CONTEXT_CLIENT_VERSION = 12440;

        @Override
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            Log.d(TAG, "Creating OpenGL ES 2.0 context");
            checkEglError("Before eglCreateContext", egl);

            EGLContext context = egl.eglCreateContext(
                    display,
                    config,
                    sharedContext,
                    new int[]{EGL_CONTEXT_CLIENT_VERSION, 2, 12344} // Terminate attributes
            );

            checkEglError("After eglCreateContext", egl);

            if (sharedContext == EGL10.EGL_NO_CONTEXT) {
                sharedContext = context;
            }

            return context;
        }

        @Override
        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            Log.d(TAG, "Destroying OpenGL ES 2.0 context");
            egl.eglDestroyContext(display, context);
        }

        private void checkEglError(String msg, EGL10 egl) {
            while (egl.eglGetError() != 12288) {
                // Clear errors
            }
        }
    }

    /**
     * Convenience method to set up GLSurfaceView with the custom context and config chooser.
     *
     * @param surfaceView GLSurfaceView instance
     * @param useDepthStencil Whether to request depth and stencil buffers
     * @param depthSize Depth buffer size (typically 16)
     * @param stencilSize Stencil buffer size (typically 0 or 8)
     */
    public static void setupContext(GLSurfaceView surfaceView, boolean useDepthStencil, int depthSize, int stencilSize) {
        surfaceView.setEGLContextFactory(new ContextFactory());
        surfaceView.setEGLConfigChooser(new ConfigChooser(8, 8, 8, 8, depthSize, stencilSize));
    }
}
