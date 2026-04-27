package com.mindtrace.ai.ai;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AiInsightService {
    private static final String META_OPENAI_API_KEY = "com.mindtrace.ai.OPENAI_API_KEY";
    private static final String META_OPENAI_MODEL = "com.mindtrace.ai.OPENAI_MODEL";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 12_000;

    private final Context appContext;
    private final AiInsightCache aiInsightCache;
    private final String apiKey;
    private final String model;

    public AiInsightService(Context context) {
        appContext = context.getApplicationContext();
        aiInsightCache = new AiInsightCache(appContext);
        Bundle metaData = loadMetaData(appContext);
        apiKey = metaData == null ? null : metaData.getString(META_OPENAI_API_KEY);
        model = metaData == null ? DEFAULT_MODEL : metaData.getString(META_OPENAI_MODEL, DEFAULT_MODEL);
    }

    public String generatePrompt(AiInsightInput input) {
        JSONObject data = new JSONObject();
        try {
            data = input == null ? new JSONObject() : input.toJson();
        } catch (Exception ignored) {
            // Fallback to empty JSON object.
        }

        return "You are a digital wellbeing assistant.\n\n"
                + "Analyze the user's behavioral data, NLP distress flags, and recent journal entries.\n\n"
                + "Rules:\n"
                + "- Be concise (max 120 words)\n"
                + "- No medical claims\n"
                + "- No exaggeration\n"
                + "- Be calm and practical\n"
                + "- If journal entries show distress or specific struggles, address them directly and compassionately.\n\n"
                + "Return JSON:\n"
                + "{\n"
                + " \"summary\": \"...\",\n"
                + " \"issues\": [\"...\", \"...\"],\n"
                + " \"recommendation\": \"...\",\n"
                + " \"tone\": \"calm\"\n"
                + "}\n\n"
                + "Input:\n"
                + data.toString();
    }

    public AiInsightResult fetchAiInsight(AiInsightInput input) {
        long today = getStartOfTodayMillis();
        AiInsightResult cached = aiInsightCache.getForDay(today);
        if (cached != null) {
            return cached;
        }

        if (apiKey == null || apiKey.trim().isEmpty() || input == null) {
            return null;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(OPENAI_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject payload = new JSONObject();
            payload.put("model", model);
            payload.put("temperature", 0.2d);
            JSONObject responseFormat = new JSONObject();
            responseFormat.put("type", "json_object");
            payload.put("response_format", responseFormat);

            JSONArray messages = new JSONArray();
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a calm digital wellbeing assistant that only returns JSON.");
            messages.put(systemMessage);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", generatePrompt(input));
            messages.put(userMessage);
            payload.put("messages", messages);

            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] requestBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                outputStream.write(requestBytes);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return null;
            }

            String responseBody = readStream(connection.getInputStream());
            AiInsightResult result = parseResponse(responseBody);
            if (result != null) {
                aiInsightCache.putForDay(today, result);
            }
            return result;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private AiInsightResult parseResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject root = new JSONObject(responseBody);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return null;
            }

            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                return null;
            }

            String content = sanitizeJson(message.optString("content", ""));
            if (content.isEmpty()) {
                return null;
            }

            JSONObject parsed = new JSONObject(content);
            AiInsightResult result = new AiInsightResult();
            result.summary = parsed.optString("summary", "");
            result.recommendation = parsed.optString("recommendation", "");
            result.tone = parsed.optString("tone", "calm");
            JSONArray issues = parsed.optJSONArray("issues");
            if (issues != null) {
                for (int i = 0; i < issues.length(); i++) {
                    result.issues.add(issues.optString(i));
                }
            }
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String sanitizeJson(String rawContent) {
        if (rawContent == null) {
            return "";
        }

        String content = rawContent.trim();
        if (content.startsWith("```")) {
            content = content.replace("```json", "").replace("```", "").trim();
        }
        return content;
    }

    private Bundle loadMetaData(Context context) {
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA
            );
            return applicationInfo.metaData;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readStream(InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private long getStartOfTodayMillis() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
