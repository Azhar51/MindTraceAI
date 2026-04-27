# Phase 5 — Workflow Diagrams & User Impact Analysis

---

# 16. Workflow Diagrams

## 16.1 Onboarding Workflow

```
┌──────────────┐
│ App Install   │
└──────┬───────┘
       ▼
┌──────────────┐     ┌──────────────┐
│ SplashActivity│────▶│ LoginActivity │
│ (2s animation)│     │ (name input)  │
└──────────────┘     └──────┬───────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             │             ▼
    ┌─────────────┐         │   ┌──────────────┐
    │ New User?    │─── Yes ─┘   │ Existing User │
    │ SignupActivity│             │ (skip to      │
    └──────┬──────┘              │  permission   │
           │                     │  check)       │
           ▼                     └──────┬───────┘
    ┌──────────────────┐               │
    │ QuestionnaireActivity │           │
    │ 30+ field assessment  │           │
    │ ┌────────────────┐   │           │
    │ │ Demographics    │   │           │
    │ │ Emotional State │   │           │
    │ │ Sleep           │   │           │
    │ │ Digital Behavior│   │           │
    │ │ Routine         │   │           │
    │ │ Social          │   │           │
    │ │ Clinical Markers│   │           │
    │ └────────────────┘   │           │
    └──────────┬───────────┘           │
               │                       │
               │  Saves: OnboardingProfile  │
               │  + QuestionnaireResponse   │
               ▼                       ▼
    ┌──────────────────────────────────┐
    │     PermissionActivity           │
    │  Step 1: Usage Stats ─────────── │
    │  Step 2: Notifications ──────── │
    │  Step 3: Accessibility ──────── │
    │  Step 4: Notification Listener ─ │
    │  (auto-skips granted permissions)│
    └──────────────┬───────────────────┘
                   ▼
    ┌──────────────────────┐
    │   MainActivity        │
    │   (OverviewFragment)  │
    │   Dashboard ready!    │
    └──────────────────────┘
```

## 16.2 Daily Check-In Workflow

```
┌─────────────────────┐
│ User taps Check-In  │
│ FAB or reminder      │
│ notification         │
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│ DailyCheckInActivity │
│ ┌─────────────────┐ │
│ │ Mood Emoji Grid │ │
│ │ (Happy→Sad)     │ │
│ ├─────────────────┤ │
│ │ Stress Slider   │ │
│ │ (0-5)           │ │
│ ├─────────────────┤ │
│ │ Sleep Hours     │ │
│ │ Slider          │ │
│ ├─────────────────┤ │
│ │ Optional fields │ │
│ │ (anxiety, focus) │ │
│ └─────────────────┘ │
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│ QuestionnaireViewModel│
│ .submitCheckIn()     │
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│ QuestionnaireDao     │
│ .insert(response)    │
└──────────┬──────────┘
           │
     ┌─────┼─────┐
     ▼     ▼     ▼
   Room  Triggers  Updates
   Save  Insight   Dashboard
         Rebuild   LiveData
```

## 16.3 Usage Capture Workflow

```
┌──────────────────────────────────────────────────────┐
│                   EVERY 3 HOURS                       │
│                                                       │
│  WorkManager triggers UsageSnapshotWorker             │
│           │                                           │
│           ▼                                           │
│  UsageStatsManager.queryUsageStats(INTERVAL_DAILY)    │
│  UsageStatsManager.queryEvents(start, end)            │
│           │                                           │
│           ▼                                           │
│  UsageRepository.captureSnapshot()                    │
│  ├─ For each app with usage:                          │
│  │   ├─ AppCategoryMapper.categorize(pkg)             │
│  │   ├─ Compute usageTimeMillis                       │
│  │   ├─ Count foreground sessions                     │
│  │   ├─ Detect binge flag (> 45min)                   │
│  │   ├─ Flag passive apps                             │
│  │   ├─ Calculate night usage %                       │
│  │   └─ Build AppUsageSnapshot entity                 │
│  ├─ Build UsageSession entities from events           │
│  │   ├─ Classify session type                         │
│  │   ├─ Compute duration category                     │
│  │   ├─ Track previousAppPackage for loops            │
│  │   └─ Flag notification-triggered sessions          │
│  └─ Aggregate into DailyUsage                         │
│      ├─ Total screen time                             │
│      ├─ Active foreground time                        │
│      ├─ Passive/productive/social/entertainment split │
│      ├─ Category breakdown JSON                       │
│      └─ Hourly breakdown JSON                         │
│           │                                           │
│           ▼                                           │
│  Room: batch insert/upsert all entities               │
└──────────────────────────────────────────────────────┘
```

