package com.mindtrace.ai.usage;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.ai.AppCategoryMapper;
import com.mindtrace.ai.behavior.BehaviorThresholds;
import com.mindtrace.ai.database.entity.AppUsageSnapshot;
import com.mindtrace.ai.database.entity.BehaviorUsageSummary;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.UsageSession;
import com.mindtrace.ai.service.NotificationEventTracker;
import com.mindtrace.ai.service.ScreenEventReceiver;
import com.mindtrace.ai.service.ScrollEventTracker;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class UsageIntelligenceEngine {
    private static final long MIN_MEANINGFUL_USAGE_MILLIS = 1_000L;
    private static final long BINGE_SESSION_THRESHOLD_MS = 30L * 60L * 1000L; // 30 min
    private static final long DUPLICATE_FOREGROUND_WINDOW_MILLIS = 2_000L;
    private static final int NIGHT_START_HOUR = 22;
    private static final int NIGHT_END_HOUR = 6;
    private static final long HIGH_RISK_USAGE_MILLIS = 3L * 60L * 60L * 1000L;       // 3 hours
    private static final long CATEGORY_RISK_USAGE_MILLIS = 2L * 60L * 60L * 1000L;   // 2 hours

    public UsageIntelligenceResult analyzeDay(
            Context context,
            long dayTimestamp,
            long endTimestamp,
            boolean includeSystemApps
    ) {
        UsageIntelligenceResult result = new UsageIntelligenceResult();
        result.dayTimestamp = dayTimestamp;
        result.recordedAt = endTimestamp;

        if (context == null || endTimestamp <= dayTimestamp) {
            return result;
        }

        UsageStatsManager usageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return result;
        }

        PackageManager packageManager = context.getPackageManager();
        Set<String> excludedPackages = getExcludedPackages(context, packageManager);
        Map<String, AppAccumulator> accumulators = new HashMap<>();

        EventPass eventPass = parseEvents(
                usageStatsManager,
                context,
                packageManager,
                accumulators,
                dayTimestamp,
                endTimestamp,
                includeSystemApps,
                excludedPackages
        );

        List<AppAccumulator> meaningfulApps = new ArrayList<>();
        for (AppAccumulator accumulator : accumulators.values()) {
            accumulator.usageTimeMillis = accumulator.eventDerivedUsageMillis;
            if (!accumulator.isUserVisible) {
                continue;
            }
            if (accumulator.usageTimeMillis < MIN_MEANINGFUL_USAGE_MILLIS
                    && accumulator.launchCount <= 0
                    && accumulator.foregroundSessions <= 0) {
                continue;
            }
            meaningfulApps.add(accumulator);
        }
        meaningfulApps.sort((left, right) -> Long.compare(right.usageTimeMillis, left.usageTimeMillis));

        long totalForegroundMillis = 0L;
        for (AppAccumulator accumulator : meaningfulApps) {
            totalForegroundMillis += accumulator.usageTimeMillis;
        }

        long topUsageMillis = meaningfulApps.isEmpty() ? 0L : meaningfulApps.get(0).usageTimeMillis;
        int highRiskAppCount = 0;
        Set<String> categoriesUsed = new HashSet<>();
        long passiveTimeMillis = 0L;
        long productiveTimeMillis = 0L;
        long socialTimeMillis = 0L;
        long entertainmentTimeMillis = 0L;
        Map<String, Long> appUsageMap = new HashMap<>();

        for (AppAccumulator accumulator : meaningfulApps) {
            categoriesUsed.add(safe(accumulator.appCategory, "Other"));
            int percentOfTotal = totalForegroundMillis <= 0L
                    ? 0
                    : clamp((int) Math.round((accumulator.usageTimeMillis * 100.0) / totalForegroundMillis), 0, 100);
            int relativeToTop = topUsageMillis <= 0L
                    ? 0
                    : clamp((int) Math.round((accumulator.usageTimeMillis * 100.0) / topUsageMillis), 0, 100);

            // ── Use AppCategoryMapper for classification ──
            AppCategoryMapper.Category catEnum = AppCategoryMapper.getCategory(accumulator.packageName, context);
            boolean isPassive = catEnum.isPassive;
            boolean hasBinge = accumulator.longestSessionMillis >= BINGE_SESSION_THRESHOLD_MS;
            long avgSessionMs = accumulator.foregroundSessions > 0
                    ? accumulator.usageTimeMillis / accumulator.foregroundSessions : 0;

            AppUsageSnapshot snapshot = new AppUsageSnapshot();
            snapshot.dayTimestamp = dayTimestamp;
            snapshot.recordedAt = endTimestamp;
            snapshot.packageName = accumulator.packageName;
            snapshot.appName = accumulator.appName;
            snapshot.usageTimeMillis = accumulator.usageTimeMillis;
            snapshot.usagePercentage = relativeToTop;
            snapshot.percentOfTotalUsage = percentOfTotal;
            snapshot.launchCount = accumulator.launchCount;
            snapshot.foregroundSessions = accumulator.foregroundSessions;
            snapshot.firstOpenedTimestamp = accumulator.firstOpenedAt;
            snapshot.lastUsedTimestamp = accumulator.lastUsedAt;
            snapshot.appCategory = catEnum.name();
            snapshot.isSystemApp = accumulator.isSystemApp;
            snapshot.isUserVisible = accumulator.isUserVisible;
            // ── New fields ──
            snapshot.isPassiveApp = isPassive;
            snapshot.bingeFlag = hasBinge;
            snapshot.averageSessionLengthMs = avgSessionMs;
            snapshot.bingeSessionCount = accumulator.bingeSessionCount;
            snapshot.longestSessionMs = accumulator.longestSessionMillis;
            snapshot.nightUsagePercent = accumulator.usageTimeMillis > 0
                    ? (int) ((accumulator.nightUsageMillis * 100) / accumulator.usageTimeMillis) : 0;
            result.appSnapshots.add(snapshot);

            result.appUsageModels.add(new AppUsageModel(
                    accumulator.appName,
                    accumulator.packageName,
                    accumulator.usageTimeMillis,
                    relativeToTop,
                    accumulator.appIcon,
                    accumulator.launchCount,
                    accumulator.foregroundSessions,
                    accumulator.firstOpenedAt,
                    accumulator.lastUsedAt,
                    accumulator.appCategory,
                    percentOfTotal,
                    accumulator.isSystemApp,
                    accumulator.isUserVisible
            ));

            if (isHighRiskApp(accumulator)) {
                highRiskAppCount++;
            }

            // ── Accumulate category time ──
            if (isPassive) {
                passiveTimeMillis += accumulator.usageTimeMillis;
            } else if (catEnum != AppCategoryMapper.Category.UTILITY && catEnum != AppCategoryMapper.Category.OTHER) {
                productiveTimeMillis += accumulator.usageTimeMillis;
            }
            if (catEnum == AppCategoryMapper.Category.SOCIAL) {
                socialTimeMillis += accumulator.usageTimeMillis;
            }
            if (catEnum == AppCategoryMapper.Category.VIDEO || catEnum == AppCategoryMapper.Category.ENTERTAINMENT) {
                entertainmentTimeMillis += accumulator.usageTimeMillis;
            }
            appUsageMap.put(accumulator.packageName, accumulator.usageTimeMillis);
        }

        if (!result.appUsageModels.isEmpty()) {
            result.mostUsedApp = result.appUsageModels.get(0);
        }

        // ── Compute category breakdown JSON ──
        Map<String, Long> categoryBreakdown = AppCategoryMapper.buildCategoryBreakdown(appUsageMap);
        String categoryBreakdownJson = new JSONObject(categoryBreakdown).toString();

        // ── Compute hourly breakdown JSON ──
        String hourlyBreakdownJson = buildHourlyBreakdown(eventPass.sessions);

        // ── Compute passive ratio ──
        float passiveRatio = totalForegroundMillis > 0
                ? (float) passiveTimeMillis / totalForegroundMillis : 0f;

        // ── Sync screen event data ──
        ScreenEventReceiver.ScreenEventData screenData =
                ScreenEventReceiver.getTodayData(context);

        // ── Sync scroll intensity (prefer direct measurement, fallback to heuristic) ──
        float scrollIntensity = syncScrollIntensity(
                context, eventPass, passiveTimeMillis, totalForegroundMillis);

        // ── Sync notification response latency ──
        long notificationResponseAvgMs = syncNotificationResponseAvg(context);

        DailyUsage dailyUsage = new DailyUsage();
        dailyUsage.date = dayTimestamp;
        dailyUsage.screenTimeMillis = totalForegroundMillis;
        dailyUsage.activeForegroundTimeMillis = totalForegroundMillis;
        dailyUsage.unlockCount = screenData.isValid() ? screenData.unlockCount : eventPass.totalLaunchCount;
        dailyUsage.totalLaunchCount = eventPass.totalLaunchCount;
        dailyUsage.appSwitches = eventPass.totalAppSwitchCount;
        dailyUsage.totalAppSwitchCount = eventPass.totalAppSwitchCount;
        dailyUsage.nightUsageMillis = eventPass.lateNightUsageMillis;
        dailyUsage.mostUsedApp = result.mostUsedApp == null ? "None" : result.mostUsedApp.appName;
        dailyUsage.topAppPackageName = result.mostUsedApp == null ? null : result.mostUsedApp.packageName;
        dailyUsage.appsTrackedCount = result.appSnapshots.size();
        dailyUsage.highRiskAppCount = highRiskAppCount;
        dailyUsage.snapshotCreatedAt = endTimestamp;
        // ── Extended screen event fields (reuse screenData from above) ──
        dailyUsage.unlockCount = screenData.isValid() ? screenData.unlockCount : eventPass.totalLaunchCount;
        dailyUsage.firstUnlockTime = screenData.firstUnlockTime;
        dailyUsage.lastScreenOffTime = screenData.lastScreenOffTime;
        if (dailyUsage.unlockCount == 0) {
            dailyUsage.unlockCount = dailyUsage.totalLaunchCount;
        }
        dailyUsage.passiveConsumptionRatio = passiveRatio;
        dailyUsage.productiveTimeMillis = productiveTimeMillis;
        dailyUsage.socialMediaTimeMillis = socialTimeMillis;
        dailyUsage.entertainmentTimeMillis = entertainmentTimeMillis;
        dailyUsage.scrollIntensityScore = scrollIntensity;
        dailyUsage.notificationResponseAvgMs = notificationResponseAvgMs;
        dailyUsage.categoryBreakdownJson = categoryBreakdownJson;
        dailyUsage.hourlyBreakdownJson = hourlyBreakdownJson;
        result.dailyUsage = dailyUsage;

        UsageBehaviorSignal signal = buildBehaviorSignal(
                dayTimestamp,
                totalForegroundMillis,
                passiveTimeMillis,
                totalForegroundMillis - passiveTimeMillis,
                eventPass,
                meaningfulApps,
                highRiskAppCount,
                categoriesUsed.size()
        );
        result.sessions.addAll(eventPass.sessions);
        result.behaviorSignal = signal;
        result.behaviorSummary = toBehaviorSummary(signal, endTimestamp);
        return result;
    }

    private EventPass parseEvents(
            UsageStatsManager usageStatsManager,
            Context context,
            PackageManager packageManager,
            Map<String, AppAccumulator> accumulators,
            long startTime,
            long endTime,
            boolean includeSystemApps,
            Set<String> excludedPackages
    ) {
        EventPass eventPass = new EventPass();
        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) {
            return eventPass;
        }

        UsageEvents.Event event = new UsageEvents.Event();
        String currentPackage = null;
        long currentSessionStart = 0L;
        String lastDistinctPackage = null;
        String lastAcceptedForegroundPackage = null;
        long lastAcceptedForegroundTime = 0L;

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            String packageName = event.getPackageName();
            if (packageName == null || packageName.trim().isEmpty()) {
                continue;
            }
            if (excludedPackages.contains(packageName)) {
                continue;
            }

            ResolvedApp resolvedApp = resolveApp(context, packageManager, packageName, includeSystemApps);
            if (resolvedApp == null) {
                continue;
            }

            AppAccumulator accumulator = getOrCreate(accumulators, packageName, resolvedApp);
            long eventTime = Math.max(startTime, Math.min(event.getTimeStamp(), endTime));
            int eventType = event.getEventType();

            if (isForegroundEvent(eventType)) {
                if (packageName.equals(lastAcceptedForegroundPackage)
                        && eventTime - lastAcceptedForegroundTime <= DUPLICATE_FOREGROUND_WINDOW_MILLIS) {
                    continue;
                }

                if (currentPackage != null && !currentPackage.equals(packageName) && currentSessionStart > 0L) {
                    closeSession(accumulators.get(currentPackage), currentSessionStart, eventTime, startTime, eventPass);
                }

                if (!packageName.equals(lastDistinctPackage)) {
                    if (lastDistinctPackage != null) {
                        eventPass.totalAppSwitchCount++;
                    }
                    lastDistinctPackage = packageName;
                }

                accumulator.launchCount++;
                accumulator.firstOpenedAt = accumulator.firstOpenedAt == 0L ? eventTime : Math.min(accumulator.firstOpenedAt, eventTime);
                accumulator.lastUsedAt = Math.max(accumulator.lastUsedAt, eventTime);
                currentPackage = packageName;
                currentSessionStart = eventTime;
                lastAcceptedForegroundPackage = packageName;
                lastAcceptedForegroundTime = eventTime;
                eventPass.totalLaunchCount++;
            } else if (isBackgroundEvent(eventType)) {
                accumulator.lastUsedAt = Math.max(accumulator.lastUsedAt, eventTime);
                if (currentPackage != null && currentPackage.equals(packageName) && currentSessionStart > 0L) {
                    closeSession(accumulator, currentSessionStart, eventTime, startTime, eventPass);
                    currentPackage = null;
                    currentSessionStart = 0L;
                }
            }
        }

        if (currentPackage != null && currentSessionStart > 0L) {
            closeSession(accumulators.get(currentPackage), currentSessionStart, endTime, startTime, eventPass);
        }

        return eventPass;
    }

    private void closeSession(
            AppAccumulator accumulator,
            long sessionStart,
            long sessionEnd,
            long dayTimestamp,
            EventPass eventPass
    ) {
        if (accumulator == null || sessionEnd <= sessionStart) {
            return;
        }

        long duration = sessionEnd - sessionStart;
        accumulator.eventDerivedUsageMillis += duration;
        accumulator.foregroundSessions++;
        accumulator.shortSessionCount += duration <= BehaviorThresholds.SHORT_SESSION_THRESHOLD_MILLIS ? 1 : 0;
        accumulator.longestSessionMillis = Math.max(accumulator.longestSessionMillis, duration);
        accumulator.lastUsedAt = Math.max(accumulator.lastUsedAt, sessionEnd);

        UsageSession session = new UsageSession();
        session.dayTimestamp = dayTimestamp;
        session.packageName = accumulator.packageName;
        session.sessionStart = sessionStart;
        session.sessionEnd = sessionEnd;
        session.durationMillis = duration;
        session.wasShortSession = duration <= BehaviorThresholds.SHORT_SESSION_THRESHOLD_MILLIS;
        session.wasLateNightSession = calculateLateNightOverlap(sessionStart, sessionEnd) > 0L;
        // ── New fields ──
        session.computeDurationCategory();
        AppCategoryMapper.Category cat = AppCategoryMapper.getCategory(accumulator.packageName);
        session.sessionType = cat.isPassive ? "passive" :
                (cat == AppCategoryMapper.Category.UTILITY ? "utility" :
                (cat == AppCategoryMapper.Category.OTHER ? "unknown" : "active"));
        session.previousAppPackage = eventPass.lastClosedPackage;
        eventPass.lastClosedPackage = accumulator.packageName;
        eventPass.sessions.add(session);

        if (duration >= BehaviorThresholds.BINGE_SESSION_THRESHOLD_MILLIS) {
            eventPass.bingeSessionCount++;
            accumulator.bingeSessionCount++;
        }
        if (session.wasShortSession) {
            eventPass.shortSessionCount++;
        }
        if (session.wasLateNightSession) {
            accumulator.nightUsageMillis += calculateLateNightOverlap(sessionStart, sessionEnd);
        }
        eventPass.longestSessionMillis = Math.max(eventPass.longestSessionMillis, duration);
        eventPass.lateNightUsageMillis += calculateLateNightOverlap(sessionStart, sessionEnd);

        // ── AI Vectors ──
        if (session.previousAppPackage != null && !session.previousAppPackage.equals(session.packageName)) {
            String transition = session.previousAppPackage + " -> " + session.packageName;
            eventPass.appTransitions.put(transition, eventPass.appTransitions.getOrDefault(transition, 0) + 1);
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(sessionStart);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String quadrant;
        if (hour >= 5 && hour < 12) quadrant = "MORNING_ROUTINE";
        else if (hour >= 12 && hour < 17) quadrant = "WORK_HOURS";
        else if (hour >= 17 && hour < 23) quadrant = "EVENING_WIND_DOWN";
        else quadrant = "LATE_NIGHT";
        
        eventPass.timeOfDayVectors.put(quadrant, eventPass.timeOfDayVectors.getOrDefault(quadrant, 0L) + duration);

        // ── Track hourly buckets ──
        addToHourlyBucket(eventPass, sessionStart, sessionEnd);
    }

    /** Build hourly breakdown JSON from sessions. */
    private String buildHourlyBreakdown(List<UsageSession> sessions) {
        long[] hourBuckets = new long[24];
        for (UsageSession s : sessions) {
            distributeToHourBuckets(hourBuckets, s.sessionStart, s.sessionEnd);
        }
        JSONObject json = new JSONObject();
        try {
            for (int h = 0; h < 24; h++) {
                json.put(String.valueOf(h), hourBuckets[h]);
            }
        } catch (Exception ignored) {}
        return json.toString();
    }

    private void distributeToHourBuckets(long[] buckets, long start, long end) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(start);
        while (cal.getTimeInMillis() < end) {
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            Calendar nextHour = (Calendar) cal.clone();
            nextHour.add(Calendar.HOUR_OF_DAY, 1);
            nextHour.set(Calendar.MINUTE, 0);
            nextHour.set(Calendar.SECOND, 0);
            nextHour.set(Calendar.MILLISECOND, 0);
            long sliceEnd = Math.min(end, nextHour.getTimeInMillis());
            buckets[hour] += sliceEnd - cal.getTimeInMillis();
            cal.setTimeInMillis(sliceEnd);
        }
    }

    private void addToHourlyBucket(EventPass eventPass, long start, long end) {
        // Tracked via session list — buildHourlyBreakdown computes from sessions
    }

    // ─────────────────────────────────────────────────────────────────────
    // PHASE 4 — Direct telemetry sync
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sync scroll intensity: prefer real AccessibilityService data,
     * fall back to session-pattern heuristic when the service hasn't
     * collected data yet (e.g., user hasn't granted permission).
     *
     * @return scroll intensity score 0.0 – 10.0
     */
    private float syncScrollIntensity(Context context, EventPass eventPass,
                                       long passiveTimeMs, long totalTimeMs) {
        ScrollEventTracker.ScrollData scrollData =
                ScrollEventTracker.getTodayData(context);

        if (scrollData.isValid()) {
            // Direct measurement available — use real data
            return ScrollEventTracker.computeIntensityScore(scrollData, totalTimeMs);
        }

        // Fallback: session-pattern heuristic (Phase 1 approach)
        return estimateScrollIntensityHeuristic(eventPass, passiveTimeMs, totalTimeMs);
    }

    /**
     * Sync notification response average from NotificationEventTracker.
     *
     * @return average notification response time in ms, 0 if no data
     */
    private long syncNotificationResponseAvg(Context context) {
        NotificationEventTracker.NotificationData notifData =
                NotificationEventTracker.getTodayData(context);

        if (notifData.isValid()) {
            return notifData.getAverageResponseMs();
        }

        return 0L; // No notification data collected yet
    }

    /**
     * Phase 1 heuristic: estimate scroll intensity from session patterns.
     * Retained as fallback when AccessibilityService scroll tracking
     * is unavailable (user hasn't granted permission).
     */
    private float estimateScrollIntensityHeuristic(EventPass eventPass,
                                                     long passiveTimeMs,
                                                     long totalTimeMs) {
        if (totalTimeMs <= 0) return 0f;
        // Heuristic: many short passive sessions + high passive ratio = likely scrolling
        int passiveShortSessions = 0;
        int passiveLongSessions = 0;
        for (UsageSession s : eventPass.sessions) {
            if ("passive".equals(s.sessionType)) {
                if (s.durationMillis < 120000) passiveShortSessions++;
                else passiveLongSessions++;
            }
        }
        float passiveRatio = (float) passiveTimeMs / totalTimeMs;
        float shortSessionFactor = Math.min(1f, passiveShortSessions / 15f);
        float longSessionFactor = Math.min(1f, passiveLongSessions / 5f);
        // Combine: passive ratio (40%) + short sessions (30%) + long sessions (30%)
        float raw = (passiveRatio * 4f) + (shortSessionFactor * 3f) + (longSessionFactor * 3f);
        return Math.min(10f, Math.max(0f, raw));
    }

    private UsageBehaviorSignal buildBehaviorSignal(
            long dayTimestamp,
            long totalUsageMillis,
            long passiveTimeMillis,
            long activeTimeMillis,
            EventPass eventPass,
            List<AppAccumulator> apps,
            int highRiskAppCount,
            int categoryCount
    ) {
        UsageBehaviorSignal signal = new UsageBehaviorSignal();
        signal.dayTimestamp = dayTimestamp;
        signal.totalUsageMillis = totalUsageMillis;

        // AI Vector: Active vs Passive
        signal.activeVsPassiveRatio = activeTimeMillis > 0 ? (double) passiveTimeMillis / activeTimeMillis : passiveTimeMillis > 0 ? 999.0 : 0.0;
        
        // AI Vector: Quadrant analysis
        long maxQuadrant = 0;
        String dominant = "UNKNOWN";
        for (Map.Entry<String, Long> entry : eventPass.timeOfDayVectors.entrySet()) {
            if (entry.getValue() > maxQuadrant) {
                maxQuadrant = entry.getValue();
                dominant = entry.getKey();
            }
        }
        signal.dominantUsageQuadrant = dominant;
        
        // AI Vector: App Loops
        for (Map.Entry<String, Integer> loop : eventPass.appTransitions.entrySet()) {
            if (loop.getValue() >= 3) { // Threshold for a "frequent loop"
                signal.frequentAppLoops.add(loop.getKey() + " (" + loop.getValue() + "x)");
            }
        }

        int sessionCount = eventPass.sessions.size();
        double shortSessionRatio = sessionCount == 0 ? 0d : eventPass.shortSessionCount / (double) sessionCount;
        double topRatio = totalUsageMillis <= 0L || apps.isEmpty()
                ? 0d
                : apps.get(0).usageTimeMillis / (double) totalUsageMillis;
        double lateNightRatio = totalUsageMillis <= 0L ? 0d : eventPass.lateNightUsageMillis / (double) totalUsageMillis;

        signal.screenTimeIntensity = clamp((int) Math.round((totalUsageMillis / (double) (8L * 60L * 60L * 1000L)) * 100d), 0, 100);
        signal.switchScore = clamp((int) Math.round((eventPass.totalAppSwitchCount / 40d) * 100d), 0, 100);
        signal.fragmentedUsageScore = clamp((int) Math.round((shortSessionRatio * 60d) + (signal.switchScore * 0.4d)), 0, 100);
        signal.bingeScore = clamp((int) Math.round((eventPass.bingeSessionCount * 25d)
                + ((eventPass.longestSessionMillis / (double) BehaviorThresholds.EXTREME_SESSION_THRESHOLD_MILLIS) * 45d)), 0, 100);
        signal.topAppDominanceScore = clamp((int) Math.round(topRatio * 100d), 0, 100);
        signal.lateNightScore = clamp((int) Math.round(lateNightRatio * 100d), 0, 100);
        signal.dependencyScore = clamp((int) Math.round((signal.topAppDominanceScore * 0.65d) + (highRiskAppCount * 12d)), 0, 100);
        signal.appDiversityScore = clamp(categoryCount * 18, 0, 100);
        signal.distractionPatternScore = clamp((int) Math.round(
                (signal.fragmentedUsageScore * 0.45d)
                        + (signal.switchScore * 0.25d)
                        + (signal.lateNightScore * 0.15d)
                        + (signal.topAppDominanceScore * 0.15d)
        ), 0, 100);

        if (signal.switchScore >= 65 || signal.fragmentedUsageScore >= 65) {
            signal.riskFlags.add("Heavy switching");
            signal.explanatoryNotes.add("Attention looked fragmented across frequent app changes and short sessions.");
        }
        if (signal.bingeScore >= 60) {
            signal.riskFlags.add("Binge session pattern");
            signal.explanatoryNotes.add("One or more long continuous sessions crossed the binge threshold.");
        }
        if (signal.lateNightScore >= 30) {
            signal.riskFlags.add("Late-night use");
            signal.explanatoryNotes.add("A meaningful share of use happened during the 11 PM to 5 AM window.");
        }
        if (signal.topAppDominanceScore >= 55) {
            signal.riskFlags.add("Dominant app dependency");
            signal.explanatoryNotes.add("A single app dominated too much of the day.");
        }
        if (signal.appDiversityScore <= 25 && totalUsageMillis > MIN_MEANINGFUL_USAGE_MILLIS) {
            signal.riskFlags.add("Low app diversity");
            signal.explanatoryNotes.add("The day was concentrated in a very small app mix.");
        }

        if (signal.lateNightScore >= 45) {
            signal.summaryLabel = "Heavy late-night use";
        } else if (signal.bingeScore >= 65) {
            signal.summaryLabel = "Binge usage detected";
        } else if (signal.fragmentedUsageScore >= 65) {
            signal.summaryLabel = "High fragmentation";
        } else if (signal.topAppDominanceScore >= 60) {
            signal.summaryLabel = "Dominant app day";
        } else {
            signal.summaryLabel = "Practical balance";
        }

        if (signal.explanatoryNotes.isEmpty()) {
            signal.explanatoryNotes.add("Usage patterns look relatively stable without a strong compulsive or fragmented signal.");
        }
        return signal;
    }

    private BehaviorUsageSummary toBehaviorSummary(UsageBehaviorSignal signal, long createdAt) {
        BehaviorUsageSummary summary = new BehaviorUsageSummary();
        summary.dayTimestamp = signal.dayTimestamp;
        summary.totalUsage = signal.totalUsageMillis;
        summary.fragmentedUsageScore = signal.fragmentedUsageScore;
        summary.bingeScore = signal.bingeScore;
        summary.switchScore = signal.switchScore;
        summary.topAppDominanceScore = signal.topAppDominanceScore;
        summary.lateNightPenaltyScore = signal.lateNightScore;
        summary.distractionPatternScore = signal.distractionPatternScore;
        summary.summaryLabel = signal.summaryLabel;
        summary.explanatoryNotes = join(signal.explanatoryNotes);
        summary.createdAt = createdAt;
        return summary;
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private Set<String> getExcludedPackages(Context context, PackageManager pm) {
        Set<String> excluded = new HashSet<>();
        excluded.add("com.android.systemui");
        excluded.add(context.getPackageName()); // Exclude MindTrace if needed? Actually DW includes MindTrace.
        // Wait, DW DOES include MindTrace AI. So we should NOT exclude our own package if we want to match DW!
        excluded.remove(context.getPackageName());
        
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<android.content.pm.ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfos != null) {
            for (android.content.pm.ResolveInfo info : resolveInfos) {
                excluded.add(info.activityInfo.packageName);
            }
        }
        
        // Add common manufacturer launchers just in case
        excluded.add("com.sec.android.app.launcher");
        excluded.add("com.google.android.apps.nexuslauncher");
        excluded.add("com.oneplus.setupwizard");
        excluded.add("com.coloros.launcher");
        excluded.add("com.bbk.launcher2");
        excluded.add("com.huawei.android.launcher");
        excluded.add("com.miui.home");
        
        return excluded;
    }

    private AppAccumulator getOrCreate(Map<String, AppAccumulator> accumulators, String packageName, ResolvedApp resolvedApp) {
        AppAccumulator accumulator = accumulators.get(packageName);
        if (accumulator != null) {
            return accumulator;
        }
        accumulator = new AppAccumulator();
        accumulator.packageName = packageName;
        accumulator.appName = resolvedApp.appName;
        accumulator.appIcon = resolvedApp.appIcon;
        accumulator.appCategory = resolvedApp.appCategory;
        accumulator.isSystemApp = resolvedApp.isSystemApp;
        accumulator.isUserVisible = resolvedApp.isUserVisible;
        accumulators.put(packageName, accumulator);
        return accumulator;
    }

    private ResolvedApp resolveApp(
            Context context,
            PackageManager packageManager,
            String packageName,
            boolean includeSystemApps
    ) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return null;
        }

        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);

            // Digital Wellbeing STRICT RULE: If it cannot be launched from the app drawer, it's not a user-facing app.
            // This prevents internal components like "System Launcher" or "Wireless Settings" background services from being summed!
            if (launchIntent == null) {
                return null;
            }

            CharSequence label = packageManager.getApplicationLabel(applicationInfo);
            Drawable icon = packageManager.getApplicationIcon(applicationInfo);

            ResolvedApp resolvedApp = new ResolvedApp();
            resolvedApp.packageName = packageName;
            resolvedApp.appName = label == null ? packageName : label.toString();
            resolvedApp.appIcon = icon;
            resolvedApp.appCategory = resolveCategory(applicationInfo);
            
            boolean isSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            resolvedApp.isSystemApp = isSystemApp;
            resolvedApp.isUserVisible = true;
            return resolvedApp;
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    /** Use AppCategoryMapper instead of basic system categories. */
    private String resolveCategory(ApplicationInfo applicationInfo) {
        // Kept for backward compatibility — new code uses AppCategoryMapper directly
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "OTHER";
        }
        switch (applicationInfo.category) {
            case ApplicationInfo.CATEGORY_GAME: return "GAMING";
            case ApplicationInfo.CATEGORY_AUDIO: return "ENTERTAINMENT";
            case ApplicationInfo.CATEGORY_VIDEO: return "VIDEO";
            case ApplicationInfo.CATEGORY_IMAGE: return "ENTERTAINMENT";
            case ApplicationInfo.CATEGORY_SOCIAL: return "SOCIAL";
            case ApplicationInfo.CATEGORY_NEWS: return "NEWS";
            case ApplicationInfo.CATEGORY_MAPS: return "TRAVEL";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "PRODUCTIVITY";
            default: return "OTHER";
        }
    }

    private boolean isHighRiskApp(AppAccumulator accumulator) {
        if (accumulator == null) {
            return false;
        }
        if (accumulator.usageTimeMillis >= HIGH_RISK_USAGE_MILLIS) {
            return true;
        }
        String category = safe(accumulator.appCategory, "Other");
        return ("Social".equals(category) || "Video".equals(category) || "Game".equals(category))
                && accumulator.usageTimeMillis >= CATEGORY_RISK_USAGE_MILLIS;
    }

    private long calculateLateNightOverlap(long sessionStart, long sessionEnd) {
        long overlap = 0L;
        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(sessionStart);
        cursor.set(Calendar.HOUR_OF_DAY, 0);
        cursor.set(Calendar.MINUTE, 0);
        cursor.set(Calendar.SECOND, 0);
        cursor.set(Calendar.MILLISECOND, 0);

        while (cursor.getTimeInMillis() <= sessionEnd) {
            Calendar lateStart = (Calendar) cursor.clone();
            lateStart.set(Calendar.HOUR_OF_DAY, BehaviorThresholds.LATE_NIGHT_START_HOUR);
            Calendar lateEnd = (Calendar) cursor.clone();
            lateEnd.add(Calendar.DAY_OF_YEAR, 1);
            lateEnd.set(Calendar.HOUR_OF_DAY, BehaviorThresholds.LATE_NIGHT_END_HOUR);

            overlap += intersect(sessionStart, sessionEnd, lateStart.getTimeInMillis(), lateEnd.getTimeInMillis());
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }

        return overlap;
    }

    private long intersect(long startA, long endA, long startB, long endB) {
        long overlapStart = Math.max(startA, startB);
        long overlapEnd = Math.min(endA, endB);
        return Math.max(0L, overlapEnd - overlapStart);
    }

    private boolean isForegroundEvent(int eventType) {
        return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                || eventType == UsageEvents.Event.ACTIVITY_RESUMED;
    }

    private boolean isBackgroundEvent(int eventType) {
        return eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
                || eventType == UsageEvents.Event.ACTIVITY_PAUSED;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class ResolvedApp {
        String packageName;
        String appName;
        Drawable appIcon;
        String appCategory;
        boolean isSystemApp;
        boolean isUserVisible;
    }

    private static class AppAccumulator {
        String packageName;
        String appName;
        Drawable appIcon;
        String appCategory;
        long usageTimeMillis;
        int launchCount;
        int foregroundSessions;
        int shortSessionCount;
        int bingeSessionCount;
        long firstOpenedAt;
        long lastUsedAt;
        long longestSessionMillis;
        long eventDerivedUsageMillis;
        long nightUsageMillis;
        boolean isSystemApp;
        boolean isUserVisible;
    }

    private static class EventPass {
        final List<UsageSession> sessions = new ArrayList<>();
        int totalLaunchCount;
        int totalAppSwitchCount;
        int bingeSessionCount;
        int shortSessionCount;
        long lateNightUsageMillis;
        long longestSessionMillis;
        String lastClosedPackage; // For previousAppPackage tracking
        
        // AI Vectors
        final Map<String, Long> timeOfDayVectors = new HashMap<>();
        final Map<String, Integer> appTransitions = new HashMap<>();
    }

    public static class UsageIntelligenceResult {
        public long dayTimestamp;
        public long recordedAt;
        public DailyUsage dailyUsage;
        public final List<AppUsageSnapshot> appSnapshots = new ArrayList<>();
        public final List<UsageSession> sessions = new ArrayList<>();
        public BehaviorUsageSummary behaviorSummary;
        public UsageBehaviorSignal behaviorSignal;
        public final List<AppUsageModel> appUsageModels = new ArrayList<>();
        public AppUsageModel mostUsedApp;
    }
}
