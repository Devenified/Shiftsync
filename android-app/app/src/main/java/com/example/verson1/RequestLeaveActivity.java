package com.example.verson1;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class RequestLeaveActivity extends AppCompatActivity {

    private RadioGroup rgLeaveType;
    private TextInputEditText etStartDate;
    private TextInputEditText etEndDate;
    private TextInputEditText etReason;
    private TextView tvSickBalance;
    private TextView tvCasualBalance;
    private TextView tvPaidBalance;
    private MaterialButton btnSubmitLeave;
    private View progressBar;
    
    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();
    private static final String TAG = "RequestLeaveActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_leave);

        if (!SessionManager.ensureEmployer(this)) {
            return;
        }

        initializeViews();
        setupToolbar();
        setupClickListeners();
        setupDatePickers();
        loadLeaveBalance();
    }
    
    private void initializeViews() {
        rgLeaveType = findViewById(R.id.rg_leave_type);
        etStartDate = findViewById(R.id.et_start_date);
        etEndDate = findViewById(R.id.et_end_date);
        etReason = findViewById(R.id.et_reason);
        tvSickBalance = findViewById(R.id.tv_sick_balance);
        tvCasualBalance = findViewById(R.id.tv_casual_balance);
        tvPaidBalance = findViewById(R.id.tv_paid_balance);
        btnSubmitLeave = findViewById(R.id.btn_submit_leave);
        progressBar = findViewById(R.id.progress_bar);
        
        // Set default leave type
        rgLeaveType.check(R.id.rb_sick_leave);
    }
    
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        LogoutUiHelper.attachSignOutMenu(this, toolbar);
    }
    
    private void setupClickListeners() {
        btnSubmitLeave.setOnClickListener(v -> submitLeaveRequest());
    }
    
    private void setupDatePickers() {
        // Start date picker
        etStartDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    startCalendar.set(Calendar.YEAR, year);
                    startCalendar.set(Calendar.MONTH, month);
                    startCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateStartDateEditText();
                },
                startCalendar.get(Calendar.YEAR),
                startCalendar.get(Calendar.MONTH),
                startCalendar.get(Calendar.DAY_OF_MONTH)
            );
            
            // Set minimum date to tomorrow
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() + 86400000); // 24 hours from now
            datePickerDialog.show();
        });
        
        // End date picker
        etEndDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    endCalendar.set(Calendar.YEAR, year);
                    endCalendar.set(Calendar.MONTH, month);
                    endCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateEndDateEditText();
                },
                endCalendar.get(Calendar.YEAR),
                endCalendar.get(Calendar.MONTH),
                endCalendar.get(Calendar.DAY_OF_MONTH)
            );
            
            // Set minimum date to start date
            datePickerDialog.getDatePicker().setMinDate(startCalendar.getTimeInMillis());
            datePickerDialog.show();
        });
    }
    
    private void updateStartDateEditText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        etStartDate.setText(dateFormat.format(startCalendar.getTime()));
        
        // Update end date minimum
        if (endCalendar.getTimeInMillis() < startCalendar.getTimeInMillis()) {
            endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
            updateEndDateEditText();
        }
    }
    
    private void updateEndDateEditText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        etEndDate.setText(dateFormat.format(endCalendar.getTime()));
    }
    
    private void loadLeaveBalance() {
        // Load leave balance from API or use mock data
        tvSickBalance.setText("5 days");
        tvCasualBalance.setText("10 days");
        tvPaidBalance.setText("15 days");
    }
    
    private void submitLeaveRequest() {
        if (!validateInputs()) {
            return;
        }
        
        String leaveType = getSelectedLeaveType();
        String startDate = etStartDate.getText().toString().trim();
        String endDate = etEndDate.getText().toString().trim();
        String reason = etReason.getText().toString().trim();
        
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
                
                // Create leave request JSON
                JSONObject leaveData = new JSONObject();
                leaveData.put("type", leaveType);
                leaveData.put("startDate", startDate);
                leaveData.put("endDate", endDate);
                leaveData.put("reason", reason);
                
                ApiClient.HttpResult result = ApiClient.post("/api/leave-requests", token, leaveData.toString());
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    
                    if (result.code == 201) {
                        Toast.makeText(this, "Leave request submitted successfully!", Toast.LENGTH_LONG).show();
                        
                        // Add activity to feed
                        addActivityToFeed("leave");
                        
                        finish();
                    } else if (result.code == 401) {
                        Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Failed to submit leave request: " + result.code, Toast.LENGTH_LONG).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error submitting leave request", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Error submitting leave request: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private boolean validateInputs() {
        if (etStartDate.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please select start date", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (etEndDate.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please select end date", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (etReason.getText().toString().trim().isEmpty()) {
            etReason.setError("Please provide a reason for leave");
            return false;
        }
        
        return true;
    }
    
    private String getSelectedLeaveType() {
        int selectedId = rgLeaveType.getCheckedRadioButtonId();
        if (selectedId == R.id.rb_sick_leave) return "sick";
        if (selectedId == R.id.rb_casual_leave) return "casual";
        if (selectedId == R.id.rb_paid_leave) return "paid";
        return "sick"; // default
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSubmitLeave.setEnabled(!show);
        btnSubmitLeave.setText(show ? "Submitting..." : "Submit Leave Request");
    }
    
    private void addActivityToFeed(String type) {
        // This would normally be handled by real-time updates
        Log.d(TAG, "Activity added to feed: " + type);
    }
}
