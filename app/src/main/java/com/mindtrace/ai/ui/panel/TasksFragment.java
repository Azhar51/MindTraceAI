package com.mindtrace.ai.ui.panel;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.UserProgress;
import com.mindtrace.ai.databinding.FragmentTasksBinding;
import com.mindtrace.ai.ui.TaskAdapter;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.ui.components.EmptyStateHelper;
import com.mindtrace.ai.viewmodel.DashboardViewModel;
import com.mindtrace.ai.viewmodel.TaskViewModel;

/**
 * Premium task management fragment with:
 * - Streak + XP/Level header
 * - Animated fulfilment ring (custom gradient arc)
 * - Category filter chips
 * - Weekly stats (completed, rate, best type) with animated counters
 * - Active task cards with skip/complete + confetti celebration
 * - Completed task history
 * - Haptic feedback on all primary actions
 * - Staggered card entry animations
 *
 * <p>Phase 5: Migrated to ViewBinding — all {@code findViewById} calls replaced
 * with generated {@link FragmentTasksBinding} for compile-time type safety.</p>
 */
public class TasksFragment extends Fragment {

    private FragmentTasksBinding binding;
    private TaskAdapter activeTaskAdapter;
    private TaskAdapter completedTaskAdapter;
    private TaskViewModel taskViewModel;
    private DashboardViewModel dashboardViewModel;

