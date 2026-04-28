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
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mindtrace.ai.R;
import com.mindtrace.ai.databinding.ActivityPermissionBinding;

/**
 * Unified Permission Activity — handles all runtime permissions with
 * step-by-step rationale screens and graceful degradation.
 *
 * <h3>Progressive Permission Flow:</h3>
 * <ul>
 *   <li><b>Default</b>: 2 required steps (UsageStats, Notifications) → Main</li>
 *   <li><b>Enhancement mode</b>: Shows optional steps (Accessibility, NotificationListener)
 *       when launched with {@link #EXTRA_SHOW_OPTIONAL} = true</li>
 * </ul>
 *
 * <p>This progressive approach lets users reach the dashboard in ~2 minutes
 * instead of ~10+ minutes, dramatically reducing onboarding abandonment.</p>
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class PermissionActivity extends AppCompatActivity {

    /** Extra: set to true to show optional enhancement permissions (Accessibility, NotifListener). */
    public static final String EXTRA_SHOW_OPTIONAL = "show_optional_permissions";

    private static final int STEP_USAGE = 0;
    private static final int STEP_NOTIFICATIONS = 1;
    private static final int STEP_ACCESSIBILITY = 2;
    private static final int STEP_NOTIF_ACCESS = 3;
    private static final int STEP_DONE = 4;

    /** In enhancement mode, we start from optional steps. Otherwise, stop after required steps. */
    private boolean showOptionalSteps = false;

    private int currentStep = STEP_USAGE;
    private ActivityPermissionBinding binding;

    private final ActivityResultLauncher<String> notificationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Regardless of result, proceed to next step
                advanceToNextStep();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPermissionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Check if we should show optional enhancement permissions
        showOptionalSteps = getIntent().getBooleanExtra(EXTRA_SHOW_OPTIONAL, false);

        // Determine starting step — skip already-granted permissions
        if (showOptionalSteps) {
            // Enhancement mode: jump straight to optional steps
            currentStep = STEP_ACCESSIBILITY;
            if (hasAccessibilityPermission()) {
                currentStep = STEP_NOTIF_ACCESS;
            }
            if (currentStep == STEP_NOTIF_ACCESS && hasNotificationListenerPermission()) {
                currentStep = STEP_DONE;
            }
        } else {
            // Default mode: only required permissions
            if (hasUsageStatsPermission()) {
                currentStep = STEP_NOTIFICATIONS;
            }
            if (currentStep == STEP_NOTIFICATIONS && hasNotificationPermission()) {
                // All required permissions granted — done
                currentStep = STEP_DONE;
            }
        }

        binding.btnGrant.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            handleGrantClicked();
        });

        if (binding.btnSkip != null) {
            binding.btnSkip.setOnClickListener(v -> advanceToNextStep());
        }

        updateUI();

        // Premium: stagger benefit list entry
        animateBenefitList();

        // CTA pulsing glow
        UiMotion.pulseGlow(binding.btnGrant);
    }

    /**
     * Stagger-animate the benefit list items one by one.
     */
    private void animateBenefitList() {
        if (binding.benefitCard != null) {
            UiMotion.staggerChildren(binding.benefitCard, 100, true);
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

        // In default mode, stop after required permissions (Usage + Notifications)
        if (!showOptionalSteps && currentStep >= STEP_ACCESSIBILITY) {
            currentStep = STEP_DONE;
        }

        // Skip already-granted steps
        if (currentStep == STEP_NOTIFICATIONS && hasNotificationPermission()) {
            currentStep++;
        }
        // Only check optional permissions in enhancement mode
        if (showOptionalSteps) {
            if (currentStep == STEP_ACCESSIBILITY && hasAccessibilityPermission()) {
                currentStep++;
            }
            if (currentStep == STEP_NOTIF_ACCESS && hasNotificationListenerPermission()) {
                currentStep++;
            }
        }

        // In default mode, cap at DONE after required steps
        if (!showOptionalSteps && currentStep >= STEP_ACCESSIBILITY) {
            currentStep = STEP_DONE;
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
                crossfadeText(binding.tvPermissionTitle, "📊 Usage Access");
                crossfadeText(binding.tvPermissionDescription,
                        "MindTrace needs usage access to track your screen time patterns " +
                                "and provide personalized wellness insights.\n\n" +
                                "Find MindTrace AI in the list and enable access.");
                if (binding.btnSkip != null) UiMotion.fadeVisibility(binding.btnSkip, false);
                break;

            case STEP_NOTIFICATIONS:
                crossfadeText(binding.tvPermissionTitle, "🔔 Notifications");
                crossfadeText(binding.tvPermissionDescription,
                        "Enable notifications for daily check-in reminders, " +
                                "weekly wellness reports, streak alerts, and " +
                                "critical safety notifications.\n\n" +
                                "Crisis alerts can bypass Do Not Disturb for your safety.");
                if (binding.btnSkip != null) UiMotion.fadeVisibility(binding.btnSkip, true);
                break;

            case STEP_ACCESSIBILITY:
                crossfadeText(binding.tvPermissionTitle, "♿ Accessibility Service");
                crossfadeText(binding.tvPermissionDescription,
                        "MindTrace uses an Accessibility Service to measure scroll " +
                                "behaviour — detecting mindless scrolling patterns that " +
                                "indicate compulsive use.\n\n" +
                                "Find MindTrace AI in the list and enable it.\n\n" +
                                "This data never leaves your device.");
                if (binding.btnSkip != null) UiMotion.fadeVisibility(binding.btnSkip, true);
                break;

            case STEP_NOTIF_ACCESS:
                crossfadeText(binding.tvPermissionTitle, "📬 Notification Access");
                crossfadeText(binding.tvPermissionDescription,
                        "MindTrace measures how quickly you respond to notifications " +
                                "to detect notification dependency patterns.\n\n" +
                                "Find MindTrace AI in the list and enable it.\n\n" +
                                "Only response timing is recorded — message content is never read.");
                if (binding.btnSkip != null) UiMotion.fadeVisibility(binding.btnSkip, true);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
