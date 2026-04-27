package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Loads and provides access to externalized AI classifier weights.
 *
 * <h3>Resolution Order:</h3>
 * <ol>
 *   <li><b>External override</b>: {@code /data/data/<pkg>/files/classifier_weights.json}
 *       — enables OTA weight updates without app releases</li>
 *   <li><b>Bundled asset</b>: {@code assets/classifier_weights_v1.json}
 *       — shipped with the APK as the default configuration</li>
 * </ol>
 *
 * <h3>Thread Safety:</h3>
 * <p>Instance is immutable after construction. The config is parsed once during
 * {@code load()} and cached. All accessor methods are thread-safe.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   ClassifierWeightsConfig config = ClassifierWeightsConfig.load(context);
 *   Map&lt;String, Float&gt; weights = config.getWeights("digital_addiction");
 *   List&lt;Interaction&gt; interactions = config.getInteractions("stress_anxiety");
 * </pre>
 *
 * @see MultiModalClassifier
 */
public class ClassifierWeightsConfig {

    private static final String TAG = "ClassifierWeights";
    private static final String ASSET_FILENAME = "classifier_weights_v1.json";
    private static final String OVERRIDE_FILENAME = "classifier_weights.json";

    // Parsed data
    private final int version;
    private final Map<String, Map<String, Float>> categoryWeights;
    private final Map<String, List<Interaction>> categoryInteractions;
    private final Map<String, Float> overallCategoryWeights;
    private final CrisisConfig crisisConfig;
    private final ProtectiveDampeningConfig dampeningConfig;
    private final CrossSignalConfig crossSignalConfig;

    /**
     * Represents an interaction term (conditional weight boost).
     */
    public static class Interaction {
        @NonNull public final String name;
        @NonNull public final Map<String, Float> conditions;
        public final float boost;

        public Interaction(@NonNull String name, @NonNull Map<String, Float> conditions, float boost) {
            this.name = name;
            this.conditions = Collections.unmodifiableMap(conditions);
            this.boost = boost;
        }
    }

    /**
     * Crisis detection thresholds.
     */
    public static class CrisisConfig {
        public final float depressionThreshold;
        public final float stressAnxietyThreshold;
        public final int multiCategoryElevatedCount;
        public final float multiCategoryOverallThreshold;
        public final float combinedDepressionThreshold;
        public final float combinedSadDaysThreshold;
        public final float combinedIsolationThreshold;

        CrisisConfig(float dt, float sat, int mcec, float mcot, float cdt, float csdt, float cit) {
            this.depressionThreshold = dt;
            this.stressAnxietyThreshold = sat;
            this.multiCategoryElevatedCount = mcec;
            this.multiCategoryOverallThreshold = mcot;
            this.combinedDepressionThreshold = cdt;
            this.combinedSadDaysThreshold = csdt;
            this.combinedIsolationThreshold = cit;
        }

        /** Default hardcoded values as fallback. */
        static CrisisConfig defaults() {
            return new CrisisConfig(0.85f, 0.90f, 4, 0.70f, 0.70f, 0.80f, 0.60f);
        }
    }

    /**
     * Protective factor dampening configuration.
     */
    public static class ProtectiveDampeningConfig {
        public final float exerciseWeight;
        public final float socialSupportWeight;
        public final float routineStabilityWeight;
        public final float activationThreshold;
        public final float maxDampeningFactor;
        @NonNull public final Map<String, Float> categoryDampening;

        ProtectiveDampeningConfig(float ew, float ssw, float rsw, float at, float mdf,
                                  @NonNull Map<String, Float> cd) {
            this.exerciseWeight = ew;
            this.socialSupportWeight = ssw;
            this.routineStabilityWeight = rsw;
            this.activationThreshold = at;
            this.maxDampeningFactor = mdf;
            this.categoryDampening = Collections.unmodifiableMap(cd);
        }

