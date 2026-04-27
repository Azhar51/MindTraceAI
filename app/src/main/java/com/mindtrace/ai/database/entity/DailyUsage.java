package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a single day's complete digital behaviour snapshot.
 *
 * <p>This is the foundational data entity for Module 1 (Digital Behaviour Monitoring).
 * Each row captures everything MindTrace observed about the user's smartphone
 * interaction for one calendar day — screen time, app switching, unlock behaviour,
 * night usage, category breakdown, and passive consumption metrics.</p>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   UsageStatsManager → UsageIntelligenceEngine → DailyUsage (this entity)
 *                                                       ↓
 *                                               DigitalFeatureExtractor
 *                                                       ↓
 *                                              FeatureVector (12 digital features)
 *                                                       ↓
 *                                              MultiModalClassifier
 * </pre>
 *
 * <h3>Indexing Strategy:</h3>
 * <ul>
 *   <li>{@code date} — unique index, one row per day</li>
 *   <li>{@code snapshotCreatedAt} — for ordering by creation time</li>
 * </ul>
 *
 * @see com.mindtrace.ai.database.dao.UsageDao
 * @see com.mindtrace.ai.usage.UsageIntelligenceEngine
 */
@Entity(
        tableName = "daily_usage",
        indices = {
                @Index(value = {"date"}, unique = true),
                @Index(value = {"snapshotCreatedAt"})
        }
)
public class DailyUsage {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // ─────────────────────────────────────────────────────────────────────
    // TEMPORAL
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Day timestamp — midnight (00:00:00) of the day in local timezone.
     * Used as the logical key for "which day is this data for?"
     */
    @ColumnInfo(name = "date")
    public long date;

    /**
     * When this snapshot was last written/updated by {@code UsageIntelligenceEngine}.
     */
    @ColumnInfo(defaultValue = "0")
    public long snapshotCreatedAt;

    // ─────────────────────────────────────────────────────────────────────
    // SCREEN TIME — Core usage metrics
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Total foreground time across all apps for the day (milliseconds).
     * Source: sum of all {@code UsageStats.getTotalTimeInForeground()}.
     */
    public long screenTimeMillis;

    /**
     * Active foreground time — excludes system UI, launchers, and lock screen.
     * More accurate than {@link #screenTimeMillis} for measuring real engagement.
     */
    @ColumnInfo(defaultValue = "0")
    public long activeForegroundTimeMillis;

    // ─────────────────────────────────────────────────────────────────────
    // APP INTERACTION — Switching, launching, fragmentation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @deprecated Use {@link #totalAppSwitchCount} instead. Kept for migration compatibility.
     */
    @Deprecated
    public int appSwitches;

    /**
     * Total number of app-to-app transitions detected via {@code UsageEvents.MOVE_TO_FOREGROUND}.
     * High values indicate attention fragmentation and dopamine-seeking behaviour.
     * <ul>
     *   <li>Normal: 30-80/day</li>
     *   <li>Elevated: 80-150/day</li>
     *   <li>Extreme: 150+/day</li>
     * </ul>
     */
    @ColumnInfo(defaultValue = "0")
    public int totalAppSwitchCount;

    /**
     * Total number of distinct app launches for the day.
     * Distinct from switches — a launch is the first foreground event for an app.
     */
    @ColumnInfo(defaultValue = "0")
    public int totalLaunchCount;

    // ─────────────────────────────────────────────────────────────────────
    // UNLOCK & PICKUP — Compulsive checking behaviour
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Number of times the user unlocked the phone today.
     * Captured via {@code ACTION_USER_PRESENT} broadcast in {@code ScreenEventReceiver}.
     * <ul>
     *   <li>Healthy: 20-50/day</li>
     *   <li>Elevated: 50-80/day</li>
     *   <li>Compulsive: 80+/day</li>
     * </ul>
     */
    public int unlockCount;

    /**
     * Timestamp of the very first unlock of the day (milliseconds since epoch).
     * Serves as a "wake-up time" proxy for sleep analysis.
     * 0 means not yet recorded for the day.
     */
    @ColumnInfo(defaultValue = "0")
    public long firstUnlockTime;

    /**
     * Timestamp of the last {@code ACTION_SCREEN_OFF} event of the day.
     * Serves as a "bedtime" proxy for sleep analysis.
     * 0 means not yet recorded for the day.
     */
    @ColumnInfo(defaultValue = "0")
    public long lastScreenOffTime;

    // ─────────────────────────────────────────────────────────────────────
    // NIGHT USAGE — Sleep disruption metrics
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Total foreground time during the night window (10:00 PM – 6:00 AM) in milliseconds.
     * Strong predictor of sleep disruption, escape behaviour, and emotional distress.
     *
     * <p>Note: Previously used 12am-5am window. Now expanded to 10pm-6am for
     * better sleep pattern capture.</p>
     */
    public long nightUsageMillis;

    // ─────────────────────────────────────────────────────────────────────
    // TOP APP — Dominance tracking
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Display name of the most-used app (e.g. "Instagram").
     */
    @Nullable
    public String mostUsedApp;

    /**
     * Package name of the most-used app (e.g. "com.instagram.android").
     */
    @Nullable
    @ColumnInfo(defaultValue = "")
    public String topAppPackageName;

    // ─────────────────────────────────────────────────────────────────────
    // APP ECOSYSTEM — Breadth & risk metrics
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Number of distinct apps that had foreground time today.
     */
    @ColumnInfo(defaultValue = "0")
    public int appsTrackedCount;

