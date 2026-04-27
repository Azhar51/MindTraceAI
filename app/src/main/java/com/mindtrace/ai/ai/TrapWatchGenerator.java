package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Behavioral Trap Watch Generator — proactively identifies and warns about
 * behavioral patterns ("traps") that the user is at risk of falling into.
 *
 * <h3>Philosophy:</h3>
 * <pre>
 *   NOT: "You're addicted! Stop using your phone!"
 *   YES: "Late-night scrolling pattern detected — you averaged 47min this week.
 *         Consider setting a phone-down alarm tonight."
 * </pre>
 *
 * <h3>Trap Detection Categories:</h3>
 * <ol>
 *   <li><b>Pattern-based</b> — detected from BehaviorReport (binge, late-night, rapid-switching)</li>
 *   <li><b>Profile-based</b> — derived from OnboardingProfile (trigger apps, vulnerability times)</li>
 *   <li><b>Time-based</b> — contextual warnings based on current hour of day</li>
 *   <li><b>Risk-based</b> — warnings amplified by current RiskClassification severity</li>
 *   <li><b>Trend-based</b> — warnings when risk trajectory is worsening</li>
 * </ol>
 *
 * <h3>Output:</h3>
 * <p>A priority-sorted list of {@link TrapWarning} objects (max 4), each with:</p>
 * <ul>
 *   <li>Severity level (HIGH, MODERATE, LOW)</li>
 *   <li>Human-readable trap name</li>
 *   <li>Contextual detail explaining the pattern</li>
 *   <li>Suggested micro-action to counter the trap</li>
 * </ul>
 *
 * @see MissionGenerator
 * @see InterventionEngine
 */
public class TrapWatchGenerator {

    private static final int MAX_WARNINGS = 4;
    private static final long LATE_NIGHT_THRESHOLD_MILLIS = 30L * 60 * 1000; // 30 min
    private static final int BINGE_SESSION_THRESHOLD = 2;
    private static final int RAPID_SWITCH_THRESHOLD = 50;

    // ═══════════════════════════════════════════════════════════════════
    // PRIMARY API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate behavioral trap warnings based on current signals.
     *
     * @param behavior       latest behavior report (can be null if unavailable)
     * @param profile        user's onboarding profile (can be null for new users)
     * @param classification current risk classification (can be null)
     * @return priority-sorted list of trap warnings (max 4)
     */
    @NonNull
    public List<TrapWarning> generateWarnings(
            @Nullable BehaviorReport behavior,
            @Nullable OnboardingProfile profile,
            @Nullable RiskClassification classification) {

        int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return generateWarnings(behavior, profile, classification, hourOfDay);
    }

