package com.mindtrace.ai.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.mindtrace.ai.R;
import com.mindtrace.ai.services.FocusBlockerService;
import com.mindtrace.ai.utils.UiHaptics;

public class FocusSessionActivity extends AppCompatActivity {

    private TextView tvFocusTimer, tvFocusDesc, tvSessionCount;
    private TextView tabPomodoro, tabShortBreak, tabLongBreak;
    private MaterialButton btnStartFocus;
    private MaterialSwitch switchStrictMode;
    private CircularProgressIndicator progressTimer;
    private LottieAnimationView lottieConfetti;
    private View viewPulse;
    
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
        setContentView(R.layout.activity_focus_session);

        ImageButton btnBack = findViewById(R.id.btn_back);
        tvFocusTimer = findViewById(R.id.tv_focus_timer);
        tvFocusDesc = findViewById(R.id.tv_focus_desc);
        tvSessionCount = findViewById(R.id.tv_session_count);
        tabPomodoro = findViewById(R.id.tab_pomodoro);
        tabShortBreak = findViewById(R.id.tab_short_break);
        tabLongBreak = findViewById(R.id.tab_long_break);
        btnStartFocus = findViewById(R.id.btn_start_focus);
        switchStrictMode = findViewById(R.id.switch_strict_mode);
        progressTimer = findViewById(R.id.progress_timer);
        lottieConfetti = findViewById(R.id.lottie_confetti);
        viewPulse = findViewById(R.id.view_pulse);

        btnBack.setOnClickListener(v -> {
            UiHaptics.click(v);
            finish();
        });

        tabPomodoro.setOnClickListener(v -> setMode(Mode.POMODORO));
        tabShortBreak.setOnClickListener(v -> setMode(Mode.SHORT_BREAK));
        tabLongBreak.setOnClickListener(v -> setMode(Mode.LONG_BREAK));

        btnStartFocus.setOnClickListener(v -> {
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
                viewPulse,
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
        UiHaptics.tick(tabPomodoro);

        // Reset Tabs
        tabPomodoro.setBackground(null);
        tabPomodoro.setTextColor(getColor(R.color.text_secondary));
        tabShortBreak.setBackground(null);
        tabShortBreak.setTextColor(getColor(R.color.text_secondary));
        tabLongBreak.setBackground(null);
        tabLongBreak.setTextColor(getColor(R.color.text_secondary));

        switch (mode) {
            case POMODORO:
                tabPomodoro.setBackgroundResource(R.drawable.bg_tab_selected);
                tabPomodoro.setTextColor(getColor(R.color.white));
                totalTimeInMillis = 30 * 60 * 1000L;
                tvFocusDesc.setText("Focus Time");
                tvFocusDesc.setTextColor(getColor(R.color.primary));
                progressTimer.setIndicatorColor(getColor(R.color.primary));
                break;
            case SHORT_BREAK:
                tabShortBreak.setBackgroundResource(R.drawable.bg_tab_selected);
                tabShortBreak.setTextColor(getColor(R.color.white));
                totalTimeInMillis = 5 * 60 * 1000L;
                tvFocusDesc.setText("Short Break");
                tvFocusDesc.setTextColor(getColor(R.color.color_success));
                progressTimer.setIndicatorColor(getColor(R.color.color_success));
                break;
            case LONG_BREAK:
                tabLongBreak.setBackgroundResource(R.drawable.bg_tab_selected);
                tabLongBreak.setTextColor(getColor(R.color.white));
                totalTimeInMillis = 15 * 60 * 1000L;
                tvFocusDesc.setText("Long Break");
                tvFocusDesc.setTextColor(getColor(R.color.color_success));
                progressTimer.setIndicatorColor(getColor(R.color.color_success));
                break;
        }

        timeLeftInMillis = totalTimeInMillis;
        updateTimerText();
        progressTimer.setMax(1000);
        progressTimer.setProgress(1000);
    }

    private void startFocusSession() {
        isFocusActive = true;
        lottieConfetti.setVisibility(View.GONE);
        btnStartFocus.setText("End Session Early");
        btnStartFocus.setBackgroundColor(getColor(R.color.warning_red));
        
        pulseAnimator.start();

        focusTimer = new CountDownTimer(timeLeftInMillis, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
                int progress = (int) ((timeLeftInMillis * 1000) / totalTimeInMillis);
                progressTimer.setProgressCompat(progress, true);
            }

            @Override
            public void onFinish() {
                handleSessionComplete();
            }
        }.start();

        if (currentMode == Mode.POMODORO && switchStrictMode.isChecked()) {
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
        progressTimer.setProgressCompat(0, true);
        pulseAnimator.cancel();
        
        UiHaptics.success(this);
        
        if (currentMode == Mode.POMODORO) {
            sessionCount++;
            tvSessionCount.setText("Session " + sessionCount);
            lottieConfetti.setVisibility(View.VISIBLE);
            lottieConfetti.playAnimation();
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
        progressTimer.setProgressCompat(1000, true);
        stopFocusSessionUI();
    }

    private void stopFocusSessionUI() {
        isFocusActive = false;
        pulseAnimator.cancel();
        viewPulse.setScaleX(1f);
        viewPulse.setScaleY(1f);
        viewPulse.setAlpha(0.1f);
        
        Intent serviceIntent = new Intent(this, FocusBlockerService.class);
        stopService(serviceIntent);

        btnStartFocus.setText("Start Session");
        btnStartFocus.setBackgroundColor(getColor(R.color.primary));
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        tvFocusTimer.setText(String.format("%02d:%02d", minutes, seconds));
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
    }
}
