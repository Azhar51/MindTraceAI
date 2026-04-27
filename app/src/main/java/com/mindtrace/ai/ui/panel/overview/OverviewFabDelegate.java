package com.mindtrace.ai.ui.panel.overview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.ui.ViewUtils;

/**
 * Delegate for the expandable FAB menu on the Overview screen.
 * No Callback interface needed — all utilities are in {@link ViewUtils}.
 */
public class OverviewFabDelegate {

    private final FloatingActionButton fabPrimary;
    private final View fabActionsContainer;
    private final View fabScrim;
    private final TextView tvFabBadge;
    private final Context context;

    private boolean fabExpanded;
    private boolean fabHiddenByScroll;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable revealRunnable = () -> setHiddenByScroll(false, true);

    public OverviewFabDelegate(
            @Nullable FloatingActionButton fabPrimary,
            @Nullable View fabActionsContainer,
            @Nullable View fabScrim,
            @Nullable TextView tvFabBadge,
            @NonNull Context context) {
        this.fabPrimary = fabPrimary;
        this.fabActionsContainer = fabActionsContainer;
        this.fabScrim = fabScrim;
        this.tvFabBadge = tvFabBadge;
        this.context = context;
    }

    public boolean isExpanded() { return fabExpanded; }

    /** Toggle between expanded and collapsed states. */
    public void toggle() {
        setHiddenByScroll(false, true);
        setExpanded(!fabExpanded, true);
    }

    /** Set expanded state with optional animation. */
    public void setExpanded(boolean expanded, boolean animate) {
        if (fabActionsContainer == null || fabScrim == null || fabPrimary == null) return;
        fabExpanded = expanded;

        fabPrimary.animate().rotation(expanded ? 45f : 0f).setDuration(220L)
            .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        updateBadgeVisibility();

        if (expanded) {
            ViewUtils.performHaptic(context, 8);
            fabActionsContainer.setVisibility(View.VISIBLE);
            fabActionsContainer.setAlpha(1f);
            fabScrim.setVisibility(View.VISIBLE);
            if (!animate) { fabScrim.setAlpha(1f); return; }

            fabScrim.setAlpha(0f);
            fabScrim.animate().alpha(1f).setDuration(200L).start();
            ViewGroup group = (ViewGroup) fabActionsContainer;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                child.setAlpha(0f); child.setTranslationY(ViewUtils.dp(context, 18));
                child.setScaleX(0.85f); child.setScaleY(0.85f);
                child.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(i * 45L).setDuration(220L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f)).start();
            }
            return;
        }

        if (!animate) {
            fabScrim.setAlpha(0f); fabScrim.setVisibility(View.GONE);
            fabActionsContainer.setVisibility(View.GONE); fabActionsContainer.setAlpha(1f);
            return;
        }

        ViewGroup group = (ViewGroup) fabActionsContainer;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            child.animate().alpha(0f).translationY(ViewUtils.dp(context, 10)).scaleX(0.9f).scaleY(0.9f)
                .setStartDelay((long)(group.getChildCount() - 1 - i) * 25L).setDuration(140L).start();
        }
        fabScrim.animate().alpha(0f).setDuration(180L)
            .withEndAction(() -> fabScrim.setVisibility(View.GONE)).start();
        fabActionsContainer.animate().alpha(0f).setStartDelay(100L).setDuration(120L)
            .withEndAction(() -> {
                fabActionsContainer.setVisibility(View.GONE);
                fabActionsContainer.setAlpha(1f);
                for (int i = 0; i < group.getChildCount(); i++) {
                    View c = group.getChildAt(i);
                    c.setScaleX(1f); c.setScaleY(1f); c.setTranslationY(0f); c.setAlpha(1f);
                }
            }).start();
    }

    /** Handle scroll events for auto-hide/show behavior. */
    public void onScroll(int scrollY, int oldScrollY) {
        int delta = scrollY - oldScrollY;
        if (fabExpanded && Math.abs(delta) > ViewUtils.dp(context, 8)) setExpanded(false, true);
        uiHandler.removeCallbacks(revealRunnable);
        if (Math.abs(delta) <= ViewUtils.dp(context, 4)) return;
        if (delta > 0 && scrollY > ViewUtils.dp(context, 100)) setHiddenByScroll(true, true);
        else if (scrollY < ViewUtils.dp(context, 80)) setHiddenByScroll(false, true);
    }

    /** Update the badge count text. Zero or negative hides it. */
    public void setBadgeCount(int count) {
        if (tvFabBadge == null) return;
        if (count <= 0) { tvFabBadge.setText(""); tvFabBadge.setVisibility(View.GONE); return; }
        tvFabBadge.setText(String.valueOf(Math.min(9, count)));
        updateBadgeVisibility();
    }

    /** Initial delayed hide after fragment entry. */
    public void scheduleInitialHide() {
        if (fabPrimary != null) fabPrimary.postDelayed(() -> setHiddenByScroll(true, true), 1600L);
    }

    /** Clean up handlers. */
    public void cleanup() {
        uiHandler.removeCallbacks(revealRunnable);
        fabExpanded = false;
        fabHiddenByScroll = false;
    }

    private void setHiddenByScroll(boolean hidden, boolean animate) {
        if (fabPrimary == null) return;
        if (hidden && fabExpanded) setExpanded(false, true);
        if (fabHiddenByScroll == hidden && animate) return;
        fabHiddenByScroll = hidden;
        fabPrimary.animate().cancel();
        if (tvFabBadge != null) tvFabBadge.animate().cancel();

        float alpha = hidden ? 0f : 1f;
        float ty = hidden ? ViewUtils.dp(context, 96) : 0f;
        fabPrimary.setClickable(!hidden); fabPrimary.setFocusable(!hidden);

        if (!animate) {
            fabPrimary.setAlpha(alpha); fabPrimary.setTranslationY(ty);
            if (tvFabBadge != null) { tvFabBadge.setAlpha(alpha); tvFabBadge.setTranslationY(ty); }
            updateBadgeVisibility(); return;
        }

        fabPrimary.animate().alpha(alpha).translationY(ty).setDuration(hidden ? 150L : 190L).start();
        if (tvFabBadge != null) {
            if (!hidden) updateBadgeVisibility();
            tvFabBadge.animate().alpha(alpha).translationY(ty).setDuration(hidden ? 150L : 190L)
                .withEndAction(this::updateBadgeVisibility).start();
        } else {
            updateBadgeVisibility();
        }
    }

    private void updateBadgeVisibility() {
        if (tvFabBadge == null) return;
        boolean hasBadge = tvFabBadge.getText() != null && tvFabBadge.getText().length() > 0;
        tvFabBadge.setVisibility(!fabExpanded && !fabHiddenByScroll && hasBadge ? View.VISIBLE : View.GONE);
    }
}
