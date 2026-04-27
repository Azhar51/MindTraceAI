package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Weekly Psychological & Behavioral Assessment — the longitudinal intelligence layer.
 *
 * <p>Unlike the daily check-in ({@link QuestionnaireResponse}) which captures transient
 * emotional states, the Weekly Assessment captures <b>structural patterns</b>:
 * burnout trajectories, isolation trends, purpose erosion, and systemic risk.</p>
 *
 * <h3>7 Dimensions:</h3>
 * <ol>
 *   <li><b>Temporal Scope</b> — exact 7-day window with week number for comparison</li>
 *   <li><b>Reflective State</b> — user's subjective evaluation of the week</li>
 *   <li><b>Emotional Trajectory</b> — mood stability, emotional range, crying days</li>
 *   <li><b>Behavioral Aggregates</b> — system-computed averages snapshotted at assessment time</li>
 *   <li><b>Clinical Markers</b> — burnout, isolation, anhedonia, and purpose tracking</li>
 *   <li><b>Protective Factors</b> — exercise, social connection, gratitude, sleep quality</li>
 *   <li><b>AI Narrative</b> — generated insights, risk trajectory, and intervention plans</li>
 * </ol>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   WeeklyAssessmentWorker (Sunday midnight)
 *       → aggregates 7 days of QuestionnaireResponse + BehaviorSnapshot
 *       → prompts user for reflective input
 *       → WeeklyAssessment (this entity)
 *           → TrendAnalyzer (cross-week regression)
 *           → MultiModalClassifier (longitudinal features W1-W8)
 *           → InsightEngine (weekly narrative generation)
 * </pre>
 *
 * @see QuestionnaireResponse
 * @see BehaviorSnapshotEntity
 */
@Entity(
        tableName = "weekly_assessments",
        indices = {
                @Index(value = {"timestamp"}),
                @Index(value = {"weekStartTimestamp", "weekEndTimestamp"}, unique = true),
                @Index(value = {"weekNumber"})
        }
)
public class WeeklyAssessment {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** When the assessment was completed/generated. */
    public long timestamp;

    /** Start of the 7-day window (midnight Monday). */
    public long weekStartTimestamp;

    /** End of the 7-day window (Sunday 23:59:59). */
    public long weekEndTimestamp;

    /**
     * ISO week number (1-52). Enables year-over-year comparison.
     * Computed from weekStartTimestamp.
     */
    @ColumnInfo(defaultValue = "0")
    public int weekNumber;

    /**
     * Year of the assessment. Combined with weekNumber for unique week identification.
     */
    @ColumnInfo(defaultValue = "0")
    public int year;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 1: SUBJECTIVE REFLECTION (User Input)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * General feeling about the week.
     * "Thriving", "Good", "Okay", "Struggling", "Exhausted", "Crisis"
     */
    public String overallMood;

    /**
     * Primary challenge faced this week.
     * "Distraction", "Anxiety", "Loneliness", "Workload", "Sleep",
     * "Motivation", "Relationships", "Health", "None"
     */
    public String coreStruggle;

    /** Self-reported win or positive moment (free text). */
    @Nullable
    public String primaryWin;

    /** Free-text reflection on the week. */
    @Nullable
    public String weeklyReflection;

    /**
     * What the user wants to focus on next week.
     * Enables goal-tracking across weeks.
     */
    @Nullable
    public String nextWeekIntention;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 2: EMOTIONAL TRAJECTORY
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Emotional stability across the week (1-10).
     * 1 = extreme volatility (mood swings), 10 = emotionally steady.
     * Computed from daily mood variance or self-reported.
     */
    @ColumnInfo(defaultValue = "0")
    public int emotionalStabilityScore;

    /**
     * Number of distinct moods reported across the 7 days.
     * High count (5+) may indicate emotional dysregulation.
     */
    @ColumnInfo(defaultValue = "0")
    public int moodVarietyCount;

    /**
     * Number of days with negative mood (Sad, Anxious, Angry, Numb).
     * ≥5 triggers depression trajectory alert.
     */
    @ColumnInfo(defaultValue = "0")
    public int negativeMoodDays;

