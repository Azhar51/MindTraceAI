package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.JournalDao;
import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.WeeklyAssessmentDao;
import com.mindtrace.ai.database.entity.JournalEntry;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.WeeklyAssessment;

import java.util.Calendar;
import java.util.List;

/**
 * Extracts the 10 Psychological features (P1–P10) from check-in and assessment data.
 *
 * <p>Second tier of the MindTrace AI pipeline. Converts self-reported psychological
 * state into normalized [0.0, 1.0] risk signals for the {@link FeatureVector}.</p>
 *
 * <h3>Feature Map (higher = greater risk):</h3>
 * <pre>
 *   P1  moodRisk             — mood string → risk (Happy=0, Numb=0.9)
 *   P2  stressLevel          — (stress-1)/4
 *   P3  lonelinessLevel      — (loneliness-1)/4
 *   P4  motivationDeficit    — INVERTED: 1-(motivation-1)/4
 *   P5  sleepDeficit          — INVERTED: 1-(sleep-3)/5, quality-weighted
 *   P6  energyDeficit         — High→0, Medium→0.5, Low→1.0
 *   P7  focusDeficit          — High→0, Medium→0.5, Low→1.0
 *   P8  purposeDeficit        — from WeeklyAssessment, INVERTED
 *   P9  addictionSelfScore   — (val-1)/9
 *   P10 consecutiveSadDays   — count/5, clamped
 * </pre>
 *
 * <h3>Advanced Capabilities:</h3>
 * <ul>
 *   <li><b>Multi-signal fusion:</b> Combines daily check-in + weekly assessment + journal sentiment</li>
 *   <li><b>Stale data decay:</b> Data older than 3 days decays toward 0.5 (uncertain)</li>
 *   <li><b>Cross-signal amplification:</b> Co-occurring distress signals boost each other</li>
 *   <li><b>Mood trajectory:</b> Tracks improving vs declining mood over recent history</li>
 *   <li><b>Emotional turbulence:</b> Detects rapid mood swings as instability signal</li>
 *   <li><b>Resilience estimation:</b> Protective factors dampen raw risk scores</li>
 * </ul>
 *
 * @see FeatureVector
 * @see MultiModalClassifier
 */
public class PsychFeatureExtractor {

    private static final String TAG = "PsychFE";

    /** Maximum days before check-in data is considered stale */
    private static final long STALE_THRESHOLD_MS = 3L * 24 * 60 * 60 * 1000; // 3 days

    /** Decay factor applied per day of staleness (0.85 = 15% decay/day) */
    private static final float STALE_DECAY_PER_DAY = 0.85f;

    /** P5: Sleep normalization range — 3h (terrible) to 8h (optimal) */
    private static final float SLEEP_MIN_HOURS = 3f;
    private static final float SLEEP_MAX_HOURS = 8f;

    /** P10: Consecutive sad days — 5+ days = max risk */
    private static final float MAX_CONSECUTIVE_SAD_DAYS = 5f;

    /** Mood trajectory window — how many recent check-ins to analyze */
    private static final int TRAJECTORY_WINDOW = 5;

    // ═══════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════

    private final QuestionnaireDao questionnaireDao;
    private final WeeklyAssessmentDao weeklyAssessmentDao;
    private final JournalDao journalDao;

    public PsychFeatureExtractor(@NonNull Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.questionnaireDao = db.questionnaireDao();
        this.weeklyAssessmentDao = db.weeklyAssessmentDao();
        this.journalDao = db.journalDao();
    }

