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
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Risk Gauge View — Semi-circular arc gauge with gradient and glow.
 *
 * <p>This is the hero element of the Overview Command Center (Part 2A).
 * It displays the user's overall risk index (0–100) as a premium
 * semi-circle arc with dynamic color gradients and a glowing tip.
 *
 * <p>Design specs from blueprint Part 2A:
 * <ul>
 *   <li>Shape: Semi-circle arc (180°), opening at bottom</li>
 *   <li>Size: 160dp × 90dp (half circle)</li>
 *   <li>Track: 14dp thick, #1A2540 (dark track)</li>
 *   <li>Arc: 14dp thick, gradient green→amber→red</li>
 *   <li>Fill: proportional to risk score (0–100)</li>
 *   <li>Glow: soft 16dp blur of arc color at 40% opacity at arc tip</li>
 *   <li>Center number: 48sp, weight 900, color matches arc tip</li>
 *   <li>Label: "Risk Index" — 13sp, #8896B0</li>
 *   <li>Animation: 800ms sweep with OvershootInterpolator(1.2)</li>
 * </ul>
 */
public class RiskGaugeView extends View {

    // ═══════════════════════════════════════════════════════════════════
    // PAINTS
    // ═══════════════════════════════════════════════════════════════════

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scoreTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wellnessLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    private int riskScore = 0;               // 0–100
    private float animatedFraction = 0f;     // 0.0–1.0 (animated)
    private float targetFraction = 0f;       // 0.0–1.0 (target)

    private String wellnessLabel = "Balanced";
    private int tipColor = 0xFF4ADE80;       // Current arc tip color
    private int scoreColor = 0xFF4ADE80;     // Color for the center number

    // Arc geometry
    private static final float ARC_START_ANGLE = 180f;   // Left side (9 o'clock)
    private static final float ARC_SWEEP_MAX = 180f;     // Full semi-circle
    private static final float STROKE_WIDTH_DP = 14f;

    // Gradient color stops (green → amber → red across the arc)
    private static final int COLOR_GREEN      = 0xFF4ADE80;
    private static final int COLOR_GREEN_MID  = 0xFF7CC87F;
    private static final int COLOR_AMBER      = 0xFFF5A623;
    private static final int COLOR_ORANGE     = 0xFFE07040;
    private static final int COLOR_RED        = 0xFFFF6B6B;

    // Listener
    private OnRiskLevelChangeListener listener;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    public RiskGaugeView(Context context) {
        this(context, null);
    }

