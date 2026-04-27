package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Aggregated efficacy metrics for the Usage dashboard.
 *
 * <p>This POJO carries the computed sentiment-enhanced efficacy data
 * from {@link InterventionEngine} through the ViewModel to the UI layer.
 * It contains both per-category efficacy scores and global summary strings
 * so the UI can render the Efficacy Pipeline card without touching the
 * AI layer directly.</p>
 *
 * <h3>Data sources:</h3>
 * <ul>
 *   <li>{@link InterventionEngine#buildSentimentEnhancedEfficacyMap} → categoryScores</li>
 *   <li>{@link InterventionEngine#getSentimentEnhancedSummary} → summaryLines</li>
 *   <li>{@link com.mindtrace.ai.database.dao.TaskDao} → measuredCount, overallAvg</li>
 * </ul>
 *
 * @see InterventionEngine
 */
public class EfficacyMetrics {

    /** Per-category sentiment-adjusted efficacy scores. Key = linkedRiskCategory. */
    @NonNull
    public final Map<String, Float> categoryScores;

    /** Human-readable per-category summaries (e.g., "stress anxiety: highly effective…"). */
    @NonNull
    public final String[] summaryLines;

    /** Total number of tasks with completed efficacy measurements. */
    public final int measuredCount;

    /** Overall average efficacy score across all measured tasks. */
    public final float overallAvg;

    /** Name of the most efficacious risk category, or null if no data. */
    @Nullable
    public final String bestCategory;

    /** Number of tasks currently in their active observation window. */
    public final int observingCount;

    public EfficacyMetrics(
            @NonNull Map<String, Float> categoryScores,
            @NonNull String[] summaryLines,
            int measuredCount,
            float overallAvg,
            @Nullable String bestCategory,
            int observingCount) {
        this.categoryScores = categoryScores;
        this.summaryLines = summaryLines;
        this.measuredCount = measuredCount;
        this.overallAvg = overallAvg;
        this.bestCategory = bestCategory;
        this.observingCount = observingCount;
    }

    /** Factory for the "no data yet" state. */
    @NonNull
    public static EfficacyMetrics empty() {
        return new EfficacyMetrics(
                Collections.emptyMap(),
                new String[]{"Complete tasks and rate your mood to see efficacy insights."},
                0,
                0f,
                null,
                0
        );
    }

    /** Whether there is enough data to display efficacy visualizations. */
    public boolean hasData() {
        return measuredCount > 0;
    }

    /** User-facing effectiveness label for the overall average. */
    @NonNull
    public String getOverallLabel() {
        if (overallAvg >= 0.10f) return "Highly Effective";
        if (overallAvg >= 0.03f) return "Moderately Effective";
        if (overallAvg >= -0.03f) return "Neutral";
        return "Needs Improvement";
    }

    /**
     * Returns a colour hint for the overall score (for tinting UI elements).
     * Values: "positive", "neutral", "negative"
     */
    @NonNull
    public String getOverallColorHint() {
        if (overallAvg >= 0.03f) return "positive";
        if (overallAvg >= -0.03f) return "neutral";
        return "negative";
    }
}
