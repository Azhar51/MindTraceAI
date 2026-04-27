package com.mindtrace.ai.behavior;

public class BehaviorSnapshot {
    public long dayTimestamp;
    public long timestamp;
    public int appSwitchCount;
    public int rapidSwitchCount;
    public int bingeSessionCount;
    public long lateNightUsageMillis;
    public long totalForegroundMillis;
    public long longestSessionMillis;
    public boolean hasLoopPattern;
    public String dominantAppPackage;
    public String summaryLabel;
    public String explanation;

    public static BehaviorSnapshot fromReport(BehaviorReport report, long dayTimestamp, long recordedAt) {
        BehaviorSnapshot snapshot = new BehaviorSnapshot();
        snapshot.dayTimestamp = dayTimestamp;
        snapshot.timestamp = recordedAt;
        snapshot.appSwitchCount = report.appSwitchCount;
        snapshot.rapidSwitchCount = report.rapidSwitchCount;
        snapshot.bingeSessionCount = report.bingeSessionCount;
        snapshot.lateNightUsageMillis = report.lateNightUsageMillis;
        snapshot.totalForegroundMillis = report.totalForegroundMillis;
        snapshot.longestSessionMillis = report.longestSessionMillis;
        snapshot.hasLoopPattern = report.hasLoopPattern;
        snapshot.dominantAppPackage = report.dominantAppPackage;
        snapshot.summaryLabel = report.summaryLabel;
        snapshot.explanation = report.explanation;
        return snapshot;
    }
}
