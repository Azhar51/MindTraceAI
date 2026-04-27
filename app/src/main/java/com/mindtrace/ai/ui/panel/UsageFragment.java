package com.mindtrace.ai.ui.panel;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mindtrace.ai.ui.FocusSessionActivity;
import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.mindtrace.ai.ui.components.UsageTrendGraphView;
import com.mindtrace.ai.ui.components.UsageTrendGraphView.TrendData;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.ai.EfficacyMetrics;
import com.mindtrace.ai.ui.components.EmptyStateHelper;
import com.mindtrace.ai.behavior.BehaviorReport;
import com.mindtrace.ai.database.entity.BehaviorUsageSummary;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.UsageSession;
import com.mindtrace.ai.ui.AppUsageAdapter;
import com.mindtrace.ai.ui.UiFormatting;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.ui.components.StateChipView;
import com.mindtrace.ai.ui.components.UsageHeatmapView;
import com.mindtrace.ai.ui.components.UsageRingView;
import com.mindtrace.ai.utils.UiHaptics;
import com.mindtrace.ai.viewmodel.DashboardViewModel;
import com.mindtrace.ai.viewmodel.SettingsViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.graphics.Color;
import com.mindtrace.ai.ai.AppCategoryMapper;

public class UsageFragment extends Fragment {
    private enum RangeMode {
        TODAY,
        WEEK,
        MONTH
    }

    private DashboardViewModel dashboardViewModel;
    private SettingsViewModel settingsViewModel;
    private final AppUsageAdapter appUsageAdapter = new AppUsageAdapter();

    private View cardHero, cardHeatmap, cardGraph, cardBehavior, cardCategory, cardTopApp;
    private UsageRingView ringUsage;
    private UsageHeatmapView heatmapView;
    private UsageTrendGraphView usageTimelineChart;
    private PieChart categoryPieChart;
    private MaterialButtonToggleGroup rangeToggle;
    private TextView tvChartSummary, tvHeroTime, tvHeroDelta, tvHeroWeekly;
    private TextView tvHeatmapDetail, tvAppCount, tvCategorySummary;
    private TextView tvStatPickupsLabel, tvStatPickupsValue;
    private TextView tvStatSwitchesLabel, tvStatSwitchesValue;
    private TextView tvStatLateLabel, tvStatLateValue;
    private TextView tvStatAppsLabel, tvStatAppsValue;
    private StateChipView tvBehaviorBadge;
    private TextView tvBehaviorSummary, tvBehaviorExplanation, tvBehaviorTrend;
    private TextView tvScoreFrag, tvScoreSwitch, tvScoreBinge, tvScoreNight, tvScoreDom;
    private ImageView ivTopIcon;
    private TextView tvTopName, tvTopMeta;
    private RecyclerView rvApps;
    private MaterialButton btnStartFocus, btnDailyReport;

    // Behavioral Fingerprint Fields
    private View cardFingerprint;
    private TextView tvLoopApp1, tvLoopApp2, tvLoopInsight;
    private com.google.android.material.progressindicator.LinearProgressIndicator progressActivePassive;
    private TextView tvConsumptionInsight, tvChronotypeTitle, tvChronotypeInsight;

    // Efficacy Pipeline views
    private View cardEfficacyPipeline;
    private StateChipView chipEfficacyStatus;
    private TextView tvEfficacyOverallScore, tvEfficacyOverallLabel, tvEfficacyMeasuredCount;
    private LinearLayout llEfficacyCategories;
    private TextView tvEfficacySummary;
    private LinearLayout llEfficacyObserving;
    private TextView tvEfficacyObserving;

    private boolean privacyModeEnabled;
    private long latestScreenTime;
    private List<DailyUsage> latestUsageHistory = Collections.emptyList();
    private List<UsageSession> latestTodaySessions = Collections.emptyList();
    private BehaviorReport latestBehaviorReport;
    private BehaviorUsageSummary latestBehaviorSummary;
    private DashboardInsights latestInsights;
    private RangeMode currentRangeMode = RangeMode.TODAY;

