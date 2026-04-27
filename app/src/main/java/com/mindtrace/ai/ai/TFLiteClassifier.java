package com.mindtrace.ai.ai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;

/**
 * Layer 2 ML-Enhanced Classifier — TensorFlow Lite on-device inference.
 *
 * <p>Loads 6 pre-trained TFLite models from {@code assets/ml/} and runs
 * inference on the 36-dimensional {@link FeatureVector} to produce risk
 * severity scores. Falls back gracefully to rule-based classification
 * when models are unavailable.</p>
 *
 * <h3>Architecture (per model):</h3>
 * <pre>
 *   Input (36 floats) → Dense(64, ReLU) → Dropout(0.3)
 *                      → Dense(32, ReLU) → Dropout(0.2)
 *                      → Dense(16, ReLU)
 *                      → Output(1, Sigmoid) → float [0.0–1.0]
 * </pre>
 *
 * <h3>Hybrid Inference Strategy:</h3>
 * <pre>
 *   if (ML model available) {
 *       score = ruleScore × 0.40 + mlScore × 0.60
 *   } else {
 *       score = ruleScore (rule-based fallback)
 *   }
 * </pre>
 *
 * <h3>Model Files (≈50KB each, ≈300KB total):</h3>
 * <pre>
 *   assets/ml/digital_addiction_model.tflite
 *   assets/ml/stress_anxiety_model.tflite
 *   assets/ml/low_fulfilment_model.tflite
 *   assets/ml/emotional_fatigue_model.tflite
 *   assets/ml/social_isolation_model.tflite
 *   assets/ml/depressive_indicators_model.tflite
 * </pre>
 *
 * <h3>Design Decisions:</h3>
 * <ul>
 *   <li><b>Graceful degradation:</b> If any model fails to load, rule-based
 *       classification handles that category — zero interruption.</li>
 *   <li><b>Thread-safe:</b> Interpreters are per-instance; create one per
 *       classification pipeline execution.</li>
 *   <li><b>Quantized:</b> Models use dynamic-range quantization for sub-10ms
 *       inference on mobile CPUs.</li>
 *   <li><b>Versioned:</b> Model version is read from assets metadata to
 *       support OTA model updates.</li>
 * </ul>
 *
 * @see MultiModalClassifier
 * @see FeatureVector
 * @see <a href="https://www.tensorflow.org/lite">TensorFlow Lite</a>
 */
public class TFLiteClassifier {

    private static final String TAG = "TFLiteClassifier";

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORY INDICES — match MultiModalClassifier sub-classifier order
    // ═══════════════════════════════════════════════════════════════════

    /** Digital Addiction: compulsive use, escape behaviour, dependency. */
    public static final int CAT_DIGITAL_ADDICTION = 0;
    /** Stress & Anxiety: acute/chronic stress, anxiety signals. */
    public static final int CAT_STRESS_ANXIETY = 1;
    /** Low Fulfilment: purposelessness, stagnation, low motivation. */
    public static final int CAT_LOW_FULFILMENT = 2;
    /** Emotional Fatigue / Sleep Disruption. */
    public static final int CAT_EMOTIONAL_FATIGUE = 3;
    /** Social Isolation: loneliness, withdrawal, disconnection. */
    public static final int CAT_SOCIAL_ISOLATION = 4;
    /** Depressive Indicators: sadness, anhedonia, hopelessness. */
    public static final int CAT_DEPRESSIVE_INDICATORS = 5;

    /** Total number of category models. */
    public static final int CATEGORY_COUNT = 6;

    // ═══════════════════════════════════════════════════════════════════
    // MODEL FILE PATHS (relative to assets/)
    // ═══════════════════════════════════════════════════════════════════

    private static final String MODEL_DIR = "ml/";
    private static final String[] MODEL_FILES = {
        "digital_addiction_model.tflite",
        "stress_anxiety_model.tflite",
        "low_fulfilment_model.tflite",
        "emotional_fatigue_model.tflite",
        "social_isolation_model.tflite",
        "depressive_indicators_model.tflite"
    };

    /** Human-readable category names (matches MODEL_FILES order). */
    public static final String[] CATEGORY_NAMES = {
        "digital_addiction",
        "stress_anxiety",
        "low_fulfilment",
        "emotional_fatigue",
        "social_isolation",
        "depressive_indicators"
    };

