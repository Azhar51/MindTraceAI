package com.mindtrace.ai.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Tracks phone unlock events, screen on/off transitions, and sleep proxy signals.
 *
 * <p>This receiver captures three critical behavioural signals that the
 * {@code UsageStatsManager} API cannot provide:</p>
 * <ol>
 *   <li><b>Unlock count</b> — how many times the user unlocked the phone (compulsive checking)</li>
 *   <li><b>First unlock time</b> — wake-up proxy for sleep analysis</li>
 *   <li><b>Last screen-off time</b> — bedtime proxy for sleep analysis</li>
 * </ol>
 *
 * <h3>Signal Flow:</h3>
 * <pre>
 *   Android System Broadcasts
 *       → ScreenEventReceiver (this class)
 *           → SharedPreferences (fast, per-day counters)
 *               → UsageIntelligenceEngine.syncScreenEvents()
 *                   → DailyUsage entity (unlockCount, firstUnlockTime, lastScreenOffTime)
 *                       → DigitalFeatureExtractor (Feature D7)
 * </pre>
 *
 * <h3>Registration:</h3>
 * Must be registered programmatically (not in Manifest) because
 * {@code ACTION_SCREEN_ON/OFF} are runtime-only broadcasts since API 26.
 *
 * <h3>Persistence Strategy:</h3>
 * Uses SharedPreferences (not Room) for ultra-fast counter increments.
 * Data is synced to Room by {@code UsageIntelligenceEngine} during daily analysis.
 *
 * @see com.mindtrace.ai.database.entity.DailyUsage#unlockCount
 * @see com.mindtrace.ai.database.entity.DailyUsage#firstUnlockTime
 * @see com.mindtrace.ai.database.entity.DailyUsage#lastScreenOffTime
 */
