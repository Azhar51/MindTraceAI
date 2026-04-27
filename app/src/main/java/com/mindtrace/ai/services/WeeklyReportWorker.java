package com.mindtrace.ai.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.MindTraceApp;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.FulfilmentEngine;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.ui.MainActivity;

import java.util.Calendar;

/**
 * Weekly Report Worker — generates and sends a weekly wellness summary
 * notification every Sunday evening.
 *
 * <h3>Report Contents:</h3>
 * <ul>
 *   <li>Composite fulfilment score and trend</li>
 *   <li>Tasks completed vs. skipped</li>
 *   <li>Crisis events count</li>
 *   <li>Purpose alignment change</li>
 * </ul>
 */
public class WeeklyReportWorker extends Worker {

    private static final String TAG = "WeeklyReportWorker";
    private static final int NOTIFICATION_ID = 4001;
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    public WeeklyReportWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Running weekly report generation...");

        try {
            Context ctx = getApplicationContext();
            AppDatabase db = AppDatabase.getInstance(ctx);
            long weekAgo = System.currentTimeMillis() - WEEK_MS;

            // Generate fulfilment report
            FulfilmentEngine engine = new FulfilmentEngine(ctx);
            FulfilmentEngine.FulfilmentReport report = engine.generateReport();

            // Collect stats
            int tasksCompleted = db.taskDao().getCompletedCountSince(weekAgo);
            int tasksSkipped = db.taskDao().getSkippedCountSince(weekAgo);
            int crisisCount = 0;
            try {
                crisisCount = db.crisisEventDao().getCountSince(weekAgo);
            } catch (Exception ignored) {}

            // Build summary text
            StringBuilder summary = new StringBuilder();
            summary.append("📊 Fulfilment: ").append(report.compositeScore).append("%");

            if (tasksCompleted > 0 || tasksSkipped > 0) {
                summary.append(" • ✅ ").append(tasksCompleted).append(" tasks done");
                if (tasksSkipped > 0) {
                    summary.append(", ").append(tasksSkipped).append(" skipped");
                }
            }

            if (crisisCount > 0) {
                summary.append(" • ⚠️ ").append(crisisCount).append(" crisis event")
                        .append(crisisCount > 1 ? "s" : "");
            }

            // Build title based on score
            String title;
            if (report.compositeScore >= 75) {
                title = "🌟 Great week! Your wellness report is ready";
            } else if (report.compositeScore >= 50) {
                title = "📊 Your weekly wellness report is ready";
            } else {
                title = "💙 Your weekly report — let's find what helps";
            }

            // Build notification
            Intent intent = new Intent(ctx, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_START_DESTINATION, MainActivity.DEST_INSIGHTS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, NOTIFICATION_ID,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, MindTraceApp.CHANNEL_WEEKLY_REPORT)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(summary.toString())
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(summary.toString() + "\n\n" + report.narrative))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .addAction(0, "📈 View Report", pi);

            try {
                NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, builder.build());
                Log.d(TAG, "Weekly report sent: score=" + report.compositeScore);
            } catch (SecurityException e) {
                Log.w(TAG, "Notification permission not granted", e);
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Weekly report generation failed", e);
            return Result.success(); // Don't retry, wait for next week
        }
    }
}
