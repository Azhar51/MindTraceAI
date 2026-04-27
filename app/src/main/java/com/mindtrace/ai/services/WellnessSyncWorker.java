package com.mindtrace.ai.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.service.WorkerErrorHandler;

import com.mindtrace.ai.ai.FulfilmentEngine;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.WellnessSummary;

import java.util.Calendar;

/**
 * WellnessSyncWorker — aggregates daily wellness metrics from multiple data sources
 * into a single WellnessSummary entity. Runs every 24 hours.
 *
 * <h3>Data Sources:</h3>
 * <ul>
 *   <li>FulfilmentEngine composite score</li>
 *   <li>Latest risk classification</li>
 *   <li>Task completion stats</li>
 *   <li>Journal sentiment trends</li>
 *   <li>Usage patterns</li>
 * </ul>
 */
public class WellnessSyncWorker extends Worker {

    private static final String TAG = "WellnessSyncWorker";

    public WellnessSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting wellness sync...");

        try {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            long now = System.currentTimeMillis();

            // Use Calendar for local-timezone day boundary (avoids UTC midnight bias)
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long dayStart = cal.getTimeInMillis();

            // Generate fulfilment report
            FulfilmentEngine engine = new FulfilmentEngine(getApplicationContext());
            FulfilmentEngine.FulfilmentReport report = engine.generateReport();

            // Get task stats
            long weekAgo = now - 7L * 24 * 60 * 60 * 1000;
            int completed = db.taskDao().getCompletedCountSince(weekAgo);
            int total = db.taskDao().getTotalCountSince(weekAgo);
            int taskScore = total > 0 ? (completed * 100 / total) : 0;

            // NOTE: Classification logic removed — ClassificationWorker (6h cadence)
            // is the sole owner of the AI classification pipeline. This worker now
            // only reads the latest RiskClassification produced by that worker,
            // avoiding duplicate battery-intensive feature extraction runs.

            // Get latest risk level
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

            // Create/update wellness summary
            WellnessSummary summary = new WellnessSummary();
            summary.dayTimestamp = dayStart;
            summary.createdAt = now;
            summary.wellnessState = wellnessState;
            summary.riskLevel = riskLevel;
            summary.taskCompletionScore = taskScore;
            summary.explanationText = report.narrative;
            summary.nextBestAction = report.insights.isEmpty() ? "" : report.insights.get(0);

            // Check for existing summary for today
            WellnessSummary existing = db.wellnessSummaryDao().getForDay(dayStart);
            if (existing != null) {
                summary.id = existing.id;
            }

            db.wellnessSummaryDao().insert(summary);

            Log.d(TAG, "Wellness sync complete: state=" + wellnessState +
                    ", fulfilment=" + report.compositeScore +
                    ", tasks=" + taskScore + "%");

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Wellness sync failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }
}
