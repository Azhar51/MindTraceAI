package com.mindtrace.ai.behavior;

public final class BehaviorThresholds {
    public static final long DUPLICATE_FOREGROUND_WINDOW_MILLIS = 2_000L;
    public static final long RAPID_SWITCH_WINDOW_MILLIS = 30_000L;
    public static final long SHORT_SESSION_THRESHOLD_MILLIS = 2L * 60L * 1000L;
    public static final long NORMAL_SESSION_THRESHOLD_MILLIS = 15L * 60L * 1000L;
    public static final long BINGE_SESSION_THRESHOLD_MILLIS = 45L * 60L * 1000L;
    public static final long EXTREME_SESSION_THRESHOLD_MILLIS = 90L * 60L * 1000L;
    public static final long LATE_NIGHT_SIGNAL_THRESHOLD_MILLIS = 30L * 60L * 1000L;

    public static final int LATE_NIGHT_START_HOUR = 23;
    public static final int LATE_NIGHT_END_HOUR = 5;
    public static final int LOOP_DETECTION_DEPTH = 6;

    public static final int MILD_SWITCH_THRESHOLD = 15;
    public static final int HIGH_FRAGMENTATION_SWITCH_THRESHOLD = 30;
    public static final int RAPID_SWITCH_SIGNAL_THRESHOLD = 6;
    public static final int HIGH_RAPID_SWITCH_THRESHOLD = 12;
    public static final int SHORT_SESSION_SIGNAL_THRESHOLD = 12;

    public static final double DOMINANT_APP_RATIO_THRESHOLD = 0.65d;
    public static final double HEAVY_LATE_NIGHT_RATIO_THRESHOLD = 0.25d;

    private BehaviorThresholds() {
    }
}
