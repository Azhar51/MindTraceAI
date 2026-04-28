package com.mindtrace.ai.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import com.mindtrace.ai.databinding.ActivityAiCoachBinding;
import com.mindtrace.ai.viewmodel.DashboardViewModel;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;

/**
 * AI Coach Activity — context-aware conversational coaching.
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class AiCoachActivity extends AppCompatActivity {

    private ActivityAiCoachBinding binding;
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
        binding = ActivityAiCoachBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnCoachBack.setOnClickListener(v -> finish());

        chatAdapter = new ChatAdapter(messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvCoachChat.setLayoutManager(layoutManager);
        binding.rvCoachChat.setAdapter(chatAdapter);

        binding.btnCoachSend.setOnClickListener(v -> {
            String text = binding.etCoachInput.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
            }
        });

        // Show coach as online immediately — no waiting
        binding.tvCoachStatus.setText("Coach is online");

        // Load user's real name from the database for the greeting
        String firstName = resolveUserFirstName();
        String greeting = firstName.isEmpty()
                ? "Hey! I'm your MindTrace AI Coach. How can I help you today?"
                : "Hey " + firstName + "! I'm your MindTrace AI Coach. How can I help you today?";

        messages.add(new ChatMessage(greeting, false, null));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        binding.rvCoachChat.scrollToPosition(messages.size() - 1);

        // Inject context silently in the background — don't block the UI
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        viewModel.getHomeScreenState().observe(this, state -> {
            if (state == null || state.isLoading) return;
            chatEngine.updateContext(state);
        });

        viewModel.getStateHistory().observe(this, responses -> {
            if (responses != null && !responses.isEmpty()) {
                chatEngine.updateCheckIn(responses.get(0));
            }
        });

        setupQuickSuggestions();
    }

    /**
     * Reads the user's first name directly from the onboarding_profile table
     * via a synchronous Room query. This is fast because the table has exactly
     * one row and is cached by Room after the first read.
     */
    private String resolveUserFirstName() {
        try {
            com.mindtrace.ai.database.AppDatabase db =
                    com.mindtrace.ai.database.AppDatabase.getInstance(this);
            com.mindtrace.ai.database.entity.OnboardingProfile profile =
                    db.onboardingProfileDao().getProfileSync();
            if (profile != null && profile.name != null && !profile.name.trim().isEmpty()) {
                String name = profile.name.trim();
                int space = name.indexOf(' ');
                return space > 0 ? name.substring(0, space) : name;
            }
        } catch (Exception e) {
            android.util.Log.w("AiCoachActivity", "Could not resolve user name", e);
        }
        return "";
    }

    private void setupQuickSuggestions() {
        if (binding.layoutCoachSuggestions == null) return;

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
                binding.etCoachInput.setText(suggestion);
                sendMessage(suggestion);
            });

            binding.layoutCoachSuggestions.addView(chip);
        }
    }

    // ─── Called when user returns from DailyCheckInActivity ─────────────
    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForCheckIn) {
            waitingForCheckIn = false;
            handler.postDelayed(this::processCheckInReturn, 2000);
        } else if (waitingForExercise) {
            waitingForExercise = false;
            processExerciseReturn();
        }
    }

    private void processExerciseReturn() {
        messages.add(new ChatMessage("✅ I've completed the " + pendingExerciseName + " exercise.", true, null));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        binding.rvCoachChat.scrollToPosition(messages.size() - 1);

        binding.tvCoachStatus.setText("Coach is analyzing...");

        String summary = "System Context: The user just successfully completed the " + pendingExerciseName + " exercise. Praise them briefly and ask how they feel now.";
        chatEngine.sendMessage(summary, (textResponse, actionWidgetType) -> {
            binding.tvCoachStatus.setText("Coach is online");
            messages.add(new ChatMessage(textResponse, false, actionWidgetType));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            binding.rvCoachChat.scrollToPosition(messages.size() - 1);
        });
    }

    /**
     * After the user completes the Daily Check-In and returns here,
     * automatically feed ALL their answers into the AI Coach so it can
     * generate a deeply personalized response.
     */
    private void processCheckInReturn() {
        List<QuestionnaireResponse> responses = viewModel.getStateHistory().getValue();
        if (responses != null && !responses.isEmpty()) {
            QuestionnaireResponse latest = responses.get(0);
            chatEngine.updateCheckIn(latest);

            messages.add(new ChatMessage("✅ I've completed my daily check-in.", true, null));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            binding.rvCoachChat.scrollToPosition(messages.size() - 1);

            binding.tvCoachStatus.setText("Coach is analyzing your check-in...");

            String checkInSummary = buildCheckInSummary(latest);
            chatEngine.sendMessage(checkInSummary, (textResponse, actionWidgetType) -> {
                binding.tvCoachStatus.setText("Coach is online");
                messages.add(new ChatMessage(textResponse, false, actionWidgetType));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                binding.rvCoachChat.scrollToPosition(messages.size() - 1);
            });
        } else {
            binding.tvCoachStatus.setText("Coach is online");
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
        binding.rvCoachChat.scrollToPosition(messages.size() - 1);
        binding.etCoachInput.setText("");

        binding.tvCoachStatus.setText("Coach is typing...");
        ChatMessage typingMessage = new ChatMessage("...", false, null);
        typingMessage.isTyping = true;
        messages.add(typingMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        binding.rvCoachChat.scrollToPosition(messages.size() - 1);

        chatEngine.sendMessage(text, (textResponse, actionWidgetType) -> {
            binding.tvCoachStatus.setText("Coach is online");
            if (!messages.isEmpty() && messages.get(messages.size() - 1).isTyping) {
                messages.remove(messages.size() - 1);
                chatAdapter.notifyItemRemoved(messages.size());
            }
            messages.add(new ChatMessage(textResponse, false, actionWidgetType));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            binding.rvCoachChat.scrollToPosition(messages.size() - 1);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
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

                aiHolder.containerWidget.removeAllViews();

                if (message.actionWidgetType != null && !message.actionWidgetType.isEmpty()) {
                    aiHolder.containerWidget.setVisibility(View.VISIBLE);

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
                            break;
                        case "ACTION_LOCKDOWN":
                            actionBtn.setText("\uD83D\uDD12  Start App Lockdown");
                            actionBtn.setOnClickListener(v -> {
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
