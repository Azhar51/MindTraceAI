package com.mindtrace.ai.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.mindtrace.ai.database.AppDatabase;
import com.mindtrace.ai.database.entity.User;
import com.mindtrace.ai.databinding.ActivitySignupBinding;


/**
 * Premium Signup Screen — Blueprint §2E1.3
 *
 * <p>Same glassmorphism design language as Login. Three fields (name, email,
 * password) with gradient CTA button "Begin My Journey".</p>
 *
 * <p>Migrated to ViewBinding for type-safe view access.</p>
 */
public class SignupActivity extends AppCompatActivity {
    private ActivitySignupBinding binding;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Transparent status bar
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setupListeners();
        animateEntry();
    }

    private void setupListeners() {
        binding.btnSignup.setOnClickListener(v -> {
            if (!isLoading) signup();
        });

        // Link to Login
        binding.btnLinkLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Real-time password strength evaluation
        binding.etPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.passwordStrength.evaluatePassword(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // CTA pulsing glow animation
        UiMotion.pulseGlow(binding.btnSignup);
    }

    private void animateEntry() {
        // Logo
        binding.ivLogo.setAlpha(0f);
        binding.ivLogo.setTranslationY(-20f);
        binding.ivLogo.animate().alpha(1f).translationY(0f).setDuration(350)
                .setInterpolator(new DecelerateInterpolator()).start();

        // Title + subtitle
        binding.tvSignupTitle.setAlpha(0f);
        binding.tvSignupTitle.animate().alpha(1f).setStartDelay(150).setDuration(300).start();

        binding.tvSignupSubtitle.setAlpha(0f);
        binding.tvSignupSubtitle.animate().alpha(1f).setStartDelay(250).setDuration(300).start();

        // Card
        binding.cardSignup.setAlpha(0f);
        binding.cardSignup.setTranslationY(32f);
        binding.cardSignup.animate().alpha(1f).translationY(0f)
                .setStartDelay(300).setDuration(350)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void signup() {
        String name = binding.etName.getText() != null
                ? binding.etName.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null
                ? binding.etEmail.getText().toString().trim() : "";
        String pass = binding.etPassword.getText() != null
                ? binding.etPassword.getText().toString().trim() : "";

        // tilEmail/tilPassword/tilName are the parent TextInputLayouts
        TextInputLayout tilName =
                (TextInputLayout) binding.etName.getParent().getParent();
        TextInputLayout tilEmail =
                (TextInputLayout) binding.etEmail.getParent().getParent();
        TextInputLayout tilPassword =
                (TextInputLayout) binding.etPassword.getParent().getParent();

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
        user.password = com.mindtrace.ai.security.PasswordHasher.hash(pass);
        user.createdAt = System.currentTimeMillis();

        com.mindtrace.ai.util.AppExecutors.diskIO().execute(() -> {
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
        binding.btnSignup.setEnabled(!loading);
        binding.btnSignup.setText(loading ? "Creating account…" : "Begin My Journey");
    }

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
