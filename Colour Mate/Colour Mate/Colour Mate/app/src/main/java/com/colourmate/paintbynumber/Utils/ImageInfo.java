package com.colourmate.paintbynumber.Utils;

import com.colourmate.paintbynumber.library.Library;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ImageInfo {

    private final String imageID;
    private final Date releaseDate;
    private final boolean free;

    public ImageInfo(String id, Date releaseDate, boolean free) {
        this.imageID = id;
        this.releaseDate = releaseDate;
        this.free = free;
    }

    public String getImageID() {
        return imageID;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public boolean isFree() {
        return free;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ImageInfo)) return false;
        ImageInfo other = (ImageInfo) obj;
        return imageID.equals(other.imageID);
    }

    @Override
    public int hashCode() {
        return imageID.hashCode();
    }

    /**
     * Parses JSON array into ImageInfo list.
     * Never returns null. Errors are skipped safely.
     */
    public static List<ImageInfo> parseImageArray(JSONObject json) {
        List<ImageInfo> list = new ArrayList<>();

        if (json == null) return list;

        try {
            JSONArray arr = json.getJSONArray("images");

            // Create date parser once
            SimpleDateFormat formatter =
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

            Date now = new Date();

            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject obj = arr.getJSONObject(i);

                    String id = obj.getString("id");

                    // optBoolean, not getBoolean. Five of the ten entries in images.json have no
                    // "free" key at all; getBoolean threw on those and the catch below swallowed
                    // the exception, silently dropping HALF the library. Anything without the key
                    // is free — only the paid ones bother to say "free": false.
                    boolean free = obj.optBoolean(Library.FREE_TAG, true);

                    Date releaseDate = formatter.parse(obj.getString("release_date"));

                    if (releaseDate == null) {
                        releaseDate = now;
                    }

                    // If release date is in the future → lock it (not null)
                    if (releaseDate.after(now)) {
                        // Keep the releaseDate but mark item as locked in UI
                        // (your UI can check with releaseDate.after(now))
                    }

                    list.add(new ImageInfo(id, releaseDate, free));

                } catch (Exception ignored) { }
            }

        } catch (JSONException ignored) { }

        return list;
    }
}

