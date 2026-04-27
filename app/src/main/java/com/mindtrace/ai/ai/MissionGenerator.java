package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Daily Mission Generator — transforms raw intervention tasks into a
 * structured daily mission with thematic coherence, progress tracking,
 * and contextual trap warnings.
 *
 * <h3>Architecture Role:</h3>
 * <pre>
 *   InterventionEngine.generateFromClassification()
 *       → produces raw List&lt;InterventionTask&gt;
 *   TrapWatchGenerator.generateWarnings()
 *       → produces List&lt;TrapWarning&gt;
 *   MissionGenerator.buildMission()
 *       → combines both into TodayMission (structured daily plan)
 * </pre>
 *
 * <h3>Mission Structure:</h3>
 * <ul>
 *   <li><b>Title</b> — thematic, encouraging headline based on primary risk</li>
 *   <li><b>Steps</b> — top 3 tasks selected for maximum impact</li>
 *   <li><b>Progress</b> — real-time completion percentage</li>
 *   <li><b>Warnings</b> — contextual trap warnings from TrapWatchGenerator</li>
 *   <li><b>Bonus</b> — optional stretch goal for high-engagement users</li>
 *   <li><b>Narrative</b> — motivational micro-copy explaining today's focus</li>
 * </ul>
 *
 * @see InterventionEngine
 * @see TrapWatchGenerator
 */
public class MissionGenerator {

    private static final int MAX_MISSION_STEPS = 3;
    private static final int MAX_WARNINGS = 4;

    // ═══════════════════════════════════════════════════════════════════
    // PRIMARY API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Build today's mission from generated tasks and trap warnings.
     *
     * @param classification current risk classification
     * @param tasks          generated intervention tasks (from InterventionEngine)
     * @param trapWarnings   behavioral trap warnings (from TrapWatchGenerator)
     * @return structured daily mission
     */
    @NonNull
    public TodayMission buildMission(
            @NonNull RiskClassification classification,
            @NonNull List<InterventionTask> tasks,
            @Nullable List<TrapWatchGenerator.TrapWarning> trapWarnings) {

        TodayMission mission = new TodayMission();
        mission.generatedAt = System.currentTimeMillis();

        // ── 1. Mission title based on primary concern ──
        mission.title = generateMissionTitle(classification.primaryCategory);

        // ── 2. Mission narrative (why this focus today) ──
        mission.narrative = generateNarrative(classification);

        // ── 3. Select top steps (max 3, priority-sorted) ──
        mission.steps = selectMissionSteps(tasks);

        // ── 4. Calculate progress ──
        mission.progress = calculateProgress(mission.steps);
        mission.completedCount = countCompleted(mission.steps);
        mission.totalCount = mission.steps.size();

        // ── 5. Attach trap warnings ──
        if (trapWarnings != null && !trapWarnings.isEmpty()) {
            int limit = Math.min(MAX_WARNINGS, trapWarnings.size());
            mission.warnings = new ArrayList<>(trapWarnings.subList(0, limit));
        }

        // ── 6. Risk context ──
        mission.riskLevel = classification.getOverallSeverity().label;
        mission.primaryFocus = classification.primaryCategory != null
                ? classification.primaryCategory.replace("_", " ")
                : "overall wellness";

        // ── 7. Bonus stretch goal (for high-engagement users) ──
        if (tasks.size() > MAX_MISSION_STEPS) {
            mission.bonusTask = tasks.get(MAX_MISSION_STEPS);
        }

        // ── 8. Time-of-day greeting ──
        mission.greeting = generateGreeting();

        // ── 9. Streak encouragement ──
        mission.encouragement = generateEncouragement(classification, mission.progress);

        return mission;
    }

