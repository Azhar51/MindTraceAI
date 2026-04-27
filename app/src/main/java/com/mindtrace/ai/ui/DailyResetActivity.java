package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ui.model.DailyResetState;
import com.mindtrace.ai.viewmodel.DailyResetViewModel;

import java.util.ArrayList;
import java.util.Locale;

public class DailyResetActivity extends AppCompatActivity {
    public static final String EXTRA_MISSION_TITLE = "extra_mission_title";
    public static final String EXTRA_MISSION_STEPS = "extra_mission_steps";
    public static final String EXTRA_WARNING_ITEMS = "extra_warning_items";
    public static final String EXTRA_NEXT_ACTION_TITLE = "extra_next_action_title";
    public static final String EXTRA_RISK_INDEX = "extra_risk_index";
    public static final String EXTRA_HIGH_RISK = "extra_high_risk";

    private DailyResetViewModel viewModel;

    private MaterialCardView cardFocus;
    private MaterialCardView cardAction;
    private MaterialCardView cardTimer;
    private MaterialCardView cardWarning;
    private TextView tvResetTitle;
    private TextView tvResetSubtitle;
    private TextView tvFocusTask;
    private TextView tvActionText;
    private TextView tvTimer;
    private TextView tvTimerMeta;
    private TextView tvWarning;
    private TextView tvCompletion;
    private MaterialButton btnTimerToggle;
    private MaterialButton btnSkipLater;
    private MaterialButton btnComplete;
    private ChipGroup chipReadiness;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_reset);

        viewModel = new ViewModelProvider(this).get(DailyResetViewModel.class);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_daily_reset);
        cardFocus = findViewById(R.id.card_daily_reset_focus);
        cardAction = findViewById(R.id.card_daily_reset_action);
        cardTimer = findViewById(R.id.card_daily_reset_timer);
        cardWarning = findViewById(R.id.card_daily_reset_warning);
        tvResetTitle = findViewById(R.id.tv_daily_reset_title);
        tvResetSubtitle = findViewById(R.id.tv_daily_reset_subtitle);
        tvFocusTask = findViewById(R.id.tv_daily_reset_focus_task);
        tvActionText = findViewById(R.id.tv_daily_reset_action_text);
        tvTimer = findViewById(R.id.tv_daily_reset_timer);
        tvTimerMeta = findViewById(R.id.tv_daily_reset_timer_meta);
        tvWarning = findViewById(R.id.tv_daily_reset_warning);
        tvCompletion = findViewById(R.id.tv_daily_reset_completion);
        btnTimerToggle = findViewById(R.id.btn_daily_reset_timer);
        btnSkipLater = findViewById(R.id.btn_daily_reset_skip);
        btnComplete = findViewById(R.id.btn_daily_reset_complete);
        chipReadiness = findViewById(R.id.group_daily_reset_readiness);

        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        UiMotion.animateCardEntry(cardFocus, 0);
        UiMotion.animateCardEntry(cardAction, 1);
        UiMotion.animateCardEntry(cardTimer, 2);
        UiMotion.animateCardEntry(cardWarning, 3);
        UiMotion.attachPressAnimation(btnTimerToggle);
        UiMotion.attachPressAnimation(btnSkipLater);
        UiMotion.attachPressAnimation(btnComplete);

        btnTimerToggle.setOnClickListener(v -> viewModel.toggleTimer());
        btnSkipLater.setOnClickListener(v -> finish());
        btnComplete.setOnClickListener(v -> handleComplete());

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
        if (state == null) {
            return;
        }

        tvResetTitle.setText(state.resetTitle);
        tvResetSubtitle.setText(state.supportingText);
        tvFocusTask.setText(state.focusTask);
        tvActionText.setText(state.firstAction);
        tvWarning.setText(state.warningItem);
        tvCompletion.setVisibility(state.completionMessage == null || state.completionMessage.isEmpty() ? View.GONE : View.VISIBLE);
        tvCompletion.setText(state.completionMessage);
        tvTimer.setText(formatDuration(state.timeRemaining));
        tvTimerMeta.setText(state.isCompleted
                ? "Today's reset is locked in."
                : String.format(Locale.getDefault(), "%d-minute reset session", state.timerDurationMinutes));
        btnTimerToggle.setText(state.timerButtonLabel);
        btnTimerToggle.setEnabled(!state.isCompleted && !state.isLoading);
        btnComplete.setText(state.completionButtonLabel);
        btnComplete.setEnabled(!state.isLoading);
        chipReadiness.setEnabled(!state.isCompleted && !state.isLoading);
        for (int i = 0; i < chipReadiness.getChildCount(); i++) {
            chipReadiness.getChildAt(i).setEnabled(!state.isCompleted && !state.isLoading);
        }
    }

    private void handleComplete() {
        DailyResetState current = viewModel.getState().getValue();
        if (current == null) {
            return;
        }
        if (current.isCompleted) {
            viewModel.finishCompletedState();
            return;
        }

        Chip selectedChip = findViewById(chipReadiness.getCheckedChipId());
        String reflection = selectedChip == null ? null : selectedChip.getText().toString();
        int readinessLevel = mapReadinessLevel(chipReadiness.getCheckedChipId());
        btnComplete.setEnabled(false);
        btnComplete.setText("Saving...");
        viewModel.markResetComplete(reflection, readinessLevel);
    }

    private int mapReadinessLevel(int chipId) {
        if (chipId == R.id.chip_daily_reset_ready_high) {
            return 5;
        }
        if (chipId == R.id.chip_daily_reset_ready_mid) {
            return 3;
        }
        if (chipId == R.id.chip_daily_reset_ready_low) {
            return 2;
        }
        return 0;
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
