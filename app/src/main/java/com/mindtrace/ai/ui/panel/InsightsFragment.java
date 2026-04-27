package com.mindtrace.ai.ui.panel;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.ClassificationTrendAnalyzer;
import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.ai.StreakRecoveryManager;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.CrisisEvent;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.ui.JournalActivity;
import com.mindtrace.ai.ui.MainActivity;
import com.mindtrace.ai.ui.UiFormatting;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.util.AppExecutors;
import com.mindtrace.ai.ui.components.DeviationView;
import com.mindtrace.ai.ui.components.RiskCategoryBarView;
import com.mindtrace.ai.ui.components.SparklineView;
import com.mindtrace.ai.ui.components.TimelineChartView;
import com.mindtrace.ai.ui.components.WellnessRingView;
import com.mindtrace.ai.viewmodel.DashboardViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AI Insights Screen — The Brain.
 *
 * <p>Shows the user their complete AI-generated psychological profile:
 * 6-category risk classification, wellness ring, behavioral explanations,
 * and personalized next actions.</p>
 *
 * <h3>Sections (7 total):</h3>
 * <ol>
 *   <li>Wellness Ring — Risk Score Hero</li>
 *   <li>6-Category Risk Classification Bars</li>
 *   <li>Why This Score — AI Explanation</li>
 *   <li>Behavioral Trend — 7-Day Sparkline</li>
 *   <li>Next Best Action (from AI)</li>
 *   <li>AI Deep Analysis (conditional)</li>
 *   <li>Support Strip (conditional — crisis/depression)</li>
 * </ol>
 */
public class InsightsFragment extends Fragment {
    private DashboardViewModel dashboardViewModel;

    // Section 1: Hero
    private WellnessRingView ringView;
    private TextView tvFreshnessBadge;
    private TextView tvAiSummary;

    // Section 2: Risk classification
    private LinearLayout llRiskBars;
    private LinearLayout llPrimaryConcern;
    private View dotPrimaryConcern;
    private TextView tvPrimaryConcern;
    private TextView tvConfidenceBadge;

    // Section 3: Why this score
    private TextView tvWhyReasons;

    // Section 4: Behavioral trend
    private SparklineView sparklineView;
    private DeviationView deviationView;
    private TextView tvTrendDirection;

    // Section 5: Next action
    private TextView tvActionContext;
    private TextView tvActionText;
    private TextView tvActionCategory;
    private TextView tvActionImpact;
    private TextView tvActionTime;
    private MaterialButton btnStartAction;

    // Section 6: AI Deep
    private MaterialCardView cardAiDeep;
    private TextView tvAiDeepText;

    // Section 7: Support
    private MaterialCardView cardSupport;
    private MaterialButton btnOpenSupport;

    // Timeline
    private TimelineChartView timelineView;

    // Gap Fix: Sparkline value labels
    private TextView tvSparklineValues;

    // Advanced: Day-over-Day comparison
    private MaterialCardView cardDayComparison;
    private TextView tvCompareYesterdayScore;
    private TextView tvCompareYesterdayLabel;
    private TextView tvCompareTodayScore;
    private TextView tvCompareTodayLabel;
    private TextView tvCompareDelta;

    // Advanced: Co-morbidity alert
    private MaterialCardView cardComorbidity;
    private TextView tvComorbidityText;
    private TextView tvComorbidityCategories;

    // Section 4.5: Trajectory assessment
    private MaterialCardView cardTrajectory;
    private TextView tvTrajectoryBadge;
    private TextView tvTrajectorySummary;
    private TextView tvTrajectoryWorsening;
    private TextView tvTrajectoryImproving;
    private TextView tvTrajectoryDrift;
    private TextView tvTrajectoryDrivers;
    private TextView tvTrajectoryDataQuality;

    // Classification Engine Mode Indicator
    private LinearLayout llClassificationEngine;
    private View dotEngineMode;
    private TextView tvEngineModeLabel;
    private TextView tvEngineModeBadge;
    private TextView tvDataQualityLabel;
    private TextView tvFeatureCoverage;
    private TextView tvMlModelStatus;

    // Section 7.5: Crisis Recovery Timeline
    private MaterialCardView cardCrisisRecovery;
    private TextView tvRecoveryPhaseBadge;
    private TextView tvRecoveryMessage;
    private LinearLayout llGraceTimer;
    private TextView tvGraceRemaining;
    private LinearLayout llStreakPreserved;
    private TextView tvStreakPreservedCount;
    private LinearLayout llCrisisEvents;
    private TextView tvNoCrisisHistory;

    // Data
    private long latestScreenTime;
    private UserBaseline latestBaseline;
    private RiskClassification latestClassification;

