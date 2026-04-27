package com.mindtrace.ai.database.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Premium OnboardingProfile — the psychometric foundation of MindTrace AI.
 *
 * <p>Captures a multidimensional snapshot of the user's psychological,
 * behavioral, and lifestyle state at the time of onboarding. This entity
 * serves as the "baseline personality fingerprint" against which all
 * future assessments are compared.</p>
 *
 * <h3>Dimension Map:</h3>
 * <pre>
 *   Identity    → name, ageRange, primaryGoal, personalityArchetype
 *   Emotional   → stress, anxiety, motivation, loneliness, selfDoubt, overthinking
 *   Functioning → sleep, focus, energy, workPressure, distraction
 *   Digital     → socialMedia, lateNight, addiction, overuse, binge, appSwitch
 *   Lifestyle   → routine, habits, procrastination, physical, stuck
 *   Support     → socialSupport, supportNeeded, safetySafety
 *   Clinical    → addictionScale, purposeScore, copingStyle, mentalHealthHistory
 * </pre>
 *
 * @see com.mindtrace.ai.ai.MultiModalClassifier
 * @see com.mindtrace.ai.onboarding.OnboardingProfileAnalyzer
 */
@Entity(tableName = "onboarding_profile")
public class OnboardingProfile {
    @PrimaryKey
    public int id = 1;

    // ═════════════════════════════════════════════════════════════════════
    // IDENTITY LAYER
    // ═════════════════════════════════════════════════════════════════════
    public String name;
    public String ageRange;
    public String primaryGoal;
    public String helpAreasCsv;

    // ═════════════════════════════════════════════════════════════════════
    // EMOTIONAL STATE (baseline snapshot)
    // ═════════════════════════════════════════════════════════════════════
    public int stressLevel;
    public int anxietyLevel;
    public int motivationLevel;
    public int lonelinessLevel;
    public int selfDoubtLevel;
    public int overthinkingLevel;

    // ═════════════════════════════════════════════════════════════════════
    // FUNCTIONING LEVEL
    // ═════════════════════════════════════════════════════════════════════
    public float sleepHours;
    public int sleepQuality;
    public int focusLevel;
    public int energyLevel;
    public int workPressure;
    public int distractionLevel;

    // ═════════════════════════════════════════════════════════════════════
    // DIGITAL BEHAVIOR
    // ═════════════════════════════════════════════════════════════════════
    public int socialMediaUse;
    public int lateNightPhoneUse;
    public int appAddictionRisk;
    public int overusePatternLevel;
    public int bingeScrollingLevel;
    public int appSwitchingHabit;

    // ═════════════════════════════════════════════════════════════════════
    // LIFESTYLE & HABITS
    // ═════════════════════════════════════════════════════════════════════
    public int routineConsistency;
    public int productiveHabits;
    public int procrastinationLevel;
    public int physicalActivity;
    public int feelingStuck;

    // ═════════════════════════════════════════════════════════════════════
    // SUPPORT SYSTEM
    // ═════════════════════════════════════════════════════════════════════
    public int socialSupportLevel;
    public boolean supportNeeded;
    public boolean safetySupportEnabled;
    public long timestamp;

    // ═════════════════════════════════════════════════════════════════════
    // PREMIUM CLINICAL MARKERS (Tasks 2.G.2, 2.G.3 — NEW)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Self-assessed addiction awareness at onboarding (1-10).
     * 1 = "I don't think I have a problem"
     * 10 = "I'm fully aware of my dependency"
     * (Task 2.G.2)
     */
    public int addictionScale;

    /**
     * Self-assessed sense of purpose at onboarding (1-5).
     * 1 = "I feel no direction"
     * 5 = "I have a clear mission"
     * (Task 2.G.3)
     */
    public int purposeScore;

    /**
     * Primary coping style — how the user handles distress.
     * Values: "avoidant", "emotional", "problem_focused", "social", "unknown"
     * Inferred from help areas + distraction/scrolling patterns.
     */
    public String copingStyle;

