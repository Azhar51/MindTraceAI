# MindTrace AI — Complete Technical & Product Documentation

---

## Document Information

| Field | Value |
|-------|-------|
| **Project** | MindTrace AI |
| **Type** | Technical & Product Documentation |
| **Version** | 1.0 |
| **Date** | April 2026 |
| **Platform** | Android (Java, MVVM) |
| **Database Version** | 27 |
| **Target SDK** | 34 |
| **Min SDK** | 26 (Android 8.0 Oreo) |
| **Prepared For** | Senior Developer Review & Handoff |

---

## Document Structure

This document is organized into six phases:

- **Phase 1** — Foundation & Vision (This file)
- **Phase 2** — System Architecture & Data Model
- **Phase 3** — Component Catalog (Activities, Fragments, ViewModels)
- **Phase 4** — AI Engines, Logic Deep Dive & Workers
- **Phase 5** — Workflows, Diagrams & User Impact Analysis
- **Phase 6** — Strengths, Limitations, Risks, Roadmap & Appendix

---

# 1. Executive Summary

## 1.1 What Is MindTrace AI?

MindTrace AI is a comprehensive Android mental wellness application that combines **passive digital behavior monitoring** with **active psychological self-assessment** to provide users with a holistic, data-driven understanding of their mental health. Unlike traditional screen time trackers that merely count hours, MindTrace AI operates as a **behavioral intelligence system**.

The app captures usage patterns, detects behavioral risk signals (binge sessions, late-night usage, compulsive app-switching, dopamine loops), combines them with self-reported mood data and structured questionnaires, and runs a **multi-dimensional risk classification engine** across six clinical categories:

1. **Digital Addiction** — Compulsive checking, app dependency, binge sessions
2. **Stress / Anxiety** — Emotional volatility, overwhelm, coping difficulties
3. **Depression Risk** — Anhedonia, low purpose, withdrawal patterns
4. **Social Isolation** — Passive consumption replacing genuine connection
5. **Sleep Disruption** — Late-night phone use eroding circadian rhythm
6. **Low Fulfilment** — Lack of direction, purpose deficit, stagnation

The app then closes the loop by generating **personalized intervention tasks** — micro-actions grounded in cognitive behavioral therapy (CBT) principles — and tracks their efficacy through a **closed-loop observation window system**. A sentiment-enhanced efficacy pipeline (70% behavioral / 30% mood-based weighting) continuously adapts which interventions are recommended, creating a system that learns what works for each individual user.

## 1.2 Technical Scale

The system is built on a Java MVVM architecture with:

- **Room Persistence**: 27 migrations, 22 entities, 22 DAOs
- **Background Processing**: 10 WorkManager workers, 4 foreground/bound services
- **AI Engines**: 48 analysis/classification classes
- **UI**: 24 Activities, 8 primary Fragments, 52 layout XML files
- **Risk Engine**: 36-feature extraction, 6-dimension classification, hybrid ML readiness

The codebase is production-grade with structured error logging (ErrorLog entity), crash-safe state persistence (SharedPreferences + DB dual-write for crisis mode), and graceful degradation throughout every pipeline stage.

## 1.3 What Makes It Different

**1. Passive Intelligence**
Most wellness apps require constant manual input. MindTrace AI silently monitors app usage, session patterns, scroll intensity, and notification response latency to build a behavioral fingerprint without user effort. The user simply uses their phone normally — the system observes and learns.

**2. Multi-Modal Risk Classification**
The system fuses digital behavior data with self-reported psychological data across 36 features to produce risk scores in 6 clinical dimensions — not just a single generic "wellness score." Each dimension has its own scoring logic, severity thresholds (None → Mild → Watch → Moderate → High → Severe), and intervention strategies.

**3. Closed-Loop Efficacy Tracking**
Every intervention task is tracked through a 2-hour post-completion observation window. The system measures whether risk scores actually decreased after the user completed the intervention. This creates a feedback loop that prioritizes effective interventions and deprioritizes those that don't work for the specific user.

**4. Crisis-Aware Design**
The app includes C-SSRS-aligned (Columbia Suicide Severity Rating Scale) suicide risk detection, automatic lockdown mode for high-severity tiers, safety plan management, trusted contact alerting, and helpline routing — features typically found only in clinical-grade tools.

