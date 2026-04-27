package com.mindtrace.ai.ui.components;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.mindtrace.ai.R;

/**
 * Helper class for managing empty states across fragments.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * EmptyStateHelper.show(view, "📓", "No journal entries yet",
 *     "Write your first journal entry to start tracking your mood",
 *     "Write Entry", v -> openJournal());
 * }</pre>
 */
public class EmptyStateHelper {

    /** Show the empty state with custom content. */
    public static void show(@NonNull View rootView, @NonNull String emoji,
                            @NonNull String title, @NonNull String subtitle,
                            @Nullable String actionText,
                            @Nullable View.OnClickListener actionListener) {
        View container = rootView.findViewById(R.id.empty_state_container);
        if (container == null) return;

        container.setVisibility(View.VISIBLE);

        TextView emojiView = rootView.findViewById(R.id.empty_state_emoji);
        TextView titleView = rootView.findViewById(R.id.empty_state_title);
        TextView subtitleView = rootView.findViewById(R.id.empty_state_subtitle);
        MaterialButton actionBtn = rootView.findViewById(R.id.empty_state_action);

        if (emojiView != null) emojiView.setText(emoji);
        if (titleView != null) titleView.setText(title);
        if (subtitleView != null) subtitleView.setText(subtitle);

        if (actionBtn != null) {
            if (actionText != null && actionListener != null) {
                actionBtn.setVisibility(View.VISIBLE);
                actionBtn.setText(actionText);
                actionBtn.setOnClickListener(actionListener);
            } else {
                actionBtn.setVisibility(View.GONE);
            }
        }
    }

    /** Hide the empty state. */
    public static void hide(@NonNull View rootView) {
        View container = rootView.findViewById(R.id.empty_state_container);
        if (container != null) {
            container.setVisibility(View.GONE);
        }
    }

    // ── Pre-configured empty states ──

    public static void showNoTasks(View rootView, View.OnClickListener onAction) {
        show(rootView, "📋", "No tasks yet",
                "Tasks will appear after your first check-in or journal entry",
                "Start Check-In", onAction);
    }

    public static void showNoJournals(View rootView, View.OnClickListener onAction) {
        show(rootView, "📓", "No journal entries",
                "Write your thoughts to track your emotional journey",
                "Write Entry", onAction);
    }

    public static void showNoCrisis(View rootView) {
        show(rootView, "💚", "No crisis events",
                "That's great news! Your safety history will appear here if needed",
                null, null);
    }

    public static void showNoUsage(View rootView) {
        show(rootView, "📊", "No usage data yet",
                "Usage data will appear after MindTrace has been tracking for a day",
                null, null);
    }

    public static void showNoInsights(View rootView) {
        show(rootView, "💡", "Insights building...",
                "Complete a few check-ins and tasks for personalized insights to appear",
                null, null);
    }
}
