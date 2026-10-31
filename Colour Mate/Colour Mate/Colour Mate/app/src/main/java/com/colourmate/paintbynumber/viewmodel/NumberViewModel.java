package com.colourmate.paintbynumber.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.util.LogUtils;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class NumberViewModel extends AndroidViewModel {

    private static final String TAG = "NumberViewModel";
    private static final String DAILY_CAT = "DAILY";
    private static final int DOWNLOAD_TIMEOUT_MS = 30000;

    // ─── Loading State ───
    public enum LoadState {
        IDLE, LOADING, SUCCESS, ERROR, TIMEOUT, NO_INTERNET
    }

    // ─── LiveData ───
    private final MutableLiveData<LoadState> loadState = new MutableLiveData<>(LoadState.IDLE);
    private final MutableLiveData<ArrayList<String>> displayCategories = new MutableLiveData<>();
    private final MutableLiveData<List<Level>> dailyLevels = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> currentSearchQuery = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isRefreshing = new MutableLiveData<>(false);

    // Events
    private final SingleLiveEvent<String> toastMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> snackbarMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<Level> navigateToGame = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showBackAd = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showInterstitialForDaily = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> scrollToFirstTab = new SingleLiveEvent<>();

    // Internal State
    private final ArrayList<String> masterCategories = new ArrayList<>();
    private final List<Level> dailyLevelsList = new ArrayList<>();
    private final OkHttpClient httpClient;
    private Call currentCall;
    private android.os.Handler timeoutHandler;
    private Runnable timeoutRunnable;

    public NumberViewModel(@NonNull Application application) {
        super(application);
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    }

    // ─── Getters ───

    public LiveData<LoadState> getLoadState() { return loadState; }
    public LiveData<ArrayList<String>> getDisplayCategories() { return displayCategories; }
    public LiveData<List<Level>> getDailyLevels() { return dailyLevels; }
    public LiveData<String> getCurrentSearchQuery() { return currentSearchQuery; }
    public LiveData<Boolean> getIsRefreshing() { return isRefreshing; }
    public SingleLiveEvent<String> getToastMessage() { return toastMessage; }
    public SingleLiveEvent<String> getSnackbarMessage() { return snackbarMessage; }
    public SingleLiveEvent<Level> getNavigateToGame() { return navigateToGame; }
    public SingleLiveEvent<Void> getShowBackAd() { return showBackAd; }
    public SingleLiveEvent<Void> getShowInterstitialForDaily() { return showInterstitialForDaily; }
    public SingleLiveEvent<Void> getScrollToFirstTab() { return scrollToFirstTab; }
    public ArrayList<String> getMasterCategories() { return masterCategories; }

    // ─── Initial Load ───

    public void initialize() {
        if (hasInternet()) {
            downloadCategories();
        } else {
            loadState.setValue(LoadState.NO_INTERNET);
        }
    }

    // ─── Category Download ───

    public void downloadCategories() {
        cancelTimeout();
        loadState.setValue(LoadState.LOADING);

        Request request = new Request.Builder()
                .url(Manager.B_URL + "category/")
                .build();

        if (currentCall != null) {
            currentCall.cancel();
        }
        currentCall = httpClient.newCall(request);

        startTimeout();

        currentCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                cancelTimeout();

                if (call.isCanceled()) {
                    Log.d(TAG, "Call was canceled");
                    return;
                }

                postLoadState(LoadState.ERROR);
                isRefreshing.postValue(false);
                snackbarMessage.postValue("Connection failed");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                cancelTimeout();

                if (call.isCanceled()) {
                    response.close();
                    return;
                }

                ResponseBody body = response.body();

                try {
                    if (!response.isSuccessful()) {
                        onFailure(call, new IOException("Unexpected code " + response));
                        return;
                    }

                    if (body == null) {
                        onFailure(call, new IOException("Empty response body"));
                        return;
                    }

                    String json = body.string();
                    if (json == null || json.isEmpty()) {
                        onFailure(call, new IOException("Empty JSON"));
                        return;
                    }

                    JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();

                    if (!jsonObj.has("categories")) {
                        onFailure(call, new IOException("Missing categories"));
                        return;
                    }

                    JsonArray jsonCategories = jsonObj.getAsJsonArray("categories");
                    if (jsonCategories == null || jsonCategories.isEmpty()) {
                        onFailure(call, new IOException("Empty categories"));
                        return;
                    }

                    ArrayList<String> categories = new GsonBuilder()
                            .setLenient()
                            .create()
                            .fromJson(jsonCategories,
                                    new TypeToken<ArrayList<String>>() {}.getType());

                    if (categories == null || categories.isEmpty()) {
                        onFailure(call, new IOException("Empty categories list"));
                        return;
                    }

                    // Store master list
                    masterCategories.clear();
                    masterCategories.addAll(categories);

                    // Reset search
                    currentSearchQuery.postValue("");

                    // Build display list
                    ArrayList<String> display = buildDisplayList(masterCategories);
                    displayCategories.postValue(display);

                    postLoadState(LoadState.SUCCESS);
                    isRefreshing.postValue(false);

                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "JSON parsing error", e);
                    onFailure(call, new IOException("JSON parsing error"));
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error", e);
                    onFailure(call, new IOException(e));
                } finally {
                    if (body != null) body.close();
                    response.close();
                }
            }
        });
    }

    // ─── Refresh ───

    public void onRefresh() {
        if (!hasInternet()) {
            loadState.setValue(LoadState.NO_INTERNET);
            isRefreshing.setValue(false);
            snackbarMessage.setValue("No internet connection");
            return;
        }
        isRefreshing.setValue(true);
        downloadCategories();
    }

    public void onRetryClicked() {
        if (hasInternet()) {
            downloadCategories();
        } else {
            snackbarMessage.setValue("No internet connection");
        }
    }

    // ─── Search ───

    public void search(String query) {
        currentSearchQuery.setValue(query);

        ArrayList<String> filtered = new ArrayList<>();

        if (query == null || query.isEmpty()) {
            filtered.addAll(masterCategories);
        } else {
            String lowerQuery = query.toLowerCase();
            for (String cat : masterCategories) {
                if (cat.toLowerCase().contains(lowerQuery)) {
                    filtered.add(cat);
                }
            }
        }

        ArrayList<String> display = buildDisplayList(filtered);
        displayCategories.setValue(display);

        if (filtered.isEmpty() && !query.isEmpty()) {
            loadState.setValue(LoadState.ERROR);
        } else {
            loadState.setValue(LoadState.SUCCESS);
        }
    }

    // ─── Daily Banner ───

    public void loadDailyImages() {
        if (!hasInternet()) return;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());

        SharedPreferences prefs = getApplication()
                .getSharedPreferences("daily_banner", android.content.Context.MODE_PRIVATE);

        String cachedDate = prefs.getString("date", "");
        String cachedFn1 = prefs.getString("fn1", "");
        String cachedCat1 = prefs.getString("cat1", "");
        String cachedFn2 = prefs.getString("fn2", "");
        String cachedCat2 = prefs.getString("cat2", "");

        // Use cached data if available for today
        if (today.equals(cachedDate) && !cachedFn1.isEmpty()) {
            dailyLevelsList.clear();
            Level l1 = new Level(null, cachedFn1, null, cachedCat1);
            l1.setColorForge(prefs.getBoolean("cf1", false));
            dailyLevelsList.add(l1);
            if (!cachedFn2.isEmpty()) {
                Level l2 = new Level(null, cachedFn2, null, cachedCat2);
                l2.setColorForge(prefs.getBoolean("cf2", false));
                dailyLevelsList.add(l2);
            }
            dailyLevels.postValue(new ArrayList<>(dailyLevelsList));
            return;
        }

        // Our own pictures first (CLAUDE.md §8.5by: Daily = our uploads, date-wise): today's
        // pictures from the ColorForge daily feed; if none were published today, the newest ones.
        // Only when our server has nothing (or is down) does the banner fall back to the paid
        // catalogue's DAILY category below.
        if (ColorForgeApi.hasExplicitServer(getApplication())) {
            Manager.getInstance().executeApiRequestWithCall(
                    ColorForgeApi.restRoot(getApplication()), "daily/", jsonObject -> {
                        List<Level> ours = parseLevelsFromJson(jsonObject, "other");
                        List<Level> todays = new ArrayList<>();
                        List<Level> live = new ArrayList<>();
                        for (Level l : ours) {
                            String ts = l.getTimeStamp() == null ? "" : l.getTimeStamp().trim();
                            if (ts.length() >= 10) ts = ts.substring(0, 10);
                            if (ts.compareTo(today) > 0) continue;      // scheduled, not yet live
                            l.setColorForge(true);
                            live.add(l);
                            if (ts.equals(today)) todays.add(l);
                        }
                        List<Level> pick = !todays.isEmpty() ? todays : live;   // feed is newest first
                        if (!pick.isEmpty()) {
                            cacheDailyLevels(pick, today, prefs);
                            dailyLevels.postValue(new ArrayList<>(dailyLevelsList));
                        } else {
                            loadDailyFromPaid(today, prefs);
                        }
                    }, null);
            return;
        }
        loadDailyFromPaid(today, prefs);
    }

    private void loadDailyFromPaid(String today, SharedPreferences prefs) {
        Manager.getInstance().executeApiRequest("gimg/c/" + DAILY_CAT, jsonObject -> {
            List<Level> levels = parseLevelsFromJson(jsonObject, DAILY_CAT);

            if (levels.isEmpty()) {
                loadDailyFromPopular(today, prefs);
                return;
            }

            pickAndCacheDailyLevels(levels, today, prefs);
            dailyLevels.postValue(new ArrayList<>(dailyLevelsList));
        });
    }

    private void loadDailyFromPopular(String today, SharedPreferences prefs) {
        Manager.getInstance().executeApiRequest("gimg/c/Popular", jsonObject -> {
            List<Level> levels = parseLevelsFromJson(jsonObject, "Popular");
            if (levels.isEmpty()) return;

            pickAndCacheDailyLevels(levels, today, prefs);
            dailyLevels.postValue(new ArrayList<>(dailyLevelsList));
        });
    }

    private List<Level> parseLevelsFromJson(JsonObject jsonObject, String fallbackCat) {
        List<Level> levels = new ArrayList<>();
        if (jsonObject == null || !jsonObject.has("data")) return levels;

        try {
            JsonArray data = jsonObject.getAsJsonArray("data");
            for (int i = 0; i < data.size(); i++) {
                Level level = Level.fromJson(data.get(i).getAsJsonObject());
                if (level != null && level.getFileName() != null
                        && !level.getFileName().isEmpty()) {
                    if (level.getCategory() == null || level.getCategory().isEmpty()) {
                        level = new Level(null, level.getFileName(),
                                level.getTimeStamp(), fallbackCat);
                    }
                    levels.add(level);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing levels", e);
        }
        return levels;
    }

    private void pickAndCacheDailyLevels(List<Level> levels, String today,
                                         SharedPreferences prefs) {
        long seed = today.hashCode();
        Collections.shuffle(levels, new Random(seed));
        cacheDailyLevels(levels, today, prefs);
    }

    /** The first two of `levels` become today's banner and are remembered for the day. */
    private void cacheDailyLevels(List<Level> levels, String today, SharedPreferences prefs) {
        dailyLevelsList.clear();
        dailyLevelsList.addAll(levels.subList(0, Math.min(2, levels.size())));

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("date", today);
        editor.putString("fn1", dailyLevelsList.get(0).getFileName());
        editor.putString("cat1", dailyLevelsList.get(0).getCategory());
        editor.putBoolean("cf1", dailyLevelsList.get(0).isColorForge());
        if (dailyLevelsList.size() > 1) {
            editor.putString("fn2", dailyLevelsList.get(1).getFileName());
            editor.putString("cat2", dailyLevelsList.get(1).getCategory());
            editor.putBoolean("cf2", dailyLevelsList.get(1).isColorForge());
        } else {
            editor.putString("fn2", "");
            editor.putString("cat2", "");
            editor.putBoolean("cf2", false);
        }
        editor.apply();
    }

    // ─── Daily Banner Click Handlers ───

    public void onDailyStartClicked() {
        if (!dailyLevelsList.isEmpty()) {
            navigateToGame.setValue(dailyLevelsList.get(0));
        } else {
            scrollToFirstTab.call();
        }
    }

    public void onDailyPreview1Clicked() {
        if (dailyLevelsList.size() > 0) {
            navigateToGame.setValue(dailyLevelsList.get(0));
        }
    }

    public void onDailyPreview2Clicked() {
        if (dailyLevelsList.size() > 1) {
            navigateToGame.setValue(dailyLevelsList.get(1));
        }
    }

    // ─── Back Press ───

    public void onBackPressed() {
        showBackAd.call();
    }

    // ─── Helper Methods ───

    private ArrayList<String> buildDisplayList(ArrayList<String> categories) {
        ArrayList<String> display = new ArrayList<>(categories);
        if (!display.contains(DAILY_CAT)) {
//            display.add(0, DAILY_CAT);
        }
        return display;
    }

    private boolean hasInternet() {
        return Manager.getInstance().hasInternetConnection(getApplication());
    }

    private void postLoadState(LoadState state) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> loadState.setValue(state));
    }

    // ─── Timeout ───

    private void startTimeout() {
        timeoutRunnable = () -> {
            Log.e(TAG, "Download timeout");
            if (currentCall != null) currentCall.cancel();
            postLoadState(LoadState.TIMEOUT);
            isRefreshing.postValue(false);
            snackbarMessage.postValue("Connection timed out");
        };
        timeoutHandler.postDelayed(timeoutRunnable, DOWNLOAD_TIMEOUT_MS);
    }

    private void cancelTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    // ─── Cleanup ───

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelTimeout();
        if (timeoutHandler != null) {
            timeoutHandler.removeCallbacksAndMessages(null);
        }
        if (currentCall != null) {
            currentCall.cancel();
        }
    }
}