    /**
     * Behavioral archetype — a high-level personality classification.
     * Values: "perfectionist", "escapist", "procrastinator", "achiever",
     *         "social_seeker", "explorer", "unknown"
     * Computed from onboarding answers by OnboardingProfileAnalyzer.
     */
    public String personalityArchetype;

    /**
     * Whether the user has previously sought professional mental health support.
     * Values: "never", "currently", "previously", "considering", null
     * (Task 2.G.7)
     *
     * <p>Clinical significance:</p>
     * <ul>
     *   <li><b>"currently"</b> — user is in active treatment, calibrate tone
     *       to complement (not replace) professional guidance</li>
     *   <li><b>"previously"</b> — user has experience with therapy,
     *       may respond better to clinical language</li>
     *   <li><b>"considering"</b> — prime opportunity for gentle nudges
     *       toward professional support via the Support panel</li>
     *   <li><b>"never"</b> — use accessible, non-clinical language
     *       throughout the app experience</li>
     * </ul>
     */
    public String mentalHealthHistory;

    /**
     * Self-reported trigger time — when the user is most vulnerable.
     * Values: "morning", "afternoon", "evening", "late_night", "unpredictable"
     */
    public String peakVulnerabilityTime;

    /**
     * Readiness to change score (1-10).
     * Based on Transtheoretical Model stages of change.
     * 1-3 = Precontemplation, 4-5 = Contemplation, 6-7 = Preparation,
     * 8-9 = Action, 10 = Maintenance
     */
    public int readinessToChange;

    /**
     * Whether onboarding was fully completed or abandoned mid-flow.
     */
    public boolean onboardingComplete;

    // ═════════════════════════════════════════════════════════════════════
    // LIFESTYLE & WELLNESS BASELINE (Tasks 2.G.4, 2.G.5, 2.G.6 — NEW)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Routine stability at onboarding (1-5). (Task 2.G.4)
     * 1 = "My days are completely chaotic"
     * 3 = "I have some structure but it often breaks down"
     * 5 = "I follow a consistent daily routine"
     *
     * <p>Used as the baseline for weekly routineStabilityScore deltas.
     * Declining stability week-over-week relative to this baseline
     * triggers early-warning alerts.</p>
     */
    public int routineStability;

    /**
     * Exercise frequency at onboarding. (Task 2.G.5)
     * Values: "Never", "1-2x", "3-4x", "Daily"
     *
     * <p>Maps to approximate days: Never=0, 1-2x=2, 3-4x=4, Daily=7.
     * Used as baseline for weekly exercise tracking and serves as
     * input to the protective factor calculation in
     * {@link WeeklyAssessment#computeProtectiveFactorScore()}.</p>
     */
    public String exerciseFrequency;

    /**
     * Typical weekly screen-free activities count (0-5). (Task 2.G.6)
     * Examples: reading, outdoor walks, hobbies, sports, cooking.
     * 0 = "I spend nearly all my time on screens"
     * 5 = "I have a rich offline life"
     *
     * <p>Strong inverse correlation with digital dependency.
     * Used by {@link #getLifestyleHealthIndex()} to compute
     * the offline engagement dimension.</p>
     */
    public int screenFreeActivities;

    /**
     * Baseline social quality self-assessment (1-5).
     * 1 = "My social interactions feel draining"
     * 5 = "I have deeply fulfilling relationships"
     */
    public int socialQualityBaseline;

    /**
     * Self-awareness of screen time impact (1-10).
     * How much the user believes their screen habits affect their wellbeing.
     * 1 = "No impact" → 10 = "It's destroying my life"
     */
    public int screenTimeAwareness;

    /**
     * Comma-separated list of apps the user identifies as triggers.
     * Example: "Instagram,TikTok,Twitter"
     * Collected during onboarding for personalized intervention targeting.
     */
    public String triggerApps;

