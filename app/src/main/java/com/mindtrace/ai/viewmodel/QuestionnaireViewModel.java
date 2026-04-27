package com.mindtrace.ai.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mindtrace.ai.ai.AnomalyDetector;
import com.mindtrace.ai.ai.CrisisDetector;
import com.mindtrace.ai.ai.InterventionEngine;
import com.mindtrace.ai.ai.LinguisticAnalyzer;
import com.mindtrace.ai.ai.MentalStateClassifier;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.repository.AssessmentRepository;
import com.mindtrace.ai.repository.UsageRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Daily Check-In flow.
 *
 * <p>Orchestrates the full submission pipeline:</p>
 * <ol>
 *   <li>Linguistic analysis on gratitude text (2.E.4)</li>
 *   <li>Auto-compute distress severity + flags (2.E.5)</li>
 *   <li>Save via AssessmentRepository (auto-creates gratitude JournalEntry) (2.E.3)</li>
 *   <li>Classify mental state + detect anomalies</li>
 *   <li>Generate intervention tasks</li>
 *   <li>Crisis detection</li>
 *   <li>Signal dashboard refresh (2.E.6)</li>
 * </ol>
 */
public class QuestionnaireViewModel extends AndroidViewModel {
    private final UsageRepository repository;
    private final AssessmentRepository assessmentRepository;
    private final LinguisticAnalyzer linguisticAnalyzer;
    private final AppDatabase appDatabase;
    private final ExecutorService executorService;
    private final MutableLiveData<SubmissionResult> submissionResult = new MutableLiveData<>();

    /**
     * Flag that signals the dashboard needs to refresh after a check-in.
     * Observed by the Activity to trigger DashboardViewModel.refreshDashboard(). (Task 2.E.6)
     */
    private final MutableLiveData<Boolean> dashboardRefreshNeeded = new MutableLiveData<>(false);

