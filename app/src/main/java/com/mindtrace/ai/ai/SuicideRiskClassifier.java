package com.mindtrace.ai.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.CrisisEvent;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Suicide Risk Classifier — dedicated engine for detecting suicide-specific
 * signals and mapping them to C-SSRS (Columbia Suicide Severity Rating Scale) tiers.
 *
 * <h3>C-SSRS Tier Mapping:</h3>
 * <ul>
 *   <li><b>Tier 1 — Passive Ideation:</b> "Life isn't worth living", general hopelessness</li>
 *   <li><b>Tier 2 — Active Ideation (No Plan):</b> "I want to die", "I wish I were dead"</li>
 *   <li><b>Tier 3 — Active Ideation (With Plan):</b> Method references, "I know how I'd do it"</li>
 *   <li><b>Tier 4 — Intent (No Action):</b> Intent phrases + behavioral escalation signals</li>
 *   <li><b>Tier 5 — Intent (With Action/Preparation):</b> Farewell language, giving away possessions</li>
 * </ul>
 *
 * <h3>Signal Sources:</h3>
 * <ul>
 *   <li>Journal/check-in text (via LinguisticAnalyzer enrichment)</li>
 *   <li>Behavioral patterns (late-night usage + isolation + high distress)</li>
 *   <li>Cumulative crisis history (escalation trajectory)</li>
 *   <li>Check-in distress + mood scores</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> This is a support tool, NOT a clinical diagnostic instrument.
 * It cannot replace professional assessment. When in doubt, always escalate.</p>
 */
public class SuicideRiskClassifier {

    private static final String TAG = "SuicideRiskClassifier";

    // ═══════════════════════════════════════════════════════════════════
    // KEYWORD TIERS — Ordered by severity
    // ═══════════════════════════════════════════════════════════════════

    /** Tier 1 — Passive ideation: general hopelessness without specific death wish */
    private static final String[] TIER_1_PASSIVE = {
            "no point in living", "life isn't worth", "what's the point",
            "tired of living", "don't want to be here", "wish i could disappear",
            "don't care anymore", "nothing to live for", "can't keep going",
            "why am i even here", "don't belong", "world without me",
            "better off alone", "nobody would notice", "pointless existence"
    };

    /** Tier 2 — Active ideation without plan: explicit death wish */
    private static final String[] TIER_2_ACTIVE_NO_PLAN = {
            "want to die", "wish i were dead", "wish i was dead",
            "i want to kill myself", "thinking about dying", "can't stop thinking about death",
            "i'd be better off dead", "rather be dead", "just want it to end",
            "don't want to wake up", "want to stop existing", "end my life",
            "thoughts of dying", "death would be easier", "want everything to stop"
    };

    /** Tier 3 — Active ideation with plan: method references */
    private static final String[] TIER_3_WITH_PLAN = {
            "i know how i'd do it", "i have a plan", "thought about how to",
            "pills", "overdose", "jump off", "hang myself", "cut myself",
            "bridge", "gun", "knife", "rope", "enough pills",
            "looked up ways", "researched methods", "found a way"
    };

    /** Tier 4 — Intent without action: expressed determination */
    private static final String[] TIER_4_INTENT = {
            "i'm going to do it", "made my decision", "i've decided",
            "this is it", "tonight is the night", "no turning back",
            "i'm done", "nothing can stop me", "it's time",
            "tomorrow won't matter", "by this time tomorrow",
            "after i'm gone", "when i'm not here anymore"
    };

    /** Tier 5 — Preparation/farewell signals */
    private static final String[] TIER_5_PREPARATION = {
            "goodbye letter", "writing goodbye", "goodbye everyone",
            "giving things away", "gave away my stuff", "settled my affairs",
            "final message", "last message", "one last thing",
            "tell my family i love them", "take care of my",
            "forgive me", "sorry for everything", "don't blame yourself",
            "this is my last", "remember me", "you'll be fine without me"
    };

