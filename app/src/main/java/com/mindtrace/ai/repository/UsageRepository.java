package com.mindtrace.ai.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.behavior.BehaviorAnalyzer;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.behavior.BehaviorSnapshot;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.AppUsageSnapshotDao;
import com.mindtrace.ai.database.dao.BehaviorSnapshotDao;
import com.mindtrace.ai.database.dao.BehaviorUsageSummaryDao;
import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.dao.UsageDao;
import com.mindtrace.ai.database.dao.UsageSessionDao;
import com.mindtrace.ai.database.dao.WellnessSummaryDao;
import com.mindtrace.ai.database.entity.AppUsageSnapshot;
import com.mindtrace.ai.database.entity.BehaviorSnapshotEntity;
import com.mindtrace.ai.database.entity.BehaviorUsageSummary;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.UsageSession;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.database.entity.WellnessSummary;
import com.mindtrace.ai.usage.UsageBehaviorSignal;
import com.mindtrace.ai.usage.UsageIntelligenceEngine;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class UsageRepository {
    private static final int DEFAULT_TOP_APPS_LIMIT = 10;

    private final Context appContext;
    private final UsageDao usageDao;
    private final QuestionnaireDao questionnaireDao;
    private final TaskDao taskDao;
    private final AppUsageSnapshotDao appUsageSnapshotDao;
    private final UsageSessionDao usageSessionDao;
    private final BehaviorSnapshotDao behaviorSnapshotDao;
    private final BehaviorUsageSummaryDao behaviorUsageSummaryDao;
    private final WellnessSummaryDao wellnessSummaryDao;
    private final BaselineManager baselineManager;
    private final BehaviorAnalyzer behaviorAnalyzer;
    private final SettingsRepository settingsRepository;
    private final UsageIntelligenceEngine usageIntelligenceEngine;
    private final ExecutorService executorService;

    public UsageRepository(Context context) {
        appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        usageDao = db.usageDao();
        questionnaireDao = db.questionnaireDao();
        taskDao = db.taskDao();
        appUsageSnapshotDao = db.appUsageSnapshotDao();
        usageSessionDao = db.usageSessionDao();
        behaviorSnapshotDao = db.behaviorSnapshotDao();
        behaviorUsageSummaryDao = db.behaviorUsageSummaryDao();
        wellnessSummaryDao = db.wellnessSummaryDao();
        baselineManager = new BaselineManager(db);
        behaviorAnalyzer = new BehaviorAnalyzer();
        settingsRepository = new SettingsRepository(appContext);
        usageIntelligenceEngine = new UsageIntelligenceEngine();
        executorService = com.mindtrace.ai.util.AppExecutors.diskIO();
    }

    public void insertUsage(DailyUsage usage) {
        executorService.execute(() -> usageDao.insert(usage));
    }

    public LiveData<List<DailyUsage>> getAllUsage() {
        return usageDao.observeAllUsage();
    }

    public LiveData<List<DailyUsage>> getWeeklyUsageHistory() {
        return usageDao.observeUsageSince(getStartOfDayOffset(-6));
    }

    public LiveData<List<DailyUsage>> getMonthlyUsageHistory() {
        return usageDao.observeUsageSince(getStartOfDayOffset(-29));
    }

    public LiveData<List<AppUsageSnapshot>> getAppUsageSnapshotsForDay(long dayTimestamp) {
        return appUsageSnapshotDao.observeSnapshotsForDay(dayTimestamp);
    }

    public LiveData<List<AppUsageSnapshot>> getTodayAppUsageSnapshots() {
        return appUsageSnapshotDao.observeSnapshotsForDay(getStartOfTodayMillis());
    }

    public LiveData<List<AppUsageSnapshot>> getAppUsageSnapshotsSince(long since) {
        return appUsageSnapshotDao.observeSnapshotsSince(since);
    }

    public LiveData<List<UsageSession>> getUsageSessionsForDate(long dayTimestamp) {
        return usageSessionDao.observeSessionsForDay(dayTimestamp);
    }

    public LiveData<BehaviorUsageSummary> getTodayBehaviorSummary() {
        return behaviorUsageSummaryDao.getSummaryForDay(getStartOfTodayMillis());
    }

    public LiveData<BehaviorSnapshotEntity> getLatestBehaviorSnapshot() {
        return behaviorSnapshotDao.observeLatest();
    }

    public LiveData<List<BehaviorSnapshotEntity>> getLast7BehaviorSnapshots() {
        return behaviorSnapshotDao.observeLast7();
    }

    public LiveData<List<BehaviorSnapshotEntity>> getBehaviorSnapshotsSince(long since) {
        return behaviorSnapshotDao.observeSince(since);
    }

    public LiveData<List<BehaviorUsageSummary>> getBehaviorSummariesSince(long since) {
        return behaviorUsageSummaryDao.getSummariesSince(since);
    }

    public LiveData<List<WellnessSummary>> getAllWellnessSummaries() {
        return wellnessSummaryDao.getAllSummaries();
    }

    public LiveData<WellnessSummary> getLatestWellnessSummary() {
        return wellnessSummaryDao.getLatestSummary();
    }

    public LiveData<UserBaseline> getUserBaseline() {
        return baselineManager.getBaseline();
    }

    public UserBaseline getUserBaselineSync() {
        return baselineManager.getBaselineSync();
    }

    public UserBaseline computeUserBaseline() {
        return baselineManager.computeUserBaseline();
    }

    public UserBaseline computeUserBaselineIfOutdated() {
        return baselineManager.computeUserBaselineIfOutdated();
    }

    public long getTodayScreenTime(Context context) {
        SettingsRepository.SettingsState settingsState = settingsRepository.getSettingsState();
        if (!settingsState.trackingEnabled || !settingsState.hasUsagePermission) {
            return 0L;
        }
        UsageIntelligenceEngine.UsageIntelligenceResult intelligence =
                analyzeToday(context, settingsState.includeSystemApps);
        return intelligence.dailyUsage == null ? 0L : intelligence.dailyUsage.screenTimeMillis;
    }

    public List<AppUsageModel> getTopUsedApps(Context context) {
        return getTopUsedApps(context, DEFAULT_TOP_APPS_LIMIT, false);
    }

    public List<AppUsageModel> getTopUsedApps(Context context, int limit, boolean includeSystemApps) {
        List<AppUsageModel> allApps = getAllUsedAppsForToday(context, includeSystemApps);
        if (allApps.isEmpty()) {
            return allApps;
        }
        int safeLimit = limit <= 0 ? allApps.size() : Math.min(limit, allApps.size());
        return new ArrayList<>(allApps.subList(0, safeLimit));
    }

    public List<AppUsageModel> getAllUsedAppsForToday(Context context, boolean includeSystemApps) {
        SettingsRepository.SettingsState settingsState = settingsRepository.getSettingsState();
        if (!settingsState.trackingEnabled || !settingsState.hasUsagePermission) {
            return new ArrayList<>();
        }
        return analyzeToday(context, includeSystemApps).appUsageModels;
    }

    public BehaviorReport getTodayBehavior(Context context) {
        SettingsRepository.SettingsState settingsState = settingsRepository.getSettingsState();
        long startOfToday = getStartOfTodayMillis();
        long now = System.currentTimeMillis();

        if (!settingsState.trackingEnabled) {
            return BehaviorReport.unavailable(startOfToday, now, "Behavior tracking is turned off in Settings.");
        }
        if (!settingsState.hasUsagePermission) {
            return BehaviorReport.unavailable(startOfToday, now, "Grant usage access to analyze switching, sessions, and late-night patterns.");
        }

        return behaviorAnalyzer.analyze(context.getApplicationContext(), startOfToday, now, settingsState.includeSystemApps);
    }

    public UsageBehaviorSignal getTodayUsageBehaviorSignal(Context context, boolean includeSystemApps) {
        SettingsRepository.SettingsState settingsState = settingsRepository.getSettingsState();
        if (!settingsState.trackingEnabled || !settingsState.hasUsagePermission) {
            return new UsageBehaviorSignal();
        }
        return analyzeToday(context, includeSystemApps).behaviorSignal;
    }



    public DashboardSnapshot refreshTodayUsageSnapshot(Context context, boolean includeSystemApps) {
        SettingsRepository.SettingsState settingsState = settingsRepository.getSettingsState();
        long dayTimestamp = getStartOfTodayMillis();
        long now = System.currentTimeMillis();

        if (!settingsState.trackingEnabled) {
            return new DashboardSnapshot(
                    0L,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    null,
                    BehaviorReport.unavailable(dayTimestamp, now, "Usage tracking is turned off in Settings.")
            );
        }

        if (!settingsState.hasUsagePermission) {
            return new DashboardSnapshot(
                    0L,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    null,
                    BehaviorReport.unavailable(dayTimestamp, now, "Grant usage access to analyze device usage.")
            );
        }

        UsageIntelligenceEngine.UsageIntelligenceResult intelligence = analyzeToday(context, includeSystemApps);
        persistUsageIntelligence(intelligence);

        // ── SINGLE SOURCE OF TRUTH ──
        // Build BehaviorReport from the SAME data pass that computed screen time.
        // This eliminates the double-query bug where BehaviorAnalyzer.analyze()
        // re-queried UsageStatsManager with different filtering logic and produced
        // totals that didn't match Android's Digital Wellbeing.
        BehaviorReport behaviorReport = buildBehaviorReportFromIntelligence(intelligence, dayTimestamp, now);
        
        persistBehaviorSnapshot(behaviorReport, dayTimestamp, now);
        computeUserBaselineIfOutdated();

        List<AppUsageModel> allApps = intelligence.appUsageModels == null
                ? new ArrayList<>()
                : new ArrayList<>(intelligence.appUsageModels);
        List<AppUsageModel> topApps = allApps.isEmpty()
                ? new ArrayList<>()
                : new ArrayList<>(allApps.subList(0, Math.min(DEFAULT_TOP_APPS_LIMIT, allApps.size())));
        long screenTimeMillis = intelligence.dailyUsage == null ? 0L : intelligence.dailyUsage.screenTimeMillis;
        return new DashboardSnapshot(
                screenTimeMillis,
                topApps,
                allApps,
                intelligence.mostUsedApp,
                behaviorReport
        );
    }

    public void buildAndSaveDailyUsageSnapshot(Context context, boolean includeSystemApps) {
        SettingsRepository.SettingsState settingsState = settingsRepository.getSettingsState();
        if (!settingsState.trackingEnabled || !settingsState.hasUsagePermission) {
            return;
        }
        persistUsageIntelligence(analyzeToday(context, includeSystemApps));
    }

    public void buildAndSaveAppUsageSnapshots(Context context, boolean includeSystemApps) {
        buildAndSaveDailyUsageSnapshot(context, includeSystemApps);
    }

    public AppUsageModel getMostUsedApp(Context context, boolean includeSystemApps) {
        List<AppUsageModel> apps = getTopUsedApps(context, 1, includeSystemApps);
        return apps.isEmpty() ? null : apps.get(0);
    }

    public void insertResponse(QuestionnaireResponse response) {
        executorService.execute(() -> questionnaireDao.insert(response));
    }

    public LiveData<List<QuestionnaireResponse>> getAllResponses() {
        return questionnaireDao.getAllResponses();
    }

    public void insertTask(InterventionTask task) {
        executorService.execute(() -> taskDao.insert(task));
    }

    public void updateTask(InterventionTask task) {
        executorService.execute(() -> taskDao.update(task));
    }

    public LiveData<List<InterventionTask>> getActiveTasks() {
        return taskDao.getActiveTasks();
    }

    public LiveData<List<InterventionTask>> getCompletedTasks() {
        return taskDao.getCompletedTasks();
    }

    public LiveData<List<InterventionTask>> getAllTasks() {
        return taskDao.getAllTasks();
    }

    public void insertResponseSync(QuestionnaireResponse response) {
        questionnaireDao.insert(response);
    }

    public List<QuestionnaireResponse> getRecentResponsesSync() {
        return questionnaireDao.getRecentResponses();
    }

    public List<DailyUsage> getUsageHistorySinceSync(long since) {
        return usageDao.getUsageSince(since);
    }

    public List<DailyUsage> getWeeklyUsageHistorySync() {
        return usageDao.getUsageSince(getStartOfDayOffset(-6));
    }

    public List<DailyUsage> getMonthlyUsageHistorySync() {
        return usageDao.getUsageSince(getStartOfDayOffset(-29));
    }

    public List<DailyUsage> getUsageHistoryBetweenSync(long start, long end) {
        return usageDao.getUsageBetween(start, end);
    }

    public List<AppUsageSnapshot> getAppUsageSnapshotsBetweenSync(long start, long end) {
        return appUsageSnapshotDao.getSnapshotsBetweenSync(start, end);
    }

    public List<BehaviorUsageSummary> getBehaviorSummariesBetweenSync(long start, long end) {
        return behaviorUsageSummaryDao.getSummariesBetweenSync(start, end);
    }

    public List<UsageSession> getUsageSessionsForDateSync(long dayTimestamp) {
        return usageSessionDao.getSessionsForDaySync(dayTimestamp);
    }

    public List<InterventionTask> getTasksSinceSync(long since) {
        return taskDao.getTasksSinceSync(since);
    }

    // ═════════════════════════════════════════════════════════════════════
    // SECTION I — Category-based & Intelligence Queries
    // ═════════════════════════════════════════════════════════════════════

    /** Get app snapshots filtered by category for a given day. */
    public List<AppUsageSnapshot> getAppsByCategory(long dayTimestamp, String category) {
        return appUsageSnapshotDao.getAppsByCategory(dayTimestamp, category);
    }

    /** Get all passive app snapshots for a day (Social, Video, Entertainment, Gaming, News). */
    public List<AppUsageSnapshot> getPassiveApps(long dayTimestamp) {
        List<AppUsageSnapshot> all = appUsageSnapshotDao.getSnapshotsForDaySync(dayTimestamp);
        List<AppUsageSnapshot> passive = new ArrayList<>();
        if (all != null) {
            for (AppUsageSnapshot s : all) {
                if (s.isPassiveApp) passive.add(s);
            }
        }
        return passive;
    }

    /** Get all productive app snapshots for a day. */
    public List<AppUsageSnapshot> getProductiveApps(long dayTimestamp) {
        List<AppUsageSnapshot> all = appUsageSnapshotDao.getSnapshotsForDaySync(dayTimestamp);
        List<AppUsageSnapshot> productive = new ArrayList<>();
        if (all != null) {
            for (AppUsageSnapshot s : all) {
                if (!s.isPassiveApp && !s.isSystemApp) productive.add(s);
            }
        }
        return productive;
    }

    /** Get total passive app time for a day (ms). (1.I.2) */
    public long getPassiveAppTime(long dayTimestamp) {
        return appUsageSnapshotDao.getPassiveTimeForDay(dayTimestamp);
    }

    /** Get total productive app time for a day (ms). (1.I.3) */
    public long getProductiveAppTime(long dayTimestamp) {
        return appUsageSnapshotDao.getProductiveTimeForDay(dayTimestamp);
    }

    /** Get passive-to-total ratio for a day. */
    public float getPassiveRatio(long dayTimestamp) {
        long passive = getPassiveAppTime(dayTimestamp);
        long productive = getProductiveAppTime(dayTimestamp);
        long total = passive + productive;
        return total > 0 ? (float) passive / total : 0f;
    }

    /**
     * Get category breakdown for a day as a map. (1.I.4)
     * Returns Map of categoryName → total time in ms.
     */
    public java.util.Map<String, Long> getCategoryBreakdown(long dayTimestamp) {
        List<AppUsageSnapshot> snapshots = appUsageSnapshotDao.getSnapshotsForDaySync(dayTimestamp);
        java.util.Map<String, Long> breakdown = new java.util.HashMap<>();
        if (snapshots == null) return breakdown;

        for (AppUsageSnapshot s : snapshots) {
            String cat = s.appCategory != null ? s.appCategory : "OTHER";
            breakdown.put(cat, breakdown.getOrDefault(cat, 0L) + s.usageTimeMillis);
        }
        return breakdown;
    }

    /** Get average session length for a day (ms). (1.I.5) */
    public long getAverageSessionLength(long dayTimestamp) {
        return usageSessionDao.getAvgSessionDuration(dayTimestamp);
    }

    /** Get average session length for a specific app on a day (ms). */
    public long getAverageSessionLengthForApp(long dayTimestamp, String packageName) {
        List<UsageSession> sessions = usageSessionDao.getSessionsForApp(dayTimestamp, packageName);
        if (sessions == null || sessions.isEmpty()) return 0;
        long total = 0;
        for (UsageSession s : sessions) total += s.durationMillis;
        return total / sessions.size();
    }

    /** Get unlock count for a day. (1.I.6) */
    public int getUnlockCount(long dayTimestamp) {
        DailyUsage usage = usageDao.getUsageForDay(dayTimestamp);
        return usage != null ? usage.unlockCount : 0;
    }

    /** Get today's unlock count from ScreenEventReceiver (real-time). */
    public int getTodayUnlockCountLive() {
        com.mindtrace.ai.service.ScreenEventReceiver.ScreenEventData data =
                com.mindtrace.ai.service.ScreenEventReceiver.getTodayData(appContext);
        return data.unlockCount;
    }

    /** Get the behavioral risk score for a specific day. */
    public float getRiskScoreForDay(long dayTimestamp) {
        BehaviorSnapshotEntity snapshot = behaviorSnapshotDao.getForDay(dayTimestamp);
        return snapshot != null ? snapshot.overallBehaviorRiskScore : 0f;
    }

    /** Get count of "green days" (healthy patterns) in the last N days. */
    public int getGreenDayCount(int lastNDays) {
        return behaviorSnapshotDao.getGreenDayCount(getStartOfDayOffset(-lastNDays));
    }

    /** Get count of high-risk days in the last N days. */
    public int getHighRiskDayCount(int lastNDays, float threshold) {
        return behaviorSnapshotDao.getHighRiskDayCount(getStartOfDayOffset(-lastNDays), threshold);
    }

    public void replaceActiveTasksSync(List<InterventionTask> newTasks) {
        List<InterventionTask> activeTasks = taskDao.getActiveTasksSync();
        long now = System.currentTimeMillis();
        for (InterventionTask task : activeTasks) {
            task.isSkipped = true;
            task.skippedAt = now;
            taskDao.update(task);
        }

        if (newTasks == null) {
            return;
        }

        for (InterventionTask task : newTasks) {
            taskDao.insert(task);
        }
    }

    public void saveWellnessSummary(WellnessSummary summary) {
        executorService.execute(() -> wellnessSummaryDao.insertOrReplace(summary));
    }

    private UsageIntelligenceEngine.UsageIntelligenceResult analyzeToday(Context context, boolean includeSystemApps) {
        long startOfToday = getStartOfTodayMillis();
        long now = System.currentTimeMillis();
        return usageIntelligenceEngine.analyzeDay(
                context.getApplicationContext(),
                startOfToday,
                now,
                includeSystemApps
        );
    }

    private void persistUsageIntelligence(UsageIntelligenceEngine.UsageIntelligenceResult intelligence) {
        if (intelligence == null || intelligence.dailyUsage == null) {
            return;
        }

        AppDatabase db = AppDatabase.getInstance(appContext);
        db.runInTransaction(() -> {
            long dayTimestamp = intelligence.dayTimestamp;
            DailyUsage existingUsage = usageDao.getUsageForDay(dayTimestamp);
            DailyUsage dailyUsage = intelligence.dailyUsage;
            if (existingUsage == null) {
                usageDao.insert(dailyUsage);
            } else {
                dailyUsage.id = existingUsage.id;
                usageDao.update(dailyUsage);
            }

            appUsageSnapshotDao.deleteForDay(dayTimestamp);
            for (AppUsageSnapshot snapshot : intelligence.appSnapshots) {
                appUsageSnapshotDao.insertOrReplace(snapshot);
            }

            usageSessionDao.deleteForDay(dayTimestamp);
            for (UsageSession session : intelligence.sessions) {
                usageSessionDao.insert(session);
            }

            if (intelligence.behaviorSummary != null) {
                BehaviorUsageSummary existingSummary = behaviorUsageSummaryDao.getSummaryForDaySync(dayTimestamp);
                if (existingSummary != null) {
                    intelligence.behaviorSummary.id = existingSummary.id;
                }
                behaviorUsageSummaryDao.insertOrReplace(intelligence.behaviorSummary);
            }
        });
    }

    /**
     * Build a BehaviorReport directly from UsageIntelligenceEngine results.
     * This avoids a second UsageStatsManager query and ensures all metrics
     * (screen time, switches, sessions) come from ONE authoritative pass.
     */
    private BehaviorReport buildBehaviorReportFromIntelligence(
            UsageIntelligenceEngine.UsageIntelligenceResult intelligence,
            long dayTimestamp,
            long recordedAt
    ) {
        if (intelligence == null || intelligence.dailyUsage == null) {
            return BehaviorReport.empty(dayTimestamp, recordedAt);
        }

        BehaviorReport report = BehaviorReport.empty(dayTimestamp, recordedAt);
        report.dataAvailable = true;
        report.totalForegroundMillis = intelligence.dailyUsage.screenTimeMillis;
        report.appSwitchCount = intelligence.dailyUsage.totalAppSwitchCount;
        report.lateNightUsageMillis = intelligence.dailyUsage.nightUsageMillis;

        // Derive metrics from sessions
        int rapidSwitchCount = 0;
        int shortSessionCount = 0;
        int bingeSessionCount = 0;
        long longestSessionMillis = 0;
        String lastPkg = null;
        long lastFgTime = 0;

        for (com.mindtrace.ai.database.entity.UsageSession s : intelligence.sessions) {
            longestSessionMillis = Math.max(longestSessionMillis, s.durationMillis);
            if (s.durationMillis <= com.mindtrace.ai.behavior.BehaviorThresholds.SHORT_SESSION_THRESHOLD_MILLIS) {
                shortSessionCount++;
            }
            if (s.durationMillis >= com.mindtrace.ai.behavior.BehaviorThresholds.BINGE_SESSION_THRESHOLD_MILLIS) {
                bingeSessionCount++;
            }
            // Detect rapid switches from session sequence
            if (lastPkg != null && !lastPkg.equals(s.packageName)) {
                if (s.sessionStart - lastFgTime <= com.mindtrace.ai.behavior.BehaviorThresholds.RAPID_SWITCH_WINDOW_MILLIS) {
                    rapidSwitchCount++;
                }
            }
            lastPkg = s.packageName;
            lastFgTime = s.sessionStart;
        }

        report.rapidSwitchCount = rapidSwitchCount;
        report.shortSessionCount = shortSessionCount;
        report.bingeSessionCount = bingeSessionCount;
        report.longestSessionMillis = longestSessionMillis;
        report.lateNightUsageRatio = report.totalForegroundMillis > 0
                ? report.lateNightUsageMillis / (double) report.totalForegroundMillis : 0d;

        // Dominant app
        if (intelligence.mostUsedApp != null) {
            report.dominantAppPackage = intelligence.mostUsedApp.packageName;
            report.dominantUsageRatio = report.totalForegroundMillis > 0
                    ? intelligence.mostUsedApp.usageTime / (double) report.totalForegroundMillis : 0d;
        }

        // AI vectors from intelligence signal
        if (intelligence.behaviorSignal != null) {
            report.activeVsPassiveRatio = intelligence.behaviorSignal.activeVsPassiveRatio;
            report.dominantUsageQuadrant = intelligence.behaviorSignal.dominantUsageQuadrant;
            report.frequentAppLoops = intelligence.behaviorSignal.frequentAppLoops;
            report.hasLoopPattern = !intelligence.behaviorSignal.frequentAppLoops.isEmpty();
        }

        // Generate summary and explanation
        report.summaryLabel = behaviorAnalyzer.buildSummaryLabel(report);
        report.explanation = behaviorAnalyzer.buildExplanation(report);

        return report;
    }

    private void persistBehaviorSnapshot(BehaviorReport report, long dayTimestamp, long recordedAt) {
        if (report == null || !report.dataAvailable) {
            return;
        }

        BehaviorSnapshot snapshot = BehaviorSnapshot.fromReport(report, dayTimestamp, recordedAt);
        BehaviorSnapshotEntity entity = new BehaviorSnapshotEntity();
        BehaviorSnapshotEntity existing = behaviorSnapshotDao.getLatestSync();
        if (existing != null && existing.dayTimestamp == dayTimestamp) {
            entity.id = existing.id;
        }
        entity.dayTimestamp = snapshot.dayTimestamp;
        entity.timestamp = snapshot.timestamp;
        entity.appSwitchCount = snapshot.appSwitchCount;
        entity.rapidSwitchCount = snapshot.rapidSwitchCount;
        entity.bingeSessionCount = snapshot.bingeSessionCount;
        entity.lateNightUsageMillis = snapshot.lateNightUsageMillis;
        entity.totalForegroundMillis = snapshot.totalForegroundMillis;
        entity.longestSessionMillis = snapshot.longestSessionMillis;
        entity.hasLoopPattern = snapshot.hasLoopPattern;
        entity.dominantAppPackage = snapshot.dominantAppPackage;
        entity.summaryLabel = snapshot.summaryLabel;
        entity.explanation = snapshot.explanation;
        behaviorSnapshotDao.insertOrReplace(entity);
    }

    private long getStartOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getStartOfDayOffset(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTimeInMillis();
    }

    public static class DashboardSnapshot {
        public final long screenTimeMillis;
        public final List<AppUsageModel> topApps;
        public final List<AppUsageModel> allApps;
        public final AppUsageModel mostUsedApp;
        public final BehaviorReport behaviorReport;

        public DashboardSnapshot(
                long screenTimeMillis,
                List<AppUsageModel> topApps,
                List<AppUsageModel> allApps,
                AppUsageModel mostUsedApp,
                BehaviorReport behaviorReport
        ) {
            this.screenTimeMillis = screenTimeMillis;
            this.topApps = topApps;
            this.allApps = allApps;
            this.mostUsedApp = mostUsedApp;
            this.behaviorReport = behaviorReport;
        }
    }
}