## 16.4 Classification Workflow

```
┌──────────────────────────────────────────────────┐
│                  EVERY 4 HOURS                    │
│                                                   │
│  ClassificationWorker.doWork()                    │
│           │                                       │
│           ▼                                       │
│  Collect data from multiple DAOs:                 │
│  ├─ DailyUsage (today)                            │
│  ├─ BehaviorSnapshot (today)                      │
│  ├─ QuestionnaireResponse (latest)                │
│  ├─ UserBaseline (7-day averages)                 │
│  ├─ JournalEntry (recent, for sentiment)          │
│  └─ AppUsageSnapshot (today, for category data)   │
│           │                                       │
│           ▼                                       │
│  FeatureVector.build(allData)                     │
│  → 36 normalized features [0.0 - 1.0]            │
│           │                                       │
│           ▼                                       │
│  MultiModalClassifier.classify(featureVector)     │
│  ├─ resolveClassificationMode()                   │
│  │   → baseline_only / partial / full / hybrid_*  │
│  ├─ computeDigitalAddictionScore()                │
│  ├─ computeStressAnxietyScore()                   │
│  ├─ computeDepressionRiskScore()                  │
│  ├─ computeSocialIsolationScore()                 │
│  ├─ computeSleepDisruptionScore()                 │
│  ├─ computeLowFulfilmentScore()                   │
│  ├─ detectCrisisSignals()                         │
│  ├─ computeOverallRisk()                          │
│  └─ computeConfidence()                           │
│           │                                       │
│           ▼                                       │
│  RiskClassification entity                        │
│  → RiskClassificationDao.insert()                 │
│           │                                       │
│     ┌─────┴─────┐                                 │
│     ▼           ▼                                 │
│  If crisis   Normal flow:                         │
│  flag set:   InterventionEngine                   │
│  CrisisDetector  .generateTasks()                 │
│  → CrisisEvent   → TaskDao.insert()              │
│  → Notifications                                  │
└──────────────────────────────────────────────────┘
```

## 16.5 Intervention Workflow

```
┌──────────────────────────────────────────┐
│  InterventionEngine.generateTasks()       │
│  ├─ Get dominant risk category            │
│  ├─ Query efficacy weights                │
│  │   (buildEfficacyWeightMap)             │
│  ├─ Select templates from                 │
│  │   TaskTemplateRepository               │
│  ├─ Filter by:                            │
│  │   ├─ Category match                    │
│  │   ├─ Difficulty (streak-based)         │
│  │   ├─ Time slot (current time)          │
│  │   ├─ Not recently completed            │
│  │   └─ Efficacy history                  │
│  ├─ Assign priority, XP, expiry           │
│  └─ Return sorted task list               │
└──────────────┬───────────────────────────┘
               ▼
┌──────────────────────────────────────────┐
│  TasksFragment displays active tasks      │
│  User sees: title, category badge,        │
│  difficulty, "why this task", XP reward   │
└──────────────┬───────────────────────────┘
               ▼
┌──────────────────────────────────────────┐
│  User completes task                      │
│  ├─ Records pre-completion mood           │
│  ├─ Sets isCompleted = true               │
│  ├─ Snapshots preInterventionRisk         │
│  ├─ Sets observationWindowEnd             │
│  │   (now + 2 hours)                      │
│  ├─ Awards XP                             │
│  └─ Prompts for effectiveness rating      │
│     and post-completion mood              │
└──────────────┬───────────────────────────┘
               ▼
┌──────────────────────────────────────────┐
│  2 HOURS LATER: EfficacyWorker           │
│  ├─ Finds expired observation windows    │
│  ├─ Gets current risk score              │
│  ├─ postInterventionRisk = current risk  │
│  ├─ efficacyScore = pre - post           │
│  ├─ Applies sentiment enhancement        │
│  └─ Updates task in database             │
└──────────────┬───────────────────────────┘
               ▼
┌──────────────────────────────────────────┐
│  NEXT TASK GENERATION CYCLE:             │
│  buildEfficacyWeightMap() uses           │
│  historical efficacy to prioritize       │
│  categories that actually worked         │
│  → ADAPTIVE LOOP COMPLETE                │
└──────────────────────────────────────────┘
```

