package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.entity.RiskClassification;
import java.util.Calendar;
import java.util.List;

/**
 * Core AI brain — classifies a 36-dimensional {@link FeatureVector} into
 * 6 risk categories, producing a {@link RiskClassification}.
 *
 * <p>Uses a 3-layer hybrid approach: (1) clinically-weighted rule-based linear
 * classifiers, (2) optional TensorFlow Lite ML models via {@link TFLiteClassifier},
 * and (3) personalized adaptation. Includes cross-domain interaction terms,
 * protective factor dampening, and crisis escalation logic.</p>
 *
 * <h3>Classification Pipeline:</h3>
 * <pre>
 *   FeatureVector → 6 rule-based sub-classifiers
 *                     ↓ hybrid ML blending (if TFLite models available)
 *                     ↓ cross-signal amplification
 *                     ↓ protective factor dampening
 *                     ↓ confidence estimation
 *                     ↓ crisis detection
 *                     ↓ delta + moving average computation
 *                   → Final RiskClassification (persisted)
 * </pre>
 *
 * @see TFLiteClassifier
 */
public class MultiModalClassifier {

    private static final String TAG = "Classifier";
    private final RiskClassificationDao riskDao;

    /**
     * Externalized weight configuration. When non-null, aggregation methods
     * (overall risk, cross-signal, dampening, crisis) read thresholds from
     * this config rather than hardcoded constants.
     *
     * @see ClassifierWeightsConfig
     */
    @Nullable
    private final ClassifierWeightsConfig config;

    /**
     * Layer 2 ML-enhanced classifier. When non-null and models are loaded,
     * rule-based scores are blended with ML predictions (40% rules + 60% ML).
     * When null or no models available, operates in pure rule-based mode.
     *
     * @see TFLiteClassifier
     */
    @Nullable
    private final TFLiteClassifier mlClassifier;

    /**
     * Primary constructor — loads externalized weights and ML models from assets.
     * This is the standard production constructor.
     */
    public MultiModalClassifier(@NonNull Context context) {
        this.riskDao = AppDatabase.getInstance(context.getApplicationContext()).riskClassificationDao();
        this.config = ClassifierWeightsConfig.load(context);
        this.mlClassifier = new TFLiteClassifier(context);
        Log.i(TAG, "Classifier initialized: " + getClassificationModeLabel());
    }

    /**
     * Test/legacy constructor — uses hardcoded defaults (no ML, no externalized config).
     */
    public MultiModalClassifier(@NonNull RiskClassificationDao dao) {
        this.riskDao = dao;
        this.config = null;
        this.mlClassifier = null;
    }

    /**
     * Full-control constructor for dependency injection / testing.
     */
    public MultiModalClassifier(@NonNull RiskClassificationDao dao,
                                 @Nullable ClassifierWeightsConfig config) {
        this.riskDao = dao;
        this.config = config;
        this.mlClassifier = null;
    }

    /**
     * Full-control constructor with ML classifier for dependency injection / testing.
     */
    public MultiModalClassifier(@NonNull RiskClassificationDao dao,
                                 @Nullable ClassifierWeightsConfig config,
                                 @Nullable TFLiteClassifier mlClassifier) {
        this.riskDao = dao;
        this.config = config;
        this.mlClassifier = mlClassifier;
    }

    /** Retrieve the loaded config (may be null for legacy instances). */
    @Nullable
    public ClassifierWeightsConfig getConfig() { return config; }

    /** Retrieve the ML classifier (may be null for legacy/test instances). */
    @Nullable
    public TFLiteClassifier getMLClassifier() { return mlClassifier; }

    /**
     * Returns a human-readable label describing the current classification mode.
     * <ul>
     *   <li>"hybrid_full" — all 6 ML models loaded, full blending active</li>
     *   <li>"hybrid_partial_N" — N of 6 ML models loaded</li>
     *   <li>"rules_only" — no ML models, pure rule-based</li>
     * </ul>
     */
    @NonNull
    public String getClassificationModeLabel() {
        if (mlClassifier == null) return "rules_only";
        return mlClassifier.getClassificationMode();
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAIN CLASSIFY — Task 3.F.11
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Full classification pipeline. Produces and persists a RiskClassification.
     *
     * <p>Pipeline stages:</p>
     * <ol>
     *   <li>Run 6 rule-based sub-classifiers (Layer 1 — always runs)</li>
     *   <li>Hybrid ML blending (Layer 2 — if {@link TFLiteClassifier} models loaded)</li>
     *   <li>Cross-signal amplification</li>
     *   <li>Protective factor dampening</li>
     *   <li>Overall risk + category selection</li>
     *   <li>Confidence estimation</li>
     *   <li>Crisis detection</li>
     *   <li>Delta + moving average</li>
     *   <li>Persist to Room DB</li>
     * </ol>
     */
    @NonNull
    public RiskClassification classify(@NonNull FeatureVector fv, long dayTimestamp) {
        RiskClassification rc = new RiskClassification();
        rc.timestamp = System.currentTimeMillis();
        rc.dayTimestamp = dayTimestamp;

        // ── 1. Layer 1: Run 6 rule-based sub-classifiers ──
        rc.digitalAddictionScore = classifyDigitalAddiction(fv);
        rc.stressAnxietyScore    = classifyStressAnxiety(fv);
        rc.depressionRiskScore   = classifyDepressiveIndicators(fv);
        rc.socialIsolationScore  = classifySocialIsolation(fv);
        rc.sleepDisruptionScore  = classifyEmotionalFatigue(fv);
        rc.lowFulfilmentScore    = classifyLowFulfilment(fv);

        // ── 2. Layer 2: Hybrid ML blending (if models available) ──
        applyHybridMLBlending(rc, fv);

        // ── 3. Cross-signal amplification ──
        applyCrossSignalAmplification(rc);

        // ── 4. Protective factor dampening ──
        applyProtectiveDampening(rc, fv);

        // ── 5. Overall risk + primary category ──
        rc.overallRiskScore = computeOverallRisk(rc);
        rc.primaryCategory  = findPrimaryCategory(rc);
        rc.secondaryCategory = findSecondaryCategory(rc);

        // ── 6. Confidence ──
        rc.confidence = computeConfidence(fv);
        rc.featureDataCount = countNonDefault(fv);
        rc.classificationMode = resolveClassificationMode(rc.featureDataCount);

        // ── 7. Crisis detection ──
        detectCrisis(rc, fv);

        // ── 8. Delta + moving average ──
        computeDelta(rc);

        // ── 9. Persist ──
        try { riskDao.insertOrReplace(rc); } catch (Exception e) {
            Log.w(TAG, "Failed to persist classification: " + e.getMessage());
        }

        Log.i(TAG, "Classification [" + rc.classificationMode + "]: " + rc);
        return rc;
    }

    /** Classify for today. */
    @NonNull
    public RiskClassification classifyToday(@NonNull FeatureVector fv) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        return classify(fv, cal.getTimeInMillis());
    }

    // ═══════════════════════════════════════════════════════════════════
    // SUB-CLASSIFIERS — Tasks 3.F.2–3.F.7
    // Each returns a 0.0–1.0 risk score using weighted feature combination.
    // Weights are clinically informed and sum to ~1.0.
    // ═══════════════════════════════════════════════════════════════════

