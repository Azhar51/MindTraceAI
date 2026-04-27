package com.mindtrace.ai.util;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Centralized mood &amp; level conversion engine for the MindTrace intelligence pipeline.
 *
 * <p>Replaces scattered inline conversions across QuestionnaireResponse,
 * BaselineManager, UiFormatting, and PersonalizationEngine with a single
 * source of truth for every mood/level → numeric/visual mapping.</p>
 *
 * <h3>Supported Moods (7):</h3>
 * Happy, Calm, Neutral, Anxious, Sad, Angry, Numb
 *
 * <h3>Supported Levels (3):</h3>
 * High, Medium, Low
 *
 * <h3>Capabilities:</h3>
 * <ul>
 *   <li><b>Numeric</b> — moodToFloat, moodToRisk, levelToFloat, levelToRisk</li>
 *   <li><b>Visual</b> — getMoodEmoji, getMoodColor, getMoodGradient, getLevelColor</li>
 *   <li><b>Clinical</b> — getMoodValence, getMoodArousal, getClinicalSeverity</li>
 *   <li><b>Analysis</b> — getConsecutiveMoodDays, getMoodDistribution, getDominantMood</li>
 *   <li><b>Intervention</b> — getMoodActionLabel, getMoodCopingTip</li>
 * </ul>
 *
 * @see com.mindtrace.ai.database.entity.QuestionnaireResponse
 */
public final class MoodMapper {

    private MoodMapper() { /* utility class */ }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** The 7 canonical mood strings used throughout MindTrace. */
    public static final String MOOD_HAPPY   = "Happy";
    public static final String MOOD_CALM    = "Calm";
    public static final String MOOD_NEUTRAL = "Neutral";
    public static final String MOOD_ANXIOUS = "Anxious";
    public static final String MOOD_SAD     = "Sad";
    public static final String MOOD_ANGRY   = "Angry";
    public static final String MOOD_NUMB    = "Numb";

    public static final String LEVEL_HIGH   = "High";
    public static final String LEVEL_MEDIUM = "Medium";
    public static final String LEVEL_LOW    = "Low";

