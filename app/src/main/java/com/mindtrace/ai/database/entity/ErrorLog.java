package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Structured error log entity for WorkManager worker failures.
 *
 * <p>Replaces blanket {@code catch(Exception) → Result.retry()} with categorized
 * error reporting. Each row represents a single worker failure event with full
 * diagnostic context for field debugging and analytics.</p>
 *
 * <h3>Error Categories:</h3>
 * <pre>
 *   DATABASE     — Room/SQLite failures (schema, disk full, corruption)
 *   NETWORK      — API call failures (timeout, HTTP error, unreachable)
 *   EXTRACTION   — Feature extraction failures (null data, permission denied)
 *   CLASSIFICATION — AI classifier crashes (NaN, OOM, model error)
 *   PERMISSION   — Missing runtime permissions (UsageStats, Accessibility)
 *   UNKNOWN      — Uncategorized failures
 * </pre>
 *
 * @see com.mindtrace.ai.database.dao.ErrorLogDao
 */
@Entity(
    tableName = "error_logs",
    indices = {
        @Index(value = "timestamp"),
        @Index(value = "workerName"),
        @Index(value = "category"),
        @Index(value = {"workerName", "category"})
    }
)
public class ErrorLog {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Timestamp of the failure occurrence (System.currentTimeMillis). */
    public long timestamp;

    /** Canonical worker name (e.g. "ClassificationWorker", "EfficacyWorker"). */
    @NonNull
    public String workerName = "";

    /** Error category for analytics grouping. */
    @NonNull
    public String category = "UNKNOWN";

    /** Exception class simple name (e.g. "SQLiteFullException"). */
    public String exceptionType;

    /** Exception message (truncated to 500 chars for storage efficiency). */
    public String message;

    /** First 3 lines of the stack trace for diagnostics. */
    public String stackTraceSnippet;

    /** Whether the worker returned Result.retry() or Result.failure(). */
    @NonNull
    public String resolution = "RETRY";

    /** Run attempt count at the time of failure (from WorkerParameters). */
    public int attemptCount;

    /** true if the worker eventually succeeded after retry. */
    public boolean resolvedOnRetry;

    // ── Error category constants ──

    public static final String CAT_DATABASE       = "DATABASE";
    public static final String CAT_NETWORK        = "NETWORK";
    public static final String CAT_EXTRACTION     = "EXTRACTION";
    public static final String CAT_CLASSIFICATION = "CLASSIFICATION";
    public static final String CAT_PERMISSION     = "PERMISSION";
    public static final String CAT_UNKNOWN        = "UNKNOWN";

    // ── Resolution constants ──

    public static final String RES_RETRY   = "RETRY";
    public static final String RES_FAILURE = "FAILURE";
    public static final String RES_SUCCESS = "SUCCESS";

    /**
     * Factory method: create an ErrorLog from a caught exception.
     *
     * @param workerName canonical worker name
     * @param exception  the caught exception
     * @param attempt    current run attempt count
     * @return populated ErrorLog (not yet persisted)
     */
    @NonNull
    public static ErrorLog from(@NonNull String workerName, @NonNull Exception exception, int attempt) {
        ErrorLog log = new ErrorLog();
        log.timestamp = System.currentTimeMillis();
        log.workerName = workerName;
        log.exceptionType = exception.getClass().getSimpleName();
        log.message = truncate(exception.getMessage(), 500);
        log.stackTraceSnippet = extractSnippet(exception, 3);
        log.attemptCount = attempt;
        log.category = categorize(exception);
        log.resolution = attempt >= 3 ? RES_FAILURE : RES_RETRY;
        return log;
    }

    /**
     * Classify an exception into a structured error category.
     * Uses exception class hierarchy for reliable categorization.
     */
    @NonNull
    public static String categorize(@NonNull Exception e) {
        String className = e.getClass().getName().toLowerCase();
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        // Database errors
        if (className.contains("sqlite") || className.contains("room")
                || className.contains("database") || msg.contains("disk full")
                || msg.contains("database is locked") || msg.contains("no such table")) {
            return CAT_DATABASE;
        }

        // Network errors
        if (className.contains("ioexception") || className.contains("timeout")
                || className.contains("socket") || className.contains("http")
                || className.contains("connect") || msg.contains("unreachable")
                || msg.contains("connection refused")) {
            return CAT_NETWORK;
        }

        // Permission errors
        if (className.contains("security") || className.contains("permission")
                || msg.contains("permission denied") || msg.contains("not granted")) {
            return CAT_PERMISSION;
        }

        // Classification/AI errors
        if (className.contains("arithmetic") || className.contains("outofmemory")
                || msg.contains("nan") || msg.contains("infinity")
                || msg.contains("classifier") || msg.contains("feature vector")) {
            return CAT_CLASSIFICATION;
        }

        // Extraction errors
        if (className.contains("nullpointer") || className.contains("illegalargument")
                || className.contains("illegalstate") || msg.contains("extract")
                || msg.contains("usage stats")) {
            return CAT_EXTRACTION;
        }

        return CAT_UNKNOWN;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private static String extractSnippet(Exception e, int maxLines) {
        StackTraceElement[] stack = e.getStackTrace();
        if (stack == null || stack.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        int lines = Math.min(maxLines, stack.length);
        for (int i = 0; i < lines; i++) {
            if (i > 0) sb.append("\n");
            sb.append("  at ").append(stack[i].toString());
        }
        return sb.toString();
    }

    @NonNull
    @Override
    public String toString() {
        return "ErrorLog{" + workerName + "/" + category + "/" + exceptionType +
                " attempt=" + attemptCount + " res=" + resolution + "}";
    }
}