public class ScreenEventReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenEventReceiver";

    // SharedPreferences keys
    private static final String PREFS_NAME = "mindtrace_screen_events";
    private static final String KEY_DATE = "tracking_date";
    private static final String KEY_UNLOCK_COUNT = "unlock_count";
    private static final String KEY_SCREEN_ON_COUNT = "screen_on_count";
    private static final String KEY_FIRST_UNLOCK_TIME = "first_unlock_time";
    private static final String KEY_LAST_SCREEN_OFF_TIME = "last_screen_off_time";
    private static final String KEY_LAST_SCREEN_ON_TIME = "last_screen_on_time";
    private static final String KEY_TOTAL_OFF_DURATION = "total_off_duration_ms";
    private static final String KEY_LONGEST_OFF_STREAK = "longest_off_streak_ms";
    private static final String KEY_SESSION_COUNT = "session_count";
    private static final String KEY_NIGHT_UNLOCK_COUNT = "night_unlock_count";

    // Night window: 10pm – 6am
    private static final int NIGHT_START_HOUR = 22;
    private static final int NIGHT_END_HOUR = 6;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();

        // Auto-reset counters at midnight
        ensureDateFreshness(prefs, now);

        switch (intent.getAction()) {
            case Intent.ACTION_USER_PRESENT:
                handleUnlock(prefs, now);
                break;

            case Intent.ACTION_SCREEN_ON:
                handleScreenOn(prefs, now);
                break;

            case Intent.ACTION_SCREEN_OFF:
                handleScreenOff(prefs, now);
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // EVENT HANDLERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * User unlocked the phone (swipe/PIN/fingerprint/face).
     * This is the definitive "phone pickup" signal.
     */
    private void handleUnlock(SharedPreferences prefs, long now) {
        SharedPreferences.Editor editor = prefs.edit();

        // Increment unlock count
        int count = prefs.getInt(KEY_UNLOCK_COUNT, 0) + 1;
        editor.putInt(KEY_UNLOCK_COUNT, count);

        // Track first unlock of the day (wake-up proxy)
        long firstUnlock = prefs.getLong(KEY_FIRST_UNLOCK_TIME, 0);
        if (firstUnlock == 0) {
            editor.putLong(KEY_FIRST_UNLOCK_TIME, now);
        }

        // Track night unlocks (10pm–6am)
        if (isNightTime(now)) {
            int nightCount = prefs.getInt(KEY_NIGHT_UNLOCK_COUNT, 0) + 1;
            editor.putInt(KEY_NIGHT_UNLOCK_COUNT, nightCount);
        }

        // Increment session count (each unlock = new session start)
        int sessions = prefs.getInt(KEY_SESSION_COUNT, 0) + 1;
        editor.putInt(KEY_SESSION_COUNT, sessions);

        editor.apply();

        Log.d(TAG, "Unlock #" + count + " at " + now
                + (isNightTime(now) ? " [NIGHT]" : ""));
    }

    /**
     * Screen turned on (may not be unlocked yet — could be notification peek).
     */
    private void handleScreenOn(SharedPreferences prefs, long now) {
        SharedPreferences.Editor editor = prefs.edit();

        // Track screen-on count (includes notification peeks, not just unlocks)
        int screenOnCount = prefs.getInt(KEY_SCREEN_ON_COUNT, 0) + 1;
        editor.putInt(KEY_SCREEN_ON_COUNT, screenOnCount);
        editor.putLong(KEY_LAST_SCREEN_ON_TIME, now);

        // Calculate off-screen duration since last screen-off
        long lastOff = prefs.getLong(KEY_LAST_SCREEN_OFF_TIME, 0);
        if (lastOff > 0) {
            long offDuration = now - lastOff;

            // Accumulate total off-screen time
            long totalOff = prefs.getLong(KEY_TOTAL_OFF_DURATION, 0) + offDuration;
            editor.putLong(KEY_TOTAL_OFF_DURATION, totalOff);

            // Track longest off-screen streak (longest phone-free period)
            long longestStreak = prefs.getLong(KEY_LONGEST_OFF_STREAK, 0);
            if (offDuration > longestStreak) {
                editor.putLong(KEY_LONGEST_OFF_STREAK, offDuration);
            }
        }

        editor.apply();
    }

    /**
     * Screen turned off (power button or timeout).
     * This is the most reliable "phone put down" signal.
     */
    private void handleScreenOff(SharedPreferences prefs, long now) {
        prefs.edit()
                .putLong(KEY_LAST_SCREEN_OFF_TIME, now)
                .apply();
    }

    // ─────────────────────────────────────────────────────────────────────
    // DATE MANAGEMENT — Auto-reset at midnight
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Checks if we've crossed midnight since the last event.
     * If so, resets all daily counters. Preserves yesterday's final data
     * in a separate key set for the sync process.
     */
    private void ensureDateFreshness(SharedPreferences prefs, long now) {
        long storedDate = prefs.getLong(KEY_DATE, 0);
        long todayMidnight = getMidnight(now);

        if (storedDate != todayMidnight) {
            // Archive yesterday's data before reset
            SharedPreferences.Editor editor = prefs.edit();

            if (storedDate > 0) {
                // Save yesterday's snapshot for sync
                editor.putInt("prev_unlock_count", prefs.getInt(KEY_UNLOCK_COUNT, 0));
                editor.putLong("prev_first_unlock", prefs.getLong(KEY_FIRST_UNLOCK_TIME, 0));
                editor.putLong("prev_last_screen_off", prefs.getLong(KEY_LAST_SCREEN_OFF_TIME, 0));
                editor.putInt("prev_night_unlocks", prefs.getInt(KEY_NIGHT_UNLOCK_COUNT, 0));
                editor.putInt("prev_screen_on_count", prefs.getInt(KEY_SCREEN_ON_COUNT, 0));
                editor.putInt("prev_session_count", prefs.getInt(KEY_SESSION_COUNT, 0));
                editor.putLong("prev_total_off_duration", prefs.getLong(KEY_TOTAL_OFF_DURATION, 0));
                editor.putLong("prev_longest_off_streak", prefs.getLong(KEY_LONGEST_OFF_STREAK, 0));
                editor.putLong("prev_date", storedDate);
            }

            // Reset for new day
            editor.putLong(KEY_DATE, todayMidnight);
            editor.putInt(KEY_UNLOCK_COUNT, 0);
            editor.putInt(KEY_SCREEN_ON_COUNT, 0);
            editor.putLong(KEY_FIRST_UNLOCK_TIME, 0);
            editor.putLong(KEY_LAST_SCREEN_OFF_TIME, 0);
            editor.putLong(KEY_LAST_SCREEN_ON_TIME, 0);
            editor.putLong(KEY_TOTAL_OFF_DURATION, 0);
            editor.putLong(KEY_LONGEST_OFF_STREAK, 0);
            editor.putInt(KEY_SESSION_COUNT, 0);
            editor.putInt(KEY_NIGHT_UNLOCK_COUNT, 0);

            editor.apply();

            Log.d(TAG, "New day detected — counters reset for " + todayMidnight);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API — For UsageIntelligenceEngine sync
    // ─────────────────────────────────────────────────────────────────────

    /** Get today's data snapshot for syncing into DailyUsage. */
    @NonNull
    public static ScreenEventData getTodayData(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ScreenEventData data = new ScreenEventData();
        data.date = prefs.getLong(KEY_DATE, getMidnight(System.currentTimeMillis()));
        data.unlockCount = prefs.getInt(KEY_UNLOCK_COUNT, 0);
        data.screenOnCount = prefs.getInt(KEY_SCREEN_ON_COUNT, 0);
        data.firstUnlockTime = prefs.getLong(KEY_FIRST_UNLOCK_TIME, 0);
        data.lastScreenOffTime = prefs.getLong(KEY_LAST_SCREEN_OFF_TIME, 0);
        data.nightUnlockCount = prefs.getInt(KEY_NIGHT_UNLOCK_COUNT, 0);
        data.sessionCount = prefs.getInt(KEY_SESSION_COUNT, 0);
        data.totalOffDurationMs = prefs.getLong(KEY_TOTAL_OFF_DURATION, 0);
        data.longestOffStreakMs = prefs.getLong(KEY_LONGEST_OFF_STREAK, 0);
        return data;
    }

    /** Get yesterday's archived data (for sync of previous day). */
    @NonNull
    public static ScreenEventData getYesterdayData(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ScreenEventData data = new ScreenEventData();
        data.date = prefs.getLong("prev_date", 0);
        data.unlockCount = prefs.getInt("prev_unlock_count", 0);
        data.firstUnlockTime = prefs.getLong("prev_first_unlock", 0);
        data.lastScreenOffTime = prefs.getLong("prev_last_screen_off", 0);
        data.nightUnlockCount = prefs.getInt("prev_night_unlocks", 0);
        data.screenOnCount = prefs.getInt("prev_screen_on_count", 0);
        data.sessionCount = prefs.getInt("prev_session_count", 0);
        data.totalOffDurationMs = prefs.getLong("prev_total_off_duration", 0);
        data.longestOffStreakMs = prefs.getLong("prev_longest_off_streak", 0);
        return data;
    }

    /** Create the IntentFilter for registering this receiver. */
    @NonNull
    public static IntentFilter createIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        return filter;
    }

    /**
     * Register this receiver in an Activity or Application.
     * Call in {@code onCreate()}, unregister in {@code onDestroy()}.
     */
    @NonNull
    public static ScreenEventReceiver register(@NonNull Context context) {
        ScreenEventReceiver receiver = new ScreenEventReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, createIntentFilter(),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, createIntentFilter());
        }
        Log.d(TAG, "ScreenEventReceiver registered");
        return receiver;
    }

    // ─────────────────────────────────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────────────────────────────────

    private boolean isNightTime(long timestamp) {
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
     * Snapshot of all screen event data for a single day.
     * Used to sync SharedPreferences data into Room via UsageIntelligenceEngine.
     */
    public static class ScreenEventData {
        /** Day timestamp (midnight). */
        public long date;

        /** Total unlocks (ACTION_USER_PRESENT). */
        public int unlockCount;

        /** Total screen-on events (includes notification peeks). */
        public int screenOnCount;

        /** Timestamp of first unlock of the day (wake-up proxy). */
        public long firstUnlockTime;

        /** Timestamp of last screen-off of the day (bedtime proxy). */
        public long lastScreenOffTime;

        /** Unlocks during 10pm–6am window. */
        public int nightUnlockCount;

        /** Total phone sessions (each unlock = one session). */
        public int sessionCount;

        /** Total milliseconds the screen was off during the day. */
        public long totalOffDurationMs;

        /** Longest continuous off-screen period (phone-free streak). */
        public long longestOffStreakMs;

        /** Whether data has been collected (date > 0). */
        public boolean isValid() {
            return date > 0;
        }

        /** Estimated phone-free hours (total off time as hours). */
        public float getPhoneFreeHours() {
            return totalOffDurationMs / 3600000f;
        }

        /** Longest phone-free streak in minutes. */
        public float getLongestFreeStreakMinutes() {
            return longestOffStreakMs / 60000f;
        }

        @NonNull
        @Override
        public String toString() {
            return "ScreenEventData{" +
                    "unlocks=" + unlockCount +
                    ", nightUnlocks=" + nightUnlockCount +
                    ", screenOns=" + screenOnCount +
                    ", sessions=" + sessionCount +
                    ", phoneFreeH=" + String.format("%.1f", getPhoneFreeHours()) +
                    ", longestFreeMin=" + String.format("%.0f", getLongestFreeStreakMinutes()) +
                    '}';
        }
    }
}
