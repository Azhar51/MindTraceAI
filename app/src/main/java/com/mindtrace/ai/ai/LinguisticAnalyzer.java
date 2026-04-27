package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lightweight on-device text analysis engine for journal and gratitude entries.
 *
 * <p>Performs keyword-based NLP without requiring an external model. Extracts:</p>
 * <ul>
 *   <li><b>Sentiment polarity</b> (-1.0 → +1.0)</li>
 *   <li><b>Distress markers</b> — hopelessness, self-blame, catastrophizing</li>
 *   <li><b>Cognitive distortions</b> — CBT framework detection</li>
 *   <li><b>Gratitude item count</b></li>
 *   <li><b>Topic tags</b></li>
 * </ul>
 *
 * <p>This is the Phase-1 analyzer. Phase-2 will use TFLite for deep sentiment.</p>
 */
public class LinguisticAnalyzer {

    // ── Positive signal words ──
    private static final String[] POSITIVE_WORDS = {
            "grateful", "thankful", "happy", "joy", "love", "blessed",
            "excited", "hopeful", "proud", "calm", "peaceful", "accomplished",
            "motivated", "inspired", "content", "appreciate", "wonderful",
            "amazing", "great", "fantastic", "good", "better", "improved",
            "relaxed", "energized", "confident", "optimistic", "fun"
    };

    // ── Negative signal words ──
    private static final String[] NEGATIVE_WORDS = {
            "sad", "anxious", "worried", "stressed", "lonely", "angry",
            "frustrated", "hopeless", "worthless", "exhausted", "overwhelmed",
            "numb", "empty", "afraid", "scared", "terrible", "awful",
            "depressed", "miserable", "trapped", "broken", "failure",
            "useless", "ugly", "hate", "suffer", "crying", "panic"
    };

    // ── Distress markers (clinical signals) ──
    private static final String[] DISTRESS_MARKERS = {
            "can't go on", "no point", "give up", "want to die",
            "hurt myself", "no one cares", "all my fault", "never get better",
            "nothing matters", "worthless", "burden", "end it",
            "can't cope", "falling apart", "losing control", "hopeless",
            // Suicide-specific (Phase 2 — Task 6.B.2)
            "goodbye letter", "giving things away", "no reason to live",
            "better off without me", "wish i were dead", "wish i was dead",
            "don't want to wake up", "tired of living", "can't keep going",
            "after i'm gone", "final message", "forgive me for everything",
            "nobody would notice", "world without me", "just want it to stop",
            "i've decided", "made my decision", "tonight is the night"
    };

    // ── Cognitive distortion patterns (CBT) ──
    private static final String[][] DISTORTION_PATTERNS = {
            {"all_or_nothing", "always", "never", "everything", "nothing", "completely"},
            {"catastrophizing", "worst", "disaster", "ruined", "terrible", "end of the world"},
            {"mind_reading", "they think", "everyone thinks", "they must", "judging me"},
            {"should_statements", "should have", "must have", "ought to", "have to"},
            {"self_blame", "my fault", "i'm to blame", "because of me", "i caused"},
            {"overgeneralization", "always happens", "never works", "every time"},
            {"emotional_reasoning", "i feel like", "feels like i'm", "must be true because"},
            {"labeling", "i'm a failure", "i'm stupid", "i'm worthless", "i'm useless"}
    };

    // ── Topic detection ──
    private static final String[][] TOPIC_PATTERNS = {
            {"work", "work", "job", "boss", "office", "meeting", "deadline", "career"},
            {"relationships", "partner", "friend", "family", "boyfriend", "girlfriend", "marriage"},
            {"sleep", "sleep", "insomnia", "tired", "dream", "nightmare", "bedtime"},
            {"phone_addiction", "phone", "screen", "scrolling", "social media", "instagram", "tiktok"},
            {"anxiety", "anxious", "worry", "panic", "nervous", "fear"},
            {"health", "sick", "pain", "doctor", "exercise", "gym", "medication"},
            {"money", "money", "bills", "debt", "salary", "afford", "financial"},
            {"school", "school", "exam", "study", "homework", "grade", "college"}
    };

    /**
     * Full analysis result for a text entry.
     */
    public static class AnalysisResult {
        public float sentimentScore;       // -1.0 to +1.0
        public String sentimentLabel;      // "very_positive" → "very_negative"
        public List<String> distressFlags;
        public List<String> cognitiveDistortions;
        public List<String> topicTags;
        public List<String> emotionTags;
        public int gratitudeItemCount;
        public int wordCount;

        public AnalysisResult() {
            distressFlags = new ArrayList<>();
            cognitiveDistortions = new ArrayList<>();
            topicTags = new ArrayList<>();
            emotionTags = new ArrayList<>();
        }

