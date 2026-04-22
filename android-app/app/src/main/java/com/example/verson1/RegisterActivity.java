package com.example.verson1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RegisterActivity extends AppCompatActivity {

    private EditText nameET, emailET, phoneET, passwordET, confirmPasswordET, companyET;
    private Button registerButton;
    private RadioGroup userRoleGroup;
    private com.google.android.material.checkbox.MaterialCheckBox termsCheckbox;
    private View companyContainer;

    private static final String TAG = "RegisterActivity";
    private static final String PREFS_NAME = "ShiftSyncPrefs";
    private static final String TOKEN_KEY = "auth_token";
    private static final String ROLE_KEY = "user_role";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account_premium);

        nameET = findViewById(R.id.et_full_name);
        emailET = findViewById(R.id.et_email);
        phoneET = findViewById(R.id.et_phone);
        passwordET = findViewById(R.id.et_password);
        confirmPasswordET = findViewById(R.id.et_confirm_password);
        companyET = findViewById(R.id.et_company);
        companyContainer = findViewById(R.id.company_container);

        registerButton = findViewById(R.id.btn_create_account);
        userRoleGroup = findViewById(R.id.rg_user_type);
        termsCheckbox = findViewById(R.id.cb_terms);
        
        TextView signInText = findViewById(R.id.tv_sign_in);

        signInText.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        // Toggle company field based on role selection
        userRoleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_employer) {
                companyContainer.setVisibility(View.VISIBLE);
            } else {
                companyContainer.setVisibility(View.GONE);
            }
        });

        registerButton.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        if (nameET.getText() == null || emailET.getText() == null || phoneET.getText() == null || 
            passwordET.getText() == null || confirmPasswordET.getText() == null) return;

        String name = nameET.getText().toString().trim();
        String email = emailET.getText().toString().trim();
        String phone = phoneET.getText().toString().trim();
        String password = passwordET.getText().toString().trim();
        String confirmPassword = confirmPasswordET.getText().toString().trim();
        String company = companyET != null ? companyET.getText().toString().trim() : "";
        
        // Get selected user type
        boolean isWorker = userRoleGroup.getCheckedRadioButtonId() == R.id.rb_worker;
        String role = isWorker ? "worker" : "employer";

        boolean isValid = true;
        
        // Basic validation
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
            isValid = false;
        }
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show();
            isValid = false;
        }
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "Phone is required", Toast.LENGTH_SHORT).show();
            isValid = false;
        }
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show();
            isValid = false;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            isValid = false;
        }
        if (!isWorker && TextUtils.isEmpty(company)) {
            Toast.makeText(this, "Company name is required for employers", Toast.LENGTH_SHORT).show();
            isValid = false;
        }
        if (!termsCheckbox.isChecked()) {
            Toast.makeText(this, "Please accept the terms and conditions", Toast.LENGTH_SHORT).show();
            isValid = false;
        }

        if (isValid) {
            performSignup(name, email, phone, password, role, company);
        }
    }

    private void performSignup(String name, String email, String phone, String password, String role, String company) {
        registerButton.setEnabled(false);
        registerButton.setText("Creating Account...");
        
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                URL url = new URL(ApiClient.BASE_URL + "/api/users/signup");
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);

                JSONObject payload = new JSONObject();
                payload.put("fullName", name);
                payload.put("email", email);
                payload.put("phoneNumber", phone);
                payload.put("password", password);
                payload.put("role", role);
                payload.put("companyName", company);
                payload.put("hasProfile", false);

                OutputStream os = con.getOutputStream();
                os.write(payload.toString().getBytes());
                os.flush();
                os.close();

                int code = con.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                final String result = sb.toString();
                final int finalCode = code;
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    registerButton.setEnabled(true);
                    registerButton.setText("Create Account");
                    
                    try {
                        JSONObject resp = new JSONObject(result);
                        if (finalCode == 201) {
                            String token = resp.getString("token");
                            saveSession(token, role);
                            
                            Toast.makeText(RegisterActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                            
                            if ("worker".equals(role)) {
                                showProfileCompletionDialog();
                            } else {
                                navigateByRole(role);
                            }
                        } else if (finalCode == 409) {
                            Toast.makeText(RegisterActivity.this, "Email already registered", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(RegisterActivity.this, resp.optString("message", "Signup failed"), Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(RegisterActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    registerButton.setEnabled(true);
                    registerButton.setText("Create Account");
                    Toast.makeText(RegisterActivity.this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private void showProfileCompletionDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Welcome to ShiftSync! 🎉")
               .setMessage("Your account has been created successfully!\n\nComplete your profile to get the best job opportunities:\n\n• Add your skills and experience\n• Set your work preferences\n• Upload your profile photo\n• Add previous work history\n\nYou can complete your profile now or skip and do it later.")
               .setPositiveButton("Complete Profile", (dialog, which) -> {
                   startActivity(new Intent(this, EditProfileActivity.class));
                   finish();
               })
               .setNegativeButton("Skip for Now", (dialog, which) -> {
                   navigateByRole("worker");
               })
               .setCancelable(false)
               .show();
    }

    private void saveSession(String token, String role) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(TOKEN_KEY, token).putString(ROLE_KEY, role).apply();
    }

    private void navigateByRole(String role) {
        Intent intent = "worker".equals(role) ? 
            new Intent(this, WorkerDashboardNewActivity.class) :
            new Intent(this, EmployerDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
