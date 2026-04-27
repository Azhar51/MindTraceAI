package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.mindtrace.ai.database.entity.BehaviorSnapshotEntity;

import java.util.List;

/**
 * Data Access Object for the {@code behavior_snapshots} table.
 *
 * <p>Provides access to computed behavioural intelligence data across
 * 4 layers: attention, consumption, circadian, and escape behaviour.
 * Used by the AI pipeline, anomaly detector, and insight engine.</p>
 *
 * @see BehaviorSnapshotEntity
 */
@Dao
public interface BehaviorSnapshotDao {

    // ─────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(BehaviorSnapshotEntity snapshot);

    @Update
    void update(BehaviorSnapshotEntity snapshot);

    @Query("DELETE FROM behavior_snapshots WHERE dayTimestamp = :dayTimestamp")
    void deleteForDay(long dayTimestamp);

    @Query("DELETE FROM behavior_snapshots")
    void deleteAll();

    // ─────────────────────────────────────────────────────────────────────
    // LATEST & SINGLE-DAY — Dashboard display
    // ─────────────────────────────────────────────────────────────────────

    /** Latest snapshot (today or most recent). */
    @Query("SELECT * FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT 1")
    LiveData<BehaviorSnapshotEntity> observeLatest();

    @Query("SELECT * FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT 1")
    BehaviorSnapshotEntity getLatestSync();

    /** Snapshot for a specific day. */
    @Query("SELECT * FROM behavior_snapshots WHERE dayTimestamp = :dayTimestamp LIMIT 1")
    BehaviorSnapshotEntity getForDay(long dayTimestamp);

    @Query("SELECT * FROM behavior_snapshots WHERE dayTimestamp = :dayTimestamp LIMIT 1")
    LiveData<BehaviorSnapshotEntity> observeForDay(long dayTimestamp);

    // ─────────────────────────────────────────────────────────────────────
    // HISTORY — For trend computation and baseline
    // ─────────────────────────────────────────────────────────────────────

    /** Last 7 snapshots. */
    @Query("SELECT * FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT 7")
    LiveData<List<BehaviorSnapshotEntity>> observeLast7();

    @Query("SELECT * FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT 7")
    List<BehaviorSnapshotEntity> getLast7Sync();

    /** Last N snapshots. */
    @Query("SELECT * FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT :limit")
    List<BehaviorSnapshotEntity> getRecentSync(int limit);

    /** Snapshots since a timestamp. */
    @Query("SELECT * FROM behavior_snapshots WHERE dayTimestamp >= :since ORDER BY dayTimestamp DESC")
    LiveData<List<BehaviorSnapshotEntity>> observeSince(long since);

    @Query("SELECT * FROM behavior_snapshots WHERE dayTimestamp >= :since ORDER BY dayTimestamp DESC")
    List<BehaviorSnapshotEntity> getSinceSync(long since);

    /** Snapshots between dates. */
    @Query("SELECT * FROM behavior_snapshots WHERE dayTimestamp BETWEEN :start AND :end ORDER BY dayTimestamp ASC")
    List<BehaviorSnapshotEntity> getBetweenSync(long start, long end);

    // ─────────────────────────────────────────────────────────────────────
    // RISK-LEVEL QUERIES — For pattern detection
    // ─────────────────────────────────────────────────────────────────────

    /** Get days where overall risk exceeded a threshold. */
    @Query("SELECT * FROM behavior_snapshots WHERE overallBehaviorRiskScore > :threshold ORDER BY dayTimestamp DESC LIMIT :limit")
    List<BehaviorSnapshotEntity> getHighRiskDays(float threshold, int limit);

    /** Count consecutive days with risk above threshold (streak detection). */
    @Query("SELECT COUNT(*) FROM behavior_snapshots WHERE dayTimestamp >= :since AND overallBehaviorRiskScore > :threshold")
    int getHighRiskDayCount(long since, float threshold);

    /** Get "green days" (risk < 0.3, good diet, no binges). */
    @Query("SELECT * FROM behavior_snapshots WHERE overallBehaviorRiskScore < 0.3 AND digitalDietScore > 0.5 AND bingeSessionCount = 0 ORDER BY dayTimestamp DESC LIMIT :limit")
    List<BehaviorSnapshotEntity> getGreenDays(int limit);

    /** Count green days in a period. */
    @Query("SELECT COUNT(*) FROM behavior_snapshots WHERE dayTimestamp >= :since AND overallBehaviorRiskScore < 0.3")
    int getGreenDayCount(long since);

    // ─────────────────────────────────────────────────────────────────────
    // ATTENTION LAYER — Fragmentation & compulsiveness trends
    // ─────────────────────────────────────────────────────────────────────

