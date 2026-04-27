package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.mindtrace.ai.database.entity.QuestionnaireResponse;

import java.util.List;

/**
 * Data Access Object for the psychological check-in system.
 *
 * <p>Provides 40+ query methods organized into 7 categories:</p>
 * <ol>
 *   <li><b>CRUD</b> — insert, update, delete</li>
 *   <li><b>Lookup</b> — by day, by range, latest, today</li>
 *   <li><b>Mood Analytics</b> — trend, distribution, streaks</li>
 *   <li><b>Distress Intelligence</b> — crisis detection, severity tracking</li>
 *   <li><b>Dimensional Analytics</b> — per-dimension aggregations</li>
 *   <li><b>Streak & Engagement</b> — consistency tracking</li>
 *   <li><b>ML Feature Extraction</b> — data for classifiers</li>
 * </ol>
 */
@Dao
public interface QuestionnaireDao {

    // ═════════════════════════════════════════════════════════════════════
    // 1. CRUD OPERATIONS
    // ═════════════════════════════════════════════════════════════════════

    @Insert
    void insert(QuestionnaireResponse response);

    @Insert
    long insertAndReturnId(QuestionnaireResponse response);

    @Update
    void update(QuestionnaireResponse response);

    @Query("DELETE FROM questionnaire_responses WHERE id = :id")
    void deleteById(int id);

    // ═════════════════════════════════════════════════════════════════════
    // 2. LOOKUP QUERIES
    // ═════════════════════════════════════════════════════════════════════

    /** All responses, newest first (LiveData for UI). */
    @Query("SELECT * FROM questionnaire_responses ORDER BY timestamp DESC")
    LiveData<List<QuestionnaireResponse>> getAllResponses();

    /** All responses, newest first (sync for background). */
    @Query("SELECT * FROM questionnaire_responses ORDER BY timestamp DESC")
    List<QuestionnaireResponse> getAllResponsesSync();

    /** Most recent N responses. */
    @Query("SELECT * FROM questionnaire_responses ORDER BY timestamp DESC LIMIT :limit")
    List<QuestionnaireResponse> getRecentResponses(int limit);

    /** Default: last 7 responses. */
    @Query("SELECT * FROM questionnaire_responses ORDER BY timestamp DESC LIMIT 7")
    List<QuestionnaireResponse> getRecentResponses();

    /** Responses since a given timestamp. */
    @Query("SELECT * FROM questionnaire_responses WHERE timestamp >= :since ORDER BY timestamp DESC")
    List<QuestionnaireResponse> getResponsesSinceSync(long since);

