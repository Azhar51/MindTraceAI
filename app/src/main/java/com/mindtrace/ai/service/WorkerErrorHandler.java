package com.mindtrace.ai.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.ErrorLog;

/**
 * Centralized error handler for all WorkManager workers.
 *
 * <p>Replaces the previous pattern of:
 * <pre>
 *   catch (Exception e) {
 *       Log.e(TAG, "Worker failed", e);
 *       return Result.retry();
 *   }
 * </pre>
 * with structured, categorized error logging and intelligent retry decisions.</p>
 *
 * <h3>Retry Policy:</h3>
 * <ul>
 *   <li><b>DATABASE</b>: Retry up to 3 attempts (transient disk/lock issues)</li>
 *   <li><b>NETWORK</b>: Retry up to 3 attempts (connectivity may recover)</li>
 *   <li><b>EXTRACTION</b>: Retry once (permission or data availability)</li>
 *   <li><b>CLASSIFICATION</b>: Fail immediately (deterministic — retry won't help)</li>
 *   <li><b>PERMISSION</b>: Fail immediately (user action required)</li>
 *   <li><b>UNKNOWN</b>: Retry up to 2 attempts</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   catch (Exception e) {
 *       return WorkerErrorHandler.handle(ctx, "ClassificationWorker", e, getRunAttemptCount());
 *   }
 * </pre>
 */
public final class WorkerErrorHandler {

    private static final String TAG = "WorkerErrorHandler";

    /** Maximum retry attempts per error category. */
    private static final int MAX_RETRY_DATABASE       = 3;
    private static final int MAX_RETRY_NETWORK        = 3;
    private static final int MAX_RETRY_EXTRACTION     = 1;
    private static final int MAX_RETRY_CLASSIFICATION = 0; // Deterministic — don't retry
    private static final int MAX_RETRY_PERMISSION     = 0; // Needs user action
    private static final int MAX_RETRY_UNKNOWN        = 2;

    private WorkerErrorHandler() { /* no instances */ }

    /**
     * Handle a worker exception: log it to Room and decide retry/failure.
     *
     * @param context      application context
     * @param workerName   canonical worker name (e.g. "ClassificationWorker")
     * @param exception    the caught exception
     * @param attemptCount current run attempt count (from getRunAttemptCount())
     * @return Result.retry() or Result.failure() based on error category and attempt count
     */
    @NonNull
    public static ListenableWorker.Result handle(
            @NonNull Context context,
            @NonNull String workerName,
            @NonNull Exception exception,
            int attemptCount) {

        // 1. Build structured error log
        ErrorLog errorLog = ErrorLog.from(workerName, exception, attemptCount);

        // 2. Determine retry eligibility
        int maxRetries = getMaxRetries(errorLog.category);
        boolean shouldRetry = attemptCount < maxRetries;
        errorLog.resolution = shouldRetry ? ErrorLog.RES_RETRY : ErrorLog.RES_FAILURE;

        // 3. Persist to Room (best-effort — don't let logging itself crash the worker)
        try {
            AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
            db.errorLogDao().insert(errorLog);
        } catch (Exception dbError) {
            Log.w(TAG, "Failed to persist error log to Room", dbError);
        }

        // 4. Console log with structured format
        if (shouldRetry) {
            Log.w(TAG, String.format("⚠ %s failed [%s/%s] attempt %d/%d — RETRYING",
                    workerName, errorLog.category, errorLog.exceptionType,
                    attemptCount, maxRetries));
        } else {
            Log.e(TAG, String.format("✗ %s failed [%s/%s] attempt %d — GIVING UP",
                    workerName, errorLog.category, errorLog.exceptionType,
                    attemptCount));
        }

        // 5. Return appropriate result
        return shouldRetry ? ListenableWorker.Result.retry() : ListenableWorker.Result.failure();
    }

    /**
     * Get the maximum retry count for a given error category.
     */
    private static int getMaxRetries(@NonNull String category) {
        switch (category) {
            case ErrorLog.CAT_DATABASE:       return MAX_RETRY_DATABASE;
            case ErrorLog.CAT_NETWORK:        return MAX_RETRY_NETWORK;
            case ErrorLog.CAT_EXTRACTION:     return MAX_RETRY_EXTRACTION;
            case ErrorLog.CAT_CLASSIFICATION: return MAX_RETRY_CLASSIFICATION;
            case ErrorLog.CAT_PERMISSION:     return MAX_RETRY_PERMISSION;
            default:                           return MAX_RETRY_UNKNOWN;
        }
    }
}
