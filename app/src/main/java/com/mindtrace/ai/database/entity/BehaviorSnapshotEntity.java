package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Comprehensive daily behavioural intelligence snapshot.
 *
 * <p>This is the AI-processed summary of a day's digital behaviour — not raw data,
 * but computed signals that feed directly into the {@code MultiModalClassifier}.
 * Each field represents a specific behavioural dimension that contributes to
 * one or more of the 6 risk categories.</p>
 *
 * <h3>Signal Architecture:</h3>
 * <pre>
 *   Raw Data (DailyUsage + UsageSession + AppUsageSnapshot)
 *       → BehaviorAnalyzer.analyze()
 *           → BehaviorSnapshotEntity (this entity)
 *               ├→ DigitalFeatureExtractor (12 normalized features)
 *               ├→ AnomalyDetector (z-score deviations)
 *               └→ InsightEngine (human-readable insights)
 * </pre>
 *
 * <h3>Three Intelligence Layers:</h3>
 * <ol>
 *   <li><b>Attention Layer</b> — switching, fragmentation, attention span</li>
 *   <li><b>Consumption Layer</b> — passive/active, category, binge patterns</li>
 *   <li><b>Circadian Layer</b> — night usage, morning grab, bedtime scrolling</li>
 * </ol>
 *
 * @see com.mindtrace.ai.behavior.BehaviorAnalyzer
 * @see com.mindtrace.ai.ai.DigitalFeatureExtractor
 */
