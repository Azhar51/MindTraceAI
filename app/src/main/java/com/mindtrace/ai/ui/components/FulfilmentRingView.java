package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Premium animated circular progress ring for Fulfilment Score.
 *
 * <p>Features:
 * <ul>
 *   <li>Gradient arc fill (accent → accent-light)</li>
 *   <li>Animated fill from 0 → target with decelerate easing</li>
 *   <li>Subtle glow shadow behind the arc</li>
 *   <li>Dark track background ring</li>
 *   <li>Center text: percentage in bold + label below</li>
 * </ul>
 */
public class FulfilmentRingView extends View {

    // Paints
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final RectF glowRect = new RectF();

    // Data
    private float targetPercent = 0f;
    private float animatedPercent = 0f;
    private String label = "Fulfilment";

    // Colors
    private static final int COLOR_TRACK = 0xFF1A2540;
    private static final int COLOR_ARC_START = 0xFF7C8FFF;
    private static final int COLOR_ARC_END = 0xFF6366F1;
    private static final int COLOR_GLOW = 0x307C8FFF;
    private static final int COLOR_PERCENT = 0xFFE8ECF4;
    private static final int COLOR_LABEL = 0xFF8896B0;

    // Dimensions
    private static final float STROKE_WIDTH_DP = 8f;
    private static final float GLOW_EXTRA_DP = 4f;

    public FulfilmentRingView(Context context) { this(context, null); }
    public FulfilmentRingView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }

    public FulfilmentRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float dp = getResources().getDisplayMetrics().density;
        float strokeWidth = STROKE_WIDTH_DP * dp;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(COLOR_TRACK);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeWidth);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(strokeWidth + GLOW_EXTRA_DP * dp * 2);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(COLOR_GLOW);

        percentPaint.setColor(COLOR_PERCENT);
        percentPaint.setTextSize(22f * dp);
        percentPaint.setTextAlign(Paint.Align.CENTER);
        percentPaint.setFakeBoldText(true);

        labelPaint.setColor(COLOR_LABEL);
        labelPaint.setTextSize(10f * dp);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Set the fulfilment percentage and animate from current to target.
     */
    public void setPercent(float percent, boolean animate) {
        this.targetPercent = Math.max(0, Math.min(100, percent));

        if (!animate) {
            this.animatedPercent = this.targetPercent;
            invalidate();
            return;
        }

        ValueAnimator anim = ValueAnimator.ofFloat(animatedPercent, targetPercent);
        anim.setDuration(1200);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(a -> {
            animatedPercent = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = MeasureSpec.getSize(widthMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);
        int min = Math.min(size, hSize > 0 ? hSize : size);
        setMeasuredDimension(min, min);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float dp = getResources().getDisplayMetrics().density;
        float strokeWidth = STROKE_WIDTH_DP * dp;
        float glowExtra = GLOW_EXTRA_DP * dp;
        float padding = strokeWidth / 2 + glowExtra;

        float w = getWidth();
        float h = getHeight();

        // Arc bounds
        arcRect.set(padding, padding, w - padding, h - padding);
        glowRect.set(padding, padding, w - padding, h - padding);

        // Draw track (full circle)
        canvas.drawArc(arcRect, -90, 360, false, trackPaint);

        float sweepAngle = animatedPercent / 100f * 360f;

        if (sweepAngle > 0.5f) {
            // Draw glow behind arc
            canvas.drawArc(glowRect, -90, sweepAngle, false, glowPaint);

            // Gradient shader for arc
            arcPaint.setShader(new LinearGradient(
                    arcRect.left, arcRect.top, arcRect.right, arcRect.bottom,
                    COLOR_ARC_START, COLOR_ARC_END, Shader.TileMode.CLAMP));

            // Draw arc
            canvas.drawArc(arcRect, -90, sweepAngle, false, arcPaint);
            arcPaint.setShader(null);
        }

        // Center text: percentage
        float cx = w / 2f;
        float cy = h / 2f;
        String percentText = Math.round(animatedPercent) + "%";
        Paint.FontMetrics fm = percentPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f - 4 * dp;
        canvas.drawText(percentText, cx, textY, percentPaint);

        // Label below percentage
        canvas.drawText(label, cx, textY + 16 * dp, labelPaint);
    }
}
