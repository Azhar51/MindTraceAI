# Phase 3 — Component Catalog

---

# 8. Data Pipeline Diagrams

## 8.1 End-to-End Data Pipeline

```
┌──────────────────────────────────────────────────────────────────┐
│                     DATA COLLECTION LAYER                        │
│                                                                  │
│  [Passive - No User Action]           [Active - User Action]     │
│  UsageStatsManager ──────┐            DailyCheckInActivity ──┐   │
│  AccessibilityService ───┤            QuestionnaireActivity──┤   │
│  NotificationListener ───┤            JournalActivity ───────┤   │
│  ScreenEventReceiver ────┘            TasksFragment ─────────┘   │
│           │                                    │                  │
│           ▼                                    ▼                  │
│  UsageSnapshotWorker (3h)           Room Direct Insert            │
│  MidnightSummaryWorker (daily)                                    │
├──────────────────────────────────────────────────────────────────┤
│                     STORAGE LAYER (Room v27)                      │
│                                                                  │
│  AppUsageSnapshot ──── DailyUsage ──── UsageSession               │
│  BehaviorSnapshot ──── BehaviorUsageSummary                       │
│  QuestionnaireResponse ──── JournalEntry                          │
│  InterventionTask ──── UserProgress                               │
│  RiskClassification ──── UserBaseline                             │
│  CrisisEvent ──── SuicideRiskEvent ──── ErrorLog                  │
├──────────────────────────────────────────────────────────────────┤
│                   ANALYSIS ENGINE LAYER                           │
│                                                                  │
│  FeatureVector.build() → 36 features                              │
│  MultiModalClassifier.classify() → 6 risk scores                  │
│  InsightEngine.generateInsights() → DashboardInsights             │
│  InterventionEngine.generateTasks() → Task list                   │
│  InterventionEngine.buildSentimentEnhancedEfficacyMap()           │
│    → EfficacyMetrics (70% behavioral + 30% sentiment)             │
├──────────────────────────────────────────────────────────────────┤
│                    PRESENTATION LAYER                              │
│                                                                  │
│  DashboardViewModel.rebuildInsightsAsync()                         │
│    → LiveData<DashboardInsights>                                  │
│    → LiveData<RiskClassification>                                 │
│    → LiveData<EfficacyMetrics>                                    │
│  Fragments observe LiveData → render UI cards                     │
└──────────────────────────────────────────────────────────────────┘
```

## 8.2 Questionnaire-to-Insight Transformation

```
QuestionnaireResponse
  ├─ mood (String) ──────────────────┐
  ├─ stressLevel (int 0-5) ──────────┤
  ├─ sleepHours (float) ─────────────┤
  ├─ anxietyLevel (int 0-5) ─────────┤
  ├─ emotionalStability (int 0-5) ───┤
  ├─ ruminationLevel (int 0-5) ──────┤
  ├─ socialInteractionQuality ───────┤
  ├─ selfWorthScore (int 0-5) ───────┤
  └─ computedDistressSeverity ───────┘
              │
              ▼
  PsychFeatureExtractor.extract()
  FeatureVector (36 dimensions)
              │
              ▼
  MultiModalClassifier.classify()
              │
              ▼
  RiskClassification
  ├─ digitalAddictionScore ─── [0.0 - 1.0]
  ├─ stressAnxietyScore ────── [0.0 - 1.0]
  ├─ depressionRiskScore ───── [0.0 - 1.0]
  ├─ socialIsolationScore ──── [0.0 - 1.0]
  ├─ sleepDisruptionScore ──── [0.0 - 1.0]
  ├─ lowFulfilmentScore ────── [0.0 - 1.0]
  └─ overallRiskScore ──────── weighted composite
              │
              ▼
  InsightEngine → DashboardInsights
  ├─ riskLevel (LOW / MODERATE / HIGH)
  ├─ reasonItems[] — why this score
  ├─ nextBestAction — what to do next
  └─ supportRecommended — escalation flag
```

## 8.3 Usage Tracking Pipeline

