package com.mindtrace.ai.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.mindtrace.ai.databinding.ActivityDailyReportBinding;
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
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class DailyReportActivity extends AppCompatActivity {

    private ActivityDailyReportBinding binding;
    private DailyReportViewModel vm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDailyReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
    // OBSERVERS — each section maps 1:1 to a ViewModel LiveData stream
    // ═══════════════════════════════════════════════════════════════════════

    private void observeHeader() {
        vm.getGreetingText().observe(this, binding.tvGreeting::setText);
        vm.getDateText().observe(this, binding.tvDate::setText);
    }

    private void observeUsage() {
        vm.getScreenTimeMillis().observe(this, ms ->
                binding.tvTotalUsageHeader.setText(DailyReportViewModel.formatDuration(ms)));
        vm.getUnlockCount().observe(this, count ->
                binding.tvTotalUnlocksHeader.setText(count + " times"));
        vm.getAppSwitchCount().observe(this, count ->
                binding.tvAppSwitches.setText(String.valueOf(count)));
        vm.getNightUsageMillis().observe(this, ms ->
                binding.tvNightUsage.setText(DailyReportViewModel.formatDuration(ms)));
    }

    private void observePattern() {
        vm.getUsagePatternLabel().observe(this, binding.tvPatternLabel::setText);
        vm.getUsagePatternDescription().observe(this, binding.tvPatternDescription::setText);
    }

    private void observeConsumption() {
        vm.getSocialMediaMillis().observe(this, ms -> {
            binding.tvSocialMediaTime.setText(DailyReportViewModel.formatDuration(ms));
            binding.pbSocial.setProgress(percentOfScreenTime(ms));
        });
        vm.getProductiveMillis().observe(this, ms -> {
            binding.tvProductiveTime.setText(DailyReportViewModel.formatDuration(ms));
            binding.pbProductive.setProgress(percentOfScreenTime(ms));
        });
        vm.getEntertainmentMillis().observe(this, ms -> {
            binding.tvEntertainmentTime.setText(DailyReportViewModel.formatDuration(ms));
            binding.pbEntertainment.setProgress(percentOfScreenTime(ms));
        });
        vm.getPassiveRatio().observe(this, ratio ->
                binding.tvPassiveRatio.setText(String.format(Locale.US, "%.0f%%", ratio * 100)));
    }

    private void observeTasks() {
        vm.getTasksCompletedToday().observe(this, completed -> {
            Integer total = vm.getTasksCreatedToday().getValue();
            int t = total != null ? total : 0;
            binding.tvTasksCompleted.setText(completed + " / " + t);
        });
        vm.getTasksCreatedToday().observe(this, total -> {
            Integer completed = vm.getTasksCompletedToday().getValue();
            int c = completed != null ? completed : 0;
            binding.tvTasksCompleted.setText(c + " / " + total);
        });
        vm.getTaskCompletionPercent().observe(this, binding.pbTaskCompletion::setProgress);
        vm.getOverallEffectiveness().observe(this, eff ->
                binding.tvEffectiveness.setText(String.format(Locale.US, "%.1f / 5", eff)));
        vm.getMostEffectiveCategory().observe(this, binding.tvBestCategory::setText);
        vm.getOverallEfficacy().observe(this, eff ->
                binding.tvEfficacy.setText(DailyReportViewModel.formatRiskPercent(eff)));
    }

    private void observeWellness() {
        vm.getLatestMood().observe(this, binding.tvMood::setText);
        vm.getAvgStress7().observe(this, s ->
                binding.tvStress.setText(String.format(Locale.US, "%.1f / 5", s)));
        vm.getAvgSleepHours7().observe(this, h ->
                binding.tvSleep.setText(String.format(Locale.US, "%.1f hrs", h)));
        vm.getAvgHope7().observe(this, h ->
                binding.tvHope.setText(String.format(Locale.US, "%.1f / 5", h)));
        vm.getAvgPurpose7().observe(this, p ->
                binding.tvPurpose.setText(String.format(Locale.US, "%.1f / 5", p)));
    }

    private void observeRisk() {
        vm.getOverallRiskScore().observe(this, score -> {
            binding.tvRiskScore.setText(DailyReportViewModel.formatRiskPercent(score));
            binding.pbRisk.setProgress(Math.round(score * 100));
        });
        vm.getPrimaryRiskCategory().observe(this, binding.tvRiskCategory::setText);
        vm.getCrisisFlag().observe(this, crisis ->
                binding.tvCrisisBadge.setVisibility(crisis ? View.VISIBLE : View.GONE));
    }

    private void observeBehavior() {
        vm.getBehaviorRiskScore().observe(this, score ->
                binding.tvBehaviorRisk.setText(DailyReportViewModel.formatRiskPercent(score)));
        vm.getFragmentationIndex().observe(this, idx ->
                binding.tvFragmentation.setText(String.format(Locale.US, "%.2f", idx)));
        vm.getHasLoopPattern().observe(this, loop ->
                binding.tvLoopPattern.setText(loop ? "Detected" : "None"));
    }

    private void observeErrors() {
        vm.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                binding.tvErrorMessage.setText(msg);
                binding.layoutError.setVisibility(View.VISIBLE);
            } else {
                binding.layoutError.setVisibility(View.GONE);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
