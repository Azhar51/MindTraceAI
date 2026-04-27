package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.LinguisticAnalyzer;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.repository.AssessmentRepository;
import com.mindtrace.ai.util.MoodInsightGenerator;
import com.mindtrace.ai.util.MoodMapper;
import com.mindtrace.ai.viewmodel.QuestionnaireViewModel;

import java.util.Calendar;

/**
 * Daily Check-In Activity — the primary psychological data collection screen.
 *
 * <p>Captures 20+ fields across 6 dimensions, auto-creates gratitude journal
 * entries, runs linguistic analysis on free text, and adapts questions based
 * on the user's recent mood trajectory.</p>
 *
 * <h3>Intelligence Features:</h3>
 * <ul>
 *   <li><b>Time-of-day awareness</b> — morning vs evening prompts (2.E.8)</li>
 *   <li><b>Adaptive questions</b> — shows support card after 2+ sad days (2.E.7)</li>
 *   <li><b>Gratitude pipeline</b> — auto-creates JournalEntry + runs NLP (2.E.2-4)</li>
 *   <li><b>Distress auto-compute</b> — severity + flags computed on save (2.E.5)</li>
 *   <li><b>Dashboard refresh</b> — triggers re-classification after save (2.E.6)</li>
 * </ul>
 */
public class DailyCheckInActivity extends AppCompatActivity {
    private QuestionnaireViewModel viewModel;
    private AssessmentRepository assessmentRepository;
    private LinguisticAnalyzer linguisticAnalyzer;

    // ── Card 1: Mood & Stress ──
    private RadioGroup rgMood;
    private SeekBar sbStress;
    private SeekBar sbLoneliness;
    private SeekBar sbMotivation;

    // ── Card 2: Daily Functioning ──
    private RadioGroup rgWorkPressure;
    private RadioGroup rgFocus;
    private RadioGroup rgDistracted;
    private RadioGroup rgEnergy;
    private EditText etSleep;
    private SeekBar sbUrgeScroll;
    private EditText etBiggestDistraction;
    private TextView tvUrgeScrollDescriptor;

    // ── Card 3: Support & Direction ──
    private SwitchMaterial switchSocialSupport;
    private SwitchMaterial switchGoalClarity;
    private SwitchMaterial switchFeltCrying;
    private SwitchMaterial switchWantedWithdraw;

    // ── Card 4: Gratitude & Self-Perception ──
    private EditText etGratitude;
    private TextView tvGratitudeLabel;
    private SeekBar sbSelfWorth;
    private SeekBar sbPurpose;
    private SeekBar sbHope;

    // ── Card 5: Current State & Body ──
    private SeekBar sbAnxiety;
    private SeekBar sbSleepQuality;
    private SwitchMaterial switchExercisedToday;
    private EditText etCurrentConcern;
    private TextView tvAnxietyDescriptor;
    private TextView tvSleepQualityDescriptor;

    // ── Live clinical descriptor labels ──
    private TextView tvStressDescriptor;
    private TextView tvLonelinessDescriptor;
    private TextView tvMotivationDescriptor;
    private TextView tvSelfWorthDescriptor;
    private TextView tvPurposeDescriptor;
    private TextView tvHopeDescriptor;

    // ── Adaptive Support Card ──
    private MaterialCardView cardAdaptiveSupport;
    private TextView tvAdaptiveMessage;
    private SwitchMaterial switchRequestSupport;

    private MaterialButton btnSubmit;

