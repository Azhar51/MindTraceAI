package com.mindtrace.ai.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.mindtrace.ai.R;

public class AppDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    public static final String EXTRA_APP_NAME = "extra_app_name";
    public static final String EXTRA_USAGE_TIME = "extra_usage_time";
    public static final String EXTRA_SESSIONS = "extra_sessions";
    public static final String EXTRA_CATEGORY = "extra_category";
    public static final String EXTRA_FIRST_OPEN = "extra_first_open";
    public static final String EXTRA_LAST_USED = "extra_last_used";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_detail);

        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageView ivAppIcon = findViewById(R.id.iv_app_icon);
        TextView tvAppName = findViewById(R.id.tv_app_name);
        TextView tvAppCategory = findViewById(R.id.tv_app_category);
        TextView tvTimeSpent = findViewById(R.id.tv_time_spent);
        TextView tvSessions = findViewById(R.id.tv_sessions);
        TextView tvFirstOpened = findViewById(R.id.tv_first_opened);
        TextView tvLastUsed = findViewById(R.id.tv_last_used);
        MaterialButton btnFocusMode = findViewById(R.id.btn_focus_mode);

        btnBack.setOnClickListener(v -> finish());

        Intent intent = getIntent();
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        String appName = intent.getStringExtra(EXTRA_APP_NAME);
        long usageTime = intent.getLongExtra(EXTRA_USAGE_TIME, 0);
        int sessions = intent.getIntExtra(EXTRA_SESSIONS, 0);
        String category = intent.getStringExtra(EXTRA_CATEGORY);
        long firstOpen = intent.getLongExtra(EXTRA_FIRST_OPEN, 0);
        long lastUsed = intent.getLongExtra(EXTRA_LAST_USED, 0);

        tvAppName.setText(appName != null ? appName : "Unknown App");
        tvAppCategory.setText(category != null ? category : "Other");
        tvTimeSpent.setText(UiFormatting.formatDuration(usageTime));
        tvSessions.setText(String.valueOf(sessions));
        
        tvFirstOpened.setText(firstOpen > 0 ? UiFormatting.formatTimeLabel(firstOpen) : "--");
        tvLastUsed.setText(lastUsed > 0 ? UiFormatting.formatTimeLabel(lastUsed) : "--");

        if (packageName != null) {
            try {
                Drawable icon = getPackageManager().getApplicationIcon(packageName);
                ivAppIcon.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException e) {
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        } else {
            ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        btnFocusMode.setOnClickListener(v -> {
            Intent focusIntent = new Intent(this, FocusSessionActivity.class);
            startActivity(focusIntent);
        });
    }
}
