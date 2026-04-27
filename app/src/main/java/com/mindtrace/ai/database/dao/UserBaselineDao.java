package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.UserBaseline;

@Dao
public interface UserBaselineDao {
    @Query("SELECT * FROM user_baseline WHERE id = 1 LIMIT 1")
    LiveData<UserBaseline> getBaseline();

    @Query("SELECT * FROM user_baseline WHERE id = 1 LIMIT 1")
    UserBaseline getBaselineSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateBaseline(UserBaseline baseline);
}
