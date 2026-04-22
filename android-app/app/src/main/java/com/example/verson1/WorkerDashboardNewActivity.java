package com.example.verson1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONObject;

public class WorkerDashboardNewActivity extends AppCompatActivity {

    private TextView workerName;
    private TextView workerStatus;
    private TextView todayEarnings;
    private TextView monthlyEarnings;
    private TextView availabilityText;
    private TextView recentActivityText;
    private Switch availabilitySwitch;
    
    private String authToken;
    private boolean suppressAvailabilityCallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_dashboard_new);

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
        fetchUserData();
    }

    private void initializeViews() {
        workerName = findViewById(R.id.worker_name);
        workerStatus = findViewById(R.id.worker_status);
        todayEarnings = findViewById(R.id.today_earnings);
        monthlyEarnings = findViewById(R.id.monthly_earnings);
        availabilityText = findViewById(R.id.availability_text);
        recentActivityText = findViewById(R.id.recent_activity_text);
        availabilitySwitch = findViewById(R.id.availability_switch);
        
        // Set accessibility descriptions
        setupAccessibility();
    }

    private void setupAccessibility() {
        // Set content descriptions for screen readers
        findViewById(R.id.find_work_card).setContentDescription("Find work button - Search for available jobs");
        findViewById(R.id.my_shifts_card).setContentDescription("My shifts button - View your work shifts");
        findViewById(R.id.profile_card).setContentDescription("My profile button - Manage your profile");
        findViewById(R.id.help_card).setContentDescription("Help button - Get support and assistance");
        availabilitySwitch.setContentDescription("Work availability toggle - Switch to change your work status");
    }

    private void setupClickListeners() {
        // Find Work Card
        findViewById(R.id.find_work_card).setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerOpenShiftsActivity.class));
        });

        // My Shifts Card
        findViewById(R.id.my_shifts_card).setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerMyShiftsActivity.class));
        });

        // Profile Card
        findViewById(R.id.profile_card).setOnClickListener(v -> {
            startActivity(new Intent(this, WorkerProfileActivity.class));
        });

        // Help Card
        findViewById(R.id.help_card).setOnClickListener(v -> {
            showHelpDialog();
        });

        // Bottom Navigation
        setupBottomNavigation();

        // Availability Switch
        availabilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressAvailabilityCallback) return;
            updateAvailability(isChecked);
        });

        findViewById(R.id.header_sign_out).setOnClickListener(v -> LogoutUiHelper.showConfirmLogout(this));

        // Notification Icon
        findViewById(R.id.notification_icon).setOnClickListener(v -> {
            Toast.makeText(this, "No new notifications", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void setupBottomNavigation() {
        View bottomNav = findViewById(R.id.bottom_navigation);
        
        // Find Work button in bottom nav
        View findWorkBtn = bottomNav.findViewById(R.id.nav_find_work);
        if (findWorkBtn != null) {
            findWorkBtn.setOnClickListener(v -> {
                startActivity(new Intent(this, WorkerOpenShiftsActivity.class));
            });
            findWorkBtn.setContentDescription("Find work - Bottom navigation");
        }

        // My Shifts button in bottom nav
        View myShiftsBtn = bottomNav.findViewById(R.id.nav_my_shifts);
        if (myShiftsBtn != null) {
            myShiftsBtn.setOnClickListener(v -> {
                startActivity(new Intent(this, WorkerMyShiftsActivity.class));
            });
            myShiftsBtn.setContentDescription("My shifts - Bottom navigation");
        }

        // Profile button in bottom nav
        View profileBtn = bottomNav.findViewById(R.id.nav_profile);
        if (profileBtn != null) {
            profileBtn.setOnClickListener(v -> {
                startActivity(new Intent(this, WorkerProfileActivity.class));
            });
            profileBtn.setContentDescription("My profile - Bottom navigation");
        }
        
        // Home button (current screen)
        View homeBtn = bottomNav.findViewById(R.id.nav_home);
        if (homeBtn != null) {
            homeBtn.setContentDescription("Home - Current screen");
        }
    }

    private void fetchUserData() {
        // Fetch profile data
        fetchProfile();
        // Fetch dashboard data
        fetchDashboardData();
    }

    private void fetchProfile() {
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/users/me", authToken);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        try {
                            JSONObject user = new JSONObject(res.body).getJSONObject("user");
                            String name = user.optString("fullName", "Worker");
                            boolean isAvailable = user.optBoolean("isAvailable", false);
                            
                            workerName.setText(name);
                            updateAvailabilityUI(isAvailable);
                        } catch (Exception e) {
                            Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show();
                        }
                    } else if (res.code == 401) {
                        Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
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
                            boolean isAvailable = summary.optBoolean("isAvailable", false);
                            int completedShifts = summary.optInt("completedShifts", 0);
                            
                            // Update earnings (simplified - in real app would calculate today's earnings)
                            todayEarnings.setText(String.format(getString(R.string.earnings_today_format), (int) totalEarnings));
                            monthlyEarnings.setText(String.format(getString(R.string.earnings_month_format), (int) totalEarnings));
                            
                            // Update availability
                            updateAvailabilityUI(isAvailable);
                            
                            // Update recent activity
                            if (completedShifts > 0) {
                                recentActivityText.setText(String.format(getString(R.string.completed_shifts), completedShifts));
                            } else {
                                recentActivityText.setText(getString(R.string.no_shifts_yet));
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Error loading dashboard", Toast.LENGTH_SHORT).show();
                        }
                    } else if (res.code == 401) {
                        Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateAvailability(boolean isAvailable) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("isAvailable", isAvailable);
                
                ApiClient.HttpResult res = ApiClient.patch("/api/users/me", authToken, payload.toString());
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        updateAvailabilityUI(isAvailable);
                        Toast.makeText(this, isAvailable ? "You are now available for work" : "You are not available", 
                                       Toast.LENGTH_SHORT).show();
                    } else {
                        // Revert switch if update failed
                        suppressAvailabilityCallback = true;
                        availabilitySwitch.setChecked(!isAvailable);
                        suppressAvailabilityCallback = false;
                        Toast.makeText(this, "Failed to update availability", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    // Revert switch if update failed
                    suppressAvailabilityCallback = true;
                    availabilitySwitch.setChecked(!isAvailable);
                    suppressAvailabilityCallback = false;
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateAvailabilityUI(boolean isAvailable) {
        suppressAvailabilityCallback = true;
        availabilitySwitch.setChecked(isAvailable);
        suppressAvailabilityCallback = false;
        
        if (isAvailable) {
            workerStatus.setText(getString(R.string.available_for_work));
            workerStatus.setTextColor(getResources().getColor(R.color.status_available));
            availabilityText.setText(getString(R.string.available_today));
        } else {
            workerStatus.setText(getString(R.string.not_available));
            workerStatus.setTextColor(getResources().getColor(R.color.text_medium));
            availabilityText.setText(getString(R.string.not_available_today));
        }
    }

    private void showHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Help & Support")
               .setMessage("📞 **24/7 Support**\nCall: 1800-123-4567\n\n" +
                       "📧 **Email Support**\nsupport@shiftsync.com\n\n" +
                       "💬 **Quick Help**\n" +
                       "• **Find Work**: Browse available shifts and apply\n" +
                       "• **My Shifts**: View your applied and assigned shifts\n" +
                       "• **Profile**: Update your skills and experience\n" +
                       "• **Availability**: Toggle your work status\n\n" +
                       "🔧 **Common Issues**\n" +
                       "• Can't find shifts? Check your availability status\n" +
                       "• Payment issues? Contact employer directly\n" +
                       "• Profile not updating? Try refreshing the app\n\n" +
                       "We're here to help you succeed! 🚀")
               .setPositiveButton("Call Support", (dialog, which) -> {
                   // Open phone dialer
                   Intent intent = new Intent(Intent.ACTION_DIAL);
                   intent.setData(Uri.parse("tel:18001234567"));
                   startActivity(intent);
               })
               .setNegativeButton("Email Support", (dialog, which) -> {
                   // Open email app
                   Intent intent = new Intent(Intent.ACTION_SENDTO);
                   intent.setData(Uri.parse("mailto:support@shiftsync.com"));
                   intent.putExtra(Intent.EXTRA_SUBJECT, "ShiftSync Support Request");
                   startActivity(intent);
               })
               .setNeutralButton("Close", null)
               .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!SessionManager.isWorker(this)) {
            return;
        }
        // Refresh data when returning to this screen
        fetchUserData();
    }
}
