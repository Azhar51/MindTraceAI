package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.UsageDao;
import com.mindtrace.ai.database.entity.BehaviorUsageSummary;
import com.mindtrace.ai.database.entity.DailyUsage;

import java.util.Calendar;
import java.util.List;

/**
 * Extracts the 14 Digital Behaviour features (D1–D14) from raw usage data.
 *
 * <p>This is the first tier of the MindTrace AI feature extraction pipeline.
 * It converts raw {@link DailyUsage} records into normalized [0.0, 1.0] risk
 * signals that feed into the {@link FeatureVector}.</p>
 *
 * <h3>Feature Map:</h3>
 * <pre>
 *   D1  screenTimeHours      — total daily screen time (0h=0, 8h+=1)
 *   D2  screenTimeDeviation  — % deviation from personal baseline
 *   D3  appSwitchCount       — attention fragmentation (0=0, 150+=1)
 *   D4  rapidSwitchCount     — compulsive switching (0=0, 30+=1)
 *   D5  bingeSessionCount    — extended sessions >30min (0=0, 5+=1)
 *   D6  nightUsageMinutes    — 10pm–6am usage (0=0, 120+=1)
 *   D7  unlockCount          — compulsive checking (0=0, 100+=1)
 *   D8  longestSessionMinutes— peak hyperfocus/absorption (0=0, 120+=1)
 *   D9  dominantAppPercent   — single-app dependency (0%=0, 70%+=1)
 *   D10 fragmentationScore   — from BehaviorUsageSummary (0=0, 10=1)
 *   D11 passiveAppRatio      — passive consumption vs total (0=0, 1=1)
 *   D12 hasLoopPattern       — compulsive loop detected (binary 0/1)
 *   D13 scrollIntensity      — mindless scroll score (0=0, 10=1)
 *   D14 notificationReactivity— fast notification response (0ms=1, 30min+=0)
 * </pre>
 *
 * <h3>Normalization Strategy:</h3>
 * <p>All features use min-max normalization with clinically-informed thresholds.
 * The max values are chosen based on digital wellbeing research — values at or
 * above the max represent concerning behaviour. The result is always clamped
 * to [0.0, 1.0] where <b>higher = greater risk</b>.</p>
 *
 * <h3>Data Sources:</h3>
 * <ul>
 *   <li>{@link DailyUsage} — primary source for D1, D3, D6, D7, D9, D11, D13, D14</li>
 *   <li>{@link BehaviorUsageSummary} — source for D4, D5, D10, D12</li>
 *   <li>Historical {@link DailyUsage} — baseline for D2</li>
 * </ul>
 *
 * @see FeatureVector
 * @see FeatureVector.Builder
 * @see MultiModalClassifier
 */
public class DigitalFeatureExtractor {

    private static final String TAG = "DigitalFE";

    // ═══════════════════════════════════════════════════════════════════
    // NORMALIZATION THRESHOLDS (clinically-informed max values)
    // ═══════════════════════════════════════════════════════════════════

    /** D1: Screen time — 8+ hours/day = maximum risk */
    private static final float MAX_SCREEN_TIME_HOURS = 8.0f;

    /** D2: Deviation from baseline — 100% above baseline = max risk */
    private static final float MAX_DEVIATION_PERCENT = 1.0f;

    /** D3: App switches — 150+/day indicates extreme fragmentation */
    private static final float MAX_APP_SWITCHES = 150f;

    /** D4: Rapid switches (within 3 seconds) — 30+/day = compulsive */
    private static final float MAX_RAPID_SWITCHES = 30f;

    /** D5: Binge sessions (>30 min continuous) — 5+/day = concerning */
    private static final float MAX_BINGE_SESSIONS = 5f;

    /** D6: Night usage — 120+ minutes between 10pm–6am = severe */
    private static final float MAX_NIGHT_USAGE_MINUTES = 120f;

    /** D7: Unlocks — 100+/day = compulsive checking */
    private static final float MAX_UNLOCKS = 100f;

    /** D8: Longest session — 120+ minutes = hyperfocus/absorption */
    private static final float MAX_LONGEST_SESSION_MINUTES = 120f;

    /** D9: Dominant app — 70%+ of screen time in one app = dependency */
    private static final float MAX_DOMINANT_APP_PERCENT = 0.70f;

    /** D10: Fragmentation score — already 0–10 scale */
    private static final float MAX_FRAGMENTATION = 10f;

    /** D13: Scroll intensity — already 0–10 scale from ScrollEventTracker */
    private static final float MAX_SCROLL_INTENSITY = 10f;

