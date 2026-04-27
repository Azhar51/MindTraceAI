package com.mindtrace.ai.ui.panel.overview;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.ui.ViewUtils;
import com.mindtrace.ai.ui.model.HomeScreenState;
import com.mindtrace.ai.ui.theme.ColorSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate for the AI Insight Card on the Overview screen.
 * Utility methods now live in {@link ViewUtils}.
 */
public class OverviewInsightDelegate {

    public interface Callback {
        void performInsightAction(@Nullable HomeScreenState.InsightItem item);
        boolean isFragmentAdded();
    }

    private final MaterialCardView cardAiInsight;
    private final TextView tvInsightHeader;
    private final TextView tvInsightTitle;
    private final TextView tvInsightBody;
    private final TextView tvInsightLearnMore;
    private final TextView tvInsightBadge;
    private final View aiAnomalyDot;
    private final LinearLayout layoutInsightDots;
    private final Callback callback;
    private final Context context;

    private int currentIndex;
    private HomeScreenState currentState;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable rotationRunnable = new Runnable() {
        @Override public void run() {
            if (!callback.isFragmentAdded() || currentState == null
                    || currentState.aiInsightItems == null || currentState.aiInsightItems.size() < 2) return;
            currentIndex = (currentIndex + 1) % currentState.aiInsightItems.size();
            animateSwap();
            uiHandler.postDelayed(this, 5200L);
        }
    };

    public OverviewInsightDelegate(
            @Nullable MaterialCardView cardAiInsight,
            @Nullable TextView tvInsightHeader, @Nullable TextView tvInsightTitle,
            @Nullable TextView tvInsightBody, @Nullable TextView tvInsightLearnMore,
            @Nullable TextView tvInsightBadge, @Nullable View aiAnomalyDot,
            @Nullable LinearLayout layoutInsightDots,
            @NonNull Callback callback, @NonNull Context context) {
        this.cardAiInsight = cardAiInsight;
        this.tvInsightHeader = tvInsightHeader;
        this.tvInsightTitle = tvInsightTitle;
        this.tvInsightBody = tvInsightBody;
        this.tvInsightLearnMore = tvInsightLearnMore;
        this.tvInsightBadge = tvInsightBadge;
        this.aiAnomalyDot = aiAnomalyDot;
        this.layoutInsightDots = layoutInsightDots;
        this.callback = callback;
        this.context = context;
    }

    public void render(@NonNull HomeScreenState state) {
        currentState = state;
        HomeScreenState.InsightItem item =
                (state.aiInsightItems == null || state.aiInsightItems.isEmpty()) ? null
                : state.aiInsightItems.get(Math.max(0, Math.min(currentIndex, state.aiInsightItems.size() - 1)));
        if (item == null) {
            stopRotation(); currentIndex = 0;
            item = new HomeScreenState.InsightItem("MindTrace AI",
                "Complete another check-in to unlock a sharper explanation of your current pattern.",
                new ArrayList<>(), "Open insights", HomeScreenState.ACTION_PLAN, false);
        }
        if (state.aiInsightItems == null || state.aiInsightItems.isEmpty()) {
            if (!state.isLoading && cardAiInsight != null) cardAiInsight.setVisibility(View.GONE);
        } else {
            if (cardAiInsight != null) cardAiInsight.setVisibility(View.VISIBLE);
        }
        bindItem(item, state.aiInsightItems == null ? 1 : state.aiInsightItems.size());
        if (state.aiInsightItems != null && state.aiInsightItems.size() > 1) startRotation();
        else stopRotation();
    }

    public void advanceInsight() {
        if (currentState == null || currentState.aiInsightItems == null
                || currentState.aiInsightItems.size() < 2) return;
        ViewUtils.performHaptic(context, 8);
        currentIndex = (currentIndex + 1) % currentState.aiInsightItems.size();
        animateSwap(); startRotation();
    }

    public void showBottomSheet(@NonNull Context ctx) {
        if (currentState == null || currentState.aiInsightItems == null
                || currentState.aiInsightItems.isEmpty()) return;
        ViewUtils.performHaptic(ctx, 10);
        BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        View sheet = LayoutInflater.from(ctx).inflate(R.layout.sheet_overview_insight, null);
        TextView header = sheet.findViewById(R.id.tv_sheet_insight_header);
        TextView body = sheet.findViewById(R.id.tv_sheet_insight_body);
        LinearLayout reasons = sheet.findViewById(R.id.layout_sheet_insight_reasons);
        LinearLayout more = sheet.findViewById(R.id.layout_sheet_insight_more);
        MaterialButton btn = sheet.findViewById(R.id.btn_sheet_insight_primary);

        HomeScreenState.InsightItem primary = currentState.aiInsightItems.get(
                Math.max(0, Math.min(currentIndex, currentState.aiInsightItems.size() - 1)));
        header.setText(primary.headline == null ? "MindTrace AI" : primary.headline);
        body.setText(primary.body);
        populateRows(reasons, primary.reasonItems == null || primary.reasonItems.isEmpty()
                ? createFallbackReasons() : primary.reasonItems, false, null, null);
        if (currentState.aiInsightItems.size() > 1) {
            List<String> extras = new ArrayList<>();
            List<HomeScreenState.InsightItem> linked = new ArrayList<>();
            for (int i = 0; i < currentState.aiInsightItems.size(); i++) {
                if (i == currentIndex) continue;
                HomeScreenState.InsightItem e = currentState.aiInsightItems.get(i);
                extras.add(e.headline + "\n" + e.body);
                linked.add(e);
            }
            populateRows(more, extras, true, dialog, linked);
        } else { more.setVisibility(View.GONE); }
        btn.setText(primary.actionLabel == null || primary.actionLabel.trim().isEmpty()
                ? "Take action" : primary.actionLabel);
        btn.setOnClickListener(v -> { dialog.dismiss(); callback.performInsightAction(primary); });
        dialog.setContentView(sheet); dialog.show();
    }

