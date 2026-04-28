package com.mindtrace.ai.database.entity;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link SafetyPlan} — validates completion tracking,
 * shareable text generation, and content detection.
 */
public class SafetyPlanTest {

    private SafetyPlan plan;

    @Before
    public void setUp() {
        plan = new SafetyPlan();
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLETION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void completionPercent_empty_zero() {
        assertEquals(0, plan.getCompletionPercent());
    }

    @Test
    public void completionPercent_oneSection_16() {
        plan.warningSignalsJson = "[\"Racing thoughts\"]";
        // 1 out of 6 = 16%
        assertEquals(16, plan.getCompletionPercent());
    }

    @Test
    public void completionPercent_allSections_100() {
        plan.warningSignalsJson = "[\"Signal 1\"]";
        plan.copingStrategiesJson = "[\"Breathing\"]";
        plan.reasonsToLiveJson = "[\"Family\"]";
        plan.trustedContactsJson = "[\"Mom\"]";
        plan.professionalContactsJson = "[\"Therapist\"]";
        plan.safeEnvironmentsJson = "[\"Home\"]";

        assertEquals(100, plan.getCompletionPercent());
    }

    @Test
    public void completionPercent_emptyJsonArray_notCounted() {
        plan.warningSignalsJson = "[]";
        assertEquals("Empty JSON array should not count as filled", 0, plan.getCompletionPercent());
    }

    // ═══════════════════════════════════════════════════════════════════
    // HAS CONTENT
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void hasContent_empty_false() {
        assertFalse(plan.hasContent());
    }

    @Test
    public void hasContent_withOneSection_true() {
        plan.copingStrategiesJson = "[\"Box breathing\"]";
        assertTrue(plan.hasContent());
    }

    @Test
    public void hasContent_emptyArrays_false() {
        plan.warningSignalsJson = "[]";
        plan.copingStrategiesJson = "[]";
        assertFalse(plan.hasContent());
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHAREABLE TEXT
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void shareableText_containsAllHeaders() {
        String text = plan.toShareableText();
        assertTrue(text.contains("Warning Signs"));
        assertTrue(text.contains("Coping Strategies"));
        assertTrue(text.contains("Reasons to Live"));
        assertTrue(text.contains("Trusted Contacts"));
        assertTrue(text.contains("Professional Contacts"));
        assertTrue(text.contains("Safe Environments"));
    }

    @Test
    public void shareableText_emptySection_showsNotFilled() {
        String text = plan.toShareableText();
        assertTrue(text.contains("(not filled yet)"));
    }

    @Test
    public void shareableText_filledSection_showsBullets() {
        plan.warningSignalsJson = "[\"Racing thoughts\",\"Withdrawal\"]";
        String text = plan.toShareableText();
        assertTrue(text.contains("• Racing thoughts"));
        assertTrue(text.contains("• Withdrawal"));
    }
}