    // ═══════════════════════════════════════════════════════════════════
    // ASSESSMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Full suicide risk assessment combining text analysis + behavioral signals.
     *
     * @param textContent   Journal or check-in text (nullable)
     * @param distressLevel Current distress level (1-10)
     * @param moodScore     Current mood score (1-10, higher = better)
     * @param isNightTime   Whether the assessment happens between 10PM-6AM
     * @param recentCrisisCount Number of crisis events in the last 7 days
     * @param isolationSignal Whether social isolation has been detected
     * @return SuicideRiskAssessment with C-SSRS tier and action recommendations
     */
    @NonNull
    public SuicideRiskAssessment assess(
            @Nullable String textContent,
            int distressLevel,
            int moodScore,
            boolean isNightTime,
            int recentCrisisCount,
            boolean isolationSignal) {

        SuicideRiskAssessment assessment = new SuicideRiskAssessment();

        // ── Text-based signal detection ──
        if (textContent != null && !textContent.trim().isEmpty()) {
            String lower = textContent.toLowerCase().trim();
            detectTextSignals(lower, assessment);
        }

        // ── Behavioral escalation signals ──
        detectBehavioralSignals(distressLevel, moodScore, isNightTime,
                recentCrisisCount, isolationSignal, assessment);

        // ── Determine final C-SSRS tier ──
        assessment.csrrsTier = calculateFinalTier(assessment);

        // ── Generate action recommendations ──
        assessment.recommendations = generateRecommendations(assessment);

        // ── Set notification urgency ──
        assessment.shouldNotify = assessment.csrrsTier >= 2;
        assessment.shouldLockdown = assessment.csrrsTier >= 4;
        assessment.shouldAutoContact = assessment.csrrsTier >= 3;

        Log.d(TAG, "Suicide risk assessment: tier=" + assessment.csrrsTier +
                ", signals=" + assessment.activeSignals.size());

        return assessment;
    }

