package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

/**
 * Baseline Deviation Bar — Horizontal indicator showing how today's
 * metrics compare to the user's established baseline.
 *
 * <p>Design specs from blueprint Part 2A:
 * <pre>
 *   ▲ 12% above baseline
 *   ━━━━━━━━━━━━━━━●━━━━━━━━━━━━━  (center = baseline, dot slides)
 * </pre>
 *
 * <ul>
 *   <li>Horizontal bar with center marker (baseline = center)</li>
 *   <li>Dot indicator slides left (below baseline) or right (above baseline)</li>
 *   <li>Text: "▲ 12% above baseline" or "▼ 8% below baseline"</li>
 *   <li>Color: green if below, amber if slightly above, red if significantly above</li>
 * </ul>
 *
 * <p>The deviation is expressed as a percentage from -100% to +100%,
 * where 0% means exactly at baseline.
 */
public class BaselineDeviationBar extends View {

    // ═══════════════════════════════════════════════════════════════════
    // PAINTS
    // ═══════════════════════════════════════════════════════════════════

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leftFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rightFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF fillRect = new RectF();

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    private float deviationPercent = 0f;      // -100 to +100 (negative = below, positive = above)
    private float animatedDeviation = 0f;
    private String metricLabel = "baseline";  // What metric (e.g., "screen time baseline")

    // Colors
    private static final int COLOR_GREEN  = 0xFF4ADE80;
    private static final int COLOR_AMBER  = 0xFFF5A623;
    private static final int COLOR_RED    = 0xFFFF6B6B;
    private static final int COLOR_TRACK  = 0xFF1A2540;
    private static final int COLOR_CENTER = 0xFF2A3A58;  // Center marker line
    private static final int COLOR_TEXT_SECONDARY = 0xFF8896B0;

    // Geometry constants
    private static final float BAR_HEIGHT_DP = 6f;
    private static final float DOT_RADIUS_DP = 7f;
    private static final float DOT_GLOW_RADIUS_DP = 14f;
    private static final float CENTER_MARKER_WIDTH_DP = 2f;
    private static final float CENTER_MARKER_HEIGHT_DP = 14f;
    private static final float CORNER_RADIUS_DP = 3f;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    public BaselineDeviationBar(Context context) {
        this(context, null);
    }

