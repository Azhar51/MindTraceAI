package com.mindtrace.ai.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_reset_sessions")
public class DailyResetSession {
    @PrimaryKey
    public long dayTimestamp;

    public long createdAt;
    public long startedAt;
    public String resetTitle;
    public String focusTask;
    public String firstAction;
    public String warningItem;
    public int timerDurationMinutes;
    public boolean isCompleted;
    public long completedAt;
    public int readinessLevel;
    public String reflectionNote;
}
