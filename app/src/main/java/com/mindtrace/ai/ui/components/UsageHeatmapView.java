package com.mindtrace.ai.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Diverging timeline chart for 24 hours.
 * Shows Productive time (pointing UP, cyan) and Passive time (pointing DOWN, red).
 */
public class UsageHeatmapView extends View {
    private static final int HOUR_COUNT = 24;
    private static final float BAR_CORNER_RADIUS_DP = 4f;
    private static final float BAR_GAP_DP = 2f;

    private final Paint prodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint passPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();
    
    private float[] productiveMins = new float[HOUR_COUNT];
    private float[] passiveMins = new float[HOUR_COUNT];
    private float[] animProd = new float[HOUR_COUNT];
    private float[] animPass = new float[HOUR_COUNT];
    
    private int selectedHour = -1;
    private OnHourSelectedListener listener;
    private android.animation.ValueAnimator animator;

    private final int colorProductive = Color.parseColor("#4DEEEA");
    private final int colorPassive = Color.parseColor("#FF6B6B");
    private final int colorAxis = Color.parseColor("#2A354E");
    private final int colorSelectedHighlight = Color.parseColor("#FFFFFF");

    public interface OnHourSelectedListener {
        void onHourSelected(int hour, float minutes);
    }

    public UsageHeatmapView(Context context) {
        super(context);
        init();
    }

    public UsageHeatmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UsageHeatmapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        
        prodPaint.setColor(colorProductive);
        prodPaint.setShadowLayer(dp(6f), 0, 0, colorProductive);
        
        passPaint.setColor(colorPassive);
        passPaint.setShadowLayer(dp(6f), 0, 0, colorPassive);
        
        trackPaint.setColor(Color.parseColor("#10FFFFFF")); // Faint white/blue track
        
        axisPaint.setColor(Color.parseColor("#3A4560")); // Brighter axis
        axisPaint.setStrokeWidth(dp(2f));
        axisPaint.setStrokeCap(Paint.Cap.ROUND);

        setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN
                    || event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                float barWidth = (getWidth() - (HOUR_COUNT - 1) * dp(BAR_GAP_DP)) / (float) HOUR_COUNT;
                int hour = (int) (event.getX() / (barWidth + dp(BAR_GAP_DP)));
                hour = Math.max(0, Math.min(HOUR_COUNT - 1, hour));
                if (hour != selectedHour) {
                    selectedHour = hour;
                    invalidate();
                    if (listener != null) {
                        listener.onHourSelected(hour, productiveMins[hour] + passiveMins[hour]);
                    }
                }
                return true;
            }
            return false;
        });
    }

    public void setDivergingData(float[] productive, float[] passive) {
        if (productive == null || passive == null) return;
        this.productiveMins = productive.clone();
        this.passiveMins = passive.clone();
        selectedHour = -1;
        animateBars();
    }
    
    // Legacy support for backward compatibility if needed temporarily
    public void setData(float[] hourlyMinutes) {
        setDivergingData(hourlyMinutes, new float[HOUR_COUNT]);
    }

    private void animateBars() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800L);
        animator.setInterpolator(new android.view.animation.OvershootInterpolator(1.0f));
        animator.addUpdateListener(anim -> {
            float fraction = (float) anim.getAnimatedValue();
            for (int i = 0; i < HOUR_COUNT; i++) {
                animProd[i] = productiveMins[i] * fraction;
                animPass[i] = passiveMins[i] * fraction;
            }
            invalidate();
        });
        animator.start();
    }

    public void setOnHourSelectedListener(OnHourSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float gap = dp(BAR_GAP_DP);
        float radius = dp(BAR_CORNER_RADIUS_DP);
        float barWidth = (getWidth() - (HOUR_COUNT - 1) * gap) / (float) HOUR_COUNT;
        
        float centerY = getHeight() / 2f;
        float maxBarHeight = centerY - dp(8f); // Padding top and bottom
        
        // Draw zero axis (Make it a little longer by drawing it from -dp(4) to width+dp(4))
        canvas.drawLine(-dp(4f), centerY, getWidth() + dp(4f), centerY, axisPaint);

        for (int i = 0; i < HOUR_COUNT; i++) {
            float left = i * (barWidth + gap);
            float right = left + barWidth;
            
            // Draw background track for context
            barRect.set(left, centerY - maxBarHeight, right, centerY + maxBarHeight);
            canvas.drawRoundRect(barRect, radius, radius, trackPaint);
            
            // Productive (UP)
            float prodMins = animProd[i];
            if (prodMins > 0.5f) {
                float h = (Math.min(prodMins, 60f) / 60f) * maxBarHeight;
                barRect.set(left, centerY - h, right, centerY);
                
                prodPaint.setColor(i == selectedHour ? colorSelectedHighlight : colorProductive);
                // Draw rounded top, square bottom
                canvas.drawRoundRect(barRect, radius, radius, prodPaint);
                if (h > radius) {
                    canvas.drawRect(left, centerY - radius, right, centerY, prodPaint);
                }
            }

            // Passive (DOWN)
            float passMins = animPass[i];
            if (passMins > 0.5f) {
                float h = (Math.min(passMins, 60f) / 60f) * maxBarHeight;
                barRect.set(left, centerY, right, centerY + h);
                
                passPaint.setColor(i == selectedHour ? colorSelectedHighlight : colorPassive);
                // Draw rounded bottom, square top
                canvas.drawRoundRect(barRect, radius, radius, passPaint);
                if (h > radius) {
                    canvas.drawRect(left, centerY, right, centerY + radius, passPaint);
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) dp(120f); // Make it slightly taller to accommodate up and down
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
        }
        setMeasuredDimension(width, height);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
