package com.mindtrace.ai.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Provides centralized haptic feedback patterns to create a premium, delightful tactile experience.
 */
public class UiHaptics {

    /**
     * Subtle tick, like scrolling a wheel or dragging a slider.
     */
    public static void tick(View view) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    /**
     * Solid click, for primary button presses.
     */
    public static void click(View view) {
        if (view == null) return;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /**
     * Celebration / Success pattern (e.g., finishing a focus session).
     */
    public static void success(Context context) {
        if (context == null) return;
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
            vibrator.vibrate(effect);
            // Delay and second click for a "da-ding" feel
            viewDelayedHaptic(vibrator, effect, 150);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 20, 100, 30}, -1));
        } else {
            vibrator.vibrate(new long[]{0, 20, 100, 30}, -1);
        }
    }

    /**
     * Warning or error pattern (e.g., trying to open a blocked app).
     */
    public static void warning(Context context) {
        if (context == null) return;
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 50, 50}, -1));
        } else {
            vibrator.vibrate(new long[]{0, 50, 50, 50}, -1);
        }
    }

    private static Vibrator getVibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    private static void viewDelayedHaptic(Vibrator vibrator, VibrationEffect effect, long delayMillis) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                vibrator.vibrate(effect);
            } catch (Exception ignored) {}
        }, delayMillis);
    }
}
