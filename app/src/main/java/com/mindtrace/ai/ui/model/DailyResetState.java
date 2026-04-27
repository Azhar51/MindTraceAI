package com.mindtrace.ai.ui.model;

public class DailyResetState {
    public final String resetTitle;
    public final String supportingText;
    public final String focusTask;
    public final String firstAction;
    public final String warningItem;
    public final int timerDurationMinutes;
    public final long timeRemaining;
    public final boolean isRunning;
    public final boolean isCompleted;
    public final long completionTimestamp;
    public final String timerButtonLabel;
    public final String completionButtonLabel;
    public final String readinessPrompt;
    public final String completionMessage;
    public final boolean isLoading;

    public DailyResetState(
            String resetTitle,
            String supportingText,
            String focusTask,
            String firstAction,
            String warningItem,
            int timerDurationMinutes,
            long timeRemaining,
            boolean isRunning,
            boolean isCompleted,
            long completionTimestamp,
            String timerButtonLabel,
            String completionButtonLabel,
            String readinessPrompt,
            String completionMessage,
            boolean isLoading
    ) {
        this.resetTitle = resetTitle;
        this.supportingText = supportingText;
        this.focusTask = focusTask;
        this.firstAction = firstAction;
        this.warningItem = warningItem;
        this.timerDurationMinutes = timerDurationMinutes;
        this.timeRemaining = timeRemaining;
        this.isRunning = isRunning;
        this.isCompleted = isCompleted;
        this.completionTimestamp = completionTimestamp;
        this.timerButtonLabel = timerButtonLabel;
        this.completionButtonLabel = completionButtonLabel;
        this.readinessPrompt = readinessPrompt;
        this.completionMessage = completionMessage;
        this.isLoading = isLoading;
    }

    public static DailyResetState loading() {
        return new DailyResetState(
                "Preparing your daily reset",
                "Reading today's mission and building the cleanest first move.",
                "Loading today's focus...",
                "Choosing the strongest first action...",
                "Reading likely distractions...",
                25,
                25L * 60L * 1000L,
                false,
                false,
                0L,
                "Starting...",
                "Preparing...",
                "How ready do you feel now?",
                "",
                true
        );
    }
}
