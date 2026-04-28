package com.mindtrace.ai.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mindtrace.ai.ai.ContextFeatureExtractor;
import com.mindtrace.ai.ai.DigitalFeatureExtractor;
import com.mindtrace.ai.ai.FeatureVector;
import com.mindtrace.ai.ai.MultiModalClassifier;
import com.mindtrace.ai.ai.PsychFeatureExtractor;
import com.mindtrace.ai.ai.TemporalFeatureExtractor;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.RiskClassification;

/**
 * ClassificationWorker — periodic (every 6 hours) MultiModal AI re-classification.
 *
 * <p>This worker runs the full four-extractor pipeline
 * ({@link DigitalFeatureExtractor}, {@link PsychFeatureExtractor},
 * {@link ContextFeatureExtractor}, {@link TemporalFeatureExtractor}),
 * builds a complete 36-dimensional {@link FeatureVector},
 * and invokes the {@link MultiModalClassifier} to produce a fresh
 * {@link RiskClassification} entity.</p>
 *
 * <h3>Why 6 Hours?</h3>
 * <ul>
 *   <li>More frequent than WellnessSyncWorker (24h) to catch intra-day shifts</li>
 *   <li>Less frequent than EfficacyWorker (2h) to conserve battery</li>
 *   <li>Typical daily runs: 00:00, 06:00, 12:00, 18:00 — covering morning,
 *       midday, evening, and overnight windows</li>
 * </ul>
 *
 * <h3>Data Flow (Part 3C §1–§9):</h3>
 * <pre>
 *   DigitalFeatureExtractor   ─┐  D1–D14 (14 features)
 *   PsychFeatureExtractor     ─┤  P1–P10 (10 features)
 *   ContextFeatureExtractor   ─┤  C1–C6  ( 6 features)
 *   TemporalFeatureExtractor  ─┘  T1–T6  ( 6 features)
 *            │
 *            ▼
 *     FeatureVector (36 floats, [0.0–1.0])
 *            │
 *            ▼
 *     MultiModalClassifier
 *            │
 *            ▼
 *     RiskClassification ─► Room DB
 * </pre>
 *
 * @see com.mindtrace.ai.service.WorkScheduler#WORK_CLASSIFICATION
 * @see com.mindtrace.ai.ai.MultiModalClassifier
 * @see com.mindtrace.ai.ai.TemporalFeatureExtractor
 */
public class ClassificationWorker extends Worker {

    private static final String TAG = "ClassificationWorker";

    public ClassificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting periodic AI classification (36-feature pipeline)...");

        try {
            Context ctx = getApplicationContext();
            AppDatabase db = AppDatabase.getInstance(ctx);

            // ── 1. Extract features from all four modalities ──
            DigitalFeatureExtractor digitalEx = new DigitalFeatureExtractor(ctx);
            PsychFeatureExtractor psychEx = new PsychFeatureExtractor(ctx);
            ContextFeatureExtractor contextEx = new ContextFeatureExtractor(ctx);
            TemporalFeatureExtractor temporalEx = new TemporalFeatureExtractor(ctx);

            FeatureVector.Builder fvBuilder = new FeatureVector.Builder()
                    .source("classification_worker");

            // D1–D14: Digital behaviour + engagement quality
            digitalEx.extractToday(fvBuilder);

            // P1–P10: Psychological state
            psychEx.extractToday(fvBuilder);

            // C1–C6: Contextual factors
            contextEx.extractToday(fvBuilder);

            // T1–T6: Temporal trajectory patterns (3C §9)
            temporalEx.extractToday(fvBuilder);

            FeatureVector fv = fvBuilder.build();

            // ── 2. Reliability gate — skip if insufficient data ──
            if (!fv.isReliable()) {
                Log.d(TAG, "Feature vector not reliable — skipping classification " +
                        "(completeness=" + fv.dataCompleteness +
                        ", nonDefault=" + fv.nonDefaultCount +
                        ", quality=" + fv.getQualityLabel() + ")");
                return Result.success();
            }

            // ── 3. Validate classifier weights at runtime ──
            MultiModalClassifier classifier = new MultiModalClassifier(ctx);
            if (!MultiModalClassifier.validateWeights()) {
                Log.e(TAG, "⚠ Classifier weight validation FAILED — skipping " +
                        "classification to prevent silent scoring drift");
                return Result.success();
            }

            // ── 4. Run classifier ──
            RiskClassification rc = classifier.classifyToday(fv);

            if (rc == null) {
                Log.w(TAG, "Classifier returned null — no classification produced");
                return Result.success();
            }

            // ── 4. Persist ──
            db.riskClassificationDao().insertOrReplace(rc);

            Log.d(TAG, "Classification complete: " + rc.toShortString() +
                    " | mode=" + classifier.getClassificationModeLabel() +
                    " | completeness=" + String.format("%.2f", fv.dataCompleteness) +
                    " | D=" + String.format("%.2f", fv.digitalRiskAvg()) +
                    " | P=" + String.format("%.2f", fv.psychRiskAvg()) +
                    " | C=" + String.format("%.2f", fv.contextRiskAvg()) +
                    " | T=" + String.format("%.2f", fv.temporalRiskAvg()) +
                    " | crisis=" + rc.crisisFlag);

            // ── 5. Crisis escalation — dispatch expedited follow-up ──
            if (rc.crisisFlag) {
                Log.w(TAG, "⚠ Crisis flag set — dispatching expedited follow-up");
                WorkScheduler.triggerImmediateCrisisFollowUp(ctx);
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Classification failed", e);
            return WorkerErrorHandler.handle(
                    getApplicationContext(), TAG, e, getRunAttemptCount());
        }
    }
}
