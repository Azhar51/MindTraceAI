package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.behavior.BehaviorThresholds;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.database.entity.UserBaseline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI-powered intervention engine that generates personalized tasks based on
 * the user's current risk classification, crisis level, behavioral signals,
 * onboarding profile, and completion history.
 *
 * <h3>Intelligence Pipeline:</h3>
 * <pre>
 *   RiskClassification + CrisisLevel + BehaviorReport + OnboardingProfile
 *       ↓
 *   Risk category → TaskTemplateRepository (84 templates)
 *       ↓
 *   Difficulty calibration (based on completion history)
 *       ↓
 *   Comorbidity-aware selection (depression+isolation → combo tasks)
 *       ↓
 *   "Why this task" reason generation
 *       ↓
 *   Dosage control (3-5 tasks/day max)
 *       ↓
 *   Deduplication (skip tasks completed/skipped in last 48h)
 *       ↓
 *   InterventionTask[] ready to persist
 * </pre>
 */
public class InterventionEngine {

    private static final int MAX_TASKS_PER_DAY = 5;
    private static final int MIN_TASKS_PER_DAY = 2;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final TaskTemplateRepository templateRepo;

    public InterventionEngine() {
        this.templateRepo = TaskTemplateRepository.getInstance();
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEGACY ENTRY POINT (backward compatible)
    // ═══════════════════════════════════════════════════════════════════

    public List<InterventionTask> generateTasks(MentalStateClassifier.State state) {
        return generateTasks(state, null, null, null, null);
    }

    public List<InterventionTask> generateTasks(
            MentalStateClassifier.State state,
            BehaviorReport behaviorReport,
            AnomalyDetector.AnomalyProfile anomalyProfile,
            UserBaseline baseline,
            List<InterventionTask> taskHistory
    ) {
        return generateTasks(state, behaviorReport, anomalyProfile, baseline, taskHistory, null);
    }

    /**
     * Efficacy-aware legacy entry point.
     * Generates state/behavior-based tasks and, when historical efficacy data is
     * available, injects an additional task from the most effective category.
     *
     * @param categoryEfficacy map of risk-category → avgEfficacy, or null
     */
    public List<InterventionTask> generateTasks(
            MentalStateClassifier.State state,
            BehaviorReport behaviorReport,
            AnomalyDetector.AnomalyProfile anomalyProfile,
            UserBaseline baseline,
            List<InterventionTask> taskHistory,
            java.util.Map<String, Float> categoryEfficacy
    ) {
        List<InterventionTask> tasks = new ArrayList<>();
        long now = System.currentTimeMillis();
        String recommendedAction = getRecommendedAction(behaviorReport, anomalyProfile, baseline);
        StrategyProfile strategyProfile = summarizeHistory(taskHistory, recommendedAction);

        if (!recommendedAction.isEmpty()) {
            addActionTasks(tasks, adaptAction(recommendedAction, strategyProfile), now);
        }

        // Fall back to state-based generation
        addStateTasks(tasks, state, now);

        // ── Efficacy-weighted reinforcement (closed-loop) ──
        // If we have measured efficacy data, add a task from the historically
        // most effective category to reinforce what works for this user.
        if (categoryEfficacy != null && !categoryEfficacy.isEmpty() && tasks.size() < MAX_TASKS_PER_DAY) {
            addEfficacyWeightedTask(tasks, categoryEfficacy,
                    null, null, "EASY", MAX_TASKS_PER_DAY);
        }

        if (tasks.isEmpty()) {
            tasks.add(createTask("Gentle Reset", "Take a short mindful pause away from your phone.",
                    "Recovery", 5, now));
        }

        return tasks;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED ENTRY POINT — RiskClassification + CrisisLevel
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate tasks from the full AI classification pipeline.
     * This is the primary entry point for Module 4 advanced generation.
     */
    @NonNull
    public List<InterventionTask> generateFromClassification(
            @NonNull RiskClassification classification,
            @NonNull MultiModalClassifier.CrisisLevel crisisLevel,
            @Nullable BehaviorReport behaviorReport,
            @Nullable OnboardingProfile profile,
            @Nullable List<InterventionTask> taskHistory,
            @Nullable String[] comorbidities
    ) {
        return generateFromClassification(
                classification, crisisLevel, behaviorReport, profile,
                taskHistory, comorbidities, null);
    }

    /**
     * Full entry point for task generation with efficacy-weighted selection.
     *
     * <p>When {@code categoryEfficacy} is provided (a map of risk-category → average
     * efficacy score from historical data), the engine inserts an additional task
     * from the historically most-effective category. This creates a reinforcement
     * loop: categories that demonstrably reduce risk for this user are favored.</p>
     *
     * @param categoryEfficacy map of risk category → average efficacy score, or null
     */
    @NonNull
    public List<InterventionTask> generateFromClassification(
            @NonNull RiskClassification classification,
            @NonNull MultiModalClassifier.CrisisLevel crisisLevel,
            @Nullable BehaviorReport behaviorReport,
            @Nullable OnboardingProfile profile,
            @Nullable List<InterventionTask> taskHistory,
            @Nullable String[] comorbidities,
            @Nullable java.util.Map<String, Float> categoryEfficacy
    ) {
        List<InterventionTask> tasks = new ArrayList<>();
        long now = System.currentTimeMillis();
        StrategyProfile strategyProfile = summarizeHistory(taskHistory, classification.primaryCategory);

        // ── 1. Determine calibrated difficulty ──
        String difficulty = calibrateDifficulty(strategyProfile);

        // ── 2. Determine dosage (how many tasks to generate) ──
        int dosage = computeDosage(strategyProfile, crisisLevel);

        // ── 3. Crisis micro-interventions first ──
        if (crisisLevel.requiresMonitoring()) {
            addCrisisTasks(tasks, crisisLevel, classification);
        }

        // ── 4. Primary risk category tasks ──
        if (classification.primaryCategory != null) {
            addTemplatedTasks(tasks, classification.primaryCategory, difficulty,
                    buildWhyReason(classification, "primary"), dosage);
        }

        // ── 4.5 Efficacy-weighted reinforcement ──
        // If we have measured efficacy data, add a task from the historically
        // most effective category (if it differs from primary/secondary).
        if (categoryEfficacy != null && !categoryEfficacy.isEmpty()
                && tasks.size() < dosage) {
            addEfficacyWeightedTask(tasks, categoryEfficacy,
                    classification.primaryCategory,
                    classification.secondaryCategory,
                    difficulty, dosage);
        }

        // ── 5. Comorbidity-aware combo tasks ──
        if (comorbidities != null) {
            for (String combo : comorbidities) {
                addComorbidityTasks(tasks, combo, difficulty);
            }
        }

        // ── 6. Secondary category tasks (if dosage allows) ──
        if (tasks.size() < dosage && classification.secondaryCategory != null
                && !classification.secondaryCategory.isEmpty()) {
            addTemplatedTasks(tasks, classification.secondaryCategory, difficulty,
                    buildWhyReason(classification, "secondary"), dosage);
        }

        // ── 7. OnboardingProfile personalization ──
        if (profile != null) {
            personalizeFromProfile(tasks, profile, difficulty, dosage);
        }

        // ── 8. Behavior-driven tasks ──
        if (behaviorReport != null && tasks.size() < dosage) {
            addBehaviorTasks(tasks, behaviorReport, now);
        }

        // ── 9. Enforce dosage limit ──
        while (tasks.size() > dosage) {
            tasks.remove(tasks.size() - 1);
        }

        // ── 10. Fallback ──
        if (tasks.isEmpty()) {
            TaskTemplateRepository.Template t = templateRepo.getRandomTemplate();
            if (t != null) {
                tasks.add(templateRepo.toTask(t, "A gentle reset for your day.", "fallback"));
            }
        }

        return tasks;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIFFICULTY CALIBRATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calibrate difficulty based on user's completion history.
     * Start EASY, escalate to MEDIUM/HARD based on completion rate.
     */
    @NonNull
    private String calibrateDifficulty(@NonNull StrategyProfile profile) {
        int total = profile.completedCount + profile.ignoredCount;
        if (total < 5) return "EASY"; // Not enough history

        float completionRate = (float) profile.completedCount / total;
        if (completionRate > 0.75f && total >= 10) return "HARD";
        if (completionRate > 0.50f) return "MEDIUM";
        return "EASY"; // Low completion → keep it easy
    }

    // ═══════════════════════════════════════════════════════════════════
    // DOSAGE CONTROL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute how many tasks to generate based on engagement and crisis level.
     */
    private int computeDosage(@NonNull StrategyProfile profile, @NonNull MultiModalClassifier.CrisisLevel crisisLevel) {
        int base = 3;

        // High engagement → more tasks
        if (profile.completedCount > profile.ignoredCount * 2) base = 4;
        if (profile.completedCount > 15) base = 5;

        // Low engagement → fewer, easier tasks
        if (profile.ignoredCount > profile.completedCount * 2) base = 2;

        // Crisis → at least 3 (including micro-intervention)
        if (crisisLevel.requiresMonitoring()) base = Math.max(base, 3);

        return Math.max(MIN_TASKS_PER_DAY, Math.min(MAX_TASKS_PER_DAY, base));
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS TASKS
    // ═══════════════════════════════════════════════════════════════════

    private void addCrisisTasks(@NonNull List<InterventionTask> tasks,
                                @NonNull MultiModalClassifier.CrisisLevel level,
                                @NonNull RiskClassification classification) {
        // Always add a micro-intervention for crisis
        String riskCat = classification.primaryCategory != null ? classification.primaryCategory : "stress_anxiety";
        List<TaskTemplateRepository.Template> micros = templateRepo.getMicroInterventions();
        for (TaskTemplateRepository.Template micro : micros) {
            if (riskCat.equals(micro.linkedRisk)) {
                String why = "Crisis level: " + level.label + " — immediate relief matters.";
                addIfMissing(tasks, templateRepo.toTask(micro, why, "crisis"));
                break;
            }
        }

        // If URGENT or CRITICAL, add a second micro
        if (level.requiresImmediateAction() && micros.size() > 1) {
            TaskTemplateRepository.Template second = templateRepo.getRandomMicroIntervention();
            if (second != null) {
                addIfMissing(tasks, templateRepo.toTask(second,
                        "Immediate support — you're going through a tough moment.", "crisis"));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEMPLATE-BASED GENERATION
    // ═══════════════════════════════════════════════════════════════════

    private void addTemplatedTasks(@NonNull List<InterventionTask> tasks,
                                    @NonNull String riskCategory,
                                    @NonNull String difficulty,
                                    @NonNull String whyReason,
                                    int dosage) {
        TaskTemplateRepository.Template t = templateRepo.getRandom(riskCategory, difficulty);
        if (t != null && tasks.size() < dosage) {
            addIfMissing(tasks, templateRepo.toTask(t, whyReason, "classification"));
        }

        // Also add one from a different difficulty for variety
        String altDiff = "EASY".equals(difficulty) ? "MEDIUM" : "EASY";
        TaskTemplateRepository.Template alt = templateRepo.getRandom(riskCategory, altDiff);
        if (alt != null && tasks.size() < dosage) {
            addIfMissing(tasks, templateRepo.toTask(alt, whyReason, "classification"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMORBIDITY-AWARE SELECTION
    // ═══════════════════════════════════════════════════════════════════

    private void addComorbidityTasks(@NonNull List<InterventionTask> tasks,
                                      @NonNull String comorbidity,
                                      @NonNull String difficulty) {
        switch (comorbidity) {
            case "depression_isolation_loop":
                // Social + Purpose combo
                addFromCategory(tasks, "social_isolation", difficulty,
                        "Pattern detected: depression + isolation are reinforcing each other. Social connection helps break the loop.");
                break;
            case "burnout_triad":
                // Recovery focus
                addFromCategory(tasks, "stress_anxiety", "EASY",
                        "Burnout pattern detected: high stress + low energy + low fulfilment. Start with recovery.");
                break;
            case "digital_escape_cycle":
                // Detox + Purpose
                addFromCategory(tasks, "digital_addiction", difficulty,
                        "Escape pattern: digital overuse is masking underlying distress. Intentional breaks help.");
                break;
            case "sleep_stress_spiral":
                addFromCategory(tasks, "sleep_disruption", "EASY",
                        "Sleep and stress are feeding each other. Prioritize wind-down tonight.");
                break;
        }
    }

    private void addFromCategory(@NonNull List<InterventionTask> tasks, @NonNull String risk,
                                  @NonNull String difficulty, @NonNull String why) {
        TaskTemplateRepository.Template t = templateRepo.getRandom(risk, difficulty);
        if (t != null) {
            addIfMissing(tasks, templateRepo.toTask(t, why, "comorbidity"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ONBOARDING PERSONALIZATION
    // ═══════════════════════════════════════════════════════════════════

    private void personalizeFromProfile(@NonNull List<InterventionTask> tasks,
                                         @NonNull OnboardingProfile profile,
                                         @NonNull String difficulty, int dosage) {
        if (tasks.size() >= dosage) return;

        // Low exercise → suggest movement
        if (profile.physicalActivity <= 2) {
            TaskTemplateRepository.Template t = templateRepo.getRandom("depression", "MEDIUM");
            if (t != null && t.title.toLowerCase().contains("walk")) {
                addIfMissing(tasks, templateRepo.toTask(t,
                        "Your profile shows low physical activity — movement lifts mood.", "onboarding"));
            }
        }

        // Low social support → suggest social tasks
        if (profile.socialSupportLevel <= 2 && tasks.size() < dosage) {
            TaskTemplateRepository.Template t = templateRepo.getRandom("social_isolation", difficulty);
            if (t != null) {
                addIfMissing(tasks, templateRepo.toTask(t,
                        "You indicated limited social support — small connections help.", "onboarding"));
            }
        }

        // High stress baseline → mindfulness
        if (profile.stressLevel >= 4 && tasks.size() < dosage) {
            TaskTemplateRepository.Template t = templateRepo.getRandom("stress_anxiety", "EASY");
            if (t != null) {
                addIfMissing(tasks, templateRepo.toTask(t,
                        "Your stress baseline is elevated — quick breathing resets help.", "onboarding"));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // "WHY THIS TASK" REASON GENERATION
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private String buildWhyReason(@NonNull RiskClassification classification, @NonNull String which) {
        String category = "primary".equals(which)
                ? classification.primaryCategory
                : classification.secondaryCategory;
        float score = classification.getScoreForCategory(category != null ? category : "overall");
        String catLabel = category != null ? category.replace("_", " ") : "overall wellness";

        return String.format(Locale.US,
                "Your %s score is %.0f%% — this task directly targets that area.",
                catLabel, score * 100);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MICRO-INTERVENTION GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a single micro-intervention for immediate crisis relief.
     * Called directly from TaskViewModel.requestMicroIntervention().
     */
    @Nullable
    public InterventionTask generateMicroIntervention(@Nullable String riskCategory) {
        if (riskCategory != null) {
            List<TaskTemplateRepository.Template> micros = templateRepo.getMicroInterventions();
            for (TaskTemplateRepository.Template m : micros) {
                if (riskCategory.equals(m.linkedRisk)) {
                    return templateRepo.toTask(m, "Quick relief for right now.", "crisis");
                }
            }
        }
        TaskTemplateRepository.Template any = templateRepo.getRandomMicroIntervention();
        return any != null ? templateRepo.toTask(any, "A moment of calm.", "crisis") : null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEGACY: STATE-BASED TASKS (kept for backward compat)
    // ═══════════════════════════════════════════════════════════════════

    private void addStateTasks(List<InterventionTask> tasks, MentalStateClassifier.State state, long now) {
        switch (state) {
            case DIGITAL_ADDICTION:
                addIfMissing(tasks, createTask("Digital Detox", "Put your phone away 30 minutes before sleep.", "Detox", 30, now));
                addIfMissing(tasks, createTask("Analog Break", "Spend 1 hour doing a non-digital hobby.", "Detox", 60, now));
                break;
            case EMOTIONAL_FATIGUE:
                addIfMissing(tasks, createTask("Sleep Reset", "Avoid screens for 45 minutes and prepare for rest.", "Recovery", 45, now));
                addIfMissing(tasks, createTask("Gentle Recharge", "Take a 10-minute quiet break.", "Recovery", 10, now));
                break;
            case STRESS_ANXIETY:
                addIfMissing(tasks, createTask("Mindful Breathing", "Complete a 5-minute deep breathing exercise.", "Mindfulness", 5, now));
                addIfMissing(tasks, createTask("Grounding", "Name 5 things you see, 4 you feel, 3 you hear.", "Mindfulness", 5, now));
                break;
            case EARLY_DEPRESSION_RISK:
                addIfMissing(tasks, createTask("Tiny Win", "Complete one small chore you've been putting off.", "Purpose", 10, now));
                addIfMissing(tasks, createTask("Social Ping", "Send a quick hello text to someone you trust.", "Social", 2, now));
                break;
            case LOW_PURPOSE:
                addIfMissing(tasks, createTask("Goal Setting", "Write down one thing you want to achieve tomorrow.", "Purpose", 5, now));
                addIfMissing(tasks, createTask("Reflection", "Write about what made you feel most alive.", "Journaling", 10, now));
                break;
            case SOCIAL_ISOLATION:
                addIfMissing(tasks, createTask("Voice Call", "Call a friend or family member for 5 minutes.", "Social", 5, now));
                break;
            case STABLE:
            default:
                addIfMissing(tasks, createTask("Gratitude Journal", "Write down three things you are grateful for today.", "Journaling", 5, now));
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEGACY: BEHAVIOR-DRIVEN TASKS
    // ═══════════════════════════════════════════════════════════════════

    public String getRecommendedAction(
            BehaviorReport behaviorReport,
            AnomalyDetector.AnomalyProfile anomalyProfile,
            UserBaseline baseline
    ) {
        if (behaviorReport != null) {
            if (behaviorReport.rapidSwitchCount >= BehaviorThresholds.HIGH_RAPID_SWITCH_THRESHOLD
                    || behaviorReport.appSwitchCount >= BehaviorThresholds.HIGH_FRAGMENTATION_SWITCH_THRESHOLD) {
                return "focus_session";
            }
            if (behaviorReport.bingeSessionCount > 0
                    || behaviorReport.longestSessionMillis >= BehaviorThresholds.BINGE_SESSION_THRESHOLD_MILLIS) {
                return "take_break";
            }
            if (behaviorReport.lateNightUsageMillis >= BehaviorThresholds.LATE_NIGHT_SIGNAL_THRESHOLD_MILLIS) {
                return "wind_down";
            }
        }

        if (anomalyProfile != null) {
            if (anomalyProfile.stress != null
                    && anomalyProfile.stress.level != AnomalyDetector.Level.NORMAL
                    && anomalyProfile.stress.zScore > 0d) {
                return "breathing";
            }
            if (anomalyProfile.sleep != null
                    && anomalyProfile.sleep.level != AnomalyDetector.Level.NORMAL
                    && anomalyProfile.sleep.zScore < 0d) {
                return "wind_down";
            }
            if (anomalyProfile.screenTime != null
                    && anomalyProfile.screenTime.level != AnomalyDetector.Level.NORMAL
                    && anomalyProfile.screenTime.zScore > 0d) {
                return "take_break";
            }
            if (anomalyProfile.taskCompletion != null
                    && anomalyProfile.taskCompletion.level != AnomalyDetector.Level.NORMAL
                    && anomalyProfile.taskCompletion.zScore < 0d) {
                return "tiny_win";
            }
        }

        if (baseline != null && baseline.avgScreenTime7d > 0d && baseline.avgTaskCompletion7d < 35d) {
            return "tiny_win";
        }

        return "";
    }

    private void addBehaviorTasks(List<InterventionTask> tasks, BehaviorReport report, long now) {
        String action = "";
        if (report.rapidSwitchCount >= BehaviorThresholds.HIGH_RAPID_SWITCH_THRESHOLD) action = "focus_session";
        else if (report.bingeSessionCount > 0) action = "take_break";
        else if (report.lateNightUsageMillis >= BehaviorThresholds.LATE_NIGHT_SIGNAL_THRESHOLD_MILLIS) action = "wind_down";

        if (!action.isEmpty()) {
            addActionTasks(tasks, action, now);
        }
    }

    private void addActionTasks(List<InterventionTask> tasks, String action, long now) {
        switch (action) {
            case "focus_session":
                addIfMissing(tasks, createTask("Focus Reset", "Turn off non-essential notifications and do one 10-minute task.", "Focus", 10, now));
                break;
            case "take_break":
                addIfMissing(tasks, createTask("Intentional Break", "Step away from your phone for 20 minutes.", "Detox", 20, now));
                break;
            case "wind_down":
                addIfMissing(tasks, createTask("Wind-Down Routine", "Dim your phone, avoid social apps, choose one calm activity.", "Recovery", 20, now));
                break;
            case "breathing":
                addIfMissing(tasks, createTask("Breathing Reset", "Try a 4-4-6 breathing cycle for 5 minutes.", "Mindfulness", 5, now));
                break;
            case "tiny_win":
                addIfMissing(tasks, createTask("Tiny Win", "Complete one very small task in the next 10 minutes.", "Purpose", 10, now));
                break;
            case "gentle_journaling":
                addIfMissing(tasks, createTask("Quick Reflection", "Write a few lines about what's draining your attention.", "Journaling", 5, now));
                break;
        }
    }

    private String adaptAction(String action, StrategyProfile strategyProfile) {
        if (strategyProfile.ignoredCount >= 2 && strategyProfile.ignoredCount > strategyProfile.completedCount) {
            if ("focus_session".equals(action)) return "gentle_journaling";
            if ("take_break".equals(action)) return "breathing";
            if ("wind_down".equals(action)) return "take_break";
            if ("breathing".equals(action)) return "gentle_journaling";
            if ("tiny_win".equals(action)) return "focus_session";
        }
        return action;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private StrategyProfile summarizeHistory(List<InterventionTask> taskHistory, String action) {
        StrategyProfile profile = new StrategyProfile();
        if (taskHistory == null || taskHistory.isEmpty()) return profile;

        for (InterventionTask task : taskHistory) {
            if ("COMPLETED".equals(task.status) || task.isCompleted) {
                profile.completedCount++;
            }
            if ("SKIPPED".equals(task.status) || task.isSkipped) {
                profile.ignoredCount++;
            }
        }
        return profile;
    }

    private void addIfMissing(List<InterventionTask> tasks, InterventionTask candidate) {
        for (InterventionTask task : tasks) {
            if (task.title != null && task.title.equalsIgnoreCase(candidate.title)) return;
        }
        tasks.add(candidate);
    }

    private InterventionTask createTask(String title, String desc, String cat, int dur, long time) {
        InterventionTask task = new InterventionTask();
        task.title = title;
        task.description = desc;
        task.category = cat;
        task.durationMinutes = dur;
        task.dateCreated = time;
        task.status = "ACTIVE";
        task.sourceTag = "check_in";
        task.linkedRiskCategory = "general";
        task.difficulty = "EASY";
        task.priority = 3;
        task.xpReward = 10;
        task.expiresAt = time + DAY_MS;
        return task;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    static class StrategyProfile {
        int completedCount;
        int ignoredCount;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFICACY-WEIGHTED SELECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Insert a task from the historically most effective risk category.
     * Skips if the best category is already the primary or secondary
     * (those are handled by steps 4 and 6).
     *
     * <p>Only categories with positive efficacy (avg > 0.03) are considered;
     * this prevents reinforcing neutral or counterproductive categories.</p>
     */
    private void addEfficacyWeightedTask(
            @NonNull List<InterventionTask> tasks,
            @NonNull java.util.Map<String, Float> categoryEfficacy,
            @Nullable String primaryCat,
            @Nullable String secondaryCat,
            @NonNull String difficulty,
            int dosage) {
        if (tasks.size() >= dosage) return;

        // Find the category with the highest positive efficacy
        String bestCat = null;
        float bestScore = 0.03f; // Minimum threshold — only truly effective categories
        for (java.util.Map.Entry<String, Float> entry : categoryEfficacy.entrySet()) {
            if (entry.getValue() > bestScore) {
                String cat = entry.getKey();
                // Skip categories already covered by primary/secondary
                if (cat.equals(primaryCat) || cat.equals(secondaryCat)) continue;
                bestScore = entry.getValue();
                bestCat = cat;
            }
        }

        if (bestCat != null) {
            String why = String.format(Locale.US,
                    "This category has been %.0f%% more effective for you than average — reinforcing what works.",
                    bestScore * 100);
            addFromCategory(tasks, bestCat, difficulty, why);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OBSERVATION WINDOW — Closed-loop efficacy tracking
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Stamp the current overall risk score onto a task at generation time.
     * This captures the "before" snapshot so that post-completion efficacy
     * can be computed after the 2-hour observation window.
     *
     * <p>Should be called by the TaskViewModel/InsightEngine immediately
     * after generating tasks, when the latest RiskClassification is available.</p>
     *
     * @param tasks         list of newly generated tasks
     * @param currentRisk   the latest overall risk score (from RiskClassification)
     */
    public void stampPreInterventionRisk(@NonNull List<InterventionTask> tasks,
                                          float currentRisk) {
        for (InterventionTask task : tasks) {
            task.capturePreInterventionRisk(currentRisk);
        }
    }

    /**
     * Compute efficacy for a single task whose observation window has closed.
     * Compares the pre-intervention risk (captured at task creation) with
     * the current risk classification score to determine if the intervention
     * helped reduce the user's risk.
     *
     * <p><b>Efficacy interpretation:</b></p>
     * <ul>
     *   <li>+0.10 or higher → clearly effective (risk dropped significantly)</li>
     *   <li>+0.01 to +0.09 → mildly effective</li>
     *   <li>0.00 → neutral (no change)</li>
     *   <li>negative → risk increased (task may have been counterproductive or
     *       external factors worsened the situation)</li>
     * </ul>
     *
     * @param task           the completed task with an expired observation window
     * @param currentRisk    the latest overall risk score at window close
     * @return the computed efficacy score (positive = improvement)
     */
    public float computeTaskEfficacy(@NonNull InterventionTask task, float currentRisk) {
        if (task.preInterventionRisk <= 0f) {
            // Legacy task without pre-intervention snapshot — can't compute
            return 0f;
        }
        task.finalizeObservation(currentRisk);
        return task.efficacyScore;
    }

    /**
     * Batch-process all tasks whose observation windows have expired.
     * Called by the periodic UsageWorker or a dedicated EfficacyWorker.
     *
     * <p>For each eligible task, captures the post-intervention risk and
     * computes the efficacy delta. The caller is responsible for persisting
     * the updated tasks back to the database via TaskDao.update().</p>
     *
     * @param awaitingTasks  tasks from TaskDao.getTasksAwaitingEfficacy(now)
     * @param currentRisk    the latest overall risk score
     * @return list of tasks that were updated with efficacy scores
     */
    @NonNull
    public List<InterventionTask> finalizeObservationWindows(
            @NonNull List<InterventionTask> awaitingTasks,
            float currentRisk) {
        List<InterventionTask> updated = new ArrayList<>();
        for (InterventionTask task : awaitingTasks) {
            if (task.hasEfficacyScore()) continue; // Already processed
            if (task.preInterventionRisk <= 0f) continue; // No baseline

            computeTaskEfficacy(task, currentRisk);
            updated.add(task);
        }
        return updated;
    }

    /**
     * Get a category-level efficacy summary from completed task history.
     * Returns an array of human-readable summaries per risk category.
     *
     * @param taskHistory  tasks with completed efficacy measurements
     * @return array of efficacy summary strings
     */
    @NonNull
    public String[] getEfficacySummary(@Nullable List<InterventionTask> taskHistory) {
        if (taskHistory == null || taskHistory.isEmpty()) {
            return new String[]{"Not enough data to compute intervention effectiveness yet."};
        }

        // Group by linkedRiskCategory
        java.util.Map<String, float[]> categoryStats = new java.util.LinkedHashMap<>();
        for (InterventionTask task : taskHistory) {
            if (!task.hasEfficacyScore()) continue;
            String cat = task.linkedRiskCategory != null ? task.linkedRiskCategory : "general";
            float[] stats = categoryStats.get(cat);
            if (stats == null) {
                stats = new float[]{0f, 0f}; // [sumEfficacy, count]
                categoryStats.put(cat, stats);
            }
            stats[0] += task.efficacyScore;
            stats[1] += 1f;
        }

        if (categoryStats.isEmpty()) {
            return new String[]{"No measured interventions yet. Complete tasks and wait 2 hours for results."};
        }

        List<String> summaries = new ArrayList<>();
        for (java.util.Map.Entry<String, float[]> entry : categoryStats.entrySet()) {
            float avg = entry.getValue()[0] / entry.getValue()[1];
            int count = (int) entry.getValue()[1];
            String label = entry.getKey().replace("_", " ");
            String effectiveness;
            if (avg >= 0.10f) effectiveness = "highly effective";
            else if (avg >= 0.03f) effectiveness = "moderately effective";
            else if (avg >= -0.03f) effectiveness = "neutral";
            else effectiveness = "not effective";

            summaries.add(String.format(Locale.US,
                    "%s: %s (avg impact: %+.0f%%, %d tasks measured)",
                    label, effectiveness, avg * 100, count));
        }
        return summaries.toArray(new String[0]);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SENTIMENT-AWARE EFFICACY — Qualitative mood feedback loop
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Structured representation of a user's mood transition around an intervention.
     * Used to compute qualitative sentiment weights that augment the quantitative
     * risk-delta-based efficacy score.
     */
    public static class SentimentSignal {
        public final String preMood;
        public final String postMood;
        public final String linkedCategory;

        public SentimentSignal(@Nullable String preMood, @Nullable String postMood,
                               @Nullable String linkedCategory) {
            this.preMood = preMood;
            this.postMood = postMood;
            this.linkedCategory = linkedCategory != null ? linkedCategory : "general";
        }
    }

    /**
     * Map a mood string to a numeric valence score (0.0–1.0).
     * Higher = more positive. Used for directional mood-shift computation.
     */
    private float moodValence(@Nullable String mood) {
        if (mood == null || mood.trim().isEmpty()) return 0.5f; // Neutral default
        switch (mood.trim().toLowerCase(Locale.US)) {
            case "great":
            case "happy":
            case "joyful":
            case "calm":
            case "relaxed":
            case "peaceful":
                return 0.9f;
            case "good":
            case "content":
            case "hopeful":
            case "relieved":
                return 0.75f;
            case "okay":
            case "fine":
            case "neutral":
                return 0.5f;
            case "tired":
            case "bored":
            case "restless":
            case "uneasy":
                return 0.35f;
            case "anxious":
            case "stressed":
            case "frustrated":
            case "irritable":
            case "overwhelmed":
                return 0.2f;
            case "sad":
            case "lonely":
            case "hopeless":
            case "depressed":
            case "angry":
            case "terrible":
                return 0.1f;
            default:
                return 0.5f;
        }
    }

    /**
     * Compute a sentiment weight from a pre/post mood pair.
     * Returns a value in the range [-0.3, +0.3]:
     * <ul>
     *   <li>Positive = mood improved after the intervention</li>
     *   <li>Zero = no change or incomplete data</li>
     *   <li>Negative = mood worsened</li>
     * </ul>
     *
     * <p>This weight is designed to be added to the quantitative efficacy
     * score to create a composite "sentiment-adjusted efficacy".</p>
     */
    public float computeSentimentWeight(@Nullable String preMood, @Nullable String postMood) {
        if (preMood == null || postMood == null) return 0f;
        float preVal = moodValence(preMood);
        float postVal = moodValence(postMood);
        float delta = postVal - preVal;
        // Clamp to ±0.3 to prevent mood data from overwhelming risk-based efficacy
        return Math.max(-0.3f, Math.min(0.3f, delta));
    }

    /**
     * Compute sentiment-adjusted efficacy for a single task.
     * Blends the quantitative risk-delta efficacy (70% weight) with the
     * qualitative mood-shift sentiment (30% weight).
     *
     * <p>This ensures that a task which measurably reduced risk <i>and</i>
     * improved the user's subjective mood gets a higher composite score
     * than one which only affected risk numbers.</p>
     *
     * @param task the completed task with efficacy and mood data
     * @return composite efficacy score
     */
    public float computeSentimentAdjustedEfficacy(@NonNull InterventionTask task) {
        float riskEfficacy = task.efficacyScore;
        float sentimentWeight = computeSentimentWeight(
                task.preCompletionMood, task.postCompletionMood);

        // 70/30 blend: risk-based efficacy is the primary signal,
        // sentiment provides a qualitative boost/penalty
        return (riskEfficacy * 0.7f) + (sentimentWeight * 0.3f);
    }

    /**
     * Build a sentiment-enhanced efficacy map from task history.
     * For each risk category, computes the average sentiment-adjusted efficacy
     * across all measured tasks.
     *
     * <p>This map can be passed directly to
     * {@link #generateFromClassification} via the {@code categoryEfficacy}
     * parameter, replacing the raw risk-only efficacy map with one that
     * accounts for user-reported mood changes.</p>
     *
     * @param measuredTasks tasks with completed efficacy measurements
     * @return category → average sentiment-adjusted efficacy
     */
    @NonNull
    public java.util.Map<String, Float> buildSentimentEnhancedEfficacyMap(
            @Nullable List<InterventionTask> measuredTasks) {
        java.util.Map<String, float[]> accumulators = new java.util.LinkedHashMap<>();

        if (measuredTasks != null) {
            for (InterventionTask task : measuredTasks) {
                if (!task.hasEfficacyScore()) continue;
                String cat = task.linkedRiskCategory != null
                        ? task.linkedRiskCategory : "general";

                float composite = computeSentimentAdjustedEfficacy(task);
                float[] stats = accumulators.get(cat);
                if (stats == null) {
                    stats = new float[]{0f, 0f};
                    accumulators.put(cat, stats);
                }
                stats[0] += composite;
                stats[1] += 1f;
            }
        }

        java.util.Map<String, Float> result = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, float[]> entry : accumulators.entrySet()) {
            if (entry.getValue()[1] > 0f) {
                result.put(entry.getKey(), entry.getValue()[0] / entry.getValue()[1]);
            }
        }
        return result;
    }

    /**
     * Build a per-category sentiment trend summary for UI display.
     * Returns human-readable strings describing both the quantitative efficacy
     * and the qualitative mood direction for each risk category.
     *
     * @param measuredTasks tasks with completed efficacy and mood data
     * @return array of display-ready summary strings
     */
    @NonNull
    public String[] getSentimentEnhancedSummary(@Nullable List<InterventionTask> measuredTasks) {
        if (measuredTasks == null || measuredTasks.isEmpty()) {
            return new String[]{"Complete tasks and rate your mood to see sentiment-adjusted insights."};
        }

        java.util.Map<String, float[]> catStats = new java.util.LinkedHashMap<>();
        // stats: [sumRiskEfficacy, sumSentiment, count, moodCaptureCount]
        for (InterventionTask task : measuredTasks) {
            if (!task.hasEfficacyScore()) continue;
            String cat = task.linkedRiskCategory != null ? task.linkedRiskCategory : "general";
            float[] stats = catStats.get(cat);
            if (stats == null) {
                stats = new float[]{0f, 0f, 0f, 0f};
                catStats.put(cat, stats);
            }
            stats[0] += task.efficacyScore;
            stats[2] += 1f;
            if (task.hasMoodCapture()) {
                stats[1] += computeSentimentWeight(task.preCompletionMood, task.postCompletionMood);
                stats[3] += 1f;
            }
        }

        if (catStats.isEmpty()) {
            return new String[]{"No measured interventions yet. Complete tasks and wait 2 hours for results."};
        }

        List<String> summaries = new ArrayList<>();
        for (java.util.Map.Entry<String, float[]> entry : catStats.entrySet()) {
            float[] s = entry.getValue();
            float avgEfficacy = s[0] / s[2];
            String label = entry.getKey().replace("_", " ");
            String effectiveness;
            if (avgEfficacy >= 0.10f) effectiveness = "highly effective";
            else if (avgEfficacy >= 0.03f) effectiveness = "moderately effective";
            else if (avgEfficacy >= -0.03f) effectiveness = "neutral";
            else effectiveness = "not effective";

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US,
                    "%s: %s (risk impact: %+.0f%%", label, effectiveness, avgEfficacy * 100));

            if (s[3] > 0f) {
                float avgSentiment = s[1] / s[3];
                String moodDir;
                if (avgSentiment > 0.05f) moodDir = "mood improves";
                else if (avgSentiment < -0.05f) moodDir = "mood worsens";
                else moodDir = "mood stable";
                sb.append(String.format(Locale.US, ", %s", moodDir));
            }

            sb.append(String.format(Locale.US, ", %d tasks)", (int) s[2]));
            summaries.add(sb.toString());
        }
        return summaries.toArray(new String[0]);
    }
}