    public void startRotation() {
        uiHandler.removeCallbacks(rotationRunnable);
        uiHandler.postDelayed(rotationRunnable, 5200L);
    }
    public void stopRotation() { uiHandler.removeCallbacks(rotationRunnable); }

    private void bindItem(HomeScreenState.InsightItem item, int total) {
        String title = item.headline;
        if (title == null || title.trim().isEmpty() || "MindTrace AI".equalsIgnoreCase(title.trim()))
            title = item.anomaly ? "Pattern shift worth watching" : "Why today's pattern matters";
        if (tvInsightHeader != null) tvInsightHeader.setText(item.anomaly ? "Pattern intelligence" : "MindTrace AI");
        if (tvInsightTitle != null) tvInsightTitle.setText(title);
        if (tvInsightBody != null) tvInsightBody.setText(item.body);
        if (tvInsightLearnMore != null) tvInsightLearnMore.setText(
            ((item.actionLabel == null || item.actionLabel.trim().isEmpty()) ? "Learn more" : item.actionLabel) + " ->");
        if (aiAnomalyDot != null) aiAnomalyDot.setVisibility(item.anomaly ? View.VISIBLE : View.GONE);
        if (tvInsightBadge != null) {
            if (item.anomaly) tvInsightBadge.setText("Anomaly");
            else if (total > 1) tvInsightBadge.setText((currentIndex + 1) + "/" + total);
            else tvInsightBadge.setText("Insight");
        }
        if (cardAiInsight != null) cardAiInsight.setCardElevation(ViewUtils.dp(context, item.anomaly ? 12 : 8));
        renderDots(total);
    }

    private void renderDots(int total) {
        if (layoutInsightDots == null) return;
        layoutInsightDots.removeAllViews();
        if (total <= 1) { layoutInsightDots.setVisibility(View.GONE); return; }
        layoutInsightDots.setVisibility(View.VISIBLE);
        Context ctx = layoutInsightDots.getContext();
        for (int i = 0; i < total; i++) {
            View dot = new View(ctx);
            boolean active = i == currentIndex;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                (int) ViewUtils.dp(ctx, active ? 20 : 8), (int) ViewUtils.dp(ctx, 8));
            if (i > 0) p.leftMargin = (int) ViewUtils.dp(ctx, 6);
            dot.setLayoutParams(p);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(ViewUtils.dp(ctx, 999));
            bg.setColor(active ? ViewUtils.translucent(ColorSystem.PRIMARY, 230) : ViewUtils.translucent(ColorSystem.TEXT_SECONDARY, 56));
            dot.setBackground(bg);
            if (active) {
                dot.setAlpha(0.85f);
                dot.animate().alpha(1f).scaleX(1.1f).scaleY(1.1f).setDuration(600L).start();
            }
            final int idx = i;
            dot.setOnClickListener(v -> {
                if (currentState == null || currentState.aiInsightItems == null || idx == currentIndex) return;
                ViewUtils.performHaptic(ctx, 8);
                currentIndex = idx; animateSwap(); startRotation();
            });
            layoutInsightDots.addView(dot);
        }
    }

    private void animateSwap() {
        if (currentState == null || currentState.aiInsightItems == null
                || currentState.aiInsightItems.isEmpty() || cardAiInsight == null) return;
        cardAiInsight.animate().alpha(0f).translationY(ViewUtils.dp(context, 6)).setDuration(120L)
            .withEndAction(() -> {
                bindItem(currentState.aiInsightItems.get(currentIndex), currentState.aiInsightItems.size());
                cardAiInsight.setTranslationY(-ViewUtils.dp(context, 6));
                cardAiInsight.animate().alpha(1f).translationY(0f).setDuration(180L).start();
            }).start();
    }

    private void populateRows(LinearLayout container, List<String> items, boolean compact,
                              @Nullable BottomSheetDialog dialog, @Nullable List<HomeScreenState.InsightItem> linked) {
        container.removeAllViews();
        if (items == null || items.isEmpty()) { container.setVisibility(View.GONE); return; }
        container.setVisibility(View.VISIBLE);
        Context ctx = container.getContext();
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            if (item == null || item.trim().isEmpty()) continue;
            TextView tv = new TextView(ctx);
            tv.setText(item); tv.setTextColor(ColorSystem.TEXT_PRIMARY);
            tv.setTextSize(compact ? 13f : 14f); tv.setLineSpacing(0f, 1.12f);
            tv.setBackgroundResource(R.drawable.bg_overview_warning_row);
            int h = (int) ViewUtils.dp(ctx, 14), v2 = (int) ViewUtils.dp(ctx, compact ? 10 : 12);
            tv.setPadding(h, v2, h, v2);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) p.topMargin = (int) ViewUtils.dp(ctx, 10);
            tv.setLayoutParams(p);
            if (compact) {
                final int idx = i;
                tv.setOnClickListener(vv -> {
                    if (dialog != null) dialog.dismiss();
                    ViewUtils.performHaptic(ctx, 8);
                    if (linked != null && linked.size() > idx) callback.performInsightAction(linked.get(idx));
                });
            }
            container.addView(tv);
        }
    }

    private List<String> createFallbackReasons() {
        List<String> r = new ArrayList<>();
        r.add("MindTrace blends check-ins, behavior shifts, and routine consistency to score today's pattern.");
        return r;
    }
}
