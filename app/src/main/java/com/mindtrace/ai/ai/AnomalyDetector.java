package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.UserBaseline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Statistical anomaly detector — identifies deviations from the user's
 * personal baseline using Z-score analysis.
 *
 * <p>Detects anomalies across 6 metrics: screen time, sleep, stress,
 * task completion, unlock count, and passive consumption ratio.</p>
 *
 * <h3>Classification Integration (3.J.6):</h3>
 * <p>The anomaly count directly feeds into the MultiModalClassifier's
 * confidence scoring — more anomalies = lower baseline reliability.</p>
 */
public class AnomalyDetector {

    // ═══════════════════════════════════════════════════════════════════
    // CORE Z-SCORE DETECTION
    // ═══════════════════════════════════════════════════════════════════

    public AnomalyResult detectAnomaly(double todayValue, double mean, double std) {
        if (std <= 0.0001d) {
            double fallbackDelta = Math.abs(todayValue - mean);
            if (mean <= 0d || fallbackDelta < Math.max(1d, mean * 0.1d)) {
                return new AnomalyResult(0d, Level.NORMAL, "normal");
            }
            return new AnomalyResult(1.5d, Level.MILD_DEVIATION, "mild deviation");
        }

        double zScore = (todayValue - mean) / std;
        double absZ = Math.abs(zScore);
        if (absZ < 1d) {
            return new AnomalyResult(zScore, Level.NORMAL, "normal");
        }
        if (absZ < 2d) {
            return new AnomalyResult(zScore, Level.MILD_DEVIATION, "mild deviation");
        }
        return new AnomalyResult(zScore, Level.ANOMALY, "anomaly");
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROFILE BUILDER — Expanded with unlock + passive ratio (3.J.2–3.J.3)
    // ═══════════════════════════════════════════════════════════════════

    public AnomalyProfile buildProfile(
            Long currentScreenTime,
            List<DailyUsage> usageHistory,
            QuestionnaireResponse latestResponse,
            List<QuestionnaireResponse> responses,
            int taskCompletionRate,
            List<InterventionTask> tasks,
            UserBaseline baseline
    ) {
        // Means from baseline or computed from history
        double screenMean = baseline != null && baseline.avgScreenTime7d > 0d
                ? baseline.avgScreenTime7d : averageScreenTime(usageHistory);
        double sleepMean = baseline != null && baseline.avgSleep7d > 0d
                ? baseline.avgSleep7d : averageSleep(responses);
        double stressMean = baseline != null && baseline.avgStress7d > 0d
                ? baseline.avgStress7d : averageStress(responses);
        double taskMean = baseline != null && baseline.avgTaskCompletion7d > 0d
                ? baseline.avgTaskCompletion7d : averageTaskOutcome(tasks);
        double unlockMean = baseline != null && baseline.avgUnlocks7d > 0d
                ? baseline.avgUnlocks7d : averageUnlocks(usageHistory);
        double passiveMean = baseline != null && baseline.avgPassiveRatio7d > 0d
                ? baseline.avgPassiveRatio7d : averagePassiveRatio(usageHistory);

        // Std devs from baseline or computed
        double screenStd = baseline != null && baseline.stdScreenTime7d > 0d
                ? baseline.stdScreenTime7d : stdDevScreenTime(usageHistory, screenMean);
        double unlockStd = baseline != null && baseline.stdUnlocks7d > 0d
                ? baseline.stdUnlocks7d : stdDevUnlocks(usageHistory, unlockMean);

        DailyUsage latestUsage = usageHistory != null && !usageHistory.isEmpty()
                ? usageHistory.get(0) : null;

        // Detect anomalies for all 6 metrics
        AnomalyResult screenTime = detectAnomaly(
                currentScreenTime == null ? 0d : currentScreenTime, screenMean, screenStd);
        AnomalyResult sleep = detectAnomaly(
                latestResponse == null ? 0d : latestResponse.sleepHours,
                sleepMean, stdDevSleep(responses, sleepMean));
        AnomalyResult stress = detectAnomaly(
                latestResponse == null ? 0d : latestResponse.stressLevel,
                stressMean, stdDevStress(responses, stressMean));
        AnomalyResult taskCompletion = detectAnomaly(
                taskCompletionRate, taskMean, stdDevTaskOutcome(tasks, taskMean));

        // 3.J.2: Unlock count anomaly
        AnomalyResult unlocks = detectAnomaly(
                latestUsage != null ? latestUsage.unlockCount : 0d,
                unlockMean, unlockStd);

        // 3.J.3: Passive ratio anomaly
        AnomalyResult passiveRatio = detectAnomaly(
                latestUsage != null ? latestUsage.passiveConsumptionRatio : 0d,
                passiveMean, stdDevPassiveRatio(usageHistory, passiveMean));

        // Build anomaly list and risk points
        List<Anomaly> anomalies = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        int riskPoints = 0;

        if (screenTime.level != Level.NORMAL && screenTime.zScore > 0d) {
            anomalies.add(new Anomaly("screen_time", screenTime.zScore, screenTime.level,
                    "Screen time is " + deviationLabel(screenTime) + " your baseline"));
            issues.add(formatIssue("Screen time", screenTime));
            riskPoints += screenTime.level == Level.ANOMALY ? 2 : 1;
        }
        if (sleep.level != Level.NORMAL && sleep.zScore < 0d) {
            anomalies.add(new Anomaly("sleep", sleep.zScore, sleep.level,
                    "Sleep is " + deviationLabel(sleep) + " below your average"));
            issues.add(formatIssue("Sleep", sleep));
            riskPoints += sleep.level == Level.ANOMALY ? 2 : 1;
        }
        if (stress.level != Level.NORMAL && stress.zScore > 0d) {
            anomalies.add(new Anomaly("stress", stress.zScore, stress.level,
                    "Stress is " + deviationLabel(stress) + " your baseline"));
            issues.add(formatIssue("Stress", stress));
            riskPoints += stress.level == Level.ANOMALY ? 2 : 1;
        }
        if (taskCompletion.level != Level.NORMAL && taskCompletion.zScore < 0d) {
            anomalies.add(new Anomaly("task_completion", taskCompletion.zScore, taskCompletion.level,
                    "Task completion dropped " + deviationLabel(taskCompletion) + " below average"));
            issues.add(formatIssue("Task completion", taskCompletion));
            riskPoints += taskCompletion.level == Level.ANOMALY ? 2 : 1;
        }
        if (unlocks.level != Level.NORMAL && unlocks.zScore > 0d) {
            anomalies.add(new Anomaly("unlock_count", unlocks.zScore, unlocks.level,
                    "Phone unlocks are " + deviationLabel(unlocks) + " your average"));
            issues.add(formatIssue("Unlocks", unlocks));
            riskPoints += unlocks.level == Level.ANOMALY ? 2 : 1;
        }
        if (passiveRatio.level != Level.NORMAL && passiveRatio.zScore > 0d) {
            anomalies.add(new Anomaly("passive_ratio", passiveRatio.zScore, passiveRatio.level,
                    "Passive consumption is " + deviationLabel(passiveRatio) + " your norm"));
            issues.add(formatIssue("Passive ratio", passiveRatio));
            riskPoints += passiveRatio.level == Level.ANOMALY ? 2 : 1;
        }

        // Build report and profile
        AnomalyReport report = new AnomalyReport(anomalies, riskPoints);
        String summary = generateSummaryText(report, issues);

        return new AnomalyProfile(screenTime, sleep, stress, taskCompletion,
                unlocks, passiveRatio, issues, summary, riskPoints, report);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3.J.6 — CLASSIFICATION CONFIDENCE MODIFIER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute a confidence modifier based on anomaly severity.
     * High anomaly count means data is unusual → reduce classifier confidence.
     * @return Multiplier between 0.7 (many anomalies) and 1.0 (no anomalies)
     */
    public float classificationConfidenceModifier(@Nullable AnomalyProfile profile) {
        if (profile == null || profile.report == null) return 1.0f;
        int count = profile.report.anomalyCount();
        int severe = profile.report.severeCount();

        // Each anomaly reduces confidence slightly
        float modifier = 1.0f - (count * 0.05f) - (severe * 0.05f);
        return Math.max(0.7f, modifier);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3.J.7 — SUMMARY TEXT GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate human-readable anomaly summary for UI display.
     */
    @NonNull
    private String generateSummaryText(@NonNull AnomalyReport report, @NonNull List<String> issues) {
        if (report.anomalies.isEmpty()) {
            return "Anomaly scan: today's signals are close to your recent pattern.";
        }

        int total = report.anomalyCount();
        int severe = report.severeCount();

        StringBuilder sb = new StringBuilder();
        if (severe > 0) {
            sb.append("⚠ ").append(severe).append(" significant deviation")
                    .append(severe > 1 ? "s" : "").append(" detected. ");
        } else {
            sb.append(total).append(" mild deviation")
                    .append(total > 1 ? "s" : "").append(" from your baseline. ");
        }

        // Append the most important anomaly description
        if (!report.anomalies.isEmpty()) {
            sb.append(report.anomalies.get(0).description).append(".");
        }

        return sb.toString();
    }

    /**
     * Get a full multi-line anomaly report for detailed UI display.
     */
    @NonNull
    public String generateDetailedReport(@Nullable AnomalyProfile profile) {
        if (profile == null || profile.report == null || profile.report.anomalies.isEmpty()) {
            return "No anomalies detected today. All metrics are within your normal range.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Anomaly Report (").append(profile.report.anomalyCount())
                .append(" deviation").append(profile.report.anomalyCount() > 1 ? "s" : "")
                .append("):\n\n");

        for (Anomaly a : profile.report.anomalies) {
            String icon = a.severity == Level.ANOMALY ? "🔴" : "🟡";
            sb.append(icon).append(" ").append(a.description)
                    .append(" (z=").append(String.format(Locale.US, "%.1f", a.zScore))
                    .append(")\n");
        }

        sb.append("\nOverall status: ").append(profile.report.getStatus());
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private String formatIssue(String label, AnomalyResult result) {
        return String.format(Locale.US, "%s shows a %s (z=%.2f)", label, result.label, result.zScore);
    }

    private String deviationLabel(AnomalyResult r) {
        double absZ = Math.abs(r.zScore);
        if (absZ >= 3d) return "dramatically above";
        if (absZ >= 2d) return "significantly above";
        return "moderately above";
    }

    private String joinIssues(List<String> issues) {
        StringBuilder builder = new StringBuilder();
        for (String issue : issues) {
            if (builder.length() > 0) builder.append(" | ");
            builder.append(issue);
        }
        return builder.toString();
    }

    // ── Average helpers ──

    private double averageScreenTime(List<DailyUsage> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d; int count = 0;
        for (DailyUsage u : list) { total += u.screenTimeMillis; count++; }
        return count == 0 ? 0d : total / count;
    }

    private double averageSleep(List<QuestionnaireResponse> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d; int count = 0;
        for (QuestionnaireResponse r : list) {
            if (r.sleepHours > 0f) { total += r.sleepHours; count++; }
        }
        return count == 0 ? 0d : total / count;
    }

    private double averageStress(List<QuestionnaireResponse> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d; int count = 0;
        for (QuestionnaireResponse r : list) {
            if (r.stressLevel > 0) { total += r.stressLevel; count++; }
        }
        return count == 0 ? 0d : total / count;
    }

    private double averageTaskOutcome(List<InterventionTask> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d; int count = 0;
        for (InterventionTask t : list) { total += t.isCompleted ? 100d : 0d; count++; }
        return count == 0 ? 0d : total / count;
    }

    private double averageUnlocks(List<DailyUsage> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d; int count = 0;
        for (DailyUsage u : list) { total += u.unlockCount; count++; }
        return count == 0 ? 0d : total / count;
    }

    private double averagePassiveRatio(List<DailyUsage> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d; int count = 0;
        for (DailyUsage u : list) { total += u.passiveConsumptionRatio; count++; }
        return count == 0 ? 0d : total / count;
    }

    // ── StdDev helpers ──

    private double stdDevScreenTime(List<DailyUsage> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double sum = 0d; int count = 0;
        for (DailyUsage u : list) { sum += Math.pow(u.screenTimeMillis - mean, 2d); count++; }
        return count < 2 ? 0d : Math.sqrt(sum / count);
    }

    private double stdDevSleep(List<QuestionnaireResponse> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double sum = 0d; int count = 0;
        for (QuestionnaireResponse r : list) {
            if (r.sleepHours > 0f) { sum += Math.pow(r.sleepHours - mean, 2d); count++; }
        }
        return count < 2 ? 0d : Math.sqrt(sum / count);
    }

    private double stdDevStress(List<QuestionnaireResponse> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double sum = 0d; int count = 0;
        for (QuestionnaireResponse r : list) {
            if (r.stressLevel > 0) { sum += Math.pow(r.stressLevel - mean, 2d); count++; }
        }
        return count < 2 ? 0d : Math.sqrt(sum / count);
    }

    private double stdDevTaskOutcome(List<InterventionTask> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double sum = 0d; int count = 0;
        for (InterventionTask t : list) {
            double v = t.isCompleted ? 100d : 0d; sum += Math.pow(v - mean, 2d); count++;
        }
        return count < 2 ? 0d : Math.sqrt(sum / count);
    }

    private double stdDevUnlocks(List<DailyUsage> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double sum = 0d; int count = 0;
        for (DailyUsage u : list) { sum += Math.pow(u.unlockCount - mean, 2d); count++; }
        return count < 2 ? 0d : Math.sqrt(sum / count);
    }

    private double stdDevPassiveRatio(List<DailyUsage> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double sum = 0d; int count = 0;
        for (DailyUsage u : list) {
            sum += Math.pow(u.passiveConsumptionRatio - mean, 2d); count++;
        }
        return count < 2 ? 0d : Math.sqrt(sum / count);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════

    public enum Level {
        NORMAL,
        MILD_DEVIATION,
        ANOMALY
    }

    public static class AnomalyResult {
        public final double zScore;
        public final Level level;
        public final String label;

        public AnomalyResult(double zScore, Level level, String label) {
            this.zScore = zScore;
            this.level = level;
            this.label = label;
        }
    }

    /** 3.J.5 — Individual anomaly with metric, z-score, severity, and description. */
    public static class Anomaly {
        public final String metric;
        public final double zScore;
        public final Level severity;
        public final String description;

        public Anomaly(String metric, double zScore, Level severity, String description) {
            this.metric = metric;
            this.zScore = zScore;
            this.severity = severity;
            this.description = description;
        }
    }

    /** 3.J.4 — Full anomaly report: list of anomalies + aggregate status. */
    public static class AnomalyReport {
        public final List<Anomaly> anomalies;
        public final int riskPoints;

        public AnomalyReport(List<Anomaly> anomalies, int riskPoints) {
            this.anomalies = anomalies != null ? anomalies : new ArrayList<>();
            this.riskPoints = riskPoints;
        }

        public int anomalyCount() { return anomalies.size(); }

        public int severeCount() {
            int c = 0;
            for (Anomaly a : anomalies) if (a.severity == Level.ANOMALY) c++;
            return c;
        }

        public String getStatus() {
            if (anomalies.isEmpty()) return "NORMAL";
            if (severeCount() > 0) return "ALERT";
            return "WATCH";
        }

        public boolean hasAnomaly(String metric) {
            for (Anomaly a : anomalies) if (metric.equals(a.metric)) return true;
            return false;
        }
    }

    /** Expanded AnomalyProfile with unlock + passive ratio results + report. */
    public static class AnomalyProfile {
        public final AnomalyResult screenTime;
        public final AnomalyResult sleep;
        public final AnomalyResult stress;
        public final AnomalyResult taskCompletion;
        public final AnomalyResult unlocks;
        public final AnomalyResult passiveRatio;
        public final List<String> issues;
        public final String summary;
        public final int riskPoints;
        public final AnomalyReport report;

        public AnomalyProfile(
                AnomalyResult screenTime, AnomalyResult sleep,
                AnomalyResult stress, AnomalyResult taskCompletion,
                AnomalyResult unlocks, AnomalyResult passiveRatio,
                List<String> issues, String summary, int riskPoints,
                AnomalyReport report
        ) {
            this.screenTime = screenTime;
            this.sleep = sleep;
            this.stress = stress;
            this.taskCompletion = taskCompletion;
            this.unlocks = unlocks;
            this.passiveRatio = passiveRatio;
            this.issues = issues;
            this.summary = summary;
            this.riskPoints = riskPoints;
            this.report = report;
        }

        // Backward compatibility constructor (without unlock/passive/report)
        public AnomalyProfile(
                AnomalyResult screenTime, AnomalyResult sleep,
                AnomalyResult stress, AnomalyResult taskCompletion,
                List<String> issues, String summary, int riskPoints
        ) {
            this(screenTime, sleep, stress, taskCompletion,
                    new AnomalyResult(0d, Level.NORMAL, "normal"),
                    new AnomalyResult(0d, Level.NORMAL, "normal"),
                    issues, summary, riskPoints,
                    new AnomalyReport(new ArrayList<>(), riskPoints));
        }
    }
}
