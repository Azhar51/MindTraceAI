package com.mindtrace.ai.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Shared UI utility methods used across Overview delegates and fragments.
 * Centralises {@code translucent}, {@code dp}, and {@code performHaptic}
 * so they are defined in exactly one place.
 */
public final class ViewUtils {

    private ViewUtils() { /* no instances */ }

    /**
     * Returns the given colour with a replaced alpha channel.
     *
     * @param color base ARGB colour
     * @param alpha new alpha value (0–255)
     */
    public static int translucent(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Converts dp to pixels using the current display density.
     */
    public static float dp(Context context, int value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    /**
     * Fires a one-shot haptic vibration.
     *
     * @param context   application or activity context
     * @param durationMs vibration duration in milliseconds
     */
    public static void performHaptic(Context context, long durationMs) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMs);
            }
        } catch (Exception ignored) {
            // Ignore haptic failures on unsupported devices.
        }
    }
}
