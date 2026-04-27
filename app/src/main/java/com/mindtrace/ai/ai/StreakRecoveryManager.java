package com.mindtrace.ai.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.CrisisEvent;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Streak Recovery Manager — compassionate grace-period logic for wellness streaks.
 *
 * <p>When users experience a crisis, their wellness streaks (check-in, journal,
 * exercise completion) naturally break. Traditional streak systems punish this
 * by resetting progress, which <b>discourages</b> users during their most
 * vulnerable moments.</p>
 *
 * <p>This manager implements a <b>grace period</b> system that:</p>
 * <ul>
 *   <li>Detects when a streak break coincides with a crisis event</li>
 *   <li>Preserves the streak during a configurable grace window</li>
 *   <li>Generates compassionate recovery messaging</li>
 *   <li>Provides graduated re-engagement paths (not all-or-nothing)</li>
 * </ul>
 *
 * <h3>Grace Period Rules:</h3>
 * <pre>
 *   ┌─────────────────┬───────────────────────────────────────────┐
 *   │ Crisis Level     │ Grace Period                              │
 *   ├─────────────────┼───────────────────────────────────────────┤
 *   │ ELEVATED         │ 24 hours                                  │
 *   │ URGENT           │ 48 hours                                  │
 *   │ CRITICAL         │ 72 hours                                  │
 *   └─────────────────┴───────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Recovery Paths:</h3>
 * <ol>
 *   <li><b>Gentle Return</b> — "Welcome back" + reduced expectations</li>
 *   <li><b>Partial Credit</b> — Half-credit for first day back</li>
 *   <li><b>Recovery Streak</b> — New mini-streak with lower bar</li>
 * </ol>
 *
 * @see com.mindtrace.ai.database.entity.CrisisEvent
 * @see com.mindtrace.ai.services.DailyReminderWorker
 */
public class StreakRecoveryManager {

    private static final String TAG = "StreakRecoveryMgr";
    private static final String PREFS_NAME = "mindtrace_streak_recovery";
    private static final String KEY_GRACE_ACTIVE = "grace_active";
    private static final String KEY_GRACE_START = "grace_start_time";
    private static final String KEY_GRACE_END = "grace_end_time";
    private static final String KEY_GRACE_LEVEL = "grace_crisis_level";
    private static final String KEY_RECOVERY_PHASE = "recovery_phase";
    private static final String KEY_PRE_CRISIS_STREAK = "pre_crisis_streak";

    /** Grace periods by crisis level (in hours). */
    private static final int GRACE_HOURS_ELEVATED = 24;
    private static final int GRACE_HOURS_URGENT = 48;
    private static final int GRACE_HOURS_CRITICAL = 72;

    // ═══════════════════════════════════════════════════════════════════
    // RECOVERY STATE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Current state of streak recovery for a user.
     */
    public static class RecoveryState {
        /** Whether a grace period is currently active. */
        public final boolean graceActive;

        /** Hours remaining in the grace period (0 if not active). */
        public final int graceHoursRemaining;

        /** The crisis level that triggered the grace period. */
        @Nullable public final String crisisLevel;

        /** Current recovery phase: "grace", "gentle_return", "recovery_streak", "normal". */
        @NonNull public final String phase;

        /** Streak count preserved from before the crisis. */
        public final int preservedStreak;

        /** Compassionate message for the user. */
        @NonNull public final String message;

        /** Whether reduced expectations should be applied. */
        public final boolean reducedExpectations;

        /** Whether partial credit is available for first task back. */
        public final boolean partialCreditAvailable;

        public RecoveryState(boolean graceActive, int graceHoursRemaining,
                             @Nullable String crisisLevel, @NonNull String phase,
                             int preservedStreak, @NonNull String message,
                             boolean reducedExpectations, boolean partialCreditAvailable) {
            this.graceActive = graceActive;
            this.graceHoursRemaining = graceHoursRemaining;
            this.crisisLevel = crisisLevel;
            this.phase = phase;
            this.preservedStreak = preservedStreak;
            this.message = message;
            this.reducedExpectations = reducedExpectations;
            this.partialCreditAvailable = partialCreditAvailable;
        }

