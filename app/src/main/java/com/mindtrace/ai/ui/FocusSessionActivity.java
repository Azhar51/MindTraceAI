package com.mindtrace.ai.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mindtrace.ai.R;
import com.mindtrace.ai.databinding.ActivityFocusSessionBinding;
import com.mindtrace.ai.services.FocusBlockerService;
import com.mindtrace.ai.utils.UiHaptics;

/**
 * Focus Session Activity — Pomodoro-style timer with strict mode (app blocker).
 *
 * <p>Migrated to ViewBinding to eliminate null-pointer risks from
 * {@code findViewById} calls.</p>
 */
public class FocusSessionActivity extends AppCompatActivity {

    private ActivityFocusSessionBinding binding;

    private ObjectAnimator pulseAnimator;

    private CountDownTimer focusTimer;
    private boolean isFocusActive = false;
    private long totalTimeInMillis = 30 * 60 * 1000L;
    private long timeLeftInMillis = 30 * 60 * 1000L;
    
    private int sessionCount = 1;

    private enum Mode { POMODORO, SHORT_BREAK, LONG_BREAK }
    private Mode currentMode = Mode.POMODORO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFocusSessionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> {
            UiHaptics.click(v);
            finish();
        });

        binding.tabPomodoro.setOnClickListener(v -> setMode(Mode.POMODORO));
        binding.tabShortBreak.setOnClickListener(v -> setMode(Mode.SHORT_BREAK));
        binding.tabLongBreak.setOnClickListener(v -> setMode(Mode.LONG_BREAK));

        binding.btnStartFocus.setOnClickListener(v -> {
            UiHaptics.click(v);
            if (isFocusActive) {
                stopFocusSession();
            } else {
                startFocusSession();
            }
        });
        
        setupPulseAnimation();
        setMode(Mode.POMODORO);
    }
    
    private void setupPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                binding.viewPulse,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.15f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.15f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.1f, 0.0f)
        );
        pulseAnimator.setDuration(2000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.RESTART);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
    }

    private void setMode(Mode mode) {
        if (isFocusActive) return; // Prevent changing mode while running
        currentMode = mode;
        UiHaptics.tick(binding.tabPomodoro);

        // Reset Tabs
        binding.tabPomodoro.setBackground(null);
        binding.tabPomodoro.setTextColor(getColor(R.color.text_secondary));
        binding.tabShortBreak.setBackground(null);
        binding.tabShortBreak.setTextColor(getColor(R.color.text_secondary));
        binding.tabLongBreak.setBackground(null);
        binding.tabLongBreak.setTextColor(getColor(R.color.text_secondary));

        switch (mode) {
            case POMODORO:
                binding.tabPomodoro.setBackgroundResource(R.drawable.bg_tab_selected);
                binding.tabPomodoro.setTextColor(getColor(R.color.white));
                totalTimeInMillis = 30 * 60 * 1000L;
                binding.tvFocusDesc.setText("Focus Time");
                binding.tvFocusDesc.setTextColor(getColor(R.color.primary));
                binding.progressTimer.setIndicatorColor(getColor(R.color.primary));
                break;
            case SHORT_BREAK:
                binding.tabShortBreak.setBackgroundResource(R.drawable.bg_tab_selected);
                binding.tabShortBreak.setTextColor(getColor(R.color.white));
                totalTimeInMillis = 5 * 60 * 1000L;
                binding.tvFocusDesc.setText("Short Break");
                binding.tvFocusDesc.setTextColor(getColor(R.color.color_success));
                binding.progressTimer.setIndicatorColor(getColor(R.color.color_success));
                break;
            case LONG_BREAK:
                binding.tabLongBreak.setBackgroundResource(R.drawable.bg_tab_selected);
                binding.tabLongBreak.setTextColor(getColor(R.color.white));
                totalTimeInMillis = 15 * 60 * 1000L;
                binding.tvFocusDesc.setText("Long Break");
                binding.tvFocusDesc.setTextColor(getColor(R.color.color_success));
                binding.progressTimer.setIndicatorColor(getColor(R.color.color_success));
                break;
        }

        timeLeftInMillis = totalTimeInMillis;
        updateTimerText();
        binding.progressTimer.setMax(1000);
        binding.progressTimer.setProgress(1000);
    }

    private void startFocusSession() {
        isFocusActive = true;
        binding.lottieConfetti.setVisibility(View.GONE);
        binding.btnStartFocus.setText("End Session Early");
        binding.btnStartFocus.setBackgroundColor(getColor(R.color.warning_red));
        
        pulseAnimator.start();

        focusTimer = new CountDownTimer(timeLeftInMillis, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
                int progress = (int) ((timeLeftInMillis * 1000) / totalTimeInMillis);
                binding.progressTimer.setProgressCompat(progress, true);
            }

            @Override
            public void onFinish() {
                handleSessionComplete();
            }
        }.start();

        if (currentMode == Mode.POMODORO && binding.switchStrictMode.isChecked()) {
            Intent serviceIntent = new Intent(this, FocusBlockerService.class);
            serviceIntent.putExtra("DURATION_MINUTES", totalTimeInMillis / 60000L);
            ContextCompat.startForegroundService(this, serviceIntent);
            Toast.makeText(this, "Deep Focus started. Distractions blocked.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleSessionComplete() {
        isFocusActive = false;
        timeLeftInMillis = 0;
        updateTimerText();
        binding.progressTimer.setProgressCompat(0, true);
        pulseAnimator.cancel();
        
        UiHaptics.success(this);
        
        if (currentMode == Mode.POMODORO) {
            sessionCount++;
            binding.tvSessionCount.setText("Session " + sessionCount);
            binding.lottieConfetti.setVisibility(View.VISIBLE);
            binding.lottieConfetti.playAnimation();
            Toast.makeText(this, "Great job! Time for a break.", Toast.LENGTH_LONG).show();
            setMode(sessionCount % 4 == 0 ? Mode.LONG_BREAK : Mode.SHORT_BREAK);
        } else {
            Toast.makeText(this, "Break over. Ready to focus?", Toast.LENGTH_LONG).show();
            setMode(Mode.POMODORO);
        }
        
        stopFocusSessionUI();
    }

    private void stopFocusSession() {
        if (focusTimer != null) focusTimer.cancel();
        timeLeftInMillis = totalTimeInMillis;
        updateTimerText();
        binding.progressTimer.setProgressCompat(1000, true);
        stopFocusSessionUI();
    }

    private void stopFocusSessionUI() {
        isFocusActive = false;
        pulseAnimator.cancel();
        binding.viewPulse.setScaleX(1f);
        binding.viewPulse.setScaleY(1f);
        binding.viewPulse.setAlpha(0.1f);
        
        Intent serviceIntent = new Intent(this, FocusBlockerService.class);
        stopService(serviceIntent);

        binding.btnStartFocus.setText("Start Session");
        binding.btnStartFocus.setBackgroundColor(getColor(R.color.primary));
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        binding.tvFocusTimer.setText(String.format("%02d:%02d", minutes, seconds));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (focusTimer != null) {
            focusTimer.cancel();
        }
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        binding = null;
    }
}
