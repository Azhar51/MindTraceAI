package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Stores the output of the AI classification engine for a single day.
 *
 * <p>Each row is a complete risk profile across 6 psychological dimensions,
 * produced by {@code MultiModalClassifier.classify(FeatureVector)}.</p>
 *
 * <h3>Risk Categories:</h3>
 * <ol>
 *   <li><b>Digital Addiction</b> — compulsive phone use, escape behaviour</li>
 *   <li><b>Stress & Anxiety</b> — acute/chronic stress, anxiety disorders</li>
 *   <li><b>Depression Risk</b> — persistent sadness, anhedonia, hopelessness</li>
 *   <li><b>Social Isolation</b> — loneliness, withdrawal, connection deficit</li>
 *   <li><b>Sleep Disruption</b> — insomnia, night usage, circadian disruption</li>
 *   <li><b>Low Fulfilment</b> — purposelessness, motivation deficit, stagnation</li>
 * </ol>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   FeatureVector (28 features)
 *       → MultiModalClassifier.classify()
 *           → RiskClassification (this entity) — persisted to DB
 *               → InsightEngine (generates human-readable insights)
 *               → Dashboard UI (risk cards, trend charts)
 *               → CrisisDetector (triggers intervention if crisis)
 * </pre>
 *
 * @see com.mindtrace.ai.ai.FeatureVector
 * @see com.mindtrace.ai.ai.MultiModalClassifier
 * @see com.mindtrace.ai.database.dao.RiskClassificationDao
 */
@Entity(
        tableName = "risk_classifications",
        indices = {
                @Index(value = {"timestamp"}),
                @Index(value = {"dayTimestamp"}, unique = true),
                @Index(value = {"overallRiskScore"}),
                @Index(value = {"primaryCategory"})
        }
)
public class RiskClassification {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // ═══════════════════════════════════════════════════════════════════
    // TEMPORAL
    // ═══════════════════════════════════════════════════════════════════

    /** When this classification was computed. */
    public long timestamp;

    /** Day timestamp (midnight) — one classification per day. */
    @ColumnInfo(defaultValue = "0")
    public long dayTimestamp;

    // ═══════════════════════════════════════════════════════════════════
    // 6 RISK DIMENSION SCORES (0.0–1.0, higher = greater risk)
    // ═══════════════════════════════════════════════════════════════════

    /** Digital addiction risk: compulsive use, escape, dependency. */
    @ColumnInfo(defaultValue = "0")
    public float digitalAddictionScore;

    /** Stress & anxiety risk: acute stress, generalized anxiety, overwhelm. */
    @ColumnInfo(defaultValue = "0")
    public float stressAnxietyScore;

    /** Depression risk: persistent sadness, anhedonia, hopelessness. */
    @ColumnInfo(defaultValue = "0")
    public float depressionRiskScore;

    /** Social isolation risk: loneliness, withdrawal, connection deficit. */
    @ColumnInfo(defaultValue = "0")
    public float socialIsolationScore;

    /** Sleep disruption risk: insomnia, night usage, circadian issues. */
    @ColumnInfo(defaultValue = "0")
    public float sleepDisruptionScore;

    /** Low fulfilment risk: purposelessness, low motivation, stagnation. */
    @ColumnInfo(defaultValue = "0")
    public float lowFulfilmentScore;

    // ═══════════════════════════════════════════════════════════════════
    // AGGREGATE SCORES
    // ═══════════════════════════════════════════════════════════════════

    /** Composite risk score (0.0–1.0) — weighted average across all dimensions. */
    @ColumnInfo(defaultValue = "0")
    public float overallRiskScore;

    /** Which risk category is dominant today. */
    @Nullable
    public String primaryCategory;

    /** Second-highest risk category (for co-morbidity detection). */
    @Nullable
    @ColumnInfo(defaultValue = "")
    public String secondaryCategory;

    /** Confidence in this classification (0.0–1.0). Higher = more data available. */
    @ColumnInfo(defaultValue = "0")
    public float confidence;

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS & INTERVENTION
    // ═══════════════════════════════════════════════════════════════════

    /** Whether this classification triggers crisis intervention protocols. */
    @ColumnInfo(defaultValue = "0")
    public boolean crisisFlag;

    /** Reason for crisis flag (e.g., "depression_risk > 0.85 + consecutive_sad_days > 4"). */
    @Nullable
    public String crisisReason;

    /** Whether the user was shown an intervention for this classification. */
    @ColumnInfo(defaultValue = "0")
    public boolean interventionShown;

    // ═══════════════════════════════════════════════════════════════════
    // METADATA
    // ═══════════════════════════════════════════════════════════════════

