package com.colourmate.paintbynumber.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Helper class for safely extracting values from JsonObjects.
 */
public class JsonHelpers {

    /**
     * Safely gets a String value from a JsonObject.
     *
     * @param jsonObject the JSON object
     * @param key        the key to look up
     * @return the string value, or null if not present or null
     */
    public static String getAsString(JsonObject jsonObject, String key) {
        if (jsonObject == null || key == null || !jsonObject.has(key)) {
            return null;
        }

        JsonElement element = jsonObject.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsString() : null;
    }

    /**
     * Safely gets a long value from a JsonObject.
     *
     * @param jsonObject the JSON object
     * @param key        the key to look up
     * @return the long value, or 0 if not present or not a valid number
     */
    public static long getAsLong(JsonObject jsonObject, String key) {
        if (jsonObject == null || key == null || !jsonObject.has(key)) {
            return 0L;
        }

        try {
            JsonElement element = jsonObject.get(key);
            if (element != null && !element.isJsonNull()) {
                return element.getAsLong();
            }
        } catch (NumberFormatException | UnsupportedOperationException e) {
            // Log or ignore invalid number formats
        }

        return 0L;
    }
}
