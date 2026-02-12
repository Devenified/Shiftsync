package com.example.verson1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText nameET, emailET, phoneET, companyET, passwordET, confirmPasswordET;
    private TextInputLayout nameLayout, emailLayout, phoneLayout, companyLayout, passwordLayout, confirmPasswordLayout;
    private Button registerButton;
    private TextView backToLoginText;
    private ProgressBar loadingIndicator;

    private static final String TAG = "RegisterActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        nameET = findViewById(R.id.name_edit_text);
        emailET = findViewById(R.id.reg_email_edit_text);
        phoneET = findViewById(R.id.phone_edit_text);
        companyET = findViewById(R.id.company_edit_text);
        passwordET = findViewById(R.id.reg_password_edit_text);
        confirmPasswordET = findViewById(R.id.confirm_password_edit_text);

        nameLayout = findViewById(R.id.name_layout);
        emailLayout = findViewById(R.id.reg_email_layout);
        phoneLayout = findViewById(R.id.phone_layout);
        companyLayout = findViewById(R.id.company_layout);
        passwordLayout = findViewById(R.id.reg_password_layout);
        confirmPasswordLayout = findViewById(R.id.confirm_password_layout);

        registerButton = findViewById(R.id.register_button);
        backToLoginText = findViewById(R.id.back_to_login_text);
        
        // Add a progress bar to the layout if it doesn't exist, or use a generic one
        // For now, I'll assume you might want to add one to the XML later, 
        // but I will just use the button state for feedback.

        // Fade-in animation
        View rootView = findViewById(R.id.register_root);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        rootView.startAnimation(fadeIn);

        registerButton.setOnClickListener(v -> attemptRegister());

        backToLoginText.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void attemptRegister() {
        String name = nameET.getText().toString().trim();
        String email = emailET.getText().toString().trim();
        String phone = phoneET.getText().toString().trim();
        String company = companyET.getText().toString().trim();
        String password = passwordET.getText().toString().trim();
        String confirmPassword = confirmPasswordET.getText().toString().trim();

        boolean isValid = true;

        if (TextUtils.isEmpty(name)) {
            nameLayout.setError("Name is required");
            isValid = false;
        } else {
            nameLayout.setError(null);
        }

        if (TextUtils.isEmpty(email)) {
            emailLayout.setError("Email is required");
            isValid = false;
        } else {
            emailLayout.setError(null);
        }

        if (TextUtils.isEmpty(phone)) {
            phoneLayout.setError("Phone number is required");
            isValid = false;
        } else {
            phoneLayout.setError(null);
        }

        if (TextUtils.isEmpty(company)) {
            companyLayout.setError("Company name is required");
            isValid = false;
        } else {
            companyLayout.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            passwordLayout.setError("Password is required");
            isValid = false;
        } else {
            passwordLayout.setError(null);
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Passwords do not match");
            isValid = false;
        } else {
            confirmPasswordLayout.setError(null);
        }

        if (isValid) {
            performSignup(name, email, phone, company, password);
        }
    }

    private void performSignup(String name, String email, String phone, String company, String password) {
        registerButton.setEnabled(false);

        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                URL url = new URL("http://10.0.2.2:3000/api/users/signup");
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                JSONObject jsonPayload = new JSONObject();
                jsonPayload.put("fullName", name);
                jsonPayload.put("email", email);
                jsonPayload.put("phoneNumber", phone);
                jsonPayload.put("companyName", company);
                jsonPayload.put("password", password);

                OutputStream os = con.getOutputStream();
                os.write(jsonPayload.toString().getBytes());
                os.flush();
                os.close();

                int responseCode = con.getResponseCode();
                InputStream inputStream = (responseCode >= 200 && responseCode < 300) 
                        ? con.getInputStream() : con.getErrorStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) res.append(line);
                br.close();

                final String result = res.toString();
                Log.d(TAG, "Signup Response: " + result);

                new Handler(Looper.getMainLooper()).post(() -> {
                    registerButton.setEnabled(true);
                    try {
                        JSONObject responseJson = new JSONObject(result);
                        String message = responseJson.optString("message", "Response received");
                        
                        if (responseCode == 201) {
                            Toast.makeText(RegisterActivity.this, "Success: " + message, Toast.LENGTH_LONG).show();
                            // Redirect to Login
                            finish();
                        } else {
                            Toast.makeText(RegisterActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(RegisterActivity.this, "Error parsing signup response", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Signup Error: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> {
                    registerButton.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, "Signup Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }
}
