package com.mindtrace.ai.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Full-screen overlay for XP level-up celebrations.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * LevelUpOverlay.show(activity, 5, "Mindful Explorer");
 * }</pre>
 */
public class LevelUpOverlay {

    private static final long DISPLAY_DURATION_MS = 3000;

    /**
     * Show a level-up celebration overlay.
     *
     * @param activity The host activity
     * @param level    The new level number
     * @param title    The level title (e.g., "Mindful Explorer")
     */
    public static void show(@NonNull AppCompatActivity activity, int level, @NonNull String title) {
        Context ctx = activity;

        // Create overlay container
        FrameLayout overlay = new FrameLayout(ctx);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(Color.parseColor("#E60B0F14"));
        overlay.setClickable(true);
        overlay.setElevation(100);

        // Content container
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.gravity = Gravity.CENTER;
        content.setLayoutParams(contentParams);

        float density = ctx.getResources().getDisplayMetrics().density;

        // Star emoji
        TextView starEmoji = new TextView(ctx);
        starEmoji.setText("⭐");
        starEmoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        starEmoji.setGravity(Gravity.CENTER);
        content.addView(starEmoji);

        // Level up text
        TextView levelText = new TextView(ctx);
        levelText.setText("LEVEL UP!");
        levelText.setTextColor(Color.parseColor("#FBBF24"));
        levelText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        levelText.setTypeface(Typeface.DEFAULT_BOLD);
        levelText.setGravity(Gravity.CENTER);
        levelText.setLetterSpacing(0.15f);
        LinearLayout.LayoutParams levelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        levelParams.topMargin = (int) (12 * density);
        levelText.setLayoutParams(levelParams);
        content.addView(levelText);

        // Level number
        TextView levelNum = new TextView(ctx);
        levelNum.setText("Level " + level);
        levelNum.setTextColor(Color.parseColor("#E5E7EB"));
        levelNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        levelNum.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams numParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        numParams.topMargin = (int) (8 * density);
        levelNum.setLayoutParams(numParams);
        content.addView(levelNum);

        // Title
        TextView titleText = new TextView(ctx);
        titleText.setText("\"" + title + "\"");
        titleText.setTextColor(Color.parseColor("#818CF8"));
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleText.setTypeface(null, Typeface.ITALIC);
        titleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = (int) (4 * density);
        titleText.setLayoutParams(titleParams);
        content.addView(titleText);

        TextView subtitleText = new TextView(ctx);
        subtitleText.setText("New reward tier unlocked");
        subtitleText.setTextColor(Color.parseColor("#9CA3AF"));
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = (int) (10 * density);
        subtitleText.setLayoutParams(subtitleParams);
        content.addView(subtitleText);

        overlay.addView(content);

        // Add to activity root
        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(overlay);

        // Animate in
        content.setScaleX(0.3f);
        content.setScaleY(0.3f);
        content.setAlpha(0f);
        overlay.setAlpha(0f);

        AnimatorSet animIn = new AnimatorSet();
        animIn.playTogether(
                ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(content, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(content, "scaleX", 0.3f, 1f),
                ObjectAnimator.ofFloat(content, "scaleY", 0.3f, 1f)
        );
        animIn.setDuration(600);
        animIn.setInterpolator(new OvershootInterpolator(1.2f));
        animIn.start();

        ValueAnimator starPulse = ValueAnimator.ofFloat(1f, 1.16f);
        starPulse.setDuration(900);
        starPulse.setRepeatCount(ValueAnimator.INFINITE);
        starPulse.setRepeatMode(ValueAnimator.REVERSE);
        starPulse.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            starEmoji.setScaleX(scale);
            starEmoji.setScaleY(scale);
        });
        starPulse.start();

        // Auto-dismiss
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AnimatorSet animOut = new AnimatorSet();
            animOut.playTogether(
                    ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(content, "scaleX", 1f, 0.8f),
                    ObjectAnimator.ofFloat(content, "scaleY", 1f, 0.8f)
            );
            animOut.setDuration(400);
            animOut.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    starPulse.cancel();
                    root.removeView(overlay);
                }
            });
            animOut.start();
        }, DISPLAY_DURATION_MS);

        // Tap to dismiss early
        overlay.setOnClickListener(v -> {
            AnimatorSet dismiss = new AnimatorSet();
            dismiss.playTogether(
                    ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f)
            );
            dismiss.setDuration(200);
            dismiss.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    starPulse.cancel();
                    root.removeView(overlay);
                }
            });
            dismiss.start();
        });
    }
}
