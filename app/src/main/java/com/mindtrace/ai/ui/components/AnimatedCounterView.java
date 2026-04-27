package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * Animated counter view — smoothly animates number transitions for scores,
 * XP counts, streaks, and percentages.
 *
 * <p>Usage: {@code counterView.animateTo(85); } or {@code counterView.animateTo(85, "%"); }</p>
 */
public class AnimatedCounterView extends AppCompatTextView {

    public interface ValueFormatter {
        @NonNull
        CharSequence format(int value, @NonNull String prefix, @NonNull String suffix);
    }

    private ValueAnimator currentAnimator;
    private int currentValue = 0;
    private String suffix = "";
    private String prefix = "";
    @Nullable
    private ValueFormatter formatter;

    public AnimatedCounterView(@NonNull Context context) {
        super(context);
    }

    public AnimatedCounterView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AnimatedCounterView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Animate from current value to target value.
     *
     * @param target Target value to animate to
     */
    public void animateTo(int target) {
        animateTo(target, "", "", 800);
    }

    /**
     * Animate with suffix (e.g., "%", " XP", " pts").
     */
    public void animateTo(int target, @NonNull String suffix) {
        animateTo(target, "", suffix, 800);
    }

    /**
     * Animate with prefix and suffix.
     */
    public void animateTo(int target, @NonNull String prefix, @NonNull String suffix, int durationMs) {
        this.suffix = suffix;
        this.prefix = prefix;
        this.formatter = null;

        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }

        currentAnimator = ValueAnimator.ofInt(currentValue, target);
        currentAnimator.setDuration(durationMs);
        currentAnimator.setInterpolator(new DecelerateInterpolator());
        currentAnimator.addUpdateListener(animation -> {
            currentValue = (int) animation.getAnimatedValue();
            setText(buildDisplayText(currentValue));
        });
        currentAnimator.start();
    }

    public void animateTo(int target, @Nullable ValueFormatter valueFormatter, int durationMs) {
        this.prefix = "";
        this.suffix = "";
        this.formatter = valueFormatter;

        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }

        currentAnimator = ValueAnimator.ofInt(currentValue, target);
        currentAnimator.setDuration(durationMs);
        currentAnimator.setInterpolator(new DecelerateInterpolator());
        currentAnimator.addUpdateListener(animation -> {
            currentValue = (int) animation.getAnimatedValue();
            setText(buildDisplayText(currentValue));
        });
        currentAnimator.start();
    }

    /**
     * Set value immediately without animation.
     */
    public void setValueImmediate(int value) {
        this.currentValue = value;
        setText(buildDisplayText(currentValue));
    }

    public void setValueImmediate(int value, @Nullable ValueFormatter valueFormatter) {
        this.formatter = valueFormatter;
        this.currentValue = value;
        setText(buildDisplayText(currentValue));
    }

    /**
     * Get current displayed value.
     */
    public int getCurrentValue() {
        return currentValue;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (currentAnimator != null) {
            currentAnimator.cancel();
            currentAnimator = null;
        }
    }

    @NonNull
    private CharSequence buildDisplayText(int value) {
        if (formatter != null) {
            return formatter.format(value, prefix, suffix);
        }
        return prefix + value + suffix;
    }
}
