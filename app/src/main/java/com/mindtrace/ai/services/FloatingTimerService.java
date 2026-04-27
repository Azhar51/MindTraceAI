package com.mindtrace.ai.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.mindtrace.ai.R;

public class FloatingTimerService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private TextView timerText;
    private CardView cardView;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private int secondsElapsed = 0;
    
    // Limits
    private static final int AMBER_LIMIT_SECONDS = 30 * 60; // 30 mins
    private static final int RED_LIMIT_SECONDS = 60 * 60; // 60 mins

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_timer_overlay, null);
        timerText = floatingView.findViewById(R.id.timer_text);
        cardView = floatingView.findViewById(R.id.floating_timer_container);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x = 30; // Margin from right
        params.y = 150; // Margin from bottom

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(floatingView, params);

        setupDragging(floatingView, params);
        
        startTimer();
    }
    
    private void startTimer() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                secondsElapsed++;
                updateTimerUI();
                handler.postDelayed(this, 1000);
            }
        });
    }
    
    private void updateTimerUI() {
        int hours = secondsElapsed / 3600;
        int minutes = (secondsElapsed % 3600) / 60;
        int seconds = secondsElapsed % 60;
        
        timerText.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        
        // Update color based on duration
        if (secondsElapsed >= RED_LIMIT_SECONDS) {
            cardView.setCardBackgroundColor(Color.parseColor("#E74C3C")); // Red
        } else if (secondsElapsed >= AMBER_LIMIT_SECONDS) {
            cardView.setCardBackgroundColor(Color.parseColor("#F39C12")); // Amber
        } else {
            cardView.setCardBackgroundColor(Color.parseColor("#2ECC71")); // Green
        }
    }

    private void setupDragging(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        // Negative because gravity is bottom/end
                        params.x = initialX - (int) (event.getRawX() - initialTouchX);
                        params.y = initialY - (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
        handler.removeCallbacksAndMessages(null);
    }
}
