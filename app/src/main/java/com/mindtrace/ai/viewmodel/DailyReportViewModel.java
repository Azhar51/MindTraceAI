package com.mindtrace.ai.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.dao.UsageDao;
import com.mindtrace.ai.database.dao.BehaviorSnapshotDao;
import com.mindtrace.ai.database.entity.BehaviorSnapshotEntity;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Daily Report dashboard.
 *
 * <p>Aggregates reactive and one-shot data from five DAOs into
 * presentation-ready {@link LiveData} streams consumed by
 * {@link com.mindtrace.ai.ui.DailyReportActivity}.</p>
 *
 * <h3>Data Sources</h3>
 * <ul>
 *   <li>{@link UsageDao} — screen time, unlocks, app switches, night usage</li>
 *   <li>{@link TaskDao} — task completion, effectiveness, efficacy</li>
 *   <li>{@link QuestionnaireDao} — mood, stress, sleep analytics</li>
 *   <li>{@link RiskClassificationDao} — daily risk scores, category breakdown</li>
 *   <li>{@link BehaviorSnapshotDao} — behavior risk, fragmentation, patterns</li>
 * </ul>
 */
public class DailyReportViewModel extends AndroidViewModel {

    // ── DAOs ─────────────────────────────────────────────────────────────
    private final UsageDao usageDao;
    private final TaskDao taskDao;
    private final QuestionnaireDao questionnaireDao;
    private final RiskClassificationDao riskClassificationDao;
    private final BehaviorSnapshotDao behaviorSnapshotDao;

    // ── Background executor ──────────────────────────────────────────────
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Usage metrics ────────────────────────────────────────────────────
    private final MutableLiveData<DailyUsage> todayUsage = new MutableLiveData<>();
    private final MutableLiveData<Long> screenTimeMillis = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer> unlockCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> appSwitchCount = new MutableLiveData<>(0);
    private final MutableLiveData<Long> nightUsageMillis = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> socialMediaMillis = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> productiveMillis = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> entertainmentMillis = new MutableLiveData<>(0L);
    private final MutableLiveData<Float> passiveRatio = new MutableLiveData<>(0f);

    // ── Usage pattern classification ─────────────────────────────────────
    private final MutableLiveData<String> usagePatternLabel = new MutableLiveData<>("—");
    private final MutableLiveData<String> usagePatternDescription = new MutableLiveData<>("");

    // ── Task efficacy ────────────────────────────────────────────────────
    private final MutableLiveData<Integer> tasksCompletedToday = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> tasksCreatedToday = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> taskCompletionPercent = new MutableLiveData<>(0);
    private final MutableLiveData<Float> overallEffectiveness = new MutableLiveData<>(0f);
    private final MutableLiveData<String> mostEffectiveCategory = new MutableLiveData<>("—");
    private final MutableLiveData<Float> overallEfficacy = new MutableLiveData<>(0f);
    private final MutableLiveData<String> mostEfficaciousCategory = new MutableLiveData<>("—");

    // ── Wellness check-in ────────────────────────────────────────────────
    private final MutableLiveData<Float> avgStress7 = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> avgSleepHours7 = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> avgHope7 = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> avgPurpose7 = new MutableLiveData<>(0f);
    private final MutableLiveData<String> latestMood = new MutableLiveData<>("—");
    private final MutableLiveData<Integer> checkInCount = new MutableLiveData<>(0);

    // ── Risk classification ──────────────────────────────────────────────
    private final MutableLiveData<RiskClassification> todayRisk = new MutableLiveData<>();
    private final MutableLiveData<Float> overallRiskScore = new MutableLiveData<>(0f);
    private final MutableLiveData<String> primaryRiskCategory = new MutableLiveData<>("—");
    private final MutableLiveData<Boolean> crisisFlag = new MutableLiveData<>(false);