```
Android UsageStatsManager
  │ queryUsageStats(INTERVAL_DAILY)
  │ queryEvents(beginTime, endTime)
  ▼
UsageRepository.captureSnapshot()
  ├─ Filter system apps (AppCategoryMapper.isSystemApp)
  ├─ Categorize (500+ known apps → 12 categories)
  ├─ Build per-app AppUsageSnapshot entities
  ├─ Detect binge sessions (> 45min continuous)
  ├─ Flag passive apps (social media scrolling)
  └─ Compute night usage percentage
  ▼
UsageSessionDao — individual session records
  ├─ Session type classification (active/passive/mixed)
  ├─ Duration category (micro/short/normal/extended/binge)
  ├─ Interruption counting
  └─ Loop detection (previousAppPackage tracking)
  ▼
BehaviorSnapshotDao — daily behavioral intelligence
  ├─ Fragmentation index (session count / total time)
  ├─ Compulsive check score (micro-sessions / unlocks)
  ├─ Digital diet score (productive / total ratio)
  ├─ Escape behavior score (usage after low mood)
  └─ Overall behavior risk score (weighted composite)
  ▼
DashboardViewModel → UI rendering
```

## 8.4 Efficacy Closed-Loop Pipeline

```
Task Generation                    Task Completion
      │                                  │
      ▼                                  ▼
InterventionEngine            User marks task complete
  .generateTasks()                       │
      │                                  ▼
      ▼                         preInterventionRisk = current risk
Prioritized task list           observationWindowEnd = now + 2h
linked to risk categories               │
                                         ▼
                              ┌─ 2-HOUR OBSERVATION WINDOW ─┐
                              │  User continues normal use   │
                              │  Background workers collect  │
                              │  new usage + mood data       │
                              └──────────┬───────────────────┘
                                         │
                                         ▼
                              EfficacyWorker (runs every 2h)
                              finds expired windows
                                         │
                                         ▼
                              postInterventionRisk = current risk
                              efficacyScore = pre - post
                              (positive = improvement)
                                         │
                                         ▼
                              buildSentimentEnhancedEfficacyMap()
                              70% behavioral + 30% mood valence
                                         │
                                         ▼
                              buildEfficacyWeightMap()
                              feeds back into task generation
                              (adaptive loop)
```

---

# 9. Component Catalog — Activities

## 9.1 Activity Catalog (24 Activities)

### SplashActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Entry point — animated splash + auth/permission/crisis routing
- **Inputs**: None (launch activity)
- **Outputs**: Intent to next screen (Login/Questionnaire/Permission/Main/Crisis)
- **Dependencies**: AppDatabase, OnboardingRepository, SharedPreferences
- **User Impact**: Premium first impression; crash-safe crisis state recovery

### LoginActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: User authentication gate
- **Inputs**: User input (name)
- **Outputs**: User entity in Room
- **Dependencies**: UserDao
- **User Impact**: Minimal-friction identity establishment

### SignupActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: New user registration
- **Inputs**: User input (name, optional fields)
- **Outputs**: User entity in Room
- **Dependencies**: UserDao
- **User Impact**: Account creation with privacy-first local storage

### QuestionnaireActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Comprehensive psychological assessment (onboarding + recurring)
- **Inputs**: User input (30+ fields across 7 sections)
- **Outputs**: OnboardingProfile, QuestionnaireResponse
- **Dependencies**: QuestionnaireViewModel, OnboardingViewModel, QuestionnaireDao, OnboardingProfileDao
- **User Impact**: Seeds the AI engine with rich psychological data; enables personalized classification

### PermissionActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Sequential permission grant flow (4 permissions)
- **Inputs**: System permission states
- **Outputs**: OS-level permission grants
- **Dependencies**: AppOpsManager, AccessibilityManager, Settings
- **User Impact**: Enables passive data collection; explains why each permission matters

### MainActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Tab host for 7 fragments with bottom navigation + FAB
- **Inputs**: EXTRA_START_DESTINATION intent extra
- **Outputs**: Fragment transactions
- **Dependencies**: DashboardViewModel, BottomNavigationView, FloatingActionButton
- **User Impact**: Central navigation hub; check-in FAB provides constant access to mood logging

### DailyCheckInActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Quick mood + stress + sleep capture
- **Inputs**: User input (mood emoji, stress slider, sleep hours)
- **Outputs**: QuestionnaireResponse entity
- **Dependencies**: QuestionnaireViewModel
- **User Impact**: Low-friction daily emotional tracking; ~30 seconds to complete

### DailyResetActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Structured morning reset ritual
- **Inputs**: User input (focus task, first action, warning item, readiness level)
- **Outputs**: DailyResetSession entity
- **Dependencies**: DailyResetViewModel
- **User Impact**: Creates intentional morning routine; sets daily direction

### JournalActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Free-form journaling with AI sentiment analysis
- **Inputs**: User input (journal text, optional prompt response)
- **Outputs**: JournalEntry entity (with AI enrichment: sentiment, emotion tags, distress flags)
- **Dependencies**: JournalViewModel, Gemini API (optional)
- **User Impact**: Therapeutic self-expression; AI-powered insight generation

### WeeklyAssessmentActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Weekly wellness review with trend visualization
- **Inputs**: WeeklyAssessment entity (pre-computed by worker)
- **Outputs**: Display only; optional reflection notes
- **Dependencies**: DashboardViewModel
- **User Impact**: Longitudinal perspective; celebrates progress and identifies patterns

### CrisisActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Full-screen crisis intervention interface
- **Inputs**: Crisis trigger (behavioral detection, self-report, or manual access)
- **Outputs**: CrisisEvent entity (status updates, resolution)
- **Dependencies**: CrisisViewModel, CrisisEventDao, SafetyPlanDao, TrustedContactDao
- **User Impact**: Immediate safety net; combines coping tools with professional resources

### CrisisLockdownActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Maximum-severity lockdown (C-SSRS Tier 4+)
- **Inputs**: EXTRA_CSSRS_TIER intent extra
- **Outputs**: CrisisEvent updates, SuicideRiskEvent, auto-contact messages
- **Dependencies**: CrisisNotificationManager, TrustedContactDao
- **User Impact**: Life-safety feature; restricts phone use and surfaces help

### BreathingExerciseActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Guided breathing patterns (4-7-8, box breathing, etc.)
- **Inputs**: Exercise selection
- **Outputs**: ExerciseCompletion entity (with pre/post distress levels)
- **Dependencies**: ExerciseCompletionDao
- **User Impact**: Immediate anxiety/stress reduction; clinically validated technique

### GroundingExerciseActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: 5-4-3-2-1 sensory grounding technique
- **Inputs**: Guided prompts (5 things you see, 4 you hear, etc.)
- **Outputs**: ExerciseCompletion entity
- **Dependencies**: ExerciseCompletionDao
- **User Impact**: Acute distress management; anchors user in present moment

### FocusSessionActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Pomodoro-style focused work timer
- **Inputs**: Duration selection
- **Outputs**: Session completion metrics
- **Dependencies**: FloatingTimerService (optional overlay)
- **User Impact**: Builds sustained attention capacity; counteracts fragmentation

### FocusBlockActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: App blocking during focus sessions
- **Inputs**: App selection for blocking, duration
- **Outputs**: Block list configuration
- **Dependencies**: FocusBlockerService (foreground service)
- **User Impact**: Environmental control; removes temptation during focus time

### FocusPanelActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: App blocking settings and configuration
- **Inputs**: User preferences
- **Outputs**: Block list, schedule, duration settings
- **Dependencies**: SharedPreferences
- **User Impact**: Customizable focus environment

### AppDetailActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Per-app analytics deep dive
- **Inputs**: Package name (selected app)
- **Outputs**: Display only
- **Dependencies**: AppUsageSnapshotDao, UsageSessionDao
- **User Impact**: Granular understanding of individual app impact

### DailyReportActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Daily wellness report with export capability
- **Inputs**: DailyReportViewModel data
- **Outputs**: Display + optional CSV/JSON export
- **Dependencies**: DailyReportViewModel, DataExportRepository
- **User Impact**: Comprehensive daily review; sharable with therapists

### OverUsageAlertActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Translucent overlay warning when usage threshold exceeded
- **Inputs**: Usage threshold trigger
- **Outputs**: User acknowledgment
- **Dependencies**: UsageRepository
- **User Impact**: Real-time intervention; breaks usage autopilot

### FastChallengeActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Quick micro-challenge during high-usage moments
- **Inputs**: Challenge prompt
- **Outputs**: Challenge completion
- **Dependencies**: InterventionEngine
- **User Impact**: Gamified interruption of compulsive use

### SafetyPlanActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Safety plan editor and viewer
- **Inputs**: User input (warning signs, coping strategies, reasons to live, contacts)
- **Outputs**: SafetyPlan entity
- **Dependencies**: SafetyPlanDao
- **User Impact**: Clinical-grade safety planning; always accessible

