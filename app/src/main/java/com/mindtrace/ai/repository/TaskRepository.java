package com.mindtrace.ai.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.entity.InterventionTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository layer for {@link InterventionTask} — provides lifecycle management,
 * analytics, streak calculation, effectiveness tracking, and deduplication.
 *
 * <p>Separates the task intelligence system from the monolithic UsageRepository.</p>
 */
public class TaskRepository {

    private static final String TAG = "TaskRepository";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final TaskDao dao;

    public TaskRepository(@NonNull Context context) {
        this.dao = AppDatabase.getInstance(context.getApplicationContext()).taskDao();
    }

    public TaskRepository(@NonNull TaskDao dao) {
        this.dao = dao;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════════════════

    public void insert(@NonNull InterventionTask task) {
        try { dao.insert(task); }
        catch (Exception e) { Log.e(TAG, "Insert failed", e); }
    }

    public void insertAll(@NonNull List<InterventionTask> tasks) {
        try { dao.insertAll(tasks); }
        catch (Exception e) { Log.e(TAG, "Bulk insert failed", e); }
    }

    /** Insert only if no active task with the same title exists. */
    public boolean insertIfNotDuplicate(@NonNull InterventionTask task) {
        try {
            if (dao.getActiveCountForTitle(task.title) > 0) return false;
            // Also check recently created (last 48h)
            long since48h = System.currentTimeMillis() - (2 * DAY_MS);
            if (dao.getRecentCountForTitle(task.title, since48h) > 0) return false;
            dao.insert(task);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Dedup insert failed", e);
            return false;
        }
    }

    public void update(@NonNull InterventionTask task) {
        try { dao.update(task); }
        catch (Exception e) { Log.e(TAG, "Update failed", e); }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    public LiveData<List<InterventionTask>> getActiveTasks() {
        return dao.getActiveTasks();
    }

    @NonNull
    public LiveData<List<InterventionTask>> getActiveTasksByCategory(@NonNull String category) {
        return dao.getActiveTasksByCategory(category);
    }

    @NonNull
    public LiveData<List<InterventionTask>> getCompletedTasks() {
        return dao.getCompletedTasks();
    }

    @NonNull
    public LiveData<List<InterventionTask>> getAllTasks() {
        return dao.getAllTasks();
    }

    @NonNull
    public List<InterventionTask> getActiveTasksSync() {
        try { return dao.getActiveTasksSync(); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Run lifecycle maintenance: expire old tasks, reactivate snoozed ones.
     * Call on app open and via nightly WorkManager.
     * @return number of tasks affected
     */
    public int runLifecycleMaintenance() {
        long now = System.currentTimeMillis();
        int expired = 0, reactivated = 0;
        try {
            expired = dao.autoExpireTasks(now);
            reactivated = dao.reactivateSnoozedTasks(now);
            if (expired > 0 || reactivated > 0) {
                Log.d(TAG, "Lifecycle: " + expired + " expired, " + reactivated + " reactivated");
            }
        } catch (Exception e) {
            Log.e(TAG, "Lifecycle maintenance failed", e);
        }
        return expired + reactivated;
    }

    /** Clean up old non-completed tasks (older than 7 days). */
    public int cleanupStale() {
        long cutoff = System.currentTimeMillis() - (7 * DAY_MS);
        try { return dao.deleteOlderThan(cutoff); }
        catch (Exception e) { return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLETION RATE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute completion rate (0.0 - 1.0) for the last N days.
     */
    public float getCompletionRate(int lastNDays) {
        long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
        try {
            int total = dao.getTotalCountSince(since);
            if (total == 0) return 0f;
            int completed = dao.getCompletedCountSince(since);
            return (float) completed / total;
        } catch (Exception e) { return 0f; }
    }

    /**
     * Compute skip rate (0.0 - 1.0) for the last N days.
     */
    public float getSkipRate(int lastNDays) {
        long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
        try {
            int total = dao.getTotalCountSince(since);
            if (total == 0) return 0f;
            int skipped = dao.getSkippedCountSince(since);
            return (float) skipped / total;
        } catch (Exception e) { return 0f; }
    }

    /**
     * Get skip-to-complete ratio. >1 means user skips more than completes.
     */
    public float getSkipToCompleteRatio(int lastNDays) {
        long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
        try {
            int completed = dao.getCompletedCountSince(since);
            int skipped = dao.getSkippedCountSince(since);
            if (completed == 0) return skipped > 0 ? Float.MAX_VALUE : 0f;
            return (float) skipped / completed;
        } catch (Exception e) { return 0f; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STREAKS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculate the current consecutive-day completion streak.
     * A streak is broken if any day has 0 completions.
     */
    public int getCurrentStreak() {
        try {
            List<Long> days = dao.getCompletionDays(); // desc order, epoch-day values
            if (days == null || days.isEmpty()) return 0;

            long todayEpochDay = System.currentTimeMillis() / DAY_MS;
            int streak = 0;

            for (int i = 0; i < days.size(); i++) {
                long expectedDay = todayEpochDay - i;
                if (days.get(i) == expectedDay) {
                    streak++;
                } else {
                    break;
                }
            }
            return streak;
        } catch (Exception e) { return 0; }
    }

    /**
     * Count unique days with completions in last N days.
     */
    public int getCompletionDayCount(int lastNDays) {
        long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
        try { return dao.getCompletionDayCountSince(since); }
        catch (Exception e) { return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFECTIVENESS
    // ═══════════════════════════════════════════════════════════════════

    /** Average effectiveness rating for a category. */
    public float getEffectiveness(@NonNull String category) {
        try { return dao.getAverageEffectiveness(category); }
        catch (Exception e) { return 0f; }
    }

    /** Overall average effectiveness. */
    public float getOverallEffectiveness() {
        try { return dao.getOverallAverageEffectiveness(); }
        catch (Exception e) { return 0f; }
    }

    /** Category name that works best for this user. */
    @Nullable
    public String getMostEffectiveCategory() {
        try { return dao.getMostEffectiveCategory(); }
        catch (Exception e) { return null; }
    }

    /** Category this user resists most (most skipped). */
    @Nullable
    public String getMostSkippedCategory() {
        try { return dao.getMostSkippedCategory(); }
        catch (Exception e) { return null; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COUNTS
    // ═══════════════════════════════════════════════════════════════════

    public int getActiveCount() {
        try { return dao.getActiveCount(); }
        catch (Exception e) { return 0; }
    }

    public int getTotalCount() {
        try { return dao.getTotalCount(); }
        catch (Exception e) { return 0; }
    }

    public int getCompletedCount(int lastNDays) {
        long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
        try { return dao.getCompletedCountSince(since); }
        catch (Exception e) { return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WEEKLY REPORT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a structured weekly intervention report.
     */
    @NonNull
    public WeeklyTaskReport getWeeklyReport() {
        WeeklyTaskReport report = new WeeklyTaskReport();
        long since = System.currentTimeMillis() - (7 * DAY_MS);
        try {
            report.generated = dao.getTotalCountSince(since);
            report.completed = dao.getCompletedCountSince(since);
            report.skipped = dao.getSkippedCountSince(since);
            report.expired = dao.getExpiredCountSince(since);
            report.completionRate = report.generated > 0 ? (float) report.completed / report.generated : 0f;
            report.avgEffectiveness = dao.getOverallAverageEffectiveness();
            report.bestCategory = dao.getMostEffectiveCategory();
            report.worstCategory = dao.getLeastEffectiveCategory();
            report.mostSkipped = dao.getMostSkippedCategory();
            report.streak = getCurrentStreak();
            report.completionDays = getCompletionDayCount(7);
        } catch (Exception e) {
            Log.e(TAG, "Weekly report failed", e);
        }
        return report;
    }

    /** Weekly report data class. */
    public static class WeeklyTaskReport {
        public int generated;
        public int completed;
        public int skipped;
        public int expired;
        public float completionRate;
        public float avgEffectiveness;
        public String bestCategory;
        public String worstCategory;
        public String mostSkipped;
        public int streak;
        public int completionDays;

        @NonNull
        public String generateNarrative() {
            if (generated == 0) return "No tasks were generated this week.";
            StringBuilder sb = new StringBuilder();

            sb.append("This week: ").append(completed).append("/").append(generated)
                    .append(" tasks completed (").append(String.format("%.0f%%", completionRate * 100)).append("). ");

            if (streak >= 3) {
                sb.append("🔥 ").append(streak).append("-day streak! ");
            }

            if (bestCategory != null && avgEffectiveness > 3f) {
                sb.append("Your most effective category: ").append(bestCategory).append(". ");
            }

            if (mostSkipped != null && skipped > completed) {
                sb.append("You tend to skip ").append(mostSkipped).append(" tasks — consider trying easier ones. ");
            }

            if (completionRate > 0.7f) {
                sb.append("Strong engagement — keep it up!");
            } else if (completionRate > 0.4f) {
                sb.append("Moderate engagement. Small wins compound.");
            } else if (generated > 0) {
                sb.append("Low completion rate. Try starting with just one task per day.");
            }

            return sb.toString();
        }
    }
}
