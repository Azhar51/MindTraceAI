package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.databinding.ActivityOverUsageAlertBinding;

/**
 * Over-Usage Alert Activity — shown when screen time exceeds threshold.
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class OverUsageAlertActivity extends AppCompatActivity {

    private ActivityOverUsageAlertBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOverUsageAlertBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btn5Min.setOnClickListener(v -> grantTime(5));
        binding.btn15Min.setOnClickListener(v -> grantTime(15));
        binding.btn30Min.setOnClickListener(v -> grantTime(30));
        binding.btn1Hour.setOnClickListener(v -> grantTime(60));

        binding.btnDismiss.setOnClickListener(v -> {
            Toast.makeText(this, "Reminders disabled for today.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void grantTime(int minutes) {
        // Logic to add 'minutes' to the allowed time
        // This would interact with SharedPreferences or the UsageEngine
        Toast.makeText(this, "Granted " + minutes + " more minutes.", Toast.LENGTH_SHORT).show();

        // Return to home screen or resume the previous app
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);

        finish();
    }

    @Override
    public void onBackPressed() {
        // Force the user to choose an option, do not let them just press back to dismiss
        Toast.makeText(this, "Please select an option.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
