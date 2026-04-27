package com.mindtrace.ai.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Persisted record of a suicide risk assessment event.
 *
 * <p>Stores each evaluation from the {@link com.mindtrace.ai.ai.SuicideRiskClassifier}
 * for clinician export, longitudinal tracking, and escalation audit trail.</p>
 *
 * <p><b>Privacy note:</b> This data is sensitive and should only be exported
 * with explicit user consent.</p>
 */
@Entity(tableName = "suicide_risk_events")
public class SuicideRiskEvent {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Timestamp of the assessment. */
    public long timestamp;

    /** C-SSRS tier (0-5). */
    public int csrrsTier;

    /** Human-readable severity label. */
    public String severityLabel;

    /** Tier from text signals alone. */
    public int textTier;

    /** Tier from behavioral signals alone. */
    public int behaviorTier;

    /** Number of active signals detected. */
    public int signalCount;

    /** JSON array of matched keyword phrases. */
    public String matchedPhrasesJson;

    /** JSON array of active signal descriptions. */
    public String activeSignalsJson;

    /** Source: "journal", "check_in", "crisis_screen", "auto_scan". */
    public String source;

    /** Whether lockdown was triggered. */
    public boolean lockdownTriggered;

    /** Whether notification was sent. */
    public boolean notificationSent;

    /** Whether auto-contact was triggered. */
    public boolean autoContactSent;

    /** Distress level at time of assessment (1-10). */
    public int distressLevel;

    /** Mood score at time of assessment (1-10). */
    public int moodScore;

    /** Whether it was nighttime. */
    public boolean isNightTime;

    /** Associated crisis event ID (if any). */
    public long linkedCrisisEventId;

    /** Resolution: how the event was handled. */
    public String resolution;
}
