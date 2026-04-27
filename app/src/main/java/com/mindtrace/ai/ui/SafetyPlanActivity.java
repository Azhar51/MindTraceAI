package com.mindtrace.ai.ui;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.SafetyPlan;
import com.mindtrace.ai.viewmodel.CrisisViewModel;

/**
 * Guided Safety Plan editor — Stanley-Brown model with 6 clinical sections.
 *
 * <p>Sections:</p>
 * <ol>
 *   <li>Warning Signs — thoughts, feelings, situations</li>
 *   <li>Coping Strategies — what helps calm you down</li>
 *   <li>Reasons to Live — people, goals, things that matter</li>
 *   <li>Trusted Contacts — personal support network</li>
 *   <li>Professional Contacts — therapist, doctor</li>
 *   <li>Safe Environments — places that feel safe</li>
 * </ol>
 *
 * <p>Auto-saves on each keystroke. Completion % updates in real-time.</p>
 */
public class SafetyPlanActivity extends AppCompatActivity {

    private CrisisViewModel crisisViewModel;
    private TextInputEditText etWarningSigns, etCoping, etReasons;
    private TextInputEditText etTrusted, etProfessional, etSafePlaces;
    private ProgressBar progressPlan;
    private TextView tvCompletion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_plan);

        crisisViewModel = new ViewModelProvider(this).get(CrisisViewModel.class);

        bindViews();
        observeSafetyPlan();
        setupSaveButton();
        setupAutoCompletion();

        // ── CTA pulse glow on save button ──
        UiMotion.pulseGlow(findViewById(R.id.btn_save));
    }

    private void bindViews() {
        etWarningSigns = findViewById(R.id.et_warning_signs);
        etCoping = findViewById(R.id.et_coping);
        etReasons = findViewById(R.id.et_reasons);
        etTrusted = findViewById(R.id.et_trusted);
        etProfessional = findViewById(R.id.et_professional);
        etSafePlaces = findViewById(R.id.et_safe_places);
        progressPlan = findViewById(R.id.progress_plan);
        tvCompletion = findViewById(R.id.tv_completion);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOAD EXISTING PLAN
    // ═══════════════════════════════════════════════════════════════════

    private void observeSafetyPlan() {
        crisisViewModel.getSafetyPlan().observe(this, plan -> {
            if (plan != null) {
                setTextIfNotEmpty(etWarningSigns, jsonToText(plan.warningSignalsJson));
                setTextIfNotEmpty(etCoping, jsonToText(plan.copingStrategiesJson));
                setTextIfNotEmpty(etReasons, jsonToText(plan.reasonsToLiveJson));
                setTextIfNotEmpty(etTrusted, jsonToText(plan.trustedContactsJson));
                setTextIfNotEmpty(etProfessional, jsonToText(plan.professionalContactsJson));
                setTextIfNotEmpty(etSafePlaces, jsonToText(plan.safeEnvironmentsJson));
                updateCompletion();
            }
        });
    }

    private void setTextIfNotEmpty(TextInputEditText et, String text) {
        if (text != null && !text.isEmpty() &&
                (et.getText() == null || et.getText().toString().isEmpty())) {
            et.setText(text);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SAVE
    // ═══════════════════════════════════════════════════════════════════

    private void setupSaveButton() {
        MaterialButton btnSave = findViewById(R.id.btn_save);
        UiMotion.attachPressAnimation(btnSave);
        btnSave.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            SafetyPlan plan = buildPlanFromFields();
            crisisViewModel.saveSafetyPlan(plan);

            int percent = plan.getCompletionPercent();
            Toast.makeText(this,
                    "🛡️ Safety plan saved (" + percent + "% complete)",
                    Toast.LENGTH_SHORT).show();

            if (percent == 100) {
                UiMotion.hapticHeavy(v);
                UiMotion.confettiBurst(v, 16);
            }
        });
    }

    private SafetyPlan buildPlanFromFields() {
        SafetyPlan plan = new SafetyPlan();
        plan.id = 1; // Singleton

        String warning = getText(etWarningSigns);
        String coping = getText(etCoping);
        String reasons = getText(etReasons);
        String trusted = getText(etTrusted);
        String professional = getText(etProfessional);
        String safePlaces = getText(etSafePlaces);

        plan.warningSignalsJson = textToJson(warning);
        plan.copingStrategiesJson = textToJson(coping);
        plan.reasonsToLiveJson = textToJson(reasons);
        plan.trustedContactsJson = textToJson(trusted);
        plan.professionalContactsJson = textToJson(professional);
        plan.safeEnvironmentsJson = textToJson(safePlaces);

        return plan;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLETION TRACKING
    // ═══════════════════════════════════════════════════════════════════

    private void setupAutoCompletion() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateCompletion(); }
        };

        etWarningSigns.addTextChangedListener(watcher);
        etCoping.addTextChangedListener(watcher);
        etReasons.addTextChangedListener(watcher);
        etTrusted.addTextChangedListener(watcher);
        etProfessional.addTextChangedListener(watcher);
        etSafePlaces.addTextChangedListener(watcher);
    }

    private int lastPercent = 0;

    private void updateCompletion() {
        int filled = 0;
        if (hasContent(etWarningSigns)) filled++;
        if (hasContent(etCoping)) filled++;
        if (hasContent(etReasons)) filled++;
        if (hasContent(etTrusted)) filled++;
        if (hasContent(etProfessional)) filled++;
        if (hasContent(etSafePlaces)) filled++;

        int percent = (filled * 100) / 6;

        // Animated progress bar fill
        ValueAnimator anim = ValueAnimator.ofInt(progressPlan.getProgress(), percent);
        anim.setDuration(400);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(a -> progressPlan.setProgress((int) a.getAnimatedValue()));
        anim.start();

        tvCompletion.setText(percent + "% complete");

        // Milestone haptics
        if (percent >= 50 && lastPercent < 50) UiMotion.hapticClick(progressPlan);
        if (percent >= 75 && lastPercent < 75) UiMotion.hapticClick(progressPlan);
        if (percent == 100 && lastPercent < 100) {
            UiMotion.hapticHeavy(progressPlan);
            UiMotion.confettiBurst(progressPlan, 14);
        }
        lastPercent = percent;
    }

    private boolean hasContent(TextInputEditText et) {
        return et.getText() != null && et.getText().toString().trim().length() > 2;
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    // ═══════════════════════════════════════════════════════════════════
    // JSON CONVERSION (comma-separated ↔ JSON array)
    // ═══════════════════════════════════════════════════════════════════

    private String textToJson(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] items = text.split(",|\\n");
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                if (!first) sb.append(",");
                sb.append("\"").append(trimmed.replace("\"", "'")).append("\"");
                first = false;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonToText(String json) {
        if (json == null || json.isEmpty()) return "";
        return json.replaceAll("[\\[\\]\"]", "")
                .replace(",", "\n");
    }
}
