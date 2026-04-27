package com.mindtrace.ai.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mindtrace.ai.R;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.User;

import java.util.concurrent.Executors;

/**
 * Premium Login Screen — Blueprint §2E1.2
 *
 * <p>Dark glassmorphism design with gradient CTA button, loading state,
 * error shake animation, and staggered card entry animation.</p>
 */
public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etEmail, etPassword;
    private TextInputLayout tilEmail, tilPassword;
    private MaterialButton btnLogin, btnLinkSignup;
    private MaterialCardView cardLogin;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Transparent status bar
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        bindViews();
        setupListeners();
        animateEntry();
    }

    private void bindViews() {
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnLinkSignup = findViewById(R.id.btn_link_signup);

        // Find the TextInputLayouts (parents of EditTexts)
        tilEmail = (TextInputLayout) etEmail.getParent().getParent();
        tilPassword = (TextInputLayout) etPassword.getParent().getParent();
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> {
            if (!isLoading) {
                UiMotion.hapticClick(v);
                login();
            }
        });
        btnLinkSignup.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));

        // CTA pulsing glow animation
        UiMotion.pulseGlow(btnLogin);
    }

    /**
     * Card entry animation: fade + translateY per blueprint.
     */
    private void animateEntry() {
        // Logo
        View logo = findViewById(R.id.ivLogo);
        if (logo != null) {
            logo.setAlpha(0f);
            logo.setTranslationY(-20f);
            logo.animate().alpha(1f).translationY(0f).setDuration(350)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        // Title + subtitle
        View tvTitle = findViewById(R.id.tvLoginTitle);
        View tvSubtitle = findViewById(R.id.tvLoginSubtitle);
        if (tvTitle != null) {
            tvTitle.setAlpha(0f);
            tvTitle.animate().alpha(1f).setStartDelay(150).setDuration(300).start();
        }
        if (tvSubtitle != null) {
            tvSubtitle.setAlpha(0f);
            tvSubtitle.animate().alpha(1f).setStartDelay(250).setDuration(300).start();
        }

        // Card
        cardLogin = findViewById(R.id.cardLogin);
        if (cardLogin != null) {
            cardLogin.setAlpha(0f);
            cardLogin.setTranslationY(32f);
            cardLogin.animate().alpha(1f).translationY(0f)
                    .setStartDelay(300).setDuration(350)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void login() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String pass = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

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

        Executors.newSingleThreadExecutor().execute(() -> {
            User user = AppDatabase.getInstance(this).userDao().login(email, pass);
            runOnUiThread(() -> {
                setLoading(false);
                if (user != null) {
                    // Success — scale press effect then navigate
                    btnLogin.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
                            .withEndAction(() -> {
                                btnLogin.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                                startActivity(new Intent(this, SplashActivity.class));
                                finish();
                            }).start();
                } else {
                    Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                    shakeView(btnLogin);
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
            btnLogin.setText("");
            btnLogin.setIcon(null);
            btnLogin.setEnabled(false);
            // Add a simple text indicator since we can't easily embed a ProgressBar
            btnLogin.setText("Signing in…");
        } else {
            btnLogin.setText("Sign In");
            btnLogin.setEnabled(true);
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
}
