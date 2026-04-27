package com.mindtrace.ai.ai;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Android package names to behavioural categories for the AI pipeline.
 *
 * <p>Three-layer classification strategy:</p>
 * <ol>
 *   <li><b>Known Map</b> — 80+ hardcoded package→category entries</li>
 *   <li><b>Heuristic</b> — Package name pattern matching for unknown apps</li>
 *   <li><b>System Fallback</b> — PackageManager app category (API 26+)</li>
 * </ol>
 *
 * <h3>Categories:</h3>
 * SOCIAL, VIDEO, ENTERTAINMENT, GAMING, COMMUNICATION, PRODUCTIVITY,
 * EDUCATION, HEALTH, NEWS, UTILITY, FINANCE, SHOPPING, TRAVEL, OTHER
 *
 * @see com.mindtrace.ai.database.entity.AppUsageSnapshot#appCategory
 */
public class AppCategoryMapper {

    // ═════════════════════════════════════════════════════════════════════
    // CATEGORY ENUM
    // ═════════════════════════════════════════════════════════════════════

    /**
     * App category with passive/active classification, dopamine risk level,
     * UI color, icon name, and emoji for dashboard display.
     */
    public enum Category {
        SOCIAL       ("Social Media",    true,  0.9f,  "#E040FB", "group",          "📱"),
        VIDEO        ("Video",           true,  0.8f,  "#FF5252", "play_circle",    "🎬"),
        ENTERTAINMENT("Entertainment",   true,  0.7f,  "#FF7043", "music_note",     "🎵"),
        GAMING       ("Gaming",          true,  0.85f, "#AB47BC", "sports_esports", "🎮"),
        NEWS         ("News",            true,  0.5f,  "#42A5F5", "newspaper",      "📰"),
        SHOPPING     ("Shopping",        true,  0.4f,  "#FFA726", "shopping_bag",   "🛒"),
        COMMUNICATION("Communication",   false, 0.2f,  "#66BB6A", "chat",           "💬"),
        PRODUCTIVITY ("Productivity",    false, 0.05f, "#26A69A", "work",           "💼"),
        EDUCATION    ("Education",       false, 0.1f,  "#29B6F6", "school",         "📚"),
        HEALTH       ("Health",          false, 0.05f, "#4ADE80", "favorite",       "💚"),
        FINANCE      ("Finance",         false, 0.1f,  "#78909C", "account_balance","💰"),
        TRAVEL       ("Travel",          false, 0.15f, "#26C6DA", "explore",        "✈️"),
        UTILITY      ("Utility",         false, 0.05f, "#8896B0", "build",          "🔧"),
        OTHER        ("Other",           false, 0.3f,  "#546E7A", "apps",           "📦");

        public final String displayName;
        public final boolean isPassive;
        public final float dopamineRisk;
        public final String colorHex;     // Premium dark theme color
        public final String iconName;     // Material icon name
        public final String emoji;        // Fallback emoji for quick display

