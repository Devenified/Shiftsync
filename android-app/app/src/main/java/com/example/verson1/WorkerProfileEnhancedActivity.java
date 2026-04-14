package com.example.verson1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WorkerProfileEnhancedActivity extends AppCompatActivity {

    // UI Components
    private TextView workerName;
    private TextView workerRole;
    private TextView availabilityStatus;
    private TextView completedShifts;
    private TextView rating;
    private TextView earnings;
    private TextView workerBio;
    private TextView skillLevel;
    private TextView experienceText;
    private TextView phoneNumber;
    private TextView emailAddress;
    private TextView locationText;
    private TextView addressText;
    private TextView expectedWage;
    private TextView workFlexibility;
    private TextView preferredHours;
    private TextView noWorkExperience;
    private ImageView profilePhoto;
    private ImageView editProfileBtn;
    private RecyclerView skillsRecycler;
    private RecyclerView previousWorkRecycler;
    
    // Data
    private String authToken;
    private SkillsAdapter skillsAdapter;
    private PreviousWorkAdapter previousWorkAdapter;
    private List<String> skillsList;
    private List<PreviousWorkItem> previousWorkList;
    
    // Real-time update receiver
    private BroadcastReceiver profileUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worker_profile_enhanced);

        // Check session
        if (!SessionManager.ensureWorker(this)) {
            return;
        }

        authToken = SessionManager.getToken(this);
        if (authToken == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        initializeViews();
        setupClickListeners();
        setupRecyclerViews();
        setupProfileUpdateReceiver();
        fetchProfileData();
    }

    private void setupProfileUpdateReceiver() {
        profileUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("PROFILE_UPDATED".equals(intent.getAction())) {
                    String updatedData = intent.getStringExtra("updated_data");
                    if (updatedData != null) {
                        try {
                            JSONObject userData = new JSONObject(updatedData);
                            updateProfileUI(userData);
                        } catch (Exception e) {
                            // Handle error
                        }
                    }
                }
            }
        };
        
        // Register receiver
        IntentFilter filter = new IntentFilter("PROFILE_UPDATED");
        registerReceiver(profileUpdateReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister receiver
        if (profileUpdateReceiver != null) {
            unregisterReceiver(profileUpdateReceiver);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when activity resumes
        fetchProfileData();
    }

    private void initializeViews() {
        // Header
        workerName = findViewById(R.id.worker_name);
        workerRole = findViewById(R.id.worker_role);
        availabilityStatus = findViewById(R.id.availability_status);
        profilePhoto = findViewById(R.id.profile_photo);
        editProfileBtn = findViewById(R.id.edit_profile_btn);
        
        // Stats
        completedShifts = findViewById(R.id.completed_shifts);
        rating = findViewById(R.id.rating);
        earnings = findViewById(R.id.earnings);
        
        // Bio
        workerBio = findViewById(R.id.worker_bio);
        
        // Skills
        skillLevel = findViewById(R.id.skill_level);
        experienceText = findViewById(R.id.experience_text);
        skillsRecycler = findViewById(R.id.skills_recycler);
        
        // Previous Work
        previousWorkRecycler = findViewById(R.id.previous_work_recycler);
        noWorkExperience = findViewById(R.id.no_work_experience);
        
        // Contact
        phoneNumber = findViewById(R.id.phone_number);
        emailAddress = findViewById(R.id.email_address);
        locationText = findViewById(R.id.location_text);
        addressText = findViewById(R.id.address_text);
        
        // Preferences
        expectedWage = findViewById(R.id.expected_wage);
        workFlexibility = findViewById(R.id.work_flexibility);
        preferredHours = findViewById(R.id.preferred_hours);
        
        // Set content descriptions for accessibility
        setupAccessibility();
    }

    private void setupAccessibility() {
        profilePhoto.setContentDescription("Profile picture");
        editProfileBtn.setContentDescription("Edit profile");
        findViewById(R.id.edit_profile_button).setContentDescription("Edit profile details");
        findViewById(R.id.share_profile_button).setContentDescription("Share profile link");
    }

    private void setupClickListeners() {
        // Edit profile button in header
        editProfileBtn.setOnClickListener(v -> {
            showEditProfileDialog();
        });

        // Edit profile button at bottom
        findViewById(R.id.edit_profile_button).setOnClickListener(v -> {
            showEditProfileDialog();
        });

        // Share profile button
        findViewById(R.id.share_profile_button).setOnClickListener(v -> {
            shareProfile();
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });
        LogoutUiHelper.attachSignOutMenu(this, toolbar);
    }

    private void setupRecyclerViews() {
        // Skills RecyclerView
        skillsList = new ArrayList<>();
        skillsAdapter = new SkillsAdapter(skillsList);
        skillsRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        skillsRecycler.setAdapter(skillsAdapter);

        // Previous Work RecyclerView
        previousWorkList = new ArrayList<>();
        previousWorkAdapter = new PreviousWorkAdapter(previousWorkList);
        previousWorkRecycler.setLayoutManager(new LinearLayoutManager(this));
        previousWorkRecycler.setAdapter(previousWorkAdapter);
        previousWorkRecycler.setNestedScrollingEnabled(false);
    }

    private void fetchProfileData() {
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/users/me", authToken);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        try {
                            JSONObject response = new JSONObject(res.body);
                            JSONObject user = response.getJSONObject("user");
                            updateProfileUI(user);
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not load profile data", Toast.LENGTH_SHORT).show();
                        }
                    } else if (res.code == 401) {
                        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else {
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateProfileUI(JSONObject user) {
        try {
            // Basic Info
            workerName.setText(user.optString("fullName", "Worker Name"));
            workerRole.setText(getRoleDisplayText(user.optString("role", "worker")));
            
            // Availability
            boolean isAvailable = user.optBoolean("isAvailable", false);
            if (isAvailable) {
                availabilityStatus.setText("Available for work");
                availabilityStatus.setTextColor(getResources().getColor(R.color.status_available));
            } else {
                availabilityStatus.setText("Not available");
                availabilityStatus.setTextColor(getResources().getColor(R.color.text_medium));
            }
            
            // Stats
            completedShifts.setText(String.valueOf(user.optInt("completedShifts", 0)));
            rating.setText(String.format(Locale.getDefault(), "%.1f", user.optDouble("rating", 0)));
            earnings.setText("₹" + (int) user.optDouble("totalEarnings", 0));
            
            // Bio
            String bio = user.optString("bio", "");
            if (bio.isEmpty()) {
                workerBio.setText("Add a bio to tell employers about yourself...");
            } else {
                workerBio.setText(bio);
            }
            
            // Skills
            skillLevel.setText(getSkillLevelDisplay(user.optString("skillLevel", "")));
            experienceText.setText(user.optInt("experienceYears", 0) + " years of experience");
            
            // Update skills list
            skillsList.clear();
            JSONArray skillsArray = user.optJSONArray("skills");
            if (skillsArray != null) {
                for (int i = 0; i < skillsArray.length(); i++) {
                    skillsList.add(skillsArray.getString(i));
                }
            }
            skillsAdapter.notifyDataSetChanged();
            
            // Previous Work
            previousWorkList.clear();
            JSONArray previousWorkArray = user.optJSONArray("previousWork");
            if (previousWorkArray != null && previousWorkArray.length() > 0) {
                for (int i = 0; i < previousWorkArray.length(); i++) {
                    JSONObject work = previousWorkArray.getJSONObject(i);
                    String companyName = work.optString("companyName", "");
                    String position = work.optString("position", "");
                    String startDate = work.optString("startDate", "");
                    String endDate = work.optString("endDate", "");
                    String description = work.optString("description", "");
                    
                    previousWorkList.add(new PreviousWorkItem(companyName, position, startDate, endDate, description));
                }
                noWorkExperience.setVisibility(View.GONE);
            } else {
                noWorkExperience.setVisibility(View.VISIBLE);
            }
            previousWorkAdapter.notifyDataSetChanged();
            
            // Contact Info
            phoneNumber.setText(user.optString("phoneNumber", ""));
            emailAddress.setText(user.optString("email", ""));
            locationText.setText(user.optString("location", ""));
            
            // Address
            JSONObject address = user.optJSONObject("address");
            if (address != null) {
                String street = address.optString("street", "");
                String city = address.optString("city", "");
                String state = address.optString("state", "");
                String pincode = address.optString("pincode", "");
                String fullAddress = street + ", " + city + ", " + state + " - " + pincode;
                addressText.setText(fullAddress.trim());
            } else {
                addressText.setText("Address not provided");
            }
            
            // Preferences
            double expectedWageValue = user.optDouble("expectedWage", 0);
            if (expectedWageValue > 0) {
                expectedWage.setText("₹" + (int) expectedWageValue + "/day");
            } else {
                expectedWage.setText("Not specified");
            }
            
            workFlexibility.setText(getWorkFlexibilityDisplay(user.optString("workFlexibility", "")));
            preferredHours.setText(getPreferredHoursDisplay(user.optString("preferredWorkHours", "")));
            
        } catch (Exception e) {
            Toast.makeText(this, "Error updating profile UI", Toast.LENGTH_SHORT).show();
        }
    }

    private String getRoleDisplayText(String role) {
        switch (role) {
            case "worker":
                return "Skilled Worker";
            case "employer":
                return "Employer";
            default:
                return "Worker";
        }
    }

    private String getSkillLevelDisplay(String skillLevel) {
        switch (skillLevel) {
            case "beginner":
                return "Beginner";
            case "intermediate":
                return "Intermediate";
            case "expert":
                return "Expert";
            default:
                return "Not specified";
        }
    }

    private String getWorkFlexibilityDisplay(String flexibility) {
        switch (flexibility) {
            case "full-time":
                return "Full Time";
            case "part-time":
                return "Part Time";
            case "flexible":
                return "Flexible";
            case "any":
                return "Any";
            default:
                return "Flexible";
        }
    }

    private String getPreferredHoursDisplay(String hours) {
        switch (hours) {
            case "morning":
                return "Morning (6AM-12PM)";
            case "afternoon":
                return "Afternoon (12PM-6PM)";
            case "evening":
                return "Evening (6PM-12AM)";
            case "night":
                return "Night (12AM-6AM)";
            case "flexible":
                return "Flexible";
            default:
                return "Flexible";
        }
    }

    private void showEditProfileDialog() {
        startActivity(new Intent(this, EditProfileActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void shareProfile() {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out my ShiftSync profile");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "I'm available for work on ShiftSync! Check out my profile: " + 
                workerName.getText().toString() + " - " + locationText.getText().toString());
            startActivity(Intent.createChooser(shareIntent, "Share Profile"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not share profile", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // Skills Adapter
    private static class SkillsAdapter extends RecyclerView.Adapter<SkillsAdapter.ViewHolder> {
        private List<String> skills;

        public SkillsAdapter(List<String> skills) {
            this.skills = skills;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_skill_chip, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String skill = skills.get(position);
            holder.skillText.setText(skill);
        }

        @Override
        public int getItemCount() {
            return skills.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView skillText;

            ViewHolder(View itemView) {
                super(itemView);
                skillText = itemView.findViewById(R.id.skill_text);
            }
        }
    }

    // Previous Work Adapter
    private static class PreviousWorkAdapter extends RecyclerView.Adapter<PreviousWorkAdapter.ViewHolder> {
        private List<PreviousWorkItem> workItems;

        public PreviousWorkAdapter(List<PreviousWorkItem> workItems) {
            this.workItems = workItems;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_previous_work, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            PreviousWorkItem item = workItems.get(position);
            holder.companyName.setText(item.companyName);
            holder.position.setText(item.position);
            holder.duration.setText(getDurationText(item.startDate, item.endDate));
            holder.description.setText(item.description);
        }

        private String getDurationText(String startDate, String endDate) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date start = sdf.parse(startDate);
                Date end = endDate.isEmpty() ? new Date() : sdf.parse(endDate);
                
                SimpleDateFormat displayFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
                if (endDate.isEmpty()) {
                    return displayFormat.format(start) + " - Present";
                } else {
                    return displayFormat.format(start) + " - " + displayFormat.format(end);
                }
            } catch (Exception e) {
                return startDate + " - " + (endDate.isEmpty() ? "Present" : endDate);
            }
        }

        @Override
        public int getItemCount() {
            return workItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView companyName;
            TextView position;
            TextView duration;
            TextView description;

            ViewHolder(View itemView) {
                super(itemView);
                companyName = itemView.findViewById(R.id.company_name);
                position = itemView.findViewById(R.id.position);
                duration = itemView.findViewById(R.id.duration);
                description = itemView.findViewById(R.id.description);
            }
        }
    }

    // Previous Work Item
    private static class PreviousWorkItem {
        String companyName;
        String position;
        String startDate;
        String endDate;
        String description;

        public PreviousWorkItem(String companyName, String position, String startDate, String endDate, String description) {
            this.companyName = companyName;
            this.position = position;
            this.startDate = startDate;
            this.endDate = endDate;
            this.description = description;
        }
    }
}
