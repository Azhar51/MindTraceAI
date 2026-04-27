package com.mindtrace.ai.ui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of everything the home screen needs to render.
 *
 * <p>Constructed exclusively via {@link Builder} to avoid the 37-parameter
 * constructor that previously made the call-site unreadable.</p>
 *
 * <p>The legacy positional constructor is preserved (marked {@code @Deprecated})
 * so that existing call sites compile during migration. New code should
 * always use {@code HomeScreenState.builder()}.</p>
 */
public class HomeScreenState {
    public static final String ACTION_NONE = "none";
    public static final String ACTION_RESET = "reset";
    public static final String ACTION_CHECK_IN = "check_in";
    public static final String ACTION_PLAN = "plan";
    public static final String ACTION_SUPPORT = "support";

    // ─────────────────────────────────────────────────────────────────────
    // FIELDS — all public + final for backward compatibility with existing
    // layout code that reads them directly (e.g., fragment XML binding).
    // ─────────────────────────────────────────────────────────────────────

    public final String greetingText;
    public final String dateText;
    public final String wellnessLabel;
    public final int riskIndex;
    public final List<Float> riskHistory;
    public final double screenTimeDeviation;
    public final String riskSummary;
    public final String baselineComparisonText;
    public final String missionTitle;
    public final List<String> missionSteps;
    public final List<MissionStepItem> missionStepItems;
    public final String missionProgressText;
    public final int missionProgressPercent;
    public final String primaryActionLabel;
    public final String primaryActionType;
    public final List<String> warningItems;
    public final List<WarningCardItem> warningCardItems;
    public final List<InsightItem> aiInsightItems;
    public final PatternRadarCard patternRadarCard;
    public final FocusWindowCard focusWindowCard;
    public final ForecastCard forecastCard;
    public final boolean hasCheckedInToday;
    public final boolean hasJournalEntryToday;
    public final boolean hasExerciseToday;
    public final String nextBestActionTitle;
    public final String nextBestActionReason;
    public final String nextBestActionETA;
    public final boolean showSupportStrip;
    public final String supportStripText;
    public final boolean showErrorState;
    public final String errorTitle;
    public final String errorMessage;
    public final boolean isLoading;
    public final boolean hasData;
    public final boolean isHighRisk;
    public final boolean isBaselineReady;
    public final boolean showEmptyState;

    // ── NEW: Efficacy Observation fields ─────────────────────────────────
    public final String efficacySummaryText;
    public final String mostEffectiveCategory;
    public final int tasksInObservationWindow;

    // ── NEW: Worker Progress & Error Tracking ────────────────────────────
    /** Human-readable progress string, e.g. "Classifying risk..." or null if idle. */
    public final String progressSummaryText;
    /** True if any background AI worker is currently running. */
    public final boolean isAnalyzing;
    /** Number of background workers currently active (RUNNING or ENQUEUED). */
    public final int activeWorkerCount;
    /** Number of errors logged in the last 24 hours (for badge/indicator). */
    public final int recentErrorCount;

    // ── NEW: Baseline Deviation for RiskGaugeView / BaselineDeviationBar ─
    /** Deviation from behavioral baseline, -100 to +100 (0 = at baseline). */
    public final float baselineDeviationPercent;

    // ─────────────────────────────────────────────────────────────────────
    // BUILDER — the primary construction API going forward
    // ─────────────────────────────────────────────────────────────────────

    /** Primary way to create a HomeScreenState. */
    public static Builder builder() {
        return new Builder();
    }

