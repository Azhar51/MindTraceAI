# Phase 7 — Comprehensive System Overview & Uncovered Details

---

# 1. Complete Input Features of the App

The app gathers a robust dataset combining both passive behavioral telemetry and active psychological self-reporting:

## 1.1 Passive Inputs (Zero User Effort)
- **Usage Stats (Android System)**: Total screen time, per-app usage, foreground session start/end times, launch counts, app switch events.
- **Accessibility Service Telemetry**: Cross-app scroll events to compute `scrollIntensityScore`.
- **Notification Listener Telemetry**: Measurement of how quickly users respond to notifications (`notificationResponseAvgMs`).
- **Screen Events**: Unlocks, screen on/off events to compute compulsive checking and attention fragmentation.
- **System Time**: For circadian analysis (late-night usage, morning phone grab).

## 1.2 Active Inputs (User Provided)
- **Onboarding Demographics & Goals**: Age, primary wellness goal, areas needing help.
- **Psychological Assessments**: Stress (0-5), anxiety (0-5), loneliness, self-doubt, overthinking, feeling stuck, motivation.
- **Digital Habits Self-Report**: Social media intensity, late-night use, binge scrolling tendency, app-switching habits.
- **Clinical & Lifestyle Markers**: Addiction scale, purpose score, coping style, personality archetype, routine consistency, physical activity.
- **Daily Check-ins**: Mood emoji (mapped to valence), stress slider, sleep hours, subjective focus/distress.
- **Therapeutic Actions**: Journal text (free-form), completion of breathing/grounding exercises, focus session durations.
- **Intervention Feedback**: Task effectiveness ratings, post-task mood updates.
- **Crisis Information**: Distress slider during crisis, safety plan data (warning signs, coping strategies, trusted contacts).

---

# 2. Entire System Logic and Final Decision Logic

The core logic of MindTrace AI functions as a continuous feedback loop ranging from data ingestion to personalized action:

## 2.1 The AI Intelligence Pipeline
1. **Feature Extraction**: Passive usage logs and active self-reports are synthesized into a **36-dimension Feature Vector**.
2. **Classification & Scoring (`MultiModalClassifier`)**: The 36 features are evaluated against a weighted, rules-based algorithm (with ML-hybrid readiness) to score the user on a [0.0 - 1.0] scale across **6 Clinical Dimensions**: 
   - Digital Addiction, Stress/Anxiety, Depression Risk, Social Isolation, Sleep Disruption, Low Fulfilment.
3. **Anomaly & Baseline Comparison**: The current day's behavior is compared against a 7-day rolling average (baseline). Deviations greater than 2.0 standard deviations flag abnormal behavior.

## 2.2 Final Decision Logic (Interventions & Actions)
Based on the classification scores and deviations, the app's `InterventionEngine` and `InsightEngine` make the final decisions:
- **Task Generation**: The system identifies the *dominant* risk category (e.g., Sleep Disruption) and selects specific therapeutic tasks (e.g., "Screen-free hour before bed"). 
- **Personalization & Difficulty**: Tasks are filtered based on the user's current streak (difficulty scaling), time of day, and avoiding recent repeats.
- **Proactive Nudging**: If real-time logic (`BehavioralNudgeEngine`) detects active bingeing or dopamine loops (A→B→A→B app switching), it interrupts the user with a micro-intervention or focus block overlay.

## 2.3 Efficacy Closed-Loop Learning
The decision logic evolves. When a user completes a task, the system enforces a **2-Hour Observation Window**. It then measures:
- **Behavioral Shift**: Did the user's risk score actually go down?
- **Sentiment Shift**: Did their self-reported mood improve?
This yields an efficacy score (`70% behavior + 30% mood`). The system uses these scores to continuously adjust the probability weights for future task recommendations, learning what uniquely works for the individual.

## 2.4 Crisis Detection Logic
A parallel high-priority logic path scans for safety threats using `CrisisDetector`:
- **Text Risk**: NLP scans journal entries for suicidal ideation markers.
- **Behavioral Risk**: Scans for sudden digital isolation or severe late-night usage spikes accompanying low mood.
- **Decision**: Maps signals to C-SSRS Tiers 1-5. Severe tiers immediately override the standard UI, launching `CrisisLockdownActivity`, enforcing phone restrictions, and auto-alerting trusted contacts.

---

# 3. What Data Is Used to Reach the Results

The insights, scores, and interventions presented to the user are derived from combining and aggregating multiple local database entities (Room DB):

- **`AppUsageSnapshot` & `DailyUsage`**: Used to determine total screen time, passive vs. productive ratio, and identify top distraction apps.
- **`UsageSession`**: Reconstructs exact app sequences to detect rapid app-switching, micro-sessions, and dopamine loops.
- **`BehaviorSnapshotEntity`**: The intermediate processed data containing the attention fragmentation index, escape behavior score, and compulsive check score.
- **`QuestionnaireResponse` & `OnboardingProfile`**: Provides the baseline psychological state, self-reported sleep, and distress severity to contextualize the behavioral data.
- **`JournalEntry`**: Enriched via the Gemini API to add sentiment valence and emotional tags, providing qualitative context to quantitative usage data.
- **`UserBaseline`**: Statistical historical averages used to compute standard deviations and detect daily anomalies.
- **`RiskClassification` history**: Used for trend analysis, 7-day trajectories, and day-over-day delta comparisons.
- **`InterventionTask` history**: Past task completion rates and efficacy scores used to weight future task recommendations.

---

