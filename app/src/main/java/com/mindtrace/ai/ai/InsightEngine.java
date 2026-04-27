package com.mindtrace.ai.ai;

import android.content.Context;

import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.behavior.BehaviorThresholds;
import com.mindtrace.ai.database.entity.BehaviorSnapshotEntity;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.services.CrisisNotificationManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.mindtrace.ai.ai.CategoryTrend;

public class InsightEngine {
    private static final long APP_WARNING_THRESHOLD_MILLIS = 2L * 60L * 60L * 1000L;
    private static final long HIGH_SCREEN_TIME_THRESHOLD_MILLIS = 6L * 60L * 60L * 1000L;
    private static final long MODERATE_SCREEN_TIME_THRESHOLD_MILLIS = 4L * 60L * 60L * 1000L;
    private static final float LOW_SLEEP_THRESHOLD_HOURS = 6f;
    private static final long LATE_NIGHT_USAGE_THRESHOLD_MILLIS = 45L * 60L * 1000L;

    private final MentalStateClassifier classifier = new MentalStateClassifier();
    private final PersonalizationEngine personalizationEngine = new PersonalizationEngine();

    // ── MultiModal AI pipeline (Section 3.I) ──
    private MultiModalClassifier multiModalClassifier;
    private DigitalFeatureExtractor digitalExtractor;
    private PsychFeatureExtractor psychExtractor;
    private ContextFeatureExtractor contextExtractor;
    private TemporalFeatureExtractor temporalExtractor;
    private com.mindtrace.ai.repository.ClassificationRepository classificationRepo;
    private WeakReference<Context> appContextRef;

    /** Latest classification result from this session. */
    private RiskClassification latestClassification;

    // ── Phase 6: Trajectory cache (volatile for cross-thread visibility) ──
    private static final long TREND_CACHE_TTL_MS = 15L * 60 * 1000; // 15 minutes
    private volatile long lastTrendTimestamp;
    private volatile ClassificationTrendAnalyzer.TrendReport cachedTrendReport;
    private volatile String cachedTrajectoryLabel;
    private volatile String cachedTrajectorySummary;

    /**
     * Initialize the MultiModal AI pipeline.
     * Call once from DashboardViewModel or Application with context.
     */
    public void initMultiModalPipeline(android.content.Context context) {
        this.multiModalClassifier = new MultiModalClassifier(context);
        this.digitalExtractor = new DigitalFeatureExtractor(context);
        this.psychExtractor = new PsychFeatureExtractor(context);
        this.contextExtractor = new ContextFeatureExtractor(context);
        this.temporalExtractor = new TemporalFeatureExtractor(context);
        this.classificationRepo = new com.mindtrace.ai.repository.ClassificationRepository(context);
        this.appContextRef = new WeakReference<>(context.getApplicationContext());
    }

    /** Get the latest classification result. */
    public RiskClassification getLatestClassification() {
        return latestClassification;
    }

