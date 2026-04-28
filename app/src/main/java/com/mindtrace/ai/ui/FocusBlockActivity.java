package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.databinding.ActivityFocusBlockBinding;

/**
 * Focus Block Activity — shown when user tries to open a blocked app.
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class FocusBlockActivity extends AppCompatActivity {

    private ActivityFocusBlockBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFocusBlockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String packageName = getIntent().getStringExtra("BLOCKED_PACKAGE");

        if (packageName != null) {
            binding.tvBlockedApp.setText("You are trying to open a restricted app:\n" + packageName);
        }

        binding.btnBackToWork.setOnClickListener(v -> {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // Prevent going back to the blocked app
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
