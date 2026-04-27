package com.mindtrace.ai.repository;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.SharedPreferences;

public class SettingsRepository {
    private static final String PREFS_NAME = "mindtrace_settings";
    private static final String KEY_INCLUDE_SYSTEM_APPS = "include_system_apps";
    private static final String KEY_TRACKING_ENABLED = "tracking_enabled";
    private static final String KEY_BACKGROUND_SNAPSHOTS = "background_snapshots";
    private static final String KEY_PRIVACY_MODE = "privacy_mode";
    private static final String KEY_NOTIFICATION_MODE = "notification_mode";

    public static final String NOTIFICATIONS_GENTLE = "gentle";
    public static final String NOTIFICATIONS_BALANCED = "balanced";
    public static final String NOTIFICATIONS_MINIMAL = "minimal";

    private final Context appContext;
    private final SharedPreferences preferences;

    public SettingsRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public SettingsState getSettingsState() {
        return new SettingsState(
                preferences.getBoolean(KEY_INCLUDE_SYSTEM_APPS, true),
                preferences.getBoolean(KEY_TRACKING_ENABLED, true),
                preferences.getBoolean(KEY_BACKGROUND_SNAPSHOTS, true),
                preferences.getBoolean(KEY_PRIVACY_MODE, false),
                preferences.getString(KEY_NOTIFICATION_MODE, NOTIFICATIONS_BALANCED),
                hasUsageStatsPermission()
        );
    }

    public void setIncludeSystemApps(boolean enabled) {
        preferences.edit().putBoolean(KEY_INCLUDE_SYSTEM_APPS, enabled).apply();
    }

    public void setTrackingEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_TRACKING_ENABLED, enabled).apply();
    }

    public void setBackgroundSnapshots(boolean enabled) {
        preferences.edit().putBoolean(KEY_BACKGROUND_SNAPSHOTS, enabled).apply();
    }

    public void setPrivacyMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_PRIVACY_MODE, enabled).apply();
    }

    public void setNotificationMode(String mode) {
        preferences.edit().putString(KEY_NOTIFICATION_MODE, mode).apply();
    }

    public boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) appContext.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }

        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                appContext.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static class SettingsState {
        public final boolean includeSystemApps;
        public final boolean trackingEnabled;
        public final boolean backgroundSnapshots;
        public final boolean privacyMode;
        public final String notificationMode;
        public final boolean hasUsagePermission;

        public SettingsState(
                boolean includeSystemApps,
                boolean trackingEnabled,
                boolean backgroundSnapshots,
                boolean privacyMode,
                String notificationMode,
                boolean hasUsagePermission
        ) {
            this.includeSystemApps = includeSystemApps;
            this.trackingEnabled = trackingEnabled;
            this.backgroundSnapshots = backgroundSnapshots;
            this.privacyMode = privacyMode;
            this.notificationMode = notificationMode;
            this.hasUsagePermission = hasUsagePermission;
        }
    }
}
