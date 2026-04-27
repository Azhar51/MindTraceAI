package com.mindtrace.ai.viewmodel;

import android.app.Application;
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
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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

    // ── Efficacy Pipeline ──
    private final MutableLiveData<EfficacyMetrics> efficacyMetrics = new MutableLiveData<>(EfficacyMetrics.empty());

    // ── Worker Progress API ──
    private final WorkerProgressTracker workerProgressTracker;
    private final LiveData<String> progressSummary;
    private final LiveData<Boolean> isAnalyzing;

    // ── Error Log Observer ──
    private final LiveData<Integer> recentErrorCount;

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
        this.executorService = Executors.newSingleThreadExecutor();
        AppDatabase db = AppDatabase.getInstance((Context)application);
        this.appDatabase = db;
        this.interventionEngine = new InterventionEngine();
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
        this.homeScreenState.setValue(HomeScreenState.loading(this.buildGreetingText(false, null), this.formatCurrentDate()));

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
        AnomalyDetector.AnomalyProfile profile;
        BehaviorFeatures features;
        List currentAllTasks = (List)this.allTasks.getValue();
        BehaviorReport currentReport = (BehaviorReport)this.currentBehavior.getValue();
        List currentTopApps = (List)this.topApps.getValue();
        Long currentScreenTime = (Long)this.screenTime.getValue();
        List currentUsageHistory = (List)this.usageHistory.getValue();
        List currentStateHistory = (List)this.stateHistory.getValue();
        UserBaseline currentBaseline = (UserBaseline)this.userBaseline.getValue();
        AppUsageModel currentMostUsedApp = (AppUsageModel)this.mostUsedApp.getValue();
        List currentBehaviorHistory = (List)this.behaviorHistory.getValue();
        AiInsightResult currentAiInsight = (AiInsightResult)this.aiInsight.getValue();
        int fulfillmentScore = this.calculateFulfillmentScore(currentAllTasks);
        this.latestBehaviorFeatures = features = this.behaviorFeatureExtractor.extractFeatures(currentReport, currentTopApps, fulfillmentScore);
        this.latestAnomalyProfile = profile = this.anomalyDetector.buildProfile(currentScreenTime, currentUsageHistory, this.getLatestResponse(currentStateHistory), currentStateHistory, fulfillmentScore, currentAllTasks, currentBaseline);
        DashboardInsights insights = this.insightEngine.buildInsights(currentScreenTime, currentMostUsedApp, currentUsageHistory, currentStateHistory, currentAllTasks, currentBaseline, currentReport, currentBehaviorHistory, features, profile, currentAiInsight);
        this.dashboardInsights.postValue(insights);
        if (insights != null && insights.classification != null) {
            this.latestClassification.postValue(insights.classification);
        }
        if (insights != null) {
            WellnessSummary summary = new WellnessSummary();
            summary.dayTimestamp = this.getStartOfTodayMillis();
            summary.createdAt = System.currentTimeMillis();
            summary.wellnessState = insights.stateLabel;
            summary.riskLevel = insights.riskLabel;
            summary.explanationText = insights.explanationText;
            summary.reasonSummary = insights.reasonSummary;
            summary.nextBestAction = insights.nextBestAction;
            summary.screenTimeMillis = currentScreenTime == null ? 0L : currentScreenTime;
            summary.taskCompletionScore = insights.fulfillmentScore;
            summary.topAppName = insights.topAppName;
            summary.topAppPackage = currentMostUsedApp == null ? null : currentMostUsedApp.packageName;
            summary.supportSuggested = insights.supportRecommended;
            this.repository.saveWellnessSummary(summary);
        }

        // ── Efficacy aggregation ──
        try {
            TaskDao taskDao = this.appDatabase.taskDao();
            long now = System.currentTimeMillis();

            // How many tasks are currently being observed?
            List<InterventionTask> observing = taskDao.getTasksInObservationWindow(now);
            int observingCount = observing != null ? observing.size() : 0;

            // Overall statistics from DAO
            int measuredCount = taskDao.getMeasuredEfficacyCount();
            float overallAvg = taskDao.getOverallAverageEfficacy();
            String bestCategory = taskDao.getMostEfficaciousCategory();

            // Sentiment-enhanced map and summaries via InterventionEngine
            long sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000);
            List<InterventionTask> recentMeasured = taskDao.getTasksWithEfficacySince(sevenDaysAgo);
            java.util.Map<String, Float> categoryScores =
                    this.interventionEngine.buildSentimentEnhancedEfficacyMap(recentMeasured);
            String[] summaryLines =
                    this.interventionEngine.getSentimentEnhancedSummary(recentMeasured);

            EfficacyMetrics metrics = new EfficacyMetrics(
                    categoryScores, summaryLines, measuredCount,
                    overallAvg, bestCategory, observingCount);
            this.efficacyMetrics.postValue(metrics);
        } catch (Exception ignored) {
            // Don't let efficacy aggregation crash the insight pipeline
        }

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
            return HomeScreenState.loading(this.buildGreetingText(false, null), this.formatCurrentDate());
        }
        boolean hasData = insights != null || behaviorReport != null && behaviorReport.dataAvailable || topApp != null || allTaskList != null && !allTaskList.isEmpty() || resetSession != null || profile != null;
        boolean showEmptyState = this.shouldShowFirstTimeEmptyState(allTaskList, resetSession);
        int riskIndex = this.resolveRiskIndex(insights, behaviorReport, onboardingAssessment);
        boolean highRisk = riskIndex >= 70 || insights != null && insights.supportRecommended || onboardingAssessment != null && onboardingAssessment.supportRecommended;
        MissionContent missionContent = this.buildMissionContent(activeTaskList, allTaskList, insights, behaviorReport, resetSession, onboardingAssessment, settingsState.privacyMode);
        NextActionContent nextActionContent = this.buildNextActionContent(insights, behaviorReport, topApp, resetSession, profile, onboardingAssessment, settingsState.privacyMode, hasData, highRisk);
        showSupportStrip = profile != null && profile.safetySupportEnabled && (highRisk || onboardingAssessment != null && onboardingAssessment.supportRecommended);
        if (!showSupportStrip && behaviorReport != null) {
            showSupportStrip = highRisk || behaviorReport.rapidSwitchCount >= 8 || behaviorReport.lateNightUsageMillis >= 1200000L || behaviorReport.hasLoopPattern;
        }
        List<HomeScreenState.WarningCardItem> warningCardItems = this.buildWarningCardItems(insights, behaviorReport, topApp, onboardingAssessment, settingsState.privacyMode);
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
                .greetingText(this.buildGreetingText(highRisk, profile))
                .dateText(this.formatCurrentDate())
                .wellnessLabel(this.buildWellnessLabel(insights, hasData, baselineReady, highRisk, onboardingAssessment))
                .riskIndex(riskIndex)
                .riskHistory(this.buildRiskTrend((List)this.classificationHistory7Day.getValue(), riskIndex))
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
                .warningItems(this.buildWarningItems(insights, behaviorReport, topApp, onboardingAssessment, settingsState.privacyMode))
                .warningCardItems(warningCardItems)
                .aiInsightItems(this.buildAiInsightItems(insights, behaviorReport, latestResponse, highRisk, hasData))
                // Cards
                .patternRadarCard(this.buildPatternRadarCard(behaviorReport, topApp, settingsState.privacyMode, hasData, riskIndex))
                .focusWindowCard(this.buildFocusWindowCard(insights, behaviorReport, topApp, latestResponse, resetSession, onboardingAssessment, settingsState.privacyMode, hasData, highRisk, riskIndex))
                .forecastCard(this.buildForecastCard((List)this.classificationHistory7Day.getValue(), behaviorReport, screenTimeValue, baseline, latestResponse, riskIndex))
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
            int score = this.clamp(Math.round((float)(insights.personalizedRiskScore * 100.0)), 0, 100);
            if (!isBaselineReady && onboardingAssessment != null) {
                return this.clamp((score + onboardingAssessment.riskIndex * 2) / 3, 0, 100);
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
            return this.clamp((score + onboardingAssessment.riskIndex) / 2, 0, 100);
        }
        return this.clamp(score, 0, 100);
    }

    private OnboardingAssessment buildOnboardingAssessment(OnboardingProfile profile) {
        return profile == null ? null : this.onboardingProfileAnalyzer.assess(profile);
    }

    private String buildGreetingText(boolean highRisk, OnboardingProfile profile) {
        int hour = Calendar.getInstance().get(11);
        String firstName = this.resolveFirstName(profile);
        if (hour < 12) {
            return highRisk ? this.joinGreeting(firstName, "Good morning", "Protect the first hour.") : this.joinGreeting(firstName, "Good morning", "Today is a fresh start.");
        }
        if (hour < 17) {
            return highRisk ? this.joinGreeting(firstName, "Good afternoon", "Reset the drift now.") : this.joinGreeting(firstName, "Good afternoon", "Reclaim the rest of the day.");
        }
        return highRisk ? this.joinGreeting(firstName, "Good evening", "Finish clean, not scattered.") : this.joinGreeting(firstName, "Good evening", "You can still close today with control.");
    }

    private String formatCurrentDate() {
        return new SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(new Date());
    }

    private String formatTimeOfDay(long timestamp) {
        if (timestamp <= 0L) {
            return "";
        }
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(timestamp));
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
                return "Today's reset was completed at " + this.formatTimeOfDay(resetSession.completedAt) + ". Protect the structure you just started.";
            }
            return "Today's reset is complete. Protect the structure you just started.";
        }
        if (onboardingAssessment != null && !this.hasEnoughSignalsForSummary(behaviorReport, topApp)) {
            return this.trimSentence(onboardingAssessment.summary, 120);
        }
        if (insights != null && insights.aiEnhanced && insights.aiSummary != null && !insights.aiSummary.trim().isEmpty()) {
            return this.trimSentence(insights.aiSummary, 120);
        }
        if (highRisk && behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L) {
            return "Last night's phone use can weaken today unless you protect the next block.";
        }
        if (highRisk && behaviorReport != null && behaviorReport.rapidSwitchCount >= 8) {
            return "Your attention is vulnerable to fast switching today. A clean first block matters.";
        }
        if (insights != null && insights.primaryInsight != null && !insights.primaryInsight.trim().isEmpty()) {
            return this.trimSentence(insights.primaryInsight, 120);
        }
        if (topApp != null && topApp.usageTime >= 2700000L) {
            return "Your day gets harder if " + this.resolveTopAppLabel(topApp, privacyMode) + " gets the first tap.";
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
            return this.trimSentence(insights.personalizedComparisons, 115);
        }
        return "Your baseline is ready, so today's guidance is now tied to your own pattern.";
    }

    private MissionContent buildMissionContent(List<InterventionTask> activeTaskList, List<InterventionTask> allTaskList, DashboardInsights insights, BehaviorReport behaviorReport, DailyResetSession resetSession, OnboardingAssessment onboardingAssessment, boolean privacyMode) {
        List<HomeScreenState.MissionStepItem> stepItems = this.buildMissionStepItems(activeTaskList, allTaskList);
        if (stepItems.isEmpty() && onboardingAssessment != null && onboardingAssessment.missionSteps != null) {
            for (String missionStep : onboardingAssessment.missionSteps) {
                if (missionStep == null || missionStep.trim().isEmpty()) continue;
                stepItems.add(new HomeScreenState.MissionStepItem(0, missionStep.trim(), false, false, "general", "This step helps you stabilize your routine while MindTrace learns your patterns."));
                if (stepItems.size() != 3) continue;
                break;
            }
        }
        if (stepItems.isEmpty()) {
            if (behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L) {
                stepItems.add(new HomeScreenState.MissionStepItem(0, "Get out of bed without scrolling", false, false, "rest", "Starting the day away from your phone helps reduce momentum from late-night usage."));
            } else {
                stepItems.add(new HomeScreenState.MissionStepItem(0, "Finish one focused study block", false, false, "focus", "A protected focus block lowers the chance of random switching taking over the morning."));
            }
            if (insights != null && insights.hasOverusedApp && insights.topAppName != null && !insights.topAppName.trim().isEmpty()) {
                stepItems.add(new HomeScreenState.MissionStepItem(0, "Keep " + (privacyMode ? "your top app" : insights.topAppName) + " away from your first work session", false, false, "digital", "Delaying your most distracting app creates a cleaner start and protects early momentum."));
            } else {
                stepItems.add(new HomeScreenState.MissionStepItem(0, "Keep your phone away during the first work session", false, false, "digital", "Physical distance from the phone makes it easier to stay locked into the first important task."));
            }
            stepItems.add(new HomeScreenState.MissionStepItem(0, "Complete one check-in before night", false, false, "general", "A check-in closes the loop for the day and helps tomorrow's guidance get sharper."));
        }
        List<String> steps = this.extractMissionStepTitles(stepItems);
        int totalTasks = 0;
        int completedTasks = 0;
        for (HomeScreenState.MissionStepItem item : stepItems) {
            if (item.taskId <= 0) continue;
            ++totalTasks;
            if (!item.isCompleted) continue;
            ++completedTasks;
        }
        if (totalTasks > 0 && completedTasks >= totalTasks) {
            return new MissionContent("Hold the routine you already built today.", steps, stepItems, totalTasks + "/" + totalTasks + " complete", 100);
        }
        if (resetSession != null && resetSession.isCompleted) {
            String progressText;
            String string = progressText = totalTasks > 0 ? "Reset done | " + completedTasks + "/" + totalTasks + " complete" : "Reset done";
            int progressPercent = totalTasks > 0 ? Math.max(34, Math.round((float)completedTasks * 100.0f / (float)totalTasks)) : Math.max(34, insights == null ? 0 : insights.fulfillmentScore);
            return new MissionContent("Your day has started. Protect the first clean block.", steps, stepItems, progressText, progressPercent);
        }
        String missionTitle = onboardingAssessment != null && !this.hasEnoughSignalsForSummary(behaviorReport, null) ? onboardingAssessment.missionTitle : (insights != null && insights.supportRecommended ? "Protect today before distraction takes control." : (behaviorReport != null && behaviorReport.rapidSwitchCount >= 8 ? "Execute your day without random phone switching." : (behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L ? "Reset early so the rest of the day can stay clean." : "Execute your daily routine without phone drift.")));
        int progressPercent = totalTasks > 0 ? Math.round((float)completedTasks * 100.0f / (float)totalTasks) : Math.max(12, insights == null ? 0 : insights.fulfillmentScore);
        String progressText = totalTasks > 0 ? completedTasks + "/" + totalTasks + " complete" : "3 priorities";
        return new MissionContent(missionTitle, steps, stepItems, progressText, progressPercent);
    }

    private List<HomeScreenState.MissionStepItem> buildMissionStepItems(List<InterventionTask> activeTaskList, List<InterventionTask> allTaskList) {
        List<InterventionTask> sourceTasks = allTaskList;
        if ((sourceTasks == null || sourceTasks.isEmpty()) && activeTaskList != null && !activeTaskList.isEmpty()) {
            sourceTasks = activeTaskList;
        }
        if (sourceTasks == null || sourceTasks.isEmpty()) {
            return new ArrayList<HomeScreenState.MissionStepItem>();
        }
        ArrayList<InterventionTask> missionTasks = new ArrayList<InterventionTask>();
        long startOfToday = this.getStartOfTodayMillis();
        for (InterventionTask task : sourceTasks) {
            if (!this.isMissionTaskRelevant(task, startOfToday)) continue;
            missionTasks.add(task);
        }
        Collections.sort(missionTasks, (first, second) -> {
            boolean secondMicro;
            boolean secondCompleted;
            boolean secondActionable;
            boolean firstActionable = first != null && first.isActionable();
            boolean bl = secondActionable = second != null && second.isActionable();
            if (firstActionable != secondActionable) {
                return Boolean.compare(secondActionable, firstActionable);
            }
            boolean firstCompleted = this.isTaskCompleted((InterventionTask)first);
            if (firstCompleted != (secondCompleted = this.isTaskCompleted((InterventionTask)second))) {
                return Boolean.compare(firstCompleted, secondCompleted);
            }
            boolean firstMicro = first != null && first.isMicroIntervention;
            boolean bl2 = secondMicro = second != null && second.isMicroIntervention;
            if (firstMicro != secondMicro) {
                return Boolean.compare(secondMicro, firstMicro);
            }
            int priorityCompare = Integer.compare(second.priority, first.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            int durationCompare = Integer.compare(first.durationMinutes, second.durationMinutes);
            if (durationCompare != 0) {
                return durationCompare;
            }
            long firstTimestamp = Math.max(first.completedAt, first.dateCreated);
            long secondTimestamp = Math.max(second.completedAt, second.dateCreated);
            return Long.compare(secondTimestamp, firstTimestamp);
        });
        ArrayList<HomeScreenState.MissionStepItem> stepItems = new ArrayList<HomeScreenState.MissionStepItem>();
        HashSet<String> usedMissionTitles = new HashSet<String>();
        for (InterventionTask task : missionTasks) {
            String titleKey;
            String title = task == null || task.title == null ? "" : task.title.trim();
            if (title.isEmpty() || !usedMissionTitles.add(titleKey = title.toLowerCase(Locale.ROOT))) continue;
            stepItems.add(new HomeScreenState.MissionStepItem(task.id, title, this.isTaskCompleted(task), task.isActionable() && !this.isTaskCompleted(task), this.resolveMissionCategory(task), this.resolveMissionReason(task)));
            if (stepItems.size() != 3) continue;
            break;
        }
        return stepItems;
    }

    private List<Float> buildRiskTrend(List<RiskClassification> classifications, int fallbackRiskIndex) {
        ArrayList<Float> history = new ArrayList<Float>();
        if (classifications != null && !classifications.isEmpty()) {
            ArrayList<RiskClassification> sorted = new ArrayList<RiskClassification>(classifications);
            Collections.sort(sorted, (first, second) -> Long.compare(first.dayTimestamp, second.dayTimestamp));
            for (RiskClassification classification : sorted) {
                if (classification == null) continue;
                history.add(Float.valueOf(Math.max(0.0f, Math.min(100.0f, classification.overallRiskScore * 100.0f))));
            }
        }
        if (history.isEmpty()) {
            history.add(Float.valueOf(fallbackRiskIndex));
        }
        while (history.size() < 7) {
            history.add(0, (Float)history.get(0));
        }
        if (history.size() > 7) {
            return new ArrayList<Float>(history.subList(history.size() - 7, history.size()));
        }
        return history;
    }

    private String resolveMissionCategory(InterventionTask task) {
        String linkedRisk;
        if (task == null) {
            return "general";
        }
        String category = task.category == null ? "" : task.category.trim().toLowerCase(Locale.ROOT);
        String string = linkedRisk = task.linkedRiskCategory == null ? "" : task.linkedRiskCategory.trim().toLowerCase(Locale.ROOT);
        if (category.contains("focus") || category.contains("mindfulness")) {
            return "focus";
        }
        if (category.contains("detox") || linkedRisk.contains("digital") || linkedRisk.contains("phone")) {
            return "digital";
        }
        if (category.contains("social") || linkedRisk.contains("social")) {
            return "social";
        }
        String timeSlot = task.scheduledTimeSlot == null ? "" : task.scheduledTimeSlot;
        if (category.contains("recovery") || linkedRisk.contains("sleep") || timeSlot.toLowerCase(Locale.ROOT).contains("evening")) {
            return "rest";
        }
        return "general";
    }

    private String resolveMissionReason(InterventionTask task) {
        if (task == null) {
            return null;
        }
        if (task.whyThisTask != null && !task.whyThisTask.trim().isEmpty()) {
            return task.whyThisTask.trim();
        }
        if (task.description != null && !task.description.trim().isEmpty()) {
            return task.description.trim();
        }
        return "This step was chosen to reduce friction and strengthen the next healthy action in your day.";
    }

    private boolean isMissionTaskRelevant(InterventionTask task, long startOfToday) {
        if (task == null || task.title == null || task.title.trim().isEmpty()) {
            return false;
        }
        if (task.isActionable()) {
            return true;
        }
        return this.isTaskCompleted(task) && task.completedAt >= startOfToday;
    }

    private boolean isTaskCompleted(InterventionTask task) {
        if (task == null) {
            return false;
        }
        return task.isCompleted || "COMPLETED".equals(task.status);
    }

    private List<String> extractMissionStepTitles(List<HomeScreenState.MissionStepItem> stepItems) {
        ArrayList<String> titles = new ArrayList<String>();
        if (stepItems == null) {
            return titles;
        }
        for (HomeScreenState.MissionStepItem item : stepItems) {
            if (item == null || item.text == null || item.text.trim().isEmpty()) continue;
            titles.add(item.text.trim());
        }
        return titles;
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

    private List<String> buildWarningItems(DashboardInsights insights, BehaviorReport behaviorReport, AppUsageModel topApp, OnboardingAssessment onboardingAssessment, boolean privacyMode) {
        ArrayList<String> items = new ArrayList<String>();
        if (onboardingAssessment != null && onboardingAssessment.warningItems != null) {
            for (String warning : onboardingAssessment.warningItems) {
                if (warning == null || warning.trim().isEmpty()) continue;
                this.addIfMissing(items, warning);
            }
        }
        if (behaviorReport != null && behaviorReport.dataAvailable) {
            if (behaviorReport.lateNightUsageMillis >= 600000L) {
                items.add("Late-night scrolling");
            }
            if (behaviorReport.rapidSwitchCount >= 8 || behaviorReport.appSwitchCount >= 20) {
                items.add("Random app switching");
            }
            if (behaviorReport.hasLoopPattern) {
                items.add("Falling into the same app loop again");
            }
            if (behaviorReport.bingeSessionCount > 0 || behaviorReport.longestSessionMillis >= 2700000L) {
                items.add("One long unplanned scroll");
            }
        }
        if (topApp != null && topApp.usageTime >= 1800000L) {
            items.add("Opening " + this.resolveTopAppLabel(topApp, privacyMode) + " first thing");
        }
        if (insights != null && insights.reasonItems != null) {
            for (String reason : insights.reasonItems) {
                if (reason == null) continue;
                String lower = reason.toLowerCase(Locale.getDefault());
                if (lower.contains("sleep")) {
                    items.add("Skipping sleep tonight");
                    continue;
                }
                if (lower.contains("app switching")) {
                    items.add("Random app switching");
                    continue;
                }
                if (!lower.contains("top app") && !lower.contains("dominates")) continue;
                items.add("Letting one app dominate the day");
            }
        }
        this.addIfMissing(items, "Staying in bed with the phone");
        this.addIfMissing(items, "Checking distracting apps before your first task");
        this.addIfMissing(items, "Skipping sleep tonight");
        return items.size() > 5 ? new ArrayList<String>(items.subList(0, 5)) : items;
    }

    private List<HomeScreenState.WarningCardItem> buildWarningCardItems(DashboardInsights insights, BehaviorReport behaviorReport, AppUsageModel topApp, OnboardingAssessment onboardingAssessment, boolean privacyMode) {
        List<String> titles = this.buildWarningItems(insights, behaviorReport, topApp, onboardingAssessment, privacyMode);
        ArrayList<HomeScreenState.WarningCardItem> items = new ArrayList<HomeScreenState.WarningCardItem>();
        for (String title : titles) {
            if (title == null || title.trim().isEmpty()) continue;
            String lower = title.toLowerCase(Locale.getDefault());
            int severity = 2;
            String detail = "This pattern has a moderate chance of stealing momentum if it shows up today.";
            if (lower.contains("late-night")) {
                severity = behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L ? 3 : 2;
                long lateNightMinutes = behaviorReport == null ? 22L : Math.max(12L, behaviorReport.lateNightUsageMillis / 60000L);
                detail = "Caught in the late-night window \u2022 Usually between 11 PM\u20131 AM \u2022 Costs about " + lateNightMinutes + " min of recovery time.";
            } else if (lower.contains("switch")) {
                int switchCount = behaviorReport == null ? 18 : Math.max(behaviorReport.rapidSwitchCount, behaviorReport.appSwitchCount);
                severity = switchCount >= 20 ? 3 : 2;
                detail = "Fragmented attention detected (" + switchCount + " switches). Your brain is searching for dopamine. Try a 2-min breathing reset.";
            } else if (lower.contains("loop")) {
                severity = 3;
                detail = "Recursive app cycling detected. This 'locked-in' state makes it hard to start meaningful work. Close all apps and stand up.";
            } else if (lower.contains("first thing") || lower.contains("first task")) {
                severity = topApp != null && topApp.usageTime >= 2700000L ? 3 : 2;
                detail = "Reacting to notifications before your morning intention. This gives distraction the first-tap advantage for the rest of your day.";
            } else if (lower.contains("sleep")) {
                severity = 3;
                detail = "Sleep pressure is part of today's risk stack \u2022 Protecting bedtime could save tomorrow's energy and focus.";
            } else if (lower.contains("bed")) {
                severity = 2;
                detail = "High-risk comfort loop \u2022 Usually happens right after waking \u2022 Often delays the first intentional action.";
            } else if (lower.contains("long unplanned scroll") || lower.contains("dominate")) {
                long scrollMinutes = behaviorReport == null ? 45L : Math.max(30L, behaviorReport.longestSessionMillis / 60000L);
                severity = scrollMinutes >= 60L ? 3 : 2;
                detail = "One dominant session can swallow " + scrollMinutes + " min \u2022 Often starts as a quick check and snowballs.";
            }
            items.add(new HomeScreenState.WarningCardItem(title, detail, severity));
            if (items.size() != 5) continue;
            break;
        }
        return items;
    }

    private List<HomeScreenState.InsightItem> buildAiInsightItems(DashboardInsights insights, BehaviorReport behaviorReport, QuestionnaireResponse latestResponse, boolean highRisk, boolean hasData) {
        ArrayList<HomeScreenState.InsightItem> items = new ArrayList<HomeScreenState.InsightItem>();
        List<String> primaryReasons = this.collectInsightReasons(insights);
        if (insights != null) {
            if (this.hasText(insights.aiSummary)) {
                items.add(new HomeScreenState.InsightItem("MindTrace AI", this.trimSentence(insights.aiSummary, 140), primaryReasons, highRisk ? "Review stabilizing insight" : "View guidance", "plan", this.hasText(insights.anomalySummary)));
            } else if (this.hasText(insights.primaryInsight)) {
                items.add(new HomeScreenState.InsightItem("MindTrace AI", this.trimSentence(insights.primaryInsight, 140), primaryReasons, highRisk ? "Review protection plan" : "View guidance", "plan", this.hasText(insights.anomalySummary)));
            }
            // ── Phase 6: Trajectory insight card ──
            if (insights.hasTrajectory() && this.hasText(insights.trajectorySummary)) {
                boolean trajectoryUrgent = "rapidly_worsening".equals(insights.trajectoryLabel)
                        || "gradually_worsening".equals(insights.trajectoryLabel);
                String trajectoryAction = trajectoryUrgent ? "Review 7-day trend" : "See your progress";
                items.add(new HomeScreenState.InsightItem(
                        "7-Day Trajectory",
                        this.trimSentence(insights.trajectorySummary, 140),
                        primaryReasons,
                        trajectoryAction,
                        "plan",
                        trajectoryUrgent
                ));
            }
            if (this.hasText(insights.anomalySummary)) {
                items.add(new HomeScreenState.InsightItem("Pattern shift detected", this.trimSentence(insights.anomalySummary, 140), primaryReasons, "Learn why", "plan", true));
            }
            if (this.hasText(insights.recommendation)) {
                items.add(new HomeScreenState.InsightItem("Suggested next move", this.trimSentence(insights.recommendation, 140), primaryReasons, "View plan", "plan", false));
            }
        }
        if (items.isEmpty()) {
            String fallback = !hasData ? "Complete one check-in so MindTrace can stop guessing and start giving you pattern-based guidance." : (highRisk ? "Today looks sensitive to distraction carryover. The best move is still to protect the next clean block." : (latestResponse != null && latestResponse.exercisedToday ? "You already stacked one protective signal by moving your body. Keep the rest of the day friction-light." : (behaviorReport != null && behaviorReport.rapidSwitchCount >= 8 ? "Attention looks more fragmented than usual. A single protected work block will outperform trying to multitask." : "Your dashboard is stable today. Small deliberate actions now will keep tomorrow easier.")));
            items.add(new HomeScreenState.InsightItem("MindTrace AI", fallback, primaryReasons, "View insight", "plan", false));
        }
        return items.size() > 3 ? new ArrayList<HomeScreenState.InsightItem>(items.subList(0, 3)) : items;
    }

    private List<String> collectInsightReasons(DashboardInsights insights) {
        ArrayList<String> reasons = new ArrayList<String>();
        if (insights == null) {
            return reasons;
        }
        // ── Phase 6: Prepend trajectory context as the first reason ──
        if (insights.hasTrajectory() && this.hasText(insights.trajectoryLabel)) {
            String trajectoryReason = this.buildTrajectoryReasonLabel(insights.trajectoryLabel);
            if (trajectoryReason != null) {
                reasons.add(trajectoryReason);
            }
        }
        if (insights.classificationReasons != null) {
            for (String reason : insights.classificationReasons) {
                if (this.hasText(reason)) {
                    reasons.add(this.trimSentence(reason, 88));
                }
                if (reasons.size() != 5) continue;
                return reasons;
            }
        }
        if (insights.reasonItems != null) {
            for (String reason : insights.reasonItems) {
                if (this.hasText(reason)) {
                    reasons.add(this.trimSentence(reason, 88));
                }
                if (reasons.size() != 5) continue;
                return reasons;
            }
        }
        if (this.hasText(insights.behaviorExplanation)) {
            reasons.add(this.trimSentence(insights.behaviorExplanation, 88));
        }
        return reasons;
    }

    /**
     * Maps a trajectory label to a concise human-readable reason bullet.
     */
    private String buildTrajectoryReasonLabel(String trajectoryLabel) {
        if (trajectoryLabel == null) return null;
        switch (trajectoryLabel) {
            case "rapidly_improving":
                return "📉 7-day trend: rapidly improving";
            case "gradually_improving":
                return "📉 7-day trend: steadily improving";
            case "gradually_worsening":
                return "📈 7-day trend: gradually worsening";
            case "rapidly_worsening":
                return "⚠ 7-day trend: rapidly worsening";
            case "stable":
                return "➡ 7-day trend: stable";
            default:
                return null;
        }
    }

    private HomeScreenState.PatternRadarCard buildPatternRadarCard(BehaviorReport behaviorReport, AppUsageModel topApp, boolean privacyMode, boolean hasData, int riskIndex) {
        String footer;
        ArrayList<String> signalPills = new ArrayList<String>();
        boolean urgent = riskIndex >= 70;
        String title = "Live Pattern Radar";
        String summary = "MindTrace is watching the strongest live signals shaping your day.";
        String string = footer = hasData ? "Live behavior stream" : "Waiting for enough signal";
        if (!hasData) {
            signalPills.add("Need one check-in");
            signalPills.add("Usage signal pending");
            signalPills.add("Pattern map warming up");
            return new HomeScreenState.PatternRadarCard(title, summary, signalPills, footer, false);
        }
        if (topApp != null && topApp.usageTime > 0L) {
            signalPills.add("Top pull: " + this.resolveTopAppLabel(topApp, privacyMode));
        } else {
            signalPills.add("Top pull: calm");
        }
        if (behaviorReport != null) {
            signalPills.add("Switches: " + Math.max(0, behaviorReport.rapidSwitchCount));
            long lateNightMinutes = Math.max(0L, behaviorReport.lateNightUsageMillis / 60000L);
            if (lateNightMinutes > 0L) {
                signalPills.add("Night drift: " + lateNightMinutes + "m");
            } else {
                long longestMinutes = Math.max(0L, behaviorReport.longestSessionMillis / 60000L);
                signalPills.add("Longest flow: " + Math.max(5L, longestMinutes) + "m");
            }
            if (behaviorReport.lateNightUsageMillis >= 1200000L) {
                urgent = true;
                summary = "Late-night carryover is one of the strongest signals in your stack today.";
                footer = "Best move: lower the stimulation before bed";
            } else if (behaviorReport.rapidSwitchCount >= 8) {
                urgent = true;
                summary = "Rapid switching is the loudest signal right now. One protected block will outperform multitasking.";
                footer = "Best move: lock one task for the next block";
            } else if (behaviorReport.hasLoopPattern) {
                urgent = true;
                summary = "MindTrace detected a repeat-loop pattern, which usually means a short check is turning into a drift session.";
                footer = "Best move: break the loop with a clean reset";
            } else if (behaviorReport.dataAvailable) {
                summary = behaviorReport.explanation == null || behaviorReport.explanation.trim().isEmpty() ? "Your strongest signals look stable right now." : this.trimSentence(behaviorReport.explanation, 115);
                footer = behaviorReport.summaryLabel == null || behaviorReport.summaryLabel.trim().isEmpty() ? "Live behavior stream" : behaviorReport.summaryLabel;
            }
        } else {
            signalPills.add("Switches: --");
            signalPills.add("Night drift: --");
        }
        while (signalPills.size() < 3) {
            signalPills.add("Pattern signal active");
        }
        return new HomeScreenState.PatternRadarCard(title, summary, signalPills, footer, urgent);
    }

    private HomeScreenState.FocusWindowCard buildFocusWindowCard(DashboardInsights insights, BehaviorReport behaviorReport, AppUsageModel topApp, QuestionnaireResponse latestResponse, DailyResetSession resetSession, OnboardingAssessment onboardingAssessment, boolean privacyMode, boolean hasData, boolean highRisk, int riskIndex) {
        boolean urgent;
        String windowLabel = this.formatMomentumWindowLabel(highRisk ? 35 : 45);
        ArrayList<String> rituals = new ArrayList<String>();
        String badgeText = "Online";
        String title = "Your personal coach is ready to help";
        String coachText = "Get context-aware advice, stress relief, and focus strategies based on your real-time usage and mood data.";
        rituals.add("Powered by MindTrace Intelligence");
        String actionLabel = "Open Coach Chat";
        String actionType = "none";
        boolean bl = urgent = riskIndex >= 55;
        if (highRisk) {
            badgeText = "AI Intervention";
            title = "Let's stabilize your focus";
            coachText = "I've detected some pattern drift. Chat with me now for a quick reset.";
            urgent = true;
        }
        return new HomeScreenState.FocusWindowCard(title, windowLabel, coachText, badgeText, rituals, actionLabel, actionType, urgent);
    }

    private String formatMomentumWindowLabel(int durationMinutes) {
        Calendar start = Calendar.getInstance();
        int minute = start.get(12);
        int nextQuarter = (minute + 14) / 15 * 15;
        if (nextQuarter >= 60) {
            start.add(11, 1);
            start.set(12, 0);
        } else {
            start.set(12, nextQuarter);
        }
        start.set(13, 0);
        start.set(14, 0);
        Calendar end = (Calendar)start.clone();
        end.add(12, Math.max(20, durationMinutes));
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return formatter.format(start.getTime()) + " - " + formatter.format(end.getTime());
    }

    private String buildForecastDriverLabel(BehaviorReport behaviorReport, QuestionnaireResponse latestResponse, double deviation) {
        if (behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L) {
            return "Driver: late-night carryover";
        }
        if (behaviorReport != null && behaviorReport.rapidSwitchCount >= 8) {
            return "Driver: fragmented attention";
        }
        if (behaviorReport != null && behaviorReport.hasLoopPattern) {
            return "Driver: repeat-loop behavior";
        }
        if (latestResponse != null && latestResponse.stressLevel >= 4) {
            return "Driver: elevated stress";
        }
        if (latestResponse != null && latestResponse.sleepHours > 0.0f && latestResponse.sleepHours < 6.0f) {
            return "Driver: light sleep debt";
        }
        if (!Double.isNaN(deviation) && deviation >= 0.75) {
            return "Driver: higher screen-time load";
        }
        if (latestResponse != null && latestResponse.exercisedToday) {
            return "Driver: exercise buffer";
        }
        return "Driver: routine stability";
    }

    private HomeScreenState.ForecastCard buildForecastCard(List<RiskClassification> classifications, BehaviorReport behaviorReport, long todayScreenTime, UserBaseline baseline, QuestionnaireResponse latestResponse, int riskIndex) {
        List<Float> trend = this.buildRiskTrend(classifications, riskIndex);
        float todayRisk = trend.isEmpty() ? (float)riskIndex : trend.get(trend.size() - 1).floatValue();
        float yesterdayRisk = trend.size() >= 2 ? trend.get(trend.size() - 2).floatValue() : todayRisk;
        float predictedRisk = todayRisk + (todayRisk - yesterdayRisk) * 0.45f;
        double deviation = this.calculateScreenTimeDeviation(todayScreenTime, baseline);
        if (!Double.isNaN(deviation)) {
            predictedRisk += deviation > 0.0 ? Math.min(14.0f, (float)deviation * 10.0f) : -Math.min(8.0f, (float)Math.abs(deviation) * 6.0f);
        }
        if (behaviorReport != null) {
            if (behaviorReport.rapidSwitchCount >= 8) {
                predictedRisk += 6.0f;
            }
            if (behaviorReport.lateNightUsageMillis >= 1200000L) {
                predictedRisk += 8.0f;
            }
            if (behaviorReport.hasLoopPattern) {
                predictedRisk += 5.0f;
            }
        }
        if (latestResponse != null) {
            if (latestResponse.sleepHours > 0.0f && latestResponse.sleepHours < 6.0f) {
                predictedRisk += 7.0f;
            }
            if (latestResponse.stressLevel >= 4) {
                predictedRisk += 10.0f;
            }
            if (latestResponse.exercisedToday) {
                predictedRisk -= 6.0f;
            }
            if ("Anxious".equalsIgnoreCase(latestResponse.mood) || "Sad".equalsIgnoreCase(latestResponse.mood) || "Numb".equalsIgnoreCase(latestResponse.mood)) {
                predictedRisk += 6.0f;
            } else if ("Happy".equalsIgnoreCase(latestResponse.mood) || "Calm".equalsIgnoreCase(latestResponse.mood)) {
                predictedRisk -= 5.0f;
            }
        }
        predictedRisk = this.clamp(Math.round(predictedRisk), 8, 96);
        int predictedRiskInt = Math.round(predictedRisk);
        int riskDelta = predictedRiskInt - Math.round(todayRisk);
        int confidence = this.clamp(52 + (classifications == null ? 0 : Math.min(21, classifications.size() * 3)) + (latestResponse != null ? 8 : 0) + (baseline != null ? 7 : 0), 50, 91);
        ArrayList<Float> forecastTrend = new ArrayList<Float>();
        forecastTrend.add(Float.valueOf(yesterdayRisk));
        forecastTrend.add(Float.valueOf(todayRisk));
        forecastTrend.add(Float.valueOf(predictedRisk));
        boolean highRiskTomorrow = predictedRisk >= 70.0f;
        String driverLabel = this.buildForecastDriverLabel(behaviorReport, latestResponse, deviation);
        if (predictedRisk >= 70.0f) {
            return new HomeScreenState.ForecastCard("\u26c8", "Tomorrow may feel heavy", "Risk is projected around " + predictedRisk + "/100 if today's patterns carry into tomorrow.", confidence, "Consider sleeping earlier, protecting your first hour, and using DND before bed.", forecastTrend, true, predictedRiskInt, riskDelta, driverLabel);
        }
        if (predictedRisk >= 45.0f) {
            return new HomeScreenState.ForecastCard("\u26c5", "Tomorrow needs a little care", "The outlook is watchful, not dangerous. A cleaner evening routine could noticeably lower tomorrow's friction.", confidence, "Keep the phone out of the first work block and cap late-night usage.", forecastTrend, false, predictedRiskInt, riskDelta, driverLabel);
        }
        return new HomeScreenState.ForecastCard("\u2600\ufe0f", "Tomorrow looks steady", "Momentum is projected to stay stable if you keep today's structure intact.", confidence, "Repeat the habits that made today cleaner: one focused block, one check-in, one protected evening.", forecastTrend, highRiskTomorrow, predictedRiskInt, riskDelta, driverLabel);
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

    private NextActionContent buildNextActionContent(DashboardInsights insights, BehaviorReport behaviorReport, AppUsageModel topApp, DailyResetSession resetSession, OnboardingProfile profile, OnboardingAssessment onboardingAssessment, boolean privacyMode, boolean hasData, boolean highRisk) {
        if (resetSession != null && resetSession.isCompleted) {
            return new NextActionContent("Protect the first block you just started.", "Your reset is done. Keep the phone away long enough for the first real win to land.", "Protect the next 30 min");
        }
        if (!hasData) {
            return new NextActionContent("Complete one quick check-in.", "This gives MindTrace enough signal to build personalized guidance instead of generic advice.", "Takes about 1 min");
        }
        if (onboardingAssessment != null && !this.hasEnoughSignalsForSummary(behaviorReport, topApp)) {
            return new NextActionContent(onboardingAssessment.nextBestActionTitle, onboardingAssessment.nextBestActionReason, profile != null && profile.supportNeeded ? "Start now | low pressure" : "Start now");
        }
        if (highRisk && behaviorReport != null && behaviorReport.rapidSwitchCount >= 8) {
            return new NextActionContent("Put your phone on focus mode for 30 minutes.", "This cuts early fragmentation and protects the first meaningful block of momentum.", "Starts now | 30 min block");
        }
        if (highRisk && behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L) {
            return new NextActionContent("Take a 2-minute reset before opening any distracting app.", "A short reset breaks the carryover from late-night phone use and helps the day start cleaner.", "Takes about 2 min");
        }
        if (topApp != null && topApp.usageTime >= 2700000L) {
            return new NextActionContent("Keep " + this.resolveTopAppLabel(topApp, privacyMode) + " off the first hour.", "The day gets easier when your main distraction does not get the first tap.", "Protect the next 60 min");
        }
        if (insights != null && insights.nextBestAction != null && !insights.nextBestAction.trim().isEmpty()) {
            return new NextActionContent(this.trimSentence(insights.nextBestAction, 95), this.trimSentence(insights.reasonSummary == null || insights.reasonSummary.trim().isEmpty() ? "This is the simplest move that protects today's momentum." : insights.reasonSummary, 120), this.resolveEta(insights.nextBestAction));
        }
        return new NextActionContent("Start your first 25-minute work block.", "The fastest way to feel better is to create one clean win before the phone steals your attention.", "Starts now | 25 min");
    }

    private String buildSupportStripText(BehaviorReport behaviorReport, AppUsageModel topApp, OnboardingProfile profile, OnboardingAssessment onboardingAssessment, boolean privacyMode) {
        if (onboardingAssessment != null && onboardingAssessment.supportRecommended && profile != null && profile.safetySupportEnabled) {
            return "Support tools are visible because your setup suggests the day may feel heavier at times. Use them early, not only when things spiral.";
        }
        if (behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L) {
            return "Hard start today? Open Support and stabilize the next 10 minutes before the spiral builds.";
        }
        if (topApp != null && topApp.usageTime >= 2700000L) {
            return "If " + this.resolveTopAppLabel(topApp, privacyMode) + " keeps pulling you back, open Support for a quick reset.";
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
        String trimmed = this.cleanDashboardCopy(text);
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
        this.executorService.shutdown();
    }

    private static final class MissionContent {
        final String title;
        final List<String> steps;
        final List<HomeScreenState.MissionStepItem> stepItems;
        final String progressText;
        final int progressPercent;

        MissionContent(String title, List<String> steps, List<HomeScreenState.MissionStepItem> stepItems, String progressText, int progressPercent) {
            this.title = title;
            this.steps = steps;
            this.stepItems = stepItems;
            this.progressText = progressText;
            this.progressPercent = progressPercent;
        }
    }

    private static final class NextActionContent {
        final String title;
        final String reason;
        final String eta;

        NextActionContent(String title, String reason, String eta) {
            this.title = title;
            this.reason = reason;
            this.eta = eta;
        }
    }
}
