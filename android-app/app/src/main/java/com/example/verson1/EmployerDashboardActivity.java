package com.example.verson1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class EmployerDashboardActivity extends AppCompatActivity {

    private TextView welcomeText;
    private static final String TAG = "EmployerDashboard";
    private static final String PREFS_NAME = "ShiftSyncPrefs";
    private static final String TOKEN_KEY = "auth_token";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employer_dashboard);

        welcomeText = findViewById(R.id.welcome_text);

        fetchUserProfile();
    }

    private void fetchUserProfile() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(TOKEN_KEY, null);

        if (token == null) {
            redirectToLogin();
            return;
        }

        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                URL url = new URL("http://10.0.2.2:3000/api/users/me");
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("Authorization", "Bearer " + token);
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                int responseCode = con.getResponseCode();
                Log.d(TAG, "Profile Fetch - Response Code: " + responseCode);

                InputStream inputStream = (responseCode == 200) ? con.getInputStream() : con.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) res.append(line);
                br.close();

                final String result = res.toString();

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (responseCode == 200) {
                        try {
                            JSONObject jsonResponse = new JSONObject(result);
                            JSONObject user = jsonResponse.getJSONObject("user");
                            String fullName = user.getString("fullName");
                            welcomeText.setText("Welcome, " + fullName + "!");
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing profile", e);
                        }
                    } else if (responseCode == 401) {
                        Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_SHORT).show();
                        redirectToLogin();
                    } else {
                        Toast.makeText(this, "Error fetching profile", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private void redirectToLogin() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(TOKEN_KEY).apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
