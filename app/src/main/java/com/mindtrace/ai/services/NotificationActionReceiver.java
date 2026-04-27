package com.mindtrace.ai.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

/**
 * Handles notification action buttons — mark as done, snooze, dismiss.
 *
 * <h3>Actions:</h3>
 * <ul>
 *   <li>{@code ACTION_MARK_SAFE} — user confirms safety from crisis notification</li>
 *   <li>{@code ACTION_SNOOZE_REMINDER} — snooze daily reminder for 2h</li>
 *   <li>{@code ACTION_DISMISS_NOTIFICATION} — dismiss without action</li>
 * </ul>
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    private static final String TAG = "NotifActionReceiver";
    public static final String ACTION_MARK_SAFE = "com.mindtrace.ai.ACTION_MARK_SAFE";
    public static final String ACTION_SNOOZE_REMINDER = "com.mindtrace.ai.ACTION_SNOOZE_REMINDER";
    public static final String ACTION_DISMISS_NOTIFICATION = "com.mindtrace.ai.ACTION_DISMISS";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);

        Log.d(TAG, "Received action: " + action + ", notifId=" + notificationId);

        switch (action) {
            case ACTION_MARK_SAFE:
                handleMarkSafe(context, notificationId);
                break;

            case ACTION_SNOOZE_REMINDER:
                handleSnoozeReminder(context, notificationId);
                break;

            case ACTION_DISMISS_NOTIFICATION:
                handleDismiss(context, notificationId);
                break;
        }
    }

    private void handleMarkSafe(Context context, int notificationId) {
        try {
            // Resolve active crisis event
            com.mindtrace.ai.repository.CrisisRepository repo =
                    new com.mindtrace.ai.repository.CrisisRepository(context);
            com.mindtrace.ai.database.entity.CrisisEvent active = repo.getActiveEvent();
            if (active != null) {
                active.resolve("notification_safe", null, 0);
                repo.update(active);
                Log.d(TAG, "Crisis marked as safe from notification");
            }

            // Clear crisis SharedPreferences flag
            context.getSharedPreferences("mindtrace_crisis", Context.MODE_PRIVATE)
                    .edit().putBoolean("active_crisis", false).apply();

            // Dismiss notification
            dismissNotification(context, notificationId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to mark safe", e);
        }
    }

    private void handleSnoozeReminder(Context context, int notificationId) {
        try {
            // Set snooze flag for 2 hours
            context.getSharedPreferences("mindtrace_daily_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("reminder_snoozed_until",
                            System.currentTimeMillis() + 2L * 60 * 60 * 1000)
                    .apply();

            dismissNotification(context, notificationId);
            Log.d(TAG, "Reminder snoozed for 2 hours");
        } catch (Exception e) {
            Log.e(TAG, "Failed to snooze reminder", e);
        }
    }

    private void handleDismiss(Context context, int notificationId) {
        dismissNotification(context, notificationId);
    }

    private void dismissNotification(Context context, int notificationId) {
        if (notificationId >= 0) {
            NotificationManagerCompat.from(context).cancel(notificationId);
        }
    }
}
