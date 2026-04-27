package com.mindtrace.ai.ui;

import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.ExerciseEngine;
import com.mindtrace.ai.viewmodel.CrisisViewModel;

import java.util.List;
import java.util.Locale;

/**
 * Step-by-step grounding exercise activity.
 *
 * <p>Shows one grounding step at a time with large typography,
 * progress dots, and smooth transitions between steps.</p>
 */
public class GroundingExerciseActivity extends AppCompatActivity {

    public static final String EXTRA_EXERCISE_INDEX = "exercise_index";
    public static final String EXTRA_PRE_DISTRESS = "pre_distress";

    private static final String[] STEP_ICONS = {"👁️", "👂", "✋", "👃", "👅"};

    private TextView tvStepIcon, tvStepNumber, tvStepInstruction, tvStepDescription;
    private TextView tvExerciseName, tvStepTimer;
    private MaterialButton btnNext;
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

        setContentView(R.layout.activity_grounding_exercise);

        crisisViewModel = new ViewModelProvider(this).get(CrisisViewModel.class);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        startTimeMs = System.currentTimeMillis();

        // Get exercise
        int idx = getIntent().getIntExtra(EXTRA_EXERCISE_INDEX, 0);
        preDistress = getIntent().getIntExtra(EXTRA_PRE_DISTRESS, 5);
        List<ExerciseEngine.GroundingExercise> all = ExerciseEngine.getAllGroundingExercises();
        exercise = all.get(Math.min(idx, all.size() - 1));

        bindViews();
        showStep(0);
    }

    private void bindViews() {
        tvStepIcon = findViewById(R.id.tv_step_icon);
        tvStepNumber = findViewById(R.id.tv_step_number);
        tvStepInstruction = findViewById(R.id.tv_step_instruction);
        tvStepDescription = findViewById(R.id.tv_step_description);
        tvExerciseName = findViewById(R.id.tv_exercise_name);
        tvStepTimer = findViewById(R.id.tv_step_timer);
        btnNext = findViewById(R.id.btn_next);

        dots = new View[]{
                findViewById(R.id.dot_1), findViewById(R.id.dot_2),
                findViewById(R.id.dot_3), findViewById(R.id.dot_4),
                findViewById(R.id.dot_5)
        };

        tvExerciseName.setText(exercise.name);
        findViewById(R.id.btn_close).setOnClickListener(v -> finishExercise(false));
        btnNext.setOnClickListener(v -> nextStep());
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
        tvStepIcon.setText(icon);

        // Text
        tvStepNumber.setText(String.format(Locale.US, "Step %d of %d",
                stepIndex + 1, exercise.steps.size()));
        tvStepInstruction.setText(step.instruction);
        tvStepDescription.setText(step.detail != null ? step.detail :
                "Take a moment to notice...");

        // Timer hint
        tvStepTimer.setText("Take your time...");

        // Button text
        if (stepIndex == exercise.steps.size() - 1) {
            btnNext.setText("Finish ✨");
        } else {
            btnNext.setText("Next →");
        }

        // Progress dots
        updateDots(stepIndex);

        // Animate in with spring physics
        tvStepIcon.setAlpha(0f);
        tvStepIcon.setScaleX(0.5f);
        tvStepIcon.setScaleY(0.5f);
        tvStepIcon.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(350)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                .start();

        tvStepInstruction.setAlpha(0f);
        tvStepInstruction.setTranslationY(30f);
        tvStepInstruction.animate().alpha(1f).translationY(0f)
                .setDuration(500).setStartDelay(100)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        tvStepDescription.setAlpha(0f);
        tvStepDescription.animate().alpha(1f).setDuration(500).setStartDelay(200).start();

        // Haptic
        UiMotion.hapticClick(tvStepIcon);
    }

    private void nextStep() {
        showStep(currentStep + 1);
    }

    private void updateDots(int activeIndex) {
        for (int i = 0; i < dots.length; i++) {
            if (i < exercise.steps.size()) {
                dots[i].setVisibility(View.VISIBLE);
                if (i < activeIndex) {
                    // Completed
                    dots[i].setBackgroundResource(R.drawable.bg_chip_selected);
                    dots[i].setAlpha(0.6f);
                } else if (i == activeIndex) {
                    // Active — scale pulse
                    dots[i].setBackgroundResource(R.drawable.bg_chip_selected);
                    dots[i].setAlpha(1.0f);
                    dots[i].animate()
                            .scaleX(1.4f).scaleY(1.4f)
                            .setDuration(200)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                            .start();
                } else {
                    // Upcoming
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
            // ── Premium nature-themed celebration ──
            UiMotion.hapticHeavy(btnNext);
            UiMotion.confettiBurst(btnNext, 12);

            // Post-exercise distress prompt
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
}
