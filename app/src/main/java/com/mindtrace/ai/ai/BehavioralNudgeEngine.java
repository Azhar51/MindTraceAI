package com.mindtrace.ai.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.BehaviorSnapshotEntity;
import com.mindtrace.ai.database.entity.UserBaseline;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Just-in-Time Behavioral Nudge Engine — proactive micro-intervention system.
 *
 * <p>Detects <b>emerging</b> behavioral micro-patterns that precede distress
 * and delivers context-aware nudges <i>before</i> the user reaches crisis
 * thresholds. This shifts the system from <b>reactive</b> detection to
 * <b>predictive</b> pre-emptive support.</p>
 *
 * <h3>Detection Patterns (7 micro-pattern detectors):</h3>
 * <pre>
 *   ┌───────────────────────────┬──────────────────────────────────────────┐
 *   │ Pattern                   │ Signal                                   │
 *   ├───────────────────────────┼──────────────────────────────────────────┤
 *   │ Rapid Unlock Cycling      │ ≥8 rapid switches in <2h window          │
 *   │ Late-Night Acceleration   │ Usage after 1AM + rising velocity         │
 *   │ Passive Consumption Spike │ Passive ratio >75% for >45min             │
 *   │ App Loop Detection        │ Same 2-3 apps in tight rotation           │
 *   │ Screen Time Velocity      │ Today's pace >140% of 7-day baseline     │
 *   │ Emotional Drift Signal    │ Rapid switch + binge + late-night combo   │
 *   │ Isolation Pattern         │ No productive apps + extended passive use │
 *   └───────────────────────────┴──────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Nudge Philosophy:</h3>
 * <ul>
 *   <li>Compassionate, never judgmental — "We noticed..." not "You should..."</li>
 *   <li>Actionable within 30 seconds — breathing, stretch, micro-journal</li>
 *   <li>Respects fatigue — max 3 nudges per day, cooldown between nudges</li>
 *   <li>Context-aware — time-of-day, recent crisis history, streak status</li>
 * </ul>
 *
 * @see AnomalyDetector
 * @see BehaviorReport
 */
public class BehavioralNudgeEngine {

    private static final String TAG = "BehavioralNudgeEngine";
    private static final String PREFS_NAME = "mindtrace_nudge_engine";
    private static final String KEY_LAST_NUDGE_TIME = "last_nudge_time";
    private static final String KEY_NUDGE_COUNT_TODAY = "nudge_count_today";
    private static final String KEY_NUDGE_DATE = "nudge_date";

    /** Minimum gap between nudges (45 minutes). */
    private static final long NUDGE_COOLDOWN_MS = 45L * 60 * 1000;

    /** Maximum nudges per day to avoid notification fatigue. */
    private static final int MAX_NUDGES_PER_DAY = 3;

    /** Rapid switch threshold that triggers a nudge. */
    private static final int RAPID_SWITCH_THRESHOLD = 8;

    /** Late-night hour boundary (1 AM). */
    private static final int LATE_NIGHT_HOUR = 1;

    /** Deep late-night hour (3 AM — higher urgency). */
    private static final int DEEP_NIGHT_HOUR = 3;

    /** Passive consumption ratio threshold (75%). */
    private static final double PASSIVE_RATIO_THRESHOLD = 0.75;

    /** Screen time velocity multiplier threshold (140% of baseline). */
    private static final double VELOCITY_THRESHOLD = 1.40;

    /** Minimum late-night usage for nudge trigger (10 minutes). */
    private static final long LATE_NIGHT_MIN_MS = 10L * 60 * 1000;

    // ═══════════════════════════════════════════════════════════════════
    // NUDGE RESULT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Result of a nudge assessment — contains the nudge content and metadata.
     */
    public static class NudgeResult {
        /** The detected pattern that triggered this nudge. */
        @NonNull public final String patternId;

        /** Nudge severity: 1 (gentle) → 3 (urgent). */
        public final int severity;

        /** Notification title. */
        @NonNull public final String title;

        /** Notification body — compassionate, actionable message. */
        @NonNull public final String body;

        /** Suggested micro-action type: "breathing", "stretch", "journal", "grounding". */
        @NonNull public final String actionType;