        Category(String displayName, boolean isPassive, float dopamineRisk,
                 String colorHex, String iconName, String emoji) {
            this.displayName = displayName;
            this.isPassive = isPassive;
            this.dopamineRisk = dopamineRisk;
            this.colorHex = colorHex;
            this.iconName = iconName;
            this.emoji = emoji;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // KNOWN PACKAGE MAP — Layer 1
    // ═════════════════════════════════════════════════════════════════════

    private static final Map<String, Category> KNOWN_APPS = new HashMap<>();

    static {
        // ── Social Media ──
        KNOWN_APPS.put("com.instagram.android", Category.SOCIAL);
        KNOWN_APPS.put("com.facebook.katana", Category.SOCIAL);
        KNOWN_APPS.put("com.facebook.lite", Category.SOCIAL);
        KNOWN_APPS.put("com.twitter.android", Category.SOCIAL);
        KNOWN_APPS.put("com.twitter.android.lite", Category.SOCIAL);
        KNOWN_APPS.put("com.zhiliaoapp.musically", Category.SOCIAL); // TikTok
        KNOWN_APPS.put("com.ss.android.ugc.trill", Category.SOCIAL); // TikTok alt
        KNOWN_APPS.put("com.snapchat.android", Category.SOCIAL);
        KNOWN_APPS.put("com.reddit.frontpage", Category.SOCIAL);
        KNOWN_APPS.put("com.pinterest", Category.SOCIAL);
        KNOWN_APPS.put("in.mohalla.sharechat", Category.SOCIAL);
        KNOWN_APPS.put("com.linkedin.android", Category.SOCIAL);
        KNOWN_APPS.put("com.tumblr", Category.SOCIAL);
        KNOWN_APPS.put("com.discord", Category.SOCIAL);
        KNOWN_APPS.put("org.telegram.messenger", Category.SOCIAL);
        KNOWN_APPS.put("com.bereal.ft", Category.SOCIAL);
        KNOWN_APPS.put("com.lemon8.android", Category.SOCIAL);

        // ── Video / Streaming ──
        KNOWN_APPS.put("com.google.android.youtube", Category.VIDEO);
        KNOWN_APPS.put("com.google.android.apps.youtube.creator", Category.VIDEO);
        KNOWN_APPS.put("com.netflix.mediaclient", Category.VIDEO);
        KNOWN_APPS.put("com.amazon.avod.thirdpartyclient", Category.VIDEO); // Prime Video
        KNOWN_APPS.put("in.startv.hotstar", Category.VIDEO); // Hotstar
        KNOWN_APPS.put("com.jio.media.ondemand", Category.VIDEO); // JioCinema
        KNOWN_APPS.put("com.sonyliv", Category.VIDEO);
        KNOWN_APPS.put("com.disney.disneyplus", Category.VIDEO);
        KNOWN_APPS.put("com.mxtech.videoplayer.ad", Category.VIDEO);
        KNOWN_APPS.put("com.voot.android", Category.VIDEO);
        KNOWN_APPS.put("tv.twitch.android.app", Category.VIDEO);
        KNOWN_APPS.put("com.spotify.music", Category.ENTERTAINMENT);
        KNOWN_APPS.put("com.apple.android.music", Category.ENTERTAINMENT);
        KNOWN_APPS.put("com.gaana", Category.ENTERTAINMENT);
        KNOWN_APPS.put("com.jio.media.jiobeats", Category.ENTERTAINMENT); // JioSaavn

        // ── Gaming ──
        KNOWN_APPS.put("com.supercell.clashofclans", Category.GAMING);
        KNOWN_APPS.put("com.supercell.clashroyale", Category.GAMING);
        KNOWN_APPS.put("com.tencent.ig", Category.GAMING); // PUBG
        KNOWN_APPS.put("com.dts.freefireth", Category.GAMING);
        KNOWN_APPS.put("com.garena.game.codm", Category.GAMING);
        KNOWN_APPS.put("com.activision.callofduty.shooter", Category.GAMING);
        KNOWN_APPS.put("com.mojang.minecraftpe", Category.GAMING);
        KNOWN_APPS.put("com.kiloo.subwaysurf", Category.GAMING);
        KNOWN_APPS.put("com.king.candycrushsaga", Category.GAMING);
        KNOWN_APPS.put("io.supercent.game", Category.GAMING);

        // ── Communication ──
        KNOWN_APPS.put("com.whatsapp", Category.COMMUNICATION);
        KNOWN_APPS.put("com.whatsapp.w4b", Category.COMMUNICATION);
        KNOWN_APPS.put("com.google.android.apps.messaging", Category.COMMUNICATION);
        KNOWN_APPS.put("com.facebook.orca", Category.COMMUNICATION); // Messenger
        KNOWN_APPS.put("com.google.android.gm", Category.COMMUNICATION); // Gmail
        KNOWN_APPS.put("com.microsoft.office.outlook", Category.COMMUNICATION);
        KNOWN_APPS.put("com.skype.raider", Category.COMMUNICATION);
        KNOWN_APPS.put("us.zoom.videomeetings", Category.COMMUNICATION);
        KNOWN_APPS.put("com.google.android.apps.meetings", Category.COMMUNICATION); // Meet
        KNOWN_APPS.put("com.Slack", Category.COMMUNICATION);
        KNOWN_APPS.put("com.microsoft.teams", Category.COMMUNICATION);
        KNOWN_APPS.put("com.viber.voip", Category.COMMUNICATION);

        // ── Productivity ──
        KNOWN_APPS.put("com.google.android.apps.docs", Category.PRODUCTIVITY); // Docs
        KNOWN_APPS.put("com.google.android.apps.docs.editors.sheets", Category.PRODUCTIVITY);
        KNOWN_APPS.put("com.google.android.apps.docs.editors.slides", Category.PRODUCTIVITY);
        KNOWN_APPS.put("com.microsoft.office.word", Category.PRODUCTIVITY);
        KNOWN_APPS.put("com.microsoft.office.excel", Category.PRODUCTIVITY);
        KNOWN_APPS.put("com.microsoft.office.powerpoint", Category.PRODUCTIVITY);
        KNOWN_APPS.put("com.google.android.keep", Category.PRODUCTIVITY); // Keep
        KNOWN_APPS.put("com.todoist", Category.PRODUCTIVITY);
        KNOWN_APPS.put("com.ticktick.task", Category.PRODUCTIVITY);
        KNOWN_APPS.put("com.notion.id", Category.PRODUCTIVITY);
        KNOWN_APPS.put("com.google.android.calendar", Category.PRODUCTIVITY);
        KNOWN_APPS.put("com.google.android.apps.tasks", Category.PRODUCTIVITY);

        // ── Education ──
        KNOWN_APPS.put("com.duolingo", Category.EDUCATION);
        KNOWN_APPS.put("com.byjus.thelearningapp", Category.EDUCATION);
        KNOWN_APPS.put("co.unacademy.learningapp", Category.EDUCATION);
        KNOWN_APPS.put("com.google.android.apps.classroom", Category.EDUCATION);
        KNOWN_APPS.put("com.udemy.android", Category.EDUCATION);
        KNOWN_APPS.put("org.coursera.android", Category.EDUCATION);
        KNOWN_APPS.put("org.khanacademy.android", Category.EDUCATION);

        // ── News ──
        KNOWN_APPS.put("com.google.android.apps.magazines", Category.NEWS); // Google News
        KNOWN_APPS.put("com.eterno", Category.NEWS); // Inshorts
        KNOWN_APPS.put("com.dailyhunt.tv", Category.NEWS);

        // ── Health ──
        KNOWN_APPS.put("com.google.android.apps.fitness", Category.HEALTH);
        KNOWN_APPS.put("com.calm.android", Category.HEALTH);
        KNOWN_APPS.put("com.headspace.android", Category.HEALTH);

        // ── Finance ──
        KNOWN_APPS.put("com.google.android.apps.nbu.paisa.user", Category.FINANCE); // GPay
        KNOWN_APPS.put("net.one97.paytm", Category.FINANCE);
        KNOWN_APPS.put("com.phonepe.app", Category.FINANCE);

        // ── Shopping ──
        KNOWN_APPS.put("com.amazon.mShop.android.shopping", Category.SHOPPING);
        KNOWN_APPS.put("com.flipkart.android", Category.SHOPPING);
        KNOWN_APPS.put("com.myntra.android", Category.SHOPPING);
        KNOWN_APPS.put("club.cred.app", Category.SHOPPING);
    }

    // ═════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Classify an app by package name using 3-layer strategy.
     *
     * @param packageName Android package name
     * @param context     Optional context for PackageManager fallback
     * @return Category enum value, never null
     */
    @NonNull
    public static Category getCategory(@NonNull String packageName, @Nullable Context context) {
        // Layer 1: Known map (instant)
        Category known = KNOWN_APPS.get(packageName);
        if (known != null) return known;

        // Layer 2: Heuristic pattern matching
        Category heuristic = classifyByPattern(packageName);
        if (heuristic != null) return heuristic;

        // Layer 3: System PackageManager (API 26+)
        if (context != null) {
            Category system = classifyBySystem(packageName, context);
            if (system != null) return system;
        }

        return Category.OTHER;
    }

    /** Convenience overload without context. */
    @NonNull
    public static Category getCategory(@NonNull String packageName) {
        return getCategory(packageName, null);
    }

    /** Whether this app is a passive consumption app. */
    public static boolean isPassive(@NonNull String packageName) {
        return getCategory(packageName).isPassive;
    }

    /** Whether this app is a productive/active app (not passive, not utility). */
    public static boolean isProductive(@NonNull String packageName) {
        Category cat = getCategory(packageName);
        return !cat.isPassive && cat != Category.UTILITY && cat != Category.OTHER;
    }

    /** Get dopamine risk score for an app (0.0–1.0). */
    public static float getDopamineRisk(@NonNull String packageName) {
        return getCategory(packageName).dopamineRisk;
    }

    /** Convert a package name to a human-readable app name heuristically. */
    @NonNull
    public static String getAppName(@Nullable String packageName) {
        if (packageName == null || packageName.isEmpty()) return "Unknown App";
        
        // Handle common ones directly
        if (packageName.contains("whatsapp")) return "WhatsApp";
        if (packageName.contains("instagram")) return "Instagram";
        if (packageName.contains("youtube")) return "YouTube";
        if (packageName.contains("facebook")) return "Facebook";
        if (packageName.contains("twitter") || packageName.equals("com.twitter.android")) return "Twitter / X";
        if (packageName.contains("snapchat")) return "Snapchat";
        if (packageName.contains("reddit")) return "Reddit";
        if (packageName.contains("tiktok") || packageName.equals("com.zhiliaoapp.musically")) return "TikTok";
        if (packageName.contains("chrome")) return "Chrome";
        if (packageName.contains("spotify")) return "Spotify";
        if (packageName.contains("netflix")) return "Netflix";
        
        // Generic fallback
        String[] parts = packageName.split("\\.");
        String name = parts.length > 0 ? parts[parts.length - 1] : packageName;
        if (name.equalsIgnoreCase("android") && parts.length > 1) {
            name = parts[parts.length - 2];
        }
        if (name.length() > 0) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
        }
        return name;
    }

