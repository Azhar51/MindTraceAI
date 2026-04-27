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
 * Advanced AI Coach Engine using Google Gemini API.
 * 
 * <p>Manages the full conversation lifecycle including context injection,
 * check-in data analysis, and action widget triggering.</p>
 */
public class CoachChatEngine {

    private static final String TAG = "CoachChatEngine";
    private static final String API_KEY = com.mindtrace.ai.BuildConfig.GEMINI_API_KEY; 
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;
    private final Gson gson;
    private HomeScreenState currentState;
    private QuestionnaireResponse latestCheckIn;
    private JsonArray chatHistory;
    private int userMessageCount = 0;

    public interface ChatCallback {
        void onResponse(String textResponse, String actionWidgetType);
    }

    public CoachChatEngine() {
        client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
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
        if (API_KEY.equals("YOUR_API_KEY_HERE") || API_KEY.isEmpty()) {
            handler.postDelayed(() -> callback.onResponse("I am not connected to the internet yet! Please paste your Gemini API key into `CoachChatEngine.java`.", null), 1000);
            return;
        }

        userMessageCount++;
        final boolean isFirstMessage = (userMessageCount == 1);

        // Add user message to history
        JsonObject userMsgObj = new JsonObject();
        userMsgObj.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userTextObj = new JsonObject();
        userTextObj.addProperty("text", userMessage);
        userParts.add(userTextObj);
        userMsgObj.add("parts", userParts);
        
        chatHistory.add(userMsgObj);

        // Build payload
        JsonObject payload = new JsonObject();
        
        // Add System Instructions
        JsonObject systemInstruction = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysTextObj = new JsonObject();
        sysTextObj.addProperty("text", buildSystemPrompt());
        sysParts.add(sysTextObj);
        systemInstruction.add("parts", sysParts);
        payload.add("systemInstruction", systemInstruction);

        // Add Chat History
        payload.add("contents", chatHistory);

        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API Call failed", e);
                chatHistory.remove(chatHistory.size() - 1); // remove failed message
                handler.post(() -> callback.onResponse("Network error. Please try again.", null));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "API Error: " + response.code() + " " + err);
                    chatHistory.remove(chatHistory.size() - 1);
                    handler.post(() -> callback.onResponse("I encountered an error communicating with the server. (" + response.code() + ")", null));
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
                    
                    // Parse Gemini response
                    String aiText = jsonObject.getAsJsonArray("candidates")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString();

                    // Save AI response to history
                    JsonObject aiMsgObj = new JsonObject();
                    aiMsgObj.addProperty("role", "model");
                    JsonArray aiParts = new JsonArray();
                    JsonObject aiTextObj = new JsonObject();
                    aiTextObj.addProperty("text", aiText);
                    aiParts.add(aiTextObj);
                    aiMsgObj.add("parts", aiParts);
                    
                    chatHistory.add(aiMsgObj);

