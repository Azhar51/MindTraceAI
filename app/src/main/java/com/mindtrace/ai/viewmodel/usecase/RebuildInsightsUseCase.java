package com.mindtrace.ai.viewmodel.usecase;

import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.ai.AnomalyDetector;
import com.mindtrace.ai.ai.BehaviorFeatureExtractor;
import com.mindtrace.ai.ai.BehaviorFeatures;
import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.ai.EfficacyMetrics;
import com.mindtrace.ai.ai.InsightEngine;
import com.mindtrace.ai.ai.InterventionEngine;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.entity.BehaviorSnapshotEntity;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.database.entity.WellnessSummary;
import com.mindtrace.ai.repository.UsageRepository;

import java.util.List;
import java.util.Map;

/**
 * Extracted from DashboardViewModel — handles the insight rebuild pipeline.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Run InsightEngine to produce DashboardInsights</li>
 *   <li>Extract behavior features and anomaly profiles</li>
 *   <li>Aggregate efficacy metrics from TaskDao + InterventionEngine</li>
 *   <li>Persist WellnessSummary snapshots</li>
 * </ul>
 */
public class RebuildInsightsUseCase {

    private final InsightEngine insightEngine;
    private final BehaviorFeatureExtractor behaviorFeatureExtractor;
    private final AnomalyDetector anomalyDetector;
    private final InterventionEngine interventionEngine;
    private final TaskDao taskDao;
    private final UsageRepository usageRepository;

    // Cached results from the last rebuild cycle
    private volatile BehaviorFeatures latestBehaviorFeatures;
    private volatile AnomalyDetector.AnomalyProfile latestAnomalyProfile;

    public RebuildInsightsUseCase(
            InsightEngine insightEngine,
            BehaviorFeatureExtractor behaviorFeatureExtractor,
            AnomalyDetector anomalyDetector,
            InterventionEngine interventionEngine,
            TaskDao taskDao,
            UsageRepository usageRepository) {
        this.insightEngine = insightEngine;
        this.behaviorFeatureExtractor = behaviorFeatureExtractor;
        this.anomalyDetector = anomalyDetector;
        this.interventionEngine = interventionEngine;
        this.taskDao = taskDao;
        this.usageRepository = usageRepository;
    }

    /**
     * Result container for the insight rebuild pipeline.
     */
    public static class InsightResult {
        public final DashboardInsights insights;
        public final EfficacyMetrics efficacyMetrics;
        public final BehaviorFeatures behaviorFeatures;
        public final AnomalyDetector.AnomalyProfile anomalyProfile;

        public InsightResult(DashboardInsights insights, EfficacyMetrics efficacyMetrics,
                             BehaviorFeatures behaviorFeatures, AnomalyDetector.AnomalyProfile anomalyProfile) {
            this.insights = insights;
            this.efficacyMetrics = efficacyMetrics;
            this.behaviorFeatures = behaviorFeatures;
            this.anomalyProfile = anomalyProfile;
        }
    }

