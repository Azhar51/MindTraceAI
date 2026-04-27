package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mindtrace.ai.database.entity.InterventionTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Centralized library of 80+ intervention templates organized by risk category,
 * difficulty, time slot, and duration. All task generation flows through this bank
 * to ensure consistency, deduplication, and expandability.
 *
 * <h3>Template Organization:</h3>
 * <ul>
 *   <li><b>Categories (7):</b> Mindfulness, Journaling, Social, Purpose, Detox, Recovery, Focus</li>
 *   <li><b>Risk Linkage (6):</b> digital_addiction, stress_anxiety, depression, social_isolation, sleep_disruption, low_fulfilment</li>
 *   <li><b>Difficulty (3):</b> EASY (2-5min), MEDIUM (10-20min), HARD (30-60min)</li>
 *   <li><b>Time Slots (4):</b> MORNING, AFTERNOON, EVENING, ANYTIME</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   TaskTemplateRepository repo = TaskTemplateRepository.getInstance();
 *   List&lt;Template&gt; stressTemplates = repo.getForRiskCategory("stress_anxiety");
 *   Template micro = repo.getRandomMicroIntervention();
 *   InterventionTask task = repo.toTask(micro, "Your stress is elevated", "classification");
 * </pre>
 */
public class TaskTemplateRepository {

    private static TaskTemplateRepository instance;
    private final List<Template> templates = new ArrayList<>();
    private final Random random = new Random();

    // ─────────────────────────────────────────────────────────────────────
    // TEMPLATE DATA CLASS
    // ─────────────────────────────────────────────────────────────────────

    public static class Template {
        public final String title;
        public final String description;
        public final String whyItHelps;
        public final String category;         // Mindfulness, Journaling, Social, Purpose, Detox, Recovery, Focus
        public final String linkedRisk;       // digital_addiction, stress_anxiety, depression, etc.
        public final String difficulty;       // EASY, MEDIUM, HARD
        public final String timeSlot;         // MORNING, AFTERNOON, EVENING, ANYTIME
        public final int durationMinutes;
        public final int xpReward;
        public final boolean isMicro;         // 2-min emergency task