@Entity(
        tableName = "behavior_snapshots",
        indices = {
                @Index(value = {"dayTimestamp"}, unique = true),
                @Index(value = {"timestamp"}),
                @Index(value = {"overallBehaviorRiskScore"})
        }
)
public class BehaviorSnapshotEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // ─────────────────────────────────────────────────────────────────────
    // TEMPORAL
    // ─────────────────────────────────────────────────────────────────────

    /** Day timestamp (midnight) — one snapshot per day. */
    public long dayTimestamp;

    /** Exact timestamp when this snapshot was computed. */
    public long timestamp;

    // ═════════════════════════════════════════════════════════════════════
    // LAYER 1: ATTENTION INTELLIGENCE
    // Measures focus quality, switching compulsion, and attention span
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Total app-to-app transitions detected.
     * Raw count from {@code UsageEvents.MOVE_TO_FOREGROUND}.
     */
    public int appSwitchCount;

    /**
     * Rapid switches: >3 app transitions within a 2-minute window.
     * Strong indicator of dopamine-seeking restlessness.
     * Feeds Feature D4.
     */
    public int rapidSwitchCount;

    /**
     * Fragmentation index: ratio of micro+short sessions to total sessions.
     * Range: 0.0 (all sessions are long/focused) to 1.0 (all sessions are fragmented).
     *
     * <p>Formula: {@code (microSessions + shortSessions) / totalSessions}</p>
     * <p>Threshold: >0.6 = attention fragmentation concern.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public float fragmentationIndex;

    /**
     * Average session length across all apps for the day (milliseconds).
     * Declining trend over days indicates shrinking attention span.
     *
     * <p>Healthy: >5min | Fragmented: 2-5min | Severe: <2min</p>
     */
    @ColumnInfo(defaultValue = "0")
    public long attentionSpanAvgMs;

    /**
     * Compulsive checking score: frequency of reflexive phone interactions.
     * Computed from ratio of <15-second sessions to total sessions.
     * Range: 0.0 (no compulsive checking) to 1.0 (highly compulsive).
     */
    @ColumnInfo(defaultValue = "0")
    public float compulsiveCheckScore;

    /**
     * Total interruptions across all sessions — how many times the user
     * left an app and came back to it. High values suggest inability to
     * maintain focus on a single task.
     */
    @ColumnInfo(defaultValue = "0")
    public int totalInterruptions;

    /**
     * Number of times the phone was unlocked today.
     * Tracked via {@code ScreenEventReceiver}.
     */
    @ColumnInfo(defaultValue = "0")
    public int unlockCount;

    // ═════════════════════════════════════════════════════════════════════
    // LAYER 2: CONSUMPTION INTELLIGENCE
    // Measures what the user consumes and how deeply
    // ═════════════════════════════════════════════════════════════════════

    /** Total foreground time across all apps (milliseconds). */
    public long totalForegroundMillis;

    /** Number of binge sessions (>30min continuous) across all apps. */
    public int bingeSessionCount;

    /** Duration of the longest single continuous session today (milliseconds). */
    public long longestSessionMillis;

    /**
     * Whether a loop pattern was detected: A→B→A→B cycling between
     * the same two apps repeatedly. Classic dopamine loop behaviour.
     */
    public boolean hasLoopPattern;

    /**
     * The A→B app pair if a loop was detected.
     * Format: "com.instagram.android↔com.twitter.android"
     * Null if no loop detected.
     */
    @Nullable
    @ColumnInfo(defaultValue = "")
    public String loopAppPair;

    /**
     * Package name of the app that consumed the most time today.
     */
    @Nullable
    public String dominantAppPackage;

    /**
     * Ratio of passive consumption time to total screen time.
     * Range: 0.0 (fully productive) to 1.0 (fully passive).
     * Feeds Feature D11.
     */
    @ColumnInfo(defaultValue = "0")
    public float passiveConsumptionRatio;

    /**
     * Digital diet score: productive time / (productive + passive time).
     * Range: 0.0 (no productive use) to 1.0 (fully productive).
     * Inverse relationship with passiveConsumptionRatio.
     */
    @ColumnInfo(defaultValue = "0")
    public float digitalDietScore;

    /**
     * Time spent in social media apps specifically (milliseconds).
     * Isolated because social media has unique dopamine implications.
     */
    @ColumnInfo(defaultValue = "0")
    public long socialMediaTimeMillis;

    /**
     * Time spent in productivity/education apps (milliseconds).
     */
    @ColumnInfo(defaultValue = "0")
    public long productiveAppMinutes;

    /**
     * Time spent in entertainment/video apps (milliseconds).
     */
    @ColumnInfo(defaultValue = "0")
    public long entertainmentTimeMillis;

    /**
     * The dominant app category for the day (e.g. "SOCIAL", "VIDEO").
     */
    @Nullable
    @ColumnInfo(defaultValue = "")
    public String dominantCategory;

    /**
     * App diversity score: measures how spread usage is across apps.
     * Range: 0.0 (single app dominated) to 1.0 (even spread).
     * Computed using Shannon entropy normalization.
     *
     * <p>Low diversity + passive category = deep dopamine loop on one app.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public float appDiversityScore;

    /**
     * Scroll intensity score (0.0–10.0).
     * Phase 1: estimated from session patterns.
     * Phase 3+: measured via AccessibilityService scroll events.
     */
    @ColumnInfo(defaultValue = "0")
    public float scrollIntensityScore;

    // ═════════════════════════════════════════════════════════════════════
    // LAYER 3: CIRCADIAN INTELLIGENCE
    // Measures sleep-adjacent behaviour patterns
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Total foreground time during the night window (10pm–6am) in milliseconds.
     * Primary sleep disruption signal.
     */
    public long lateNightUsageMillis;

    /**
     * Time from first unlock to first session lasting >2 minutes (milliseconds).
     * Short values = immediate phone grab upon waking.
     * Long values = user takes time before engaging.
     *
     * <p>Healthy: >15min | Reflexive: 5-15min | Compulsive: <5min</p>
     * <p>0 = not tracked or insufficient data.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public long morningPhoneGrabMs;

    /**
     * Total screen time in the 30 minutes before the last screen-off of the day (ms).
     * High values indicate bedtime scrolling which disrupts sleep quality.
     *
     * <p>Good: 0 | Moderate: <15min | Poor: >15min</p>
     * <p>0 = not tracked or insufficient data.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public long bedtimeScrollMs;

    /**
     * Number of distinct sessions that occurred after 10pm.
     * Even short late-night sessions disrupt melatonin production.
     */
    @ColumnInfo(defaultValue = "0")
    public int lateNightSessionCount;

    // ═════════════════════════════════════════════════════════════════════
    // LAYER 4: ESCAPE BEHAVIOUR INTELLIGENCE
    // Detects digital coping mechanisms for emotional distress
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Escape behaviour score: did passive app usage spike after a high-stress
     * check-in? Detects using the phone as emotional numbing.
     * Range: 0.0 (no escape pattern) to 1.0 (clear escape behaviour).
     *
     * <p>Computed by comparing passive app time in the 2 hours following a
     * check-in where stress ≥ 4 against the user's baseline passive time.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public float escapeBehaviorScore;

    /**
     * Whether today's usage pattern suggests the phone was used as an
     * avoidance mechanism — high passive ratio + high binge count + low
     * productive time on a day with elevated stress.
     */
    @ColumnInfo(defaultValue = "0")
    public boolean isAvoidanceDayFlag;

    // ═════════════════════════════════════════════════════════════════════
    // COMPOSITE SCORE & NARRATIVE
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Overall behavioural risk score — a single composite of all signals above.
     * Range: 0.0 (exemplary digital habits) to 1.0 (severely unhealthy patterns).
     *
     * <p>Weighted formula:
     * 0.20 × fragmentationIndex +
     * 0.15 × compulsiveCheckScore +
     * 0.20 × passiveConsumptionRatio +
     * 0.15 × normalizedNightUsage +
     * 0.10 × normalizedBingeSessions +
     * 0.10 × escapeBehaviorScore +
     * 0.10 × (1 - digitalDietScore)</p>
     *
     * <p>This score is used by the AnomalyDetector for z-score deviation
     * and by the InsightEngine for the overview dashboard risk gauge.</p>
     */
    @ColumnInfo(defaultValue = "0")
    public float overallBehaviorRiskScore;

    /**
     * Human-readable label summarizing today's behaviour.
     * Examples: "Focused & Balanced", "Fragmented", "Binge Day", "Night Owl"
     */
    @Nullable
    public String summaryLabel;

    /**
     * Detailed explanation text generated by InsightEngine.
     * Shown on the insights screen as the "Why" behind the risk score.
     */
    @Nullable
    public String explanation;

    // ─────────────────────────────────────────────────────────────────────
    // CONVENIENCE METHODS
    // ─────────────────────────────────────────────────────────────────────

    /** Returns total foreground time as hours. */
    public float getTotalHours() {
        return totalForegroundMillis / 3600000f;
    }

    /** Returns attention span average as minutes. */
    public float getAttentionSpanMinutes() {
        return attentionSpanAvgMs / 60000f;
    }

    /** Returns night usage as minutes. */
    public float getLateNightMinutes() {
        return lateNightUsageMillis / 60000f;
    }

    /** Returns morning phone grab as minutes. */
    public float getMorningGrabMinutes() {
        return morningPhoneGrabMs / 60000f;
    }

    /** Returns bedtime scroll as minutes. */
    public float getBedtimeScrollMinutes() {
        return bedtimeScrollMs / 60000f;
    }

    /**
     * Returns a risk severity label for the overall score.
     */
    public String getRiskSeverityLabel() {
        if (overallBehaviorRiskScore < 0.2f) return "Excellent";
        if (overallBehaviorRiskScore < 0.4f) return "Good";
        if (overallBehaviorRiskScore < 0.6f) return "Moderate";
        if (overallBehaviorRiskScore < 0.8f) return "Concerning";
        return "Severe";
    }

    /**
     * Returns whether this day shows any crisis-adjacent behavioural signals:
     * extreme screen time + high passive ratio + night usage + escape behaviour.
     */
    public boolean hasCrisisAdjacentSignals() {
        return overallBehaviorRiskScore > 0.8f
                && passiveConsumptionRatio > 0.8f
                && lateNightUsageMillis > 3600000  // >1 hour night
                && escapeBehaviorScore > 0.6f;
    }

    /**
     * Returns whether this is a "green day" — healthy digital patterns.
     */
    public boolean isGreenDay() {
        return overallBehaviorRiskScore < 0.3f
                && digitalDietScore > 0.5f
                && bingeSessionCount == 0
                && fragmentationIndex < 0.3f;
    }

    @NonNull
    @Override
    public String toString() {
        return "BehaviorSnapshot{" +
                "day=" + dayTimestamp +
                ", risk=" + String.format("%.0f%%", overallBehaviorRiskScore * 100) +
                ", frag=" + String.format("%.0f%%", fragmentationIndex * 100) +
                ", passive=" + String.format("%.0f%%", passiveConsumptionRatio * 100) +
                ", diet=" + String.format("%.0f%%", digitalDietScore * 100) +
                ", switches=" + appSwitchCount +
                ", binges=" + bingeSessionCount +
                ", nightMin=" + String.format("%.0f", getLateNightMinutes()) +
                ", label='" + summaryLabel + '\'' +
                '}';
    }
}
