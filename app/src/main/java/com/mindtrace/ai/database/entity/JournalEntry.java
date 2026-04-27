package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Journal Entry — qualitative psychological data for the NLP pipeline.
 *
 * <p>Captures free-form and guided text data that serves as the raw input
 * for sentiment analysis, distress detection, and narrative pattern tracking.
 * Each entry is enriched with AI-computed metadata after submission.</p>
 *
 * <h3>5 Data Layers:</h3>
 * <ol>
 *   <li><b>Content</b> — the raw text, entry type, and any associated prompt</li>
 *   <li><b>Context</b> — mood at time of writing, linked check-in, trigger event</li>
 *   <li><b>AI Enrichment</b> — sentiment, emotions, distress flags, topic tags</li>
 *   <li><b>Engagement</b> — word count, writing duration, completion status</li>
 *   <li><b>Therapeutic Value</b> — action items, cognitive reframes, gratitude count</li>
 * </ol>
 *
 * <h3>Data Flow:</h3>
 * <pre>
 *   JournalActivity (user writes)
 *       → JournalEntry (this entity, saved immediately)
 *           → NLPProcessor (async enrichment)
 *               → updates aiSentiment, emotionTags, distressFlags, topicTags
 *           → InsightEngine (cross-references with usage patterns)
 *           → WeeklyAssessment (aggregated journalDays count)
 * </pre>
 *
 * @see QuestionnaireResponse
 * @see WeeklyAssessment
 */
@Entity(
        tableName = "journal_entries",
        indices = {
                @Index(value = {"timestamp"}),
                @Index(value = {"dayTimestamp"}),
                @Index(value = {"entryType"}),
                @Index(value = {"aiSentiment"})
        }
)
public class JournalEntry {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** When the entry was created. */
    public long timestamp;

    /** Day midnight timestamp — enables per-day lookups. */
    @ColumnInfo(defaultValue = "0")
    public long dayTimestamp;

    // ═════════════════════════════════════════════════════════════════════
    // LAYER 1: CONTENT
    // ═════════════════════════════════════════════════════════════════════

    /** The raw text content of the journal entry. */
    public String content;

    /**
     * Type of entry:
     * "free_form"          — unprompted user writing
     * "gratitude"          — what they're grateful for (auto-created from check-in)
     * "venting"            — emotional release / frustration
     * "guided_reflection"  — response to an AI prompt
     * "goal_setting"       — intention or commitment
     * "cbt_reframe"        — cognitive behavioral reframe exercise
     * "dream_log"          — sleep/dream journaling
     * "trigger_log"        — documenting what triggered a negative state
     */
    public String entryType;

    /** If this was a guided entry, the prompt text. */
    @Nullable
    public String relatedPrompt;

    /** Title/subject line (optional, for longer entries). */
    @Nullable
    public String title;

    // ═════════════════════════════════════════════════════════════════════
    // LAYER 2: CONTEXT (State at time of writing)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Mood at time of writing: "Happy", "Calm", "Neutral", "Anxious", "Sad", "Angry", "Numb".
     * Provides emotional context for the text content.
     */
    @Nullable
    public String moodAtWriting;

    /**
     * Stress level at time of writing (1-5, 0 = not recorded).
     */
    @ColumnInfo(defaultValue = "0")
    public int stressAtWriting;

    /**
     * What triggered this entry (if identifiable).
     * "check_in", "notification_prompt", "self_initiated", "crisis_prompt",
     * "post_binge", "high_stress", "bedtime_reflection"
     */
    @Nullable
    public String triggerSource;

    /**
     * ID of the linked QuestionnaireResponse (if this entry was created
     * as part of a check-in flow). 0 = standalone entry.
     */
    @ColumnInfo(defaultValue = "0")
    public int linkedCheckInId;

    // ═════════════════════════════════════════════════════════════════════
    // LAYER 3: AI ENRICHMENT (Computed post-submission)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * AI-assigned overall sentiment.
     * "very_positive", "positive", "neutral", "negative", "very_negative", "mixed"
     */
    @Nullable
    public String aiSentiment;

    /**
     * Sentiment polarity score (-1.0 to +1.0).
     * -1.0 = deeply negative, 0 = neutral, +1.0 = deeply positive.
     * More nuanced than categorical sentiment.
     */
    @ColumnInfo(defaultValue = "0")
    public float sentimentScore;

