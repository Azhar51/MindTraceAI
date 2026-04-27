package com.mindtrace.ai.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.AdaptiveCrisisResponse;
import com.mindtrace.ai.ai.ExerciseEngine;
import com.mindtrace.ai.database.entity.SafetyPlan;
import com.mindtrace.ai.database.entity.TrustedContact;
import com.mindtrace.ai.viewmodel.CrisisViewModel;

import java.util.List;
import java.util.Locale;

/**
 * Premium immersive crisis support activity.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Deep navy gradient background with radial glow</li>
 *   <li>Breathing heart animation (subtle scale pulse)</li>
 *   <li>Time-in-support counter ("You've been here for 4 min")</li>
 *   <li>Enhanced distress slider with emoji face + large number</li>
 *   <li>Exercise picker dialogs → real animated activities</li>
 *   <li>Trusted contact call/SMS with pre-composed crisis message</li>
 *   <li>Safety plan view/edit</li>
 *   <li>Resolution method tracking for analytics</li>
 * </ul>
 */
public class CrisisActivity extends AppCompatActivity {

    private CrisisViewModel crisisViewModel;
    private Slider sliderDistress;
    private TextView tvDistressEmoji, tvDistressValue, tvTimeHere, tvHeroEmoji;
    private int initialDistress = 5;
    private String lastExerciseUsed = null;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long openedAtMs;

    // Distress emoji mapping (1-10)
    private static final String[] DISTRESS_EMOJIS = {
            "😊", "🙂", "😌", "😐", "😕", "😟", "😰", "😨", "😱", "🆘"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(0x000D1B2A);

        setContentView(R.layout.activity_crisis);

        // Crash-safe crisis flag (7.F.5)
        getSharedPreferences("mindtrace_crisis", MODE_PRIVATE)
                .edit().putBoolean("active_crisis", true).apply();

        crisisViewModel = new ViewModelProvider(this).get(CrisisViewModel.class);
        openedAtMs = System.currentTimeMillis();

        bindViews();
        setupDistressSlider();
        setupBreathing();
        setupGrounding();
        setupTrustedContacts();
        setupSafetyPlan();
        setupResolution();
        startHeartAnimation();
        startTimeCounter();
        loadAdaptiveProfile();

        // ── Premium enhancements ──
        startContinuousParticles();
        animateSectionEntry();
        addDistressGradientBar();
    }

