package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.mindtrace.ai.ui.theme.ColorSystem;

/**
 * Enhanced Wellness Ring — full-circle gradient arc with glow,
 * animated sweep, and center score display.
 *
 * <p>Design specs (from blueprint Part 2D-1):
 * <ul>
 *   <li>Shape: Full 360° circle</li>
 *   <li>Size: 180dp × 180dp (recommended)</li>
 *   <li>Track: 16dp, #1A2540 background</li>
 *   <li>Arc: 16dp, gradient based on score range</li>
 *   <li>Arc start: 135° (bottom-left), sweep: 270° max</li>
 *   <li>Glow: 20dp blur of arc color at 25%</li>
 *   <li>Animation: 900ms with DecelerateInterpolator(1.5)</li>
 * </ul>
 */
public class WellnessRingView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint primaryTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondaryTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private OnThresholdCrossedListener thresholdListener;
    private boolean highThresholdNotified = false;

    private float score;
    private float animatedScore;
    private String centerText = "0";
    private String captionText = "risk index";
    private String riskLabel = "";
    private int riskLabelColor = 0xFF8896B0;

    // Arc geometry
    private static final float ARC_START = 135f;   // bottom-left
    private static final float ARC_SWEEP_MAX = 270f; // leaving 90° gap at bottom

    // Gradient colors per score range
    private int gradientStart = 0xFF2A8B5A;
    private int gradientEnd = 0xFF4ADE80;

    public WellnessRingView(Context context) { this(context, null); }
    public WellnessRingView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }

    public WellnessRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(0xFF1A2540);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setColor(0xFF0F1729);

        primaryTextPaint.setColor(0xFFE8ECF4);
        primaryTextPaint.setTextAlign(Paint.Align.CENTER);
        primaryTextPaint.setFakeBoldText(true);

        secondaryTextPaint.setColor(0xFF8896B0);
        secondaryTextPaint.setTextAlign(Paint.Align.CENTER);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);

        setLayerType(LAYER_TYPE_SOFTWARE, null); // Required for blur/glow
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    public void setScore(float score, boolean animate) {
        this.score = Math.max(0f, Math.min(1f, score));
        if (this.score < 0.7f || animatedScore < 0.7f) {
            highThresholdNotified = false;
        }
        updateGradientColors();
        setCenterText(String.valueOf(Math.round(this.score * 100f)));

        if (!animate) {
            animatedScore = this.score;
            invalidate();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, this.score);
        animator.setDuration(900L);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(a -> {
            animatedScore = (float) a.getAnimatedValue();
            if (!highThresholdNotified && animatedScore >= 0.7f && thresholdListener != null) {
                highThresholdNotified = true;
                thresholdListener.onThresholdCrossed(animatedScore);
            }
            invalidate();
        });
        animator.start();
    }

    public void setCenterText(String text) {
        this.centerText = text == null ? "" : text;
        invalidate();
    }

    public void setCaptionText(String text) {
        this.captionText = text == null ? "" : text;
        invalidate();
    }

    /** Set the risk level label shown below the caption (e.g., "Moderate Risk"). */
    public void setRiskLabel(String label, int color) {
        this.riskLabel = label == null ? "" : label;
        this.riskLabelColor = color;
        invalidate();
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRADIENT COLOR MAPPING
    // ═══════════════════════════════════════════════════════════════════

    private void updateGradientColors() {
        float s = this.score * 100f;
        if (s <= 25) {
            gradientStart = 0xFF2A8B5A; gradientEnd = 0xFF4ADE80;
            riskLabel = "Balanced"; riskLabelColor = 0xFF4ADE80;
        } else if (s <= 40) {
            gradientStart = 0xFF4ADE80; gradientEnd = 0xFF8CC63F;
            riskLabel = "Steady"; riskLabelColor = 0xFF8CC63F;
        } else if (s <= 55) {
            gradientStart = 0xFF8CC63F; gradientEnd = 0xFFF5A623;
            riskLabel = "Moderate Risk"; riskLabelColor = 0xFFF5A623;
        } else if (s <= 70) {
            gradientStart = 0xFFF5A623; gradientEnd = 0xFFE07040;
            riskLabel = "Elevated"; riskLabelColor = 0xFFE07040;
        } else if (s <= 85) {
            gradientStart = 0xFFE07040; gradientEnd = 0xFFFF6B6B;
            riskLabel = "High Risk"; riskLabelColor = 0xFFFF6B6B;
        } else {
            gradientStart = 0xFFFF6B6B; gradientEnd = 0xFFFF3B3B;
            riskLabel = "Critical"; riskLabelColor = 0xFFFF3B3B;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DRAW
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float strokeWidth = size * 0.09f; // ~16dp at 180dp
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = size / 2f - strokeWidth / 2f - dp(2);

        trackPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeWidth(strokeWidth);

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // 1. Background track arc (270° with gap)
        canvas.drawArc(arcRect, ARC_START, ARC_SWEEP_MAX, false, trackPaint);

        // 2. Progress arc with gradient
        float sweepAngle = ARC_SWEEP_MAX * animatedScore;
        if (sweepAngle > 0.5f) {
            // Apply sweep gradient
            SweepGradient gradient = new SweepGradient(cx, cy, gradientStart, gradientEnd);
            progressPaint.setShader(gradient);

            // Glow layer (wider, translucent)
            glowPaint.setStrokeWidth(strokeWidth + dp(10));
            glowPaint.setColor(withAlpha(gradientEnd, 60));
            canvas.drawArc(arcRect, ARC_START, sweepAngle, false, glowPaint);

            // Main arc
            canvas.drawArc(arcRect, ARC_START, sweepAngle, false, progressPaint);
            progressPaint.setShader(null);
        }

        // 3. Inner circle (dark center)
        float innerRadius = radius - strokeWidth / 2f - dp(3);
        canvas.drawCircle(cx, cy, Math.max(0f, innerRadius), innerPaint);

        // 4. Center text: score number
        primaryTextPaint.setTextSize(size * 0.28f);
        primaryTextPaint.setColor(riskLabelColor);
        Paint.FontMetrics fm = primaryTextPaint.getFontMetrics();
        float textCenterY = cy - (fm.ascent + fm.descent) / 2f - dp(6);
        canvas.drawText(centerText, cx, textCenterY, primaryTextPaint);

        // 5. Caption "/100" below score
        secondaryTextPaint.setTextSize(size * 0.09f);
        canvas.drawText("/100", cx, textCenterY + dp(18), secondaryTextPaint);

        // 6. Risk label below ring
        if (riskLabel != null && !riskLabel.isEmpty()) {
            labelPaint.setTextSize(size * 0.08f);
            labelPaint.setColor(riskLabelColor);
            canvas.drawText(riskLabel, cx, cy + radius + strokeWidth / 2f + dp(16), labelPaint);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setOnThresholdCrossedListener(OnThresholdCrossedListener listener) {
        this.thresholdListener = listener;
    }

    public interface OnThresholdCrossedListener {
        void onThresholdCrossed(float score);
    }
}