## 16.6 Support Routing Workflow

```
┌─────────────────────────────────────────────────┐
│  SUPPORT ROUTING DECISION TREE                   │
│                                                   │
│  InsightEngine.evaluateSupportNeed()              │
│           │                                       │
│     ┌─────┼───────────────┐                       │
│     ▼     ▼               ▼                       │
│  Normal  Elevated      Crisis                     │
│  Risk    Risk          Detected                   │
│     │     │               │                       │
│     ▼     ▼               ▼                       │
│  Standard  Support      C-SSRS Tier               │
│  dashboard  card        Assessment                │
│  display    appears      │                        │
│             in           ┌─┴──────────┐           │
│             InsightsFragment           │           │
│                    │      ▼            ▼           │
│                    │   Tier 1-3    Tier 4-5        │
│                    │      │            │           │
│                    ▼      ▼            ▼           │
│              SupportFragment  CrisisActivity  CrisisLockdown │
│              ├─ Breathing    ├─ Distress      ├─ Phone locked │
│              ├─ Grounding    │  assessment    ├─ Safety plan  │
│              ├─ Safety Plan  ├─ Coping tools  ├─ Auto-contact │
│              ├─ Contacts     ├─ Safety plan   ├─ Helplines    │
│              └─ Helplines    ├─ Contacts      └─ Must confirm │
│                              └─ "I'm safe"       safety       │
│                                 resolution                    │
└─────────────────────────────────────────────────┘
```

## 16.7 Settings & Permissions Workflow

```
SettingsFragment
├─ Privacy Mode Toggle → Masks app names in UI
├─ Notification Preferences → Per-channel enable/disable
├─ Daily Reminder Time → DailyReminderWorker schedule
├─ Data Export → DataExportRepository
│   ├─ CSV format (usage, mood, tasks)
│   └─ JSON format (full data dump)
│   → FileProvider share intent
├─ Tracking Controls
│   ├─ Usage tracking on/off
│   ├─ Scroll tracking on/off
│   └─ Notification tracking on/off
├─ Permission Status → Shows granted/denied for each
├─ Theme → Light/Dark/System
├─ About → Version, build info
└─ Delete Data → Destructive data wipe with confirmation
```

---

# 17. Full Architecture Diagram

