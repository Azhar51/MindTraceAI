package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Persistent record of a crisis event for longitudinal safety tracking.
 *
 * <p>Created when CrisisDetector identifies CrisisLevel >= ELEVATED.
 * Tracks the crisis lifecycle from detection through resolution and
 * optional post-crisis debrief.</p>
 *
 * <h3>Crisis Event Lifecycle:</h3>
 * <pre>
 *   CrisisDetector.assess() → CrisisLevel >= ELEVATED
 *       → CrisisEvent created (status = "ACTIVE")
 *           → User engages support resources
 *               → "I'm safe now" pressed → status = "RESOLVED"
 *                   → 4-6h later: PostCrisisDebrief prompted
 * </pre>
 */
@Entity(
        tableName = "crisis_events",
        indices = {
                @Index(value = {"timestamp"}),
                @Index(value = {"crisisLevel"}),
                @Index(value = {"status"})
        }
)
public class CrisisEvent {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** When the crisis was detected. */
    public long timestamp;

    /**
     * Crisis level at detection.
     * Values: "WATCH", "ELEVATED", "URGENT", "CRITICAL"
     */
    @NonNull
    public String crisisLevel = "ELEVATED";

    /**
     * Current status of this crisis event.
     * Values: "ACTIVE", "RESOLVED", "DISMISSED", "ESCALATED"
     */
    @NonNull
    @ColumnInfo(defaultValue = "ACTIVE")
    public String status = "ACTIVE";

    /** JSON array of signal descriptions that triggered detection. */
    @Nullable
    public String triggerSignalsJson;

    /** JSON array of actions the user took during the crisis. */
    @Nullable
    public String actionsTakenJson;

    /** When the crisis was resolved (user pressed "I'm safe now"). */
    @ColumnInfo(defaultValue = "0")
    public long resolvedAt;

    /**
     * How the crisis was resolved.
     * Values: "self_resolved", "grounding_exercise", "breathing_exercise",
     *         "contacted_friend", "called_helpline", "journaled", "dismissed"
     */
    @Nullable
    public String resolutionMethod;

    /** User's mood after resolving the crisis. */
    @Nullable
    public String postCrisisMood;

    /** Distress level before resolution (1-10). */
    @ColumnInfo(defaultValue = "0")
    public int preDistressLevel;

    /** Distress level after resolution (1-10). */
    @ColumnInfo(defaultValue = "0")
    public int postDistressLevel;

    /** Confidence score of the crisis assessment (0.0-1.0). */
    @ColumnInfo(defaultValue = "0")
    public float assessmentConfidence;

    /** Whether a follow-up check-in was scheduled. */
    @ColumnInfo(defaultValue = "0")
    public boolean followUpScheduled;

    /** Whether the post-crisis debrief was completed. */
    @ColumnInfo(defaultValue = "0")
    public boolean debriefCompleted;

    /** Whether the 24h safety check notification has been sent. */
    @ColumnInfo(defaultValue = "0")
    public boolean safetyCheckSent;

    /**
     * C-SSRS (Columbia Suicide Severity Rating Scale) tier at detection time.
     * <ul>
     *   <li>0 = Not evaluated or no risk</li>
     *   <li>1 = Passive ideation (wish to be dead)</li>
     *   <li>2 = Active ideation without plan</li>
     *   <li>3 = Active ideation with plan but no intent</li>
     *   <li>4 = Active ideation with intent (no specific plan)</li>
     *   <li>5 = Active ideation with plan and intent</li>
     * </ul>
     *
     * <p>Used by {@link com.mindtrace.ai.services.CrisisFollowUpWorker}
     * to scale follow-up frequency and notification urgency.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public int severityTier;

    /** Alias for whether event is resolved (convenience for queries). */
    public boolean isResolved() {
        return "RESOLVED".equals(status) || "DISMISSED".equals(status);
    }

    // Compatibility accessors for cross-module use
    /** Whether the event has been resolved. */
    @ColumnInfo(name = "resolved", defaultValue = "0")
    public boolean resolved;

    /** Trigger source identifier. */
    @Nullable
    public String triggerSource;

    /** Alias: crisis level string. */
    public String getLevel() { return crisisLevel; }
    public String level; // Room field alias

    /** Timestamp alias — same as timestamp for backward compat. */
    public long createdAt;

    // ─────────────────────────────────────────────────────────────────────
    // CONVENIENCE METHODS
    // ─────────────────────────────────────────────────────────────────────

    /** Mark this crisis as resolved. */
    public void resolve(@NonNull String method, @Nullable String mood, int postDistress) {
        this.status = "RESOLVED";
        this.resolvedAt = System.currentTimeMillis();
        this.resolutionMethod = method;
        this.postCrisisMood = mood;
        this.postDistressLevel = postDistress;
    }

    /** How long the crisis lasted in milliseconds. 0 if unresolved. */
    public long getDurationMs() {
        if (resolvedAt == 0) return 0;
        return resolvedAt - timestamp;
    }

    /** Whether this crisis is still active (unresolved). */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * Whether this crisis requires intensive follow-up (C-SSRS ≥ 2 or CRITICAL level).
     * Used by CrisisFollowUpWorker to determine 15-min vs 30-min vs 1-hour cadence.
     */
    public boolean requiresIntensiveFollowUp() {
        return severityTier >= 2 || "CRITICAL".equals(crisisLevel) || "URGENT".equals(crisisLevel);
    }

    /**
     * Get the recommended follow-up interval in minutes, scaled by C-SSRS tier.
     * <ul>
     *   <li>Tier 4-5 → 15 min (maximum urgency)</li>
     *   <li>Tier 2-3 or CRITICAL → 30 min</li>
     *   <li>Tier 1 or URGENT/ELEVATED → 45 min</li>
     *   <li>Tier 0 or WATCH → 60 min (standard)</li>
     * </ul>
     */
    public int getRecommendedFollowUpMinutes() {
        if (severityTier >= 4) return 15;
        if (severityTier >= 2 || "CRITICAL".equals(crisisLevel)) return 30;
        if (severityTier >= 1 || "URGENT".equals(crisisLevel)) return 45;
        return 60;
    }

    /** Whether the de-escalation was effective (distress dropped). */
    public boolean wasDeescalationEffective() {
        return postDistressLevel > 0 && preDistressLevel > 0
                && postDistressLevel < preDistressLevel;
    }

    /** Get distress reduction in points. */
    public int getDistressReduction() {
        if (preDistressLevel <= 0 || postDistressLevel <= 0) return 0;
        return preDistressLevel - postDistressLevel;
    }

    @NonNull
    @Override
    public String toString() {
        return "CrisisEvent{" +
                "id=" + id +
                ", level=" + crisisLevel +
                ", status=" + status +
                ", duration=" + (getDurationMs() / 60000) + "min" +
                '}';
    }
}
