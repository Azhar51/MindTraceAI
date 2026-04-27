# Phase 4 — AI Engines, Workers & Logic Deep Dive

---

# 13. Component Catalog — AI / Analysis Engines

## 13.1 Core AI Engine Catalog

### MultiModalClassifier (73KB)
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: The primary risk classification engine. Takes a 36-feature vector and produces risk scores across 6 clinical dimensions.
- **Key Methods**:
  - `classify(FeatureVector)` — Main entry point; returns RiskClassification with 6 dimension scores
  - `resolveClassificationMode()` — Determines whether to use rules-only, hybrid, or ML-dominant mode based on data availability
  - `computeDigitalAddictionScore(features)` — Rules-based scoring for digital addiction dimension
  - `computeStressAnxietyScore(features)` — Rules-based scoring for stress/anxiety dimension
  - `computeDepressionRiskScore(features)` — Rules-based scoring for depression risk dimension
  - `computeSocialIsolationScore(features)` — Rules-based scoring for social isolation dimension
  - `computeSleepDisruptionScore(features)` — Rules-based scoring for sleep disruption dimension
  - `computeLowFulfilmentScore(features)` — Rules-based scoring for low fulfilment dimension
  - `detectCrisisSignals(features)` — Crisis flag evaluation
  - `computeOverallRisk(scores)` — Weighted composite of all 6 dimensions
  - `computeConfidence(featureCount)` — Data quality → confidence score mapping
- **Classification Modes**:
  - `baseline_only` — Minimal data, conservative estimates (< 10 features)
  - `partial` — Moderate data, rules-based (10-19 features)
  - `full` — Full data, rules-based (20+ features)
  - `hybrid_baseline` — ML active, low data
  - `hybrid_partial` — ML active, moderate data
  - `hybrid_partial_N` — N of 6 ML models loaded
  - `hybrid_full` — All 6 ML models active, 40% rules + 60% ML blending
- **Severity Scale**: None (0-0.15) → Mild (0.15-0.30) → Watch (0.30-0.45) → Moderate (0.45-0.60) → High (0.60-0.80) → Severe (0.80-1.0)
- **User Impact**: Every risk score, severity badge, and recommendation in the app traces back to this engine

### InsightEngine (57KB)
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: Transforms raw data into human-readable DashboardInsights
- **Key Methods**:
  - `generateInsights(dailyUsage, responses, behavior, baseline, classification)` — Main aggregation method
  - `buildReasonItems(classification, behavior)` — Generates "why this score" explanation list
  - `determineRiskLevel(classification)` — Maps numeric score to LOW/MODERATE/HIGH enum
  - `suggestNextAction(classification, tasks)` — Recommends single best next action
  - `evaluateSupportNeed(classification, behavior)` — Determines if support escalation is needed
- **Output**: `DashboardInsights` POJO containing risk level, reason items, next action, support flag, AI summary, personalized risk score, classification reasons
- **User Impact**: Bridges the gap between numeric scores and actionable understanding

### InterventionEngine (49KB)
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: Task generation, efficacy tracking, and sentiment-enhanced scoring
- **Key Methods**:
  - `generateTasks(riskClassification, profile, progress)` — Produces prioritized task list
  - `selectTemplatesForCategory(category, difficulty)` — Pulls from TaskTemplateRepository
  - `computeTaskEfficacy(task)` — Raw pre/post risk delta calculation
  - `finalizeObservationWindows(tasks, currentRisk)` — Bulk processing of expired 2h windows
  - `buildSentimentEnhancedEfficacyMap(tasks)` — 70/30 behavioral/sentiment blend
  - `getSentimentEnhancedSummary(tasks)` — Human-readable per-category efficacy summary
  - `buildEfficacyWeightMap(tasks)` — Generates weight adjustments for future task selection
  - `getEfficacySummary(tasks)` — Category-level efficacy report
  - `getMoodValence(mood)` — Maps mood strings to [-1.0, +1.0] valence scores
- **Efficacy Formula**: `sentimentEnhancedScore = (0.7 × behavioralEfficacy) + (0.3 × moodValence)`
- **User Impact**: Determines what tasks users see; adapts over time based on what actually works

