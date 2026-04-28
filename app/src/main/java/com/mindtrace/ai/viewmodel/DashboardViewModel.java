package com.mindtrace.ai.viewmodel;

import android.app.Application;
import com.mindtrace.ai.viewmodel.usecase.BuildForecastUseCase;
import com.mindtrace.ai.viewmodel.usecase.HomeScreenTextHelper;
import com.mindtrace.ai.viewmodel.usecase.RebuildInsightsUseCase;
import com.mindtrace.ai.viewmodel.usecase.BuildMissionContentUseCase;
import com.mindtrace.ai.viewmodel.usecase.BuildWarningCardsUseCase;
import com.mindtrace.ai.viewmodel.usecase.BuildInsightCardsUseCase;
import com.mindtrace.ai.viewmodel.usecase.BuildPatternRadarCardUseCase;
import com.mindtrace.ai.viewmodel.usecase.BuildFocusWindowCardUseCase;
import com.mindtrace.ai.viewmodel.usecase.BuildNextActionUseCase;
import com.mindtrace.ai.ui.model.HomeScreenState.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.ai.AiInsightInput;
import com.mindtrace.ai.ai.AiInsightResult;
import com.mindtrace.ai.ai.AiInsightService;
import com.mindtrace.ai.ai.AnomalyDetector;
import com.mindtrace.ai.ai.BehaviorFeatureExtractor;
import com.mindtrace.ai.ai.BehaviorFeatures;
import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.ai.InsightEngine;
import com.mindtrace.ai.ai.EfficacyMetrics;
import com.mindtrace.ai.ai.InterventionEngine;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.BehaviorSnapshotEntity;
import com.mindtrace.ai.database.entity.BehaviorUsageSummary;
import com.mindtrace.ai.database.entity.DailyResetSession;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.ExerciseCompletion;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.JournalEntry;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.database.entity.UsageSession;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.database.entity.WellnessSummary;
import com.mindtrace.ai.onboarding.OnboardingAssessment;
import com.mindtrace.ai.onboarding.OnboardingProfileAnalyzer;
import com.mindtrace.ai.repository.ClassificationRepository;
import com.mindtrace.ai.repository.DailyResetRepository;
import com.mindtrace.ai.repository.OnboardingRepository;
import com.mindtrace.ai.repository.SettingsRepository;
import com.mindtrace.ai.repository.UsageRepository;
import com.mindtrace.ai.service.WorkerProgressTracker;
import com.mindtrace.ai.ui.model.HomeScreenState;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

