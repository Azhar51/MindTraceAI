package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Premium shimmer skeleton loading view.
 *
 * <p>Displays a subtle shimmer animation over a dark surface,
 * creating a high-end loading placeholder effect. Can be used
 * as an overlay or standalone skeleton block.</p>
 *
 * <p>Usage: Add to layout, call {@link #startShimmer()} when loading,
 * and {@link #stopShimmer()} when data arrives.</p>
 */
public class ShimmerView extends View {

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix shimmerMatrix = new Matrix();

    private ValueAnimator shimmerAnimator;
    private float shimmerTranslateX = 0f;
    private float cornerRadius;

    private static final int COLOR_BASE = 0xFF131D30;
    private static final int COLOR_SHIMMER_DARK = 0x00FFFFFF;
    private static final int COLOR_SHIMMER_LIGHT = 0x18FFFFFF;

    public ShimmerView(Context context) { this(context, null); }
    public ShimmerView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }

    public ShimmerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float dp = getResources().getDisplayMetrics().density;
        cornerRadius = 12f * dp;

        basePaint.setColor(COLOR_BASE);
        basePaint.setStyle(Paint.Style.FILL);
    }

    public void setCornerRadius(float radiusDp) {
        this.cornerRadius = radiusDp * getResources().getDisplayMetrics().density;
        invalidate();
    }

    public void startShimmer() {
        if (shimmerAnimator != null && shimmerAnimator.isRunning()) return;

        shimmerAnimator = ValueAnimator.ofFloat(-1f, 2f);
        shimmerAnimator.setDuration(1800);
        shimmerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shimmerAnimator.setInterpolator(new LinearInterpolator());
        shimmerAnimator.addUpdateListener(a -> {
            shimmerTranslateX = (float) a.getAnimatedValue();
            invalidate();
        });
        shimmerAnimator.start();
        setVisibility(VISIBLE);
    }

    public void stopShimmer() {
        if (shimmerAnimator != null) {
            shimmerAnimator.cancel();
            shimmerAnimator = null;
        }
        setVisibility(GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopShimmer();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        rect.set(0, 0, w, h);

        // Base dark rectangle
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, basePaint);

        // Shimmer gradient overlay (moving)
        float shimmerWidth = w * 0.6f;
        float translateX = shimmerTranslateX * w;

        LinearGradient shimmerGradient = new LinearGradient(
                0, 0, shimmerWidth, 0,
                new int[]{COLOR_SHIMMER_DARK, COLOR_SHIMMER_LIGHT, COLOR_SHIMMER_DARK},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);

        shimmerMatrix.setTranslate(translateX, 0);
        shimmerGradient.setLocalMatrix(shimmerMatrix);
        shimmerPaint.setShader(shimmerGradient);

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, shimmerPaint);
    }
}