    /**
     * JSON array of detected emotions.
     * Example: ["sadness", "frustration", "hope"]
     * Richer than single sentiment — captures mixed emotions.
     */
    @Nullable
    public String emotionTags;

    /**
     * JSON array of distress markers found via NLP.
     * Example: ["hopelessness", "self_blame", "catastrophizing", "isolation"]
     * Maps to CBT cognitive distortion categories.
     */
    @Nullable
    public String distressFlags;

    /**
     * Number of distinct distress flags detected.
     * Faster than parsing JSON for threshold checks.
     */
    @ColumnInfo(defaultValue = "0")
    public int distressFlagCount;

    /**
     * JSON array of topic tags extracted from content.
     * Example: ["work", "relationships", "sleep", "phone_addiction"]
     * Used for pattern analysis across entries.
     */
    @Nullable
    public String topicTags;

    /**
     * JSON array of cognitive distortions detected (CBT framework).
     * Example: ["all_or_nothing", "catastrophizing", "mind_reading"]
     * Feeds the CBT intervention engine.
     */
    @Nullable
    public String cognitiveDistortions;

    // ═════════════════════════════════════════════════════════════════════
    // LAYER 4: ENGAGEMENT METRICS
    // ═════════════════════════════════════════════════════════════════════

    /** Word count of the content. */
    @ColumnInfo(defaultValue = "0")
    public int wordCount;

    /**
     * How long the user spent writing (ms).
     * Measured from entry open to submit.
     */
    @ColumnInfo(defaultValue = "0")
    public long writingDurationMs;

    /**
     * Whether the entry was completed or abandoned mid-flow.
     * Abandoned entries are still saved but marked incomplete.
     */
    @ColumnInfo(defaultValue = "1")
    public boolean isComplete;

    /**
     * Whether this entry was edited after initial submission.
     */
    @ColumnInfo(defaultValue = "0")
    public boolean wasEdited;

    // ═════════════════════════════════════════════════════════════════════
    // LAYER 5: THERAPEUTIC VALUE
    // ═════════════════════════════════════════════════════════════════════

    /** Actionable goal or takeaway the user noted. */
    @Nullable
    public String actionItem;

    /** Whether the action item was marked as completed. */
    @ColumnInfo(defaultValue = "0")
    public boolean actionItemCompleted;

    /**
     * Number of gratitude items mentioned (for gratitude entries).
     * Research shows 3+ daily gratitude items measurably improve wellbeing.
     */
    @ColumnInfo(defaultValue = "0")
    public int gratitudeItemCount;

    /**
     * AI-generated therapeutic insight or reframe suggestion.
     * Shown to the user after they submit their entry.
     */
    @Nullable
    public String aiReframeSuggestion;

    /**
     * Whether the user found the AI insight helpful (null = not rated).
     */
    @Nullable
    public Boolean aiInsightHelpful;

    // ═════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═════════════════════════════════════════════════════════════════════

    /** Compute word count from content. Call before saving. */
    public void computeWordCount() {
        if (content == null || content.trim().isEmpty()) {
            wordCount = 0;
            return;
        }
        wordCount = content.trim().split("\\s+").length;
    }

    /** Whether this entry has distress signals. */
    public boolean hasDistress() {
        return distressFlagCount > 0
                || (distressFlags != null && !distressFlags.isEmpty() && !"[]".equals(distressFlags));
    }

    /** Whether the sentiment is negative or very negative. */
    public boolean isNegativeSentiment() {
        return sentimentScore < -0.3f
                || "negative".equals(aiSentiment)
                || "very_negative".equals(aiSentiment);
    }

    /** Whether the entry has therapeutic content (gratitude, reframe, action items). */
    public boolean hasTherapeuticValue() {
        return gratitudeItemCount > 0
                || actionItem != null
                || "cbt_reframe".equals(entryType)
                || "goal_setting".equals(entryType);
    }

    /** Whether this entry has enough content for NLP analysis. */
    public boolean isAnalyzable() {
        return content != null && wordCount >= 10 && isComplete;
    }

    @NonNull
    @Override
    public String toString() {
        return "Journal{" +
                "type='" + entryType + '\'' +
                ", words=" + wordCount +
                ", sentiment=" + aiSentiment + "(" + String.format("%.2f", sentimentScore) + ")" +
                ", distress=" + distressFlagCount +
                ", mood='" + moodAtWriting + '\'' +
                '}';
    }
}
