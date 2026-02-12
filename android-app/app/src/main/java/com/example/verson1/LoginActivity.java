package com.example.verson1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText;
    private TextInputLayout emailLayout, passwordLayout;
    private Button loginButton;
    private ProgressBar loadingIndicator;
    private TextView createProfileText;

    private static final String DUMMY_EMAIL = "employer@shiftsync.com";
    private static final String DUMMY_PASSWORD = "Employer@123";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        emailLayout = findViewById(R.id.email_layout);
        passwordLayout = findViewById(R.id.password_layout);
        loginButton = findViewById(R.id.login_button);
        loadingIndicator = findViewById(R.id.loading_indicator);
        createProfileText = findViewById(R.id.create_profile_text);

        // Fade-in animation
        View rootView = findViewById(R.id.login_root);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        rootView.startAnimation(fadeIn);

        loginButton.setOnClickListener(v -> attemptLogin());

        createProfileText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void attemptLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        boolean isValid = true;

        if (TextUtils.isEmpty(email)) {
            emailLayout.setError("Email is required");
            isValid = false;
        } else if (!validateEmail(email)) {
            emailLayout.setError("Invalid email format");
            isValid = false;
        } else {
            emailLayout.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            passwordLayout.setError("Password is required");
            isValid = false;
        } else if (!validatePassword(password)) {
            passwordLayout.setError("Password does not meet requirements");
            isValid = false;
        } else {
            passwordLayout.setError(null);
        }

        if (isValid) {
            performLogin(email, password);
        }
    }

    private boolean validateEmail(String email) {
        // - Contains “@”
        // - Does not start with a number
        // - Has characters before “@”
        // - Ends with “.com”
        String emailPattern = "^[^0-9][a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.com$";
        return Pattern.compile(emailPattern).matcher(email).matches();
    }

    private boolean validatePassword(String password) {
        // - Minimum 8 characters
        // - At least one uppercase letter
        // - At least one lowercase letter
        // - At least one digit
        // - At least one special character
        String passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";
        return Pattern.compile(passwordPattern).matcher(password).matches();
    }

    private void performLogin(String email, String password) {
        loginButton.setEnabled(false);
        loadingIndicator.setVisibility(View.VISIBLE);

        new Handler().postDelayed(() -> {
            loadingIndicator.setVisibility(View.GONE);
            loginButton.setEnabled(true);

            if (email.equals(DUMMY_EMAIL) && password.equals(DUMMY_PASSWORD)) {
                Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                // Navigate to EmployerDashboardActivity (assuming it exists or will be created)
                Intent intent = new Intent(LoginActivity.this, EmployerDashboardActivity.class);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(LoginActivity.this, "Invalid Credentials", Toast.LENGTH_SHORT).show();
            }
        }, 1000);
    }
}
