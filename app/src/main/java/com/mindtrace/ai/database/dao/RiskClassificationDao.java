package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.List;

/**
 * DAO for {@link RiskClassification} — the AI engine's daily risk output.
 */
@Dao
public interface RiskClassificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrReplace(RiskClassification classification);

    @Insert
    void insert(RiskClassification classification);

    // ── Latest ──

    @Query("SELECT * FROM risk_classifications ORDER BY timestamp DESC LIMIT 1")
    RiskClassification getLatestSync();

    @Query("SELECT * FROM risk_classifications ORDER BY timestamp DESC LIMIT 1")
    LiveData<RiskClassification> getLatest();

    // ── By day ──

    @Query("SELECT * FROM risk_classifications WHERE dayTimestamp = :dayTimestamp LIMIT 1")
    RiskClassification getForDay(long dayTimestamp);

    @Query("SELECT * FROM risk_classifications WHERE dayTimestamp = :dayTimestamp LIMIT 1")
    LiveData<RiskClassification> observeForDay(long dayTimestamp);

    // ── History ──

    @Query("SELECT * FROM risk_classifications ORDER BY timestamp DESC LIMIT :limit")
    List<RiskClassification> getHistory(int limit);

    @Query("SELECT * FROM risk_classifications ORDER BY timestamp DESC LIMIT :limit")
    LiveData<List<RiskClassification>> observeHistory(int limit);

    @Query("SELECT * FROM risk_classifications WHERE dayTimestamp >= :since ORDER BY dayTimestamp ASC")
    List<RiskClassification> getHistorySince(long since);

    // ── Category trend analysis ──

    @Query("SELECT * FROM risk_classifications WHERE dayTimestamp >= :since ORDER BY dayTimestamp ASC")
    LiveData<List<RiskClassification>> observeTrendSince(long since);

    @Query("SELECT AVG(overallRiskScore) FROM risk_classifications WHERE dayTimestamp >= :since")
    float getAverageRiskSince(long since);

    @Query("SELECT AVG(digitalAddictionScore) FROM risk_classifications WHERE dayTimestamp >= :since")
    float getAverageDigitalAddictionSince(long since);

    @Query("SELECT AVG(stressAnxietyScore) FROM risk_classifications WHERE dayTimestamp >= :since")
    float getAverageStressSince(long since);

    @Query("SELECT AVG(depressionRiskScore) FROM risk_classifications WHERE dayTimestamp >= :since")
    float getAverageDepressionSince(long since);

    @Query("SELECT AVG(socialIsolationScore) FROM risk_classifications WHERE dayTimestamp >= :since")
    float getAverageIsolationSince(long since);

    @Query("SELECT AVG(sleepDisruptionScore) FROM risk_classifications WHERE dayTimestamp >= :since")
    float getAverageSleepDisruptionSince(long since);

    @Query("SELECT AVG(lowFulfilmentScore) FROM risk_classifications WHERE dayTimestamp >= :since")
    float getAverageFulfilmentSince(long since);

    // ── Crisis queries ──

    @Query("SELECT * FROM risk_classifications WHERE crisisFlag = 1 ORDER BY timestamp DESC LIMIT :limit")
    List<RiskClassification> getCrisisEvents(int limit);

    @Query("SELECT COUNT(*) FROM risk_classifications WHERE crisisFlag = 1 AND dayTimestamp >= :since")
    int getCrisisCountSince(long since);

    // ── High risk queries ──

    @Query("SELECT * FROM risk_classifications WHERE overallRiskScore >= :threshold ORDER BY timestamp DESC LIMIT :limit")
    List<RiskClassification> getHighRiskDays(float threshold, int limit);

    @Query("SELECT COUNT(*) FROM risk_classifications WHERE overallRiskScore >= :threshold AND dayTimestamp >= :since")
    int getHighRiskDayCountSince(float threshold, long since);

    // ── Peak queries ──

    @Query("SELECT * FROM risk_classifications ORDER BY overallRiskScore DESC LIMIT 1")
    RiskClassification getPeakRiskDay();

    @Query("SELECT * FROM risk_classifications ORDER BY overallRiskScore ASC LIMIT 1")
    RiskClassification getBestDay();

    // ── Count ──

    @Query("SELECT COUNT(*) FROM risk_classifications")
    int getTotalClassificationCount();

    @Query("DELETE FROM risk_classifications WHERE dayTimestamp < :before")
    void deleteOlderThan(long before);
}
