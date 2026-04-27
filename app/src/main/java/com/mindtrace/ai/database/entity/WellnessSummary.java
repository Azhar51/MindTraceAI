package com.mindtrace.ai.database.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "wellness_summaries",
        indices = {
                @Index(value = {"dayTimestamp"}, unique = true),
                @Index(value = {"createdAt"})
        }
)
public class WellnessSummary {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public long dayTimestamp;
    public long createdAt;
    public String wellnessState;
    public String riskLevel;
    public String explanationText;
    public String reasonSummary;
    public String nextBestAction;
    public long screenTimeMillis;
    public int taskCompletionScore;
    public String topAppName;
    public String topAppPackage;
    public boolean supportSuggested;
}
