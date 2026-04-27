package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Master Intervention Generator — the top-level orchestrator that combines
 * the InterventionEngine, TrapWatchGenerator, and MissionGenerator into a
 * single cohesive pipeline.
 *
 * <h3>Complete Pipeline:</h3>
 * <pre>
 *   Inputs:
 *     RiskClassification + CrisisLevel + BehaviorReport + OnboardingProfile
 *         │
 *     ┌───┴────────────────────────────────────────────────────┐
 *     │           InterventionGenerator (this class)           │
 *     │                                                        │
 *     │  ① InterventionEngine.generateFromClassification()     │
 *     │     → generates raw task list (3-5 tasks)              │
 *     │                                                        │
 *     │  ② TrapWatchGenerator.generateWarnings()               │
 *     │     → detects behavioral traps (0-4 warnings)          │
 *     │                                                        │
 *     │  ③ Deduplication against 48h history                   │
 *     │                                                        │
 *     │  ④ Pre-intervention risk stamping (efficacy baseline)  │
 *     │                                                        │
 *     │  ⑤ MissionGenerator.buildMission()                     │
 *     │     → structures tasks + warnings into daily mission   │
 *     │                                                        │
 *     └───┬────────────────────────────────────────────────────┘
 *         │
 *     Output:
 *       GenerationResult { tasks, mission, warnings, diagnostics }
 * </pre>
 *
 * <h3>Key Design Principles:</h3>
 * <ul>
 *   <li>Never shame. Never punish. Every task has a "why" explanation.</li>
 *   <li>Tasks rotate — no repetition within 48 hours.</li>
 *   <li>Dosage adapts to engagement (2-5 tasks/day).</li>
 *   <li>Closed-loop: efficacy data feeds back into future generation.</li>
 *   <li>Graceful degradation: works with partial/missing input data.</li>
 * </ul>
 *
 * @see InterventionEngine
 * @see TrapWatchGenerator
 * @see MissionGenerator
 */
public class InterventionGenerator {

    private static final String TAG = "InterventionGenerator";
    private static final long DEDUP_WINDOW_MS = 48L * 60 * 60 * 1000; // 48 hours

    private final InterventionEngine interventionEngine;
    private final TrapWatchGenerator trapWatchGenerator;
    private final MissionGenerator missionGenerator;
    private final AppDatabase db;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════