    /** Responses between two timestamps. */
    @Query("SELECT * FROM questionnaire_responses WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    List<QuestionnaireResponse> getResponsesBetweenSync(long start, long end);

    /** Get the most recent response (sync). */
    @Query("SELECT * FROM questionnaire_responses ORDER BY timestamp DESC LIMIT 1")
    QuestionnaireResponse getLatestResponseSync();

    /** Get the most recent response (LiveData). */
    @Query("SELECT * FROM questionnaire_responses ORDER BY timestamp DESC LIMIT 1")
    LiveData<QuestionnaireResponse> getLatestResponse();

    /** Get today's response(s) for a specific day timestamp. */
    @Query("SELECT * FROM questionnaire_responses WHERE dayTimestamp = :dayTimestamp ORDER BY timestamp DESC")
    List<QuestionnaireResponse> getResponsesForDay(long dayTimestamp);

    /** Get the most recent morning check-in. */
    @Query("SELECT * FROM questionnaire_responses WHERE checkInType = 'morning' ORDER BY timestamp DESC LIMIT 1")
    QuestionnaireResponse getLatestMorningCheckIn();

    /** Get the most recent evening check-in. */
    @Query("SELECT * FROM questionnaire_responses WHERE checkInType = 'evening' ORDER BY timestamp DESC LIMIT 1")
    QuestionnaireResponse getLatestEveningCheckIn();

    /** Total number of check-ins ever recorded. */
    @Query("SELECT COUNT(*) FROM questionnaire_responses")
    int getTotalCheckInCount();

    /** Number of distinct days with check-ins. */
    @Query("SELECT COUNT(DISTINCT dayTimestamp) FROM questionnaire_responses WHERE dayTimestamp > 0")
    int getDistinctCheckInDays();

    // ═════════════════════════════════════════════════════════════════════
    // 3. MOOD ANALYTICS
    // ═════════════════════════════════════════════════════════════════════

    /** Get mood values for the last N check-ins (for trend chart). */
    @Query("SELECT mood FROM questionnaire_responses WHERE mood IS NOT NULL ORDER BY timestamp DESC LIMIT :limit")
    List<String> getMoodHistory(int limit);

    /** Count of each mood type in the last N days. */
    @Query("SELECT mood, COUNT(*) as cnt FROM questionnaire_responses " +
            "WHERE timestamp >= :since AND mood IS NOT NULL " +
            "GROUP BY mood ORDER BY cnt DESC")
    List<MoodCount> getMoodDistribution(long since);

    /** Count consecutive days with a specific mood (most recent streak). */
    @Query("SELECT COUNT(*) FROM (" +
            "SELECT dayTimestamp FROM questionnaire_responses " +
            "WHERE mood = :mood AND dayTimestamp > 0 " +
            "GROUP BY dayTimestamp " +
            "ORDER BY dayTimestamp DESC" +
            ")")
    int getDaysWithMood(String mood);

    /** Count consecutive sad/anxious days from today backwards. */
    @Query("SELECT * FROM questionnaire_responses WHERE mood IN ('Sad', 'Anxious', 'Numb') " +
            "AND dayTimestamp > 0 ORDER BY dayTimestamp DESC")
    List<QuestionnaireResponse> getNegativeMoodResponses();

    /** Average stress level over last N responses. */
    @Query("SELECT AVG(stressLevel) FROM questionnaire_responses " +
            "WHERE stressLevel > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgStressLevel(int limit);

    /** Average anxiety level over last N responses. */
    @Query("SELECT AVG(anxietyLevel) FROM questionnaire_responses " +
            "WHERE anxietyLevel > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgAnxietyLevel(int limit);

    // ═════════════════════════════════════════════════════════════════════
    // 4. DISTRESS INTELLIGENCE
    // ═════════════════════════════════════════════════════════════════════

    /** Get all responses where distress severity exceeds a threshold. */
    @Query("SELECT * FROM questionnaire_responses " +
            "WHERE computedDistressSeverity >= :threshold " +
            "ORDER BY timestamp DESC")
    List<QuestionnaireResponse> getHighDistressResponses(float threshold);

    /** Get responses where the user requested support. */
    @Query("SELECT * FROM questionnaire_responses " +
            "WHERE requestedSupport = 1 ORDER BY timestamp DESC")
    List<QuestionnaireResponse> getSupportRequestResponses();

    /** Get responses with non-null distress flags. */
    @Query("SELECT * FROM questionnaire_responses " +
            "WHERE distressFlags IS NOT NULL AND distressFlags != '' " +
            "ORDER BY timestamp DESC")
    List<QuestionnaireResponse> getDistressFlaggedResponses();

    /** Count of high-distress days in last N days. */
    @Query("SELECT COUNT(DISTINCT dayTimestamp) FROM questionnaire_responses " +
            "WHERE computedDistressSeverity >= :threshold AND timestamp >= :since")
    int getHighDistressDayCount(long since, float threshold);

    /** Average distress severity over last N responses. */
    @Query("SELECT AVG(computedDistressSeverity) FROM questionnaire_responses " +
            "WHERE computedDistressSeverity > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgDistressSeverity(int limit);

    /** Get the peak distress severity ever recorded. */
    @Query("SELECT MAX(computedDistressSeverity) FROM questionnaire_responses")
    float getPeakDistressSeverity();

    /** Responses where feltLikeCrying = true. */
    @Query("SELECT * FROM questionnaire_responses WHERE feltLikeCrying = 1 " +
            "ORDER BY timestamp DESC LIMIT :limit")
    List<QuestionnaireResponse> getCryingResponses(int limit);

    /** Responses where user wanted to withdraw. */
    @Query("SELECT * FROM questionnaire_responses WHERE wantedToWithdraw = 1 " +
            "ORDER BY timestamp DESC LIMIT :limit")
    List<QuestionnaireResponse> getWithdrawalResponses(int limit);

    // ═════════════════════════════════════════════════════════════════════
    // 5. DIMENSIONAL ANALYTICS
    // ═════════════════════════════════════════════════════════════════════

    /** Average hope level over last N (trend detection). */
    @Query("SELECT AVG(hopeLevel) FROM questionnaire_responses " +
            "WHERE hopeLevel > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgHopeLevel(int limit);

    /** Average self-worth over last N (depression trajectory). */
    @Query("SELECT AVG(selfWorthScore) FROM questionnaire_responses " +
            "WHERE selfWorthScore > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgSelfWorth(int limit);

    /** Average motivation over last N. */
    @Query("SELECT AVG(motivationLevel) FROM questionnaire_responses " +
            "WHERE motivationLevel > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgMotivation(int limit);

    /** Average loneliness over last N. */
    @Query("SELECT AVG(lonelinessLevel) FROM questionnaire_responses " +
            "WHERE lonelinessLevel > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgLoneliness(int limit);

    /** Average sleep hours over last N. */
    @Query("SELECT AVG(sleepHours) FROM questionnaire_responses " +
            "WHERE sleepHours > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgSleepHours(int limit);

    /** Average sleep quality over last N. */
    @Query("SELECT AVG(sleepQuality) FROM questionnaire_responses " +
            "WHERE sleepQuality > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgSleepQuality(int limit);

    /** Average purpose score over last N. */
    @Query("SELECT AVG(purposeScore) FROM questionnaire_responses " +
            "WHERE purposeScore > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgPurposeScore(int limit);

    /** Average mental clarity over last N. */
    @Query("SELECT AVG(mentalClarity) FROM questionnaire_responses " +
            "WHERE mentalClarity > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgMentalClarity(int limit);

    /** Average rumination over last N. */
    @Query("SELECT AVG(ruminationLevel) FROM questionnaire_responses " +
            "WHERE ruminationLevel > 0 ORDER BY timestamp DESC LIMIT :limit")
    float getAvgRumination(int limit);

    /** Count of days user exercised in date range. */
    @Query("SELECT COUNT(*) FROM questionnaire_responses " +
            "WHERE exercisedToday = 1 AND timestamp >= :since")
    int getExerciseDayCount(long since);

    /** Distribution of coping mechanisms. */
    @Query("SELECT copingMechanism, COUNT(*) as cnt FROM questionnaire_responses " +
            "WHERE copingMechanism IS NOT NULL AND copingMechanism != '' " +
            "AND timestamp >= :since GROUP BY copingMechanism ORDER BY cnt DESC")
    List<CopingCount> getCopingDistribution(long since);

    // ═════════════════════════════════════════════════════════════════════
    // 6. STREAK & ENGAGEMENT
    // ═════════════════════════════════════════════════════════════════════

    /** Get all distinct day timestamps with check-ins, ordered. */
    @Query("SELECT DISTINCT dayTimestamp FROM questionnaire_responses " +
            "WHERE dayTimestamp > 0 ORDER BY dayTimestamp DESC")
    List<Long> getCheckInDayTimestamps();

    /** Count of consecutive days with poor sleep (hours < threshold). */
    @Query("SELECT * FROM questionnaire_responses " +
            "WHERE sleepHours > 0 AND sleepHours < :threshold " +
            "ORDER BY timestamp DESC")
    List<QuestionnaireResponse> getPoorSleepResponses(float threshold);

    /** Responses with high stress (>=4) — for escape behavior correlation. */
    @Query("SELECT * FROM questionnaire_responses " +
            "WHERE stressLevel >= 4 ORDER BY timestamp DESC LIMIT :limit")
    List<QuestionnaireResponse> getHighStressResponses(int limit);

    /** Get the most recent high-stress check-in timestamp (for escape detection). */
    @Query("SELECT timestamp FROM questionnaire_responses " +
            "WHERE stressLevel >= 4 ORDER BY timestamp DESC LIMIT 1")
    Long getLatestHighStressTimestamp();

    /** Get most recent stress level (for real-time escape detection). */
    @Query("SELECT stressLevel FROM questionnaire_responses " +
            "ORDER BY timestamp DESC LIMIT 1")
    int getLatestStressLevel();

    // ═════════════════════════════════════════════════════════════════════
    // 7. ML FEATURE EXTRACTION SUPPORT
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Get the N most recent self-worth scores for trend analysis.
     * Returns oldest→newest for linear regression.
     */
    @Query("SELECT selfWorthScore FROM (" +
            "SELECT selfWorthScore, timestamp FROM questionnaire_responses " +
            "WHERE selfWorthScore > 0 ORDER BY timestamp DESC LIMIT :limit" +
            ") ORDER BY timestamp ASC")
    List<Integer> getSelfWorthTrend(int limit);

    /**
     * Get N most recent hope levels for trend analysis.
     */
    @Query("SELECT hopeLevel FROM (" +
            "SELECT hopeLevel, timestamp FROM questionnaire_responses " +
            "WHERE hopeLevel > 0 ORDER BY timestamp DESC LIMIT :limit" +
            ") ORDER BY timestamp ASC")
    List<Integer> getHopeTrend(int limit);

    /**
     * Get N most recent stress levels for trend analysis.
     */
    @Query("SELECT stressLevel FROM (" +
            "SELECT stressLevel, timestamp FROM questionnaire_responses " +
            "WHERE stressLevel > 0 ORDER BY timestamp DESC LIMIT :limit" +
            ") ORDER BY timestamp ASC")
    List<Integer> getStressTrend(int limit);

    /**
     * Get N most recent motivation levels for trend analysis.
     */
    @Query("SELECT motivationLevel FROM (" +
            "SELECT motivationLevel, timestamp FROM questionnaire_responses " +
            "WHERE motivationLevel > 0 ORDER BY timestamp DESC LIMIT :limit" +
            ") ORDER BY timestamp ASC")
    List<Integer> getMotivationTrend(int limit);

    /**
     * Get whether the last N check-ins have consecutive negative mood.
     * Returns count of consecutive Sad/Anxious/Numb at the end.
     */
    @Query("SELECT mood FROM questionnaire_responses " +
            "WHERE mood IS NOT NULL ORDER BY timestamp DESC LIMIT :limit")
    List<String> getRecentMoods(int limit);

    /**
     * Check if there is a response from today (prevents duplicate check-ins).
     */
    @Query("SELECT COUNT(*) FROM questionnaire_responses WHERE dayTimestamp = :dayTimestamp")
    int getCheckInCountForDay(long dayTimestamp);

    /**
     * Get the response closest to a given timestamp (for correlating with usage data).
     */
    @Query("SELECT * FROM questionnaire_responses " +
            "WHERE timestamp <= :targetTime ORDER BY timestamp DESC LIMIT 1")
    QuestionnaireResponse getClosestResponseBefore(long targetTime);

    // ═════════════════════════════════════════════════════════════════════
    // RESULT TYPES for GROUP BY queries
    // ═════════════════════════════════════════════════════════════════════

    /** Mood distribution result row. */
    class MoodCount {
        public String mood;
        public int cnt;
    }

    /** Coping mechanism distribution result row. */
    class CopingCount {
        public String copingMechanism;
        public int cnt;
    }

    /** Count of check-ins since a timestamp. */
    @Query("SELECT COUNT(*) FROM questionnaire_responses WHERE timestamp >= :since")
    int getCountSince(long since);
}
