package com.mindtrace.ai.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Gradient progress bar — renders a multi-color gradient bar (green → yellow → red)
 * for risk index, distress level, fulfilment score.
 *
 * <p>Color direction can be reversed: green-to-red (low-to-high risk) or
 * green-to-red (high-to-low fulfilment).</p>
 */
public class GradientProgressBar extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF fillRect = new RectF();

    private float progress = 0f; // 0.0 to 1.0
    private float animatedProgress = 0f;
    private float cornerRadius = 12f;
    private ValueAnimator animator;
    private boolean reverseGradient = false;

    // Default colors: green → yellow → orange → red
    private int[] gradientColors = {0xFF22C55E, 0xFFF59E0B, 0xFFFF6B6B, 0xFFEF4444};
    private int trackColor = 0xFF1A2540;

    public GradientProgressBar(Context context) {
        super(context);
        init();
    }

    public GradientProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GradientProgressBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(trackColor);
        fillPaint.setStyle(Paint.Style.FILL);
        cornerRadius = dpToPx(6);
    }

    /**
     * Set progress with animation.
     * @param progress 0.0 to 1.0
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        animateToProgress(this.progress);
    }

    /**
     * Set progress immediately without animation.
     */
    public void setProgressImmediate(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.animatedProgress = this.progress;
        invalidate();
    }

    /**
     * Reverse gradient direction (red-to-green instead of green-to-red).
     */
    public void setReverseGradient(boolean reverse) {
        this.reverseGradient = reverse;
        invalidate();
    }

    /**
     * Set custom gradient colors.
     */
    public void setGradientColors(int[] colors) {
        this.gradientColors = colors;
        invalidate();
    }

    /**
     * Set track background color.
     */
    public void setTrackColor(int color) {
        this.trackColor = color;
        trackPaint.setColor(color);
        invalidate();
    }

    private void animateToProgress(float target) {
        if (animator != null && animator.isRunning()) animator.cancel();
        animator = ValueAnimator.ofFloat(animatedProgress, target);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            animatedProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w == 0 || h == 0) return;

        // Draw track
        trackRect.set(0, 0, w, h);
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);

        // Draw filled portion
        float fillWidth = w * animatedProgress;
        if (fillWidth < cornerRadius * 2) fillWidth = Math.min(w, cornerRadius * 2);
        if (animatedProgress <= 0.01f) return;

        fillRect.set(0, 0, fillWidth, h);

        // Create gradient shader
        int[] colors = reverseGradient ? reverseArray(gradientColors) : gradientColors;
        LinearGradient gradient = new LinearGradient(0, 0, w, 0,
                colors, null, Shader.TileMode.CLAMP);
        fillPaint.setShader(gradient);

        canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, fillPaint);
    }

    private int[] reverseArray(int[] arr) {
        int[] reversed = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            reversed[i] = arr[arr.length - 1 - i];
        }
        return reversed;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