    public DashboardInsights buildInsights(
            Long currentScreenTime,
            AppUsageModel mostUsedApp,
            List<DailyUsage> usageHistory,
            List<QuestionnaireResponse> responses,
            List<InterventionTask> allTasks,
            UserBaseline userBaseline,
            BehaviorReport behaviorReport,
            List<BehaviorSnapshotEntity> behaviorHistory,
            BehaviorFeatures behaviorFeatures,
            AnomalyDetector.AnomalyProfile anomalyProfile,
            AiInsightResult aiInsightResult
    ) {
        QuestionnaireResponse latestResponse = responses != null && !responses.isEmpty() ? responses.get(0) : null;
        DailyUsage latestUsage = usageHistory != null && !usageHistory.isEmpty() ? usageHistory.get(0) : null;
        MentalStateClassifier.State state = classifier.classify(usageHistory, responses);

        long screenTime = currentScreenTime != null
                ? currentScreenTime
                : latestUsage != null ? latestUsage.screenTimeMillis : 0L;
        int fulfillmentScore = calculateFulfillmentScore(allTasks);
        boolean hasTaskHistory = allTasks != null && !allTasks.isEmpty();

        InsightInput input = new InsightInput(
                screenTime,
                mostUsedApp,
                latestResponse,
                latestUsage,
                calculateWeeklyAverageScreenTime(usageHistory),
                fulfillmentScore,
                hasTaskHistory,
                state,
                userBaseline,
                behaviorReport,
                behaviorHistory,
                behaviorFeatures,
                anomalyProfile,
                aiInsightResult
        );

        DashboardInsights insights = analyze(input);

        // ── 3.I.3–3.I.5: Run MultiModal classification pipeline ──
        if (multiModalClassifier != null && latestUsage != null) {
            try {
                long dayTimestamp = getStartOfDay(System.currentTimeMillis());
                FeatureVector.Builder fvBuilder = new FeatureVector.Builder();

                // Build FeatureVector from all 4 extractors
                digitalExtractor.extract(fvBuilder, dayTimestamp);
                psychExtractor.extract(fvBuilder, dayTimestamp);
                contextExtractor.extract(fvBuilder, dayTimestamp);
                if (temporalExtractor != null) {
                    temporalExtractor.extract(fvBuilder, dayTimestamp);
                }
                FeatureVector fv = fvBuilder.build();

                // Classify and persist
                latestClassification = multiModalClassifier.classify(fv, dayTimestamp);

                // ── Persist feature vector JSON for audit/debug ──
                latestClassification.featureVectorJson = fv.toJson();
                latestClassification.featureDataCount = fv.nonDefaultCount;

                // ── ADVANCED: Anomaly-based confidence modifier ──
                if (anomalyProfile != null) {
                    AnomalyDetector detector = new AnomalyDetector();
                    float confidenceMod = detector.classificationConfidenceModifier(anomalyProfile);
                    latestClassification.confidence =
                            Math.max(0f, Math.min(1f, latestClassification.confidence * confidenceMod));
                }

                // ── ADVANCED: Crisis escalation assessment ──
                MultiModalClassifier.CrisisLevel crisisLevel =
                        multiModalClassifier.assessCrisisLevel(latestClassification);

                // ── ADVANCED: Volatility & recovery ──
                float volatility = multiModalClassifier.computeVolatility(7);
                float recoveryScore = multiModalClassifier.computeRecoveryScore();
                int improvingStreak = multiModalClassifier.getImprovingStreak();

                // ── ADVANCED: Comorbidity detection ──
                String[] comorbidities = multiModalClassifier.detectComorbidities(latestClassification);

                // ── ADVANCED A3: Ensemble smoothing (reduce daily noise) ──
                multiModalClassifier.applyEnsembleSmoothing(latestClassification, 0.7f);

                // ── ADVANCED A4: Risk momentum tracking ──
                CategoryTrend[] allTrends = multiModalClassifier.buildAllCategoryTrends();

                // ── ADVANCED A2: Feature drift detection ──
                String[] driftSignals = multiModalClassifier.detectFeatureDrift(fv, 0.20f);

                // Generate "Why This Score" explanation reasons
                List<String> explanationReasons = buildExplanationReasons(latestClassification, fv);

                // Add advanced intelligence to reasons
                if (crisisLevel.requiresMonitoring()) {
                    explanationReasons.add("Crisis level: " + crisisLevel.label);
                }
                if (volatility > 0.25f) {
                    explanationReasons.add("Volatility: " +
                            multiModalClassifier.getVolatilityLabel(volatility));
                }
                if (improvingStreak >= 3) {
                    explanationReasons.add("🎯 " + improvingStreak + "-day improving streak");
                }
                if (recoveryScore > 0.5f) {
                    explanationReasons.add("Recovery: " +
                            String.format(Locale.US, "%.0f%%", recoveryScore * 100) + " from peak");
                }
                for (String comorbidity : comorbidities) {
                    explanationReasons.add("Pattern: " + comorbidity.replace("_", " "));
                }

                // Add momentum signals
                for (CategoryTrend trend : allTrends) {
                    if (trend.isRapidChange()) {
                        explanationReasons.add(trend.uiText + " (" +
                                trend.category.replace("_", " ") + ")");
                    }
                }

                // Add drift warnings
                for (String drift : driftSignals) {
                    explanationReasons.add("Drift: " + drift.replace("_", " "));
                }

                insights = insights.withClassification(latestClassification, explanationReasons);

                // A new classification was just persisted by MultiModalClassifier.classify() —
                // invalidate the trend cache so the trajectory block below re-runs with fresh data.
                invalidateTrendCache();
            } catch (Exception e) {
                // Fallback: classification failed, insights remain unchanged
            }
        }

        // ── Phase 6: Longitudinal trajectory enrichment (cached) ──
        if (classificationRepo != null) {
            try {
                long now = System.currentTimeMillis();
                boolean cacheValid = cachedTrendReport != null
                        && (now - lastTrendTimestamp) < TREND_CACHE_TTL_MS;

                if (!cacheValid) {
                    ClassificationTrendAnalyzer trendAnalyzer =
                            new ClassificationTrendAnalyzer(classificationRepo.getDao());
                    cachedTrendReport = trendAnalyzer.analyzeTrend(7);
                    lastTrendTimestamp = now;

                    if (cachedTrendReport != null) {
                        cachedTrajectoryLabel = cachedTrendReport.overallRiskTrajectory;
                        cachedTrajectorySummary = buildTrajectorySummary(cachedTrendReport);
                    } else {
                        cachedTrajectoryLabel = null;
                        cachedTrajectorySummary = null;
                    }
                }

                if (cachedTrajectoryLabel != null) {
                    insights = insights.withTrajectory(cachedTrajectoryLabel, cachedTrajectorySummary);

                    // ── Phase 6: Proactive trajectory notification ──
                    if ("rapidly_worsening".equals(cachedTrajectoryLabel) && appContextRef != null) {
                        Context ctx = appContextRef.get();
                        if (ctx != null && cachedTrendReport != null) {
                            String worseningCat = cachedTrendReport.getFastestWorseningCategory();
                            int riskPct = latestClassification != null
                                    ? Math.round(latestClassification.overallRiskScore * 100)
                                    : 0;
                            CrisisNotificationManager.notifyTrajectoryIfNeeded(
                                    ctx, cachedTrajectoryLabel, worseningCat, riskPct);
                        }
                    }
                }
            } catch (Exception e) {
                // Trajectory analysis failed; insights remain unchanged
            }
        }

        return insights;
    }

    /** Get the latest cached TrendReport (may be null if not yet computed). */
    public ClassificationTrendAnalyzer.TrendReport getLatestTrendReport() {
        return cachedTrendReport;
    }

    /** Invalidate the trajectory cache (call after a new classification is persisted). */
    public void invalidateTrendCache() {
        lastTrendTimestamp = 0;
        cachedTrendReport = null;
        cachedTrajectoryLabel = null;
        cachedTrajectorySummary = null;
    }

