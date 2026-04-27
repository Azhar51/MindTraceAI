package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.List;
import java.util.Locale;

/**
 * Trend predictor — uses linear regression to determine whether risk scores,
 * mood, and behavioral metrics are improving, stable, or worsening over time.
 *
 * <h3>Key Capabilities:</h3>
 * <ul>
 *   <li><b>Risk score trend</b> — overall + per-category (6 dimensions)</li>
 *   <li><b>Screen time trend</b> — from DailyUsage history</li>
 *   <li><b>Mood trend</b> — from QuestionnaireResponse history</li>
 *   <li><b>Sleep trend</b> — from questionnaire sleep hours</li>
 *   <li><b>Proactive alerts</b> — enables interventions BEFORE a crisis</li>
 * </ul>
 *
 * <h3>Method:</h3>
 * <p>Simple linear regression (least squares) over the last 7 data points.
 * The slope determines the trend direction and magnitude.</p>
 *
 * @see AnomalyDetector
 * @see CorrelationEngine
 */
public class TrendPredictor {

    // ═══════════════════════════════════════════════════════════════════
    // TREND ENUM
    // ═══════════════════════════════════════════════════════════════════

    public enum Trend {
        IMPROVING("↓ Improving", "#4ADE80", -2),
        SLIGHTLY_IMPROVING("↓ Slight improvement", "#86EFAC", -1),
        STABLE("→ Stable", "#8896B0", 0),
        SLIGHTLY_WORSENING("↑ Slight increase", "#F5A623", 1),
        WORSENING("↑ Rising", "#FF6B6B", 2);

        public final String label;
        public final String color;
        public final int severity; // -2 best → +2 worst

        Trend(String label, String color, int severity) {
            this.label = label;
            this.color = color;
            this.severity = severity;
        }

