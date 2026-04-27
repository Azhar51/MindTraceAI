package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Breathing and grounding exercise engine with preset patterns,
 * guided prompts, and effectiveness tracking.
 *
 * <h3>Breathing Presets:</h3>
 * <ul>
 *   <li><b>4-4-6 Calm</b> — inhale 4s, hold 4s, exhale 6s (general calming)</li>
 *   <li><b>4-7-8 Sleep</b> — inhale 4s, hold 7s, exhale 8s (sleep onset)</li>
 *   <li><b>Box Breathing</b> — 4-4-4-4 (focus and control)</li>
 *   <li><b>Quick Reset</b> — 3-3-3 (2-min emergency calm)</li>
 * </ul>
 *
 * <h3>Grounding Presets:</h3>
 * <ul>
 *   <li><b>5-4-3-2-1 Senses</b> — progressive sensory awareness</li>
 *   <li><b>Body Scan</b> — head-to-toe tension release</li>
 *   <li><b>Progressive Muscle Relaxation</b> — tense-and-release cycle</li>
 * </ul>
 */
public class ExerciseEngine {

    // ═══════════════════════════════════════════════════════════════════
    // BREATHING EXERCISE MODEL
    // ═══════════════════════════════════════════════════════════════════

    public static class BreathingExercise {
        public final String name;
        public final String description;
        public final long inhaleMs;
        public final long hold1Ms;   // hold after inhale
        public final long exhaleMs;
        public final long hold2Ms;   // hold after exhale (box breathing)
        public final int totalCycles;
        public final String bestFor;

        BreathingExercise(String name, String desc, long inhale, long hold1,
                          long exhale, long hold2, int cycles, String bestFor) {
            this.name = name;
            this.description = desc;
            this.inhaleMs = inhale;
            this.hold1Ms = hold1;
            this.exhaleMs = exhale;
            this.hold2Ms = hold2;
            this.totalCycles = cycles;
            this.bestFor = bestFor;
        }

        /** Total duration of one cycle in milliseconds. */
        public long getCycleDurationMs() {
            return inhaleMs + hold1Ms + exhaleMs + hold2Ms;
        }

        /** Total exercise duration in milliseconds. */
        public long getTotalDurationMs() {
            return getCycleDurationMs() * totalCycles;
        }

        /** Total exercise duration in minutes (rounded). */
        public int getDurationMinutes() {
            return (int) Math.ceil(getTotalDurationMs() / 60000.0);
        }

        @NonNull
        public String getPhaseLabel(int phase) {
            switch (phase % 4) {
                case 0: return "Breathe In";
                case 1: return "Hold";
                case 2: return "Breathe Out";
                case 3: return "Hold";
                default: return "";
            }
        }