    /** Mood groups for clinical analysis. */
    public static final Set<String> POSITIVE_MOODS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MOOD_HAPPY, MOOD_CALM)));
    public static final Set<String> NEGATIVE_MOODS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MOOD_SAD, MOOD_ANXIOUS, MOOD_ANGRY, MOOD_NUMB)));
    public static final Set<String> CLINICAL_ALERT_MOODS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MOOD_SAD, MOOD_NUMB)));

    // ── Color palette ────────────────────────────────────────────────
    @ColorInt private static final int COLOR_HAPPY   = Color.parseColor("#4ADE80");
    @ColorInt private static final int COLOR_CALM    = Color.parseColor("#60A5FA");
    @ColorInt private static final int COLOR_NEUTRAL = Color.parseColor("#F5A623");
    @ColorInt private static final int COLOR_ANXIOUS = Color.parseColor("#FF6B6B");
    @ColorInt private static final int COLOR_SAD     = Color.parseColor("#7C8FFF");
    @ColorInt private static final int COLOR_ANGRY   = Color.parseColor("#EF4444");
    @ColorInt private static final int COLOR_NUMB    = Color.parseColor("#6B7280");
    @ColorInt private static final int COLOR_UNKNOWN = Color.parseColor("#9CA3AF");

    @ColorInt private static final int COLOR_LEVEL_HIGH   = Color.parseColor("#22C55E");
    @ColorInt private static final int COLOR_LEVEL_MEDIUM = Color.parseColor("#F59E0B");
    @ColorInt private static final int COLOR_LEVEL_LOW    = Color.parseColor("#EF4444");

    // ═══════════════════════════════════════════════════════════════════
    // TASK 2.H.2 — moodToFloat()
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Converts a mood string to a wellbeing float (0.0–4.0).
     * Higher = better emotional state.
     *
     * <pre>
     *   Happy   → 4.0    Calm    → 3.5    Neutral → 3.0
     *   Anxious → 1.5    Angry   → 1.5    Sad     → 1.0
     *   Numb    → 0.5    null    → 2.5 (default)
     * </pre>
     */
    public static float moodToFloat(@Nullable String mood) {
        if (mood == null) return 2.5f;
        switch (mood) {
            case MOOD_HAPPY:   return 4.0f;
            case MOOD_CALM:    return 3.5f;
            case MOOD_NEUTRAL: return 3.0f;
            case MOOD_ANXIOUS: return 1.5f;
            case MOOD_ANGRY:   return 1.5f;
            case MOOD_SAD:     return 1.0f;
            case MOOD_NUMB:    return 0.5f;
            default:           return 2.5f;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TASK 2.H.3 — moodToRisk()
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Converts a mood string to a risk score (0.0–1.0).
     * Higher = greater mental health risk.
     *
     * <pre>
     *   Happy   → 0.00   Calm    → 0.10   Neutral → 0.30
     *   Angry   → 0.60   Anxious → 0.70   Sad     → 0.85
     *   Numb    → 0.90   null    → 0.50 (default)
     * </pre>
     */
    public static float moodToRisk(@Nullable String mood) {
        if (mood == null) return 0.5f;
        switch (mood) {
            case MOOD_HAPPY:   return 0.00f;
            case MOOD_CALM:    return 0.10f;
            case MOOD_NEUTRAL: return 0.30f;
            case MOOD_ANGRY:   return 0.60f;
            case MOOD_ANXIOUS: return 0.70f;
            case MOOD_SAD:     return 0.85f;
            case MOOD_NUMB:    return 0.90f;
            default:           return 0.50f;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TASK 2.H.4 — levelToFloat() & levelToRisk()
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Converts a level string to a capacity float (1.0–3.0).
     * Higher = better capacity.
     */
    public static float levelToFloat(@Nullable String level) {
        if (level == null) return 2.0f;
        switch (level) {
            case LEVEL_HIGH:   return 3.0f;
            case LEVEL_MEDIUM: return 2.0f;
            case LEVEL_LOW:    return 1.0f;
            default:           return 2.0f;
        }
    }

    /**
     * Converts a level string to a risk score (0.0–1.0).
     * Higher = greater risk (Low capacity = high risk).
     */
    public static float levelToRisk(@Nullable String level) {
        if (level == null) return 0.5f;
        switch (level) {
            case LEVEL_HIGH:   return 0.0f;
            case LEVEL_MEDIUM: return 0.5f;
            case LEVEL_LOW:    return 1.0f;
            default:           return 0.5f;
        }
    }

    /**
     * Normalizes a level to a 0.0–1.0 scale (High=1.0, Low=0.0).
     * Useful for feature vectors.
     */
    public static float levelToNormalized(@Nullable String level) {
        if (level == null) return 0.5f;
        switch (level) {
            case LEVEL_HIGH:   return 1.0f;
            case LEVEL_MEDIUM: return 0.5f;
            case LEVEL_LOW:    return 0.0f;
            default:           return 0.5f;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // VISUAL — Emoji, Color, Gradient
    // ═══════════════════════════════════════════════════════════════════

    /** Returns an emoji for the given mood. */
    @NonNull
    public static String getMoodEmoji(@Nullable String mood) {
        if (mood == null) return "😐";
        switch (mood) {
            case MOOD_HAPPY:   return "😊";
            case MOOD_CALM:    return "😌";
            case MOOD_NEUTRAL: return "😐";
            case MOOD_ANXIOUS: return "😰";
            case MOOD_SAD:     return "😢";
            case MOOD_ANGRY:   return "😠";
            case MOOD_NUMB:    return "😶";
            default:           return "😐";
        }
    }

    /** Returns a color int for the given mood. */
    @ColorInt
    public static int getMoodColor(@Nullable String mood) {
        if (mood == null) return COLOR_UNKNOWN;
        switch (mood) {
            case MOOD_HAPPY:   return COLOR_HAPPY;
            case MOOD_CALM:    return COLOR_CALM;
            case MOOD_NEUTRAL: return COLOR_NEUTRAL;
            case MOOD_ANXIOUS: return COLOR_ANXIOUS;
            case MOOD_SAD:     return COLOR_SAD;
            case MOOD_ANGRY:   return COLOR_ANGRY;
            case MOOD_NUMB:    return COLOR_NUMB;
            default:           return COLOR_UNKNOWN;
        }
    }

    /** Returns a color for the given level. */
    @ColorInt
    public static int getLevelColor(@Nullable String level) {
        if (level == null) return COLOR_UNKNOWN;
        switch (level) {
            case LEVEL_HIGH:   return COLOR_LEVEL_HIGH;
            case LEVEL_MEDIUM: return COLOR_LEVEL_MEDIUM;
            case LEVEL_LOW:    return COLOR_LEVEL_LOW;
            default:           return COLOR_UNKNOWN;
        }
    }

    /**
     * Returns a pair of gradient colors [startColor, endColor] for the mood.
     * Useful for card backgrounds, chart fills, and timeline markers.
     */
    @ColorInt
    public static int[] getMoodGradient(@Nullable String mood) {
        int base = getMoodColor(mood);
        int faded = Color.argb(80, Color.red(base), Color.green(base), Color.blue(base));
        return new int[]{base, faded};
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLINICAL — Valence, Arousal, Severity
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns emotional valence (-1.0 to +1.0).
     * Negative = unpleasant, Positive = pleasant.
     * Used by the affect circumplex model.
     */
    public static float getMoodValence(@Nullable String mood) {
        if (mood == null) return 0f;
        switch (mood) {
            case MOOD_HAPPY:   return 0.9f;
            case MOOD_CALM:    return 0.6f;
            case MOOD_NEUTRAL: return 0.0f;
            case MOOD_ANXIOUS: return -0.6f;
            case MOOD_ANGRY:   return -0.7f;
            case MOOD_SAD:     return -0.8f;
            case MOOD_NUMB:    return -0.5f;
            default:           return 0.0f;
        }
    }

    /**
     * Returns emotional arousal (0.0 to 1.0).
     * Low = deactivated (Numb, Calm), High = activated (Anxious, Angry).
     */
    public static float getMoodArousal(@Nullable String mood) {
        if (mood == null) return 0.5f;
        switch (mood) {
            case MOOD_HAPPY:   return 0.7f;
            case MOOD_CALM:    return 0.2f;
            case MOOD_NEUTRAL: return 0.4f;
            case MOOD_ANXIOUS: return 0.9f;
            case MOOD_ANGRY:   return 0.95f;
            case MOOD_SAD:     return 0.3f;
            case MOOD_NUMB:    return 0.1f;
            default:           return 0.5f;
        }
    }

    /**
     * Returns a clinical severity level for the mood.
     * <pre>
     *   0 = none    (Happy, Calm)
     *   1 = mild    (Neutral)
     *   2 = moderate (Anxious, Angry)
     *   3 = severe  (Sad, Numb)
     * </pre>
     */
    public static int getClinicalSeverity(@Nullable String mood) {
        if (mood == null) return 1;
        switch (mood) {
            case MOOD_HAPPY:
            case MOOD_CALM:    return 0;
            case MOOD_NEUTRAL: return 1;
            case MOOD_ANXIOUS:
            case MOOD_ANGRY:   return 2;
            case MOOD_SAD:
            case MOOD_NUMB:    return 3;
            default:           return 1;
        }
    }

    /** Whether this mood is a negative/distress mood. */
    public static boolean isNegativeMood(@Nullable String mood) {
        return mood != null && NEGATIVE_MOODS.contains(mood);
    }

    /** Whether this mood triggers a clinical alert (depression markers). */
    public static boolean isClinicalAlertMood(@Nullable String mood) {
        return mood != null && CLINICAL_ALERT_MOODS.contains(mood);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANALYSIS — History, Streaks, Distribution
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Counts the number of consecutive days (from end of list) where
     * the mood is in the target set.
     *
     * @param moodHistory ordered list of daily moods (oldest first)
     * @param targetMoods set of moods to count
     * @return consecutive days from the most recent entry
     */
    public static int getConsecutiveMoodDays(
            @Nullable List<String> moodHistory,
            @NonNull Set<String> targetMoods) {
        if (moodHistory == null || moodHistory.isEmpty()) return 0;
        int count = 0;
        for (int i = moodHistory.size() - 1; i >= 0; i--) {
            if (targetMoods.contains(moodHistory.get(i))) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * Returns how many consecutive days the user has been in a
     * negative mood (Sad, Anxious, Angry, Numb) from the end of history.
     */
    public static int getConsecutiveNegativeDays(@Nullable List<String> moodHistory) {
        return getConsecutiveMoodDays(moodHistory, NEGATIVE_MOODS);
    }

    /**
     * Returns the mood distribution as a map of mood → count.
     */
    @NonNull
    public static Map<String, Integer> getMoodDistribution(@Nullable List<String> moods) {
        Map<String, Integer> dist = new HashMap<>();
        if (moods == null) return dist;
        for (String m : moods) {
            if (m != null) dist.merge(m, 1, Integer::sum);
        }
        return dist;
    }

    /**
     * Returns the dominant mood from a list (most frequent).
     * Ties broken by higher risk (worst mood wins).
     */
    @Nullable
    public static String getDominantMood(@Nullable List<String> moods) {
        Map<String, Integer> dist = getMoodDistribution(moods);
        if (dist.isEmpty()) return null;
        String dominant = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> e : dist.entrySet()) {
            if (e.getValue() > maxCount ||
                    (e.getValue() == maxCount && moodToRisk(e.getKey()) > moodToRisk(dominant))) {
                dominant = e.getKey();
                maxCount = e.getValue();
            }
        }
        return dominant;
    }

    /**
     * Computes the average wellbeing float from a list of moods.
     * Returns 2.5 if the list is empty.
     */
    public static float getAverageWellbeing(@Nullable List<String> moods) {
        if (moods == null || moods.isEmpty()) return 2.5f;
        float sum = 0;
        for (String m : moods) sum += moodToFloat(m);
        return sum / moods.size();
    }

    /**
     * Determines the mood trajectory from a history list.
     *
     * @return "improving", "stable", "declining", or "volatile"
     */
    @NonNull
    public static String getMoodTrajectory(@Nullable List<String> moods) {
        if (moods == null || moods.size() < 3) return "stable";

        int n = moods.size();
        int half = n / 2;
        float firstHalfAvg = 0, secondHalfAvg = 0;
        for (int i = 0; i < half; i++) firstHalfAvg += moodToFloat(moods.get(i));
        for (int i = half; i < n; i++) secondHalfAvg += moodToFloat(moods.get(i));
        firstHalfAvg /= half;
        secondHalfAvg /= (n - half);

        // Check volatility: how many mood switches
        int switches = 0;
        for (int i = 1; i < n; i++) {
            if (!safeEquals(moods.get(i), moods.get(i - 1))) switches++;
        }
        float switchRate = switches / (float) (n - 1);
        if (switchRate > 0.8f) return "volatile";

        float delta = secondHalfAvg - firstHalfAvg;
        if (delta > 0.5f) return "improving";
        if (delta < -0.5f) return "declining";
        return "stable";
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERVENTION — Labels & Tips
    // ═══════════════════════════════════════════════════════════════════

    /** Returns a human-readable action label for the mood. */
    @NonNull
    public static String getMoodActionLabel(@Nullable String mood) {
        if (mood == null) return "Check in with yourself";
        switch (mood) {
            case MOOD_HAPPY:   return "Keep the momentum going";
            case MOOD_CALM:    return "Maintain your balance";
            case MOOD_NEUTRAL: return "Check in with yourself";
            case MOOD_ANXIOUS: return "Try a grounding exercise";
            case MOOD_SAD:     return "Be gentle with yourself today";
            case MOOD_ANGRY:   return "Take a mindful pause";
            case MOOD_NUMB:    return "Reconnect with something small";
            default:           return "Check in with yourself";
        }
    }

    /** Returns a contextual coping tip for the mood. */
    @NonNull
    public static String getMoodCopingTip(@Nullable String mood) {
        if (mood == null) return "Take a moment to notice how you feel.";
        switch (mood) {
            case MOOD_HAPPY:
                return "Channel this energy — start a task you've been putting off.";
            case MOOD_CALM:
                return "This is a great time for reflection or journaling.";
            case MOOD_NEUTRAL:
                return "Neutral is okay. Try a 5-minute gratitude exercise.";
            case MOOD_ANXIOUS:
                return "Try 4-7-8 breathing: inhale 4s, hold 7s, exhale 8s.";
            case MOOD_SAD:
                return "Reach out to someone you trust, or write about what you feel.";
            case MOOD_ANGRY:
                return "Step away from screens for 10 minutes. Movement helps.";
            case MOOD_NUMB:
                return "Start small: name 3 things you can see, hear, and touch.";
            default:
                return "Take a moment to notice how you feel.";
        }
    }

    /** Returns a short descriptive label for the mood. */
    @NonNull
    public static String getMoodDescription(@Nullable String mood) {
        if (mood == null) return "Unknown state";
        switch (mood) {
            case MOOD_HAPPY:   return "Feeling positive and energized";
            case MOOD_CALM:    return "Peaceful and balanced";
            case MOOD_NEUTRAL: return "Neither good nor bad";
            case MOOD_ANXIOUS: return "Worried or on edge";
            case MOOD_SAD:     return "Feeling down or low";
            case MOOD_ANGRY:   return "Frustrated or irritated";
            case MOOD_NUMB:    return "Emotionally disconnected";
            default:           return "Unknown state";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPOSITE — Risk scoring from multiple signals
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes an emotional risk score combining mood, energy level,
     * and focus level into a single 0.0–1.0 risk value.
     *
     * <p>Weights: mood 50%, energy 25%, focus 25%.</p>
     */
    public static float computeEmotionalRisk(
            @Nullable String mood,
            @Nullable String energyLevel,
            @Nullable String focusLevel) {
        float moodRisk = moodToRisk(mood) * 0.50f;
        float energyRisk = levelToRisk(energyLevel) * 0.25f;
        float focusRisk = levelToRisk(focusLevel) * 0.25f;
        return Math.min(1.0f, moodRisk + energyRisk + focusRisk);
    }

    /**
     * Returns a risk category label from a 0.0–1.0 risk score.
     */
    @NonNull
    public static String riskToLabel(float risk) {
        if (risk >= 0.75f) return "Critical";
        if (risk >= 0.55f) return "Elevated";
        if (risk >= 0.35f) return "Moderate";
        return "Low";
    }

    /**
     * Returns a color int for a 0.0–1.0 risk score.
     */
    @ColorInt
    public static int riskToColor(float risk) {
        if (risk >= 0.75f) return COLOR_ANGRY;
        if (risk >= 0.55f) return COLOR_ANXIOUS;
        if (risk >= 0.35f) return COLOR_NEUTRAL;
        return COLOR_HAPPY;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED ANALYTICS (Tasks 2.H.5–2.H.8 — Premium Extension)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes mood entropy (0.0–1.0) — a measure of emotional volatility.
     *
     * <p>Uses Shannon entropy normalized to [0,1]. High entropy means the user
     * cycles through many different moods (dysregulation signal). Low entropy
     * means emotional consistency (either stable or stuck).</p>
     *
     * @param moods list of mood strings over a time window
     * @return 0.0 = perfectly stable, 1.0 = maximally volatile
     */
    public static float getMoodEntropy(@Nullable List<String> moods) {
        if (moods == null || moods.size() < 2) return 0f;
        Map<String, Integer> dist = getMoodDistribution(moods);
        int n = moods.size();
        double entropy = 0.0;
        for (int count : dist.values()) {
            double p = count / (double) n;
            if (p > 0) entropy -= p * Math.log(p);
        }
        // Normalize: max entropy = log(numDistinctMoods)
        double maxEntropy = Math.log(Math.min(dist.size(), 7));
        if (maxEntropy <= 0) return 0f;
        return (float) Math.min(1.0, entropy / maxEntropy);
    }

    /**
     * Computes mood velocity — rate of emotional change per day.
     *
     * <p>Positive velocity = improving, negative = declining.
     * Uses a simple linear regression on wellbeing values.</p>
     *
     * @param moods ordered list of daily moods (oldest first)
     * @return change in wellbeing points per day (roughly -1.5 to +1.5)
     */
    public static float getMoodVelocity(@Nullable List<String> moods) {
        if (moods == null || moods.size() < 2) return 0f;
        int n = moods.size();
        // Simple least-squares slope
        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            float x = i;
            float y = moodToFloat(moods.get(i));
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        float denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 0.001f) return 0f;
        return (n * sumXY - sumX * sumY) / denom;
    }

    /**
     * Computes a streak-amplified risk score.
     *
     * <p>Base risk from the current mood is amplified by consecutive
     * negative days. Each consecutive day adds a 10% multiplier,
     * capped at 2.0x. This models how sustained distress compounds
     * mental health risk non-linearly.</p>
     *
     * <pre>
     *   Day 1 Sad  → 0.85 × 1.0 = 0.85
     *   Day 2 Sad  → 0.85 × 1.1 = 0.94
     *   Day 3 Sad  → 0.85 × 1.2 = 1.00 (capped)
     *   Day 5 Numb → 0.90 × 1.4 = 1.00 (capped)
     * </pre>
     *
     * @param currentMood today's mood
     * @param moodHistory past moods (oldest first, not including today)
     * @return amplified risk 0.0–1.0
     */
    public static float getStreakAmplifiedRisk(
            @Nullable String currentMood,
            @Nullable List<String> moodHistory) {
        float baseRisk = moodToRisk(currentMood);
        int streak = getConsecutiveNegativeDays(moodHistory);
        if (isNegativeMood(currentMood)) streak++; // include today
        float multiplier = 1.0f + (streak * 0.10f);
        multiplier = Math.min(2.0f, multiplier);
        return Math.min(1.0f, baseRisk * multiplier);
    }

    /**
     * Returns the ratio of negative moods in a history list (0.0–1.0).
     */
    public static float getNegativeRatio(@Nullable List<String> moods) {
        if (moods == null || moods.isEmpty()) return 0f;
        int neg = 0;
        for (String m : moods) {
            if (isNegativeMood(m)) neg++;
        }
        return neg / (float) moods.size();
    }

    /**
     * Computes a mood stability score (0.0–1.0).
     *
     * <p>Inverse of entropy weighted by the magnitude of mood swings.
     * 1.0 = perfectly stable, 0.0 = wildly volatile.</p>
     */
    public static float getMoodStability(@Nullable List<String> moods) {
        if (moods == null || moods.size() < 2) return 1.0f;
        float entropy = getMoodEntropy(moods);
        // Also factor in average absolute delta between consecutive days
        float totalDelta = 0;
        for (int i = 1; i < moods.size(); i++) {
            totalDelta += Math.abs(moodToFloat(moods.get(i)) - moodToFloat(moods.get(i - 1)));
        }
        float avgDelta = totalDelta / (moods.size() - 1);
        float normalizedDelta = Math.min(1.0f, avgDelta / 3.0f); // max delta ~3.5
        return Math.max(0f, 1.0f - (entropy * 0.5f + normalizedDelta * 0.5f));
    }

    /**
     * Generates a weekly mood summary object from 7 days of mood data.
     */
    @NonNull
    public static WeeklySummary getWeeklySummary(@Nullable List<String> weekMoods) {
        WeeklySummary s = new WeeklySummary();
        if (weekMoods == null || weekMoods.isEmpty()) return s;

        s.dominantMood = getDominantMood(weekMoods);
        s.averageWellbeing = getAverageWellbeing(weekMoods);
        s.negativeRatio = getNegativeRatio(weekMoods);
        s.stability = getMoodStability(weekMoods);
        s.entropy = getMoodEntropy(weekMoods);
        s.velocity = getMoodVelocity(weekMoods);
        s.trajectory = getMoodTrajectory(weekMoods);
        s.consecutiveNegativeDays = getConsecutiveNegativeDays(weekMoods);
        s.totalDays = weekMoods.size();

        // Composite risk
        float baseRisk = moodToRisk(s.dominantMood);
        float entropyBoost = s.entropy * 0.15f;
        float streakBoost = Math.min(0.20f, s.consecutiveNegativeDays * 0.05f);
        s.compositeRisk = Math.min(1.0f, baseRisk + entropyBoost + streakBoost);

        return s;
    }

    /**
     * Interpolates between two mood colors for smooth chart rendering.
     *
     * @param mood1  first mood
     * @param mood2  second mood
     * @param ratio  interpolation factor (0.0 = mood1, 1.0 = mood2)
     * @return blended color
     */
    @ColorInt
    public static int interpolateMoodColor(@Nullable String mood1, @Nullable String mood2, float ratio) {
        int c1 = getMoodColor(mood1);
        int c2 = getMoodColor(mood2);
        ratio = Math.max(0f, Math.min(1f, ratio));
        int a = Math.round(Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * ratio);
        int r = Math.round(Color.red(c1) + (Color.red(c2) - Color.red(c1)) * ratio);
        int g = Math.round(Color.green(c1) + (Color.green(c2) - Color.green(c1)) * ratio);
        int b = Math.round(Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * ratio);
        return Color.argb(a, r, g, b);
    }

    /**
     * Returns a numeric rank for sorting moods by severity (0 = best, 6 = worst).
     */
    public static int getMoodRank(@Nullable String mood) {
        if (mood == null) return 3;
        switch (mood) {
            case MOOD_HAPPY:   return 0;
            case MOOD_CALM:    return 1;
            case MOOD_NEUTRAL: return 2;
            case MOOD_ANGRY:   return 3;
            case MOOD_ANXIOUS: return 4;
            case MOOD_SAD:     return 5;
            case MOOD_NUMB:    return 6;
            default:           return 3;
        }
    }

    /** Returns an emoji for the given level. */
    @NonNull
    public static String getLevelEmoji(@Nullable String level) {
        if (level == null) return "➖";
        switch (level) {
            case LEVEL_HIGH:   return "🟢";
            case LEVEL_MEDIUM: return "🟡";
            case LEVEL_LOW:    return "🔴";
            default:           return "➖";
        }
    }

    /** Returns a human-readable description for a level in context. */
    @NonNull
    public static String getLevelDescription(@Nullable String level, @NonNull String context) {
        if (level == null) return context + ": unknown";
        switch (level) {
            case LEVEL_HIGH:   return context + " is strong";
            case LEVEL_MEDIUM: return context + " is moderate";
            case LEVEL_LOW:    return context + " needs attention";
            default:           return context + ": unknown";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASS — WeeklySummary
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Immutable summary of a week's mood data.
     * Produced by {@link #getWeeklySummary(List)}.
     */
    public static class WeeklySummary {
        public String dominantMood;
        public float averageWellbeing;
        public float negativeRatio;
        public float stability;
        public float entropy;
        public float velocity;
        public String trajectory;
        public int consecutiveNegativeDays;
        public int totalDays;
        public float compositeRisk;

        /** Returns the dominant mood emoji. */
        @NonNull
        public String getEmoji() { return getMoodEmoji(dominantMood); }

        /** Returns the dominant mood color. */
        @ColorInt
        public int getColor() { return getMoodColor(dominantMood); }

        /** Returns a human-readable one-line summary. */
        @NonNull
        public String getLabel() {
            if (compositeRisk >= 0.7f) return "Difficult week — please be kind to yourself";
            if (compositeRisk >= 0.45f) return "Mixed week — some ups and downs";
            if (averageWellbeing >= 3.5f) return "Great week — keep it up!";
            return "Okay week — room to grow";
        }

        @NonNull
        @Override
        public String toString() {
            return "WeeklySummary{" +
                    "mood=" + dominantMood +
                    ", wellbeing=" + String.format("%.1f", averageWellbeing) +
                    ", risk=" + String.format("%.0f%%", compositeRisk * 100) +
                    ", trajectory=" + trajectory +
                    ", stability=" + String.format("%.0f%%", stability * 100) +
                    '}';
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────

    private static boolean safeEquals(@Nullable String a, @Nullable String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
