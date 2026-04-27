package com.mindtrace.ai.ui.panel.overview;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.ui.ViewUtils;
import com.mindtrace.ai.ui.components.StateChipView;
import com.mindtrace.ai.ui.model.HomeScreenState;
import com.mindtrace.ai.ui.theme.ColorSystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Delegate for the Warning Card section of the Overview screen.
 * Extracted from OverviewFragment (Phase 5 decomposition).
 */
public class OverviewWarningDelegate {

    private final View cardWarning;
    private final StateChipView chipWarning;
    private final View[] warningRows;
    private final TextView[] warningViews;
    private final TextView[] warningDetailViews;
    private final View[] warningSeverityBars;
    private final MaterialButton[] warningAckButtons;
    private final View rootView;
    private final Context context;

    private final Set<String> dismissedItems = new HashSet<>();
    private final Set<String> expandedItems = new HashSet<>();
    private int currentWarningCount;
    private String lastHighSeverityKey = "";

    public OverviewWarningDelegate(
            View cardWarning, StateChipView chipWarning,
            View[] warningRows, TextView[] warningViews,
            TextView[] warningDetailViews, View[] warningSeverityBars,
            MaterialButton[] warningAckButtons, View rootView,
            Context context) {
        this.cardWarning = cardWarning;
        this.chipWarning = chipWarning;
        this.warningRows = warningRows;
        this.warningViews = warningViews;
        this.warningDetailViews = warningDetailViews;
        this.warningSeverityBars = warningSeverityBars;
        this.warningAckButtons = warningAckButtons;
        this.rootView = rootView;
        this.context = context;
    }

