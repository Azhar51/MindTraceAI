package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyses historical {@link RiskClassification} data to produce cross-day
 * and cross-week trend insights.
 *
 * <p>This class bridges the gap between raw daily classifications and
 * human-readable trajectory assessments. It operates on the persisted
 * {@code featureVectorJson} fields to reconstruct feature-level trends.</p>
 *
 * <h3>Capabilities:</h3>
 * <ul>
 *   <li>Linear regression on overall risk (slope = trajectory direction)</li>
 *   <li>Per-category trend direction (improving / stable / deteriorating)</li>
 *   <li>Feature-level drift detection (which features are changing fastest)</li>
 *   <li>Confidence trajectory (is data quality improving?)</li>
 *   <li>Top risk driver identification across time</li>
 * </ul>
 *
 * @see FeatureVector
 * @see RiskClassification
 */
public class ClassificationTrendAnalyzer {

    private static final String TAG = "TrendAnalyzer";
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    private final RiskClassificationDao dao;

    public ClassificationTrendAnalyzer(@NonNull Context context) {
        this.dao = AppDatabase.getInstance(context.getApplicationContext())
                .riskClassificationDao();
    }

    public ClassificationTrendAnalyzer(@NonNull RiskClassificationDao dao) {
        this.dao = dao;
    }

    // ═══════════════════════════════════════════════════════════════════
    // TREND ANALYSIS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes a full trend report for the last N days.
     *
     * @param days Number of days to analyze (7 = weekly, 14 = biweekly, 30 = monthly)
     * @return TrendReport containing all computed trajectories, or null if insufficient data
     */
    @Nullable
    public TrendReport analyzeTrend(int days) {
        long since = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
        List<RiskClassification> history = dao.getHistorySince(since);

        if (history == null || history.size() < 2) {
            Log.d(TAG, "Insufficient data for trend analysis: " +
                    (history == null ? 0 : history.size()) + " days");
            return null;
        }

        TrendReport report = new TrendReport();
        report.daysAnalyzed = days;
        report.dataPoints = history.size();

        // ── Overall risk trajectory ──
        float[] overallScores = new float[history.size()];
        for (int i = 0; i < history.size(); i++) {
            overallScores[i] = history.get(i).overallRiskScore;
        }
        report.overallRiskSlope = linearRegressionSlope(overallScores);
        report.overallRiskTrajectory = slopeToTrajectory(report.overallRiskSlope);
        report.currentRisk = overallScores[overallScores.length - 1];
        report.averageRisk = mean(overallScores);

        // ── Per-category trajectories ──
        report.digitalAddictionSlope = categorySlope(history, "digital");
        report.stressAnxietySlope = categorySlope(history, "stress");
        report.depressionRiskSlope = categorySlope(history, "depression");
        report.socialIsolationSlope = categorySlope(history, "isolation");
        report.sleepDisruptionSlope = categorySlope(history, "sleep");
        report.lowFulfilmentSlope = categorySlope(history, "fulfilment");

        // ── Confidence trajectory ──
        float[] confidences = new float[history.size()];
        for (int i = 0; i < history.size(); i++) {
            confidences[i] = history.get(i).confidence;
        }
        report.confidenceSlope = linearRegressionSlope(confidences);
        report.averageConfidence = mean(confidences);

        // ── Feature-level drift ──
        report.featureDrifts = computeFeatureDrift(history);
        report.topDriftingFeature = identifyTopDriftFeature(report.featureDrifts);

        // ── Top risk drivers ──
        report.topRiskDrivers = identifyTopRiskDrivers(history);

        // ── Crisis analysis ──
        int crises = 0;
        for (RiskClassification rc : history) {
            if (rc.crisisFlag) crises++;
        }
        report.crisisCount = crises;

        Log.d(TAG, String.format("Trend analysis (%dd): slope=%.4f (%s), " +
                        "avg=%.2f, conf=%.2f, crises=%d",
                days, report.overallRiskSlope, report.overallRiskTrajectory,
                report.averageRisk, report.averageConfidence, crises));

        return report;
    }

    /** Quick trajectory check: is risk improving, stable, or worsening? */
    @NonNull
    public String getTrajectoryLabel(int days) {
        TrendReport report = analyzeTrend(days);
        if (report == null) return "insufficient_data";
        return report.overallRiskTrajectory;
    }

