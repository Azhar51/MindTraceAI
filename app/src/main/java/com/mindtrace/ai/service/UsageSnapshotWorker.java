package com.mindtrace.ai.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.repository.SettingsRepository;
import com.mindtrace.ai.repository.UsageRepository;

/**
 * UsageSnapshotWorker — periodic (every 4 hours) usage data refresh.
 *
 * <p>This worker runs the single-pass {@link UsageRepository#refreshTodayUsageSnapshot}
 * pipeline which queries {@code UsageStatsManager}, rebuilds app snapshots, sessions,
 * behavior summaries, and persists them to Room. It is a lighter counterpart to the
 * full {@code UsageWorker} (24h) because it does NOT trigger AI analysis or task
 * generation — it only ensures the database stays reasonably fresh between full
 * sync cycles.</p>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   UsageStatsManager ─► UsageIntelligenceEngine.analyzeDay()
 *     ├─► DailyUsage        (upsert)
 *     ├─► AppUsageSnapshot[] (replace-for-day)
 *     ├─► UsageSession[]     (replace-for-day)
 *     └─► BehaviorUsageSummary (upsert)
 * </pre>
 *
 * @see com.mindtrace.ai.service.WorkScheduler#WORK_USAGE_SNAPSHOT
 */
public class UsageSnapshotWorker extends Worker {

    private static final String TAG = "UsageSnapshotWorker";

    public UsageSnapshotWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting periodic usage snapshot refresh...");

        try {
            SettingsRepository.SettingsState settings =
                    new SettingsRepository(getApplicationContext()).getSettingsState();

            if (!settings.trackingEnabled || !settings.hasUsagePermission) {
                Log.d(TAG, "Tracking disabled or no permission — skipping");
                return Result.success();
            }

            if (!settings.backgroundSnapshots) {
                Log.d(TAG, "Background snapshots disabled — skipping");
                return Result.success();
            }

            UsageRepository repository = new UsageRepository(getApplicationContext());

            // Single-pass: refreshes DailyUsage, AppUsageSnapshot, UsageSession,
            // BehaviorUsageSummary, and BehaviorSnapshotEntity.
            UsageRepository.DashboardSnapshot snapshot =
                    repository.refreshTodayUsageSnapshot(
                            getApplicationContext(),
                            settings.includeSystemApps
                    );

            long screenTime = snapshot != null ? snapshot.screenTimeMillis : 0;
            Log.d(TAG, "Snapshot refresh complete: screenTime=" +
                    (screenTime / 60000) + " min, apps=" +
                    (snapshot != null && snapshot.allApps != null ? snapshot.allApps.size() : 0));

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Usage snapshot refresh failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }
}