**5. Adaptive Architecture**
As more data accumulates, the system is designed to transition from rules-based classification to hybrid ML classification. The infrastructure for TFLite model loading, feature vector serialization, confidence scoring, and mode switching (rules-only → hybrid → ML-dominant) is already implemented in the MultiModalClassifier class.

---

# 2. Problem Statement

## 2.1 The Digital Mental Health Crisis

The modern smartphone creates a constellation of interconnected mental health challenges that existing tools address in isolation. A user who spends 7 hours on their phone, sleeps poorly, feels anxious, and lacks direction needs more than a screen time counter — they need an intelligent system that understands the relationships between these problems and guides them toward measurable improvement.

MindTrace AI targets the following user problems:

## 2.2 Digital Addiction & Compulsive Use

**The Problem**: Users spend 4–8+ hours daily on smartphones, often without conscious awareness. Compulsive checking (50–80+ unlocks per day), rapid app-switching (opening and closing apps within seconds), and dopamine-driven usage loops (Instagram → YouTube → TikTok → Instagram) erode attention span and create behavioral dependency.

**Why Existing Tools Fail**: Stock screen time trackers (Android Digital Wellbeing, iOS Screen Time) show aggregate numbers but provide no behavioral analysis. They tell you "you used your phone for 6 hours" but cannot tell you "you had 3 binge sessions, 12 dopamine loops, and your fragmentation index is 0.78."

**How MindTrace AI Addresses It**: The app computes a fragmentation index (session count / total time), compulsive check score (micro-sessions / unlocks), binge session detection (> 45 minutes continuous use), dopamine loop detection (repeated app-pair switching), and passive consumption ratio. These metrics power the Digital Addiction risk dimension.

## 2.3 Lack of Routine & Direction

**The Problem**: Many users lack structured daily habits. Without intentional routines, they default to passive consumption — endless scrolling, binge-watching, and reactive phone use. They know they should "use their phone less" but have no concrete, personalized steps to follow.

**How MindTrace AI Addresses It**: The InterventionEngine generates personalized tasks linked to the user's dominant risk category. Tasks are difficulty-graded (Easy → Medium → Hard), time-slot optimized (Morning → Afternoon → Evening → Anytime), and include explanatory "why this task" rationale. The DailyResetActivity provides a structured morning ritual with focus tasks, first actions, and warning items.

## 2.4 Poor Sleep Hygiene

**The Problem**: Late-night phone use (after 10 PM) directly disrupts circadian rhythm and melatonin production. Users are often unaware of how much screen time occurs in the critical pre-sleep window. The blue light and cognitive stimulation from social media, gaming, and messaging apps delay sleep onset and reduce sleep quality.

**How MindTrace AI Addresses It**: The BehaviorSnapshotEntity tracks `bedtimeScrollMs` (scrolling after 10 PM), `lateNightSessionCount`, and `morningPhoneGrabMs` (time to first phone use after waking). The Sleep Disruption risk dimension uses these metrics alongside self-reported sleep hours and quality from check-ins. When late-night usage is detected, the NudgeWorker can send a proactive "bedtime nudge."

## 2.5 Emotional Instability & Stress

**The Problem**: Anxiety, stress, and mood volatility are increasingly common, particularly among young adults. Users lack objective tools to track emotional patterns over time. They may sense that "things have been harder lately" but cannot quantify whether their distress is stable, worsening, or improving.

**How MindTrace AI Addresses It**: Daily check-ins capture mood, stress (0–5), anxiety (0–5), emotional stability (0–5), and computed distress severity across 15+ psychological dimensions. The system correlates these self-reports with behavioral data — for example, detecting that social media binge sessions consistently follow low-mood check-ins (escape behavior detection).

## 2.6 Low Focus & Cognitive Fragmentation

**The Problem**: Rapid app-switching and micro-sessions (< 30 seconds) create "attention residue" — the cognitive cost of constantly shifting between contexts. Each switch reduces the brain's ability to sustain deep focus. Over time, the user's baseline attention span shortens.

