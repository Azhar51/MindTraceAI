package com.mindtrace.ai.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.service.WorkerErrorHandler;

import com.mindtrace.ai.MindTraceApp;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.FeatureVector;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.ui.MainActivity;

import java.util.List;

/**
 * Weekly Assessment Worker — aggregates 7 days of classification data
 * and computes cross-week feature vector trend regression.
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Aggregate daily feature vectors into a weekly composite</li>
 *   <li>Compute cross-week trend regression on overall risk</li>
 *   <li>Detect week-over-week improvement or deterioration</li>
 *   <li>Generate a weekly trajectory assessment notification</li>
 *   <li>Persist weekly assessment summary to SharedPreferences</li>
 * </ul>
 *
 * <p>Runs every 7 days, offset from WeeklyReportWorker. While
 * WeeklyReportWorker handles fulfilment/task metrics, this worker
 * focuses on the 34-dimensional feature vector trends and risk
 * trajectory analytics.</p>
 *
 * @see com.mindtrace.ai.ai.TemporalFeatureExtractor
 * @see com.mindtrace.ai.ai.FeatureVector
 */
public class WeeklyAssessmentWorker extends Worker {

    private static final String TAG = "WeeklyAssessmentWorker";
    private static final int NOTIFICATION_ID = 4002;
    private static final long WEEK_MS  = 7L * 24 * 60 * 60 * 1000;
    private static final String PREFS  = "mindtrace_weekly_assessment";

