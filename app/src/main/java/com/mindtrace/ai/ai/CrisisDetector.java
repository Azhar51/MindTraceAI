package com.mindtrace.ai.ai;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.RiskClassification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Multi-signal crisis detection engine.
 *
 * <h3>Signal Sources (6 channels):</h3>
 * <ol>
 *   <li><b>Questionnaire</b> — mood patterns, stress spikes, zero motivation</li>
 *   <li><b>Classification</b> — RiskClassification.crisisFlag + CrisisLevel</li>
 *   <li><b>Digital behaviour</b> — extreme night usage, 3x screen time spike, 150+ unlocks</li>
 *   <li><b>Longitudinal</b> — 3+ consecutive worsening days</li>
 *   <li><b>Journal</b> — crisis keyword detection (via LinguisticAnalyzer)</li>
 *   <li><b>Comorbidity</b> — depression+isolation loop, burnout triad patterns</li>
 * </ol>
 *
 * <h3>Output:</h3>
 * Structured {@link CrisisAssessment} with urgency level, active signals, recommended
 * actions, and a human-readable summary for the UI.
 */
public class CrisisDetector {

    private static final String TAG = "CrisisDetector";

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS ASSESSMENT DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    public static class CrisisAssessment {
        public final MultiModalClassifier.CrisisLevel level;
        public final List<String> activeSignals;
        public final List<String> recommendedActions;
        public final float confidenceScore;
        public final long timestamp;

        /** Suicide risk assessment (null if not evaluated) */
        public SuicideRiskClassifier.SuicideRiskAssessment suicideRisk;

        CrisisAssessment(MultiModalClassifier.CrisisLevel level,
                         List<String> signals, List<String> actions, float confidence) {
            this.level = level;
            this.activeSignals = signals;
            this.recommendedActions = actions;
            this.confidenceScore = confidence;
            this.timestamp = System.currentTimeMillis();
        }

        /** Generate a human-readable summary for the UI. */
        @NonNull
        public String toHumanSummary() {
            if (level == MultiModalClassifier.CrisisLevel.NONE) {
                return "No crisis indicators detected.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Crisis level: ").append(level.label).append(".\n");
            sb.append("Active signals: ").append(String.join(", ", activeSignals)).append(".\n");
            if (!recommendedActions.isEmpty()) {
                sb.append("Recommended: ").append(recommendedActions.get(0)).append(".");
            }
            return sb.toString();
        }

        /** Whether this assessment should trigger automatic UI navigation. */
        public boolean shouldAutoLaunchCrisisScreen() {
            return level.requiresImmediateAction();
        }

        /** Whether this assessment should fire a notification. */
        public boolean shouldNotify() {
            return level.requiresImmediateAction() ||
                    (suicideRisk != null && suicideRisk.shouldNotify);
        }

        /** Whether this should launch CrisisLockdownActivity instead of CrisisActivity. */
        public boolean shouldLaunchLockdown() {
            return suicideRisk != null && suicideRisk.shouldLockdown;
        }

        /** Get C-SSRS tier (0 if not evaluated). */
        public int getCsrrsTier() {
            return suicideRisk != null ? suicideRisk.csrrsTier : 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEGACY (kept for backward compat)
    // ═══════════════════════════════════════════════════════════════════

    public boolean isCrisisDetected(List<QuestionnaireResponse> responses) {
        CrisisAssessment assessment = assessFromQuestionnaire(responses);
        return assessment.level.requiresMonitoring();
    }

    // ═══════════════════════════════════════════════════════════════════
    // FULL MULTI-SIGNAL ASSESSMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Comprehensive crisis assessment from all available signals.
     */
    @NonNull
    public CrisisAssessment assess(
            @Nullable List<QuestionnaireResponse> responses,
            @Nullable RiskClassification latestClassification,
            @Nullable MultiModalClassifier.CrisisLevel classifierLevel,
            @Nullable DigitalBehaviorSignals digitalSignals,
            @Nullable LongitudinalSignals longitudinalSignals,
            @Nullable JournalSignals journalSignals,
            @Nullable String[] comorbidities
    ) {
        List<String> signals = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        int urgencyScore = 0;

        // ── 1. Questionnaire signals ──
        urgencyScore += assessQuestionnaire(responses, signals, actions);

        // ── 2. Classification signals ──
        urgencyScore += assessClassification(latestClassification, classifierLevel, signals, actions);

        // ── 3. Digital behaviour signals ──
        urgencyScore += assessDigitalBehavior(digitalSignals, signals, actions);

        // ── 4. Longitudinal signals ──
        urgencyScore += assessLongitudinal(longitudinalSignals, signals, actions);

        // ── 5. Journal signals ──
        urgencyScore += assessJournal(journalSignals, signals, actions);

        // ── 6. Comorbidity signals ──
        urgencyScore += assessComorbidity(comorbidities, signals, actions);

        // ── 7. Suicide risk assessment (via journal text) ──
        SuicideRiskClassifier.SuicideRiskAssessment suicideRisk = null;
        if (journalSignals != null && journalSignals.rawText != null) {
            SuicideRiskClassifier src = new SuicideRiskClassifier();
            boolean isNight = digitalSignals != null && digitalSignals.isNightOwl;
            int crisisCount = longitudinalSignals != null ? longitudinalSignals.worseningDays : 0;
            boolean isolated = comorbidities != null && containsComorbidity(comorbidities, "isolation");
            suicideRisk = src.assess(
                    journalSignals.rawText,
                    journalSignals.distressLevel,
                    journalSignals.moodScore,
                    isNight, crisisCount, isolated);

            if (suicideRisk.csrrsTier >= 4) {
                urgencyScore = Math.max(urgencyScore, 12); // Force CRITICAL
                signals.add("🆘 SUICIDE RISK: " + suicideRisk.getSeverityLabel());
                actions.clear();
                actions.add("IMMEDIATE: Call 988 Suicide & Crisis Lifeline");
                actions.add("Launch crisis lockdown mode");
            } else if (suicideRisk.csrrsTier >= 2) {
                urgencyScore = Math.max(urgencyScore, 8); // Force at least URGENT
                signals.add("⚠️ Suicide risk: " + suicideRisk.getSeverityLabel());
            } else if (suicideRisk.csrrsTier >= 1) {
                urgencyScore = Math.max(urgencyScore, 5); // Force at least ELEVATED
                signals.add("Passive ideation signals detected");
            }
        }

        // ── Determine level from composite score ──
        MultiModalClassifier.CrisisLevel level;
        if (urgencyScore >= 10) level = MultiModalClassifier.CrisisLevel.CRITICAL;
        else if (urgencyScore >= 7) level = MultiModalClassifier.CrisisLevel.URGENT;
        else if (urgencyScore >= 4) level = MultiModalClassifier.CrisisLevel.ELEVATED;
        else if (urgencyScore >= 2) level = MultiModalClassifier.CrisisLevel.WATCH;
        else level = MultiModalClassifier.CrisisLevel.NONE;

        // Confidence based on number of signal sources available
        int sourcesAvailable = 0;
        if (responses != null && !responses.isEmpty()) sourcesAvailable++;
        if (latestClassification != null) sourcesAvailable++;
        if (digitalSignals != null) sourcesAvailable++;
        if (longitudinalSignals != null) sourcesAvailable++;
        if (journalSignals != null) sourcesAvailable++;
        if (suicideRisk != null) sourcesAvailable++;
        float confidence = Math.min(1.0f, sourcesAvailable / 6.0f);

        // Add default actions based on level
        if (level.requiresImmediateAction() && actions.isEmpty()) {
            actions.add("Open crisis support resources");
            actions.add("Consider reaching out to a trusted contact");
        } else if (level.requiresMonitoring() && actions.isEmpty()) {
            actions.add("Try a breathing exercise");
            actions.add("Journal about what you're feeling");
        }

        Log.d(TAG, String.format(Locale.US, "Assessment: %s (score=%d, signals=%d, confidence=%.2f, cssrs=%d)",
                level.label, urgencyScore, signals.size(), confidence,
                suicideRisk != null ? suicideRisk.csrrsTier : 0));

        CrisisAssessment assessment = new CrisisAssessment(level, signals, actions, confidence);
        assessment.suicideRisk = suicideRisk;
        return assessment;
    }

    /**
     * Quick assessment from questionnaire data only (backward compatible).
     */
    @NonNull
    public CrisisAssessment assessFromQuestionnaire(@Nullable List<QuestionnaireResponse> responses) {
        return assess(responses, null, null, null, null, null, null);
    }

    /**
     * Assessment from classification + questionnaire (common pipeline path).
     */
    @NonNull
    public CrisisAssessment assessFromClassification(
            @Nullable List<QuestionnaireResponse> responses,
            @NonNull RiskClassification classification,
            @NonNull MultiModalClassifier.CrisisLevel classifierLevel
    ) {
        return assess(responses, classification, classifierLevel, null, null, null, null);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SIGNAL ASSESSMENT METHODS
    // ═══════════════════════════════════════════════════════════════════

    private int assessQuestionnaire(@Nullable List<QuestionnaireResponse> responses,
                                     List<String> signals, List<String> actions) {
        if (responses == null || responses.size() < 3) return 0;
        int score = 0;

        // Pattern 1: Repeated very low mood + high loneliness
        int lowMoodCount = 0, highLonelinessCount = 0;
        for (int i = 0; i < Math.min(3, responses.size()); i++) {
            QuestionnaireResponse r = responses.get(i);
            if ("Sad".equalsIgnoreCase(r.mood)) lowMoodCount++;
            if (r.lonelinessLevel >= 4) highLonelinessCount++;
        }
        if (lowMoodCount >= 3 && highLonelinessCount >= 2) {
            signals.add("3+ consecutive sad days with high loneliness");
            actions.add("Reach out to someone you trust");
            score += 4;
        }

        // Pattern 2: Severe stress + low motivation + high loneliness
        QuestionnaireResponse latest = responses.get(0);
        if (latest.stressLevel >= 5 && latest.motivationLevel <= 1 && latest.lonelinessLevel >= 4) {
            signals.add("Max stress + zero motivation + high loneliness");
            actions.add("Try a grounding exercise first");
            score += 3;
        }

        // Pattern 3: Sad + maxed stress + min motivation
        if ("Sad".equalsIgnoreCase(latest.mood) && latest.stressLevel >= 5 && latest.motivationLevel <= 1) {
            signals.add("Sad mood with extreme stress and no motivation");
            score += 3;
        }

        // Pattern 4: Anxious mood + high stress + overwhelm
        if ("Anxious".equalsIgnoreCase(latest.mood) && latest.stressLevel >= 4) {
            signals.add("Anxious mood with elevated stress");
            score += 2;
        }

        // Pattern 5: Requested support in check-in
        if (latest.requestedSupport) {
            signals.add("User explicitly requested support");
            actions.add("Open support resources immediately");
            score += 4;
        }

        return score;
    }

    private int assessClassification(@Nullable RiskClassification classification,
                                      @Nullable MultiModalClassifier.CrisisLevel classifierLevel,
                                      List<String> signals, List<String> actions) {
        if (classification == null) return 0;
        int score = 0;

        if (classification.crisisFlag) {
            signals.add("AI classification flagged crisis");
            score += 3;
        }

        if (classification.depressionRiskScore > 0.85f) {
            signals.add("Depression risk score critically high (" +
                    String.format(Locale.US, "%.0f%%", classification.depressionRiskScore * 100) + ")");
            score += 2;
        }

        if (classification.overallRiskScore > 0.80f) {
            signals.add("Overall risk score critically high");
            score += 2;
        }

        if (classifierLevel != null && classifierLevel.requiresImmediateAction()) {
            signals.add("Classifier crisis level: " + classifierLevel.label);
            actions.add("Auto-launch crisis support screen");
            score += 3;
        }

        return score;
    }

    private int assessDigitalBehavior(@Nullable DigitalBehaviorSignals digitalSignals,
                                       List<String> signals, List<String> actions) {
        if (digitalSignals == null) return 0;
        int score = 0;

        if (digitalSignals.nightUsageMinutes > 120) {
            signals.add("Extreme night usage (" + digitalSignals.nightUsageMinutes + " min after midnight)");
            actions.add("Consider a wind-down routine");
            score += 2;
        }

        if (digitalSignals.screenTimeMultiplier > 3.0f) {
            signals.add("Screen time 3x above normal");
            score += 2;
        }

        if (digitalSignals.unlockCount > 150) {
            signals.add("150+ unlocks today (compulsive checking)");
            score += 1;
        }

        return score;
    }

    private int assessLongitudinal(@Nullable LongitudinalSignals longitudinalSignals,
                                    List<String> signals, List<String> actions) {
        if (longitudinalSignals == null) return 0;
        int score = 0;

        if (longitudinalSignals.consecutiveWorseningDays >= 3) {
            signals.add(longitudinalSignals.consecutiveWorseningDays + " consecutive worsening days");
            actions.add("Schedule a check-in");
            score += 3;
        }

        if (longitudinalSignals.riskDelta > 0.2f) {
            signals.add("Risk score jumped " + String.format(Locale.US, "%.0f%%", longitudinalSignals.riskDelta * 100) + " in 24h");
            score += 2;
        }

        return score;
    }

    private int assessJournal(@Nullable JournalSignals journalSignals,
                               List<String> signals, List<String> actions) {
        if (journalSignals == null) return 0;
        int score = 0;

        if (journalSignals.crisisKeywordCount > 0) {
            signals.add("Crisis keywords detected in journal (" + journalSignals.crisisKeywordCount + " flags)");
            actions.add("Gently suggest support resources");
            score += journalSignals.crisisKeywordCount >= 3 ? 4 : 2;
        }

        if (journalSignals.distressSeverity > 0.8f) {
            signals.add("Very high distress detected in journal content");
            score += 2;
        }

        return score;
    }

    private int assessComorbidity(@Nullable String[] comorbidities,
                                   List<String> signals, List<String> actions) {
        if (comorbidities == null) return 0;
        int score = 0;

        for (String combo : comorbidities) {
            if ("depression_isolation_loop".equals(combo)) {
                signals.add("Depression + isolation reinforcing loop detected");
                score += 2;
            }
            if ("burnout_triad".equals(combo)) {
                signals.add("Burnout triad: high stress + low energy + low fulfilment");
                score += 1;
            }
        }

        return score;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SIGNAL INPUT CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /** Digital behaviour crisis signals. */
    public static class DigitalBehaviorSignals {
        public int nightUsageMinutes;
        public float screenTimeMultiplier;  // relative to 7-day average
        public int unlockCount;
        public boolean isNightOwl;          // true if current time is 10PM-6AM

        public DigitalBehaviorSignals(int nightMin, float stMultiplier, int unlocks) {
            this.nightUsageMinutes = nightMin;
            this.screenTimeMultiplier = stMultiplier;
            this.unlockCount = unlocks;
            this.isNightOwl = nightMin > 60;
        }
    }

    /** Longitudinal trend signals. */
    public static class LongitudinalSignals {
        public int consecutiveWorseningDays;
        public int worseningDays;   // alias for suicide risk classifier
        public float riskDelta;     // 24h risk score change

        public LongitudinalSignals(int worseningDays, float delta) {
            this.consecutiveWorseningDays = worseningDays;
            this.worseningDays = worseningDays;
            this.riskDelta = delta;
        }
    }

    /** Journal-based crisis signals. */
    public static class JournalSignals {
        public int crisisKeywordCount;
        public float distressSeverity;  // 0.0 - 1.0
        public String rawText;          // raw journal text for suicide risk scan
        public int distressLevel;       // 1-10 scale
        public int moodScore;           // 1-10 scale (higher = better)

        public JournalSignals(int keywordCount, float severity) {
            this.crisisKeywordCount = keywordCount;
            this.distressSeverity = severity;
        }

        /** Extended constructor with text for suicide risk classification. */
        public JournalSignals(int keywordCount, float severity, String rawText,
                              int distressLevel, int moodScore) {
            this.crisisKeywordCount = keywordCount;
            this.distressSeverity = severity;
            this.rawText = rawText;
            this.distressLevel = distressLevel;
            this.moodScore = moodScore;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private boolean containsComorbidity(String[] comorbidities, String keyword) {
        if (comorbidities == null) return false;
        for (String c : comorbidities) {
            if (c != null && c.contains(keyword)) return true;
        }
        return false;
    }
}
