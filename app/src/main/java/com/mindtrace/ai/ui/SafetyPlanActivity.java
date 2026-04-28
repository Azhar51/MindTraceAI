package com.mindtrace.ai.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.mindtrace.ai.R;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.SafetyPlan;
import com.mindtrace.ai.database.entity.TrustedContact;
import com.mindtrace.ai.databinding.ActivitySafetyPlanBinding;
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
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class SafetyPlanActivity extends AppCompatActivity {

    private ActivitySafetyPlanBinding binding;
    private CrisisViewModel crisisViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySafetyPlanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        crisisViewModel = new ViewModelProvider(this).get(CrisisViewModel.class);

        observeSafetyPlan();
        setupSaveButton();
        setupShareButton();
        setupAutoCompletion();

        binding.btnBack.setOnClickListener(v -> finish());

        // CTA pulse glow on save button
        UiMotion.pulseGlow(binding.btnSave);
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOAD EXISTING PLAN
    // ═══════════════════════════════════════════════════════════════════

    private void observeSafetyPlan() {
        crisisViewModel.getSafetyPlan().observe(this, plan -> {
            if (plan != null) {
                setTextIfNotEmpty(binding.etWarningSigns, jsonToText(plan.warningSignalsJson));
                setTextIfNotEmpty(binding.etCoping, jsonToText(plan.copingStrategiesJson));
                setTextIfNotEmpty(binding.etReasons, jsonToText(plan.reasonsToLiveJson));
                setTextIfNotEmpty(binding.etTrusted, jsonToText(plan.trustedContactsJson));
                setTextIfNotEmpty(binding.etProfessional, jsonToText(plan.professionalContactsJson));
                setTextIfNotEmpty(binding.etSafePlaces, jsonToText(plan.safeEnvironmentsJson));
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
        UiMotion.attachPressAnimation(binding.btnSave);
        binding.btnSave.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            SafetyPlan plan = buildPlanFromFields();
            crisisViewModel.saveSafetyPlan(plan);

            // Auto-sync trusted contacts from Section 4 into DB
            syncTrustedContactsToDb(getText(binding.etTrusted));

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

        String warning = getText(binding.etWarningSigns);
        String coping = getText(binding.etCoping);
        String reasons = getText(binding.etReasons);
        String trusted = getText(binding.etTrusted);
        String professional = getText(binding.etProfessional);
        String safePlaces = getText(binding.etSafePlaces);

        plan.warningSignalsJson = textToJson(warning);
        plan.copingStrategiesJson = textToJson(coping);
        plan.reasonsToLiveJson = textToJson(reasons);
        plan.trustedContactsJson = textToJson(trusted);
        plan.professionalContactsJson = textToJson(professional);
        plan.safeEnvironmentsJson = textToJson(safePlaces);

        long now = System.currentTimeMillis();
        plan.updatedAt = now;
        // Preserve creation time if re-saving, ViewModel handles this too
        plan.isComplete = plan.getCompletionPercent() == 100;

        return plan;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHARE
    // ═══════════════════════════════════════════════════════════════════

    private void setupShareButton() {
        binding.btnBack.setOnLongClickListener(v -> {
            SafetyPlan plan = buildPlanFromFields();
            if (plan.hasContent()) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My Safety Plan");
                shareIntent.putExtra(Intent.EXTRA_TEXT, plan.toShareableText());
                startActivity(Intent.createChooser(shareIntent, "Share safety plan with..."));
            } else {
                Toast.makeText(this, "Add some content first before sharing.",
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRUSTED CONTACT SYNC
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Parses free-text trusted contacts from Section 4 and syncs them
     * into the structured TrustedContact table for crisis auto-notification.
     *
     * <p>Format detection: "Name: Phone" or "Name (Relationship): Phone"
     * Falls back to storing the raw text as name if no phone is found.</p>
     */
    private void syncTrustedContactsToDb(String trustedText) {
        if (trustedText == null || trustedText.trim().isEmpty()) return;

        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                String[] lines = trustedText.split(",|\\n");

                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    TrustedContact contact = parseContactLine(trimmed);
                    if (contact != null && !contact.name.isEmpty()) {
                        java.util.List<TrustedContact> existing =
                                db.trustedContactDao().getAllSync();
                        boolean alreadyExists = false;
                        for (TrustedContact c : existing) {
                            if (c.name.equalsIgnoreCase(contact.name)) {
                                alreadyExists = true;
                                break;
                            }
                        }
                        if (!alreadyExists) {
                            contact.createdAt = System.currentTimeMillis();
                            contact.notifyOnCrisis = true;
                            db.trustedContactDao().insert(contact);
                            Log.d("SafetyPlanActivity",
                                    "Synced contact: " + contact.getDisplayLabel());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("SafetyPlanActivity", "Failed to sync trusted contacts", e);
            }
        });
    }

    /**
     * Parse a contact line like "Mom: 555-1234" or "Sarah (Friend)".
     */
    private TrustedContact parseContactLine(String line) {
        TrustedContact c = new TrustedContact();

        if (line.contains(":")) {
            String[] parts = line.split(":", 2);
            c.name = parts[0].trim();
            if (parts.length > 1) {
                c.phone = parts[1].trim().replaceAll("[^0-9+\\-() ]", "");
            }
        } else {
            c.name = line;
        }

        if (c.name.contains("(") && c.name.contains(")")) {
            int start = c.name.indexOf('(');
            int end = c.name.indexOf(')');
            if (end > start) {
                c.relationship = c.name.substring(start + 1, end).trim();
                c.name = c.name.substring(0, start).trim();
            }
        } else {
            c.relationship = "Safety Plan";
        }

        return c;
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

        binding.etWarningSigns.addTextChangedListener(watcher);
        binding.etCoping.addTextChangedListener(watcher);
        binding.etReasons.addTextChangedListener(watcher);
        binding.etTrusted.addTextChangedListener(watcher);
        binding.etProfessional.addTextChangedListener(watcher);
        binding.etSafePlaces.addTextChangedListener(watcher);
    }

    private int lastPercent = 0;

    private void updateCompletion() {
        int filled = 0;
        if (hasContent(binding.etWarningSigns)) filled++;
        if (hasContent(binding.etCoping)) filled++;
        if (hasContent(binding.etReasons)) filled++;
        if (hasContent(binding.etTrusted)) filled++;
        if (hasContent(binding.etProfessional)) filled++;
        if (hasContent(binding.etSafePlaces)) filled++;

        int percent = (filled * 100) / 6;

        ValueAnimator anim = ValueAnimator.ofInt(binding.progressPlan.getProgress(), percent);
        anim.setDuration(400);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(a -> binding.progressPlan.setProgress((int) a.getAnimatedValue()));
        anim.start();

        binding.tvCompletion.setText(percent + "% complete");

        if (percent >= 50 && lastPercent < 50) UiMotion.hapticClick(binding.progressPlan);
        if (percent >= 75 && lastPercent < 75) UiMotion.hapticClick(binding.progressPlan);
        if (percent == 100 && lastPercent < 100) {
            UiMotion.hapticHeavy(binding.progressPlan);
            UiMotion.confettiBurst(binding.progressPlan, 14);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
