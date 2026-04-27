package com.mindtrace.ai.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline helpline database for crisis support resources.
 *
 * <h3>Design Principles:</h3>
 * <ul>
 *   <li>All data is bundled locally — no internet required during crisis</li>
 *   <li>Country-specific helplines detected from system locale</li>
 *   <li>Global fallback resources always available</li>
 *   <li>Helplines are NEVER auto-dialed — user must explicitly tap to call</li>
 *   <li>Text-based alternatives provided for users uncomfortable with voice calls</li>
 * </ul>
 *
 * <h3>Supported Regions:</h3>
 * <p>India (IN), United States (US), United Kingdom (GB), Canada (CA),
 * Australia (AU), with global fallbacks for other regions.</p>
 *
 * @see CrisisManager
 */
public class HelplineProvider {

    // ═══════════════════════════════════════════════════════════════════
    // HELPLINE DATA CLASS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * A single helpline resource.
     */
    public static class Helpline {
        /** Organization name. */
        @NonNull public final String name;

        /** Phone number or contact method (e.g., "Text HOME to 741741"). */
        @NonNull public final String contact;

        /** Country/region flag emoji. */
        @NonNull public final String flag;

        /** Type of contact: "phone", "text", "chat", "website". */
        @NonNull public final String type;

        /** Optional description of the service. */
        @Nullable public final String description;

        /** Whether this is available 24/7. */
        public final boolean is24x7;

        /** Country code this helpline belongs to. */
        @NonNull public final String countryCode;

        public Helpline(@NonNull String name, @NonNull String contact,
                        @NonNull String flag, @NonNull String type,
                        @Nullable String description, boolean is24x7,
                        @NonNull String countryCode) {
            this.name = name;
            this.contact = contact;
            this.flag = flag;
            this.type = type;
            this.description = description;
            this.is24x7 = is24x7;
            this.countryCode = countryCode;
        }

        /** Simplified constructor for phone helplines. */
        public Helpline(@NonNull String name, @NonNull String contact,
                        @NonNull String flag) {
            this(name, contact, flag, "phone", null, true, "GLOBAL");
        }

        /** Whether this helpline can be directly dialed. */
        public boolean isDialable() {
            return "phone".equals(type) && contact.matches("[0-9\\-+() ]+");
        }

        /** Whether this is a text-based resource. */
        public boolean isTextBased() {
            return "text".equals(type) || "chat".equals(type);
        }

        /** Get a human-readable availability label. */
        @NonNull
        public String getAvailabilityLabel() {
            return is24x7 ? "Available 24/7" : "Check hours";
        }

