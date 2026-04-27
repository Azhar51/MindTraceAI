package com.mindtrace.ai;

import android.graphics.drawable.Drawable;

public class AppUsageModel {
    public String appName;
    public String packageName;
    public long usageTime;
    public int usagePercentage;
    public Drawable appIcon;
    public int launchCount;
    public int foregroundSessions;
    public long firstOpenedTimestamp;
    public long lastUsedTimestamp;
    public String appCategory;
    public int percentOfTotalUsage;
    public boolean systemApp;
    public boolean userVisible;

    public AppUsageModel(String appName, String packageName, long usageTime, int usagePercentage, Drawable appIcon) {
        this(appName, packageName, usageTime, usagePercentage, appIcon, 0, 0, 0L, 0L, null, 0, false, true);
    }

    public AppUsageModel(
            String appName,
            String packageName,
            long usageTime,
            int usagePercentage,
            Drawable appIcon,
            int launchCount,
            int foregroundSessions,
            long firstOpenedTimestamp,
            long lastUsedTimestamp,
            String appCategory,
            int percentOfTotalUsage,
            boolean systemApp,
            boolean userVisible
    ) {
        this.appName = appName;
        this.packageName = packageName;
        this.usageTime = usageTime;
        this.usagePercentage = usagePercentage;
        this.appIcon = appIcon;
        this.launchCount = launchCount;
        this.foregroundSessions = foregroundSessions;
        this.firstOpenedTimestamp = firstOpenedTimestamp;
        this.lastUsedTimestamp = lastUsedTimestamp;
        this.appCategory = appCategory;
        this.percentOfTotalUsage = percentOfTotalUsage;
        this.systemApp = systemApp;
        this.userVisible = userVisible;
    }
}
