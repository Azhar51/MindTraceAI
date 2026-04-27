package com.mindtrace.ai.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Persistent state manager for surviving process death, crash recovery,
 * and configuration changes.
 *
 * <h3>Persisted State:</h3>
 * <ul>
 *   <li>Last active tab (for seamless restore)</li>
 *   <li>Active crisis session flag</li>
 *   <li>Last check-in timestamp (for smart prompts)</li>
 *   <li>Pending XP credits (survive crashes)</li>
 *   <li>Unsaved journal draft</li>
 * </ul>
 */
public class ProcessDeathRecovery {

    private static final String PREFS_NAME = "mindtrace_process_state";
    private static final String TAG = "ProcessDeathRecovery";

    // Keys
    private static final String KEY_LAST_TAB = "last_active_tab";
    private static final String KEY_ACTIVE_CRISIS = "active_crisis";
    private static final String KEY_LAST_CHECKIN = "last_checkin_timestamp";
    private static final String KEY_PENDING_XP = "pending_xp";
    private static final String KEY_JOURNAL_DRAFT = "journal_draft";
    private static final String KEY_JOURNAL_DRAFT_TYPE = "journal_draft_type";
    private static final String KEY_LAST_SAVE_TIME = "last_save_time";

    private final SharedPreferences prefs;

    public ProcessDeathRecovery(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TAB STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Save current tab for seamless restore after process death. */
    public void saveLastTab(@NonNull String tabDestination) {
        prefs.edit().putString(KEY_LAST_TAB, tabDestination).apply();
    }

    /** Get last active tab, defaulting to overview. */
    @NonNull
    public String getLastTab() {
        return prefs.getString(KEY_LAST_TAB, "overview");
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Flag an active crisis session (survives process death). */
    public void setCrisisActive(boolean active) {
        prefs.edit().putBoolean(KEY_ACTIVE_CRISIS, active).apply();
        Log.d(TAG, "Crisis active state: " + active);
    }

    /** Check if there's an active crisis that needs recovery. */
    public boolean isCrisisActive() {
        return prefs.getBoolean(KEY_ACTIVE_CRISIS, false);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CHECK-IN STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Record when the last check-in happened. */
    public void saveLastCheckIn() {
        prefs.edit().putLong(KEY_LAST_CHECKIN, System.currentTimeMillis()).apply();
    }

    /** Get last check-in timestamp. */
    public long getLastCheckInTimestamp() {
        return prefs.getLong(KEY_LAST_CHECKIN, 0);
    }

    /** Whether a check-in was done today. */
    public boolean hasCheckedInToday() {
        long last = getLastCheckInTimestamp();
        if (last == 0) return false;
        long dayStart = (System.currentTimeMillis() / 86400000L) * 86400000L;
        return last >= dayStart;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PENDING XP (crash-safe)
    // ═══════════════════════════════════════════════════════════════════

    /** Add pending XP that survived a crash. */
    public void addPendingXp(int xp) {
        int current = prefs.getInt(KEY_PENDING_XP, 0);
        prefs.edit().putInt(KEY_PENDING_XP, current + xp).apply();
    }

    /** Get and clear pending XP. */
    public int consumePendingXp() {
        int pending = prefs.getInt(KEY_PENDING_XP, 0);
        if (pending > 0) {
            prefs.edit().putInt(KEY_PENDING_XP, 0).apply();
        }
        return pending;
    }

    // ═══════════════════════════════════════════════════════════════════
    // JOURNAL DRAFT (auto-save)
    // ═══════════════════════════════════════════════════════════════════

    /** Auto-save journal draft (called periodically during writing). */
    public void saveJournalDraft(@NonNull String content, @NonNull String entryType) {
        prefs.edit()
                .putString(KEY_JOURNAL_DRAFT, content)
                .putString(KEY_JOURNAL_DRAFT_TYPE, entryType)
                .putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis())
                .apply();
    }

    /** Get saved journal draft, or null if none. */
    public String getJournalDraft() {
        return prefs.getString(KEY_JOURNAL_DRAFT, null);
    }

    /** Get draft entry type. */
    public String getJournalDraftType() {
        return prefs.getString(KEY_JOURNAL_DRAFT_TYPE, "free_form");
    }

    /** Check if there's a recent unsaved draft (< 24h old). */
    public boolean hasRecentDraft() {
        String draft = getJournalDraft();
        if (draft == null || draft.trim().isEmpty()) return false;
        long saveTime = prefs.getLong(KEY_LAST_SAVE_TIME, 0);
        return System.currentTimeMillis() - saveTime < 24 * 60 * 60 * 1000;
    }

    /** Clear the journal draft after successful save. */
    public void clearJournalDraft() {
        prefs.edit()
                .remove(KEY_JOURNAL_DRAFT)
                .remove(KEY_JOURNAL_DRAFT_TYPE)
                .remove(KEY_LAST_SAVE_TIME)
                .apply();
    }

    /** Clear all saved state (for data deletion). */
    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
