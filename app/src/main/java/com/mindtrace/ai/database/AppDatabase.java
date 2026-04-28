package com.mindtrace.ai.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.dao.UsageDao;
import com.mindtrace.ai.database.dao.UserDao;
import com.mindtrace.ai.database.dao.AppUsageSnapshotDao;
import com.mindtrace.ai.database.dao.BehaviorSnapshotDao;
import com.mindtrace.ai.database.dao.BehaviorUsageSummaryDao;
import com.mindtrace.ai.database.dao.DailyResetSessionDao;
import com.mindtrace.ai.database.dao.OnboardingProfileDao;
import com.mindtrace.ai.database.dao.UserBaselineDao;
import com.mindtrace.ai.database.dao.UsageSessionDao;
import com.mindtrace.ai.database.dao.WellnessSummaryDao;
import com.mindtrace.ai.database.entity.AppUsageSnapshot;
import com.mindtrace.ai.database.entity.BehaviorSnapshotEntity;
import com.mindtrace.ai.database.entity.BehaviorUsageSummary;
import com.mindtrace.ai.database.entity.DailyUsage;
import com.mindtrace.ai.database.entity.DailyResetSession;
import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.database.entity.UsageSession;
import com.mindtrace.ai.database.entity.UserBaseline;
import com.mindtrace.ai.database.entity.User;
import com.mindtrace.ai.database.entity.WellnessSummary;
import com.mindtrace.ai.database.dao.WeeklyAssessmentDao;
import com.mindtrace.ai.database.entity.WeeklyAssessment;
import com.mindtrace.ai.database.dao.JournalDao;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.entity.JournalEntry;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.database.entity.UserProgress;
import com.mindtrace.ai.database.entity.CrisisEvent;
import com.mindtrace.ai.database.entity.SafetyPlan;
import com.mindtrace.ai.database.entity.TrustedContact;
import com.mindtrace.ai.database.entity.ExerciseCompletion;
import com.mindtrace.ai.database.dao.UserProgressDao;
import com.mindtrace.ai.database.dao.CrisisEventDao;
import com.mindtrace.ai.database.dao.SafetyPlanDao;
import com.mindtrace.ai.database.dao.ExerciseCompletionDao;
import com.mindtrace.ai.database.dao.TrustedContactDao;
import com.mindtrace.ai.database.dao.SuicideRiskEventDao;
import com.mindtrace.ai.database.dao.ErrorLogDao;
import com.mindtrace.ai.database.entity.SuicideRiskEvent;
import com.mindtrace.ai.database.entity.ErrorLog;

