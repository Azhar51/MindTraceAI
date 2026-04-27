package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Emergency/trusted contact for crisis support.
 *
 * <p>Stored locally only — never synced to any server.
 * Shown in SupportFragment for quick "reach out" and in CrisisActivity
 * for auto-compose SMS when CrisisLevel >= URGENT.</p>
 */
@Entity(tableName = "trusted_contacts")
public class TrustedContact {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String name = "";

    @NonNull
    public String phone = "";

    /** Relationship to user: "Friend", "Family", "Partner", "Therapist", "Other" */
    @Nullable
    public String relationship;

    /** Whether this is the primary emergency contact. */
    @ColumnInfo(defaultValue = "0")
    public boolean isEmergency;

    /** Whether to auto-notify this contact during URGENT/CRITICAL crisis. */
    @ColumnInfo(defaultValue = "0")
    public boolean notifyOnCrisis;

    public long createdAt;

    // ─────────────────────────────────────────────────────────────────────
    // CONVENIENCE
    // ─────────────────────────────────────────────────────────────────────

    /** Get display label (name + relationship). */
    @NonNull
    public String getDisplayLabel() {
        if (relationship != null && !relationship.isEmpty()) {
            return name + " (" + relationship + ")";
        }
        return name;
    }

    /** Generate a pre-composed crisis SMS text. */
    @NonNull
    public String getCrisisSmsText() {
        return "Hey " + name + ", I'm having a really tough time right now. " +
                "Can you check on me when you get this? — via MindTrace AI";
    }

    @NonNull
    @Override
    public String toString() {
        return "TrustedContact{" + name + ", " + phone +
                (isEmergency ? " [EMERGENCY]" : "") + "}";
    }
}
