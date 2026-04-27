package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Weekly wellness summary generator — produces a comprehensive narrative
 * and statistics every week for the user's personal growth tracking.
 *
 * <h3>Generated Every Sunday Night by MidnightSummaryWorker</h3>
 *
 * <h3>Summary Contents:</h3>
 * <ul>
 *   <li><b>Usage stats</b> — total screen time, daily average, pickups, peak/best day</li>
 *   <li><b>Mood stats</b> — dominant mood, avg stress, avg sleep, trend direction</li>
 *   <li><b>Task stats</b> — completed vs. total, completion rate</li>
 *   <li><b>Risk trend</b> — direction + magnitude of overall risk change</li>
 *   <li><b>Narrative</b> — human-readable paragraph summarizing the week</li>
 * </ul>
 *
 * @see TrendPredictor
 * @see CorrelationEngine
 */
public class WeeklySummaryGenerator {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    // ═══════════════════════════════════════════════════════════════════
    // WEEKLY SUMMARY DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    public static class WeeklySummary {
        // Identifiers
        public long weekStart;
        public long weekEnd;
        public long generatedAt;

        // Usage stats
        public long totalScreenTimeMs;
        public long avgDailyScreenTimeMs;
        public int totalPickups;
        public String peakDay;
        public String bestDay;
        public float avgDailyScreenTimeHours;

        // Mood stats
        public String dominantMood;
        public float avgStress;
        public float avgSleep;
        public boolean moodImproved;

        // Task stats
        public int tasksCompleted;
        public int tasksTotal;
        public float completionRate;

        // Risk trend
        public float riskChange;
        public String riskDirection; // "IMPROVING", "WORSENING", "STABLE"

        // Narrative
        public String narrative;

        /** Get formatted screen time string. */
        @NonNull
        public String getFormattedScreenTime() {
            long hours = totalScreenTimeMs / (1000 * 60 * 60);
            long minutes = (totalScreenTimeMs / (1000 * 60)) % 60;
            return hours + "h " + minutes + "m";
        }

        /** Get formatted daily average. */
        @NonNull
        public String getFormattedDailyAverage() {
            long hours = avgDailyScreenTimeMs / (1000 * 60 * 60);
            long minutes = (avgDailyScreenTimeMs / (1000 * 60)) % 60;
            return hours + "h " + minutes + "m";
        }

        /** Get completion rate as a percentage string. */
        @NonNull
        public String getCompletionRateLabel() {
            return String.format(Locale.US, "%.0f%%", completionRate * 100);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIMARY API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a complete weekly summary.
     *
     * @param weekUsage           this week's usage records
     * @param weekMoods           this week's mood check-ins
     * @param weekTasks           this week's intervention tasks
     * @param weekClassifications this week's risk classifications
     * @return populated WeeklySummary with narrative
     */
    @NonNull
    public WeeklySummary generate(
            @Nullable List<DailyUsage> weekUsage,
            @Nullable List<QuestionnaireResponse> weekMoods,
            @Nullable List<InterventionTask> weekTasks,
            @Nullable List<RiskClassification> weekClassifications) {

        WeeklySummary summary = new WeeklySummary();
        summary.generatedAt = System.currentTimeMillis();
        summary.weekStart = getMonday();
        summary.weekEnd = getSunday();

        // ── Usage stats ──
        if (weekUsage != null && !weekUsage.isEmpty()) {
            computeUsageStats(weekUsage, summary);
        }

        // ── Mood stats ──
        if (weekMoods != null && !weekMoods.isEmpty()) {
            computeMoodStats(weekMoods, summary);
        }

        // ── Task stats ──
        if (weekTasks != null && !weekTasks.isEmpty()) {
            computeTaskStats(weekTasks, summary);
        }

        // ── Risk trend ──
        if (weekClassifications != null && weekClassifications.size() >= 2) {
            computeRiskTrend(weekClassifications, summary);
        } else {
            summary.riskDirection = "STABLE";
        }

        // ── Generate narrative ──
        summary.narrative = generateNarrative(summary);

        return summary;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STAT COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    private void computeUsageStats(@NonNull List<DailyUsage> usage,
                                    @NonNull WeeklySummary s) {
        long totalScreen = 0;
        int totalPickups = 0;
        long maxScreen = Long.MIN_VALUE;
        long minScreen = Long.MAX_VALUE;
        String peakDay = null;
        String bestDay = null;

        Calendar cal = Calendar.getInstance();
        for (DailyUsage u : usage) {
            totalScreen += u.screenTimeMillis;
            totalPickups += u.unlockCount;

            cal.setTimeInMillis(u.date);
            String dayName = cal.getDisplayName(
                    Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());

            if (u.screenTimeMillis > maxScreen) {
                maxScreen = u.screenTimeMillis;
                peakDay = dayName;
            }
            if (u.screenTimeMillis < minScreen) {
                minScreen = u.screenTimeMillis;
                bestDay = dayName;
            }
        }

        s.totalScreenTimeMs = totalScreen;
        s.avgDailyScreenTimeMs = totalScreen / Math.max(1, usage.size());
        s.avgDailyScreenTimeHours = s.avgDailyScreenTimeMs / (1000f * 60f * 60f);
        s.totalPickups = totalPickups;
        s.peakDay = peakDay;
        s.bestDay = bestDay;
    }

    private void computeMoodStats(@NonNull List<QuestionnaireResponse> moods,
                                   @NonNull WeeklySummary s) {
        // Count mood occurrences
        Map<String, Integer> moodCounts = new HashMap<>();
        float totalStress = 0;
        float totalSleep = 0;
        int stressCount = 0;
        int sleepCount = 0;

        for (QuestionnaireResponse r : moods) {
            String mood = r.mood != null ? r.mood : "Neutral";
            moodCounts.merge(mood, 1, Integer::sum);

            if (r.stressLevel > 0) { totalStress += r.stressLevel; stressCount++; }
            if (r.sleepHours > 0) { totalSleep += r.sleepHours; sleepCount++; }
        }

        // Find dominant mood
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : moodCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                s.dominantMood = entry.getKey();
            }
        }

        s.avgStress = stressCount > 0 ? totalStress / stressCount : 0;
        s.avgSleep = sleepCount > 0 ? totalSleep / sleepCount : 0;

        // Mood improvement: compare first half vs. second half
        if (moods.size() >= 4) {
            int mid = moods.size() / 2;
            float firstHalf = 0, secondHalf = 0;
            for (int i = 0; i < mid; i++) {
                firstHalf += TrendPredictor.mapMoodToFloat(moods.get(i).mood);
            }
            for (int i = mid; i < moods.size(); i++) {
                secondHalf += TrendPredictor.mapMoodToFloat(moods.get(i).mood);
            }
            firstHalf /= mid;
            secondHalf /= (moods.size() - mid);
            s.moodImproved = secondHalf > firstHalf + 0.2f;
        }
    }

