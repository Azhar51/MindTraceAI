package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a single app's usage data for one calendar day.
 *
 * <p>Each row captures how much time a specific app was in the foreground,
 * how many times it was launched, its category classification, and behavioural
 * flags that feed the AI classification pipeline.</p>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   UsageStatsManager.queryUsageStats()
 *       → UsageIntelligenceEngine.buildAppSnapshots()
 *           → AppUsageSnapshot[] (this entity)
 *               → AppCategoryMapper.getCategory(packageName)
 *               → DigitalFeatureExtractor (D9 dominant%, D11 passive ratio)
 * </pre>
 *
 * <h3>Indexing:</h3>
 * <ul>
 *   <li>Unique composite on (dayTimestamp, packageName) — one row per app per day</li>
 *   <li>Index on dayTimestamp — fast day-level queries</li>
 *   <li>Index on appCategory — fast category-level aggregation</li>
 * </ul>
 *
 * @see com.mindtrace.ai.database.dao.AppUsageSnapshotDao
 * @see com.mindtrace.ai.usage.UsageIntelligenceEngine
 */
@Entity(
        tableName = "app_usage_snapshots",
        indices = {
                @Index(value = {"dayTimestamp", "packageName"}, unique = true),
                @Index(value = {"dayTimestamp"}),
                @Index(value = {"appCategory"})
        }
)
public class AppUsageSnapshot {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // ─────────────────────────────────────────────────────────────────────
    // TEMPORAL
    // ─────────────────────────────────────────────────────────────────────

    /** Day timestamp — midnight of the day this snapshot belongs to. */
    public long dayTimestamp;

    /** When this snapshot was recorded/last updated. */
    public long recordedAt;

    // ─────────────────────────────────────────────────────────────────────
    // APP IDENTITY
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Android package name (e.g. "com.instagram.android").
     * Used as the stable identifier for the app across sessions.
     */
    @NonNull
    public String packageName = "";

    /**
     * Human-readable app name (e.g. "Instagram").
     * Resolved via {@code PackageManager.getApplicationLabel()}.
     */
    @Nullable
    public String appName;

    // ─────────────────────────────────────────────────────────────────────
    // USAGE METRICS — How much and how often
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Total foreground time for this app on this day (milliseconds).
     * Source: {@code UsageStats.getTotalTimeInForeground()}.
     */
    public long usageTimeMillis;

    /**
     * This app's foreground time as a percentage of total screen time (0-100).
     * Useful for dominance detection (Feature D9).
     */
    public int usagePercentage;

    /**
     * Same as {@link #usagePercentage} but computed after filtering system apps.
     * More accurate representation of user-chosen app usage.
     */
    @ColumnInfo(defaultValue = "0")
    public int percentOfTotalUsage;

    /**
     * Number of times this app was launched (brought to foreground from cold/warm start).
     * High launch count with low duration = compulsive checking.
     */
    public int launchCount;

    /**
     * Total number of foreground sessions for this app.
     * Includes both launches and returns from other apps.
     */
    @ColumnInfo(defaultValue = "0")
    public int foregroundSessions;

    /** Timestamp of the first time this app was opened today. */
    public long firstOpenedTimestamp;

    /** Timestamp of the last time this app was used today. */
    public long lastUsedTimestamp;

    // ─────────────────────────────────────────────────────────────────────
    // CATEGORY & CLASSIFICATION
    // ─────────────────────────────────────────────────────────────────────

    /**
     * App category as classified by {@code AppCategoryMapper}.
     *
     * <p>Possible values: SOCIAL, VIDEO, ENTERTAINMENT, GAMING, COMMUNICATION,
     * PRODUCTIVITY, EDUCATION, HEALTH, NEWS, UTILITY, OTHER</p>
     *
     * <p>Used by {@code DigitalFeatureExtractor} to compute Feature D11 (passive ratio)
     * and by the category breakdown analytics on the Usage Dashboard.</p>
     */
    @Nullable
    public String appCategory;

    /** Whether this is a system/pre-installed app (dialer, settings, etc.). */
    public boolean isSystemApp;

