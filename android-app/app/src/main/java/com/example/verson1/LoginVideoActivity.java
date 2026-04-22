package com.example.verson1;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginVideoActivity extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText;
    private Button loginButton;
    private ProgressBar loadingIndicator;
    private TabLayout loginTabs;
    private VideoView loginVideo;
    private MediaPlayer mediaPlayer;

    private static final String TAG = "LoginVideoActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Auto-login check
        if (checkExistingSession()) return;

        setContentView(R.layout.activity_login_video);

        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        loginButton = findViewById(R.id.login_button);
        loadingIndicator = findViewById(R.id.loading_indicator);
        loginTabs = findViewById(R.id.login_tabs);
        loginVideo = findViewById(R.id.login_video);

        // Setup video
        setupVideoBackground();

        // Add tabs for Worker and Employer
        loginTabs.addTab(loginTabs.newTab().setText("WORKER"));
        loginTabs.addTab(loginTabs.newTab().setText("EMPLOYER"));

        loginButton.setOnClickListener(v -> performLogin());

        findViewById(R.id.create_profile_text).setOnClickListener(v -> {
            startActivity(new Intent(LoginVideoActivity.this, RegisterActivity.class));
        });
    }

    private void setupVideoBackground() {
        try {
            // Set video from raw resources
            String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.login_video;
            Uri videoUri = Uri.parse(videoPath);
            
            loginVideo.setVideoURI(videoUri);

            // Configure video
            loginVideo.setOnPreparedListener(mp -> {
                mediaPlayer = mp;
                mp.setLooping(true);
                mp.setVolume(0, 0); // Mute the video
                mp.start();
                
                // Add rotation animation
                startVideoRotation();
            });

            loginVideo.setOnCompletionListener(mp -> {
                // Restart video when it completes
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                }
            });

            // Start video
            loginVideo.start();

        } catch (Exception e) {
            // If video fails, show fallback background
            Toast.makeText(this, "Video loading failed, using fallback", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void startVideoRotation() {
        // Create continuous rotation animation
        RotateAnimation rotateAnimation = new RotateAnimation(
            0, 360,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        );
        
        rotateAnimation.setDuration(8000); // 8 seconds for full rotation
        rotateAnimation.setRepeatCount(Animation.INFINITE);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        
        // Apply rotation to video
        loginVideo.startAnimation(rotateAnimation);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private boolean checkExistingSession() {
        if (SessionManager.isLoggedIn(this)) {
            navigateByRole(SessionManager.getRole(this));
            return true;
        }
        return false;
    }

    private void performLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isWorker = loginTabs.getSelectedTabPosition() == 0;

        // Show loading state
        loginButton.setEnabled(false);
        loginButton.setText("CONNECTING...");
        loadingIndicator.setVisibility(View.VISIBLE);

        // Direct login for emulator testing
        proceedWithLogin(email, password, isWorker);
    }

    private void proceedWithLogin(String email, String password, boolean isWorker) {
        new Thread(() -> {
            try {
                String endpoint = isWorker ? "/api/users/login-worker" : "/api/users/login-employer";
                String requestBody = new JSONObject()
                        .put("email", email)
                        .put("password", password).toString();
                        
                ApiClient.HttpResult result = ApiClient.post(endpoint, null, requestBody);

                runOnUiThread(() -> {
                    loginButton.setEnabled(true);
                    loginButton.setText("LOG IN");
                    loadingIndicator.setVisibility(View.GONE);

                    if (result.code == 200 && result.body != null) {
                        try {
                            JSONObject response = new JSONObject(result.body);
                            if (response.has("token")) {
                                String token = response.getString("token");
                                String role = response.getString("role");

                                saveSession(token, role);
                                navigateByRole(role);
                            } else {
                                Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(this, "Invalid response format", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Login failed: " + result.code, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loginButton.setEnabled(true);
                    loginButton.setText("LOG IN");
                    loadingIndicator.setVisibility(View.GONE);
                    Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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
            // Use simpler dashboard for testing to isolate crash
            intent = new Intent(this, WorkerDashboardCleanActivity.class);
        } else {
            intent = new Intent(this, EmployerDashboardActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
