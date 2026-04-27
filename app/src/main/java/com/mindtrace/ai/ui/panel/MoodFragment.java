package com.mindtrace.ai.ui.panel;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.JournalEntry;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.repository.AssessmentRepository;
import com.mindtrace.ai.ui.JournalActivity;
import com.mindtrace.ai.ui.QuestionnaireHistoryAdapter;
import com.mindtrace.ai.ui.UiFormatting;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.ui.WeeklyAssessmentActivity;
import com.mindtrace.ai.ui.components.EmptyStateHelper;
import com.mindtrace.ai.ui.components.SparklineView;
import com.mindtrace.ai.ui.widget.MoodCalendarView;
import com.mindtrace.ai.viewmodel.DashboardViewModel;
import com.mindtrace.ai.viewmodel.JournalViewModel;
import android.animation.ValueAnimator;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

public class MoodFragment extends Fragment {
    private DashboardViewModel dashboardViewModel;
    private JournalViewModel journalViewModel;
    private final QuestionnaireHistoryAdapter historyAdapter = new QuestionnaireHistoryAdapter();

    // Section 1: Hero
    private View viewMoodGlow;
    private TextView tvMoodEmoji, tvMoodFeeling, tvQuickContext;
    private LinearLayout llMoodDots, llMoodDotLabels;

    // Section 2: Chart
    private LineChart moodChart;
    private TextView btn7d, btn30d;
    private boolean showing30d = false;

    // Section 3: Sparklines
    private SparklineView sparklineStress, sparklineSleep;
    private TextView tvStressAvg, tvSleepAvg;

    // Section 4: Journal
    private TextView tvJournalStreak, tvJournalTodayPreview, tvJournalPrevHeader;
    private LinearLayout llJournalHistory;

    // Section 5: Consistency
    private TextView tvStatCheckins, tvStatJournal, tvStatTasks;
    private TextView tvCurrentStreak, tvLongestStreak;

    // Section 6: History + Calendar
    private View containerEmpty;
    private MoodCalendarView moodCalendar;
    private View cardWeeklyAssessment;

    // Bonus: Correlation + Sentiment
    private View cardCorrelation, cardSentiment;
    private TextView tvCorrelationInsight, tvSentimentLabel;
    private SparklineView sparklineSentiment;

    // Bonus: Donut
    private View cardMoodDonut;
    private PieChart pieMoodDistribution;
    private LinearLayout llDonutLegend;

    // Data
    private List<QuestionnaireResponse> cachedResponses;

