package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * 36-dimensional feature vector for the MindTrace AI classification engine.
 *
 * <p>This is the core data structure that bridges raw behavioural/psychological
 * data with the {@link MultiModalClassifier}. Every feature is normalized to
 * [0.0, 1.0] where <b>higher = greater risk</b>.</p>
 *
 * <h3>Feature Domains:</h3>
 * <pre>
 *   D1–D12  (12 features) — Digital Behaviour    → DigitalFeatureExtractor
 *   P1–P10  (10 features) — Psychological State  → PsychFeatureExtractor
 *   C1–C6   ( 6 features) — Contextual Factors   → ContextFeatureExtractor
 *   T1–T6   ( 6 features) — Temporal Patterns    → TemporalFeatureExtractor
 *   D13–D14 ( 2 features) — Engagement Quality   → DigitalFeatureExtractor
 *   ────────────────────────────────────────────────────────────────
 *   Total:   36 features   → FeatureVector (this class)
 * </pre>
 *
 * <h3>Design Principles:</h3>
 * <ul>
 *   <li><b>Uniform scale:</b> All features [0.0, 1.0], higher = worse</li>
 *   <li><b>Default = 0.5:</b> Missing data defaults to mid-range (uncertain)</li>
 *   <li><b>Immutable after build:</b> Use {@link Builder} for construction</li>
 *   <li><b>Self-describing:</b> Each feature carries metadata (name, group, range)</li>
 * </ul>
 *
 * @see DigitalFeatureExtractor
 * @see PsychFeatureExtractor
 * @see ContextFeatureExtractor
 * @see TemporalFeatureExtractor
 * @see MultiModalClassifier
 */
public final class FeatureVector {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    public static final int TOTAL_FEATURES = 36;
    public static final int DIGITAL_COUNT = 12;   // D1–D12 (contiguous block)
    public static final int DIGITAL_EXT_COUNT = 2; // D13–D14 (engagement quality)
    public static final int PSYCH_COUNT = 10;      // P1–P10
    public static final int CONTEXT_COUNT = 6;     // C1–C6
    public static final int TEMPORAL_COUNT = 6;    // T1–T6

    public static final int IDX_D1 = 0;
    public static final int IDX_D12 = 11;
    public static final int IDX_P1 = 12;
    public static final int IDX_P10 = 21;
    public static final int IDX_C1 = 22;
    public static final int IDX_C6 = 27;
    public static final int IDX_T1 = 28;
    public static final int IDX_T6 = 33;
    public static final int IDX_D13 = 34;  // scrollIntensity
    public static final int IDX_D14 = 35;  // notificationReactivity

    /** Default value for missing/unknown features (mid-range = uncertain). */
    public static final float DEFAULT_VALUE = 0.5f;

    /** Feature group identifiers. */
    public static final String GROUP_DIGITAL = "digital";
    public static final String GROUP_PSYCH = "psychological";
    public static final String GROUP_CONTEXT = "contextual";
    public static final String GROUP_TEMPORAL = "temporal";

    // ── Feature metadata ─────────────────────────────────────────────

    /** Short names for all 36 features (index-aligned). */
    public static final String[] FEATURE_NAMES = {
            // D1–D12: Digital
            "screenTimeHours",       "screenTimeDeviation",   "appSwitchCount",
            "rapidSwitchCount",      "bingeSessionCount",     "nightUsageMinutes",
            "unlockCount",           "longestSessionMinutes",  "dominantAppPercent",
            "fragmentationScore",    "passiveAppRatio",       "hasLoopPattern",
            // P1–P10: Psychological
            "moodRisk",              "stressLevel",           "lonelinessLevel",
            "motivationDeficit",     "sleepDeficit",          "energyDeficit",
            "focusDeficit",          "purposeDeficit",        "addictionSelfScore",
            "consecutiveSadDays",
            // C1–C6: Contextual
            "workPressure",          "socialSupportDeficit",  "goalClarityDeficit",
            "exerciseDeficit",       "screenFreeDeficit",     "routineInstability",
            // T1–T6: Temporal
            "screenTimeTrend3d",     "moodStability7d",       "sleepConsistency7d",
            "weekendWeekdayRatio",   "postCheckInChange",     "recoverySpeed",
            // D13–D14: Engagement Quality (telemetry-driven)
            "scrollIntensity",       "notificationReactivity"
    };

