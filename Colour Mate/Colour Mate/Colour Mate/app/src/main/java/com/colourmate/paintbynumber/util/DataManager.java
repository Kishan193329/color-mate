package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.model.UnlockImage;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.model.model.Step;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;


public class DataManager {
    private static final String PREF_IS_FINISHED = "IsFinishedGame_";
    public static final String PREF_STEP_PREFIX = "SavedSteps_";

    public static String CAT_DREAMGIRL = "dreamgirl_new";
    public static String CAT_HOTSUMMER = "hotsummer_new";
    public static String CAT_ARTGHOST = "artghost_new";
    public static String CAT_CARTOON = "cartooncough_new";
    public static String CAT_TITANIC = "titanicsea_new";

    private static final DataManager instance = new DataManager();
    private final Gson gson = new Gson();

    // Keeps same visibility/semantics as original
    public static boolean selectImageExclusive = false;

    private DataManager() { /* singleton */ }

    public static DataManager getInstance() {
        return instance;
    }

    // --------------------
    // Helper: prefs
    // --------------------
    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(GlobalApplication.getAppContext());
    }

    // --------------------
    // Array flags & list storage
    // --------------------
    public boolean isAddedInArray(String key) {
        return prefs().getBoolean(key + "_add", false);
    }

    public void setArrayAdded(String key, boolean added) {
        prefs().edit().putBoolean(key + "_add", added).apply();
    }

    public static void saveArrayList(String key, ArrayList<UnlockImage> list) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(GlobalApplication.getAppContext()).edit();
        String json = new Gson().toJson(list);
        editor.putString(key + "_save", json).apply();
    }

    public static ArrayList<UnlockImage> getArrayList(Context context, String key) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String json = sp.getString(key + "_save", null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<ArrayList<UnlockImage>>() {
            }.getType();
            ArrayList<UnlockImage> list = new Gson().fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void setTotalCatWise(String cat, int total) {
        PreferenceManager.getDefaultSharedPreferences(GlobalApplication.getAppContext())
                .edit()
                .putInt("Item_total_" + cat, total)
                .apply();
    }

    public static int getTotalCatWise(String cat, Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt("Item_total_" + cat, 0);
    }

    public static int getCountCatWise(String cat, Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt("Item_count_" + cat, 0);
    }

    // --------------------
    // Steps per level (save/get)
    // --------------------
    public void saveStep(Level level, Step step) {
        SharedPreferences sp = prefs();
        String key = PREF_STEP_PREFIX + level.getFileName();
        String json = sp.getString(key, null);

        ArrayList<Step> steps;
        if (json == null) {
            steps = new ArrayList<>();
        } else {
            try {
                Type type = new TypeToken<ArrayList<Step>>() {
                }.getType();
                steps = gson.fromJson(json, type);
                if (steps == null) steps = new ArrayList<>();
            } catch (Exception e) {
                e.printStackTrace();
                steps = new ArrayList<>();
            }
        }

        steps.add(step);
        sp.edit().putString(key, gson.toJson(steps)).apply();
    }

    public ArrayList<Step> getSteps(Level level) {
        SharedPreferences sp = prefs();
        String json = sp.getString(PREF_STEP_PREFIX + level.getFileName(), null);
        if (json == null) return new ArrayList<>();

        try {
            Type type = new TypeToken<ArrayList<Step>>() {
            }.getType();
            ArrayList<Step> steps = gson.fromJson(json, type);
            return steps != null ? steps : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // --------------------
    // Preview file helpers
    // --------------------
    public File getPreview(Level level) {
        File file = new File(GlobalApplication.getAppContext().getFilesDir(), level.getFileName());
        return file.exists() ? file : null;
    }

    public void savePreview(Level level, Bitmap bitmap) {
        File file = new File(GlobalApplication.getAppContext().getFilesDir(), level.getFileName());
        if (file.getParentFile() != null) file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            Bitmap preview = generatePreview(bitmap, GlobalApplication.getAppContext().getResources().getDimensionPixelSize(R.dimen.preview_size));
            if (preview != null) {
                preview.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Picasso.get().invalidate(file);
        } catch (OutOfMemoryError oom) {
            oom.printStackTrace();
            Picasso.get().invalidate(file);
        }

        // Ensure cache invalidation so next load reads new file
        Picasso.get().invalidate(file);
    }

    /**
     * Create a square preview of size x size. Returns null on OOM.
     */
    public Bitmap generatePreview(Bitmap bitmap, int size) {
        if (bitmap == null || size <= 0) return null;
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size, size, false);
            int[] pixels = new int[size * size];
            scaled.getPixels(pixels, 0, size, 0, 0, size, size);

            // Replace color -3355444 with transparent (same behaviour as original)
            for (int i = 0; i < pixels.length; i++) {
                if (pixels[i] == -3355444) {
                    pixels[i] = 0;
                }
            }
            scaled.setPixels(pixels, 0, size, 0, 0, size, size);
            return scaled;
        } catch (OutOfMemoryError oom) {
            oom.printStackTrace();
            return null;
        }
    }

    // --------------------
    // Level finished / clear
    // --------------------
    public void finishLevel(Level level) {
        prefs().edit().putBoolean(PREF_IS_FINISHED + level.getFileName(), true).apply();
    }

    public boolean isFinishedLevel(Level level) {
        return prefs().getBoolean(PREF_IS_FINISHED + level.getFileName(), false);
    }

    public void clearLevel(Level level) {
        SharedPreferences.Editor editor = prefs().edit();
        editor.remove(PREF_STEP_PREFIX + level.getFileName());
        editor.remove(PREF_IS_FINISHED + level.getFileName());
        editor.apply();

        File preview = getPreview(level);
        if (preview != null && preview.exists()) {
            preview.delete();
        }
        Picasso.get().invalidate(preview);
    }
}

