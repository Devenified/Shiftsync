package com.example.verson1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class WorkerDashboardNewActivity extends AppCompatActivity {

    private TextView workerName;
    private TextView workerStatus;
    private TextView todayEarnings;
    private TextView monthlyEarnings;
    private TextView lifetimeEarnings;
    private TextView completedCount;
    private TextView availabilityText;
    private TextView recentActivityText;
    private Switch availabilitySwitch;

    private String authToken;
    private boolean suppressAvailabilityCallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_dashboard_new);

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
        lifetimeEarnings = findViewById(R.id.lifetime_earnings);
        completedCount = findViewById(R.id.completed_count);
        availabilityText = findViewById(R.id.availability_text);
        recentActivityText = findViewById(R.id.recent_activity_text);
        availabilitySwitch = findViewById(R.id.availability_switch);

        setupAccessibility();
    }

    private void setupAccessibility() {
        findViewById(R.id.find_work_card).setContentDescription("Find work button - Search for available jobs");
        findViewById(R.id.my_shifts_card).setContentDescription("My shifts button - View your work shifts");
        findViewById(R.id.profile_card).setContentDescription("My profile button - Manage your profile");
        findViewById(R.id.help_card).setContentDescription("Help button - Get support and assistance");
        availabilitySwitch.setContentDescription("Work availability toggle - Switch to change your work status");
    }

    private void setupClickListeners() {
        findViewById(R.id.find_work_card).setOnClickListener(v ->
                startActivity(new Intent(this, WorkerOpenShiftsActivity.class)));

        findViewById(R.id.my_shifts_card).setOnClickListener(v ->
                startActivity(new Intent(this, WorkerMyShiftsActivity.class)));

        findViewById(R.id.profile_card).setOnClickListener(v ->
                startActivity(new Intent(this, WorkerProfileActivity.class)));

        findViewById(R.id.help_card).setOnClickListener(v ->
                startActivity(new Intent(this, AISkillAdvisorActivity.class)));

        setupBottomNavigation();

        availabilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressAvailabilityCallback) return;
            updateAvailability(isChecked);
        });

        findViewById(R.id.header_sign_out).setOnClickListener(v ->
                LogoutUiHelper.showConfirmLogout(this));

        findViewById(R.id.notification_icon).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));

        View earningsHero = findViewById(R.id.earnings_hero_card);
        if (earningsHero != null) {
            earningsHero.setOnClickListener(v ->
                    startActivity(new Intent(this, WorkerEarningsActivity.class)));
        }
    }

    private void setupBottomNavigation() {
        View bottomNav = findViewById(R.id.bottom_navigation);

        View findWorkBtn = bottomNav.findViewById(R.id.nav_find_work);
        if (findWorkBtn != null) {
            findWorkBtn.setOnClickListener(v ->
                    startActivity(new Intent(this, WorkerOpenShiftsActivity.class)));
            findWorkBtn.setContentDescription("Find work - Bottom navigation");
        }

        View myShiftsBtn = bottomNav.findViewById(R.id.nav_my_shifts);
        if (myShiftsBtn != null) {
            myShiftsBtn.setOnClickListener(v ->
                    startActivity(new Intent(this, WorkerMyShiftsActivity.class)));
            myShiftsBtn.setContentDescription("My shifts - Bottom navigation");
        }

        View profileBtn = bottomNav.findViewById(R.id.nav_profile);
        if (profileBtn != null) {
            profileBtn.setOnClickListener(v ->
                    startActivity(new Intent(this, WorkerProfileActivity.class)));
            profileBtn.setContentDescription("My profile - Bottom navigation");
        }

        View homeBtn = bottomNav.findViewById(R.id.nav_home);
        if (homeBtn != null) {
            homeBtn.setContentDescription("Home - Current screen");
        }
    }

    private void fetchUserData() {
        fetchProfile();
        fetchDashboardData();
        fetchUnreadNotifications();
    }

    private void fetchUnreadNotifications() {
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/notifications/unread-count", authToken);
                if (res.code == 200) {
                    JSONObject obj = new JSONObject(res.body);
                    int count = obj.optInt("count", 0);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        View dot = findViewById(R.id.notification_icon);
                        if (dot instanceof android.widget.FrameLayout) {
                            android.widget.FrameLayout fl = (android.widget.FrameLayout) dot;
                            if (fl.getChildCount() > 1) {
                                fl.getChildAt(1).setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                            }
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        }).start();
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
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show());
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
                            double todayEarningsVal = summary.optDouble("todayEarnings", 0);
                            double monthlyEarningsVal = summary.optDouble("monthlyEarnings", 0);
                            double lifetimeEarningsVal = summary.optDouble("totalEarnings", 0);
                            boolean isAvailable = summary.optBoolean("isAvailable", false);
                            int completedShifts = summary.optInt("completedShifts", 0);

                            todayEarnings.setText("\u20B9" + (int) todayEarningsVal);
                            monthlyEarnings.setText("\u20B9" + (int) monthlyEarningsVal);
                            if (lifetimeEarnings != null) {
                                lifetimeEarnings.setText("\u20B9" + (int) lifetimeEarningsVal);
                            }
                            if (completedCount != null) {
                                completedCount.setText(String.valueOf(completedShifts));
                            }
                            updateAvailabilityUI(isAvailable);

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
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show());
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
                        Toast.makeText(this,
                                isAvailable ? "You are now available for work" : "You are not available",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        suppressAvailabilityCallback = true;
                        availabilitySwitch.setChecked(!isAvailable);
                        suppressAvailabilityCallback = false;
                        Toast.makeText(this, "Failed to update availability", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
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
            workerStatus.setBackgroundResource(R.drawable.bg_pill_success);
            availabilityText.setText(getString(R.string.available_today));
        } else {
            workerStatus.setText(getString(R.string.not_available));
            workerStatus.setBackgroundResource(R.drawable.bg_pill_warning);
            availabilityText.setText(getString(R.string.not_available_today));
        }
    }

    private void showHelpDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Help & Support")
                .setMessage("24/7 Support\nCall: 1800-123-4567\n\n" +
                        "Email Support\nsupport@shiftsync.com\n\n" +
                        "Quick Help\n" +
                        "- Find Work: Browse available shifts and apply\n" +
                        "- My Shifts: View your applied and assigned shifts\n" +
                        "- Profile: Update your skills and experience\n" +
                        "- Availability: Toggle your work status")
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
    protected void onResume() {
        super.onResume();
        if (!SessionManager.isWorker(this)) {
            return;
        }
        fetchUserData();
        refreshNotificationBadge();
    }

    private void refreshNotificationBadge() {
        TextView badge = findViewById(R.id.notification_badge);
        NotificationBadgeHelper.refresh(this, badge);
    }
}
