package com.mindtrace.ai.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.WeeklyAssessment;
import com.mindtrace.ai.databinding.ActivityWeeklyAssessmentBinding;
import com.mindtrace.ai.repository.AssessmentRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Weekly Assessment Activity — long-term psychological trend capture.
 *
 * <p>This screen appears once per week (triggered when
 * {@code AssessmentRepository.isWeeklyAssessmentDue()} is true) and captures
 * the user's reflective evaluation across 4 dimensions:</p>
 *
 * <ol>
 *   <li><b>Overall Mood & Core Struggle</b> — subjective week rating</li>
 *   <li><b>Clinical Markers</b> — purpose, social connection, burnout,
 *       self-efficacy, anhedonia, addiction awareness (all 1-10)</li>
 *   <li><b>Protective Factors</b> — exercise, sleep, social days, stability</li>
 *   <li><b>Free-Text Reflection</b> — highlight, challenge, next-week intention</li>
 * </ol>
 *
 * <p>On save, the {@link AssessmentRepository} auto-computes:
 * week-over-week deltas, protective factor score, weekly wellness score,
 * and systemic risk evaluation.</p>
 *
 * <h3>Architecture:</h3>
 * <pre>
 *   WeeklyAssessmentActivity (this class)
 *       → AssessmentRepository.saveWeeklyAssessment()
 *           → computeProtectiveFactorScore()
 *           → computeWeeklyWellnessScore()
 *           → evaluateSystemicRisk()
 *           → compute deltas from previous week
 *           → persist to DB
 * </pre>
 *
 * @see WeeklyAssessment
 * @see AssessmentRepository
 */
public class WeeklyAssessmentActivity extends AppCompatActivity {

    private ActivityWeeklyAssessmentBinding binding;
    private AssessmentRepository assessmentRepository;
    private ExecutorService executor;

    // ── Week window ──
    private long weekStartTimestamp;
    private long weekEndTimestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWeeklyAssessmentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        assessmentRepository = new AssessmentRepository(this);
        executor = com.mindtrace.ai.util.AppExecutors.diskIO();

        computeWeekWindow();
        attachSeekBarLabels();
        attachCharCountWatchers();

