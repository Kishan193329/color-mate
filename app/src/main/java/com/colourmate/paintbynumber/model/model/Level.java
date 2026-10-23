package com.colourmate.paintbynumber.model.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.colourmate.paintbynumber.util.JsonHelpers;
import com.google.gson.JsonObject;

public class Level implements Parcelable {
    public static final Creator<Level> CREATOR = new CreatorCategory();
    private String appName;
    private String category;
    private String fileName;
    private String icon;
    private boolean isNewToday;
    private String packaage;
    private String timeStamp;
    private String key;
    /** Set by RestDataSource when the item came from the ColorForge server. */
    private boolean colorForge;
    /** Set alongside {@link #colorForge} when the item came from the Diamond feed, whose assets
     *  live under a different root — the thumbnail and the payload are not the colour ones. */
    private boolean diamond;
    /** Our pictures only: "free", "reward" (an ad unlocks it) or "premium" (coins unlock it). */
    private String tier = "free";

    public int describeContents() {
        return 0;
    }

    public Level() {
    }


    public Level(String str, String str2, String str3, String str4) {
        this.key = str;
        this.fileName = str2;
        this.timeStamp = str3;
        this.category = str4;
    }


    public String getKey() {
        return this.key;
    }

    public void setKey(String str) {
        this.key = str;
    }


    public String getAppName() {
        return this.appName;
    }

    public void setAppName(String str) {
        this.appName = str;
    }

    public String getPackaage() {
        return this.packaage;
    }

    public void setPackaage(String str) {
        this.packaage = str;
    }

    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String str) {
        this.icon = str;
    }

    static class CreatorCategory implements Creator<Level> {
        CreatorCategory() {
        }

        @Override
        public Level createFromParcel(Parcel parcel) {
            return new Level(parcel);
        }

        @Override
        public Level[] newArray(int i) {
            return new Level[i];
        }
    }

    public Level(String str) {
        this.fileName = str;
    }

    protected Level(Parcel parcel) {
        this.fileName = parcel.readString();
        this.category = parcel.readString();
        this.isNewToday = parcel.readByte() != 0;
        this.colorForge = parcel.readByte() != 0;
        this.diamond = parcel.readByte() != 0;
        this.tier = parcel.readString();
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.fileName);
        parcel.writeString(this.category);
        parcel.writeByte(this.isNewToday ? (byte) 1 : 0);
        // Must be parcelled: GameActivity gets the Level through an Intent, and without this
        // flag it would fetch a ColorForge picture from the ionicsolutions host and 404.
        parcel.writeByte(this.colorForge ? (byte) 1 : 0);
        parcel.writeByte(this.diamond ? (byte) 1 : 0);
        parcel.writeString(this.tier);
    }

    /** True when this level comes from our ColorForge server ("New 2026" tab). */
    public boolean isColorForge() {
        return colorForge;
    }

    public void setColorForge(boolean colorForge) {
        this.colorForge = colorForge;
    }

    /** True when this level came from the Diamond feed rather than the colour one. */
    public boolean isDiamond() {
        return diamond;
    }

    public String getTier() {
        return tier == null ? "free" : tier;
    }

    public boolean isPaidTier() {
        return "premium".equals(tier) || "reward".equals(tier);
    }

    public void setDiamond(boolean diamond) {
        this.diamond = diamond;
    }

    public String getFileName() {
        return this.fileName;
    }

    public String getCategory() {
        return this.category;
    }

    public String getTimeStamp() {
        return this.timeStamp;
    }

    public void setTimeStamp(String str) {
        this.timeStamp = str;
    }

    public boolean isNewToday() {
        return this.isNewToday;
    }

    public void setNewToday(boolean z) {
        this.isNewToday = z;
    }

    public String toString() {
        return "fileName: " + this.fileName + ",category: " + this.category + ",isNewToday: " + this.isNewToday + ",timeStamp: " + this.timeStamp;
    }

    public static Level fromJson(JsonObject jsonObject) {
        try {
            Level level = new Level();
            level.fileName = JsonHelpers.getAsString(jsonObject, "fileName");
            level.category = JsonHelpers.getAsString(jsonObject, "category");
            level.timeStamp = JsonHelpers.getAsString(jsonObject, "timeStamp");
            if (jsonObject.has("tier") && !jsonObject.get("tier").isJsonNull()) {
                level.tier = jsonObject.get("tier").getAsString();
            }
            if (jsonObject.has("isNewToday")) {
                if (!jsonObject.get("isNewToday").isJsonNull()) {
                    level.isNewToday = jsonObject.get("isNewToday").getAsBoolean();
                    return level;
                }
            }
            return level;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
