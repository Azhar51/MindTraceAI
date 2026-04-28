package com.mindtrace.ai.viewmodel.usecase;

import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.entity.DailyResetSession;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.onboarding.OnboardingAssessment;
import com.mindtrace.ai.ui.model.HomeScreenState;

import java.util.ArrayList;

public class BuildFocusWindowCardUseCase {

    public HomeScreenState.FocusWindowCard execute(DashboardInsights insights, BehaviorReport behaviorReport, AppUsageModel topApp, QuestionnaireResponse latestResponse, DailyResetSession resetSession, OnboardingAssessment onboardingAssessment, boolean privacyMode, boolean hasData, boolean highRisk, int riskIndex) {
        boolean urgent;
        String windowLabel = HomeScreenTextHelper.formatMomentumWindowLabel(highRisk ? 35 : 45);
        ArrayList<String> rituals = new ArrayList<>();
        String badgeText = "Online";
        String title = "Your personal coach is ready to help";
        String coachText = "Get context-aware advice, stress relief, and focus strategies based on your real-time usage and mood data.";
        rituals.add("Powered by MindTrace Intelligence");
        String actionLabel = "Open Coach Chat";
        String actionType = "none";
        urgent = riskIndex >= 55;
        if (highRisk) {
            badgeText = "AI Intervention";
            title = "Let's stabilize your focus";
            coachText = "I've detected some pattern drift. Chat with me now for a quick reset.";
            urgent = true;
        }
        return new HomeScreenState.FocusWindowCard(title, windowLabel, coachText, badgeText, rituals, actionLabel, actionType, urgent);
    }
}
