package com.mindtrace.ai.ui.panel;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mindtrace.ai.BuildConfig;
import com.mindtrace.ai.R;
import com.mindtrace.ai.debug.TrajectorySimulator;
import com.mindtrace.ai.repository.DataExportRepository;
import com.mindtrace.ai.repository.SettingsRepository;
import com.mindtrace.ai.viewmodel.DashboardViewModel;
import com.mindtrace.ai.viewmodel.SettingsViewModel;

import java.io.File;

public class SettingsFragment extends Fragment {
    private SettingsViewModel settingsViewModel;
    private DashboardViewModel dashboardViewModel;
    private boolean isBindingState;

    private TextView tvPermissionState;
    private SwitchMaterial switchIncludeSystemApps;
    private SwitchMaterial switchTrackingEnabled;
    private SwitchMaterial switchBackgroundSnapshots;
    private SwitchMaterial switchPrivacyMode;
    private RadioGroup rgNotifications;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        settingsViewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);
        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        tvPermissionState = view.findViewById(R.id.tv_settings_permission_state);
        switchIncludeSystemApps = view.findViewById(R.id.switch_settings_include_system_apps);
        switchTrackingEnabled = view.findViewById(R.id.switch_settings_tracking_enabled);
        switchBackgroundSnapshots = view.findViewById(R.id.switch_settings_background_snapshots);
        switchPrivacyMode = view.findViewById(R.id.switch_settings_privacy_mode);
        rgNotifications = view.findViewById(R.id.rg_settings_notifications);
        MaterialButton btnManagePermission = view.findViewById(R.id.btn_settings_manage_permission);
        MaterialButton btnRefreshNow = view.findViewById(R.id.btn_settings_refresh_now);

        btnManagePermission.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        btnRefreshNow.setOnClickListener(v -> dashboardViewModel.refreshDashboard());

        // ── Data Export & Privacy (7.D + 6.F.5) ──
        setupDataExportButtons(view);
        setupNotificationPreferences(view);
        setupDebugTools(view);

        bindListeners();
        settingsViewModel.getSettingsState().observe(getViewLifecycleOwner(), this::renderSettingsState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (settingsViewModel != null) {
            settingsViewModel.loadSettings();
        }
        updatePrivacyDashboard();
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA EXPORT & PRIVACY
    // ═══════════════════════════════════════════════════════════════════

    private void setupDataExportButtons(View view) {
        // Export All Data
        View btnExportData = view.findViewById(R.id.btn_export_data);
        if (btnExportData != null) {
            btnExportData.setOnClickListener(v -> exportAllData());
        }

        // Export Clinician Report
        View btnClinicianExport = view.findViewById(R.id.btn_clinician_export);
        if (btnClinicianExport != null) {
            btnClinicianExport.setOnClickListener(v -> exportClinicianReport());
        }

        // Delete All Data
        View btnDeleteData = view.findViewById(R.id.btn_delete_all_data);
        if (btnDeleteData != null) {
            btnDeleteData.setOnClickListener(v -> confirmDataDeletion());
        }
    }

    private void exportAllData() {
        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            DataExportRepository repo = new DataExportRepository(requireContext());
            File exported = repo.exportAllDataAsJson();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (exported != null) {
                        Toast.makeText(requireContext(),
                                "✅ Data exported to: " + exported.getName(),
                                Toast.LENGTH_LONG).show();
                        // Share via intent
                        shareFile(exported);
                    } else {
                        Toast.makeText(requireContext(),
                                "❌ Export failed. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void exportClinicianReport() {
        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            DataExportRepository repo = new DataExportRepository(requireContext());
            File exported = repo.exportClinicianReport();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (exported != null) {
                        Toast.makeText(requireContext(),
                                "📋 Clinician report exported",
                                Toast.LENGTH_LONG).show();
                        shareFile(exported);
                    } else {
                        Toast.makeText(requireContext(),
                                "❌ Export failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void shareFile(File file) {
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Export"));
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "File saved locally: " + file.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDataDeletion() {
        new AlertDialog.Builder(requireContext())
                .setTitle("⚠️ Delete All Data")
                .setMessage("This will permanently delete ALL your wellness data, " +
                        "including journals, check-ins, crisis history, and settings.\n\n" +
                        "This action cannot be undone.")
                .setPositiveButton("Delete Everything", (dialog, which) -> {
                    com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
                        DataExportRepository repo = new DataExportRepository(requireContext());
                        boolean success = repo.deleteAllData();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (success) {
                                    Toast.makeText(requireContext(),
                                            "✅ All data deleted", Toast.LENGTH_SHORT).show();
                                    // Restart app
                                    Intent restart = requireContext().getPackageManager()
                                            .getLaunchIntentForPackage(requireContext().getPackageName());
                                    if (restart != null) {
                                        restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(restart);
                                    }
                                }
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════
    // NOTIFICATION PREFERENCES (7.B.4)
    // ═══════════════════════════════════════════════════════════════════

    private void setupNotificationPreferences(View view) {
        SwitchMaterial switchDailyReminders = view.findViewById(R.id.switch_daily_reminders);
        SwitchMaterial switchStreakAlerts = view.findViewById(R.id.switch_streak_alerts);
        SwitchMaterial switchWeeklyReport = view.findViewById(R.id.switch_weekly_report);

        SharedPreferences prefs = requireContext().getSharedPreferences(
                "mindtrace_daily_settings", android.content.Context.MODE_PRIVATE);

        if (switchDailyReminders != null) {
            switchDailyReminders.setChecked(prefs.getBoolean("daily_reminders_enabled", true));
            switchDailyReminders.setOnCheckedChangeListener((btn, checked) -> {
                if (btn.isPressed()) {
                    prefs.edit().putBoolean("daily_reminders_enabled", checked).apply();
                }
            });
        }

        if (switchStreakAlerts != null) {
            switchStreakAlerts.setChecked(prefs.getBoolean("streak_alerts_enabled", true));
            switchStreakAlerts.setOnCheckedChangeListener((btn, checked) -> {
                if (btn.isPressed()) {
                    prefs.edit().putBoolean("streak_alerts_enabled", checked).apply();
                }
            });
        }

        if (switchWeeklyReport != null) {
            switchWeeklyReport.setChecked(prefs.getBoolean("weekly_report_enabled", true));
            switchWeeklyReport.setOnCheckedChangeListener((btn, checked) -> {
                if (btn.isPressed()) {
                    prefs.edit().putBoolean("weekly_report_enabled", checked).apply();
                }
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVACY DASHBOARD (7.D.4)
    // ═══════════════════════════════════════════════════════════════════

    private void updatePrivacyDashboard() {
        View view = getView();
        if (view == null) return;

        TextView tvStorageInfo = view.findViewById(R.id.tv_storage_info);
        if (tvStorageInfo == null) return;

        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            DataExportRepository repo = new DataExportRepository(requireContext());
            DataExportRepository.StorageStats stats = repo.getStorageStats();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    tvStorageInfo.setText(String.format(
                            "📦 %d tasks • %d journals • %d crisis events\n💾 Database: %d KB",
                            stats.taskCount, stats.journalCount,
                            stats.crisisEventCount, stats.databaseSizeKB));
                });
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEVELOPER TOOLS (debug builds only)
    // ═══════════════════════════════════════════════════════════════════

    private void setupDebugTools(View view) {
        MaterialCardView debugCard = view.findViewById(R.id.card_debug_tools);
        if (debugCard == null) return;

        // Only show in debug builds
        if (!BuildConfig.DEBUG) {
            debugCard.setVisibility(View.GONE);
            return;
        }
        debugCard.setVisibility(View.VISIBLE);

        TrajectorySimulator sim = new TrajectorySimulator(requireContext());

        // Simulation scenario buttons
        setClickIfExists(view, R.id.btn_sim_stable,    v -> { sim.injectStableTrajectory();    refreshAfterDelay(); });
        setClickIfExists(view, R.id.btn_sim_worsening, v -> { sim.injectWorseningTrajectory(); refreshAfterDelay(); });
        setClickIfExists(view, R.id.btn_sim_improving, v -> { sim.injectImprovingTrajectory(); refreshAfterDelay(); });
        setClickIfExists(view, R.id.btn_sim_crisis,    v -> { sim.injectCrisisEscalation();    refreshAfterDelay(); });
        setClickIfExists(view, R.id.btn_sim_mixed,     v -> { sim.injectMixedRecovery();       refreshAfterDelay(); });
        setClickIfExists(view, R.id.btn_sim_gradual,   v -> { sim.injectGradualWorsening();    refreshAfterDelay(); });

        // Utility buttons
        setClickIfExists(view, R.id.btn_sim_dump,  v -> sim.dumpAllToLog());
        setClickIfExists(view, R.id.btn_sim_clear, v -> {
            sim.clearAllClassifications();
            refreshAfterDelay();
        });
    }

    /** Sets a click listener only if the view exists. */
    private void setClickIfExists(View parent, int id, View.OnClickListener listener) {
        View v = parent.findViewById(id);
        if (v != null) v.setOnClickListener(listener);
    }

    /**
     * Delays dashboard refresh by 1 second to allow DB writes to complete,
     * then triggers loadTrendReport() to update the Insights tab.
     */
    private void refreshAfterDelay() {
        View root = getView();
        if (root != null) {
            root.postDelayed(() -> {
                if (dashboardViewModel != null) {
                    dashboardViewModel.loadTrendReport(7, report -> { /* UI will auto-observe */ });
                    dashboardViewModel.refreshDashboard();
                }
            }, 1000);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXISTING LOGIC
    // ═══════════════════════════════════════════════════════════════════

    private void bindListeners() {
        switchIncludeSystemApps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                settingsViewModel.setIncludeSystemApps(isChecked);
                dashboardViewModel.refreshDashboard();
            }
        });

        switchTrackingEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                settingsViewModel.setTrackingEnabled(isChecked);
                dashboardViewModel.refreshDashboard();
            }
        });

        switchBackgroundSnapshots.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                settingsViewModel.setBackgroundSnapshots(isChecked);
            }
        });

        switchPrivacyMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                settingsViewModel.setPrivacyMode(isChecked);
            }
        });

        rgNotifications.setOnCheckedChangeListener((group, checkedId) -> {
            if (isBindingState) {
                return;
            }

            if (checkedId == R.id.rb_settings_notifications_gentle) {
                settingsViewModel.setNotificationMode(SettingsRepository.NOTIFICATIONS_GENTLE);
            } else if (checkedId == R.id.rb_settings_notifications_minimal) {
                settingsViewModel.setNotificationMode(SettingsRepository.NOTIFICATIONS_MINIMAL);
            } else {
                settingsViewModel.setNotificationMode(SettingsRepository.NOTIFICATIONS_BALANCED);
            }
        });
    }

    private void renderSettingsState(SettingsRepository.SettingsState state) {
        if (state == null) {
            return;
        }

        isBindingState = true;
        tvPermissionState.setText(state.hasUsagePermission
                ? "Usage access is granted"
                : "Usage access is not granted yet");

        switchIncludeSystemApps.setChecked(state.includeSystemApps);
        switchTrackingEnabled.setChecked(state.trackingEnabled);
        switchBackgroundSnapshots.setChecked(state.backgroundSnapshots);
        switchPrivacyMode.setChecked(state.privacyMode);

        if (SettingsRepository.NOTIFICATIONS_GENTLE.equals(state.notificationMode)) {
            rgNotifications.check(R.id.rb_settings_notifications_gentle);
        } else if (SettingsRepository.NOTIFICATIONS_MINIMAL.equals(state.notificationMode)) {
            rgNotifications.check(R.id.rb_settings_notifications_minimal);
        } else {
            rgNotifications.check(R.id.rb_settings_notifications_balanced);
        }
        isBindingState = false;
    }
}

