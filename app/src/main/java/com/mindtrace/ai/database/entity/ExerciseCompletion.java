package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Tracks completion of breathing and grounding exercises for trend analysis.
 * Used to measure: "Box breathing reduces your distress by avg 2 points."
 */
@Entity(
        tableName = "exercise_completions",
        indices = {
                @Index(value = {"completedAt"}),
                @Index(value = {"exerciseType"})
        }
)
public class ExerciseCompletion {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Exercise type: "breathing_4_4_6", "breathing_4_7_8", "breathing_box",
     *  "breathing_quick", "grounding_54321", "grounding_body_scan",
     *  "grounding_muscle_relaxation" */
    @NonNull
    public String exerciseType = "";

    /** Exercise name for display. */
    @NonNull
    public String exerciseName = "";

    public long completedAt;

    /** Duration the user actually spent in ms. */
    @ColumnInfo(defaultValue = "0")
    public long durationMs;

    /** Self-reported distress level before exercise (1-10). */
    @ColumnInfo(defaultValue = "0")
    public int preDistressLevel;

    /** Self-reported distress level after exercise (1-10). */
    @ColumnInfo(defaultValue = "0")
    public int postDistressLevel;

    /** Whether the exercise was completed fully or exited early. */
    @ColumnInfo(defaultValue = "1")
    public boolean completedFully;

    // ─────────────────────────────────────────────────────────────────────
    // CONVENIENCE
    // ─────────────────────────────────────────────────────────────────────

    /** Get distress reduction (positive = improvement). */
    public int getDistressReduction() {
        if (preDistressLevel <= 0 || postDistressLevel <= 0) return 0;
        return preDistressLevel - postDistressLevel;
    }

    /** Whether the exercise was effective. */
    public boolean wasEffective() {
        return getDistressReduction() > 0;
    }
}