```
╔══════════════════════════════════════════════════════════════════╗
║                    USER INTERFACE LAYER                          ║
║                                                                  ║
║  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────────┐ ║
║  │SplashActivity│LoginActivity│QuestionnaireActivity│PermissionActivity│ ║
║  └──────┬─────┘ └─────┬──────┘ └──────┬──────┘ └──────┬───────┘ ║
║         └─────────────┴───────────────┴────────────────┘         ║
║                              ▼                                    ║
║  ┌───────────────── MainActivity ─────────────────────────────┐  ║
║  │ BottomNavigationView + FloatingActionButton + Toolbar       │  ║
║  │ ┌──────────┐┌────────┐┌──────┐┌──────┐┌────────┐          │  ║
║  │ │ Overview ││ Usage  ││ Mood ││ Tasks││Insights│          │  ║
║  │ │ Fragment ││Fragment││Frag. ││Frag. ││Fragment│          │  ║
║  │ │ (116KB)  ││(56KB)  ││(55KB)││(16KB)││(64KB)  │          │  ║
║  │ └──────────┘└────────┘└──────┘└──────┘└────────┘          │  ║
║  │        ┌──────────┐ ┌──────────┐                           │  ║
║  │        │ Support  │ │ Settings │  (secondary destinations)  │  ║
║  │        │ Fragment │ │ Fragment │                             │  ║
║  │        └──────────┘ └──────────┘                           │  ║
║  └────────────────────────────────────────────────────────────┘  ║
║  + 14 standalone Activities (Crisis, Journal, Focus, etc.)       ║
╠══════════════════════════════════════════════════════════════════╣
║                   VIEWMODEL LAYER                                ║
║                                                                  ║
║  ┌─────────────────────────────────────────────────────────┐     ║
║  │         DashboardViewModel (94KB — Central Hub)          │     ║
║  │    15+ LiveData streams observed by all fragments        │     ║
║  └─────────────────────────┬───────────────────────────────┘     ║
║  + TaskVM, QuestionnaireVM, JournalVM, CrisisVM, UsageVM, etc.   ║
╠══════════════════════════════════════════════════════════════════╣
║                   REPOSITORY LAYER                               ║
║                                                                  ║
║  UsageRepository ─── ClassificationRepository ─── TaskRepository  ║
║  AssessmentRepository ─── CrisisRepository ─── BaselineManager    ║
║  OnboardingRepository ─── SettingsRepository ─── DataExportRepo   ║
╠══════════════════════════════════════════════════════════════════╣
║                 AI / ANALYSIS ENGINE LAYER                       ║
║                                                                  ║
║  MultiModalClassifier(73KB) ─── InsightEngine(57KB)              ║
║  InterventionEngine(49KB) ──── FeatureVector(48KB)               ║
║  PsychFeatureExtractor ──────── TaskTemplateRepository            ║
║  AppCategoryMapper ──────────── ClassificationTrendAnalyzer       ║
║  BehavioralNudgeEngine ──────── CrisisDetector                   ║
║  EfficacyMetrics ────────────── StreakRecoveryManager             ║
║  + 38 more analysis classes                                       ║
╠══════════════════════════════════════════════════════════════════╣
║               WORKER / SERVICE LAYER                             ║
║                                                                  ║
║  UsageSnapshotWorker(3h) ─── ClassificationWorker(4h)            ║
║  EfficacyWorker(2h) ──────── MidnightSummaryWorker(daily)        ║
║  BaselineComputeWorker ───── DailyReminderWorker                 ║
║  NudgeWorker ─────────────── TaskReminderWorker                  ║
║  DataCleanupWorker(weekly) ─ WeeklyReportWorker                  ║
║  + FocusBlockerService, FloatingTimerService                     ║
║  + AccessibilityService, NotificationListenerService             ║
║  + ScreenEventReceiver, NotificationEventTracker                 ║
╠══════════════════════════════════════════════════════════════════╣
║                    DATA LAYER (Room v27)                          ║
║                                                                  ║
║  AppDatabase (976 lines, 27 migrations)                          ║
║  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             ║
║  │ 22 Entities   │ │ 22 DAOs      │ │ 27 Migrations│             ║
║  │ User          │ │ UserDao      │ │ v1→v27       │             ║
║  │ DailyUsage    │ │ UsageDao     │ │ Additive only│             ║
║  │ Questionnaire │ │ TaskDao      │ │ + destructive│             ║
║  │ InterventTask │ │ JournalDao   │ │   fallback   │             ║
║  │ + 18 more     │ │ + 18 more    │ │              │             ║
║  └──────────────┘ └──────────────┘ └──────────────┘             ║
╚══════════════════════════════════════════════════════════════════╝
```