    /**
     * Generate warnings with explicit hour (for testing/scheduling).
     */
    @NonNull
    public List<TrapWarning> generateWarnings(
            @Nullable BehaviorReport behavior,
            @Nullable OnboardingProfile profile,
            @Nullable RiskClassification classification,
            int hourOfDay) {

        List<TrapWarning> warnings = new ArrayList<>();

        // ── 1. Pattern-based traps (from BehaviorReport) ──
        if (behavior != null && behavior.dataAvailable) {
            addPatternTraps(warnings, behavior);
        }

        // ── 2. Profile-based traps (from OnboardingProfile) ──
        if (profile != null) {
            addProfileTraps(warnings, profile);
        }

        // ── 3. Time-based traps (contextual to hour of day) ──
        addTimeTraps(warnings, hourOfDay);

        // ── 4. Risk-based amplification ──
        if (classification != null) {
            addRiskTraps(warnings, classification);
        }

        // ── 5. Trend-based warnings ──
        if (classification != null && classification.isWorsening()) {
            addTrendWarning(warnings, classification);
        }

        // ── 6. Sort by severity (HIGH first) and limit ──
        Collections.sort(warnings, (a, b) -> severityRank(b.level) - severityRank(a.level));
        if (warnings.size() > MAX_WARNINGS) {
            return new ArrayList<>(warnings.subList(0, MAX_WARNINGS));
        }

        return warnings;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PATTERN-BASED TRAPS
    // ═══════════════════════════════════════════════════════════════════

    private void addPatternTraps(@NonNull List<TrapWarning> warnings,
                                  @NonNull BehaviorReport behavior) {

        // Late-night scrolling trap
        if (behavior.lateNightUsageMillis > LATE_NIGHT_THRESHOLD_MILLIS) {
            long minutes = behavior.lateNightUsageMillis / (60 * 1000);
            warnings.add(new TrapWarning(
                    "HIGH",
                    "Late-night scrolling after 10pm",
                    String.format(Locale.US, "You spent %d minutes on your phone late at night", minutes),
                    "Set a phone-down alarm 30 minutes before bed"
            ));
        }

        // Binge session trap
        if (behavior.bingeSessionCount > BINGE_SESSION_THRESHOLD) {
            String app = behavior.dominantAppPackage != null
                    ? extractAppName(behavior.dominantAppPackage)
                    : "your most-used app";
            warnings.add(new TrapWarning(
                    "MODERATE",
                    "Long binge sessions on " + app,
                    String.format(Locale.US, "%d sessions over 30 minutes detected", behavior.bingeSessionCount),
                    "Try setting a 15-minute timer before opening the app"
            ));
        }

        // Rapid app-switching (attention fragmentation)
        if (behavior.rapidSwitchCount > RAPID_SWITCH_THRESHOLD) {
            warnings.add(new TrapWarning(
                    "MODERATE",
                    "Rapid app-switching pattern",
                    String.format(Locale.US, "%d rapid switches detected — your attention is fragmented", behavior.rapidSwitchCount),
                    "Try a 10-minute single-task sprint: one app, one purpose"
            ));
        }

        // Loop pattern (switching between same apps repeatedly)
        if (behavior.hasLoopPattern && behavior.frequentAppLoops != null
                && !behavior.frequentAppLoops.isEmpty()) {
            String loopApps = behavior.frequentAppLoops.size() > 2
                    ? behavior.frequentAppLoops.get(0) + " → " + behavior.frequentAppLoops.get(1)
                    : String.join(" → ", behavior.frequentAppLoops);
            warnings.add(new TrapWarning(
                    "MODERATE",
                    "App loop detected: " + loopApps,
                    "Cycling between the same apps signals restless scanning behavior",
                    "Put your phone face-down for 2 minutes. Break the loop."
            ));
        }

        // Dominant app monopoly (one app > 60% of screen time)
        if (behavior.dominantUsageRatio > 0.60 && behavior.dominantAppPackage != null) {
            String app = extractAppName(behavior.dominantAppPackage);
            warnings.add(new TrapWarning(
                    "LOW",
                    app + " dominates your screen time",
                    String.format(Locale.US, "%.0f%% of your usage is one app", behavior.dominantUsageRatio * 100),
                    "Consider using grayscale mode or setting a daily time limit"
            ));
        }

        // Excessive total screen time (> 5 hours)
        long totalHours = behavior.totalForegroundMillis / (60 * 60 * 1000);
        if (totalHours >= 5) {
            warnings.add(new TrapWarning(
                    "MODERATE",
                    "Extended screen time",
                    String.format(Locale.US, "%d hours of active screen time today", totalHours),
                    "Take a 20-minute phone-free break right now"
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROFILE-BASED TRAPS
    // ═══════════════════════════════════════════════════════════════════

    private void addProfileTraps(@NonNull List<TrapWarning> warnings,
                                  @NonNull OnboardingProfile profile) {

        // Trigger apps (identified during onboarding)
        if (profile.triggerApps != null && !profile.triggerApps.isEmpty()) {
            String[] apps = profile.triggerApps.split(",");
            for (int i = 0; i < Math.min(2, apps.length); i++) {
                String app = apps[i].trim();
                if (!app.isEmpty()) {
                    warnings.add(new TrapWarning(
                            "MODERATE",
                            "Opening " + app + " out of habit",
                            "You identified this as a problem app during setup",
                            "Ask yourself: 'What am I looking for?' before opening"
                    ));
                }
            }
        }

        // Peak vulnerability time (if we're in or approaching it)
        if (profile.peakVulnerabilityTime != null) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            boolean inVulnerableWindow = isInVulnerableWindow(hour, profile.peakVulnerabilityTime);
            if (inVulnerableWindow) {
                warnings.add(new TrapWarning(
                        "HIGH",
                        "You're in your vulnerable window",
                        "You told us " + profile.peakVulnerabilityTime.replace("_", " ") +
                                " is when you're most at risk",
                        "Use this awareness as power — choose one intentional action right now"
                ));
            }
        }

        // Low addiction awareness + high digital use
        if (profile.addictionScale <= 3 && profile.getDigitalDependencyIndex() > 0.6f) {
            warnings.add(new TrapWarning(
                    "LOW",
                    "Blind spot: low awareness, high usage",
                    "Your self-assessed awareness is low while digital dependency signals are elevated",
                    "Try tracking your phone pickups today — awareness is the first step"
            ));
        }

        // Avoidant coping + stress
        if ("avoidant".equals(profile.copingStyle) && profile.stressLevel >= 4) {
            warnings.add(new TrapWarning(
                    "MODERATE",
                    "Stress + avoidant coping = phone escape risk",
                    "Your coping style tends toward avoidance, and stress is elevated",
                    "Instead of reaching for your phone, try 3 deep breaths first"
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIME-BASED TRAPS
    // ═══════════════════════════════════════════════════════════════════

    private void addTimeTraps(@NonNull List<TrapWarning> warnings, int hourOfDay) {

        // Early morning phone-in-bed trap
        if (hourOfDay >= 5 && hourOfDay < 9) {
            warnings.add(new TrapWarning(
                    "LOW",
                    "Morning phone habit",
                    "Staying in bed with the phone after the alarm sets a reactive tone for the day",
                    "Try getting up before checking your phone — even for 5 minutes"
            ));
        }

        // Post-lunch slump (attention dip)
        if (hourOfDay >= 13 && hourOfDay < 15) {
            warnings.add(new TrapWarning(
                    "LOW",
                    "Afternoon attention dip",
                    "Energy naturally drops after lunch — phone use spikes during this window",
                    "A 5-minute walk or stretch works better than scrolling"
            ));
        }

        // Evening "quick check" trap
        if (hourOfDay >= 21) {
            warnings.add(new TrapWarning(
                    "MODERATE",
                    "The 'quick check' that becomes an hour",
                    "Evening phone checks are the #1 cause of late-night screen time",
                    "If you must use your phone, set a 10-minute timer first"
            ));
        }

        // Late night (past midnight)
        if (hourOfDay >= 0 && hourOfDay < 5) {
            warnings.add(new TrapWarning(
                    "HIGH",
                    "You're up past midnight on your phone",
                    "Late-night screen use disrupts melatonin production and sleep quality",
                    "Turn on night mode, put the phone down, and try a relaxation exercise"
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RISK-BASED AMPLIFICATION
    // ═══════════════════════════════════════════════════════════════════

    private void addRiskTraps(@NonNull List<TrapWarning> warnings,
                               @NonNull RiskClassification classification) {

        // Co-morbidity warning (multiple elevated categories)
        int elevated = classification.getElevatedCategoryCount();
        if (elevated >= 3) {
            warnings.add(new TrapWarning(
                    "HIGH",
                    "Multiple risk areas are elevated",
                    String.format(Locale.US, "%d of 6 risk categories are at MODERATE or above", elevated),
                    "Focus on one area at a time — today's mission targets the most urgent"
            ));
        }

        // Specific high-risk combinations
        if (classification.sleepDisruptionScore > 0.5f
                && classification.stressAnxietyScore > 0.5f) {
            warnings.add(new TrapWarning(
                    "HIGH",
                    "Sleep-stress spiral detected",
                    "Poor sleep feeds stress, which worsens sleep — a destructive cycle",
                    "Tonight: no screens 1 hour before bed + 5 min breathing exercise"
            ));
        }

        if (classification.depressionRiskScore > 0.5f
                && classification.socialIsolationScore > 0.5f) {
            warnings.add(new TrapWarning(
                    "HIGH",
                    "Depression + isolation reinforcing each other",
                    "Withdrawal deepens depression, which increases withdrawal desire",
                    "One small social connection today — even a text message — helps break the loop"
            ));
        }

        if (classification.digitalAddictionScore > 0.6f
                && classification.lowFulfilmentScore > 0.5f) {
            warnings.add(new TrapWarning(
                    "MODERATE",
                    "Digital escape masking low fulfilment",
                    "Phone overuse often increases when purpose and meaning feel absent",
                    "Before opening your phone, ask: 'What would actually help right now?'"
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TREND-BASED WARNINGS
    // ═══════════════════════════════════════════════════════════════════

    private void addTrendWarning(@NonNull List<TrapWarning> warnings,
                                  @NonNull RiskClassification classification) {
        float delta = classification.riskDelta;
        String direction = delta > 0.10f ? "significantly worsening" : "gradually rising";

        warnings.add(new TrapWarning(
                delta > 0.10f ? "HIGH" : "MODERATE",
                "Risk trajectory is " + direction,
                String.format(Locale.US, "Overall risk changed by %+.0f%% compared to yesterday",
                        delta * 100),
                "Today's tasks are especially important — small actions compound"
        ));
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if the current hour falls within the user's self-reported
     * peak vulnerability time window.
     */
    private boolean isInVulnerableWindow(int hour, @NonNull String vulnerabilityTime) {
        switch (vulnerabilityTime) {
            case "morning":     return hour >= 6 && hour < 10;
            case "afternoon":   return hour >= 12 && hour < 17;
            case "evening":     return hour >= 17 && hour < 22;
            case "late_night":  return hour >= 22 || hour < 5;
            case "unpredictable": return false; // Can't predict
            default: return false;
        }
    }

    /**
     * Extract a human-readable app name from a package name.
     * e.g., "com.instagram.android" → "Instagram"
     */
    @NonNull
    private String extractAppName(@NonNull String packageName) {
        // Extract the meaningful part from common patterns
        String[] parts = packageName.split("\\.");
        if (parts.length >= 2) {
            String name = parts.length >= 3 ? parts[1] : parts[0];
            // Capitalize first letter
            return name.substring(0, 1).toUpperCase(Locale.US)
                    + name.substring(1).toLowerCase(Locale.US);
        }
        return packageName;
    }

    /**
     * Convert severity level string to numeric rank for sorting.
     */
    private int severityRank(@NonNull String level) {
        switch (level) {
            case "HIGH":     return 3;
            case "MODERATE": return 2;
            case "LOW":      return 1;
            default:         return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * A single behavioral trap warning.
     *
     * <p>Each warning identifies a specific pattern the user should be
     * aware of, explains the data behind it, and offers a concrete
     * micro-action to counter it.</p>
     */
    public static class TrapWarning {

        /** Severity level: "HIGH", "MODERATE", "LOW". */
        @NonNull
        public final String level;

        /** Human-readable trap name (e.g., "Late-night scrolling after 10pm"). */
        @NonNull
        public final String name;

        /** Contextual detail explaining the pattern/data. */
        @NonNull
        public final String detail;

        /** Suggested micro-action to counter the trap. */
        @NonNull
        public final String suggestion;

        /** Icon for UI display. */
        @NonNull
        public final String icon;

        public TrapWarning(@NonNull String level, @NonNull String name,
                           @NonNull String detail, @NonNull String suggestion) {
            this.level = level;
            this.name = name;
            this.detail = detail;
            this.suggestion = suggestion;
            this.icon = getIconForLevel(level);
        }

        /**
         * Legacy 3-arg constructor (backward compatible with blueprint spec).
         * Uses detail as suggestion.
         */
        public TrapWarning(@NonNull String level, @NonNull String name,
                           @NonNull String detail) {
            this(level, name, detail, "Be mindful of this pattern.");
        }

        @NonNull
        private static String getIconForLevel(@NonNull String level) {
            switch (level) {
                case "HIGH":     return "🔴";
                case "MODERATE": return "🟡";
                case "LOW":      return "🟢";
                default:         return "⚪";
            }
        }

        @NonNull
        @Override
        public String toString() {
            return icon + " [" + level + "] " + name + ": " + detail;
        }
    }
}
