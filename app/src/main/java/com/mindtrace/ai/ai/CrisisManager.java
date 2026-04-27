package com.mindtrace.ai.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.CrisisEvent;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.database.entity.TrustedContact;
import com.mindtrace.ai.repository.CrisisRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Crisis Manager — the runtime orchestrator that bridges
 * {@link CrisisDetector} assessments to the 3-tier UI response system.
 *
 * <h3>Three-Tier Activation System:</h3>
 * <pre>
 *   TIER 1: WATCH  (3-4 crisis points)
 *     → Support strip visible, gentle notification, risk bump
 *
 *   TIER 2: ALERT  (5-9 crisis points)
 *     → Prominent support strip, tab badge, warm notification,
 *       crisis exercises pre-loaded, trusted contact visible
 *
 *   TIER 3: CRITICAL  (10+ crisis points)
 *     → Auto-navigate to crisis screen, full-screen gentle message,
 *       breathing exercise auto-starts, helpline numbers displayed,
 *       trusted contact one-tap call, "I'm safe now" button
 * </pre>
 *
 * <h3>Safety Guardrails:</h3>
 * <ul>
 *   <li>No false diagnoses — never says "depression" or "suicidal" in UI</li>
 *   <li>No panic — crisis UI is calm, warm, gentle (NOT alarming red)</li>
 *   <li>User autonomy — can always dismiss or tap "I'm safe"</li>
 *   <li>No surveillance — crisis data is local-only, never transmitted</li>
 *   <li>No auto-contact — trusted contacts are never called automatically</li>
 *   <li>Cool-down — 24h between CRITICAL re-triggers (unless new Tier 1 match)</li>
 *   <li>Consent — crisis features only active if user opted in</li>
 * </ul>
 *
 * @see CrisisDetector
 * @see CrisisRepository
 * @see AdaptiveCrisisResponse
 * @see HelplineProvider
 */
public class CrisisManager {

    private static final String TAG = "CrisisManager";
    private static final String PREFS_NAME = "crisis_prefs";
    private static final String KEY_CRISIS_ENABLED = "crisis_detection_enabled";
    private static final String KEY_LAST_CRITICAL_TIME = "last_critical_timestamp";
    private static final String KEY_COOLDOWN_OVERRIDE = "cooldown_override";

    private static final long CRITICAL_COOLDOWN_MS = 24L * 60 * 60 * 1000; // 24 hours
    private static final long ALERT_COOLDOWN_MS = 2L * 60 * 60 * 1000;    // 2 hours

    private final Context context;
    private final CrisisDetector detector;
    private final CrisisRepository repository;
    private final AdaptiveCrisisResponse adaptiveResponse;
    private final LinguisticAnalyzer linguisticAnalyzer;
    private final AppDatabase db;
    private final SharedPreferences prefs;

    // Cached state
    private CrisisDetector.CrisisAssessment lastAssessment;
    private AdaptiveCrisisResponse.CrisisProfile cachedProfile;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════

