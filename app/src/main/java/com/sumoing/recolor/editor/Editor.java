package com.sumoing.recolor.editor;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.util.PurchaseManager;
import com.colourmate.paintbynumber.util.UIHelpers;
import com.sumoing.recolor.library.LibraryItem;

public class Editor {
    private static GLUIListener mListener;
    private static boolean sNativeLibsLoaded = false;
    private static String sLoadError = null;

    public interface GLUIListener {
        void donePressedCallback();
        void drawingReady();
        void openSubsribtionDialogCallback();
        void wakeupCallback();
    }

    public static native long drawingViewInit();
    public static native void effectLayerLoad(AssetManager assetManager, String str);
    public static native void effectLayerPan(float dx, float dy);
    public static native void effectLayerRender(float progress, int width, int height, boolean isFinal);
    public static native void effectLayerSetLut(float value, String lutPath);
    public static native void effectLayerSetOutline(int outline);
    public static native void effectLayerShuffle();
    public static native void effectLayerUnload();
    public static native boolean exportDraw(int width, int height, boolean transparent);
    public static native int exportLoadEffect(
            AssetManager assetManager,
            int width,
            int height,
            String effect,
            String lut,
            String outline,
            float x,
            float y,
            float scale,
            String mask,
            String overlay,
            String background
    );
    public static native void exportPan(float dx, float dy);
    public static native void exportPinch(float x, float y, float scale);
    public static native boolean exportPrepare(AssetManager assetManager, int width, int height);
    public static native void exportRelease();
    public static native void exportSingleTouchpointEvent(int action, float x, float y);
    public static native Bitmap gluiCreateThumb(int size);
    public static native int gluiDraw(long handle, int width, int height);
    public static native void gluiGestureEvent(long handle, int action, float x1, float y1, float x2, float y2);
    public static native boolean gluiHitCheck(long handle, float x, float y);
    public static native long gluiInit(
            AssetManager assetManager,
            String filesDir,
            int width,
            int height,
            float density,
            boolean isTablet
    );

    public static native void gluiRelease();
    public static native void gluiSaveProgress(String path);
    public static native void gluiSetHasSubscription(boolean hasSubscription);
    public static native boolean gluiShouldSave();
    public static native void gluiTouchEvent(
            long handle,
            int action,
            int pointerCount,
            float x,
            float y
    );
    public static native boolean setDrawing(
            String id,
            byte[] a1, byte[] a2, byte[] a3,
            byte[] a4, byte[] a5, byte[] a6,
            byte[] a7, byte[] a8, byte[] a9,
            LibraryItem.Config3D config3D
    );

    static {
        try {
            System.loadLibrary("pdfium");
            System.loadLibrary("editor");
            sNativeLibsLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            sNativeLibsLoaded = false;
            sLoadError = e.getMessage();
            android.util.Log.e("Editor", "Failed to load native libraries: " + e.getMessage(), e);
        } catch (Exception e) {
            sNativeLibsLoaded = false;
            sLoadError = e.getMessage();
            android.util.Log.e("Editor", "Error loading native libraries: " + e.getMessage(), e);
        }
    }

    /**
     * Check if native editor libraries were loaded successfully.
     * Call this before using any native methods to avoid UnsatisfiedLinkError crashes.
     */
    public static boolean isNativeLoaded() {
        return sNativeLibsLoaded;
    }

    /**
     * Get the error message if native libraries failed to load.
     */
    public static String getLoadError() {
        return sLoadError;
    }

    public static long gluiInit(int width, int height) {
        if (!sNativeLibsLoaded) {
            android.util.Log.e("Editor", "Cannot init GLUI - native libraries not loaded: " + sLoadError);
            return 0;
        }

        String filesDir;
        try {
            filesDir = new ContextWrapper(
                    GlobalApplication.getAppContext()
            ).getFilesDir().getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            filesDir = null;
        }

        float density = 1.0f / (
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        10.0f,
                        GlobalApplication.getAppContext()
                                .getResources()
                                .getDisplayMetrics()
                ) / 10.0f
        );