    // ═══════════════════════════════════════════════════════════════════
    // FEATURE DRIFT DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes per-feature drift (slope) across all historical feature vectors.
     * Returns a float[34] where each value is the regression slope for that feature.
     * Positive slope = feature is increasing (worsening), negative = improving.
     */
    private float[] computeFeatureDrift(List<RiskClassification> history) {
        float[] drifts = new float[FeatureVector.TOTAL_FEATURES];

        // Collect feature vectors that have JSON
        List<float[]> vectors = new ArrayList<>();
        for (RiskClassification rc : history) {
            if (rc.featureVectorJson != null && !rc.featureVectorJson.isEmpty()) {
                try {
                    FeatureVector fv = FeatureVector.fromJson(rc.featureVectorJson);
                    vectors.add(fv.toArray());
                } catch (Exception e) {
                    // Skip malformed entries
                }
            }
        }

        if (vectors.size() < 2) return drifts;

        // Compute slope for each feature
        for (int f = 0; f < FeatureVector.TOTAL_FEATURES; f++) {
            float[] featureValues = new float[vectors.size()];
            for (int i = 0; i < vectors.size(); i++) {
                featureValues[i] = vectors.get(i)[f];
            }
            drifts[f] = linearRegressionSlope(featureValues);
        }

        return drifts;
    }

    /** Identifies the feature with the largest absolute drift (most rapidly changing). */
    @NonNull
    private String identifyTopDriftFeature(float[] drifts) {
        int maxIdx = 0;
        float maxAbs = 0;
        for (int i = 0; i < drifts.length; i++) {
            float abs = Math.abs(drifts[i]);
            if (abs > maxAbs) {
                maxAbs = abs;
                maxIdx = i;
            }
        }
        if (maxIdx < FeatureVector.FEATURE_NAMES.length) {
            String direction = drifts[maxIdx] > 0 ? " ↑" : " ↓";
            return FeatureVector.FEATURE_NAMES[maxIdx] + direction;
        }
        return "unknown";
    }

    /**
     * Identifies the top 3 risk drivers from the most recent classification.
     * Uses raw feature values weighted by clinical significance.
     */
    @NonNull
    private List<String> identifyTopRiskDrivers(List<RiskClassification> history) {
        List<String> drivers = new ArrayList<>();
        if (history.isEmpty()) return drivers;

        // Use the most recent classification with feature JSON
        for (int i = history.size() - 1; i >= 0; i--) {
            RiskClassification rc = history.get(i);
            if (rc.featureVectorJson != null && !rc.featureVectorJson.isEmpty()) {
                try {
                    FeatureVector fv = FeatureVector.fromJson(rc.featureVectorJson);
                    float[] arr = fv.toArray();

                    // Find top 3 features by weighted risk contribution
                    int[] topIndices = new int[3];
                    float[] topValues = new float[3];
                    for (int f = 0; f < arr.length; f++) {
                        float weighted = arr[f]; // Raw feature risk value
                        for (int t = 0; t < 3; t++) {
                            if (weighted > topValues[t]) {
                                // Shift down
                                for (int s = 2; s > t; s--) {
                                    topIndices[s] = topIndices[s - 1];
                                    topValues[s] = topValues[s - 1];
                                }
                                topIndices[t] = f;
                                topValues[t] = weighted;
                                break;
                            }
                        }
                    }

                    for (int t = 0; t < 3; t++) {
                        if (topValues[t] > 0.01f) {
                            drivers.add(FeatureVector.FEATURE_NAMES[topIndices[t]] +
                                    " (" + Math.round(arr[topIndices[t]] * 100) + "%)");
                        }
                    }
                    break;
                } catch (Exception e) {
                    // Try next
                }
            }
        }
        return drivers;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MATH UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes the slope of a simple linear regression (y = mx + b).
     * x-values are 0, 1, 2, ..., n-1 (day index).
     * Returns slope m normalized to per-day change.
     */
    private float linearRegressionSlope(float[] values) {
        int n = values.length;
        if (n < 2) return 0f;

        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX  += i;
            sumY  += values[i];
            sumXY += i * values[i];
            sumX2 += i * i;
        }

        float denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-6f) return 0f;

