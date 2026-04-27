package com.mindtrace.ai.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.mindtrace.ai.R;
import com.mindtrace.ai.viewmodel.DailyReportViewModel;

import java.util.Locale;

/**
 * Daily Report Activity — renders a comprehensive daily intelligence
 * dashboard driven reactively by {@link DailyReportViewModel}.
 *
 * <p>Sections displayed:</p>
 * <ol>
 *   <li>Hero metrics (screen time, unlocks, app switches, night usage)</li>
 *   <li>Usage pattern classification (quadrant-based)</li>
 *   <li>Consumption breakdown (social, productive, entertainment, passive)</li>
 *   <li>Task efficacy (completion %, effectiveness, best category)</li>
 *   <li>Wellness signals (mood, stress, sleep, hope, purpose)</li>
 *   <li>Risk classification (overall score, primary category, crisis flag)</li>
 *   <li>Behavior snapshot (risk, fragmentation, loop pattern)</li>
 * </ol>
 */
public class DailyReportActivity extends AppCompatActivity {

    private DailyReportViewModel vm;

    // ── Hero ──
    private TextView tvGreeting, tvDate;
    private TextView tvScreenTime, tvUnlocks, tvAppSwitches, tvNightUsage;

    // ── Usage Pattern ──
    private TextView tvPatternLabel, tvPatternDescription;

    // ── Consumption ──
    private TextView tvSocialTime, tvProductiveTime, tvEntertainmentTime, tvPassiveRatio;
    private ProgressBar pbSocial, pbProductive, pbEntertainment;

    // ── Task Efficacy ──
    private TextView tvTasksCompleted, tvEffectiveness, tvBestCategory, tvEfficacy;
    private ProgressBar pbTaskCompletion;

    // ── Wellness ──
    private TextView tvMood, tvStress, tvSleep, tvHope, tvPurpose;

    // ── Risk ──
    private TextView tvRiskScore, tvRiskCategory, tvCrisisBadge;
    private ProgressBar pbRisk;

    // ── Behavior ──
    private TextView tvBehaviorRisk, tvFragmentation, tvLoopPattern;

