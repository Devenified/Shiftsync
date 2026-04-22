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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WorkerMyShiftsActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView emptyText;
    private SwipeRefreshLayout swipeRefresh;
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
        recyclerView = findViewById(R.id.list);
        progressBar = findViewById(R.id.progress);
        emptyText = findViewById(R.id.empty_text);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyShiftAdapter();
        recyclerView.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.brand_primary, R.color.brand_secondary);
        swipeRefresh.setOnRefreshListener(this::loadMyShifts);

        loadMyShifts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMyShifts();
    }

    private void loadMyShifts() {
        if (!swipeRefresh.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/shifts/worker/my", token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    if (res.code == 200) {
                        try {
                            JSONArray arr = new JSONObject(res.body).getJSONArray("shifts");
                            List<JSONObject> list = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                list.add(arr.getJSONObject(i));
                            }
                            // Put assigned (upcoming) shifts first for a "booking-style" feel.
                            list.sort((a, b) -> {
                                String sa = a.optString("status", "");
                                String sb = b.optString("status", "");
                                if ("assigned".equals(sa) && !"assigned".equals(sb)) return -1;
                                if (!"assigned".equals(sa) && "assigned".equals(sb)) return 1;
                                // Secondary sort by date/time strings (best-effort)
                                String da = a.optString("shiftDate", "");
                                String db = b.optString("shiftDate", "");
                                int d = da.compareTo(db);
                                if (d != 0) return d;
                                return a.optString("startTime", "").compareTo(b.optString("startTime", ""));
                            });
                            adapter.setItems(list);
                            updateEmptyState(list.isEmpty());
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
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateEmptyState(boolean isEmpty) {
        if (emptyText != null) emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
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

    private int getStatusColor(String status) {
        switch (status.toLowerCase()) {
            case "open":
                return ContextCompat.getColor(this, R.color.brand_primary);
            case "assigned":
                return ContextCompat.getColor(this, R.color.brand_secondary);
            case "completed":
                return ContextCompat.getColor(this, R.color.status_available);
            case "cancelled":
                return ContextCompat.getColor(this, R.color.status_busy);
            default:
                return ContextCompat.getColor(this, R.color.text_medium);
        }
    }

    private int getStatusBackground(String status) {
        switch (status.toLowerCase()) {
            case "open":
                return R.drawable.status_background_open;
            case "assigned":
                return R.drawable.status_background_assigned;
            case "completed":
                return R.drawable.status_background_assigned;
            default:
                return R.drawable.status_background_assigned;
        }
    }

    private class MyShiftAdapter extends RecyclerView.Adapter<MyShiftAdapter.VH> {

        private final List<JSONObject> items = new ArrayList<>();

        void setItems(List<JSONObject> data) {
            items.clear();
            items.addAll(data);
            // Avoid RecyclerView "computing layout" crashes on fast refreshes.
            try {
                notifyDataSetChanged();
            } catch (Exception ignored) {
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_worker_shift_enhanced, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject shift = items.get(position);

            holder.title.setText(shift.optString("title", "Shift"));
            holder.location.setText(shift.optString("location", ""));
            holder.date.setText(shift.optString("shiftDate", ""));

            String startTime = shift.optString("startTime", "");
            String endTime = shift.optString("endTime", "");
            if (!startTime.isEmpty() && !endTime.isEmpty()) {
                String formattedStart = formatTime(startTime);
                String formattedEnd = formatTime(endTime);
                holder.time.setText(formattedStart + " - " + formattedEnd);
            } else {
                holder.time.setText("Full Day");
            }

            holder.skills.setText(shift.optString("skillRequired", ""));

            double wage = shift.optDouble("wage", 0);
            int days = Math.max(1, shift.optInt("durationDays", 1));
            if (days > 1) {
                holder.wage.setText("\u20B9" + (int) wage + "/day \u00B7 " + days + " days");
            } else {
                holder.wage.setText("\u20B9" + (int) wage + "/day");
            }

            String status = shift.optString("status", "");
            String appStatusRaw = shift.optString("applicationStatus", "null");
            if ("null".equals(appStatusRaw) || appStatusRaw.isEmpty()) {
                appStatusRaw = "pending";
            }

            holder.statusBadge.setText(status.toUpperCase());
            holder.statusBadge.setBackgroundResource(getStatusBackground(status));

            String appStatusTitle = appStatusRaw.substring(0, 1).toUpperCase()
                    + appStatusRaw.substring(1).toLowerCase();
            String appLabel;
            switch (appStatusRaw.toLowerCase()) {
                case "accepted":
                    appLabel = "\u2713 Accepted - you got the shift!";
                    holder.applicationStatus.setTextColor(
                            ContextCompat.getColor(WorkerMyShiftsActivity.this, R.color.status_success));
                    break;
                case "rejected":
                    appLabel = "\u2717 Not selected this time";
                    holder.applicationStatus.setTextColor(
                            ContextCompat.getColor(WorkerMyShiftsActivity.this, R.color.status_busy));
                    break;
                default:
                    appLabel = "Application: " + appStatusTitle;
                    holder.applicationStatus.setTextColor(
                            ContextCompat.getColor(WorkerMyShiftsActivity.this, R.color.text_medium));
                    break;
            }
            holder.applicationStatus.setText(appLabel);

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
            String rawTime = (startTime.isEmpty() || endTime.isEmpty())
                    ? "Full Day"
                    : formatTime(startTime) + " - " + formatTime(endTime);
            String skills = shift.optString("skillRequired", "None");
            double wage = shift.optDouble("wage", 0);
            int days = Math.max(1, shift.optInt("durationDays", 1));
            int totalPayout = (int) wage * days;
            String status = shift.optString("status", "").toUpperCase();
            String appStatus = shift.optString("applicationStatus", "Unknown");

            String displayStatus = "null".equalsIgnoreCase(appStatus) ? "None" : appStatus;

            String message = "Date: " + date + "\n" +
                    "Time: " + rawTime + "\n" +
                    "Duration: " + days + (days == 1 ? " day" : " days") + "\n" +
                    "Location: " + location + "\n" +
                    "Skills: " + skills + "\n" +
                    "Wage: \u20B9" + (int) wage + "/day"
                    + (days > 1 ? "  (Total: \u20B9" + totalPayout + ")" : "") + "\n\n" +
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
