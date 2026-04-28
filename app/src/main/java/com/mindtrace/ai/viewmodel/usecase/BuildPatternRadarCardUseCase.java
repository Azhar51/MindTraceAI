package com.mindtrace.ai.viewmodel.usecase;

import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.ui.model.HomeScreenState;

import java.util.ArrayList;

public class BuildPatternRadarCardUseCase {

    public HomeScreenState.PatternRadarCard execute(BehaviorReport behaviorReport, AppUsageModel topApp, boolean privacyMode, boolean hasData, int riskIndex) {
        String footer;
        ArrayList<String> signalPills = new ArrayList<>();
        boolean urgent = riskIndex >= 70;
        String title = "Live Pattern Radar";
        String summary = "MindTrace is watching the strongest live signals shaping your day.";
        footer = hasData ? "Live behavior stream" : "Waiting for enough signal";
        if (!hasData) {
            signalPills.add("Need one check-in");
            signalPills.add("Usage signal pending");
            signalPills.add("Pattern map warming up");
            return new HomeScreenState.PatternRadarCard(title, summary, signalPills, footer, false);
        }
        if (topApp != null && topApp.usageTime > 0L) {
            signalPills.add("Top pull: " + HomeScreenTextHelper.resolveTopAppLabel(topApp, privacyMode));
        } else {
            signalPills.add("Top pull: calm");
        }
        if (behaviorReport != null) {
            signalPills.add("Switches: " + Math.max(0, behaviorReport.rapidSwitchCount));
            long lateNightMinutes = Math.max(0L, behaviorReport.lateNightUsageMillis / 60000L);
            if (lateNightMinutes > 0L) {
                signalPills.add("Night drift: " + lateNightMinutes + "m");
            } else {
                long longestMinutes = Math.max(0L, behaviorReport.longestSessionMillis / 60000L);
                signalPills.add("Longest flow: " + Math.max(5L, longestMinutes) + "m");
            }
            if (behaviorReport.lateNightUsageMillis >= 1200000L) {
                urgent = true;
                summary = "Late-night carryover is one of the strongest signals in your stack today.";
                footer = "Best move: lower the stimulation before bed";
            } else if (behaviorReport.rapidSwitchCount >= 8) {
                urgent = true;
                summary = "Rapid switching is the loudest signal right now. One protected block will outperform multitasking.";
                footer = "Best move: lock one task for the next block";
            } else if (behaviorReport.hasLoopPattern) {
                urgent = true;
                summary = "MindTrace detected a repeat-loop pattern, which usually means a short check is turning into a drift session.";
                footer = "Best move: break the loop with a clean reset";
            } else if (behaviorReport.dataAvailable) {
                summary = behaviorReport.explanation == null || behaviorReport.explanation.trim().isEmpty() ? "Your strongest signals look stable right now." : HomeScreenTextHelper.trimSentence(behaviorReport.explanation, 115);
                footer = behaviorReport.summaryLabel == null || behaviorReport.summaryLabel.trim().isEmpty() ? "Live behavior stream" : behaviorReport.summaryLabel;
            }
        } else {
            signalPills.add("Switches: --");
            signalPills.add("Night drift: --");
        }
        while (signalPills.size() < 3) {
            signalPills.add("Pattern signal active");
        }
        return new HomeScreenState.PatternRadarCard(title, summary, signalPills, footer, urgent);
    }
}
