package com.mindtrace.ai.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.databinding.ActivityAppDetailBinding;

/**
 * App Detail Activity — shows per-app usage stats and offers Focus Mode.
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class AppDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    public static final String EXTRA_APP_NAME = "extra_app_name";
    public static final String EXTRA_USAGE_TIME = "extra_usage_time";
    public static final String EXTRA_SESSIONS = "extra_sessions";
    public static final String EXTRA_CATEGORY = "extra_category";
    public static final String EXTRA_FIRST_OPEN = "extra_first_open";
    public static final String EXTRA_LAST_USED = "extra_last_used";

    private ActivityAppDetailBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAppDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        Intent intent = getIntent();
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        String appName = intent.getStringExtra(EXTRA_APP_NAME);
        long usageTime = intent.getLongExtra(EXTRA_USAGE_TIME, 0);
        int sessions = intent.getIntExtra(EXTRA_SESSIONS, 0);
        String category = intent.getStringExtra(EXTRA_CATEGORY);
        long firstOpen = intent.getLongExtra(EXTRA_FIRST_OPEN, 0);
        long lastUsed = intent.getLongExtra(EXTRA_LAST_USED, 0);

        binding.tvAppName.setText(appName != null ? appName : "Unknown App");
        binding.tvAppCategory.setText(category != null ? category : "Other");
        binding.tvTimeSpent.setText(UiFormatting.formatDuration(usageTime));
        binding.tvSessions.setText(String.valueOf(sessions));

        binding.tvFirstOpened.setText(firstOpen > 0 ? UiFormatting.formatTimeLabel(firstOpen) : "--");
        binding.tvLastUsed.setText(lastUsed > 0 ? UiFormatting.formatTimeLabel(lastUsed) : "--");

        if (packageName != null) {
            try {
                Drawable icon = getPackageManager().getApplicationIcon(packageName);
                binding.ivAppIcon.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException e) {
                binding.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        } else {
            binding.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        binding.btnFocusMode.setOnClickListener(v -> {
            Intent focusIntent = new Intent(this, FocusSessionActivity.class);
            startActivity(focusIntent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
