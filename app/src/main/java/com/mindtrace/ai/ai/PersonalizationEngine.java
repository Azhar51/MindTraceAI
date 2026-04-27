package com.mindtrace.ai.ai;

import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.util.MoodMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PersonalizationEngine {
    private static final double HEALTHY_THRESHOLD = 0.4d;
    private static final double MODERATE_THRESHOLD = 0.7d;

    public Result analyze(UserBaseline baseline, long screenTimeMillis, QuestionnaireResponse response, int fulfillmentScore) {
        MetricResult screenTimeMetric = evaluateHigherIsRisk(
                screenTimeMillis,
                baseline == null ? 0d : baseline.avgScreenTime7d,
                getFallbackScreenTimeScore(screenTimeMillis),
                "Better than your weekly average usage",
                "Close to your weekly average usage",
                "Above your normal usage",
                "Far above your normal usage"
        );

        float sleepHours = response == null ? 0f : response.sleepHours;
        MetricResult sleepMetric = evaluateLowerIsRisk(
                sleepHours,
                baseline == null ? 0d : baseline.avgSleep7d,
                getFallbackSleepScore(sleepHours),
                "Sleep is better than your baseline",
                "Sleep is close to your baseline",
                "Sleep dropped compared to your baseline",
                "Sleep is well below your baseline"
        );

        int stressLevel = response == null ? 0 : response.stressLevel;
        MetricResult stressMetric = evaluateHigherIsRisk(
                stressLevel,
                baseline == null ? 0d : baseline.avgStress7d,
                getFallbackStressScore(stressLevel),
                "Stress is lower than your usual pattern",
                "Stress is close to your baseline",
                "Stress is above your usual pattern",
                "Stress is much higher than your baseline"
        );

        int moodScore = Math.round(MoodMapper.moodToFloat(response == null ? null : response.mood));
        MetricResult moodMetric = evaluateLowerIsRisk(
                moodScore,
                baseline == null ? 0d : baseline.avgMoodScore7d,
                getFallbackMoodScore(moodScore),
                "Mood is better than your weekly average",
                "Mood is close to your baseline",
                "Mood is below your baseline",
                "Mood dropped compared to your baseline"
        );

        MetricResult taskMetric = evaluateLowerIsRisk(
                fulfillmentScore,
                baseline == null ? 0d : baseline.avgTaskCompletion7d,
                getFallbackTaskScore(fulfillmentScore),
                "Task follow-through is better than your normal pattern",
                "Task follow-through is close to your baseline",
                "Task follow-through is below your normal pattern",
                "Task completion fell below your baseline"
        );

        double compositeScore =
                (screenTimeMetric.score * 0.35d) +
                (sleepMetric.score * 0.20d) +
                (stressMetric.score * 0.20d) +
                (moodMetric.score * 0.15d) +
                (taskMetric.score * 0.10d);

        DashboardInsights.RiskLevel riskLevel;
        String riskLabel;
        if (compositeScore < HEALTHY_THRESHOLD) {
            riskLevel = DashboardInsights.RiskLevel.LOW;
            riskLabel = "HEALTHY";
        } else if (compositeScore < MODERATE_THRESHOLD) {
            riskLevel = DashboardInsights.RiskLevel.MODERATE;
            riskLabel = "MODERATE";
        } else {
            riskLevel = DashboardInsights.RiskLevel.HIGH;
            riskLabel = "HIGH RISK";
        }

        List<String> comparisons = new ArrayList<>();
        comparisons.add(screenTimeMetric.message);
        comparisons.add(sleepMetric.message);
        comparisons.add(stressMetric.message);
        comparisons.add(moodMetric.message);
        comparisons.add(taskMetric.message);

        return new Result(
                screenTimeMetric.personalized
                        || sleepMetric.personalized
                        || stressMetric.personalized
                        || moodMetric.personalized
                        || taskMetric.personalized,
                compositeScore,
                riskLevel,
                riskLabel,
                chooseSummary(screenTimeMetric, sleepMetric, stressMetric, moodMetric, taskMetric),
                joinComparisons(comparisons),
                screenTimeMetric.message,
                sleepMetric.message,
                stressMetric.message,
                moodMetric.message,
                taskMetric.message
        );
    }

    private MetricResult evaluateHigherIsRisk(
            double currentValue,
            double baselineValue,
            double fallbackScore,
            String healthyMessage,
            String normalMessage,
            String elevatedMessage,
            String highRiskMessage
    ) {
        if (baselineValue <= 0d || currentValue < 0d) {
            return new MetricResult(false, fallbackScore, getFallbackLabel(fallbackScore));
        }

        double ratio = currentValue / baselineValue;
        if (ratio < 0.8d) {
            return new MetricResult(true, 0.2d, healthyMessage);
        }
        if (ratio <= 1.2d) {
            return new MetricResult(true, 0.45d, normalMessage);
        }
        if (ratio <= 1.5d) {
            return new MetricResult(true, 0.7d, elevatedMessage);
        }
        return new MetricResult(true, 0.9d, highRiskMessage);
    }

    private MetricResult evaluateLowerIsRisk(
            double currentValue,
            double baselineValue,
            double fallbackScore,
            String healthyMessage,
            String normalMessage,
            String elevatedMessage,
            String highRiskMessage
    ) {
        if (baselineValue <= 0d || currentValue < 0d) {
            return new MetricResult(false, fallbackScore, getFallbackLabel(fallbackScore));
        }

        double ratio = currentValue / baselineValue;
        if (ratio > 1.2d) {
            return new MetricResult(true, 0.2d, healthyMessage);
        }
        if (ratio >= 0.8d) {
            return new MetricResult(true, 0.45d, normalMessage);
        }
        if (ratio >= 0.6d) {
            return new MetricResult(true, 0.7d, elevatedMessage);
        }
        return new MetricResult(true, 0.9d, highRiskMessage);
    }

    private String chooseSummary(MetricResult... metrics) {
        MetricResult strongest = null;
        for (MetricResult metric : metrics) {
            if (strongest == null || metric.score > strongest.score) {
                strongest = metric;
            }
        }
        return strongest == null ? "Your baseline is still being learned." : strongest.message;
    }

    private String joinComparisons(List<String> comparisons) {
        StringBuilder builder = new StringBuilder();
        for (String comparison : comparisons) {
            if (comparison == null || comparison.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(comparison);
        }
        return builder.toString();
    }

    private String getFallbackLabel(double fallbackScore) {
        if (fallbackScore < HEALTHY_THRESHOLD) {
            return "Using general healthy thresholds";
        }
        if (fallbackScore < MODERATE_THRESHOLD) {
            return "Using general moderate thresholds";
        }
        return "Using general high-risk thresholds";
    }

    private double getFallbackScreenTimeScore(long screenTimeMillis) {
        long fourHours = 4L * 60L * 60L * 1000L;
        long sixHours = 6L * 60L * 60L * 1000L;
        long eightHours = 8L * 60L * 60L * 1000L;
        if (screenTimeMillis < fourHours) {
            return 0.2d;
        }
        if (screenTimeMillis < sixHours) {
            return 0.45d;
        }
        if (screenTimeMillis < eightHours) {
            return 0.7d;
        }
        return 0.9d;
    }

    private double getFallbackSleepScore(float sleepHours) {
        if (sleepHours <= 0f) {
            return 0.45d;
        }
        if (sleepHours >= 7f) {
            return 0.2d;
        }
        if (sleepHours >= 6f) {
            return 0.45d;
        }
        if (sleepHours >= 5f) {
            return 0.7d;
        }
        return 0.9d;
    }

    private double getFallbackStressScore(int stressLevel) {
        if (stressLevel <= 0) {
            return 0.45d;
        }
        if (stressLevel <= 2) {
            return 0.2d;
        }
        if (stressLevel == 3) {
            return 0.45d;
        }
        if (stressLevel == 4) {
            return 0.7d;
        }
        return 0.9d;
    }

    private double getFallbackMoodScore(int moodScore) {
        if (moodScore >= 4) {
            return 0.2d;
        }
        if (moodScore == 3) {
            return 0.45d;
        }
        if (moodScore == 2) {
            return 0.7d;
        }
        return 0.9d;
    }

    private double getFallbackTaskScore(int fulfillmentScore) {
        if (fulfillmentScore >= 80) {
            return 0.2d;
        }
        if (fulfillmentScore >= 40) {
            return 0.45d;
        }
        if (fulfillmentScore > 0) {
            return 0.7d;
        }
        return 0.45d;
    }

    // Removed: mapMoodToScore() — now delegated to MoodMapper.moodToFloat()

    public static class Result {
        public final boolean personalized;
        public final double compositeScore;
        public final DashboardInsights.RiskLevel riskLevel;
        public final String riskLabel;
        public final String summaryMessage;
        public final String comparisons;
        public final String screenTimeMessage;
        public final String sleepMessage;
        public final String stressMessage;
        public final String moodMessage;
        public final String taskMessage;

        public Result(
                boolean personalized,
                double compositeScore,
                DashboardInsights.RiskLevel riskLevel,
                String riskLabel,
                String summaryMessage,
                String comparisons,
                String screenTimeMessage,
                String sleepMessage,
                String stressMessage,
                String moodMessage,
                String taskMessage
        ) {
            this.personalized = personalized;
            this.compositeScore = compositeScore;
            this.riskLevel = riskLevel;
            this.riskLabel = riskLabel;
            this.summaryMessage = summaryMessage;
            this.comparisons = comparisons;
            this.screenTimeMessage = screenTimeMessage;
            this.sleepMessage = sleepMessage;
            this.stressMessage = stressMessage;
            this.moodMessage = moodMessage;
            this.taskMessage = taskMessage;
        }
    }

    private static class MetricResult {
        final boolean personalized;
        final double score;
        final String message;

        MetricResult(boolean personalized, double score, String message) {
            this.personalized = personalized;
            this.score = score;
            this.message = message;
        }
    }
}