    /** Whether this app should be shown to the user in the Usage Dashboard. */
    @ColumnInfo(defaultValue = "1")
    public boolean isUserVisible;

    /**
     * Whether this app is classified as a "passive consumption" app.
     * True for: SOCIAL, VIDEO, ENTERTAINMENT, GAMING, NEWS.
     * False for: COMMUNICATION, PRODUCTIVITY, EDUCATION, HEALTH, UTILITY.
     *
     * <p>This is a denormalized flag derived from {@link #appCategory} for
     * fast query-time aggregation without needing to join or reclassify.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public boolean isPassiveApp;

    // ─────────────────────────────────────────────────────────────────────
    // BEHAVIOURAL FLAGS — AI-relevant signals per app
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Whether this app had at least one "binge session" today (continuous use > 30 minutes).
     * A key signal for dopamine loop detection — feeds into Feature D5.
     */
    @ColumnInfo(defaultValue = "0")
    public boolean bingeFlag;

    /**
     * Average session length for this app today (milliseconds).
     * Short avg + high launch count = compulsive checking.
     * Long avg + single session = deep engagement or binge.
     *
     * <p>Calculated as: {@code usageTimeMillis / max(1, foregroundSessions)}</p>
     */
    @ColumnInfo(defaultValue = "0")
    public long averageSessionLengthMs;

    /**
     * Number of binge sessions (> 30 min continuous) for this specific app today.
     * 0 means no binge sessions detected.
     */
    @ColumnInfo(defaultValue = "0")
    public int bingeSessionCount;

    /**
     * The longest single continuous session for this app today (milliseconds).
     * Useful for identifying which apps trigger the deepest dopamine loops.
     */
    @ColumnInfo(defaultValue = "0")
    public long longestSessionMs;

    /**
     * Percentage of this app's usage that occurred during the night window (10pm-6am).
     * Range: 0-100. High values indicate this app is a "bedtime trap."
     */
    @ColumnInfo(defaultValue = "0")
    public int nightUsagePercent;

    // ─────────────────────────────────────────────────────────────────────
    // CONVENIENCE METHODS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns usage time formatted as minutes.
     */
    public float getUsageMinutes() {
        return usageTimeMillis / 60000f;
    }

    /**
     * Returns usage time formatted as hours.
     */
    public float getUsageHours() {
        return usageTimeMillis / 3600000f;
    }

    /**
     * Returns a computed "compulsive checking score" for this app.
     * High score = many launches with short sessions (checking behaviour).
     * Range: 0.0 (not compulsive) to 1.0 (highly compulsive).
     */
    public float getCompulsiveScore() {
        if (launchCount <= 1 || usageTimeMillis <= 0) return 0f;
        // Many launches with short avg session = compulsive
        float avgSessionMin = (averageSessionLengthMs > 0 ? averageSessionLengthMs : usageTimeMillis / launchCount) / 60000f;
        if (avgSessionMin <= 0) return 0f;
        // Score: launches/10 * (1/avgSessionMinutes), clamped to 0-1
        float raw = (launchCount / 10f) * (1f / avgSessionMin);
        return Math.min(1f, Math.max(0f, raw));
    }

    /**
     * Returns whether this app had significant usage today (> 1 minute).
     */
    public boolean hasSignificantUsage() {
        return usageTimeMillis > 60000;
    }

    /**
     * Returns whether this app is a "trap app" — high usage, passive, with binge behaviour.
     */
    public boolean isTrapApp() {
        return isPassiveApp && bingeFlag && usagePercentage >= 20;
    }

    @NonNull
    @Override
    public String toString() {
        return "AppUsageSnapshot{" +
                "pkg='" + packageName + '\'' +
                ", name='" + appName + '\'' +
                ", time=" + String.format("%.1fmin", getUsageMinutes()) +
                ", pct=" + usagePercentage + "%" +
                ", cat=" + appCategory +
                ", passive=" + isPassiveApp +
                ", binge=" + bingeFlag +
                '}';
    }
}