    /** Private constructor — only the Builder may call this. */
    private HomeScreenState(Builder b) {
        this.greetingText = b.greetingText;
        this.dateText = b.dateText;
        this.wellnessLabel = b.wellnessLabel;
        this.riskIndex = b.riskIndex;
        this.riskHistory = safe(b.riskHistory);
        this.screenTimeDeviation = b.screenTimeDeviation;
        this.riskSummary = b.riskSummary;
        this.baselineComparisonText = b.baselineComparisonText;
        this.missionTitle = b.missionTitle;

        // Mission steps + items derive from each other if one is null
        if (b.missionStepItems != null) {
            this.missionStepItems = new ArrayList<>(b.missionStepItems);
            this.missionSteps = b.missionSteps != null
                    ? new ArrayList<>(b.missionSteps)
                    : extractMissionStepTitles(this.missionStepItems);
        } else if (b.missionSteps != null) {
            this.missionSteps = new ArrayList<>(b.missionSteps);
            this.missionStepItems = createMissionStepItems(this.missionSteps);
        } else {
            this.missionSteps = Collections.emptyList();
            this.missionStepItems = Collections.emptyList();
        }

        this.missionProgressText = b.missionProgressText;
        this.missionProgressPercent = b.missionProgressPercent;
        this.primaryActionLabel = b.primaryActionLabel;
        this.primaryActionType = b.primaryActionType;
        this.warningItems = safe(b.warningItems);
        this.warningCardItems = safe(b.warningCardItems);
        this.aiInsightItems = safe(b.aiInsightItems);
        this.patternRadarCard = b.patternRadarCard;
        this.focusWindowCard = b.focusWindowCard;
        this.forecastCard = b.forecastCard;
        this.hasCheckedInToday = b.hasCheckedInToday;
        this.hasJournalEntryToday = b.hasJournalEntryToday;
        this.hasExerciseToday = b.hasExerciseToday;
        this.nextBestActionTitle = b.nextBestActionTitle;
        this.nextBestActionReason = b.nextBestActionReason;
        this.nextBestActionETA = b.nextBestActionETA;
        this.showSupportStrip = b.showSupportStrip;
        this.supportStripText = b.supportStripText;
        this.showErrorState = b.showErrorState;
        this.errorTitle = b.errorTitle;
        this.errorMessage = b.errorMessage;
        this.isLoading = b.isLoading;
        this.hasData = b.hasData;
        this.isHighRisk = b.isHighRisk;
        this.isBaselineReady = b.isBaselineReady;
        this.showEmptyState = b.showEmptyState;

        // Efficacy fields
        this.efficacySummaryText = b.efficacySummaryText;
        this.mostEffectiveCategory = b.mostEffectiveCategory;
        this.tasksInObservationWindow = b.tasksInObservationWindow;

        // Worker progress & error fields
        this.progressSummaryText = b.progressSummaryText;
        this.isAnalyzing = b.isAnalyzing;
        this.activeWorkerCount = b.activeWorkerCount;
        this.recentErrorCount = b.recentErrorCount;

        // Baseline deviation
        this.baselineDeviationPercent = b.baselineDeviationPercent;
    }