**How MindTrace AI Addresses It**: The system measures attention span averages (`attentionSpanAvgMs`), compulsive check scores, rapid switch counts, and total interruptions. The `fragmentationIndex` quantifies how fragmented the user's digital behavior is. High fragmentation triggers interventions like focus sessions (FocusSessionActivity with Pomodoro timer) and app-blocking (FocusBlockActivity).

## 2.7 Social Isolation

**The Problem**: Passive content consumption (scrolling feeds without posting, liking, or messaging) creates the illusion of social connection while actually deepening isolation. A user who spends 3 hours on Instagram but never messages anyone is consuming, not connecting.

**How MindTrace AI Addresses It**: The system tracks passive consumption ratio (`passiveConsumptionRatio`), app diversity score, and distinguishes between social-interactive apps (messaging) and social-passive apps (feed scrolling). The Social Isolation risk dimension considers self-reported social interaction quality, meaningful conversations, and withdrawal tendencies from check-ins.

## 2.8 Crisis & Suicidal Ideation

**The Problem**: Some users experience acute distress, suicidal ideation, or crisis episodes. Consumer wellness apps typically ignore this entirely, creating a dangerous gap between "wellness tracking" and actual safety.

**How MindTrace AI Addresses It**: The CrisisDetector implements C-SSRS-aligned risk tier detection (Tier 1–5). When elevated risk is detected through journal content analysis (matched phrases), behavioral signals (sudden usage pattern changes), or self-reported distress, the system can:
- Display crisis support (CrisisActivity)
- Trigger lockdown mode (CrisisLockdownActivity) for Tier 4+
- Alert trusted contacts automatically
- Surface safety plans and helpline numbers
- Log events for clinical review (SuicideRiskEvent entity)

## 2.9 Intervention Fatigue

**The Problem**: Generic advice ("use your phone less," "go for a walk") fails because it lacks personalization. Users quickly tire of repetitive, irrelevant suggestions and disengage from the app entirely.

**How MindTrace AI Addresses It**: The InterventionEngine selects tasks from a library of 100+ templates (TaskTemplateRepository) based on:
- The user's dominant risk category
- Time of day and scheduled slot preference
- Difficulty appropriate to the user's current streak
- Historical efficacy data (which tasks actually worked)
- Micro-intervention flag for quick wins during high-distress moments

The closed-loop efficacy system ensures that ineffective interventions are deprioritized over time, creating genuinely adaptive recommendations.

---

# 3. Product Vision

## 3.1 The Three Stages

MindTrace AI's long-term vision encompasses three evolutionary stages:

### Stage 1: Behavioral Intelligence (Current State)

The app currently operates as a rules-based behavioral intelligence system with hybrid ML readiness. It captures digital behavior passively, combines it with active self-assessment, classifies risk across 6 dimensions, generates personalized interventions, and measures their effectiveness.

**Current capabilities:**
- 36-feature extraction from multiple data sources
- Rules-based risk classification with severity gradients
- Baseline comparison with standard deviation anomaly detection
- 7-day trend analysis (ClassificationTrendAnalyzer)
- Closed-loop efficacy tracking with sentiment enhancement
- Crisis detection with C-SSRS-aligned protocols
- Gamification (XP, streaks, badges, level-ups)

### Stage 2: Adaptive ML (Near-term Vision)

The architecture is designed for on-device TFLite model inference. The MultiModalClassifier already contains the infrastructure for:
- Feature vector serialization to JSON
- Model loading and prediction
- Confidence scoring per dimension
- Hybrid blending (configurable rules/ML ratio, default: 40% rules + 60% ML)
- Mode switching based on data availability

As users generate sufficient longitudinal data (30+ days with daily check-ins), the system can train per-user models that capture individual behavioral signatures — patterns that rules-based logic may miss.

### Stage 3: Predictive & Preventive (Long-term Vision)

The ultimate vision is a system that **predicts deterioration before it occurs**:
- Detecting early warning signs from behavioral drift (subtle changes in usage patterns that precede mood deterioration)
- Recommending preventive interventions before crisis points
- Adapting in real-time to the user's evolving patterns
- Providing clinician-grade longitudinal reports for professional handoff

