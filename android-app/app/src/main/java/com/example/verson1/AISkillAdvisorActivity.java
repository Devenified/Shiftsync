package com.example.verson1;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Skill Advisor Activity
 * Allows workers to ask questions about skills, career development, and job opportunities
 * Powered by Google's Gemini API
 */
public class AISkillAdvisorActivity extends AppCompatActivity {

    private RecyclerView messageRecyclerView;
    private EditText questionEditText;
    private ImageButton sendButton;
    private MaterialButton skillAssessmentBtn, jobRecommendationsBtn;
    private ProgressBar progressBar;
    
    private AIMessageAdapter messageAdapter;
    private List<AIMessage> messages;
    private String authToken;
    
    private static final String TAG = "AISkillAdvisor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_skill_advisor);

        if (!SessionManager.ensureWorker(this)) {
            return;
        }

        authToken = SessionManager.getToken(this);
        if (authToken == null) {
            SessionManager.logoutToLogin(this);
            return;
        }

        initializeViews();
        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        addWelcomeMessage();
    }

    private void initializeViews() {
        messageRecyclerView = findViewById(R.id.message_recycler);
        questionEditText = findViewById(R.id.question_input);
        sendButton = findViewById(R.id.btn_send);
        skillAssessmentBtn = findViewById(R.id.btn_skill_assessment);
        jobRecommendationsBtn = findViewById(R.id.btn_job_recommendations);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle("AI Skill Advisor");
        toolbar.setSubtitle("Get personalized career guidance");
        LogoutUiHelper.attachSignOutMenu(this, toolbar);
    }

    private void setupRecyclerView() {
        messages = new ArrayList<>();
        messageAdapter = new AIMessageAdapter(messages);
        messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageRecyclerView.setItemAnimator(new DefaultItemAnimator());
        messageRecyclerView.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_ai_messages));
        messageRecyclerView.setAdapter(messageAdapter);
    }

    private void setupClickListeners() {
        sendButton.setOnClickListener(v -> sendQuestion());
        
        skillAssessmentBtn.setOnClickListener(v -> {
            showSkillAssessmentDialog();
        });
        
        jobRecommendationsBtn.setOnClickListener(v -> {
            showJobRecommendationsDialog();
        });

        questionEditText.setOnEditorActionListener((v, actionId, event) -> {
            sendQuestion();
            return true;
        });

        questionEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        updateSendButtonState();
    }

    private void addWelcomeMessage() {
        AIMessage welcome = new AIMessage(
                "Tell me your skills + location + experience and I’ll recommend the best work for you.\n\n" +
                        "Examples:\n" +
                        "• “Packing + basic Excel, Pune, 1 year”\n" +
                        "• “Cashier + customer support, Bangalore, 0 exp”\n" +
                        "• “Cleaning + housekeeping, Delhi, 2 years”\n\n" +
                        "Or tap “Job ideas” for quick recommendations.",
                AIMessage.MessageType.BOT,
                "Welcome"
        );
        messages.add(welcome);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        messageRecyclerView.scheduleLayoutAnimation();
        scrollToBottom();
    }

    private void sendQuestion() {
        String question = questionEditText.getText().toString().trim();
        
        if (question.isEmpty()) {
            Toast.makeText(this, "Please enter a question", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add user message to chat
        AIMessage userMessage = new AIMessage(question, AIMessage.MessageType.USER, "You");
        messages.add(userMessage);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        // Clear input
        questionEditText.setText("");
        sendButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        updateSendButtonState();

        // Send to backend
        askSkillAdvisor(question);
    }

    private void askSkillAdvisor(String question) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put(
                        "question",
                        "You are a career + shift advisor for ShiftSync. Give practical, India-friendly advice.\n" +
                                "Output format:\n" +
                                "1) Best-fit work (top 3)\n" +
                                "2) Why it fits (1 line each)\n" +
                                "3) What to do next (3 bullets)\n" +
                                "4) Skill upgrade (2 quick skills)\n\n" +
                                "User question:\n" +
                                question
                );

                ApiClient.HttpResult res = ApiClient.post(
                    "/api/ai/ask-skill-advisor",
                    authToken,
                    payload.toString()
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    updateSendButtonState();

                    if (res.code == 200) {
                        try {
                            JSONObject response = new JSONObject(res.body);
                            String answer = response.optString("answer", "I'm sorry, I couldn't generate a response. Please try again.");
                            
                            // Add bot response to chat
                            AIMessage botMessage = new AIMessage(answer, AIMessage.MessageType.BOT, "AI Advisor");
                            messages.add(botMessage);
                            messageAdapter.notifyItemInserted(messages.size() - 1);
                            scrollToBottom();

                        } catch (Exception e) {
                            showErrorMessage("Error parsing response");
                        }
                    } else if (res.code == 401) {
                        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else if (res.code == 429) {
                        try {
                            JSONObject errorRes = new JSONObject(res.body);
                            String friendlyMsg = errorRes.optString("answer",
                                "AI is temporarily busy due to high usage. Please try again in a few minutes!");
                            AIMessage botMsg = new AIMessage(friendlyMsg, AIMessage.MessageType.BOT, "AI Advisor");
                            messages.add(botMsg);
                            messageAdapter.notifyItemInserted(messages.size() - 1);
                            scrollToBottom();
                        } catch (Exception e) {
                            showErrorMessage("AI is busy. Please try again in a few minutes!");
                        }
                    } else {
                        try {
                            JSONObject errorRes = new JSONObject(res.body);
                            showErrorMessage(errorRes.optString("message", "Error getting response"));
                        } catch (Exception e) {
                            showErrorMessage("Error: Code " + res.code);
                        }
                    }
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    updateSendButtonState();
                    showErrorMessage("Connection error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showSkillAssessmentDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_skill_assessment, null);
        EditText currentSkillsInput = dialogView.findViewById(R.id.current_skills_input);
        EditText targetSkillsInput = dialogView.findViewById(R.id.target_skills_input);
        EditText experienceInput = dialogView.findViewById(R.id.experience_input);

        builder.setTitle("Skill Assessment")
               .setMessage("Tell us about your skills to get personalized recommendations")
               .setView(dialogView)
               .setPositiveButton("Analyze", (d, w) -> {
                   String currentSkills = currentSkillsInput.getText().toString().trim();
                   String targetSkills = targetSkillsInput.getText().toString().trim();
                   String experience = experienceInput.getText().toString().trim();

                   if (currentSkills.isEmpty()) {
                       Toast.makeText(this, "Please enter your current skills", Toast.LENGTH_SHORT).show();
                       return;
                   }

                   performSkillAssessment(currentSkills, targetSkills, experience);
               })
               .setNegativeButton("Cancel", null)
               .show();
    }

    private void performSkillAssessment(String currentSkills, String targetSkills, String experience) {
        progressBar.setVisibility(View.VISIBLE);
        sendButton.setEnabled(false);
        updateSendButtonState();

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                
                // Parse comma-separated skills into array
                String[] skillsArray = currentSkills.split(",");
                org.json.JSONArray currentSkillsArray = new org.json.JSONArray();
                for (String skill : skillsArray) {
                    currentSkillsArray.put(skill.trim());
                }
                payload.put("currentSkills", currentSkillsArray);

                if (!targetSkills.isEmpty()) {
                    String[] targetArray = targetSkills.split(",");
                    org.json.JSONArray targetSkillsArray = new org.json.JSONArray();
                    for (String skill : targetArray) {
                        targetSkillsArray.put(skill.trim());
                    }
                    payload.put("targetSkills", targetSkillsArray);
                }

                if (!experience.isEmpty()) {
                    try {
                        payload.put("experience", Integer.parseInt(experience));
                    } catch (NumberFormatException e) {
                        // Skip if not a number
                    }
                }

                ApiClient.HttpResult res = ApiClient.post(
                    "/api/ai/skill-assessment",
                    authToken,
                    payload.toString()
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    updateSendButtonState();

                    if (res.code == 200) {
                        try {
                            JSONObject response = new JSONObject(res.body);
                            String assessment = response.optString("assessment", "");
                            
                            AIMessage assessmentMessage = new AIMessage(
                                assessment,
                                AIMessage.MessageType.BOT,
                                "Skill Assessment"
                            );
                            messages.add(assessmentMessage);
                            messageAdapter.notifyItemInserted(messages.size() - 1);
                            scrollToBottom();

                        } catch (Exception e) {
                            showErrorMessage("Error parsing assessment");
                        }
                    } else {
                        showErrorMessage("Could not generate assessment");
                    }
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    updateSendButtonState();
                    showErrorMessage("Connection error");
                });
            }
        }).start();
    }

    private void showJobRecommendationsDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_job_recommendations, null);
        EditText skillsInput = dialogView.findViewById(R.id.skills_input);
        EditText locationInput = dialogView.findViewById(R.id.location_input);
        EditText workTypeInput = dialogView.findViewById(R.id.work_type_input);

        builder.setTitle("Job Recommendations")
               .setMessage("Tell us about yourself to get job opportunities")
               .setView(dialogView)
               .setPositiveButton("Get Recommendations", (d, w) -> {
                   String skills = skillsInput.getText().toString().trim();
                   String location = locationInput.getText().toString().trim();
                   String workType = workTypeInput.getText().toString().trim();

                   if (skills.isEmpty()) {
                       Toast.makeText(this, "Please enter your skills", Toast.LENGTH_SHORT).show();
                       return;
                   }

                   performJobRecommendations(skills, location, workType);
               })
               .setNegativeButton("Cancel", null)
               .show();
    }

    private void performJobRecommendations(String skills, String location, String workType) {
        progressBar.setVisibility(View.VISIBLE);
        sendButton.setEnabled(false);
        updateSendButtonState();

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                
                // Parse skills
                String[] skillsArray = skills.split(",");
                org.json.JSONArray skillsJsonArray = new org.json.JSONArray();
                for (String skill : skillsArray) {
                    skillsJsonArray.put(skill.trim());
                }
                payload.put("skills", skillsJsonArray);

                if (!location.isEmpty()) {
                    payload.put("location", location);
                }
                if (!workType.isEmpty()) {
                    payload.put("workType", workType);
                }

                ApiClient.HttpResult res = ApiClient.post(
                    "/api/ai/job-recommendations",
                    authToken,
                    payload.toString()
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    updateSendButtonState();

                    if (res.code == 200) {
                        try {
                            JSONObject response = new JSONObject(res.body);
                            String recommendations = response.optString("recommendations", "");
                            
                            AIMessage recMessage = new AIMessage(
                                recommendations,
                                AIMessage.MessageType.BOT,
                                "Job Recommendations"
                            );
                            messages.add(recMessage);
                            messageAdapter.notifyItemInserted(messages.size() - 1);
                            scrollToBottom();

                        } catch (Exception e) {
                            showErrorMessage("Error parsing recommendations");
                        }
                    } else {
                        showErrorMessage("Could not generate recommendations");
                    }
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    updateSendButtonState();
                    showErrorMessage("Connection error");
                });
            }
        }).start();
    }

    private void showErrorMessage(String error) {
        AIMessage errorMessage = new AIMessage(
            "❌ " + error,
            AIMessage.MessageType.ERROR,
            "Error"
        );
        messages.add(errorMessage);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
    }

    private void updateSendButtonState() {
        boolean hasText = questionEditText != null
                && questionEditText.getText() != null
                && questionEditText.getText().toString().trim().length() > 0;
        boolean isReady = hasText && progressBar.getVisibility() != View.VISIBLE;
        sendButton.setEnabled(isReady);
        sendButton.setAlpha(isReady ? 1f : 0.55f);
        sendButton.setScaleX(isReady ? 1f : 0.96f);
        sendButton.setScaleY(isReady ? 1f : 0.96f);
    }

    private void scrollToBottom() {
        if (!messages.isEmpty()) {
            messageRecyclerView.smoothScrollToPosition(messages.size() - 1);
        }
    }
}
