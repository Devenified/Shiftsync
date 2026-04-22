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

    private final Calendar startCalendar = Calendar.getInstance();
    private final Calendar endCalendar = Calendar.getInstance();
    private static final String TAG = "RequestLeaveActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_leave);

        if (!SessionManager.isLoggedIn(this)) {
            SessionManager.logoutToLogin(this);
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
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
            datePickerDialog.show();
        });

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
            datePickerDialog.getDatePicker().setMinDate(startCalendar.getTimeInMillis());
            datePickerDialog.show();
        });
    }

    private void updateStartDateEditText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        etStartDate.setText(dateFormat.format(startCalendar.getTime()));
        if (endCalendar.getTimeInMillis() < startCalendar.getTimeInMillis()) {
            endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
            updateEndDateEditText();
        }
    }

    private void updateEndDateEditText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        etEndDate.setText(dateFormat.format(endCalendar.getTime()));
    }

    private void loadLeaveBalance() {
        new Thread(() -> {
            try {
                String token = SessionManager.getToken(this);
                if (token == null) {
                    return;
                }
                ApiClient.HttpResult result = ApiClient.get("/api/leaves/balance", token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (result.code == 200) {
                        try {
                            JSONObject obj = new JSONObject(result.body).getJSONObject("balance");
                            tvSickBalance.setText(obj.optInt("sick", 0) + " days");
                            tvCasualBalance.setText(obj.optInt("casual", 0) + " days");
                            tvPaidBalance.setText(obj.optInt("paid", 0) + " days");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse leave balance", e);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load leave balance", e);
            }
        }).start();
    }

    private void submitLeaveRequest() {
        if (!validateInputs()) {
            return;
        }

        String leaveType = getSelectedLeaveType();
        String startDate = etStartDate.getText() != null ? etStartDate.getText().toString().trim() : "";
        String endDate = etEndDate.getText() != null ? etEndDate.getText().toString().trim() : "";
        String reason = etReason.getText() != null ? etReason.getText().toString().trim() : "";

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

                JSONObject leaveData = new JSONObject();
                leaveData.put("leaveType", leaveType);
                leaveData.put("startDate", startDate);
                leaveData.put("endDate", endDate);
                leaveData.put("reason", reason);

                ApiClient.HttpResult result = ApiClient.post("/api/leaves/request", token, leaveData.toString());
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    if (result.code == 201) {
                        Toast.makeText(this, "Leave request submitted successfully", Toast.LENGTH_LONG).show();
                        finish();
                    } else if (result.code == 401) {
                        SessionManager.logoutToLogin(this);
                    } else {
                        try {
                            String message = new JSONObject(result.body).optString("message", "Failed to submit leave request");
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        } catch (Exception ignored) {
                            Toast.makeText(this, "Failed to submit leave request", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error submitting leave request", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Error submitting leave request", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean validateInputs() {
        if (etStartDate.getText() == null || etStartDate.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please select start date", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (etEndDate.getText() == null || etEndDate.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please select end date", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (etReason.getText() == null || etReason.getText().toString().trim().isEmpty()) {
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
        return "sick";
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSubmitLeave.setEnabled(!show);
        btnSubmitLeave.setText(show ? "Submitting..." : "Submit Leave Request");
    }
}
