package com.example.verson1;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CrashScreenActivity extends AppCompatActivity {

    public static final String EXTRA_STACKTRACE = "extra_stacktrace";
    public static final String EXTRA_THREAD = "extra_thread";
    public static final String EXTRA_DEVICE = "extra_device";
    public static final String EXTRA_SDK = "extra_sdk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_screen);

        String trace = getIntent().getStringExtra(EXTRA_STACKTRACE);
        String device = getIntent().getStringExtra(EXTRA_DEVICE);
        String sdk = getIntent().getStringExtra(EXTRA_SDK);
        String thread = getIntent().getStringExtra(EXTRA_THREAD);

        TextView text = findViewById(R.id.crash_text);
        if (text != null) {
            String header = "Device: " + (device == null ? "" : device) +
                    "\nSDK: " + (sdk == null ? "" : sdk) +
                    "\nThread: " + (thread == null ? "" : thread) +
                    "\n\n";
            text.setText(header + (trace == null ? "" : trace));
        }

        Button copy = findViewById(R.id.btn_copy);
        if (copy != null) {
            copy.setOnClickListener(v -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null && text != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("ShiftSync crash", String.valueOf(text.getText())));
                        Toast.makeText(this, "Copied crash details", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Copy failed", Toast.LENGTH_SHORT).show();
                }
            });
        }

        Button close = findViewById(R.id.btn_close);
        if (close != null) {
            close.setOnClickListener(v -> finishAffinity());
        }
    }
}

