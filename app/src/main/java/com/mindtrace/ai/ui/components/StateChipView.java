package com.mindtrace.ai.ui.components;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.appcompat.widget.AppCompatTextView;

import com.mindtrace.ai.ui.theme.ColorSystem;

public class StateChipView extends AppCompatTextView {
    public StateChipView(Context context) {
        super(context);
        init();
    }

    public StateChipView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StateChipView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int horizontal = dp(14);
        int vertical = dp(7);
        setPadding(horizontal, vertical, horizontal, vertical);
        setTextSize(12f);
        setAllCaps(false);
        setChipColors(ColorSystem.TRACK, ColorSystem.TEXT_PRIMARY);
    }

    public void applyRiskLabel(String label) {
        setText(label);
        if (label == null) {
            setChipColors(adjustAlpha(ColorSystem.TRACK, 0.75f), ColorSystem.TEXT_PRIMARY);
            return;
        }

        if (label.contains("HIGH")) {
            setChipColors(adjustAlpha(ColorSystem.RED, 0.18f), ColorSystem.RED);
        } else if (label.contains("MODERATE")) {
            setChipColors(adjustAlpha(ColorSystem.AMBER, 0.18f), ColorSystem.AMBER);
        } else {
            setChipColors(adjustAlpha(ColorSystem.GREEN, 0.18f), ColorSystem.GREEN);
        }
    }

    public void applyBehaviorLabel(String label) {
        setText(label);
        if (label == null) {
            setChipColors(adjustAlpha(ColorSystem.TRACK, 0.75f), ColorSystem.TEXT_PRIMARY);
            return;
        }

        if (label.contains("Heavy")
                || label.contains("Binge")
                || label.contains("Loop")
                || label.contains("High")) {
            setChipColors(adjustAlpha(ColorSystem.RED, 0.18f), ColorSystem.RED);
        } else if (label.contains("Mild")
                || label.contains("concentration")
                || label.contains("Concentration")
                || label.contains("Moderate")) {
            setChipColors(adjustAlpha(ColorSystem.AMBER, 0.18f), ColorSystem.AMBER);
        } else {
            setChipColors(adjustAlpha(ColorSystem.GREEN, 0.18f), ColorSystem.GREEN);
        }
    }

    public void applyNeutralLabel(String label) {
        setText(label);
        setChipColors(adjustAlpha(ColorSystem.PRIMARY, 0.14f), ColorSystem.TEXT_PRIMARY);
    }

    public void setChipColors(@ColorInt int backgroundColor, @ColorInt int textColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(backgroundColor);
        drawable.setCornerRadius(dp(999));
        drawable.setStroke(dp(1), adjustAlpha(textColor, 0.2f));
        setBackground(drawable);
        setTextColor(textColor);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(android.graphics.Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
