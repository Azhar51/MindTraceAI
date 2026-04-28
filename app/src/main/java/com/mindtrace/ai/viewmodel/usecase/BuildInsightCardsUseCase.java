package com.mindtrace.ai.viewmodel.usecase;

import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.ui.model.HomeScreenState;

import java.util.ArrayList;
import java.util.List;

public class BuildInsightCardsUseCase {

    public List<HomeScreenState.InsightItem> execute(DashboardInsights insights, BehaviorReport behaviorReport, QuestionnaireResponse latestResponse, boolean highRisk, boolean hasData) {
        ArrayList<HomeScreenState.InsightItem> items = new ArrayList<>();
        List<String> primaryReasons = this.collectInsightReasons(insights);
        if (insights != null) {
            if (HomeScreenTextHelper.hasText(insights.aiSummary)) {
                items.add(new HomeScreenState.InsightItem("MindTrace AI", HomeScreenTextHelper.trimSentence(insights.aiSummary, 140), primaryReasons, highRisk ? "Review stabilizing insight" : "View guidance", "plan", HomeScreenTextHelper.hasText(insights.anomalySummary)));
            } else if (HomeScreenTextHelper.hasText(insights.primaryInsight)) {
                items.add(new HomeScreenState.InsightItem("MindTrace AI", HomeScreenTextHelper.trimSentence(insights.primaryInsight, 140), primaryReasons, highRisk ? "Review protection plan" : "View guidance", "plan", HomeScreenTextHelper.hasText(insights.anomalySummary)));
            }
            if (insights.hasTrajectory() && HomeScreenTextHelper.hasText(insights.trajectorySummary)) {
                boolean trajectoryUrgent = "rapidly_worsening".equals(insights.trajectoryLabel)
                        || "gradually_worsening".equals(insights.trajectoryLabel);
                String trajectoryAction = trajectoryUrgent ? "Review 7-day trend" : "See your progress";
                items.add(new HomeScreenState.InsightItem(
                        "7-Day Trajectory",
                        HomeScreenTextHelper.trimSentence(insights.trajectorySummary, 140),
                        primaryReasons,
                        trajectoryAction,
                        "plan",
                        trajectoryUrgent
                ));
            }
            if (HomeScreenTextHelper.hasText(insights.anomalySummary)) {
                items.add(new HomeScreenState.InsightItem("Pattern shift detected", HomeScreenTextHelper.trimSentence(insights.anomalySummary, 140), primaryReasons, "Learn why", "plan", true));
            }
            if (HomeScreenTextHelper.hasText(insights.recommendation)) {
                items.add(new HomeScreenState.InsightItem("Suggested next move", HomeScreenTextHelper.trimSentence(insights.recommendation, 140), primaryReasons, "View plan", "plan", false));
            }
        }
        if (items.isEmpty()) {
            String fallback = !hasData ? "Complete one check-in so MindTrace can stop guessing and start giving you pattern-based guidance." : (highRisk ? "Today looks sensitive to distraction carryover. The best move is still to protect the next clean block." : (latestResponse != null && latestResponse.exercisedToday ? "You already stacked one protective signal by moving your body. Keep the rest of the day friction-light." : (behaviorReport != null && behaviorReport.rapidSwitchCount >= 8 ? "Attention looks more fragmented than usual. A single protected work block will outperform trying to multitask." : "Your dashboard is stable today. Small deliberate actions now will keep tomorrow easier.")));
            items.add(new HomeScreenState.InsightItem("MindTrace AI", fallback, primaryReasons, "View insight", "plan", false));
        }
        return items.size() > 3 ? new ArrayList<>(items.subList(0, 3)) : items;
    }

    private List<String> collectInsightReasons(DashboardInsights insights) {
        ArrayList<String> reasons = new ArrayList<>();
        if (insights == null) {
            return reasons;
        }
        if (insights.hasTrajectory() && HomeScreenTextHelper.hasText(insights.trajectoryLabel)) {
            String trajectoryReason = this.buildTrajectoryReasonLabel(insights.trajectoryLabel);
            if (trajectoryReason != null) {
                reasons.add(trajectoryReason);
            }
        }
        if (insights.classificationReasons != null) {
            for (String reason : insights.classificationReasons) {
                if (HomeScreenTextHelper.hasText(reason)) {
                    reasons.add(HomeScreenTextHelper.trimSentence(reason, 88));
                }
                if (reasons.size() != 5) continue;
                return reasons;
            }
        }
        if (insights.reasonItems != null) {
            for (String reason : insights.reasonItems) {
                if (HomeScreenTextHelper.hasText(reason)) {
                    reasons.add(HomeScreenTextHelper.trimSentence(reason, 88));
                }
                if (reasons.size() != 5) continue;
                return reasons;
            }
        }
        if (HomeScreenTextHelper.hasText(insights.behaviorExplanation)) {
            reasons.add(HomeScreenTextHelper.trimSentence(insights.behaviorExplanation, 88));
        }
        return reasons;
    }

    private String buildTrajectoryReasonLabel(String trajectoryLabel) {
        if (trajectoryLabel == null) return null;
        switch (trajectoryLabel) {
            case "rapidly_improving":
                return "📉 7-day trend: rapidly improving";
            case "gradually_improving":
                return "📉 7-day trend: steadily improving";
            case "gradually_worsening":
                return "📈 7-day trend: gradually worsening";
            case "rapidly_worsening":
                return "⚠ 7-day trend: rapidly worsening";
            case "stable":
                return "➡ 7-day trend: stable";
            default:
                return null;
        }
    }
}
