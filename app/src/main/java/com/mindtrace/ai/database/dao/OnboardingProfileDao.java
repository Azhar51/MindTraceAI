package com.mindtrace.ai.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mindtrace.ai.database.entity.OnboardingProfile;

@Dao
public interface OnboardingProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(OnboardingProfile profile);

    @Query("SELECT * FROM onboarding_profile WHERE id = 1 LIMIT 1")
    LiveData<OnboardingProfile> getProfile();

    @Query("SELECT * FROM onboarding_profile WHERE id = 1 LIMIT 1")
    OnboardingProfile getProfileSync();
}
