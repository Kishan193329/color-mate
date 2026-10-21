package com.colourmate.paintbynumber.library;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.colourmate.paintbynumber.GlobalApplication;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;
import com.sumoing.recolor.library.LibraryItem;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

/**
 * NOTE:
 * - Requires: Gson + Ion + AndroidX LocalBroadcastManager
 * - Requires your app classes: ColoringBookApplication, LibraryItem
 */
public class Library {

    public static final String TAG = "Library";

    public static final String B_TAG = "https://";
    public static final String U1_TAG = "coloringlibcdn";
    public static final String U2_TAG = "coloringrecolor";
    public static final String U3_TAG = "com/";
    public static final String U4_TAG = "Books";

    public static final String FORMAT = ".json";
    private static final String MY_BOOK_JSON = "MyBook.json";

    public static final String CAMPAIGN_TAG = "_campaign";
    public static final String DAILIES_TAG = "Dailies";
    public static final String DAILY_NEW_TAG = "todaynew";
    public static final String FREE_TAG = "free";
    public static final String POPULAR_TAG = "popular";
    public static final String RECOMMENDED_TAG = "recommended";
    public static final String SCANNED_TAG = "imported";

    public static final String TAG1 = "XYZMD56983"; // (kept as-is)

    public static Drawing mCurrentDrawing;
    public static JsonObject mMap;

    private static HashMap<String, LibraryNotification> mNotifications = new HashMap<>();
    private static String mTagCache;
    private static HashMap<String, ArrayList<LibraryItem>> mTagMap = new HashMap<>();
    private static String mTagNotification;

    private static final Library singleton = new Library();

    private final ArrayList<ColoredPicture> mColoredPictures = new ArrayList<>();
    public boolean mLibraryItemsChanged;

    // -------------------- MODELS --------------------

    public static class ColoredPicture {
        private String mBaseName;
        private LibraryItem mItem;
        public boolean mScanned;
        public boolean mSelected;
        private long mTimestamp;

        public ColoredPicture(long timestamp, String baseName, LibraryItem item) {
            set(timestamp, baseName, item);
        }

        public File getDocumentFile() {
            File filesDir = GlobalApplication.getAppContext().getFilesDir();
            return new File(filesDir, mBaseName + ".png");
        }

        public File getThumbFile() {
            File filesDir = GlobalApplication.getAppContext().getFilesDir();
            if (mScanned) {
                return new File(filesDir, "scan/" + mBaseName + ".jpg");
            }
            return new File(filesDir, mBaseName + "_thumb.webp");
        }

        public LibraryItem getLibraryItem() {
            return mItem;
        }

        public String getBaseName() {
            return mBaseName;
        }

