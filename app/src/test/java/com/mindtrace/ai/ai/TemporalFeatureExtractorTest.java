package com.mindtrace.ai.ai;

import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.dao.UsageDao;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link TemporalFeatureExtractor}.
 *
 * Uses manual DAO stubs injected via the testing constructor to verify
 * T1–T6 temporal feature extraction without requiring Room or Android context.
 *
 * @see TemporalFeatureExtractor
 * @see FeatureVector
 */
public class TemporalFeatureExtractorTest {

    private static final float DELTA = 0.02f;
    private static final long DAY_MS = 86400000L;

    private StubUsageDao usageDao;
    private StubQuestionnaireDao questionnaireDao;
    private TemporalFeatureExtractor extractor;

    @Before
    public void setUp() {
        usageDao = new StubUsageDao();
        questionnaireDao = new StubQuestionnaireDao();
        extractor = new TemporalFeatureExtractor(usageDao, questionnaireDao, null);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS — Build test data
    // ═══════════════════════════════════════════════════════════════════

    private static long midnight(int daysAgo) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() - (daysAgo * DAY_MS);
    }

    private static DailyUsage makeUsage(long date, float screenHours) {
        DailyUsage u = new DailyUsage();
        u.date = date;
        u.screenTimeMillis = (long) (screenHours * 3600000L);
        return u;
    }

    private static QuestionnaireResponse makeResponse(long timestamp, String mood, float sleepHours) {
        QuestionnaireResponse r = new QuestionnaireResponse();
        r.timestamp = timestamp;
        r.mood = mood;
        r.sleepHours = sleepHours;
        return r;
    }

