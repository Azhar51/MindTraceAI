package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.mindtrace.ai.database.entity.JournalEntry;

import java.util.List;

/**
 * Data Access Object for the Journal system.
 *
 * <p>Provides 30+ queries across 6 categories:</p>
 * <ol>
 *   <li><b>CRUD</b> — insert, update, delete</li>
 *   <li><b>Lookup</b> — by day, by type, by range, latest</li>
 *   <li><b>Distress Intelligence</b> — distress entries, crisis detection</li>
 *   <li><b>Sentiment Analytics</b> — trends, distribution, polarity tracking</li>
 *   <li><b>Engagement Metrics</b> — consistency, word count, completion rates</li>
 *   <li><b>ML Feature Extraction</b> — sentiment trends, topic patterns</li>
 * </ol>
 */
@Dao
public interface JournalDao {

    // ═════════════════════════════════════════════════════════════════════
    // 1. CRUD
    // ═════════════════════════════════════════════════════════════════════

    @Insert
    void insert(JournalEntry entry);

    @Insert
    long insertAndReturnId(JournalEntry entry);

    @Update
    void update(JournalEntry entry);

    @Query("DELETE FROM journal_entries WHERE id = :id")
    void deleteById(int id);

    // ═════════════════════════════════════════════════════════════════════
    // 2. LOOKUP QUERIES
    // ═════════════════════════════════════════════════════════════════════

    /** All entries, newest first (LiveData). */
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    LiveData<List<JournalEntry>> getAllEntries();

    /** All entries, newest first (sync). */
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    List<JournalEntry> getAllEntriesSync();

    /** Recent N entries. */
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC LIMIT :limit")
    List<JournalEntry> getRecentEntries(int limit);

    /** Entries for a specific day. */
    @Query("SELECT * FROM journal_entries WHERE dayTimestamp = :dayTimestamp ORDER BY timestamp DESC")
    List<JournalEntry> getEntriesForDay(long dayTimestamp);

    /** Latest entry for a day. */
    @Query("SELECT * FROM journal_entries WHERE dayTimestamp = :dayTimestamp ORDER BY timestamp DESC LIMIT 1")
    JournalEntry getLatestEntryForDay(long dayTimestamp);

    /** Entries by type. (Task 2.C.5) */
    @Query("SELECT * FROM journal_entries WHERE entryType = :type ORDER BY timestamp DESC")
    List<JournalEntry> getEntriesByType(String type);

    /** Entries by type with limit. */
    @Query("SELECT * FROM journal_entries WHERE entryType = :type ORDER BY timestamp DESC LIMIT :limit")
    List<JournalEntry> getEntriesByType(String type, int limit);

