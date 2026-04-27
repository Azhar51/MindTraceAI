package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.dao.UsageDao;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.util.MoodMapper;

import java.util.Calendar;
import java.util.List;

/**
 * Extracts the 6 Temporal features (T1–T6) from multi-day historical data.
 *
 * <p>Unlike the core 28 features which capture a single day's snapshot, temporal
 * features detect <b>patterns across time</b>: acceleration, stability, and
 * context-dependent shifts. These are the most powerful predictors of emerging
 * risk because they capture <i>trajectories</i>, not states.</p>
 *
 * <h3>Feature Map:</h3>
 * <pre>
 *   T1  screenTimeTrend3d    — 3-day linear regression slope of screen time
 *   T2  moodStability7d     — 7-day mood score standard deviation (inverted)
 *   T3  sleepConsistency7d  — 7-day sleep hours standard deviation (inverted)
 *   T4  weekendWeekdayRatio — weekend avg / weekday avg screen time ratio
 *   T5  postCheckInChange   — usage change in 2h after check-in (awareness)
 *   T6  recoverySpeed       — days from last "Sad" to "Neutral/Happy"
 * </pre>
 *
 * <h3>Normalization:</h3>
 * <p>All features are normalized to [0.0, 1.0] where <b>higher = greater risk</b>,
 * consistent with the core FeatureVector convention.</p>
 *
 * <h3>Blueprint Reference:</h3>
 * <p>Part 3C §9 — Temporal Features (Phase 3+ Enhancement)</p>
 *
 * @see FeatureVector
 * @see FeatureVector.Builder
 * @see MultiModalClassifier
 */
public class TemporalFeatureExtractor {

    private static final String TAG = "TemporalFE";

    // ═══════════════════════════════════════════════════════════════════
    // NORMALIZATION THRESHOLDS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * T1: Max screen time increase (hours/day) that maps to 1.0.
     * A 3-hour daily increase over 3 days = maximum acceleration risk.
     */
    private static final float MAX_SCREEN_TIME_SLOPE_HOURS = 3.0f;

    /**
     * T2: Max mood std dev that maps to 1.0.
     * A std dev of 0.35 on a 0-1 mood scale = extreme volatility.
     */
    private static final float MAX_MOOD_STDDEV = 0.35f;

    /**
     * T3: Max sleep std dev that maps to 1.0.
     * A std dev of 3 hours = severely erratic sleep.
     */
    private static final float MAX_SLEEP_STDDEV = 3.0f;

    /**
     * T4: Weekend/weekday ratio threshold for max risk.
     * 2.0 = weekend usage is double weekday = very context-dependent.
     */
    private static final float MAX_WE_WD_RATIO = 2.0f;

    /**
     * T5: Max post-check-in screen time increase (minutes) for max risk.
     * If user gains 90+ minutes of screen time after check-in = no awareness.
     */
    private static final float MAX_POST_CHECKIN_INCREASE_MINS = 90.0f;

    /**
     * T6: Max recovery days that maps to 1.0 (slowest recovery = most risk).
     * 7+ days to recover from Sad = very slow recovery.
     */
    private static final float MAX_RECOVERY_DAYS = 7.0f;

    /** How many days of history for screen time trend. */
    private static final int TREND_WINDOW_DAYS = 3;

    /** How many days of history for stability measures. */
    private static final int STABILITY_WINDOW_DAYS = 7;

    // ═══════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════

    private final UsageDao usageDao;
    private final QuestionnaireDao questionnaireDao;
    private final RiskClassificationDao riskDao;

    /**
     * Create extractor with application context.
     * @param context Android context (used to access the database)
     */
    public TemporalFeatureExtractor(@NonNull Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.usageDao = db.usageDao();
        this.questionnaireDao = db.questionnaireDao();
        this.riskDao = db.riskClassificationDao();
    }

