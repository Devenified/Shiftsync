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
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

public class EmployerProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employer_profile);

        if (!SessionManager.ensureEmployer(this)) {
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        LogoutUiHelper.attachSignOutMenu(this, toolbar);

        TextView nameText = findViewById(R.id.name_text);
        TextView companyText = findViewById(R.id.company_text);
        TextView emailText = findViewById(R.id.email_text);
        TextView phoneText = findViewById(R.id.phone_text);
        ProgressBar progress = findViewById(R.id.progress);
        MaterialButton logoutBtn = findViewById(R.id.btn_logout);

        logoutBtn.setOnClickListener(v -> LogoutUiHelper.showConfirmLogout(this));

        final String token = SessionManager.getToken(this);
        if (token == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/users/me", token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    progress.setVisibility(View.GONE);
                    if (res.code != 200) {
                        Toast.makeText(this, "Could not load account", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        JSONObject user = new JSONObject(res.body).getJSONObject("user");
                        nameText.setText(user.optString("fullName", ""));
                        companyText.setText(user.optString("companyName", ""));
                        emailText.setText(user.optString("email", ""));
                        phoneText.setText(user.optString("phoneNumber", ""));
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid profile data", Toast.LENGTH_SHORT).show();
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
