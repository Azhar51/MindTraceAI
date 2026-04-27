package com.mindtrace.ai.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Iterator;
import java.util.TimeZone;

/**
 * Lightweight, SharedPreferences-backed notification event tracker.
 *
 * <p>Receives notification events from {@code MindTraceNotificationListener}
 * and accumulates daily metrics for the AI pipeline:</p>
 *
 * <h3>Tracked Signals:</h3>
 * <ul>
 *   <li><b>notificationsPosted</b> — total notifications received today</li>
 *   <li><b>notificationsOpened</b> — notifications that led to app opens</li>
 *   <li><b>avgResponseTimeMs</b> — average time from post → app open</li>
 *   <li><b>nightNotifications</b> — notifications received 10pm–6am</li>
 *   <li><b>perAppJson</b> — per-package notification counts</li>
 * </ul>
 *
 * <h3>Response Latency Tracking:</h3>
 * <p>When a notification is posted, its timestamp is cached keyed by package.
 * When the notification is removed (user opened it), the latency is calculated
 * and added to the running average. This gives the time-to-respond signal
 * that feeds into {@code DailyUsage.notificationResponseAvgMs}.</p>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   Android NotificationListenerService
 *       → MindTraceNotificationListener
 *           → NotificationEventTracker (this class, SharedPrefs)
 *               → UsageIntelligenceEngine.syncNotificationData()
 *                   → DailyUsage.notificationResponseAvgMs
 *                       → FeatureVector (future feature slot)
 * </pre>
 *
 * @see com.mindtrace.ai.services.MindTraceNotificationListener
 * @see com.mindtrace.ai.database.entity.DailyUsage#notificationResponseAvgMs
 * @see com.mindtrace.ai.database.entity.UsageSession#wasNotificationTriggered
 */
public class NotificationEventTracker {

    private static final String TAG = "NotificationTracker";

    // SharedPreferences store
    private static final String PREFS_NAME = "mindtrace_notification_events";

    // Current day keys
    private static final String KEY_DATE = "tracking_date";
    private static final String KEY_POSTED_COUNT = "notifications_posted";
    private static final String KEY_OPENED_COUNT = "notifications_opened";
    private static final String KEY_TOTAL_RESPONSE_MS = "total_response_ms";
    private static final String KEY_NIGHT_COUNT = "night_notification_count";
    private static final String KEY_PER_APP_JSON = "per_app_notification_json";
    private static final String KEY_PENDING_JSON = "pending_notifications_json";
    private static final String KEY_FASTEST_RESPONSE_MS = "fastest_response_ms";
    private static final String KEY_SLOWEST_RESPONSE_MS = "slowest_response_ms";

    // Night window (same as ScreenEventReceiver)
    private static final int NIGHT_START_HOUR = 22;
    private static final int NIGHT_END_HOUR = 6;

    // Ignore responses faster than 1s (auto-dismiss) or slower than 2h (stale)
    private static final long MIN_VALID_RESPONSE_MS = 1000;
    private static final long MAX_VALID_RESPONSE_MS = 2 * 60 * 60 * 1000;

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API — Called from NotificationListenerService
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Called when a notification is posted by any app.
     *
     * @param context     application context
     * @param packageName originating app's package
     * @param notifKey    notification key (for matching with removal)
     */
    public static void onNotificationPosted(@NonNull Context context,
                                             @NonNull String packageName,
                                             @NonNull String notifKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();

        ensureDateFreshness(prefs, now);

        SharedPreferences.Editor editor = prefs.edit();

        // Increment posted count
        int posted = prefs.getInt(KEY_POSTED_COUNT, 0) + 1;
        editor.putInt(KEY_POSTED_COUNT, posted);

        // Track night notifications
        if (isNightTime(now)) {
            int nightCount = prefs.getInt(KEY_NIGHT_COUNT, 0) + 1;
            editor.putInt(KEY_NIGHT_COUNT, nightCount);
        }

        // Update per-app count
        updatePerAppCount(prefs, editor, packageName);

        // Cache this notification's timestamp for response latency calculation
        cacheNotificationTimestamp(prefs, editor, packageName, notifKey, now);

        editor.apply();
    }

