package com.mindtrace.ai.viewmodel;

import android.app.Application;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mindtrace.ai.database.entity.DailyResetSession;
import com.mindtrace.ai.database.entity.OnboardingProfile;
import com.mindtrace.ai.onboarding.OnboardingAssessment;
import com.mindtrace.ai.onboarding.OnboardingProfileAnalyzer;
import com.mindtrace.ai.repository.DailyResetRepository;
import com.mindtrace.ai.repository.OnboardingRepository;
import com.mindtrace.ai.ui.model.DailyResetState;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class DailyResetViewModel extends AndroidViewModel {
    private final DailyResetRepository dailyResetRepository;
    private final OnboardingRepository onboardingRepository;
    private final OnboardingProfileAnalyzer onboardingProfileAnalyzer;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final MutableLiveData<DailyResetState> state = new MutableLiveData<>(DailyResetState.loading());
    private final MutableLiveData<Boolean> finishSignal = new MutableLiveData<>(false);

    private CountDownTimer countDownTimer;
    private DailyResetSession currentSession;
    private boolean initialized;
    private String pendingResetTitle;
    private String pendingFocusTask;
    private String pendingFirstAction;
    private String pendingWarningItem;
    private boolean pendingHighRisk;
    private long sessionDurationMillis = 25L * 60L * 1000L;
    private long timeRemainingMillis = sessionDurationMillis;
    private boolean timerRunning;

    public DailyResetViewModel(@NonNull Application application) {
        super(application);
        dailyResetRepository = new DailyResetRepository(application);
        onboardingRepository = new OnboardingRepository(application);
        onboardingProfileAnalyzer = new OnboardingProfileAnalyzer();
        executorService = com.mindtrace.ai.util.AppExecutors.diskIO();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public LiveData<DailyResetState> getState() {
        return state;
    }

    public LiveData<Boolean> getFinishSignal() {
        return finishSignal;
    }

    public void initialize(
            String missionTitle,
            ArrayList<String> missionSteps,
            ArrayList<String> warningItems,
            String nextBestActionTitle,
            int riskIndex,
            boolean highRisk
    ) {
        if (initialized) {
            return;
        }
        initialized = true;
        pendingHighRisk = highRisk || riskIndex >= 70;

        executorService.execute(() -> {
            OnboardingProfile profile = onboardingRepository.getProfileSync();
            OnboardingAssessment assessment = onboardingProfileAnalyzer.assess(profile);

            pendingResetTitle = buildResetTitle(missionTitle, assessment, pendingHighRisk);
            pendingFocusTask = buildFocusTask(missionSteps, assessment, pendingHighRisk);
            pendingFirstAction = buildFirstAction(nextBestActionTitle, assessment, pendingHighRisk);
            pendingWarningItem = buildWarningItem(warningItems, assessment);
            int durationMinutes = resolveDurationMinutes(pendingHighRisk, assessment);

            currentSession = dailyResetRepository.getOrCreateTodaySession(
                    pendingResetTitle,
                    pendingFocusTask,
                    pendingFirstAction,
                    pendingWarningItem,
                    durationMinutes
            );

            if (currentSession != null) {
                sessionDurationMillis = Math.max(1, currentSession.timerDurationMinutes) * 60L * 1000L;
                timeRemainingMillis = currentSession.isCompleted ? 0L : sessionDurationMillis;
            }

            state.postValue(buildState(currentSession, false));

            if (currentSession != null && !currentSession.isCompleted) {
                mainHandler.post(() -> startTimerInternal(sessionDurationMillis));
            }
        });
    }

    public void toggleTimer() {
        if (currentSession == null || currentSession.isCompleted) {
            return;
        }
        if (timerRunning) {
            pauseTimer();
        } else {
            startTimerInternal(timeRemainingMillis <= 0L ? sessionDurationMillis : timeRemainingMillis);
        }
    }

    public void markResetComplete(String reflectionNote, int readinessLevel) {
        if (currentSession != null && currentSession.isCompleted) {
            finishSignal.setValue(true);
            return;
        }

        cancelTimer();
        executorService.execute(() -> {
            currentSession = dailyResetRepository.completeTodayReset(reflectionNote, readinessLevel);
            timeRemainingMillis = 0L;
            timerRunning = false;
            state.postValue(buildState(currentSession, false));
            finishSignal.postValue(true);
        });
    }

    public void finishCompletedState() {
        finishSignal.setValue(true);
    }

    private void pauseTimer() {
        cancelTimer();
        timerRunning = false;
        state.setValue(buildState(currentSession, false));
    }

    private void startTimerInternal(long startFromMillis) {
        cancelTimer();
        timerRunning = true;
        timeRemainingMillis = Math.max(1_000L, startFromMillis);
        state.postValue(buildState(currentSession, true));
        countDownTimer = new CountDownTimer(timeRemainingMillis, 1_000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeRemainingMillis = millisUntilFinished;
                state.postValue(buildState(currentSession, true));
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                timeRemainingMillis = 0L;
                state.postValue(buildState(currentSession, false));
            }
        };
        countDownTimer.start();
    }

    private DailyResetState buildState(DailyResetSession session, boolean isRunning) {
        if (session == null) {
            return DailyResetState.loading();
        }

        long remaining = session.isCompleted ? 0L : Math.max(0L, timeRemainingMillis);
        return new DailyResetState(
                safe(session.resetTitle, pendingResetTitle, "Daily Reset"),
                buildSupportingText(session),
                safe(session.focusTask, pendingFocusTask, "Start one focused block."),
                safe(session.firstAction, pendingFirstAction, "Keep the phone away until the timer ends."),
                safe(session.warningItem, pendingWarningItem, "Do not open distracting apps before this session."),
                Math.max(1, session.timerDurationMinutes),
                remaining,
                isRunning,
                session.isCompleted,
                session.completedAt,
                buildTimerButtonLabel(session, isRunning, remaining),
                session.isCompleted ? "Back to Home" : "Mark Reset Complete",
                "How ready do you feel now?",
                buildCompletionMessage(session),
                false
        );
    }

    private String buildResetTitle(String missionTitle, OnboardingAssessment assessment, boolean highRisk) {
        if (highRisk) {
            return "Protect the first clean block of the day.";
        }
        if (missionTitle != null && !missionTitle.trim().isEmpty()) {
            return missionTitle.trim();
        }
        if (assessment != null && assessment.missionTitle != null && !assessment.missionTitle.trim().isEmpty()) {
            return assessment.missionTitle.trim();
        }
        return "Start the day with one clear move.";
    }

    private String buildFocusTask(List<String> missionSteps, OnboardingAssessment assessment, boolean highRisk) {
        if (highRisk) {
            return "Finish one clean block before your phone chooses the day for you.";
        }
        if (missionSteps != null) {
            for (String step : missionSteps) {
                if (step != null && !step.trim().isEmpty()) {
                    return step.trim();
                }
            }
        }
        if (assessment != null && assessment.missionSteps != null) {
            for (String step : assessment.missionSteps) {
                if (step != null && !step.trim().isEmpty()) {
                    return step.trim();
                }
            }
        }
        return "Finish one focused work block.";
    }

    private String buildFirstAction(String nextBestActionTitle, OnboardingAssessment assessment, boolean highRisk) {
        if (highRisk) {
            return "Put the phone out of reach and protect the next 30 minutes.";
        }
        if (nextBestActionTitle != null && !nextBestActionTitle.trim().isEmpty()) {
            return trimSentence(nextBestActionTitle.trim(), 95);
        }
        if (assessment != null && assessment.nextBestActionTitle != null && !assessment.nextBestActionTitle.trim().isEmpty()) {
            return trimSentence(assessment.nextBestActionTitle.trim(), 95);
        }
        return "Start one 25-minute focus block before opening distracting apps.";
    }

    private String buildWarningItem(List<String> warningItems, OnboardingAssessment assessment) {
        if (warningItems != null) {
            for (String warning : warningItems) {
                if (warning != null && !warning.trim().isEmpty()) {
                    return warning.trim();
                }
            }
        }
        if (assessment != null && assessment.warningItems != null) {
            for (String warning : assessment.warningItems) {
                if (warning != null && !warning.trim().isEmpty()) {
                    return warning.trim();
                }
            }
        }
        return "Do not open social media before this session ends.";
    }

    private int resolveDurationMinutes(boolean highRisk, OnboardingAssessment assessment) {
        if (highRisk || (assessment != null && assessment.phoneRiskHigh)) {
            return 30;
        }
        return 25;
    }

    private String buildSupportingText(DailyResetSession session) {
        if (session.isCompleted) {
            return "Today's reset is already complete. Protect the momentum you created.";
        }
        if (pendingHighRisk) {
            return "Keep this first block strict and simple. The goal is control, not intensity.";
        }
        return "Start the day with one focused move before drift takes over.";
    }

    private String buildTimerButtonLabel(DailyResetSession session, boolean isRunning, long remaining) {
        if (session.isCompleted) {
            return "Completed";
        }
        if (isRunning) {
            return "Pause Timer";
        }
        if (remaining < sessionDurationMillis) {
            return "Resume Timer";
        }
        return "Start Timer";
    }

    private String buildCompletionMessage(DailyResetSession session) {
        if (!session.isCompleted || session.completedAt <= 0L) {
            return "";
        }
        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(session.completedAt));
        return "Reset completed at " + time + ". Carry that structure into your next block.";
    }

    private String safe(String primary, String secondary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.trim().isEmpty()) {
            return secondary.trim();
        }
        return fallback;
    }

    private String trimSentence(String text, int maxChars) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelTimer();
        // executorService is AppExecutors.diskIO() — never shut down the shared pool
    }
}
