package com.mindtrace.ai.ui;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.mindtrace.ai.R;
import com.mindtrace.ai.databinding.ActivityQuestionnaireBinding;
import com.mindtrace.ai.ui.theme.ColorSystem;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.viewmodel.OnboardingViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QuestionnaireActivity extends AppCompatActivity {

    private ActivityQuestionnaireBinding binding;

    private static final int TOTAL_STEPS = 7;
    private static final int STEP_COMPLETE = 7; // index of completion view
    private static final String PREFS_NAME = "onboarding_progress";
    private static final String KEY_STEP = "current_step";

    private final String[] stepTitles = {
            "Tell us who you\u2019re building this for.",
            "How heavy does your mind feel lately?",
            "How is your day-to-day functioning?",
            "What does your phone behavior look like?",
            "How stable does your routine feel?",
            "Choose the support you want visible.",
            "What does your nutrition look like?"
    };

    private final String[] stepSubtitles = {
            "This gives MindTrace a clear starting point for your daily guidance.",
            "No judgment \u2014 we only use this to shape calmer, more useful recommendations.",
            "These answers help the app understand your energy, focus, and sleep.",
            "We use this to estimate distraction risk before enough live data arrives.",
            "Routine and direction matter because motivation usually follows structure.",
            "Support stays respectful, optional, and easier to reach only if you want it.",
            "What you eat and drink shapes your mood, focus, and sleep more than you think."
    };

    private OnboardingViewModel viewModel;
    private int currentStep = 0;

    // Header
    private TextView tvStepBadge, tvTitle, tvSubtitle;
    private LinearProgressIndicator progressIndicator;
    private ViewFlipper viewFlipper;
    private MaterialButton btnBack, btnNext;

    // Step 1 — Identity
    private TextInputEditText etName;
    private ChipGroup chipGroupAge, chipGroupGoal, chipGroupHelp;

    // Step 2 — Emotional (Material Sliders)
    private Slider sliderStress, sliderAnxiety, sliderMotivation, sliderLoneliness, sliderSelfDoubt, sliderOverthinking;
    private TextView tvStressVal, tvAnxietyVal, tvMotivationVal, tvLonelinessVal, tvSelfDoubtVal, tvOverthinkingVal;

    // Step 3 — Functioning
    private Slider sliderSleepHours, sliderSleepQuality, sliderFocus, sliderEnergy, sliderWorkPressure, sliderDistraction;
    private TextView tvSleepHoursVal, tvSleepQualityVal, tvFocusVal, tvEnergyVal, tvWorkPressureVal, tvDistractionVal;

    // Step 4 — Digital
    private Slider sliderSocialMediaUse, sliderLateNightUse, sliderAppAddictionRisk, sliderOverusePattern, sliderBingeScrolling, sliderAppSwitching;
    private TextView tvSocialMediaVal, tvLateNightVal, tvAddictionVal, tvOveruseVal, tvBingeVal, tvSwitchingVal;

    // Step 5 — Lifestyle
    private Slider sliderRoutineConsistency, sliderProductiveHabits, sliderProcrastination, sliderFeelingStuck, sliderPhysicalActivity;
    private Slider sliderRoutineStability, sliderScreenFree, sliderAddictionScale, sliderPurposeScore;
    private TextView tvRoutineVal, tvProductiveVal, tvProcrastinationVal, tvStuckVal, tvPhysicalVal;
    private TextView tvRoutineStabilityVal, tvScreenFreeVal, tvAddictionScaleVal, tvPurposeScoreVal;
    private ChipGroup chipGroupExerciseFrequency, chipGroupMentalHealth;

    // Step 4 — Digital (Advanced)
    private ChipGroup chipGroupTriggerApps, chipGroupVulnerabilityTime;
    private Slider sliderScreenAwareness;
    private TextView tvScreenAwarenessVal, tvScreenAwarenessDesc;
    private TextView tvSocialMediaDesc, tvLateNightDesc, tvAddictionDesc;
    private TextView tvDigitalRiskBadge, tvDigitalRiskSummary;
    private LinearProgressIndicator progressDigitalRisk;

    // Step 6 — Support (Advanced)
    private Slider sliderSocialSupport, sliderSocialQuality, sliderReadiness;
    private TextView tvSupportVal, tvSocialQualityVal, tvReadinessVal;
    private TextView tvSupportDesc, tvSocialQualityDesc, tvReadinessDesc;
    private SwitchMaterial switchSupportNeeded, switchSafetySupport;

    // Step 6 — Nutrition
    private Slider sliderWaterIntake, sliderCaffeineIntake, sliderDietQuality;
    private Slider sliderMealRegularity, sliderSugarIntake, sliderEmotionalEating, sliderLateEating;
    private TextView tvWaterIntakeVal, tvCaffeineVal, tvDietQualityVal;
    private TextView tvMealRegularityVal, tvSugarIntakeVal, tvEmotionalEatingVal, tvLateEatingVal;
    private TextView tvWaterIntakeDesc, tvCaffeineDesc, tvDietQualityDesc, tvEmotionalEatingDesc;
    private ChipGroup chipGroupAlcohol;

    // Completion
    private View viewCompleteCircle;
    private LinearProgressIndicator progressComplete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuestionnaireBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        configureSystemBars();

        viewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

        bindViews();
        wireSliderLabels();
        restoreState(savedInstanceState);
        setupInteractions();
        updateStepUi(false);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep > 0 && currentStep < TOTAL_STEPS) {
                    currentStep--;
                    updateStepUi(true);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        viewModel.getSubmissionResult().observe(this, result -> {
            if (result == null) return;
            if (!result.successful) {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                // Go back to last step so user can retry
                currentStep = TOTAL_STEPS - 1;
                updateStepUi(false);
                btnNext.setEnabled(true);
                btnBack.setEnabled(true);
                return;
            }
            // Navigate out after a brief moment on the completion screen
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent nextIntent;
                if (!hasUsageStatsPermission()) {
                    nextIntent = new Intent(this, PermissionActivity.class);
                } else {
                    nextIntent = new Intent(this, MainActivity.class);
                }
                if (result.openSupportFirst) {
                    nextIntent.putExtra(MainActivity.EXTRA_START_DESTINATION, MainActivity.DEST_SUPPORT);
                }
                nextIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(nextIntent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }, 600);
        });
    }

    // ─── View Binding ────────────────────────────────────────────────

    private void bindViews() {
        tvStepBadge = binding.tvOnboardingStepBadge;
        tvTitle = binding.tvOnboardingTitle;
        tvSubtitle = binding.tvOnboardingSubtitle;
        progressIndicator = binding.progressOnboarding;
        viewFlipper = binding.viewFlipperOnboarding;
        btnBack = binding.btnOnboardingBack;
        btnNext = binding.btnOnboardingNext;

        // Step views live inside unnamed <include> layouts inside ViewFlipper.
        // ViewBinding cannot reference them — must use findViewById.

        // Step 1
        etName = findViewById(R.id.et_onboarding_name);
        chipGroupAge = findViewById(R.id.chip_group_age);
        chipGroupGoal = findViewById(R.id.chip_group_goal);
        chipGroupHelp = findViewById(R.id.chip_group_help);

        // Step 2
        sliderStress = findViewById(R.id.slider_stress);
        sliderAnxiety = findViewById(R.id.slider_anxiety);
        sliderMotivation = findViewById(R.id.slider_motivation);
        sliderLoneliness = findViewById(R.id.slider_loneliness);
        sliderSelfDoubt = findViewById(R.id.slider_self_doubt);
        sliderOverthinking = findViewById(R.id.slider_overthinking);
        tvStressVal = findViewById(R.id.tv_stress_value);
        tvAnxietyVal = findViewById(R.id.tv_anxiety_value);
        tvMotivationVal = findViewById(R.id.tv_motivation_value);
        tvLonelinessVal = findViewById(R.id.tv_loneliness_value);
        tvSelfDoubtVal = findViewById(R.id.tv_self_doubt_value);
        tvOverthinkingVal = findViewById(R.id.tv_overthinking_value);

        // Step 3
        sliderSleepHours = findViewById(R.id.slider_sleep_hours);
        sliderSleepQuality = findViewById(R.id.slider_sleep_quality);
        sliderFocus = findViewById(R.id.slider_focus);
        sliderEnergy = findViewById(R.id.slider_energy);
        sliderWorkPressure = findViewById(R.id.slider_work_pressure);
        sliderDistraction = findViewById(R.id.slider_distraction);
        tvSleepHoursVal = findViewById(R.id.tv_sleep_hours_value);
        tvSleepQualityVal = findViewById(R.id.tv_sleep_quality_value);
        tvFocusVal = findViewById(R.id.tv_focus_value);
        tvEnergyVal = findViewById(R.id.tv_energy_value);
        tvWorkPressureVal = findViewById(R.id.tv_work_pressure_value);
        tvDistractionVal = findViewById(R.id.tv_distraction_value);

        // Step 4
        sliderSocialMediaUse = findViewById(R.id.slider_social_media_use);
        sliderLateNightUse = findViewById(R.id.slider_late_night_use);
        sliderAppAddictionRisk = findViewById(R.id.slider_app_addiction_risk);
        sliderOverusePattern = findViewById(R.id.slider_overuse_pattern);
        sliderBingeScrolling = findViewById(R.id.slider_binge_scrolling);
        sliderAppSwitching = findViewById(R.id.slider_app_switching);
        tvSocialMediaVal = findViewById(R.id.tv_social_media_value);
        tvLateNightVal = findViewById(R.id.tv_late_night_value);
        tvAddictionVal = findViewById(R.id.tv_addiction_value);
        tvOveruseVal = findViewById(R.id.tv_overuse_value);
        tvBingeVal = findViewById(R.id.tv_binge_value);
        tvSwitchingVal = findViewById(R.id.tv_switching_value);
        chipGroupTriggerApps = findViewById(R.id.chip_group_trigger_apps);
        chipGroupVulnerabilityTime = findViewById(R.id.chip_group_vulnerability_time);
        sliderScreenAwareness = findViewById(R.id.slider_screen_awareness);
        tvScreenAwarenessVal = findViewById(R.id.tv_screen_awareness_value);
        tvScreenAwarenessDesc = findViewById(R.id.tv_screen_awareness_desc);
        tvSocialMediaDesc = findViewById(R.id.tv_social_media_desc);
        tvLateNightDesc = findViewById(R.id.tv_late_night_desc);
        tvAddictionDesc = findViewById(R.id.tv_addiction_desc);
        tvDigitalRiskBadge = findViewById(R.id.tv_digital_risk_badge);
        tvDigitalRiskSummary = findViewById(R.id.tv_digital_risk_summary);
        progressDigitalRisk = findViewById(R.id.progress_digital_risk);

        // Step 5
        sliderRoutineConsistency = findViewById(R.id.slider_routine_consistency);
        sliderProductiveHabits = findViewById(R.id.slider_productive_habits);
        sliderProcrastination = findViewById(R.id.slider_procrastination);
        sliderFeelingStuck = findViewById(R.id.slider_feeling_stuck);
        sliderPhysicalActivity = findViewById(R.id.slider_physical_activity);
        sliderRoutineStability = findViewById(R.id.slider_routine_stability);
        sliderScreenFree = findViewById(R.id.slider_screen_free);
        sliderAddictionScale = findViewById(R.id.slider_addiction_scale);
        sliderPurposeScore = findViewById(R.id.slider_purpose_score);
        tvRoutineVal = findViewById(R.id.tv_routine_value);
        tvProductiveVal = findViewById(R.id.tv_productive_value);
        tvProcrastinationVal = findViewById(R.id.tv_procrastination_value);
        tvStuckVal = findViewById(R.id.tv_stuck_value);
        tvPhysicalVal = findViewById(R.id.tv_physical_value);
        tvRoutineStabilityVal = findViewById(R.id.tv_routine_stability_value);
        tvScreenFreeVal = findViewById(R.id.tv_screen_free_value);
        tvAddictionScaleVal = findViewById(R.id.tv_addiction_scale_value);
        tvPurposeScoreVal = findViewById(R.id.tv_purpose_score_value);
        chipGroupExerciseFrequency = findViewById(R.id.chip_group_exercise_frequency);
        chipGroupMentalHealth = findViewById(R.id.chip_group_mental_health);

        // Step 6
        sliderSocialSupport = findViewById(R.id.slider_social_support);
        tvSupportVal = findViewById(R.id.tv_support_value);
        tvSupportDesc = findViewById(R.id.tv_support_desc);
        sliderSocialQuality = findViewById(R.id.slider_social_quality);
        tvSocialQualityVal = findViewById(R.id.tv_social_quality_value);
        tvSocialQualityDesc = findViewById(R.id.tv_social_quality_desc);
        sliderReadiness = findViewById(R.id.slider_readiness);
        tvReadinessVal = findViewById(R.id.tv_readiness_value);
        tvReadinessDesc = findViewById(R.id.tv_readiness_desc);
        switchSupportNeeded = findViewById(R.id.switch_support_needed);
        switchSafetySupport = findViewById(R.id.switch_safety_support);

        // Step 6 — Nutrition
        sliderWaterIntake = findViewById(R.id.slider_water_intake);
        sliderCaffeineIntake = findViewById(R.id.slider_caffeine_intake);
        sliderDietQuality = findViewById(R.id.slider_diet_quality);
        sliderMealRegularity = findViewById(R.id.slider_meal_regularity);
        sliderSugarIntake = findViewById(R.id.slider_sugar_intake);
        sliderEmotionalEating = findViewById(R.id.slider_emotional_eating);
        sliderLateEating = findViewById(R.id.slider_late_eating);
        tvWaterIntakeVal = findViewById(R.id.tv_water_intake_value);
        tvCaffeineVal = findViewById(R.id.tv_caffeine_value);
        tvDietQualityVal = findViewById(R.id.tv_diet_quality_value);
        tvMealRegularityVal = findViewById(R.id.tv_meal_regularity_value);
        tvSugarIntakeVal = findViewById(R.id.tv_sugar_intake_value);
        tvEmotionalEatingVal = findViewById(R.id.tv_emotional_eating_value);
        tvLateEatingVal = findViewById(R.id.tv_late_eating_value);
        tvWaterIntakeDesc = findViewById(R.id.tv_water_intake_desc);
        tvCaffeineDesc = findViewById(R.id.tv_caffeine_desc);
        tvDietQualityDesc = findViewById(R.id.tv_diet_quality_desc);
        tvEmotionalEatingDesc = findViewById(R.id.tv_emotional_eating_desc);
        chipGroupAlcohol = findViewById(R.id.chip_group_alcohol);

        // Completion
        viewCompleteCircle = findViewById(R.id.view_complete_circle);
        progressComplete = findViewById(R.id.progress_complete);
    }

    // ─── Slider → Label wiring ──────────────────────────────────────

    private void wireSliderLabels() {
        wireIntSlider(sliderStress, tvStressVal);
        wireIntSlider(sliderAnxiety, tvAnxietyVal);
        wireIntSlider(sliderMotivation, tvMotivationVal);
        wireIntSlider(sliderLoneliness, tvLonelinessVal);
        wireIntSlider(sliderSelfDoubt, tvSelfDoubtVal);
        wireIntSlider(sliderOverthinking, tvOverthinkingVal);

        // Sleep hours — show decimal
        if (sliderSleepHours != null && tvSleepHoursVal != null) {
            sliderSleepHours.addOnChangeListener((s, v, u) ->
                    tvSleepHoursVal.setText(String.format(Locale.US, "%.1fh", v)));
        }
        wireIntSlider(sliderSleepQuality, tvSleepQualityVal);
        wireIntSlider(sliderFocus, tvFocusVal);
        wireIntSlider(sliderEnergy, tvEnergyVal);
        wireIntSlider(sliderWorkPressure, tvWorkPressureVal);
        wireIntSlider(sliderDistraction, tvDistractionVal);

        wireIntSlider(sliderSocialMediaUse, tvSocialMediaVal);
        wireIntSlider(sliderLateNightUse, tvLateNightVal);
        wireIntSlider(sliderAppAddictionRisk, tvAddictionVal);
        wireIntSlider(sliderOverusePattern, tvOveruseVal);
        wireIntSlider(sliderBingeScrolling, tvBingeVal);
        wireIntSlider(sliderAppSwitching, tvSwitchingVal);

        wireIntSlider(sliderRoutineConsistency, tvRoutineVal);
        wireIntSlider(sliderProductiveHabits, tvProductiveVal);
        wireIntSlider(sliderProcrastination, tvProcrastinationVal);
        wireIntSlider(sliderFeelingStuck, tvStuckVal);
        wireIntSlider(sliderPhysicalActivity, tvPhysicalVal);
        wireIntSlider(sliderRoutineStability, tvRoutineStabilityVal);
        wireIntSlider(sliderScreenFree, tvScreenFreeVal);
        wireIntSlider(sliderAddictionScale, tvAddictionScaleVal);
        wireIntSlider(sliderPurposeScore, tvPurposeScoreVal);

        wireIntSlider(sliderSocialSupport, tvSupportVal);
        wireIntSlider(sliderSocialQuality, tvSocialQualityVal);
        wireIntSlider(sliderReadiness, tvReadinessVal);
        wireIntSlider(sliderScreenAwareness, tvScreenAwarenessVal);

        // Nutrition sliders
        wireIntSlider(sliderWaterIntake, tvWaterIntakeVal);
        wireIntSlider(sliderCaffeineIntake, tvCaffeineVal);
        wireIntSlider(sliderDietQuality, tvDietQualityVal);
        wireIntSlider(sliderMealRegularity, tvMealRegularityVal);
        wireIntSlider(sliderSugarIntake, tvSugarIntakeVal);
        wireIntSlider(sliderEmotionalEating, tvEmotionalEatingVal);
        wireIntSlider(sliderLateEating, tvLateEatingVal);

        // ── Live clinical descriptors (Advanced) ──
        wireDescriptor(sliderSocialMediaUse, tvSocialMediaDesc, new String[]{
                "Minimal — rarely check social media",
                "Low — occasional check, easy to stop",
                "Moderate — you check often but can stop",
                "High — strong pull, hard to put down",
                "Extreme — it dominates your screen time"
        });
        wireDescriptor(sliderLateNightUse, tvLateNightDesc, new String[]{
                "Rare — phone is off before bed",
                "Occasional — maybe once a week",
                "Sometimes — a few nights per week",
                "Frequent — most nights in bed with phone",
                "Every night — can't sleep without it"
        });
        wireDescriptor(sliderAppAddictionRisk, tvAddictionDesc, new String[]{
                "Low — you feel in control",
                "Mild — occasional urges to check",
                "Moderate — you notice the pull but manage it",
                "High — you struggle to stop once you start",
                "Severe — it controls your day"
        });
        wireDescriptor(sliderScreenAwareness, tvScreenAwarenessDesc, new String[]{
                "Unaware — don't think about it",
                "Slightly — vaguely notice",
                "Starting — beginning to see patterns",
                "Noticing — it bothers you sometimes",
                "Aware — you know it affects you",
                "Conscious — actively trying to change",
                "Motivated — ready for intervention",
                "Focused — it's a top priority",
                "Committed — taking daily action",
                "Fully aware — this is why you're here"
        });
        wireDescriptor(sliderSocialSupport, tvSupportDesc, new String[]{
                "Isolated — no one to turn to",
                "Limited — one or two people",
                "Some support — a few people you can talk to",
                "Good support — reliable friends/family",
                "Strong network — deeply supported"
        });
        wireDescriptor(sliderSocialQuality, tvSocialQualityDesc, new String[]{
                "Draining — interactions feel exhausting",
                "Surface-level — not much depth",
                "Mixed — some connections feel meaningful",
                "Good — mostly fulfilling relationships",
                "Deep — truly meaningful connections"
        });
        wireDescriptor(sliderReadiness, tvReadinessDesc, new String[]{
                "Not thinking about it yet",
                "Aware but not ready",
                "Precontemplation — maybe someday",
                "Contemplation — thinking about change",
                "Contemplation — weighing pros and cons",
                "Preparation — planning to act soon",
                "Preparation — setting things up",
                "Action — actively making changes",
                "Action — building new habits",
                "Maintenance — sustaining progress"
        });

        // ── Nutrition descriptors ──
        wireDescriptor(sliderWaterIntake, tvWaterIntakeDesc, new String[]{
                "Critically low — barely any water",
                "Low — 2-3 glasses, often dehydrated",
                "Moderate — some water but could improve",
                "Good — regular hydration throughout the day",
                "Excellent — well hydrated, 2+ liters daily"
        });
        wireDescriptor(sliderCaffeineIntake, tvCaffeineDesc, new String[]{
                "None — caffeine-free",
                "Light — 1 cup, minimal impact",
                "Moderate — 2-3 cups, within safe range",
                "High — 4+ cups, likely affecting sleep",
                "Heavy — 5+ cups, anxiety/sleep disruption risk"
        });
        wireDescriptor(sliderDietQuality, tvDietQualityDesc, new String[]{
                "Poor — mostly processed and fast food",
                "Below average — limited variety",
                "Mixed — some healthy, some processed",
                "Good — mostly whole foods and variety",
                "Excellent — balanced, nutrient-rich diet"
        });
        wireDescriptor(sliderEmotionalEating, tvEmotionalEatingDesc, new String[]{
                "Never — food isn't a coping mechanism",
                "Rarely — occasional comfort eating",
                "Sometimes — stress triggers snacking",
                "Often — regularly eat to manage emotions",
                "Constant — food is my primary coping tool"
        });

        // ── Digital risk preview (live-updating) ──
        wireDigitalRiskPreview();
    }

    private void wireDescriptor(Slider slider, TextView desc, String[] labels) {
        if (slider == null || desc == null) return;
        slider.addOnChangeListener((s, value, fromUser) -> {
            int idx = Math.round(value) - (int) s.getValueFrom();
            if (idx >= 0 && idx < labels.length) {
                desc.setText(labels[idx]);
            }
        });
    }

    private void wireDigitalRiskPreview() {
        if (progressDigitalRisk == null) return;
        Slider.OnChangeListener riskUpdater = (s, v, u) -> updateDigitalRiskPreview();
        if (sliderSocialMediaUse != null) sliderSocialMediaUse.addOnChangeListener(riskUpdater);
        if (sliderLateNightUse != null) sliderLateNightUse.addOnChangeListener(riskUpdater);
        if (sliderAppAddictionRisk != null) sliderAppAddictionRisk.addOnChangeListener(riskUpdater);
        if (sliderBingeScrolling != null) sliderBingeScrolling.addOnChangeListener(riskUpdater);
        if (sliderAppSwitching != null) sliderAppSwitching.addOnChangeListener(riskUpdater);
        updateDigitalRiskPreview();
    }

    private void updateDigitalRiskPreview() {
        float social = sliderSocialMediaUse != null ? sliderSocialMediaUse.getValue() : 3;
        float lateNight = sliderLateNightUse != null ? sliderLateNightUse.getValue() : 3;
        float addiction = sliderAppAddictionRisk != null ? sliderAppAddictionRisk.getValue() : 3;
        float binge = sliderBingeScrolling != null ? sliderBingeScrolling.getValue() : 3;
        float switching = sliderAppSwitching != null ? sliderAppSwitching.getValue() : 3;

        float risk = ((social / 5f) * 0.25f + (lateNight / 5f) * 0.20f +
                (addiction / 5f) * 0.25f + (binge / 5f) * 0.15f + (switching / 5f) * 0.15f);
        int percent = Math.round(risk * 100);

        if (progressDigitalRisk != null) progressDigitalRisk.setProgressCompat(percent, true);

        String label, summary;
        int color;
        if (percent >= 75) {
            label = "High risk";
            color = ColorSystem.RED;
            summary = "Your digital habits show significant dependency. MindTrace will provide strong intervention support.";
        } else if (percent >= 50) {
            label = "Moderate";
            color = ColorSystem.AMBER;
            summary = "Your digital dependency is moderate. MindTrace will calibrate interventions to help.";
        } else if (percent >= 30) {
            label = "Low-moderate";
            color = ColorSystem.PRIMARY;
            summary = "Your habits are mostly healthy with room for improvement. Light guidance ahead.";
        } else {
            label = "Low risk";
            color = ColorSystem.GREEN;
            summary = "Your digital habits look healthy. MindTrace will focus on maintaining your balance.";
        }

        if (tvDigitalRiskBadge != null) tvDigitalRiskBadge.setText(label);
        if (tvDigitalRiskSummary != null) tvDigitalRiskSummary.setText(summary);
        if (progressDigitalRisk != null)
            progressDigitalRisk.setIndicatorColor(color);
    }

    private void wireIntSlider(Slider slider, TextView label) {
        if (slider == null || label == null) return;
        slider.addOnChangeListener((s, value, fromUser) ->
                label.setText(String.valueOf(Math.round(value))));
    }

    // ─── Interactions ────────────────────────────────────────────────

    private void setupInteractions() {
        applyForwardAnimations();
        UiMotion.attachPressAnimation(btnBack);
        UiMotion.attachPressAnimation(btnNext);

        btnBack.setOnClickListener(v -> goBack());
        btnNext.setOnClickListener(v -> goNext());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep > 0 && currentStep <= TOTAL_STEPS - 1) {
                    goBack();
                } else {
                    savePartialProgress();
                    finish();
                }
            }
        });
    }

    private void goBack() {
        if (currentStep <= 0) return;
        currentStep--;
        applyBackwardAnimations();
        viewFlipper.showPrevious();
        updateStepUi(true);
    }

    private void goNext() {
        if (currentStep < TOTAL_STEPS - 1) {
            currentStep++;
            applyForwardAnimations();
            viewFlipper.showNext();
            updateStepUi(true);
            savePartialProgress();
        } else {
            showCompletionAndSubmit();
        }
    }

    // ─── Step UI update ──────────────────────────────────────────────

    private void updateStepUi(boolean animate) {
        boolean isComplete = currentStep >= TOTAL_STEPS;

        if (!isComplete) {
            tvStepBadge.setVisibility(View.VISIBLE);
            tvStepBadge.setText("STEP " + (currentStep + 1) + " OF " + TOTAL_STEPS);
            tvTitle.setText(stepTitles[currentStep]);
            tvSubtitle.setText(stepSubtitles[currentStep]);
            tvTitle.setVisibility(View.VISIBLE);
            tvSubtitle.setVisibility(View.VISIBLE);
            progressIndicator.setProgressCompat(
                    Math.round(((currentStep + 1) * 100f) / TOTAL_STEPS), animate);
        } else {
            tvStepBadge.setVisibility(View.GONE);
            tvTitle.setVisibility(View.GONE);
            tvSubtitle.setVisibility(View.GONE);
            progressIndicator.setProgressCompat(100, true);
        }

        btnBack.setVisibility(currentStep > 0 && !isComplete ? View.VISIBLE : View.GONE);
        btnNext.setVisibility(isComplete ? View.GONE : View.VISIBLE);
        btnNext.setText(currentStep == TOTAL_STEPS - 1 ? "Finish setup" : "Continue");

        // Animate header text change
        if (animate && !isComplete) {
            tvTitle.setAlpha(0f);
            tvTitle.setTranslationY(12f);
            tvTitle.animate().alpha(1f).translationY(0f).setDuration(300).start();
            tvSubtitle.setAlpha(0f);
            tvSubtitle.animate().alpha(1f).setStartDelay(80).setDuration(250).start();
        }
    }

    // ─── Completion ──────────────────────────────────────────────────

    private void showCompletionAndSubmit() {
        currentStep = STEP_COMPLETE;
        applyForwardAnimations();
        viewFlipper.showNext();
        updateStepUi(true);

        // Animate the completion circle
        if (viewCompleteCircle != null) {
            viewCompleteCircle.setScaleX(0f);
            viewCompleteCircle.setScaleY(0f);
            viewCompleteCircle.setAlpha(0f);
            viewCompleteCircle.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(500)
                    .setStartDelay(200)
                    .start();
        }
        if (progressComplete != null) {
            progressComplete.setVisibility(View.VISIBLE);
        }

        // Submit data
        btnNext.setEnabled(false);
        btnBack.setEnabled(false);
        viewModel.completeOnboarding(buildProfile());
        clearPartialProgress();
    }

    // ─── Profile building ────────────────────────────────────────────

    private OnboardingProfile buildProfile() {
        OnboardingProfile p = new OnboardingProfile();
        p.name = textValue(etName);
        p.ageRange = selectedChipText(chipGroupAge);
        p.primaryGoal = selectedChipText(chipGroupGoal);
        p.helpAreasCsv = joinCheckedChipTexts(chipGroupHelp);

        p.stressLevel = sliderInt(sliderStress);
        p.anxietyLevel = sliderInt(sliderAnxiety);
        p.motivationLevel = sliderInt(sliderMotivation);
        p.lonelinessLevel = sliderInt(sliderLoneliness);
        p.selfDoubtLevel = sliderInt(sliderSelfDoubt);
        p.overthinkingLevel = sliderInt(sliderOverthinking);

        p.sleepHours = sliderSleepHours != null ? sliderSleepHours.getValue() : 7f;
        p.sleepQuality = sliderInt(sliderSleepQuality);
        p.focusLevel = sliderInt(sliderFocus);
        p.energyLevel = sliderInt(sliderEnergy);
        p.workPressure = sliderInt(sliderWorkPressure);
        p.distractionLevel = sliderInt(sliderDistraction);

        p.socialMediaUse = sliderInt(sliderSocialMediaUse);
        p.lateNightPhoneUse = sliderInt(sliderLateNightUse);
        p.appAddictionRisk = sliderInt(sliderAppAddictionRisk);
        p.overusePatternLevel = sliderInt(sliderOverusePattern);
        p.bingeScrollingLevel = sliderInt(sliderBingeScrolling);
        p.appSwitchingHabit = sliderInt(sliderAppSwitching);

        p.routineConsistency = sliderInt(sliderRoutineConsistency);
        p.productiveHabits = sliderInt(sliderProductiveHabits);
        p.procrastinationLevel = sliderInt(sliderProcrastination);
        p.physicalActivity = sliderInt(sliderPhysicalActivity);
        p.feelingStuck = sliderInt(sliderFeelingStuck);

        p.socialSupportLevel = sliderInt(sliderSocialSupport);
        p.supportNeeded = switchSupportNeeded != null && switchSupportNeeded.isChecked();
        p.safetySupportEnabled = switchSafetySupport != null && switchSafetySupport.isChecked();

        // Premium clinical markers (Tasks 2.G.2–2.G.7)
        p.routineStability = sliderInt(sliderRoutineStability);
        p.screenFreeActivities = sliderInt(sliderScreenFree);
        p.addictionScale = sliderInt(sliderAddictionScale);
        p.purposeScore = sliderInt(sliderPurposeScore);
        p.exerciseFrequency = selectedChipText(chipGroupExerciseFrequency);
        String mhHistory = selectedChipText(chipGroupMentalHealth);
        p.mentalHealthHistory = mhHistory.isEmpty() ? null : mhHistory.toLowerCase();

        // Advanced premium fields
        p.triggerApps = joinCheckedChipTexts(chipGroupTriggerApps);
        p.screenTimeAwareness = sliderInt(sliderScreenAwareness);
        String vulnTime = selectedChipText(chipGroupVulnerabilityTime);
        if (vulnTime.contains("Morning")) p.peakVulnerabilityTime = "morning";
        else if (vulnTime.contains("Afternoon")) p.peakVulnerabilityTime = "afternoon";
        else if (vulnTime.contains("Evening")) p.peakVulnerabilityTime = "evening";
        else if (vulnTime.contains("Late")) p.peakVulnerabilityTime = "late_night";
        else p.peakVulnerabilityTime = "unpredictable";
        p.socialQualityBaseline = sliderInt(sliderSocialQuality);
        p.readinessToChange = sliderInt(sliderReadiness);

        // Nutrition & hydration baseline
        p.waterIntake = sliderInt(sliderWaterIntake);
        p.caffeineLevel = sliderInt(sliderCaffeineIntake);
        String alcohol = selectedChipText(chipGroupAlcohol);
        p.alcoholFrequency = alcohol.isEmpty() ? "Never" : alcohol;
        p.dietQuality = sliderInt(sliderDietQuality);
        p.mealRegularity = sliderInt(sliderMealRegularity);
        p.sugarIntake = sliderInt(sliderSugarIntake);
        p.emotionalEating = sliderInt(sliderEmotionalEating);
        p.lateNightEating = sliderInt(sliderLateEating);

        // Auto-compute coping style from patterns
        p.copingStyle = inferCopingStyle(p);
        p.onboardingComplete = true;
        p.timestamp = System.currentTimeMillis();
        return p;
    }

    /**
     * Infers primary coping style from onboarding profile patterns.
     * Uses a weighted signal approach.
     */
    private String inferCopingStyle(OnboardingProfile p) {
        // Avoidant: high distraction + high binge + low purpose
        int avoidant = p.distractionLevel + p.bingeScrollingLevel + (6 - p.purposeScore);
        // Emotional: high anxiety + high overthinking + high loneliness
        int emotional = p.anxietyLevel + p.overthinkingLevel + p.lonelinessLevel;
        // Social: high social support + high social media
        int social = p.socialSupportLevel + p.socialMediaUse;
        // Problem-focused: high motivation + high routine + high purpose
        int focused = p.motivationLevel + p.routineConsistency + p.purposeScore;

        if (focused >= avoidant && focused >= emotional && focused >= social) return "problem_focused";
        if (avoidant >= emotional && avoidant >= social) return "avoidant";
        if (emotional >= social) return "emotional";
        return "social";
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private int sliderInt(Slider slider) {
        return slider != null ? Math.round(slider.getValue()) : 3;
    }

    private String selectedChipText(ChipGroup group) {
        if (group == null) return "";
        int checkedId = group.getCheckedChipId();
        if (checkedId == View.NO_ID) return "";
        Chip chip = findViewById(checkedId);
        return chip == null ? "" : chip.getText().toString().trim();
    }

    private String joinCheckedChipTexts(ChipGroup group) {
        if (group == null) return "";
        List<String> values = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                values.add(((Chip) child).getText().toString().trim());
            }
        }
        return android.text.TextUtils.join(", ", values);
    }

    private String textValue(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) return "";
        return editText.getText().toString().trim();
    }

    // ─── Animations ──────────────────────────────────────────────────

    private void applyForwardAnimations() {
        viewFlipper.setInAnimation(this, R.anim.onboarding_slide_in_right);
        viewFlipper.setOutAnimation(this, R.anim.onboarding_slide_out_left);
    }

    private void applyBackwardAnimations() {
        viewFlipper.setInAnimation(this, R.anim.onboarding_slide_in_left);
        viewFlipper.setOutAnimation(this, R.anim.onboarding_slide_out_right);
    }

    // ─── Partial progress save/restore ───────────────────────────────

    private void savePartialProgress() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(KEY_STEP, currentStep)
                .apply();
    }

    private void clearPartialProgress() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
    }

    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt("step_index", 0);
        } else {
            currentStep = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_STEP, 0);
        }
        currentStep = Math.max(0, Math.min(TOTAL_STEPS - 1, currentStep));
        viewFlipper.setDisplayedChild(currentStep);
    }

    // ─── System bars ─────────────────────────────────────────────────

    private void configureSystemBars() {
        int darkBg = Color.parseColor("#0C1220");
        int darkFooter = Color.parseColor("#0E1525");
        getWindow().setStatusBarColor(darkBg);
        getWindow().setNavigationBarColor(darkFooter);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Clear light flags — dark bg needs light (white) icons
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("step_index", currentStep);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentStep < TOTAL_STEPS) {
            savePartialProgress();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
