package com.mindtrace.ai.ui;

import android.view.MotionEvent;
import android.view.View;

import com.mindtrace.ai.R;

public final class UiMotion {
    private UiMotion() {
    }

    public static void animateCardEntry(View view, int order) {
        if (view == null || Boolean.TRUE.equals(view.getTag())) {
            return;
        }

        view.setTag(Boolean.TRUE);
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 28));
        view.setScaleX(0.96f);
        view.setScaleY(0.96f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(Math.max(0, order) * 55L)
                .setDuration(300L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .start();
    }

    public static void animateHorizontalEntry(View view, int order) {
        if (view == null || Boolean.TRUE.equals(view.getTag(R.id.tag_motion_bound))) {
            return;
        }

        view.setTag(R.id.tag_motion_bound, Boolean.TRUE);
        view.setAlpha(0f);
        view.setTranslationX(dp(view, 28));
        view.animate()
                .alpha(1f)
                .translationX(0f)
                .setStartDelay(Math.max(0, order) * 55L)
                .setDuration(280L)
                .start();
    }

    public static void fadeVisibility(View view, boolean visible) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        if (visible) {
            if (view.getVisibility() == View.VISIBLE && view.getAlpha() >= 0.99f) {
                return;
            }
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(180L).start();
            return;
        }
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        view.animate()
                .alpha(0f)
                .setDuration(150L)
                .withEndAction(() -> {
                    view.setVisibility(View.GONE);
                    view.setAlpha(1f);
                })
                .start();
    }

    public static void attachPressAnimation(View view) {
        if (view == null) {
            return;
        }

        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .translationY(-dp(v, 1))
                        .setDuration(120L)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                        .start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(160L)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                        .start();
            }
            return false;
        });
    }

    /**
     * Stagger-animate children of a ViewGroup (e.g., radar signal pills).
     */
    public static void animatePillStagger(android.view.ViewGroup parent, int delayPerChild) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            child.setAlpha(0f);
            child.setScaleX(0.8f);
            child.setScaleY(0.8f);
            child.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(i * (long) delayPerChild)
                    .setDuration(220L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                    .start();
        }
    }

    /**
     * Pop animation for emoji or badge changes.
     */
    public static void animatePop(View view) {
        if (view == null) return;
        view.setScaleX(0.6f);
        view.setScaleY(0.6f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(280L)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f))
                .start();
    }

    public static void shake(View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setTranslationX(0f);
        view.setAlpha(1f); // Ensure it's visible in case of cancelled entry animation
        view.animate().translationX(dp(view, -8)).setDuration(40L).withEndAction(() ->
                view.animate().translationX(dp(view, 8)).setDuration(55L).withEndAction(() ->
                        view.animate().translationX(dp(view, -5)).setDuration(50L).withEndAction(() ->
                                view.animate().translationX(0f).setDuration(45L).start()
                        ).start()
                ).start()
        ).start();
    }

    private static float dp(View view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    /**
     * Completion celebration — pulse + scale effect on the whole card.
     * Call this when the user taps "Complete" on a task card.
     */
    public static void animateCompletion(View cardView, Runnable onComplete) {
        if (cardView == null) return;
        cardView.animate()
                .scaleX(1.05f).scaleY(1.05f)
                .setDuration(150)
                .withEndAction(() -> cardView.animate()
                        .scaleX(0.95f).scaleY(0.95f)
                        .alpha(0.3f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            cardView.setScaleX(1f);
                            cardView.setScaleY(1f);
                            cardView.setAlpha(1f);
                            if (onComplete != null) onComplete.run();
                        })
                        .start())
                .start();
    }

    /**
     * Skip animation — fade-out + slide left.
     */
    public static void animateSkip(View cardView, Runnable onComplete) {
        if (cardView == null) return;
        cardView.animate()
                .translationX(-dp(cardView, 200))
                .alpha(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    cardView.setTranslationX(0);
                    cardView.setAlpha(1f);
                    if (onComplete != null) onComplete.run();
                })
                .start();
    }

    /**
     * Smooth expand/collapse toggle for "Why this task?" section.
     */
    public static void toggleExpand(View content) {
        if (content == null) return;
        if (content.getVisibility() == View.VISIBLE) {
            content.animate()
                    .alpha(0f)
                    .translationY(-dp(content, 8))
                    .setDuration(180)
                    .withEndAction(() -> {
                        content.setVisibility(View.GONE);
                        content.setAlpha(1f);
                        content.setTranslationY(0f);
                    })
                    .start();
        } else {
            content.setAlpha(0f);
            content.setTranslationY(-dp(content, 8));
            content.setVisibility(View.VISIBLE);
            content.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .start();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADVANCED ANIMATIONS — PREMIUM LEVEL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Confetti particle burst — spawns N colorful particle views that
     * fly outward from the anchor point with random trajectories, then fade.
     * Used for task completion celebrations.
     *
     * @param anchor The view to burst from (e.g., the Complete button)
     * @param count  Number of particles (12-20 recommended)
     */
    public static void confettiBurst(View anchor, int count) {
        if (anchor == null || anchor.getParent() == null) return;

        android.view.ViewGroup root = (android.view.ViewGroup) anchor.getRootView()
                .findViewById(android.R.id.content);
        if (root == null) return;

        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int cx = loc[0] + anchor.getWidth() / 2;
        int cy = loc[1] + anchor.getHeight() / 2;

        int[] colors = {0xFF7C8FFF, 0xFF4ADE80, 0xFFF5A623, 0xFFFF6B6B, 0xFF6366F1, 0xFFE8ECF4};
        java.util.Random rnd = new java.util.Random();

        for (int i = 0; i < count; i++) {
            View particle = new View(anchor.getContext());
            int size = (int) dp(anchor, 4 + rnd.nextInt(5));
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(size, size);
            particle.setLayoutParams(lp);
            particle.setX(cx - size / 2f);
            particle.setY(cy - size / 2f);

            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(colors[rnd.nextInt(colors.length)]);
            particle.setBackground(dot);
            root.addView(particle);

            float angle = (float) (rnd.nextDouble() * 2 * Math.PI);
            float dist = dp(anchor, 60 + rnd.nextInt(80));
            float dx = (float) (Math.cos(angle) * dist);
            float dy = (float) (Math.sin(angle) * dist) - dp(anchor, 40); // bias upward

            particle.animate()
                    .translationXBy(dx)
                    .translationYBy(dy)
                    .scaleX(0.2f).scaleY(0.2f)
                    .alpha(0f)
                    .rotation(rnd.nextInt(360))
                    .setDuration(600 + rnd.nextInt(400))
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                    .withEndAction(() -> root.removeView(particle))
                    .start();
        }
    }

    /**
     * XP badge celebration — overshoot pop + gold flash.
     * Call when XP is awarded after task completion.
     */
    public static void animateXpBadge(View xpView) {
        if (xpView == null) return;
        xpView.setScaleX(0.3f);
        xpView.setScaleY(0.3f);
        xpView.setAlpha(0f);
        xpView.animate()
                .scaleX(1.15f).scaleY(1.15f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.OvershootInterpolator(3.0f))
                .withEndAction(() -> xpView.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(150)
                        .start())
                .start();
    }

    /**
     * Breathing pulse loop — gentle scale oscillation for mood emoji
     * or any view that should feel "alive". Runs continuously.
     *
     * @param view  The view to pulse
     * @param scale Peak scale factor (1.06f recommended)
     */
    public static void startBreathingPulse(View view, float scale) {
        if (view == null) return;
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(
                view, "scaleX", 1f, scale, 1f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(
                view, "scaleY", 1f, scale, 1f);

        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(2000);
        set.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        set.start();

        // Tag the animator so it can be cancelled later
        view.setTag(com.mindtrace.ai.R.id.tag_motion_bound, set);
    }

    /**
     * Stop a breathing pulse previously started with startBreathingPulse().
     */
    public static void stopBreathingPulse(View view) {
        if (view == null) return;
        Object tag = view.getTag(com.mindtrace.ai.R.id.tag_motion_bound);
        if (tag instanceof android.animation.AnimatorSet) {
            ((android.animation.AnimatorSet) tag).cancel();
            view.setScaleX(1f);
            view.setScaleY(1f);
        }
    }

    /**
     * Staggered children entry — animates all direct children of a ViewGroup
     * with cascading fade + slide-up. Perfect for card lists and form sections.
     *
     * @param parent       The parent ViewGroup
     * @param delayPerMs   Delay between each child's animation start
     * @param fromBottom   If true, slide up from below; if false, slide down from above
     */
    public static void staggerChildren(android.view.ViewGroup parent, int delayPerMs, boolean fromBottom) {
        if (parent == null) return;
        float direction = fromBottom ? 1f : -1f;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;

            child.setAlpha(0f);
            child.setTranslationY(direction * dp(child, 24));
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * (long) delayPerMs)
                    .setDuration(350)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                    .start();
        }
    }

    /**
     * Haptic feedback helper — provides light haptic click on supported devices.
     */
    public static void hapticClick(View view) {
        if (view == null) return;
        view.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    /**
     * Haptic feedback — heavy impact feel for completions and celebrations.
     */
    public static void hapticHeavy(View view) {
        if (view == null) return;
        view.performHapticFeedback(
                android.view.HapticFeedbackConstants.LONG_PRESS,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    /**
     * CTA button glow pulse — a repeating subtle glow expansion on important buttons.
     * Draws attention to the primary action.
     */
    public static void pulseGlow(View button) {
        if (button == null) return;
        android.animation.ObjectAnimator alpha = android.animation.ObjectAnimator.ofFloat(
                button, "alpha", 1f, 0.85f, 1f);
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(
                button, "scaleX", 1f, 1.02f, 1f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(
                button, "scaleY", 1f, 1.02f, 1f);

        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.playTogether(alpha, scaleX, scaleY);
        set.setDuration(2500);
        alpha.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        set.start();
    }

    /**
     * Success checkmark animation — draws a checkmark with scale + rotation.
     * Call after a successful form submission or task completion.
     */
    public static void animateSuccess(View view) {
        if (view == null) return;
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.setRotation(-45f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1f).scaleY(1f)
                .rotation(0f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f))
                .start();
    }
}