This stage requires accumulated longitudinal data, validated ML models, and potentially federated learning for population-level insights while preserving privacy.

## 3.2 Definition of User Success

| Metric | Target | Measurement Method |
|--------|--------|--------------------|
| Screen time reduction | 15–25% over 30 days | DailyUsage.screenTimeMillis trend |
| Passive consumption decrease | Below 40% ratio | BehaviorSnapshot.passiveConsumptionRatio |
| Mood improvement | Upward 7-day moving average | QuestionnaireResponse.mood trend |
| Task completion rate | Above 60% | InterventionTask completion count |
| Efficacy positivity | > +3% average score | EfficacyMetrics.overallScore |
| Crisis safety | Zero missed events | SuicideRiskEvent false negative rate |
| Engagement consistency | 7+ day streak | UserProgress.currentStreak |
| Sleep improvement | +30min avg sleep duration | QuestionnaireResponse.sleepHours trend |
| Fragmentation decrease | Below 0.5 index | BehaviorSnapshot.fragmentationIndex |

---

# 4. User Journey End-to-End

## 4.1 App Install & First Launch

When the user installs MindTrace AI from the Play Store and opens it for the first time, the **SplashActivity** renders a premium animated splash screen:

- **Background**: Radial gradient from `#0D0D1A` (deep navy) center to `#1A1A2E` edges
- **Logo Glow**: Circular gradient (`#7C8FFF` → transparent) with continuous 360° rotation over 12 seconds
- **Logo Container**: Fade + scale animation (0.85 → 1.0) with overshoot interpolator over 500ms
- **App Name**: Slides up 12dp with fade-in at 200ms delay
- **Tagline**: Slides up 8dp with fade-in at 800ms delay
- **Loading Dot**: Pulses opacity 0.3 → 0.8 → 0.3 in 1.5-second loop
- **Floating Particles**: 8 ambient particles drift upward with random colors, sizes, and speeds

After the 2-second animation, the system performs an **authentication and routing check** on a background thread:

```
Priority 1: Active crisis? → CrisisLockdownActivity (tier ≥ 4) or CrisisActivity
Priority 2: User exists? → LoginActivity (if no User entity in Room)
Priority 3: Onboarding done? → QuestionnaireActivity (if OnboardingProfile incomplete)
Priority 4: Permissions granted? → PermissionActivity (if any required permission missing)
Priority 5: All clear → MainActivity
```

The crisis state is persisted in **both** SharedPreferences (`mindtrace_crisis/active_crisis`) and the CrisisEvent table. This dual-write ensures that even if the app crashes during a crisis, the crisis state survives and the user is routed back to safety mode on next launch.

## 4.2 Authentication

New users arrive at **LoginActivity** where they can sign in with their name or navigate to **SignupActivity** for account creation. Authentication is entirely local — no remote server, no cloud sync, no external accounts. The system creates a single `User` entity (id=1) in Room with the user's name and creation timestamp.

This design decision reflects the privacy-first philosophy: mental health data never leaves the device. The User entity serves primarily as a "gate" — its existence signals that the user has completed initial setup.

## 4.3 Onboarding Questionnaire

The **QuestionnaireActivity** presents a comprehensive 30+ field psychological assessment. This is the richest data collection point in the entire app, seeding the `OnboardingProfile` entity that provides the initial feature vector for risk classification.

**Sections and Fields:**

**Demographics & Goals**
- Age range (dropdown: Under 18, 18–24, 25–34, 35–44, 45+)
- Primary goal (free text or preset: Reduce screen time, Improve sleep, Manage stress, Build routine, etc.)
- Help areas (multi-select CSV)

**Emotional State (7 dimensions, each 0–5 slider)**
- Stress level
- Anxiety level
- Motivation level
- Loneliness level
- Self-doubt level
- Overthinking level
- Feeling stuck

**Sleep Assessment**
- Average sleep hours (float slider, 0–12)
- Sleep quality (0–5 rating)

**Digital Behavior (6 risk indicators, each 0–5)**
- Social media use intensity
- Late-night phone use frequency
- App addiction risk self-assessment
- Overuse pattern awareness
- Binge scrolling tendency
- App-switching habit

