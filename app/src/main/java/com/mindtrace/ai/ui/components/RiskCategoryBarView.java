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
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Risk Category Bar — a single horizontal bar representing one of the
 * 6 psychological risk categories.
 *
 * <p>Layout (top to bottom):
 * <pre>
 *   Category Name                     SEVERITY
 *   ━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━━━  (fill bar)
 * </pre>
 *
 * <p>The bar animates from 0% to the target fill width using a
 * {@link FastOutSlowInInterpolator}.</p>
 */
public class RiskCategoryBarView extends View {

    // Paints
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint severityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF fillRect = new RectF();

    // Data
    private String categoryName = "";
    private String severityLabel = "";
    private float score = 0f;          // 0.0–1.0
    private float animatedScore = 0f;
    private int fillColor = 0xFF8896B0;
    private int severityColor = 0xFF8896B0;

    // Layout constants
    private static final float BAR_HEIGHT_DP = 6f;
    private static final float CORNER_RADIUS_DP = 3f;
    private static final float TEXT_TOP_PADDING_DP = 0f;
    private static final float BAR_TOP_MARGIN_DP = 6f;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    public RiskCategoryBarView(Context context) { this(context, null); }
    public RiskCategoryBarView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }

    public RiskCategoryBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float dp = getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(0xFF1A2540);

        fillPaint.setStyle(Paint.Style.FILL);

        namePaint.setColor(0xFFE8ECF4);
        namePaint.setTextSize(13f * dp);
        namePaint.setAntiAlias(true);

        severityPaint.setTextSize(12f * dp);
        severityPaint.setFakeBoldText(true);
        severityPaint.setTextAlign(Paint.Align.RIGHT);
        severityPaint.setAntiAlias(true);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Set the bar data and optionally animate.
     *
     * @param name       Category display name (e.g., "Digital Addiction")
     * @param score      Risk score 0.0–1.0
     * @param animate    Whether to animate the fill bar
     * @param delayMs    Animation start delay (for stagger effect)
     */
    public void setData(@NonNull String name, float score, boolean animate, long delayMs) {
        this.categoryName = name;
        this.score = Math.max(0f, Math.min(1f, score));
        mapSeverity(this.score);

        if (!animate) {
            this.animatedScore = this.score;
            invalidate();
            return;
        }

        ValueAnimator anim = ValueAnimator.ofFloat(0f, this.score);
        anim.setDuration(500L);
        anim.setStartDelay(delayMs);
        anim.setInterpolator(new FastOutSlowInInterpolator());
        anim.addUpdateListener(a -> {
            animatedScore = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    /** Convenience: set data without delay. */
    public void setData(@NonNull String name, float score, boolean animate) {
        setData(name, score, animate, 0L);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEVERITY MAPPING
    // ═══════════════════════════════════════════════════════════════════

    private void mapSeverity(float score) {
        if (score <= 0.10f) {
            severityLabel = "NONE";
            fillColor = 0xFF1A2540;
            severityColor = 0xFF8896B0;
        } else if (score <= 0.30f) {
            severityLabel = "MILD";
            fillColor = 0xFF4ADE80;
            severityColor = 0xFF4ADE80;
        } else if (score <= 0.50f) {
            severityLabel = "WATCH";
            fillColor = 0xFFD4C84A;
            severityColor = 0xFFD4C84A;
        } else if (score <= 0.70f) {
            severityLabel = "MOD";
            fillColor = 0xFFF5A623;
            severityColor = 0xFFF5A623;
        } else if (score <= 0.85f) {
            severityLabel = "HIGH";
            fillColor = 0xFFE07040;
            severityColor = 0xFFE07040;
        } else {
            severityLabel = "SEVERE";
            fillColor = 0xFFFF6B6B;
            severityColor = 0xFFFF6B6B;
        }
        fillPaint.setColor(fillColor);
        severityPaint.setColor(severityColor);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MEASURE + DRAW
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float dp = getResources().getDisplayMetrics().density;
        float textHeight = namePaint.getTextSize();
        float barHeight = BAR_HEIGHT_DP * dp;
        float totalHeight = textHeight + (BAR_TOP_MARGIN_DP * dp) + barHeight + (4f * dp);
        setMeasuredDimension(width, (int) totalHeight);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float dp = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float barHeight = BAR_HEIGHT_DP * dp;
        float cornerRadius = CORNER_RADIUS_DP * dp;

        // Row 1: Category name (left) + severity label (right)
        float textY = namePaint.getTextSize();
        canvas.drawText(categoryName, 0, textY, namePaint);
        canvas.drawText(severityLabel, w, textY, severityPaint);

        // Row 2: Track + fill bar
        float barTop = textY + (BAR_TOP_MARGIN_DP * dp);

        // Track (full width)
        trackRect.set(0, barTop, w, barTop + barHeight);
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);

        // Fill bar (animated width)
        if (animatedScore > 0.01f) {
            float fillWidth = w * animatedScore;
            fillRect.set(0, barTop, fillWidth, barTop + barHeight);

            // Gradient fill: darker version → full color
            int darkColor = darken(fillColor, 0.6f);
            fillPaint.setShader(new LinearGradient(
                    0, barTop, fillWidth, barTop,
                    darkColor, fillColor,
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, fillPaint);
            fillPaint.setShader(null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private int darken(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
