package com.colourmate.paintbynumber.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

public class Utils {

    /**
     * Converts a bitmap to grayscale and draws it on a white background.
     */
    public static Bitmap monocromaticImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        // Fill white background
        Paint whitePaint = new Paint();
        whitePaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, width, height, whitePaint);

        // Apply grayscale filter
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0f);

        Paint grayPaint = new Paint();
        grayPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

        // Draw grayscale bitmap
        canvas.drawBitmap(bitmap, 0, 0, grayPaint);

        return output;
    }

    /**
     * Draws a bitmap on a white background (no grayscale).
     */
    public static Bitmap whiteBGImage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint whitePaint = new Paint();
        whitePaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, width, height, whitePaint);

        canvas.drawBitmap(bitmap, 0, 0, null);
        return output;
    }

    /**
     * Loads a bitmap from resources WITHOUT any automatic scaling.
     */
    public static Bitmap decodePixelBitmapFromResource(Context context, int resId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false; // preserve raw pixel data
        return BitmapFactory.decodeResource(context.getResources(), resId, options);
    }
}