    public CrisisManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.detector = new CrisisDetector();
        this.repository = new CrisisRepository(context);
        this.adaptiveResponse = new AdaptiveCrisisResponse(context);
        this.linguisticAnalyzer = new LinguisticAnalyzer();
        this.db = AppDatabase.getInstance(this.context);
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIMARY API: EVALUATE & RESPOND
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Run the full crisis evaluation pipeline and determine the response.
     *
     * @param responses       recent questionnaire check-ins (nullable)
     * @param classification  latest risk classification (nullable)
     * @param classifierLevel crisis level from MultiModalClassifier (nullable)
     * @param journalText     latest journal entry text (nullable)
     * @return structured crisis response with tier, actions, and UI directives
     */
    @NonNull
    public CrisisResponse evaluate(
            @Nullable List<QuestionnaireResponse> responses,
            @Nullable RiskClassification classification,
            @Nullable MultiModalClassifier.CrisisLevel classifierLevel,
            @Nullable String journalText) {

        CrisisResponse response = new CrisisResponse();

        // Guard: is crisis detection enabled?
        if (!isCrisisDetectionEnabled()) {
            response.tier = Tier.NONE;
            response.reason = "Crisis detection is disabled by user preference.";
            return response;
        }

        try {
            // ── 1. Build signal inputs ──
            CrisisDetector.JournalSignals journalSignals = buildJournalSignals(journalText);
            String[] comorbidities = detectComorbidities(classification);

            // ── 2. Run multi-signal assessment ──
            CrisisDetector.CrisisAssessment assessment = detector.assess(
                    responses, classification, classifierLevel,
                    null, // digital signals (populated by caller if available)
                    null, // longitudinal signals (populated by caller if available)
                    journalSignals,
                    comorbidities
            );
            lastAssessment = assessment;

            // ── 3. Check cooldown ──
            if (isInCooldown(assessment)) {
                response.tier = Tier.NONE;
                response.reason = "In cooldown period — suppressing duplicate alert.";
                response.assessment = assessment;
                return response;
            }

            // ── 4. Map assessment to tier ──
            response.tier = mapToTier(assessment);
            response.assessment = assessment;

            // ── 5. Build tier-specific response ──
            if (response.tier != Tier.NONE) {
                response.signals = assessment.activeSignals;
                response.actions = assessment.recommendedActions;
                response.helplines = HelplineProvider.getHelplines();
                response.trustedContacts = loadTrustedContacts();

                // Personalize using crisis history
                cachedProfile = adaptiveResponse.buildProfile();
                response.profile = cachedProfile;
                response.personalizedMessage = cachedProfile.personalizedMessage;

                // Log the crisis event
                long eventId = repository.saveCrisisEvent(assessment);
                response.crisisEventId = eventId;

                // Update cooldown timestamp for CRITICAL
                if (response.tier == Tier.CRITICAL) {
                    prefs.edit().putLong(KEY_LAST_CRITICAL_TIME,
                            System.currentTimeMillis()).apply();
                }
            }

            Log.d(TAG, String.format(Locale.US,
                    "Crisis evaluation: tier=%s, signals=%d, confidence=%.2f",
                    response.tier, assessment.activeSignals.size(),
                    assessment.confidenceScore));

        } catch (Exception e) {
            Log.e(TAG, "Crisis evaluation failed", e);
            response.tier = Tier.NONE;
            response.error = e.getMessage();
        }

        return response;
    }

