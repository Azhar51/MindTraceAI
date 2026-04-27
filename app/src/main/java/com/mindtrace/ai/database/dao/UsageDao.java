package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.mindtrace.ai.database.entity.DailyUsage;

import java.util.List;

/**
 * Data Access Object for the {@code daily_usage} table.
 *
 * <p>Provides reactive (LiveData) and synchronous access to daily usage data.
 * Organized by purpose: CRUD, temporal queries, analytics, and AI pipeline support.</p>
 *
 * <h3>Usage Pattern:</h3>
 * <ul>
 *   <li>UI observation: Use {@code LiveData} variants (reactive, main-thread safe)</li>
 *   <li>Background processing: Use {@code List} variants (synchronous, worker/executor only)</li>
 *   <li>AI pipeline: Use aggregate and analytical queries</li>
 * </ul>
 *
 * @see DailyUsage
 */
@Dao
public interface UsageDao {

    // ─────────────────────────────────────────────────────────────────────
    // CRUD — Basic operations
    // ─────────────────────────────────────────────────────────────────────

    /** Insert a new daily usage record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DailyUsage usage);

    /** Insert or replace — safe for idempotent re-processing of the same day. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrReplace(DailyUsage usage);

    /** Update an existing record (matched by primary key). */
    @Update
    void update(DailyUsage usage);

    /** Delete all usage data (used for privacy "clear all" in Settings). */
    @Query("DELETE FROM daily_usage")
    void deleteAll();

    // ─────────────────────────────────────────────────────────────────────
    // SINGLE-DAY LOOKUPS
    // ─────────────────────────────────────────────────────────────────────

    /** Get usage for a specific day by its midnight timestamp. */
    @Query("SELECT * FROM daily_usage WHERE date = :dayTimestamp LIMIT 1")
    DailyUsage getUsageForDay(long dayTimestamp);

    /** Reactive version — observes changes for a specific day (live dashboard updates). */
    @Query("SELECT * FROM daily_usage WHERE date = :dayTimestamp LIMIT 1")
    LiveData<DailyUsage> observeUsageForDay(long dayTimestamp);

    /** Get the most recent day's data (today or last recorded day). */
    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT 1")
    DailyUsage getLatestUsage();

    /** Reactive version of latest usage. */
    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT 1")
    LiveData<DailyUsage> observeLatestUsage();

    // ─────────────────────────────────────────────────────────────────────
    // HISTORY — Multi-day queries for trends and baselines
    // ─────────────────────────────────────────────────────────────────────

    /** Get all usage data, newest first (for full export). */
    @Query("SELECT * FROM daily_usage ORDER BY date DESC")
    LiveData<List<DailyUsage>> observeAllUsage();

    /** Get usage since a given timestamp (e.g., last 7 days). Background thread only. */
    @Query("SELECT * FROM daily_usage WHERE date >= :since ORDER BY date DESC")
    List<DailyUsage> getUsageSince(long since);

    /** Reactive version — observe usage since a given timestamp. */
    @Query("SELECT * FROM daily_usage WHERE date >= :since ORDER BY date DESC")
    LiveData<List<DailyUsage>> observeUsageSince(long since);