    public InterventionGenerator(@NonNull Context context) {
        this.interventionEngine = new InterventionEngine();
        this.trapWatchGenerator = new TrapWatchGenerator();
        this.missionGenerator = new MissionGenerator();
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    /**
     * Constructor with injectable dependencies (for testing).
     */
    public InterventionGenerator(@NonNull InterventionEngine engine,
                                  @NonNull TrapWatchGenerator trapWatch,
                                  @NonNull MissionGenerator missionGen,
                                  @NonNull AppDatabase database) {
        this.interventionEngine = engine;
        this.trapWatchGenerator = trapWatch;
        this.missionGenerator = missionGen;
        this.db = database;
    }

    // ═══════════════════════════════════════════════════════════════════
    // FULL GENERATION PIPELINE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Execute the complete intervention generation pipeline.
     * This is the primary entry point for the entire Phase 3 system.
     *
     * @param classification current risk classification
     * @param crisisLevel    current crisis level
     * @param behaviorReport latest behavior analysis (nullable)
     * @param profile        user's onboarding profile (nullable)
     * @return complete generation result with tasks, mission, and diagnostics
     */
    @NonNull
    public GenerationResult generate(
            @NonNull RiskClassification classification,
            @NonNull MultiModalClassifier.CrisisLevel crisisLevel,
            @Nullable BehaviorReport behaviorReport,
            @Nullable OnboardingProfile profile) {

        long startTime = System.currentTimeMillis();
        GenerationResult result = new GenerationResult();

        try {
            // ── Step 1: Load history for dedup + efficacy ──
            List<InterventionTask> taskHistory = loadRecentHistory();
            String[] comorbidities = detectComorbidities(classification);
            Map<String, Float> categoryEfficacy = computeCategoryEfficacy(taskHistory);

            // ── Step 2: Generate raw tasks via InterventionEngine ──
            List<InterventionTask> rawTasks = interventionEngine.generateFromClassification(
                    classification,
                    crisisLevel,
                    behaviorReport,
                    profile,
                    taskHistory,
                    comorbidities,
                    categoryEfficacy
            );
            result.rawTaskCount = rawTasks.size();

            // ── Step 3: Deduplicate against 48h history ──
            List<InterventionTask> dedupedTasks = deduplicateTasks(rawTasks, taskHistory);
            result.dedupRemovedCount = rawTasks.size() - dedupedTasks.size();

            // ── Step 4: Stamp pre-intervention risk (for efficacy tracking) ──
            interventionEngine.stampPreInterventionRisk(dedupedTasks, classification.overallRiskScore);

            // ── Step 5: Generate trap warnings ──
            result.warnings = trapWatchGenerator.generateWarnings(
                    behaviorReport, profile, classification);

            // ── Step 6: Build structured mission ──
            result.mission = missionGenerator.buildMission(
                    classification, dedupedTasks, result.warnings);

            // ── Step 7: Set final task list ──
            result.tasks = dedupedTasks;

            // ── Diagnostics ──
            result.generationTimeMs = System.currentTimeMillis() - startTime;
            result.comorbidityCount = comorbidities != null ? comorbidities.length : 0;
            result.efficacyDataAvailable = !categoryEfficacy.isEmpty();
            result.classificationMode = classification.classificationMode;
            result.overallRisk = classification.overallRiskScore;
            result.crisisLevel = crisisLevel.label;
            result.success = true;

            Log.d(TAG, "Pipeline complete: " + result);

        } catch (Exception e) {
            Log.e(TAG, "Generation pipeline failed", e);
            result.error = e.getMessage();
            result.success = false;

            // Fallback: generate basic tasks without dependencies
            try {
                result.tasks = interventionEngine.generateFromClassification(
                        classification, crisisLevel, null, null, null, null);
                result.mission = missionGenerator.buildFallbackMission(result.tasks);
                result.warnings = new ArrayList<>();
            } catch (Exception fallbackError) {
                Log.e(TAG, "Fallback generation also failed", fallbackError);
                result.tasks = new ArrayList<>();
            }
        }

        return result;
    }

    /**
     * Lightweight generation without behavior report (for background workers).
     */
    @NonNull
    public GenerationResult generateBasic(
            @NonNull RiskClassification classification,
            @NonNull MultiModalClassifier.CrisisLevel crisisLevel) {
        return generate(classification, crisisLevel, null, null);
    }

    /**
     * Generate a single micro-intervention for immediate crisis relief.
     */
    @Nullable
    public InterventionTask generateCrisisIntervention(@Nullable String riskCategory) {
        return interventionEngine.generateMicroIntervention(riskCategory);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEDUPLICATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Remove tasks that were completed or skipped within the last 48 hours.
     * Prevents repetition fatigue — users should see fresh interventions.
     */
    @NonNull
    private List<InterventionTask> deduplicateTasks(
            @NonNull List<InterventionTask> candidates,
            @NonNull List<InterventionTask> history) {

        java.util.Set<String> recentTitles = new java.util.HashSet<>();
        long cutoff = System.currentTimeMillis() - DEDUP_WINDOW_MS;

        for (InterventionTask historical : history) {
            if (historical.dateCreated > cutoff && historical.title != null) {
                recentTitles.add(historical.title.toLowerCase());
            }
        }

        List<InterventionTask> deduped = new ArrayList<>();
        for (InterventionTask candidate : candidates) {
            String titleKey = candidate.title != null ? candidate.title.toLowerCase() : "";
            if (!recentTitles.contains(titleKey)) {
                deduped.add(candidate);
            }
        }

        // If dedup removed everything, keep at least 1 task
        if (deduped.isEmpty() && !candidates.isEmpty()) {
            deduped.add(candidates.get(0));
        }

        return deduped;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMORBIDITY DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detect comorbidity patterns from risk classification.
     * These trigger specialized combo tasks in the InterventionEngine.
     */
    @NonNull
    private String[] detectComorbidities(@NonNull RiskClassification classification) {
        List<String> combos = new ArrayList<>();

        // Depression + Isolation loop
        if (classification.depressionRiskScore > 0.45f
                && classification.socialIsolationScore > 0.45f) {
            combos.add("depression_isolation_loop");
        }

        // Burnout triad: stress + low energy + low fulfilment
        if (classification.stressAnxietyScore > 0.50f
                && classification.lowFulfilmentScore > 0.40f) {
            combos.add("burnout_triad");
        }

        // Digital escape cycle: digital addiction masking distress
        if (classification.digitalAddictionScore > 0.50f
                && (classification.depressionRiskScore > 0.40f
                || classification.stressAnxietyScore > 0.40f)) {
            combos.add("digital_escape_cycle");
        }

        // Sleep-stress spiral
        if (classification.sleepDisruptionScore > 0.45f
                && classification.stressAnxietyScore > 0.45f) {
            combos.add("sleep_stress_spiral");
        }

        return combos.toArray(new String[0]);
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFICACY COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute per-category efficacy from historical task data.
     * Returns a map of riskCategory → average efficacy score.
     * Positive scores = effective interventions.
     */
    @NonNull
    private Map<String, Float> computeCategoryEfficacy(
            @NonNull List<InterventionTask> history) {

        Map<String, float[]> categoryAccum = new HashMap<>();

        for (InterventionTask task : history) {
            if (!task.hasEfficacyScore()) continue;
            String cat = task.linkedRiskCategory != null ? task.linkedRiskCategory : "general";

            float[] accum = categoryAccum.get(cat);
            if (accum == null) {
                accum = new float[]{0f, 0f}; // [sum, count]
                categoryAccum.put(cat, accum);
            }
            accum[0] += task.efficacyScore;
            accum[1] += 1f;
        }

        Map<String, Float> result = new HashMap<>();
        for (Map.Entry<String, float[]> entry : categoryAccum.entrySet()) {
            float avg = entry.getValue()[0] / entry.getValue()[1];
            if (entry.getValue()[1] >= 2) { // Need at least 2 data points
                result.put(entry.getKey(), avg);
            }
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HISTORY LOADING
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private List<InterventionTask> loadRecentHistory() {
        try {
            long since = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000); // 7 days
            List<InterventionTask> history = db.taskDao().getTasksSinceSync(since);
            return history != null ? history : new ArrayList<>();
        } catch (Exception e) {
            Log.w(TAG, "Failed to load task history", e);
            return new ArrayList<>();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OBSERVATION WINDOW MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Process tasks whose 2-hour observation windows have expired.
     * Computes efficacy scores for closed-loop reinforcement.
     *
     * @param currentRisk the latest overall risk score
     * @return list of tasks with newly computed efficacy scores
     */
    @NonNull
    public List<InterventionTask> processObservationWindows(float currentRisk) {
        try {
            long now = System.currentTimeMillis();
            List<InterventionTask> awaiting = db.taskDao().getTasksAwaitingEfficacy(now);
            if (awaiting == null || awaiting.isEmpty()) return new ArrayList<>();

            List<InterventionTask> updated = interventionEngine.finalizeObservationWindows(
                    awaiting, currentRisk);

            // Persist updated efficacy scores
            for (InterventionTask task : updated) {
                db.taskDao().update(task);
            }

            Log.d(TAG, "Processed " + updated.size() + " observation windows");
            return updated;

        } catch (Exception e) {
            Log.e(TAG, "Failed to process observation windows", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get human-readable efficacy summary for the dashboard.
     */
    @NonNull
    public String[] getEfficacySummary() {
        try {
            List<InterventionTask> history = loadRecentHistory();
            return interventionEngine.getEfficacySummary(history);
        } catch (Exception e) {
            return new String[]{"Unable to compute efficacy data."};
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WEEKLY REPORT GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate the weekly intervention report.
     */
    @NonNull
    public WeeklyInterventionReport generateWeeklyReport(
            int currentStreak, int longestStreak,
            float riskStart, float riskEnd) {
        try {
            long weekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            List<InterventionTask> weekTasks = db.taskDao().getTasksSinceSync(weekAgo);
            if (weekTasks == null) weekTasks = new ArrayList<>();

            return WeeklyInterventionReport.generate(
                    weekTasks, currentStreak, longestStreak, riskStart, riskEnd);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate weekly report", e);
            return WeeklyInterventionReport.generate(
                    new ArrayList<>(), 0, 0, 0, 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPONENT ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    /** Direct access to the underlying InterventionEngine. */
    @NonNull
    public InterventionEngine getInterventionEngine() {
        return interventionEngine;
    }

    /** Direct access to the TrapWatchGenerator. */
    @NonNull
    public TrapWatchGenerator getTrapWatchGenerator() {
        return trapWatchGenerator;
    }

    /** Direct access to the MissionGenerator. */
    @NonNull
    public MissionGenerator getMissionGenerator() {
        return missionGenerator;
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESULT DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Complete output of the intervention generation pipeline.
     */
    public static class GenerationResult {
        /** Whether the generation succeeded. */
        public boolean success;

        /** Final task list (after dedup, ready to persist). */
        public List<InterventionTask> tasks = new ArrayList<>();

        /** Structured daily mission. */
        @Nullable
        public MissionGenerator.TodayMission mission;

        /** Behavioral trap warnings. */
        public List<TrapWatchGenerator.TrapWarning> warnings = new ArrayList<>();

        // ── Diagnostics ──
        public int rawTaskCount;
        public int dedupRemovedCount;
        public int comorbidityCount;
        public boolean efficacyDataAvailable;
        public long generationTimeMs;
        public float overallRisk;
        public String crisisLevel;
        public String classificationMode;
        public String error;

        /** Total tasks ready to persist. */
        public int getTaskCount() {
            return tasks.size();
        }

        /** Whether any trap warnings were generated. */
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }

        @NonNull
        @Override
        public String toString() {
            return "GenerationResult{" +
                    "success=" + success +
                    ", tasks=" + (tasks != null ? tasks.size() : 0) +
                    ", warnings=" + (warnings != null ? warnings.size() : 0) +
                    ", raw=" + rawTaskCount +
                    ", deduped=" + dedupRemovedCount +
                    ", comorbidities=" + comorbidityCount +
                    ", efficacy=" + efficacyDataAvailable +
                    ", risk=" + String.format("%.0f%%", overallRisk * 100) +
                    ", crisis=" + crisisLevel +
                    ", time=" + generationTimeMs + "ms" +
                    (error != null ? ", error=" + error : "") +
                    '}';
        }
    }
}
