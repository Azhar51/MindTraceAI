package com.mindtrace.ai.viewmodel.usecase;

import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.onboarding.OnboardingAssessment;
import com.mindtrace.ai.ui.model.HomeScreenState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BuildWarningCardsUseCase {

    public List<HomeScreenState.WarningCardItem> execute(DashboardInsights insights, BehaviorReport behaviorReport, AppUsageModel topApp, OnboardingAssessment onboardingAssessment, boolean privacyMode) {
        List<String> titles = this.buildWarningItems(insights, behaviorReport, topApp, onboardingAssessment, privacyMode);
        ArrayList<HomeScreenState.WarningCardItem> items = new ArrayList<>();
        for (String title : titles) {
            if (title == null || title.trim().isEmpty()) continue;
            String lower = title.toLowerCase(Locale.getDefault());
            int severity = 2;
            String detail = "This pattern has a moderate chance of stealing momentum if it shows up today.";
            if (lower.contains("late-night")) {
                severity = behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L ? 3 : 2;
                long lateNightMinutes = behaviorReport == null ? 22L : Math.max(12L, behaviorReport.lateNightUsageMillis / 60000L);
                detail = "Caught in the late-night window \u2022 Usually between 11 PM\u20131 AM \u2022 Costs about " + lateNightMinutes + " min of recovery time.";
            } else if (lower.contains("switch")) {
                int switchCount = behaviorReport == null ? 18 : Math.max(behaviorReport.rapidSwitchCount, behaviorReport.appSwitchCount);
                severity = switchCount >= 20 ? 3 : 2;
                detail = "Fragmented attention detected (" + switchCount + " switches). Your brain is searching for dopamine. Try a 2-min breathing reset.";
            } else if (lower.contains("loop")) {
                severity = 3;
                detail = "Recursive app cycling detected. This 'locked-in' state makes it hard to start meaningful work. Close all apps and stand up.";
            } else if (lower.contains("first thing") || lower.contains("first task")) {
                severity = topApp != null && topApp.usageTime >= 2700000L ? 3 : 2;
                detail = "Reacting to notifications before your morning intention. This gives distraction the first-tap advantage for the rest of your day.";
            } else if (lower.contains("sleep")) {
                severity = 3;
                detail = "Sleep pressure is part of today's risk stack \u2022 Protecting bedtime could save tomorrow's energy and focus.";
            } else if (lower.contains("bed")) {
                severity = 2;
                detail = "High-risk comfort loop \u2022 Usually happens right after waking \u2022 Often delays the first intentional action.";
            } else if (lower.contains("long unplanned scroll") || lower.contains("dominate")) {
                long scrollMinutes = behaviorReport == null ? 45L : Math.max(30L, behaviorReport.longestSessionMillis / 60000L);
                severity = scrollMinutes >= 60L ? 3 : 2;
                detail = "One dominant session can swallow " + scrollMinutes + " min \u2022 Often starts as a quick check and snowballs.";
            }
            items.add(new HomeScreenState.WarningCardItem(title, detail, severity));
            if (items.size() != 5) continue;
            break;
        }
        return items;
    }

    public List<String> buildWarningItems(DashboardInsights insights, BehaviorReport behaviorReport, AppUsageModel topApp, OnboardingAssessment onboardingAssessment, boolean privacyMode) {
        ArrayList<String> items = new ArrayList<>();
        if (onboardingAssessment != null && onboardingAssessment.warningItems != null) {
            for (String warning : onboardingAssessment.warningItems) {
                if (warning == null || warning.trim().isEmpty()) continue;
                this.addIfMissing(items, warning);
            }
        }
        if (behaviorReport != null && behaviorReport.dataAvailable) {
            if (behaviorReport.lateNightUsageMillis >= 600000L) {
                items.add("Late-night scrolling");
            }
            if (behaviorReport.rapidSwitchCount >= 8 || behaviorReport.appSwitchCount >= 20) {
                items.add("Random app switching");
            }
            if (behaviorReport.hasLoopPattern) {
                items.add("Falling into the same app loop again");
            }
            if (behaviorReport.bingeSessionCount > 0 || behaviorReport.longestSessionMillis >= 2700000L) {
                items.add("One long unplanned scroll");
            }
        }
        if (topApp != null && topApp.usageTime >= 1800000L) {
            items.add("Opening " + HomeScreenTextHelper.resolveTopAppLabel(topApp, privacyMode) + " first thing");
        }
        if (insights != null && insights.reasonItems != null) {
            for (String reason : insights.reasonItems) {
                if (reason == null) continue;
                String lower = reason.toLowerCase(Locale.getDefault());
                if (lower.contains("sleep")) {
                    items.add("Skipping sleep tonight");
                    continue;
                }
                if (lower.contains("app switching")) {
                    items.add("Random app switching");
                    continue;
                }
                if (!lower.contains("top app") && !lower.contains("dominates")) continue;
                items.add("Letting one app dominate the day");
            }
        }
        this.addIfMissing(items, "Staying in bed with the phone");
        this.addIfMissing(items, "Checking distracting apps before your first task");
        this.addIfMissing(items, "Skipping sleep tonight");
        return items.size() > 5 ? new ArrayList<>(items.subList(0, 5)) : items;
    }

    private void addIfMissing(List<String> items, String item) {
        if (!items.contains(item)) {
            items.add(item);
        }
    }
}
