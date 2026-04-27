package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.mindtrace.ai.database.entity.WeeklyAssessment;

import java.util.List;

/**
 * Data Access Object for the Weekly Assessment system.
 *
 * <p>Provides 25+ queries across 5 categories:</p>
 * <ol>
 *   <li><b>CRUD</b> — insert, update, upsert</li>
 *   <li><b>Lookup</b> — by week, by range, latest, before date</li>
 *   <li><b>Aggregate Analytics</b> — averages, trends, counts</li>
 *   <li><b>Risk Intelligence</b> — systemic risk, trajectory, crisis detection</li>
 *   <li><b>ML Feature Extraction</b> — trend arrays for classifiers</li>
 * </ol>
 */
@Dao
public interface WeeklyAssessmentDao {

    // ═════════════════════════════════════════════════════════════════════
    // 1. CRUD
    // ═════════════════════════════════════════════════════════════════════

    @Insert
    void insert(WeeklyAssessment assessment);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(WeeklyAssessment assessment);

    @Update
    void update(WeeklyAssessment assessment);

    @Query("DELETE FROM weekly_assessments WHERE id = :id")
    void deleteById(int id);

    // ═════════════════════════════════════════════════════════════════════
    // 2. LOOKUP QUERIES
    // ═════════════════════════════════════════════════════════════════════

    /** All assessments, newest first (LiveData). */
    @Query("SELECT * FROM weekly_assessments ORDER BY weekEndTimestamp DESC")
    LiveData<List<WeeklyAssessment>> getAllAssessments();

    /** All assessments, newest first (sync). */
    @Query("SELECT * FROM weekly_assessments ORDER BY weekEndTimestamp DESC")
    List<WeeklyAssessment> getAllAssessmentsSync();

    /** Most recent assessment (sync). */
    @Query("SELECT * FROM weekly_assessments ORDER BY weekEndTimestamp DESC LIMIT 1")
    WeeklyAssessment getLatestAssessmentSync();

    /** Most recent assessment (LiveData). */
    @Query("SELECT * FROM weekly_assessments ORDER BY weekEndTimestamp DESC LIMIT 1")
    LiveData<WeeklyAssessment> getLatestAssessment();

    /** Get assessment for a specific week window. */
    @Query("SELECT * FROM weekly_assessments WHERE weekStartTimestamp = :start AND weekEndTimestamp = :end LIMIT 1")
    WeeklyAssessment getAssessmentByWeekSync(long start, long end);

    /** Get assessment by week number and year. */
    @Query("SELECT * FROM weekly_assessments WHERE weekNumber = :weekNumber AND year = :year LIMIT 1")
    WeeklyAssessment getAssessmentByWeekNumber(int weekNumber, int year);

    /** N most recent assessments for historical tracking. */
    @Query("SELECT * FROM weekly_assessments ORDER BY weekEndTimestamp DESC LIMIT :limit")
    List<WeeklyAssessment> getHistoricalAssessments(int limit);

    /** Assessments in a date range. */
    @Query("SELECT * FROM weekly_assessments WHERE weekStartTimestamp >= :start AND weekEndTimestamp <= :end ORDER BY weekStartTimestamp ASC")
    List<WeeklyAssessment> getAssessmentsBetween(long start, long end);

    /**
     * Latest assessment ending before a specific date. (Task 2.B.3)
     * Useful for time-scoped lookups or preventing overlap.
     */
    @Query("SELECT * FROM weekly_assessments WHERE weekEndTimestamp <= :date ORDER BY weekEndTimestamp DESC LIMIT 1")
    WeeklyAssessment getLatestBeforeDate(long date);

    /** Total number of completed assessments. */
    @Query("SELECT COUNT(*) FROM weekly_assessments")
    int getTotalAssessmentCount();

    // ═════════════════════════════════════════════════════════════════════
    // 3. AGGREGATE ANALYTICS
    // ═════════════════════════════════════════════════════════════════════

    /** Average purpose score over the last N weeks. (Task 2.B.4) */
    @Query("SELECT AVG(purposeScore) FROM (" +
            "SELECT purposeScore FROM weekly_assessments " +
            "WHERE purposeScore > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit)")
    float getAveragePurposeScore(int limit);

    /** Average burnout risk over the last N weeks. */
    @Query("SELECT AVG(burnoutRiskScore) FROM (" +
            "SELECT burnoutRiskScore FROM weekly_assessments " +
            "WHERE burnoutRiskScore > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit)")
    float getAverageBurnoutRiskScore(int limit);

    /** Average social connection over the last N weeks. */
    @Query("SELECT AVG(socialConnectionScore) FROM (" +
            "SELECT socialConnectionScore FROM weekly_assessments " +
            "WHERE socialConnectionScore > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit)")
    float getAverageSocialConnection(int limit);