    public DashboardInsights analyze(InsightInput input) {
        long screenTime = Math.max(input.screenTimeMillis, 0L);
        QuestionnaireResponse response = input.latestResponse;
        DailyUsage latestUsage = input.latestUsage;

        int stressLevel = response == null ? 0 : clampScale(response.stressLevel);
        int lonelinessLevel = response == null ? 0 : clampScale(response.lonelinessLevel);
        int motivationLevel = response == null ? 0 : clampScale(response.motivationLevel);
        float sleepHours = response == null ? 0f : response.sleepHours;
        String mood = response == null ? "Unknown" : safe(response.mood, "Unknown");
        String focusLevel = response == null ? "Unknown" : safe(response.focusLevel, "Unknown");
        String energyLevel = response == null ? "Unknown" : safe(response.energyLevel, "Unknown");
        String workPressure = response == null ? "Unknown" : safe(response.workPressure, "Unknown");
        boolean socialSupport = response != null && response.socialSupport;
        boolean goalClarity = response != null && response.goalClarity;
        boolean feltDistracted = response != null && Boolean.TRUE.equals(response.feltDistracted);
        int fulfillmentScore = input.fulfillmentScore;
        long weeklyAverage = input.weeklyAverageScreenTimeMillis;
        long nightUsageMillis = latestUsage == null ? 0L : latestUsage.nightUsageMillis;

        boolean highUsage = screenTime >= HIGH_SCREEN_TIME_THRESHOLD_MILLIS;
        boolean moderateUsage = screenTime >= MODERATE_SCREEN_TIME_THRESHOLD_MILLIS;
        boolean usageAboveAverage = weeklyAverage > 0L && screenTime > weeklyAverage * 1.25f;
        boolean lowSleep = sleepHours > 0f && sleepHours < LOW_SLEEP_THRESHOLD_HOURS;
        boolean highStress = stressLevel >= 4;
        boolean highLoneliness = lonelinessLevel >= 4;
        boolean lowMotivation = motivationLevel > 0 && motivationLevel <= 2;
        boolean lowEnergy = "Low".equalsIgnoreCase(energyLevel);
        boolean lowFocus = "Low".equalsIgnoreCase(focusLevel);
        boolean difficultMood = "Sad".equalsIgnoreCase(mood) || "Anxious".equalsIgnoreCase(mood);
        boolean highWorkPressure = "High".equalsIgnoreCase(workPressure);
        boolean lateNightUse = nightUsageMillis >= LATE_NIGHT_USAGE_THRESHOLD_MILLIS;
        boolean overusedApp = input.mostUsedApp != null && input.mostUsedApp.usageTime >= APP_WARNING_THRESHOLD_MILLIS;

        BehaviorEvaluation behaviorEvaluation = evaluateBehavior(input.behaviorReport, input.behaviorHistory);
        AnomalyEvaluation anomalyEvaluation = evaluateAnomalies(input.anomalyProfile);

        int riskScore = 0;
        if (highUsage) riskScore += 2;
        else if (moderateUsage) riskScore += 1;
        if (usageAboveAverage) riskScore += 1;
        if (overusedApp) riskScore += 1;
        if (lateNightUse) riskScore += 1;
        if (highStress) riskScore += 1;
        if (difficultMood) riskScore += 1;
        if (lowSleep) riskScore += 1;
        if (highLoneliness) riskScore += 1;
        if (lowMotivation) riskScore += 1;
        if (highWorkPressure) riskScore += 1;
        if (feltDistracted && lowFocus) riskScore += 1;
        if (lowEnergy) riskScore += 1;
        if (input.hasTaskHistory && fulfillmentScore < 40) riskScore += 1;
        if (highUsage && highStress) riskScore += 1;
        riskScore += behaviorEvaluation.fallbackRiskPoints;
        riskScore += anomalyEvaluation.fallbackRiskPoints;

        DashboardInsights.RiskLevel fallbackRiskLevel;
        String fallbackRiskLabel;
        if (riskScore >= 6) {
            fallbackRiskLevel = DashboardInsights.RiskLevel.HIGH;
            fallbackRiskLabel = "HIGH";
        } else if (riskScore >= 3) {
            fallbackRiskLevel = DashboardInsights.RiskLevel.MODERATE;
            fallbackRiskLabel = "MODERATE";
        } else {
            fallbackRiskLevel = DashboardInsights.RiskLevel.LOW;
            fallbackRiskLabel = "LOW";
        }

        PersonalizationEngine.Result personalizationResult =
                personalizationEngine.analyze(input.userBaseline, screenTime, response, fulfillmentScore);
        double combinedPersonalizedScore = clampScore(
                personalizationResult.compositeScore
                        + behaviorEvaluation.personalizedModifier
                        + anomalyEvaluation.personalizedModifier
        );
        DashboardInsights.RiskLevel riskLevel = personalizationResult.personalized
                ? scoreToRiskLevel(combinedPersonalizedScore)
                : fallbackRiskLevel;
        String riskLabel = personalizationResult.personalized
                ? scoreToRiskLabel(combinedPersonalizedScore)
                : fallbackRiskLabel;

        String topAppName = input.mostUsedApp != null ? input.mostUsedApp.appName : "No app data yet";
        long topAppUsage = input.mostUsedApp != null ? input.mostUsedApp.usageTime : 0L;
        String stateLabel = humanizeState(input.currentState);
        String featureSummary = buildFeatureSummary(input.behaviorFeatures);

        String basePatternSummary = personalizationResult.personalized
                ? personalizationResult.screenTimeMessage
                : buildWeeklyPatternSummary(screenTime, weeklyAverage, overusedApp, topAppName);
        String weeklyPatternSummary = combinePatternSummaries(
                basePatternSummary,
                behaviorEvaluation.trendSummary,
                anomalyEvaluation.summary
        );

        String fallbackExplanationText = buildExplanation(
                highUsage,
                usageAboveAverage,
                highStress,
                difficultMood,
                lowSleep,
                lowMotivation,
                input.hasTaskHistory,
                fulfillmentScore,
                topAppName
        );

        String deterministicExplanation = chooseDeterministicExplanation(
                personalizationResult,
                behaviorEvaluation,
                anomalyEvaluation,
                fallbackExplanationText
        );

        String personalizedComparisons = personalizationResult.personalized
                ? personalizationResult.comparisons
                : "Baseline not ready yet, so MindTrace is using general wellbeing thresholds today.";

        List<String> reasonItems = buildReasonItems(
                screenTime,
                weeklyAverage,
                stressLevel,
                sleepHours,
                mood,
                workPressure,
                fulfillmentScore,
                input.hasTaskHistory,
                lateNightUse,
                topAppName,
                topAppUsage,
                behaviorEvaluation,
                anomalyEvaluation,
                input.aiInsightResult
        );
        String reasonSummary = joinReasons(reasonItems);

        String deterministicAction = buildRecommendation(
                highUsage,
                highStress,
                lowSleep,
                feltDistracted,
                lowFocus,
                lowEnergy,
                overusedApp,
                lateNightUse,
                lowMotivation,
                socialSupport,
                goalClarity,
                fulfillmentScore,
                input.hasTaskHistory,
                behaviorEvaluation,
                anomalyEvaluation
        );

        boolean aiEnhanced = input.aiInsightResult != null
                && input.aiInsightResult.summary != null
                && !input.aiInsightResult.summary.trim().isEmpty();

        String primaryInsight = aiEnhanced ? input.aiInsightResult.summary : deterministicExplanation;
        String explanationText = aiEnhanced
                ? primaryInsight + " " + deterministicExplanation
                : deterministicExplanation;
        String nextBestAction = aiEnhanced
                && input.aiInsightResult.recommendation != null
                && !input.aiInsightResult.recommendation.trim().isEmpty()
                ? input.aiInsightResult.recommendation
                : deterministicAction;

        boolean supportRecommended = riskLevel == DashboardInsights.RiskLevel.HIGH
                && difficultMood
                && (highLoneliness || lowSleep || !socialSupport || lowMotivation);

        String overuseMessage = overusedApp
                ? topAppName + " crossed 2 hours and is dominating today's usage."
                : "No app has crossed the 2-hour overuse threshold today.";

        return new DashboardInsights(
                riskLevel,
                riskLabel,
                input.currentState,
                stateLabel,
                primaryInsight,
                aiEnhanced ? anomalyEvaluation.summary : (personalizationResult.personalized ? personalizedComparisons : behaviorEvaluation.explanation),
                nextBestAction,
                explanationText,
                reasonSummary,
                weeklyPatternSummary,
                nextBestAction,
                personalizationResult.personalized,
                personalizationResult.personalized ? combinedPersonalizedScore : 0d,
                personalizationResult.personalized
                        ? personalizationResult.summaryMessage
                        : "MindTrace will personalize these insights after it learns your baseline.",
                personalizedComparisons,
                behaviorEvaluation.summaryLabel,
                behaviorEvaluation.explanation,
                behaviorEvaluation.trendSummary,
                reasonItems,
                aiEnhanced,
                aiEnhanced ? input.aiInsightResult.summary : "MindTrace is using your recent patterns to generate a local wellness insight.",
                anomalyEvaluation.summary,
                featureSummary,
                topAppName,
                topAppUsage,
                overusedApp,
                overuseMessage,
                fulfillmentScore,
                supportRecommended
        );
    }