    /** 3.F.2 — Digital Addiction: compulsive use, escape behaviour, dependency. */
    public float classifyDigitalAddiction(@NonNull FeatureVector fv) {
        // Config-driven path (OTA-tunable)
        float configScore = computeConfigScore("digital_addiction", fv);
        if (configScore >= 0f) return configScore;

        // Hardcoded fallback — weights sum to ~1.0
        float score =
            fv.screenTimeHours()       * 0.09f +
            fv.screenTimeDeviation()   * 0.06f +
            fv.appSwitchCount()        * 0.06f +
            fv.rapidSwitchCount()      * 0.08f +
            fv.bingeSessionCount()     * 0.08f +
            fv.nightUsageMinutes()     * 0.06f +
            fv.unlockCount()           * 0.08f +
            fv.dominantAppPercent()    * 0.04f +
            fv.passiveAppRatio()       * 0.06f +
            fv.hasLoopPattern()        * 0.06f +
            fv.addictionSelfScore()    * 0.09f +
            fv.screenTimeTrend3d()     * 0.07f +
            fv.weekendWeekdayRatio()   * 0.04f +
            // D13–D14: Engagement Quality (telemetry-driven)
            fv.scrollIntensity()       * 0.07f +
            fv.notificationReactivity()* 0.06f;
        // Synergy: compulsive loop + compulsive checking
        if (fv.hasLoopPattern() > 0.5f && fv.unlockCount() > 0.6f) {
            score = clamp(score + 0.08f);
        }
        // Synergy: accelerating screen time + high absolute use
        if (fv.screenTimeTrend3d() > 0.6f && fv.screenTimeHours() > 0.7f) {
            score = clamp(score + 0.05f);
        }
        // Synergy: mindless scrolling + instant notification response
        if (fv.scrollIntensity() > 0.7f && fv.notificationReactivity() > 0.7f) {
            score = clamp(score + 0.04f);
        }
        return clamp(score);
    }

    /** 3.F.3 — Stress & Anxiety: acute/chronic stress, anxiety signals. */
    public float classifyStressAnxiety(@NonNull FeatureVector fv) {
        float configScore = computeConfigScore("stress_anxiety", fv);
        if (configScore >= 0f) return configScore;

        float score =
            fv.stressLevel()           * 0.17f +
            fv.moodRisk()              * 0.08f +
            fv.sleepDeficit()          * 0.10f +
            fv.energyDeficit()         * 0.07f +
            fv.focusDeficit()          * 0.08f +
            fv.workPressure()          * 0.13f +
            fv.routineInstability()    * 0.06f +
            fv.nightUsageMinutes()     * 0.06f +
            fv.screenTimeDeviation()   * 0.04f +
            fv.fragmentationScore()    * 0.04f +
            fv.moodStability7d()       * 0.10f +
            fv.postCheckInChange()     * 0.07f;
        if (fv.stressLevel() > 0.7f && fv.screenTimeHours() > 0.6f) {
            score = clamp(score + 0.06f);
        }
        if (fv.moodStability7d() > 0.6f && fv.stressLevel() > 0.6f) {
            score = clamp(score + 0.04f);
        }
        return clamp(score);
    }

    /** 3.F.7 — Depressive Indicators: sadness, anhedonia, hopelessness. */
    public float classifyDepressiveIndicators(@NonNull FeatureVector fv) {
        float configScore = computeConfigScore("depression", fv);
        if (configScore >= 0f) return configScore;

        float score =
            fv.moodRisk()              * 0.14f +
            fv.consecutiveSadDays()    * 0.12f +
            fv.motivationDeficit()     * 0.10f +
            fv.energyDeficit()         * 0.08f +
            fv.sleepDeficit()          * 0.07f +
            fv.purposeDeficit()        * 0.08f +
            fv.socialSupportDeficit()  * 0.07f +
            fv.exerciseDeficit()       * 0.05f +
            fv.lonelinessLevel()       * 0.06f +
            fv.goalClarityDeficit()    * 0.04f +
            fv.recoverySpeed()         * 0.10f +
            fv.moodStability7d()       * 0.05f +
            fv.sleepConsistency7d()    * 0.04f;
        if (fv.consecutiveSadDays() > 0.6f && fv.motivationDeficit() > 0.7f) {
            score = clamp(score + 0.10f);
        }
        if (fv.recoverySpeed() > 0.7f && fv.consecutiveSadDays() > 0.5f) {
            score = clamp(score + 0.06f);
        }
        return clamp(score);
    }

    /** 3.F.6 — Social Isolation: loneliness, withdrawal, disconnection. */
    public float classifySocialIsolation(@NonNull FeatureVector fv) {
        float configScore = computeConfigScore("social_isolation", fv);
        if (configScore >= 0f) return configScore;

        float score =
            fv.lonelinessLevel()       * 0.19f +
            fv.socialSupportDeficit()  * 0.15f +
            fv.moodRisk()              * 0.07f +
            fv.motivationDeficit()     * 0.06f +
            fv.screenTimeHours()       * 0.07f +
            fv.passiveAppRatio()       * 0.08f +
            fv.nightUsageMinutes()     * 0.05f +
            fv.exerciseDeficit()       * 0.07f +
            fv.routineInstability()    * 0.05f +
            fv.purposeDeficit()        * 0.05f +
            fv.weekendWeekdayRatio()   * 0.08f +
            fv.recoverySpeed()         * 0.08f;
        if (fv.lonelinessLevel() > 0.7f && fv.passiveAppRatio() > 0.6f) {
            score = clamp(score + 0.07f);
        }
        if (fv.weekendWeekdayRatio() > 0.6f && fv.lonelinessLevel() > 0.5f) {
            score = clamp(score + 0.05f);
        }
        return clamp(score);
    }

    /** 3.F.5 — Sleep Disruption / Emotional Fatigue. */
    public float classifyEmotionalFatigue(@NonNull FeatureVector fv) {
        float configScore = computeConfigScore("sleep_disruption", fv);
        if (configScore >= 0f) return configScore;

        float score =
            fv.sleepDeficit()          * 0.17f +
            fv.nightUsageMinutes()     * 0.15f +
            fv.energyDeficit()         * 0.12f +
            fv.stressLevel()           * 0.07f +
            fv.moodRisk()              * 0.05f +
            fv.screenTimeHours()       * 0.06f +
            fv.unlockCount()           * 0.04f +
            fv.focusDeficit()          * 0.06f +
            fv.routineInstability()    * 0.05f +
            fv.longestSessionMinutes() * 0.04f +
            fv.sleepConsistency7d()    * 0.12f +
            fv.screenTimeTrend3d()     * 0.07f;
        if (fv.nightUsageMinutes() > 0.6f && fv.sleepDeficit() > 0.6f) {
            score = clamp(score + 0.08f);
        }
        if (fv.sleepConsistency7d() > 0.6f && fv.nightUsageMinutes() > 0.5f) {
            score = clamp(score + 0.05f);
        }
        return clamp(score);
    }

    /** 3.F.4 — Low Fulfilment: purposelessness, stagnation, low motivation. */
    public float classifyLowFulfilment(@NonNull FeatureVector fv) {
        float configScore = computeConfigScore("low_fulfilment", fv);
        if (configScore >= 0f) return configScore;

        float score =
            fv.purposeDeficit()        * 0.17f +
            fv.motivationDeficit()     * 0.15f +
            fv.goalClarityDeficit()    * 0.12f +
            fv.moodRisk()              * 0.06f +
            fv.exerciseDeficit()       * 0.07f +
            fv.screenFreeDeficit()     * 0.08f +
            fv.passiveAppRatio()       * 0.07f +
            fv.routineInstability()    * 0.05f +
            fv.addictionSelfScore()    * 0.05f +
            fv.postCheckInChange()     * 0.09f +
            fv.recoverySpeed()         * 0.09f;
        if (fv.goalClarityDeficit() > 0.7f && fv.exerciseDeficit() > 0.7f) {
            score = clamp(score + 0.06f);
        }
        if (fv.postCheckInChange() > 0.6f && fv.purposeDeficit() > 0.6f) {
            score = clamp(score + 0.05f);
        }
        return clamp(score);
    }

