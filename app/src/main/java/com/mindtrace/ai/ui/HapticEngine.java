package com.mindtrace.ai.ui;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.NonNull;

/**
 * Centralized haptic feedback engine — provides consistent tactile feedback
 * across the entire app with intensity levels mapped to UX contexts.
 *
 * <h3>Haptic Levels:</h3>
 * <ul>
 *   <li><b>tick</b> — Ultra-light tap for button presses, toggles</li>
 *   <li><b>light</b> — Subtle feedback for card selections, chip taps</li>
 *   <li><b>medium</b> — Satisfying feedback for task completion, XP gain</li>
 *   <li><b>heavy</b> — Strong buzz for crisis alerts, warnings</li>
 *   <li><b>success</b> — Double-pulse celebration pattern</li>
 *   <li><b>error</b> — Triple-short error pattern</li>
 *   <li><b>breathe</b> — Rhythmic pattern for breathing exercises</li>
 * </ul>
 */
public class HapticEngine {

    private final Vibrator vibrator;
    private boolean enabled = true;

    public HapticEngine(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    /** Enable or disable all haptics. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Ultra-light tap — button press, toggle. */
    public void tick() {
        vibrate(20, 40);
    }

    /** Light feedback — card selection, chip tap. */
    public void light() {
        vibrate(30, 80);
    }

    /** Medium feedback — task completion, achievement. */
    public void medium() {
        vibrate(50, 150);
    }

    /** Heavy feedback — crisis alert, danger warning. */
    public void heavy() {
        vibrate(100, 255);
    }

    /** Double-pulse success — completion, level up. */
    public void success() {
        vibratePattern(new long[]{0, 60, 80, 100}, new int[]{0, 150, 0, 200});
    }

    /** Triple-short error — validation failure, danger. */
    public void error() {
        vibratePattern(new long[]{0, 40, 60, 40, 60, 40}, new int[]{0, 180, 0, 180, 0, 180});
    }

    /** Rhythmic breathing — inhale pulse + exhale release. */
    public void breatheInhale() {
        vibrate(100, 80);
    }

    /** Breathing exhale — softer release. */
    public void breatheExhale() {
        vibrate(60, 40);
    }

    /** Milestone celebration — long satisfying buzz. */
    public void celebration() {
        vibratePattern(new long[]{0, 80, 100, 80, 100, 200},
                new int[]{0, 120, 0, 180, 0, 255});
    }

    /** Streak at risk — urgent nudge. */
    public void streakWarning() {
        vibratePattern(new long[]{0, 100, 80, 100}, new int[]{0, 200, 0, 150});
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    private void vibrate(int durationMs, int amplitude) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    private void vibratePattern(long[] timings, int[] amplitudes) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        } else {
            vibrator.vibrate(timings, -1);
        }
    }
}
