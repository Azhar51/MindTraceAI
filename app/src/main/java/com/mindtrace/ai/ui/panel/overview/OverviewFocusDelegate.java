package com.mindtrace.ai.ui.panel.overview;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.ui.ViewUtils;
import com.mindtrace.ai.ui.model.HomeScreenState;
import com.mindtrace.ai.ui.theme.ColorSystem;

/**
 * Delegate for the Focus Window Card section of the Overview screen.
 * No Callback interface needed — all utilities are in {@link ViewUtils}.
 */
public class OverviewFocusDelegate {

    private final MaterialCardView cardFocusWindow;
    private final TextView tvFocusBadge;
    private final TextView tvFocusTitle;
    private final TextView tvFocusWindow;
    private final TextView tvFocusBody;
    private final MaterialButton btnFocusAction;
    private final TextView[] focusRitualViews;
    private final Context context;

    public OverviewFocusDelegate(
            MaterialCardView cardFocusWindow,
            TextView tvFocusBadge, TextView tvFocusTitle,
            TextView tvFocusWindow, TextView tvFocusBody,
            MaterialButton btnFocusAction,
            TextView[] focusRitualViews,
            Context context) {
        this.cardFocusWindow = cardFocusWindow;
        this.tvFocusBadge = tvFocusBadge;
        this.tvFocusTitle = tvFocusTitle;
        this.tvFocusWindow = tvFocusWindow;
        this.tvFocusBody = tvFocusBody;
        this.btnFocusAction = btnFocusAction;
        this.focusRitualViews = focusRitualViews;
        this.context = context;
    }

    public void render(@NonNull HomeScreenState state) {
        if (cardFocusWindow == null) return;
        HomeScreenState.FocusWindowCard focusCard = state.focusWindowCard;
        if (focusCard == null) { cardFocusWindow.setVisibility(View.GONE); return; }

        cardFocusWindow.setVisibility(View.VISIBLE);
        tvFocusBadge.setText(focusCard.badgeText == null ? "Momentum window" : focusCard.badgeText);
        tvFocusTitle.setText(focusCard.title == null
                ? "Shape the next block before the day shapes it" : focusCard.title);
        // Hide the timestamp — not relevant for coach card
        tvFocusWindow.setVisibility(View.GONE);
        tvFocusBody.setText(focusCard.coachText == null
                ? "MindTrace is shaping a premium focus plan for the next clean stretch of your day."
                : focusCard.coachText);
        btnFocusAction.setText(
                focusCard.actionLabel == null || focusCard.actionLabel.trim().isEmpty()
                        ? "Start next step" : focusCard.actionLabel);

        bindRitualItems(focusCard);

        int accentColor = focusCard.urgent ? ColorSystem.RED : ColorSystem.PRIMARY;
        cardFocusWindow.setCardElevation(ViewUtils.dp(context, focusCard.urgent ? 14 : 8));
        tvFocusWindow.setTextColor(accentColor);
        btnFocusAction.setStrokeColor(ColorStateList.valueOf(accentColor));
        btnFocusAction.setTextColor(accentColor);

        if (focusCard.urgent) {
            tvFocusWindow.animate()
                    .alpha(0.7f).setDuration(600L)
                    .withEndAction(() -> tvFocusWindow.animate().alpha(1f).setDuration(600L).start())
                    .start();
        }
    }

    private void bindRitualItems(@NonNull HomeScreenState.FocusWindowCard focusCard) {
        ViewGroup ritualContainer = null;
        for (int i = 0; i < focusRitualViews.length; i++) {
            if (focusRitualViews[i] == null) continue;
            if (focusCard.ritualItems != null && i < focusCard.ritualItems.size()) {
                focusRitualViews[i].setVisibility(View.VISIBLE);
                focusRitualViews[i].setText(focusCard.ritualItems.get(i));
                focusRitualViews[i].setAlpha(focusCard.urgent ? 1f : 0.94f);
                if (ritualContainer == null && focusRitualViews[i].getParent() instanceof ViewGroup)
                    ritualContainer = (ViewGroup) focusRitualViews[i].getParent();
            } else {
                focusRitualViews[i].setVisibility(View.GONE);
            }
        }
        if (ritualContainer != null) UiMotion.animatePillStagger(ritualContainer, 55);
    }
}