    public InsightsFragment() {
        super(R.layout.fragment_insights);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);
        bindViews(view);
        setupListeners(view);
        observeData();
    }

    // ═══════════════════════════════════════════════════════════════════
    // VIEW BINDING
    // ═══════════════════════════════════════════════════════════════════

    private void bindViews(View v) {
        // Section 1
        ringView = v.findViewById(R.id.ring_insights_score);
        tvFreshnessBadge = v.findViewById(R.id.tv_freshness_badge);
        tvAiSummary = v.findViewById(R.id.tv_insights_ai_summary);

        // Section 2
        llRiskBars = v.findViewById(R.id.ll_risk_bars);
        llPrimaryConcern = v.findViewById(R.id.ll_primary_concern);
        dotPrimaryConcern = v.findViewById(R.id.dot_primary_concern);
        tvPrimaryConcern = v.findViewById(R.id.tv_primary_concern);
        tvConfidenceBadge = v.findViewById(R.id.tv_confidence_badge);

        // Section 3
        tvWhyReasons = v.findViewById(R.id.tv_why_reasons);

        // Section 4
        sparklineView = v.findViewById(R.id.spark_insights_trend);
        deviationView = v.findViewById(R.id.view_insights_deviation);
        tvTrendDirection = v.findViewById(R.id.tv_trend_direction);

        // Section 5
        tvActionContext = v.findViewById(R.id.tv_action_context);
        tvActionText = v.findViewById(R.id.tv_action_text);
        tvActionCategory = v.findViewById(R.id.tv_action_category);
        tvActionImpact = v.findViewById(R.id.tv_action_impact);
        tvActionTime = v.findViewById(R.id.tv_action_time);
        btnStartAction = v.findViewById(R.id.btn_start_action);

        // Section 6
        cardAiDeep = v.findViewById(R.id.card_ai_deep);
        tvAiDeepText = v.findViewById(R.id.tv_ai_deep_text);

        // Section 7
        cardSupport = v.findViewById(R.id.card_insights_support);
        btnOpenSupport = v.findViewById(R.id.btn_insights_support);

        // Timeline
        timelineView = v.findViewById(R.id.timeline_insights);

        // Gap fix: sparkline value labels
        tvSparklineValues = v.findViewById(R.id.tv_sparkline_values);

        // Advanced: Day-over-Day
        cardDayComparison = v.findViewById(R.id.card_day_comparison);
        tvCompareYesterdayScore = v.findViewById(R.id.tv_compare_yesterday_score);
        tvCompareYesterdayLabel = v.findViewById(R.id.tv_compare_yesterday_label);
        tvCompareTodayScore = v.findViewById(R.id.tv_compare_today_score);
        tvCompareTodayLabel = v.findViewById(R.id.tv_compare_today_label);
        tvCompareDelta = v.findViewById(R.id.tv_compare_delta);

        // Advanced: Co-morbidity
        cardComorbidity = v.findViewById(R.id.card_comorbidity);
        tvComorbidityText = v.findViewById(R.id.tv_comorbidity_text);
        tvComorbidityCategories = v.findViewById(R.id.tv_comorbidity_categories);

        // Section 4.5: Trajectory assessment
        cardTrajectory = v.findViewById(R.id.card_trajectory);
        tvTrajectoryBadge = v.findViewById(R.id.tv_trajectory_badge);
        tvTrajectorySummary = v.findViewById(R.id.tv_trajectory_summary);
        tvTrajectoryWorsening = v.findViewById(R.id.tv_trajectory_worsening);
        tvTrajectoryImproving = v.findViewById(R.id.tv_trajectory_improving);
        tvTrajectoryDrift = v.findViewById(R.id.tv_trajectory_drift);
        tvTrajectoryDrivers = v.findViewById(R.id.tv_trajectory_drivers);
        tvTrajectoryDataQuality = v.findViewById(R.id.tv_trajectory_data_quality);

        // Classification Engine Mode Indicator
        llClassificationEngine = v.findViewById(R.id.ll_classification_engine);
        dotEngineMode = v.findViewById(R.id.dot_engine_mode);
        tvEngineModeLabel = v.findViewById(R.id.tv_engine_mode_label);
        tvEngineModeBadge = v.findViewById(R.id.tv_engine_mode_badge);
        tvDataQualityLabel = v.findViewById(R.id.tv_data_quality_label);
        tvFeatureCoverage = v.findViewById(R.id.tv_feature_coverage);
        tvMlModelStatus = v.findViewById(R.id.tv_ml_model_status);

        // Section 7.5: Crisis Recovery Timeline
        cardCrisisRecovery = v.findViewById(R.id.card_crisis_recovery);
        tvRecoveryPhaseBadge = v.findViewById(R.id.tv_recovery_phase_badge);
        tvRecoveryMessage = v.findViewById(R.id.tv_recovery_message);
        llGraceTimer = v.findViewById(R.id.ll_grace_timer);
        tvGraceRemaining = v.findViewById(R.id.tv_grace_remaining);
        llStreakPreserved = v.findViewById(R.id.ll_streak_preserved);
        tvStreakPreservedCount = v.findViewById(R.id.tv_streak_preserved_count);
        llCrisisEvents = v.findViewById(R.id.ll_crisis_events);
        tvNoCrisisHistory = v.findViewById(R.id.tv_no_crisis_history);

        // Sparkline color
        if (sparklineView != null) sparklineView.setLineColor(Color.parseColor("#7C8FFF"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // LISTENERS + ANIMATIONS
    // ═══════════════════════════════════════════════════════════════════

    private void setupListeners(View v) {
        // Support button
        if (btnOpenSupport != null) {
            UiMotion.attachPressAnimation(btnOpenSupport);
            btnOpenSupport.setOnClickListener(x -> {
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).openSupportPanel();
                }
            });
        }

        // Start action button
        if (btnStartAction != null) {
            UiMotion.attachPressAnimation(btnStartAction);
            btnStartAction.setOnClickListener(x ->
                    startActivity(new Intent(requireContext(), JournalActivity.class)));
        }

        // Card entry animations
        UiMotion.animateCardEntry(v.findViewById(R.id.card_insights_hero), 0);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_risk_classification), 1);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_why_score), 2);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_behavior_trend), 3);
        // card_trajectory animated at index 4 when data arrives (see renderTrajectory)
        UiMotion.animateCardEntry(v.findViewById(R.id.card_next_action), 5);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_insights_timeline), 6);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA OBSERVERS
    // ═══════════════════════════════════════════════════════════════════

    private void observeData() {
        dashboardViewModel.getDashboardInsights().observe(getViewLifecycleOwner(), this::renderInsights);

        dashboardViewModel.getScreenTime().observe(getViewLifecycleOwner(), time -> {
            latestScreenTime = time == null ? 0L : time;
            updateDeviation();
        });

        dashboardViewModel.getUserBaseline().observe(getViewLifecycleOwner(), baseline -> {
            latestBaseline = baseline;
            updateDeviation();
        });

        dashboardViewModel.getUsageHistory().observe(getViewLifecycleOwner(), this::renderSparkline);

        // Risk classification (new observer)
        dashboardViewModel.getLatestClassification().observe(getViewLifecycleOwner(), rc -> {
            latestClassification = rc;
            if (rc != null) {
                renderRiskBars(rc);
                // Trigger trajectory analysis when classification updates
                loadTrajectoryData();
                // Trigger crisis recovery timeline
                loadCrisisRecovery();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 1: WELLNESS RING HERO
    // ═══════════════════════════════════════════════════════════════════

    private void renderInsights(DashboardInsights insights) {
        if (insights == null) return;

        // Wellness ring score
        float riskScore = resolveRiskScore(insights);
        ringView.setScore(riskScore, true);

        // Freshness badge
        updateFreshnessBadge(insights);

        // AI summary text
        String summary = insights.aiEnhanced && insights.aiSummary != null
                ? insights.aiSummary
                : insights.explanationText;
        if (tvAiSummary != null && summary != null) {
            tvAiSummary.setText(summary);
        }

        // Section 3: Why this score
        renderWhySection(insights);

        // Section 5: Next best action
        renderNextAction(insights);

        // Section 6: AI deep analysis
        renderAiDeep(insights);

        // Section 7: Support strip
        if (cardSupport != null) {
            cardSupport.setVisibility(insights.supportRecommended ? View.VISIBLE : View.GONE);
            if (insights.supportRecommended) {
                UiMotion.animateCardEntry(cardSupport, 6);
            }
        }

        updateDeviation();
        renderTimeline();
    }

    private void updateFreshnessBadge(DashboardInsights insights) {
        if (tvFreshnessBadge == null) return;

        // Use classification timestamp if available, otherwise show "Live"
        long timestamp = latestClassification != null ? latestClassification.timestamp : 0L;
        if (timestamp <= 0L) {
            tvFreshnessBadge.setText("Live");
            tvFreshnessBadge.setTextColor(Color.parseColor("#4ADE80"));
            return;
        }

        long ageMs = System.currentTimeMillis() - timestamp;
        long ageMinutes = ageMs / 60000;

        if (ageMinutes < 5) {
            tvFreshnessBadge.setText("Live");
            tvFreshnessBadge.setTextColor(Color.parseColor("#4ADE80"));
        } else if (ageMinutes < 60) {
            tvFreshnessBadge.setText(ageMinutes + "m ago");
            tvFreshnessBadge.setTextColor(Color.parseColor("#7C8FFF"));
        } else if (ageMinutes < 1440) {
            tvFreshnessBadge.setText((ageMinutes / 60) + "h ago");
            tvFreshnessBadge.setTextColor(Color.parseColor("#F5A623"));
        } else {
            tvFreshnessBadge.setText("Stale");
            tvFreshnessBadge.setTextColor(Color.parseColor("#FF6B6B"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 2: 6-CATEGORY RISK BARS
    // ═══════════════════════════════════════════════════════════════════

    private void renderRiskBars(RiskClassification rc) {
        if (llRiskBars == null) return;
        llRiskBars.removeAllViews();

        String[][] categories = {
                {"Digital Addiction", String.valueOf(rc.digitalAddictionScore)},
                {"Stress / Anxiety", String.valueOf(rc.stressAnxietyScore)},
                {"Depression Risk", String.valueOf(rc.depressionRiskScore)},
                {"Social Isolation", String.valueOf(rc.socialIsolationScore)},
                {"Sleep Disruption", String.valueOf(rc.sleepDisruptionScore)},
                {"Low Fulfilment", String.valueOf(rc.lowFulfilmentScore)}
        };

        for (int i = 0; i < categories.length; i++) {
            RiskCategoryBarView bar = new RiskCategoryBarView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp(10);
            bar.setLayoutParams(lp);
            bar.setData(categories[i][0], Float.parseFloat(categories[i][1]), true, i * 80L);
            llRiskBars.addView(bar);
        }

        // Primary concern
        if (rc.primaryCategory != null && llPrimaryConcern != null) {
            String displayName = formatCategoryName(rc.primaryCategory);
            float primaryScore = rc.getScoreForCategory(rc.primaryCategory);
            int primaryColor = getSeverityColor(RiskClassification.Severity.fromScore(primaryScore));

            tvPrimaryConcern.setText("Primary concern: " + displayName);
            tvPrimaryConcern.setTextColor(primaryColor);

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(primaryColor);
            dotPrimaryConcern.setBackground(dot);

            llPrimaryConcern.setVisibility(View.VISIBLE);
        }

        // Confidence badge
        if (tvConfidenceBadge != null) {
            if (rc.confidence >= 0.8f) {
                tvConfidenceBadge.setText("✓ High confidence");
                tvConfidenceBadge.setTextColor(Color.parseColor("#4ADE80"));
            } else if (rc.confidence >= 0.5f) {
                tvConfidenceBadge.setText("~ Moderate confidence");
                tvConfidenceBadge.setTextColor(Color.parseColor("#F5A623"));
            } else {
                tvConfidenceBadge.setText("⟳ Learning — need more data");
                tvConfidenceBadge.setTextColor(Color.parseColor("#8896B0"));
            }
        }

        // Classification Engine Mode Indicator
        renderClassificationEngine(rc);

        // Advanced: Co-morbidity detection
        renderComorbidity(rc);

        // Advanced: Day-over-day comparison
        loadDayComparison();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 3: WHY THIS SCORE
    // ═══════════════════════════════════════════════════════════════════

    private void renderWhySection(DashboardInsights insights) {
        if (tvWhyReasons == null) return;

        // Prefer MultiModal AI classification reasons (richer, includes drift/momentum)
        List<String> reasons = insights.classificationReasons;
        if (reasons == null || reasons.isEmpty()) {
            reasons = insights.reasonItems;
        }
        if (reasons == null || reasons.isEmpty()) {
            if (insights.reasonSummary != null) {
                tvWhyReasons.setText("• " + insights.reasonSummary);
            }
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(7, reasons.size()); i++) {
            if (i > 0) sb.append("\n\n");
            sb.append("• ").append(reasons.get(i));
        }

        // Apply keyword highlighting
        tvWhyReasons.setText(UiFormatting.highlightKeywords(
                requireContext(), sb.toString(),
                "above baseline", "late-night", "binge", "high fragmentation",
                "declining", "consecutive", "increased", "dropped",
                "Drift", "momentum", "crisis", "worsening", "improving",
                "accelerating", "recovery"
        ));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 4: BEHAVIORAL TREND SPARKLINE
    // ═══════════════════════════════════════════════════════════════════

    private void renderSparkline(List<DailyUsage> usageHistory) {
        List<Float> values = new ArrayList<>();
        if (usageHistory != null && !usageHistory.isEmpty()) {
            List<DailyUsage> recent = new ArrayList<>(usageHistory);
            if (recent.size() > 7) recent = recent.subList(0, 7);
            Collections.reverse(recent);
            for (DailyUsage usage : recent) {
                values.add(usage.screenTimeMillis / (60f * 60f * 1000f));
            }
        }
        if (sparklineView != null) sparklineView.setData(values);

        // Compute trend direction
        if (tvTrendDirection != null && values.size() >= 2) {
            float latest = values.get(values.size() - 1);
            float avg = 0;
            for (float v : values) avg += v;
            avg /= values.size();

            float changePct = avg > 0 ? ((latest - avg) / avg) * 100f : 0;
            if (changePct > 5) {
                tvTrendDirection.setText(String.format(Locale.US, "▲ Rising %.0f%% this week", changePct));
                tvTrendDirection.setTextColor(Color.parseColor("#FF6B6B"));
            } else if (changePct < -5) {
                tvTrendDirection.setText(String.format(Locale.US, "▼ Falling %.0f%% this week", Math.abs(changePct)));
                tvTrendDirection.setTextColor(Color.parseColor("#4ADE80"));
            } else {
                tvTrendDirection.setText("~ Stable this week");
                tvTrendDirection.setTextColor(Color.parseColor("#8896B0"));
            }
        }

        // Gap Fix #1: Value labels row ("4.2h → 3.8h → 5.1h")
        if (tvSparklineValues != null && !values.isEmpty()) {
            StringBuilder labels = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) labels.append(" → ");
                labels.append(String.format(Locale.US, "%.1fh", values.get(i)));
            }
            tvSparklineValues.setText(labels.toString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 5: NEXT BEST ACTION
    // ═══════════════════════════════════════════════════════════════════

    private void renderNextAction(DashboardInsights insights) {
        if (insights.nextBestAction == null) return;

        // Context line
        String primaryName = latestClassification != null && latestClassification.primaryCategory != null
                ? formatCategoryName(latestClassification.primaryCategory)
                : "your current pattern";
        if (tvActionContext != null) {
            tvActionContext.setText("Based on your " + primaryName + " score:");
        }

        // Action text
        if (tvActionText != null) {
            tvActionText.setText("\"" + insights.nextBestAction + "\"");
        }

        // Meta tags
        ActionMeta meta = resolveActionMeta(
                latestClassification != null ? latestClassification.primaryCategory : null);
        if (tvActionCategory != null) tvActionCategory.setText("Category: " + meta.category);
        if (tvActionImpact != null) tvActionImpact.setText("Impact: " + meta.impact);
        if (tvActionTime != null) tvActionTime.setText("Time: " + meta.time);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 6: AI DEEP ANALYSIS
    // ═══════════════════════════════════════════════════════════════════

    private void renderAiDeep(DashboardInsights insights) {
        if (cardAiDeep == null) return;
        if (insights.aiEnhanced && insights.aiSummary != null && !insights.aiSummary.trim().isEmpty()) {
            tvAiDeepText.setText(insights.aiSummary);
            cardAiDeep.setVisibility(View.VISIBLE);
            UiMotion.animateCardEntry(cardAiDeep, 5);
        } else {
            cardAiDeep.setVisibility(View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIMELINE
    // ═══════════════════════════════════════════════════════════════════

    private void renderTimeline() {
        if (timelineView == null) return;
        List<TimelineChartView.TimelineEvent> events = new ArrayList<>();

        // Build timeline from REAL data sources

        // 1. Latest check-in mood
        List<com.mindtrace.ai.database.entity.QuestionnaireResponse> responses =
                dashboardViewModel.getStateHistory().getValue();
        if (responses != null && !responses.isEmpty()) {
            com.mindtrace.ai.database.entity.QuestionnaireResponse latest = responses.get(0);
            String moodEmoji = com.mindtrace.ai.util.MoodMapper.getMoodEmoji(latest.mood);
            events.add(new TimelineChartView.TimelineEvent(
                    moodEmoji + " Mood Check-in: " + (latest.mood != null ? latest.mood : "Unknown"),
                    "Stress " + latest.stressLevel + "/5 · Sleep " +
                            String.format(Locale.US, "%.1fh", latest.sleepHours),
                    com.mindtrace.ai.util.MoodMapper.getMoodColor(latest.mood)));
        }

        // 2. Screen time summary
        Long screenTimeMs = dashboardViewModel.getScreenTime().getValue();
        if (screenTimeMs != null && screenTimeMs > 0) {
            long hours = screenTimeMs / (1000L * 60L * 60L);
            long minutes = (screenTimeMs % (1000L * 60L * 60L)) / (1000L * 60L);
            String timeStr = hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
            boolean highUsage = screenTimeMs >= 6L * 60L * 60L * 1000L;
            events.add(new TimelineChartView.TimelineEvent(
                    "📱 Screen Time: " + timeStr,
                    highUsage ? "Above the 6-hour threshold — high usage day."
                              : "Within normal range for today.",
                    highUsage ? 0xFFEF4444 : 0xFF10B981));
        }

        // 3. Top app usage
        com.mindtrace.ai.AppUsageModel topApp = dashboardViewModel.getMostUsedApp().getValue();
        if (topApp != null && topApp.usageTime > 0) {
            long appHours = topApp.usageTime / (1000L * 60L * 60L);
            long appMinutes = (topApp.usageTime % (1000L * 60L * 60L)) / (1000L * 60L);
            String appTimeStr = appHours > 0 ? appHours + "h " + appMinutes + "m" : appMinutes + "m";
            boolean overused = topApp.usageTime >= 2L * 60L * 60L * 1000L;
            events.add(new TimelineChartView.TimelineEvent(
                    "🔝 Top App: " + topApp.appName,
                    appTimeStr + " usage" + (overused ? " — crossed 2-hour overuse threshold." : "."),
                    overused ? 0xFFF59E0B : 0xFF3B82F6));
        }

        // 4. Behavior signals from BehaviorReport
        com.mindtrace.ai.behavior.BehaviorReport behavior =
                dashboardViewModel.getCurrentBehavior().getValue();
        if (behavior != null && behavior.dataAvailable) {
            if (behavior.bingeSessionCount > 0) {
                events.add(new TimelineChartView.TimelineEvent(
                        "⚠️ Binge Session Detected",
                        behavior.bingeSessionCount + " binge session(s). Longest: " +
                                UiFormatting.formatDuration(behavior.longestSessionMillis) + ".",
                        0xFFEF4444));
            }
            if (behavior.lateNightUsageMillis >= 600000L) {
                events.add(new TimelineChartView.TimelineEvent(
                        "🌙 Late-Night Usage",
                        UiFormatting.formatDuration(behavior.lateNightUsageMillis) +
                                " of phone use after 10 PM.",
                        0xFFF59E0B));
            }
            if (behavior.rapidSwitchCount >= 6) {
                events.add(new TimelineChartView.TimelineEvent(
                        "🔀 High App Switching",
                        behavior.rapidSwitchCount + " rapid switches detected today.",
                        0xFFF59E0B));
            }
            if (behavior.hasLoopPattern) {
                events.add(new TimelineChartView.TimelineEvent(
                        "🔁 Dopamine Loop Pattern",
                        "Repetitive app-switching loop detected in today's sessions.",
                        0xFFEF4444));
            }
        }

        // 5. Task completion status
        List<com.mindtrace.ai.database.entity.InterventionTask> tasks =
                dashboardViewModel.getAllTasks().getValue();
        if (tasks != null && !tasks.isEmpty()) {
            int completed = 0;
            for (com.mindtrace.ai.database.entity.InterventionTask t : tasks) {
                if (t.isCompleted) completed++;
            }
            boolean allDone = completed >= tasks.size() && tasks.size() > 0;
            events.add(new TimelineChartView.TimelineEvent(
                    allDone ? "✅ All Tasks Complete" : "📋 Tasks: " + completed + "/" + tasks.size(),
                    allDone ? "Great work — all interventions completed today!"
                            : (tasks.size() - completed) + " task(s) remaining.",
                    allDone ? 0xFF10B981 : 0xFF3B82F6));
        }

        // Fallback if no data at all
        if (events.isEmpty()) {
            events.add(new TimelineChartView.TimelineEvent(
                    "📊 Waiting for Data",
                    "Complete a check-in or use your phone to generate timeline events.",
                    0xFF8896B0));
        }

        timelineView.setEvents(events);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEVIATION
    // ═══════════════════════════════════════════════════════════════════

    private void updateDeviation() {
        if (deviationView == null) return;
        if (latestBaseline == null || latestBaseline.avgScreenTime7d <= 0d) {
            deviationView.showNeutral("Learning your behavior...");
            return;
        }
        deviationView.setDeviation((latestScreenTime / latestBaseline.avgScreenTime7d) - 1d);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private float resolveRiskScore(DashboardInsights insights) {
        if (latestClassification != null) {
            return latestClassification.overallRiskScore;
        }
        if (insights.personalized) {
            return (float) Math.max(0d, Math.min(1d, insights.personalizedRiskScore));
        }
        switch (insights.riskLevel) {
            case HIGH:     return 0.82f;
            case MODERATE: return 0.58f;
            case LOW:
            default:       return 0.24f;
        }
    }

    private String formatCategoryName(String category) {
        if (category == null) return "Unknown";
        switch (category) {
            case "digital_addiction": return "Digital Addiction";
            case "stress_anxiety":   return "Stress / Anxiety";
            case "depression":       return "Depression Risk";
            case "social_isolation": return "Social Isolation";
            case "sleep_disruption": return "Sleep Disruption";
            case "low_fulfilment":   return "Low Fulfilment";
            default:                 return category;
        }
    }

    private int getSeverityColor(RiskClassification.Severity severity) {
        switch (severity) {
            case NONE:     return Color.parseColor("#8896B0");
            case MILD:     return Color.parseColor("#4ADE80");
            case WATCH:    return Color.parseColor("#D4C84A");
            case MODERATE: return Color.parseColor("#F5A623");
            case HIGH:     return Color.parseColor("#E07040");
            case SEVERE:   return Color.parseColor("#FF6B6B");
            default:       return Color.parseColor("#8896B0");
        }
    }

    /** Resolve action metadata based on primary risk category. */
    private ActionMeta resolveActionMeta(@Nullable String category) {
        if (category == null) return new ActionMeta("Self-Awareness", "Builds emotional clarity", "~5 minutes");
        switch (category) {
            case "digital_addiction":
                return new ActionMeta("Digital Detox", "Reduces compulsive checking", "~30 minutes");
            case "stress_anxiety":
                return new ActionMeta("Emotional Regulation", "Calms nervous system", "~5 minutes");
            case "depression":
                return new ActionMeta("Mindfulness", "Stabilizes mood baseline", "~5 minutes");
            case "social_isolation":
                return new ActionMeta("Social Engagement", "Strengthens connection", "~10 minutes");
            case "sleep_disruption":
                return new ActionMeta("Sleep Hygiene", "Improves sleep onset", "~15 minutes");
            case "low_fulfilment":
                return new ActionMeta("Purpose-Building", "Addresses fulfilment gap", "~5 minutes");
            default:
                return new ActionMeta("Self-Care", "Improves overall wellness", "~5 minutes");
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLASSIFICATION ENGINE MODE INDICATOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Renders the Classification Engine transparency section.
     *
     * <p>Displays:
     * <ul>
     *   <li>Engine mode — "Rules Only" vs "Hybrid ML" with colored dot + badge</li>
     *   <li>Data quality level — full / partial / baseline</li>
     *   <li>Feature coverage — N/36 features with real data</li>
     *   <li>ML model count — only shown in hybrid mode</li>
     * </ul>
     *
     * <p>Mode strings from {@code MultiModalClassifier.resolveClassificationMode()}:
     * <pre>
     *   hybrid_full       → ML fully active, all 6 models
     *   hybrid_partial     → ML active, high data
     *   hybrid_partial_N   → N of 6 ML models loaded
     *   hybrid_baseline    → ML active, low data
     *   full               → Rules only, high data
     *   partial            → Rules only, moderate data
     *   baseline_only      → Rules only, minimal data
     * </pre>
     */
    private void renderClassificationEngine(RiskClassification rc) {
        if (llClassificationEngine == null) return;

        String mode = rc.classificationMode != null ? rc.classificationMode : "baseline_only";
        boolean isHybrid = mode.startsWith("hybrid");

        // ── 1. Engine mode dot color ──
        int dotColor;
        if (isHybrid) {
            dotColor = Color.parseColor("#7C8FFF"); // ML blue
        } else if ("full".equals(mode)) {
            dotColor = Color.parseColor("#4ADE80"); // Green
        } else {
            dotColor = Color.parseColor("#F5A623"); // Amber
        }
        if (dotEngineMode != null) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(dotColor);
            dotEngineMode.setBackground(dot);
        }

        // ── 2. Engine mode label text ──
        if (tvEngineModeLabel != null) {
            if (isHybrid) {
                tvEngineModeLabel.setText("Classification Engine: ML-Enhanced");
                tvEngineModeLabel.setTextColor(Color.parseColor("#7C8FFF"));
            } else {
                tvEngineModeLabel.setText("Classification Engine: Rules-Based");
                tvEngineModeLabel.setTextColor(Color.parseColor("#B0BEC5"));
            }
        }

        // ── 3. Engine mode badge ──
        if (tvEngineModeBadge != null) {
            String badgeText;
            int badgeColor;
            switch (mode) {
                case "hybrid_full":
                    badgeText = "ML Full";
                    badgeColor = Color.parseColor("#7C8FFF");
                    break;
                case "hybrid_partial":
                    badgeText = "ML Active";
                    badgeColor = Color.parseColor("#7C8FFF");
                    break;
                case "hybrid_baseline":
                    badgeText = "ML Baseline";
                    badgeColor = Color.parseColor("#A78BFA");
                    break;
                case "full":
                    badgeText = "Full Data";
                    badgeColor = Color.parseColor("#4ADE80");
                    break;
                case "partial":
                    badgeText = "Partial";
                    badgeColor = Color.parseColor("#F5A623");
                    break;
                default:
                    // Handles "baseline_only" and "hybrid_partial_N"
                    if (mode.startsWith("hybrid_partial_")) {
                        badgeText = "ML " + mode.substring("hybrid_partial_".length()) + "/6";
                        badgeColor = Color.parseColor("#A78BFA");
                    } else {
                        badgeText = "Baseline";
                        badgeColor = Color.parseColor("#8896B0");
                    }
                    break;
            }
            tvEngineModeBadge.setText(badgeText);
            tvEngineModeBadge.setTextColor(badgeColor);
        }

        // ── 4. Data quality label ──
        if (tvDataQualityLabel != null) {
            String quality;
            if (rc.featureDataCount >= 28) {
                quality = "✓ Excellent data coverage";
            } else if (rc.featureDataCount >= 20) {
                quality = "✓ Good data coverage";
            } else if (rc.featureDataCount >= 10) {
                quality = "~ Moderate data coverage";
            } else {
                quality = "⟳ Limited data — keep using the app";
            }
            tvDataQualityLabel.setText(quality);
        }

        // ── 5. Feature coverage fraction ──
        if (tvFeatureCoverage != null) {
            tvFeatureCoverage.setText(
                    String.format(Locale.US, "%d/36 features", rc.featureDataCount));
        }

        // ── 6. ML model status (hybrid mode only) ──
        if (tvMlModelStatus != null) {
            if (isHybrid) {
                String mlDetail;
                if ("hybrid_full".equals(mode)) {
                    mlDetail = "⚡ All 6 ML models active · 40% rules + 60% ML blending";
                } else if (mode.startsWith("hybrid_partial_")) {
                    String count = mode.substring("hybrid_partial_".length());
                    mlDetail = "⚡ " + count + "/6 ML models loaded · partial blending active";
                } else {
                    mlDetail = "⚡ ML models active · hybrid analysis running";
                }
                tvMlModelStatus.setText(mlDetail);
                tvMlModelStatus.setVisibility(View.VISIBLE);
            } else {
                tvMlModelStatus.setVisibility(View.GONE);
            }
        }

        // Show the container
        llClassificationEngine.setVisibility(View.VISIBLE);
    }

    /** Simple action metadata container. */
    private static class ActionMeta {
        final String category, impact, time;
        ActionMeta(String category, String impact, String time) {
            this.category = category;
            this.impact = impact;
            this.time = time;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 4.5: TRAJECTORY ASSESSMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Loads 7-day trend report from ClassificationTrendAnalyzer and renders
     * the trajectory card. Runs analysis on background thread.
     */
    private void loadTrajectoryData() {
        if (cardTrajectory == null) return;
        dashboardViewModel.loadTrendReport(7, report -> {
            if (report == null || !isAdded()) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            cardTrajectory.setVisibility(View.GONE));
                }
                return;
            }
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> renderTrajectory(report));
            }
        });
    }

    private void renderTrajectory(ClassificationTrendAnalyzer.TrendReport report) {
        if (cardTrajectory == null) return;

        // ── Badge: trajectory direction ──
        String badgeText;
        int badgeColor;
        switch (report.overallRiskTrajectory) {
            case "rapidly_improving":
                badgeText = "▼▼ Rapidly Improving";
                badgeColor = Color.parseColor("#4ADE80");
                break;
            case "gradually_improving":
                badgeText = "▼ Improving";
                badgeColor = Color.parseColor("#4ADE80");
                break;
            case "gradually_worsening":
                badgeText = "▲ Worsening";
                badgeColor = Color.parseColor("#F5A623");
                break;
            case "rapidly_worsening":
                badgeText = "▲▲ Rapidly Worsening";
                badgeColor = Color.parseColor("#FF6B6B");
                break;
            default:
                badgeText = "~ Stable";
                badgeColor = Color.parseColor("#8896B0");
                break;
        }
        if (tvTrajectoryBadge != null) {
            tvTrajectoryBadge.setText(badgeText);
            tvTrajectoryBadge.setTextColor(badgeColor);
        }

        // ── Summary sentence ──
        if (tvTrajectorySummary != null) {
            int avgPct = Math.round(report.averageRisk * 100);
            int currentPct = Math.round(report.currentRisk * 100);
            String confLabel = report.averageConfidence >= 0.8f ? "high"
                    : report.averageConfidence >= 0.5f ? "moderate" : "building";
            tvTrajectorySummary.setText(String.format(Locale.US,
                    "Over the past %d days (%d data points), your average risk was %d%% " +
                    "and is currently at %d%%. Data confidence: %s.",
                    report.daysAnalyzed, report.dataPoints, avgPct, currentPct, confLabel));
        }

        // ── Worsening / Improving categories ──
        if (tvTrajectoryWorsening != null) {
            tvTrajectoryWorsening.setText(report.getFastestWorseningCategory());
        }
        if (tvTrajectoryImproving != null) {
            tvTrajectoryImproving.setText(report.getFastestImprovingCategory());
        }

        // ── Feature drift ──
        if (tvTrajectoryDrift != null && report.topDriftingFeature != null) {
            tvTrajectoryDrift.setText(report.topDriftingFeature);
        }

        // ── Top risk drivers ──
        if (tvTrajectoryDrivers != null && report.topRiskDrivers != null
                && !report.topRiskDrivers.isEmpty()) {
            StringBuilder drivers = new StringBuilder("Top drivers: ");
            for (int i = 0; i < report.topRiskDrivers.size(); i++) {
                if (i > 0) drivers.append(" · ");
                drivers.append(report.topRiskDrivers.get(i));
            }
            tvTrajectoryDrivers.setText(drivers.toString());
        }

        // ── Data quality ──
        if (tvTrajectoryDataQuality != null) {
            String quality;
            if (report.dataPoints >= 6) {
                quality = "✓ Strong data coverage (" + report.dataPoints + " days)";
            } else if (report.dataPoints >= 3) {
                quality = "~ Moderate coverage (" + report.dataPoints + " days) — trends will sharpen";
            } else {
                quality = "⟳ Minimal data (" + report.dataPoints + " days) — keep using the app";
            }
            if (report.crisisCount > 0) {
                quality += " · " + report.crisisCount + " crisis flag(s)";
            }
            tvTrajectoryDataQuality.setText(quality);
        }

        // Show + animate
        cardTrajectory.setVisibility(View.VISIBLE);
        UiMotion.animateCardEntry(cardTrajectory, 4);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED A6: CO-MORBIDITY DETECTION
    // ═══════════════════════════════════════════════════════════════════

    private void renderComorbidity(RiskClassification rc) {
        if (cardComorbidity == null) return;
        int elevated = rc.getElevatedCategoryCount();
        if (elevated >= 3) {
            String[] topCats = rc.getTopCategories(0.45f);
            StringBuilder catNames = new StringBuilder();
            for (int i = 0; i < Math.min(4, topCats.length); i++) {
                if (i > 0) catNames.append(" · ");
                catNames.append(formatCategoryName(topCats[i]));
            }

            tvComorbidityText.setText(String.format(Locale.US,
                    "%d risk categories are simultaneously elevated. " +
                    "When multiple areas converge, the compound effect on " +
                    "wellbeing is greater than each area alone.", elevated));
            tvComorbidityCategories.setText(catNames.toString());
            cardComorbidity.setVisibility(View.VISIBLE);
            UiMotion.animateCardEntry(cardComorbidity, 7);
        } else {
            cardComorbidity.setVisibility(View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED A2: DAY-OVER-DAY COMPARISON
    // ═══════════════════════════════════════════════════════════════════

    private void loadDayComparison() {
        if (cardDayComparison == null) return;
        dashboardViewModel.loadDayComparison(comparison -> {
            if (!isAdded()) return;
            if (comparison == null || !comparison.hasPreviousDay) {
                requireActivity().runOnUiThread(() ->
                        cardDayComparison.setVisibility(View.GONE));
                return;
            }
            requireActivity().runOnUiThread(() -> renderDayComparison(comparison));
        });
    }

    private void renderDayComparison(
            com.mindtrace.ai.repository.ClassificationRepository.DayComparison comp) {
        int yesterdayPct = Math.round(comp.yesterdayRisk * 100);
        int todayPct = Math.round(comp.todayRisk * 100);

        tvCompareYesterdayScore.setText(String.valueOf(yesterdayPct));
        tvCompareTodayScore.setText(String.valueOf(todayPct));

        // Severity labels + colors
        RiskClassification.Severity ySev = RiskClassification.Severity.fromScore(comp.yesterdayRisk);
        RiskClassification.Severity tSev = RiskClassification.Severity.fromScore(comp.todayRisk);
        tvCompareYesterdayLabel.setText(ySev.label);
        tvCompareYesterdayLabel.setTextColor(getSeverityColor(ySev));
        tvCompareTodayLabel.setText(tSev.label);
        tvCompareTodayLabel.setTextColor(getSeverityColor(tSev));

        // Delta
        float deltaPct = comp.delta * 100f;
        if (deltaPct > 3) {
            tvCompareDelta.setText(String.format(Locale.US, "▲ +%.0f%% — Risk increased", deltaPct));
            tvCompareDelta.setTextColor(Color.parseColor("#FF6B6B"));
        } else if (deltaPct < -3) {
            tvCompareDelta.setText(String.format(Locale.US, "▼ %.0f%% — Risk decreased", deltaPct));
            tvCompareDelta.setTextColor(Color.parseColor("#4ADE80"));
        } else {
            tvCompareDelta.setText("~ No significant change");
            tvCompareDelta.setTextColor(Color.parseColor("#8896B0"));
        }

        cardDayComparison.setVisibility(View.VISIBLE);
        UiMotion.animateCardEntry(cardDayComparison, 6);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 7.5: CRISIS RECOVERY TIMELINE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Loads crisis recovery state and recent crisis events on a background
     * thread, then renders the recovery timeline card on the UI thread.
     *
     * <p>Data flow:</p>
     * <pre>
     *   diskIO: StreakRecoveryManager.getRecoveryState()
     *   diskIO: CrisisEventDao.getEventsSince(30 days)
     *     → mainThread: renderCrisisRecovery(state, events)
     * </pre>
     */
    private void loadCrisisRecovery() {
        if (cardCrisisRecovery == null) return;

        AppExecutors.diskIO().execute(() -> {
            try {
                StreakRecoveryManager manager = new StreakRecoveryManager();
                StreakRecoveryManager.RecoveryState state =
                        manager.getRecoveryState(requireContext());

                // Load last 30 days of crisis events
                long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
                AppDatabase db = AppDatabase.getInstance(requireContext());
                List<CrisisEvent> events = db.crisisEventDao().getEventsSince(thirtyDaysAgo);

                if (!isAdded()) return;
                AppExecutors.mainThread().execute(() ->
                        renderCrisisRecovery(state, events));

            } catch (Exception e) {
                if (isAdded()) {
                    AppExecutors.mainThread().execute(() ->
                            cardCrisisRecovery.setVisibility(View.GONE));
                }
            }
        });
    }

    /**
     * Renders the Crisis Recovery Timeline card.
     *
     * <p>Three visual states:</p>
     * <ol>
     *   <li><b>Grace Active</b> — shield badge, timer, preserved streak count</li>
     *   <li><b>Recovery Phase</b> — gentle return / recovery streak messaging</li>
     *   <li><b>Normal</b> — shows recent crisis history only (or hides if none)</li>
     * </ol>
     */
    private void renderCrisisRecovery(StreakRecoveryManager.RecoveryState state,
                                       List<CrisisEvent> events) {
        if (cardCrisisRecovery == null) return;

        boolean hasRecovery = !"normal".equals(state.phase);
        boolean hasEvents = events != null && !events.isEmpty();

        // If no recovery state AND no recent events, hide the card entirely
        if (!hasRecovery && !hasEvents) {
            cardCrisisRecovery.setVisibility(View.GONE);
            return;
        }

        // ── 1. Phase badge ──
        if (tvRecoveryPhaseBadge != null) {
            switch (state.phase) {
                case "grace":
                    tvRecoveryPhaseBadge.setText("🛡 Grace Active");
                    tvRecoveryPhaseBadge.setTextColor(Color.parseColor("#7C8FFF"));
                    break;
                case "gentle_return":
                    tvRecoveryPhaseBadge.setText("💙 Welcome Back");
                    tvRecoveryPhaseBadge.setTextColor(Color.parseColor("#4ADE80"));
                    break;
                case "recovery_streak":
                    tvRecoveryPhaseBadge.setText("🌱 Rebuilding");
                    tvRecoveryPhaseBadge.setTextColor(Color.parseColor("#4ADE80"));
                    break;
                default:
                    tvRecoveryPhaseBadge.setText("✓ Stable");
                    tvRecoveryPhaseBadge.setTextColor(Color.parseColor("#8896B0"));
                    break;
            }
        }

        // ── 2. Recovery message ──
        if (tvRecoveryMessage != null) {
            if (hasRecovery && state.message != null && !state.message.isEmpty()) {
                tvRecoveryMessage.setText(state.message);
                tvRecoveryMessage.setVisibility(View.VISIBLE);
            } else if (hasEvents) {
                tvRecoveryMessage.setText(
                        "Your recent crisis history shows your resilience. " +
                        "Every challenge navigated builds strength.");
                tvRecoveryMessage.setVisibility(View.VISIBLE);
            } else {
                tvRecoveryMessage.setVisibility(View.GONE);
            }
        }

        // ── 3. Grace period timer ──
        if (llGraceTimer != null) {
            if (state.graceActive && state.graceHoursRemaining > 0) {
                llGraceTimer.setVisibility(View.VISIBLE);
                if (tvGraceRemaining != null) {
                    tvGraceRemaining.setText(state.graceHoursRemaining + "h");
                }
            } else {
                llGraceTimer.setVisibility(View.GONE);
            }
        }

        // ── 4. Streak preserved ──
        if (llStreakPreserved != null) {
            if (state.preservedStreak > 0) {
                llStreakPreserved.setVisibility(View.VISIBLE);
                if (tvStreakPreservedCount != null) {
                    tvStreakPreservedCount.setText(
                            state.preservedStreak + " day" +
                            (state.preservedStreak != 1 ? "s" : ""));
                }
            } else {
                llStreakPreserved.setVisibility(View.GONE);
            }
        }

        // ── 5. Crisis event list ──
        if (llCrisisEvents != null) {
            llCrisisEvents.removeAllViews();

            if (hasEvents) {
                if (tvNoCrisisHistory != null) tvNoCrisisHistory.setVisibility(View.GONE);

                // Show up to 5 most recent events
                int limit = Math.min(5, events.size());
                for (int i = 0; i < limit; i++) {
                    CrisisEvent event = events.get(i);
                    llCrisisEvents.addView(buildCrisisEventRow(event));
                }

                // "More" indicator if there are additional events
                if (events.size() > 5) {
                    TextView moreText = new TextView(requireContext());
                    moreText.setText("+ " + (events.size() - 5) + " more events");
                    moreText.setTextColor(Color.parseColor("#8896B0"));
                    moreText.setTextSize(11);
                    moreText.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    moreParams.topMargin = dp(6);
                    moreText.setLayoutParams(moreParams);
                    llCrisisEvents.addView(moreText);
                }
            } else {
                if (tvNoCrisisHistory != null) tvNoCrisisHistory.setVisibility(View.VISIBLE);
            }
        }

        // Show + animate
        cardCrisisRecovery.setVisibility(View.VISIBLE);
        UiMotion.animateCardEntry(cardCrisisRecovery, 8);
    }

    /**
     * Builds a single crisis event row for the timeline list.
     *
     * <p>Layout per row:</p>
     * <pre>
     *   [●] ELEVATED · Apr 27 · 12:30 PM · Resolved via breathing
     * </pre>
     */
    private View buildCrisisEventRow(CrisisEvent event) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(6);
        row.setLayoutParams(rowParams);

        // ── Severity dot ──
        View dot = new View(requireContext());
        int dotSize = dp(8);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
        dot.setLayoutParams(dotParams);
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(getCrisisLevelColor(event.crisisLevel));
        dot.setBackground(dotDrawable);
        row.addView(dot);

        // ── Event text ──
        TextView text = new TextView(requireContext());
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dp(8));
        text.setLayoutParams(textParams);
        text.setTextColor(Color.parseColor("#E0E6ED"));
        text.setTextSize(12);

        StringBuilder sb = new StringBuilder();
        sb.append(formatCrisisLevel(event.crisisLevel));

        // Date
        if (event.timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d · h:mm a", Locale.US);
            sb.append(" · ").append(sdf.format(new Date(event.timestamp)));
        }

        // Status
        if (event.isResolved()) {
            sb.append(" · ✓ Resolved");
            if (event.resolutionMethod != null) {
                sb.append(" via ").append(formatResolutionMethod(event.resolutionMethod));
            }
        } else if (event.isActive()) {
            sb.append(" · ⟳ Active");
        }

        // Duration (if resolved)
        long durationMs = event.getDurationMs();
        if (durationMs > 0) {
            long durationMin = durationMs / 60000;
            if (durationMin < 60) {
                sb.append(" (").append(durationMin).append("m)");
            } else {
                sb.append(" (").append(durationMin / 60).append("h ");
                sb.append(durationMin % 60).append("m)");
            }
        }

        text.setText(sb.toString());
        row.addView(text);

        return row;
    }

    private int getCrisisLevelColor(String level) {
        if (level == null) return Color.parseColor("#8896B0");
        switch (level) {
            case "CRITICAL": return Color.parseColor("#FF6B6B");
            case "URGENT":   return Color.parseColor("#E07040");
            case "ELEVATED": return Color.parseColor("#F5A623");
            case "WATCH":    return Color.parseColor("#D4C84A");
            default:         return Color.parseColor("#8896B0");
        }
    }

    private String formatCrisisLevel(String level) {
        if (level == null) return "Unknown";
        switch (level) {
            case "CRITICAL": return "🔴 Critical";
            case "URGENT":   return "🟠 Urgent";
            case "ELEVATED": return "🟡 Elevated";
            case "WATCH":    return "⚪ Watch";
            default:         return level;
        }
    }

    private String formatResolutionMethod(String method) {
        if (method == null) return "";
        switch (method) {
            case "self_resolved":      return "self";
            case "grounding_exercise": return "grounding";
            case "breathing_exercise": return "breathing";
            case "contacted_friend":   return "social support";
            case "called_helpline":    return "helpline";
            case "journaled":          return "journaling";
            case "dismissed":          return "dismiss";
            default:                    return method;
        }
    }
}