### AiCoachActivity
- **Package**: `com.mindtrace.ai.ui`
- **Purpose**: Gemini-powered conversational AI coach
- **Inputs**: User messages + current context (risk scores, recent behavior)
- **Outputs**: AI responses, optional task suggestions
- **Dependencies**: Gemini API (OkHttp), DashboardViewModel (context injection)
- **User Impact**: Personalized conversational support; context-aware advice

### HourlyReviewActivity
- **Package**: `com.mindtrace.ai.ui.panel`
- **Purpose**: Hour-by-hour usage breakdown for a specific day
- **Inputs**: Day timestamp
- **Outputs**: Display only
- **Dependencies**: UsageSessionDao, AppUsageSnapshotDao
- **User Impact**: Granular temporal analysis; shows exactly when usage spikes occur

---

# 10. Component Catalog — Fragments

## 10.1 Fragment Catalog (8 Fragments)

### OverviewFragment
- **Package**: `com.mindtrace.ai.ui.panel`
- **Size**: 116KB (largest fragment)
- **Purpose**: Central dashboard — wellness ring, risk badge, sparkline, tasks, streaks
- **LiveData Observed**: 10+ streams from DashboardViewModel
- **Custom Views Used**: WellnessRingView, SparklineChartView, BehaviorFingerprint, NudgeCard
- **User Impact**: "Health at a glance" — the screen users see most often

### UsageFragment
- **Package**: `com.mindtrace.ai.ui.panel`
- **Size**: 56KB
- **Purpose**: Digital behavior analytics — screen time, heatmap, efficacy pipeline
- **Key Feature**: Efficacy Pipeline card with sentiment-enhanced metrics
- **Custom Views**: HeatmapView, CategoryPieChart, BehaviorScorePills
- **User Impact**: Transforms raw usage data into behavioral intelligence

### InsightsFragment
- **Package**: `com.mindtrace.ai.ui.panel`
- **Size**: 64KB
- **Purpose**: AI risk analysis — 6-category scores, trajectory, co-morbidity
- **Key Feature**: Classification engine transparency (Rules vs. ML mode indicator)
- **Custom Views**: RiskCategoryBarView, TimelineChartView, DeviationView
- **User Impact**: Full transparency into AI reasoning; builds trust in recommendations

### MoodFragment
- **Package**: `com.mindtrace.ai.ui.panel`
- **Size**: 55KB
- **Purpose**: Emotional tracking — mood history, trends, journal access
- **Custom Views**: MoodTimelineView, TrendChart
- **User Impact**: Emotional self-awareness through pattern recognition

### TasksFragment
- **Package**: `com.mindtrace.ai.ui.panel`
- **Size**: 16KB
- **Purpose**: Intervention task management — active/completed lists
- **Adapter**: TaskAdapter with MaterialCardView items
- **User Impact**: The "action" layer — concrete steps the user can take

### SupportFragment
- **Package**: `com.mindtrace.ai.ui.panel`
- **Size**: 19KB
- **Purpose**: Crisis support hub — exercises, safety plan, contacts, helplines
- **User Impact**: Safety net; one-tap access to all therapeutic resources

### SettingsFragment
- **Package**: `com.mindtrace.ai.ui.panel`
- **Size**: 18KB
- **Purpose**: Privacy controls, notifications, data export, theme
- **User Impact**: User control over their data and experience

### HourlyReviewActivity (in panel package)
- **Package**: `com.mindtrace.ai.ui.panel`
- **Size**: 8KB
- **Purpose**: Hour-by-hour usage breakdown
- **User Impact**: Temporal analysis of usage patterns

---

# 11. Component Catalog — ViewModels

## 11.1 ViewModel Catalog (10 ViewModels)

### DashboardViewModel (94KB — Central Orchestrator)
- **Package**: `com.mindtrace.ai.viewmodel`
- **Purpose**: Single source of truth for all dashboard data across all fragments
- **Key Methods**:
  - `refreshDashboard()` — Triggers full data reload from all repositories
  - `rebuildInsightsAsync()` — Background aggregation: usage + mood + behavior → DashboardInsights + EfficacyMetrics
  - `loadTrendReport(days, callback)` — Runs ClassificationTrendAnalyzer for trajectory assessment
  - `loadDayComparison(callback)` — Computes day-over-day risk delta
- **LiveData Streams**: 15+ covering screen time, classification, behavior, tasks, progress, baseline, efficacy
- **Dependencies**: UsageRepository, TaskRepository, ClassificationRepository, AssessmentRepository, BaselineManager, InsightEngine, InterventionEngine

