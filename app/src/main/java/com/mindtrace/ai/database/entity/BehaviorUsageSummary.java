package com.mindtrace.ai.database.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "behavior_usage_summaries",
        indices = {
                @Index(value = {"dayTimestamp"}, unique = true),
                @Index(value = {"createdAt"})
        }
)
public class BehaviorUsageSummary {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long dayTimestamp;
    public long totalUsage;
    public int fragmentedUsageScore;
    public int bingeScore;
    public int switchScore;
    public int topAppDominanceScore;
    public int lateNightPenaltyScore;
    public int distractionPatternScore;
    public String summaryLabel;
    public String explanatoryNotes;
    public long createdAt;
}
