package com.mindtrace.ai.services;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.mindtrace.ai.service.NotificationEventTracker;

/**
 * System-level notification listener that tracks all posted and removed
 * notifications across all apps on the device.
 *
 * <p>This service requires the user to grant notification access via
 * Settings → Special app access → Notification access. MindTrace's
 * {@code PermissionActivity} guides the user through this flow.</p>
 *
 * <h3>Purpose:</h3>
 * <ul>
 *   <li>Measure <b>notification response latency</b> — time from notification
 *       post to user action (open/dismiss). Lower values indicate higher
 *       notification dependency.</li>
 *   <li>Track <b>notification volume</b> — total notifications per day, by app,
 *       during night hours.</li>
 *   <li>Enable <b>notification-triggered session attribution</b> — mark
 *       {@code UsageSession.wasNotificationTriggered} when an app session
 *       follows a notification from the same app.</li>
 * </ul>
 *
 * <h3>Privacy:</h3>
 * <p>This service does NOT read notification content (title, text, extras).
 * It only records: package name, notification key, post timestamp, and
 * removal reason. All data stays on-device in SharedPreferences.</p>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   Android System → MindTraceNotificationListener (this service)
 *       → NotificationEventTracker (SharedPrefs)
 *           → UsageIntelligenceEngine → DailyUsage.notificationResponseAvgMs
 *           → BehaviorAnalyzer → UsageSession.wasNotificationTriggered
 * </pre>
 *
 * @see NotificationEventTracker
 * @see com.mindtrace.ai.database.entity.DailyUsage#notificationResponseAvgMs
 * @see com.mindtrace.ai.database.entity.UsageSession#wasNotificationTriggered
 */
public class MindTraceNotificationListener extends NotificationListenerService {

    private static final String TAG = "MindTraceNotifListener";

    // Packages to ignore (system, own app)
    private static final String[] IGNORED_PACKAGES = {
            "android",
            "com.android.systemui",
            "com.android.providers.downloads",
            "com.mindtrace.ai"  // Don't track our own notifications
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "MindTrace Notification Listener connected — tracking notification responses");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.i(TAG, "MindTrace Notification Listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null) return;

        String packageName = sbn.getPackageName();

        // Skip system and own notifications
        if (shouldIgnore(packageName)) return;

        // Skip ongoing/persistent notifications (music players, foreground services)
        if (sbn.isOngoing()) return;

        String notifKey = sbn.getKey();
        NotificationEventTracker.onNotificationPosted(
                getApplicationContext(), packageName, notifKey);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        if (sbn == null || sbn.getPackageName() == null) return;

        String packageName = sbn.getPackageName();
        if (shouldIgnore(packageName)) return;
        if (sbn.isOngoing()) return;

        // Determine if this was a user-initiated removal
        // REASON_CLICK = user tapped the notification → opened the app
        // REASON_CANCEL = user swiped to dismiss
        // Other reasons (REASON_APP_CANCEL, REASON_CHANNEL_BANNED, etc.) are auto-removals
        boolean userAction = (reason == REASON_CLICK || reason == REASON_CANCEL);

        String notifKey = sbn.getKey();
        NotificationEventTracker.onNotificationRemoved(
                getApplicationContext(), packageName, notifKey, userAction);
    }

    /**
     * Check if a package should be ignored (system packages, own app).
     */
    private boolean shouldIgnore(String packageName) {
        for (String ignored : IGNORED_PACKAGES) {
            if (ignored.equals(packageName)) {
                return true;
            }
        }
        // Also ignore packages that look like system framework
        return packageName.startsWith("com.android.") && !packageName.contains("messaging");
    }
}
