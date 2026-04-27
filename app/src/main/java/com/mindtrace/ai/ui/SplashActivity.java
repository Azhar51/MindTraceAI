package com.mindtrace.ai.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mindtrace.ai.R;
import com.mindtrace.ai.repository.OnboardingRepository;

import java.util.List;

/**
 * Premium Splash Screen — Blueprint §2E1.1
 *
 * <p>Radial gradient background with staggered logo, tagline, and loading dot
 * animations. Auto-routes after 2 seconds to the appropriate destination.</p>
 *
 * <h3>Animation Timeline:</h3>
 * <pre>
 *   0ms   — Logo glow fades in
 *   0ms   — Logo container fade + scale (0.95→1.0) over 400ms
 *   200ms — App name fades in
 *   800ms — Tagline fades in (400ms duration)
 *   0ms   — Loading dot begins pulse loop (0.3→0.8→0.3, 1.5s)
 * </pre>
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Transparent status bar
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        // ── Animate splash elements ──
        animateSplash();

        // ── Auto-route after 2 seconds ──
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthAndPermissions, 2000);
    }

    /**
     * Staggered splash animation per blueprint + premium particles.
     */
    private void animateSplash() {
        View logoGlow = findViewById(R.id.logoGlow);
        View logoContainer = findViewById(R.id.logoContainer);
        View tvAppName = findViewById(R.id.tvAppName);
        View tvTagline = findViewById(R.id.tvTagline);
        View loadingDot = findViewById(R.id.loadingDot);

        // ── Logo glow: fade in 0→0.6 over 600ms + slow rotation ──
        ObjectAnimator glowFade = ObjectAnimator.ofFloat(logoGlow, "alpha", 0f, 0.6f);
        glowFade.setDuration(600);
        glowFade.setInterpolator(new DecelerateInterpolator());

        ObjectAnimator glowRotate = ObjectAnimator.ofFloat(logoGlow, "rotation", 0f, 360f);
        glowRotate.setDuration(12000);
        glowRotate.setRepeatCount(ValueAnimator.INFINITE);
        glowRotate.setInterpolator(new android.view.animation.LinearInterpolator());
        glowRotate.start();

        // ── Logo container: fade 0→1 + scale 0.85→1.0 (bigger spring) over 500ms ──
        ObjectAnimator logoFade = ObjectAnimator.ofFloat(logoContainer, "alpha", 0f, 1f);
        logoFade.setDuration(500);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(logoContainer, "scaleX", 0.85f, 1.0f);
        logoScaleX.setDuration(500);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(logoContainer, "scaleY", 0.85f, 1.0f);
        logoScaleY.setDuration(500);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(logoFade, logoScaleX, logoScaleY, glowFade);
        logoSet.setInterpolator(new android.view.animation.OvershootInterpolator(1.3f));
        logoSet.start();

        // ── App name: fade in + slide up at 200ms delay ──
        tvAppName.setTranslationY(12f);
        tvAppName.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(200)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // ── Tagline: fade in at 800ms delay, 400ms duration ──
        tvTagline.setTranslationY(8f);
        tvTagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(800)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // ── Loading dot: pulse opacity 0.3→0.8→0.3 in 1.5s loop ──
        ObjectAnimator dotPulse = ObjectAnimator.ofFloat(loadingDot, "alpha", 0.3f, 0.8f, 0.3f);
        dotPulse.setDuration(1500);
        dotPulse.setRepeatCount(ValueAnimator.INFINITE);
        dotPulse.start();

        // ── Premium: floating particles ──
        spawnFloatingParticles();
    }

    /**
     * Spawns soft ambient particles that drift upward across the splash screen.
     * Creates a premium, living atmosphere.
     */
    private void spawnFloatingParticles() {
        android.widget.FrameLayout root = (android.widget.FrameLayout) findViewById(android.R.id.content);
        if (root == null) return;

        java.util.Random rnd = new java.util.Random();
        int[] colors = {0x307C8FFF, 0x206366F1, 0x204ADE80, 0x18FFFFFF};

        for (int i = 0; i < 8; i++) {
            View particle = new View(this);
            int size = (int) (3 + rnd.nextInt(5)) * (int) getResources().getDisplayMetrics().density;
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(size, size);
            particle.setLayoutParams(lp);

            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(colors[rnd.nextInt(colors.length)]);
            particle.setBackground(dot);

            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;

            particle.setX(rnd.nextInt(screenW));
            particle.setY(screenH + rnd.nextInt(100));
            particle.setAlpha(0f);
            root.addView(particle);

            long duration = 3000 + rnd.nextInt(3000);
            long delay = rnd.nextInt(1500);

            particle.animate()
                    .translationYBy(-(screenH + 200))
                    .alpha(0.6f)
                    .setDuration(duration)
                    .setStartDelay(delay)
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .withEndAction(() -> root.removeView(particle))
                    .start();
        }
    }

    private void checkAuthAndPermissions() {
        com.mindtrace.ai.database.AppDatabase db = com.mindtrace.ai.database.AppDatabase.getInstance(this);
        OnboardingRepository onboardingRepository = new OnboardingRepository(this);
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            // ── Crash-safe crisis mode (7.F.5) ──
            boolean crisisActive = getSharedPreferences("mindtrace_crisis", MODE_PRIVATE)
                    .getBoolean("active_crisis", false);
            int savedCsrrsTier = getSharedPreferences("mindtrace_crisis", MODE_PRIVATE)
                    .getInt("cssrs_tier", 0);

            // Also check DB for unresolved crisis events
            if (!crisisActive) {
                try {
                    com.mindtrace.ai.database.entity.CrisisEvent activeEvent =
                            db.crisisEventDao().getActiveEvent();
                    if (activeEvent != null) crisisActive = true;
                } catch (Exception ignored) {}
            }

            final boolean shouldLaunchCrisis = crisisActive;
            final boolean shouldLockdown = savedCsrrsTier >= 4;

            com.mindtrace.ai.database.entity.User user = db.userDao().getUser();
            boolean onboardingCompleted = onboardingRepository.isOnboardingCompleted();
            runOnUiThread(() -> {
                // Crisis recovery takes priority over everything
                if (shouldLaunchCrisis) {
                    if (shouldLockdown) {
                        Intent lockdownIntent = new Intent(this, CrisisLockdownActivity.class);
                        lockdownIntent.putExtra(CrisisLockdownActivity.EXTRA_CSSRS_TIER, savedCsrrsTier);
                        startActivity(lockdownIntent);
                    } else {
                        startActivity(new Intent(this, CrisisActivity.class));
                    }
                } else if (user == null) {
                    startActivity(new Intent(this, LoginActivity.class));
                } else if (!onboardingCompleted) {
                    startActivity(new Intent(this, QuestionnaireActivity.class));
                } else if (!hasAllRequiredPermissions()) {
                    startActivity(new Intent(this, PermissionActivity.class));
                } else {
                    startActivity(new Intent(this, MainActivity.class));
                }
                finish();
            });
        });
    }

    /**
     * Check if all core permissions needed for the data collection engine
     * have been granted. Routes to PermissionActivity if any are missing.
     *
     * <p>Permissions checked:</p>
     * <ol>
     *   <li>PACKAGE_USAGE_STATS — screen time tracking (required)</li>
     *   <li>POST_NOTIFICATIONS — reminders and crisis alerts (API 33+)</li>
     *   <li>ACCESSIBILITY_SERVICE — scroll intensity telemetry D13</li>
     *   <li>NOTIFICATION_LISTENER — response latency telemetry D14</li>
     * </ol>
     */
    private boolean hasAllRequiredPermissions() {
        // 1. Usage Stats (mandatory — blocks everything)
        if (!hasUsageStatsPermission()) return false;

        // 2-4. Secondary permissions: if ANY is missing, route to PermissionActivity
        // so the user sees the remaining steps. PermissionActivity will skip
        // already-granted steps automatically.
        if (!hasNotificationPermission()) return false;
        if (!hasAccessibilityPermission()) return false;
        if (!hasNotificationListenerPermission()) return false;

        return true;
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean hasAccessibilityPermission() {
        try {
            AccessibilityManager am = (AccessibilityManager)
                    getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> services =
                    am.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            String myPackage = getPackageName();
            for (AccessibilityServiceInfo info : services) {
                ComponentName cn = new ComponentName(
                        info.getResolveInfo().serviceInfo.packageName,
                        info.getResolveInfo().serviceInfo.name);
                if (cn.getPackageName().equals(myPackage)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean hasNotificationListenerPermission() {
        String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        return flat.contains(getPackageName());
    }
}
