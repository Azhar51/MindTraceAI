package com.mindtrace.ai.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link MoodMapper}.
 *
 * Validates:
 *  - moodToFloat() and moodToRisk() mappings for all 7 canonical moods + null
 *  - levelToFloat(), levelToRisk(), levelToNormalized()
 *  - Clinical severity levels, valence, arousal
 *  - Mood analysis: entropy, velocity, stability, trajectory
 *  - Streak-amplified risk, composite emotional risk
 *  - Weekly summary generation
 */
public class MoodMapperTest {

    private static final float DELTA = 0.01f;

    // ═══════════════════════════════════════════════════════════════════
    // moodToFloat() — wellbeing scale [0.0, 4.0]
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void moodToFloat_happyReturns4() {
        assertEquals(4.0f, MoodMapper.moodToFloat("Happy"), DELTA);
    }

    @Test
    public void moodToFloat_calmReturns3_5() {
        assertEquals(3.5f, MoodMapper.moodToFloat("Calm"), DELTA);
    }

    @Test
    public void moodToFloat_neutralReturns3() {
        assertEquals(3.0f, MoodMapper.moodToFloat("Neutral"), DELTA);
    }

    @Test
    public void moodToFloat_anxiousReturns1_5() {
        assertEquals(1.5f, MoodMapper.moodToFloat("Anxious"), DELTA);
    }

    @Test
    public void moodToFloat_sadReturns1() {
        assertEquals(1.0f, MoodMapper.moodToFloat("Sad"), DELTA);
    }

    @Test
    public void moodToFloat_angryReturns1_5() {
        assertEquals(1.5f, MoodMapper.moodToFloat("Angry"), DELTA);
    }

    @Test
    public void moodToFloat_numbReturns0_5() {
        assertEquals(0.5f, MoodMapper.moodToFloat("Numb"), DELTA);
    }

    @Test
    public void moodToFloat_nullReturnsDefault() {
        assertEquals(2.5f, MoodMapper.moodToFloat(null), DELTA);
    }