    /**
     * Executes the full insight rebuild pipeline. Must be called on a background thread.
     *
     * @param screenTime        today's screen time in millis
     * @param mostUsedApp       the most used app today
     * @param usageHistory      all daily usage records
     * @param stateHistory      all questionnaire responses
     * @param allTasks          all intervention tasks
     * @param baseline          the user's computed baseline
     * @param behaviorReport    today's behavior report
     * @param behaviorHistory   last 7 behavior snapshots
     * @param aiInsight         current AI insight result (nullable)
     * @param startOfTodayMillis start of today in epoch millis
     * @return InsightResult containing all computed data
     */
    public InsightResult execute(
            Long screenTime,
            AppUsageModel mostUsedApp,
            List<DailyUsage> usageHistory,
            List<QuestionnaireResponse> stateHistory,
            List<InterventionTask> allTasks,
            UserBaseline baseline,
            BehaviorReport behaviorReport,
            List<BehaviorSnapshotEntity> behaviorHistory,
            com.mindtrace.ai.ai.AiInsightResult aiInsight,
            long startOfTodayMillis) {

        // 1. Extract behavior features
        List<AppUsageModel> topApps = null; // passed separately if needed
        int fulfillmentScore = calculateFulfillmentScore(allTasks);
        BehaviorFeatures features = behaviorFeatureExtractor.extractFeatures(
                behaviorReport, topApps, fulfillmentScore);
        this.latestBehaviorFeatures = features;

        // 2. Build anomaly profile
        QuestionnaireResponse latestResponse = getLatestResponse(stateHistory);
        AnomalyDetector.AnomalyProfile profile = anomalyDetector.buildProfile(
                screenTime, usageHistory, latestResponse, stateHistory,
                fulfillmentScore, allTasks, baseline);
        this.latestAnomalyProfile = profile;

        // 3. Run InsightEngine
        DashboardInsights insights = insightEngine.buildInsights(
                screenTime, mostUsedApp, usageHistory, stateHistory,
                allTasks, baseline, behaviorReport, behaviorHistory,
                features, profile, aiInsight);

        // 4. Persist WellnessSummary
        persistWellnessSummary(insights, screenTime, mostUsedApp, startOfTodayMillis);

        // 5. Aggregate efficacy metrics
        EfficacyMetrics efficacyMetrics = aggregateEfficacy();

        return new InsightResult(insights, efficacyMetrics, features, profile);
    }

    public BehaviorFeatures getLatestBehaviorFeatures() {
        return latestBehaviorFeatures;
    }

    public AnomalyDetector.AnomalyProfile getLatestAnomalyProfile() {
        return latestAnomalyProfile;
    }

    // ─── Internal helpers ───────────────────────────────────────────

    private int calculateFulfillmentScore(List<InterventionTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int completed = 0;
        for (InterventionTask task : tasks) {
            if (task.isCompleted) completed++;
        }
        return Math.round((float) completed * 100.0f / (float) tasks.size());
    }

    private QuestionnaireResponse getLatestResponse(List<QuestionnaireResponse> responses) {
        return responses == null || responses.isEmpty() ? null : responses.get(0);
    }

    private void persistWellnessSummary(DashboardInsights insights, Long screenTime,
                                         AppUsageModel mostUsedApp, long startOfTodayMillis) {
        if (insights == null) return;

        WellnessSummary summary = new WellnessSummary();
        summary.dayTimestamp = startOfTodayMillis;
        summary.createdAt = System.currentTimeMillis();
        summary.wellnessState = insights.stateLabel;
        summary.riskLevel = insights.riskLabel;
        summary.explanationText = insights.explanationText;
        summary.reasonSummary = insights.reasonSummary;
        summary.nextBestAction = insights.nextBestAction;
        summary.screenTimeMillis = screenTime == null ? 0L : screenTime;
        summary.taskCompletionScore = insights.fulfillmentScore;
        summary.topAppName = insights.topAppName;
        summary.topAppPackage = mostUsedApp == null ? null : mostUsedApp.packageName;
        summary.supportSuggested = insights.supportRecommended;
        usageRepository.saveWellnessSummary(summary);
    }

    private EfficacyMetrics aggregateEfficacy() {
        try {
            long now = System.currentTimeMillis();
            List<InterventionTask> observing = taskDao.getTasksInObservationWindow(now);
            int observingCount = observing != null ? observing.size() : 0;
            int measuredCount = taskDao.getMeasuredEfficacyCount();
            float overallAvg = taskDao.getOverallAverageEfficacy();
            String bestCategory = taskDao.getMostEfficaciousCategory();

            long sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000);
            List<InterventionTask> recentMeasured = taskDao.getTasksWithEfficacySince(sevenDaysAgo);
            Map<String, Float> categoryScores =
                    interventionEngine.buildSentimentEnhancedEfficacyMap(recentMeasured);
            String[] summaryLines =
                    interventionEngine.getSentimentEnhancedSummary(recentMeasured);

            return new EfficacyMetrics(
                    categoryScores, summaryLines, measuredCount,
                    overallAvg, bestCategory, observingCount);
        } catch (Exception ignored) {
            return EfficacyMetrics.empty();
        }
    }
}