    /**
     * Number of apps classified as high-risk (social, entertainment, video, gaming).
     */
    @ColumnInfo(defaultValue = "0")
    public int highRiskAppCount;

    // ─────────────────────────────────────────────────────────────────────
    // CONSUMPTION PATTERN — Passive vs active analysis [NEW]
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Ratio of time spent in passive-consumption apps (social, video, news, gaming)
     * versus total screen time. Range: 0.0 (fully productive) to 1.0 (fully passive).
     *
     * <p>Computed by {@code UsageIntelligenceEngine} using {@code AppCategoryMapper}.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public float passiveConsumptionRatio;

    /**
     * Total time in milliseconds spent in productive apps (communication, productivity,
     * education, health) for the day.
     */
    @ColumnInfo(defaultValue = "0")
    public long productiveTimeMillis;

    /**
     * Total time in milliseconds spent in social media apps specifically.
     * Separate from passive ratio because social media has unique dopamine implications.
     */
    @ColumnInfo(defaultValue = "0")
    public long socialMediaTimeMillis;

    /**
     * Total time in milliseconds spent in entertainment/video apps.
     */
    @ColumnInfo(defaultValue = "0")
    public long entertainmentTimeMillis;

    // ─────────────────────────────────────────────────────────────────────
    // ENGAGEMENT QUALITY — Scroll & notification metrics [NEW]
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Composite scroll intensity score for the day (0.0 – 10.0).
     * Higher values indicate more mindless scrolling behaviour.
     *
     * <p>Phase 1: estimated from session patterns (many short sessions = higher score).
     * Phase 3+: measured directly via {@code AccessibilityService} scroll events.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public float scrollIntensityScore;

    /**
     * Average time (ms) between receiving a notification and opening the associated app.
     * Lower values indicate higher notification dependency.
     *
     * <p>Phase 3+: measured via {@code NotificationListenerService}.
     * Until then, defaults to 0 (unknown).</p>
     */
    @ColumnInfo(defaultValue = "0")
    public long notificationResponseAvgMs;

    // ─────────────────────────────────────────────────────────────────────
    // CATEGORY ANALYTICS — Per-category time breakdown [NEW]
    // ─────────────────────────────────────────────────────────────────────

    /**
     * JSON string containing per-category time breakdown for the day.
     *
     * <p>Format: {@code {"SOCIAL":3600000,"VIDEO":1800000,"PRODUCTIVITY":900000,...}}</p>
     *
     * <p>Categories: SOCIAL, VIDEO, ENTERTAINMENT, GAMING, COMMUNICATION,
     * PRODUCTIVITY, EDUCATION, HEALTH, NEWS, UTILITY, OTHER</p>
     *
     * <p>Parsed by {@code AppCategoryMapper} and consumed by the
     * {@code DigitalFeatureExtractor} for feature D11 (passive ratio).</p>
     */
    @Nullable
    public String categoryBreakdownJson;

    /**
     * JSON string containing hourly usage breakdown in 24 buckets (0-23).
     *
     * <p>Format: {@code {"0":120000,"1":0,"2":0,...,"14":600000,...,"23":300000}}</p>
     *
     * <p>Each value is foreground time in milliseconds for that hour.
     * Powers the Usage Dashboard heatmap and time-of-day analysis.</p>
     */
    @Nullable
    public String hourlyBreakdownJson;

    // ─────────────────────────────────────────────────────────────────────
    // CONVENIENCE METHODS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns screen time formatted as hours (e.g., 4.5 for 4h30m).
     */
    public float getScreenTimeHours() {
        return screenTimeMillis / 3600000f;
    }

    /**
     * Returns night usage formatted as minutes.
     */
    public float getNightUsageMinutes() {
        return nightUsageMillis / 60000f;
    }

    /**
     * Returns whether the user has been tracked for a meaningful amount today.
     * Useful for deciding if the day's data is worth classifying.
     */
    public boolean hasSignificantData() {
        return screenTimeMillis > 300000; // > 5 minutes
    }

    /**
     * Returns the "digital diet" score — ratio of productive to total screen time.
     * Range: 0.0 (no productive use) to 1.0 (fully productive).
     */
    public float getDigitalDietScore() {
        if (screenTimeMillis <= 0) return 0f;
        return Math.min(1f, (float) productiveTimeMillis / screenTimeMillis);
    }

    /**
     * Returns estimated waking phone-free hours based on first unlock and last screen off.
     * Returns -1 if data is insufficient.
     */
    public float getPhoneFreeHours() {
        if (firstUnlockTime <= 0 || lastScreenOffTime <= 0) return -1f;
        long awakeWindowMs = lastScreenOffTime - firstUnlockTime;
        if (awakeWindowMs <= 0) return -1f;
        long phoneActiveMs = screenTimeMillis;
        long phoneFreeMs = Math.max(0, awakeWindowMs - phoneActiveMs);
        return phoneFreeMs / 3600000f;
    }

    @NonNull
    @Override
    public String toString() {
        return "DailyUsage{" +
                "date=" + date +
                ", screenTime=" + String.format("%.1fh", getScreenTimeHours()) +
                ", unlocks=" + unlockCount +
                ", switches=" + totalAppSwitchCount +
                ", nightMin=" + String.format("%.0f", getNightUsageMinutes()) +
                ", passiveRatio=" + String.format("%.0f%%", passiveConsumptionRatio * 100) +
                ", topApp='" + mostUsedApp + '\'' +
                '}';
    }
}