        /** Estimated time for the suggested action (e.g., "2 min"). */
        @NonNull public final String actionDuration;

        /** Confidence in the pattern detection (0.0 — 1.0). */
        public final float confidence;

        public NudgeResult(@NonNull String patternId, int severity,
                           @NonNull String title, @NonNull String body,
                           @NonNull String actionType, @NonNull String actionDuration,
                           float confidence) {
            this.patternId = patternId;
            this.severity = severity;
            this.title = title;
            this.body = body;
            this.actionType = actionType;
            this.actionDuration = actionDuration;
            this.confidence = confidence;
        }

        @NonNull
        @Override
        public String toString() {
            return "NudgeResult{" + patternId + ", sev=" + severity +
                    ", conf=" + confidence + "}";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORE ASSESSMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Assess current behavioral state and return a nudge if warranted.
     *
     * <p>Runs all 7 micro-pattern detectors against the current behavior
     * report and returns the highest-priority nudge that passes cooldown
     * and daily frequency checks.</p>
     *
     * @param ctx      application context
     * @param report   current behavior report (non-null, dataAvailable=true)
     * @param baseline user's 7-day baseline (nullable — degrades gracefully)
     * @param screenTimeMs today's total screen time in milliseconds
     * @return highest-priority nudge, or null if no nudge warranted
     */
    @Nullable
    public NudgeResult assess(@NonNull Context ctx,
                               @NonNull BehaviorReport report,
                               @Nullable UserBaseline baseline,
                               long screenTimeMs) {
        if (!report.dataAvailable) return null;

        // ── Cooldown + daily limit checks ──
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastNudge = prefs.getLong(KEY_LAST_NUDGE_TIME, 0);
        if (now - lastNudge < NUDGE_COOLDOWN_MS) {
            Log.d(TAG, "Nudge cooldown active, skipping assessment");
            return null;
        }

        int todayCount = getDailyNudgeCount(prefs, now);
        if (todayCount >= MAX_NUDGES_PER_DAY) {
            Log.d(TAG, "Daily nudge limit reached (" + MAX_NUDGES_PER_DAY + ")");
            return null;
        }

        // ── Run all detectors, collect candidates ──
        List<NudgeResult> candidates = new ArrayList<>();

        NudgeResult emotionalDrift = detectEmotionalDrift(report);
        if (emotionalDrift != null) candidates.add(emotionalDrift);

        NudgeResult lateNight = detectLateNightAcceleration(report);
        if (lateNight != null) candidates.add(lateNight);

        NudgeResult rapidUnlock = detectRapidUnlockCycling(report);
        if (rapidUnlock != null) candidates.add(rapidUnlock);

        NudgeResult passive = detectPassiveConsumptionSpike(report, screenTimeMs);
        if (passive != null) candidates.add(passive);

        NudgeResult velocity = detectScreenTimeVelocity(report, baseline, screenTimeMs);
        if (velocity != null) candidates.add(velocity);

        NudgeResult appLoop = detectAppLoopPattern(report);
        if (appLoop != null) candidates.add(appLoop);

        NudgeResult isolation = detectIsolationPattern(report, screenTimeMs);
        if (isolation != null) candidates.add(isolation);

        if (candidates.isEmpty()) {
            Log.d(TAG, "No nudge-worthy patterns detected");
            return null;
        }

        // ── Select highest-priority (severity desc, then confidence desc) ──
        NudgeResult best = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            NudgeResult c = candidates.get(i);
            if (c.severity > best.severity ||
                (c.severity == best.severity && c.confidence > best.confidence)) {
                best = c;
            }
        }

        Log.d(TAG, "Nudge selected: " + best);
        return best;
    }

