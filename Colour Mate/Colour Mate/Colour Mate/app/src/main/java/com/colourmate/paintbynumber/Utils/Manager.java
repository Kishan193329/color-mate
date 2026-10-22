package com.colourmate.paintbynumber.Utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.colourmate.paintbynumber.model.GalleryTimelineEntry;
import com.colourmate.paintbynumber.model.GalleryUser;
import com.colourmate.paintbynumber.model.model.Level;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class Manager {

    public static String BASE_URL;
    public static String B_URL;
    private static final MediaType JSON;
    public static final String METHOD_DELETE = "DELETE";
    public static String url_Exclusive_banner;
    public static String url_story;
    public static String url_exclusive;
    public static String REST_BASE_URL;
    private static final String TAG = "Manager";
    public static String giftURL;
    /**
     * @deprecated Kept only so nothing outside breaks; the real cursor now lives on each
     * {@link RestDataSource}. It used to be static, which meant every list in the app shared ONE
     * cursor — opening a second tab overwrote the first tab's position, so "load more" fetched the
     * wrong page or refused to fire at all.
     */
    @Deprecated
    public static String mNextKey;
    public static String serverMusic;
    public static String CONTENT_BASE_URL;
    private static Manager singleton = new Manager();

    private final FirebaseAuth.AuthStateListener mAuthListener = firebaseAuth -> Manager.this.fetchMyUserData();

    /** Content lists stay fresh for this long before the network is consulted again. */
    private static final int CACHE_FRESH_SECONDS = 180;
    /** How stale a cached list may be when there is no usable connection. */
    private static final int CACHE_OFFLINE_DAYS = 7;

    /** Pictures are immutable — a fileName is never reused (CLAUDE.md §3.5) — so cache them hard. */
    private static final int IMAGE_CACHE_DAYS = 30;
    private static final long IMAGE_CACHE_BYTES = 64L * 1024 * 1024;

    private volatile OkHttpClient mClient;
    private volatile OkHttpClient mImageClient;

    /**
     * The client Picasso downloads thumbnails and line art through.
     *
     * <p>Two things were wrong with letting Picasso use its own default client. It kept a second
     * connection pool and thread pool for the <em>same hosts</em> the API already talks to, so no
     * TLS session was ever reused between a list request and the pictures in that list. And the
     * content servers send no cache headers at all, which leaves an HTTP cache nothing to work
     * with — so scrolling back up a grid re-downloaded thumbnails the device had already seen.
     *
     * <p>So: derived from the API client with {@code newBuilder()}, which shares the expensive
     * parts (connection pool, dispatcher, socket factory), then given its own cache and its own
     * rules. The API's interceptors are dropped deliberately — a 180-second freshness window is
     * right for a list that gains new items and quite wrong for a picture that never changes.
     */
    public OkHttpClient imageClient() {
        OkHttpClient local = mImageClient;
        if (local != null) return local;
        synchronized (this) {
            if (mImageClient == null) {
                OkHttpClient.Builder builder = client().newBuilder();
                builder.interceptors().clear();
                builder.networkInterceptors().clear();

                try {
                    Context ctx = com.colourmate.paintbynumber.GlobalApplication.getAppContext();
                    if (ctx != null) {
                        builder.cache(new okhttp3.Cache(
                                new java.io.File(ctx.getCacheDir(), "image-cache"),
                                IMAGE_CACHE_BYTES));

                        builder.addNetworkInterceptor(chain -> chain.proceed(chain.request())
                                .newBuilder()
                                .removeHeader("Pragma")
                                .removeHeader("Cache-Control")
                                .header("Cache-Control", "public, max-age="
                                        + (IMAGE_CACHE_DAYS * 24 * 60 * 60) + ", immutable")
                                .build());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Image cache unavailable, continuing without it", e);
                }

                mImageClient = builder.build();
            }
            return mImageClient;
        }
    }

    /**
     * Built lazily because the disk cache needs an application context, and this class can be
     * touched before {@code GlobalApplication.onCreate()} has finished.
     */
    private OkHttpClient client() {
        OkHttpClient local = mClient;
        if (local != null) return local;
        synchronized (this) {
            if (mClient == null) {
                mClient = buildClient();
            }
            return mClient;
        }
    }

    private OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);

        try {
            Context ctx = com.colourmate.paintbynumber.GlobalApplication.getAppContext();
            if (ctx != null) {
                builder.cache(new okhttp3.Cache(
                        new java.io.File(ctx.getCacheDir(), "api-cache"), 12L * 1024 * 1024));

                // The content servers send no cache headers, so every tab switch re-downloaded a
                // list the app already had. Give responses a short freshness window on the way in.
                builder.addNetworkInterceptor(chain -> chain.proceed(chain.request())
                        .newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Cache-Control")
                        .header("Cache-Control", "public, max-age=" + CACHE_FRESH_SECONDS)
                        .build());

                // With no usable connection, serve the last known list instead of an empty screen.
                builder.addInterceptor(chain -> {
                    Request request = chain.request();
                    if (!hasInternetConnection(ctx)) {
                        request = request.newBuilder()
                                .removeHeader("Pragma")
                                .cacheControl(new okhttp3.CacheControl.Builder()
                                        .onlyIfCached()
                                        .maxStale(CACHE_OFFLINE_DAYS, TimeUnit.DAYS)
                                        .build())
                                .build();
                    }
                    return chain.proceed(request);
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "HTTP cache unavailable, continuing without it", e);
        }

        return builder.build();
    }

    private GalleryUser mMe;
    private final ArrayList<MyStatusListener> mMyListeners = new ArrayList<>();
    private FirebaseRemoteConfig mRemoteConfig;

    public interface MyStatusListener {
        void myStatusChanged(GalleryUser galleryUser);
    }

    public interface OnRestCompletedListener {
        void onCompleted(JsonObject jsonObject);
    }

    // ✅ NEW: Add error listener interface
    public interface OnRestCompletedWithErrorListener {
        void onCompleted(JsonObject jsonObject);
        void onError(String errorMessage);
    }

    public interface SingleUserListener {
        void onUserFetched(String key, GalleryUser galleryUser);
    }

    // Native methods...
    public static native String GetBUrl();
    public static native String GetGiftUrl();
    public static native String GetMainUrl();
    public static native String GetMusicUrl();
    public static native String GetRestUrl();
    public static native String AnimalURL();
    public static native String FlowerURL();
    public static native String GetExclusive();
    public static native String GetExclusiveBanner();
    public static native String GetStory();
    public static native String KidURL();
    public static native String LoveURL();
    public static native String MandalaURL();
    public static native String NatureURL();
    public static native String PeopleURL();
    public static native String GetContentBaseUrl();

    static {
        try {
            System.loadLibrary("colorbynumber");
        } catch (Exception ignored) {
        }

        JSON = MediaType.parse("application/json; charset=utf-8");
        BASE_URL = GetMainUrl();
        REST_BASE_URL = BASE_URL + GetRestUrl();
        B_URL = BASE_URL + GetBUrl();
        giftURL = GetGiftUrl();
        serverMusic = GetMusicUrl();
        url_Exclusive_banner = GetExclusiveBanner();
        url_story = GetStory();
        url_exclusive = GetExclusive();
        CONTENT_BASE_URL = GetContentBaseUrl();
        singleton = new Manager();
    }

    private boolean initialized = false;

    /**
     * Idempotent: called from both GlobalApplication.onCreate() and
     * MainActivity.onCreate(), which used to register the auth listener twice.
     */
    public void initialize() {
        if (initialized) return;
        initialized = true;
        FirebaseAuth.getInstance().addAuthStateListener(this.mAuthListener);
    }

    public static Manager getInstance() {
        return singleton;
    }

    private Manager() {
        try {
            this.mRemoteConfig = FirebaseRemoteConfig.getInstance();
            this.mRemoteConfig.fetch().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Manager.this.mRemoteConfig.activate();
                } else {
                    Log.d(Manager.TAG, "Remote config fetch failed.");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Manager init error: " + e.getMessage());
        } catch (ExceptionInInitializerError e2) {
            Log.e(TAG, "Manager init error: " + e2.getMessage());
        }
    }

    public boolean hasInternetConnection(Context context) {
        if (context == null) return false;

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
    }

    // =====================================================
    // ✅ FIXED: RestDataSource with proper error handling
    // =====================================================
    public static class RestDataSource<T> {
        private volatile boolean mAbort;
        private final int mAuthMode;
        private final ArrayList<T> mItems = new ArrayList<>();
        private final ArrayList<String> mKeys = new ArrayList<>();
        private final ArrayList<RestDataSourceListener> mListeners = new ArrayList<>();
        private String mPath;
        private final Class<T> mType;
        private int method = 0;
        /** Null = use the global B_URL (ionicsolutions). Set for the ColorForge tab. */
        private String baseUrlOverride;

        /**
         * By Number mix: when set, OUR server's pictures for the same category are fetched in
         * parallel and shown ABOVE the paid list. The paid list never waits for them — if our
         * server is slow or down the paid content appears exactly as before, and ours pop in on
         * top whenever (if ever) they arrive. Owner's rule: new pictures sit at the top.
         */
        private String mixRoot;
        private final ArrayList<T> mixItems = new ArrayList<>();
        private final ArrayList<String> mixKeys = new ArrayList<>();
        private boolean paidLoaded = false;
        /** How many of mixItems currently sit at the front of mItems (so a re-load replaces them). */
        private int mixInList = 0;

        public void setMixRoot(String root) {
            this.mixRoot = (root == null || root.isEmpty()) ? null : root;
        }

        // ✅ NEW: Track loading state
        private volatile boolean isLoading = false;
        private Call currentCall;

        // ✅ NEW: Error tracking
        private String lastError = null;
        private boolean hasError = false;

        /** This list's own pagination cursor — see the note on {@link Manager#mNextKey}. */
        private String nextKey;

        /** Transient network failures get a couple of quiet retries before the user sees nothing. */
        private static final int MAX_RETRIES = 2;
        private int retryCount = 0;
        private final Handler retryHandler = new Handler(Looper.getMainLooper());

        // ✅ UPDATED: Interface with error callback
        public interface RestDataSourceListener {
            void onItemsLoaded();

            // Default method for backward compatibility
            default void onError(String error) {
                Log.e(TAG, "DataSource error: " + error);
            }
        }

        private final OnRestCompletedListener mRestCompletedListener = jsonObject -> {
            // Process results in the background (caller thread)
            if (mAbort) return;

            if (jsonObject != null) {
                hasError = false;
                lastError = null;
                // mNextKey is now handled in parseAndAddResults or locally
                
                if (jsonObject.has("data")) {
                    mItems.clear();
                    mKeys.clear();
                    parseAndAddResults(jsonObject.getAsJsonArray("data"));
                    mItems.addAll(0, mixItems);
                    mKeys.addAll(0, mixKeys);
                    mixInList = mixItems.size();
                    paidLoaded = true;
                }
                
                if (jsonObject.has("next") && !jsonObject.get("next").isJsonNull()) {
                    nextKey = jsonObject.get("next").getAsString();
                } else {
                    nextKey = null;
                }
                retryCount = 0;
            } else {
                hasError = true;
                lastError = "Null response";

                // A DNS hiccup or a dropped connection used to leave the grid empty until the user
                // pulled to refresh. Try again a couple of times, backing off, before giving up.
                if (retryCount < MAX_RETRIES && !mAbort) {
                    retryCount++;
                    long delay = 600L * retryCount;
                    Log.w(TAG, "Load failed for " + mPath + " — retry " + retryCount + " in " + delay + "ms");
                    isLoading = false;
                    retryHandler.postDelayed(this::refresh, delay);
                    return;
                }
            }

            isLoading = false;
            notifyListenersOfDataLoad();
        };

        private final OnRestCompletedListener mRestLoadMoreCompletedListener = jsonObject -> {
            if (mAbort) return;

            if (jsonObject != null) {
                hasError = false;
                if (jsonObject.has("data")) {
                    parseAndAddResults(jsonObject.getAsJsonArray("data"));
                }
                
                if (jsonObject.has("next") && !jsonObject.get("next").isJsonNull()) {
                    nextKey = jsonObject.get("next").getAsString();
                } else {
                    nextKey = null;
                }
            }

            isLoading = false;
            notifyListenersOfDataLoad();
        };

        public RestDataSource(Class<T> cls, String path, int authMode) {
            this.mType = cls;
            this.mPath = path;
            Log.d(Manager.TAG, "RestDataSource: mPath----->" + this.mPath);
            this.mAuthMode = authMode;
        }

        public RestDataSource(Class<T> cls, String path, int authMode, int method) {
            this(cls, path, authMode, method, null);
        }

        /**
         * @param baseUrlOverride REST root for this source only; null keeps the global B_URL.
         */
        public RestDataSource(Class<T> cls, String path, int authMode, int method,
                              String baseUrlOverride) {
            this.mType = cls;
            this.mPath = path;
            this.mAuthMode = authMode;
            this.method = method;
            this.baseUrlOverride = baseUrlOverride;
            Log.d(Manager.TAG, "RestDataSource: mPath----->" + this.mPath);
        }

        public void refresh() {
            if (mAbort) {
                Log.d(TAG, "Refresh called but already aborted");
                return;
            }

            if (isLoading) {
                Log.d(TAG, "Refresh called but already loading");
                return;
            }

            isLoading = true;
            hasError = false;
            lastError = null;

            Log.d(TAG, "Starting refresh for: " + mPath);

            if (mixRoot != null && this.method == 1) {
                paidLoaded = false;
                Manager.getInstance().executeApiRequestWithCall(mixRoot, this.mPath,
                        this::onMixLoaded, call -> { });
            }

            if (this.method == 1) {
                Manager.getInstance().executeApiRequestWithCall(this.baseUrlOverride, this.mPath,
                        this.mRestCompletedListener, call -> {
                            this.currentCall = call;
                        });
            } else {
                Manager.getInstance().executeRestRequest("GET", this.mPath, shouldUseAuthentication(), this.mRestCompletedListener);
            }
        }

        public ArrayList<T> getItems() {
            return new ArrayList<>(this.mItems); // Return copy to prevent modification
        }

        public ArrayList<String> getKeys() {
            return new ArrayList<>(this.mKeys);
        }

        // ✅ NEW: Check if has data
        public boolean hasData() {
            return !mItems.isEmpty();
        }

        // ✅ NEW: Check if has error
        public boolean hasError() {
            return hasError;
        }

        // ✅ NEW: Get last error
        public String getLastError() {
            return lastError;
        }

        // ✅ NEW: Check if loading
        public boolean isLoading() {
            return isLoading;
        }

        public void addListener(RestDataSourceListener listener) {
            if (listener != null && !mListeners.contains(listener)) {
                this.mListeners.add(listener);
            }
        }

        public void removeListener(RestDataSourceListener listener) {
            this.mListeners.remove(listener);
        }

        // ✅ NEW: Clear all listeners
        public void clearListeners() {
            this.mListeners.clear();
        }

        public void loadMore() {
            if (mAbort || isLoading) return;

            if (nextKey == null) {
                return;
            }

            isLoading = true;

            if (this.method == 1) {
                Manager.getInstance().executeApiRequest(this.mPath + "?next=" + nextKey, this.mRestLoadMoreCompletedListener);
            } else {
                Log.d(Manager.TAG, "loadMore: mPath " + this.mPath);
                Manager.getInstance().executeRestRequest("GET", this.mPath + "?next=" + nextKey, shouldUseAuthentication(), this.mRestLoadMoreCompletedListener);
            }
        }

        public boolean canLoadMore() {
            return nextKey != null && !isLoading && !mAbort;
        }

        public void abort() {
            Log.d(TAG, "Aborting data source: " + mPath);
            this.mAbort = true;
            this.isLoading = false;
            this.retryHandler.removeCallbacksAndMessages(null);

            // Cancel current network call if exists
            if (currentCall != null) {
                currentCall.cancel();
                currentCall = null;
            }

            // Clear listeners to prevent memory leaks
            clearListeners();
        }

        private boolean shouldUseAuthentication() {
            boolean loggedIn = Manager.getInstance().getCurrentUserId() != null;
            if (mAuthMode == 0) return false;
            if (mAuthMode == 1) return loggedIn;
            return true;
        }

        /** Our server's list for this category. Runs on the main thread (like every listener). */
        @SuppressWarnings("unchecked")
        private void onMixLoaded(JsonObject json) {
            if (mAbort || json == null || !json.has("data") || !mType.equals(Level.class)) return;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date now = new Date();
            ArrayList<T> items = new ArrayList<>();
            ArrayList<String> keys = new ArrayList<>();
            for (JsonElement el : json.getAsJsonArray("data")) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("key")) continue;
                    Level lv = Level.fromJson(o);
                    if (lv == null) continue;
                    String ts = lv.getTimeStamp();
                    if (ts != null && !ts.isEmpty()) {
                        Date d = sdf.parse(ts);
                        if (d != null && d.after(now)) continue;     // scheduled for later
                    }
                    lv.setColorForge(true);
                    lv.setDiamond(false);
                    items.add((T) lv);
                    keys.add(o.get("key").getAsString());
                } catch (Exception e) {
                    Log.e(TAG, "Mix item skipped: " + e.getMessage());
                }
            }
            mixItems.clear();
            mixKeys.clear();
            mixItems.addAll(items);
            mixKeys.addAll(keys);
            if (paidLoaded) {
                for (int i = 0; i < mixInList && !mItems.isEmpty(); i++) {
                    mItems.remove(0);
                    mKeys.remove(0);
                }
                mItems.addAll(0, items);
                mKeys.addAll(0, keys);
                mixInList = items.size();
                notifyListenersOfDataLoad();
            }
            Log.d(TAG, "Mix: " + items.size() + " of our pictures on top of " + mPath);
        }

        private void parseAndAddResults(JsonArray arr) {
            if (mAbort || arr == null) {
                Log.w(TAG, "parseAndAddResults: abort=" + mAbort + ", arr=" + (arr == null ? "null" : "valid"));
                return;
            }

            int addedCount = 0;
            final int startIndex = mItems.size();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date now = new Date();

            for (JsonElement el : arr) {
                if (mAbort) break;

                try {
                    JsonObject jsonObj = el.getAsJsonObject();

                    if (!jsonObj.has("key")) continue;

                    String key = jsonObj.get("key").getAsString();
                    Object obj = null;

                    if (mType.equals(GalleryTimelineEntry.class)) {
                        obj = GalleryTimelineEntry.fromJson(jsonObj);
                    } else if (mType.equals(Level.class)) {
                        Level lv = Level.fromJson(jsonObj);
                        if (lv != null) {
                            // Filter future dates
                            String ts = lv.getTimeStamp();
                            if (ts != null && !ts.isEmpty()) {
                                try {
                                    Date date = sdf.parse(ts);
                                    if (date != null && date.after(now)) {
                                        key = null; // Skip
                                    }
                                } catch (ParseException ignored) {}
                            }
                            // Stamped here (not by the caller) so every path through this source
                            // carries the flag — GameViewModel needs it to pick the right host.
                            boolean ours = baseUrlOverride != null && !baseUrlOverride.isEmpty();
                            lv.setColorForge(ours);
                            // The Diamond feed shares this source but not its asset layout, so the
                            // root itself says which one this is.
                            lv.setDiamond(ours && baseUrlOverride.contains("/Diamond/"));
                            obj = lv;
                        }
                    }

                    if (obj != null && key != null) {
                        mKeys.add(key);
                        @SuppressWarnings("unchecked")
                        T item = (T) obj;
                        mItems.add(item);
                        addedCount++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing item: " + e.getMessage());
                }
            }

            applyDailyRotation(startIndex);

            Log.d(TAG, "Parsed " + addedCount + " items from " + arr.size() + " elements");
        }

        /**
         * Rearranges the page that just arrived so the galleries look different from one day to the
         * next. See {@link com.colourmate.paintbynumber.util.DailyRotation} — the arrangement holds
         * for 24 hours, so a refresh or a restart inside the period shows exactly what it did
         * before.
         *
         * <p>Picture lists only. The By Images tab is a timeline whose order carries meaning, so
         * it is left in the order the server sent.
         */
        private void applyDailyRotation(int startIndex) {
            if (!mType.equals(Level.class)) return;

            Context ctx = com.colourmate.paintbynumber.GlobalApplication.getAppContext();
            if (ctx == null) return;

            long seed = com.colourmate.paintbynumber.util.DailyRotation.pageSeed(
                    com.colourmate.paintbynumber.util.DailyRotation.seed(ctx), mPath, startIndex);
            com.colourmate.paintbynumber.util.DailyRotation.shuffleRange(
                    mItems, mKeys, startIndex, mItems.size(), seed);
        }

        private void notifyListenersOfDataLoad() {
            if (mAbort) return;

            // Always notify on the UI thread to prevent UI exceptions in fragments/activities
            new Handler(Looper.getMainLooper()).post(() -> {
                if (mAbort) return;
                
                List<RestDataSourceListener> listenersCopy = new ArrayList<>(mListeners);
                for (RestDataSourceListener listener : listenersCopy) {
                    try {
                        if (hasError) {
                            listener.onError(lastError != null ? lastError : "Unknown error");
                        }
                        listener.onItemsLoaded();
                    } catch (Exception e) {
                        Log.e(TAG, "Error notifying listener: " + e.getMessage());
                    }
                }
            });
        }
    }

    // =====================================================
    // ✅ NEW: API request with Call reference for cancellation
    // =====================================================
    public interface CallCallback {
        void onCallCreated(Call call);
    }

    public void executeApiRequestWithCall(String path, final OnRestCompletedListener listener, CallCallback callCallback) {
        executeApiRequestWithCall(null, path, listener, callCallback);
    }

    /**
     * @param baseUrlOverride REST root to use instead of {@link #B_URL}. Null/empty keeps the
     *                        original ionicsolutions behaviour — see ColorForgeApi for why the
     *                        "New 2026" tab needs its own root.
     */
    public void executeApiRequestWithCall(String baseUrlOverride, String path,
                                          final OnRestCompletedListener listener,
                                          CallCallback callCallback) {
        String root = (baseUrlOverride == null || baseUrlOverride.isEmpty()) ? B_URL : baseUrlOverride;

        Request.Builder builder = new Request.Builder();
        builder.url(root + path);
        builder.get();

        Call call = client().newCall(builder.build());

        if (callCallback != null) {
            callCallback.onCallCreated(call);
        }

        call.enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException exception) {
                if (call.isCanceled()) {
                    Log.d(TAG, "Call was canceled: " + path);
                    return;
                }
                Log.e(TAG, "API request failed: " + path + " - " + exception.getMessage());
                Manager.this.processRestResponse(exception, null, this.mainHandler, listener);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (call.isCanceled()) {
                    response.close();
                    return;
                }
                Manager.this.processRestResponse(null, response, this.mainHandler, listener);
            }
        });
    }

    public void executeApiRequest(String path, final OnRestCompletedListener listener) {
        executeApiRequestWithCall(path, listener, null);
    }

    // =====================================================
    // ✅ IMPROVED: REST request with better error handling
    // =====================================================
    private void executeRestRequest(String method, String path, boolean useAuth, OnRestCompletedListener listener) {
        if (listener == null) {
            Log.e(TAG, "executeRestRequest: listener is null");
            return;
        }

        if (useAuth) {
            executeAuthenticatedRestRequest(method, path, null, listener);
        } else {
            executeUnauthenticatedRestRequest(method, path, null, listener);
        }
    }

    private void executeUnauthenticatedRestRequest(String method, String path, JsonObject jsonBody, final OnRestCompletedListener listener) {
        Request.Builder requestBuilder = buildRequest(method, path, jsonBody);

        if (requestBuilder == null) {
            Log.e(TAG, "Failed to build request for: " + path);
            new Handler(Looper.getMainLooper()).post(() -> listener.onCompleted(null));
            return;
        }

        client().newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException exception) {
                if (call.isCanceled()) return;
                // Don't force main thread here, invokeListenerOnMainThread will handle it
                Manager.this.processRestResponse(exception, null, null, listener);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (call.isCanceled()) {
                    response.close();
                    return;
                }
                Manager.this.processRestResponse(null, response, null, listener);
            }
        });
    }

    private void executeAuthenticatedRestRequest(final String method, final String path, final JsonObject jsonBody, final OnRestCompletedListener listener) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for request: " + path);
            new Handler(Looper.getMainLooper()).post(() -> listener.onCompleted(null));
            return;
        }

        currentUser.getIdToken(false)
                .addOnFailureListener(exc -> {
                    Log.e(TAG, "Failed to get auth token: " + exc.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> listener.onCompleted(null));
                })
                .addOnSuccessListener(tokenResult -> {
                    Request.Builder requestBuilder = Manager.this.buildRequest(method, path, jsonBody);

                    if (requestBuilder == null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onCompleted(null));
                        return;
                    }

                    requestBuilder.header("Authorization", "Bearer " + tokenResult.getToken());

                    Manager.this.client().newCall(requestBuilder.build()).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException exception) {
                            if (call.isCanceled()) return;
                            Manager.this.processRestResponse(exception, null, null, listener);
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) {
                            if (call.isCanceled()) {
                                response.close();
                                return;
                            }
                            Manager.this.processRestResponse(null, response, null, listener);
                        }
                    });
                });
    }

    private Request.Builder buildRequest(String method, String path, JsonObject jsonBody) {
        try {
            Request.Builder builder = new Request.Builder();
            builder.url(REST_BASE_URL + path);

            switch (method) {
                case "GET":
                    builder.get();
                    break;
                case "POST":
                    builder.post(RequestBody.create(JSON, jsonBody != null ? new Gson().toJson(jsonBody) : ""));
                    break;
                case "PUT":
                    builder.put(RequestBody.create(JSON, jsonBody != null ? new Gson().toJson(jsonBody) : ""));
                    break;
                case METHOD_DELETE:
                    if (jsonBody != null) {
                        builder.delete(RequestBody.create(JSON, new Gson().toJson(jsonBody)));
                    } else {
                        builder.delete();
                    }
                    break;
                default:
                    Log.e(TAG, "Unhandled method: " + method);
                    return null;
            }

            return builder;
        } catch (Exception e) {
            Log.e(TAG, "Failed to build request: " + e.getMessage());
            return null;
        }
    }

    private void processRestResponse(Exception exception, Response response, Handler handler, OnRestCompletedListener callback) {
        JsonObject json = null;

        try {
            if (exception != null) {
                Log.e(TAG, "Request exception: " + exception.getMessage());
                invokeListenerOnMainThread(handler, null, callback);
                return;
            }

            if (response == null) {
                Log.e(TAG, "Response is null");
                invokeListenerOnMainThread(handler, null, callback);
                return;
            }

            if (!response.isSuccessful()) {
                Log.e(TAG, "Response not successful: " + response.code() + " " + response.message());
                response.close();
                invokeListenerOnMainThread(handler, null, callback);
                return;
            }

            ResponseBody body = response.body();
            if (body == null) {
                Log.e(TAG, "Response body is null");
                response.close();
                invokeListenerOnMainThread(handler, null, callback);
                return;
            }

            String responseString = body.string();
            body.close();
            response.close();

            if (responseString == null || responseString.isEmpty()) {
                Log.e(TAG, "Response string is empty");
                invokeListenerOnMainThread(handler, null, callback);
                return;
            }

            json = JsonParser.parseString(responseString).getAsJsonObject();
            Log.d(TAG, "Successfully parsed response");

        } catch (JsonSyntaxException e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "IO error reading response: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error: " + e.getMessage());
        }

        invokeListenerOnMainThread(handler, json, callback);
    }

    private void invokeListenerOnMainThread(Handler handler, final JsonObject jsonObject, final OnRestCompletedListener listener) {
        if (listener == null) return;

        // Note: RestDataSource now handles its own UI thread switching in notifyListenersOfDataLoad().
        // For other callers, we still post normally.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onCompleted(jsonObject);
        } else if (handler != null) {
            handler.post(() -> listener.onCompleted(jsonObject));
        } else {
            // If no handler, execute on caller thread (usually background)
            listener.onCompleted(jsonObject);
        }
    }

    // Rest of your methods remain the same...
    public void fetchUserById(String userId, @NonNull final SingleUserListener listener) {
        if (userId == null) {
            listener.onUserFetched(null, null);
            return;
        }
        listener.onUserFetched(null, null);
    }

    public void setUserLoginData(String uid, GalleryUser user) {
        if (uid == null || user == null) return;
        setCurrentUser(user);
    }

    public void registerDeviceToken(String uid) {
        if (uid == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e("FCM", "Token fetch failed", task.getException());
                        return;
                    }

                    String token = task.getResult();

                    JsonObject json = new JsonObject();
                    json.addProperty("registerUser", uid);
                    json.addProperty("registerId", token);

                    executeAuthenticatedRestRequest("POST", "authtoken", json, res -> { });
                });
    }

    private void fetchMyUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            fetchUserById(user.getUid(), (key, u) -> {
                setUserLoginData(key, u);
                registerDeviceToken(key);
            });
        } else {
            setCurrentUser(null);
        }
    }

    private void setCurrentUser(GalleryUser user) {
        this.mMe = user;
        notifyMyStatusChanged();
    }

    public String getCurrentUserId() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || this.mMe == null) {
            return null;
        }
        return currentUser.getUid();
    }

    private void notifyMyStatusChanged() {
        for (MyStatusListener listener : this.mMyListeners) {
            listener.myStatusChanged(this.mMe);
        }
    }

    public void addMyStatusListener(MyStatusListener listener) {
        if (listener != null && !this.mMyListeners.contains(listener)) {
            this.mMyListeners.add(listener);
        }
    }

    public void removeMyStatusListener(MyStatusListener listener) {
        this.mMyListeners.remove(listener);
    }

    public RestDataSource<Level> getImagesByCategory(String category) {
        RestDataSource<Level> ds = new RestDataSource<>(Level.class, "gimg/c/" + category, 0, 1);
        Context ctx = com.colourmate.paintbynumber.GlobalApplication.getAppContext();
        if (ctx != null && ColorForgeApi.hasExplicitServer(ctx)) {
            ds.setMixRoot(ColorForgeApi.restRoot(ctx));       // our new pictures, on top
        }
        return ds;
    }

    /**
     * Same gallery call as {@link #getImagesByCategory}, but served by ColorForge instead of
     * ionicsolutions — this is what feeds the "New 2026" tab.
     *
     * @return null when no ColorForge server is configured yet (see ColorForgeApi).
     */
    public RestDataSource<Level> getColorForgeImagesByCategory(Context context, String category) {
        String root = ColorForgeApi.restRoot(context);
        if (root.isEmpty()) return null;

        return new RestDataSource<>(Level.class, "gimg/c/" + category, 0, 1, root);
    }

    /** Same list flow for the Diamond tab — only the REST root differs (diamond_tab.py). */
    public RestDataSource<Level> getDiamondImagesByCategory(Context context, String category) {
        String root = ColorForgeApi.diamondRestRoot(context);
        if (root.isEmpty()) return null;

        return new RestDataSource<>(Level.class, "gimg/c/" + category, 0, 1, root);
    }
}