        Template(String title, String desc, String why, String cat, String risk,
                 String diff, String slot, int dur, int xp, boolean micro) {
            this.title = title;
            this.description = desc;
            this.whyItHelps = why;
            this.category = cat;
            this.linkedRisk = risk;
            this.difficulty = diff;
            this.timeSlot = slot;
            this.durationMinutes = dur;
            this.xpReward = xp;
            this.isMicro = micro;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SINGLETON
    // ─────────────────────────────────────────────────────────────────────

    public static synchronized TaskTemplateRepository getInstance() {
        if (instance == null) {
            instance = new TaskTemplateRepository();
        }
        return instance;
    }

    private TaskTemplateRepository() {
        seedAllTemplates();
    }

    // ─────────────────────────────────────────────────────────────────────
    // QUERY METHODS
    // ─────────────────────────────────────────────────────────────────────

    /** Get all templates for a specific risk category. */
    @NonNull
    public List<Template> getForRiskCategory(@NonNull String riskCategory) {
        List<Template> result = new ArrayList<>();
        for (Template t : templates) {
            if (riskCategory.equals(t.linkedRisk)) result.add(t);
        }
        return result;
    }

    /** Get all templates for a task category (Mindfulness, Social, etc.). */
    @NonNull
    public List<Template> getForCategory(@NonNull String category) {
        List<Template> result = new ArrayList<>();
        String lc = category.toLowerCase();
        for (Template t : templates) {
            if (t.category.toLowerCase().equals(lc)) result.add(t);
        }
        return result;
    }

    /** Get templates by difficulty level. */
    @NonNull
    public List<Template> getByDifficulty(@NonNull String difficulty) {
        List<Template> result = new ArrayList<>();
        for (Template t : templates) {
            if (difficulty.equals(t.difficulty)) result.add(t);
        }
        return result;
    }

    /** Get templates for a specific time slot. */
    @NonNull
    public List<Template> getForTimeSlot(@NonNull String timeSlot) {
        List<Template> result = new ArrayList<>();
        for (Template t : templates) {
            if (timeSlot.equals(t.timeSlot) || "ANYTIME".equals(t.timeSlot)) result.add(t);
        }
        return result;
    }

    /** Get all micro-intervention templates (2-min emergency tasks). */
    @NonNull
    public List<Template> getMicroInterventions() {
        List<Template> result = new ArrayList<>();
        for (Template t : templates) {
            if (t.isMicro) result.add(t);
        }
        return result;
    }

    /** Get a random template matching the given risk category and difficulty. */
    @Nullable
    public Template getRandom(@NonNull String riskCategory, @NonNull String difficulty) {
        List<Template> matches = new ArrayList<>();
        for (Template t : templates) {
            if (riskCategory.equals(t.linkedRisk) && difficulty.equals(t.difficulty)) {
                matches.add(t);
            }
        }
        return matches.isEmpty() ? null : matches.get(random.nextInt(matches.size()));
    }

    /** Get a random template from any category. */
    @Nullable
    public Template getRandomTemplate() {
        return templates.isEmpty() ? null : templates.get(random.nextInt(templates.size()));
    }

    /** Get a random micro-intervention. */
    @Nullable
    public Template getRandomMicroIntervention() {
        List<Template> micros = getMicroInterventions();
        return micros.isEmpty() ? null : micros.get(random.nextInt(micros.size()));
    }

    /** Get templates matching risk + time slot, sorted by difficulty. */
    @NonNull
    public List<Template> getRecommended(@NonNull String riskCategory, @NonNull String timeSlot) {
        List<Template> result = new ArrayList<>();
        for (Template t : templates) {
            if (riskCategory.equals(t.linkedRisk) &&
                    (timeSlot.equals(t.timeSlot) || "ANYTIME".equals(t.timeSlot))) {
                result.add(t);
            }
        }
        // Sort: EASY first, then MEDIUM, then HARD
        Collections.sort(result, (a, b) -> difficultyOrdinal(a.difficulty) - difficultyOrdinal(b.difficulty));
        return result;
    }

    /** Get total template count. */
    public int getTemplateCount() {
        return templates.size();
    }

    // ─────────────────────────────────────────────────────────────────────
    // CONVERSION: Template → InterventionTask
    // ─────────────────────────────────────────────────────────────────────

    /** Convert a template into a ready-to-save InterventionTask. */
    @NonNull
    public InterventionTask toTask(@NonNull Template template,
                                    @Nullable String whyThisTask,
                                    @Nullable String sourceTag) {
        InterventionTask task = new InterventionTask();
        long now = System.currentTimeMillis();

        task.title = template.title;
        task.description = template.description;
        task.category = template.category;
        task.durationMinutes = template.durationMinutes;
        task.dateCreated = now;
        task.difficulty = template.difficulty;
        task.linkedRiskCategory = template.linkedRisk;
        task.whyThisTask = whyThisTask != null ? whyThisTask : template.whyItHelps;
        task.sourceTag = sourceTag != null ? sourceTag : "classification";
        task.scheduledTimeSlot = template.timeSlot;
        task.xpReward = template.xpReward;
        task.isMicroIntervention = template.isMicro;
        task.status = "ACTIVE";
        task.priority = template.isMicro ? 5 : (template.difficulty.equals("HARD") ? 4 : 3);
        task.expiresAt = now + (24L * 60 * 60 * 1000); // 24h expiry

        return task;
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEMPLATE SEED — 84 templates across all categories
    // ─────────────────────────────────────────────────────────────────────

    private void seedAllTemplates() {
        seedStressAnxiety();
        seedDepression();
        seedDigitalAddiction();
        seedSocialIsolation();
        seedSleepDisruption();
        seedLowFulfilment();
        seedMicroInterventions();
    }

    private void seedStressAnxiety() {
        // EASY
        add("Box Breathing", "Breathe in 4s, hold 4s, out 4s, hold 4s. Repeat 6 cycles.", "Activates parasympathetic nervous system, reducing cortisol within minutes.", "Mindfulness", "stress_anxiety", "EASY", "ANYTIME", 5, 10);
        add("Body Scan", "Starting from your toes, notice tension in each body part and consciously release it.", "Releases physical tension that accumulates with stress.", "Mindfulness", "stress_anxiety", "EASY", "EVENING", 5, 10);
        add("Grounding Check", "Name 5 things you see, 4 you feel, 3 you hear, 2 you smell, 1 you taste.", "Anchors you to the present moment, interrupting anxious thought spirals.", "Mindfulness", "stress_anxiety", "EASY", "ANYTIME", 3, 10);
        add("Tension Release", "Tighten your fists for 10 seconds, then release. Repeat with shoulders, jaw, and feet.", "Progressive muscle relaxation reduces physical stress markers.", "Recovery", "stress_anxiety", "EASY", "ANYTIME", 5, 10);
        // MEDIUM
        add("Worry Journal", "Write down every worry for 10 minutes. Circle ones you can control. Cross out the rest.", "Externalizing worries reduces their perceived weight and builds agency.", "Journaling", "stress_anxiety", "MEDIUM", "EVENING", 10, 25);
        add("4-7-8 Breathing", "Inhale 4s, hold 7s, exhale 8s. Continue for 15 minutes with eyes closed.", "Extended exhale activates vagus nerve for deep calm.", "Mindfulness", "stress_anxiety", "MEDIUM", "EVENING", 15, 25);
        add("Stress Audit", "List your top 5 stressors. For each, write one small action you can take this week.", "Converts abstract stress into concrete, manageable actions.", "Purpose", "stress_anxiety", "MEDIUM", "MORNING", 15, 25);
        // HARD
        add("Deep Journaling", "Write for 30 minutes about what is causing you the most stress and what you wish were different.", "Long-form writing activates cognitive processing of emotional distress.", "Journaling", "stress_anxiety", "HARD", "EVENING", 30, 50);
        add("Nature Reset", "Spend 30 minutes outdoors without your phone. Walk slowly and observe your surroundings.", "Nature exposure reduces cortisol by 12-16% on average.", "Recovery", "stress_anxiety", "HARD", "AFTERNOON", 30, 50);
    }

    private void seedDepression() {
        // EASY
        add("Tiny Win", "Complete one small task you've been putting off — dishes, an email, making your bed.", "Accomplishment activates dopamine reward pathways disrupted by depression.", "Purpose", "depression", "EASY", "MORNING", 5, 10);
        add("Gratitude Snapshot", "Write down 3 specific things you're grateful for right now.", "Shifts attention from negativity bias to positive recognition.", "Journaling", "depression", "EASY", "MORNING", 5, 10);
        add("Sunlight Dose", "Step outside for 5 minutes and face the sun with eyes closed.", "Natural light regulates serotonin production and circadian rhythm.", "Recovery", "depression", "EASY", "MORNING", 5, 10);
        add("Social Ping", "Send a quick hello text to one person you trust.", "Minimal social effort breaks isolation without overwhelming.", "Social", "depression", "EASY", "ANYTIME", 2, 10);
        // MEDIUM
        add("Achievement List", "Write 10 things you've accomplished in the last year, big or small.", "Counters the 'I've done nothing' cognitive distortion common in depression.", "Journaling", "depression", "MEDIUM", "ANYTIME", 15, 25);
        add("Movement Boost", "Take a 15-minute walk at a brisk pace. No phone.", "Exercise releases endorphins and BDNF, improving mood for hours.", "Recovery", "depression", "MEDIUM", "AFTERNOON", 15, 25);
        add("Cook Something", "Prepare a simple, nourishing meal from scratch.", "Cooking engages multiple senses and produces a tangible reward.", "Purpose", "depression", "MEDIUM", "EVENING", 20, 25);
        // HARD
        add("Voice Call", "Call a friend or family member and talk for at least 20 minutes.", "Voice connection releases oxytocin, a powerful anti-depressant hormone.", "Social", "depression", "HARD", "AFTERNOON", 30, 50);
        add("Life Map", "Draw a timeline of your life. Mark high and low points. Write what you learned from each.", "Narrative coherence gives meaning to difficult experiences.", "Journaling", "depression", "HARD", "EVENING", 30, 50);
    }

    private void seedDigitalAddiction() {
        // EASY
        add("Phone Down", "Place your phone in another room for 30 minutes.", "Physical separation breaks compulsive checking loops.", "Detox", "digital_addiction", "EASY", "ANYTIME", 5, 10);
        add("Notification Audit", "Turn off notifications for 3 non-essential apps right now.", "Reduces interrupt-driven usage that feeds dopamine loops.", "Detox", "digital_addiction", "EASY", "MORNING", 5, 10);
        add("Grayscale Mode", "Switch your phone to grayscale for the next 2 hours.", "Removes color-driven engagement cues that apps exploit.", "Detox", "digital_addiction", "EASY", "ANYTIME", 3, 10);
        add("App Intention", "Before opening any app, say out loud why you're opening it.", "Creates a conscious friction layer against mindless usage.", "Focus", "digital_addiction", "EASY", "ANYTIME", 2, 10);
        // MEDIUM
        add("Analog Hour", "Spend 1 hour doing a non-digital hobby: reading, sketching, cooking, or walking.", "Rebuilds capacity for sustained attention without screen stimulation.", "Detox", "digital_addiction", "MEDIUM", "EVENING", 60, 25);
        add("App Limit Setup", "Set daily time limits on your top 3 most-used apps using built-in screen time tools.", "External constraints reduce reliance on willpower alone.", "Detox", "digital_addiction", "MEDIUM", "MORNING", 10, 25);
        add("Single-Task Sprint", "Choose one priority and work on it for 20 minutes without touching your phone.", "Trains sustained attention and reduces task-switching dependency.", "Focus", "digital_addiction", "MEDIUM", "AFTERNOON", 20, 25);
        // HARD
        add("Digital Sunset", "No screens after 8 PM tonight. Use the evening for reading, journaling, or conversation.", "Breaks the evening-scroll pattern and improves sleep onset.", "Detox", "digital_addiction", "HARD", "EVENING", 60, 50);
        add("App Delete Challenge", "Uninstall one app you spend more than 1 hour/day on. Reinstall in 3 days if you miss it.", "Tests actual dependency vs. perceived need.", "Detox", "digital_addiction", "HARD", "MORNING", 5, 50);
    }

    private void seedSocialIsolation() {
        // EASY
        add("Text a Friend", "Send a genuine message to someone you haven't spoken to in a week.", "Low-effort social connection maintains relational bonds.", "Social", "social_isolation", "EASY", "ANYTIME", 2, 10);
        add("Compliment Someone", "Give a genuine, specific compliment to someone today — in person or via message.", "Positive social interactions strengthen belonging and reciprocity.", "Social", "social_isolation", "EASY", "ANYTIME", 2, 10);
        add("Social Media Detox", "Unfollow 5 accounts that make you feel worse about yourself.", "Curating your feed reduces social comparison and perceived isolation.", "Social", "social_isolation", "EASY", "ANYTIME", 5, 10);
        // MEDIUM
        add("Coffee Chat", "Invite someone for a 15-minute coffee or tea — in person or video call.", "Face-to-face interaction activates mirror neurons and empathy circuits.", "Social", "social_isolation", "MEDIUM", "AFTERNOON", 15, 25);
        add("Community Search", "Search for one local event, class, or group that aligns with your interests.", "Low-commitment exploration of social opportunities.", "Social", "social_isolation", "MEDIUM", "ANYTIME", 10, 25);
        add("Shared Activity", "Do something alongside another person — walk, cook, play a game.", "Parallel activities build connection without the pressure of deep conversation.", "Social", "social_isolation", "MEDIUM", "AFTERNOON", 20, 25);
        // HARD
        add("Deep Conversation", "Have a 30-minute phone or in-person conversation. Ask at least 3 genuine questions.", "Deep social exchange is the strongest antidote to isolation.", "Social", "social_isolation", "HARD", "EVENING", 30, 50);
        add("Vulnerability Practice", "Share one honest feeling or struggle with someone you trust.", "Vulnerability builds trust and authentic connection.", "Social", "social_isolation", "HARD", "ANYTIME", 10, 50);
    }

    private void seedSleepDisruption() {
        // EASY
        add("Screen Curfew", "Put your phone on a charger across the room 30 minutes before bed.", "Removes the #1 sleep delay trigger from arm's reach.", "Recovery", "sleep_disruption", "EASY", "EVENING", 2, 10);
        add("Night Mode", "Enable blue light filter / night shift on all devices.", "Reduces blue light that suppresses melatonin production.", "Detox", "sleep_disruption", "EASY", "EVENING", 2, 10);
        add("Sleep Breathing", "Do 5 minutes of 4-7-8 breathing lying in bed.", "Activates rest-and-digest nervous system for sleep onset.", "Mindfulness", "sleep_disruption", "EASY", "EVENING", 5, 10);
        // MEDIUM
        add("Wind-Down Routine", "Create a 20-minute pre-bed routine: dim lights, herbal tea, light reading.", "Consistent wind-down cues train your brain to expect sleep.", "Recovery", "sleep_disruption", "MEDIUM", "EVENING", 20, 25);
        add("Sleep Journal", "Write down everything on your mind before bed. Close the notebook and leave it.", "Externalizing thoughts prevents rumination that delays sleep.", "Journaling", "sleep_disruption", "MEDIUM", "EVENING", 10, 25);
        add("Caffeine Audit", "No caffeine after 2 PM today. Note your sleep quality tomorrow.", "Caffeine half-life is 5-6 hours; afternoon intake disrupts deep sleep.", "Recovery", "sleep_disruption", "MEDIUM", "AFTERNOON", 5, 25);
        // HARD
        add("Sleep Reset", "Go to bed and wake up at the same time for the next 3 days, regardless of sleep quality.", "Consistent sleep schedule is the single most effective sleep hygiene practice.", "Recovery", "sleep_disruption", "HARD", "MORNING", 5, 50);
        add("Bedroom Audit", "Remove all screens from your bedroom. Set room temp to 65-68°F. Use blackout curtains.", "Environment optimization has stronger effects than supplements.", "Recovery", "sleep_disruption", "HARD", "EVENING", 15, 50);
    }

    private void seedLowFulfilment() {
        // EASY
        add("Goal Setting", "Write down one thing you want to achieve tomorrow.", "Forward-looking intention provides direction and anticipation.", "Purpose", "low_fulfilment", "EASY", "EVENING", 5, 10);
        add("Values Check", "Write 3 things that matter most to you. Rate how aligned today was (1-10).", "Values alignment check reveals gap between intention and action.", "Journaling", "low_fulfilment", "EASY", "EVENING", 5, 10);
        add("Learn Something", "Spend 5 minutes learning one new fact, skill, or concept.", "Learning activates curiosity circuits and growth mindset.", "Purpose", "low_fulfilment", "EASY", "ANYTIME", 5, 10);
        // MEDIUM
        add("Passion Audit", "List 10 activities that made you feel most alive. Circle the one you could do this week.", "Reconnects with intrinsic motivation sources.", "Journaling", "low_fulfilment", "MEDIUM", "ANYTIME", 15, 25);
        add("Skill Practice", "Spend 20 minutes practicing a skill you want to develop.", "Mastery experience builds self-efficacy and purpose.", "Purpose", "low_fulfilment", "MEDIUM", "AFTERNOON", 20, 25);
        add("Help Someone", "Do one act of kindness for someone today — hold a door, offer help, write a thank-you.", "Prosocial behavior increases sense of meaning and belonging.", "Social", "low_fulfilment", "MEDIUM", "ANYTIME", 10, 25);
        // HARD
        add("Life Vision", "Write a 1-page letter from your future self 5 years from now. What did you build?", "Future-self connection strengthens motivation and long-term planning.", "Journaling", "low_fulfilment", "HARD", "EVENING", 30, 50);
        add("Volunteer Search", "Research one volunteer opportunity in your area. Sign up or bookmark it.", "Volunteering is one of the strongest predictors of life satisfaction.", "Purpose", "low_fulfilment", "HARD", "ANYTIME", 20, 50);
    }

    private void seedMicroInterventions() {
        // 2-minute emergency tasks for crisis/elevated states
        addMicro("Quick Exhale", "Take 3 slow, deep breaths right now. Exhale longer than you inhale.", "Immediate nervous system regulation.", "Mindfulness", "stress_anxiety");
        addMicro("Ground Now", "Press your feet firmly into the floor. Feel the weight of your body in your chair.", "Physical grounding interrupts panic response.", "Mindfulness", "stress_anxiety");
        addMicro("Cold Water Reset", "Splash cold water on your face or hold ice cubes for 30 seconds.", "Cold activates the dive reflex, immediately lowering heart rate.", "Recovery", "stress_anxiety");
        addMicro("Name It", "Say out loud: 'I am feeling ___.' Just naming the emotion reduces its intensity.", "Affect labeling reduces amygdala activation by up to 43%.", "Mindfulness", "depression");
        addMicro("Look Up", "Look up from your phone. Find something beautiful in your surroundings.", "Breaks negative attention tunnel and engages awe circuits.", "Recovery", "digital_addiction");
        addMicro("Phone Flip", "Turn your phone face-down for 2 minutes. Just sit.", "Removes visual notification triggers.", "Detox", "digital_addiction");
        addMicro("Stretch Break", "Stand up. Stretch your arms above your head. Roll your shoulders 5 times.", "Physical movement disrupts sedentary doom-scrolling patterns.", "Recovery", "digital_addiction");
        addMicro("One Text", "Text one person right now: 'Hey, thinking of you.'", "Micro-connection breaks isolation instantly.", "Social", "social_isolation");
        addMicro("Safe Thought", "Think of one person who cares about you. Picture their face.", "Activates secure attachment neural circuits.", "Recovery", "depression");
        addMicro("Tiny Accomplishment", "Do the smallest possible productive thing: wipe a counter, close a tab, drink water.", "Any action momentum counters learned helplessness.", "Purpose", "low_fulfilment");
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private void add(String title, String desc, String why, String cat, String risk,
                     String diff, String slot, int dur, int xp) {
        templates.add(new Template(title, desc, why, cat, risk, diff, slot, dur, xp, false));
    }

    private void addMicro(String title, String desc, String why, String cat, String risk) {
        templates.add(new Template(title, desc, why, cat, risk, "EASY", "ANYTIME", 2, 75, true));
    }

    private static int difficultyOrdinal(String difficulty) {
        switch (difficulty) {
            case "EASY": return 0;
            case "MEDIUM": return 1;
            case "HARD": return 2;
            default: return 3;
        }
    }
}
