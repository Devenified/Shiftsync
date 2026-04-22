package com.example.verson1;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    // UI Components
    private ImageView profilePhoto;
    private TextView changePhotoText;
    
    // Personal Info
    private com.google.android.material.textfield.TextInputEditText fullNameInput;
    private com.google.android.material.textfield.TextInputEditText phoneInput;
    private com.google.android.material.textfield.TextInputEditText dobInput;
    private RadioGroup genderGroup;
    private RadioButton genderMale, genderFemale, genderOther;
    
    // Address
    private com.google.android.material.textfield.TextInputEditText streetInput;
    private com.google.android.material.textfield.TextInputEditText cityInput;
    private com.google.android.material.textfield.TextInputEditText stateInput;
    private com.google.android.material.textfield.TextInputEditText pincodeInput;
    private com.google.android.material.textfield.TextInputEditText locationInput;
    
    // Professional Info
    private com.google.android.material.textfield.TextInputEditText bioInput;
    private com.google.android.material.textfield.TextInputEditText experienceYearsInput;
    private AutoCompleteTextView skillLevelInput;
    private com.google.android.material.textfield.TextInputEditText skillInput;
    private Button addSkillButton;
    private RecyclerView skillsRecycler;
    private Button addWorkButton;
    private RecyclerView previousWorkRecycler;
    
    // Work Preferences
    private com.google.android.material.textfield.TextInputEditText expectedWageInput;
    private Switch wageNegotiableSwitch;
    private AutoCompleteTextView workFlexibilityInput;
    private AutoCompleteTextView preferredHoursInput;
    private Switch availabilitySwitch;
    
    // Emergency Contact
    private com.google.android.material.textfield.TextInputEditText emergencyNameInput;
    private com.google.android.material.textfield.TextInputEditText emergencyRelationInput;
    private com.google.android.material.textfield.TextInputEditText emergencyPhoneInput;
    
    // Action Buttons
    private Button cancelButton;
    private Button saveButton;
    
    // Data
    private String authToken;
    private List<String> skillsList;
    private List<PreviousWorkItem> previousWorkList;
    private SkillsAdapter skillsAdapter;
    private PreviousWorkAdapter previousWorkAdapter;
    
    // Dropdown Options
    private final String[] SKILL_LEVELS = {"Beginner", "Intermediate", "Expert"};
    private final String[] WORK_FLEXIBILITY = {"Full Time", "Part Time", "Flexible", "Any"};
    private final String[] PREFERRED_HOURS = {"Morning (6AM-12PM)", "Afternoon (12PM-6PM)", "Evening (6PM-12AM)", "Night (12AM-6AM)", "Flexible"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

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
        setupDropdowns();
        setupClickListeners();
        setupRecyclerViews();
        setupTextWatchers();
        loadCurrentProfile();
    }

    private void initializeViews() {
        // Header
        profilePhoto = findViewById(R.id.profile_photo);
        changePhotoText = findViewById(R.id.change_photo_text);
        
        // Personal Info
        fullNameInput = findViewById(R.id.full_name_input);
        phoneInput = findViewById(R.id.phone_input);
        dobInput = findViewById(R.id.dob_input);
        genderGroup = findViewById(R.id.gender_group);
        genderMale = findViewById(R.id.gender_male);
        genderFemale = findViewById(R.id.gender_female);
        genderOther = findViewById(R.id.gender_other);
        
        // Address
        streetInput = findViewById(R.id.street_input);
        cityInput = findViewById(R.id.city_input);
        stateInput = findViewById(R.id.state_input);
        pincodeInput = findViewById(R.id.pincode_input);
        locationInput = findViewById(R.id.location_input);
        
        // Professional Info
        bioInput = findViewById(R.id.bio_input);
        experienceYearsInput = findViewById(R.id.experience_years_input);
        skillLevelInput = findViewById(R.id.skill_level_input);
        skillInput = findViewById(R.id.skill_input);
        addSkillButton = findViewById(R.id.add_skill_button);
        skillsRecycler = findViewById(R.id.skills_recycler);
        addWorkButton = findViewById(R.id.add_work_button);
        previousWorkRecycler = findViewById(R.id.previous_work_recycler);
        
        // Work Preferences
        expectedWageInput = findViewById(R.id.expected_wage_input);
        wageNegotiableSwitch = findViewById(R.id.wage_negotiable_switch);
        workFlexibilityInput = findViewById(R.id.work_flexibility_input);
        preferredHoursInput = findViewById(R.id.preferred_hours_input);
        availabilitySwitch = findViewById(R.id.availability_switch);
        
        // Emergency Contact
        emergencyNameInput = findViewById(R.id.emergency_name_input);
        emergencyRelationInput = findViewById(R.id.emergency_relation_input);
        emergencyPhoneInput = findViewById(R.id.emergency_phone_input);
        
        // Action Buttons
        cancelButton = findViewById(R.id.cancel_button);
        saveButton = findViewById(R.id.save_button);
        
        // Set content descriptions for accessibility
        setupAccessibility();
    }

    private void setupAccessibility() {
        profilePhoto.setContentDescription("Profile photo");
        changePhotoText.setContentDescription("Change profile photo");
        addSkillButton.setContentDescription("Add skill to profile");
        addWorkButton.setContentDescription("Add previous work experience");
        cancelButton.setContentDescription("Cancel profile editing");
        saveButton.setContentDescription("Save profile changes");
    }

    private void setupDropdowns() {
        // Skill Level Dropdown
        ArrayAdapter<String> skillLevelAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_dropdown_item_1line, SKILL_LEVELS);
        skillLevelInput.setAdapter(skillLevelAdapter);
        
        // Work Flexibility Dropdown
        ArrayAdapter<String> workFlexibilityAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_dropdown_item_1line, WORK_FLEXIBILITY);
        workFlexibilityInput.setAdapter(workFlexibilityAdapter);
        
        // Preferred Hours Dropdown
        ArrayAdapter<String> preferredHoursAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_dropdown_item_1line, PREFERRED_HOURS);
        preferredHoursInput.setAdapter(preferredHoursAdapter);
    }

    private void setupClickListeners() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });
        LogoutUiHelper.attachSignOutMenu(this, toolbar);

        // Change Photo
        changePhotoText.setOnClickListener(v -> {
            Toast.makeText(this, "Photo upload coming soon!", Toast.LENGTH_SHORT).show();
        });

        // DOB Picker
        dobInput.setOnClickListener(v -> {
            showDatePicker();
        });

        // Add Skill
        addSkillButton.setOnClickListener(v -> {
            addSkill();
        });

        // Add Previous Work
        addWorkButton.setOnClickListener(v -> {
            addPreviousWork();
        });

        // Cancel
        cancelButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        // Save
        saveButton.setOnClickListener(v -> {
            saveProfile();
        });
    }

    private void setupRecyclerViews() {
        // Skills RecyclerView
        skillsList = new ArrayList<>();
        skillsAdapter = new SkillsAdapter(skillsList, this::removeSkill);
        skillsRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        skillsRecycler.setAdapter(skillsAdapter);

        // Previous Work RecyclerView
        previousWorkList = new ArrayList<>();
        previousWorkAdapter = new PreviousWorkAdapter(previousWorkList, this::removeWork);
        previousWorkRecycler.setLayoutManager(new LinearLayoutManager(this));
        previousWorkRecycler.setAdapter(previousWorkAdapter);
        previousWorkRecycler.setNestedScrollingEnabled(false);
    }

    private void setupTextWatchers() {
        // Auto-save on text change (debounced)
        TextWatcher autoSaveWatcher = new TextWatcher() {
            private Handler handler = new Handler(Looper.getMainLooper());
            private Runnable runnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(runnable);
                runnable = () -> {
                    // Auto-save logic can be implemented here
                    // For now, we'll just validate
                    validateForm();
                };
                handler.postDelayed(runnable, 1000); // 1 second delay
            }
        };

        // Add watchers to key fields
        fullNameInput.addTextChangedListener(autoSaveWatcher);
        phoneInput.addTextChangedListener(autoSaveWatcher);
        bioInput.addTextChangedListener(autoSaveWatcher);
        experienceYearsInput.addTextChangedListener(autoSaveWatcher);
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, selectedYear, selectedMonth, selectedDay) -> {
                String date = String.format(Locale.getDefault(), "%02d/%02d/%04d", 
                    selectedDay, selectedMonth + 1, selectedYear);
                dobInput.setText(date);
            },
            year, month, day
        );
        
        // Set max date to today
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void addSkill() {
        String skill = skillInput.getText().toString().trim();
        if (skill.isEmpty()) {
            Toast.makeText(this, "Please enter a skill", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (skillsList.contains(skill)) {
            Toast.makeText(this, "Skill already added", Toast.LENGTH_SHORT).show();
            return;
        }
        
        skillsList.add(skill);
        skillsAdapter.notifyDataSetChanged();
        skillInput.setText("");
        validateForm();
    }

    private void removeSkill(String skill) {
        skillsList.remove(skill);
        skillsAdapter.notifyDataSetChanged();
        validateForm();
    }

    private void addPreviousWork() {
        // Show dialog for adding work experience
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Add Previous Work")
               .setView(R.layout.dialog_add_work)
               .setPositiveButton("Add", (dialog, which) -> {
                   // Get data from dialog and add to list
                   addWorkFromDialog();
               })
               .setNegativeButton("Cancel", null)
               .show();
    }

    private void addWorkFromDialog() {
        // For now, add a dummy work item
        previousWorkList.add(new PreviousWorkItem("Company Name", "Position", "2020-01-01", "2021-01-01", "Work description"));
        previousWorkAdapter.notifyDataSetChanged();
        validateForm();
    }

    private void removeWork(PreviousWorkItem work) {
        previousWorkList.remove(work);
        previousWorkAdapter.notifyDataSetChanged();
        validateForm();
    }

    private void validateForm() {
        boolean isValid = true;
        
        // Basic validation
        if (fullNameInput.getText().toString().trim().isEmpty()) {
            isValid = false;
        }
        
        if (phoneInput.getText().toString().trim().isEmpty()) {
            isValid = false;
        }
        
        // Enable/disable save button
        saveButton.setEnabled(isValid);
        saveButton.setAlpha(isValid ? 1.0f : 0.5f);
    }

    private void loadCurrentProfile() {
        new Thread(() -> {
            try {
                ApiClient.HttpResult res = ApiClient.get("/api/users/me", authToken);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        try {
                            JSONObject response = new JSONObject(res.body);
                            JSONObject user = response.getJSONObject("user");
                            populateForm(user);
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

    private void populateForm(JSONObject user) {
        try {
            // Personal Info
            fullNameInput.setText(user.optString("fullName", ""));
            phoneInput.setText(user.optString("phoneNumber", ""));
            
            String dob = user.optString("dateOfBirth", "");
            if (!dob.isEmpty()) {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date date = inputFormat.parse(dob);
                dobInput.setText(outputFormat.format(date));
            }
            
            String gender = user.optString("gender", "");
            if ("male".equals(gender)) {
                genderMale.setChecked(true);
            } else if ("female".equals(gender)) {
                genderFemale.setChecked(true);
            } else if ("other".equals(gender)) {
                genderOther.setChecked(true);
            }
            
            // Address
            JSONObject address = user.optJSONObject("address");
            if (address != null) {
                streetInput.setText(address.optString("street", ""));
                cityInput.setText(address.optString("city", ""));
                stateInput.setText(address.optString("state", ""));
                pincodeInput.setText(address.optString("pincode", ""));
            }
            locationInput.setText(user.optString("location", ""));
            
            // Professional Info
            bioInput.setText(user.optString("bio", ""));
            experienceYearsInput.setText(String.valueOf(user.optInt("experienceYears", 0)));
            
            String skillLevel = user.optString("skillLevel", "");
            if (!skillLevel.isEmpty()) {
                skillLevelInput.setText(skillLevel.substring(0, 1).toUpperCase() + skillLevel.substring(1));
            }
            
            // Load skills
            skillsList.clear();
            JSONArray skillsArray = user.optJSONArray("skills");
            if (skillsArray != null) {
                for (int i = 0; i < skillsArray.length(); i++) {
                    skillsList.add(skillsArray.getString(i));
                }
            }
            skillsAdapter.notifyDataSetChanged();
            
            // Work Preferences
            expectedWageInput.setText(String.valueOf(user.optInt("expectedWage", 0)));
            wageNegotiableSwitch.setChecked(user.optBoolean("wageNegotiable", true));
            
            String workFlexibility = user.optString("workFlexibility", "");
            if (!workFlexibility.isEmpty()) {
                workFlexibilityInput.setText(workFlexibility.substring(0, 1).toUpperCase() + workFlexibility.substring(1));
            }
            
            String preferredHours = user.optString("preferredWorkHours", "");
            if (!preferredHours.isEmpty()) {
                preferredHoursInput.setText(getPreferredHoursDisplay(preferredHours));
            }
            
            availabilitySwitch.setChecked(user.optBoolean("isAvailable", false));
            
            // Emergency Contact
            JSONObject emergencyContact = user.optJSONObject("emergencyContact");
            if (emergencyContact != null) {
                emergencyNameInput.setText(emergencyContact.optString("name", ""));
                emergencyRelationInput.setText(emergencyContact.optString("relation", ""));
                emergencyPhoneInput.setText(emergencyContact.optString("phoneNumber", ""));
            }
            
            validateForm();
            
        } catch (Exception e) {
            Toast.makeText(this, "Error loading profile data", Toast.LENGTH_SHORT).show();
        }
    }

    private String getPreferredHoursDisplay(String hours) {
        switch (hours) {
            case "morning": return "Morning (6AM-12PM)";
            case "afternoon": return "Afternoon (12PM-6PM)";
            case "evening": return "Evening (6PM-12AM)";
            case "night": return "Night (12AM-6AM)";
            case "flexible": return "Flexible";
            default: return "Flexible";
        }
    }

    private void saveProfile() {
        // Show loading state
        saveButton.setEnabled(false);
        saveButton.setText("Saving...");
        
        // Validate essential fields
        if (fullNameInput.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show();
            saveButton.setEnabled(true);
            saveButton.setText("Save Profile");
            return;
        }
        
        if (phoneInput.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show();
            saveButton.setEnabled(true);
            saveButton.setText("Save Profile");
            return;
        }
        
        new Thread(() -> {
            try {
                JSONObject updates = new JSONObject();
                
                // Personal Info
                updates.put("fullName", fullNameInput.getText().toString().trim());
                updates.put("phoneNumber", phoneInput.getText().toString().trim());
                
                String dob = dobInput.getText().toString().trim();
                if (!dob.isEmpty()) {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Date date = inputFormat.parse(dob);
                    updates.put("dateOfBirth", outputFormat.format(date));
                }
                
                String gender = "male";
                if (genderFemale.isChecked()) gender = "female";
                else if (genderOther.isChecked()) gender = "other";
                updates.put("gender", gender);
                
                // Address
                JSONObject address = new JSONObject();
                address.put("street", streetInput.getText().toString().trim());
                address.put("city", cityInput.getText().toString().trim());
                address.put("state", stateInput.getText().toString().trim());
                address.put("pincode", pincodeInput.getText().toString().trim());
                address.put("landmark", ""); // Add landmark field
                updates.put("address", address);
                updates.put("location", locationInput.getText().toString().trim());
                
                // Professional Info
                updates.put("bio", bioInput.getText().toString().trim());
                
                String experienceText = experienceYearsInput.getText().toString().trim();
                int experience = 0;
                if (!experienceText.isEmpty()) {
                    experience = Integer.parseInt(experienceText);
                }
                updates.put("experienceYears", experience);
                
                String skillLevel = skillLevelInput.getText().toString().trim().toLowerCase();
                if (!skillLevel.isEmpty()) {
                    updates.put("skillLevel", skillLevel);
                }
                
                // Skills
                JSONArray skillsArray = new JSONArray();
                for (String skill : skillsList) {
                    skillsArray.put(skill.trim());
                }
                updates.put("skills", skillsArray);
                
                // Work Preferences
                String wageText = expectedWageInput.getText().toString().trim();
                if (!wageText.isEmpty()) {
                    updates.put("expectedWage", Integer.parseInt(wageText));
                }
                updates.put("wageNegotiable", wageNegotiableSwitch.isChecked());
                
                String flexibility = workFlexibilityInput.getText().toString().trim().toLowerCase();
                if (!flexibility.isEmpty()) {
                    updates.put("workFlexibility", flexibility);
                }
                
                String hours = preferredHoursInput.getText().toString().trim().toLowerCase();
                if (!hours.isEmpty()) {
                    updates.put("preferredWorkHours", getPreferredHoursValue(hours));
                }
                
                updates.put("isAvailable", availabilitySwitch.isChecked());
                
                // Set availability dates if available
                if (availabilitySwitch.isChecked()) {
                    updates.put("availableFrom", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                } else {
                    updates.put("availableFrom", JSONObject.NULL);
                    updates.put("availableTo", JSONObject.NULL);
                }
                
                // Emergency Contact
                JSONObject emergencyContact = new JSONObject();
                emergencyContact.put("name", emergencyNameInput.getText().toString().trim());
                emergencyContact.put("relation", emergencyRelationInput.getText().toString().trim());
                emergencyContact.put("phoneNumber", emergencyPhoneInput.getText().toString().trim());
                updates.put("emergencyContact", emergencyContact);
                
                // Mark profile as complete if essential fields are filled
                boolean hasEssentialFields = !skillsList.isEmpty() && 
                    experience > 0 && 
                    !locationInput.getText().toString().trim().isEmpty() &&
                    !fullNameInput.getText().toString().trim().isEmpty();
                
                updates.put("hasProfile", hasEssentialFields);
                
                // Send update request
                String payload = updates.toString();
                ApiClient.HttpResult res = ApiClient.patch("/api/users/me", authToken, payload);
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (res.code == 200) {
                        try {
                            JSONObject response = new JSONObject(res.body);
                            JSONObject updatedUser = response.getJSONObject("user");
                            
                            // Update session with new data
                            SessionManager.updateProfileData(this, updatedUser);
                            
                            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_LONG).show();
                            
                            // Return to profile with updated data
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("profile_updated", true);
                            setResult(RESULT_OK, resultIntent);
                            
                            finish();
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                            
                        } catch (Exception e) {
                            Toast.makeText(this, "Profile saved! Refreshing...", Toast.LENGTH_SHORT).show();
                            finish();
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                        }
                    } else {
                        try {
                            JSONObject errorResponse = new JSONObject(res.body);
                            String errorMsg = errorResponse.optString("message", "Failed to update profile");
                            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
                        }
                        saveButton.setEnabled(true);
                        saveButton.setText("Save Profile");
                    }
                });
                
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "Error saving profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    saveButton.setEnabled(true);
                    saveButton.setText("Save Profile");
                });
            }
        }).start();
    }

    private String getPreferredHoursValue(String display) {
        if (display.contains("Morning")) return "morning";
        if (display.contains("Afternoon")) return "afternoon";
        if (display.contains("Evening")) return "evening";
        if (display.contains("Night")) return "night";
        return "flexible";
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // Skills Adapter
    private static class SkillsAdapter extends RecyclerView.Adapter<SkillsAdapter.ViewHolder> {
        private List<String> skills;
        private OnSkillRemoveListener listener;

        public SkillsAdapter(List<String> skills, OnSkillRemoveListener listener) {
            this.skills = skills;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_skill_chip_editable, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String skill = skills.get(position);
            holder.skillText.setText(skill);
            holder.removeButton.setOnClickListener(v -> listener.onRemove(skill));
        }

        @Override
        public int getItemCount() {
            return skills.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView skillText;
            ImageView removeButton;

            ViewHolder(View itemView) {
                super(itemView);
                skillText = itemView.findViewById(R.id.skill_text);
                removeButton = itemView.findViewById(R.id.remove_skill);
            }
        }

        interface OnSkillRemoveListener {
            void onRemove(String skill);
        }
    }

    // Previous Work Adapter
    private static class PreviousWorkAdapter extends RecyclerView.Adapter<PreviousWorkAdapter.ViewHolder> {
        private List<PreviousWorkItem> workItems;
        private OnWorkRemoveListener listener;

        public PreviousWorkAdapter(List<PreviousWorkItem> workItems, OnWorkRemoveListener listener) {
            this.workItems = workItems;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_previous_work_editable, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            PreviousWorkItem item = workItems.get(position);
            holder.companyName.setText(item.companyName);
            holder.position.setText(item.position);
            holder.duration.setText(getDurationText(item.startDate, item.endDate));
            holder.removeButton.setOnClickListener(v -> listener.onRemove(item));
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
            ImageView removeButton;

            ViewHolder(View itemView) {
                super(itemView);
                companyName = itemView.findViewById(R.id.company_name);
                position = itemView.findViewById(R.id.position);
                duration = itemView.findViewById(R.id.duration);
                removeButton = itemView.findViewById(R.id.remove_work);
            }
        }

        interface OnWorkRemoveListener {
            void onRemove(PreviousWorkItem item);
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
