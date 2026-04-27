package com.mindtrace.ai.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mindtrace.ai.ai.CrisisDetector;
import com.mindtrace.ai.ai.ExerciseEngine;
import com.mindtrace.ai.ai.PostCrisisDebrief;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.CrisisEvent;
import com.mindtrace.ai.database.entity.ExerciseCompletion;
import com.mindtrace.ai.database.entity.SafetyPlan;
import com.mindtrace.ai.database.dao.SafetyPlanDao;
import com.mindtrace.ai.database.dao.ExerciseCompletionDao;
import com.mindtrace.ai.repository.CrisisRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * ViewModel for the crisis support & safety system.
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Run multi-signal crisis detection</li>
 *   <li>Save/resolve crisis events</li>
 *   <li>Manage safety plan (6 sections)</li>
 *   <li>Provide breathing/grounding exercises</li>
 *   <li>Track exercise effectiveness</li>
 *   <li>Post-crisis debrief scheduling & insight generation</li>
 *   <li>Weekly crisis analytics</li>
 * </ul>
 */
public class CrisisViewModel extends AndroidViewModel {

    private static final String TAG = "CrisisViewModel";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final CrisisRepository crisisRepository;
    private final CrisisDetector crisisDetector;
    private final SafetyPlanDao safetyPlanDao;
    private final ExerciseCompletionDao exerciseCompletionDao;

    // ── LiveData ──
    private final LiveData<List<CrisisEvent>> crisisHistory;
    private final MutableLiveData<CrisisDetector.CrisisAssessment> currentAssessment = new MutableLiveData<>();
    private final MutableLiveData<SafetyPlan> safetyPlan = new MutableLiveData<>();
    private final MutableLiveData<PostCrisisDebrief.DebriefInsight> debriefInsight = new MutableLiveData<>();

