package com.mindtrace.ai.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.service.WorkerErrorHandler;

import com.mindtrace.ai.MindTraceApp;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.TaskLifecycleManager;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.ui.MainActivity;

import java.util.Calendar;
import java.util.List;

/**
 * WorkManager worker for task scheduling, reminders, and lifecycle maintenance.
 *
 * <h3>Runs every 4 hours and performs:</h3>
 * <ol>
 *   <li><b>Lifecycle maintenance</b> — auto-expire 24h-old tasks, reactivate snoozed, purge stale</li>
 *   <li><b>Time slot rescheduling</b> — move missed morning tasks to afternoon, etc.</li>
 *   <li><b>Gentle nudge notifications</b> — remind user of pending active tasks</li>
 *   <li><b>Contextual timing</b> — breathing tasks during detected high-stress hours</li>
 * </ol>
 */
public class TaskReminderWorker extends Worker {

    private static final String TAG = "TaskReminderWorker";
    private static final int NOTIFICATION_ID_REMINDER = 3001;

    public TaskReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "TaskReminderWorker started");
        Context ctx = getApplicationContext();

        try {
            // 1. Run lifecycle maintenance
            runLifecycleMaintenance(ctx);

            // 2. Reschedule missed time-slot tasks
            rescheduleExpiredSlots(ctx);

            // 3. Send gentle reminder if pending tasks exist
            sendReminderIfNeeded(ctx);

            Log.d(TAG, "TaskReminderWorker completed successfully");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "TaskReminderWorker failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. LIFECYCLE MAINTENANCE
    // ═══════════════════════════════════════════════════════════════════

    private void runLifecycleMaintenance(@NonNull Context ctx) {
        TaskLifecycleManager manager = new TaskLifecycleManager(ctx);
        TaskLifecycleManager.LifecycleResult result = manager.runMaintenance();
        Log.d(TAG, "Lifecycle: " + result.expired + " expired, " +
                result.reactivated + " reactivated, " + result.cleaned + " cleaned");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. RESCHEDULE MISSED TIME SLOTS
    // ═══════════════════════════════════════════════════════════════════

    private void rescheduleExpiredSlots(@NonNull Context ctx) {
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            List<InterventionTask> activeTasks = db.taskDao().getActiveTasksSync();
            if (activeTasks == null) return;

            String currentSlot = getCurrentTimeSlot();
            long now = System.currentTimeMillis();

            for (InterventionTask task : activeTasks) {
                if (task.scheduledTimeSlot == null || "ANYTIME".equals(task.scheduledTimeSlot)) continue;

                // If task was scheduled for MORNING but it's now AFTERNOON, reschedule
                if ("MORNING".equals(task.scheduledTimeSlot) && "AFTERNOON".equals(currentSlot)) {
                    task.scheduledTimeSlot = "AFTERNOON";
                    db.taskDao().update(task);
                    Log.d(TAG, "Rescheduled task " + task.id + " from MORNING → AFTERNOON");
                }
                // If task was scheduled for AFTERNOON but it's now EVENING, reschedule
                else if ("AFTERNOON".equals(task.scheduledTimeSlot) && "EVENING".equals(currentSlot)) {
                    task.scheduledTimeSlot = "EVENING";
                    db.taskDao().update(task);
                    Log.d(TAG, "Rescheduled task " + task.id + " from AFTERNOON → EVENING");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Reschedule failed", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. GENTLE REMINDER NOTIFICATION
    // ═══════════════════════════════════════════════════════════════════

    private void sendReminderIfNeeded(@NonNull Context ctx) {
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            List<InterventionTask> activeTasks = db.taskDao().getActiveTasksSync();
            if (activeTasks == null || activeTasks.isEmpty()) return;

            // Only remind between 9AM and 9PM
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour < 9 || hour > 21) return;

            // Find a task matching the current time slot
            String currentSlot = getCurrentTimeSlot();
            InterventionTask bestTask = null;
            for (InterventionTask task : activeTasks) {
                if (task.isActionable()) {
                    if (currentSlot.equals(task.scheduledTimeSlot) || "ANYTIME".equals(task.scheduledTimeSlot)) {
                        bestTask = task;
                        break;
                    }
                    if (bestTask == null) bestTask = task;
                }
            }

            if (bestTask == null) return;

            // Don't remind if task was created in last 30min (just generated)
            if (System.currentTimeMillis() - bestTask.dateCreated < 30 * 60 * 1000) return;

            sendNotification(ctx, bestTask, activeTasks.size());
        } catch (Exception e) {
            Log.e(TAG, "Reminder notification failed", e);
        }
    }

    private void sendNotification(@NonNull Context ctx, @NonNull InterventionTask task, int totalActive) {
        // Channel already created by MindTraceApp.createNotificationChannels()

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = task.isMicroIntervention ? "⚡ Quick intervention" : "🌿 Gentle reminder";
        String body = task.title;
        if (totalActive > 1) {
            body += " (+" + (totalActive - 1) + " more)";
        }
        if (task.whyThisTask != null && !task.whyThisTask.isEmpty()) {
            body += "\n" + task.whyThisTask;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, MindTraceApp.CHANNEL_TASK_NUDGE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_REMINDER, builder.build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private String getCurrentTimeSlot() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) return "MORNING";
        if (hour >= 12 && hour < 17) return "AFTERNOON";
        if (hour >= 17 && hour < 22) return "EVENING";
        return "ANYTIME";
    }

    // NOTE: Notification channel is created centrally by MindTraceApp
    // using CHANNEL_TASK_NUDGE — no inline channel creation needed.
}
