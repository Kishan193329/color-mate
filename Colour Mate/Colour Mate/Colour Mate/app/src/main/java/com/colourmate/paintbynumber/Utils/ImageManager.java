package com.colourmate.paintbynumber.Utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Registry;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.SharePreviewActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pl.droidsonroids.gif.GifDrawable;

public class ImageManager {
    private static ImageManager instance;
    private final Context context;
    private List<ImageInfo> imageInfoList = new ArrayList<>();
    private List<ImageInfo> myImageInfoList = new ArrayList<>();
    private List<String> myImageInfoListIDs = new ArrayList<>();

    public interface OnGifLoaded {
        void onFail();

        void onSuccess();
    }

    public interface OnImageLoaded {
        void onFail();

        void onSuccess(Bitmap bitmap);
    }

    public interface OnImageLoadedUI {
        void onFail();

        /**
         * @param image    The loaded image: Bitmap or GifDrawable
         * @param animated true = image is GIF, false = static Bitmap
         */
        void onSuccess(Object image, boolean animated);
    }

    public ImageManager(Context context2) {
        this.context = context2;
    }

    public static void init(Context context) {
        synchronized (ImageManager.class) {
            if (instance == null) {
                instance = new ImageManager(context);
                instance.loadLocalJSON();
                instance.requestImagesRemoteUpdate();
            }
        }
    }

    public static ImageManager getInstance() {
        ImageManager localRef = instance;
        if (localRef == null) {
            // Startup init runs on a background thread now, so a caller can beat it here. Build it
            // on the spot rather than throwing — init() is synchronized and idempotent, so whoever
            // arrives second simply gets the finished instance.
            init(com.colourmate.paintbynumber.GlobalApplication.getAppContext());
            localRef = instance;
        }
        return localRef;
    }

    public List<ImageInfo> getImageInfoList() {
        return this.imageInfoList;
    }

    public List<ImageInfo> getMyImageInfoList() {
        return this.myImageInfoList;
    }

    private void requestImagesRemoteUpdate() {
        ContentProvider.getGalleryFromServer(new ContentProvider.JsonResponseListener() {

            @Override
            public void onDownloadFailed() {
                // Log or handle the failure (optional)
                Log.w("ImageManager", "Remote image update failed.");
            }

            @Override
            public void onDownloadSucceeded(String json) {
                if (json == null || json.trim().isEmpty()) {
                    Log.w("ImageManager", "Received empty JSON. Skipping parse.");
                    return;
                }
                parseImageArrayFromJSON(json, true);
            }
        });
    }