    /** Constructor for testing/injection. */
    public PsychFeatureExtractor(@NonNull QuestionnaireDao qDao,
                                  @NonNull WeeklyAssessmentDao wDao,
                                  @NonNull JournalDao jDao) {
        this.questionnaireDao = qDao;
        this.weeklyAssessmentDao = wDao;
        this.journalDao = jDao;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAIN EXTRACTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extract all 10 psychological features and apply to the builder.
     *
     * @param builder      FeatureVector builder to populate P1–P10
     * @param dayTimestamp  Midnight timestamp of the target day
     * @return Same builder with P1–P10 set
     */
    @NonNull
    public FeatureVector.Builder extract(@NonNull FeatureVector.Builder builder, long dayTimestamp) {
        // Load primary data: today's check-in (or closest available)
        QuestionnaireResponse checkIn = loadBestCheckIn(dayTimestamp);
        WeeklyAssessment weekly = loadLatestWeeklyAssessment();
        float staleFactor = computeStaleFactor(checkIn, dayTimestamp);

        if (checkIn == null) {
            Log.w(TAG, "No check-in data available — using defaults (0.5)");
            return builder; // All P-features stay at default 0.5
        }

        // Load history for trajectory + consecutive sad days
        List<QuestionnaireResponse> recent = questionnaireDao.getRecentResponses(TRAJECTORY_WINDOW);
        int consecutiveSadDays = countConsecutiveSadDays(recent);
        float moodTrajectory = computeMoodTrajectory(recent);
        float turbulence = computeEmotionalTurbulence(recent);

        // Load journal sentiment for cross-validation
        float journalSentiment = loadJournalSentiment(dayTimestamp);

        // ── P1: Mood Risk ───────────────────────────────────────────
        float p1 = computeMoodRisk(checkIn, moodTrajectory, journalSentiment);
        builder.p1_moodRisk(applyStale(p1, staleFactor));

        // ── P2: Stress Level ────────────────────────────────────────
        float p2 = computeStressRisk(checkIn);
        builder.p2_stressLevel(applyStale(p2, staleFactor));

        // ── P3: Loneliness Level ────────────────────────────────────
        float p3 = computeLonelinessRisk(checkIn);
        builder.p3_lonelinessLevel(applyStale(p3, staleFactor));

        // ── P4: Motivation Deficit ──────────────────────────────────
        float p4 = computeMotivationDeficit(checkIn, weekly);
        builder.p4_motivationDeficit(applyStale(p4, staleFactor));

        // ── P5: Sleep Deficit ───────────────────────────────────────
        float p5 = computeSleepDeficit(checkIn);
        builder.p5_sleepDeficit(applyStale(p5, staleFactor));

        // ── P6: Energy Deficit ──────────────────────────────────────
        float p6 = computeEnergyDeficit(checkIn);
        builder.p6_energyDeficit(applyStale(p6, staleFactor));

        // ── P7: Focus Deficit ───────────────────────────────────────
        float p7 = computeFocusDeficit(checkIn);
        builder.p7_focusDeficit(applyStale(p7, staleFactor));

        // ── P8: Purpose Deficit ─────────────────────────────────────
        float p8 = computePurposeDeficit(checkIn, weekly);
        builder.p8_purposeDeficit(applyStale(p8, staleFactor));

        // ── P9: Addiction Self Score ─────────────────────────────────
        float p9 = computeAddictionScore(checkIn, weekly);
        builder.p9_addictionSelfScore(applyStale(p9, staleFactor));

        // ── P10: Consecutive Sad Days ───────────────────────────────
        float p10 = clamp(consecutiveSadDays / MAX_CONSECUTIVE_SAD_DAYS);
        builder.p10_consecutiveSadDays(p10); // No stale decay — this is historical

        // ── Advanced: Cross-signal amplification ────────────────────
        float amplification = computeAmplification(p1, p2, p3, p5, turbulence);
        if (amplification > 0) {
            // Boost the top 3 risk signals by the amplification factor
            builder.p1_moodRisk(clamp(applyStale(p1, staleFactor) + amplification * 0.15f));
            builder.p2_stressLevel(clamp(applyStale(p2, staleFactor) + amplification * 0.10f));
            builder.p3_lonelinessLevel(clamp(applyStale(p3, staleFactor) + amplification * 0.10f));
        }

        Log.d(TAG, String.format("P1-P10: mood=%.2f stress=%.2f lonely=%.2f motiv=%.2f " +
                        "sleep=%.2f energy=%.2f focus=%.2f purpose=%.2f addict=%.2f sadDays=%.2f " +
                        "[stale=%.2f, amp=%.2f, turb=%.2f, traj=%.2f]",
                p1, p2, p3, p4, p5, p6, p7, p8, p9, p10,
                staleFactor, amplification, turbulence, moodTrajectory));

        return builder;
    }

    /** Extract features for today. */
    @NonNull
    public FeatureVector.Builder extractToday(@NonNull FeatureVector.Builder builder) {
        return extract(builder, getStartOfTodayMillis());
    }

    // ═══════════════════════════════════════════════════════════════════
    // INDIVIDUAL FEATURE COMPUTATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * P1: Mood risk — maps mood string to risk, enhanced by trajectory and journal sentiment.
     * Declining mood trajectory amplifies risk; journal negativity cross-validates.
     */
    private float computeMoodRisk(@NonNull QuestionnaireResponse r,
                                   float trajectory, float journalSentiment) {
        float baseRisk = r.getMoodRisk(); // 0.0 (Happy) to 0.9 (Numb)

        // Trajectory boost: declining mood adds risk
        if (trajectory < -0.3f) {
            baseRisk = clamp(baseRisk + 0.10f); // Declining
        } else if (trajectory < -0.6f) {
            baseRisk = clamp(baseRisk + 0.20f); // Rapidly declining
        }

        // Journal cross-validation: if journal sentiment is very negative, boost
        if (journalSentiment < -0.5f) {
            baseRisk = clamp(baseRisk + 0.05f);
        }

        // Emotional signals: crying or withdrawal amplify mood risk
        if (r.feltLikeCrying) baseRisk = clamp(baseRisk + 0.10f);
        if (r.wantedToWithdraw) baseRisk = clamp(baseRisk + 0.08f);

        // Hope deficit is a powerful depression predictor
        if (r.hopeLevel > 0 && r.hopeLevel <= 2) {
            baseRisk = clamp(baseRisk + 0.12f);
        }

        return baseRisk;
    }

    /**
     * P2: Stress risk — (stress-1)/4, amplified by overwhelm and rumination.
     */
    private float computeStressRisk(@NonNull QuestionnaireResponse r) {
        float base = (r.stressLevel - 1f) / 4f; // 1→0, 5→1

        // Overwhelm amplifies stress
        if (r.overwhelmLevel > 0) {
            float overwhelm = (r.overwhelmLevel - 1f) / 4f;
            base = clamp(base * 0.6f + overwhelm * 0.4f); // Weighted blend
        }

        // Rumination amplifies perceived stress
        if (r.ruminationLevel >= 4) {
            base = clamp(base + 0.08f);
        }

        // Unhealthy coping is a stress amplifier
        if ("phone".equals(r.copingMechanism) || "substance".equals(r.copingMechanism) ||
                "avoidance".equals(r.copingMechanism)) {
            base = clamp(base + 0.05f);
        }

        return clamp(base);
    }

    /**
     * P3: Loneliness risk — (loneliness-1)/4, amplified by social signals.
     */
    private float computeLonelinessRisk(@NonNull QuestionnaireResponse r) {
        float base = (r.lonelinessLevel - 1f) / 4f;

        // No social support amplifies loneliness
        if (!r.socialSupport) base = clamp(base + 0.10f);

        // Poor social interaction quality
        if (r.socialInteractionQuality > 0 && r.socialInteractionQuality <= 2) {
            base = clamp(base + 0.08f);
        }

        // Zero meaningful conversations = isolation
        if (r.meaningfulConversations == 0 && r.lonelinessLevel >= 3) {
            base = clamp(base + 0.10f);
        }

        // Withdrawal desire is the strongest isolation signal
        if (r.wantedToWithdraw) base = clamp(base + 0.12f);

        return clamp(base);
    }

    /**
     * P4: Motivation deficit — INVERTED: 1-(motivation-1)/4.
     * Low motivation = high risk. Enhanced by goal clarity and self-worth.
     */
    private float computeMotivationDeficit(@NonNull QuestionnaireResponse r,
                                            @Nullable WeeklyAssessment weekly) {
        float base = 1f - (r.motivationLevel - 1f) / 4f; // 5→0, 1→1

        // No goal clarity amplifies motivation deficit
        if (!r.goalClarity && r.motivationLevel <= 3) {
            base = clamp(base + 0.08f);
        }

        // Low self-worth correlates with low motivation
        if (r.selfWorthScore > 0 && r.selfWorthScore <= 2) {
            base = clamp(base + 0.10f);
        }

        // Anhedonia from weekly assessment = extreme motivation killer
        if (weekly != null && weekly.anhedoniaScore >= 7) {
            base = clamp(base + 0.12f);
        }

        // Burnout from weekly erodes motivation
        if (weekly != null && weekly.burnoutRiskScore >= 7) {
            base = clamp(base + 0.08f);
        }

        return clamp(base);
    }

    /**
     * P5: Sleep deficit — INVERTED: 1-(sleep-3)/5, weighted by sleep quality.
     * Combines hours and subjective quality into one risk signal.
     */
    private float computeSleepDeficit(@NonNull QuestionnaireResponse r) {
        // Hours-based deficit: 3h=1.0, 8h+=0.0
        float hourDeficit = 1f - clamp((r.sleepHours - SLEEP_MIN_HOURS) /
                (SLEEP_MAX_HOURS - SLEEP_MIN_HOURS));

        // Quality-based deficit: 1=1.0, 5=0.0
        float qualityDeficit = 0.5f; // default if not reported
        if (r.sleepQuality > 0) {
            qualityDeficit = 1f - (r.sleepQuality - 1f) / 4f;
        }

        // Weighted: 60% hours, 40% quality
        return clamp(hourDeficit * 0.6f + qualityDeficit * 0.4f);
    }

    /**
     * P6: Energy deficit — High→0, Medium→0.5, Low→1.0.
     * Cross-validated with exercise (protective) and appetite (confirming).
     */
    private float computeEnergyDeficit(@NonNull QuestionnaireResponse r) {
        float base = mapLevelToRisk(r.energyLevel);

        // Exercise is protective — dampens energy deficit
        if (r.exercisedToday && base > 0.3f) {
            base = clamp(base - 0.10f);
        }

        // Appetite disruption confirms low energy
        if ("reduced".equals(r.appetiteStatus) || "none".equals(r.appetiteStatus)) {
            base = clamp(base + 0.08f);
        }

        return clamp(base);
    }

    /**
     * P7: Focus deficit — High→0, Medium→0.5, Low→1.0.
     * Cross-validated with distraction, mental clarity, and decision difficulty.
     */
    private float computeFocusDeficit(@NonNull QuestionnaireResponse r) {
        float base = mapLevelToRisk(r.focusLevel);

        // Felt distracted amplifies focus deficit
        if (Boolean.TRUE.equals(r.feltDistracted)) {
            base = clamp(base + 0.10f);
        }

        // Low mental clarity confirms focus issues
        if (r.mentalClarity > 0 && r.mentalClarity <= 2) {
            base = clamp(base + 0.08f);
        }

        // Decision difficulty correlates with cognitive depletion
        if (r.decisionDifficulty >= 4) {
            base = clamp(base + 0.06f);
        }

        return clamp(base);
    }

    /**
     * P8: Purpose deficit — from daily or weekly assessment, INVERTED.
     * Prefers daily check-in purpose score; falls back to weekly.
     */
    private float computePurposeDeficit(@NonNull QuestionnaireResponse r,
                                         @Nullable WeeklyAssessment weekly) {
        int purposeVal = 0;

        // Daily check-in purpose score (1-5)
        if (r.purposeScore > 0) {
            purposeVal = r.purposeScore;
        }
        // Fallback to weekly assessment (1-10 scale → normalize to 1-5)
        else if (weekly != null && weekly.purposeScore > 0) {
            purposeVal = Math.max(1, Math.min(5, (weekly.purposeScore + 1) / 2));
        }

        if (purposeVal <= 0) return 0.5f; // Unknown

        float base = 1f - (purposeVal - 1f) / 4f; // 5→0.0, 1→1.0

        // Low self-efficacy from weekly amplifies purpose deficit
        if (weekly != null && weekly.selfEfficacyScore > 0 && weekly.selfEfficacyScore <= 3) {
            base = clamp(base + 0.10f);
        }

        return clamp(base);
    }

    /**
     * P9: Addiction self-score — (val-1)/9.
     * Prefers daily; falls back to weekly addictionAwarenessScore.
     */
    private float computeAddictionScore(@NonNull QuestionnaireResponse r,
                                         @Nullable WeeklyAssessment weekly) {
        int score = 0;

        if (r.addictionSelfScore > 0) {
            score = r.addictionSelfScore; // 1-10
        } else if (weekly != null && weekly.addictionAwarenessScore > 0) {
            score = weekly.addictionAwarenessScore; // 1-10
        }

        if (score <= 0) return 0.5f; // Unknown
        return clamp((score - 1f) / 9f); // 1→0, 10→1
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: TRAJECTORY, TURBULENCE, AMPLIFICATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute mood trajectory: positive = improving, negative = declining.
     * Uses linear regression over recent mood scores.
     * Returns slope in range [-1.0, +1.0].
     */
    private float computeMoodTrajectory(@Nullable List<QuestionnaireResponse> recent) {
        if (recent == null || recent.size() < 3) return 0f;

        int n = Math.min(recent.size(), TRAJECTORY_WINDOW);
        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            float x = i;
            float y = recent.get(i).getMoodFloat(); // 0.5 (Numb) to 4.0 (Happy)
            sumX += x; sumY += y; sumXY += x * y; sumX2 += x * x;
        }
        float denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 0.001f) return 0f;
        float slope = (n * sumXY - sumX * sumY) / denom;
        // Normalize: slope of ~0.5/day = strong improvement
        return clamp(slope / 0.5f) * (slope > 0 ? 1f : -1f);
    }