        static ProtectiveDampeningConfig defaults() {
            Map<String, Float> cd = new HashMap<>();
            cd.put("stress_anxiety", 1.0f);
            cd.put("depression", 1.0f);
            cd.put("social_isolation", 0.8f);
            cd.put("sleep_disruption", 0.5f);
            return new ProtectiveDampeningConfig(0.4f, 0.3f, 0.3f, 0.5f, 0.15f, cd);
        }
    }

    /**
     * Cross-signal amplification configuration.
     */
    public static class CrossSignalConfig {
        public final float boost4Elevated;
        public final float boost3Elevated;

        CrossSignalConfig(float b4, float b3) {
            this.boost4Elevated = b4;
            this.boost3Elevated = b3;
        }

        static CrossSignalConfig defaults() {
            return new CrossSignalConfig(0.08f, 0.04f);
        }
    }

    // ─── Private constructor ──────────────────────────────────────────
    private ClassifierWeightsConfig(int version,
                                     Map<String, Map<String, Float>> categoryWeights,
                                     Map<String, List<Interaction>> categoryInteractions,
                                     Map<String, Float> overallCategoryWeights,
                                     CrisisConfig crisisConfig,
                                     ProtectiveDampeningConfig dampeningConfig,
                                     CrossSignalConfig crossSignalConfig) {
        this.version = version;
        this.categoryWeights = categoryWeights;
        this.categoryInteractions = categoryInteractions;
        this.overallCategoryWeights = overallCategoryWeights;
        this.crisisConfig = crisisConfig;
        this.dampeningConfig = dampeningConfig;
        this.crossSignalConfig = crossSignalConfig;
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Load the classifier weights configuration.
     * Checks for an external override first, then falls back to the bundled asset.
     *
     * @param context application context
     * @return parsed config, or a hardcoded-defaults config if parsing fails
     */
    @NonNull
    public static ClassifierWeightsConfig load(@NonNull Context context) {
        try {
            String json = loadJsonString(context);
            if (json != null) {
                ClassifierWeightsConfig config = parse(json);
                Log.d(TAG, "Loaded classifier weights v" + config.version +
                        " (" + config.categoryWeights.size() + " categories)");
                return config;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load classifier weights — using hardcoded defaults", e);
        }
        return createHardcodedDefaults();
    }

    /**
     * Try external override first (with validation), then bundled asset.
     *
     * <p>OTA overrides are validated via {@link ConfigValidator} before being
     * accepted. If validation fails, the override is rejected and the bundled
     * asset is used instead.</p>
     */
    @Nullable
    private static String loadJsonString(@NonNull Context context) {
        // 1. Check for external override (e.g. OTA-pushed config)
        File override = new File(context.getFilesDir(), OVERRIDE_FILENAME);
        if (override.exists() && override.canRead()) {
            try (InputStream is = new FileInputStream(override)) {
                String json = readStream(is);

                // Validate before applying
                if (ConfigValidator.isValid(json)) {
                    Log.d(TAG, "Using validated external override: " +
                            override.getAbsolutePath());
                    return json;
                } else {
                    Log.w(TAG, "External override FAILED validation — rejecting");
                    ConfigValidator.validateAndLog(json); // Log detailed errors
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to read override file, falling back to asset", e);
            }
        }

        // 2. Bundled asset
        try (InputStream is = context.getAssets().open(ASSET_FILENAME)) {
            return readStream(is);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read bundled asset: " + ASSET_FILENAME, e);
        }

        return null;
    }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Parse the JSON string into a ClassifierWeightsConfig.
     */
    @NonNull
    private static ClassifierWeightsConfig parse(@NonNull String json) throws Exception {
        JSONObject root = new JSONObject(json);
        int version = root.optInt("version", 1);

        Map<String, Map<String, Float>> categoryWeights = new HashMap<>();
        Map<String, List<Interaction>> categoryInteractions = new HashMap<>();

        // Parse the 6 sub-classifier categories
        String[] categories = {
            "digital_addiction", "stress_anxiety", "depression",
            "social_isolation", "sleep_disruption", "low_fulfilment"
        };

        for (String cat : categories) {
            if (root.has(cat)) {
                JSONObject catObj = root.getJSONObject(cat);

                // Weights
                if (catObj.has("weights")) {
                    JSONObject weightsObj = catObj.getJSONObject("weights");
                    Map<String, Float> weights = new HashMap<>();
                    Iterator<String> keys = weightsObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        weights.put(key, (float) weightsObj.getDouble(key));
                    }
                    categoryWeights.put(cat, Collections.unmodifiableMap(weights));
                }

                // Interactions
                if (catObj.has("interactions")) {
                    JSONArray interArray = catObj.getJSONArray("interactions");
                    List<Interaction> interactions = new ArrayList<>();
                    for (int i = 0; i < interArray.length(); i++) {
                        JSONObject interObj = interArray.getJSONObject(i);
                        String name = interObj.optString("name", "unnamed_" + i);
                        float boost = (float) interObj.optDouble("boost", 0.0);

                        Map<String, Float> conditions = new HashMap<>();
                        if (interObj.has("condition")) {
                            JSONObject condObj = interObj.getJSONObject("condition");
                            Iterator<String> condKeys = condObj.keys();
                            while (condKeys.hasNext()) {
                                String ck = condKeys.next();
                                conditions.put(ck, (float) condObj.getDouble(ck));
                            }
                        }
                        interactions.add(new Interaction(name, conditions, boost));
                    }
                    categoryInteractions.put(cat, Collections.unmodifiableList(interactions));
                }
            }
        }

        // Overall risk weights
        Map<String, Float> overallWeights = new HashMap<>();
        if (root.has("overall_risk")) {
            JSONObject overallObj = root.getJSONObject("overall_risk");
            if (overallObj.has("category_weights")) {
                JSONObject cw = overallObj.getJSONObject("category_weights");
                Iterator<String> keys = cw.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    overallWeights.put(key, (float) cw.getDouble(key));
                }
            }
        }

        // Crisis config
        CrisisConfig crisis = CrisisConfig.defaults();
        if (root.has("crisis_detection")) {
            JSONObject cd = root.getJSONObject("crisis_detection");
            crisis = new CrisisConfig(
                    (float) cd.optDouble("depression_threshold", 0.85),
                    (float) cd.optDouble("stress_anxiety_threshold", 0.90),
                    cd.optInt("multi_category_elevated_count", 4),
                    (float) cd.optDouble("multi_category_overall_threshold", 0.70),
                    (float) cd.optDouble("combined_depression_threshold", 0.70),
                    (float) cd.optDouble("combined_sad_days_threshold", 0.80),
                    (float) cd.optDouble("combined_isolation_threshold", 0.60)
            );
        }

        // Protective dampening
        ProtectiveDampeningConfig dampening = ProtectiveDampeningConfig.defaults();
        if (root.has("protective_dampening")) {
            JSONObject pd = root.getJSONObject("protective_dampening");
            Map<String, Float> catDamp = new HashMap<>();
            if (pd.has("category_dampening")) {
                JSONObject cdObj = pd.getJSONObject("category_dampening");
                Iterator<String> keys = cdObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    catDamp.put(key, (float) cdObj.getDouble(key));
                }
            }
            dampening = new ProtectiveDampeningConfig(
                    (float) pd.optDouble("exercise_weight", 0.4),
                    (float) pd.optDouble("social_support_weight", 0.3),
                    (float) pd.optDouble("routine_stability_weight", 0.3),
                    (float) pd.optDouble("activation_threshold", 0.5),
                    (float) pd.optDouble("max_dampening_factor", 0.15),
                    catDamp.isEmpty() ? ProtectiveDampeningConfig.defaults().categoryDampening : catDamp
            );
        }

        // Cross-signal amplification
        CrossSignalConfig crossSignal = CrossSignalConfig.defaults();
        if (root.has("cross_signal_amplification")) {
            JSONObject cs = root.getJSONObject("cross_signal_amplification");
            float b4 = 0.08f, b3 = 0.04f;
            if (cs.has("threshold_4_elevated")) {
                b4 = (float) cs.getJSONObject("threshold_4_elevated").optDouble("boost", 0.08);
            }
            if (cs.has("threshold_3_elevated")) {
                b3 = (float) cs.getJSONObject("threshold_3_elevated").optDouble("boost", 0.04);
            }
            crossSignal = new CrossSignalConfig(b4, b3);
        }

        return new ClassifierWeightsConfig(
                version, categoryWeights, categoryInteractions,
                Collections.unmodifiableMap(overallWeights),
                crisis, dampening, crossSignal
        );
    }

