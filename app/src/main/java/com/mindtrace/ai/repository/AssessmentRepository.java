package com.mindtrace.ai.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.JournalDao;
import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.WeeklyAssessmentDao;
import com.mindtrace.ai.database.entity.JournalEntry;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.WeeklyAssessment;
import com.mindtrace.ai.util.MoodMapper;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central repository for all psychological assessment data — check-ins,
 * journal entries, and weekly assessments.
 *
 * <p>This is the single entry point for the UI/ViewModel layer to interact
 * with the psychological data pipeline. It coordinates across three DAOs
 * and adds business logic (streak calculation, consecutive-day analysis,
 * assessment scheduling) that doesn't belong in DAOs or entities.</p>
 *
 * <h3>Responsibilities:</h3>
 * <ol>
 *   <li><b>Check-In Management</b> — save, retrieve, auto-compute distress,
 *       auto-create gratitude journal entries</li>
 *   <li><b>Journal Management</b> — save, retrieve, streak tracking</li>
 *   <li><b>Weekly Assessment</b> — save, retrieve, scheduling logic</li>
 *   <li><b>Pattern Detection</b> — consecutive sad/poor-sleep days,
 *       mood streaks, engagement consistency</li>
 *   <li><b>Cross-Entity Intelligence</b> — correlations between check-in
 *       mood and journal sentiment, engagement metrics</li>
 * </ol>
 *
 * <h3>Architecture:</h3>
 * <pre>
 *   DailyCheckInActivity / JournalActivity / WeeklyReviewActivity
 *       → AssessmentViewModel
 *           → AssessmentRepository (this class)
 *               ├── QuestionnaireDao
 *               ├── JournalDao
 *               └── WeeklyAssessmentDao
 * </pre>
 *
 * @see QuestionnaireResponse
 * @see JournalEntry
 * @see WeeklyAssessment
 */
public class AssessmentRepository {

    private final Context appContext;
    private final QuestionnaireDao questionnaireDao;
    private final JournalDao journalDao;
    private final WeeklyAssessmentDao weeklyAssessmentDao;
    private final ExecutorService executor;

    public AssessmentRepository(Context context) {
        appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        questionnaireDao = db.questionnaireDao();
        journalDao = db.journalDao();
        weeklyAssessmentDao = db.weeklyAssessmentDao();
        executor = Executors.newSingleThreadExecutor();
    }

    // ═════════════════════════════════════════════════════════════════════
    // 1. CHECK-IN MANAGEMENT (Tasks 2.D.2, 2.D.3)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Save a daily check-in response. (Task 2.D.2)
     *
     * <p>Pre-save pipeline:</p>
     * <ol>
     *   <li>Sets dayTimestamp if not already set</li>
     *   <li>Auto-computes distress severity</li>
     *   <li>Auto-builds distress flags JSON</li>
     *   <li>Persists to DB</li>
     *   <li>If gratitudeText is non-empty, auto-creates a JournalEntry(type="gratitude")</li>
     * </ol>
     */
    public void saveCheckIn(QuestionnaireResponse response) {
        executor.execute(() -> {
            // Set day timestamp if missing
            if (response.dayTimestamp == 0) {
                response.dayTimestamp = getStartOfTodayMillis();
            }

            // Auto-compute intelligence fields
            response.computeDistressSeverity();
            response.buildDistressFlags();

            // Persist
            questionnaireDao.insert(response);

            // Auto-create gratitude journal entry
            if (response.gratitudeText != null && !response.gratitudeText.trim().isEmpty()) {
                JournalEntry gratitudeEntry = new JournalEntry();
                gratitudeEntry.timestamp = response.timestamp;
                gratitudeEntry.dayTimestamp = response.dayTimestamp;
                gratitudeEntry.content = response.gratitudeText;
                gratitudeEntry.entryType = "gratitude";
                gratitudeEntry.triggerSource = "check_in";
                gratitudeEntry.moodAtWriting = response.mood;
                gratitudeEntry.stressAtWriting = response.stressLevel;
                gratitudeEntry.isComplete = true;
                gratitudeEntry.computeWordCount();

                // Count gratitude items (split by newlines or commas)
                String[] items = response.gratitudeText.split("[\\n,;]+");
                int count = 0;
                for (String item : items) {
                    if (item.trim().length() > 2) count++;
                }
                gratitudeEntry.gratitudeItemCount = count;

                journalDao.insert(gratitudeEntry);
            }
        });
    }

