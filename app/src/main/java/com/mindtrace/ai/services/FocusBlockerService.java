package com.mindtrace.ai.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.mindtrace.ai.MindTraceApp;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.AppCategoryMapper;
import com.mindtrace.ai.ui.FocusSessionActivity;
import com.mindtrace.ai.ui.FocusBlockActivity;

public class FocusBlockerService extends Service {

    private static final int NOTIFICATION_ID = 888;
    private static final long POLLING_INTERVAL_MS = 1500L; // 1.5 seconds

    private Handler handler;
    private UsageStatsManager usageStatsManager;
    private boolean isRunning = false;
    private long endTimeMillis = 0;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            if (System.currentTimeMillis() >= endTimeMillis) {
                stopSelf();
                return;
            }

            checkForegroundApp();
            handler.postDelayed(this, POLLING_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_FOCUS".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        long durationMinutes = intent != null ? intent.getLongExtra("DURATION_MINUTES", 25) : 25;
        endTimeMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }

        if (!isRunning) {
            isRunning = true;
            handler.post(pollRunnable);
        }

        return START_STICKY;
    }

    private void checkForegroundApp() {
        if (usageStatsManager == null) return;

        long now = System.currentTimeMillis();
        UsageEvents events = usageStatsManager.queryEvents(now - 10000, now);
        UsageEvents.Event event = new UsageEvents.Event();
        
        String foregroundApp = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundApp = event.getPackageName();
            }
        }

        if (foregroundApp != null && !foregroundApp.equals(getPackageName())) {
            // Check if app is distracting
            boolean isDistracting = AppCategoryMapper.isPassive(foregroundApp);
            if (isDistracting) {
                blockApp(foregroundApp);
            }
        }
    }

    private void blockApp(String packageName) {
        Intent lockIntent = new Intent(this, FocusBlockActivity.class);
        lockIntent.putExtra("BLOCKED_PACKAGE", packageName);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(lockIntent);
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, FocusSessionActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, FocusBlockerService.class);
        stopIntent.setAction("STOP_FOCUS");
        PendingIntent stopPendingIntent = PendingIntent.getService(this,
                1, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, MindTraceApp.CHANNEL_DAILY_REMINDER)
                .setContentTitle("Focus Mode Active")
                .setContentText("MindTrace is blocking distracting apps.")
                .setSmallIcon(R.drawable.ic_mission_focus)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Focus", stopPendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacks(pollRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