    public void loadDismissed(@NonNull Context ctx) {
        dismissedItems.clear();
        SharedPreferences prefs = ctx.getSharedPreferences("overview_premium_state", Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(getDismissedKey(), new HashSet<>());
        if (stored != null) dismissedItems.addAll(stored);
    }

    public int getCurrentWarningCount() { return currentWarningCount; }

    public void render(@NonNull HomeScreenState state) {
        List<HomeScreenState.WarningCardItem> source = new ArrayList<>();
        if (state.warningCardItems != null && !state.warningCardItems.isEmpty()) {
            source.addAll(state.warningCardItems);
        } else if (state.warningItems != null) {
            for (String w : state.warningItems) {
                if (w != null && !w.trim().isEmpty())
                    source.add(new HomeScreenState.WarningCardItem(w,
                        "This pattern is worth watching.", HomeScreenState.WarningCardItem.SEVERITY_MEDIUM));
            }
        }

        List<HomeScreenState.WarningCardItem> visible = new ArrayList<>();
        for (HomeScreenState.WarningCardItem item : source) {
            if (item != null && !dismissedItems.contains(norm(item.title))) visible.add(item);
        }

        currentWarningCount = visible.size();
        updateChip(state, visible.size());
        maybeShake(visible);

        if (visible.isEmpty()) {
            if (cardWarning != null) cardWarning.setVisibility(View.GONE);
            return;
        }
        if (cardWarning != null) cardWarning.setVisibility(View.VISIBLE);

        for (int i = 0; i < warningRows.length; i++) {
            if (i >= visible.size()) { warningRows[i].setVisibility(View.GONE); continue; }
            bindRow(i, visible.get(i), state);
        }
    }

    public void scheduleAutoDismiss(@NonNull HomeScreenState state, @NonNull Context ctx) {
        if (state.warningCardItems == null) return;
        for (HomeScreenState.WarningCardItem item : state.warningCardItems) {
            if (item.severity <= 1) {
                String key = norm(item.title);
                if (!dismissedItems.contains(key) && rootView != null) {
                    rootView.postDelayed(() -> {
                        dismissedItems.add(key);
                        persistDismissed(ctx);
                    }, 30 * 60 * 1000L);
                }
            }
        }
    }

    private void bindRow(int i, HomeScreenState.WarningCardItem item, HomeScreenState state) {
        View row = warningRows[i];
        row.setVisibility(View.VISIBLE);
        warningViews[i].setText(item.title);
        warningDetailViews[i].setText(item.detailText == null ? "" : item.detailText);
        boolean expanded = expandedItems.contains(norm(item.title));
        warningDetailViews[i].setVisibility(expanded ? View.VISIBLE : View.GONE);

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(ViewUtils.dp(context, 4));
        d.setColor(severityColor(item.severity));
        warningSeverityBars[i].setBackground(d);
        ViewGroup.LayoutParams lp = warningSeverityBars[i].getLayoutParams();
        if (lp != null) {
            lp.height = (int) ViewUtils.dp(context, item.severity >= 3 ? 48 : (item.severity >= 2 ? 36 : 24));
            warningSeverityBars[i].setLayoutParams(lp);
        }

        warningAckButtons[i].setVisibility(View.VISIBLE);
        warningAckButtons[i].setText("I'm aware");
        warningAckButtons[i].setOnClickListener(v -> ack(item, row, state));
        row.setOnClickListener(v -> toggle(item, i));
    }

    private void toggle(HomeScreenState.WarningCardItem item, int i) {
        String k = norm(item.title);
        if (expandedItems.contains(k)) expandedItems.remove(k); else expandedItems.add(k);
        UiMotion.toggleExpand(warningDetailViews[i]);
        ViewUtils.performHaptic(context, 6);
    }

    private void ack(HomeScreenState.WarningCardItem item, View row, HomeScreenState state) {
        dismissedItems.add(norm(item.title));
        expandedItems.remove(norm(item.title));
        persistDismissed(rootView.getContext());
        ViewUtils.performHaptic(context, 8);
        row.animate().alpha(0f).translationX(ViewUtils.dp(context, 18)).setDuration(180L)
            .withEndAction(() -> { row.setAlpha(1f); row.setTranslationX(0f); render(state); }).start();
    }

    private void updateChip(HomeScreenState state, int count) {
        if (state.isLoading) { chipWarning.applyNeutralLabel("Scanning traps"); return; }
        if (count <= 0) {
            chipWarning.setChipColors(ViewUtils.translucent(ColorSystem.GREEN, 48), ColorSystem.GREEN);
            chipWarning.setText("All clear"); return;
        }
        int c = count >= 4 || state.isHighRisk ? ColorSystem.RED : count >= 2 ? ColorSystem.AMBER : ColorSystem.GREEN;
        chipWarning.setChipColors(ViewUtils.translucent(c, 48), c);
        chipWarning.setText(count >= 4 ? count + " traps detected" : count == 1 ? "1 trap" : count + " traps");
    }

    private void maybeShake(List<HomeScreenState.WarningCardItem> items) {
        String k = "";
        if (items != null) for (HomeScreenState.WarningCardItem it : items)
            if (it != null && it.severity >= HomeScreenState.WarningCardItem.SEVERITY_HIGH) { k = norm(it.title); break; }
        if (k.isEmpty()) { lastHighSeverityKey = ""; return; }
        if (!k.equals(lastHighSeverityKey)) {
            lastHighSeverityKey = k;
            if (cardWarning != null) cardWarning.postDelayed(() -> {
                if (cardWarning.isAttachedToWindow() && cardWarning.getVisibility() == View.VISIBLE)
                    UiMotion.shake(cardWarning);
            }, 1000L);
        }
    }

    private void persistDismissed(@NonNull Context ctx) {
        ctx.getSharedPreferences("overview_premium_state", Context.MODE_PRIVATE)
            .edit().putStringSet(getDismissedKey(), new HashSet<>(dismissedItems)).apply();
    }

    private String getDismissedKey() {
        return "dismissed_warning_items_" + (System.currentTimeMillis() / (24L * 60 * 60 * 1000L));
    }

    private String norm(@Nullable String t) { return t == null ? "" : t.trim().toLowerCase(Locale.ROOT); }
    private int severityColor(int s) {
        if (s >= HomeScreenState.WarningCardItem.SEVERITY_HIGH) return ColorSystem.RED;
        if (s == HomeScreenState.WarningCardItem.SEVERITY_LOW) return ColorSystem.GREEN;
        return ColorSystem.AMBER;
    }
}
