# Phase 2 — System Architecture & Data Model

---

# 5. Screen-by-Screen Product Walkthrough

## 5.1 SplashActivity — Entry Point & Routing Hub

**Purpose**: First screen the user sees. Renders premium splash animation and routes to the correct destination.

**UI Structure**: Full-screen FrameLayout with radial gradient background (`#0D0D1A` → `#1A1A2E`), circular logo glow (animated rotation), logo container with brain icon, app name text, tagline text, and pulsing loading dot. 8 floating particles drift upward for ambient atmosphere.

**Logic**: After 2-second animation delay, `checkAuthAndPermissions()` runs on a background thread. It queries:
1. `SharedPreferences("mindtrace_crisis")` for active crisis flag
2. `CrisisEventDao.getActiveEvent()` for unresolved crisis in DB
3. `UserDao.getUser()` for authentication state
4. `OnboardingRepository.isOnboardingCompleted()` for onboarding state
5. Permission checks: Usage Stats, Notifications, Accessibility, Notification Listener

**Routing Priority**: Crisis Lockdown → Crisis → Login → Onboarding → Permissions → Main Dashboard

**Data Consumed**: User entity, CrisisEvent entity, OnboardingProfile, SharedPreferences  
**Data Written**: Nothing (read-only routing)

**User Impact**: Creates a premium first impression. The 2-second delay is intentional — it gives the background thread time to query the database while the user enjoys the animation.

## 5.2 LoginActivity & SignupActivity — Authentication

**Purpose**: Gate that ensures a User entity exists before proceeding.

**UI Structure**: Clean login form with name input, sign-in button, and "Create Account" link. SignupActivity has similar form with additional fields.

**Logic**: Creates a `User` entity (id=1) in Room. No remote authentication — entirely local.

**Data Written**: User entity  
**User Impact**: Establishes identity for personalized experience. Minimal friction by design.

## 5.3 QuestionnaireActivity — Psychological Onboarding

**Purpose**: Comprehensive psychological assessment that seeds the AI classification engine.

**UI Structure**: Vertically scrolling form organized into collapsible sections. Each section has a header, description, and input fields. Progress bar at top shows completion percentage. Uses Material sliders (0–5), dropdowns, multi-select chips, and text inputs.

**Logic**: Driven by `QuestionnaireViewModel` and `OnboardingViewModel`. On submission:
1. Creates `OnboardingProfile` entity with all 30+ fields
2. Creates initial `QuestionnaireResponse` entity
3. Marks onboarding as complete in SharedPreferences
4. Routes to PermissionActivity

**Data Written**: OnboardingProfile (30+ fields), QuestionnaireResponse  
**User Impact**: This is the richest self-report moment. The depth of questions (clinical markers like addiction scale, purpose score, coping style, personality archetype) enables much more nuanced risk classification than typical wellness apps that ask only "how are you feeling?"

## 5.4 PermissionActivity — Data Collection Setup

**Purpose**: Guides users through 4 required permissions with explanations.

**UI Structure**: Step-by-step permission wizard. Each step shows: permission name, icon, explanation of why it's needed, what data it enables, and a "Grant" button that opens the appropriate system settings. Already-granted permissions are auto-skipped.

**Permissions**:
1. **PACKAGE_USAGE_STATS** → System settings → Usage access toggle
2. **POST_NOTIFICATIONS** → Android permission dialog (API 33+)
3. **Accessibility Service** → Accessibility settings → MindTraceAccessibilityService toggle
4. **Notification Listener** → Notification access settings → MindTrace toggle

**Data Written**: Nothing (permissions are OS-level)  
**User Impact**: Critical for data collection. Without Usage Stats permission, the behavioral intelligence engine has no input data. The app explains this clearly to motivate permission grants.

## 5.5 OverviewFragment — Central Dashboard

**Purpose**: The command center that provides a holistic snapshot of the user's current state.

**UI Structure**: Vertically scrolling layout with MaterialCardView components:
- WellnessRingView (custom circular progress with animated arc)
- Risk level badge with severity color
- Mood sparkline (SparklineChartView, 7 data points)
- Screen time hero card with delta indicator
- Active task cards with completion toggles
- Streak/XP bar with level indicator
- Behavioral nudge cards
- Quick-action row (4 circular buttons)
- AI coach FAB

