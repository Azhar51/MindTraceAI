package com.mindtrace.ai.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.CrisisDetector;
import com.mindtrace.ai.ai.MultiModalClassifier;
import com.mindtrace.ai.ui.CrisisActivity;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Crisis notification manager — fires high-priority notifications when
 * CrisisLevel >= URGENT, with support actions and cooldown awareness.
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>HIGH-priority notification with vibration for URGENT/CRITICAL</li>
 *   <li>Actions: "Open Support", "I'm OK"</li>
 *   <li>Cooldown: no duplicate notifications within 30 minutes</li>
 *   <li>"Check on me" follow-ups at 30min/1hr/2hr after crisis event</li>
 *   <li>Integrates with ScreenEventReceiver for 3AM+ unlock detection</li>
 * </ul>
 */
public class CrisisNotificationManager {

    private static final String TAG = "CrisisNotificationMgr";
    public static final String CHANNEL_ID_CRISIS = "mindtrace_crisis";
    public static final String CHANNEL_NAME_CRISIS = "Crisis Alerts";
    public static final String CHANNEL_ID_FOLLOW_UP = "mindtrace_follow_up";
    public static final String CHANNEL_NAME_FOLLOW_UP = "Follow-up Check-ins";

    private static final int NOTIFICATION_ID_CRISIS = 5001;
    private static final int NOTIFICATION_ID_FOLLOW_UP = 5002;
    private static final int NOTIFICATION_ID_NIGHT = 5003;

