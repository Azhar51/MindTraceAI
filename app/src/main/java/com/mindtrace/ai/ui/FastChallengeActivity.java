package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.databinding.ActivityFastChallengeBinding;

/**
 * Fast Challenge Activity — 30-second timer challenge before unlocking apps.
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class FastChallengeActivity extends AppCompatActivity {

    private ActivityFastChallengeBinding binding;
    private CountDownTimer countDownTimer;
    private long timeLeftInMillis = 30000; // 30 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFastChallengeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnQuitChallenge.setOnClickListener(v -> {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            Toast.makeText(this, "Challenge Quit. App Unlocked.", Toast.LENGTH_SHORT).show();
            finish();
        });

        startTimer();
    }

    private void startTimer() {
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
            }

            @Override
            public void onFinish() {
                Toast.makeText(FastChallengeActivity.this, "Challenge Failed! Returning Home.", Toast.LENGTH_LONG).show();

                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(homeIntent);

                finish();
            }
        }.start();
    }

    private void updateTimerText() {
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        binding.challengeTimerText.setText("Fast Challenge will fail in " + seconds + " seconds.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        binding = null;
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Complete or quit the challenge to continue.", Toast.LENGTH_SHORT).show();
    }
}
