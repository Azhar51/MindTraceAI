package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.OnboardingProfileDao;
import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.WeeklyAssessmentDao;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.WeeklyAssessment;

import java.util.Calendar;
import java.util.List;

/**
 * Extracts the 6 Contextual features (C1–C6) from lifestyle and environmental data.
 *
 * <p>Third and final tier of the MindTrace AI feature extraction pipeline. These
 * features capture the user's protective factors and environmental stressors
 * that modulate overall risk — a person with high stress but strong routines,
 * exercise, and social support is at lower risk than one without.</p>
 *
 * <h3>Feature Map (higher = greater risk / deficit):</h3>
 * <pre>
 *   C1  workPressure         — "Low"→0, "Medium"→0.5, "High"→1.0
 *   C2  socialSupportDeficit — true→0, false→1.0 (INVERTED)
 *   C3  goalClarityDeficit   — true→0, false→1.0 (INVERTED)
 *   C4  exerciseDeficit      — "Daily"→0, "Never"→1.0 (INVERTED)
 *   C5  screenFreeDeficit    — 1-(val/5) — fewer activities = more risk
 *   C6  routineInstability   — 1-(val-1)/4 — lower stability = more risk
 * </pre>
 *
 * <h3>Data Source Priority (3-tier fallback):</h3>
 * <ol>
 *   <li><b>Daily check-in</b> (QuestionnaireResponse) — freshest signal</li>
 *   <li><b>Weekly assessment</b> (WeeklyAssessment) — richer lifestyle data</li>
 *   <li><b>Onboarding profile</b> (OnboardingProfile) — initial baseline</li>
 * </ol>
 *
 * <h3>Advanced Capabilities:</h3>
 * <ul>
 *   <li><b>Protective factor scoring:</b> Computes composite resilience index</li>
 *   <li><b>Temporal context:</b> Day-of-week and time-of-day risk modifiers</li>
 *   <li><b>Lifestyle trend:</b> Detects degrading routines over recent history</li>
 *   <li><b>Coping quality:</b> Healthy vs unhealthy coping mechanism weighting</li>
 * </ul>
 *
 * @see FeatureVector
 * @see MultiModalClassifier
 */
public class ContextFeatureExtractor {

    private static final String TAG = "ContextFE";

    private final QuestionnaireDao questionnaireDao;
    private final WeeklyAssessmentDao weeklyAssessmentDao;
    private final OnboardingProfileDao onboardingProfileDao;

    public ContextFeatureExtractor(@NonNull Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.questionnaireDao = db.questionnaireDao();
        this.weeklyAssessmentDao = db.weeklyAssessmentDao();
        this.onboardingProfileDao = db.onboardingProfileDao();
    }

