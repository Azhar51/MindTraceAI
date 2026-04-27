package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.ExerciseCompletion;

import java.util.List;

/**
 * DAO for {@link ExerciseCompletion} — tracks breathing/grounding exercise effectiveness.
 */
@Dao
public interface ExerciseCompletionDao {

    @Insert
    void insert(ExerciseCompletion completion);

    @Query("SELECT * FROM exercise_completions ORDER BY completedAt DESC LIMIT :limit")
    List<ExerciseCompletion> getRecent(int limit);

    @Query("SELECT * FROM exercise_completions ORDER BY completedAt DESC")
    LiveData<List<ExerciseCompletion>> getAll();

    @Query("SELECT AVG(preDistressLevel - postDistressLevel) FROM exercise_completions " +
            "WHERE preDistressLevel > 0 AND postDistressLevel > 0 AND exerciseType = :type")
    float getAvgDistressReductionByType(String type);

    @Query("SELECT AVG(preDistressLevel - postDistressLevel) FROM exercise_completions " +
            "WHERE preDistressLevel > 0 AND postDistressLevel > 0 AND exerciseName = :name")
    float getAvgDistressReductionByName(String name);

    @Query("SELECT COUNT(*) FROM exercise_completions WHERE completedAt > :since")
    int getCountSince(long since);

    @Query("SELECT exerciseName FROM exercise_completions " +
            "GROUP BY exerciseName ORDER BY COUNT(*) DESC LIMIT 1")
    String getMostUsedExercise();

    @Query("SELECT exerciseName FROM exercise_completions " +
            "WHERE preDistressLevel > 0 AND postDistressLevel > 0 " +
            "GROUP BY exerciseName " +
            "ORDER BY AVG(preDistressLevel - postDistressLevel) DESC LIMIT 1")
    String getMostEffectiveExercise();
}
