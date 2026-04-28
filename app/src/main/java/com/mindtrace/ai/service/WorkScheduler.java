package com.mindtrace.ai.service;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.mindtrace.ai.services.CrisisFollowUpWorker;
import com.mindtrace.ai.services.DailyReminderWorker;
import com.mindtrace.ai.services.DataCleanupWorker;
import com.mindtrace.ai.services.EfficacyWorker;
import com.mindtrace.ai.services.NudgeWorker;
import com.mindtrace.ai.services.TaskReminderWorker;
import com.mindtrace.ai.services.UsageWorker;
import com.mindtrace.ai.services.WeeklyAssessmentWorker;
import com.mindtrace.ai.services.WeeklyReportWorker;
import com.mindtrace.ai.services.WellnessSyncWorker;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Central coordinator for all WorkManager periodic jobs.
 *
 * <p>This class is the <b>single source of truth</b> for worker scheduling.
 * Both {@code MindTraceApp.onCreate()} and {@code BootReceiver} delegate to
 * {@link #scheduleAll(Context)} so there is never a divergence in the
 * registered job set.</p>
 *
 * <h3>Worker Roster (12 workers):</h3>
 * <pre>
 *   ┌──────────────────────────┬──────────┬────────────────────────────────────┐
 *   │ Worker                   │ Interval │ Purpose                            │
 *   ├──────────────────────────┼──────────┼────────────────────────────────────┤
 *   │ UsageWorker              │ 24 h     │ Usage data aggregation + AI tasks  │
 *   │ UsageSnapshotWorker      │  4 h     │ Frequent usage snapshot refresh    │
 *   │ MidnightSummaryWorker    │ 24 h     │ Daily finalize + WellnessSummary   │
 *   │ ClassificationWorker     │  6 h     │ MultiModal AI re-classification    │
 *   │ BaselineComputeWorker    │  7 d     │ Recompute rolling baselines        │
 *   │ TaskReminderWorker       │  4 h     │ Task lifecycle + nudges            │
 *   │ CrisisFollowUpWorker     │  1 h     │ Crisis follow-up check-ins         │
 *   │ EfficacyWorker           │  2 h     │ Observation window processing      │
 *   │ WellnessSyncWorker       │ 24 h     │ Aggregate daily wellness metrics   │
 *   │ DataCleanupWorker        │  7 d     │ Prune old snapshots                │
 *   │ DailyReminderWorker      │ 12 h     │ Check-in + streak reminders        │
 *   │ WeeklyReportWorker       │  7 d     │ Weekly wellness notification       │
 *   │ WeeklyAssessmentWorker   │  7 d     │ Feature vector trend analysis      │
 *   │ NudgeWorker              │ 30 min   │ JIT behavioral micro-nudges        │
 *   └──────────────────────────┴──────────┴────────────────────────────────────┘
 * </pre>
 *
 * @see com.mindtrace.ai.MindTraceApp
 * @see com.mindtrace.ai.services.BootReceiver
 */
public final class WorkScheduler {

    private static final String TAG = "WorkScheduler";

    // ── Work names (unique identifiers) ──
    public static final String WORK_USAGE_SYNC           = "USAGE_SYNC";
    public static final String WORK_USAGE_SNAPSHOT       = "USAGE_SNAPSHOT";
    public static final String WORK_MIDNIGHT_SUMMARY     = "MIDNIGHT_SUMMARY";
    public static final String WORK_CLASSIFICATION       = "CLASSIFICATION";
    public static final String WORK_BASELINE_COMPUTE     = "BASELINE_COMPUTE";
    public static final String WORK_TASK_REMINDER        = "TASK_REMINDER";
    public static final String WORK_CRISIS_FOLLOW_UP     = "CRISIS_FOLLOW_UP";
    public static final String WORK_EFFICACY_OBSERVATION = "EFFICACY_OBSERVATION";
    public static final String WORK_WELLNESS_SYNC        = "WELLNESS_SYNC";
    public static final String WORK_DATA_CLEANUP         = "DATA_CLEANUP";
    public static final String WORK_DAILY_REMINDER       = "DAILY_REMINDER";
    public static final String WORK_WEEKLY_REPORT        = "WEEKLY_REPORT";
    public static final String WORK_WEEKLY_ASSESSMENT    = "WEEKLY_ASSESSMENT";
    public static final String WORK_BEHAVIORAL_NUDGE     = "BEHAVIORAL_NUDGE";

    private WorkScheduler() { /* no instances */ }

    // ═════════════════════════════════════════════════════════════════
    // EXPEDITED CRISIS DISPATCH
    // ═════════════════════════════════════════════════════════════════

    /** Unique name for the one-time expedited crisis work. */
    private static final String WORK_CRISIS_IMMEDIATE = "CRISIS_IMMEDIATE";

    /**
     * Immediately dispatches an expedited one-time crisis follow-up.
     *
     * <p>Called by {@link ClassificationWorker} when a crisis flag is first set.
     * This ensures the user receives a safety notification within seconds,
     * rather than waiting up to 60 minutes for the periodic
     * {@link CrisisFollowUpWorker} to fire.</p>
     *
     * <p>Uses {@link OutOfQuotaPolicy#RUN_AS_NON_EXPEDITED_WORK_REQUEST}
     * as a graceful fallback if the device's expedited quota is exhausted.</p>
     *
     * @param context application context
     */
    public static void triggerImmediateCrisisFollowUp(Context context) {
        try {
            OneTimeWorkRequest crisisWork = new OneTimeWorkRequest.Builder(
                    CrisisFollowUpWorker.class)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(WORK_CRISIS_FOLLOW_UP)
                    .addTag("crisis_expedited")
                    .build();

            WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_CRISIS_IMMEDIATE,
                    ExistingWorkPolicy.REPLACE,
                    crisisWork);

            Log.d(TAG, "⚡ Expedited crisis follow-up dispatched");
        } catch (Exception e) {
            Log.e(TAG, "Failed to dispatch expedited crisis work", e);
        }
    }

    /**
     * Schedules (or re-schedules) all periodic workers.
     * Safe to call multiple times — {@code ExistingPeriodicWorkPolicy.KEEP}
     * ensures already-running work is not duplicated.
     *
     * @param context application or activity context
     */
    public static void scheduleAll(Context context) {
        WorkManager wm = WorkManager.getInstance(context);

        // ── Constraints ──
        Constraints batteryOk = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();
        Constraints batteryAndIdle = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build();

        // ═════════════════════════════════════════════════════════════════
        // 1. USAGE SYNC — every 24h (legacy full pipeline)
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_USAGE_SYNC,
                new PeriodicWorkRequest.Builder(UsageWorker.class, 24, TimeUnit.HOURS)
                        .addTag(WORK_USAGE_SYNC)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 2. USAGE SNAPSHOT — every 4h (frequent data refresh)
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_USAGE_SNAPSHOT,
                new PeriodicWorkRequest.Builder(
                        UsageSnapshotWorker.class, 4, TimeUnit.HOURS)
                        .addTag(WORK_USAGE_SNAPSHOT)
                        .setConstraints(batteryOk)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 3. MIDNIGHT SUMMARY — daily (offset to ~00:05)
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_MIDNIGHT_SUMMARY,
                new PeriodicWorkRequest.Builder(
                        MidnightSummaryWorker.class, 24, TimeUnit.HOURS)
                        .addTag(WORK_MIDNIGHT_SUMMARY)
                        .setInitialDelay(millisUntilMidnight(), TimeUnit.MILLISECONDS)
                        .setConstraints(batteryOk)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 4. CLASSIFICATION — every 6h (AI re-run)
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_CLASSIFICATION,
                new PeriodicWorkRequest.Builder(
                        ClassificationWorker.class, 6, TimeUnit.HOURS)
                        .addTag(WORK_CLASSIFICATION)
                        .setConstraints(batteryOk)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 5. BASELINE COMPUTE — weekly (Sunday recompute)
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_BASELINE_COMPUTE,
                new PeriodicWorkRequest.Builder(
                        BaselineComputeWorker.class, 7, TimeUnit.DAYS)
                        .addTag(WORK_BASELINE_COMPUTE)
                        .setConstraints(batteryAndIdle)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 6. TASK REMINDER — every 4h
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_TASK_REMINDER,
                new PeriodicWorkRequest.Builder(TaskReminderWorker.class, 4, TimeUnit.HOURS)
                        .addTag(WORK_TASK_REMINDER)
                        .setConstraints(batteryOk)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 7. CRISIS FOLLOW-UP — every 1h (default; adaptive 30min in crisis)
        //    Uses UPDATE instead of KEEP so that if CrisisFollowUpWorker has
        //    escalated to 30-min cadence, a scheduleAll() call (e.g. reboot)
        //    won't silently revert to 1h while a crisis is still active.
        // ═════════════════════════════════════════════════════════════════
        wm.enqueueUniquePeriodicWork(WORK_CRISIS_FOLLOW_UP,
                ExistingPeriodicWorkPolicy.UPDATE,
                new PeriodicWorkRequest.Builder(CrisisFollowUpWorker.class, 1, TimeUnit.HOURS)
                        .addTag(WORK_CRISIS_FOLLOW_UP)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 8. EFFICACY OBSERVATION — every 2h
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_EFFICACY_OBSERVATION,
                new PeriodicWorkRequest.Builder(EfficacyWorker.class, 2, TimeUnit.HOURS)
                        .addTag(WORK_EFFICACY_OBSERVATION)
                        .setConstraints(batteryOk)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 9. WELLNESS SYNC — every 24h
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_WELLNESS_SYNC,
                new PeriodicWorkRequest.Builder(WellnessSyncWorker.class, 24, TimeUnit.HOURS)
                        .addTag(WORK_WELLNESS_SYNC)
                        .setConstraints(batteryOk)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 10. DATA CLEANUP — every 7 days
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_DATA_CLEANUP,
                new PeriodicWorkRequest.Builder(DataCleanupWorker.class, 7, TimeUnit.DAYS)
                        .addTag(WORK_DATA_CLEANUP)
                        .setConstraints(batteryAndIdle)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 11. DAILY REMINDER — every 12h
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_DAILY_REMINDER,
                new PeriodicWorkRequest.Builder(DailyReminderWorker.class, 12, TimeUnit.HOURS)
                        .addTag(WORK_DAILY_REMINDER)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 12. WEEKLY REPORT — every 7 days
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_WEEKLY_REPORT,
                new PeriodicWorkRequest.Builder(WeeklyReportWorker.class, 7, TimeUnit.DAYS)
                        .addTag(WORK_WEEKLY_REPORT)
                        .setConstraints(batteryOk)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 13. WEEKLY ASSESSMENT — every 7 days
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_WEEKLY_ASSESSMENT,
                new PeriodicWorkRequest.Builder(WeeklyAssessmentWorker.class, 7, TimeUnit.DAYS)
                        .addTag(WORK_WEEKLY_ASSESSMENT)
                        .setConstraints(batteryOk)
                        .build());

        // ═════════════════════════════════════════════════════════════════
        // 14. BEHAVIORAL NUDGE — every 30 min (JIT micro-interventions)
        // ═════════════════════════════════════════════════════════════════
        enqueue(wm, WORK_BEHAVIORAL_NUDGE,
                new PeriodicWorkRequest.Builder(NudgeWorker.class, 30, TimeUnit.MINUTES)
                        .addTag(WORK_BEHAVIORAL_NUDGE)
                        .setConstraints(batteryOk)
                        .build());

        Log.d(TAG, "All 14 workers scheduled successfully");
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private static void enqueue(WorkManager wm, String uniqueName,
                                PeriodicWorkRequest request) {
        wm.enqueueUniquePeriodicWork(uniqueName,
                ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /**
     * Returns milliseconds from now until the next midnight + 5 minutes.
     * Used to align the MidnightSummaryWorker initial delay.
     */
    static long millisUntilMidnight() {
        Calendar now = Calendar.getInstance(TimeZone.getDefault());
        Calendar target = (Calendar) now.clone();
        target.add(Calendar.DAY_OF_YEAR, 1);
        target.set(Calendar.HOUR_OF_DAY, 0);
        target.set(Calendar.MINUTE, 5);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        long delay = target.getTimeInMillis() - now.getTimeInMillis();
        return Math.max(delay, 60_000L); // At least 1 minute
    }
}
