package com.mindtrace.ai.repository;

import android.util.Log;

import com.mindtrace.ai.util.MoodMapper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.dao.UsageDao;
import com.mindtrace.ai.database.dao.UserBaselineDao;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.UserBaseline;

import java.util.Calendar;
import java.util.List;

/**
 * Computes and maintains the user's personal behavioural baseline.
 *
 * <p>The baseline captures rolling averages AND standard deviations for key
 * metrics. This enables the AI pipeline to detect anomalies relative to the
 * user's own patterns rather than population-level thresholds.</p>
 *
 * <h3>Key Concepts:</h3>
 * <ul>
 *   <li><b>Z-Score Detection:</b> A day with screen time 2+ std devs above
 *       baseline is flagged as anomalous even if the absolute value is "normal"</li>
 *   <li><b>Baseline Lifecycle:</b> INSUFFICIENT → CALIBRATING → READY</li>
 *   <li><b>Lazy Recomputation:</b> Only recomputes if baseline is outdated (stale)</li>
 * </ul>
 */
public class BaselineManager {

    private static final String TAG = "BaselineMgr";
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    /** Minimum days required for CALIBRATING status. */
    private static final int MIN_DAYS_CALIBRATING = 3;
    /** Minimum days required for READY status. */
    private static final int MIN_DAYS_READY = 7;

    private final UsageDao usageDao;
    private final QuestionnaireDao questionnaireDao;
    private final TaskDao taskDao;
    private final UserBaselineDao userBaselineDao;

    public BaselineManager(AppDatabase database) {
        usageDao = database.usageDao();
        questionnaireDao = database.questionnaireDao();
        taskDao = database.taskDao();
        userBaselineDao = database.userBaselineDao();
    }

    public LiveData<UserBaseline> getBaseline() {
        return userBaselineDao.getBaseline();
    }

