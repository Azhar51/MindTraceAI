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
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mindtrace.ai.R;
import com.mindtrace.ai.databinding.ActivitySplashBinding;
import com.mindtrace.ai.repository.OnboardingRepository;

import java.util.List;

/**
 * Premium Splash Screen — Blueprint §2E1.1
 *
 * <p>Radial gradient background with staggered logo, tagline, and loading dot
 * animations. Auto-routes after 2 seconds to the appropriate destination.</p>
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Transparent status bar
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        // Animate splash elements
        animateSplash();

        // Auto-route after a short 500ms delay to make app feel fast
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthAndPermissions, 500);
    }

    /**
     * Staggered splash animation per blueprint + premium particles.
     */
    private void animateSplash() {
        // Logo glow: fade in 0→0.6 over 600ms + slow rotation
        ObjectAnimator glowFade = ObjectAnimator.ofFloat(binding.logoGlow, "alpha", 0f, 0.6f);
        glowFade.setDuration(600);
        glowFade.setInterpolator(new DecelerateInterpolator());

        ObjectAnimator glowRotate = ObjectAnimator.ofFloat(binding.logoGlow, "rotation", 0f, 360f);
        glowRotate.setDuration(12000);
        glowRotate.setRepeatCount(ValueAnimator.INFINITE);
        glowRotate.setInterpolator(new android.view.animation.LinearInterpolator());
        glowRotate.start();

        // Logo container: fade 0→1 + scale 0.85→1.0 over 500ms
        ObjectAnimator logoFade = ObjectAnimator.ofFloat(binding.logoContainer, "alpha", 0f, 1f);
        logoFade.setDuration(500);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(binding.logoContainer, "scaleX", 0.85f, 1.0f);
        logoScaleX.setDuration(500);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(binding.logoContainer, "scaleY", 0.85f, 1.0f);
        logoScaleY.setDuration(500);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(logoFade, logoScaleX, logoScaleY, glowFade);
        logoSet.setInterpolator(new android.view.animation.OvershootInterpolator(1.3f));
        logoSet.start();

        // App name: fade in + slide up at 200ms delay
        binding.tvAppName.setTranslationY(12f);
        binding.tvAppName.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(200)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Tagline: fade in at 800ms delay
        binding.tvTagline.setTranslationY(8f);
        binding.tvTagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(800)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Loading dot: pulse opacity 0.3→0.8→0.3 in 1.5s loop
        ObjectAnimator dotPulse = ObjectAnimator.ofFloat(binding.loadingDot, "alpha", 0.3f, 0.8f, 0.3f);
        dotPulse.setDuration(1500);
        dotPulse.setRepeatCount(ValueAnimator.INFINITE);
        dotPulse.start();

        // Premium: floating particles
        spawnFloatingParticles();
    }

    /**
     * Spawns soft ambient particles that drift upward across the splash screen.
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
        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            // Crash-safe crisis mode (7.F.5)
            boolean crisisActive = getSharedPreferences("mindtrace_crisis", MODE_PRIVATE)
                    .getBoolean("active_crisis", false);
            int savedCsrrsTier = getSharedPreferences("mindtrace_crisis", MODE_PRIVATE)
                    .getInt("cssrs_tier", 0);

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
     * Check if the <b>essential</b> permissions needed for core functionality
     * have been granted.
     */
    private boolean hasAllRequiredPermissions() {
        if (!hasUsageStatsPermission()) return false;
        if (!hasNotificationPermission()) return false;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
