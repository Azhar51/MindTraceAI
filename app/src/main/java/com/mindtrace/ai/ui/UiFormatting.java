package com.mindtrace.ai.ui;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;

import com.mindtrace.ai.R;
import com.mindtrace.ai.util.MoodMapper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class UiFormatting {
    private UiFormatting() {
    }

    public static String formatDuration(long millis) {
        if (millis <= 0L) {
            return "0m";
        }

        long hours = millis / (1000L * 60L * 60L);
        long minutes = (millis % (1000L * 60L * 60L)) / (1000L * 60L);

        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    public static String formatDayLabel(long timestamp) {
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String formatFullDate(long timestamp) {
        return new SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String formatTimeLabel(long timestamp) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String formatSleep(float hours) {
        return String.format(Locale.getDefault(), "%.1fh sleep", hours);
    }

    public static CharSequence highlightKeywords(Context context, String text, String... keywords) {
        if (text == null) {
            return "";
        }

        SpannableString spannable = new SpannableString(text);
        int highlightColor = ContextCompat.getColor(context, R.color.primary);
        String lowerText = text.toLowerCase(Locale.getDefault());
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }

            String lowerKeyword = keyword.toLowerCase(Locale.getDefault());
            int start = lowerText.indexOf(lowerKeyword);
            while (start >= 0) {
                int end = start + keyword.length();
                spannable.setSpan(new ForegroundColorSpan(highlightColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = lowerText.indexOf(lowerKeyword, end);
            }
        }
        return spannable;
    }

    public static String formatDeviationPercent(double deviation) {
        return String.format(Locale.getDefault(), "%.0f%%", Math.abs(deviation) * 100d);
    }

    /** @deprecated Use {@link MoodMapper#moodToFloat(String)} instead. */
    @Deprecated
    public static float getMoodScore(String mood) {
        return MoodMapper.moodToFloat(mood);
    }

    /** @deprecated Use {@link MoodMapper} reverse lookup instead. */
    @Deprecated
    public static String getMoodLabel(float score) {
        int moodValue = Math.round(score);
        switch (moodValue) {
            case 1:  return MoodMapper.MOOD_SAD;
            case 2:  return MoodMapper.MOOD_ANXIOUS;
            case 3:  return MoodMapper.MOOD_NEUTRAL;
            case 4:  return MoodMapper.MOOD_HAPPY;
            default: return "";
        }
    }

    /** Returns a formatted mood chip string: emoji + mood name. */
    public static String formatMoodChip(String mood) {
        return MoodMapper.getMoodEmoji(mood) + " " + (mood != null ? mood : "Unknown");
    }

    /** Returns a concise risk label with color context. */
    public static String formatRiskBadge(float risk) {
        return MoodMapper.riskToLabel(risk) + " (" +
                String.format(Locale.getDefault(), "%.0f%%", risk * 100) + ")";
    }
}
