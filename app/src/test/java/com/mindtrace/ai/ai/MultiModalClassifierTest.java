package com.mindtrace.ai.ai;

import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.entity.RiskClassification;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MultiModalClassifier}.
 *
 * <p>Uses a mocked {@link RiskClassificationDao} to isolate the classifier logic
 * from Room database dependencies.</p>
 *
 * <p>Covers: sub-classifiers, weight validation, crisis detection, category
 * identification, protective dampening, cross-signal amplification, confidence,
 * comorbidity detection, and escape behaviour scoring.</p>
 */
public class MultiModalClassifierTest {

    private static final float EPSILON = 0.01f;

    private MultiModalClassifier classifier;
    private RiskClassificationDao mockDao;

    @Before
    public void setUp() {
        mockDao = Mockito.mock(RiskClassificationDao.class);
        // Return empty history by default so computeDelta() is safe
        when(mockDao.getHistory(anyInt())).thenReturn(Collections.emptyList());
        when(mockDao.getHistorySince(anyLong())).thenReturn(Collections.emptyList());
        classifier = new MultiModalClassifier(mockDao);
    }

    // ═══════════════════════════════════════════════════════════════════
    // WEIGHT VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void validateWeights_allSubClassifiersSumToOne() {
        assertTrue("Classifier weight sums should be ~1.0",
                MultiModalClassifier.validateWeights());
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOW-RISK SCENARIO
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void classify_lowRisk_producesLowScores() {
        FeatureVector fv = buildLowRiskVector();
        RiskClassification rc = classifier.classifyToday(fv);

        assertNotNull(rc);
        assertTrue("Overall risk should be low", rc.overallRiskScore < 0.30f);
        assertFalse("No crisis expected", rc.crisisFlag);
        assertEquals("full", rc.classificationMode);
    }

