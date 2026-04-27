package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ConfettiView — particle burst animation for task completions,
 * XP gains, level ups, and streak milestones.
 *
 * <p>Usage: {@code confettiView.burst(); } or {@code confettiView.burst(50); }</p>
 */
public class ConfettiView extends View {

    private static final int DEFAULT_PARTICLE_COUNT = 40;
    private static final long ANIMATION_DURATION = 1500;

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private ValueAnimator animator;
    private float burstOriginX = -1f;
    private float burstOriginY = -1f;

    // Celebration colors — vibrant palette
    private final int[] colors = {
            0xFFFBBF24, // Gold
            0xFF818CF8, // Indigo
            0xFF22C55E, // Green
            0xFFF472B6, // Pink
            0xFF60A5FA, // Blue
            0xFFFF6B6B, // Red
            0xFFA78BFA, // Purple
            0xFF34D399  // Teal
    };

    public ConfettiView(Context context) {
        super(context);
    }

    public ConfettiView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Trigger a confetti burst from the center.
     */
    public void burst() {
        burst(DEFAULT_PARTICLE_COUNT);
    }

    /**
     * Trigger a confetti burst with specified particle count.
     */
    public void burst(int count) {
        setVisibility(VISIBLE);
        particles.clear();

        float cx = burstOriginX >= 0f ? burstOriginX : getWidth() / 2f;
        float cy = burstOriginY >= 0f ? burstOriginY : getHeight() / 2f;

        for (int i = 0; i < count; i++) {
            particles.add(new Particle(cx, cy));
        }

        if (animator != null && animator.isRunning()) animator.cancel();

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new AccelerateInterpolator(0.5f));
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            for (Particle p : particles) {
                p.update(progress);
            }
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                particles.clear();
                burstOriginX = -1f;
                burstOriginY = -1f;
                setVisibility(GONE);
            }
        });
        animator.start();
    }

    public void burstFrom(float x, float y, int count) {
        burstOriginX = x;
        burstOriginY = y;
        burst(count);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Particle p : particles) {
            paint.setColor(p.color);
            paint.setAlpha((int) (p.alpha * 255));
            canvas.save();
            canvas.rotate(p.rotation, p.x, p.y);
            canvas.drawRect(p.x - p.size / 2, p.y - p.size / 2,
                    p.x + p.size / 2, p.y + p.size / 2, paint);
            canvas.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    /**
     * Single confetti particle with physics.
     */
    private class Particle {
        float x, y;
        float startX, startY;
        float velocityX, velocityY;
        float size;
        float rotation;
        float rotationSpeed;
        float alpha = 1f;
        int color;
        float gravity;

        Particle(float cx, float cy) {
            this.startX = cx;
            this.startY = cy;
            this.x = cx;
            this.y = cy;

            // Random direction burst
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = 200 + random.nextFloat() * 400;
            this.velocityX = (float) (Math.cos(angle) * speed);
            this.velocityY = (float) (Math.sin(angle) * speed) - 300; // Upward bias

            this.size = 6 + random.nextFloat() * 10;
            this.rotation = random.nextFloat() * 360;
            this.rotationSpeed = -360 + random.nextFloat() * 720;
            this.gravity = 800 + random.nextFloat() * 400;
            this.color = colors[random.nextInt(colors.length)];
        }

        void update(float progress) {
            float t = progress;
            x = startX + velocityX * t;
            y = startY + velocityY * t + 0.5f * gravity * t * t;
            rotation += rotationSpeed * 0.016f; // ~60fps
            alpha = Math.max(0, 1f - progress * progress);
        }
    }
}