    private void computeTaskStats(@NonNull List<InterventionTask> tasks,
                                   @NonNull WeeklySummary s) {
        int completed = 0;
        for (InterventionTask t : tasks) {
            if (t.isCompleted) completed++;
        }
        s.tasksCompleted = completed;
        s.tasksTotal = tasks.size();
        s.completionRate = tasks.size() > 0 ? (float) completed / tasks.size() : 0;
    }

    private void computeRiskTrend(@NonNull List<RiskClassification> classifications,
                                   @NonNull WeeklySummary s) {
        float startRisk = classifications.get(0).overallRiskScore;
        float endRisk = classifications.get(classifications.size() - 1).overallRiskScore;
        s.riskChange = endRisk - startRisk;

        if (s.riskChange > 0.05f) {
            s.riskDirection = "WORSENING";
        } else if (s.riskChange < -0.05f) {
            s.riskDirection = "IMPROVING";
        } else {
            s.riskDirection = "STABLE";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NARRATIVE GENERATION
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private String generateNarrative(@NonNull WeeklySummary s) {
        StringBuilder sb = new StringBuilder();

        // Opening based on risk direction
        if ("IMPROVING".equals(s.riskDirection)) {
            sb.append("Great week! Your overall wellness has improved. ");
        } else if ("WORSENING".equals(s.riskDirection)) {
            sb.append("This week was a bit challenging. ");
        } else {
            sb.append("Your patterns remained steady this week. ");
        }

        // Screen time insight
        if (s.avgDailyScreenTimeHours > 0) {
            sb.append(String.format(Locale.US,
                    "You averaged %.1fh of screen time daily",
                    s.avgDailyScreenTimeHours));
            if (s.peakDay != null) {
                sb.append(" (peak on ").append(s.peakDay).append(")");
            }
            sb.append(". ");
        }

        // Task completion
        if (s.tasksTotal > 0) {
            if (s.completionRate > 0.7f) {
                sb.append(String.format(Locale.US,
                        "You completed %d of %d tasks — excellent engagement! ",
                        s.tasksCompleted, s.tasksTotal));
            } else if (s.completionRate > 0.4f) {
                sb.append(String.format(Locale.US,
                        "You completed %d of %d tasks — solid effort! ",
                        s.tasksCompleted, s.tasksTotal));
            } else if (s.tasksCompleted > 0) {
                sb.append(String.format(Locale.US,
                        "You completed %d of %d tasks — every step counts. ",
                        s.tasksCompleted, s.tasksTotal));
            }
        }

        // Mood insight
        if (s.moodImproved) {
            sb.append("Your mood showed improvement as the week went on. ");
        }

        // Sleep insight
        if (s.avgSleep > 0) {
            if (s.avgSleep >= 7.0f) {
                sb.append(String.format(Locale.US,
                        "Great sleep averaging %.1f hours. ", s.avgSleep));
            } else if (s.avgSleep < 6.0f) {
                sb.append(String.format(Locale.US,
                        "Your sleep averaged %.1f hours — try to prioritize rest. ",
                        s.avgSleep));
            }
        }

        return sb.toString().trim();
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /** Get timestamp for Monday 00:00 of the current week. */
    private long getMonday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Get timestamp for Sunday 23:59 of the current week. */
    private long getSunday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }
}
