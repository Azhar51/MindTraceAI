package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Correlation engine — discovers hidden connections between digital behavior
 * and emotional wellbeing using Pearson correlation analysis.
 *
 * <h3>Supported Correlations (5 types):</h3>
 * <ol>
 *   <li><b>Screen time ↔ Mood</b> — does more screen time correlate with worse mood?</li>
 *   <li><b>Night usage ↔ Sleep quality</b> — late phone use vs. sleep hours</li>
 *   <li><b>App switches ↔ Stress</b> — restless switching vs. stress level</li>
 *   <li><b>Social media ↔ Loneliness</b> — scroll time vs. isolation feelings</li>
 *   <li><b>Screen time ↔ Sleep hours</b> — total screen vs. sleep duration</li>
 * </ol>
 *
 * <h3>Interpretation:</h3>
 * <ul>
 *   <li>|r| > 0.6 → <b>Strong</b> correlation</li>
 *   <li>|r| > 0.4 → <b>Moderate</b> correlation</li>
 *   <li>|r| > 0.3 → <b>Weak</b> correlation (still reported)</li>
 *   <li>|r| ≤ 0.3 → Not significant (not reported)</li>
 * </ul>
 *
 * @see TrendPredictor
 * @see AnomalyDetector
 */
public class CorrelationEngine {

    private static final int MIN_PAIRED_DAYS = 7;

    // ═══════════════════════════════════════════════════════════════════
    // CORRELATION INSIGHT
    // ═══════════════════════════════════════════════════════════════════

    public static class CorrelationInsight {
        @NonNull public final String type;
        public final float coefficient;
        @NonNull public final String finding;
        @NonNull public final String recommendation;
        @NonNull public final String strength;
        public final int pairedDays;

        public CorrelationInsight(@NonNull String type, float r,
                                  @NonNull String finding, @NonNull String rec,
                                  int pairedDays) {
            this.type = type;
            this.coefficient = r;
            this.finding = finding;
            this.recommendation = rec;
            this.pairedDays = pairedDays;
            this.strength = Math.abs(r) > 0.6f ? "Strong"
                    : Math.abs(r) > 0.4f ? "Moderate" : "Weak";
        }

        /** Whether this is a negative correlation (e.g., more X → less Y). */
        public boolean isNegative() { return coefficient < 0; }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.US, "%s: r=%.2f (%s) — %s",
                    type, coefficient, strength, finding);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PAIRED DAY
    // ═══════════════════════════════════════════════════════════════════

    /** A day where both usage and mood data exist. */
    private static class PairedDay {
        final DailyUsage usage;
        final QuestionnaireResponse mood;

