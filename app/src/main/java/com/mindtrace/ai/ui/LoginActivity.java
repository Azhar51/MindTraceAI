package com.mindtrace.ai.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.User;
import com.mindtrace.ai.databinding.ActivityLoginBinding;


/**
 * Premium Login Screen — Blueprint §2E1.2
 *
 * <p>Dark glassmorphism design with gradient CTA button, loading state,
 * error shake animation, and staggered card entry animation.</p>
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Transparent status bar
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setupListeners();
        animateEntry();
    }

    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            if (!isLoading) {
                UiMotion.hapticClick(v);
                login();
            }
        });
        binding.btnLinkSignup.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));

        // CTA pulsing glow animation
        UiMotion.pulseGlow(binding.btnLogin);
    }

    /**
     * Card entry animation: fade + translateY per blueprint.
     */
    private void animateEntry() {
        // Logo
        binding.ivLogo.setAlpha(0f);
        binding.ivLogo.setTranslationY(-20f);
        binding.ivLogo.animate().alpha(1f).translationY(0f).setDuration(350)
                .setInterpolator(new DecelerateInterpolator()).start();

        // Title + subtitle
        binding.tvLoginTitle.setAlpha(0f);
        binding.tvLoginTitle.animate().alpha(1f).setStartDelay(150).setDuration(300).start();

        binding.tvLoginSubtitle.setAlpha(0f);
        binding.tvLoginSubtitle.animate().alpha(1f).setStartDelay(250).setDuration(300).start();

        // Card
        binding.cardLogin.setAlpha(0f);
        binding.cardLogin.setTranslationY(32f);
        binding.cardLogin.animate().alpha(1f).translationY(0f)
                .setStartDelay(300).setDuration(350)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void login() {
        String email = binding.etEmail.getText() != null
                ? binding.etEmail.getText().toString().trim() : "";
        String pass = binding.etPassword.getText() != null
                ? binding.etPassword.getText().toString().trim() : "";

        // tilEmail/tilPassword are the parent TextInputLayouts
        com.google.android.material.textfield.TextInputLayout tilEmail =
                (com.google.android.material.textfield.TextInputLayout) binding.etEmail.getParent().getParent();
        com.google.android.material.textfield.TextInputLayout tilPassword =
                (com.google.android.material.textfield.TextInputLayout) binding.etPassword.getParent().getParent();

        // Validate
        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            shakeView(tilEmail);
            return;
        }
        tilEmail.setError(null);

        if (pass.isEmpty()) {
            tilPassword.setError("Password is required");
            shakeView(tilPassword);
            return;
        }
        tilPassword.setError(null);

        // Loading state
        setLoading(true);

        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            User user = db.userDao().findByEmail(email);
            boolean authenticated = user != null
                    && com.mindtrace.ai.security.PasswordHasher.verify(pass, user.password);

            // Auto-migrate legacy plaintext passwords to hashed format
            if (authenticated
                    && com.mindtrace.ai.security.PasswordHasher.isLegacyPlaintext(user.password)) {
                user.password = com.mindtrace.ai.security.PasswordHasher.hash(pass);
                db.userDao().update(user);
            }

            runOnUiThread(() -> {
                setLoading(false);
                if (authenticated) {
                    // Success — scale press effect then navigate
                    binding.btnLogin.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
                            .withEndAction(() -> {
                                binding.btnLogin.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                                startActivity(new Intent(this, SplashActivity.class));
                                finish();
                            }).start();
                } else {
                    Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                    shakeView(binding.btnLogin);
                }
            });
        });
    }

    /**
     * Loading state: button text → progress indicator.
     */
    private void setLoading(boolean loading) {
        isLoading = loading;
        if (loading) {
            binding.btnLogin.setText("");
            binding.btnLogin.setIcon(null);
            binding.btnLogin.setEnabled(false);
            // Add a simple text indicator since we can't easily embed a ProgressBar
            binding.btnLogin.setText("Signing in…");
        } else {
            binding.btnLogin.setText("Sign In");
            binding.btnLogin.setEnabled(true);
        }
    }

    /**
     * Error shake animation: translateX ±8dp, 3 cycles, 300ms.
     */
    private void shakeView(View view) {
        float dp8 = 8f * getResources().getDisplayMetrics().density;
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX",
                0, dp8, -dp8, dp8, -dp8, dp8, -dp8, 0);
        shake.setDuration(300);
        shake.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
