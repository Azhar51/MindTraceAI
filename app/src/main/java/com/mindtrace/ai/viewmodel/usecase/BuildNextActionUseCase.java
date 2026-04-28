package com.mindtrace.ai.viewmodel.usecase;

import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.entity.DailyResetSession;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.onboarding.OnboardingAssessment;

public class BuildNextActionUseCase {

    public static class NextActionContent {
        public final String title;
        public final String reason;
        public final String eta;

        public NextActionContent(String title, String reason, String eta) {
            this.title = title;
            this.reason = reason;
            this.eta = eta;
        }
    }

    public NextActionContent execute(DashboardInsights insights, BehaviorReport behaviorReport, AppUsageModel topApp, DailyResetSession resetSession, OnboardingProfile profile, OnboardingAssessment onboardingAssessment, boolean privacyMode, boolean hasData, boolean highRisk) {
        if (resetSession != null && resetSession.isCompleted) {
            return new NextActionContent("Protect the first block you just started.", "Your reset is done. Keep the phone away long enough for the first real win to land.", "Protect the next 30 min");
        }
        if (!hasData) {
            return new NextActionContent("Complete one quick check-in.", "This gives MindTrace enough signal to build personalized guidance instead of generic advice.", "Takes about 1 min");
        }
        if (onboardingAssessment != null && !(behaviorReport != null && behaviorReport.dataAvailable)) {
            return new NextActionContent(onboardingAssessment.nextBestActionTitle, onboardingAssessment.nextBestActionReason, profile != null && profile.supportNeeded ? "Start now | low pressure" : "Start now");
        }
        if (highRisk && behaviorReport != null && behaviorReport.rapidSwitchCount >= 8) {
            return new NextActionContent("Put your phone on focus mode for 30 minutes.", "This cuts early fragmentation and protects the first meaningful block of momentum.", "Starts now | 30 min block");
        }
        if (highRisk && behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L) {
            return new NextActionContent("Take a 2-minute reset before opening any distracting app.", "A short reset breaks the carryover from late-night phone use and helps the day start cleaner.", "Takes about 2 min");
        }
        if (topApp != null && topApp.usageTime >= 2700000L) {
            return new NextActionContent("Keep " + HomeScreenTextHelper.resolveTopAppLabel(topApp, privacyMode) + " off the first hour.", "The day gets easier when your main distraction does not get the first tap.", "Protect the next 60 min");
        }
        if (insights != null && HomeScreenTextHelper.hasText(insights.nextBestAction)) {
            return new NextActionContent(HomeScreenTextHelper.trimSentence(insights.nextBestAction, 95), HomeScreenTextHelper.trimSentence(insights.reasonSummary == null || insights.reasonSummary.trim().isEmpty() ? "This is the simplest move that protects today's momentum." : insights.reasonSummary, 120), HomeScreenTextHelper.resolveEta(insights.nextBestAction));
        }
        return new NextActionContent("Start your first 25-minute work block.", "The fastest way to feel better is to create one clean win before the phone steals your attention.", "Starts now | 25 min");
    }
}
