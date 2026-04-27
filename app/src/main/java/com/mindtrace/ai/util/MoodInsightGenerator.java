package com.mindtrace.ai.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates rich, contextual text insights from mood history data.
 *
 * <p>This utility transforms raw MoodMapper analytics into human-readable
 * insight cards for the dashboard, weekly reports, and notification copy.
 * Each insight is a {@link MoodInsight} with a type, title, body, emoji,
 * and priority level.</p>
 *
 * <h3>Insight Types:</h3>
 * <ul>
 *   <li><b>STREAK_ALERT</b> — Consecutive negative days warning</li>
 *   <li><b>TRAJECTORY</b> — Mood trajectory (improving/declining/volatile)</li>
 *   <li><b>STABILITY</b> — Emotional stability analysis</li>
 *   <li><b>WEEKLY_SUMMARY</b> — End-of-week composite insight</li>
 *   <li><b>POSITIVE_MOMENTUM</b> — Reinforcement for positive streaks</li>
 *   <li><b>COPING_SUGGESTION</b> — Contextual intervention nudge</li>
 * </ul>
 */
public final class MoodInsightGenerator {

    private MoodInsightGenerator() { /* utility class */ }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates all applicable insights from a mood history list.
     * Insights are sorted by priority (highest first).
     *
     * @param moodHistory ordered list of daily moods (oldest first)
     * @return list of insights, never null
     */
    @NonNull
    public static List<MoodInsight> generateInsights(@Nullable List<String> moodHistory) {
        List<MoodInsight> insights = new ArrayList<>();
        if (moodHistory == null || moodHistory.isEmpty()) {
            insights.add(new MoodInsight(
                    InsightType.COPING_SUGGESTION, 0,
                    "📝", "Start tracking",
                    "Complete your first check-in to unlock personalized mood insights."
            ));
            return insights;
        }

        // Generate all insight types
        addStreakAlerts(insights, moodHistory);
        addTrajectoryInsight(insights, moodHistory);
        addStabilityInsight(insights, moodHistory);
        addPositiveMomentum(insights, moodHistory);
        addCopingSuggestion(insights, moodHistory);

        if (moodHistory.size() >= 5) {
            addWeeklySummary(insights, moodHistory);
        }

        // Sort by priority descending
        insights.sort((a, b) -> Integer.compare(b.priority, a.priority));
        return insights;
    }

