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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class EmployerFindWorkersActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private WorkerSearchAdapter adapter;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employer_find_workers);

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
        TextInputEditText skillFilter = findViewById(R.id.skill_filter);
        TextInputEditText locationFilter = findViewById(R.id.location_filter);
        SwitchMaterial availableOnly = findViewById(R.id.available_only);
        RecyclerView recyclerView = findViewById(R.id.recycler_workers);
        progressBar = findViewById(R.id.progress);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WorkerSearchAdapter(workerId -> {
            android.content.Intent intent = new android.content.Intent(this, WorkerPublicProfileActivity.class);
            intent.putExtra(WorkerPublicProfileActivity.EXTRA_WORKER_ID, workerId);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btn_search).setOnClickListener(v -> {
            String skill = skillFilter.getText() != null ? skillFilter.getText().toString().trim() : "";
            String loc = locationFilter.getText() != null ? locationFilter.getText().toString().trim() : "";
            boolean onlyAvail = availableOnly.isChecked();
            searchWorkers(skill, loc, onlyAvail);
        });

        searchWorkers("", "", false);
    }

    private void searchWorkers(String skill, String location, boolean availableOnly) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                StringBuilder path = new StringBuilder("/api/users/workers/search");
                List<String> parts = new ArrayList<>();
                if (!skill.isEmpty()) {
                    parts.add("skill=" + URLEncoder.encode(skill, StandardCharsets.UTF_8.name()));
                }
                if (!location.isEmpty()) {
                    parts.add("location=" + URLEncoder.encode(location, StandardCharsets.UTF_8.name()));
                }
                if (availableOnly) {
                    parts.add("availableOnly=true");
                }
                if (!parts.isEmpty()) {
                    path.append("?");
                    for (int i = 0; i < parts.size(); i++) {
                        if (i > 0) path.append("&");
                        path.append(parts.get(i));
                    }
                }

                ApiClient.HttpResult res = ApiClient.get(path.toString(), token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (res.code == 200) {
                        try {
                            JSONArray arr = new JSONObject(res.body).getJSONArray("workers");
                            List<JSONObject> list = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                list.add(arr.getJSONObject(i));
                            }
                            adapter.setItems(list);
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not read workers", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        try {
                            String msg = new JSONObject(res.body).optString("message", "Search failed");
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Search failed (" + res.code + ")", Toast.LENGTH_SHORT).show();
                        }
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

    private static class WorkerSearchAdapter extends RecyclerView.Adapter<WorkerSearchAdapter.VH> {

        interface OnWorkerClick {
            void onClick(String workerId);
        }

        private final List<JSONObject> items = new ArrayList<>();
        private final OnWorkerClick onWorkerClick;

        WorkerSearchAdapter(OnWorkerClick onWorkerClick) {
            this.onWorkerClick = onWorkerClick;
        }

        void setItems(List<JSONObject> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_worker_search, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            JSONObject w = items.get(position);
            holder.name.setText(w.optString("fullName", "Worker"));
            JSONArray skills = w.optJSONArray("skills");
            String skillsStr = "";
            if (skills != null && skills.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < skills.length(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(skills.optString(i, ""));
                }
                skillsStr = sb.toString();
            }
            String loc = w.optString("location", "");
            double rating = w.optDouble("rating", 0);
            boolean avail = w.optBoolean("isAvailable", false);
            holder.details.setText(
                    skillsStr
                            + " • "
                            + loc
                            + " • Rating "
                            + rating
                            + (avail ? " • Available" : "")
            );
            holder.phone.setText(w.optString("phoneNumber", ""));

            final String workerId = w.optString("id", w.optString("_id", ""));
            holder.itemView.setOnClickListener(v -> {
                if (onWorkerClick != null && workerId != null && !workerId.isEmpty()) {
                    onWorkerClick.onClick(workerId);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView details;
            final TextView phone;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.name);
                details = itemView.findViewById(R.id.details);
                phone = itemView.findViewById(R.id.phone);
            }
        }
    }
}