    public TasksFragment() {
        super(R.layout.fragment_tasks);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Phase 5: ViewBinding ──
        binding = FragmentTasksBinding.bind(view);

        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        // Active tasks RecyclerView
        activeTaskAdapter = new TaskAdapter();
        binding.rvActiveTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvActiveTasks.setAdapter(activeTaskAdapter);
        binding.rvActiveTasks.setNestedScrollingEnabled(false);

        // Completed tasks RecyclerView
        completedTaskAdapter = new TaskAdapter();
        binding.rvCompletedTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCompletedTasks.setAdapter(completedTaskAdapter);
        binding.rvCompletedTasks.setNestedScrollingEnabled(false);

        // Category filter chips
        setupCategoryChips();

        // Observe data
        observeData();

        // Load analytics
        loadAnalytics();

        // ── Premium entry animations ──
        animateFragmentEntry(view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leaks
    }

    /**
     * Staggered entry animation for the entire fragment.
     */
    private void animateFragmentEntry(@NonNull View root) {
        // Animate stat boxes with stagger
        View statsRow = binding.progressXp;
        if (statsRow != null && statsRow.getParent() instanceof android.view.ViewGroup) {
            // Animate key sections
            UiMotion.animateCardEntry(binding.progressFulfilmentRing != null
                    ? (View) binding.progressFulfilmentRing.getParent().getParent()
                    : null, 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORY CHIPS
    // ═══════════════════════════════════════════════════════════════════

    private void setupCategoryChips() {
        binding.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty() || checkedIds.contains(R.id.chip_all)) {
                taskViewModel.setCategoryFilter(null);
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chip_mindfulness) taskViewModel.setCategoryFilter("Mindfulness");
                else if (id == R.id.chip_journaling) taskViewModel.setCategoryFilter("Journaling");
                else if (id == R.id.chip_social) taskViewModel.setCategoryFilter("Social");
                else if (id == R.id.chip_purpose) taskViewModel.setCategoryFilter("Purpose");
                else if (id == R.id.chip_detox) taskViewModel.setCategoryFilter("Detox");
                else if (id == R.id.chip_recovery) taskViewModel.setCategoryFilter("Recovery");
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // OBSERVERS
    // ═══════════════════════════════════════════════════════════════════

    private void observeData() {
        // Active tasks
        taskViewModel.getActiveTasks().observe(getViewLifecycleOwner(), tasks -> {
            activeTaskAdapter.setTasks(tasks, new TaskAdapter.OnTaskActionListener() {
                @Override
                public void onCompleteClick(com.mindtrace.ai.database.entity.InterventionTask task) {
                    taskViewModel.markTaskCompleted(task);

                    // ── Premium celebration ──
                    UiMotion.hapticHeavy(requireView());
                    Toast.makeText(requireContext(), "✨ +" + task.getEffectiveXp() + " XP — Great work!", Toast.LENGTH_SHORT).show();

                    // Confetti burst from the RecyclerView
                    if (binding != null && binding.rvActiveTasks != null) {
                        UiMotion.confettiBurst(binding.rvActiveTasks, 16);
                    }
                }

                @Override
                public void onSkipClick(com.mindtrace.ai.database.entity.InterventionTask task) {
                    taskViewModel.skipTask(task);
                    UiMotion.hapticClick(requireView());
                    Toast.makeText(requireContext(), "Task skipped", Toast.LENGTH_SHORT).show();
                }
            });

            boolean isEmpty = tasks == null || tasks.isEmpty();
            if (isEmpty) {
                EmptyStateHelper.show(
                        binding.layoutEmpty,
                        "🌱",
                        "No active interventions",
                        "Complete a check-in to generate personalized tasks.",
                        null,
                        null
                );
            } else {
                EmptyStateHelper.hide(binding.layoutEmpty);
            }
            binding.tvActiveCount.setText((tasks != null ? tasks.size() : 0) + " active");
        });

        // Completed tasks
        taskViewModel.getCompletedTasks().observe(getViewLifecycleOwner(), tasks -> {
            completedTaskAdapter.setTasks(tasks, (TaskAdapter.OnTaskClickListener) null);
            boolean isEmpty = tasks == null || tasks.isEmpty();
            if (isEmpty) {
                EmptyStateHelper.show(
                        binding.tvTasksCompletedEmpty,
                        "🏆",
                        "No completed tasks",
                        "Completed interventions will appear here.",
                        null,
                        null
                );
            } else {
                EmptyStateHelper.hide(binding.tvTasksCompletedEmpty);
            }
        });

        // User progress (XP, streaks, level)
        taskViewModel.getUserProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress != null) {
                updateProgressUI(progress);
            }
        });

        // Dashboard insights — update fulfilment ring (animated)
        dashboardViewModel.getDashboardInsights().observe(getViewLifecycleOwner(), insights -> {
            if (insights != null) {
                int score = insights.fulfillmentScore;
                binding.tvTasksScore.setText("Fulfillment score: " + score + "%");

                // Prefer custom animated ring
                if (binding.fulfilmentRingView != null) {
                    binding.fulfilmentRingView.setPercent(score, true);
                } else if (binding.progressFulfilmentRing != null) {
                    // Animated ProgressBar fallback
                    ValueAnimator ringAnim = ValueAnimator.ofInt(
                            binding.progressFulfilmentRing.getProgress(), score);
                    ringAnim.setDuration(1000);
                    ringAnim.setInterpolator(
                            new android.view.animation.DecelerateInterpolator(1.5f));
                    ringAnim.addUpdateListener(a ->
                            binding.progressFulfilmentRing.setProgress((int) a.getAnimatedValue()));
                    ringAnim.start();
                }

                // Animated percentage text
                if (binding.tvFulfilmentPercent != null) {
                    animatePercentText(score);
                }
            }
        });

        // Active task count for summary line
        taskViewModel.getActiveTasks().observe(getViewLifecycleOwner(), tasks -> {
            if (binding != null && binding.tvFulfilmentSummary != null && tasks != null) {
                long completed = tasks.stream().filter(t -> "COMPLETED".equals(t.status)).count();
                binding.tvFulfilmentSummary.setText(completed + " of " + tasks.size() + " tasks completed today");
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // GAMIFICATION UI
    // ═══════════════════════════════════════════════════════════════════

    private void updateProgressUI(@NonNull UserProgress progress) {
        if (binding == null) return;

        // Streak
        if (progress.currentStreak > 0 && progress.isStreakActive()) {
            binding.tvStreak.setText("🔥 " + progress.currentStreak);
        } else {
            binding.tvStreak.setText("🔥 0");

            // Streak recovery dialog — show once when streak breaks
            if (progress.longestStreak > 2 && progress.currentStreak == 0) {
                showStreakRecoveryDialog(progress.longestStreak);
            }
        }

        // Level + XP
        binding.tvLevel.setText("Lv " + progress.getLevel() + " • " + progress.totalXp + " XP");

        // XP progress bar — animated fill
        int targetXp = (int) (progress.getLevelProgress() * 100);
        ValueAnimator xpAnim = ValueAnimator.ofInt(binding.progressXp.getProgress(), targetXp);
        xpAnim.setDuration(600);
        xpAnim.addUpdateListener(a -> {
            if (binding != null) binding.progressXp.setProgress((int) a.getAnimatedValue());
        });
        xpAnim.start();
    }

    /**
     * Animate percentage text counting up from 0 to target.
     */
    private void animatePercentText(int target) {
        ValueAnimator anim = ValueAnimator.ofInt(0, target);
        anim.setDuration(1000);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        anim.addUpdateListener(a -> {
            if (binding != null && binding.tvFulfilmentPercent != null) {
                binding.tvFulfilmentPercent.setText(a.getAnimatedValue() + "%");
            }
        });
        anim.start();
    }

    /**
     * Show a supportive dialog when the user's streak breaks.
     * Non-judgmental, focuses on starting fresh.
     */
    private void showStreakRecoveryDialog(int previousStreak) {
        if (getContext() == null) return;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("🌱 Fresh start")
                .setMessage("Your " + previousStreak + "-day streak ended — but that's completely okay.\n\n" +
                        "What matters is showing up, not being perfect. " +
                        "Complete one task today to start a new streak.")
                .setPositiveButton("Let's go", null)
                .setCancelable(true)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANALYTICS
    // ═══════════════════════════════════════════════════════════════════

    private void loadAnalytics() {
        // Completed count (7 days)
        taskViewModel.loadCompletionRate(7, rate -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding != null)
                        binding.tvStatRate.setText(String.format("%.0f%%", rate * 100));
                });
            }
        });

        // Weekly completed count
        taskViewModel.loadWeeklyReport(report -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding != null)
                        binding.tvStatCompleted.setText(String.valueOf(report.completed));
                });
            }
        });

        // Best category
        taskViewModel.loadMostEffectiveCategory(cat -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding != null) binding.tvStatBest.setText(cat);
                });
            }
        });

        // Smart timing insights
        loadTimingInsights();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SMART TASK TIMING INSIGHTS
    // ═══════════════════════════════════════════════════════════════════

    private void loadTimingInsights() {
        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            try {
                com.mindtrace.ai.ai.SmartTaskTimingEngine timingEngine =
                        new com.mindtrace.ai.ai.SmartTaskTimingEngine(requireContext());
                com.mindtrace.ai.ai.SmartTaskTimingEngine.TimingReport report = timingEngine.analyze();

                if (getActivity() != null && report.totalCompletions > 5 && !report.insights.isEmpty()) {
                    com.mindtrace.ai.util.AppExecutors.mainThread().execute(() -> {
                        if (!isAdded() || binding == null) return;
                        // Show top insight in the score line
                        String topInsight = report.insights.get(0);
                        binding.tvTasksScore.setText("💡 " + topInsight);
                    });
                }
            } catch (Exception e) {
                // Not enough data yet
            }
        });
    }
}
