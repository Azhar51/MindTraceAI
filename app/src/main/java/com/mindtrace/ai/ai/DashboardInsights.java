package com.mindtrace.ai.ai;

import com.mindtrace.ai.database.entity.RiskClassification;
import java.util.List;

public class DashboardInsights {
    public enum RiskLevel {
        LOW,
        MODERATE,
        HIGH
    }

    public final RiskLevel riskLevel;
    public final String riskLabel;
    public final MentalStateClassifier.State currentState;
    public final String stateLabel;
    public final String primaryInsight;
    public final String secondaryInsight;
    public final String recommendation;
    public final String explanationText;
    public final String reasonSummary;
    public final String weeklyPatternSummary;
    public final String nextBestAction;
    public final boolean personalized;
    public final double personalizedRiskScore;
    public final String personalizationSummary;
    public final String personalizedComparisons;
    public final String behaviorSummaryLabel;
    public final String behaviorExplanation;
    public final String behaviorTrendSummary;
    public final List<String> reasonItems;
    public final boolean aiEnhanced;
    public final String aiSummary;
    public final String anomalySummary;
    public final String featureSummary;
    public final String topAppName;
    public final long topAppUsageMillis;
    public final boolean hasOverusedApp;
    public final String overuseMessage;
    public final int fulfillmentScore;
    public final boolean supportRecommended;

    // ── 3.I.7: MultiModal AI classification fields ──
    public final RiskClassification classification;
    public final List<String> classificationReasons;

    // ── Phase 6: Longitudinal trajectory fields ──
    /** Human-readable trajectory direction, e.g. "gradually_improving", "stable". */
    public final String trajectoryLabel;
    /** One-sentence summary of the 7-day trajectory. */
    public final String trajectorySummary;

    public DashboardInsights(
            RiskLevel riskLevel,
            String riskLabel,
            MentalStateClassifier.State currentState,
            String stateLabel,
            String primaryInsight,
            String secondaryInsight,
            String recommendation,
            String explanationText,
            String reasonSummary,
            String weeklyPatternSummary,
            String nextBestAction,
            boolean personalized,
            double personalizedRiskScore,
            String personalizationSummary,
            String personalizedComparisons,
            String behaviorSummaryLabel,
            String behaviorExplanation,
            String behaviorTrendSummary,
            List<String> reasonItems,
            boolean aiEnhanced,
            String aiSummary,
            String anomalySummary,
            String featureSummary,
            String topAppName,
            long topAppUsageMillis,
            boolean hasOverusedApp,
            String overuseMessage,
            int fulfillmentScore,
            boolean supportRecommended
    ) {
        this(riskLevel, riskLabel, currentState, stateLabel, primaryInsight,
                secondaryInsight, recommendation, explanationText, reasonSummary,
                weeklyPatternSummary, nextBestAction, personalized, personalizedRiskScore,
                personalizationSummary, personalizedComparisons, behaviorSummaryLabel,
                behaviorExplanation, behaviorTrendSummary, reasonItems, aiEnhanced,
                aiSummary, anomalySummary, featureSummary, topAppName, topAppUsageMillis,
                hasOverusedApp, overuseMessage, fulfillmentScore, supportRecommended,
                null, null, null, null);
    }