    /** Human-readable descriptions (index-aligned). */
    public static final String[] FEATURE_DESCRIPTIONS = {
            // D1–D12
            "Total screen time normalized (0h→0, 8h+→1)",
            "Deviation from personal baseline",
            "App switching frequency (fragmented attention)",
            "Rapid switches within 30s (compulsive checking)",
            "Sessions >30min on passive apps",
            "Phone usage during 10pm–6am",
            "Total phone unlocks (compulsive checking)",
            "Longest continuous app session",
            "Top app's share of total screen time",
            "Ratio of <2min sessions (attention fragmentation)",
            "Passive app time / total time",
            "Repeated app-loop pattern detected",
            // P1–P10
            "Mood mapped to risk (Happy→0, Numb→0.9)",
            "Self-reported stress (1-5 → 0-1)",
            "Self-reported loneliness (1-5 → 0-1)",
            "Inverted motivation (high motivation→0)",
            "Inverted sleep quality (8h+→0, <3h→1)",
            "Inverted energy level (High→0, Low→1)",
            "Inverted focus level (High→0, Low→1)",
            "Inverted purpose score (high purpose→0)",
            "Self-assessed addiction severity (1-10 → 0-1)",
            "Consecutive negative mood days (0-5 → 0-1)",
            // C1–C6
            "Work/academic pressure level",
            "Inverted social support (has support→0)",
            "Inverted goal clarity (clear goals→0)",
            "Inverted exercise frequency (daily→0, never→1)",
            "Inverted screen-free activities count",
            "Inverted routine stability",
            // T1–T6
            "3-day screen time linear regression slope",
            "7-day mood score standard deviation (inverted)",
            "7-day sleep hours standard deviation (inverted)",
            "Weekend avg / weekday avg screen time ratio",
            "Screen time change after check-in (awareness)",
            "Days from Sad to Neutral/Happy (resilience)",
            // D13–D14: Engagement Quality
            "Scroll intensity score (mindless scrolling, 0–10→0–1)",
            "Notification response latency inverted (fast=1, slow=0)"
    };

