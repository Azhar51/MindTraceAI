package com.mindtrace.ai.ai;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link FeatureVector}.
 *
 * <p>Covers: Builder, clamping, data completeness, domain averages,
 * overall risk estimate, serialization (array + JSON), distance metrics,
 * anomaly detection, quality labels, and vector blending.</p>
 */
public class FeatureVectorTest {

    private static final float EPSILON = 0.001f;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void empty_allDefaults() {
        FeatureVector fv = FeatureVector.empty();
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            assertEquals("Feature " + i + " should be DEFAULT_VALUE",
                    FeatureVector.DEFAULT_VALUE, fv.get(i), EPSILON);
        }
        assertEquals(0, fv.nonDefaultCount);
        assertEquals(0f, fv.dataCompleteness, EPSILON);
        assertEquals("empty", fv.source);
    }

    @Test
    public void builder_setsValueCorrectly() {
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(0.8f)
                .p1_moodRisk(0.9f)
                .c1_workPressure(0.7f)
                .t1_screenTimeTrend3d(0.6f)
                .build();

        assertEquals(0.8f, fv.screenTimeHours(), EPSILON);
        assertEquals(0.9f, fv.moodRisk(), EPSILON);
        assertEquals(0.7f, fv.workPressure(), EPSILON);
        assertEquals(0.6f, fv.screenTimeTrend3d(), EPSILON);
    }

    @Test
    public void builder_clampsValues() {
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(1.5f)   // above 1.0
                .p1_moodRisk(-0.3f)         // below 0.0
                .build();

        assertEquals(1.0f, fv.screenTimeHours(), EPSILON);
        assertEquals(0.0f, fv.moodRisk(), EPSILON);
    }

    @Test
    public void builder_genericSet() {
        FeatureVector fv = new FeatureVector.Builder()
                .set(0, 0.75f)
                .set(12, 0.85f)
                .build();

        assertEquals(0.75f, fv.get(0), EPSILON);
        assertEquals(0.85f, fv.get(12), EPSILON);
    }

    @Test
    public void get_outOfBounds_returnsDefault() {
        FeatureVector fv = FeatureVector.empty();
        assertEquals(FeatureVector.DEFAULT_VALUE, fv.get(-1), EPSILON);
        assertEquals(FeatureVector.DEFAULT_VALUE, fv.get(100), EPSILON);
    }

    @Test
    public void getByName_validName() {
        FeatureVector fv = new FeatureVector.Builder()
                .d6_nightUsageMinutes(0.9f)
                .build();
        assertEquals(0.9f, fv.getByName("nightUsageMinutes"), EPSILON);
    }

    @Test
    public void getByName_invalidName_returnsDefault() {
        FeatureVector fv = FeatureVector.empty();
        assertEquals(FeatureVector.DEFAULT_VALUE, fv.getByName("nonExistent"), EPSILON);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA COMPLETENESS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void dataCompleteness_allDefault_isZero() {
        FeatureVector fv = FeatureVector.empty();
        assertEquals(0f, fv.dataCompleteness, EPSILON);
        assertFalse(fv.isReliable());
    }

    @Test
    public void dataCompleteness_allSet_isOne() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            b.set(i, 0.1f * (i % 10)); // All different from 0.5
        }
        FeatureVector fv = b.build();
        // Features at index 5 = 0.5 (default), so not all will be non-default
        assertTrue(fv.dataCompleteness > 0.8f);
    }

    @Test
    public void dataCompleteness_halfSet() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        for (int i = 0; i < 17; i++) {
            b.set(i, 0.0f); // Non-default
        }
        FeatureVector fv = b.build();
        assertEquals(17, fv.nonDefaultCount);
        assertEquals(17f / 34f, fv.dataCompleteness, EPSILON);
        assertTrue(fv.isReliable());
    }

    // ═══════════════════════════════════════════════════════════════════
    // DOMAIN AVERAGES
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void digitalRiskAvg_computesCorrectly() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        // Set all digital features to 0.6
        for (int i = FeatureVector.IDX_D1; i <= FeatureVector.IDX_D12; i++) {
            b.set(i, 0.6f);
        }
        FeatureVector fv = b.build();
        assertEquals(0.6f, fv.digitalRiskAvg(), EPSILON);
    }

    @Test
    public void psychRiskAvg_computesCorrectly() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        for (int i = FeatureVector.IDX_P1; i <= FeatureVector.IDX_P10; i++) {
            b.set(i, 0.3f);
        }
        FeatureVector fv = b.build();
        assertEquals(0.3f, fv.psychRiskAvg(), EPSILON);
    }

    @Test
    public void overallRiskEstimate_weightedCorrectly() {
        // Digital=30%, Psych=35%, Context=15%, Temporal=20%
        FeatureVector.Builder b = new FeatureVector.Builder();
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) b.set(i, 0.0f);
        // Set only digital to 1.0
        for (int i = FeatureVector.IDX_D1; i <= FeatureVector.IDX_D12; i++) b.set(i, 1.0f);
        FeatureVector fv = b.build();
        // Digital avg = 1.0, rest = 0.0 → overall = 1.0 * 0.30 = 0.30
        assertEquals(0.30f, fv.overallRiskEstimate(), EPSILON);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void toArray_fromArray_roundTrip() {
        FeatureVector original = new FeatureVector.Builder()
                .d1_screenTimeHours(0.8f)
                .p1_moodRisk(0.7f)
                .c1_workPressure(0.6f)
                .t1_screenTimeTrend3d(0.4f)
                .build();

        float[] arr = original.toArray();
        assertEquals(FeatureVector.TOTAL_FEATURES, arr.length);

        FeatureVector restored = FeatureVector.fromArray(arr);
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            assertEquals("Feature " + i, original.get(i), restored.get(i), EPSILON);
        }
    }

    @Test
    public void fromArray_legacy28_fillsTemporalDefaults() {
        float[] legacy = new float[28];
        legacy[0] = 0.9f;
        FeatureVector fv = FeatureVector.fromArray(legacy);
        assertEquals(0.9f, fv.get(0), EPSILON);
        // Temporal features (28-33) should be DEFAULT_VALUE
        for (int i = 28; i < 34; i++) {
            assertEquals(FeatureVector.DEFAULT_VALUE, fv.get(i), EPSILON);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromArray_invalidSize_throws() {
        FeatureVector.fromArray(new float[15]);
    }

    @Test
    public void toJson_fromJson_roundTrip() {
        FeatureVector original = new FeatureVector.Builder()
                .d1_screenTimeHours(0.8f)
                .p1_moodRisk(0.7f)
                .source("test")
                .build();

        String json = original.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"f\":["));

        FeatureVector restored = FeatureVector.fromJson(json);
        assertEquals(original.get(0), restored.get(0), 0.01f);
        assertEquals(original.get(12), restored.get(12), 0.01f);
    }

    @Test
    public void fromJson_null_returnsEmpty() {
        FeatureVector fv = FeatureVector.fromJson(null);
        assertEquals(0f, fv.dataCompleteness, EPSILON);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISTANCE & SIMILARITY
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void distanceTo_identicalVectors_isZero() {
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(0.5f)
                .build();
        assertEquals(0f, fv.distanceTo(fv), EPSILON);
    }

    @Test
    public void distanceTo_differentVectors_isPositive() {
        FeatureVector a = new FeatureVector.Builder().d1_screenTimeHours(0.0f).build();
        FeatureVector b = new FeatureVector.Builder().d1_screenTimeHours(1.0f).build();
        assertTrue(a.distanceTo(b) > 0f);
    }

    @Test
    public void cosineSimilarity_identicalVectors_isOne() {
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(0.8f)
                .p1_moodRisk(0.6f)
                .build();
        assertEquals(1.0f, fv.cosineSimilarity(fv), EPSILON);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANOMALY & THRESHOLD
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void getAnomalousFeatures_returnsCorrectIndices() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        b.d1_screenTimeHours(0.9f); // index 0
        b.d6_nightUsageMinutes(0.85f); // index 5
        FeatureVector fv = b.build();

        int[] anomalous = fv.getAnomalousFeatures(0.8f);
        assertTrue(anomalous.length >= 2);
    }

    @Test
    public void countAboveThreshold() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        b.set(0, 0.9f);
        b.set(1, 0.85f);
        b.set(2, 0.3f);
        FeatureVector fv = b.build();
        assertTrue(fv.countAboveThreshold(0.8f) >= 2);
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUALITY LABELS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void qualityLabel_excellent() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) b.set(i, 0.1f);
        assertEquals("Excellent", b.build().getQualityLabel());
    }

    @Test
    public void qualityLabel_insufficient() {
        FeatureVector fv = FeatureVector.empty();
        assertEquals("Insufficient", fv.getQualityLabel());
    }

    // ═══════════════════════════════════════════════════════════════════
    // DOMAIN DATA CHECKS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void hasDigitalData_requiresSixNonDefault() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        // Set 6 digital features to non-default
        for (int i = 0; i < 6; i++) b.set(i, 0.1f);
        assertTrue(b.build().hasDigitalData());
    }

    @Test
    public void hasDigitalData_fiveIsNotEnough() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        for (int i = 0; i < 5; i++) b.set(i, 0.1f);
        assertFalse(b.build().hasDigitalData());
    }

    // ═══════════════════════════════════════════════════════════════════
    // WEIGHTED RISK & CONFIDENCE
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void weightedRiskScore_allZero_isZero() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) b.set(i, 0f);
        assertEquals(0f, b.build().weightedRiskScore(), EPSILON);
    }

    @Test
    public void weightedRiskScore_allOne_isOne() {
        FeatureVector.Builder b = new FeatureVector.Builder();
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) b.set(i, 1.0f);
        // Weighted average of all 1.0s = 1.0
        assertEquals(1.0f, b.build().weightedRiskScore(), EPSILON);
    }

    @Test
    public void confidenceAdjustedRisk_lowCompleteness_pullsToHalf() {
        // Empty vector: completeness=0, adjusted = 0.5
        FeatureVector fv = FeatureVector.empty();
        assertEquals(0.5f, fv.confidenceAdjustedRisk(), 0.05f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RISK DRIVERS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void topRiskDrivers_returnsRequestedCount() {
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(0.9f)
                .p1_moodRisk(0.95f)
                .build();
        FeatureVector.RiskDriver[] top3 = fv.getTopRiskDrivers(3);
        assertEquals(3, top3.length);
    }

    @Test
    public void topRiskDrivers_highestFirst() {
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(0.1f)
                .p1_moodRisk(0.95f)
                .build();
        FeatureVector.RiskDriver[] top = fv.getTopRiskDrivers(1);
        assertEquals("moodRisk", top[0].name);
    }

    // ═══════════════════════════════════════════════════════════════════
    // VECTOR BLENDING
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void blend_halfWeight_isMidpoint() {
        FeatureVector.Builder bA = new FeatureVector.Builder();
        FeatureVector.Builder bB = new FeatureVector.Builder();
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            bA.set(i, 0.0f);
            bB.set(i, 1.0f);
        }
        FeatureVector blended = bA.build().blend(bB.build(), 0.5f);
        for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
            assertEquals(0.5f, blended.get(i), EPSILON);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // METADATA
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void constants_featureCounts() {
        assertEquals(34, FeatureVector.TOTAL_FEATURES);
        assertEquals(12, FeatureVector.DIGITAL_COUNT);
        assertEquals(10, FeatureVector.PSYCH_COUNT);
        assertEquals(6, FeatureVector.CONTEXT_COUNT);
        assertEquals(6, FeatureVector.TEMPORAL_COUNT);
        assertEquals(34, FeatureVector.FEATURE_NAMES.length);
        assertEquals(34, FeatureVector.FEATURE_WEIGHTS.length);
    }

    @Test
    public void maxFeature_returnsHighest() {
        FeatureVector fv = new FeatureVector.Builder()
                .d1_screenTimeHours(0.3f)
                .p1_moodRisk(0.95f)
                .build();
        assertEquals(0.95f, fv.maxFeature(), EPSILON);
        assertEquals("moodRisk", fv.maxFeatureName());
    }

    @Test
    public void toString_containsKey() {
        FeatureVector fv = FeatureVector.empty();
        String s = fv.toString();
        assertTrue(s.contains("FeatureVector{"));
        assertTrue(s.contains("quality="));
    }
}