    public CrisisViewModel(@NonNull Application application) {
        super(application);
        crisisRepository = new CrisisRepository(application);
        crisisDetector = new CrisisDetector();
        AppDatabase db = AppDatabase.getInstance(application);
        safetyPlanDao = db.safetyPlanDao();
        exerciseCompletionDao = db.exerciseCompletionDao();
        crisisHistory = crisisRepository.getAllEvents();

        // Load safety plan on init
        executor.execute(this::loadSafetyPlan);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /** Run multi-signal crisis assessment. */
    public void runCrisisAssessment(
            @Nullable com.mindtrace.ai.database.entity.QuestionnaireResponse latestResponse,
            @Nullable com.mindtrace.ai.database.entity.RiskClassification classification,
            @Nullable com.mindtrace.ai.ai.MultiModalClassifier.CrisisLevel classifierLevel,
            @Nullable CrisisDetector.DigitalBehaviorSignals digitalSignals,
            @Nullable CrisisDetector.LongitudinalSignals longitudinalSignals,
            @Nullable CrisisDetector.JournalSignals journalSignals,
            @Nullable String[] comorbidities
    ) {
        executor.execute(() -> {
            java.util.List<com.mindtrace.ai.database.entity.QuestionnaireResponse> responses =
                    latestResponse != null ? java.util.Collections.singletonList(latestResponse) : null;

            CrisisDetector.CrisisAssessment assessment = crisisDetector.assess(
                    responses, classification, classifierLevel,
                    digitalSignals, longitudinalSignals, journalSignals, comorbidities
            );

            currentAssessment.postValue(assessment);

            // Auto-save crisis event if elevated+
            if (assessment.level.requiresMonitoring() && !crisisRepository.isInCooldown()) {
                crisisRepository.saveCrisisEvent(assessment);
                Log.d(TAG, "Crisis event saved: " + assessment.level.label);

                // Fire notification if urgent+
                com.mindtrace.ai.services.CrisisNotificationManager.notifyIfNeeded(
                        getApplication(), assessment);
            }
        });
    }

    @NonNull
    public MutableLiveData<CrisisDetector.CrisisAssessment> getCurrentAssessment() {
        return currentAssessment;
    }

    @NonNull
    public LiveData<List<CrisisEvent>> getCrisisHistory() {
        return crisisHistory;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS RESOLUTION
    // ═══════════════════════════════════════════════════════════════════

    /** Mark the active crisis as resolved with coping method and post-distress level. */
    public void resolveCrisis(@NonNull String method, @Nullable String mood, int postDistress) {
        executor.execute(() -> {
            crisisRepository.resolveActive(method, mood, postDistress);
            currentAssessment.postValue(null); // Clear active assessment
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SAFETY PLAN
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    public MutableLiveData<SafetyPlan> getSafetyPlan() {
        return safetyPlan;
    }

    /** Save or update the safety plan. */
    public void saveSafetyPlan(@NonNull SafetyPlan plan) {
        executor.execute(() -> {
            plan.updatedAt = System.currentTimeMillis();
            if (plan.createdAt == 0) plan.createdAt = plan.updatedAt;
            plan.isComplete = plan.getCompletionPercent() == 100;
            try {
                plan.id = 1; // Singleton
                safetyPlanDao.insertOrUpdate(plan);
                safetyPlan.postValue(plan);
                Log.d(TAG, "Safety plan saved (" + plan.getCompletionPercent() + "% complete)");
            } catch (Exception e) {
                Log.e(TAG, "Failed to save safety plan", e);
            }
        });
    }

    private void loadSafetyPlan() {
        try {
            SafetyPlan plan = safetyPlanDao.getSync();
            if (plan != null) {
                safetyPlan.postValue(plan);
            }
        } catch (Exception e) {
            Log.d(TAG, "No safety plan found (first run)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BREATHING & GROUNDING EXERCISES
    // ═══════════════════════════════════════════════════════════════════

    /** Get all available breathing exercises. */
    @NonNull
    public List<ExerciseEngine.BreathingExercise> getBreathingExercises() {
        return ExerciseEngine.getAllBreathingExercises();
    }

    /** Get all available grounding exercises. */
    @NonNull
    public List<ExerciseEngine.GroundingExercise> getGroundingExercises() {
        return ExerciseEngine.getAllGroundingExercises();
    }

    /** Log completion of a breathing/grounding exercise. */
    public void logExerciseCompletion(@NonNull String type, @NonNull String name,
                                       long durationMs, int preDistress, int postDistress,
                                       boolean completedFully) {
        executor.execute(() -> {
            ExerciseCompletion completion = new ExerciseCompletion();
            completion.exerciseType = type;
            completion.exerciseName = name;
            completion.completedAt = System.currentTimeMillis();
            completion.durationMs = durationMs;
            completion.preDistressLevel = preDistress;
            completion.postDistressLevel = postDistress;
            completion.completedFully = completedFully;

            try {
                exerciseCompletionDao.insert(completion);
                Log.d(TAG, "Exercise logged: " + name + " (distress: " + preDistress + " → " + postDistress + ")");
            } catch (Exception e) {
                Log.e(TAG, "Failed to log exercise", e);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // POST-CRISIS DEBRIEF
    // ═══════════════════════════════════════════════════════════════════

    /** Check if a debrief is pending. */
    public void checkForPendingDebrief(@NonNull Consumer<CrisisEvent> callback) {
        executor.execute(() -> {
            CrisisEvent event = crisisRepository.getEventNeedingDebrief();
            if (event != null) {
                callback.accept(event);
            }
        });
    }

    /** Submit debrief response and generate insights. */
    public void submitDebrief(@NonNull CrisisEvent event,
                               @NonNull PostCrisisDebrief.DebriefResponse response) {
        executor.execute(() -> {
            event.debriefCompleted = true;
            crisisRepository.update(event);

            PostCrisisDebrief.DebriefInsight insight =
                    PostCrisisDebrief.generateInsight(event, response);
            debriefInsight.postValue(insight);

            Log.d(TAG, "Debrief completed: " + insight.recoveryStatus);
        });
    }

    @NonNull
    public MutableLiveData<PostCrisisDebrief.DebriefInsight> getDebriefInsight() {
        return debriefInsight;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANALYTICS
    // ═══════════════════════════════════════════════════════════════════

    /** Load weekly crisis summary. */
    public void loadWeeklySummary(@NonNull Consumer<String> callback) {
        executor.execute(() -> {
            String summary = crisisRepository.getWeeklyCrisisSummary();
            callback.accept(summary);
        });
    }

    /** Load most effective coping strategy. */
    public void loadBestCopingStrategy(@NonNull Consumer<String> callback) {
        executor.execute(() -> {
            String strategy = crisisRepository.getMostEffectiveCopingStrategy();
            callback.accept(strategy != null ? strategy.replace("_", " ") : "Not enough data yet");
        });
    }

    /** Load average distress reduction from exercises. */
    public void loadAvgDistressReduction(@NonNull Consumer<Float> callback) {
        executor.execute(() -> {
            float reduction = crisisRepository.getAverageDistressReduction();
            callback.accept(reduction);
        });
    }

    /** Load trusted contacts for crisis screen. */
    public void loadTrustedContacts(@NonNull Consumer<List<com.mindtrace.ai.database.entity.TrustedContact>> callback) {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplication());
                List<com.mindtrace.ai.database.entity.TrustedContact> contacts =
                        db.trustedContactDao().getAllSync();
                callback.accept(contacts != null ? contacts : java.util.Collections.emptyList());
            } catch (Exception e) {
                Log.e(TAG, "Failed to load trusted contacts", e);
                callback.accept(java.util.Collections.emptyList());
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SUICIDE RISK LOGGING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Log a crisis lockdown event to the database.
     */
    public void logCrisisLockdown(int csrrsTier) {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplication());
                com.mindtrace.ai.database.entity.SuicideRiskEvent event =
                        new com.mindtrace.ai.database.entity.SuicideRiskEvent();
                event.timestamp = System.currentTimeMillis();
                event.csrrsTier = csrrsTier;
                event.severityLabel = csrrsTier >= 5 ? "CRITICAL — Preparation/Farewell" :
                        "SEVERE — Intent Expressed";
                event.lockdownTriggered = true;
                event.source = "crisis_lockdown";
                db.suicideRiskEventDao().insert(event);
                Log.d(TAG, "Logged crisis lockdown event: tier=" + csrrsTier);
            } catch (Exception e) {
                Log.e(TAG, "Failed to log lockdown event", e);
            }
        });
    }

    /**
     * Log a suicide risk assessment event from any source.
     */
    public void logSuicideRiskAssessment(
            com.mindtrace.ai.ai.SuicideRiskClassifier.SuicideRiskAssessment assessment,
            String source) {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplication());
                com.mindtrace.ai.database.entity.SuicideRiskEvent event =
                        new com.mindtrace.ai.database.entity.SuicideRiskEvent();
                event.timestamp = System.currentTimeMillis();
                event.csrrsTier = assessment.csrrsTier;
                event.severityLabel = assessment.getSeverityLabel();
                event.textTier = assessment.textTier;
                event.behaviorTier = assessment.behaviorTier;
                event.signalCount = assessment.activeSignals.size();
                event.lockdownTriggered = assessment.shouldLockdown;
                event.notificationSent = assessment.shouldNotify;
                event.autoContactSent = assessment.shouldAutoContact;
                event.source = source;
                db.suicideRiskEventDao().insert(event);
                Log.d(TAG, "Logged suicide risk event: " + assessment);
            } catch (Exception e) {
                Log.e(TAG, "Failed to log suicide risk event", e);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