    /**
     * Record that a nudge was delivered (updates cooldown and daily count).
     * Call this <b>after</b> the notification is successfully posted.
     */
    public void recordNudgeDelivered(@NonNull Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        int todayCount = getDailyNudgeCount(prefs, now);

        prefs.edit()
                .putLong(KEY_LAST_NUDGE_TIME, now)
                .putInt(KEY_NUDGE_COUNT_TODAY, todayCount + 1)
                .putLong(KEY_NUDGE_DATE, getStartOfDay(now))
                .apply();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PATTERN DETECTORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detector 1: Rapid Unlock Cycling
     * Signals restless, compulsive phone checking behavior.
     */
    @Nullable
    private NudgeResult detectRapidUnlockCycling(@NonNull BehaviorReport report) {
        if (report.rapidSwitchCount < RAPID_SWITCH_THRESHOLD) return null;

        float conf = Math.min(1.0f, report.rapidSwitchCount / 16.0f);
        int severity = report.rapidSwitchCount >= 14 ? 3 : 2;

        return new NudgeResult(
                "rapid_unlock",
                severity,
                "🔄 Restless scrolling detected",
                "You've been switching between apps rapidly. " +
                        "A 60-second breathing pause can help reset your focus. " +
                        "Try: inhale 4s → hold 4s → exhale 6s.",
                "breathing",
                "1 min",
                conf
        );
    }

    /**
     * Detector 2: Late-Night Acceleration
     * Escalating phone use during hours that disrupt sleep.
     */
    @Nullable
    private NudgeResult detectLateNightAcceleration(@NonNull BehaviorReport report) {
        if (report.lateNightUsageMillis < LATE_NIGHT_MIN_MS) return null;

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        // Only trigger during actual late-night hours
        if (hour >= 5 && hour < LATE_NIGHT_HOUR) return null;
        boolean isDeepNight = hour >= DEEP_NIGHT_HOUR && hour < 5 || hour < LATE_NIGHT_HOUR;

        float conf = Math.min(1.0f, report.lateNightUsageMillis / (30.0f * 60 * 1000));
        int severity = isDeepNight ? 3 : 2;

        String title, body;
        if (isDeepNight) {
            title = "🌙 It's very late";
            body = "Late-night screen time often reflects restlessness. " +
                    "Your body needs rest to recover. Try a 4-7-8 breathing exercise: " +
                    "inhale 4s → hold 7s → exhale 8s. It naturally induces drowsiness.";
        } else {
            title = "🌙 Winding down?";
            body = "We've noticed your screen time picking up late at night. " +
                    "A quick wind-down routine can improve tomorrow's mood. " +
                    "Try setting your phone aside for 10 minutes.";
        }

        return new NudgeResult(
                "late_night",
                severity,
                title, body,
                "breathing",
                "2 min",
                conf
        );
    }

    /**
     * Detector 3: Passive Consumption Spike
     * Extended passive scrolling without active engagement.
     */
    @Nullable
    private NudgeResult detectPassiveConsumptionSpike(@NonNull BehaviorReport report,
                                                      long screenTimeMs) {
        double ratio = report.activeVsPassiveRatio;
        // activeVsPassiveRatio = passive/active, so high ratio = more passive
        double passiveFraction = ratio > 0 ? ratio / (1.0 + ratio) : 0.5;

        if (passiveFraction < PASSIVE_RATIO_THRESHOLD) return null;
        // Need at least 45 min of screen time for this to be meaningful
        if (screenTimeMs < 45L * 60 * 1000) return null;

        float conf = Math.min(1.0f, (float)(passiveFraction - 0.5) * 4.0f);

        return new NudgeResult(
                "passive_spike",
                2,
                "📱 Mindless scrolling detected",
                "You've been passively consuming content for a while. " +
                        "When we scroll without purpose, it often leaves us feeling drained. " +
                        "Try a micro-journal entry: write one sentence about how you're feeling.",
                "journal",
                "1 min",
                conf
        );
    }

    /**
     * Detector 4: App Loop Detection
     * Tight rotation between same 2-3 apps (doom-scrolling pattern).
     */
    @Nullable
    private NudgeResult detectAppLoopPattern(@NonNull BehaviorReport report) {
        if (!report.hasLoopPattern) return null;
        if (report.frequentAppLoops == null || report.frequentAppLoops.isEmpty()) return null;

        float conf = Math.min(1.0f, report.frequentAppLoops.size() / 3.0f);

        return new NudgeResult(
                "app_loop",
                2,
                "🔁 Loop pattern detected",
                "You're cycling between the same apps in a tight loop. " +
                        "This often happens when we're looking for stimulation but not finding satisfaction. " +
                        "Try a 30-second body scan: notice your breathing, shoulders, and jaw tension.",
                "grounding",
                "30 sec",
                conf
        );
    }

    /**
     * Detector 5: Screen Time Velocity
     * Today's usage pace significantly exceeds the 7-day baseline.
     */
    @Nullable
    private NudgeResult detectScreenTimeVelocity(@NonNull BehaviorReport report,
                                                   @Nullable UserBaseline baseline,
                                                   long screenTimeMs) {
        if (baseline == null || baseline.avgScreenTime7d <= 0) return null;

        // Calculate expected screen time at this hour vs. actual
        Calendar cal = Calendar.getInstance();
        int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        if (hourOfDay < 8) return null; // Too early to judge velocity accurately

        double fractionOfDay = hourOfDay / 24.0;
        double expectedAtThisHour = baseline.avgScreenTime7d * fractionOfDay;
        double velocityRatio = screenTimeMs / expectedAtThisHour;

        if (velocityRatio < VELOCITY_THRESHOLD) return null;

        float conf = Math.min(1.0f, (float)((velocityRatio - 1.0) / 0.6));
        int severity = velocityRatio >= 1.8 ? 3 : 2;

        int pctOver = (int)((velocityRatio - 1.0) * 100);

        return new NudgeResult(
                "screen_velocity",
                severity,
                "📊 Screen time pacing high",
                "You're on pace to exceed your usual screen time by ~" + pctOver + "% today. " +
                        "Try a 2-minute stretch break: stand up, roll your shoulders, " +
                        "and take 5 deep breaths. Small pauses add up.",
                "stretch",
                "2 min",
                conf
        );
    }

    /**
     * Detector 6: Emotional Drift Signal (compound)
     * Combination of rapid switches + binge sessions + late-night usage.
     * This compound pattern strongly predicts emerging distress.
     */
    @Nullable
    private NudgeResult detectEmotionalDrift(@NonNull BehaviorReport report) {
        int signals = 0;
        if (report.rapidSwitchCount >= 6) signals++;
        if (report.bingeSessionCount > 0) signals++;
        if (report.lateNightUsageMillis >= LATE_NIGHT_MIN_MS) signals++;
        if (report.hasLoopPattern) signals++;
        if (report.dominantUsageRatio >= 0.65) signals++;

        if (signals < 3) return null; // Need at least 3 converging signals

        float conf = Math.min(1.0f, signals / 4.0f);

        return new NudgeResult(
                "emotional_drift",
                3,
                "💙 Multiple signals converging",
                "We're noticing several patterns that sometimes happen during tough moments: " +
                        "rapid switching, extended sessions, and late-night use. " +
                        "If you're going through something, a grounding exercise might help. " +
                        "5-4-3-2-1: name 5 things you see, 4 you hear, 3 you can touch...",
                "grounding",
                "3 min",
                conf
        );
    }

    /**
     * Detector 7: Isolation Pattern
     * Extended passive use with no productive app engagement.
     */
    @Nullable
    private NudgeResult detectIsolationPattern(@NonNull BehaviorReport report,
                                                long screenTimeMs) {
        // Extended session + high passive ratio + no short productive sessions
        if (report.longestSessionMillis < 60L * 60 * 1000) return null; // Need 1h+ continuous
        if (report.shortSessionCount > 5) return null; // Active task-switching = not isolated
        if (report.activeVsPassiveRatio < 2.0) return null; // Need heavy passive bias

        float conf = Math.min(1.0f,
                (float)(report.longestSessionMillis / (120.0 * 60 * 1000)));

        return new NudgeResult(
                "isolation",
                2,
                "🌿 Long solo session",
                "You've been using your phone continuously for over an hour. " +
                        "Extended solo use can sometimes feel numbing. " +
                        "Consider reaching out to someone — even a quick text to a friend " +
                        "can shift your energy positively.",
                "grounding",
                "1 min",
                conf
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private int getDailyNudgeCount(SharedPreferences prefs, long now) {
        long savedDate = prefs.getLong(KEY_NUDGE_DATE, 0);
        long startOfToday = getStartOfDay(now);
        if (savedDate != startOfToday) {
            return 0; // New day, reset count
        }
        return prefs.getInt(KEY_NUDGE_COUNT_TODAY, 0);
    }

    private long getStartOfDay(long timeMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