    /** Entries in a date range. (Task 2.C.6) */
    @Query("SELECT * FROM journal_entries WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    List<JournalEntry> getEntriesInDateRange(long start, long end);

    /** Get entry by ID. */
    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    JournalEntry getEntryById(int id);

    /** Get entries linked to a specific check-in. */
    @Query("SELECT * FROM journal_entries WHERE linkedCheckInId = :checkInId ORDER BY timestamp DESC")
    List<JournalEntry> getEntriesForCheckIn(int checkInId);

    // ═════════════════════════════════════════════════════════════════════
    // 3. DISTRESS INTELLIGENCE
    // ═════════════════════════════════════════════════════════════════════

    /** Entries with non-null distress flags. (Task 2.C.3) */
    @Query("SELECT * FROM journal_entries WHERE distressFlags IS NOT NULL AND distressFlags != '' AND distressFlags != '[]' ORDER BY timestamp DESC")
    List<JournalEntry> getDistressEntries();

    /** Entries with high distress flag count. */
    @Query("SELECT * FROM journal_entries WHERE distressFlagCount >= :threshold ORDER BY timestamp DESC")
    List<JournalEntry> getHighDistressEntries(int threshold);

    /** Entries with cognitive distortions detected. */
    @Query("SELECT * FROM journal_entries WHERE cognitiveDistortions IS NOT NULL AND cognitiveDistortions != '' AND cognitiveDistortions != '[]' ORDER BY timestamp DESC LIMIT :limit")
    List<JournalEntry> getEntriesWithDistortions(int limit);

    /** Count of distress entries in a date range. */
    @Query("SELECT COUNT(*) FROM journal_entries WHERE distressFlagCount > 0 AND timestamp BETWEEN :start AND :end")
    int getDistressEntryCount(long start, long end);

    // ═════════════════════════════════════════════════════════════════════
    // 4. SENTIMENT ANALYTICS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Sentiment scores for trend analysis (oldest→newest). (Task 2.C.7)
     * Returns polarity scores for the last N entries.
     */
    @Query("SELECT sentimentScore FROM (" +
            "SELECT sentimentScore, timestamp FROM journal_entries " +
            "WHERE sentimentScore != 0 AND isComplete = 1 " +
            "ORDER BY timestamp DESC LIMIT :limit" +
            ") ORDER BY timestamp ASC")
    List<Float> getSentimentTrend(int limit);

    /** Average sentiment score over last N entries. */
    @Query("SELECT AVG(sentimentScore) FROM (" +
            "SELECT sentimentScore FROM journal_entries " +
            "WHERE sentimentScore != 0 AND isComplete = 1 " +
            "ORDER BY timestamp DESC LIMIT :limit)")
    float getAvgSentimentScore(int limit);

    /** Count of positive vs negative entries since a date. */
    @Query("SELECT COUNT(*) FROM journal_entries WHERE sentimentScore > 0.3 AND timestamp >= :since")
    int getPositiveEntryCount(long since);

    @Query("SELECT COUNT(*) FROM journal_entries WHERE sentimentScore < -0.3 AND timestamp >= :since")
    int getNegativeEntryCount(long since);

    /** Entries with very negative sentiment. */
    @Query("SELECT * FROM journal_entries WHERE aiSentiment = 'very_negative' ORDER BY timestamp DESC LIMIT :limit")
    List<JournalEntry> getVeryNegativeEntries(int limit);

    /** Distribution of sentiment categories. */
    @Query("SELECT aiSentiment, COUNT(*) as cnt FROM journal_entries " +
            "WHERE aiSentiment IS NOT NULL AND timestamp >= :since " +
            "GROUP BY aiSentiment ORDER BY cnt DESC")
    List<SentimentCount> getSentimentDistribution(long since);

    // ═════════════════════════════════════════════════════════════════════
    // 5. ENGAGEMENT METRICS
    // ═════════════════════════════════════════════════════════════════════

    /** Total distinct days with journal entries. (Task 2.C.4) */
    @Query("SELECT COUNT(DISTINCT dayTimestamp) FROM journal_entries WHERE dayTimestamp > 0")
    int getTotalJournalDays();

    /** Total number of entries. */
    @Query("SELECT COUNT(*) FROM journal_entries")
    int getTotalEntryCount();

    /** Total count alias for cross-module compat. */
    @Query("SELECT COUNT(*) FROM journal_entries")
    int getTotalCount();

    /** Average word count across entries. */
    @Query("SELECT AVG(wordCount) FROM journal_entries WHERE wordCount > 0 AND isComplete = 1")
    float getAvgWordCount();

    /** Average writing duration across entries (ms). */
    @Query("SELECT AVG(writingDurationMs) FROM journal_entries WHERE writingDurationMs > 0 AND isComplete = 1")
    long getAvgWritingDuration();

    /** Completion rate: completed / total. */
    @Query("SELECT COUNT(*) FROM journal_entries WHERE isComplete = 1")
    int getCompletedEntryCount();

    /** Count of entries by type since a date. */
    @Query("SELECT entryType, COUNT(*) as cnt FROM journal_entries " +
            "WHERE entryType IS NOT NULL AND timestamp >= :since " +
            "GROUP BY entryType ORDER BY cnt DESC")
    List<TypeCount> getEntryTypeDistribution(long since);

    /** Count of gratitude entries with 3+ items (the therapeutic threshold). */
    @Query("SELECT COUNT(*) FROM journal_entries WHERE entryType = 'gratitude' AND gratitudeItemCount >= 3 AND timestamp >= :since")
    int getTherapeuticGratitudeCount(long since);

    /** Count of action items that were completed. */
    @Query("SELECT COUNT(*) FROM journal_entries WHERE actionItemCompleted = 1 AND timestamp >= :since")
    int getCompletedActionItemCount(long since);

    // ═════════════════════════════════════════════════════════════════════
    // 6. ML FEATURE EXTRACTION
    // ═════════════════════════════════════════════════════════════════════

    /** Word counts for engagement trend (oldest→newest). */
    @Query("SELECT wordCount FROM (" +
            "SELECT wordCount, timestamp FROM journal_entries " +
            "WHERE wordCount > 0 AND isComplete = 1 " +
            "ORDER BY timestamp DESC LIMIT :limit" +
            ") ORDER BY timestamp ASC")
    List<Integer> getWordCountTrend(int limit);

    /** Distress flag counts for trend analysis (oldest→newest). */
    @Query("SELECT distressFlagCount FROM (" +
            "SELECT distressFlagCount, timestamp FROM journal_entries " +
            "WHERE isComplete = 1 " +
            "ORDER BY timestamp DESC LIMIT :limit" +
            ") ORDER BY timestamp ASC")
    List<Integer> getDistressTrend(int limit);

    /** Moods at time of writing for pattern analysis. */
    @Query("SELECT moodAtWriting FROM journal_entries " +
            "WHERE moodAtWriting IS NOT NULL " +
            "ORDER BY timestamp DESC LIMIT :limit")
    List<String> getWritingMoodHistory(int limit);

    /** Check-in entries for days user wrote — for correlation analysis. */
    @Query("SELECT DISTINCT dayTimestamp FROM journal_entries WHERE dayTimestamp > 0 AND timestamp >= :since")
    List<Long> getJournalDayTimestamps(long since);

    // ═════════════════════════════════════════════════════════════════════
    // RESULT TYPES
    // ═════════════════════════════════════════════════════════════════════

    /** Sentiment distribution result row. */
    class SentimentCount {
        public String aiSentiment;
        public int cnt;
    }

    /** Entry type distribution result row. */
    class TypeCount {
        public String entryType;
        public int cnt;
    }
}