    /** Classification mode: "full", "partial", "baseline_only". */
    @Nullable
    @ColumnInfo(defaultValue = "full")
    public String classificationMode;

    /** Number of features that had real data (vs defaults). Higher = more reliable. */
    @ColumnInfo(defaultValue = "0")
    public int featureDataCount;

    /** Raw feature vector snapshot (JSON) for debugging/auditing. */
    @Nullable
    public String featureVectorJson;

    /** Delta from previous day's overall risk score. Positive = worsening. */
    @ColumnInfo(defaultValue = "0")
    public float riskDelta;

    /** 7-day moving average of overall risk. */
    @ColumnInfo(defaultValue = "0")
    public float riskMovingAverage;

    // ═══════════════════════════════════════════════════════════════════
    // SEVERITY ENUM
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 6-level severity classification for any risk score.
     */
    public enum Severity {
        NONE("None", 0),
        MILD("Mild", 1),
        WATCH("Watch", 2),
        MODERATE("Moderate", 3),
        HIGH("High", 4),
        SEVERE("Severe", 5);

        public final String label;
        public final int level;

        Severity(String label, int level) {
            this.label = label;
            this.level = level;
        }

        /**
         * Map a 0.0–1.0 risk score to a Severity level.
         *
         * <pre>
         *   0.00–0.15 → NONE
         *   0.16–0.30 → MILD
         *   0.31–0.45 → WATCH
         *   0.46–0.65 → MODERATE
         *   0.66–0.80 → HIGH
         *   0.81–1.00 → SEVERE
         * </pre>
         */
        public static Severity fromScore(float score) {
            if (score <= 0.15f) return NONE;
            if (score <= 0.30f) return MILD;
            if (score <= 0.45f) return WATCH;
            if (score <= 0.65f) return MODERATE;
            if (score <= 0.80f) return HIGH;
            return SEVERE;
        }

        /** Whether this severity warrants active monitoring. */
        public boolean requiresAttention() {
            return level >= WATCH.level;
        }

        /** Whether this severity should trigger intervention suggestions. */
        public boolean requiresIntervention() {
            return level >= MODERATE.level;
        }

