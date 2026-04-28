package com.mindtrace.ai.viewmodel.usecase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.mindtrace.ai.database.entity.OnboardingProfile;

/**
 * Extracted from DashboardViewModel — pure utility class for dashboard text generation.
 *
 * <p>Handles greeting text, date formatting, sentence trimming, text cleaning,
 * and other copy-related helpers that don't require state or repositories.</p>
 */
public final class HomeScreenTextHelper {

    private HomeScreenTextHelper() { /* utility class */ }

    // ─── Greetings ──────────────────────────────────────────────────

    public static String buildGreetingText(boolean highRisk, OnboardingProfile profile) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String firstName = resolveFirstName(profile);
        if (hour < 12) {
            return highRisk
                    ? joinGreeting(firstName, "Good morning", "Protect the first hour.")
                    : joinGreeting(firstName, "Good morning", "Today is a fresh start.");
        }
        if (hour < 17) {
            return highRisk
                    ? joinGreeting(firstName, "Good afternoon", "Reset the drift now.")
                    : joinGreeting(firstName, "Good afternoon", "Reclaim the rest of the day.");
        }
        return highRisk
                ? joinGreeting(firstName, "Good evening", "Finish clean, not scattered.")
                : joinGreeting(firstName, "Good evening", "You can still close today with control.");
    }

    public static String formatCurrentDate() {
        return new SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(new Date());
    }

    public static String formatTimeOfDay(long timestamp) {
        if (timestamp <= 0L) return "";
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    // ─── Text utilities ─────────────────────────────────────────────

    public static String trimSentence(String text, int maxChars) {
        if (text == null) return "";
        String trimmed = cleanDashboardCopy(text);
        if (trimmed.length() <= maxChars) return trimmed;
        return trimmed.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    public static String cleanDashboardCopy(String text) {
        if (text == null) return "";
        return text.trim()
                .replace("LLM enhancement unavailable, using deterministic insight output.",
                         "MindTrace is using your recent patterns to generate a local wellness insight.")
                .replace("you is", "you are")
                .replace("You is", "You are")
                .replace("you was", "you were")
                .replace("You was", "You were")
                .replaceAll("\\s+", " ");
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ─── Action helpers ─────────────────────────────────────────────

    public static String resolveTopAppLabel(com.mindtrace.ai.AppUsageModel topApp, boolean privacyMode) {
        if (privacyMode || topApp == null || topApp.appName == null || topApp.appName.trim().isEmpty()) {
            return "your top app";
        }
        return topApp.appName.trim();
    }

    public static String resolveEta(String actionText) {
        String lower = actionText == null ? "" : actionText.toLowerCase(Locale.getDefault());
        if (lower.contains("30")) return "About 30 min";
        if (lower.contains("25")) return "About 25 min";
        if (lower.contains("2-minute") || lower.contains("2 minute")) return "About 2 min";
        if (lower.contains("walk")) return "About 10 min";
        return "Start now";
    }

    public static String formatMomentumWindowLabel(int durationMinutes) {
        Calendar start = Calendar.getInstance();
        int minute = start.get(Calendar.MINUTE);
        int nextQuarter = (minute + 14) / 15 * 15;
        if (nextQuarter >= 60) {
            start.add(Calendar.HOUR_OF_DAY, 1);
            start.set(Calendar.MINUTE, 0);
        } else {
            start.set(Calendar.MINUTE, nextQuarter);
        }
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MINUTE, Math.max(20, durationMinutes));
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return formatter.format(start.getTime()) + " - " + formatter.format(end.getTime());
    }

    // ─── Internal ───────────────────────────────────────────────────

    static String resolveFirstName(OnboardingProfile profile) {
        if (profile == null || profile.name == null) return "";
        String trimmed = profile.name.trim();
        if (trimmed.isEmpty()) return "";
        int firstSpace = trimmed.indexOf(' ');
        return firstSpace > 0 ? trimmed.substring(0, firstSpace) : trimmed;
    }

    static String joinGreeting(String firstName, String greeting, String message) {
        if (firstName == null || firstName.trim().isEmpty()) {
            return greeting + ". " + message;
        }
        return greeting + ", " + firstName.trim() + ". " + message;
    }
}
