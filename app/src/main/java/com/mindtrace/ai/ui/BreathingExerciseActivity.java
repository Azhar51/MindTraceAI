package com.mindtrace.ai.ui;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.ExerciseEngine;
import com.mindtrace.ai.viewmodel.CrisisViewModel;

import java.util.Locale;

/**
 * Fullscreen animated breathing exercise activity.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Animated circle that expands on inhale, holds, contracts on exhale</li>
 *   <li>Phase label + countdown timer per phase</li>
 *   <li>Cycle counter ("Cycle 3 of 8")</li>
 *   <li>Phase dots showing current position</li>
 *   <li>Haptic feedback on phase transitions</li>
 *   <li>Timer showing total elapsed time</li>
 *   <li>Logs completion to CrisisViewModel with distress data</li>
 * </ul>
 */
public class BreathingExerciseActivity extends AppCompatActivity {

    public static final String EXTRA_EXERCISE_INDEX = "exercise_index";
    public static final String EXTRA_PRE_DISTRESS = "pre_distress";

    private com.mindtrace.ai.ui.components.BreathingRingView breathingCircle;
    private TextView tvPhaseLbl, tvCountdown, tvCycleCounter, tvExerciseName, tvTimer;
    private View dotInhale, dotHold1, dotExhale, dotHold2;
    private MaterialButton btnPause, btnFinish;

    private ExerciseEngine.BreathingExercise exercise;
    private CrisisViewModel crisisViewModel;
    private Vibrator vibrator;

    private int currentCycle = 1;
    private int currentPhase = 0; // 0=inhale, 1=hold1, 2=exhale, 3=hold2
    private boolean isPaused = false;
    private boolean isFinished = false;
    private int preDistress = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startTimeMs;
    private ValueAnimator circleAnimator;

