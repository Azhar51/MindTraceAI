package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.mindtrace.ai.database.entity.CrisisEvent;

import java.util.List;

/**
 * DAO for {@link CrisisEvent} — crisis history and safety analytics.
 */
@Dao
public interface CrisisEventDao {

    @Insert
    long insert(CrisisEvent event);

    @Update
    void update(CrisisEvent event);

    @Query("SELECT * FROM crisis_events ORDER BY timestamp DESC")
    LiveData<List<CrisisEvent>> getAllEvents();

    @Query("SELECT * FROM crisis_events ORDER BY timestamp DESC LIMIT :limit")
    List<CrisisEvent> getRecentEvents(int limit);

    @Query("SELECT * FROM crisis_events ORDER BY createdAt DESC LIMIT :limit")
    List<CrisisEvent> getRecentSync(int limit);

    @Query("SELECT * FROM crisis_events WHERE status = 'ACTIVE' ORDER BY timestamp DESC LIMIT 1")
    CrisisEvent getActiveEvent();

    @Query("SELECT * FROM crisis_events WHERE status = 'ACTIVE'")
    List<CrisisEvent> getUnresolvedEvents();

    @Query("SELECT COUNT(*) FROM crisis_events WHERE timestamp >= :since")
    int getCountSince(long since);

    @Query("SELECT COUNT(*) FROM crisis_events WHERE crisisLevel = :level AND timestamp >= :since")
    int getCountForLevelSince(String level, long since);

    @Query("SELECT AVG(resolvedAt - timestamp) FROM crisis_events WHERE resolvedAt > 0 AND timestamp >= :since")
    long getAverageResolutionTimeMs(long since);

    @Query("SELECT * FROM crisis_events WHERE resolvedAt > 0 AND debriefCompleted = 0 AND resolvedAt < :before ORDER BY resolvedAt DESC LIMIT 1")
    CrisisEvent getEventNeedingDebrief(long before);

    @Query("SELECT AVG(preDistressLevel - postDistressLevel) FROM crisis_events WHERE preDistressLevel > 0 AND postDistressLevel > 0")
    float getAverageDistressReduction();

    @Query("SELECT resolutionMethod FROM crisis_events WHERE resolvedAt > 0 GROUP BY resolutionMethod ORDER BY COUNT(*) DESC LIMIT 1")
    String getMostUsedResolutionMethod();

    @Query("SELECT * FROM crisis_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    List<CrisisEvent> getEventsSince(long since);

    @Query("SELECT * FROM crisis_events ORDER BY timestamp DESC")
    List<CrisisEvent> getAllEventsSync();

    @Query("SELECT COUNT(*) FROM crisis_events")
    int getTotalCountSync();

}
