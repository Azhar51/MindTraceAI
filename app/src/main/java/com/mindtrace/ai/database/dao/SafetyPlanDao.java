package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.mindtrace.ai.database.entity.SafetyPlan;

/**
 * DAO for {@link SafetyPlan} — singleton entity (id=1).
 */
@Dao
public interface SafetyPlanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(SafetyPlan plan);

    @Update
    void update(SafetyPlan plan);

    @Query("SELECT * FROM safety_plan WHERE id = 1 LIMIT 1")
    SafetyPlan getSync();

    @Query("SELECT * FROM safety_plan WHERE id = 1 LIMIT 1")
    LiveData<SafetyPlan> get();

    @Query("DELETE FROM safety_plan WHERE id = 1")
    void delete();
}
