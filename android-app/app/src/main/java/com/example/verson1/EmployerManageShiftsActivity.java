package com.example.verson1;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class EmployerManageShiftsActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView emptyText;
    private SwipeRefreshLayout swipeRefresh;
    private EmployerShiftAdapter adapter;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employer_manage_shifts);

        if (!SessionManager.ensureEmployer(this)) {
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
        adapter = new EmployerShiftAdapter();
        recyclerView.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.brand_primary, R.color.brand_secondary);
        swipeRefresh.setOnRefreshListener(this::loadShifts);

        loadShifts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadShifts();
    }

    private void loadShifts() {
        if (!swipeRefresh.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/shifts/employer/my", token);
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
                            adapter.setItems(list);
                            updateEmptyState(list.isEmpty());
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

    private void reviewApplication(String shiftId, String workerId, String decision) {
        new Thread(() -> {
            try {
                String path = "/api/shifts/" + shiftId + "/applications/" + workerId;
                JSONObject body = new JSONObject();
                body.put("decision", decision);
                ApiClient.HttpResult res = ApiClient.patch(path, token, body.toString());
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        Snackbar.make(recyclerView, "Application " + decision, Snackbar.LENGTH_LONG)
                                .setBackgroundTint(ContextCompat.getColor(this, R.color.status_available))
                                .show();
                        loadShifts();
                    } else {
                        Toast.makeText(this, "Could not update application", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(
                        () -> Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private void openWorkerProfile(String workerId) {
        if (workerId == null || workerId.isEmpty()) return;
        android.content.Intent intent = new android.content.Intent(this, WorkerPublicProfileActivity.class);
        intent.putExtra(WorkerPublicProfileActivity.EXTRA_WORKER_ID, workerId);
        startActivity(intent);
    }

    private void confirmCompleteShift(String shiftId, String shiftTitle, int paidCount, double wage, int pending) {
        StringBuilder msg = new StringBuilder();
        msg.append("This will pay ").append(paidCount)
                .append(paidCount == 1 ? " worker " : " workers ")
                .append("\u20B9").append((int) wage).append(" each and close the shift.");
        if (pending > 0) {
            msg.append("\n\n").append(pending)
                    .append(pending == 1 ? " pending applicant " : " pending applicants ")
                    .append("will be auto-rejected.");
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Complete \"" + shiftTitle + "\"?")
                .setMessage(msg.toString())
                .setPositiveButton("Mark Completed", (d, w) -> completeShift(shiftId))
                .setNegativeButton("Not yet", null)
                .show();
    }

    private void completeShift(String shiftId) {
        new Thread(() -> {
            try {
                String path = "/api/shifts/" + shiftId + "/complete";
                ApiClient.HttpResult res = ApiClient.patch(path, token, "{}");
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        Snackbar.make(recyclerView, "Shift marked as completed", Snackbar.LENGTH_LONG)
                                .setBackgroundTint(ContextCompat.getColor(this, R.color.status_available))
                                .show();
                        loadShifts();
                    } else {
                        try {
                            String msg = new JSONObject(res.body).optString("message", "Complete failed");
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Complete failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(
                        () -> Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private void confirmCancelShift(String shiftId, String shiftTitle) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel Shift")
                .setMessage("Are you sure you want to cancel \"" + shiftTitle + "\"? This action cannot be undone.")
                .setPositiveButton("Cancel Shift", (d, w) -> cancelShift(shiftId))
                .setNegativeButton("Keep", null)
                .show();
    }

    private void cancelShift(String shiftId) {
        new Thread(() -> {
            try {
                String path = "/api/shifts/" + shiftId;
                ApiClient.HttpResult res = ApiClient.delete(path, token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        Snackbar.make(recyclerView, "Shift cancelled successfully", Snackbar.LENGTH_LONG)
                                .setBackgroundTint(ContextCompat.getColor(this, R.color.status_available))
                                .show();
                        loadShifts();
                    } else {
                        try {
                            String msg = new JSONObject(res.body).optString("message", "Cancel failed");
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Cancel failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(
                        () -> Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private class EmployerShiftAdapter extends RecyclerView.Adapter<EmployerShiftAdapter.VH> {

        private final List<JSONObject> items = new ArrayList<>();

        void setItems(List<JSONObject> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_employer_shift, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject shift = items.get(position);
            String shiftId = JsonHelper.idString(shift, "_id");
            String title = shift.optString("title", "Shift");
            holder.title.setText(title);
            String status = shift.optString("status", "");
            holder.meta.setText(
                    status
                            + " • "
                            + shift.optString("skillRequired", "")
                            + " • "
                            + shift.optString("location", "")
                            + " • "
                            + shift.optString("shiftDate", "")
            );
            double wage = shift.optDouble("wage", 0);
            holder.wage.setText("\u20B9" + (int) wage);

            int workersNeeded = Math.max(1, shift.optInt("workersNeeded", 1));
            int slotsFilled = shift.optInt("slotsFilled", -1);
            int pendingCount = 0;

            holder.applicationsContainer.removeAllViews();
            JSONArray apps = shift.optJSONArray("applications");
            int applicantCount = apps == null ? 0 : apps.length();
            if (slotsFilled < 0 && apps != null) {
                int acc = 0;
                for (int k = 0; k < apps.length(); k++) {
                    JSONObject a = apps.optJSONObject(k);
                    if (a != null && "accepted".equals(a.optString("status"))) acc++;
                }
                slotsFilled = acc;
            }
            if (slotsFilled < 0) slotsFilled = 0;
            if (apps != null) {
                for (int k = 0; k < apps.length(); k++) {
                    JSONObject a = apps.optJSONObject(k);
                    if (a != null && "pending".equals(a.optString("status"))) pendingCount++;
                }
            }

            if (holder.slotProgressLabel != null) {
                String progressText = slotsFilled + " / " + workersNeeded
                        + (workersNeeded == 1 ? " slot filled" : " slots filled");
                holder.slotProgressLabel.setText(progressText);
                if (slotsFilled >= workersNeeded) {
                    holder.slotProgressLabel.setBackgroundResource(R.drawable.bg_pill_success);
                } else if (slotsFilled > 0) {
                    holder.slotProgressLabel.setBackgroundResource(R.drawable.bg_pill_warning);
                } else {
                    holder.slotProgressLabel.setBackgroundResource(R.drawable.bg_pill_info);
                }
            }

            if (holder.applicantCountLabel != null) {
                holder.applicantCountLabel.setText(applicantCount == 0
                        ? "No applicants yet"
                        : applicantCount + (applicantCount == 1 ? " applicant" : " applicants"));
            }
            if (apps != null) {
                android.view.LayoutInflater inflater =
                        android.view.LayoutInflater.from(holder.itemView.getContext());
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject app = apps.optJSONObject(i);
                    if (app == null) continue;
                    JSONObject worker = app.optJSONObject("worker");
                    String workerId = "";
                    String name = "Worker";
                    double rating = 0;
                    int completed = 0;
                    String workerSkills = "";
                    if (worker != null) {
                        workerId = JsonHelper.idString(worker, "_id");
                        if (workerId.isEmpty()) {
                            workerId = worker.optString("_id", "");
                        }
                        name = worker.optString("fullName", "Worker");
                        rating = worker.optDouble("rating", 0);
                        completed = worker.optInt("completedShifts", 0);
                        JSONArray sk = worker.optJSONArray("skills");
                        if (sk != null && sk.length() > 0) {
                            StringBuilder sb = new StringBuilder();
                            for (int s = 0; s < sk.length(); s++) {
                                if (s > 0) sb.append(", ");
                                sb.append(sk.optString(s));
                            }
                            workerSkills = sb.toString();
                        }
                    }
                    String appStatus = app.optString("status", "pending");

                    View row = inflater.inflate(R.layout.item_applicant_card,
                            holder.applicationsContainer, false);

                    TextView initial = row.findViewById(R.id.applicant_initial);
                    TextView nameTv = row.findViewById(R.id.applicant_name);
                    TextView metaTv = row.findViewById(R.id.applicant_meta);
                    TextView statusPill = row.findViewById(R.id.applicant_status_pill);
                    com.google.android.material.button.MaterialButton viewProfile =
                            row.findViewById(R.id.btn_view_profile);
                    com.google.android.material.button.MaterialButton acceptBtn =
                            row.findViewById(R.id.btn_accept);
                    com.google.android.material.button.MaterialButton rejectBtn =
                            row.findViewById(R.id.btn_reject);

                    initial.setText(name.isEmpty() ? "W" : name.substring(0, 1).toUpperCase());
                    nameTv.setText(name);
                    StringBuilder meta = new StringBuilder();
                    meta.append("\u2605 ")
                            .append(String.format(java.util.Locale.getDefault(), "%.1f", rating))
                            .append(" \u2022 ")
                            .append(completed)
                            .append(completed == 1 ? " shift" : " shifts");
                    if (!workerSkills.isEmpty()) {
                        meta.append(" \u2022 ").append(workerSkills);
                    }
                    metaTv.setText(meta.toString());

                    statusPill.setText(appStatus.toUpperCase());
                    switch (appStatus) {
                        case "accepted":
                            statusPill.setBackgroundResource(R.drawable.bg_pill_success);
                            break;
                        case "rejected":
                            statusPill.setBackgroundResource(R.drawable.bg_pill_warning);
                            break;
                        default:
                            statusPill.setBackgroundResource(R.drawable.bg_pill_info);
                    }

                    final String finalWorkerId = workerId;
                    final String finalShiftId = shiftId;
                    viewProfile.setOnClickListener(v -> openWorkerProfile(finalWorkerId));

                    int remaining = Math.max(0, workersNeeded - slotsFilled);
                    boolean canReview = "open".equals(status)
                            && "pending".equals(appStatus)
                            && !finalWorkerId.isEmpty()
                            && remaining > 0;
                    acceptBtn.setVisibility(canReview ? View.VISIBLE : View.GONE);
                    rejectBtn.setVisibility(canReview ? View.VISIBLE : View.GONE);
                    if (canReview) {
                        acceptBtn.setOnClickListener(v ->
                                reviewApplication(finalShiftId, finalWorkerId, "accepted"));
                        rejectBtn.setOnClickListener(v ->
                                reviewApplication(finalShiftId, finalWorkerId, "rejected"));
                    }

                    holder.applicationsContainer.addView(row);
                }
            }

            // Complete button: allowed for assigned, or for open shifts that already
            // have at least one accepted worker (multi-worker partial-fill early end).
            boolean showComplete = "assigned".equals(status)
                    || ("open".equals(status) && slotsFilled > 0);
            holder.complete.setVisibility(showComplete ? View.VISIBLE : View.GONE);
            if (showComplete) {
                final int finalFilled = slotsFilled;
                final int finalPending = pendingCount;
                final String finalShiftIdForComplete = shiftId;
                final String finalTitle = title;
                final double finalWage = wage;
                if ("open".equals(status) && slotsFilled < workersNeeded) {
                    holder.complete.setText("End Early (" + finalFilled + "/" + workersNeeded + ")");
                } else {
                    holder.complete.setText("Complete Shift");
                }
                holder.complete.setOnClickListener(v -> confirmCompleteShift(
                        finalShiftIdForComplete, finalTitle, finalFilled, finalWage, finalPending));
            }

            // Show cancel button for open or assigned shifts
            boolean showCancel = "open".equals(status) || "assigned".equals(status);
            holder.cancel.setVisibility(showCancel ? View.VISIBLE : View.GONE);
            if (showCancel) {
                holder.cancel.setOnClickListener(v -> confirmCancelShift(shiftId, title));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;
            final TextView wage;
            final TextView applicantCountLabel;
            final TextView slotProgressLabel;
            final LinearLayout applicationsContainer;
            final Button complete;
            final Button cancel;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.title);
                meta = itemView.findViewById(R.id.meta);
                wage = itemView.findViewById(R.id.wage);
                applicantCountLabel = itemView.findViewById(R.id.applicant_count_label);
                slotProgressLabel = itemView.findViewById(R.id.slot_progress_label);
                applicationsContainer = itemView.findViewById(R.id.applications_container);
                complete = itemView.findViewById(R.id.btn_complete);
                cancel = itemView.findViewById(R.id.btn_cancel);
            }
        }
    }
}