        /** Normal state — no recovery needed. */
        public static RecoveryState normal() {
            return new RecoveryState(false, 0, null, "normal",
                    0, "", false, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS EVENT DETECTION + GRACE ACTIVATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check for recent crisis events and activate grace period if needed.
     *
     * <p>Called by DailyReminderWorker and streak-checking logic. If a
     * crisis event occurred within the last 72 hours and no grace period
     * is already active, one is automatically activated.</p>
     *
     * @param ctx application context
     * @param currentStreak current streak count before potential break
     */
    public void checkAndActivateGrace(@NonNull Context ctx, int currentStreak) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Already in grace period?
        if (prefs.getBoolean(KEY_GRACE_ACTIVE, false)) {
            long graceEnd = prefs.getLong(KEY_GRACE_END, 0);
            if (System.currentTimeMillis() < graceEnd) {
                Log.d(TAG, "Grace period already active");
                return;
            }
            // Grace period expired — transition to gentle return
            transitionToGentleReturn(prefs);
            return;
        }

        // Check for recent crisis events
        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            long seventyTwoHoursAgo = System.currentTimeMillis() - (72L * 60 * 60 * 1000);
            List<CrisisEvent> recentCrises = db.crisisEventDao().getEventsSince(seventyTwoHoursAgo);

            if (recentCrises == null || recentCrises.isEmpty()) return;

            // Find the most severe recent crisis
            CrisisEvent mostSevere = recentCrises.get(0);
            for (CrisisEvent event : recentCrises) {
                if (getSeverityRank(event.crisisLevel) > getSeverityRank(mostSevere.crisisLevel)) {
                    mostSevere = event;
                }
            }

            // Only activate grace for ELEVATED or above
            int rank = getSeverityRank(mostSevere.crisisLevel);
            if (rank < 2) return; // WATCH doesn't trigger grace

            activateGracePeriod(prefs, mostSevere.crisisLevel, currentStreak);

        } catch (Exception e) {
            Log.e(TAG, "Failed to check crisis events for grace period", e);
        }
    }

    /**
     * Activate a grace period for the given crisis level.
     */
    private void activateGracePeriod(@NonNull SharedPreferences prefs,
                                      @NonNull String crisisLevel,
                                      int currentStreak) {
        int graceHours;
        switch (crisisLevel) {
            case "CRITICAL": graceHours = GRACE_HOURS_CRITICAL; break;
            case "URGENT":   graceHours = GRACE_HOURS_URGENT;   break;
            default:         graceHours = GRACE_HOURS_ELEVATED;  break;
        }

        long now = System.currentTimeMillis();
        long graceEnd = now + TimeUnit.HOURS.toMillis(graceHours);

        prefs.edit()
                .putBoolean(KEY_GRACE_ACTIVE, true)
                .putLong(KEY_GRACE_START, now)
                .putLong(KEY_GRACE_END, graceEnd)
                .putString(KEY_GRACE_LEVEL, crisisLevel)
                .putString(KEY_RECOVERY_PHASE, "grace")
                .putInt(KEY_PRE_CRISIS_STREAK, currentStreak)
                .apply();

        Log.d(TAG, "Grace period activated: " + graceHours + "h for " + crisisLevel +
                " (preserving streak of " + currentStreak + ")");
    }

    /**
     * Transition from grace period to gentle return phase.
     */
    private void transitionToGentleReturn(@NonNull SharedPreferences prefs) {
        prefs.edit()
                .putBoolean(KEY_GRACE_ACTIVE, false)
                .putString(KEY_RECOVERY_PHASE, "gentle_return")
                .apply();
        Log.d(TAG, "Transitioned to gentle return phase");
    }