    /** Number of days the user reported crying or wanting to cry. */
    @ColumnInfo(defaultValue = "0")
    public int cryingDays;

    /**
     * Dominant mood of the week (the most frequent).
     * Provides the "color" of the week for the timeline visualization.
     */
    @Nullable
    public String dominantMood;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 3: CLINICAL MARKERS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Sense of purpose/meaning for the week (1-10).
     * High purpose is the strongest buffer against depression.
     * Tracked across weeks for erosion detection.
     */
    @ColumnInfo(defaultValue = "0")
    public int purposeScore;

    /**
     * Sense of connection to others (1-10).
     * Structural isolation measure — different from daily loneliness.
     * 1 = completely isolated, 10 = deeply connected.
     */
    @ColumnInfo(defaultValue = "0")
    public int socialConnectionScore;

    /**
     * Self-reported burnout/exhaustion risk (1-10).
     * Combines physical exhaustion + emotional depletion + cynicism.
     */
    @ColumnInfo(defaultValue = "0")
    public int burnoutRiskScore;

    /**
     * Anhedonia indicator (1-10).
     * 1 = nothing brings joy, 10 = fully enjoying activities.
     * Core depression marker — loss of pleasure/interest.
     */
    @ColumnInfo(defaultValue = "0")
    public int anhedoniaScore;

    /**
     * Self-efficacy (1-10).
     * 1 = "I can't accomplish anything", 10 = "I can handle challenges".
     * Predicts resilience and recovery trajectory.
     */
    @ColumnInfo(defaultValue = "0")
    public int selfEfficacyScore;

    /**
     * Addiction self-awareness progression (1-10).
     * Tracked weekly for trend — is the user becoming more or less aware?
     */
    @ColumnInfo(defaultValue = "0")
    public int addictionAwarenessScore;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 4: PROTECTIVE FACTORS
    // ═════════════════════════════════════════════════════════════════════

    /** Number of days user reported exercising (0-7). */
    @ColumnInfo(defaultValue = "0")
    public int exerciseDaysCount;

    /** Average sleep hours across the week. */
    @ColumnInfo(defaultValue = "0")
    public float avgSleepHours;

    /** Average sleep quality across the week (1-5). */
    @ColumnInfo(defaultValue = "0")
    public float avgSleepQuality;

    /** Number of days with meaningful social interactions. */
    @ColumnInfo(defaultValue = "0")
    public int socialInteractionDays;

    /** Exercise frequency label: "Never", "1-2x", "3-4x", "Daily". (Task 2.F.6) */
    @Nullable
    public String exerciseFrequency;

    /** Routine stability self-report (1-5). (Task 2.F.5) */
    @ColumnInfo(defaultValue = "0")
    public int routineStabilityScore;

    /** Screen-free activities count (0-5). (Task 2.F.7) */
    @ColumnInfo(defaultValue = "0")
    public int screenFreeActivities;

    /** Quality of social interactions (1-5). (Task 2.F.8) */
    @ColumnInfo(defaultValue = "0")
    public int socialQualityScore;

    /** Number of days with gratitude entries. */
    @ColumnInfo(defaultValue = "0")
    public int gratitudeDays;

    /** Number of days the user completed their journal. */
    @ColumnInfo(defaultValue = "0")
    public int journalDays;

    /**
     * Protective factor composite (0.0-1.0).
     * Auto-computed: exercise + sleep + social + gratitude.
     * High protective factor buffers against risk.
     */
    @ColumnInfo(defaultValue = "0")
    public float protectiveFactorScore;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 5: BEHAVIORAL AGGREGATES (System-Computed)
    // ═════════════════════════════════════════════════════════════════════

    /** Average daily screen time for the week (ms). */
    @ColumnInfo(defaultValue = "0")
    public long avgScreenTimeMs;

    /** Average passive consumption ratio (0.0-1.0). */
    @ColumnInfo(defaultValue = "0")
    public float avgPassiveRatio;

