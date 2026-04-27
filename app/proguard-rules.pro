# ═══════════════════════════════════════════════════════════════════════
# MindTrace AI — ProGuard / R8 Rules
# ═══════════════════════════════════════════════════════════════════════
# These rules protect critical reflection-dependent and serialized classes
# from being stripped or obfuscated in release builds.

# ─── Room Database Entities ───────────────────────────────────────────
# Room uses reflection for entity construction and DAO proxy generation.
# Stripping fields or constructors will crash at runtime.
-keep class com.mindtrace.ai.database.entity.** { *; }
-keep class com.mindtrace.ai.database.dao.** { *; }
-keep class com.mindtrace.ai.database.AppDatabase { *; }

# ─── AI Classifier Pipeline ──────────────────────────────────────────
# FeatureVector uses array-index-based access and JSON serialization.
# MultiModalClassifier uses String-based category switching.
# Obfuscating these breaks the classification pipeline.
-keep class com.mindtrace.ai.ai.FeatureVector { *; }
-keep class com.mindtrace.ai.ai.FeatureVector$Builder { *; }
-keep class com.mindtrace.ai.ai.FeatureVector$RiskDriver { *; }
-keep class com.mindtrace.ai.ai.MultiModalClassifier { *; }
-keep class com.mindtrace.ai.ai.MultiModalClassifier$CrisisLevel { *; }

# ─── Externalized AI Weights Config ──────────────────────────────────
# ClassifierWeightsConfig inner classes use direct field access from
# the classifier. Obfuscating breaks the OTA weight loading pipeline.
-keep class com.mindtrace.ai.ai.ClassifierWeightsConfig { *; }
-keep class com.mindtrace.ai.ai.ClassifierWeightsConfig$* { *; }

# ─── Worker Progress Tracker ─────────────────────────────────────────
# WorkerStatus is exposed via LiveData generics to UI observers.
-keep class com.mindtrace.ai.service.WorkerProgressTracker$WorkerStatus { *; }

# Keep all inner enums used by RiskClassification (Severity)
-keep class com.mindtrace.ai.database.entity.RiskClassification$Severity { *; }

# ─── WorkManager Workers ─────────────────────────────────────────────
# WorkManager instantiates workers via reflection using class names.
-keep class com.mindtrace.ai.service.** extends androidx.work.Worker { *; }
-keep class com.mindtrace.ai.services.** extends androidx.work.Worker { *; }

# ─── Gson Serialization ──────────────────────────────────────────────
# Models serialized/deserialized via Gson require field names to be preserved.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.mindtrace.ai.ai.AiInsightInput { *; }
-keep class com.mindtrace.ai.ai.AiInsightResult { *; }
-keep class com.mindtrace.ai.ai.AiInsightCache { *; }

# ─── OkHttp ───────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ─── MPAndroidChart ───────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }

# ─── Lottie Animations ───────────────────────────────────────────────
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ─── General Android Best Practices ──────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Suppress warnings for missing annotations ───────────────────────
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