        UiMotion.attachPressAnimation(binding.btnSubmitWeekly);
        binding.btnSubmitWeekly.setOnClickListener(v -> submitAssessment());
    }

    // ═════════════════════════════════════════════════════════════════════
    // VIEW BINDING — now using ActivityWeeklyAssessmentBinding
    // ═════════════════════════════════════════════════════════════════════

    // ═════════════════════════════════════════════════════════════════════
    // WEEK WINDOW COMPUTATION
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Computes the 7-day window that this assessment covers.
     * Defaults to the last completed full week (Monday → Sunday).
     */
    private void computeWeekWindow() {
        Calendar cal = Calendar.getInstance();

        // Roll back to last Monday (start of the assessed week)
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysBack = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
        cal.add(Calendar.DAY_OF_YEAR, -(daysBack + 7)); // Previous week's Monday
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        weekStartTimestamp = cal.getTimeInMillis();

        // End = Sunday 23:59:59
        cal.add(Calendar.DAY_OF_YEAR, 6);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        weekEndTimestamp = cal.getTimeInMillis();

        // Format header
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
        String startStr = sdf.format(new Date(weekStartTimestamp));
        String endStr = sdf.format(new Date(weekEndTimestamp));
        binding.tvWeekRange.setText("Week of " + startStr + " – " + endStr);
    }

    // ═════════════════════════════════════════════════════════════════════
    // FORM SUBMISSION
    // ═════════════════════════════════════════════════════════════════════

    private void submitAssessment() {
        WeeklyAssessment assessment = new WeeklyAssessment();
        assessment.timestamp = System.currentTimeMillis();
        assessment.weekStartTimestamp = weekStartTimestamp;
        assessment.weekEndTimestamp = weekEndTimestamp;

        // ── Card 1: Overall Mood ──
        assessment.overallMood = resolveOverallMood();
        assessment.coreStruggle = resolveCoreStruggle();

        // ── Card 2: Clinical Markers (1-10) ──
        assessment.addictionAwarenessScore = binding.sbAddictionAwareness.getProgress();
        assessment.purposeScore = binding.sbPurpose.getProgress();
        assessment.socialConnectionScore = binding.sbSocialConnection.getProgress();
        assessment.burnoutRiskScore = binding.sbBurnout.getProgress();
        assessment.selfEfficacyScore = binding.sbSelfEfficacy.getProgress();
        assessment.anhedoniaScore = binding.sbAnhedonia.getProgress();

        // ── Card 3: Protective Factors ──
        assessment.exerciseFrequency = resolveExerciseFrequency();
        assessment.exerciseDaysCount = exerciseFrequencyToDays(assessment.exerciseFrequency);
        assessment.routineStabilityScore = binding.sbRoutineStability.getProgress();
        assessment.socialInteractionDays = binding.sbSocialDays.getProgress();
        assessment.avgSleepQuality = binding.sbSleepQuality.getProgress();
        assessment.emotionalStabilityScore = binding.sbEmotionalStability.getProgress();
        assessment.negativeMoodDays = binding.sbNegativeDays.getProgress();
        assessment.screenFreeActivities = binding.sbScreenFree.getProgress();     // 2.F.7
        assessment.socialQualityScore = binding.sbSocialQuality.getProgress();    // 2.F.8

        try {
            assessment.avgSleepHours = Float.parseFloat(binding.etAvgSleep.getText().toString());
        } catch (Exception e) {
            assessment.avgSleepHours = 7.0f;
        }

        // ── Card 4: Free-Text ──
        String win = binding.etPrimaryWin.getText().toString().trim();
        if (!win.isEmpty()) assessment.primaryWin = win;

        String struggle = binding.etCoreStruggleText.getText().toString().trim();
        if (!struggle.isEmpty()) assessment.weeklyReflection = struggle;

        String intention = binding.etNextWeekIntention.getText().toString().trim();
        if (!intention.isEmpty()) assessment.nextWeekIntention = intention;

        // ── Save with NLP enrichment (Task 2.F.11 — Advanced) ──
        binding.btnSubmitWeekly.setEnabled(false);
        binding.btnSubmitWeekly.setText("Analyzing reflections...");

        executor.execute(() -> {
            try {
                // Step 1: NLP analysis on free-text fields
                com.mindtrace.ai.ai.LinguisticAnalyzer analyzer =
                        new com.mindtrace.ai.ai.LinguisticAnalyzer();

                // Combine all text for holistic analysis
                StringBuilder combinedText = new StringBuilder();
                if (assessment.primaryWin != null) combinedText.append(assessment.primaryWin).append(" ");
                if (assessment.weeklyReflection != null) combinedText.append(assessment.weeklyReflection).append(" ");
                if (assessment.nextWeekIntention != null) combinedText.append(assessment.nextWeekIntention);

                String fullText = combinedText.toString().trim();
                if (!fullText.isEmpty()) {
                    com.mindtrace.ai.ai.LinguisticAnalyzer.AnalysisResult nlp =
                            analyzer.analyze(fullText);
                    if (nlp != null) {
                        assessment.nlpSentiment = nlp.sentimentScore;
                        assessment.nlpDistressFlags = nlp.distressFlagsJson();
                        assessment.nlpTopics = nlp.topicTagsJson();
                    }
                }

                // Step 2: Auto-create journal entry from weekly reflection
                if (assessment.weeklyReflection != null && !assessment.weeklyReflection.isEmpty()) {
                    com.mindtrace.ai.database.entity.JournalEntry reflectionEntry =
                            new com.mindtrace.ai.database.entity.JournalEntry();
                    reflectionEntry.timestamp = System.currentTimeMillis();
                    reflectionEntry.content = "Weekly Challenge: " + assessment.weeklyReflection;
                    if (assessment.primaryWin != null) {
                        reflectionEntry.content += "\n\nWeekly Win: " + assessment.primaryWin;
                    }
                    reflectionEntry.entryType = "weekly_reflection";
                    reflectionEntry.triggerSource = "weekly_assessment";
                    reflectionEntry.isComplete = true;
                    reflectionEntry.computeWordCount();
                    assessmentRepository.saveJournalEntrySync(reflectionEntry);
                }

                // Step 3: Persist assessment (computes wellness/risk/deltas)
                runOnUiThread(() -> binding.btnSubmitWeekly.setText("Computing wellness score..."));
                assessmentRepository.saveWeeklyAssessmentSync(assessment);

                runOnUiThread(() -> {
                    String trajectoryMsg = assessment.overallTrajectory != null
                            ? " Trajectory: " + assessment.overallTrajectory + "."
                            : "";
                    String wellnessMsg = String.format(Locale.US,
                            "Weekly wellness: %.0f%%.",
                            assessment.weeklyWellnessScore * 100);

                    if (assessment.isCrisisWeek()) {
                        Toast.makeText(this,
                                "⚠️ Systemic risk detected. " + wellnessMsg + trajectoryMsg,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "✅ Assessment saved. " + wellnessMsg + trajectoryMsg,
                                Toast.LENGTH_LONG).show();
                    }
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.btnSubmitWeekly.setEnabled(true);
                    binding.btnSubmitWeekly.setText("Complete Weekly Assessment");
                    Toast.makeText(this,
                            "Failed to save assessment. Please try again.",
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // RESOLVERS
    // ═════════════════════════════════════════════════════════════════════

    private String resolveOverallMood() {
        int id = binding.rgOverallMood.getCheckedRadioButtonId();
        if (id == R.id.rb_mood_thriving) return "Thriving";
        if (id == R.id.rb_mood_good) return "Good";
        if (id == R.id.rb_mood_struggling) return "Struggling";
        if (id == R.id.rb_mood_exhausted) return "Exhausted";
        if (id == R.id.rb_mood_crisis) return "Crisis";
        return "Okay";
    }

    private String resolveCoreStruggle() {
        int id = binding.rgCoreStruggle.getCheckedRadioButtonId();
        if (id == R.id.rb_struggle_distraction) return "Distraction";
        if (id == R.id.rb_struggle_anxiety) return "Anxiety";
        if (id == R.id.rb_struggle_loneliness) return "Loneliness";
        if (id == R.id.rb_struggle_workload) return "Workload";
        if (id == R.id.rb_struggle_sleep) return "Sleep";
        if (id == R.id.rb_struggle_motivation) return "Motivation";
        return "None";
    }

    /** Resolve exercise frequency from RadioGroup (Task 2.F.6). */
    private String resolveExerciseFrequency() {
        int id = binding.rgExerciseFrequency.getCheckedRadioButtonId();
        if (id == R.id.rb_exercise_never) return "Never";
        if (id == R.id.rb_exercise_3_4) return "3-4x";
        if (id == R.id.rb_exercise_daily) return "Daily";
        return "1-2x";
    }

    /** Convert frequency string to approximate day count for entity compatibility. */
    private int exerciseFrequencyToDays(String frequency) {
        if (frequency == null) return 1;
        switch (frequency) {
            case "Never": return 0;
            case "1-2x": return 2;
            case "3-4x": return 4;
            case "Daily": return 7;
            default: return 1;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // LIVE SEEKBAR LABELS (Tasks 2.F.3, 2.F.4, 2.F.5)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Attaches real-time descriptive labels to each SeekBar.
     * As the user slides, the label updates with a value and clinical descriptor.
     */
    private void attachSeekBarLabels() {
        attachLabel(binding.sbAddictionAwareness, binding.tvAddictionLabel, this::describeAddiction);
        attachLabel(binding.sbPurpose, binding.tvPurposeLabel, this::describePurpose);
        attachLabel(binding.sbSocialConnection, binding.tvSocialLabel, this::describeSocial);
        attachLabel(binding.sbBurnout, binding.tvBurnoutLabel, this::describeBurnout);
        attachLabel(binding.sbSelfEfficacy, binding.tvEfficacyLabel, this::describeEfficacy);
        attachLabel(binding.sbAnhedonia, binding.tvAnhedoniaLabel, this::describeAnhedonia);
        attachLabel(binding.sbRoutineStability, binding.tvRoutineLabel, this::describeRoutine);
        attachLabel(binding.sbScreenFree, binding.tvScreenFreeLabel, this::describeScreenFree);
        attachLabel(binding.sbSocialQuality, binding.tvSocialQualityLabel, this::describeSocialQuality);
    }

    private void attachLabel(SeekBar seekBar, TextView label, LabelFormatter formatter) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                label.setText(formatter.format(progress, sb.getMax()));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private interface LabelFormatter {
        String format(int value, int max);
    }

    // ── Descriptor methods ──

    private String describeAddiction(int v, int max) {
        String desc;
        if (v <= 2) desc = "Blind to habit";
        else if (v <= 4) desc = "Starting to notice";
        else if (v <= 6) desc = "Moderate awareness";
        else if (v <= 8) desc = "High awareness";
        else desc = "Fully conscious";
        return v + "/" + max + " — " + desc;
    }

    private String describePurpose(int v, int max) {
        String desc;
        if (v <= 2) desc = "No direction";
        else if (v <= 4) desc = "Some direction";
        else if (v <= 6) desc = "Building purpose";
        else if (v <= 8) desc = "Strong purpose";
        else desc = "Deeply driven";
        return v + "/" + max + " — " + desc;
    }

    private String describeSocial(int v, int max) {
        String desc;
        if (v <= 2) desc = "Isolated";
        else if (v <= 4) desc = "Some contact";
        else if (v <= 6) desc = "Moderate connection";
        else if (v <= 8) desc = "Well connected";
        else desc = "Deeply connected";
        return v + "/" + max + " — " + desc;
    }

    private String describeBurnout(int v, int max) {
        String desc;
        if (v <= 2) desc = "Energized";
        else if (v <= 4) desc = "Low burnout";
        else if (v <= 6) desc = "Moderate fatigue";
        else if (v <= 8) desc = "High burnout";
        else desc = "Completely drained";
        return v + "/" + max + " — " + desc;
    }

    private String describeEfficacy(int v, int max) {
        String desc;
        if (v <= 2) desc = "Helpless";
        else if (v <= 4) desc = "Low confidence";
        else if (v <= 6) desc = "Moderate confidence";
        else if (v <= 8) desc = "High confidence";
        else desc = "Can handle anything";
        return v + "/" + max + " — " + desc;
    }

    private String describeAnhedonia(int v, int max) {
        String desc;
        if (v <= 2) desc = "Nothing brings joy";
        else if (v <= 4) desc = "Occasional enjoyment";
        else if (v <= 6) desc = "Moderate enjoyment";
        else if (v <= 8) desc = "Good enjoyment";
        else desc = "Fully enjoying life";
        return v + "/" + max + " — " + desc;
    }

    private String describeRoutine(int v, int max) {
        String desc;
        if (v <= 1) desc = "No routine";
        else if (v <= 2) desc = "Chaotic";
        else if (v <= 3) desc = "Moderate stability";
        else if (v <= 4) desc = "Mostly stable";
        else desc = "Rock solid";
        return v + "/" + max + " — " + desc;
    }

    private String describeScreenFree(int v, int max) {
        String desc;
        if (v == 0) desc = "None — all screen time";
        else if (v == 1) desc = "Minimal offline time";
        else if (v <= 3) desc = "Some offline activities";
        else if (v == 4) desc = "Good balance";
        else desc = "Excellent offline life";
        return v + "/" + max + " — " + desc;
    }

    private String describeSocialQuality(int v, int max) {
        String desc;
        if (v <= 1) desc = "Negative/draining";
        else if (v == 2) desc = "Mostly surface-level";
        else if (v == 3) desc = "Neutral interactions";
        else if (v == 4) desc = "Meaningful connections";
        else desc = "Deeply fulfilling";
        return v + "/" + max + " — " + desc;
    }

    // ═════════════════════════════════════════════════════════════════════
    // CHAR COUNT WATCHERS (Tasks 2.F.9, 2.F.10)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Attaches live character count labels to free-text fields.
     * Longer entries produce richer NLP data for trend analysis.
     */
    private void attachCharCountWatchers() {
        attachCharWatcher(binding.etPrimaryWin, binding.tvWinCharCount);
        attachCharWatcher(binding.etCoreStruggleText, binding.tvStruggleCharCount);
        attachCharWatcher(binding.etNextWeekIntention, binding.tvIntentionCharCount);
    }

    private void attachCharWatcher(EditText editText, TextView counter) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                int len = s.length();
                String quality;
                if (len == 0) quality = "";
                else if (len < 20) quality = " — brief";
                else if (len < 80) quality = " — good";
                else quality = " — detailed ✔";
                counter.setText(len + " chars" + quality);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // executor is AppExecutors.diskIO() — never shut down the shared pool
        binding = null;
    }
}
