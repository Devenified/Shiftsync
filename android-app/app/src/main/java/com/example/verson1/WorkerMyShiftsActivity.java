package com.example.verson1;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WorkerMyShiftsActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private MyShiftAdapter adapter;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_my_shifts);

        if (!SessionManager.ensureWorker(this)) {
            return;
        }

        token = SessionManager.getToken(this);
        if (token == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
            LogoutUiHelper.attachSignOutMenu(this, toolbar);
        }
        RecyclerView recyclerView = findViewById(R.id.list);
        progressBar = findViewById(R.id.progress);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyShiftAdapter();
        recyclerView.setAdapter(adapter);

        loadMyShifts();
    }

    private void loadMyShifts() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/shifts/worker/my", token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (res.code == 200) {
                        try {
                            JSONArray arr = new JSONObject(res.body).getJSONArray("shifts");
                            List<JSONObject> list = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                list.add(arr.getJSONObject(i));
                            }
                            adapter.setItems(list);
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not read shifts", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Failed to load your shifts", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private static String formatTime(String time24) {
        try {
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            
            String period = hour >= 12 ? "PM" : "AM";
            hour = hour % 12;
            if (hour == 0) hour = 12;
            
            return String.format("%d:%02d %s", hour, minute, period);
        } catch (Exception e) {
            return time24;
        }
    }

    private class MyShiftAdapter extends RecyclerView.Adapter<MyShiftAdapter.VH> {

        private final List<JSONObject> items = new ArrayList<>();

        void setItems(List<JSONObject> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_worker_shift_enhanced, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject shift = items.get(position);
            
            // Update with enhanced layout fields
            holder.title.setText(shift.optString("title", "Shift"));
            
            // Set location, date, and time
            holder.location.setText(shift.optString("location", ""));
            holder.date.setText(shift.optString("shiftDate", ""));
            
            // Format time from startTime and endTime
            String startTime = shift.optString("startTime", "");
            String endTime = shift.optString("endTime", "");
            if (!startTime.isEmpty() && !endTime.isEmpty()) {
                // Convert 24-hour format to 12-hour format
                String formattedStart = formatTime(startTime);
                String formattedEnd = formatTime(endTime);
                holder.time.setText(formattedStart + "-" + formattedEnd);
            } else {
                holder.time.setText("Full Day");
            }
            
            // Set skills
            holder.skills.setText(shift.optString("skillRequired", ""));
            
            // Set wage
            double wage = shift.optDouble("wage", 0);
            holder.wage.setText("₹" + (int) wage);
            
            // Set status and application status
            String status = shift.optString("status", "");
            String appStatus = shift.optString("applicationStatus", "null");
            if ("null".equals(appStatus)) {
                appStatus = "—";
            }
            
            // Update status badge
            holder.statusBadge.setText(status.toUpperCase());
            holder.statusBadge.setBackgroundResource(R.drawable.status_background_assigned);
            
            // Update application status
            holder.applicationStatus.setText("Application: " + appStatus);
            
            // Set action button text
            holder.actionButton.setText("View Details");
            holder.actionButton.setOnClickListener(v -> showShiftDetailsDialog(shift));
        }

        private void showShiftDetailsDialog(JSONObject shift) {
            String title = shift.optString("title", "Shift Details");
            String desc = shift.optString("description", "No description provided.");
            String location = shift.optString("location", "N/A");
            String date = shift.optString("shiftDate", "N/A");
            String startTime = shift.optString("startTime", "");
            String endTime = shift.optString("endTime", "");
            String rawTime = (startTime.isEmpty() || endTime.isEmpty()) ? "Full Day" : formatTime(startTime) + " - " + formatTime(endTime);
            String skills = shift.optString("skillRequired", "None");
            double wage = shift.optDouble("wage", 0);
            String status = shift.optString("status", "").toUpperCase();
            String appStatus = shift.optString("applicationStatus", "Unknown");

            String displayStatus = "null".equalsIgnoreCase(appStatus) ? "None" : appStatus;

            String message = "Date: " + date + "\n" +
                             "Time: " + rawTime + "\n" +
                             "Location: " + location + "\n" +
                             "Skills: " + skills + "\n" +
                             "Wage: ₹" + (int)wage + "\n\n" +
                             "Status: " + status + "\n" +
                             "Your Application: " + displayStatus + "\n\n" +
                             "Description:\n" + desc;

            new androidx.appcompat.app.AlertDialog.Builder(WorkerMyShiftsActivity.this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Close", null)
                    .show();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView location;
            final TextView date;
            final TextView time;
            final TextView skills;
            final TextView wage;
            final TextView statusBadge;
            final TextView applicationStatus;
            final Button actionButton;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.shift_title);
                location = itemView.findViewById(R.id.shift_location);
                date = itemView.findViewById(R.id.shift_date);
                time = itemView.findViewById(R.id.shift_time);
                skills = itemView.findViewById(R.id.shift_skills);
                wage = itemView.findViewById(R.id.shift_wage);
                statusBadge = itemView.findViewById(R.id.shift_status_badge);
                applicationStatus = itemView.findViewById(R.id.application_status);
                actionButton = itemView.findViewById(R.id.btn_action);
            }
        }
    }
}