    // ── Behavior snapshot ────────────────────────────────────────────────
    private final MutableLiveData<BehaviorSnapshotEntity> todayBehavior = new MutableLiveData<>();
    private final MutableLiveData<Float> behaviorRiskScore = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> fragmentationIndex = new MutableLiveData<>(0f);
    private final MutableLiveData<Boolean> hasLoopPattern = new MutableLiveData<>(false);

    // ── Date header ──────────────────────────────────────────────────────
    private final MutableLiveData<String> dateText = new MutableLiveData<>();
    private final MutableLiveData<String> greetingText = new MutableLiveData<>();

    // ── Loading state ────────────────────────────────────────────────────
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(true);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // ═══════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════

    public DailyReportViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance((Context) application);
        this.usageDao = db.usageDao();
        this.taskDao = db.taskDao();
        this.questionnaireDao = db.questionnaireDao();
        this.riskClassificationDao = db.riskClassificationDao();
        this.behaviorSnapshotDao = db.behaviorSnapshotDao();

        // Set header values immediately
        this.dateText.setValue(formatCurrentDate());
        this.greetingText.setValue(buildGreeting());

        // Kick off the background data load
        loadReport();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC ACCESSORS — LiveData streams for the Activity
    // ═══════════════════════════════════════════════════════════════════════

    // ── Usage ──
    public LiveData<DailyUsage> getTodayUsage() { return todayUsage; }
    public LiveData<Long> getScreenTimeMillis() { return screenTimeMillis; }
    public LiveData<Integer> getUnlockCount() { return unlockCount; }
    public LiveData<Integer> getAppSwitchCount() { return appSwitchCount; }
    public LiveData<Long> getNightUsageMillis() { return nightUsageMillis; }
    public LiveData<Long> getSocialMediaMillis() { return socialMediaMillis; }
    public LiveData<Long> getProductiveMillis() { return productiveMillis; }
    public LiveData<Long> getEntertainmentMillis() { return entertainmentMillis; }
    public LiveData<Float> getPassiveRatio() { return passiveRatio; }
    public LiveData<String> getUsagePatternLabel() { return usagePatternLabel; }
    public LiveData<String> getUsagePatternDescription() { return usagePatternDescription; }

    // ── Tasks ──
    public LiveData<Integer> getTasksCompletedToday() { return tasksCompletedToday; }
    public LiveData<Integer> getTasksCreatedToday() { return tasksCreatedToday; }
    public LiveData<Integer> getTaskCompletionPercent() { return taskCompletionPercent; }
    public LiveData<Float> getOverallEffectiveness() { return overallEffectiveness; }
    public LiveData<String> getMostEffectiveCategory() { return mostEffectiveCategory; }
    public LiveData<Float> getOverallEfficacy() { return overallEfficacy; }
    public LiveData<String> getMostEfficaciousCategory() { return mostEfficaciousCategory; }

    // ── Wellness ──
    public LiveData<Float> getAvgStress7() { return avgStress7; }
    public LiveData<Float> getAvgSleepHours7() { return avgSleepHours7; }
    public LiveData<Float> getAvgHope7() { return avgHope7; }
    public LiveData<Float> getAvgPurpose7() { return avgPurpose7; }
    public LiveData<String> getLatestMood() { return latestMood; }
    public LiveData<Integer> getCheckInCount() { return checkInCount; }

    // ── Risk ──
    public LiveData<RiskClassification> getTodayRisk() { return todayRisk; }
    public LiveData<Float> getOverallRiskScore() { return overallRiskScore; }
    public LiveData<String> getPrimaryRiskCategory() { return primaryRiskCategory; }
    public LiveData<Boolean> getCrisisFlag() { return crisisFlag; }

    // ── Behavior ──
    public LiveData<BehaviorSnapshotEntity> getTodayBehavior() { return todayBehavior; }
    public LiveData<Float> getBehaviorRiskScore() { return behaviorRiskScore; }
    public LiveData<Float> getFragmentationIndex() { return fragmentationIndex; }
    public LiveData<Boolean> getHasLoopPattern() { return hasLoopPattern; }

