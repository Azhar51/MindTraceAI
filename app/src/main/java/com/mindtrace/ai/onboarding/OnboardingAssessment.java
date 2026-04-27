package com.mindtrace.ai.onboarding;

import java.util.List;

public class OnboardingAssessment {
    public final int riskIndex;
    public final String wellnessLabel;
    public final String summary;
    public final String missionTitle;
    public final List<String> missionSteps;
    public final List<String> warningItems;
    public final String nextBestActionTitle;
    public final String nextBestActionReason;
    public final boolean supportRecommended;
    public final boolean phoneRiskHigh;
    public final boolean routineRiskHigh;
    public final boolean sleepRiskHigh;
    public final boolean purposeRiskHigh;
    public final String primaryFocus;

    public OnboardingAssessment(
            int riskIndex,
            String wellnessLabel,
            String summary,
            String missionTitle,
            List<String> missionSteps,
            List<String> warningItems,
            String nextBestActionTitle,
            String nextBestActionReason,
            boolean supportRecommended,
            boolean phoneRiskHigh,
            boolean routineRiskHigh,
            boolean sleepRiskHigh,
            boolean purposeRiskHigh,
            String primaryFocus
    ) {
        this.riskIndex = riskIndex;
        this.wellnessLabel = wellnessLabel;
        this.summary = summary;
        this.missionTitle = missionTitle;
        this.missionSteps = missionSteps;
        this.warningItems = warningItems;
        this.nextBestActionTitle = nextBestActionTitle;
        this.nextBestActionReason = nextBestActionReason;
        this.supportRecommended = supportRecommended;
        this.phoneRiskHigh = phoneRiskHigh;
        this.routineRiskHigh = routineRiskHigh;
        this.sleepRiskHigh = sleepRiskHigh;
        this.purposeRiskHigh = purposeRiskHigh;
        this.primaryFocus = primaryFocus;
    }
}