    // ═══════════════════════════════════════════════════════════════════
    // HYBRID BLENDING WEIGHTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Weight for rule-based score in hybrid mode.
     * Rules provide the safety floor — always active.
     */
    private static final float RULE_WEIGHT = 0.40f;

    /**
     * Weight for ML model score in hybrid mode.
     * ML improves accuracy once models are trained.
     */
    private static final float ML_WEIGHT = 0.60f;

    /**
     * Minimum confidence threshold for ML prediction to be blended.
     * If the model's output is very close to 0.5 (uncertain), we
     * lean more toward rules.
     */
    private static final float ML_CONFIDENCE_THRESHOLD = 0.15f;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * TFLite interpreter instances — one per category.
     * null means the model is not available (use rule-based fallback).
     *
     * <p>Note: We use Object[] to avoid a hard compile-time dependency on
     * the TFLite library. At runtime, if the library is present, these
     * are cast to {@code org.tensorflow.lite.Interpreter}. If the library
     * is missing, all entries remain null and we gracefully fall back.</p>
     */
    private final Object[] interpreters = new Object[CATEGORY_COUNT];

    /** Tracks which models loaded successfully. */
    private final boolean[] modelAvailable = new boolean[CATEGORY_COUNT];

    /** Number of successfully loaded models. */
    private int loadedModelCount = 0;

    /** Model version string (read from assets metadata). */
    @Nullable
    private String modelVersion;

    /** Whether GPU delegate is being used (faster but less compatible). */
    private boolean usingGpuDelegate = false;

    /** Pre-allocated input buffer (reused across inferences). */
    private final ByteBuffer inputBuffer;

