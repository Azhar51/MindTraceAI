package com.mindtrace.ai.behavior;

import java.util.ArrayList;
import java.util.List;

public class BehaviorReport {
    public long analysisStartTime;
    public long analysisEndTime;
    public int appSwitchCount;
    public int rapidSwitchCount;
    public int bingeSessionCount;
    public boolean hasLoopPattern;
    public long lateNightUsageMillis;
    public long totalForegroundMillis;
    public long longestSessionMillis;
    public int shortSessionCount;
    public int dominantAppCount;
    public String dominantAppPackage;
    public List<String> detectedSignals;
    public List<String> reasoningNotes;
    public String summaryLabel;
    public String explanation;
    public double dominantUsageRatio;
    public double lateNightUsageRatio;
    public boolean dataAvailable;
    
    // AI Vectors
    public double activeVsPassiveRatio;
    public String dominantUsageQuadrant;
    public List<String> frequentAppLoops;

    public BehaviorReport() {
        detectedSignals = new ArrayList<>();
        reasoningNotes = new ArrayList<>();
        frequentAppLoops = new ArrayList<>();
        summaryLabel = "Healthy balance";
        explanation = "No strong behavior concern was detected in the current usage window.";
        dataAvailable = true;
    }

    public static BehaviorReport empty(long startTime, long endTime) {
        BehaviorReport report = new BehaviorReport();
        report.analysisStartTime = startTime;
        report.analysisEndTime = endTime;
        report.summaryLabel = "Calm / low activity";
        report.explanation = "No meaningful foreground behavior was recorded for this period.";
        return report;
    }

    public static BehaviorReport unavailable(long startTime, long endTime, String explanation) {
        BehaviorReport report = empty(startTime, endTime);
        report.dataAvailable = false;
        report.summaryLabel = "Behavior data unavailable";
        report.explanation = explanation;
        report.detectedSignals.clear();
        report.reasoningNotes.clear();
        report.reasoningNotes.add(explanation);
        return report;
    }
}
