package com.sumoing.recolor.scanner;

import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.TypedValue;

import com.colourmate.paintbynumber.GlobalApplication;

public class Scanner {
    public static final String TAG = "Scanner";

    private native boolean processImage(Bitmap bitmap, boolean isHighQuality);
    public static native void scannerDealloc();
    public native boolean canUndo();
    public native Bitmap getIndexImage();
    public native Bitmap getOverlayImage();
    public native boolean isLineDrawing();
    public native int nextStage();

    /**
     * Send a pan/touch event to the native scanner.
     *
     * @param viewWidth   Current view width in pixels
     * @param viewHeight  Current view height in pixels
     * @param x           Touch X coordinate
     * @param y           Touch Y coordinate
     * @param eventType   0=cancel, 1=down, 2=move, 3=up
     * @param isEraseMode Whether the user is in erase/paint brush mode
     */
    public native void panEvent(
            int viewWidth,
            int viewHeight,
            float x,
            float y,
            int eventType,
            boolean isEraseMode
    );

    /**
     * Send a pinch/scale event to the native scanner.
     *
     * @param viewWidth  Current view width in pixels
     * @param viewHeight Current view height in pixels
     * @param focusX     Focus X of the scale gesture
     * @param focusY     Focus Y of the scale gesture
     * @param scale      Scale factor
     * @param eventType  0=end, 1=begin, 2=update
     */
    public native void pinchEvent(
            int viewWidth,
            int viewHeight,
            float focusX,
            float focusY,
            float scale,
            int eventType
    );

    /**
     * Send a rotation event to the native scanner.
     * IMPORTANT: Parameter order must match C++ JNI: (focusX, focusY, angle)
     *
     * @param focusX Focus X of the rotation gesture
     * @param focusY Focus Y of the rotation gesture
     * @param angle  Rotation angle in radians
     */
    public native void rotateEvent(
            float focusX,
            float focusY,
            float angle
    );

    public native void scannerCreate(
            AssetManager assetManager,
            String filesDir,
            float density,
            boolean isTablet
    );

    public native void setMode(int mode);

    public native void setScannerType(int scannerType);

    public native int step(float viewWidth, float viewHeight);

    public native void tapEvent(
            int action,
            int pointerCount,
            float x,
            float y
    );

    public native boolean undo();

    private static boolean sNativeLoaded = false;

    static {
        try {
            System.loadLibrary("scanner");
            sNativeLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            sNativeLoaded = false;
            android.util.Log.e(TAG, "Failed to load scanner native library: " + e.getMessage(), e);
        } catch (Exception e) {
            sNativeLoaded = false;
            android.util.Log.e(TAG, "Error loading scanner native library: " + e.getMessage(), e);
        }
    }

    /**
     * Check if the scanner native library was loaded successfully.
     */
    public static boolean isNativeLoaded() {
        return sNativeLoaded;
    }

    public Scanner() {
        initializeScanner();
    }

    public boolean startProcessing(Bitmap bitmap, boolean isHighQuality) {
        return processImage(bitmap, isHighQuality);
    }

    private void initializeScanner() {
        String filesDirPath;
        try {
            filesDirPath = new ContextWrapper(
                    GlobalApplication.getAppContext()
            ).getFilesDir().getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            filesDirPath = null;
        }

        float densityScale = 1.0f / (
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        10.0f,
                        GlobalApplication.getAppContext()
                                .getResources()
                                .getDisplayMetrics()
                ) / 10.0f
        );

        scannerCreate(
                GlobalApplication.getAppContext().getAssets(),
                filesDirPath,
                densityScale,
                GlobalApplication.isTablet()
        );
    }
}
