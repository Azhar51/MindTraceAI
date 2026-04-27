package com.mindtrace.ai.ai;

import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Validates externalized classifier weight JSON before it is applied.
 *
 * <h3>Purpose:</h3>
 * <p>Prevents malformed OTA weight updates from corrupting AI risk scores.
 * Run this validator on any incoming JSON <b>before</b> writing it to the
 * override file ({@code /data/data/<pkg>/files/classifier_weights.json}).</p>
 *
 * <h3>Checks performed:</h3>
 * <ol>
 *   <li>Schema structure: required top-level keys present</li>
 *   <li>Weight completeness: each category has a {@code weights} map</li>
 *   <li>Weight sum: per-category weights sum to ~1.0 (±0.05 tolerance)</li>
 *   <li>Weight range: individual weights are in [0.0, 1.0]</li>
 *   <li>Feature key validity: all weight keys resolve to known features</li>
 *   <li>Interaction validity: conditions reference valid features</li>
 *   <li>Overall risk weights: category weights sum to ~1.0</li>
 *   <li>Threshold sanity: crisis thresholds are in [0.0, 1.0]</li>
 * </ol>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   ConfigValidator.ValidationResult result = ConfigValidator.validate(jsonString);
 *   if (result.isValid()) {
 *       // Safe to write to override file
 *   } else {
 *       Log.e(TAG, "Validation failed: " + result.getErrors());
 *   }
 * </pre>
 *
 * @see ClassifierWeightsConfig
 */
public final class ConfigValidator {

    private static final String TAG = "ConfigValidator";

    /** Weight sum tolerance: ±5% from 1.0. */
    private static final float WEIGHT_SUM_TOLERANCE = 0.05f;

    /** The 6 required sub-classifier category keys. */
    private static final String[] REQUIRED_CATEGORIES = {
        "digital_addiction", "stress_anxiety", "depression",
        "social_isolation", "sleep_disruption", "low_fulfilment"
    };

    /** Known feature name set — built from FeatureVector.FEATURE_NAMES. */
    private static final Set<String> KNOWN_FEATURES = new HashSet<>();
    static {
        for (String name : FeatureVector.FEATURE_NAMES) {
            KNOWN_FEATURES.add(name);
        }
    }

    // Prevent instantiation
    private ConfigValidator() {}

    // ═══════════════════════════════════════════════════════════════════
    // VALIDATION RESULT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Result of a config validation run.
     */
    public static class ValidationResult {
        private final List<String> errors;
        private final List<String> warnings;

        ValidationResult(List<String> errors, List<String> warnings) {
            this.errors = errors;
            this.warnings = warnings;
        }

        /** True if no errors were found (warnings are OK). */
        public boolean isValid() { return errors.isEmpty(); }

        /** Critical errors that would prevent safe operation. */
        @NonNull
        public List<String> getErrors() { return errors; }

        /** Non-critical issues (e.g. sub-optimal weight sums). */
        @NonNull
        public List<String> getWarnings() { return warnings; }

        /** Total issue count (errors + warnings). */
        public int issueCount() { return errors.size() + warnings.size(); }

        @NonNull
        @Override
        public String toString() {
            return "ValidationResult{valid=" + isValid() +
                    ", errors=" + errors.size() +
                    ", warnings=" + warnings.size() + "}";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAIN VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Validate a JSON string as a classifier weights config.
     *
     * @param json the raw JSON string to validate
     * @return validation result with errors and warnings
     */
    @NonNull
    public static ValidationResult validate(@NonNull String json) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Parse JSON
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            errors.add("Invalid JSON: " + e.getMessage());
            return new ValidationResult(errors, warnings);
        }

        // 2. Version check
        if (!root.has("version")) {
            warnings.add("Missing 'version' field — defaulting to 1");
        } else {
            int version = root.optInt("version", 0);
            if (version < 1) {
                errors.add("Invalid version: " + version + " (must be >= 1)");
            }
        }

