package com.mindtrace.ai.ui.panel.overview;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.ui.ViewUtils;
import com.mindtrace.ai.ui.components.GradientProgressBar;
import com.mindtrace.ai.ui.components.StateChipView;
import com.mindtrace.ai.ui.model.HomeScreenState;
import com.mindtrace.ai.ui.theme.ColorSystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Delegate for the Mission Card section of the Overview screen.
 * Utility methods now live in {@link ViewUtils}.
 */
public class OverviewMissionDelegate {

    public interface Callback {
        void burstConfettiFrom(View target, int count);
        void burstConfettiGlobal(int count);
        void markTaskCompleted(InterventionTask task);
        boolean isFragmentAdded();
        Context getContext();
    }

    private final View cardMission;
    private final View missionCardSurface;
    private final TextView tvMissionTitle;
    private final TextView tvMissionCompleteState;
    private final GradientProgressBar missionProgress;
    private final StateChipView chipMissionProgress;
    private final View[] missionRows;
    private final TextView[] missionIndicators;
    private final TextView[] missionStepViews;
    private final TextView[] missionReasonViews;
    private final ImageView[] missionIconViews;
    private final ImageButton[] missionInfoButtons;
    private final Callback callback;
    private final Context context;

    private final Set<Integer> pendingCompletions = new HashSet<>();
    private final Set<String> expandedReasons = new HashSet<>();
    private boolean celebrated;

    public OverviewMissionDelegate(
            View cardMission, View missionCardSurface,
            TextView tvMissionTitle, TextView tvMissionCompleteState,
            GradientProgressBar missionProgress, StateChipView chipMissionProgress,
            View[] missionRows, TextView[] missionIndicators,
            TextView[] missionStepViews, TextView[] missionReasonViews,
            ImageView[] missionIconViews, ImageButton[] missionInfoButtons,
            Callback callback, Context context) {
        this.cardMission = cardMission;
        this.missionCardSurface = missionCardSurface;
        this.tvMissionTitle = tvMissionTitle;
        this.tvMissionCompleteState = tvMissionCompleteState;
        this.missionProgress = missionProgress;
        this.chipMissionProgress = chipMissionProgress;
        this.missionRows = missionRows;
        this.missionIndicators = missionIndicators;
        this.missionStepViews = missionStepViews;
        this.missionReasonViews = missionReasonViews;
        this.missionIconViews = missionIconViews;
        this.missionInfoButtons = missionInfoButtons;
        this.callback = callback;
        this.context = context;
    }

    public Set<Integer> getPendingCompletions() { return pendingCompletions; }
    public void confirmCompletion(int taskId) { pendingCompletions.remove(taskId); }

    public void render(@NonNull HomeScreenState state) {
        if (cardMission == null) return;
        if (state.missionStepItems == null || state.missionStepItems.isEmpty() && !state.isLoading) {
            cardMission.setVisibility(View.GONE);
            return;
        }
        cardMission.setVisibility(View.VISIBLE);
        tvMissionTitle.setText(state.missionTitle);
        renderProgress(state);
        bindSteps(state.isLoading ? createLoadingSteps() : state.missionStepItems);
    }

    public void renderProgress(@NonNull HomeScreenState state) {
        missionProgress.setReverseGradient(true);
        if (state.isLoading) {
            chipMissionProgress.applyNeutralLabel(state.missionProgressText);
            missionProgress.setAlpha(0.55f);
            missionProgress.setProgressImmediate(0.14f);
            setCompletionState(false, false);
            return;
        }
        int totalTaskSteps = 0, completedTaskSteps = 0;
        for (HomeScreenState.MissionStepItem item : state.missionStepItems) {
            if (item.taskId <= 0) continue;
            totalTaskSteps++;
            if (item.isCompleted || pendingCompletions.contains(item.taskId)) completedTaskSteps++;
        }
        missionProgress.setAlpha(1f);
        if (totalTaskSteps > 0) {
            String progressLabel = completedTaskSteps + "/" + totalTaskSteps + " complete";
            if (state.missionProgressText != null && state.missionProgressText.startsWith("Reset done"))
                progressLabel = "Reset done | " + progressLabel;
            chipMissionProgress.applyNeutralLabel(progressLabel);
            missionProgress.setProgress(totalTaskSteps == 0 ? 0f : completedTaskSteps / (float) totalTaskSteps);
            setCompletionState(completedTaskSteps >= totalTaskSteps, false);
            return;
        }
        chipMissionProgress.applyNeutralLabel(state.missionProgressText);
        missionProgress.setProgress(state.missionProgressPercent / 100f);
        setCompletionState(false, false);
    }