    public BaselineDeviationBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaselineDeviationBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    private void initPaints() {
        float dp = getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(COLOR_TRACK);

        leftFillPaint.setStyle(Paint.Style.FILL);
        rightFillPaint.setStyle(Paint.Style.FILL);

        centerMarkerPaint.setStyle(Paint.Style.FILL);
        centerMarkerPaint.setColor(COLOR_CENTER);

        dotPaint.setStyle(Paint.Style.FILL);

        dotGlowPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(COLOR_TEXT_SECONDARY);
        textPaint.setTextSize(12f * dp);

        percentPaint.setFakeBoldText(true);
        percentPaint.setTextSize(13f * dp);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Set the deviation from baseline.
     *
     * @param percent  Deviation percentage (-100 to +100).
     *                 Negative = below baseline (good), Positive = above (concerning)
     * @param animate  Whether to animate the indicator
     */
    public void setDeviation(float percent, boolean animate) {
        this.deviationPercent = Math.max(-100f, Math.min(100f, percent));

        if (!animate) {
            this.animatedDeviation = this.deviationPercent;
            invalidate();
            return;
        }

        float from = animatedDeviation;
        ValueAnimator animator = ValueAnimator.ofFloat(from, this.deviationPercent);
        animator.setDuration(600L);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(a -> {
            animatedDeviation = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    /**
     * Set the label for what metric is being compared to baseline.
     * @param label e.g., "screen time", "app switches"
     */
    public void setMetricLabel(@NonNull String label) {
        this.metricLabel = label;
        invalidate();
    }

    /** Get the current deviation percentage. */
    public float getDeviationPercent() {
        return deviationPercent;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COLOR LOGIC
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Map deviation to color:
     * - Below baseline (negative) → green (good)
     * - Slightly above (0–20%) → amber (watch)
     * - Significantly above (>20%) → red (concerning)
     */
    private int getDeviationColor() {
        if (animatedDeviation <= 0) {
            return COLOR_GREEN;
        } else if (animatedDeviation <= 20) {
            return COLOR_AMBER;
        } else {
            return COLOR_RED;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MEASURE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float dp = getResources().getDisplayMetrics().density;

        // Total height: text line + gap + bar + dot overhang
        float textHeight = textPaint.getTextSize() + dp * 4;
        float barArea = Math.max(BAR_HEIGHT_DP, DOT_RADIUS_DP * 2 + 4) * dp;
        int totalHeight = (int) (textHeight + dp * 6 + barArea + dp * 4);

        setMeasuredDimension(width, totalHeight);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DRAW
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float dp = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float barH = BAR_HEIGHT_DP * dp;
        float cornerR = CORNER_RADIUS_DP * dp;
        float dotR = DOT_RADIUS_DP * dp;
        float dotGlowR = DOT_GLOW_RADIUS_DP * dp;
        float markerW = CENTER_MARKER_WIDTH_DP * dp;
        float markerH = CENTER_MARKER_HEIGHT_DP * dp;

        int devColor = getDeviationColor();
        int absPercent = Math.round(Math.abs(animatedDeviation));

        // ── 1. Text label ("▲ 12% above baseline" or "▼ 8% below baseline") ──
        String arrow;
        String direction;
        if (animatedDeviation > 0.5f) {
            arrow = "▲ ";
            direction = " above ";
        } else if (animatedDeviation < -0.5f) {
            arrow = "▼ ";
            direction = " below ";
        } else {
            arrow = "● ";
            direction = " at ";
        }

        percentPaint.setColor(devColor);
        String percentStr = arrow + absPercent + "%";
        float percentWidth = percentPaint.measureText(percentStr);
        float textY = percentPaint.getTextSize();
        canvas.drawText(percentStr, 0, textY, percentPaint);

        textPaint.setColor(COLOR_TEXT_SECONDARY);
        canvas.drawText(direction + metricLabel, percentWidth + dp * 2, textY, textPaint);

        // ── 2. Track bar ──
        float barTop = textY + dp * 10;
        float barCenterY = barTop + barH / 2f;
        float centerX = w / 2f;
        float barPadding = dotR; // Ensure dot doesn't clip edges

        trackRect.set(barPadding, barTop, w - barPadding, barTop + barH);
        canvas.drawRoundRect(trackRect, cornerR, cornerR, trackPaint);

        // ── 3. Fill from center to dot position ──
        float barWidth = w - barPadding * 2;
        float normalizedDev = animatedDeviation / 100f; // -1 to +1
        float dotX = centerX + (barWidth / 2f) * normalizedDev;
        dotX = Math.max(barPadding + dotR, Math.min(w - barPadding - dotR, dotX));

        if (Math.abs(animatedDeviation) > 0.5f) {
            float fillLeft = Math.min(centerX, dotX);
            float fillRight = Math.max(centerX, dotX);
            fillRect.set(fillLeft, barTop, fillRight, barTop + barH);

            // Gradient fill from center to dot
            int darkColor = withAlpha(devColor, 80);
            Paint fillPaint = animatedDeviation > 0 ? rightFillPaint : leftFillPaint;
            fillPaint.setShader(new LinearGradient(
                fillLeft, barTop, fillRight, barTop,
                darkColor, devColor,
                Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(fillRect, cornerR, cornerR, fillPaint);
            fillPaint.setShader(null);
        }

        // ── 4. Center baseline marker ──
        float markerTop = barCenterY - markerH / 2f;
        RectF markerRect = new RectF(
            centerX - markerW / 2f,
            markerTop,
            centerX + markerW / 2f,
            markerTop + markerH
        );
        canvas.drawRoundRect(markerRect, markerW / 2f, markerW / 2f, centerMarkerPaint);

        // ── 5. Dot indicator with glow ──
        // Outer glow
        dotGlowPaint.setColor(withAlpha(devColor, 30));
        canvas.drawCircle(dotX, barCenterY, dotGlowR, dotGlowPaint);

        // Mid glow
        dotGlowPaint.setColor(withAlpha(devColor, 60));
        canvas.drawCircle(dotX, barCenterY, dotR + dp * 3, dotGlowPaint);

        // Solid dot
        dotPaint.setColor(devColor);
        canvas.drawCircle(dotX, barCenterY, dotR, dotPaint);

        // Inner highlight (white dot for depth)
        dotPaint.setColor(withAlpha(0xFFFFFFFF, 100));
        canvas.drawCircle(dotX - dp * 1.5f, barCenterY - dp * 1.5f, dp * 2, dotPaint);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