        // 3. Validate each sub-classifier category
        for (String category : REQUIRED_CATEGORIES) {
            validateCategory(root, category, errors, warnings);
        }

        // 4. Validate overall risk weights
        validateOverallRisk(root, errors, warnings);

        // 5. Validate crisis detection thresholds
        validateCrisisDetection(root, errors, warnings);

        // 6. Validate protective dampening
        validateProtectiveDampening(root, errors, warnings);

        // 7. Validate cross-signal amplification
        validateCrossSignal(root, errors, warnings);

        ValidationResult result = new ValidationResult(errors, warnings);
        Log.d(TAG, "Validation complete: " + result);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORY VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    private static void validateCategory(JSONObject root, String category,
                                          List<String> errors, List<String> warnings) {
        if (!root.has(category)) {
            errors.add("Missing required category: '" + category + "'");
            return;
        }

        try {
            JSONObject catObj = root.getJSONObject(category);

            // Weights
            if (!catObj.has("weights")) {
                errors.add("[" + category + "] Missing 'weights' map");
            } else {
                JSONObject weights = catObj.getJSONObject("weights");
                validateWeightsMap(category, weights, errors, warnings);
            }

            // Interactions (optional but validated if present)
            if (catObj.has("interactions")) {
                JSONArray interactions = catObj.getJSONArray("interactions");
                validateInteractions(category, interactions, errors, warnings);
            }
        } catch (Exception e) {
            errors.add("[" + category + "] Parse error: " + e.getMessage());
        }
    }

    private static void validateWeightsMap(String category, JSONObject weights,
                                            List<String> errors, List<String> warnings) {
        float sum = 0f;
        int count = 0;
        Iterator<String> keys = weights.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            count++;

            // Validate feature key resolves
            if (!isValidFeatureKey(key)) {
                errors.add("[" + category + "] Unknown feature key: '" + key + "'");
            }

            // Validate weight value
            try {
                float value = (float) weights.getDouble(key);
                if (value < 0f || value > 1f) {
                    errors.add("[" + category + "] Weight out of range [0,1]: " +
                            key + " = " + value);
                }
                sum += value;
            } catch (Exception e) {
                errors.add("[" + category + "] Invalid weight value for '" +
                        key + "': " + e.getMessage());
            }
        }

        // Weight count check
        if (count < 5) {
            warnings.add("[" + category + "] Low feature count (" + count +
                    ") — classifier may underperform");
        }

        // Weight sum check
        if (Math.abs(sum - 1.0f) > WEIGHT_SUM_TOLERANCE) {
            if (Math.abs(sum - 1.0f) > 0.15f) {
                errors.add("[" + category + "] Weight sum critically off: " +
                        String.format("%.3f", sum) + " (expected ~1.0)");
            } else {
                warnings.add("[" + category + "] Weight sum slightly off: " +
                        String.format("%.3f", sum) + " (expected ~1.0)");
            }
        }
    }

