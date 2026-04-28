package com.mindtrace.ai.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mindtrace.ai.database.entity.UsageSession;
import com.mindtrace.ai.repository.UsageRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel for hourly usage review — decouples data loading and business logic
 * from {@link com.mindtrace.ai.ui.panel.HourlyReviewActivity}.
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Load and aggregate today's usage sessions into hourly buckets</li>
 *   <li>Classify each hour as productive, passive, or fragmented</li>
 *   <li>Expose lifecycle-safe {@link LiveData} to the Activity</li>
 * </ul>
 *
 * <p>Phase 5 refactor: business logic extracted from HourlyReviewActivity to
 * survive configuration changes and follow MVVM architecture.</p>
 */
public class UsageViewModel extends AndroidViewModel {

    private static final String TAG = "UsageViewModel";
    private final ExecutorService executor = com.mindtrace.ai.util.AppExecutors.diskIO();
    private final UsageRepository usageRepository;

    private final MutableLiveData<List<HourBucket>> hourlyData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public UsageViewModel(@NonNull Application application) {
        super(application);
        usageRepository = new UsageRepository(application);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /** Observable hourly usage data, sorted latest-first. */
    @NonNull
    public LiveData<List<HourBucket>> getHourlyData() {
        return hourlyData;
    }

    /** Observable loading state. */
    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Load today's usage sessions and aggregate them into hourly buckets.
     * Safe to call multiple times — previous work is superseded.
     */
    public void loadTodayHourlyData() {
        isLoading.setValue(true);
        executor.execute(() -> {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long dayStart = cal.getTimeInMillis();

            List<UsageSession> sessions = usageRepository.getUsageSessionsForDateSync(dayStart);
            if (sessions == null) sessions = new ArrayList<>();

            List<HourBucket> buckets = aggregateHours(dayStart, sessions);
            Collections.reverse(buckets); // latest first

            hourlyData.postValue(buckets);
            isLoading.postValue(false);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // AGGREGATION LOGIC (extracted from HourlyReviewActivity)
    // ═══════════════════════════════════════════════════════════════════

    @NonNull
    private List<HourBucket> aggregateHours(long dayStart, @NonNull List<UsageSession> sessions) {
        List<HourBucket> result = new ArrayList<>();

        for (int h = 0; h < 24; h++) {
            long hourStart = dayStart + h * 3600000L;
            long hourEnd = hourStart + 3600000L;

            long passiveMs = 0;
            long productiveMs = 0;
            Map<String, Long> appTimes = new HashMap<>();

            for (UsageSession s : sessions) {
                long overlapStart = Math.max(s.sessionStart, hourStart);
                long overlapEnd = Math.min(s.sessionEnd, hourEnd);
                if (overlapEnd > overlapStart) {
                    long duration = overlapEnd - overlapStart;
                    appTimes.put(s.packageName,
                            appTimes.getOrDefault(s.packageName, 0L) + duration);
                    if ("passive".equals(s.sessionType)) {
                        passiveMs += duration;
                    } else {
                        productiveMs += duration;
                    }
                }
            }

            if (!appTimes.isEmpty()) {
                // Sort apps by usage descending
                List<Map.Entry<String, Long>> sortedApps = new ArrayList<>(appTimes.entrySet());
                sortedApps.sort((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()));

                HourBucket bucket = new HourBucket(h, productiveMs, passiveMs, sortedApps);
                result.add(bucket);
            }
        }

        return result;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Shared executor — do not shutdown
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA MODEL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Immutable data class representing a single hour's aggregated usage.
     *
     * <p>Contains all the data the UI needs to render one hourly review card,
     * including classification logic that was previously embedded in the Activity.</p>
     */
    public static final class HourBucket {
        public final int hour;
        public final long productiveMs;
        public final long passiveMs;
        public final List<Map.Entry<String, Long>> appTimes;

        public HourBucket(int hour, long productiveMs, long passiveMs,
                          @NonNull List<Map.Entry<String, Long>> appTimes) {
            this.hour = hour;
            this.productiveMs = productiveMs;
            this.passiveMs = passiveMs;
            this.appTimes = appTimes;
        }

        /** Total screen time in this hour slot. */
        public long getTotalMs() {
            return productiveMs + passiveMs;
        }

        /** Top package name by usage duration. */
        @NonNull
        public String getTopPackage() {
            if (appTimes == null || appTimes.isEmpty()) return "";
            return appTimes.get(0).getKey();
        }

        /**
         * Classify this hour as productive, passive, or fragmented.
         * @return one of {@link #TYPE_PASSIVE}, {@link #TYPE_PRODUCTIVE}, {@link #TYPE_FRAGMENTED}
         */
        public int getClassification() {
            if (passiveMs > productiveMs * 1.5) return TYPE_PASSIVE;
            if (productiveMs > passiveMs * 1.5) return TYPE_PRODUCTIVE;
            return TYPE_FRAGMENTED;
        }

        /**
         * Generate a human-readable insight string for this hour.
         * @param topAppLabel resolved human-readable app name
         */
        @NonNull
        public String getInsight(@NonNull String topAppLabel) {
            long totalMins = getTotalMs() / 60000L;
            switch (getClassification()) {
                case TYPE_PASSIVE:
                    if (totalMins > 45) return "Extreme Dopamine Binge on " + topAppLabel;
                    return "Mindless dopamine loop via " + topAppLabel;
                case TYPE_PRODUCTIVE:
                    if (totalMins > 45) return "Hyper-focus mode on " + topAppLabel;
                    return "Deep focus block on " + topAppLabel;
                default:
                    return "Fragmented attention span between tasks";
            }
        }

        public static final int TYPE_PASSIVE = 0;
        public static final int TYPE_PRODUCTIVE = 1;
        public static final int TYPE_FRAGMENTED = 2;
    }
}