**Logic**: Observes 10+ LiveData streams from DashboardViewModel:
- `getInsights()` → DashboardInsights (risk level, reasons, actions)
- `getScreenTime()` → Long (total screen time today)
- `getLatestClassification()` → RiskClassification (6-dimension scores)
- `getCurrentBehavior()` → BehaviorReport (behavioral signals)
- `getAllTasks()` → List<InterventionTask> (active tasks)
- `getUserProgress()` → UserProgress (XP, streaks)
- `getStateHistory()` → List<QuestionnaireResponse> (mood history)
- `getMostUsedApp()` → AppUsageModel (top app)
- `getLatestBaseline()` → UserBaseline (rolling averages)

**Data Consumed**: Everything (aggregated through DashboardViewModel)  
**Data Written**: Nothing (display only)  
**User Impact**: Provides a "health at a glance" view. The wellness ring creates an emotional anchor — the user immediately sees whether they're in a good (green), concerning (amber), or critical (red) state.

## 5.6 UsageFragment — Digital Behavior Analytics

**Purpose**: Deep dive into screen time, app usage patterns, and behavioral fingerprints.

**UI Structure**: Rich scrollable dashboard:
- Hero card: Total screen time with 7-day average delta
- Heatmap: 24-hour diverging bar chart (productive vs. passive by hour)
- Behavioral fingerprint card: Active/passive ratio dial, chronotype badge, loop detection
- Efficacy pipeline card: Sentiment-enhanced intervention effectiveness
- Usage timeline: Stacked bar chart with daily/weekly/monthly toggle
- Behavior analysis card: 5 score pills (Fragmentation, Switching, Binge, Night, Dominance)
- Category pie chart: Time distribution across app categories
- Top app card with usage trend
- App list: Per-app usage with category badges and trend arrows

**Logic**: UsageFragment binds to DashboardViewModel for screen time, BehaviorReport, and EfficacyMetrics. The efficacy pipeline card renders dynamically:
- "Measuring" state when insufficient data
- "Active" state with overall efficacy % and per-category bars
- "Observing" state when tasks are in the 2-hour observation window

**Data Consumed**: DailyUsage, AppUsageSnapshots, BehaviorReport, BehaviorUsageSummary, EfficacyMetrics  
**Data Written**: Nothing (display only)  
**User Impact**: Transforms abstract screen time into behavioral intelligence. Users can see not just how much they used their phone, but how they used it — passive scrolling vs. productive work, late-night vs. daytime, fragmented vs. focused.

## 5.7 MoodFragment — Emotional Tracking

**Purpose**: Mood history visualization and quick mood logging.

**UI Structure**: Mood timeline with emoji indicators and color-coded entries. Trend chart showing mood trajectory. Check-in prompt card. Journal entry list. Sleep correlation display.

**Logic**: Driven by DashboardViewModel mood-related LiveData. Supports quick inline mood logging and links to DailyCheckInActivity for full check-ins.

**Data Consumed**: QuestionnaireResponses, JournalEntries  
**Data Written**: Quick mood entries  
**User Impact**: Creates emotional awareness through pattern recognition. Users can see if Mondays are consistently low-mood days, or if their mood drops after high screen time days.

## 5.8 TasksFragment — Intervention Management

**Purpose**: Displays and manages personalized intervention tasks.

**UI Structure**: RecyclerView with TaskAdapter. Each task card shows: title, description, category badge (color-coded), difficulty indicator (Easy/Medium/Hard), priority number, time slot, XP reward, "Why this task" expandable section, completion toggle, effectiveness rating (post-completion), and snooze button.

**Logic**: TaskViewModel loads tasks from TaskRepository. On completion:
1. Sets `isCompleted = true`, `completedAt = now`
2. Records pre-completion mood
3. Starts 2-hour observation window (`observationWindowEnd = now + 7200000`)
4. Snapshots current risk as `preInterventionRisk`
5. Awards XP via UserProgress update
6. Prompts for post-completion mood and effectiveness rating

**Data Consumed**: InterventionTask list (active, completed, expired)  
**Data Written**: Task completion status, effectiveness rating, pre/post mood, observation window timestamps  
**User Impact**: The "action" layer of the app. Users receive concrete, personalized steps they can take right now. The "why this task" explanation builds understanding of why the recommendation matters for their specific risk profile.

## 5.9 InsightsFragment — AI Risk Analysis

**Purpose**: The most analytically dense screen — displays full risk classification results with transparency.

