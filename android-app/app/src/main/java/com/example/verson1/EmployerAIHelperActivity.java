package com.example.verson1;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class EmployerAIHelperActivity extends AppCompatActivity {

    private RecyclerView messageRecyclerView;
    private EditText questionEditText;
    private ImageButton sendButton;
    private ProgressBar progressBar;

    private AIMessageAdapter messageAdapter;
    private List<AIMessage> messages;
    private String authToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employer_ai_helper);

        if (!SessionManager.ensureEmployer(this)) {
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
        setupQuickQuestions();
        addWelcomeMessage();
    }

    private void initializeViews() {
        messageRecyclerView = findViewById(R.id.message_recycler);
        questionEditText = findViewById(R.id.question_input);
        sendButton = findViewById(R.id.btn_send);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        LogoutUiHelper.attachSignOutMenu(this, toolbar);
    }

    private void setupRecyclerView() {
        messages = new ArrayList<>();
        messageAdapter = new AIMessageAdapter(messages);
        messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageRecyclerView.setAdapter(messageAdapter);
    }

    private void setupClickListeners() {
        sendButton.setOnClickListener(v -> sendQuestion());
        questionEditText.setOnEditorActionListener((v, actionId, event) -> {
            sendQuestion();
            return true;
        });
    }

    private void setupQuickQuestions() {
        int[] chipIds = {
            R.id.chip_wage, R.id.chip_description,
            R.id.chip_retain, R.id.chip_schedule
        };
        String[] questions = {
            "What's a fair hourly wage for a warehouse shift worker?",
            "Help me write a good job description for a delivery driver shift",
            "How can I retain my best shift workers and reduce turnover?",
            "What's the best way to handle last-minute shift cancellations?"
        };

        for (int i = 0; i < chipIds.length; i++) {
            Chip chip = findViewById(chipIds[i]);
            if (chip != null) {
                final String q = questions[i];
                chip.setOnClickListener(v -> {
                    questionEditText.setText(q);
                    sendQuestion();
                });
            }
        }
    }

    private void addWelcomeMessage() {
        AIMessage welcome = new AIMessage(
            "Hi! I'm your AI Business Helper. I can assist you with:\n\n" +
            "- Writing job/shift descriptions\n" +
            "- Setting fair wages\n" +
            "- Managing your team\n" +
            "- Scheduling tips\n" +
            "- Hiring best practices\n\n" +
            "Ask me anything or tap a quick question below!",
            AIMessage.MessageType.BOT,
            "AI Helper"
        );
        messages.add(welcome);
        messageAdapter.notifyItemInserted(0);
    }

    private void sendQuestion() {
        String question = questionEditText.getText().toString().trim();
        if (question.isEmpty()) {
            Toast.makeText(this, "Please enter a question", Toast.LENGTH_SHORT).show();
            return;
        }

        AIMessage userMessage = new AIMessage(question, AIMessage.MessageType.USER, "You");
        messages.add(userMessage);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        questionEditText.setText("");
        sendButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        askEmployerHelper(question);
    }

    private void askEmployerHelper(String question) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("question", question);

                ApiClient.HttpResult res = ApiClient.post(
                    "/api/ai/employer-helper",
                    authToken,
                    payload.toString()
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    sendButton.setEnabled(true);

                    if (res.code == 200) {
                        try {
                            JSONObject response = new JSONObject(res.body);
                            String answer = response.optString("answer",
                                "Sorry, I couldn't generate a response. Please try again.");
                            AIMessage botMessage = new AIMessage(answer, AIMessage.MessageType.BOT, "AI Helper");
                            messages.add(botMessage);
                            messageAdapter.notifyItemInserted(messages.size() - 1);
                            scrollToBottom();
                        } catch (Exception e) {
                            showError("Error parsing response");
                        }
                    } else if (res.code == 401) {
                        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                        SessionManager.logoutToLogin(this);
                    } else if (res.code == 429) {
                        try {
                            JSONObject errorRes = new JSONObject(res.body);
                            String friendlyMsg = errorRes.optString("answer",
                                "AI is temporarily busy due to high usage. Please try again in a few minutes!");
                            AIMessage botMsg = new AIMessage(friendlyMsg, AIMessage.MessageType.BOT, "AI Helper");
                            messages.add(botMsg);
                            messageAdapter.notifyItemInserted(messages.size() - 1);
                            scrollToBottom();
                        } catch (Exception e) {
                            showError("AI is busy. Please try again shortly!");
                        }
                    } else {
                        try {
                            JSONObject errorRes = new JSONObject(res.body);
                            showError(errorRes.optString("message", "Error getting response"));
                        } catch (Exception e) {
                            showError("Error: Code " + res.code);
                        }
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    sendButton.setEnabled(true);
                    showError("Connection error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showError(String error) {
        AIMessage errorMessage = new AIMessage(error, AIMessage.MessageType.ERROR, "Error");
        messages.add(errorMessage);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
    }

    private void scrollToBottom() {
        messageRecyclerView.scrollToPosition(messages.size() - 1);
    }
}