    /** Which group each feature belongs to (index-aligned). */
    public static final String[] FEATURE_GROUPS = new String[TOTAL_FEATURES];
    static {
        for (int i = 0; i < DIGITAL_COUNT; i++) FEATURE_GROUPS[i] = GROUP_DIGITAL;
        for (int i = IDX_P1; i <= IDX_P10; i++) FEATURE_GROUPS[i] = GROUP_PSYCH;
        for (int i = IDX_C1; i <= IDX_C6; i++) FEATURE_GROUPS[i] = GROUP_CONTEXT;
        for (int i = IDX_T1; i <= IDX_T6; i++) FEATURE_GROUPS[i] = GROUP_TEMPORAL;
        // D13–D14 appended after T6 — still digital domain
        FEATURE_GROUPS[IDX_D13] = GROUP_DIGITAL;
        FEATURE_GROUPS[IDX_D14] = GROUP_DIGITAL;
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS — The 28 Features (Tasks 3.A.1)
    // ═══════════════════════════════════════════════════════════════════

    private final float[] features;

    /** Fraction of features with real (non-default) data. 0.0–1.0. (Task 3.A.4) */
    public final float dataCompleteness;

    /** Timestamp when this vector was extracted. (Task 3.A.5) */
    public final long extractionTimestamp;

    /** Source identifier: "live", "historical", "simulated". */
    public final String source;

    /** Number of features that have real (non-default) data. (Task 3.A.7) */
    public final int nonDefaultCount;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR (private — use Builder)
    // ═══════════════════════════════════════════════════════════════════

    private FeatureVector(float[] features, long timestamp, String source) {
        this.features = features;
        this.extractionTimestamp = timestamp;
        this.source = source != null ? source : "live";

        // Compute data completeness
        int nonDefault = 0;
        for (float f : features) {
            if (Math.abs(f - DEFAULT_VALUE) > 0.001f) nonDefault++;
        }
        this.nonDefaultCount = nonDefault;
        this.dataCompleteness = nonDefault / (float) TOTAL_FEATURES;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS — Individual Features
    // ═══════════════════════════════════════════════════════════════════

    /** Get feature value by index (0–27). */
    public float get(int index) {
        if (index < 0 || index >= TOTAL_FEATURES) return DEFAULT_VALUE;
        return features[index];
    }

    /** Get feature value by name. Returns DEFAULT_VALUE if not found. */
    public float getByName(@NonNull String name) {
        for (int i = 0; i < FEATURE_NAMES.length; i++) {
            if (FEATURE_NAMES[i].equals(name)) return features[i];
        }
        return DEFAULT_VALUE;
    }

    // ── Digital features (D1–D12) ────────────────────────────────────
    public float screenTimeHours()       { return features[0]; }
    public float screenTimeDeviation()   { return features[1]; }
    public float appSwitchCount()        { return features[2]; }
    public float rapidSwitchCount()      { return features[3]; }
    public float bingeSessionCount()     { return features[4]; }
    public float nightUsageMinutes()     { return features[5]; }
    public float unlockCount()           { return features[6]; }
    public float longestSessionMinutes() { return features[7]; }
    public float dominantAppPercent()    { return features[8]; }
    public float fragmentationScore()    { return features[9]; }
    public float passiveAppRatio()       { return features[10]; }
    public float hasLoopPattern()        { return features[11]; }

    // ── Engagement Quality features (D13–D14) ────────────────────────
    public float scrollIntensity()       { return features[34]; }
    public float notificationReactivity(){ return features[35]; }

    // ── Psychological features (P1–P10) ──────────────────────────────
    public float moodRisk()              { return features[12]; }
    public float stressLevel()           { return features[13]; }
    public float lonelinessLevel()       { return features[14]; }
    public float motivationDeficit()     { return features[15]; }
    public float sleepDeficit()          { return features[16]; }
    public float energyDeficit()         { return features[17]; }
    public float focusDeficit()          { return features[18]; }
    public float purposeDeficit()        { return features[19]; }
    public float addictionSelfScore()    { return features[20]; }
    public float consecutiveSadDays()    { return features[21]; }

    // ── Contextual features (C1–C6) ──────────────────────────────────
    public float workPressure()          { return features[22]; }
    public float socialSupportDeficit()  { return features[23]; }
    public float goalClarityDeficit()    { return features[24]; }
    public float exerciseDeficit()       { return features[25]; }
    public float screenFreeDeficit()     { return features[26]; }
    public float routineInstability()    { return features[27]; }

    // ── Temporal features (T1–T6) ────────────────────────────────────
    public float screenTimeTrend3d()     { return features[28]; }
    public float moodStability7d()       { return features[29]; }
    public float sleepConsistency7d()    { return features[30]; }
    public float weekendWeekdayRatio()   { return features[31]; }
    public float postCheckInChange()     { return features[32]; }
    public float recoverySpeed()         { return features[33]; }

    // ═══════════════════════════════════════════════════════════════════
    // DOMAIN AGGREGATES
    // ═══════════════════════════════════════════════════════════════════

    /** Average risk across all digital features (D1–D12 + D13–D14). */
    public float digitalRiskAvg() {
        float sum = 0f;
        for (int i = IDX_D1; i <= IDX_D12; i++) sum += features[i];
        sum += features[IDX_D13] + features[IDX_D14];
        return sum / (DIGITAL_COUNT + DIGITAL_EXT_COUNT);
    }

    /** Average risk across all psychological features (P1–P10). */
    public float psychRiskAvg() {
        return avgRange(IDX_P1, IDX_P10);
    }

    /** Average risk across all contextual features (C1–C6). */
    public float contextRiskAvg() {
        return avgRange(IDX_C1, IDX_C6);
    }

    /** Average risk across all temporal features (T1–T6). */
    public float temporalRiskAvg() {
        return avgRange(IDX_T1, IDX_T6);
    }

    /** Weighted overall risk: digital 30%, psych 35%, context 15%, temporal 20%. */
    public float overallRiskEstimate() {
        return digitalRiskAvg() * 0.30f
                + psychRiskAvg() * 0.35f
                + contextRiskAvg() * 0.15f
                + temporalRiskAvg() * 0.20f;
    }

    /** Max feature value across all 28 features (hotspot detection). */
    public float maxFeature() {
        float max = 0f;
        for (float f : features) max = Math.max(max, f);
        return max;
    }

    /** Index of the highest-risk feature. */
    public int maxFeatureIndex() {
        int idx = 0;
        for (int i = 1; i < TOTAL_FEATURES; i++) {
            if (features[i] > features[idx]) idx = i;
        }
        return idx;
    }

    /** Name of the highest-risk feature. */
    @NonNull
    public String maxFeatureName() {
        return FEATURE_NAMES[maxFeatureIndex()];
    }

    // ═══════════════════════════════════════════════════════════════════
    // SERIALIZATION (Tasks 3.A.2, 3.A.3)
    // ═══════════════════════════════════════════════════════════════════

    /** Returns a copy of the raw float[34] array. (Task 3.A.2) */
    @NonNull
    public float[] toArray() {
        return Arrays.copyOf(features, TOTAL_FEATURES);
    }

    /**
     * Creates a FeatureVector from a float array. (Task 3.A.3)
     * Accepts both legacy 28-element and current 34-element arrays.
     * For 28-element arrays, T1–T6 default to 0.5 (unknown).
     */
    @NonNull
    public static FeatureVector fromArray(@NonNull float[] array) {
        if (array.length != TOTAL_FEATURES && array.length != 34 && array.length != 28) {
            throw new IllegalArgumentException(
                    "Expected " + TOTAL_FEATURES + ", 34, or 28 features, got " + array.length);
        }
        float[] clamped = new float[TOTAL_FEATURES];
        Arrays.fill(clamped, DEFAULT_VALUE); // Defaults for missing features
        for (int i = 0; i < Math.min(array.length, TOTAL_FEATURES); i++) {
            clamped[i] = clamp(array[i]);
        }
        return new FeatureVector(clamped, System.currentTimeMillis(), "fromArray");
    }

    /** Creates a FeatureVector with all features set to DEFAULT_VALUE. */
    @NonNull
    public static FeatureVector empty() {
        float[] defaults = new float[TOTAL_FEATURES];
        Arrays.fill(defaults, DEFAULT_VALUE);
        return new FeatureVector(defaults, System.currentTimeMillis(), "empty");
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANALYSIS — Comparison, Distance, Anomaly
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Euclidean distance between this vector and another.
     * Useful for clustering and similarity detection.
     */
    public float distanceTo(@NonNull FeatureVector other) {
        float sum = 0f;
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            float diff = features[i] - other.features[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    /**
     * Cosine similarity between this vector and another (0.0–1.0).
     */
    public float cosineSimilarity(@NonNull FeatureVector other) {
        float dot = 0f, magA = 0f, magB = 0f;
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            dot += features[i] * other.features[i];
            magA += features[i] * features[i];
            magB += other.features[i] * other.features[i];
        }
        float denom = (float) (Math.sqrt(magA) * Math.sqrt(magB));
        return denom < 0.001f ? 0f : dot / denom;
    }

    /**
     * Computes feature-level delta from a previous vector.
     * Positive delta = feature got worse, negative = improved.
     *
     * @return float[28] of deltas
     */
    @NonNull
    public float[] deltaFrom(@NonNull FeatureVector previous) {
        float[] delta = new float[TOTAL_FEATURES];
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            delta[i] = features[i] - previous.features[i];
        }
        return delta;
    }

    /**
     * Returns indices of features that are anomalously high (>= threshold).
     * Useful for generating targeted interventions.
     */
    @NonNull
    public int[] getAnomalousFeatures(float threshold) {
        int count = 0;
        for (float f : features) if (f >= threshold) count++;
        int[] result = new int[count];
        int idx = 0;
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            if (features[i] >= threshold) result[idx++] = i;
        }
        return result;
    }

    /**
     * Counts how many features exceed the given risk threshold.
     */
    public int countAboveThreshold(float threshold) {
        int count = 0;
        for (float f : features) if (f >= threshold) count++;
        return count;
    }

    /**
     * Returns the standard deviation of all feature values.
     * High std = imbalanced risk profile, low = uniform.
     */
    public float standardDeviation() {
        float mean = 0f;
        for (float f : features) mean += f;
        mean /= TOTAL_FEATURES;
        float variance = 0f;
        for (float f : features) {
            float diff = f - mean;
            variance += diff * diff;
        }
        return (float) Math.sqrt(variance / TOTAL_FEATURES);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA QUALITY
    // ═══════════════════════════════════════════════════════════════════

    /** Whether this vector has enough real data for reliable classification. */
    public boolean isReliable() {
        return dataCompleteness >= 0.5f;
    }

    /** Whether the digital domain has sufficient data. */
    public boolean hasDigitalData() {
        return nonDefaultCountInRange(IDX_D1, IDX_D12) >= 6;
    }

    /** Whether the psychological domain has sufficient data. */
    public boolean hasPsychData() {
        return nonDefaultCountInRange(IDX_P1, IDX_P10) >= 5;
    }

    /** Whether the contextual domain has sufficient data. */
    public boolean hasContextData() {
        return nonDefaultCountInRange(IDX_C1, IDX_C6) >= 3;
    }

    /** Whether the temporal domain has sufficient data. */
    public boolean hasTemporalData() {
        return nonDefaultCountInRange(IDX_T1, IDX_T6) >= 3;
    }

    /** Data quality label. */
    @NonNull
    public String getQualityLabel() {
        if (dataCompleteness >= 0.85f) return "Excellent";
        if (dataCompleteness >= 0.65f) return "Good";
        if (dataCompleteness >= 0.45f) return "Partial";
        if (dataCompleteness >= 0.25f) return "Limited";
        return "Insufficient";
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEBUG / LOGGING (Task 3.A.6)
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FeatureVector{");
        sb.append("completeness=").append(pct(dataCompleteness));
        sb.append(", quality=").append(getQualityLabel());
        sb.append(", risk=").append(pct(overallRiskEstimate()));
        sb.append(", D=").append(pct(digitalRiskAvg()));
        sb.append(", P=").append(pct(psychRiskAvg()));
        sb.append(", C=").append(pct(contextRiskAvg()));
        sb.append(", hotspot=").append(maxFeatureName());
        sb.append("(").append(pct(maxFeature())).append(")");
        sb.append(", src=").append(source);
        sb.append("}");
        return sb.toString();
    }

    /** Detailed multi-line dump for debug logging. */
    @NonNull
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════╗\n");
        sb.append("║         FEATURE VECTOR DUMP                 ║\n");
        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append(String.format(Locale.US, "║ Quality: %s (%s)%n",
                getQualityLabel(), pct(dataCompleteness)));
        sb.append(String.format(Locale.US, "║ Overall Risk: %s%n",
                pct(overallRiskEstimate())));
        sb.append("╠══════════════════════════════════════════════╣\n");

        String currentGroup = "";
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            if (!FEATURE_GROUPS[i].equals(currentGroup)) {
                currentGroup = FEATURE_GROUPS[i];
                sb.append("║ ── ").append(currentGroup.toUpperCase(Locale.US)).append(" ──\n");
            }
            String bar = riskBar(features[i]);
            boolean isDefault = Math.abs(features[i] - DEFAULT_VALUE) < 0.001f;
            sb.append(String.format(Locale.US, "║  %s%-24s %s %s%s%n",
                    featureIndex(i), FEATURE_NAMES[i],
                    bar, pct(features[i]),
                    isDefault ? " [default]" : ""));
        }
        sb.append("╚══════════════════════════════════════════════╝");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builder for constructing FeatureVectors.
     * All features default to 0.5 (unknown/uncertain).
     */
    public static class Builder {
        private final float[] f = new float[TOTAL_FEATURES];
        private long timestamp = System.currentTimeMillis();
        private String source = "live";

        public Builder() {
            Arrays.fill(f, DEFAULT_VALUE);
        }

        // ── Generic setter ───────────────────────────────────────────
        public Builder set(int index, float value) {
            if (index >= 0 && index < TOTAL_FEATURES) f[index] = clamp(value);
            return this;
        }

        // ── Digital setters (D1–D12) ─────────────────────────────────
        public Builder d1_screenTimeHours(float v)       { f[0] = clamp(v); return this; }
        public Builder d2_screenTimeDeviation(float v)   { f[1] = clamp(v); return this; }
        public Builder d3_appSwitchCount(float v)        { f[2] = clamp(v); return this; }
        public Builder d4_rapidSwitchCount(float v)      { f[3] = clamp(v); return this; }
        public Builder d5_bingeSessionCount(float v)     { f[4] = clamp(v); return this; }
        public Builder d6_nightUsageMinutes(float v)     { f[5] = clamp(v); return this; }
        public Builder d7_unlockCount(float v)           { f[6] = clamp(v); return this; }
        public Builder d8_longestSessionMinutes(float v) { f[7] = clamp(v); return this; }
        public Builder d9_dominantAppPercent(float v)    { f[8] = clamp(v); return this; }
        public Builder d10_fragmentationScore(float v)   { f[9] = clamp(v); return this; }
        public Builder d11_passiveAppRatio(float v)      { f[10] = clamp(v); return this; }
        public Builder d12_hasLoopPattern(float v)       { f[11] = clamp(v); return this; }

        // ── Engagement Quality setters (D13–D14) ─────────────────────
        public Builder d13_scrollIntensity(float v)       { f[34] = clamp(v); return this; }
        public Builder d14_notificationReactivity(float v){ f[35] = clamp(v); return this; }

        // ── Psychological setters (P1–P10) ───────────────────────────
        public Builder p1_moodRisk(float v)              { f[12] = clamp(v); return this; }
        public Builder p2_stressLevel(float v)           { f[13] = clamp(v); return this; }
        public Builder p3_lonelinessLevel(float v)       { f[14] = clamp(v); return this; }
        public Builder p4_motivationDeficit(float v)     { f[15] = clamp(v); return this; }
        public Builder p5_sleepDeficit(float v)          { f[16] = clamp(v); return this; }
        public Builder p6_energyDeficit(float v)         { f[17] = clamp(v); return this; }
        public Builder p7_focusDeficit(float v)          { f[18] = clamp(v); return this; }
        public Builder p8_purposeDeficit(float v)        { f[19] = clamp(v); return this; }
        public Builder p9_addictionSelfScore(float v)    { f[20] = clamp(v); return this; }
        public Builder p10_consecutiveSadDays(float v)   { f[21] = clamp(v); return this; }

        // ── Contextual setters (C1–C6) ───────────────────────────────
        public Builder c1_workPressure(float v)          { f[22] = clamp(v); return this; }
        public Builder c2_socialSupportDeficit(float v)  { f[23] = clamp(v); return this; }
        public Builder c3_goalClarityDeficit(float v)    { f[24] = clamp(v); return this; }
        public Builder c4_exerciseDeficit(float v)       { f[25] = clamp(v); return this; }
        public Builder c5_screenFreeDeficit(float v)     { f[26] = clamp(v); return this; }
        public Builder c6_routineInstability(float v)    { f[27] = clamp(v); return this; }

        // ── Temporal setters (T1–T6) ─────────────────────────────────
        public Builder t1_screenTimeTrend3d(float v)     { f[28] = clamp(v); return this; }
        public Builder t2_moodStability7d(float v)       { f[29] = clamp(v); return this; }
        public Builder t3_sleepConsistency7d(float v)    { f[30] = clamp(v); return this; }
        public Builder t4_weekendWeekdayRatio(float v)   { f[31] = clamp(v); return this; }
        public Builder t5_postCheckInChange(float v)     { f[32] = clamp(v); return this; }
        public Builder t6_recoverySpeed(float v)         { f[33] = clamp(v); return this; }

        // ── Metadata ─────────────────────────────────────────────────
        public Builder timestamp(long ts)     { this.timestamp = ts; return this; }
        public Builder source(String src)     { this.source = src; return this; }

        /** Build the immutable FeatureVector. */
        @NonNull
        public FeatureVector build() {
            return new FeatureVector(Arrays.copyOf(f, TOTAL_FEATURES), timestamp, source);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private float avgRange(int start, int end) {
        float sum = 0f;
        for (int i = start; i <= end; i++) sum += features[i];
        return sum / (end - start + 1);
    }

    private int nonDefaultCountInRange(int start, int end) {
        int count = 0;
        for (int i = start; i <= end; i++) {
            if (Math.abs(features[i] - DEFAULT_VALUE) > 0.001f) count++;
        }
        return count;
    }

    private static String pct(float v) {
        return String.format(Locale.US, "%.0f%%", v * 100);
    }

    private static String featureIndex(int i) {
        if (i <= IDX_D12) return "D" + (i + 1) + " ";
        if (i <= IDX_P10) return "P" + (i - IDX_P1 + 1) + " ";
        if (i <= IDX_C6)  return "C" + (i - IDX_C1 + 1) + " ";
        if (i <= IDX_T6) return "T" + (i - IDX_T1 + 1) + " ";
        return "D" + (i - IDX_D13 + 13) + " ";
    }

    private static String riskBar(float v) {
        int filled = Math.round(v * 10);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "█" : "░");
        bar.append("]");
        return bar.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED — Feature Importance Weights
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clinical importance weight for each feature (0.0–1.0).
     * Higher = feature contributes more to risk classification.
     * Derived from mental health research on digital wellbeing.
     */
    public static final float[] FEATURE_WEIGHTS = {
            // D1–D12: Digital
            0.7f,  // D1  screenTimeHours — moderate signal
            0.8f,  // D2  screenTimeDeviation — strong signal (change from baseline)
            0.5f,  // D3  appSwitchCount — weaker standalone
            0.7f,  // D4  rapidSwitchCount — compulsive marker
            0.85f, // D5  bingeSessionCount — strong addiction marker
            0.9f,  // D6  nightUsageMinutes — circadian disruption
            0.6f,  // D7  unlockCount — moderate
            0.65f, // D8  longestSessionMinutes — hyperfocus/absorption
            0.5f,  // D9  dominantAppPercent — dependency marker
            0.6f,  // D10 fragmentationScore — attention disruption
            0.75f, // D11 passiveAppRatio — consumption vs creation
            0.8f,  // D12 hasLoopPattern — compulsive loop marker
            // P1–P10: Psychological
            0.95f, // P1  moodRisk — primary psychological signal
            0.9f,  // P2  stressLevel — major wellbeing indicator
            0.85f, // P3  lonelinessLevel — social isolation marker
            0.7f,  // P4  motivationDeficit — executive function
            0.8f,  // P5  sleepDeficit — physiological foundation
            0.65f, // P6  energyDeficit — physical state
            0.7f,  // P7  focusDeficit — cognitive function
            0.75f, // P8  purposeDeficit — existential wellbeing
            0.85f, // P9  addictionSelfScore — self-awareness marker
            0.95f, // P10 consecutiveSadDays — strongest depression signal
            // C1–C6: Contextual
            0.6f,  // C1  workPressure — environmental stressor
            0.7f,  // C2  socialSupportDeficit — protective factor
            0.55f, // C3  goalClarityDeficit — direction
            0.65f, // C4  exerciseDeficit — physical protective factor
            0.5f,  // C5  screenFreeDeficit — lifestyle balance
            0.6f,  // C6  routineInstability — structure
            // T1–T6: Temporal
            0.85f, // T1  screenTimeTrend3d — acceleration is most dangerous
            0.80f, // T2  moodStability7d — emotional volatility
            0.75f, // T3  sleepConsistency7d — erratic sleep
            0.55f, // T4  weekendWeekdayRatio — context-dependent use
            0.60f, // T5  postCheckInChange — awareness impact
            0.90f, // T6  recoverySpeed — emotional resilience
            // D13–D14: Engagement Quality
            0.80f, // D13 scrollIntensity — mindless scrolling marker
            0.75f  // D14 notificationReactivity — notification dependency
    };

    /**
     * Computes a weighted risk score using feature importance.
     * More clinically significant features contribute more.
     *
     * @return weighted risk 0.0–1.0
     */
    public float weightedRiskScore() {
        float weightedSum = 0f, weightTotal = 0f;
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            weightedSum += features[i] * FEATURE_WEIGHTS[i];
            weightTotal += FEATURE_WEIGHTS[i];
        }
        return weightTotal > 0 ? weightedSum / weightTotal : 0.5f;
    }

    /**
     * Confidence-adjusted risk: penalizes the risk estimate when data
     * is incomplete. Low completeness → risk pulled toward 0.5 (uncertain).
     *
     * <pre>
     *   adjustedRisk = risk * completeness + 0.5 * (1 - completeness)
     * </pre>
     */
    public float confidenceAdjustedRisk() {
        float raw = weightedRiskScore();
        return raw * dataCompleteness + DEFAULT_VALUE * (1f - dataCompleteness);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED — Temporal Freshness
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns a freshness score (0.0–1.0) based on how recently this
     * vector was extracted.
     *
     * <pre>
     *   < 1 hour:  1.0
     *   < 6 hours: 0.8
     *   < 24 hours: 0.5
     *   < 72 hours: 0.3
     *   > 72 hours: 0.1
     * </pre>
     */
    public float freshness() {
        long ageMs = System.currentTimeMillis() - extractionTimestamp;
        long hours = ageMs / (1000L * 60L * 60L);
        if (hours < 1) return 1.0f;
        if (hours < 6) return 0.8f;
        if (hours < 24) return 0.5f;
        if (hours < 72) return 0.3f;
        return 0.1f;
    }

    /** Whether this vector is stale (> 24 hours old). */
    public boolean isStale() {
        return freshness() < 0.5f;
    }

    /** Age of this vector in hours. */
    public float ageHours() {
        return (System.currentTimeMillis() - extractionTimestamp) / 3600000f;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED — Risk Profile
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns the top N risk-driving features as a RiskDriver array,
     * sorted by weighted contribution (feature value × importance weight).
     *
     * @param n number of top drivers to return
     */
    @NonNull
    public RiskDriver[] getTopRiskDrivers(int n) {
        RiskDriver[] all = new RiskDriver[TOTAL_FEATURES];
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            all[i] = new RiskDriver(i, FEATURE_NAMES[i], FEATURE_GROUPS[i],
                    features[i], FEATURE_WEIGHTS[i],
                    features[i] * FEATURE_WEIGHTS[i]);
        }
        // Sort by weighted contribution descending
        Arrays.sort(all, (a, b) -> Float.compare(b.weightedContribution, a.weightedContribution));
        return Arrays.copyOf(all, Math.min(n, TOTAL_FEATURES));
    }

    /**
     * Returns features that improved compared to a previous vector,
     * sorted by magnitude of improvement.
     */
    @NonNull
    public RiskDriver[] getImprovements(@NonNull FeatureVector previous) {
        float[] delta = deltaFrom(previous);
        int count = 0;
        for (float d : delta) if (d < -0.05f) count++;

        RiskDriver[] improvements = new RiskDriver[count];
        int idx = 0;
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            if (delta[i] < -0.05f) {
                improvements[idx++] = new RiskDriver(i, FEATURE_NAMES[i],
                        FEATURE_GROUPS[i], features[i], FEATURE_WEIGHTS[i], delta[i]);
            }
        }
        Arrays.sort(improvements, (a, b) -> Float.compare(a.weightedContribution, b.weightedContribution));
        return improvements;
    }

    /**
     * Returns features that worsened compared to a previous vector.
     */
    @NonNull
    public RiskDriver[] getDeteriorations(@NonNull FeatureVector previous) {
        float[] delta = deltaFrom(previous);
        int count = 0;
        for (float d : delta) if (d > 0.05f) count++;

        RiskDriver[] worse = new RiskDriver[count];
        int idx = 0;
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            if (delta[i] > 0.05f) {
                worse[idx++] = new RiskDriver(i, FEATURE_NAMES[i],
                        FEATURE_GROUPS[i], features[i], FEATURE_WEIGHTS[i], delta[i]);
            }
        }
        Arrays.sort(worse, (a, b) -> Float.compare(b.weightedContribution, a.weightedContribution));
        return worse;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED — Vector Blending
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Blends two vectors with a ratio. Useful for exponential moving average.
     *
     * @param other  the other vector to blend with
     * @param weight weight of the other vector (0.0 = all this, 1.0 = all other)
     * @return new blended FeatureVector
     */
    @NonNull
    public FeatureVector blend(@NonNull FeatureVector other, float weight) {
        weight = Math.max(0f, Math.min(1f, weight));
        float thisWeight = 1f - weight;
        float[] blended = new float[TOTAL_FEATURES];
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            blended[i] = clamp(features[i] * thisWeight + other.features[i] * weight);
        }
        return new FeatureVector(blended,
                Math.max(extractionTimestamp, other.extractionTimestamp), "blended");
    }

    /**
     * Returns an exponential moving average of a series of vectors.
     * Recent vectors are weighted more heavily.
     *
     * @param history ordered list oldest→newest
     * @param alpha   smoothing factor (0.0–1.0, higher = more weight on recent)
     */
    @NonNull
    public static FeatureVector exponentialMovingAverage(
            @NonNull FeatureVector[] history, float alpha) {
        if (history.length == 0) return empty();
        if (history.length == 1) return history[0];

        alpha = Math.max(0.1f, Math.min(0.9f, alpha));
        float[] ema = Arrays.copyOf(history[0].features, TOTAL_FEATURES);

        for (int t = 1; t < history.length; t++) {
            for (int i = 0; i < TOTAL_FEATURES; i++) {
                ema[i] = alpha * history[t].features[i] + (1f - alpha) * ema[i];
            }
        }
        return new FeatureVector(ema,
                history[history.length - 1].extractionTimestamp, "ema");
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED — JSON Serialization
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Serializes to a compact JSON string.
     * Format: {"f":[0.1,0.2,...],"ts":123456,"src":"live"}
     */
    @NonNull
    public String toJson() {
        StringBuilder sb = new StringBuilder("{\"f\":[");
        for (int i = 0; i < TOTAL_FEATURES; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(Locale.US, "%.3f", features[i]));
        }
        sb.append("],\"ts\":").append(extractionTimestamp);
        sb.append(",\"src\":\"").append(source).append("\"}");
        return sb.toString();
    }

    /**
     * Deserializes from a JSON string produced by {@link #toJson()}.
     * Falls back to empty() on parse failure.
     */
    @NonNull
    public static FeatureVector fromJson(@Nullable String json) {
        if (json == null || json.isEmpty()) return empty();
        try {
            // Extract features array
            int fStart = json.indexOf("[") + 1;
            int fEnd = json.indexOf("]");
            String[] parts = json.substring(fStart, fEnd).split(",");

            float[] values = new float[TOTAL_FEATURES];
            Arrays.fill(values, DEFAULT_VALUE);
            for (int i = 0; i < Math.min(parts.length, TOTAL_FEATURES); i++) {
                values[i] = clamp(Float.parseFloat(parts[i].trim()));
            }

            // Extract timestamp
            long ts = System.currentTimeMillis();
            int tsIdx = json.indexOf("\"ts\":");
            if (tsIdx >= 0) {
                int tsStart = tsIdx + 5;
                int tsEnd = json.indexOf(",", tsStart);
                if (tsEnd < 0) tsEnd = json.indexOf("}", tsStart);
                ts = Long.parseLong(json.substring(tsStart, tsEnd).trim());
            }

            // Extract source
            String src = "fromJson";
            int srcIdx = json.indexOf("\"src\":\"");
            if (srcIdx >= 0) {
                int srcStart = srcIdx + 7;
                int srcEnd = json.indexOf("\"", srcStart);
                src = json.substring(srcStart, srcEnd);
            }

            return new FeatureVector(values, ts, src);
        } catch (Exception e) {
            return empty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASS — RiskDriver
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Represents a single feature as a risk driver with its contribution.
     * Used by {@link #getTopRiskDrivers(int)}.
     */
    public static class RiskDriver {
        public final int index;
        public final String name;
        public final String group;
        public final float value;
        public final float importance;
        public final float weightedContribution;

        public RiskDriver(int index, String name, String group,
                          float value, float importance, float weightedContribution) {
            this.index = index;
            this.name = name;
            this.group = group;
            this.value = value;
            this.importance = importance;
            this.weightedContribution = weightedContribution;
        }

        /** Human-readable risk bar for this feature. */
        @NonNull
        public String getRiskBar() { return riskBar(value); }

        /** Feature label like "D6 nightUsageMinutes". */
        @NonNull
        public String getLabel() { return featureIndex(index) + name; }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.US, "%s%s %s %.0f%% (w=%.0f%%)",
                    featureIndex(index), name, riskBar(value),
                    value * 100, importance * 100);
        }
    }
}

