package com.mindtrace.ai.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mindtrace.ai.R;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.User;
import com.mindtrace.ai.ui.components.PasswordStrengthView;

import java.util.concurrent.Executors;

/**
 * Premium Signup Screen — Blueprint §2E1.3
 *
 * <p>Same glassmorphism design language as Login. Three fields (name, email,
 * password) with gradient CTA button "Begin My Journey".</p>
 */
public class SignupActivity extends AppCompatActivity {
    private TextInputEditText etName, etEmail, etPassword;
    private TextInputLayout tilName, tilEmail, tilPassword;
    private MaterialButton btnSignup;
    private PasswordStrengthView passwordStrength;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Transparent status bar
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        bindViews();
        setupListeners();
        animateEntry();
    }

    private void bindViews() {
        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnSignup = findViewById(R.id.btn_signup);

        tilName = (TextInputLayout) etName.getParent().getParent();
        tilEmail = (TextInputLayout) etEmail.getParent().getParent();
        tilPassword = (TextInputLayout) etPassword.getParent().getParent();

        // Password strength meter
        passwordStrength = findViewById(R.id.password_strength);
    }

    private void setupListeners() {
        btnSignup.setOnClickListener(v -> {
            if (!isLoading) signup();
        });

        // Link to Login
        View btnLinkLogin = findViewById(R.id.btn_link_login);
        if (btnLinkLogin != null) {
            btnLinkLogin.setOnClickListener(v -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        }

        // Real-time password strength evaluation
        if (etPassword != null && passwordStrength != null) {
            etPassword.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    passwordStrength.evaluatePassword(s.toString());
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // CTA pulsing glow animation
        UiMotion.pulseGlow(btnSignup);
    }

    private void animateEntry() {
        View logo = findViewById(R.id.ivLogo);
        if (logo != null) {
            logo.setAlpha(0f);
            logo.setTranslationY(-20f);
            logo.animate().alpha(1f).translationY(0f).setDuration(350)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        View tvTitle = findViewById(R.id.tvSignupTitle);
        if (tvTitle != null) {
            tvTitle.setAlpha(0f);
            tvTitle.animate().alpha(1f).setStartDelay(150).setDuration(300).start();
        }

        View tvSubtitle = findViewById(R.id.tvSignupSubtitle);
        if (tvSubtitle != null) {
            tvSubtitle.setAlpha(0f);
            tvSubtitle.animate().alpha(1f).setStartDelay(250).setDuration(300).start();
        }

        View card = findViewById(R.id.cardSignup);
        if (card != null) {
            card.setAlpha(0f);
            card.setTranslationY(32f);
            card.animate().alpha(1f).translationY(0f)
                    .setStartDelay(300).setDuration(350)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void signup() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String pass = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        // Validation
        boolean valid = true;
        if (name.isEmpty()) {
            tilName.setError("Name is required");
            shakeView(tilName);
            valid = false;
        } else { tilName.setError(null); }

        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            shakeView(tilEmail);
            valid = false;
        } else { tilEmail.setError(null); }

        if (pass.isEmpty()) {
            tilPassword.setError("Password is required");
            shakeView(tilPassword);
            valid = false;
        } else if (pass.length() < 6) {
            tilPassword.setError("At least 6 characters");
            shakeView(tilPassword);
            valid = false;
        } else { tilPassword.setError(null); }

        if (!valid) return;

        // Loading
        setLoading(true);

        User user = new User();
        user.name = name;
        user.email = email;
        user.password = pass;
        user.createdAt = System.currentTimeMillis();

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(this).userDao().insert(user);
            runOnUiThread(() -> {
                setLoading(false);
                Toast.makeText(this, "Welcome to MindTrace! Let's personalize your experience.",
                        Toast.LENGTH_SHORT).show();
                // Start onboarding questionnaire immediately after signup
                Intent onboardingIntent = new Intent(this, QuestionnaireActivity.class);
                startActivity(onboardingIntent);
                finish();
            });
        });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        btnSignup.setEnabled(!loading);
        btnSignup.setText(loading ? "Creating account…" : "Begin My Journey");
    }

    private void shakeView(View view) {
        float dp8 = 8f * getResources().getDisplayMetrics().density;
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX",
                0, dp8, -dp8, dp8, -dp8, dp8, -dp8, 0);
        shake.setDuration(300);
        shake.start();
    }
}