    /**
     * Build a minimal mission when no classification is available.
     * Uses only the task list to create a gentle daily plan.
     */
    @NonNull
    public TodayMission buildFallbackMission(@NonNull List<InterventionTask> tasks) {
        TodayMission mission = new TodayMission();
        mission.generatedAt = System.currentTimeMillis();
        mission.title = "Focus on what matters most today";
        mission.narrative = "Start small. Every completed task is a step forward.";
        mission.steps = selectMissionSteps(tasks);
        mission.progress = calculateProgress(mission.steps);
        mission.completedCount = countCompleted(mission.steps);
        mission.totalCount = mission.steps.size();
        mission.riskLevel = "Unknown";
        mission.primaryFocus = "general wellness";
        mission.greeting = generateGreeting();
        mission.encouragement = "You're here — that already matters.";
        return mission;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MISSION TITLE GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate an encouraging, thematic mission title based on the
     * user's primary risk category. Avoids clinical language.
     */
    @NonNull
    private String generateMissionTitle(@Nullable String primaryCategory) {
        if (primaryCategory == null) return "Focus on what matters most today";

        switch (primaryCategory) {
            case "digital_addiction":
                return "Build focus and reduce digital noise";
            case "stress_anxiety":
                return "Calm your mind and restore balance";
            case "depression":
                return "Take small, gentle steps forward";
            case "social_isolation":
                return "Strengthen your human connections";
            case "sleep_disruption":
                return "Reset your rest and recharge";
            case "low_fulfilment":
                return "Reconnect with purpose and meaning";
            default:
                return "Focus on what matters most today";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NARRATIVE GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a motivational micro-narrative explaining today's focus.
     * Adapts tone to risk severity — gentler at HIGH, encouraging at LOW.
     */
    @NonNull
    private String generateNarrative(@NonNull RiskClassification classification) {
        RiskClassification.Severity severity = classification.getOverallSeverity();
        String category = classification.primaryCategory != null
                ? classification.primaryCategory : "general";

        switch (severity) {
            case SEVERE:
            case HIGH:
                return generateHighRiskNarrative(category, classification);
            case MODERATE:
                return generateModerateNarrative(category, classification);
            case WATCH:
                return generateWatchNarrative(category);
            default:
                return generateLowRiskNarrative(category);
        }
    }

    @NonNull
    private String generateHighRiskNarrative(@NonNull String category,
                                              @NonNull RiskClassification rc) {
        switch (category) {
            case "digital_addiction":
                return "Your digital patterns are running high. Today's mission " +
                        "focuses on creating intentional pauses. Even small breaks make a difference.";
            case "stress_anxiety":
                return "Your stress indicators are elevated. Let's focus on calming " +
                        "your nervous system today. Start with the easiest task.";
            case "depression":
                return "Things feel heavy right now. Today's tasks are gentle — " +
                        "no pressure. Even one small step counts.";
            case "social_isolation":
                return "Connection matters, especially now. Today's mission includes " +
                        "one small social step. You don't have to do it alone.";
            case "sleep_disruption":
                return "Your sleep patterns need attention. Tonight's focus is on " +
                        "creating a calmer wind-down routine.";
            case "low_fulfilment":
                return "Purpose can feel distant when you're struggling. Today's tasks " +
                        "are designed to reconnect you with what matters.";
            default:
                return "Today calls for extra care. Start with one small, gentle action.";
        }
    }

    @NonNull
    private String generateModerateNarrative(@NonNull String category,
                                              @NonNull RiskClassification rc) {
        boolean improving = rc.isImproving();
        String trend = improving ? " Your trend is improving — keep it up." : "";

        switch (category) {
            case "digital_addiction":
                return "Your screen habits need some attention. Today's tasks " +
                        "help you build healthier boundaries." + trend;
            case "stress_anxiety":
                return "Stress is moderate but manageable. These tasks help " +
                        "you stay ahead of it." + trend;
            case "depression":
                return "You're in a challenging zone. Small wins today build " +
                        "momentum for tomorrow." + trend;
            default:
                return "Today's mission targets your primary area of concern. " +
                        "Steady progress adds up." + trend;
        }
    }

    @NonNull
    private String generateWatchNarrative(@NonNull String category) {
        return "Some patterns are worth watching. Today's tasks are " +
                "preventive — keeping things on track.";
    }

    @NonNull
    private String generateLowRiskNarrative(@NonNull String category) {
        return "Things are looking good! Today's tasks help maintain " +
                "your wellbeing and build resilience.";
    }

    // ═══════════════════════════════════════════════════════════════════
    // STEP SELECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Select the top mission steps from the full task list.
     * Prioritizes: crisis > high-priority > variety across categories.
     */
    @NonNull
    private List<InterventionTask> selectMissionSteps(@NonNull List<InterventionTask> tasks) {
        if (tasks.size() <= MAX_MISSION_STEPS) {
            return new ArrayList<>(tasks);
        }

        // Sort by priority descending, then by micro-intervention status
        List<InterventionTask> sorted = new ArrayList<>(tasks);
        sorted.sort((a, b) -> {
            // Micro-interventions first
            if (a.isMicroIntervention && !b.isMicroIntervention) return -1;
            if (!a.isMicroIntervention && b.isMicroIntervention) return 1;
            // Then by priority
            if (a.priority != b.priority) return b.priority - a.priority;
            // Then by duration (shorter first for approachability)
            return a.durationMinutes - b.durationMinutes;
        });

        // Select top tasks while ensuring category diversity
        List<InterventionTask> selected = new ArrayList<>();
        java.util.Set<String> usedCategories = new java.util.HashSet<>();

        // First pass: one per category
        for (InterventionTask task : sorted) {
            if (selected.size() >= MAX_MISSION_STEPS) break;
            String cat = task.category != null ? task.category : "General";
            if (!usedCategories.contains(cat)) {
                selected.add(task);
                usedCategories.add(cat);
            }
        }

        // Second pass: fill remaining slots
        for (InterventionTask task : sorted) {
            if (selected.size() >= MAX_MISSION_STEPS) break;
            if (!selected.contains(task)) {
                selected.add(task);
            }
        }

        return selected;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROGRESS & ENCOURAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    private float calculateProgress(@NonNull List<InterventionTask> steps) {
        if (steps.isEmpty()) return 0f;
        int done = countCompleted(steps);
        return (float) done / steps.size();
    }

    private int countCompleted(@NonNull List<InterventionTask> steps) {
        int done = 0;
        for (InterventionTask t : steps) {
            if ("COMPLETED".equals(t.status) || t.isCompleted) done++;
        }
        return done;
    }

    @NonNull
    private String generateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) return "Still up? Let's wind down.";
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        if (hour < 21) return "Good evening";
        return "It's getting late — be gentle with yourself";
    }

    @NonNull
    private String generateEncouragement(@NonNull RiskClassification classification,
                                          float progress) {
        if (progress >= 1.0f) {
            return "🌟 Mission complete! You showed up for yourself today.";
        }
        if (progress >= 0.66f) {
            return "Almost there — one more step to go!";
        }
        if (progress > 0f) {
            return "Great start. Keep the momentum going.";
        }

        // No progress yet — adapt to severity
        if (classification.getOverallSeverity().isCritical()) {
            return "Start with the smallest task. You've got this.";
        }
        return "Your mission awaits. Start whenever you're ready.";
    }

    // ═══════════════════════════════════════════════════════════════════
    // MISSION ANALYTICS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Analyze historical missions to find which mission themes lead to
     * the highest completion rates. Used for reinforcement learning.
     */
    @NonNull
    public MissionAnalytics analyzeMissionHistory(
            @NonNull List<InterventionTask> allTaskHistory) {

        MissionAnalytics analytics = new MissionAnalytics();

        // Group by sourceTag to understand which generation pathways work best
        Map<String, int[]> sourceStats = new HashMap<>();
        // Group by category to find category completion rates
        Map<String, int[]> categoryStats = new HashMap<>();

        for (InterventionTask task : allTaskHistory) {
            String source = task.sourceTag != null ? task.sourceTag : "unknown";
            String cat = task.category != null ? task.category : "General";

            sourceStats.computeIfAbsent(source, k -> new int[]{0, 0});
            categoryStats.computeIfAbsent(cat, k -> new int[]{0, 0});

            sourceStats.get(source)[0]++; // total
            categoryStats.get(cat)[0]++;

            if ("COMPLETED".equals(task.status) || task.isCompleted) {
                sourceStats.get(source)[1]++; // completed
                categoryStats.get(cat)[1]++;
            }
        }

        // Find best performing source
        float bestSourceRate = 0;
        for (Map.Entry<String, int[]> entry : sourceStats.entrySet()) {
            int[] stats = entry.getValue();
            float rate = stats[0] > 0 ? (float) stats[1] / stats[0] : 0;
            if (rate > bestSourceRate && stats[0] >= 3) {
                bestSourceRate = rate;
                analytics.bestSource = entry.getKey();
                analytics.bestSourceRate = rate;
            }
        }

        // Find best and worst categories
        float bestCatRate = -1f, worstCatRate = 2f;
        for (Map.Entry<String, int[]> entry : categoryStats.entrySet()) {
            int[] stats = entry.getValue();
            if (stats[0] < 2) continue;
            float rate = (float) stats[1] / stats[0];
            if (rate > bestCatRate) {
                bestCatRate = rate;
                analytics.strongestCategory = entry.getKey();
            }
            if (rate < worstCatRate) {
                worstCatRate = rate;
                analytics.weakestCategory = entry.getKey();
            }
        }

        analytics.totalMissions = allTaskHistory.size();
        analytics.overallCompletionRate = allTaskHistory.isEmpty() ? 0f :
                (float) countAllCompleted(allTaskHistory) / allTaskHistory.size();

        return analytics;
    }

    private int countAllCompleted(@NonNull List<InterventionTask> tasks) {
        int count = 0;
        for (InterventionTask t : tasks) {
            if ("COMPLETED".equals(t.status) || t.isCompleted) count++;
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Structured daily mission — the primary UI-facing data object.
     */
    public static class TodayMission {
        /** When the mission was generated. */
        public long generatedAt;

        /** Thematic title (e.g., "Calm your mind and restore balance"). */
        public String title;

        /** Motivational narrative explaining today's focus. */
        public String narrative;

        /** Time-of-day greeting. */
        public String greeting;

        /** Top 3 tasks selected as mission steps. */
        public List<InterventionTask> steps = new ArrayList<>();

        /** Completion progress (0.0 – 1.0). */
        public float progress;

        /** Number of completed steps. */
        public int completedCount;

        /** Total number of mission steps. */
        public int totalCount;

        /** Optional bonus/stretch task for high-engagement users. */
        @Nullable
        public InterventionTask bonusTask;

        /** Behavioral trap warnings. */
        public List<TrapWatchGenerator.TrapWarning> warnings = new ArrayList<>();

        /** Current risk level label. */
        public String riskLevel;

        /** Primary focus area (human-readable). */
        public String primaryFocus;

        /** Contextual encouragement message. */
        public String encouragement;

        /** Whether the mission is fully completed. */
        public boolean isComplete() {
            return progress >= 1.0f && totalCount > 0;
        }

        /** Short summary for notifications. */
        @NonNull
        public String toShortSummary() {
            return String.format(Locale.US, "%s — %d/%d completed",
                    title, completedCount, totalCount);
        }

        @NonNull
        @Override
        public String toString() {
            return "TodayMission{" +
                    "title='" + title + '\'' +
                    ", progress=" + String.format(Locale.US, "%.0f%%", progress * 100) +
                    ", steps=" + totalCount +
                    ", warnings=" + warnings.size() +
                    '}';
        }
    }

    /**
     * Historical mission performance analytics.
     */
    public static class MissionAnalytics {
        public int totalMissions;
        public float overallCompletionRate;
        public String bestSource;
        public float bestSourceRate;
        public String strongestCategory;
        public String weakestCategory;
    }
}