    private static final AtomicLong lastCrisisNotificationTime = new AtomicLong(0);
    private static final long COOLDOWN_MS = 30 * 60 * 1000; // 30 minutes

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS NOTIFICATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Fire a crisis notification if appropriate (respects cooldown).
     * @return true if notification was fired
     */
    public static boolean notifyIfNeeded(@NonNull Context ctx,
                                          @NonNull CrisisDetector.CrisisAssessment assessment) {
        if (!assessment.shouldNotify()) return false;

        // Cooldown check (thread-safe via AtomicLong CAS)
        long now = System.currentTimeMillis();
        long lastTime = lastCrisisNotificationTime.get();
        if (now - lastTime < COOLDOWN_MS) {
            Log.d(TAG, "Skipping crisis notification (cooldown active)");
            return false;
        }
        // Atomic compare-and-set prevents duplicate notifications from concurrent workers
        if (!lastCrisisNotificationTime.compareAndSet(lastTime, now)) {
            Log.d(TAG, "Skipping crisis notification (concurrent dispatch detected)");
            return false;
        }
        createCrisisChannel(ctx);

        Intent intent = new Intent(ctx, CrisisActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openSupport = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "I'm OK" action — just dismiss
        PendingIntent dismissIntent = PendingIntent.getActivity(ctx, 1,
                new Intent(), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title;
        String body;
        if (assessment.level == MultiModalClassifier.CrisisLevel.CRITICAL) {
            title = "🆘 We're here for you";
            body = "Multiple signals suggest you may be struggling. Support resources are one tap away.";
        } else {
            title = "💙 How are you doing?";
            body = "We noticed some concerning patterns. Would you like to see support resources?";
        }

        if (!assessment.activeSignals.isEmpty()) {
            body += "\n" + assessment.activeSignals.get(0);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID_CRISIS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openSupport)
                .addAction(0, "Open Support", openSupport)
                .addAction(0, "I'm OK", dismissIntent);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_CRISIS, builder.build());
            Log.d(TAG, "Crisis notification fired: " + assessment.level.label);
        }

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // FOLLOW-UP CHECK-IN
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Schedule a gentle follow-up check-in notification after a crisis event.
     * Called at 30min/1hr/2hr after the crisis event.
     */
    public static void sendFollowUp(@NonNull Context ctx, int followUpNumber) {
        createFollowUpChannel(ctx);

        Intent intent = new Intent(ctx, CrisisActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 10 + followUpNumber, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title;
        String body;
        switch (followUpNumber) {
            case 1:
                title = "💚 Quick check-in";
                body = "It's been 30 minutes since your tough moment. How are you feeling now?";
                break;
            case 2:
                title = "🌿 Checking in";
                body = "Just checking in — hope you're doing a bit better. A breathing exercise might help.";
                break;
            case 3:
                title = "✨ You're strong";
                body = "It's been a couple of hours. Remember: tough moments pass. You've got this.";
                break;
            default:
                return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID_FOLLOW_UP)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_FOLLOW_UP + followUpNumber, builder.build());
            Log.d(TAG, "Follow-up #" + followUpNumber + " sent");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NIGHT USAGE ALERT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Send a gentle notification when night usage (3AM+) is detected
     * and the user has a recent high-risk classification.
     */
    public static void sendNightUsageAlert(@NonNull Context ctx) {
        createFollowUpChannel(ctx);

        Intent intent = new Intent(ctx, CrisisActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 20, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID_FOLLOW_UP)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("🌙 Can't sleep?")
                .setContentText("It's late. If you're having trouble sleeping, try a 4-7-8 breathing exercise.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_NIGHT, builder.build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEBRIEF NOTIFICATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Notify user that a post-crisis debrief is available.
     */
    public static void sendDebriefNotification(@NonNull Context ctx) {
        createFollowUpChannel(ctx);

        Intent intent = new Intent(ctx, CrisisActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 30, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID_FOLLOW_UP)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("📝 Ready to reflect?")
                .setContentText("Earlier was tough. When you're ready, a quick reflection can help you learn and grow from the experience.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "Earlier was tough. When you're ready, a quick reflection can help you learn and grow from the experience."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_FOLLOW_UP + 10, builder.build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRAJECTORY ALERT (Phase 6)
    // ═══════════════════════════════════════════════════════════════════

    public static final String CHANNEL_ID_TRAJECTORY = "mindtrace_trajectory";
    public static final String CHANNEL_NAME_TRAJECTORY = "Trend Insights";
    private static final int NOTIFICATION_ID_TRAJECTORY = 5010;
    private static final AtomicLong lastTrajectoryNotificationTime = new AtomicLong(0);
    private static final long TRAJECTORY_COOLDOWN_MS = 6L * 60 * 60 * 1000; // 6 hours

    /**
     * Fire a proactive notification when the 7-day trajectory is rapidly worsening.
     * Uses a separate cooldown from crisis alerts to avoid overloading the user.
     *
     * @param ctx             application context
     * @param trajectoryLabel the trajectory label (e.g., "rapidly_worsening")
     * @param worseningCategory the fastest-worsening category (e.g., "Stress & Anxiety")
     * @param currentRiskPercent current risk percentage (0–100)
     * @return true if notification was fired
     */
    public static boolean notifyTrajectoryIfNeeded(
            @NonNull Context ctx,
            @NonNull String trajectoryLabel,
            @NonNull String worseningCategory,
            int currentRiskPercent) {

        // Only fire for rapidly worsening trajectories
        if (!"rapidly_worsening".equals(trajectoryLabel)) return false;

        // Cooldown check (thread-safe via AtomicLong CAS)
        long now = System.currentTimeMillis();
        long lastTime = lastTrajectoryNotificationTime.get();
        if (now - lastTime < TRAJECTORY_COOLDOWN_MS) {
            Log.d(TAG, "Skipping trajectory notification (cooldown active)");
            return false;
        }
        // Atomic compare-and-set prevents duplicate notifications from concurrent workers
        if (!lastTrajectoryNotificationTime.compareAndSet(lastTime, now)) {
            Log.d(TAG, "Skipping trajectory notification (concurrent dispatch detected)");
            return false;
        }
        createTrajectoryChannel(ctx);

        Intent intent = new Intent(ctx, CrisisActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openDashboard = PendingIntent.getActivity(ctx, 40, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "📊 Your 7-day trend needs attention";
        String body = "Your risk is rapidly worsening (now at " + currentRiskPercent + "%). " +
                worseningCategory + " is rising fastest. " +
                "Open MindTrace to review your trajectory and get a plan.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID_TRAJECTORY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(openDashboard)
                .addAction(0, "Review Trend", openDashboard);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_TRAJECTORY, builder.build());
            Log.d(TAG, "Trajectory alert fired: " + trajectoryLabel +
                    " (" + worseningCategory + ", risk=" + currentRiskPercent + "%)");
        }

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CHANNEL CREATION
    // ═══════════════════════════════════════════════════════════════════

    private static void createCrisisChannel(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_CRISIS, CHANNEL_NAME_CRISIS, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Urgent crisis support notifications");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            NotificationManager manager = ctx.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private static void createFollowUpChannel(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_FOLLOW_UP, CHANNEL_NAME_FOLLOW_UP, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Gentle follow-up check-ins after crisis events");
            NotificationManager manager = ctx.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private static void createTrajectoryChannel(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_TRAJECTORY, CHANNEL_NAME_TRAJECTORY,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Proactive trend insights when your 7-day trajectory changes significantly");
            NotificationManager manager = ctx.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
