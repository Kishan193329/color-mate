package com.colourmate.paintbynumber.Utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * A utility class for fetching remote content like JSON data, images, and GIFs from a server.
 * This class uses {@link AsyncTask} for performing network operations on a background thread.
 * <p>
 * Note: This class uses the deprecated AsyncTask. For new development, consider using modern
 * concurrency solutions like Kotlin Coroutines, RxJava, or Java's {@code ExecutorService}.
 */
public class ContentProvider {

    private static final String TAG = "RemoteContentFetcher";

    // The base URL for all remote content (Loaded from native C++ for security)
    private static final String BASE_URL = Manager.CONTENT_BASE_URL;

    /**
     * Interface for receiving callbacks for a JSON download request.
     */
    public interface JsonResponseListener {
        /**
         * Called when the download fails.
         */
        void onDownloadFailed();

        /**
         * Called when the download succeeds.
         *
         * @param jsonResponse The JSON string response from the server.
         */
        void onDownloadSucceeded(@NonNull String jsonResponse);
    }

    /**
     * AsyncTask to download a GIF from a URL and save it using the {@link ImageManager}.
     */
    public static class DownloadGifTask extends AsyncTask<String, Void, Boolean> {
        private final ImageManager.OnGifLoaded listener;
        private final String imageId;

        public DownloadGifTask(@NonNull ImageManager.OnGifLoaded onGifLoaded, @NonNull String imageId) {
            this.listener = onGifLoaded;
            this.imageId = imageId;
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            if (urls == null || urls.length == 0) {
                return false;
            }
            try (BufferedInputStream in = new BufferedInputStream(new URL(urls[0]).openStream())) {
                ImageManager.getInstance().saveGif(in, this.imageId);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error downloading or saving GIF for imageId: " + imageId, e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                listener.onSuccess();
            } else {
                listener.onFail();
            }
        }
    }

    /**
     * AsyncTask to download an image from a URL as a Bitmap.
     */
    public static class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageManager.OnImageLoaded listener;

        public DownloadImageTask(@NonNull ImageManager.OnImageLoaded onImageLoaded) {
            this.listener = onImageLoaded;
        }

        @Nullable
        @Override
        protected Bitmap doInBackground(String... urls) {
            if (urls == null || urls.length == 0) {
                return null;
            }
            try (InputStream in = new URL(urls[0]).openStream()) {
                return BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                Log.e(TAG, "Error downloading image from URL: " + urls[0], e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(@Nullable Bitmap result) {
            if (result == null) {
                listener.onFail();
            } else {
                listener.onSuccess(result);
            }
        }
    }

    /**
     * AsyncTask to download a JSON string from a URL.
     */
    public static class JsonDownloadTask extends AsyncTask<String, Void, String> {
        private final JsonResponseListener listener;

        public JsonDownloadTask(@NonNull JsonResponseListener listener) {
            this.listener = listener;
        }

        private String readAll(@NonNull Reader reader) throws IOException {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }

        @Nullable
        @Override
        protected String doInBackground(String... urls) {
            if (urls == null || urls.length == 0) {
                return null;
            }
            try (InputStream inputStream = new URL(urls[0]).openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return readAll(reader);
            } catch (IOException e) {
                Log.e(TAG, "Error downloading JSON from URL: " + urls[0], e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(@Nullable String result) {
            if (result != null) {
                listener.onDownloadSucceeded(result);
            } else {
                listener.onDownloadFailed();
            }
        }
    }

    /**
     * Fetches the main gallery JSON file from the server.
     *
     * @param listener The callback to be invoked when the download is complete or fails.
     */
    public static void getGalleryFromServer(@NonNull JsonResponseListener listener) {
        String url = BASE_URL + "images2.json";
        new JsonDownloadTask(listener).execute(url);
    }

    /**
     * Downloads a PNG image from the server.
     *
     * @param imageFileName The file name of the image (without extension).
     * @param listener      The callback for the download result.
     */
    public static void downloadImage(@NonNull String imageFileName, @NonNull ImageManager.OnImageLoaded listener) {
        String url = BASE_URL + "images" + File.separator + imageFileName + ".png";
        new DownloadImageTask(listener).execute(url);
    }

    /**
     * Downloads a GIF from the server.
     *
     * @param imageFileName The file name of the GIF (without extension).
     * @param listener      The callback for the download result.
     */
    public static void downloadGif(@NonNull String imageFileName, @NonNull ImageManager.OnGifLoaded listener) {
        String url = BASE_URL + "images" + File.separator + imageFileName + ".gif";
        new DownloadGifTask(listener, imageFileName).execute(url);
    }
}
