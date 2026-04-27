package com.mindtrace.ai.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.JournalDao;
import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.entity.JournalEntry;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.repository.AssessmentRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Mood & Journal screen (Part 2C — Advanced).
 *
 * <p>Manages all journal-related data flows including:</p>
 * <ul>
 *   <li>Today's journal entry preview</li>
 *   <li>Recent entry history (last 5)</li>
 *   <li>Current and longest streaks (non-punitive)</li>
 *   <li>Cross-entity engagement stats (check-ins, entries, tasks)</li>
 *   <li>Sentiment trend data for sparkline visualization</li>
 *   <li>Mood-to-behavior correlation insight generation</li>
 * </ul>
 *
 * <h3>Architecture:</h3>
 * <pre>
 *   MoodFragment
 *     └── JournalViewModel (this)
 *           ├── JournalDao       — entries, sentiment, streaks
 *           ├── QuestionnaireDao — check-in counts, mood correlations
 *           ├── TaskDao          — task completion counts
 *           └── AssessmentRepository — streak computation engine
 * </pre>
 */
public class JournalViewModel extends AndroidViewModel {

    // ═══════════════════════════════════════════════════════════════════
    // LIVE DATA STREAMS
    // ═══════════════════════════════════════════════════════════════════

    private final MutableLiveData<List<JournalEntry>> recentEntries = new MutableLiveData<>();
    private final MutableLiveData<JournalEntry> todayEntry = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentStreak = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> longestStreak = new MutableLiveData<>(0);
    private final MutableLiveData<int[]> engagementStats = new MutableLiveData<>(new int[]{0, 0, 0});
    private final MutableLiveData<List<Float>> sentimentTrend = new MutableLiveData<>();
    private final MutableLiveData<List<String>> correlationInsights = new MutableLiveData<>();
    private final MutableLiveData<String> streakMessage = new MutableLiveData<>("Start your journey 💛");
    private final MutableLiveData<Float> avgSentiment = new MutableLiveData<>(0f);

    // ═══════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════