    public WeeklyAssessmentWorker(@NonNull Context context,
                                   @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Running weekly assessment...");

        try {
            Context ctx = getApplicationContext();
            AppDatabase db = AppDatabase.getInstance(ctx);
            RiskClassificationDao dao = db.riskClassificationDao();

            long now = System.currentTimeMillis();
            long weekAgo  = now - WEEK_MS;
            long twoWeeksAgo = now - 2 * WEEK_MS;

            // ── 1. Current week classifications ──
            List<RiskClassification> thisWeek = dao.getHistorySince(weekAgo);
            if (thisWeek == null || thisWeek.isEmpty()) {
                Log.d(TAG, "No classifications this week, skipping assessment");
                return Result.success();
            }

            // ── 2. Previous week classifications (for comparison) ──
            List<RiskClassification> allRecent = dao.getHistorySince(twoWeeksAgo);
            List<RiskClassification> prevWeek = new java.util.ArrayList<>();
            if (allRecent != null) {
                for (RiskClassification rc : allRecent) {
                    if (rc.dayTimestamp < weekAgo) {
                        prevWeek.add(rc);
                    }
                }
            }

            // ── 3. Compute weekly aggregates ──
            WeeklyAggregate currentAgg = computeAggregate(thisWeek);
            WeeklyAggregate previousAgg = prevWeek.isEmpty() ? null : computeAggregate(prevWeek);

            // ── 4. Compute cross-week trend ──
            float trendDelta = 0f;
            String trajectory = "stable";
            if (previousAgg != null) {
                trendDelta = currentAgg.avgOverallRisk - previousAgg.avgOverallRisk;
                if (trendDelta > 0.05f) {
                    trajectory = "deteriorating";
                } else if (trendDelta < -0.05f) {
                    trajectory = "improving";
                }
            }

            // ── 5. Feature vector domain analysis ──
            float[] avgFeatures = computeAverageFeatureVector(thisWeek);
            String dominantDomain = identifyDominantRiskDomain(currentAgg);
            String topFeature = identifyTopFeature(avgFeatures);

            // ── 6. Persist assessment ──
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit()
                    .putFloat("avg_overall_risk", currentAgg.avgOverallRisk)
                    .putFloat("avg_digital_addiction", currentAgg.avgDigitalAddiction)
                    .putFloat("avg_stress_anxiety", currentAgg.avgStressAnxiety)
                    .putFloat("avg_depression_risk", currentAgg.avgDepressionRisk)
                    .putFloat("avg_social_isolation", currentAgg.avgSocialIsolation)
                    .putFloat("avg_sleep_disruption", currentAgg.avgSleepDisruption)
                    .putFloat("avg_low_fulfilment", currentAgg.avgLowFulfilment)
                    .putFloat("avg_confidence", currentAgg.avgConfidence)
                    .putFloat("trend_delta", trendDelta)
                    .putString("trajectory", trajectory)
                    .putString("dominant_domain", dominantDomain)
                    .putString("top_feature", topFeature)
                    .putInt("days_with_data", currentAgg.daysWithData)
                    .putInt("crisis_count", currentAgg.crisisCount)
                    .putLong("last_assessment_timestamp", now)
                    .apply();

            // ── 7. Send notification ──
            sendAssessmentNotification(ctx, currentAgg, trajectory, trendDelta, dominantDomain);

            Log.i(TAG, String.format("Weekly assessment: risk=%.2f, trend=%s (Δ=%.3f), " +
                            "domain=%s, days=%d, crises=%d",
                    currentAgg.avgOverallRisk, trajectory, trendDelta,
                    dominantDomain, currentAgg.daysWithData, currentAgg.crisisCount));

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Weekly assessment failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // AGGREGATION
    // ═══════════════════════════════════════════════════════════════════

    /** Compute weekly aggregate statistics from a list of classifications. */
    private WeeklyAggregate computeAggregate(List<RiskClassification> classifications) {
        WeeklyAggregate agg = new WeeklyAggregate();
        agg.daysWithData = classifications.size();

        float sumOverall = 0, sumDigital = 0, sumStress = 0, sumDepression = 0;
        float sumIsolation = 0, sumSleep = 0, sumFulfilment = 0, sumConfidence = 0;
        int crises = 0;

        for (RiskClassification rc : classifications) {
            sumOverall    += rc.overallRiskScore;
            sumDigital    += rc.digitalAddictionScore;
            sumStress     += rc.stressAnxietyScore;
            sumDepression += rc.depressionRiskScore;
            sumIsolation  += rc.socialIsolationScore;
            sumSleep      += rc.sleepDisruptionScore;
            sumFulfilment += rc.lowFulfilmentScore;
            sumConfidence += rc.confidence;
            if (rc.crisisFlag) crises++;
        }

        int n = classifications.size();
        agg.avgOverallRisk     = sumOverall / n;
        agg.avgDigitalAddiction = sumDigital / n;
        agg.avgStressAnxiety   = sumStress / n;
        agg.avgDepressionRisk  = sumDepression / n;
        agg.avgSocialIsolation = sumIsolation / n;
        agg.avgSleepDisruption = sumSleep / n;
        agg.avgLowFulfilment   = sumFulfilment / n;
        agg.avgConfidence      = sumConfidence / n;
        agg.crisisCount        = crises;

        return agg;
    }

    /** Compute average feature vector across all classifications that have JSON. */
    private float[] computeAverageFeatureVector(List<RiskClassification> classifications) {
        float[] sum = new float[FeatureVector.TOTAL_FEATURES];
        int count = 0;

        for (RiskClassification rc : classifications) {
            if (rc.featureVectorJson == null || rc.featureVectorJson.isEmpty()) continue;
            try {
                FeatureVector fv = FeatureVector.fromJson(rc.featureVectorJson);
                float[] arr = fv.toArray();
                for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
                    sum[i] += arr[i];
                }
                count++;
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse feature vector JSON", e);
            }
        }

        if (count > 0) {
            for (int i = 0; i < FeatureVector.TOTAL_FEATURES; i++) {
                sum[i] /= count;
            }
        }
        return sum;
    }

    /** Identify which risk domain is highest this week. */
    private String identifyDominantRiskDomain(WeeklyAggregate agg) {
        float max = agg.avgDigitalAddiction;
        String domain = "Digital Addiction";

        if (agg.avgStressAnxiety > max)   { max = agg.avgStressAnxiety;   domain = "Stress & Anxiety"; }
        if (agg.avgDepressionRisk > max)  { max = agg.avgDepressionRisk;  domain = "Depression Risk"; }
        if (agg.avgSocialIsolation > max) { max = agg.avgSocialIsolation; domain = "Social Isolation"; }
        if (agg.avgSleepDisruption > max) { max = agg.avgSleepDisruption; domain = "Sleep Disruption"; }
        if (agg.avgLowFulfilment > max)   { domain = "Low Fulfilment"; }

        return domain;
    }

    /** Identify the highest-risk individual feature from the average vector. */
    private String identifyTopFeature(float[] avgFeatures) {
        int maxIdx = 0;
        for (int i = 1; i < avgFeatures.length; i++) {
            if (avgFeatures[i] > avgFeatures[maxIdx]) maxIdx = i;
        }
        if (maxIdx < FeatureVector.FEATURE_NAMES.length) {
            return FeatureVector.FEATURE_NAMES[maxIdx];
        }
        return "unknown";
    }

    // ═══════════════════════════════════════════════════════════════════
    // NOTIFICATION
    // ═══════════════════════════════════════════════════════════════════

    private void sendAssessmentNotification(Context ctx, WeeklyAggregate agg,
                                             String trajectory, float delta,
                                             String dominantDomain) {
        // Choose title based on trajectory
        String title;
        String emoji;
        switch (trajectory) {
            case "improving":
                emoji = "📈";
                title = emoji + " Your trajectory is improving this week!";
                break;
            case "deteriorating":
                emoji = "📉";
                title = emoji + " Your weekly assessment needs attention";
                break;
            default:
                emoji = "📊";
                title = emoji + " Your weekly trajectory assessment is ready";
                break;
        }

        // Build summary
        StringBuilder body = new StringBuilder();
        int riskPercent = Math.round(agg.avgOverallRisk * 100);
        body.append("Overall risk: ").append(riskPercent).append("% ");

        if (delta != 0 && !"stable".equals(trajectory)) {
            int deltaPercent = Math.abs(Math.round(delta * 100));
            body.append(delta > 0 ? "↑" : "↓").append(deltaPercent).append("% vs last week");
        } else {
            body.append("(stable)");
        }

        body.append("\nPrimary concern: ").append(dominantDomain);
        body.append("\nDays tracked: ").append(agg.daysWithData).append("/7");

        if (agg.crisisCount > 0) {
            body.append("\n⚠️ ").append(agg.crisisCount).append(" crisis event")
                    .append(agg.crisisCount > 1 ? "s" : "").append(" this week");
        }

        body.append("\nConfidence: ").append(Math.round(agg.avgConfidence * 100)).append("%");

        // Open insights on tap
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_START_DESTINATION, MainActivity.DEST_INSIGHTS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, NOTIFICATION_ID,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(ctx, MindTraceApp.CHANNEL_WEEKLY_REPORT)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText("Risk: " + riskPercent + "% • " + dominantDomain)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString()))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .addAction(0, "📈 View Trajectory", pi);

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission not granted", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    /** Weekly aggregate statistics. */
    static class WeeklyAggregate {
        float avgOverallRisk;
        float avgDigitalAddiction;
        float avgStressAnxiety;
        float avgDepressionRisk;
        float avgSocialIsolation;
        float avgSleepDisruption;
        float avgLowFulfilment;
        float avgConfidence;
        int daysWithData;
        int crisisCount;
    }
}