        /** Whether this severity is crisis-adjacent. */
        public boolean isCritical() {
            return level >= HIGH.level;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONVENIENCE GETTERS
    // ═══════════════════════════════════════════════════════════════════

    public Severity getDigitalAddictionSeverity()  { return Severity.fromScore(digitalAddictionScore); }
    public Severity getStressAnxietySeverity()     { return Severity.fromScore(stressAnxietyScore); }
    public Severity getDepressionSeverity()        { return Severity.fromScore(depressionRiskScore); }
    public Severity getSocialIsolationSeverity()   { return Severity.fromScore(socialIsolationScore); }
    public Severity getSleepDisruptionSeverity()   { return Severity.fromScore(sleepDisruptionScore); }
    public Severity getLowFulfilmentSeverity()     { return Severity.fromScore(lowFulfilmentScore); }
    public Severity getOverallSeverity()           { return Severity.fromScore(overallRiskScore); }

    /** Get the highest individual severity across all 6 categories. */
    public Severity getPeakSeverity() {
        Severity peak = getDigitalAddictionSeverity();
        if (getStressAnxietySeverity().level > peak.level) peak = getStressAnxietySeverity();
        if (getDepressionSeverity().level > peak.level) peak = getDepressionSeverity();
        if (getSocialIsolationSeverity().level > peak.level) peak = getSocialIsolationSeverity();
        if (getSleepDisruptionSeverity().level > peak.level) peak = getSleepDisruptionSeverity();
        if (getLowFulfilmentSeverity().level > peak.level) peak = getLowFulfilmentSeverity();
        return peak;
    }

    /** Count how many categories are at MODERATE or above. */
    public int getElevatedCategoryCount() {
        int count = 0;
        if (digitalAddictionScore > 0.45f) count++;
        if (stressAnxietyScore > 0.45f) count++;
        if (depressionRiskScore > 0.45f) count++;
        if (socialIsolationScore > 0.45f) count++;
        if (sleepDisruptionScore > 0.45f) count++;
        if (lowFulfilmentScore > 0.45f) count++;
        return count;
    }

    /** Get the score for a category by name. */
    public float getScoreForCategory(String category) {
        if (category == null) return 0f;
        switch (category) {
            case "digital_addiction": return digitalAddictionScore;
            case "stress_anxiety":   return stressAnxietyScore;
            case "depression":       return depressionRiskScore;
            case "social_isolation": return socialIsolationScore;
            case "sleep_disruption": return sleepDisruptionScore;
            case "low_fulfilment":   return lowFulfilmentScore;
            default:                 return 0f;
        }
    }

    /** Whether the risk trajectory is worsening (positive delta). */
    public boolean isWorsening() {
        return riskDelta > 0.05f;
    }

    /** Whether the risk trajectory is improving (negative delta). */
    public boolean isImproving() {
        return riskDelta < -0.05f;
    }

    @NonNull
    @Override
    public String toString() {
        return "RiskClassification{" +
                "overall=" + String.format("%.2f", overallRiskScore) +
                " (" + getOverallSeverity().label + ")" +
                ", primary=" + primaryCategory +
                ", digital=" + String.format("%.2f", digitalAddictionScore) +
                ", stress=" + String.format("%.2f", stressAnxietyScore) +
                ", depression=" + String.format("%.2f", depressionRiskScore) +
                ", isolation=" + String.format("%.2f", socialIsolationScore) +
                ", sleep=" + String.format("%.2f", sleepDisruptionScore) +
                ", fulfilment=" + String.format("%.2f", lowFulfilmentScore) +
                ", crisis=" + crisisFlag +
                ", confidence=" + String.format("%.2f", confidence) +
                '}';
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED ANALYSIS METHODS
    // ═══════════════════════════════════════════════════════════════════

    /** Get ordered list of categories above threshold (highest first). */
    @NonNull
    public String[] getTopCategories(float threshold) {
        java.util.ArrayList<float[]> pairs = new java.util.ArrayList<>();
        pairs.add(new float[]{0, digitalAddictionScore});
        pairs.add(new float[]{1, stressAnxietyScore});
        pairs.add(new float[]{2, depressionRiskScore});
        pairs.add(new float[]{3, socialIsolationScore});
        pairs.add(new float[]{4, sleepDisruptionScore});
        pairs.add(new float[]{5, lowFulfilmentScore});
        // Sort descending
        java.util.Collections.sort(pairs, (a, b) -> Float.compare(b[1], a[1]));
        String[] names = {"digital_addiction","stress_anxiety","depression",
                           "social_isolation","sleep_disruption","low_fulfilment"};
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (float[] p : pairs) {
            if (p[1] >= threshold) result.add(names[(int) p[0]]);
        }
        return result.toArray(new String[0]);
    }

    /** Clinical urgency score (0–10 integer scale for quick triage). */
    public int getClinicalUrgency() {
        int urgency = 0;
        if (overallRiskScore >= 0.80f) urgency += 3;
        else if (overallRiskScore >= 0.60f) urgency += 2;
        else if (overallRiskScore >= 0.40f) urgency += 1;

        if (crisisFlag) urgency += 3;
        if (getElevatedCategoryCount() >= 4) urgency += 2;
        else if (getElevatedCategoryCount() >= 2) urgency += 1;
        if (isWorsening()) urgency += 1;

        return Math.min(10, urgency);
    }

    /**
     * How dominant is the primary category vs the rest?
     * Returns 0.0 (balanced) to 1.0 (one category overwhelmingly dominant).
     */
    public float getDominanceRatio() {
        float primary = getScoreForCategory(primaryCategory);
        float total = digitalAddictionScore + stressAnxietyScore + depressionRiskScore +
                      socialIsolationScore + sleepDisruptionScore + lowFulfilmentScore;
        if (total <= 0.001f) return 0f;
        return primary / total;
    }

    /**
     * Balance score: how evenly distributed is risk across categories?
     * Returns 0.0 (concentrated in one) to 1.0 (evenly spread).
     * Uses normalized entropy of the 6 category scores.
     */
    public float getBalanceScore() {
        float[] scores = {digitalAddictionScore, stressAnxietyScore, depressionRiskScore,
                          socialIsolationScore, sleepDisruptionScore, lowFulfilmentScore};
        float total = 0;
        for (float s : scores) total += s;
        if (total <= 0.001f) return 1.0f;

        double entropy = 0;
        for (float s : scores) {
            if (s > 0.001f) {
                double p = s / total;
                entropy -= p * Math.log(p);
            }
        }
        double maxEntropy = Math.log(6.0);
        return (float) Math.min(1.0, entropy / maxEntropy);
    }

    /** Compact single-line summary for logs. */
    @NonNull
    public String toShortString() {
        return String.format(java.util.Locale.US,
                "R:%.0f%% [%s] %s c=%.0f%% %s",
                overallRiskScore * 100,
                getOverallSeverity().label,
                primaryCategory != null ? primaryCategory : "none",
                confidence * 100,
                crisisFlag ? "⚠CRISIS" : "");
    }

    /** Get the "overall" score (alias for ClassificationRepository trend query). */
    public float getScoreForCategoryOrOverall(String category) {
        if ("overall".equals(category)) return overallRiskScore;
        return getScoreForCategory(category);
    }
}