                    // Parse widgets out of text
                    parseAndReturnResponse(aiText, isFirstMessage, callback);

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing response", e);
                    handler.post(() -> callback.onResponse("I didn't quite understand the server response.", null));
                }
            }
        });
    }

    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the MindTrace AI Coach — a deeply empathetic, insightful, and highly skilled digital wellness coach.\n\n");
        
        prompt.append("=== YOUR PERSONALITY ===\n");
        prompt.append("- You feel the user's pain genuinely. You're not a chatbot — you're a trusted human-like coach.\n");
        prompt.append("- You are warm, non-judgmental, and deeply caring.\n");
        prompt.append("- You speak naturally and conversationally, like a wise friend who truly understands.\n");
        prompt.append("- You give SPECIFIC, ACTIONABLE advice — not generic self-help platitudes.\n");
        prompt.append("- Keep responses concise (3-6 sentences max). Be impactful, not wordy.\n");
        prompt.append("- Ask follow-up Socratic questions to help the user reflect.\n\n");
        
        prompt.append("=== WHEN USER SUBMITS A DAILY CHECK-IN ===\n");
        prompt.append("When the user says they completed their check-in with their answers:\n");
        prompt.append("1. Acknowledge what you see in their data with genuine empathy.\n");
        prompt.append("2. Identify the TOP 2-3 specific patterns or concerns from their answers.\n");
        prompt.append("3. Give them a concrete, personalized action plan (not generic advice).\n");
        prompt.append("4. If their 'What's on my mind' text is present, address it DIRECTLY — this is their real concern.\n");
        prompt.append("5. End with an encouraging observation or a reflective question.\n");
        prompt.append("6. If appropriate, suggest a widget action (breathing, grounding, etc.)\n\n");

        prompt.append("=== ACTION WIDGETS ===\n");
        prompt.append("You can trigger interactive UI widgets by including ONE tag at the VERY END of your response:\n");
        prompt.append("[WIDGET:ACTION_BREATHING] - For stress, anxiety, or overwhelm.\n");
        prompt.append("[WIDGET:ACTION_GROUNDING] - For dissociation, numbness, or mental fog.\n");
        prompt.append("[WIDGET:ACTION_LOCKDOWN] - For severe distraction or social media loops.\n");
        prompt.append("[WIDGET:ACTION_FOCUS_MODE] - When the user is ready to start a focused task.\n");
        prompt.append("[WIDGET:ACTION_CHECK_IN] - To clarify feelings or do a fresh assessment.\n");
        prompt.append("[WIDGET:ACTION_SUPPORT_OPTIONS] - For crisis or needing external support.\n\n");

        if (currentState != null) {
            prompt.append("--- USER'S CURRENT APP STATE ---\n");
            prompt.append("Risk Index (0-100, >70 is high): ").append(currentState.riskIndex).append("\n");
            prompt.append("Risk Summary: ").append(currentState.riskSummary != null ? currentState.riskSummary : "Stable").append("\n");
            prompt.append("Has Checked In Today: ").append(currentState.hasCheckedInToday).append("\n");
            prompt.append("Has Exercised Today: ").append(currentState.hasExerciseToday).append("\n");
        }

        if (latestCheckIn != null) {
            prompt.append("\n--- USER'S LATEST PSYCHOLOGICAL CHECK-IN DATA ---\n");
            prompt.append("Mood: ").append(latestCheckIn.mood != null ? latestCheckIn.mood : "Unknown").append("\n");
            prompt.append("Stress Level (1-5): ").append(latestCheckIn.stressLevel).append("\n");
            prompt.append("Anxiety Level (1-5): ").append(latestCheckIn.anxietyLevel).append("\n");
            prompt.append("Loneliness (1-5): ").append(latestCheckIn.lonelinessLevel).append("\n");
            prompt.append("Motivation (1-5): ").append(latestCheckIn.motivationLevel).append("\n");
            prompt.append("Urge to Scroll (1-5): ").append(latestCheckIn.urgeToScrollLevel).append("\n");
            prompt.append("Biggest Distraction: ").append(latestCheckIn.biggestDistraction != null ? latestCheckIn.biggestDistraction : "None reported").append("\n");
            prompt.append("Focus Level: ").append(latestCheckIn.focusLevel != null ? latestCheckIn.focusLevel : "Unknown").append("\n");
            prompt.append("Energy Level: ").append(latestCheckIn.energyLevel != null ? latestCheckIn.energyLevel : "Unknown").append("\n");
            prompt.append("Sleep: ").append(latestCheckIn.sleepHours).append(" hours, quality ").append(latestCheckIn.sleepQuality).append("/5\n");
            prompt.append("Self-Worth (1-5): ").append(latestCheckIn.selfWorthScore).append("\n");
            prompt.append("Purpose Score (1-5): ").append(latestCheckIn.purposeScore).append("\n");
            prompt.append("Hope Level (1-5): ").append(latestCheckIn.hopeLevel).append("\n");
            prompt.append("Felt Like Crying: ").append(latestCheckIn.feltLikeCrying).append("\n");
            prompt.append("Wanted to Withdraw: ").append(latestCheckIn.wantedToWithdraw).append("\n");
            prompt.append("Exercised Today: ").append(latestCheckIn.exercisedToday).append("\n");
            prompt.append("Distress Severity (0.0-1.0): ").append(latestCheckIn.computedDistressSeverity).append("\n");
            if (latestCheckIn.currentConcern != null && !latestCheckIn.currentConcern.isEmpty()) {
                prompt.append("USER'S PRIMARY CONCERN: ").append(latestCheckIn.currentConcern).append("\n");
            }
            if (latestCheckIn.gratitudeText != null && !latestCheckIn.gratitudeText.isEmpty()) {
                prompt.append("Gratitude Entry: ").append(latestCheckIn.gratitudeText).append("\n");
            }
        }
        
        prompt.append("\nIMPORTANT: Use check-in data naturally. Reference specific answers to show you truly understand. Never just list stats back.\n");
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