---

# 18. User Impact Analysis

## 18.1 Awareness Stage (Days 1-7)

| Feature | Emotional Effect | Behavioral Target | Measurable Outcome |
|---------|-----------------|-------------------|-------------------|
| Onboarding Questionnaire | Creates self-reflection moment | Forces honest self-assessment | OnboardingProfile completion |
| Screen Time Display | Surprise/concern at usage volume | Conscious usage awareness | DailyUsage.screenTimeMillis awareness |
| Behavioral Fingerprint | Understanding of usage patterns | Pattern recognition | BehaviorSnapshot fields populated |
| Risk Classification | Objective external perspective | Motivates change | RiskClassification scores as baseline |

## 18.2 Action Stage (Days 7-30)

| Feature | Emotional Effect | Behavioral Target | Measurable Outcome |
|---------|-----------------|-------------------|-------------------|
| Intervention Tasks | Sense of agency and direction | Daily micro-actions | Task completion rate > 60% |
| Focus Sessions | Restored attention capability | Sustained focus practice | FocusSession completions |
| Breathing Exercises | Immediate stress relief | Coping skill building | ExerciseCompletion count |
| Mood Check-ins | Emotional literacy | Daily emotional tracking | QuestionnaireResponse frequency |
| Behavioral Nudges | Awareness of real-time patterns | Breaking autopilot | Nudge response rate |
| Streak System | Pride and momentum | Consistency building | UserProgress.currentStreak |

## 18.3 Transformation Stage (Days 30+)

| Feature | Emotional Effect | Behavioral Target | Measurable Outcome |
|---------|-----------------|-------------------|-------------------|
| Efficacy Pipeline | Trust in personalized advice | Following proven interventions | EfficacyMetrics.overallScore > 0 |
| Trend Analysis | Progress visibility | Sustained improvement | Trajectory = "improving" |
| Weekly Reports | Long-term perspective | Celebrating growth | WeeklyAssessment trend deltas |
| Baseline Deviation | Personal norm awareness | Self-regulation | Deviation alerts decreasing |
| Adaptive Tasks | Relevance and engagement | Continued participation | Task variety score |

## 18.4 Module-Level Impact

### Usage Monitoring Module
- **Intended Effect**: Breaks the "invisible" nature of phone addiction by making usage visible and quantified
- **Habit Target**: Reduces unconscious phone checking and passive scrolling
- **Influence Metric**: screenTimeMillis trend (target: 15-25% decrease over 30 days)

### Mood Tracking Module
- **Intended Effect**: Builds emotional vocabulary and self-awareness
- **Habit Target**: Daily emotional check-in becomes routine
- **Influence Metric**: Check-in frequency and mood trend stability

### Task/Intervention Module
- **Intended Effect**: Provides concrete, achievable actions that build self-efficacy
- **Habit Target**: Daily completion of at least one wellness intervention
- **Influence Metric**: Task completion rate, efficacy scores, streak length

### Insights/Classification Module
- **Intended Effect**: Creates trust through transparency; user understands why recommendations are made
- **Habit Target**: Regular review of risk scores and progress
- **Influence Metric**: InsightsFragment visit frequency, classification confidence growth

### Crisis/Support Module
- **Intended Effect**: Provides safety net; user knows help is always available
- **Habit Target**: Proactive use of coping tools before crisis escalation
- **Influence Metric**: Exercise completion during distress, crisis event resolution rate

### Gamification Module
- **Intended Effect**: Intrinsic motivation through progress visibility
- **Habit Target**: Sustained daily engagement beyond initial novelty
- **Influence Metric**: Streak length, XP accumulation, badge unlocks

---

*End of Phase 5 — Continue to Phase 6: Strengths, Limitations, Risks & Roadmap*
