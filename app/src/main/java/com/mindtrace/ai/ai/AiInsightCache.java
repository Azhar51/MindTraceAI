package com.mindtrace.ai.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class AiInsightCache {
    private static final String PREFS_NAME = "mindtrace_ai_cache";
    private static final String KEY_DAY = "cached_day";
    private static final String KEY_RESULT = "cached_result";

    private final SharedPreferences preferences;

    public AiInsightCache(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public AiInsightResult getForDay(long dayTimestamp) {
        long cachedDay = preferences.getLong(KEY_DAY, -1L);
        if (cachedDay != dayTimestamp) {
            return null;
        }

        String cachedJson = preferences.getString(KEY_RESULT, null);
        if (cachedJson == null || cachedJson.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject jsonObject = new JSONObject(cachedJson);
            AiInsightResult result = new AiInsightResult();
            result.summary = jsonObject.optString("summary", "");
            result.recommendation = jsonObject.optString("recommendation", "");
            result.tone = jsonObject.optString("tone", "calm");
            JSONArray issuesArray = jsonObject.optJSONArray("issues");
            if (issuesArray != null) {
                for (int i = 0; i < issuesArray.length(); i++) {
                    result.issues.add(issuesArray.optString(i));
                }
            }
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void putForDay(long dayTimestamp, AiInsightResult result) {
        if (result == null) {
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("summary", result.summary);
            jsonObject.put("recommendation", result.recommendation);
            jsonObject.put("tone", result.tone);
            JSONArray issuesArray = new JSONArray();
            if (result.issues != null) {
                for (String issue : result.issues) {
                    issuesArray.put(issue);
                }
            }
            jsonObject.put("issues", issuesArray);

            preferences.edit()
                    .putLong(KEY_DAY, dayTimestamp)
                    .putString(KEY_RESULT, jsonObject.toString())
                    .apply();
        } catch (Exception ignored) {
            // Cache failure should never break app logic.
        }
    }
}
