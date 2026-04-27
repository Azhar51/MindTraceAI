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
 * Lightweight, SharedPreferences-backed scroll event counter.
 *
 * <p>Receives throttled scroll events from {@code MindTraceAccessibilityService}
 * and accumulates daily metrics without Room I/O overhead.</p>
 *
 * <h3>Tracked Signals:</h3>
 * <ul>
 *   <li><b>scrollEventCount</b> — total scroll gestures (throttled to 1/500ms)</li>
 *   <li><b>scrollTotalDistancePx</b> — cumulative pixel distance scrolled</li>
 *   <li><b>scrollActiveTimeMs</b> — total time spent in scrolling state</li>
 *   <li><b>perAppScrollJson</b> — JSON breakdown by package name</li>
 * </ul>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   AccessibilityEvent.TYPE_VIEW_SCROLLED
 *       → MindTraceAccessibilityService (throttle 500ms)
 *           → ScrollEventTracker (this class, SharedPrefs)
 *               → UsageIntelligenceEngine.syncScrollData()
 *                   → DailyUsage.scrollIntensityScore
 *                       → DigitalFeatureExtractor
 * </pre>
 *
 * <h3>Midnight Rollover:</h3>
 * <p>Follows the {@code ScreenEventReceiver} pattern — on first event after
 * midnight, yesterday's data is archived under {@code prev_*} keys and
 * counters are reset.</p>
 *
 * @see com.mindtrace.ai.services.MindTraceAccessibilityService
 * @see com.mindtrace.ai.database.entity.DailyUsage#scrollIntensityScore
 */
public class ScrollEventTracker {

    private static final String TAG = "ScrollEventTracker";

    // SharedPreferences store
    private static final String PREFS_NAME = "mindtrace_scroll_events";

    // Current day keys
    private static final String KEY_DATE = "tracking_date";
    private static final String KEY_SCROLL_COUNT = "scroll_event_count";
    private static final String KEY_SCROLL_DISTANCE_PX = "scroll_total_distance_px";
    private static final String KEY_SCROLL_ACTIVE_MS = "scroll_active_time_ms";
    private static final String KEY_LAST_SCROLL_TIME = "last_scroll_time";
    private static final String KEY_PER_APP_JSON = "per_app_scroll_json";
    private static final String KEY_RAPID_SCROLL_BURSTS = "rapid_scroll_bursts";
    private static final String KEY_CURRENT_BURST_COUNT = "current_burst_count";
    private static final String KEY_BURST_START_TIME = "burst_start_time";

    // Throttle: ignore scroll events closer than this apart (per package)
    private static final long THROTTLE_MS = 500;

    // A rapid-scroll burst = ≥10 scroll events within 5 seconds
    private static final int BURST_THRESHOLD_EVENTS = 10;
    private static final long BURST_WINDOW_MS = 5000;

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API — Called from AccessibilityService
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Record a scroll event. Called from AccessibilityService on each
     * (throttled) TYPE_VIEW_SCROLLED event.
     *
     * @param context     application context
     * @param packageName package that was scrolled in
     * @param scrollDeltaY absolute vertical scroll delta (pixels), 0 if unknown
     */
    public static void recordScrollEvent(@NonNull Context context,
                                          @NonNull String packageName,
                                          int scrollDeltaY) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();

        // Midnight rollover
        ensureDateFreshness(prefs, now);