    /**
     * Generates a single top-priority insight for notification copy.
     *
     * @param moodHistory ordered list of daily moods (oldest first)
     * @return the highest-priority insight
     */
    @NonNull
    public static MoodInsight getTopInsight(@Nullable List<String> moodHistory) {
        List<MoodInsight> all = generateInsights(moodHistory);
        return all.isEmpty()
                ? new MoodInsight(InsightType.COPING_SUGGESTION, 0, "💭",
                "How are you?", "Take a moment to check in.")
                : all.get(0);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INSIGHT GENERATORS
    // ═══════════════════════════════════════════════════════════════════

    private static void addStreakAlerts(List<MoodInsight> insights, List<String> history) {
        int negStreak = MoodMapper.getConsecutiveNegativeDays(history);

        if (negStreak >= 5) {
            insights.add(new MoodInsight(
                    InsightType.STREAK_ALERT, 100,
                    "🚨", "5+ days of distress",
                    "You've been in a tough space for " + negStreak +
                            " days. Please consider reaching out to someone you trust. " +
                            "MindTrace's safety resources are available if you need them."
            ));
        } else if (negStreak >= 3) {
            insights.add(new MoodInsight(
                    InsightType.STREAK_ALERT, 80,
                    "⚠️", negStreak + "-day low streak",
                    "You've been feeling " + MoodMapper.getMoodDescription(
                            history.get(history.size() - 1)).toLowerCase() +
                            " for " + negStreak + " days. " +
                            MoodMapper.getMoodCopingTip(history.get(history.size() - 1))
            ));
        } else if (negStreak == 2) {
            insights.add(new MoodInsight(
                    InsightType.STREAK_ALERT, 50,
                    "💛", "Two tough days",
                    "Yesterday and today have been hard. " +
                            "Tomorrow is a new opportunity. " +
                            MoodMapper.getMoodActionLabel(history.get(history.size() - 1)) + "."
            ));
        }
    }

    private static void addTrajectoryInsight(List<MoodInsight> insights, List<String> history) {
        if (history.size() < 3) return;

        String trajectory = MoodMapper.getMoodTrajectory(history);
        float velocity = MoodMapper.getMoodVelocity(history);

        switch (trajectory) {
            case "improving":
                insights.add(new MoodInsight(
                        InsightType.TRAJECTORY, 40,
                        "📈", "You're on the rise",
                        String.format(Locale.getDefault(),
                                "Your mood has been improving (+%.1f/day). " +
                                        "Whatever you're doing, keep at it!", velocity)
                ));
                break;
            case "declining":
                insights.add(new MoodInsight(
                        InsightType.TRAJECTORY, 70,
                        "📉", "Mood trending down",
                        String.format(Locale.getDefault(),
                                "Your wellbeing has dropped (%.1f/day). " +
                                        "Try to identify what changed recently.", velocity)
                ));
                break;
            case "volatile":
                insights.add(new MoodInsight(
                        InsightType.TRAJECTORY, 60,
                        "🎢", "Emotional rollercoaster",
                        "Your mood has been swinging a lot. Routine and sleep " +
                                "consistency can help stabilize your emotional baseline."
                ));
                break;
            // "stable" — no insight needed
        }
    }

    private static void addStabilityInsight(List<MoodInsight> insights, List<String> history) {
        if (history.size() < 4) return;

        float stability = MoodMapper.getMoodStability(history);
        float entropy = MoodMapper.getMoodEntropy(history);

        if (stability >= 0.85f) {
            insights.add(new MoodInsight(
                    InsightType.STABILITY, 20,
                    "🧘", "Emotionally consistent",
                    String.format(Locale.getDefault(),
                            "Your emotional stability is at %.0f%%. " +
                                    "This is a strong foundation for building new habits.",
                            stability * 100)
            ));
        } else if (entropy >= 0.8f) {
            insights.add(new MoodInsight(
                    InsightType.STABILITY, 55,
                    "🌀", "High emotional variability",
                    String.format(Locale.getDefault(),
                            "Your mood entropy is %.0f%% — you're experiencing many " +
                                    "different emotional states. Grounding exercises may help.",
                            entropy * 100)
            ));
        }
    }

    private static void addPositiveMomentum(List<MoodInsight> insights, List<String> history) {
        int posStreak = MoodMapper.getConsecutiveMoodDays(history, MoodMapper.POSITIVE_MOODS);

        if (posStreak >= 5) {
            insights.add(new MoodInsight(
                    InsightType.POSITIVE_MOMENTUM, 30,
                    "🌟", posStreak + "-day positive streak!",
                    "You've been feeling good for " + posStreak +
                            " days straight. This is exceptional — you're building resilience."
            ));
        } else if (posStreak >= 3) {
            insights.add(new MoodInsight(
                    InsightType.POSITIVE_MOMENTUM, 25,
                    "✨", "Great momentum",
                    posStreak + " days of positive mood. Keep nurturing what's working."
            ));
        }
    }

    private static void addCopingSuggestion(List<MoodInsight> insights, List<String> history) {
        if (history.isEmpty()) return;

        String latest = history.get(history.size() - 1);
        if (!MoodMapper.isNegativeMood(latest)) return;

        // Only suggest if no streak alert already covers this
        int negStreak = MoodMapper.getConsecutiveNegativeDays(history);
        if (negStreak >= 2) return; // streak alert handles this

        insights.add(new MoodInsight(
                InsightType.COPING_SUGGESTION, 35,
                MoodMapper.getMoodEmoji(latest), MoodMapper.getMoodActionLabel(latest),
                MoodMapper.getMoodCopingTip(latest)
        ));
    }

    private static void addWeeklySummary(List<MoodInsight> insights, List<String> history) {
        // Use last 7 entries or all if fewer
        int start = Math.max(0, history.size() - 7);
        List<String> week = history.subList(start, history.size());
        MoodMapper.WeeklySummary summary = MoodMapper.getWeeklySummary(week);

        String body = String.format(Locale.getDefault(),
                "%s Dominant mood: %s %s · Wellbeing: %.1f/4.0 · " +
                        "Stability: %.0f%% · %s",
                summary.getEmoji(),
                MoodMapper.getMoodEmoji(summary.dominantMood),
                summary.dominantMood != null ? summary.dominantMood : "Unknown",
                summary.averageWellbeing,
                summary.stability * 100,
                capitalizeFirst(summary.trajectory)
        );

        insights.add(new MoodInsight(
                InsightType.WEEKLY_SUMMARY, 15,
                "📊", summary.getLabel(), body
        ));
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /** Types of mood insights. */
    public enum InsightType {
        STREAK_ALERT,
        TRAJECTORY,
        STABILITY,
        WEEKLY_SUMMARY,
        POSITIVE_MOMENTUM,
        COPING_SUGGESTION
    }

    /** A single generated insight. */
    public static class MoodInsight {
        public final InsightType type;
        public final int priority;   // 0–100, higher = more urgent
        public final String emoji;
        public final String title;
        public final String body;

        public MoodInsight(InsightType type, int priority,
                           String emoji, String title, String body) {
            this.type = type;
            this.priority = priority;
            this.emoji = emoji;
            this.title = title;
            this.body = body;
        }

        /** Whether this insight requires immediate attention. */
        public boolean isUrgent() { return priority >= 70; }

        /** Whether this insight is positive reinforcement. */
        public boolean isPositive() {
            return type == InsightType.POSITIVE_MOMENTUM
                    || (type == InsightType.TRAJECTORY && priority <= 40);
        }

        /** Returns formatted display string: emoji + title. */
        @NonNull
        public String getDisplayTitle() { return emoji + "  " + title; }

        @NonNull
        @Override
        public String toString() {
            return "MoodInsight{" + type + ", p=" + priority +
                    ", title='" + title + "'}";
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return "Stable";
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }
}
