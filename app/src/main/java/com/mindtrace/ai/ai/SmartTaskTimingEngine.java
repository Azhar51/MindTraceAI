package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.InterventionTask;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Smart Task Timing Engine — analyzes when tasks are completed vs. time-of-day
 * and generates scheduling optimization insights.
 *
 * <h3>Capabilities:</h3>
 * <ul>
 *   <li>Productive window detection: "You complete 80% of tasks between 7-9 AM"</li>
 *   <li>Category-specific timing: "Mindfulness tasks work best in the morning"</li>
 *   <li>Skip probability prediction: tasks assigned during unproductive times</li>
 *   <li>Day-of-week patterns: "You're most active on Mondays and Thursdays"</li>
 *   <li>Scheduling recommendations: auto-adjust to natural rhythms</li>
 * </ul>
 */
public class SmartTaskTimingEngine {

    private static final String TAG = "SmartTaskTiming";
    private final AppDatabase db;

    public SmartTaskTimingEngine(@NonNull Context context) {
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIMING ANALYSIS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Analyze task completion patterns and generate timing insights.
     */
    @NonNull
    public TimingReport analyze() {
        TimingReport report = new TimingReport();

        try {
            List<InterventionTask> completed = db.taskDao().getCompletedTasksSync();
            List<InterventionTask> skipped = db.taskDao().getSkippedTasksSync();

            if (completed == null || completed.isEmpty()) return report;

            analyzeHourlyDistribution(completed, report);
            analyzeDayOfWeek(completed, report);
            analyzeCategoryTiming(completed, report);
            analyzeSkipPatterns(skipped, report);
            generateInsights(report);

        } catch (Exception e) {
            Log.e(TAG, "Failed to analyze task timing", e);
        }

        return report;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HOURLY DISTRIBUTION
    // ═══════════════════════════════════════════════════════════════════

    private void analyzeHourlyDistribution(List<InterventionTask> completed, TimingReport report) {
        int[] hourCounts = new int[24];
        Calendar cal = Calendar.getInstance();

        for (InterventionTask task : completed) {
            if (task.completedAt > 0) {
                cal.setTimeInMillis(task.completedAt);
                hourCounts[cal.get(Calendar.HOUR_OF_DAY)]++;
            }
        }

        report.hourlyDistribution = hourCounts;

        // Find peak window (2-hour block with most completions)
        int bestStart = 0;
        int bestCount = 0;
        for (int i = 0; i < 23; i++) {
            int windowCount = hourCounts[i] + hourCounts[i + 1];
            if (windowCount > bestCount) {
                bestCount = windowCount;
                bestStart = i;
            }
        }

        report.peakHourStart = bestStart;
        report.peakHourEnd = bestStart + 2;
        report.peakWindowCompletions = bestCount;
        report.totalCompletions = completed.size();
        report.peakWindowPercent = completed.size() > 0 ?
                (float) bestCount / completed.size() * 100 : 0;

        // Classify productive time
        if (bestStart >= 5 && bestStart < 10) {
            report.productiveTime = "Early Morning";
        } else if (bestStart >= 10 && bestStart < 14) {
            report.productiveTime = "Midday";
        } else if (bestStart >= 14 && bestStart < 18) {
            report.productiveTime = "Afternoon";
        } else if (bestStart >= 18 && bestStart < 22) {
            report.productiveTime = "Evening";
        } else {
            report.productiveTime = "Night";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DAY OF WEEK
    // ═══════════════════════════════════════════════════════════════════

    private void analyzeDayOfWeek(List<InterventionTask> completed, TimingReport report) {
        int[] dayCounts = new int[7]; // Sun=0 .. Sat=6
        String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday",
                "Thursday", "Friday", "Saturday"};
        Calendar cal = Calendar.getInstance();

        for (InterventionTask task : completed) {
            if (task.completedAt > 0) {
                cal.setTimeInMillis(task.completedAt);
                dayCounts[cal.get(Calendar.DAY_OF_WEEK) - 1]++;
            }
        }

        report.dayOfWeekDistribution = dayCounts;

        // Best day
        int bestDay = 0;
        for (int i = 1; i < 7; i++) {
            if (dayCounts[i] > dayCounts[bestDay]) bestDay = i;
        }
        report.bestDay = dayNames[bestDay];

        // Worst day
        int worstDay = 0;
        for (int i = 1; i < 7; i++) {
            if (dayCounts[i] < dayCounts[worstDay]) worstDay = i;
        }
        report.worstDay = dayNames[worstDay];

        // Weekend vs weekday
        int weekday = dayCounts[1] + dayCounts[2] + dayCounts[3] + dayCounts[4] + dayCounts[5];
        int weekend = dayCounts[0] + dayCounts[6];
        float weekdayAvg = weekday / 5.0f;
        float weekendAvg = weekend / 2.0f;
        report.prefersWeekdays = weekdayAvg > weekendAvg;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORY × TIME CORRELATION
    // ═══════════════════════════════════════════════════════════════════

    private void analyzeCategoryTiming(List<InterventionTask> completed, TimingReport report) {
        Map<String, int[]> categoryHours = new HashMap<>();
        Calendar cal = Calendar.getInstance();

        for (InterventionTask task : completed) {
            if (task.completedAt > 0 && task.category != null) {
                categoryHours.computeIfAbsent(task.category, k -> new int[4]); // morning/afternoon/evening/night
                cal.setTimeInMillis(task.completedAt);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                int slot;
                if (hour >= 6 && hour < 12) slot = 0;      // Morning
                else if (hour >= 12 && hour < 18) slot = 1; // Afternoon
                else if (hour >= 18 && hour < 22) slot = 2; // Evening
                else slot = 3;                                // Night
                categoryHours.get(task.category)[slot]++;
            }
        }

        String[] slotNames = {"morning", "afternoon", "evening", "night"};
        report.categoryTimePreferences = new HashMap<>();

        for (Map.Entry<String, int[]> entry : categoryHours.entrySet()) {
            int[] slots = entry.getValue();
            int bestSlot = 0;
            for (int i = 1; i < 4; i++) {
                if (slots[i] > slots[bestSlot]) bestSlot = i;
            }
            report.categoryTimePreferences.put(entry.getKey(), slotNames[bestSlot]);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SKIP PATTERN ANALYSIS
    // ═══════════════════════════════════════════════════════════════════

    private void analyzeSkipPatterns(List<InterventionTask> skipped, TimingReport report) {
        if (skipped == null || skipped.isEmpty()) {
            report.skipRiskInsight = "You rarely skip tasks — excellent consistency!";
            return;
        }

        // Category skip rates
        Map<String, Integer> skipsByCategory = new HashMap<>();
        for (InterventionTask task : skipped) {
            if (task.category != null) {
                skipsByCategory.merge(task.category, 1, Integer::sum);
            }
        }

        String worstCategory = null;
        int worstCount = 0;
        for (Map.Entry<String, Integer> entry : skipsByCategory.entrySet()) {
            if (entry.getValue() > worstCount) {
                worstCount = entry.getValue();
                worstCategory = entry.getKey();
            }
        }

        if (worstCategory != null) {
            report.mostSkippedCategory = worstCategory;
            report.skipRiskInsight = String.format(Locale.US,
                    "%s tasks are skipped most often (%d times). Try shorter versions or different timing.",
                    worstCategory, worstCount);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INSIGHT GENERATION
    // ═══════════════════════════════════════════════════════════════════

    private void generateInsights(TimingReport report) {
        report.insights = new ArrayList<>();

        // Peak window
        if (report.peakWindowPercent > 30) {
            report.insights.add(String.format(Locale.US,
                    "You complete %.0f%% of tasks between %d:00-%d:00 — that's your power window!",
                    report.peakWindowPercent, report.peakHourStart, report.peakHourEnd));
        }

        // Best day
        if (report.bestDay != null) {
            report.insights.add(String.format(Locale.US,
                    "%s is your most productive day", report.bestDay));
        }

        // Category-time preferences
        if (report.categoryTimePreferences != null) {
            for (Map.Entry<String, String> entry : report.categoryTimePreferences.entrySet()) {
                report.insights.add(String.format(Locale.US,
                        "%s tasks work best in the %s for you", entry.getKey(), entry.getValue()));
            }
        }

        // Weekend vs weekday
        if (report.prefersWeekdays) {
            report.insights.add("You're more consistent on weekdays — consider lighter weekend tasks");
        }

        // Skip risk
        if (report.skipRiskInsight != null) {
            report.insights.add(report.skipRiskInsight);
        }
    }

    /**
     * Get the optimal hour to schedule a task of the given category.
     */
    public int getOptimalHour(@Nullable String category) {
        try {
            TimingReport report = analyze();
            if (report.categoryTimePreferences != null && category != null) {
                String timeSlot = report.categoryTimePreferences.get(category);
                if (timeSlot != null) {
                    switch (timeSlot) {
                        case "morning": return 8;
                        case "afternoon": return 14;
                        case "evening": return 19;
                        case "night": return 21;
                    }
                }
            }
            // Fallback to peak window
            return report.peakHourStart > 0 ? report.peakHourStart : 9;
        } catch (Exception e) {
            return 9; // Default
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    public static class TimingReport {
        // Hourly
        public int[] hourlyDistribution;
        public int peakHourStart;
        public int peakHourEnd;
        public int peakWindowCompletions;
        public int totalCompletions;
        public float peakWindowPercent;
        public String productiveTime;

        // Day of week
        public int[] dayOfWeekDistribution;
        public String bestDay;
        public String worstDay;
        public boolean prefersWeekdays;

        // Category × Time
        public Map<String, String> categoryTimePreferences;

        // Skip analysis
        public String mostSkippedCategory;
        public String skipRiskInsight;

        // Generated
        public List<String> insights = new ArrayList<>();
    }
}
