package com.example.verson1;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RequestSwapActivity extends AppCompatActivity {

    private TextView tvYourShiftTitle;
    private TextView tvYourShiftDatetime;
    private TextInputEditText etSwapReason;
    private TextInputEditText etPreferredDate;
    private RecyclerView rvTeamMembers;
    private MaterialButton btnSubmitSwap;
    private View progressBar;

    private TeamMemberAdapter teamMemberAdapter;
    private String selectedTeamMemberId = null;
    private String selectedShiftId = null;
    private final Calendar calendar = Calendar.getInstance();
    private static final String TAG = "RequestSwapActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_swap);

        if (!SessionManager.isLoggedIn(this)) {
            SessionManager.logoutToLogin(this);
            return;
        }

        initializeViews();
        setupToolbar();
        setupClickListeners();
        setupDatePicker();
        loadSwapOptions();
    }

    private void initializeViews() {
        tvYourShiftTitle = findViewById(R.id.tv_your_shift_title);
        tvYourShiftDatetime = findViewById(R.id.tv_your_shift_datetime);
        etSwapReason = findViewById(R.id.et_swap_reason);
        etPreferredDate = findViewById(R.id.et_preferred_date);
        rvTeamMembers = findViewById(R.id.rv_team_members);
        btnSubmitSwap = findViewById(R.id.btn_submit_swap);
        progressBar = findViewById(R.id.progress_bar);

        teamMemberAdapter = new TeamMemberAdapter(this, member -> {
            selectedTeamMemberId = member.getId();
            teamMemberAdapter.setSelectedMember(selectedTeamMemberId);
        });
        rvTeamMembers.setLayoutManager(new LinearLayoutManager(this));
        rvTeamMembers.setAdapter(teamMemberAdapter);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        LogoutUiHelper.attachSignOutMenu(this, toolbar);
    }

    private void setupClickListeners() {
        btnSubmitSwap.setOnClickListener(v -> submitSwapRequest());
    }

    private void setupDatePicker() {
        etPreferredDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        etPreferredDate.setText(dateFormat.format(calendar.getTime()));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
            datePickerDialog.show();
        });
    }

    private void loadSwapOptions() {
        showLoading(true);
        new Thread(() -> {
            try {
                String token = SessionManager.getToken(this);
                if (token == null) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        SessionManager.logoutToLogin(this);
                    });
                    return;
                }

                ApiClient.HttpResult result = ApiClient.get("/api/swaps/options", token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    if (result.code == 200) {
                        try {
                            JSONObject jsonResponse = new JSONObject(result.body);
                            JSONArray shifts = jsonResponse.optJSONArray("shifts");
                            if (shifts != null && shifts.length() > 0) {
                                JSONObject shift = shifts.getJSONObject(0);
                                selectedShiftId = shift.optString("id", "");
                                tvYourShiftTitle.setText(shift.optString("title", "Shift"));
                                tvYourShiftDatetime.setText(
                                        shift.optString("shiftDate", "") + "  " +
                                        shift.optString("startTime", "") + " - " +
                                        shift.optString("endTime", "")
                                );
                            } else {
                                tvYourShiftTitle.setText("No eligible shift found");
                                tvYourShiftDatetime.setText("You need at least one active shift to request a swap.");
                            }

                            JSONArray membersArray = jsonResponse.optJSONArray("teamMembers");
                            List<TeamMember> teamMembers = new ArrayList<>();
                            if (membersArray != null) {
                                for (int i = 0; i < membersArray.length(); i++) {
                                    JSONObject memberJson = membersArray.getJSONObject(i);
                                    teamMembers.add(new TeamMember(
                                            memberJson.optString("id", ""),
                                            memberJson.optString("name", "Worker"),
                                            memberJson.optString("role", "Worker"),
                                            memberJson.optString("availability", "Availability not set")
                                    ));
                                }
                            }
                            teamMemberAdapter.setTeamMembers(teamMembers);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed parsing swap options", e);
                            Toast.makeText(this, "Could not load swap options", Toast.LENGTH_SHORT).show();
                        }
                    } else if (result.code == 401) {
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Failed to load swap options", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading swap options", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void submitSwapRequest() {
        if (selectedShiftId == null || selectedShiftId.isEmpty()) {
            Toast.makeText(this, "No active shift available for swap", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedTeamMemberId == null || selectedTeamMemberId.isEmpty()) {
            Toast.makeText(this, "Please select a team member", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validateInputs()) {
            return;
        }

        String reason = etSwapReason.getText() != null ? etSwapReason.getText().toString().trim() : "";
        String preferredDate = etPreferredDate.getText() != null ? etPreferredDate.getText().toString().trim() : "";

        showLoading(true);
        new Thread(() -> {
            try {
                String token = SessionManager.getToken(this);
                if (token == null) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        SessionManager.logoutToLogin(this);
                    });
                    return;
                }

                JSONObject swapData = new JSONObject();
                swapData.put("targetUserId", selectedTeamMemberId);
                swapData.put("shiftId", selectedShiftId);
                swapData.put("notes", reason);
                if (!preferredDate.isEmpty()) {
                    swapData.put("preferredDate", preferredDate);
                }

                ApiClient.HttpResult result = ApiClient.post("/api/swaps/request", token, swapData.toString());
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    if (result.code == 201) {
                        Toast.makeText(this, "Swap request sent successfully", Toast.LENGTH_LONG).show();
                        finish();
                    } else if (result.code == 401) {
                        SessionManager.logoutToLogin(this);
                    } else {
                        try {
                            String message = new JSONObject(result.body).optString("message", "Failed to submit swap request");
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        } catch (Exception ignored) {
                            Toast.makeText(this, "Failed to submit swap request", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error submitting swap request", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Error submitting swap request", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean validateInputs() {
        if (etSwapReason.getText() == null || etSwapReason.getText().toString().trim().isEmpty()) {
            etSwapReason.setError("Please provide a reason for the swap");
            return false;
        }
        return true;
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSubmitSwap.setEnabled(!show);
        btnSubmitSwap.setText(show ? "Submitting..." : "Submit Swap Request");
    }

    public static class TeamMember {
        private final String id;
        private final String name;
        private final String role;
        private final String availability;

        public TeamMember(String id, String name, String role, String availability) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.availability = availability;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getRole() { return role; }
        public String getAvailability() { return availability; }
    }
}
