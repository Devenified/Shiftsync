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
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

public class WorkerProfileActivity extends AppCompatActivity {

    private TextInputEditText skillsInput;
    private TextInputEditText locationInput;
    private TextInputEditText experienceInput;
    private TextView statsText;
    private ProgressBar progressBar;

    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_profile);

        if (!SessionManager.ensureWorker(this)) {
            return;
        }

        token = SessionManager.getToken(this);
        if (token == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        skillsInput = findViewById(R.id.skills_input);
        locationInput = findViewById(R.id.location_input);
        experienceInput = findViewById(R.id.experience_input);
        statsText = findViewById(R.id.stats_text);
        progressBar = findViewById(R.id.progress);

        findViewById(R.id.btn_save_profile).setOnClickListener(v -> saveProfile());
        findViewById(R.id.btn_logout).setOnClickListener(v -> LogoutUiHelper.showConfirmLogout(this));

        loadProfile();
    }

    private void loadProfile() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/users/me", token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (res.code != 200) {
                        Toast.makeText(this, "Could not load profile", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        JSONObject user = new JSONObject(res.body).getJSONObject("user");
                        JSONArray skills = user.optJSONArray("skills");
                        if (skills != null && skills.length() > 0) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < skills.length(); i++) {
                                if (i > 0) sb.append(", ");
                                sb.append(skills.optString(i, ""));
                            }
                            skillsInput.setText(sb.toString());
                        }
                        locationInput.setText(user.optString("location", ""));
                        experienceInput.setText(String.valueOf(user.optInt("experienceYears", 0)));
                        double rating = user.optDouble("rating", 0);
                        int completed = user.optInt("completedShifts", 0);
                        double earnings = user.optDouble("totalEarnings", 0);
                        statsText.setText(
                                "Rating: "
                                        + rating
                                        + " · Completed shifts: "
                                        + completed
                                        + " · Total earnings: ₹"
                                        + (int) earnings
                        );
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse error", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void saveProfile() {
        if (skillsInput.getText() == null || locationInput.getText() == null || experienceInput.getText() == null) {
            return;
        }
        String skillsRaw = skillsInput.getText().toString().trim();
        String location = locationInput.getText().toString().trim();
        String expStr = experienceInput.getText().toString().trim();
        int expYears = 0;
        try {
            if (!expStr.isEmpty()) expYears = (int) Double.parseDouble(expStr);
        } catch (NumberFormatException ignored) {
        }
        final int experienceYearsFinal = expYears;

        JSONArray skillsArr = new JSONArray();
        if (!skillsRaw.isEmpty()) {
            for (String part : skillsRaw.split(",")) {
                String s = part.trim();
                if (!s.isEmpty()) skillsArr.put(s);
            }
        }

        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("skills", skillsArr);
                payload.put("location", location);
                payload.put("experienceYears", experienceYearsFinal);
                payload.put("hasProfile", true);

                ApiClient.HttpResult res = ApiClient.patch("/api/users/me", token, payload.toString());
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (res.code == 200) {
                        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

}