    public MoodFragment() { super(R.layout.fragment_mood); }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);
        journalViewModel = new ViewModelProvider(requireActivity()).get(JournalViewModel.class);
        bindViews(v);
        setupListeners(v);
        setupParallaxScroll(v);
        observeData();
    }

    private void bindViews(View v) {
        viewMoodGlow = v.findViewById(R.id.view_mood_glow);
        tvMoodEmoji = v.findViewById(R.id.tv_mood_emoji);
        tvMoodFeeling = v.findViewById(R.id.tv_mood_feeling);
        tvQuickContext = v.findViewById(R.id.tv_mood_quick_context);
        llMoodDots = v.findViewById(R.id.ll_mood_dots);
        llMoodDotLabels = v.findViewById(R.id.ll_mood_dot_labels);
        moodChart = v.findViewById(R.id.panel_mood_chart);
        btn7d = v.findViewById(R.id.btn_mood_7d);
        btn30d = v.findViewById(R.id.btn_mood_30d);
        sparklineStress = v.findViewById(R.id.sparkline_stress);
        sparklineSleep = v.findViewById(R.id.sparkline_sleep);
        tvStressAvg = v.findViewById(R.id.tv_stress_avg);
        tvSleepAvg = v.findViewById(R.id.tv_sleep_avg);
        tvJournalStreak = v.findViewById(R.id.tv_journal_streak);
        tvJournalTodayPreview = v.findViewById(R.id.tv_journal_today_preview);
        tvJournalPrevHeader = v.findViewById(R.id.tv_journal_prev_header);
        llJournalHistory = v.findViewById(R.id.ll_journal_history);
        tvStatCheckins = v.findViewById(R.id.tv_stat_checkins_num);
        tvStatJournal = v.findViewById(R.id.tv_stat_journal_num);
        tvStatTasks = v.findViewById(R.id.tv_stat_tasks_num);
        tvCurrentStreak = v.findViewById(R.id.tv_current_streak);
        tvLongestStreak = v.findViewById(R.id.tv_longest_streak);
        containerEmpty = v.findViewById(R.id.container_mood_empty);
        moodCalendar = v.findViewById(R.id.mood_calendar);
        cardWeeklyAssessment = v.findViewById(R.id.card_weekly_assessment);

        // Bonus sections
        cardCorrelation = v.findViewById(R.id.card_correlation);
        cardSentiment = v.findViewById(R.id.card_sentiment);
        tvCorrelationInsight = v.findViewById(R.id.tv_correlation_insight);
        tvSentimentLabel = v.findViewById(R.id.tv_sentiment_label);
        sparklineSentiment = v.findViewById(R.id.sparkline_sentiment);

        RecyclerView rv = v.findViewById(R.id.rv_mood_history);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(historyAdapter);
        rv.setNestedScrollingEnabled(false);

        // Set sparkline colors per blueprint
        sparklineStress.setLineColor(Color.parseColor("#FF6B6B"));
        sparklineSleep.setLineColor(Color.parseColor("#7C8FFF"));
        if (sparklineSentiment != null) sparklineSentiment.setLineColor(Color.parseColor("#4ADE80"));

        // Donut
        cardMoodDonut = v.findViewById(R.id.card_mood_donut);
        pieMoodDistribution = v.findViewById(R.id.pie_mood_distribution);
        llDonutLegend = v.findViewById(R.id.ll_donut_legend);
    }

    private void setupListeners(View v) {
        // #8: Daily rotating journal prompts
        setRotatingJournalPrompt(v);

        v.findViewById(R.id.btn_write_journal).setOnClickListener(x ->
                startActivity(new Intent(requireContext(), JournalActivity.class)));

        btn7d.setOnClickListener(x -> { showing30d = false; updateToggle(); renderChart(); });
        btn30d.setOnClickListener(x -> { showing30d = true; updateToggle(); renderChart(); });

        if (cardWeeklyAssessment != null) {
            cardWeeklyAssessment.setOnClickListener(x ->
                    startActivity(new Intent(requireContext(), WeeklyAssessmentActivity.class)));
            checkWeeklyAssessmentDue();
        }

        // #7: Sparkline card tap-to-expand
        v.findViewById(R.id.card_stress_sparkline).setOnClickListener(x -> showSparklineDetail("Stress", "#FF6B6B", true));
        v.findViewById(R.id.card_sleep_sparkline).setOnClickListener(x -> showSparklineDetail("Sleep", "#7C8FFF", false));

        // #10: Calendar day tap tooltip
        if (moodCalendar != null) {
            moodCalendar.setOnDayClickListener((dayTs, mood, dateLabel) -> {
                String msg = mood != null
                        ? getMoodEmoji(mood) + " " + mood + " on " + dateLabel
                        : "No check-in on " + dateLabel;
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            });
        }

        // Animations
        UiMotion.animateCardEntry(v.findViewById(R.id.card_mood_hero), 0);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_mood_chart), 1);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_stress_sparkline), 2);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_sleep_sparkline), 2);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_journal), 3);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_consistency), 4);
        UiMotion.animateCardEntry(v.findViewById(R.id.card_mood_calendar), 5);

        // Press animations on interactive cards
        UiMotion.attachPressAnimation(v.findViewById(R.id.card_journal));
        UiMotion.attachPressAnimation(v.findViewById(R.id.card_mood_hero));
    }

    private void observeData() {
        dashboardViewModel.getStateHistory().observe(getViewLifecycleOwner(), responses -> {
            cachedResponses = responses;
            renderHero(responses);
            renderMoodDots(responses);
            renderChart();
            renderSparklines(responses);
            renderMoodDonut(responses);
            renderCalendar(responses);
            renderHistory(responses);
        });
        journalViewModel.getTodayEntry().observe(getViewLifecycleOwner(), this::renderTodayJournal);
        journalViewModel.getRecentEntries().observe(getViewLifecycleOwner(), this::renderJournalHistory);
        journalViewModel.getCurrentStreak().observe(getViewLifecycleOwner(), streak -> {
            tvJournalStreak.setText(streak > 0 ? streak + " day \uD83D\uDD25" : "Start today \uD83D\uDC9B");
            tvCurrentStreak.setText("Current streak: " + streak + " days");
            // Animate stat boxes on data arrival
            UiMotion.animatePillStagger(requireView().findViewById(R.id.ll_stat_boxes), 40);
        });
        journalViewModel.getLongestStreak().observe(getViewLifecycleOwner(), longest ->
                tvLongestStreak.setText("Longest streak: " + longest + " days"));
        journalViewModel.getEngagementStats().observe(getViewLifecycleOwner(), stats -> {
            // #2: Animated counter roll-up
            animateCounter(tvStatCheckins, stats[0]);
            animateCounter(tvStatJournal, stats[1]);
            animateCounter(tvStatTasks, stats[2]);
        });
        journalViewModel.getStreakMessage().observe(getViewLifecycleOwner(), msg ->
                tvCurrentStreak.setText(msg));

        // #12: Multi-insight carousel (shows all correlations, swipeable)
        journalViewModel.getCorrelationInsights().observe(getViewLifecycleOwner(), insights -> {
            if (insights != null && !insights.isEmpty() && cardCorrelation != null && tvCorrelationInsight != null) {
                renderInsightCarousel(insights);
                cardCorrelation.setVisibility(View.VISIBLE);
                UiMotion.animateCardEntry(cardCorrelation, 6);
            } else if (cardCorrelation != null) {
                cardCorrelation.setVisibility(View.GONE);
            }
        });

        // Bonus: Sentiment sparkline
        journalViewModel.getSentimentTrend().observe(getViewLifecycleOwner(), scores -> {
            if (scores != null && !scores.isEmpty() && sparklineSentiment != null && cardSentiment != null) {
                sparklineSentiment.setData(scores);
                cardSentiment.setVisibility(View.VISIBLE);
                UiMotion.animateCardEntry(cardSentiment, 7);
            } else if (cardSentiment != null) {
                cardSentiment.setVisibility(View.GONE);
            }
        });
        journalViewModel.getAvgSentiment().observe(getViewLifecycleOwner(), avg -> {
            if (tvSentimentLabel != null && avg != null) {
                String label = avg > 0.3f ? "Positive ↑" : avg < -0.3f ? "Negative ↓" : "Neutral —";
                tvSentimentLabel.setText("Avg: " + label);
                tvSentimentLabel.setTextColor(avg > 0.3f ? Color.parseColor("#4ADE80")
                        : avg < -0.3f ? Color.parseColor("#FF6B6B") : Color.parseColor("#8896B0"));
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        journalViewModel.loadAll();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 1: EMOTIONAL STATE HERO
    // ═══════════════════════════════════════════════════════════════════

    private void renderHero(List<QuestionnaireResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            tvMoodEmoji.setText("\uD83D\uDE10");
            tvMoodFeeling.setText("Complete a check-in to see your mood");
            tvQuickContext.setText("Your emotional insights will appear here");
            return;
        }
        QuestionnaireResponse latest = responses.get(0);
        String mood = latest.mood != null ? latest.mood : "Neutral";

        tvMoodEmoji.setText(getMoodEmoji(mood));
        setGlowColor(getMoodGlowColor(mood));

        // Mood delta: compare with previous check-in
        if (responses.size() >= 2) {
            String prevMood = responses.get(1).mood;
            int delta = moodRank(mood) - moodRank(prevMood);
            String arrow = delta > 0 ? " \u2191" : delta < 0 ? " \u2193" : "";
            tvMoodFeeling.setText("You're feeling " + mood + arrow);
            tvMoodFeeling.setTextColor(delta > 0 ? Color.parseColor("#4ADE80")
                    : delta < 0 ? Color.parseColor("#FF6B6B")
                    : ContextCompat.getColor(requireContext(), R.color.text_main));
        } else {
            tvMoodFeeling.setText("You're feeling " + mood);
            tvMoodFeeling.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        }

        // Breathing pulse animation on emoji
        tvMoodEmoji.setScaleX(1f);
        tvMoodEmoji.setScaleY(1f);
        tvMoodEmoji.animate().scaleX(1.08f).scaleY(1.08f).setDuration(400)
                .withEndAction(() -> tvMoodEmoji.animate().scaleX(1f).scaleY(1f).setDuration(400).start()).start();

        // Glow pulse sync
        viewMoodGlow.setAlpha(0.6f);
        viewMoodGlow.animate().alpha(1f).setDuration(600)
                .withEndAction(() -> viewMoodGlow.animate().alpha(0.6f).setDuration(600).start()).start();

        // Quick context with richer formatting
        StringBuilder ctx = new StringBuilder();
        if (latest.stressLevel > 0) {
            String stressIcon = latest.stressLevel >= 4 ? "\uD83D\uDD34" : latest.stressLevel >= 3 ? "\uD83D\uDFE1" : "\uD83D\uDFE2";
            ctx.append(stressIcon).append(" Stress ").append(latest.stressLevel).append("/5");
        }
        if (latest.sleepHours > 0) {
            if (ctx.length() > 0) ctx.append("  ·  ");
            String sleepIcon = latest.sleepHours >= 7 ? "\uD83D\uDFE2" : latest.sleepHours >= 5 ? "\uD83D\uDFE1" : "\uD83D\uDD34";
            ctx.append(sleepIcon).append(" Sleep ").append(UiFormatting.formatSleep(latest.sleepHours));
        }
        if (latest.energyLevel != null && !latest.energyLevel.isEmpty()) {
            if (ctx.length() > 0) ctx.append("  ·  ");
            ctx.append("\u26A1 Energy ").append(latest.energyLevel);
        }
        tvQuickContext.setText(ctx.length() > 0 ? ctx.toString() : "Check-in recorded");

        // #3: Mood-based affirmation banner
        renderAffirmation(mood);
    }

    private void setGlowColor(int color) {
        // #5: Radial gradient glow — premium light-bloom effect
        android.graphics.drawable.GradientDrawable glow = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{ adjustAlpha(color, 0.35f), adjustAlpha(color, 0.10f), Color.TRANSPARENT }
        );
        glow.setShape(GradientDrawable.OVAL);
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(dp(50));
        glow.setGradientCenter(0.5f, 0.5f);
        glow.setSize(dp(90), dp(90));
        viewMoodGlow.setBackground(glow);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 1B: 7-DAY MOOD DOTS
    // ═══════════════════════════════════════════════════════════════════

    private void renderMoodDots(List<QuestionnaireResponse> responses) {
        llMoodDots.removeAllViews();
        llMoodDotLabels.removeAllViews();
        String[] dayLabels = {"M", "T", "W", "T", "F", "S", "S"};
        Map<Integer, String> dayMoods = new HashMap<>();

        if (responses != null) {
            Calendar cal = Calendar.getInstance();
            for (QuestionnaireResponse r : responses) {
                cal.setTimeInMillis(r.timestamp);
                int dow = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun..7=Sat
                int idx = (dow + 5) % 7; // Convert to 0=Mon..6=Sun
                if (!dayMoods.containsKey(idx)) dayMoods.put(idx, r.mood);
            }
        }

        Calendar today = Calendar.getInstance();
        int todayIdx = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7;

        for (int i = 0; i < 7; i++) {
            boolean isToday = (i == todayIdx);
            String mood = dayMoods.get(i);
            int dotSize = dp(isToday ? 12 : 10);
            int spacing = dp(12);

            // Dot
            View dot = new View(requireContext());
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            if (i > 0) dotParams.setMarginStart(spacing);
            dot.setLayoutParams(dotParams);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(mood != null ? getMoodDotColor(mood) : Color.parseColor("#1E2A42"));
            if (isToday) { dotBg.setStroke(dp(2), Color.WHITE); }
            dot.setBackground(dotBg);
            llMoodDots.addView(dot);

            // Stagger animation
            dot.setAlpha(0f);
            dot.animate().alpha(1f).setStartDelay(i * 40L).setDuration(200).start();

            // #6: Long-press tooltip showing mood for that day
            String[] fullDayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
            final String dotMood = mood;
            final String dayName = fullDayNames[i];
            dot.setOnLongClickListener(dotView -> {
                String tip = dotMood != null
                        ? dayName + " \u2014 " + dotMood + " " + getMoodEmoji(dotMood)
                        : dayName + " \u2014 No check-in";
                Toast.makeText(requireContext(), tip, Toast.LENGTH_SHORT).show();
                // Haptic feedback
                dotView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                return true;
            });

            // Label
            TextView label = new TextView(requireContext());
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dotSize, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) labelParams.setMarginStart(spacing);
            label.setLayoutParams(labelParams);
            label.setText(dayLabels[i]);
            label.setTextSize(10);
            label.setTextColor(Color.parseColor("#8896B0"));
            label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            llMoodDotLabels.addView(label);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 2: MOOD TREND CHART
    // ═══════════════════════════════════════════════════════════════════

    private void renderChart() {
        if (cachedResponses == null || cachedResponses.isEmpty()) {
            moodChart.setNoDataText("Mood trends appear after your first check-in.");
            moodChart.setNoDataTextColor(Color.parseColor("#8896B0"));
            moodChart.invalidate();
            return;
        }

        int limit = showing30d ? 30 : 7;
        List<QuestionnaireResponse> data = new ArrayList<>(cachedResponses);
        if (data.size() > limit) data = data.subList(0, limit);
        Collections.reverse(data);

        List<Entry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            entries.add(new Entry(i, UiFormatting.getMoodScore(data.get(i).mood)));
            xLabels.add(UiFormatting.formatDayLabel(data.get(i).timestamp));
        }

        LineDataSet ds = new LineDataSet(entries, "Mood");
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setColor(Color.parseColor("#7C8FFF"));
        ds.setCircleColor(Color.parseColor("#7C8FFF"));
        ds.setCircleHoleColor(Color.parseColor("#131D30"));
        ds.setCircleRadius(4f);
        ds.setCircleHoleRadius(2f);
        ds.setLineWidth(3f);
        ds.setDrawValues(false);
        ds.setDrawFilled(true);
        ds.setFillDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.bg_chart_fill));

        moodChart.setData(new LineData(ds));
        moodChart.getDescription().setEnabled(false);
        moodChart.getLegend().setEnabled(false);
        moodChart.setExtraOffsets(8f, 8f, 8f, 8f);
        moodChart.setTouchEnabled(true);
        moodChart.setDragEnabled(false);
        moodChart.setScaleEnabled(false);

        XAxis xAxis = moodChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.parseColor("#8896B0"));
        xAxis.setTextSize(12f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));

        YAxis left = moodChart.getAxisLeft();
        left.setAxisMinimum(1f);
        left.setAxisMaximum(4f);
        left.setGranularity(1f);
        left.setTextColor(Color.parseColor("#8896B0"));
        left.setDrawGridLines(false);
        left.setValueFormatter(new ValueFormatter() {
            @Override public String getAxisLabel(float value, AxisBase axis) {
                return UiFormatting.getMoodLabel(value);
            }
        });
        moodChart.getAxisRight().setEnabled(false);
        // #9: Left-to-right line draw + Y fade per blueprint spec
        moodChart.animateXY(1200, 700);
        moodChart.invalidate();
    }

    private void updateToggle() {
        btn7d.setBackgroundResource(showing30d ? R.drawable.bg_chip_unselected : R.drawable.bg_chip_selected);
        btn7d.setTextColor(showing30d ? Color.parseColor("#8896B0") : ContextCompat.getColor(requireContext(), R.color.primary));
        btn30d.setBackgroundResource(showing30d ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btn30d.setTextColor(showing30d ? ContextCompat.getColor(requireContext(), R.color.primary) : Color.parseColor("#8896B0"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 3: STRESS & SLEEP SPARKLINES
    // ═══════════════════════════════════════════════════════════════════

    private void renderSparklines(List<QuestionnaireResponse> responses) {
        if (responses == null || responses.isEmpty()) return;
        List<QuestionnaireResponse> recent = new ArrayList<>(responses);
        if (recent.size() > 7) recent = recent.subList(0, 7);
        Collections.reverse(recent);

        List<Float> stressData = new ArrayList<>();
        List<Float> sleepData = new ArrayList<>();
        float stressSum = 0, sleepSum = 0;
        int stressCount = 0, sleepCount = 0;

        for (QuestionnaireResponse r : recent) {
            if (r.stressLevel > 0) { stressData.add((float) r.stressLevel); stressSum += r.stressLevel; stressCount++; }
            if (r.sleepHours > 0) { sleepData.add(r.sleepHours); sleepSum += r.sleepHours; sleepCount++; }
        }

        sparklineStress.setData(stressData.isEmpty() ? null : stressData);
        sparklineSleep.setData(sleepData.isEmpty() ? null : sleepData);

        // Trend delta arrows: compare first half vs second half average
        String stressArrow = computeTrendArrow(stressData, true);  // higher stress = bad
        String sleepArrow = computeTrendArrow(sleepData, false);   // higher sleep = good

        tvStressAvg.setText(stressCount > 0
                ? String.format(Locale.US, "Avg: %.1f/5 %s", stressSum / stressCount, stressArrow)
                : "Avg: \u2014/5");
        tvSleepAvg.setText(sleepCount > 0
                ? String.format(Locale.US, "Avg: %.1fh %s", sleepSum / sleepCount, sleepArrow)
                : "Avg: \u2014h");
    }

    /**
     * Computes a trend arrow by comparing the first-half and second-half
     * averages of the data series. invertedIsGood=true means lower is better (stress).
     */
    private String computeTrendArrow(List<Float> data, boolean invertedIsGood) {
        if (data == null || data.size() < 4) return "";
        int mid = data.size() / 2;
        float firstAvg = 0, secondAvg = 0;
        for (int i = 0; i < mid; i++) firstAvg += data.get(i);
        firstAvg /= mid;
        for (int i = mid; i < data.size(); i++) secondAvg += data.get(i);
        secondAvg /= (data.size() - mid);
        float diff = secondAvg - firstAvg;
        if (Math.abs(diff) < 0.2f) return "\u2014"; // stable
        boolean improving = invertedIsGood ? diff < 0 : diff > 0;
        return improving ? "\u2191" : "\u2193";
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 4: GRATITUDE JOURNAL
    // ═══════════════════════════════════════════════════════════════════

    private void renderTodayJournal(JournalEntry today) {
        if (today != null && today.content != null && !today.content.trim().isEmpty()) {
            tvJournalTodayPreview.setText("\"" + today.content.trim() + "\"");
            tvJournalTodayPreview.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        } else {
            tvJournalTodayPreview.setText("No entry yet today. Take a moment to reflect.");
            tvJournalTodayPreview.setTextColor(Color.parseColor("#8896B0"));
        }
    }

    private void renderJournalHistory(List<JournalEntry> entries) {
        llJournalHistory.removeAllViews();
        if (entries == null || entries.isEmpty()) {
            tvJournalPrevHeader.setVisibility(View.GONE);
            return;
        }
        tvJournalPrevHeader.setVisibility(View.VISIBLE);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.US);
        int shown = 0;
        for (JournalEntry entry : entries) {
            if (shown >= 5) break;
            if (entry.content == null || entry.content.trim().isEmpty()) continue;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // Sentiment indicator dot
            View sentimentDot = new View(requireContext());
            sentimentDot.setLayoutParams(new LinearLayout.LayoutParams(dp(6), dp(6)));
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(getSentimentColor(entry.sentimentScore));
            sentimentDot.setBackground(dotBg);
            row.addView(sentimentDot);

            // Date
            TextView date = new TextView(requireContext());
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dateParams.setMarginStart(dp(8));
            date.setLayoutParams(dateParams);
            date.setText(sdf.format(new Date(entry.timestamp)));
            date.setTextColor(Color.parseColor("#8896B0"));
            date.setTextSize(12);
            date.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(date);

            // Preview text
            TextView preview = new TextView(requireContext());
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            previewParams.setMarginStart(dp(6));
            preview.setLayoutParams(previewParams);
            String text = entry.content.trim();
            preview.setText("\"" + (text.length() > 45 ? text.substring(0, 45) + "..." : text) + "\"");
            preview.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
            preview.setTextSize(13);
            preview.setMaxLines(1);
            preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(preview);

            // Entry type badge
            if (entry.entryType != null && !entry.entryType.isEmpty()) {
                TextView typeBadge = new TextView(requireContext());
                typeBadge.setText(getEntryTypeEmoji(entry.entryType));
                typeBadge.setTextSize(12);
                typeBadge.setPadding(dp(4), 0, 0, 0);
                row.addView(typeBadge);
            }

            // Click to open full entry
            final int entryId = entry.id;
            row.setOnClickListener(x -> {
                Intent intent = new Intent(requireContext(), JournalActivity.class);
                intent.putExtra("entry_id", entryId);
                startActivity(intent);
            });

            // Stagger fade-in
            row.setAlpha(0f);
            row.animate().alpha(1f).setStartDelay(shown * 60L).setDuration(250).start();

            llJournalHistory.addView(row);

            // Divider
            if (shown < 4) {
                View div = new View(requireContext());
                div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
                div.setBackgroundColor(Color.parseColor("#1E2A42"));
                llJournalHistory.addView(div);
            }
            shown++;
        }

        // #4: "Show all" expandable link (blueprint spec)
        if (entries.size() > 5) {
            TextView showAll = new TextView(requireContext());
            showAll.setText("Show all entries \u2192");
            showAll.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
            showAll.setTextSize(13);
            showAll.setPadding(0, dp(10), 0, dp(4));
            showAll.setOnClickListener(x -> startActivity(new Intent(requireContext(), JournalActivity.class)));
            llJournalHistory.addView(showAll);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SECTION 5+6: CALENDAR + HISTORY
    // ═══════════════════════════════════════════════════════════════════

    private void renderCalendar(List<QuestionnaireResponse> responses) {
        if (moodCalendar == null) return;
        if (responses == null || responses.isEmpty()) { moodCalendar.setMoodData(new HashMap<>()); return; }
        Map<Long, String> data = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        for (QuestionnaireResponse r : responses) {
            long dayTs = r.dayTimestamp;
            if (dayTs == 0) {
                cal.setTimeInMillis(r.timestamp);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                dayTs = cal.getTimeInMillis();
            }
            if (!data.containsKey(dayTs) && r.mood != null) data.put(dayTs, r.mood);
        }
        moodCalendar.setMoodData(data);
    }

    private void renderHistory(List<QuestionnaireResponse> responses) {
        historyAdapter.setResponses(responses);
        boolean empty = responses == null || responses.isEmpty();
        if (empty) {
            EmptyStateHelper.show(containerEmpty, "\uD83D\uDCDD", "No check-ins yet",
                    "Complete your first wellbeing check-in to unlock mood trends.", null, null);
        } else {
            EmptyStateHelper.hide(containerEmpty);
        }
    }

    private void checkWeeklyAssessmentDue() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AssessmentRepository repo = new AssessmentRepository(requireContext());
                boolean isDue = repo.isWeeklyAssessmentDue();
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    if (cardWeeklyAssessment != null)
                        cardWeeklyAssessment.setVisibility(isDue ? View.VISIBLE : View.GONE);
                });
            } catch (Exception ignored) {}
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private String getMoodEmoji(String mood) {
        if (mood == null) return "\uD83D\uDE10";
        switch (mood) {
            case "Happy": return "\uD83D\uDE0A";
            case "Calm": return "\uD83D\uDE0C";
            case "Neutral": return "\uD83D\uDE10";
            case "Anxious": return "\uD83D\uDE30";
            case "Sad": return "\uD83D\uDE22";
            case "Angry": return "\uD83D\uDE20";
            case "Numb": return "\uD83D\uDE36";
            default: return "\uD83D\uDE10";
        }
    }

    private int getMoodGlowColor(String mood) {
        if (mood == null) return Color.parseColor("#F5A623");
        switch (mood) {
            case "Happy": case "Calm": return Color.parseColor("#4ADE80");
            case "Neutral": return Color.parseColor("#F5A623");
            case "Sad": return Color.parseColor("#7C8FFF");
            case "Anxious": case "Angry": return Color.parseColor("#FF6B6B");
            default: return Color.parseColor("#F5A623");
        }
    }

    private int getMoodDotColor(String mood) {
        if (mood == null) return Color.parseColor("#1E2A42");
        switch (mood) {
            case "Happy": case "Calm": return Color.parseColor("#4ADE80");
            case "Neutral": return Color.parseColor("#F5A623");
            case "Sad": return Color.parseColor("#7C8FFF");
            case "Anxious": case "Angry": return Color.parseColor("#FF6B6B");
            default: return Color.parseColor("#1E2A42");
        }
    }

    private String energyLabel(int level) {
        if (level >= 4) return "High";
        if (level >= 3) return "Medium";
        return "Low";
    }

    /** Ranks mood on a 1-5 scale for delta comparison. */
    private int moodRank(String mood) {
        if (mood == null) return 3;
        switch (mood) {
            case "Happy": return 5;
            case "Calm": return 4;
            case "Neutral": return 3;
            case "Anxious": return 2;
            case "Sad": case "Numb": return 1;
            case "Angry": return 2;
            default: return 3;
        }
    }

    /** Maps NLP sentiment score to a color: green (positive) → red (negative). */
    private int getSentimentColor(float score) {
        if (score > 0.3f) return Color.parseColor("#4ADE80");
        if (score < -0.3f) return Color.parseColor("#FF6B6B");
        if (score != 0f) return Color.parseColor("#F5A623");
        return Color.parseColor("#3A4766"); // unanalyzed
    }

    /** Maps journal entry type to a compact emoji badge. */
    private String getEntryTypeEmoji(String type) {
        if (type == null) return "";
        switch (type) {
            case "gratitude": return "\uD83D\uDE4F";
            case "reflection": return "\uD83D\uDCDD";
            case "crisis": return "\u26A0\uFE0F";
            case "goal": return "\uD83C\uDFAF";
            default: return "\uD83D\uDCDD";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PHASE A ENHANCEMENTS
    // ═══════════════════════════════════════════════════════════════════

    /** #2: Animated counter roll-up from 0 → target over 800ms. */
    private void animateCounter(TextView tv, int target) {
        if (target <= 0) { tv.setText("0"); return; }
        ValueAnimator animator = ValueAnimator.ofInt(0, target);
        animator.setDuration(800);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(a -> tv.setText(String.valueOf((int) a.getAnimatedValue())));
        animator.start();
    }

    /** #3: Mood-based affirmation — non-punitive positive reinforcement. */
    private void renderAffirmation(String mood) {
        String affirmation;
        switch (mood != null ? mood : "Neutral") {
            case "Happy": affirmation = "You're doing great \u2014 keep nurturing this energy \uD83C\uDF1F"; break;
            case "Calm": affirmation = "Peace suits you. Savor this feeling \uD83C\uDF3F"; break;
            case "Sad": affirmation = "It's okay to feel this way. You showed up, and that matters \uD83D\uDC9B"; break;
            case "Anxious": affirmation = "Take a slow breath. You're safe right now \uD83C\uDF0A"; break;
            case "Angry": affirmation = "Your feelings are valid. Breathe and let the wave pass \uD83D\uDD25"; break;
            case "Numb": affirmation = "You're here, and that counts. Small steps forward \uD83D\uDC9C"; break;
            default: affirmation = "Welcome. Check in with yourself \u2014 you deserve that space \u2728"; break;
        }
        tvQuickContext.append("\n" + affirmation);
    }

    /** #8: Daily rotating journal prompts — reduces journal fatigue. */
    private void setRotatingJournalPrompt(View v) {
        String[] prompts = {
            "\u270F\uFE0F  What made you smile today?",
            "\u270F\uFE0F  Who are you grateful for?",
            "\u270F\uFE0F  What small win did you have?",
            "\u270F\uFE0F  Write about a kind moment",
            "\u270F\uFE0F  Reflect on your week so far",
            "\u270F\uFE0F  What brought you peace today?",
            "\u270F\uFE0F  Write today's gratitude"
        };
        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        TextView btn = v.findViewById(R.id.btn_write_journal);
        if (btn != null) btn.setText(prompts[dayOfWeek % prompts.length]);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PHASE B ENHANCEMENTS
    // ═══════════════════════════════════════════════════════════════════

    /** #1: Mood Distribution Donut — shows mood breakdown with percentages. */
    private void renderMoodDonut(List<QuestionnaireResponse> responses) {
        if (pieMoodDistribution == null || cardMoodDonut == null) return;
        if (responses == null || responses.size() < 3) {
            cardMoodDonut.setVisibility(View.GONE);
            return;
        }

        // Count mood occurrences
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (QuestionnaireResponse r : responses) {
            if (r.mood != null) counts.merge(r.mood, 1, Integer::sum);
        }
        if (counts.isEmpty()) { cardMoodDonut.setVisibility(View.GONE); return; }

        int total = 0;
        for (int c : counts.values()) total += c;

        // Build PieEntries
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            float pct = (float) e.getValue() / total * 100f;
            entries.add(new PieEntry(pct, getMoodEmoji(e.getKey())));
            colors.add(getMoodDotColor(e.getKey()));
        }

        PieDataSet ds = new PieDataSet(entries, "");
        ds.setColors(colors);
        ds.setSliceSpace(2f);
        ds.setDrawValues(false);
        ds.setSelectionShift(4f);

        pieMoodDistribution.setData(new PieData(ds));
        pieMoodDistribution.setUsePercentValues(true);
        pieMoodDistribution.setDrawHoleEnabled(true);
        pieMoodDistribution.setHoleColor(Color.parseColor("#131D30"));
        pieMoodDistribution.setHoleRadius(55f);
        pieMoodDistribution.setTransparentCircleRadius(60f);
        pieMoodDistribution.setTransparentCircleColor(Color.parseColor("#131D30"));
        pieMoodDistribution.setTransparentCircleAlpha(80);
        pieMoodDistribution.setDrawEntryLabels(true);
        pieMoodDistribution.setEntryLabelColor(Color.WHITE);
        pieMoodDistribution.setEntryLabelTextSize(14f);
        pieMoodDistribution.getDescription().setEnabled(false);
        pieMoodDistribution.getLegend().setEnabled(false);
        pieMoodDistribution.setRotationEnabled(false);
        pieMoodDistribution.animateY(800);
        pieMoodDistribution.invalidate();

        // Custom legend
        if (llDonutLegend != null) {
            llDonutLegend.removeAllViews();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                float pct = (float) e.getValue() / total * 100f;
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(3), 0, dp(3));

                View colorDot = new View(requireContext());
                colorDot.setLayoutParams(new LinearLayout.LayoutParams(dp(8), dp(8)));
                GradientDrawable dotShape = new GradientDrawable();
                dotShape.setShape(GradientDrawable.OVAL);
                dotShape.setColor(getMoodDotColor(e.getKey()));
                colorDot.setBackground(dotShape);
                row.addView(colorDot);

                TextView label = new TextView(requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart(dp(8));
                label.setLayoutParams(lp);
                label.setText(String.format(Locale.US, "%s %s  %.0f%%",
                        getMoodEmoji(e.getKey()), e.getKey(), pct));
                label.setTextSize(12);
                label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
                row.addView(label);

                llDonutLegend.addView(row);
            }
        }

        cardMoodDonut.setVisibility(View.VISIBLE);
        UiMotion.animateCardEntry(cardMoodDonut, 2);
    }

    /** #11: Parallax scroll — hero emoji shrinks + glow fades as user scrolls down. */
    private void setupParallaxScroll(View root) {
        androidx.core.widget.NestedScrollView scrollView =
                (androidx.core.widget.NestedScrollView) root;
        View heroCard = root.findViewById(R.id.card_mood_hero);
        if (heroCard == null) return;

        scrollView.setOnScrollChangeListener(
                (androidx.core.widget.NestedScrollView sv, int scrollX, int scrollY, int oldX, int oldY) -> {
                    float maxScroll = dp(200);
                    float ratio = Math.min(1f, scrollY / maxScroll);

                    // Emoji scales from 1.0 down to 0.85
                    float scale = 1f - (ratio * 0.15f);
                    tvMoodEmoji.setScaleX(scale);
                    tvMoodEmoji.setScaleY(scale);

                    // Glow fades from 1.0 to 0.2
                    viewMoodGlow.setAlpha(1f - (ratio * 0.8f));

                    // Hero card subtle translationY parallax (moves slower than scroll)
                    heroCard.setTranslationY(scrollY * 0.15f);
                });
    }

    // ═══════════════════════════════════════════════════════════════════
    // PHASE C ENHANCEMENTS
    // ═══════════════════════════════════════════════════════════════════

    /** #7: Sparkline tap-to-expand — opens a dialog with full 30-day detail chart. */
    private void showSparklineDetail(String metric, String colorHex, boolean isStress) {
        if (cachedResponses == null || cachedResponses.isEmpty()) return;

        List<QuestionnaireResponse> data = new ArrayList<>(cachedResponses);
        if (data.size() > 30) data = data.subList(0, 30);
        Collections.reverse(data);

        List<Entry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            float val = isStress ? data.get(i).stressLevel : data.get(i).sleepHours;
            if (val > 0) {
                entries.add(new Entry(i, val));
                xLabels.add(UiFormatting.formatDayLabel(data.get(i).timestamp));
            }
        }
        if (entries.isEmpty()) return;

        // Build chart in a dialog
        LineChart chart = new LineChart(requireContext());
        chart.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));
        chart.setBackgroundColor(Color.parseColor("#131D30"));

        LineDataSet ds = new LineDataSet(entries, metric);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setColor(Color.parseColor(colorHex));
        ds.setCircleColor(Color.parseColor(colorHex));
        ds.setCircleHoleColor(Color.parseColor("#131D30"));
        ds.setCircleRadius(3f);
        ds.setCircleHoleRadius(1.5f);
        ds.setLineWidth(2.5f);
        ds.setDrawValues(false);
        ds.setDrawFilled(true);
        ds.setFillColor(Color.parseColor(colorHex));
        ds.setFillAlpha(40);

        chart.setData(new LineData(ds));
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setExtraOffsets(12f, 12f, 12f, 12f);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#8896B0"));
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));

        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.parseColor("#8896B0"));
        left.setDrawGridLines(false);
        left.setGranularity(1f);
        chart.getAxisRight().setEnabled(false);
        chart.animateXY(800, 500);

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.Theme_MindTraceDialog)
                .setView(chart)
                .setPositiveButton("Close", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }
        dialog.show();
    }

    /** #12: Multi-insight carousel — horizontally scrollable insights with dots. */
    private void renderInsightCarousel(List<String> insights) {
        if (tvCorrelationInsight == null) return;

        if (insights.size() == 1) {
            // Single insight — no carousel needed
            tvCorrelationInsight.setText(insights.get(0));
            return;
        }

        // Replace static TextView with carousel
        ViewGroup parent = (ViewGroup) tvCorrelationInsight.getParent();
        if (parent == null) return;

        int tvIndex = parent.indexOfChild(tvCorrelationInsight);
        tvCorrelationInsight.setVisibility(View.GONE);

        // Check if carousel already added (prevent duplicates on re-observe)
        View existing = parent.findViewWithTag("insight_carousel");
        if (existing != null) parent.removeView(existing);
        View existingDots = parent.findViewWithTag("insight_dots");
        if (existingDots != null) parent.removeView(existingDots);

        // Build HorizontalScrollView with insight pages
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(requireContext());
        hsv.setTag("insight_carousel");
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout strip = new LinearLayout(requireContext());
        strip.setOrientation(LinearLayout.HORIZONTAL);
        int cardWidth = (int) (getResources().getDisplayMetrics().widthPixels - dp(80));

        for (String insight : insights) {
            TextView tv = new TextView(requireContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setText("\uD83D\uDCA1 " + insight);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
            tv.setTextSize(14);
            tv.setLineSpacing(dp(2), 1f);
            tv.setPadding(0, dp(4), dp(16), dp(4));
            strip.addView(tv);
        }
        hsv.addView(strip);
        parent.addView(hsv, tvIndex + 1);

        // Page indicator dots
        LinearLayout dotsLayout = new LinearLayout(requireContext());
        dotsLayout.setTag("insight_dots");
        dotsLayout.setOrientation(LinearLayout.HORIZONTAL);
        dotsLayout.setGravity(android.view.Gravity.CENTER);
        dotsLayout.setPadding(0, dp(8), 0, 0);
        dotsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View[] dots = new View[insights.size()];
        for (int i = 0; i < insights.size(); i++) {
            dots[i] = new View(requireContext());
            LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(6), dp(6));
            if (i > 0) dp2.setMarginStart(dp(6));
            dots[i].setLayoutParams(dp2);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(i == 0 ? Color.parseColor("#7C8FFF") : Color.parseColor("#1E2A42"));
            dots[i].setBackground(bg);
            dotsLayout.addView(dots[i]);
        }
        parent.addView(dotsLayout, tvIndex + 2);

        // Update dots on scroll
        hsv.setOnScrollChangeListener((View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) -> {
            int page = Math.round((float) scrollX / cardWidth);
            page = Math.max(0, Math.min(page, insights.size() - 1));
            for (int i = 0; i < dots.length; i++) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(i == page ? Color.parseColor("#7C8FFF") : Color.parseColor("#1E2A42"));
                dots[i].setBackground(bg);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
