package com.mindtrace.ai.debug;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.mindtrace.ai.ai.FeatureVector;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Debug-only utility for injecting synthetic {@link RiskClassification} rows
 * into the Room database so the Dashboard trajectory UI can be validated
 * without waiting for real longitudinal data collection.
 *
 * <h3>Supported Scenarios:</h3>
 * <ul>
 *   <li>{@link #injectStableTrajectory()} — 7 days of flat, low risk → UI shows "Stable ✓"</li>
 *   <li>{@link #injectWorseningTrajectory()} — 7 days of escalating risk → UI shows "Worsening ▲"</li>
 *   <li>{@link #injectImprovingTrajectory()} — 7 days of declining risk → UI shows "Improving ▼"</li>
 *   <li>{@link #injectCrisisEscalation()} — 10 days ending in crisis → UI shows "Crisis ⚠"</li>
 *   <li>{@link #injectMixedRecovery()} — worsening then recovering → UI shows mixed signals</li>
 *   <li>{@link #clearAllClassifications()} — wipe table for a clean slate</li>
 * </ul>
 *
 * <p><b>Usage:</b> Call from a debug menu, ADB shell, or a hidden developer gesture.
 * After injection, trigger {@code DashboardViewModel.loadTrendReport()} to refresh the UI.</p>
 *
 * <p><b>WARNING:</b> This class should NEVER be invoked in production builds.
 * Guard all call-sites with {@code BuildConfig.DEBUG} checks.</p>
 *
 * @see com.mindtrace.ai.ai.ClassificationTrendAnalyzer
 * @see com.mindtrace.ai.viewmodel.DashboardViewModel#loadTrendReport
 */
public class TrajectorySimulator {

    private static final String TAG = "TrajectorySimulator";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final RiskClassificationDao dao;
    private final Context context;
    private final ExecutorService executor;

    public TrajectorySimulator(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(this.context).riskClassificationDao();
        this.executor = com.mindtrace.ai.util.AppExecutors.diskIO();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 1: STABLE TRAJECTORY
    // 7 days of consistent low risk (0.20 ± 0.02)
    // Expected TrendAnalyzer output: slope ≈ 0, trajectory = "stable"
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Injects 7 days of flat, low-risk data.
     * Verifies that the UI correctly renders the "Stable" badge.
     */
    public void injectStableTrajectory() {
        executor.execute(() -> {
            Log.i(TAG, "═══ Injecting STABLE trajectory (7 days) ═══");
            clearSync();

            long now = System.currentTimeMillis();
            float[] risks = {0.18f, 0.20f, 0.19f, 0.21f, 0.20f, 0.19f, 0.20f};

            for (int day = 0; day < risks.length; day++) {
                long dayTs = midnightOf(now - (long)(risks.length - 1 - day) * DAY_MS);
                float risk = risks[day];
                RiskClassification rc = buildClassification(
                        dayTs, risk,
                        risk * 0.9f,   // digital
                        risk * 0.7f,   // stress
                        risk * 0.5f,   // depression
                        risk * 0.6f,   // isolation
                        risk * 0.8f,   // sleep
                        risk * 0.4f,   // fulfilment
                        "digital_addiction",
                        "sleep_disruption",
                        false, null,
                        0.85f,
                        day == 0 ? 0f : risks[day] - risks[day - 1]
                );
                dao.insertOrReplace(rc);
            }

            int count = dao.getTotalClassificationCount();
            Log.i(TAG, "✓ Stable trajectory injected: " + count + " rows");
            showToast("✓ Stable trajectory injected (" + count + " days)");
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 2: WORSENING TRAJECTORY
    // 7 days of escalating risk (0.25 → 0.75)
    // Expected TrendAnalyzer output: slope > 0.02, trajectory = "rapidly_worsening"
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Injects 7 days of steadily escalating risk scores.
     * Verifies that the UI correctly renders the "Worsening ▲" badge.
     */
    public void injectWorseningTrajectory() {
        executor.execute(() -> {
            Log.i(TAG, "═══ Injecting WORSENING trajectory (7 days) ═══");
            clearSync();

            long now = System.currentTimeMillis();
            float[] risks = {0.25f, 0.32f, 0.40f, 0.48f, 0.56f, 0.65f, 0.75f};

            for (int day = 0; day < risks.length; day++) {
                long dayTs = midnightOf(now - (long)(risks.length - 1 - day) * DAY_MS);
                float risk = risks[day];
                RiskClassification rc = buildClassification(
                        dayTs, risk,
                        risk * 1.1f,          // digital — escalating fast
                        risk * 0.9f,          // stress
                        risk * 0.8f,          // depression
                        risk * 0.6f,          // isolation
                        risk * 1.0f,          // sleep
                        risk * 0.5f,          // fulfilment
                        "digital_addiction",
                        "stress_anxiety",
                        risk >= 0.70f,        // crisis on last days
                        risk >= 0.70f ? "Overall risk > 0.70 + sustained escalation" : null,
                        Math.max(0.5f, 0.90f - risk * 0.2f),
                        day == 0 ? 0f : risks[day] - risks[day - 1]
                );
                dao.insertOrReplace(rc);
            }

            int count = dao.getTotalClassificationCount();
            Log.i(TAG, "✓ Worsening trajectory injected: " + count + " rows");
            showToast("✓ Worsening trajectory injected (" + count + " days)");
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 3: IMPROVING TRAJECTORY
    // 7 days of declining risk (0.70 → 0.20)
    // Expected TrendAnalyzer output: slope < -0.02, trajectory = "rapidly_improving"
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Injects 7 days of steadily decreasing risk scores.
     * Verifies that the UI correctly renders the "Improving ▼" badge.
     */
    public void injectImprovingTrajectory() {
        executor.execute(() -> {
            Log.i(TAG, "═══ Injecting IMPROVING trajectory (7 days) ═══");
            clearSync();

            long now = System.currentTimeMillis();
            float[] risks = {0.70f, 0.60f, 0.50f, 0.42f, 0.34f, 0.27f, 0.20f};

            for (int day = 0; day < risks.length; day++) {
                long dayTs = midnightOf(now - (long)(risks.length - 1 - day) * DAY_MS);
                float risk = risks[day];
                RiskClassification rc = buildClassification(
                        dayTs, risk,
                        risk * 0.8f,    // digital — improving
                        risk * 0.9f,    // stress
                        risk * 0.7f,    // depression
                        risk * 0.5f,    // isolation
                        risk * 0.6f,    // sleep
                        risk * 0.4f,    // fulfilment
                        "stress_anxiety",
                        "digital_addiction",
                        false, null,
                        0.80f + (0.7f - risk) * 0.2f,  // confidence increases as user recovers
                        day == 0 ? 0f : risks[day] - risks[day - 1]
                );
                dao.insertOrReplace(rc);
            }

            int count = dao.getTotalClassificationCount();
            Log.i(TAG, "✓ Improving trajectory injected: " + count + " rows");
            showToast("✓ Improving trajectory injected (" + count + " days)");
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 4: CRISIS ESCALATION
    // 10 days starting moderate, escalating to crisis with crisis flags
    // Expected: trajectory = "rapidly_worsening", crisisCount >= 3
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Injects 10 days of escalation ending in a sustained crisis state.
     * Verifies crisis badge rendering and multi-category elevation.
     */
    public void injectCrisisEscalation() {
        executor.execute(() -> {
            Log.i(TAG, "═══ Injecting CRISIS ESCALATION trajectory (10 days) ═══");
            clearSync();

            long now = System.currentTimeMillis();
            float[] risks = {0.30f, 0.35f, 0.42f, 0.50f, 0.58f, 0.66f, 0.74f, 0.82f, 0.88f, 0.92f};

            for (int day = 0; day < risks.length; day++) {
                long dayTs = midnightOf(now - (long)(risks.length - 1 - day) * DAY_MS);
                float risk = risks[day];
                boolean isCrisis = risk >= 0.80f;
                RiskClassification rc = buildClassification(
                        dayTs, risk,
                        risk * 1.05f,                   // digital
                        risk * 1.10f,                   // stress — primary driver
                        risk * 0.95f,                   // depression — close behind
                        risk * 0.70f,                   // isolation
                        risk * 0.85f,                   // sleep
                        risk * 0.60f,                   // fulfilment
                        "stress_anxiety",
                        "depression",
                        isCrisis,
                        isCrisis ? "depression_risk > 0.85 + stress_anxiety > 0.90 + consecutive_sad_days > 5" : null,
                        Math.max(0.4f, 0.95f - risk * 0.3f),
                        day == 0 ? 0f : risks[day] - risks[day - 1]
                );
                dao.insertOrReplace(rc);
            }

            int count = dao.getTotalClassificationCount();
            int crises = dao.getCrisisCountSince(0);
            Log.i(TAG, "✓ Crisis escalation injected: " + count + " rows, " + crises + " crisis days");
            showToast("✓ Crisis trajectory injected (" + count + " days, " + crises + " crises)");
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 5: MIXED RECOVERY
    // 14 days: worsening week 1, recovery week 2
    // Expected: depends on lookback window; 7d = improving, 14d = stable/mixed
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Injects 14 days: a worsening first week followed by a recovering second week.
     * Useful for validating how the analyzer handles V-shaped recovery patterns.
     */
    public void injectMixedRecovery() {
        executor.execute(() -> {
            Log.i(TAG, "═══ Injecting MIXED RECOVERY trajectory (14 days) ═══");
            clearSync();

            long now = System.currentTimeMillis();
            float[] risks = {
                    // Week 1: worsening
                    0.25f, 0.35f, 0.45f, 0.55f, 0.65f, 0.72f, 0.78f,
                    // Week 2: recovering
                    0.70f, 0.60f, 0.50f, 0.40f, 0.32f, 0.26f, 0.22f
            };

            for (int day = 0; day < risks.length; day++) {
                long dayTs = midnightOf(now - (long)(risks.length - 1 - day) * DAY_MS);
                float risk = risks[day];
                // Shift primary category during recovery
                String primary = day < 7 ? "digital_addiction" : "stress_anxiety";
                String secondary = day < 7 ? "stress_anxiety" : "sleep_disruption";

                RiskClassification rc = buildClassification(
                        dayTs, risk,
                        risk * 0.9f,
                        risk * 0.85f,
                        risk * 0.7f,
                        risk * 0.5f,
                        risk * 0.75f,
                        risk * 0.4f,
                        primary, secondary,
                        risk >= 0.75f,
                        risk >= 0.75f ? "Peak risk during escalation phase" : null,
                        0.80f,
                        day == 0 ? 0f : risks[day] - risks[day - 1]
                );
                dao.insertOrReplace(rc);
            }

            int count = dao.getTotalClassificationCount();
            Log.i(TAG, "✓ Mixed recovery injected: " + count + " rows");
            showToast("✓ Mixed recovery injected (" + count + " days)");
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 6: GRADUAL WORSENING (SUBTLE)
    // 7 days of slow creep (0.30 → 0.45)
    // Expected TrendAnalyzer output: slope ≈ 0.01–0.02, trajectory = "gradually_worsening"
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Injects 7 days of subtle, gradual risk increase.
     * Validates that the analyzer detects slow-creep deterioration.
     */
    public void injectGradualWorsening() {
        executor.execute(() -> {
            Log.i(TAG, "═══ Injecting GRADUAL WORSENING trajectory (7 days) ═══");
            clearSync();

            long now = System.currentTimeMillis();
            float[] risks = {0.30f, 0.32f, 0.34f, 0.37f, 0.39f, 0.42f, 0.45f};

            for (int day = 0; day < risks.length; day++) {
                long dayTs = midnightOf(now - (long)(risks.length - 1 - day) * DAY_MS);
                float risk = risks[day];
                RiskClassification rc = buildClassification(
                        dayTs, risk,
                        risk * 0.9f, risk * 0.8f, risk * 0.6f,
                        risk * 0.5f, risk * 0.7f, risk * 0.4f,
                        "sleep_disruption", "stress_anxiety",
                        false, null,
                        0.82f,
                        day == 0 ? 0f : risks[day] - risks[day - 1]
                );
                dao.insertOrReplace(rc);
            }

            int count = dao.getTotalClassificationCount();
            Log.i(TAG, "✓ Gradual worsening injected: " + count + " rows");
            showToast("✓ Gradual worsening injected (" + count + " days)");
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY: CLEAR ALL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Deletes all RiskClassification rows from the database.
     * Use before injecting a fresh scenario.
     */
    public void clearAllClassifications() {
        executor.execute(() -> {
            clearSync();
            showToast("✓ All classifications cleared");
        });
    }

    private void clearSync() {
        int before = dao.getTotalClassificationCount();
        dao.deleteOlderThan(Long.MAX_VALUE);
        Log.i(TAG, "Cleared " + before + " existing classifications");
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY: DUMP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Logs all current RiskClassification rows for visual inspection.
     */
    public void dumpAllToLog() {
        executor.execute(() -> {
            java.util.List<RiskClassification> all = dao.getHistory(30);
            Log.i(TAG, "╔══════════════════════════════════════════════════════════╗");
            Log.i(TAG, "║         RISK CLASSIFICATION DATABASE DUMP               ║");
            Log.i(TAG, "╠══════════════════════════════════════════════════════════╣");
            if (all == null || all.isEmpty()) {
                Log.i(TAG, "║  (empty — no classifications found)                     ║");
            } else {
                for (int i = 0; i < all.size(); i++) {
                    RiskClassification rc = all.get(i);
                    Log.i(TAG, String.format(Locale.US,
                            "║ Day %2d │ risk=%.2f │ %s │ crisis=%s │ Δ=%+.3f │ %s",
                            i + 1, rc.overallRiskScore,
                            rc.getOverallSeverity().label,
                            rc.crisisFlag ? "YES" : "no ",
                            rc.riskDelta,
                            rc.primaryCategory != null ? rc.primaryCategory : "none"));
                }
            }
            Log.i(TAG, "╚══════════════════════════════════════════════════════════╝");
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER — Constructs a fully populated RiskClassification
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private RiskClassification buildClassification(
            long dayTimestamp,
            float overallRisk,
            float digital, float stress, float depression,
            float isolation, float sleep, float fulfilment,
            String primaryCategory, String secondaryCategory,
            boolean crisisFlag, String crisisReason,
            float confidence,
            float riskDelta
    ) {
        RiskClassification rc = new RiskClassification();

        // Temporal
        rc.timestamp = dayTimestamp + (12 * 60 * 60 * 1000); // noon of that day
        rc.dayTimestamp = dayTimestamp;

        // 6 risk dimensions (clamped 0–1)
        rc.digitalAddictionScore = clamp(digital);
        rc.stressAnxietyScore    = clamp(stress);
        rc.depressionRiskScore   = clamp(depression);
        rc.socialIsolationScore  = clamp(isolation);
        rc.sleepDisruptionScore  = clamp(sleep);
        rc.lowFulfilmentScore    = clamp(fulfilment);

        // Aggregate
        rc.overallRiskScore = clamp(overallRisk);
        rc.primaryCategory  = primaryCategory;
        rc.secondaryCategory = secondaryCategory;
        rc.confidence = clamp(confidence);

        // Crisis
        rc.crisisFlag = crisisFlag;
        rc.crisisReason = crisisReason;
        rc.interventionShown = false;

        // Metadata
        rc.classificationMode = "simulated";
        rc.featureDataCount = 28; // simulated full coverage
        rc.riskDelta = riskDelta;
        rc.riskMovingAverage = overallRisk; // simplified for sim

        // Build a synthetic FeatureVector JSON so TrendAnalyzer can parse it
        rc.featureVectorJson = buildSyntheticFeatureJson(overallRisk);

        return rc;
    }

    /**
     * Generates a compact FeatureVector JSON string with features distributed
     * around the overall risk level. Uses the same format as {@link FeatureVector#toJson()}.
     */
    @NonNull
    private String buildSyntheticFeatureJson(float overallRisk) {
        StringBuilder sb = new StringBuilder("{\"f\":[");
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            if (i > 0) sb.append(",");
            // Distribute features around the risk level with ±15% jitter
            float jitter = (float) (Math.random() * 0.3 - 0.15);
            float value = clamp(overallRisk + jitter);
            sb.append(String.format(Locale.US, "%.3f", value));
        }
        sb.append("],\"ts\":").append(System.currentTimeMillis());
        sb.append(",\"src\":\"simulated\"}");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Truncate a timestamp to midnight of that day. */
    private static long midnightOf(long timestamp) {
        return (timestamp / DAY_MS) * DAY_MS;
    }

    /** Show a toast on the main thread (safe from background executor). */
    private void showToast(String message) {
        try {
            android.os.Handler mainHandler = new android.os.Handler(
                    android.os.Looper.getMainLooper());
            mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Log.w(TAG, "Could not show toast: " + e.getMessage());
        }
    }
}
