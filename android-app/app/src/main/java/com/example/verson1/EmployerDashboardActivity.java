package com.example.verson1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmployerDashboardActivity extends AppCompatActivity {

    private TextView welcomeText;
    private TextView dateText;
    private TextView activeShiftsCount;
    private TextView teamMembersCount;
    private TextView nextShiftText;
    private TextView nextShiftTime;
    private RecyclerView activityFeed;
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fabPostShift;
    
    private ActivityFeedAdapter activityFeedAdapter;
    private static final String TAG = "EmployerDashboard";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employer_dashboard_modern);

        if (!SessionManager.ensureEmployer(this)) {
            return;
        }

        // Initialize UI components
        initializeViews();
        setupToolbar();
        setupClickListeners();
        setupBottomNav();
        setupActivityFeed();
        
        fetchEmployerDashboard();
        fetchUserProfile();
        fetchActivityFeed();
        updateDateAndTime();
    }
    
    private void initializeViews() {
        welcomeText = findViewById(R.id.welcome_text);
        dateText = findViewById(R.id.date_text);
        activeShiftsCount = findViewById(R.id.active_shifts_count);
        teamMembersCount = findViewById(R.id.team_members_count);
        nextShiftText = findViewById(R.id.next_shift_text);
        nextShiftTime = findViewById(R.id.next_shift_time);
        activityFeed = findViewById(R.id.activity_feed);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        fabPostShift = findViewById(R.id.fab_post_shift);
    }
    
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_account) {
                startActivity(new Intent(this, EmployerProfileActivity.class));
                return true;
            }
            if (id == R.id.action_notifications) {
                startActivity(new Intent(this, NotificationsActivity.class));
                return true;
            }
            return LogoutUiHelper.onMenuItemLogout(this, id);
        });

        MenuItem bellItem = toolbar.getMenu().findItem(R.id.action_notifications);
        if (bellItem != null) {
            View actionView = bellItem.getActionView();
            if (actionView != null) {
                actionView.setOnClickListener(v ->
                        startActivity(new Intent(this, NotificationsActivity.class)));
            }
        }
    }

    private void refreshNotificationBadge() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) return;
        MenuItem bellItem = toolbar.getMenu().findItem(R.id.action_notifications);
        if (bellItem == null) return;
        View actionView = bellItem.getActionView();
        if (actionView == null) return;
        TextView badge = actionView.findViewById(R.id.action_bell_badge);
        NotificationBadgeHelper.refresh(this, badge);
    }
    
    private void setupClickListeners() {
        findViewById(R.id.btn_request_swap).setOnClickListener(v -> {
            startActivity(new Intent(this, RequestSwapActivity.class));
        });
        
        findViewById(R.id.btn_request_leave).setOnClickListener(v -> {
            startActivity(new Intent(this, RequestLeaveActivity.class));
        });
        
        findViewById(R.id.btn_view_schedule).setOnClickListener(v -> {
            startActivity(new Intent(this, EmployerManageShiftsActivity.class));
        });
        
        findViewById(R.id.btn_team_chat).setOnClickListener(v -> {
            startActivity(new Intent(this, EmployerAIHelperActivity.class));
        });
        
        fabPostShift.setOnClickListener(v -> {
            startActivity(new Intent(this, EmployerPostShiftActivity.class));
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateDateAndTime();
        fetchUserProfile();
        fetchEmployerDashboard();
        fetchActivityFeed();
        refreshNotificationBadge();
    }
    
    private void setupBottomNav() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_employer_home) {
                return true;
            }
            if (itemId == R.id.nav_employer_shifts) {
                startActivity(new Intent(this, EmployerManageShiftsActivity.class));
                return true;
            }
            if (itemId == R.id.nav_employer_hire) {
                startActivity(new Intent(this, EmployerFindWorkersActivity.class));
                return true;
            }
            if (itemId == R.id.nav_employer_profile) {
                startActivity(new Intent(this, EmployerProfileActivity.class));
                return true;
            }
            return false;
        });
    }
    
    private void setupActivityFeed() {
        activityFeedAdapter = new ActivityFeedAdapter(this);
        activityFeed.setLayoutManager(new LinearLayoutManager(this));
        activityFeed.setAdapter(activityFeedAdapter);
    }
    
    private void fetchActivityFeed() {
        String token = SessionManager.getToken(this);
        if (token == null) return;
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/activity", token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        if (res.code == 200) {
                            JSONObject obj = new JSONObject(res.body);
                            JSONArray feed = obj.optJSONArray("feed");
                            if (feed == null) feed = new JSONArray();
                            List<ActivityFeedAdapter.ActivityItem> list = new ArrayList<>();
                            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                            for (int i = 0; i < Math.min(feed.length(), 10); i++) {
                                JSONObject f = feed.getJSONObject(i);
                                String desc = f.optString("description", "Activity");
                                String type = f.optString("type", "info");
                                String ts = f.optString("timestamp", f.optString("createdAt", ""));
                                String time = ts;
                                try {
                                    if (ts.length() >= 19) {
                                        Date d = in.parse(ts.substring(0, 19));
                                        if (d != null) {
                                            long diff = System.currentTimeMillis() - d.getTime();
                                            long min = diff / 60000;
                                            if (min < 1) time = "just now";
                                            else if (min < 60) time = min + "m ago";
                                            else if (min < 1440) time = (min / 60) + "h ago";
                                            else time = (min / 1440) + "d ago";
                                        }
                                    }
                                } catch (Exception ignored) { }
                                int icon;
                                switch (type) {
                                    case "shift_swap": icon = R.drawable.ic_swap_modern; break;
                                    case "leave_request": icon = R.drawable.ic_calendar_modern; break;
                                    case "shift_assignment": icon = R.drawable.ic_briefcase_modern; break;
                                    case "attendance": icon = R.drawable.ic_check_modern; break;
                                    default: icon = R.drawable.ic_person_modern;
                                }
                                list.add(new ActivityFeedAdapter.ActivityItem(desc, time, type, icon));
                            }
                            activityFeedAdapter.setActivities(list);
                        } else {
                            activityFeedAdapter.setActivities(new ArrayList<>());
                        }
                    } catch (Exception e) {
                        activityFeedAdapter.setActivities(new ArrayList<>());
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(
                        () -> activityFeedAdapter.setActivities(new ArrayList<>())
                );
            }
        }).start();
    }
    
    private void updateDateAndTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        dateText.setText(currentDate);
    }
    
    private void fetchUserProfile() {
        new Thread(() -> {
            try {
                String token = SessionManager.getToken(this);
                if (token == null) {
                    runOnUiThread(() -> showError("No authentication token found"));
                    return;
                }

                ApiClient.HttpResult result = ApiClient.get("/api/users/me", token);
                int responseCode = result.code;
                String responseBody = result.body;

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (responseCode == 200 && responseBody != null) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            JSONObject user = jsonResponse.getJSONObject("user");
                            String fullName = user.optString("fullName", "Employer");
                            
                            welcomeText.setText("Welcome back, " + fullName + "!");
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing profile", e);
                            showError("Error parsing profile data");
                        }
                    } else if (responseCode == 401) {
                        Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else {
                        showError("Failed to load profile: " + responseCode);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Network error", e);
                new Handler(Looper.getMainLooper()).post(() -> showError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    private void fetchEmployerDashboard() {
        new Thread(() -> {
            try {
                String token = SessionManager.getToken(this);
                if (token == null) {
                    runOnUiThread(() -> showError("No authentication token found"));
                    return;
                }

                ApiClient.HttpResult result = ApiClient.get("/api/users/employer-dashboard", token);
                int responseCode = result.code;
                String responseBody = result.body;

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (responseCode == 200 && responseBody != null) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            JSONObject summary = jsonResponse.getJSONObject("summary");
                            
                            int activeShifts = summary.optInt("activeShifts", 0);
                            int totalEmployees = summary.optInt("totalWorkersEngaged", 0);
                            
                            activeShiftsCount.setText(String.valueOf(activeShifts));
                            teamMembersCount.setText(String.valueOf(totalEmployees));

                            JSONObject nextShift = summary.optJSONObject("nextShift");
                            if (nextShift != null) {
                                nextShiftText.setText(nextShift.optString("title", "Upcoming shift"));
                                nextShiftTime.setText(
                                        nextShift.optString("shiftDate", "") + "  " +
                                        nextShift.optString("startTime", "") + " - " +
                                        nextShift.optString("endTime", "")
                                );
                            } else {
                                nextShiftText.setText("No upcoming shifts");
                                nextShiftTime.setText("Create a shift to get started");
                            }
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing dashboard summary", e);
                            showError("Error parsing dashboard data");
                        }
                    } else if (responseCode == 401) {
                        Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else {
                        showError("Failed to load dashboard: " + responseCode);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Network error", e);
                new Handler(Looper.getMainLooper()).post(() -> showError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Error: " + message);
    }
}