    /**
     * Save check-in synchronously (for background workers).
     *
     * <p>Mirrors the full pipeline of {@link #saveCheckIn(QuestionnaireResponse)}:
     * dayTimestamp → distress compute → flags → persist → gratitude journal.</p>
     */
    public void saveCheckInSync(QuestionnaireResponse response) {
        if (response.dayTimestamp == 0) {
            response.dayTimestamp = getStartOfTodayMillis();
        }
        response.computeDistressSeverity();
        response.buildDistressFlags();
        questionnaireDao.insert(response);

        // Auto-create gratitude journal entry (mirrors saveCheckIn async path)
        if (response.gratitudeText != null && !response.gratitudeText.trim().isEmpty()) {
            JournalEntry gratitudeEntry = new JournalEntry();
            gratitudeEntry.timestamp = response.timestamp;
            gratitudeEntry.dayTimestamp = response.dayTimestamp;
            gratitudeEntry.content = response.gratitudeText;
            gratitudeEntry.entryType = "gratitude";
            gratitudeEntry.triggerSource = "check_in";
            gratitudeEntry.moodAtWriting = response.mood;
            gratitudeEntry.stressAtWriting = response.stressLevel;
            gratitudeEntry.isComplete = true;
            gratitudeEntry.computeWordCount();

            String[] items = response.gratitudeText.split("[\\n,;]+");
            int count = 0;
            for (String item : items) {
                if (item.trim().length() > 2) count++;
            }
            gratitudeEntry.gratitudeItemCount = count;

            journalDao.insert(gratitudeEntry);
        }
    }

    /** Get recent check-ins. (Task 2.D.3) */
    public List<QuestionnaireResponse> getRecentCheckIns(int limit) {
        return questionnaireDao.getRecentResponses(limit);
    }

    /** Get all check-ins (LiveData for UI). */
    public LiveData<List<QuestionnaireResponse>> getAllCheckIns() {
        return questionnaireDao.getAllResponses();
    }

    /** Get latest check-in (LiveData). */
    public LiveData<QuestionnaireResponse> getLatestCheckIn() {
        return questionnaireDao.getLatestResponse();
    }

    /** Get latest check-in (sync). */
    public QuestionnaireResponse getLatestCheckInSync() {
        return questionnaireDao.getLatestResponseSync();
    }

    /** Get check-ins for a specific day. */
    public List<QuestionnaireResponse> getCheckInsForDay(long dayTimestamp) {
        return questionnaireDao.getResponsesForDay(dayTimestamp);
    }

    /** Whether the user has already checked in today. */
    public boolean hasCheckedInToday() {
        return questionnaireDao.getCheckInCountForDay(getStartOfTodayMillis()) > 0;
    }

    /** Get the most recent stress level (for escape behavior detection). */
    public int getLatestStressLevel() {
        return questionnaireDao.getLatestStressLevel();
    }

    /** Get the timestamp of the most recent high-stress check-in. */
    public Long getLatestHighStressTimestamp() {
        return questionnaireDao.getLatestHighStressTimestamp();
    }

    // ═════════════════════════════════════════════════════════════════════
    // 2. CONSECUTIVE-DAY PATTERN DETECTION (Tasks 2.D.4, 2.D.5)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Count consecutive days ending today where mood was Sad, Anxious, or Numb.
     * (Task 2.D.4)
     *
     * <p>Uses a backward scan from today. If today has no check-in,
     * starts from the most recent check-in day.</p>
     *
     * @return Number of consecutive negative mood days (0 if none)
     */
    public int getConsecutiveSadDays() {
        List<QuestionnaireResponse> responses = questionnaireDao.getNegativeMoodResponses();
        if (responses == null || responses.isEmpty()) return 0;

        int streak = 0;
        long expectedDay = getStartOfTodayMillis();

        for (QuestionnaireResponse r : responses) {
            if (r.dayTimestamp == 0) continue;

            // Allow 1 day gap tolerance (user might miss a day)
            long diffDays = (expectedDay - r.dayTimestamp) / (24L * 60L * 60L * 1000L);

            if (diffDays <= 1) {
                streak++;
                expectedDay = r.dayTimestamp - (24L * 60L * 60L * 1000L);
            } else {
                break; // Streak broken
            }
        }
        return streak;
    }

