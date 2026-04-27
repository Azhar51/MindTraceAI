package com.mindtrace.ai.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.LinguisticAnalyzer;
import com.mindtrace.ai.database.entity.JournalEntry;
import com.mindtrace.ai.repository.AssessmentRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.AlertDialog;
import android.content.Intent;

/**
 * Premium Journal Activity — free-form writing with real-time NLP analysis.
 *
 * <p>Supports 6 entry types (free_form, gratitude, venting, guided_reflection,
 * goal_setting, trigger_log), 7 mood selectors, live word counting,
 * writing duration tracking, and post-save AI sentiment/distress analysis.</p>
 *
 * <h3>Pipeline:</h3>
 * <pre>
 *   User writes → JournalEntry built → LinguisticAnalyzer.analyze()
 *       → AI enrichment fields populated → AssessmentRepository.saveJournalEntry()
 *       → Analysis card shown with sentiment, emotions, topics, distortions
 * </pre>
 */
public class JournalActivity extends AppCompatActivity {

    // ── Entry type chips ──
    private TextView chipFreeForm, chipGratitude, chipVenting, chipReflection, chipGoal, chipTrigger;
    private String selectedType = "free_form";

    // ── Mood selectors ──
    private TextView moodHappy, moodCalm, moodNeutral, moodAnxious, moodSad, moodAngry, moodNumb;
    private TextView tvMoodLabel;
    private String selectedMood = null;

    // ── Writing surface ──
    private EditText etTitle, etContent, etActionItem;
    private TextView tvWordCount, tvStressValue, tvTimer;
    private SeekBar seekStress;

    // ── AI analysis card ──
    private CardView cardAnalysis;
    private TextView tvSentimentLabel, tvEmotionTags, tvTopicTags, tvDistressMessage, tvDistortions, tvReframe;
    private LinearLayout distressWarning, reframeContainer;
    private View sentimentBar;

    // ── History ──
    private CardView cardHistory;
    private LinearLayout historyContainer;
    private TextView tvNoEntries, tvStreak;
    private CardView cardPrompt;
    private TextView tvPrompt;

    // ── Save button ──
    private Button btnSave;

    // ── State ──
    private AssessmentRepository repository;
    private LinguisticAnalyzer analyzer;
    private ExecutorService executor;
    private long writingStartTime;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private boolean isSaved = false;
    private boolean isSafetyMode = false;

    // ── Safety mode extras ──
    public static final String EXTRA_SAFETY_MODE = "safety_mode";
    private static final String[] SAFETY_PROMPTS = {
            "What triggered this feeling? Take your time — there's no rush.",
            "What has helped you through tough moments like this before?",
            "Who can you reach out to right now? Even a text can help.",
            "Name one thing you can see, hear, and feel right now.",
            "Write freely — getting thoughts out of your head can bring relief."
    };