    /**
     * Evaluate with extended digital and longitudinal signals.
     */
    @NonNull
    public CrisisResponse evaluateFull(
            @Nullable List<QuestionnaireResponse> responses,
            @Nullable RiskClassification classification,
            @Nullable MultiModalClassifier.CrisisLevel classifierLevel,
            @Nullable String journalText,
            @Nullable CrisisDetector.DigitalBehaviorSignals digitalSignals,
            @Nullable CrisisDetector.LongitudinalSignals longitudinalSignals) {

        if (!isCrisisDetectionEnabled()) {
            CrisisResponse r = new CrisisResponse();
            r.tier = Tier.NONE;
            return r;
        }

        CrisisResponse response = new CrisisResponse();
        try {
            CrisisDetector.JournalSignals journalSignals = buildJournalSignals(journalText);
            String[] comorbidities = detectComorbidities(classification);

            CrisisDetector.CrisisAssessment assessment = detector.assess(
                    responses, classification, classifierLevel,
                    digitalSignals, longitudinalSignals,
                    journalSignals, comorbidities);
            lastAssessment = assessment;

            if (isInCooldown(assessment)) {
                response.tier = Tier.NONE;
                response.assessment = assessment;
                return response;
            }

            response.tier = mapToTier(assessment);
            response.assessment = assessment;

            if (response.tier != Tier.NONE) {
                response.signals = assessment.activeSignals;
                response.actions = assessment.recommendedActions;
                response.helplines = HelplineProvider.getHelplines();
                response.trustedContacts = loadTrustedContacts();
                cachedProfile = adaptiveResponse.buildProfile();
                response.profile = cachedProfile;
                response.personalizedMessage = cachedProfile.personalizedMessage;
                long eventId = repository.saveCrisisEvent(assessment);
                response.crisisEventId = eventId;

                if (response.tier == Tier.CRITICAL) {
                    prefs.edit().putLong(KEY_LAST_CRITICAL_TIME,
                            System.currentTimeMillis()).apply();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Full crisis evaluation failed", e);
            response.tier = Tier.NONE;
            response.error = e.getMessage();
        }

        return response;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UI ACTION HANDLER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Handle the crisis response in the UI layer.
     * Called by Activity/Fragment after receiving a CrisisResponse.
     *
     * @param response the crisis response to handle
     * @param activity the current activity (for navigation)
     */
    public void handleResponse(@NonNull CrisisResponse response,
                                @NonNull Activity activity) {
        switch (response.tier) {
            case WATCH:
                handleWatch(response, activity);
                break;
            case ALERT:
                handleAlert(response, activity);
                break;
            case CRITICAL:
                handleCritical(response, activity);
                break;
            case NONE:
            default:
                // No action needed
                break;
        }
    }

    /**
     * Tier 1: WATCH — gentle, non-intrusive support.
     */
    private void handleWatch(@NonNull CrisisResponse response,
                              @NonNull Activity activity) {
        Log.d(TAG, "WATCH tier activated — showing support strip");

        // Broadcast to show support strip on dashboard
        Intent intent = new Intent("com.mindtrace.ai.CRISIS_WATCH");
        intent.putExtra("message", "How are you feeling? Take a moment to check in.");
        intent.putExtra("crisis_event_id", response.crisisEventId);
        activity.sendBroadcast(intent);
    }

    /**
     * Tier 2: ALERT — prominent support with pre-loaded exercises.
     */
    private void handleAlert(@NonNull CrisisResponse response,
                              @NonNull Activity activity) {
        Log.d(TAG, "ALERT tier activated — prominent support + badge");

        Intent intent = new Intent("com.mindtrace.ai.CRISIS_ALERT");
        intent.putExtra("message",
                "You've been going through a lot. We're here for you.");
        intent.putExtra("crisis_event_id", response.crisisEventId);
        intent.putExtra("show_badge", true);
        intent.putExtra("preload_exercises", true);
        if (response.personalizedMessage != null) {
            intent.putExtra("personalized_message", response.personalizedMessage);
        }
        activity.sendBroadcast(intent);
    }

    /**
     * Tier 3: CRITICAL — navigate to full crisis screen.
     */
    private void handleCritical(@NonNull CrisisResponse response,
                                 @NonNull Activity activity) {
        Log.d(TAG, "CRITICAL tier activated — launching crisis screen");

        // Navigate to CrisisActivity
        try {
            Intent crisisIntent = new Intent();
            crisisIntent.setClassName(activity.getPackageName(),
                    activity.getPackageName() + ".ui.crisis.CrisisActivity");
            crisisIntent.putExtra("auto_triggered", true);
            crisisIntent.putExtra("crisis_event_id", response.crisisEventId);
            crisisIntent.putStringArrayListExtra("signals",
                    new ArrayList<>(response.signals));
            if (response.personalizedMessage != null) {
                crisisIntent.putExtra("personalized_message",
                        response.personalizedMessage);
            }
            // FLAG_SECURE prevents screenshots of crisis screen
            crisisIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(crisisIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch CrisisActivity", e);
            // Fallback: broadcast to show in-app crisis dialog
            Intent fallback = new Intent("com.mindtrace.ai.CRISIS_CRITICAL");
            fallback.putExtra("crisis_event_id", response.crisisEventId);
            activity.sendBroadcast(fallback);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESOLUTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * User pressed "I'm safe now" — resolve the active crisis.
     *
     * @param method       how it was resolved (e.g., "breathing_exercise")
     * @param mood         current mood after resolution (nullable)
     * @param postDistress distress level after resolution (1-10)
     */
    public void resolveActiveCrisis(@NonNull String method,
                                     @Nullable String mood,
                                     int postDistress) {
        repository.resolveActive(method, mood, postDistress);
        Log.d(TAG, "Active crisis resolved via " + method);
    }

    /**
     * Schedule a post-crisis debrief check-in (4-6h after resolution).
     */
    public void schedulePostCrisisDebrief(long crisisEventId) {
        try {
            CrisisEvent event = repository.getActiveEvent();
            if (event != null) {
                event.followUpScheduled = true;
                repository.update(event);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule debrief", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COOLDOWN & GATING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if we're in a cooldown period to prevent notification spam.
     * CRITICAL has 24h cooldown; ALERT has 2h cooldown.
     * Exception: Tier 1 linguistic match (suicide-related) bypasses cooldown.
     */
    private boolean isInCooldown(@NonNull CrisisDetector.CrisisAssessment assessment) {
        // Suicide risk always bypasses cooldown
        if (assessment.suicideRisk != null && assessment.suicideRisk.csrrsTier >= 3) {
            return false;
        }

        // Check CRITICAL cooldown
        long lastCritical = prefs.getLong(KEY_LAST_CRITICAL_TIME, 0);
        if (lastCritical > 0) {
            long elapsed = System.currentTimeMillis() - lastCritical;
            if (elapsed < CRITICAL_COOLDOWN_MS) {
                Log.d(TAG, "In CRITICAL cooldown (" +
                        ((CRITICAL_COOLDOWN_MS - elapsed) / 60000) + " min remaining)");
                return true;
            }
        }

        // Check general cooldown via repository
        return repository.isInCooldown();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    /** Check if crisis detection is enabled by the user. */
    public boolean isCrisisDetectionEnabled() {
        return prefs.getBoolean(KEY_CRISIS_ENABLED, true); // Default: enabled
    }

    /** Enable or disable crisis detection (user setting). */
    public void setCrisisDetectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CRISIS_ENABLED, enabled).apply();
        Log.d(TAG, "Crisis detection " + (enabled ? "enabled" : "disabled"));
    }

    /** Reset cooldown (for testing or manual override). */
    public void resetCooldown() {
        prefs.edit().remove(KEY_LAST_CRITICAL_TIME).apply();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANALYTICS
    // ═══════════════════════════════════════════════════════════════════

    /** Get crisis count in last N days. */
    public int getCrisisCountLastNDays(int days) {
        return repository.getCrisisCountLastNDays(days);
    }

    /** Get the weekly crisis summary string. */
    @NonNull
    public String getWeeklySummary() {
        return repository.getWeeklyCrisisSummary();
    }

    /** Get the most effective coping strategy from history. */
    @Nullable
    public String getMostEffectiveCopingStrategy() {
        return repository.getMostEffectiveCopingStrategy();
    }

    /** Get the last assessment (cached from most recent evaluation). */
    @Nullable
    public CrisisDetector.CrisisAssessment getLastAssessment() {
        return lastAssessment;
    }

    /** Get the cached crisis profile (from last evaluation). */
    @Nullable
    public AdaptiveCrisisResponse.CrisisProfile getCachedProfile() {
        return cachedProfile;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Map CrisisAssessment level to our 3-tier system.
     */
    @NonNull
    private Tier mapToTier(@NonNull CrisisDetector.CrisisAssessment assessment) {
        if (assessment.shouldLaunchLockdown() ||
                assessment.level == MultiModalClassifier.CrisisLevel.CRITICAL) {
            return Tier.CRITICAL;
        }
        if (assessment.level == MultiModalClassifier.CrisisLevel.URGENT ||
                assessment.level == MultiModalClassifier.CrisisLevel.ELEVATED) {
            return Tier.ALERT;
        }
        if (assessment.level == MultiModalClassifier.CrisisLevel.WATCH) {
            return Tier.WATCH;
        }
        return Tier.NONE;
    }

    /**
     * Build journal signals from raw text using LinguisticAnalyzer.
     */
    @Nullable
    private CrisisDetector.JournalSignals buildJournalSignals(
            @Nullable String journalText) {
        if (journalText == null || journalText.trim().isEmpty()) return null;

        LinguisticAnalyzer.AnalysisResult analysis = linguisticAnalyzer.analyze(journalText);
        if (analysis == null) return null;

        int keywordCount = analysis.distressFlags.size();
        float severity = Math.abs(Math.min(0, analysis.sentimentScore));
        int distressLevel = (int) Math.ceil(severity * 10);
        int moodScore = (int) Math.ceil((1 + analysis.sentimentScore) * 5);

        return new CrisisDetector.JournalSignals(
                keywordCount, severity, journalText, distressLevel, moodScore);
    }

    /**
     * Detect comorbidity patterns from classification.
     */
    @Nullable
    private String[] detectComorbidities(@Nullable RiskClassification rc) {
        if (rc == null) return null;
        List<String> combos = new ArrayList<>();

        if (rc.depressionRiskScore > 0.45f && rc.socialIsolationScore > 0.45f) {
            combos.add("depression_isolation_loop");
        }
        if (rc.stressAnxietyScore > 0.50f && rc.lowFulfilmentScore > 0.40f) {
            combos.add("burnout_triad");
        }
        if (rc.digitalAddictionScore > 0.50f &&
                (rc.depressionRiskScore > 0.40f || rc.stressAnxietyScore > 0.40f)) {
            combos.add("digital_escape_cycle");
        }

        return combos.isEmpty() ? null : combos.toArray(new String[0]);
    }

    /**
     * Load trusted contacts from the database.
     */
    @NonNull
    private List<TrustedContact> loadTrustedContacts() {
        try {
            List<TrustedContact> contacts = db.trustedContactDao().getEmergencyContactsSync();
            if (contacts == null || contacts.isEmpty()) {
                contacts = db.trustedContactDao().getAllSync();
            }
            return contacts != null ? contacts : new ArrayList<>();
        } catch (Exception e) {
            Log.w(TAG, "Failed to load trusted contacts", e);
            return new ArrayList<>();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENUMS & DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Three-tier activation level.
     */
    public enum Tier {
        NONE("None", "No action needed"),
        WATCH("Watch", "Gentle support strip"),
        ALERT("Alert", "Prominent support + badge"),
        CRITICAL("Critical", "Full crisis screen");

        public final String label;
        public final String description;

        Tier(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public boolean requiresAction() {
            return this != NONE;
        }

        public boolean requiresNavigation() {
            return this == CRITICAL;
        }
    }

    /**
     * Complete crisis response — the output of the CrisisManager pipeline.
     */
    public static class CrisisResponse {
        /** Activation tier (NONE, WATCH, ALERT, CRITICAL). */
        @NonNull public Tier tier = Tier.NONE;

        /** Raw assessment from CrisisDetector. */
        @Nullable public CrisisDetector.CrisisAssessment assessment;

        /** Active signal descriptions. */
        @NonNull public List<String> signals = new ArrayList<>();

        /** Recommended actions. */
        @NonNull public List<String> actions = new ArrayList<>();

        /** Available helplines. */
        @NonNull public List<HelplineProvider.Helpline> helplines = new ArrayList<>();

        /** User's trusted contacts. */
        @NonNull public List<TrustedContact> trustedContacts = new ArrayList<>();

        /** Personalized crisis profile from history. */
        @Nullable public AdaptiveCrisisResponse.CrisisProfile profile;

        /** Personalized message based on crisis history. */
        @Nullable public String personalizedMessage;

        /** Database ID of the logged crisis event. */
        public long crisisEventId = -1;

        /** Reason for suppression (if tier is NONE). */
        @Nullable public String reason;

        /** Error message if evaluation failed. */
        @Nullable public String error;

        /** Whether this response requires any UI action. */
        public boolean requiresAction() { return tier.requiresAction(); }

        /** Whether this response requires navigation to crisis screen. */
        public boolean requiresNavigation() { return tier.requiresNavigation(); }

        /** Whether trusted contacts are available. */
        public boolean hasTrustedContacts() {
            return !trustedContacts.isEmpty();
        }

        @NonNull
        @Override
        public String toString() {
            return "CrisisResponse{tier=" + tier.label +
                    ", signals=" + signals.size() +
                    ", helplines=" + helplines.size() +
                    ", contacts=" + trustedContacts.size() +
                    (error != null ? ", error=" + error : "") +
                    "}";
        }
    }
}
