package com.example.verson1;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class EmployerPostShiftActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private String token;

    private TextInputEditText titleInput;
    private TextInputEditText descInput;
    private TextInputEditText skillInput;
    private TextInputEditText locationInput;
    private TextInputEditText dateInput;
    private TextInputEditText startInput;
    private TextInputEditText endInput;
    private TextInputEditText wageInput;
    private TextInputEditText workersNeededInput;
    private TextInputEditText durationDaysInput;

    private ActivityResultLauncher<Intent> locationPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employer_post_shift);

        if (!SessionManager.ensureEmployer(this)) {
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
            LogoutUiHelper.attachSignOutMenu(this, toolbar);
        }
        progressBar = findViewById(R.id.progress);

        titleInput = findViewById(R.id.title_input);
        descInput = findViewById(R.id.desc_input);
        skillInput = findViewById(R.id.skill_input);
        locationInput = findViewById(R.id.location_input);
        dateInput = findViewById(R.id.date_input);
        startInput = findViewById(R.id.start_input);
        endInput = findViewById(R.id.end_input);
        wageInput = findViewById(R.id.wage_input);
        workersNeededInput = findViewById(R.id.workers_needed_input);
        durationDaysInput = findViewById(R.id.duration_days_input);

        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String addr = result.getData().getStringExtra(LocationPickerActivity.EXTRA_PICKED_ADDRESS);
                        if (addr != null && !addr.trim().isEmpty()) {
                            locationInput.setText(addr);
                        }
                    }
                });

        locationInput.setOnClickListener(v -> openLocationPicker());

        dateInput.setOnClickListener(v -> showDatePicker());
        startInput.setOnClickListener(v -> showTimePicker(true));
        endInput.setOnClickListener(v -> showTimePicker(false));

        findViewById(R.id.btn_publish).setOnClickListener(v -> {
            try {
                publishShift();
            } catch (Exception ex) {
                Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openLocationPicker() {
        Intent intent = new Intent(this, LocationPickerActivity.class);
        if (locationInput.getText() != null) {
            intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_QUERY, locationInput.getText().toString());
        }
        locationPickerLauncher.launch(intent);
    }

    private void showDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select shift date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(selection);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            dateInput.setText(sdf.format(cal.getTime()));
        });
        FragmentManager fm = getSupportFragmentManager();
        picker.show(fm, "SHIFT_DATE_PICKER");
    }

    private void showTimePicker(boolean isStart) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);

        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(hour)
                .setMinute(minute)
                .setTitleText(isStart ? "Pick start time" : "Pick end time")
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            int h = picker.getHour();
            int m = picker.getMinute();
            String formatted = String.format(Locale.US, "%02d:%02d", h, m);
            if (isStart) {
                startInput.setText(formatted);
            } else {
                endInput.setText(formatted);
            }
        });
        picker.show(getSupportFragmentManager(), isStart ? "START_TIME" : "END_TIME");
    }

    private void publishShift() throws Exception {
        String title = textOf(titleInput);
        String description = textOf(descInput);
        String skillRequired = textOf(skillInput);
        String location = textOf(locationInput);
        String shiftDate = textOf(dateInput);
        String startTime = textOf(startInput);
        String endTime = textOf(endInput);
        String wageStr = textOf(wageInput);

        if (title.isEmpty() || skillRequired.isEmpty() || location.isEmpty()
                || shiftDate.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
            Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double wageVal;
        try {
            wageVal = Double.parseDouble(wageStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid wage amount", Toast.LENGTH_SHORT).show();
            return;
        }

        int workersNeededVal = parsePositiveInt(workersNeededInput, 1);
        int durationDaysVal = parsePositiveInt(durationDaysInput, 1);

        JSONObject body = new JSONObject();
        body.put("title", title);
        body.put("description", description);
        body.put("skillRequired", skillRequired);
        body.put("location", location);
        body.put("shiftDate", shiftDate);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        body.put("wage", wageVal);
        body.put("workersNeeded", workersNeededVal);
        body.put("durationDays", durationDaysVal);

        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.post("/api/shifts", token, body.toString());
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (res.code == 201) {
                        Toast.makeText(this, "Shift posted", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Could not post shift", Toast.LENGTH_SHORT).show();
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

    private static String textOf(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private static int parsePositiveInt(TextInputEditText et, int fallback) {
        String raw = textOf(et);
        if (raw.isEmpty()) return fallback;
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
