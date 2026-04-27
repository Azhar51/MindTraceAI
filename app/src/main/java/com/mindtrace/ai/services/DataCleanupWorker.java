package com.mindtrace.ai.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.service.WorkerErrorHandler;

import com.mindtrace.ai.database.AppDatabase;

/**
 * DataCleanupWorker — weekly maintenance worker that prunes stale data,
 * compresses storage, and maintains database health.
 *
 * <h3>Cleanup Tasks:</h3>
 * <ul>
 *   <li>Delete usage snapshots older than 90 days</li>
 *   <li>Delete expired/non-completed tasks older than 60 days</li>
 *   <li>Clean up orphaned data</li>
 *   <li>Log storage stats</li>
 * </ul>
 */
public class DataCleanupWorker extends Worker {

    private static final String TAG = "DataCleanupWorker";
    private static final long NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000;
    private static final long SIXTY_DAYS_MS = 60L * 24 * 60 * 60 * 1000;

    public DataCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting data cleanup...");

        try {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            long now = System.currentTimeMillis();
            int totalCleaned = 0;

            // 1. Prune old usage snapshots (>90 days)
            try {
                long cutoff = now - NINETY_DAYS_MS;
                int deleted = db.appUsageSnapshotDao().deleteOlderThan(cutoff);
                totalCleaned += deleted;
                Log.d(TAG, "Pruned " + deleted + " old usage snapshots");
            } catch (Exception e) {
                Log.w(TAG, "Usage snapshot cleanup failed", e);
            }

            // 2. Delete old expired tasks (>60 days, non-completed)
            try {
                long cutoff = now - SIXTY_DAYS_MS;
                int deleted = db.taskDao().deleteOlderThan(cutoff);
                totalCleaned += deleted;
                Log.d(TAG, "Pruned " + deleted + " old expired tasks");
            } catch (Exception e) {
                Log.w(TAG, "Task cleanup failed", e);
            }

            // 3. Clean up old behavior snapshots (>90 days)
            try {
                long cutoff = now - NINETY_DAYS_MS;
                int deleted = db.behaviorSnapshotDao().deleteOlderThan(cutoff);
                totalCleaned += deleted;
                Log.d(TAG, "Pruned " + deleted + " old behavior snapshots");
            } catch (Exception e) {
                Log.w(TAG, "Behavior snapshot cleanup failed", e);
            }

            // 4. Prune old error logs (>30 days)
            try {
                long cutoff = now - (30L * 24 * 60 * 60 * 1000);
                int deleted = db.errorLogDao().deleteOlderThan(cutoff);
                totalCleaned += deleted;
                if (deleted > 0) Log.d(TAG, "Pruned " + deleted + " old error logs");
            } catch (Exception e) {
                Log.w(TAG, "Error log cleanup failed", e);
            }

            // 5. Log stats
            try {
                int totalTasks = db.taskDao().getTotalCount();
                int totalJournals = db.journalDao().getTotalCount();
                Log.d(TAG, String.format(
                        "Cleanup complete: removed %d records. Current: %d tasks, %d journals",
                        totalCleaned, totalTasks, totalJournals));
            } catch (Exception ignored) {}

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Data cleanup failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }
}
