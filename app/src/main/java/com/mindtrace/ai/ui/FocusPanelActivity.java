package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.databinding.ActivityFocusPanelBinding;

/**
 * Focus Panel Activity — accessibility permission setup for focus features.
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class FocusPanelActivity extends AppCompatActivity {

    private ActivityFocusPanelBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFocusPanelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.cardA11yPermission.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