    public QuestionnaireViewModel(@NonNull Application application) {
        super(application);
        repository = new UsageRepository(application);
        assessmentRepository = new AssessmentRepository(application);
        linguisticAnalyzer = new LinguisticAnalyzer();
        appDatabase = AppDatabase.getInstance(application);
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<SubmissionResult> getSubmissionResult() {
        return submissionResult;
    }

    /** Dashboard listens to this to know when to refresh. (Task 2.E.6) */
    public LiveData<Boolean> getDashboardRefreshNeeded() {
        return dashboardRefreshNeeded;
    }

    public void submitResponse(QuestionnaireResponse response) {
        executorService.execute(() -> {
            try {
                // ── Step 1: Linguistic analysis on gratitude text (Task 2.E.4) ──
                if (response.gratitudeText != null && !response.gratitudeText.trim().isEmpty()) {
                    LinguisticAnalyzer.AnalysisResult analysis =
                            linguisticAnalyzer.analyze(response.gratitudeText);
                    if (analysis != null) {
                        // Enrich with NLP-detected cognitive distortions
                        if (analysis.distressFlags != null && !analysis.distressFlags.isEmpty()) {
                            response.distressFlags = analysis.distressFlagsJson();
                        }
                    }
                }

                // ── Step 2: Save via AssessmentRepository (Tasks 2.E.2, 2.E.3, 2.E.5) ──
                // This auto-computes:
                //   - dayTimestamp
                //   - distressSeverity (computeDistressSeverity)
                //   - distressFlags (buildDistressFlags — merges with any NLP flags)
                //   - Auto-creates JournalEntry if gratitudeText is non-empty
                assessmentRepository.saveCheckInSync(response);
                
                // Force a baseline recomputation so that the dashboard instantly
                // reflects the new psychological baseline (sleep, stress, mood)
                repository.computeUserBaseline();

                // ── Step 3: Existing AI pipeline (unchanged) ──
                List<QuestionnaireResponse> recentResponses = repository.getRecentResponsesSync();
                List<DailyUsage> usageHistory = repository.getUsageHistorySinceSync(
                        System.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L)
                );
                List<InterventionTask> recentTasks = repository.getTasksSinceSync(
                        System.currentTimeMillis() - (14L * 24L * 60L * 60L * 1000L)
                );

                MentalStateClassifier classifier = new MentalStateClassifier();
                MentalStateClassifier.State state = classifier.classify(usageHistory, recentResponses);

                BehaviorReport behaviorReport = repository.getTodayBehavior(getApplication());
                UserBaseline baseline = repository.getUserBaselineSync();
                AnomalyDetector anomalyDetector = new AnomalyDetector();
                AnomalyDetector.AnomalyProfile anomalyProfile = anomalyDetector.buildProfile(
                        usageHistory == null || usageHistory.isEmpty() ? 0L : usageHistory.get(0).screenTimeMillis,
                        usageHistory,
                        response,
                        recentResponses,
                        calculateFulfillmentScore(recentTasks),
                        recentTasks,
                        baseline
                );

                InterventionEngine interventionEngine = new InterventionEngine();

                // ── Closed-loop: Finalize expired observation windows ──
                try {
                    long now = System.currentTimeMillis();
                    float currentRisk = state.ordinal() * 0.15f;
                    List<InterventionTask> awaiting = appDatabase.taskDao()
                            .getTasksAwaitingEfficacy(now);
                    if (awaiting != null && !awaiting.isEmpty()) {
                        List<InterventionTask> updated =
                                interventionEngine.finalizeObservationWindows(awaiting, currentRisk);
                        for (InterventionTask t : updated) {
                            appDatabase.taskDao().update(t);
                        }
                    }
                } catch (Exception ignored) {
                    // Non-critical: don't block submission
                }

                // ── Build efficacy weight map ──
                Map<String, Float> categoryEfficacy = buildCategoryEfficacyMap();

                List<InterventionTask> generatedTasks = interventionEngine.generateTasks(
                        state,
                        behaviorReport,
                        anomalyProfile,
                        baseline,
                        recentTasks
                );

                // Stamp pre-intervention risk for future efficacy computation
                if (generatedTasks != null && !generatedTasks.isEmpty()) {
                    float approxRisk = anomalyProfile != null
                            && anomalyProfile.screenTime != null
                            ? (float) Math.min(1.0, Math.max(0.0,
                                    anomalyProfile.screenTime.zScore * 0.15))
                            : state.ordinal() * 0.15f;
                    interventionEngine.stampPreInterventionRisk(generatedTasks, approxRisk);
                }

                repository.replaceActiveTasksSync(generatedTasks);

                CrisisDetector crisisDetector = new CrisisDetector();
                boolean crisisDetected = crisisDetector.isCrisisDetected(recentResponses);

                // ── Step 4: Signal dashboard refresh (Task 2.E.6) ──
                dashboardRefreshNeeded.postValue(true);

                submissionResult.postValue(
                        new SubmissionResult(
                                true,
                                state,
                                humanizeState(state),
                                crisisDetected,
                                generatedTasks == null ? 0 : generatedTasks.size(),
                                crisisDetected
                                        ? "MindTrace detected a higher-risk pattern and opened the support screen."
                                        : "Response saved. State updated to " + humanizeState(state) + "."
                        )
                );
            } catch (Exception e) {
                submissionResult.postValue(
                        new SubmissionResult(
                                false,
                                MentalStateClassifier.State.STABLE,
                                "Unavailable",
                                false,
                                0,
                                "MindTrace could not analyze this response. Please try again."
                        )
                );
            }
        });
    }

    /** Clear the refresh flag after dashboard has consumed it. */
    public void onDashboardRefreshed() {
        dashboardRefreshNeeded.setValue(false);
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

    /**
     * Build a category → avgEfficacy map from TaskDao for the InterventionEngine.
     * Returns an empty map if no measured efficacy data exists yet.
     */
    private Map<String, Float> buildCategoryEfficacyMap() {
        Map<String, Float> map = new HashMap<>();
        try {
            List<TaskDao.CategoryEfficacy> scores = appDatabase.taskDao().getCategoryEfficacyScores();
            if (scores != null) {
                for (TaskDao.CategoryEfficacy ce : scores) {
                    if (ce.category != null) {
                        map.put(ce.category, ce.avgEfficacy);
                    }
                }
            }
        } catch (Exception ignored) {
            // Graceful degradation: empty map means no efficacy weighting
        }
        return map;
    }

    private String humanizeState(MentalStateClassifier.State state) {
        switch (state) {
            case DIGITAL_ADDICTION:
                return "Digital Addiction Risk";
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

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }

    public static class SubmissionResult {
        public final boolean successful;
        public final MentalStateClassifier.State state;
        public final String stateLabel;
        public final boolean crisisDetected;
        public final int generatedTasks;
        public final String message;

        public SubmissionResult(
                boolean successful,
                MentalStateClassifier.State state,
                String stateLabel,
                boolean crisisDetected,
                int generatedTasks,
                String message
        ) {
            this.successful = successful;
            this.state = state;
            this.stateLabel = stateLabel;
            this.crisisDetected = crisisDetected;
            this.generatedTasks = generatedTasks;
            this.message = message;
        }
    }
}

