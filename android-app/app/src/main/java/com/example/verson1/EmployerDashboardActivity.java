package com.example.verson1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
        
        // Load data
        fetchEmployerDashboard();
        fetchUserProfile();
        loadMockActivityData();
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
            return LogoutUiHelper.onMenuItemLogout(this, id);
        });
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
    
    private void loadMockActivityData() {
        List<ActivityFeedAdapter.ActivityItem> activities = new ArrayList<>();
        
        // Add mock activities
        activities.add(new ActivityFeedAdapter.ActivityItem(
            "John swapped shift with Alex", 
            "2 hours ago", 
            "swap", 
            android.R.drawable.ic_menu_myplaces
        ));
        
        activities.add(new ActivityFeedAdapter.ActivityItem(
            "Priya requested leave for March 28", 
            "4 hours ago", 
            "leave", 
            android.R.drawable.ic_menu_myplaces
        ));
        
        activities.add(new ActivityFeedAdapter.ActivityItem(
            "Manager approved shift change", 
            "6 hours ago", 
            "approval", 
            android.R.drawable.ic_menu_myplaces
        ));
        
        activities.add(new ActivityFeedAdapter.ActivityItem(
            "New team member Sarah joined", 
            "1 day ago", 
            "info", 
            android.R.drawable.ic_menu_myplaces
        ));
        
        activityFeedAdapter.setActivities(activities);
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
                            int totalEmployees = summary.optInt("totalEmployees", 0);
                            
                            activeShiftsCount.setText(String.valueOf(activeShifts));
                            teamMembersCount.setText(String.valueOf(totalEmployees));
                            
                            // Set mock next shift data
                            nextShiftText.setText("Morning Shift");
                            nextShiftTime.setText("Tomorrow, 8:00 AM - 4:00 PM");
                            
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
