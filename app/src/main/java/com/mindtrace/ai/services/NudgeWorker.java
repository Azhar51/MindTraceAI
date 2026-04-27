package com.mindtrace.ai.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.BehavioralNudgeEngine;
import com.mindtrace.ai.ai.StreakRecoveryManager;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.repository.UsageRepository;
import com.mindtrace.ai.ui.MainActivity;

/**
 * Background worker for Just-in-Time behavioral nudges.
 *
 * <p>Runs every 30 minutes. On each execution:</p>
 * <ol>
 *   <li>Refreshes the current {@link BehaviorReport} from usage data</li>
 *   <li>Loads the user's 7-day {@link UserBaseline}</li>
 *   <li>Feeds both into {@link BehavioralNudgeEngine#assess}</li>
 *   <li>If a nudge is warranted, delivers a notification via the
 *       {@code mindtrace_nudge} channel</li>
 * </ol>
 *
 * <h3>Notification Channel:</h3>
 * Uses {@code CHANNEL_ID_NUDGE} ("mindtrace_nudge"), registered in
 * {@link com.mindtrace.ai.MindTraceApp#createNotificationChannels()}.
 *
 * <h3>Frequency:</h3>
 * 30 minutes (WorkManager minimum for periodic work is 15 min).
 * Internal cooldown in {@link BehavioralNudgeEngine} provides additional
 * throttling (45 min between nudges, max 3/day).
 *
 * @see BehavioralNudgeEngine
 * @see com.mindtrace.ai.service.WorkScheduler
 */
public class NudgeWorker extends Worker {

    private static final String TAG = "NudgeWorker";

    /** Notification channel ID — must match MindTraceApp registration. */
    public static final String CHANNEL_ID_NUDGE = "mindtrace_nudge";
    public static final String CHANNEL_NAME_NUDGE = "Behavioral Nudges";

    private static final int NOTIFICATION_ID_NUDGE = 6001;

    public NudgeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(TAG, "NudgeWorker executing...");
            Context ctx = getApplicationContext();

            // ── 1. Get fresh behavior report ──
            UsageRepository repo = new UsageRepository(ctx);
            long todayScreenTime = repo.getTodayScreenTime(ctx);
            BehaviorReport report = repo.getTodayBehavior(ctx);

            if (report == null || !report.dataAvailable) {
                Log.d(TAG, "No behavior data available, skipping nudge check");
                return Result.success();
            }

            // ── 2. Load baseline ──
            AppDatabase db = AppDatabase.getInstance(ctx);
            UserBaseline baseline = db.userBaselineDao().getBaselineSync();

            // ── 2.5. Crisis-aware suppression ──
            // Don't fire nudges during active grace periods — the user is
            // recovering from a crisis and doesn't need behavioral pressure.
            StreakRecoveryManager recoveryManager = new StreakRecoveryManager();
            StreakRecoveryManager.RecoveryState recoveryState =
                    recoveryManager.getRecoveryState(ctx);

            if (recoveryState.graceActive) {
                Log.d(TAG, "Crisis grace period active — suppressing all nudges");
                return Result.success();
            }

            // ── 3. Run nudge engine ──
            BehavioralNudgeEngine engine = new BehavioralNudgeEngine();
            BehavioralNudgeEngine.NudgeResult nudge = engine.assess(
                    ctx, report, baseline, todayScreenTime);

            if (nudge == null) {
                Log.d(TAG, "No nudge warranted at this time");
                return Result.success();
            }

            // ── 4. Deliver notification ──
            deliverNudgeNotification(ctx, nudge);
            engine.recordNudgeDelivered(ctx);

            Log.d(TAG, "Nudge delivered: " + nudge);
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "NudgeWorker failed", e);
            return Result.success(); // Don't retry — nudges are non-critical
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NOTIFICATION DELIVERY
    // ═══════════════════════════════════════════════════════════════════

    private void deliverNudgeNotification(@NonNull Context ctx,
                                           @NonNull BehavioralNudgeEngine.NudgeResult nudge) {
        // Create channel if needed (idempotent on API 26+)
        createNudgeChannel(ctx);

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // Direct the user to the appropriate section based on action type
        intent.putExtra("nudge_action", nudge.actionType);
        intent.putExtra("nudge_pattern", nudge.patternId);

        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 60, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Priority scales with severity
        int priority;
        switch (nudge.severity) {
            case 3:  priority = NotificationCompat.PRIORITY_HIGH; break;
            case 2:  priority = NotificationCompat.PRIORITY_DEFAULT; break;
            default: priority = NotificationCompat.PRIORITY_LOW; break;
        }

        // Action duration suffix
        String actionSuffix = " · " + nudge.actionDuration;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID_NUDGE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(nudge.title)
                .setContentText(nudge.body)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(nudge.body + actionSuffix))
                .setPriority(priority)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(0, "Try Now", pendingIntent);

        // High-severity nudges get a "Get Support" action
        if (nudge.severity >= 3) {
            Intent supportIntent = new Intent(ctx, com.mindtrace.ai.ui.CrisisActivity.class);
            supportIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent supportPending = PendingIntent.getActivity(ctx, 61, supportIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(0, "Get Support", supportPending);
        }

        NotificationManager manager = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_NUDGE, builder.build());
        }
    }

    private void createNudgeChannel(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    CHANNEL_ID_NUDGE, CHANNEL_NAME_NUDGE,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Proactive micro-nudges based on behavioral patterns");
            NotificationManager manager = ctx.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
