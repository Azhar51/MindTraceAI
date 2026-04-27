package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.SuicideRiskEvent;

import java.util.List;

/**
 * DAO for {@link SuicideRiskEvent} — suicide risk assessment history.
 */
@Dao
public interface SuicideRiskEventDao {

    @Insert
    long insert(SuicideRiskEvent event);

    /** All events ordered by recency. */
    @Query("SELECT * FROM suicide_risk_events ORDER BY timestamp DESC")
    LiveData<List<SuicideRiskEvent>> getAllEvents();

    /** Recent events (sync). */
    @Query("SELECT * FROM suicide_risk_events ORDER BY timestamp DESC LIMIT :limit")
    List<SuicideRiskEvent> getRecentSync(int limit);

    /** Events since a timestamp (for weekly/monthly reports). */
    @Query("SELECT * FROM suicide_risk_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    List<SuicideRiskEvent> getEventsSince(long since);

    /** Count of events at or above a given C-SSRS tier. */
    @Query("SELECT COUNT(*) FROM suicide_risk_events WHERE csrrsTier >= :minTier AND timestamp >= :since")
    int getCountAtTierOrAbove(int minTier, long since);

    /** Highest C-SSRS tier recorded in a time period. */
    @Query("SELECT MAX(csrrsTier) FROM suicide_risk_events WHERE timestamp >= :since")
    int getHighestTierSince(long since);

    /** Count of lockdowns triggered. */
    @Query("SELECT COUNT(*) FROM suicide_risk_events WHERE lockdownTriggered = 1 AND timestamp >= :since")
    int getLockdownCountSince(long since);

    /** Average C-SSRS tier over time. */
    @Query("SELECT AVG(csrrsTier) FROM suicide_risk_events WHERE timestamp >= :since AND csrrsTier > 0")
    float getAverageTierSince(long since);

    /** Get events linked to a specific crisis event. */
    @Query("SELECT * FROM suicide_risk_events WHERE linkedCrisisEventId = :crisisEventId ORDER BY timestamp DESC")
    List<SuicideRiskEvent> getEventsForCrisis(long crisisEventId);

    /** Total count. */
    @Query("SELECT COUNT(*) FROM suicide_risk_events")
    int getTotalCount();
}
