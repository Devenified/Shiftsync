package com.example.verson1;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight Google Maps–style location picker. Uses Android's built-in Geocoder for address
 * lookup + reverse geocoding (no paid API key required).
 */
public class LocationPickerActivity extends AppCompatActivity {

    public static final String EXTRA_PICKED_ADDRESS = "picked_address";
    public static final String EXTRA_PICKED_LAT = "picked_lat";
    public static final String EXTRA_PICKED_LNG = "picked_lng";
    public static final String EXTRA_INITIAL_QUERY = "initial_query";

    private static final int REQ_LOCATION = 2041;

    private static final String[] POPULAR = {
            "HSR Layout, Bengaluru",
            "Whitefield, Bengaluru",
            "Indiranagar, Bengaluru",
            "Koramangala, Bengaluru",
            "Andheri West, Mumbai",
            "Bandra Kurla Complex, Mumbai",
            "Gurugram Sector 54",
            "Connaught Place, New Delhi",
            "Hitec City, Hyderabad",
            "T. Nagar, Chennai"
    };

    private TextInputEditText searchInput;
    private TextView selectedPreview;
    private TextView resultsHeading;
    private ProgressBar locationProgress;
    private ChipGroup popularChips;
    private RecyclerView resultsList;
    private ResultAdapter adapter;

    private String pickedAddress = null;
    private Double pickedLat = null;
    private Double pickedLng = null;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        searchInput = findViewById(R.id.location_search);
        selectedPreview = findViewById(R.id.selected_preview);
        resultsHeading = findViewById(R.id.results_heading);
        locationProgress = findViewById(R.id.location_progress);
        popularChips = findViewById(R.id.popular_chips);
        resultsList = findViewById(R.id.results_list);

        MaterialButton btnUseCurrent = findViewById(R.id.btn_use_current);
        MaterialButton btnConfirm = findViewById(R.id.btn_confirm);

        resultsList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultAdapter();
        resultsList.setAdapter(adapter);

        populatePopularChips();

