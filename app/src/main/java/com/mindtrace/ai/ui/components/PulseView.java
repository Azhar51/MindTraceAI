package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * PulseView — reusable breathing/pulse circle with radial gradient glow.
 * Extracted from BreathingExerciseActivity for reuse across crisis screens,
 * overview cards, and wellness indicators.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * pulseView.setColors(0xFF60A5FA, 0xFF818CF8);
 * pulseView.startPulse(4000); // 4-second inhale cycle
 * }</pre>
 */
public class PulseView extends View {

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float scale = 0.6f;
    private float glowAlpha = 0.3f;
    private ValueAnimator pulseAnimator;

    private int colorPrimary = 0xFF60A5FA;
    private int colorSecondary = 0xFF818CF8;

    public PulseView(Context context) {
        super(context);
        init();
    }

    public PulseView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PulseView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        circlePaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Set pulse colors.
     */
    public void setColors(int primary, int secondary) {
        this.colorPrimary = primary;
        this.colorSecondary = secondary;
        invalidate();
    }

    /**
     * Start pulsing animation.
     * @param cycleDurationMs Duration of one full pulse cycle
     */
    public void startPulse(long cycleDurationMs) {
        if (pulseAnimator != null && pulseAnimator.isRunning()) {
            pulseAnimator.cancel();
        }

        pulseAnimator = ValueAnimator.ofFloat(0.6f, 1.0f);
        pulseAnimator.setDuration(cycleDurationMs / 2);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            scale = (float) animation.getAnimatedValue();
            glowAlpha = 0.15f + (scale - 0.6f) * 0.5f;
            invalidate();
        });
        pulseAnimator.start();
    }

    /**
     * Stop pulsing.
     */
    public void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        scale = 0.6f;
        glowAlpha = 0.3f;
        invalidate();
    }

    /**
     * Set scale manually (0.0 to 1.0).
     */
    public void setScale(float scale) {
        this.scale = Math.max(0f, Math.min(1f, scale));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(cx, cy);
        float radius = maxRadius * scale;

        // Outer glow
        float glowRadius = radius * 1.4f;
        glowPaint.setShader(new RadialGradient(
                cx, cy, glowRadius,
                withAlpha(colorPrimary, (int) (glowAlpha * 255)),
                withAlpha(colorSecondary, 0),
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, glowRadius, glowPaint);

        // Inner circle
        circlePaint.setShader(new RadialGradient(
                cx, cy, radius,
                colorPrimary, colorSecondary,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, circlePaint);
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPulse();
    }
}
