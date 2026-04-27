package com.mindtrace.ai.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-populated safety resource database — crisis helplines, grounding
 * techniques, and emergency contact templates.
 *
 * <p>All data is hardcoded to ensure availability offline during crisis events.</p>
 */
public class SafetyResources {

    // ═══════════════════════════════════════════════════════════════════
    // CRISIS HELPLINES
    // ═══════════════════════════════════════════════════════════════════

    public static List<Helpline> getHelplines() {
        List<Helpline> lines = new ArrayList<>();

        lines.add(new Helpline("988 Suicide & Crisis Lifeline", "988",
                "Call or text 988", "US", true));
        lines.add(new Helpline("Crisis Text Line", "741741",
                "Text HOME to 741741", "US", true));
        lines.add(new Helpline("National Suicide Prevention", "1-800-273-8255",
                "24/7 free and confidential", "US", true));
        lines.add(new Helpline("SAMHSA Helpline", "1-800-662-4357",
                "Mental health & substance abuse", "US", false));
        lines.add(new Helpline("Veterans Crisis Line", "838255",
                "Text 838255", "US", false));
        lines.add(new Helpline("Trevor Project", "1-866-488-7386",
                "LGBTQ+ youth crisis support", "US", false));

        // International
        lines.add(new Helpline("Samaritans", "116 123",
                "24/7 emotional support", "UK", true));
        lines.add(new Helpline("Lifeline Australia", "13 11 14",
                "24/7 crisis support", "AU", true));
        lines.add(new Helpline("Vandrevala Foundation", "9999 666 555",
                "24/7 mental health helpline", "IN", true));
        lines.add(new Helpline("iCall", "9152987821",
                "Psychosocial helpline", "IN", false));

        return lines;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GROUNDING TECHNIQUES (5-4-3-2-1)
    // ═══════════════════════════════════════════════════════════════════

    public static List<GroundingExercise> getGroundingExercises() {
        List<GroundingExercise> exercises = new ArrayList<>();

        exercises.add(new GroundingExercise(
                "5-4-3-2-1 Senses",
                "Name 5 things you can see, 4 you can touch, 3 you can hear, " +
                        "2 you can smell, and 1 you can taste.",
                "🌿", 3, "sensory"));

        exercises.add(new GroundingExercise(
                "Box Breathing",
                "Breathe in for 4 seconds, hold for 4, breathe out for 4, hold for 4. Repeat 4 times.",
                "💨", 2, "breathing"));

        exercises.add(new GroundingExercise(
                "Cold Water",
                "Hold a piece of ice or splash cold water on your face. Focus on the sensation.",
                "🧊", 1, "physical"));

        exercises.add(new GroundingExercise(
                "Body Scan",
                "Starting from your toes, slowly notice each part of your body. " +
                        "Notice any tension and gently release it.",
                "🧘", 5, "mindfulness"));

        exercises.add(new GroundingExercise(
                "Safe Place Visualization",
                "Close your eyes and imagine a place where you feel completely safe. " +
                        "Notice every detail — colors, sounds, temperature.",
                "🏖️", 3, "visualization"));

        exercises.add(new GroundingExercise(
                "Alphabet Game",
                "Pick a category (animals, cities, foods) and name one for each letter of the alphabet.",
                "🔤", 3, "cognitive"));

        exercises.add(new GroundingExercise(
                "Progressive Muscle Relaxation",
                "Tense each muscle group for 5 seconds, then release. " +
                        "Start with feet and work up to your face.",
                "💪", 5, "physical"));

        exercises.add(new GroundingExercise(
                "4-7-8 Breathing",
                "Inhale for 4 seconds, hold for 7 seconds, exhale for 8 seconds. " +
                        "This activates your parasympathetic nervous system.",
                "🌬️", 3, "breathing"));

        return exercises;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SAFETY PLAN TEMPLATE
    // ═══════════════════════════════════════════════════════════════════

    public static List<String> getSafetyPlanSteps() {
        List<String> steps = new ArrayList<>();
        steps.add("Recognize your personal warning signs");
        steps.add("Use your internal coping strategies (breathing, grounding)");
        steps.add("Reach out to people who provide distraction");
        steps.add("Contact friends or family who can help");
        steps.add("Contact a professional or crisis helpline");
        steps.add("Make your environment safe");
        return steps;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COPING STATEMENTS
    // ═══════════════════════════════════════════════════════════════════

    public static List<String> getCopingStatements() {
        List<String> statements = new ArrayList<>();
        statements.add("This feeling is temporary. It will pass.");
        statements.add("I've gotten through difficult times before.");
        statements.add("I don't need to act on these feelings right now.");
        statements.add("It's okay to ask for help.");
        statements.add("My feelings are valid, but they don't define my future.");
        statements.add("One moment at a time. I just need to get through this moment.");
        statements.add("I am more than my worst day.");
        statements.add("There are people who care about me.");
        statements.add("Recovery is not linear. Bad days are part of the process.");
        statements.add("I deserve support and compassion — especially from myself.");
        return statements;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════

    public static class Helpline {
        public final String name;
        public final String number;
        public final String description;
        public final String country;
        public final boolean isPrimary;

        public Helpline(String name, String number, String description,
                        String country, boolean isPrimary) {
            this.name = name;
            this.number = number;
            this.description = description;
            this.country = country;
            this.isPrimary = isPrimary;
        }
    }

    public static class GroundingExercise {
        public final String title;
        public final String instructions;
        public final String emoji;
        public final int durationMinutes;
        public final String type;

        public GroundingExercise(String title, String instructions,
                                String emoji, int durationMinutes, String type) {
            this.title = title;
            this.instructions = instructions;
            this.emoji = emoji;
            this.durationMinutes = durationMinutes;
            this.type = type;
        }
    }
}
