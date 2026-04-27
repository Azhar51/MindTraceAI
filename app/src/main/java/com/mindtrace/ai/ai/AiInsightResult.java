package com.mindtrace.ai.ai;

import java.util.ArrayList;
import java.util.List;

public class AiInsightResult {
    public String summary;
    public List<String> issues;
    public String recommendation;
    public String tone;

    public AiInsightResult() {
        issues = new ArrayList<>();
    }

    public static AiInsightResult fallback(String summary, List<String> issues, String recommendation) {
        AiInsightResult result = new AiInsightResult();
        result.summary = summary;
        result.issues = issues == null ? new ArrayList<>() : new ArrayList<>(issues);
        result.recommendation = recommendation;
        result.tone = "calm";
        return result;
    }
}
