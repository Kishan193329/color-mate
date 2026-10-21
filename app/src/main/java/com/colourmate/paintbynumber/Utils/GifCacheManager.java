package com.colourmate.paintbynumber.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GifCacheManager {

    private static final String TAG = "GifCacheManager";
    private static final String GIF_FILENAME = "splash_animation.gif";
    private static final String PREF_NAME = "gif_cache_prefs";
    private static final String KEY_GIF_URL = "cached_gif_url";
    private static final String KEY_GIF_CACHED = "gif_is_cached";
    private static final String KEY_GIF_SIZE = "cached_gif_size";

    @Nullable
    public static File getCachedGif(Context context) {
        File file = new File(context.getFilesDir(), GIF_FILENAME);
        if (!file.exists() || file.length() == 0) {
            Log.d(TAG, "Cache file not found or empty");
            return null;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_GIF_CACHED, false)) {
            Log.d(TAG, "Cache flag not set");
            return null;
        }

        // Verify file size matches what we saved
        long expectedSize = prefs.getLong(KEY_GIF_SIZE, 0);
        if (expectedSize > 0 && file.length() != expectedSize) {
            Log.w(TAG, "Cache file size mismatch. Expected: " + expectedSize
                    + ", Actual: " + file.length());
            clearCache(context);
            return null;
        }

        Log.d(TAG, "Valid cache found: " + file.getAbsolutePath()
                + " (" + file.length() + " bytes)");
        return file;
    }

    public static boolean isCached(Context context) {
        return getCachedGif(context) != null;
    }

    public static boolean isUrlChanged(Context context, String newUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String cachedUrl = prefs.getString(KEY_GIF_URL, "");
        return !newUrl.equals(cachedUrl);
    }

    /**
     * Download GIF in background thread and save to internal storage
     */
    public static void downloadAndCache(Context context, String url,
                                        @Nullable OnCacheCompleteListener listener) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;

            try {
                File tempFile = new File(context.getFilesDir(), GIF_FILENAME + ".tmp");
                File outputFile = new File(context.getFilesDir(), GIF_FILENAME);

                // Clean up temp file
                if (tempFile.exists()) tempFile.delete();

                URL gifUrl = new URL(url);
                connection = (HttpURLConnection) gifUrl.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent",
                        "ColourMate-Android/" + android.os.Build.VERSION.RELEASE);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP error: " + responseCode);
                    notifyFailed(listener, "HTTP " + responseCode);
                    return;
                }

                long contentLength = connection.getContentLength();
                Log.d(TAG, "Downloading GIF: " + contentLength + " bytes");

                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[65536];
                int bytesRead;
                long totalBytesRead = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }

                outputStream.flush();
                outputStream.close();
                outputStream = null;

                inputStream.close();
                inputStream = null;

                // Verify download
                if (tempFile.length() == 0) {
                    Log.e(TAG, "Downloaded file is empty");
                    tempFile.delete();
                    notifyFailed(listener, "Empty file");
                    return;
                }

                // Atomic rename: temp → final
                if (outputFile.exists()) outputFile.delete();
                boolean renamed = tempFile.renameTo(outputFile);

                if (!renamed) {
                    // Fallback: copy
                    copyFile(tempFile, outputFile);
                    tempFile.delete();
                }

                // Save cache metadata
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_GIF_CACHED, true)
                        .putString(KEY_GIF_URL, url)
                        .putLong(KEY_GIF_SIZE, outputFile.length())
                        .apply();

                Log.d(TAG, "GIF cached: " + outputFile.length() + " bytes");

                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(
                            () -> listener.onCached(outputFile));
                }

            } catch (Exception e) {
                Log.e(TAG, "GIF download failed", e);
                notifyFailed(listener, e.getMessage());
            } finally {
                try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) {}
                try { if (inputStream != null) inputStream.close(); } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        }, "GifDownloadThread").start();
    }

    public static void clearCache(Context context) {
        File file = new File(context.getFilesDir(), GIF_FILENAME);
        if (file.exists()) file.delete();

        File temp = new File(context.getFilesDir(), GIF_FILENAME + ".tmp");
        if (temp.exists()) temp.delete();

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        Log.d(TAG, "Cache cleared");
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static void notifyFailed(@Nullable OnCacheCompleteListener listener, String error) {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(
                    () -> listener.onFailed(error));
        }
    }

    public interface OnCacheCompleteListener {
        void onCached(File gifFile);
        void onFailed(String error);
    }
}
