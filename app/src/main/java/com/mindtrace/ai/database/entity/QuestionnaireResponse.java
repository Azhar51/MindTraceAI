package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Daily psychological check-in response — the user's self-reported state.
 *
 * <p>This is the primary psychological data source for the AI classification
 * pipeline. Each response captures 6 dimensions of wellbeing:</p>
 * <ol>
 *   <li><b>Emotional State</b> — mood, anxiety, hope, emotional stability</li>
 *   <li><b>Cognitive State</b> — focus, distraction, mental clarity, rumination</li>
 *   <li><b>Stress & Coping</b> — stress level, coping mechanisms, overwhelm</li>
 *   <li><b>Social Connection</b> — loneliness, social quality, isolation signals</li>
 *   <li><b>Physical Wellbeing</b> — sleep, energy, exercise, appetite</li>
 *   <li><b>Self-Perception</b> — self-worth, purpose, motivation, gratitude</li>
 * </ol>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   DailyCheckInActivity (user input)
 *       → QuestionnaireResponse (this entity)
 *           → PsychFeatureExtractor (10 normalized features P1-P10)
 *               → MultiModalClassifier (risk classification)
 *           → BehaviorAnalyzer.detectEscapeBehavior (stress context)
 *           → InsightEngine (mood trend analysis)
 * </pre>
 *
 * @see com.mindtrace.ai.database.dao.QuestionnaireDao
 */
@Entity(
        tableName = "questionnaire_responses",
        indices = {
                @Index(value = {"timestamp"}),
                @Index(value = {"dayTimestamp"})
        }
)
public class QuestionnaireResponse {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** When the user submitted this check-in. */
    public long timestamp;

    /**
     * Day timestamp (midnight) — enables one-per-day lookups.
     * Multiple check-ins on the same day will share this value.
     */
    @ColumnInfo(defaultValue = "0")
    public long dayTimestamp;

    /**
     * Check-in type: "morning", "evening", or "ad_hoc".
     * Morning check-ins capture sleep quality + anticipation.
     * Evening check-ins capture reflection + gratitude.
     */
    @Nullable
    @ColumnInfo(defaultValue = "")
    public String checkInType;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 1: EMOTIONAL STATE
    // ═════════════════════════════════════════════════════════════════════

    /** Primary mood: "Happy", "Calm", "Neutral", "Anxious", "Sad", "Angry", "Numb". */
    public String mood;

    /**
     * Anxiety level (1–5).
     * 1 = calm/relaxed, 3 = mild worry, 5 = severe anxiety/panic.
     */
    @ColumnInfo(defaultValue = "0")
    public int anxietyLevel;

    /**
     * Hope level (1–5).
     * 1 = hopeless, 3 = neutral, 5 = optimistic about future.
     * Inverse of despair — critical for depression detection.
     */
    @ColumnInfo(defaultValue = "0")
    public int hopeLevel;

    /**
     * Emotional stability (1–5).
     * 1 = volatile/reactive, 5 = emotionally steady.
     * Detects emotional dysregulation patterns.
     */
    @ColumnInfo(defaultValue = "0")
    public int emotionalStability;

    /**
     * Did the user cry or feel like crying today?
     * Binary distress signal — strong predictor of depression.
     */
    @ColumnInfo(defaultValue = "0")
    public boolean feltLikeCrying;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 2: COGNITIVE STATE
    // ═════════════════════════════════════════════════════════════════════

    /** Focus level: "Low", "Medium", "High". */
    public String focusLevel;

    /** Whether the user felt distracted during the day. */
    public Boolean feltDistracted;

    /**
     * Mental clarity (1–5).
     * 1 = foggy/confused, 5 = sharp/clear thinking.
     */
    @ColumnInfo(defaultValue = "0")
    public int mentalClarity;

    /**
     * Urge to scroll endlessly (1-5).
     * 1 = none, 5 = severe cravings to use phone.
     */
    @ColumnInfo(defaultValue = "0")
    public int urgeToScrollLevel;

    /**
     * The biggest distraction encountered today (e.g. "Instagram", "News").
     */
    @Nullable
    public String biggestDistraction;

    /**
     * Rumination score (1–5).
     * 1 = no repetitive thoughts, 5 = stuck in thought loops.
     * Correlates with anxiety and depression.
     */
    @ColumnInfo(defaultValue = "0")
    public int ruminationLevel;

    /**
     * Decision difficulty (1–5).
     * 1 = decisions feel easy, 5 = paralyzed by choices.
     */
    @ColumnInfo(defaultValue = "0")
    public int decisionDifficulty;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 3: STRESS & COPING
    // ═════════════════════════════════════════════════════════════════════

    /** Stress level (1–5). 1 = relaxed, 5 = overwhelmed. */
    public int stressLevel;