    /** Pre-allocated output buffer (reused across inferences). */
    private final float[][] outputBuffer = new float[1][1];

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Create a TFLite classifier and attempt to load all 6 models.
     * Models that fail to load are silently skipped — the system
     * degrades gracefully to rule-based for those categories.
     *
     * @param context Android context for accessing assets
     */
    public TFLiteClassifier(@NonNull Context context) {
        // Pre-allocate input buffer: 1 × 36 floats × 4 bytes
        inputBuffer = ByteBuffer.allocateDirect(FeatureVector.TOTAL_FEATURES * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        loadModels(context);
    }

    /**
     * Testing constructor — creates a classifier with no models loaded.
     * All classifications will return -1 (fall back to rules).
     */
    TFLiteClassifier() {
        inputBuffer = ByteBuffer.allocateDirect(FeatureVector.TOTAL_FEATURES * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        // All models remain null → all fallback to rules
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODEL LOADING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Load all 6 TFLite models from the assets directory.
     * Uses reflection to avoid hard dependency on TFLite library.
     */
    private void loadModels(@NonNull Context context) {
        long startTime = System.currentTimeMillis();
        loadedModelCount = 0;

        for (int i = 0; i < CATEGORY_COUNT; i++) {
            try {
                MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_DIR + MODEL_FILES[i]);
                if (modelBuffer != null) {
                    // Use reflection to create Interpreter
                    Object interpreter = createInterpreter(modelBuffer);
                    if (interpreter != null) {
                        interpreters[i] = interpreter;
                        modelAvailable[i] = true;
                        loadedModelCount++;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load model " + MODEL_FILES[i] + ": " + e.getMessage());
                interpreters[i] = null;
                modelAvailable[i] = false;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        Log.i(TAG, String.format(Locale.US,
                "Model loading complete: %d/%d models loaded in %dms",
                loadedModelCount, CATEGORY_COUNT, elapsed));

        // Try to read model version
        modelVersion = readModelVersion(context);
        if (modelVersion != null) {
            Log.i(TAG, "Model version: " + modelVersion);
        }
    }

    /**
     * Load a single model file from assets into a memory-mapped buffer.
     * Returns null if the file doesn't exist.
     */
    @Nullable
    private MappedByteBuffer loadModelFile(@NonNull Context context, @NonNull String assetPath) {
        try {
            AssetFileDescriptor afd = context.getAssets().openFd(assetPath);
            FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
            FileChannel channel = fis.getChannel();
            long startOffset = afd.getStartOffset();
            long declaredLength = afd.getDeclaredLength();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY,
                    startOffset, declaredLength);
            fis.close();
            afd.close();
            return buffer;
        } catch (IOException e) {
            // Model file not present — this is expected during development
            Log.d(TAG, "Model not found: " + assetPath + " (will use rule-based fallback)");
            return null;
        }
    }

    /**
     * Create a TFLite Interpreter via reflection.
     * This avoids a hard compile-time dependency on the TFLite library.
     * If the library is not available, returns null.
     */
    @Nullable
    private Object createInterpreter(@NonNull MappedByteBuffer modelBuffer) {
        try {
            // Try: new org.tensorflow.lite.Interpreter(modelBuffer)
            Class<?> interpreterClass = Class.forName("org.tensorflow.lite.Interpreter");
            return interpreterClass.getConstructor(ByteBuffer.class).newInstance(modelBuffer);
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "TensorFlow Lite library not found — ML models will not be used");
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Failed to create TFLite interpreter: " + e.getMessage());
            return null;
        }
    }

    /**
     * Read model version from assets/ml/version.txt.
     */
    @Nullable
    private String readModelVersion(@NonNull Context context) {
        try {
            java.io.InputStream is = context.getAssets().open(MODEL_DIR + "version.txt");
            byte[] bytes = new byte[64];
            int len = is.read(bytes);
            is.close();
            return len > 0 ? new String(bytes, 0, len).trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SINGLE-CATEGORY INFERENCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Run ML inference for a single risk category.
     *
     * @param categoryIndex one of {@link #CAT_DIGITAL_ADDICTION}..{@link #CAT_DEPRESSIVE_INDICATORS}
     * @param fv            the 36-dimensional feature vector
     * @return ML risk score [0.0, 1.0], or <b>-1.0</b> if the model is not
     *         available (caller should use rule-based fallback)
     */
    public float classifyCategory(int categoryIndex, @NonNull FeatureVector fv) {
        if (categoryIndex < 0 || categoryIndex >= CATEGORY_COUNT) return -1f;
        if (!modelAvailable[categoryIndex]) return -1f;

        try {
            long startNs = System.nanoTime();

            // Fill input buffer with feature vector
            inputBuffer.rewind();
            float[] features = fv.toArray();
            for (float f : features) {
                inputBuffer.putFloat(f);
            }

            // Run inference via reflection
            Object interpreter = interpreters[categoryIndex];
            java.lang.reflect.Method runMethod = interpreter.getClass()
                    .getMethod("run", Object.class, Object.class);
            inputBuffer.rewind();
            runMethod.invoke(interpreter, inputBuffer, outputBuffer);

            float mlScore = clamp(outputBuffer[0][0]);
            long elapsedUs = (System.nanoTime() - startNs) / 1000;

            Log.d(TAG, String.format(Locale.US,
                    "ML inference [%s]: %.3f in %dμs",
                    CATEGORY_NAMES[categoryIndex], mlScore, elapsedUs));

            return mlScore;
        } catch (Exception e) {
            Log.w(TAG, "Inference failed for " + CATEGORY_NAMES[categoryIndex] + ": " + e.getMessage());
            return -1f;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HYBRID INFERENCE — Rule + ML blending
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Classify with hybrid rule+ML blending.
     *
     * <p>When the ML model is available:
     * <pre>score = ruleScore × 0.40 + mlScore × 0.60</pre>
     * When ML is not available:
     * <pre>score = ruleScore (100% rule-based)</pre>
     *
     * <p>If the ML score is very close to 0.5 (uncertain), we dynamically
     * shift more weight toward the rule-based score.</p>
     *
     * @param categoryIndex risk category index
     * @param ruleScore     the rule-based score from {@link MultiModalClassifier}
     * @param fv            the feature vector
     * @return blended score [0.0, 1.0]
     */
    public float classifyHybrid(int categoryIndex, float ruleScore, @NonNull FeatureVector fv) {
        float mlScore = classifyCategory(categoryIndex, fv);

        if (mlScore < 0f) {
            // ML model not available — use rules only
            return ruleScore;
        }

        // Compute ML confidence: distance from 0.5 (uncertain)
        float mlConfidence = Math.abs(mlScore - 0.5f) * 2f; // 0.0 = totally uncertain, 1.0 = very confident

        if (mlConfidence < ML_CONFIDENCE_THRESHOLD) {
            // ML is very uncertain — lean heavily on rules
            return clamp(ruleScore * 0.80f + mlScore * 0.20f);
        }

        // Standard hybrid blend
        return clamp(ruleScore * RULE_WEIGHT + mlScore * ML_WEIGHT);
    }

    /**
     * Run all 6 categories in hybrid mode at once.
     * Returns an array of 6 blended scores.
     *
     * @param ruleScores array of 6 rule-based scores (same order as CATEGORY_NAMES)
     * @param fv         the feature vector
     * @return array of 6 blended scores, or the ruleScores unchanged if no ML is available
     */
    @NonNull
    public float[] classifyAllHybrid(@NonNull float[] ruleScores, @NonNull FeatureVector fv) {
        if (ruleScores.length != CATEGORY_COUNT) {
            throw new IllegalArgumentException("Expected " + CATEGORY_COUNT + " rule scores, got " + ruleScores.length);
        }
        if (loadedModelCount == 0) {
            return ruleScores; // No ML models — return rules unchanged
        }

        float[] hybrid = new float[CATEGORY_COUNT];
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            hybrid[i] = classifyHybrid(i, ruleScores[i], fv);
        }
        return hybrid;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATUS & DIAGNOSTICS
    // ═══════════════════════════════════════════════════════════════════

    /** Returns true if at least one ML model is loaded and ready. */
    public boolean isMLAvailable() {
        return loadedModelCount > 0;
    }

    /** Returns true if ALL 6 ML models are loaded. */
    public boolean isFullMLAvailable() {
        return loadedModelCount == CATEGORY_COUNT;
    }

    /** Returns the number of successfully loaded models. */
    public int getLoadedModelCount() {
        return loadedModelCount;
    }

    /** Check if a specific category's ML model is available. */
    public boolean isModelAvailable(int categoryIndex) {
        return categoryIndex >= 0 && categoryIndex < CATEGORY_COUNT && modelAvailable[categoryIndex];
    }

    /** Get the loaded model version, or null if not available. */
    @Nullable
    public String getModelVersion() {
        return modelVersion;
    }

    /**
     * Returns the current classification mode string.
     * Used to tag the {@link com.mindtrace.ai.database.entity.RiskClassification}.
     */
    @NonNull
    public String getClassificationMode() {
        if (loadedModelCount == 0) return "rules_only";
        if (loadedModelCount == CATEGORY_COUNT) return "hybrid_full";
        return "hybrid_partial_" + loadedModelCount;
    }

    /**
     * Generate a diagnostic summary for debugging and logging.
     */
    @NonNull
    public String getDiagnosticSummary() {
        StringBuilder sb = new StringBuilder("TFLiteClassifier Status:\n");
        sb.append("  Mode: ").append(getClassificationMode()).append("\n");
        sb.append("  Models: ").append(loadedModelCount).append("/").append(CATEGORY_COUNT).append("\n");
        if (modelVersion != null) {
            sb.append("  Version: ").append(modelVersion).append("\n");
        }
        sb.append("  GPU: ").append(usingGpuDelegate ? "enabled" : "disabled").append("\n");
        sb.append("  Per-category:\n");
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            sb.append("    ").append(CATEGORY_NAMES[i]).append(": ")
                    .append(modelAvailable[i] ? "✓ loaded" : "✗ fallback").append("\n");
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // BENCHMARK — Performance measurement
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Benchmark inference speed for all loaded models.
     * Runs each model N times and reports average latency.
     *
     * @param fv    test feature vector
     * @param runs  number of inference runs per model
     * @return formatted benchmark report
     */
    @NonNull
    public String benchmark(@NonNull FeatureVector fv, int runs) {
        StringBuilder sb = new StringBuilder("Inference Benchmark (" + runs + " runs each):\n");
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            if (!modelAvailable[i]) {
                sb.append("  ").append(CATEGORY_NAMES[i]).append(": N/A (no model)\n");
                continue;
            }
            long totalNs = 0;
            float lastScore = 0;
            for (int r = 0; r < runs; r++) {
                long start = System.nanoTime();
                lastScore = classifyCategory(i, fv);
                totalNs += (System.nanoTime() - start);
            }
            float avgMs = (totalNs / (float) runs) / 1_000_000f;
            sb.append(String.format(Locale.US, "  %s: %.2fms avg (score=%.3f)\n",
                    CATEGORY_NAMES[i], avgMs, lastScore));
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Release all TFLite interpreter resources.
     * Call this when the classifier is no longer needed.
     */
    public void close() {
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            if (interpreters[i] != null) {
                try {
                    java.lang.reflect.Method closeMethod = interpreters[i].getClass().getMethod("close");
                    closeMethod.invoke(interpreters[i]);
                } catch (Exception ignored) {}
                interpreters[i] = null;
                modelAvailable[i] = false;
            }
        }
        loadedModelCount = 0;
        Log.d(TAG, "All TFLite interpreters released");
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
