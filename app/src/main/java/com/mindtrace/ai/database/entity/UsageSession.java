package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a single app foreground session — from the moment an app comes
 * to the foreground until it goes to the background.
 *
 * <p>Sessions are the most granular unit of digital behaviour data. They reveal
 * patterns invisible at the daily aggregate level: rapid switching, binge loops,
 * late-night scrolling, and attention fragmentation.</p>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   UsageEvents.MOVE_TO_FOREGROUND → session starts
 *   UsageEvents.MOVE_TO_BACKGROUND → session ends
 *       → UsageIntelligenceEngine builds UsageSession
 *           → BehaviorAnalyzer detects switching/binge/loop patterns
 *               → DigitalFeatureExtractor extracts D3-D5, D8, D12
 * </pre>
 *
 * <h3>Session Classification:</h3>
 * <ul>
 *   <li><b>Micro</b>: &lt;30s — likely accidental or reflexive check</li>
 *   <li><b>Short</b>: 30s–2min — quick check/notification response</li>
 *   <li><b>Normal</b>: 2–15min — typical intentional use</li>
 *   <li><b>Extended</b>: 15–30min — deep engagement</li>
 *   <li><b>Binge</b>: &gt;30min — potential dopamine loop</li>
 * </ul>
 *
 * @see com.mindtrace.ai.database.dao.UsageSessionDao
 * @see com.mindtrace.ai.behavior.BehaviorAnalyzer
 */
@Entity(
        tableName = "usage_sessions",
        indices = {
                @Index(value = {"dayTimestamp"}),
                @Index(value = {"packageName"}),
                @Index(value = {"dayTimestamp", "packageName"}),
                @Index(value = {"sessionType"})
        }
)
public class UsageSession {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // ─────────────────────────────────────────────────────────────────────
    // IDENTITY — When, where, how long
    // ─────────────────────────────────────────────────────────────────────

    /** Day timestamp (midnight) this session belongs to. */
    public long dayTimestamp;

    /** Package name of the app that was in the foreground. */
    @Nullable
    public String packageName;

    /** Timestamp when the app came to the foreground (epoch ms). */
    public long sessionStart;

    /** Timestamp when the app went to the background (epoch ms). */
    public long sessionEnd;

    /** Duration of this session in milliseconds ({@code sessionEnd - sessionStart}). */
    public long durationMillis;

    // ─────────────────────────────────────────────────────────────────────
    // CLASSIFICATION FLAGS — Session behaviour type
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Whether this was a short session (&lt;2 minutes).
     * High count of short sessions indicates attention fragmentation.
     */
    public boolean wasShortSession;

    /**
     * Whether this session occurred during the night window (10pm–6am).
     * Night sessions are weighted heavily in sleep disruption scoring.
     */
    public boolean wasLateNightSession;

    /**
     * Session type classification based on the app's category.
     *
     * <p>Values:</p>
     * <ul>
     *   <li>{@code "passive"} — Social, video, entertainment, gaming, news</li>
     *   <li>{@code "active"} — Productivity, education, communication, health</li>
     *   <li>{@code "utility"} — System, settings, launcher</li>
     *   <li>{@code "unknown"} — Unclassified app</li>
     * </ul>
     *
     * <p>Set by {@code UsageIntelligenceEngine} using {@code AppCategoryMapper}.</p>
     */
    @Nullable
    @ColumnInfo(defaultValue = "unknown")
    public String sessionType;

    // ─────────────────────────────────────────────────────────────────────
    // CONTEXT — What happened during this session
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Number of times this session was interrupted by another app and then
     * the user returned to this app. High values suggest multitasking or
     * notification-driven interruptions.
     *
     * <p>Computed by {@code BehaviorAnalyzer} by detecting foreground→background→foreground
     * patterns for the same package within a session window.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public int interruptionCount;

    /**
     * Size classification of this session based on duration.
     *
     * <p>Values: {@code "micro"} (&lt;30s), {@code "short"} (30s–2min),
     * {@code "normal"} (2–15min), {@code "extended"} (15–30min),
     * {@code "binge"} (&gt;30min)</p>
     */
    @Nullable
    @ColumnInfo(defaultValue = "normal")
    public String durationCategory;

    /**
     * Whether this session was preceded by a notification from the same app.
     * True indicates the session was notification-triggered (reactive behaviour).
     * False or null indicates self-initiated (proactive behaviour).
     *
     * <p>Phase 3+: populated via {@code NotificationListenerService}.
     * Until then, defaults to false.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public boolean wasNotificationTriggered;

    /**
     * The package name of the app that was in the foreground immediately
     * BEFORE this session. Null for the first session of the day.
     *
     * <p>Used by {@code BehaviorAnalyzer} to detect app-switching patterns
     * and loop detection (A→B→A→B cycles).</p>
     */
    @Nullable
    public String previousAppPackage;

    // ─────────────────────────────────────────────────────────────────────
    // CONVENIENCE METHODS
    // ─────────────────────────────────────────────────────────────────────

    /** Returns duration in seconds. */
    public float getDurationSeconds() {
        return durationMillis / 1000f;
    }

    /** Returns duration in minutes. */
    public float getDurationMinutes() {
        return durationMillis / 60000f;
    }

    /** Whether this qualifies as a binge session (> 30 minutes). */
    public boolean isBingeSession() {
        return durationMillis > 1800000; // 30 min
    }

    /** Whether this is a micro session (< 30 seconds). */
    public boolean isMicroSession() {
        return durationMillis < 30000;
    }

    /** Whether this was a reflexive check (< 15 seconds). */
    public boolean isReflexiveCheck() {
        return durationMillis < 15000;
    }

    /**
     * Computes the duration category from the raw duration.
     * Call this after setting {@link #durationMillis}.
     */
    public void computeDurationCategory() {
        if (durationMillis < 30000) {
            durationCategory = "micro";
        } else if (durationMillis < 120000) {
            durationCategory = "short";
        } else if (durationMillis < 900000) {
            durationCategory = "normal";
        } else if (durationMillis < 1800000) {
            durationCategory = "extended";
        } else {
            durationCategory = "binge";
        }
    }

    /**
     * Returns the hour of day (0-23) when this session started.
     * Useful for heatmap and time-of-day analysis.
     */
    public int getStartHour() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(sessionStart);
        return cal.get(java.util.Calendar.HOUR_OF_DAY);
    }

    /**
     * Returns gap in milliseconds between this session's end and the given
     * next session's start. Used for rapid-switch detection.
     */
    public long getGapToNext(UsageSession next) {
        return next.sessionStart - this.sessionEnd;
    }

    @NonNull
    @Override
    public String toString() {
        return "UsageSession{" +
                "pkg='" + packageName + '\'' +
                ", dur=" + String.format("%.1fmin", getDurationMinutes()) +
                ", type=" + sessionType +
                ", cat=" + durationCategory +
                ", night=" + wasLateNightSession +
                ", interruptions=" + interruptionCount +
                '}';
    }
}