        return (n * sumXY - sumX * sumY) / denominator;
    }

    private float mean(float[] values) {
        if (values.length == 0) return 0f;
        float sum = 0;
        for (float v : values) sum += v;
        return sum / values.length;
    }

    /**
     * Converts a regression slope to a human-readable trajectory label.
     * Thresholds are per-day risk change.
     */
    @NonNull
    private String slopeToTrajectory(float slope) {
        if (slope > 0.02f)  return "rapidly_worsening";
        if (slope > 0.005f) return "gradually_worsening";
        if (slope < -0.02f) return "rapidly_improving";
        if (slope < -0.005f) return "gradually_improving";
        return "stable";
    }

    private float categorySlope(List<RiskClassification> history, String category) {
        float[] scores = new float[history.size()];
        for (int i = 0; i < history.size(); i++) {
            RiskClassification rc = history.get(i);
            switch (category) {
                case "digital":    scores[i] = rc.digitalAddictionScore; break;
                case "stress":     scores[i] = rc.stressAnxietyScore; break;
                case "depression": scores[i] = rc.depressionRiskScore; break;
                case "isolation":  scores[i] = rc.socialIsolationScore; break;
                case "sleep":      scores[i] = rc.sleepDisruptionScore; break;
                case "fulfilment": scores[i] = rc.lowFulfilmentScore; break;
                default:           scores[i] = rc.overallRiskScore; break;
            }
        }
        return linearRegressionSlope(scores);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESULT DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Complete trend analysis report.
     */
    public static class TrendReport {
        /** Number of days the analysis covers. */
        public int daysAnalyzed;
        /** Number of actual data points (may be < daysAnalyzed if gaps exist). */
        public int dataPoints;

        // ── Overall ──
        /** Slope of overall risk over the period (positive = worsening). */
        public float overallRiskSlope;
        /** Human label: "rapidly_improving", "gradually_improving", "stable",
         *  "gradually_worsening", "rapidly_worsening". */
        public String overallRiskTrajectory;
        /** Most recent overall risk score. */
        public float currentRisk;
        /** Average risk across the period. */
        public float averageRisk;

        // ── Per-category slopes (positive = worsening) ──
        public float digitalAddictionSlope;
        public float stressAnxietySlope;
        public float depressionRiskSlope;
        public float socialIsolationSlope;
        public float sleepDisruptionSlope;
        public float lowFulfilmentSlope;

        // ── Confidence ──
        public float confidenceSlope;
        public float averageConfidence;

        // ── Feature drift ──
        /** Per-feature regression slopes (float[34]). */
        public float[] featureDrifts;
        /** The feature changing fastest (with direction arrow). */
        public String topDriftingFeature;

        // ── Risk drivers ──
        /** Top 3 risk driver labels from the latest classification. */
        public List<String> topRiskDrivers;

        // ── Crisis ──
        public int crisisCount;

        /** Returns the category with the steepest worsening slope. */
        @NonNull
        public String getFastestWorseningCategory() {
            float max = digitalAddictionSlope;
            String cat = "Digital Addiction";

            if (stressAnxietySlope > max) { max = stressAnxietySlope; cat = "Stress & Anxiety"; }
            if (depressionRiskSlope > max) { max = depressionRiskSlope; cat = "Depression"; }
            if (socialIsolationSlope > max) { max = socialIsolationSlope; cat = "Social Isolation"; }
            if (sleepDisruptionSlope > max) { max = sleepDisruptionSlope; cat = "Sleep Disruption"; }
            if (lowFulfilmentSlope > max) { cat = "Low Fulfilment"; }

            return cat;
        }

        /** Returns the category showing the most improvement. */
        @NonNull
        public String getFastestImprovingCategory() {
            float min = digitalAddictionSlope;
            String cat = "Digital Addiction";

            if (stressAnxietySlope < min) { min = stressAnxietySlope; cat = "Stress & Anxiety"; }
            if (depressionRiskSlope < min) { min = depressionRiskSlope; cat = "Depression"; }
            if (socialIsolationSlope < min) { min = socialIsolationSlope; cat = "Social Isolation"; }
            if (sleepDisruptionSlope < min) { min = sleepDisruptionSlope; cat = "Sleep Disruption"; }
            if (lowFulfilmentSlope < min) { cat = "Low Fulfilment"; }

            return cat;
        }
    }
}