    /** Work/academic pressure: "Low", "Medium", "High". */
    public String workPressure;

    /**
     * Feeling of overwhelm (1–5).
     * 1 = in control, 5 = can't cope with demands.
     * More specific than general stress.
     */
    @ColumnInfo(defaultValue = "0")
    public int overwhelmLevel;

    /**
     * Primary coping mechanism used today (if any).
     * Values: "exercise", "social", "meditation", "phone", "food",
     * "substance", "avoidance", "creative", "none".
     * "phone" correlates with escape behaviour detection.
     */
    @Nullable
    @ColumnInfo(defaultValue = "")
    public String copingMechanism;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 4: SOCIAL CONNECTION
    // ═════════════════════════════════════════════════════════════════════

    /** Loneliness level (1–5). 1 = connected, 5 = deeply isolated. */
    public int lonelinessLevel;

    /** Whether the user has social support. */
    public boolean socialSupport;

    /**
     * Social interaction quality (1–5).
     * 1 = negative/draining, 3 = neutral, 5 = fulfilling/energizing.
     * 0 = no social interaction today.
     */
    @ColumnInfo(defaultValue = "0")
    public int socialInteractionQuality;

    /**
     * Number of meaningful in-person conversations today.
     * 0 = none (isolation signal), 3+ = healthy.
     */
    @ColumnInfo(defaultValue = "0")
    public int meaningfulConversations;

    /**
     * Whether the user felt like withdrawing from others.
     * Strong isolation/depression signal.
     */
    @ColumnInfo(defaultValue = "0")
    public boolean wantedToWithdraw;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 5: PHYSICAL WELLBEING
    // ═════════════════════════════════════════════════════════════════════

    /** Hours of sleep last night. */
    public float sleepHours;

    /**
     * Sleep quality (1–5).
     * 1 = terrible/restless, 5 = deep/refreshing.
     * More informative than hours alone.
     */
    @ColumnInfo(defaultValue = "0")
    public int sleepQuality;

    /** Energy level: "Low", "Medium", "High". */
    public String energyLevel;

    /**
     * Whether the user exercised today.
     * Exercise is the strongest protective factor against all risk categories.
     */
    @ColumnInfo(defaultValue = "0")
    public boolean exercisedToday;

    /**
     * Appetite change indicator.
     * "normal", "reduced", "increased", "none" (no appetite).
     */
    @Nullable
    @ColumnInfo(defaultValue = "")
    public String appetiteStatus;

    // ═════════════════════════════════════════════════════════════════════
    // DIMENSION 6: SELF-PERCEPTION & MEANING
    // ═════════════════════════════════════════════════════════════════════

    /** Motivation level (1–5). 1 = none, 5 = driven. */
    public int motivationLevel;

    /** Whether the user has clear goals. */
    public boolean goalClarity;

    /**
     * Self-worth score (1–5).
     * 1 = worthless, 3 = neutral, 5 = valuable/confident.
     * Core depression indicator.
     */
    @ColumnInfo(defaultValue = "0")
    public int selfWorthScore;

    /**
     * Purpose score (1–5).
     * 1 = life feels meaningless, 5 = strong sense of purpose.
     * Correlates with low fulfilment risk category.
     */
    @ColumnInfo(defaultValue = "0")
    public int purposeScore;

    /**
     * Addiction self-awareness (1–10).
     * 1 = "I have no phone addiction", 10 = "I'm severely addicted".
     * Self-reported, feeds directly into digital addiction classifier.
     */
    @ColumnInfo(defaultValue = "0")
    public int addictionSelfScore;

    /**
     * Free-text gratitude entry.
     * Non-null → auto-creates a JournalEntry(type="gratitude").
     */
    @Nullable
    public String gratitudeText;

    /**
     * Free-text: what's weighing on the user's mind right now.
     * This is the single most important field for personalized AI coaching.
     * The coach reads this verbatim to understand the user's real concern.
     */
    @Nullable
    public String currentConcern;

    // ═════════════════════════════════════════════════════════════════════
    // DISTRESS & CRISIS SIGNALS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * JSON array of distress flags detected during check-in.
     * Examples: ["sleep_disruption", "social_withdrawal", "low_energy",
     * "crying", "hopelessness", "appetite_change"].
     * Used by the crisis detection pipeline.
     */
    @Nullable
    public String distressFlags;

    /**
     * Whether the user indicated they want support/help.
     * Triggered by adaptive question: "Would you like to talk to someone?"
     * This is a CRITICAL signal — may trigger crisis intervention flow.
     */
    @ColumnInfo(defaultValue = "0")
    public boolean requestedSupport;

    /**
     * Computed distress severity (0.0–1.0).
     * Auto-calculated from combination of all distress signals.
     * >0.8 = crisis-adjacent, triggers immediate intervention prompt.
     */
    @ColumnInfo(defaultValue = "0")
    public float computedDistressSeverity;

