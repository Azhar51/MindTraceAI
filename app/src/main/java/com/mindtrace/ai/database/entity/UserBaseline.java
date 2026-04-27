package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * User's personal baseline — rolling averages and standard deviations
 * of key behavioural and psychological metrics.
 *
 * <p>Used by the AI pipeline to detect deviations from the user's own
 * "normal" — a spike above personal baseline is more meaningful than
 * an absolute threshold.</p>
 *
 * <h3>Baseline Status Lifecycle:</h3>
 * <pre>
 *   INSUFFICIENT (0-2 days)  → not enough data for any baseline
 *   CALIBRATING  (3-6 days)  → preliminary baseline, low confidence
 *   READY        (7+ days)   → full baseline, high confidence
 * </pre>
 *
 * @see com.mindtrace.ai.repository.BaselineManager
 */
@Entity(tableName = "user_baseline")
public class UserBaseline {

    @PrimaryKey
    public int id = 1;

    // ═══════════════════════════════════════════════════════════════════
    // AVERAGES — Rolling means
    // ═══════════════════════════════════════════════════════════════════

    public double avgScreenTime7d;
    public double avgScreenTime30d;
    public double avgSleep7d;
    public double avgStress7d;
    public double avgMoodScore7d;
    public double avgTaskCompletion7d;

    /** Average app switches per day (7-day). */
    @ColumnInfo(defaultValue = "0")
    public double avgAppSwitches7d;

    /** Average night usage minutes (7-day). */
    @ColumnInfo(defaultValue = "0")
    public double avgNightUsageMinutes7d;

    /** Average unlock count per day (7-day). */
    @ColumnInfo(defaultValue = "0")
    public double avgUnlocks7d;

    /** Average app launches per day (7-day). */
    @ColumnInfo(defaultValue = "0")
    public double avgLaunches7d;

    /** Average passive consumption ratio (7-day). */
    @ColumnInfo(defaultValue = "0")
    public double avgPassiveRatio7d;

    // ═══════════════════════════════════════════════════════════════════
    // STANDARD DEVIATIONS — Variability measures
    // ═══════════════════════════════════════════════════════════════════

    /** Std dev of screen time (ms) over 7 days. */
    @ColumnInfo(defaultValue = "0")
    public double stdScreenTime7d;

    /** Std dev of app switches over 7 days. */
    @ColumnInfo(defaultValue = "0")
    public double stdAppSwitches7d;

    /** Std dev of night usage (ms) over 7 days. */
    @ColumnInfo(defaultValue = "0")
    public double stdNightUsage7d;

    /** Std dev of unlock count over 7 days. */
    @ColumnInfo(defaultValue = "0")
    public double stdUnlocks7d;

    /** Std dev of launch count over 7 days. */
    @ColumnInfo(defaultValue = "0")
    public double stdLaunches7d;

    /** Std dev of sleep hours over 7 days. */
    @ColumnInfo(defaultValue = "0")
    public double stdSleep7d;

    /** Std dev of stress level over 7 days. */
    @ColumnInfo(defaultValue = "0")
    public double stdStress7d;

    /** Std dev of mood score over 7 days. */
    @ColumnInfo(defaultValue = "0")
    public double stdMoodScore7d;

    // ═══════════════════════════════════════════════════════════════════
    // METADATA
    // ═══════════════════════════════════════════════════════════════════

    public long lastUpdated;

    /** Number of days with usage data contributing to the baseline. */
    @ColumnInfo(defaultValue = "0")
    public int dataPointCount;

    /**
     * Baseline readiness status.
     * "INSUFFICIENT" = 0-2 days, "CALIBRATING" = 3-6 days, "READY" = 7+ days.
     */
    @ColumnInfo(defaultValue = "INSUFFICIENT")
    public String baselineStatus;

    // ═══════════════════════════════════════════════════════════════════
    // CONVENIENCE
    // ═══════════════════════════════════════════════════════════════════

    /** Check if baseline has enough data for reliable deviation detection. */
    public boolean isReady() {
        return "READY".equals(baselineStatus);
    }

    /** Get screen time hours (from ms average). */
    public float getAvgScreenTimeHours() {
        return (float) (avgScreenTime7d / 3600000.0);
    }

    /** Compute z-score for today's screen time relative to baseline. */
    public float screenTimeZScore(long todayScreenTimeMs) {
        if (stdScreenTime7d <= 0) return 0f;
        return (float) ((todayScreenTimeMs - avgScreenTime7d) / stdScreenTime7d);
    }

    /** Compute z-score for today's unlock count. */
    public float unlockZScore(int todayUnlocks) {
        if (stdUnlocks7d <= 0) return 0f;
        return (float) ((todayUnlocks - avgUnlocks7d) / stdUnlocks7d);
    }

    /** Compute z-score for today's app switches. */
    public float appSwitchZScore(int todaySwitches) {
        if (stdAppSwitches7d <= 0) return 0f;
        return (float) ((todaySwitches - avgAppSwitches7d) / stdAppSwitches7d);
    }

    @NonNull
    @Override
    public String toString() {
        return "UserBaseline{" +
                "status=" + baselineStatus +
                ", days=" + dataPointCount +
                ", screenTime=" + String.format("%.1fh", getAvgScreenTimeHours()) +
                ", sleep=" + String.format("%.1f", avgSleep7d) +
                ", stress=" + String.format("%.1f", avgStress7d) +
                ", mood=" + String.format("%.1f", avgMoodScore7d) +
                '}';
    }
}
