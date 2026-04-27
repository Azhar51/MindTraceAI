<h1 align="center">MindTrace AI</h1>

<p align="center">
  <strong>An Intelligent, Privacy-First Mental Wellness & Behavioral Tracking Platform</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Architecture-MVVM-blue" alt="Architecture">
  <img src="https://img.shields.io/badge/Database-Room-orange" alt="Database">
  <img src="https://img.shields.io/badge/Language-Java-red" alt="Language">
</p>

---

## 🧠 What is MindTrace AI?
MindTrace AI is a comprehensive Android mental wellness application that moves beyond simple screen-time tracking. It combines **passive digital behavior monitoring** with **active psychological self-assessment** to provide a holistic, data-driven understanding of your mental health.

Unlike traditional trackers, MindTrace AI operates as a **behavioral intelligence system**. It silently captures usage patterns, detects risk signals (like dopamine loops and binge sessions), fuses them with self-reported mood data, and runs a multi-dimensional risk classification engine across six clinical categories.

## ✨ Key Features

*   📊 **Passive Behavioral Intelligence:** Silently monitors app usage, session patterns, and scroll intensity without requiring constant user input.
*   🎯 **Multi-Modal Risk Classification:** Evaluates risk across 6 clinical dimensions: Digital Addiction, Stress/Anxiety, Depression Risk, Social Isolation, Sleep Disruption, and Low Fulfilment.
*   🔄 **Closed-Loop Efficacy Tracking:** Generates personalized CBT-based intervention tasks and measures their actual effectiveness via a 2-hour observation window. The system learns what works for *you*.
*   🚨 **Clinical-Grade Crisis Detection:** Features C-SSRS-aligned suicide risk detection, automatic lockdown modes, safety plans, and trusted contact routing.
*   🔒 **Offline-First & Privacy Focused:** All data processing, including risk classification and database storage, happens **100% on-device**. Your mental health data never leaves your phone.

## 🛠️ Technology Stack

*   **Language:** Java
*   **Architecture:** Strict MVVM (Model-View-ViewModel) + Repository Pattern
*   **Local Database:** Room Persistence Library (27 Migrations, 22 Entities)
*   **Background Processing:** WorkManager & Android Foreground Services
*   **AI/ML:** TFLite-ready Hybrid Classification Architecture + Gemini API integration
*   **UI/UX:** Material Design Components, Custom Data Visualizations, Glassmorphism aesthetic

## 📖 Complete Technical Documentation

For a deep dive into how the AI engines, background workers, and behavioral classification systems operate, please refer to our comprehensive **60-page Technical & Product Documentation** located in the [`/docs`](./docs) folder:

1. [Foundation & Vision](./docs/Phase1_Foundation_and_Vision.md)
2. [Architecture & Data Model](./docs/Phase2_Architecture_and_Data.md)
3. [Component Catalog](./docs/Phase3_Component_Catalog.md)
4. [AI Engines & Logic Deep Dive](./docs/Phase4_AI_Engines_and_Logic.md)
5. [Workflows & Impact Analysis](./docs/Phase5_Workflows_and_Impact.md)
6. [Assessment & Future Roadmap](./docs/Phase6_Assessment_and_Future.md)

## 🚀 Getting Started

### Prerequisites
*   Android Studio (Iguana or newer recommended)
*   Android SDK 34
*   A physical Android device for testing (Emulators do not have proper Usage Stats history).

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/Azhar51/MindTraceAI.git
   ```
2. Open the project in Android Studio.
3. **Important:** Create a file named `local.properties` in the root directory (if it doesn't exist) and add your Gemini API Key to enable the AI Coach feature:
   ```properties
   GEMINI_API_KEY="your_api_key_here"
   ```
4. Sync the project with Gradle files.
5. Run the app on your physical device. Make sure to grant the "Usage Access" permission when prompted during onboarding!

---
*MindTrace AI was built to bridge the gap between digital well-being features and genuine mental health support.*