    public UserBaseline getBaselineSync() {
        return userBaselineDao.getBaselineSync();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORE COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Full baseline recomputation — averages, std devs, and status.
     */
    public UserBaseline computeUserBaseline() {
        long now = System.currentTimeMillis();
        long since7Days = now - (7L * DAY_MILLIS);
        long since30Days = now - (30L * DAY_MILLIS);

        List<DailyUsage> usage7d = usageDao.getUsageSince(since7Days);
        List<DailyUsage> usage30d = usageDao.getUsageSince(since30Days);
        List<QuestionnaireResponse> responses7d = questionnaireDao.getResponsesSinceSync(since7Days);
        List<InterventionTask> tasks7d = taskDao.getTasksSinceSync(since7Days);

        UserBaseline baseline = userBaselineDao.getBaselineSync();
        if (baseline == null) {
            baseline = new UserBaseline();
        }

        // ── Averages ──
        baseline.avgScreenTime7d = averageScreenTime(usage7d);
        baseline.avgScreenTime30d = averageScreenTime(usage30d);
        baseline.avgSleep7d = averageSleep(responses7d);
        baseline.avgStress7d = averageStress(responses7d);
        baseline.avgMoodScore7d = averageMoodScore(responses7d);
        baseline.avgTaskCompletion7d = averageTaskCompletion(tasks7d);
        baseline.avgAppSwitches7d = averageAppSwitches(usage7d);
        baseline.avgNightUsageMinutes7d = averageNightUsage(usage7d);
        baseline.avgUnlocks7d = averageUnlocks(usage7d);
        baseline.avgLaunches7d = averageLaunches(usage7d);
        baseline.avgPassiveRatio7d = averagePassiveRatio(usage7d);

        // ── Standard Deviations ──
        baseline.stdScreenTime7d = stdDevScreenTime(usage7d, baseline.avgScreenTime7d);
        baseline.stdAppSwitches7d = stdDevAppSwitches(usage7d, baseline.avgAppSwitches7d);
        baseline.stdNightUsage7d = stdDevNightUsage(usage7d, baseline.avgNightUsageMinutes7d);
        baseline.stdUnlocks7d = stdDevUnlocks(usage7d, baseline.avgUnlocks7d);
        baseline.stdLaunches7d = stdDevLaunches(usage7d, baseline.avgLaunches7d);
        baseline.stdSleep7d = stdDevSleep(responses7d, baseline.avgSleep7d);
        baseline.stdStress7d = stdDevStress(responses7d, baseline.avgStress7d);
        baseline.stdMoodScore7d = stdDevMoodScore(responses7d, baseline.avgMoodScore7d);

        // ── Metadata ──
        int usageDays = usage7d != null ? usage7d.size() : 0;
        baseline.dataPointCount = usageDays;
        baseline.baselineStatus = computeStatus(usageDays);
        baseline.lastUpdated = now;

        userBaselineDao.insertOrUpdateBaseline(baseline);
        Log.d(TAG, "Baseline computed: " + baseline);
        return baseline;
    }

    /**
     * Only recompute if baseline is outdated (not updated today).
     */
    public UserBaseline computeUserBaselineIfOutdated() {
        UserBaseline baseline = userBaselineDao.getBaselineSync();
        if (baseline != null && isSameDay(baseline.lastUpdated, System.currentTimeMillis())) {
            return baseline;
        }
        return computeUserBaseline();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANOMALY DETECTION — Beyond the task list
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if today's usage is anomalous compared to baseline.
     * Returns true if any metric exceeds 2 standard deviations.
     */
    public boolean isAnomalousDay(@NonNull DailyUsage today) {
        UserBaseline b = getBaselineSync();
        if (b == null || !b.isReady()) return false;

        return b.screenTimeZScore(today.screenTimeMillis) > 2.0f
                || b.unlockZScore(today.unlockCount) > 2.0f
                || b.appSwitchZScore(today.totalAppSwitchCount) > 2.0f;
    }

    /**
     * Get a deviation summary for a given day.
     * Returns a human-readable string, or null if within normal range.
     */
    public String getDeviationSummary(@NonNull DailyUsage today) {
        UserBaseline b = getBaselineSync();
        if (b == null || !b.isReady()) return null;

        StringBuilder sb = new StringBuilder();
        float stZ = b.screenTimeZScore(today.screenTimeMillis);
        float ulZ = b.unlockZScore(today.unlockCount);
        float asZ = b.appSwitchZScore(today.totalAppSwitchCount);

        if (stZ > 2.0f) sb.append("Screen time is ").append(String.format("%.0f%%", (stZ - 1) * 100))
                .append(" above your baseline. ");
        if (ulZ > 2.0f) sb.append("Unlocks are significantly higher than usual. ");
        if (asZ > 2.0f) sb.append("App switching is unusually high. ");

        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // AVERAGE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private double averageScreenTime(List<DailyUsage> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d;
        for (DailyUsage u : list) total += Math.max(u.screenTimeMillis, 0L);
        return total / list.size();
    }

    private double averageAppSwitches(List<DailyUsage> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d;
        for (DailyUsage u : list) total += u.totalAppSwitchCount;
        return total / list.size();
    }

    private double averageNightUsage(List<DailyUsage> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d;
        for (DailyUsage u : list) total += u.getNightUsageMinutes();
        return total / list.size();
    }

    private double averageUnlocks(List<DailyUsage> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d;
        for (DailyUsage u : list) total += u.unlockCount;
        return total / list.size();
    }

    private double averageLaunches(List<DailyUsage> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d;
        for (DailyUsage u : list) total += u.totalLaunchCount;
        return total / list.size();
    }

    private double averagePassiveRatio(List<DailyUsage> list) {
        if (list == null || list.isEmpty()) return 0d;
        double total = 0d;
        for (DailyUsage u : list) total += u.passiveConsumptionRatio;
        return total / list.size();
    }

    private double averageSleep(List<QuestionnaireResponse> responses) {
        if (responses == null || responses.isEmpty()) return 0d;
        double total = 0d; int count = 0;
        for (QuestionnaireResponse r : responses) {
            if (r.sleepHours > 0f) { total += r.sleepHours; count++; }
        }
        return count == 0 ? 0d : total / count;
    }

    private double averageStress(List<QuestionnaireResponse> responses) {
        if (responses == null || responses.isEmpty()) return 0d;
        double total = 0d; int count = 0;
        for (QuestionnaireResponse r : responses) {
            if (r.stressLevel > 0) { total += r.stressLevel; count++; }
        }
        return count == 0 ? 0d : total / count;
    }

    private double averageMoodScore(List<QuestionnaireResponse> responses) {
        if (responses == null || responses.isEmpty()) return 0d;
        double total = 0d; int count = 0;
        for (QuestionnaireResponse r : responses) {
            total += MoodMapper.moodToFloat(r.mood); count++;
        }
        return count == 0 ? 0d : total / count;
    }

    private double averageTaskCompletion(List<InterventionTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return 0d;
        int completed = 0;
        for (InterventionTask t : tasks) { if (t.isCompleted) completed++; }
        return (completed * 100d) / tasks.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // STANDARD DEVIATION HELPERS (Task 3.G.7)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute population standard deviation from a list of values and their mean.
     */
    private static double stdDev(double[] values, double mean) {
        if (values == null || values.length < 2) return 0d;
        double sumSqDiff = 0d;
        for (double v : values) {
            double diff = v - mean;
            sumSqDiff += diff * diff;
        }
        return Math.sqrt(sumSqDiff / values.length);
    }

    private double stdDevScreenTime(List<DailyUsage> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double[] vals = new double[list.size()];
        for (int i = 0; i < list.size(); i++) vals[i] = list.get(i).screenTimeMillis;
        return stdDev(vals, mean);
    }

    private double stdDevAppSwitches(List<DailyUsage> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double[] vals = new double[list.size()];
        for (int i = 0; i < list.size(); i++) vals[i] = list.get(i).totalAppSwitchCount;
        return stdDev(vals, mean);
    }

    private double stdDevNightUsage(List<DailyUsage> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double[] vals = new double[list.size()];
        for (int i = 0; i < list.size(); i++) vals[i] = list.get(i).getNightUsageMinutes();
        return stdDev(vals, mean);
    }

    private double stdDevUnlocks(List<DailyUsage> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double[] vals = new double[list.size()];
        for (int i = 0; i < list.size(); i++) vals[i] = list.get(i).unlockCount;
        return stdDev(vals, mean);
    }

    private double stdDevLaunches(List<DailyUsage> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double[] vals = new double[list.size()];
        for (int i = 0; i < list.size(); i++) vals[i] = list.get(i).totalLaunchCount;
        return stdDev(vals, mean);
    }

    private double stdDevSleep(List<QuestionnaireResponse> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double[] vals = new double[list.size()];
        int c = 0;
        for (QuestionnaireResponse r : list) {
            if (r.sleepHours > 0) vals[c++] = r.sleepHours;
        }
        if (c < 2) return 0d;
        double[] trimmed = new double[c];
        System.arraycopy(vals, 0, trimmed, 0, c);
        return stdDev(trimmed, mean);
    }

    private double stdDevStress(List<QuestionnaireResponse> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double[] vals = new double[list.size()];
        int c = 0;
        for (QuestionnaireResponse r : list) {
            if (r.stressLevel > 0) vals[c++] = r.stressLevel;
        }
        if (c < 2) return 0d;
        double[] trimmed = new double[c];
        System.arraycopy(vals, 0, trimmed, 0, c);
        return stdDev(trimmed, mean);
    }

    private double stdDevMoodScore(List<QuestionnaireResponse> list, double mean) {
        if (list == null || list.size() < 2) return 0d;
        double[] vals = new double[list.size()];
        for (int i = 0; i < list.size(); i++) vals[i] = MoodMapper.moodToFloat(list.get(i).mood);
        return stdDev(vals, mean);
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATUS HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private String computeStatus(int dataPoints) {
        if (dataPoints >= MIN_DAYS_READY) return "READY";
        if (dataPoints >= MIN_DAYS_CALIBRATING) return "CALIBRATING";
        return "INSUFFICIENT";
    }

    private boolean isSameDay(long first, long second) {
        if (first <= 0L || second <= 0L) return false;
        Calendar a = Calendar.getInstance(); a.setTimeInMillis(first);
        Calendar b = Calendar.getInstance(); b.setTimeInMillis(second);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