    // Phase colors
    private int colorInhale, colorHold, colorExhale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.crisis_bg));

        setContentView(R.layout.activity_breathing_exercise);

        crisisViewModel = new ViewModelProvider(this).get(CrisisViewModel.class);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Get exercise
        int idx = getIntent().getIntExtra(EXTRA_EXERCISE_INDEX, 0);
        preDistress = getIntent().getIntExtra(EXTRA_PRE_DISTRESS, 5);
        exercise = ExerciseEngine.getAllBreathingExercises().get(
                Math.min(idx, ExerciseEngine.getAllBreathingExercises().size() - 1));

        // Colors
        colorInhale = ContextCompat.getColor(this, R.color.breathe_in);
        colorHold = ContextCompat.getColor(this, R.color.breathe_hold);
        colorExhale = ContextCompat.getColor(this, R.color.breathe_out);

        bindViews();
        setupButtons();
        startExercise();
    }

    private void bindViews() {
        breathingCircle = findViewById(R.id.breathing_circle);
        breathingCircle.setPhaseColors(
                colorInhale, colorHold, colorExhale);
        tvPhaseLbl = findViewById(R.id.tv_phase_label);
        tvCountdown = findViewById(R.id.tv_phase_countdown);
        tvCycleCounter = findViewById(R.id.tv_cycle_counter);
        tvExerciseName = findViewById(R.id.tv_exercise_name);
        tvTimer = findViewById(R.id.tv_timer);
        dotInhale = findViewById(R.id.dot_inhale);
        dotHold1 = findViewById(R.id.dot_hold1);
        dotExhale = findViewById(R.id.dot_exhale);
        dotHold2 = findViewById(R.id.dot_hold2);
        btnPause = findViewById(R.id.btn_pause);
        btnFinish = findViewById(R.id.btn_finish);

        tvExerciseName.setText(exercise.name);
        updateCycleCounter();
    }

    private void setupButtons() {
        findViewById(R.id.btn_close).setOnClickListener(v -> finishExercise(false));

        btnPause.setOnClickListener(v -> {
            isPaused = !isPaused;
            btnPause.setText(isPaused ? "Resume" : "Pause");
            if (!isPaused) runCurrentPhase();
        });

        btnFinish.setOnClickListener(v -> finishExercise(currentCycle > 1));
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXERCISE LOOP
    // ═══════════════════════════════════════════════════════════════════

    private void startExercise() {
        startTimeMs = System.currentTimeMillis();
        handler.post(timerRunnable);
        currentCycle = 1;
        currentPhase = 0;
        runCurrentPhase();
    }

    private void runCurrentPhase() {
        if (isPaused || isFinished) return;

        long durationMs;
        String label;
        int color;
        float targetScale;

        switch (currentPhase) {
            case 0: // Inhale
                durationMs = exercise.inhaleMs;
                label = "Breathe In";
                color = colorInhale;
                targetScale = 1.4f;
                highlightDot(dotInhale);
                break;
            case 1: // Hold 1
                durationMs = exercise.hold1Ms;
                label = "Hold";
                color = colorHold;
                targetScale = 1.4f;
                highlightDot(dotHold1);
                break;
            case 2: // Exhale
                durationMs = exercise.exhaleMs;
                label = "Breathe Out";
                color = colorExhale;
                targetScale = 0.8f;
                highlightDot(dotExhale);
                break;
            case 3: // Hold 2
                durationMs = exercise.hold2Ms;
                label = "Hold";
                color = colorHold;
                targetScale = 0.8f;
                highlightDot(dotHold2);
                break;
            default:
                return;
        }

        // Skip zero-duration phases
        if (durationMs <= 0) {
            advancePhase();
            return;
        }

        // Smooth crossfade phase label
        crossfadePhaseLabel(label, color);

        // Phase-specific haptic
        hapticPhase(currentPhase);

        // Animate circle scale + phase color
        breathingCircle.setPhaseColor(currentPhase, 400);
        animateCircle(targetScale, durationMs);

        // Countdown
        runCountdown(durationMs);
    }

    /**
     * Crossfade text: fade out old label, swap, fade in new.
     */
    private void crossfadePhaseLabel(String newLabel, int color) {
        tvPhaseLbl.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            tvPhaseLbl.setText(newLabel);
            tvPhaseLbl.setTextColor(color);
            tvCountdown.setTextColor(color);
            tvPhaseLbl.animate().alpha(1f).setDuration(200).start();
        }).start();
    }

    private void animateCircle(float targetScale, long durationMs) {
        if (circleAnimator != null) circleAnimator.cancel();

        float currentScale = breathingCircle.getScaleX();
        // Map view-scale to ring-scale (0.3 to 1.0 range)
        float startRing = mapScale(currentScale);
        float endRing = mapScale(targetScale);

        circleAnimator = ValueAnimator.ofFloat(startRing, endRing);
        circleAnimator.setDuration(durationMs);
        circleAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        circleAnimator.addUpdateListener(anim -> {
            float scale = (float) anim.getAnimatedValue();
            breathingCircle.setBreathingScale(scale);
        });
        circleAnimator.start();
    }

    /** Map the exercise scale range (0.8-1.4) to ring view range (0.4-1.0). */
    private float mapScale(float exerciseScale) {
        return 0.4f + (exerciseScale - 0.8f) * (0.6f / 0.6f);
    }

    private void runCountdown(long durationMs) {
        int totalSeconds = (int) (durationMs / 1000);
        for (int i = totalSeconds; i >= 1; i--) {
            final int sec = i;
            handler.postDelayed(() -> {
                if (!isPaused && !isFinished) {
                    tvCountdown.setText(String.valueOf(sec));
                }
            }, (totalSeconds - i) * 1000L);
        }

        // Advance after duration
        handler.postDelayed(() -> {
            if (!isPaused && !isFinished) advancePhase();
        }, durationMs);
    }

    private void advancePhase() {
        currentPhase++;
        if (currentPhase > 3) {
            currentPhase = 0;
            currentCycle++;
            if (currentCycle > exercise.totalCycles) {
                finishExercise(true);
                return;
            }
            updateCycleCounter();
        }
        runCurrentPhase();
    }

    // ═══════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private void highlightDot(View active) {
        View[] dots = {dotInhale, dotHold1, dotExhale, dotHold2};
        for (View dot : dots) {
            dot.setAlpha(dot == active ? 1.0f : 0.3f);
            dot.setScaleX(dot == active ? 1.3f : 1.0f);
            dot.setScaleY(dot == active ? 1.3f : 1.0f);
        }
    }

    private void updateCycleCounter() {
        tvCycleCounter.setText(String.format(Locale.US, "Cycle %d of %d",
                currentCycle, exercise.totalCycles));
    }

    private void hapticPulse() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    /**
     * Phase-specific haptic feedback — different patterns for inhale/hold/exhale.
     */
    private void hapticPhase(int phase) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            switch (phase) {
                case 0: // Inhale — gentle rising
                    vibrator.vibrate(VibrationEffect.createOneShot(30, 60));
                    break;
                case 1: // Hold — very subtle
                    vibrator.vibrate(VibrationEffect.createOneShot(20, 30));
                    break;
                case 2: // Exhale — soft falling
                    vibrator.vibrate(VibrationEffect.createOneShot(40, 80));
                    break;
                case 3: // Hold 2 — same as hold 1
                    vibrator.vibrate(VibrationEffect.createOneShot(20, 30));
                    break;
            }
        }
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinished) {
                long elapsed = (System.currentTimeMillis() - startTimeMs) / 1000;
                int mins = (int) (elapsed / 60);
                int secs = (int) (elapsed % 60);
                tvTimer.setText(String.format(Locale.US, "%d:%02d", mins, secs));
                handler.postDelayed(this, 1000);
            }
        }
    };

    // ═══════════════════════════════════════════════════════════════════
    // FINISH & LOG
    // ═══════════════════════════════════════════════════════════════════

    private void finishExercise(boolean completedFully) {
        if (isFinished) return;
        isFinished = true;

        handler.removeCallbacksAndMessages(null);
        if (circleAnimator != null) circleAnimator.cancel();

        long durationMs = System.currentTimeMillis() - startTimeMs;

        // Log completion
        crisisViewModel.logExerciseCompletion(
                "breathing", exercise.name, durationMs,
                preDistress, 0, completedFully);

        if (completedFully) {
            // ── Premium celebration ──
            UiMotion.hapticHeavy(breathingCircle);
            UiMotion.confettiBurst(breathingCircle, 16);

            // Post-exercise distress prompt
            showPostExercisePrompt(durationMs);
        } else {
            finish();
        }
    }

    /**
     * Post-exercise distress prompt — "How do you feel now?" dialog
     * with a mini slider before exit. Logs the reduction.
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
                .setTitle("✨ Exercise Complete!")
                .setView(layout)
                .setPositiveButton("Done", (d, w) -> {
                    int postDistress = (int) slider.getValue();
                    // Re-log with actual post-distress
                    crisisViewModel.logExerciseCompletion(
                            "breathing", exercise.name, durationMs,
                            preDistress, postDistress, true);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (circleAnimator != null) circleAnimator.cancel();
    }
}
