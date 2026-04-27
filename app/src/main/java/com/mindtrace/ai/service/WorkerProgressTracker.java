package com.mindtrace.ai.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time progress tracker for all WorkManager background workers.
 *
 * <p>Surfaces "Analyzing...", "Syncing...", "Classifying..." status messages
 * to the UI by observing {@code WorkManager.getWorkInfosForUniqueWorkLiveData()}
 * for each registered worker.</p>
 *
 * <h3>Architecture:</h3>
 * <pre>
 *   WorkManager → WorkInfo LiveData → WorkerProgressTracker
 *                                       ├─ activeWorkers (list of running labels)
 *                                       ├─ progressSummary (human-readable string)
 *                                       └─ isAnyWorkerRunning (boolean)
 * </pre>
 *
 * <h3>Usage in DashboardViewModel:</h3>
 * <pre>
 *   WorkerProgressTracker tracker = new WorkerProgressTracker(application);
 *   LiveData&lt;String&gt; status = tracker.getProgressSummary();
 * </pre>
 *
 * @see WorkScheduler
 * @see com.mindtrace.ai.viewmodel.DashboardViewModel
 */
public class WorkerProgressTracker {

    /** Human-readable label for each worker, shown in UI status text. */
    private static final Map<String, String> WORKER_LABELS;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(WorkScheduler.WORK_CLASSIFICATION,       "Classifying risk");
        map.put(WorkScheduler.WORK_USAGE_SYNC,           "Syncing usage data");
        map.put(WorkScheduler.WORK_USAGE_SNAPSHOT,        "Refreshing usage");
        map.put(WorkScheduler.WORK_MIDNIGHT_SUMMARY,      "Finalizing daily summary");
        map.put(WorkScheduler.WORK_BASELINE_COMPUTE,      "Recomputing baselines");
        map.put(WorkScheduler.WORK_TASK_REMINDER,         "Checking tasks");
        map.put(WorkScheduler.WORK_CRISIS_FOLLOW_UP,      "Crisis check-in");
        map.put(WorkScheduler.WORK_EFFICACY_OBSERVATION,  "Measuring efficacy");
        map.put(WorkScheduler.WORK_WELLNESS_SYNC,         "Syncing wellness");
        map.put(WorkScheduler.WORK_DATA_CLEANUP,          "Cleaning up data");
        map.put(WorkScheduler.WORK_DAILY_REMINDER,        "Preparing reminders");
        map.put(WorkScheduler.WORK_WEEKLY_REPORT,         "Building weekly report");
        map.put(WorkScheduler.WORK_WEEKLY_ASSESSMENT,     "Running assessment");
        WORKER_LABELS = Collections.unmodifiableMap(map);
    }

    // Per-worker latest state, updated by MediatorLiveData observers
    private final ConcurrentHashMap<String, WorkInfo.State> latestStates =
            new ConcurrentHashMap<>();

    private final MediatorLiveData<List<WorkerStatus>> activeWorkers = new MediatorLiveData<>();
    private final LiveData<String> progressSummary;
    private final LiveData<Boolean> isAnyRunning;

    /**
     * Snapshot of a single worker's current status.
     */
    public static class WorkerStatus {
        @NonNull public final String workName;
        @NonNull public final String label;
        @NonNull public final WorkInfo.State state;

        public WorkerStatus(@NonNull String workName, @NonNull String label,
                            @NonNull WorkInfo.State state) {
            this.workName = workName;
            this.label = label;
            this.state = state;
        }

        /** True if RUNNING or ENQUEUED. */
        public boolean isActive() {
            return state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED;
        }

        /** True if actively executing right now. */
        public boolean isRunning() {
            return state == WorkInfo.State.RUNNING;
        }
    }

    /**
     * Create a new progress tracker. Call from ViewModel/Activity.
     *
     * @param context application context
     */
    public WorkerProgressTracker(@NonNull Context context) {
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        activeWorkers.setValue(new ArrayList<>());

        // Observe each registered worker and merge into the unified list
        for (Map.Entry<String, String> entry : WORKER_LABELS.entrySet()) {
            String workName = entry.getKey();

            LiveData<List<WorkInfo>> workInfos =
                    wm.getWorkInfosForUniqueWorkLiveData(workName);

            activeWorkers.addSource(workInfos, infos -> {
                // Update the per-worker state cache
                WorkInfo.State state = extractBestState(infos);
                if (state != null) {
                    latestStates.put(workName, state);
                } else {
                    latestStates.remove(workName);
                }
                // Rebuild the aggregated list
                rebuildActiveList();
            });
        }

        // Derived LiveData: human-readable summary string
        progressSummary = Transformations.map(activeWorkers, workers -> {
            if (workers == null || workers.isEmpty()) return null;

            List<String> running = new ArrayList<>();
            for (WorkerStatus ws : workers) {
                if (ws.isRunning()) {
                    running.add(ws.label);
                }
            }

            if (running.isEmpty()) return null;
            if (running.size() == 1) return running.get(0) + "...";
            return running.get(0) + " + " + (running.size() - 1) + " more...";
        });

        // Derived LiveData: boolean "is anything running right now?"
        isAnyRunning = Transformations.map(activeWorkers, workers -> {
            if (workers == null) return false;
            for (WorkerStatus ws : workers) {
                if (ws.isRunning()) return true;
            }
            return false;
        });
    }

    /**
     * Extract the "most interesting" state from a WorkInfo list.
     * Priority: RUNNING > ENQUEUED > BLOCKED > others.
     */
    @Nullable
    private WorkInfo.State extractBestState(@Nullable List<WorkInfo> infos) {
        if (infos == null || infos.isEmpty()) return null;

        WorkInfo.State best = null;
        for (WorkInfo info : infos) {
            WorkInfo.State s = info.getState();
            if (s == WorkInfo.State.RUNNING) return s;  // Highest priority
            if (s == WorkInfo.State.ENQUEUED) best = s;
            else if (best == null) best = s;
        }
        return best;
    }

    /**
     * Rebuild the active workers list from the per-worker state cache.
     * Called internally whenever any WorkInfo LiveData emits.
     */
    private void rebuildActiveList() {
        List<WorkerStatus> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : WORKER_LABELS.entrySet()) {
            String workName = entry.getKey();
            String label = entry.getValue();
            WorkInfo.State state = latestStates.get(workName);
            if (state != null) {
                result.add(new WorkerStatus(workName, label, state));
            }
        }
        activeWorkers.setValue(result);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Observable list of all workers with their current states.
     * UI can iterate to show per-worker status indicators.
     */
    @NonNull
    public LiveData<List<WorkerStatus>> getActiveWorkers() {
        return activeWorkers;
    }

    /**
     * Human-readable progress string, e.g. "Classifying risk...",
     * "Syncing usage data + 2 more...", or null if idle.
     */
    @NonNull
    public LiveData<String> getProgressSummary() {
        return progressSummary;
    }

    /**
     * True if any worker is currently in RUNNING state.
     * Useful for showing/hiding a global progress indicator.
     */
    @NonNull
    public LiveData<Boolean> getIsAnyRunning() {
        return isAnyRunning;
    }

    /**
     * Get the label map for UI display purposes (e.g. settings screen).
     */
    @NonNull
    public static Map<String, String> getWorkerLabels() {
        return WORKER_LABELS;
    }
}
