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
import com.google.android.material.radiobutton.MaterialRadioButton;
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
    private Calendar calendar = Calendar.getInstance();
    private static final String TAG = "RequestSwapActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_swap);

        if (!SessionManager.ensureEmployer(this)) {
            return;
        }

        initializeViews();
        setupToolbar();
        setupClickListeners();
        setupDatePicker();
        loadTeamMembers();
        loadUserShift();
    }
    
    private void initializeViews() {
        tvYourShiftTitle = findViewById(R.id.tv_your_shift_title);
        tvYourShiftDatetime = findViewById(R.id.tv_your_shift_datetime);
        etSwapReason = findViewById(R.id.et_swap_reason);
        etPreferredDate = findViewById(R.id.et_preferred_date);
        rvTeamMembers = findViewById(R.id.rv_team_members);
        btnSubmitSwap = findViewById(R.id.btn_submit_swap);
        progressBar = findViewById(R.id.progress_bar);
        
        setupRecyclerView();
    }
    
    private void setupRecyclerView() {
        teamMemberAdapter = new TeamMemberAdapter(this, member -> {
            selectedTeamMemberId = member.getId();
            // Notify adapter of selection change
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
                    updateDateEditText();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            );
            
            // Set minimum date to tomorrow
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() + 86400000); // 24 hours from now
            datePickerDialog.show();
        });
    }
    
    private void updateDateEditText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        etPreferredDate.setText(dateFormat.format(calendar.getTime()));
    }
    
    private void loadUserShift() {
        // Load user's current shift (mock data for now)
        tvYourShiftTitle.setText("Morning Shift - Front Desk");
        tvYourShiftDatetime.setText("Tomorrow, 8:00 AM - 4:00 PM");
    }
    
    private void loadTeamMembers() {
        new Thread(() -> {
            try {
                String token = SessionManager.getToken(this);
                if (token == null) {
                    runOnUiThread(() -> Toast.makeText(this, "No authentication token found", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                ApiClient.HttpResult result = ApiClient.get("/api/team-members", token);
                
                if (result.code == 200 && result.body != null) {
                    JSONObject jsonResponse = new JSONObject(result.body);
                    JSONArray membersArray = jsonResponse.getJSONArray("members");
                    
                    List<TeamMember> teamMembers = new ArrayList<>();
                    for (int i = 0; i < membersArray.length(); i++) {
                        JSONObject memberJson = membersArray.getJSONObject(i);
                        TeamMember member = new TeamMember(
                            memberJson.getString("id"),
                            memberJson.getString("name"),
                            memberJson.getString("role"),
                            memberJson.optString("availability", "Available")
                        );
                        teamMembers.add(member);
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> {
                        teamMemberAdapter.setTeamMembers(teamMembers);
                    });
                    
                } else {
                    // Load mock data if API fails
                    loadMockTeamMembers();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading team members", e);
                new Handler(Looper.getMainLooper()).post(this::loadMockTeamMembers);
            }
        }).start();
    }
    
    private void loadMockTeamMembers() {
        List<TeamMember> mockMembers = new ArrayList<>();
        mockMembers.add(new TeamMember("1", "John Doe", "Front Desk Staff", "Available tomorrow"));
        mockMembers.add(new TeamMember("2", "Jane Smith", "Customer Service", "Available this week"));
        mockMembers.add(new TeamMember("3", "Mike Johnson", "Sales Associate", "Available weekends"));
        mockMembers.add(new TeamMember("4", "Sarah Wilson", "Front Desk Staff", "Available evenings"));
        
        teamMemberAdapter.setTeamMembers(mockMembers);
    }
    
    private void submitSwapRequest() {
        if (!validateInputs()) {
            return;
        }
        
        String reason = etSwapReason.getText().toString().trim();
        String preferredDate = etPreferredDate.getText().toString().trim();
        
        if (selectedTeamMemberId == null) {
            Toast.makeText(this, "Please select a team member", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading(true);
        
        new Thread(() -> {
            try {
                String token = SessionManager.getToken(this);
                if (token == null) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "No authentication token found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // Create swap request JSON
                JSONObject swapData = new JSONObject();
                swapData.put("targetUserId", selectedTeamMemberId);
                swapData.put("reason", reason);
                swapData.put("preferredDate", preferredDate);
                
                ApiClient.HttpResult result = ApiClient.post("/api/swap-requests", token, swapData.toString());
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    
                    if (result.code == 201) {
                        Toast.makeText(this, "Swap request sent successfully!", Toast.LENGTH_LONG).show();
                        
                        // Add activity to feed
                        addActivityToFeed("swap");
                        
                        finish();
                    } else if (result.code == 401) {
                        Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Failed to submit swap request: " + result.code, Toast.LENGTH_LONG).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error submitting swap request", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Error submitting swap request: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private boolean validateInputs() {
        if (etSwapReason.getText().toString().trim().isEmpty()) {
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
    
    private void addActivityToFeed(String type) {
        // This would normally be handled by real-time updates
        Log.d(TAG, "Activity added to feed: " + type);
    }
    
    // Team Member data class
    public static class TeamMember {
        private String id;
        private String name;
        private String role;
        private String availability;
        
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