    /** Average digital diet score (0.0-1.0). */
    @ColumnInfo(defaultValue = "0")
    public float avgDigitalDietScore;

    /** Average fragmentation index (0.0-1.0). */
    @ColumnInfo(defaultValue = "0")
    public float avgFragmentationIndex;

    /** Average behavioral risk score (0.0-1.0). */
    @ColumnInfo(defaultValue = "0")
    public float avgBehaviorRiskScore;

    /** Average daily distress severity from QuestionnaireResponses. */
    @ColumnInfo(defaultValue = "0")
    public float avgDistressSeverity;

    /** Number of days where behavioral risk > 0.6. */
    @ColumnInfo(defaultValue = "0")
    public int highRiskDaysCount;

    /** Number of optimal/healthy behavioral days (risk < 0.3). */
    @ColumnInfo(defaultValue = "0")
    public int greenDaysCount;

    /** Average daily unlock count. */
    @ColumnInfo(defaultValue = "0")
    public int avgUnlockCount;

    /** Number of binge sessions across the week. */
    @ColumnInfo(defaultValue = "0")
    public int totalBingeSessions;

    /** Number of escape behavior days (escapeBehaviorScore > 0.5). */
    @ColumnInfo(defaultValue = "0")
    public int escapeBehaviorDays;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 6: WEEK-OVER-WEEK CHANGE (Delta Tracking)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Change in screen time from previous week (ms).
     * Negative = improved, Positive = worsened.
     */
    @ColumnInfo(defaultValue = "0")
    public long screenTimeDeltaMs;

    /**
     * Change in distress severity from previous week.
     * Negative = improved, Positive = worsened.
     */
    @ColumnInfo(defaultValue = "0")
    public float distressDelta;

    /**
     * Change in purpose score from previous week.
     * Positive = improved, Negative = eroding.
     */
    @ColumnInfo(defaultValue = "0")
    public int purposeDelta;

    /**
     * Overall trajectory: "improving", "stable", "declining", "crisis".
     * Set by comparing 3+ weeks of data.
     */
    @Nullable
    public String overallTrajectory;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 7: AI INSIGHTS & INTERVENTION
    // ═════════════════════════════════════════════════════════════════════

    /** Multi-modal AI-generated summary of the week's patterns. */
    @Nullable
    public String generatedInsight;

    /** AI-identified primary risk factor. */
    @Nullable
    public String primaryRiskFactor;

    /** Recommended focus area for next week. */
    @Nullable
    public String suggestedAction;

    /**
     * JSON array of specific AI recommendations.
     * Example: ["Reduce evening screen time by 30min", "Try 10-min meditation before bed"]
     */
    @Nullable
    public String actionRecommendations;

    /**
     * Risk flag: true if aggregates indicate systemic mental health decline.
     * Triggered by: 3+ negative mood days + high distress + declining purpose.
     */
    @ColumnInfo(defaultValue = "0")
    public boolean systemicRiskFlag;

    /**
     * Composite weekly wellness score (0.0-1.0).
     * Computed from all dimensions.
     */
    @ColumnInfo(defaultValue = "0")
    public float weeklyWellnessScore;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 8: NLP ENRICHMENT (Task 2.F.11)
    // ═════════════════════════════════════════════════════════════════════

    /** Sentiment polarity from free-text analysis (-1.0 to 1.0). */
    @ColumnInfo(defaultValue = "0")
    public float nlpSentiment;

    /** JSON array of distress flags detected in free text. */
    @Nullable
    public String nlpDistressFlags;

    /** JSON array of topic tags extracted from free text. */
    @Nullable
    public String nlpTopics;