    // ── Prompts per type ──
    private static final String[] FREE_PROMPTS = {
            "What's on your mind right now? Write freely — there are no wrong answers.",
            "If today were a chapter in your story, what would its title be?",
            "What moment today stood out to you, and why?"
    };
    private static final String[] GRATITUDE_PROMPTS = {
            "Name 3 things you're grateful for today, big or small.",
            "Who made a positive difference in your day today?",
            "What simple pleasure did you enjoy today?"
    };
    private static final String[] VENTING_PROMPTS = {
            "Let it out. What's frustrating or bothering you right now?",
            "What would you say if nobody was listening?",
            "What feels unfair or overwhelming today?"
    };
    private static final String[] REFLECTION_PROMPTS = {
            "What did you learn about yourself today?",
            "If you could redo one moment from today, which would it be?",
            "What pattern do you notice in how you've been feeling lately?"
    };
    private static final String[] GOAL_PROMPTS = {
            "What's one thing you want to accomplish tomorrow?",
            "What small step could bring you closer to how you want to feel?",
            "If you had full control, what would you change this week?"
    };
    private static final String[] TRIGGER_PROMPTS = {
            "What event or thought triggered this feeling?",
            "When did you first notice the shift in your mood?",
            "What were you doing when the negative feeling started?"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.background));

        repository = new AssessmentRepository(this);
        analyzer = new LinguisticAnalyzer();
        executor = Executors.newSingleThreadExecutor();
        writingStartTime = System.currentTimeMillis();

        bindViews();
        setupTypeChips();
        setupMoodSelector();
        setupWritingSurface();
        setupSaveButton();
        loadHistory();
        startTimer();

        // Safety mode check
        if (getIntent().getBooleanExtra(EXTRA_SAFETY_MODE, false)) {
            activateSafetyMode();
        }
    }

    /**
     * Activate safety journal mode — switches to guided crisis prompts,
     * auto-selects "venting" type, and pre-fills supportive messaging.
     */
    private void activateSafetyMode() {
        isSafetyMode = true;
        selectedType = "venting";

        // Set safety prompt
        tvPrompt.setText(SAFETY_PROMPTS[(int) (Math.random() * SAFETY_PROMPTS.length)]);

        // Update hints
        etContent.setHint("Write freely — this is a safe space. No one else will see this.");
        etTitle.setHint("How I'm feeling right now");

        // Auto-select anxious mood if nothing selected
        if (selectedMood == null) {
            moodAnxious.performClick();
        }
    }

    private void bindViews() {
        // Top bar
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        tvTimer = findViewById(R.id.tvTimer);

        // Type chips
        chipFreeForm = findViewById(R.id.chipFreeForm);
        chipGratitude = findViewById(R.id.chipGratitude);
        chipVenting = findViewById(R.id.chipVenting);
        chipReflection = findViewById(R.id.chipReflection);
        chipGoal = findViewById(R.id.chipGoal);
        chipTrigger = findViewById(R.id.chipTrigger);

        // Mood
        moodHappy = findViewById(R.id.moodHappy);
        moodCalm = findViewById(R.id.moodCalm);
        moodNeutral = findViewById(R.id.moodNeutral);
        moodAnxious = findViewById(R.id.moodAnxious);
        moodSad = findViewById(R.id.moodSad);
        moodAngry = findViewById(R.id.moodAngry);
        moodNumb = findViewById(R.id.moodNumb);
        tvMoodLabel = findViewById(R.id.tvMoodLabel);

        // Writing
        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        etActionItem = findViewById(R.id.etActionItem);
        tvWordCount = findViewById(R.id.tvWordCount);
        seekStress = findViewById(R.id.seekStress);
        tvStressValue = findViewById(R.id.tvStressValue);

        // Prompt
        cardPrompt = findViewById(R.id.cardPrompt);
        tvPrompt = findViewById(R.id.tvPrompt);

        // Analysis
        cardAnalysis = findViewById(R.id.cardAnalysis);
        tvSentimentLabel = findViewById(R.id.tvSentimentLabel);
        tvEmotionTags = findViewById(R.id.tvEmotionTags);
        tvTopicTags = findViewById(R.id.tvTopicTags);
        tvDistressMessage = findViewById(R.id.tvDistressMessage);
        tvDistortions = findViewById(R.id.tvDistortions);
        tvReframe = findViewById(R.id.tvReframe);
        distressWarning = findViewById(R.id.distressWarning);
        reframeContainer = findViewById(R.id.reframeContainer);
        sentimentBar = findViewById(R.id.sentimentBar);

        // History
        cardHistory = findViewById(R.id.cardHistory);
        historyContainer = findViewById(R.id.historyContainer);
        tvNoEntries = findViewById(R.id.tvNoEntries);
        tvStreak = findViewById(R.id.tvStreak);

        // Save
        btnSave = findViewById(R.id.btnSave);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TYPE CHIPS
    // ═══════════════════════════════════════════════════════════════════

    private void setupTypeChips() {
        TextView[] chips = {chipFreeForm, chipGratitude, chipVenting, chipReflection, chipGoal, chipTrigger};
        String[] types = {"free_form", "gratitude", "venting", "guided_reflection", "goal_setting", "trigger_log"};
        String[][] prompts = {FREE_PROMPTS, GRATITUDE_PROMPTS, VENTING_PROMPTS, REFLECTION_PROMPTS, GOAL_PROMPTS, TRIGGER_PROMPTS};

        for (int i = 0; i < chips.length; i++) {
            final int idx = i;
            chips[i].setOnClickListener(v -> {
                selectedType = types[idx];
                for (int j = 0; j < chips.length; j++) {
                    chips[j].setBackgroundResource(j == idx ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                    chips[j].setTextColor(ContextCompat.getColor(this, j == idx ? R.color.text_main : R.color.text_secondary));
                }
                // Set random prompt for the selected type
                String[] pool = prompts[idx];
                tvPrompt.setText(pool[(int) (Math.random() * pool.length)]);
                // Update title hint per type
                updateHintForType(types[idx]);
            });
        }
    }

    private void updateHintForType(String type) {
        switch (type) {
            case "gratitude":
                etContent.setHint("I'm grateful for...");
                break;
            case "venting":
                etContent.setHint("What I need to get off my chest...");
                break;
            case "guided_reflection":
                etContent.setHint("Looking back, I notice...");
                break;
            case "goal_setting":
                etContent.setHint("My intention is...");
                break;
            case "trigger_log":
                etContent.setHint("What triggered this was...");
                break;
            default:
                etContent.setHint("Start writing...");
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOOD SELECTOR
    // ═══════════════════════════════════════════════════════════════════

    private void setupMoodSelector() {
        TextView[] moods = {moodHappy, moodCalm, moodNeutral, moodAnxious, moodSad, moodAngry, moodNumb};
        String[] labels = {"Happy", "Calm", "Neutral", "Anxious", "Sad", "Angry", "Numb"};

        for (int i = 0; i < moods.length; i++) {
            final int idx = i;
            moods[i].setOnClickListener(v -> {
                selectedMood = labels[idx];
                for (int j = 0; j < moods.length; j++) {
                    moods[j].setBackgroundResource(j == idx ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                    moods[j].setAlpha(j == idx ? 1.0f : 0.5f);
                }
                tvMoodLabel.setText("Feeling: " + labels[idx]);
                tvMoodLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WRITING SURFACE — word count, stress bar
    // ═══════════════════════════════════════════════════════════════════

    private void setupWritingSurface() {
        etContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                int words = text.isEmpty() ? 0 : text.split("\\s+").length;
                tvWordCount.setText(words + (words == 1 ? " word" : " words"));
            }
        });

        seekStress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                tvStressValue.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIMER
    // ═══════════════════════════════════════════════════════════════════

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long elapsed = (System.currentTimeMillis() - writingStartTime) / 1000;
            int mins = (int) (elapsed / 60);
            int secs = (int) (elapsed % 60);
            tvTimer.setText(String.format(Locale.US, "%d:%02d", mins, secs));
            timerHandler.postDelayed(this, 1000);
        }
    };

    private void startTimer() {
        timerHandler.post(timerRunnable);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SAVE & ANALYZE
    // ═══════════════════════════════════════════════════════════════════

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> {
            String content = etContent.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(this, "Write something before saving", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isSaved) {
                Toast.makeText(this, "Already saved!", Toast.LENGTH_SHORT).show();
                return;
            }
            saveAndAnalyze(content);
        });
    }

    private void saveAndAnalyze(String content) {
        btnSave.setEnabled(false);
        btnSave.setText("Analyzing...");

        executor.execute(() -> {
            // Build JournalEntry
            JournalEntry entry = new JournalEntry();
            entry.timestamp = System.currentTimeMillis();
            entry.dayTimestamp = getStartOfTodayMillis();
            entry.content = content;
            entry.entryType = selectedType;
            entry.title = etTitle.getText().toString().trim().isEmpty() ? null : etTitle.getText().toString().trim();
            entry.moodAtWriting = selectedMood;
            entry.stressAtWriting = seekStress.getProgress();
            entry.triggerSource = "self_initiated";
            entry.writingDurationMs = System.currentTimeMillis() - writingStartTime;
            entry.isComplete = true;
            entry.computeWordCount();

            // Action item
            String action = etActionItem.getText().toString().trim();
            if (!action.isEmpty()) {
                entry.actionItem = action;
            }

            // Run NLP analysis
            LinguisticAnalyzer.AnalysisResult analysis = analyzer.analyze(content);
            if (analysis != null) {
                entry.sentimentScore = analysis.sentimentScore;
                entry.aiSentiment = analysis.sentimentLabel;
                entry.emotionTags = analysis.emotionTagsJson();
                entry.distressFlags = analysis.distressFlagsJson();
                entry.distressFlagCount = analysis.distressFlags.size();
                entry.topicTags = analysis.topicTagsJson();
                entry.cognitiveDistortions = analysis.cognitiveDistortionsJson();
                entry.gratitudeItemCount = analysis.gratitudeItemCount;

                // Generate reframe if distortions detected
                if (!analysis.cognitiveDistortions.isEmpty()) {
                    entry.aiReframeSuggestion = generateReframe(analysis.cognitiveDistortions.get(0), content);
                }
            }

            // Save
            repository.saveJournalEntrySync(entry);

            // ── Gap Fix: Wire journal→crisis re-check (Blueprint §6 Level 3) ──
            // When distress markers are detected, re-evaluate crisis classification
            if (analysis != null && !analysis.distressFlags.isEmpty()) {
                try {
                    com.mindtrace.ai.ai.MultiModalClassifier classifier =
                            new com.mindtrace.ai.ai.MultiModalClassifier(JournalActivity.this);
                    com.mindtrace.ai.database.entity.RiskClassification latestRc =
                            com.mindtrace.ai.database.AppDatabase
                                    .getInstance(JournalActivity.this)
                                    .riskClassificationDao().getLatestSync();
                    if (latestRc != null) {
                        classifier.recheckCrisisFromJournal(latestRc, analyzer, content);
                    }
                } catch (Exception ignored) {
                    // Crisis recheck is best-effort — don't block journal save
                }
            }

            // Crisis keyword detection after save
            boolean hasCrisisSignals = analysis != null &&
                    (analysis.distressFlags.size() >= 2 ||
                     hasCrisisKeywords(content) ||
                     (analysis.sentimentScore < -0.6f && entry.stressAtWriting >= 8));

            // Show results on UI thread
            final LinguisticAnalyzer.AnalysisResult finalAnalysis = analysis;
            final JournalEntry savedEntry = entry;
            final boolean showCrisisPrompt = hasCrisisSignals;
            runOnUiThread(() -> {
                isSaved = true;
                btnSave.setText("✓ Saved");
                btnSave.setBackgroundResource(R.drawable.bg_chip_selected);
                timerHandler.removeCallbacks(timerRunnable);
                showAnalysisResult(finalAnalysis, savedEntry);
                loadHistory();
                Toast.makeText(this,
                        "Journal saved" + (savedEntry.wordCount > 0 ? " · " + savedEntry.wordCount + " words" : ""),
                        Toast.LENGTH_SHORT).show();

                // Show gentle support prompt if crisis signals detected
                if (showCrisisPrompt) {
                    showCrisisSupportDialog();
                }
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANALYSIS DISPLAY
    // ═══════════════════════════════════════════════════════════════════

    private void showAnalysisResult(LinguisticAnalyzer.AnalysisResult analysis, JournalEntry entry) {
        if (analysis == null) {
            cardAnalysis.setVisibility(View.GONE);
            return;
        }

        cardAnalysis.setVisibility(View.VISIBLE);

        // Sentiment
        tvSentimentLabel.setText(formatSentimentLabel(analysis.sentimentLabel));
        int sentimentColor = getSentimentColor(analysis.sentimentScore);
        tvSentimentLabel.setTextColor(sentimentColor);
        colorSentimentBar(analysis.sentimentScore);

        // Emotion tags
        if (!analysis.emotionTags.isEmpty()) {
            StringBuilder emo = new StringBuilder("Emotions: ");
            for (int i = 0; i < analysis.emotionTags.size(); i++) {
                if (i > 0) emo.append(" · ");
                emo.append(capitalizeFirst(analysis.emotionTags.get(i)));
            }
            tvEmotionTags.setText(emo.toString());
            tvEmotionTags.setVisibility(View.VISIBLE);
        } else {
            tvEmotionTags.setVisibility(View.GONE);
        }

        // Topic tags
        if (!analysis.topicTags.isEmpty()) {
            StringBuilder topics = new StringBuilder("Topics: ");
            for (int i = 0; i < analysis.topicTags.size(); i++) {
                if (i > 0) topics.append(" · ");
                topics.append("#").append(analysis.topicTags.get(i));
            }
            tvTopicTags.setText(topics.toString());
            tvTopicTags.setVisibility(View.VISIBLE);
        } else {
            tvTopicTags.setVisibility(View.GONE);
        }

        // Distress warning
        if (!analysis.distressFlags.isEmpty()) {
            distressWarning.setVisibility(View.VISIBLE);
            tvDistressMessage.setText("Distress signals detected: " +
                    String.join(", ", analysis.distressFlags) +
                    ". You're not alone — support is available.");
        } else {
            distressWarning.setVisibility(View.GONE);
        }

        // Cognitive distortions
        if (!analysis.cognitiveDistortions.isEmpty()) {
            StringBuilder dist = new StringBuilder("🧠 Thought patterns: ");
            for (int i = 0; i < analysis.cognitiveDistortions.size(); i++) {
                if (i > 0) dist.append(", ");
                dist.append(humanizeDistortion(analysis.cognitiveDistortions.get(i)));
            }
            tvDistortions.setText(dist.toString());
            tvDistortions.setVisibility(View.VISIBLE);
        } else {
            tvDistortions.setVisibility(View.GONE);
        }

        // Reframe suggestion
        if (entry.aiReframeSuggestion != null && !entry.aiReframeSuggestion.isEmpty()) {
            tvReframe.setText(entry.aiReframeSuggestion);
            reframeContainer.setVisibility(View.VISIBLE);
        } else {
            reframeContainer.setVisibility(View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HISTORY
    // ═══════════════════════════════════════════════════════════════════

    private void loadHistory() {
        executor.execute(() -> {
            List<JournalEntry> recent = repository.getRecentJournalEntries(5);
            int streak = repository.getJournalStreak();

            runOnUiThread(() -> {
                tvStreak.setText("🔥 " + streak + "-day streak");
                historyContainer.removeAllViews();

                if (recent == null || recent.isEmpty()) {
                    tvNoEntries.setVisibility(View.VISIBLE);
                    return;
                }
                tvNoEntries.setVisibility(View.GONE);

                SimpleDateFormat sdf = new SimpleDateFormat("MMM d · h:mm a", Locale.US);
                for (JournalEntry entry : recent) {
                    View row = buildHistoryRow(entry, sdf);
                    historyContainer.addView(row);
                }
            });
        });
    }

    private View buildHistoryRow(JournalEntry entry, SimpleDateFormat sdf) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        // Header: type emoji + date + sentiment
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView emoji = new TextView(this);
        emoji.setText(getTypeEmoji(entry.entryType));
        emoji.setTextSize(14);

        TextView date = new TextView(this);
        date.setText(sdf.format(new Date(entry.timestamp)));
        date.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        date.setTextSize(12);
        date.setPadding(dp(8), 0, 0, 0);

        TextView sentiment = new TextView(this);
        if (entry.aiSentiment != null) {
            sentiment.setText(formatSentimentLabel(entry.aiSentiment));
            sentiment.setTextColor(getSentimentColor(entry.sentimentScore));
        }
        sentiment.setTextSize(11);
        sentiment.setPadding(dp(8), 0, 0, 0);

        header.addView(emoji);
        header.addView(date);
        header.addView(sentiment);

        // Preview text
        TextView preview = new TextView(this);
        String text = entry.content != null ? entry.content : "";
        preview.setText(text.length() > 80 ? text.substring(0, 80) + "..." : text);
        preview.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
        preview.setTextSize(12);
        preview.setMaxLines(2);
        preview.setPadding(0, dp(4), 0, 0);

        // Stats: word count + mood
        TextView stats = new TextView(this);
        String statsText = entry.wordCount + " words";
        if (entry.moodAtWriting != null) statsText += " · " + entry.moodAtWriting;
        stats.setText(statsText);
        stats.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
        stats.setTextSize(11);
        stats.setPadding(0, dp(2), 0, 0);

        row.addView(header);
        row.addView(preview);
        row.addView(stats);

        // Divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.outline));
        row.addView(divider);

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private String formatSentimentLabel(String label) {
        if (label == null) return "Neutral";
        switch (label) {
            case "very_positive": return "Very Positive";
            case "positive": return "Positive";
            case "neutral": return "Neutral";
            case "negative": return "Negative";
            case "very_negative": return "Very Negative";
            case "mixed": return "Mixed";
            default: return capitalizeFirst(label);
        }
    }

    private int getSentimentColor(float score) {
        if (score > 0.3f) return ContextCompat.getColor(this, R.color.calm_green);
        if (score < -0.3f) return ContextCompat.getColor(this, R.color.error);
        return ContextCompat.getColor(this, R.color.risk_moderate);
    }

    private void colorSentimentBar(float score) {
        try {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(3));
            bg.setColor(getSentimentColor(score));
            sentimentBar.setBackground(bg);
            // Set width proportional to sentiment (map -1..+1 to 10%..100%)
            float pct = Math.max(0.1f, (score + 1f) / 2f);
            sentimentBar.setScaleX(pct);
            sentimentBar.setPivotX(0);
        } catch (Exception ignored) {}
    }

    private String getTypeEmoji(String type) {
        if (type == null) return "✍️";
        switch (type) {
            case "gratitude": return "🙏";
            case "venting": return "😤";
            case "guided_reflection": return "🪞";
            case "goal_setting": return "🎯";
            case "trigger_log": return "⚡";
            case "cbt_reframe": return "💭";
            case "dream_log": return "🌙";
            default: return "✍️";
        }
    }

    private String humanizeDistortion(String distortion) {
        if (distortion == null) return "";
        switch (distortion) {
            case "all_or_nothing": return "All-or-nothing thinking";
            case "catastrophizing": return "Catastrophizing";
            case "mind_reading": return "Mind reading";
            case "should_statements": return "Should statements";
            case "self_blame": return "Self-blame";
            case "overgeneralization": return "Overgeneralization";
            case "emotional_reasoning": return "Emotional reasoning";
            case "labeling": return "Labeling";
            default: return capitalizeFirst(distortion.replace("_", " "));
        }
    }

    private String generateReframe(String distortion, String content) {
        switch (distortion) {
            case "all_or_nothing":
                return "Try looking for the middle ground. Things are rarely all good or all bad — what's one small positive in this situation?";
            case "catastrophizing":
                return "Your mind is jumping to the worst case. Ask yourself: what's actually most likely to happen? And what evidence do I have?";
            case "self_blame":
                return "You're being hard on yourself. Would you say this to a close friend? Consider what external factors also contributed.";
            case "should_statements":
                return "Replace 'should' with 'I'd prefer to' or 'I'll try to.' Shoulds create unnecessary pressure.";
            case "mind_reading":
                return "You can't know what others are thinking without asking. Consider checking in with them directly.";
            case "overgeneralization":
                return "One event doesn't define a pattern. Think of times when things went differently.";
            case "emotional_reasoning":
                return "Feelings aren't facts. Just because something feels true doesn't mean it is. What does the evidence say?";
            case "labeling":
                return "You are not your worst moment. Separate your actions from your identity — everyone has difficult days.";
            default:
                return "Notice this thought pattern and gently challenge it. Ask: is this thought helpful? Is it accurate?";
        }
    }

    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private long getStartOfTodayMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS KEYWORD DETECTION & SUPPORT DIALOG
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check journal content for crisis keywords.
     * These are clinical distress indicators that warrant a support prompt.
     */
    private boolean hasCrisisKeywords(String content) {
        if (content == null) return false;
        String lower = content.toLowerCase(Locale.US);
        String[] crisisTerms = {
                "don't want to live", "want to die", "kill myself", "end it all",
                "can't go on", "no reason to live", "hopeless", "worthless",
                "self-harm", "hurt myself", "nobody cares", "can't take it anymore",
                "give up", "what's the point", "better off dead", "suicidal",
                "no way out", "exhausted of life", "tired of living"
        };
        for (String term : crisisTerms) {
            if (lower.contains(term)) return true;
        }
        return false;
    }

    /**
     * Show a gentle, non-alarming dialog when crisis signals are detected.
     * Offers to open support resources without being intrusive.
     */
    private void showCrisisSupportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("💙 We hear you")
                .setMessage("It sounds like you're going through a tough time. " +
                        "You're not alone — would you like to see some support resources?")
                .setPositiveButton("Yes, show me", (dialog, which) -> {
                    Intent intent = new Intent(this, CrisisActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("I'm okay", (dialog, which) -> {
                    Toast.makeText(this,
                            "Remember: the Support tab is always available if you need it.",
                            Toast.LENGTH_LONG).show();
                })
                .setCancelable(true)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