@Database(
        entities = {
                User.class,
                DailyUsage.class,
                QuestionnaireResponse.class,
                InterventionTask.class,
                OnboardingProfile.class,
                AppUsageSnapshot.class,
                WellnessSummary.class,
                UserBaseline.class,
                BehaviorSnapshotEntity.class,
                DailyResetSession.class,
                UsageSession.class,
                BehaviorUsageSummary.class,
                WeeklyAssessment.class,
                JournalEntry.class,
                RiskClassification.class,
                UserProgress.class,
                CrisisEvent.class,
                SafetyPlan.class,
                TrustedContact.class,
                ExerciseCompletion.class,
                SuicideRiskEvent.class,
                ErrorLog.class
        },
        version = 28,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN focusLevel TEXT");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN feltDistracted INTEGER");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN energyLevel TEXT");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN completedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN skippedAt INTEGER NOT NULL DEFAULT 0");

            database.execSQL("CREATE TABLE IF NOT EXISTS `app_usage_snapshots` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`dayTimestamp` INTEGER NOT NULL, " +
                    "`recordedAt` INTEGER NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`appName` TEXT, " +
                    "`usageTimeMillis` INTEGER NOT NULL, " +
                    "`usagePercentage` INTEGER NOT NULL, " +
                    "`launchCount` INTEGER NOT NULL, " +
                    "`firstOpenedTimestamp` INTEGER NOT NULL, " +
                    "`lastUsedTimestamp` INTEGER NOT NULL, " +
                    "`isSystemApp` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_app_usage_snapshots_dayTimestamp_packageName` ON `app_usage_snapshots` (`dayTimestamp`, `packageName`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_app_usage_snapshots_dayTimestamp` ON `app_usage_snapshots` (`dayTimestamp`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `wellness_summaries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`dayTimestamp` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`wellnessState` TEXT, " +
                    "`riskLevel` TEXT, " +
                    "`explanationText` TEXT, " +
                    "`reasonSummary` TEXT, " +
                    "`nextBestAction` TEXT, " +
                    "`screenTimeMillis` INTEGER NOT NULL, " +
                    "`taskCompletionScore` INTEGER NOT NULL, " +
                    "`topAppName` TEXT, " +
                    "`topAppPackage` TEXT, " +
                    "`supportSuggested` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wellness_summaries_dayTimestamp` ON `wellness_summaries` (`dayTimestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_wellness_summaries_createdAt` ON `wellness_summaries` (`createdAt`)");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `user_baseline` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`avgScreenTime7d` REAL NOT NULL, " +
                    "`avgScreenTime30d` REAL NOT NULL, " +
                    "`avgSleep7d` REAL NOT NULL, " +
                    "`avgStress7d` REAL NOT NULL, " +
                    "`avgMoodScore7d` REAL NOT NULL, " +
                    "`avgTaskCompletion7d` REAL NOT NULL, " +
                    "`lastUpdated` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `behavior_snapshots` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`dayTimestamp` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`appSwitchCount` INTEGER NOT NULL, " +
                    "`rapidSwitchCount` INTEGER NOT NULL, " +
                    "`bingeSessionCount` INTEGER NOT NULL, " +
                    "`lateNightUsageMillis` INTEGER NOT NULL, " +
                    "`totalForegroundMillis` INTEGER NOT NULL, " +
                    "`longestSessionMillis` INTEGER NOT NULL, " +
                    "`hasLoopPattern` INTEGER NOT NULL, " +
                    "`dominantAppPackage` TEXT, " +
                    "`summaryLabel` TEXT, " +
                    "`explanation` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_behavior_snapshots_dayTimestamp` ON `behavior_snapshots` (`dayTimestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_behavior_snapshots_timestamp` ON `behavior_snapshots` (`timestamp`)");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `onboarding_profile` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`name` TEXT, " +
                    "`ageRange` TEXT, " +
                    "`primaryGoal` TEXT, " +
                    "`helpAreasCsv` TEXT, " +
                    "`stressLevel` INTEGER NOT NULL, " +
                    "`anxietyLevel` INTEGER NOT NULL, " +
                    "`motivationLevel` INTEGER NOT NULL, " +
                    "`lonelinessLevel` INTEGER NOT NULL, " +
                    "`selfDoubtLevel` INTEGER NOT NULL, " +
                    "`overthinkingLevel` INTEGER NOT NULL, " +
                    "`sleepHours` REAL NOT NULL, " +
                    "`sleepQuality` INTEGER NOT NULL, " +
                    "`focusLevel` INTEGER NOT NULL, " +
                    "`energyLevel` INTEGER NOT NULL, " +
                    "`workPressure` INTEGER NOT NULL, " +
                    "`distractionLevel` INTEGER NOT NULL, " +
                    "`socialMediaUse` INTEGER NOT NULL, " +
                    "`lateNightPhoneUse` INTEGER NOT NULL, " +
                    "`appAddictionRisk` INTEGER NOT NULL, " +
                    "`overusePatternLevel` INTEGER NOT NULL, " +
                    "`bingeScrollingLevel` INTEGER NOT NULL, " +
                    "`appSwitchingHabit` INTEGER NOT NULL, " +
                    "`routineConsistency` INTEGER NOT NULL, " +
                    "`productiveHabits` INTEGER NOT NULL, " +
                    "`procrastinationLevel` INTEGER NOT NULL, " +
                    "`physicalActivity` INTEGER NOT NULL, " +
                    "`feelingStuck` INTEGER NOT NULL, " +
                    "`socialSupportLevel` INTEGER NOT NULL, " +
                    "`supportNeeded` INTEGER NOT NULL, " +
                    "`safetySupportEnabled` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))");
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `daily_reset_sessions` (" +
                    "`dayTimestamp` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, " +
                    "`resetTitle` TEXT, " +
                    "`focusTask` TEXT, " +
                    "`firstAction` TEXT, " +
                    "`warningItem` TEXT, " +
                    "`timerDurationMinutes` INTEGER NOT NULL, " +
                    "`isCompleted` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL, " +
                    "`readinessLevel` INTEGER NOT NULL, " +
                    "`reflectionNote` TEXT, " +
                    "PRIMARY KEY(`dayTimestamp`))");
        }
    };

    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN percentOfTotalUsage INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN foregroundSessions INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN appCategory TEXT");
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN isUserVisible INTEGER NOT NULL DEFAULT 1");

            database.execSQL("ALTER TABLE daily_usage ADD COLUMN activeForegroundTimeMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN totalLaunchCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN totalAppSwitchCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN topAppPackageName TEXT");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN appsTrackedCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN highRiskAppCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN snapshotCreatedAt INTEGER NOT NULL DEFAULT 0");

            database.execSQL("CREATE TABLE IF NOT EXISTS `usage_sessions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`dayTimestamp` INTEGER NOT NULL, " +
                    "`packageName` TEXT, " +
                    "`sessionStart` INTEGER NOT NULL, " +
                    "`sessionEnd` INTEGER NOT NULL, " +
                    "`durationMillis` INTEGER NOT NULL, " +
                    "`wasShortSession` INTEGER NOT NULL, " +
                    "`wasLateNightSession` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_sessions_dayTimestamp` ON `usage_sessions` (`dayTimestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_sessions_packageName` ON `usage_sessions` (`packageName`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_sessions_dayTimestamp_packageName` ON `usage_sessions` (`dayTimestamp`, `packageName`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `behavior_usage_summaries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`dayTimestamp` INTEGER NOT NULL, " +
                    "`totalUsage` INTEGER NOT NULL, " +
                    "`fragmentedUsageScore` INTEGER NOT NULL, " +
                    "`bingeScore` INTEGER NOT NULL, " +
                    "`switchScore` INTEGER NOT NULL, " +
                    "`topAppDominanceScore` INTEGER NOT NULL, " +
                    "`lateNightPenaltyScore` INTEGER NOT NULL, " +
                    "`distractionPatternScore` INTEGER NOT NULL, " +
                    "`summaryLabel` TEXT, " +
                    "`explanatoryNotes` TEXT, " +
                    "`createdAt` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_behavior_usage_summaries_dayTimestamp` ON `behavior_usage_summaries` (`dayTimestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_behavior_usage_summaries_createdAt` ON `behavior_usage_summaries` (`createdAt`)");
        }
    };

    /**
     * Migration v8 → v9: Expand DailyUsage for advanced digital behaviour monitoring.
     *
     * Adds:
     * - Sleep proxy fields (firstUnlockTime, lastScreenOffTime)
     * - Consumption pattern fields (passiveConsumptionRatio, productiveTimeMillis,
     *   socialMediaTimeMillis, entertainmentTimeMillis)
     * - Engagement quality fields (scrollIntensityScore, notificationResponseAvgMs)
     * - Category analytics (categoryBreakdownJson, hourlyBreakdownJson)
     * - Unique index on date column for data integrity
     */
    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // ── Sleep proxy fields ──
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN firstUnlockTime INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN lastScreenOffTime INTEGER NOT NULL DEFAULT 0");

            // ── Consumption pattern fields ──
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN passiveConsumptionRatio REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN productiveTimeMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN socialMediaTimeMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN entertainmentTimeMillis INTEGER NOT NULL DEFAULT 0");

            // ── Engagement quality fields ──
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN scrollIntensityScore REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN notificationResponseAvgMs INTEGER NOT NULL DEFAULT 0");

            // ── Category analytics (JSON) ──
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN categoryBreakdownJson TEXT");
            database.execSQL("ALTER TABLE daily_usage ADD COLUMN hourlyBreakdownJson TEXT");

            // ── Index for data integrity ──
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_usage_date` ON `daily_usage` (`date`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_usage_snapshotCreatedAt` ON `daily_usage` (`snapshotCreatedAt`)");
        }
    };

    /**
     * Migration v9 → v10: Expand AppUsageSnapshot for behavioural classification.
     *
     * Adds per-app flags: passive classification, binge detection, session analytics,
     * and night usage percentage. Also adds index on appCategory for fast aggregation.
     */
    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // ── Passive/active classification ──
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN isPassiveApp INTEGER NOT NULL DEFAULT 0");

            // ── Behavioural flags ──
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN bingeFlag INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN bingeSessionCount INTEGER NOT NULL DEFAULT 0");

            // ── Session analytics ──
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN averageSessionLengthMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN longestSessionMs INTEGER NOT NULL DEFAULT 0");

            // ── Night usage per app ──
            database.execSQL("ALTER TABLE app_usage_snapshots ADD COLUMN nightUsagePercent INTEGER NOT NULL DEFAULT 0");

            // ── Index for category-level queries ──
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_app_usage_snapshots_appCategory` ON `app_usage_snapshots` (`appCategory`)");
        }
    };

    /**
     * Migration v10 → v11: Expand UsageSession for granular session analytics.
     *
     * Adds session type classification, interruption tracking, duration categories,
     * notification-trigger flag, and previous-app tracking for loop detection.
     */
    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // ── Session classification ──
            database.execSQL("ALTER TABLE usage_sessions ADD COLUMN sessionType TEXT DEFAULT 'unknown'");
            database.execSQL("ALTER TABLE usage_sessions ADD COLUMN durationCategory TEXT DEFAULT 'normal'");

            // ── Context tracking ──
            database.execSQL("ALTER TABLE usage_sessions ADD COLUMN interruptionCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE usage_sessions ADD COLUMN wasNotificationTriggered INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE usage_sessions ADD COLUMN previousAppPackage TEXT");

            // ── Index for session type queries ──
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_sessions_sessionType` ON `usage_sessions` (`sessionType`)");
        }
    };

    /**
     * Migration v11 → v12: Transform BehaviorSnapshot into 4-layer intelligence entity.
     *
     * Adds: Attention layer (fragmentation, compulsive score, attention span),
     * Consumption layer (passive ratio, diet score, diversity, category times),
     * Circadian layer (morning grab, bedtime scroll, night session count),
     * Escape layer (escape score, avoidance flag), composite risk score.
     */
    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // ── Attention layer ──
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN fragmentationIndex REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN attentionSpanAvgMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN compulsiveCheckScore REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN totalInterruptions INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN unlockCount INTEGER NOT NULL DEFAULT 0");

            // ── Consumption layer ──
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN loopAppPair TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN passiveConsumptionRatio REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN digitalDietScore REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN socialMediaTimeMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN productiveAppMinutes INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN entertainmentTimeMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN dominantCategory TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN appDiversityScore REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN scrollIntensityScore REAL NOT NULL DEFAULT 0");

            // ── Circadian layer ──
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN morningPhoneGrabMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN bedtimeScrollMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN lateNightSessionCount INTEGER NOT NULL DEFAULT 0");

            // ── Escape behaviour layer ──
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN escapeBehaviorScore REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN isAvoidanceDayFlag INTEGER NOT NULL DEFAULT 0");

            // ── Composite score ──
            database.execSQL("ALTER TABLE behavior_snapshots ADD COLUMN overallBehaviorRiskScore REAL NOT NULL DEFAULT 0");

            // ── Index for risk-based queries ──
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_behavior_snapshots_overallBehaviorRiskScore` ON `behavior_snapshots` (`overallBehaviorRiskScore`)");
        }
    };

    // ═════════════════════════════════════════════════════════════════════
    // MIGRATION 12→13: QuestionnaireResponse — Psychological Intelligence
    // Expands from 12 basic fields to 35+ multi-dimensional assessment
    // ═════════════════════════════════════════════════════════════════════
    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // ── Temporal ──
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN dayTimestamp INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN checkInType TEXT DEFAULT ''");

            // ── Emotional state ──
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN anxietyLevel INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN hopeLevel INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN emotionalStability INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN feltLikeCrying INTEGER NOT NULL DEFAULT 0");

            // ── Cognitive state ──
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN mentalClarity INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN ruminationLevel INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN decisionDifficulty INTEGER NOT NULL DEFAULT 0");

            // ── Stress & coping ──
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN overwhelmLevel INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN copingMechanism TEXT DEFAULT ''");

            // ── Social connection ──
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN socialInteractionQuality INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN meaningfulConversations INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN wantedToWithdraw INTEGER NOT NULL DEFAULT 0");

            // ── Physical wellbeing ──
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN sleepQuality INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN exercisedToday INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN appetiteStatus TEXT DEFAULT ''");

            // ── Self-perception & meaning ──
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN selfWorthScore INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN purposeScore INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN addictionSelfScore INTEGER NOT NULL DEFAULT 0");

            // ── Distress & crisis ──
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN requestedSupport INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN computedDistressSeverity REAL NOT NULL DEFAULT 0");

            // ── Indices for fast lookups ──
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_questionnaire_responses_timestamp` ON `questionnaire_responses` (`timestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_questionnaire_responses_dayTimestamp` ON `questionnaire_responses` (`dayTimestamp`)");
        }
    };

    // ═════════════════════════════════════════════════════════════════════
    // MIGRATION 13→14: WeeklyAssessment (Advanced)
    // 7-dimension weekly psychological & behavioral intelligence
    // ═════════════════════════════════════════════════════════════════════
    private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `weekly_assessments` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`weekStartTimestamp` INTEGER NOT NULL, " +
                    "`weekEndTimestamp` INTEGER NOT NULL, " +
                    "`weekNumber` INTEGER NOT NULL DEFAULT 0, " +
                    "`year` INTEGER NOT NULL DEFAULT 0, " +
                    // Subjective reflection
                    "`overallMood` TEXT, " +
                    "`coreStruggle` TEXT, " +
                    "`primaryWin` TEXT, " +
                    "`weeklyReflection` TEXT, " +
                    "`nextWeekIntention` TEXT, " +
                    // Emotional trajectory
                    "`emotionalStabilityScore` INTEGER NOT NULL DEFAULT 0, " +
                    "`moodVarietyCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`negativeMoodDays` INTEGER NOT NULL DEFAULT 0, " +
                    "`cryingDays` INTEGER NOT NULL DEFAULT 0, " +
                    "`dominantMood` TEXT, " +
                    // Clinical markers
                    "`purposeScore` INTEGER NOT NULL DEFAULT 0, " +
                    "`socialConnectionScore` INTEGER NOT NULL DEFAULT 0, " +
                    "`burnoutRiskScore` INTEGER NOT NULL DEFAULT 0, " +
                    "`anhedoniaScore` INTEGER NOT NULL DEFAULT 0, " +
                    "`selfEfficacyScore` INTEGER NOT NULL DEFAULT 0, " +
                    "`addictionAwarenessScore` INTEGER NOT NULL DEFAULT 0, " +
                    // Protective factors
                    "`exerciseDaysCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`avgSleepHours` REAL NOT NULL DEFAULT 0, " +
                    "`avgSleepQuality` REAL NOT NULL DEFAULT 0, " +
                    "`socialInteractionDays` INTEGER NOT NULL DEFAULT 0, " +
                    "`gratitudeDays` INTEGER NOT NULL DEFAULT 0, " +
                    "`journalDays` INTEGER NOT NULL DEFAULT 0, " +
                    "`protectiveFactorScore` REAL NOT NULL DEFAULT 0, " +
                    // Behavioral aggregates
                    "`avgScreenTimeMs` INTEGER NOT NULL DEFAULT 0, " +
                    "`avgPassiveRatio` REAL NOT NULL DEFAULT 0, " +
                    "`avgDigitalDietScore` REAL NOT NULL DEFAULT 0, " +
                    "`avgFragmentationIndex` REAL NOT NULL DEFAULT 0, " +
                    "`avgBehaviorRiskScore` REAL NOT NULL DEFAULT 0, " +
                    "`avgDistressSeverity` REAL NOT NULL DEFAULT 0, " +
                    "`highRiskDaysCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`greenDaysCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`avgUnlockCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`totalBingeSessions` INTEGER NOT NULL DEFAULT 0, " +
                    "`escapeBehaviorDays` INTEGER NOT NULL DEFAULT 0, " +
                    // Delta tracking
                    "`screenTimeDeltaMs` INTEGER NOT NULL DEFAULT 0, " +
                    "`distressDelta` REAL NOT NULL DEFAULT 0, " +
                    "`purposeDelta` INTEGER NOT NULL DEFAULT 0, " +
                    "`overallTrajectory` TEXT, " +
                    // AI narrative
                    "`generatedInsight` TEXT, " +
                    "`primaryRiskFactor` TEXT, " +
                    "`suggestedAction` TEXT, " +
                    "`actionRecommendations` TEXT, " +
                    "`systemicRiskFlag` INTEGER NOT NULL DEFAULT 0, " +
                    "`weeklyWellnessScore` REAL NOT NULL DEFAULT 0)");

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_weekly_assessments_timestamp` ON `weekly_assessments` (`timestamp`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weekly_assessments_weekStartTimestamp_weekEndTimestamp` ON `weekly_assessments` (`weekStartTimestamp`, `weekEndTimestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_weekly_assessments_weekNumber` ON `weekly_assessments` (`weekNumber`)");
        }
    };

    // ═════════════════════════════════════════════════════════════════════
    // MIGRATION 14→15: JournalEntry (Advanced)
    // 5-layer qualitative data for NLP + therapeutic pipeline
    // ═════════════════════════════════════════════════════════════════════
    private static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `journal_entries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`dayTimestamp` INTEGER NOT NULL DEFAULT 0, " +
                    // Content layer
                    "`content` TEXT, " +
                    "`entryType` TEXT, " +
                    "`relatedPrompt` TEXT, " +
                    "`title` TEXT, " +
                    // Context layer
                    "`moodAtWriting` TEXT, " +
                    "`stressAtWriting` INTEGER NOT NULL DEFAULT 0, " +
                    "`triggerSource` TEXT, " +
                    "`linkedCheckInId` INTEGER NOT NULL DEFAULT 0, " +
                    // AI enrichment layer
                    "`aiSentiment` TEXT, " +
                    "`sentimentScore` REAL NOT NULL DEFAULT 0, " +
                    "`emotionTags` TEXT, " +
                    "`distressFlags` TEXT, " +
                    "`distressFlagCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`topicTags` TEXT, " +
                    "`cognitiveDistortions` TEXT, " +
                    // Engagement layer
                    "`wordCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`writingDurationMs` INTEGER NOT NULL DEFAULT 0, " +
                    "`isComplete` INTEGER NOT NULL DEFAULT 1, " +
                    "`wasEdited` INTEGER NOT NULL DEFAULT 0, " +
                    // Therapeutic layer
                    "`actionItem` TEXT, " +
                    "`actionItemCompleted` INTEGER NOT NULL DEFAULT 0, " +
                    "`gratitudeItemCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`aiReframeSuggestion` TEXT, " +
                    "`aiInsightHelpful` INTEGER)");

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_timestamp` ON `journal_entries` (`timestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_dayTimestamp` ON `journal_entries` (`dayTimestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_entryType` ON `journal_entries` (`entryType`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_aiSentiment` ON `journal_entries` (`aiSentiment`)");
        }
    };

    // ── Migration 15→16: Premium OnboardingProfile expansion (Tasks 2.G.1–2.G.3) ──
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // Clinical markers
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN addictionScale INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN purposeScore INTEGER NOT NULL DEFAULT 0");

            // Personality & coping
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN copingStyle TEXT");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN personalityArchetype TEXT");

            // Clinical history
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN mentalHealthHistory TEXT");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN peakVulnerabilityTime TEXT");

            // Readiness & completion
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN readinessToChange INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN onboardingComplete INTEGER NOT NULL DEFAULT 0");

            // Lifestyle & wellness baseline (Tasks 2.G.4, 2.G.5, 2.G.6)
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN routineStability INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN exerciseFrequency TEXT");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN screenFreeActivities INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN socialQualityBaseline INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN screenTimeAwareness INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN triggerApps TEXT");

            // ── WeeklyAssessment new columns (Tasks 2.F.5–2.F.11) ──
            database.execSQL("ALTER TABLE weekly_assessments ADD COLUMN exerciseFrequency TEXT");
            database.execSQL("ALTER TABLE weekly_assessments ADD COLUMN routineStabilityScore INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE weekly_assessments ADD COLUMN screenFreeActivities INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE weekly_assessments ADD COLUMN socialQualityScore INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE weekly_assessments ADD COLUMN nlpSentiment REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE weekly_assessments ADD COLUMN nlpDistressFlags TEXT");
            database.execSQL("ALTER TABLE weekly_assessments ADD COLUMN nlpTopics TEXT");
        }
    };

    // ── Migration 16→17: RiskClassification entity (AI output) ──
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `risk_classifications` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`dayTimestamp` INTEGER NOT NULL DEFAULT 0, " +
                    "`digitalAddictionScore` REAL NOT NULL DEFAULT 0, " +
                    "`stressAnxietyScore` REAL NOT NULL DEFAULT 0, " +
                    "`depressionRiskScore` REAL NOT NULL DEFAULT 0, " +
                    "`socialIsolationScore` REAL NOT NULL DEFAULT 0, " +
                    "`sleepDisruptionScore` REAL NOT NULL DEFAULT 0, " +
                    "`lowFulfilmentScore` REAL NOT NULL DEFAULT 0, " +
                    "`overallRiskScore` REAL NOT NULL DEFAULT 0, " +
                    "`primaryCategory` TEXT, " +
                    "`secondaryCategory` TEXT DEFAULT '', " +
                    "`confidence` REAL NOT NULL DEFAULT 0, " +
                    "`crisisFlag` INTEGER NOT NULL DEFAULT 0, " +
                    "`crisisReason` TEXT, " +
                    "`interventionShown` INTEGER NOT NULL DEFAULT 0, " +
                    "`classificationMode` TEXT DEFAULT 'full', " +
                    "`featureDataCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`featureVectorJson` TEXT, " +
                    "`riskDelta` REAL NOT NULL DEFAULT 0, " +
                    "`riskMovingAverage` REAL NOT NULL DEFAULT 0)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_risk_classifications_timestamp` ON `risk_classifications` (`timestamp`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_risk_classifications_dayTimestamp` ON `risk_classifications` (`dayTimestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_risk_classifications_overallRiskScore` ON `risk_classifications` (`overallRiskScore`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_risk_classifications_primaryCategory` ON `risk_classifications` (`primaryCategory`)");
        }
    };

    // ── Migration 17→18: UserBaseline expansion (std devs + status) ──
    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // New averages
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN avgAppSwitches7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN avgNightUsageMinutes7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN avgUnlocks7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN avgLaunches7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN avgPassiveRatio7d REAL NOT NULL DEFAULT 0");
            // Standard deviations
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN stdScreenTime7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN stdAppSwitches7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN stdNightUsage7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN stdUnlocks7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN stdLaunches7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN stdSleep7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN stdStress7d REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN stdMoodScore7d REAL NOT NULL DEFAULT 0");
            // Metadata
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN dataPointCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE user_baseline ADD COLUMN baselineStatus TEXT DEFAULT 'INSUFFICIENT'");
        }
    };

    // ═════════════════════════════════════════════════════════════════════
    // MIGRATION 18→19: InterventionTask — Advanced Task Intelligence
    // Expands 10-field basic entity to 25-field adaptive intervention system
    // ═════════════════════════════════════════════════════════════════════
    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // ── Priority & Difficulty ──
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'EASY'");

            // ── Source & Linkage ──
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN sourceTag TEXT DEFAULT 'check_in'");
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN linkedRiskCategory TEXT DEFAULT 'general'");
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN whyThisTask TEXT");

            // ── Scheduling ──
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN scheduledTimeSlot TEXT NOT NULL DEFAULT 'ANYTIME'");
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN expiresAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN snoozedUntil INTEGER NOT NULL DEFAULT 0");

            // ── Lifecycle status ──
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'");

            // ── Effectiveness feedback ──
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN effectivenessRating INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN preCompletionMood TEXT");
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN postCompletionMood TEXT");

            // ── Gamification ──
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN xpReward INTEGER NOT NULL DEFAULT 10");

            // ── Micro-intervention ──
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN isMicroIntervention INTEGER NOT NULL DEFAULT 0");

            // ── Sequences ──
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN sequenceId TEXT");
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN sequenceOrder INTEGER NOT NULL DEFAULT 0");

            // ── Indices for advanced queries ──
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_intervention_tasks_dateCreated` ON `intervention_tasks` (`dateCreated`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_intervention_tasks_status` ON `intervention_tasks` (`status`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_intervention_tasks_category` ON `intervention_tasks` (`category`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_intervention_tasks_linkedRiskCategory` ON `intervention_tasks` (`linkedRiskCategory`)");
        }
    };

    // ═════════════════════════════════════════════════════════════════════
    // MIGRATION 19→20: UserProgress — Gamification & Streak System
    // ═════════════════════════════════════════════════════════════════════
    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `user_progress` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`totalXp` INTEGER NOT NULL DEFAULT 0, " +
                    "`currentStreak` INTEGER NOT NULL DEFAULT 0, " +
                    "`longestStreak` INTEGER NOT NULL DEFAULT 0, " +
                    "`lastCompletionDay` INTEGER NOT NULL DEFAULT 0, " +
                    "`totalTasksCompleted` INTEGER NOT NULL DEFAULT 0, " +
                    "`totalTasksSkipped` INTEGER NOT NULL DEFAULT 0, " +
                    "`totalCrisisTasksCompleted` INTEGER NOT NULL DEFAULT 0, " +
                    "`badgesUnlockedJson` TEXT, " +
                    "`lastUpdated` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id`))");
        }
    };

    // ═════════════════════════════════════════════════════════════════════
    // MIGRATION 20→21: Module 5 — Support, Crisis & Safety
    // CrisisEvent, SafetyPlan, TrustedContact, ExerciseCompletion
    // ═════════════════════════════════════════════════════════════════════
    static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // ── CrisisEvent ──
            database.execSQL("CREATE TABLE IF NOT EXISTS `crisis_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`crisisLevel` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL DEFAULT 'ACTIVE', " +
                    "`triggerSignalsJson` TEXT, " +
                    "`actionsTakenJson` TEXT, " +
                    "`resolvedAt` INTEGER NOT NULL DEFAULT 0, " +
                    "`resolutionMethod` TEXT, " +
                    "`postCrisisMood` TEXT, " +
                    "`preDistressLevel` INTEGER NOT NULL DEFAULT 0, " +
                    "`postDistressLevel` INTEGER NOT NULL DEFAULT 0, " +
                    "`assessmentConfidence` REAL NOT NULL DEFAULT 0, " +
                    "`followUpScheduled` INTEGER NOT NULL DEFAULT 0, " +
                    "`debriefCompleted` INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_crisis_events_timestamp` ON `crisis_events` (`timestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_crisis_events_crisisLevel` ON `crisis_events` (`crisisLevel`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_crisis_events_status` ON `crisis_events` (`status`)");

            // ── SafetyPlan ──
            database.execSQL("CREATE TABLE IF NOT EXISTS `safety_plan` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`warningSignalsJson` TEXT, " +
                    "`copingStrategiesJson` TEXT, " +
                    "`reasonsToLiveJson` TEXT, " +
                    "`trustedContactsJson` TEXT, " +
                    "`professionalContactsJson` TEXT, " +
                    "`safeEnvironmentsJson` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "`isComplete` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id`))");

            // ── TrustedContact ──
            database.execSQL("CREATE TABLE IF NOT EXISTS `trusted_contacts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`phone` TEXT NOT NULL, " +
                    "`relationship` TEXT, " +
                    "`isEmergency` INTEGER NOT NULL DEFAULT 0, " +
                    "`notifyOnCrisis` INTEGER NOT NULL DEFAULT 0, " +
                    "`createdAt` INTEGER NOT NULL)");

            // ── ExerciseCompletion ──
            database.execSQL("CREATE TABLE IF NOT EXISTS `exercise_completions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`exerciseType` TEXT NOT NULL, " +
                    "`exerciseName` TEXT NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL DEFAULT 0, " +
                    "`preDistressLevel` INTEGER NOT NULL DEFAULT 0, " +
                    "`postDistressLevel` INTEGER NOT NULL DEFAULT 0, " +
                    "`completedFully` INTEGER NOT NULL DEFAULT 1)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_completions_completedAt` ON `exercise_completions` (`completedAt`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_completions_exerciseType` ON `exercise_completions` (`exerciseType`)");
        }
    };

    public abstract UserDao userDao();
    public abstract UsageDao usageDao();
    public abstract QuestionnaireDao questionnaireDao();
    public abstract TaskDao taskDao();
    public abstract AppUsageSnapshotDao appUsageSnapshotDao();
    public abstract BehaviorSnapshotDao behaviorSnapshotDao();
    public abstract BehaviorUsageSummaryDao behaviorUsageSummaryDao();
    public abstract UsageSessionDao usageSessionDao();
    public abstract DailyResetSessionDao dailyResetSessionDao();
    public abstract OnboardingProfileDao onboardingProfileDao();
    public abstract WellnessSummaryDao wellnessSummaryDao();
    public abstract UserBaselineDao userBaselineDao();
    public abstract WeeklyAssessmentDao weeklyAssessmentDao();
    public abstract JournalDao journalDao();
    public abstract RiskClassificationDao riskClassificationDao();
    public abstract UserProgressDao userProgressDao();
    public abstract CrisisEventDao crisisEventDao();
    public abstract SafetyPlanDao safetyPlanDao();
    public abstract TrustedContactDao trustedContactDao();
    public abstract ExerciseCompletionDao exerciseCompletionDao();
    public abstract SuicideRiskEventDao suicideRiskEventDao();
    public abstract ErrorLogDao errorLogDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "mindtrace_db"
                    )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
                    .addMigrations(MIGRATION_9_10)
                    .addMigrations(MIGRATION_10_11)
                    .addMigrations(MIGRATION_11_12)
                    .addMigrations(MIGRATION_12_13)
                    .addMigrations(MIGRATION_13_14)
                    .addMigrations(MIGRATION_14_15)
                    .addMigrations(MIGRATION_15_16)
                    .addMigrations(MIGRATION_16_17)
                    .addMigrations(MIGRATION_17_18)
                    .addMigrations(MIGRATION_18_19)
                    .addMigrations(MIGRATION_19_20)
                    .addMigrations(MIGRATION_20_21)
                    .addMigrations(MIGRATION_21_22)
                    .addMigrations(MIGRATION_22_23)
                    .addMigrations(MIGRATION_23_24)
                    .addMigrations(MIGRATION_24_25)
                    .addMigrations(MIGRATION_25_26)
                    .addMigrations(MIGRATION_26_27)
                    .addMigrations(MIGRATION_27_28)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }

    private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `suicide_risk_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`csrrsTier` INTEGER NOT NULL, " +
                    "`severityLabel` TEXT, " +
                    "`textTier` INTEGER NOT NULL, " +
                    "`behaviorTier` INTEGER NOT NULL, " +
                    "`signalCount` INTEGER NOT NULL, " +
                    "`matchedPhrasesJson` TEXT, " +
                    "`activeSignalsJson` TEXT, " +
                    "`source` TEXT, " +
                    "`lockdownTriggered` INTEGER NOT NULL DEFAULT 0, " +
                    "`notificationSent` INTEGER NOT NULL DEFAULT 0, " +
                    "`autoContactSent` INTEGER NOT NULL DEFAULT 0, " +
                    "`distressLevel` INTEGER NOT NULL DEFAULT 0, " +
                    "`moodScore` INTEGER NOT NULL DEFAULT 0, " +
                    "`isNightTime` INTEGER NOT NULL DEFAULT 0, " +
                    "`linkedCrisisEventId` INTEGER NOT NULL DEFAULT 0, " +
                    "`resolution` TEXT)");
        }
    };

    private static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // New CrisisEvent fields for Module 6/7
            database.execSQL("ALTER TABLE crisis_events ADD COLUMN safetyCheckSent INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE crisis_events ADD COLUMN resolved INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE crisis_events ADD COLUMN triggerSource TEXT");
            database.execSQL("ALTER TABLE crisis_events ADD COLUMN level TEXT");
            database.execSQL("ALTER TABLE crisis_events ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN urgeToScrollLevel INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN biggestDistraction TEXT");
        }
    };

    private static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE questionnaire_responses ADD COLUMN currentConcern TEXT");
        }
    };

    // ═════════════════════════════════════════════════════════════════════
    // MIGRATION 25→26: Observation Window — Closed-loop efficacy tracking
    // Adds 4 columns to intervention_tasks for post-completion observation
    // ═════════════════════════════════════════════════════════════════════
    static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // 2-hour observation window end timestamp
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN observationWindowEnd INTEGER NOT NULL DEFAULT 0");
            // Pre-intervention risk score snapshot
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN preInterventionRisk REAL NOT NULL DEFAULT 0");
            // Post-intervention risk score (after observation window closes)
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN postInterventionRisk REAL NOT NULL DEFAULT 0");
            // Computed efficacy delta (pre - post, positive = improvement)
            database.execSQL("ALTER TABLE intervention_tasks ADD COLUMN efficacyScore REAL NOT NULL DEFAULT 0");
            // Index for efficient observation window queries
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_intervention_tasks_observationWindowEnd` ON `intervention_tasks` (`observationWindowEnd`)");
        }
    };

    // ═════════════════════════════════════════════════════════════════════
    // MIGRATION 26→27: ErrorLog — Structured Worker Error Logging
    // Enables categorized error persistence for all WorkManager workers
    // ═════════════════════════════════════════════════════════════════════
    static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `error_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`workerName` TEXT NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
                    "`exceptionType` TEXT, " +
                    "`message` TEXT, " +
                    "`stackTraceSnippet` TEXT, " +
                    "`resolution` TEXT NOT NULL, " +
                    "`attemptCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`resolvedOnRetry` INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_error_logs_timestamp` ON `error_logs` (`timestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_error_logs_workerName` ON `error_logs` (`workerName`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_error_logs_category` ON `error_logs` (`category`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_error_logs_workerName_category` ON `error_logs` (`workerName`, `category`)");
        }
    };

    // ═════════════════════════════════════════════════════════════════════
    // MIGRATION 27→28: Nutrition & Hydration Baseline
    // Adds 8 columns to onboarding_profile for gut-brain axis tracking
    // ═════════════════════════════════════════════════════════════════════
    static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN waterIntake INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN caffeineLevel INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN alcoholFrequency TEXT");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN dietQuality INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN mealRegularity INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN sugarIntake INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN emotionalEating INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE onboarding_profile ADD COLUMN lateNightEating INTEGER NOT NULL DEFAULT 0");
        }
    };
}