    // ═══════════════════════════════════════════════════════════════════
    // RECOVERY STATE QUERY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get the current recovery state for UI rendering.
     *
     * @param ctx application context
     * @return current recovery state with messaging and phase info
     */
    @NonNull
    public RecoveryState getRecoveryState(@NonNull Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String phase = prefs.getString(KEY_RECOVERY_PHASE, "normal");
        if ("normal".equals(phase)) return RecoveryState.normal();

        String crisisLevel = prefs.getString(KEY_GRACE_LEVEL, "ELEVATED");
        int preservedStreak = prefs.getInt(KEY_PRE_CRISIS_STREAK, 0);
        long graceEnd = prefs.getLong(KEY_GRACE_END, 0);
        long now = System.currentTimeMillis();

        switch (phase) {
            case "grace": {
                if (now >= graceEnd) {
                    transitionToGentleReturn(prefs);
                    return getRecoveryState(ctx); // Re-evaluate
                }
                int hoursRemaining = (int) TimeUnit.MILLISECONDS.toHours(graceEnd - now);

                String message = buildGraceMessage(crisisLevel, hoursRemaining, preservedStreak);
                return new RecoveryState(true, hoursRemaining, crisisLevel, "grace",
                        preservedStreak, message, true, false);
            }

            case "gentle_return": {
                String message = buildGentleReturnMessage(preservedStreak);
                return new RecoveryState(false, 0, crisisLevel, "gentle_return",
                        preservedStreak, message, true, true);
            }

            case "recovery_streak": {
                String message = "You're building momentum again. Every step counts — " +
                        "even small ones. Your " + preservedStreak + "-day streak " +
                        "isn't lost, it's transforming.";
                return new RecoveryState(false, 0, crisisLevel, "recovery_streak",
                        preservedStreak, message, false, false);
            }

            default:
                return RecoveryState.normal();
        }
    }

    /**
     * Mark that the user has completed their first activity after a grace period.
     * Transitions from gentle_return to recovery_streak.
     */
    public void markFirstActivityCompleted(@NonNull Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String phase = prefs.getString(KEY_RECOVERY_PHASE, "normal");

        if ("gentle_return".equals(phase)) {
            prefs.edit()
                    .putString(KEY_RECOVERY_PHASE, "recovery_streak")
                    .apply();
            Log.d(TAG, "Transitioned to recovery streak phase");
        }
    }

    /**
     * Complete the recovery process and return to normal streak tracking.
     * Called after 3 consecutive days of activity post-recovery.
     */
    public void completeRecovery(@NonNull Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_GRACE_ACTIVE, false)
                .putString(KEY_RECOVERY_PHASE, "normal")
                .putLong(KEY_GRACE_START, 0)
                .putLong(KEY_GRACE_END, 0)
                .putString(KEY_GRACE_LEVEL, "")
                .putInt(KEY_PRE_CRISIS_STREAK, 0)
                .apply();
        Log.d(TAG, "Recovery complete, returning to normal streak tracking");
    }

    /**
     * Check whether the streak should be preserved (grace period active).
     * Used by DailyReminderWorker when evaluating streak breaks.
     *
     * @return true if the streak should NOT be reset
     */
    public boolean shouldPreserveStreak(@NonNull Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_GRACE_ACTIVE, false)) return false;

        long graceEnd = prefs.getLong(KEY_GRACE_END, 0);
        return System.currentTimeMillis() < graceEnd;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MESSAGE BUILDERS
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private String buildGraceMessage(@NonNull String crisisLevel,
                                      int hoursRemaining,
                                      int preservedStreak) {
        StringBuilder sb = new StringBuilder();

        switch (crisisLevel) {
            case "CRITICAL":
                sb.append("You went through something really difficult. ");
                sb.append("Your wellbeing matters more than any streak. ");
                break;
            case "URGENT":
                sb.append("Yesterday was tough, and that's okay. ");
                sb.append("We're keeping your progress safe while you recover. ");
                break;
            default:
                sb.append("We noticed you had a challenging moment. ");
                sb.append("Your streak is safe — take the time you need. ");
                break;
        }

        if (preservedStreak > 0) {
            sb.append("Your ").append(preservedStreak).append("-day streak is preserved. ");
        }
        if (hoursRemaining > 0) {
            sb.append("Grace period: ").append(hoursRemaining).append("h remaining.");
        }

        return sb.toString();
    }

    @NonNull
    private String buildGentleReturnMessage(int preservedStreak) {
        if (preservedStreak >= 14) {
            return "Welcome back. Your " + preservedStreak + "-day journey isn't gone — " +
                    "it's part of who you are now. Start with something small today. " +
                    "Just one check-in earns you full credit.";
        } else if (preservedStreak >= 7) {
            return "You're back, and that takes strength. Your " + preservedStreak +
                    "-day streak is waiting for you. Complete any one activity " +
                    "to pick right back up.";
        } else {
            return "Welcome back. There's no pressure here — do what feels " +
                    "right today. Any small step counts as a full win.";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private int getSeverityRank(@NonNull String level) {
        switch (level) {
            case "CRITICAL": return 4;
            case "URGENT":   return 3;
            case "ELEVATED": return 2;
            case "WATCH":    return 1;
            default:         return 0;
        }
    }
}
