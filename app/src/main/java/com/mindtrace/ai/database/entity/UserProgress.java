package com.mindtrace.ai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Tracks the user's gamification progress: XP, streaks, badges, and level.
 *
 * <p>Singleton entity (id=1). Updated each time a task is completed.</p>
 *
 * <h3>XP System:</h3>
 * <ul>
 *   <li>EASY task = 10 XP</li>
 *   <li>MEDIUM task = 25 XP</li>
 *   <li>HARD task = 50 XP</li>
 *   <li>Crisis micro-intervention = 75 XP</li>
 *   <li>Streak bonus: +5 XP per streak day</li>
 * </ul>
 *
 * <h3>Levels:</h3>
 * <ul>
 *   <li>Level 1: 0-99 XP (Beginner)</li>
 *   <li>Level 2: 100-299 XP (Explorer)</li>
 *   <li>Level 3: 300-599 XP (Builder)</li>
 *   <li>Level 4: 600-999 XP (Achiever)</li>
 *   <li>Level 5: 1000+ XP (Champion)</li>
 * </ul>
 */
@Entity(tableName = "user_progress")
public class UserProgress {

    @PrimaryKey
    public int id = 1;

    // ── XP & Level ──
    @ColumnInfo(defaultValue = "0")
    public int totalXp;

    // ── Streaks ──
    @ColumnInfo(defaultValue = "0")
    public int currentStreak;

    @ColumnInfo(defaultValue = "0")
    public int longestStreak;

    @ColumnInfo(defaultValue = "0")
    public long lastCompletionDay;  // epoch-day (ms / 86400000)

    // ── Lifetime stats ──
    @ColumnInfo(defaultValue = "0")
    public int totalTasksCompleted;

    @ColumnInfo(defaultValue = "0")
    public int totalTasksSkipped;

    @ColumnInfo(defaultValue = "0")
    public int totalCrisisTasksCompleted;

    // ── Badges (JSON array of badge IDs) ──
    public String badgesUnlockedJson;

    @ColumnInfo(defaultValue = "0")
    public long lastUpdated;

    // ─────────────────────────────────────────────────────────────────────
    // XP METHODS
    // ─────────────────────────────────────────────────────────────────────

    /** Add XP with streak bonus. */
    public void addXp(int baseXp) {
        int streakBonus = currentStreak * 5;
        this.totalXp += baseXp + streakBonus;
        this.lastUpdated = System.currentTimeMillis();
    }

    /** Get current level (1-5). */
    public int getLevel() {
        if (totalXp >= 1000) return 5;
        if (totalXp >= 600) return 4;
        if (totalXp >= 300) return 3;
        if (totalXp >= 100) return 2;
        return 1;
    }

    /** Get level title. */
    @NonNull
    public String getLevelTitle() {
        switch (getLevel()) {
            case 5: return "Champion";
            case 4: return "Achiever";
            case 3: return "Builder";
            case 2: return "Explorer";
            default: return "Beginner";
        }
    }

    /** Get XP needed for next level. */
    public int getXpToNextLevel() {
        switch (getLevel()) {
            case 1: return 100 - totalXp;
            case 2: return 300 - totalXp;
            case 3: return 600 - totalXp;
            case 4: return 1000 - totalXp;
            default: return 0; // Max level
        }
    }

    /** Get progress to next level as 0.0-1.0. */
    public float getLevelProgress() {
        int[] thresholds = {0, 100, 300, 600, 1000};
        int level = getLevel();
        if (level >= 5) return 1.0f;
        int current = totalXp - thresholds[level - 1];
        int needed = thresholds[level] - thresholds[level - 1];
        return needed > 0 ? (float) current / needed : 0f;
    }

    // ─────────────────────────────────────────────────────────────────────
    // STREAK METHODS
    // ─────────────────────────────────────────────────────────────────────

