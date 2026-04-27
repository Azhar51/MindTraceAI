package com.mindtrace.ai.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.MindTraceApp;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.StreakRecoveryManager;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.ui.DailyCheckInActivity;
import com.mindtrace.ai.ui.JournalActivity;
import com.mindtrace.ai.ui.MainActivity;

import java.util.Calendar;

/**
 * Daily Reminder Worker — sends configurable evening reminders for check-ins,
 * journal entries, and task completion.
 *
 * <h3>Notification Types:</h3>
 * <ul>
 *   <li>Check-in reminder (if no check-in today)</li>
 *   <li>Journal prompt (if no journal entry today)</li>
 *   <li>Task summary (X tasks remaining)</li>
 *   <li>Streak at risk (if streak will break without activity today)</li>
 * </ul>
 */
public class DailyReminderWorker extends Worker {

    private static final String TAG = "DailyReminderWorker";
    private static final int NOTIFICATION_ID_CHECKIN = 2001;
    private static final int NOTIFICATION_ID_STREAK = 2002;
    private static final int NOTIFICATION_ID_RECOVERY = 2003;
    private static final String PREF_DAILY_SETTINGS = "mindtrace_daily_settings";

    public DailyReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Running daily reminder check...");

        try {
            Context ctx = getApplicationContext();
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_DAILY_SETTINGS, Context.MODE_PRIVATE);

            // Check if reminders are enabled
            if (!prefs.getBoolean("daily_reminders_enabled", true)) {
                Log.d(TAG, "Daily reminders disabled by user");
                return Result.success();
            }

            // Check quiet hours
            int quietStart = prefs.getInt("quiet_start_hour", 23);
            int quietEnd = prefs.getInt("quiet_end_hour", 7);
            int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (isInQuietHours(currentHour, quietStart, quietEnd)) {
                Log.d(TAG, "In quiet hours, skipping notification");
                return Result.success();
            }

            AppDatabase db = AppDatabase.getInstance(ctx);
            long dayStart = getDayStartMs();

            // ── Crisis-aware streak recovery integration ──
            StreakRecoveryManager recoveryManager = new StreakRecoveryManager();
            StreakRecoveryManager.RecoveryState recoveryState =
                    recoveryManager.getRecoveryState(ctx);

            // Check-in reminder (suppress during active grace period)
            boolean checkedInToday = hasCheckedInToday(db, dayStart);
            if (!checkedInToday && !recoveryState.graceActive) {
                sendCheckInReminder(ctx);
            }

