package com.mindtrace.ai.ai;

import org.json.JSONException;
import org.json.JSONObject;

public class AiInsightInput {
    public long screenTimeToday;
    public double screenTimeDeviation;
    public String fragmentationLevel;
    public int rapidSwitchCount;
    public boolean bingeFlag;
    public long lateNightUsage;
    public String mood;
    public int stressLevel;
    public float sleepHours;
    public int taskCompletionRate;
    public String dominantApp;
    public String distressFlags;
    public String recentJournalEntries;

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("screenTimeToday", screenTimeToday);
        jsonObject.put("screenTimeDeviation", screenTimeDeviation);
        jsonObject.put("fragmentationLevel", fragmentationLevel);
        jsonObject.put("rapidSwitchCount", rapidSwitchCount);
        jsonObject.put("bingeFlag", bingeFlag);
        jsonObject.put("lateNightUsage", lateNightUsage);
        jsonObject.put("mood", mood);
        jsonObject.put("stressLevel", stressLevel);
        jsonObject.put("sleepHours", sleepHours);
        jsonObject.put("taskCompletionRate", taskCompletionRate);
        jsonObject.put("dominantApp", dominantApp);
        jsonObject.put("distressFlags", distressFlags);
        jsonObject.put("recentJournalEntries", recentJournalEntries);
        return jsonObject;
    }
}