    public UsageFragment() {
        super(R.layout.fragment_usage);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);
        settingsViewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);

        cardHero = view.findViewById(R.id.card_usage_hero);
        cardHeatmap = view.findViewById(R.id.card_usage_heatmap);
        cardGraph = view.findViewById(R.id.card_usage_graph);
        cardBehavior = view.findViewById(R.id.card_usage_behavior);
        cardCategory = view.findViewById(R.id.card_usage_category);
        cardTopApp = view.findViewById(R.id.card_usage_top_app);
        ringUsage = view.findViewById(R.id.ring_usage);
        heatmapView = view.findViewById(R.id.view_usage_heatmap);
        tvHeroTime = view.findViewById(R.id.tv_usage_hero_time);
        tvHeroDelta = view.findViewById(R.id.tv_usage_hero_delta);
        tvHeroWeekly = view.findViewById(R.id.tv_usage_hero_weekly);
        tvHeatmapDetail = view.findViewById(R.id.tv_heatmap_detail);
        
        btnStartFocus = view.findViewById(R.id.btn_start_focus);
        btnDailyReport = view.findViewById(R.id.btn_daily_report);

        tvAppCount = view.findViewById(R.id.tv_usage_app_count);
        rvApps = view.findViewById(R.id.rv_usage_apps);
        
        tvCategorySummary = view.findViewById(R.id.tv_category_summary);
        usageTimelineChart = view.findViewById(R.id.chart_usage_timeline);
        categoryPieChart = view.findViewById(R.id.chart_usage_category);
        rangeToggle = view.findViewById(R.id.toggle_usage_range);
        tvChartSummary = view.findViewById(R.id.tv_usage_chart_summary);
        tvBehaviorBadge = view.findViewById(R.id.tv_usage_behavior_badge);
        tvBehaviorSummary = view.findViewById(R.id.tv_usage_behavior_summary);
        tvBehaviorExplanation = view.findViewById(R.id.tv_usage_behavior_explanation);
        tvBehaviorTrend = view.findViewById(R.id.tv_usage_behavior_trend);
        tvScoreFrag = view.findViewById(R.id.tv_score_frag);
        tvScoreSwitch = view.findViewById(R.id.tv_score_switch);
        tvScoreBinge = view.findViewById(R.id.tv_score_binge);
        tvScoreNight = view.findViewById(R.id.tv_score_night);
        tvScoreDom = view.findViewById(R.id.tv_score_dom);
        ivTopIcon = view.findViewById(R.id.iv_usage_top_icon);
        tvTopName = view.findViewById(R.id.tv_usage_top_name);
        tvTopMeta = view.findViewById(R.id.tv_usage_top_meta);

        // Fingerprint views
        cardFingerprint = view.findViewById(R.id.card_behavioral_fingerprint);
        tvLoopApp1 = view.findViewById(R.id.tv_loop_app1);
        tvLoopApp2 = view.findViewById(R.id.tv_loop_app2);
        tvLoopInsight = view.findViewById(R.id.tv_loop_insight);
        progressActivePassive = view.findViewById(R.id.progress_active_passive);
        tvConsumptionInsight = view.findViewById(R.id.tv_consumption_insight);
        tvChronotypeTitle = view.findViewById(R.id.tv_chronotype_title);
        tvChronotypeInsight = view.findViewById(R.id.tv_chronotype_insight);

        // Efficacy Pipeline views
        cardEfficacyPipeline = view.findViewById(R.id.card_efficacy_pipeline);
        chipEfficacyStatus = view.findViewById(R.id.chip_efficacy_status);
        tvEfficacyOverallScore = view.findViewById(R.id.tv_efficacy_overall_score);
        tvEfficacyOverallLabel = view.findViewById(R.id.tv_efficacy_overall_label);
        tvEfficacyMeasuredCount = view.findViewById(R.id.tv_efficacy_measured_count);
        llEfficacyCategories = view.findViewById(R.id.ll_efficacy_categories);
        tvEfficacySummary = view.findViewById(R.id.tv_efficacy_summary);
        llEfficacyObserving = view.findViewById(R.id.ll_efficacy_observing);
        tvEfficacyObserving = view.findViewById(R.id.tv_efficacy_observing);

        View statPickups = view.findViewById(R.id.stat_pickups);
        tvStatPickupsLabel = statPickups.findViewById(R.id.tv_stat_label);
        tvStatPickupsValue = statPickups.findViewById(R.id.tv_stat_value);
        tvStatPickupsLabel.setText("Launches");
        View statSwitches = view.findViewById(R.id.stat_switches);
        tvStatSwitchesLabel = statSwitches.findViewById(R.id.tv_stat_label);
        tvStatSwitchesValue = statSwitches.findViewById(R.id.tv_stat_value);
        tvStatSwitchesLabel.setText("Switches");
        View statLate = view.findViewById(R.id.stat_late_night);
        tvStatLateLabel = statLate.findViewById(R.id.tv_stat_label);
        tvStatLateValue = statLate.findViewById(R.id.tv_stat_value);
        tvStatLateLabel.setText("Late night");
        View statApps = view.findViewById(R.id.stat_apps);
        tvStatAppsLabel = statApps.findViewById(R.id.tv_stat_label);
        tvStatAppsValue = statApps.findViewById(R.id.tv_stat_value);
        tvStatAppsLabel.setText("Apps");

        rvApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvApps.setAdapter(appUsageAdapter);
        rvApps.setNestedScrollingEnabled(false);

        UiMotion.animateCardEntry(cardHero, 0);
        UiMotion.animateCardEntry(cardHeatmap, 1);
        if (cardFingerprint != null) UiMotion.animateCardEntry(cardFingerprint, 2);
        if (cardEfficacyPipeline != null) UiMotion.animateCardEntry(cardEfficacyPipeline, 3);
        UiMotion.animateCardEntry(cardGraph, 4);
        UiMotion.animateCardEntry(cardBehavior, 5);
        UiMotion.animateCardEntry(cardCategory, 6);
        UiMotion.animateCardEntry(cardTopApp, 7);

        heatmapView.setOnHourSelectedListener((hour, minutes) -> {
            UiHaptics.tick(heatmapView);
            String label = labelForHour(hour);
            String insight = generateHourlyInsight(hour, minutes);
            tvHeatmapDetail.setText(String.format(Locale.getDefault(), "%s — %dm | %s", label.isEmpty() ? hour + ":00" : label, Math.round(minutes), insight));
        });

        View btnInDepth = view.findViewById(R.id.btn_in_depth_review);
        if (btnInDepth != null) {
            btnInDepth.setOnClickListener(v -> {
                UiHaptics.click(v);
                startActivity(new Intent(requireContext(), HourlyReviewActivity.class));
            });
        }

        // Setup action buttons with Haptics
        MaterialButton btnPomodoro = view.findViewById(R.id.btn_hub_pomodoro);
        if (btnPomodoro != null) {
            btnPomodoro.setOnClickListener(v -> {
                UiHaptics.click(v);
                startActivity(new Intent(requireContext(), com.mindtrace.ai.ui.FocusSessionActivity.class));
            });
        }

        MaterialButton btnBlocker = view.findViewById(R.id.btn_hub_blocker);
        if (btnBlocker != null) {
            btnBlocker.setOnClickListener(v -> {
                UiHaptics.click(v);
                startActivity(new Intent(requireContext(), com.mindtrace.ai.ui.FocusPanelActivity.class));
            });
        }
        
        MaterialButton btnReport = view.findViewById(R.id.btn_daily_report);
        if (btnReport != null) {
            btnReport.setOnClickListener(v -> {
                UiHaptics.click(v);
                startActivity(new Intent(requireContext(), com.mindtrace.ai.ui.DailyReportActivity.class));
            });
        }



        setupChart();
        setupPieChart();
        setupRangeToggle();
        observeData();
    }

    private void showFocusModeOptions() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.sheet_focus_options, null);
        dialog.setContentView(sheetView);

        MaterialButton btnPomodoro = sheetView.findViewById(R.id.btn_pomodoro_timer);
        MaterialButton btnAppBlocker = sheetView.findViewById(R.id.btn_app_blocker_settings);

        btnPomodoro.setOnClickListener(v -> {
            UiHaptics.click(v);
            dialog.dismiss();
            startActivity(new Intent(requireContext(), com.mindtrace.ai.ui.FocusSessionActivity.class));
        });

        btnAppBlocker.setOnClickListener(v -> {
            UiHaptics.click(v);
            dialog.dismiss();
            startActivity(new Intent(requireContext(), com.mindtrace.ai.ui.FocusPanelActivity.class));
        });

        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dashboardViewModel != null) {
            dashboardViewModel.refreshDashboard();
        }
    }

    private void setupChart() {
        // Initialization handled internally by UsageTrendGraphView
    }

    private void setupPieChart() {
        categoryPieChart.setUsePercentValues(true);
        categoryPieChart.getDescription().setEnabled(false);
        categoryPieChart.setExtraOffsets(5, 10, 5, 5);
        categoryPieChart.setDragDecelerationFrictionCoef(0.95f);
        categoryPieChart.setDrawHoleEnabled(true);
        categoryPieChart.setHoleColor(Color.TRANSPARENT);
        categoryPieChart.setTransparentCircleColor(Color.TRANSPARENT);
        categoryPieChart.setTransparentCircleAlpha(110);
        categoryPieChart.setHoleRadius(58f);
        categoryPieChart.setTransparentCircleRadius(61f);
        categoryPieChart.setDrawCenterText(true);
        categoryPieChart.setRotationAngle(0);
        categoryPieChart.setRotationEnabled(true);
        categoryPieChart.setHighlightPerTapEnabled(true);
        categoryPieChart.getLegend().setEnabled(false);
        categoryPieChart.setNoDataText("Category data will appear after usage is recorded.");
        categoryPieChart.setEntryLabelColor(Color.WHITE);
        categoryPieChart.setEntryLabelTextSize(11f);
    }

    private void setupRangeToggle() {
        rangeToggle.check(R.id.btn_usage_today);
        rangeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btn_usage_week) {
                currentRangeMode = RangeMode.WEEK;
            } else if (checkedId == R.id.btn_usage_month) {
                currentRangeMode = RangeMode.MONTH;
            } else {
                currentRangeMode = RangeMode.TODAY;
            }
            renderUsageTimeline();
        });
    }

    private void observeData() {
        settingsViewModel.getSettingsState().observe(getViewLifecycleOwner(), state -> {
            privacyModeEnabled = state != null && state.privacyMode;
            appUsageAdapter.setPrivacyModeEnabled(privacyModeEnabled);
            renderMostUsedApp(dashboardViewModel.getMostUsedApp().getValue());
        });

        dashboardViewModel.getScreenTime().observe(getViewLifecycleOwner(), total -> {
            latestScreenTime = total == null ? 0L : total;
            tvHeroTime.setText(UiFormatting.formatDuration(latestScreenTime));
            updateHeroRing();
            updateHeatmap();
            renderUsageTimeline();
        });

        dashboardViewModel.getAppUsageList().observe(getViewLifecycleOwner(), apps -> {
            renderAppList(apps);
            renderCategoryPieChart(apps);
        });

        dashboardViewModel.getCurrentBehavior().observe(getViewLifecycleOwner(), report -> {
            latestBehaviorReport = report;
            renderBehaviorCard();
            renderBehavioralFingerprint();
        });

        dashboardViewModel.getTodayBehaviorSummary().observe(getViewLifecycleOwner(), summary -> {
            latestBehaviorSummary = summary;
            renderBehaviorCard();
        });

        dashboardViewModel.getDashboardInsights().observe(getViewLifecycleOwner(), insights -> {
            latestInsights = insights;
            renderBehaviorCard();
        });

        dashboardViewModel.getMostUsedApp().observe(getViewLifecycleOwner(), this::renderMostUsedApp);
        dashboardViewModel.getUsageHistory().observe(getViewLifecycleOwner(), usageHistory -> {
            latestUsageHistory = usageHistory == null ? Collections.emptyList() : usageHistory;
            renderDailySummary(latestUsageHistory);
            updateHeroRing();
            renderUsageTimeline();
        });
        dashboardViewModel.getTodayUsageSessions().observe(getViewLifecycleOwner(), sessions -> {
            latestTodaySessions = sessions == null ? Collections.emptyList() : sessions;
            updateHeatmap();
            renderUsageTimeline();
        });

        // ── Efficacy Pipeline observer ──
        dashboardViewModel.getEfficacyMetrics().observe(getViewLifecycleOwner(), this::renderEfficacyPipeline);
    }

    private void renderDailySummary(List<DailyUsage> usageHistory) {
        if (usageHistory == null || usageHistory.isEmpty()) {
            tvHeroWeekly.setText("Learning your usage pattern...");
            tvStatPickupsValue.setText("0");
            tvStatSwitchesValue.setText("0");
            tvStatLateValue.setText("0m");
            tvStatAppsValue.setText("0");
            return;
        }
        DailyUsage todayUsage = usageHistory.get(0);
        int launchCount = todayUsage.totalLaunchCount > 0 ? todayUsage.totalLaunchCount : todayUsage.unlockCount;
        int switchCount = todayUsage.totalAppSwitchCount > 0 ? todayUsage.totalAppSwitchCount : todayUsage.appSwitches;
        tvStatPickupsValue.setText(String.valueOf(launchCount));
        tvStatSwitchesValue.setText(String.valueOf(switchCount));
        tvStatLateValue.setText(UiFormatting.formatDuration(todayUsage.nightUsageMillis));
        tvStatAppsValue.setText(String.valueOf(todayUsage.appsTrackedCount));

        List<DailyUsage> recentHistory = new ArrayList<>(usageHistory);
        if (recentHistory.size() > 7) recentHistory = recentHistory.subList(0, 7);
        long totalMillis = 0L;
        for (DailyUsage usage : recentHistory) totalMillis += usage.screenTimeMillis;
        long averageMillis = recentHistory.isEmpty() ? 0L : totalMillis / recentHistory.size();
        tvHeroWeekly.setText(String.format(Locale.getDefault(), "7-day avg: %s/day", UiFormatting.formatDuration(averageMillis)));
    }

    private void updateHeroRing() {
        long avgMillis = 0L;
        if (latestUsageHistory != null && !latestUsageHistory.isEmpty()) {
            int count = Math.min(7, latestUsageHistory.size());
            long total = 0L;
            for (int i = 0; i < count; i++) total += latestUsageHistory.get(i).screenTimeMillis;
            avgMillis = total / count;
        }
        float maxHours = avgMillis > 0 ? avgMillis / (60f * 60f * 1000f) : 6f;
        float currentHours = latestScreenTime / (60f * 60f * 1000f);
        ringUsage.setProgress(currentHours, maxHours);

        if (avgMillis > 0) {
            double deviation = ((double) latestScreenTime / avgMillis) - 1d;
            int pct = (int) Math.abs(deviation * 100d);
            if (deviation > 0.05d) {
                tvHeroDelta.setText("▲ " + pct + "%");
                tvHeroDelta.setTextColor(ContextCompat.getColor(requireContext(), R.color.usage_delta_up));
                tvHeroWeekly.setText(String.format(Locale.getDefault(), "7-day avg: %s/day. You are trending towards digital exhaustion.", UiFormatting.formatDuration(avgMillis)));
            } else if (deviation < -0.05d) {
                tvHeroDelta.setText("▼ " + pct + "%");
                tvHeroDelta.setTextColor(ContextCompat.getColor(requireContext(), R.color.usage_delta_down));
                tvHeroWeekly.setText(String.format(Locale.getDefault(), "7-day avg: %s/day. You are successfully regaining focus.", UiFormatting.formatDuration(avgMillis)));
            } else {
                tvHeroDelta.setText("~ avg");
                tvHeroDelta.setTextColor(ContextCompat.getColor(requireContext(), R.color.usage_delta_text));
                tvHeroWeekly.setText(String.format(Locale.getDefault(), "7-day avg: %s/day. Your habits are fully stabilized.", UiFormatting.formatDuration(avgMillis)));
            }
        } else {
            tvHeroDelta.setText("—");
            tvHeroDelta.setTextColor(ContextCompat.getColor(requireContext(), R.color.usage_delta_text));
        }
    }

    private String generateHourlyInsight(int hour, float minutes) {
        if (minutes <= 0) return "No activity";
        
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();
        long hourStart = dayStart + hour * 3600000L;
        long hourEnd = hourStart + 3600000L;
        
        long passiveMs = 0;
        long productiveMs = 0;
        String topApp = "";
        long topAppMs = 0;
        Map<String, Long> appTimes = new HashMap<>();
        
        if (latestTodaySessions != null) {
            for (UsageSession s : latestTodaySessions) {
                long overlapStart = Math.max(s.sessionStart, hourStart);
                long overlapEnd = Math.min(s.sessionEnd, hourEnd);
                if (overlapEnd > overlapStart) {
                    long duration = overlapEnd - overlapStart;
                    appTimes.put(s.packageName, appTimes.getOrDefault(s.packageName, 0L) + duration);
                    if ("passive".equals(s.sessionType)) passiveMs += duration;
                    else productiveMs += duration;
                }
            }
        }
        
        for (Map.Entry<String, Long> e : appTimes.entrySet()) {
            if (e.getValue() > topAppMs) {
                topAppMs = e.getValue();
                topApp = e.getKey();
            }
        }
        
        String appName = com.mindtrace.ai.ai.AppCategoryMapper.getAppName(topApp);
        if (passiveMs > productiveMs * 1.5) {
            return "Mindless dopamine loop via " + appName;
        } else if (productiveMs > passiveMs * 1.5) {
            return "Deep focus block on " + appName;
        } else {
            return "Fragmented attention span";
        }
    }

    private void updateHeatmap() {
        float[] productiveMins = new float[24];
        float[] passiveMins = new float[24];
        
        if (latestTodaySessions != null) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long dayStart = cal.getTimeInMillis();
            
            for (UsageSession s : latestTodaySessions) {
                for (int h = 0; h < 24; h++) {
                    long hStart = dayStart + h * 3600000L;
                    long hEnd = hStart + 3600000L;
                    long overlapStart = Math.max(s.sessionStart, hStart);
                    long overlapEnd = Math.min(s.sessionEnd, hEnd);
                    if (overlapEnd > overlapStart) {
                        float mins = (overlapEnd - overlapStart) / 60000f;
                        if ("passive".equals(s.sessionType)) {
                            passiveMins[h] += mins;
                        } else {
                            productiveMins[h] += mins;
                        }
                    }
                }
            }
        }
        heatmapView.setDivergingData(productiveMins, passiveMins);
    }

    private void renderUsageTimeline() {
        switch (currentRangeMode) {
            case WEEK:
                renderWeekBars();
                break;
            case MONTH:
                renderMonthBars();
                break;
            case TODAY:
            default:
                renderTodayBars();
                break;
        }
    }

    private void renderTodayBars() {
        List<TrendData> list = new ArrayList<>();
        float[] prodMins = new float[24];
        float[] passMins = new float[24];
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();

        for (UsageSession s : latestTodaySessions) {
            for (int h = 0; h < 24; h++) {
                long hStart = dayStart + h * 3600000L;
                long hEnd = hStart + 3600000L;
                long overlapStart = Math.max(s.sessionStart, hStart);
                long overlapEnd = Math.min(s.sessionEnd, hEnd);
                if (overlapEnd > overlapStart) {
                    float m = (overlapEnd - overlapStart) / 60000f;
                    if ("passive".equals(s.sessionType)) passMins[h] += m;
                    else prodMins[h] += m;
                }
            }
        }

        for (int hour = 0; hour < 24; hour += 3) {
            float pProd = 0; float pPass = 0;
            for(int k=0; k<3; k++) {
                if(hour+k < 24) { pProd += prodMins[hour+k]; pPass += passMins[hour+k]; }
            }
            String label = hour % 12 == 0 ? "12" : String.valueOf(hour % 12);
            label += (hour >= 12 ? "p" : "a");
            list.add(new TrendData(label, pProd, pPass, hour <= currentHour && currentHour < hour+3));
        }

        String summary = latestTodaySessions.isEmpty()
                ? "Today view shows your usage by hour once app sessions are recorded."
                : "Today's usage grouped by 3-hour blocks. " + UiFormatting.formatDuration(latestScreenTime) + " total so far.";
        applyTrendChart(list, summary);
    }

    private void renderWeekBars() {
        List<DailyUsage> recent = getRecentHistory(7);
        List<TrendData> list = new ArrayList<>();
        long totalMillis = 0L;

        for (int i = 0; i < recent.size(); i++) {
            DailyUsage usage = recent.get(i);
            float totalMins = millisToHours(usage.screenTimeMillis) * 60f;
            // Use real passive ratio from DailyUsage, default to 0.5 if not computed yet
            float passiveRatio = usage.passiveConsumptionRatio > 0f ? usage.passiveConsumptionRatio : 0.5f;
            float passMins = totalMins * passiveRatio;
            float prodMins = totalMins - passMins;
            
            list.add(new TrendData(UiFormatting.formatDayLabel(usage.date), prodMins, passMins, i == recent.size() - 1));
            totalMillis += usage.screenTimeMillis;
        }

        long averageMillis = recent.isEmpty() ? 0L : totalMillis / recent.size();
        String summary = recent.isEmpty()
                ? "Weekly bars will appear after a few saved usage snapshots."
                : "Last 7 days of screen time. Average " + UiFormatting.formatDuration(averageMillis) + " per day.";
        applyTrendChart(list, summary);
    }

    private void renderMonthBars() {
        List<DailyUsage> recent = getRecentHistory(30);
        List<TrendData> list = new ArrayList<>();
        long totalMillis = 0L;

        // Group into 6 chunks
        int chunkSize = 5;
        for (int i = 0; i < 6; i++) {
            float tProd = 0; float tPass = 0;
            String label = "";
            boolean isCurrent = false;
            for (int k = 0; k < chunkSize; k++) {
                int idx = i * chunkSize + k;
                if (idx < recent.size()) {
                    DailyUsage u = recent.get(idx);
                    float tMins = millisToHours(u.screenTimeMillis) * 60f;
                    float passiveRatio = u.passiveConsumptionRatio > 0f ? u.passiveConsumptionRatio : 0.5f;
                    tPass += tMins * passiveRatio;
                    tProd += tMins * (1f - passiveRatio);
                    if (k == 0) label = labelForMonthIndex(recent, idx);
                    if (idx == recent.size() - 1) isCurrent = true;
                    totalMillis += u.screenTimeMillis;
                }
            }
            if (tProd > 0 || tPass > 0 || !label.isEmpty()) {
                list.add(new TrendData(label, tProd, tPass, isCurrent));
            }
        }

        String summary = recent.isEmpty()
                ? "Monthly bars will appear once a few daily snapshots are saved."
                : "Last 30 days. " + UiFormatting.formatDuration(totalMillis) + " recorded across saved daily history.";
        applyTrendChart(list, summary);
    }

    private void applyTrendChart(List<TrendData> list, String summary) {
        tvChartSummary.setText(summary);
        usageTimelineChart.setData(list);
    }

    private float[] buildHourlyUsageHours(List<UsageSession> sessions) {
        float[] hours = new float[24];
        if (sessions == null || sessions.isEmpty()) {
            return hours;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long dayStart = calendar.getTimeInMillis();

        for (UsageSession session : sessions) {
            if (session == null || session.sessionEnd <= session.sessionStart) {
                continue;
            }

            long sessionCursor = session.sessionStart;
            while (sessionCursor < session.sessionEnd) {
                int hourIndex = (int) ((sessionCursor - dayStart) / (60L * 60L * 1000L));
                if (hourIndex < 0 || hourIndex >= 24) {
                    break;
                }
                long bucketEnd = dayStart + ((hourIndex + 1L) * 60L * 60L * 1000L);
                long overlapEnd = Math.min(session.sessionEnd, bucketEnd);
                hours[hourIndex] += (overlapEnd - sessionCursor) / (60f * 60f * 1000f);
                sessionCursor = overlapEnd;
            }
        }
        return hours;
    }

    private List<DailyUsage> getRecentHistory(int maxCount) {
        if (latestUsageHistory == null || latestUsageHistory.isEmpty()) {
            return Collections.emptyList();
        }

        List<DailyUsage> recent = new ArrayList<>(latestUsageHistory);
        if (recent.size() > maxCount) {
            recent = recent.subList(0, maxCount);
        }
        Collections.reverse(recent);
        return recent;
    }

    private String labelForHour(int hour) {
        if (hour == 0) {
            return "12a";
        }
        if (hour < 12) {
            return hour % 4 == 0 ? hour + "a" : "";
        }
        if (hour == 12) {
            return "12p";
        }
        return hour % 4 == 0 ? (hour - 12) + "p" : "";
    }

    private String labelForMonthIndex(List<DailyUsage> recent, int index) {
        if (recent == null || recent.isEmpty() || index < 0 || index >= recent.size()) {
            return "";
        }
        if (index == recent.size() - 1 || index == 0 || index % 5 == 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(recent.get(index).date);
            return String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
        }
        return "";
    }

    private float millisToHours(long millis) {
        return millis / (60f * 60f * 1000f);
    }

    private void renderBehaviorCard() {
        String badgeLabel = resolveBehaviorBadge();
        tvBehaviorBadge.applyBehaviorLabel(badgeLabel);

        if (latestBehaviorReport == null && latestBehaviorSummary == null) {
            tvBehaviorSummary.setText("Behavior signals will appear after today's sessions are processed.");
            tvBehaviorExplanation.setText("We look for switching, binge sessions, late-night usage, and app dominance.");
            tvBehaviorScores_render();
            tvBehaviorTrend.setText("Trend comparison will appear once daily snapshots are saved.");
            return;
        }

        if (latestBehaviorReport != null && !latestBehaviorReport.dataAvailable) {
            tvBehaviorSummary.setText("Usage access needed");
            tvBehaviorExplanation.setText(latestBehaviorReport.explanation);
            tvBehaviorScores_render();
            tvBehaviorTrend.setText("Once permission is granted, daily behavior trends will be saved automatically.");
            return;
        }

        String summaryText = latestBehaviorSummary != null && hasText(latestBehaviorSummary.summaryLabel)
                ? latestBehaviorSummary.summaryLabel
                : latestInsights != null && hasText(latestInsights.behaviorSummaryLabel)
                ? latestInsights.behaviorSummaryLabel
                : "Healthy balance";
        tvBehaviorSummary.setText(summaryText);

        String explanation = latestBehaviorSummary != null && hasText(latestBehaviorSummary.explanatoryNotes)
                ? latestBehaviorSummary.explanatoryNotes
                : latestBehaviorReport != null && hasText(latestBehaviorReport.explanation)
                ? latestBehaviorReport.explanation
                : "No major behavior risks stand out right now.";
        tvBehaviorExplanation.setText(UiFormatting.highlightKeywords(
                requireContext(),
                explanation,
                "Late-night usage",
                "Rapid switching",
                "Binge usage",
                "high fragmentation",
                "dominant app"
        ));

        tvBehaviorScores_render();
        String trendText = latestInsights != null && hasText(latestInsights.behaviorTrendSummary)
                ? latestInsights.behaviorTrendSummary
                : "Trend comparison will appear once daily snapshots are saved.";
        tvBehaviorTrend.setText(UiFormatting.highlightKeywords(requireContext(), trendText, "rising", "improving", "steady", "fragmented"));
    }

    private void renderBehavioralFingerprint() {
        if (latestBehaviorReport == null) return;
        
        // Render Active vs Passive Ratio
        double ratio = latestBehaviorReport.activeVsPassiveRatio;
        int activePct = (int) Math.round(100.0 / (1.0 + ratio));
        if (activePct < 0) activePct = 0;
        if (activePct > 100) activePct = 100;
        progressActivePassive.setProgressCompat(activePct, true);
        
        if (activePct < 40) {
            tvConsumptionInsight.setText("Your screen time is highly passive. This heavily correlates with digital fatigue.");
        } else if (activePct > 60) {
            tvConsumptionInsight.setText("You maintain a strong active engagement ratio. Excellent focus.");
        } else {
            tvConsumptionInsight.setText("Your usage is balanced between productivity and passive consumption.");
        }
        
        // Render Dominant Quadrant
        String quadrant = latestBehaviorReport.dominantUsageQuadrant;
        if (quadrant == null || quadrant.isEmpty() || "UNKNOWN".equals(quadrant)) {
            tvChronotypeTitle.setText("ANALYZING");
            tvChronotypeInsight.setText("Gathering more data to find your peak distraction time.");
        } else {
            String title = quadrant.replace("_", " ");
            tvChronotypeTitle.setText(title);
            if (quadrant.equals("LATE_NIGHT")) {
                tvChronotypeInsight.setText("Most of your screen time occurs after dark, which can severely impact REM sleep.");
            } else if (quadrant.equals("EVENING_WIND_DOWN")) {
                tvChronotypeInsight.setText("Your usage peaks when you should be disconnecting. Try activating Focus Mode at 7 PM.");
            } else if (quadrant.equals("MORNING_ROUTINE")) {
                tvChronotypeInsight.setText("You consume significant digital content immediately after waking. This spikes morning cortisol.");
            } else {
                tvChronotypeInsight.setText("Your usage peaks during work hours. Ensure these are active, intentional sessions.");
            }
        }
        
        // Render Frequent App Loops
        List<String> loops = latestBehaviorReport.frequentAppLoops;
        if (loops == null || loops.isEmpty()) {
            tvLoopApp1.setText("App 1");
            tvLoopApp2.setText("App 2");
            tvLoopInsight.setText("No recurring dopamine loops detected yet. Keep it up!");
            tvLoopInsight.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_success));
        } else {
            String loopStr = loops.get(0); // Expected format: "com.whatsapp -> com.instagram (4x)"
            try {
                String[] parts = loopStr.split(" -> ");
                String app1 = parts[0];
                String[] part2 = parts[1].split(" \\(");
                String app2 = part2[0];
                String count = part2[1].replace(")", "");
                
                tvLoopApp1.setText(AppCategoryMapper.getAppName(app1));
                tvLoopApp2.setText(AppCategoryMapper.getAppName(app2));
                tvLoopInsight.setText("You looped this pattern " + count + " today. This indicates subconscious switching.");
                tvLoopInsight.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning_red));
            } catch (Exception e) {
                tvLoopApp1.setText("Multiple");
                tvLoopApp2.setText("Apps");
                tvLoopInsight.setText("Multiple fast-switching patterns detected.");
            }
        }
    }

    private void tvBehaviorScores_render() {
        if (latestBehaviorSummary != null) {
            // Blueprint specifies 0-10 scale, but engine stores 0-100. Convert.
            int frag = Math.round(latestBehaviorSummary.fragmentedUsageScore / 10f);
            int swch = Math.round(latestBehaviorSummary.switchScore / 10f);
            int bnge = Math.round(latestBehaviorSummary.bingeScore / 10f);
            int nght = Math.round(latestBehaviorSummary.lateNightPenaltyScore / 10f);
            int dom  = Math.round(latestBehaviorSummary.topAppDominanceScore / 10f);
            setScorePill(tvScoreFrag, "Frag", frag);
            setScorePill(tvScoreSwitch, "Swch", swch);
            setScorePill(tvScoreBinge, "Bnge", bnge);
            setScorePill(tvScoreNight, "Nght", nght);
            setScorePill(tvScoreDom, "Dom", dom);
        } else if (latestBehaviorReport != null) {
            // Fallback: derive approximate 0-10 scores from raw BehaviorReport counts
            int swScore = Math.min(10, Math.round(latestBehaviorReport.appSwitchCount / 8f));
            int rpScore = Math.min(10, Math.round(latestBehaviorReport.rapidSwitchCount / 3f));
            int bgScore = Math.min(10, latestBehaviorReport.bingeSessionCount * 3);
            int ntScore = Math.min(10, Math.round(latestBehaviorReport.lateNightUsageMillis / 360000f));
            float domRatio = (float) latestBehaviorReport.dominantUsageRatio;
            int dmScore = Math.min(10, Math.round(domRatio * 10f));
            setScorePill(tvScoreFrag, "Frag", swScore);
            setScorePill(tvScoreSwitch, "Swch", rpScore);
            setScorePill(tvScoreBinge, "Bnge", bgScore);
            setScorePill(tvScoreNight, "Nght", ntScore);
            setScorePill(tvScoreDom, "Dom", dmScore);
        } else {
            tvScoreFrag.setText("Frag\n\u2014");
            tvScoreSwitch.setText("Swch\n\u2014");
            tvScoreBinge.setText("Bnge\n\u2014");
            tvScoreNight.setText("Nght\n\u2014");
            tvScoreDom.setText("Dom\n\u2014");
        }
    }

    /** Apply a 0-10 score to a pill TextView with color coding per blueprint. */
    private void setScorePill(TextView pill, String label, int score) {
        score = Math.max(0, Math.min(10, score));
        pill.setText(label + "\n" + score);
        int color;
        if (score <= 3) {
            color = ContextCompat.getColor(requireContext(), R.color.color_success);       // Green — healthy
        } else if (score <= 6) {
            color = ContextCompat.getColor(requireContext(), R.color.color_warning);        // Amber — watch
        } else {
            color = ContextCompat.getColor(requireContext(), R.color.warning_red);          // Red — alert
        }
        pill.setTextColor(color);
    }

    private String resolveBehaviorBadge() {
        if (latestBehaviorSummary != null && hasText(latestBehaviorSummary.summaryLabel)) {
            return latestBehaviorSummary.summaryLabel;
        }
        if (latestInsights != null && hasText(latestInsights.behaviorSummaryLabel)) {
            return latestInsights.behaviorSummaryLabel;
        }
        if (latestBehaviorReport != null && hasText(latestBehaviorReport.summaryLabel)) {
            return latestBehaviorReport.summaryLabel;
        }
        return "Learning";
    }

    private void renderMostUsedApp(AppUsageModel app) {
        if (app == null) {
            ivTopIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            tvTopName.setText("No usage data yet");
            tvTopMeta.setText("Your most used app will appear here after today's device activity is recorded.");
            return;
        }

        Drawable icon = app.appIcon != null
                ? app.appIcon
                : ContextCompat.getDrawable(requireContext(), android.R.drawable.sym_def_app_icon);
        ivTopIcon.setImageDrawable(icon);
        tvTopName.setText(privacyModeEnabled ? maskAppName(app.appName) : app.appName);

        StringBuilder metaBuilder = new StringBuilder();
        metaBuilder.append(UiFormatting.formatDuration(app.usageTime))
                .append(" | ")
                .append(app.percentOfTotalUsage > 0 ? app.percentOfTotalUsage : app.usagePercentage)
                .append("% of today");
        if (app.foregroundSessions > 0) {
            metaBuilder.append("\n")
                    .append(app.foregroundSessions)
                    .append(app.foregroundSessions == 1 ? " session" : " sessions");
        }
        if (app.launchCount > 0) {
            metaBuilder.append(" | ")
                    .append(app.launchCount)
                    .append(app.launchCount == 1 ? " launch" : " launches");
        }
        if (hasText(app.appCategory)) {
            metaBuilder.append("\n").append(app.appCategory);
        }
        if (app.firstOpenedTimestamp > 0L || app.lastUsedTimestamp > 0L) {
            metaBuilder.append("\n");
            if (app.firstOpenedTimestamp > 0L) {
                metaBuilder.append("First ")
                        .append(UiFormatting.formatTimeLabel(app.firstOpenedTimestamp));
            }
            if (app.lastUsedTimestamp > 0L) {
                if (app.firstOpenedTimestamp > 0L) {
                    metaBuilder.append(" | ");
                }
                metaBuilder.append("Last ")
                        .append(UiFormatting.formatTimeLabel(app.lastUsedTimestamp));
            }
        }
        tvTopMeta.setText(metaBuilder.toString());
    }

    private void renderAppList(List<AppUsageModel> apps) {
        if (apps == null || apps.isEmpty()) {
            EmptyStateHelper.showNoUsage(requireView());
            rvApps.setVisibility(View.GONE);
            tvAppCount.setText("0 tracked");
        } else {
            EmptyStateHelper.hide(requireView());
            rvApps.setVisibility(View.VISIBLE);
            tvAppCount.setText(apps.size() + " tracked");
            appUsageAdapter.setData(apps);
        }
    }

    private void exportUsageReport() {
        if (dashboardViewModel.getUsageHistory().getValue() == null || dashboardViewModel.getUsageHistory().getValue().isEmpty()) return;
        DailyUsage usage = dashboardViewModel.getUsageHistory().getValue().get(0);
        
        StringBuilder report = new StringBuilder();
        report.append("📊 MindTrace AI Daily Usage Report\n");
        report.append("Date: ").append(UiFormatting.formatTimeLabel(usage.date)).append("\n\n");
        report.append("Screen Time: ").append(UiFormatting.formatDuration(usage.screenTimeMillis)).append("\n");
        report.append("Unlocks: ").append(usage.unlockCount).append("\n");
        report.append("App Switches: ").append(usage.totalAppSwitchCount).append("\n");
        report.append("Top App: ").append(usage.mostUsedApp).append("\n");
        report.append("Generated by MindTrace AI.");
        
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, report.toString());
        sendIntent.setType("text/plain");
        
        Intent shareIntent = Intent.createChooser(sendIntent, "Share Usage Report");
        startActivity(shareIntent);
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFICACY PIPELINE RENDERING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Renders the Efficacy Pipeline card from the aggregated
     * {@link EfficacyMetrics} posted by the ViewModel.
     */
    private void renderEfficacyPipeline(@Nullable EfficacyMetrics metrics) {
        if (cardEfficacyPipeline == null) return;
        if (metrics == null) metrics = EfficacyMetrics.empty();

        // ── Status chip ──
        if (chipEfficacyStatus != null) {
            if (metrics.observingCount > 0) {
                chipEfficacyStatus.applyBehaviorLabel("Observing");
            } else if (metrics.hasData()) {
                chipEfficacyStatus.applyBehaviorLabel("Active");
            } else {
                chipEfficacyStatus.applyBehaviorLabel("Measuring");
            }
        }

        // ── Overall score ──
        if (metrics.hasData()) {
            int scorePercent = Math.round(metrics.overallAvg * 100);
            String sign = scorePercent >= 0 ? "+" : "";
            tvEfficacyOverallScore.setText(sign + scorePercent + "%");

            int color;
            switch (metrics.getOverallColorHint()) {
                case "positive":
                    color = ContextCompat.getColor(requireContext(), R.color.color_success);
                    break;
                case "negative":
                    color = ContextCompat.getColor(requireContext(), R.color.warning_red);
                    break;
                default:
                    color = ContextCompat.getColor(requireContext(), R.color.text_main);
                    break;
            }
            tvEfficacyOverallScore.setTextColor(color);
            tvEfficacyOverallLabel.setText(metrics.getOverallLabel());
        } else {
            tvEfficacyOverallScore.setText("\u2014");
            tvEfficacyOverallScore.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_secondary));
            tvEfficacyOverallLabel.setText("Awaiting data");
        }

        tvEfficacyMeasuredCount.setText(
                metrics.measuredCount + (metrics.measuredCount == 1 ? " task measured" : " tasks measured"));

        // ── Per-category bars ──
        llEfficacyCategories.removeAllViews();
        if (!metrics.categoryScores.isEmpty()) {
            for (Map.Entry<String, Float> entry : metrics.categoryScores.entrySet()) {
                addEfficacyCategoryBar(entry.getKey(), entry.getValue());
            }
        }

        // ── Summary text ──
        if (metrics.summaryLines.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < metrics.summaryLines.length; i++) {
                if (i > 0) sb.append("\n");
                sb.append(metrics.summaryLines[i]);
            }
            tvEfficacySummary.setText(sb.toString());
        } else {
            tvEfficacySummary.setText(
                    "Complete tasks and rate your mood to see efficacy insights.");
        }

        // ── Observation window indicator ──
        if (metrics.observingCount > 0) {
            llEfficacyObserving.setVisibility(View.VISIBLE);
            tvEfficacyObserving.setText(
                    metrics.observingCount
                            + (metrics.observingCount == 1
                            ? " task in observation window"
                            : " tasks in observation window"));
        } else {
            llEfficacyObserving.setVisibility(View.GONE);
        }
    }

    /**
     * Inflates a single horizontal category bar into the efficacy container.
     * Layout: [Category label] [coloured progress bar] [score %]
     */
    private void addEfficacyCategoryBar(String category, float score) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        // Category label
        TextView label = new TextView(requireContext());
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        label.setText(formatCategoryLabel(category));
        label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        label.setTextSize(12f);
        label.setMaxLines(1);
        row.addView(label);

        // Progress bar
        com.google.android.material.progressindicator.LinearProgressIndicator bar =
                new com.google.android.material.progressindicator.LinearProgressIndicator(requireContext());
        LinearLayout.LayoutParams barParams =
                new LinearLayout.LayoutParams(0, dp(10), 2f);
        barParams.setMarginStart(dp(8));
        barParams.setMarginEnd(dp(8));
        bar.setLayoutParams(barParams);
        bar.setMax(100);
        // Clamp score to -1..1 range, map to 0..100 for progress bar
        int progressValue = Math.max(0, Math.min(100, Math.round((score + 1f) * 50f)));
        bar.setProgressCompat(progressValue, false);
        bar.setTrackCornerRadius(dp(5));
        bar.setTrackThickness(dp(10));

        int barColor;
        if (score >= 0.03f) {
            barColor = ContextCompat.getColor(requireContext(), R.color.color_success);
        } else if (score >= -0.03f) {
            barColor = ContextCompat.getColor(requireContext(), R.color.color_warning);
        } else {
            barColor = ContextCompat.getColor(requireContext(), R.color.warning_red);
        }
        bar.setIndicatorColor(barColor);
        bar.setTrackColor(0x1AFFFFFF); // 10% white
        row.addView(bar);

        // Score label
        TextView scoreLabel = new TextView(requireContext());
        scoreLabel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int pct = Math.round(score * 100);
        String sign = pct >= 0 ? "+" : "";
        scoreLabel.setText(sign + pct + "%");
        scoreLabel.setTextColor(barColor);
        scoreLabel.setTextSize(11f);
        row.addView(scoreLabel);

        llEfficacyCategories.addView(row);
    }

    /** Convert dp value to pixels. */
    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /** Cleans up linkedRiskCategory keys into human-readable labels. */
    private String formatCategoryLabel(String raw) {
        if (raw == null || raw.isEmpty()) return "Other";
        // Replace underscores/dashes, title-case the first letter of each word
        String[] words = raw.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String maskAppName(String name) {
        if (!hasText(name) || name.length() <= 2) {
            return "Hidden app";
        }
        return name.substring(0, 1) + "...";
    }

    private void renderCategoryPieChart(List<AppUsageModel> apps) {
        if (apps == null || apps.isEmpty()) {
            categoryPieChart.clear();
            return;
        }

        Map<String, Long> categoryTimes = new HashMap<>();
        long totalMillis = 0;

        for (AppUsageModel app : apps) {
            if (app.usageTime > 0) {
                String category = hasText(app.appCategory) ? app.appCategory : "Other";
                categoryTimes.put(category, categoryTimes.getOrDefault(category, 0L) + app.usageTime);
                totalMillis += app.usageTime;
            }
        }

        if (totalMillis == 0) {
            categoryPieChart.clear();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (Map.Entry<String, Long> entry : categoryTimes.entrySet()) {
            float percentage = (float) entry.getValue() / totalMillis;
            if (percentage > 0.02f) { // Only show > 2%
                entries.add(new PieEntry(entry.getValue(), entry.getKey()));
                colors.add(AppCategoryMapper.getCategoryColorInt(AppCategoryMapper.fromString(entry.getKey())));
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "Categories");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);
        dataSet.setColors(colors);
        dataSet.setValueLinePart1OffsetPercentage(80.f);
        dataSet.setValueLinePart1Length(0.2f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(categoryPieChart));
        data.setValueTextSize(11f);
        data.setValueTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));

        categoryPieChart.setData(data);
        categoryPieChart.setCenterText("Categories");
        categoryPieChart.setCenterTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        categoryPieChart.animateY(1000);
        categoryPieChart.invalidate();
    }
}
