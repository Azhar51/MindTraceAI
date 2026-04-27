package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.mindtrace.ai.database.entity.UserProgress;

/**
 * DAO for {@link UserProgress} — singleton gamification state.
 */
@Dao
public interface UserProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(UserProgress progress);

    @Update
    void update(UserProgress progress);

    @Query("SELECT * FROM user_progress WHERE id = 1 LIMIT 1")
    UserProgress getProgress();

    @Query("SELECT * FROM user_progress WHERE id = 1 LIMIT 1")
    LiveData<UserProgress> getProgressLive();

    @Query("SELECT totalXp FROM user_progress WHERE id = 1")
    int getTotalXp();

    @Query("SELECT currentStreak FROM user_progress WHERE id = 1")
    int getCurrentStreak();
}
