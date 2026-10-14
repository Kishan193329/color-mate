package com.sumoing.recolor.library;

import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.library.TokenManager;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;

public class LibraryItem implements Comparable {
    public String artUrl;
    public Config3D config3D;
    public long created;
    public boolean dailyFree;
    public String index2Url;
    public String indexUrl;
    public Library.ColoredPicture mColored;
    public String mFolder;
    public long mPosition;
    public long mPublishDate;
    protected String name;
    private ArrayList<String> tags;
    public String thumbUrl;

    public static class Config3D {
        public float ambient_light;
        public boolean calculate_normals;
        public String controlmapUrl;
        public float default_rotation_angle;
        public float default_scaling;
        public String envmapUrl;
        public boolean flipv;
        public float lighting_ambient_b;
        public float lighting_ambient_diffusemul;
        public float lighting_ambient_g;
        public float lighting_ambient_r;
        public float lighting_light1_color_b;
        public float lighting_light1_color_g;
        public float lighting_light1_color_r;
        public float lighting_light1_dir_x;
        public float lighting_light1_dir_y;
        public float lighting_light1_dir_z;
        public float lighting_light1_lpow;
        public float lighting_light1_lsub;
        public float lighting_light2_color_b;
        public float lighting_light2_color_g;
        public float lighting_light2_color_r;
        public float lighting_light2_dir_x;
        public float lighting_light2_dir_y;
        public float lighting_light2_dir_z;
        public float lighting_light2_lpow;
        public float lighting_light2_lsub;
        public float lighting_light3_color_b;
        public float lighting_light3_color_g;
        public float lighting_light3_color_r;
        public float lighting_light3_dir_x;
        public float lighting_light3_dir_y;
        public float lighting_light3_dir_z;
        public float lighting_light3_lpow;
        public float lighting_light3_lsub;
        public float mirrormul;
        public float mirrorpow;
        public boolean noao;
        public String normalmapUrl;
        public float normalstrength;
        public float offsety;
        public String skyBackgroundUrl;
        public float specularmul;
        public float specularpow;
        public String tobjUrl;
        public boolean wrapu;

        public boolean isValid() {
            return this.tobjUrl != null;
        }

        public void loadConfigurationFromJson(JsonObject jsonObject) { // Old method: parseConfig()
            // Specular properties
            this.specularpow = getFloatOrDefault(jsonObject, "specularpow", 10.0f);
            this.specularmul = getFloatOrDefault(jsonObject, "specularmul", 0.7f);

            // Normal and mirror properties
            this.normalstrength = getFloatOrDefault(jsonObject, "normalstrength", 1.0f);
            this.mirrorpow = getFloatOrDefault(jsonObject, "mirrorpow", 10.0f);
            this.mirrormul = getFloatOrDefault(jsonObject, "mirrormul", 1.0f);

            // Position and lighting
            this.offsety = getFloatOrDefault(jsonObject, "offsety", 0.0f);
            this.ambient_light = getFloatOrDefault(jsonObject, "ambient_light", 0.75f);

            // Boolean flags
            this.calculate_normals = getBooleanOrDefault(jsonObject, "calculate_normals", false);
            this.flipv = getBooleanOrDefault(jsonObject, "flipv", false);
            this.wrapu = getBooleanOrDefault(jsonObject, "wrapu", false);
            this.noao = jsonObject.has("ao") && jsonObject.get("ao").getAsString().equalsIgnoreCase("none");

            // Default rotation and scaling
            this.default_rotation_angle = getFloatOrDefault(jsonObject, "default_rotation_angle", 0.0f);
            this.default_scaling = getFloatOrDefault(jsonObject, "default_scaling", 1.0f);

            // Ambient lighting
            this.lighting_ambient_r = getFloatOrDefault(jsonObject, "lighting.ambient.r", 0.3f);
            this.lighting_ambient_g = getFloatOrDefault(jsonObject, "lighting.ambient.g", 0.3f);
            this.lighting_ambient_b = getFloatOrDefault(jsonObject, "lighting.ambient.b", 0.3f);
            this.lighting_ambient_diffusemul = getFloatOrDefault(jsonObject, "lighting.ambient.diffusemul", 0.9f);

            // Light 1 properties
            this.lighting_light1_dir_x = getFloatOrDefault(jsonObject, "lighting.light1.dir.x", 1.0f);
            this.lighting_light1_dir_y = getFloatOrDefault(jsonObject, "lighting.light1.dir.y", -1.0f);
            this.lighting_light1_dir_z = getFloatOrDefault(jsonObject, "lighting.light1.dir.z", -0.1f);
            this.lighting_light1_lsub = getFloatOrDefault(jsonObject, "lighting.light1.lsub", 0.0f);
            this.lighting_light1_lpow = getFloatOrDefault(jsonObject, "lighting.light1.lpow", 2.0f);
            this.lighting_light1_color_r = getFloatOrDefault(jsonObject, "lighting.light1.color.r", 0.75f);
            this.lighting_light1_color_g = getFloatOrDefault(jsonObject, "lighting.light1.color.g", 0.5f);
            this.lighting_light1_color_b = getFloatOrDefault(jsonObject, "lighting.light1.color.b", 0.5f);

            // Light 2 properties
            this.lighting_light2_dir_x = getFloatOrDefault(jsonObject, "lighting.light2.dir.x", -0.5f);
            this.lighting_light2_dir_y = getFloatOrDefault(jsonObject, "lighting.light2.dir.y", -0.5f);
            this.lighting_light2_dir_z = getFloatOrDefault(jsonObject, "lighting.light2.dir.z", 1.0f);
            this.lighting_light2_lsub = getFloatOrDefault(jsonObject, "lighting.light2.lsub", 0.0f);
            this.lighting_light2_lpow = getFloatOrDefault(jsonObject, "lighting.light2.lpow", 2.5f);
            this.lighting_light2_color_r = getFloatOrDefault(jsonObject, "lighting.light2.color.r", 1.0f);
            this.lighting_light2_color_g = getFloatOrDefault(jsonObject, "lighting.light2.color.g", 1.0f);
            this.lighting_light2_color_b = getFloatOrDefault(jsonObject, "lighting.light2.color.b", 1.0f);

            // Light 3 properties
            this.lighting_light3_dir_x = getFloatOrDefault(jsonObject, "lighting.light3.dir.x", 2.0f);
            this.lighting_light3_dir_y = getFloatOrDefault(jsonObject, "lighting.light3.dir.y", 2.0f);
            this.lighting_light3_dir_z = getFloatOrDefault(jsonObject, "lighting.light3.dir.z", 1.0f);
            this.lighting_light3_lsub = getFloatOrDefault(jsonObject, "lighting.light3.lsub", 0.0f);
            this.lighting_light3_lpow = getFloatOrDefault(jsonObject, "lighting.light3.lpow", 4.0f);
            this.lighting_light3_color_r = getFloatOrDefault(jsonObject, "lighting.light3.color.r", 1.0f);
            this.lighting_light3_color_g = getFloatOrDefault(jsonObject, "lighting.light3.color.g", 1.0f);
            this.lighting_light3_color_b = getFloatOrDefault(jsonObject, "lighting.light3.color.b", 1.0f);
        }