    /** Average fragmentation index over last N days. */
    @Query("SELECT AVG(fragmentationIndex) FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT :days")
    float getAvgFragmentation(int days);

    /** Average attention span over last N days. */
    @Query("SELECT AVG(attentionSpanAvgMs) FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT :days")
    long getAvgAttentionSpan(int days);

    /** Average compulsive check score over last N days. */
    @Query("SELECT AVG(compulsiveCheckScore) FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT :days")
    float getAvgCompulsiveScore(int days);

    // ─────────────────────────────────────────────────────────────────────
    // CONSUMPTION LAYER — Passive/active trends
    // ─────────────────────────────────────────────────────────────────────

    /** Average passive consumption ratio over last N days. */
    @Query("SELECT AVG(passiveConsumptionRatio) FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT :days")
    float getAvgPassiveRatio(int days);

    /** Average digital diet score over last N days. */
    @Query("SELECT AVG(digitalDietScore) FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT :days")
    float getAvgDietScore(int days);

    /** Average app diversity over last N days. */
    @Query("SELECT AVG(appDiversityScore) FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT :days")
    float getAvgDiversity(int days);

    /** Days with loop patterns detected. */
    @Query("SELECT * FROM behavior_snapshots WHERE hasLoopPattern = 1 ORDER BY dayTimestamp DESC LIMIT :limit")
    List<BehaviorSnapshotEntity> getLoopDays(int limit);

    // ─────────────────────────────────────────────────────────────────────
    // CIRCADIAN LAYER — Night/sleep pattern trends
    // ─────────────────────────────────────────────────────────────────────

    /** Average night usage over last N days. */
    @Query("SELECT AVG(lateNightUsageMillis) FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT :days")
    long getAvgNightUsage(int days);

    /** Average morning phone grab time over last N days. */
    @Query("SELECT AVG(morningPhoneGrabMs) FROM behavior_snapshots WHERE morningPhoneGrabMs > 0 ORDER BY dayTimestamp DESC LIMIT :days")
    long getAvgMorningGrab(int days);

    /** Average bedtime scroll time over last N days. */
    @Query("SELECT AVG(bedtimeScrollMs) FROM behavior_snapshots WHERE bedtimeScrollMs > 0 ORDER BY dayTimestamp DESC LIMIT :days")
    long getAvgBedtimeScroll(int days);

    // ─────────────────────────────────────────────────────────────────────
    // ESCAPE LAYER — Avoidance pattern detection
    // ─────────────────────────────────────────────────────────────────────

    /** Get days with escape behaviour detected. */
    @Query("SELECT * FROM behavior_snapshots WHERE escapeBehaviorScore > 0.5 ORDER BY dayTimestamp DESC LIMIT :limit")
    List<BehaviorSnapshotEntity> getEscapeDays(int limit);

    /** Get avoidance days. */
    @Query("SELECT * FROM behavior_snapshots WHERE isAvoidanceDayFlag = 1 ORDER BY dayTimestamp DESC LIMIT :limit")
    List<BehaviorSnapshotEntity> getAvoidanceDays(int limit);

    /** Count escape days in a period. */
    @Query("SELECT COUNT(*) FROM behavior_snapshots WHERE dayTimestamp >= :since AND escapeBehaviorScore > 0.5")
    int getEscapeDayCount(long since);

    // ─────────────────────────────────────────────────────────────────────
    // AGGREGATE — For baseline & overview
    // ─────────────────────────────────────────────────────────────────────

    /** Average overall risk over last N days. */
    @Query("SELECT AVG(overallBehaviorRiskScore) FROM behavior_snapshots ORDER BY dayTimestamp DESC LIMIT :days")
    float getAvgRiskScore(int days);

    /** Total tracked days. */
    @Query("SELECT COUNT(*) FROM behavior_snapshots")
    int getTotalSnapshotCount();

    /** Day with the worst risk score ever. */
    @Query("SELECT * FROM behavior_snapshots ORDER BY overallBehaviorRiskScore DESC LIMIT 1")
    BehaviorSnapshotEntity getWorstDay();

    /** Day with the best risk score ever. */
    @Query("SELECT * FROM behavior_snapshots WHERE overallBehaviorRiskScore > 0 ORDER BY overallBehaviorRiskScore ASC LIMIT 1")
    BehaviorSnapshotEntity getBestDay();

    /** Delete snapshots older than a timestamp. */
    @Query("DELETE FROM behavior_snapshots WHERE dayTimestamp < :before")
    int deleteOlderThan(long before);
}