**UI Structure** (12 major sections):
1. **Overall Risk Gauge** — Semicircular gauge with animated fill and severity badge
2. **6-Category Risk Bars** — RiskCategoryBarView for each dimension with percentage and color
3. **Freshness Badge** — "Live" / "Xm ago" / "Xh ago" / "Stale" based on classification recency
4. **Classification Engine Mode** — "Rules-Based" vs. "ML-Enhanced" with data quality and feature coverage (N/36)
5. **Primary Concern** — Highest-scoring category with colored dot
6. **Confidence Badge** — High (≥0.8) / Moderate (≥0.5) / Building (<0.5)
7. **Why This Score** — Keyword-highlighted explanation reasons
8. **Behavioral Sparkline** — 7-day screen time trend with direction indicator
9. **Next Best Action** — Recommended action with category, impact, and time metadata
10. **Trajectory Assessment** — 7-day trend direction with worsening/improving categories
11. **Co-morbidity Detection** — Alert when 3+ categories are simultaneously elevated
12. **Day-over-Day Comparison** — Yesterday vs. today risk with delta

**Data Consumed**: RiskClassification, DashboardInsights, UserBaseline, BehaviorReport, CrisisEvents  
**Data Written**: Nothing (display only)  
**User Impact**: Complete transparency into the AI's reasoning. Users understand not just their risk level but why the system scored them that way, what's changing, and what to do about it.

## 5.10 SupportFragment — Crisis & Therapeutic Tools

**Purpose**: One-stop access to all support resources, therapeutic exercises, and crisis features.

**UI Structure**: Card-based layout with: current distress indicator, exercise launchers (breathing, grounding), safety plan section, trusted contacts list, helpline directory, crisis history.

**Data Consumed**: SafetyPlan, TrustedContacts, CrisisEvents, ExerciseCompletions  
**Data Written**: Navigation only (launches sub-activities)

## 5.11 CrisisActivity & CrisisLockdownActivity

**CrisisActivity**: Full-screen crisis intervention with distress assessment slider, immediate coping tools (breathing button, grounding button), safety plan display, trusted contact quick-dial buttons, helpline routing, and "I'm Safe" resolution flow. On resolution, updates CrisisEvent with resolution method, post-crisis mood, and post-distress level.

**CrisisLockdownActivity**: Maximum-severity mode (C-SSRS Tier 4+). Restricts phone use, displays safety plan prominently, auto-sends messages to trusted contacts, requires active safety confirmation to unlock. Uses `android:taskAffinity=""` and `android:excludeFromRecents="true"` to prevent easy dismissal.

---

# 6. System Architecture

## 6.1 Architecture Style

MindTrace AI follows the **Android MVVM** (Model-View-ViewModel) architecture pattern with Repository abstraction. The codebase enforces strict separation of concerns across 7 layers:

```
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 1: UI (Activities + Fragments + Custom Views + Adapters) │
│  Responsibility: Render data, handle user input                 │
│  Communication: Observes ViewModel LiveData                     │
├─────────────────────────────────────────────────────────────────┤
│  LAYER 2: VIEWMODEL (10 ViewModels)                             │
│  Responsibility: Hold UI state, orchestrate data loading        │
│  Communication: Calls Repository methods, exposes LiveData      │
├─────────────────────────────────────────────────────────────────┤
│  LAYER 3: REPOSITORY (10 Repositories)                          │
│  Responsibility: Abstract data sources, business logic          │
│  Communication: Queries DAOs, calls AI engines                  │
├─────────────────────────────────────────────────────────────────┤
│  LAYER 4: AI / ANALYSIS ENGINE (48 classes)                     │
│  Responsibility: Classification, insight generation, task gen   │
│  Communication: Receives data from repositories, returns models │
├─────────────────────────────────────────────────────────────────┤
│  LAYER 5: DAO (22 DAO interfaces)                               │
│  Responsibility: Database query definitions                     │
│  Communication: Room-generated implementations query SQLite     │
├─────────────────────────────────────────────────────────────────┤
│  LAYER 6: ENTITY (22 Room entities)                             │
│  Responsibility: Data model / schema definition                 │
│  Communication: Mapped to SQLite tables by Room                 │
├─────────────────────────────────────────────────────────────────┤
│  LAYER 7: WORKER / SERVICE (10 workers + 4 services)            │
│  Responsibility: Background processing, data collection         │
│  Communication: Accesses Repositories and DAOs directly         │
└─────────────────────────────────────────────────────────────────┘
```

Each layer communicates only with its adjacent layer (with the exception of Workers, which bypass ViewModels to access Repositories directly since they run without UI context).

## 6.2 Package Organization

