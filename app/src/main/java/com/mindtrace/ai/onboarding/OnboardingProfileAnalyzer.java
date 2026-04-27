package com.mindtrace.ai.onboarding;

import com.mindtrace.ai.database.entity.OnboardingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OnboardingProfileAnalyzer {

    public OnboardingAssessment assess(OnboardingProfile profile) {
        if (profile == null) {
            return new OnboardingAssessment(
                    20,
                    "Baseline Building",
                    "Complete your first setup so MindTrace can start shaping guidance around your routine.",
                    "Start with a simple reset that feels realistic today.",
                    buildDefaultMissionSteps("Build steady structure"),
                    buildDefaultWarningItems(),
                    "Complete your first check-in.",
                    "This gives MindTrace the first clear signal it needs to personalize guidance.",
                    false,
                    false,
                    false,
                    false,
                    false,
                    "steady_build"
            );
        }

        float emotionalLoad = average(
                profile.stressLevel,
                profile.anxietyLevel,
                profile.overthinkingLevel,
                profile.selfDoubtLevel
        );
        float digitalLoad = average(
                profile.socialMediaUse,
                profile.lateNightPhoneUse,
                profile.appAddictionRisk,
                profile.overusePatternLevel,
                profile.bingeScrollingLevel,
                profile.appSwitchingHabit
        );
        float routineLoad = average(
                invert(profile.routineConsistency),
                invert(profile.productiveHabits),
                profile.procrastinationLevel,
                invert(profile.physicalActivity),
                profile.distractionLevel
        );
        float sleepLoad = average(
                sleepHoursRisk(profile.sleepHours),
                invert(profile.sleepQuality),
                profile.lateNightPhoneUse
        );
        float purposeLoad = average(
                invert(profile.motivationLevel),
                profile.feelingStuck,
                profile.procrastinationLevel
        );
        float supportLoad = average(
                profile.lonelinessLevel,
                invert(profile.socialSupportLevel),
                boolAsScale(profile.supportNeeded)
        );

        boolean phoneRiskHigh = digitalLoad >= 3.6f
                || hasHelpArea(profile, "phone addiction")
                || hasHelpArea(profile, "screen time");
        boolean routineRiskHigh = routineLoad >= 3.4f || hasHelpArea(profile, "poor routine");
        boolean sleepRiskHigh = sleepLoad >= 3.4f || hasHelpArea(profile, "sleep");
        boolean purposeRiskHigh = purposeLoad >= 3.4f || hasHelpArea(profile, "stuck");
        boolean supportRecommended = profile.safetySupportEnabled
                && (supportLoad >= 3.5f
                || emotionalLoad >= 4.1f
                || riskFromLoads(digitalLoad, emotionalLoad, routineLoad, sleepLoad, purposeLoad, supportLoad) >= 72);

        int riskIndex = riskFromLoads(digitalLoad, emotionalLoad, routineLoad, sleepLoad, purposeLoad, supportLoad);
        String primaryFocus = determinePrimaryFocus(digitalLoad, emotionalLoad, routineLoad, sleepLoad, purposeLoad);
        String wellnessLabel = buildWellnessLabel(riskIndex, supportRecommended);
        String summary = buildSummary(profile, phoneRiskHigh, routineRiskHigh, sleepRiskHigh, purposeRiskHigh, supportRecommended, primaryFocus);
        String missionTitle = buildMissionTitle(profile, primaryFocus);
        List<String> missionSteps = buildMissionSteps(profile, primaryFocus);
        List<String> warningItems = buildWarningItems(profile, phoneRiskHigh, routineRiskHigh, sleepRiskHigh, purposeRiskHigh);
        String nextActionTitle = buildNextActionTitle(profile, primaryFocus);
        String nextActionReason = buildNextActionReason(primaryFocus);

        return new OnboardingAssessment(
                riskIndex,
                wellnessLabel,
                summary,
                missionTitle,
                missionSteps,
                warningItems,
                nextActionTitle,
                nextActionReason,
                supportRecommended,
                phoneRiskHigh,
                routineRiskHigh,
                sleepRiskHigh,
                purposeRiskHigh,
                primaryFocus
        );
    }

    private int riskFromLoads(
            float digitalLoad,
            float emotionalLoad,
            float routineLoad,
            float sleepLoad,
            float purposeLoad,
            float supportLoad
    ) {
        double score = (digitalLoad * 0.22d)
                + (emotionalLoad * 0.22d)
                + (routineLoad * 0.18d)
                + (sleepLoad * 0.16d)
                + (purposeLoad * 0.14d)
                + (supportLoad * 0.08d);
        return clamp(Math.round((float) ((score / 5d) * 100d)), 18, 92);
    }

    private String determinePrimaryFocus(
            float digitalLoad,
            float emotionalLoad,
            float routineLoad,
            float sleepLoad,
            float purposeLoad
    ) {
        float highest = digitalLoad;
        String focus = "phone_control";

        if (sleepLoad > highest) {
            highest = sleepLoad;
            focus = "sleep_reset";
        }
        if (routineLoad > highest) {
            highest = routineLoad;
            focus = "routine_reset";
        }
        if (purposeLoad > highest) {
            highest = purposeLoad;
            focus = "direction_reset";
        }
        if (emotionalLoad > highest) {
            focus = "calm_reset";
        }
        return focus;
    }

    private String buildWellnessLabel(int riskIndex, boolean supportRecommended) {
        if (supportRecommended || riskIndex >= 78) {
            return "Supportive Start";
        }
        if (riskIndex >= 58) {
            return "Reset Mode";
        }
        if (riskIndex >= 38) {
            return "Build Mode";
        }
        return "Steady Start";
    }

    private String buildSummary(
            OnboardingProfile profile,
            boolean phoneRiskHigh,
            boolean routineRiskHigh,
            boolean sleepRiskHigh,
            boolean purposeRiskHigh,
            boolean supportRecommended,
            String primaryFocus
    ) {
        String name = safeName(profile.name);
        if (supportRecommended) {
            return "MindTrace will start in a calmer support mode for " + name + ". We'll focus on steadier structure, softer wording, and easier access to support tools.";
        }
        if (phoneRiskHigh) {
            return name + " is showing a strong pull toward phone drift, especially around switching, scrolling, or late-night use. Early boundaries will matter most.";
        }
        if (sleepRiskHigh) {
            return "Sleep looks like a weak point right now, so MindTrace will protect mornings and reduce late-night drift before it grows.";
        }
        if (routineRiskHigh) {
            return name + " needs more structure than intensity right now. A small repeatable routine will help more than big goals.";
        }
        if (purposeRiskHigh) {
            return "Direction feels shaky, so MindTrace will keep today's guidance focused on one clear task instead of vague motivation.";
        }
        if ("calm_reset".equals(primaryFocus)) {
            return "Stress looks higher than it should be, so the app will lean toward calmer, lower-pressure guidance first.";
        }
        return "MindTrace has enough intake data to begin shaping the day around routine, attention, and one realistic next step.";
    }

    private String buildMissionTitle(OnboardingProfile profile, String primaryFocus) {
        String goal = safe(profile.primaryGoal);
        if ("sleep_reset".equals(primaryFocus)) {
            return "Protect your routine early so the day does not inherit last night's drift.";
        }
        if ("phone_control".equals(primaryFocus)) {
            return "Build a day that your phone does not control.";
        }
        if ("direction_reset".equals(primaryFocus)) {
            return "Reduce drift by giving today one clear direction.";
        }
        if ("routine_reset".equals(primaryFocus)) {
            return "Create simple structure before the day gets noisy.";
        }
        if ("calm_reset".equals(primaryFocus)) {
            return "Lower internal pressure first, then build momentum.";
        }
        if (!goal.isEmpty()) {
            return "Use today to move one step toward " + goal.toLowerCase(Locale.getDefault()) + ".";
        }
        return "Start small, stay steady, and protect today's best hour.";
    }

    private List<String> buildMissionSteps(OnboardingProfile profile, String primaryFocus) {
        List<String> steps = new ArrayList<>();
        if ("phone_control".equals(primaryFocus)) {
            steps.add("Keep distracting apps closed during the first work block");
            steps.add("Start one 25-minute focus block before casual phone use");
            steps.add("Do one evening check-in before sleep");
        } else if ("sleep_reset".equals(primaryFocus)) {
            steps.add("Start the day away from the bed and away from scrolling");
            steps.add("Protect one clean work or study block before noon");
            steps.add("Set a phone-off point before sleep");
        } else if ("direction_reset".equals(primaryFocus)) {
            steps.add("Write down today's 3 priorities");
            steps.add("Finish one meaningful block before checking social media");
            steps.add("End the day with a clear shutdown");
        } else if ("calm_reset".equals(primaryFocus)) {
            steps.add("Start with a brief breathing or grounding reset");
            steps.add("Choose one task only for the first work block");
            steps.add("Reduce unnecessary notifications for the day");
        } else {
            steps.addAll(buildDefaultMissionSteps(profile.primaryGoal));
        }
        return steps;
    }

    private List<String> buildDefaultMissionSteps(String primaryGoal) {
        List<String> steps = new ArrayList<>();
        String safeGoal = safe(primaryGoal);
        if (!safeGoal.isEmpty()) {
            steps.add("Take one concrete step toward " + safeGoal.toLowerCase(Locale.getDefault()));
        } else {
            steps.add("Start with one clear priority");
        }
        steps.add("Keep the phone away during the first work session");
        steps.add("Complete one evening check-in");
        return steps;
    }

    private List<String> buildWarningItems(
            OnboardingProfile profile,
            boolean phoneRiskHigh,
            boolean routineRiskHigh,
            boolean sleepRiskHigh,
            boolean purposeRiskHigh
    ) {
        List<String> items = new ArrayList<>();
        if (sleepRiskHigh) {
            items.add("Late-night scrolling");
        }
        if (profile.appSwitchingHabit >= 4 || phoneRiskHigh) {
            items.add("Random app switching");
        }
        if (profile.bingeScrollingLevel >= 4 || profile.socialMediaUse >= 4) {
            items.add("One long social scroll");
        }
        if (routineRiskHigh) {
            items.add("Staying in bed with the phone");
        }
        if (purposeRiskHigh) {
            items.add("Starting the day without one clear task");
        }
        if (profile.procrastinationLevel >= 4) {
            items.add("Letting small delays become a lost hour");
        }
        while (items.size() < 3) {
            for (String fallback : buildDefaultWarningItems()) {
                if (!items.contains(fallback)) {
                    items.add(fallback);
                }
                if (items.size() == 3) {
                    break;
                }
            }
        }
        return items.size() > 5 ? new ArrayList<>(items.subList(0, 5)) : items;
    }

    private List<String> buildDefaultWarningItems() {
        List<String> items = new ArrayList<>();
        items.add("Late-night scrolling");
        items.add("Opening social media first thing");
        items.add("Random app switching");
        items.add("Skipping sleep");
        return items;
    }

    private String buildNextActionTitle(OnboardingProfile profile, String primaryFocus) {
        if ("phone_control".equals(primaryFocus)) {
            return "Turn on focus mode before your first important task.";
        }
        if ("sleep_reset".equals(primaryFocus)) {
            return "Create a phone-free buffer before sleep tonight.";
        }
        if ("direction_reset".equals(primaryFocus)) {
            return "Write down the 3 things that matter most today.";
        }
        if ("calm_reset".equals(primaryFocus)) {
            return "Take a 2-minute reset before opening distracting apps.";
        }
        if ("routine_reset".equals(primaryFocus)) {
            return "Start your first 25-minute block before casual phone use.";
        }
        if (!safe(profile.primaryGoal).isEmpty()) {
            return "Take one step toward " + profile.primaryGoal.toLowerCase(Locale.getDefault()) + " right now.";
        }
        return "Start your first focused block before the phone pulls you away.";
    }

    private String buildNextActionReason(String primaryFocus) {
        if ("phone_control".equals(primaryFocus)) {
            return "Early phone boundaries reduce drift before it grows into a scattered day.";
        }
        if ("sleep_reset".equals(primaryFocus)) {
            return "Protecting sleep improves energy, attention, and the chance of staying in control tomorrow.";
        }
        if ("direction_reset".equals(primaryFocus)) {
            return "Clarity lowers procrastination and gives the day a direction to follow.";
        }
        if ("calm_reset".equals(primaryFocus)) {
            return "A small reset lowers pressure and makes the next action feel more doable.";
        }
        return "One simple move is enough to give the day structure and momentum.";
    }

    private boolean hasHelpArea(OnboardingProfile profile, String keyword) {
        String helpAreas = safe(profile.helpAreasCsv);
        return helpAreas.contains(keyword.toLowerCase(Locale.getDefault()));
    }

    private String safeName(String name) {
        String trimmed = safe(name);
        if (trimmed.isEmpty()) {
            return "you";
        }
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            trimmed = trimmed.substring(0, spaceIndex);
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.getDefault()) + trimmed.substring(1);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private float average(float... values) {
        if (values == null || values.length == 0) {
            return 0f;
        }
        float total = 0f;
        for (float value : values) {
            total += value;
        }
        return total / values.length;
    }

    private float invert(int value) {
        int normalized = clamp(value, 1, 5);
        return 6 - normalized;
    }

    private float boolAsScale(boolean value) {
        return value ? 5f : 2f;
    }

    private float sleepHoursRisk(float sleepHours) {
        if (sleepHours >= 8f) {
            return 1f;
        }
        if (sleepHours >= 7f) {
            return 2f;
        }
        if (sleepHours >= 6f) {
            return 3f;
        }
        if (sleepHours >= 5f) {
            return 4f;
        }
        return 5f;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