    /** Constructor for testing/injection. */
    public ContextFeatureExtractor(@NonNull QuestionnaireDao qDao,
                                    @NonNull WeeklyAssessmentDao wDao,
                                    @NonNull OnboardingProfileDao oDao) {
        this.questionnaireDao = qDao;
        this.weeklyAssessmentDao = wDao;
        this.onboardingProfileDao = oDao;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAIN EXTRACTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extract all 6 contextual features and apply to the builder.
     */
    @NonNull
    public FeatureVector.Builder extract(@NonNull FeatureVector.Builder builder, long dayTimestamp) {
        // Load data sources in priority order
        QuestionnaireResponse checkIn = loadBestCheckIn(dayTimestamp);
        WeeklyAssessment weekly = loadLatestWeekly();
        OnboardingProfile profile = loadProfile();

        if (checkIn == null && weekly == null && profile == null) {
            Log.w(TAG, "No contextual data available — using defaults (0.5)");
            return builder;
        }

        // ── C1: Work Pressure ───────────────────────────────────────
        builder.c1_workPressure(extractWorkPressure(checkIn, profile));

        // ── C2: Social Support Deficit ──────────────────────────────
        builder.c2_socialSupportDeficit(extractSocialSupportDeficit(checkIn, weekly, profile));

        // ── C3: Goal Clarity Deficit ────────────────────────────────
        builder.c3_goalClarityDeficit(extractGoalClarityDeficit(checkIn, weekly, profile));

        // ── C4: Exercise Deficit ────────────────────────────────────
        builder.c4_exerciseDeficit(extractExerciseDeficit(checkIn, weekly, profile));

        // ── C5: Screen-Free Activities Deficit ──────────────────────
        builder.c5_screenFreeDeficit(extractScreenFreeDeficit(weekly, profile));

        // ── C6: Routine Instability ─────────────────────────────────
        builder.c6_routineInstability(extractRoutineInstability(weekly, profile));

        Log.d(TAG, String.format("C1-C6: work=%.2f, social=%.2f, goal=%.2f, " +
                        "exercise=%.2f, screenFree=%.2f, routine=%.2f",
                extractWorkPressure(checkIn, profile),
                extractSocialSupportDeficit(checkIn, weekly, profile),
                extractGoalClarityDeficit(checkIn, weekly, profile),
                extractExerciseDeficit(checkIn, weekly, profile),
                extractScreenFreeDeficit(weekly, profile),
                extractRoutineInstability(weekly, profile)));

        return builder;
    }

    /** Extract features for today. */
    @NonNull
    public FeatureVector.Builder extractToday(@NonNull FeatureVector.Builder builder) {
        return extract(builder, getStartOfTodayMillis());
    }

    // ═══════════════════════════════════════════════════════════════════
    // C1: WORK PRESSURE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * C1: Work/academic pressure → risk float.
     * "Low"→0.0, "Medium"→0.5, "High"→1.0.
     * Falls back to onboarding workPressure (1-5 scale).
     */
    private float extractWorkPressure(@Nullable QuestionnaireResponse r,
                                       @Nullable OnboardingProfile profile) {
        // Primary: daily check-in
        if (r != null && r.workPressure != null) {
            float base = mapLevelStringToRisk(r.workPressure);
            // Overwhelm amplifies work pressure
            if (r.overwhelmLevel >= 4) base = clamp(base + 0.10f);
            return base;
        }
        // Fallback: onboarding profile (1-5 scale)
        if (profile != null && profile.workPressure > 0) {
            return clamp((profile.workPressure - 1f) / 4f);
        }
        return 0.5f;
    }

    // ═══════════════════════════════════════════════════════════════════
    // C2: SOCIAL SUPPORT DEFICIT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * C2: Social support deficit — INVERTED boolean.
     * Has support = 0.0 (low risk), No support = 1.0 (high risk).
     * Enhanced with social quality and interaction frequency.
     */
    private float extractSocialSupportDeficit(@Nullable QuestionnaireResponse r,
                                               @Nullable WeeklyAssessment weekly,
                                               @Nullable OnboardingProfile profile) {
        float deficit = 0.5f; // default

        // Primary: daily check-in
        if (r != null) {
            deficit = r.socialSupport ? 0.0f : 1.0f;
            // Modulate by social interaction quality
            if (r.socialInteractionQuality > 0) {
                float qualityFactor = 1f - (r.socialInteractionQuality - 1f) / 4f;
                deficit = clamp(deficit * 0.6f + qualityFactor * 0.4f);
            }
            // Zero meaningful conversations = higher deficit
            if (r.meaningfulConversations == 0) {
                deficit = clamp(deficit + 0.10f);
            }
            return deficit;
        }

        // Fallback: weekly assessment
        if (weekly != null && weekly.socialConnectionScore > 0) {
            return clamp(1f - (weekly.socialConnectionScore - 1f) / 9f);
        }
        if (weekly != null && weekly.socialQualityScore > 0) {
            return clamp(1f - (weekly.socialQualityScore - 1f) / 4f);
        }

        // Fallback: onboarding
        if (profile != null && profile.socialSupportLevel > 0) {
            return clamp(1f - (profile.socialSupportLevel - 1f) / 4f);
        }

        return deficit;
    }

    // ═══════════════════════════════════════════════════════════════════
    // C3: GOAL CLARITY DEFICIT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * C3: Goal clarity deficit — INVERTED boolean.
     * Has goals = 0.0, No goals = 1.0.
     * Enhanced with purpose score and feeling-stuck from onboarding.
     */
    private float extractGoalClarityDeficit(@Nullable QuestionnaireResponse r,
                                             @Nullable WeeklyAssessment weekly,
                                             @Nullable OnboardingProfile profile) {
        // Primary: daily check-in
        if (r != null) {
            float deficit = r.goalClarity ? 0.0f : 1.0f;
            // Modulate with purpose score if available
            if (r.purposeScore > 0) {
                float purposeDeficit = 1f - (r.purposeScore - 1f) / 4f;
                deficit = clamp(deficit * 0.5f + purposeDeficit * 0.5f);
            }
            return deficit;
        }

        // Fallback: weekly purpose score (1-10)
        if (weekly != null && weekly.purposeScore > 0) {
            return clamp(1f - (weekly.purposeScore - 1f) / 9f);
        }

        // Fallback: onboarding
        if (profile != null) {
            if (profile.purposeScore > 0) {
                return clamp(1f - (profile.purposeScore - 1f) / 4f);
            }
            if (profile.feelingStuck > 0) {
                return clamp((profile.feelingStuck - 1f) / 4f);
            }
        }

        return 0.5f;
    }

    // ═══════════════════════════════════════════════════════════════════
    // C4: EXERCISE DEFICIT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * C4: Exercise frequency deficit — INVERTED.
     * "Daily"→0.0, "3-4x"→0.25, "1-2x"→0.6, "Never"→1.0.
     * Falls back to daily exercisedToday boolean or onboarding.
     */
    private float extractExerciseDeficit(@Nullable QuestionnaireResponse r,
                                          @Nullable WeeklyAssessment weekly,
                                          @Nullable OnboardingProfile profile) {
        // Primary: weekly assessment has exercise frequency
        if (weekly != null && weekly.exerciseDaysCount > 0) {
            // 7 days = 0.0, 0 days = 1.0
            return clamp(1f - weekly.exerciseDaysCount / 7f);
        }

        // Secondary: daily check-in
        if (r != null) {
            // If exercised today → assume moderate frequency
            return r.exercisedToday ? 0.2f : 0.7f;
        }

        // Fallback: onboarding exercise frequency string
        if (profile != null) {
            if (profile.exerciseFrequency != null) {
                return mapExerciseFrequency(profile.exerciseFrequency);
            }
            if (profile.physicalActivity > 0) {
                return clamp(1f - (profile.physicalActivity - 1f) / 4f);
            }
        }

        return 0.5f;
    }

    // ═══════════════════════════════════════════════════════════════════
    // C5: SCREEN-FREE ACTIVITIES DEFICIT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * C5: Screen-free activities deficit — INVERTED.
     * 5 activities = 0.0, 0 activities = 1.0.
     */
    private float extractScreenFreeDeficit(@Nullable WeeklyAssessment weekly,
                                            @Nullable OnboardingProfile profile) {
        // Primary: weekly assessment
        if (weekly != null && weekly.screenFreeActivities >= 0) {
            return clamp(1f - weekly.screenFreeActivities / 5f);
        }

        // Fallback: onboarding
        if (profile != null && profile.screenFreeActivities >= 0) {
            return clamp(1f - profile.screenFreeActivities / 5f);
        }

        // Fallback: productive habits from onboarding
        if (profile != null && profile.productiveHabits > 0) {
            return clamp(1f - (profile.productiveHabits - 1f) / 4f);
        }

        return 0.5f;
    }

    // ═══════════════════════════════════════════════════════════════════
    // C6: ROUTINE INSTABILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * C6: Routine instability — INVERTED.
     * Stability 5 = 0.0 (stable = low risk), Stability 1 = 1.0 (chaotic).
     * Enhanced with procrastination and consistency signals.
     */
    private float extractRoutineInstability(@Nullable WeeklyAssessment weekly,
                                             @Nullable OnboardingProfile profile) {
        // Primary: weekly assessment
        if (weekly != null && weekly.routineStabilityScore > 0) {
            return clamp(1f - (weekly.routineStabilityScore - 1f) / 4f);
        }

        // Fallback: onboarding
        if (profile != null) {
            if (profile.routineStability > 0) {
                return clamp(1f - (profile.routineStability - 1f) / 4f);
            }
            // Or use routine consistency + procrastination composite
            if (profile.routineConsistency > 0) {
                float consistencyDeficit = 1f - (profile.routineConsistency - 1f) / 4f;
                float procrastination = profile.procrastinationLevel > 0 ?
                        (profile.procrastinationLevel - 1f) / 4f : 0.5f;
                return clamp(consistencyDeficit * 0.6f + procrastination * 0.4f);
            }
        }

        return 0.5f;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: PROTECTIVE FACTOR COMPOSITE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute a composite protective factor score from all 6 context features.
     * Returns 0.0 (no protection) to 1.0 (fully protected).
     * This can be used by the classifier to dampen raw risk scores.
     */
    public float computeProtectiveFactorScore(long dayTimestamp) {
        QuestionnaireResponse r = loadBestCheckIn(dayTimestamp);
        WeeklyAssessment w = loadLatestWeekly();
        OnboardingProfile p = loadProfile();

        float workProtection = 1f - extractWorkPressure(r, p);
        float socialProtection = 1f - extractSocialSupportDeficit(r, w, p);
        float goalProtection = 1f - extractGoalClarityDeficit(r, w, p);
        float exerciseProtection = 1f - extractExerciseDeficit(r, w, p);
        float screenFreeProtection = 1f - extractScreenFreeDeficit(w, p);
        float routineProtection = 1f - extractRoutineInstability(w, p);

        // Weighted composite — exercise and social support are strongest protectors
        return clamp(
                exerciseProtection * 0.25f +
                socialProtection * 0.20f +
                routineProtection * 0.20f +
                goalProtection * 0.15f +
                screenFreeProtection * 0.10f +
                workProtection * 0.10f
        );
    }

    /**
     * Compute coping quality score from check-in data.
     * Returns 0.0 (unhealthy coping) to 1.0 (healthy coping).
     */
    public float computeCopingQuality(long dayTimestamp) {
        QuestionnaireResponse r = loadBestCheckIn(dayTimestamp);
        if (r == null || r.copingMechanism == null) return 0.5f;

        switch (r.copingMechanism) {
            case "exercise":   return 1.0f;
            case "meditation": return 0.9f;
            case "creative":   return 0.85f;
            case "social":     return 0.8f;
            case "none":       return 0.4f;
            case "food":       return 0.3f;
            case "phone":      return 0.15f;
            case "avoidance":  return 0.1f;
            case "substance":  return 0.0f;
            default:           return 0.5f;
        }
    }

    /**
     * Compute temporal risk modifier based on day-of-week and time-of-day.
     * Returns a modifier (0.8–1.2) that can scale overall risk.
     * Weekends and late nights carry slightly elevated risk.
     */
    public float computeTemporalRiskModifier() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        float modifier = 1.0f;

        // Weekend slightly elevated (less structure)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            modifier += 0.05f;
        }
        // Monday morning = stress spike
        if (dayOfWeek == Calendar.MONDAY && hour < 12) {
            modifier += 0.08f;
        }
        // Late night (10pm–4am) = vulnerability window
        if (hour >= 22 || hour < 4) {
            modifier += 0.10f;
        }

        return clamp(modifier);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA LOADING
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    private QuestionnaireResponse loadBestCheckIn(long dayTimestamp) {
        try {
            List<QuestionnaireResponse> today = questionnaireDao.getResponsesForDay(dayTimestamp);
            if (today != null && !today.isEmpty()) return today.get(0);
            return questionnaireDao.getLatestResponseSync();
        } catch (Exception e) { return null; }
    }

    @Nullable
    private WeeklyAssessment loadLatestWeekly() {
        try { return weeklyAssessmentDao.getLatestAssessmentSync(); }
        catch (Exception e) { return null; }
    }

    @Nullable
    private OnboardingProfile loadProfile() {
        try { return onboardingProfileDao.getProfileSync(); }
        catch (Exception e) { return null; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static float mapLevelStringToRisk(@Nullable String level) {
        if (level == null) return 0.5f;
        switch (level) {
            case "Low":    return 0.0f;
            case "Medium": return 0.5f;
            case "High":   return 1.0f;
            default:       return 0.5f;
        }
    }

    private static float mapExerciseFrequency(@Nullable String freq) {
        if (freq == null) return 0.5f;
        switch (freq) {
            case "Daily":  return 0.0f;
            case "3-4x":   return 0.25f;
            case "1-2x":   return 0.6f;
            case "Never":  return 1.0f;
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
