package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.mindtrace.ai.R;
import com.mindtrace.ai.databinding.ActivityDailyResetBinding;
import com.mindtrace.ai.ui.model.DailyResetState;
import com.mindtrace.ai.viewmodel.DailyResetViewModel;

import java.util.Locale;

/**
 * Daily Reset Activity — guided end-of-day reset session.
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class DailyResetActivity extends AppCompatActivity {
    public static final String EXTRA_MISSION_TITLE = "extra_mission_title";
    public static final String EXTRA_MISSION_STEPS = "extra_mission_steps";
    public static final String EXTRA_WARNING_ITEMS = "extra_warning_items";
    public static final String EXTRA_NEXT_ACTION_TITLE = "extra_next_action_title";
    public static final String EXTRA_RISK_INDEX = "extra_risk_index";
    public static final String EXTRA_HIGH_RISK = "extra_high_risk";

    private ActivityDailyResetBinding binding;
    private DailyResetViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDailyResetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(DailyResetViewModel.class);

        setSupportActionBar(binding.toolbarDailyReset);
        binding.toolbarDailyReset.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        binding.toolbarDailyReset.setNavigationOnClickListener(v -> finish());

        UiMotion.animateCardEntry(binding.cardDailyResetFocus, 0);
        UiMotion.animateCardEntry(binding.cardDailyResetAction, 1);
        UiMotion.animateCardEntry(binding.cardDailyResetTimer, 2);
        UiMotion.animateCardEntry(binding.cardDailyResetWarning, 3);
        UiMotion.attachPressAnimation(binding.btnDailyResetTimer);
        UiMotion.attachPressAnimation(binding.btnDailyResetSkip);
        UiMotion.attachPressAnimation(binding.btnDailyResetComplete);

        binding.btnDailyResetTimer.setOnClickListener(v -> viewModel.toggleTimer());
        binding.btnDailyResetSkip.setOnClickListener(v -> finish());
        binding.btnDailyResetComplete.setOnClickListener(v -> handleComplete());

        viewModel.getState().observe(this, this::renderState);
        viewModel.getFinishSignal().observe(this, shouldFinish -> {
            if (Boolean.TRUE.equals(shouldFinish)) {
                setResult(RESULT_OK);
                finish();
            }
        });

        Intent intent = getIntent();
        viewModel.initialize(
                intent.getStringExtra(EXTRA_MISSION_TITLE),
                intent.getStringArrayListExtra(EXTRA_MISSION_STEPS),
                intent.getStringArrayListExtra(EXTRA_WARNING_ITEMS),
                intent.getStringExtra(EXTRA_NEXT_ACTION_TITLE),
                intent.getIntExtra(EXTRA_RISK_INDEX, 0),
                intent.getBooleanExtra(EXTRA_HIGH_RISK, false)
        );
    }

    private void renderState(DailyResetState state) {
        if (state == null) return;

        binding.tvDailyResetTitle.setText(state.resetTitle);
        binding.tvDailyResetSubtitle.setText(state.supportingText);
        binding.tvDailyResetFocusTask.setText(state.focusTask);
        binding.tvDailyResetActionText.setText(state.firstAction);
        binding.tvDailyResetWarning.setText(state.warningItem);
        binding.tvDailyResetCompletion.setVisibility(
                state.completionMessage == null || state.completionMessage.isEmpty()
                        ? View.GONE : View.VISIBLE);
        binding.tvDailyResetCompletion.setText(state.completionMessage);
        binding.tvDailyResetTimer.setText(formatDuration(state.timeRemaining));
        binding.tvDailyResetTimerMeta.setText(state.isCompleted
                ? "Today's reset is locked in."
                : String.format(Locale.getDefault(), "%d-minute reset session", state.timerDurationMinutes));
        binding.btnDailyResetTimer.setText(state.timerButtonLabel);
        binding.btnDailyResetTimer.setEnabled(!state.isCompleted && !state.isLoading);
        binding.btnDailyResetComplete.setText(state.completionButtonLabel);
        binding.btnDailyResetComplete.setEnabled(!state.isLoading);
        binding.groupDailyResetReadiness.setEnabled(!state.isCompleted && !state.isLoading);
        for (int i = 0; i < binding.groupDailyResetReadiness.getChildCount(); i++) {
            binding.groupDailyResetReadiness.getChildAt(i).setEnabled(!state.isCompleted && !state.isLoading);
        }
    }

    private void handleComplete() {
        DailyResetState current = viewModel.getState().getValue();
        if (current == null) return;
        if (current.isCompleted) {
            viewModel.finishCompletedState();
            return;
        }

        Chip selectedChip = findViewById(binding.groupDailyResetReadiness.getCheckedChipId());
        String reflection = selectedChip == null ? null : selectedChip.getText().toString();
        int readinessLevel = mapReadinessLevel(binding.groupDailyResetReadiness.getCheckedChipId());
        binding.btnDailyResetComplete.setEnabled(false);
        binding.btnDailyResetComplete.setText("Saving...");
        viewModel.markResetComplete(reflection, readinessLevel);
    }

    private int mapReadinessLevel(int chipId) {
        if (chipId == R.id.chip_daily_reset_ready_high) return 5;
        if (chipId == R.id.chip_daily_reset_ready_mid) return 3;
        if (chipId == R.id.chip_daily_reset_ready_low) return 2;
        return 0;
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
