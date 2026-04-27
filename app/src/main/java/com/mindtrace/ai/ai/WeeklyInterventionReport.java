package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.entity.InterventionTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Weekly intervention intelligence report — auto-generated narrative
 * summarizing task performance, effectiveness, streaks, and risk correlation.
 *
 * <h3>Report Contents:</h3>
 * <ul>
 *   <li>Tasks generated / completed / skipped / expired</li>
 *   <li>Completion rate (percentage)</li>
 *   <li>Average effectiveness rating (1-5 stars)</li>
 *   <li>Best and worst performing categories</li>
 *   <li>Streak status (current + longest)</li>
 *   <li>Risk score correlation</li>
 *   <li>Auto-generated narrative summary</li>
 * </ul>
 */
public class WeeklyInterventionReport {

    // ═══════════════════════════════════════════════════════════════════
    // DATA FIELDS
    // ═══════════════════════════════════════════════════════════════════

    public int tasksGenerated;
    public int completed;
    public int skipped;
    public int expired;
    public float completionRate;       // 0.0 - 1.0
    public float averageEffectiveness; // 0.0 - 5.0
    public String bestCategory;
    public String worstCategory;
    public int currentStreak;
    public int longestStreak;
    public float riskScoreStart;       // Risk at start of week
    public float riskScoreEnd;         // Risk at end of week
    public int totalXpEarned;
    public long generatedAt;

    // Category breakdown
    public Map<String, CategoryStats> categoryBreakdown = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORY STATS
    // ═══════════════════════════════════════════════════════════════════

    public static class CategoryStats {
        public int generated;
        public int completed;
        public int skipped;
        public float avgEffectiveness;

        public float getCompletionRate() {
            return generated > 0 ? (float) completed / generated : 0f;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER — generates report from task list
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a weekly report from a list of tasks created in the last 7 days.
     */
    @NonNull
    public static WeeklyInterventionReport generate(@NonNull List<InterventionTask> weekTasks,
                                                     int currentStreak, int longestStreak,
                                                     float riskStart, float riskEnd) {
        WeeklyInterventionReport report = new WeeklyInterventionReport();
        report.generatedAt = System.currentTimeMillis();
        report.currentStreak = currentStreak;
        report.longestStreak = longestStreak;
        report.riskScoreStart = riskStart;
        report.riskScoreEnd = riskEnd;
        report.tasksGenerated = weekTasks.size();

        float totalEffectiveness = 0;
        int effectivenessCount = 0;
        int totalXp = 0;

        for (InterventionTask task : weekTasks) {
            String cat = task.category != null ? task.category : "General";

            // Get or create category stats
            CategoryStats stats = report.categoryBreakdown.get(cat);
            if (stats == null) {
                stats = new CategoryStats();
                report.categoryBreakdown.put(cat, stats);
            }
            stats.generated++;

            if ("COMPLETED".equals(task.status)) {
                report.completed++;
                stats.completed++;
                totalXp += task.getEffectiveXp();

                if (task.effectivenessRating > 0) {
                    totalEffectiveness += task.effectivenessRating;
                    effectivenessCount++;
                    stats.avgEffectiveness = (stats.avgEffectiveness * (stats.completed - 1) + task.effectivenessRating) / stats.completed;
                }
            } else if ("SKIPPED".equals(task.status)) {
                report.skipped++;
                stats.skipped++;
            } else if ("EXPIRED".equals(task.status)) {
                report.expired++;
            }
        }

        report.totalXpEarned = totalXp;
        report.completionRate = report.tasksGenerated > 0 ?
                (float) report.completed / report.tasksGenerated : 0f;
        report.averageEffectiveness = effectivenessCount > 0 ?
                totalEffectiveness / effectivenessCount : 0f;

        // Find best & worst categories
        float bestRate = -1f, worstRate = 2f;
        for (Map.Entry<String, CategoryStats> entry : report.categoryBreakdown.entrySet()) {
            float rate = entry.getValue().getCompletionRate();
            if (rate > bestRate) {
                bestRate = rate;
                report.bestCategory = entry.getKey();
            }
            if (rate < worstRate && entry.getValue().generated >= 2) {
                worstRate = rate;
                report.worstCategory = entry.getKey();
            }
        }

        return report;
    }

    // ═══════════════════════════════════════════════════════════════════
    // NARRATIVE GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a human-readable narrative summary.
     */
    @NonNull
    public String toNarrative() {
        StringBuilder sb = new StringBuilder();

        // Opening
        if (completionRate >= 0.8f) {
            sb.append("🌟 Exceptional week! ");
        } else if (completionRate >= 0.5f) {
            sb.append("👍 Good progress this week. ");
        } else if (completed > 0) {
            sb.append("Every step counts. ");
        } else {
            sb.append("This week was quiet. ");
        }

        // Completion stats
        sb.append(String.format(Locale.US, "You completed %d of %d tasks (%.0f%%).",
                completed, tasksGenerated, completionRate * 100));

        // XP
        if (totalXpEarned > 0) {
            sb.append(String.format(Locale.US, " Earned %d XP.", totalXpEarned));
        }

        // Effectiveness
        if (averageEffectiveness > 0) {
            sb.append(String.format(Locale.US, "\n\nAverage helpfulness rating: %.1f/5.", averageEffectiveness));
        }

        // Best category
        if (bestCategory != null && completionRate > 0) {
            sb.append("\n\n").append(bestCategory).append(" tasks worked best for you");
            if (averageEffectiveness > 3.5f) {
                sb.append(" — keep it up!");
            } else {
                sb.append(".");
            }
        }

        // Worst category
        if (worstCategory != null && !worstCategory.equals(bestCategory)) {
            sb.append(" ").append(worstCategory).append(" tasks were tougher — we'll adjust difficulty.");
        }

        // Risk correlation
        float riskDelta = riskScoreEnd - riskScoreStart;
        if (completed >= 3 && riskDelta < -0.05f) {
            sb.append(String.format(Locale.US, "\n\n📉 Your risk score dropped %.0f%% this week — your efforts are making a difference.",
                    Math.abs(riskDelta) * 100));
        } else if (completed >= 3 && riskDelta > 0.1f) {
            sb.append("\n\n📈 Risk went up slightly despite task completion — we'll try different approaches.");
        }

        // Streak
        if (currentStreak >= 7) {
            sb.append(String.format(Locale.US, "\n\n🔥 %d-day streak — you're on fire!", currentStreak));
        } else if (currentStreak >= 3) {
            sb.append(String.format(Locale.US, "\n\n🔥 %d-day streak — keep going!", currentStreak));
        }

        // Skipped analysis
        if (skipped > completed && skipped >= 3) {
            sb.append("\n\nYou skipped more tasks than you completed. We'll suggest simpler, shorter tasks next week.");
        }

        return sb.toString();
    }

    /**
     * Generate a short summary for notification.
     */
    @NonNull
    public String toShortSummary() {
        return String.format(Locale.US, "%d/%d completed (%.0f%%) • %d XP earned",
                completed, tasksGenerated, completionRate * 100, totalXpEarned);
    }

    @NonNull
    @Override
    public String toString() {
        return "WeeklyInterventionReport{" +
                "completed=" + completed + "/" + tasksGenerated +
                ", rate=" + String.format(Locale.US, "%.0f%%", completionRate * 100) +
                ", xp=" + totalXpEarned +
                ", streak=" + currentStreak +
                '}';
    }
}