        @NonNull
        @Override
        public String toString() {
            return flag + " " + name + " — " + contact;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIMARY API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get helplines for the user's detected country.
     * Falls back to global resources if country is not supported.
     *
     * @return list of helplines, local first, then global
     */
    @NonNull
    public static List<Helpline> getHelplines() {
        String countryCode = Locale.getDefault().getCountry();
        return getHelplines(countryCode);
    }

    /**
     * Get helplines for a specific country code.
     *
     * @param countryCode ISO 3166-1 alpha-2 country code (e.g., "IN", "US")
     * @return list of helplines, local first, then global
     */
    @NonNull
    public static List<Helpline> getHelplines(@Nullable String countryCode) {
        List<Helpline> result = new ArrayList<>();

        // Add country-specific helplines
        if (countryCode != null) {
            List<Helpline> local = HELPLINE_DATABASE.get(countryCode.toUpperCase());
            if (local != null) {
                result.addAll(local);
            }
        }

        // Always add global resources
        result.addAll(getGlobalResources());

        return result;
    }

    /**
     * Get only helplines that can be directly dialed.
     */
    @NonNull
    public static List<Helpline> getDialableHelplines(@Nullable String countryCode) {
        List<Helpline> all = getHelplines(countryCode);
        List<Helpline> dialable = new ArrayList<>();
        for (Helpline h : all) {
            if (h.isDialable()) dialable.add(h);
        }
        return dialable;
    }

    /**
     * Get text/chat-based resources (for users who prefer not to call).
     */
    @NonNull
    public static List<Helpline> getTextBasedResources(@Nullable String countryCode) {
        List<Helpline> all = getHelplines(countryCode);
        List<Helpline> textBased = new ArrayList<>();
        for (Helpline h : all) {
            if (h.isTextBased()) textBased.add(h);
        }
        return textBased;
    }

    /**
     * Get the primary (first) helpline for a country.
     * Used for prominent display in crisis mode.
     */
    @NonNull
    public static Helpline getPrimaryHelpline(@Nullable String countryCode) {
        List<Helpline> lines = getHelplines(countryCode);
        return lines.isEmpty() ? getDefaultGlobalHelpline() : lines.get(0);
    }

    /**
     * Get the emergency number for a country (e.g., 112, 911).
     */
    @NonNull
    public static String getEmergencyNumber(@Nullable String countryCode) {
        if (countryCode == null) return "112";
        switch (countryCode.toUpperCase()) {
            case "US": case "CA": return "911";
            case "GB":             return "999";
            case "AU":             return "000";
            case "IN":             return "112";
            case "EU":             return "112";
            default:               return "112";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPLINE DATABASE
    // ═══════════════════════════════════════════════════════════════════

    private static final Map<String, List<Helpline>> HELPLINE_DATABASE = new HashMap<>();

    static {
        // ── India 🇮🇳 ──
        List<Helpline> india = new ArrayList<>();
        india.add(new Helpline("iCall",
                "9152987821", "🇮🇳", "phone",
                "TISS psychosocial helpline — trained counsellors",
                true, "IN"));
        india.add(new Helpline("Vandrevala Foundation",
                "1860-2662-345", "🇮🇳", "phone",
                "Free, multilingual mental health helpline",
                true, "IN"));
        india.add(new Helpline("AASRA",
                "9820466726", "🇮🇳", "phone",
                "Crisis intervention centre",
                true, "IN"));
        india.add(new Helpline("Snehi",
                "044-24640050", "🇮🇳", "phone",
                "Emotional support and suicide prevention",
                false, "IN"));
        india.add(new Helpline("iCall Chat",
                "icall.org.in", "🇮🇳", "chat",
                "Online chat counselling",
                false, "IN"));
        HELPLINE_DATABASE.put("IN", india);

        // ── United States 🇺🇸 ──
        List<Helpline> usa = new ArrayList<>();
        usa.add(new Helpline("988 Suicide & Crisis Lifeline",
                "988", "🇺🇸", "phone",
                "Free, confidential 24/7 support",
                true, "US"));
        usa.add(new Helpline("Crisis Text Line",
                "Text HOME to 741741", "🇺🇸", "text",
                "Free crisis support via text message",
                true, "US"));
        usa.add(new Helpline("SAMHSA National Helpline",
                "1-800-662-4357", "🇺🇸", "phone",
                "Treatment referral and information",
                true, "US"));
        usa.add(new Helpline("The Trevor Project",
                "1-866-488-7386", "🇺🇸", "phone",
                "LGBTQ+ youth crisis support",
                true, "US"));
        usa.add(new Helpline("Trevor Project Text",
                "Text START to 678-678", "🇺🇸", "text",
                "LGBTQ+ youth text support",
                true, "US"));
        HELPLINE_DATABASE.put("US", usa);

        // ── United Kingdom 🇬🇧 ──
        List<Helpline> uk = new ArrayList<>();
        uk.add(new Helpline("Samaritans",
                "116 123", "🇬🇧", "phone",
                "Free, confidential emotional support",
                true, "GB"));
        uk.add(new Helpline("Shout",
                "Text SHOUT to 85258", "🇬🇧", "text",
                "Free, confidential text support",
                true, "GB"));
        uk.add(new Helpline("CALM (Campaign Against Living Miserably)",
                "0800 58 58 58", "🇬🇧", "phone",
                "For men who need support",
                false, "GB"));
        uk.add(new Helpline("Childline",
                "0800 1111", "🇬🇧", "phone",
                "Support for under 19s",
                true, "GB"));
        HELPLINE_DATABASE.put("GB", uk);

        // ── Canada 🇨🇦 ──
        List<Helpline> canada = new ArrayList<>();
        canada.add(new Helpline("988 Suicide Crisis Helpline",
                "988", "🇨🇦", "phone",
                "National suicide prevention line",
                true, "CA"));
        canada.add(new Helpline("Crisis Text Line Canada",
                "Text HOME to 686868", "🇨🇦", "text",
                "Free crisis support via text",
                true, "CA"));
        canada.add(new Helpline("Kids Help Phone",
                "1-800-668-6868", "🇨🇦", "phone",
                "Young people aged 5-29",
                true, "CA"));
        HELPLINE_DATABASE.put("CA", canada);

        // ── Australia 🇦🇺 ──
        List<Helpline> australia = new ArrayList<>();
        australia.add(new Helpline("Lifeline Australia",
                "13 11 14", "🇦🇺", "phone",
                "24-hour crisis support",
                true, "AU"));
        australia.add(new Helpline("Lifeline Text",
                "Text 0477 13 11 14", "🇦🇺", "text",
                "Text-based crisis support",
                true, "AU"));
        australia.add(new Helpline("Beyond Blue",
                "1300 22 46 36", "🇦🇺", "phone",
                "Anxiety, depression and suicide prevention",
                true, "AU"));
        australia.add(new Helpline("Kids Helpline",
                "1800 55 1800", "🇦🇺", "phone",
                "Counselling for young people aged 5-25",
                true, "AU"));
        HELPLINE_DATABASE.put("AU", australia);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GLOBAL RESOURCES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get global resources that are always shown regardless of country.
     */
    @NonNull
    private static List<Helpline> getGlobalResources() {
        List<Helpline> global = new ArrayList<>();

        global.add(new Helpline("Befrienders Worldwide",
                "befrienders.org/find-support", "🌍", "website",
                "Find a helpline in your country",
                true, "GLOBAL"));

        global.add(new Helpline("International Association for Suicide Prevention",
                "iasp.info/resources/Crisis_Centres", "🌍", "website",
                "Directory of crisis centres worldwide",
                true, "GLOBAL"));

        return global;
    }

    /**
     * Default helpline when no country match is found.
     */
    @NonNull
    private static Helpline getDefaultGlobalHelpline() {
        return new Helpline("Befrienders Worldwide",
                "befrienders.org/find-support", "🌍", "website",
                "Find a helpline in your country",
                true, "GLOBAL");
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get the list of country codes we have helplines for.
     */
    @NonNull
    public static List<String> getSupportedCountries() {
        return new ArrayList<>(HELPLINE_DATABASE.keySet());
    }

    /**
     * Check if we have local helplines for a country.
     */
    public static boolean hasLocalHelplines(@Nullable String countryCode) {
        return countryCode != null && HELPLINE_DATABASE.containsKey(countryCode.toUpperCase());
    }

    /**
     * Get helpline count for a country (including global).
     */
    public static int getHelplineCount(@Nullable String countryCode) {
        return getHelplines(countryCode).size();
    }
}