        long handle = gluiInit(
                GlobalApplication.getAppContext().getAssets(),
                filesDir,
                width,
                height,
                density,
                GlobalApplication.isTablet()
        );

        gluiSetHasSubscription(
                PurchaseManager.getInstance().hasSubscription()
        );

        return handle;
    }

    public static Bitmap renderText(String text,
                                    int maxWidth,
                                    float redComponent,
                                    float greenComponent,
                                    float blueComponent,
                                    float fontSizeSp,
                                    int fontWeight) {

        // Initialize text paint with font
        TextPaint textPaint = new TextPaint();
        Context appContext = GlobalApplication.getAppContext();
        String fontFileName = "SanssarifRounded-" + fontWeight + ".otf";
        textPaint.setTypeface(UIHelpers.FontCache.getFont(appContext, fontFileName));

        // Set font size with default fallback
        if (fontSizeSp <= 0.0f) {
            fontSizeSp = 14.0f;
        }
        float fontSizePixels = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                fontSizeSp,
                appContext.getResources().getDisplayMetrics()
        );
        textPaint.setTextSize(fontSizePixels);
        textPaint.setAntiAlias(true);

        // Set text color from RGB components (0.0-1.0 range to 0-255)
        int red = (int) (redComponent * 255.0f);
        int green = (int) (greenComponent * 255.0f);
        int blue = (int) (blueComponent * 255.0f);
        textPaint.setARGB(255, red, green, blue);

        // Measure text width
        float textWidth = textPaint.measureText(text, 0, text.length());

        // Handle multi-line text if width exceeds maximum
        if (maxWidth > 0 && textWidth > maxWidth) {
            // Calculate adjusted width with scaling factor
            float densityScaleFactor = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    10.0f,
                    appContext.getResources().getDisplayMetrics()
            ) / 10.0f;

            int layoutWidth = (int) (maxWidth * densityScaleFactor);

            // Create multi-line layout
            StaticLayout textLayout = new StaticLayout(
                    text,
                    textPaint,
                    layoutWidth,
                    Layout.Alignment.ALIGN_CENTER,
                    1.0f,  // lineSpacingMultiplier
                    0.0f,  // lineSpacingExtra
                    false  // includePadding
            );

            // Create bitmap for multi-line text
            Bitmap bitmap = Bitmap.createBitmap(
                    textLayout.getWidth(),
                    textLayout.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            bitmap.eraseColor(0); // Transparent background

            Canvas canvas = new Canvas(bitmap);
            textLayout.draw(canvas);
            return bitmap;
        }

        // Handle single-line text
        Bitmap bitmap = Bitmap.createBitmap(
                (int) textWidth,
                (int) fontSizePixels,
                Bitmap.Config.ARGB_8888
        );
        bitmap.eraseColor(0); // Transparent background

        Canvas canvas = new Canvas(bitmap);

        // Calculate vertical center position for text baseline
        float canvasHeight = canvas.getHeight();
        float textAscent = textPaint.ascent();
        float textDescent = textPaint.descent();
        float verticalCenter = (canvasHeight / 2.0f) - ((textDescent + textAscent) / 2.0f);

        // Draw text centered vertically
        canvas.drawText(text, 0.0f, verticalCenter, textPaint);

        return bitmap;
    }


    public static long drawingViewInit(int width, int height) {
        return drawingViewInit();
    }

    public static void setListener(GLUIListener gLUIListener) {
        mListener = gLUIListener;
    }

    public static void drawingReady() {
        if (mListener != null) {
            mListener.drawingReady();
        }
    }

    public static void donePressedCallback() {
        if (mListener != null) {
            mListener.donePressedCallback();
        }
    }

    public static void openSubsribtionDialogCallback() {
        if (mListener != null) {
            mListener.openSubsribtionDialogCallback();
        }
    }

    public static void wakeupCallback() {
        if (mListener != null) {
            mListener.wakeupCallback();
        }
    }
}
