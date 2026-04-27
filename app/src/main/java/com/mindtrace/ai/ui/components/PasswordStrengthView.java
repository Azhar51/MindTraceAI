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
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Premium animated password strength meter.
 *
 * <p>Displays a horizontal bar that fills with a gradient color
 * based on password strength: red → orange → yellow → green.
 * Includes a text label showing the strength level.</p>
 *
 * <p>Strength levels:
 * <ul>
 *   <li>0-20%: Weak (red)</li>
 *   <li>20-50%: Fair (orange)</li>
 *   <li>50-75%: Good (yellow-green)</li>
 *   <li>75-100%: Strong (green)</li>
 * </ul>
 */
public class PasswordStrengthView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF fillRect = new RectF();

    private float strength = 0f; // 0.0–1.0
    private float animatedStrength = 0f;
    private String label = "";

    private static final float BAR_HEIGHT_DP = 4f;
    private static final float CORNER_DP = 2f;
    private static final int COLOR_TRACK = 0xFF1A2540;
    private static final int COLOR_WEAK = 0xFFFF6B6B;
    private static final int COLOR_FAIR = 0xFFF5A623;
    private static final int COLOR_GOOD = 0xFFD4C84A;
    private static final int COLOR_STRONG = 0xFF4ADE80;

    public PasswordStrengthView(Context context) { this(context, null); }
    public PasswordStrengthView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }

    public PasswordStrengthView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float dp = getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(COLOR_TRACK);

        fillPaint.setStyle(Paint.Style.FILL);

        labelPaint.setTextSize(11f * dp);
        labelPaint.setColor(0xFF8896B0);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
    }

    /**
     * Evaluate a password and set the strength bar accordingly.
     */
    public void evaluatePassword(@Nullable String password) {
        if (password == null || password.isEmpty()) {
            setStrength(0f);
            label = "";
            invalidate();
            return;
        }

        float score = 0f;
        int len = password.length();

        // Length scoring
        if (len >= 6) score += 0.15f;
        if (len >= 8) score += 0.15f;
        if (len >= 12) score += 0.10f;

        // Character diversity
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (hasLower) score += 0.10f;
        if (hasUpper) score += 0.15f;
        if (hasDigit) score += 0.15f;
        if (hasSpecial) score += 0.20f;

        score = Math.min(1f, score);

        // Set label
        if (score < 0.20f) label = "Weak";
        else if (score < 0.50f) label = "Fair";
        else if (score < 0.75f) label = "Good";
        else label = "Strong 💪";

        setStrength(score);
    }

    private void setStrength(float target) {
        this.strength = target;
        ValueAnimator anim = ValueAnimator.ofFloat(animatedStrength, target);
        anim.setDuration(400);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            animatedStrength = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float dp = getResources().getDisplayMetrics().density;
        int height = (int) (BAR_HEIGHT_DP * dp + labelPaint.getTextSize() + 6 * dp);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float dp = getResources().getDisplayMetrics().density;
        float barH = BAR_HEIGHT_DP * dp;
        float corner = CORNER_DP * dp;
        float w = getWidth();

        // Track
        trackRect.set(0, 0, w, barH);
        canvas.drawRoundRect(trackRect, corner, corner, trackPaint);

        // Fill
        if (animatedStrength > 0.01f) {
            float fillW = w * animatedStrength;
            fillRect.set(0, 0, fillW, barH);

            int color = getStrengthColor(animatedStrength);
            int darkColor = darken(color, 0.6f);
            fillPaint.setShader(new LinearGradient(0, 0, fillW, 0,
                    darkColor, color, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(fillRect, corner, corner, fillPaint);
            fillPaint.setShader(null);

            // Update label color to match
            labelPaint.setColor(color);
        }

        // Label text
        if (!label.isEmpty()) {
            canvas.drawText(label, w, barH + labelPaint.getTextSize() + 2 * dp, labelPaint);
        }
    }

    private int getStrengthColor(float s) {
        if (s < 0.20f) return COLOR_WEAK;
        if (s < 0.50f) return COLOR_FAIR;
        if (s < 0.75f) return COLOR_GOOD;
        return COLOR_STRONG;
    }

    private int darken(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
