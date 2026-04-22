package com.example.verson1;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class EmployerFindWorkersActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView emptyText;
    private TextView resultsCount;
    private WorkerSearchAdapter adapter;
    private String token;

    private TextInputEditText searchInput;
    private TextInputEditText skillFilter;
    private TextInputEditText locationFilter;
    private SwitchMaterial availableOnly;

    private final List<JSONObject> allWorkers = new ArrayList<>();
    private String liveQuery = "";
    private int sortMode = 0; // 0=rating, 1=shifts, 2=experience, 3=available
    private double minRating = 0.0;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

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

        searchInput = findViewById(R.id.search_input);
        skillFilter = findViewById(R.id.skill_filter);
        locationFilter = findViewById(R.id.location_filter);
        availableOnly = findViewById(R.id.available_only);
        RecyclerView recyclerView = findViewById(R.id.recycler_workers);
        progressBar = findViewById(R.id.progress);
        emptyText = findViewById(R.id.empty_text);
        resultsCount = findViewById(R.id.results_count);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(null);
        adapter = new WorkerSearchAdapter(workerId -> {
            android.content.Intent intent = new android.content.Intent(this, WorkerPublicProfileActivity.class);
            intent.putExtra(WorkerPublicProfileActivity.EXTRA_WORKER_ID, workerId);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        setupTextWatchers();
        setupChips();

        findViewById(R.id.btn_search).setOnClickListener(v -> triggerSearch(false));

        triggerSearch(false);
    }

    private void setupTextWatchers() {
        TextWatcher debounce = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { triggerSearch(true); }
        };
        if (skillFilter != null) skillFilter.addTextChangedListener(debounce);
        if (locationFilter != null) locationFilter.addTextChangedListener(debounce);
        if (availableOnly != null) availableOnly.setOnCheckedChangeListener((b, c) -> triggerSearch(true));

        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    liveQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                    applyLocalFilters();
                }
            });
        }
    }

    private void setupChips() {
        ChipGroup ratingChips = findViewById(R.id.rating_chips);
        if (ratingChips != null) {
            ratingChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                if (id == R.id.chip_rating_3) minRating = 3.0;
                else if (id == R.id.chip_rating_4) minRating = 4.0;
                else if (id == R.id.chip_rating_45) minRating = 4.5;
                else minRating = 0.0;
                triggerSearch(true);
            });
        }

        ChipGroup sortChips = findViewById(R.id.sort_chips);
        if (sortChips != null) {
            sortChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                if (id == R.id.chip_sort_shifts) sortMode = 1;
                else if (id == R.id.chip_sort_experience) sortMode = 2;
                else if (id == R.id.chip_sort_available) sortMode = 3;
                else sortMode = 0;
                applyLocalFilters();
            });
        }
    }

    private void triggerSearch(boolean debounced) {
        if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);
        pendingSearch = () -> {
            String skill = textOf(skillFilter);
            String loc = textOf(locationFilter);
            boolean avail = availableOnly != null && availableOnly.isChecked();
            searchWorkers(skill, loc, avail, minRating);
        };
        debounceHandler.postDelayed(pendingSearch, debounced ? 450 : 0);
    }

    private String textOf(TextInputEditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void searchWorkers(String skill, String location, boolean availableOnlyFlag, double minRating) {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                StringBuilder path = new StringBuilder("/api/users/workers/search");
                List<String> parts = new ArrayList<>();
                if (!skill.isEmpty()) parts.add("skill=" + URLEncoder.encode(skill, StandardCharsets.UTF_8.name()));
                if (!location.isEmpty()) parts.add("location=" + URLEncoder.encode(location, StandardCharsets.UTF_8.name()));
                if (availableOnlyFlag) parts.add("availableOnly=true");
                if (minRating > 0) parts.add("minRating=" + minRating);
                if (!parts.isEmpty()) {
                    path.append("?");
                    for (int i = 0; i < parts.size(); i++) {
                        if (i > 0) path.append("&");
                        path.append(parts.get(i));
                    }
                }

                ApiClient.HttpResult res = ApiClient.get(path.toString(), token);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    if (res.code == 200) {
                        try {
                            JSONArray arr = new JSONObject(res.body).getJSONArray("workers");
                            allWorkers.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                allWorkers.add(arr.getJSONObject(i));
                            }
                            applyLocalFilters();
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
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void applyLocalFilters() {
        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject w : allWorkers) {
            if (!matchesLiveQuery(w)) continue;
            filtered.add(w);
        }

        switch (sortMode) {
            case 1:
                Collections.sort(filtered, (a, b) -> Integer.compare(b.optInt("completedShifts", 0), a.optInt("completedShifts", 0)));
                break;
            case 2:
                Collections.sort(filtered, (a, b) -> Integer.compare(b.optInt("experienceYears", 0), a.optInt("experienceYears", 0)));
                break;
            case 3:
                Collections.sort(filtered, new Comparator<JSONObject>() {
                    @Override public int compare(JSONObject a, JSONObject b) {
                        int av = a.optBoolean("isAvailable", false) ? 1 : 0;
                        int bv = b.optBoolean("isAvailable", false) ? 1 : 0;
                        if (av != bv) return Integer.compare(bv, av);
                        return Double.compare(b.optDouble("rating", 0), a.optDouble("rating", 0));
                    }
                });
                break;
            default:
                Collections.sort(filtered, (a, b) -> Double.compare(b.optDouble("rating", 0), a.optDouble("rating", 0)));
                break;
        }

        adapter.setItems(filtered);

        boolean empty = filtered.isEmpty();
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (resultsCount != null) {
            int total = allWorkers.size();
            int shown = filtered.size();
            if (total == 0) {
                resultsCount.setText("No workers found yet. Tweak filters and try again.");
            } else if (shown == total) {
                resultsCount.setText(total + (total == 1 ? " worker found" : " workers found"));
            } else {
                resultsCount.setText(shown + " of " + total + " workers");
            }
        }
    }

    private boolean matchesLiveQuery(JSONObject w) {
        if (liveQuery.isEmpty()) return true;
        StringBuilder sb = new StringBuilder();
        sb.append(w.optString("fullName", "")).append(' ')
          .append(w.optString("location", "")).append(' ')
          .append(w.optString("phoneNumber", ""));
        JSONArray skills = w.optJSONArray("skills");
        if (skills != null) {
            for (int i = 0; i < skills.length(); i++) {
                sb.append(' ').append(skills.optString(i, ""));
            }
        }
        return sb.toString().toLowerCase(Locale.US).contains(liveQuery);
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
            int shifts = w.optInt("completedShifts", 0);

            StringBuilder details = new StringBuilder();
            if (!skillsStr.isEmpty()) details.append(skillsStr);
            if (!loc.isEmpty()) {
                if (details.length() > 0) details.append(" \u2022 ");
                details.append(loc);
            }
            if (details.length() > 0) details.append(" \u2022 ");
            details.append("\u2605 ").append(String.format(Locale.US, "%.1f", rating));
            if (shifts > 0) details.append(" \u2022 ").append(shifts).append(" shifts");
            if (avail) details.append(" \u2022 Available");

            holder.details.setText(details.toString());
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
