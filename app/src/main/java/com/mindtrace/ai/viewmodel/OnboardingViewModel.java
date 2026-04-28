package com.mindtrace.ai.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mindtrace.ai.database.entity.InterventionTask;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.onboarding.OnboardingAssessment;
import com.mindtrace.ai.onboarding.OnboardingProfileAnalyzer;
import com.mindtrace.ai.repository.OnboardingRepository;
import com.mindtrace.ai.repository.UsageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class OnboardingViewModel extends AndroidViewModel {
    private final OnboardingRepository onboardingRepository;
    private final UsageRepository usageRepository;
    private final OnboardingProfileAnalyzer profileAnalyzer;
    private final ExecutorService executorService;
    private final MutableLiveData<SubmissionResult> submissionResult = new MutableLiveData<>();

    public OnboardingViewModel(@NonNull Application application) {
        super(application);
        onboardingRepository = new OnboardingRepository(application);
        usageRepository = new UsageRepository(application);
        profileAnalyzer = new OnboardingProfileAnalyzer();
        executorService = com.mindtrace.ai.util.AppExecutors.diskIO();
    }

    public LiveData<SubmissionResult> getSubmissionResult() {
        return submissionResult;
    }

    public void completeOnboarding(OnboardingProfile profile) {
        executorService.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                profile.id = 1;
                profile.timestamp = now;

                OnboardingAssessment assessment = profileAnalyzer.assess(profile);
                onboardingRepository.completeOnboarding(profile);
                usageRepository.insertResponseSync(buildSeedQuestionnaireResponse(profile, now));
                usageRepository.replaceActiveTasksSync(buildStarterTasks(profile, assessment, now));
                usageRepository.computeUserBaselineIfOutdated();

                submissionResult.postValue(new SubmissionResult(
                        true,
                        assessment,
                        assessment.supportRecommended,
                        "Setup complete. MindTrace has personalized your starting guidance."
                ));
            } catch (Exception e) {
                submissionResult.postValue(new SubmissionResult(
                        false,
                        null,
                        false,
                        "MindTrace could not save your setup. Please try again."
                ));
            }
        });
    }

    private QuestionnaireResponse buildSeedQuestionnaireResponse(OnboardingProfile profile, long timestamp) {
        QuestionnaireResponse response = new QuestionnaireResponse();
        response.timestamp = timestamp;
        response.mood = resolveMood(profile);
        response.stressLevel = clamp(profile.stressLevel);
        response.lonelinessLevel = clamp(profile.lonelinessLevel);
        response.motivationLevel = clamp(profile.motivationLevel);
        response.sleepHours = profile.sleepHours <= 0f ? 7f : profile.sleepHours;
        response.workPressure = resolveBand(profile.workPressure);
        response.socialSupport = profile.socialSupportLevel >= 3;
        response.goalClarity = profile.feelingStuck <= 2
                && profile.primaryGoal != null
                && !profile.primaryGoal.trim().isEmpty();
        response.focusLevel = resolveBand(profile.focusLevel);
        response.feltDistracted = profile.distractionLevel >= 3 || profile.appSwitchingHabit >= 3;
        response.energyLevel = resolveBand(profile.energyLevel);
        return response;
    }

    private List<InterventionTask> buildStarterTasks(OnboardingProfile profile, OnboardingAssessment assessment, long now) {
        List<InterventionTask> tasks = new ArrayList<>();

        addTask(tasks, createTask(
                "Lock the First Hour",
                "Keep your phone away from the first meaningful task of the day.",
                "Focus",
                60,
                now
        ));

        switch (assessment.primaryFocus) {
            case "sleep_reset":
                addTask(tasks, createTask(
                        "Night Shutdown",
                        "Pick a time tonight to stop casual scrolling and keep the phone out of reach.",
                        "Recovery",
                        20,
                        now
                ));
                addTask(tasks, createTask(
                        "Morning Reset",
                        "Get out of bed before opening social media or random apps.",
                        "Routine",
                        10,
                        now
                ));
                break;
            case "direction_reset":
                addTask(tasks, createTask(
                        "Write Today's 3 Priorities",
                        "Choose the three things that matter most before the day gets noisy.",
                        "Purpose",
                        5,
                        now
                ));
                addTask(tasks, createTask(
                        "Start a 25-Minute Block",
                        "Begin one focused work block before casual phone use.",
                        "Focus",
                        25,
                        now
                ));
                break;
            case "calm_reset":
                addTask(tasks, createTask(
                        "Two-Minute Reset",
                        "Pause, breathe slowly, and settle your attention before opening distracting apps.",
                        "Mindfulness",
                        2,
                        now
                ));
                addTask(tasks, createTask(
                        "Single-Task Start",
                        "Choose only one task for the next block and ignore everything else.",
                        "Focus",
                        15,
                        now
                ));
                break;
            case "routine_reset":
                addTask(tasks, createTask(
                        "Build the Morning Frame",
                        "Start with one planned action instead of drifting into the phone.",
                        "Routine",
                        10,
                        now
                ));
                addTask(tasks, createTask(
                        "One Planned Block",
                        "Protect one focused block today with no random switching.",
                        "Focus",
                        25,
                        now
                ));
                break;
            case "phone_control":
            default:
                addTask(tasks, createTask(
                        "Focus Mode Reset",
                        "Turn on focus mode for one clean block before opening distracting apps.",
                        "Focus",
                        30,
                        now
                ));
                addTask(tasks, createTask(
                        "Evening Scroll Cutoff",
                        "Set a stop point for scrolling so tonight does not steal tomorrow.",
                        "Detox",
                        20,
                        now
                ));
                break;
        }

        if (assessment.supportRecommended || profile.supportNeeded) {
            addTask(tasks, createTask(
                    "Support Anchor",
                    "Choose one trusted person or calming support tool you can use if the day starts feeling heavy.",
                    "Support",
                    5,
                    now
            ));
        }

        return tasks.size() > 3 ? new ArrayList<>(tasks.subList(0, 3)) : tasks;
    }

    private InterventionTask createTask(String title, String description, String category, int duration, long now) {
        InterventionTask task = new InterventionTask();
        task.title = title;
        task.description = description;
        task.category = category;
        task.durationMinutes = duration;
        task.dateCreated = now;
        task.isCompleted = false;
        task.isSkipped = false;
        return task;
    }

    private void addTask(List<InterventionTask> tasks, InterventionTask candidate) {
        for (InterventionTask task : tasks) {
            if (task.title != null && task.title.equalsIgnoreCase(candidate.title)) {
                return;
            }
        }
        tasks.add(candidate);
    }

    private String resolveMood(OnboardingProfile profile) {
        if (profile.anxietyLevel >= 4 || profile.stressLevel >= 4 || profile.overthinkingLevel >= 4) {
            return "Anxious";
        }
        if (profile.motivationLevel <= 2 || profile.feelingStuck >= 4 || profile.selfDoubtLevel >= 4) {
            return "Sad";
        }
        if (profile.motivationLevel >= 4 && profile.energyLevel >= 4 && profile.stressLevel <= 2) {
            return "Happy";
        }
        return "Neutral";
    }

    private String resolveBand(int value) {
        int normalized = clamp(value);
        if (normalized <= 2) {
            return "Low";
        }
        if (normalized >= 4) {
            return "High";
        }
        return "Medium";
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(5, value));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // executorService is AppExecutors.diskIO() — never shut down the shared pool
    }

    public static class SubmissionResult {
        public final boolean successful;
        public final OnboardingAssessment assessment;
        public final boolean openSupportFirst;
        public final String message;

        public SubmissionResult(
                boolean successful,
                OnboardingAssessment assessment,
                boolean openSupportFirst,
                String message
        ) {
            this.successful = successful;
            this.assessment = assessment;
            this.openSupportFirst = openSupportFirst;
            this.message = message;
        }
    }
}
