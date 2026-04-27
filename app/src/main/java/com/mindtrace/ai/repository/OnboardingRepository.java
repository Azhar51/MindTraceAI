package com.mindtrace.ai.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.OnboardingProfileDao;
import com.mindtrace.ai.database.entity.OnboardingProfile;

public class OnboardingRepository {
    private static final String PREFS_NAME = "mindtrace_onboarding";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";

    private final OnboardingProfileDao onboardingProfileDao;
    private final SharedPreferences sharedPreferences;

    public OnboardingRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context.getApplicationContext());
        onboardingProfileDao = database.onboardingProfileDao();
        sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public LiveData<OnboardingProfile> getProfile() {
        return onboardingProfileDao.getProfile();
    }

    public OnboardingProfile getProfileSync() {
        return onboardingProfileDao.getProfileSync();
    }

    public boolean isOnboardingCompleted() {
        if (sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)) {
            return true;
        }
        return onboardingProfileDao.getProfileSync() != null;
    }

    public void completeOnboarding(OnboardingProfile profile) {
        if (profile == null) {
            return;
        }
        profile.id = 1;
        onboardingProfileDao.insertOrReplace(profile);
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply();
    }

    public void setOnboardingCompleted(boolean completed) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }
}
