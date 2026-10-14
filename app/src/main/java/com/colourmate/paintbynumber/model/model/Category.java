package com.colourmate.paintbynumber.model.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Category model
 * Represents a folder that contains multiple Levels
 */
public class Category implements Parcelable {

    @NonNull
    private String folder;

    @NonNull
    private List<Level> levels = new ArrayList<>();

    // -------------------- CONSTRUCTORS --------------------

    public Category(@NonNull String folder) {
        this.folder = folder;
    }

    protected Category(Parcel in) {
        folder = in.readString();
        levels = in.createTypedArrayList(Level.CREATOR);
        if (levels == null) {
            levels = new ArrayList<>();
        }
    }

    // -------------------- GETTERS / SETTERS --------------------

    @NonNull
    public String getFolder() {
        return folder;
    }

    public void setFolder(@NonNull String folder) {
        this.folder = folder;
    }

    @NonNull
    public List<Level> getLevels() {
        return levels;
    }

    public void setLevels(@NonNull List<Level> levels) {
        this.levels = levels;
    }

    public void addLevel(@NonNull Level level) {
        levels.add(level);
    }

    public int size() {
        return levels.size();
    }

    // -------------------- PARCELABLE --------------------

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(folder);
        dest.writeTypedList(levels);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Category> CREATOR = new Creator<Category>() {
        @Override
        public Category createFromParcel(Parcel in) {
            return new Category(in);
        }

        @Override
        public Category[] newArray(int size) {
            return new Category[size];
        }
    };

    // -------------------- DEBUG --------------------

    @NonNull
    @Override
    public String toString() {
        return "Category{" +
                "folder='" + folder + '\'' +
                ", levels=" + levels.size() +
                '}';
    }
}
