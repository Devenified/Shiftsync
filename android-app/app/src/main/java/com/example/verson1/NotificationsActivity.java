package com.example.verson1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progress;
    private View emptyState;
    private NotificationsAdapter adapter;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        token = SessionManager.getToken(this);
        if (token == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.rv_notifications);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        progress = findViewById(R.id.progress);
        emptyState = findViewById(R.id.empty_state);
        TextView markAllRead = findViewById(R.id.tv_mark_all_read);

        adapter = new NotificationsAdapter(this::onNotificationClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadNotifications);
        swipeRefresh.setColorSchemeResources(R.color.brand_primary, R.color.brand_secondary);

        markAllRead.setOnClickListener(v -> markAllAsRead());

        loadNotifications();
    }

    private void loadNotifications() {
        progress.setVisibility(swipeRefresh.isRefreshing() ? View.GONE : View.VISIBLE);
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/notifications", token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    if (res.code == 200) {
                        try {
                            JSONObject obj = new JSONObject(res.body);
                            JSONArray arr = obj.optJSONArray("notifications");
                            if (arr == null) arr = new JSONArray();
                            List<NotificationItem> list = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                list.add(NotificationItem.fromJson(arr.getJSONObject(i)));
                            }
                            adapter.setItems(list);
                            emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                            recyclerView.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                        } catch (Exception e) {
                            Toast.makeText(this, "Error loading notifications", Toast.LENGTH_SHORT).show();
                        }
                    } else if (res.code == 401) {
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Could not load notifications", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void onNotificationClick(NotificationItem item) {
        if (!item.read) {
            new Thread(() -> {
                try {
                    ApiClient.patch("/api/notifications/" + item.id + "/read", token, "{}");
                } catch (Exception ignored) { }
            }).start();
            item.read = true;
            adapter.notifyDataSetChanged();
        }
        routeForNotification(item);
    }

    private void routeForNotification(NotificationItem item) {
        String type = item.type == null ? "" : item.type;
        boolean isEmployer = SessionManager.isEmployer(this);
        Intent intent = null;
        switch (type) {
            case "shift_assignment":
            case "shift_created":
            case "shift_updated":
            case "shift_cancelled":
            case "shift_rejected":
                intent = new Intent(this,
                        isEmployer ? EmployerManageShiftsActivity.class
                                   : WorkerMyShiftsActivity.class);
                break;
            case "leave_request":
            case "leave_approved":
            case "leave_rejected":
                intent = new Intent(this, isEmployer
                        ? RequestLeaveActivity.class
                        : RequestLeaveActivity.class);
                break;
            case "swap_request":
            case "swap_approved":
            case "swap_rejected":
                intent = new Intent(this, RequestSwapActivity.class);
                break;
            default:
                return;
        }
        try {
            startActivity(intent);
        } catch (Exception ignored) { }
    }

    private void markAllAsRead() {
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.patch("/api/notifications/mark-all-read", token, "{}");
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        Toast.makeText(this, "All notifications marked as read", Toast.LENGTH_SHORT).show();
                        loadNotifications();
                    } else {
                        Toast.makeText(this, "Could not update notifications", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