| Package | Files | Purpose |
|---------|-------|---------|
| `com.mindtrace.ai` | 3 | Application root: MindTraceApp, AppUsageModel, AppExecutors |
| `com.mindtrace.ai.ui` | 20 | Activities: all screen controllers (Login, Signup, Crisis, etc.) |
| `com.mindtrace.ai.ui.panel` | 9 | Fragments: 7 main tabs + HourlyReviewActivity + overview subpackage |
| `com.mindtrace.ai.viewmodel` | 10 | ViewModels: UI state holders and data orchestrators |
| `com.mindtrace.ai.repository` | 10 | Repositories: data access abstraction layer |
| `com.mindtrace.ai.database` | 1 | AppDatabase: Room configuration, 27 migrations |
| `com.mindtrace.ai.database.entity` | 22 | Room entities: data model definitions |
| `com.mindtrace.ai.database.dao` | 22 | DAO interfaces: query definitions |
| `com.mindtrace.ai.ai` | 48 | AI/analysis engines: classification, insights, interventions |
| `com.mindtrace.ai.service` | 10 | WorkManager workers + scheduling + error handling |
| `com.mindtrace.ai.services` | 17 | Foreground services, bound services, additional workers |
| `com.mindtrace.ai.behavior` | ~5 | Behavioral analysis models (BehaviorReport, etc.) |
| `com.mindtrace.ai.util` | ~8 | Utilities: formatting, mood mapping, constants |
| `com.mindtrace.ai.adapter` | ~6 | RecyclerView adapters for list UIs |

## 6.3 Key Design Principles

**Single Source of Truth**: DashboardViewModel (94KB) is the central orchestrator. All fragments observe its LiveData rather than querying databases directly. This prevents data inconsistency across screens.

**Offline-First**: All data is stored locally in Room. No network dependency for core features. The only network call is the optional AI Coach (Gemini API), which degrades gracefully if offline.

**Graceful Degradation**: Every pipeline stage uses try-catch with fallback values. The `InsightEngine` returns default insights if classification fails. The `InterventionEngine` falls back to generic tasks if efficacy data is unavailable. Errors are logged to the `ErrorLog` entity for diagnostics.

**Privacy by Design**: All processing happens on-device. No data leaves the phone. No analytics SDKs. No crash reporting services. The global exception handler in `MindTraceApp` writes crash logs to local files only.

**Background-Safe**: Workers use WorkManager with battery-aware constraints, exponential backoff retry policies, and structured error handling via `WorkerErrorHandler`. Each worker logs its lifecycle stages for debugging.

**Crisis-Safe State**: Crisis mode persists across crashes via SharedPreferences + DB dual-write. `SplashActivity` checks both on every launch.

## 6.4 DashboardViewModel — The Central Orchestrator

The `DashboardViewModel` (94KB, 1680+ lines) is the largest and most important component in the app. It serves as the single source of truth for all dashboard data across all fragments.

**Key responsibilities:**
- Loads and caches screen time, app usage, mood history, tasks, baseline, and classification data
- Runs `rebuildInsightsAsync()` on background threads to aggregate all data into DashboardInsights
- Exposes 15+ LiveData streams observed by OverviewFragment, UsageFragment, InsightsFragment, etc.
- Orchestrates the EfficacyMetrics pipeline within the insight rebuild cycle
- Provides callback-based methods for trajectory analysis and day comparison

**LiveData Streams:**

| LiveData | Type | Consumers |
|----------|------|-----------|
| `getInsights()` | DashboardInsights | Overview, Insights |
| `getScreenTime()` | Long | Overview, Usage, Insights |
| `getLatestClassification()` | RiskClassification | Overview, Insights |
| `getCurrentBehavior()` | BehaviorReport | Overview, Usage, Insights |
| `getAllTasks()` | List<InterventionTask> | Overview, Tasks |
| `getUserProgress()` | UserProgress | Overview, Tasks |
| `getStateHistory()` | List<QuestionnaireResponse> | Overview, Mood, Insights |
| `getMostUsedApp()` | AppUsageModel | Overview, Usage |
| `getLatestBaseline()` | UserBaseline | Insights |
| `getEfficacyMetrics()` | EfficacyMetrics | Usage |
| `getUsageHistory()` | List<DailyUsage> | Usage, Insights |

---

# 7. Data Model & Database Architecture

## 7.1 Entity Catalog (22 Entities)

### Core User Entities

**User** (`user` table)  
Single-row entity (id=1). Stores name and creation timestamp. Acts as authentication gate.

