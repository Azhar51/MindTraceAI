package com.mindtrace.ai.ui;

import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.ExerciseEngine;
import com.mindtrace.ai.databinding.ActivityGroundingExerciseBinding;
import com.mindtrace.ai.viewmodel.CrisisViewModel;

import java.util.List;
import java.util.Locale;

/**
 * Step-by-step grounding exercise activity.
 *
 * <p>Shows one grounding step at a time with large typography,
 * progress dots, and smooth transitions between steps.</p>
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class GroundingExerciseActivity extends AppCompatActivity {

    public static final String EXTRA_EXERCISE_INDEX = "exercise_index";
    public static final String EXTRA_PRE_DISTRESS = "pre_distress";

    private static final String[] STEP_ICONS = {"👁️", "👂", "✋", "👃", "👅"};

    private ActivityGroundingExerciseBinding binding;
    private View[] dots;

    private ExerciseEngine.GroundingExercise exercise;
    private CrisisViewModel crisisViewModel;
    private Vibrator vibrator;

    private int currentStep = 0;
    private int preDistress = 5;
    private long startTimeMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.crisis_bg));

        binding = ActivityGroundingExerciseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        crisisViewModel = new ViewModelProvider(this).get(CrisisViewModel.class);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        startTimeMs = System.currentTimeMillis();

        // Get exercise
        int idx = getIntent().getIntExtra(EXTRA_EXERCISE_INDEX, 0);
        preDistress = getIntent().getIntExtra(EXTRA_PRE_DISTRESS, 5);
        List<ExerciseEngine.GroundingExercise> all = ExerciseEngine.getAllGroundingExercises();
        exercise = all.get(Math.min(idx, all.size() - 1));

        setupViews();
        showStep(0);
    }

    private void setupViews() {
        dots = new View[]{
                binding.dot1, binding.dot2,
                binding.dot3, binding.dot4,
                binding.dot5
        };

        binding.tvExerciseName.setText(exercise.name);
        binding.btnClose.setOnClickListener(v -> finishExercise(false));
        binding.btnNext.setOnClickListener(v -> nextStep());
    }

    // ═══════════════════════════════════════════════════════════════════
    // STEP DISPLAY
    // ═══════════════════════════════════════════════════════════════════

    private void showStep(int stepIndex) {
        if (stepIndex >= exercise.steps.size()) {
            finishExercise(true);
            return;
        }

        currentStep = stepIndex;
        ExerciseEngine.GroundingStep step = exercise.steps.get(stepIndex);

        // Icon
        String icon = stepIndex < STEP_ICONS.length ? STEP_ICONS[stepIndex] : "🌿";
        binding.tvStepIcon.setText(icon);

        // Text
        binding.tvStepNumber.setText(String.format(Locale.US, "Step %d of %d",
                stepIndex + 1, exercise.steps.size()));
        binding.tvStepInstruction.setText(step.instruction);
        binding.tvStepDescription.setText(step.detail != null ? step.detail :
                "Take a moment to notice...");

        // Timer hint
        binding.tvStepTimer.setText("Take your time...");

        // Button text
        if (stepIndex == exercise.steps.size() - 1) {
            binding.btnNext.setText("Finish ✨");
        } else {
            binding.btnNext.setText("Next →");
        }

        // Progress dots
        updateDots(stepIndex);

        // Animate in with spring physics
        binding.tvStepIcon.setAlpha(0f);
        binding.tvStepIcon.setScaleX(0.5f);
        binding.tvStepIcon.setScaleY(0.5f);
        binding.tvStepIcon.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(350)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                .start();

        binding.tvStepInstruction.setAlpha(0f);
        binding.tvStepInstruction.setTranslationY(30f);
        binding.tvStepInstruction.animate().alpha(1f).translationY(0f)
                .setDuration(500).setStartDelay(100)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        binding.tvStepDescription.setAlpha(0f);
        binding.tvStepDescription.animate().alpha(1f).setDuration(500).setStartDelay(200).start();

        // Haptic
        UiMotion.hapticClick(binding.tvStepIcon);
    }

    private void nextStep() {
        showStep(currentStep + 1);
    }

    private void updateDots(int activeIndex) {
        for (int i = 0; i < dots.length; i++) {
            if (i < exercise.steps.size()) {
                dots[i].setVisibility(View.VISIBLE);
                if (i < activeIndex) {
                    dots[i].setBackgroundResource(R.drawable.bg_chip_selected);
                    dots[i].setAlpha(0.6f);
                } else if (i == activeIndex) {
                    dots[i].setBackgroundResource(R.drawable.bg_chip_selected);
                    dots[i].setAlpha(1.0f);
                    dots[i].animate()
                            .scaleX(1.4f).scaleY(1.4f)
                            .setDuration(200)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                            .start();
                } else {
                    dots[i].setBackgroundResource(R.drawable.bg_chip_unselected);
                    dots[i].setAlpha(0.4f);
                    dots[i].setScaleX(1.0f);
                    dots[i].setScaleY(1.0f);
                }
            } else {
                dots[i].setVisibility(View.GONE);
            }
        }
    }

    private void hapticPulse() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FINISH & LOG
    // ═══════════════════════════════════════════════════════════════════

    private void finishExercise(boolean completedFully) {
        long durationMs = System.currentTimeMillis() - startTimeMs;

        crisisViewModel.logExerciseCompletion(
                "grounding", exercise.name, durationMs,
                preDistress, 0, completedFully);

        if (completedFully) {
            UiMotion.hapticHeavy(binding.btnNext);
            UiMotion.confettiBurst(binding.btnNext, 12);
            showPostExercisePrompt(durationMs);
        } else {
            finish();
        }
    }

    /**
     * Post-exercise distress prompt — "How do you feel now?" dialog
     * with a mini slider before exit. Logs the actual reduction.
     */
    private void showPostExercisePrompt(long durationMs) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        android.widget.TextView label = new android.widget.TextView(this);
        label.setText("How are you feeling now? (1 = calm, 10 = distressed)");
        label.setTextColor(0xCCFFFFFF);
        layout.addView(label);

        com.google.android.material.slider.Slider slider =
                new com.google.android.material.slider.Slider(this);
        slider.setValueFrom(1);
        slider.setValueTo(10);
        slider.setStepSize(1);
        slider.setValue(Math.max(1, preDistress - 2));
        layout.addView(slider);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🌿 Well done!")
                .setView(layout)
                .setPositiveButton("Done", (d, w) -> {
                    int postDistress = (int) slider.getValue();
                    crisisViewModel.logExerciseCompletion(
                            "grounding", exercise.name, durationMs,
                            preDistress, postDistress, true);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