            // Streak at risk — now crisis-aware
            boolean completedTaskToday = hasCompletedTaskToday(db, dayStart);
            if (!completedTaskToday) {
                int currentStreak = calculateCurrentStreak(db);

                // Check if a crisis warrants activating a grace period
                recoveryManager.checkAndActivateGrace(ctx, currentStreak);

                // Re-evaluate state after potential activation
                recoveryState = recoveryManager.getRecoveryState(ctx);

                if (recoveryState.graceActive) {
                    // Grace active — send compassionate recovery notification
                    sendRecoveryNotification(ctx, recoveryState);
                    Log.d(TAG, "Grace period active, sent recovery notification " +
                            "(streak " + currentStreak + " preserved)");
                } else if (!"normal".equals(recoveryState.phase)) {
                    // In gentle_return or recovery_streak — softer reminder
                    sendGentleReturnReminder(ctx, recoveryState);
                } else if (currentStreak >= 2) {
                    // Normal flow — standard streak-at-risk warning
                    sendStreakReminder(ctx, currentStreak);
                }
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Daily reminder failed", e);
            return Result.success(); // Don't retry — not critical
        }
    }

    private void sendCheckInReminder(Context ctx) {
        Intent intent = new Intent(ctx, DailyCheckInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, NOTIFICATION_ID_CHECKIN,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Journal action
        Intent journalIntent = new Intent(ctx, JournalActivity.class);
        journalIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent journalPi = PendingIntent.getActivity(ctx, NOTIFICATION_ID_CHECKIN + 100,
                journalIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, MindTraceApp.CHANNEL_DAILY_REMINDER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("How was your day?")
                .setContentText("Take a quick check-in to track your wellness journey")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("A quick check-in helps you understand your patterns and grow. It only takes 2 minutes."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .addAction(0, "📝 Check In", pi)
                .addAction(0, "📓 Journal", journalPi);

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID_CHECKIN, builder.build());
            Log.d(TAG, "Sent check-in reminder");
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission not granted", e);
        }
    }

    private void sendStreakReminder(Context ctx, int streak) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, NOTIFICATION_ID_STREAK,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, MindTraceApp.CHANNEL_STREAK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("🔥 " + streak + "-day streak at risk!")
                .setContentText("Complete just one task to keep your streak alive")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID_STREAK, builder.build());
            Log.d(TAG, "Sent streak reminder (streak=" + streak + ")");
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission not granted", e);
        }
    }

    /**
     * Sends a compassionate recovery notification when a grace period is active.
     * Instead of "Your streak is at risk!", the user sees a supportive message
     * confirming their streak is safe.
     */
    private void sendRecoveryNotification(Context ctx, StreakRecoveryManager.RecoveryState state) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("open_tab", "insights");
        PendingIntent pi = PendingIntent.getActivity(ctx, NOTIFICATION_ID_RECOVERY,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "🛡 Your streak is safe";
        String body = state.preservedStreak > 0
                ? "Your " + state.preservedStreak + "-day streak is preserved. " +
                  "Take all the time you need — " + state.graceHoursRemaining + "h of grace remaining."
                : "We've paused your streak while you recover. No pressure — your progress is safe.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, MindTraceApp.CHANNEL_STREAK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID_RECOVERY, builder.build());
            Log.d(TAG, "Sent recovery notification (grace active)");
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission not granted", e);
        }
    }

    /**
     * Sends a gentle return reminder for users in the recovery phase
     * (post-grace, pre-normal). Uses encouraging tone with reduced expectations.
     */
    private void sendGentleReturnReminder(Context ctx, StreakRecoveryManager.RecoveryState state) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("open_tab", "tasks");
        PendingIntent pi = PendingIntent.getActivity(ctx, NOTIFICATION_ID_RECOVERY + 1,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "gentle_return".equals(state.phase)
                ? "💙 Welcome back"
                : "🌱 You're rebuilding";
        String body = "gentle_return".equals(state.phase)
                ? "Any small step today counts as a full win. No rush."
                : "Every day you show up is proof of your resilience.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, MindTraceApp.CHANNEL_DAILY_REMINDER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID_RECOVERY, builder.build());
            Log.d(TAG, "Sent gentle return reminder (phase=" + state.phase + ")");
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission not granted", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private boolean hasCheckedInToday(AppDatabase db, long dayStart) {
        try {
            return db.questionnaireDao().getCountSince(dayStart) > 0;
        } catch (Exception e) {
            return true; // Assume checked in to avoid spamming
        }
    }

    private boolean hasCompletedTaskToday(AppDatabase db, long dayStart) {
        try {
            return db.taskDao().getCompletedCountSince(dayStart) > 0;
        } catch (Exception e) {
            return true;
        }
    }

    private int calculateCurrentStreak(AppDatabase db) {
        try {
            java.util.List<Long> days = db.taskDao().getCompletionDays();
            if (days == null || days.isEmpty()) return 0;
            int streak = 1;
            for (int i = 1; i < days.size(); i++) {
                if (days.get(i - 1) - days.get(i) == 1) streak++;
                else break;
            }
            return streak;
        } catch (Exception e) {
            return 0;
        }
    }

    private long getDayStartMs() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private boolean isInQuietHours(int currentHour, int quietStart, int quietEnd) {
        if (quietStart <= quietEnd) {
            return currentHour >= quietStart && currentHour < quietEnd;
        } else {
            return currentHour >= quietStart || currentHour < quietEnd;
        }
    }
}