    // ═════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═════════════════════════════════════════════════════════════════════

    /** Maps mood to a numeric risk score (0.0 = low risk, 1.0 = high risk). */
    public float getMoodRisk() {
        if (mood == null) return 0.5f;
        switch (mood) {
            case "Happy": return 0.0f;
            case "Calm": return 0.1f;
            case "Neutral": return 0.3f;
            case "Anxious": return 0.7f;
            case "Sad": return 0.85f;
            case "Angry": return 0.6f;
            case "Numb": return 0.9f;
            default: return 0.5f;
        }
    }

    /** Maps mood to a 0–4 float scale for trend computation. */
    public float getMoodFloat() {
        if (mood == null) return 2.0f;
        switch (mood) {
            case "Happy": return 4.0f;
            case "Calm": return 3.5f;
            case "Neutral": return 3.0f;
            case "Anxious": return 1.5f;
            case "Angry": return 1.5f;
            case "Sad": return 1.0f;
            case "Numb": return 0.5f;
            default: return 2.0f;
        }
    }

    /** Whether this check-in has any crisis-adjacent signals. */
    public boolean hasCrisisSignals() {
        return computedDistressSeverity > 0.8f
                || requestedSupport
                || (hopeLevel > 0 && hopeLevel <= 1 && selfWorthScore > 0 && selfWorthScore <= 1);
    }

    /** Whether this check-in has enough data for meaningful analysis. */
    public boolean hasMinimumData() {
        return mood != null && stressLevel > 0;
    }

    /**
     * Auto-compute distress severity from all signals.
     * Call this before saving to DB.
     */
    public void computeDistressSeverity() {
        float score = 0f;
        int signals = 0;

        // Mood risk (weight: 0.20)
        score += getMoodRisk() * 0.20f;
        signals++;

        // Stress (weight: 0.15)
        if (stressLevel > 0) {
            score += ((stressLevel - 1f) / 4f) * 0.15f;
            signals++;
        }

        // Loneliness (weight: 0.10)
        if (lonelinessLevel > 0) {
            score += ((lonelinessLevel - 1f) / 4f) * 0.10f;
            signals++;
        }

        // Hope (inverted, weight: 0.15)
        if (hopeLevel > 0) {
            score += (1f - (hopeLevel - 1f) / 4f) * 0.15f;
            signals++;
        }

        // Self-worth (inverted, weight: 0.15)
        if (selfWorthScore > 0) {
            score += (1f - (selfWorthScore - 1f) / 4f) * 0.15f;
            signals++;
        }

        // Sleep quality (inverted, weight: 0.10)
        if (sleepQuality > 0) {
            score += (1f - (sleepQuality - 1f) / 4f) * 0.10f;
            signals++;
        }

        // Binary signals boost
        if (feltLikeCrying) score += 0.05f;
        if (wantedToWithdraw) score += 0.05f;
        if (requestedSupport) score += 0.05f;

        computedDistressSeverity = Math.min(1f, Math.max(0f, score));
    }

    /**
     * Build distress flags JSON from current field values.
     * Call this before saving to DB.
     */
    public void buildDistressFlags() {
        java.util.List<String> flags = new java.util.ArrayList<>();

        if (sleepHours < 5 || (sleepQuality > 0 && sleepQuality <= 2))
            flags.add("sleep_disruption");
        if (lonelinessLevel >= 4 || wantedToWithdraw)
            flags.add("social_withdrawal");
        if ("Low".equals(energyLevel))
            flags.add("low_energy");
        if (feltLikeCrying)
            flags.add("crying");
        if (hopeLevel > 0 && hopeLevel <= 2)
            flags.add("hopelessness");
        if ("reduced".equals(appetiteStatus) || "none".equals(appetiteStatus))
            flags.add("appetite_change");
        if (stressLevel >= 4)
            flags.add("high_stress");
        if (ruminationLevel >= 4)
            flags.add("rumination");
        if (selfWorthScore > 0 && selfWorthScore <= 2)
            flags.add("low_self_worth");
        if ("phone".equals(copingMechanism) || "substance".equals(copingMechanism))
            flags.add("unhealthy_coping");

        if (!flags.isEmpty()) {
            org.json.JSONArray arr = new org.json.JSONArray(flags);
            distressFlags = arr.toString();
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "CheckIn{" +
                "mood='" + mood + '\'' +
                ", stress=" + stressLevel +
                ", anxiety=" + anxietyLevel +
                ", hope=" + hopeLevel +
                ", worth=" + selfWorthScore +
                ", sleep=" + sleepHours + "h(q" + sleepQuality + ")" +
                ", distress=" + String.format("%.0f%%", computedDistressSeverity * 100) +
                ", crisis=" + hasCrisisSignals() +
                '}';
    }
}
