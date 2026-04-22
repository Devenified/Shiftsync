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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorkerEarningsActivity extends AppCompatActivity {

    private TextView lifetimeView;
    private TextView todayView;
    private TextView monthlyView;
    private TextView completedView;
    private TextView emptyText;
    private ProgressBar progress;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView historyList;
    private EarningsAdapter adapter;

    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_earnings);

        if (!SessionManager.ensureWorker(this)) return;
        token = SessionManager.getToken(this);
        if (token == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        lifetimeView = findViewById(R.id.lifetime_earnings);
        todayView = findViewById(R.id.today_earnings);
        monthlyView = findViewById(R.id.monthly_earnings);
        completedView = findViewById(R.id.completed_shifts);
        emptyText = findViewById(R.id.empty_text);
        progress = findViewById(R.id.progress);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        historyList = findViewById(R.id.history_list);

        historyList.setLayoutManager(new LinearLayoutManager(this));
        historyList.setNestedScrollingEnabled(false);
        adapter = new EarningsAdapter();
        historyList.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.brand_primary, R.color.brand_secondary);
        swipeRefresh.setOnRefreshListener(this::loadAll);

        loadAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAll();
    }

    private void loadAll() {
        if (!swipeRefresh.isRefreshing()) {
            progress.setVisibility(View.VISIBLE);
        }
        new Thread(() -> {
            try {
                ApiClient.HttpResult dash = ApiClient.get("/api/users/worker-dashboard", token);
                ApiClient.HttpResult shifts = ApiClient.get("/api/shifts/worker/my", token);

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);

                    double lifetime = 0, today = 0, monthly = 0;
                    int completedCount = 0;
                    if (dash.code == 200) {
                        try {
                            JSONObject s = new JSONObject(dash.body).getJSONObject("summary");
                            lifetime = s.optDouble("totalEarnings", 0);
                            today = s.optDouble("todayEarnings", 0);
                            monthly = s.optDouble("monthlyEarnings", 0);
                            completedCount = s.optInt("completedShifts", 0);
                        } catch (Exception ignored) {
                        }
                    }
                    lifetimeView.setText("\u20B9" + (int) lifetime);
                    todayView.setText("\u20B9" + (int) today);
                    monthlyView.setText("\u20B9" + (int) monthly);
                    completedView.setText(String.valueOf(completedCount));

                    List<JSONObject> completedList = new ArrayList<>();
                    if (shifts.code == 200) {
                        try {
                            JSONArray arr = new JSONObject(shifts.body).getJSONArray("shifts");
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject s = arr.getJSONObject(i);
                                if ("completed".equalsIgnoreCase(s.optString("status", ""))) {
                                    completedList.add(s);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    adapter.setItems(completedList);
                    emptyText.setVisibility(completedList.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private class EarningsAdapter extends RecyclerView.Adapter<EarningsAdapter.VH> {
        private final List<JSONObject> items = new ArrayList<>();

        void setItems(List<JSONObject> data) {
            items.clear();
            items.addAll(data);
            try {
                notifyDataSetChanged();
            } catch (Exception ignored) {
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_earning_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject s = items.get(position);
            h.title.setText(s.optString("title", "Shift"));
            String date = s.optString("shiftDate", "");
            String company = "";
            JSONObject emp = s.optJSONObject("employer");
            if (emp != null) {
                String cn = emp.optString("companyName", "");
                String fn = emp.optString("fullName", "");
                company = cn.isEmpty() ? fn : cn;
            }
            StringBuilder meta = new StringBuilder();
            if (!company.isEmpty()) meta.append(company);
            if (!date.isEmpty()) {
                if (meta.length() > 0) meta.append(" \u2022 ");
                meta.append(date);
            }
            h.meta.setText(meta.toString());
            int wage = (int) s.optDouble("wage", 0);
            int days = Math.max(1, s.optInt("durationDays", 1));
            int total = wage * days;
            h.amount.setText(String.format(Locale.getDefault(), "+\u20B9%d", total));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;
            final TextView amount;

            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.row_title);
                meta = v.findViewById(R.id.row_meta);
                amount = v.findViewById(R.id.row_amount);
            }
        }
    }
}