    /**
     * Continuous calming particles that loop until activity is destroyed.
     */
    private void startContinuousParticles() {
        spawnCalmingParticles();
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    spawnCalmingParticles();
                    timerHandler.postDelayed(this, 6000);
                }
            }
        }, 6000);
    }

    /**
     * Soft calming particles drifting upward for immersive atmosphere.
     */
    private void spawnCalmingParticles() {
        android.widget.FrameLayout root = (android.widget.FrameLayout) findViewById(android.R.id.content);
        if (root == null) return;

        java.util.Random rnd = new java.util.Random();
        int[] colors = {0x205C9DFF, 0x184ADE80, 0x18FFB74D, 0x10FFFFFF};

        for (int i = 0; i < 6; i++) {
            android.view.View particle = new android.view.View(this);
            int size = (int) ((3 + rnd.nextInt(4)) * getResources().getDisplayMetrics().density);
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
            particle.setY(screenH + rnd.nextInt(50));
            particle.setAlpha(0f);
            root.addView(particle);

            long duration = 4000 + rnd.nextInt(4000);
            long delay = rnd.nextInt(2000);

            particle.animate()
                    .translationYBy(-(screenH + 100))
                    .alpha(0.5f)
                    .setDuration(duration)
                    .setStartDelay(delay)
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .withEndAction(() -> root.removeView(particle))
                    .start();
        }
    }

    /**
     * Stagger-animate the main content sections.
     */
    private void animateSectionEntry() {
        android.view.View scrollContent = ((android.widget.ScrollView) findViewById(android.R.id.content)
                .getRootView().findViewById(android.R.id.content)).getChildAt(0);
        if (scrollContent instanceof android.widget.ScrollView) {
            android.view.View linearLayout = ((android.widget.ScrollView) scrollContent).getChildAt(0);
            if (linearLayout instanceof android.view.ViewGroup) {
                UiMotion.staggerChildren((android.view.ViewGroup) linearLayout, 100, true);
            }
        }
    }

    private void bindViews() {
        sliderDistress = findViewById(R.id.slider_distress);
        tvDistressEmoji = findViewById(R.id.tv_distress_emoji);
        tvDistressValue = findViewById(R.id.tv_distress_value);
        tvTimeHere = findViewById(R.id.tv_time_here);
        tvHeroEmoji = findViewById(R.id.tv_hero_emoji);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HERO ANIMATION — Subtle breathing pulse on heart emoji
    // ═══════════════════════════════════════════════════════════════════

    private void startHeartAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(tvHeroEmoji, "scaleX", 1.0f, 1.15f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(tvHeroEmoji, "scaleY", 1.0f, 1.15f, 1.0f);
        scaleX.setDuration(3000);
        scaleY.setDuration(3000);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleX.start();
        scaleY.start();
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIME COUNTER — Validates coping time
    // ═══════════════════════════════════════════════════════════════════

    private void startTimeCounter() {
        timerHandler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = (System.currentTimeMillis() - openedAtMs) / 1000;
                int mins = (int) (elapsed / 60);
                if (mins == 0) {
                    tvTimeHere.setText("You've been here for " + elapsed + " sec");
                } else {
                    tvTimeHere.setText("You've been here for " + mins + " min — that takes strength");
                }
                timerHandler.postDelayed(this, 1000);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISTRESS SLIDER — Emoji face + color change
    // ═══════════════════════════════════════════════════════════════════

    private void setupDistressSlider() {
        sliderDistress.addOnChangeListener((slider, value, fromUser) -> {
            int level = (int) value;
            initialDistress = level;

            // Update emoji with spring bounce
            String newEmoji = DISTRESS_EMOJIS[Math.min(level - 1, 9)];
            tvDistressEmoji.setText(newEmoji);
            tvDistressEmoji.animate()
                    .scaleX(1.3f).scaleY(1.3f)
                    .setDuration(100)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> tvDistressEmoji.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(180)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                            .start())
                    .start();

            // Update number
            tvDistressValue.setText(String.valueOf(level));

            // Color shift: green → red based on level
            float ratio = (level - 1) / 9.0f;
            int r = (int) (76 + (244 - 76) * ratio);
            int g = (int) (175 + (67 - 175) * ratio);
            int b = (int) (80 + (54 - 80) * ratio);
            int color = 0xFF000000 | (r << 16) | (g << 8) | b;
            tvDistressValue.setTextColor(color);

            // Haptic on each step
            if (fromUser) UiMotion.hapticClick(slider);

            // Update gradient bar if present
            if (distressBar != null) {
                distressBar.setProgress(ratio);
            }
        });
    }

    // ── Distress gradient bar ──
    private com.mindtrace.ai.ui.components.GradientProgressBar distressBar;

    /**
     * Programmatically inject a GradientProgressBar below the distress slider
     * showing green→yellow→red gradient.
     */
    private void addDistressGradientBar() {
        android.view.ViewParent parent = sliderDistress.getParent();
        if (parent instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) parent;
            distressBar = new com.mindtrace.ai.ui.components.GradientProgressBar(this);
            int height = (int) (6 * getResources().getDisplayMetrics().density);
            int margin = (int) (8 * getResources().getDisplayMetrics().density);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, height);
            lp.topMargin = margin;
            lp.leftMargin = margin;
            lp.rightMargin = margin;
            distressBar.setLayoutParams(lp);
            distressBar.setGradientColors(new int[]{0xFF22C55E, 0xFFF59E0B, 0xFFFF6B6B, 0xFFEF4444});
            distressBar.setTrackColor(0xFF1A2540);

            // Find slider index and insert after it
            int idx = group.indexOfChild(sliderDistress);
            if (idx >= 0 && idx < group.getChildCount()) {
                group.addView(distressBar, idx + 1);
            } else {
                group.addView(distressBar);
            }

            // Set initial progress
            float initialRatio = (initialDistress - 1) / 9.0f;
            distressBar.setProgressImmediate(initialRatio);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BREATHING EXERCISE
    // ═══════════════════════════════════════════════════════════════════

    private void setupBreathing() {
        android.view.View btnBreathing = findViewById(R.id.btn_breathing);
        UiMotion.attachPressAnimation(btnBreathing);
        btnBreathing.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            List<ExerciseEngine.BreathingExercise> exercises = ExerciseEngine.getAllBreathingExercises();
            String[] names = new String[exercises.size()];
            for (int i = 0; i < exercises.size(); i++) {
                names[i] = exercises.get(i).name + " (" + exercises.get(i).getDurationMinutes() + " min)";
            }

            new AlertDialog.Builder(this)
                    .setTitle("Choose a breathing exercise")
                    .setItems(names, (dialog, which) -> {
                        lastExerciseUsed = "breathing_exercise";
                        Intent intent = new Intent(this, BreathingExerciseActivity.class);
                        intent.putExtra(BreathingExerciseActivity.EXTRA_EXERCISE_INDEX, which);
                        intent.putExtra(BreathingExerciseActivity.EXTRA_PRE_DISTRESS, initialDistress);
                        startActivity(intent);
                    })
                    .show();
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // GROUNDING EXERCISE
    // ═══════════════════════════════════════════════════════════════════

    private void setupGrounding() {
        android.view.View btnGrounding = findViewById(R.id.btn_grounding);
        UiMotion.attachPressAnimation(btnGrounding);
        btnGrounding.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            List<ExerciseEngine.GroundingExercise> exercises = ExerciseEngine.getAllGroundingExercises();
            String[] names = new String[exercises.size()];
            for (int i = 0; i < exercises.size(); i++) {
                names[i] = exercises.get(i).name + " (" + exercises.get(i).getStepCount() + " steps)";
            }

            new AlertDialog.Builder(this)
                    .setTitle("Choose a grounding exercise")
                    .setItems(names, (dialog, which) -> {
                        lastExerciseUsed = "grounding_exercise";
                        Intent intent = new Intent(this, GroundingExerciseActivity.class);
                        intent.putExtra(GroundingExerciseActivity.EXTRA_EXERCISE_INDEX, which);
                        intent.putExtra(GroundingExerciseActivity.EXTRA_PRE_DISTRESS, initialDistress);
                        startActivity(intent);
                    })
                    .show();
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRUSTED CONTACTS
    // ═══════════════════════════════════════════════════════════════════

    private void setupTrustedContacts() {
        findViewById(R.id.btn_contact_trusted).setOnClickListener(v -> {
            crisisViewModel.loadTrustedContacts(contacts -> {
                runOnUiThread(() -> {
                    if (contacts == null || contacts.isEmpty()) {
                        Toast.makeText(this,
                                "No trusted contacts saved yet. Add them in Settings or Safety Plan.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    String[] names = new String[contacts.size()];
                    for (int i = 0; i < contacts.size(); i++) {
                        TrustedContact c = contacts.get(i);
                        names[i] = c.name + " (" + c.relationship + ")";
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Reach out to someone")
                            .setItems(names, (dialog, which) -> {
                                TrustedContact selected = contacts.get(which);
                                showContactOptions(selected);
                            })
                            .show();
                });
            });
        });
    }

    private void showContactOptions(TrustedContact contact) {
        String[] options = {"📞 Call " + contact.name, "💬 Send a text"};
        new AlertDialog.Builder(this)
                .setTitle(contact.name)
                .setItems(options, (dialog, which) -> {
                    lastExerciseUsed = "contacted_friend";
                    if (which == 0) {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL);
                        callIntent.setData(Uri.parse("tel:" + contact.phone));
                        startActivity(callIntent);
                    } else {
                        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
                        smsIntent.setData(Uri.parse("smsto:" + contact.phone));
                        smsIntent.putExtra("sms_body", contact.getCrisisSmsText());
                        startActivity(smsIntent);
                    }
                })
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SAFETY PLAN
    // ═══════════════════════════════════════════════════════════════════

    private void setupSafetyPlan() {
        findViewById(R.id.btn_view_safety_plan).setOnClickListener(v -> {
            crisisViewModel.getSafetyPlan().observe(this, plan -> {
                if (plan != null && plan.hasContent()) {
                    new AlertDialog.Builder(this)
                            .setTitle("🛡️ Your Safety Plan")
                            .setMessage(plan.toShareableText())
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Edit", (d, w) ->
                                    startActivity(new Intent(this, SafetyPlanActivity.class)))
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("No safety plan yet")
                            .setMessage("Creating a safety plan is an important step. Set one up now?")
                            .setPositiveButton("Create Now", (d, w) ->
                                    startActivity(new Intent(this, SafetyPlanActivity.class)))
                            .setNegativeButton("Later", null)
                            .show();
                }
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESOLUTION
    // ═══════════════════════════════════════════════════════════════════

    private void setupResolution() {
        android.view.View btnSafe = findViewById(R.id.btn_safe_now);
        UiMotion.attachPressAnimation(btnSafe);
        UiMotion.pulseGlow(btnSafe); // Green CTA glow

        btnSafe.setOnClickListener(v -> {
            int postDistress = (int) sliderDistress.getValue();
            String method = lastExerciseUsed != null ? lastExerciseUsed : "self_resolved";

            crisisViewModel.resolveCrisis(method, null, postDistress);

            // ── Premium celebration ──
            UiMotion.hapticHeavy(v);
            UiMotion.confettiBurst(v, 14);

            Toast.makeText(this,
                    "💚 That's great. We're glad you're feeling safer.",
                    Toast.LENGTH_SHORT).show();

            // Delay finish for confetti to be visible
            v.postDelayed(this::finish, 800);
        });

        android.view.View tvBack = findViewById(R.id.tv_back);
        if (tvBack != null) tvBack.setOnClickListener(v -> finish());
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADAPTIVE PERSONALIZATION
    // ═══════════════════════════════════════════════════════════════════

    private void loadAdaptiveProfile() {
        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            try {
                AdaptiveCrisisResponse adaptive = new AdaptiveCrisisResponse(this);
                AdaptiveCrisisResponse.CrisisProfile profile = adaptive.buildProfile();

                com.mindtrace.ai.util.AppExecutors.mainThread().execute(() -> {
                    if (!isFinishing()) {
                        applyProfile(profile);
                    }
                });
            } catch (Exception e) {
                Log.d("CrisisActivity", "Adaptive profile not available yet");
            }
        });
    }

    private void applyProfile(AdaptiveCrisisResponse.CrisisProfile profile) {
        // Personalized message
        TextView tvMessage = findViewById(R.id.tv_crisis_message);
        if (profile.personalizedMessage != null && !profile.personalizedMessage.isEmpty()) {
            tvMessage.setText(profile.personalizedMessage);
        }

        // Nighttime adjustments
        if (profile.isNightTimeProne) {
            tvHeroEmoji.setText("🌙");
        }

        // Recommended exercise hint
        if (profile.recommendedExercise != null) {
            MaterialButton btnBreathing = findViewById(R.id.btn_breathing);
            if (profile.breathingMoreEffective) {
                btnBreathing.setText("🌬️ " + profile.recommendedExercise + " (recommended)");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void finish() {
        // Clear crash-safe crisis flag on normal exit
        getSharedPreferences("mindtrace_crisis", MODE_PRIVATE)
                .edit().putBoolean("active_crisis", false)
                .putInt("cssrs_tier", 0).apply();
        super.finish();
    }
}
