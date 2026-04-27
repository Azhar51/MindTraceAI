package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.AppUsageSnapshot;

import java.util.List;

/**
 * Data Access Object for the {@code app_usage_snapshots} table.
 *
 * <p>Provides per-app, per-day usage data with category-aware queries
 * for the Usage Dashboard, AI feature extraction, and trap detection.</p>
 *
 * @see AppUsageSnapshot
 */
@Dao
public interface AppUsageSnapshotDao {

    // ─────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────

    /** Insert or replace a single snapshot (unique on dayTimestamp + packageName). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(AppUsageSnapshot snapshot);

    /** Bulk insert/replace all snapshots for a day. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AppUsageSnapshot> snapshots);

    /** Delete all snapshots for a specific day (before re-inserting fresh data). */
    @Query("DELETE FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp")
    void deleteForDay(long dayTimestamp);

    /** Delete all data (privacy clear). */
    @Query("DELETE FROM app_usage_snapshots")
    void deleteAll();

    /** Delete old snapshots (for data cleanup worker). */
    @Query("DELETE FROM app_usage_snapshots WHERE dayTimestamp < :before")
    int deleteOlderThan(long before);

    // ─────────────────────────────────────────────────────────────────────
    // PER-DAY LOOKUPS — For dashboard display
    // ─────────────────────────────────────────────────────────────────────

    /** Get all app snapshots for a day, sorted by usage time (highest first). */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp ORDER BY usageTimeMillis DESC")
    LiveData<List<AppUsageSnapshot>> observeSnapshotsForDay(long dayTimestamp);

    /** Synchronous version — for background processing. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp ORDER BY usageTimeMillis DESC")
    List<AppUsageSnapshot> getSnapshotsForDaySync(long dayTimestamp);

    /** Get only user-visible apps for a day (filtered for display). */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND isUserVisible = 1 AND usageTimeMillis > 60000 ORDER BY usageTimeMillis DESC")
    LiveData<List<AppUsageSnapshot>> observeVisibleAppsForDay(long dayTimestamp);

    /** Get a specific app's snapshot for a specific day. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND packageName = :packageName LIMIT 1")
    AppUsageSnapshot getAppForDay(long dayTimestamp, String packageName);

    /** Get top N apps for a day by usage time. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND isUserVisible = 1 ORDER BY usageTimeMillis DESC LIMIT :limit")
    List<AppUsageSnapshot> getTopAppsForDay(long dayTimestamp, int limit);

    // ─────────────────────────────────────────────────────────────────────
    // CATEGORY QUERIES — For consumption pattern analysis
    // ─────────────────────────────────────────────────────────────────────

    /** Get all apps of a specific category for a day. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND appCategory = :category ORDER BY usageTimeMillis DESC")
    List<AppUsageSnapshot> getAppsByCategory(long dayTimestamp, String category);

    /** Get total time spent in a specific category for a day. */
    @Query("SELECT COALESCE(SUM(usageTimeMillis), 0) FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND appCategory = :category")
    long getCategoryTimeForDay(long dayTimestamp, String category);

    /** Get total time in all passive apps for a day. */
    @Query("SELECT COALESCE(SUM(usageTimeMillis), 0) FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND isPassiveApp = 1")
    long getPassiveTimeForDay(long dayTimestamp);

    /** Get total time in all non-passive (productive) apps for a day. */
    @Query("SELECT COALESCE(SUM(usageTimeMillis), 0) FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND isPassiveApp = 0 AND isSystemApp = 0")
    long getProductiveTimeForDay(long dayTimestamp);

    /** Count distinct categories used in a day. */
    @Query("SELECT COUNT(DISTINCT appCategory) FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND appCategory IS NOT NULL")
    int getDistinctCategoryCount(long dayTimestamp);

    // ─────────────────────────────────────────────────────────────────────
    // BINGE & TRAP DETECTION — For AI risk signals
    // ─────────────────────────────────────────────────────────────────────

    /** Get all apps that had binge sessions today. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND bingeFlag = 1 ORDER BY usageTimeMillis DESC")
    List<AppUsageSnapshot> getBingeAppsForDay(long dayTimestamp);

    /** Count apps with binge sessions for a day. */
    @Query("SELECT COUNT(*) FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND bingeFlag = 1")
    int getBingeAppCount(long dayTimestamp);

    /** Get total binge session count across all apps for a day. */
    @Query("SELECT COALESCE(SUM(bingeSessionCount), 0) FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp")
    int getTotalBingeSessionCount(long dayTimestamp);

    /** Get "trap apps" — passive + binge + >20% of total usage. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND isPassiveApp = 1 AND bingeFlag = 1 AND usagePercentage >= 20 ORDER BY usageTimeMillis DESC")
    List<AppUsageSnapshot> getTrapAppsForDay(long dayTimestamp);

    /** Get apps with highest night usage percentage. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND nightUsagePercent > 0 ORDER BY nightUsagePercent DESC LIMIT :limit")
    List<AppUsageSnapshot> getNightAppsForDay(long dayTimestamp, int limit);

    // ─────────────────────────────────────────────────────────────────────
    // HISTORY & TRENDS — Multi-day queries
    // ─────────────────────────────────────────────────────────────────────

    /** Get all snapshots since a timestamp (for export/trend). */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp >= :since ORDER BY dayTimestamp DESC, usageTimeMillis DESC")
    LiveData<List<AppUsageSnapshot>> observeSnapshotsSince(long since);

    /** Synchronous version. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp >= :since ORDER BY dayTimestamp DESC, usageTimeMillis DESC")
    List<AppUsageSnapshot> getSnapshotsSinceSync(long since);

    /** Get snapshots between dates. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp BETWEEN :start AND :end ORDER BY dayTimestamp DESC, usageTimeMillis DESC")
    List<AppUsageSnapshot> getSnapshotsBetweenSync(long start, long end);

    /** Get a specific app's usage history across multiple days (for per-app trend). */
    @Query("SELECT * FROM app_usage_snapshots WHERE packageName = :packageName AND dayTimestamp >= :since ORDER BY dayTimestamp ASC")
    List<AppUsageSnapshot> getAppHistory(String packageName, long since);

    /** Average daily usage of a specific app over last N days. */
    @Query("SELECT AVG(usageTimeMillis) FROM app_usage_snapshots WHERE packageName = :packageName ORDER BY dayTimestamp DESC LIMIT :days")
    long getAvgAppUsage(String packageName, int days);

    /** Get the single most used app across all tracked days. */
    @Query("SELECT packageName FROM app_usage_snapshots GROUP BY packageName ORDER BY SUM(usageTimeMillis) DESC LIMIT 1")
    String getMostUsedAppAllTime();

    // ─────────────────────────────────────────────────────────────────────
    // AGGREGATE — For dashboard cards
    // ─────────────────────────────────────────────────────────────────────

    /** Count distinct apps tracked for a day. */
    @Query("SELECT COUNT(*) FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp")
    int getAppCountForDay(long dayTimestamp);

    /** Count passive apps for a day. */
    @Query("SELECT COUNT(*) FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp AND isPassiveApp = 1 AND usageTimeMillis > 60000")
    int getPassiveAppCount(long dayTimestamp);

    /** Get the dominant app (highest usage) for a day. */
    @Query("SELECT * FROM app_usage_snapshots WHERE dayTimestamp = :dayTimestamp ORDER BY usageTimeMillis DESC LIMIT 1")
    AppUsageSnapshot getDominantApp(long dayTimestamp);
}
