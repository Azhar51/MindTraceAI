package com.mindtrace.ai;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.mindtrace.ai.service.ScreenEventReceiver;
import com.mindtrace.ai.service.WorkScheduler;

import android.content.IntentFilter;
import android.content.Intent;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MindTraceApp extends Application {

    // ═══════════════════════════════════════════════════════════════════
    // NOTIFICATION CHANNEL IDs
    // ═══════════════════════════════════════════════════════════════════
    public static final String CHANNEL_CRISIS = "mindtrace_crisis";
    public static final String CHANNEL_FOLLOW_UP = "mindtrace_follow_up";
    public static final String CHANNEL_DAILY_REMINDER = "mindtrace_daily_reminder";
    public static final String CHANNEL_WEEKLY_REPORT = "mindtrace_weekly_report";
    public static final String CHANNEL_TASK_NUDGE = "mindtrace_task_nudge";
    public static final String CHANNEL_STREAK = "mindtrace_streak";
    public static final String CHANNEL_TRAJECTORY = "mindtrace_trajectory";
    public static final String CHANNEL_NUDGE = "mindtrace_nudge";

    @Override
    public void onCreate() {
        super.onCreate();

        setupGlobalExceptionHandler();
        createNotificationChannels();

        // ── Centralized worker scheduling (single source of truth) ──
        WorkScheduler.scheduleAll(this);

        registerScreenReceiver();
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        
        // We register receiver dynamically so it can listen to screen events
        // as long as the application process is alive.
        // API 33+ requires explicit export flag for runtime-registered receivers.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(new ScreenEventReceiver(), filter,
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(new ScreenEventReceiver(), filter);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GLOBAL EXCEPTION HANDLER (7.F.6)
    // ═══════════════════════════════════════════════════════════════════

    private void setupGlobalExceptionHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                // Log crash to file for diagnostics
                java.io.File crashDir = new java.io.File(getFilesDir(), "crash_logs");
                if (!crashDir.exists()) crashDir.mkdirs();
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                        java.util.Locale.US).format(new java.util.Date());
                java.io.File crashFile = new java.io.File(crashDir, "crash_" + timestamp + ".txt");
                java.io.FileWriter writer = new java.io.FileWriter(crashFile);
                writer.write("Thread: " + thread.getName() + "\n");
                writer.write("Time: " + timestamp + "\n");
                writer.write("Exception: " + throwable.toString() + "\n\n");
                java.io.StringWriter sw = new java.io.StringWriter();
                throwable.printStackTrace(new java.io.PrintWriter(sw));
                writer.write(sw.toString());
                writer.close();
                android.util.Log.e("MindTraceApp", "Crash logged to " + crashFile.getAbsolutePath());
            } catch (Exception ignored) {}

            // Keep crisis flag intact (don't clear it on crash)
            // SplashActivity will check and re-open crisis mode

            // Delegate to default handler
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // NOTIFICATION CHANNELS (Android O+)
    // ═══════════════════════════════════════════════════════════════════

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // 1. Crisis — maximum priority, bypass DND
        NotificationChannel crisis = new NotificationChannel(
                CHANNEL_CRISIS, "Crisis Alerts", NotificationManager.IMPORTANCE_MAX);
        crisis.setDescription("Urgent safety notifications when crisis indicators are detected");
        crisis.enableVibration(true);
        crisis.setVibrationPattern(new long[]{0, 500, 200, 500});
        crisis.setBypassDnd(true);
        crisis.setShowBadge(true);
        nm.createNotificationChannel(crisis);

        // 2. Follow-up — high priority
        NotificationChannel followUp = new NotificationChannel(
                CHANNEL_FOLLOW_UP, "Follow-Up Check-ins", NotificationManager.IMPORTANCE_HIGH);
        followUp.setDescription("Post-crisis follow-up and debrief reminders");
        followUp.enableVibration(true);
        nm.createNotificationChannel(followUp);

        // 3. Daily reminders
        NotificationChannel daily = new NotificationChannel(
                CHANNEL_DAILY_REMINDER, "Daily Reminders", NotificationManager.IMPORTANCE_DEFAULT);
        daily.setDescription("Daily check-in and wellness reminders");
        nm.createNotificationChannel(daily);

        // 4. Weekly report
        NotificationChannel weekly = new NotificationChannel(
                CHANNEL_WEEKLY_REPORT, "Weekly Reports", NotificationManager.IMPORTANCE_DEFAULT);
        weekly.setDescription("Weekly wellness report notifications");
        nm.createNotificationChannel(weekly);

        // 5. Task nudges
        NotificationChannel taskNudge = new NotificationChannel(
                CHANNEL_TASK_NUDGE, "Task Reminders", NotificationManager.IMPORTANCE_DEFAULT);
        taskNudge.setDescription("Personalized task completion reminders");
        nm.createNotificationChannel(taskNudge);

        // 6. Streak alerts
        NotificationChannel streak = new NotificationChannel(
                CHANNEL_STREAK, "Streak & Progress", NotificationManager.IMPORTANCE_DEFAULT);
        streak.setDescription("Streak milestones and achievement notifications");
        nm.createNotificationChannel(streak);

        // 7. Trajectory / Trend Insights
        NotificationChannel trajectory = new NotificationChannel(
                CHANNEL_TRAJECTORY, "Trend Insights", NotificationManager.IMPORTANCE_DEFAULT);
        trajectory.setDescription("Proactive insights when your 7-day trajectory changes significantly");
        nm.createNotificationChannel(trajectory);

        // 8. Behavioral nudges
        NotificationChannel nudge = new NotificationChannel(
                CHANNEL_NUDGE, "Behavioral Nudges", NotificationManager.IMPORTANCE_DEFAULT);
        nudge.setDescription("Proactive micro-nudges based on real-time behavioral patterns");
        nm.createNotificationChannel(nudge);
    }
}