    // ═════════════════════════════════════════════════════════════════════
    // HEURISTIC ENGINE — Layer 2
    // ═════════════════════════════════════════════════════════════════════

    @Nullable
    private static Category classifyByPattern(@NonNull String pkg) {
        String lower = pkg.toLowerCase();

        // Social patterns
        if (containsAny(lower, "social", "instagram", "tiktok", "twitter", "snap",
                "reddit", "tumblr", "pinterest")) return Category.SOCIAL;

        // Video patterns
        if (containsAny(lower, "youtube", "netflix", "video", "player", "stream",
                "vod", "cinema", "movie", "tv.")) return Category.VIDEO;

        // Gaming patterns
        if (containsAny(lower, "game", "gaming", "supercell", "gameloft",
                "tencent.ig", "garena", "arcade", "puzzle")) return Category.GAMING;

        // Music/Entertainment
        if (containsAny(lower, "music", "spotify", "audio", "podcast",
                "radio", "fm")) return Category.ENTERTAINMENT;

        // Communication
        if (containsAny(lower, "messenger", "chat", "mail", "email",
                "sms", "call", "dialer", "voip", "meeting")) return Category.COMMUNICATION;

        // Productivity
        if (containsAny(lower, "office", "docs", "sheet", "note", "task",
                "calendar", "drive", "cloud", "editor")) return Category.PRODUCTIVITY;

        // Education
        if (containsAny(lower, "learn", "edu", "course", "study", "academy",
                "school", "university", "quiz")) return Category.EDUCATION;

        // Health
        if (containsAny(lower, "health", "fitness", "workout", "meditation",
                "calm", "sleep", "yoga")) return Category.HEALTH;

        // News
        if (containsAny(lower, "news", "magazine", "times", "daily",
                "journal", "press")) return Category.NEWS;

        // Finance
        if (containsAny(lower, "bank", "pay", "finance", "money", "wallet",
                "invest", "stock", "upi")) return Category.FINANCE;

        // Shopping
        if (containsAny(lower, "shop", "store", "market", "buy", "cart",
                "deal", "offer")) return Category.SHOPPING;

        return null; // Unknown — fall to Layer 3
    }

