package com.example.verson1;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class EmployerDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textView = new TextView(this);
        textView.setText("Employer Dashboard - Welcome!");
        textView.setTextSize(24);
        textView.setPadding(50, 50, 50, 50);
        setContentView(textView);
    }
}