    private void loadLocalJSON() {

        // 1. Read JSON from SharedPreferences
        String json = context.getSharedPreferences("colorbynumber", 0)
                .getString("galleryJson", null);

        // 2. If SP is empty → load fallback from assets/images.json
        if (json == null || json.trim().isEmpty()) {
            json = loadJsonFromAssets("images.json");
        }

        // 3. If everything fails → use an empty JSON object
        if (json == null || json.trim().isEmpty()) {
            Log.e("ImageManager", "Local JSON not found. Using empty data.");
            json = "{\"images\":[]}";
        }

        // 4. Parse safely on main thread
        final String finalJson = json;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                parseImageArrayFromJSON(finalJson, false);
            } catch (Exception e) {
                Log.e("ImageManager", "Failed to parse local gallery JSON", e);
            }
        });
    }

    private String loadJsonFromAssets(String filename) {
        try {
            InputStream is = context.getAssets().open(filename);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;

            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }

            return baos.toString("UTF-8");

        } catch (IOException e) {
            Log.e("ImageManager", "Error reading JSON from assets: " + filename, e);
            return null;
        }
    }


    private void parseImageArrayFromJSON(String json, boolean saveToPrefs) {

        if (json == null || json.trim().isEmpty()) {
            Log.e("ImageManager", "Empty JSON, parse aborted");
            return;
        }

        try {
            JSONObject root = new JSONObject(json);

            List<ImageInfo> images = ImageInfo.parseImageArray(root);

            if (images == null || images.isEmpty()) {
                Log.e("ImageManager", "Parsed image list empty");
                return;
            }

            // Save to SharedPreferences if requested
            if (saveToPrefs) {
                context.getSharedPreferences("colorbynumber", Context.MODE_PRIVATE)
                        .edit()
                        .putString("galleryJson", json)
                        .apply();
            }

            // Sort safely by release date (newest first)
            Collections.sort(images, (img1, img2) -> {
                Date d1 = img1.getReleaseDate();
                Date d2 = img2.getReleaseDate();
                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1; // null dates come last
                if (d2 == null) return -1;
                return d2.compareTo(d1);
            });

            this.imageInfoList = images;

            // Load user works after list is ready
            loadMyWorks();

            // Notify UI layer to update adapters
            LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(new Intent("requestMenuReload"));

        } catch (JSONException e) {
            Log.e("ImageManager", "JSON parse error", e);
        } catch (Exception e) {
            Log.e("ImageManager", "Unexpected error parsing JSON", e);
        }
    }

    public String getExistingPathToFolder(String folderName) {

        if (folderName == null || folderName.trim().isEmpty()) {
            Log.e("ImageManager", "Invalid folder name");
            return null;
        }

        File folder = new File(context.getFilesDir(), folderName);

        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                Log.e("ImageManager", "Failed to create folder: " + folder.getAbsolutePath());
                return null;
            }
        }

        return folder.getAbsolutePath();
    }

    public void saveImage(Bitmap bitmap, String fileName, String folderName) {

        String folderPath = getExistingPathToFolder(folderName);
        if (folderPath == null) {
            Log.e("ImageManager", "Folder creation failed");
            return;
        }

        File file = new File(folderPath, fileName + ".png");

        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            Log.e("ImageManager", "Failed to save image: " + file.getAbsolutePath(), e);
        }
    }

    public void saveGif(BufferedInputStream inputStream, String fileName) {

        String folderPath = getExistingPathToFolder(Registry.BUCKET_GIF);

        if (folderPath == null) {
            Log.e("ImageManager", "Failed to create GIF folder");
            return;
        }

        File gifFile = new File(folderPath, fileName + ".gif");

        try (BufferedInputStream in = inputStream;
             FileOutputStream out = new FileOutputStream(gifFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            out.flush();
            Log.d("ImageManager", "GIF saved: " + gifFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e("ImageManager", "Failed to save GIF: " + gifFile.getAbsolutePath(), e);
        }
    }

    public void saveImage(Bitmap bitmap, String fileName) {
        saveImage(bitmap, fileName, "Images");
    }


    public void saveColorImage(Bitmap bitmap, String fileName) {
        saveImage(bitmap, fileName, "MyWorks");
        addToMyWorks(fileName);
    }

    public void saveProgressImage(Bitmap bitmap, String fileName) {
        saveImage(bitmap, fileName, "Progress");
    }

    public void saveTimelapse(Bitmap bitmap, String baseName) {

        // Target folder: /files/Timelapses/<id>
        String folderName = "Timelapses" + File.separator + baseName;
        String folderPath = getExistingPathToFolder(folderName);

        if (folderPath == null) {
            Log.e("ImageManager", "Unable to access timelapse folder: " + folderName);
            return;
        }

        File folder = new File(folderPath);
        File[] files = folder.listFiles();
        int index = 0;

        if (files != null) {
            index = files.length;
        }

        // Create a file name like: "img_000000123"
        String fileName = String.format("%s_%09d", baseName, index);

        saveImage(bitmap, fileName, folderName);
    }

    public void getImage(final String id, final OnImageLoadedUI callback) {

        // ---- 1. Load PNG Local ----
        String imagesFolder = getExistingPathToFolder("Images");
        if (imagesFolder != null) {
            File pngFile = new File(imagesFolder, id + ".png");
            if (pngFile.exists()) {
                Bitmap bitmap = loadBitmapSafely(pngFile);
                if (bitmap != null) {
                    if (callback != null) callback.onSuccess(bitmap, false);
                    return;
                }
            }
        }

        // ---- 2. Load GIF Local ----
        String gifFolder = getExistingPathToFolder(Registry.BUCKET_GIF);
        if (gifFolder != null) {
            File gifFile = new File(gifFolder, id + ".gif");
            if (gifFile.exists()) {
                try {
                    GifDrawable gifDrawable = new GifDrawable(gifFile.getAbsolutePath());
                    if (callback != null) callback.onSuccess(gifDrawable, false);
                    return;
                } catch (Throwable t) {
                    Log.e("ImageManager", "Error loading GIF", t);
                }
            }
        }

        // ---- 3. Remote IMAGE Download ----
        ContentProvider.downloadImage(id, new OnImageLoaded() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                saveImage(bitmap, id);      // Save local PNG
                if (callback != null) callback.onSuccess(bitmap, true);
            }

            @Override
            public void onFail() {

                // ---- 4. Remote GIF Download ----
                ContentProvider.downloadGif(id, new OnGifLoaded() {
                    @Override
                    public void onSuccess() {
                        String gifPath = getExistingPathToFolder(Registry.BUCKET_GIF)
                                + File.separator + id + ".gif";

                        try {
                            GifDrawable drawable = new GifDrawable(gifPath);
                            if (callback != null) callback.onSuccess(drawable, false);
                        } catch (Throwable t) {
                            Log.e("ImageManager", "GIF load error", t);
                            if (callback != null) callback.onFail();
                        }
                    }

                    @Override
                    public void onFail() {
                        if (callback != null) callback.onFail();
                    }
                });
            }
        });
    }

    private Bitmap loadBitmapSafely(File file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeStream(fis, null, opts);
        } catch (Exception e) {
            Log.e("ImageManager", "Failed to decode bitmap: " + file.getAbsolutePath(), e);
            return null;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException ignored) {}
            }
        }
    }

    public Bitmap getImageLocal(String imageName) {
        if (imageName.contains("import_")) {
            return getImageLocal(imageName, "Import");
        }
        return getImageLocal(imageName, "Images");
    }

    public GifDrawable getGifLocal(String id) {

        // Validate GIF folder
        String folderPath = getExistingPathToFolder(Registry.BUCKET_GIF);
        if (folderPath == null) {
            Log.e("ImageManager", "GIF folder missing.");
            return null;
        }

        // File path
        File gifFile = new File(folderPath, id + ".gif");

        if (!gifFile.exists()) {
            return null;
        }

        try {
            return new GifDrawable(gifFile.getAbsolutePath());
        } catch (Throwable t) {  // catch everything
            Log.e("ImageManager", "Failed to load GIF: " + gifFile.getAbsolutePath(), t);
            return null;
        }
    }


    public Bitmap getColorImage(String imageName) {
        return getImageLocal(imageName, "MyWorks");
    }

    public Bitmap getProgressImage(String imageLocal) {
        return getImageLocal(imageLocal, "Progress");
    }

    public boolean getImageLocalExists(String id) {
        // Imports use a different bucket
        if (id != null && id.contains("import_")) {
            return getImageLocalExists(id, "png", "Import");
        }
        return getImageLocalExists(id, "png", "Images");
    }

    public boolean getImageLocalExists(String id, String ext, String folderName) {

        if (id == null || ext == null || folderName == null) {
            return false;
        }

        String folderPath = getExistingPathToFolder(folderName);
        if (folderPath == null) {
            Log.e("ImageManager", "Folder missing: " + folderName);
            return false;
        }

        File file = new File(folderPath, id + "." + ext);
        return file.exists();
    }


    public Bitmap getImageLocal(String id, String folderName) {

        if (id == null || folderName == null) {
            return null;
        }

        String folderPath = getExistingPathToFolder(folderName);
        if (folderPath == null) {
            Log.e("ImageManager", "Folder does not exist: " + folderName);
            return null;
        }

        File file = new File(folderPath, id + ".png");

        if (!file.exists()) {
            return null;
        }

        FileInputStream fis = null;

        try {
            fis = new FileInputStream(file);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bmp = BitmapFactory.decodeStream(fis, null, opts);

            if (bmp == null) {
                Log.e("ImageManager", "Failed to decode PNG: " + file.getAbsolutePath());
            }

            return bmp;

        } catch (Exception e) {
            Log.e("ImageManager", "Error loading local image: " + file.getAbsolutePath(), e);
            return null;

        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException ignored) {}
            }
        }
    }

    public List<Bitmap> getTimelapseArray(String id) {

        List<Bitmap> frames = new ArrayList<>();

        File dir = new File(getExistingPathToFolder("Timelapses" + File.separator + id));
        File[] files = dir.listFiles();

        if (files == null || files.length == 0) {
            return frames; // safe empty list
        }

        // Sort by filename (default behavior), safe against nulls
        Arrays.sort(files, Comparator.comparing(File::getName));

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        for (File f : files) {
            if (!f.isFile()) continue;
            if (!f.getName().toLowerCase().endsWith(".png")) continue; // only images

            try (FileInputStream fis = new FileInputStream(f)) {
                Bitmap bmp = BitmapFactory.decodeStream(fis, null, opts);
                if (bmp != null) {
                    frames.add(bmp);
                }
            } catch (Exception ignored) { }
        }

        return frames;
    }

    public void loadMyWorks() {

        myImageInfoListIDs = new ArrayList<>();
        myImageInfoList    = new ArrayList<>();

        // These folders
        File myWorksDir  = new File(getExistingPathToFolder("MyWorks"));
        File importDir   = new File(getExistingPathToFolder("Import"));

        File[] myWorksFiles = myWorksDir.listFiles();
        File[] importFiles  = importDir.listFiles();

        // Build map for O(1) lookup
        Map<String, ImageInfo> infoMap = new HashMap<>();
        for (ImageInfo info : imageInfoList) {
            infoMap.put(info.getImageID(), info);
        }

        // 1) Load items from MyWorks folder
        if (myWorksFiles != null) {
            for (File f : myWorksFiles) {
                if (f.isFile()) {
                    String id = f.getName().replace(".png", "");
                    if (!myImageInfoListIDs.contains(id)) {
                        myImageInfoListIDs.add(id);

                        ImageInfo info = infoMap.get(id);
                        if (info != null) {
                            myImageInfoList.add(info);
                        }
                    }
                }
            }
        }

        // 2) Load imported items
        if (importFiles != null) {
            for (File f : importFiles) {
                if (f.isFile()) {
                    String id = f.getName().replace(".png", "");
                    myImageInfoListIDs.add(id);

                    // Imported images always free & use current date
                    myImageInfoList.add(new ImageInfo(id, new Date(), true));
                }
            }
        }
    }

    private void addToMyWorks(String imageId) {
        if (imageId == null) return;

        // Already added? skip
        if (myImageInfoListIDs.contains(imageId)) return;

        // Add ID
        myImageInfoListIDs.add(imageId);

        // Fast lookup
        for (ImageInfo info : imageInfoList) {
            if (info != null && imageId.equals(info.getImageID())) {
                myImageInfoList.add(0, info);
                break;
            }
        }
    }

    public String createAndSaveImageFile(String name, Bitmap bitmap, Context context) {

        if (bitmap == null) return null;

        // Use app-specific external directory (SAFE for Android 10–14)
        File dir = new File(context.getExternalFilesDir(null), SharePreviewActivity.APPFOLDER);

        if (!dir.exists() && !dir.mkdirs()) {
            return null; // cannot create folder
        }

        // Build final file path
        File outFile = new File(dir, name + ".png");

        // Delete old file safely
        if (outFile.exists() && !outFile.delete()) {
            return null;
        }

        // Scale bitmap while keeping aspect ratio
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap,
                512,
                (int) (bitmap.getHeight() * (512f / bitmap.getWidth())),
                true
        );

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            return outFile.getAbsolutePath();
        } catch (IOException e) {
            return null;
        } finally {
            if (scaled != bitmap) scaled.recycle();   // avoid memory leak
        }
    }

    public String createAndSaveImageFile1(String name, Bitmap bitmap, Context context) {
        if (bitmap == null) return null;

        // Ensure folder exists
        String dirPath = getExistingPathToFolder("MyNumberPic");
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs();

        // Final file path
        String filePath = dirPath + File.separator + name + ".png";
        File file = new File(filePath);

        // Delete old file safely
        if (file.exists() && !file.delete()) {
            return null; // cannot overwrite old file
        }

        // Maintain aspect ratio while fitting into 512x512
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap,
                512,
                (int) (bitmap.getHeight() * (512f / bitmap.getWidth())),
                true
        );

        try (FileOutputStream fos = new FileOutputStream(file)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            return filePath;
        } catch (IOException e) {

            return null;
        } finally {
            if (scaled != bitmap) {
                scaled.recycle(); // avoid memory leak
            }
        }
    }


    public ArrayList<String> getNumberPixelImages() {
        ArrayList<String> paths = new ArrayList<>();

        File folder = new File(getExistingPathToFolder("MyNumberPic"));
        if (!folder.exists() || !folder.isDirectory()) {
            return paths;
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return paths;
        }

        for (File f : files) {
            if (f.isFile()) {
                paths.add(f.getAbsolutePath());
            }
        }

        return paths;
    }
}