    @Test
    public void moodToFloat_unknownReturnsDefault() {
        assertEquals(2.5f, MoodMapper.moodToFloat("Surprised"), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════
    // moodToRisk() — risk scale [0.0, 1.0]
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void moodToRisk_happyIsZero() {
        assertEquals(0.0f, MoodMapper.moodToRisk("Happy"), DELTA);
    }

    @Test
    public void moodToRisk_calmIsLow() {
        assertEquals(0.10f, MoodMapper.moodToRisk("Calm"), DELTA);
    }

    @Test
    public void moodToRisk_sadIsHigh() {
        assertEquals(0.85f, MoodMapper.moodToRisk("Sad"), DELTA);
    }

    @Test
    public void moodToRisk_numbIsHighest() {
        assertEquals(0.90f, MoodMapper.moodToRisk("Numb"), DELTA);
    }

    @Test
    public void moodToRisk_nullReturnsDefault() {
        assertEquals(0.50f, MoodMapper.moodToRisk(null), DELTA);
    }

    @Test
    public void moodToRisk_monotonicWithFloat() {
        // Higher wellbeing → lower risk (inverse relationship)
        assertTrue(MoodMapper.moodToRisk("Happy") < MoodMapper.moodToRisk("Neutral"));
        assertTrue(MoodMapper.moodToRisk("Neutral") < MoodMapper.moodToRisk("Sad"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // levelToFloat() / levelToRisk() / levelToNormalized()
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void levelToFloat_highIs3() {
        assertEquals(3.0f, MoodMapper.levelToFloat("High"), DELTA);
    }

    @Test
    public void levelToFloat_lowIs1() {
        assertEquals(1.0f, MoodMapper.levelToFloat("Low"), DELTA);
    }

    @Test
    public void levelToRisk_lowIsMaxRisk() {
        assertEquals(1.0f, MoodMapper.levelToRisk("Low"), DELTA);
    }

    @Test
    public void levelToRisk_highIsZeroRisk() {
        assertEquals(0.0f, MoodMapper.levelToRisk("High"), DELTA);
    }

    @Test
    public void levelToNormalized_coversFullRange() {
        assertEquals(1.0f, MoodMapper.levelToNormalized("High"), DELTA);
        assertEquals(0.5f, MoodMapper.levelToNormalized("Medium"), DELTA);
        assertEquals(0.0f, MoodMapper.levelToNormalized("Low"), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Clinical severity
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void clinicalSeverity_happyIsNone() {
        assertEquals(0, MoodMapper.getClinicalSeverity("Happy"));
    }

    @Test
    public void clinicalSeverity_anxiousIsModerate() {
        assertEquals(2, MoodMapper.getClinicalSeverity("Anxious"));
    }

    @Test
    public void clinicalSeverity_sadIsSevere() {
        assertEquals(3, MoodMapper.getClinicalSeverity("Sad"));
    }

    @Test
    public void clinicalSeverity_numbIsSevere() {
        assertEquals(3, MoodMapper.getClinicalSeverity("Numb"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Mood classification groups
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void isNegativeMood_sadTrue() {
        assertTrue(MoodMapper.isNegativeMood("Sad"));
    }

    @Test
    public void isNegativeMood_happyFalse() {
        assertFalse(MoodMapper.isNegativeMood("Happy"));
    }

    @Test
    public void isClinicalAlertMood_numbTrue() {
        assertTrue(MoodMapper.isClinicalAlertMood("Numb"));
    }

    @Test
    public void isClinicalAlertMood_anxiousFalse() {
        assertFalse(MoodMapper.isClinicalAlertMood("Anxious"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Mood entropy (Shannon entropy, normalized)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getMoodEntropy_singleMoodIsZero() {
        List<String> moods = Arrays.asList("Happy", "Happy", "Happy", "Happy");
        assertEquals(0f, MoodMapper.getMoodEntropy(moods), DELTA);
    }

    @Test
    public void getMoodEntropy_allDifferentIsHigh() {
        List<String> moods = Arrays.asList("Happy", "Sad", "Anxious", "Calm", "Neutral", "Angry", "Numb");
        float entropy = MoodMapper.getMoodEntropy(moods);
        assertTrue("All-different entropy should be >= 0.9, was " + entropy, entropy >= 0.9f);
    }

    @Test
    public void getMoodEntropy_nullReturnsZero() {
        assertEquals(0f, MoodMapper.getMoodEntropy(null), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Mood velocity (linear regression slope)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getMoodVelocity_improvingIsPositive() {
        // Oldest → newest: getting better
        List<String> moods = Arrays.asList("Sad", "Anxious", "Neutral", "Calm", "Happy");
        float velocity = MoodMapper.getMoodVelocity(moods);
        assertTrue("Improving velocity should be positive, was " + velocity, velocity > 0);
    }

    @Test
    public void getMoodVelocity_decliningIsNegative() {
        // Oldest → newest: getting worse
        List<String> moods = Arrays.asList("Happy", "Calm", "Neutral", "Anxious", "Sad");
        float velocity = MoodMapper.getMoodVelocity(moods);
        assertTrue("Declining velocity should be negative, was " + velocity, velocity < 0);
    }

    @Test
    public void getMoodVelocity_stableIsNearZero() {
        List<String> moods = Arrays.asList("Neutral", "Neutral", "Neutral", "Neutral");
        assertEquals(0f, MoodMapper.getMoodVelocity(moods), DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Mood trajectory
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getMoodTrajectory_improvingDetected() {
        // First half low, second half high
        List<String> moods = Arrays.asList("Sad", "Sad", "Anxious", "Neutral", "Calm", "Happy");
        assertEquals("improving", MoodMapper.getMoodTrajectory(moods));
    }

    @Test
    public void getMoodTrajectory_decliningDetected() {
        List<String> moods = Arrays.asList("Happy", "Calm", "Neutral", "Anxious", "Sad", "Sad");
        assertEquals("declining", MoodMapper.getMoodTrajectory(moods));
    }

    @Test
    public void getMoodTrajectory_stableDetected() {
        List<String> moods = Arrays.asList("Neutral", "Neutral", "Neutral", "Neutral", "Neutral");
        assertEquals("stable", MoodMapper.getMoodTrajectory(moods));
    }

    @Test
    public void getMoodTrajectory_tooFewDefaultsStable() {
        assertEquals("stable", MoodMapper.getMoodTrajectory(Arrays.asList("Happy")));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Streak-amplified risk
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getStreakAmplifiedRisk_day1SadEqualsBase() {
        float risk = MoodMapper.getStreakAmplifiedRisk("Sad", Collections.emptyList());
        // Day 1 → streak=1 → multiplier=1.1
        assertEquals(0.85f * 1.1f, risk, DELTA);
    }

    @Test
    public void getStreakAmplifiedRisk_multipleDaysAmplifies() {
        List<String> history = Arrays.asList("Sad", "Sad", "Sad"); // oldest first, 3 past days sad
        float risk = MoodMapper.getStreakAmplifiedRisk("Sad", history);
        // streak = 3 (history) + 1 (today) = 4 → multiplier = 1.4
        float expected = Math.min(1.0f, 0.85f * 1.4f);
        assertEquals(expected, risk, DELTA);
    }

    @Test
    public void getStreakAmplifiedRisk_cappedAt1() {
        // 10 days of sad → multiplier = 2.0 (capped), 0.85 * 2.0 = 1.7 → capped at 1.0
        List<String> history = Arrays.asList("Sad","Sad","Sad","Sad","Sad","Sad","Sad","Sad","Sad","Sad");
        float risk = MoodMapper.getStreakAmplifiedRisk("Numb", history);
        assertEquals(1.0f, risk, DELTA);
    }

    @Test
    public void getStreakAmplifiedRisk_happyNoAmplification() {
        List<String> history = Arrays.asList("Happy", "Happy", "Happy");
        float risk = MoodMapper.getStreakAmplifiedRisk("Happy", history);
        assertEquals(0.0f, risk, DELTA); // Happy risk is 0
    }

    // ═══════════════════════════════════════════════════════════════════
    // Composite emotional risk
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void computeEmotionalRisk_allGoodIsLow() {
        float risk = MoodMapper.computeEmotionalRisk("Happy", "High", "High");
        assertEquals(0.0f, risk, DELTA);
    }

    @Test
    public void computeEmotionalRisk_allBadIsHigh() {
        float risk = MoodMapper.computeEmotionalRisk("Numb", "Low", "Low");
        // 0.9*0.5 + 1.0*0.25 + 1.0*0.25 = 0.45+0.25+0.25 = 0.95
        assertEquals(0.95f, risk, DELTA);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Mood stability
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getMoodStability_constantIsPerfect() {
        List<String> moods = Arrays.asList("Happy", "Happy", "Happy", "Happy");
        float stability = MoodMapper.getMoodStability(moods);
        assertEquals(1.0f, stability, DELTA);
    }

    @Test
    public void getMoodStability_wildSwingsIsLow() {
        List<String> moods = Arrays.asList("Happy", "Numb", "Happy", "Numb", "Happy", "Numb");
        float stability = MoodMapper.getMoodStability(moods);
        assertTrue("Volatile stability should be < 0.4, was " + stability, stability < 0.4f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Mood distribution
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getMoodDistribution_correctCounts() {
        List<String> moods = Arrays.asList("Happy", "Sad", "Happy", "Sad", "Sad");
        Map<String, Integer> dist = MoodMapper.getMoodDistribution(moods);
        assertEquals(Integer.valueOf(2), dist.get("Happy"));
        assertEquals(Integer.valueOf(3), dist.get("Sad"));
    }

    @Test
    public void getDominantMood_tieBreaksByRisk() {
        // Same count, Sad has higher risk than Happy
        List<String> moods = Arrays.asList("Happy", "Sad");
        String dominant = MoodMapper.getDominantMood(moods);
        assertEquals("Sad", dominant); // Higher risk wins tie
    }

    // ═══════════════════════════════════════════════════════════════════
    // Weekly summary
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getWeeklySummary_emptyList() {
        MoodMapper.WeeklySummary s = MoodMapper.getWeeklySummary(null);
        assertNull(s.dominantMood);
        assertEquals(0, s.totalDays);
    }

    @Test
    public void getWeeklySummary_allHappyWeek() {
        List<String> moods = Arrays.asList("Happy", "Happy", "Happy", "Happy", "Happy", "Happy", "Happy");
        MoodMapper.WeeklySummary s = MoodMapper.getWeeklySummary(moods);
        assertEquals("Happy", s.dominantMood);
        assertEquals(4.0f, s.averageWellbeing, DELTA);
        assertEquals(0f, s.negativeRatio, DELTA);
        assertEquals(7, s.totalDays);
        assertTrue("Happy week composite risk should be low, was " + s.compositeRisk,
                s.compositeRisk < 0.2f);
    }

    @Test
    public void getWeeklySummary_mixedWeek() {
        List<String> moods = Arrays.asList("Happy", "Sad", "Anxious", "Calm", "Sad", "Neutral", "Happy");
        MoodMapper.WeeklySummary s = MoodMapper.getWeeklySummary(moods);
        assertEquals(7, s.totalDays);
        assertTrue("Mixed week negative ratio should be > 0", s.negativeRatio > 0);
        assertTrue("Mixed week composite risk should be moderate", s.compositeRisk > 0.2f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Valence & arousal (circumplex model)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void valence_happyIsPositive() {
        assertTrue(MoodMapper.getMoodValence("Happy") > 0);
    }

    @Test
    public void valence_sadIsNegative() {
        assertTrue(MoodMapper.getMoodValence("Sad") < 0);
    }

    @Test
    public void arousal_angryIsHigh() {
        assertTrue(MoodMapper.getMoodArousal("Angry") > 0.9f);
    }

    @Test
    public void arousal_numbIsLow() {
        assertTrue(MoodMapper.getMoodArousal("Numb") < 0.2f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Risk label conversion
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void riskToLabel_critical() {
        assertEquals("Critical", MoodMapper.riskToLabel(0.80f));
    }

    @Test
    public void riskToLabel_low() {
        assertEquals("Low", MoodMapper.riskToLabel(0.10f));
    }

    @Test
    public void riskToLabel_moderate() {
        assertEquals("Moderate", MoodMapper.riskToLabel(0.40f));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Consecutive negative days
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getConsecutiveNegativeDays_endingWithNegative() {
        // Oldest first: Happy, Sad, Sad, Sad → 3 consecutive negative at end
        List<String> moods = Arrays.asList("Happy", "Sad", "Sad", "Sad");
        assertEquals(3, MoodMapper.getConsecutiveNegativeDays(moods));
    }

    @Test
    public void getConsecutiveNegativeDays_endingWithPositive() {
        List<String> moods = Arrays.asList("Sad", "Sad", "Happy");
        assertEquals(0, MoodMapper.getConsecutiveNegativeDays(moods));
    }

    @Test
    public void getConsecutiveNegativeDays_empty() {
        assertEquals(0, MoodMapper.getConsecutiveNegativeDays(null));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Mood rank (for sorting)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getMoodRank_happyBest() {
        assertEquals(0, MoodMapper.getMoodRank("Happy"));
    }

    @Test
    public void getMoodRank_numbWorst() {
        assertEquals(6, MoodMapper.getMoodRank("Numb"));
    }

    @Test
    public void getMoodRank_orderIsCorrect() {
        assertTrue(MoodMapper.getMoodRank("Happy") < MoodMapper.getMoodRank("Neutral"));
        assertTrue(MoodMapper.getMoodRank("Neutral") < MoodMapper.getMoodRank("Sad"));
        assertTrue(MoodMapper.getMoodRank("Sad") < MoodMapper.getMoodRank("Numb"));
    }
}