    // ── Error ──
    private LinearLayout layoutError;
    private TextView tvErrorMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_report);

        bindViews();

        vm = new ViewModelProvider(this).get(DailyReportViewModel.class);

        observeHeader();
        observeUsage();
        observePattern();
        observeConsumption();
        observeTasks();
        observeWellness();
        observeRisk();
        observeBehavior();
        observeErrors();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VIEW BINDING
    // ═══════════════════════════════════════════════════════════════════════

    private void bindViews() {
        tvGreeting          = findViewById(R.id.tv_greeting);
        tvDate              = findViewById(R.id.tv_date);
        tvScreenTime        = findViewById(R.id.tv_total_usage_header);
        tvUnlocks           = findViewById(R.id.tv_total_unlocks_header);
        tvAppSwitches       = findViewById(R.id.tv_app_switches);
        tvNightUsage        = findViewById(R.id.tv_night_usage);
        tvPatternLabel      = findViewById(R.id.tv_pattern_label);
        tvPatternDescription = findViewById(R.id.tv_pattern_description);
        tvSocialTime        = findViewById(R.id.tv_social_media_time);
        tvProductiveTime    = findViewById(R.id.tv_productive_time);
        tvEntertainmentTime = findViewById(R.id.tv_entertainment_time);
        tvPassiveRatio      = findViewById(R.id.tv_passive_ratio);
        pbSocial            = findViewById(R.id.pb_social);
        pbProductive        = findViewById(R.id.pb_productive);
        pbEntertainment     = findViewById(R.id.pb_entertainment);
        tvTasksCompleted    = findViewById(R.id.tv_tasks_completed);
        pbTaskCompletion    = findViewById(R.id.pb_task_completion);
        tvEffectiveness     = findViewById(R.id.tv_effectiveness);
        tvBestCategory      = findViewById(R.id.tv_best_category);
        tvEfficacy          = findViewById(R.id.tv_efficacy);
        tvMood              = findViewById(R.id.tv_mood);
        tvStress            = findViewById(R.id.tv_stress);
        tvSleep             = findViewById(R.id.tv_sleep);
        tvHope              = findViewById(R.id.tv_hope);
        tvPurpose           = findViewById(R.id.tv_purpose);
        tvRiskScore         = findViewById(R.id.tv_risk_score);
        tvRiskCategory      = findViewById(R.id.tv_risk_category);
        tvCrisisBadge       = findViewById(R.id.tv_crisis_badge);
        pbRisk              = findViewById(R.id.pb_risk);
        tvBehaviorRisk      = findViewById(R.id.tv_behavior_risk);
        tvFragmentation     = findViewById(R.id.tv_fragmentation);
        tvLoopPattern       = findViewById(R.id.tv_loop_pattern);
        layoutError         = findViewById(R.id.layout_error);
        tvErrorMessage      = findViewById(R.id.tv_error_message);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OBSERVERS — each section maps 1:1 to a ViewModel LiveData stream
    // ═══════════════════════════════════════════════════════════════════════

    private void observeHeader() {
        vm.getGreetingText().observe(this, tvGreeting::setText);
        vm.getDateText().observe(this, tvDate::setText);
    }

    private void observeUsage() {
        vm.getScreenTimeMillis().observe(this, ms ->
                tvScreenTime.setText(DailyReportViewModel.formatDuration(ms)));
        vm.getUnlockCount().observe(this, count ->
                tvUnlocks.setText(count + " times"));
        vm.getAppSwitchCount().observe(this, count ->
                tvAppSwitches.setText(String.valueOf(count)));
        vm.getNightUsageMillis().observe(this, ms ->
                tvNightUsage.setText(DailyReportViewModel.formatDuration(ms)));
    }

    private void observePattern() {
        vm.getUsagePatternLabel().observe(this, tvPatternLabel::setText);
        vm.getUsagePatternDescription().observe(this, tvPatternDescription::setText);
    }

    private void observeConsumption() {
        vm.getSocialMediaMillis().observe(this, ms -> {
            tvSocialTime.setText(DailyReportViewModel.formatDuration(ms));
            pbSocial.setProgress(percentOfScreenTime(ms));
        });
        vm.getProductiveMillis().observe(this, ms -> {
            tvProductiveTime.setText(DailyReportViewModel.formatDuration(ms));
            pbProductive.setProgress(percentOfScreenTime(ms));
        });
        vm.getEntertainmentMillis().observe(this, ms -> {
            tvEntertainmentTime.setText(DailyReportViewModel.formatDuration(ms));
            pbEntertainment.setProgress(percentOfScreenTime(ms));
        });
        vm.getPassiveRatio().observe(this, ratio ->
                tvPassiveRatio.setText(String.format(Locale.US, "%.0f%%", ratio * 100)));
    }

    private void observeTasks() {
        vm.getTasksCompletedToday().observe(this, completed -> {
            Integer total = vm.getTasksCreatedToday().getValue();
            int t = total != null ? total : 0;
            tvTasksCompleted.setText(completed + " / " + t);
        });
        vm.getTasksCreatedToday().observe(this, total -> {
            Integer completed = vm.getTasksCompletedToday().getValue();
            int c = completed != null ? completed : 0;
            tvTasksCompleted.setText(c + " / " + total);
        });
        vm.getTaskCompletionPercent().observe(this, pbTaskCompletion::setProgress);
        vm.getOverallEffectiveness().observe(this, eff ->
                tvEffectiveness.setText(String.format(Locale.US, "%.1f / 5", eff)));
        vm.getMostEffectiveCategory().observe(this, tvBestCategory::setText);
        vm.getOverallEfficacy().observe(this, eff ->
                tvEfficacy.setText(DailyReportViewModel.formatRiskPercent(eff)));
    }

    private void observeWellness() {
        vm.getLatestMood().observe(this, tvMood::setText);
        vm.getAvgStress7().observe(this, s ->
                tvStress.setText(String.format(Locale.US, "%.1f / 5", s)));
        vm.getAvgSleepHours7().observe(this, h ->
                tvSleep.setText(String.format(Locale.US, "%.1f hrs", h)));
        vm.getAvgHope7().observe(this, h ->
                tvHope.setText(String.format(Locale.US, "%.1f / 5", h)));
        vm.getAvgPurpose7().observe(this, p ->
                tvPurpose.setText(String.format(Locale.US, "%.1f / 5", p)));
    }

    private void observeRisk() {
        vm.getOverallRiskScore().observe(this, score -> {
            tvRiskScore.setText(DailyReportViewModel.formatRiskPercent(score));
            pbRisk.setProgress(Math.round(score * 100));
        });
        vm.getPrimaryRiskCategory().observe(this, tvRiskCategory::setText);
        vm.getCrisisFlag().observe(this, crisis ->
                tvCrisisBadge.setVisibility(crisis ? View.VISIBLE : View.GONE));
    }

    private void observeBehavior() {
        vm.getBehaviorRiskScore().observe(this, score ->
                tvBehaviorRisk.setText(DailyReportViewModel.formatRiskPercent(score)));
        vm.getFragmentationIndex().observe(this, idx ->
                tvFragmentation.setText(String.format(Locale.US, "%.2f", idx)));
        vm.getHasLoopPattern().observe(this, loop ->
                tvLoopPattern.setText(loop ? "Detected" : "None"));
    }

    private void observeErrors() {
        vm.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                tvErrorMessage.setText(msg);
                layoutError.setVisibility(View.VISIBLE);
            } else {
                layoutError.setVisibility(View.GONE);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Compute the percentage a category takes relative to total screen time.
     * Returns 0–100 for the progress bar.
     */
    private int percentOfScreenTime(long categoryMillis) {
        Long total = vm.getScreenTimeMillis().getValue();
        if (total == null || total <= 0) return 0;
        return Math.min(100, Math.round((float) categoryMillis * 100f / total));
    }
}