        public void set(long timestamp, String baseName, LibraryItem item) {
            this.mTimestamp = timestamp;
            this.mBaseName = baseName;
            this.mItem = item;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ColoredPicture)) return false;
            return mBaseName != null && mBaseName.equals(((ColoredPicture) obj).getBaseName());
        }

        @Override
        public int hashCode() {
            return mBaseName == null ? 0 : mBaseName.hashCode();
        }
    }

    public static class ColoredPictureComparator implements Comparator<ColoredPicture> {
        @Override
        public int compare(ColoredPicture a, ColoredPicture b) {
            return Long.compare(b.mTimestamp, a.mTimestamp); // newest first
        }
    }

    public static class Drawing {
        public byte[] mArt;
        public byte[] mControlmap;
        public byte[] mEnvmap;
        public byte[] mIndex;
        public byte[] mIndex2;
        public boolean mIsDownloading;
        public LibraryItem mItem;
        private OnDownloadCompletedListener mListener;
        public byte[] mNormalmap;
        public byte[] mSkybackground;
        public byte[] mTobj;

        public interface OnDownloadCompletedListener {
            void onCompleted();

            void onFailed(String error);
        }

        public byte[] getArtLayer() {
            if (this.mItem.containsTag(Library.SCANNED_TAG)) {
                return null;
            }
            return this.mArt;
        }

        public byte[] getScanLayer() {
            if (mItem != null && mItem.containsTag(Library.SCANNED_TAG)) return mArt;
            return null;
        }

        public String getName() {
            return mItem == null ? "" : mItem.getName();
        }

        public boolean isComplete() {
            LibraryItem libraryItem = this.mItem;
            if (libraryItem == null || this.mArt == null || this.mIndex == null) {
                return false;
            }
            if (libraryItem.index2Url != null && this.mIndex2 == null) {
                return false;
            }
            if (!this.mItem.is3DModel()) {
                return true;
            }
            if (this.mTobj == null) {
                return false;
            }
            if (this.mItem.config3D.normalmapUrl != null && this.mNormalmap == null) {
                return false;
            }
            if (this.mItem.config3D.controlmapUrl != null && this.mControlmap == null) {
                return false;
            }
            if (this.mItem.config3D.envmapUrl != null && this.mEnvmap == null) {
                return false;
            }
            if (this.mItem.config3D.skyBackgroundUrl == null || this.mSkybackground != null) {
                return true;
            }
            return false;
        }


        public boolean isDownloading() {
            return mIsDownloading;
        }

        private void check(Exception exc) {
            if (mListener == null) {
                return;
            }

            if (exc != null) {
                mListener.onFailed(exc.getLocalizedMessage());
                mIsDownloading = false;
                mListener = null;
            } else if (isComplete()) {
                mListener.onCompleted();
                mIsDownloading = false;
                mListener = null;
            }
        }

        public void cancelDownload(Context context) {
            if (!mIsDownloading || mItem == null) return;
            mListener = null;
            mIsDownloading = false;
            Ion.getDefault(context).cancelAll(mItem);
        }

        public void download(Context context, OnDownloadCompletedListener listener) {

            File scanDir = new File(context.getFilesDir(), "scan");
            if (!scanDir.exists()) {
                scanDir.mkdirs();
            }


            if (mItem == null) {
                if (listener != null) listener.onFailed("Drawing item is null");
                return;
            }

            mListener = listener;
            mIsDownloading = true;

            Log.i(TAG, "download: "+mItem.indexUrl);

            // Index
            Ion.with(context).load(mItem.indexUrl).group(mItem).asByteArray().setCallback((e, data) -> {
                Log.d(Library.TAG, "indexData " + e + " " + (data != null ? data.length : 0));
                Library.mCurrentDrawing.mIndex = data;
                check(e);
            });

            // Index2 (optional)
            if (mItem.index2Url != null) {
                Ion.with(context).load(mItem.index2Url).group(mItem).asByteArray().setCallback((e, data) -> {
                    Log.d(Library.TAG, "index2Data " + e + " " + (data != null ? data.length : 0));
                    Library.mCurrentDrawing.mIndex2 = data;
                    check(e);
                });
            }

            // Art
            Ion.with(context).load(mItem.artUrl).group(mItem).asByteArray().setCallback((e, data) -> {
                Log.d(Library.TAG, "art " + e + " " + (data != null ? data.length : 0));
                Library.mCurrentDrawing.mArt = data;
                check(e);
            });

            // 3D assets
            if (mItem.is3DModel() && mItem.config3D != null) {
                Ion.with(context).load(mItem.config3D.tobjUrl).group(mItem).asByteArray().setCallback((e, data) -> {
                    Log.d(Library.TAG, "tobj " + e + " " + (data != null ? data.length : 0));
                    Library.mCurrentDrawing.mTobj = data;
                    check(e);
                });

                if (mItem.config3D.normalmapUrl != null) {
                    Ion.with(context).load(mItem.config3D.normalmapUrl).group(mItem).asByteArray().setCallback((e, data) -> {
                        Log.d(Library.TAG, "normalmap " + e + " " + (data != null ? data.length : 0));
                        Library.mCurrentDrawing.mNormalmap = data;
                        check(e);
                    });
                }

                if (mItem.config3D.controlmapUrl != null) {
                    Ion.with(context).load(mItem.config3D.controlmapUrl).group(mItem).asByteArray().setCallback((e, data) -> {
                        Log.d(Library.TAG, "controlmap " + e + " " + (data != null ? data.length : 0));
                        Library.mCurrentDrawing.mControlmap = data;
                        check(e);
                    });
                }

                if (mItem.config3D.envmapUrl != null) {
                    Ion.with(context).load(mItem.config3D.envmapUrl).group(mItem).asByteArray().setCallback((e, data) -> {
                        Log.d(Library.TAG, "envmap " + e + " " + (data != null ? data.length : 0));
                        Library.mCurrentDrawing.mEnvmap = data;
                        check(e);
                    });
                }

                if (mItem.config3D.skyBackgroundUrl != null) {
                    Ion.with(context).load(mItem.config3D.skyBackgroundUrl).group(mItem).asByteArray().setCallback((e, data) -> {
                        Log.d(Library.TAG, "skyBackground " + e + " " + (data != null ? data.length : 0));
                        Library.mCurrentDrawing.mSkybackground = data;
                        check(e);
                    });
                }
            }
        }
    }

    public static class LibraryNotification {
        public String mNotification;
        public long mPublishDate;

        public LibraryNotification(long publishDate, String notification) {
            this.mPublishDate = publishDate;
            this.mNotification = notification;
        }
    }

    // -------------------- SINGLETON --------------------

    public static Library getInstance() {
        return singleton;
    }

    // -------------------- JSON LOADERS --------------------

    public boolean isNewCopy = false;


    public void saveColoredPicture() {
        if (isNewCopy) {
            addNewColoredPicture(mCurrentDrawing.mItem);
        } else {
            updateAddColoredPicture(mCurrentDrawing.mItem);
        }
        isNewCopy = false; // reset after save
    }

    private JsonObject getLibraryJson() {
        try {
            if (mMap == null) { // ✅ only when nothing is loaded
                Log.d(TAG, "loading asset json");
                mMap = fetchLibraryAsset();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mMap;
    }


    /**
     * If you later implement true online fetch, replace this method.
     * Currently returns the same cached JSON.
     */
    private JsonObject getLibraryJsononline() {
        return mMap; // remote set thai gayu hoy to direct return
    }


    private JsonObject fetchLibraryAsset() throws IOException {
        Log.d(TAG, "fetchLibraryAsset");
        Reader reader = new BufferedReader(new InputStreamReader(GlobalApplication.getAppContext().getAssets().open(MY_BOOK_JSON)));
        JsonElement el = new Gson().fromJson(reader, JsonElement.class);
        return el == null ? null : el.getAsJsonObject();
    }

    // -------------------- TAG / CACHE --------------------

    public String todaysFreeName() {
        Calendar cal = GregorianCalendar.getInstance();
        // shift -12 hours (your original logic)
        cal.setTimeInMillis(System.currentTimeMillis() - 43200000L);
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH) + 1;
        int d = cal.get(Calendar.DAY_OF_MONTH);
        return String.valueOf(y * 10000 + m * 100 + d);
    }

    public void flushCache() {
        mTagCache = null;
    }

    public ArrayList<LibraryItem> getLibraryItemsByTag(String tag) {
        if (!getInstance().todaysFreeName().equals(mTagCache)) {
            updateTagCache();
        }
        return mTagMap.get(tag);
    }

    public ArrayList<LibraryItem> getLibraryItemsByFolder(String folder) {
        if (mTagCache == null) return null;

        ArrayList<LibraryItem> out = new ArrayList<>();
        for (ArrayList<LibraryItem> list : mTagMap.values()) {
            for (LibraryItem item : list) {
                if (item.mFolder != null && item.mFolder.equals(folder)) {
                    if (!item.containsTag(FREE_TAG)) item.addTag(FREE_TAG);

                    boolean exists = false;
                    for (LibraryItem already : out) {
                        if (already.getName().equals(item.getName())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) out.add(item);
                }
            }
        }
        return out;
    }

    // -------------------- SCANNED ITEMS --------------------

    public LibraryItem addScannedFileToTagCache(File file, HashMap<String, ArrayList<ColoredPicture>> coloredMap, boolean addOnTop) {
        String base = file.getName().replace(".jpg", "");
        LibraryItem item = new LibraryItem(base);

        item.artUrl = file.toURI().toString();
        item.thumbUrl = file.toURI().toString();

        File parent = file.getParentFile();
        item.indexUrl = new File(parent, base + "idx.png").toURI().toString();

        item.created = file.lastModified() / 1000L;
        item.addTag(SCANNED_TAG);

        if (coloredMap != null) {
            ArrayList<ColoredPicture> pics = coloredMap.get(item.getName());
            if (pics != null) {
                addColoredPicturesToItem(pics, item);
            } else {
                ColoredPicture cp = new ColoredPicture(file.lastModified(), base, item);
                cp.mScanned = true;
                item.mColored = cp;

                ArrayList<ColoredPicture> list = new ArrayList<>();
                list.add(cp);
                mColoredPictures.add(cp);

                coloredMap.put(base, list);
            }
        } else {
            ColoredPicture cp = new ColoredPicture(file.lastModified(), base, item);
            cp.mScanned = true;
            item.mColored = cp;
            mColoredPictures.add(cp);
            sortColoredPictures();
        }

        addLibraryItem(item, addOnTop);
        return item;
    }

    private void addColoredPicturesToItem(ArrayList<ColoredPicture> pics, LibraryItem item) {
        item.mColored = pics.get(0);
        for (ColoredPicture p : pics) {
            p.mItem = item;
        }
        mColoredPictures.addAll(pics);
    }

    // -------------------- NOTIFICATIONS --------------------

    public LibraryNotification getCurrentNotification() {
        try {
            updateNotifications();
            return mNotifications.get(todaysFreeName());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void updateNotifications() {
        if (mNotifications == null) mNotifications = new HashMap<>();

        if (todaysFreeName().equals(mTagNotification) && !mNotifications.isEmpty()) return;

        mNotifications.clear();

        JsonObject books = getLibraryJson();
        if (books == null) return;

        JsonObject booksRoot = books.getAsJsonObject(U4_TAG);
        if (booksRoot == null) return;

        JsonObject dailiesFolder = booksRoot.getAsJsonObject(DAILIES_TAG);
        if (dailiesFolder == null) return;

        for (Map.Entry<String, JsonElement> entry : dailiesFolder.entrySet()) {
            String key = entry.getKey();
            JsonObject itemJson = entry.getValue().getAsJsonObject();

            JsonObject contents = itemJson.getAsJsonObject("Contents");
            String art = (contents != null && contents.has("Art")) ? relative(contents.get("Art").getAsString()) : null;
            String idx = (contents != null && contents.has("Idx")) ? relative(contents.get("Idx").getAsString()) : null;

            long publishDate = itemJson.has("PublishDate") ? itemJson.get("PublishDate").getAsLong() : 0L;
            String notification = itemJson.has("Notification") ? itemJson.get("Notification").getAsString() : null;

            if (publishDate > 0 && art != null && idx != null && notification != null) {
                mNotifications.put(key, new LibraryNotification(publishDate, notification));
            }
        }

        mTagNotification = todaysFreeName();
    }

    // -------------------- MAIN CACHE BUILD --------------------

    private void updateTagCache() {
        Log.d(TAG, "updateTagCache start");
        mTagMap = new HashMap<>();
        mColoredPictures.clear();

        File filesDir = GlobalApplication.getAppContext().getFilesDir();
        HashMap<String, ArrayList<ColoredPicture>> coloredMap = new HashMap<>();

        // 1) Load colored PNGs
        File[] pngs = filesDir.listFiles((dir, name) -> name != null && name.endsWith(".png"));

        if (pngs != null) {
            for (File f : pngs) {
                String fileName = f.getName();
                String baseName = fileName.replace(".png", "");

                int idx = fileName.indexOf("__");
                String groupKey = idx > 0 ? fileName.substring(0, idx) : baseName;

                ColoredPicture cp = new ColoredPicture(f.lastModified(), baseName, null);

                ArrayList<ColoredPicture> list = coloredMap.get(groupKey);
                if (list == null) {
                    list = new ArrayList<>();
                    coloredMap.put(groupKey, list);
                }
                list.add(cp);
                Collections.sort(list, new ColoredPictureComparator());
            }
        }

        // 2) Load scanned JPGs
        File scanDir = new File(filesDir, "scan");
        File[] scans = scanDir.listFiles((dir, name) -> name != null && name.endsWith(".jpg"));

        if (scans != null) {
            for (File f : scans) {
                addScannedFileToTagCache(f, coloredMap, false);
            }
        }

        // 3) Build from JSON
        JsonObject root = getLibraryJson();
        if (root == null) return;

        JsonObject books = root.getAsJsonObject(U4_TAG);
        if (books == null) return;

        String today = todaysFreeName();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, JsonElement> folderEntry : books.entrySet()) {
            String folderName = folderEntry.getKey();
            boolean isDailiesFolder = DAILIES_TAG.equals(folderName);

            JsonObject folderJson = folderEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> itemEntry : folderJson.entrySet()) {
                String itemName = itemEntry.getKey();
                JsonObject itemJson = itemEntry.getValue().getAsJsonObject();

                LibraryItem item = new LibraryItem(itemName);
                item.setTags(mapJsonArrayToList(itemJson.getAsJsonArray("Tags")));
                item.addTag(CAMPAIGN_TAG);
                item.mFolder = folderName;

                JsonObject contents = itemJson.getAsJsonObject("Contents");
                if (contents != null) {
                    if (contents.has("Art"))
                        item.artUrl = relative(contents.get("Art").getAsString());
                    if (contents.has("Idx"))
                        item.indexUrl = relative(contents.get("Idx").getAsString());
                    if (contents.has("Idx2"))
                        item.index2Url = relative(contents.get("Idx2").getAsString());
                    if (contents.has("Thumb"))
                        item.thumbUrl = relative(contents.get("Thumb").getAsString());

                    if (contents.has("Tobj")) {
                        item.config3D = new LibraryItem.Config3D();
                        item.config3D.tobjUrl = relative(contents.get("Tobj").getAsString());
                        if (contents.has("Normalmap"))
                            item.config3D.normalmapUrl = relative(contents.get("Normalmap").getAsString());
                        if (contents.has("Controlmap"))
                            item.config3D.controlmapUrl = relative(contents.get("Controlmap").getAsString());
                        if (contents.has("Envmap"))
                            item.config3D.envmapUrl = relative(contents.get("Envmap").getAsString());
                        if (contents.has("Sky_Background"))
                            item.config3D.skyBackgroundUrl = relative(contents.get("Sky_Background").getAsString());
                        if (contents.has("3DConfig"))
                            item.config3D.loadConfigurationFromJson(contents.get("3DConfig").getAsJsonObject());
                    }
                }

                if (itemJson.has("Created")) item.created = itemJson.get("Created").getAsLong();
                if (itemJson.has("PublishDate"))
                    item.mPublishDate = itemJson.get("PublishDate").getAsLong();
                if (itemJson.has("Position")) item.mPosition = itemJson.get("Position").getAsLong();

                // Must have art + idx
                if (item.indexUrl == null || item.artUrl == null) {
                    Log.d(TAG, "No art+idx, skipping " + itemName);
                    continue;
                }

                // Dailies logic (same idea as your original)
                if (isDailiesFolder && itemName.length() >= 8) {
                    try {
                        int dateInt = Integer.parseInt(itemName.substring(0, 8));
                        Calendar cal = GregorianCalendar.getInstance();
                        cal.clear();
                        cal.set(Calendar.YEAR, dateInt / 10000);
                        cal.set(Calendar.MONTH, ((dateInt / 100) % 100) - 1);
                        cal.set(Calendar.DAY_OF_MONTH, dateInt % 100);
                        cal.set(Calendar.HOUR_OF_DAY, 12);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);

                        if (now > cal.getTimeInMillis()) {
                            if (itemName.startsWith(today)) {
                                item.dailyFree = true;
                                item.addTag(DAILY_NEW_TAG);
                            }
                            item.addTag(DAILIES_TAG);
                        }
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }

                // Attach colored pics if exist
                ArrayList<ColoredPicture> pics = coloredMap.get(item.getName());
                if (pics != null) {
                    addColoredPicturesToItem(pics, item);
                    coloredMap.remove(item.getName());
                    item.addTag(FREE_TAG);
                }

                addLibraryItem(item, false);
            }
        }

        if (!coloredMap.isEmpty()) {
            Log.d(TAG, "Some colored pictures were not found from library: " + coloredMap.keySet());
        }

        Log.d(TAG, "updateTagCache sort");
        sortColoredPictures();
        sortLibraryItems();
        mTagCache = today;
        broadcastItemsChanged();
        Log.d(TAG, "updateTagCache done");
    }

    private void sortLibraryItems() {
        for (Map.Entry<String, ArrayList<LibraryItem>> entry : mTagMap.entrySet()) {
            try {
                if (POPULAR_TAG.equals(entry.getKey())) {
                    Collections.sort(entry.getValue(), new LibraryItem.PositionComparator());
                } else if (FREE_TAG.equals(entry.getKey())) {
                    Collections.sort(entry.getValue(), new LibraryItem.FreeCategoryComparator());
                } else if (DAILIES_TAG.equals(entry.getKey()) || DAILY_NEW_TAG.equals(entry.getKey())) {
                    Collections.sort(entry.getValue(), Collections.reverseOrder());
                } else {
                    Collections.shuffle(entry.getValue());
                }
            } catch (Exception ignore) {
            }
        }
    }

    private void addLibraryItem(LibraryItem item, boolean addOnTop) {
        if (item.getTags() == null) return;

        for (String tag : item.getTags()) {
            ArrayList<LibraryItem> list = mTagMap.get(tag);
            if (list == null) {
                list = new ArrayList<>();
                mTagMap.put(tag, list);
            }
            if (addOnTop) list.add(0, item);
            else list.add(item);
        }
    }

    public LibraryItem getItemByName(String name) {
        if (mTagCache == null) return null;

        for (ArrayList<LibraryItem> list : mTagMap.values()) {
            for (LibraryItem item : list) {
                if (item.getName().equals(name)) return item;
            }
        }
        return null;
    }

    // -------------------- DELETE SCANNED --------------------

    private void deleteScannedItem(LibraryItem item) {
        if (mTagCache == null) return;

        ArrayList<LibraryItem> scanned = mTagMap.get(SCANNED_TAG);
        if (scanned != null) {
            for (int i = 0; i < scanned.size(); i++) {
                if (scanned.get(i).getName().equals(item.getName())) {
                    scanned.remove(i);
                    break;
                }
            }
        }

        safeDeleteUriFile(item.artUrl);
        safeDeleteUriFile(item.thumbUrl);
        safeDeleteUriFile(item.indexUrl);
        safeDeleteUriFile(item.index2Url);
    }

    private void safeDeleteUriFile(String uriString) {
        if (uriString == null) return;
        try {
            if (uriString.startsWith("file:")) {
                File f = new File(URI.create(uriString));
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            } else {
                // if it's plain path
                File f = new File(uriString);
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Exception ignore) {
        }
    }

    // -------------------- URL HELPER --------------------

    private static String relative(String path) {
        // same logic you had, kept as-is
        String base = B_TAG + U1_TAG.substring(8) + "." + U2_TAG.substring(8) + "." + U3_TAG + U4_TAG + "/";

        if (path == null) return null;
        if (path.contains("://")) return path;
        return base + path;
    }

    // -------------------- SETTINGS / TAG PRIORITY --------------------

    public ArrayList<String> getTagPriorityList() {
        try {
            JsonObject json = getLibraryJsononline();
            if (json == null) return getTagPriorityListoff();

            JsonObject settings = json.getAsJsonObject("Settings");
            if (settings == null) return getTagPriorityListoff();

            ArrayList<String> list = mapJsonArrayToList(settings.getAsJsonArray("Tag Priority"));
            if (list == null || list.isEmpty()) return getTagPriorityListoff();

            // remove unwanted same as offline
            list.remove(FREE_TAG);
            list.remove(RECOMMENDED_TAG);
            list.remove("WWF");
            list.remove("my gallery");
            list.remove("new");
            list.remove(CAMPAIGN_TAG);

            File scanDir = new File(GlobalApplication.getAppContext().getFilesDir(), "scan");
            File[] scans = scanDir.listFiles();
            if (scans != null && scans.length > 0) list.add(0, SCANNED_TAG);

            list.add(0, DAILIES_TAG);

            if (!getInstance().todaysFreeName().equals(mTagCache)) {
                updateTagCache(); // (see next fix below)
            }
            return list;

        } catch (Exception e) {
            Log.e(TAG, "getTagPriorityList error", e);
            return getTagPriorityListoff();
        }
    }


    public ArrayList<String> getTagPriorityListoff() {
        JsonObject json = getLibraryJson();
        if (json == null) return null;

        ArrayList<String> list = mapJsonArrayToList(json.getAsJsonObject("Settings").getAsJsonArray("Tag Priority"));
        if (list == null) return null;

        list.remove(FREE_TAG);
        list.remove(RECOMMENDED_TAG);
        list.remove("WWF");
        list.remove("my gallery");
        list.remove("new");
        list.remove(CAMPAIGN_TAG);

        File scanDir = new File(GlobalApplication.getAppContext().getFilesDir(), "scan");
        File[] scans = scanDir.listFiles();
        if (scans != null && scans.length > 0) {
            list.add(0, SCANNED_TAG);
        }

        list.add(0, DAILIES_TAG);

        if (!getInstance().todaysFreeName().equals(mTagCache)) {
            updateTagCache();
        }
        return list;
    }

    @NonNull
    private ArrayList<String> mapJsonArrayToList(JsonArray arr) {
        ArrayList<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement e : arr) out.add(e.getAsString());
        return out;
    }

    // -------------------- COLORED PICTURES --------------------

    public ArrayList<ColoredPicture> getColoredPictures() {
        ArrayList<ColoredPicture> out = new ArrayList<>();
        for (ColoredPicture p : mColoredPictures) {
            if (!p.mScanned) out.add(p);
        }
        return out;
    }

    private void sortColoredPictures() {
        Collections.sort(mColoredPictures, new ColoredPictureComparator());
    }

    public ColoredPicture getColoredItemByBaseName(String baseName) {
        for (ColoredPicture p : mColoredPictures) {
            if (p.getBaseName().equals(baseName)) return p;
        }
        return null;
    }

    public void updateAddColoredPicture(LibraryItem item) {
        if (item.getColoredPicture() == null) {
            addNewColoredPicture(item);
            return;
        }

        long time = new Date().getTime();

        item.mColored.getDocumentFile().delete();
        item.mColored.getThumbFile().delete();

        item.mColored.set(time, item.getName() + "__" + time, item);

        sortColoredPictures();
        broadcastItemsChanged();
    }

    public void addNewColoredPicture(LibraryItem item) {
        long time = new Date().getTime();
        item.mColored = new ColoredPicture(time, item.getName() + "__" + time, item);
        mColoredPictures.add(item.mColored);
        sortColoredPictures();
        item.addTag(FREE_TAG);
        broadcastItemsChanged();
    }

    public void deleteColoredPicture(ColoredPicture picture) {
        picture.getDocumentFile().delete();
        picture.getThumbFile().delete();

        LibraryItem item = picture.getLibraryItem();
        if (item != null) item.mColored = null;

        mColoredPictures.remove(picture);

        // set latest colored for that item (if multiple)
        for (ColoredPicture p : mColoredPictures) {
            if (p.getLibraryItem() != null && item != null && p.getLibraryItem().getName().equals(item.getName())) {
                item.mColored = p;
                break;
            }
        }

        if (picture.mScanned && item != null && item.mColored == null) {
            deleteScannedItem(item);
        }

        broadcastItemsChanged();
    }

    // -------------------- BROADCAST --------------------

    public void broadcastItemsChanged() {
        mLibraryItemsChanged = true;
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.setData(Uri.fromParts("itemsChanged", "", null));
        LocalBroadcastManager.getInstance(GlobalApplication.getAppContext()).sendBroadcast(intent);
    }

    // -------------------- CACHE ENSURE (FIX FIRST LOAD) --------------------

    /**
     * Ensure Library cache is built before using colored pictures / tag map.
     * Safe to call many times.
     */
    public synchronized void ensureCacheLoaded() {
        String today = todaysFreeName();
        if (mTagCache == null || !today.equals(mTagCache)) {
            updateTagCache();
        }
    }

    /**
     * Background version (recommended to avoid heavy work on UI thread).
     */
    public void ensureCacheLoadedAsync(@NonNull Runnable onDoneUiThread) {
        new Thread(() -> {
            ensureCacheLoaded();
            // move result back to main thread
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(onDoneUiThread);
        }).start();
    }

}