    private final AssessmentRepository repository;
    private final JournalDao journalDao;
    private final QuestionnaireDao questionnaireDao;
    private final TaskDao taskDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public JournalViewModel(@NonNull Application application) {
        super(application);
        repository = new AssessmentRepository(application);
        AppDatabase db = AppDatabase.getInstance(application);
        journalDao = db.journalDao();
        questionnaireDao = db.questionnaireDao();
        taskDao = db.taskDao();
        loadAll();
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA LOADING PIPELINE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Full data refresh — called on init and onResume.
     * Loads all journal metrics in a single background pass.
     */
    public void loadAll() {
        executor.execute(() -> {
            try {
                loadTodayEntry();
                loadRecentEntries();
                loadStreaks();
                loadEngagementStats();
                loadSentimentTrend();
                generateCorrelationInsight();
            } catch (Exception e) {
                // Fail gracefully — individual sections handle their own nulls
            }
        });
    }

    private void loadTodayEntry() {
        long todayStart = getStartOfTodayMillis();
        JournalEntry today = journalDao.getLatestEntryForDay(todayStart);
        todayEntry.postValue(today);
    }

    private void loadRecentEntries() {
        List<JournalEntry> recent = journalDao.getRecentEntries(5);
        recentEntries.postValue(recent);
    }

    private void loadStreaks() {
        int streak = repository.getJournalStreak();
        currentStreak.postValue(streak);

        int longest = repository.getLongestJournalStreak();
        longestStreak.postValue(longest);

        // Non-punitive streak messaging per blueprint
        if (streak >= 7) {
            streakMessage.postValue(streak + " day streak — incredible consistency! 🔥");
        } else if (streak >= 3) {
            streakMessage.postValue(streak + " day streak — you're building momentum 💪");
        } else if (streak == 1) {
            streakMessage.postValue("Great start today! One day at a time 🌱");
        } else if (longest > 0 && streak == 0) {
            // Streak broken — non-punitive per blueprint
            streakMessage.postValue("Welcome back 💛 — your wellbeing matters more than numbers");
        } else {
            streakMessage.postValue("Start your reflection journey today 💛");
        }
    }

    private void loadEngagementStats() {
        try {
            int checkIns = questionnaireDao.getTotalCheckInCount();
            int journals = journalDao.getTotalEntryCount();
            int tasks = taskDao.getCompletedCountSince(0);
            engagementStats.postValue(new int[]{checkIns, journals, tasks});
        } catch (Exception e) {
            engagementStats.postValue(new int[]{0, 0, 0});
        }
    }

    /**
     * Load sentiment scores for the last 14 entries, oldest→newest.
     * Used by the sentiment sparkline in the journal section.
     */
    private void loadSentimentTrend() {
        try {
            List<Float> scores = journalDao.getSentimentTrend(14);
            sentimentTrend.postValue(scores);

            float avg = journalDao.getAvgSentimentScore(14);
            avgSentiment.postValue(avg);
        } catch (Exception e) {
            sentimentTrend.postValue(null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORRELATION INSIGHT ENGINE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Cross-references mood check-in data with journal sentiment to
     * detect behavioral patterns. Requires 7+ days of data.
     *
     * <p>Example insights:</p>
     * <ul>
     *   <li>"You tend to feel anxious on days when stress exceeds 3/5."</li>
     *   <li>"Your mood improves on days you journal."</li>
     *   <li>"Sleep quality drops after high-stress days."</li>
     * </ul>
     */
    private void generateCorrelationInsight() {
        try {
            List<QuestionnaireResponse> responses = questionnaireDao.getRecentResponses(14);
            if (responses == null || responses.size() < 7) {
                correlationInsights.postValue(null);
                return;
            }

            // Analysis: count mood outcomes by stress level
            int highStressAnxious = 0, highStressTotal = 0;
            int goodSleepHappy = 0, goodSleepTotal = 0;
            int journalDays = journalDao.getTotalJournalDays();
            int totalDays = questionnaireDao.getDistinctCheckInDays();

            for (QuestionnaireResponse r : responses) {
                if (r.stressLevel >= 4) {
                    highStressTotal++;
                    if ("Anxious".equals(r.mood) || "Sad".equals(r.mood)) highStressAnxious++;
                }
                if (r.sleepHours >= 7) {
                    goodSleepTotal++;
                    if ("Happy".equals(r.mood) || "Calm".equals(r.mood)) goodSleepHappy++;
                }
            }

            List<String> insights = new ArrayList<>();

            // Stress → anxiety correlation
            if (highStressTotal >= 3 && highStressAnxious >= 2) {
                int pct = (int) ((float) highStressAnxious / highStressTotal * 100);
                insights.add("You feel anxious or sad " + pct + "% of the time when stress exceeds 3/5.");
            }

            // Sleep → mood correlation
            if (goodSleepTotal >= 3 && goodSleepHappy >= 2) {
                int pct = (int) ((float) goodSleepHappy / goodSleepTotal * 100);
                insights.add("You feel happy or calm " + pct + "% of the time when you sleep 7+ hours.");
            }

            // Journal engagement benefit
            if (journalDays >= 5 && totalDays >= 7) {
                float journalRatio = (float) journalDays / totalDays;
                if (journalRatio > 0.5f) {
                    insights.add("You journal on " + Math.round(journalRatio * 100)
                            + "% of days — consistent self-reflection strengthens emotional awareness.");
                }
            }

            // Avg stress insight
            float avgStress = questionnaireDao.getAvgStressLevel(7);
            if (avgStress >= 3.5f) {
                insights.add(String.format(Locale.US,
                        "Your 7-day stress average is %.1f/5 — consider adding a relaxation exercise.", avgStress));
            }

            if (!insights.isEmpty()) {
                correlationInsights.postValue(insights);
            } else {
                correlationInsights.postValue(null);
            }
        } catch (Exception e) {
            correlationInsights.postValue(null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    public LiveData<List<JournalEntry>> getRecentEntries() { return recentEntries; }
    public LiveData<JournalEntry> getTodayEntry() { return todayEntry; }
    public LiveData<Integer> getCurrentStreak() { return currentStreak; }
    public LiveData<Integer> getLongestStreak() { return longestStreak; }
    public LiveData<int[]> getEngagementStats() { return engagementStats; }
    public LiveData<List<Float>> getSentimentTrend() { return sentimentTrend; }
    public LiveData<List<String>> getCorrelationInsights() { return correlationInsights; }
    public LiveData<String> getStreakMessage() { return streakMessage; }
    public LiveData<Float> getAvgSentiment() { return avgSentiment; }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private long getStartOfTodayMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