    // ═════════════════════════════════════════════════════════════════════
    // COMPUTED INTELLIGENCE (Task 2.G.1 — Advanced Analysis Methods)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Returns a risk profile string based on the convergence of emotional,
     * digital, and lifestyle signals captured during onboarding.
     *
     * <p>Risk Levels:</p>
     * <ul>
     *   <li><b>critical</b> — Multiple high-risk signals + low protective factors</li>
     *   <li><b>elevated</b> — Moderate risk with weak coping</li>
     *   <li><b>moderate</b> — Some concerns but adequate protective factors</li>
     *   <li><b>low</b> — Healthy baseline</li>
     * </ul>
     */
    @Ignore
    public String getRiskProfile() {
        int emotionalRisk = 0;
        if (stressLevel >= 4) emotionalRisk++;
        if (anxietyLevel >= 4) emotionalRisk++;
        if (lonelinessLevel >= 4) emotionalRisk++;
        if (selfDoubtLevel >= 4) emotionalRisk++;
        if (overthinkingLevel >= 4) emotionalRisk++;

        int digitalRisk = 0;
        if (socialMediaUse >= 4) digitalRisk++;
        if (lateNightPhoneUse >= 4) digitalRisk++;
        if (appAddictionRisk >= 4) digitalRisk++;
        if (bingeScrollingLevel >= 4) digitalRisk++;
        if (addictionScale <= 3) digitalRisk++; // Low awareness = higher risk

        int protectiveFactors = 0;
        if (physicalActivity >= 3) protectiveFactors++;
        if (socialSupportLevel >= 3) protectiveFactors++;
        if (routineConsistency >= 3) protectiveFactors++;
        if (purposeScore >= 3) protectiveFactors++;
        if (motivationLevel >= 3) protectiveFactors++;

        int totalRisk = emotionalRisk + digitalRisk;
        if (totalRisk >= 7 && protectiveFactors <= 2) return "critical";
        if (totalRisk >= 5) return "elevated";
        if (totalRisk >= 3) return "moderate";
        return "low";
    }

    /**
     * Computes a Digital Dependency Index (0.0–1.0).
     * Higher values indicate deeper digital dependency.
     *
     * <p>Formula weights:</p>
     * <ul>
     *   <li>25% — Social media use intensity</li>
     *   <li>20% — Late night phone use</li>
     *   <li>20% — App addiction risk self-assessment</li>
     *   <li>15% — Binge scrolling frequency</li>
     *   <li>10% — App switching chaos</li>
     *   <li>10% — Inverse of addiction awareness (blind users = higher)</li>
     * </ul>
     */
    @Ignore
    public float getDigitalDependencyIndex() {
        float index = 0f;
        index += normalize(socialMediaUse, 5) * 0.25f;
        index += normalize(lateNightPhoneUse, 5) * 0.20f;
        index += normalize(appAddictionRisk, 5) * 0.20f;
        index += normalize(bingeScrollingLevel, 5) * 0.15f;
        index += normalize(appSwitchingHabit, 5) * 0.10f;
        // Low awareness amplifies dependency score
        index += (1.0f - normalize(addictionScale, 10)) * 0.10f;
        return Math.min(1.0f, index);
    }

    /**
     * Computes a Readiness Score (0.0–1.0) — how ready the user is
     * to engage with behavior change interventions.
     *
     * <p>Combines motivation, purpose, routine, and explicit readiness.</p>
     */
    @Ignore
    public float computeReadinessScore() {
        float score = 0f;
        score += normalize(motivationLevel, 5) * 0.30f;
        score += normalize(purposeScore, 5) * 0.25f;
        score += normalize(routineConsistency, 5) * 0.15f;
        score += normalize(routineStability, 5) * 0.05f;
        score += normalize(readinessToChange, 10) * 0.15f;
        score += normalize(productiveHabits, 5) * 0.10f;
        return Math.min(1.0f, score);
    }

