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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class EmployerManageShiftsActivity extends AppCompatActivity {

    private ProgressBar progressBar;
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
        RecyclerView recyclerView = findViewById(R.id.list);
        progressBar = findViewById(R.id.progress);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EmployerShiftAdapter();
        recyclerView.setAdapter(adapter);

        loadShifts();
    }

    private void loadShifts() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/shifts/employer/my", token);
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

    private void reviewApplication(String shiftId, String workerId, String decision) {
        new Thread(() -> {
            try {
                String path = "/api/shifts/" + shiftId + "/applications/" + workerId;
                JSONObject body = new JSONObject();
                body.put("decision", decision);
                ApiClient.HttpResult res = ApiClient.patch(path, token, body.toString());
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        Toast.makeText(this, "Application " + decision, Toast.LENGTH_SHORT).show();
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

    private void completeShift(String shiftId) {
        new Thread(() -> {
            try {
                String path = "/api/shifts/" + shiftId + "/complete";
                ApiClient.HttpResult res = ApiClient.patch(path, token, "{}");
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        Toast.makeText(this, "Shift completed", Toast.LENGTH_SHORT).show();
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
            holder.title.setText(shift.optString("title", "Shift"));
            String status = shift.optString("status", "");
            holder.meta.setText(
                    status
                            + " · "
                            + shift.optString("skillRequired", "")
                            + " · "
                            + shift.optString("location", "")
                            + " · "
                            + shift.optString("shiftDate", "")
            );
            double wage = shift.optDouble("wage", 0);
            holder.wage.setText("₹" + (int) wage);

            holder.applicationsContainer.removeAllViews();
            JSONArray apps = shift.optJSONArray("applications");
            if (apps != null) {
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject app = apps.optJSONObject(i);
                    if (app == null) continue;
                    JSONObject worker = app.optJSONObject("worker");
                    String workerId = "";
                    String name = "Worker";
                    if (worker != null) {
                        workerId = JsonHelper.idString(worker, "_id");
                        if (workerId.isEmpty()) {
                            workerId = worker.optString("_id", "");
                        }
                        name = worker.optString("fullName", "Worker");
                    }
                    String appStatus = app.optString("status", "");

                    TextView line = new TextView(holder.itemView.getContext());
                    line.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_medium));
                    line.setText(name + " — " + appStatus);
                    line.setPadding(0, 4, 0, 4);
                    holder.applicationsContainer.addView(line);

                    if ("open".equals(status) && "pending".equals(appStatus) && !workerId.isEmpty()) {
                        final String finalShiftId = shiftId;
                        final String finalWorkerId = workerId;
                        com.google.android.material.button.MaterialButton accept = new com.google.android.material.button.MaterialButton(holder.itemView.getContext());
                        accept.setText("Accept " + name);
                        accept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_orange)));
                        accept.setTextColor(
                                ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_white));
                        accept.setCornerRadius(16);
                        LinearLayout.LayoutParams paramsAccept = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        paramsAccept.setMargins(0, 16, 0, 8);
                        accept.setLayoutParams(paramsAccept);
                        accept.setOnClickListener(
                                v -> reviewApplication(finalShiftId, finalWorkerId, "accepted")
                        );
                        holder.applicationsContainer.addView(accept);

                        com.google.android.material.button.MaterialButton reject = new com.google.android.material.button.MaterialButton(holder.itemView.getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                        reject.setText("Reject");
                        reject.setStrokeColor(android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_orange)));
                        reject.setStrokeWidth(2);
                        reject.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_surface)));
                        reject.setTextColor(
                                ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_white));
                        reject.setCornerRadius(16);
                        LinearLayout.LayoutParams paramsReject = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        paramsReject.setMargins(0, 0, 0, 16);
                        reject.setLayoutParams(paramsReject);
                        reject.setOnClickListener(
                                v -> reviewApplication(finalShiftId, finalWorkerId, "rejected")
                        );
                        holder.applicationsContainer.addView(reject);
                    }
                }
            }

            boolean showComplete = "assigned".equals(status);
            holder.complete.setVisibility(showComplete ? View.VISIBLE : View.GONE);
            if (showComplete) {
                holder.complete.setOnClickListener(v -> completeShift(shiftId));
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
            final LinearLayout applicationsContainer;
            final Button complete;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.title);
                meta = itemView.findViewById(R.id.meta);
                wage = itemView.findViewById(R.id.wage);
                applicationsContainer = itemView.findViewById(R.id.applications_container);
                complete = itemView.findViewById(R.id.btn_complete);
            }
        }
    }
}
