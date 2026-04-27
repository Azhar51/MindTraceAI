package com.mindtrace.ai.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.Calendar;
import java.util.List;

/**
 * Repository layer for {@link RiskClassification} — mediates between
 * the AI classification engine and the persistence/UI layers.
 *
 * <p>Provides cached access, staleness checks, trend queries, and
 * aggregated analytics for the dashboard and insight engine.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   ClassificationRepository repo = new ClassificationRepository(context);
 *   repo.save(classification);
 *   RiskClassification latest = repo.getLatest();
 *   boolean stale = repo.isStale(12); // older than 12 hours?
 *   float trend = repo.getTrendForCategory("depression", 7);
 * </pre>
 */
public class ClassificationRepository {

    private static final String TAG = "ClassificationRepo";
    private static final long HOUR_MS = 60L * 60 * 1000;
    private static final long DAY_MS = 24L * HOUR_MS;

    private final RiskClassificationDao dao;

    public ClassificationRepository(@NonNull Context context) {
        this.dao = AppDatabase.getInstance(context.getApplicationContext()).riskClassificationDao();
    }

    public ClassificationRepository(@NonNull RiskClassificationDao dao) {
        this.dao = dao;
    }

    /** Expose the underlying DAO for use by ClassificationTrendAnalyzer. */
    @NonNull
    public RiskClassificationDao getDao() {
        return this.dao;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3.H.2 — SAVE
    // ═══════════════════════════════════════════════════════════════════

    /** Persist a classification (insert or replace by dayTimestamp). */
    public long save(@NonNull RiskClassification rc) {
        try {
            long id = dao.insertOrReplace(rc);
            Log.d(TAG, "Saved classification: overall=" +
                    String.format("%.2f", rc.overallRiskScore) + " id=" + id);
            return id;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save classification", e);
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3.H.3 — LATEST
    // ═══════════════════════════════════════════════════════════════════

    /** Get the most recent classification (sync). */
    @Nullable
    public RiskClassification getLatest() {
        try { return dao.getLatestSync(); }
        catch (Exception e) { return null; }
    }

    /** Observe the most recent classification (LiveData). */
    @NonNull
    public LiveData<RiskClassification> observeLatest() {
        return dao.getLatest();
    }

    /** Get classification for a specific day. */
    @Nullable
    public RiskClassification getForDay(long dayTimestamp) {
        try { return dao.getForDay(dayTimestamp); }
        catch (Exception e) { return null; }
    }

    /** Get today's classification. */
    @Nullable
    public RiskClassification getToday() {
        return getForDay(getStartOfTodayMs());
    }

    /** Observe today's classification. */
    @NonNull
    public LiveData<RiskClassification> observeToday() {
        return dao.observeForDay(getStartOfTodayMs());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3.H.4 — HISTORY
    // ═══════════════════════════════════════════════════════════════════

    /** Get classification history for the last N days. */
    @NonNull
    public List<RiskClassification> getHistory(int lastNDays) {
        long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
        try {
            List<RiskClassification> result = dao.getHistorySince(since);
            return result != null ? result : java.util.Collections.emptyList();
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /** Get last N classifications regardless of date. */
    @NonNull
    public List<RiskClassification> getRecentClassifications(int limit) {
        try {
            List<RiskClassification> result = dao.getHistory(limit);
            return result != null ? result : java.util.Collections.emptyList();
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /** Observe classification history as LiveData. */
    @NonNull
    public LiveData<List<RiskClassification>> observeHistory(int lastNDays) {
        long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
        return dao.observeTrendSince(since);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3.H.5 — TREND ANALYSIS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get the trend slope for a specific risk category over the last N days.
     * Returns: positive = worsening, negative = improving, 0 = stable/insufficient data.
     * Uses linear regression for robust trend estimation.
     */
    public float getTrendForCategory(@NonNull String category, int lastNDays) {
        List<RiskClassification> history = getHistory(lastNDays);
        if (history.size() < 3) return 0f;

        int n = history.size();
        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            float x = i;
            float y = history.get(i).getScoreForCategory(category);
            sumX += x; sumY += y; sumXY += x * y; sumX2 += x * x;
        }
        float denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 0.001f) return 0f;
        return (n * sumXY - sumX * sumY) / denom;
    }

    /**
     * Get the overall risk trend over last N days.
     */
    public float getOverallTrend(int lastNDays) {
        return getTrendForCategory("overall", lastNDays);
    }

    /**
     * Get a human-readable trend label.
     */
    @NonNull
    public String getTrendLabel(float trendSlope) {
        if (trendSlope > 0.10f) return "rapidly_worsening";
        if (trendSlope > 0.05f) return "worsening";
        if (trendSlope < -0.10f) return "rapidly_improving";
        if (trendSlope < -0.05f) return "improving";
        return "stable";
    }

    /**
     * Get the average risk score for a category over last N days.
     */
    public float getAverageForCategory(@NonNull String category, int lastNDays) {
        long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
        try {
            switch (category) {
                case "digital_addiction": return dao.getAverageDigitalAddictionSince(since);
                case "stress_anxiety":   return dao.getAverageStressSince(since);
                case "depression":       return dao.getAverageDepressionSince(since);
                case "social_isolation": return dao.getAverageIsolationSince(since);
                case "sleep_disruption": return dao.getAverageSleepDisruptionSince(since);
                case "low_fulfilment":   return dao.getAverageFulfilmentSince(since);
                default:                 return dao.getAverageRiskSince(since);
            }
        } catch (Exception e) { return 0f; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3.H.6 — STALENESS CHECK
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if the latest classification is stale (older than maxAgeHours).
     * @param maxAgeHours Maximum age in hours before classification is stale
     * @return true if stale or no classification exists
     */
    public boolean isStale(int maxAgeHours) {
        RiskClassification latest = getLatest();
        if (latest == null) return true;
        long ageMs = System.currentTimeMillis() - latest.timestamp;
        return ageMs > (maxAgeHours * HOUR_MS);
    }

    /**
     * Check if today already has a classification.
     */
    public boolean hasClassificationToday() {
        return getToday() != null;
    }

    /**
     * Check if a reclassification is needed.
     * Returns true if: no classification today, OR latest is >12h old.
     */
    public boolean needsReclassification() {
        if (!hasClassificationToday()) return true;
        return isStale(12);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: CRISIS & HIGH-RISK
    // ═══════════════════════════════════════════════════════════════════

    /** Get all crisis events in the last N days. */
    @NonNull
    public List<RiskClassification> getCrisisHistory(int lastNDays) {
        try {
            long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
            int count = dao.getCrisisCountSince(since);
            if (count == 0) return java.util.Collections.emptyList();
            return dao.getCrisisEvents(count);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /** Count of high-risk days (overall >= threshold) in last N days. */
    public int getHighRiskDayCount(int lastNDays, float threshold) {
        long since = System.currentTimeMillis() - (lastNDays * DAY_MS);
        try { return dao.getHighRiskDayCountSince(threshold, since); }
        catch (Exception e) { return 0; }
    }

    /** Get the user's best and worst classification days. */
    @Nullable
    public RiskClassification getPeakRiskDay() {
        try { return dao.getPeakRiskDay(); } catch (Exception e) { return null; }
    }

    @Nullable
    public RiskClassification getBestDay() {
        try { return dao.getBestDay(); } catch (Exception e) { return null; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: WEEKLY SUMMARY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute a weekly risk summary with category averages and trend.
     */
    @NonNull
    public WeeklyRiskSummary getWeeklySummary() {
        List<RiskClassification> week = getHistory(7);
        WeeklyRiskSummary summary = new WeeklyRiskSummary();

        if (week.isEmpty()) return summary;

        float totalOverall = 0, totalDigital = 0, totalStress = 0;
        float totalDepression = 0, totalIsolation = 0, totalSleep = 0, totalFulfilment = 0;
        int crisisCount = 0;
        float peakRisk = 0;
        String peakCategory = null;

        for (RiskClassification rc : week) {
            totalOverall += rc.overallRiskScore;
            totalDigital += rc.digitalAddictionScore;
            totalStress += rc.stressAnxietyScore;
            totalDepression += rc.depressionRiskScore;
            totalIsolation += rc.socialIsolationScore;
            totalSleep += rc.sleepDisruptionScore;
            totalFulfilment += rc.lowFulfilmentScore;
            if (rc.crisisFlag) crisisCount++;
            if (rc.overallRiskScore > peakRisk) {
                peakRisk = rc.overallRiskScore;
                peakCategory = rc.primaryCategory;
            }
        }

        int n = week.size();
        summary.avgOverallRisk = totalOverall / n;
        summary.avgDigitalAddiction = totalDigital / n;
        summary.avgStressAnxiety = totalStress / n;
        summary.avgDepression = totalDepression / n;
        summary.avgSocialIsolation = totalIsolation / n;
        summary.avgSleepDisruption = totalSleep / n;
        summary.avgLowFulfilment = totalFulfilment / n;
        summary.crisisDayCount = crisisCount;
        summary.classifiedDayCount = n;
        summary.overallTrend = getTrendForCategory("overall", 7);
        summary.peakRiskScore = peakRisk;
        summary.peakCategory = peakCategory;

        return summary;
    }

    /** Weekly summary data class. */
    public static class WeeklyRiskSummary {
        public float avgOverallRisk;
        public float avgDigitalAddiction;
        public float avgStressAnxiety;
        public float avgDepression;
        public float avgSocialIsolation;
        public float avgSleepDisruption;
        public float avgLowFulfilment;
        public int crisisDayCount;
        public int classifiedDayCount;
        public float overallTrend;
        public float peakRiskScore;
        public String peakCategory;

        public RiskClassification.Severity getOverallSeverity() {
            return RiskClassification.Severity.fromScore(avgOverallRisk);
        }

        public boolean isWeekConcerning() {
            return avgOverallRisk > 0.5f || crisisDayCount > 0;
        }

        /** Get the worst category name for the week. */
        @NonNull
        public String getWorstCategory() {
            float max = avgDigitalAddiction;
            String cat = "digital_addiction";
            if (avgStressAnxiety > max) { max = avgStressAnxiety; cat = "stress_anxiety"; }
            if (avgDepression > max) { max = avgDepression; cat = "depression"; }
            if (avgSocialIsolation > max) { max = avgSocialIsolation; cat = "social_isolation"; }
            if (avgSleepDisruption > max) { max = avgSleepDisruption; cat = "sleep_disruption"; }
            if (avgLowFulfilment > max) { cat = "low_fulfilment"; }
            return cat;
        }

        /** Generate a human-readable weekly narrative. */
        @NonNull
        public String generateNarrative() {
            if (classifiedDayCount == 0) return "Not enough data for a weekly summary yet.";
            StringBuilder sb = new StringBuilder();
            sb.append("Over ").append(classifiedDayCount).append(" day")
                    .append(classifiedDayCount > 1 ? "s" : "").append(", ");

            RiskClassification.Severity sev = getOverallSeverity();
            if (sev.level <= 1) {
                sb.append("your overall wellbeing has been positive. ");
            } else if (sev.level <= 3) {
                sb.append("some areas need attention. ");
            } else {
                sb.append("significant challenges are present. ");
            }

            String worst = getWorstCategory();
            sb.append("Your biggest area of concern: ")
                    .append(worst.replace("_", " ")).append(". ");

            if (crisisDayCount > 0) {
                sb.append("⚠ ").append(crisisDayCount).append(" crisis day")
                        .append(crisisDayCount > 1 ? "s" : "").append(" detected. ");
            }

            if (overallTrend > 0.05f) sb.append("The trend is worsening.");
            else if (overallTrend < -0.05f) sb.append("Encouragingly, the trend is improving.");
            else sb.append("The trend is stable.");

            return sb.toString();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: PROGRESS REPORT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a structured progress report comparing two time periods.
     *
     * <p>The previous period average is computed exclusively — it does NOT
     * overlap with the current period. We achieve this by querying the
     * combined window and subtracting the current period's contribution.</p>
     */
    @NonNull
    public ProgressReport getProgressReport(int currentPeriodDays, int previousPeriodDays) {
        float currentAvg = getAverageForCategory("overall", currentPeriodDays);

        // Compute the exclusive previous-period average by removing
        // the current period's contribution from the combined average.
        int totalDays = currentPeriodDays + previousPeriodDays;
        float combinedAvg = getAverageForCategory("overall", totalDays);
        float previousAvg;
        if (previousPeriodDays > 0) {
            // previousAvg = (combinedAvg * totalDays - currentAvg * currentDays) / previousDays
            previousAvg = (combinedAvg * totalDays - currentAvg * currentPeriodDays) / previousPeriodDays;
            previousAvg = Math.max(0f, previousAvg); // Guard against negative from rounding
        } else {
            previousAvg = combinedAvg;
        }

        float delta = currentAvg - previousAvg;

        ProgressReport report = new ProgressReport();
        report.currentPeriodAvg = currentAvg;
        report.previousPeriodAvg = previousAvg;
        report.delta = delta;
        report.improvementPercent = previousAvg > 0.01f
                ? ((previousAvg - currentAvg) / previousAvg) * 100f : 0f;

        if (delta < -0.08f) report.verdict = "significant_improvement";
        else if (delta < -0.03f) report.verdict = "modest_improvement";
        else if (delta > 0.08f) report.verdict = "significant_regression";
        else if (delta > 0.03f) report.verdict = "modest_regression";
        else report.verdict = "stable";

        return report;
    }

    /** Progress report data class. */
    public static class ProgressReport {
        public float currentPeriodAvg;
        public float previousPeriodAvg;
        public float delta;
        public float improvementPercent;
        public String verdict;

        @NonNull
        public String getSummary() {
            if ("significant_improvement".equals(verdict))
                return String.format(java.util.Locale.US,
                        "Your risk improved by %.0f%% — significant progress.", Math.abs(improvementPercent));
            if ("modest_improvement".equals(verdict))
                return String.format(java.util.Locale.US,
                        "Modest improvement of %.0f%% — keep the momentum.", Math.abs(improvementPercent));
            if ("significant_regression".equals(verdict))
                return "Risk has increased significantly. Consider adjusting your routine.";
            if ("modest_regression".equals(verdict))
                return "Slight uptick in risk. Stay aware and course-correct early.";
            return "Risk has been stable. Consistency is key.";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: DAY-OVER-DAY COMPARISON
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compare today vs yesterday for quick insight.
     */
    @NonNull
    public DayComparison getDayOverDayComparison() {
        DayComparison comp = new DayComparison();
        List<RiskClassification> recent = getRecentClassifications(2);
        if (recent.size() < 2) {
            comp.hasPreviousDay = false;
            return comp;
        }
        comp.hasPreviousDay = true;
        comp.todayRisk = recent.get(0).overallRiskScore;
        comp.yesterdayRisk = recent.get(1).overallRiskScore;
        comp.delta = comp.todayRisk - comp.yesterdayRisk;
        comp.todayPrimary = recent.get(0).primaryCategory;
        comp.yesterdayPrimary = recent.get(1).primaryCategory;
        comp.categoryShifted = !String.valueOf(comp.todayPrimary)
                .equals(String.valueOf(comp.yesterdayPrimary));
        return comp;
    }

    /** Day-over-day comparison data class. */
    public static class DayComparison {
        public boolean hasPreviousDay;
        public float todayRisk;
        public float yesterdayRisk;
        public float delta;
        public String todayPrimary;
        public String yesterdayPrimary;
        public boolean categoryShifted;

        @NonNull
        public String getSummary() {
            if (!hasPreviousDay) return "Not enough data for comparison.";
            if (Math.abs(delta) < 0.03f) return "Similar to yesterday.";
            if (delta > 0) return String.format(java.util.Locale.US,
                    "Risk is %.0f%% higher than yesterday.", delta * 100);
            return String.format(java.util.Locale.US,
                    "Risk is %.0f%% lower than yesterday.", Math.abs(delta) * 100);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /** Get total number of classifications ever recorded. */
    public int getTotalCount() {
        try { return dao.getTotalClassificationCount(); }
        catch (Exception e) { return 0; }
    }

    /** Delete classifications older than N days (maintenance). */
    public void purgeOlderThan(int days) {
        long before = System.currentTimeMillis() - (days * DAY_MS);
        try { dao.deleteOlderThan(before); }
        catch (Exception e) { Log.w(TAG, "Purge failed", e); }
    }

    private static long getStartOfTodayMs() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