**OnboardingProfile** (`onboarding_profile` table)  
30+ field psychological profile seeded during onboarding. Includes emotional dimensions (7 fields), sleep, digital behavior indicators (6 fields), routine assessment (5 fields), social wellbeing (3 fields), clinical markers (addiction scale, purpose, coping style, personality archetype, mental health history, peak vulnerability time, readiness to change), and lifestyle baselines (routine stability, exercise frequency, screen-free activities, social quality, screen time awareness, trigger apps).

### Behavioral Data Entities

**DailyUsage** (`daily_usage` table)  
Daily aggregate containing: screen time (total + active foreground), mood, stress, sleep hours, app switch count, launch count, top app, high-risk app count, sleep proxy fields (first unlock, last screen off), consumption pattern fields (passive ratio, productive/social/entertainment time), engagement quality (scroll intensity, notification response latency), and category/hourly breakdown JSON.

**AppUsageSnapshot** (`app_usage_snapshots` table)  
Per-app per-day usage record: package name, app name, usage time, usage percentage, launch count, first/last opened, category, passive flag, binge flag/count, average/longest session length, night usage percentage.

**BehaviorSnapshotEntity** (`behavior_snapshots` table)  
4-layer daily behavioral intelligence:
- **Attention Layer**: Fragmentation index, attention span average, compulsive check score, interruptions, unlock count
- **Consumption Layer**: Loop app pair, passive ratio, digital diet score, social/productive/entertainment time, dominant category, app diversity, scroll intensity
- **Circadian Layer**: Morning phone grab time, bedtime scroll duration, late-night session count
- **Escape Layer**: Escape behavior score, avoidance day flag
- **Composite**: Overall behavior risk score

**UsageSession** (`usage_sessions` table)  
Individual app session records: start/end timestamps, duration, session type (active/passive/mixed), duration category (micro/short/normal/extended/binge), interruption count, notification trigger flag, previous app package for loop detection.

**BehaviorUsageSummary** (`behavior_usage_summaries` table)  
Daily behavioral scoring: fragmented usage score, binge score, switch score, top-app dominance score, late-night penalty, distraction pattern score, summary label, explanatory notes.

### Psychological Data Entities

**QuestionnaireResponse** (`questionnaire_responses` table)  
Check-in data with 35+ fields across: temporal (day timestamp, check-in type), emotional state (mood, anxiety, hope, emotional stability, crying), cognitive (mental clarity, rumination, decision difficulty), stress/coping (overwhelm, coping mechanism), social (interaction quality, meaningful conversations, withdrawal), physical (sleep quality, exercise, appetite), self-perception (self-worth, purpose, addiction self-score), distress (support requested, computed severity), digital (urge to scroll, biggest distraction, current concern).

**JournalEntry** (`journal_entries` table)  
5-layer qualitative data: content (text, entry type, related prompt, title), context (mood at writing, stress, trigger source, linked check-in), AI enrichment (sentiment, sentiment score, emotion tags, distress flags, topic tags, cognitive distortions), engagement (word count, writing duration, completeness, edited flag), therapeutic (action item, completion, gratitude count, AI reframe suggestion, helpfulness rating).

**WeeklyAssessment** (`weekly_assessments` table)  
7-dimension weekly intelligence: subjective reflection (overall mood, core struggle, primary win, weekly reflection, next week intention), emotional trajectory (stability, mood variety, negative days, crying days, dominant mood), clinical markers (purpose, social connection, burnout risk, anhedonia, self-efficacy, addiction awareness), protective factors (exercise, sleep, social, gratitude, journal, protective factor score), behavioral aggregates (screen time, passive ratio, digital diet, fragmentation, behavior risk, distress, high-risk days, green days, unlocks, binge sessions, escape behavior days), delta tracking (screen time delta, distress delta, purpose delta, overall trajectory), AI narrative (generated insight, primary risk factor, suggested action, recommendations, systemic risk flag, weekly wellness score).

### Clinical & Safety Entities

**RiskClassification** (`risk_classifications` table)  
AI classification output: 6 dimension scores (digital addiction, stress/anxiety, depression, social isolation, sleep disruption, low fulfilment), overall risk score, primary/secondary category, confidence, crisis flag, crisis reason, intervention shown flag, classification mode, feature data count, feature vector JSON, risk delta, risk moving average.

**CrisisEvent** (`crisis_events` table)  
Crisis episode records: crisis level, status (ACTIVE/RESOLVED), trigger signals JSON, actions taken JSON, resolved timestamp, resolution method, post-crisis mood, pre/post distress levels, assessment confidence, follow-up scheduled, debrief completed, safety check sent, trigger source.