    // ═══════════════════════════════════════════════════════════════════
    // T1: SCREEN TIME TREND (3-Day Linear Regression)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void t1_noHistory_returnsDefault() {
        usageDao.usageBetweenResult = Collections.emptyList();
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), midnight(0)).build();
        assertEquals(0.5f, fv.screenTimeTrend3d(), DELTA);
    }

    @Test
    public void t1_increasingUsage_returnsPositive() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today - 2 * DAY_MS, 2.0f),
                makeUsage(today - DAY_MS, 4.0f),
                makeUsage(today, 6.0f)
        );
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertTrue("Increasing trend should be > 0", fv.screenTimeTrend3d() > 0.0f);
        assertTrue("Increasing trend should be <= 1", fv.screenTimeTrend3d() <= 1.0f);
    }

    @Test
    public void t1_decreasingUsage_returnsZero() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today - 2 * DAY_MS, 6.0f),
                makeUsage(today - DAY_MS, 4.0f),
                makeUsage(today, 2.0f)
        );
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("Decreasing trend maps to 0 risk", 0.0f, fv.screenTimeTrend3d(), DELTA);
    }

    @Test
    public void t1_flatUsage_returnsZero() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today - 2 * DAY_MS, 4.0f),
                makeUsage(today - DAY_MS, 4.0f),
                makeUsage(today, 4.0f)
        );
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals(0.0f, fv.screenTimeTrend3d(), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════
    // T2: MOOD STABILITY (7-Day Std Dev)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void t2_stableMood_returnsLowRisk() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        List<QuestionnaireResponse> responses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            responses.add(makeResponse(today - i * DAY_MS, "Neutral", 7));
        }
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("Constant mood → 0 std dev → 0 risk", 0.0f, fv.moodStability7d(), DELTA);
    }

    @Test
    public void t2_volatileMood_returnsHighRisk() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        String[] moods = {"Happy", "Numb", "Happy", "Numb", "Happy"};
        List<QuestionnaireResponse> responses = new ArrayList<>();
        for (int i = 0; i < moods.length; i++) {
            responses.add(makeResponse(today - i * DAY_MS, moods[i], 7));
        }
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertTrue("Volatile mood should yield high risk", fv.moodStability7d() > 0.5f);
    }

    @Test
    public void t2_tooFewResponses_returnsNeutral() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        questionnaireDao.responsesSinceResult = Arrays.asList(
                makeResponse(today, "Happy", 7),
                makeResponse(today - DAY_MS, "Sad", 7)
        );
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("< 3 responses → neutral 0.5", 0.5f, fv.moodStability7d(), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════
    // T3: SLEEP CONSISTENCY (7-Day Std Dev)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void t3_consistentSleep_returnsLowRisk() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        List<QuestionnaireResponse> responses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            responses.add(makeResponse(today - i * DAY_MS, "Neutral", 7.0f));
        }
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("Constant sleep → 0 risk", 0.0f, fv.sleepConsistency7d(), DELTA);
    }

    @Test
    public void t3_erraticSleep_returnsHighRisk() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        float[] sleepHrs = {3.0f, 9.0f, 4.0f, 10.0f, 3.0f};
        List<QuestionnaireResponse> responses = new ArrayList<>();
        for (int i = 0; i < sleepHrs.length; i++) {
            responses.add(makeResponse(today - i * DAY_MS, "Neutral", sleepHrs[i]));
        }
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertTrue("Erratic sleep should yield high risk", fv.sleepConsistency7d() > 0.5f);
    }

    @Test
    public void t3_zeroSleepSkipped_returnsNeutral() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        List<QuestionnaireResponse> responses = Arrays.asList(
                makeResponse(today, "Neutral", 7.0f),
                makeResponse(today - DAY_MS, "Neutral", 0.0f),
                makeResponse(today - 2 * DAY_MS, "Neutral", 0.0f),
                makeResponse(today - 3 * DAY_MS, "Neutral", 8.0f)
        );
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("Only 2 valid sleep entries → neutral", 0.5f, fv.sleepConsistency7d(), DELTA);
    }

    @Test
    public void t3_mildVariation_returnsLowRisk() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        float[] sleepHrs = {7.0f, 7.5f, 6.5f, 7.0f, 7.2f};
        List<QuestionnaireResponse> responses = new ArrayList<>();
        for (int i = 0; i < sleepHrs.length; i++) {
            responses.add(makeResponse(today - i * DAY_MS, "Neutral", sleepHrs[i]));
        }
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertTrue("Mild sleep variation should be low risk (<0.2), was " + fv.sleepConsistency7d(),
                fv.sleepConsistency7d() < 0.2f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // T4: WEEKEND VS WEEKDAY USAGE RATIO
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void t4_noHistory_returnsNeutral() {
        usageDao.usageBetweenResult = Collections.emptyList();
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), midnight(0)).build();
        assertEquals("No usage data → neutral", 0.5f, fv.weekendWeekdayRatio(), DELTA);
    }

    @Test
    public void t4_insufficientData_returnsNeutral() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today, 3.0f),
                makeUsage(today - DAY_MS, 3.0f)
        );
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("< 3 usage days → neutral", 0.5f, fv.weekendWeekdayRatio(), DELTA);
    }

    @Test
    public void t4_balancedUsage_returnsLowRisk() {
        // Build 7 days of equal usage spanning a full week
        long today = midnight(0);
        List<DailyUsage> history = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            history.add(makeUsage(today - i * DAY_MS, 4.0f));
        }
        usageDao.usageBetweenResult = history;
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        // Ratio = 1.0 → (1.0 - 1.0) / (2.0 - 1.0) = 0.0 risk
        assertTrue("Balanced weekend/weekday should be low risk, was " + fv.weekendWeekdayRatio(),
                fv.weekendWeekdayRatio() <= 0.15f);
    }

    @Test
    public void t4_heavyWeekendUsage_returnsHighRisk() {
        // Build data where weekends have 3x weekday usage
        long today = midnight(0);
        List<DailyUsage> history = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            long date = today - i * DAY_MS;
            cal.setTimeInMillis(date);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            boolean isWeekend = (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY);
            history.add(makeUsage(date, isWeekend ? 9.0f : 3.0f));
        }
        usageDao.usageBetweenResult = history;
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertTrue("3x weekend usage should yield high risk (>0.5), was " + fv.weekendWeekdayRatio(),
                fv.weekendWeekdayRatio() > 0.5f);
    }

    @Test
    public void t4_onlyWeekdaysOrWeekends_returnsNeutral() {
        // If all days fall on weekdays (no weekend data), should be neutral
        long today = midnight(0);
        Calendar cal = Calendar.getInstance();
        List<DailyUsage> weekdayOnly = new ArrayList<>();
        // Find 3 consecutive weekdays
        for (int i = 0; i < 14; i++) {
            long date = today - i * DAY_MS;
            cal.setTimeInMillis(date);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY) {
                weekdayOnly.add(makeUsage(date, 5.0f));
                if (weekdayOnly.size() >= 3) break;
            }
        }
        usageDao.usageBetweenResult = weekdayOnly;
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("No weekend data → neutral", 0.5f, fv.weekendWeekdayRatio(), DELTA);
    }

    @Test
    public void t4_zeroScreenTimeDaysSkipped() {
        long today = midnight(0);
        List<DailyUsage> history = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            // Mix in zero-screen-time days that should be filtered out
            float hours = (i % 3 == 0) ? 0.0f : 4.0f;
            history.add(makeUsage(today - i * DAY_MS, hours));
        }
        usageDao.usageBetweenResult = history;
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        // Should still compute a valid result (not crash) from filtered data
        float ratio = fv.weekendWeekdayRatio();
        assertTrue("T4 should be in [0,1], was " + ratio, ratio >= 0.0f && ratio <= 1.0f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // T5: POST-CHECK-IN BEHAVIOUR CHANGE
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void t5_noCheckIn_returnsNeutral() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today, 4.0f),
                makeUsage(today - DAY_MS, 3.0f)
        );
        // No questionnaire responses
        questionnaireDao.responsesSinceResult = Collections.emptyList();
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("No check-in → neutral", 0.5f, fv.postCheckInChange(), DELTA);
    }

    @Test
    public void t5_decreasedAfterCheckIn_returnsZeroRisk() {
        long today = midnight(0);
        // Screen time dropped after check-in day
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today, 2.0f),           // check-in day: 2h
                makeUsage(today - DAY_MS, 5.0f)   // previous day: 5h
        );
        questionnaireDao.responsesSinceResult = Arrays.asList(
                makeResponse(today, "Neutral", 7)  // check-in on today
        );
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("Decreased usage after check-in → awareness working → 0 risk",
                0.0f, fv.postCheckInChange(), DELTA);
    }

    @Test
    public void t5_increasedAfterCheckIn_returnsPositiveRisk() {
        long today = midnight(0);
        // Screen time increased after check-in
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today, 5.0f),            // check-in day: 5h (300min)
                makeUsage(today - DAY_MS, 3.0f)    // previous day: 3h (180min)
        );
        questionnaireDao.responsesSinceResult = Arrays.asList(
                makeResponse(today, "Neutral", 7)
        );
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        float t5 = fv.postCheckInChange();
        // Increase of 120 minutes / 90 max = 1.33 → clamped to 1.0
        assertTrue("Increased usage should yield positive risk, was " + t5, t5 > 0.0f);
        assertTrue("T5 should be clamped to [0,1], was " + t5, t5 <= 1.0f);
    }

    @Test
    public void t5_largeIncrease_saturatesAtOne() {
        long today = midnight(0);
        // Huge increase: 1h → 6h = +300min >> 90min max
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today, 6.0f),
                makeUsage(today - DAY_MS, 1.0f)
        );
        questionnaireDao.responsesSinceResult = Arrays.asList(
                makeResponse(today, "Neutral", 7)
        );
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("300min increase → saturates at 1.0", 1.0f, fv.postCheckInChange(), DELTA);
    }

    @Test
    public void t5_noPreviousDayUsage_returnsNeutral() {
        long today = midnight(0);
        // Only today's usage exists, no previous day to compare
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today, 4.0f)
        );
        questionnaireDao.responsesSinceResult = Arrays.asList(
                makeResponse(today, "Neutral", 7)
        );
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("No previous day → neutral", 0.5f, fv.postCheckInChange(), DELTA);
    }

    @Test
    public void t5_zeroPreviousDayScreenTime_returnsNeutral() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today, 4.0f),
                makeUsage(today - DAY_MS, 0.0f)  // zero screen time
        );
        questionnaireDao.responsesSinceResult = Arrays.asList(
                makeResponse(today, "Neutral", 7)
        );
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("Zero previous screen time → neutral", 0.5f, fv.postCheckInChange(), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════
    // T6: RECOVERY SPEED
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void t6_noSadDays_returnsZeroRisk() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        List<QuestionnaireResponse> responses = Arrays.asList(
                makeResponse(today, "Happy", 7),
                makeResponse(today - DAY_MS, "Happy", 7),
                makeResponse(today - 2 * DAY_MS, "Neutral", 7)
        );
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("No sad days → resilient → 0 risk", 0.0f, fv.recoverySpeed(), DELTA);
    }

    @Test
    public void t6_stillSad_returnsMaxRisk() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        List<QuestionnaireResponse> responses = Arrays.asList(
                makeResponse(today, "Sad", 5),
                makeResponse(today - DAY_MS, "Sad", 5),
                makeResponse(today - 2 * DAY_MS, "Sad", 5)
        );
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("Still sad → max risk", 1.0f, fv.recoverySpeed(), DELTA);
    }

    @Test
    public void t6_fastRecovery_returnsLowRisk() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        List<QuestionnaireResponse> responses = Arrays.asList(
                makeResponse(today, "Happy", 7),
                makeResponse(today - DAY_MS, "Sad", 5),
                makeResponse(today - 2 * DAY_MS, "Neutral", 7)
        );
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        float recovery = fv.recoverySpeed();
        assertTrue("1-day recovery should be low risk (<0.3), was " + recovery, recovery < 0.3f);
    }

    @Test
    public void t6_anxiousTriggersSameAsStad() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        // Anxious is also a negative mood that triggers recovery tracking
        List<QuestionnaireResponse> responses = Arrays.asList(
                makeResponse(today, "Anxious", 6),
                makeResponse(today - DAY_MS, "Anxious", 5),
                makeResponse(today - 2 * DAY_MS, "Neutral", 7)
        );
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("Still anxious → max risk", 1.0f, fv.recoverySpeed(), DELTA);
    }

    @Test
    public void t6_singleResponse_returnsNeutral() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        questionnaireDao.responsesSinceResult = Arrays.asList(
                makeResponse(today, "Sad", 5)
        );
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("<2 responses → neutral", 0.5f, fv.recoverySpeed(), DELTA);
    }

    @Test
    public void t6_slowRecovery_returnsHighRisk() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        // Happy today, but Sad was 6 days ago → slow recovery
        List<QuestionnaireResponse> responses = Arrays.asList(
                makeResponse(today, "Happy", 7),
                makeResponse(today - DAY_MS, "Neutral", 7),
                makeResponse(today - 2 * DAY_MS, "Neutral", 6),
                makeResponse(today - 3 * DAY_MS, "Neutral", 6),
                makeResponse(today - 4 * DAY_MS, "Neutral", 6),
                makeResponse(today - 5 * DAY_MS, "Sad", 5),
                makeResponse(today - 6 * DAY_MS, "Happy", 7)
        );
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        float recovery = fv.recoverySpeed();
        assertTrue("5-day recovery should be high risk (>0.5), was " + recovery, recovery > 0.5f);
    }

    @Test
    public void t6_numbTriggersSameAsSad() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Collections.emptyList();
        // Numb is also a negative trigger mood
        List<QuestionnaireResponse> responses = Arrays.asList(
                makeResponse(today, "Numb", 5),
                makeResponse(today - DAY_MS, "Numb", 5),
                makeResponse(today - 2 * DAY_MS, "Happy", 7)
        );
        questionnaireDao.responsesSinceResult = responses;
        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();
        assertEquals("Still numb → max risk", 1.0f, fv.recoverySpeed(), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTEGRATION: Full extract sets all T1–T6
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void extract_setsAllTemporalFeatures() {
        long today = midnight(0);
        usageDao.usageBetweenResult = Arrays.asList(
                makeUsage(today - 2 * DAY_MS, 3.0f),
                makeUsage(today - DAY_MS, 4.0f),
                makeUsage(today, 5.0f)
        );
        List<QuestionnaireResponse> responses = new ArrayList<>();
        String[] moods = {"Happy", "Neutral", "Sad", "Happy", "Calm"};
        float[] sleep = {7, 6, 8, 7, 5};
        for (int i = 0; i < moods.length; i++) {
            responses.add(makeResponse(today - i * DAY_MS, moods[i], sleep[i]));
        }
        questionnaireDao.responsesSinceResult = responses;

        FeatureVector fv = extractor.extract(new FeatureVector.Builder(), today).build();

        // All temporal features should be in [0, 1]
        for (int i = FeatureVector.IDX_T1; i <= FeatureVector.IDX_T6; i++) {
            float v = fv.get(i);
            assertTrue("T" + (i - FeatureVector.IDX_T1 + 1) + " should be in [0,1], was " + v,
                    v >= 0.0f && v <= 1.0f);
        }
    }

    @Test
    public void extract_exceptionFallsBackToDefaults() {
        // Force an exception by providing a DAO that throws
        UsageDao throwingDao = new StubUsageDao() {
            @Override
            public List<DailyUsage> getUsageBetween(long start, long end) {
                throw new RuntimeException("DB error");
            }
        };
        TemporalFeatureExtractor safeExtractor =
                new TemporalFeatureExtractor(throwingDao, questionnaireDao, null);
        FeatureVector fv = safeExtractor.extract(new FeatureVector.Builder(), midnight(0)).build();

        // All temporal features should be at default 0.5
        for (int i = FeatureVector.IDX_T1; i <= FeatureVector.IDX_T6; i++) {
            assertEquals("T" + (i - FeatureVector.IDX_T1 + 1) + " should default to 0.5",
                    0.5f, fv.get(i), DELTA);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLASSIFIER WEIGHT VALIDATION (Phase 3 gate)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void classifierWeights_allSumToOne() {
        assertTrue("All classifier weight sums must be ~1.0",
                MultiModalClassifier.validateWeights());
    }

    // ═══════════════════════════════════════════════════════════════════
    // STUB DAOs — Manual mocks for isolated testing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Minimal stub for UsageDao. Only implements methods used by
     * TemporalFeatureExtractor; all others throw UnsupportedOperationException.
     */
    static class StubUsageDao implements UsageDao {
        List<DailyUsage> usageBetweenResult = Collections.emptyList();

        @Override public List<DailyUsage> getUsageBetween(long start, long end) {
            return usageBetweenResult;
        }

        // ── Unused methods — safe stubs ──────────────────────────────
        @Override public void insert(DailyUsage u) {}
        @Override public long insertOrReplace(DailyUsage u) { return 0; }
        @Override public void update(DailyUsage u) {}
        @Override public void deleteAll() {}
        @Override public DailyUsage getUsageForDay(long ts) { return null; }
        @Override public androidx.lifecycle.LiveData<DailyUsage> observeUsageForDay(long ts) { return null; }
        @Override public DailyUsage getLatestUsage() { return null; }
        @Override public androidx.lifecycle.LiveData<DailyUsage> observeLatestUsage() { return null; }
        @Override public androidx.lifecycle.LiveData<List<DailyUsage>> observeAllUsage() { return null; }
        @Override public List<DailyUsage> getUsageSince(long since) { return Collections.emptyList(); }
        @Override public androidx.lifecycle.LiveData<List<DailyUsage>> observeUsageSince(long since) { return null; }
        @Override public List<DailyUsage> getRecentUsage(int limit) { return Collections.emptyList(); }
        @Override public androidx.lifecycle.LiveData<List<DailyUsage>> observeRecentUsage(int limit) { return null; }
        @Override public double getAvgScreenTime(int days) { return 0; }
        @Override public double getAvgUnlockCount(int days) { return 0; }
        @Override public double getAvgAppSwitchCount(int days) { return 0; }
        @Override public double getAvgNightUsage(int days) { return 0; }
        @Override public double getAvgPassiveRatio(int days) { return 0; }
        @Override public long getMaxScreenTime(int days) { return 0; }
        @Override public long getMinScreenTime(int days) { return 0; }
        @Override public int getTotalTrackedDays() { return 0; }
        @Override public List<DailyUsage> getDaysWithHighNightUsage(long t, int l) { return Collections.emptyList(); }
        @Override public List<DailyUsage> getDaysWithHighUnlocks(int t, int l) { return Collections.emptyList(); }
        @Override public List<DailyUsage> getDaysWithHighScreenTime(long t, int l) { return Collections.emptyList(); }
        @Override public List<DailyUsage> getDaysWithHighPassiveRatio(float t, int l) { return Collections.emptyList(); }
        @Override public DailyUsage getPeakUsageDay() { return null; }
        @Override public DailyUsage getBestUsageDay() { return null; }
        @Override public double getAvgSocialMediaTime(int days) { return 0; }
        @Override public double getAvgEntertainmentTime(int days) { return 0; }
        @Override public double getAvgProductiveTime(int days) { return 0; }
        @Override public List<DailyUsage> getScreenTimeTrend(long since) { return Collections.emptyList(); }
        @Override public double getAvgFirstUnlockTime(int days) { return 0; }
        @Override public double getAvgLastScreenOffTime(int days) { return 0; }
        @Override public int countForDay(long ts) { return 0; }
    }

    /**
     * Minimal stub for QuestionnaireDao. Only implements getResponsesSinceSync.
     */
    static class StubQuestionnaireDao implements QuestionnaireDao {
        List<QuestionnaireResponse> responsesSinceResult = Collections.emptyList();

        @Override public List<QuestionnaireResponse> getResponsesSinceSync(long since) {
            return responsesSinceResult;
        }

        // ── Unused methods — safe stubs ──────────────────────────────
        @Override public void insert(QuestionnaireResponse r) {}
        @Override public long insertAndReturnId(QuestionnaireResponse r) { return 0; }
        @Override public void update(QuestionnaireResponse r) {}
        @Override public void deleteById(int id) {}
        @Override public androidx.lifecycle.LiveData<List<QuestionnaireResponse>> getAllResponses() { return null; }
        @Override public List<QuestionnaireResponse> getAllResponsesSync() { return Collections.emptyList(); }
        @Override public List<QuestionnaireResponse> getRecentResponses(int l) { return Collections.emptyList(); }
        @Override public List<QuestionnaireResponse> getRecentResponses() { return Collections.emptyList(); }
        @Override public List<QuestionnaireResponse> getResponsesBetweenSync(long s, long e) { return Collections.emptyList(); }
        @Override public QuestionnaireResponse getLatestResponseSync() { return null; }
        @Override public androidx.lifecycle.LiveData<QuestionnaireResponse> getLatestResponse() { return null; }
        @Override public List<QuestionnaireResponse> getResponsesForDay(long d) { return Collections.emptyList(); }
        @Override public QuestionnaireResponse getLatestMorningCheckIn() { return null; }
        @Override public QuestionnaireResponse getLatestEveningCheckIn() { return null; }
        @Override public int getTotalCheckInCount() { return 0; }
        @Override public int getDistinctCheckInDays() { return 0; }
        @Override public List<String> getMoodHistory(int l) { return Collections.emptyList(); }
        @Override public List<MoodCount> getMoodDistribution(long s) { return Collections.emptyList(); }
        @Override public int getDaysWithMood(String m) { return 0; }
        @Override public List<QuestionnaireResponse> getNegativeMoodResponses() { return Collections.emptyList(); }
        @Override public float getAvgStressLevel(int l) { return 0; }
        @Override public float getAvgAnxietyLevel(int l) { return 0; }
        @Override public List<QuestionnaireResponse> getHighDistressResponses(float t) { return Collections.emptyList(); }
        @Override public List<QuestionnaireResponse> getSupportRequestResponses() { return Collections.emptyList(); }
        @Override public List<QuestionnaireResponse> getDistressFlaggedResponses() { return Collections.emptyList(); }
        @Override public int getHighDistressDayCount(long s, float t) { return 0; }
        @Override public float getAvgDistressSeverity(int l) { return 0; }
        @Override public float getPeakDistressSeverity() { return 0; }
        @Override public List<QuestionnaireResponse> getCryingResponses(int l) { return Collections.emptyList(); }
        @Override public List<QuestionnaireResponse> getWithdrawalResponses(int l) { return Collections.emptyList(); }
        @Override public float getAvgHopeLevel(int l) { return 0; }
        @Override public float getAvgSelfWorth(int l) { return 0; }
        @Override public float getAvgMotivation(int l) { return 0; }
        @Override public float getAvgLoneliness(int l) { return 0; }
        @Override public float getAvgSleepHours(int l) { return 0; }
        @Override public float getAvgSleepQuality(int l) { return 0; }
        @Override public float getAvgPurposeScore(int l) { return 0; }
        @Override public float getAvgMentalClarity(int l) { return 0; }
        @Override public float getAvgRumination(int l) { return 0; }
        @Override public int getExerciseDayCount(long s) { return 0; }
        @Override public List<CopingCount> getCopingDistribution(long s) { return Collections.emptyList(); }
        @Override public List<Long> getCheckInDayTimestamps() { return Collections.emptyList(); }
        @Override public List<QuestionnaireResponse> getPoorSleepResponses(float t) { return Collections.emptyList(); }
        @Override public List<QuestionnaireResponse> getHighStressResponses(int l) { return Collections.emptyList(); }
        @Override public Long getLatestHighStressTimestamp() { return null; }
        @Override public int getLatestStressLevel() { return 0; }
        @Override public List<Integer> getSelfWorthTrend(int l) { return Collections.emptyList(); }
        @Override public List<Integer> getHopeTrend(int l) { return Collections.emptyList(); }
        @Override public List<Integer> getStressTrend(int l) { return Collections.emptyList(); }
        @Override public List<Integer> getMotivationTrend(int l) { return Collections.emptyList(); }
        @Override public List<String> getRecentMoods(int l) { return Collections.emptyList(); }
        @Override public int getCheckInCountForDay(long d) { return 0; }
        @Override public QuestionnaireResponse getClosestResponseBefore(long t) { return null; }
        @Override public int getCountSince(long s) { return 0; }
    }
}
