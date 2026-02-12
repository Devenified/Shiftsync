package com.example.verson1;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText nameET, emailET, phoneET, companyET, passwordET, confirmPasswordET;
    private TextInputLayout nameLayout, emailLayout, phoneLayout, companyLayout, passwordLayout, confirmPasswordLayout;
    private Button registerButton;
    private TextView backToLoginText;

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
        } else if (!validateEmail(email)) {
            emailLayout.setError("Invalid email format");
            isValid = false;
        } else {
            emailLayout.setError(null);
        }

        if (TextUtils.isEmpty(phone)) {
            phoneLayout.setError("Phone number is required");
            isValid = false;
        } else if (phone.length() != 10) {
            phoneLayout.setError("Phone number must be 10 digits");
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
        } else if (!validatePassword(password)) {
            passwordLayout.setError("Password does not meet requirements");
            isValid = false;
        } else {
            passwordLayout.setError(null);
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordLayout.setError("Please confirm your password");
            isValid = false;
        } else if (!confirmPassword.equals(password)) {
            confirmPasswordLayout.setError("Passwords do not match");
            isValid = false;
        } else {
            confirmPasswordLayout.setError(null);
        }

        if (isValid) {
            Toast.makeText(this, "Profile Created Successfully", Toast.LENGTH_LONG).show();
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    private boolean validateEmail(String email) {
        String emailPattern = "^[^0-9][a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.com$";
        return Pattern.compile(emailPattern).matcher(email).matches();
    }

    private boolean validatePassword(String password) {
        String passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$";
        return Pattern.compile(passwordPattern).matcher(password).matches();
    }
}
