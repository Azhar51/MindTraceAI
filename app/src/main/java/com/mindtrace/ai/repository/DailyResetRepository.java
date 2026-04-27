package com.mindtrace.ai.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.dao.DailyResetSessionDao;
import com.mindtrace.ai.database.entity.DailyResetSession;

import java.util.Calendar;

public class DailyResetRepository {
    private final DailyResetSessionDao dailyResetSessionDao;

    public DailyResetRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context.getApplicationContext());
        dailyResetSessionDao = database.dailyResetSessionDao();
    }

    public LiveData<DailyResetSession> getTodaySession() {
        return dailyResetSessionDao.getSessionForDay(getStartOfTodayMillis());
    }

    public DailyResetSession getTodaySessionSync() {
        return dailyResetSessionDao.getSessionForDaySync(getStartOfTodayMillis());
    }

    public DailyResetSession getOrCreateTodaySession(
            String resetTitle,
            String focusTask,
            String firstAction,
            String warningItem,
            int timerDurationMinutes
    ) {
        long dayTimestamp = getStartOfTodayMillis();
        long now = System.currentTimeMillis();
        DailyResetSession existing = dailyResetSessionDao.getSessionForDaySync(dayTimestamp);
        if (existing != null) {
            boolean changed = false;
            if (isBlank(existing.resetTitle) && !isBlank(resetTitle)) {
                existing.resetTitle = resetTitle;
                changed = true;
            }
            if (isBlank(existing.focusTask) && !isBlank(focusTask)) {
                existing.focusTask = focusTask;
                changed = true;
            }
            if (isBlank(existing.firstAction) && !isBlank(firstAction)) {
                existing.firstAction = firstAction;
                changed = true;
            }
            if (isBlank(existing.warningItem) && !isBlank(warningItem)) {
                existing.warningItem = warningItem;
                changed = true;
            }
            if (existing.timerDurationMinutes <= 0 && timerDurationMinutes > 0) {
                existing.timerDurationMinutes = timerDurationMinutes;
                changed = true;
            }
            if (existing.startedAt <= 0L) {
                existing.startedAt = now;
                changed = true;
            }
            if (changed) {
                dailyResetSessionDao.insertOrReplace(existing);
            }
            return existing;
        }

        DailyResetSession session = new DailyResetSession();
        session.dayTimestamp = dayTimestamp;
        session.createdAt = now;
        session.startedAt = now;
        session.resetTitle = resetTitle;
        session.focusTask = focusTask;
        session.firstAction = firstAction;
        session.warningItem = warningItem;
        session.timerDurationMinutes = timerDurationMinutes;
        session.isCompleted = false;
        session.completedAt = 0L;
        session.readinessLevel = 0;
        session.reflectionNote = null;
        dailyResetSessionDao.insertOrReplace(session);
        return session;
    }

    public DailyResetSession completeTodayReset(String reflectionNote, int readinessLevel) {
        DailyResetSession session = dailyResetSessionDao.getSessionForDaySync(getStartOfTodayMillis());
        long now = System.currentTimeMillis();
        if (session == null) {
            session = new DailyResetSession();
            session.dayTimestamp = getStartOfTodayMillis();
            session.createdAt = now;
            session.startedAt = now;
            session.timerDurationMinutes = 25;
        }

        session.isCompleted = true;
        session.completedAt = now;
        session.readinessLevel = Math.max(0, readinessLevel);
        session.reflectionNote = isBlank(reflectionNote) ? null : reflectionNote.trim();
        dailyResetSessionDao.insertOrReplace(session);
        return session;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long getStartOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
