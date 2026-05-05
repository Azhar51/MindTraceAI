package com.mindtrace.ai.ui.panel;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.UserProgress;
import com.mindtrace.ai.repository.AssessmentRepository;
import com.mindtrace.ai.ui.BreathingExerciseActivity;
import com.mindtrace.ai.ui.DailyCheckInActivity;
import com.mindtrace.ai.ui.DailyResetActivity;
import com.mindtrace.ai.ui.JournalActivity;
import com.mindtrace.ai.ui.LevelUpOverlay;
import com.mindtrace.ai.ui.MainActivity;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.ui.WeeklyAssessmentActivity;
import com.mindtrace.ai.ui.components.AnimatedCounterView;
import com.mindtrace.ai.ui.components.BaselineDeviationBar;
import com.mindtrace.ai.ui.components.ConfettiView;
import com.mindtrace.ai.ui.components.DeviationView;
import com.mindtrace.ai.ui.components.EmptyStateHelper;
import com.mindtrace.ai.ui.components.GlowButton;
import com.mindtrace.ai.ui.components.GradientProgressBar;
import com.mindtrace.ai.ui.components.PulseView;
import com.mindtrace.ai.ui.components.RiskGaugeView;
import com.mindtrace.ai.ui.components.SparklineView;
import com.mindtrace.ai.ui.components.StateChipView;
import com.mindtrace.ai.ui.components.WellnessRingView;
import com.mindtrace.ai.ui.model.HomeScreenState;
import com.mindtrace.ai.ui.theme.ColorSystem;
import com.mindtrace.ai.viewmodel.DashboardViewModel;
import com.mindtrace.ai.viewmodel.TaskViewModel;
import com.mindtrace.ai.ui.panel.overview.OverviewFabDelegate;
import com.mindtrace.ai.ui.panel.overview.OverviewFocusDelegate;
import com.mindtrace.ai.ui.panel.overview.OverviewInsightDelegate;
import com.mindtrace.ai.ui.panel.overview.OverviewMissionDelegate;
import com.mindtrace.ai.ui.panel.overview.OverviewWarningDelegate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;

public class OverviewFragment extends Fragment {
    private DashboardViewModel dashboardViewModel;
    private TaskViewModel taskViewModel;

    private View rootView;
    private View overviewContent;
    private View overviewLoading;
    private View progressLayer;
    private View missionCardSurface;
    private View heroSurface;
    private View layoutHeroExpandedContent;
    private View layoutHeroCopy;
    private View orbPrimary;
    private View orbSecondary;
    private View streakGlowView;
    private View aiAnomalyDot;
    private View fabScrim;
    private TextView tvAppSubtitle;
    private TextView tvGreeting;
    private TextView tvRiskSummary;
    private TextView tvBaselineComparison;
    private TextView tvRiskTrendLabel;
    private TextView tvMissionTitle;
    private TextView tvMissionCompleteState;
    private TextView tvNextActionTitle;
    private TextView tvNextActionReason;
    private TextView tvNextActionEta;
    private TextView tvSupportStrip;
    private AnimatedCounterView tvStatScreenTime;
    private TextView tvStatStreak;
    private TextView tvStatTasks;
    private TextView tvStatTasksCaption;
    private TextView tvStatMoodEmoji;
    private TextView tvStatMood;
    private TextView tvCompactRiskValue;
    private TextView tvWeeklySubtitle;
    private TextView tvXpLabel;
    private TextView tvXpSubtitle;
    private TextView badgeCheckIn;
    private TextView badgeExercise;
    private TextView badgeJournal;
    private TextView tvErrorTitle;
    private TextView tvErrorMessage;
    private TextView tvInsightHeader;
    private TextView tvInsightTitle;
    private TextView tvInsightBody;
    private TextView tvInsightLearnMore;
    private TextView tvInsightBadge;
    private TextView tvRadarTitle;
    private TextView tvRadarBadge;
    private TextView tvRadarSummary;
    private TextView tvRadarFooter;
    private TextView tvFocusBadge;
    private TextView tvFocusTitle;
    private TextView tvFocusWindow;
    private TextView tvFocusBody;
    private TextView tvForecastIcon;
    private TextView tvForecastTitle;
    private TextView tvForecastConfidence;
    private TextView tvForecastSummary;
    private TextView tvForecastTip;
    private TextView tvForecastDriver;
    private TextView tvFabBadge;
    private StateChipView chipDate;
    private StateChipView chipWellness;
    private StateChipView chipRiskLevel;
    private StateChipView chipMissionProgress;
    private StateChipView chipWarning;
    private StateChipView chipNextAction;
    private StateChipView chipCompactWellness;
    private StateChipView chipCompactDate;
    private StateChipView chipWeeklyStatus;
    private StateChipView chipForecastShift;
    private MaterialCardView cardHero;
    private MaterialCardView cardPatternRadar;
    private MaterialCardView cardFocusWindow;
    private MaterialCardView cardMission;
    private MaterialCardView cardWarning;
    private MaterialCardView cardNextAction;
    private MaterialCardView cardSupportStrip;
    private MaterialCardView cardAiInsight;
    private MaterialCardView cardForecast;
    private MaterialCardView cardErrorState;
    private MaterialCardView cardStatScreenTime;
    private MaterialCardView cardStatStreak;
    private MaterialCardView cardStatTasks;
    private MaterialCardView cardStatMood;
    private MaterialCardView cardWeeklyPrompt;
    private GlowButton btnPrimaryAction;
    private MaterialButton btnSupport;
    private MaterialButton btnWeeklyPrompt;
    private MaterialButton btnRetry;
    private MaterialButton btnFocusAction;
    private MaterialButton btnForecastAction;
    private SwipeRefreshLayout swipeRefreshLayout;
    private AppBarLayout appBarLayout;
    private NestedScrollView scrollView;
    private WellnessRingView gaugeRisk;
    private WellnessRingView gaugeRiskCompact;
    private WellnessRingView gaugeStatTasks;
    private PulseView pulseRisk;
    private SparklineView sparklineRisk;
    private SparklineView sparklineForecast;
    private ConfettiView confettiView;
    private GradientProgressBar missionProgress;
    private GradientProgressBar progressXp;
    private DeviationView viewStatScreenTimeDelta;
    private AnimatedCounterView counterStatStreak;

    // ── Phase 6: Premium custom components ──────────────────────────
    private RiskGaugeView gaugeRiskSemi;
    private BaselineDeviationBar barBaselineDeviation;
    private FloatingActionButton fabPrimary;
    private FloatingActionButton fabCheckIn;
    private FloatingActionButton fabJournal;
    private FloatingActionButton fabBreathe;
    private FloatingActionButton fabReset;
    private FloatingActionButton fabFocus;
    private View fabActionsContainer;
    private LinearLayout layoutInsightDots;

    // ── Phase 5: Delegate instances ──────────────────────────────────────
    private OverviewWarningDelegate warningDelegate;
    private OverviewFabDelegate fabDelegate;
    private OverviewInsightDelegate insightDelegate;
    private OverviewMissionDelegate missionDelegate;
    private OverviewFocusDelegate focusDelegate;

    // ── Phase C: Advanced Feature Fields ─────────────────────────────────
    private View bannerCrisis;
    private View layoutBehaviorBadge;
    private TextView tvCrisisTitle;
    private TextView tvCrisisMessage;
    private TextView tvSparklineTrend;
    private MaterialButton btnCrisisAction;
    private MaterialCardView cardScreenTimeSparkline;
    private MaterialCardView cardMoodTrend;
    private SparklineView sparklineScreenTime;
    private LinearLayout layoutMoodTrendEmojis;
    private StateChipView chipBehaviorScore;
    private StateChipView chipAssessmentDue;
    private View viewBehaviorStatusDot;
    private View cardBehaviorBadge;
    private android.animation.ObjectAnimator behaviorDotPulse;

    // ── Wellness Insights (Efficacy Closed-Loop) ─────────────────────────
    private MaterialCardView cardWellnessInsights;
    private StateChipView chipWellnessInsights;
    private TextView tvEfficacySummary;
    private TextView tvEfficacyBestCategory;
    private TextView tvEfficacyObserving;
    private View layoutEfficacyCategory;

    // ── Pipeline Progress Strip ──────────────────────────────────────────
    private View layoutPipelineStrip;
    private View viewPipelineDot;
    private TextView tvPipelineStatus;
    private TextView tvPipelineErrorBadge;
    private ObjectAnimator pipelineDotAnimator;

    private final View[] missionRows = new View[3];
    private final TextView[] missionIndicators = new TextView[3];
    private final TextView[] missionStepViews = new TextView[3];
    private final TextView[] missionReasonViews = new TextView[3];
    private final ImageView[] missionIconViews = new ImageView[3];
    private final ImageButton[] missionInfoButtons = new ImageButton[3];
    private final View[] warningRows = new View[5];
    private final TextView[] warningViews = new TextView[5];
    private final TextView[] warningDetailViews = new TextView[5];
    private final View[] warningSeverityBars = new View[5];
    private final MaterialButton[] warningAckButtons = new MaterialButton[5];
    private final TextView[] radarSignalViews = new TextView[3];
    private final TextView[] focusRitualViews = new TextView[3];

    private final Map<Integer, InterventionTask> missionTasksById = new HashMap<>();

    private HomeScreenState currentState;
    private UserProgress currentUserProgress;
    private ExecutorService weeklyCheckExecutor;
    private AnimatorSet orbAnimator;
    private ObjectAnimator streakGlowAnimator;
    private ObjectAnimator loadingPulseAnimator;
    private ValueAnimator greetingAnimator;
    private int currentStreakCount;
    private int lastKnownLevel = -1;
    private String lastGreetingAnimatedText = "";

