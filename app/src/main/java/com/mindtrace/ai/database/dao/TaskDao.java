package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.mindtrace.ai.database.entity.InterventionTask;
import java.util.List;

/**
 * DAO for {@link InterventionTask} — provides advanced queries for
 * task lifecycle management, analytics, streaks, and effectiveness tracking.
 */
@Dao
public interface TaskDao {

    // ═══════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════

    @Insert
    void insert(InterventionTask task);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<InterventionTask> tasks);

    @Update
    void update(InterventionTask task);

    @Query("DELETE FROM intervention_tasks WHERE id = :taskId")
    void deleteById(int taskId);

    // ═══════════════════════════════════════════════════════════════════
    // ACTIVE TASKS (status-based)
    // ═══════════════════════════════════════════════════════════════════

    /** Get active tasks (not completed/skipped/expired). */
    @Query("SELECT * FROM intervention_tasks WHERE status = 'ACTIVE' ORDER BY priority DESC, dateCreated DESC")
    LiveData<List<InterventionTask>> getActiveTasks();

    @Query("SELECT * FROM intervention_tasks WHERE status = 'ACTIVE' ORDER BY priority DESC, dateCreated DESC")
    List<InterventionTask> getActiveTasksSync();

    /** Get active tasks filtered by category. */
    @Query("SELECT * FROM intervention_tasks WHERE status = 'ACTIVE' AND category = :category ORDER BY priority DESC")
    LiveData<List<InterventionTask>> getActiveTasksByCategory(String category);

    /** Get active tasks filtered by linked risk category. */
    @Query("SELECT * FROM intervention_tasks WHERE status = 'ACTIVE' AND linkedRiskCategory = :risk ORDER BY priority DESC")
    List<InterventionTask> getActiveTasksByRisk(String risk);

    /** Get active micro-interventions. */
    @Query("SELECT * FROM intervention_tasks WHERE status = 'ACTIVE' AND isMicroIntervention = 1 ORDER BY priority DESC")
    List<InterventionTask> getActiveMicroInterventions();

    // ═══════════════════════════════════════════════════════════════════
    // COMPLETED / HISTORY
    // ═══════════════════════════════════════════════════════════════════

    @Query("SELECT * FROM intervention_tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    LiveData<List<InterventionTask>> getCompletedTasks();

    @Query("SELECT * FROM intervention_tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    List<InterventionTask> getCompletedTasksSync();

    @Query("SELECT * FROM intervention_tasks WHERE status = 'COMPLETED' AND completedAt >= :since ORDER BY completedAt DESC")
    List<InterventionTask> getCompletedSince(long since);

    @Query("SELECT * FROM intervention_tasks WHERE status = 'SKIPPED' ORDER BY skippedAt DESC")
    LiveData<List<InterventionTask>> getSkippedTasks();

    @Query("SELECT * FROM intervention_tasks WHERE status = 'SKIPPED' ORDER BY skippedAt DESC")
    List<InterventionTask> getSkippedTasksSync();

    @Query("SELECT * FROM intervention_tasks WHERE dateCreated >= :since ORDER BY dateCreated DESC")
    List<InterventionTask> getTasksSinceSync(long since);

    @Query("SELECT * FROM intervention_tasks ORDER BY dateCreated DESC")
    LiveData<List<InterventionTask>> getAllTasks();

    @Query("SELECT * FROM intervention_tasks ORDER BY dateCreated DESC LIMIT :limit")
    List<InterventionTask> getRecentTasks(int limit);

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /** Get tasks that have expired but not yet marked as EXPIRED. */
    @Query("SELECT * FROM intervention_tasks WHERE status = 'ACTIVE' AND expiresAt > 0 AND expiresAt < :now")
    List<InterventionTask> getExpiredUnmarked(long now);

    /** Bulk-mark expired tasks. */
    @Query("UPDATE intervention_tasks SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expiresAt > 0 AND expiresAt < :now")
    int autoExpireTasks(long now);

    /** Get snoozed tasks whose snooze period has ended. */
    @Query("SELECT * FROM intervention_tasks WHERE status = 'SNOOZED' AND snoozedUntil > 0 AND snoozedUntil < :now")
    List<InterventionTask> getUnsnoozedTasks(long now);

    /** Re-activate snoozed tasks whose snooze period ended. */
    @Query("UPDATE intervention_tasks SET status = 'ACTIVE' WHERE status = 'SNOOZED' AND snoozedUntil > 0 AND snoozedUntil < :now")
    int reactivateSnoozedTasks(long now);

    /** Delete tasks older than the given timestamp. */
    @Query("DELETE FROM intervention_tasks WHERE dateCreated < :before AND status != 'COMPLETED'")
    int deleteOlderThan(long before);

    // ═══════════════════════════════════════════════════════════════════
    // COUNTS & ANALYTICS
    // ═══════════════════════════════════════════════════════════════════

    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE dateCreated >= :since")
    int getTotalCountSince(long since);

    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE status = 'COMPLETED' AND completedAt >= :since")
    int getCompletedCountSince(long since);

    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE status = 'SKIPPED' AND skippedAt >= :since")
    int getSkippedCountSince(long since);

    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE status = 'EXPIRED' AND dateCreated >= :since")
    int getExpiredCountSince(long since);

    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE status = 'ACTIVE'")
    int getActiveCount();

    @Query("SELECT COUNT(*) FROM intervention_tasks")
    int getTotalCount();

    // ═══════════════════════════════════════════════════════════════════
    // EFFECTIVENESS
    // ═══════════════════════════════════════════════════════════════════

    /** Average effectiveness rating for completed tasks in a category. */
    @Query("SELECT AVG(effectivenessRating) FROM intervention_tasks WHERE status = 'COMPLETED' AND effectivenessRating > 0 AND category = :category")
    float getAverageEffectiveness(String category);

    /** Average effectiveness across all completed rated tasks. */
    @Query("SELECT AVG(effectivenessRating) FROM intervention_tasks WHERE status = 'COMPLETED' AND effectivenessRating > 0")
    float getOverallAverageEffectiveness();

    /** Most effective category (highest average rating). */
    @Query("SELECT category FROM intervention_tasks WHERE status = 'COMPLETED' AND effectivenessRating > 0 GROUP BY category ORDER BY AVG(effectivenessRating) DESC LIMIT 1")
    String getMostEffectiveCategory();

    /** Least effective category. */
    @Query("SELECT category FROM intervention_tasks WHERE status = 'COMPLETED' AND effectivenessRating > 0 GROUP BY category ORDER BY AVG(effectivenessRating) ASC LIMIT 1")
    String getLeastEffectiveCategory();

    /** Count of tasks with effectiveness ratings. */
    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE effectivenessRating > 0")
    int getRatedTaskCount();

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORY ANALYTICS
    // ═══════════════════════════════════════════════════════════════════

    /** Get completion count per category since a timestamp. */
    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE status = 'COMPLETED' AND category = :category AND completedAt >= :since")
    int getCompletedCountForCategory(String category, long since);

    /** Get skip count per category since a timestamp. */
    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE status = 'SKIPPED' AND category = :category AND skippedAt >= :since")
    int getSkippedCountForCategory(String category, long since);

    /** Most skipped category. */
    @Query("SELECT category FROM intervention_tasks WHERE status = 'SKIPPED' GROUP BY category ORDER BY COUNT(*) DESC LIMIT 1")
    String getMostSkippedCategory();

    /** Most completed category. */
    @Query("SELECT category FROM intervention_tasks WHERE status = 'COMPLETED' GROUP BY category ORDER BY COUNT(*) DESC LIMIT 1")
    String getMostCompletedCategory();

    // ═══════════════════════════════════════════════════════════════════
    // STREAK QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /** Get distinct days (truncated to midnight) with at least 1 completion. */
    @Query("SELECT DISTINCT (completedAt / 86400000) AS day FROM intervention_tasks WHERE status = 'COMPLETED' ORDER BY day DESC")
    List<Long> getCompletionDays();

    /** Count of unique completion days in the last N days. */
    @Query("SELECT COUNT(DISTINCT (completedAt / 86400000)) FROM intervention_tasks WHERE status = 'COMPLETED' AND completedAt >= :since")
    int getCompletionDayCountSince(long since);

    // ═══════════════════════════════════════════════════════════════════
    // DEDUPLICATION
    // ═══════════════════════════════════════════════════════════════════

    /** Check if a task with this title was created/completed/skipped in the recent window. */
    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE title = :title AND dateCreated >= :since")
    int getRecentCountForTitle(String title, long since);

    /** Check if a task with this title is currently active. */
    @Query("SELECT COUNT(*) FROM intervention_tasks WHERE title = :title AND status = 'ACTIVE'")
    int getActiveCountForTitle(String title);
    /** All tasks (sync). */
    @Query("SELECT * FROM intervention_tasks ORDER BY dateCreated DESC")
    java.util.List<com.mindtrace.ai.database.entity.InterventionTask> getAllTasksSync();

    /** Delete expired tasks older than a timestamp. */
    @Query("DELETE FROM intervention_tasks WHERE status = 'EXPIRED' AND dateCreated < :before")
    int deleteExpiredOlderThan(long before);

    // ═══════════════════════════════════════════════════════════════════
    // OBSERVATION WINDOW — Closed-loop efficacy tracking
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get completed tasks whose observation window has expired
     * but whose post-intervention risk has not yet been captured.
     * These are ready for efficacy computation.
     */
    @Query("SELECT * FROM intervention_tasks WHERE status = 'COMPLETED' " +
            "AND observationWindowEnd > 0 AND observationWindowEnd <= :now " +
            "AND postInterventionRisk = 0")
    List<InterventionTask> getTasksAwaitingEfficacy(long now);

    /**
     * Get tasks currently in their active observation window.
     * Useful for UI indicators showing "observing effects..."
     */
    @Query("SELECT * FROM intervention_tasks WHERE status = 'COMPLETED' " +
            "AND observationWindowEnd > :now")
    List<InterventionTask> getTasksInObservationWindow(long now);

    /** Average efficacy score for a given risk category (completed + measured tasks only). */
    @Query("SELECT AVG(efficacyScore) FROM intervention_tasks " +
            "WHERE status = 'COMPLETED' AND postInterventionRisk > 0 " +
            "AND linkedRiskCategory = :riskCategory")
    float getAverageEfficacyForCategory(String riskCategory);

    /** Average efficacy score across all measured tasks. */
    @Query("SELECT AVG(efficacyScore) FROM intervention_tasks " +
            "WHERE status = 'COMPLETED' AND postInterventionRisk > 0")
    float getOverallAverageEfficacy();

    /** Count of tasks with completed efficacy measurements. */
    @Query("SELECT COUNT(*) FROM intervention_tasks " +
            "WHERE status = 'COMPLETED' AND postInterventionRisk > 0")
    int getMeasuredEfficacyCount();

    /** Get the most effective task category (highest average efficacy). */
    @Query("SELECT linkedRiskCategory FROM intervention_tasks " +
            "WHERE status = 'COMPLETED' AND postInterventionRisk > 0 " +
            "GROUP BY linkedRiskCategory ORDER BY AVG(efficacyScore) DESC LIMIT 1")
    String getMostEfficaciousCategory();

    /** Get recent tasks with completed efficacy for analytics display. */
    @Query("SELECT * FROM intervention_tasks " +
            "WHERE status = 'COMPLETED' AND postInterventionRisk > 0 " +
            "AND completedAt >= :since ORDER BY completedAt DESC")
    List<InterventionTask> getTasksWithEfficacySince(long since);

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORY EFFICACY MAP — for adaptive weighting
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Return average efficacy score grouped by linked risk category.
     * Used by InterventionEngine to build the efficacy weight map for
     * adaptive task generation.
     */
    @Query("SELECT linkedRiskCategory AS category, AVG(efficacyScore) AS avgEfficacy " +
            "FROM intervention_tasks " +
            "WHERE status = 'COMPLETED' AND postInterventionRisk > 0 " +
            "GROUP BY linkedRiskCategory")
    List<CategoryEfficacy> getCategoryEfficacyScores();

    /** Lightweight POJO for category-level efficacy aggregation. */
    class CategoryEfficacy {
        public String category;
        public float avgEfficacy;
    }
}
