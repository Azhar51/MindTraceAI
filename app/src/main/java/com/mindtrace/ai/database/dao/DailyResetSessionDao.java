package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.DailyResetSession;

@Dao
public interface DailyResetSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(DailyResetSession session);

    @Query("SELECT * FROM daily_reset_sessions WHERE dayTimestamp = :dayTimestamp LIMIT 1")
    LiveData<DailyResetSession> getSessionForDay(long dayTimestamp);

    @Query("SELECT * FROM daily_reset_sessions WHERE dayTimestamp = :dayTimestamp LIMIT 1")
    DailyResetSession getSessionForDaySync(long dayTimestamp);
}
