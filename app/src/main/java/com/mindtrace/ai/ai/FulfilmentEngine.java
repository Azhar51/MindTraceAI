package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.WeeklyAssessment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-dimensional Fulfilment Engine — tracks life fulfilment across 5 dimensions
 * and generates actionable insights for purpose alignment and growth.
 *
 * <h3>Dimensions (weighted):</h3>
 * <ul>
 *   <li><b>Task Completion</b> (30%) — task completion rate, consistency, variety</li>
 *   <li><b>Purpose Alignment</b> (25%) — purposeScore trend from check-ins/weekly assessments</li>
 *   <li><b>Social Connection</b> (20%) — trusted contacts used, social tasks completed</li>
 *   <li><b>Physical Activity</b> (15%) — exercise tasks, outdoor activities, movement</li>
 *   <li><b>Growth Trajectory</b> (10%) — improvement rate, new categories tried, streak length</li>
 * </ul>
 */
public class FulfilmentEngine {

    private static final String TAG = "FulfilmentEngine";
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long MONTH_MS = 30L * 24 * 60 * 60 * 1000;

    private final AppDatabase db;

    public FulfilmentEngine(@NonNull Context context) {
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPOSITE SCORE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculate composite fulfilment score (0-100).
     */
    @NonNull
    public FulfilmentReport generateReport() {
        FulfilmentReport report = new FulfilmentReport();

        try {
            long now = System.currentTimeMillis();
            long weekAgo = now - WEEK_MS;
            long twoWeeksAgo = now - 2 * WEEK_MS;

            // Dimension 1: Task Completion (30%)
            report.taskCompletionScore = calculateTaskCompletion(weekAgo);

            // Dimension 2: Purpose Alignment (25%)
            report.purposeAlignmentScore = calculatePurposeAlignment();

            // Dimension 3: Social Connection (20%)
            report.socialConnectionScore = calculateSocialConnection(weekAgo);

            // Dimension 4: Physical Activity (15%)
            report.physicalActivityScore = calculatePhysicalActivity(weekAgo);

            // Dimension 5: Growth Trajectory (10%)
            report.growthTrajectoryScore = calculateGrowthTrajectory(weekAgo, twoWeeksAgo);

            // Composite
            report.compositeScore = (int) (
                    report.taskCompletionScore * 0.30f +
                    report.purposeAlignmentScore * 0.25f +
                    report.socialConnectionScore * 0.20f +
                    report.physicalActivityScore * 0.15f +
                    report.growthTrajectoryScore * 0.10f
            );

            // Purpose drift detection
            report.purposeDrift = detectPurposeDrift();

            // Generate insights
            report.insights = generateInsights(report);

            // Generate narrative
            report.narrative = generateNarrative(report);

        } catch (Exception e) {
            Log.e(TAG, "Failed to generate fulfilment report", e);
        }

        return report;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIMENSION 1: TASK COMPLETION
    // ═══════════════════════════════════════════════════════════════════

    private float calculateTaskCompletion(long since) {
        try {
            int total = db.taskDao().getTotalCountSince(since);
            int completed = db.taskDao().getCompletedCountSince(since);
            int skipped = db.taskDao().getSkippedCountSince(since);

            if (total == 0) return 50f; // Neutral if no tasks

            float completionRate = (float) completed / total;
            float skipPenalty = (float) skipped / total * 0.3f;
            float score = (completionRate - skipPenalty) * 100;

            // Bonus for consistency (completing tasks on multiple days)
            int activeDays = db.taskDao().getCompletionDayCountSince(since);
            float consistencyBonus = Math.min(20f, activeDays * 3f);

            return Math.max(0, Math.min(100, score + consistencyBonus));
        } catch (Exception e) {
            return 50f;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIMENSION 2: PURPOSE ALIGNMENT
    // ═══════════════════════════════════════════════════════════════════

    private float calculatePurposeAlignment() {
        try {
            List<WeeklyAssessment> assessments = db.weeklyAssessmentDao().getHistoricalAssessments(4);
            if (assessments == null || assessments.isEmpty()) return 50f;

            float totalPurpose = 0;
            for (WeeklyAssessment a : assessments) {
                totalPurpose += a.purposeScore;
            }
            float avgPurpose = totalPurpose / assessments.size();

            // Scale from 0-10 to 0-100
            return Math.min(100, avgPurpose * 10);
        } catch (Exception e) {
            return 50f;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIMENSION 3: SOCIAL CONNECTION
    // ═══════════════════════════════════════════════════════════════════

    private float calculateSocialConnection(long since) {
        try {
            int socialTasks = db.taskDao().getCompletedCountForCategory("Social", since);
            int relationshipTasks = db.taskDao().getCompletedCountForCategory("Relationships", since);
            int connectionTasks = socialTasks + relationshipTasks;

            // Base score from social tasks
            float score = Math.min(70, connectionTasks * 15f);

            // Bonus if trusted contacts exist
            try {
                android.database.Cursor cursor = db.getOpenHelper().getReadableDatabase()
                        .query("SELECT COUNT(*) FROM trusted_contacts");
                if (cursor != null && cursor.moveToFirst()) {
                    int contactCount = cursor.getInt(0);
                    if (contactCount > 0) score += 15f;
                    if (contactCount >= 3) score += 15f;
                    cursor.close();
                }
            } catch (Exception ignored) {}

            return Math.min(100, score);
        } catch (Exception e) {
            return 40f;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIMENSION 4: PHYSICAL ACTIVITY
    // ═══════════════════════════════════════════════════════════════════

    private float calculatePhysicalActivity(long since) {
        try {
            int exerciseTasks = db.taskDao().getCompletedCountForCategory("Physical", since);
            int wellnessTasks = db.taskDao().getCompletedCountForCategory("Wellness", since);
            int mindfulTasks = db.taskDao().getCompletedCountForCategory("Mindfulness", since);

            float score = Math.min(60, (exerciseTasks + wellnessTasks) * 15f);
            score += Math.min(30, mindfulTasks * 10f);

            // Bonus for exercise completions
            try {
                List<com.mindtrace.ai.database.entity.ExerciseCompletion> exercises =
                        db.exerciseCompletionDao().getRecent(10);
                if (exercises != null && !exercises.isEmpty()) {
                    score += Math.min(10, exercises.size() * 2f);
                }
            } catch (Exception ignored) {}

            return Math.min(100, score);
        } catch (Exception e) {
            return 30f;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIMENSION 5: GROWTH TRAJECTORY
    // ═══════════════════════════════════════════════════════════════════

    private float calculateGrowthTrajectory(long weekAgo, long twoWeeksAgo) {
        try {
            int thisWeekCompleted = db.taskDao().getCompletedCountSince(weekAgo);
            int lastWeekCompleted = db.taskDao().getCompletedCountSince(twoWeeksAgo)
                    - thisWeekCompleted;

            float growthRate = 0;
            if (lastWeekCompleted > 0) {
                growthRate = (float) (thisWeekCompleted - lastWeekCompleted) / lastWeekCompleted;
            }

            // Base score
            float score = 50;

            // Growth bonus/penalty
            if (growthRate > 0.2f) score += 25; // Significant improvement
            else if (growthRate > 0) score += 10;
            else if (growthRate < -0.2f) score -= 15;

            // Category diversity bonus
            String mostCompleted = db.taskDao().getMostCompletedCategory();
            String leastCompleted = db.taskDao().getMostSkippedCategory();
            if (mostCompleted != null && leastCompleted != null && !mostCompleted.equals(leastCompleted)) {
                score += 10; // Variety in task types
            }

            // Streak bonus
            List<Long> days = db.taskDao().getCompletionDays();
            int streak = calculateStreak(days);
            score += Math.min(15, streak * 3f);

            return Math.max(0, Math.min(100, score));
        } catch (Exception e) {
            return 50f;
        }
    }

    private int calculateStreak(List<Long> days) {
        if (days == null || days.isEmpty()) return 0;
        int streak = 1;
        for (int i = 1; i < days.size(); i++) {
            if (days.get(i - 1) - days.get(i) == 1) streak++;
            else break;
        }
        return streak;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PURPOSE DRIFT DETECTION
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private PurposeDrift detectPurposeDrift() {
        PurposeDrift drift = new PurposeDrift();

        try {
            List<WeeklyAssessment> assessments = db.weeklyAssessmentDao().getHistoricalAssessments(6);
            if (assessments == null || assessments.size() < 3) {
                drift.status = "insufficient_data";
                return drift;
            }

            // Check for declining trend over 3+ weeks
            int decliningWeeks = 0;
            for (int i = 1; i < assessments.size(); i++) {
                if (assessments.get(i - 1).purposeScore < assessments.get(i).purposeScore) {
                    decliningWeeks++;
                }
            }

            float recentAvg = 0, olderAvg = 0;
            int mid = assessments.size() / 2;
            for (int i = 0; i < mid; i++) recentAvg += assessments.get(i).purposeScore;
            for (int i = mid; i < assessments.size(); i++) olderAvg += assessments.get(i).purposeScore;
            recentAvg /= mid;
            olderAvg /= (assessments.size() - mid);

            drift.recentAverage = recentAvg;
            drift.olderAverage = olderAvg;
            drift.delta = recentAvg - olderAvg;

            if (decliningWeeks >= 3) {
                drift.status = "declining";
                drift.message = "Your sense of purpose has been declining for " +
                        decliningWeeks + " weeks. Consider journaling about what gives you meaning.";
            } else if (drift.delta > 1.0f) {
                drift.status = "improving";
                drift.message = "Your sense of purpose is growing — keep doing what you're doing!";
            } else if (drift.delta < -1.0f) {
                drift.status = "dipping";
                drift.message = "Your purpose score dipped recently. A small values-aligned activity might help.";
            } else {
                drift.status = "stable";
                drift.message = "Your sense of purpose is holding steady.";
            }
        } catch (Exception e) {
            drift.status = "error";
        }

        return drift;
    }

    // ═══════════════════════════════════════════════════════════════════
    // INSIGHT GENERATION
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private List<String> generateInsights(FulfilmentReport report) {
        List<String> insights = new ArrayList<>();

        // Strongest dimension
        Map<String, Float> dims = new HashMap<>();
        dims.put("Task Completion", report.taskCompletionScore);
        dims.put("Purpose", report.purposeAlignmentScore);
        dims.put("Social Connection", report.socialConnectionScore);
        dims.put("Physical Activity", report.physicalActivityScore);
        dims.put("Growth", report.growthTrajectoryScore);

        String strongest = null, weakest = null;
        float max = -1, min = 101;
        for (Map.Entry<String, Float> e : dims.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); strongest = e.getKey(); }
            if (e.getValue() < min) { min = e.getValue(); weakest = e.getKey(); }
        }

        if (strongest != null) {
            insights.add("💪 Strongest area: " + strongest + " (" + (int) max + "%)");
        }
        if (weakest != null && min < 40) {
            insights.add("🎯 Area to focus: " + weakest + " (" + (int) min + "%)");
        }

        // Purpose drift
        if ("declining".equals(report.purposeDrift.status)) {
            insights.add("⚠️ " + report.purposeDrift.message);
        } else if ("improving".equals(report.purposeDrift.status)) {
            insights.add("🌱 " + report.purposeDrift.message);
        }

        // Composite assessment
        if (report.compositeScore >= 75) {
            insights.add("✨ High fulfilment — you're living aligned with your values");
        } else if (report.compositeScore >= 50) {
            insights.add("📊 Moderate fulfilment — room for growth in specific areas");
        } else {
            insights.add("💡 Low fulfilment — small changes in " + weakest + " could make a big impact");
        }

        return insights;
    }

    // ═══════════════════════════════════════════════════════════════════
    // NARRATIVE GENERATION
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private String generateNarrative(FulfilmentReport report) {
        StringBuilder sb = new StringBuilder();

        if (report.compositeScore >= 75) {
            sb.append("Your fulfilment score is strong at ").append(report.compositeScore).append("%.");
        } else if (report.compositeScore >= 50) {
            sb.append("Your fulfilment score is ").append(report.compositeScore)
                    .append("% — steady but with room to grow.");
        } else {
            sb.append("Your fulfilment is at ").append(report.compositeScore)
                    .append("% — let's find small ways to boost it.");
        }

        if (report.taskCompletionScore > 70) {
            sb.append(" Your task consistency is excellent.");
        }

        if (report.socialConnectionScore < 30) {
            sb.append(" Reaching out to someone today could help.");
        }

        if ("declining".equals(report.purposeDrift.status)) {
            sb.append(" Your purpose score has been dipping — consider what matters most to you right now.");
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════

    public static class FulfilmentReport {
        public int compositeScore = 0;
        public float taskCompletionScore = 0;
        public float purposeAlignmentScore = 0;
        public float socialConnectionScore = 0;
        public float physicalActivityScore = 0;
        public float growthTrajectoryScore = 0;
        public PurposeDrift purposeDrift = new PurposeDrift();
        public List<String> insights = new ArrayList<>();
        public String narrative = "";
    }

    public static class PurposeDrift {
        public String status = "insufficient_data";
        public String message = "";
        public float recentAverage = 0;
        public float olderAverage = 0;
        public float delta = 0;
    }
}
