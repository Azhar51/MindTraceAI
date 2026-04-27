package com.mindtrace.ai.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.CrisisEvent;
import com.mindtrace.ai.database.entity.JournalEntry;
import com.mindtrace.ai.database.entity.RiskClassification;
import com.mindtrace.ai.database.entity.WeeklyAssessment;
import com.mindtrace.ai.database.entity.SuicideRiskEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Data Export Repository — exports user data in structured formats for
 * privacy compliance, backup, and clinician sharing.
 *
 * <h3>Export Formats:</h3>
 * <ul>
 *   <li><b>Full JSON Export</b> — All user data (GDPR/privacy compliant)</li>
 *   <li><b>Clinician Export</b> — Structured clinical summary (risk timeline, exercise effectiveness)</li>
 *   <li><b>Data Deletion</b> — Complete data wipe with confirmation</li>
 * </ul>
 */
public class DataExportRepository {

    private static final String TAG = "DataExportRepository";
    private final AppDatabase db;
    private final Context context;

    public DataExportRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
    }

    // ═══════════════════════════════════════════════════════════════════
    // FULL JSON EXPORT (GDPR Compliant)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Export all user data as a JSON file.
     *
     * @return File object pointing to the exported JSON, or null on failure
     */
    public File exportAllDataAsJson() {
        try {
            JSONObject root = new JSONObject();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

            root.put("export_version", "1.0");
            root.put("exported_at", sdf.format(new Date()));
            root.put("app_name", "MindTrace AI");

            // Journal entries
            JSONArray journals = new JSONArray();
            List<JournalEntry> entries = db.journalDao().getAllEntriesSync();
            if (entries != null) {
                for (JournalEntry e : entries) {
                    JSONObject j = new JSONObject();
                    j.put("id", e.id);
                    j.put("content", e.content);
                    j.put("mood_at_writing", e.moodAtWriting);
                    j.put("sentiment_score", e.sentimentScore);
                    j.put("sentiment_label", e.aiSentiment);
                    j.put("type", e.entryType);
                    j.put("word_count", e.wordCount);
                    j.put("timestamp", e.timestamp);
                    journals.put(j);
                }
            }
            root.put("journal_entries", journals);

            // Weekly assessments
            JSONArray assessments = new JSONArray();
            List<WeeklyAssessment> weeklyList = db.weeklyAssessmentDao().getHistoricalAssessments(52);
            if (weeklyList != null) {
                for (WeeklyAssessment a : weeklyList) {
                    JSONObject wa = new JSONObject();
                    wa.put("id", a.id);
                    wa.put("purpose_score", a.purposeScore);
                    wa.put("timestamp", a.timestamp);
                    assessments.put(wa);
                }
            }
            root.put("weekly_assessments", assessments);

            // Crisis events
            JSONArray crisisEvents = new JSONArray();
            List<CrisisEvent> events = db.crisisEventDao().getAllEventsSync();
            if (events != null) {
                for (CrisisEvent ce : events) {
                    JSONObject c = new JSONObject();
                    c.put("id", ce.id);
                    c.put("level", ce.crisisLevel);
                    c.put("status", ce.status);
                    c.put("trigger_signals", ce.triggerSignalsJson);
                    c.put("resolution_method", ce.resolutionMethod);
                    c.put("resolved_at", ce.resolvedAt);
                    c.put("timestamp", ce.timestamp);
                    crisisEvents.put(c);
                }
            }
            root.put("crisis_events", crisisEvents);

            // Suicide risk events
            JSONArray riskEvents = new JSONArray();
            try {
                List<SuicideRiskEvent> sre = db.suicideRiskEventDao().getRecentSync(100);
                if (sre != null) {
                    for (SuicideRiskEvent r : sre) {
                        JSONObject re = new JSONObject();
                        re.put("id", r.id);
                        re.put("cssrs_tier", r.csrrsTier);
                        re.put("severity_label", r.severityLabel);
                        re.put("source", r.source);
                        re.put("lockdown_triggered", r.lockdownTriggered);
                        re.put("timestamp", r.timestamp);
                        riskEvents.put(re);
                    }
                }
            } catch (Exception ignored) {}
            root.put("suicide_risk_events", riskEvents);

            // Write to file
            File exportDir = new File(context.getFilesDir(), "exports");
            if (!exportDir.exists()) exportDir.mkdirs();
            String filename = "mindtrace_export_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
            File exportFile = new File(exportDir, filename);

            FileWriter writer = new FileWriter(exportFile);
            writer.write(root.toString(2));
            writer.flush();
            writer.close();

            Log.d(TAG, "Exported data to: " + exportFile.getAbsolutePath());
            return exportFile;

        } catch (Exception e) {
            Log.e(TAG, "Data export failed", e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLINICIAN EXPORT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a clinician-friendly structured export with risk timeline,
     * exercise effectiveness, and mood trend data.
     */
    public File exportClinicianReport() {
        try {
            JSONObject root = new JSONObject();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

            root.put("report_type", "clinician_summary");
            root.put("generated_at", sdf.format(new Date()));
            root.put("disclaimer", "This data is from a self-report wellness app and should not " +
                    "replace clinical assessment. Use as supplementary information only.");

            // Risk timeline
            JSONArray riskTimeline = new JSONArray();
            try {
                List<SuicideRiskEvent> events = db.suicideRiskEventDao().getRecentSync(50);
                if (events != null) {
                    for (SuicideRiskEvent e : events) {
                        JSONObject entry = new JSONObject();
                        entry.put("date", sdf.format(new Date(e.timestamp)));
                        entry.put("cssrs_tier", e.csrrsTier);
                        entry.put("severity", e.severityLabel);
                        entry.put("lockdown_triggered", e.lockdownTriggered);
                        entry.put("source", e.source);
                        riskTimeline.put(entry);
                    }
                }
            } catch (Exception ignored) {}
            root.put("suicide_risk_timeline", riskTimeline);

            // Exercise effectiveness
            JSONObject exerciseStats = new JSONObject();
            try {
                float avgEffectiveness = db.taskDao().getOverallAverageEffectiveness();
                String mostEffective = db.taskDao().getMostEffectiveCategory();
                exerciseStats.put("average_effectiveness", avgEffectiveness);
                exerciseStats.put("most_effective_category", mostEffective);
            } catch (Exception ignored) {}
            root.put("exercise_effectiveness", exerciseStats);

            // Write file
            File exportDir = new File(context.getFilesDir(), "exports");
            if (!exportDir.exists()) exportDir.mkdirs();
            String filename = "mindtrace_clinician_" + sdf.format(new Date()) + ".json";
            File exportFile = new File(exportDir, filename);

            FileWriter writer = new FileWriter(exportFile);
            writer.write(root.toString(2));
            writer.flush();
            writer.close();

            Log.d(TAG, "Clinician report exported: " + exportFile.getAbsolutePath());
            return exportFile;

        } catch (Exception e) {
            Log.e(TAG, "Clinician export failed", e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DATA DELETION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Delete ALL user data from the database.
     * This action is irreversible.
     *
     * @return true if successful
     */
    public boolean deleteAllData() {
        try {
            db.clearAllTables();

            // Also clear shared preferences
            context.getSharedPreferences("mindtrace_crisis", Context.MODE_PRIVATE)
                    .edit().clear().apply();
            context.getSharedPreferences("mindtrace_daily_settings", Context.MODE_PRIVATE)
                    .edit().clear().apply();
            context.getSharedPreferences("mindtrace_onboarding", Context.MODE_PRIVATE)
                    .edit().clear().apply();

            // Delete export files
            File exportDir = new File(context.getFilesDir(), "exports");
            if (exportDir.exists()) {
                File[] files = exportDir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
            }

            Log.d(TAG, "All user data deleted successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Data deletion failed", e);
            return false;
        }
    }

    /**
     * Get storage statistics for privacy dashboard.
     */
    public StorageStats getStorageStats() {
        StorageStats stats = new StorageStats();
        try {
            stats.taskCount = db.taskDao().getTotalCount();
            stats.journalCount = db.journalDao().getTotalCount();
            stats.crisisEventCount = db.crisisEventDao().getTotalCountSync();
            stats.suicideRiskEventCount = db.suicideRiskEventDao().getTotalCount();

            File dbFile = context.getDatabasePath("mindtrace_db");
            if (dbFile.exists()) {
                stats.databaseSizeKB = dbFile.length() / 1024;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get storage stats", e);
        }
        return stats;
    }

    public static class StorageStats {
        public int taskCount = 0;
        public int journalCount = 0;
        public int crisisEventCount = 0;
        public int suicideRiskEventCount = 0;
        public long databaseSizeKB = 0;
    }
}