    /** Get usage between two dates (inclusive). */
    @Query("SELECT * FROM daily_usage WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    List<DailyUsage> getUsageBetween(long start, long end);

    /** Get the last N days of usage data (for trend computation). */
    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT :limit")
    List<DailyUsage> getRecentUsage(int limit);

    /** Reactive version — observe last N days. */
    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT :limit")
    LiveData<List<DailyUsage>> observeRecentUsage(int limit);

    // ─────────────────────────────────────────────────────────────────────
    // AGGREGATE ANALYTICS — For baseline and trend computation
    // ─────────────────────────────────────────────────────────────────────

    /** Average screen time over the last N days (for baseline). */
    @Query("SELECT AVG(screenTimeMillis) FROM daily_usage ORDER BY date DESC LIMIT :days")
    double getAvgScreenTime(int days);

    /** Average unlock count over the last N days. */
    @Query("SELECT AVG(unlockCount) FROM daily_usage ORDER BY date DESC LIMIT :days")
    double getAvgUnlockCount(int days);

    /** Average app switch count over the last N days. */
    @Query("SELECT AVG(totalAppSwitchCount) FROM daily_usage ORDER BY date DESC LIMIT :days")
    double getAvgAppSwitchCount(int days);

    /** Average night usage over the last N days. */
    @Query("SELECT AVG(nightUsageMillis) FROM daily_usage ORDER BY date DESC LIMIT :days")
    double getAvgNightUsage(int days);

    /** Average passive consumption ratio over the last N days. */
    @Query("SELECT AVG(passiveConsumptionRatio) FROM daily_usage ORDER BY date DESC LIMIT :days")
    double getAvgPassiveRatio(int days);

    /** Maximum screen time in the last N days (peak detection). */
    @Query("SELECT MAX(screenTimeMillis) FROM daily_usage ORDER BY date DESC LIMIT :days")
    long getMaxScreenTime(int days);

    /** Minimum screen time in the last N days (drop detection). */
    @Query("SELECT MIN(screenTimeMillis) FROM daily_usage WHERE screenTimeMillis > 0 ORDER BY date DESC LIMIT :days")
    long getMinScreenTime(int days);

    /** Total days of tracked data (for maturity/confidence assessment). */
    @Query("SELECT COUNT(*) FROM daily_usage WHERE screenTimeMillis > 0")
    int getTotalTrackedDays();

    // ─────────────────────────────────────────────────────────────────────
    // PATTERN DETECTION — For anomaly and crisis signals
    // ─────────────────────────────────────────────────────────────────────

    /** Get days where night usage exceeded a threshold (sleep disruption pattern). */
    @Query("SELECT * FROM daily_usage WHERE nightUsageMillis > :thresholdMs ORDER BY date DESC LIMIT :limit")
    List<DailyUsage> getDaysWithHighNightUsage(long thresholdMs, int limit);

    /** Get days where unlock count exceeded a threshold (compulsive checking). */
    @Query("SELECT * FROM daily_usage WHERE unlockCount > :threshold ORDER BY date DESC LIMIT :limit")
    List<DailyUsage> getDaysWithHighUnlocks(int threshold, int limit);

    /** Get days where screen time exceeded a threshold (binge days). */
    @Query("SELECT * FROM daily_usage WHERE screenTimeMillis > :thresholdMs ORDER BY date DESC LIMIT :limit")
    List<DailyUsage> getDaysWithHighScreenTime(long thresholdMs, int limit);

    /** Get days where passive consumption ratio exceeded a threshold. */
    @Query("SELECT * FROM daily_usage WHERE passiveConsumptionRatio > :threshold ORDER BY date DESC LIMIT :limit")
    List<DailyUsage> getDaysWithHighPassiveRatio(float threshold, int limit);

    /** Get the day with the highest screen time ever (for "worst day" insights). */
    @Query("SELECT * FROM daily_usage ORDER BY screenTimeMillis DESC LIMIT 1")
    DailyUsage getPeakUsageDay();

    /** Get the day with the lowest screen time (for "best day" insights). */
    @Query("SELECT * FROM daily_usage WHERE screenTimeMillis > 300000 ORDER BY screenTimeMillis ASC LIMIT 1")
    DailyUsage getBestUsageDay();

    // ─────────────────────────────────────────────────────────────────────
    // CATEGORY & TIME-OF-DAY — For consumption analysis
    // ─────────────────────────────────────────────────────────────────────

    /** Average social media time over the last N days. */
    @Query("SELECT AVG(socialMediaTimeMillis) FROM daily_usage ORDER BY date DESC LIMIT :days")
    double getAvgSocialMediaTime(int days);

    /** Average entertainment time over the last N days. */
    @Query("SELECT AVG(entertainmentTimeMillis) FROM daily_usage ORDER BY date DESC LIMIT :days")
    double getAvgEntertainmentTime(int days);

    /** Average productive time over the last N days. */
    @Query("SELECT AVG(productiveTimeMillis) FROM daily_usage ORDER BY date DESC LIMIT :days")
    double getAvgProductiveTime(int days);

    /** Screen time trend data for charting. */
    @Query("SELECT * FROM daily_usage WHERE date >= :since ORDER BY date ASC")
    List<DailyUsage> getScreenTimeTrend(long since);

    // ─────────────────────────────────────────────────────────────────────
    // SLEEP PROXY — First unlock / last screen off analysis
    // ─────────────────────────────────────────────────────────────────────

    /** Average first unlock time over the last N days (wake-up pattern). */
    @Query("SELECT AVG(firstUnlockTime) FROM daily_usage WHERE firstUnlockTime > 0 ORDER BY date DESC LIMIT :days")
    double getAvgFirstUnlockTime(int days);

    /** Average last screen off time over the last N days (bedtime pattern). */
    @Query("SELECT AVG(lastScreenOffTime) FROM daily_usage WHERE lastScreenOffTime > 0 ORDER BY date DESC LIMIT :days")
    double getAvgLastScreenOffTime(int days);

    /** Check if any data exists for a specific day. */
    @Query("SELECT COUNT(*) FROM daily_usage WHERE date = :dayTimestamp")
    int countForDay(long dayTimestamp);
}