    /** Average anhedonia score over the last N weeks. */
    @Query("SELECT AVG(anhedoniaScore) FROM (" +
            "SELECT anhedoniaScore FROM weekly_assessments " +
            "WHERE anhedoniaScore > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit)")
    float getAverageAnhedoniaScore(int limit);

    /** Average weekly wellness score over the last N weeks. */
    @Query("SELECT AVG(weeklyWellnessScore) FROM (" +
            "SELECT weeklyWellnessScore FROM weekly_assessments " +
            "WHERE weeklyWellnessScore > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit)")
    float getAverageWellnessScore(int limit);

    /** Average screen time over the last N weeks. */
    @Query("SELECT AVG(avgScreenTimeMs) FROM (" +
            "SELECT avgScreenTimeMs FROM weekly_assessments " +
            "WHERE avgScreenTimeMs > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit)")
    long getAverageScreenTime(int limit);

    // ═════════════════════════════════════════════════════════════════════
    // 4. RISK INTELLIGENCE
    // ═════════════════════════════════════════════════════════════════════

    /** Count of systemic risk flags in the last N weeks. */
    @Query("SELECT COUNT(*) FROM (" +
            "SELECT systemicRiskFlag FROM weekly_assessments " +
            "ORDER BY weekEndTimestamp DESC LIMIT :limit" +
            ") WHERE systemicRiskFlag = 1")
    int getSystemicRiskCount(int limit);

    /** Get all weeks flagged as systemic risk. */
    @Query("SELECT * FROM weekly_assessments WHERE systemicRiskFlag = 1 ORDER BY weekEndTimestamp DESC")
    List<WeeklyAssessment> getSystemicRiskWeeks();

    /** Get weeks with crisis trajectory. */
    @Query("SELECT * FROM weekly_assessments WHERE overallTrajectory = 'crisis' ORDER BY weekEndTimestamp DESC")
    List<WeeklyAssessment> getCrisisWeeks();

    /** Get weeks where the trajectory was declining or crisis. */
    @Query("SELECT * FROM weekly_assessments WHERE overallTrajectory IN ('declining', 'crisis') ORDER BY weekEndTimestamp DESC LIMIT :limit")
    List<WeeklyAssessment> getDecliningWeeks(int limit);

    /** Count of improving weeks in the last N. */
    @Query("SELECT COUNT(*) FROM (" +
            "SELECT overallTrajectory FROM weekly_assessments " +
            "ORDER BY weekEndTimestamp DESC LIMIT :limit" +
            ") WHERE overallTrajectory = 'improving'")
    int getImprovingWeekCount(int limit);

    // ═════════════════════════════════════════════════════════════════════
    // 5. ML FEATURE EXTRACTION
    // ═════════════════════════════════════════════════════════════════════

    /** Purpose scores for trend regression (oldest→newest). */
    @Query("SELECT purposeScore FROM (" +
            "SELECT purposeScore, weekEndTimestamp FROM weekly_assessments " +
            "WHERE purposeScore > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit" +
            ") ORDER BY weekEndTimestamp ASC")
    List<Integer> getPurposeScoreTrend(int limit);

    /** Burnout scores for trend regression (oldest→newest). */
    @Query("SELECT burnoutRiskScore FROM (" +
            "SELECT burnoutRiskScore, weekEndTimestamp FROM weekly_assessments " +
            "WHERE burnoutRiskScore > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit" +
            ") ORDER BY weekEndTimestamp ASC")
    List<Integer> getBurnoutTrend(int limit);

    /** Wellness scores for trend regression (oldest→newest). */
    @Query("SELECT weeklyWellnessScore FROM (" +
            "SELECT weeklyWellnessScore, weekEndTimestamp FROM weekly_assessments " +
            "WHERE weeklyWellnessScore > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit" +
            ") ORDER BY weekEndTimestamp ASC")
    List<Float> getWellnessTrend(int limit);

    /** Social connection scores for trend regression (oldest→newest). */
    @Query("SELECT socialConnectionScore FROM (" +
            "SELECT socialConnectionScore, weekEndTimestamp FROM weekly_assessments " +
            "WHERE socialConnectionScore > 0 ORDER BY weekEndTimestamp DESC LIMIT :limit" +
            ") ORDER BY weekEndTimestamp ASC")
    List<Integer> getSocialConnectionTrend(int limit);

    /** Overall trajectories for pattern detection. */
    @Query("SELECT overallTrajectory FROM weekly_assessments " +
            "WHERE overallTrajectory IS NOT NULL " +
            "ORDER BY weekEndTimestamp DESC LIMIT :limit")
    List<String> getTrajectoryHistory(int limit);
}
