package com.mindtrace.ai.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mindtrace.ai.repository.SettingsRepository;

public class SettingsViewModel extends AndroidViewModel {
    private final SettingsRepository repository;
    private final MutableLiveData<SettingsRepository.SettingsState> settingsState = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        repository = new SettingsRepository(application);
        loadSettings();
    }

    public LiveData<SettingsRepository.SettingsState> getSettingsState() {
        return settingsState;
    }

    public void loadSettings() {
        settingsState.setValue(repository.getSettingsState());
    }

    public void setIncludeSystemApps(boolean enabled) {
        repository.setIncludeSystemApps(enabled);
        loadSettings();
    }

    public void setTrackingEnabled(boolean enabled) {
        repository.setTrackingEnabled(enabled);
        loadSettings();
    }

    public void setBackgroundSnapshots(boolean enabled) {
        repository.setBackgroundSnapshots(enabled);
        loadSettings();
    }

    public void setPrivacyMode(boolean enabled) {
        repository.setPrivacyMode(enabled);
        loadSettings();
    }

    public void setNotificationMode(String mode) {
        repository.setNotificationMode(mode);
        loadSettings();
    }
}
