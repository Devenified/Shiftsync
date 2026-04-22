package com.example.verson1;

import org.json.JSONObject;

/**
 * Small helpers for Mongo-style JSON ids from Express/Mongoose.
 */
public final class JsonHelper {

    private JsonHelper() {}

    public static String idString(JSONObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        try {
            Object v = obj.get(key);
            if (v instanceof String) {
                return (String) v;
            }
            if (v instanceof JSONObject) {
                JSONObject nested = (JSONObject) v;
                if (nested.has("$oid")) {
                    return nested.optString("$oid", "");
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
