package com.mindtrace.ai.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.TrustedContact;
import com.mindtrace.ai.databinding.ActivityCrisisLockdownBinding;
import com.mindtrace.ai.viewmodel.CrisisViewModel;

/**
 * Crisis Lockdown Activity — full-screen safety mode for C-SSRS ≥4.
 *
 * <h3>Safety Features:</h3>
 * <ul>
 *   <li>Back button disabled for first 60 seconds</li>
 *   <li>Only shows: helpline, emergency contacts, breathing exercise</li>
 *   <li>No dismiss option until 60-second timer completes</li>
 *   <li>Pulsing heart animation for grounding</li>
 *   <li>Auto-logs crisis event with lockdown flag</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> This screen is intentionally restrictive to ensure
 * the user engages with at least one support resource before dismissing.</p>
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class CrisisLockdownActivity extends AppCompatActivity {

    public static final String EXTRA_CSSRS_TIER = "cssrs_tier";
    public static final String EXTRA_SEVERITY_LABEL = "severity_label";

    private static final int LOCKDOWN_SECONDS = 60;

    private ActivityCrisisLockdownBinding binding;
    private CrisisViewModel crisisViewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int secondsRemaining = LOCKDOWN_SECONDS;
    private boolean canDismiss = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full immersive + keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);

        binding = ActivityCrisisLockdownBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        crisisViewModel = new ViewModelProvider(this).get(CrisisViewModel.class);

        setupHelpline();
        setupEmergencyContacts();
        setupBreathing();
        setupDismiss();
        startHeartAnimation();
        startLockdownTimer();

        // Premium enhancements
        startHeartbeatHaptics();
        startContinuousParticles();
        startCountdownArc();

        // Log lockdown event
        int tier = getIntent().getIntExtra(EXTRA_CSSRS_TIER, 4);
        crisisViewModel.logCrisisLockdown(tier);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPLINE
    // ═══════════════════════════════════════════════════════════════════

    private void setupHelpline() {
        UiMotion.attachPressAnimation(binding.btnCall988);
        UiMotion.pulseGlow(binding.btnCall988);
        binding.btnCall988.setOnClickListener(v -> {
            UiMotion.hapticHeavy(v);
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:988"));
            startActivity(callIntent);
        });

        UiMotion.attachPressAnimation(binding.btnText741741);
        binding.btnText741741.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
            smsIntent.setData(Uri.parse("smsto:741741"));
            smsIntent.putExtra("sms_body", "HOME");
            startActivity(smsIntent);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // EMERGENCY CONTACTS
    // ═══════════════════════════════════════════════════════════════════

    private void setupEmergencyContacts() {
        UiMotion.attachPressAnimation(binding.btnEmergencyContacts);
        binding.btnEmergencyContacts.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            crisisViewModel.loadTrustedContacts(contacts -> {
                runOnUiThread(() -> {
                    if (contacts == null || contacts.isEmpty()) {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL);
                        callIntent.setData(Uri.parse("tel:988"));
                        startActivity(callIntent);
                        return;
                    }

                    TrustedContact primary = contacts.get(0);
                    new AlertDialog.Builder(this)
                            .setTitle("Call " + primary.name + "?")
                            .setMessage(primary.relationship + " — " + primary.phone)
                            .setPositiveButton("📞 Call Now", (d, w) -> {
                                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                                callIntent.setData(Uri.parse("tel:" + primary.phone));
                                startActivity(callIntent);
                            })
                            .setNeutralButton("💬 Send Text", (d, w) -> {
                                Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
                                smsIntent.setData(Uri.parse("smsto:" + primary.phone));
                                smsIntent.putExtra("sms_body",
                                        "I'm going through a really difficult time right now and could use your support. Can you call me?");
                                startActivity(smsIntent);
                            })
                            .show();
                });
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // BREATHING
    // ═══════════════════════════════════════════════════════════════════

    private void setupBreathing() {
        UiMotion.attachPressAnimation(binding.btnBreathing);
        binding.btnBreathing.setOnClickListener(v -> {
            UiMotion.hapticClick(v);
            Intent intent = new Intent(this, BreathingExerciseActivity.class);
            intent.putExtra(BreathingExerciseActivity.EXTRA_EXERCISE_INDEX, 0);
            startActivity(intent);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOCKDOWN TIMER — 60 second minimum
    // ═══════════════════════════════════════════════════════════════════

    private void startLockdownTimer() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                secondsRemaining--;

                if (secondsRemaining > 0) {
                    binding.tvDismissTimer.animate().alpha(0f).setDuration(100).withEndAction(() -> {
                        binding.tvDismissTimer.setText("Please stay for " + secondsRemaining + " seconds");
                        binding.tvDismissTimer.animate().alpha(1f).setDuration(200).start();
                    }).start();
                    handler.postDelayed(this, 1000);
                } else {
                    canDismiss = true;
                    binding.tvDismissTimer.setText("Take all the time you need");

                    binding.btnDismiss.setVisibility(android.view.View.VISIBLE);
                    binding.btnDismiss.setEnabled(true);
                    binding.btnDismiss.setTextColor(0xCC66BB6A);
                    binding.btnDismiss.setAlpha(0f);
                    binding.btnDismiss.animate()
                            .alpha(1f)
                            .setDuration(800)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    UiMotion.pulseGlow(binding.btnDismiss);
                }
            }
        });
    }

    private void setupDismiss() {
        binding.btnDismiss.setOnClickListener(v -> {
            if (canDismiss) {
                new AlertDialog.Builder(this)
                        .setTitle("Before you go")
                        .setMessage("Are you in a safer place now?\n\n" +
                                "If you're still struggling, please call 988. " +
                                "There's no shame in asking for help.")
                        .setPositiveButton("💚 I'm feeling safer", (d, w) -> {
                            crisisViewModel.resolveCrisis("lockdown_completed", null, 0);
                            finish();
                        })
                        .setNegativeButton("Stay here", null)
                        .setCancelable(false)
                        .show();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // HEART ANIMATION
    // ═══════════════════════════════════════════════════════════════════

    private void startHeartAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(binding.tvLockdownHeart, "scaleX", 1.0f, 1.2f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(binding.tvLockdownHeart, "scaleY", 1.0f, 1.2f, 1.0f);
        scaleX.setDuration(2500);
        scaleY.setDuration(2500);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleX.start();
        scaleY.start();
    }

    // ═══════════════════════════════════════════════════════════════════
    // BACK BUTTON DISABLED DURING LOCKDOWN
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onBackPressed() {
        if (!canDismiss) {
            Toast.makeText(this,
                    "Please stay — help is available. Call 988.",
                    Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        binding = null;
    }

    // ═════════════════════════════════════════════════════════════════
    // HEARTBEAT HAPTICS — gentle vibration synced with heart animation
    // ═════════════════════════════════════════════════════════════════

    private void startHeartbeatHaptics() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    UiMotion.hapticClick(binding.tvLockdownHeart);
                    handler.postDelayed(this, 2500);
                }
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════
    // CONTINUOUS AMBIENT PARTICLES (loop until destroy)
    // ═════════════════════════════════════════════════════════════════

    private void startContinuousParticles() {
        spawnSubtleParticles();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    spawnSubtleParticles();
                    handler.postDelayed(this, 8000);
                }
            }
        }, 8000);
    }

    private void spawnSubtleParticles() {
        android.widget.FrameLayout root = (android.widget.FrameLayout) findViewById(android.R.id.content);
        if (root == null) return;

        java.util.Random rnd = new java.util.Random();
        int[] colors = {0x10FFFFFF, 0x18F44336, 0x1066BB6A};

        for (int i = 0; i < 4; i++) {
            android.view.View particle = new android.view.View(this);
            int size = (int) ((2 + rnd.nextInt(3)) * getResources().getDisplayMetrics().density);
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

            particle.animate()
                    .translationYBy(-(screenH + 100))
                    .alpha(0.3f)
                    .setDuration(6000 + rnd.nextInt(4000))
                    .setStartDelay(rnd.nextInt(3000))
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .withEndAction(() -> root.removeView(particle))
                    .start();
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // CIRCULAR COUNTDOWN ARC
    // ═════════════════════════════════════════════════════════════════

    private void startCountdownArc() {
        if (binding.countdownArc != null) {
            binding.countdownArc.setColors(0xFF66BB6A, 0x201A2540);
            binding.countdownArc.startCountdown(LOCKDOWN_SECONDS * 1000L);
        }
    }
}
