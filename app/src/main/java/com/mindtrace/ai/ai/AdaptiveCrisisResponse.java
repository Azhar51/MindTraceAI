package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.CrisisEvent;
import com.mindtrace.ai.database.entity.ExerciseCompletion;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adaptive Crisis Response — personalizes the crisis screen based on history.
 *
 * <h3>Adaptations:</h3>
 * <ul>
 *   <li>If breathing exercises helped most → show them first + recommend the best one</li>
 *   <li>If user always calls the helpline → surface it more prominently</li>
 *   <li>If nighttime crises → auto-enable dark mode + softer tones</li>
 *   <li>If frequent short crises → suggest prevention strategies</li>
 *   <li>If grounding worked best → recommend specific grounding variant</li>
 *   <li>Tracks resolution method effectiveness over time</li>
 * </ul>
 */
public class AdaptiveCrisisResponse {

    private static final String TAG = "AdaptiveCrisis";
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    private final AppDatabase db;
    private final ExerciseAnalyticsEngine analyticsEngine;

    public AdaptiveCrisisResponse(@NonNull Context context) {
        this.db = AppDatabase.getInstance(context.getApplicationContext());
        this.analyticsEngine = new ExerciseAnalyticsEngine(context);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERSONALIZATION PROFILE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Build a personalized crisis response profile for the current user.
     */
    @NonNull
    public CrisisProfile buildProfile() {
        CrisisProfile profile = new CrisisProfile();

        try {
            List<CrisisEvent> events = db.crisisEventDao().getRecentSync(50);
            List<ExerciseCompletion> exercises = db.exerciseCompletionDao().getRecent(50);

            if (events.isEmpty() && exercises.isEmpty()) {
                profile.isFirstCrisis = true;
                return profile;
            }

            // Resolution method analysis
            analyzeResolutionMethods(events, profile);

            // Time-of-day pattern
            analyzeTimePatterns(events, profile);

            // Frequency analysis
            analyzeCrisisFrequency(events, profile);

            // Exercise effectiveness
            analyzeExerciseEffectiveness(exercises, profile);

            // Generate personalized message
            profile.personalizedMessage = generatePersonalizedMessage(profile);

            // Determine recommended actions order
            profile.recommendedActions = determineActionOrder(profile);

        } catch (Exception e) {
            Log.e(TAG, "Failed to build crisis profile", e);
        }

        return profile;
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESOLUTION METHOD ANALYSIS
    // ═══════════════════════════════════════════════════════════════════

    private void analyzeResolutionMethods(List<CrisisEvent> events, CrisisProfile profile) {
        Map<String, Integer> methodCounts = new HashMap<>();
        Map<String, Float> methodDistressReduction = new HashMap<>();

        for (CrisisEvent event : events) {
            if (event.resolutionMethod != null && !"unresolved".equals(event.status)) {
                methodCounts.merge(event.resolutionMethod, 1, Integer::sum);

                if (event.postDistressLevel > 0 && event.preDistressLevel > 0) {
                    float reduction = event.preDistressLevel - event.postDistressLevel;
                    methodDistressReduction.merge(event.resolutionMethod, reduction, Float::sum);
                }
            }
        }

        // Find most used method
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : methodCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                profile.preferredMethod = entry.getKey();
            }
        }

        // Find most effective method
        float bestAvg = 0;
        for (Map.Entry<String, Float> entry : methodDistressReduction.entrySet()) {
            int count = methodCounts.getOrDefault(entry.getKey(), 1);
            float avg = entry.getValue() / count;
            if (avg > bestAvg) {
                bestAvg = avg;
                profile.mostEffectiveMethod = entry.getKey();
                profile.bestMethodReduction = avg;
            }
        }

        profile.resolutionMethodCounts = methodCounts;
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIME PATTERN ANALYSIS
    // ═══════════════════════════════════════════════════════════════════

    private void analyzeTimePatterns(List<CrisisEvent> events, CrisisProfile profile) {
        int nightCount = 0;
        int morningCount = 0;
        int eveningCount = 0;

        Calendar cal = Calendar.getInstance();
        for (CrisisEvent event : events) {
            cal.setTimeInMillis(event.createdAt);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour >= 22 || hour < 6) nightCount++;
            else if (hour >= 6 && hour < 12) morningCount++;
            else eveningCount++;
        }

        profile.nightCrisisCount = nightCount;
        profile.totalCrisisCount = events.size();

        if (events.size() >= 3) {
            float nightRatio = (float) nightCount / events.size();
            profile.isNightTimeProne = nightRatio > 0.4f;
            profile.peakCrisisTime = nightCount >= morningCount && nightCount >= eveningCount ?
                    "Night" : (morningCount >= eveningCount ? "Morning" : "Evening");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FREQUENCY ANALYSIS
    // ═══════════════════════════════════════════════════════════════════

    private void analyzeCrisisFrequency(List<CrisisEvent> events, CrisisProfile profile) {
        if (events.size() < 2) return;

        long now = System.currentTimeMillis();
        int lastWeek = 0;
        int last2Weeks = 0;

        for (CrisisEvent event : events) {
            long age = now - event.createdAt;
            if (age < WEEK_MS) lastWeek++;
            if (age < 2 * WEEK_MS) last2Weeks++;
        }

        profile.crisesLastWeek = lastWeek;
        profile.crisesLast2Weeks = last2Weeks;

        // Trend detection
        int firstWeek = last2Weeks - lastWeek;
        if (lastWeek > firstWeek + 1) {
            profile.frequencyTrend = "increasing";
        } else if (lastWeek < firstWeek - 1) {
            profile.frequencyTrend = "decreasing";
        } else {
            profile.frequencyTrend = "stable";
        }

        // Short crisis detection (resolved in < 10 min)
        int shortCount = 0;
        for (CrisisEvent event : events) {
            if (event.resolvedAt > 0 && (event.resolvedAt - event.createdAt) < 10 * 60 * 1000) {
                shortCount++;
            }
        }
        profile.shortCrisisRatio = events.size() > 0 ? (float) shortCount / events.size() : 0f;
        profile.hasFrequentShortCrises = profile.shortCrisisRatio > 0.6f && events.size() >= 3;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXERCISE EFFECTIVENESS
    // ═══════════════════════════════════════════════════════════════════

    private void analyzeExerciseEffectiveness(List<ExerciseCompletion> exercises, CrisisProfile profile) {
        if (exercises.isEmpty()) return;

        float breathingTotal = 0, groundingTotal = 0;
        int breathingCount = 0, groundingCount = 0;

        for (ExerciseCompletion c : exercises) {
            float reduction = c.preDistressLevel > 0 && c.postDistressLevel > 0 ?
                    c.preDistressLevel - c.postDistressLevel : 0;
            if ("breathing".equals(c.exerciseType)) {
                breathingTotal += reduction;
                breathingCount++;
            } else if ("grounding".equals(c.exerciseType)) {
                groundingTotal += reduction;
                groundingCount++;
            }
        }

        float breathingAvg = breathingCount > 0 ? breathingTotal / breathingCount : 0;
        float groundingAvg = groundingCount > 0 ? groundingTotal / groundingCount : 0;

        profile.breathingMoreEffective = breathingAvg > groundingAvg;
        profile.exerciseBreathingAvgReduction = breathingAvg;
        profile.exerciseGroundingAvgReduction = groundingAvg;

        // Recommended exercise
        ExerciseAnalyticsEngine.ExerciseReport report = analyticsEngine.generateReport();
        profile.recommendedExercise = report.mostEffectiveExercise;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERSONALIZED MESSAGE
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private String generatePersonalizedMessage(CrisisProfile profile) {
        List<String> parts = new ArrayList<>();

        if (profile.isFirstCrisis) {
            return "We're here with you. You're not alone in this.";
        }

        // Acknowledge past success
        if (profile.totalCrisisCount > 1) {
            parts.add(String.format(Locale.US,
                    "You've navigated %d difficult moments before — you can do this again.",
                    profile.totalCrisisCount));
        }

        // Suggest what worked
        if (profile.mostEffectiveMethod != null) {
            String method = profile.mostEffectiveMethod.replace("_", " ");
            parts.add(String.format(Locale.US,
                    "Last time, %s helped the most (%.1f point reduction).",
                    method, profile.bestMethodReduction));
        }

        // Decreasing trend
        if ("decreasing".equals(profile.frequencyTrend)) {
            parts.add("Your difficult moments are becoming less frequent — that's real progress.");
        }

        // Frequent short crises
        if (profile.hasFrequentShortCrises) {
            parts.add("Most of your tough moments resolve quickly — you have strong coping skills.");
        }

        if (parts.isEmpty()) {
            return "This moment is temporary. You have the tools to get through it.";
        }

        return String.join("\n\n", parts);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTION ORDER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Determine the recommended order of actions based on what's worked before.
     */
    @NonNull
    private List<String> determineActionOrder(CrisisProfile profile) {
        List<String> actions = new ArrayList<>();

        // Primary: what worked best
        if (profile.breathingMoreEffective) {
            actions.add("breathing_exercise");
            actions.add("grounding_exercise");
        } else {
            actions.add("grounding_exercise");
            actions.add("breathing_exercise");
        }

        // If contacting friends worked
        if (profile.resolutionMethodCounts != null) {
            int contactCount = profile.resolutionMethodCounts.getOrDefault("contacted_friend", 0);
            if (contactCount > 1) {
                actions.add(1, "contact_trusted"); // Move up
            } else {
                actions.add("contact_trusted");
            }
        } else {
            actions.add("contact_trusted");
        }

        actions.add("safety_plan");
        actions.add("helpline");

        return actions;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    public static class CrisisProfile {
        // Identity
        public boolean isFirstCrisis = false;
        public int totalCrisisCount = 0;

        // Resolution methods
        public String preferredMethod;
        public String mostEffectiveMethod;
        public float bestMethodReduction = 0f;
        public Map<String, Integer> resolutionMethodCounts;

        // Time patterns
        public boolean isNightTimeProne = false;
        public int nightCrisisCount = 0;
        public String peakCrisisTime;

        // Frequency
        public int crisesLastWeek = 0;
        public int crisesLast2Weeks = 0;
        public String frequencyTrend; // "increasing", "decreasing", "stable"
        public float shortCrisisRatio = 0f;
        public boolean hasFrequentShortCrises = false;

        // Exercises
        public boolean breathingMoreEffective = true;
        public float exerciseBreathingAvgReduction = 0f;
        public float exerciseGroundingAvgReduction = 0f;
        public String recommendedExercise;

        // UI adaptations
        public String personalizedMessage;
        public List<String> recommendedActions = new ArrayList<>();
    }
}