    private DashboardInsights(
            RiskLevel riskLevel, String riskLabel,
            MentalStateClassifier.State currentState, String stateLabel,
            String primaryInsight, String secondaryInsight, String recommendation,
            String explanationText, String reasonSummary, String weeklyPatternSummary,
            String nextBestAction, boolean personalized, double personalizedRiskScore,
            String personalizationSummary, String personalizedComparisons,
            String behaviorSummaryLabel, String behaviorExplanation,
            String behaviorTrendSummary, List<String> reasonItems,
            boolean aiEnhanced, String aiSummary, String anomalySummary,
            String featureSummary, String topAppName, long topAppUsageMillis,
            boolean hasOverusedApp, String overuseMessage,
            int fulfillmentScore, boolean supportRecommended,
            RiskClassification classification, List<String> classificationReasons,
            String trajectoryLabel, String trajectorySummary
    ) {
        this.riskLevel = riskLevel;
        this.riskLabel = riskLabel;
        this.currentState = currentState;
        this.stateLabel = stateLabel;
        this.primaryInsight = primaryInsight;
        this.secondaryInsight = secondaryInsight;
        this.recommendation = recommendation;
        this.explanationText = explanationText;
        this.reasonSummary = reasonSummary;
        this.weeklyPatternSummary = weeklyPatternSummary;
        this.nextBestAction = nextBestAction;
        this.personalized = personalized;
        this.personalizedRiskScore = personalizedRiskScore;
        this.personalizationSummary = personalizationSummary;
        this.personalizedComparisons = personalizedComparisons;
        this.behaviorSummaryLabel = behaviorSummaryLabel;
        this.behaviorExplanation = behaviorExplanation;
        this.behaviorTrendSummary = behaviorTrendSummary;
        this.reasonItems = reasonItems;
        this.aiEnhanced = aiEnhanced;
        this.aiSummary = aiSummary;
        this.anomalySummary = anomalySummary;
        this.featureSummary = featureSummary;
        this.topAppName = topAppName;
        this.topAppUsageMillis = topAppUsageMillis;
        this.hasOverusedApp = hasOverusedApp;
        this.overuseMessage = overuseMessage;
        this.fulfillmentScore = fulfillmentScore;
        this.supportRecommended = supportRecommended;
        this.classification = classification;
        this.classificationReasons = classificationReasons;
        this.trajectoryLabel = trajectoryLabel;
        this.trajectorySummary = trajectorySummary;
    }

    /**
     * Return a copy enriched with the MultiModal classification result.
     * Overrides riskLevel/riskLabel if the classifier ran successfully.
     */
    public DashboardInsights withClassification(
            RiskClassification rc, List<String> reasons) {
        if (rc == null) return this;
        RiskLevel newLevel;
        String newLabel;
        float s = rc.overallRiskScore;
        if (s >= 0.65f) { newLevel = RiskLevel.HIGH; newLabel = "HIGH RISK"; }
        else if (s >= 0.35f) { newLevel = RiskLevel.MODERATE; newLabel = "MODERATE"; }
        else { newLevel = RiskLevel.LOW; newLabel = "HEALTHY"; }

        return new DashboardInsights(
                newLevel, newLabel, currentState, stateLabel,
                primaryInsight, secondaryInsight, recommendation,
                explanationText, reasonSummary, weeklyPatternSummary,
                nextBestAction, personalized, rc.overallRiskScore,
                personalizationSummary, personalizedComparisons,
                behaviorSummaryLabel, behaviorExplanation, behaviorTrendSummary,
                reasonItems, aiEnhanced, aiSummary, anomalySummary, featureSummary,
                topAppName, topAppUsageMillis, hasOverusedApp, overuseMessage,
                fulfillmentScore, supportRecommended || rc.crisisFlag,
                rc, reasons,
                trajectoryLabel, trajectorySummary
        );
    }

    /**
     * Return a copy enriched with longitudinal trajectory data.
     * Called from InsightEngine after ClassificationTrendAnalyzer runs.
     */
    public DashboardInsights withTrajectory(String label, String summary) {
        if (label == null && summary == null) return this;
        return new DashboardInsights(
                riskLevel, riskLabel, currentState, stateLabel,
                primaryInsight, secondaryInsight, recommendation,
                explanationText, reasonSummary, weeklyPatternSummary,
                nextBestAction, personalized, personalizedRiskScore,
                personalizationSummary, personalizedComparisons,
                behaviorSummaryLabel, behaviorExplanation, behaviorTrendSummary,
                reasonItems, aiEnhanced, aiSummary, anomalySummary, featureSummary,
                topAppName, topAppUsageMillis, hasOverusedApp, overuseMessage,
                fulfillmentScore, supportRecommended,
                classification, classificationReasons,
                label, summary
        );
    }

    /** Convenience: does this insight have a MultiModal classification? */
    public boolean hasClassification() {
        return classification != null;
    }

    /** Convenience: does this insight carry trajectory data? */
    public boolean hasTrajectory() {
        return trajectoryLabel != null;
    }
}
