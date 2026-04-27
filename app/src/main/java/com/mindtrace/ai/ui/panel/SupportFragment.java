package com.mindtrace.ai.ui.panel;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.DashboardInsights;
import com.mindtrace.ai.ai.ExerciseEngine;
import com.mindtrace.ai.ui.CrisisActivity;
import com.mindtrace.ai.ui.DailyCheckInActivity;
import com.mindtrace.ai.ui.JournalActivity;
import com.mindtrace.ai.ui.BreathingExerciseActivity;
import com.mindtrace.ai.ui.GroundingExerciseActivity;
import com.mindtrace.ai.ui.SafetyPlanActivity;
import com.mindtrace.ai.ui.UiMotion;
import com.mindtrace.ai.viewmodel.CrisisViewModel;
import com.mindtrace.ai.viewmodel.DashboardViewModel;

import java.util.List;

/**
 * Premium support & safety fragment — integrates breathing/grounding exercises,
 * safety plan, crisis history, trusted contacts, and helpline references.
 */
public class SupportFragment extends Fragment {

    private DashboardViewModel dashboardViewModel;
    private CrisisViewModel crisisViewModel;

    // Hero
    private TextView tvSupportHeadline, tvSupportMessage, tvCrisisStatus;
    private MaterialButton btnGrounding, btnCheckIn, btnOpenFullSupport;

    // Exercise cards
    private MaterialCardView cardBreathing, cardGrounding;

    // Safety plan
    private MaterialCardView cardSafetyPlan;
    private TextView tvSafetyPercent, tvSafetyHint;
    private ProgressBar progressSafety;

    // Crisis history
    private TextView tvCrisisSummary, tvBestCoping;

    public SupportFragment() {
        super(R.layout.fragment_support);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dashboardViewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);
        crisisViewModel = new ViewModelProvider(requireActivity()).get(CrisisViewModel.class);

        bindViews(view);
        setupClickListeners();
        observeData();
        loadCrisisAnalytics();

        // ── Premium: staggered card entry ──
        animateFragmentEntry(view);