    // ── UI State ──
    public LiveData<String> getDateText() { return dateText; }
    public LiveData<String> getGreetingText() { return greetingText; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA LOADING — background thread
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Trigger a full report refresh. Safe to call multiple times
     * (re-entrant — runs on the single-thread executor).
     */
    public void loadReport() {
        isLoading.postValue(true);
        executor.execute(() -> {
            try {
                long today = getStartOfTodayMillis();
                loadUsageMetrics(today);
                loadTaskMetrics(today);
                loadWellnessMetrics();
                loadRiskMetrics(today);
                loadBehaviorMetrics(today);
                errorMessage.postValue(null);
            } catch (Exception e) {
                errorMessage.postValue("Unable to load today's report. Try again shortly.");
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    // ─── Usage ───────────────────────────────────────────────────────────

    private void loadUsageMetrics(long today) {
        DailyUsage usage = usageDao.getUsageForDay(today);
        todayUsage.postValue(usage);
        if (usage != null) {
            screenTimeMillis.postValue(usage.screenTimeMillis);
            unlockCount.postValue(usage.unlockCount);
            appSwitchCount.postValue(usage.totalAppSwitchCount);
            nightUsageMillis.postValue(usage.nightUsageMillis);
            socialMediaMillis.postValue(usage.socialMediaTimeMillis);
            productiveMillis.postValue(usage.productiveTimeMillis);
            entertainmentMillis.postValue(usage.entertainmentTimeMillis);
            passiveRatio.postValue(usage.passiveConsumptionRatio);
            classifyUsagePattern(usage);
        } else {
            screenTimeMillis.postValue(0L);
            unlockCount.postValue(0);
            appSwitchCount.postValue(0);
            nightUsageMillis.postValue(0L);
            socialMediaMillis.postValue(0L);
            productiveMillis.postValue(0L);
            entertainmentMillis.postValue(0L);
            passiveRatio.postValue(0f);
            usagePatternLabel.postValue("No Data");
            usagePatternDescription.postValue("Start using your phone to see patterns");
        }
    }

    /**
     * Classify the user's pattern into one of four quadrants based on
     * screen time vs. unlock count.
     *
     * <pre>
     *   High Usage + Low Unlocks  → Over Engager
     *   High Usage + High Unlocks → Addicted & Highly Engaged
     *   Low Usage  + Low Unlocks  → Minimalist User
     *   Low Usage  + High Unlocks → Compulsive Checker
     * </pre>
     */
    private void classifyUsagePattern(DailyUsage usage) {
        // Thresholds derived from 7-day averages, fall back to sensible defaults
        double avgScreen = usageDao.getAvgScreenTime(7);
        double avgUnlocks = usageDao.getAvgUnlockCount(7);
        boolean highUsage = usage.screenTimeMillis > (avgScreen > 0 ? avgScreen : 14400000L); // 4h default
        boolean highUnlocks = usage.unlockCount > (avgUnlocks > 0 ? avgUnlocks : 50);

        if (highUsage && highUnlocks) {
            usagePatternLabel.postValue("Addicted & Highly Engaged");
            usagePatternDescription.postValue("↑ High Usage · ↑ High Unlocks");
        } else if (highUsage) {
            usagePatternLabel.postValue("Over Engager");
            usagePatternDescription.postValue("↑ High Usage · ↓ Low Unlocks");
        } else if (highUnlocks) {
            usagePatternLabel.postValue("Compulsive Checker");
            usagePatternDescription.postValue("↓ Low Usage · ↑ High Unlocks");
        } else {
            usagePatternLabel.postValue("Minimalist User");
            usagePatternDescription.postValue("↓ Low Usage · ↓ Low Unlocks");
        }
    }

    // ─── Tasks ───────────────────────────────────────────────────────────

    private void loadTaskMetrics(long today) {
        int completed = taskDao.getCompletedCountSince(today);
        int total = taskDao.getTotalCountSince(today);
        tasksCompletedToday.postValue(completed);
        tasksCreatedToday.postValue(total);
        taskCompletionPercent.postValue(total > 0 ? Math.round((float) completed * 100f / total) : 0);

        float effectiveness = taskDao.getOverallAverageEffectiveness();
        overallEffectiveness.postValue(Float.isNaN(effectiveness) ? 0f : effectiveness);

        String bestCategory = taskDao.getMostEffectiveCategory();
        mostEffectiveCategory.postValue(bestCategory != null ? bestCategory : "—");

        float efficacy = taskDao.getOverallAverageEfficacy();
        overallEfficacy.postValue(Float.isNaN(efficacy) ? 0f : efficacy);

        String bestEfficacyCat = taskDao.getMostEfficaciousCategory();
        mostEfficaciousCategory.postValue(bestEfficacyCat != null ? bestEfficacyCat : "—");
    }

    // ─── Wellness ────────────────────────────────────────────────────────

    private void loadWellnessMetrics() {
        float stress = questionnaireDao.getAvgStressLevel(7);
        avgStress7.postValue(Float.isNaN(stress) ? 0f : stress);

        float sleep = questionnaireDao.getAvgSleepHours(7);
        avgSleepHours7.postValue(Float.isNaN(sleep) ? 0f : sleep);

        float hope = questionnaireDao.getAvgHopeLevel(7);
        avgHope7.postValue(Float.isNaN(hope) ? 0f : hope);

        float purpose = questionnaireDao.getAvgPurposeScore(7);
        avgPurpose7.postValue(Float.isNaN(purpose) ? 0f : purpose);

        QuestionnaireResponse latest = questionnaireDao.getLatestResponseSync();
        latestMood.postValue(latest != null && latest.mood != null ? latest.mood : "—");

        checkInCount.postValue(questionnaireDao.getTotalCheckInCount());
    }

    // ─── Risk ────────────────────────────────────────────────────────────

    private void loadRiskMetrics(long today) {
        RiskClassification risk = riskClassificationDao.getForDay(today);
        todayRisk.postValue(risk);
        if (risk != null) {
            overallRiskScore.postValue((float) risk.overallRiskScore);
            primaryRiskCategory.postValue(risk.primaryCategory != null ? risk.primaryCategory : "—");
            crisisFlag.postValue(risk.crisisFlag);
        } else {
            overallRiskScore.postValue(0f);
            primaryRiskCategory.postValue("—");
            crisisFlag.postValue(false);
        }
    }

    // ─── Behavior ────────────────────────────────────────────────────────

    private void loadBehaviorMetrics(long today) {
        BehaviorSnapshotEntity snap = behaviorSnapshotDao.getForDay(today);
        todayBehavior.postValue(snap);
        if (snap != null) {
            behaviorRiskScore.postValue((float) snap.overallBehaviorRiskScore);
            fragmentationIndex.postValue((float) snap.fragmentationIndex);
            hasLoopPattern.postValue(snap.hasLoopPattern);
        } else {
            behaviorRiskScore.postValue(0f);
            fragmentationIndex.postValue(0f);
            hasLoopPattern.postValue(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════

    private long getStartOfTodayMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String formatCurrentDate() {
        return new SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(new Date());
    }

    private String buildGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good Morning";
        if (hour < 17) return "Good Afternoon";
        return "Good Evening";
    }

    /**
     * Format milliseconds as "Xh Ym".
     */
    public static String formatDuration(long millis) {
        long totalMinutes = millis / 60_000;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    /**
     * Format a float risk score (0.0–1.0) as a percentage string.
     */
    public static String formatRiskPercent(float score) {
        return Math.round(score * 100) + "%";
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