    /** D14: Notification response — 30+ minutes avg = low reactivity (healthy) */
    private static final float MAX_NOTIFICATION_RESPONSE_MS = 30f * 60f * 1000f; // 30 minutes

    /** Baseline window: average over the last N days */
    private static final int BASELINE_WINDOW_DAYS = 14;

    // ═══════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════

    private final UsageDao usageDao;
    private final com.mindtrace.ai.database.dao.BehaviorUsageSummaryDao behaviorUsageSummaryDao;

    /**
     * Create extractor with application context.
     * @param context Android context (used to access the database)
     */
    public DigitalFeatureExtractor(@NonNull Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.usageDao = db.usageDao();
        this.behaviorUsageSummaryDao = db.behaviorUsageSummaryDao();
    }

    /**
     * Create extractor with an existing UsageDao (for testing/injection).
     */
    public DigitalFeatureExtractor(@NonNull UsageDao usageDao, @Nullable com.mindtrace.ai.database.dao.BehaviorUsageSummaryDao summaryDao) {
        this.usageDao = usageDao;
        this.behaviorUsageSummaryDao = summaryDao;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAIN EXTRACTION — Tasks 3.B.1 through 3.B.13
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extract all 14 digital features for the given day and apply them
     * to the provided FeatureVector builder.
     *
     * <p>This is the primary API — call this from the classification pipeline
     * to populate D1–D14 in one shot.</p>
     *
     * @param builder  The FeatureVector.Builder to populate
     * @param dayTimestamp Midnight timestamp of the target day
     * @return The same builder (for chaining), with D1–D14 set
     */
    @NonNull
    public FeatureVector.Builder extract(@NonNull FeatureVector.Builder builder, long dayTimestamp) {
        DailyUsage today = usageDao.getUsageForDay(dayTimestamp);
        if (today == null || !today.hasSignificantData()) {
            Log.w(TAG, "No significant usage data for day " + dayTimestamp);
            return builder; // All features stay at default 0.5
        }

        // Load historical data for baseline computation (D2)
        long baselineStart = dayTimestamp - (BASELINE_WINDOW_DAYS * 86400000L);
        List<DailyUsage> history = usageDao.getUsageBetween(baselineStart, dayTimestamp - 1);

        // Load BehaviorUsageSummary for D4, D5, D10, D12
        BehaviorUsageSummary summary = loadBehaviorSummary(dayTimestamp);

        // ── D1: Screen Time Hours (Task 3.B.2) ──────────────────────
        float screenTimeHours = today.getScreenTimeHours();
        builder.d1_screenTimeHours(normalize(screenTimeHours, 0f, MAX_SCREEN_TIME_HOURS));

        // ── D2: Screen Time Deviation (Task 3.B.3) ──────────────────
        float deviation = computeScreenTimeDeviation(today, history);
        builder.d2_screenTimeDeviation(normalize(deviation, 0f, MAX_DEVIATION_PERCENT));

        // ── D3: App Switch Count (Task 3.B.4) ───────────────────────
        builder.d3_appSwitchCount(normalize(today.totalAppSwitchCount, 0f, MAX_APP_SWITCHES));

        // ── D4: Rapid Switch Count (Task 3.B.5) ─────────────────────
        float rapidSwitches = estimateRapidSwitches(today, summary);
        builder.d4_rapidSwitchCount(normalize(rapidSwitches, 0f, MAX_RAPID_SWITCHES));

        // ── D5: Binge Session Count (Task 3.B.6) ────────────────────
        float bingeSessions = estimateBingeSessions(today, summary);
        builder.d5_bingeSessionCount(normalize(bingeSessions, 0f, MAX_BINGE_SESSIONS));

        // ── D6: Night Usage Minutes ─────────────────────────────────
        float nightMinutes = today.getNightUsageMinutes();
        builder.d6_nightUsageMinutes(normalize(nightMinutes, 0f, MAX_NIGHT_USAGE_MINUTES));

        // ── D7: Unlock Count ────────────────────────────────────────
        builder.d7_unlockCount(normalize(today.unlockCount, 0f, MAX_UNLOCKS));

        // ── D8: Longest Session Minutes ─────────────────────────────
        float longestSession = estimateLongestSession(today, summary);
        builder.d8_longestSessionMinutes(normalize(longestSession, 0f, MAX_LONGEST_SESSION_MINUTES));

        // ── D9: Dominant App Percent ────────────────────────────────
        float dominantPercent = computeDominantAppPercent(today);
        builder.d9_dominantAppPercent(normalize(dominantPercent, 0f, MAX_DOMINANT_APP_PERCENT));

        // ── D10: Fragmentation Score ────────────────────────────────
        float fragScore = (summary != null) ? summary.fragmentedUsageScore : estimateFragmentation(today);
        builder.d10_fragmentationScore(normalize(fragScore, 0f, MAX_FRAGMENTATION));

        // ── D11: Passive App Ratio ──────────────────────────────────
        builder.d11_passiveAppRatio(clamp(today.passiveConsumptionRatio));

        // ── D12: Has Loop Pattern ───────────────────────────────────
        boolean hasLoop = detectLoopPattern(today, summary);
        builder.d12_hasLoopPattern(hasLoop ? 1.0f : 0.0f);

        // ── D13: Scroll Intensity (Telemetry) ─────────────────────────
        float scrollScore = today.scrollIntensityScore;
        if (scrollScore > 0f) {
            builder.d13_scrollIntensity(normalize(scrollScore, 0f, MAX_SCROLL_INTENSITY));
        }
        // else: stays at default 0.5 (unknown — AccessibilityService may not be active)

        // ── D14: Notification Reactivity (Telemetry) ────────────────
        long notifAvgMs = today.notificationResponseAvgMs;
        if (notifAvgMs > 0) {
            // Invert: fast response = high risk (notification dependency)
            // 0ms → 1.0 risk, 30min+ → 0.0 risk
            float reactivity = 1.0f - normalize(notifAvgMs, 0f, MAX_NOTIFICATION_RESPONSE_MS);
            builder.d14_notificationReactivity(reactivity);
        }
        // else: stays at default 0.5 (unknown — NotificationListenerService may not be active)

        Log.d(TAG, String.format("Extracted D1-D14: ST=%.1fh, dev=%.0f%%, switches=%d, " +
                        "night=%.0fm, unlocks=%d, passive=%.0f%%, scroll=%.1f, notifMs=%d",
                screenTimeHours, deviation * 100, today.totalAppSwitchCount,
                nightMinutes, today.unlockCount, today.passiveConsumptionRatio * 100,
                scrollScore, notifAvgMs));

        return builder;
    }

    /**
     * Extract features for today.
     */
    @NonNull
    public FeatureVector.Builder extractToday(@NonNull FeatureVector.Builder builder) {
        return extract(builder, getStartOfTodayMillis());
    }

    // ═══════════════════════════════════════════════════════════════════
    // FEATURE COMPUTATION — Individual feature logic
    // ═══════════════════════════════════════════════════════════════════

    /**
     * D2: Compute screen time deviation from personal baseline.
     * Returns (today - baseline) / baseline as a fraction.
     * Positive = above baseline = higher risk.
     * If no history, returns 0 (no deviation detectable).
     */
    private float computeScreenTimeDeviation(@NonNull DailyUsage today,
                                              @Nullable List<DailyUsage> history) {
        if (history == null || history.isEmpty()) return 0f;

        // Compute average baseline screen time
        long totalMs = 0;
        int validDays = 0;
        for (DailyUsage day : history) {
            if (day.hasSignificantData()) {
                totalMs += day.screenTimeMillis;
                validDays++;
            }
        }
        if (validDays == 0) return 0f;

        float baselineMs = (float) totalMs / validDays;
        if (baselineMs <= 0) return 0f;

        // Deviation: how much today exceeds the baseline
        float deviation = (today.screenTimeMillis - baselineMs) / baselineMs;
        return Math.max(0f, deviation); // Only positive deviation = risk
    }

    /**
     * D4: Estimate rapid switch count.
     * If BehaviorUsageSummary has a switchScore, use that.
     * Otherwise, estimate from totalAppSwitchCount (rapid ≈ 20% of total).
     */
    private float estimateRapidSwitches(@NonNull DailyUsage today,
                                         @Nullable BehaviorUsageSummary summary) {
        if (summary != null && summary.switchScore > 0) {
            // switchScore is 0-10; map to estimated rapid count
            return summary.switchScore * 3f; // 10 → 30 rapid switches
        }
        // Heuristic: ~20% of total switches are "rapid" (within 3 seconds)
        return today.totalAppSwitchCount * 0.20f;
    }

    /**
     * D5: Estimate binge session count.
     * If BehaviorUsageSummary has a bingeScore, use that.
     * Otherwise, estimate from screen time (each 30+ min block ≈ 1 binge).
     */
    private float estimateBingeSessions(@NonNull DailyUsage today,
                                         @Nullable BehaviorUsageSummary summary) {
        if (summary != null && summary.bingeScore > 0) {
            // bingeScore is 0-10; map to session count
            return summary.bingeScore * 0.5f; // 10 → 5 sessions
        }
        // Heuristic: estimate binge count from total screen time
        // If 4h total and 30 switches, likely ~3 extended sessions
        float hours = today.getScreenTimeHours();
        if (hours < 1) return 0f;
        if (today.totalAppSwitchCount > 0) {
            // avgSessionLength = totalTime / switches; count sessions > 30min
            float avgSessionMin = (hours * 60f) / Math.max(1, today.totalAppSwitchCount);
            if (avgSessionMin > 30) return hours / 0.5f; // heavy binger
            return Math.max(0, hours - 2f); // rough: 1 binge per hour above 2h
        }
        return Math.max(0, hours - 2f);
    }

    /**
     * D8: Estimate longest session minutes.
     * No direct field in DailyUsage, so estimate from screen time and switches.
     */
    private float estimateLongestSession(@NonNull DailyUsage today,
                                          @Nullable BehaviorUsageSummary summary) {
        if (summary != null && summary.bingeScore > 0) {
            // Binge score correlates with longest session
            return summary.bingeScore * 12f; // 10 → 120 minutes
        }
        // Heuristic: longest session ≈ totalTime / (switches/3 + 1)
        // Fewer switches = longer sessions
        float totalMinutes = today.getScreenTimeHours() * 60f;
        int effectiveSessions = Math.max(1, today.totalAppSwitchCount / 3);
        return totalMinutes / effectiveSessions;
    }

    /**
     * D9: Compute dominant app percentage.
     * Uses category breakdown if available, otherwise falls back to heuristic.
     */
    private float computeDominantAppPercent(@NonNull DailyUsage today) {
        if (today.screenTimeMillis <= 0) return 0f;

        // Use social media + entertainment as proxy for dominant app time
        long dominantMs = Math.max(today.socialMediaTimeMillis, today.entertainmentTimeMillis);
        if (dominantMs > 0) {
            return (float) dominantMs / today.screenTimeMillis;
        }

        // Fallback: if passiveConsumptionRatio is high, dominant app likely exists
        if (today.passiveConsumptionRatio > 0.5f) {
            return today.passiveConsumptionRatio * 0.8f; // Estimate
        }

        // No data available — estimate from app count
        // Fewer apps tracked = more likely single-app dominance
        if (today.appsTrackedCount > 0 && today.appsTrackedCount <= 3) {
            return 0.6f; // Likely dominant
        }

        return 0.3f; // Default: moderate
    }

    /**
     * D10: Estimate fragmentation score when BehaviorUsageSummary is unavailable.
     * Uses app switch count and screen time ratio.
     */
    private float estimateFragmentation(@NonNull DailyUsage today) {
        // Fragmentation = many short sessions = high switches relative to screen time
        float hours = today.getScreenTimeHours();
        if (hours <= 0) return 0f;
        float switchesPerHour = today.totalAppSwitchCount / hours;
        // 0-10 switches/hr = low frag, 10-30 = moderate, 30+ = high
        return Math.min(10f, switchesPerHour / 3f);
    }

    /**
     * D12: Detect compulsive loop patterns.
     * A loop = opening the same few apps repeatedly in short cycles.
     * Estimated from: high switches + high passive ratio + low app diversity.
     */
    private boolean detectLoopPattern(@NonNull DailyUsage today,
                                       @Nullable BehaviorUsageSummary summary) {
        if (summary != null && summary.distractionPatternScore > 5) {
            return true; // BehaviorUsageSummary already detected it
        }
        // Heuristic: loop if high switches + high passive + few unique apps
        boolean highSwitches = today.totalAppSwitchCount > 80;
        boolean highPassive = today.passiveConsumptionRatio > 0.6f;
        boolean lowDiversity = today.appsTrackedCount > 0 && today.appsTrackedCount <= 5;
        return highSwitches && highPassive && lowDiversity;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA LOADING
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    private BehaviorUsageSummary loadBehaviorSummary(long dayTimestamp) {
        try {
            return behaviorUsageSummaryDao == null ? null : behaviorUsageSummaryDao.getSummaryForDaySync(dayTimestamp);
        } catch (Exception e) {
            Log.w(TAG, "BehaviorUsageSummary not available: " + e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NORMALIZATION HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Min-max normalize a value to [0.0, 1.0].
     * Values at or above max are clamped to 1.0.
     * Values at or below min are clamped to 0.0.
     */
    private static float normalize(float value, float min, float max) {
        if (max <= min) return 0f;
        return clamp((value - min) / (max - min));
    }

    /** Clamp a value to [0.0, 1.0]. */
    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Get midnight timestamp for today. */
    private static long getStartOfTodayMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