    /**
     * Create extractor with DAOs (for testing/injection).
     */
    public TemporalFeatureExtractor(@NonNull UsageDao usageDao,
                                     @NonNull QuestionnaireDao questionnaireDao,
                                     @Nullable RiskClassificationDao riskDao) {
        this.usageDao = usageDao;
        this.questionnaireDao = questionnaireDao;
        this.riskDao = riskDao;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAIN EXTRACTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extract all 6 temporal features and apply them to the FeatureVector builder.
     *
     * @param builder      The FeatureVector.Builder to populate (T1–T6)
     * @param dayTimestamp  Midnight timestamp of the target day
     * @return The same builder (for chaining), with T1–T6 set
     */
    @NonNull
    public FeatureVector.Builder extract(@NonNull FeatureVector.Builder builder,
                                          long dayTimestamp) {
        try {
            // Load 7 days of history (covers both 3-day and 7-day windows)
            long since7d = dayTimestamp - (STABILITY_WINDOW_DAYS * 86400000L);
            List<DailyUsage> usageHistory = usageDao.getUsageBetween(since7d, dayTimestamp);
            List<QuestionnaireResponse> responses = questionnaireDao.getResponsesSinceSync(since7d);

            // ── T1: Screen Time 3-Day Trend ─────────────────────────
            float screenTimeTrend = computeScreenTimeTrend(usageHistory, dayTimestamp);
            builder.t1_screenTimeTrend3d(screenTimeTrend);

            // ── T2: Mood Stability (7-day) ──────────────────────────
            float moodStability = computeMoodStability(responses);
            builder.t2_moodStability7d(moodStability);

            // ── T3: Sleep Consistency (7-day) ───────────────────────
            float sleepConsistency = computeSleepConsistency(responses);
            builder.t3_sleepConsistency7d(sleepConsistency);

            // ── T4: Weekend vs Weekday Ratio ────────────────────────
            float weekendRatio = computeWeekendWeekdayRatio(usageHistory);
            builder.t4_weekendWeekdayRatio(weekendRatio);

            // ── T5: Post-Check-In Behaviour Change ──────────────────
            float postCheckIn = computePostCheckInChange(usageHistory, responses, dayTimestamp);
            builder.t5_postCheckInChange(postCheckIn);

            // ── T6: Recovery Speed ──────────────────────────────────
            float recoverySpeed = computeRecoverySpeed(responses);
            builder.t6_recoverySpeed(recoverySpeed);

            Log.d(TAG, String.format(
                    "Extracted T1-T6: trend=%.2f, moodStab=%.2f, sleepCon=%.2f, " +
                            "weRatio=%.2f, postCI=%.2f, recovery=%.2f",
                    screenTimeTrend, moodStability, sleepConsistency,
                    weekendRatio, postCheckIn, recoverySpeed));

        } catch (Exception e) {
            Log.w(TAG, "Temporal extraction failed: " + e.getMessage());
            // All temporal features remain at default 0.5 (uncertain)
        }

        return builder;
    }

    /**
     * Extract temporal features for today.
     */
    @NonNull
    public FeatureVector.Builder extractToday(@NonNull FeatureVector.Builder builder) {
        return extract(builder, getStartOfTodayMillis());
    }

    // ═══════════════════════════════════════════════════════════════════
    // T1: SCREEN TIME 3-DAY TREND
    // Detects accelerating usage — the most dangerous signal.
    // Uses simple linear regression slope over 3 days.
    // Positive slope = increasing usage = higher risk.
    // ═══════════════════════════════════════════════════════════════════

    private float computeScreenTimeTrend(@Nullable List<DailyUsage> history,
                                          long dayTimestamp) {
        if (history == null || history.size() < 2) return FeatureVector.DEFAULT_VALUE; // Insufficient data → uncertain

        // Take the most recent 3 days only
        long trendStart = dayTimestamp - (TREND_WINDOW_DAYS * 86400000L);
        int n = 0;
        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (DailyUsage usage : history) {
            if (usage.screenTimeMillis <= 0) continue;
            if (usage.date < trendStart) continue;

            float x = n; // day index (0, 1, 2)
            float y = usage.getScreenTimeHours(); // screen time in hours
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            n++;
        }

        if (n < 2) return FeatureVector.DEFAULT_VALUE; // Filtered data insufficient → uncertain

        // Linear regression slope: β = (n·Σxy - Σx·Σy) / (n·Σx² - (Σx)²)
        float denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 0.001f) return 0.0f;

        float slope = (n * sumXY - sumX * sumY) / denom;

        // Normalize: slope in hours/day → [0, 1]
        // Only positive slope = risk (increasing); negative slope = improvement
        return clamp(Math.max(0, slope) / MAX_SCREEN_TIME_SLOPE_HOURS);
    }

