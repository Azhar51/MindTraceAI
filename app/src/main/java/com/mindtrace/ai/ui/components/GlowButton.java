package com.mindtrace.ai.ui.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

/**
 * Glow Button — A premium MaterialButton with animated shadow glow,
 * gradient background, and press-scale feedback.
 *
 * <p>Design specs from blueprint Part 2A:
 * <ul>
 *   <li>Width: match parent minus 32dp padding</li>
 *   <li>Height: 52dp</li>
 *   <li>Background: Gradient #7C8FFF → #6366F1 (left to right)</li>
 *   <li>Corner radius: 16dp</li>
 *   <li>Text: 16sp bold, white</li>
 *   <li>Glow: soft shadow of #7C8FFF at 30% opacity, 12dp spread</li>
 *   <li>Press animation: Scale to 0.97 + darken 10% (80ms)</li>
 *   <li>Idle glow: shadow opacity pulsates between 20%–40% over 2s</li>
 * </ul>
 *
 * <p>Usage in XML:
 * <pre>
 *   &lt;com.mindtrace.ai.ui.components.GlowButton
 *       android:id="@+id/btnBeginReset"
 *       android:layout_width="match_parent"
 *       android:layout_height="52dp"
 *       android:text="⚡ Begin My Reset"
 *       android:textColor="#FFFFFF"
 *       android:textSize="16sp" /&gt;
 * </pre>
 */
public class GlowButton extends MaterialButton {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    private static final int PRIMARY_COLOR      = 0xFF7C8FFF;
    private static final int PRIMARY_DARK_COLOR  = 0xFF6366F1;
    private static final int GLOW_COLOR          = 0xFF7C8FFF;
    private static final float CORNER_RADIUS_DP  = 16f;
    private static final float PRESSED_SCALE     = 0.97f;
    private static final long PRESS_DURATION     = 80L;
    private static final long GLOW_CYCLE_MS      = 2000L;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();

    private ValueAnimator glowAnimator;
    private float glowAlpha = 0.25f;          // Current glow opacity (0.0–1.0)
    private boolean glowEnabled = true;
    private boolean isCustomDrawEnabled = true;

    private int gradientStart = PRIMARY_COLOR;
    private int gradientEnd = PRIMARY_DARK_COLOR;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    public GlowButton(@NonNull Context context) {
        this(context, null);
    }

    public GlowButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialButtonStyle);
    }

    public GlowButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float dp = getResources().getDisplayMetrics().density;

        // Disable default Material background to draw our own
        setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));

        // Configure text defaults
        setTextColor(Color.WHITE);
        setAllCaps(false);

        // Setup glow paint
        glowPaint.setStyle(Paint.Style.FILL);

        // Setup background paint
        bgPaint.setStyle(Paint.Style.FILL);

        // Enable software rendering for shadow/glow
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // Start pulsing glow animation
        startGlowAnimation();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Set custom gradient colors for the button background.
     *
     * @param startColor Left color of the gradient
     * @param endColor   Right color of the gradient
     */
    public void setGradientColors(int startColor, int endColor) {
        this.gradientStart = startColor;
        this.gradientEnd = endColor;
        invalidate();
    }

    /**
     * Enable or disable the pulsing glow effect.
     */
    public void setGlowEnabled(boolean enabled) {
        this.glowEnabled = enabled;
        if (enabled) {
            startGlowAnimation();
        } else {
            stopGlowAnimation();
        }
        invalidate();
    }

    /**
     * Enable or disable the custom drawing (gradient + glow).
     * When disabled, the button behaves like a standard MaterialButton.
     */
    public void setCustomDrawEnabled(boolean enabled) {
        this.isCustomDrawEnabled = enabled;
        invalidate();
    }

    /**
     * Set the button to "danger" mode (red gradient + red glow).
     */
    public void setDangerMode() {
        gradientStart = 0xFFFF6B6B;
        gradientEnd = 0xFFE04040;
        invalidate();
    }

    /**
     * Set the button to "success" mode (green gradient + green glow).
     */
    public void setSuccessMode() {
        gradientStart = 0xFF4ADE80;
        gradientEnd = 0xFF2A8B5A;
        invalidate();
    }

    /**
     * Reset to default primary colors.
     */
    public void setPrimaryMode() {
        gradientStart = PRIMARY_COLOR;
        gradientEnd = PRIMARY_DARK_COLOR;
        invalidate();
    }

    // ═══════════════════════════════════════════════════════════════════
    // GLOW ANIMATION (Pulsing shadow)
    // ═══════════════════════════════════════════════════════════════════

    private void startGlowAnimation() {
        if (glowAnimator != null && glowAnimator.isRunning()) {
            return;
        }

        glowAnimator = ValueAnimator.ofFloat(0.20f, 0.40f);
        glowAnimator.setDuration(GLOW_CYCLE_MS);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        glowAnimator.addUpdateListener(animation -> {
            glowAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        glowAnimator.start();
    }

    private void stopGlowAnimation() {
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRESS ANIMATION (Scale feedback)
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                animatePress(true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animatePress(false);
                break;
        }
        return super.onTouchEvent(event);
    }

    private void animatePress(boolean pressed) {
        float target = pressed ? PRESSED_SCALE : 1.0f;
        animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(PRESS_DURATION)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    // ═══════════════════════════════════════════════════════════════════
    // DRAWING
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!isCustomDrawEnabled) {
            super.onDraw(canvas);
            return;
        }

        float dp = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        float cornerR = CORNER_RADIUS_DP * dp;

        // ── 1. Glow shadow layer (below the button) ──
        if (glowEnabled) {
            float glowSpread = dp * 12;
            int alpha = (int) (glowAlpha * 255);
            glowPaint.setColor(withAlpha(gradientStart, alpha));

            // Use setShadowLayer for a soft outer glow
            bgPaint.setShadowLayer(
                glowSpread,   // blur radius
                0f,           // dx
                dp * 4,       // dy (slight downward shift)
                withAlpha(gradientStart, alpha)
            );
        }

        // ── 2. Gradient background ──
        bgRect.set(0, 0, w, h);
        LinearGradient gradient = new LinearGradient(
            0, 0, w, 0,
            gradientStart, gradientEnd,
            Shader.TileMode.CLAMP
        );
        bgPaint.setShader(gradient);
        canvas.drawRoundRect(bgRect, cornerR, cornerR, bgPaint);
        bgPaint.setShader(null);
        bgPaint.clearShadowLayer();

        // ── 3. Subtle inner highlight (glass edge at top) ──
        Paint innerHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerHighlight.setStyle(Paint.Style.STROKE);
        innerHighlight.setStrokeWidth(dp * 1);
        innerHighlight.setColor(withAlpha(0xFFFFFFFF, 20)); // 8% white
        RectF innerRect = new RectF(dp * 0.5f, dp * 0.5f, w - dp * 0.5f, h - dp * 0.5f);
        canvas.drawRoundRect(innerRect, cornerR, cornerR, innerHighlight);

        // ── 4. Draw the text (handled by super) ──
        super.onDraw(canvas);
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (glowEnabled) {
            startGlowAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopGlowAnimation();
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
