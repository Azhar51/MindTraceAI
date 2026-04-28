package com.mindtrace.ai.viewmodel.usecase;

import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.entity.DailyResetSession;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.onboarding.OnboardingAssessment;
import com.mindtrace.ai.ui.model.HomeScreenState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Extracted from DashboardViewModel — builds the mission content card.
 */
public class BuildMissionContentUseCase {

    public static class MissionContent {
        public final String title;
        public final List<String> steps;
        public final List<HomeScreenState.MissionStepItem> stepItems;
        public final String progressText;
        public final int progressPercent;

        public MissionContent(String title, List<String> steps, List<HomeScreenState.MissionStepItem> stepItems, String progressText, int progressPercent) {
            this.title = title;
            this.steps = steps;
            this.stepItems = stepItems;
            this.progressText = progressText;
            this.progressPercent = progressPercent;
        }
    }

    public MissionContent execute(List<InterventionTask> activeTaskList, List<InterventionTask> allTaskList, DashboardInsights insights, BehaviorReport behaviorReport, DailyResetSession resetSession, OnboardingAssessment onboardingAssessment, boolean privacyMode, long startOfToday) {
        List<HomeScreenState.MissionStepItem> stepItems = this.buildMissionStepItems(activeTaskList, allTaskList, startOfToday);
        if (stepItems.isEmpty() && onboardingAssessment != null && onboardingAssessment.missionSteps != null) {
            for (String missionStep : onboardingAssessment.missionSteps) {
                if (missionStep == null || missionStep.trim().isEmpty()) continue;
                stepItems.add(new HomeScreenState.MissionStepItem(0, missionStep.trim(), false, false, "general", "This step helps you stabilize your routine while MindTrace learns your patterns."));
                if (stepItems.size() != 3) continue;
                break;
            }
        }
        if (stepItems.isEmpty()) {
            if (behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L) {
                stepItems.add(new HomeScreenState.MissionStepItem(0, "Get out of bed without scrolling", false, false, "rest", "Starting the day away from your phone helps reduce momentum from late-night usage."));
            } else {
                stepItems.add(new HomeScreenState.MissionStepItem(0, "Finish one focused study block", false, false, "focus", "A protected focus block lowers the chance of random switching taking over the morning."));
            }
            if (insights != null && insights.hasOverusedApp && insights.topAppName != null && !insights.topAppName.trim().isEmpty()) {
                stepItems.add(new HomeScreenState.MissionStepItem(0, "Keep " + (privacyMode ? "your top app" : insights.topAppName) + " away from your first work session", false, false, "digital", "Delaying your most distracting app creates a cleaner start and protects early momentum."));
            } else {
                stepItems.add(new HomeScreenState.MissionStepItem(0, "Keep your phone away during the first work session", false, false, "digital", "Physical distance from the phone makes it easier to stay locked into the first important task."));
            }
            stepItems.add(new HomeScreenState.MissionStepItem(0, "Complete one check-in before night", false, false, "general", "A check-in closes the loop for the day and helps tomorrow's guidance get sharper."));
        }
        List<String> steps = this.extractMissionStepTitles(stepItems);
        int totalTasks = 0;
        int completedTasks = 0;
        for (HomeScreenState.MissionStepItem item : stepItems) {
            if (item.taskId <= 0) continue;
            ++totalTasks;
            if (!item.isCompleted) continue;
            ++completedTasks;
        }
        if (totalTasks > 0 && completedTasks >= totalTasks) {
            return new MissionContent("Hold the routine you already built today.", steps, stepItems, totalTasks + "/" + totalTasks + " complete", 100);
        }
        if (resetSession != null && resetSession.isCompleted) {
            String progressText = totalTasks > 0 ? "Reset done | " + completedTasks + "/" + totalTasks + " complete" : "Reset done";
            int progressPercent = totalTasks > 0 ? Math.max(34, Math.round((float)completedTasks * 100.0f / (float)totalTasks)) : Math.max(34, insights == null ? 0 : insights.fulfillmentScore);
            return new MissionContent("Your day has started. Protect the first clean block.", steps, stepItems, progressText, progressPercent);
        }
        String missionTitle = onboardingAssessment != null && !hasEnoughSignalsForSummary(behaviorReport) ? onboardingAssessment.missionTitle : (insights != null && insights.supportRecommended ? "Protect today before distraction takes control." : (behaviorReport != null && behaviorReport.rapidSwitchCount >= 8 ? "Execute your day without random phone switching." : (behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L ? "Reset early so the rest of the day can stay clean." : "Execute your daily routine without phone drift.")));
        int progressPercent = totalTasks > 0 ? Math.round((float)completedTasks * 100.0f / (float)totalTasks) : Math.max(12, insights == null ? 0 : insights.fulfillmentScore);
        String progressText = totalTasks > 0 ? completedTasks + "/" + totalTasks + " complete" : "3 priorities";
        return new MissionContent(missionTitle, steps, stepItems, progressText, progressPercent);
    }

    private boolean hasEnoughSignalsForSummary(BehaviorReport behaviorReport) {
        return behaviorReport != null && behaviorReport.dataAvailable;
    }