    // ═══════════════════════════════════════════════════════════════════
    // T2: MOOD STABILITY (7-Day)
    // High std dev of mood scores = emotional volatility = risk.
    // Low std dev = emotionally steady = low risk.
    // ═══════════════════════════════════════════════════════════════════

    private float computeMoodStability(@Nullable List<QuestionnaireResponse> responses) {
        if (responses == null || responses.size() < 3) return 0.5f; // Neutral

        // Convert moods to float scores
        int count = 0;
        float sum = 0;
        float[] scores = new float[responses.size()];

        for (QuestionnaireResponse r : responses) {
            if (r.mood == null || r.mood.isEmpty()) continue;
            float score = MoodMapper.moodToFloat(r.mood);
            scores[count] = score;
            sum += score;
            count++;
        }

        if (count < 3) return 0.5f;

        float mean = sum / count;

        // Compute standard deviation
        float variance = 0;
        for (int i = 0; i < count; i++) {
            float diff = scores[i] - mean;
            variance += diff * diff;
        }
        float stdDev = (float) Math.sqrt(variance / count);

        // Normalize: high std dev = high risk (volatile mood)
        return clamp(stdDev / MAX_MOOD_STDDEV);
    }

    // ═══════════════════════════════════════════════════════════════════
    // T3: SLEEP CONSISTENCY (7-Day)
    // High std dev of sleep hours = erratic sleep = risk.
    // Consistent sleep = low risk.
    // ═══════════════════════════════════════════════════════════════════

    private float computeSleepConsistency(@Nullable List<QuestionnaireResponse> responses) {
        if (responses == null || responses.size() < 3) return 0.5f;

        int count = 0;
        float sum = 0;
        float[] hours = new float[responses.size()];

        for (QuestionnaireResponse r : responses) {
            if (r.sleepHours <= 0) continue;
            hours[count] = r.sleepHours;
            sum += r.sleepHours;
            count++;
        }

        if (count < 3) return 0.5f;

        float mean = sum / count;
        float variance = 0;
        for (int i = 0; i < count; i++) {
            float diff = hours[i] - mean;
            variance += diff * diff;
        }
        float stdDev = (float) Math.sqrt(variance / count);

        // Normalize: high std dev = erratic sleep = risk
        return clamp(stdDev / MAX_SLEEP_STDDEV);
    }

    // ═══════════════════════════════════════════════════════════════════
    // T4: WEEKEND VS WEEKDAY USAGE RATIO
    // If usage spikes on weekends, behaviour is context-dependent —
    // user may be using phone to cope with unstructured time.
    // Ratio > 1.0 = more weekend usage = higher risk.
    // ═══════════════════════════════════════════════════════════════════

    private float computeWeekendWeekdayRatio(@Nullable List<DailyUsage> history) {
        if (history == null || history.size() < 3) return 0.5f;

        float weekendSum = 0, weekdaySum = 0;
        int weekendCount = 0, weekdayCount = 0;

        Calendar cal = Calendar.getInstance();
        for (DailyUsage usage : history) {
            if (usage.screenTimeMillis <= 0) continue;

            cal.setTimeInMillis(usage.date);
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

            float hours = usage.getScreenTimeHours();
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                weekendSum += hours;
                weekendCount++;
            } else {
                weekdaySum += hours;
                weekdayCount++;
            }
        }

        if (weekdayCount == 0 || weekendCount == 0) return 0.5f;

        float weekendAvg = weekendSum / weekendCount;
        float weekdayAvg = weekdaySum / weekdayCount;

        if (weekdayAvg < 0.1f) return 0.5f; // Avoid division by near-zero

        float ratio = weekendAvg / weekdayAvg;

