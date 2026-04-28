package com.mindtrace.ai.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mindtrace.ai.ui.model.HomeScreenState;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Advanced AI Coach Engine using Groq API (OpenAI-compatible).
 * 
 * <p>Manages the full conversation lifecycle including context injection,
 * check-in data analysis, and action widget triggering.</p>
 */
public class CoachChatEngine {

    private static final String TAG = "CoachChatEngine";
    private static final String API_KEY = com.mindtrace.ai.BuildConfig.GROQ_API_KEY;
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;
    private final Gson gson;
    private HomeScreenState currentState;
    private QuestionnaireResponse latestCheckIn;
    private JsonArray chatHistory; // OpenAI-format messages array
    private int userMessageCount = 0;

    public interface ChatCallback {
        void onResponse(String textResponse, String actionWidgetType);
    }

    public CoachChatEngine() {
        client = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .build();
        gson = new Gson();
        chatHistory = new JsonArray();
    }

    public void updateContext(HomeScreenState state) {
        this.currentState = state;
    }

    public void updateCheckIn(QuestionnaireResponse checkIn) {
        this.latestCheckIn = checkIn;
    }

    public void sendMessage(String userMessage, ChatCallback callback) {
        if (API_KEY == null || API_KEY.isEmpty() || API_KEY.equals("YOUR_API_KEY_HERE")) {
            handler.postDelayed(() -> callback.onResponse("Coach is not configured yet. Please add your Groq API key.", null), 1000);
            return;
        }

        userMessageCount++;
        final boolean isFirstMessage = (userMessageCount == 1);

        // Add user message to history (OpenAI format: {"role": "user", "content": "..."})
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        chatHistory.add(userMsg);

        // Build full messages array: system + chat history
        JsonArray messages = new JsonArray();

        // System message first
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", buildSystemPrompt());
        messages.add(systemMsg);

        // Then all conversation history
        messages.addAll(chatHistory);

        // Build payload
        JsonObject payload = new JsonObject();
        payload.addProperty("model", MODEL);
        payload.add("messages", messages);
        payload.addProperty("temperature", 0.7);
        payload.addProperty("max_tokens", 1024);

        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API Call failed", e);
                chatHistory.remove(chatHistory.size() - 1); // remove failed user message
                handler.post(() -> callback.onResponse("Network error. Please check your connection and try again.", null));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Groq API Error: " + response.code() + " " + err);
                    chatHistory.remove(chatHistory.size() - 1);

                    String userMsg;
                    if (response.code() == 429) {
                        userMsg = "I'm thinking too fast! Please wait a moment and try again.";
                    } else if (response.code() == 401) {
                        userMsg = "Authentication failed. Please check your API key.";
                    } else {
                        userMsg = "I encountered a server error. (" + response.code() + ")";
                    }
                    handler.post(() -> callback.onResponse(userMsg, null));
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

                    // Parse OpenAI-compatible response: choices[0].message.content
                    String aiText = jsonObject.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                    // Save AI response to history (OpenAI format)
                    JsonObject aiMsg = new JsonObject();
                    aiMsg.addProperty("role", "assistant");
                    aiMsg.addProperty("content", aiText);
                    chatHistory.add(aiMsg);

                    // Parse widgets out of text
                    parseAndReturnResponse(aiText, isFirstMessage, callback);

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing Groq response", e);
                    handler.post(() -> callback.onResponse("I didn't quite understand the server response.", null));
                }
            }
        });
    }

    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the MindTrace AI Coach — an advanced, intelligent AI assistant embedded inside the MindTrace mental wellness app.\n\n");

        prompt.append("=== CORE RULES ===\n");
        prompt.append("1. ALWAYS answer the user's EXACT question first. Read their message carefully and respond DIRECTLY to what they asked.\n");
        prompt.append("2. Do NOT give generic wellness advice unless the user specifically asks for it.\n");
        prompt.append("3. If the user asks a factual question (e.g., 'what is anxiety?', 'how does sleep affect mood?'), give a precise, accurate, educational answer.\n");
        prompt.append("4. If the user shares how they feel, respond with empathy AND specific, actionable suggestions tailored to their exact words.\n");
        prompt.append("5. If the user asks about their data (screen time, risk score, mood), reference the context data provided below.\n");
        prompt.append("6. Keep responses concise — 2-5 sentences for simple questions, up to 8 sentences for complex topics.\n");
        prompt.append("7. Never make up data you don't have. If you don't know something, say so honestly.\n");
        prompt.append("8. Use markdown formatting (bold, bullets, line breaks) to make longer responses readable.\n\n");

        prompt.append("=== YOUR CAPABILITIES ===\n");
        prompt.append("- Mental health education (anxiety, depression, stress, sleep science, focus, habits)\n");
        prompt.append("- Digital wellness coaching (screen time management, app addiction, social media detox)\n");
        prompt.append("- Personalized advice based on the user's check-in data and usage patterns\n");
        prompt.append("- Guided exercises (breathing, grounding, journaling prompts)\n");
        prompt.append("- General knowledge and conversation — you can discuss ANY topic the user brings up\n\n");

        prompt.append("=== TONE ===\n");
        prompt.append("- Warm but direct. Not robotic, not overly flowery.\n");
        prompt.append("- Like a smart, caring friend who gives straight answers.\n");
        prompt.append("- Match the user's energy: if they're casual, be casual. If they're distressed, be gentle and supportive.\n\n");

        prompt.append("=== WHEN USER SUBMITS A DAILY CHECK-IN ===\n");
        prompt.append("When the user says they completed their check-in:\n");
        prompt.append("1. Identify the TOP 2-3 specific patterns or concerns from their answers.\n");
        prompt.append("2. Give concrete, personalized guidance (not generic advice).\n");
        prompt.append("3. If their 'What's on my mind' text is present, address it DIRECTLY.\n\n");

        prompt.append("=== ACTION WIDGETS ===\n");
        prompt.append("You can trigger interactive UI widgets by including ONE tag at the VERY END of your response (ONLY when relevant):\n");
        prompt.append("[WIDGET:ACTION_BREATHING] - Only when user mentions stress, anxiety, or panic.\n");
        prompt.append("[WIDGET:ACTION_GROUNDING] - Only for dissociation, numbness, or mental fog.\n");
        prompt.append("[WIDGET:ACTION_LOCKDOWN] - Only for severe distraction or social media loops.\n");
        prompt.append("[WIDGET:ACTION_FOCUS_MODE] - Only when user wants to start focused work.\n");
        prompt.append("[WIDGET:ACTION_CHECK_IN] - Only to suggest a fresh mood assessment.\n");
        prompt.append("[WIDGET:ACTION_SUPPORT_OPTIONS] - Only for crisis situations.\n");
        prompt.append("Do NOT add widgets to every response — only when genuinely appropriate.\n\n");

        if (currentState != null) {
            prompt.append("--- USER'S CURRENT APP STATE (use only when relevant to their question) ---\n");
            prompt.append("Risk Index: ").append(currentState.riskIndex).append("/100\n");
            prompt.append("Risk Summary: ").append(currentState.riskSummary != null ? currentState.riskSummary : "Stable").append("\n");
            prompt.append("Checked In Today: ").append(currentState.hasCheckedInToday ? "Yes" : "No").append("\n");
            prompt.append("Exercised Today: ").append(currentState.hasExerciseToday ? "Yes" : "No").append("\n");
        }

        if (latestCheckIn != null) {
            prompt.append("\n--- USER'S LATEST CHECK-IN DATA (reference only when they ask about their state) ---\n");
            prompt.append("Mood: ").append(latestCheckIn.mood != null ? latestCheckIn.mood : "Unknown").append("\n");
            prompt.append("Stress: ").append(latestCheckIn.stressLevel).append("/5, ");
            prompt.append("Anxiety: ").append(latestCheckIn.anxietyLevel).append("/5, ");
            prompt.append("Loneliness: ").append(latestCheckIn.lonelinessLevel).append("/5\n");
            prompt.append("Motivation: ").append(latestCheckIn.motivationLevel).append("/5, ");
            prompt.append("Focus: ").append(latestCheckIn.focusLevel != null ? latestCheckIn.focusLevel : "Unknown").append(", ");
            prompt.append("Energy: ").append(latestCheckIn.energyLevel != null ? latestCheckIn.energyLevel : "Unknown").append("\n");
            prompt.append("Sleep: ").append(latestCheckIn.sleepHours).append("h, quality ").append(latestCheckIn.sleepQuality).append("/5\n");
            prompt.append("Self-Worth: ").append(latestCheckIn.selfWorthScore).append("/5, ");
            prompt.append("Purpose: ").append(latestCheckIn.purposeScore).append("/5, ");
            prompt.append("Hope: ").append(latestCheckIn.hopeLevel).append("/5\n");
            if (latestCheckIn.currentConcern != null && !latestCheckIn.currentConcern.isEmpty()) {
                prompt.append("User's concern: ").append(latestCheckIn.currentConcern).append("\n");
            }
        }

        return prompt.toString();
    }

    private void parseAndReturnResponse(String fullText, boolean isFirstMessage, ChatCallback callback) {
        String actionWidget = null;
        String cleanText = fullText;

        String[] widgets = {
                "ACTION_BREATHING", "ACTION_GROUNDING", "ACTION_LOCKDOWN", 
                "ACTION_FOCUS_MODE", "ACTION_CHECK_IN", "ACTION_SUPPORT_OPTIONS"
        };

        for (String widget : widgets) {
            String tag = "[WIDGET:" + widget + "]";
            if (cleanText.contains(tag)) {
                actionWidget = widget;
                cleanText = cleanText.replace(tag, "").trim();
                break; // Only support one widget
            }
        }

        // On the very first user message, ALWAYS show the check-in button
        if (isFirstMessage && actionWidget == null) {
            actionWidget = "ACTION_CHECK_IN";
        }

        final String finalText = cleanText;
        final String finalWidget = actionWidget;

        handler.post(() -> callback.onResponse(finalText, finalWidget));
    }
}
