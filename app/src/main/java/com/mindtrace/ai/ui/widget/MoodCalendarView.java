package com.mindtrace.ai.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mood Calendar Heatmap — a 35-cell (5 weeks × 7 days) grid showing
 * emotional history at a glance.
 *
 * <p>Each cell represents one day, colored by the user's mood check-in.
 * Tapping a cell triggers a ripple animation and fires the {@link OnDayClickListener}.</p>
 */
public class MoodCalendarView extends View {

    private static final int COLS = 7;
    private static final int ROWS = 5;
    private static final String[] DAY_LABELS = {"S", "M", "T", "W", "T", "F", "S"};
    private static final float CELL_CORNER_RADIUS = 6f;
    private static final float CELL_GAP = 4f;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cellRect = new RectF();

    private int colorEmpty, colorHappy, colorCalm, colorNeutral;
    private int colorAnxious, colorSad, colorAngry, colorNumb;
    private int colorTextDim, colorTodayStroke;

    private Map<Long, String> moodData = new HashMap<>();
    private long gridStartTimestamp;
    private long todayTimestamp;

    // Ripple state
    private float rippleX = -1, rippleY = -1, rippleRadius = 0, rippleAlpha = 0;
    private OnDayClickListener dayClickListener;

    /** Callback for calendar day taps. */
    public interface OnDayClickListener {
        void onDayClick(long dayTimestamp, @Nullable String mood, String dateLabel);
    }

    public MoodCalendarView(Context context) { super(context); init(); }
    public MoodCalendarView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public MoodCalendarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        colorEmpty = 0xFF1E2A42;
        colorHappy = 0xFF22C55E;
        colorCalm = 0xFF60A5FA;
        colorNeutral = 0xFFF59E0B;
        colorAnxious = 0xFFF97316;
        colorSad = 0xFFEF4444;
        colorAngry = 0xFFE11D48;
        colorNumb = 0xFF8B5CF6;
        colorTextDim = 0xFF6B7280;
        colorTodayStroke = 0xFF7C8FFF;

        float dp = getResources().getDisplayMetrics().density;
        textPaint.setColor(0xFFE5E7EB);
        textPaint.setTextSize(10f * dp);
        textPaint.setTextAlign(Paint.Align.CENTER);

        labelPaint.setColor(colorTextDim);
        labelPaint.setTextSize(10f * dp);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);

        todayStrokePaint.setColor(colorTodayStroke);
        todayStrokePaint.setStyle(Paint.Style.STROKE);
        todayStrokePaint.setStrokeWidth(2f * dp);

        ripplePaint.setColor(0xFFFFFFFF);
        ripplePaint.setStyle(Paint.Style.FILL);

        computeGrid();
    }

    private void computeGrid() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        todayTimestamp = cal.getTimeInMillis();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysBack = (ROWS - 1) * 7 + (dayOfWeek - 1);
        cal.add(Calendar.DAY_OF_YEAR, -daysBack);
        gridStartTimestamp = cal.getTimeInMillis();
    }

    public void setMoodData(@NonNull Map<Long, String> data) {
        this.moodData = data;
        invalidate();
    }

    public void setOnDayClickListener(@Nullable OnDayClickListener listener) {
        this.dayClickListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float dp = getResources().getDisplayMetrics().density;
        float labelHeight = 20f * dp;
        float cellSize = (width - (COLS - 1) * CELL_GAP * dp) / (float) COLS;
        int height = (int) (labelHeight + ROWS * cellSize + (ROWS - 1) * CELL_GAP * dp + 8 * dp);
        setMeasuredDimension(width, height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX(), y = event.getY();
            float dp = getResources().getDisplayMetrics().density;
            float gap = CELL_GAP * dp;
            float labelHeight = 20f * dp;
            float cellSize = (getWidth() - (COLS - 1) * gap) / COLS;

            int col = (int) (x / (cellSize + gap));
            int row = (int) ((y - labelHeight) / (cellSize + gap));

            if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(gridStartTimestamp);
                cal.add(Calendar.DAY_OF_YEAR, row * COLS + col);
                long dayTs = cal.getTimeInMillis();

                if (dayTs <= todayTimestamp) {
                    String mood = moodData.get(dayTs);

                    // Trigger ripple animation
                    float cellLeft = col * (cellSize + gap);
                    float cellTop = labelHeight + row * (cellSize + gap);
                    rippleX = cellLeft + cellSize / 2f;
                    rippleY = cellTop + cellSize / 2f;
                    animateRipple(cellSize);

                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                    if (dayClickListener != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.US);
                        dayClickListener.onDayClick(dayTs, mood, sdf.format(new Date(dayTs)));
                    }
                }
            }
            return true;
        }
        return event.getAction() == MotionEvent.ACTION_DOWN || super.onTouchEvent(event);
    }

    private void animateRipple(float maxRadius) {
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(350);
        anim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            rippleRadius = maxRadius * f;
            rippleAlpha = (1f - f) * 0.35f;
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                rippleX = -1; rippleY = -1; invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float dp = getResources().getDisplayMetrics().density;
        float gap = CELL_GAP * dp;
        float labelHeight = 20f * dp;
        float cellSize = (getWidth() - (COLS - 1) * gap) / COLS;
        float cornerRadius = CELL_CORNER_RADIUS * dp;

        for (int col = 0; col < COLS; col++) {
            float cx = col * (cellSize + gap) + cellSize / 2f;
            canvas.drawText(DAY_LABELS[col], cx, labelHeight - 4 * dp, labelPaint);
        }

        Calendar dayCal = Calendar.getInstance();
        dayCal.setTimeInMillis(gridStartTimestamp);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                long dayTs = dayCal.getTimeInMillis();
                float left = col * (cellSize + gap);
                float top = labelHeight + row * (cellSize + gap);
                cellRect.set(left, top, left + cellSize, top + cellSize);

                String mood = moodData.get(dayTs);
                cellPaint.setColor(getMoodColor(mood));
                cellPaint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, cellPaint);

                if (dayTs == todayTimestamp) {
                    canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, todayStrokePaint);
                }

                boolean isFuture = dayTs > todayTimestamp;
                if (!isFuture) {
                    textPaint.setColor(mood != null ? 0xFFFFFFFF : colorTextDim);
                    textPaint.setTextSize(10f * dp);
                    float textY = top + cellSize / 2f + textPaint.getTextSize() / 3f;
                    canvas.drawText(String.valueOf(dayCal.get(Calendar.DAY_OF_MONTH)),
                            left + cellSize / 2f, textY, textPaint);
                }
                dayCal.add(Calendar.DAY_OF_YEAR, 1);
            }
        }

        // Ripple overlay
        if (rippleX >= 0 && rippleY >= 0 && rippleAlpha > 0) {
            ripplePaint.setAlpha((int) (rippleAlpha * 255));
            canvas.drawCircle(rippleX, rippleY, rippleRadius, ripplePaint);
        }
    }

    private int getMoodColor(@Nullable String mood) {
        if (mood == null) return colorEmpty;
        switch (mood) {
            case "Happy":   return colorHappy;
            case "Calm":    return colorCalm;
            case "Neutral": return colorNeutral;
            case "Anxious": return colorAnxious;
            case "Sad":     return colorSad;
            case "Angry":   return colorAngry;
            case "Numb":    return colorNumb;
            default:        return colorEmpty;
        }
    }
}
