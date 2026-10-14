package com.colourmate.paintbynumber.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;

public class ExifHelper {
    public static int exifToDegrees(int exifOrientation) {
        switch (exifOrientation) {
            case 6:
                return 90;
            case 3:
                return 180;
            case 8:
                return 270;
            default:
                return 0;
        }
    }

    public static Bitmap convertBitmapToCorrectOrientation(Bitmap bitmap, int degrees) {
        if (bitmap == null || degrees == 0) {
            return bitmap; // No rotation needed
        }

        Matrix matrix = new Matrix();
        matrix.preRotate(degrees);

        return Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true // Filter set to true for smoother rotation
        );
    }
}