    private BehaviorEvaluation evaluateBehavior(
            BehaviorReport report,
            List<BehaviorSnapshotEntity> history
    ) {
        if (report == null) {
            return new BehaviorEvaluation(
                    "Behavior data unavailable",
                    "Behavior analysis is still loading for this session.",
                    "MindTrace will compare today's behavior once event analysis is available.",
                    new ArrayList<>(),
                    0,
                    0d,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }

        List<String> reasons = new ArrayList<>(report.reasoningNotes == null ? new ArrayList<>() : report.reasoningNotes);
        int fallbackRiskPoints = 0;
        double personalizedModifier = 0d;
        boolean fragmentationConcern = false;
        boolean bingeConcern = false;
        boolean loopConcern = false;
        boolean lateNightConcern = false;
        boolean dominantAppConcern = false;

        if (!report.dataAvailable) {
            return new BehaviorEvaluation(
                    report.summaryLabel,
                    report.explanation,
                    "Behavior history could not be compared today because event access is unavailable.",
                    reasons,
                    0,
                    0d,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }

        if (report.totalForegroundMillis <= 0L) {
            reasons.clear();
            reasons.add("No meaningful foreground behavior was recorded today.");
            return new BehaviorEvaluation(
                    report.summaryLabel,
                    report.explanation,
                    "Very little device activity was available to compare against recent days.",
                    reasons,
                    0,
                    -0.05d,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }

        if (report.appSwitchCount >= BehaviorThresholds.MILD_SWITCH_THRESHOLD
                || report.rapidSwitchCount >= BehaviorThresholds.RAPID_SWITCH_SIGNAL_THRESHOLD
                || report.shortSessionCount >= BehaviorThresholds.SHORT_SESSION_SIGNAL_THRESHOLD) {
            fragmentationConcern = true;
            fallbackRiskPoints += report.rapidSwitchCount >= BehaviorThresholds.HIGH_RAPID_SWITCH_THRESHOLD ? 2 : 1;
            personalizedModifier += report.rapidSwitchCount >= BehaviorThresholds.HIGH_RAPID_SWITCH_THRESHOLD ? 0.14d : 0.08d;
        }

        if (report.bingeSessionCount > 0 || report.longestSessionMillis >= BehaviorThresholds.EXTREME_SESSION_THRESHOLD_MILLIS) {
            bingeConcern = true;
            fallbackRiskPoints += report.longestSessionMillis >= BehaviorThresholds.EXTREME_SESSION_THRESHOLD_MILLIS ? 2 : 1;
            personalizedModifier += report.longestSessionMillis >= BehaviorThresholds.EXTREME_SESSION_THRESHOLD_MILLIS ? 0.12d : 0.08d;
        }

        if (report.hasLoopPattern) {
            loopConcern = true;
            fallbackRiskPoints += 1;
            personalizedModifier += 0.10d;
        }

        if (report.lateNightUsageMillis >= BehaviorThresholds.LATE_NIGHT_SIGNAL_THRESHOLD_MILLIS) {
            lateNightConcern = true;
            fallbackRiskPoints += report.lateNightUsageRatio >= BehaviorThresholds.HEAVY_LATE_NIGHT_RATIO_THRESHOLD ? 2 : 1;
            personalizedModifier += report.lateNightUsageRatio >= BehaviorThresholds.HEAVY_LATE_NIGHT_RATIO_THRESHOLD ? 0.12d : 0.08d;
        }

        if (report.dominantUsageRatio >= BehaviorThresholds.DOMINANT_APP_RATIO_THRESHOLD) {
            dominantAppConcern = true;
            fallbackRiskPoints += 1;
            personalizedModifier += 0.06d;
        }

        TrendEvaluation trendEvaluation = compareBehaviorToHistory(report, history);
        if (trendEvaluation.reason != null && !trendEvaluation.reason.isEmpty()) {
            reasons.add(trendEvaluation.reason);
        }
        fallbackRiskPoints += trendEvaluation.fallbackRiskPoints;
        personalizedModifier += trendEvaluation.personalizedModifier;

        if (!fragmentationConcern && !bingeConcern && !loopConcern && !lateNightConcern && !dominantAppConcern) {
            personalizedModifier -= 0.04d;
        }

        return new BehaviorEvaluation(
                safe(report.summaryLabel, "Healthy balance"),
                safe(report.explanation, "Behavior signals look relatively balanced today."),
                trendEvaluation.summary,
                reasons,
                fallbackRiskPoints,
                personalizedModifier,
                fragmentationConcern,
                bingeConcern,
                loopConcern,
                lateNightConcern,
                dominantAppConcern
        );
    }

    private TrendEvaluation compareBehaviorToHistory(
            BehaviorReport report,
            List<BehaviorSnapshotEntity> history
    ) {
        if (history == null || history.isEmpty()) {
            return new TrendEvaluation(
                    "Behavior baseline is still being learned from recent snapshots.",
                    "",
                    0,
                    0d
            );
        }

        long currentDay = getStartOfDay(report.analysisStartTime);
        int count = 0;
        double averageSwitches = 0d;
        double averageRapidSwitches = 0d;
        double averageLateNightMillis = 0d;

        for (BehaviorSnapshotEntity snapshot : history) {
            if (snapshot.dayTimestamp == currentDay) {
                continue;
            }
            averageSwitches += snapshot.appSwitchCount;
            averageRapidSwitches += snapshot.rapidSwitchCount;
            averageLateNightMillis += snapshot.lateNightUsageMillis;
            count++;
        }

        if (count == 0) {
            return new TrendEvaluation(
                    "Behavior baseline is still being learned from recent snapshots.",
                    "",
                    0,
                    0d
            );
        }

        averageSwitches /= count;
        averageRapidSwitches /= count;
        averageLateNightMillis /= count;

        double fragmentationRatio = calculateRatio(
                report.appSwitchCount + report.rapidSwitchCount,
                averageSwitches + averageRapidSwitches
        );
        double lateNightRatio = calculateRatio(report.lateNightUsageMillis, averageLateNightMillis);

        if (fragmentationRatio > 1.25d) {
            return new TrendEvaluation(
                    "Usage looks more fragmented than your recent behavior baseline.",
                    "Usage appears more fragmented than your normal pattern.",
                    1,
                    0.06d
            );
        }
        if (lateNightRatio > 1.25d && report.lateNightUsageMillis >= BehaviorThresholds.LATE_NIGHT_SIGNAL_THRESHOLD_MILLIS) {
            return new TrendEvaluation(
                    "Late-night phone activity is higher than your recent pattern.",
                    "Late-night usage is rising compared with recent days.",
                    1,
                    0.05d
            );
        }
        if (fragmentationRatio > 0d && fragmentationRatio < 0.8d) {
            return new TrendEvaluation(
                    "Switching looks calmer than your recent pattern.",
                    "",
                    0,
                    -0.04d
            );
        }
        if (lateNightRatio > 0d && lateNightRatio < 0.8d && averageLateNightMillis > 0d) {
            return new TrendEvaluation(
                    "Late-night usage is lower than your recent pattern.",
                    "",
                    0,
                    -0.03d
            );
        }
        return new TrendEvaluation(
                "Today's behavior signals are close to your recent baseline.",
                "",
                0,
                0d
        );
    }

    private AnomalyEvaluation evaluateAnomalies(AnomalyDetector.AnomalyProfile anomalyProfile) {
        if (anomalyProfile == null) {
            return new AnomalyEvaluation(
                    "Anomaly scan unavailable for this session.",
                    new ArrayList<>(),
                    0,
                    0d,
                    false,
                    false,
                    false,
                    false
            );
        }

        double modifier = 0d;
        boolean screenTimeConcern = false;
        boolean sleepConcern = false;
        boolean stressConcern = false;
        boolean taskConcern = false;

        if (anomalyProfile.screenTime != null
                && anomalyProfile.screenTime.level != AnomalyDetector.Level.NORMAL
                && anomalyProfile.screenTime.zScore > 0d) {
            screenTimeConcern = true;
            modifier += anomalyProfile.screenTime.level == AnomalyDetector.Level.ANOMALY ? 0.08d : 0.04d;
        }
        if (anomalyProfile.sleep != null
                && anomalyProfile.sleep.level != AnomalyDetector.Level.NORMAL
                && anomalyProfile.sleep.zScore < 0d) {
            sleepConcern = true;
            modifier += anomalyProfile.sleep.level == AnomalyDetector.Level.ANOMALY ? 0.08d : 0.04d;
        }
        if (anomalyProfile.stress != null
                && anomalyProfile.stress.level != AnomalyDetector.Level.NORMAL
                && anomalyProfile.stress.zScore > 0d) {
            stressConcern = true;
            modifier += anomalyProfile.stress.level == AnomalyDetector.Level.ANOMALY ? 0.08d : 0.04d;
        }
        if (anomalyProfile.taskCompletion != null
                && anomalyProfile.taskCompletion.level != AnomalyDetector.Level.NORMAL
                && anomalyProfile.taskCompletion.zScore < 0d) {
            taskConcern = true;
            modifier += anomalyProfile.taskCompletion.level == AnomalyDetector.Level.ANOMALY ? 0.06d : 0.03d;
        }

        return new AnomalyEvaluation(
                anomalyProfile.summary,
                anomalyProfile.issues == null ? new ArrayList<>() : anomalyProfile.issues,
                anomalyProfile.riskPoints,
                modifier,
                screenTimeConcern,
                sleepConcern,
                stressConcern,
                taskConcern
        );
    }

    private List<String> buildReasonItems(
            long screenTime,
            long weeklyAverage,
            int stressLevel,
            float sleepHours,
            String mood,
            String workPressure,
            int fulfillmentScore,
            boolean hasTaskHistory,
            boolean lateNightUse,
            String topAppName,
            long topAppUsage,
            BehaviorEvaluation behaviorEvaluation,
            AnomalyEvaluation anomalyEvaluation,
            AiInsightResult aiInsightResult
    ) {
        List<String> reasons = new ArrayList<>();
        reasons.add("Screen time " + formatDuration(screenTime));
        if (weeklyAverage > 0L) {
            reasons.add(calculateTrendLabel(screenTime, weeklyAverage));
        }
        if (sleepHours > 0f) {
            reasons.add("Sleep " + formatHours(sleepHours) + "h");
        }
        if (stressLevel > 0) {
            reasons.add("Stress " + stressLevel + "/5");
        }
        if (mood != null && !"Unknown".equalsIgnoreCase(mood)) {
            reasons.add("Mood " + mood);
        }
        if (workPressure != null && !"Unknown".equalsIgnoreCase(workPressure)) {
            reasons.add("Work pressure " + workPressure);
        }
        if (topAppName != null && topAppUsage > 0L) {
            reasons.add("Top app " + topAppName + " (" + formatDuration(topAppUsage) + ")");
        }
        if (lateNightUse) {
            reasons.add("Late-night usage detected");
        }
        if (hasTaskHistory) {
            reasons.add("Task completion " + fulfillmentScore + "%");
        }
        reasons.addAll(behaviorEvaluation.reasons);
        reasons.addAll(anomalyEvaluation.issues);
        if (aiInsightResult != null && aiInsightResult.issues != null) {
            reasons.addAll(aiInsightResult.issues);
        }
        return reasons;
    }

    private String chooseDeterministicExplanation(
            PersonalizationEngine.Result personalizationResult,
            BehaviorEvaluation behaviorEvaluation,
            AnomalyEvaluation anomalyEvaluation,
            String fallbackExplanationText
    ) {
        if (behaviorEvaluation.hasConcern()) {
            if (personalizationResult.personalized) {
                return behaviorEvaluation.explanation + " " + personalizationResult.summaryMessage;
            }
            return behaviorEvaluation.explanation;
        }
        if (anomalyEvaluation.hasConcern()) {
            return anomalyEvaluation.summary;
        }
        if (personalizationResult.personalized) {
            return personalizationResult.summaryMessage;
        }
        return fallbackExplanationText;
    }

    private String buildFeatureSummary(BehaviorFeatures behaviorFeatures) {
        if (behaviorFeatures == null || behaviorFeatures.sessionCount <= 0) {
            return "Feature extraction is waiting for more session data.";
        }
        return String.format(
                Locale.US,
                "Avg session %s | Switch rate %.1f per hour | Diversity %.2f | Late-night ratio %.0f%%",
                formatDuration((long) behaviorFeatures.avgSessionLength),
                behaviorFeatures.switchRate,
                behaviorFeatures.appDiversityScore,
                behaviorFeatures.lateNightRatio * 100d
        );
    }

    private String combinePatternSummaries(String... summaries) {
        StringBuilder builder = new StringBuilder();
        for (String summary : summaries) {
            if (summary == null || summary.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(summary);
        }
        return builder.toString();
    }

    private String buildExplanation(
            boolean highUsage,
            boolean usageAboveAverage,
            boolean highStress,
            boolean difficultMood,
            boolean lowSleep,
            boolean lowMotivation,
            boolean hasTaskHistory,
            int fulfillmentScore,
            String topAppName
    ) {
        if (highUsage && lowSleep) {
            return "Digital overload risk is rising because high screen time is appearing alongside reduced sleep.";
        }
        if (highStress && difficultMood) {
            return "Your latest check-in points to emotional strain, with stress and mood pressure rising together.";
        }
        if (usageAboveAverage && highUsage) {
            return "Today's device use is running meaningfully above your recent pattern, which can amplify mental fatigue.";
        }
        if (lowMotivation && hasTaskHistory && fulfillmentScore < 40) {
            return "Engagement looks lower today, with motivation dipping and intervention follow-through staying limited.";
        }
        if (topAppName != null && !"No app data yet".equals(topAppName)) {
            return topAppName + " is shaping a large share of today's digital behavior.";
        }
        return "Your current signals look relatively balanced, with no dominant high-risk pattern right now.";
    }

    private String buildWeeklyPatternSummary(long screenTime, long weeklyAverage, boolean overusedApp, String topAppName) {
        if (weeklyAverage <= 0L) {
            return overusedApp
                    ? topAppName + " is the strongest usage signal today, and it has already crossed 2 hours."
                    : "MindTrace is still building your weekly baseline for better comparisons.";
        }

        return calculateTrendLabel(screenTime, weeklyAverage)
                + (overusedApp ? " " + topAppName + " is also showing heavy use today." : "");
    }

    private String buildRecommendation(
            boolean highUsage,
            boolean highStress,
            boolean lowSleep,
            boolean feltDistracted,
            boolean lowFocus,
            boolean lowEnergy,
            boolean overusedApp,
            boolean lateNightUse,
            boolean lowMotivation,
            boolean socialSupport,
            boolean goalClarity,
            int fulfillmentScore,
            boolean hasTaskHistory,
            BehaviorEvaluation behaviorEvaluation,
            AnomalyEvaluation anomalyEvaluation
    ) {
        if (behaviorEvaluation.lateNightConcern || anomalyEvaluation.sleepConcern || lowSleep || lateNightUse) {
            return "Avoid phone use before sleep, dim the device early tonight, and choose one low-screen wind-down activity.";
        }
        if (behaviorEvaluation.bingeConcern || anomalyEvaluation.screenTimeConcern || highUsage || overusedApp) {
            return "Take a 30-minute offline break and move to a different environment if you can.";
        }
        if (behaviorEvaluation.fragmentationConcern || behaviorEvaluation.loopConcern || highStress || anomalyEvaluation.stressConcern || (feltDistracted && lowFocus)) {
            return "Silence non-essential notifications and stay with one focused offline task for the next 10 minutes.";
        }
        if (behaviorEvaluation.dominantAppConcern) {
            return "Place a deliberate boundary around the app dominating today and switch to one offline activity before reopening it.";
        }
        if (lowMotivation && hasTaskHistory && fulfillmentScore < 40 || anomalyEvaluation.taskConcern) {
            return "Pick one very short intervention task to rebuild momentum instead of trying to fix everything at once.";
        }
        if (!socialSupport) {
            return "Reach out to one trusted person with a short message and let them know how your day is going.";
        }
        if (!goalClarity) {
            return "Write down one concrete next step for today so your attention has a single target.";
        }
        if (lowEnergy) {
            return "Step away from the screen briefly, hydrate, and choose a lighter recovery task.";
        }
        return "Maintain the current balance with a short mindful break and one intentional offline activity.";
    }

    private int calculateFulfillmentScore(List<InterventionTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        int completed = 0;
        for (InterventionTask task : tasks) {
            if (task.isCompleted) {
                completed++;
            }
        }
        return Math.round((completed * 100f) / tasks.size());
    }

    private long calculateWeeklyAverageScreenTime(List<DailyUsage> usageHistory) {
        if (usageHistory == null || usageHistory.isEmpty()) {
            return 0L;
        }

        int count = Math.min(7, usageHistory.size());
        long total = 0L;
        for (int i = 0; i < count; i++) {
            total += Math.max(usageHistory.get(i).screenTimeMillis, 0L);
        }
        return count == 0 ? 0L : total / count;
    }

    private String calculateTrendLabel(long current, long baseline) {
        if (baseline <= 0L) {
            return "No weekly baseline yet";
        }

        double deltaRatio = ((current - baseline) * 100.0) / baseline;
        String direction = deltaRatio >= 0 ? "above" : "below";
        return String.format(Locale.US, "%.0f%% %s your recent average", Math.abs(deltaRatio), direction);
    }

    private String joinReasons(List<String> reasons) {
        StringBuilder builder = new StringBuilder();
        for (String reason : reasons) {
            if (reason == null || reason.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(reason);
        }
        return builder.toString();
    }

    private int clampScale(int value) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(value, 5);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private long getStartOfDay(long timestamp) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private double calculateRatio(double currentValue, double baselineValue) {
        if (baselineValue <= 0d || currentValue <= 0d) {
            return 0d;
        }
        return currentValue / baselineValue;
    }

    private double clampScore(double score) {
        return Math.max(0d, Math.min(1d, score));
    }

    private DashboardInsights.RiskLevel scoreToRiskLevel(double score) {
        if (score < 0.4d) {
            return DashboardInsights.RiskLevel.LOW;
        }
        if (score < 0.7d) {
            return DashboardInsights.RiskLevel.MODERATE;
        }
        return DashboardInsights.RiskLevel.HIGH;
    }

    private String scoreToRiskLabel(double score) {
        if (score < 0.4d) {
            return "HEALTHY";
        }
        if (score < 0.7d) {
            return "MODERATE";
        }
        return "HIGH RISK";
    }

    private String formatDuration(long millis) {
        if (millis <= 0L) {
            return "0m";
        }

        long hours = millis / (60L * 60L * 1000L);
        long minutes = (millis % (60L * 60L * 1000L)) / (60L * 1000L);
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String formatHours(float hours) {
        if (hours <= 0f) {
            return "0.0";
        }
        return String.format(Locale.US, "%.1f", hours);
    }

    private String humanizeState(MentalStateClassifier.State state) {
        switch (state) {
            case DIGITAL_ADDICTION:
                return "Digital Dependence";
            case STRESS_ANXIETY:
                return "Stress & Anxiety";
            case LOW_PURPOSE:
                return "Low Purpose";
            case EMOTIONAL_FATIGUE:
                return "Emotional Fatigue";
            case SOCIAL_ISOLATION:
                return "Social Isolation";
            case EARLY_DEPRESSION_RISK:
                return "Early Depression Risk";
            case STABLE:
            default:
                return "Stable";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Phase 6: Trajectory summary builder
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Build a human-readable one-sentence trajectory summary from
     * a {@link ClassificationTrendAnalyzer.TrendReport}.
     */
    private String buildTrajectorySummary(ClassificationTrendAnalyzer.TrendReport trend) {
        int avgPct = Math.round(trend.averageRisk * 100);
        int currentPct = Math.round(trend.currentRisk * 100);

        String direction;
        switch (trend.overallRiskTrajectory) {
            case "rapidly_improving":
                direction = "rapidly improving"; break;
            case "gradually_improving":
                direction = "steadily improving"; break;
            case "gradually_worsening":
                direction = "gradually worsening"; break;
            case "rapidly_worsening":
                direction = "rapidly worsening"; break;
            default:
                direction = "stable"; break;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "Your 7-day risk is %s (avg %d%%, now %d%%).", direction, avgPct, currentPct));

        // Mention the fastest-moving categories if meaningful
        String worsening = trend.getFastestWorseningCategory();
        String improving = trend.getFastestImprovingCategory();
        if (worsening != null && !worsening.isEmpty() && !worsening.equals("—")) {
            sb.append(" ").append(worsening).append(" is rising fastest.");
        }
        if (improving != null && !improving.isEmpty() && !improving.equals("—")) {
            sb.append(" ").append(improving).append(" is your strongest improvement.");
        }

        // Crisis warning
        if (trend.crisisCount > 0) {
            sb.append(String.format(Locale.US,
                    " ⚠ %d crisis flag(s) in this window.", trend.crisisCount));
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3.I.9 — "Why This Score" explanation bullets
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate human-readable explanation reasons for the classification.
     * Used to populate the dashboard "Why This Score" card.
     */
    public List<String> buildExplanationReasons(
            RiskClassification rc, FeatureVector fv) {
        List<String> reasons = new ArrayList<>();
        if (rc == null) return reasons;

        // Add severity-based opening
        RiskClassification.Severity overall = rc.getOverallSeverity();
        if (overall.level >= 4) {
            reasons.add("⚠ Overall risk is " + overall.name().toLowerCase() +
                    " (" + pct(rc.overallRiskScore) + ")");
        }

        // Per-category contributions (only elevated ones)
        if (rc.digitalAddictionScore > 0.4f)
            reasons.add("Digital dependency: " + pct(rc.digitalAddictionScore));
        if (rc.stressAnxietyScore > 0.4f)
            reasons.add("Stress & anxiety: " + pct(rc.stressAnxietyScore));
        if (rc.depressionRiskScore > 0.4f)
            reasons.add("Depression indicators: " + pct(rc.depressionRiskScore));
        if (rc.socialIsolationScore > 0.4f)
            reasons.add("Social isolation: " + pct(rc.socialIsolationScore));
        if (rc.sleepDisruptionScore > 0.4f)
            reasons.add("Sleep disruption: " + pct(rc.sleepDisruptionScore));
        if (rc.lowFulfilmentScore > 0.4f)
            reasons.add("Low fulfilment: " + pct(rc.lowFulfilmentScore));

        // Top 3 feature drivers
        if (multiModalClassifier != null && fv != null && rc.primaryCategory != null) {
            String[] tops = multiModalClassifier.getTopContributors(fv, rc.primaryCategory, 3);
            for (String feat : tops) {
                reasons.add("Driven by: " + humanizeFeature(feat));
            }
        }

        // Trajectory
        if (rc.isWorsening())
            reasons.add("Trend: worsening over recent days");
        else if (rc.isImproving())
            reasons.add("Trend: improving over recent days");

        // Crisis
        if (rc.crisisFlag && rc.crisisReason != null)
            reasons.add("⚠ Crisis flag: " + rc.crisisReason);

        // Confidence caveat
        if (rc.confidence < 0.5f)
            reasons.add("Note: limited data — confidence " + pct(rc.confidence));

        return reasons;
    }

    /**
     * Map the MultiModal classification to the existing DashboardInsights.RiskLevel.
     * (3.I.6 — bridges old and new risk systems)
     */
    private DashboardInsights.RiskLevel classificationToRiskLevel(RiskClassification rc) {
        if (rc == null) return null;
        float s = rc.overallRiskScore;
        if (s >= 0.65f) return DashboardInsights.RiskLevel.HIGH;
        if (s >= 0.35f) return DashboardInsights.RiskLevel.MODERATE;
        return DashboardInsights.RiskLevel.LOW;
    }

    private String pct(float v) {
        return String.format(Locale.US, "%.0f%%", v * 100f);
    }

    private String humanizeFeature(String name) {
        if (name == null) return "";
        return name
                .replace("Deficit", " levels")
                .replace("Score", "")
                .replaceAll("([A-Z])", " $1")
                .toLowerCase().trim();
    }

    public static class InsightInput {
        public final long screenTimeMillis;
        public final AppUsageModel mostUsedApp;
        public final QuestionnaireResponse latestResponse;
        public final DailyUsage latestUsage;
        public final long weeklyAverageScreenTimeMillis;
        public final int fulfillmentScore;
        public final boolean hasTaskHistory;
        public final MentalStateClassifier.State currentState;
        public final UserBaseline userBaseline;
        public final BehaviorReport behaviorReport;
        public final List<BehaviorSnapshotEntity> behaviorHistory;
        public final BehaviorFeatures behaviorFeatures;
        public final AnomalyDetector.AnomalyProfile anomalyProfile;
        public final AiInsightResult aiInsightResult;

        public InsightInput(
                long screenTimeMillis,
                AppUsageModel mostUsedApp,
                QuestionnaireResponse latestResponse,
                DailyUsage latestUsage,
                long weeklyAverageScreenTimeMillis,
                int fulfillmentScore,
                boolean hasTaskHistory,
                MentalStateClassifier.State currentState,
                UserBaseline userBaseline,
                BehaviorReport behaviorReport,
                List<BehaviorSnapshotEntity> behaviorHistory,
                BehaviorFeatures behaviorFeatures,
                AnomalyDetector.AnomalyProfile anomalyProfile,
                AiInsightResult aiInsightResult
        ) {
            this.screenTimeMillis = screenTimeMillis;
            this.mostUsedApp = mostUsedApp;
            this.latestResponse = latestResponse;
            this.latestUsage = latestUsage;
            this.weeklyAverageScreenTimeMillis = weeklyAverageScreenTimeMillis;
            this.fulfillmentScore = fulfillmentScore;
            this.hasTaskHistory = hasTaskHistory;
            this.currentState = currentState;
            this.userBaseline = userBaseline;
            this.behaviorReport = behaviorReport;
            this.behaviorHistory = behaviorHistory;
            this.behaviorFeatures = behaviorFeatures;
            this.anomalyProfile = anomalyProfile;
            this.aiInsightResult = aiInsightResult;
        }
    }

    private static class BehaviorEvaluation {
        final String summaryLabel;
        final String explanation;
        final String trendSummary;
        final List<String> reasons;
        final int fallbackRiskPoints;
        final double personalizedModifier;
        final boolean fragmentationConcern;
        final boolean bingeConcern;
        final boolean loopConcern;
        final boolean lateNightConcern;
        final boolean dominantAppConcern;

        BehaviorEvaluation(
                String summaryLabel,
                String explanation,
                String trendSummary,
                List<String> reasons,
                int fallbackRiskPoints,
                double personalizedModifier,
                boolean fragmentationConcern,
                boolean bingeConcern,
                boolean loopConcern,
                boolean lateNightConcern,
                boolean dominantAppConcern
        ) {
            this.summaryLabel = summaryLabel;
            this.explanation = explanation;
            this.trendSummary = trendSummary;
            this.reasons = reasons;
            this.fallbackRiskPoints = fallbackRiskPoints;
            this.personalizedModifier = personalizedModifier;
            this.fragmentationConcern = fragmentationConcern;
            this.bingeConcern = bingeConcern;
            this.loopConcern = loopConcern;
            this.lateNightConcern = lateNightConcern;
            this.dominantAppConcern = dominantAppConcern;
        }

        boolean hasConcern() {
            return fragmentationConcern || bingeConcern || loopConcern || lateNightConcern || dominantAppConcern;
        }
    }

    private static class TrendEvaluation {
        final String summary;
        final String reason;
        final int fallbackRiskPoints;
        final double personalizedModifier;

        TrendEvaluation(String summary, String reason, int fallbackRiskPoints, double personalizedModifier) {
            this.summary = summary;
            this.reason = reason;
            this.fallbackRiskPoints = fallbackRiskPoints;
            this.personalizedModifier = personalizedModifier;
        }
    }

    private static class AnomalyEvaluation {
        final String summary;
        final List<String> issues;
        final int fallbackRiskPoints;
        final double personalizedModifier;
        final boolean screenTimeConcern;
        final boolean sleepConcern;
        final boolean stressConcern;
        final boolean taskConcern;

        AnomalyEvaluation(
                String summary,
                List<String> issues,
                int fallbackRiskPoints,
                double personalizedModifier,
                boolean screenTimeConcern,
                boolean sleepConcern,
                boolean stressConcern,
                boolean taskConcern
        ) {
            this.summary = summary;
            this.issues = issues;
            this.fallbackRiskPoints = fallbackRiskPoints;
            this.personalizedModifier = personalizedModifier;
            this.screenTimeConcern = screenTimeConcern;
            this.sleepConcern = sleepConcern;
            this.stressConcern = stressConcern;
            this.taskConcern = taskConcern;
        }

        boolean hasConcern() {
            return screenTimeConcern || sleepConcern || stressConcern || taskConcern;
        }
    }
}