    @Test
    public void classify_lowRisk_severityIsNoneOrMild() {
        FeatureVector fv = buildLowRiskVector();
        RiskClassification rc = classifier.classifyToday(fv);

        RiskClassification.Severity sev = rc.getOverallSeverity();
        assertTrue("Severity should be NONE or MILD",
                sev == RiskClassification.Severity.NONE ||
                sev == RiskClassification.Severity.MILD);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HIGH-RISK SCENARIO
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void classify_highRisk_producesHighScores() {
        FeatureVector fv = buildHighRiskVector();
        RiskClassification rc = classifier.classifyToday(fv);

        assertNotNull(rc);
        assertTrue("Overall risk should be high", rc.overallRiskScore > 0.60f);
    }

    @Test
    public void classify_highRisk_elevatedCategoryCount() {
        FeatureVector fv = buildHighRiskVector();
        RiskClassification rc = classifier.classifyToday(fv);

        assertTrue("Multiple categories should be elevated",
                rc.getElevatedCategoryCount() >= 3);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS DETECTION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void classify_crisisFromDepression() {
        // Depression crisis: depression >= 0.85
        FeatureVector fv = new FeatureVector.Builder()
                .p1_moodRisk(1.0f)
                .p10_consecutiveSadDays(1.0f)
                .p4_motivationDeficit(1.0f)
                .p6_energyDeficit(1.0f)
                .p5_sleepDeficit(0.9f)
                .p8_purposeDeficit(1.0f)
                .p3_lonelinessLevel(0.9f)
                .c2_socialSupportDeficit(0.9f)
                .t6_recoverySpeed(1.0f)
                .t2_moodStability7d(0.9f)
                .t3_sleepConsistency7d(0.8f)
                .source("test")
                .build();

        RiskClassification rc = classifier.classifyToday(fv);
        assertTrue("Crisis flag should be set for severe depression",
                rc.crisisFlag);
        assertNotNull(rc.crisisReason);
        assertTrue(rc.crisisReason.contains("depression"));
    }

    @Test
    public void classify_crisisFromStress() {
        // Stress crisis: stress >= 0.90
        FeatureVector fv = new FeatureVector.Builder()
                .p2_stressLevel(1.0f)
                .p1_moodRisk(0.9f)
                .p5_sleepDeficit(0.9f)
                .p7_focusDeficit(0.9f)
                .c1_workPressure(1.0f)
                .d6_nightUsageMinutes(0.9f)
                .t2_moodStability7d(0.9f)
                .t5_postCheckInChange(0.8f)
                .source("test")
                .build();

        RiskClassification rc = classifier.classifyToday(fv);
        assertTrue("Crisis flag should be set for severe stress",
                rc.crisisFlag);
    }

    @Test
    public void classify_noCrisis_whenModerate() {
        FeatureVector fv = buildModerateRiskVector();
        RiskClassification rc = classifier.classifyToday(fv);
        assertFalse("No crisis for moderate risk", rc.crisisFlag);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SUB-CLASSIFIERS INDIVIDUALLY
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void classifyDigitalAddiction_highInput() {
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(0.9f)
                .d4_rapidSwitchCount(0.8f)
                .d7_unlockCount(0.9f)
                .d12_hasLoopPattern(0.8f)
                .p9_addictionSelfScore(0.9f)
                .t1_screenTimeTrend3d(0.7f)
                .source("test")
                .build();

        float score = classifier.classifyDigitalAddiction(fv);
        assertTrue("Digital addiction should be elevated", score > 0.40f);
    }

    @Test
    public void classifyDigitalAddiction_lowInput() {
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(0.1f)
                .d4_rapidSwitchCount(0.05f)
                .d7_unlockCount(0.1f)
                .d12_hasLoopPattern(0.0f)
                .source("test")
                .build();

        float score = classifier.classifyDigitalAddiction(fv);
        assertTrue("Digital addiction should be low", score < 0.40f);
    }

    @Test
    public void classifyLowFulfilment_scoreBounds() {
        FeatureVector low = buildLowRiskVector();
        FeatureVector high = buildHighRiskVector();
        float scoreLow = classifier.classifyLowFulfilment(low);
        float scoreHigh = classifier.classifyLowFulfilment(high);

        assertTrue(scoreLow >= 0f && scoreLow <= 1f);
        assertTrue(scoreHigh >= 0f && scoreHigh <= 1f);
        assertTrue("High risk should score higher", scoreHigh > scoreLow);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIMARY / SECONDARY CATEGORY
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void findPrimaryCategory_returnsValidCategory() {
        FeatureVector fv = buildHighRiskVector();
        RiskClassification rc = classifier.classifyToday(fv);

        assertNotNull(rc.primaryCategory);
        assertTrue("Primary category should be a known category",
                isValidCategory(rc.primaryCategory));
    }

    @Test
    public void findSecondaryCategory_differentFromPrimary() {
        FeatureVector fv = buildHighRiskVector();
        RiskClassification rc = classifier.classifyToday(fv);

        assertNotNull(rc.secondaryCategory);
        assertNotEquals("Secondary should differ from primary",
                rc.primaryCategory, rc.secondaryCategory);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIDENCE
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void confidence_fullData_isHigh() {
        FeatureVector fv = buildFullDataVector();
        float confidence = classifier.computeConfidence(fv);
        assertTrue("Full data should produce high confidence", confidence > 0.7f);
    }

    @Test
    public void confidence_emptyData_isLow() {
        FeatureVector fv = FeatureVector.empty();
        float confidence = classifier.computeConfidence(fv);
        assertTrue("Empty data should produce low confidence", confidence < 0.3f);
    }

    @Test
    public void confidence_inBounds() {
        FeatureVector fv = buildModerateRiskVector();
        float confidence = classifier.computeConfidence(fv);
        assertTrue(confidence >= 0f && confidence <= 1f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // OVERALL RISK COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void computeOverallRisk_weightsToOne() {
        // Verify that if all categories are 1.0, overall = 1.0
        RiskClassification rc = new RiskClassification();
        rc.digitalAddictionScore = 1.0f;
        rc.stressAnxietyScore = 1.0f;
        rc.depressionRiskScore = 1.0f;
        rc.socialIsolationScore = 1.0f;
        rc.sleepDisruptionScore = 1.0f;
        rc.lowFulfilmentScore = 1.0f;

        float overall = classifier.computeOverallRisk(rc);
        assertEquals("All 1.0 should produce 1.0 overall", 1.0f, overall, EPSILON);
    }

    @Test
    public void computeOverallRisk_depressionHeaviest() {
        // Depression weight (0.22) is the largest
        RiskClassification deprOnly = new RiskClassification();
        deprOnly.depressionRiskScore = 1.0f;

        RiskClassification digOnly = new RiskClassification();
        digOnly.digitalAddictionScore = 1.0f;

        float deprOverall = classifier.computeOverallRisk(deprOnly);
        float digOverall = classifier.computeOverallRisk(digOnly);

        assertTrue("Depression should contribute more than digital",
                deprOverall > digOverall);
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMORBIDITY DETECTION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void detectComorbidities_depressionIsolationLoop() {
        RiskClassification rc = new RiskClassification();
        rc.depressionRiskScore = 0.7f;
        rc.socialIsolationScore = 0.7f;

        String[] patterns = classifier.detectComorbidities(rc);
        assertTrue(patterns.length > 0);
        boolean found = false;
        for (String p : patterns) {
            if ("depression_isolation_loop".equals(p)) found = true;
        }
        assertTrue("Should detect depression-isolation loop", found);
    }

    @Test
    public void detectComorbidities_noPatterns() {
        RiskClassification rc = new RiskClassification();
        rc.depressionRiskScore = 0.1f;
        rc.socialIsolationScore = 0.1f;

        String[] patterns = classifier.detectComorbidities(rc);
        assertEquals(0, patterns.length);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ESCAPE BEHAVIOUR
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void escapeBehaviour_highDistressHighScreen() {
        FeatureVector fv = new FeatureVector.Builder()
                .p2_stressLevel(0.9f)
                .p1_moodRisk(0.8f)
                .p3_lonelinessLevel(0.9f)
                .d2_screenTimeDeviation(0.8f)
                .d11_passiveAppRatio(0.9f)
                .d6_nightUsageMinutes(0.8f)
                .d5_bingeSessionCount(0.7f)
                .source("test")
                .build();

        float escape = classifier.computeEscapeBehaviourScore(fv);
        assertTrue("Escape score should be elevated", escape > 0.4f);
    }

    @Test
    public void escapeBehaviour_lowDistress_isLow() {
        FeatureVector fv = buildLowRiskVector();
        float escape = classifier.computeEscapeBehaviourScore(fv);
        assertTrue("Escape score should be low", escape < 0.3f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS LEVEL ASSESSMENT
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void assessCrisisLevel_none_forLowRisk() {
        RiskClassification rc = new RiskClassification();
        rc.overallRiskScore = 0.2f;
        rc.crisisFlag = false;

        assertEquals(MultiModalClassifier.CrisisLevel.NONE,
                classifier.assessCrisisLevel(rc));
    }

    @Test
    public void assessCrisisLevel_critical_forSevereCrisis() {
        RiskClassification rc = new RiskClassification();
        rc.overallRiskScore = 0.9f;
        rc.crisisFlag = true;
        rc.depressionRiskScore = 0.9f;
        rc.stressAnxietyScore = 0.95f;
        rc.socialIsolationScore = 0.8f;
        rc.sleepDisruptionScore = 0.7f;
        rc.lowFulfilmentScore = 0.6f;
        rc.digitalAddictionScore = 0.5f;

        MultiModalClassifier.CrisisLevel level = classifier.assessCrisisLevel(rc);
        assertTrue("Should be URGENT or CRITICAL",
                level.requiresImmediateAction());
    }

    // ═══════════════════════════════════════════════════════════════════
    // PATTERN FINGERPRINTING
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void generatePatternFingerprint_sixDigits() {
        RiskClassification rc = new RiskClassification();
        rc.digitalAddictionScore = 0.5f;
        rc.stressAnxietyScore = 0.3f;
        rc.depressionRiskScore = 0.7f;
        rc.socialIsolationScore = 0.1f;
        rc.sleepDisruptionScore = 0.8f;
        rc.lowFulfilmentScore = 0.2f;

        String fp = classifier.generatePatternFingerprint(rc);
        assertEquals(6, fp.length());
    }

    @Test
    public void hasProfileShifted_largeChange_true() {
        assertTrue(classifier.hasProfileShifted("000000", "200200"));
    }

    @Test
    public void hasProfileShifted_smallChange_false() {
        assertFalse(classifier.hasProfileShifted("111111", "111111"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLASSIFICATION MODE
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void classificationMode_fullWhenEnoughData() {
        FeatureVector fv = buildFullDataVector();
        RiskClassification rc = classifier.classifyToday(fv);
        assertEquals("full", rc.classificationMode);
    }

    @Test
    public void classificationMode_baselineOnlyWhenSparse() {
        // Build a vector with very few non-default features
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(0.3f)
                .p1_moodRisk(0.4f)
                .source("test")
                .build();
        RiskClassification rc = classifier.classifyToday(fv);
        // Only 2 non-default features → baseline_only
        assertEquals("baseline_only", rc.classificationMode);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS — Build test vectors
    // ═══════════════════════════════════════════════════════════════════

    private FeatureVector buildLowRiskVector() {
        FeatureVector.Builder b = new FeatureVector.Builder().source("test");
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            b.set(i, 0.1f);
        }
        return b.build();
    }

    private FeatureVector buildHighRiskVector() {
        FeatureVector.Builder b = new FeatureVector.Builder().source("test");
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            b.set(i, 0.9f);
        }
        return b.build();
    }

    private FeatureVector buildModerateRiskVector() {
        FeatureVector.Builder b = new FeatureVector.Builder().source("test");
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            b.set(i, 0.45f);
        }
        return b.build();
    }

    private FeatureVector buildFullDataVector() {
        FeatureVector.Builder b = new FeatureVector.Builder().source("test");
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            b.set(i, 0.1f + (i * 0.02f)); // All different from 0.5
        }
        return b.build();
    }

    private boolean isValidCategory(String cat) {
        return "digital_addiction".equals(cat) ||
               "stress_anxiety".equals(cat) ||
               "depression".equals(cat) ||
               "social_isolation".equals(cat) ||
               "sleep_disruption".equals(cat) ||
               "low_fulfilment".equals(cat);
    }
}
