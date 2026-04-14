package com.example.verson1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WorkerDashboardCompleteActivity extends AppCompatActivity {

    // UI Components
    private TextView welcomeText;
    private TextView workerName;
    private TextView availabilityStatus;
    private TextView todayEarnings;
    private TextView monthlyEarnings;
    private TextView completedShifts;
    private TextView ratingValue;
    private TextView availableShiftsCount;
    private TextView myShiftsCount;
    private TextView availabilityDescription;
    private TextView availabilityLabel;
    private Switch availabilitySwitch;
    private RecyclerView recentActivityRecycler;
    private TextView noActivityText;
    
    // Real-time update receiver
    private BroadcastReceiver profileUpdateReceiver;
    
    // Data
    private String authToken;
    private boolean suppressAvailabilityCallback = false;
    private RecentActivityAdapter activityAdapter;
    private List<ActivityItem> activityList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_dashboard_complete);

        // Check session
        if (!SessionManager.ensureWorker(this)) {
            return;
        }

        authToken = SessionManager.getToken(this);
        if (authToken == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        initializeViews();
        setupClickListeners();
        setupBottomNavigation();
        setupRecentActivity();
        setupProfileUpdateReceiver();
        fetchUserData();
        updateGreeting();
    }

    private void initializeViews() {
        // Header
        welcomeText = findViewById(R.id.welcome_text);
        workerName = findViewById(R.id.worker_name);
        availabilityStatus = findViewById(R.id.availability_status);
        
        // Earnings
        todayEarnings = findViewById(R.id.today_earnings);
        monthlyEarnings = findViewById(R.id.monthly_earnings);
        completedShifts = findViewById(R.id.completed_shifts);
        ratingValue = findViewById(R.id.rating_value);
        
        // Quick Actions
        availableShiftsCount = findViewById(R.id.available_shifts_count);
        myShiftsCount = findViewById(R.id.my_shifts_count);
        
        // Availability
        availabilityDescription = findViewById(R.id.availability_description);
        availabilityLabel = findViewById(R.id.availability_label);
        availabilitySwitch = findViewById(R.id.availability_switch);
        
        // Recent Activity
        recentActivityRecycler = findViewById(R.id.recent_activity_recycler);
        noActivityText = findViewById(R.id.no_activity_text);
        
        // Set content descriptions for accessibility
        setupAccessibility();
    }

    private void setupAccessibility() {
        findViewById(R.id.profile_image).setContentDescription("Profile picture");
        findViewById(R.id.notification_button).setContentDescription("Notifications");
        findViewById(R.id.settings_button).setContentDescription("Settings");
        findViewById(R.id.find_work_card).setContentDescription("Find work - Browse available shifts");
        findViewById(R.id.my_shifts_card).setContentDescription("My shifts - View your applied and assigned shifts");
        findViewById(R.id.profile_card).setContentDescription("My profile - Update your skills and experience");
        findViewById(R.id.help_card).setContentDescription("Help - Get support and assistance");
        availabilitySwitch.setContentDescription("Work availability toggle - Switch to change your work status");
    }

    private void setupClickListeners() {
        // Profile Card
        findViewById(R.id.profile_card).setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerProfileEnhancedActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // Profile Image
        findViewById(R.id.profile_image).setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerProfileEnhancedActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        findViewById(R.id.header_sign_out).setOnClickListener(v -> LogoutUiHelper.showConfirmLogout(this));

        // Notifications
        findViewById(R.id.notification_button).setOnClickListener(v -> {
            showNotificationsDialog();
        });

        // Settings
        findViewById(R.id.settings_button).setOnClickListener(v -> {
            showSettingsDialog();
        });

        // Quick Action Cards
        findViewById(R.id.find_work_card).setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerOpenShiftsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        findViewById(R.id.my_shifts_card).setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerMyShiftsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        findViewById(R.id.help_card).setOnClickListener(v -> {
            showHelpDialog();
        });
    }

    private void setupBottomNavigation() {
        // Home (current screen)
        View homeNav = findViewById(R.id.nav_home);
        homeNav.setOnClickListener(v -> {
            // Already on home screen
            Toast.makeText(this, "You're already on the home screen", Toast.LENGTH_SHORT).show();
        });

        // Find Work
        View findWorkNav = findViewById(R.id.nav_find_work);
        findWorkNav.setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerOpenShiftsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // My Shifts
        View myShiftsNav = findViewById(R.id.nav_my_shifts);
        myShiftsNav.setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerMyShiftsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // Profile
        View profileNav = findViewById(R.id.nav_profile);
        profileNav.setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerProfileEnhancedActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void setupRecentActivity() {
        activityList = new ArrayList<>();
        activityAdapter = new RecentActivityAdapter(activityList);
        
        recentActivityRecycler.setLayoutManager(new LinearLayoutManager(this));
        recentActivityRecycler.setAdapter(activityAdapter);
        recentActivityRecycler.setNestedScrollingEnabled(false);
    }

    private void updateGreeting() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH", Locale.getDefault());
        int hour = Integer.parseInt(timeFormat.format(new Date()));
        
        String greeting;
        if (hour < 12) {
            greeting = "Good Morning";
        } else if (hour < 17) {
            greeting = "Good Afternoon";
        } else {
            greeting = "Good Evening";
        }
        
        welcomeText.setText(greeting);
    }

    private void fetchUserData() {
        // Fetch profile data
        fetchProfile();
        // Fetch dashboard data
        fetchDashboardData();
        // Fetch shift counts
        fetchShiftCounts();
        // Fetch recent activity
        fetchRecentActivity();
    }

    private void fetchProfile() {
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/users/me", authToken);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        try {
                            JSONObject user = new JSONObject(res.body).getJSONObject("user");
                            workerName.setText(user.optString("fullName", "Worker"));
                            boolean isAvailable = user.optBoolean("isAvailable", false);
                            updateAvailabilityUI(isAvailable);
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not load profile data", Toast.LENGTH_SHORT).show();
                        }
                    } else if (res.code == 401) {
                        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void fetchDashboardData() {
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/users/worker-dashboard", authToken);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        try {
                            JSONObject summary = new JSONObject(res.body).getJSONObject("summary");
                            double totalEarnings = summary.optDouble("totalEarnings", 0);
                            double todayEarnings = summary.optDouble("todayEarnings", 0);
                            double monthlyEarnings = summary.optDouble("monthlyEarnings", 0);
                            int completedShifts = summary.optInt("completedShifts", 0);
                            double rating = summary.optDouble("rating", 0);
                            boolean isAvailable = summary.optBoolean("isAvailable", false);
                            
                            // Update earnings
                            this.todayEarnings.setText("₹" + (int) todayEarnings);
                            this.monthlyEarnings.setText("₹" + (int) monthlyEarnings);
                            
                            // Update stats
                            this.completedShifts.setText(String.valueOf(completedShifts));
                            ratingValue.setText(String.format(Locale.getDefault(), "%.1f", rating));
                            
                            // Update availability
                            updateAvailabilityUI(isAvailable);
                            
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not load dashboard data", Toast.LENGTH_SHORT).show();
                        }
                    } else if (res.code == 401) {
                        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Failed to load dashboard", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void fetchShiftCounts() {
        new Thread(() -> {
            try {
                // Fetch available shifts count
                ApiClient.HttpResult openShiftsRes = ApiClient.get("/api/shifts/open", authToken);
                int availableShiftsCountValue = 0;
                if (openShiftsRes.code == 200) {
                    try {
                        JSONObject response = new JSONObject(openShiftsRes.body);
                        JSONArray shifts = response.optJSONArray("shifts");
                        if (shifts != null) {
                            availableShiftsCountValue = shifts.length();
                        }
                    } catch (Exception e) {
                        // Ignore count error
                    }
                }
                
                // Fetch my shifts count
                ApiClient.HttpResult myShiftsRes = ApiClient.get("/api/shifts/worker/my", authToken);
                int myShiftsCountValue = 0;
                if (myShiftsRes.code == 200) {
                    try {
                        JSONObject response = new JSONObject(myShiftsRes.body);
                        JSONArray shifts = response.optJSONArray("shifts");
                        if (shifts != null) {
                            myShiftsCountValue = shifts.length();
                        }
                    } catch (Exception e) {
                        // Ignore count error
                    }
                }
                
                final int finalOpenCount = availableShiftsCountValue;
                final int finalMyCount = myShiftsCountValue;
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    availableShiftsCount.setText(finalOpenCount + " shifts available");
                    myShiftsCount.setText(finalMyCount + " active shifts");
                });
                
            } catch (Exception e) {
                // Ignore count errors
            }
        }).start();
    }

    private void fetchRecentActivity() {
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/shifts/worker/my", authToken);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        try {
                            JSONObject response = new JSONObject(res.body);
                            JSONArray shifts = response.optJSONArray("shifts");
                            
                            activityList.clear();
                            if (shifts != null && shifts.length() > 0) {
                                for (int i = 0; i < Math.min(shifts.length(), 5); i++) {
                                    JSONObject shift = shifts.getJSONObject(i);
                                    String title = shift.optString("title", "Unknown Shift");
                                    String status = shift.optString("status", "unknown");
                                    String applicationStatus = shift.optString("applicationStatus", "none");
                                    String date = shift.optString("shiftDate", "");
                                    
                                    String activityText = getActivityText(title, status, applicationStatus, date);
                                    activityList.add(new ActivityItem(activityText, getActivityType(status, applicationStatus)));
                                }
                            }
                            
                            if (activityList.isEmpty()) {
                                noActivityText.setVisibility(View.VISIBLE);
                                recentActivityRecycler.setVisibility(View.GONE);
                            } else {
                                noActivityText.setVisibility(View.GONE);
                                recentActivityRecycler.setVisibility(View.VISIBLE);
                                activityAdapter.notifyDataSetChanged();
                            }
                            
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not load recent activity", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Show no activity on error
                        noActivityText.setVisibility(View.VISIBLE);
                        recentActivityRecycler.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    noActivityText.setVisibility(View.VISIBLE);
                    recentActivityRecycler.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private String getActivityText(String title, String status, String applicationStatus, String date) {
        if ("pending".equals(applicationStatus)) {
            return "Applied to " + title;
        } else if ("accepted".equals(applicationStatus)) {
            return "Started working on " + title;
        } else if ("completed".equals(status)) {
            return "Completed " + title + " • Earned ₹" + getShiftWage(title);
        } else {
            return "Applied to " + title;
        }
    }

    private int getActivityType(String status, String applicationStatus) {
        if ("completed".equals(status)) {
            return ActivityItem.TYPE_COMPLETED;
        } else if ("accepted".equals(applicationStatus)) {
            return ActivityItem.TYPE_WORKING;
        } else if ("pending".equals(applicationStatus)) {
            return ActivityItem.TYPE_APPLIED;
        }
        return ActivityItem.TYPE_APPLIED;
    }

    private String getShiftWage(String title) {
        // This would normally come from the shift data
        // For demo purposes, return a reasonable wage
        return "1200";
    }

    private void updateAvailability(boolean isAvailable) {
        new Thread(() -> {
            try {
                String payload = "{\"isAvailable\":" + isAvailable + "}";
                ApiClient.HttpResult res = ApiClient.patch("/api/users/me", authToken, payload);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        updateAvailabilityUI(isAvailable);
                        String message = isAvailable ? 
                            "You are now available for work. Employers can see your profile!" :
                            "You are not available for work. You won't receive shift notifications.";
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        
                        // Refresh shift counts
                        fetchShiftCounts();
                    } else {
                        // Revert switch state on error
                        suppressAvailabilityCallback = true;
                        availabilitySwitch.setChecked(!isAvailable);
                        suppressAvailabilityCallback = false;
                        Toast.makeText(this, "Failed to update availability", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    // Revert switch state on error
                    suppressAvailabilityCallback = true;
                    availabilitySwitch.setChecked(!isAvailable);
                    suppressAvailabilityCallback = false;
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateAvailabilityUI(boolean isAvailable) {
        if (isAvailable) {
            availabilityStatus.setText("Available for work");
            availabilityStatus.setTextColor(getResources().getColor(R.color.status_available));
            availabilityLabel.setText("Available");
            availabilityLabel.setTextColor(getResources().getColor(R.color.status_available));
            availabilityDescription.setText("You're visible to employers and will receive shift notifications");
            availabilitySwitch.setChecked(true);
        } else {
            availabilityStatus.setText("Not available");
            availabilityStatus.setTextColor(getResources().getColor(R.color.text_medium));
            availabilityLabel.setText("Not Available");
            availabilityLabel.setTextColor(getResources().getColor(R.color.text_medium));
            availabilityDescription.setText("You won't receive shift notifications until you become available");
            availabilitySwitch.setChecked(false);
        }
    }

    private void showNotificationsDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Notifications")
               .setMessage("You have no new notifications.\n\nWe'll notify you when:\n• New shifts match your skills\n• Employers accept your applications\n• Shifts are assigned to you")
               .setPositiveButton("OK", null)
               .show();
    }

    private void showSettingsDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Settings")
               .setMessage("Settings coming soon!\n\nFuture options:\n• Notification preferences\n• Language selection\n• Theme customization\n• Privacy settings")
               .setPositiveButton("OK", null)
               .show();
    }

    private void showHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Help & Support")
               .setMessage("24/7 Support\nCall: 1800-123-4567\n\nEmail Support\nsupport@shiftsync.com\n\nQuick Help:\n• Find Work: Browse available shifts and apply\n• My Shifts: View your applied and assigned shifts\n• Profile: Update your skills and experience\n• Availability: Toggle your work status\n\nCommon Issues:\n• Can't find shifts? Check your availability status\n• Payment issues? Contact employer directly\n• Profile not updating? Try refreshing the app\n\nWe're here to help you succeed!")
               .setPositiveButton("Call Support", (dialog, which) -> {
                   Intent intent = new Intent(Intent.ACTION_DIAL);
                   intent.setData(Uri.parse("tel:18001234567"));
                   startActivity(intent);
               })
               .setNegativeButton("Email Support", (dialog, which) -> {
                   Intent intent = new Intent(Intent.ACTION_SENDTO);
                   intent.setData(Uri.parse("mailto:support@shiftsync.com"));
                   intent.putExtra(Intent.EXTRA_SUBJECT, "ShiftSync Support Request");
                   startActivity(intent);
               })
               .setNeutralButton("Close", null)
               .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Set up availability switch listener
        if (availabilitySwitch != null) {
            availabilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressAvailabilityCallback) return;
                updateAvailability(isChecked);
            });
        }
    }

    // Activity item class for recent activity
    private static class ActivityItem {
        public static final int TYPE_APPLIED = 1;
        public static final int TYPE_WORKING = 2;
        public static final int TYPE_COMPLETED = 3;
        
        public String text;
        public int type;
        
        public ActivityItem(String text, int type) {
            this.text = text;
            this.type = type;
        }
    }

    // Simple adapter for recent activity
    private static class RecentActivityAdapter extends RecyclerView.Adapter<RecentActivityAdapter.ViewHolder> {
        private List<ActivityItem> items;

        public RecentActivityAdapter(List<ActivityItem> items) {
            this.items = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ActivityItem item = items.get(position);
            holder.textView.setText(item.text);
            
            // Set color based on type
            int color;
            switch (item.type) {
                case ActivityItem.TYPE_COMPLETED:
                    color = android.graphics.Color.parseColor("#4CAF50");
                    break;
                case ActivityItem.TYPE_WORKING:
                    color = android.graphics.Color.parseColor("#2196F3");
                    break;
                case ActivityItem.TYPE_APPLIED:
                    color = android.graphics.Color.parseColor("#FF9800");
                    break;
                default:
                    color = android.graphics.Color.parseColor("#757575");
                    break;
            }
            holder.textView.setTextColor(color);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            ViewHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
            }
        }
    }

    private void setupProfileUpdateReceiver() {
        profileUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("PROFILE_UPDATED".equals(intent.getAction())) {
                    String updatedData = intent.getStringExtra("updated_data");
                    if (updatedData != null) {
                        try {
                            JSONObject userData = new JSONObject(updatedData);
                            updateDashboardUI(userData);
                        } catch (Exception e) {
                            // Handle error
                        }
                    }
                }
            }
        };
        
        // Register receiver
        IntentFilter filter = new IntentFilter("PROFILE_UPDATED");
        registerReceiver(profileUpdateReceiver, filter);
    }

    private void updateDashboardUI(JSONObject userData) {
        try {
            // Update welcome message with new name
            String fullName = userData.optString("fullName", "Worker");
            welcomeText.setText("Welcome back, " + fullName + "!");
            workerName.setText(fullName);
            
            // Update availability status
            boolean isAvailable = userData.optBoolean("isAvailable", false);
            if (isAvailable) {
                availabilityStatus.setText("Available for work");
                availabilityStatus.setTextColor(getResources().getColor(R.color.status_available));
                availabilitySwitch.setChecked(true);
            } else {
                availabilityStatus.setText("Not available");
                availabilityStatus.setTextColor(getResources().getColor(R.color.text_medium));
                availabilitySwitch.setChecked(false);
            }
            
            // Update stats
            completedShifts.setText(String.valueOf(userData.optInt("completedShifts", 0)));
            ratingValue.setText(String.format(Locale.getDefault(), "%.1f", userData.optDouble("rating", 0)));
            
        } catch (Exception e) {
            // Handle error
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister receiver
        if (profileUpdateReceiver != null) {
            unregisterReceiver(profileUpdateReceiver);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when activity resumes
        fetchUserData();
    }
}
