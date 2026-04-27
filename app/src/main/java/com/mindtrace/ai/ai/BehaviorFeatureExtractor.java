package com.mindtrace.ai.ai;

import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.behavior.BehaviorReport;

import java.util.List;

public class BehaviorFeatureExtractor {
    public BehaviorFeatures extractFeatures(
            BehaviorReport report,
            List<AppUsageModel> topApps,
            int taskCompletionRate
    ) {
        BehaviorFeatures features = new BehaviorFeatures();
        if (report == null || report.totalForegroundMillis <= 0L) {
            features.taskCompletionRate = taskCompletionRate;
            return features;
        }

        int estimatedSessionCount = report.appSwitchCount + 1;
        features.sessionCount = estimatedSessionCount;
        features.avgSessionLength = estimatedSessionCount <= 0
                ? 0d
                : report.totalForegroundMillis / (double) estimatedSessionCount;
        double foregroundHours = report.totalForegroundMillis / (60d * 60d * 1000d);
        features.switchRate = foregroundHours <= 0d ? 0d : report.appSwitchCount / foregroundHours;
        features.longestSession = report.longestSessionMillis;
        features.lateNightRatio = report.totalForegroundMillis <= 0L
                ? 0d
                : report.lateNightUsageMillis / (double) report.totalForegroundMillis;
        features.appDiversityScore = calculateAppDiversity(topApps);
        features.taskCompletionRate = taskCompletionRate;
        return features;
    }

    private double calculateAppDiversity(List<AppUsageModel> topApps) {
        if (topApps == null || topApps.isEmpty()) {
            return 0d;
        }

        long totalUsage = 0L;
        for (AppUsageModel app : topApps) {
            totalUsage += Math.max(app.usageTime, 0L);
        }
        if (totalUsage <= 0L) {
            return 0d;
        }

        double entropy = 0d;
        int activeApps = 0;
        for (AppUsageModel app : topApps) {
            if (app.usageTime <= 0L) {
                continue;
            }
            activeApps++;
            double probability = app.usageTime / (double) totalUsage;
            entropy -= probability * Math.log(probability);
        }
        if (activeApps <= 1) {
            return 0d;
        }
        return entropy / Math.log(activeApps);
    }
}
