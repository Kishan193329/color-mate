package com.colourmate.paintbynumber.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;

import com.colourmate.paintbynumber.GlobalApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class AssetHelper {
    public static final String TAG = "Helper";

    public static void copyInputStreamToFile(InputStream sourceInputStream, File destinationFile) throws IOException {
        // Use try-with-resources to automatically close streams
        try (InputStream inputStream = sourceInputStream;
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

            byte[] buffer = new byte[16 * 1024]; // 16 KB buffer
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }


    public static Bitmap loadBitmap(String pathOrAsset) {
        Log.d(TAG, "Loading bitmap: " + pathOrAsset);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;   // No scaling based on device density
        options.inSampleSize = 1;   // Full resolution
        options.inDensity = 0;      // Disable density scaling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            options.inPremultiplied = false;
        }

        InputStream inputStream = null;
        try {
            File file = new File(pathOrAsset);
            if (file.exists()) {
                inputStream = Files.newInputStream(file.toPath());
            } else {
                inputStream = GlobalApplication.getAppContext().getAssets().open(pathOrAsset);
            }

            if (inputStream == null) {
                Log.e(TAG, "Failed to open bitmap input stream: " + pathOrAsset);
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);

            if (bitmap != null) {
                Log.d(TAG, "Bitmap loaded: " + pathOrAsset + " (" +
                        bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
            } else {
                Log.e(TAG, "Failed to decode bitmap: " + pathOrAsset);
            }

            return bitmap;
        } catch (IOException e) {
            Log.e(TAG, "Error loading bitmap: " + pathOrAsset, e);
            return null;
        } finally {
            // Ensure the input stream is closed
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close input stream", e);
                }
            }
        }
    }


}
