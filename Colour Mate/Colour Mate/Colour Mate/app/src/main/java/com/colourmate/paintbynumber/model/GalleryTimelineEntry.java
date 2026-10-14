package com.colourmate.paintbynumber.model;

import com.colourmate.paintbynumber.util.JsonHelpers;
import com.google.gson.JsonObject;

public class GalleryTimelineEntry {
    public static final String TYPE_COMMENT = "Comment";
    public static final String TYPE_FOLLOWED = "kTimelineTypeFollowed";
    public static final String TYPE_LIKED = "Liked";
    public static final String TYPE_MENTIONED = "kTimelineTypeMentioned";
    public static final String TYPE_WARNING = "kTimelineTypeWarning";
    private String entryFromDisplayName;
    private String entryFromProfilePicture;
    private String entryFromUID;
    private String entryMessage;
    private String entryTargetPostAuthorUID;
    private String entryTargetPostID;
    private String entryTargetPostThumb;
    private long entryTimestamp;
    private String entryType;

    public String getEntryFromDisplayName() {
        return this.entryFromDisplayName;
    }

    public String getEntryFromProfilePicture() {
        return this.entryFromProfilePicture;
    }

    public String getEntryFromUID() {
        return this.entryFromUID;
    }

    public String getEntryMessage() {
        return this.entryMessage;
    }

    public String getEntryTargetPostID() {
        return this.entryTargetPostID;
    }

    public String getEntryTargetPostThumb() {
        return this.entryTargetPostThumb;
    }

    public String getEntryTargetPostAuthorUID() {
        return this.entryTargetPostAuthorUID;
    }

    public String getEntryType() {
        return this.entryType;
    }

    public long getEntryTimestamp() {
        return this.entryTimestamp;
    }

    public static GalleryTimelineEntry fromJson(JsonObject jsonObject) {
        try {
            GalleryTimelineEntry galleryTimelineEntry = new GalleryTimelineEntry();
            galleryTimelineEntry.entryFromDisplayName = JsonHelpers.getAsString(jsonObject, "entryFromDisplayName");
            galleryTimelineEntry.entryFromProfilePicture = JsonHelpers.getAsString(jsonObject, "entryFromProfilePicture");
            galleryTimelineEntry.entryFromUID = JsonHelpers.getAsString(jsonObject, "entryFromUID");
            galleryTimelineEntry.entryMessage = JsonHelpers.getAsString(jsonObject, "entryMessage");
            galleryTimelineEntry.entryTargetPostID = JsonHelpers.getAsString(jsonObject, "entryTargetPostID");
            galleryTimelineEntry.entryTargetPostThumb = JsonHelpers.getAsString(jsonObject, "entryTargetPostThumb");
            galleryTimelineEntry.entryTargetPostAuthorUID = JsonHelpers.getAsString(jsonObject, "entryTargetPostAuthorUID");
            galleryTimelineEntry.entryType = JsonHelpers.getAsString(jsonObject, "entryType");
            galleryTimelineEntry.entryTimestamp = JsonHelpers.getAsLong(jsonObject, "entryTimestamp");
            return galleryTimelineEntry;
        } catch (Exception e) {
            return null;
        }
    }
}