**Routine & Productivity (5 fields)**
- Routine consistency (0–5)
- Productive habits (0–5)
- Procrastination level (0–5)
- Physical activity level (0–5)
- Screen-free activities count

**Social Wellbeing**
- Social support level (0–5)
- Support needed flag
- Safety support enabled (consent for crisis features)

**Clinical Markers (Advanced)**
- Addiction scale (0–10)
- Purpose score (0–5)
- Coping style (dropdown: Avoidant, Problem-solving, Emotional, Social, Creative)
- Personality archetype (dropdown)
- Mental health history (text)
- Peak vulnerability time (dropdown: Morning, Afternoon, Evening, Late Night)
- Readiness to change (0–5)

The data is saved to the `OnboardingProfile` entity and an initial `QuestionnaireResponse` is created. This provides enough signal for the classification engine to produce a meaningful first risk assessment even before any usage data is collected.

## 4.4 Permission Grants

**PermissionActivity** guides users through 4 required permissions in a sequential flow. Each step includes a clear explanation of why the permission is needed and what data it enables. Already-granted permissions are automatically skipped.

**Permission 1: Usage Stats Access (PACKAGE_USAGE_STATS)**
- Required for all screen time and app usage tracking
- Opens Android's system settings for usage access
- This is the most critical permission — without it, the behavioral intelligence engine has no data

**Permission 2: Notifications (POST_NOTIFICATIONS, API 33+)**
- Required for daily reminders, behavioral nudges, crisis alerts, and weekly reports
- Uses standard Android permission dialog

**Permission 3: Accessibility Service**
- Required for scroll intensity telemetry (Feature D13)
- Tracks scroll events across apps to compute scrollIntensityScore
- Opens Accessibility settings

**Permission 4: Notification Listener**
- Required for notification response latency tracking (Feature D14)
- Measures how quickly users respond to notifications (notificationResponseAvgMs)
- Opens Notification Access settings

## 4.5 Baseline Creation (Days 1–7)

After permissions are granted and the user enters the main app, a silent learning period begins. The **BaselineComputeWorker** (runs daily) collects 7-day rolling averages for:

| Metric | Field | Purpose |
|--------|-------|---------|
| Screen time | `avgScreenTime7d` / `avgScreenTime30d` | Normal usage baseline |
| App switches | `avgAppSwitches7d` | Attention fragmentation baseline |
| Night usage | `avgNightUsageMinutes7d` | Sleep disruption baseline |
| Unlocks | `avgUnlocks7d` | Compulsive checking baseline |
| Launches | `avgLaunches7d` | App dependency baseline |
| Passive ratio | `avgPassiveRatio7d` | Content consumption baseline |
| Sleep hours | `avgSleep7d` | Self-reported sleep baseline |
| Stress level | `avgStress7d` | Self-reported stress baseline |
| Mood score | `avgMoodScore7d` | Self-reported mood baseline |

**Standard deviations** are computed for each metric to enable anomaly detection — if today's screen time is > 2σ above the 7-day average, it signals an abnormal usage day.

The `UserBaseline` entity transitions through three states:
- **INSUFFICIENT** — Fewer than 3 data points; classification runs in "baseline_only" mode
- **LEARNING** — 3–6 data points; classification confidence is moderate
- **READY** — 7+ data points; full baseline comparison active

## 4.6 Home Dashboard (Day 1+)

The **OverviewFragment** serves as the central command center. Even during the baseline learning period, it displays available data with appropriate "building baseline" indicators.

**Dashboard Cards (top to bottom):**

1. **Wellness Score Ring** — WellnessRingView showing composite risk score (0–100%) with animated arc and color gradient (green → amber → red)
2. **Risk Level Badge** — Current severity level (Low / Moderate / High) with color coding
3. **Mood Sparkline** — 7-day mood trend visualization with direction indicator
4. **Screen Time Hero Card** — Today's total screen time with delta vs. 7-day average
5. **Active Tasks** — Current intervention tasks with completion progress
6. **Streak & XP** — Current streak days, total XP, level indicator
7. **Behavioral Nudge Cards** — Proactive nudges based on real-time behavior
8. **Quick Actions** — One-tap buttons for Check-in, Journal, Focus Session, Breathing Exercise
9. **AI Coach** — Access to AiCoachActivity (Gemini-powered conversational support)