        public long getPhaseDuration(int phase) {
            switch (phase % 4) {
                case 0: return inhaleMs;
                case 1: return hold1Ms;
                case 2: return exhaleMs;
                case 3: return hold2Ms;
                default: return 0;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GROUNDING EXERCISE MODEL
    // ═══════════════════════════════════════════════════════════════════

    public static class GroundingExercise {
        public final String name;
        public final String description;
        public final List<GroundingStep> steps;
        public final String technique;

        GroundingExercise(String name, String desc, List<GroundingStep> steps, String technique) {
            this.name = name;
            this.description = desc;
            this.steps = steps;
            this.technique = technique;
        }

        public int getStepCount() { return steps.size(); }
    }

    public static class GroundingStep {
        public final String instruction;
        public final String detail;
        public final int targetCount; // how many things to identify
        public final String senseType; // see, feel, hear, smell, taste, body

        GroundingStep(String instruction, String detail, int count, String sense) {
            this.instruction = instruction;
            this.detail = detail;
            this.targetCount = count;
            this.senseType = sense;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BREATHING PRESETS
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    public static BreathingExercise getCalmBreathing() {
        return new BreathingExercise(
                "4-4-6 Calm",
                "A gentle breathing pattern to activate your parasympathetic nervous system.",
                4000, 4000, 6000, 0, 8, "General calming, stress relief"
        );
    }

    @NonNull
    public static BreathingExercise getSleepBreathing() {
        return new BreathingExercise(
                "4-7-8 Sleep",
                "Extended exhale pattern that promotes drowsiness and sleep onset.",
                4000, 7000, 8000, 0, 6, "Sleep preparation, deep relaxation"
        );
    }

    @NonNull
    public static BreathingExercise getBoxBreathing() {
        return new BreathingExercise(
                "Box Breathing",
                "Equal-phase breathing used by Navy SEALs for focus under pressure.",
                4000, 4000, 4000, 4000, 6, "Focus, control, anxiety management"
        );
    }

    @NonNull
    public static BreathingExercise getQuickReset() {
        return new BreathingExercise(
                "Quick Reset",
                "Fast 2-minute breathing reset for immediate crisis de-escalation.",
                3000, 3000, 3000, 0, 8, "Emergency calming, panic reduction"
        );
    }

    /** Get all breathing exercise presets. */
    @NonNull
    public static List<BreathingExercise> getAllBreathingExercises() {
        List<BreathingExercise> list = new ArrayList<>();
        list.add(getCalmBreathing());
        list.add(getSleepBreathing());
        list.add(getBoxBreathing());
        list.add(getQuickReset());
        return list;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GROUNDING PRESETS
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    public static GroundingExercise get54321Grounding() {
        List<GroundingStep> steps = new ArrayList<>();
        steps.add(new GroundingStep(
                "Name 5 things you can SEE",
                "Look around you. Notice 5 objects — their colors, shapes, textures.",
                5, "see"));
        steps.add(new GroundingStep(
                "Name 4 things you can FEEL",
                "Notice the texture of your clothes, the temperature of the air, the weight of your body.",
                4, "feel"));
        steps.add(new GroundingStep(
                "Name 3 things you can HEAR",
                "Listen carefully. Birds? Traffic? Your own breathing?",
                3, "hear"));
        steps.add(new GroundingStep(
                "Name 2 things you can SMELL",
                "Take a deep breath. Coffee? Rain? Soap? Fresh air?",
                2, "smell"));
        steps.add(new GroundingStep(
                "Name 1 thing you can TASTE",
                "Taste your mouth. Water? Toothpaste? A recent meal?",
                1, "taste"));
        return new GroundingExercise(
                "5-4-3-2-1 Senses",
                "Anchors you to the present moment by engaging all five senses.",
                steps, "sensory_awareness"
        );
    }

    @NonNull
    public static GroundingExercise getBodyScan() {
        List<GroundingStep> steps = new ArrayList<>();
        steps.add(new GroundingStep("Focus on your HEAD", "Notice any tension in your forehead, jaw, or temples. Consciously relax.", 1, "body"));
        steps.add(new GroundingStep("Focus on your SHOULDERS", "Drop your shoulders away from your ears. Let them be heavy.", 1, "body"));
        steps.add(new GroundingStep("Focus on your CHEST", "Place your hand on your chest. Feel your breath moving.", 1, "body"));
        steps.add(new GroundingStep("Focus on your HANDS", "Unclench your fists. Spread your fingers wide, then relax.", 1, "body"));
        steps.add(new GroundingStep("Focus on your STOMACH", "Let your belly be soft. Release any holding.", 1, "body"));
        steps.add(new GroundingStep("Focus on your LEGS", "Feel the weight of your legs. Let them be still.", 1, "body"));
        steps.add(new GroundingStep("Focus on your FEET", "Press your feet into the ground. Feel the connection.", 1, "body"));
        return new GroundingExercise(
                "Body Scan",
                "Progressive head-to-toe scan that releases accumulated physical tension.",
                steps, "body_scan"
        );
    }

    @NonNull
    public static GroundingExercise getMuscleRelaxation() {
        List<GroundingStep> steps = new ArrayList<>();
        steps.add(new GroundingStep("FISTS: Squeeze tightly for 10 seconds", "Make a fist as hard as you can. Feel the tension. Now release.", 1, "body"));
        steps.add(new GroundingStep("SHOULDERS: Shrug up to your ears", "Hold for 10 seconds. Feel the burn. Now drop them.", 1, "body"));
        steps.add(new GroundingStep("FACE: Scrunch everything tight", "Close your eyes hard, clench your jaw, wrinkle your nose. Hold. Release.", 1, "body"));
        steps.add(new GroundingStep("STOMACH: Tighten your core", "Pull your belly button in. Hold. Let it go.", 1, "body"));
        steps.add(new GroundingStep("LEGS: Push your feet into the floor", "Tighten your thighs and calves. Push hard. Release.", 1, "body"));
        steps.add(new GroundingStep("FULL BODY: Everything at once", "Tense every muscle you can. Hold 10 seconds. Let everything go.", 1, "body"));
        return new GroundingExercise(
                "Progressive Muscle Relaxation",
                "Tense-and-release cycle that teaches your body to recognize and release stress.",
                steps, "muscle_relaxation"
        );
    }

    /** Get all grounding exercise presets. */
    @NonNull
    public static List<GroundingExercise> getAllGroundingExercises() {
        List<GroundingExercise> list = new ArrayList<>();
        list.add(get54321Grounding());
        list.add(getBodyScan());
        list.add(getMuscleRelaxation());
        return list;
    }
}
