package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.R;

public class FastChallengeActivity extends AppCompatActivity {

    private TextView timerText;
    private CountDownTimer countDownTimer;
    private long timeLeftInMillis = 30000; // 30 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fast_challenge);

        timerText = findViewById(R.id.challenge_timer_text);
        Button btnQuit = findViewById(R.id.btn_quit_challenge);

        btnQuit.setOnClickListener(v -> {
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
        timerText.setText("Fast Challenge will fail in " + seconds + " seconds.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @Override
    public void onBackPressed() {
        // Disable back button during challenge
        Toast.makeText(this, "Complete or quit the challenge to continue.", Toast.LENGTH_SHORT).show();
    }
}