    /**
     * Count consecutive days ending today where sleep hours < threshold.
     * (Task 2.D.5)
     *
     * @param threshold Hours below which sleep is considered poor (default: 6.0)
     * @return Number of consecutive poor sleep days
     */
    public int getConsecutivePoorSleepDays(float threshold) {
        List<QuestionnaireResponse> responses =
                questionnaireDao.getPoorSleepResponses(threshold);
        if (responses == null || responses.isEmpty()) return 0;

        int streak = 0;
        long expectedDay = getStartOfTodayMillis();

        for (QuestionnaireResponse r : responses) {
            if (r.dayTimestamp == 0) continue;
            long diffDays = (expectedDay - r.dayTimestamp) / (24L * 60L * 60L * 1000L);
            if (diffDays <= 1) {
                streak++;
                expectedDay = r.dayTimestamp - (24L * 60L * 60L * 1000L);
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * Count consecutive days with high stress (>=4).
     * Useful for burnout trajectory detection.
     */
    public int getConsecutiveHighStressDays() {
        List<QuestionnaireResponse> responses =
                questionnaireDao.getHighStressResponses(30);
        if (responses == null || responses.isEmpty()) return 0;

        int streak = 0;
        long expectedDay = getStartOfTodayMillis();

        for (QuestionnaireResponse r : responses) {
            if (r.dayTimestamp == 0) continue;
            long diffDays = (expectedDay - r.dayTimestamp) / (24L * 60L * 60L * 1000L);
            if (diffDays <= 1) {
                streak++;
                expectedDay = r.dayTimestamp - (24L * 60L * 60L * 1000L);
            } else {
                break;
            }
        }
        return streak;
    }

    // ═════════════════════════════════════════════════════════════════════
    // 3. JOURNAL MANAGEMENT (Tasks 2.D.6, 2.D.7, 2.D.8, 2.D.9)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Save a journal entry. (Task 2.D.6)
     *
     * <p>Pre-save pipeline:</p>
     * <ol>
     *   <li>Sets dayTimestamp + timestamp if missing</li>
     *   <li>Auto-computes word count</li>
     *   <li>Persists to DB</li>
     * </ol>
     *
     * <p>Note: AI enrichment (sentiment, distress flags, topic tags)
     * happens asynchronously via NLPProcessor after save.</p>
     */
    public void saveJournalEntry(JournalEntry entry) {
        executor.execute(() -> {
            if (entry.timestamp == 0) entry.timestamp = System.currentTimeMillis();
            if (entry.dayTimestamp == 0) entry.dayTimestamp = getStartOfTodayMillis();
            entry.computeWordCount();
            journalDao.insert(entry);
        });
    }

    /** Save journal entry synchronously. */
    public void saveJournalEntrySync(JournalEntry entry) {
        if (entry.timestamp == 0) entry.timestamp = System.currentTimeMillis();
        if (entry.dayTimestamp == 0) entry.dayTimestamp = getStartOfTodayMillis();
        entry.computeWordCount();
        journalDao.insert(entry);
    }

    /** Get today's journal entries. (Task 2.D.7) */
    public List<JournalEntry> getTodayJournal() {
        return journalDao.getEntriesForDay(getStartOfTodayMillis());
    }

    /** Get all journal entries (LiveData for UI). */
    public LiveData<List<JournalEntry>> getAllJournalEntries() {
        return journalDao.getAllEntries();
    }

    /** Get recent journal entries. */
    public List<JournalEntry> getRecentJournalEntries(int limit) {
        return journalDao.getRecentEntries(limit);
    }

    /**
     * Get current journal streak — consecutive days with at least one entry.
     * (Task 2.D.8)
     *
     * @return Number of consecutive days ending today (or yesterday) with entries
     */
    public int getJournalStreak() {
        List<Long> dayTimestamps = journalDao.getJournalDayTimestamps(
                getStartOfTodayMillis() - (365L * 24L * 60L * 60L * 1000L));
        return computeStreakFromDays(dayTimestamps);
    }

    /**
     * Get the longest journal streak ever achieved. (Task 2.D.9)
     *
     * @return Longest consecutive day count with journal entries
     */
    public int getLongestJournalStreak() {
        List<Long> dayTimestamps = journalDao.getJournalDayTimestamps(0);
        return computeLongestStreakFromDays(dayTimestamps);
    }

    /**
     * Get the check-in streak — consecutive days with at least one check-in.
     */
    public int getCheckInStreak() {
        List<Long> dayTimestamps = questionnaireDao.getCheckInDayTimestamps();
        return computeStreakFromDays(dayTimestamps);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 4. WEEKLY ASSESSMENT MANAGEMENT (Tasks 2.D.10, 2.D.11, 2.D.12)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Save a weekly assessment. (Task 2.D.10)
     *
     * <p>Pre-save pipeline:</p>
     * <ol>
     *   <li>Computes protective factor score</li>
     *   <li>Computes weekly wellness score</li>
     *   <li>Evaluates systemic risk</li>
     *   <li>Sets week number + year</li>
     *   <li>Computes deltas from previous week</li>
     *   <li>Persists to DB</li>
     * </ol>
     */
    public void saveWeeklyAssessment(WeeklyAssessment assessment) {
        executor.execute(() -> saveWeeklyAssessmentSync(assessment));
    }

    /** Save weekly assessment synchronously. */
    public void saveWeeklyAssessmentSync(WeeklyAssessment assessment) {
        if (assessment.timestamp == 0) assessment.timestamp = System.currentTimeMillis();

        // Compute week number and year
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(assessment.weekStartTimestamp);
        assessment.weekNumber = cal.get(Calendar.WEEK_OF_YEAR);
        assessment.year = cal.get(Calendar.YEAR);

        // Compute deltas from previous week
        WeeklyAssessment previous = weeklyAssessmentDao.getLatestAssessmentSync();
        if (previous != null) {
            assessment.screenTimeDeltaMs = assessment.avgScreenTimeMs - previous.avgScreenTimeMs;
            assessment.distressDelta = assessment.avgDistressSeverity - previous.avgDistressSeverity;
            assessment.purposeDelta = assessment.purposeScore - previous.purposeScore;
        }

        // Run computations
        assessment.computeProtectiveFactorScore();
        assessment.computeWeeklyWellnessScore();
        assessment.evaluateSystemicRisk();

        // Check for existing assessment for this week
        WeeklyAssessment existing = weeklyAssessmentDao.getAssessmentByWeekSync(
                assessment.weekStartTimestamp, assessment.weekEndTimestamp);
        if (existing != null) {
            assessment.id = existing.id;
        }

        weeklyAssessmentDao.insertOrReplace(assessment);
    }

    /** Get the latest weekly assessment. (Task 2.D.11) */
    public WeeklyAssessment getLatestWeeklyAssessment() {
        return weeklyAssessmentDao.getLatestAssessmentSync();
    }

    /** Get latest weekly assessment (LiveData for UI). */
    public LiveData<WeeklyAssessment> getLatestWeeklyAssessmentLive() {
        return weeklyAssessmentDao.getLatestAssessment();
    }

    /** Get N most recent weekly assessments. */
    public List<WeeklyAssessment> getWeeklyAssessmentHistory(int limit) {
        return weeklyAssessmentDao.getHistoricalAssessments(limit);
    }

    /**
     * Whether a weekly assessment is due. (Task 2.D.12)
     *
     * <p>Returns true if:</p>
     * <ul>
     *   <li>No assessment exists yet, OR</li>
     *   <li>More than 7 days have passed since the last assessment's weekEndTimestamp</li>
     * </ul>
     */
    public boolean isWeeklyAssessmentDue() {
        WeeklyAssessment latest = weeklyAssessmentDao.getLatestAssessmentSync();
        if (latest == null) return true;

        long now = System.currentTimeMillis();
        long daysSince = (now - latest.weekEndTimestamp) / (24L * 60L * 60L * 1000L);
        return daysSince >= 7;
    }

    /**
     * Get the number of days until the next weekly assessment.
     * Returns 0 if already due.
     */
    public int getDaysUntilWeeklyAssessment() {
        WeeklyAssessment latest = weeklyAssessmentDao.getLatestAssessmentSync();
        if (latest == null) return 0;

        long now = System.currentTimeMillis();
        long daysSince = (now - latest.weekEndTimestamp) / (24L * 60L * 60L * 1000L);
        return Math.max(0, (int) (7 - daysSince));
    }

    // ═════════════════════════════════════════════════════════════════════
    // 5. CROSS-ENTITY INTELLIGENCE
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Get a holistic engagement summary for the dashboard.
     * Combines check-in, journal, and weekly assessment data.
     */
    public EngagementSummary getEngagementSummary() {
        EngagementSummary summary = new EngagementSummary();
        summary.checkInStreak = getCheckInStreak();
        summary.journalStreak = getJournalStreak();
        summary.longestJournalStreak = getLongestJournalStreak();
        summary.totalCheckIns = questionnaireDao.getTotalCheckInCount();
        summary.totalJournalDays = journalDao.getTotalJournalDays();
        summary.totalWeeklyAssessments = weeklyAssessmentDao.getTotalAssessmentCount();
        summary.hasCheckedInToday = hasCheckedInToday();
        summary.weeklyAssessmentDue = isWeeklyAssessmentDue();
        summary.consecutiveSadDays = getConsecutiveSadDays();
        summary.consecutivePoorSleepDays = getConsecutivePoorSleepDays(6.0f);
        return summary;
    }

    /**
     * Get a crisis assessment — are there warning signals?
     */
    public CrisisAssessment getCrisisAssessment() {
        CrisisAssessment assessment = new CrisisAssessment();

        QuestionnaireResponse latest = questionnaireDao.getLatestResponseSync();
        if (latest != null) {
            assessment.latestDistressSeverity = latest.computedDistressSeverity;
            assessment.requestedSupport = latest.requestedSupport;
            assessment.hasCrisisSignals = latest.hasCrisisSignals();
        }

        assessment.consecutiveSadDays = getConsecutiveSadDays();
        assessment.consecutiveHighStressDays = getConsecutiveHighStressDays();
        assessment.avgDistress7Days = questionnaireDao.getAvgDistressSeverity(7);
        assessment.highDistressDays7 = questionnaireDao.getHighDistressDayCount(
                getStartOfDayOffset(-6), 0.7f);

        WeeklyAssessment latestWeekly = weeklyAssessmentDao.getLatestAssessmentSync();
        if (latestWeekly != null) {
            assessment.systemicRiskFlag = latestWeekly.systemicRiskFlag;
            assessment.weeklyTrajectory = latestWeekly.overallTrajectory;
        }

        // Crisis if multiple signals converge
        assessment.isCrisisLevel = assessment.hasCrisisSignals
                || (assessment.consecutiveSadDays >= 5 && assessment.avgDistress7Days > 0.7f)
                || assessment.systemicRiskFlag;

        return assessment;
    }

    // ═════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═════════════════════════════════════════════════════════════════════

    /** Compute current streak from sorted day timestamps (newest first). */
    private int computeStreakFromDays(List<Long> dayTimestamps) {
        if (dayTimestamps == null || dayTimestamps.isEmpty()) return 0;

        // Sort newest first
        java.util.Collections.sort(dayTimestamps, java.util.Collections.reverseOrder());

        int streak = 0;
        long expectedDay = getStartOfTodayMillis();

        for (long dayTs : dayTimestamps) {
            long diffDays = (expectedDay - dayTs) / (24L * 60L * 60L * 1000L);
            if (diffDays <= 1) {
                streak++;
                expectedDay = dayTs - (24L * 60L * 60L * 1000L);
            } else {
                break;
            }
        }
        return streak;
    }

    /** Compute longest streak ever from day timestamps. */
    private int computeLongestStreakFromDays(List<Long> dayTimestamps) {
        if (dayTimestamps == null || dayTimestamps.isEmpty()) return 0;

        // Sort oldest first
        java.util.Collections.sort(dayTimestamps);

        // Deduplicate
        java.util.List<Long> unique = new java.util.ArrayList<>();
        for (long ts : dayTimestamps) {
            if (unique.isEmpty() || ts != unique.get(unique.size() - 1)) {
                unique.add(ts);
            }
        }

        int longestStreak = 1;
        int currentStreak = 1;
        for (int i = 1; i < unique.size(); i++) {
            long diffDays = (unique.get(i) - unique.get(i - 1)) / (24L * 60L * 60L * 1000L);
            if (diffDays <= 1) {
                currentStreak++;
                longestStreak = Math.max(longestStreak, currentStreak);
            } else {
                currentStreak = 1;
            }
        }
        return longestStreak;
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

    // ═════════════════════════════════════════════════════════════════════
    // RESULT TYPES
    // ═════════════════════════════════════════════════════════════════════

    /** Holistic engagement summary across all assessment systems. */
    public static class EngagementSummary {
        public int checkInStreak;
        public int journalStreak;
        public int longestJournalStreak;
        public int totalCheckIns;
        public int totalJournalDays;
        public int totalWeeklyAssessments;
        public boolean hasCheckedInToday;
        public boolean weeklyAssessmentDue;
        public int consecutiveSadDays;
        public int consecutivePoorSleepDays;

        /** Overall engagement score (0.0-1.0). */
        public float getEngagementScore() {
            float score = 0f;
            // Streak rewards
            score += Math.min(1f, checkInStreak / 7f) * 0.35f;
            score += Math.min(1f, journalStreak / 7f) * 0.25f;
            // Today's engagement
            if (hasCheckedInToday) score += 0.20f;
            // Weekly compliance
            if (!weeklyAssessmentDue) score += 0.10f;
            // Volume bonus
            score += Math.min(1f, totalCheckIns / 30f) * 0.10f;
            return Math.min(1f, score);
        }
    }

    /** Multi-signal crisis convergence assessment. */
    public static class CrisisAssessment {
        public float latestDistressSeverity;
        public boolean requestedSupport;
        public boolean hasCrisisSignals;
        public int consecutiveSadDays;
        public int consecutiveHighStressDays;
        public float avgDistress7Days;
        public int highDistressDays7;
        public boolean systemicRiskFlag;
        public String weeklyTrajectory;
        public boolean isCrisisLevel;

        /** Get a human-readable crisis level label. */
        public String getCrisisLabel() {
            if (isCrisisLevel) return "Immediate Attention Needed";
            if (hasCrisisSignals || consecutiveSadDays >= 3) return "Elevated Concern";
            if (avgDistress7Days > 0.5f) return "Moderate Distress";
            return "Stable";
        }

        /** Get an emoji for the crisis level. */
        public String getCrisisEmoji() {
            if (isCrisisLevel) return "🚨";
            if (hasCrisisSignals || consecutiveSadDays >= 3) return "⚠️";
            if (avgDistress7Days > 0.5f) return "🟡";
            return "✅";
        }

        /** Get a color int for the crisis level using MoodMapper. */
        public int getCrisisColor() {
            float risk = Math.min(1f, avgDistress7Days);
            if (isCrisisLevel) risk = 1f;
            else if (hasCrisisSignals) risk = Math.max(risk, 0.75f);
            return MoodMapper.riskToColor(risk);
        }

        /** Returns a prioritized coping suggestion based on severity. */
        public String getCopingSuggestion() {
            if (isCrisisLevel) {
                return "Please reach out to a trusted person or crisis helpline. You don't have to face this alone.";
            }
            if (consecutiveSadDays >= 3) {
                return MoodMapper.getMoodCopingTip(MoodMapper.MOOD_SAD)
                        + " Consider speaking with a counselor.";
            }
            if (consecutiveHighStressDays >= 3) {
                return "Extended high stress can impact health. "
                        + MoodMapper.getMoodCopingTip(MoodMapper.MOOD_ANXIOUS);
            }
            return "You're doing okay. Keep checking in with yourself.";
        }

        /** Returns overall risk score (0.0–1.0). */
        public float getOverallRisk() {
            float risk = avgDistress7Days;
            risk += consecutiveSadDays * 0.05f;
            risk += consecutiveHighStressDays * 0.04f;
            if (hasCrisisSignals) risk += 0.2f;
            if (systemicRiskFlag) risk += 0.15f;
            return Math.min(1f, risk);
        }
    }
}
