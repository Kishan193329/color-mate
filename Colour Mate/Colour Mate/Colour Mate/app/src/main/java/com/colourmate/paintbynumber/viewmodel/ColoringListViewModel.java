package com.colourmate.paintbynumber.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.util.DataManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ColoringListViewModel extends AndroidViewModel {

    private static final String TAG = "ColoringListVM";
    private static final String DAILY_CAT = "daily";
    private static final int LOAD_TIMEOUT_MS = 15000;
    public static final String COLOR_NUMBER_PATH = "MyColorByNumber";

    // ─── State Enum ───
    public enum UiState {
        LOADING, SUCCESS, ERROR, EMPTY
    }

    // ─── LiveData ───
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.LOADING);
    private final MutableLiveData<List<Object>> dailyItems = new MutableLiveData<>();
    private final MutableLiveData<List<Level>> normalItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRefreshing = new MutableLiveData<>(false);

    // Events
    private final SingleLiveEvent<NavigationAction> navigationAction = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> snackbarMessage = new SingleLiveEvent<>();

    // Internal State
    private String category = "";
    private boolean isDaily = false;
    private boolean dataLoaded = false;
    private int dailyReqId = 0;

    // Data source for normal categories
    private Manager.RestDataSource<Level> dataSource;

    // Timeout
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    public ColoringListViewModel(@NonNull Application application) {
        super(application);
    }

    // ─── Getters ───

    public LiveData<UiState> getUiState() { return uiState; }
    public LiveData<List<Object>> getDailyItems() { return dailyItems; }
    public LiveData<List<Level>> getNormalItems() { return normalItems; }
    public LiveData<Boolean> getIsRefreshing() { return isRefreshing; }
    public SingleLiveEvent<NavigationAction> getNavigationAction() { return navigationAction; }
    public SingleLiveEvent<String> getSnackbarMessage() { return snackbarMessage; }
    public boolean isDaily() { return isDaily; }
    public String getCategory() { return category; }

    // ─── Initialize ───

    public void initialize(String category) {
        if (category == null || category.isEmpty()) {
            uiState.setValue(UiState.ERROR);
            return;
        }

        this.category = category;
        this.isDaily = category.equalsIgnoreCase(DAILY_CAT);

        // Only load if we don't already have data
        if (!dataLoaded) {
            loadData();
        }
    }

    // ─── Load Data ───

    public void loadData() {
        if (category.isEmpty()) {
            uiState.setValue(UiState.ERROR);
            return;
        }

        if (!hasInternet()) {
            uiState.setValue(UiState.ERROR);
            return;
        }

        dataLoaded = false;
        uiState.setValue(UiState.LOADING);

        if (isDaily) {
            loadDailyData();
        } else {
            loadNormalData();
        }
    }

    // ─── Refresh ───

    public void onRefresh() {
        isRefreshing.setValue(false);
        cancelTimeout();

        if (!hasInternet()) {
            uiState.setValue(UiState.ERROR);
            return;
        }

        loadData();
    }

    public void onRetryClicked() {
        if (hasInternet()) {
            loadData();
        }
    }

    // ─── Daily Category Loading ───

    private void loadDailyData() {
        final int reqId = ++dailyReqId;

        startTimeout(() -> {
            if (reqId == dailyReqId && !dataLoaded) {
                Log.e(TAG, "Daily load timeout");
                uiState.postValue(UiState.ERROR);
            }
        });

        Manager.getInstance().executeApiRequest("gimg/c/" + category, jsonObject -> {
            cancelTimeout();

            // Ignore stale callbacks
            if (reqId != dailyReqId) {
                Log.d(TAG, "Ignoring stale daily callback");
                return;
            }

            dataLoaded = true;

            if (jsonObject == null || !jsonObject.has("data")) {
                Log.e(TAG, "Daily API missing 'data' field");
                uiState.postValue(UiState.ERROR);
                return;
            }

            try {
                List<Object> items = processApiData(jsonObject);

                if (items == null || items.isEmpty()) {
                    Log.e(TAG, "Processed items list is empty");
                    uiState.postValue(UiState.ERROR);
                    return;
                }

                Log.d(TAG, "Daily loaded " + items.size() + " items");
                dailyItems.postValue(items);
                uiState.postValue(UiState.SUCCESS);

            } catch (Exception e) {
                Log.e(TAG, "Error processing daily data", e);
                uiState.postValue(UiState.ERROR);
            }
        });
    }

    // ─── Normal Category Loading ───

    private void loadNormalData() {
        // Clean up previous data source
        if (dataSource != null) {
            dataSource.abort();
            dataSource = null;
        }

        dataSource = Manager.getInstance().getImagesByCategory(category);

        if (dataSource == null) {
            Log.e(TAG, "DataSource is null for category: " + category);
            uiState.postValue(UiState.ERROR);
            return;
        }

        // Set listener BEFORE refresh
        dataSource.addListener(() -> {
            cancelTimeout();
            dataLoaded = true;

            List<Level> items = dataSource.getItems();
            if (items == null || items.isEmpty()) {
                Log.e(TAG, "DataSource returned empty items");
                uiState.postValue(UiState.ERROR);
                return;
            }

            normalItems.postValue(new ArrayList<>(items));
            uiState.postValue(UiState.SUCCESS);
        });

        startTimeout(() -> {
            if (!dataLoaded) {
                Log.e(TAG, "Normal load timeout");
                uiState.postValue(UiState.ERROR);
            }
        });

        dataSource.refresh();
    }

    // ─── Click Handling ───

    public void onColoringClicked(Level level) {
        if (level == null) return;

        // Our own pictures (mixed on top of By Number) play in the reveal game, which keeps its
        // own progress — the flat game's "finished -> share" shortcut does not apply to them.
        if (level.isColorForge()) {
            navigationAction.postValue(new NavigationAction(NavigationType.GAME, level, null));
            return;
        }

        if (DataManager.getInstance().isFinishedLevel(level)) {
            // Completed level → share screen
            String imagePath = getCompletedImagePath(level);

            if (imagePath == null) {
                Log.e(TAG, "Completed image not found");
                return;
            }

            navigationAction.postValue(
                    new NavigationAction(NavigationType.SHARE, level, imagePath));

        } else if (hasInternet()) {
            // Not completed → game screen
            navigationAction.postValue(
                    new NavigationAction(NavigationType.GAME, level, null));

        } else {
            snackbarMessage.postValue("No internet connection");
        }
    }

    private String getCompletedImagePath(Level level) {
        String[] parts = level.getFileName().split("/");
        String fileName = parts[parts.length - 1];

        File dir = new File(getApplication().getFilesDir(), COLOR_NUMBER_PATH);
        File image = new File(dir, fileName + ".png");

        if (image.exists()) {
            return image.getAbsolutePath();
        }

        // Fallback to preview
        File preview = DataManager.getInstance().getPreview(level);
        if (preview != null && preview.exists()) {
            return preview.getAbsolutePath();
        }

        return null;
    }

    // ─── Data Processing ───

    private List<Object> processApiData(JsonObject jsonObject) {
        Map<String, List<Level>> grouped = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 23);
        today.set(Calendar.MINUTE, 59);
        today.set(Calendar.SECOND, 59);
        Date now = today.getTime();

        JsonArray dataArray = jsonObject.getAsJsonArray("data");

        for (JsonElement element : dataArray) {
            try {
                Level level = Level.fromJson(element.getAsJsonObject());
                if (level == null || level.getTimeStamp() == null) continue;

                Date date = sdf.parse(level.getTimeStamp());
                if (date == null) continue;

                if (!date.after(now)) {
                    calendar.setTime(date);
                    int year = calendar.get(Calendar.YEAR);
                    int month = calendar.get(Calendar.MONTH) + 1;
                    String monthKey = String.format(Locale.US, "%04d-%02d", year, month);

                    grouped.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(level);
                }
            } catch (Exception ignored) {}
        }

        List<String> monthKeys = new ArrayList<>(grouped.keySet());
        Collections.sort(monthKeys, Collections.reverseOrder());

        List<Object> items = new ArrayList<>();
        SimpleDateFormat tsFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (String monthKey : monthKeys) {
            List<Level> levels = grouped.get(monthKey);
            if (levels == null) continue;

            levels.sort((a, b) -> {
                try {
                    Date da = tsFmt.parse(a.getTimeStamp());
                    Date db = tsFmt.parse(b.getTimeStamp());
                    if (da == null || db == null) return 0;
                    return db.compareTo(da);
                } catch (Exception e) {
                    return 0;
                }
            });

            items.addAll(levels);
        }

        return items;
    }

    // ─── Timeout ───

    private void startTimeout(Runnable onTimeout) {
        cancelTimeout();
        timeoutRunnable = onTimeout;
        timeoutHandler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS);
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    // ─── Helpers ───

    private boolean hasInternet() {
        return Manager.getInstance().hasInternetConnection(getApplication());
    }

    public Manager.RestDataSource<Level> getDataSource() {
        return dataSource;
    }

    // ─── Cleanup ───

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelTimeout();
        timeoutHandler.removeCallbacksAndMessages(null);
        dailyReqId++;

        if (dataSource != null) {
            dataSource.abort();
            dataSource = null;
        }
    }

    // ─── Inner Classes ───

    public enum NavigationType {
        GAME, SHARE
    }

    public static class NavigationAction {
        public final NavigationType type;
        public final Level level;
        public final String imagePath;

        public NavigationAction(NavigationType type, Level level, String imagePath) {
            this.type = type;
            this.level = level;
            this.imagePath = imagePath;
        }
    }
}