### FeatureVector (48KB)
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: Extracts and normalizes 36 features from multiple data sources
- **36 Features** (grouped):
  - **Usage (8)**: Screen time, app switches, launch count, unlock count, passive ratio, social media time, entertainment time, productive time
  - **Behavior (8)**: Fragmentation index, compulsive check score, binge session count, rapid switch count, digital diet score, escape behavior score, loop pattern flag, attention span average
  - **Circadian (4)**: Late-night usage, bedtime scroll, morning phone grab, night session count
  - **Mood (6)**: Current mood score, stress level, anxiety level, emotional stability, hope level, distress severity
  - **Sleep (3)**: Sleep hours, sleep quality, sleep consistency
  - **Social (3)**: Social interaction quality, meaningful conversations, withdrawal tendency
  - **Cognitive (2)**: Mental clarity, rumination level
  - **Self (2)**: Self-worth score, purpose score
- **Normalization**: Each feature is normalized to [0.0, 1.0] range for classifier input
- **User Impact**: The quality of feature extraction directly determines classification accuracy

### PsychFeatureExtractor (26KB)
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: Derives psychological features from questionnaire responses
- **Key Methods**:
  - `extract(responses)` — Computes psychological feature subset from check-in history
  - `computeDistressSeverity(response)` — Multi-factor distress score computation
  - `detectRuminationPattern(responses)` — Identifies rumination trends over time
  - `computeAnhedoniaIndicator(responses)` — Detects pleasure deficit patterns
- **User Impact**: Enables clinical-grade psychological assessment from self-report data

### TaskTemplateRepository (26KB)
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: Library of 100+ intervention task templates organized by risk category
- **Categories**: Digital Addiction, Stress/Anxiety, Depression, Social Isolation, Sleep Disruption, Low Fulfilment, General Wellness
- **Template Fields**: Title, description, category, difficulty, estimated duration, evidence base
- **User Impact**: The diversity and quality of templates determines intervention variety

### AppCategoryMapper (26KB)
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: Maps 500+ known app package names to behavioral categories
- **Categories**: Social Media, Messaging, Entertainment, Gaming, Productivity, Education, Health, News, Shopping, Finance, Utility, System
- **Key Methods**:
  - `categorize(packageName)` — Returns category for known apps
  - `isPassiveApp(packageName)` — Returns true for scroll-heavy apps
  - `isSystemApp(packageName)` — Filters out system apps from tracking
  - `isHighRiskApp(packageName)` — Identifies addiction-prone apps
- **User Impact**: Determines how app usage is classified (productive vs. passive vs. entertainment)

### ClassificationTrendAnalyzer
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: Computes 7-day risk trajectory from classification history
- **Output**: `TrendReport` containing: overall risk trajectory (5 levels), average/current risk, per-category trends, fastest worsening/improving categories, top drifting feature, top risk drivers, crisis count, data quality assessment
- **User Impact**: Powers the trajectory assessment card in InsightsFragment

### BehavioralNudgeEngine
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: Real-time nudge decision engine based on current behavioral state
- **Key Methods**: `evaluateNudge(currentBehavior, baseline)` — Returns nudge text + urgency
- **Triggers**: Extended session (> 30min), rapid app switching (> 6 switches), late-night usage, binge detection
- **User Impact**: Proactive micro-interventions that interrupt harmful patterns in real-time

### CrisisDetector
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: C-SSRS-aligned suicide risk detection from text + behavioral signals
- **Key Methods**:
  - `assessTextRisk(text)` — NLP-based phrase matching for suicidal ideation markers
  - `assessBehavioralRisk(behavior, mood)` — Behavioral signal analysis
  - `computeCsrrsTier(textTier, behaviorTier)` — Maps to C-SSRS Tier 1-5
- **C-SSRS Tiers**:
  - Tier 1: Wish to be dead (passive ideation)
  - Tier 2: Non-specific active thoughts
  - Tier 3: Active ideation with method
  - Tier 4: Active ideation with intent
  - Tier 5: Active ideation with plan
- **User Impact**: Life-safety feature; triggers lockdown and emergency protocols

### EfficacyMetrics (3.4KB)
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: POJO encapsulating sentiment-enhanced efficacy scores for UI rendering
- **Fields**: `overallScore`, `categoryScores` (Map<String, Float>), `totalMeasuredTasks`, `isActive`, `topCategory`, `observationWindowActive`
- **Factory**: `EfficacyMetrics.empty()` — Safe empty state for cold start
- **User Impact**: Drives the efficacy pipeline card in UsageFragment

### StreakRecoveryManager
- **Package**: `com.mindtrace.ai.ai`
- **Purpose**: Manages post-crisis recovery streak tracking
- **Output**: `RecoveryState` — days since last crisis, recovery trajectory, stability indicator
- **User Impact**: Powers the crisis recovery timeline in InsightsFragment

---

# 14. Component Catalog — Workers & Services

## 14.1 WorkManager Workers

