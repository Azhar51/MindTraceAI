package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

public class UsageRingView extends View {
    private static final float START_ANGLE = -90f;
    private static final float STROKE_WIDTH_DP = 12f;
    private static final float TRACK_STROKE_WIDTH_DP = 10f;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private float progress = 0f;
    private float maxProgress = 100f;
    private float displayedProgress = 0f;
    private int arcColor = Color.parseColor("#7C8FFF");
    private int trackColor = Color.parseColor("#1A2540");
    private int warningColor = Color.parseColor("#FF6B6B");
    private boolean overLimit = false;
    private ValueAnimator animator;

    public UsageRingView(Context context) {
        super(context);
        init();
    }

    public UsageRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UsageRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(STROKE_WIDTH_DP * density);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(arcColor);
        // Add neon glow
        arcPaint.setShadowLayer(15f, 0f, 0f, arcColor);
        setLayerType(LAYER_TYPE_SOFTWARE, arcPaint); // Required for shadows to draw properly

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(TRACK_STROKE_WIDTH_DP * density);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(trackColor);
    }

    public void setProgress(float progress, float max) {
        this.progress = Math.max(0f, progress);
        this.maxProgress = Math.max(1f, max);
        this.overLimit = this.progress > this.maxProgress;
        animateTo(Math.min(this.progress / this.maxProgress, 1.0f));
    }

    public void setColors(int arc, int track, int warning) {
        this.arcColor = arc;
        this.trackColor = track;
        this.warningColor = warning;
        trackPaint.setColor(trackColor);
        invalidate();
    }

    private void animateTo(float fraction) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        float startFraction = displayedProgress;
        animator = ValueAnimator.ofFloat(startFraction, fraction);
        animator.setDuration(600L);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(anim -> {
            displayedProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        float inset = STROKE_WIDTH_DP * density / 2f + 4f;
        arcRect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint);

        float sweepAngle = displayedProgress * 360f;
        int activeColor = overLimit ? warningColor : arcColor;
        
        // Dynamic gradient based on progress
        if (displayedProgress > 0) {
            int startColor = Color.parseColor("#4DEEEA");
            int endColor = activeColor;
            android.graphics.SweepGradient gradient = new android.graphics.SweepGradient(
                    arcRect.centerX(), arcRect.centerY(),
                    new int[]{startColor, endColor, startColor},
                    new float[]{0f, displayedProgress, 1f}
            );
            // Rotate gradient to match start angle
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(START_ANGLE, arcRect.centerX(), arcRect.centerY());
            gradient.setLocalMatrix(matrix);
            arcPaint.setShader(gradient);
        } else {
            arcPaint.setShader(null);
        }
        
        arcPaint.setColor(activeColor);
        arcPaint.setShadowLayer(15f, 0f, 0f, activeColor);
        
        canvas.drawArc(arcRect, START_ANGLE, sweepAngle, false, arcPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec)
        );
        setMeasuredDimension(size, size);
    }
}