@HiltViewModel
public class DashboardViewModel
extends AndroidViewModel {
    private final UsageRepository repository;
    private final DailyResetRepository dailyResetRepository;
    private final OnboardingRepository onboardingRepository;
    private final InsightEngine insightEngine;
    private final SettingsRepository settingsRepository;
    private final BehaviorFeatureExtractor behaviorFeatureExtractor;
    private final AnomalyDetector anomalyDetector;
    private final AiInsightService aiInsightService;
    private final OnboardingProfileAnalyzer onboardingProfileAnalyzer;
    private final ExecutorService executorService;
    private final LiveData<List<DailyUsage>> usageHistory;
    private final LiveData<List<QuestionnaireResponse>> stateHistory;
    private final LiveData<List<InterventionTask>> activeTasks;
    private final LiveData<List<InterventionTask>> allTasks;
    private final LiveData<List<BehaviorSnapshotEntity>> behaviorHistory;
    private final LiveData<BehaviorUsageSummary> todayBehaviorSummary;
    private final LiveData<List<UsageSession>> todayUsageSessions;
    private final LiveData<DailyResetSession> todayResetSession;
    private final LiveData<OnboardingProfile> onboardingProfile;
    private final LiveData<UserBaseline> userBaseline;
    private final LiveData<WellnessSummary> latestWellnessSummary;
    private final LiveData<List<RiskClassification>> classificationHistory7Day;
    private final LiveData<List<JournalEntry>> journalEntries;
    private final LiveData<List<ExerciseCompletion>> exerciseCompletions;
    private final MutableLiveData<Long> screenTime = new MutableLiveData();
    private final MutableLiveData<List<AppUsageModel>> topApps = new MutableLiveData();
    private final MutableLiveData<List<AppUsageModel>> allTrackedApps = new MutableLiveData();
    private final MutableLiveData<AppUsageModel> mostUsedApp = new MutableLiveData();
    private final MutableLiveData<BehaviorReport> currentBehavior = new MutableLiveData();
    private final MutableLiveData<AiInsightResult> aiInsight = new MutableLiveData();
    private final MutableLiveData<String> dashboardLoadError = new MutableLiveData();
    private final MediatorLiveData<DashboardInsights> dashboardInsights = new MediatorLiveData();
    private final MediatorLiveData<HomeScreenState> homeScreenState = new MediatorLiveData();
    private volatile boolean aiRequestInFlight;
    private String lastAiRequestKey = "";
    private BehaviorFeatures latestBehaviorFeatures;
    private AnomalyDetector.AnomalyProfile latestAnomalyProfile;
    private final MutableLiveData<RiskClassification> latestClassification = new MutableLiveData();
    private ClassificationRepository classificationRepository;
    private final AppDatabase appDatabase;
    private final InterventionEngine interventionEngine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable buildInsightsTask = null;
    private Runnable buildUiStateTask = null;

    // ── Extracted UseCases ──
    private final RebuildInsightsUseCase rebuildInsightsUseCase;
    private final BuildForecastUseCase buildForecastUseCase;

    // ── Efficacy Pipeline ──
    private final MutableLiveData<EfficacyMetrics> efficacyMetrics = new MutableLiveData<>(EfficacyMetrics.empty());

    // ── Worker Progress API ──
    private final WorkerProgressTracker workerProgressTracker;
    private final LiveData<String> progressSummary;
    private final LiveData<Boolean> isAnalyzing;

    // ── Error Log Observer ──
    private final LiveData<Integer> recentErrorCount;

    @Inject
    public DashboardViewModel(@NonNull Application application) {
        super(application);
        this.repository = new UsageRepository((Context)application);
        this.dailyResetRepository = new DailyResetRepository((Context)application);
        this.onboardingRepository = new OnboardingRepository((Context)application);
        this.insightEngine = new InsightEngine();
        this.insightEngine.initMultiModalPipeline((Context)application);
        this.classificationRepository = new ClassificationRepository((Context)application);
        this.settingsRepository = new SettingsRepository((Context)application);
        this.behaviorFeatureExtractor = new BehaviorFeatureExtractor();
        this.anomalyDetector = new AnomalyDetector();
        this.aiInsightService = new AiInsightService((Context)application);
        this.onboardingProfileAnalyzer = new OnboardingProfileAnalyzer();
        this.executorService = com.mindtrace.ai.util.AppExecutors.diskIO();
        AppDatabase db = AppDatabase.getInstance((Context)application);
        this.appDatabase = db;
        this.interventionEngine = new InterventionEngine();
        this.rebuildInsightsUseCase = new RebuildInsightsUseCase(
                this.insightEngine, this.behaviorFeatureExtractor,
                this.anomalyDetector, this.interventionEngine,
                db.taskDao(), this.repository);
        this.buildForecastUseCase = new BuildForecastUseCase();
        this.usageHistory = this.repository.getAllUsage();
        this.stateHistory = this.repository.getAllResponses();
        this.activeTasks = this.repository.getActiveTasks();
        this.allTasks = this.repository.getAllTasks();
        this.behaviorHistory = this.repository.getLast7BehaviorSnapshots();
        this.todayBehaviorSummary = this.repository.getTodayBehaviorSummary();
        this.todayUsageSessions = this.repository.getUsageSessionsForDate(this.getStartOfTodayMillis());
        this.todayResetSession = this.dailyResetRepository.getTodaySession();
        this.onboardingProfile = this.onboardingRepository.getProfile();
        this.userBaseline = this.repository.getUserBaseline();
        this.latestWellnessSummary = this.repository.getLatestWellnessSummary();
        this.classificationHistory7Day = this.classificationRepository.observeHistory(7);
        this.journalEntries = db.journalDao().getAllEntries();
        this.exerciseCompletions = db.exerciseCompletionDao().getAll();
        this.dashboardInsights.addSource(this.usageHistory, ignored -> this.scheduleRebuildInsights());
        this.dashboardInsights.addSource(this.stateHistory, ignored -> this.scheduleRebuildInsights());
        this.dashboardInsights.addSource(this.allTasks, ignored -> this.scheduleRebuildInsights());
        this.dashboardInsights.addSource(this.behaviorHistory, ignored -> this.scheduleRebuildInsights());
        this.dashboardInsights.addSource(this.userBaseline, ignored -> this.scheduleRebuildInsights());
        this.dashboardInsights.addSource(this.screenTime, ignored -> this.scheduleRebuildInsights());
        this.dashboardInsights.addSource(this.mostUsedApp, ignored -> this.scheduleRebuildInsights());
        this.dashboardInsights.addSource(this.currentBehavior, ignored -> this.scheduleRebuildInsights());
        this.dashboardInsights.addSource(this.aiInsight, ignored -> this.scheduleRebuildInsights());
        this.homeScreenState.addSource(this.dashboardInsights, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.activeTasks, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.allTasks, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.currentBehavior, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.mostUsedApp, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.todayResetSession, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.onboardingProfile, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.userBaseline, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.classificationHistory7Day, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.journalEntries, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.exerciseCompletions, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.dashboardLoadError, ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.setValue(HomeScreenState.loading(HomeScreenTextHelper.buildGreetingText(false, null), HomeScreenTextHelper.formatCurrentDate()));

        // ── Worker Progress API: observe WorkManager states ──
        this.workerProgressTracker = new WorkerProgressTracker(application);
        this.progressSummary = this.workerProgressTracker.getProgressSummary();
        this.isAnalyzing = this.workerProgressTracker.getIsAnyRunning();
        // Wire progress into homeScreenState rebuild cycle
        this.homeScreenState.addSource(this.workerProgressTracker.getProgressSummary(),
                ignored -> this.scheduleRebuildHomeScreenState());
        this.homeScreenState.addSource(this.isAnalyzing,
                ignored -> this.scheduleRebuildHomeScreenState());

        // ── Error Log Observer: 24h error count for dashboard badge ──
        long twentyFourHoursAgo = System.currentTimeMillis() - (24L * 60 * 60 * 1000);
        this.recentErrorCount = db.errorLogDao().observeErrorCountSince(twentyFourHoursAgo);
        this.homeScreenState.addSource(this.recentErrorCount,
                ignored -> this.scheduleRebuildHomeScreenState());
    }

    public LiveData<List<DailyUsage>> getUsageHistory() {
        return this.usageHistory;
    }

    public LiveData<List<QuestionnaireResponse>> getStateHistory() {
        return this.stateHistory;
    }

    public LiveData<List<InterventionTask>> getActiveTasks() {
        return this.activeTasks;
    }

    public LiveData<List<InterventionTask>> getAllTasks() {
        return this.allTasks;
    }

    public LiveData<Long> getScreenTime() {
        return this.screenTime;
    }

    public LiveData<List<AppUsageModel>> getTopApps() {
        return this.topApps;
    }

    public LiveData<List<AppUsageModel>> getAppUsageList() {
        return this.allTrackedApps;
    }

    public LiveData<AppUsageModel> getMostUsedApp() {
        return this.mostUsedApp;
    }

    public LiveData<BehaviorReport> getCurrentBehavior() {
        return this.currentBehavior;
    }

    public LiveData<List<BehaviorSnapshotEntity>> getBehaviorHistory() {
        return this.behaviorHistory;
    }

    public LiveData<BehaviorUsageSummary> getTodayBehaviorSummary() {
        return this.todayBehaviorSummary;
    }

    public LiveData<List<UsageSession>> getTodayUsageSessions() {
        return this.todayUsageSessions;
    }

    public LiveData<DashboardInsights> getDashboardInsights() {
        return this.dashboardInsights;
    }

    public LiveData<HomeScreenState> getHomeScreenState() {
        return this.homeScreenState;
    }

    public LiveData<WellnessSummary> getLatestWellnessSummary() {
        return this.latestWellnessSummary;
    }

    public LiveData<UserBaseline> getUserBaseline() {
        return this.userBaseline;
    }

    public LiveData<RiskClassification> getLatestClassification() {
        return this.latestClassification;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFICACY PIPELINE — Sentiment-enhanced intervention metrics
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Observable efficacy metrics for the Usage dashboard.
     * Aggregated during each insight rebuild cycle from TaskDao + InterventionEngine.
     * Contains per-category sentiment-adjusted scores, summary text, and observation counts.
     */
    public LiveData<EfficacyMetrics> getEfficacyMetrics() {
        return this.efficacyMetrics;
    }

    public LiveData<List<RiskClassification>> getClassificationHistory(int days) {
        return this.classificationRepository.observeHistory(days);
    }

    public void loadWeeklySummary(Consumer<ClassificationRepository.WeeklyRiskSummary> callback) {
        this.executorService.execute(() -> {
            ClassificationRepository.WeeklyRiskSummary summary = this.classificationRepository.getWeeklySummary();
            if (callback != null) {
                callback.accept(summary);
            }
        });
    }

    public void loadDayComparison(Consumer<ClassificationRepository.DayComparison> callback) {
        this.executorService.execute(() -> {
            ClassificationRepository.DayComparison comparison = this.classificationRepository.getDayOverDayComparison();
            if (callback != null) {
                callback.accept(comparison);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORKER PROGRESS API — Priority 1 (Pipeline Hardening)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Observable list of all workers with their current execution states.
     * UI can use this to render per-worker status indicators.
     */
    public LiveData<java.util.List<WorkerProgressTracker.WorkerStatus>> getWorkerProgress() {
        return this.workerProgressTracker.getActiveWorkers();
    }

    /**
     * Human-readable progress string, e.g. "Classifying risk...",
     * "Syncing usage data + 2 more...", or null when idle.
     * Bind directly to a TextView in the dashboard header.
     */
    public LiveData<String> getProgressSummary() {
        return this.progressSummary;
    }

    /**
     * True if any background worker is currently running.
     * Use to show/hide a global progress indicator (spinner, shimmer, etc.).
     */
    public LiveData<Boolean> getIsAnalyzing() {
        return this.isAnalyzing;
    }

    /**
     * Loads a trend report via {@link com.mindtrace.ai.ai.ClassificationTrendAnalyzer}.
     * Runs on background thread; result delivered via callback.
     *
     * @param days    Number of days to analyze (7 = weekly, 14 = biweekly)
     * @param callback Receives the TrendReport, or null if insufficient data
     */
    public void loadTrendReport(int days,
            Consumer<com.mindtrace.ai.ai.ClassificationTrendAnalyzer.TrendReport> callback) {
        this.executorService.execute(() -> {
            try {
                com.mindtrace.ai.ai.ClassificationTrendAnalyzer analyzer =
                        new com.mindtrace.ai.ai.ClassificationTrendAnalyzer(
                                (Context) this.getApplication());
                com.mindtrace.ai.ai.ClassificationTrendAnalyzer.TrendReport report =
                        analyzer.analyzeTrend(days);
                if (callback != null) {
                    callback.accept(report);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.accept(null);
                }
            }
        });
    }

    public void loadScreenTime() {
        this.executorService.execute(() -> {
            try {
                long time = this.repository.getTodayScreenTime((Context)this.getApplication());
                this.screenTime.postValue(time);
                this.dashboardLoadError.postValue(null);
            }
            catch (Exception e) {
                this.dashboardLoadError.postValue("MindTrace couldn't refresh screen-time signals right now.");
            }
        });
    }

    public void loadAppUsage() {
        this.refreshDashboard();
    }

    public void loadTopApps(int limit, boolean includeSystemApps) {
        this.executorService.execute(() -> {
            try {
                List<AppUsageModel> apps = this.repository.getTopUsedApps((Context)this.getApplication(), limit, includeSystemApps);
                this.topApps.postValue(apps);
                this.mostUsedApp.postValue(apps.isEmpty() ? null : apps.get(0));
                this.dashboardLoadError.postValue(null);
            }
            catch (Exception e) {
                this.dashboardLoadError.postValue("App usage insights are temporarily unavailable.");
            }
        });
    }

    public void refreshDashboard() {
        SettingsRepository.SettingsState settingsState = this.settingsRepository.getSettingsState();
        if (!settingsState.trackingEnabled) {
            this.dashboardLoadError.postValue(null);
            this.screenTime.postValue(0L);
            this.topApps.postValue(Collections.emptyList());
            this.allTrackedApps.postValue(Collections.emptyList());
            this.mostUsedApp.postValue(null);
            this.currentBehavior.postValue(BehaviorReport.unavailable(System.currentTimeMillis(), System.currentTimeMillis(), "Behavior tracking is turned off in Settings."));
            this.aiInsight.postValue(null);
            this.lastAiRequestKey = "";
            return;
        }
        this.executorService.execute(() -> {
            try {
                UsageRepository.DashboardSnapshot snapshot = this.repository.refreshTodayUsageSnapshot((Context)this.getApplication(), settingsState.includeSystemApps);
                this.screenTime.postValue(snapshot.screenTimeMillis);
                this.topApps.postValue(snapshot.topApps);
                this.allTrackedApps.postValue(snapshot.allApps);
                this.mostUsedApp.postValue(snapshot.mostUsedApp);
                this.currentBehavior.postValue(snapshot.behaviorReport);
                this.dashboardLoadError.postValue(null);
            }
            catch (Exception e) {
                this.dashboardLoadError.postValue("Something went wrong while refreshing today's dashboard.");
            }
        });
    }

    private void scheduleRebuildInsights() {
        if (this.buildInsightsTask != null) {
            this.mainHandler.removeCallbacks(this.buildInsightsTask);
        }
        this.buildInsightsTask = () -> this.executorService.execute(this::rebuildInsightsAsync);
        this.mainHandler.postDelayed(this.buildInsightsTask, 200L);
    }

    private void rebuildInsightsAsync() {
        // Delegate to extracted UseCase
        RebuildInsightsUseCase.InsightResult result = this.rebuildInsightsUseCase.execute(
                (Long) this.screenTime.getValue(),
                (AppUsageModel) this.mostUsedApp.getValue(),
                (List) this.usageHistory.getValue(),
                (List) this.stateHistory.getValue(),
                (List) this.allTasks.getValue(),
                (UserBaseline) this.userBaseline.getValue(),
                (BehaviorReport) this.currentBehavior.getValue(),
                (List) this.behaviorHistory.getValue(),
                (AiInsightResult) this.aiInsight.getValue(),
                this.getStartOfTodayMillis());

        this.latestBehaviorFeatures = result.behaviorFeatures;
        this.latestAnomalyProfile = result.anomalyProfile;
        this.dashboardInsights.postValue(result.insights);
        if (result.insights != null && result.insights.classification != null) {
            this.latestClassification.postValue(result.insights.classification);
        }
        this.efficacyMetrics.postValue(result.efficacyMetrics);

        this.mainHandler.post(this::requestAiEnhancementIfNeeded);
        this.scheduleRebuildHomeScreenState();
    }

    private void scheduleRebuildHomeScreenState() {
        if (this.buildUiStateTask != null) {
            this.mainHandler.removeCallbacks(this.buildUiStateTask);
        }
        this.buildUiStateTask = () -> this.executorService.execute(() -> {
            HomeScreenState state = this.buildHomeScreenState();
            this.homeScreenState.postValue(state);
        });
        this.mainHandler.postDelayed(this.buildUiStateTask, 150L);
    }

    private HomeScreenState buildHomeScreenState() {
        boolean showSupportStrip;
        boolean loading;
        DashboardInsights insights = (DashboardInsights)this.dashboardInsights.getValue();
        BehaviorReport behaviorReport = (BehaviorReport)this.currentBehavior.getValue();
        AppUsageModel topApp = (AppUsageModel)this.mostUsedApp.getValue();
        List activeTaskList = (List)this.activeTasks.getValue();
        List allTaskList = (List)this.allTasks.getValue();
        List responses = (List)this.stateHistory.getValue();
        List journalList = (List)this.journalEntries.getValue();
        List exerciseList = (List)this.exerciseCompletions.getValue();
        DailyResetSession resetSession = (DailyResetSession)this.todayResetSession.getValue();
        OnboardingProfile profile = (OnboardingProfile)this.onboardingProfile.getValue();
        OnboardingAssessment onboardingAssessment = this.buildOnboardingAssessment(profile);
        UserBaseline baseline = (UserBaseline)this.userBaseline.getValue();
        SettingsRepository.SettingsState settingsState = this.settingsRepository.getSettingsState();
        QuestionnaireResponse latestResponse = this.getLatestResponse(responses);
        boolean baselineReady = baseline != null && baseline.avgScreenTime7d > 0.0;
        boolean bl = loading = insights == null && behaviorReport == null && activeTaskList == null && allTaskList == null && resetSession == null && topApp == null && profile == null;
        if (loading) {
            return HomeScreenState.loading(HomeScreenTextHelper.buildGreetingText(false, null), HomeScreenTextHelper.formatCurrentDate());
        }
        boolean hasData = insights != null || behaviorReport != null && behaviorReport.dataAvailable || topApp != null || allTaskList != null && !allTaskList.isEmpty() || resetSession != null || profile != null;
        boolean showEmptyState = this.shouldShowFirstTimeEmptyState(allTaskList, resetSession);
        int riskIndex = this.resolveRiskIndex(insights, behaviorReport, onboardingAssessment);
        boolean highRisk = riskIndex >= 70 || insights != null && insights.supportRecommended || onboardingAssessment != null && onboardingAssessment.supportRecommended;
        BuildMissionContentUseCase.MissionContent missionContent = new BuildMissionContentUseCase().execute(activeTaskList, allTaskList, insights, behaviorReport, resetSession, onboardingAssessment, settingsState.privacyMode, this.getStartOfTodayMillis());
        BuildNextActionUseCase.NextActionContent nextActionContent = new BuildNextActionUseCase().execute(insights, behaviorReport, topApp, resetSession, profile, onboardingAssessment, settingsState.privacyMode, hasData, highRisk);
        showSupportStrip = profile != null && profile.safetySupportEnabled && (highRisk || onboardingAssessment != null && onboardingAssessment.supportRecommended);
        if (!showSupportStrip && behaviorReport != null) {
            showSupportStrip = highRisk || behaviorReport.rapidSwitchCount >= 8 || behaviorReport.lateNightUsageMillis >= 1200000L || behaviorReport.hasLoopPattern;
        }
        List<HomeScreenState.WarningCardItem> warningCardItems = new BuildWarningCardsUseCase().execute(insights, behaviorReport, topApp, onboardingAssessment, settingsState.privacyMode);
        String loadErrorMessage = (String)this.dashboardLoadError.getValue();
        boolean showErrorState = loadErrorMessage != null && !loadErrorMessage.trim().isEmpty() && !showEmptyState;
        int productiveMinutes = 0;
        int passiveMinutes = 0;
        int bingeCount = 0;
        if (allTaskList != null) {
            for (AppUsageModel appUsageModel : this.topApps != null ? java.util.Collections.<com.mindtrace.ai.AppUsageModel>singletonList(topApp) : java.util.Collections.<com.mindtrace.ai.AppUsageModel>emptyList()) {
            }
        }
        if (behaviorReport != null) {
            bingeCount = behaviorReport.bingeSessionCount;
            long totalMin = (this.screenTime.getValue() == null ? 0L : (Long)this.screenTime.getValue()) / 60000L;
            // activeVsPassiveRatio = passiveTime/activeTime, so derive fraction correctly
            double ratio = behaviorReport.activeVsPassiveRatio;
            double passiveFraction = ratio > 0 ? ratio / (1.0 + ratio) : 0.5;
            passiveFraction = Math.max(0.0, Math.min(1.0, passiveFraction));
            passiveMinutes = (int)((double)totalMin * passiveFraction);
            productiveMinutes = (int)(totalMin - passiveMinutes);
        }

        // ── Efficacy data pipeline ──────────────────────────────────
        // Query the DB for closed-loop efficacy metrics (safe for main thread
        // because these are lightweight aggregate queries cached by Room).
        String efficacySummary = null;
        String mostEffectiveCat = null;
        int observingCount = 0;
        try {
            TaskDao taskDao = this.appDatabase.taskDao();
            long now = System.currentTimeMillis();
            long sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000);

            // Tasks currently being observed (awaiting post-intervention reading)
            List<InterventionTask> observing = taskDao.getTasksInObservationWindow(now);
            observingCount = observing != null ? observing.size() : 0;

            // Most efficacious category from historical data
            mostEffectiveCat = taskDao.getMostEfficaciousCategory();

            // Build human-readable efficacy summary from recent measured tasks
            List<InterventionTask> recentMeasured = taskDao.getTasksWithEfficacySince(sevenDaysAgo);
            if (recentMeasured != null && !recentMeasured.isEmpty()) {
                String[] summaries = this.interventionEngine.getEfficacySummary(recentMeasured);
                if (summaries.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < summaries.length; i++) {
                        if (i > 0) sb.append(" · ");
                        sb.append(summaries[i]);
                    }
                    efficacySummary = sb.toString();
                }
            }
        } catch (Exception e) {
            // Efficacy data is non-critical; degrade gracefully
            efficacySummary = null;
            mostEffectiveCat = null;
            observingCount = 0;
        }

        long screenTimeValue = this.screenTime.getValue() == null ? 0L : (Long)this.screenTime.getValue();

        // ── Build via fluent Builder API ─────────────────────────────
        return HomeScreenState.builder()
                // Header
                .greetingText(HomeScreenTextHelper.buildGreetingText(highRisk, profile))
                .dateText(HomeScreenTextHelper.formatCurrentDate())
                .wellnessLabel(this.buildWellnessLabel(insights, hasData, baselineReady, highRisk, onboardingAssessment))
                .riskIndex(riskIndex)
                .riskHistory(this.buildForecastUseCase.buildRiskTrend((List)this.classificationHistory7Day.getValue(), riskIndex))
                .screenTimeDeviation(this.calculateScreenTimeDeviation(screenTimeValue, baseline))
                .riskSummary(this.buildRiskSummary(insights, behaviorReport, topApp, resetSession, onboardingAssessment, settingsState.privacyMode, hasData, highRisk))
                .baselineComparisonText(this.buildBaselineComparison(insights, baselineReady, profile))
                // Mission
                .missionTitle(missionContent.title)
                .missionSteps(missionContent.steps)
                .missionStepItems(missionContent.stepItems)
                .missionProgressText(missionContent.progressText)
                .missionProgressPercent(missionContent.progressPercent)
                // Actions
                .primaryActionLabel(this.buildPrimaryActionLabel(resetSession))
                .primaryActionType(this.buildPrimaryActionType())
                // Warnings & Insights
                .warningItems(new BuildWarningCardsUseCase().buildWarningItems(insights, behaviorReport, topApp, onboardingAssessment, settingsState.privacyMode))
                .warningCardItems(warningCardItems)
                .aiInsightItems(new BuildInsightCardsUseCase().execute(insights, behaviorReport, latestResponse, highRisk, hasData))
                // Cards
                .patternRadarCard(new BuildPatternRadarCardUseCase().execute(behaviorReport, topApp, settingsState.privacyMode, hasData, riskIndex))
                .focusWindowCard(new BuildFocusWindowCardUseCase().execute(insights, behaviorReport, topApp, latestResponse, resetSession, onboardingAssessment, settingsState.privacyMode, hasData, highRisk, riskIndex))
                .forecastCard(this.buildForecastUseCase.execute((List)this.classificationHistory7Day.getValue(), behaviorReport, screenTimeValue, baseline, latestResponse, riskIndex))
                // Completion flags
                .hasCheckedInToday(this.hasCheckInToday(responses))
                .hasJournalEntryToday(this.hasJournalEntryToday(journalList))
                .hasExerciseToday(this.hasExerciseToday(exerciseList))
                // Next action
                .nextBestActionTitle(nextActionContent.title)
                .nextBestActionReason(nextActionContent.reason)
                .nextBestActionETA(nextActionContent.eta)
                // Support
                .showSupportStrip(showSupportStrip)
                .supportStripText(this.buildSupportStripText(behaviorReport, topApp, profile, onboardingAssessment, settingsState.privacyMode))
                // Error
                .showErrorState(showErrorState)
                .errorTitle("Something went wrong")
                .errorMessage(showErrorState ? loadErrorMessage : "")
                // State flags
                .isLoading(false)
                .hasData(hasData)
                .isHighRisk(highRisk)
                .isBaselineReady(baselineReady)
                .showEmptyState(showEmptyState)
                // Efficacy (closed-loop observation data)
                .efficacySummaryText(efficacySummary)
                .mostEffectiveCategory(mostEffectiveCat)
                .tasksInObservationWindow(observingCount)
                // Worker progress & error monitoring
                .progressSummaryText(this.progressSummary.getValue())
                .isAnalyzing(Boolean.TRUE.equals(this.isAnalyzing.getValue()))
                .activeWorkerCount(this.resolveActiveWorkerCount())
                .recentErrorCount(this.recentErrorCount.getValue() != null
                        ? this.recentErrorCount.getValue() : 0)
                .build();
    }

    /**
     * Count active (RUNNING or ENQUEUED) workers from the tracker's latest snapshot.
     * Returns 0 if no data is available yet, avoiding NPE in the builder.
     */
    private int resolveActiveWorkerCount() {
        java.util.List<WorkerProgressTracker.WorkerStatus> workers =
                this.workerProgressTracker.getActiveWorkers().getValue();
        if (workers == null || workers.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WorkerProgressTracker.WorkerStatus ws : workers) {
            if (ws.isActive()) {
                count++;
            }
        }
        return count;
    }

    private boolean shouldShowFirstTimeEmptyState(List<InterventionTask> allTaskList, DailyResetSession resetSession) {
        OnboardingProfile profile = (OnboardingProfile)this.onboardingProfile.getValue();
        if (profile != null && profile.onboardingComplete) {
            return false;
        }
        List usageEntries = (List)this.usageHistory.getValue();
        List responses = (List)this.stateHistory.getValue();
        List snapshots = (List)this.behaviorHistory.getValue();
        boolean hasUsageHistory = usageEntries != null && !usageEntries.isEmpty();
        boolean hasResponses = responses != null && !responses.isEmpty();
        boolean hasTasks = allTaskList != null && !allTaskList.isEmpty();
        boolean hasReset = resetSession != null;
        boolean hasBehaviorSnapshots = snapshots != null && !snapshots.isEmpty();
        return !hasUsageHistory && !hasResponses && !hasTasks && !hasReset && !hasBehaviorSnapshots;
    }

    private void requestAiEnhancementIfNeeded() {
        if (!this.aiInsightService.isConfigured()) {
            return;
        }
        AiInsightInput input = this.buildAiInsightInput();
        if (input == null) {
            return;
        }
        String requestKey = this.buildAiRequestKey(input);
        if (this.aiRequestInFlight || requestKey.equals(this.lastAiRequestKey)) {
            return;
        }
        this.lastAiRequestKey = requestKey;
        this.aiRequestInFlight = true;
        this.executorService.execute(() -> {
            try {
                AiInsightResult result = this.aiInsightService.fetchAiInsight(input);
                if (result != null) {
                    this.aiInsight.postValue(result);
                }
            }
            finally {
                this.aiRequestInFlight = false;
            }
        });
    }

    private AiInsightInput buildAiInsightInput() {
        BehaviorReport behaviorReport = (BehaviorReport)this.currentBehavior.getValue();
        if (behaviorReport == null || !behaviorReport.dataAvailable) {
            return null;
        }
        QuestionnaireResponse latestResponse = this.getLatestResponse((List)this.stateHistory.getValue());
        AppUsageModel topApp = (AppUsageModel)this.mostUsedApp.getValue();
        AiInsightInput input = new AiInsightInput();
        input.screenTimeToday = this.screenTime.getValue() == null ? 0L : (Long)this.screenTime.getValue();
        input.screenTimeDeviation = this.calculateScreenTimeDeviation(input.screenTimeToday, (UserBaseline)this.userBaseline.getValue());
        input.fragmentationLevel = this.resolveFragmentationLevel(behaviorReport);
        input.rapidSwitchCount = behaviorReport.rapidSwitchCount;
        input.bingeFlag = behaviorReport.bingeSessionCount > 0 || behaviorReport.longestSessionMillis >= 2700000L;
        input.lateNightUsage = behaviorReport.lateNightUsageMillis;
        input.mood = latestResponse == null ? "Unknown" : latestResponse.mood;
        input.stressLevel = latestResponse == null ? 0 : latestResponse.stressLevel;
        input.sleepHours = latestResponse == null ? 0.0f : latestResponse.sleepHours;
        input.distressFlags = latestResponse == null || latestResponse.distressFlags == null ? "None" : latestResponse.distressFlags;
        input.taskCompletionRate = this.calculateFulfillmentScore((List)this.allTasks.getValue());
        input.dominantApp = topApp == null ? behaviorReport.dominantAppPackage : topApp.appName;
        List journals = (List)this.journalEntries.getValue();
        if (journals != null && !journals.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int count = Math.min(3, journals.size());
            for (int i = 0; i < count; ++i) {
                if (((JournalEntry)journals.get((int)i)).content == null) continue;
                sb.append(((JournalEntry)journals.get((int)i)).content).append(" | ");
            }
            input.recentJournalEntries = sb.toString();
        } else {
            input.recentJournalEntries = "None";
        }
        return input;
    }

    private double calculateScreenTimeDeviation(long todayScreenTime, UserBaseline baseline) {
        if (baseline == null || baseline.avgScreenTime7d <= 0.0) {
            return 0.0;
        }
        return (double)todayScreenTime / baseline.avgScreenTime7d - 1.0;
    }

    private String resolveFragmentationLevel(BehaviorReport behaviorReport) {
        if (behaviorReport == null) {
            return "low";
        }
        if (behaviorReport.rapidSwitchCount >= 12 || behaviorReport.appSwitchCount >= 30) {
            return "high";
        }
        if (behaviorReport.rapidSwitchCount >= 6 || behaviorReport.appSwitchCount >= 15) {
            return "moderate";
        }
        return "low";
    }

    private String buildAiRequestKey(AiInsightInput input) {
        return this.getStartOfTodayMillis() + "|" + input.screenTimeToday + "|" + input.rapidSwitchCount + "|" + input.fragmentationLevel + "|" + input.bingeFlag + "|" + input.lateNightUsage + "|" + input.taskCompletionRate + "|" + input.stressLevel;
    }

    private QuestionnaireResponse getLatestResponse(List<QuestionnaireResponse> responses) {
        return responses == null || responses.isEmpty() ? null : responses.get(0);
    }

    private int calculateFulfillmentScore(List<InterventionTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int completed = 0;
        for (InterventionTask task : tasks) {
            if (!task.isCompleted) continue;
            ++completed;
        }
        return Math.round((float)completed * 100.0f / (float)tasks.size());
    }

    private int resolveRiskIndex(DashboardInsights insights, BehaviorReport behaviorReport, OnboardingAssessment onboardingAssessment) {
        boolean isBaselineReady;
        UserBaseline baseline = (UserBaseline)this.userBaseline.getValue();
        boolean bl = isBaselineReady = baseline != null && baseline.isReady();
        if (insights != null && insights.personalized) {
            int score = HomeScreenTextHelper.clamp(Math.round((float)(insights.personalizedRiskScore * 100.0)), 0, 100);
            if (!isBaselineReady && onboardingAssessment != null) {
                return HomeScreenTextHelper.clamp((score + onboardingAssessment.riskIndex * 2) / 3, 0, 100);
            }
            return score;
        }
        if (behaviorReport == null || !behaviorReport.dataAvailable) {
            return onboardingAssessment == null ? 24 : onboardingAssessment.riskIndex;
        }
        // Always compute proportional score from real behavior signals
        int score = 22;
        if (behaviorReport.rapidSwitchCount >= 12) {
            score += 28;
        } else if (behaviorReport.rapidSwitchCount >= 6) {
            score += 16;
        } else if (behaviorReport.rapidSwitchCount >= 3) {
            score += 8;
        }
        if (behaviorReport.bingeSessionCount > 0) {
            score += Math.min(24, behaviorReport.bingeSessionCount * 9);
        }
        if (behaviorReport.lateNightUsageMillis >= 1800000L) {
            score += 24;
        } else if (behaviorReport.lateNightUsageMillis >= 600000L) {
            score += 10;
        } else if (behaviorReport.lateNightUsageMillis >= 300000L) {
            score += 5;
        }
        if (behaviorReport.hasLoopPattern) {
            score += 12;
        }
        if (behaviorReport.dominantUsageRatio >= 0.6) {
            score += 6;
        }
        // Use InsightEngine riskLevel as a floor (don't let proportional go below the AI classification)
        if (insights != null) {
            int floor = 0;
            switch (insights.riskLevel) {
                case HIGH:     floor = 70; break;
                case MODERATE: floor = 40; break;
            }
            score = Math.max(score, floor);
        }
        if (onboardingAssessment != null) {
            return HomeScreenTextHelper.clamp((score + onboardingAssessment.riskIndex) / 2, 0, 100);
        }
        return HomeScreenTextHelper.clamp(score, 0, 100);
    }

    private OnboardingAssessment buildOnboardingAssessment(OnboardingProfile profile) {
        return profile == null ? null : this.onboardingProfileAnalyzer.assess(profile);
    }



    private String buildWellnessLabel(DashboardInsights insights, boolean hasData, boolean baselineReady, boolean highRisk, OnboardingAssessment onboardingAssessment) {
        if (!hasData) {
            return "Learning Mode";
        }
        if (!baselineReady) {
            if (onboardingAssessment != null) {
                return onboardingAssessment.wellnessLabel;
            }
            return "Baseline Building";
        }
        if (highRisk) {
            return "High Risk";
        }
        if (insights != null && insights.riskLevel == DashboardInsights.RiskLevel.MODERATE) {
            return "Watch Mode";
        }
        return "Steady State";
    }

    private String buildRiskSummary(DashboardInsights insights, BehaviorReport behaviorReport, AppUsageModel topApp, DailyResetSession resetSession, OnboardingAssessment onboardingAssessment, boolean privacyMode, boolean hasData, boolean highRisk) {
        if (!hasData) {
            return "We are learning your behavior. Complete one check-in to unlock personalized guidance.";
        }
        if (resetSession != null && resetSession.isCompleted) {
            if (resetSession.completedAt > 0L) {
                return "Today's reset was completed at " + HomeScreenTextHelper.formatTimeOfDay(resetSession.completedAt) + ". Protect the structure you just started.";
            }
            return "Today's reset is complete. Protect the structure you just started.";
        }
        if (onboardingAssessment != null && !this.hasEnoughSignalsForSummary(behaviorReport, topApp)) {
            return HomeScreenTextHelper.trimSentence(onboardingAssessment.summary, 120);
        }
        if (insights != null && insights.aiEnhanced && insights.aiSummary != null && !insights.aiSummary.trim().isEmpty()) {
            return HomeScreenTextHelper.trimSentence(insights.aiSummary, 120);
        }
        if (highRisk && behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L) {
            return "Last night's phone use can weaken today unless you protect the next block.";
        }
        if (highRisk && behaviorReport != null && behaviorReport.rapidSwitchCount >= 8) {
            return "Your attention is vulnerable to fast switching today. A clean first block matters.";
        }
        if (insights != null && insights.primaryInsight != null && !insights.primaryInsight.trim().isEmpty()) {
            return HomeScreenTextHelper.trimSentence(insights.primaryInsight, 120);
        }
        if (topApp != null && topApp.usageTime >= 2700000L) {
            return "Your day gets harder if " + HomeScreenTextHelper.resolveTopAppLabel(topApp, privacyMode) + " gets the first tap.";
        }
        return "Small disciplined actions today will protect tomorrow.";
    }

    private boolean hasEnoughSignalsForSummary(BehaviorReport behaviorReport, AppUsageModel topApp) {
        return behaviorReport != null && behaviorReport.dataAvailable || topApp != null;
    }

    private String buildBaselineComparison(DashboardInsights insights, boolean baselineReady, OnboardingProfile profile) {
        if (!baselineReady) {
            if (profile != null) {
                return "Baseline building... MindTrace is using your onboarding profile while it learns your real routine.";
            }
            return "Baseline building... A few more days of data will improve your guidance.";
        }
        if (insights != null && insights.personalizedComparisons != null && !insights.personalizedComparisons.trim().isEmpty()) {
            return HomeScreenTextHelper.trimSentence(insights.personalizedComparisons, 115);
        }
        return "Your baseline is ready, so today's guidance is now tied to your own pattern.";
    }

    private String buildPrimaryActionLabel(DailyResetSession resetSession) {
        if (resetSession != null && resetSession.isCompleted) {
            return "Reset Completed Today";
        }
        return "Begin My Reset";
    }

    private String buildPrimaryActionType() {
        return "reset";
    }

    private boolean hasCheckInToday(List<QuestionnaireResponse> responses) {
        QuestionnaireResponse latestResponse = this.getLatestResponse(responses);
        return latestResponse != null && latestResponse.dayTimestamp >= this.getStartOfTodayMillis();
    }

    private boolean hasJournalEntryToday(List<JournalEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        long startOfToday = this.getStartOfTodayMillis();
        for (JournalEntry entry : entries) {
            if (entry == null || entry.dayTimestamp < startOfToday && entry.timestamp < startOfToday) continue;
            return true;
        }
        return false;
    }

    private boolean hasExerciseToday(List<ExerciseCompletion> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        long startOfToday = this.getStartOfTodayMillis();
        for (ExerciseCompletion entry : entries) {
            if (entry == null || entry.completedAt < startOfToday) continue;
            return true;
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String buildSupportStripText(BehaviorReport behaviorReport, AppUsageModel topApp, OnboardingProfile profile, OnboardingAssessment onboardingAssessment, boolean privacyMode) {
        if (onboardingAssessment != null && onboardingAssessment.supportRecommended && profile != null && profile.safetySupportEnabled) {
            return "Support tools are visible because your setup suggests the day may feel heavier at times. Use them early, not only when things spiral.";
        }
        if (behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L) {
            return "Hard start today? Open Support and stabilize the next 10 minutes before the spiral builds.";
        }
        if (topApp != null && topApp.usageTime >= 2700000L) {
            return "If " + HomeScreenTextHelper.resolveTopAppLabel(topApp, privacyMode) + " keeps pulling you back, open Support for a quick reset.";
        }
        return "If the day already feels heavy, open Support before the drift turns into a spiral.";
    }

    private String resolveTopAppLabel(AppUsageModel topApp, boolean privacyMode) {
        if (privacyMode || topApp == null || topApp.appName == null || topApp.appName.trim().isEmpty()) {
            return "your top app";
        }
        return topApp.appName.trim();
    }

    private void addIfMissing(List<String> items, String item) {
        if (items.contains(item)) {
            return;
        }
        items.add(item);
    }

    private String resolveFirstName(OnboardingProfile profile) {
        if (profile == null || profile.name == null) {
            return "";
        }
        String trimmed = profile.name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int firstSpace = trimmed.indexOf(32);
        return firstSpace > 0 ? trimmed.substring(0, firstSpace) : trimmed;
    }

    private String joinGreeting(String firstName, String greeting, String message) {
        if (firstName == null || firstName.trim().isEmpty()) {
            return greeting + ". " + message;
        }
        return greeting + ", " + firstName.trim() + ". " + message;
    }

    private String resolveEta(String actionText) {
        String lower;
        String string = lower = actionText == null ? "" : actionText.toLowerCase(Locale.getDefault());
        if (lower.contains("30")) {
            return "About 30 min";
        }
        if (lower.contains("25")) {
            return "About 25 min";
        }
        if (lower.contains("2-minute") || lower.contains("2 minute")) {
            return "About 2 min";
        }
        if (lower.contains("walk")) {
            return "About 10 min";
        }
        return "Start now";
    }

    private String trimSentence(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String trimmed = HomeScreenTextHelper.cleanDashboardCopy(text);
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private String cleanDashboardCopy(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replace("LLM enhancement unavailable, using deterministic insight output.", "MindTrace is using your recent patterns to generate a local wellness insight.").replace("you is", "you are").replace("You is", "You are").replace("you was", "you were").replace("You was", "You were").replaceAll("\\s+", " ");
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private long getStartOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(11, 0);
        calendar.set(12, 0);
        calendar.set(13, 0);
        calendar.set(14, 0);
        return calendar.getTimeInMillis();
    }

    protected void onCleared() {
        super.onCleared();
        // this.executorService is AppExecutors.diskIO() — never shut down the shared pool
    }
}
