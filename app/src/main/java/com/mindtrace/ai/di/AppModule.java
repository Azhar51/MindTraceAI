package com.mindtrace.ai.di;

import android.content.Context;

import androidx.room.Room;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.RiskClassificationDao;
import com.mindtrace.ai.database.dao.DailyResetSessionDao;
import com.mindtrace.ai.database.dao.JournalDao;
import com.mindtrace.ai.database.dao.OnboardingProfileDao;
import com.mindtrace.ai.database.dao.QuestionnaireDao;
import com.mindtrace.ai.database.dao.TaskDao;
import com.mindtrace.ai.database.dao.UsageDao;
import com.mindtrace.ai.repository.ClassificationRepository;
import com.mindtrace.ai.repository.DailyResetRepository;
import com.mindtrace.ai.repository.OnboardingRepository;
import com.mindtrace.ai.repository.SettingsRepository;
import com.mindtrace.ai.repository.UsageRepository;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    @Provides
    @Singleton
    public AppDatabase provideAppDatabase(@ApplicationContext Context context) {
        return AppDatabase.getInstance(context);
    }

    @Provides
    public UsageDao provideUsageDao(AppDatabase appDatabase) {
        return appDatabase.usageDao();
    }

    @Provides
    public QuestionnaireDao provideQuestionnaireDao(AppDatabase appDatabase) {
        return appDatabase.questionnaireDao();
    }

    @Provides
    public TaskDao provideTaskDao(AppDatabase appDatabase) {
        return appDatabase.taskDao();
    }

    @Provides
    public JournalDao provideJournalDao(AppDatabase appDatabase) {
        return appDatabase.journalDao();
    }

    @Provides
    public RiskClassificationDao provideClassificationDao(AppDatabase appDatabase) {
        return appDatabase.riskClassificationDao();
    }

    @Provides
    public OnboardingProfileDao provideOnboardingDao(AppDatabase appDatabase) {
        return appDatabase.onboardingProfileDao();
    }

    @Provides
    public DailyResetSessionDao provideDailyResetDao(AppDatabase appDatabase) {
        return appDatabase.dailyResetSessionDao();
    }

    @Provides
    @Singleton
    public UsageRepository provideUsageRepository(@ApplicationContext Context context) {
        return new UsageRepository(context);
    }

    @Provides
    @Singleton
    public OnboardingRepository provideOnboardingRepository(@ApplicationContext Context context) {
        return new OnboardingRepository(context);
    }

    @Provides
    @Singleton
    public SettingsRepository provideSettingsRepository(@ApplicationContext Context context) {
        return new SettingsRepository(context);
    }

    @Provides
    @Singleton
    public ClassificationRepository provideClassificationRepository(RiskClassificationDao classificationDao) {
        return new ClassificationRepository(classificationDao);
    }

    @Provides
    @Singleton
    public DailyResetRepository provideDailyResetRepository(@ApplicationContext Context context) {
        return new DailyResetRepository(context);
    }
}
