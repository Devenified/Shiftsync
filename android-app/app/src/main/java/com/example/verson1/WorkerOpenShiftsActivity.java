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

public class WorkerOpenShiftsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private OpenShiftAdapter adapter;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_open_shifts);

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
        recyclerView = findViewById(R.id.list);
        progressBar = findViewById(R.id.progress);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OpenShiftAdapter();
        recyclerView.setAdapter(adapter);

        loadOpenShifts();
    }

    private void loadOpenShifts() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/shifts/open", token);
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
                        Toast.makeText(this, "Failed to load shifts", Toast.LENGTH_SHORT).show();
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

    private void applyToShift(String shiftId, Button applyButton) {
        applyButton.setEnabled(false);
        new Thread(() -> {
            try {
                String path = "/api/shifts/" + shiftId + "/apply";
                ApiClient.HttpResult res = ApiClient.post(path, token, "{}");
                new Handler(Looper.getMainLooper()).post(() -> {
                    applyButton.setEnabled(true);
                    if (res.code == 200) {
                        Toast.makeText(this, "Applied successfully", Toast.LENGTH_SHORT).show();
                        applyButton.setText("Applied");
                        applyButton.setEnabled(false);
                    } else {
                        try {
                            String msg = new JSONObject(res.body).optString("message", "Apply failed");
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Apply failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    applyButton.setEnabled(true);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String formatTime(String time24) {
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

    private class OpenShiftAdapter extends RecyclerView.Adapter<OpenShiftAdapter.VH> {

        private final List<JSONObject> items = new ArrayList<>();

        void setItems(List<JSONObject> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_open_shift_enhanced, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject shift = items.get(position);
            String id = JsonHelper.idString(shift, "_id");
            
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
            
            // Set status
            holder.status.setText("OPEN");
            holder.status.setBackgroundResource(R.drawable.status_background_open);

            boolean hasApplied = shift.optBoolean("hasApplied", false);
            if (hasApplied) {
                holder.apply.setText("Applied");
                holder.apply.setEnabled(false);
                holder.apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_surface)));
                holder.apply.setTextColor(
                        androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.text_medium));
                holder.apply.setOnClickListener(null);
            } else {
                holder.apply.setText("Apply");
                holder.apply.setEnabled(true);
                holder.apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_orange)));
                holder.apply.setTextColor(
                        androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_white));
                holder.apply.setOnClickListener(v -> applyToShift(id, holder.apply));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView location;
            final TextView date;
            final TextView time;
            final TextView skills;
            final TextView wage;
            final TextView status;
            final Button apply;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.shift_title);
                location = itemView.findViewById(R.id.shift_location);
                date = itemView.findViewById(R.id.shift_date);
                time = itemView.findViewById(R.id.shift_time);
                skills = itemView.findViewById(R.id.shift_skills);
                wage = itemView.findViewById(R.id.shift_wage);
                status = itemView.findViewById(R.id.shift_status);
                apply = itemView.findViewById(R.id.btn_apply);
            }
        }
    }
}
