package com.mindtrace.ai.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mindtrace.ai.ai.InterventionEngine;
import com.mindtrace.ai.ai.StreakRecoveryManager;
import com.mindtrace.ai.ai.TaskLifecycleManager;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.UserProgressDao;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.UserProgress;
import com.mindtrace.ai.repository.TaskRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * ViewModel for the Tasks screen — exposes active/completed tasks, streak data,
 * XP/level progress, effectiveness metrics, and task lifecycle operations.
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Active/completed task LiveData</li>
 *   <li>Category-filtered views</li>
 *   <li>Task lifecycle (complete, skip, snooze, expire)</li>
 *   <li>Gamification (XP, streaks, badges) via UserProgress</li>
 *   <li>Micro-intervention generation for crisis moments</li>
 *   <li>Weekly report and effectiveness analytics</li>
 * </ul>
 */
public class TaskViewModel extends AndroidViewModel {

    private static final String TAG = "TaskViewModel";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final TaskRepository taskRepository;
    private final UserProgressDao progressDao;
    private final InterventionEngine interventionEngine;
    private final TaskLifecycleManager lifecycleManager;

    // ── LiveData ──
    private final LiveData<List<InterventionTask>> activeTasks;
    private final LiveData<List<InterventionTask>> completedTasks;
    private final LiveData<UserProgress> userProgress;
    private final MutableLiveData<String> categoryFilter = new MutableLiveData<>(null);