    /**
     * Computes a Lifestyle Health Index (0.0–1.0).
     * Measures how healthy the user's non-digital life is.
     *
     * <p>Formula:</p>
     * <ul>
     *   <li>25% — Exercise frequency (converted to 0-1 scale)</li>
     *   <li>20% — Routine stability</li>
     *   <li>20% — Screen-free activities</li>
     *   <li>15% — Sleep quality</li>
     *   <li>10% — Social quality</li>
     *   <li>10% — Physical activity</li>
     * </ul>
     */
    @Ignore
    public float getLifestyleHealthIndex() {
        float score = 0f;
        score += exerciseFrequencyToFloat() * 0.25f;
        score += normalize(routineStability, 5) * 0.20f;
        score += normalize(screenFreeActivities, 5) * 0.20f;
        score += normalize(sleepQuality, 5) * 0.15f;
        score += normalize(socialQualityBaseline, 5) * 0.10f;
        score += normalize(physicalActivity, 5) * 0.10f;
        return Math.min(1.0f, score);
    }

    /**
     * Returns profile completeness percentage (0-100).
     * Measures how many fields have meaningful (non-default) values.
     */
    @Ignore
    public int getProfileCompleteness() {
        int filled = 0;
        int total = 36;

        // Identity
        if (name != null && !name.isEmpty()) filled++;
        if (ageRange != null && !ageRange.isEmpty()) filled++;
        if (primaryGoal != null && !primaryGoal.isEmpty()) filled++;
        if (helpAreasCsv != null && !helpAreasCsv.isEmpty()) filled++;

        // Emotional (all > 0 means set)
        if (stressLevel > 0) filled++;
        if (anxietyLevel > 0) filled++;
        if (motivationLevel > 0) filled++;
        if (lonelinessLevel > 0) filled++;
        if (selfDoubtLevel > 0) filled++;
        if (overthinkingLevel > 0) filled++;

        // Functioning
        if (sleepHours > 0) filled++;
        if (sleepQuality > 0) filled++;
        if (focusLevel > 0) filled++;
        if (energyLevel > 0) filled++;
        if (workPressure > 0) filled++;
        if (distractionLevel > 0) filled++;

        // Digital
        if (socialMediaUse > 0) filled++;
        if (lateNightPhoneUse > 0) filled++;
        if (appAddictionRisk > 0) filled++;

        // Lifestyle
        if (routineConsistency > 0) filled++;
        if (physicalActivity > 0) filled++;

        // Premium clinical markers
        if (addictionScale > 0) filled++;
        if (purposeScore > 0) filled++;
        if (copingStyle != null) filled++;
        if (personalityArchetype != null) filled++;
        if (mentalHealthHistory != null) filled++;
        if (peakVulnerabilityTime != null) filled++;
        if (readinessToChange > 0) filled++;

        // Lifestyle & wellness baseline
        if (routineStability > 0) filled++;
        if (exerciseFrequency != null) filled++;
        if (screenFreeActivities > 0) filled++;
        if (socialQualityBaseline > 0) filled++;
        if (screenTimeAwareness > 0) filled++;
        if (triggerApps != null) filled++;

        if (socialSupportLevel > 0) filled++;
        if (timestamp > 0) filled++;

        return (int) ((filled / (float) total) * 100);
    }

    /** Normalize an int value (0..max) to 0.0–1.0 float. */
    @Ignore
    private float normalize(int value, int max) {
        if (max <= 0) return 0f;
        return Math.max(0f, Math.min(1f, value / (float) max));
    }

    /** Convert exerciseFrequency string to 0.0–1.0 for computation. */
    @Ignore
    private float exerciseFrequencyToFloat() {
        if (exerciseFrequency == null) return 0f;
        switch (exerciseFrequency) {
            case "Never": return 0f;
            case "1-2x": return 0.3f;
            case "3-4x": return 0.6f;
            case "Daily": return 1.0f;
            default: return 0f;
        }
    }
}
