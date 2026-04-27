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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * BreathingRingView — premium animated breathing circle with:
 * <ul>
 *   <li>Inner solid circle with radial gradient</li>
 *   <li>Outer glow ring that pulses with the animation</li>
 *   <li>Phase-color transitions (blue→amber→teal→amber)</li>
 *   <li>Smooth color interpolation between phases</li>
 * </ul>
 *
 * <p>Replaces the flat drawable bg_breathing_circle with a
 * fully dynamic, canvas-drawn breathing ring.</p>
 */
public class BreathingRingView extends View {

    private final Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Current scale (0.0 to 1.0)
    private float scale = 0.7f;
    private float glowAlpha = 0.25f;

    // Phase colors
    private int colorInhale = 0xFF5C9DFF;   // Blue
    private int colorHold   = 0xFFFFB74D;   // Amber
    private int colorExhale = 0xFF4ADE80;   // Green/Teal

    // Current color (animated)
    private int currentColor = colorInhale;
    private int targetColor = colorInhale;
    private ValueAnimator colorAnimator;

    public BreathingRingView(Context context) {
        super(context);
        init();
    }

    public BreathingRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BreathingRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        innerPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dpToPx(3));
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set the current breathing scale (0.0 to 1.0).
     * Call this from ValueAnimator updates.
     */
    public void setBreathingScale(float s) {
        this.scale = Math.max(0.3f, Math.min(1.0f, s));
        this.glowAlpha = 0.1f + (scale - 0.3f) * 0.35f;
        invalidate();
    }

    /**
     * Transition to a new phase color over the given duration.
     *
     * @param phase 0=inhale (blue), 1=hold (amber), 2=exhale (teal), 3=hold (amber)
     * @param durationMs Color transition duration
     */
    public void setPhaseColor(int phase, long durationMs) {
        switch (phase) {
            case 0: targetColor = colorInhale; break;
            case 1: targetColor = colorHold;   break;
            case 2: targetColor = colorExhale; break;
            case 3: targetColor = colorHold;   break;
            default: targetColor = colorInhale;
        }
        animateColor(currentColor, targetColor, Math.min(durationMs, 800));
    }

    /**
     * Set custom phase colors.
     */
    public void setPhaseColors(int inhale, int hold, int exhale) {
        this.colorInhale = inhale;
        this.colorHold = hold;
        this.colorExhale = exhale;
    }

    // ═══════════════════════════════════════════════════════════════
    // COLOR ANIMATION
    // ═══════════════════════════════════════════════════════════════

    private void animateColor(int from, int to, long durationMs) {
        if (colorAnimator != null) colorAnimator.cancel();
        colorAnimator = ValueAnimator.ofArgb(from, to);
        colorAnimator.setDuration(durationMs);
        colorAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        colorAnimator.addUpdateListener(a -> {
            currentColor = (int) a.getAnimatedValue();
            invalidate();
        });
        colorAnimator.start();
    }

    // ═══════════════════════════════════════════════════════════════
    // DRAWING
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(cx, cy) * 0.85f;
        float radius = maxRadius * scale;
        float glowRadius = radius * 1.5f;

        // 1. Outer glow — radial gradient from phase color to transparent
        int glowColor = withAlpha(currentColor, (int) (glowAlpha * 180));
        glowPaint.setShader(new RadialGradient(
                cx, cy, glowRadius,
                glowColor,
                withAlpha(currentColor, 0),
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, glowRadius, glowPaint);

        // 2. Outer ring — thin stroke at glow boundary
        ringPaint.setColor(withAlpha(currentColor, (int) (glowAlpha * 120)));
        canvas.drawCircle(cx, cy, radius * 1.15f, ringPaint);

        // 3. Inner circle — radial gradient from bright center to darker edge
        int centerColor = lighten(currentColor, 0.3f);
        innerPaint.setShader(new RadialGradient(
                cx, cy - radius * 0.1f, radius,
                centerColor, currentColor,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, innerPaint);
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private int withAlpha(int color, int alpha) {
        return (Math.min(255, Math.max(0, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private int lighten(int color, float amount) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) + 255 * amount));
        int g = Math.min(255, (int) (((color >> 8)  & 0xFF) + 255 * amount));
        int b = Math.min(255, (int) ((color          & 0xFF) + 255 * amount));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
    }
}
