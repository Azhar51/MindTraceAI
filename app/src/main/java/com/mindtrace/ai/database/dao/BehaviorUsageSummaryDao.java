package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.BehaviorUsageSummary;

import java.util.List;

@Dao
public interface BehaviorUsageSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(BehaviorUsageSummary summary);

    @Query("SELECT * FROM behavior_usage_summaries WHERE dayTimestamp = :dayTimestamp LIMIT 1")
    LiveData<BehaviorUsageSummary> getSummaryForDay(long dayTimestamp);

    @Query("SELECT * FROM behavior_usage_summaries WHERE dayTimestamp = :dayTimestamp LIMIT 1")
    BehaviorUsageSummary getSummaryForDaySync(long dayTimestamp);

    @Query("SELECT * FROM behavior_usage_summaries ORDER BY dayTimestamp DESC LIMIT 1")
    LiveData<BehaviorUsageSummary> getLatestSummary();

    @Query("SELECT * FROM behavior_usage_summaries WHERE dayTimestamp >= :since ORDER BY dayTimestamp DESC")
    LiveData<List<BehaviorUsageSummary>> getSummariesSince(long since);

    @Query("SELECT * FROM behavior_usage_summaries WHERE dayTimestamp BETWEEN :start AND :end ORDER BY dayTimestamp DESC")
    List<BehaviorUsageSummary> getSummariesBetweenSync(long start, long end);
}