        PairedDay(DailyUsage usage, QuestionnaireResponse mood) {
            this.usage = usage;
            this.mood = mood;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIMARY API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Find statistically significant correlations between behavior and mood.
     *
     * @param usageHistory usage records (newest first)
     * @param moodHistory  questionnaire responses (newest first)
     * @return list of discovered correlations (empty if insufficient data)
     */
    @NonNull
    public List<CorrelationInsight> findCorrelations(
            @Nullable List<DailyUsage> usageHistory,
            @Nullable List<QuestionnaireResponse> moodHistory) {

        List<CorrelationInsight> insights = new ArrayList<>();

        if (usageHistory == null || moodHistory == null ||
                usageHistory.size() < MIN_PAIRED_DAYS ||
                moodHistory.size() < MIN_PAIRED_DAYS) {
            return insights;
        }

        // Pair data by date
        List<PairedDay> paired = pairByDate(usageHistory, moodHistory);
        if (paired.size() < MIN_PAIRED_DAYS) return insights;

        int n = paired.size();

        // ── Correlation 1: Screen time ↔ Mood ──
        float r1 = pearsonCorrelation(paired,
                p -> (float) p.usage.screenTimeMillis,
                p -> TrendPredictor.mapMoodToFloat(p.mood.mood));
        if (Math.abs(r1) > 0.3f) {
            insights.add(new CorrelationInsight(
                    "SCREEN_TIME_MOOD", r1,
                    r1 < 0 ? "You tend to feel worse on days with higher screen time"
                            : "Higher screen time days correlate with better mood",
                    r1 < 0 ? "Consider setting screen time intentions on difficult days"
                            : "Your screen use may include positive activities — keep monitoring",
                    n));
        }

        // ── Correlation 2: Night usage ↔ Sleep ──
        float r2 = pearsonCorrelation(paired,
                p -> (float) p.usage.nightUsageMillis,
                p -> p.mood.sleepHours);
        if (r2 < -0.3f) {
            insights.add(new CorrelationInsight(
                    "NIGHT_USAGE_SLEEP", r2,
                    "Late-night phone use is correlated with less sleep",
                    "Try putting your phone down 30 minutes before bed",
                    n));
        }

        // ── Correlation 3: App switches ↔ Stress ──
        float r3 = pearsonCorrelation(paired,
                p -> (float) p.usage.totalAppSwitchCount,
                p -> (float) p.mood.stressLevel);
        if (r3 > 0.3f) {
            insights.add(new CorrelationInsight(
                    "SWITCHES_STRESS", r3,
                    "Higher app switching correlates with higher stress levels",
                    "When feeling restless, try a 5-minute breathing exercise",
                    n));
        }

        // ── Correlation 4: Social media ↔ Loneliness ──
        float r4 = pearsonCorrelation(paired,
                p -> (float) p.usage.socialMediaTimeMillis,
                p -> (float) p.mood.lonelinessLevel);
        if (r4 > 0.3f) {
            insights.add(new CorrelationInsight(
                    "SOCIAL_APPS_LONELINESS", r4,
                    "More time on social media correlates with feeling lonelier",
                    "Consider replacing scroll time with real conversations",
                    n));
        }

        // ── Correlation 5: Screen time ↔ Sleep hours ──
        float r5 = pearsonCorrelation(paired,
                p -> (float) p.usage.screenTimeMillis,
                p -> p.mood.sleepHours);
        if (r5 < -0.3f) {
            insights.add(new CorrelationInsight(
                    "SCREEN_SLEEP", r5,
                    "Days with higher screen time tend to result in less sleep",
                    "Your sleep quality improves when you reduce evening screen time",
                    n));
        }

        return insights;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PEARSON CORRELATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Pearson correlation coefficient.
     *
     * @return value between -1.0 (perfect negative) and +1.0 (perfect positive)
     */
    private float pearsonCorrelation(
            List<PairedDay> data,
            ValueExtractor xExtractor,
            ValueExtractor yExtractor) {

        int n = data.size();
        if (n < 3) return 0f;

        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;

        for (PairedDay p : data) {
            float x = xExtractor.extract(p);
            float y = yExtractor.extract(p);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }

        float numerator = n * sumXY - sumX * sumY;
        float denominator = (float) Math.sqrt(
                (n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        if (denominator < 0.001f) return 0f;
        return Math.max(-1f, Math.min(1f, numerator / denominator));
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATE PAIRING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Pair usage and mood records by calendar date.
     * Only includes days where both data sources have entries.
     */
    @NonNull
    private List<PairedDay> pairByDate(List<DailyUsage> usage,
                                        List<QuestionnaireResponse> mood) {
        // Index mood by date string
        Map<String, QuestionnaireResponse> moodByDate = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        for (QuestionnaireResponse r : mood) {
            cal.setTimeInMillis(r.timestamp);
            String key = String.format(Locale.US, "%d-%d-%d",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH));
            // Keep the first entry per day (most recent if sorted desc)
            if (!moodByDate.containsKey(key)) {
                moodByDate.put(key, r);
            }
        }

        // Match usage with mood
        List<PairedDay> paired = new ArrayList<>();
        for (DailyUsage u : usage) {
            cal.setTimeInMillis(u.date);
            String key = String.format(Locale.US, "%d-%d-%d",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH));
            QuestionnaireResponse match = moodByDate.get(key);
            if (match != null) {
                paired.add(new PairedDay(u, match));
            }
        }

        return paired;
    }

    // ═══════════════════════════════════════════════════════════════════
    // FUNCTIONAL INTERFACE (API < 24 compat)
    // ═══════════════════════════════════════════════════════════════════

    private interface ValueExtractor {
        float extract(PairedDay pair);
    }
}
