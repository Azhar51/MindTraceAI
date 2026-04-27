package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.entity.CrisisEvent;

/**
 * Post-crisis debrief framework — presented 4-6 hours after a crisis resolves.
 *
 * <h3>Debrief Flow:</h3>
 * <pre>
 *   CrisisEvent resolved → 4-6h delay → PostCrisisDebrief prompted
 *       → User answers 5 questions → responses saved
 *           → AI generates personalized insights
 *               → Safety plan adjustments recommended
 * </pre>
 *
 * <h3>Questions:</h3>
 * <ol>
 *   <li>How are you feeling now? (1-10 scale)</li>
 *   <li>What helped you the most? (resolution method reflection)</li>
 *   <li>What didn't help? (negative strategy identification)</li>
 *   <li>What would you do differently next time?</li>
 *   <li>Do you want to update your safety plan?</li>
 * </ol>
 */
public class PostCrisisDebrief {

    public static final int DEBRIEF_DELAY_HOURS = 4;

    // ═══════════════════════════════════════════════════════════════════
    // DEBRIEF RESPONSE MODEL
    // ═══════════════════════════════════════════════════════════════════

    public static class DebriefResponse {
        public int currentMoodLevel;          // 1-10
        public String whatHelped;              // free text
        public String whatDidntHelp;           // free text
        public String wouldDoDifferently;      // free text
        public boolean wantsToUpdateSafetyPlan;
        public long completedAt;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEBRIEF QUESTIONS
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    public static String[] getQuestions() {
        return new String[]{
                "How are you feeling right now? (1 = very bad, 10 = very good)",
                "Looking back, what helped you the most during the crisis?",
                "Was there anything that didn't help or made things worse?",
                "What would you do differently next time?",
                "Would you like to update your safety plan based on this experience?"
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEBRIEF INSIGHT GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate personalized insights from the debrief and crisis event.
     */
    @NonNull
    public static DebriefInsight generateInsight(@NonNull CrisisEvent event,
                                                  @NonNull DebriefResponse response) {
        DebriefInsight insight = new DebriefInsight();

        // Recovery assessment
        if (response.currentMoodLevel >= 7) {
            insight.recoveryStatus = "strong";
            insight.summary = "You've recovered well from this crisis. Your coping strategies are working.";
        } else if (response.currentMoodLevel >= 4) {
            insight.recoveryStatus = "moderate";
            insight.summary = "You're making progress. Be gentle with yourself — recovery takes time.";
        } else {
            insight.recoveryStatus = "ongoing";
            insight.summary = "You're still processing. Consider reaching out to someone or trying a breathing exercise.";
        }

        // De-escalation effectiveness
        if (event.wasDeescalationEffective()) {
            int reduction = event.getDistressReduction();
            insight.deescalationNote = "Your distress dropped by " + reduction +
                    " points during the crisis. " + (event.resolutionMethod != null ?
                    "'" + event.resolutionMethod.replace("_", " ") + "' was especially effective." : "");
        }

        // Duration insight
        long durationMin = event.getDurationMs() / 60000;
        if (durationMin > 0 && durationMin < 30) {
            insight.durationNote = "The crisis lasted about " + durationMin +
                    " minutes — that's a relatively quick resolution. You handled it well.";
        } else if (durationMin >= 30) {
            insight.durationNote = "The crisis lasted about " + durationMin +
                    " minutes. Consider adding a micro-intervention to your safety plan for faster relief.";
        }

        // Safety plan recommendation
        if (response.whatHelped != null && !response.whatHelped.isEmpty()) {
            insight.safetyPlanUpdate = "Consider adding '" + response.whatHelped +
                    "' to your safety plan's coping strategies section.";
        }

        // Follow-up recommendation
        if (response.currentMoodLevel <= 3) {
            insight.followUpRecommendation = "Your mood is still low. A follow-up check-in is recommended.";
            insight.needsFollowUp = true;
        }

        return insight;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEBRIEF INSIGHT MODEL
    // ═══════════════════════════════════════════════════════════════════

    public static class DebriefInsight {
        public String recoveryStatus;       // "strong", "moderate", "ongoing"
        public String summary;
        public String deescalationNote;
        public String durationNote;
        public String safetyPlanUpdate;
        public String followUpRecommendation;
        public boolean needsFollowUp;

        @NonNull
        public String toNarrative() {
            StringBuilder sb = new StringBuilder();
            if (summary != null) sb.append(summary).append("\n\n");
            if (deescalationNote != null) sb.append("📊 ").append(deescalationNote).append("\n\n");
            if (durationNote != null) sb.append("⏱️ ").append(durationNote).append("\n\n");
            if (safetyPlanUpdate != null) sb.append("🛡️ ").append(safetyPlanUpdate).append("\n\n");
            if (followUpRecommendation != null) sb.append("⚠️ ").append(followUpRecommendation);
            return sb.toString().trim();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER: Check if debrief is due
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if a resolved crisis event is ready for a debrief.
     * Must be resolved 4+ hours ago and debrief not yet completed.
     */
    public static boolean isDebriefDue(@NonNull CrisisEvent event) {
        if (event.debriefCompleted) return false;
        if (event.resolvedAt == 0) return false;
        long hoursSinceResolution = (System.currentTimeMillis() - event.resolvedAt) / (60 * 60 * 1000);
        return hoursSinceResolution >= DEBRIEF_DELAY_HOURS;
    }
}
