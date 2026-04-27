package com.mindtrace.ai.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.ai.FulfilmentEngine;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.database.entity.WellnessSummary;
import com.mindtrace.ai.repository.BaselineManager;
import com.mindtrace.ai.repository.SettingsRepository;
import com.mindtrace.ai.repository.UsageRepository;

import java.util.Calendar;
import java.util.List;

/**
 * MidnightSummaryWorker — runs once daily around midnight to finalize the
 * previous day's data and prepare the system for the new day.
 *
 * <h3>Responsibilities:</h3>
 * <ol>
 *   <li><b>Final Usage Snapshot:</b> Runs the usage analysis pipeline one last
 *       time for the day that just ended, ensuring complete data capture.</li>
 *   <li><b>ScreenEventReceiver Reset:</b> Archives screen event counters
 *       (unlocks, first-unlock, last-screen-off) into the DailyUsage entity,
 *       then resets SharedPreferences for the new day.</li>
 *   <li><b>WellnessSummary Finalization:</b> Creates or updates the day's
 *       WellnessSummary with final fulfilment scores and risk levels.</li>
 *   <li><b>Baseline Recomputation:</b> Triggers a lazy baseline recompute
 *       (only if outdated) so the AI pipeline has fresh baselines for the
 *       morning classification runs.</li>
 *   <li><b>Anomaly Flag:</b> Checks if the completed day was anomalous and
 *       logs a deviation summary for trend analysis.</li>
 * </ol>
 *
 * <h3>Scheduling:</h3>
 * <p>WorkScheduler applies an initial delay of {@code millisUntilMidnight()}
 * to align this worker's first run close to 00:05. Subsequent runs repeat
 * every 24 hours.</p>
 *
 * @see com.mindtrace.ai.service.WorkScheduler#WORK_MIDNIGHT_SUMMARY
 * @see com.mindtrace.ai.repository.BaselineManager
 */
public class MidnightSummaryWorker extends Worker {

    private static final String TAG = "MidnightSummary";

    public MidnightSummaryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting midnight summary for previous day...");

        try {
            Context ctx = getApplicationContext();
            AppDatabase db = AppDatabase.getInstance(ctx);
            SettingsRepository.SettingsState settings =
                    new SettingsRepository(ctx).getSettingsState();

            // ── 1. Determine the day that just ended ──
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();

            // The "previous day" is yesterday — shift back 1 day
            cal.add(Calendar.DAY_OF_YEAR, -1);
            long yesterdayStart = cal.getTimeInMillis();
            long yesterdayEnd = todayStart; // midnight boundary

            // ── 2. Final usage snapshot for yesterday ──
            if (settings.trackingEnabled && settings.hasUsagePermission) {
                UsageRepository repository = new UsageRepository(ctx);
                repository.buildAndSaveDailyUsageSnapshot(ctx, settings.includeSystemApps);
                Log.d(TAG, "Final usage snapshot persisted");
            }

            // ── 3. Sync screen event data into DailyUsage ──
            // Strategy: Try getTodayData() first (if we fire just before midnight
            // reset). If its date doesn't match yesterday, fall back to
            // getYesterdayData() which reads the prev_* archived counters that
            // ScreenEventReceiver preserves during the midnight rollover.
            try {
                ScreenEventReceiver.ScreenEventData screenData =
                        ScreenEventReceiver.getTodayData(ctx);

                // Fallback: if the current SharedPrefs day already rolled over
                // to today, use the archived yesterday snapshot instead.
                if (!screenData.isValid() || !isSameDay(screenData.date, yesterdayStart)) {
                    ScreenEventReceiver.ScreenEventData yesterdayScreenData =
                            ScreenEventReceiver.getYesterdayData(ctx);
                    if (yesterdayScreenData.isValid()
                            && isSameDay(yesterdayScreenData.date, yesterdayStart)) {
                        screenData = yesterdayScreenData;
                        Log.d(TAG, "Using getYesterdayData() fallback for screen events");
                    }
                }

                DailyUsage yesterdayUsage = db.usageDao().getUsageForDay(yesterdayStart);
                if (yesterdayUsage != null && screenData.isValid()
                        && isSameDay(screenData.date, yesterdayStart)) {
                    yesterdayUsage.unlockCount = screenData.unlockCount;
                    yesterdayUsage.firstUnlockTime = screenData.firstUnlockTime;
                    yesterdayUsage.lastScreenOffTime = screenData.lastScreenOffTime;
                    db.usageDao().update(yesterdayUsage);
                    Log.d(TAG, "Screen events synced: unlocks=" + screenData.unlockCount);
                }
            } catch (Exception screenErr) {
                Log.w(TAG, "Screen event sync skipped: " + screenErr.getMessage());
            }

            // ── 4. Finalize WellnessSummary for yesterday ──
            try {
                finalizeWellnessSummary(ctx, db, yesterdayStart);
            } catch (Exception wellnessErr) {
                Log.w(TAG, "Wellness summary finalization skipped: " + wellnessErr.getMessage());
            }

            // ── 5. Baseline recomputation (lazy — only if outdated) ──
            try {
                BaselineManager baselineManager = new BaselineManager(db);
                UserBaseline baseline = baselineManager.computeUserBaselineIfOutdated();
                Log.d(TAG, "Baseline status: " + (baseline != null ? baseline.baselineStatus : "null"));
            } catch (Exception baselineErr) {
                Log.w(TAG, "Baseline recomputation skipped: " + baselineErr.getMessage());
            }

            // ── 6. Anomaly check for the completed day ──
            try {
                DailyUsage completedDay = db.usageDao().getUsageForDay(yesterdayStart);
                if (completedDay != null) {
                    BaselineManager baselineManager = new BaselineManager(db);
                    boolean anomalous = baselineManager.isAnomalousDay(completedDay);
                    if (anomalous) {
                        String deviation = baselineManager.getDeviationSummary(completedDay);
                        Log.i(TAG, "⚠ Anomalous day detected: " + deviation);
                        // Deviation logged — downstream consumers (DailyReminderWorker,
                        // WeeklyAssessmentWorker) will pick up the anomaly from the
                        // BehaviorSnapshotEntity and UserBaseline z-scores.
                    } else {
                        Log.d(TAG, "Day within normal baseline range");
                    }
                }
            } catch (Exception anomalyErr) {
                Log.w(TAG, "Anomaly check skipped: " + anomalyErr.getMessage());
            }

            Log.d(TAG, "Midnight summary complete");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Midnight summary failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }

    /**
     * Creates or updates the WellnessSummary for a given day using the
     * fulfilment engine and the latest risk classification.
     */
    private void finalizeWellnessSummary(Context ctx, AppDatabase db, long dayStart) {
        long now = System.currentTimeMillis();

        // Generate fulfilment report
        FulfilmentEngine engine = new FulfilmentEngine(ctx);
        FulfilmentEngine.FulfilmentReport report = engine.generateReport();

        // Task completion score
        long weekAgo = now - 7L * 24 * 60 * 60 * 1000;
        int completed = db.taskDao().getCompletedCountSince(weekAgo);
        int total = db.taskDao().getTotalCountSince(weekAgo);
        int taskScore = total > 0 ? (completed * 100 / total) : 0;

        // Latest risk
        String riskLevel = "unknown";
        String wellnessState = "stable";
        try {
            com.mindtrace.ai.database.entity.RiskClassification latestRisk =
                    db.riskClassificationDao().getLatestSync();
            if (latestRisk != null) {
                riskLevel = latestRisk.getOverallSeverity().name().toLowerCase();
                if (latestRisk.crisisFlag) {
                    wellnessState = "crisis";
                } else if ("HIGH".equalsIgnoreCase(latestRisk.getOverallSeverity().name())) {
                    wellnessState = "struggling";
                } else if ("MODERATE".equalsIgnoreCase(latestRisk.getOverallSeverity().name())) {
                    wellnessState = "coping";
                } else {
                    wellnessState = "thriving";
                }
            }
        } catch (Exception ignored) {}

        // Create/update
        WellnessSummary summary = new WellnessSummary();
        summary.dayTimestamp = dayStart;
        summary.createdAt = now;
        summary.wellnessState = wellnessState;
        summary.riskLevel = riskLevel;
        summary.taskCompletionScore = taskScore;
        summary.explanationText = report.narrative;
        summary.nextBestAction = report.insights.isEmpty() ? "" : report.insights.get(0);

        WellnessSummary existing = db.wellnessSummaryDao().getForDay(dayStart);
        if (existing != null) {
            summary.id = existing.id;
        }
        db.wellnessSummaryDao().insert(summary);

        Log.d(TAG, "WellnessSummary finalized: state=" + wellnessState +
                ", fulfilment=" + report.compositeScore + ", tasks=" + taskScore + "%");
    }

    private boolean isSameDay(long ts1, long ts2) {
        if (ts1 <= 0 || ts2 <= 0) return false;
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(ts1);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(ts2);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