    // ─────────────────────────────────────────────────────────────────────
    // LEGACY CONSTRUCTOR — kept for backward compatibility during migration
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @deprecated Use {@link #builder()} instead. This 37-parameter constructor
     *             is preserved solely so existing call sites compile.
     */
    @Deprecated
    public HomeScreenState(
            String greetingText,
            String dateText,
            String wellnessLabel,
            int riskIndex,
            List<Float> riskHistory,
            double screenTimeDeviation,
            String riskSummary,
            String baselineComparisonText,
            String missionTitle,
            List<String> missionSteps,
            List<MissionStepItem> missionStepItems,
            String missionProgressText,
            int missionProgressPercent,
            String primaryActionLabel,
            String primaryActionType,
            List<String> warningItems,
            List<WarningCardItem> warningCardItems,
            List<InsightItem> aiInsightItems,
            PatternRadarCard patternRadarCard,
            FocusWindowCard focusWindowCard,
            ForecastCard forecastCard,
            boolean hasCheckedInToday,
            boolean hasJournalEntryToday,
            boolean hasExerciseToday,
            String nextBestActionTitle,
            String nextBestActionReason,
            String nextBestActionETA,
            boolean showSupportStrip,
            String supportStripText,
            boolean showErrorState,
            String errorTitle,
            String errorMessage,
            boolean isLoading,
            boolean hasData,
            boolean isHighRisk,
            boolean isBaselineReady,
            boolean showEmptyState
    ) {
        this.greetingText = greetingText;
        this.dateText = dateText;
        this.wellnessLabel = wellnessLabel;
        this.riskIndex = riskIndex;
        this.riskHistory = riskHistory == null ? Collections.emptyList() : new ArrayList<>(riskHistory);
        this.screenTimeDeviation = screenTimeDeviation;
        this.riskSummary = riskSummary;
        this.baselineComparisonText = baselineComparisonText;
        this.missionTitle = missionTitle;
        this.missionSteps = missionSteps == null
                ? extractMissionStepTitles(missionStepItems)
                : new ArrayList<>(missionSteps);
        this.missionStepItems = missionStepItems == null
                ? createMissionStepItems(this.missionSteps)
                : new ArrayList<>(missionStepItems);
        this.missionProgressText = missionProgressText;
        this.missionProgressPercent = missionProgressPercent;
        this.primaryActionLabel = primaryActionLabel;
        this.primaryActionType = primaryActionType;
        this.warningItems = warningItems == null ? Collections.emptyList() : new ArrayList<>(warningItems);
        this.warningCardItems = warningCardItems == null ? Collections.emptyList() : new ArrayList<>(warningCardItems);
        this.aiInsightItems = aiInsightItems == null ? Collections.emptyList() : new ArrayList<>(aiInsightItems);
        this.patternRadarCard = patternRadarCard;
        this.focusWindowCard = focusWindowCard;
        this.forecastCard = forecastCard;
        this.hasCheckedInToday = hasCheckedInToday;
        this.hasJournalEntryToday = hasJournalEntryToday;
        this.hasExerciseToday = hasExerciseToday;
        this.nextBestActionTitle = nextBestActionTitle;
        this.nextBestActionReason = nextBestActionReason;
        this.nextBestActionETA = nextBestActionETA;
        this.showSupportStrip = showSupportStrip;
        this.supportStripText = supportStripText;
        this.showErrorState = showErrorState;
        this.errorTitle = errorTitle;
        this.errorMessage = errorMessage;
        this.isLoading = isLoading;
        this.hasData = hasData;
        this.isHighRisk = isHighRisk;
        this.isBaselineReady = isBaselineReady;
        this.showEmptyState = showEmptyState;

        // Legacy constructor defaults for new fields
        this.efficacySummaryText = null;
        this.mostEffectiveCategory = null;
        this.tasksInObservationWindow = 0;

        // Worker progress defaults
        this.progressSummaryText = null;
        this.isAnalyzing = false;
        this.activeWorkerCount = 0;
        this.recentErrorCount = 0;

        // Baseline deviation default
        this.baselineDeviationPercent = 0f;
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOADING FACTORY — convenience for initial/loading state
    // ─────────────────────────────────────────────────────────────────────

    public static HomeScreenState loading(String greetingText, String dateText) {
        List<String> placeholderSteps = new ArrayList<>();
        placeholderSteps.add("Preparing your reset");
        placeholderSteps.add("Reading your current patterns");
        placeholderSteps.add("Building today's priority");
        List<MissionStepItem> placeholderStepItems = createMissionStepItems(placeholderSteps);

        return builder()
                .greetingText(greetingText)
                .dateText(dateText)
                .wellnessLabel("Preparing")
                .riskIndex(0)
                .riskHistory(Collections.singletonList(0f))
                .screenTimeDeviation(Double.NaN)
                .riskSummary("Loading your daily reset...")
                .baselineComparisonText("A moment while MindTrace gathers today's signals.")
                .missionTitle("Preparing today's mission...")
                .missionSteps(placeholderSteps)
                .missionStepItems(placeholderStepItems)
                .missionProgressText("Loading")
                .missionProgressPercent(0)
                .primaryActionLabel("Preparing your reset...")
                .primaryActionType(ACTION_NONE)
                .patternRadarCard(new PatternRadarCard(
                        "Live Pattern Radar",
                        "Reading your behavior stream...",
                        Collections.emptyList(),
                        "Waiting for enough signal",
                        false
                ))
                .focusWindowCard(new FocusWindowCard(
                        "Building your momentum window",
                        "Preparing next block",
                        "MindTrace is finding the next clean stretch of the day for you.",
                        "Preparing",
                        Collections.emptyList(),
                        "Just a moment",
                        ACTION_NONE,
                        false
                ))
                .nextBestActionTitle("Preparing your next action...")
                .nextBestActionReason("We are building the strongest next step from your current state.")
                .nextBestActionETA("Just a moment")
                .isLoading(true)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────────────────────────────

    private static <T> List<T> safe(List<T> list) {
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    private static List<String> extractMissionStepTitles(List<MissionStepItem> missionStepItems) {
        if (missionStepItems == null || missionStepItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> titles = new ArrayList<>(missionStepItems.size());
        for (MissionStepItem item : missionStepItems) {
            if (item != null && item.text != null) {
                titles.add(item.text);
            }
        }
        return titles;
    }

    private static List<MissionStepItem> createMissionStepItems(List<String> missionSteps) {
        if (missionSteps == null || missionSteps.isEmpty()) {
            return Collections.emptyList();
        }
        List<MissionStepItem> items = new ArrayList<>(missionSteps.size());
        for (String missionStep : missionSteps) {
            if (missionStep == null) {
                continue;
            }
            items.add(new MissionStepItem(0, missionStep, false, false));
        }
        return items;
    }

    // ─────────────────────────────────────────────────────────────────────
    // BUILDER
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link HomeScreenState}.
     *
     * <p>Every setter returns {@code this} so calls can be chained.
     * Only {@link #greetingText} and {@link #dateText} are conceptually
     * required; everything else defaults to safe empty/zero/false values.</p>
     */
    public static final class Builder {
        // Header
        private String greetingText = "";
        private String dateText = "";
        private String wellnessLabel = "";
        private int riskIndex = 0;
        private List<Float> riskHistory = null;
        private double screenTimeDeviation = 0.0;
        private String riskSummary = "";
        private String baselineComparisonText = "";

        // Mission
        private String missionTitle = "";
        private List<String> missionSteps = null;
        private List<MissionStepItem> missionStepItems = null;
        private String missionProgressText = "";
        private int missionProgressPercent = 0;

        // Actions
        private String primaryActionLabel = "";
        private String primaryActionType = ACTION_NONE;

        // Warnings & Insights
        private List<String> warningItems = null;
        private List<WarningCardItem> warningCardItems = null;
        private List<InsightItem> aiInsightItems = null;

        // Cards
        private PatternRadarCard patternRadarCard = null;
        private FocusWindowCard focusWindowCard = null;
        private ForecastCard forecastCard = null;

        // Completion flags
        private boolean hasCheckedInToday = false;
        private boolean hasJournalEntryToday = false;
        private boolean hasExerciseToday = false;

        // Next action
        private String nextBestActionTitle = "";
        private String nextBestActionReason = "";
        private String nextBestActionETA = "";

        // Support
        private boolean showSupportStrip = false;
        private String supportStripText = "";

        // Error
        private boolean showErrorState = false;
        private String errorTitle = "";
        private String errorMessage = "";

        // State flags
        private boolean isLoading = false;
        private boolean hasData = false;
        private boolean isHighRisk = false;
        private boolean isBaselineReady = false;
        private boolean showEmptyState = false;

        // Efficacy
        private String efficacySummaryText = null;
        private String mostEffectiveCategory = null;
        private int tasksInObservationWindow = 0;

        // Worker Progress & Errors
        private String progressSummaryText = null;
        private boolean isAnalyzing = false;
        private int activeWorkerCount = 0;
        private int recentErrorCount = 0;

        // Baseline Deviation
        private float baselineDeviationPercent = 0f;

        private Builder() {}

        // ── Header setters ──────────────────────────────────────────

        public Builder greetingText(String v) { this.greetingText = v; return this; }
        public Builder dateText(String v) { this.dateText = v; return this; }
        public Builder wellnessLabel(String v) { this.wellnessLabel = v; return this; }
        public Builder riskIndex(int v) { this.riskIndex = v; return this; }
        public Builder riskHistory(List<Float> v) { this.riskHistory = v; return this; }
        public Builder screenTimeDeviation(double v) { this.screenTimeDeviation = v; return this; }
        public Builder riskSummary(String v) { this.riskSummary = v; return this; }
        public Builder baselineComparisonText(String v) { this.baselineComparisonText = v; return this; }

        // ── Mission setters ─────────────────────────────────────────

        public Builder missionTitle(String v) { this.missionTitle = v; return this; }
        public Builder missionSteps(List<String> v) { this.missionSteps = v; return this; }
        public Builder missionStepItems(List<MissionStepItem> v) { this.missionStepItems = v; return this; }
        public Builder missionProgressText(String v) { this.missionProgressText = v; return this; }
        public Builder missionProgressPercent(int v) { this.missionProgressPercent = v; return this; }

        // ── Action setters ──────────────────────────────────────────

        public Builder primaryActionLabel(String v) { this.primaryActionLabel = v; return this; }
        public Builder primaryActionType(String v) { this.primaryActionType = v; return this; }

        // ── Warning & Insight setters ───────────────────────────────

        public Builder warningItems(List<String> v) { this.warningItems = v; return this; }
        public Builder warningCardItems(List<WarningCardItem> v) { this.warningCardItems = v; return this; }
        public Builder aiInsightItems(List<InsightItem> v) { this.aiInsightItems = v; return this; }

        // ── Card setters ────────────────────────────────────────────

        public Builder patternRadarCard(PatternRadarCard v) { this.patternRadarCard = v; return this; }
        public Builder focusWindowCard(FocusWindowCard v) { this.focusWindowCard = v; return this; }
        public Builder forecastCard(ForecastCard v) { this.forecastCard = v; return this; }

        // ── Completion flag setters ─────────────────────────────────

        public Builder hasCheckedInToday(boolean v) { this.hasCheckedInToday = v; return this; }
        public Builder hasJournalEntryToday(boolean v) { this.hasJournalEntryToday = v; return this; }
        public Builder hasExerciseToday(boolean v) { this.hasExerciseToday = v; return this; }

        // ── Next action setters ─────────────────────────────────────

        public Builder nextBestActionTitle(String v) { this.nextBestActionTitle = v; return this; }
        public Builder nextBestActionReason(String v) { this.nextBestActionReason = v; return this; }
        public Builder nextBestActionETA(String v) { this.nextBestActionETA = v; return this; }

        // ── Support setters ─────────────────────────────────────────

        public Builder showSupportStrip(boolean v) { this.showSupportStrip = v; return this; }
        public Builder supportStripText(String v) { this.supportStripText = v; return this; }

        // ── Error setters ───────────────────────────────────────────

        public Builder showErrorState(boolean v) { this.showErrorState = v; return this; }
        public Builder errorTitle(String v) { this.errorTitle = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }

        // ── State flag setters ──────────────────────────────────────

        public Builder isLoading(boolean v) { this.isLoading = v; return this; }
        public Builder hasData(boolean v) { this.hasData = v; return this; }
        public Builder isHighRisk(boolean v) { this.isHighRisk = v; return this; }
        public Builder isBaselineReady(boolean v) { this.isBaselineReady = v; return this; }
        public Builder showEmptyState(boolean v) { this.showEmptyState = v; return this; }

        // ── Efficacy setters ────────────────────────────────────────

        public Builder efficacySummaryText(String v) { this.efficacySummaryText = v; return this; }
        public Builder mostEffectiveCategory(String v) { this.mostEffectiveCategory = v; return this; }
        public Builder tasksInObservationWindow(int v) { this.tasksInObservationWindow = v; return this; }

        // ── Worker Progress & Error setters ──────────────────────────

        public Builder progressSummaryText(String v) { this.progressSummaryText = v; return this; }
        public Builder isAnalyzing(boolean v) { this.isAnalyzing = v; return this; }
        public Builder activeWorkerCount(int v) { this.activeWorkerCount = v; return this; }
        public Builder recentErrorCount(int v) { this.recentErrorCount = v; return this; }

        // ── Baseline Deviation setter ────────────────────────────────

        public Builder baselineDeviationPercent(float v) { this.baselineDeviationPercent = v; return this; }

        /** Build the immutable state snapshot. */
        public HomeScreenState build() {
            return new HomeScreenState(this);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INNER MODEL CLASSES (unchanged)
    // ─────────────────────────────────────────────────────────────────────

    public static final class MissionStepItem {
        public final int taskId;
        public final String text;
        public final boolean isCompleted;
        public final boolean isInteractive;
        public final String categoryKey;
        public final String whyText;

        public MissionStepItem(int taskId, String text, boolean isCompleted, boolean isInteractive) {
            this(taskId, text, isCompleted, isInteractive, "general", null);
        }

        public MissionStepItem(
                int taskId,
                String text,
                boolean isCompleted,
                boolean isInteractive,
                String categoryKey,
                String whyText
        ) {
            this.taskId = taskId;
            this.text = text;
            this.isCompleted = isCompleted;
            this.isInteractive = isInteractive;
            this.categoryKey = categoryKey == null ? "general" : categoryKey;
            this.whyText = whyText;
        }
    }

    public static final class WarningCardItem {
        public static final int SEVERITY_LOW = 1;
        public static final int SEVERITY_MEDIUM = 2;
        public static final int SEVERITY_HIGH = 3;

        public final String title;
        public final String detailText;
        public final int severity;

        public WarningCardItem(String title, String detailText, int severity) {
            this.title = title;
            this.detailText = detailText;
            this.severity = severity;
        }
    }

    public static final class InsightItem {
        public final String headline;
        public final String body;
        public final List<String> reasonItems;
        public final String actionLabel;
        public final String actionType;
        public final boolean anomaly;

        public InsightItem(
                String headline,
                String body,
                List<String> reasonItems,
                String actionLabel,
                String actionType,
                boolean anomaly
        ) {
            this.headline = headline;
            this.body = body;
            this.reasonItems = reasonItems == null ? Collections.emptyList() : new ArrayList<>(reasonItems);
            this.actionLabel = actionLabel;
            this.actionType = actionType;
            this.anomaly = anomaly;
        }
    }

    public static final class PatternRadarCard {
        public final String title;
        public final String summary;
        public final List<String> signalPills;
        public final String footerLabel;
        public final boolean urgent;

        public PatternRadarCard(
                String title,
                String summary,
                List<String> signalPills,
                String footerLabel,
                boolean urgent
        ) {
            this.title = title;
            this.summary = summary;
            this.signalPills = signalPills == null ? Collections.emptyList() : new ArrayList<>(signalPills);
            this.footerLabel = footerLabel;
            this.urgent = urgent;
        }
    }

    public static final class FocusWindowCard {
        public final String title;
        public final String windowLabel;
        public final String coachText;
        public final String badgeText;
        public final List<String> ritualItems;
        public final String actionLabel;
        public final String actionType;
        public final boolean urgent;

        public FocusWindowCard(
                String title,
                String windowLabel,
                String coachText,
                String badgeText,
                List<String> ritualItems,
                String actionLabel,
                String actionType,
                boolean urgent
        ) {
            this.title = title;
            this.windowLabel = windowLabel;
            this.coachText = coachText;
            this.badgeText = badgeText;
            this.ritualItems = ritualItems == null ? Collections.emptyList() : new ArrayList<>(ritualItems);
            this.actionLabel = actionLabel;
            this.actionType = actionType;
            this.urgent = urgent;
        }
    }

    public static final class ForecastCard {
        public final String emoji;
        public final String label;
        public final String summary;
        public final int confidencePercent;
        public final String actionTip;
        public final List<Float> trendPoints;
        public final boolean highRiskTomorrow;
        public final int predictedRisk;
        public final int deltaFromToday;
        public final String driverLabel;

        public ForecastCard(
                String emoji,
                String label,
                String summary,
                int confidencePercent,
                String actionTip,
                List<Float> trendPoints,
                boolean highRiskTomorrow,
                int predictedRisk,
                int deltaFromToday,
                String driverLabel
        ) {
            this.emoji = emoji;
            this.label = label;
            this.summary = summary;
            this.confidencePercent = confidencePercent;
            this.actionTip = actionTip;
            this.trendPoints = trendPoints == null ? Collections.emptyList() : new ArrayList<>(trendPoints);
            this.highRiskTomorrow = highRiskTomorrow;
            this.predictedRisk = predictedRisk;
            this.deltaFromToday = deltaFromToday;
            this.driverLabel = driverLabel;
        }
    }
}
