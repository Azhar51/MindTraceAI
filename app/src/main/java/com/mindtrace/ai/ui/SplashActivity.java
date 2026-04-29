package com.mindtrace.ai.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mindtrace.ai.R;
import com.mindtrace.ai.databinding.ActivitySplashBinding;
import com.mindtrace.ai.repository.OnboardingRepository;

import java.util.List;

/**
 * Premium Splash Screen — red raindrop ripple transition.
 *
 * <p>
 * Flow: System adaptive icon (brain on black) → black screen →
 * red ripple circles expand from center like a raindrop hitting water →
 * red washes away revealing MindTrace AI blue branding.
 * </p>
 */
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Immersive black status bar
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        // Wait for system icon animation to finish, then drop the ripple
        handler.postDelayed(this::startRedRippleDrop, 400);

        // Auto-route after full animation
        handler.postDelayed(this::checkAuthAndPermissions, 3600);
    }

    // ═════════════════════════════════════════════════════════════════
    // RED RAINDROP RIPPLE — concentric rings expand from center
    // ═════════════════════════════════════════════════════════════════

    private void startRedRippleDrop() {
        FrameLayout container = binding.rippleContainer;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int maxDimension = (int) (Math.max(screenW, screenH) * 1.5f);

        // Red color matching the brain image background
        int redColor = 0xFFC0392B;

        // ── Ripple 1: Main drop — solid circle expands fast ──
        spawnRipple(container, maxDimension, redColor, 0.85f,
                /* startDelay */ 0, /* duration */ 1000, /* fadeDelay */ 400);

        // ── Ripple 2: Second ring — slightly delayed, thinner ──
        spawnRipple(container, maxDimension, redColor, 0.50f,
                /* startDelay */ 150, /* duration */ 1100, /* fadeDelay */ 350);

        // ── Ripple 3: Third outer ring — even more delayed ──
        spawnRipple(container, maxDimension, redColor, 0.30f,
                /* startDelay */ 300, /* duration */ 1200, /* fadeDelay */ 300);

        // ── Ripple 4: Subtle outer wash ──
        spawnRipple(container, maxDimension, redColor, 0.15f,
                /* startDelay */ 450, /* duration */ 1300, /* fadeDelay */ 250);

        // ── Blue background starts fading in as ripples wash over ──
        ObjectAnimator blueIn = ObjectAnimator.ofFloat(binding.blueBackground, "alpha", 0f, 1f);
        blueIn.setDuration(1000);
        blueIn.setStartDelay(800);
        blueIn.setInterpolator(new DecelerateInterpolator(1.5f));
        blueIn.start();

        // ── Branding emerges after ripple transition ──
        handler.postDelayed(this::startBrandingReveal, 1400);
    }

    /**
     * Creates a single circular ripple that expands from the center of the screen.
     *
     * @param container  Parent FrameLayout to add the ripple view to
     * @param maxSize    Maximum diameter the ripple will expand to
     * @param color      Base color of the ripple (red)
     * @param startAlpha Initial opacity (outer rings are more transparent)
     * @param startDelay Delay before this ripple starts (stagger effect)
     * @param duration   Total expansion duration
     * @param fadeDelay  When to start fading out (relative to expansion start)
     */
    private void spawnRipple(FrameLayout container, int maxSize, int color,
            float startAlpha, long startDelay, long duration, long fadeDelay) {

        // Create a small circle at center
        int startSize = (int) (40 * getResources().getDisplayMetrics().density);
        View ripple = new View(this);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(startSize, startSize);
        lp.gravity = Gravity.CENTER;
        ripple.setLayoutParams(lp);

        // Circular gradient drawable
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        ripple.setBackground(circle);
        ripple.setAlpha(0f);

        container.addView(ripple);

        float scaleTo = (float) maxSize / startSize;

        // Scale up from center
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ripple, "scaleX", 0.2f, scaleTo);
        scaleX.setDuration(duration);

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ripple, "scaleY", 0.2f, scaleTo);
        scaleY.setDuration(duration);

        // Fade in quickly, then fade out
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(ripple, "alpha", 0f, startAlpha);
        fadeIn.setDuration(200);

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(ripple, "alpha", startAlpha, 0f);
        fadeOut.setDuration(duration - fadeDelay);
        fadeOut.setStartDelay(fadeDelay);

        AnimatorSet rippleAnim = new AnimatorSet();
        rippleAnim.playTogether(scaleX, scaleY, fadeIn, fadeOut);
        rippleAnim.setStartDelay(startDelay);
        rippleAnim.setInterpolator(new AccelerateInterpolator(0.6f));
        rippleAnim.start();
    }

    // ═════════════════════════════════════════════════════════════════
    // BRANDING REVEAL — logo, name, tagline stagger in
    // ═════════════════════════════════════════════════════════════════

    private void startBrandingReveal() {
        // Branding container fades in
        binding.brandingContainer.setAlpha(0f);
        binding.brandingContainer.animate()
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Logo glow
        ObjectAnimator glowFade = ObjectAnimator.ofFloat(binding.logoGlow, "alpha", 0f, 0.6f);
        glowFade.setDuration(600);
        glowFade.setInterpolator(new DecelerateInterpolator());
        glowFade.start();

        ObjectAnimator glowRotate = ObjectAnimator.ofFloat(binding.logoGlow, "rotation", 0f, 360f);
        glowRotate.setDuration(12000);
        glowRotate.setRepeatCount(ValueAnimator.INFINITE);
        glowRotate.setInterpolator(new android.view.animation.LinearInterpolator());
        glowRotate.start();

        // Logo: fade + scale pop
        ObjectAnimator logoFade = ObjectAnimator.ofFloat(binding.logoContainer, "alpha", 0f, 1f);
        logoFade.setDuration(500);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(binding.logoContainer, "scaleX", 0.85f, 1.0f);
        logoScaleX.setDuration(500);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(binding.logoContainer, "scaleY", 0.85f, 1.0f);
        logoScaleY.setDuration(500);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(logoFade, logoScaleX, logoScaleY);
        logoSet.setInterpolator(new OvershootInterpolator(1.1f));
        logoSet.start();

        // App name: fade + slide up
        binding.tvAppName.setTranslationY(10f);
        binding.tvAppName.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(200)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Tagline: fade + slide up
        binding.tvTagline.setTranslationY(8f);
        binding.tvTagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(400)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Loading dot pulse
        ObjectAnimator dotPulse = ObjectAnimator.ofFloat(binding.loadingDot, "alpha", 0.3f, 0.8f, 0.3f);
        dotPulse.setDuration(1500);
        dotPulse.setRepeatCount(ValueAnimator.INFINITE);
        dotPulse.setStartDelay(600);
        dotPulse.start();

        // Floating particles
        spawnFloatingParticles();
    }

    /**
     * Spawns soft ambient particles that drift upward across the splash screen.
     */
    private void spawnFloatingParticles() {
        FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
        if (root == null)
            return;

        java.util.Random rnd = new java.util.Random();
        int[] colors = { 0x307C8FFF, 0x206366F1, 0x204ADE80, 0x18FFFFFF };

        for (int i = 0; i < 8; i++) {
            View particle = new View(this);
            int size = (int) (3 + rnd.nextInt(5)) * (int) getResources().getDisplayMetrics().density;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            particle.setLayoutParams(lp);

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
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

    // ═════════════════════════════════════════════════════════════════
    // AUTH & PERMISSION ROUTING
    // ═════════════════════════════════════════════════════════════════

    private void checkAuthAndPermissions() {
        com.mindtrace.ai.database.AppDatabase db = com.mindtrace.ai.database.AppDatabase.getInstance(this);
        OnboardingRepository onboardingRepository = new OnboardingRepository(this);
        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            boolean crisisActive = getSharedPreferences("mindtrace_crisis", MODE_PRIVATE)
                    .getBoolean("active_crisis", false);
            int savedCsrrsTier = getSharedPreferences("mindtrace_crisis", MODE_PRIVATE)
                    .getInt("cssrs_tier", 0);

            if (!crisisActive) {
                try {
                    com.mindtrace.ai.database.entity.CrisisEvent activeEvent = db.crisisEventDao().getActiveEvent();
                    if (activeEvent != null)
                        crisisActive = true;
                } catch (Exception ignored) {
                }
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

    private boolean hasAllRequiredPermissions() {
        if (!hasUsageStatsPermission())
            return false;
        if (!hasNotificationPermission())
            return false;
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
                    android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean hasAccessibilityPermission() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            String myPackage = getPackageName();
            for (AccessibilityServiceInfo info : services) {
                ComponentName cn = new ComponentName(
                        info.getResolveInfo().serviceInfo.packageName,
                        info.getResolveInfo().serviceInfo.name);
                if (cn.getPackageName().equals(myPackage))
                    return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean hasNotificationListenerPermission() {
        String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty())
            return false;
        return flat.contains(getPackageName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        binding = null;
    }
}