    public boolean allStepsComplete(@Nullable HomeScreenState state) {
        if (state == null || state.missionStepItems == null || state.missionStepItems.isEmpty()) return false;
        int total = 0, completed = 0;
        for (HomeScreenState.MissionStepItem item : state.missionStepItems) {
            if (item.taskId <= 0) continue;
            total++;
            if (item.isCompleted || pendingCompletions.contains(item.taskId)) completed++;
        }
        return total > 0 && completed >= total;
    }

    private void bindSteps(List<HomeScreenState.MissionStepItem> stepItems) {
        List<HomeScreenState.MissionStepItem> safeSteps = stepItems == null || stepItems.isEmpty()
                ? createFallbackSteps() : stepItems;
        for (int i = 0; i < missionRows.length; i++) {
            if (i >= safeSteps.size()) { missionRows[i].setVisibility(View.GONE); continue; }
            final int index = i;
            HomeScreenState.MissionStepItem item = safeSteps.get(i);
            boolean completed = item.isCompleted || pendingCompletions.contains(item.taskId);
            missionRows[i].setVisibility(View.VISIBLE);
            missionStepViews[i].setText(item.text);
            missionReasonViews[i].setText(item.whyText == null ? "This step helps steady your routine." : item.whyText);
            missionReasonViews[i].setVisibility(expandedReasons.contains(reasonKey(item, index)) ? View.VISIBLE : View.GONE);
            missionInfoButtons[i].setVisibility(item.whyText == null || item.whyText.trim().isEmpty() ? View.GONE : View.VISIBLE);
            missionInfoButtons[i].setRotation(missionReasonViews[i].getVisibility() == View.VISIBLE ? 180f : 0f);
            missionInfoButtons[i].setOnClickListener(v -> toggleReason(item, index));
            applyStepState(missionRows[i], missionIndicators[i], missionStepViews[i], missionIconViews[i], item, completed, index);
        }
    }

