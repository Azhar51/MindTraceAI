package com.mindtrace.ai.viewmodel.usecase;

import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.ui.model.HomeScreenState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extracted from DashboardViewModel — builds the tomorrow-forecast card.
 *
 * <p>Computes a predicted risk index using momentum-based extrapolation
 * from 7-day classification history, adjusted by behavioral signals
 * (screen time deviation, late-night usage, stress, sleep debt, exercise).</p>
 */
public class BuildForecastUseCase {

    /**
     * Build the forecast card for tomorrow's predicted risk.
     *
     * @param classifications   7-day classification history
     * @param behaviorReport    today's behavior report
     * @param todayScreenTime   screen time in millis
     * @param baseline          user baseline (for deviation calculation)
     * @param latestResponse    latest questionnaire response
     * @param riskIndex         today's computed risk index
     * @return ForecastCard for the HomeScreenState
     */
    public HomeScreenState.ForecastCard execute(
            List<RiskClassification> classifications,
            BehaviorReport behaviorReport,
            long todayScreenTime,
            UserBaseline baseline,
            QuestionnaireResponse latestResponse,
            int riskIndex) {

        List<Float> trend = buildRiskTrend(classifications, riskIndex);
        float todayRisk = trend.isEmpty() ? (float) riskIndex : trend.get(trend.size() - 1);
        float yesterdayRisk = trend.size() >= 2 ? trend.get(trend.size() - 2) : todayRisk;

        // Momentum-based prediction
        float predictedRisk = todayRisk + (todayRisk - yesterdayRisk) * 0.45f;

        // Adjust by screen time deviation
        double deviation = calculateScreenTimeDeviation(todayScreenTime, baseline);
        if (!Double.isNaN(deviation)) {
            predictedRisk += deviation > 0.0
                    ? Math.min(14.0f, (float) deviation * 10.0f)
                    : -Math.min(8.0f, (float) Math.abs(deviation) * 6.0f);
        }

        // Behavioral adjustments
        if (behaviorReport != null) {
            if (behaviorReport.rapidSwitchCount >= 8) predictedRisk += 6.0f;
            if (behaviorReport.lateNightUsageMillis >= 1200000L) predictedRisk += 8.0f;
            if (behaviorReport.hasLoopPattern) predictedRisk += 5.0f;
        }

        // Response-based adjustments
        if (latestResponse != null) {
            if (latestResponse.sleepHours > 0.0f && latestResponse.sleepHours < 6.0f)
                predictedRisk += 7.0f;
            if (latestResponse.stressLevel >= 4) predictedRisk += 10.0f;
            if (latestResponse.exercisedToday) predictedRisk -= 6.0f;
            if ("Anxious".equalsIgnoreCase(latestResponse.mood)
                    || "Sad".equalsIgnoreCase(latestResponse.mood)
                    || "Numb".equalsIgnoreCase(latestResponse.mood)) {
                predictedRisk += 6.0f;
            } else if ("Happy".equalsIgnoreCase(latestResponse.mood)
                    || "Calm".equalsIgnoreCase(latestResponse.mood)) {
                predictedRisk -= 5.0f;
            }
        }

        predictedRisk = HomeScreenTextHelper.clamp(Math.round(predictedRisk), 8, 96);
        int predictedRiskInt = Math.round(predictedRisk);
        int riskDelta = predictedRiskInt - Math.round(todayRisk);

        // Confidence calculation
        int confidence = HomeScreenTextHelper.clamp(
                52 + (classifications == null ? 0 : Math.min(21, classifications.size() * 3))
                        + (latestResponse != null ? 8 : 0)
                        + (baseline != null ? 7 : 0),
                50, 91);

        // Forecast trend line
        ArrayList<Float> forecastTrend = new ArrayList<>();
        forecastTrend.add(yesterdayRisk);
        forecastTrend.add(todayRisk);
        forecastTrend.add(predictedRisk);

        boolean highRiskTomorrow = predictedRisk >= 70.0f;
        String driverLabel = buildForecastDriverLabel(behaviorReport, latestResponse, deviation);

        if (predictedRisk >= 70.0f) {
            return new HomeScreenState.ForecastCard(
                    "\u26c8", "Tomorrow may feel heavy",
                    "Risk is projected around " + predictedRisk + "/100 if today's patterns carry into tomorrow.",
                    confidence,
                    "Consider sleeping earlier, protecting your first hour, and using DND before bed.",
                    forecastTrend, true, predictedRiskInt, riskDelta, driverLabel);
        }
        if (predictedRisk >= 45.0f) {
            return new HomeScreenState.ForecastCard(
                    "\u26c5", "Tomorrow needs a little care",
                    "The outlook is watchful, not dangerous. A cleaner evening routine could noticeably lower tomorrow's friction.",
                    confidence,
                    "Keep the phone out of the first work block and cap late-night usage.",
                    forecastTrend, false, predictedRiskInt, riskDelta, driverLabel);
        }
        return new HomeScreenState.ForecastCard(
                "\u2600\ufe0f", "Tomorrow looks steady",
                "Momentum is projected to stay stable if you keep today's structure intact.",
                confidence,
                "Repeat the habits that made today cleaner: one focused block, one check-in, one protected evening.",
                forecastTrend, highRiskTomorrow, predictedRiskInt, riskDelta, driverLabel);
    }

    // ─── Risk Trend ─────────────────────────────────────────────────

    public List<Float> buildRiskTrend(List<RiskClassification> classifications, int fallbackRiskIndex) {
        ArrayList<Float> history = new ArrayList<>();
        if (classifications != null && !classifications.isEmpty()) {
            ArrayList<RiskClassification> sorted = new ArrayList<>(classifications);
            Collections.sort(sorted, (a, b) -> Long.compare(a.dayTimestamp, b.dayTimestamp));
            for (RiskClassification c : sorted) {
                if (c == null) continue;
                history.add(Math.max(0.0f, Math.min(100.0f, c.overallRiskScore * 100.0f)));
            }
        }
        if (history.isEmpty()) {
            history.add((float) fallbackRiskIndex);
        }
        while (history.size() < 7) {
            history.add(0, history.get(0));
        }
        if (history.size() > 7) {
            return new ArrayList<>(history.subList(history.size() - 7, history.size()));
        }
        return history;
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private double calculateScreenTimeDeviation(long todayScreenTime, UserBaseline baseline) {
        if (baseline == null || baseline.avgScreenTime7d <= 0.0) return 0.0;
        return (double) todayScreenTime / baseline.avgScreenTime7d - 1.0;
    }

    private String buildForecastDriverLabel(BehaviorReport behaviorReport,
                                             QuestionnaireResponse latestResponse,
                                             double deviation) {
        if (behaviorReport != null && behaviorReport.lateNightUsageMillis >= 1200000L)
            return "Driver: late-night carryover";
        if (behaviorReport != null && behaviorReport.rapidSwitchCount >= 8)
            return "Driver: fragmented attention";
        if (behaviorReport != null && behaviorReport.hasLoopPattern)
            return "Driver: repeat-loop behavior";
        if (latestResponse != null && latestResponse.stressLevel >= 4)
            return "Driver: elevated stress";
        if (latestResponse != null && latestResponse.sleepHours > 0.0f && latestResponse.sleepHours < 6.0f)
            return "Driver: light sleep debt";
        if (!Double.isNaN(deviation) && deviation >= 0.75)
            return "Driver: higher screen-time load";
        if (latestResponse != null && latestResponse.exercisedToday)
            return "Driver: exercise buffer";
        return "Driver: routine stability";
    }
}
