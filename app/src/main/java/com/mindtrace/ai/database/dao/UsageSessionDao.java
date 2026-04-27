package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.UsageSession;

import java.util.List;

/**
 * Data Access Object for the {@code usage_sessions} table.
 *
 * <p>Provides granular session-level queries for behaviour analysis —
 * switching patterns, binge detection, loop identification, and
 * time-of-day analytics.</p>
 *
 * @see UsageSession
 */
@Dao
public interface UsageSessionDao {

    // ─────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────

    @Insert
    void insert(UsageSession session);

    @Insert
    void insertAll(List<UsageSession> sessions);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(UsageSession session);

    @Query("DELETE FROM usage_sessions WHERE dayTimestamp = :dayTimestamp")
    void deleteForDay(long dayTimestamp);

    @Query("DELETE FROM usage_sessions")
    void deleteAll();

    // ─────────────────────────────────────────────────────────────────────
    // PER-DAY — All sessions for a day
    // ─────────────────────────────────────────────────────────────────────

    /** Get all sessions for a day, chronologically. */
    @Query("SELECT * FROM usage_sessions WHERE dayTimestamp = :dayTimestamp ORDER BY sessionStart ASC")
    LiveData<List<UsageSession>> observeSessionsForDay(long dayTimestamp);

    /** Synchronous — for background processing by BehaviorAnalyzer. */
    @Query("SELECT * FROM usage_sessions WHERE dayTimestamp = :dayTimestamp ORDER BY sessionStart ASC")
    List<UsageSession> getSessionsForDaySync(long dayTimestamp);

    /** Get sessions for a specific app on a specific day. */
    @Query("SELECT * FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND packageName = :packageName ORDER BY sessionStart ASC")
    List<UsageSession> getSessionsForApp(long dayTimestamp, String packageName);

    // ─────────────────────────────────────────────────────────────────────
    // SESSION TYPE — Passive vs active analysis
    // ─────────────────────────────────────────────────────────────────────

    /** Get all passive sessions for a day. */
    @Query("SELECT * FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND sessionType = 'passive' ORDER BY sessionStart ASC")
    List<UsageSession> getPassiveSessionsForDay(long dayTimestamp);

    /** Count passive sessions for a day. */
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND sessionType = 'passive'")
    int getPassiveSessionCount(long dayTimestamp);

    /** Total passive session time for a day. */
    @Query("SELECT COALESCE(SUM(durationMillis), 0) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND sessionType = 'passive'")
    long getPassiveSessionTime(long dayTimestamp);

    /** Total active session time for a day. */
    @Query("SELECT COALESCE(SUM(durationMillis), 0) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND sessionType = 'active'")
    long getActiveSessionTime(long dayTimestamp);

    // ─────────────────────────────────────────────────────────────────────
    // DURATION CATEGORY — Session size analysis
    // ─────────────────────────────────────────────────────────────────────

    /** Count micro sessions (< 30s) — reflexive checks. */
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND durationCategory = 'micro'")
    int getMicroSessionCount(long dayTimestamp);

    /** Count short sessions (30s–2min) — quick checks. */
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND durationCategory = 'short'")
    int getShortSessionCount(long dayTimestamp);

    /** Count binge sessions (> 30min) — dopamine loops. */
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND durationCategory = 'binge'")
    int getBingeSessionCount(long dayTimestamp);

    /** Get all binge sessions for a day with app details. */
    @Query("SELECT * FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND durationCategory = 'binge' ORDER BY durationMillis DESC")
    List<UsageSession> getBingeSessionsForDay(long dayTimestamp);

    /** Get the longest session of the day. */
    @Query("SELECT * FROM usage_sessions WHERE dayTimestamp = :dayTimestamp ORDER BY durationMillis DESC LIMIT 1")
    UsageSession getLongestSession(long dayTimestamp);

    /** Average session duration for a day (ms). */
    @Query("SELECT AVG(durationMillis) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp")
    long getAvgSessionDuration(long dayTimestamp);

    // ─────────────────────────────────────────────────────────────────────
    // NIGHT & TIME-OF-DAY — Sleep disruption analysis
    // ─────────────────────────────────────────────────────────────────────

    /** Get all late-night sessions for a day. */
    @Query("SELECT * FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND wasLateNightSession = 1 ORDER BY sessionStart ASC")
    List<UsageSession> getLateNightSessions(long dayTimestamp);

    /** Count late-night sessions. */
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND wasLateNightSession = 1")
    int getLateNightSessionCount(long dayTimestamp);

    /** Total late-night session time (ms). */
    @Query("SELECT COALESCE(SUM(durationMillis), 0) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND wasLateNightSession = 1")
    long getLateNightSessionTime(long dayTimestamp);

    // ─────────────────────────────────────────────────────────────────────
    // SWITCHING PATTERNS — Attention fragmentation
    // ─────────────────────────────────────────────────────────────────────

    /** Total session count for a day (proxy for switch count). */
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp")
    int getSessionCount(long dayTimestamp);

    /** Count sessions with interruptions (multitasking indicator). */
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND interruptionCount > 0")
    int getInterruptedSessionCount(long dayTimestamp);

    /** Total interruptions across all sessions for a day. */
    @Query("SELECT COALESCE(SUM(interruptionCount), 0) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp")
    int getTotalInterruptions(long dayTimestamp);

    /** Get sessions that were notification-triggered (reactive usage). */
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND wasNotificationTriggered = 1")
    int getNotificationTriggeredCount(long dayTimestamp);

    // ─────────────────────────────────────────────────────────────────────
    // HISTORY — Multi-day queries
    // ─────────────────────────────────────────────────────────────────────

    /** Get sessions since a timestamp (for export/batch processing). */
    @Query("SELECT * FROM usage_sessions WHERE dayTimestamp >= :since ORDER BY dayTimestamp DESC, sessionStart ASC")
    List<UsageSession> getSessionsSinceSync(long since);

    /** Get a specific app's sessions across multiple days (per-app trend). */
    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND dayTimestamp >= :since ORDER BY dayTimestamp ASC, sessionStart ASC")
    List<UsageSession> getAppSessionHistory(String packageName, long since);

    /** Average binge session count per day over last N days. */
    @Query("SELECT AVG(cnt) FROM (SELECT COUNT(*) AS cnt FROM usage_sessions WHERE durationCategory = 'binge' GROUP BY dayTimestamp ORDER BY dayTimestamp DESC LIMIT :days)")
    float getAvgDailyBingeSessions(int days);

    /** Get distinct apps used in a time window within a day (for switching analysis). */
    @Query("SELECT DISTINCT packageName FROM usage_sessions WHERE dayTimestamp = :dayTimestamp AND sessionStart BETWEEN :windowStart AND :windowEnd")
    List<String> getAppsInTimeWindow(long dayTimestamp, long windowStart, long windowEnd);
}