### UsageSnapshotWorker (3.2KB, `service` package)
- **Schedule**: Every 3 hours
- **Function**: Captures per-app usage from UsageStatsManager, builds AppUsageSnapshot entities
- **Data Flow**: UsageStatsManager → UsageRepository.captureSnapshot() → AppUsageSnapshotDao.insertAll()

### ClassificationWorker (5.7KB, `service` package)
- **Schedule**: Every 4 hours
- **Function**: Runs the full classification pipeline: FeatureVector.build() → MultiModalClassifier.classify() → RiskClassificationDao.insert()
- **Error Handling**: WorkerErrorHandler with retry logic and ErrorLog persistence

### EfficacyWorker (7.3KB, `services` package)
- **Schedule**: Every 2 hours
- **Function**: Finds completed tasks with expired 2h observation windows, computes post-intervention risk scores, calculates efficacy deltas
- **Data Flow**: TaskDao.getTasksAwaitingEfficacy(now) → InterventionEngine.finalizeObservationWindows() → TaskDao.update()

### MidnightSummaryWorker (10.9KB, `service` package)
- **Schedule**: Daily at midnight
- **Function**: Compiles the complete daily behavioral snapshot by aggregating all usage sessions, app snapshots, and behavioral metrics into BehaviorSnapshotEntity and DailyUsage summaries

### BaselineComputeWorker (3.6KB, `service` package)
- **Schedule**: Daily
- **Function**: Recomputes 7-day rolling averages and standard deviations for UserBaseline entity. Updates baseline status (INSUFFICIENT → LEARNING → READY)

### DailyReminderWorker (13.3KB, `services` package)
- **Schedule**: Daily (user-configurable time)
- **Function**: Sends check-in reminder notification via CHANNEL_DAILY_REMINDER. Content adapts based on current risk level and streak state

### TaskReminderWorker (9.5KB, `services` package)
- **Schedule**: Periodic
- **Function**: Reminds users of incomplete intervention tasks. Prioritizes high-priority and expiring tasks

### NudgeWorker (8KB, `services` package)
- **Schedule**: Periodic
- **Function**: Evaluates current behavioral state against baseline for proactive nudges. Uses BehavioralNudgeEngine for decision logic

### DataCleanupWorker (3.8KB, `services` package)
- **Schedule**: Weekly
- **Function**: Prunes old data (error logs > 30 days, raw session data > 90 days) to manage storage

### WeeklyReportWorker (4.9KB, `services` package)
- **Schedule**: Weekly
- **Function**: Generates weekly wellness report notification with key metrics summary

### WeeklyAssessmentWorker (14.9KB, `services` package)
- **Schedule**: Weekly
- **Function**: Compiles the comprehensive WeeklyAssessment entity with 50+ fields across 7 dimensions

### CrisisFollowUpWorker (12.5KB, `services` package)
- **Schedule**: Triggered after crisis resolution
- **Function**: Sends follow-up check-in notifications at 1h, 6h, 24h, and 72h post-crisis. Tracks debrief completion

### WellnessSyncWorker (4.8KB, `services` package)
- **Schedule**: Periodic
- **Function**: Synchronizes WellnessSummary entity with latest classification and usage data

### UsageWorker (6.9KB, `services` package)
- **Schedule**: Periodic
- **Function**: Additional usage data capture and session building

## 14.2 Foreground & Bound Services

### FocusBlockerService (5KB, `services` package)
- **Type**: Foreground service (specialUse)
- **Purpose**: Blocks specified apps during focus sessions by monitoring foreground app and redirecting

### FloatingTimerService (4.8KB, `services` package)
- **Type**: Foreground service (specialUse)
- **Purpose**: Displays floating overlay timer during focus sessions using SYSTEM_ALERT_WINDOW permission

### MindTraceAccessibilityService (7.6KB, `services` package)
- **Type**: Bound accessibility service
- **Purpose**: Captures scroll events across apps for scroll intensity telemetry (Feature D13). Feeds scrollIntensityScore into behavioral analysis

### MindTraceNotificationListener (4.6KB, `services` package)
- **Type**: Bound notification listener service
- **Purpose**: Measures notification response latency (Feature D14). Computes notificationResponseAvgMs for engagement quality analysis

## 14.3 Other Service Components

### WorkScheduler (19.5KB, `service` package)
- **Purpose**: Centralized worker scheduling — single source of truth for all PeriodicWorkRequest configurations
- **Key Method**: `scheduleAll(context)` — Called once from MindTraceApp.onCreate()
- **Scheduling Table**: Defines frequency, constraints, and uniqueness policy for all 10+ workers