    /**
     * Quick text-only check for journal auto-scan.
     */
    @NonNull
    public SuicideRiskAssessment quickTextScan(@Nullable String text) {
        return assess(text, 5, 5, false, 0, false);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEXT SIGNAL DETECTION
    // ═══════════════════════════════════════════════════════════════════

    private void detectTextSignals(String lower, SuicideRiskAssessment assessment) {
        // Check each tier in reverse order (highest severity first)
        for (String phrase : TIER_5_PREPARATION) {
            if (lower.contains(phrase)) {
                assessment.textTier = Math.max(assessment.textTier, 5);
                assessment.activeSignals.add("⚠️ Farewell/preparation language: \"" + phrase + "\"");
                assessment.matchedPhrases.add(phrase);
            }
        }

        for (String phrase : TIER_4_INTENT) {
            if (lower.contains(phrase)) {
                assessment.textTier = Math.max(assessment.textTier, 4);
                assessment.activeSignals.add("🔴 Intent expression: \"" + phrase + "\"");
                assessment.matchedPhrases.add(phrase);
            }
        }

        for (String phrase : TIER_3_WITH_PLAN) {
            if (lower.contains(phrase)) {
                assessment.textTier = Math.max(assessment.textTier, 3);
                assessment.activeSignals.add("🟠 Plan/method reference: \"" + phrase + "\"");
                assessment.matchedPhrases.add(phrase);
            }
        }

        for (String phrase : TIER_2_ACTIVE_NO_PLAN) {
            if (lower.contains(phrase)) {
                assessment.textTier = Math.max(assessment.textTier, 2);
                assessment.activeSignals.add("🟡 Active ideation: \"" + phrase + "\"");
                assessment.matchedPhrases.add(phrase);
            }
        }

        for (String phrase : TIER_1_PASSIVE) {
            if (lower.contains(phrase)) {
                assessment.textTier = Math.max(assessment.textTier, 1);
                assessment.activeSignals.add("🔵 Passive ideation signal: \"" + phrase + "\"");
                assessment.matchedPhrases.add(phrase);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BEHAVIORAL SIGNALS
    // ═══════════════════════════════════════════════════════════════════

    private void detectBehavioralSignals(
            int distress, int mood, boolean night,
            int crisisCount, boolean isolated,
            SuicideRiskAssessment assessment) {

        int behaviorScore = 0;

        // High distress (8+/10)
        if (distress >= 8) {
            behaviorScore += 2;
            assessment.activeSignals.add("Extremely high distress (" + distress + "/10)");
        } else if (distress >= 6) {
            behaviorScore += 1;
        }

        // Low mood (2 or less)
        if (mood <= 2) {
            behaviorScore += 2;
            assessment.activeSignals.add("Very low mood (" + mood + "/10)");
        } else if (mood <= 4) {
            behaviorScore += 1;
        }

        // Nighttime (10PM-6AM) — vulnerability window
        if (night) {
            behaviorScore += 1;
            assessment.activeSignals.add("Late-night activity (vulnerability window)");
        }

        // Escalating crisis frequency
        if (crisisCount >= 3) {
            behaviorScore += 2;
            assessment.activeSignals.add("Frequent crisis events (" + crisisCount + " in 7 days)");
        } else if (crisisCount >= 2) {
            behaviorScore += 1;
        }

        // Social isolation
        if (isolated) {
            behaviorScore += 1;
            assessment.activeSignals.add("Social isolation detected");
        }

        // Combination amplifiers
        if (night && distress >= 8 && isolated) {
            behaviorScore += 2; // High-risk combination
            assessment.activeSignals.add("⚠️ High-risk combination: night + extreme distress + isolation");
        }

        if (distress >= 9 && mood <= 2 && crisisCount >= 2) {
            behaviorScore += 2;
            assessment.activeSignals.add("⚠️ Acute escalation pattern detected");
        }

        // Map behavior score to tier contribution
        assessment.behaviorTier = mapBehaviorScoreToTier(behaviorScore);
    }

    private int mapBehaviorScoreToTier(int score) {
        if (score >= 8) return 4;
        if (score >= 6) return 3;
        if (score >= 4) return 2;
        if (score >= 2) return 1;
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // FINAL TIER CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    private int calculateFinalTier(SuicideRiskAssessment assessment) {
        // Text tier takes priority (most direct signal)
        int textTier = assessment.textTier;
        int behaviorTier = assessment.behaviorTier;

        if (textTier >= 4) return textTier; // Intent/preparation → always highest
        if (textTier >= 3) return Math.max(textTier, behaviorTier); // Plan → amplify
        if (textTier >= 2 && behaviorTier >= 2) return 3; // Active ideation + behavioral = escalate
        if (textTier >= 1 && behaviorTier >= 3) return 3; // Passive + high behavioral = escalate

        return Math.max(textTier, behaviorTier);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RECOMMENDATIONS
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private List<String> generateRecommendations(SuicideRiskAssessment assessment) {
        List<String> recs = new ArrayList<>();

        switch (assessment.csrrsTier) {
            case 5:
                recs.add("IMMEDIATE: Call 988 Suicide & Crisis Lifeline");
                recs.add("Contact emergency services if in immediate danger");
                recs.add("Auto-notify trusted emergency contacts");
                recs.add("Stay on the line with a trained counselor");
                break;
            case 4:
                recs.add("URGENT: Open crisis lockdown mode");
                recs.add("Call 988 Suicide & Crisis Lifeline");
                recs.add("Reach out to a trusted contact now");
                recs.add("Remove access to any means of self-harm");
                break;
            case 3:
                recs.add("Call 988 or text HOME to 741741 (Crisis Text Line)");
                recs.add("Contact a trusted person within the next hour");
                recs.add("Use a grounding exercise to reduce acute distress");
                recs.add("Review your safety plan");
                break;
            case 2:
                recs.add("Consider calling 988 to talk with someone");
                recs.add("Try a breathing exercise to ground yourself");
                recs.add("Reach out to someone you trust");
                recs.add("Review your reasons to live (safety plan section 3)");
                break;
            case 1:
                recs.add("Practice a coping strategy from your safety plan");
                recs.add("Journal about what you're feeling");
                recs.add("Consider scheduling a check-in with a counselor");
                break;
            default:
                recs.add("Continue your regular wellness routine");
                break;
        }

        return recs;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ASSESSMENT RESULT
    // ═══════════════════════════════════════════════════════════════════

    public static class SuicideRiskAssessment {
        /** Final C-SSRS tier (0-5) */
        public int csrrsTier = 0;

        /** Tier derived from text analysis alone */
        public int textTier = 0;

        /** Tier derived from behavioral signals alone */
        public int behaviorTier = 0;

        /** Human-readable active signal descriptions */
        public List<String> activeSignals = new ArrayList<>();

        /** Raw matched phrases from text */
        public List<String> matchedPhrases = new ArrayList<>();

        /** Recommended actions for this tier */
        public List<String> recommendations = new ArrayList<>();

        /** Whether to fire a notification */
        public boolean shouldNotify = false;

        /** Whether to launch crisis lockdown mode */
        public boolean shouldLockdown = false;

        /** Whether to auto-compose messages to trusted contacts */
        public boolean shouldAutoContact = false;

        /** Get severity label */
        @NonNull
        public String getSeverityLabel() {
            switch (csrrsTier) {
                case 5: return "CRITICAL — Preparation/Farewell";
                case 4: return "SEVERE — Intent Expressed";
                case 3: return "HIGH — Plan Identified";
                case 2: return "ELEVATED — Active Ideation";
                case 1: return "MODERATE — Passive Ideation";
                default: return "LOW — No Acute Risk";
            }
        }

        /** Get tier color (for UI) */
        public int getTierColorHex() {
            switch (csrrsTier) {
                case 5: return 0xFFD32F2F; // Deep red
                case 4: return 0xFFF44336; // Red
                case 3: return 0xFFFF9800; // Orange
                case 2: return 0xFFFFC107; // Amber
                case 1: return 0xFF2196F3; // Blue
                default: return 0xFF4CAF50; // Green
            }
        }

        /** Whether this assessment requires any intervention */
        public boolean requiresIntervention() {
            return csrrsTier >= 1;
        }

        /** Summary for logging/export */
        @NonNull
        @Override
        public String toString() {
            return "SuicideRiskAssessment{tier=" + csrrsTier +
                    " (" + getSeverityLabel() + ")" +
                    ", signals=" + activeSignals.size() +
                    ", notify=" + shouldNotify +
                    ", lockdown=" + shouldLockdown + "}";
        }
    }
}
