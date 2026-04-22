package com.example.verson1;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import java.util.Calendar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

public class EmployerPostShiftActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ShiftSyncPrefs";
    private static final String TOKEN_KEY = "auth_token";

    private ProgressBar progressBar;
    private String token;

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

        TextInputEditText title = findViewById(R.id.title_input);
        TextInputEditText desc = findViewById(R.id.desc_input);
        TextInputEditText skill = findViewById(R.id.skill_input);
        TextInputEditText location = findViewById(R.id.location_input);
        TextInputEditText date = findViewById(R.id.date_input);
        TextInputEditText start = findViewById(R.id.start_input);
        TextInputEditText end = findViewById(R.id.end_input);
        TextInputEditText wage = findViewById(R.id.wage_input);

        date.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String d = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
                date.setText(d);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        start.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                String t = String.format("%02d:%02d", hourOfDay, minute);
                start.setText(t);
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });

        end.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                String t = String.format("%02d:%02d", hourOfDay, minute);
                end.setText(t);
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });

        findViewById(R.id.btn_publish).setOnClickListener(v -> {
            try {
                publishShift(title, desc, skill, location, date, start, end, wage);
            } catch (Exception ex) {
                Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void publishShift(
            TextInputEditText titleEt,
            TextInputEditText descEt,
            TextInputEditText skillEt,
            TextInputEditText locationEt,
            TextInputEditText dateEt,
            TextInputEditText startEt,
            TextInputEditText endEt,
            TextInputEditText wageEt
    ) throws Exception {
        if (titleEt.getText() == null
                || skillEt.getText() == null
                || locationEt.getText() == null
                || dateEt.getText() == null
                || startEt.getText() == null
                || endEt.getText() == null
                || wageEt.getText() == null) {
            return;
        }

        String title = titleEt.getText().toString().trim();
        String description = descEt.getText() != null ? descEt.getText().toString().trim() : "";
        String skillRequired = skillEt.getText().toString().trim();
        String location = locationEt.getText().toString().trim();
        String shiftDate = dateEt.getText().toString().trim();
        String startTime = startEt.getText().toString().trim();
        String endTime = endEt.getText().toString().trim();
        
        double wageVal = 0;
        try {
            wageVal = Double.parseDouble(wageEt.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid wage amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (title.isEmpty()
                || skillRequired.isEmpty()
                || location.isEmpty()
                || shiftDate.isEmpty()
                || startTime.isEmpty()
                || endTime.isEmpty()) {
            Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject body = new JSONObject();
        body.put("title", title);
        body.put("description", description);
        body.put("skillRequired", skillRequired);
        body.put("location", location);
        body.put("shiftDate", shiftDate);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        body.put("wage", wageVal);

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
}
