package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * User-defined safety plan — clinical best practice for crisis management.
 *
 * <p>Based on the Stanley-Brown Safety Planning Intervention, a 6-step
 * evidence-based tool for suicide prevention and crisis management.</p>
 *
 * <h3>6 Sections:</h3>
 * <ol>
 *   <li>Warning signs — "I know I'm in trouble when..."</li>
 *   <li>Coping strategies — things I can do to calm down</li>
 *   <li>Reasons to live — what matters to me</li>
 *   <li>People I can contact — friends/family for distraction</li>
 *   <li>Professional contacts — therapist, helpline numbers</li>
 *   <li>Safe environments — places I feel safe</li>
 * </ol>
 *
 * <p>Singleton entity (id=1). Auto-surfaced during crisis when CrisisLevel >= ELEVATED.</p>
 */
@Entity(tableName = "safety_plan")
public class SafetyPlan {

    @PrimaryKey
    public int id = 1;

    /** JSON array: warning signs that indicate a crisis is starting. */
    @Nullable
    public String warningSignalsJson;

    /** JSON array: personal coping strategies that work. */
    @Nullable
    public String copingStrategiesJson;

    /** JSON array: reasons to keep going. */
    @Nullable
    public String reasonsToLiveJson;

    /** JSON array: people to contact (name + phone). */
    @Nullable
    public String trustedContactsJson;

    /** JSON array: professional contacts (name + phone + role). */
    @Nullable
    public String professionalContactsJson;

    /** JSON array: safe environments / places. */
    @Nullable
    public String safeEnvironmentsJson;

    public long createdAt;

    public long updatedAt;

    /** Whether the safety plan has been completed (all sections filled). */
    @ColumnInfo(defaultValue = "0")
    public boolean isComplete;

    // ─────────────────────────────────────────────────────────────────────
    // CONVENIENCE
    // ─────────────────────────────────────────────────────────────────────

    /** Check if the safety plan has any content. */
    public boolean hasContent() {
        return hasItems(warningSignalsJson) || hasItems(copingStrategiesJson)
                || hasItems(reasonsToLiveJson) || hasItems(trustedContactsJson);
    }

    /** Get the completion percentage (0-100). */
    public int getCompletionPercent() {
        int filled = 0;
        if (hasItems(warningSignalsJson)) filled++;
        if (hasItems(copingStrategiesJson)) filled++;
        if (hasItems(reasonsToLiveJson)) filled++;
        if (hasItems(trustedContactsJson)) filled++;
        if (hasItems(professionalContactsJson)) filled++;
        if (hasItems(safeEnvironmentsJson)) filled++;
        return (filled * 100) / 6;
    }

    /** Export safety plan as shareable text. */
    @NonNull
    public String toShareableText() {
        StringBuilder sb = new StringBuilder("My Safety Plan\n\n");
        appendSection(sb, "⚠️ Warning Signs", warningSignalsJson);
        appendSection(sb, "💪 Coping Strategies", copingStrategiesJson);
        appendSection(sb, "❤️ Reasons to Live", reasonsToLiveJson);
        appendSection(sb, "📞 Trusted Contacts", trustedContactsJson);
        appendSection(sb, "🏥 Professional Contacts", professionalContactsJson);
        appendSection(sb, "🏠 Safe Environments", safeEnvironmentsJson);
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, String json) {
        sb.append(title).append("\n");
        if (json != null && !json.isEmpty()) {
            String[] items = json.replace("[", "").replace("]", "")
                    .replace("\"", "").split(",");
            for (String item : items) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) sb.append("  • ").append(trimmed).append("\n");
            }
        } else {
            sb.append("  (not filled yet)\n");
        }
        sb.append("\n");
    }

    private boolean hasItems(String json) {
        return json != null && !json.isEmpty() && !json.equals("[]");
    }
}