    /**
     * Called when a notification is removed (usually because user opened it).
     *
     * @param context     application context
     * @param packageName originating app's package
     * @param notifKey    notification key (matches posted key)
     * @param userAction  true if user explicitly dismissed/opened (not auto-cancel)
     */
    public static void onNotificationRemoved(@NonNull Context context,
                                              @NonNull String packageName,
                                              @NonNull String notifKey,
                                              boolean userAction) {
        if (!userAction) return; // Ignore auto-cancelled notifications

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();

        ensureDateFreshness(prefs, now);

        // Look up when this notification was posted
        Long postedTime = getAndRemovePendingTimestamp(prefs, packageName, notifKey);
        if (postedTime == null) return; // No matching post found

        long responseMs = now - postedTime;

        // Validate response time
        if (responseMs < MIN_VALID_RESPONSE_MS || responseMs > MAX_VALID_RESPONSE_MS) return;

        SharedPreferences.Editor editor = prefs.edit();

        // Increment opened count
        int opened = prefs.getInt(KEY_OPENED_COUNT, 0) + 1;
        editor.putInt(KEY_OPENED_COUNT, opened);

        // Add to running total for average calculation
        long totalMs = prefs.getLong(KEY_TOTAL_RESPONSE_MS, 0) + responseMs;
        editor.putLong(KEY_TOTAL_RESPONSE_MS, totalMs);

        // Track min/max response
        long fastest = prefs.getLong(KEY_FASTEST_RESPONSE_MS, Long.MAX_VALUE);
        if (responseMs < fastest) {
            editor.putLong(KEY_FASTEST_RESPONSE_MS, responseMs);
        }
        long slowest = prefs.getLong(KEY_SLOWEST_RESPONSE_MS, 0);
        if (responseMs > slowest) {
            editor.putLong(KEY_SLOWEST_RESPONSE_MS, responseMs);
        }

        editor.apply();

        Log.d(TAG, String.format("Notification response: %s → %dms (avg=%dms, n=%d)",
                packageName, responseMs, opened > 0 ? totalMs / opened : 0, opened));
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API — Data reads for pipeline sync
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Get today's notification data for syncing into DailyUsage.
     */
    @NonNull
    public static NotificationData getTodayData(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return readData(prefs, false);
    }

    /**
     * Get yesterday's archived notification data.
     */
    @NonNull
    public static NotificationData getYesterdayData(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return readData(prefs, true);
    }

    /**
     * Check if a given package just posted a notification within the last N ms.
     * Used by the pipeline to mark sessions as notification-triggered.
     *
     * @param packageName package to check
     * @param withinMs    lookback window in milliseconds
     * @return true if a notification from this package was posted within the window
     */
    public static boolean wasRecentlyNotified(@NonNull Context context,
                                               @NonNull String packageName,
                                               long withinMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            String json = prefs.getString(KEY_PENDING_JSON, "{}");
            JSONObject pending = new JSONObject(json);
            JSONObject pkgNotifs = pending.optJSONObject(packageName);
            if (pkgNotifs == null) return false;

            long now = System.currentTimeMillis();
            java.util.Iterator<String> keys = pkgNotifs.keys();
            while (keys.hasNext()) {
                long postedTime = pkgNotifs.optLong(keys.next(), 0);
                if (postedTime > 0 && (now - postedTime) < withinMs) {
                    return true;
                }
            }
        } catch (JSONException ignored) {}
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL — Date management
    // ─────────────────────────────────────────────────────────────────────

    private static void ensureDateFreshness(SharedPreferences prefs, long now) {
        long storedDate = prefs.getLong(KEY_DATE, 0);
        long todayMidnight = getMidnight(now);

        if (storedDate != todayMidnight) {
            SharedPreferences.Editor editor = prefs.edit();

            if (storedDate > 0) {
                // Archive yesterday's data
                editor.putInt("prev_posted", prefs.getInt(KEY_POSTED_COUNT, 0));
                editor.putInt("prev_opened", prefs.getInt(KEY_OPENED_COUNT, 0));
                editor.putLong("prev_total_response_ms", prefs.getLong(KEY_TOTAL_RESPONSE_MS, 0));
                editor.putInt("prev_night_count", prefs.getInt(KEY_NIGHT_COUNT, 0));
                editor.putString("prev_per_app_json", prefs.getString(KEY_PER_APP_JSON, "{}"));
                editor.putLong("prev_fastest_ms", prefs.getLong(KEY_FASTEST_RESPONSE_MS, 0));
                editor.putLong("prev_slowest_ms", prefs.getLong(KEY_SLOWEST_RESPONSE_MS, 0));
                editor.putLong("prev_date", storedDate);
            }

            // Reset for new day
            editor.putLong(KEY_DATE, todayMidnight);
            editor.putInt(KEY_POSTED_COUNT, 0);
            editor.putInt(KEY_OPENED_COUNT, 0);
            editor.putLong(KEY_TOTAL_RESPONSE_MS, 0);
            editor.putInt(KEY_NIGHT_COUNT, 0);
            editor.putString(KEY_PER_APP_JSON, "{}");
            editor.putString(KEY_PENDING_JSON, "{}");
            editor.putLong(KEY_FASTEST_RESPONSE_MS, Long.MAX_VALUE);
            editor.putLong(KEY_SLOWEST_RESPONSE_MS, 0);

            editor.apply();
            Log.d(TAG, "New day detected — notification counters reset");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL — Pending notification cache
    // ─────────────────────────────────────────────────────────────────────

    private static void cacheNotificationTimestamp(SharedPreferences prefs,
                                                    SharedPreferences.Editor editor,
                                                    String packageName,
                                                    String notifKey,
                                                    long timestamp) {
        try {
            String json = prefs.getString(KEY_PENDING_JSON, "{}");
            JSONObject pending = new JSONObject(json);

            JSONObject pkgNotifs = pending.optJSONObject(packageName);
            if (pkgNotifs == null) {
                pkgNotifs = new JSONObject();
            }

            // Only keep last 10 pending notifications per package (prevent bloat)
            if (pkgNotifs.length() >= 10) {
                // Remove oldest
                Iterator<String> keys = pkgNotifs.keys();
                if (keys.hasNext()) {
                    keys.next();
                    keys.remove();
                }
            }

            pkgNotifs.put(notifKey, timestamp);
            pending.put(packageName, pkgNotifs);
            editor.putString(KEY_PENDING_JSON, pending.toString());
        } catch (JSONException e) {
            Log.w(TAG, "Failed to cache notification timestamp", e);
        }
    }

    @Nullable
    private static Long getAndRemovePendingTimestamp(SharedPreferences prefs,
                                                      String packageName,
                                                      String notifKey) {
        try {
            String json = prefs.getString(KEY_PENDING_JSON, "{}");
            JSONObject pending = new JSONObject(json);

            JSONObject pkgNotifs = pending.optJSONObject(packageName);
            if (pkgNotifs == null || !pkgNotifs.has(notifKey)) return null;

            long timestamp = pkgNotifs.getLong(notifKey);
            pkgNotifs.remove(notifKey);
            if (pkgNotifs.length() == 0) {
                pending.remove(packageName);
            } else {
                pending.put(packageName, pkgNotifs);
            }

            prefs.edit().putString(KEY_PENDING_JSON, pending.toString()).apply();
            return timestamp;
        } catch (JSONException e) {
            Log.w(TAG, "Failed to retrieve pending notification", e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL — Per-app tracking
    // ─────────────────────────────────────────────────────────────────────

    private static void updatePerAppCount(SharedPreferences prefs,
                                           SharedPreferences.Editor editor,
                                           String packageName) {
        try {
            String json = prefs.getString(KEY_PER_APP_JSON, "{}");
            JSONObject perApp = new JSONObject(json);
            int count = perApp.optInt(packageName, 0) + 1;
            perApp.put(packageName, count);
            editor.putString(KEY_PER_APP_JSON, perApp.toString());
        } catch (JSONException e) {
            Log.w(TAG, "Failed to update per-app notification count", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL — Data reading
    // ─────────────────────────────────────────────────────────────────────

    private static NotificationData readData(SharedPreferences prefs, boolean yesterday) {
        NotificationData data = new NotificationData();
        if (yesterday) {
            data.date = prefs.getLong("prev_date", 0);
            data.notificationsPosted = prefs.getInt("prev_posted", 0);
            data.notificationsOpened = prefs.getInt("prev_opened", 0);
            data.totalResponseMs = prefs.getLong("prev_total_response_ms", 0);
            data.nightNotificationCount = prefs.getInt("prev_night_count", 0);
            data.perAppJson = prefs.getString("prev_per_app_json", "{}");
            data.fastestResponseMs = prefs.getLong("prev_fastest_ms", 0);
            data.slowestResponseMs = prefs.getLong("prev_slowest_ms", 0);
        } else {
            data.date = prefs.getLong(KEY_DATE, getMidnight(System.currentTimeMillis()));
            data.notificationsPosted = prefs.getInt(KEY_POSTED_COUNT, 0);
            data.notificationsOpened = prefs.getInt(KEY_OPENED_COUNT, 0);
            data.totalResponseMs = prefs.getLong(KEY_TOTAL_RESPONSE_MS, 0);
            data.nightNotificationCount = prefs.getInt(KEY_NIGHT_COUNT, 0);
            data.perAppJson = prefs.getString(KEY_PER_APP_JSON, "{}");
            data.fastestResponseMs = prefs.getLong(KEY_FASTEST_RESPONSE_MS, Long.MAX_VALUE);
            data.slowestResponseMs = prefs.getLong(KEY_SLOWEST_RESPONSE_MS, 0);
        }
        return data;
    }

    // ─────────────────────────────────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────────────────────────────────

    private static boolean isNightTime(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
    }

    private static long getMidnight(long timestamp) {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ─────────────────────────────────────────────────────────────────────
    // DATA CLASS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Snapshot of notification event data for a single day.
     * Used to sync into DailyUsage / BehaviorSnapshotEntity.
     */
    public static class NotificationData {
        /** Day timestamp (midnight). */
        public long date;

        /** Total notifications received. */
        public int notificationsPosted;

        /** Notifications that the user opened/acted on. */
        public int notificationsOpened;

        /** Cumulative response time for average calculation (ms). */
        public long totalResponseMs;

        /** Notifications received during night window (10pm–6am). */
        public int nightNotificationCount;

        /** JSON string: {"package.name": count, ...} */
        @Nullable
        public String perAppJson;

        /** Fastest notification response of the day (ms). */
        public long fastestResponseMs;

        /** Slowest notification response of the day (ms). */
        public long slowestResponseMs;

        /** Whether any data has been collected. */
        public boolean isValid() {
            return notificationsPosted > 0;
        }

        /** Average response time in milliseconds. Returns 0 if no responses. */
        public long getAverageResponseMs() {
            return notificationsOpened > 0 ? totalResponseMs / notificationsOpened : 0;
        }

        /** Response rate: fraction of notifications that were opened. */
        public float getResponseRate() {
            return notificationsPosted > 0
                    ? (float) notificationsOpened / notificationsPosted : 0f;
        }

        @NonNull
        @Override
        public String toString() {
            return "NotificationData{" +
                    "posted=" + notificationsPosted +
                    ", opened=" + notificationsOpened +
                    ", avgResponseMs=" + getAverageResponseMs() +
                    ", responseRate=" + String.format("%.0f%%", getResponseRate() * 100) +
                    ", nightCount=" + nightNotificationCount +
                    '}';
        }
    }
}
