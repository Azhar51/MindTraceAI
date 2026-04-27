package com.mindtrace.ai.ai;

import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;

import java.util.List;

public class MentalStateClassifier {

    public enum State {
        STABLE,
        DIGITAL_ADDICTION,
        STRESS_ANXIETY,
        LOW_PURPOSE,
        EMOTIONAL_FATIGUE,
        SOCIAL_ISOLATION,
        EARLY_DEPRESSION_RISK
    }

    /**
     * Rule-based classifier. In a production app, this would call a TFLite model
     * or a cloud AI API.
     */
    public State classify(List<DailyUsage> usageHistory, List<QuestionnaireResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return State.STABLE;
        }

        QuestionnaireResponse latest = responses.get(0);
        String mood = safe(latest.mood);
        String focusLevel = safe(latest.focusLevel);
        String energyLevel = safe(latest.energyLevel);
        int stressLevel = clampScale(latest.stressLevel);
        int lonelinessLevel = clampScale(latest.lonelinessLevel);
        int motivationLevel = clampScale(latest.motivationLevel);
        boolean hasLonelinessData = latest.lonelinessLevel > 0;
        boolean hasMotivationData = latest.motivationLevel > 0;
        boolean hasSleepData = latest.sleepHours > 0f;
        boolean lowFocus = "Low".equalsIgnoreCase(focusLevel);
        boolean lowEnergy = "Low".equalsIgnoreCase(energyLevel);

        if (usageHistory != null && !usageHistory.isEmpty()) {
            DailyUsage today = usageHistory.get(0);
            if (today.screenTimeMillis > 6L * 3600000L || today.unlockCount > 80) {
                return State.DIGITAL_ADDICTION;
            }
        }

        if ("Sad".equalsIgnoreCase(mood) && hasMotivationData && motivationLevel <= 2) {
            return State.EARLY_DEPRESSION_RISK;
        }

        if ((hasSleepData && latest.sleepHours < 6f && stressLevel >= 4)
                || (lowEnergy && hasSleepData && latest.sleepHours < 6.5f)) {
            return State.EMOTIONAL_FATIGUE;
        }

        if (stressLevel >= 4 || "Anxious".equalsIgnoreCase(mood) || (Boolean.TRUE.equals(latest.feltDistracted) && lowFocus)) {
            return State.STRESS_ANXIETY;
        }

        if (hasLonelinessData && !latest.socialSupport && lonelinessLevel >= 4) {
            return State.SOCIAL_ISOLATION;
        }

        if ((hasMotivationData && !latest.goalClarity && motivationLevel <= 3) || (lowFocus && lowEnergy)) {
            return State.LOW_PURPOSE;
        }

        return State.STABLE;
    }

    private int clampScale(int value) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(value, 5);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
