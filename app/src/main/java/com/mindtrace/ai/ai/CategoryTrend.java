package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Per-category trend analysis data class (Blueprint §7).
 *
 * <p>Tracks today's score, yesterday's score, 7-day average, and computes
 * directional labels with change percentages for each risk category.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   CategoryTrend trend = CategoryTrend.compute("stress_anxiety",
 *       todayScore, yesterdayScore, sevenDayAvg);
 *   trend.direction;     // "IMPROVING" | "STABLE" | "WORSENING"
 *   trend.changePercent; // -12.5f
 *   trend.uiText;        // "↓ Improving 12%"
 * </pre>
 *
 * @see MultiModalClassifier#getCategoryTrend(String, int)
 */
public final class CategoryTrend {

    /** Risk category identifier (e.g. "stress_anxiety"). */
    @NonNull public final String category;

    /** Today's classifier score (0.0–1.0). */
    public final float todayScore;

    /** Yesterday's classifier score (0.0–1.0). */
    public final float yesterdayScore;

    /** 7-day rolling average score (0.0–1.0). */
    public final float sevenDayAvgScore;

    /** Direction: "IMPROVING", "STABLE", or "WORSENING". */
    @NonNull public final String direction;

    /** Percent change vs 7-day average (-100 to +200). */
    public final float changePercent;

    /** Linear regression slope over lookback window. */
    public final float trendSlope;

    /** UI display text (e.g. "↓ Improving 12%"). */
    @NonNull public final String uiText;

    /** UI color hex (green/grey/red based on direction). */
    @NonNull public final String uiColor;

    // ═══════════════════════════════════════════════════════════════════
    // THRESHOLDS
    // ═══════════════════════════════════════════════════════════════════

    private static final float IMPROVING_THRESHOLD = -5f;   // >5% drop = improving
    private static final float WORSENING_THRESHOLD = 5f;    // >5% rise = worsening
    private static final float RAPID_THRESHOLD = 15f;       // >15% = rapid change

    private static final String COLOR_IMPROVING = "#4ADE80";
    private static final String COLOR_STABLE    = "#8896B0";
    private static final String COLOR_WORSENING = "#FF6B6B";

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    private CategoryTrend(@NonNull String category, float todayScore,
                          float yesterdayScore, float sevenDayAvgScore,
                          float trendSlope) {
        this.category = category;
        this.todayScore = todayScore;
        this.yesterdayScore = yesterdayScore;
        this.sevenDayAvgScore = sevenDayAvgScore;
        this.trendSlope = trendSlope;

        // Compute change percent vs 7-day average
        if (sevenDayAvgScore > 0.01f) {
            this.changePercent = ((todayScore - sevenDayAvgScore) / sevenDayAvgScore) * 100f;
        } else {
            this.changePercent = 0f;
        }

        // Determine direction
        if (changePercent < IMPROVING_THRESHOLD) {
            this.direction = "IMPROVING";
            this.uiColor = COLOR_IMPROVING;
            if (changePercent < -RAPID_THRESHOLD) {
                this.uiText = String.format(Locale.US, "↓↓ Rapidly improving %.0f%%",
                        Math.abs(changePercent));
            } else {
                this.uiText = String.format(Locale.US, "↓ Improving %.0f%%",
                        Math.abs(changePercent));
            }
        } else if (changePercent > WORSENING_THRESHOLD) {
            this.direction = "WORSENING";
            this.uiColor = COLOR_WORSENING;
            if (changePercent > RAPID_THRESHOLD) {
                this.uiText = String.format(Locale.US, "↑↑ Rapidly rising %.0f%%",
                        changePercent);
            } else {
                this.uiText = String.format(Locale.US, "↑ Rising %.0f%%",
                        changePercent);
            }
        } else {
            this.direction = "STABLE";
            this.uiColor = COLOR_STABLE;
            this.uiText = "→ Stable";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute a CategoryTrend from raw scores.
     */
    @NonNull
    public static CategoryTrend compute(@NonNull String category,
                                         float todayScore,
                                         float yesterdayScore,
                                         float sevenDayAvgScore) {
        return new CategoryTrend(category, todayScore, yesterdayScore,
                sevenDayAvgScore, 0f);
    }

    /**
     * Compute with an explicit trend slope from linear regression.
     */
    @NonNull
    public static CategoryTrend compute(@NonNull String category,
                                         float todayScore,
                                         float yesterdayScore,
                                         float sevenDayAvgScore,
                                         float trendSlope) {
        return new CategoryTrend(category, todayScore, yesterdayScore,
                sevenDayAvgScore, trendSlope);
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /** Whether the category is actively improving. */
    public boolean isImproving() { return "IMPROVING".equals(direction); }

    /** Whether the category is actively worsening. */
    public boolean isWorsening() { return "WORSENING".equals(direction); }

    /** Whether the category is stable (within ±5%). */
    public boolean isStable() { return "STABLE".equals(direction); }

    /** Whether this is a rapid change (>15% either direction). */
    public boolean isRapidChange() { return Math.abs(changePercent) > RAPID_THRESHOLD; }

    /** Delta from yesterday (positive = worse). */
    public float dayOverDayDelta() { return todayScore - yesterdayScore; }

    /** Delta from 7-day average (positive = worse). */
    public float weekDelta() { return todayScore - sevenDayAvgScore; }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED: MOMENTUM
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compute risk momentum: combines velocity (slope) with acceleration
     * (rate of change of slope). Positive = risk accelerating upward.
     *
     * @param previousSlope the trend slope from the previous period
     * @return momentum value: -1.0 to +1.0
     */
    public float computeMomentum(float previousSlope) {
        float velocity = trendSlope;
        float acceleration = trendSlope - previousSlope;
        return Math.max(-1f, Math.min(1f,
                velocity * 0.6f + acceleration * 0.4f));
    }

    /**
     * Get a momentum label.
     */
    @NonNull
    public String getMomentumLabel(float momentum) {
        if (momentum > 0.3f) return "accelerating_risk";
        if (momentum > 0.1f) return "building_risk";
        if (momentum < -0.3f) return "strong_recovery";
        if (momentum < -0.1f) return "gradual_recovery";
        return "neutral";
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEBUG
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US,
                "CategoryTrend{%s: today=%.2f, yesterday=%.2f, avg7d=%.2f, %s (%.1f%%), slope=%.3f}",
                category, todayScore, yesterdayScore, sevenDayAvgScore,
                direction, changePercent, trendSlope);
    }
}
