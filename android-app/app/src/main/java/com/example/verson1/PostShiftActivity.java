package com.example.verson1;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.DatePicker;
import android.widget.RadioGroup;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PostShiftActivity extends AppCompatActivity {

    private TextInputEditText etShiftTitle;
    private TextInputEditText etDate;
    private TextInputEditText etStartTime;
    private TextInputEditText etEndTime;
    private TextInputEditText etLocation;
    private TextInputEditText etWage;
    private TextInputEditText etDescription;
    private RadioGroup rgShiftType;
    private MaterialButton btnPostShift;
    private View progressBar;
    
    private Calendar calendar = Calendar.getInstance();
    private static final String TAG = "PostShiftActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_shift);

        if (!SessionManager.ensureEmployer(this)) {
            return;
        }

        initializeViews();
        setupToolbar();
        setupClickListeners();
        setupDateTimePickers();
    }
    
    private void initializeViews() {
        etShiftTitle = findViewById(R.id.et_shift_title);
        etDate = findViewById(R.id.et_date);
        etStartTime = findViewById(R.id.et_start_time);
        etEndTime = findViewById(R.id.et_end_time);
        etLocation = findViewById(R.id.et_location);
        etWage = findViewById(R.id.et_wage);
        etDescription = findViewById(R.id.et_description);
        rgShiftType = findViewById(R.id.rg_shift_type);
        btnPostShift = findViewById(R.id.btn_post_shift);
        progressBar = findViewById(R.id.progress_bar);
        
        // Set default shift type to morning
        rgShiftType.check(R.id.rb_morning);
    }
    
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        LogoutUiHelper.attachSignOutMenu(this, toolbar);
    }
    
    private void setupClickListeners() {
        btnPostShift.setOnClickListener(v -> postShift());
    }
    
    private void setupDateTimePickers() {
        // Date picker
        etDate.setOnClickListener(v -> {
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
            
            // Set minimum date to today
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });
        
        // Start time picker
        etStartTime.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    updateStartTimeEditText();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            );
            timePickerDialog.show();
        });
        
        // End time picker
        etEndTime.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    updateEndTimeEditText();
                },
                calendar.get(Calendar.HOUR_OF_DAY) + 8, // Default 8 hours later
                calendar.get(Calendar.MINUTE),
                false
            );
            timePickerDialog.show();
        });
    }
    
    private void updateDateEditText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        etDate.setText(dateFormat.format(calendar.getTime()));
    }
    
    private void updateStartTimeEditText() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        etStartTime.setText(timeFormat.format(calendar.getTime()));
    }
    
    private void updateEndTimeEditText() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        etEndTime.setText(timeFormat.format(calendar.getTime()));
    }
    
    private void postShift() {
        // Validate inputs
        if (!validateInputs()) {
            return;
        }
        
        // Get shift data
        String shiftTitle = etShiftTitle.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        String startTime = etStartTime.getText().toString().trim();
        String endTime = etEndTime.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String wageStr = etWage.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        
        String shiftType = getSelectedShiftType();
        double wage = Double.parseDouble(wageStr);
        
        // Show loading
        showLoading(true);
        
        // Post shift on background thread
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
                
                JSONObject shiftData = new JSONObject();
                shiftData.put("title", shiftTitle);
                shiftData.put("skillRequired", shiftType);
                shiftData.put("shiftDate", date);
                shiftData.put("startTime", startTime);
                shiftData.put("endTime", endTime);
                shiftData.put("location", location);
                shiftData.put("wage", wage);
                shiftData.put("description", description);
                
                // Make API call
                ApiClient.HttpResult result = ApiClient.post("/api/shifts", token, shiftData.toString());
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    
                    if (result.code == 201) {
                        Toast.makeText(this, "Shift posted successfully!", Toast.LENGTH_LONG).show();
                        
                        // Add activity to feed
                        addActivityToFeed(shiftTitle, "posted");
                        
                        // Close activity
                        finish();
                    } else if (result.code == 401) {
                        Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Failed to post shift: " + result.code, Toast.LENGTH_LONG).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error posting shift", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Error posting shift: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private boolean validateInputs() {
        if (etShiftTitle.getText().toString().trim().isEmpty()) {
            etShiftTitle.setError("Shift title is required");
            return false;
        }
        
        if (etDate.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (etStartTime.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please select start time", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (etEndTime.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please select end time", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (etLocation.getText().toString().trim().isEmpty()) {
            etLocation.setError("Location is required");
            return false;
        }
        
        if (etWage.getText().toString().trim().isEmpty()) {
            etWage.setError("Wage is required");
            return false;
        }
        
        try {
            double wage = Double.parseDouble(etWage.getText().toString().trim());
            if (wage <= 0) {
                etWage.setError("Wage must be greater than 0");
                return false;
            }
        } catch (NumberFormatException e) {
            etWage.setError("Invalid wage amount");
            return false;
        }
        
        return true;
    }
    
    private String getSelectedShiftType() {
        int selectedId = rgShiftType.getCheckedRadioButtonId();
        if (selectedId == R.id.rb_morning) return "morning";
        if (selectedId == R.id.rb_afternoon) return "afternoon";
        if (selectedId == R.id.rb_night) return "night";
        return "morning"; // default
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnPostShift.setEnabled(!show);
        btnPostShift.setText(show ? "Posting..." : "Post Shift");
    }
    
    private void addActivityToFeed(String shiftTitle, String action) {
        // This would normally be handled by real-time updates
        // For now, we'll just show a success message
        Log.d(TAG, "Activity added to feed: " + action + " shift " + shiftTitle);
    }
}
