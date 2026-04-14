package com.example.verson1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
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
    private MaterialButton loginButton;
    private ProgressBar loadingIndicator;
    private TabLayout loginTabs;

    private static final String TAG = "LoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkExistingSession()) return;

        setContentView(R.layout.activity_login_premium);

        emailEditText = findViewById(R.id.et_email);
        passwordEditText = findViewById(R.id.et_password);
        loginButton = findViewById(R.id.btn_login);
        loadingIndicator = findViewById(R.id.progress_bar);
        loginTabs = findViewById(R.id.login_tabs);

        if (loginTabs != null) {
            loginTabs.addTab(loginTabs.newTab().setText("Worker"));
            loginTabs.addTab(loginTabs.newTab().setText("Employer"));
        }

        loginButton.setOnClickListener(v -> performLogin());

        findViewById(R.id.tv_sign_up).setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private boolean checkExistingSession() {
        if (SessionManager.isLoggedIn(this)) {
            navigateByRole(SessionManager.getRole(this));
            return true;
        }
        return false;
    }

    private void performLogin() {
        if (emailEditText.getText() == null || passwordEditText.getText() == null) return;

        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        boolean isWorker = loginTabs == null || loginTabs.getSelectedTabPosition() == 0;

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // For emulator testing - skip connection testing to isolate crash
        // Uncomment the connection testing below once basic login works
        /*
        // Test connection first (run in background to prevent UI blocking)
        new Thread(() -> {
            String workingIP = ApiClient.findWorkingIP();
            runOnUiThread(() -> {
                if (workingIP == null) {
                    Toast.makeText(this, "Connection error: Cannot reach server\n\nTried all IPs:\n• 10.0.2.2:3000\n• localhost:3000\n• 10.87.0.168:3000\n• 192.168.1.100:3000\n• 192.168.0.100:3000\n\nSolutions:\n• Check backend is running\n• Allow port 3000 in firewall\n• Ensure same Wi-Fi network", Toast.LENGTH_LONG).show();
                    loginButton.setEnabled(true);
                    loginButton.setText("LOGIN");
                    return;
                }
                
                // Show success message with working IP
                Toast.makeText(this, "Connected to: " + workingIP, Toast.LENGTH_SHORT).show();
                
                // Proceed with login
                proceedWithLogin(email, password, isWorker);
            });
        }).start();
        */
        
        // Direct login for emulator testing
        proceedWithLogin(email, password, isWorker);
    }
    
    private void proceedWithLogin(String email, String password, boolean isWorker) {

        // Show loading
        loginButton.setEnabled(false);
        loginButton.setText("Connecting...");
        loadingIndicator.setVisibility(View.VISIBLE);

        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                String endpoint = isWorker ? "/api/users/login-worker" : "/api/users/login-employer";
                URL url = new URL(ApiClient.BASE_URL + endpoint);
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                JSONObject jsonPayload = new JSONObject();
                jsonPayload.put("email", email);
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
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    loginButton.setEnabled(true);
                    
                    try {
                        JSONObject responseJson = new JSONObject(result);
                        String message = responseJson.optString("message", "");

                        if (responseCode == 200) {
                            String token = responseJson.getString("token");
                            String role = responseJson.getJSONObject("user").getString("role");
                            saveSession(token, role);
                            navigateByRole(role);
                        } else if (responseCode == 401) {
                            Toast.makeText(this, "Invalid email/password", Toast.LENGTH_SHORT).show();
                        } else if (responseCode == 403) {
                            Toast.makeText(this, "Please use correct login type (" + (isWorker ? "worker" : "employer") + ")", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, message.isEmpty() ? "Login failed" : message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Server error", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    loginButton.setEnabled(true);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private void saveSession(String token, String role) {
        SessionManager.prefs(this)
                .edit()
                .putString(SessionManager.TOKEN_KEY, token)
                .putString(SessionManager.ROLE_KEY, role)
                .apply();
    }

    private void navigateByRole(String role) {
        Intent intent;
        if ("worker".equalsIgnoreCase(role)) {
            intent = new Intent(this, WorkerDashboardNewActivity.class);
        } else {
            intent = new Intent(this, EmployerDashboardActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
