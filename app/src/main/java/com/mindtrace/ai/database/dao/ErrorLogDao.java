package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.ErrorLog;

import java.util.List;

/**
 * DAO for the {@link ErrorLog} entity.
 *
 * <p>Provides insert, query, and cleanup operations for structured worker
 * error logs. Supports both analytics (aggregation queries) and debugging
 * (recent error retrieval).</p>
 */
@Dao
public interface ErrorLogDao {

    /** Insert a new error log entry. */
    @Insert
    void insert(ErrorLog errorLog);

    /** Get the N most recent error logs across all workers. */
    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC LIMIT :limit")
    List<ErrorLog> getRecent(int limit);

    /** Get error logs for a specific worker (most recent first). */
    @Query("SELECT * FROM error_logs WHERE workerName = :workerName ORDER BY timestamp DESC LIMIT :limit")
    List<ErrorLog> getForWorker(String workerName, int limit);

    /** Get error logs for a specific category since a given timestamp. */
    @Query("SELECT * FROM error_logs WHERE category = :category AND timestamp >= :since ORDER BY timestamp DESC")
    List<ErrorLog> getByCategorySince(String category, long since);

    /** Count errors per category in the last N days (for analytics dashboard). */
    @Query("SELECT category, COUNT(*) as errorCount FROM error_logs WHERE timestamp >= :since GROUP BY category ORDER BY errorCount DESC")
    List<CategoryErrorCount> getCategoryCountsSince(long since);

    /** Count total errors for a specific worker since a timestamp. */
    @Query("SELECT COUNT(*) FROM error_logs WHERE workerName = :workerName AND timestamp >= :since")
    int getErrorCountForWorker(String workerName, long since);

    /** Get the most recent error for a worker (for retry-decision logic). */
    @Query("SELECT * FROM error_logs WHERE workerName = :workerName ORDER BY timestamp DESC LIMIT 1")
    ErrorLog getLatestForWorker(String workerName);

    /** Check if a specific error category has been recurring (3+ times in 24h). */
    @Query("SELECT COUNT(*) FROM error_logs WHERE category = :category AND timestamp >= :since")
    int getRecurrenceCount(String category, long since);

    /** Observe error count (for UI badge/indicator). */
    @Query("SELECT COUNT(*) FROM error_logs WHERE timestamp >= :since")
    LiveData<Integer> observeErrorCountSince(long since);

    /** Delete error logs older than a given timestamp (cleanup). */
    @Query("DELETE FROM error_logs WHERE timestamp < :before")
    int deleteOlderThan(long before);

    /** Delete all error logs (debug/testing). */
    @Query("DELETE FROM error_logs")
    void deleteAll();

    /**
     * Projection class for category-level error aggregation.
     */
    class CategoryErrorCount {
        public String category;
        public int errorCount;
    }
}