    public RiskGaugeView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RiskGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    private void initPaints() {
        float dp = getResources().getDisplayMetrics().density;

        // Track (dark background arc)
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(0xFF1A2540);
        trackPaint.setStrokeWidth(STROKE_WIDTH_DP * dp);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        // Arc (gradient progress)
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(STROKE_WIDTH_DP * dp);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        // Glow (wider, translucent arc behind main arc)
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth((STROKE_WIDTH_DP + 10) * dp);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        // Tip glow (radial glow at arc endpoint)
        tipGlowPaint.setStyle(Paint.Style.FILL);

        // Score text (large center number)
        scoreTextPaint.setTextAlign(Paint.Align.CENTER);
        scoreTextPaint.setFakeBoldText(true);
        scoreTextPaint.setColor(0xFF4ADE80);

        // "Risk Index" label
        labelTextPaint.setTextAlign(Paint.Align.CENTER);
        labelTextPaint.setColor(0xFF8896B0);

        // Wellness label (e.g., "Balanced", "Watch Mode")
        wellnessLabelPaint.setTextAlign(Paint.Align.CENTER);
        wellnessLabelPaint.setFakeBoldText(true);

        // Enable software layer for glow/blur effects
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Set the risk score (0–100) and optionally animate the arc sweep.
     *
     * @param score   Risk index from 0 (balanced) to 100 (critical)
     * @param animate Whether to animate from current position
     */
    public void setRiskScore(int score, boolean animate) {
        this.riskScore = Math.max(0, Math.min(100, score));
        this.targetFraction = this.riskScore / 100f;
        updateColors();

        if (!animate) {
            this.animatedFraction = this.targetFraction;
            invalidate();
            notifyListener();
            return;
        }

        float from = this.animatedFraction;
        ValueAnimator animator = ValueAnimator.ofFloat(from, this.targetFraction);
        animator.setDuration(800L);
        animator.setInterpolator(new OvershootInterpolator(1.2f));
        animator.addUpdateListener(a -> {
            animatedFraction = (float) a.getAnimatedValue();
            // Clamp to valid range after overshoot
            animatedFraction = Math.max(0f, Math.min(1.0f, animatedFraction));
            invalidate();
        });
        animator.start();
        notifyListener();
    }

    /** Get the current risk score (0–100). */
    public int getRiskScore() {
        return riskScore;
    }

    /** Get the current wellness label text (e.g., "Balanced", "Elevated"). */
    public String getWellnessLabel() {
        return wellnessLabel;
    }

    /** Get the current arc tip color. */
    public int getTipColor() {
        return tipColor;
    }

    /** Set a listener for risk level changes. */
    public void setOnRiskLevelChangeListener(OnRiskLevelChangeListener listener) {
        this.listener = listener;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COLOR LOGIC (from blueprint)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Maps risk score to arc tip color, number color, and wellness label.
     *
     * | Score  | Arc Color         | Number Color  | Label        |
     * |--------|-------------------|---------------|--------------|
     * | 0-30   | #4ADE80 (green)   | #4ADE80       | "Balanced"   |
     * | 31-49  | #7CC87F           | #E8ECF4       | "Steady"     |
     * | 50-64  | #F5A623 (amber)   | #F5A623       | "Watch Mode" |
     * | 65-79  | #E07040           | #E07040       | "Elevated"   |
     * | 80-100 | #FF6B6B (red)     | #FF6B6B       | "High Risk"  |
     */
    private void updateColors() {
        if (riskScore <= 30) {
            tipColor = COLOR_GREEN;
            scoreColor = COLOR_GREEN;
            wellnessLabel = "Balanced";
        } else if (riskScore <= 49) {
            tipColor = COLOR_GREEN_MID;
            scoreColor = 0xFFE8ECF4; // Ice white
            wellnessLabel = "Steady";
        } else if (riskScore <= 64) {
            tipColor = COLOR_AMBER;
            scoreColor = COLOR_AMBER;
            wellnessLabel = "Watch Mode";
        } else if (riskScore <= 79) {
            tipColor = COLOR_ORANGE;
            scoreColor = COLOR_ORANGE;
            wellnessLabel = "Elevated";
        } else {
            tipColor = COLOR_RED;
            scoreColor = COLOR_RED;
            wellnessLabel = "High Risk";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MEASURE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float dp = getResources().getDisplayMetrics().density;
        float strokeW = STROKE_WIDTH_DP * dp;

        // Height = radius + stroke + glow margin + space for text below
        float radius = (width - strokeW * 2 - dp * 4) / 2f;
        float textAreaHeight = dp * 70; // Space for score number + label + wellness label
        int height = (int) (radius + strokeW + dp * 12 + textAreaHeight);

        setMeasuredDimension(width, height);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DRAW
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float dp = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float strokeW = STROKE_WIDTH_DP * dp;
        float cx = w / 2f;

        // Calculate arc rect (semi-circle at top of view)
        float margin = strokeW + dp * 8; // Extra margin for glow
        float arcDiameter = w - margin * 2;
        float radius = arcDiameter / 2f;
        float arcTop = dp * 8;
        arcRect.set(
            cx - radius,
            arcTop,
            cx + radius,
            arcTop + arcDiameter
        );

        float arcCenterY = arcTop + radius;

        // ── 1. Background track arc (full 180°) ──
        canvas.drawArc(arcRect, ARC_START_ANGLE, ARC_SWEEP_MAX, false, trackPaint);

        // ── 2. Progress arc with gradient + glow ──
        float sweepAngle = ARC_SWEEP_MAX * animatedFraction;
        if (sweepAngle > 0.5f) {
            // Create a multi-stop sweep gradient across the arc
            // The gradient spans the entire 360° but we only draw 180° (top semicircle)
            SweepGradient gradient = new SweepGradient(cx, arcCenterY,
                new int[]{
                    COLOR_GREEN,      // 0° (right, 3 o'clock) — but we start at 180°
                    COLOR_GREEN,      // padding before arc starts
                    COLOR_GREEN,      // arc start (180° = left)
                    COLOR_GREEN_MID,  // ~210°
                    COLOR_AMBER,      // ~270° (top)
                    COLOR_ORANGE,     // ~330°
                    COLOR_RED,        // 360°/0° (right = arc end)
                    COLOR_RED         // padding after arc
                },
                new float[]{
                    0.0f,     // 0°
                    0.49f,    // just before 180°
                    0.50f,    // 180° — arc start
                    0.60f,    // ~216°
                    0.75f,    // ~270° (top)
                    0.90f,    // ~324°
                    1.0f,     // 360° — arc end
                    1.0f
                }
            );

            // Glow layer (wider + translucent)
            glowPaint.setStrokeWidth(strokeW + dp * 12);
            glowPaint.setShader(gradient);
            glowPaint.setAlpha(65); // ~25% opacity
            canvas.drawArc(arcRect, ARC_START_ANGLE, sweepAngle, false, glowPaint);
            glowPaint.setAlpha(255);

            // Main arc
            arcPaint.setShader(gradient);
            canvas.drawArc(arcRect, ARC_START_ANGLE, sweepAngle, false, arcPaint);
            arcPaint.setShader(null);

            // ── 3. Tip glow (soft radial glow at arc endpoint) ──
            drawTipGlow(canvas, cx, arcCenterY, radius, sweepAngle, dp);
        }

        // ── 4. Center score number ──
        float scoreY = arcCenterY + dp * 2;
        scoreTextPaint.setTextSize(dp * 48);
        scoreTextPaint.setColor(scoreColor);
        Paint.FontMetrics fm = scoreTextPaint.getFontMetrics();
        float scoreTextY = scoreY - (fm.ascent + fm.descent) / 2f;
        String scoreStr = String.valueOf(Math.round(animatedFraction * 100));
        canvas.drawText(scoreStr, cx, scoreTextY, scoreTextPaint);

        // ── 5. "Risk Index" label below number ──
        labelTextPaint.setTextSize(dp * 13);
        float labelY = scoreTextY + dp * 20;
        canvas.drawText("Risk Index", cx, labelY, labelTextPaint);

        // ── 6. Wellness label below that ──
        wellnessLabelPaint.setTextSize(dp * 14);
        wellnessLabelPaint.setColor(tipColor);
        float wellnessY = labelY + dp * 22;
        canvas.drawText(wellnessLabel, cx, wellnessY, wellnessLabelPaint);
    }

    /**
     * Draw a soft glowing dot at the tip of the arc.
     */
    private void drawTipGlow(Canvas canvas, float cx, float cy, float radius,
                              float sweepAngle, float dp) {
        // Calculate tip position on the arc
        double angle = Math.toRadians(ARC_START_ANGLE + sweepAngle);
        float tipX = cx + radius * (float) Math.cos(angle);
        float tipY = cy + radius * (float) Math.sin(angle);

        // Outer glow (large, very translucent)
        tipGlowPaint.setColor(withAlpha(tipColor, 25));
        canvas.drawCircle(tipX, tipY, dp * 20, tipGlowPaint);

        // Mid glow
        tipGlowPaint.setColor(withAlpha(tipColor, 50));
        canvas.drawCircle(tipX, tipY, dp * 12, tipGlowPaint);

        // Inner bright dot
        tipGlowPaint.setColor(withAlpha(tipColor, 180));
        canvas.drawCircle(tipX, tipY, dp * 4, tipGlowPaint);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onRiskLevelChanged(riskScore, wellnessLabel, tipColor);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LISTENER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Callback when the risk score/level changes.
     */
    public interface OnRiskLevelChangeListener {
        /**
         * @param score     Current risk score (0–100)
         * @param label     Wellness label ("Balanced", "Elevated", etc.)
         * @param tipColor  The color of the arc tip
         */
        void onRiskLevelChanged(int score, String label, int tipColor);
    }
}
