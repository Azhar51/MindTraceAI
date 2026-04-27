package com.mindtrace.ai.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.service.WorkerErrorHandler;

import com.mindtrace.ai.ai.AnomalyDetector;
import com.mindtrace.ai.ai.InterventionEngine;
import com.mindtrace.ai.ai.MentalStateClassifier;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.repository.SettingsRepository;
import com.mindtrace.ai.repository.UsageRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageWorker extends Worker {
    private final AppDatabase db;
    private final UsageRepository repository;

    public UsageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        db = AppDatabase.getInstance(context);
        repository = new UsageRepository(context);
    }

    private static final String TAG = "UsageWorker";

    @NonNull
    @Override
    public Result doWork() {
        try {
            SettingsRepository.SettingsState settingsState = new SettingsRepository(getApplicationContext()).getSettingsState();
            if (!settingsState.trackingEnabled || !settingsState.backgroundSnapshots || !settingsState.hasUsagePermission) {
                return Result.success();
            }

            // Single-pass: refreshTodayUsageSnapshot already builds the BehaviorReport
            // from the same UsageStatsManager data. Reuse it instead of re-querying.
            UsageRepository.DashboardSnapshot snapshot =
                    repository.refreshTodayUsageSnapshot(getApplicationContext(), settingsState.includeSystemApps);
            repository.computeUserBaseline();
            runAiAnalysis(snapshot != null ? snapshot.behaviorReport : null);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Usage sync failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }

    private void runAiAnalysis(BehaviorReport behaviorReport) {
        List<QuestionnaireResponse> responses = db.questionnaireDao().getRecentResponses();
        List<DailyUsage> usageHistory = db.usageDao().getUsageSince(System.currentTimeMillis() - 7L * 24L * 3600L * 1000L);
        List<InterventionTask> recentTasks = repository.getTasksSinceSync(
                System.currentTimeMillis() - (14L * 24L * 60L * 60L * 1000L)
        );
        // Use the report from the single-pass pipeline; only fall back if null
        if (behaviorReport == null) {
            behaviorReport = repository.getTodayBehavior(getApplicationContext());
        }
        UserBaseline baseline = repository.getUserBaselineSync();

        MentalStateClassifier classifier = new MentalStateClassifier();
        MentalStateClassifier.State state = classifier.classify(usageHistory, responses);

        AnomalyDetector anomalyDetector = new AnomalyDetector();
        AnomalyDetector.AnomalyProfile anomalyProfile = anomalyDetector.buildProfile(
                usageHistory == null || usageHistory.isEmpty() ? 0L : usageHistory.get(0).screenTimeMillis,
                usageHistory,
                responses == null || responses.isEmpty() ? null : responses.get(0),
                responses,
                calculateFulfillmentScore(recentTasks),
                recentTasks,
                baseline
        );

        InterventionEngine engine = new InterventionEngine();

        // ── Closed-loop: Finalize expired observation windows ──
        // This computes efficacy scores for tasks completed >2h ago whose
        // post-intervention risk hasn't been captured yet.
        try {
            long now = System.currentTimeMillis();
            float currentRisk = state.ordinal() * 0.15f; // Approximate risk from state
            List<InterventionTask> awaiting = db.taskDao().getTasksAwaitingEfficacy(now);
            if (awaiting != null && !awaiting.isEmpty()) {
                List<InterventionTask> updated = engine.finalizeObservationWindows(awaiting, currentRisk);
                for (InterventionTask t : updated) {
                    db.taskDao().update(t);
                }
            }
        } catch (Exception ignored) {
            // Efficacy finalization is non-critical; don't block task generation
        }

        // ── Build efficacy weight map for adaptive task generation ──
        Map<String, Float> categoryEfficacy = buildCategoryEfficacyMap();

        List<InterventionTask> newTasks = engine.generateTasks(
                state,
                behaviorReport,
                anomalyProfile,
                baseline,
                recentTasks,
                categoryEfficacy
        );

        // Stamp pre-intervention risk so efficacy can be computed later
        if (!newTasks.isEmpty()) {
            float approxRisk = anomalyProfile != null && anomalyProfile.screenTime != null
                    ? (float) Math.min(1.0, Math.max(0.0, anomalyProfile.screenTime.zScore * 0.15))
                    : state.ordinal() * 0.15f;
            engine.stampPreInterventionRisk(newTasks, approxRisk);
        }

        repository.replaceActiveTasksSync(newTasks);
    }

    /**
     * Build a category → avgEfficacy map from TaskDao for the InterventionEngine.
     * Returns an empty map if no measured efficacy data exists yet.
     */
    private Map<String, Float> buildCategoryEfficacyMap() {
        Map<String, Float> map = new HashMap<>();
        try {
            List<TaskDao.CategoryEfficacy> scores = db.taskDao().getCategoryEfficacyScores();
            if (scores != null) {
                for (TaskDao.CategoryEfficacy ce : scores) {
                    if (ce.category != null) {
                        map.put(ce.category, ce.avgEfficacy);
                    }
                }
            }
        } catch (Exception ignored) {
            // Graceful degradation: empty map means no efficacy weighting
        }
        return map;
    }

    private int calculateFulfillmentScore(List<InterventionTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        int completed = 0;
        for (InterventionTask task : tasks) {
            if (task.isCompleted) {
                completed++;
            }
        }
        return Math.round((completed * 100f) / tasks.size());
    }
}
