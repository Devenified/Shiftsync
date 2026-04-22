package com.example.verson1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class WorkerDashboardActivity extends AppCompatActivity {
    private static final String TAG = "WorkerDashboard";

    private TextView welcomeText;
    private TextView earningsText;
    private TextView statusText;
    private ImageView statusIcon;
    private SwitchMaterial availabilitySwitch;
    private BottomNavigationView bottomNavigation;

    private String authToken;
    private boolean suppressAvailabilityCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_dashboard);

        if (!SessionManager.ensureWorker(this)) {
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> LogoutUiHelper.onMenuItemLogout(this, item.getItemId()));

        welcomeText = findViewById(R.id.welcome_text);
        earningsText = findViewById(R.id.earnings_text);
        statusText = findViewById(R.id.status_text);
        statusIcon = findViewById(R.id.status_icon);
        availabilitySwitch = findViewById(R.id.availability_switch);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        MaterialButton findWorkButton = findViewById(R.id.btn_find_work);
        MaterialButton logoutButton = findViewById(R.id.btn_logout);

        authToken = SessionManager.getToken(this);
        if (authToken == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        setupBottomNav();
        availabilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressAvailabilityCallback) return;
            updateAvailability(isChecked);
        });
        findWorkButton.setOnClickListener(v -> startActivity(new Intent(this, WorkerOpenShiftsActivity.class)));
        logoutButton.setOnClickListener(v -> LogoutUiHelper.showConfirmLogout(this));

        fetchWorkerDashboard();
        fetchProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!SessionManager.isWorker(this)) {
            return;
        }
        bottomNavigation.setSelectedItemId(R.id.nav_worker_home);
        fetchWorkerDashboard();
        fetchProfile();
    }

    private void setupBottomNav() {
        bottomNavigation.setSelectedItemId(R.id.nav_worker_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_worker_home) {
                return true;
            }
            if (itemId == R.id.nav_worker_shifts) {
                startActivity(new Intent(this, WorkerMyShiftsActivity.class));
                return true;
            }
            if (itemId == R.id.nav_worker_profile) {
                startActivity(new Intent(this, WorkerProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    private void fetchProfile() {
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                URL url = new URL(ApiClient.BASE_URL + "/api/users/me");
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("Authorization", "Bearer " + authToken);
                con.setConnectTimeout(6000);
                con.setReadTimeout(6000);

                int responseCode = con.getResponseCode();
                String responseBody = readResponseBody(con, responseCode);

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (responseCode == 200) {
                        try {
                            JSONObject user = new JSONObject(responseBody).getJSONObject("user");
                            String name = user.optString("fullName", "Worker");
                            welcomeText.setText(getString(R.string.toolbar_worker) + " · " + name);
                            boolean available = user.optBoolean("isAvailable", false);
                            setAvailabilitySwitchUi(available);
                        } catch (Exception e) {
                            Log.e(TAG, "Profile parse error", e);
                        }
                    } else if (responseCode == 401) {
                        Toast.makeText(this, "Session expired. Login again.", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Profile fetch error", e);
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private void fetchWorkerDashboard() {
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                URL url = new URL(ApiClient.BASE_URL + "/api/users/worker-dashboard");
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("Authorization", "Bearer " + authToken);
                con.setConnectTimeout(6000);
                con.setReadTimeout(6000);

                int responseCode = con.getResponseCode();
                String responseBody = readResponseBody(con, responseCode);

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (responseCode == 200) {
                        try {
                            JSONObject summary = new JSONObject(responseBody).getJSONObject("summary");
                            double totalEarnings = summary.optDouble("totalEarnings", 0.0);
                            boolean isAvailable = summary.optBoolean("isAvailable", false);

                            earningsText.setText("₹" + ((int) totalEarnings));
                            setAvailabilitySwitchUi(isAvailable);
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not parse dashboard data", Toast.LENGTH_SHORT).show();
                        }
                    } else if (responseCode == 401) {
                        Toast.makeText(this, "Session expired. Login again.", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else if (responseCode == 403) {
                        Toast.makeText(this, "Wrong account type for this screen", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Failed to load worker dashboard", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Dashboard fetch error", e);
                new Handler(Looper.getMainLooper()).post(
                        () -> Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private void updateAvailability(boolean isAvailable) {
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                URL url = new URL(ApiClient.BASE_URL + "/api/users/me");
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("PATCH");
                con.setRequestProperty("Authorization", "Bearer " + authToken);
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                con.setConnectTimeout(6000);
                con.setReadTimeout(6000);

                JSONObject payload = new JSONObject();
                payload.put("isAvailable", isAvailable);
                OutputStream os = con.getOutputStream();
                os.write(payload.toString().getBytes());
                os.flush();
                os.close();

                int responseCode = con.getResponseCode();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (responseCode == 200) {
                        applyAvailabilityUI(isAvailable);
                    } else {
                        setAvailabilitySwitchUi(!isAvailable);
                        Toast.makeText(this, "Could not update availability", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Availability update error", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    setAvailabilitySwitchUi(!isAvailable);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private void setAvailabilitySwitchUi(boolean isAvailable) {
        suppressAvailabilityCallback = true;
        availabilitySwitch.setChecked(isAvailable);
        suppressAvailabilityCallback = false;
        applyAvailabilityUI(isAvailable);
    }

    private void applyAvailabilityUI(boolean isAvailable) {
        statusText.setText(isAvailable ? "READY" : "OFF DUTY");
        int color = ContextCompat.getColor(this, isAvailable ? R.color.status_available : R.color.status_busy);
        statusText.setTextColor(color);
        statusIcon.setImageResource(isAvailable
                ? android.R.drawable.presence_online
                : android.R.drawable.presence_busy);
    }

    private String readResponseBody(HttpURLConnection con, int responseCode) throws Exception {
        InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                ? con.getInputStream()
                : con.getErrorStream();
        if (inputStream == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}