    public OverviewFragment() {
        super(R.layout.fragment_overview_premium);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;
        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);
        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);

        bindViews(view);
        initDelegates();
        applyTimeOfDayHeroTheme();
        setupSwipeRefresh(view);
        setupInteractions();
        setupAppBarBehavior();
        startOrbAnimation();
        warningDelegate.loadDismissed(requireContext());

        gaugeRisk.setOnThresholdCrossedListener(score -> performHaptic(50));

        dashboardViewModel.getHomeScreenState().observe(getViewLifecycleOwner(), this::renderState);
        dashboardViewModel.getAllTasks().observe(getViewLifecycleOwner(), this::cacheMissionTasks);

        dashboardViewModel.getScreenTime().observe(getViewLifecycleOwner(), screenTimeMs -> {
            renderScreenTimeStat(screenTimeMs == null ? 0L : screenTimeMs);
            renderScreenTimeDeviation();
        });

        taskViewModel.getUserProgress().observe(getViewLifecycleOwner(), progress -> {
            currentUserProgress = progress;
            renderStreakStat(progress);
            renderProgressLayer();
        });
        dashboardViewModel.getStateHistory().observe(getViewLifecycleOwner(), responses -> {
            renderMoodStat(responses);
            renderMoodTrend(responses);
        });

        // C.1: Observe usage history for sparkline
        dashboardViewModel.getUsageHistory().observe(getViewLifecycleOwner(), this::renderScreenTimeSparkline);

        // C.4: Observe behavior report for badge
        dashboardViewModel.getCurrentBehavior().observe(getViewLifecycleOwner(), this::renderBehaviorBadge);

        checkWeeklyAssessmentDue();
        applyAccessibilityLabels();
    }

    private void bindViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_overview);
        appBarLayout = view.findViewById(R.id.appbar_overview);
        scrollView = view.findViewById(R.id.scroll_overview_content);
        overviewLoading = view.findViewById(R.id.layout_overview_loading);
        overviewContent = view.findViewById(R.id.layout_overview_content);
        progressLayer = view.findViewById(R.id.layout_overview_progress_layer);
        heroSurface = view.findViewById(R.id.layout_overview_hero_surface);
        layoutHeroExpandedContent = view.findViewById(R.id.layout_overview_hero_expanded_content);
        layoutHeroCopy = view.findViewById(R.id.layout_overview_hero_copy);
        orbPrimary = view.findViewById(R.id.view_overview_orb_primary);
        orbSecondary = view.findViewById(R.id.view_overview_orb_secondary);
        missionCardSurface = view.findViewById(R.id.layout_overview_mission_surface);
        streakGlowView = view.findViewById(R.id.view_stat_streak_glow);
        confettiView = view.findViewById(R.id.confetti_overview);
        aiAnomalyDot = view.findViewById(R.id.view_overview_ai_anomaly);
        fabScrim = view.findViewById(R.id.view_overview_fab_scrim);
        fabActionsContainer = view.findViewById(R.id.layout_overview_fab_actions);
        layoutInsightDots = view.findViewById(R.id.layout_overview_insight_dots);

        cardHero = view.findViewById(R.id.card_overview_hero);
        cardPatternRadar = view.findViewById(R.id.card_overview_pattern_radar);
        cardFocusWindow = view.findViewById(R.id.card_overview_focus_window);
        cardMission = view.findViewById(R.id.card_overview_mission);
        cardAiInsight = view.findViewById(R.id.card_overview_ai_insight);
        cardForecast = view.findViewById(R.id.card_overview_forecast);
        cardWarning = view.findViewById(R.id.card_overview_warning);
        cardNextAction = view.findViewById(R.id.card_overview_next_action);
        cardSupportStrip = view.findViewById(R.id.card_overview_support_strip);
        cardErrorState = view.findViewById(R.id.card_overview_error_state);
        cardStatScreenTime = view.findViewById(R.id.card_stat_screen_time);
        cardStatStreak = view.findViewById(R.id.card_stat_streak);
        cardStatTasks = view.findViewById(R.id.card_stat_tasks);
        cardStatMood = view.findViewById(R.id.card_stat_mood);
        cardWeeklyPrompt = view.findViewById(R.id.card_weekly_prompt);

        chipDate = view.findViewById(R.id.chip_overview_date);
        chipWellness = view.findViewById(R.id.chip_overview_wellness);
        chipRiskLevel = view.findViewById(R.id.chip_overview_risk_level);
        chipMissionProgress = view.findViewById(R.id.chip_overview_mission_progress);
        chipWarning = view.findViewById(R.id.chip_overview_warning);
        chipNextAction = view.findViewById(R.id.chip_overview_next_action);
        chipCompactWellness = view.findViewById(R.id.chip_overview_compact_wellness);
        chipCompactDate = view.findViewById(R.id.chip_overview_compact_date);
        chipWeeklyStatus = view.findViewById(R.id.chip_weekly_prompt_status);
        chipForecastShift = view.findViewById(R.id.chip_overview_forecast_shift);

        tvAppSubtitle = view.findViewById(R.id.tv_overview_app_subtitle);
        tvGreeting = view.findViewById(R.id.tv_overview_greeting);
        tvRiskSummary = view.findViewById(R.id.tv_overview_risk_summary);
        tvBaselineComparison = view.findViewById(R.id.tv_overview_baseline_comparison);
        tvRiskTrendLabel = view.findViewById(R.id.tv_overview_risk_trend_label);
        tvMissionTitle = view.findViewById(R.id.tv_overview_mission_title);
        tvMissionCompleteState = view.findViewById(R.id.tv_overview_mission_complete_state);
        tvNextActionTitle = view.findViewById(R.id.tv_overview_next_action_title);
        tvNextActionReason = view.findViewById(R.id.tv_overview_next_action_reason);
        tvNextActionEta = view.findViewById(R.id.tv_overview_next_action_eta);
        tvSupportStrip = view.findViewById(R.id.tv_overview_support_strip);
        tvStatScreenTime = view.findViewById(R.id.tv_stat_screen_time);
        tvStatStreak = view.findViewById(R.id.tv_stat_streak);
        tvStatTasks = view.findViewById(R.id.tv_stat_tasks);
        tvStatTasksCaption = view.findViewById(R.id.tv_stat_tasks_caption);
        tvStatMoodEmoji = view.findViewById(R.id.tv_stat_mood_emoji);
        tvStatMood = view.findViewById(R.id.tv_stat_mood);
        tvCompactRiskValue = view.findViewById(R.id.tv_overview_compact_risk_value);
        tvWeeklySubtitle = view.findViewById(R.id.tv_weekly_prompt_subtitle);
        tvXpLabel = view.findViewById(R.id.tv_overview_xp_label);
        tvXpSubtitle = view.findViewById(R.id.tv_overview_xp_subtitle);
        badgeCheckIn = view.findViewById(R.id.badge_overview_checkin);
        badgeExercise = view.findViewById(R.id.badge_overview_exercise);
        badgeJournal = view.findViewById(R.id.badge_overview_journal);
        tvErrorTitle = view.findViewById(R.id.tv_overview_error_title);
        tvErrorMessage = view.findViewById(R.id.tv_overview_error_message);
        tvInsightHeader = view.findViewById(R.id.tv_overview_insight_header);
        tvInsightTitle = view.findViewById(R.id.tv_overview_insight_title);
        tvInsightBody = view.findViewById(R.id.tv_overview_insight_body);
        tvInsightLearnMore = view.findViewById(R.id.tv_overview_insight_learn_more);
        tvInsightBadge = view.findViewById(R.id.tv_overview_insight_badge);
        tvRadarTitle = view.findViewById(R.id.tv_overview_radar_title);
        tvRadarBadge = view.findViewById(R.id.tv_overview_radar_badge);
        tvRadarSummary = view.findViewById(R.id.tv_overview_radar_summary);
        tvRadarFooter = view.findViewById(R.id.tv_overview_radar_footer);
        tvFocusBadge = view.findViewById(R.id.tv_overview_focus_badge);
        tvFocusTitle = view.findViewById(R.id.tv_overview_focus_title);
        tvFocusWindow = view.findViewById(R.id.tv_overview_focus_window);
        tvFocusBody = view.findViewById(R.id.tv_overview_focus_body);
        tvForecastIcon = view.findViewById(R.id.tv_overview_forecast_icon);
        tvForecastTitle = view.findViewById(R.id.tv_overview_forecast_title);
        tvForecastConfidence = view.findViewById(R.id.tv_overview_forecast_confidence);
        tvForecastSummary = view.findViewById(R.id.tv_overview_forecast_summary);
        tvForecastTip = view.findViewById(R.id.tv_overview_forecast_tip);
        tvForecastDriver = view.findViewById(R.id.tv_overview_forecast_driver);
        tvFabBadge = view.findViewById(R.id.tv_overview_fab_badge);

        // Phase C: New view bindings
        bannerCrisis = view.findViewById(R.id.banner_crisis);
        layoutBehaviorBadge = view.findViewById(R.id.layout_behavior_badge);
        tvCrisisTitle = view.findViewById(R.id.tv_crisis_title);
        tvCrisisMessage = view.findViewById(R.id.tv_crisis_message);
        tvSparklineTrend = view.findViewById(R.id.tv_sparkline_trend);
        btnCrisisAction = view.findViewById(R.id.btn_crisis_action);
        cardScreenTimeSparkline = view.findViewById(R.id.card_screen_time_sparkline);
        cardMoodTrend = view.findViewById(R.id.card_mood_trend);
        sparklineScreenTime = view.findViewById(R.id.sparkline_screen_time);
        layoutMoodTrendEmojis = view.findViewById(R.id.layout_mood_trend_emojis);
        chipBehaviorScore = view.findViewById(R.id.chip_behavior_score);
        chipAssessmentDue = view.findViewById(R.id.chip_assessment_due);
        viewBehaviorStatusDot = view.findViewById(R.id.view_behavior_status_dot);
        cardBehaviorBadge = view.findViewById(R.id.card_behavior_badge);

        // Wellness Insights bindings
        cardWellnessInsights = view.findViewById(R.id.card_overview_wellness_insights);
        chipWellnessInsights = view.findViewById(R.id.chip_overview_wellness_insights);
        tvEfficacySummary = view.findViewById(R.id.tv_overview_efficacy_summary);
        tvEfficacyBestCategory = view.findViewById(R.id.tv_overview_efficacy_best_category);
        tvEfficacyObserving = view.findViewById(R.id.tv_overview_efficacy_observing);
        layoutEfficacyCategory = view.findViewById(R.id.layout_overview_efficacy_category);

        // Pipeline progress strip bindings
        layoutPipelineStrip = view.findViewById(R.id.layout_overview_pipeline_strip);
        viewPipelineDot = view.findViewById(R.id.view_pipeline_dot);
        tvPipelineStatus = view.findViewById(R.id.tv_pipeline_status);
        tvPipelineErrorBadge = view.findViewById(R.id.tv_pipeline_error_badge);

        gaugeRisk = view.findViewById(R.id.gauge_overview_risk);
        gaugeRiskCompact = view.findViewById(R.id.gauge_overview_risk_compact);
        gaugeStatTasks = view.findViewById(R.id.gauge_stat_tasks);
        pulseRisk = view.findViewById(R.id.pulse_overview_risk);
        sparklineRisk = view.findViewById(R.id.sparkline_overview_risk);
        sparklineForecast = view.findViewById(R.id.sparkline_overview_forecast);
        missionProgress = view.findViewById(R.id.progress_overview_mission);
        progressXp = view.findViewById(R.id.progress_overview_xp);
        viewStatScreenTimeDelta = view.findViewById(R.id.view_stat_screen_time_delta);
        counterStatStreak = view.findViewById(R.id.counter_stat_streak);

        // Phase 6: Premium custom components
        gaugeRiskSemi = view.findViewById(R.id.gauge_overview_risk_semi);
        barBaselineDeviation = view.findViewById(R.id.bar_overview_baseline_deviation);
        fabPrimary = view.findViewById(R.id.fab_overview_primary);
        fabCheckIn = view.findViewById(R.id.fab_overview_checkin);
        fabJournal = view.findViewById(R.id.fab_overview_journal);
        fabBreathe = view.findViewById(R.id.fab_overview_breathe);
        fabReset = view.findViewById(R.id.fab_overview_reset);
        fabFocus = view.findViewById(R.id.fab_overview_focus);

        btnPrimaryAction = view.findViewById(R.id.btn_overview_primary_action);
        btnSupport = view.findViewById(R.id.btn_overview_support);
        btnWeeklyPrompt = view.findViewById(R.id.btn_weekly_prompt);
        btnRetry = view.findViewById(R.id.btn_overview_retry);
        MaterialButton btnFocusAction = view.findViewById(R.id.btn_overview_focus_action);
        btnForecastAction = view.findViewById(R.id.btn_overview_forecast_action);
        this.btnFocusAction = btnFocusAction;

        missionRows[0] = view.findViewById(R.id.row_overview_mission_step_1);
        missionRows[1] = view.findViewById(R.id.row_overview_mission_step_2);
        missionRows[2] = view.findViewById(R.id.row_overview_mission_step_3);
        missionIndicators[0] = view.findViewById(R.id.indicator_overview_mission_step_1);
        missionIndicators[1] = view.findViewById(R.id.indicator_overview_mission_step_2);
        missionIndicators[2] = view.findViewById(R.id.indicator_overview_mission_step_3);
        missionStepViews[0] = view.findViewById(R.id.tv_overview_mission_step_1);
        missionStepViews[1] = view.findViewById(R.id.tv_overview_mission_step_2);
        missionStepViews[2] = view.findViewById(R.id.tv_overview_mission_step_3);
        missionReasonViews[0] = view.findViewById(R.id.tv_overview_mission_reason_1);
        missionReasonViews[1] = view.findViewById(R.id.tv_overview_mission_reason_2);
        missionReasonViews[2] = view.findViewById(R.id.tv_overview_mission_reason_3);
        missionIconViews[0] = view.findViewById(R.id.icon_overview_mission_step_1);
        missionIconViews[1] = view.findViewById(R.id.icon_overview_mission_step_2);
        missionIconViews[2] = view.findViewById(R.id.icon_overview_mission_step_3);
        missionInfoButtons[0] = view.findViewById(R.id.btn_overview_mission_info_1);
        missionInfoButtons[1] = view.findViewById(R.id.btn_overview_mission_info_2);
        missionInfoButtons[2] = view.findViewById(R.id.btn_overview_mission_info_3);

        warningRows[0] = view.findViewById(R.id.row_overview_warning_1);
        warningRows[1] = view.findViewById(R.id.row_overview_warning_2);
        warningRows[2] = view.findViewById(R.id.row_overview_warning_3);
        warningRows[3] = view.findViewById(R.id.row_overview_warning_4);
        warningRows[4] = view.findViewById(R.id.row_overview_warning_5);
        warningViews[0] = view.findViewById(R.id.tv_overview_warning_1);
        warningViews[1] = view.findViewById(R.id.tv_overview_warning_2);
        warningViews[2] = view.findViewById(R.id.tv_overview_warning_3);
        warningViews[3] = view.findViewById(R.id.tv_overview_warning_4);
        warningViews[4] = view.findViewById(R.id.tv_overview_warning_5);
        warningDetailViews[0] = view.findViewById(R.id.tv_overview_warning_detail_1);
        warningDetailViews[1] = view.findViewById(R.id.tv_overview_warning_detail_2);
        warningDetailViews[2] = view.findViewById(R.id.tv_overview_warning_detail_3);
        warningDetailViews[3] = view.findViewById(R.id.tv_overview_warning_detail_4);
        warningDetailViews[4] = view.findViewById(R.id.tv_overview_warning_detail_5);
        warningSeverityBars[0] = view.findViewById(R.id.bar_overview_warning_1);
        warningSeverityBars[1] = view.findViewById(R.id.bar_overview_warning_2);
        warningSeverityBars[2] = view.findViewById(R.id.bar_overview_warning_3);
        warningSeverityBars[3] = view.findViewById(R.id.bar_overview_warning_4);
        warningSeverityBars[4] = view.findViewById(R.id.bar_overview_warning_5);
        warningAckButtons[0] = view.findViewById(R.id.btn_overview_warning_ack_1);
        warningAckButtons[1] = view.findViewById(R.id.btn_overview_warning_ack_2);
        warningAckButtons[2] = view.findViewById(R.id.btn_overview_warning_ack_3);
        warningAckButtons[3] = view.findViewById(R.id.btn_overview_warning_ack_4);
        warningAckButtons[4] = view.findViewById(R.id.btn_overview_warning_ack_5);

        radarSignalViews[0] = view.findViewById(R.id.tv_overview_radar_signal_1);
        radarSignalViews[1] = view.findViewById(R.id.tv_overview_radar_signal_2);
        radarSignalViews[2] = view.findViewById(R.id.tv_overview_radar_signal_3);
        focusRitualViews[0] = view.findViewById(R.id.tv_overview_focus_ritual_1);
        focusRitualViews[1] = null;
        focusRitualViews[2] = null;
    }

    /**
     * Phase 5: Constructs the delegate instances that own the Warning, FAB,
     * and Insight card subsystems. Each delegate receives only the view
     * references it needs, enforcing clear ownership boundaries.
     */
    private void initDelegates() {
        // ── Warning Delegate ─────────────────────────────────────────────
        warningDelegate = new OverviewWarningDelegate(
                cardWarning, chipWarning,
                warningRows, warningViews,
                warningDetailViews, warningSeverityBars,
                warningAckButtons, rootView,
                requireContext()
        );

        // ── FAB Delegate ─────────────────────────────────────────────────
        fabDelegate = new OverviewFabDelegate(
                fabPrimary, fabActionsContainer, fabScrim, tvFabBadge,
                requireContext()
        );

        // ── Insight Delegate ─────────────────────────────────────────────
        insightDelegate = new OverviewInsightDelegate(
                cardAiInsight, tvInsightHeader, tvInsightTitle,
                tvInsightBody, tvInsightLearnMore, tvInsightBadge,
                aiAnomalyDot, layoutInsightDots,
                new OverviewInsightDelegate.Callback() {
                    @Override public void performInsightAction(@Nullable HomeScreenState.InsightItem item) {
                        OverviewFragment.this.performInsightAction(item);
                    }
                    @Override public boolean isFragmentAdded() { return isAdded(); }
                },
                requireContext()
        );

        // ── Mission Delegate ─────────────────────────────────────────────
        missionDelegate = new OverviewMissionDelegate(
                cardMission, missionCardSurface,
                tvMissionTitle, tvMissionCompleteState,
                missionProgress, chipMissionProgress,
                missionRows, missionIndicators,
                missionStepViews, missionReasonViews,
                missionIconViews, missionInfoButtons,
                new OverviewMissionDelegate.Callback() {
                    @Override public void burstConfettiFrom(View target, int count) { OverviewFragment.this.burstConfettiFrom(target, count); }
                    @Override public void burstConfettiGlobal(int count) {
                        if (confettiView != null) confettiView.post(() -> confettiView.burst(count));
                    }
                    @Override public void markTaskCompleted(InterventionTask task) {
                        taskViewModel.markTaskCompleted(task);
                        if (currentState != null) {
                            missionDelegate.renderProgress(currentState);
                            if (missionDelegate.allStepsComplete(currentState)) {
                                missionDelegate.celebrateComplete(currentState);
                            }
                        }
                    }
                    @Override public boolean isFragmentAdded() { return isAdded(); }
                    @Override public android.content.Context getContext() { return requireContext(); }
                },
                requireContext()
        );

        // ── Focus Delegate ───────────────────────────────────────────────
        focusDelegate = new OverviewFocusDelegate(
                cardFocusWindow,
                tvFocusBadge, tvFocusTitle,
                tvFocusWindow, tvFocusBody,
                btnFocusAction, focusRitualViews,
                requireContext()
        );
    }

    private void setupSwipeRefresh(View view) {
        swipeRefreshLayout.setColorSchemeColors(ColorSystem.PRIMARY, ColorSystem.AMBER, ColorSystem.GREEN);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ColorSystem.CARD);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            dashboardViewModel.refreshDashboard();
            dashboardViewModel.loadScreenTime();
            dashboardViewModel.loadTopApps(5, false);
            view.postDelayed(() -> {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, 1800);
        });
    }

    private void setupInteractions() {
        UiMotion.animateCardEntry(progressLayer, 0);
        UiMotion.animateCardEntry(cardHero, 1);
        UiMotion.animateHorizontalEntry(cardStatScreenTime, 0);
        UiMotion.animateHorizontalEntry(cardStatStreak, 1);
        UiMotion.animateHorizontalEntry(cardStatTasks, 2);
        UiMotion.animateHorizontalEntry(cardStatMood, 3);
        UiMotion.animateCardEntry(cardPatternRadar, 4);
        UiMotion.animateCardEntry(cardFocusWindow, 5);
        UiMotion.animateCardEntry(cardMission, 6);
        UiMotion.animateCardEntry(cardAiInsight, 7);
        UiMotion.animateCardEntry(cardForecast, 8);
        UiMotion.animateCardEntry(btnPrimaryAction, 9);
        UiMotion.animateCardEntry(cardWarning, 10);
        UiMotion.animateCardEntry(cardNextAction, 11);

        UiMotion.attachPressAnimation(btnPrimaryAction);
        UiMotion.attachPressAnimation(btnFocusAction);
        UiMotion.attachPressAnimation(btnSupport);
        UiMotion.attachPressAnimation(btnWeeklyPrompt);
        UiMotion.attachPressAnimation(cardPatternRadar);
        UiMotion.attachPressAnimation(cardFocusWindow);
        UiMotion.attachPressAnimation(cardAiInsight);
        UiMotion.attachPressAnimation(cardForecast);
        UiMotion.attachPressAnimation(fabPrimary);
        UiMotion.attachPressAnimation(fabCheckIn);
        UiMotion.attachPressAnimation(fabJournal);
        UiMotion.attachPressAnimation(fabBreathe);
        UiMotion.attachPressAnimation(fabReset);
        UiMotion.attachPressAnimation(fabFocus);

        // B.3: Glassmorphism borders on Quick Stats cards
        applyGlassBorder(cardStatScreenTime);
        applyGlassBorder(cardStatStreak);
        applyGlassBorder(cardStatTasks);
        applyGlassBorder(cardStatMood);

        UiMotion.attachPressAnimation(cardStatScreenTime);
        UiMotion.attachPressAnimation(cardStatStreak);
        UiMotion.attachPressAnimation(cardStatTasks);
        UiMotion.attachPressAnimation(cardStatMood);

        cardStatScreenTime.setOnClickListener(v -> navigateToTab(MainActivity.DEST_USAGE));
        cardStatStreak.setOnClickListener(v -> navigateToTab(MainActivity.DEST_TASKS));
        cardStatTasks.setOnClickListener(v -> navigateToTab(MainActivity.DEST_TASKS));
        cardStatMood.setOnClickListener(v -> navigateToTab(MainActivity.DEST_MOOD));
        // C.13: Pattern radar bottom sheet instead of direct navigation
        cardPatternRadar.setOnClickListener(v -> {
            if (currentState != null) {
                showPatternRadarBottomSheet(currentState.patternRadarCard);
            }
        });
        cardFocusWindow.setOnClickListener(v -> openAiCoach());
        cardAiInsight.setOnClickListener(v -> insightDelegate.showBottomSheet(requireContext()));
        tvInsightLearnMore.setOnClickListener(v -> insightDelegate.showBottomSheet(requireContext()));
        tvInsightBadge.setOnClickListener(v -> insightDelegate.advanceInsight());

        // C.14: Forecast bottom sheet instead of direct navigation
        cardForecast.setOnClickListener(v -> {
            if (currentState != null) {
                showForecastBottomSheet(currentState.forecastCard);
            }
        });
        // C.1/C.2: Press animations on new cards
        UiMotion.attachPressAnimation(cardScreenTimeSparkline);
        UiMotion.attachPressAnimation(cardMoodTrend);

        // C.8: Setup long-press tooltips
        setupStatTooltips();

        // C.7: Assessment due indicator
        renderAssessmentDueChip();

        btnPrimaryAction.setOnClickListener(v -> {
            performHaptic(10);
            handlePrimaryAction();
        });
        btnFocusAction.setOnClickListener(v -> openAiCoach());
        btnSupport.setOnClickListener(v -> openSupportPanel());
        btnWeeklyPrompt.setOnClickListener(v -> {
            performHaptic(10);
            startActivity(new Intent(requireContext(), WeeklyAssessmentActivity.class));
        });
        btnRetry.setOnClickListener(v -> {
            performHaptic(10);
            dashboardViewModel.refreshDashboard();
            dashboardViewModel.loadScreenTime();
            dashboardViewModel.loadTopApps(5, false);
        });
        btnForecastAction.setOnClickListener(v -> handleForecastAction());

        fabPrimary.setOnClickListener(v -> fabDelegate.toggle());
        fabScrim.setOnClickListener(v -> fabDelegate.setExpanded(false, true));
        fabCheckIn.setOnClickListener(v -> {
            performHaptic(12);
            fabDelegate.setExpanded(false, true);
            startActivity(new Intent(requireContext(), DailyCheckInActivity.class));
        });
        fabJournal.setOnClickListener(v -> {
            performHaptic(12);
            fabDelegate.setExpanded(false, true);
            startActivity(new Intent(requireContext(), JournalActivity.class));
        });
        fabBreathe.setOnClickListener(v -> {
            performHaptic(12);
            fabDelegate.setExpanded(false, true);
            startActivity(new Intent(requireContext(), BreathingExerciseActivity.class));
        });
        fabFocus.setOnClickListener(v -> {
            performHaptic(12);
            fabDelegate.setExpanded(false, true);
            startActivity(new Intent(requireContext(), com.mindtrace.ai.ui.FocusSessionActivity.class));
        });
        fabReset.setOnClickListener(v -> {
            performHaptic(12);
            fabDelegate.setExpanded(false, true);
            handlePrimaryAction();
        });

        // Add missing listeners for momentum layer badges
        UiMotion.attachPressAnimation(badgeCheckIn);
        UiMotion.attachPressAnimation(badgeJournal);
        UiMotion.attachPressAnimation(badgeExercise);
        
        badgeCheckIn.setOnClickListener(v -> {
            performHaptic(10);
            startActivity(new Intent(requireContext(), DailyCheckInActivity.class));
        });
        badgeJournal.setOnClickListener(v -> {
            performHaptic(10);
            startActivity(new Intent(requireContext(), JournalActivity.class));
        });
        badgeExercise.setOnClickListener(v -> {
            performHaptic(10);
            startActivity(new Intent(requireContext(), BreathingExerciseActivity.class));
        });

        if (scrollView != null) {
            scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                fabDelegate.onScroll(scrollY, oldScrollY);
                applyScrollAwareAnimations(scrollY);
            });
        }
        fabDelegate.scheduleInitialHide();
    }

    private void setupAppBarBehavior() {
        appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setEnabled(verticalOffset == 0);
            }
            float totalRange = appBarLayout1.getTotalScrollRange() == 0
                    ? 1f
                    : appBarLayout1.getTotalScrollRange();
            float progress = Math.min(1f, Math.abs(verticalOffset) / totalRange);
            // B.8: Smoother eased parallax curves
            float eased = (float) (1.0 - Math.pow(1.0 - progress, 2.2));
            float collapsedAlpha = clampFloat((eased - 0.18f) / 0.52f, 0f, 1f);
            float expandedAlpha = 1f - clampFloat(eased / 0.55f, 0f, 1f);

            View compactToolbar = requireView().findViewById(R.id.toolbar_overview_collapsed);
            compactToolbar.setAlpha(collapsedAlpha);
            compactToolbar.setTranslationY(-dp(8) * (1f - collapsedAlpha));

            if (layoutHeroExpandedContent != null) {
                layoutHeroExpandedContent.setAlpha(expandedAlpha);
                layoutHeroExpandedContent.setTranslationY(-dp(14) * eased);
                layoutHeroExpandedContent.setScaleX(1f - (0.03f * eased));
                layoutHeroExpandedContent.setScaleY(1f - (0.03f * eased));
            }
            if (layoutHeroCopy != null) {
                layoutHeroCopy.setAlpha(expandedAlpha);
            }
            if (orbPrimary != null) {
                orbPrimary.setTranslationY(dp(30) * eased);
                orbPrimary.setTranslationX(-dp(14) * eased);
                orbPrimary.setScaleX(1f - (0.12f * eased));
                orbPrimary.setScaleY(1f - (0.12f * eased));
                orbPrimary.setAlpha(1f - (0.4f * eased));
            }
            if (orbSecondary != null) {
                orbSecondary.setTranslationY(-dp(22) * eased);
                orbSecondary.setTranslationX(dp(14) * eased);
                orbSecondary.setScaleX(1f + (0.08f * eased));
                orbSecondary.setScaleY(1f + (0.08f * eased));
                orbSecondary.setAlpha(1f - (0.3f * eased));
            }
        });
    }

    private void renderState(HomeScreenState state) {
        if (state == null) {
            return;
        }
        currentState = state;
        renderSurfaceState(state);
        if (state.showEmptyState || (state.isLoading && !state.hasData)) {
            return;
        }

        chipDate.applyNeutralLabel(state.dateText);
        chipCompactDate.applyNeutralLabel(state.dateText);
        applyWellnessChip(chipWellness, state);
        applyWellnessChip(chipCompactWellness, state);
        renderGreetingText(state.greetingText, state.isLoading);

        renderRiskSection(state);
        renderPatternRadarSection(state);
        focusDelegate.render(state);
        missionDelegate.render(state);
        renderProgressLayer();
        insightDelegate.render(state);
        renderForecastSection(state);
        warningDelegate.render(state);
        renderWellnessInsights(state);
        renderPipelineStrip(state);
        renderDelegateFabBadge(state);
        renderScreenTimeDeviation();

        // C.5: Show crisis banner if high risk
        if (state.riskIndex >= 85) {
            showCrisisBanner("High Risk Detected",
                    "Your behavior patterns show elevated risk signals. Consider taking a break.");
        } else {
            hideCrisisBanner();
        }
        // C.16: Schedule auto-dismiss for low-severity warnings
        warningDelegate.scheduleAutoDismiss(state, requireContext());

        btnPrimaryAction.setText(state.primaryActionLabel);
        btnPrimaryAction.setEnabled(!state.isLoading);
        btnPrimaryAction.setAlpha(state.isLoading ? 0.7f : 1f);

        // Fix: Hide Next Action card when no content available
        boolean hasNextAction = state.nextBestActionTitle != null
                && !state.nextBestActionTitle.trim().isEmpty()
                && !state.isLoading;
        if (hasNextAction) {
            cardNextAction.setVisibility(View.VISIBLE);
            chipNextAction.setChipColors(translucent(ColorSystem.PRIMARY, 44), ColorSystem.TEXT_PRIMARY);
            chipNextAction.setText("Do this now");
            tvNextActionTitle.setText(state.nextBestActionTitle);
            tvNextActionReason.setText(state.nextBestActionReason);
            tvNextActionEta.setText(state.nextBestActionETA);
        } else {
            cardNextAction.setVisibility(View.GONE);
        }

        UiMotion.fadeVisibility(cardSupportStrip, state.showSupportStrip);
        tvSupportStrip.setText(state.supportStripText);
        if (state.showSupportStrip) {
            UiMotion.animateCardEntry(cardSupportStrip, 12);
        }

        cardHero.setCardElevation(dp(state.isHighRisk ? 12 : 8));
        cardWarning.setCardElevation(dp(state.isHighRisk ? 12 : 8));
        cardNextAction.setCardElevation(dp(8));
    }

    private void renderRiskSection(HomeScreenState state) {
        if (state.isLoading) {
            gaugeRisk.setCenterText("--");
            gaugeRisk.setCaptionText("loading");
            gaugeRisk.setScore(0f, false);
            gaugeRiskCompact.setScore(0f, false);
            gaugeRiskCompact.setCenterText("");
            gaugeRiskCompact.setCaptionText("");
            chipRiskLevel.applyNeutralLabel("...");
            tvCompactRiskValue.setText("Loading");
            tvCompactRiskValue.setTextColor(ColorSystem.TEXT_PRIMARY);
            tvRiskSummary.setText(state.riskSummary);
            tvBaselineComparison.setText(state.baselineComparisonText);
            sparklineRisk.setData(state.riskHistory);
            tvRiskTrendLabel.setText("Trend loading");
            // Hide premium views during loading
            if (gaugeRiskSemi != null) gaugeRiskSemi.setVisibility(View.GONE);
            if (barBaselineDeviation != null) barBaselineDeviation.setVisibility(View.GONE);
            stopHighRiskPulse();
            return;
        }

        float riskFraction = state.riskIndex / 100f;
        gaugeRisk.setCaptionText("risk index");
        gaugeRisk.setScore(riskFraction, true);

        gaugeRiskCompact.setCaptionText("");
        gaugeRiskCompact.setScore(riskFraction, false);
        gaugeRiskCompact.setCenterText("");

        // Feed data to the premium RiskGaugeView (if present)
        if (gaugeRiskSemi != null) {
            gaugeRiskSemi.setRiskScore(state.riskIndex, true);
        }

        tvCompactRiskValue.setText(state.riskIndex + " risk");
        tvCompactRiskValue.setTextColor(resolveRiskColor(state.riskIndex));
        applyRiskLevelChip(chipRiskLevel, state.riskIndex);

        tvRiskSummary.setText(createHighlightedSummary(state.riskSummary));

        // BaselineDeviationBar: Show when baseline is established, hide otherwise
        if (state.isBaselineReady && barBaselineDeviation != null) {
            tvBaselineComparison.setVisibility(View.GONE);
            barBaselineDeviation.setVisibility(View.VISIBLE);
            barBaselineDeviation.setMetricLabel("baseline");
            barBaselineDeviation.setDeviation(state.baselineDeviationPercent, true);
        } else {
            tvBaselineComparison.setText(state.baselineComparisonText);
            tvBaselineComparison.setVisibility(View.VISIBLE);
            if (barBaselineDeviation != null) barBaselineDeviation.setVisibility(View.GONE);
        }

        sparklineRisk.setData(state.riskHistory);
        tvRiskTrendLabel.setText(describeRiskTrend(state.riskHistory));

        if (state.riskIndex >= 70) {
            startHighRiskPulse();
            // Make the GlowButton pulse red in high-risk state
            if (btnPrimaryAction instanceof GlowButton) {
                ((GlowButton) btnPrimaryAction).setDangerMode();
            }
        } else {
            stopHighRiskPulse();
            // Reset to primary mode
            if (btnPrimaryAction instanceof GlowButton) {
                ((GlowButton) btnPrimaryAction).setPrimaryMode();
            }
        }
    }

    private void renderPatternRadarSection(HomeScreenState state) {
        if (cardPatternRadar == null) {
            return;
        }

        HomeScreenState.PatternRadarCard radarCard = state.patternRadarCard;
        if (radarCard == null) {
            cardPatternRadar.setVisibility(View.GONE);
            return;
        }

        cardPatternRadar.setVisibility(View.VISIBLE);
        tvRadarTitle.setText(radarCard.title == null ? "Live Pattern Radar" : radarCard.title);
        tvRadarSummary.setText(
                radarCard.summary == null
                        ? "MindTrace is watching the strongest live signals shaping your day."
                        : radarCard.summary
        );
        tvRadarFooter.setText(
                radarCard.footerLabel == null || radarCard.footerLabel.trim().isEmpty()
                        ? "Live behavior stream"
                        : radarCard.footerLabel
        );
        tvRadarBadge.setText(radarCard.urgent ? "Hot" : "Live");

        // B.12: Stagger-animate radar signal pills
        ViewGroup pillContainer = null;
        for (int i = 0; i < radarSignalViews.length; i++) {
            if (radarCard.signalPills != null && i < radarCard.signalPills.size()) {
                radarSignalViews[i].setVisibility(View.VISIBLE);
                radarSignalViews[i].setText(radarCard.signalPills.get(i));
                if (pillContainer == null && radarSignalViews[i].getParent() instanceof ViewGroup) {
                    pillContainer = (ViewGroup) radarSignalViews[i].getParent();
                }
            } else {
                radarSignalViews[i].setVisibility(View.GONE);
            }
        }
        if (pillContainer != null) {
            UiMotion.animatePillStagger(pillContainer, 65);
        }

        int accentColor = radarCard.urgent ? ColorSystem.RED : ColorSystem.PRIMARY;
        tvRadarBadge.setTextColor(Color.WHITE);
        cardPatternRadar.setCardElevation(dp(radarCard.urgent ? 12 : 8));
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setShape(GradientDrawable.RECTANGLE);
        badgeBackground.setCornerRadius(dp(999));
        badgeBackground.setColor(translucent(accentColor, 92));
        badgeBackground.setStroke(Math.max(1, (int) dp(1)), translucent(accentColor, 156));
        tvRadarBadge.setBackground(badgeBackground);
    }


    private void renderProgressLayer() {
        if (currentState == null) {
            return;
        }

        UserProgress progress = currentUserProgress;
        if (progress == null) {
            tvXpLabel.setText("Level 1 | 0 XP");
            tvXpSubtitle.setText(currentState.isLoading ? "Building momentum..." : "Momentum layer");
            progressXp.setReverseGradient(false);
            progressXp.setProgressImmediate(0f);
            applyBadgeState(badgeCheckIn, "Check-In", currentState.hasCheckedInToday);
            applyBadgeState(badgeExercise, "Exercise", currentState.hasExerciseToday);
            applyBadgeState(badgeJournal, "Journal", currentState.hasJournalEntryToday);
            return;
        }

        int currentLevel = progress.getLevel();
        if (lastKnownLevel > 0
                && currentLevel > lastKnownLevel
                && isAdded()
                && requireActivity() instanceof AppCompatActivity) {
            LevelUpOverlay.show((AppCompatActivity) requireActivity(), currentLevel, progress.getLevelTitle());
        }
        lastKnownLevel = currentLevel;

        int unlockedBadgeCount = countUnlockedBadges(progress.badgesUnlockedJson);
        tvXpLabel.setText("Level " + currentLevel + " | " + progress.totalXp + " XP");
        if (currentLevel >= 5) {
            tvXpSubtitle.setText(progress.getLevelTitle() + " | " + unlockedBadgeCount + " badges unlocked");
        } else {
            tvXpSubtitle.setText(
                    progress.getLevelTitle() + " | " + progress.getXpToNextLevel() + " XP to level " + (currentLevel + 1)
            );
        }

        progressXp.setReverseGradient(false);
        progressXp.setGradientColors(new int[]{
                0xFFFBBF24,
                0xFF38BDF8,
                0xFF818CF8
        });
        // B.14: Animate XP progress with slight delay for visual delight
        progressXp.postDelayed(() -> progressXp.setProgress(progress.getLevelProgress()), 200L);

        applyBadgeState(badgeCheckIn, "Check-In", currentState.hasCheckedInToday);
        applyBadgeState(badgeExercise, "Exercise", currentState.hasExerciseToday);
        applyBadgeState(badgeJournal, "Journal", currentState.hasJournalEntryToday);
    }

    private void applyTimeOfDayHeroTheme() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (heroSurface == null || tvAppSubtitle == null) {
            return;
        }

        if (hour >= 5 && hour < 12) {
            heroSurface.setBackgroundResource(R.drawable.bg_overview_hero_morning);
        } else if (hour >= 12 && hour < 18) {
            heroSurface.setBackgroundResource(R.drawable.bg_overview_hero_afternoon);
        } else {
            heroSurface.setBackgroundResource(R.drawable.bg_overview_hero_night);
        }
        // Advanced: Smart context-aware subtitle
        tvAppSubtitle.setText(getSmartGreetingSubtext());
    }

    private void renderGreetingText(@Nullable String greetingText, boolean immediate) {
        String safeText = greetingText == null ? "" : greetingText;
        if (safeText.equals(lastGreetingAnimatedText)) {
            return;
        }

        lastGreetingAnimatedText = safeText;
        if (greetingAnimator != null) {
            greetingAnimator.cancel();
            greetingAnimator = null;
        }

        if (immediate || safeText.length() < 8) {
            tvGreeting.setText(safeText);
            return;
        }

        greetingAnimator = ValueAnimator.ofInt(0, safeText.length());
        greetingAnimator.setDuration(Math.min(1100L, 320L + (safeText.length() * 18L)));
        greetingAnimator.addUpdateListener(animation -> {
            int length = (int) animation.getAnimatedValue();
            int safeLength = Math.max(0, Math.min(safeText.length(), length));
            tvGreeting.setText(safeText.substring(0, safeLength));
        });
        greetingAnimator.start();
    }


    private void renderForecastSection(HomeScreenState state) {
        HomeScreenState.ForecastCard forecast = state.forecastCard;
        if (forecast == null) {
            cardForecast.setVisibility(View.GONE);
            return;
        }

        cardForecast.setVisibility(View.VISIBLE);
        tvForecastIcon.setText(forecast.emoji == null ? "..." : forecast.emoji);
        tvForecastTitle.setText(forecast.label);
        tvForecastConfidence.setText(forecast.confidencePercent + "% confidence");
        tvForecastSummary.setText(forecast.summary);
        tvForecastTip.setText(forecast.actionTip);
        tvForecastDriver.setText(
                forecast.driverLabel == null || forecast.driverLabel.trim().isEmpty()
                        ? "Driver: routine stability"
                        : forecast.driverLabel
        );
        sparklineForecast.setData(
                forecast.trendPoints == null || forecast.trendPoints.isEmpty()
                        ? state.riskHistory
                        : forecast.trendPoints
        );
        btnForecastAction.setVisibility(View.GONE);
        tvForecastTitle.setTextColor(forecast.highRiskTomorrow ? ColorSystem.RED : ColorSystem.TEXT_PRIMARY);
        tvForecastConfidence.setTextColor(forecast.highRiskTomorrow ? ColorSystem.RED : ColorSystem.TEXT_SECONDARY);
        cardForecast.setCardElevation(dp(forecast.highRiskTomorrow ? 12 : 8));

        int shiftColor;
        String shiftText;
        if (forecast.deltaFromToday >= 5) {
            shiftColor = ColorSystem.RED;
            shiftText = "+" + forecast.deltaFromToday + " tomorrow";
        } else if (forecast.deltaFromToday <= -5) {
            shiftColor = ColorSystem.GREEN;
            shiftText = Math.abs(forecast.deltaFromToday) + " lighter";
        } else {
            shiftColor = ColorSystem.AMBER;
            shiftText = "Steady";
        }
        chipForecastShift.setChipColors(translucent(shiftColor, 46), shiftColor);
        chipForecastShift.setText(shiftText);
        // B.10: Pop animation on forecast shift chip
        UiMotion.animatePop(chipForecastShift);
    }

    /**
     * Renders the Wellness Insights card showing closed-loop efficacy data.
     * <p>Card is hidden when no efficacy summary is available (progressive visibility).</p>
     */
    private void renderWellnessInsights(HomeScreenState state) {
        if (cardWellnessInsights == null) {
            return;
        }

        boolean hasEfficacyData = state.efficacySummaryText != null
                && !state.efficacySummaryText.trim().isEmpty();

        if (!hasEfficacyData || state.isLoading) {
            cardWellnessInsights.setVisibility(View.GONE);
            return;
        }

        cardWellnessInsights.setVisibility(View.VISIBLE);
        tvEfficacySummary.setText(state.efficacySummaryText);

        // Chip badge: "Active" when observation is in progress, "Learning" otherwise
        if (state.tasksInObservationWindow > 0) {
            chipWellnessInsights.setChipColors(
                    translucent(ColorSystem.AMBER, 44), ColorSystem.AMBER);
            chipWellnessInsights.setText("Observing");
        } else {
            chipWellnessInsights.setChipColors(
                    translucent(ColorSystem.GREEN, 44), ColorSystem.GREEN);
            chipWellnessInsights.setText("Active");
        }

        // Most effective category badge
        if (state.mostEffectiveCategory != null
                && !state.mostEffectiveCategory.trim().isEmpty()) {
            layoutEfficacyCategory.setVisibility(View.VISIBLE);
            tvEfficacyBestCategory.setText(state.mostEffectiveCategory);
        } else {
            layoutEfficacyCategory.setVisibility(View.GONE);
        }

        // Observation window count
        if (state.tasksInObservationWindow > 0) {
            tvEfficacyObserving.setVisibility(View.VISIBLE);
            tvEfficacyObserving.setText(
                    state.tasksInObservationWindow + " task"
                            + (state.tasksInObservationWindow == 1 ? "" : "s")
                            + " being observed");
        } else {
            tvEfficacyObserving.setVisibility(View.GONE);
        }

        UiMotion.animateCardEntry(cardWellnessInsights, 11);
    }

    /**
     * Renders the pipeline progress strip in the header area.
     * <p>Shows a status label + pulsing dot when background workers are running,
     * and an error badge when recent errors have been logged.</p>
     */
    private void renderPipelineStrip(HomeScreenState state) {
        if (layoutPipelineStrip == null) {
            return;
        }

        boolean showStrip = state.isAnalyzing || state.recentErrorCount > 0;

        if (!showStrip) {
            layoutPipelineStrip.setVisibility(View.GONE);
            stopPipelineDotPulse();
            return;
        }

        layoutPipelineStrip.setVisibility(View.VISIBLE);

        // Status text
        if (state.isAnalyzing && state.progressSummaryText != null
                && !state.progressSummaryText.trim().isEmpty()) {
            tvPipelineStatus.setText(state.progressSummaryText);
            tvPipelineStatus.setVisibility(View.VISIBLE);
            startPipelineDotPulse();
        } else if (state.isAnalyzing) {
            tvPipelineStatus.setText("Analyzing\u2026");
            tvPipelineStatus.setVisibility(View.VISIBLE);
            startPipelineDotPulse();
        } else {
            tvPipelineStatus.setVisibility(View.GONE);
            stopPipelineDotPulse();
        }

        // Pulsing dot visibility
        if (viewPipelineDot != null) {
            viewPipelineDot.setVisibility(state.isAnalyzing ? View.VISIBLE : View.GONE);
        }

        // Error badge
        if (state.recentErrorCount > 0) {
            tvPipelineErrorBadge.setVisibility(View.VISIBLE);
            tvPipelineErrorBadge.setText(
                    state.recentErrorCount + " error"
                            + (state.recentErrorCount == 1 ? "" : "s"));
        } else {
            tvPipelineErrorBadge.setVisibility(View.GONE);
        }
    }

    private void startPipelineDotPulse() {
        if (viewPipelineDot == null) return;
        if (pipelineDotAnimator != null && pipelineDotAnimator.isRunning()) return;
        pipelineDotAnimator = ObjectAnimator.ofFloat(viewPipelineDot, "alpha", 1f, 0.3f);
        pipelineDotAnimator.setDuration(800);
        pipelineDotAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pipelineDotAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pipelineDotAnimator.setInterpolator(new LinearInterpolator());
        pipelineDotAnimator.start();
    }

    private void stopPipelineDotPulse() {
        if (pipelineDotAnimator != null) {
            pipelineDotAnimator.cancel();
            pipelineDotAnimator = null;
        }
        if (viewPipelineDot != null) {
            viewPipelineDot.setAlpha(1f);
        }
    }



    private void renderScreenTimeStat(long screenTimeMs) {
        // B.4: Animated counter for screen time stat
        int totalMinutes = (int) Math.max(0L, screenTimeMs / 60000L);
        AnimatedCounterView.ValueFormatter formatter = (value, prefix, suffix) -> formatDurationMinutes(value);
        if (currentState != null && !currentState.isLoading) {
            tvStatScreenTime.animateTo(totalMinutes, formatter, 700);
        } else {
            tvStatScreenTime.setValueImmediate(totalMinutes, formatter);
        }
    }

    private String formatDurationMinutes(int totalMinutes) {
        int safeMinutes = Math.max(0, totalMinutes);
        int hours = safeMinutes / 60;
        int minutes = safeMinutes % 60;
        return hours + "h " + minutes + "m";
    }

    private void applyWellnessChip(StateChipView chip, HomeScreenState state) {
        chip.setText(state.wellnessLabel);
        if (state.isLoading) {
            chip.applyNeutralLabel("Preparing");
            return;
        }
        if (!state.hasData || !state.isBaselineReady) {
            chip.applyNeutralLabel(state.wellnessLabel);
            return;
        }
        if (state.isHighRisk) {
            chip.setChipColors(translucent(ColorSystem.RED, 52), ColorSystem.RED);
            return;
        }
        if (state.riskIndex >= 40) {
            chip.setChipColors(translucent(ColorSystem.AMBER, 52), ColorSystem.AMBER);
            return;
        }
        chip.setChipColors(translucent(ColorSystem.GREEN, 52), ColorSystem.GREEN);
    }

    private void renderEmptyState(HomeScreenState state) {
        if (state.showEmptyState) {
            EmptyStateHelper.show(
                    rootView,
                    "\uD83C\uDF31",
                    "Welcome to MindTrace",
                    "Complete your first check-in to unlock your dashboard.",
                    "Start Check-In",
                    v -> startActivity(new Intent(requireContext(), DailyCheckInActivity.class))
            );
            return;
        }
        EmptyStateHelper.hide(rootView);
    }

    private void renderSurfaceState(HomeScreenState state) {
        renderEmptyState(state);

        boolean showLoading = state.isLoading && !state.showEmptyState && !state.hasData;
        boolean showErrorCard = state.showErrorState;
        boolean showContent = !state.showEmptyState && !showLoading && (!showErrorCard || state.hasData);

        overviewContent.setVisibility(showContent ? View.VISIBLE : View.GONE);
        UiMotion.fadeVisibility(overviewLoading, showLoading);
        UiMotion.fadeVisibility(cardErrorState, showErrorCard);

        if (showErrorCard) {
            tvErrorTitle.setText(state.errorTitle == null || state.errorTitle.trim().isEmpty()
                    ? "Something went wrong"
                    : state.errorTitle);
            tvErrorMessage.setText(state.errorMessage == null || state.errorMessage.trim().isEmpty()
                    ? "MindTrace couldn't refresh this panel."
                    : state.errorMessage);
        }

        if (showLoading) {
            startLoadingPulse();
        } else {
            stopLoadingPulse();
        }
    }

    private void startLoadingPulse() {
        if (overviewLoading == null) {
            return;
        }
        if (loadingPulseAnimator != null) {
            loadingPulseAnimator.cancel();
        }
        // B.11: Shimmer sweep effect instead of basic pulse
        overviewLoading.setAlpha(0.55f);
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(overviewLoading, View.ALPHA, 0.45f, 0.85f);
        alphaAnim.setDuration(1200L);
        alphaAnim.setRepeatCount(ObjectAnimator.INFINITE);
        alphaAnim.setRepeatMode(ObjectAnimator.REVERSE);
        alphaAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        ObjectAnimator translateAnim = ObjectAnimator.ofFloat(overviewLoading, View.TRANSLATION_X, -dp(8), dp(8));
        translateAnim.setDuration(2400L);
        translateAnim.setRepeatCount(ObjectAnimator.INFINITE);
        translateAnim.setRepeatMode(ObjectAnimator.REVERSE);
        translateAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        AnimatorSet shimmerSet = new AnimatorSet();
        shimmerSet.playTogether(alphaAnim, translateAnim);
        shimmerSet.start();
        loadingPulseAnimator = alphaAnim;
    }

    private void stopLoadingPulse() {
        if (loadingPulseAnimator != null) {
            loadingPulseAnimator.cancel();
            loadingPulseAnimator = null;
        }
        if (overviewLoading != null) {
            overviewLoading.setAlpha(1f);
            overviewLoading.setTranslationX(0f);
        }
    }

    private void cacheMissionTasks(List<InterventionTask> tasks) {
        missionTasksById.clear();
        int completed = 0;
        int total = 0;
        long startOfToday = getStartOfTodayMillis();
        if (tasks != null) {
            for (InterventionTask task : tasks) {
                if (task == null) {
                    continue;
                }
                missionTasksById.put(task.id, task);
                if (isTaskCompleted(task)) {
                    missionDelegate.confirmCompletion(task.id);
                }
                if (!isTodayMissionTask(task, startOfToday)) {
                    continue;
                }
                total++;
                if (isTaskCompleted(task)) {
                    completed++;
                }
            }
        }

        missionDelegate.setMissionTasks(missionTasksById);

        tvStatTasks.setText(completed + "/" + total);
        tvStatTasksCaption.setText(total == 0 ? "No tasks yet" : "done today");
        float completionFraction = total == 0 ? 0f : completed / (float) total;
        gaugeStatTasks.setCaptionText("");
        gaugeStatTasks.setScore(completionFraction, false);
        gaugeStatTasks.setCenterText("");

        if (currentState != null) {
            missionDelegate.renderProgress(currentState);
        }
    }

    private void renderScreenTimeDeviation() {
        if (currentState == null || currentState.isLoading || !currentState.isBaselineReady) {
            viewStatScreenTimeDelta.showNeutral("Learning your rhythm");
            return;
        }
        double deviation = currentState.screenTimeDeviation;
        if (Double.isNaN(deviation) || Double.isInfinite(deviation)) {
            viewStatScreenTimeDelta.showNeutral("Building baseline");
            return;
        }
        viewStatScreenTimeDelta.setDeviation(deviation);
    }

    private void renderStreakStat(@Nullable UserProgress progress) {
        int streak = 0;
        if (progress != null && progress.currentStreak > 0 && progress.isStreakActive()) {
            streak = progress.currentStreak;
        }
        currentStreakCount = streak;
        counterStatStreak.animateTo(streak, "", "", 700);
        // B.19: Fire emoji for 14+ day streaks
        if (streak >= 14) {
            tvStatStreak.setText("\uD83D\uDD25 days active");
        } else {
            tvStatStreak.setText(streak == 1 ? "day active" : "days active");
        }

        if (streak >= 7) {
            startStreakGlow();
        } else {
            stopStreakGlow();
        }
        maybeShowStreakRecovery(progress);
    }

    private void startStreakGlow() {
        streakGlowView.setVisibility(View.VISIBLE);
        if (streakGlowAnimator != null) {
            streakGlowAnimator.cancel();
        }
        streakGlowView.setAlpha(0.55f);
        streakGlowAnimator = ObjectAnimator.ofFloat(streakGlowView, View.ALPHA, 0.48f, 0.92f);
        streakGlowAnimator.setDuration(1100L);
        streakGlowAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        streakGlowAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        streakGlowAnimator.start();
    }

    private void stopStreakGlow() {
        if (streakGlowAnimator != null) {
            streakGlowAnimator.cancel();
            streakGlowAnimator = null;
        }
        streakGlowView.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction(() -> streakGlowView.setVisibility(View.GONE))
                .start();
    }

    private void renderMoodStat(@Nullable List<QuestionnaireResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            tvStatMoodEmoji.setText("--");
            tvStatMood.setText("No check-in");
            return;
        }
        String mood = responses.get(0).mood;
        String emoji = moodToEmoji(mood);
        String currentEmoji = tvStatMoodEmoji.getText().toString();
        tvStatMoodEmoji.setText(emoji);
        // B.18: Pop animation when mood emoji changes
        if (!emoji.equals(currentEmoji)) {
            UiMotion.animatePop(tvStatMoodEmoji);
        }
        tvStatMood.setText(mood == null || mood.trim().isEmpty() ? "Neutral" : mood);
    }

    private void bindTextLines(
            List<String> lines,
            View[] rows,
            TextView[] textViews,
            List<String> fallback
    ) {
        List<String> safeLines = lines == null || lines.isEmpty() ? fallback : lines;
        for (int i = 0; i < rows.length; i++) {
            if (safeLines != null && i < safeLines.size()) {
                rows[i].setVisibility(View.VISIBLE);
                textViews[i].setText(safeLines.get(i));
            } else {
                rows[i].setVisibility(View.GONE);
            }
        }
    }

    private List<String> createLoadingLines(String prefix, int count) {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            lines.add(prefix + " " + i + "...");
        }
        return lines;
    }

    private boolean isTaskCompleted(InterventionTask task) {
        return task != null && (task.isCompleted || "COMPLETED".equals(task.status));
    }

    private boolean isTodayMissionTask(InterventionTask task, long startOfToday) {
        if (task == null) {
            return false;
        }
        if (task.isActionable()) {
            return true;
        }
        return isTaskCompleted(task) && task.completedAt >= startOfToday;
    }

    private void handlePrimaryAction() {
        if (currentState == null || currentState.isLoading) {
            return;
        }

        String actionType = currentState.primaryActionType;
        if (HomeScreenState.ACTION_CHECK_IN.equals(actionType)) {
            startActivity(new Intent(requireContext(), DailyCheckInActivity.class));
            return;
        }
        if (HomeScreenState.ACTION_SUPPORT.equals(actionType)) {
            openSupportPanel();
            return;
        }
        if (HomeScreenState.ACTION_PLAN.equals(actionType)) {
            navigateToTab(MainActivity.DEST_TASKS);
            return;
        }
        Intent intent = new Intent(requireContext(), DailyResetActivity.class);
        intent.putExtra(DailyResetActivity.EXTRA_MISSION_TITLE, currentState.missionTitle);
        intent.putStringArrayListExtra(
                DailyResetActivity.EXTRA_MISSION_STEPS,
                new ArrayList<>(currentState.missionSteps)
        );
        intent.putStringArrayListExtra(
                DailyResetActivity.EXTRA_WARNING_ITEMS,
                new ArrayList<>(currentState.warningItems)
        );
        intent.putExtra(DailyResetActivity.EXTRA_NEXT_ACTION_TITLE, currentState.nextBestActionTitle);
        intent.putExtra(DailyResetActivity.EXTRA_RISK_INDEX, currentState.riskIndex);
        intent.putExtra(DailyResetActivity.EXTRA_HIGH_RISK, currentState.isHighRisk);
        startActivity(intent);
    }

    private void openSupportPanel() {
        performHaptic(10);
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).openSupportPanel();
            return;
        }
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_START_DESTINATION, MainActivity.DEST_SUPPORT);
        startActivity(intent);
    }



    private void handleForecastAction() {
        if (currentState == null || currentState.forecastCard == null) {
            navigateToTab(MainActivity.DEST_INSIGHTS);
            return;
        }
        showForecastBottomSheet(currentState.forecastCard);
    }

    private void handleFocusWindowAction() {
        if (currentState == null || currentState.focusWindowCard == null) {
            navigateToTab(MainActivity.DEST_TASKS);
            return;
        }
        showFocusWindowBottomSheet(currentState.focusWindowCard);
    }

    private void openAiCoach() {
        performHaptic(10);
        startActivity(new Intent(requireContext(), com.mindtrace.ai.ui.AiCoachActivity.class));
    }



    /**
     * Phase 5 bridge: Computes the pending-action count from fragment state
     * and forwards it to the FAB delegate for badge rendering.
     */
    private void renderDelegateFabBadge(HomeScreenState state) {
        if (state == null || fabDelegate == null) return;
        int pending = 0;
        if (!state.hasCheckedInToday) pending++;
        if (!state.hasJournalEntryToday) pending++;
        if (!state.hasExerciseToday) pending++;
        if (state.missionStepItems != null) {
            for (HomeScreenState.MissionStepItem step : state.missionStepItems) {
                if (!step.isCompleted) pending++;
            }
        }
        pending += warningDelegate.getCurrentWarningCount();
        fabDelegate.setBadgeCount(pending);
    }

    private void applyScrollAwareAnimations(int scrollY) {
        float premiumShift = clampFloat(scrollY / dp(420), 0f, 1f);
        // Keep scroll motion calm: large parallax made stacked panels feel like they overlapped.
        float eased = (float) (1.0 - Math.pow(1.0 - premiumShift, 1.8));
        if (progressLayer != null) {
            progressLayer.setTranslationY(-dp(4) * eased);
            progressLayer.setAlpha(1f - (0.04f * eased));
        }
        if (cardStatScreenTime != null) {
            cardStatScreenTime.setTranslationY(0f);
        }
        if (cardStatStreak != null) {
            cardStatStreak.setTranslationY(0f);
        }
        if (cardStatTasks != null) {
            cardStatTasks.setTranslationY(0f);
        }
        if (cardStatMood != null) {
            cardStatMood.setTranslationY(0f);
        }
        if (cardPatternRadar != null) {
            cardPatternRadar.setTranslationY(0f);
        }
        if (cardFocusWindow != null) {
            cardFocusWindow.setTranslationY(0f);
        }
        if (cardAiInsight != null) {
            cardAiInsight.setTranslationY(0f);
        }
        if (cardForecast != null) {
            cardForecast.setTranslationY(0f);
        }
        if (cardWarning != null) {
            cardWarning.setTranslationY(0f);
        }
    }



    private void maybeShowStreakRecovery(@Nullable UserProgress progress) {
        if (progress == null || currentState == null || currentState.showEmptyState || !isResumed()) {
            return;
        }
        if (progress.isStreakActive() || progress.currentStreak < 3) {
            return;
        }
        SharedPreferences prefs = requireContext().getSharedPreferences("overview_premium_state", Context.MODE_PRIVATE);
        String key = "streak_recovery_prompt_" + (System.currentTimeMillis() / (24L * 60L * 60L * 1000L));
        if (prefs.getBoolean(key, false)) {
            return;
        }
        prefs.edit().putBoolean(key, true).apply();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Recover your streak")
                .setMessage(
                        "Your " + progress.currentStreak + "-day streak is paused, not lost. One clean check-in today starts a fresh recovery arc."
                )
                .setPositiveButton("Recover today", (dialog, which) ->
                        startActivity(new Intent(requireContext(), DailyCheckInActivity.class)))
                .setNegativeButton("Maybe later", null)
                .setNeutralButton("Open reset", (dialog, which) -> handlePrimaryAction())
                .show();
    }





    private void showPatternRadarBottomSheet(HomeScreenState.PatternRadarCard radarCard) {
        if (radarCard == null) return;
        performHaptic(10);
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.sheet_overview_insight, null);
        TextView headerView = sheet.findViewById(R.id.tv_sheet_insight_header);
        TextView bodyView = sheet.findViewById(R.id.tv_sheet_insight_body);
        LinearLayout reasonsLayout = sheet.findViewById(R.id.layout_sheet_insight_reasons);
        sheet.findViewById(R.id.layout_sheet_insight_more).setVisibility(View.GONE);
        
        headerView.setText(radarCard.title == null ? "Pattern Radar" : radarCard.title);
        bodyView.setText(radarCard.summary == null ? "Live monitoring active." : radarCard.summary);
        
        if (radarCard.signalPills != null && !radarCard.signalPills.isEmpty()) {
            populateInsightRows(reasonsLayout, radarCard.signalPills);
        }
        
        MaterialButton primaryButton = sheet.findViewById(R.id.btn_sheet_insight_primary);
        primaryButton.setText("View detailed usage");
        primaryButton.setOnClickListener(v -> {
            dialog.dismiss();
            navigateToTab(MainActivity.DEST_USAGE);
        });
        
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showFocusWindowBottomSheet(HomeScreenState.FocusWindowCard focusCard) {
        if (focusCard == null) return;
        performHaptic(10);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.sheet_overview_action_panel, null);
        TextView headerView = sheet.findViewById(R.id.tv_sheet_action_title);
        TextView bodyView = sheet.findViewById(R.id.tv_sheet_action_body);
        LinearLayout reasonsLayout = sheet.findViewById(R.id.layout_sheet_action_rows);
        MaterialButton primaryButton = sheet.findViewById(R.id.btn_sheet_action_primary);
        MaterialButton secondaryButton = sheet.findViewById(R.id.btn_sheet_action_secondary);

        headerView.setText("Premium Coach");
        StringBuilder coachBody = new StringBuilder();
        if (focusCard.title != null && !focusCard.title.trim().isEmpty()) {
            coachBody.append(focusCard.title.trim()).append("\n\n");
        }
        if (focusCard.coachText != null && !focusCard.coachText.trim().isEmpty()) {
            coachBody.append(focusCard.coachText.trim()).append("\n\n");
        }
        if (focusCard.windowLabel != null && !focusCard.windowLabel.trim().isEmpty()) {
            coachBody.append("Window: ").append(focusCard.windowLabel.trim());
        }
        bodyView.setText(coachBody.toString().trim());
        populateInsightRows(reasonsLayout, focusCard.ritualItems);

        boolean needsCheckIn = HomeScreenState.ACTION_CHECK_IN.equals(focusCard.actionType);
        primaryButton.setText("Start Focus Timer");
        primaryButton.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(requireContext(), com.mindtrace.ai.ui.FocusSessionActivity.class));
        });
        secondaryButton.setVisibility(View.VISIBLE);
        secondaryButton.setText(needsCheckIn ? "Update Check-In" : "Open Coach Chat");
        secondaryButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (needsCheckIn) {
                startActivity(new Intent(requireContext(), DailyCheckInActivity.class));
                return;
            }
            openAiCoach();
        });

        dialog.setContentView(sheet);
        dialog.show();
        expandActionBottomSheet(dialog);
    }

    private void showForecastBottomSheet(HomeScreenState.ForecastCard forecastCard) {
        if (forecastCard == null) return;
        performHaptic(10);
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.sheet_overview_action_panel, null);
        TextView headerView = sheet.findViewById(R.id.tv_sheet_action_title);
        TextView bodyView = sheet.findViewById(R.id.tv_sheet_action_body);
        LinearLayout reasonsLayout = sheet.findViewById(R.id.layout_sheet_action_rows);
        
        headerView.setText(forecastCard.label == null ? "Tomorrow Forecast" : forecastCard.label);
        String riskLine = "Projected risk: " + forecastCard.predictedRisk + "/100";
        if (forecastCard.deltaFromToday > 0) {
            riskLine += " (+" + forecastCard.deltaFromToday + " vs today)";
        } else if (forecastCard.deltaFromToday < 0) {
            riskLine += " (" + forecastCard.deltaFromToday + " vs today)";
        } else {
            riskLine += " (steady vs today)";
        }
        String summary = forecastCard.summary == null ? "Forecast generated." : forecastCard.summary;
        String tip = forecastCard.actionTip == null ? "" : forecastCard.actionTip.trim();
        bodyView.setText(tip.isEmpty()
                ? summary + "\n\n" + riskLine
                : summary + "\n\n" + riskLine + "\n\n" + tip);
        
        List<String> forecastRows = new ArrayList<>();
        if (forecastCard.driverLabel != null && !forecastCard.driverLabel.trim().isEmpty()) {
            forecastRows.add(forecastCard.driverLabel);
        }
        forecastRows.add(forecastCard.confidencePercent + "% confidence from recent behavior, check-ins, and usage drift.");
        forecastRows.add("Use this as a planning signal, not a panic button.");
        populateInsightRows(reasonsLayout, forecastRows);
        
        MaterialButton primaryButton = sheet.findViewById(R.id.btn_sheet_action_primary);
        MaterialButton secondaryButton = sheet.findViewById(R.id.btn_sheet_action_secondary);
        primaryButton.setText("Open Insights");
        primaryButton.setOnClickListener(v -> {
            dialog.dismiss();
            navigateToTab(MainActivity.DEST_INSIGHTS);
        });
        secondaryButton.setText("Update Check-In");
        secondaryButton.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(requireContext(), DailyCheckInActivity.class));
        });
        
        dialog.setContentView(sheet);
        dialog.show();
        expandActionBottomSheet(dialog);
    }

    private void expandActionBottomSheet(BottomSheetDialog dialog) {
        if (dialog == null) {
            return;
        }
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        bottomSheet.setLayoutParams(params);
        com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
        behavior.setSkipCollapsed(true);
        behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
    }

    private void populateInsightRows(LinearLayout container, List<String> items) {
        populateInsightRows(container, items, false, null, null);
    }

    private void populateInsightRows(
            LinearLayout container,
            List<String> items,
            boolean compact,
            @Nullable BottomSheetDialog dialog,
            @Nullable List<HomeScreenState.InsightItem> linkedInsights
    ) {
        container.removeAllViews();
        if (items == null || items.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }

        container.setVisibility(View.VISIBLE);
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            if (item == null || item.trim().isEmpty()) {
                continue;
            }

            TextView textView = new TextView(requireContext());
            textView.setText(item);
            textView.setTextColor(ColorSystem.TEXT_PRIMARY);
            textView.setTextSize(compact ? 13f : 14f);
            textView.setLineSpacing(0f, 1.12f);
            textView.setBackgroundResource(R.drawable.bg_overview_warning_row);
            int horizontal = (int) dp(14);
            int vertical = (int) dp(compact ? 10 : 12);
            textView.setPadding(horizontal, vertical, horizontal, vertical);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                params.topMargin = (int) dp(10);
            }
            textView.setLayoutParams(params);

            if (compact) {
                final int index = i;
                textView.setOnClickListener(v -> {
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    performHaptic(8);
                    if (linkedInsights != null && linkedInsights.size() > index) {
                        performInsightAction(linkedInsights.get(index));
                    } else {
                        navigateToTab(MainActivity.DEST_INSIGHTS);
                    }
                });
            }

            container.addView(textView);
        }
    }

    private List<String> createFallbackInsightReasons() {
        List<String> reasons = new ArrayList<>();
        if (currentState != null && currentState.baselineComparisonText != null) {
            reasons.add(currentState.baselineComparisonText);
        }
        reasons.add("MindTrace blends check-ins, behavior shifts, and routine consistency to score today's pattern.");
        return reasons;
    }

    private void performInsightAction(@Nullable HomeScreenState.InsightItem item) {
        if (item == null) {
            navigateToTab(MainActivity.DEST_INSIGHTS);
            return;
        }

        performHaptic(12);
        String actionType = item.actionType == null ? HomeScreenState.ACTION_PLAN : item.actionType;
        switch (actionType) {
            case HomeScreenState.ACTION_CHECK_IN:
                startActivity(new Intent(requireContext(), DailyCheckInActivity.class));
                return;
            case HomeScreenState.ACTION_SUPPORT:
            case HomeScreenState.ACTION_RESET:
                navigateToTab(MainActivity.DEST_INSIGHTS);
                return;
            case HomeScreenState.ACTION_PLAN:
                navigateToTab(MainActivity.DEST_INSIGHTS);
                return;
            case HomeScreenState.ACTION_NONE:
            default:
                navigateToTab(MainActivity.DEST_INSIGHTS);
        }
    }



    private int countUnlockedBadges(@Nullable String badgesUnlockedJson) {
        if (badgesUnlockedJson == null || badgesUnlockedJson.trim().isEmpty()) {
            return 0;
        }
        String[] parts = badgesUnlockedJson.split(",");
        int count = 0;
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }



    private void applyRiskLevelChip(StateChipView chip, int riskIndex) {
        int color = resolveRiskColor(riskIndex);
        chip.setChipColors(translucent(color, 52), color);
        if (riskIndex >= 70) {
            chip.setText("HIGH");
        } else if (riskIndex >= 40) {
            chip.setText("MODERATE");
        } else {
            chip.setText("LOW");
        }
    }

    private int resolveRiskColor(int riskIndex) {
        if (riskIndex >= 70) {
            return ColorSystem.RED;
        }
        if (riskIndex >= 40) {
            return ColorSystem.AMBER;
        }
        return ColorSystem.GREEN;
    }

    private void startHighRiskPulse() {
        pulseRisk.setVisibility(View.VISIBLE);
        pulseRisk.setAlpha(1f);
        // B.2: More dramatic pulse for high-risk states
        pulseRisk.setColors(0x80EF4444, 0x00EF4444);
        pulseRisk.startPulse(3400L);
        performHaptic(30);
    }

    private void stopHighRiskPulse() {
        pulseRisk.stopPulse();
        pulseRisk.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction(() -> pulseRisk.setVisibility(View.GONE))
                .start();
    }

    private void startOrbAnimation() {
        if (orbPrimary == null) {
            return;
        }
        if (orbAnimator != null) {
            orbAnimator.cancel();
        }
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(orbPrimary, View.SCALE_X, 0.92f, 1.08f);
        scaleX.setRepeatMode(ObjectAnimator.REVERSE);
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleX.setDuration(4000L);
        scaleX.setInterpolator(new LinearInterpolator());

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(orbPrimary, View.SCALE_Y, 0.92f, 1.08f);
        scaleY.setRepeatMode(ObjectAnimator.REVERSE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setDuration(4000L);
        scaleY.setInterpolator(new LinearInterpolator());

        ObjectAnimator alpha = ObjectAnimator.ofFloat(orbPrimary, View.ALPHA, 0.60f, 0.95f);
        alpha.setRepeatMode(ObjectAnimator.REVERSE);
        alpha.setRepeatCount(ObjectAnimator.INFINITE);
        alpha.setDuration(6000L);
        alpha.setInterpolator(new LinearInterpolator());

        orbAnimator = new AnimatorSet();
        List<android.animation.Animator> animators = new ArrayList<>();
        animators.add(scaleX);
        animators.add(scaleY);
        animators.add(alpha);

        if (orbSecondary != null) {
            ObjectAnimator secondaryScaleX = ObjectAnimator.ofFloat(orbSecondary, View.SCALE_X, 0.88f, 1.06f);
            secondaryScaleX.setRepeatMode(ObjectAnimator.REVERSE);
            secondaryScaleX.setRepeatCount(ObjectAnimator.INFINITE);
            secondaryScaleX.setDuration(5200L);
            secondaryScaleX.setInterpolator(new LinearInterpolator());

            ObjectAnimator secondaryScaleY = ObjectAnimator.ofFloat(orbSecondary, View.SCALE_Y, 0.88f, 1.06f);
            secondaryScaleY.setRepeatMode(ObjectAnimator.REVERSE);
            secondaryScaleY.setRepeatCount(ObjectAnimator.INFINITE);
            secondaryScaleY.setDuration(5200L);
            secondaryScaleY.setInterpolator(new LinearInterpolator());

            ObjectAnimator secondaryAlpha = ObjectAnimator.ofFloat(orbSecondary, View.ALPHA, 0.45f, 0.84f);
            secondaryAlpha.setRepeatMode(ObjectAnimator.REVERSE);
            secondaryAlpha.setRepeatCount(ObjectAnimator.INFINITE);
            secondaryAlpha.setDuration(6800L);
            secondaryAlpha.setInterpolator(new LinearInterpolator());
            animators.add(secondaryScaleX);
            animators.add(secondaryScaleY);
            animators.add(secondaryAlpha);
        }

        orbAnimator = new AnimatorSet();
        orbAnimator.playTogether(animators);
        orbAnimator.start();
    }

    private CharSequence createHighlightedSummary(@Nullable String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            return "";
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(summary);
        highlightPhrase(builder, summary, "late-night", ColorSystem.RED);
        highlightPhrase(builder, summary, "scroll", ColorSystem.RED);
        highlightPhrase(builder, summary, "switch", ColorSystem.AMBER);
        highlightPhrase(builder, summary, "drift", ColorSystem.AMBER);
        highlightPhrase(builder, summary, "fresh start", ColorSystem.GREEN);
        highlightPhrase(builder, summary, "protect", ColorSystem.GREEN);
        highlightPhrase(builder, summary, "control", ColorSystem.GREEN);
        return builder;
    }

    private void highlightPhrase(
            SpannableStringBuilder builder,
            String source,
            String phrase,
            int color
    ) {
        String lowerSource = source.toLowerCase(Locale.ROOT);
        String lowerPhrase = phrase.toLowerCase(Locale.ROOT);
        int start = lowerSource.indexOf(lowerPhrase);
        while (start >= 0) {
            int end = start + phrase.length();
            builder.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = lowerSource.indexOf(lowerPhrase, end);
        }
    }

    private String describeRiskTrend(List<Float> riskHistory) {
        if (riskHistory == null || riskHistory.isEmpty()) {
            return "Trend unavailable";
        }
        float first = riskHistory.get(0);
        float last = riskHistory.get(riskHistory.size() - 1);
        int delta = Math.round(last - first);
        if (Math.abs(delta) < 4) {
            return "7-day trend stable";
        }
        if (delta > 0) {
            return "7-day trend rising " + delta + " pts";
        }
        return "7-day trend improving " + Math.abs(delta) + " pts";
    }

    private void burstConfettiFrom(View target, int count) {
        if (confettiView == null || target == null) {
            return;
        }
        int[] targetLocation = new int[2];
        int[] confettiLocation = new int[2];
        target.getLocationOnScreen(targetLocation);
        confettiView.getLocationOnScreen(confettiLocation);
        float x = targetLocation[0] - confettiLocation[0] + target.getWidth() / 2f;
        float y = targetLocation[1] - confettiLocation[1] + target.getHeight() / 2f;
        confettiView.post(() -> confettiView.burstFrom(x, y, count));
    }

    private void performHaptic(long durationMs) {
        try {
            Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMs);
            }
        } catch (Exception ignored) {
            // Ignore haptic failures on unsupported devices.
        }
    }

    private int translucent(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(int value) {
        return value * requireContext().getResources().getDisplayMetrics().density;
    }

    private long getStartOfTodayMillis() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void navigateToTab(String destination) {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).openDestination(destination);
            return;
        }
        if (isAdded()) {
            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_START_DESTINATION, destination);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
    }

    private String moodToEmoji(String mood) {
        if (mood == null) {
            return "--";
        }
        switch (mood.toLowerCase(Locale.ROOT)) {
            case "great":
            case "happy":
            case "excellent":
                return "\uD83D\uDE0A";
            case "good":
            case "calm":
                return "\uD83D\uDE42";
            case "okay":
            case "neutral":
                return "\uD83D\uDE10";
            case "bad":
            case "low":
            case "sad":
                return "\uD83D\uDE1F";
            case "terrible":
            case "awful":
            case "anxious":
            case "stressed":
                return "\uD83D\uDE30";
            default:
                return "\uD83D\uDE10";
        }
    }

    private String formatScreenTime(long screenTimeMs) {
        long hours = screenTimeMs / (1000L * 60L * 60L);
        long minutes = (screenTimeMs / (1000L * 60L)) % 60L;
        return hours + "h " + minutes + "m";
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }


    private void checkWeeklyAssessmentDue() {
        // Use shared executor — no shutdown needed
        weeklyCheckExecutor = com.mindtrace.ai.util.AppExecutors.diskIO();
        weeklyCheckExecutor.execute(() -> {
            try {
                AssessmentRepository repo = new AssessmentRepository(requireContext());
                boolean isDue = repo.isWeeklyAssessmentDue();
                int totalAssessments = repo.getWeeklyAssessmentHistory(100) != null
                        ? repo.getWeeklyAssessmentHistory(100).size()
                        : 0;

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isDue) {
                            cardWeeklyPrompt.setVisibility(View.GONE);
                            return;
                        }

                        cardWeeklyPrompt.setVisibility(View.VISIBLE);
                        UiMotion.animateCardEntry(cardWeeklyPrompt, 2);

                        if (totalAssessments == 0) {
                            chipWeeklyStatus.applyNeutralLabel("First review");
                            tvWeeklySubtitle.setText(
                                    "Complete your first weekly assessment to unlock longitudinal trend analysis and wellness scoring."
                            );
                        } else {
                            chipWeeklyStatus.setChipColors(translucent(ColorSystem.AMBER, 52), ColorSystem.AMBER);
                            chipWeeklyStatus.setText("Overdue");
                            tvWeeklySubtitle.setText(
                                    "Your weekly assessment is due. Reflect on the past 7 days to keep your trend data accurate."
                            );
                        }
                    });
                }
            } catch (Exception ignored) {
                // Fragment may have detached.
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        checkWeeklyAssessmentDue();
        startOrbAnimation();
        // Phase 5: Delegate owns insight rotation lifecycle
        if (currentState != null && currentState.aiInsightItems != null && currentState.aiInsightItems.size() > 1) {
            insightDelegate.startRotation();
        }
        if (currentStreakCount >= 7) {
            startStreakGlow();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Phase 5: Delegate owns insight rotation lifecycle
        insightDelegate.stopRotation();
        if (orbAnimator != null) {
            orbAnimator.cancel();
        }
        if (streakGlowAnimator != null) {
            streakGlowAnimator.cancel();
        }
        stopPipelineDotPulse();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // weeklyCheckExecutor is AppExecutors.diskIO() — never shut down the shared pool
        if (pulseRisk != null) {
            pulseRisk.stopPulse();
        }
        if (orbAnimator != null) {
            orbAnimator.cancel();
            orbAnimator = null;
        }
        if (streakGlowAnimator != null) {
            streakGlowAnimator.cancel();
            streakGlowAnimator = null;
        }
        if (loadingPulseAnimator != null) {
            loadingPulseAnimator.cancel();
            loadingPulseAnimator = null;
        }
        if (greetingAnimator != null) {
            greetingAnimator.cancel();
            greetingAnimator = null;
        }
        // Phase 5: Delegate lifecycle cleanup
        insightDelegate.stopRotation();
        fabDelegate.cleanup();
        // Pipeline progress strip cleanup
        stopPipelineDotPulse();
        // Behavior badge pulse cleanup
        stopBehaviorDotPulse();
    }

    // ── B.3: Glassmorphism border for premium stat cards ─────────────────
    private void applyGlassBorder(MaterialCardView card) {
        if (card == null) return;
        card.setStrokeWidth((int) dp(1));
        card.setStrokeColor(ColorStateList.valueOf(translucent(0xFFFFFFFF, 28)));
    }

    // ── Advanced: Animated badge state with pop when unlocked ────────────
    private void applyBadgeState(TextView badge, String label, boolean unlocked) {
        if (badge == null) return;
        String emoji;
        String displayLabel = label;
        switch (label) {
            case "Check-In":
                emoji = "\uD83D\uDCDD ";
                displayLabel = "Check in";
                break;
            case "Exercise":
                emoji = "\uD83D\uDCA8 ";
                displayLabel = "Breathe";
                break;
            case "Journal":  emoji = "✍ "; break;
            default:         emoji = ""; break;
        }
        badge.setText(emoji + displayLabel);
        if (unlocked) {
            badge.setBackgroundResource(R.drawable.bg_overview_badge_done);
            badge.setTextColor(ColorSystem.GREEN);
            // Advanced: Pop animation for newly unlocked badges
            UiMotion.animatePop(badge);
        } else {
            badge.setBackgroundResource(R.drawable.bg_overview_badge_pending);
            badge.setTextColor(ColorSystem.TEXT_SECONDARY);
        }
    }

    // ── Advanced: Smart greeting context messages ────────────────────────
    private String getSmartGreetingSubtext() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (currentStreakCount >= 14) {
            return "\uD83D\uDD25 " + currentStreakCount + "-day streak on fire";
        }
        if (currentStreakCount >= 7) {
            return "⭐ " + currentStreakCount + "-day streak active";
        }
        if (hour >= 5 && hour < 9) {
            return "Early start advantage";
        }
        if (hour >= 9 && hour < 12) {
            return "Peak focus hours";
        }
        if (hour >= 12 && hour < 14) {
            return "Midday momentum";
        }
        if (hour >= 14 && hour < 18) {
            return "Afternoon discipline";
        }
        if (hour >= 18 && hour < 21) {
            return "Wind-down phase";
        }
        return "Night protection active";
    }

    // ══════════════════════════════════════════════════════════════════════
    // PHASE C: Advanced Features
    // ══════════════════════════════════════════════════════════════════════

    // ── C.1: Screen Time Sparkline ───────────────────────────────────────
    private void renderScreenTimeSparkline(@Nullable List<com.mindtrace.ai.database.entity.DailyUsage> history) {
        if (cardScreenTimeSparkline == null || sparklineScreenTime == null) return;
        if (history == null || history.size() < 2) {
            cardScreenTimeSparkline.setVisibility(View.GONE);
            return;
        }

        cardScreenTimeSparkline.setVisibility(View.VISIBLE);
        List<Float> values = new ArrayList<>();
        for (com.mindtrace.ai.database.entity.DailyUsage day : history) {
            values.add(day.getScreenTimeHours());
        }
        sparklineScreenTime.setData(values);
        UiMotion.animateCardEntry(cardScreenTimeSparkline, 3);

        // Calculate trend
        if (values.size() >= 2) {
            float latest = values.get(values.size() - 1);
            float previous = values.get(values.size() - 2);
            float delta = latest - previous;
            if (tvSparklineTrend != null) {
                if (delta < -0.3f) {
                    tvSparklineTrend.setText("↓ improving");
                    tvSparklineTrend.setTextColor(ColorSystem.GREEN);
                } else if (delta > 0.3f) {
                    tvSparklineTrend.setText("↑ rising");
                    tvSparklineTrend.setTextColor(ColorSystem.RED);
                } else {
                    tvSparklineTrend.setText("→ steady");
                    tvSparklineTrend.setTextColor(ColorSystem.AMBER);
                }
            }
        }
    }

    // ── C.2: Mood Trend Mini-Row ─────────────────────────────────────────
    private void renderMoodTrend(@Nullable List<QuestionnaireResponse> responses) {
        if (cardMoodTrend == null || layoutMoodTrendEmojis == null) return;
        if (responses == null || responses.size() < 2) {
            cardMoodTrend.setVisibility(View.GONE);
            return;
        }

        cardMoodTrend.setVisibility(View.VISIBLE);
        layoutMoodTrendEmojis.removeAllViews();

        int count = Math.min(7, responses.size());
        for (int i = count - 1; i >= 0; i--) {
            TextView emoji = new TextView(requireContext());
            emoji.setText(moodToEmoji(responses.get(i).mood));
            emoji.setTextSize(22);
            emoji.setPadding((int) dp(8), (int) dp(4), (int) dp(8), (int) dp(4));

            // Day label below emoji
            LinearLayout dayColumn = new LinearLayout(requireContext());
            dayColumn.setOrientation(LinearLayout.VERTICAL);
            dayColumn.setGravity(android.view.Gravity.CENTER);
            dayColumn.addView(emoji);

            TextView dayLabel = new TextView(requireContext());
            dayLabel.setTextSize(10);
            dayLabel.setTextColor(ColorSystem.TEXT_SECONDARY);
            dayLabel.setGravity(android.view.Gravity.CENTER);
            if (i == 0) {
                dayLabel.setText("Today");
            } else {
                dayLabel.setText(i + "d");
            }
            dayColumn.addView(dayLabel);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            dayColumn.setLayoutParams(lp);
            layoutMoodTrendEmojis.addView(dayColumn);
        }
        UiMotion.animatePillStagger(layoutMoodTrendEmojis, 50);
        UiMotion.animateCardEntry(cardMoodTrend, 4);
    }

    // ── C.4: Behavior Score Badge — Premium ────────────────────────────────
    private void renderBehaviorBadge(@Nullable com.mindtrace.ai.behavior.BehaviorReport report) {
        if (chipBehaviorScore == null || layoutBehaviorBadge == null) return;
        if (report == null || !report.dataAvailable) {
            layoutBehaviorBadge.setVisibility(View.GONE);
            stopBehaviorDotPulse();
            return;
        }

        layoutBehaviorBadge.setVisibility(View.VISIBLE);
        String label;
        int color;
        if (report.bingeSessionCount >= 2 || report.hasLoopPattern) {
            label = "⚠ Watch";
            color = ColorSystem.AMBER;
        } else if (report.rapidSwitchCount >= 10) {
            label = "🔴 Alert";
            color = ColorSystem.RED;
        } else {
            label = "✅ Healthy";
            color = ColorSystem.GREEN;
        }
        chipBehaviorScore.setText("Behavior: " + label);
        chipBehaviorScore.setChipColors(translucent(color, 40), color);

        // Premium: Color the status dot and start pulsing animation
        if (viewBehaviorStatusDot != null) {
            android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
            dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dotBg.setColor(color);
            viewBehaviorStatusDot.setBackground(dotBg);
            startBehaviorDotPulse();
        }

        // Premium: Clickable area is the entire card container
        View clickTarget = cardBehaviorBadge != null ? cardBehaviorBadge : chipBehaviorScore;
        clickTarget.setOnClickListener(v -> {
            performHaptic(10);
            // Micro-press animation
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction(() ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            ).start();
            showBehaviorBottomSheet(report, label);
        });
    }

    private void startBehaviorDotPulse() {
        stopBehaviorDotPulse();
        if (viewBehaviorStatusDot == null) return;
        behaviorDotPulse = android.animation.ObjectAnimator.ofFloat(viewBehaviorStatusDot, View.ALPHA, 1f, 0.3f);
        behaviorDotPulse.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
        behaviorDotPulse.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        behaviorDotPulse.setDuration(1200L);
        behaviorDotPulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        behaviorDotPulse.start();
    }

    private void stopBehaviorDotPulse() {
        if (behaviorDotPulse != null) {
            behaviorDotPulse.cancel();
            behaviorDotPulse = null;
        }
    }

    private void showBehaviorBottomSheet(@androidx.annotation.NonNull com.mindtrace.ai.behavior.BehaviorReport report, String badgeLabel) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View sheet = android.view.LayoutInflater.from(requireContext()).inflate(R.layout.sheet_behavior_breakdown, null);

        // ── Header ──────────────────────────────────────────────────────────
        StateChipView statusChip = sheet.findViewById(R.id.chip_behavior_sheet_status);
        TextView subtitleView = sheet.findViewById(R.id.tv_behavior_sheet_subtitle);
        TextView explanationView = sheet.findViewById(R.id.tv_behavior_sheet_explanation);

        int statusColor;
        if (badgeLabel.contains("Watch")) {
            statusColor = ColorSystem.AMBER;
        } else if (badgeLabel.contains("Alert")) {
            statusColor = ColorSystem.RED;
        } else {
            statusColor = ColorSystem.GREEN;
        }
        statusChip.setText(badgeLabel);
        statusChip.setChipColors(translucent(statusColor, 40), statusColor);

        subtitleView.setText(report.summaryLabel != null ? report.summaryLabel : "Analyzing behavior patterns");
        if (report.explanation != null && !report.explanation.isEmpty()) {
            explanationView.setVisibility(View.VISIBLE);
            explanationView.setText(report.explanation);
        } else {
            explanationView.setVisibility(View.GONE);
        }

        // ── Signal Cards with Icons ──────────────────────────────────────────
        LinearLayout signalCardsLayout = sheet.findViewById(R.id.layout_behavior_signal_cards);
        signalCardsLayout.removeAllViews();

        java.util.List<String[]> signalItems = new java.util.ArrayList<>();
        if (report.hasLoopPattern) {
            signalItems.add(new String[]{"🔄", "Looping behavior detected", "Repetitive app cycling identified"});
        }
        if (report.rapidSwitchCount > 0) {
            signalItems.add(new String[]{"🔀", "High context switching", report.rapidSwitchCount + " rapid app swaps detected"});
        }
        if (report.bingeSessionCount > 0) {
            signalItems.add(new String[]{"⏱", "Prolonged sessions", report.bingeSessionCount + " extended usage sessions"});
        }
        if (report.reasoningNotes != null) {
            for (String note : report.reasoningNotes) {
                signalItems.add(new String[]{"📊", note, null});
            }
        }
        if (signalItems.isEmpty()) {
            signalItems.add(new String[]{"✅", "Balanced behavior", "No concerning patterns detected"});
        }

        for (int i = 0; i < signalItems.size(); i++) {
            String[] item = signalItems.get(i);
            signalCardsLayout.addView(createPremiumSignalCard(item[0], item[1], item[2], i));
        }

        // ── Loop Visualization with App Icons ──────────────────────────────
        LinearLayout loopSection = sheet.findViewById(R.id.layout_behavior_loop_section);
        LinearLayout loopIconsLayout = sheet.findViewById(R.id.layout_behavior_loop_icons);
        TextView loopDetail = sheet.findViewById(R.id.tv_behavior_loop_detail);
        if (report.frequentAppLoops != null && !report.frequentAppLoops.isEmpty()) {
            loopSection.setVisibility(View.VISIBLE);
            loopIconsLayout.removeAllViews();

            android.content.pm.PackageManager pm = requireContext().getPackageManager();
            // Parse unique packages from loop entries (format: "com.pkg1 -> com.pkg2 (Nx)")
            java.util.LinkedHashSet<String> loopPackages = new java.util.LinkedHashSet<>();
            for (String entry : report.frequentAppLoops) {
                // Strip count suffix like " (11x)"
                String cleaned = entry.replaceAll("\\s*\\(\\d+x\\)\\s*$", "").trim();
                // Split on " -> "
                String[] parts = cleaned.split("\\s*->\\s*");
                for (String part : parts) {
                    String pkg = part.trim();
                    if (!pkg.isEmpty()) loopPackages.add(pkg);
                }
            }

            int loopIndex = 0;
            for (String pkg : loopPackages) {
                // Add arrow separator between icons
                if (loopIndex > 0) {
                    TextView arrow = new TextView(requireContext());
                    arrow.setText("→");
                    arrow.setTextColor(ColorSystem.TEXT_SECONDARY);
                    arrow.setTextSize(16f);
                    arrow.setPadding((int) dp(6), 0, (int) dp(6), 0);
                    arrow.setGravity(android.view.Gravity.CENTER);
                    loopIconsLayout.addView(arrow);
                }

                // Build icon + label column
                LinearLayout iconColumn = new LinearLayout(requireContext());
                iconColumn.setOrientation(LinearLayout.VERTICAL);
                iconColumn.setGravity(android.view.Gravity.CENTER);
                int colPad = (int) dp(4);
                iconColumn.setPadding(colPad, 0, colPad, 0);

                // App icon
                android.widget.ImageView iconView = new android.widget.ImageView(requireContext());
                int iconSize = (int) dp(36);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconView.setLayoutParams(iconLp);
                try {
                    android.graphics.drawable.Drawable appIcon = pm.getApplicationIcon(pkg);
                    iconView.setImageDrawable(appIcon);
                } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                    // Fallback: show a default circle with first letter
                    iconView.setBackgroundResource(R.drawable.bg_behavior_sheet_icon_circle);
                }
                iconColumn.addView(iconView);

                // App name label
                TextView nameLabel = new TextView(requireContext());
                String displayName = pkg;
                try {
                    android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                    CharSequence label = pm.getApplicationLabel(appInfo);
                    if (label != null) displayName = label.toString();
                } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                    // Simplify package name
                    if (displayName.contains(".")) {
                        String[] parts = displayName.split("\\.");
                        displayName = parts[parts.length - 1];
                    }
                }
                // Truncate long names
                if (displayName.length() > 10) {
                    displayName = displayName.substring(0, 9) + "…";
                }
                nameLabel.setText(displayName);
                nameLabel.setTextColor(ColorSystem.TEXT_SECONDARY);
                nameLabel.setTextSize(10f);
                nameLabel.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                nameLp.topMargin = (int) dp(4);
                nameLabel.setLayoutParams(nameLp);
                iconColumn.addView(nameLabel);

                loopIconsLayout.addView(iconColumn);
                loopIndex++;
            }
        } else {
            loopSection.setVisibility(View.GONE);
        }

        // ── Quick Stats Row — Always visible with real-time data ─────────
        TextView ratioValue = sheet.findViewById(R.id.tv_behavior_stat_ratio_value);
        TextView switchesValue = sheet.findViewById(R.id.tv_behavior_stat_switches_value);

        // Active/Passive ratio — real-time from behavior signal
        ratioValue.setText(String.format(java.util.Locale.US, "%.1f", report.activeVsPassiveRatio));

        // App switches — use total switch count (real-time)
        switchesValue.setText(String.valueOf(report.appSwitchCount));

        // ── Action Button ────────────────────────────────────────────────────
        com.google.android.material.button.MaterialButton actionBtn = sheet.findViewById(R.id.btn_behavior_sheet_action);
        actionBtn.setOnClickListener(v -> {
            dialog.dismiss();
            navigateToTab(MainActivity.DEST_USAGE);
        });

        dialog.setContentView(sheet);
        dialog.show();

        // Premium stagger animation for signal cards
        for (int i = 0; i < signalCardsLayout.getChildCount(); i++) {
            View child = signalCardsLayout.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dp(20));
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(120L + i * 80L)
                    .setDuration(300L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private View createPremiumSignalCard(String icon, String title, @Nullable String subtitle, int index) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.bg_behavior_sheet_card);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int h = (int) dp(16);
        int v = (int) dp(14);
        card.setPadding(h, v, h, v);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (index > 0) cardLp.topMargin = (int) dp(10);
        card.setLayoutParams(cardLp);

        // Icon circle
        TextView iconView = new TextView(requireContext());
        iconView.setText(icon);
        iconView.setTextSize(20f);
        iconView.setGravity(android.view.Gravity.CENTER);
        iconView.setBackgroundResource(R.drawable.bg_behavior_sheet_icon_circle);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int) dp(42), (int) dp(42));
        iconView.setLayoutParams(iconLp);
        card.addView(iconView);

        // Text column
        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart((int) dp(14));
        textCol.setLayoutParams(textLp);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextColor(ColorSystem.TEXT_PRIMARY);
        titleView.setTextSize(14f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = new TextView(requireContext());
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(ColorSystem.TEXT_SECONDARY);
            subtitleView.setTextSize(12f);
            LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            stLp.topMargin = (int) dp(3);
            subtitleView.setLayoutParams(stLp);
            textCol.addView(subtitleView);
        }

        card.addView(textCol);
        return card;
    }

    // ── C.5: Crisis Banner ───────────────────────────────────────────────
    private void showCrisisBanner(String title, String message) {
        if (bannerCrisis == null) return;
        bannerCrisis.setVisibility(View.VISIBLE);
        if (tvCrisisTitle != null) tvCrisisTitle.setText(title);
        if (tvCrisisMessage != null) tvCrisisMessage.setText(message);
        if (btnCrisisAction != null) {
            btnCrisisAction.setOnClickListener(v -> {
                performHaptic(20);
                openSupportPanel();
            });
        }
        // Urgent pulse animation on crisis banner
        bannerCrisis.setAlpha(0f);
        bannerCrisis.animate().alpha(1f).setDuration(400L).start();
        performHaptic(50);
    }

    private void hideCrisisBanner() {
        if (bannerCrisis == null) return;
        bannerCrisis.animate()
                .alpha(0f)
                .setDuration(200L)
                .withEndAction(() -> bannerCrisis.setVisibility(View.GONE))
                .start();
    }

    // ── C.7: Assessment Due Indicator — Glow Effect ─────────────────────
    private android.animation.AnimatorSet assessmentGlowAnim;

    private void renderAssessmentDueChip() {
        if (chipAssessmentDue == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("assessment_state", Context.MODE_PRIVATE);
        long lastAssessmentTime = prefs.getLong("last_assessment_time", 0L);
        long daysSince = (System.currentTimeMillis() - lastAssessmentTime) / (1000L * 60 * 60 * 24);

        if (lastAssessmentTime == 0 || daysSince >= 7) {
            chipAssessmentDue.setVisibility(View.VISIBLE);
            int statusColor;
            if (daysSince > 14) {
                chipAssessmentDue.setText("Assessment overdue");
                statusColor = ColorSystem.RED;
            } else {
                chipAssessmentDue.setText("Assessment due");
                statusColor = ColorSystem.AMBER;
            }
            chipAssessmentDue.setChipColors(translucent(statusColor, 40), statusColor);

            // Glowing pulse animation
            stopAssessmentGlow();
            android.animation.ObjectAnimator alphaAnim =
                    android.animation.ObjectAnimator.ofFloat(chipAssessmentDue, View.ALPHA, 1f, 0.55f);
            alphaAnim.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
            alphaAnim.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            android.animation.ObjectAnimator scaleX =
                    android.animation.ObjectAnimator.ofFloat(chipAssessmentDue, View.SCALE_X, 1f, 1.04f);
            scaleX.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
            scaleX.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            android.animation.ObjectAnimator scaleY =
                    android.animation.ObjectAnimator.ofFloat(chipAssessmentDue, View.SCALE_Y, 1f, 1.04f);
            scaleY.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
            scaleY.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            assessmentGlowAnim = new android.animation.AnimatorSet();
            assessmentGlowAnim.playTogether(alphaAnim, scaleX, scaleY);
            assessmentGlowAnim.setDuration(1200L);
            assessmentGlowAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            assessmentGlowAnim.start();

            chipAssessmentDue.setOnClickListener(v -> {
                performHaptic(8);
                startActivity(new android.content.Intent(requireContext(), WeeklyAssessmentActivity.class));
            });
        } else {
            chipAssessmentDue.setVisibility(View.GONE);
            stopAssessmentGlow();
        }
    }

    private void stopAssessmentGlow() {
        if (assessmentGlowAnim != null) {
            assessmentGlowAnim.cancel();
            assessmentGlowAnim = null;
        }
    }

    // ── C.8: Quick Stats Long-Press Tooltip ──────────────────────────────
    private void setupStatTooltips() {
        if (cardStatScreenTime != null) {
            cardStatScreenTime.setOnLongClickListener(v -> {
                performHaptic(6);
                com.google.android.material.snackbar.Snackbar.make(v,
                        "Total screen time tracked today. Tap to see full breakdown.",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                return true;
            });
        }
        if (cardStatStreak != null) {
            cardStatStreak.setOnLongClickListener(v -> {
                performHaptic(6);
                String msg = currentStreakCount >= 7
                        ? "🔥 You've been active " + currentStreakCount + " days straight!"
                        : "Days in a row you've completed at least one check-in.";
                com.google.android.material.snackbar.Snackbar.make(v, msg,
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                return true;
            });
        }
        if (cardStatMood != null) {
            cardStatMood.setOnLongClickListener(v -> {
                performHaptic(6);
                com.google.android.material.snackbar.Snackbar.make(v,
                        "Your latest mood check-in. Tap to see mood journal.",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                return true;
            });
        }
    }



    // ── C.18: Accessibility Labels ───────────────────────────────────────
    private void applyAccessibilityLabels() {
        setContentDesc(fabPrimary, "Quick actions menu");
        setContentDesc(fabCheckIn, "Start check-in");
        setContentDesc(fabJournal, "Open journal");
        setContentDesc(fabBreathe, "Start breathing exercise");
        setContentDesc(fabReset, "Reset session");
        setContentDesc(fabFocus, "Start focus timer");
        setContentDesc(cardStatScreenTime, "Screen time statistics");
        setContentDesc(cardStatStreak, "Current streak statistics");
        setContentDesc(cardStatTasks, "Task completion statistics");
        setContentDesc(cardStatMood, "Current mood statistics");
        setContentDesc(cardPatternRadar, "Live pattern radar");
        setContentDesc(cardFocusWindow, "Focus window");
        setContentDesc(cardAiInsight, "AI insight card");
        setContentDesc(cardForecast, "Tomorrow's forecast");
        setContentDesc(btnPrimaryAction, "Primary action button");
    }

    private void setContentDesc(View view, String desc) {
        if (view != null) {
            view.setContentDescription(desc);
        }
    }

    // ── C.19: Dashboard Screenshot Share ─────────────────────────────────
    private void shareScreenshot() {
        if (scrollView == null || !isAdded()) return;
        try {
            scrollView.setDrawingCacheEnabled(true);
            scrollView.buildDrawingCache();
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(scrollView.getDrawingCache());
            scrollView.setDrawingCacheEnabled(false);

            java.io.File cachePath = new java.io.File(requireContext().getCacheDir(), "images");
            cachePath.mkdirs();
            java.io.File file = new java.io.File(cachePath, "mindtrace_dashboard.png");
            java.io.FileOutputStream stream = new java.io.FileOutputStream(file);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            android.net.Uri contentUri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file
            );
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(shareIntent, "Share Dashboard"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not share screenshot", Toast.LENGTH_SHORT).show();
        }
    }
}