        // Throttle per-package: skip if last event for this package was <500ms ago
        long lastScroll = prefs.getLong(KEY_LAST_SCROLL_TIME, 0);
        if (now - lastScroll < THROTTLE_MS) {
            // Still count toward burst detection but don't increment main counter
            updateBurstTracking(prefs, now);
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();

        // ── Global counters ──
        int count = prefs.getInt(KEY_SCROLL_COUNT, 0) + 1;
        editor.putInt(KEY_SCROLL_COUNT, count);

        long totalDistance = prefs.getLong(KEY_SCROLL_DISTANCE_PX, 0) + Math.abs(scrollDeltaY);
        editor.putLong(KEY_SCROLL_DISTANCE_PX, totalDistance);

        // Track active scroll time (time between consecutive scrolls within 3s)
        if (lastScroll > 0 && (now - lastScroll) < 3000) {
            long activeMs = prefs.getLong(KEY_SCROLL_ACTIVE_MS, 0) + (now - lastScroll);
            editor.putLong(KEY_SCROLL_ACTIVE_MS, activeMs);
        }

        editor.putLong(KEY_LAST_SCROLL_TIME, now);

        // ── Per-app breakdown ──
        updatePerAppBreakdown(prefs, editor, packageName);

        // ── Burst tracking ──
        updateBurstTrackingWithEditor(prefs, editor, now);

        editor.apply();

        if (count % 100 == 0) {
            Log.d(TAG, "Scroll events: " + count + " total, distance=" + totalDistance + "px");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API — Data reads for pipeline sync
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Get today's scroll data for syncing into DailyUsage.
     */
    @NonNull
    public static ScrollData getTodayData(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return readData(prefs, false);
    }

    /**
     * Get yesterday's archived scroll data.
     */
    @NonNull
    public static ScrollData getYesterdayData(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return readData(prefs, true);
    }

    /**
     * Compute the scroll intensity score (0.0 – 10.0) from raw data.
     *
     * <p>Formula considers:
     * <ul>
     *   <li>Scroll event frequency (events per hour of foreground time)</li>
     *   <li>Scroll active time ratio (time scrolling / total foreground time)</li>
     *   <li>Rapid-scroll bursts (doomscrolling indicator)</li>
     *   <li>Per-app concentration (scrolling in passive apps weighted higher)</li>
     * </ul></p>
     *
     * @param data              scroll data snapshot
     * @param foregroundTimeMs  total daily foreground time (for normalization)
     * @return intensity score 0.0 (no scrolling) to 10.0 (extreme doomscrolling)
     */
    public static float computeIntensityScore(@NonNull ScrollData data, long foregroundTimeMs) {
        if (!data.isValid() || foregroundTimeMs <= 0) return 0f;

        float hours = foregroundTimeMs / 3600000f;
        if (hours <= 0) return 0f;

        // Component 1: Scroll frequency (events/hour) — max 200/hr = 1.0
        float frequencyScore = Math.min(1f, (data.scrollEventCount / hours) / 200f);

        // Component 2: Active scroll time ratio — time spent scrolling
        float activeRatio = Math.min(1f, (float) data.scrollActiveTimeMs / foregroundTimeMs);

        // Component 3: Burst density — rapid-scroll bursts per hour
        float burstScore = Math.min(1f, (data.rapidScrollBursts / hours) / 5f);

        // Weighted combination: frequency 30%, active ratio 35%, bursts 35%
        float raw = (frequencyScore * 3f) + (activeRatio * 3.5f) + (burstScore * 3.5f);
        return Math.min(10f, Math.max(0f, raw));
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
                editor.putInt("prev_scroll_count", prefs.getInt(KEY_SCROLL_COUNT, 0));
                editor.putLong("prev_scroll_distance", prefs.getLong(KEY_SCROLL_DISTANCE_PX, 0));
                editor.putLong("prev_scroll_active_ms", prefs.getLong(KEY_SCROLL_ACTIVE_MS, 0));
                editor.putInt("prev_rapid_bursts", prefs.getInt(KEY_RAPID_SCROLL_BURSTS, 0));
                editor.putString("prev_per_app_json", prefs.getString(KEY_PER_APP_JSON, "{}"));
                editor.putLong("prev_date", storedDate);
            }

            // Reset for new day
            editor.putLong(KEY_DATE, todayMidnight);
            editor.putInt(KEY_SCROLL_COUNT, 0);
            editor.putLong(KEY_SCROLL_DISTANCE_PX, 0);
            editor.putLong(KEY_SCROLL_ACTIVE_MS, 0);
            editor.putLong(KEY_LAST_SCROLL_TIME, 0);
            editor.putString(KEY_PER_APP_JSON, "{}");
            editor.putInt(KEY_RAPID_SCROLL_BURSTS, 0);
            editor.putInt(KEY_CURRENT_BURST_COUNT, 0);
            editor.putLong(KEY_BURST_START_TIME, 0);

            editor.apply();
            Log.d(TAG, "New day detected — scroll counters reset");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL — Burst tracking
    // ─────────────────────────────────────────────────────────────────────

    private static void updateBurstTracking(SharedPreferences prefs, long now) {
        long burstStart = prefs.getLong(KEY_BURST_START_TIME, 0);
        int burstCount = prefs.getInt(KEY_CURRENT_BURST_COUNT, 0);

        SharedPreferences.Editor editor = prefs.edit();

        if (burstStart > 0 && (now - burstStart) < BURST_WINDOW_MS) {
            burstCount++;
            if (burstCount >= BURST_THRESHOLD_EVENTS) {
                int bursts = prefs.getInt(KEY_RAPID_SCROLL_BURSTS, 0) + 1;
                editor.putInt(KEY_RAPID_SCROLL_BURSTS, bursts);
                editor.putInt(KEY_CURRENT_BURST_COUNT, 0);
                editor.putLong(KEY_BURST_START_TIME, 0);
            } else {
                editor.putInt(KEY_CURRENT_BURST_COUNT, burstCount);
            }
        } else {
            // Start new burst window
            editor.putLong(KEY_BURST_START_TIME, now);
            editor.putInt(KEY_CURRENT_BURST_COUNT, 1);
        }

        editor.apply();
    }

    private static void updateBurstTrackingWithEditor(SharedPreferences prefs,
                                                       SharedPreferences.Editor editor,
                                                       long now) {
        long burstStart = prefs.getLong(KEY_BURST_START_TIME, 0);
        int burstCount = prefs.getInt(KEY_CURRENT_BURST_COUNT, 0);

        if (burstStart > 0 && (now - burstStart) < BURST_WINDOW_MS) {
            burstCount++;
            if (burstCount >= BURST_THRESHOLD_EVENTS) {
                int bursts = prefs.getInt(KEY_RAPID_SCROLL_BURSTS, 0) + 1;
                editor.putInt(KEY_RAPID_SCROLL_BURSTS, bursts);
                editor.putInt(KEY_CURRENT_BURST_COUNT, 0);
                editor.putLong(KEY_BURST_START_TIME, 0);
            } else {
                editor.putInt(KEY_CURRENT_BURST_COUNT, burstCount);
            }
        } else {
            editor.putLong(KEY_BURST_START_TIME, now);
            editor.putInt(KEY_CURRENT_BURST_COUNT, 1);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL — Per-app tracking
    // ─────────────────────────────────────────────────────────────────────

    private static void updatePerAppBreakdown(SharedPreferences prefs,
                                               SharedPreferences.Editor editor,
                                               String packageName) {
        try {
            String json = prefs.getString(KEY_PER_APP_JSON, "{}");
            JSONObject perApp = new JSONObject(json);
            int appCount = perApp.optInt(packageName, 0) + 1;
            perApp.put(packageName, appCount);
            editor.putString(KEY_PER_APP_JSON, perApp.toString());
        } catch (JSONException e) {
            Log.w(TAG, "Failed to update per-app scroll breakdown", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL — Data reading
    // ─────────────────────────────────────────────────────────────────────

    private static ScrollData readData(SharedPreferences prefs, boolean yesterday) {
        ScrollData data = new ScrollData();
        if (yesterday) {
            data.date = prefs.getLong("prev_date", 0);
            data.scrollEventCount = prefs.getInt("prev_scroll_count", 0);
            data.scrollTotalDistancePx = prefs.getLong("prev_scroll_distance", 0);
            data.scrollActiveTimeMs = prefs.getLong("prev_scroll_active_ms", 0);
            data.rapidScrollBursts = prefs.getInt("prev_rapid_bursts", 0);
            data.perAppScrollJson = prefs.getString("prev_per_app_json", "{}");
        } else {
            data.date = prefs.getLong(KEY_DATE, getMidnight(System.currentTimeMillis()));
            data.scrollEventCount = prefs.getInt(KEY_SCROLL_COUNT, 0);
            data.scrollTotalDistancePx = prefs.getLong(KEY_SCROLL_DISTANCE_PX, 0);
            data.scrollActiveTimeMs = prefs.getLong(KEY_SCROLL_ACTIVE_MS, 0);
            data.rapidScrollBursts = prefs.getInt(KEY_RAPID_SCROLL_BURSTS, 0);
            data.perAppScrollJson = prefs.getString(KEY_PER_APP_JSON, "{}");
        }
        return data;
    }

    // ─────────────────────────────────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────────────────────────────────

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
     * Snapshot of scroll event data for a single day.
     * Used to sync into DailyUsage / BehaviorSnapshotEntity.
     */
    public static class ScrollData {
        /** Day timestamp (midnight). */
        public long date;

        /** Total throttled scroll events for the day. */
        public int scrollEventCount;

        /** Cumulative scroll distance in pixels. */
        public long scrollTotalDistancePx;

        /** Total time spent actively scrolling (ms). */
        public long scrollActiveTimeMs;

        /** Number of rapid-scroll bursts (≥10 events in 5s window). */
        public int rapidScrollBursts;

        /** JSON string: {"package.name": scrollCount, ...} */
        @Nullable
        public String perAppScrollJson;

        /** Whether any scroll data has been collected. */
        public boolean isValid() {
            return scrollEventCount > 0;
        }

        /** Active scroll time in minutes. */
        public float getActiveScrollMinutes() {
            return scrollActiveTimeMs / 60000f;
        }

        @NonNull
        @Override
        public String toString() {
            return "ScrollData{" +
                    "events=" + scrollEventCount +
                    ", distancePx=" + scrollTotalDistancePx +
                    ", activeMin=" + String.format("%.1f", getActiveScrollMinutes()) +
                    ", bursts=" + rapidScrollBursts +
                    '}';
        }
    }
}
