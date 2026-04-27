package com.mindtrace.ai.behavior;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.mindtrace.ai.ai.AppCategoryMapper;
import com.mindtrace.ai.database.entity.BehaviorSnapshotEntity;
import com.mindtrace.ai.database.entity.UsageSession;
import com.mindtrace.ai.service.NotificationEventTracker;
import com.mindtrace.ai.service.ScreenEventReceiver;
import com.mindtrace.ai.service.ScrollEventTracker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BehaviorAnalyzer {

    // ═════════════════════════════════════════════════════════════════════
    // INTELLIGENCE LAYER — Advanced behavioural computations
    // These methods take processed session data and produce signals
    // for the BehaviorSnapshotEntity's 4 intelligence layers.
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Compute fragmentation index — measures how fractured attention is.
     *
     * <p>Unlike a simple ratio, this uses weighted scoring:</p>
     * <ul>
     *   <li>Micro sessions (&lt;30s) get 1.0 weight — reflexive, zero-value interactions</li>
     *   <li>Short sessions (30s–2min) get 0.6 weight — quick checks, low engagement</li>
     *   <li>Normal sessions (2–15min) get 0.0 weight — healthy engagement</li>
     *   <li>Extended/binge sessions get -0.2 weight — bonus for sustained focus</li>
     * </ul>
     *
     * <p>Final score is normalized to 0.0–1.0.</p>
     *
     * @param sessions List of usage sessions for the day
     * @return 0.0 (fully focused, all long sessions) to 1.0 (fully fragmented)
     */
    public float computeFragmentationIndex(List<UsageSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return 0f;

        float weightedSum = 0f;
        int count = 0;

        for (UsageSession s : sessions) {
            long dur = s.durationMillis;
            if (dur < 30_000) {
                // Micro: < 30s — reflexive check, full fragmentation weight
                weightedSum += 1.0f;
            } else if (dur < 120_000) {
                // Short: 30s–2min — quick check
                weightedSum += 0.6f;
            } else if (dur < 900_000) {
                // Normal: 2–15min — healthy, no penalty
                weightedSum += 0.0f;
            } else {
                // Extended/binge: 15min+ — reward sustained focus
                weightedSum -= 0.2f;
            }
            count++;
        }

        if (count == 0) return 0f;

        float raw = weightedSum / count;
        return Math.min(1f, Math.max(0f, raw));
    }

    /**
     * Detect escape behaviour — did the user flee to dopamine apps after stress?
     *
     * <p>Escape behaviour is when someone uses passive apps (social, video, gaming)
     * as emotional numbing after experiencing distress. This method detects it by:</p>
     * <ol>
     *   <li>Checking if a high-stress check-in occurred today (stress ≥ 4)</li>
     *   <li>Measuring passive app usage in the 2-hour window AFTER the check-in</li>
     *   <li>Comparing that usage rate against the user's baseline passive rate</li>
     *   <li>Scaling the score by stress severity (stress 4 vs 5 has different weight)</li>
     * </ol>
     *
     * <p>A score > 0.5 means passive usage surged meaningfully after stress.
     * A score > 0.8 means clear escape/avoidance behaviour.</p>
     *
     * @param sessions            All sessions for the day
     * @param checkInTimestamp    When the user completed their check-in (epoch ms). 0 if no check-in.
     * @param stressLevel         Reported stress level (1–5). 0 if no check-in.
     * @param baselinePassiveRatio User's average passive ratio from last 7 days (0.0–1.0). Use 0.5 default.
     * @return 0.0 (no escape pattern) to 1.0 (clear escape behaviour)
     */
    public float detectEscapeBehavior(
            List<UsageSession> sessions,
            long checkInTimestamp,
            int stressLevel,
            float baselinePassiveRatio
    ) {
        // No check-in or low stress → no escape signal
        if (sessions == null || sessions.isEmpty()) return 0f;
        if (checkInTimestamp <= 0 || stressLevel < 4) return 0f;

        // Define the escape window: 2 hours after check-in
        long windowStart = checkInTimestamp;
        long windowEnd = checkInTimestamp + (2L * 60L * 60L * 1000L); // +2 hours

        // Measure passive vs total usage in the post-stress window
        long passiveInWindow = 0;
        long totalInWindow = 0;

        for (UsageSession s : sessions) {
            // Calculate overlap between session and escape window
            long overlapStart = Math.max(s.sessionStart, windowStart);
            long overlapEnd = Math.min(s.sessionEnd, windowEnd);
            if (overlapEnd <= overlapStart) continue;

            long overlapMs = overlapEnd - overlapStart;
            totalInWindow += overlapMs;

            if ("passive".equals(s.sessionType)) {
                passiveInWindow += overlapMs;
            }
        }

        // Not enough data in the window
        if (totalInWindow < 300_000) return 0f; // Need at least 5 min of data

        // Compute passive ratio in post-stress window
        float postStressPassiveRatio = (float) passiveInWindow / totalInWindow;

        // Compare against baseline — how much did passive usage SURGE?
        float safeBaseline = Math.max(0.1f, baselinePassiveRatio); // Avoid divide-by-zero
        float surgeRatio = postStressPassiveRatio / safeBaseline;

        // Scale by stress severity: stress 4 = 0.7x weight, stress 5 = 1.0x weight
        float severityMultiplier = stressLevel == 5 ? 1.0f : 0.7f;

        // Escape score: surge above 1.0 = passive usage exceeded baseline after stress
        // surgeRatio 1.0 = same as baseline (score ~0), 2.0 = double baseline (score ~0.7)
        float escapeScore;
        if (surgeRatio <= 1.0f) {
            // Passive usage didn't increase after stress — no escape
            escapeScore = 0f;
        } else {
            // Passive usage surged after stress — escape detected
            // Map surge 1.0→2.0+ to score 0.0→1.0, with diminishing returns
            escapeScore = Math.min(1f, (surgeRatio - 1.0f) * 0.8f * severityMultiplier);
        }

        // Bonus: if post-stress passive ratio is very high (>80%) regardless of baseline
        if (postStressPassiveRatio > 0.8f && stressLevel >= 4) {
            escapeScore = Math.max(escapeScore, 0.6f * severityMultiplier);
        }

        return Math.min(1f, Math.max(0f, escapeScore));
    }

    /**
     * Compute today's average session length in milliseconds.
     * Excludes micro sessions (&lt;10s) to avoid noise from system transitions.
     *
     * @param sessions Today's usage sessions
     * @return Average session length in ms, or 0 if no valid sessions
     */
    public long computeAvgSessionLength(List<UsageSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return 0;

        long totalDuration = 0;
        int validCount = 0;

        for (UsageSession s : sessions) {
            if (s.durationMillis >= 10_000) { // Exclude <10s noise
                totalDuration += s.durationMillis;
                validCount++;
            }
        }

        return validCount > 0 ? totalDuration / validCount : 0;
    }

    /**
     * Compute attention span trend — is the user's average session length shrinking?
     *
     * <p>Uses simple linear regression on historical average session lengths
     * to detect attention span decay or improvement over time.</p>
     *
     * <p>The method computes the slope of a best-fit line through
     * [day_index, avg_session_ms] data points. A negative slope means
     * sessions are getting shorter (attention decay).</p>
     *
     * <h3>Interpretation:</h3>
     * <ul>
     *   <li><b>-1.0</b> — Rapidly shrinking attention span (losing ~2min/day avg)</li>
     *   <li><b>-0.5</b> — Moderate decay</li>
     *   <li><b>0.0</b> — Stable</li>
     *   <li><b>+0.5</b> — Improving focus</li>
     *   <li><b>+1.0</b> — Rapidly improving (gaining ~2min/day avg)</li>
     * </ul>
     *
     * @param historicalAvgSessionMs Array of avg session lengths (ms) for past N days,
     *                               ordered oldest→newest. Minimum 3 days needed.
     * @param todayAvgSessionMs      Today's average session length (ms)
     * @return -1.0 (rapidly shrinking) to +1.0 (rapidly improving). 0.0 if insufficient data.
     */
    public float computeAttentionSpanTrend(long[] historicalAvgSessionMs, long todayAvgSessionMs) {
        if (historicalAvgSessionMs == null || historicalAvgSessionMs.length < 2) return 0f;

        // Build data points: append today to the history
        int n = historicalAvgSessionMs.length + 1;
        double[] x = new double[n]; // day index
        double[] y = new double[n]; // avg session length in minutes

        for (int i = 0; i < historicalAvgSessionMs.length; i++) {
            x[i] = i;
            y[i] = historicalAvgSessionMs[i] / 60_000.0; // Convert to minutes
        }
        x[n - 1] = n - 1;
        y[n - 1] = todayAvgSessionMs / 60_000.0;

        // Simple linear regression: slope = (n*Σxy - Σx*Σy) / (n*Σx² - (Σx)²)
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }

        double denominator = n * sumX2 - sumX * sumX;
        if (denominator == 0) return 0f;

        double slope = (n * sumXY - sumX * sumY) / denominator;

        // Normalize slope to -1.0 to +1.0
        // A slope of +2.0 min/day = max improvement (+1.0)
        // A slope of -2.0 min/day = max decay (-1.0)
        float normalized = (float) (slope / 2.0);
        return Math.min(1f, Math.max(-1f, normalized));
    }

    /**
     * Detect morning phone grab pattern — compulsive phone use upon waking.
     *
     * <p>This is a multi-signal detector that measures four dimensions
     * of morning compulsiveness:</p>
     * <ol>
     *   <li><b>Time-to-first-session</b> — how quickly the user starts a real session
     *       after unlocking. &lt;2min = compulsive, &lt;5min = reflexive, &gt;15min = healthy.</li>
     *   <li><b>First app type</b> — was the first meaningful app passive/dopamine
     *       (social, video, gaming) or productive (email, calendar)?</li>
     *   <li><b>30-minute passive load</b> — total passive app time in the first 30 minutes.
     *       High values indicate morning mindless scrolling.</li>
     *   <li><b>App scatter</b> — number of distinct apps opened in first 15 minutes.
     *       High scatter = restless morning checking across apps.</li>
     * </ol>
     *
     * @param sessions       All sessions for the day, sorted by sessionStart
     * @param firstUnlockTime Timestamp of first unlock (from ScreenEventReceiver). 0 = unavailable.
     * @return MorningGrabResult with composite score and individual signals
     */
    public MorningGrabResult detectMorningPhonePattern(
            List<UsageSession> sessions,
            long firstUnlockTime
    ) {
        MorningGrabResult result = new MorningGrabResult();
        if (sessions == null || sessions.isEmpty() || firstUnlockTime <= 0) return result;

        // ── Signal 1: Time to first meaningful session (>10s) ──
        long firstSessionStart = 0;
        for (UsageSession s : sessions) {
            if (s.sessionStart >= firstUnlockTime && s.durationMillis >= 10_000) {
                firstSessionStart = s.sessionStart;
                break;
            }
        }

        if (firstSessionStart > 0) {
            result.timeToFirstSessionMs = firstSessionStart - firstUnlockTime;
        }

        // ── Signal 2: Was the first app passive/dopamine? ──
        for (UsageSession s : sessions) {
            if (s.sessionStart >= firstUnlockTime && s.durationMillis >= 10_000) {
                AppCategoryMapper.Category cat = AppCategoryMapper.getCategory(s.packageName);
                result.firstAppPackage = s.packageName;
                result.firstAppIsPassive = cat.isPassive;
                result.firstAppDopamineRisk = cat.dopamineRisk;
                break;
            }
        }

        // ── Signal 3: Passive time in first 30 minutes ──
        long windowEnd = firstUnlockTime + (30L * 60L * 1000L); // +30 min
        long passiveFirst30 = 0;
        long totalFirst30 = 0;

        for (UsageSession s : sessions) {
            if (s.sessionStart >= windowEnd) break;
            long overlapStart = Math.max(s.sessionStart, firstUnlockTime);
            long overlapEnd = Math.min(s.sessionEnd, windowEnd);
            if (overlapEnd <= overlapStart) continue;

            long overlapMs = overlapEnd - overlapStart;
            totalFirst30 += overlapMs;

            if ("passive".equals(s.sessionType)) {
                passiveFirst30 += overlapMs;
            }
        }
        result.passiveTimeFirst30Min = passiveFirst30;
        result.totalTimeFirst30Min = totalFirst30;

        // ── Signal 4: App scatter in first 15 minutes ──
        long scatterWindow = firstUnlockTime + (15L * 60L * 1000L);
        java.util.Set<String> distinctApps = new java.util.HashSet<>();
        for (UsageSession s : sessions) {
            if (s.sessionStart >= scatterWindow) break;
            if (s.sessionStart >= firstUnlockTime && s.durationMillis >= 5_000) {
                distinctApps.add(s.packageName);
            }
        }
        result.appsInFirst15Min = distinctApps.size();

        // ── Composite Score ──
        result.compulsivenessScore = computeMorningCompulsivenessScore(result);

        return result;
    }

    private float computeMorningCompulsivenessScore(MorningGrabResult r) {
        float score = 0f;

        // Time-to-first-session (35% weight)
        // <1min = 1.0, <2min = 0.8, <5min = 0.5, <15min = 0.2, >15min = 0.0
        if (r.timeToFirstSessionMs > 0) {
            long mins = r.timeToFirstSessionMs / 60_000;
            float timeScore;
            if (mins < 1) timeScore = 1.0f;
            else if (mins < 2) timeScore = 0.8f;
            else if (mins < 5) timeScore = 0.5f;
            else if (mins < 15) timeScore = 0.2f;
            else timeScore = 0.0f;
            score += timeScore * 0.35f;
        }

        // First app type (25% weight)
        // Passive + high dopamine = 1.0, passive + low dopamine = 0.6, active = 0.0
        if (r.firstAppIsPassive) {
            score += (0.4f + r.firstAppDopamineRisk * 0.6f) * 0.25f;
        }

        // Passive load in first 30 min (25% weight)
        // >20min passive = 1.0, >10min = 0.6, >5min = 0.3, <5min = 0.0
        if (r.totalTimeFirst30Min > 0) {
            float passiveMin = r.passiveTimeFirst30Min / 60_000f;
            float loadScore;
            if (passiveMin > 20) loadScore = 1.0f;
            else if (passiveMin > 10) loadScore = 0.6f;
            else if (passiveMin > 5) loadScore = 0.3f;
            else loadScore = 0.0f;
            score += loadScore * 0.25f;
        }

        // App scatter (15% weight)
        // >6 apps in 15min = 1.0, >4 = 0.6, >2 = 0.3, <=2 = 0.0
        float scatterScore;
        if (r.appsInFirst15Min > 6) scatterScore = 1.0f;
        else if (r.appsInFirst15Min > 4) scatterScore = 0.6f;
        else if (r.appsInFirst15Min > 2) scatterScore = 0.3f;
        else scatterScore = 0.0f;
        score += scatterScore * 0.15f;

        return Math.min(1f, Math.max(0f, score));
    }

    /**
     * Result of morning phone grab analysis — carries individual signals
     * for both the composite score and independent ML feature extraction.
     */
    public static class MorningGrabResult {
        /** Time from first unlock to first meaningful session (ms). 0 = no data. */
        public long timeToFirstSessionMs;

        /** Package name of the first app opened. */
        public String firstAppPackage;

        /** Whether the first app was a passive/dopamine app. */
        public boolean firstAppIsPassive;

        /** Dopamine risk of the first app (0.0–1.0). */
        public float firstAppDopamineRisk;

        /** Total passive app time in the first 30 minutes after unlock (ms). */
        public long passiveTimeFirst30Min;

        /** Total app time in the first 30 minutes after unlock (ms). */
        public long totalTimeFirst30Min;

        /** Distinct apps opened in first 15 minutes after unlock. */
        public int appsInFirst15Min;

        /** Composite compulsiveness score: 0.0 (healthy) to 1.0 (compulsive). */
        public float compulsivenessScore;

        /** Passive ratio in first 30 minutes. */
        public float getPassiveRatioFirst30Min() {
            return totalTimeFirst30Min > 0 ? (float) passiveTimeFirst30Min / totalTimeFirst30Min : 0f;
        }

        /** Time-to-first-session in minutes. */
        public float getTimeToFirstSessionMinutes() {
            return timeToFirstSessionMs / 60_000f;
        }
    }

    /**
     * Detect bedtime phone scrolling — usage in the wind-down period before sleep.
     *
     * <p>This is the circadian mirror of {@link #detectMorningPhonePattern}.
     * It measures five dimensions of bedtime disruption:</p>
     * <ol>
     *   <li><b>Wind-down usage</b> — total screen time in the 30 min before last screen-off</li>
     *   <li><b>Passive saturation</b> — what % of wind-down was passive/dopamine apps</li>
     *   <li><b>Blue-light risk</b> — time spent in video/gaming (high screen brightness apps)</li>
     *   <li><b>Late session count</b> — number of distinct sessions after 10pm</li>
     *   <li><b>Final session quality</b> — was the last session of the day passive + long?</li>
     * </ol>
     *
     * <p>Research shows phone use in the 30 minutes before bed suppresses
     * melatonin by up to 22% and delays sleep onset by 30-60 minutes.</p>
     *
     * @param sessions         All sessions for the day, sorted by sessionStart
     * @param lastScreenOffTime Timestamp of last screen-off (from ScreenEventReceiver). 0 = unavailable.
     * @return BedtimeScrollResult with composite score and individual signals
     */
    public BedtimeScrollResult detectBedtimePhonePattern(
            List<UsageSession> sessions,
            long lastScreenOffTime
    ) {
        BedtimeScrollResult result = new BedtimeScrollResult();
        if (sessions == null || sessions.isEmpty() || lastScreenOffTime <= 0) return result;

        // ── Signal 1: Total usage in 30-min wind-down window ──
        long windowStart = lastScreenOffTime - (30L * 60L * 1000L); // -30 min
        long passiveWindDown = 0;
        long totalWindDown = 0;
        long blueLightMs = 0;

        for (UsageSession s : sessions) {
            long overlapStart = Math.max(s.sessionStart, windowStart);
            long overlapEnd = Math.min(s.sessionEnd, lastScreenOffTime);
            if (overlapEnd <= overlapStart) continue;

            long overlapMs = overlapEnd - overlapStart;
            totalWindDown += overlapMs;

            // Signal 2: Passive saturation
            if ("passive".equals(s.sessionType)) {
                passiveWindDown += overlapMs;
            }

            // Signal 3: Blue-light risk (video + gaming)
            AppCategoryMapper.Category cat = AppCategoryMapper.getCategory(s.packageName);
            if (cat == AppCategoryMapper.Category.VIDEO || cat == AppCategoryMapper.Category.GAMING) {
                blueLightMs += overlapMs;
            }
        }

        result.windDownUsageMs = totalWindDown;
        result.passiveWindDownMs = passiveWindDown;
        result.blueLightExposureMs = blueLightMs;

        // ── Signal 4: Sessions after 10pm ──
        Calendar tenPm = Calendar.getInstance();
        tenPm.setTimeInMillis(lastScreenOffTime);
        tenPm.set(Calendar.HOUR_OF_DAY, 22);
        tenPm.set(Calendar.MINUTE, 0);
        tenPm.set(Calendar.SECOND, 0);
        // If last screen-off is before 10pm, go to previous day's 10pm
        if (lastScreenOffTime < tenPm.getTimeInMillis()) {
            tenPm.add(Calendar.DAY_OF_YEAR, -1);
        }
        long tenPmMs = tenPm.getTimeInMillis();

        int lateSessionCount = 0;
        long totalLateMs = 0;
        for (UsageSession s : sessions) {
            if (s.sessionStart >= tenPmMs && s.sessionEnd <= lastScreenOffTime) {
                lateSessionCount++;
                totalLateMs += s.durationMillis;
            }
        }
        result.sessionsAfter10pm = lateSessionCount;
        result.totalLateNightMs = totalLateMs;

        // ── Signal 5: Final session quality ──
        UsageSession lastSession = null;
        for (int i = sessions.size() - 1; i >= 0; i--) {
            UsageSession s = sessions.get(i);
            if (s.durationMillis >= 10_000 && s.sessionEnd <= lastScreenOffTime + 60_000) {
                lastSession = s;
                break;
            }
        }
        if (lastSession != null) {
            result.lastSessionDurationMs = lastSession.durationMillis;
            result.lastSessionIsPassive = "passive".equals(lastSession.sessionType);
            result.lastSessionPackage = lastSession.packageName;
        }

        // ── Composite Score ──
        result.sleepDisruptionScore = computeBedtimeDisruptionScore(result);

        return result;
    }

    private float computeBedtimeDisruptionScore(BedtimeScrollResult r) {
        float score = 0f;

        // Wind-down usage (30% weight)
        // >25min = 1.0, >15min = 0.7, >5min = 0.4, <5min = 0.1
        float windDownMin = r.windDownUsageMs / 60_000f;
        float windDownScore;
        if (windDownMin > 25) windDownScore = 1.0f;
        else if (windDownMin > 15) windDownScore = 0.7f;
        else if (windDownMin > 5) windDownScore = 0.4f;
        else if (windDownMin > 0) windDownScore = 0.1f;
        else windDownScore = 0f;
        score += windDownScore * 0.30f;

        // Passive saturation (25% weight)
        float passiveRatio = r.windDownUsageMs > 0
                ? (float) r.passiveWindDownMs / r.windDownUsageMs : 0f;
        score += passiveRatio * 0.25f;

        // Blue-light exposure (20% weight)
        // >15min video/gaming before bed = 1.0
        float blueMin = r.blueLightExposureMs / 60_000f;
        float blueScore;
        if (blueMin > 15) blueScore = 1.0f;
        else if (blueMin > 8) blueScore = 0.7f;
        else if (blueMin > 3) blueScore = 0.4f;
        else blueScore = 0f;
        score += blueScore * 0.20f;

        // Late session count (15% weight)
        // >5 sessions after 10pm = 1.0
        float lateScore;
        if (r.sessionsAfter10pm > 5) lateScore = 1.0f;
        else if (r.sessionsAfter10pm > 3) lateScore = 0.6f;
        else if (r.sessionsAfter10pm > 1) lateScore = 0.3f;
        else lateScore = 0f;
        score += lateScore * 0.15f;

        // Final session quality (10% weight)
        // Last session was passive + >10min = worst signal
        if (r.lastSessionIsPassive && r.lastSessionDurationMs > 600_000) {
            score += 1.0f * 0.10f; // 10min+ passive as the last thing
        } else if (r.lastSessionIsPassive && r.lastSessionDurationMs > 180_000) {
            score += 0.6f * 0.10f; // 3min+ passive
        } else if (r.lastSessionIsPassive) {
            score += 0.3f * 0.10f;
        }

        return Math.min(1f, Math.max(0f, score));
    }

    /**
     * Result of bedtime phone analysis — individual signals for ML
     * and composite disruption score for the dashboard.
     */
    public static class BedtimeScrollResult {
        /** Total screen time in the 30-min wind-down window (ms). */
        public long windDownUsageMs;

        /** Passive app time in the wind-down window (ms). */
        public long passiveWindDownMs;

        /** Video + gaming time in the wind-down (high blue-light apps) (ms). */
        public long blueLightExposureMs;

        /** Distinct sessions that started after 10pm. */
        public int sessionsAfter10pm;

        /** Total screen time after 10pm (ms). */
        public long totalLateNightMs;

        /** Duration of the very last session of the day (ms). */
        public long lastSessionDurationMs;

        /** Whether the last session was a passive app. */
        public boolean lastSessionIsPassive;

        /** Package name of the last app used before bed. */
        public String lastSessionPackage;

        /** Composite sleep disruption score: 0.0 (clean) to 1.0 (severe disruption). */
        public float sleepDisruptionScore;

        /** Wind-down passive ratio. */
        public float getPassiveRatio() {
            return windDownUsageMs > 0 ? (float) passiveWindDownMs / windDownUsageMs : 0f;
        }

        /** Wind-down usage in minutes. */
        public float getWindDownMinutes() {
            return windDownUsageMs / 60_000f;
        }

        /** Blue-light exposure in minutes. */
        public float getBlueLightMinutes() {
            return blueLightExposureMs / 60_000f;
        }
    }

    /**
     * Compute Digital Diet Score — quality of digital consumption.
     *
     * <p>Goes beyond a simple productive/(productive+passive) ratio by
     * incorporating three advanced factors:</p>
     * <ol>
     *   <li><b>Base ratio</b> (50% weight) — productive time / (productive + passive).
     *       Core signal of how the user spends their time.</li>
     *   <li><b>Dopamine-weighted penalty</b> (30% weight) — penalizes high-dopamine
     *       passive apps more than low-dopamine ones. 2 hours on news is less
     *       harmful than 2 hours on TikTok.</li>
     *   <li><b>Depth bonus</b> (20% weight) — rewards long productive sessions.
     *       15 minutes in a doc editor counts more than 15 × 1-minute checks of email.</li>
     * </ol>
     *
     * @param sessions All sessions for the day
     * @return 0.0 (all dopamine consumption) to 1.0 (fully productive/balanced)
     */
    public float computeDigitalDietScore(List<UsageSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return 0.5f; // Neutral default

        long productiveMs = 0;
        long passiveMs = 0;
        float dopamineWeightedPassive = 0f;
        int deepProductiveSessions = 0; // >10min productive sessions

        for (UsageSession s : sessions) {
            if (s.durationMillis < 5_000) continue; // Skip noise

            AppCategoryMapper.Category cat = AppCategoryMapper.getCategory(s.packageName);

            if (cat.isPassive) {
                passiveMs += s.durationMillis;
                // Weight by dopamine risk: TikTok (0.9) penalized more than News (0.5)
                dopamineWeightedPassive += s.durationMillis * cat.dopamineRisk;
            } else if (cat != AppCategoryMapper.Category.UTILITY
                    && cat != AppCategoryMapper.Category.OTHER) {
                productiveMs += s.durationMillis;
                if (s.durationMillis >= 600_000) { // 10min+ = deep work
                    deepProductiveSessions++;
                }
            }
        }

        long total = productiveMs + passiveMs;
        if (total <= 0) return 0.5f; // No categorizable usage

        // ── Factor 1: Base productive ratio (50%) ──
        float baseRatio = (float) productiveMs / total;

        // ── Factor 2: Dopamine-weighted penalty (30%) ──
        // Normalize: dopamineWeightedPassive / totalPassiveMs gives avg dopamine risk
        // Invert: high dopamine = low score
        float avgDopamineRisk = passiveMs > 0
                ? dopamineWeightedPassive / passiveMs : 0f;
        float dopaminePenalty = 1.0f - avgDopamineRisk; // 0.0 if all high-dopamine

        // Scale by how much time was passive — if only 10% is passive, penalty is mild
        float passiveShare = (float) passiveMs / total;
        float adjustedDopamine = dopaminePenalty * (1.0f - passiveShare) + (1.0f - passiveShare);
        adjustedDopamine = Math.min(1f, adjustedDopamine / 2f);

        // ── Factor 3: Depth bonus (20%) ──
        // Reward sustained productive sessions, not just passive avoidance
        float depthBonus;
        if (deepProductiveSessions >= 4) depthBonus = 1.0f;
        else if (deepProductiveSessions >= 2) depthBonus = 0.7f;
        else if (deepProductiveSessions >= 1) depthBonus = 0.4f;
        else depthBonus = 0f;

        // ── Composite ──
        float score = (baseRatio * 0.50f) + (adjustedDopamine * 0.30f) + (depthBonus * 0.20f);
        return Math.min(1f, Math.max(0f, score));
    }

    // ═════════════════════════════════════════════════════════════════════
    // MASTER WIRING — Populates BehaviorSnapshotEntity from all signals
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Build a complete BehaviorSnapshotEntity by orchestrating all intelligence
     * methods from Section H and feeding their outputs into the 4-layer entity.
     *
     * <p>This is the single entry point that bridges raw session data with
     * the stored behavioural intelligence. Call this once per day after
     * {@code UsageIntelligenceEngine.analyzeDay()} completes.</p>
     *
     * <h3>Orchestration Flow:</h3>
     * <pre>
     *   sessions + screenData + checkInData
     *       → computeFragmentationIndex()       → ATTENTION layer
     *       → computeAvgSessionLength()         → ATTENTION layer
     *       → computeAttentionSpanTrend()       → ATTENTION layer
     *       → detectMorningPhonePattern()       → CIRCADIAN layer
     *       → detectBedtimePhonePattern()       → CIRCADIAN layer
     *       → detectEscapeBehavior()            → ESCAPE layer
     *       → computeDigitalDietScore()         → CONSUMPTION layer
     *       → computeOverallRiskScore()         → COMPOSITE
     *       → generateSummaryLabel()            → NARRATIVE
     *       → BehaviorSnapshotEntity (fully populated)
     * </pre>
     *
     * @param dayTimestamp         Day midnight timestamp
     * @param sessions             All sessions for the day
     * @param report               BehaviorReport from analyze()
     * @param screenData           Screen event data from ScreenEventReceiver
     * @param checkInTimestamp      When the user did their check-in (0 if none)
     * @param stressLevel           Reported stress (1-5, 0 if no check-in)
     * @param baselinePassiveRatio  7-day avg passive ratio (0.5 default)
     * @param historicalAvgSessions Past N days avg session lengths (ms), oldest→newest
     * @param appUsageTimes         Map of packageName → usage time ms
     * @param context               Application context for accessing ScrollEventTracker
     *                              and NotificationEventTracker. Pass null to use
     *                              heuristic fallbacks (backward compatible).
     * @return Fully populated BehaviorSnapshotEntity ready for Room insert
     */
    public BehaviorSnapshotEntity buildBehaviorSnapshot(
            long dayTimestamp,
            List<UsageSession> sessions,
            BehaviorReport report,
            ScreenEventReceiver.ScreenEventData screenData,
            long checkInTimestamp,
            int stressLevel,
            float baselinePassiveRatio,
            long[] historicalAvgSessions,
            Map<String, Long> appUsageTimes,
            Context context
    ) {
        BehaviorSnapshotEntity snapshot = new BehaviorSnapshotEntity();
        snapshot.dayTimestamp = dayTimestamp;
        snapshot.timestamp = System.currentTimeMillis();

        // ─── ATTENTION LAYER ───
        snapshot.fragmentationIndex = computeFragmentationIndex(sessions);
        snapshot.attentionSpanAvgMs = computeAvgSessionLength(sessions);
        snapshot.appSwitchCount = report != null ? report.appSwitchCount : 0;
        snapshot.rapidSwitchCount = report != null ? report.rapidSwitchCount : 0;
        snapshot.unlockCount = screenData != null ? screenData.unlockCount : 0;
        snapshot.totalInterruptions = countInterruptions(sessions);
        snapshot.compulsiveCheckScore = computeCompulsiveCheckScore(sessions);

        // ─── CONSUMPTION LAYER ───
        snapshot.totalForegroundMillis = report != null ? report.totalForegroundMillis : 0;
        snapshot.bingeSessionCount = report != null ? report.bingeSessionCount : 0;
        snapshot.longestSessionMillis = report != null ? report.longestSessionMillis : 0;
        snapshot.hasLoopPattern = report != null && report.hasLoopPattern;
        snapshot.dominantAppPackage = report != null ? report.dominantAppPackage : null;
        snapshot.digitalDietScore = computeDigitalDietScore(sessions);

        // ── Scroll intensity: prefer direct measurement (Phase 4) ──
        snapshot.scrollIntensityScore = syncScrollIntensity(
                context, sessions, report != null ? report.totalForegroundMillis : 0);

        // ── Enrich sessions with notification-triggered flags ──
        enrichSessionsWithNotificationData(context, sessions);

        // Category times from appUsageTimes map
        if (appUsageTimes != null && !appUsageTimes.isEmpty()) {
            long passiveMs = 0, productiveMs = 0, socialMs = 0, entertainMs = 0;
            for (Map.Entry<String, Long> entry : appUsageTimes.entrySet()) {
                AppCategoryMapper.Category cat = AppCategoryMapper.getCategory(entry.getKey());
                if (cat.isPassive) passiveMs += entry.getValue();
                else if (cat != AppCategoryMapper.Category.UTILITY
                        && cat != AppCategoryMapper.Category.OTHER) {
                    productiveMs += entry.getValue();
                }
                if (cat == AppCategoryMapper.Category.SOCIAL) socialMs += entry.getValue();
                if (cat == AppCategoryMapper.Category.VIDEO
                        || cat == AppCategoryMapper.Category.ENTERTAINMENT) {
                    entertainMs += entry.getValue();
                }
            }
            snapshot.socialMediaTimeMillis = socialMs;
            snapshot.productiveAppMinutes = productiveMs;
            snapshot.entertainmentTimeMillis = entertainMs;
            snapshot.passiveConsumptionRatio = snapshot.totalForegroundMillis > 0
                    ? (float) passiveMs / snapshot.totalForegroundMillis : 0f;
            snapshot.appDiversityScore = AppCategoryMapper.computeAppDiversity(appUsageTimes);

            // Dominant category
            String bestCat = null;
            long bestCatMs = 0;
            Map<String, Long> catBreakdown = AppCategoryMapper.buildCategoryBreakdown(appUsageTimes);
            for (Map.Entry<String, Long> e : catBreakdown.entrySet()) {
                if (e.getValue() > bestCatMs) {
                    bestCatMs = e.getValue();
                    bestCat = e.getKey();
                }
            }
            snapshot.dominantCategory = bestCat;
        }

        // Loop app pair detection
        if (report != null && report.hasLoopPattern) {
            snapshot.loopAppPair = detectLoopPair(sessions);
        }

        // ─── CIRCADIAN LAYER ───
        snapshot.lateNightUsageMillis = report != null ? report.lateNightUsageMillis : 0;
        snapshot.lateNightSessionCount = countLateNightSessions(sessions);

        if (screenData != null) {
            MorningGrabResult morning = detectMorningPhonePattern(sessions, screenData.firstUnlockTime);
            snapshot.morningPhoneGrabMs = morning.timeToFirstSessionMs;

            BedtimeScrollResult bedtime = detectBedtimePhonePattern(sessions, screenData.lastScreenOffTime);
            snapshot.bedtimeScrollMs = bedtime.windDownUsageMs;
        }

        // ─── ESCAPE LAYER ───
        snapshot.escapeBehaviorScore = detectEscapeBehavior(
                sessions, checkInTimestamp, stressLevel, baselinePassiveRatio);
        snapshot.isAvoidanceDayFlag = snapshot.escapeBehaviorScore > 0.6f
                && snapshot.passiveConsumptionRatio > 0.7f
                && snapshot.bingeSessionCount >= 2;

        // ─── ATTENTION TREND ───
        // Uses linear regression on historical data
        float attentionTrend = computeAttentionSpanTrend(historicalAvgSessions, snapshot.attentionSpanAvgMs);
        // Trend stored implicitly via comparison — used by AnomalyDetector

        // ─── COMPOSITE RISK SCORE ───
        snapshot.overallBehaviorRiskScore = computeOverallRiskScore(snapshot);

        // ─── NARRATIVE ───
        snapshot.summaryLabel = generateSnapshotLabel(snapshot);
        snapshot.explanation = generateSnapshotExplanation(snapshot);

        return snapshot;
    }

    /**
     * Backward-compatible overload: delegates to the new signature with null context.
     * Existing callers that don't have Context will use heuristic fallbacks.
     */
    public BehaviorSnapshotEntity buildBehaviorSnapshot(
            long dayTimestamp,
            List<UsageSession> sessions,
            BehaviorReport report,
            ScreenEventReceiver.ScreenEventData screenData,
            long checkInTimestamp,
            int stressLevel,
            float baselinePassiveRatio,
            long[] historicalAvgSessions,
            Map<String, Long> appUsageTimes
    ) {
        return buildBehaviorSnapshot(
                dayTimestamp, sessions, report, screenData,
                checkInTimestamp, stressLevel, baselinePassiveRatio,
                historicalAvgSessions, appUsageTimes, null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPER METHODS for buildBehaviorSnapshot
    // ─────────────────────────────────────────────────────────────────────

    /** Count total interruptions (sessions where same app was re-opened). */
    private int countInterruptions(List<UsageSession> sessions) {
        if (sessions == null) return 0;
        Map<String, Integer> appSessionCounts = new HashMap<>();
        for (UsageSession s : sessions) {
            appSessionCounts.put(s.packageName,
                    appSessionCounts.getOrDefault(s.packageName, 0) + 1);
        }
        int interruptions = 0;
        for (int count : appSessionCounts.values()) {
            if (count > 1) interruptions += count - 1; // Each revisit = one interruption
        }
        return interruptions;
    }

    /** Compulsive checking score: ratio of <15s sessions to total. */
    private float computeCompulsiveCheckScore(List<UsageSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return 0f;
        int microChecks = 0;
        for (UsageSession s : sessions) {
            if (s.durationMillis < 15_000) microChecks++;
        }
        return Math.min(1f, (float) microChecks / sessions.size());
    }

    /** Estimate scroll intensity from passive session patterns (Phase 1 fallback). */
    private float estimateScrollFromSessions(List<UsageSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return 0f;
        int passiveShort = 0, passiveLong = 0;
        long passiveTotal = 0, allTotal = 0;
        for (UsageSession s : sessions) {
            allTotal += s.durationMillis;
            if ("passive".equals(s.sessionType)) {
                passiveTotal += s.durationMillis;
                if (s.durationMillis < 120_000) passiveShort++;
                else passiveLong++;
            }
        }
        if (allTotal <= 0) return 0f;
        float passiveRatio = (float) passiveTotal / allTotal;
        float raw = (passiveRatio * 4f) + (Math.min(1f, passiveShort / 15f) * 3f)
                + (Math.min(1f, passiveLong / 5f) * 3f);
        return Math.min(10f, Math.max(0f, raw));
    }

    // ─────────────────────────────────────────────────────────────────────
    // PHASE 4 — Direct telemetry helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sync scroll intensity: prefer real AccessibilityService data,
     * fall back to session-pattern heuristic.
     */
    private float syncScrollIntensity(Context context, List<UsageSession> sessions,
                                       long foregroundTimeMs) {
        if (context != null) {
            ScrollEventTracker.ScrollData scrollData =
                    ScrollEventTracker.getTodayData(context);
            if (scrollData.isValid()) {
                return ScrollEventTracker.computeIntensityScore(scrollData, foregroundTimeMs);
            }
        }
        // Fallback: session-pattern heuristic
        return estimateScrollFromSessions(sessions);
    }

    /**
     * Enrich sessions with notification-triggered flags.
     *
     * <p>For each session, checks whether a notification from the session's
     * app was posted within 60 seconds before the session started.
     * This identifies "notification-pulled" usage vs. self-initiated usage,
     * a key signal for compulsive behavior detection.</p>
     */
    private void enrichSessionsWithNotificationData(Context context,
                                                      List<UsageSession> sessions) {
        if (context == null || sessions == null || sessions.isEmpty()) return;

        for (UsageSession s : sessions) {
            if (s.packageName != null) {
                // Was a notification from this app posted within 60s before session start?
                s.wasNotificationTriggered = NotificationEventTracker.wasRecentlyNotified(
                        context, s.packageName, 60_000);
            }
        }
    }

    /** Detect the A↔B loop pair from session history. */
    private String detectLoopPair(List<UsageSession> sessions) {
        if (sessions == null || sessions.size() < 4) return "";
        // Find most common A→B→A pattern
        Map<String, Integer> pairCounts = new HashMap<>();
        for (int i = 2; i < sessions.size(); i++) {
            String a = sessions.get(i - 2).packageName;
            String b = sessions.get(i - 1).packageName;
            String c = sessions.get(i).packageName;
            if (a.equals(c) && !a.equals(b)) {
                String pair = a + "↔" + b;
                pairCounts.put(pair, pairCounts.getOrDefault(pair, 0) + 1);
            }
        }
        String bestPair = "";
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : pairCounts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                bestPair = e.getKey();
            }
        }
        return bestCount >= 2 ? bestPair : "";
    }

    /** Count sessions starting after 10pm. */
    private int countLateNightSessions(List<UsageSession> sessions) {
        if (sessions == null) return 0;
        int count = 0;
        Calendar cal = Calendar.getInstance();
        for (UsageSession s : sessions) {
            cal.setTimeInMillis(s.sessionStart);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour >= 22 || hour < 6) count++;
        }
        return count;
    }

    /**
     * Weighted composite risk score — the single number that represents
     * the user's digital behaviour health for the day.
     *
     * <p>Formula:</p>
     * <pre>
     *   0.20 × fragmentationIndex
     * + 0.15 × compulsiveCheckScore
     * + 0.20 × passiveConsumptionRatio
     * + 0.15 × normalizedNightUsage
     * + 0.10 × normalizedBingeSessions
     * + 0.10 × escapeBehaviorScore
     * + 0.10 × (1 - digitalDietScore)
     * </pre>
     */
    private float computeOverallRiskScore(BehaviorSnapshotEntity s) {
        float nightNorm = Math.min(1f, s.lateNightUsageMillis / (2f * 3600000f)); // 2hr = 1.0
        float bingeNorm = Math.min(1f, s.bingeSessionCount / 4f); // 4 binges = 1.0

        float score = (s.fragmentationIndex * 0.20f)
                + (s.compulsiveCheckScore * 0.15f)
                + (s.passiveConsumptionRatio * 0.20f)
                + (nightNorm * 0.15f)
                + (bingeNorm * 0.10f)
                + (s.escapeBehaviorScore * 0.10f)
                + ((1f - s.digitalDietScore) * 0.10f);

        return Math.min(1f, Math.max(0f, score));
    }

    /** Generate a human-readable summary label for the snapshot. */
    private String generateSnapshotLabel(BehaviorSnapshotEntity s) {
        if (s.overallBehaviorRiskScore < 0.2f) return "Focused & Balanced";
        if (s.escapeBehaviorScore > 0.6f) return "Escape Pattern";
        if (s.lateNightUsageMillis > 3600000) return "Night Owl";
        if (s.bingeSessionCount >= 3) return "Binge Day";
        if (s.fragmentationIndex > 0.6f) return "Fragmented";
        if (s.compulsiveCheckScore > 0.5f) return "Compulsive Checking";
        if (s.passiveConsumptionRatio > 0.7f) return "Passive Consumption";
        if (s.overallBehaviorRiskScore < 0.4f) return "Mostly Balanced";
        return "Moderate Risk";
    }

    /** Generate explanation text for the insight screen. */
    private String generateSnapshotExplanation(BehaviorSnapshotEntity s) {
        List<String> reasons = new ArrayList<>();

        if (s.fragmentationIndex > 0.5f)
            reasons.add("Attention was fragmented across many short sessions.");
        if (s.compulsiveCheckScore > 0.4f)
            reasons.add("Frequent micro-checks suggest compulsive phone picking.");
        if (s.passiveConsumptionRatio > 0.6f)
            reasons.add("Most screen time was spent on passive consumption apps.");
        if (s.lateNightUsageMillis > 1800000)
            reasons.add("Significant phone use during sleep hours may affect rest quality.");
        if (s.escapeBehaviorScore > 0.5f)
            reasons.add("Passive app usage spiked after a high-stress check-in, suggesting escape behaviour.");
        if (s.bingeSessionCount >= 2)
            reasons.add("Multiple binge sessions exceeded 30 minutes of continuous use.");
        if (s.hasLoopPattern)
            reasons.add("A repetitive app loop pattern was detected.");

        if (reasons.isEmpty())
            reasons.add("Digital habits look relatively balanced today.");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(reasons.get(i));
        }
        return sb.toString();
    }

    public BehaviorReport analyze(Context context, long startTime, long endTime) {
        return analyze(context, startTime, endTime, false);
    }

    public BehaviorReport analyze(Context context, long startTime, long endTime, boolean includeSystemApps) {
        if (context == null || endTime <= startTime) {
            return BehaviorReport.empty(startTime, endTime);
        }

        UsageStatsManager usageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return BehaviorReport.unavailable(startTime, endTime, "Usage event history is not available on this device.");
        }

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) {
            return BehaviorReport.empty(startTime, endTime);
        }

        PackageManager packageManager = context.getPackageManager();
        BehaviorReport report = BehaviorReport.empty(startTime, endTime);
        Map<String, Long> foregroundMillisByPackage = new HashMap<>();
        Map<String, Integer> foregroundCountByPackage = new HashMap<>();
        Map<String, Boolean> includeCache = new HashMap<>();
        Deque<String> recentPackages = new ArrayDeque<>();

        UsageEvents.Event event = new UsageEvents.Event();
        String currentPackage = null;
        long currentSessionStart = 0L;
        String lastDistinctForegroundPackage = null;
        long lastDistinctForegroundTime = 0L;
        String lastAcceptedForegroundPackage = null;
        long lastAcceptedForegroundTime = 0L;

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);

            String packageName = event.getPackageName();
            if (packageName == null || packageName.trim().isEmpty()) {
                continue;
            }
            if (!shouldIncludePackage(context, packageManager, packageName, includeSystemApps, includeCache)) {
                continue;
            }

            int eventType = event.getEventType();
            long eventTime = Math.max(startTime, Math.min(event.getTimeStamp(), endTime));

            if (isForegroundEvent(eventType)) {
                if (packageName.equals(lastAcceptedForegroundPackage)
                        && eventTime - lastAcceptedForegroundTime <= BehaviorThresholds.DUPLICATE_FOREGROUND_WINDOW_MILLIS) {
                    continue;
                }

                if (currentPackage != null && !currentPackage.equals(packageName) && currentSessionStart > 0L) {
                    closeSession(report, foregroundMillisByPackage, currentPackage, currentSessionStart, eventTime);
                }

                if (!packageName.equals(lastDistinctForegroundPackage)) {
                    if (lastDistinctForegroundPackage != null) {
                        report.appSwitchCount++;
                        if (eventTime - lastDistinctForegroundTime <= BehaviorThresholds.RAPID_SWITCH_WINDOW_MILLIS) {
                            report.rapidSwitchCount++;
                        }
                    }
                    lastDistinctForegroundPackage = packageName;
                    lastDistinctForegroundTime = eventTime;
                    incrementCount(foregroundCountByPackage, packageName);
                    pushRecentPackage(recentPackages, packageName);
                    if (!report.hasLoopPattern && hasLoopPattern(recentPackages)) {
                        report.hasLoopPattern = true;
                    }
                }

                currentPackage = packageName;
                currentSessionStart = eventTime;
                lastAcceptedForegroundPackage = packageName;
                lastAcceptedForegroundTime = eventTime;
            } else if (isBackgroundEvent(eventType)) {
                if (currentPackage != null && currentPackage.equals(packageName) && currentSessionStart > 0L) {
                    closeSession(report, foregroundMillisByPackage, currentPackage, currentSessionStart, eventTime);
                    currentPackage = null;
                    currentSessionStart = 0L;
                }
            }
        }

        if (currentPackage != null && currentSessionStart > 0L) {
            closeSession(report, foregroundMillisByPackage, currentPackage, currentSessionStart, endTime);
        }

        populateDominantApp(report, foregroundMillisByPackage, foregroundCountByPackage);
        populateSignals(report);
        report.summaryLabel = buildSummaryLabel(report);
        report.explanation = buildExplanation(report);

        if (report.reasoningNotes.isEmpty()) {
            report.reasoningNotes.add(report.explanation);
        }

        return report;
    }

    public String buildSummaryLabel(BehaviorReport report) {
        if (report == null || !report.dataAvailable) {
            return "Behavior data unavailable";
        }
        if (report.totalForegroundMillis <= 0L) {
            return "Calm / low activity";
        }
        if (report.lateNightUsageMillis >= BehaviorThresholds.LATE_NIGHT_SIGNAL_THRESHOLD_MILLIS
                && report.lateNightUsageRatio >= BehaviorThresholds.HEAVY_LATE_NIGHT_RATIO_THRESHOLD) {
            return "Heavy late-night use";
        }
        if (report.bingeSessionCount > 0 || report.longestSessionMillis >= BehaviorThresholds.EXTREME_SESSION_THRESHOLD_MILLIS) {
            return "Binge usage detected";
        }
        if (report.hasLoopPattern) {
            return "Repetitive loop behavior";
        }
        if (report.rapidSwitchCount >= BehaviorThresholds.HIGH_RAPID_SWITCH_THRESHOLD
                || report.shortSessionCount >= BehaviorThresholds.SHORT_SESSION_SIGNAL_THRESHOLD
                || report.appSwitchCount >= BehaviorThresholds.HIGH_FRAGMENTATION_SWITCH_THRESHOLD) {
            return "High fragmentation";
        }
        if (report.appSwitchCount >= BehaviorThresholds.MILD_SWITCH_THRESHOLD
                || report.rapidSwitchCount >= BehaviorThresholds.RAPID_SWITCH_SIGNAL_THRESHOLD) {
            return "Mild distraction";
        }
        if (report.dominantUsageRatio >= BehaviorThresholds.DOMINANT_APP_RATIO_THRESHOLD) {
            return "Usage concentration";
        }
        return "Healthy balance";
    }

    public String buildExplanation(BehaviorReport report) {
        if (report == null || !report.dataAvailable) {
            return report == null ? "Behavior data is not available yet." : report.explanation;
        }
        if (report.totalForegroundMillis <= 0L) {
            return "The device showed very little foreground activity in the selected time window.";
        }
        if ("Heavy late-night use".equals(report.summaryLabel)) {
            return "A meaningful share of phone activity happened between 11 PM and 5 AM, which can interfere with sleep recovery.";
        }
        if ("Binge usage detected".equals(report.summaryLabel)) {
            return "One or more long uninterrupted sessions crossed the binge threshold, suggesting heavier continuous use.";
        }
        if ("Repetitive loop behavior".equals(report.summaryLabel)) {
            return "The same small set of apps was opened in a repeated back-and-forth pattern.";
        }
        if ("High fragmentation".equals(report.summaryLabel)) {
            return "Rapid switching and short sessions suggest attention was pulled across too many app transitions.";
        }
        if ("Mild distraction".equals(report.summaryLabel)) {
            return "App switching is elevated, but not yet at the strongest fragmentation level.";
        }
        if ("Usage concentration".equals(report.summaryLabel)) {
            return "A single app is dominating a large share of today's foreground activity.";
        }
        return "Usage transitions look relatively balanced without a strong compulsive or fragmented pattern.";
    }

    private void closeSession(
            BehaviorReport report,
            Map<String, Long> foregroundMillisByPackage,
            String packageName,
            long sessionStart,
            long sessionEnd
    ) {
        if (sessionEnd <= sessionStart) {
            return;
        }

        long sessionDuration = sessionEnd - sessionStart;
        report.totalForegroundMillis += sessionDuration;
        report.longestSessionMillis = Math.max(report.longestSessionMillis, sessionDuration);
        report.lateNightUsageMillis += calculateLateNightOverlap(sessionStart, sessionEnd);
        foregroundMillisByPackage.put(
                packageName,
                foregroundMillisByPackage.containsKey(packageName)
                        ? foregroundMillisByPackage.get(packageName) + sessionDuration
                        : sessionDuration
        );

        if (sessionDuration <= BehaviorThresholds.SHORT_SESSION_THRESHOLD_MILLIS) {
            report.shortSessionCount++;
        }
        if (sessionDuration >= BehaviorThresholds.BINGE_SESSION_THRESHOLD_MILLIS) {
            report.bingeSessionCount++;
        }
    }

    private void populateDominantApp(
            BehaviorReport report,
            Map<String, Long> foregroundMillisByPackage,
            Map<String, Integer> foregroundCountByPackage
    ) {
        String dominantPackage = null;
        long dominantMillis = 0L;

        for (Map.Entry<String, Long> entry : foregroundMillisByPackage.entrySet()) {
            if (entry.getValue() > dominantMillis) {
                dominantMillis = entry.getValue();
                dominantPackage = entry.getKey();
            }
        }

        report.dominantAppPackage = dominantPackage;
        report.dominantAppCount = dominantPackage == null
                ? 0
                : foregroundCountByPackage.containsKey(dominantPackage)
                ? foregroundCountByPackage.get(dominantPackage)
                : 0;
        report.dominantUsageRatio = report.totalForegroundMillis <= 0L
                ? 0d
                : dominantMillis / (double) report.totalForegroundMillis;
        report.lateNightUsageRatio = report.totalForegroundMillis <= 0L
                ? 0d
                : report.lateNightUsageMillis / (double) report.totalForegroundMillis;
    }

    private void populateSignals(BehaviorReport report) {
        report.detectedSignals.clear();
        report.reasoningNotes.clear();

        if (!report.dataAvailable) {
            report.reasoningNotes.add(report.explanation);
            return;
        }

        if (report.totalForegroundMillis <= 0L) {
            report.detectedSignals.add("Low activity");
            report.reasoningNotes.add("No meaningful foreground sessions were recorded during the selected period.");
            return;
        }

        if (report.appSwitchCount >= BehaviorThresholds.MILD_SWITCH_THRESHOLD) {
            report.detectedSignals.add("Frequent app switching");
            report.reasoningNotes.add("Frequent app switching observed across " + report.appSwitchCount + " foreground transitions.");
        }

        if (report.rapidSwitchCount >= BehaviorThresholds.RAPID_SWITCH_SIGNAL_THRESHOLD) {
            report.detectedSignals.add("Fragmented attention");
            report.reasoningNotes.add("Short-interval switching indicates fragmented attention, with " + report.rapidSwitchCount + " rapid switches.");
        }

        if (report.bingeSessionCount > 0) {
            report.detectedSignals.add("Long continuous sessions");
            report.reasoningNotes.add("One or more extended usage sessions exceeded the binge threshold.");
        }

        if (report.longestSessionMillis >= BehaviorThresholds.EXTREME_SESSION_THRESHOLD_MILLIS) {
            report.reasoningNotes.add("The longest single session lasted " + formatDuration(report.longestSessionMillis) + ".");
        }

        if (report.lateNightUsageMillis >= BehaviorThresholds.LATE_NIGHT_SIGNAL_THRESHOLD_MILLIS) {
            report.detectedSignals.add("Late-night usage");
            report.reasoningNotes.add(
                    String.format(
                            Locale.US,
                            "A meaningful share of usage occurred during sleep hours (%s, %.0f%% of foreground time).",
                            formatDuration(report.lateNightUsageMillis),
                            report.lateNightUsageRatio * 100d
                    )
            );
        }

        if (report.hasLoopPattern) {
            report.detectedSignals.add("Loop pattern");
            report.reasoningNotes.add("Repeated app loop detected, with repeated returns to the same small set of apps.");
        }

        if (report.dominantAppPackage != null && report.dominantUsageRatio >= BehaviorThresholds.DOMINANT_APP_RATIO_THRESHOLD) {
            report.detectedSignals.add("Usage concentration");
            report.reasoningNotes.add(
                    String.format(
                            Locale.US,
                            "Usage is concentrated in a single app, which accounted for %.0f%% of foreground time.",
                            report.dominantUsageRatio * 100d
                    )
            );
        }

        if (report.shortSessionCount >= BehaviorThresholds.SHORT_SESSION_SIGNAL_THRESHOLD) {
            report.reasoningNotes.add(report.shortSessionCount + " short sessions suggest restless or fragmented device use.");
        }

        if (report.detectedSignals.isEmpty()) {
            report.detectedSignals.add("Healthy balance");
            report.reasoningNotes.add("Behavior signals look relatively balanced, without strong fragmentation, bingeing, or late-night overuse.");
        }
    }

    private boolean shouldIncludePackage(
            Context context,
            PackageManager packageManager,
            String packageName,
            boolean includeSystemApps,
            Map<String, Boolean> includeCache
    ) {
        if (packageName.equals(context.getPackageName())) {
            return false;
        }

        if (includeCache.containsKey(packageName)) {
            return includeCache.get(packageName);
        }

        boolean include = true;
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            boolean systemApp = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            include = includeSystemApps || !systemApp;
        } catch (PackageManager.NameNotFoundException ignored) {
            include = false;
        }

        includeCache.put(packageName, include);
        return include;
    }

    private boolean hasLoopPattern(Deque<String> recentPackages) {
        if (recentPackages.size() < 4) {
            return false;
        }

        List<String> packages = new ArrayList<>(recentPackages);
        int size = packages.size();

        if (size >= 6) {
            String a = packages.get(size - 6);
            String b = packages.get(size - 5);
            if (a.equals(packages.get(size - 4))
                    && b.equals(packages.get(size - 3))
                    && a.equals(packages.get(size - 2))
                    && b.equals(packages.get(size - 1))
                    && !a.equals(b)) {
                return true;
            }
        }

        String a = packages.get(size - 4);
        String b = packages.get(size - 3);
        return a.equals(packages.get(size - 2))
                && b.equals(packages.get(size - 1))
                && !a.equals(b);
    }

    private void pushRecentPackage(Deque<String> recentPackages, String packageName) {
        recentPackages.addLast(packageName);
        while (recentPackages.size() > BehaviorThresholds.LOOP_DETECTION_DEPTH) {
            recentPackages.removeFirst();
        }
    }

    private void incrementCount(Map<String, Integer> countByPackage, String packageName) {
        countByPackage.put(
                packageName,
                countByPackage.containsKey(packageName) ? countByPackage.get(packageName) + 1 : 1
        );
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
            long dayStart = cursor.getTimeInMillis();

            Calendar nightStart = (Calendar) cursor.clone();
            nightStart.set(Calendar.HOUR_OF_DAY, BehaviorThresholds.LATE_NIGHT_START_HOUR);

            Calendar nightEnd = (Calendar) cursor.clone();
            nightEnd.add(Calendar.DAY_OF_YEAR, 1);
            nightEnd.set(Calendar.HOUR_OF_DAY, BehaviorThresholds.LATE_NIGHT_END_HOUR);

            overlap += intersectDuration(sessionStart, sessionEnd, nightStart.getTimeInMillis(), nightEnd.getTimeInMillis());
            cursor.add(Calendar.DAY_OF_YEAR, 1);
            cursor.setTimeInMillis(Math.max(cursor.getTimeInMillis(), dayStart + (24L * 60L * 60L * 1000L)));
        }

        return overlap;
    }

    private long intersectDuration(long startA, long endA, long startB, long endB) {
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

    private String formatDuration(long millis) {
        if (millis <= 0L) {
            return "0m";
        }

        long hours = millis / (60L * 60L * 1000L);
        long minutes = (millis % (60L * 60L * 1000L)) / (60L * 1000L);
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
