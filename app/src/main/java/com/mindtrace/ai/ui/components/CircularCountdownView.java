package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * CircularCountdownView — draws a circular arc that fills over a specified
 * duration, providing a visual countdown timer.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * countdownView.setColors(0xFF66BB6A, 0x331A2540);
 * countdownView.startCountdown(60000); // 60 seconds
 * }</pre>
 *
 * <p>Used in CrisisLockdownActivity to show a visual progress ring
 * around the dismiss button area during the 60-second lockdown period.</p>
 */
public class CircularCountdownView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float sweepAngle = 0f;  // 0 to 360
    private ValueAnimator countdownAnimator;
    private Runnable onCompleteCallback;

    // Visual properties
    private int arcColor = 0xFF66BB6A;      // Green arc
    private int trackColor = 0x201A2540;    // Dark track
    private float strokeWidth;

    public CircularCountdownView(Context context) {
        super(context);
        init();
    }

    public CircularCountdownView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularCountdownView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokeWidth = dpToPx(4);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(trackColor);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeWidth);
        arcPaint.setColor(arcColor);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Set the arc and track colors.
     */
    public void setColors(int arc, int track) {
        this.arcColor = arc;
        this.trackColor = track;
        arcPaint.setColor(arcColor);
        trackPaint.setColor(trackColor);
        invalidate();
    }

    /**
     * Set the on-complete callback.
     */
    public void setOnCompleteListener(Runnable callback) {
        this.onCompleteCallback = callback;
    }

    /**
     * Start the countdown animation.
     *
     * @param durationMs Total duration in milliseconds
     */
    public void startCountdown(long durationMs) {
        if (countdownAnimator != null) countdownAnimator.cancel();

        countdownAnimator = ValueAnimator.ofFloat(0f, 360f);
        countdownAnimator.setDuration(durationMs);
        countdownAnimator.setInterpolator(new LinearInterpolator());
        countdownAnimator.addUpdateListener(a -> {
            sweepAngle = (float) a.getAnimatedValue();
            invalidate();
        });
        countdownAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (onCompleteCallback != null) {
                    onCompleteCallback.run();
                }
            }
        });
        countdownAnimator.start();
    }

    /**
     * Get current progress as a fraction (0.0 to 1.0).
     */
    public float getProgress() {
        return sweepAngle / 360f;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float padding = strokeWidth / 2f + dpToPx(2);
        arcRect.set(padding, padding,
                getWidth() - padding, getHeight() - padding);

        // Track (full circle)
        canvas.drawArc(arcRect, -90, 360, false, trackPaint);

        // Progress arc (starts from top, clockwise)
        if (sweepAngle > 0) {
            canvas.drawArc(arcRect, -90, sweepAngle, false, arcPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (countdownAnimator != null) {
            countdownAnimator.cancel();
            countdownAnimator = null;
        }
    }
}
