package com.example.verson1;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

public class WorkerPublicProfileActivity extends AppCompatActivity {

    public static final String EXTRA_WORKER_ID = "worker_id";

    private String token;
    private String workerId;

    private ProgressBar progress;
    private TextView name;
    private TextView meta;
    private TextView skills;
    private TextView bio;
    private TextView stats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_public_profile);

        if (!SessionManager.ensureEmployer(this)) return;

        token = SessionManager.getToken(this);
        if (token == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        workerId = getIntent().getStringExtra(EXTRA_WORKER_ID);
        if (workerId == null || workerId.isEmpty()) {
            Toast.makeText(this, "Missing worker id", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        LogoutUiHelper.attachSignOutMenu(this, toolbar);

        progress = findViewById(R.id.progress);
        name = findViewById(R.id.tv_name);
        meta = findViewById(R.id.tv_meta);
        skills = findViewById(R.id.tv_skills);
        bio = findViewById(R.id.tv_bio);
        stats = findViewById(R.id.tv_stats);

        loadProfile();
    }

    private void loadProfile() {
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/users/" + workerId, token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    progress.setVisibility(View.GONE);
                    if (res.code == 200) {
                        try {
                            JSONObject u = new JSONObject(res.body).getJSONObject("user");
                            name.setText(u.optString("fullName", "Worker"));

                            String location = u.optString("location", "");
                            double rating = u.optDouble("rating", 0);
                            int completed = u.optInt("completedShifts", 0);
                            boolean available = u.optBoolean("isAvailable", false);
                            meta.setText((location.isEmpty() ? "Location not set" : location)
                                    + " • " + (available ? "Available" : "Not available")
                                    + " • ★ " + String.format(java.util.Locale.getDefault(), "%.1f", rating)
                                    + " • " + completed + " shifts");

                            JSONArray arr = u.optJSONArray("skills");
                            if (arr != null && arr.length() > 0) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < arr.length(); i++) {
                                    if (i > 0) sb.append(", ");
                                    sb.append(arr.optString(i));
                                }
                                skills.setText(sb.toString());
                            } else {
                                skills.setText("No skills listed");
                            }

                            String bioText = u.optString("bio", "");
                            bio.setText(bioText.isEmpty() ? "No bio provided." : bioText);

                            int years = u.optInt("experienceYears", 0);
                            double earnings = u.optDouble("totalEarnings", 0);
                            stats.setText("Experience: " + years + " years\n"
                                    + "Lifetime earnings: ₹" + (int) earnings);

                        } catch (Exception e) {
                            Toast.makeText(this, "Could not parse profile", Toast.LENGTH_SHORT).show();
                        }
                    } else if (res.code == 401) {
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Could not load profile", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}