        /** Convert list to JSON array string. */
        @NonNull
        public String distressFlagsJson() { return toJson(distressFlags); }
        @NonNull
        public String cognitiveDistortionsJson() { return toJson(cognitiveDistortions); }
        @NonNull
        public String topicTagsJson() { return toJson(topicTags); }
        @NonNull
        public String emotionTagsJson() { return toJson(emotionTags); }

        private String toJson(List<String> list) {
            if (list == null || list.isEmpty()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(list.get(i)).append("\"");
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * Analyze a text entry and return enrichment data.
     *
     * @param text The raw text content
     * @return Full analysis result, or null if text is empty
     */
    @Nullable
    public AnalysisResult analyze(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) return null;

        AnalysisResult result = new AnalysisResult();
        String lower = text.toLowerCase().trim();
        String[] words = lower.split("\\s+");
        result.wordCount = words.length;

        // ── Sentiment scoring ──
        int positiveCount = 0;
        int negativeCount = 0;

        for (String word : words) {
            String cleaned = word.replaceAll("[^a-z']", "");
            for (String pos : POSITIVE_WORDS) {
                if (cleaned.equals(pos)) { positiveCount++; break; }
            }
            for (String neg : NEGATIVE_WORDS) {
                if (cleaned.equals(neg)) { negativeCount++; break; }
            }
        }

        int total = positiveCount + negativeCount;
        if (total > 0) {
            result.sentimentScore = (positiveCount - negativeCount) / (float) Math.max(1, total);
        } else {
            result.sentimentScore = 0f;
        }
        result.sentimentScore = Math.max(-1f, Math.min(1f, result.sentimentScore));
        result.sentimentLabel = classifySentiment(result.sentimentScore);

        // ── Emotion tags ──
        if (positiveCount > 0 && negativeCount > 0) result.emotionTags.add("mixed");
        if (containsAny(lower, "happy", "joy", "excited")) result.emotionTags.add("happiness");
        if (containsAny(lower, "sad", "crying", "depressed")) result.emotionTags.add("sadness");
        if (containsAny(lower, "anxious", "worried", "nervous")) result.emotionTags.add("anxiety");
        if (containsAny(lower, "angry", "frustrated", "furious")) result.emotionTags.add("anger");
        if (containsAny(lower, "grateful", "thankful", "appreciate")) result.emotionTags.add("gratitude");
        if (containsAny(lower, "hopeful", "optimistic")) result.emotionTags.add("hope");
        if (containsAny(lower, "lonely", "isolated", "alone")) result.emotionTags.add("loneliness");
        if (containsAny(lower, "calm", "peaceful", "relaxed")) result.emotionTags.add("peace");

        // ── Distress markers ──
        for (String marker : DISTRESS_MARKERS) {
            if (lower.contains(marker)) {
                result.distressFlags.add(marker.replace(" ", "_"));
            }
        }

        // ── Cognitive distortions ──
        for (String[] pattern : DISTORTION_PATTERNS) {
            String distortionName = pattern[0];
            for (int i = 1; i < pattern.length; i++) {
                if (lower.contains(pattern[i])) {
                    if (!result.cognitiveDistortions.contains(distortionName)) {
                        result.cognitiveDistortions.add(distortionName);
                    }
                    break;
                }
            }
        }

        // ── Topic tags ──
        for (String[] topic : TOPIC_PATTERNS) {
            String topicName = topic[0];
            for (int i = 1; i < topic.length; i++) {
                if (lower.contains(topic[i])) {
                    if (!result.topicTags.contains(topicName)) {
                        result.topicTags.add(topicName);
                    }
                    break;
                }
            }
        }

        // ── Gratitude item count ──
        result.gratitudeItemCount = countGratitudeItems(text);

        return result;
    }

    /**
     * Quick sentiment-only check (lighter than full analyze).
     */
    public float quickSentiment(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) return 0f;
        AnalysisResult result = analyze(text);
        return result != null ? result.sentimentScore : 0f;
    }

    /**
     * Quick distress check — returns true if any distress markers found.
     */
    public boolean hasDistressSignals(@Nullable String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        for (String marker : DISTRESS_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    // ── Helpers ──

    private String classifySentiment(float score) {
        if (score >= 0.6f) return "very_positive";
        if (score >= 0.2f) return "positive";
        if (score > -0.2f) return "neutral";
        if (score > -0.6f) return "negative";
        return "very_negative";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Count distinct gratitude items.
     * Splits by newlines, commas, semicolons, and numbered lists.
     */
    private int countGratitudeItems(String text) {
        String[] lines = text.split("[\\n,;]+|\\d+[.)\\-]\\s");
        int count = 0;
        for (String line : lines) {
            if (line.trim().length() > 2) count++;
        }
        return count;
    }
}