    /**
     * Compute emotional turbulence: rapid mood swings = instability.
     * Returns 0.0 (stable) to 1.0 (extreme swings).
     */
    private float computeEmotionalTurbulence(@Nullable List<QuestionnaireResponse> recent) {
        if (recent == null || recent.size() < 2) return 0f;

        float totalDelta = 0;
        int n = Math.min(recent.size(), TRAJECTORY_WINDOW);
        for (int i = 1; i < n; i++) {
            float prev = recent.get(i).getMoodFloat();
            float curr = recent.get(i - 1).getMoodFloat();
            totalDelta += Math.abs(curr - prev);
        }
        float avgDelta = totalDelta / (n - 1);
        // Avg delta of 2.0+ = extreme turbulence
        return clamp(avgDelta / 2.0f);
    }

    /**
     * Cross-signal amplification — when multiple distress signals co-occur,
     * the combined risk is greater than the sum of parts.
     * Returns amplification factor (0.0 = no amplification, 1.0 = maximum).
     */
    private float computeAmplification(float mood, float stress, float loneliness,
                                        float sleepDeficit, float turbulence) {
        int highRiskCount = 0;
        if (mood > 0.7f) highRiskCount++;
        if (stress > 0.7f) highRiskCount++;
        if (loneliness > 0.7f) highRiskCount++;
        if (sleepDeficit > 0.7f) highRiskCount++;
        if (turbulence > 0.6f) highRiskCount++;

        // Amplification kicks in when 3+ signals are simultaneously high
        if (highRiskCount >= 4) return 0.25f;
        if (highRiskCount >= 3) return 0.15f;
        return 0f;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA LOADING & STALENESS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Load the best available check-in for the target day.
     * First tries exact day match, then falls back to most recent.
     */
    @Nullable
    private QuestionnaireResponse loadBestCheckIn(long dayTimestamp) {
        // Try today's check-in first
        List<QuestionnaireResponse> todayList = questionnaireDao.getResponsesForDay(dayTimestamp);
        if (todayList != null && !todayList.isEmpty()) {
            return todayList.get(0); // Most recent for that day
        }
        // Fall back to the most recent check-in available
        return questionnaireDao.getLatestResponseSync();
    }

    /** Load the latest weekly assessment. */
    @Nullable
    private WeeklyAssessment loadLatestWeeklyAssessment() {
        try {
            return weeklyAssessmentDao.getLatestAssessmentSync();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Load average journal sentiment for the day.
     * Returns [-1.0, +1.0] or 0 if no journals.
     */
    private float loadJournalSentiment(long dayTimestamp) {
        try {
            JournalEntry entry = journalDao.getLatestEntryForDay(dayTimestamp);
            if (entry != null && entry.sentimentScore != 0) {
                return entry.sentimentScore;
            }
        } catch (Exception ignored) {}
        return 0f;
    }

    /**
     * Compute stale-data decay factor.
     * 1.0 = fresh data (today), decays toward 0.0 as data ages.
     * After STALE_THRESHOLD_MS, values decay exponentially per day.
     */
    private float computeStaleFactor(@Nullable QuestionnaireResponse r, long targetDay) {
        if (r == null) return 0f;
        long ageMs = targetDay - r.timestamp;
        if (ageMs <= 0) return 1f; // Same day or future

        long ageDays = ageMs / (24L * 60 * 60 * 1000);
        if (ageDays <= 1) return 1f; // Within 1 day = fresh

        // Exponential decay after day 1
        float factor = (float) Math.pow(STALE_DECAY_PER_DAY, ageDays - 1);
        return Math.max(0.2f, factor); // Never decay below 0.2 (keep some signal)
    }

    /**
     * Apply staleness: blend raw value toward 0.5 (uncertain) as data ages.
     */
    private float applyStale(float value, float staleFactor) {
        if (staleFactor >= 1f) return value;
        return value * staleFactor + 0.5f * (1f - staleFactor);
    }

    /**
     * Count consecutive days ending with the most recent check-in where
     * mood was Sad, Anxious, Angry, or Numb.
     */
    private int countConsecutiveSadDays(@Nullable List<QuestionnaireResponse> recent) {
        if (recent == null || recent.isEmpty()) return 0;
        int streak = 0;
        for (QuestionnaireResponse r : recent) {
            if (isNegativeMood(r.mood)) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static boolean isNegativeMood(@Nullable String mood) {
        if (mood == null) return false;
        return "Sad".equals(mood) || "Anxious".equals(mood) ||
                "Angry".equals(mood) || "Numb".equals(mood);
    }

    /** Map "High"→0.0, "Medium"→0.5, "Low"→1.0 (higher = greater risk). */
    private static float mapLevelToRisk(@Nullable String level) {
        if (level == null) return 0.5f;
        switch (level) {
            case "High":   return 0.0f;
            case "Medium": return 0.5f;
            case "Low":    return 1.0f;
            default:       return 0.5f;
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static long getStartOfTodayMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