    // ═════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Compute the weekly wellness score from all available signals.
     * Call this before saving to DB.
     *
     * <p>Formula weights:</p>
     * <pre>
     *   0.15 × (purposeScore / 10)
     *   0.15 × (socialConnectionScore / 10)
     *   0.10 × (1 - burnoutRiskScore / 10)      // inverted
     *   0.10 × (anhedoniaScore / 10)
     *   0.10 × (selfEfficacyScore / 10)
     *   0.15 × protectiveFactorScore
     *   0.10 × (greenDaysCount / 7)
     *   0.15 × (1 - avgBehaviorRiskScore)        // inverted
     * </pre>
     */
    public void computeWeeklyWellnessScore() {
        float score = 0f;

        // Subjective clinical markers (60%)
        if (purposeScore > 0)
            score += (purposeScore / 10f) * 0.15f;
        if (socialConnectionScore > 0)
            score += (socialConnectionScore / 10f) * 0.15f;
        if (burnoutRiskScore > 0)
            score += ((10f - burnoutRiskScore) / 10f) * 0.10f;
        if (anhedoniaScore > 0)
            score += (anhedoniaScore / 10f) * 0.10f;
        if (selfEfficacyScore > 0)
            score += (selfEfficacyScore / 10f) * 0.10f;

        // Protective factors (15%)
        score += protectiveFactorScore * 0.15f;

        // Behavioral health (25%)
        score += (greenDaysCount / 7f) * 0.10f;
        score += (1f - avgBehaviorRiskScore) * 0.15f;

        weeklyWellnessScore = Math.min(1f, Math.max(0f, score));
    }

    /**
     * Compute the protective factor score from lifestyle signals.
     * Call this before computeWeeklyWellnessScore().
     */
    public void computeProtectiveFactorScore() {
        float score = 0f;

        // Exercise (30% weight) — strongest protective factor
        score += (exerciseDaysCount / 7f) * 0.30f;

        // Sleep quality (25% weight)
        if (avgSleepQuality > 0)
            score += (avgSleepQuality / 5f) * 0.25f;

        // Social interaction (25% weight)
        score += (socialInteractionDays / 7f) * 0.25f;

        // Journaling + gratitude (20% weight)
        float reflectiveDays = (gratitudeDays + journalDays) / 14f; // max 7 each
        score += Math.min(1f, reflectiveDays) * 0.20f;

        protectiveFactorScore = Math.min(1f, Math.max(0f, score));
    }

    /**
     * Determine if this week meets criteria for systemic risk.
     * Call this before saving to DB.
     */
    public void evaluateSystemicRisk() {
        int riskSignals = 0;

        if (negativeMoodDays >= 4) riskSignals++;
        if (avgDistressSeverity > 0.6f) riskSignals++;
        if (purposeScore > 0 && purposeScore <= 3) riskSignals++;
        if (socialConnectionScore > 0 && socialConnectionScore <= 3) riskSignals++;
        if (burnoutRiskScore >= 7) riskSignals++;
        if (anhedoniaScore > 0 && anhedoniaScore <= 3) riskSignals++;
        if (highRiskDaysCount >= 4) riskSignals++;
        if (escapeBehaviorDays >= 3) riskSignals++;
        if (cryingDays >= 3) riskSignals++;

        systemicRiskFlag = riskSignals >= 3;

        // Set trajectory
        if (riskSignals >= 5) overallTrajectory = "crisis";
        else if (riskSignals >= 3) overallTrajectory = "declining";
        else if (greenDaysCount >= 5 && protectiveFactorScore > 0.6f) overallTrajectory = "improving";
        else overallTrajectory = "stable";
    }

    /** Whether this is a crisis-level week. */
    public boolean isCrisisWeek() {
        return systemicRiskFlag && "crisis".equals(overallTrajectory);
    }

    /** Whether enough data exists for meaningful analysis. */
    public boolean hasMinimumData() {
        return purposeScore > 0 || socialConnectionScore > 0 || greenDaysCount > 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "Week{" +
                "w" + weekNumber + "/" + year +
                ", mood='" + overallMood + "'" +
                ", purpose=" + purposeScore +
                ", burnout=" + burnoutRiskScore +
                ", wellness=" + String.format("%.0f%%", weeklyWellnessScore * 100) +
                ", trajectory='" + overallTrajectory + "'" +
                ", crisis=" + isCrisisWeek() +
                '}';
    }
}