        // Normalize: ratio 1.0 = balanced (0.0 risk), 2.0+ = max risk
        // Below 1.0 = weekdays are worse (also a concern, but lower)
        return clamp((ratio - 1.0f) / (MAX_WE_WD_RATIO - 1.0f));
    }

    // ═══════════════════════════════════════════════════════════════════
    // T5: POST-CHECK-IN BEHAVIOUR CHANGE
    // Measures if the user's awareness (from a check-in) actually
    // changes their phone behaviour. If screen time INCREASES after
    // a check-in, the user is likely using the phone to cope.
    // ═══════════════════════════════════════════════════════════════════

    private float computePostCheckInChange(@Nullable List<DailyUsage> usageHistory,
                                            @Nullable List<QuestionnaireResponse> responses,
                                            long dayTimestamp) {
        if (usageHistory == null || usageHistory.isEmpty() ||
                responses == null || responses.isEmpty()) return 0.5f;

        // Find the most recent check-in's timestamp
        QuestionnaireResponse latestCheckIn = null;
        for (QuestionnaireResponse r : responses) {
            if (r.timestamp > 0) {
                latestCheckIn = r;
                break; // Responses are sorted newest first
            }
        }
        if (latestCheckIn == null) return 0.5f;

        // Compare screen time on the check-in day vs the day before
        DailyUsage checkInDay = null;
        DailyUsage previousDay = null;
        long checkInDayTs = getStartOfDay(latestCheckIn.timestamp);

        for (DailyUsage u : usageHistory) {
            if (u.date == checkInDayTs) checkInDay = u;
            if (u.date == checkInDayTs - 86400000L) previousDay = u;
        }

        if (checkInDay == null || previousDay == null) return 0.5f;
        if (previousDay.screenTimeMillis <= 0) return 0.5f;

        // Calculate post-check-in increase in minutes
        float checkInMins = checkInDay.screenTimeMillis / 60000f;
        float previousMins = previousDay.screenTimeMillis / 60000f;
        float increaseMins = checkInMins - previousMins;

        // Only positive increase = risk (using phone more after reflecting)
        if (increaseMins <= 0) return 0.0f; // Decrease = awareness is working

        return clamp(increaseMins / MAX_POST_CHECKIN_INCREASE_MINS);
    }

    // ═══════════════════════════════════════════════════════════════════
    // T6: RECOVERY SPEED
    // How many days from the last "Sad/Anxious" mood to "Neutral/Happy"?
    // Fast recovery = resilient = low risk.
    // Slow/no recovery = vulnerability = high risk.
    // ═══════════════════════════════════════════════════════════════════

    private float computeRecoverySpeed(@Nullable List<QuestionnaireResponse> responses) {
        if (responses == null || responses.size() < 2) return 0.5f;

        // Responses are sorted newest-first. Walk backward to find
        // the most recent Sad→Neutral/Happy transition.
        int lastSadIndex = -1;
        int recoveryIndex = -1;

        // First, find the most recent negative mood
        for (int i = 0; i < responses.size(); i++) {
            String mood = responses.get(i).mood;
            if ("Sad".equalsIgnoreCase(mood) || "Anxious".equalsIgnoreCase(mood)
                    || "Numb".equalsIgnoreCase(mood)) {
                lastSadIndex = i;
                break;
            }
        }

        if (lastSadIndex < 0) {
            // No recent sad days — user is resilient
            return 0.0f;
        }

        // Now check if they've recovered (is the mood before lastSadIndex positive?)
        for (int i = 0; i < lastSadIndex; i++) {
            String mood = responses.get(i).mood;
            if ("Happy".equalsIgnoreCase(mood) || "Neutral".equalsIgnoreCase(mood)
                    || "Good".equalsIgnoreCase(mood)) {
                recoveryIndex = i;
                break;
            }
        }

        if (recoveryIndex < 0) {
            // Still in negative mood — not recovered yet
            // Use days since last sad mood as risk
            float daysSinceSad = (float) lastSadIndex;
            return clamp(1.0f); // Maximum risk: still sad
        }

        // Calculate days between sad and recovery
        long sadTimestamp = responses.get(lastSadIndex).timestamp;
        long recoveryTimestamp = responses.get(recoveryIndex).timestamp;
        float recoveryDays = Math.abs(recoveryTimestamp - sadTimestamp) / 86400000f;

        // Normalize: 0 days = instant recovery (0 risk), 7+ days = max risk
        return clamp(recoveryDays / MAX_RECOVERY_DAYS);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Get midnight timestamp for a given epoch millis. */
    private static long getStartOfDay(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Get midnight timestamp for today. */
    private static long getStartOfTodayMillis() {
        return getStartOfDay(System.currentTimeMillis());
    }
}
