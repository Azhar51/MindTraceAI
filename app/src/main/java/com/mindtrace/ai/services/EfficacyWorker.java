package com.mindtrace.ai.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.service.WorkerErrorHandler;

import com.mindtrace.ai.ai.InterventionEngine;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.List;

/**
 * WorkManager worker that processes the closed-loop observation window.
 *
 * <p>Runs every 2 hours and performs two core operations:</p>
 * <ol>
 *   <li><b>Finalize expired windows:</b> Finds completed tasks whose 2-hour
 *       observation window has elapsed, captures the current risk score as the
 *       "after" measurement, and computes the efficacy delta.</li>
 *   <li><b>Log analytics:</b> Reports a summary of intervention effectiveness
 *       per risk category for debugging and future dashboard integration.</li>
 * </ol>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   TaskDao.getTasksAwaitingEfficacy(now)
 *       → InterventionEngine.finalizeObservationWindows(tasks, currentRisk)
 *       → TaskDao.update(task)  [for each finalized task]
 *       → Log efficacy summary
 * </pre>
 *
 * @see InterventionTask#OBSERVATION_WINDOW_MS
 * @see InterventionEngine#finalizeObservationWindows
 * @see InterventionEngine#getEfficacySummary
 */
public class EfficacyWorker extends Worker {

    private static final String TAG = "EfficacyWorker";

    public EfficacyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "EfficacyWorker started — scanning for expired observation windows");
        Context ctx = getApplicationContext();

        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            TaskDao taskDao = db.taskDao();
            RiskClassificationDao riskDao = db.riskClassificationDao();

            // ─── Step 1: Get the current risk baseline ──────────────────
            RiskClassification latestRisk = riskDao.getLatestSync();
            if (latestRisk == null) {
                Log.d(TAG, "No risk classification available yet — skipping efficacy pass");
                return Result.success();
            }
            float currentRisk = latestRisk.overallRiskScore;

            // ─── Step 2: Find tasks with expired observation windows ────
            long now = System.currentTimeMillis();
            List<InterventionTask> awaitingTasks = taskDao.getTasksAwaitingEfficacy(now);

            if (awaitingTasks == null || awaitingTasks.isEmpty()) {
                Log.d(TAG, "No tasks awaiting efficacy computation");
                logObservationStatus(taskDao, now);
                return Result.success();
            }

            Log.d(TAG, "Found " + awaitingTasks.size() + " task(s) awaiting efficacy computation");

            // ─── Step 3: Compute efficacy for each task ─────────────────
            InterventionEngine engine = new InterventionEngine();
            List<InterventionTask> finalized = engine.finalizeObservationWindows(
                    awaitingTasks, currentRisk);

            // ─── Step 4: Persist updated tasks ──────────────────────────
            int persistedCount = 0;
            for (InterventionTask task : finalized) {
                try {
                    taskDao.update(task);
                    persistedCount++;
                    Log.d(TAG, String.format(
                            "  ✓ Task #%d '%s' → efficacy: %+.1f%% (pre=%.2f, post=%.2f)",
                            task.id, task.title,
                            task.efficacyScore * 100,
                            task.preInterventionRisk,
                            task.postInterventionRisk));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to persist efficacy for task #" + task.id, e);
                }
            }

            Log.d(TAG, "Finalized efficacy for " + persistedCount + "/" +
                    awaitingTasks.size() + " tasks");

            // ─── Step 5: Log analytics summary ──────────────────────────
            logEfficacySummary(taskDao, engine);
            logObservationStatus(taskDao, now);

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "EfficacyWorker failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }

    /**
     * Log a human-readable summary of intervention effectiveness per category.
     * This data will later power the dashboard analytics cards.
     */
    private void logEfficacySummary(TaskDao taskDao, InterventionEngine engine) {
        try {
            long thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
            List<InterventionTask> measuredTasks = taskDao.getTasksWithEfficacySince(thirtyDaysAgo);

            if (measuredTasks != null && !measuredTasks.isEmpty()) {
                String[] summaries = engine.getEfficacySummary(measuredTasks);
                Log.d(TAG, "═══ 30-Day Efficacy Report ═══");
                for (String summary : summaries) {
                    Log.d(TAG, "  " + summary);
                }
                Log.d(TAG, "  Total measured: " + taskDao.getMeasuredEfficacyCount());

                // Log the most efficacious category
                String bestCategory = taskDao.getMostEfficaciousCategory();
                if (bestCategory != null) {
                    float bestAvg = taskDao.getAverageEfficacyForCategory(bestCategory);
                    Log.d(TAG, "  Best category: " + bestCategory +
                            " (avg efficacy: " + String.format("%.1f%%", bestAvg * 100) + ")");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to generate efficacy summary", e);
        }
    }

    /**
     * Log the current observation window status for debugging.
     */
    private void logObservationStatus(TaskDao taskDao, long now) {
        try {
            List<InterventionTask> inWindow = taskDao.getTasksInObservationWindow(now);
            if (inWindow != null && !inWindow.isEmpty()) {
                Log.d(TAG, inWindow.size() + " task(s) currently in observation window:");
                for (InterventionTask task : inWindow) {
                    long remainingMs = task.observationWindowEnd - now;
                    long remainingMin = remainingMs / (60 * 1000);
                    Log.d(TAG, "  ⏱ Task #" + task.id + " '" + task.title +
                            "' — " + remainingMin + " min remaining");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to log observation status", e);
        }
    }
}