    private static boolean containsAny(String text, String... patterns) {
        for (String p : patterns) {
            if (text.contains(p)) return true;
        }
        return false;
    }

    // ═════════════════════════════════════════════════════════════════════
    // SYSTEM FALLBACK — Layer 3 (API 26+)
    // ═════════════════════════════════════════════════════════════════════

    @Nullable
    private static Category classifyBySystem(@NonNull String packageName, @NonNull Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            int cat = info.category;

            switch (cat) {
                case ApplicationInfo.CATEGORY_SOCIAL: return Category.SOCIAL;
                case ApplicationInfo.CATEGORY_VIDEO: return Category.VIDEO;
                case ApplicationInfo.CATEGORY_GAME: return Category.GAMING;
                case ApplicationInfo.CATEGORY_AUDIO: return Category.ENTERTAINMENT;
                case ApplicationInfo.CATEGORY_NEWS: return Category.NEWS;
                case ApplicationInfo.CATEGORY_MAPS: return Category.TRAVEL;
                case ApplicationInfo.CATEGORY_PRODUCTIVITY: return Category.PRODUCTIVITY;
                case ApplicationInfo.CATEGORY_IMAGE: return Category.ENTERTAINMENT;
                default: return null;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // SYSTEM APP DETECTION
    // ═════════════════════════════════════════════════════════════════════

    /** Check if a package is a system app that should be hidden from user. */
    public static boolean isSystemApp(@NonNull String packageName) {
        return packageName.startsWith("com.android.")
                || packageName.startsWith("com.google.android.inputmethod")
                || packageName.startsWith("com.google.android.gms")
                || packageName.startsWith("com.google.android.gsf")
                || packageName.startsWith("com.google.android.providers")
                || packageName.startsWith("com.google.android.ext")
                || packageName.startsWith("com.google.android.permissioncontroller")
                || packageName.startsWith("com.google.android.packageinstaller")
                || packageName.startsWith("android")
                || packageName.equals("com.google.android.apps.nexuslauncher")
                || packageName.equals("com.sec.android.app.launcher")
                || packageName.equals("com.miui.home");
    }

    /** Check if this app should be shown in the usage dashboard. */
    public static boolean isUserVisible(@NonNull String packageName) {
        return !isSystemApp(packageName);
    }

    // ═════════════════════════════════════════════════════════════════════
    // UI HELPERS — Colors, Icons, Emojis
    // ═════════════════════════════════════════════════════════════════════

    /** Get the hex color string for a category (e.g. "#E040FB"). */
    @NonNull
    public static String getCategoryColor(@NonNull String categoryName) {
        try {
            return Category.valueOf(categoryName).colorHex;
        } catch (IllegalArgumentException e) {
            return Category.OTHER.colorHex;
        }
    }

    /** Get the hex color for a Category enum. */
    @NonNull
    public static String getCategoryColor(@NonNull Category category) {
        return category.colorHex;
    }

    /** Get the parsed int color for a category (for Canvas/Paint). */
    public static int getCategoryColorInt(@NonNull Category category) {
        return android.graphics.Color.parseColor(category.colorHex);
    }

    /** Get the Material icon name for a category (e.g. "group"). */
    @NonNull
    public static String getCategoryIcon(@NonNull String categoryName) {
        try {
            return Category.valueOf(categoryName).iconName;
        } catch (IllegalArgumentException e) {
            return Category.OTHER.iconName;
        }
    }

    /** Get emoji for a category (e.g. "📱" for SOCIAL). */
    @NonNull
    public static String getCategoryEmoji(@NonNull String categoryName) {
        try {
            return Category.valueOf(categoryName).emoji;
        } catch (IllegalArgumentException e) {
            return Category.OTHER.emoji;
        }
    }

    /** Get emoji for a Category enum. */
    @NonNull
    public static String getCategoryEmoji(@NonNull Category category) {
        return category.emoji;
    }

    /** Safe Category lookup from string — returns OTHER if invalid. */
    @NonNull
    public static Category fromString(@Nullable String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) return Category.OTHER;
        try {
            return Category.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            return Category.OTHER;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // ANALYTICS — Diversity & aggregate scoring
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Compute Shannon entropy-based app diversity score.
     * Measures how evenly usage is spread across apps.
     *
     * @param usageTimes Map of packageName → usage time in ms
     * @return 0.0 (single app dominated) to 1.0 (perfectly even spread)
     */
    public static float computeAppDiversity(@NonNull Map<String, Long> usageTimes) {
        if (usageTimes.isEmpty()) return 0f;
        if (usageTimes.size() == 1) return 0f;

        long total = 0;
        for (long t : usageTimes.values()) total += t;
        if (total <= 0) return 0f;

        double entropy = 0;
        for (long t : usageTimes.values()) {
            if (t <= 0) continue;
            double p = (double) t / total;
            entropy -= p * Math.log(p);
        }

        // Normalize to 0-1 using max possible entropy (log(n))
        double maxEntropy = Math.log(usageTimes.size());
        if (maxEntropy <= 0) return 0f;

        return (float) Math.min(1.0, entropy / maxEntropy);
    }

    /**
     * Compute weighted dopamine risk score for a set of apps.
     * Weights each app's dopamine risk by its share of total usage time.
     *
     * @param usageTimes Map of packageName → usage time in ms
     * @return 0.0 (all productive) to 1.0 (all high-dopamine)
     */
    public static float computeWeightedDopamineScore(@NonNull Map<String, Long> usageTimes) {
        if (usageTimes.isEmpty()) return 0f;

        long total = 0;
        for (long t : usageTimes.values()) total += t;
        if (total <= 0) return 0f;

        float weightedScore = 0;
        for (Map.Entry<String, Long> entry : usageTimes.entrySet()) {
            float weight = (float) entry.getValue() / total;
            float risk = getCategory(entry.getKey()).dopamineRisk;
            weightedScore += weight * risk;
        }

        return Math.min(1f, Math.max(0f, weightedScore));
    }

    /**
     * Build a category time breakdown from per-app usage times.
     *
     * @param usageTimes Map of packageName → usage time in ms
     * @return Map of Category name → total time in ms
     */
    @NonNull
    public static Map<String, Long> buildCategoryBreakdown(@NonNull Map<String, Long> usageTimes) {
        Map<String, Long> breakdown = new HashMap<>();
        for (Category cat : Category.values()) {
            breakdown.put(cat.name(), 0L);
        }

        for (Map.Entry<String, Long> entry : usageTimes.entrySet()) {
            Category cat = getCategory(entry.getKey());
            breakdown.put(cat.name(), breakdown.get(cat.name()) + entry.getValue());
        }

        return breakdown;
    }
}