    private void applyStepState(View row, TextView indicator, TextView label, ImageView icon,
            HomeScreenState.MissionStepItem item, boolean isCompleted, int position) {
        row.setOnClickListener(null);
        int accentColor = resolveAccentColor(item, position);
        label.setPaintFlags(isCompleted ? label.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : label.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        label.setAlpha(isCompleted ? 0.68f : 1f);
        label.setTextColor(isCompleted ? ColorSystem.TEXT_SECONDARY : ColorSystem.TEXT_PRIMARY);
        icon.setImageResource(resolveIconRes(item.categoryKey));
        icon.setColorFilter(isCompleted ? ColorSystem.GREEN : accentColor);
        if (isCompleted) {
            indicator.setText("\u2713");
            indicator.setBackground(buildIndicator(ColorSystem.GREEN, ColorSystem.GREEN, true));
            indicator.setAlpha(1f);
            row.setEnabled(false); row.setAlpha(1f);
            return;
        }
        indicator.setText("");
        indicator.setBackground(buildIndicator(ViewUtils.translucent(accentColor, 52), accentColor, false));
        boolean interactive = item.isInteractive && item.taskId > 0;
        row.setEnabled(interactive); row.setAlpha(interactive ? 1f : 0.86f);
        if (interactive) row.setOnClickListener(v -> onStepClicked(item, indicator, label, row, position, icon));
    }

    private void toggleReason(HomeScreenState.MissionStepItem item, int position) {
        String key = reasonKey(item, position);
        boolean showing = expandedReasons.contains(key);
        if (showing) expandedReasons.remove(key); else expandedReasons.add(key);
        UiMotion.toggleExpand(missionReasonViews[position]);
        missionInfoButtons[position].animate().rotation(showing ? 0f : 180f).setDuration(180L).start();
    }

    private void onStepClicked(HomeScreenState.MissionStepItem item, TextView indicator, TextView label,
            View row, int position, ImageView icon) {
        Map<Integer, InterventionTask> tasks = getMissionTasks();
        InterventionTask task = tasks != null ? tasks.get(item.taskId) : null;
        if (task == null || isTaskCompleted(task) || pendingCompletions.contains(item.taskId)) return;
        ViewUtils.performHaptic(context, 12);
        pendingCompletions.add(item.taskId);
        expandedReasons.remove(reasonKey(item, position));
        missionReasonViews[position].setVisibility(View.GONE);
        missionInfoButtons[position].setRotation(0f);
        indicator.animate().cancel();
        indicator.setScaleX(0.82f); indicator.setScaleY(0.82f);
        label.animate().alpha(0.68f).setDuration(180L).start();
        icon.animate().rotationBy(16f).setDuration(180L).withEndAction(() -> icon.setRotation(0f)).start();
        indicator.animate().scaleX(1f).scaleY(1f).setDuration(180L).start();
        applyStepState(row, indicator, label, icon,
                new HomeScreenState.MissionStepItem(item.taskId, item.text, true, false, item.categoryKey, item.whyText),
                true, position);
        callback.burstConfettiFrom(row, 24);
        callback.markTaskCompleted(task);
    }

    public void celebrateComplete(@Nullable HomeScreenState state) {
        if (celebrated) return;
        celebrated = true;
        setCompletionState(true, true);
        ViewUtils.performHaptic(context, 40);
        callback.burstConfettiGlobal(90);
        callback.burstConfettiFrom(cardMission, 30);
        if (callback.isFragmentAdded() && callback.getContext() != null)
            Toast.makeText(callback.getContext(), "\uD83C\uDF89 All missions complete!", Toast.LENGTH_SHORT).show();
    }

    private void setCompletionState(boolean complete, boolean animate) {
        missionCardSurface.setBackgroundResource(complete ? R.drawable.bg_overview_mission_success : R.drawable.bg_overview_mission_card);
        tvMissionCompleteState.setVisibility(complete ? View.VISIBLE : View.GONE);
        if (complete) {
            chipMissionProgress.setChipColors(ViewUtils.translucent(ColorSystem.GREEN, 44), ColorSystem.GREEN);
            chipMissionProgress.setText("Mission complete");
            if (animate) UiMotion.animateCompletion(cardMission, null);
            return;
        }
        celebrated = false;
    }

    private Map<Integer, InterventionTask> missionTasks;
    public void setMissionTasks(Map<Integer, InterventionTask> tasks) { this.missionTasks = tasks; }
    private Map<Integer, InterventionTask> getMissionTasks() { return missionTasks; }

    private List<HomeScreenState.MissionStepItem> createLoadingSteps() {
        List<HomeScreenState.MissionStepItem> items = new ArrayList<>();
        for (int i = 1; i <= 3; i++) items.add(new HomeScreenState.MissionStepItem(0, "Preparing mission step " + i + "...", false, false));
        return items;
    }

    private List<HomeScreenState.MissionStepItem> createFallbackSteps() {
        List<HomeScreenState.MissionStepItem> items = new ArrayList<>();
        items.add(new HomeScreenState.MissionStepItem(0, "Finish one focused study block", false, false, "focus", "A protected focus block lowers the chance of random switching taking over the morning."));
        items.add(new HomeScreenState.MissionStepItem(0, "Keep phone away during first work session", false, false, "digital", "Physical distance from the phone makes it easier to stay locked into the first important task."));
        items.add(new HomeScreenState.MissionStepItem(0, "Complete one check-in before night", false, false, "general", "A check-in closes the loop for the day and helps tomorrow's guidance get sharper."));
        return items;
    }

    private GradientDrawable buildIndicator(int fillColor, int strokeColor, boolean completed) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        if (!completed) drawable.setStroke(Math.max(1, (int) ViewUtils.dp(context, 2)), strokeColor);
        int size = Math.max(1, (int) ViewUtils.dp(context, 16));
        drawable.setSize(size, size);
        return drawable;
    }

    private int resolveAccentColor(HomeScreenState.MissionStepItem item, int position) {
        if (item != null) {
            String category = item.categoryKey == null ? "" : item.categoryKey.toLowerCase(Locale.ROOT);
            if ("focus".equals(category)) return ColorSystem.PRIMARY;
            if ("digital".equals(category)) return ColorSystem.RED;
            if ("social".equals(category)) return ColorSystem.AMBER;
            if ("rest".equals(category)) return ColorSystem.GREEN;
        }
        switch (position) { case 0: return ColorSystem.GREEN; case 1: return ColorSystem.PRIMARY; default: return ColorSystem.AMBER; }
    }

    private int resolveIconRes(String categoryKey) {
        if (categoryKey == null) return R.drawable.ic_mission_general;
        switch (categoryKey.toLowerCase(Locale.ROOT)) {
            case "focus": return R.drawable.ic_mission_focus; case "digital": return R.drawable.ic_mission_digital;
            case "social": return R.drawable.ic_mission_social; case "rest": return R.drawable.ic_mission_rest;
            default: return R.drawable.ic_mission_general;
        }
    }

    private boolean isTaskCompleted(InterventionTask task) { return task != null && (task.isCompleted || "COMPLETED".equals(task.status)); }
    private String reasonKey(HomeScreenState.MissionStepItem item, int position) {
        if (item.taskId > 0) return "task:" + item.taskId;
        return "position:" + position + ":" + item.text;
    }
}