        // ── Premium: breathing pulse on card emoji ──
        startBreathingCardPulse(view);
    }

    /**
     * Subtle breathing pulse on the breathing card emoji to show it's "alive".
     */
    private void startBreathingCardPulse(@NonNull View root) {
        if (cardBreathing != null) {
            // Find the emoji TextView inside the card (first child of first LinearLayout)
            android.view.ViewGroup cardContent = (android.view.ViewGroup) cardBreathing.getChildAt(0);
            if (cardContent != null && cardContent.getChildCount() > 0) {
                View emoji = cardContent.getChildAt(0);
                UiMotion.startBreathingPulse(emoji, 1.08f);
            }
        }
    }

    /**
     * Stagger-animate all cards into view on fragment load.
     */
    private void animateFragmentEntry(@NonNull View root) {
        // Animate all direct child cards with stagger
        android.view.ViewGroup content = root.findViewById(R.id.tv_support_headline) != null
                ? (android.view.ViewGroup) root.findViewById(R.id.tv_support_headline).getParent().getParent().getParent()
                : null;

        if (content instanceof android.view.ViewGroup) {
            UiMotion.staggerChildren((android.view.ViewGroup) content, 80, true);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // VIEW BINDING
    // ═══════════════════════════════════════════════════════════════════

    private void bindViews(@NonNull View view) {
        tvSupportHeadline = view.findViewById(R.id.tv_support_headline);
        tvSupportMessage = view.findViewById(R.id.tv_support_message);
        tvCrisisStatus = view.findViewById(R.id.tv_crisis_status);
        btnGrounding = view.findViewById(R.id.btn_support_grounding);
        btnCheckIn = view.findViewById(R.id.btn_support_check_in);
        btnOpenFullSupport = view.findViewById(R.id.btn_support_open_fullscreen);

        cardBreathing = view.findViewById(R.id.card_breathing);
        cardGrounding = view.findViewById(R.id.card_grounding);

        cardSafetyPlan = view.findViewById(R.id.card_safety_plan);
        tvSafetyPercent = view.findViewById(R.id.tv_safety_percent);
        tvSafetyHint = view.findViewById(R.id.tv_safety_hint);
        progressSafety = view.findViewById(R.id.progress_safety);

        tvCrisisSummary = view.findViewById(R.id.tv_crisis_summary);
        tvBestCoping = view.findViewById(R.id.tv_best_coping);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLICK LISTENERS
    // ═══════════════════════════════════════════════════════════════════

    private void setupClickListeners() {
        // Quick breathing exercise → launches real animated activity
        btnGrounding.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            Intent intent = new Intent(requireContext(), BreathingExerciseActivity.class);
            intent.putExtra(BreathingExerciseActivity.EXTRA_EXERCISE_INDEX, 0);
            startActivity(intent);
        });

        // Check-in
        btnCheckIn.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            startActivity(new Intent(requireContext(), DailyCheckInActivity.class));
        });

        // Full crisis screen
        btnOpenFullSupport.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            startActivity(new Intent(requireContext(), CrisisActivity.class));
        });

        // Breathing exercises card → exercise picker dialog
        UiMotion.attachPressAnimation(cardBreathing);
        cardBreathing.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            List<ExerciseEngine.BreathingExercise> exercises = ExerciseEngine.getAllBreathingExercises();
            String[] names = new String[exercises.size()];
            for (int i = 0; i < exercises.size(); i++) {
                names[i] = exercises.get(i).name + " (" + exercises.get(i).getDurationMinutes() + " min)";
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Choose a breathing exercise")
                    .setItems(names, (dialog, which) -> {
                        Intent intent = new Intent(requireContext(), BreathingExerciseActivity.class);
                        intent.putExtra(BreathingExerciseActivity.EXTRA_EXERCISE_INDEX, which);
                        startActivity(intent);
                    })
                    .show();
        });

        // Grounding exercises card → exercise picker dialog
        UiMotion.attachPressAnimation(cardGrounding);
        cardGrounding.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            List<ExerciseEngine.GroundingExercise> exercises = ExerciseEngine.getAllGroundingExercises();
            String[] names = new String[exercises.size()];
            for (int i = 0; i < exercises.size(); i++) {
                names[i] = exercises.get(i).name + " (" + exercises.get(i).getStepCount() + " steps)";
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Choose a grounding exercise")
                    .setItems(names, (dialog, which) -> {
                        Intent intent = new Intent(requireContext(), GroundingExerciseActivity.class);
                        intent.putExtra(GroundingExerciseActivity.EXTRA_EXERCISE_INDEX, which);
                        startActivity(intent);
                    })
                    .show();
        });

        // Safety plan card → real editor
        UiMotion.attachPressAnimation(cardSafetyPlan);
        cardSafetyPlan.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            startActivity(new Intent(requireContext(), SafetyPlanActivity.class));
        });

        // Journal shortcut (safety mode) — triggered from crisis assessment
        btnCheckIn.setOnLongClickListener(v -> {
            Intent journalIntent = new Intent(requireContext(), JournalActivity.class);
            journalIntent.putExtra(JournalActivity.EXTRA_SAFETY_MODE, true);
            startActivity(journalIntent);
            return true;
        });

        // CTA pulse glow on primary action button
        UiMotion.pulseGlow(btnGrounding);
    }

    // ═══════════════════════════════════════════════════════════════════
    // OBSERVERS
    // ═══════════════════════════════════════════════════════════════════

    private void observeData() {
        // Dashboard insights → dynamic headline
        dashboardViewModel.getDashboardInsights().observe(getViewLifecycleOwner(), this::renderSupportState);

        // Crisis assessment → status indicator with pulsing badge
        crisisViewModel.getCurrentAssessment().observe(getViewLifecycleOwner(), assessment -> {
            if (assessment != null && assessment.level.requiresMonitoring()) {
                tvCrisisStatus.setVisibility(View.VISIBLE);
                tvCrisisStatus.setText("⚠️ " + assessment.level.label + " — " +
                        assessment.activeSignals.size() + " signals detected");
                tvCrisisStatus.setTextColor(requireContext().getColor(R.color.crisis_accent));

                // ── Pulsing red badge animation ──
                startCrisisPulse(tvCrisisStatus);
            } else {
                tvCrisisStatus.setVisibility(View.GONE);
                stopCrisisPulse(tvCrisisStatus);
            }
        });

        // Safety plan → progress (animated)
        crisisViewModel.getSafetyPlan().observe(getViewLifecycleOwner(), plan -> {
            if (plan != null && plan.hasContent()) {
                int percent = plan.getCompletionPercent();
                tvSafetyPercent.setText(percent + "%");

                // Animated progress bar fill
                ValueAnimator anim = ValueAnimator.ofInt(progressSafety.getProgress(), percent);
                anim.setDuration(600);
                anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
                anim.addUpdateListener(a -> progressSafety.setProgress((int) a.getAnimatedValue()));
                anim.start();

                if (percent == 100) {
                    tvSafetyHint.setText("Your safety plan is complete. Tap to review or update.");
                    // Celebrate completion
                    UiMotion.confettiBurst(cardSafetyPlan, 12);
                    UiMotion.hapticHeavy(cardSafetyPlan);
                } else {
                    tvSafetyHint.setText("Your safety plan is " + percent + "% complete. Tap to continue building it.");
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SUPPORT STATE RENDERING
    // ═══════════════════════════════════════════════════════════════════

    private void renderSupportState(DashboardInsights insights) {
        if (insights == null) {
            tvSupportHeadline.setText("Support space");
            tvSupportMessage.setText("This space stays available whenever you need a calm reset.");
            return;
        }

        if (insights.supportRecommended) {
            tvSupportHeadline.setText("Let's slow things down.");
            tvSupportMessage.setText("Your recent pattern looks heavier than usual. Take a pause, step away from the phone if you can, and reach out to someone you trust if you need support.");
        } else if (insights.riskLevel == DashboardInsights.RiskLevel.MODERATE) {
            tvSupportHeadline.setText("A steady reset can help.");
            tvSupportMessage.setText("Some overload signals are showing up. A short break, hydration, and one calm offline activity can help you regain balance.");
        } else {
            tvSupportHeadline.setText("Support is always available.");
            tvSupportMessage.setText("You seem relatively balanced right now, but this panel stays here for grounding, check-ins, and crisis references whenever you need them.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS ANALYTICS
    // ═══════════════════════════════════════════════════════════════════

    private void loadCrisisAnalytics() {
        crisisViewModel.loadWeeklySummary(summary -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> tvCrisisSummary.setText(summary));
            }
        });

        crisisViewModel.loadBestCopingStrategy(strategy -> {
            if (getActivity() != null && !"Not enough data yet".equals(strategy)) {
                getActivity().runOnUiThread(() -> {
                    tvBestCoping.setVisibility(View.VISIBLE);
                    tvBestCoping.setText("Best coping strategy: " + strategy);
                });
            }
        });

        // Exercise analytics
        loadExerciseInsights();
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXERCISE ANALYTICS INSIGHTS
    // ═══════════════════════════════════════════════════════════════════

    private void loadExerciseInsights() {
        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            try {
                com.mindtrace.ai.ai.ExerciseAnalyticsEngine analytics =
                        new com.mindtrace.ai.ai.ExerciseAnalyticsEngine(requireContext());
                List<String> cards = analytics.generateInsightCards();

                if (getActivity() != null && !cards.isEmpty()) {
                    com.mindtrace.ai.util.AppExecutors.mainThread().execute(() -> {
                        if (!isAdded()) return;
                        StringBuilder sb = new StringBuilder();
                        for (String card : cards) {
                            sb.append("• ").append(card).append("\n");
                        }
                        tvBestCoping.setVisibility(View.VISIBLE);
                        String existing = tvBestCoping.getText().toString();
                        if (!existing.isEmpty()) {
                            tvBestCoping.setText(existing + "\n\n📊 Exercise Insights:\n" + sb.toString().trim());
                        } else {
                            tvBestCoping.setText("📊 Exercise Insights:\n" + sb.toString().trim());
                        }
                    });
                }
            } catch (Exception e) {
                // Not enough data yet
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // PULSING CRISIS BADGE
    // ═══════════════════════════════════════════════════════════════════

    private android.animation.AnimatorSet crisisPulseAnimator;

    /**
     * Start an infinite pulsing animation on the crisis status text
     * to draw attention when signals are active.
     */
    private void startCrisisPulse(View view) {
        if (crisisPulseAnimator != null && crisisPulseAnimator.isRunning()) return;

        android.animation.ObjectAnimator alpha = android.animation.ObjectAnimator.ofFloat(
                view, "alpha", 1f, 0.5f, 1f);
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(
                view, "scaleX", 1f, 1.03f, 1f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(
                view, "scaleY", 1f, 1.03f, 1f);

        crisisPulseAnimator = new android.animation.AnimatorSet();
        crisisPulseAnimator.playTogether(alpha, scaleX, scaleY);
        crisisPulseAnimator.setDuration(2000);
        alpha.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        crisisPulseAnimator.start();
    }

    /**
     * Stop the crisis pulse animation.
     */
    private void stopCrisisPulse(View view) {
        if (crisisPulseAnimator != null) {
            crisisPulseAnimator.cancel();
            crisisPulseAnimator = null;
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
        }
    }
}