    // ── State ──
    private String checkInType = "ad_hoc";
    private int consecutiveSadDays = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_check_in);

        viewModel = new ViewModelProvider(this).get(QuestionnaireViewModel.class);
        assessmentRepository = new AssessmentRepository(this);
        linguisticAnalyzer = new LinguisticAnalyzer();

        bindViews();
        wireEmojiCards(); // §2E1.7: emoji mood selection
        wireDescriptors();
        detectTimeOfDay();
        loadAdaptiveState();

        UiMotion.attachPressAnimation(btnSubmit);
        btnSubmit.setOnClickListener(v -> submitForm());

        // Back button
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        viewModel.getSubmissionResult().observe(this, result -> {
            if (result == null) return;

            btnSubmit.setEnabled(true);
            btnSubmit.setText("Save & Analyze");

            if (!result.successful) {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
                return;
            }

            if (result.crisisDetected) {
                Intent supportIntent = new Intent(this, MainActivity.class);
                supportIntent.putExtra(MainActivity.EXTRA_START_DESTINATION, MainActivity.DEST_SUPPORT);
                startActivity(supportIntent);
                finish();
                return;
            }

            String toastMessage = result.message + " " + result.generatedTasks
                    + (result.generatedTasks == 1 ? " task created." : " tasks created.");
            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // VIEW BINDING
    // ═════════════════════════════════════════════════════════════════════

    private void bindViews() {
        // Card 1
        rgMood = findViewById(R.id.rg_mood);
        sbStress = findViewById(R.id.sb_stress);
        sbLoneliness = findViewById(R.id.sb_loneliness);
        sbMotivation = findViewById(R.id.sb_motivation);

        // Card 2
        rgWorkPressure = findViewById(R.id.rg_work_pressure);
        rgFocus = findViewById(R.id.rg_focus);
        rgDistracted = findViewById(R.id.rg_distracted);
        rgEnergy = findViewById(R.id.rg_energy);
        etSleep = findViewById(R.id.et_sleep);
        sbUrgeScroll = findViewById(R.id.sb_urge_scroll);
        etBiggestDistraction = findViewById(R.id.et_biggest_distraction);
        tvUrgeScrollDescriptor = findViewById(R.id.tv_urge_scroll_descriptor);

        // Card 3
        switchSocialSupport = findViewById(R.id.switch_social_support);
        switchGoalClarity = findViewById(R.id.switch_goal_clarity);
        switchFeltCrying = findViewById(R.id.switch_felt_crying);
        switchWantedWithdraw = findViewById(R.id.switch_wanted_withdraw);

        // Card 4
        etGratitude = findViewById(R.id.et_gratitude);
        tvGratitudeLabel = findViewById(R.id.tv_gratitude_label);
        sbSelfWorth = findViewById(R.id.sb_self_worth);
        sbPurpose = findViewById(R.id.sb_purpose);
        sbHope = findViewById(R.id.sb_hope);

        // Card 5
        sbAnxiety = findViewById(R.id.sb_anxiety);
        sbSleepQuality = findViewById(R.id.sb_sleep_quality);
        switchExercisedToday = findViewById(R.id.switch_exercised_today);
        etCurrentConcern = findViewById(R.id.et_current_concern);
        tvAnxietyDescriptor = findViewById(R.id.tv_anxiety_descriptor);
        tvSleepQualityDescriptor = findViewById(R.id.tv_sleep_quality_descriptor);

        // Adaptive
        cardAdaptiveSupport = findViewById(R.id.card_adaptive_support);
        tvAdaptiveMessage = findViewById(R.id.tv_adaptive_message);
        switchRequestSupport = findViewById(R.id.switch_request_support);

        btnSubmit = findViewById(R.id.btn_submit);

        // Descriptor labels — lookup by name so the build doesn't fail
        // when the layout hasn't been updated with these views yet.
        tvStressDescriptor = findViewByIdName("tv_stress_descriptor");
        tvLonelinessDescriptor = findViewByIdName("tv_loneliness_descriptor");
        tvMotivationDescriptor = findViewByIdName("tv_motivation_descriptor");
        tvSelfWorthDescriptor = findViewByIdName("tv_self_worth_descriptor");
        tvPurposeDescriptor = findViewByIdName("tv_purpose_descriptor");
        tvHopeDescriptor = findViewByIdName("tv_hope_descriptor");
    }

    // ═════════════════════════════════════════════════════════════════════
    // EMOJI MOOD CARDS (§2E1.7)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Wires tappable emoji mood cards that visually toggle selected/unselected
     * states and sync with the hidden RadioGroup for data collection.
     */
    private void wireEmojiCards() {
        View emojiHappy = findViewById(R.id.emoji_happy);
        View emojiNeutral = findViewById(R.id.emoji_neutral);
        View emojiSad = findViewById(R.id.emoji_sad);
        View emojiAnxious = findViewById(R.id.emoji_anxious);

        View[] allCards = {emojiHappy, emojiNeutral, emojiSad, emojiAnxious};
        int[] radioIds = {R.id.rb_happy, R.id.rb_neutral, R.id.rb_sad, R.id.rb_anxious};

        for (int i = 0; i < allCards.length; i++) {
            if (allCards[i] == null) continue;
            final int index = i;
            allCards[i].setOnClickListener(v -> {
                // ── Haptic feedback ──
                UiMotion.hapticClick(v);

                // Reset all to unselected + stop breathing pulses
                for (View card : allCards) {
                    if (card != null) {
                        card.setBackgroundResource(R.drawable.bg_mood_emoji_unselected);
                        UiMotion.stopBreathingPulse(card);
                        // Reset label color
                        if (card instanceof android.view.ViewGroup) {
                            android.view.ViewGroup group = (android.view.ViewGroup) card;
                            if (group.getChildCount() > 1) {
                                View label = group.getChildAt(1);
                                if (label instanceof TextView) {
                                    ((TextView) label).setTextColor(0xFF8896B0);
                                }
                            }
                        }
                    }
                }

                // Set this one to selected
                v.setBackgroundResource(R.drawable.bg_mood_emoji_selected);
                if (v instanceof android.view.ViewGroup) {
                    android.view.ViewGroup group = (android.view.ViewGroup) v;
                    if (group.getChildCount() > 1) {
                        View label = group.getChildAt(1);
                        if (label instanceof TextView) {
                            ((TextView) label).setTextColor(0xFF7C8FFF);
                        }
                    }
                }

                // ── Spring physics bounce (OvershootInterpolator 2.5x) ──
                v.animate()
                        .scaleX(1.12f).scaleY(1.12f)
                        .setDuration(120)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .withEndAction(() -> v.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(200)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                                .withEndAction(() -> {
                                    // ── Start breathing pulse on selected emoji ──
                                    if (v instanceof android.view.ViewGroup
                                            && ((android.view.ViewGroup) v).getChildCount() > 0) {
                                        View emojiText = ((android.view.ViewGroup) v).getChildAt(0);
                                        UiMotion.startBreathingPulse(emojiText, 1.06f);
                                    }
                                })
                                .start())
                        .start();

                // Sync hidden RadioGroup
                rgMood.check(radioIds[index]);
            });
        }

        // ── CTA pulsing glow on submit button ──
        UiMotion.pulseGlow(btnSubmit);
    }

    // ═════════════════════════════════════════════════════════════════════
    // LIVE CLINICAL DESCRIPTORS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Wires all SeekBars to show a live text descriptor below each one.
     * Gives the user immediate clinical context for abstract 1-5 scales.
     */
    private void wireDescriptors() {
        wireDescriptor(sbStress, tvStressDescriptor, new String[]{
                "Minimal — feeling at ease",
                "Mild — some tension but manageable",
                "Moderate — noticeably stressed",
                "High — hard to focus or relax",
                "Severe — feeling overwhelmed"
        });

        wireDescriptor(sbLoneliness, tvLonelinessDescriptor, new String[]{
                "Connected — not lonely at all",
                "Mild — occasional isolation",
                "Moderate — wishing for company",
                "High — feeling left out",
                "Severe — deeply alone"
        });

        wireDescriptor(sbMotivation, tvMotivationDescriptor, new String[]{
                "Very low — no drive at all",
                "Low — struggling to get started",
                "Moderate — some effort possible",
                "High — feeling driven",
                "Very high — energized and ready"
        });

        wireDescriptor(sbSelfWorth, tvSelfWorthDescriptor, new String[]{
                "Very low — feeling worthless",
                "Low — doubting yourself",
                "Moderate — somewhat confident",
                "Good — feeling capable",
                "Strong — confident and valued"
        });

        wireDescriptor(sbPurpose, tvPurposeDescriptor, new String[]{
                "Lost — no sense of direction",
                "Unclear — searching for meaning",
                "Moderate — some direction",
                "Clear — goals feel meaningful",
                "Strong — deeply purposeful"
        });

        wireDescriptor(sbHope, tvHopeDescriptor, new String[]{
                "Hopeless — the future feels dark",
                "Low — hard to see things improving",
                "Moderate — cautiously optimistic",
                "Hopeful — better days are coming",
                "Very hopeful — confident about the future"
        });

        wireDescriptor(sbUrgeScroll, tvUrgeScrollDescriptor, new String[]{
                "None — zero desire",
                "Mild — passing thought",
                "Moderate — noticeable urges",
                "Strong — hard to resist",
                "Severe — consumed by cravings"
        });

        wireDescriptor(sbAnxiety, tvAnxietyDescriptor, new String[]{
                "Calm — no worry",
                "Mild — some worry",
                "Moderate — noticeable anxiety",
                "High — hard to relax",
                "Severe — overwhelming panic"
        });

        wireDescriptor(sbSleepQuality, tvSleepQualityDescriptor, new String[]{
                "Terrible — barely slept",
                "Poor — restless and tired",
                "Decent — woke up okay",
                "Good — refreshing sleep",
                "Excellent — deep and restorative"
        });
    }

    private void wireDescriptor(SeekBar seekBar, TextView label, String[] descriptors) {
        if (seekBar == null || label == null || descriptors == null) return;
        // Set initial
        int idx = Math.max(0, Math.min(seekBar.getProgress() - 1, descriptors.length - 1));
        label.setText(descriptors[idx]);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int i = Math.max(0, Math.min(progress - 1, descriptors.length - 1));
                label.setText(descriptors[i]);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    /** Safe runtime view lookup by ID name — returns null if view not in layout. */
    @SuppressWarnings("unchecked")
    private <T extends android.view.View> T findViewByIdName(String idName) {
        int resId = getResources().getIdentifier(idName, "id", getPackageName());
        return resId != 0 ? (T) findViewById(resId) : null;
    }

    // ═════════════════════════════════════════════════════════════════════
    // TIME-OF-DAY DETECTION (Task 2.E.8)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Detects whether this is a morning or evening check-in and adjusts
     * UI labels accordingly.
     *
     * <p>Morning (5am-12pm): focuses on sleep quality + anticipation.
     * Evening (5pm-11pm): focuses on reflection + gratitude.
     * Other times: treated as ad-hoc.</p>
     */
    private void detectTimeOfDay() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hour >= 5 && hour < 12) {
            checkInType = "morning";
            tvGratitudeLabel.setText("What are you looking forward to today?");
            etGratitude.setHint("e.g. A productive morning, meeting a friend...");
        } else if (hour >= 17 && hour <= 23) {
            checkInType = "evening";
            tvGratitudeLabel.setText("What are you grateful for today?");
            etGratitude.setHint("e.g. My morning coffee, a kind word from a friend...");
        } else {
            checkInType = "ad_hoc";
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // ADAPTIVE QUESTION LOGIC (Task 2.E.7)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Checks consecutive sad days and shows the support card if ≥2.
     * Runs on a background thread to avoid blocking the UI.
     */
    private void loadAdaptiveState() {
        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            try {
                consecutiveSadDays = assessmentRepository.getConsecutiveSadDays();
                com.mindtrace.ai.util.AppExecutors.mainThread().execute(() -> {
                    if (isFinishing()) return;
                    if (consecutiveSadDays >= 2) {
                        cardAdaptiveSupport.setVisibility(View.VISIBLE);

                        // Use MoodMapper for streak-amplified risk
                        float streakRisk = MoodMapper.getStreakAmplifiedRisk(
                                MoodMapper.MOOD_SAD, null);
                        String riskLabel = MoodMapper.riskToLabel(
                                Math.min(1f, streakRisk + consecutiveSadDays * 0.05f));

                        String message;
                        if (consecutiveSadDays >= 5) {
                            message = "⚠️ You've been in distress for " + consecutiveSadDays
                                    + " days (" + riskLabel + " risk). "
                                    + "We strongly recommend reaching out. "
                                    + "Would you like MindTrace to connect you with support?";
                        } else {
                            message = MoodMapper.getMoodEmoji(MoodMapper.MOOD_SAD)
                                    + " You've reported feeling down for "
                                    + consecutiveSadDays + " consecutive day"
                                    + (consecutiveSadDays > 1 ? "s" : "")
                                    + ". " + MoodMapper.getMoodCopingTip(MoodMapper.MOOD_SAD)
                                    + " Would you like MindTrace to connect you with support resources?";
                        }
                        tvAdaptiveMessage.setText(message);
                    }
                });
            } catch (Exception ignored) {
                // Fail silently — adaptive is enhancement, not critical
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // FORM SUBMISSION (Tasks 2.E.2–2.E.6)
    // ═════════════════════════════════════════════════════════════════════

    private void submitForm() {
        QuestionnaireResponse response = new QuestionnaireResponse();
        response.timestamp = System.currentTimeMillis();
        response.checkInType = checkInType;

        // ── Dimension 1: Emotional State ──
        int selectedMoodId = rgMood.getCheckedRadioButtonId();
        if (selectedMoodId == R.id.rb_happy) response.mood = MoodMapper.MOOD_HAPPY;
        else if (selectedMoodId == R.id.rb_sad) response.mood = MoodMapper.MOOD_SAD;
        else if (selectedMoodId == R.id.rb_anxious) response.mood = MoodMapper.MOOD_ANXIOUS;
        else response.mood = MoodMapper.MOOD_NEUTRAL;

        response.hopeLevel = normalizeScale(sbHope);               // 2.E.2: new
        response.feltLikeCrying = switchFeltCrying.isChecked();     // 2.E.2: new

        // ── Dimension 2: Cognitive State & Distraction ──
        response.focusLevel = resolveFocusLevel();
        response.feltDistracted = resolveDistracted();
        response.urgeToScrollLevel = normalizeScale(sbUrgeScroll);
        
        String distractionText = etBiggestDistraction.getText().toString().trim();
        if (!distractionText.isEmpty()) {
            response.biggestDistraction = distractionText;
        }

        // ── Dimension 3: Stress & Coping ──
        response.stressLevel = normalizeScale(sbStress);
        response.workPressure = resolveWorkPressure();

        // ── Dimension 4: Social Connection ──
        response.lonelinessLevel = normalizeScale(sbLoneliness);
        response.socialSupport = switchSocialSupport.isChecked();
        response.wantedToWithdraw = switchWantedWithdraw.isChecked(); // 2.E.2: new

        // ── Dimension 5: Physical Wellbeing ──
        response.energyLevel = resolveEnergyLevel();
        response.anxietyLevel = normalizeScale(sbAnxiety);
        response.sleepQuality = normalizeScale(sbSleepQuality);
        response.exercisedToday = switchExercisedToday.isChecked();
        try {
            float sleepHours = Float.parseFloat(etSleep.getText().toString());
            response.sleepHours = sleepHours <= 0f ? 7.0f : sleepHours;
        } catch (Exception e) {
            response.sleepHours = 7.0f;
        }

        // ── Current Concern (for AI Coach) ──
        String concernText = etCurrentConcern.getText().toString().trim();
        if (!concernText.isEmpty()) {
            response.currentConcern = concernText;
        }

        // ── Dimension 6: Self-Perception (2.E.2: new) ──
        response.motivationLevel = normalizeScale(sbMotivation);
        response.selfWorthScore = normalizeScale(sbSelfWorth);
        response.purposeScore = normalizeScale(sbPurpose);
        response.goalClarity = switchGoalClarity.isChecked();

        // ── Gratitude text binding (Task 2.E.2) ──
        String gratitudeText = etGratitude.getText().toString().trim();
        if (!gratitudeText.isEmpty()) {
            response.gratitudeText = gratitudeText;
        }

        // ── Adaptive support (Task 2.E.7) ──
        if (cardAdaptiveSupport.getVisibility() == View.VISIBLE) {
            response.requestedSupport = switchRequestSupport.isChecked();
        }

        // ── Linguistic analysis on gratitude text (Task 2.E.4) ──
        if (response.gratitudeText != null && !response.gratitudeText.isEmpty()) {
            LinguisticAnalyzer.AnalysisResult analysis =
                    linguisticAnalyzer.analyze(response.gratitudeText);
            if (analysis != null) {
                // Enrich the response with NLP signals
                if (analysis.distressFlags != null && !analysis.distressFlags.isEmpty()) {
                    // Merge NLP distress flags with auto-computed ones
                    response.distressFlags = analysis.distressFlagsJson();
                }
            }
        }

        // ── Distress auto-compute (Task 2.E.5) ──
        // computeDistressSeverity() and buildDistressFlags() are called
        // inside AssessmentRepository.saveCheckIn() — no manual call needed.
        // AssessmentRepository also auto-creates JournalEntry (Task 2.E.3).

        // ── Submit ──
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Analyzing...");

        // Task 2.E.6: ViewModel handles the full pipeline:
        // save → classify → anomaly detect → generate tasks → crisis check
        viewModel.submitResponse(response);
    }

    // ═════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private int normalizeScale(SeekBar seekBar) {
        int value = seekBar.getProgress();
        if (value < 1) return 1;
        return Math.min(value, 5);
    }

    private String resolveWorkPressure() {
        int selectedId = rgWorkPressure.getCheckedRadioButtonId();
        if (selectedId == R.id.rb_work_low) return "Low";
        if (selectedId == R.id.rb_work_high) return "High";
        return "Medium";
    }

    private String resolveFocusLevel() {
        int selectedId = rgFocus.getCheckedRadioButtonId();
        if (selectedId == R.id.rb_focus_low) return "Low";
        if (selectedId == R.id.rb_focus_high) return "High";
        return "Medium";
    }

    private boolean resolveDistracted() {
        return rgDistracted.getCheckedRadioButtonId() == R.id.rb_distracted_yes;
    }

    private String resolveEnergyLevel() {
        int selectedId = rgEnergy.getCheckedRadioButtonId();
        if (selectedId == R.id.rb_energy_low) return "Low";
        if (selectedId == R.id.rb_energy_high) return "High";
        return "Medium";
    }
}