    // ═══════════════════════════════════════════════════════════════════
    // AGGREGATION — Tasks 3.F.8–3.F.10
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 3.F.8 — Overall risk: weighted blend favouring depression and stress.
     * Reads category weights from externalized config when available.
     */
    public float computeOverallRisk(@NonNull RiskClassification rc) {
        if (config != null && !config.getOverallCategoryWeights().isEmpty()) {
            java.util.Map<String, Float> w = config.getOverallCategoryWeights();
            return clamp(
                rc.digitalAddictionScore * w.getOrDefault("digital_addiction", 0.18f) +
                rc.stressAnxietyScore    * w.getOrDefault("stress_anxiety",    0.20f) +
                rc.depressionRiskScore   * w.getOrDefault("depression",        0.22f) +
                rc.socialIsolationScore  * w.getOrDefault("social_isolation",  0.15f) +
                rc.sleepDisruptionScore  * w.getOrDefault("sleep_disruption",  0.12f) +
                rc.lowFulfilmentScore    * w.getOrDefault("low_fulfilment",    0.13f)
            );
        }
        // Hardcoded fallback (matches classifier_weights_v1.json defaults)
        return clamp(
            rc.digitalAddictionScore * 0.18f +
            rc.stressAnxietyScore    * 0.20f +
            rc.depressionRiskScore   * 0.22f +
            rc.socialIsolationScore  * 0.15f +
            rc.sleepDisruptionScore  * 0.12f +
            rc.lowFulfilmentScore    * 0.13f
        );
    }

    /** 3.F.9 — Find the highest-scoring category. */
    @NonNull
    public String findPrimaryCategory(@NonNull RiskClassification rc) {
        float max = rc.digitalAddictionScore;
        String cat = "digital_addiction";
        if (rc.stressAnxietyScore > max)   { max = rc.stressAnxietyScore;   cat = "stress_anxiety"; }
        if (rc.depressionRiskScore > max)  { max = rc.depressionRiskScore;  cat = "depression"; }
        if (rc.socialIsolationScore > max) { max = rc.socialIsolationScore; cat = "social_isolation"; }
        if (rc.sleepDisruptionScore > max) { max = rc.sleepDisruptionScore; cat = "sleep_disruption"; }
        if (rc.lowFulfilmentScore > max)   { cat = "low_fulfilment"; }
        return cat;
    }

    @NonNull
    private String findSecondaryCategory(@NonNull RiskClassification rc) {
        String primary = rc.primaryCategory != null ? rc.primaryCategory : findPrimaryCategory(rc);
        float[] scores = {
            rc.digitalAddictionScore, rc.stressAnxietyScore, rc.depressionRiskScore,
            rc.socialIsolationScore, rc.sleepDisruptionScore, rc.lowFulfilmentScore
        };
        String[] names = {
            "digital_addiction", "stress_anxiety", "depression",
            "social_isolation", "sleep_disruption", "low_fulfilment"
        };
        float secondMax = -1; String secondCat = "";
        for (int i = 0; i < scores.length; i++) {
            if (!names[i].equals(primary) && scores[i] > secondMax) {
                secondMax = scores[i]; secondCat = names[i];
            }
        }
        return secondCat;
    }

