package com.mindtrace.ai.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.repository.BaselineManager;

/**
 * BaselineComputeWorker — weekly (every 7 days) forced baseline recomputation.
 *
 * <p>While the {@link BaselineManager} performs lazy recomputation (via
 * {@code computeUserBaselineIfOutdated()}) during daily flows, this worker
 * ensures a <b>guaranteed full recomputation</b> at least once per week.
 * This catches edge cases where the user hasn't opened the app for several
 * days and the baseline has become stale.</p>
 *
 * <h3>What Gets Recomputed:</h3>
 * <ul>
 *   <li><b>7-day rolling averages:</b> screen time, sleep, stress, mood,
 *       task completion, app switches, night usage, unlocks, launches,
 *       passive consumption ratio</li>
 *   <li><b>Standard deviations:</b> for z-score anomaly detection</li>
 *   <li><b>30-day screen time average:</b> for long-term trend detection</li>
 *   <li><b>Baseline status lifecycle:</b> INSUFFICIENT → CALIBRATING → READY</li>
 * </ul>
 *
 * <h3>Constraints:</h3>
 * <ul>
 *   <li>{@code requiresBatteryNotLow} — baseline computation queries 30 days
 *       of data and shouldn't drain a low battery</li>
 *   <li>{@code requiresDeviceIdle} — runs during idle windows to avoid
 *       competing with user-facing operations</li>
 * </ul>
 *
 * @see com.mindtrace.ai.repository.BaselineManager#computeUserBaseline()
 * @see com.mindtrace.ai.database.entity.UserBaseline
 * @see com.mindtrace.ai.service.WorkScheduler#WORK_BASELINE_COMPUTE
 */
public class BaselineComputeWorker extends Worker {

    private static final String TAG = "BaselineComputeWorker";

    public BaselineComputeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting weekly baseline recomputation...");

        try {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            BaselineManager baselineManager = new BaselineManager(db);

            // Force full recomputation (not the lazy "if outdated" variant)
            UserBaseline baseline = baselineManager.computeUserBaseline();

            if (baseline == null) {
                Log.w(TAG, "Baseline computation returned null — insufficient data");
                return Result.success();
            }

            Log.d(TAG, "Baseline recomputed successfully: " + baseline.toString());
            Log.d(TAG, "  Status: " + baseline.baselineStatus +
                    " (" + baseline.dataPointCount + " data points)");
            Log.d(TAG, "  Avg screen time (7d): " +
                    String.format("%.1f h", baseline.getAvgScreenTimeHours()));
            Log.d(TAG, "  Avg unlocks (7d): " +
                    String.format("%.0f", baseline.avgUnlocks7d));
            Log.d(TAG, "  Avg app switches (7d): " +
                    String.format("%.0f", baseline.avgAppSwitches7d));
            Log.d(TAG, "  Passive ratio (7d): " +
                    String.format("%.1f%%", baseline.avgPassiveRatio7d * 100));

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Baseline computation failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }
}
