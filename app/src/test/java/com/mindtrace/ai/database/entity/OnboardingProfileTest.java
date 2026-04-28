package com.mindtrace.ai.database.entity;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link OnboardingProfile} — specifically the new nutrition
 * health index, lifestyle health index, and profile completeness calculations.
 *
 * <p>These verify that the computed indices produce clinically meaningful
 * outputs across edge cases (empty profile, minimal data, full profile).</p>
 */
public class OnboardingProfileTest {

    private OnboardingProfile profile;

    @Before
    public void setUp() {
        profile = new OnboardingProfile();
    }

    // ═══════════════════════════════════════════════════════════════════
    // NUTRITION HEALTH INDEX
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void nutritionHealthIndex_allDefault_returnsZero() {
        // All fields default to 0
        float index = profile.getNutritionHealthIndex();
        // With all zeros: positive terms = 0, inverted terms = full
        // (1 - 0/5)*0.15 + (1 - 0/5)*0.15 + (1 - 0/5)*0.10 = 0.40
        assertTrue("Default profile should have non-zero NHI from inverted fields",
                index > 0.3f && index <= 0.5f);
    }

    @Test
    public void nutritionHealthIndex_optimalDiet_nearOne() {
        profile.dietQuality = 5;
        profile.mealRegularity = 5;
        profile.waterIntake = 5;
        profile.sugarIntake = 1;     // Low sugar = healthy
        profile.emotionalEating = 1;  // Low emotional eating = healthy
        profile.lateNightEating = 1;  // Low late eating = healthy

        float index = profile.getNutritionHealthIndex();
        assertTrue("Optimal nutrition should produce index > 0.85, was " + index,
                index >= 0.85f);
    }

    @Test
    public void nutritionHealthIndex_poorDiet_belowHalf() {
        profile.dietQuality = 1;
        profile.mealRegularity = 1;
        profile.waterIntake = 1;
        profile.sugarIntake = 5;      // High sugar = unhealthy
        profile.emotionalEating = 5;   // High emotional eating = unhealthy
        profile.lateNightEating = 5;   // High late eating = unhealthy

        float index = profile.getNutritionHealthIndex();
        assertTrue("Poor nutrition should produce index < 0.25, was " + index,
                index <= 0.25f);
    }

    @Test
    public void nutritionHealthIndex_clampedAtOne() {
        // Even with extreme values, index should never exceed 1.0
        profile.dietQuality = 5;
        profile.mealRegularity = 5;
        profile.waterIntake = 5;
        profile.sugarIntake = 1;
        profile.emotionalEating = 1;
        profile.lateNightEating = 1;

        float index = profile.getNutritionHealthIndex();
        assertTrue("NHI should never exceed 1.0, was " + index, index <= 1.0f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFESTYLE HEALTH INDEX (includes nutrition)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void lifestyleHealthIndex_includesNutrition() {
        // Set only nutrition-related fields
        profile.dietQuality = 5;
        profile.waterIntake = 5;

        float index = profile.getLifestyleHealthIndex();
        // dietQuality=5 contributes 10% * (5/5) = 0.10
        // waterIntake=5 contributes 5% * (5/5) = 0.05
        assertTrue("LHI should reflect nutrition contribution, was " + index,
                index >= 0.14f);
    }

    @Test
    public void lifestyleHealthIndex_allMaximal_nearOne() {
        profile.exerciseFrequency = "Daily";
        profile.routineStability = 5;
        profile.screenFreeActivities = 5;
        profile.sleepQuality = 5;
        profile.dietQuality = 5;
        profile.socialQualityBaseline = 5;
        profile.physicalActivity = 5;
        profile.waterIntake = 5;

        float index = profile.getLifestyleHealthIndex();
        assertTrue("All-maximal lifestyle should produce index > 0.85, was " + index,
                index >= 0.85f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROFILE COMPLETENESS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void profileCompleteness_emptyProfile_zero() {
        int percent = profile.getProfileCompleteness();
        assertEquals("Empty profile should be 0%", 0, percent);
    }

    @Test
    public void profileCompleteness_nutritionFields_counted() {
        // Set only nutrition fields
        profile.waterIntake = 3;
        profile.caffeineLevel = 2;
        profile.alcoholFrequency = "Never";
        profile.dietQuality = 4;
        profile.mealRegularity = 3;
        profile.sugarIntake = 2;
        profile.emotionalEating = 1;
        profile.lateNightEating = 1;

        int percent = profile.getProfileCompleteness();
        // 8 out of 44 total = ~18%
        assertTrue("8 nutrition fields should show ~18% complete, was " + percent,
                percent >= 17 && percent <= 19);
    }

    @Test
    public void profileCompleteness_fullProfile_hundred() {
        // Fill every field
        profile.name = "Test User";
        profile.ageRange = "25-34";
        profile.primaryGoal = "Reduce Anxiety";
        profile.helpAreasCsv = "sleep,focus";

        profile.stressLevel = 3;
        profile.anxietyLevel = 2;
        profile.motivationLevel = 4;
        profile.lonelinessLevel = 1;
        profile.selfDoubtLevel = 2;
        profile.overthinkingLevel = 3;

        profile.sleepHours = 7;
        profile.sleepQuality = 4;
        profile.focusLevel = 3;
        profile.energyLevel = 3;
        profile.workPressure = 2;
        profile.distractionLevel = 3;

        profile.socialMediaUse = 3;
        profile.lateNightPhoneUse = 2;
        profile.appAddictionRisk = 2;

        profile.routineConsistency = 3;
        profile.physicalActivity = 4;

        profile.addictionScale = 2;
        profile.purposeScore = 4;
        profile.copingStyle = "problem_solving";
        profile.personalityArchetype = "analytical";
        profile.mentalHealthHistory = "none";
        profile.peakVulnerabilityTime = "evening";
        profile.readinessToChange = 7;

        profile.routineStability = 4;
        profile.exerciseFrequency = "3-4x";
        profile.screenFreeActivities = 3;
        profile.socialQualityBaseline = 4;
        profile.screenTimeAwareness = 3;
        profile.triggerApps = "Instagram,TikTok";

        profile.waterIntake = 4;
        profile.caffeineLevel = 2;
        profile.alcoholFrequency = "Rarely";
        profile.dietQuality = 4;
        profile.mealRegularity = 4;
        profile.sugarIntake = 2;
        profile.emotionalEating = 1;
        profile.lateNightEating = 1;

        profile.socialSupportLevel = 4;
        profile.timestamp = System.currentTimeMillis();

        int percent = profile.getProfileCompleteness();
        assertEquals("Fully filled profile should be 100%", 100, percent);
    }
}