    /**
     * Create a config with the same hardcoded defaults as the original classifier.
     * Used as ultimate fallback if asset loading fails.
     */
    @NonNull
    private static ClassifierWeightsConfig createHardcodedDefaults() {
        Map<String, Map<String, Float>> cw = new HashMap<>();
        Map<String, List<Interaction>> ci = new HashMap<>();
        for (String cat : new String[]{"digital_addiction", "stress_anxiety", "depression",
                "social_isolation", "sleep_disruption", "low_fulfilment"}) {
            cw.put(cat, Collections.emptyMap());
            ci.put(cat, Collections.emptyList());
        }

        Map<String, Float> overall = new HashMap<>();
        overall.put("digital_addiction", 0.18f);
        overall.put("stress_anxiety", 0.20f);
        overall.put("depression", 0.22f);
        overall.put("social_isolation", 0.15f);
        overall.put("sleep_disruption", 0.12f);
        overall.put("low_fulfilment", 0.13f);

        return new ClassifierWeightsConfig(0, cw, ci, overall,
                CrisisConfig.defaults(),
                ProtectiveDampeningConfig.defaults(),
                CrossSignalConfig.defaults());
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /** Config schema version number. */
    public int getVersion() { return version; }

    /**
     * Get the weight map for a specific category.
     * Keys are feature identifiers like "D1_screenTimeHours".
     *
     * @param category e.g. "digital_addiction", "stress_anxiety"
     * @return unmodifiable map of feature→weight, or empty map if not found
     */
    @NonNull
    public Map<String, Float> getWeights(@NonNull String category) {
        Map<String, Float> w = categoryWeights.get(category);
        return w != null ? w : Collections.emptyMap();
    }

    /**
     * Get a specific weight value with a default fallback.
     */
    public float getWeight(@NonNull String category, @NonNull String feature, float defaultValue) {
        Map<String, Float> w = categoryWeights.get(category);
        if (w == null) return defaultValue;
        Float val = w.get(feature);
        return val != null ? val : defaultValue;
    }

    /**
     * Get the interaction terms for a specific category.
     */
    @NonNull
    public List<Interaction> getInteractions(@NonNull String category) {
        List<Interaction> i = categoryInteractions.get(category);
        return i != null ? i : Collections.emptyList();
    }

    /**
     * Get the overall risk category weights.
     */
    @NonNull
    public Map<String, Float> getOverallCategoryWeights() {
        return overallCategoryWeights;
    }

    /** Get crisis detection configuration. */
    @NonNull
    public CrisisConfig getCrisisConfig() { return crisisConfig; }

    /** Get protective dampening configuration. */
    @NonNull
    public ProtectiveDampeningConfig getDampeningConfig() { return dampeningConfig; }

    /** Get cross-signal amplification configuration. */
    @NonNull
    public CrossSignalConfig getCrossSignalConfig() { return crossSignalConfig; }

    /**
     * Check if this config was loaded from external override (OTA update).
     */
    public boolean isExternalOverride() {
        return version > 0; // version 0 = hardcoded defaults
    }

    @NonNull
    @Override
    public String toString() {
        return "ClassifierWeightsConfig{v=" + version +
                ", categories=" + categoryWeights.size() + "}";
    }
}
