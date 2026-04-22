package com.example.verson1;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Helper that polls the backend for the unread notification count and updates a
 * small badge (TextView) overlaid on the bell icon. Shows 99+ when the count is
 * larger than 99. Hides the badge when there are no unread notifications.
 */
public class NotificationBadgeHelper {

    public static void refresh(final Activity activity, final TextView badge) {
        if (activity == null || badge == null) return;
        final String token = SessionManager.getToken(activity);
        if (token == null) return;

        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/notifications/unread-count", token);
                int count = 0;
                if (res.code == 200) {
                    try {
                        JSONObject obj = new JSONObject(res.body);
                        count = obj.optInt("count", 0);
                    } catch (Exception ignored) { }
                }
                final int finalCount = count;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    apply(badge, finalCount);
                });
            } catch (Exception ignored) { }
        }).start();
    }

    private static void apply(TextView badge, int count) {
        if (count <= 0) {
            badge.setVisibility(View.GONE);
            return;
        }
        badge.setVisibility(View.VISIBLE);
        badge.setText(count > 99 ? "99+" : String.valueOf(count));
    }
}