### TaskViewModel (12KB)
- **Purpose**: Task lifecycle management
- **Key Methods**: `completeTask()`, `skipTask()`, `snoozeTask()`, `rateEffectiveness()`
- **Dependencies**: TaskRepository, UserProgressDao

### QuestionnaireViewModel (13KB)
- **Purpose**: Check-in form data handling and submission
- **Dependencies**: QuestionnaireDao, OnboardingProfileDao

### JournalViewModel (13KB)
- **Purpose**: Journal entry creation with AI sentiment enrichment
- **Dependencies**: JournalDao, Gemini API (optional)

### CrisisViewModel (16KB)
- **Purpose**: Crisis event lifecycle management
- **Dependencies**: CrisisEventDao, SafetyPlanDao, TrustedContactDao, SuicideRiskEventDao

### UsageViewModel (9KB)
- **Purpose**: Usage-specific data loading for UsageFragment
- **Dependencies**: UsageRepository, AppUsageSnapshotDao

### OnboardingViewModel (11KB)
- **Purpose**: Onboarding flow state management
- **Dependencies**: OnboardingRepository, OnboardingProfileDao

### DailyReportViewModel (22KB)
- **Purpose**: Daily report data aggregation and export
- **Dependencies**: DataExportRepository, DailyUsage, QuestionnaireResponse

### DailyResetViewModel (13KB)
- **Purpose**: Morning reset ritual state management
- **Dependencies**: DailyResetRepository, DailyResetSessionDao

### SettingsViewModel (2KB)
- **Purpose**: Settings state management
- **Dependencies**: SettingsRepository (SharedPreferences wrapper)

---

# 12. Component Catalog — Repositories

## 12.1 Repository Catalog (10 Repositories)

### UsageRepository (28KB)
- **Purpose**: All usage data access — screen time, app snapshots, sessions, behavioral metrics
- **Key Methods**: `captureUsageSnapshot()`, `getTodayScreenTime()`, `getAppUsageForDay()`, `buildBehaviorReport()`
- **Data Sources**: UsageDao, AppUsageSnapshotDao, UsageSessionDao, BehaviorSnapshotDao
- **External Dependencies**: Android UsageStatsManager

### ClassificationRepository (24KB)
- **Purpose**: Risk classification data access and trend analysis
- **Key Methods**: `getLatestClassification()`, `getClassificationHistory()`, `computeDayComparison()`
- **Inner Class**: `DayComparison` — encapsulates yesterday vs. today risk delta
- **Data Sources**: RiskClassificationDao

### AssessmentRepository (29KB)
- **Purpose**: Questionnaire and mood assessment data access
- **Key Methods**: `getRecentResponses()`, `getMoodTrend()`, `computeDistressSeverity()`
- **Data Sources**: QuestionnaireDao, JournalDao

### TaskRepository (14KB)
- **Purpose**: Intervention task CRUD and efficacy queries
- **Key Methods**: `getActiveTasks()`, `completeTask()`, `getTasksWithEfficacy()`, `getMostEfficaciousCategory()`
- **Data Sources**: TaskDao

### CrisisRepository (7KB)
- **Purpose**: Crisis event management and history
- **Key Methods**: `createCrisisEvent()`, `resolveEvent()`, `getActiveEvent()`
- **Data Sources**: CrisisEventDao, SuicideRiskEventDao

### BaselineManager (16KB)
- **Purpose**: Rolling baseline computation with standard deviations
- **Key Methods**: `recomputeBaseline()`, `isBaselineReady()`, `getDeviation(metric)`
- **Data Sources**: UserBaselineDao, DailyUsage history

### OnboardingRepository (2KB)
- **Purpose**: Onboarding completion state management
- **Data Sources**: SharedPreferences, OnboardingProfileDao

### SettingsRepository (4KB)
- **Purpose**: App settings persistence wrapper
- **Data Sources**: SharedPreferences

### DataExportRepository (12KB)
- **Purpose**: Data export in CSV/JSON format for clinical handoff
- **Key Methods**: `exportUsageData()`, `exportMoodData()`, `exportFullReport()`
- **Data Sources**: All DAOs (read-only aggregation)

### DailyResetRepository (4KB)
- **Purpose**: Morning reset session persistence
- **Data Sources**: DailyResetSessionDao

---

*End of Phase 3 — Continue to Phase 4: AI Engines & Logic Deep Dive*
