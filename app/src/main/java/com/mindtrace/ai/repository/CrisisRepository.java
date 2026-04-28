package com.mindtrace.ai.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.mindtrace.ai.ai.CrisisDetector;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.CrisisEventDao;
import com.mindtrace.ai.database.entity.CrisisEvent;

import java.util.List;

/**
 * Repository for crisis event management — save, resolve, cooldown, and analytics.
 */
public class CrisisRepository {

    private static final String TAG = "CrisisRepository";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long COOLDOWN_MS = 30 * 60 * 1000; // 30 min

    private final CrisisEventDao dao;
    private final Context context;

    public CrisisRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(this.context).crisisEventDao();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SAVE & RESOLVE
    // ═══════════════════════════════════════════════════════════════════

    /** Create a new crisis event from a CrisisAssessment. */
    public long saveCrisisEvent(@NonNull CrisisDetector.CrisisAssessment assessment) {
        CrisisEvent event = new CrisisEvent();
        event.timestamp = assessment.timestamp;
        event.crisisLevel = assessment.level.name();
        event.status = "ACTIVE";
        event.assessmentConfidence = assessment.confidenceScore;
        event.triggerSignalsJson = String.join("|", assessment.activeSignals);

        try {
            long id = dao.insert(event);
            Log.d(TAG, "Crisis event saved: " + assessment.level.label + " (id=" + id + ")");

            // Auto-notify trusted contacts for URGENT+ crises
            if (assessment.level.ordinal() >= 3) { // URGENT = 3, CRITICAL = 4
                notifyTrustedContacts(event);
            }

            return id;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save crisis event", e);
            return -1;
        }
    }

    /**
     * Auto-notify all TrustedContacts flagged with notifyOnCrisis=true.
     *
     * <p>Uses SmsManager to send pre-composed crisis SMS messages.
     * This is the critical bridge between SafetyPlan contacts and
     * real-time crisis intervention.</p>
     *
     * <p>Only fires for URGENT/CRITICAL crises to prevent alert fatigue.</p>
     */
    private void notifyTrustedContacts(@NonNull CrisisEvent event) {
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            List<com.mindtrace.ai.database.entity.TrustedContact> contacts =
                    db.trustedContactDao().getCrisisNotifyContactsSync();

            if (contacts == null || contacts.isEmpty()) {
                Log.d(TAG, "No crisis-notify contacts configured");
                return;
            }

            for (com.mindtrace.ai.database.entity.TrustedContact contact : contacts) {
                if (contact.phone != null && !contact.phone.isEmpty()) {
                    try {
                        android.telephony.SmsManager sms =
                                android.telephony.SmsManager.getDefault();
                        String message = contact.getCrisisSmsText();
                        sms.sendTextMessage(contact.phone, null, message, null, null);
                        Log.d(TAG, "Crisis SMS sent to: " + contact.name);
                    } catch (SecurityException se) {
                        // SMS permission not granted — fall through silently
                        Log.w(TAG, "SMS permission denied for: " + contact.name);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send SMS to: " + contact.name, e);
                    }
                }
            }

            Log.d(TAG, "Notified " + contacts.size() + " trusted contacts for " +
                    event.crisisLevel + " crisis");
        } catch (Exception e) {
            Log.e(TAG, "Failed to notify trusted contacts", e);
        }
    }

    /** Resolve the active crisis event. */
    public void resolveActive(@NonNull String method, @Nullable String mood, int postDistress) {
        try {
            CrisisEvent active = dao.getActiveEvent();
            if (active != null) {
                active.resolve(method, mood, postDistress);
                dao.update(active);
                Log.d(TAG, "Crisis resolved via " + method);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve crisis", e);
        }
    }

    /** Update a crisis event (for debrief completion, etc.). */
    public void update(@NonNull CrisisEvent event) {
        try { dao.update(event); }
        catch (Exception e) { Log.e(TAG, "Update failed", e); }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    public LiveData<List<CrisisEvent>> getAllEvents() {
        return dao.getAllEvents();
    }

    @Nullable
    public CrisisEvent getActiveEvent() {
        try { return dao.getActiveEvent(); }
        catch (Exception e) { return null; }
    }

    @NonNull
    public List<CrisisEvent> getRecentEvents(int limit) {
        try { return dao.getRecentEvents(limit); }
        catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COOLDOWN
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if we're in a cooldown period (to prevent notification spam).
     * Returns true if a crisis event was created in the last 30 minutes.
     */
    public boolean isInCooldown() {
        long since = System.currentTimeMillis() - COOLDOWN_MS;
        try { return dao.getCountSince(since) > 0; }
        catch (Exception e) { return false; }
    }

    /** Get time in ms since the last crisis event. */
    public long getTimeSinceLastCrisis() {
        try {
            List<CrisisEvent> recent = dao.getRecentEvents(1);
            if (recent.isEmpty()) return Long.MAX_VALUE;
            return System.currentTimeMillis() - recent.get(0).timestamp;
        } catch (Exception e) { return Long.MAX_VALUE; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANALYTICS
    // ═══════════════════════════════════════════════════════════════════

    /** Count crisis events in last N days. */
    public int getCrisisCountLastNDays(int days) {
        long since = System.currentTimeMillis() - (days * DAY_MS);
        try { return dao.getCountSince(since); }
        catch (Exception e) { return 0; }
    }

    /** Average time to resolve a crisis (ms) in last 30 days. */
    public long getAverageResolutionTime() {
        long since = System.currentTimeMillis() - (30 * DAY_MS);
        try { return dao.getAverageResolutionTimeMs(since); }
        catch (Exception e) { return 0; }
    }

    /** Average distress reduction from exercises/support. */
    public float getAverageDistressReduction() {
        try { return dao.getAverageDistressReduction(); }
        catch (Exception e) { return 0f; }
    }

    /** Get the most-used resolution method. */
    @Nullable
    public String getMostEffectiveCopingStrategy() {
        try { return dao.getMostUsedResolutionMethod(); }
        catch (Exception e) { return null; }
    }

    /** Get a crisis event that needs a post-crisis debrief (resolved 4-6h ago). */
    @Nullable
    public CrisisEvent getEventNeedingDebrief() {
        long before = System.currentTimeMillis() - (4 * 60 * 60 * 1000); // 4h ago
        try { return dao.getEventNeedingDebrief(before); }
        catch (Exception e) { return null; }
    }

    /** Generate a weekly crisis summary. */
    @NonNull
    public String getWeeklyCrisisSummary() {
        int thisWeek = getCrisisCountLastNDays(7);
        int lastWeek = getCrisisCountLastNDays(14) - thisWeek;

        if (thisWeek == 0 && lastWeek == 0) return "No crisis events in the last 2 weeks.";
        if (thisWeek == 0) return "No crisis events this week (down from " + lastWeek + " last week).";

        int delta = thisWeek - lastWeek;
        String trend = delta < 0 ? (Math.abs(delta) + " fewer") :
                delta > 0 ? (delta + " more") : "same number of";
        return thisWeek + " crisis event" + (thisWeek > 1 ? "s" : "") + " this week (" +
                trend + " than last week).";
    }
}
