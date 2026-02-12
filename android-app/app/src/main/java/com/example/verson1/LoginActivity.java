package com.example.verson1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText;
    private Button loginButton;
    private ProgressBar loadingIndicator;

    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "ShiftSyncPrefs";
    private static final String TOKEN_KEY = "auth_token";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        loginButton = findViewById(R.id.login_button);
        loadingIndicator = findViewById(R.id.loading_indicator);
        TextView createProfileText = findViewById(R.id.create_profile_text);

        // Fade-in animation
        View rootView = findViewById(R.id.login_root);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        rootView.startAnimation(fadeIn);

        loginButton.setOnClickListener(v -> performLogin());

        createProfileText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // Optional: Check if token already exists to auto-login
        checkExistingToken();
    }

    private void checkExistingToken() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(TOKEN_KEY, null);
        if (token != null) {
            // Token exists, could navigate to dashboard directly or verify it
            Log.d(TAG, "Existing token found: " + token);
        }
    }

    private void performLogin() {
        if (emailEditText.getText() == null || passwordEditText.getText() == null) return;
        
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        loginButton.setEnabled(false);
        loadingIndicator.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                URL url = new URL("http://10.0.2.2:3000/api/users/login");
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                // Create JSON payload
                JSONObject jsonPayload = new JSONObject();
                jsonPayload.put("email", email);
                jsonPayload.put("password", password);

                OutputStream os = con.getOutputStream();
                os.write(jsonPayload.toString().getBytes());
                os.flush();
                os.close();

                int responseCode = con.getResponseCode();
                Log.d(TAG, "Login - Response Code: " + responseCode);

                InputStream inputStream = (responseCode >= 200 && responseCode < 300) 
                        ? con.getInputStream() : con.getErrorStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) res.append(line);
                br.close();

                final String result = res.toString();
                Log.d(TAG, "Login - Server Response: " + result);

                new Handler(Looper.getMainLooper()).post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    loginButton.setEnabled(true);
                    
                    try {
                        JSONObject responseJson = new JSONObject(result);
                        String message = responseJson.optString("message", "Unknown response");
                        
                        if (responseCode == 200) {
                            String token = responseJson.optString("token");
                            saveToken(token);
                            
                            Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, EmployerDashboardActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, "Login Failed: " + message, Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(LoginActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Login - Error: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    loginButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Connection Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private void saveToken(String token) {
        if (token == null || token.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(TOKEN_KEY, token).apply();
        Log.d(TAG, "Token saved successfully");
    }
}