        String initial = getIntent().getStringExtra(EXTRA_INITIAL_QUERY);
        if (initial != null && !initial.trim().isEmpty()) {
            searchInput.setText(initial);
            pickedAddress = initial;
            selectedPreview.setText(initial);
            scheduleSearch(initial);
        }

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s == null ? "" : s.toString();
                if (q.trim().length() >= 3) {
                    scheduleSearch(q);
                } else {
                    adapter.setResults(new ArrayList<>());
                    resultsHeading.setVisibility(View.GONE);
                }
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = searchInput.getText() == null ? "" : searchInput.getText().toString();
                if (q.trim().length() >= 2) {
                    runGeocode(q, true);
                }
                return true;
            }
            return false;
        });

        btnUseCurrent.setOnClickListener(v -> tryUseCurrentLocation());

        btnConfirm.setOnClickListener(v -> {
            if (pickedAddress == null || pickedAddress.trim().isEmpty()) {
                String typed = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
                if (typed.isEmpty()) {
                    Toast.makeText(this, "Pick or type a location first", Toast.LENGTH_SHORT).show();
                    return;
                }
                pickedAddress = typed;
            }
            android.content.Intent data = new android.content.Intent();
            data.putExtra(EXTRA_PICKED_ADDRESS, pickedAddress);
            if (pickedLat != null) data.putExtra(EXTRA_PICKED_LAT, pickedLat);
            if (pickedLng != null) data.putExtra(EXTRA_PICKED_LNG, pickedLng);
            setResult(RESULT_OK, data);
            finish();
        });
    }

    private void populatePopularChips() {
        popularChips.removeAllViews();
        for (String name : POPULAR) {
            Chip chip = new Chip(this);
            chip.setText(name);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(R.color.brand_surface);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            chip.setChipStrokeWidth(2f);
            chip.setChipStrokeColorResource(R.color.brand_primary);
            chip.setOnClickListener(v -> {
                searchInput.setText(name);
                selectLocation(name, null, null);
                runGeocode(name, true);
            });
            popularChips.addView(chip);
        }
    }

    private void scheduleSearch(String query) {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        pendingSearch = () -> runGeocode(query, false);
        searchHandler.postDelayed(pendingSearch, 400);
    }

    private void runGeocode(String query, boolean showProgress) {
        if (!Geocoder.isPresent()) {
            resultsHeading.setVisibility(View.GONE);
            return;
        }
        if (showProgress) locationProgress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Address> found = new ArrayList<>();
            try {
                Geocoder geocoder = new Geocoder(this, new Locale("en", "IN"));
                List<Address> fromName = geocoder.getFromLocationName(query, 8);
                if (fromName != null) found.addAll(fromName);
            } catch (Exception ignored) {
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                locationProgress.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) return;
                if (found.isEmpty()) {
                    resultsHeading.setVisibility(View.GONE);
                    adapter.setResults(new ArrayList<>());
                } else {
                    resultsHeading.setVisibility(View.VISIBLE);
                    adapter.setResults(found);
                }
            });
        }).start();
    }

    private void selectLocation(String address, Double lat, Double lng) {
        pickedAddress = address;
        pickedLat = lat;
        pickedLng = lng;
        selectedPreview.setText(address);
    }

    private void tryUseCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }
        fetchCurrentLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean granted = false;
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (granted) {
                fetchCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission is required to auto-fill", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void fetchCurrentLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            Toast.makeText(this, "Location service unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        locationProgress.setVisibility(View.VISIBLE);

        Location best = null;
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                best = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (best == null) {
                best = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException ignored) {
        }

        if (best != null) {
            reverseGeocode(best);
            return;
        }

        try {
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    lm.removeUpdates(this);
                    reverseGeocode(location);
                }

                @Override public void onProviderDisabled(@NonNull String provider) {}
                @Override public void onProviderEnabled(@NonNull String provider) {}
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            };
            String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER
                    : LocationManager.NETWORK_PROVIDER;
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (locationProgress.getVisibility() == View.VISIBLE) {
                    locationProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "Couldn't detect your location. Try typing it instead.", Toast.LENGTH_SHORT).show();
                    try { lm.removeUpdates(listener); } catch (Exception ignored) {}
                }
            }, 10000);
        } catch (Exception e) {
            locationProgress.setVisibility(View.GONE);
            Toast.makeText(this, "Couldn't access location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void reverseGeocode(Location loc) {
        new Thread(() -> {
            String address = null;
            try {
                Geocoder geocoder = new Geocoder(this, new Locale("en", "IN"));
                List<Address> results = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                if (results != null && !results.isEmpty()) {
                    address = formatAddress(results.get(0));
                }
            } catch (Exception ignored) {
            }
            final String finalAddress = address != null ? address
                    : String.format(Locale.US, "%.5f, %.5f", loc.getLatitude(), loc.getLongitude());
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isFinishing() || isDestroyed()) return;
                locationProgress.setVisibility(View.GONE);
                searchInput.setText(finalAddress);
                selectLocation(finalAddress, loc.getLatitude(), loc.getLongitude());
            });
        }).start();
    }

    private static String formatAddress(Address a) {
        StringBuilder sb = new StringBuilder();
        if (a.getMaxAddressLineIndex() >= 0) {
            return a.getAddressLine(0);
        }
        if (a.getFeatureName() != null) sb.append(a.getFeatureName());
        if (a.getLocality() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.getLocality());
        }
        if (a.getAdminArea() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.getAdminArea());
        }
        return sb.toString();
    }

    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {

        private final List<Address> items = new ArrayList<>();

        void setResults(List<Address> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_location_result, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Address a = items.get(position);
            String line = formatAddress(a);
            String feature = a.getFeatureName();
            String subtitle;
            StringBuilder sb = new StringBuilder();
            if (a.getSubLocality() != null) sb.append(a.getSubLocality());
            if (a.getLocality() != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.getLocality());
            }
            if (a.getAdminArea() != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.getAdminArea());
            }
            subtitle = sb.length() == 0 ? (a.getCountryName() != null ? a.getCountryName() : "") : sb.toString();

            String title = feature != null && !feature.isEmpty() ? feature : line;
            holder.title.setText(title);
            holder.subtitle.setText(subtitle);
            holder.itemView.setOnClickListener(v ->
                    selectLocation(line, a.getLatitude(), a.getLongitude()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;
            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.result_title);
                subtitle = itemView.findViewById(R.id.result_subtitle);
            }
        }
    }
}
