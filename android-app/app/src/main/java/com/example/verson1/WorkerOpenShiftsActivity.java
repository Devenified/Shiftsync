package com.example.verson1;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WorkerOpenShiftsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyText;
    private SwipeRefreshLayout swipeRefresh;
    private OpenShiftAdapter adapter;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_open_shifts);

        CrashReporter.install(this);

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
        recyclerView.setItemAnimator(null);
        adapter = new OpenShiftAdapter();
        recyclerView.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.brand_primary, R.color.brand_secondary);
        swipeRefresh.setOnRefreshListener(this::loadOpenShifts);

        View refreshBtn = findViewById(R.id.btn_refresh);
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(v -> loadOpenShifts());
        }

        loadOpenShifts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOpenShifts();
    }

    private void loadOpenShifts() {
        if (!swipeRefresh.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        updateEmptyState(false);
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/shifts/open", token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    if (res.code == 200) {
                        try {
                            JSONArray arr = new JSONObject(res.body).getJSONArray("shifts");
                            List<JSONObject> list = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                list.add(arr.getJSONObject(i));
                            }
                            adapter.setItems(list);
                            updateEmptyState(list.isEmpty());
                        } catch (Exception e) {
                            safeToast("Could not read shifts");
                        }
                    } else {
                        String msg;
                        try {
                            msg = new JSONObject(res.body).optString("message", "Failed to load shifts");
                        } catch (Exception e) {
                            msg = "Failed to load shifts (" + res.code + ")";
                        }
                        safeToast(msg);
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    safeToast("Connection error");
                });
            }
        }).start();
    }

    private void updateEmptyState(boolean isEmpty) {
        if (emptyText != null) emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void safeToast(String text) {
        try {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private void applyToShift(int position, String shiftId, MaterialButton applyButton) {
        if (shiftId == null || shiftId.trim().isEmpty()) {
            safeToast("Shift id missing");
            return;
        }
        applyButton.setEnabled(false);
        applyButton.setText("Applying...");
        new Thread(() -> {
            try {
                String path = "/api/shifts/" + shiftId + "/apply";
                ApiClient.HttpResult res = ApiClient.post(path, token, "{}");
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (res.code == 200 || res.code == 409) {
                        adapter.markAppliedAt(position);
                        safeToast(res.code == 409 ? "You already applied to this shift" : "Applied successfully!");
                    } else {
                        applyButton.setEnabled(true);
                        applyButton.setText("Apply");
                        String msg;
                        try {
                            msg = new JSONObject(res.body).optString("message", "Apply failed");
                        } catch (Exception e) {
                            msg = "Apply failed (" + res.code + ")";
                        }
                        safeToast(msg);
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    applyButton.setEnabled(true);
                    applyButton.setText("Apply");
                    safeToast("Connection error");
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
            try {
                notifyDataSetChanged();
            } catch (Exception ignored) {
            }
        }

        void markAppliedAt(int position) {
            if (position < 0 || position >= items.size()) return;
            try {
                items.get(position).put("hasApplied", true);
                notifyItemChanged(position);
            } catch (Exception ignored) {
            }
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
            try {
                JSONObject shift = items.get(position);
                String id = JsonHelper.idString(shift, "_id");
                if (id == null || id.isEmpty()) {
                    id = shift.optString("_id", "");
                }

                String title = shift.optString("title", "Shift");
                holder.title.setText(title);
                holder.location.setText(shift.optString("location", ""));
                holder.date.setText(shift.optString("shiftDate", ""));

                JSONObject employer = shift.optJSONObject("employer");
                if (holder.employerName != null) {
                    if (employer != null) {
                        String empName = employer.optString("fullName", "");
                        String companyName = employer.optString("companyName", "");
                        String display = companyName.isEmpty() ? empName : companyName;
                        holder.employerName.setText(display);
                        holder.employerName.setVisibility(display.isEmpty() ? View.GONE : View.VISIBLE);
                    } else {
                        holder.employerName.setVisibility(View.GONE);
                    }
                }

                String startTime = shift.optString("startTime", "");
                String endTime = shift.optString("endTime", "");
                if (!startTime.isEmpty() && !endTime.isEmpty()) {
                    holder.time.setText(formatTime(startTime) + " - " + formatTime(endTime));
                } else {
                    holder.time.setText("Full Day");
                }

                holder.skills.setText(shift.optString("skillRequired", ""));

                double wage = shift.optDouble("wage", 0);
                holder.wage.setText("\u20B9" + (int) wage);

                int workersNeeded = Math.max(1, shift.optInt("workersNeeded", 1));
                int slotsRemaining = shift.optInt("slotsRemaining", workersNeeded);
                if (holder.slots != null) {
                    if (workersNeeded > 1) {
                        holder.slots.setVisibility(View.VISIBLE);
                        holder.slots.setText(slotsRemaining + " of " + workersNeeded + " slots open");
                    } else {
                        holder.slots.setVisibility(View.GONE);
                    }
                }

                holder.status.setText("OPEN");
                holder.status.setBackgroundResource(R.drawable.status_background_open);

                boolean hasApplied = shift.optBoolean("hasApplied", false);
                final String shiftId = id;
                if (hasApplied) {
                    holder.apply.setText("Applied");
                    holder.apply.setEnabled(false);
                    holder.apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_surface)));
                    holder.apply.setTextColor(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.text_medium));
                    holder.apply.setOnClickListener(null);
                } else {
                    holder.apply.setText("Apply");
                    holder.apply.setEnabled(true);
                    holder.apply.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_primary)));
                    holder.apply.setTextColor(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_white));
                    holder.apply.setOnClickListener(v -> {
                        int pos = holder.getBindingAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) pos = position;
                        applyToShift(pos, shiftId, holder.apply);
                    });
                }
            } catch (Exception e) {
                holder.title.setText("Shift");
                holder.location.setText("");
                holder.date.setText("");
                holder.time.setText("");
                holder.skills.setText("");
                holder.wage.setText("\u20B90");
                holder.apply.setEnabled(false);
                holder.apply.setText("Unavailable");
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
            final TextView employerName;
            final TextView slots;
            final MaterialButton apply;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.shift_title);
                location = itemView.findViewById(R.id.shift_location);
                date = itemView.findViewById(R.id.shift_date);
                time = itemView.findViewById(R.id.shift_time);
                skills = itemView.findViewById(R.id.shift_skills);
                wage = itemView.findViewById(R.id.shift_wage);
                status = itemView.findViewById(R.id.shift_status);
                employerName = itemView.findViewById(R.id.shift_employer);
                slots = itemView.findViewById(R.id.shift_slots);
                apply = itemView.findViewById(R.id.btn_apply);
            }
        }
    }
}
