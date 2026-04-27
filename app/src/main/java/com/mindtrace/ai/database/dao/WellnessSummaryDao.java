package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.WellnessSummary;

import java.util.List;

@Dao
public interface WellnessSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(WellnessSummary summary);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WellnessSummary summary);

    @Query("SELECT * FROM wellness_summaries ORDER BY dayTimestamp DESC")
    LiveData<List<WellnessSummary>> getAllSummaries();

    @Query("SELECT * FROM wellness_summaries ORDER BY dayTimestamp DESC LIMIT 1")
    LiveData<WellnessSummary> getLatestSummary();

    @Query("SELECT * FROM wellness_summaries WHERE dayTimestamp >= :since ORDER BY dayTimestamp DESC")
    List<WellnessSummary> getSummariesSinceSync(long since);

    @Query("SELECT * FROM wellness_summaries WHERE dayTimestamp = :dayStart LIMIT 1")
    WellnessSummary getForDay(long dayStart);

    @Query("SELECT * FROM wellness_summaries ORDER BY dayTimestamp DESC LIMIT :limit")
    List<WellnessSummary> getRecentSync(int limit);
}
