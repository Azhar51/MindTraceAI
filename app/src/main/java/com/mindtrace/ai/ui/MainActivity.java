package com.mindtrace.ai.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.mindtrace.ai.R;
import com.mindtrace.ai.databinding.ActivityMainBinding;
import com.mindtrace.ai.ui.panel.InsightsFragment;
import com.mindtrace.ai.ui.panel.MoodFragment;
import com.mindtrace.ai.ui.panel.OverviewFragment;
import com.mindtrace.ai.ui.panel.SettingsFragment;
import com.mindtrace.ai.ui.panel.SupportFragment;
import com.mindtrace.ai.ui.panel.TasksFragment;
import com.mindtrace.ai.ui.panel.UsageFragment;
import com.mindtrace.ai.viewmodel.DashboardViewModel;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Main Activity — navigation shell hosting all dashboard fragments.
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_START_DESTINATION = "start_destination";
    public static final String DEST_OVERVIEW = "overview";
    public static final String DEST_USAGE = "usage";
    public static final String DEST_MOOD = "mood";
    public static final String DEST_TASKS = "tasks";
    public static final String DEST_INSIGHTS = "insights";
    public static final String DEST_SUPPORT = "support";
    public static final String DEST_SETTINGS = "settings";

    private static final String STATE_CURRENT_DESTINATION = "state_current_destination";
    private static final String STATE_LAST_PRIMARY_DESTINATION = "state_last_primary_destination";

    /** Prefs for tracking optional permission prompts. */
    private static final String PREFS_OPTIONAL_PERMS = "optional_permission_prompts";
    private static final String KEY_LAST_PROMPT_DAY = "last_optional_prompt_day";
    private static final String KEY_FIRST_LAUNCH_DAY = "first_launch_day";

    private ActivityMainBinding binding;
    private DashboardViewModel dashboardViewModel;

    private String currentDestination = DEST_OVERVIEW;
    private String lastPrimaryDestination = DEST_OVERVIEW;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        setSupportActionBar(binding.topAppBar);
        binding.topAppBar.setOnMenuItemClickListener(this::handleToolbarAction);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            navigateTo(resolvePrimaryDestination(item.getItemId()), true);
            return true;
        });

        UiMotion.attachPressAnimation(binding.fabCheckIn);
        binding.fabCheckIn.setOnClickListener(v -> startActivity(new Intent(this, DailyCheckInActivity.class)));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSecondaryDestination(currentDestination)) {
                    navigateTo(lastPrimaryDestination, true);
                    return;
                }

                if (!DEST_OVERVIEW.equals(currentDestination)) {
                    navigateTo(DEST_OVERVIEW, true);
                    return;
                }

                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        String startDestination = savedInstanceState != null
                ? savedInstanceState.getString(STATE_CURRENT_DESTINATION, DEST_OVERVIEW)
                : getIntent().getStringExtra(EXTRA_START_DESTINATION);
        lastPrimaryDestination = savedInstanceState != null
                ? savedInstanceState.getString(STATE_LAST_PRIMARY_DESTINATION, DEST_OVERVIEW)
                : DEST_OVERVIEW;

        if (startDestination == null || startDestination.trim().isEmpty()) {
            startDestination = DEST_OVERVIEW;
        }

        navigateTo(startDestination, !isSecondaryDestination(startDestination));

        // Record first launch day for optional permission timing
        SharedPreferences prefs = getSharedPreferences(PREFS_OPTIONAL_PERMS, MODE_PRIVATE);
        if (prefs.getLong(KEY_FIRST_LAUNCH_DAY, 0) == 0) {
            prefs.edit().putLong(KEY_FIRST_LAUNCH_DAY, todayDayNumber()).apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        dashboardViewModel.refreshDashboard();
        checkOptionalPermissions();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            String destination = intent.getStringExtra(EXTRA_START_DESTINATION);
            if (destination != null && !destination.trim().isEmpty()) {
                navigateTo(destination, !isSecondaryDestination(destination));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CURRENT_DESTINATION, currentDestination);
        outState.putString(STATE_LAST_PRIMARY_DESTINATION, lastPrimaryDestination);
    }

    public void openSupportPanel() {
        navigateTo(DEST_SUPPORT, false);
    }

    public void openSettingsPanel() {
        navigateTo(DEST_SETTINGS, false);
    }

    public void openDestination(String destination) {
        navigateTo(destination, !isSecondaryDestination(destination));
    }

    private boolean handleToolbarAction(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_support) {
            openSupportPanel();
            return true;
        }
        if (itemId == R.id.action_settings) {
            openSettingsPanel();
            return true;
        }
        return false;
    }

    private void navigateTo(String destination, boolean primaryDestination) {
        String sanitizedDestination = sanitizeDestination(destination);
        if (primaryDestination) {
            lastPrimaryDestination = sanitizedDestination;
        }
        currentDestination = sanitizedDestination;

        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.fade_in,
                        R.anim.fade_out,
                        R.anim.fade_in,
                        R.anim.fade_out
                )
                .replace(R.id.fragment_container, createFragment(sanitizedDestination), sanitizedDestination)
                .commit();

        updateToolbar(sanitizedDestination);
        syncBottomNavigationSelection(sanitizedDestination);

        // Hide the activity-level check-in FAB and App Bar on Overview (it has its own FAB and AppBar)
        binding.fabCheckIn.setVisibility(DEST_OVERVIEW.equals(sanitizedDestination)
                ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.topAppBar.setVisibility(DEST_OVERVIEW.equals(sanitizedDestination)
                ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private Fragment createFragment(String destination) {
        switch (destination) {
            case DEST_USAGE:
                return new UsageFragment();
            case DEST_MOOD:
                return new MoodFragment();
            case DEST_TASKS:
                return new TasksFragment();
            case DEST_INSIGHTS:
                return new InsightsFragment();
            case DEST_SUPPORT:
                return new SupportFragment();
            case DEST_SETTINGS:
                return new SettingsFragment();
            case DEST_OVERVIEW:
            default:
                return new OverviewFragment();
        }
    }

    private void updateToolbar(String destination) {
        binding.topAppBar.setTitle(getDestinationTitle(destination));
        binding.topAppBar.setSubtitle(getDestinationSubtitle(destination));

        if (isSecondaryDestination(destination)) {
            binding.topAppBar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            binding.topAppBar.setNavigationOnClickListener(v -> navigateTo(lastPrimaryDestination, true));
        } else {
            binding.topAppBar.setNavigationIcon(null);
            binding.topAppBar.setNavigationOnClickListener(null);
        }
    }

    private void syncBottomNavigationSelection(String destination) {
        int selectedItemId = getPrimaryMenuItemId(isSecondaryDestination(destination)
                ? lastPrimaryDestination
                : destination);
        if (selectedItemId == 0) {
            return;
        }

        MenuItem menuItem = binding.bottomNavigation.getMenu().findItem(selectedItemId);
        if (menuItem != null) {
            menuItem.setChecked(true);
        }
    }

    private String resolvePrimaryDestination(int itemId) {
        if (itemId == R.id.nav_usage) {
            return DEST_USAGE;
        }
        if (itemId == R.id.nav_mood) {
            return DEST_MOOD;
        }
        if (itemId == R.id.nav_tasks) {
            return DEST_TASKS;
        }
        if (itemId == R.id.nav_insights) {
            return DEST_INSIGHTS;
        }
        return DEST_OVERVIEW;
    }

    private int getPrimaryMenuItemId(String destination) {
        switch (destination) {
            case DEST_USAGE:
                return R.id.nav_usage;
            case DEST_MOOD:
                return R.id.nav_mood;
            case DEST_TASKS:
                return R.id.nav_tasks;
            case DEST_INSIGHTS:
                return R.id.nav_insights;
            case DEST_OVERVIEW:
            default:
                return R.id.nav_overview;
        }
    }

    private String sanitizeDestination(String destination) {
        switch (destination) {
            case DEST_USAGE:
            case DEST_MOOD:
            case DEST_TASKS:
            case DEST_INSIGHTS:
            case DEST_SUPPORT:
            case DEST_SETTINGS:
                return destination;
            case DEST_OVERVIEW:
            default:
                return DEST_OVERVIEW;
        }
    }

    private boolean isSecondaryDestination(String destination) {
        return DEST_SUPPORT.equals(destination) || DEST_SETTINGS.equals(destination);
    }

    private String getDestinationTitle(String destination) {
        switch (destination) {
            case DEST_USAGE:
                return "Usage";
            case DEST_MOOD:
                return "Mood";
            case DEST_TASKS:
                return "Tasks";
            case DEST_INSIGHTS:
                return "Insights";
            case DEST_SUPPORT:
                return "Support";
            case DEST_SETTINGS:
                return "Settings";
            case DEST_OVERVIEW:
            default:
                return "Dashboard";
        }
    }

    private String getDestinationSubtitle(String destination) {
        switch (destination) {
            case DEST_USAGE:
                return "Daily screen-time and app activity";
            case DEST_MOOD:
                return "Check-ins and emotional trends";
            case DEST_TASKS:
                return "Interventions and steady progress";
            case DEST_INSIGHTS:
                return "Wellness analysis and next actions";
            case DEST_SUPPORT:
                return "Grounding and support resources";
            case DEST_SETTINGS:
                return "Privacy and tracking controls";
            case DEST_OVERVIEW:
            default:
                return "Your wellbeing summary";
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // PROGRESSIVE PERMISSION PROMPTS (optional enhancement permissions)
    // ═════════════════════════════════════════════════════════════════

    /**
     * Contextually prompt for optional permissions (Accessibility, NotificationListener)
     * after the user has been using the app for a few days.
     */
    public void checkOptionalPermissions() {
        boolean hasAccessibility = hasAccessibilityPermission();
        boolean hasNotifListener = hasNotificationListenerPermission();
        if (hasAccessibility && hasNotifListener) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_OPTIONAL_PERMS, MODE_PRIVATE);
        long firstLaunchDay = prefs.getLong(KEY_FIRST_LAUNCH_DAY, 0);
        long lastPromptDay = prefs.getLong(KEY_LAST_PROMPT_DAY, 0);
        long today = todayDayNumber();

        if (firstLaunchDay == 0 || today - firstLaunchDay < 3) return;
        if (lastPromptDay == today) return;

        prefs.edit().putLong(KEY_LAST_PROMPT_DAY, today).apply();

        String message = !hasAccessibility
                ? "Enable scroll tracking for deeper behavioral insights"
                : "Enable notification tracking for response pattern analysis";

        Snackbar.make(binding.fragmentContainer, message, Snackbar.LENGTH_LONG)
                .setAction("Enable", v -> {
                    Intent intent = new Intent(this, PermissionActivity.class);
                    intent.putExtra(PermissionActivity.EXTRA_SHOW_OPTIONAL, true);
                    startActivity(intent);
                })
                .setActionTextColor(getResources().getColor(R.color.primary, getTheme()))
                .show();
    }

    private boolean hasAccessibilityPermission() {
        try {
            AccessibilityManager am = (AccessibilityManager)
                    getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> services =
                    am.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            String myPackage = getPackageName();
            for (AccessibilityServiceInfo info : services) {
                ComponentName cn = new ComponentName(
                        info.getResolveInfo().serviceInfo.packageName,
                        info.getResolveInfo().serviceInfo.name);
                if (cn.getPackageName().equals(myPackage)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean hasNotificationListenerPermission() {
        String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        return flat.contains(getPackageName());
    }

    private long todayDayNumber() {
        return System.currentTimeMillis() / (24L * 60 * 60 * 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
