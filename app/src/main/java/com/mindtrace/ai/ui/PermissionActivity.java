package com.mindtrace.ai.ui;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mindtrace.ai.R;

/**
 * Unified Permission Activity — handles all runtime permissions with
 * step-by-step rationale screens and graceful degradation.
 *
 * <h3>Permission Flow (4 steps):</h3>
 * <ol>
 *   <li>PACKAGE_USAGE_STATS — required for screen time tracking</li>
 *   <li>POST_NOTIFICATIONS (API 33+) — required for reminders and crisis alerts</li>
 *   <li>ACCESSIBILITY_SERVICE — required for scroll intensity telemetry (D13)</li>
 *   <li>NOTIFICATION_LISTENER — required for notification response latency (D14)</li>
 * </ol>
 */
public class PermissionActivity extends AppCompatActivity {

    private static final int STEP_USAGE = 0;
    private static final int STEP_NOTIFICATIONS = 1;
    private static final int STEP_ACCESSIBILITY = 2;
    private static final int STEP_NOTIF_ACCESS = 3;
    private static final int STEP_DONE = 4;

    private int currentStep = STEP_USAGE;
    private TextView tvTitle;
    private TextView tvDescription;
    private View btnGrant;
    private View btnSkip;

    private final ActivityResultLauncher<String> notificationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Regardless of result, proceed to next step
                advanceToNextStep();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        tvTitle = findViewById(R.id.tv_permission_title);
        tvDescription = findViewById(R.id.tv_permission_description);
        btnGrant = findViewById(R.id.btn_grant);
        btnSkip = findViewById(R.id.btn_skip);

        // Determine starting step — skip already-granted permissions
        if (hasUsageStatsPermission()) {
            currentStep = STEP_NOTIFICATIONS;
        }
        if (currentStep == STEP_NOTIFICATIONS && hasNotificationPermission()) {
            currentStep = STEP_ACCESSIBILITY;
        }
        if (currentStep == STEP_ACCESSIBILITY && hasAccessibilityPermission()) {
            currentStep = STEP_NOTIF_ACCESS;
        }
        if (currentStep == STEP_NOTIF_ACCESS && hasNotificationListenerPermission()) {
            currentStep = STEP_DONE;
        }

        btnGrant.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            handleGrantClicked();
        });

        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> advanceToNextStep());
        }

        updateUI();

        // ── Premium: stagger benefit list entry ──
        animateBenefitList();

        // ── CTA pulsing glow ──
        UiMotion.pulseGlow(btnGrant);
    }

    /**
     * Stagger-animate the benefit list items one by one.
     */
    private void animateBenefitList() {
        LinearLayout benefitCard = findViewById(R.id.benefitCard);
        if (benefitCard != null) {
            UiMotion.staggerChildren(benefitCard, 100, true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if permission was granted while we were in system settings
        if (currentStep == STEP_USAGE && hasUsageStatsPermission()) {
            advanceToNextStep();
            return;
        }
        if (currentStep == STEP_ACCESSIBILITY && hasAccessibilityPermission()) {
            advanceToNextStep();
            return;
        }
        if (currentStep == STEP_NOTIF_ACCESS && hasNotificationListenerPermission()) {
            advanceToNextStep();
            return;
        }
        if (currentStep >= STEP_DONE) {
            proceedToMain();
        }
    }

    private void handleGrantClicked() {
        switch (currentStep) {
            case STEP_USAGE:
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                break;

            case STEP_NOTIFICATIONS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    advanceToNextStep();
                }
                break;

            case STEP_ACCESSIBILITY:
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                break;

            case STEP_NOTIF_ACCESS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } else {
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                }
                break;

            case STEP_DONE:
                proceedToMain();
                break;
        }
    }

    private void advanceToNextStep() {
        currentStep++;

        // Skip already-granted steps
        if (currentStep == STEP_NOTIFICATIONS && hasNotificationPermission()) {
            currentStep++;
        }
        if (currentStep == STEP_ACCESSIBILITY && hasAccessibilityPermission()) {
            currentStep++;
        }
        if (currentStep == STEP_NOTIF_ACCESS && hasNotificationListenerPermission()) {
            currentStep++;
        }

        if (currentStep >= STEP_DONE) {
            proceedToMain();
        } else {
            updateUI();
        }
    }

    private void updateUI() {
        switch (currentStep) {
            case STEP_USAGE:
                crossfadeText(tvTitle, "📊 Usage Access");
                crossfadeText(tvDescription,
                        "MindTrace needs usage access to track your screen time patterns " +
                                "and provide personalized wellness insights.\n\n" +
                                "Find MindTrace AI in the list and enable access.");
                if (btnSkip != null) UiMotion.fadeVisibility(btnSkip, false);
                break;

            case STEP_NOTIFICATIONS:
                crossfadeText(tvTitle, "🔔 Notifications");
                crossfadeText(tvDescription,
                        "Enable notifications for daily check-in reminders, " +
                                "weekly wellness reports, streak alerts, and " +
                                "critical safety notifications.\n\n" +
                                "Crisis alerts can bypass Do Not Disturb for your safety.");
                if (btnSkip != null) UiMotion.fadeVisibility(btnSkip, true);
                break;

            case STEP_ACCESSIBILITY:
                crossfadeText(tvTitle, "♿ Accessibility Service");
                crossfadeText(tvDescription,
                        "MindTrace uses an Accessibility Service to measure scroll " +
                                "behaviour — detecting mindless scrolling patterns that " +
                                "indicate compulsive use.\n\n" +
                                "Find MindTrace AI in the list and enable it.\n\n" +
                                "This data never leaves your device.");
                if (btnSkip != null) UiMotion.fadeVisibility(btnSkip, true);
                break;

            case STEP_NOTIF_ACCESS:
                crossfadeText(tvTitle, "📬 Notification Access");
                crossfadeText(tvDescription,
                        "MindTrace measures how quickly you respond to notifications " +
                                "to detect notification dependency patterns.\n\n" +
                                "Find MindTrace AI in the list and enable it.\n\n" +
                                "Only response timing is recorded — message content is never read.");
                if (btnSkip != null) UiMotion.fadeVisibility(btnSkip, true);
                break;
        }
    }

    /**
     * Crossfade text change — fades out, swaps text, fades in.
     */
    private void crossfadeText(TextView tv, String newText) {
        if (tv == null) return;
        if (tv.getText().toString().equals(newText)) {
            tv.setText(newText);
            return;
        }
        tv.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            tv.setText(newText);
            tv.animate().alpha(1f).setDuration(200).start();
        }).start();
    }

    private void proceedToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        String startDestination = getIntent().getStringExtra(MainActivity.EXTRA_START_DESTINATION);
        if (startDestination != null && !startDestination.trim().isEmpty()) {
            intent.putExtra(MainActivity.EXTRA_START_DESTINATION, startDestination);
        }
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Pre-Android 13 doesn't need runtime permission
    }

    /**
     * Check if MindTrace's AccessibilityService is enabled.
     * Required for scroll intensity telemetry (D13).
     */
    private boolean hasAccessibilityPermission() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> services =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            String myPackage = getPackageName();
            for (AccessibilityServiceInfo info : services) {
                ComponentName cn = new ComponentName(info.getResolveInfo().serviceInfo.packageName,
                        info.getResolveInfo().serviceInfo.name);
                if (cn.getPackageName().equals(myPackage)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Check if MindTrace's NotificationListenerService is enabled.
     * Required for notification response latency telemetry (D14).
     */
    private boolean hasNotificationListenerPermission() {
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        return flat.contains(getPackageName());
    }
}