    public TaskViewModel(@NonNull Application application) {
        super(application);

        taskRepository = new TaskRepository(application);
        progressDao = AppDatabase.getInstance(application).userProgressDao();
        interventionEngine = new InterventionEngine();
        lifecycleManager = new TaskLifecycleManager(application);

        activeTasks = taskRepository.getActiveTasks();
        completedTasks = taskRepository.getCompletedTasks();
        userProgress = progressDao.getProgressLive();

        // Run lifecycle maintenance on init
        executor.execute(lifecycleManager::runMaintenance);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TASK QUERIES
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    public LiveData<List<InterventionTask>> getActiveTasks() {
        return activeTasks;
    }

    @NonNull
    public LiveData<List<InterventionTask>> getCompletedTasks() {
        return completedTasks;
    }

    @NonNull
    public LiveData<List<InterventionTask>> getActiveTasksByCategory(@NonNull String category) {
        return taskRepository.getActiveTasksByCategory(category);
    }

    @NonNull
    public LiveData<UserProgress> getUserProgress() {
        return userProgress;
    }

    @NonNull
    public MutableLiveData<String> getCategoryFilter() {
        return categoryFilter;
    }

    public void setCategoryFilter(@Nullable String category) {
        categoryFilter.setValue(category);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TASK LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /** Mark task as completed + update gamification. */
    public void markTaskCompleted(@NonNull InterventionTask task) {
        executor.execute(() -> {
            task.markCompleted();
            taskRepository.update(task);

            // Update gamification
            UserProgress progress = getOrCreateProgress();
            progress.recordCompletion();
            progress.addXp(task.getEffectiveXp());
            if (task.isMicroIntervention) {
                progress.totalCrisisTasksCompleted++;
            }

            // Check for new badges
            String[] newBadges = progress.checkNewBadges();
            if (newBadges.length > 0) {
                progress.unlockBadges(newBadges);
                Log.d(TAG, "New badges unlocked: " + String.join(", ", newBadges));
            }

            progressDao.insertOrUpdate(progress);

            // ── Recovery phase transition ──
            // If the user is in a recovery phase (post-crisis), completing
            // any task advances the recovery pipeline:
            //   gentle_return → recovery_streak (first activity)
            //   recovery_streak → normal (after 3 consecutive days)
            try {
                StreakRecoveryManager recoveryManager = new StreakRecoveryManager();
                StreakRecoveryManager.RecoveryState state =
                        recoveryManager.getRecoveryState(getApplication());

                if ("gentle_return".equals(state.phase)) {
                    recoveryManager.markFirstActivityCompleted(getApplication());
                    Log.d(TAG, "Recovery: gentle_return → recovery_streak");
                } else if ("recovery_streak".equals(state.phase)) {
                    // Check if 3+ consecutive days of activity since recovery
                    int recentStreak = taskRepository.getCurrentStreak();
                    if (recentStreak >= 3) {
                        recoveryManager.completeRecovery(getApplication());
                        Log.d(TAG, "Recovery complete: returning to normal streak tracking");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Recovery phase check failed (non-critical)", e);
            }
        });
    }

    /** Mark task as skipped with tracking. */
    public void skipTask(@NonNull InterventionTask task) {
        executor.execute(() -> {
            task.markSkipped();
            taskRepository.update(task);

            UserProgress progress = getOrCreateProgress();
            progress.totalTasksSkipped++;
            progressDao.insertOrUpdate(progress);
        });
    }

    /** Snooze a task for the given duration in milliseconds. */
    public void snoozeTask(@NonNull InterventionTask task, long durationMs) {
        executor.execute(() -> {
            task.snooze(durationMs);
            taskRepository.update(task);
        });
    }

    /** Rate a completed task's effectiveness. */
    public void rateEffectiveness(@NonNull InterventionTask task, int rating,
                                   @Nullable String postMood) {
        executor.execute(() -> {
            task.effectivenessRating = rating;
            if (postMood != null) task.postCompletionMood = postMood;
            taskRepository.update(task);
        });
    }

    /** Set pre-completion mood before starting a task. */
    public void setPreCompletionMood(@NonNull InterventionTask task, @NonNull String mood) {
        executor.execute(() -> {
            task.preCompletionMood = mood;
            taskRepository.update(task);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // MICRO-INTERVENTION
    // ═══════════════════════════════════════════════════════════════════

    /** Generate and insert a micro-intervention for immediate crisis support. */
    public void requestMicroIntervention(@Nullable String riskCategory) {
        executor.execute(() -> {
            InterventionTask micro = interventionEngine.generateMicroIntervention(riskCategory);
            if (micro != null) {
                taskRepository.insertIfNotDuplicate(micro);
                Log.d(TAG, "Micro-intervention generated: " + micro.title);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANALYTICS (background execution with callback)
    // ═══════════════════════════════════════════════════════════════════

    /** Load completion rate asynchronously. */
    public void loadCompletionRate(int lastNDays, @NonNull Consumer<Float> callback) {
        executor.execute(() -> {
            float rate = taskRepository.getCompletionRate(lastNDays);
            callback.accept(rate);
        });
    }

    /** Load current streak asynchronously. */
    public void loadStreak(@NonNull Consumer<Integer> callback) {
        executor.execute(() -> {
            int streak = taskRepository.getCurrentStreak();
            callback.accept(streak);
        });
    }

    /** Load skip-to-complete ratio. */
    public void loadSkipRatio(int lastNDays, @NonNull Consumer<Float> callback) {
        executor.execute(() -> {
            float ratio = taskRepository.getSkipToCompleteRatio(lastNDays);
            callback.accept(ratio);
        });
    }

    /** Load weekly task report. */
    public void loadWeeklyReport(@NonNull Consumer<TaskRepository.WeeklyTaskReport> callback) {
        executor.execute(() -> {
            TaskRepository.WeeklyTaskReport report = taskRepository.getWeeklyReport();
            callback.accept(report);
        });
    }

    /** Load most effective category. */
    public void loadMostEffectiveCategory(@NonNull Consumer<String> callback) {
        executor.execute(() -> {
            String cat = taskRepository.getMostEffectiveCategory();
            callback.accept(cat != null ? cat : "None yet");
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE MAINTENANCE
    // ═══════════════════════════════════════════════════════════════════

    /** Run lifecycle maintenance (expire, reactivate, cleanup). */
    public void runMaintenance() {
        executor.execute(lifecycleManager::runMaintenance);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private UserProgress getOrCreateProgress() {
        UserProgress progress = progressDao.getProgress();
        if (progress == null) {
            progress = new UserProgress();
            progressDao.insertOrUpdate(progress);
        }
        return progress;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