**SuicideRiskEvent** (`suicide_risk_events` table)  
C-SSRS-aligned risk events: CSSRS tier (1-5), severity label, text tier, behavior tier, signal count, matched phrases JSON, active signals JSON, source, lockdown/notification/auto-contact triggered flags, distress level, mood score, night-time flag, linked crisis event ID, resolution.

**SafetyPlan** (`safety_plan` table)  
Clinical safety plan with JSON arrays: warning signals, coping strategies, reasons to live, trusted contacts, professional contacts, safe environments, completion status.

**TrustedContact** (`trusted_contacts` table)  
Emergency contacts: name, phone number, relationship type, emergency flag, crisis notification preference.

**ExerciseCompletion** (`exercise_completions` table)  
Therapeutic exercise tracking: exercise type (breathing/grounding), exercise name, completion timestamp, duration, pre/post distress levels, full completion flag.

### Support Entities

**UserBaseline** (`user_baseline` table)  
Rolling statistical baselines: 7-day and 30-day averages for screen time, plus 7-day averages for app switches, night usage, unlocks, launches, passive ratio, sleep, stress, mood. Standard deviations for 8 metrics. Data point count and baseline status (INSUFFICIENT/LEARNING/READY).

**UserProgress** (`user_progress` table)  
Gamification state: total XP, current streak, longest streak, last completion day, total tasks completed/skipped, crisis tasks completed, badges unlocked JSON.

**ErrorLog** (`error_logs` table)  
Structured worker error logging: worker name, category, exception type, message, stack trace snippet, resolution strategy, attempt count, retry success flag.

**WellnessSummary** (`wellness_summaries` table)  
Daily wellness state: risk level, wellness state, explanation text, reason summary, next best action, screen time, task completion score, top app, support suggested flag.

**DailyResetSession** (`daily_reset_sessions` table)  
Morning reset ritual records: focus task, first action, warning item, timer duration, completion status, readiness level, reflection note.

## 7.2 Migration Strategy

The database uses **27 incremental migrations** (v1 → v27), all registered in `AppDatabase.getInstance()`:

| Migration | Key Changes |
|-----------|-------------|
| v1→2 | QuestionnaireResponse: focus, distraction, energy fields |
| v2→3 | InterventionTask: completedAt, skippedAt. New: AppUsageSnapshot, WellnessSummary |
| v3→4 | New: UserBaseline (7d/30d averages) |
| v4→5 | New: BehaviorSnapshot (behavioral signals) |
| v5→6 | New: OnboardingProfile (30 fields) |
| v6→7 | New: DailyResetSession |
| v7→8 | AppUsageSnapshot expansion. DailyUsage expansion. New: UsageSession, BehaviorUsageSummary |
| v8→9 | DailyUsage: sleep proxy, consumption, engagement, category JSON |
| v9→10 | AppUsageSnapshot: passive, binge, session analytics, night usage |
| v10→11 | UsageSession: type, duration category, interruptions, notification trigger, loop detection |
| v11→12 | BehaviorSnapshot → 4-layer intelligence (20 new columns) |
| v12→13 | QuestionnaireResponse: 20+ psychological dimensions |
| v13→14 | New: WeeklyAssessment (50+ fields) |
| v14→15 | New: JournalEntry (25+ fields) |
| v15→16 | OnboardingProfile clinical expansion. WeeklyAssessment NLP fields |
| v16→17 | New: RiskClassification (20+ fields) |
| v17→18 | UserBaseline: std devs, new averages, status |
| v18→19 | InterventionTask: priority, difficulty, source, scheduling, efficacy, gamification, sequences |
| v19→20 | New: UserProgress (gamification state) |
| v20→21 | New: CrisisEvent, SafetyPlan, TrustedContact, ExerciseCompletion |
| v21→22 | New: SuicideRiskEvent |
| v22→23 | CrisisEvent expansion: safety check, resolved, trigger source |
| v23→24 | QuestionnaireResponse: urge to scroll, biggest distraction |
| v24→25 | QuestionnaireResponse: current concern |
| v25→26 | InterventionTask: observation window, pre/post risk, efficacy score |
| v26→27 | New: ErrorLog |

All migrations are **additive only** (ALTER TABLE ADD COLUMN or CREATE TABLE). No destructive column drops or table recreations. A `fallbackToDestructiveMigration()` safety net handles edge cases where the compiled schema hash doesn't match.

---

*End of Phase 2 — Continue to Phase 3: Component Catalog*
