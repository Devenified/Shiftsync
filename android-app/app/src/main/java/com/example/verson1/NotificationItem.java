package com.example.verson1;

import org.json.JSONObject;

public class NotificationItem {
    public String id;
    public String type;
    public String title;
    public String message;
    public String createdAt;
    public String relatedId;
    public boolean read;

    public static NotificationItem fromJson(JSONObject o) {
        NotificationItem n = new NotificationItem();
        n.id = o.optString("_id", o.optString("id", ""));
        n.type = o.optString("type", "info");
        n.title = o.optString("title", "");
        n.message = o.optString("message", "");
        n.createdAt = o.optString("createdAt", "");
        n.relatedId = o.optString("relatedId", "");
        n.read = o.optBoolean("read", false);
        return n;
    }
}
