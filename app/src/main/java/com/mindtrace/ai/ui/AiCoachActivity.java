package com.mindtrace.ai.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.CoachChatEngine;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.viewmodel.DashboardViewModel;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;

public class AiCoachActivity extends AppCompatActivity {

    private RecyclerView rvChat;
    private EditText etInput;
    private MaterialButton btnSend;
    private TextView tvStatus;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());
    private CoachChatEngine chatEngine = new CoachChatEngine();
    private DashboardViewModel viewModel;

    private boolean waitingForCheckIn = false;
    private boolean waitingForExercise = false;
    private String pendingExerciseName = "";
    private Markwon markwon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        markwon = Markwon.create(this);
        setContentView(R.layout.activity_ai_coach);

        rvChat = findViewById(R.id.rv_coach_chat);
        etInput = findViewById(R.id.et_coach_input);
        btnSend = findViewById(R.id.btn_coach_send);
        tvStatus = findViewById(R.id.tv_coach_status);
        ImageButton btnBack = findViewById(R.id.btn_coach_back);

        btnBack.setOnClickListener(v -> finish());

        chatAdapter = new ChatAdapter(messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);
        rvChat.setAdapter(chatAdapter);

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
            }
        });

        // Phase 2: Context Injection
        tvStatus.setText("Gathering vital context...");
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        
        viewModel.getHomeScreenState().observe(this, state -> {
            if (state == null || state.isLoading) return;
            
            chatEngine.updateContext(state);
            
            if (messages.isEmpty()) {
                tvStatus.setText("Coach is online");
                
                // Build a dynamic greeting based on the injected context
                String dynamicGreeting = "Hi Alex! I've reviewed your current state. ";
                if (state.riskIndex > 70) {
                    dynamicGreeting += "I noticed your risk index is high (" + state.riskIndex + "). " + state.riskSummary + " Let's talk about what's going on.";
                } else {
                    dynamicGreeting += "Your momentum is looking stable today. " + state.riskSummary + " How are you feeling?";
                }
                
                messages.add(new ChatMessage(dynamicGreeting, false, null));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                rvChat.scrollToPosition(messages.size() - 1);
            }
        });

        viewModel.getStateHistory().observe(this, responses -> {
            if (responses != null && !responses.isEmpty()) {
                chatEngine.updateCheckIn(responses.get(0));
            }
        });

        setupQuickSuggestions();
    }

    private void setupQuickSuggestions() {
        LinearLayout layoutSuggestions = findViewById(R.id.layout_coach_suggestions);
        if (layoutSuggestions == null) return;
        
        String[] suggestions = {
            "Feeling anxious",
            "Analyze my day",
            "I'm stuck in a loop",
            "Breathing exercise"
        };
        
        for (String suggestion : suggestions) {
            MaterialButton chip = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(suggestion);
            chip.setTextColor(getResources().getColor(R.color.white));
            chip.setStrokeColorResource(android.R.color.transparent);
            chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1A2235")));
            chip.setCornerRadius(32);
            chip.setAllCaps(false);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 16, 0);
            chip.setLayoutParams(params);
            
            chip.setOnClickListener(v -> {
                etInput.setText(suggestion);
                sendMessage(suggestion);
            });
            
            layoutSuggestions.addView(chip);
        }
    }

    // ─── Called when user returns from DailyCheckInActivity ─────────────
    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForCheckIn) {
            waitingForCheckIn = false;
            // Delay slightly to let Room DB finish writing the check-in
            handler.postDelayed(this::processCheckInReturn, 2000);
        } else if (waitingForExercise) {
            waitingForExercise = false;
            processExerciseReturn();
        }
    }

    private void processExerciseReturn() {
        // Show a system message indicating the exercise was completed
        messages.add(new ChatMessage("✅ I've completed the " + pendingExerciseName + " exercise.", true, null));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);

        tvStatus.setText("Coach is analyzing...");

        // Build a system summary
        String summary = "System Context: The user just successfully completed the " + pendingExerciseName + " exercise. Praise them briefly and ask how they feel now.";
        chatEngine.sendMessage(summary, (textResponse, actionWidgetType) -> {
            tvStatus.setText("Coach is online");
            messages.add(new ChatMessage(textResponse, false, actionWidgetType));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);
        });
    }

    /**
     * After the user completes the Daily Check-In and returns here,
     * automatically feed ALL their answers into the AI Coach so it can
     * generate a deeply personalized response.
     */
    private void processCheckInReturn() {
        // Refresh latest check-in data from the LiveData snapshot
        List<QuestionnaireResponse> responses = viewModel.getStateHistory().getValue();
        if (responses != null && !responses.isEmpty()) {
            QuestionnaireResponse latest = responses.get(0);
            chatEngine.updateCheckIn(latest);

            // Show a system message indicating the check-in was submitted
            messages.add(new ChatMessage("✅ I've completed my daily check-in.", true, null));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);

            tvStatus.setText("Coach is analyzing your check-in...");

            // Build a rich summary of all check-in answers and send to AI
            String checkInSummary = buildCheckInSummary(latest);
            chatEngine.sendMessage(checkInSummary, (textResponse, actionWidgetType) -> {
                tvStatus.setText("Coach is online");
                messages.add(new ChatMessage(textResponse, false, actionWidgetType));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                rvChat.scrollToPosition(messages.size() - 1);
            });
        } else {
            // Fallback if data not available yet
            tvStatus.setText("Coach is online");
            messages.add(new ChatMessage("Welcome back! It looks like your check-in is still being processed. Give me a moment...", false, null));
            chatAdapter.notifyItemInserted(messages.size() - 1);
        }
    }

    private String buildCheckInSummary(QuestionnaireResponse r) {
        StringBuilder sb = new StringBuilder();
        sb.append("I just completed my daily check-in. Here are all my answers:\n");
        sb.append("• Mood: ").append(r.mood != null ? r.mood : "Neutral").append("\n");
        sb.append("• Stress: ").append(r.stressLevel).append("/5\n");
        sb.append("• Anxiety: ").append(r.anxietyLevel).append("/5\n");
        sb.append("• Loneliness: ").append(r.lonelinessLevel).append("/5\n");
        sb.append("• Motivation: ").append(r.motivationLevel).append("/5\n");
        sb.append("• Sleep: ").append(r.sleepHours).append(" hours (quality ").append(r.sleepQuality).append("/5)\n");
        sb.append("• Focus: ").append(r.focusLevel != null ? r.focusLevel : "Medium").append("\n");
        sb.append("• Energy: ").append(r.energyLevel != null ? r.energyLevel : "Medium").append("\n");
        sb.append("• Urge to scroll: ").append(r.urgeToScrollLevel).append("/5\n");
        if (r.biggestDistraction != null && !r.biggestDistraction.isEmpty()) {
            sb.append("• Biggest distraction: ").append(r.biggestDistraction).append("\n");
        }
        sb.append("• Self-worth: ").append(r.selfWorthScore).append("/5\n");
        sb.append("• Purpose: ").append(r.purposeScore).append("/5\n");
        sb.append("• Hope: ").append(r.hopeLevel).append("/5\n");
        sb.append("• Felt like crying: ").append(r.feltLikeCrying ? "Yes" : "No").append("\n");
        sb.append("• Wanted to withdraw: ").append(r.wantedToWithdraw ? "Yes" : "No").append("\n");
        sb.append("• Exercised today: ").append(r.exercisedToday ? "Yes" : "No").append("\n");
        if (r.currentConcern != null && !r.currentConcern.isEmpty()) {
            sb.append("• What's on my mind right now: ").append(r.currentConcern).append("\n");
        }
        if (r.gratitudeText != null && !r.gratitudeText.isEmpty()) {
            sb.append("• Gratitude: ").append(r.gratitudeText).append("\n");
        }
        sb.append("\nPlease analyze everything deeply and give me personalized, actionable guidance based on my exact answers. Don't just summarize — give me real solutions.");
        return sb.toString();
    }

    /**
     * Called from the adapter when user taps the "Do a Quick Check-in" button.
     */
    void launchCheckIn() {
        waitingForCheckIn = true;
        startActivity(new Intent(this, DailyCheckInActivity.class));
    }

    void launchExercise(String name, Class<?> activityClass) {
        waitingForExercise = true;
        pendingExerciseName = name;
        startActivity(new Intent(this, activityClass));
    }

    private void sendMessage(String text) {
        messages.add(new ChatMessage(text, true, null));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);
        etInput.setText("");

        // Show typing indicator
        tvStatus.setText("Coach is typing...");
        ChatMessage typingMessage = new ChatMessage("...", false, null);
        typingMessage.isTyping = true;
        messages.add(typingMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);

        // Pass message to engine
        chatEngine.sendMessage(text, (textResponse, actionWidgetType) -> {
            tvStatus.setText("Coach is online");
            // Remove typing indicator
            if (!messages.isEmpty() && messages.get(messages.size() - 1).isTyping) {
                messages.remove(messages.size() - 1);
                chatAdapter.notifyItemRemoved(messages.size());
            }
            messages.add(new ChatMessage(textResponse, false, actionWidgetType));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);
        });
    }

    private static class ChatMessage {
        String text;
        boolean isUser;
        String actionWidgetType;
        boolean isTyping = false;

        ChatMessage(String text, boolean isUser, String actionWidgetType) {
            this.text = text;
            this.isUser = isUser;
            this.actionWidgetType = actionWidgetType;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_AI = 0;
        private static final int TYPE_USER = 1;

        private List<ChatMessage> messageList;

        ChatAdapter(List<ChatMessage> messageList) {
            this.messageList = messageList;
        }

        @Override
        public int getItemViewType(int position) {
            return messageList.get(position).isUser ? TYPE_USER : TYPE_AI;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_USER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_coach_message_user, parent, false);
                return new UserMessageViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_coach_message_ai, parent, false);
                return new AiMessageViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage message = messageList.get(position);
            if (holder instanceof UserMessageViewHolder) {
                ((UserMessageViewHolder) holder).tvMessage.setText(message.text);
            } else if (holder instanceof AiMessageViewHolder) {
                AiMessageViewHolder aiHolder = (AiMessageViewHolder) holder;
                
                if (message.isTyping) {
                    aiHolder.tvMessage.setText("...");
                    aiHolder.tvMessage.setTextSize(24f);
                } else {
                    aiHolder.tvMessage.setTextSize(15f);
                    markwon.setMarkdown(aiHolder.tvMessage, message.text);
                }
                
                // Clear previous widgets if any
                aiHolder.containerWidget.removeAllViews();
                
                // Render Action Widget if present
                if (message.actionWidgetType != null && !message.actionWidgetType.isEmpty()) {
                    aiHolder.containerWidget.setVisibility(View.VISIBLE);
                    
                    // Inflate widget button dynamically
                    MaterialButton actionBtn = new MaterialButton(aiHolder.itemView.getContext(), null, com.google.android.material.R.attr.materialButtonStyle);
                    actionBtn.setCornerRadius(32);
                    
                    switch (message.actionWidgetType) {
                        case "ACTION_BREATHING":
                            actionBtn.setText("Start 2-min Breathing");
                            actionBtn.setOnClickListener(v -> launchExercise("Breathing", BreathingExerciseActivity.class));
                            break;
                        case "ACTION_CHECK_IN":
                            actionBtn.setText("\uD83D\uDCCB  Do a Quick Check-in");
                            actionBtn.setOnClickListener(v -> launchCheckIn());
                            break;
                        case "ACTION_GROUNDING":
                            actionBtn.setText("Start Grounding");
                            actionBtn.setOnClickListener(v -> launchExercise("Grounding", GroundingExerciseActivity.class));
                            break;
                        case "ACTION_SUPPORT_OPTIONS":
                            actionBtn.setText("Seek Support");
                            // Fallback to overview logic or launch SupportFragment equivalent
                            break;
                        case "ACTION_LOCKDOWN":
                            actionBtn.setText("\uD83D\uDD12  Start App Lockdown");
                            actionBtn.setOnClickListener(v -> {
                                // Since we don't have a standalone lockdown activity yet, route to Focus/Lockdown logic
                                Toast.makeText(AiCoachActivity.this, "Lockdown Mode starting...", Toast.LENGTH_SHORT).show();
                            });
                            break;
                        case "ACTION_FOCUS_MODE":
                            actionBtn.setText("\uD83C\uDFAF  Enter Focus Mode");
                            actionBtn.setOnClickListener(v -> {
                                Toast.makeText(AiCoachActivity.this, "Focus Mode active.", Toast.LENGTH_SHORT).show();
                            });
                            break;
                        default:
                            actionBtn.setText("Take Action");
                    }
                    aiHolder.containerWidget.addView(actionBtn);
                } else {
                    aiHolder.containerWidget.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return messageList.size();
        }

        class UserMessageViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage;

            UserMessageViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.tv_user_message_text);
            }
        }

        class AiMessageViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage;
            FrameLayout containerWidget;

            AiMessageViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.tv_coach_message_text);
                containerWidget = itemView.findViewById(R.id.container_coach_widget);
            }
        }
    }
}