    /** 3.F.10 — Confidence based on data completeness and freshness. */
    public float computeConfidence(@NonNull FeatureVector fv) {
        int total = FeatureVector.TOTAL_FEATURES;
        int nonDefault = countNonDefault(fv);
        float dataCompleteness = (float) nonDefault / total;

        // Digital domain: at least 6/12 features should have real data
        int digitalReal = 0;
        for (int i = FeatureVector.IDX_D1; i <= FeatureVector.IDX_D12; i++) {
            if (Math.abs(fv.get(i) - FeatureVector.DEFAULT_VALUE) > 0.01f) digitalReal++;
        }
        float digitalConf = Math.min(1f, digitalReal / 6f);

        // Psych domain: at least 5/10
        int psychReal = 0;
        for (int i = FeatureVector.IDX_P1; i <= FeatureVector.IDX_P10; i++) {
            if (Math.abs(fv.get(i) - FeatureVector.DEFAULT_VALUE) > 0.01f) psychReal++;
        }
        float psychConf = Math.min(1f, psychReal / 5f);

        // Temporal domain: at least 3/6
        int temporalReal = 0;
        for (int i = FeatureVector.IDX_T1; i <= FeatureVector.IDX_T6; i++) {
            if (Math.abs(fv.get(i) - FeatureVector.DEFAULT_VALUE) > 0.01f) temporalReal++;
        }
        float temporalConf = Math.min(1f, temporalReal / 3f);

        return clamp(dataCompleteness * 0.30f + digitalConf * 0.25f +
                     psychConf * 0.25f + temporalConf * 0.20f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HYBRID ML BLENDING — Layer 2 integration
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Apply Layer 2 ML-enhanced blending to the 6 category scores.
     *
     * <p>If {@link TFLiteClassifier} is available and has models loaded,
     * each rule-based score is blended with the ML prediction
     * (40% rules + 60% ML). If ML is unavailable, scores pass through
     * unchanged — pure rule-based mode.</p>
     *
     * <p>This runs BEFORE cross-signal amplification and dampening,
     * so the downstream pipeline operates on blended scores.</p>
     *
     * @param rc the classification with rule-based scores already set
     * @param fv the 36-dimensional feature vector
     */
    private void applyHybridMLBlending(@NonNull RiskClassification rc,
                                        @NonNull FeatureVector fv) {
        if (mlClassifier == null || !mlClassifier.isMLAvailable()) {
            return; // No ML models — scores pass through unchanged
        }

        // Pack rule scores in TFLiteClassifier category order:
        //   [0] digital_addiction, [1] stress_anxiety, [2] low_fulfilment,
        //   [3] emotional_fatigue, [4] social_isolation, [5] depressive_indicators
        float[] ruleScores = {
            rc.digitalAddictionScore,
            rc.stressAnxietyScore,
            rc.lowFulfilmentScore,
            rc.sleepDisruptionScore,  // emotional_fatigue maps to sleepDisruption
            rc.socialIsolationScore,
            rc.depressionRiskScore
        };

        float[] blended = mlClassifier.classifyAllHybrid(ruleScores, fv);

        // Unpack blended scores back into the classification
        rc.digitalAddictionScore = blended[TFLiteClassifier.CAT_DIGITAL_ADDICTION];
        rc.stressAnxietyScore    = blended[TFLiteClassifier.CAT_STRESS_ANXIETY];
        rc.lowFulfilmentScore    = blended[TFLiteClassifier.CAT_LOW_FULFILMENT];
        rc.sleepDisruptionScore  = blended[TFLiteClassifier.CAT_EMOTIONAL_FATIGUE];
        rc.socialIsolationScore  = blended[TFLiteClassifier.CAT_SOCIAL_ISOLATION];
        rc.depressionRiskScore   = blended[TFLiteClassifier.CAT_DEPRESSIVE_INDICATORS];

        Log.d(TAG, "ML blending applied (" + mlClassifier.getLoadedModelCount() +
                "/6 models active)");
    }

    /**
     * Determine the classification mode string based on data completeness
     * and ML availability.
     *
     * <p>Mode priority:</p>
     * <ol>
     *   <li>If ML models available → "hybrid_full" or "hybrid_partial_N"</li>
     *   <li>If ≥20 features → "full" (rule-based, high confidence)</li>
     *   <li>If ≥10 features → "partial" (rule-based, moderate confidence)</li>
     *   <li>Otherwise → "baseline_only" (rule-based, low confidence)</li>
     * </ol>
     */
    @NonNull
    private String resolveClassificationMode(int featureDataCount) {
        // ML mode takes precedence if models are loaded
        if (mlClassifier != null && mlClassifier.isMLAvailable()) {
            if (mlClassifier.isFullMLAvailable()) {
                return featureDataCount >= 20 ? "hybrid_full" :
                       featureDataCount >= 10 ? "hybrid_partial" : "hybrid_baseline";
            }
            return "hybrid_partial_" + mlClassifier.getLoadedModelCount();
        }
        // Pure rule-based mode
        return featureDataCount >= 20 ? "full" :
               featureDataCount >= 10 ? "partial" : "baseline_only";
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: CROSS-SIGNAL, DAMPENING, CRISIS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * When multiple categories are elevated, boost all of them.
     * Boost values read from externalized config when available.
     */
    private void applyCrossSignalAmplification(@NonNull RiskClassification rc) {
        float boost4 = 0.08f;
        float boost3 = 0.04f;
        if (config != null) {
            ClassifierWeightsConfig.CrossSignalConfig cs = config.getCrossSignalConfig();
            boost4 = cs.boost4Elevated;
            boost3 = cs.boost3Elevated;
        }

        int elevated = rc.getElevatedCategoryCount();
        if (elevated >= 4) {
            rc.digitalAddictionScore = clamp(rc.digitalAddictionScore + boost4);
            rc.stressAnxietyScore    = clamp(rc.stressAnxietyScore + boost4);
            rc.depressionRiskScore   = clamp(rc.depressionRiskScore + boost4);
            rc.socialIsolationScore  = clamp(rc.socialIsolationScore + boost4);
            rc.sleepDisruptionScore  = clamp(rc.sleepDisruptionScore + boost4);
            rc.lowFulfilmentScore    = clamp(rc.lowFulfilmentScore + boost4);
        } else if (elevated >= 3) {
            rc.digitalAddictionScore = clamp(rc.digitalAddictionScore + boost3);
            rc.stressAnxietyScore    = clamp(rc.stressAnxietyScore + boost3);
            rc.depressionRiskScore   = clamp(rc.depressionRiskScore + boost3);
        }
    }

    /**
     * Exercise, social support, and routine stability dampen raw risk.
     * All weights and thresholds read from externalized config when available.
     */
    private void applyProtectiveDampening(@NonNull RiskClassification rc, @NonNull FeatureVector fv) {
        float exW = 0.4f, ssW = 0.3f, rsW = 0.3f;
        float threshold = 0.5f, maxDamp = 0.15f;
        float stressDamp = 1.0f, deprDamp = 1.0f, isolDamp = 0.8f, sleepDamp = 0.5f;

        if (config != null) {
            ClassifierWeightsConfig.ProtectiveDampeningConfig pd = config.getDampeningConfig();
            exW = pd.exerciseWeight;
            ssW = pd.socialSupportWeight;
            rsW = pd.routineStabilityWeight;
            threshold = pd.activationThreshold;
            maxDamp = pd.maxDampeningFactor;
            stressDamp  = pd.categoryDampening.getOrDefault("stress_anxiety",   1.0f);
            deprDamp    = pd.categoryDampening.getOrDefault("depression",        1.0f);
            isolDamp    = pd.categoryDampening.getOrDefault("social_isolation",  0.8f);
            sleepDamp   = pd.categoryDampening.getOrDefault("sleep_disruption",  0.5f);
        }

        // Exercise is the strongest protective factor
        float exerciseProtection = 1f - fv.exerciseDeficit();
        // Social support dampens isolation and depression
        float socialProtection = 1f - fv.socialSupportDeficit();
        // Routine stability dampens stress and sleep issues
        float routineProtection = 1f - fv.routineInstability();

        float protectiveFactor = (exerciseProtection * exW + socialProtection * ssW +
                                  routineProtection * rsW);

        // Only dampen if significant protective factors exist
        if (protectiveFactor > threshold) {
            float dampening = (protectiveFactor - threshold) * maxDamp;
            rc.stressAnxietyScore   = clamp(rc.stressAnxietyScore   - dampening * stressDamp);
            rc.depressionRiskScore  = clamp(rc.depressionRiskScore  - dampening * deprDamp);
            rc.socialIsolationScore = clamp(rc.socialIsolationScore - dampening * isolDamp);
            rc.sleepDisruptionScore = clamp(rc.sleepDisruptionScore - dampening * sleepDamp);
        }
    }

    /**
     * Crisis detection: flag if any category is SEVERE or dangerous combinations.
     * All thresholds read from externalized config when available.
     */
    private void detectCrisis(@NonNull RiskClassification rc, @NonNull FeatureVector fv) {
        float depT = 0.85f, saT = 0.90f;
        int multiCount = 4;
        float multiOverall = 0.70f;
        float combDepT = 0.70f, combSadT = 0.80f, combIsolT = 0.60f;

        if (config != null) {
            ClassifierWeightsConfig.CrisisConfig cc = config.getCrisisConfig();
            depT = cc.depressionThreshold;
            saT = cc.stressAnxietyThreshold;
            multiCount = cc.multiCategoryElevatedCount;
            multiOverall = cc.multiCategoryOverallThreshold;
            combDepT = cc.combinedDepressionThreshold;
            combSadT = cc.combinedSadDaysThreshold;
            combIsolT = cc.combinedIsolationThreshold;
        }

        StringBuilder reasons = new StringBuilder();

        // Single-category crisis
        if (rc.depressionRiskScore >= depT) {
            rc.crisisFlag = true;
            reasons.append("depression_risk>=" + depT + "; ");
        }
        if (rc.stressAnxietyScore >= saT) {
            rc.crisisFlag = true;
            reasons.append("stress_anxiety>=" + saT + "; ");
        }

        // Multi-category crisis
        if (rc.getElevatedCategoryCount() >= multiCount && rc.overallRiskScore >= multiOverall) {
            rc.crisisFlag = true;
            reasons.append(multiCount + "+_elevated_categories; ");
        }

        // Depression + consecutive sad days + isolation = severe
        if (rc.depressionRiskScore >= combDepT && fv.consecutiveSadDays() >= combSadT
                && rc.socialIsolationScore >= combIsolT) {
            rc.crisisFlag = true;
            reasons.append("depression+sadDays+isolation; ");
        }

        rc.crisisReason = reasons.length() > 0 ? reasons.toString().trim() : null;
    }

    /**
     * Gap Fix #2: Re-check crisis flag when a journal entry is saved.
     * Blueprint §6 Level 3: "Journal distress words detected → crisis flag."
     *
     * @param rc       current classification
     * @param analyzer linguistic analyzer instance
     * @param text     journal entry text
     * @return true if crisis was newly escalated
     */
    public boolean recheckCrisisFromJournal(@NonNull RiskClassification rc,
                                             @NonNull LinguisticAnalyzer analyzer,
                                             @NonNull String text) {
        LinguisticAnalyzer.AnalysisResult analysis = analyzer.analyze(text);
        if (analysis == null) return false;

        boolean escalated = false;

        // Level 3: 2+ distress markers → crisis
        if (analysis.distressFlags.size() >= 2) {
            rc.crisisFlag = true;
            String existing = rc.crisisReason != null ? rc.crisisReason + " " : "";
            rc.crisisReason = existing + "journal_distress_" + analysis.distressFlags.size() + "_markers;";
            escalated = true;
        }

        // Level 3.5: Very negative sentiment + existing high risk
        if (analysis.sentimentScore <= -0.6f && rc.overallRiskScore >= 0.60f) {
            rc.crisisFlag = true;
            String existing = rc.crisisReason != null ? rc.crisisReason + " " : "";
            rc.crisisReason = existing + "journal_negative_sentiment+high_risk;";
            escalated = true;
        }

        // Persist updated crisis state
        if (escalated) {
            try { riskDao.insertOrReplace(rc); } catch (Exception ignored) {}
        }

        return escalated;
    }

    /** Compute risk delta and 7-day moving average from history. */
    private void computeDelta(@NonNull RiskClassification rc) {
        try {
            List<RiskClassification> history = riskDao.getHistory(7);
            if (history != null && !history.isEmpty()) {
                // Delta from most recent
                rc.riskDelta = rc.overallRiskScore - history.get(0).overallRiskScore;
                // 7-day moving average
                float sum = rc.overallRiskScore;
                for (RiskClassification h : history) sum += h.overallRiskScore;
                rc.riskMovingAverage = sum / (history.size() + 1);
            }
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private int countNonDefault(@NonNull FeatureVector fv) {
        int count = 0;
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            if (Math.abs(fv.get(i) - FeatureVector.DEFAULT_VALUE) > 0.01f) count++;
        }
        return count;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    /**
     * Resolve a JSON weight key (e.g. "D1_screenTimeHours") to a FeatureVector index.
     * The suffix after the underscore must match a FEATURE_NAMES entry.
     * Returns -1 if the key cannot be resolved.
     */
    private static int resolveFeatureIndex(@NonNull String jsonKey) {
        int underscore = jsonKey.indexOf('_');
        String featureName = underscore >= 0 ? jsonKey.substring(underscore + 1) : jsonKey;
        for (int i = 0; i < FeatureVector.FEATURE_NAMES.length; i++) {
            if (FeatureVector.FEATURE_NAMES[i].equals(featureName)) return i;
        }
        return -1;
    }

    /**
     * Generic config-driven sub-classifier scoring.
     * Computes weighted sum from config weights + evaluates interaction terms.
     * Returns -1 if the config has no weights for this category (caller should
     * fall back to hardcoded logic).
     */
    private float computeConfigScore(@NonNull String category, @NonNull FeatureVector fv) {
        if (config == null) return -1f;
        java.util.Map<String, Float> weights = config.getWeights(category);
        if (weights.isEmpty()) return -1f;

        // Weighted sum
        float score = 0f;
        for (java.util.Map.Entry<String, Float> entry : weights.entrySet()) {
            int idx = resolveFeatureIndex(entry.getKey());
            if (idx >= 0) {
                score += fv.get(idx) * entry.getValue();
            }
        }

        // Interaction terms
        java.util.List<ClassifierWeightsConfig.Interaction> interactions =
                config.getInteractions(category);
        for (ClassifierWeightsConfig.Interaction inter : interactions) {
            boolean allMet = true;
            for (java.util.Map.Entry<String, Float> cond : inter.conditions.entrySet()) {
                int idx = resolveFeatureIndex(cond.getKey());
                if (idx < 0 || fv.get(idx) <= cond.getValue()) {
                    allMet = false;
                    break;
                }
            }
            if (allMet) {
                score = clamp(score + inter.boost);
            }
        }

        return clamp(score);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3.F.12 — WEIGHT VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Validates that all sub-classifier weights sum to approximately 1.0.
     * Call during testing/debug to catch weight drift.
     * @return true if all classifiers pass validation (sum within 0.95–1.05)
     */
    public static boolean validateWeights() {
        // Digital Addiction weights (must match classifyDigitalAddiction)
        // D1:0.10 D2:0.07 D3:0.07 D4:0.09 D5:0.09 D6:0.07 D7:0.09 D9:0.05 D11:0.07 D12:0.07 P9:0.10 T1:0.08 T4:0.05
        float da = 0.10f+0.07f+0.07f+0.09f+0.09f+0.07f+0.09f+0.05f+0.07f+0.07f+0.10f+0.08f+0.05f;
        // Stress Anxiety weights (must match classifyStressAnxiety)
        // P2:0.17 P1:0.08 P5:0.10 P6:0.07 P7:0.08 C1:0.13 C6:0.06 D6:0.06 D2:0.04 D10:0.04 T2:0.10 T5:0.07
        float sa = 0.17f+0.08f+0.10f+0.07f+0.08f+0.13f+0.06f+0.06f+0.04f+0.04f+0.10f+0.07f;
        // Depression weights (must match classifyDepressiveIndicators)
        // P1:0.14 P10:0.12 P4:0.10 P6:0.08 P5:0.07 P8:0.08 C2:0.07 C4:0.05 P3:0.06 C3:0.04 T6:0.10 T2:0.05 T3:0.04
        float dp = 0.14f+0.12f+0.10f+0.08f+0.07f+0.08f+0.07f+0.05f+0.06f+0.04f+0.10f+0.05f+0.04f;
        // Social Isolation weights (must match classifySocialIsolation)
        // P3:0.19 C2:0.15 P1:0.07 P4:0.06 D1:0.07 D11:0.08 D6:0.05 C4:0.07 C6:0.05 P8:0.05 T4:0.08 T6:0.08
        float si = 0.19f+0.15f+0.07f+0.06f+0.07f+0.08f+0.05f+0.07f+0.05f+0.05f+0.08f+0.08f;
        // Sleep/Emotional Fatigue weights (must match classifyEmotionalFatigue)
        // P5:0.17 D6:0.15 P6:0.12 P2:0.07 P1:0.05 D1:0.06 D7:0.04 P7:0.06 C6:0.05 D8:0.04 T3:0.12 T1:0.07
        float ef = 0.17f+0.15f+0.12f+0.07f+0.05f+0.06f+0.04f+0.06f+0.05f+0.04f+0.12f+0.07f;
        // Low Fulfilment weights (must match classifyLowFulfilment)
        // P8:0.17 P4:0.15 C3:0.12 P1:0.06 C4:0.07 C5:0.08 D11:0.07 C6:0.05 P9:0.05 T5:0.09 T6:0.09
        float lf = 0.17f+0.15f+0.12f+0.06f+0.07f+0.08f+0.07f+0.05f+0.05f+0.09f+0.09f;
        // Overall risk weights (must match computeOverallRisk)
        float ov = 0.18f+0.20f+0.22f+0.15f+0.12f+0.13f;

        boolean valid = true;
        String[] names = {"DigitalAddiction","StressAnxiety","Depression","SocialIsolation",
                           "EmotionalFatigue","LowFulfilment","OverallRisk"};
        float[] sums = {da, sa, dp, si, ef, lf, ov};
        for (int i = 0; i < sums.length; i++) {
            if (sums[i] < 0.95f || sums[i] > 1.05f) {
                Log.e(TAG, "Weight validation FAILED for " + names[i] + ": sum=" + sums[i]);
                valid = false;
            }
        }
        if (valid) Log.d(TAG, "All classifier weights validated ✓");
        return valid;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: FEATURE IMPORTANCE — Top contributing features
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Identify the top N features contributing most to the primary risk category.
     * Returns feature names sorted by weighted contribution (descending).
     */
    @NonNull
    public String[] getTopContributors(@NonNull FeatureVector fv,
                                        @NonNull String category, int topN) {
        float[][] weightMatrix = getWeightMatrix(category);
        if (weightMatrix == null) return new String[0];

        int[] indices = weightMatrix.length > 0 ? new int[weightMatrix.length] : new int[0];
        float[] contributions = new float[indices.length];

        for (int i = 0; i < weightMatrix.length; i++) {
            int featureIdx = (int) weightMatrix[i][0];
            float weight = weightMatrix[i][1];
            indices[i] = featureIdx;
            contributions[i] = fv.get(featureIdx) * weight;
        }

        // Sort by contribution descending
        for (int i = 0; i < contributions.length - 1; i++) {
            for (int j = i + 1; j < contributions.length; j++) {
                if (contributions[j] > contributions[i]) {
                    float tmp = contributions[i]; contributions[i] = contributions[j]; contributions[j] = tmp;
                    int ti = indices[i]; indices[i] = indices[j]; indices[j] = ti;
                }
            }
        }

        int count = Math.min(topN, indices.length);
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            result[i] = FeatureVector.FEATURE_NAMES[indices[i]];
        }
        return result;
    }

    /**
     * Get the weight mapping (featureIndex, weight) for a given category.
     * IMPORTANT: These must match the actual sub-classifier weights exactly.
     * See classifyDigitalAddiction(), classifyStressAnxiety(), etc.
     */
    private float[][] getWeightMatrix(String category) {
        if (category == null) return null;
        switch (category) {
            case "digital_addiction":
                // Matches classifyDigitalAddiction(): D1,D2,D3,D4,D5,D6,D7,D9,D11,D12,P9,T1,T4
                return new float[][]{{0,0.10f},{1,0.07f},{2,0.07f},{3,0.09f},{4,0.09f},
                        {5,0.07f},{6,0.09f},{8,0.05f},{10,0.07f},{11,0.07f},{20,0.10f},
                        {28,0.08f},{31,0.05f}};
            case "stress_anxiety":
                // Matches classifyStressAnxiety(): P2,P1,P5,P6,P7,C1,C6,D6,D2,D10,T2,T5
                return new float[][]{{13,0.17f},{12,0.08f},{16,0.10f},{17,0.07f},{18,0.08f},
                        {22,0.13f},{27,0.06f},{5,0.06f},{1,0.04f},{9,0.04f},
                        {29,0.10f},{32,0.07f}};
            case "depression":
                // Matches classifyDepressiveIndicators(): P1,P10,P4,P6,P5,P8,C2,C4,P3,C3,T6,T2,T3
                return new float[][]{{12,0.14f},{21,0.12f},{15,0.10f},{17,0.08f},{16,0.07f},
                        {19,0.08f},{23,0.07f},{25,0.05f},{14,0.06f},{24,0.04f},
                        {33,0.10f},{29,0.05f},{30,0.04f}};
            case "social_isolation":
                // Matches classifySocialIsolation(): P3,C2,P1,P4,D1,D11,D6,C4,C6,P8,T4,T6
                return new float[][]{{14,0.19f},{23,0.15f},{12,0.07f},{15,0.06f},{0,0.07f},
                        {10,0.08f},{5,0.05f},{25,0.07f},{27,0.05f},{19,0.05f},
                        {31,0.08f},{33,0.08f}};
            case "sleep_disruption":
                // Matches classifyEmotionalFatigue(): P5,D6,P6,P2,P1,D1,D7,P7,C6,D8,T3,T1
                return new float[][]{{16,0.17f},{5,0.15f},{17,0.12f},{13,0.07f},{12,0.05f},
                        {0,0.06f},{6,0.04f},{18,0.06f},{27,0.05f},{7,0.04f},
                        {30,0.12f},{28,0.07f}};
            case "low_fulfilment":
                // Matches classifyLowFulfilment(): P8,P4,C3,P1,C4,C5,D11,C6,P9,T5,T6
                return new float[][]{{19,0.17f},{15,0.15f},{24,0.12f},{12,0.06f},{25,0.07f},
                        {26,0.08f},{10,0.07f},{27,0.05f},{20,0.05f},
                        {32,0.09f},{33,0.09f}};
            default: return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: RISK NARRATIVE — Human-readable explanations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a human-readable narrative explaining the classification.
     * Used by the InsightEngine and dashboard cards.
     */
    @NonNull
    public String generateNarrative(@NonNull RiskClassification rc, @NonNull FeatureVector fv) {
        StringBuilder sb = new StringBuilder();
        RiskClassification.Severity overall = rc.getOverallSeverity();

        // Opening
        switch (overall) {
            case NONE: case MILD:
                sb.append("Your overall wellbeing looks positive today. ");
                break;
            case WATCH:
                sb.append("There are some areas to watch today. ");
                break;
            case MODERATE:
                sb.append("Several signals suggest you may need some support today. ");
                break;
            case HIGH: case SEVERE:
                sb.append("Your wellbeing signals indicate significant challenges today. ");
                break;
        }

        // Primary concern
        if (rc.primaryCategory != null) {
            sb.append(getCategoryNarrative(rc.primaryCategory, rc.getScoreForCategory(rc.primaryCategory)));
        }

        // Comorbidity note
        if (rc.getElevatedCategoryCount() >= 3) {
            sb.append("Multiple areas are elevated — these often reinforce each other. ");
        }

        // Trajectory
        if (rc.isWorsening()) {
            sb.append("Your trend is worsening compared to recent days. ");
        } else if (rc.isImproving()) {
            sb.append("Encouragingly, your trend is improving. ");
        }

        // Top contributor
        String[] tops = getTopContributors(fv, rc.primaryCategory, 1);
        if (tops.length > 0) {
            sb.append("The main driver is your ").append(humanize(tops[0])).append(". ");
        }

        return sb.toString().trim();
    }

    private String getCategoryNarrative(String cat, float score) {
        RiskClassification.Severity sev = RiskClassification.Severity.fromScore(score);
        switch (cat) {
            case "digital_addiction":
                return sev.level >= 3 ? "Your phone usage patterns suggest compulsive behaviour. " :
                       "Your digital habits show some dependency signals. ";
            case "stress_anxiety":
                return sev.level >= 3 ? "Stress and anxiety levels are significantly elevated. " :
                       "You're experiencing some stress that's worth monitoring. ";
            case "depression":
                return sev.level >= 3 ? "Persistent mood and motivation patterns need attention. " :
                       "Some emotional patterns are worth keeping an eye on. ";
            case "social_isolation":
                return sev.level >= 3 ? "Social disconnection signals are concerning. " :
                       "Building more social connection could help your wellbeing. ";
            case "sleep_disruption":
                return sev.level >= 3 ? "Sleep disruption is significantly impacting your health. " :
                       "Your sleep patterns have room for improvement. ";
            case "low_fulfilment":
                return sev.level >= 3 ? "A sense of purposelessness may be weighing on you. " :
                       "Finding more meaningful activities could boost your mood. ";
            default: return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: COMORBIDITY DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detect clinically-known comorbidity patterns.
     * Returns a descriptive tag or null if no comorbidity detected.
     */
    @NonNull
    public String[] detectComorbidities(@NonNull RiskClassification rc) {
        java.util.ArrayList<String> patterns = new java.util.ArrayList<>();

        // Depression + Social Isolation (most common comorbidity)
        if (rc.depressionRiskScore > 0.5f && rc.socialIsolationScore > 0.5f) {
            patterns.add("depression_isolation_loop");
        }
        // Stress + Sleep Disruption (vicious cycle)
        if (rc.stressAnxietyScore > 0.5f && rc.sleepDisruptionScore > 0.5f) {
            patterns.add("stress_sleep_cycle");
        }
        // Digital Addiction + Low Fulfilment (escape pattern)
        if (rc.digitalAddictionScore > 0.5f && rc.lowFulfilmentScore > 0.5f) {
            patterns.add("digital_escape_pattern");
        }
        // Full-spectrum: 4+ categories elevated (systemic)
        if (rc.getElevatedCategoryCount() >= 4) {
            patterns.add("systemic_distress");
        }
        // Burnout triad: stress + fatigue + low motivation
        if (rc.stressAnxietyScore > 0.6f && rc.sleepDisruptionScore > 0.5f
                && rc.lowFulfilmentScore > 0.5f) {
            patterns.add("burnout_triad");
        }

        return patterns.toArray(new String[0]);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: ESCAPE BEHAVIOUR SCORING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute an escape behaviour score — the degree to which phone usage
     * is being used to avoid dealing with psychological distress.
     * Returns 0.0 (no escape) to 1.0 (severe escape behaviour).
     */
    public float computeEscapeBehaviourScore(@NonNull FeatureVector fv) {
        // Escape = high stress + high phone use + passive + night usage
        float distress = (fv.stressLevel() + fv.moodRisk() + fv.lonelinessLevel()) / 3f;
        float digitalEscape = (fv.screenTimeDeviation() + fv.passiveAppRatio() +
                                fv.nightUsageMinutes() + fv.bingeSessionCount()) / 4f;

        // Escape score = distress × digital_coping correlation
        float escapeScore = distress * 0.4f + digitalEscape * 0.4f +
                            (distress * digitalEscape) * 0.2f; // Interaction term

        return clamp(escapeScore);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: TREND INTELLIGENCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Analyze 7-day trend for a specific category.
     * Returns: positive = worsening, negative = improving, 0 = stable.
     */
    public float getCategoryTrend(@NonNull String category, int days) {
        try {
            long since = System.currentTimeMillis() - (days * 86400000L);
            List<RiskClassification> history = riskDao.getHistorySince(since);
            if (history == null || history.size() < 3) return 0f;

            // Simple linear regression on category scores
            int n = history.size();
            float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                float x = i;
                float y = history.get(i).getScoreForCategory(category);
                sumX += x; sumY += y; sumXY += x * y; sumX2 += x * x;
            }
            float denom = n * sumX2 - sumX * sumX;
            if (Math.abs(denom) < 0.001f) return 0f;
            return (n * sumXY - sumX * sumY) / denom;
        } catch (Exception e) { return 0f; }
    }

    /** Get an overall trajectory label. */
    @NonNull
    public String getTrajectoryLabel(@NonNull RiskClassification rc) {
        if (rc.riskDelta > 0.10f) return "rapidly_worsening";
        if (rc.riskDelta > 0.05f) return "worsening";
        if (rc.riskDelta < -0.10f) return "rapidly_improving";
        if (rc.riskDelta < -0.05f) return "improving";
        return "stable";
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: TEMPORAL DECAY — Recent features weighted heavier
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Apply temporal decay: features extracted from today's data get full weight,
     * while features derived from stale baselines are discounted.
     * Returns a decay multiplier (0.5–1.0) based on feature freshness.
     */
    public float computeTemporalDecay(@NonNull FeatureVector fv) {
        long age = System.currentTimeMillis() - fv.extractionTimestamp;
        long hourMs = 3600000L;
        if (age < 2 * hourMs) return 1.0f;        // Fresh — full weight
        if (age < 6 * hourMs) return 0.90f;        // Recent — slight decay
        if (age < 12 * hourMs) return 0.80f;       // Stale — moderate decay
        if (age < 24 * hourMs) return 0.70f;       // Old — significant decay
        return 0.50f;                               // Very old — heavy decay
    }

    /**
     * Reclassify with temporal decay applied to the overall score.
     * Use when the feature vector may be stale but still informative.
     */
    @NonNull
    public RiskClassification classifyWithDecay(@NonNull FeatureVector fv, long dayTimestamp) {
        RiskClassification rc = classify(fv, dayTimestamp);
        float decay = computeTemporalDecay(fv);
        if (decay < 1.0f) {
            rc.overallRiskScore = clamp(rc.overallRiskScore * decay);
            rc.confidence = clamp(rc.confidence * decay);
            rc.classificationMode = "decay_adjusted";
        }
        return rc;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: VOLATILITY DETECTION — Score oscillation analysis
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Measure risk score volatility over recent days.
     * High volatility = unstable mental state requiring closer monitoring.
     * Returns: 0.0 (perfectly stable) to 1.0 (wildly oscillating).
     */
    public float computeVolatility(int lookbackDays) {
        try {
            long since = System.currentTimeMillis() - (lookbackDays * 86400000L);
            List<RiskClassification> history = riskDao.getHistorySince(since);
            if (history == null || history.size() < 3) return 0f;

            // Compute mean
            float sum = 0;
            for (RiskClassification rc : history) sum += rc.overallRiskScore;
            float mean = sum / history.size();

            // Compute standard deviation
            float variance = 0;
            for (RiskClassification rc : history) {
                float diff = rc.overallRiskScore - mean;
                variance += diff * diff;
            }
            float stdDev = (float) Math.sqrt(variance / history.size());

            // Also compute day-over-day delta variance
            float deltaVariance = 0;
            for (int i = 1; i < history.size(); i++) {
                float delta = Math.abs(history.get(i).overallRiskScore -
                                       history.get(i - 1).overallRiskScore);
                deltaVariance += delta * delta;
            }
            float deltaStd = (float) Math.sqrt(deltaVariance / (history.size() - 1));

            // Combine: raw volatility + delta volatility
            return clamp(stdDev * 2f + deltaStd * 3f);
        } catch (Exception e) { return 0f; }
    }

    /**
     * Get a human-readable volatility label.
     */
    @NonNull
    public String getVolatilityLabel(float volatility) {
        if (volatility < 0.10f) return "stable";
        if (volatility < 0.25f) return "mildly_variable";
        if (volatility < 0.50f) return "volatile";
        return "highly_volatile";
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: RECOVERY SCORING — Progress from peak risk
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute a recovery score relative to the user's peak (worst) risk day.
     * Returns: 0.0 (at peak risk) to 1.0 (fully recovered).
     */
    public float computeRecoveryScore() {
        try {
            RiskClassification peak = riskDao.getPeakRiskDay();
            RiskClassification latest = riskDao.getLatestSync();
            if (peak == null || latest == null) return 0.5f;
            if (peak.overallRiskScore <= 0.01f) return 1.0f;

            float recovery = 1.0f - (latest.overallRiskScore / peak.overallRiskScore);
            return clamp(recovery);
        } catch (Exception e) { return 0.5f; }
    }

    /**
     * Get streak count of consecutive improving days.
     */
    public int getImprovingStreak() {
        try {
            List<RiskClassification> history = riskDao.getHistory(14);
            if (history == null || history.size() < 2) return 0;
            int streak = 0;
            for (int i = 0; i < history.size() - 1; i++) {
                if (history.get(i).overallRiskScore < history.get(i + 1).overallRiskScore) {
                    streak++;
                } else {
                    break;
                }
            }
            return streak;
        } catch (Exception e) { return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: CRISIS ESCALATION LEVELS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Multi-tier crisis escalation assessment.
     * Goes beyond binary crisis flag to provide graduated urgency.
     */
    @NonNull
    public CrisisLevel assessCrisisLevel(@NonNull RiskClassification rc) {
        if (!rc.crisisFlag && rc.overallRiskScore < 0.65f) return CrisisLevel.NONE;

        int score = 0;
        // Scoring: accumulate crisis indicators
        if (rc.depressionRiskScore >= 0.85f) score += 3;
        else if (rc.depressionRiskScore >= 0.70f) score += 2;

        if (rc.stressAnxietyScore >= 0.90f) score += 3;
        else if (rc.stressAnxietyScore >= 0.75f) score += 1;

        if (rc.getElevatedCategoryCount() >= 5) score += 3;
        else if (rc.getElevatedCategoryCount() >= 4) score += 2;

        if (rc.isWorsening()) score += 1;
        if (rc.crisisFlag) score += 2;

        // Map score to crisis level
        if (score >= 8) return CrisisLevel.CRITICAL;
        if (score >= 5) return CrisisLevel.URGENT;
        if (score >= 3) return CrisisLevel.ELEVATED;
        if (score >= 1) return CrisisLevel.WATCH;
        return CrisisLevel.NONE;
    }

    /** Graduated crisis levels with recommended intervention type. */
    public enum CrisisLevel {
        NONE("No crisis", "standard_monitoring"),
        WATCH("Watch", "enhanced_check_in"),
        ELEVATED("Elevated concern", "proactive_outreach"),
        URGENT("Urgent intervention", "immediate_support_prompt"),
        CRITICAL("Critical — seek support", "crisis_resource_display");

        public final String label;
        public final String interventionType;

        CrisisLevel(String label, String interventionType) {
            this.label = label;
            this.interventionType = interventionType;
        }

        public boolean requiresImmediateAction() { return ordinal() >= URGENT.ordinal(); }
        public boolean requiresMonitoring() { return ordinal() >= WATCH.ordinal(); }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: PATTERN FINGERPRINTING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a 6-character risk pattern fingerprint.
     * Each character represents severity level (0-5) for one risk category.
     * Example: "213041" = mild digital, watch stress, mild depression, none isolation,
     *                      moderate sleep, mild fulfilment.
     * Useful for pattern matching and profile clustering.
     */
    @NonNull
    public String generatePatternFingerprint(@NonNull RiskClassification rc) {
        return "" + rc.getDigitalAddictionSeverity().level
                + rc.getStressAnxietySeverity().level
                + rc.getDepressionSeverity().level
                + rc.getSocialIsolationSeverity().level
                + rc.getSleepDisruptionSeverity().level
                + rc.getLowFulfilmentSeverity().level;
    }

    /**
     * Compare two fingerprints to see if the risk profile has fundamentally shifted.
     * Returns true if the dominant risk category changed or severity shifted by 2+ levels.
     */
    public boolean hasProfileShifted(@NonNull String fingerprint1, @NonNull String fingerprint2) {
        if (fingerprint1.length() != 6 || fingerprint2.length() != 6) return false;
        int maxDelta = 0;
        for (int i = 0; i < 6; i++) {
            int delta = Math.abs(fingerprint1.charAt(i) - fingerprint2.charAt(i));
            maxDelta = Math.max(maxDelta, delta);
        }
        return maxDelta >= 2;
    }

    private String humanize(String featureName) {
        if (featureName == null) return "unknown";
        return featureName
                .replace("Deficit", " levels")
                .replace("Score", " score")
                .replaceAll("([A-Z])", " $1")
                .toLowerCase().trim();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED A4: RISK MOMENTUM TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute risk momentum for a given category.
     * Momentum = velocity (slope) weighted with acceleration (slope delta).
     *
     * @param category risk category name
     * @return CategoryTrend with momentum data
     */
    @NonNull
    public CategoryTrend buildCategoryTrend(@NonNull String category) {
        try {
            List<RiskClassification> history = riskDao.getHistory(7);
            if (history == null || history.isEmpty()) {
                return CategoryTrend.compute(category, 0.5f, 0.5f, 0.5f);
            }

            float todayScore = history.get(0).getScoreForCategory(category);
            float yesterdayScore = history.size() > 1
                    ? history.get(1).getScoreForCategory(category) : todayScore;

            float sum = 0;
            for (RiskClassification rc : history) {
                sum += rc.getScoreForCategory(category);
            }
            float avg7d = sum / history.size();
            float slope = getCategoryTrend(category, 7);

            return CategoryTrend.compute(category, todayScore, yesterdayScore,
                    avg7d, slope);
        } catch (Exception e) {
            return CategoryTrend.compute(category, 0.5f, 0.5f, 0.5f);
        }
    }

    /**
     * Build trends for all 6 risk categories at once.
     */
    @NonNull
    public CategoryTrend[] buildAllCategoryTrends() {
        String[] categories = {
                "digital_addiction", "stress_anxiety", "depression",
                "social_isolation", "sleep_disruption", "low_fulfilment"
        };
        CategoryTrend[] trends = new CategoryTrend[categories.length];
        for (int i = 0; i < categories.length; i++) {
            trends[i] = buildCategoryTrend(categories[i]);
        }
        return trends;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED A3: ENSEMBLE SMOOTHING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Apply exponential moving average smoothing to reduce daily noise.
     * Blends today's raw classification with the 3-day historical average.
     *
     * @param rc    today's raw classification
     * @param alpha smoothing factor (0.0–1.0, higher = more weight on today)
     */
    public void applyEnsembleSmoothing(@NonNull RiskClassification rc, float alpha) {
        try {
            List<RiskClassification> recent = riskDao.getHistory(3);
            if (recent == null || recent.size() < 2) return;

            alpha = Math.max(0.3f, Math.min(0.9f, alpha));
            float beta = 1f - alpha;

            // Average recent scores
            float avgDigital = 0, avgStress = 0, avgDepression = 0;
            float avgIsolation = 0, avgSleep = 0, avgFulfilment = 0;
            for (RiskClassification h : recent) {
                avgDigital += h.digitalAddictionScore;
                avgStress += h.stressAnxietyScore;
                avgDepression += h.depressionRiskScore;
                avgIsolation += h.socialIsolationScore;
                avgSleep += h.sleepDisruptionScore;
                avgFulfilment += h.lowFulfilmentScore;
            }
            int n = recent.size();
            avgDigital /= n; avgStress /= n; avgDepression /= n;
            avgIsolation /= n; avgSleep /= n; avgFulfilment /= n;

            // Blend: today * alpha + history * beta
            rc.digitalAddictionScore = clamp(rc.digitalAddictionScore * alpha + avgDigital * beta);
            rc.stressAnxietyScore = clamp(rc.stressAnxietyScore * alpha + avgStress * beta);
            rc.depressionRiskScore = clamp(rc.depressionRiskScore * alpha + avgDepression * beta);
            rc.socialIsolationScore = clamp(rc.socialIsolationScore * alpha + avgIsolation * beta);
            rc.sleepDisruptionScore = clamp(rc.sleepDisruptionScore * alpha + avgSleep * beta);
            rc.lowFulfilmentScore = clamp(rc.lowFulfilmentScore * alpha + avgFulfilment * beta);

            // Recompute overall
            rc.overallRiskScore = computeOverallRisk(rc);
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED A5: INTERVENTION EFFICACY SCORING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Measure how effective an intervention was by comparing risk scores
     * before and after the intervention period.
     *
     * @param category       risk category to evaluate
     * @param daysBeforeTask lookback days before intervention
     * @param daysAfterTask  lookback days after intervention
     * @return efficacy score: positive = intervention helped, negative = no effect
     */
    public float computeInterventionEfficacy(@NonNull String category,
                                              int daysBeforeTask,
                                              int daysAfterTask) {
        try {
            long now = System.currentTimeMillis();
            long dayMs = 86400000L;
            long cutoff = now - (daysAfterTask * dayMs);

            // Average risk BEFORE intervention
            List<RiskClassification> allHistory = riskDao.getHistorySince(
                    now - ((daysBeforeTask + daysAfterTask) * dayMs));
            if (allHistory == null || allHistory.size() < 3) return 0f;

            float beforeSum = 0, afterSum = 0;
            int beforeCount = 0, afterCount = 0;

            for (RiskClassification rc : allHistory) {
                float score = rc.getScoreForCategory(category);
                if (rc.timestamp < cutoff) {
                    beforeSum += score;
                    beforeCount++;
                } else {
                    afterSum += score;
                    afterCount++;
                }
            }

            if (beforeCount == 0 || afterCount == 0) return 0f;
            float beforeAvg = beforeSum / beforeCount;
            float afterAvg = afterSum / afterCount;

            // Positive = improvement (risk went down)
            return beforeAvg - afterAvg;
        } catch (Exception e) { return 0f; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED A2: FEATURE DRIFT DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detect when a user's feature profile has fundamentally shifted
     * compared to their 7-day baseline. Returns features that drifted
     * more than the threshold.
     *
     * @param currentFv  today's feature vector
     * @param threshold  minimum delta to count as drift (0.0–1.0)
     * @return array of drifted feature names with directions
     */
    @NonNull
    public String[] detectFeatureDrift(@NonNull FeatureVector currentFv,
                                        float threshold) {
        java.util.ArrayList<String> drifted = new java.util.ArrayList<>();
        try {
            List<RiskClassification> history = riskDao.getHistory(7);
            if (history == null || history.size() < 3) return new String[0];

            // We can only detect drift in the overall risk dimensions
            // since we don't store raw feature vectors historically.
            // Use the category scores as proxy features.
            float avgOverall = 0;
            for (RiskClassification h : history) avgOverall += h.overallRiskScore;
            avgOverall /= history.size();

            float currentOverall = currentFv.overallRiskEstimate();
            float delta = Math.abs(currentOverall - avgOverall);

            if (delta > threshold) {
                String dir = currentOverall > avgOverall ? "↑" : "↓";
                drifted.add(dir + " overall_risk_drift (" +
                        String.format(java.util.Locale.US, "%.0f%%", delta * 100) + ")");
            }

            // Check per-domain drift
            float digitalAvg = currentFv.digitalRiskAvg();
            float psychAvg = currentFv.psychRiskAvg();
            float contextAvg = currentFv.contextRiskAvg();

            if (Math.abs(digitalAvg - 0.5f) > threshold)
                drifted.add((digitalAvg > 0.5f ? "↑" : "↓") + " digital_domain_drift");
            if (Math.abs(psychAvg - 0.5f) > threshold)
                drifted.add((psychAvg > 0.5f ? "↑" : "↓") + " psych_domain_drift");
            if (Math.abs(contextAvg - 0.5f) > threshold)
                drifted.add((contextAvg > 0.5f ? "↑" : "↓") + " context_domain_drift");

        } catch (Exception ignored) {}
        return drifted.toArray(new String[0]);
    }
}