    private List<HomeScreenState.MissionStepItem> buildMissionStepItems(List<InterventionTask> activeTaskList, List<InterventionTask> allTaskList, long startOfToday) {
        List<InterventionTask> sourceTasks = allTaskList;
        if ((sourceTasks == null || sourceTasks.isEmpty()) && activeTaskList != null && !activeTaskList.isEmpty()) {
            sourceTasks = activeTaskList;
        }
        if (sourceTasks == null || sourceTasks.isEmpty()) {
            return new ArrayList<HomeScreenState.MissionStepItem>();
        }
        ArrayList<InterventionTask> missionTasks = new ArrayList<InterventionTask>();
        for (InterventionTask task : sourceTasks) {
            if (!this.isMissionTaskRelevant(task, startOfToday)) continue;
            missionTasks.add(task);
        }
        Collections.sort(missionTasks, (first, second) -> {
            boolean secondMicro;
            boolean secondCompleted;
            boolean secondActionable;
            boolean firstActionable = first != null && first.isActionable();
            secondActionable = second != null && second.isActionable();
            if (firstActionable != secondActionable) {
                return Boolean.compare(secondActionable, firstActionable);
            }
            boolean firstCompleted = this.isTaskCompleted(first);
            secondCompleted = this.isTaskCompleted(second);
            if (firstCompleted != secondCompleted) {
                return Boolean.compare(firstCompleted, secondCompleted);
            }
            boolean firstMicro = first != null && first.isMicroIntervention;
            secondMicro = second != null && second.isMicroIntervention;
            if (firstMicro != secondMicro) {
                return Boolean.compare(secondMicro, firstMicro);
            }
            int priorityCompare = Integer.compare(second.priority, first.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            int durationCompare = Integer.compare(first.durationMinutes, second.durationMinutes);
            if (durationCompare != 0) {
                return durationCompare;
            }
            long firstTimestamp = Math.max(first.completedAt, first.dateCreated);
            long secondTimestamp = Math.max(second.completedAt, second.dateCreated);
            return Long.compare(secondTimestamp, firstTimestamp);
        });
        ArrayList<HomeScreenState.MissionStepItem> stepItems = new ArrayList<HomeScreenState.MissionStepItem>();
        HashSet<String> usedMissionTitles = new HashSet<String>();
        for (InterventionTask task : missionTasks) {
            String titleKey;
            String title = task == null || task.title == null ? "" : task.title.trim();
            if (title.isEmpty() || !usedMissionTitles.add(titleKey = title.toLowerCase(Locale.ROOT))) continue;
            stepItems.add(new HomeScreenState.MissionStepItem(task.id, title, this.isTaskCompleted(task), task.isActionable() && !this.isTaskCompleted(task), this.resolveMissionCategory(task), this.resolveMissionReason(task)));
            if (stepItems.size() != 3) continue;
            break;
        }
        return stepItems;
    }

    private String resolveMissionCategory(InterventionTask task) {
        String linkedRisk;
        if (task == null) {
            return "general";
        }
        String category = task.category == null ? "" : task.category.trim().toLowerCase(Locale.ROOT);
        linkedRisk = task.linkedRiskCategory == null ? "" : task.linkedRiskCategory.trim().toLowerCase(Locale.ROOT);
        if (category.contains("focus") || category.contains("mindfulness")) {
            return "focus";
        }
        if (category.contains("detox") || linkedRisk.contains("digital") || linkedRisk.contains("phone")) {
            return "digital";
        }
        if (category.contains("social") || linkedRisk.contains("social")) {
            return "social";
        }
        String timeSlot = task.scheduledTimeSlot == null ? "" : task.scheduledTimeSlot;
        if (category.contains("recovery") || linkedRisk.contains("sleep") || timeSlot.toLowerCase(Locale.ROOT).contains("evening")) {
            return "rest";
        }
        return "general";
    }

    private String resolveMissionReason(InterventionTask task) {
        if (task == null) {
            return null;
        }
        if (task.whyThisTask != null && !task.whyThisTask.trim().isEmpty()) {
            return task.whyThisTask.trim();
        }
        if (task.description != null && !task.description.trim().isEmpty()) {
            return task.description.trim();
        }
        return "This step was chosen to reduce friction and strengthen the next healthy action in your day.";
    }

    private boolean isMissionTaskRelevant(InterventionTask task, long startOfToday) {
        if (task == null || task.title == null || task.title.trim().isEmpty()) {
            return false;
        }
        if (task.isActionable()) {
            return true;
        }
        return this.isTaskCompleted(task) && task.completedAt >= startOfToday;
    }

    private boolean isTaskCompleted(InterventionTask task) {
        if (task == null) {
            return false;
        }
        return task.isCompleted || "COMPLETED".equals(task.status);
    }

    private List<String> extractMissionStepTitles(List<HomeScreenState.MissionStepItem> stepItems) {
        ArrayList<String> titles = new ArrayList<String>();
        if (stepItems == null) {
            return titles;
        }
        for (HomeScreenState.MissionStepItem item : stepItems) {
            if (item == null || item.text == null || item.text.trim().isEmpty()) continue;
            titles.add(item.text.trim());
        }
        return titles;
    }
}
