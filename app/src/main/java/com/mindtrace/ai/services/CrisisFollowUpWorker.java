package com.mindtrace.ai.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.service.WorkerErrorHandler;

import com.mindtrace.ai.ai.PostCrisisDebrief;
import com.mindtrace.ai.database.entity.CrisisEvent;
import com.mindtrace.ai.repository.CrisisRepository;
import com.mindtrace.ai.service.WorkScheduler;

/**
 * WorkManager worker for crisis follow-ups and debrief scheduling.
 *
 * <p>Runs periodically (every 1 hour) and checks:</p>
 * <ol>
 *   <li>Any unresolved crisis events that need follow-up</li>
 *   <li>Any resolved events that need a debrief (4-6h after resolution)</li>
 * </ol>
 */
public class CrisisFollowUpWorker extends Worker {

    private static final String TAG = "CrisisFollowUpWorker";

    public CrisisFollowUpWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "CrisisFollowUpWorker started");
        Context ctx = getApplicationContext();

        try {
            CrisisRepository repo = new CrisisRepository(ctx);

            // 1. Check for unresolved crises and send tiered follow-up
            CrisisEvent active = repo.getActiveEvent();

            // Adaptive interval: scale frequency by C-SSRS severity tier
            adjustWorkerFrequency(ctx, active);

            if (active != null) {
                long elapsed = System.currentTimeMillis() - active.timestamp;
                long elapsedMinutes = elapsed / (60 * 1000);

                // Determine follow-up cadence from C-SSRS tier
                int followUpIntervalMin = active.getRecommendedFollowUpMinutes();
                int urgencyLevel = computeFollowUpUrgency(active, elapsedMinutes, followUpIntervalMin);

                if (urgencyLevel > 0) {
                    CrisisNotificationManager.sendFollowUp(ctx, urgencyLevel);

                    // Mark scheduled on first high-urgency follow-up
                    if (urgencyLevel >= 3 && !active.followUpScheduled) {
                        active.followUpScheduled = true;
                        repo.update(active);
                    }

                    Log.d(TAG, String.format(
                            "Follow-up sent: urgency=%d, csrrsTier=%d, elapsed=%dm, interval=%dm",
                            urgencyLevel, active.severityTier, elapsedMinutes, followUpIntervalMin));
                }
            }

            // 2. Check for debrief-pending events
            CrisisEvent debriefEvent = repo.getEventNeedingDebrief();
            if (debriefEvent != null && PostCrisisDebrief.isDebriefDue(debriefEvent)) {
                CrisisNotificationManager.sendDebriefNotification(ctx);
                Log.d(TAG, "Debrief notification sent for event " + debriefEvent.id);
            }

            // 3. Post-crisis 24h safety check (6.B.8)
            sendSafetyCheckIfNeeded(ctx, repo);

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "CrisisFollowUpWorker failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }

    /**
     * Compute the follow-up urgency level based on elapsed time and C-SSRS tier.
     *
     * <p>Higher severity tiers trigger earlier and more frequent follow-ups:</p>
     * <ul>
     *   <li><b>Tier 4-5:</b> First follow-up at 15min, escalation at 45min, max urgency at 90min</li>
     *   <li><b>Tier 2-3:</b> First follow-up at 30min, escalation at 60min, max urgency at 120min</li>
     *   <li><b>Tier 0-1:</b> First follow-up at 30min, escalation at 60min, max urgency at 120min</li>
     * </ul>
     *
     * @return 0 (no follow-up needed), 1 (gentle), 2 (moderate), 3 (urgent)
     */
    private int computeFollowUpUrgency(CrisisEvent event, long elapsedMinutes, int intervalMin) {
        // High-severity (C-SSRS ≥ 4): accelerated cadence
        if (event.severityTier >= 4) {
            if (elapsedMinutes >= intervalMin * 6L) return 3; // 90+ min → max urgency
            if (elapsedMinutes >= intervalMin * 3L) return 3; // 45+ min → urgent
            if (elapsedMinutes >= intervalMin)      return 2; // 15+ min → moderate
            return 0;
        }

        // Medium-severity (C-SSRS 2-3 or CRITICAL/URGENT): standard accelerated
        if (event.requiresIntensiveFollowUp()) {
            if (elapsedMinutes >= 120 && !event.followUpScheduled) return 3;
            if (elapsedMinutes >= 60)  return 2;
            if (elapsedMinutes >= 30)  return 1;
            return 0;
        }

        // Low-severity: standard follow-up cadence
        if (elapsedMinutes >= 120 && !event.followUpScheduled) return 3;
        if (elapsedMinutes >= 60) return 2;
        if (elapsedMinutes >= 30) return 1;
        return 0;
    }

    /**
     * Check for crisis events resolved within the last 24 hours that haven't
     * received a safety check notification yet.
     *
     * <p>Safety checks are sent 12-24h after resolution. For high-severity events
     * (C-SSRS ≥ 3), a second safety check is sent 6-12h after resolution.</p>
     */
    private void sendSafetyCheckIfNeeded(Context ctx, CrisisRepository repo) {
        try {
            long now = System.currentTimeMillis();
            long twentyFourHoursAgo = now - 24L * 60 * 60 * 1000;
            long twelveHoursAgo = now - 12L * 60 * 60 * 1000;
            long sixHoursAgo = now - 6L * 60 * 60 * 1000;

            // Get recently resolved events
            java.util.List<CrisisEvent> recentEvents =
                    com.mindtrace.ai.database.AppDatabase.getInstance(ctx)
                            .crisisEventDao().getEventsSince(twentyFourHoursAgo);

            if (recentEvents == null) return;

            for (CrisisEvent event : recentEvents) {
                if (!event.resolved || event.safetyCheckSent) continue;

                // High-severity: earlier safety check (6-12h post-resolution)
                boolean earlyWindow = event.severityTier >= 3
                        && event.resolvedAt > sixHoursAgo
                        && event.resolvedAt < now - 4L * 60 * 60 * 1000;

                // Standard: 12-24h after resolution
                boolean standardWindow = event.resolvedAt > twelveHoursAgo
                        && event.resolvedAt < sixHoursAgo;

                if (earlyWindow || standardWindow) {
                    sendSafetyCheckNotification(ctx, event.severityTier);
                    event.safetyCheckSent = true;
                    repo.update(event);

                    Log.d(TAG, "Sent safety check for crisis event " + event.id +
                            " (csrrsTier=" + event.severityTier +
                            ", window=" + (earlyWindow ? "early" : "standard") + ")");
                    break; // One at a time
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Safety check evaluation failed", e);
        }
    }

    private void sendSafetyCheckNotification(Context ctx, int severityTier) {
        try {
            android.app.PendingIntent checkInIntent = android.app.PendingIntent.getActivity(
                    ctx, 3001,
                    new android.content.Intent(ctx, com.mindtrace.ai.ui.DailyCheckInActivity.class)
                            .setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

            // Scale notification tone by severity
            String title, body;
            int priority;
            if (severityTier >= 3) {
                title = "💚 We're thinking of you";
                body = "You went through something really difficult recently. " +
                        "How are you feeling right now? Your wellbeing matters deeply to us.";
                priority = androidx.core.app.NotificationCompat.PRIORITY_HIGH;
            } else {
                title = "💚 How are you doing today?";
                body = "Yesterday was a tough day. How are you feeling right now? " +
                        "A quick check-in can help you track your recovery.";
                priority = androidx.core.app.NotificationCompat.PRIORITY_DEFAULT;
            }

            androidx.core.app.NotificationCompat.Builder builder =
                    new androidx.core.app.NotificationCompat.Builder(ctx, com.mindtrace.ai.MindTraceApp.CHANNEL_FOLLOW_UP)
                            .setSmallIcon(com.mindtrace.ai.R.drawable.ic_notification)
                            .setContentTitle(title)
                            .setContentText(body)
                            .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                            .setPriority(priority)
                            .setContentIntent(checkInIntent)
                            .setAutoCancel(true)
                            .addAction(0, "📝 Check In", checkInIntent);

            // High-severity: add crisis support action
            if (severityTier >= 3) {
                android.app.PendingIntent supportIntent = android.app.PendingIntent.getActivity(
                        ctx, 3002,
                        new android.content.Intent(ctx, com.mindtrace.ai.ui.CrisisActivity.class)
                                .setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(0, "🆘 Get Support", supportIntent);
            }

            androidx.core.app.NotificationManagerCompat.from(ctx).notify(3001, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission not granted for safety check", e);
        }
    }

    /**
     * CSSRS-tiered adaptive interval scaling.
     *
     * <p>Replaces the binary 30min/1h toggle with a dynamic interval
     * derived from the active crisis event's C-SSRS tier:</p>
     * <ul>
     *   <li>Tier 4-5 → 15 min (WorkManager minimum)</li>
     *   <li>Tier 2-3 → 30 min</li>
     *   <li>Tier 1 or ELEVATED → 45 min</li>
     *   <li>No crisis → 60 min (de-escalate)</li>
     * </ul>
     */
    private void adjustWorkerFrequency(Context ctx, CrisisEvent activeEvent) {
        try {
            android.content.SharedPreferences prefs = ctx.getSharedPreferences(
                    "mindtrace_worker_state", Context.MODE_PRIVATE);
            int currentIntervalMin = prefs.getInt("crisis_follow_up_interval", 60);

            int targetInterval;
            if (activeEvent != null) {
                targetInterval = activeEvent.getRecommendedFollowUpMinutes();
            } else {
                targetInterval = 60; // No active crisis — standard cadence
            }

            // Only re-enqueue if interval actually changed
            if (targetInterval != currentIntervalMin) {
                // WorkManager minimum is 15 minutes
                int safeInterval = Math.max(15, targetInterval);
                java.util.concurrent.TimeUnit unit = java.util.concurrent.TimeUnit.MINUTES;

                androidx.work.PeriodicWorkRequest newFreq =
                        new androidx.work.PeriodicWorkRequest.Builder(
                                CrisisFollowUpWorker.class, safeInterval, unit)
                                .addTag(WorkScheduler.WORK_CRISIS_FOLLOW_UP)
                                .build();
                androidx.work.WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                        WorkScheduler.WORK_CRISIS_FOLLOW_UP,
                        androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                        newFreq);
                prefs.edit().putInt("crisis_follow_up_interval", targetInterval).apply();

                Log.d(TAG, "Follow-up interval adjusted: " + currentIntervalMin +
                        "min → " + targetInterval + "min" +
                        (activeEvent != null ? " (csrrsTier=" + activeEvent.severityTier + ")" : " (no crisis)"));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to adjust worker frequency", e);
        }
    }
}
