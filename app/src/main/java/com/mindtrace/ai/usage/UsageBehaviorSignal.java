package com.mindtrace.ai.usage;

import java.util.ArrayList;
import java.util.List;

public class UsageBehaviorSignal {
    public long dayTimestamp;
    public long totalUsageMillis;
    public int screenTimeIntensity;
    public int fragmentedUsageScore;
    public int bingeScore;
    public int switchScore;
    public int lateNightScore;
    public int dependencyScore;
    public int appDiversityScore;
    public int topAppDominanceScore;
    public int distractionPatternScore;
    public String summaryLabel;
    public final List<String> riskFlags = new ArrayList<>();
    public final List<String> explanatoryNotes = new ArrayList<>();
    
    // AI Advanced Vectors
    public String dominantUsageQuadrant; // e.g., "MORNING", "LATE_NIGHT"
    public List<String> frequentAppLoops = new ArrayList<>(); // e.g., ["com.instagram -> com.whatsapp"]
    public double activeVsPassiveRatio; // Active apps vs Doomscrolling apps
}