        private float getFloatOrDefault(JsonObject json, String key, float defaultValue) {
            return json.has(key) ? json.get(key).getAsFloat() : defaultValue;
        }

        private boolean getBooleanOrDefault(JsonObject json, String key, boolean defaultValue) {
            return json.has(key) ? json.get(key).getAsString().equalsIgnoreCase("true") : defaultValue;
        }
    }

    public static class FreeCategoryComparator implements Comparator<LibraryItem> {
        @Override
        public int compare(LibraryItem item1, LibraryItem item2) {
            if (item2.mPosition == item1.mPosition) {
                return item1.compareTo(item2);
            }
            if (item2.mPosition > item1.mPosition) {
                return -1;
            }
            return item2.mPosition == item1.mPosition ? 0 : 1;
        }
    }

    public static class PositionComparator implements Comparator<LibraryItem> {
        @Override
        public int compare(LibraryItem item1, LibraryItem item2) {
            if (item2.mPosition > item1.mPosition) {
                return -1;
            }
            return item2.mPosition == item1.mPosition ? 0 : 1;
        }
    }

    public LibraryItem(String name) {
        this.name = name;
    }

    public boolean isFreeContent() { // Old method: isFree()
        return this.tags.contains(Library.FREE_TAG) ||
                this.tags.contains(Library.SCANNED_TAG) ||
                TokenManager.getInstance().isPurchased(getName());
    }

    public boolean isNewContent() { // Old method: isNew()
        return this.tags.contains(Library.DAILY_NEW_TAG);
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Library.ColoredPicture getColoredPicture() { // Old method: getColored()
        return this.mColored;
    }

    @Override
    public int compareTo(Object object) {
        if (!(object instanceof LibraryItem)) {
            return 0;
        }
        LibraryItem otherItem = (LibraryItem) object;
        long thisDate = this.mPublishDate > 0 ? this.mPublishDate : this.created;
        long otherDate = otherItem.mPublishDate > 0 ? otherItem.mPublishDate : otherItem.created;
        return constrainToIntRange(thisDate - otherDate);
    }

    // Old method: clampToInt()
    private static int constrainToIntRange(long value) {
        return (int) Math.max(Math.min(value, Integer.MAX_VALUE), Integer.MIN_VALUE);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || !(object instanceof LibraryItem)) {
            return false;
        }
        return this.name.equals(((LibraryItem) object).name);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public String toString() {
        return "<" + getName() + ">";
    }

    public boolean containsTag(String tag) { // Old method: hasTag()
        return this.tags != null && this.tags.contains(tag);
    }

    public void addTag(String tag) {
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        } else if (this.tags.contains(tag)) {
            return;
        }
        this.tags.add(tag);
    }

    public void setTags(ArrayList<String> tags) {
        this.tags = tags;
    }

    public boolean hasAnyTags() { // Old method: hasTags()
        return this.tags != null && this.tags.size() > 0;
    }

    public ArrayList<String> getTags() {
        return this.tags;
    }

    public boolean is3DModel() { // Old method: is3D()
        return this.config3D != null;
    }
}