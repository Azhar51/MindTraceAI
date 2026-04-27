package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.entity.InterventionTask;

import java.util.List;

/**
 * Manages task lifecycle: expiry, snooze reactivation, and stale cleanup.
 *
 * <h3>Task State Machine:</h3>
 * <pre>
 *   PENDING → ACTIVE → COMPLETED / SKIPPED / EXPIRED / SNOOZED
 *                                                        ↓ (timer)
 *                                                      ACTIVE
 * </pre>
 *
 * <h3>Execution Points:</h3>
 * <ul>
 *   <li>On app open (Activity.onResume)</li>
 *   <li>Nightly via WorkManager (TaskLifecycleWorker)</li>
 * </ul>
 */
public class TaskLifecycleManager {

    private static final String TAG = "TaskLifecycle";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long STALE_THRESHOLD_DAYS = 7;

    private final TaskDao taskDao;

    public TaskLifecycleManager(@NonNull Context context) {
        this.taskDao = AppDatabase.getInstance(context.getApplicationContext()).taskDao();
    }

    public TaskLifecycleManager(@NonNull TaskDao dao) {
        this.taskDao = dao;
    }

    /**
     * Run full lifecycle maintenance.
     * Call on app open and via nightly worker.
     *
     * @return summary of actions taken
     */
    @NonNull
    public LifecycleResult runMaintenance() {
        long now = System.currentTimeMillis();
        LifecycleResult result = new LifecycleResult();

        try {
            // 1. Auto-expire tasks past their expiry time
            result.expired = taskDao.autoExpireTasks(now);

            // 2. Reactivate snoozed tasks whose snooze period ended
            result.reactivated = taskDao.reactivateSnoozedTasks(now);

            // 3. Clean up stale tasks (>7 days old, non-completed)
            long staleCutoff = now - (STALE_THRESHOLD_DAYS * DAY_MS);
            result.cleaned = taskDao.deleteOlderThan(staleCutoff);

            if (result.hasChanges()) {
                Log.d(TAG, "Lifecycle: " + result);
            }
        } catch (Exception e) {
            Log.e(TAG, "Lifecycle maintenance failed", e);
            result.error = e.getMessage();
        }

        return result;
    }

    /**
     * Check and mark individual tasks that need state transitions.
     * More granular than bulk SQL — useful for logging each transition.
     */
    public int processIndividualExpiry() {
        long now = System.currentTimeMillis();
        int count = 0;

        try {
            List<InterventionTask> expired = taskDao.getExpiredUnmarked(now);
            for (InterventionTask task : expired) {
                task.markExpired();
                taskDao.update(task);
                count++;
                Log.d(TAG, "Expired: " + task.title);
            }
        } catch (Exception e) {
            Log.e(TAG, "Individual expiry failed", e);
        }

        return count;
    }

    /**
     * Reactivate snoozed tasks individually.
     */
    public int processSnoozeReactivation() {
        long now = System.currentTimeMillis();
        int count = 0;

        try {
            List<InterventionTask> unsnoozed = taskDao.getUnsnoozedTasks(now);
            for (InterventionTask task : unsnoozed) {
                task.status = "ACTIVE";
                task.snoozedUntil = 0;
                taskDao.update(task);
                count++;
                Log.d(TAG, "Reactivated: " + task.title);
            }
        } catch (Exception e) {
            Log.e(TAG, "Snooze reactivation failed", e);
        }

        return count;
    }

    /** Result of a lifecycle maintenance run. */
    public static class LifecycleResult {
        public int expired;
        public int reactivated;
        public int cleaned;
        public String error;

        public boolean hasChanges() {
            return expired > 0 || reactivated > 0 || cleaned > 0;
        }

        @NonNull
        @Override
        public String toString() {
            return expired + " expired, " + reactivated + " reactivated, " + cleaned + " cleaned" +
                    (error != null ? " [ERROR: " + error + "]" : "");
        }
    }
}