### WorkerErrorHandler (4.6KB, `service` package)
- **Purpose**: Structured error handling for all workers. Categorizes errors, implements retry logic, persists to ErrorLog entity
- **Key Method**: `handle(context, workerName, exception, attemptCount)` — Returns `Result.retry()` or `Result.failure()` based on error category and attempt count

### WorkerProgressTracker (8.7KB, `service` package)
- **Purpose**: Logs worker lifecycle progress for debugging (started, data loaded, processing, complete, failed)

### ScreenEventReceiver (16.6KB, `service` package)
- **Purpose**: BroadcastReceiver for ACTION_SCREEN_ON, ACTION_SCREEN_OFF, ACTION_USER_PRESENT events. Tracks unlock times, screen-on duration, and feeds into compulsive check score computation

### NotificationEventTracker (21.5KB, `service` package)
- **Purpose**: Tracks notification delivery and response timestamps. Computes response latency per notification for engagement quality metrics

### ScrollEventTracker (18.2KB, `service` package)
- **Purpose**: Processes raw scroll events from AccessibilityService into intensity scores. Computes scrollIntensityScore per-app and aggregate

---

# 15. Logic Deep Dive

## 15.1 Usage Tracking Logic

**What it detects**: How much time users spend on each app, how many times they open apps, and temporal patterns of usage.

**How it works**: The UsageSnapshotWorker queries Android's `UsageStatsManager.queryUsageStats(INTERVAL_DAILY)` and `queryEvents()` APIs every 3 hours. Events are parsed to reconstruct individual sessions (foreground start → foreground end). The AppCategoryMapper classifies each app into one of 12 categories using a database of 500+ known package names.

**Why it matters**: Raw screen time is insufficient. A user who spends 3 hours on a productivity app has a fundamentally different behavioral profile than one who spends 3 hours on social media. Category-level analysis enables targeted interventions.

**Assumptions**: UsageStatsManager data is accurate and available within 3-hour windows. System apps are correctly filtered. Unknown apps are categorized as "Other."

**False Positive Risks**: Music/podcast apps playing in background may inflate usage time. Navigation apps during driving may appear as "high usage" but are contextually appropriate.

## 15.2 Session Building Logic

**What it detects**: Individual app usage sessions — when the user opened an app, how long they used it, and what they used before/after.

**How it works**: UsageEvents from Android are processed chronologically. A session begins on MOVE_TO_FOREGROUND and ends on MOVE_TO_BACKGROUND. Sessions are classified by duration: micro (< 30s), short (30s-5min), normal (5-30min), extended (30-60min), binge (> 60min). The previousAppPackage field enables loop detection — if the user switches A→B→A→B, a dopamine loop is flagged.

**Why it matters**: Session-level data reveals compulsive behavior patterns invisible in daily aggregates. A user with 100 micro-sessions has a very different problem than one with 3 extended sessions.

**Assumptions**: MOVE_TO_FOREGROUND/BACKGROUND events are consistently emitted by Android. Multi-window mode is not tracked separately.

## 15.3 Behavioral Signal Detection

**What it detects**: Five behavioral risk signals: fragmentation, compulsive checking, binge sessions, late-night usage, and dopamine loops.

**How it works**:
- **Fragmentation Index**: `totalSessions / totalTimeHours`. High fragmentation (> 0.7) indicates inability to sustain attention.
- **Compulsive Check Score**: `microSessions / unlockCount`. High ratio indicates reflexive checking without purpose.
- **Binge Detection**: Sessions > 45 minutes on passive apps (social media, entertainment) are flagged.
- **Late-Night Usage**: Any usage between 10 PM and 6 AM is tracked separately. The `bedtimeScrollMs` field captures specifically pre-sleep scrolling.
- **Dopamine Loop**: Detected when `previousAppPackage` chains reveal A→B→A→B patterns with micro-sessions.

**Why it matters**: These signals are early indicators of digital addiction and behavioral dysregulation. They appear before the user consciously recognizes a problem.

## 15.4 Risk Scoring Logic

**What it detects**: The user's risk level across 6 clinical dimensions on a 0.0-1.0 scale.

**How it works**: MultiModalClassifier receives a 36-feature vector and computes each dimension using weighted rules:
- **Digital Addiction**: Weighted sum of screen time deviation, passive ratio, binge count, fragmentation index, compulsive score, app switches, unlock count
- **Stress/Anxiety**: Self-reported stress, anxiety, overwhelm, rumination, plus behavioral escape score
- **Depression**: Low mood persistence, anhedonia indicators, purpose deficit, self-worth decline, social withdrawal
- **Social Isolation**: Passive consumption ratio, social interaction quality, meaningful conversations deficit, withdrawal tendency
- **Sleep Disruption**: Late-night usage, bedtime scroll duration, sleep hours deficit, sleep quality, morning phone grab
- **Low Fulfilment**: Purpose score deficit, routine instability, productive time ratio, task completion rate