    /** Update streak based on today's completion. */
    public void recordCompletion() {
        long todayEpochDay = System.currentTimeMillis() / (24L * 60 * 60 * 1000);
        totalTasksCompleted++;

        if (lastCompletionDay == todayEpochDay) {
            // Already completed today — no streak change
            return;
        }

        if (lastCompletionDay == todayEpochDay - 1) {
            // Consecutive day — extend streak
            currentStreak++;
        } else if (lastCompletionDay < todayEpochDay - 1) {
            // Streak broken — restart
            currentStreak = 1;
        }

        if (currentStreak > longestStreak) {
            longestStreak = currentStreak;
        }

        lastCompletionDay = todayEpochDay;
        lastUpdated = System.currentTimeMillis();
    }

    /** Check if streak is active (completed something yesterday or today). */
    public boolean isStreakActive() {
        long todayEpochDay = System.currentTimeMillis() / (24L * 60 * 60 * 1000);
        return lastCompletionDay >= todayEpochDay - 1;
    }

    /** Get streak display text. */
    @NonNull
    public String getStreakDisplay() {
        if (currentStreak <= 0 || !isStreakActive()) return "No streak";
        return "🔥 " + currentStreak + "-day streak";
    }

    // ─────────────────────────────────────────────────────────────────────
    // BADGE SYSTEM
    // ─────────────────────────────────────────────────────────────────────

    /** Badge IDs: FIRST_STEP, STREAK_3, STREAK_7, STREAK_14, STREAK_30,
     *  CRISIS_WARRIOR, WEEK_CHAMPION, CATEGORY_MASTER, XP_100, XP_500, XP_1000 */

    /** Check which new badges should be unlocked. */
    @NonNull
    public String[] checkNewBadges() {
        java.util.List<String> newBadges = new java.util.ArrayList<>();
        String existing = badgesUnlockedJson != null ? badgesUnlockedJson : "";

        if (totalTasksCompleted >= 1 && !existing.contains("FIRST_STEP"))
            newBadges.add("FIRST_STEP");
        if (currentStreak >= 3 && !existing.contains("STREAK_3"))
            newBadges.add("STREAK_3");
        if (currentStreak >= 7 && !existing.contains("STREAK_7"))
            newBadges.add("STREAK_7");
        if (currentStreak >= 14 && !existing.contains("STREAK_14"))
            newBadges.add("STREAK_14");
        if (currentStreak >= 30 && !existing.contains("STREAK_30"))
            newBadges.add("STREAK_30");
        if (totalCrisisTasksCompleted >= 1 && !existing.contains("CRISIS_WARRIOR"))
            newBadges.add("CRISIS_WARRIOR");
        if (totalXp >= 100 && !existing.contains("XP_100"))
            newBadges.add("XP_100");
        if (totalXp >= 500 && !existing.contains("XP_500"))
            newBadges.add("XP_500");
        if (totalXp >= 1000 && !existing.contains("XP_1000"))
            newBadges.add("XP_1000");

        return newBadges.toArray(new String[0]);
    }

    /** Add badges to the unlocked list. */
    public void unlockBadges(@NonNull String[] badges) {
        StringBuilder sb = new StringBuilder(badgesUnlockedJson != null ? badgesUnlockedJson : "");
        for (String badge : badges) {
            if (sb.length() > 0) sb.append(",");
            sb.append(badge);
        }
        badgesUnlockedJson = sb.toString();
    }

    /** Get badge display name. */
    @NonNull
    public static String getBadgeLabel(@NonNull String badgeId) {
        switch (badgeId) {
            case "FIRST_STEP": return "🌱 First Step";
            case "STREAK_3": return "🔥 3-Day Streak";
            case "STREAK_7": return "⭐ Week Warrior";
            case "STREAK_14": return "💫 Two-Week Champion";
            case "STREAK_30": return "🏆 Monthly Master";
            case "CRISIS_WARRIOR": return "🛡️ Crisis Warrior";
            case "XP_100": return "💎 100 XP";
            case "XP_500": return "🎖️ 500 XP";
            case "XP_1000": return "👑 1000 XP";
            default: return badgeId;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "UserProgress{xp=" + totalXp + ", level=" + getLevel() +
                ", streak=" + currentStreak + "/" + longestStreak +
                ", completed=" + totalTasksCompleted + "}";
    }
}