    private static void validateInteractions(String category, JSONArray interactions,
                                              List<String> errors, List<String> warnings) {
        for (int i = 0; i < interactions.length(); i++) {
            try {
                JSONObject inter = interactions.getJSONObject(i);

                // Name
                if (!inter.has("name")) {
                    warnings.add("[" + category + "] Interaction " + i +
                            " missing 'name'");
                }

                // Boost
                if (inter.has("boost")) {
                    float boost = (float) inter.getDouble("boost");
                    if (boost < 0f || boost > 0.20f) {
                        warnings.add("[" + category + "] Interaction '" +
                                inter.optString("name", "#" + i) +
                                "' boost seems extreme: " + boost);
                    }
                } else {
                    warnings.add("[" + category + "] Interaction " + i +
                            " missing 'boost'");
                }

                // Conditions
                if (inter.has("condition")) {
                    JSONObject cond = inter.getJSONObject("condition");
                    Iterator<String> condKeys = cond.keys();
                    while (condKeys.hasNext()) {
                        String ck = condKeys.next();
                        if (!isValidFeatureKey(ck)) {
                            errors.add("[" + category + "] Interaction '" +
                                    inter.optString("name", "#" + i) +
                                    "' references unknown feature: '" + ck + "'");
                        }
                        float threshold = (float) cond.getDouble(ck);
                        if (threshold < 0f || threshold > 1f) {
                            errors.add("[" + category + "] Interaction condition threshold " +
                                    "out of range: " + ck + " = " + threshold);
                        }
                    }
                }
            } catch (Exception e) {
                errors.add("[" + category + "] Interaction " + i +
                        " parse error: " + e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OVERALL RISK VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    private static void validateOverallRisk(JSONObject root,
                                             List<String> errors, List<String> warnings) {
        if (!root.has("overall_risk")) {
            warnings.add("Missing 'overall_risk' section — will use hardcoded defaults");
            return;
        }

        try {
            JSONObject overall = root.getJSONObject("overall_risk");
            if (!overall.has("category_weights")) {
                warnings.add("Missing 'overall_risk.category_weights'");
                return;
            }

            JSONObject cw = overall.getJSONObject("category_weights");
            float sum = 0f;

            // Check all required categories are present
            for (String cat : REQUIRED_CATEGORIES) {
                if (!cw.has(cat)) {
                    errors.add("[overall_risk] Missing category weight: '" + cat + "'");
                } else {
                    float w = (float) cw.getDouble(cat);
                    if (w < 0f || w > 1f) {
                        errors.add("[overall_risk] Weight out of range: " +
                                cat + " = " + w);
                    }
                    sum += w;
                }
            }

            // Sum check
            if (Math.abs(sum - 1.0f) > WEIGHT_SUM_TOLERANCE) {
                errors.add("[overall_risk] Category weights sum to " +
                        String.format("%.3f", sum) + " (expected ~1.0)");
            }
        } catch (Exception e) {
            errors.add("[overall_risk] Parse error: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS DETECTION VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    private static void validateCrisisDetection(JSONObject root,
                                                 List<String> errors, List<String> warnings) {
        if (!root.has("crisis_detection")) {
            warnings.add("Missing 'crisis_detection' — will use hardcoded defaults");
            return;
        }

        try {
            JSONObject cd = root.getJSONObject("crisis_detection");

            validateThreshold(cd, "depression_threshold", 0.5f, 1.0f, errors, warnings);
            validateThreshold(cd, "stress_anxiety_threshold", 0.5f, 1.0f, errors, warnings);
            validateThreshold(cd, "multi_category_overall_threshold", 0.3f, 1.0f, errors, warnings);
            validateThreshold(cd, "combined_depression_threshold", 0.3f, 1.0f, errors, warnings);
            validateThreshold(cd, "combined_sad_days_threshold", 0.3f, 1.0f, errors, warnings);
            validateThreshold(cd, "combined_isolation_threshold", 0.3f, 1.0f, errors, warnings);

            if (cd.has("multi_category_elevated_count")) {
                int count = cd.getInt("multi_category_elevated_count");
                if (count < 1 || count > 6) {
                    errors.add("[crisis_detection] multi_category_elevated_count must be 1-6, got: " + count);
                }
            }
        } catch (Exception e) {
            errors.add("[crisis_detection] Parse error: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROTECTIVE DAMPENING VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    private static void validateProtectiveDampening(JSONObject root,
                                                     List<String> errors, List<String> warnings) {
        if (!root.has("protective_dampening")) {
            warnings.add("Missing 'protective_dampening' — will use hardcoded defaults");
            return;
        }

        try {
            JSONObject pd = root.getJSONObject("protective_dampening");

            // Weight sum of exercise + social_support + routine_stability should be ~1.0
            float ew = (float) pd.optDouble("exercise_weight", 0.4);
            float ssw = (float) pd.optDouble("social_support_weight", 0.3);
            float rsw = (float) pd.optDouble("routine_stability_weight", 0.3);
            float factorSum = ew + ssw + rsw;

            if (Math.abs(factorSum - 1.0f) > WEIGHT_SUM_TOLERANCE) {
                warnings.add("[protective_dampening] Factor weights sum to " +
                        String.format("%.3f", factorSum) + " (expected ~1.0)");
            }

            // Max dampening should be reasonable
            float mdf = (float) pd.optDouble("max_dampening_factor", 0.15);
            if (mdf > 0.30f) {
                warnings.add("[protective_dampening] max_dampening_factor=" +
                        mdf + " seems high — risk scores may be over-dampened");
            }
            if (mdf < 0f) {
                errors.add("[protective_dampening] max_dampening_factor cannot be negative");
            }
        } catch (Exception e) {
            errors.add("[protective_dampening] Parse error: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CROSS-SIGNAL VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    private static void validateCrossSignal(JSONObject root,
                                             List<String> errors, List<String> warnings) {
        if (!root.has("cross_signal_amplification")) {
            warnings.add("Missing 'cross_signal_amplification' — will use hardcoded defaults");
            return;
        }

        try {
            JSONObject cs = root.getJSONObject("cross_signal_amplification");

            if (cs.has("threshold_4_elevated")) {
                float boost = (float) cs.getJSONObject("threshold_4_elevated")
                        .optDouble("boost", 0.08);
                if (boost > 0.20f) {
                    warnings.add("[cross_signal] 4-elevated boost=" + boost +
                            " seems extreme");
                }
            }

            if (cs.has("threshold_3_elevated")) {
                float boost = (float) cs.getJSONObject("threshold_3_elevated")
                        .optDouble("boost", 0.04);
                if (boost > 0.15f) {
                    warnings.add("[cross_signal] 3-elevated boost=" + boost +
                            " seems extreme");
                }
            }
        } catch (Exception e) {
            errors.add("[cross_signal_amplification] Parse error: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if a JSON feature key resolves to a known FeatureVector feature.
     * Keys can be in format "D1_screenTimeHours" or just "screenTimeHours".
     */
    private static boolean isValidFeatureKey(@NonNull String key) {
        // Strip prefix (e.g. "D1_", "P2_", "C3_", "T4_")
        int underscore = key.indexOf('_');
        String featureName = underscore >= 0 ? key.substring(underscore + 1) : key;
        return KNOWN_FEATURES.contains(featureName);
    }

    /**
     * Validate a threshold value is within expected range.
     */
    private static void validateThreshold(JSONObject obj, String key,
                                           float min, float max,
                                           List<String> errors, List<String> warnings) {
        if (!obj.has(key)) {
            warnings.add("[crisis_detection] Missing '" + key + "' — using default");
            return;
        }
        try {
            float value = (float) obj.getDouble(key);
            if (value < min || value > max) {
                errors.add("[crisis_detection] " + key + " = " + value +
                        " is out of safe range [" + min + ", " + max + "]");
            }
        } catch (Exception e) {
            errors.add("[crisis_detection] Invalid value for '" + key + "'");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUICK VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Quick check — returns true if the JSON is valid, false otherwise.
     * Use {@link #validate(String)} for detailed error reporting.
     */
    public static boolean isValid(@NonNull String json) {
        return validate(json).isValid();
    }

    /**
     * Validate and log all issues at appropriate log levels.
     *
     * @return true if valid (no errors)
     */
    public static boolean validateAndLog(@NonNull String json) {
        ValidationResult result = validate(json);

        for (String error : result.getErrors()) {
            Log.e(TAG, "ERROR: " + error);
        }
        for (String warning : result.getWarnings()) {
            Log.w(TAG, "WARNING: " + warning);
        }

        if (result.isValid()) {
            Log.i(TAG, "Config validation PASSED (" +
                    result.getWarnings().size() + " warnings)");
        } else {
            Log.e(TAG, "Config validation FAILED (" +
                    result.getErrors().size() + " errors, " +
                    result.getWarnings().size() + " warnings)");
        }

        return result.isValid();
    }
}