Each dimension score maps to a severity level: None (< 0.15) → Mild → Watch → Moderate → High → Severe (> 0.80).

**Overall Risk**: Weighted composite — digital addiction and depression have slightly higher weights due to clinical significance.

**Assumptions**: Self-reported data is honest. Behavioral proxies (e.g., late-night usage correlates with sleep disruption) are valid. Feature weights are clinically informed but not validated through clinical trials.

## 15.5 Intervention Selection Logic

**What it detects**: Which specific intervention task will be most effective for this user at this moment.

**How it works**: InterventionEngine selects tasks through a multi-factor scoring process:
1. **Category Matching**: Tasks are primarily drawn from the user's dominant risk category
2. **Efficacy Weighting**: Categories with historically positive efficacy scores receive higher selection probability
3. **Difficulty Progression**: Task difficulty escalates as the user's streak increases (Easy → Medium → Hard)
4. **Time-Slot Matching**: Tasks are assigned to appropriate time slots (Morning tasks like "set an intention" vs. Evening tasks like "screen-free hour before bed")
5. **Variety**: The engine avoids repeating recently-completed tasks
6. **Micro-Interventions**: During high-distress moments, very short tasks (< 5 minutes) are prioritized

**Why it matters**: Generic advice fails. The combination of category targeting, efficacy learning, and difficulty progression creates a genuinely adaptive system.

## 15.6 Efficacy Computation Logic

**What it detects**: Whether a specific intervention actually improved the user's state.

**How it works**:
1. When a task is completed, `preInterventionRisk = currentRiskClassification.overallRiskScore`
2. `observationWindowEnd = System.currentTimeMillis() + 7_200_000` (2 hours)
3. EfficacyWorker (every 2h) finds tasks where `now > observationWindowEnd`
4. `postInterventionRisk = latestRiskClassification.overallRiskScore`
5. `efficacyScore = preInterventionRisk - postInterventionRisk` (positive = improvement)

**Sentiment Enhancement**:
- If the user rated their pre/post mood: `moodValence = getMoodValence(postMood) - getMoodValence(preMood)`
- Enhanced score: `(0.7 × efficacyScore) + (0.3 × moodValence)`

**Assumptions**: Risk score changes within 2 hours are primarily attributable to the intervention. Confounding factors (other events during the window) are not controlled for.

**False Positive/Negative Risks**:
- False positive: Risk naturally decreases in the evening regardless of intervention
- False negative: An effective intervention may be masked by a stressful event during the observation window

## 15.7 Crisis Detection Logic

**What it detects**: Suicidal ideation, severe distress, and crisis states requiring immediate intervention.

**How it works**: CrisisDetector uses two signal channels:
1. **Text Analysis**: Journal entries and check-in free-text fields are scanned against a curated phrase library (matched phrases for suicidal ideation markers). Text tier reflects severity of matched phrases.
2. **Behavioral Signals**: Sudden behavioral changes (dramatic usage increase, extreme late-night use, social withdrawal pattern) combined with low mood self-reports. Behavioral tier reflects cumulative signal strength.
3. **C-SSRS Mapping**: `csrrsTier = max(textTier, behaviorTier)` with upward adjustment when both channels are elevated

**Escalation Protocol**:
- Tier 1-2: Display support resources, log SuicideRiskEvent
- Tier 3: Trigger CrisisActivity, send follow-up notifications
- Tier 4-5: Trigger CrisisLockdownActivity, auto-alert trusted contacts, bypass DND for notifications

**Why it matters**: This is the most critical feature in the app. False negatives are unacceptable — the system errs on the side of caution.

## 15.8 Baseline Comparison Logic

**What it detects**: Whether today's behavior is normal or abnormal for this specific user.

**How it works**: BaselineManager computes 7-day rolling averages and standard deviations for 9 metrics. A "deviation" is computed as: `deviation = (todayValue - mean) / stdDev`. Deviations > 2.0 are flagged as significant anomalies.

**Why it matters**: A user who normally uses their phone 2 hours/day and suddenly uses it 6 hours has a very different signal than a user who normally uses 5 hours and uses 6. Baseline comparison enables personalized anomaly detection.

---

*End of Phase 4 — Continue to Phase 5: Workflows & User Impact Analysis*