All data flows through **DashboardViewModel** — the central orchestrator (94KB, largest ViewModel) that aggregates data from UsageRepository, TaskRepository, ClassificationRepository, AssessmentRepository, and BaselineManager.

## 4.7 Daily Usage Cycle

Throughout each day, the following background processes operate silently:

| Worker | Frequency | Function |
|--------|-----------|----------|
| **UsageSnapshotWorker** | Every 3 hours | Captures per-app usage, session data, behavioral metrics from UsageStatsManager |
| **ClassificationWorker** | Every 4 hours | Runs the full 36-feature extraction → 6-dimension risk classification pipeline |
| **EfficacyWorker** | Every 2 hours | Finds completed tasks with expired observation windows, computes efficacy scores |
| **NudgeWorker** | Periodic | Evaluates real-time behavioral signals for proactive micro-nudges |
| **BaselineComputeWorker** | Daily | Updates 7-day rolling averages and standard deviations |
| **MidnightSummaryWorker** | Daily (midnight) | Compiles the complete daily behavioral snapshot |
| **DailyReminderWorker** | Daily (configurable) | Sends check-in reminders |
| **TaskReminderWorker** | Periodic | Reminds users of incomplete intervention tasks |
| **DataCleanupWorker** | Weekly | Prunes old data to manage storage |
| **WeeklyReportWorker** | Weekly | Generates and delivers weekly wellness reports |

**User-initiated interactions during the day:**

- **Mood Check-in** (DailyCheckInActivity) — Quick mood + stress + sleep capture
- **Task Completion** (TasksFragment) — Mark interventions done, rate effectiveness, log pre/post mood
- **Journal Entry** (JournalActivity) — Free-form journaling with AI sentiment analysis
- **Focus Session** (FocusSessionActivity) — Pomodoro-style focused work timer
- **Breathing Exercise** (BreathingExerciseActivity) — Guided breathing patterns
- **Grounding Exercise** (GroundingExerciseActivity) — 5-4-3-2-1 sensory grounding
- **AI Coach** (AiCoachActivity) — Conversational support powered by Gemini API

## 4.8 Weekly Cycle

Every 7 days, the **WeeklyAssessmentWorker** compiles a comprehensive weekly intelligence report:

- Emotional trajectory (stability score, mood variety, negative mood days, crying days)
- Clinical markers (purpose, social connection, burnout risk, anhedonia, self-efficacy)
- Protective factors (exercise days, sleep quality, social interactions, gratitude, journaling)
- Behavioral aggregates (average screen time, passive ratio, fragmentation, behavior risk)
- Delta tracking (week-over-week changes in screen time, distress, purpose)
- AI narrative (generated insight, primary risk factor, suggested action)
- Systemic risk flag (when multiple risk dimensions are simultaneously elevated)

The user can access this through **WeeklyAssessmentActivity** which renders the full report with trend visualizations.

## 4.9 Long-Term Engagement (Weeks → Months)

Over extended use, the system provides increasingly personalized value:

**Baseline Maturation**: 30-day averages stabilize, enabling more accurate anomaly detection. Standard deviations tighten as the system learns the user's "normal."

**Trend Detection**: The ClassificationTrendAnalyzer computes 7-day risk trajectories (rapidly improving → gradually improving → stable → gradually worsening → rapidly worsening) and identifies which categories are changing fastest.

**Efficacy Learning**: The efficacy pipeline accumulates data on which intervention categories work best for this specific user. The `buildEfficacyWeightMap()` function feeds this back into task generation, creating genuinely adaptive recommendations.

**ML Transition**: With 30+ days of data, the MultiModalClassifier can transition from rules-only to hybrid ML mode. The feature vector history enables model training, and the hybrid blending system allows gradual transition with configurable confidence thresholds.

**Gamification Depth**: The UserProgress system tracks total XP, current/longest streaks, badges unlocked, and tasks completed. Milestone notifications celebrate progress and provide intrinsic motivation for sustained engagement.

---

*End of Phase 1 — Continue to Phase 2: System Architecture & Data Model*