        public boolean isWorsening() { return severity > 0; }
        public boolean isImproving() { return severity < 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TREND REPORT
    // ═══════════════════════════════════════════════════════════════════

    public static class TrendReport {
        // Overall
        public Trend riskTrend = Trend.STABLE;
        public String status = "READY";

        // Per-category risk trends
        public Trend addictionTrend = Trend.STABLE;
        public Trend stressTrend = Trend.STABLE;
        public Trend fulfilmentTrend = Trend.STABLE;
        public Trend depressionTrend = Trend.STABLE;
        public Trend isolationTrend = Trend.STABLE;
        public Trend sleepDisruptionTrend = Trend.STABLE;

        // Behavioral trends
        public Trend screenTimeTrend = Trend.STABLE;
        public Trend moodTrend = Trend.STABLE;
        public Trend sleepTrend = Trend.STABLE;

        // Slope values (for detailed UI)
        public float riskSlope = 0f;
        public float screenTimeSlope = 0f;
        public float moodSlope = 0f;

        /** Count of worsening categories. */
        public int worseningCount() {
            int count = 0;
            if (addictionTrend.isWorsening()) count++;
            if (stressTrend.isWorsening()) count++;
            if (fulfilmentTrend.isWorsening()) count++;
            if (depressionTrend.isWorsening()) count++;
            if (isolationTrend.isWorsening()) count++;
            if (sleepDisruptionTrend.isWorsening()) count++;
            return count;
        }

        /** Count of improving categories. */
        public int improvingCount() {
            int count = 0;
            if (addictionTrend.isImproving()) count++;
            if (stressTrend.isImproving()) count++;
            if (fulfilmentTrend.isImproving()) count++;
            if (depressionTrend.isImproving()) count++;
            if (isolationTrend.isImproving()) count++;
            if (sleepDisruptionTrend.isImproving()) count++;
            return count;
        }

        /** Generate a one-line human-readable summary. */
        @NonNull
        public String toSummary() {
            if ("INSUFFICIENT_DATA".equals(status)) {
                return "Not enough data for trend analysis yet.";
            }
            int worsening = worseningCount();
            int improving = improvingCount();

            if (worsening == 0 && improving == 0) {
                return "All indicators are stable this week.";
            }
            if (worsening == 0) {
                return improving + " indicator" + (improving > 1 ? "s" : "") +
                        " improving — keep it up!";
            }
            if (improving == 0) {
                return worsening + " indicator" + (worsening > 1 ? "s" : "") +
                        " trending up — let's work on that.";
            }
            return improving + " improving, " + worsening +
                    " rising — mixed signals this week.";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIMARY API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Analyze trends across all available data sources.
     *
     * @param classificationHistory risk classifications (newest first)
     * @param usageHistory          daily usage records (newest first)
     * @param moodHistory           questionnaire responses (newest first)
     * @return trend report with per-category and aggregate trends
     */
    @NonNull
    public TrendReport analyzeTrends(
            @Nullable List<RiskClassification> classificationHistory,
            @Nullable List<DailyUsage> usageHistory,
            @Nullable List<QuestionnaireResponse> moodHistory) {

        TrendReport report = new TrendReport();

        // Need at least 3 data points for meaningful trend
        if (classificationHistory == null || classificationHistory.size() < 3) {
            report.status = "INSUFFICIENT_DATA";
            return report;
        }

        // ── Risk score trends ──
        float[] riskScores = extractFloats(classificationHistory,
                rc -> rc.overallRiskScore);
        report.riskTrend = computeTrend(riskScores);
        report.riskSlope = computeSlope(riskScores);

        // Per-category
        report.addictionTrend = computeTrend(extractFloats(
                classificationHistory, rc -> rc.digitalAddictionScore));
        report.stressTrend = computeTrend(extractFloats(
                classificationHistory, rc -> rc.stressAnxietyScore));
        report.fulfilmentTrend = computeTrend(extractFloats(
                classificationHistory, rc -> rc.lowFulfilmentScore));
        report.depressionTrend = computeTrend(extractFloats(
                classificationHistory, rc -> rc.depressionRiskScore));
        report.isolationTrend = computeTrend(extractFloats(
                classificationHistory, rc -> rc.socialIsolationScore));
        report.sleepDisruptionTrend = computeTrend(extractFloats(
                classificationHistory, rc -> rc.sleepDisruptionScore));

        // ── Screen time trend ──
        if (usageHistory != null && usageHistory.size() >= 3) {
            float[] screenTimes = new float[usageHistory.size()];
            for (int i = 0; i < usageHistory.size(); i++) {
                screenTimes[i] = usageHistory.get(i).screenTimeMillis;
            }
            report.screenTimeTrend = computeTrend(screenTimes);
            report.screenTimeSlope = computeSlope(screenTimes);
        }

        // ── Mood trend ──
        if (moodHistory != null && moodHistory.size() >= 3) {
            float[] moods = new float[moodHistory.size()];
            for (int i = 0; i < moodHistory.size(); i++) {
                moods[i] = mapMoodToFloat(moodHistory.get(i).mood);
            }
            report.moodTrend = computeMoodTrend(moods);
            report.moodSlope = computeSlope(moods);
        }

        // ── Sleep trend ──
        if (moodHistory != null && moodHistory.size() >= 3) {
            float[] sleepHours = new float[moodHistory.size()];
            for (int i = 0; i < moodHistory.size(); i++) {
                sleepHours[i] = moodHistory.get(i).sleepHours;
            }
            // For sleep, negative slope = worsening (less sleep)
            report.sleepTrend = computeSleepTrend(sleepHours);
        }

        report.status = "READY";
        return report;
    }

    // ═══════════════════════════════════════════════════════════════════
    // LINEAR REGRESSION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute trend from risk scores.
     * Positive slope = worsening (risk going up).
     */
    @NonNull
    Trend computeTrend(float[] values) {
        float slope = computeSlope(values);
        if (slope > 0.05f)  return Trend.WORSENING;
        if (slope > 0.02f)  return Trend.SLIGHTLY_WORSENING;
        if (slope < -0.05f) return Trend.IMPROVING;
        if (slope < -0.02f) return Trend.SLIGHTLY_IMPROVING;
        return Trend.STABLE;
    }

    /**
     * Compute trend for mood scores.
     * Positive slope = improving (mood going up is good).
     */
    @NonNull
    private Trend computeMoodTrend(float[] values) {
        float slope = computeSlope(values);
        // Mood: positive slope = improvement
        if (slope > 0.05f)  return Trend.IMPROVING;
        if (slope > 0.02f)  return Trend.SLIGHTLY_IMPROVING;
        if (slope < -0.05f) return Trend.WORSENING;
        if (slope < -0.02f) return Trend.SLIGHTLY_WORSENING;
        return Trend.STABLE;
    }

    /**
     * Compute trend for sleep hours.
     * Positive slope = improving (more sleep is good).
     */
    @NonNull
    private Trend computeSleepTrend(float[] values) {
        float slope = computeSlope(values);
        if (slope > 0.1f)  return Trend.IMPROVING;
        if (slope > 0.05f) return Trend.SLIGHTLY_IMPROVING;
        if (slope < -0.1f) return Trend.WORSENING;
        if (slope < -0.05f) return Trend.SLIGHTLY_WORSENING;
        return Trend.STABLE;
    }

    /**
     * Linear regression slope using least squares.
     * Uses the last 7 values maximum.
     */
    float computeSlope(float[] values) {
        if (values == null || values.length < 3) return 0f;

        int n = values.length;
        int start = Math.max(0, n - 7);
        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int count = 0;

        for (int i = start; i < n; i++) {
            float x = count;
            float y = values[i];
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            count++;
        }

        float denom = count * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 0.0001f) return 0f;

        return (count * sumXY - sumX * sumY) / denom;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extract float values from a list using an extractor function.
     */
    private float[] extractFloats(List<RiskClassification> list,
                                   ScoreExtractor extractor) {
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = extractor.extract(list.get(i));
        }
        return result;
    }

    /**
     * Map mood string to a float (higher = better mood).
     */
    static float mapMoodToFloat(@Nullable String mood) {
        if (mood == null) return 3f;
        switch (mood.toLowerCase(Locale.US)) {
            case "great":    case "amazing":   return 5f;
            case "good":     case "happy":     return 4f;
            case "okay":     case "neutral":   return 3f;
            case "bad":      case "sad":       return 2f;
            case "terrible": case "anxious":   return 1f;
            default:                           return 3f;
        }
    }

    /** Functional interface for score extraction (Android API < 24 compat). */
    private interface ScoreExtractor {
        float extract(RiskClassification rc);
    }
}
