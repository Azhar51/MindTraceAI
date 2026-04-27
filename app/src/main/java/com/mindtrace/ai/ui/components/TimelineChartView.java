package com.mindtrace.ai.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * TimelineChartView — vertical timeline with colored dot + card events.
 * Used for crisis history, wellness milestones, and progress tracking.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * List<TimelineEvent> events = new ArrayList<>();
 * events.add(new TimelineEvent("Crisis Resolved", "Used breathing exercise", 0xFF22C55E));
 * timelineView.setEvents(events);
 * }</pre>
 */
public class TimelineChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();

    private List<TimelineEvent> events = new ArrayList<>();
    private float itemHeight;
    private float dotRadius;
    private float lineWidth;

    // Colors
    private int lineColor = 0xFF1F2937;
    private int cardColor = 0xFF18212D;
    private int titleColor = 0xFFE5E7EB;
    private int subtitleColor = 0xFF9CA3AF;

    public TimelineChartView(Context context) {
        super(context);
        init();
    }

    public TimelineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimelineChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        itemHeight = 80 * density;
        dotRadius = 8 * density;
        lineWidth = 2 * density;

        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setColor(lineColor);
        linePaint.setStrokeWidth(lineWidth);

        dotPaint.setStyle(Paint.Style.FILL);

        dotOutlinePaint.setStyle(Paint.Style.STROKE);
        dotOutlinePaint.setStrokeWidth(2 * density);

        titlePaint.setColor(titleColor);
        titlePaint.setTextSize(14 * density);

        subtitlePaint.setColor(subtitleColor);
        subtitlePaint.setTextSize(12 * density);

        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor(cardColor);
    }

    /**
     * Set timeline events.
     */
    public void setEvents(List<TimelineEvent> events) {
        this.events = events != null ? events : new ArrayList<>();
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (events.size() * itemHeight + getPaddingTop() + getPaddingBottom());
        setMeasuredDimension(width, Math.max(height, getSuggestedMinimumHeight()));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (events.isEmpty()) return;

        float density = getResources().getDisplayMetrics().density;
        float dotCenterX = getPaddingLeft() + 24 * density;
        float contentLeft = dotCenterX + 24 * density;
        float contentRight = getWidth() - getPaddingRight() - 8 * density;
        float cornerRadius = 12 * density;

        for (int i = 0; i < events.size(); i++) {
            TimelineEvent event = events.get(i);
            float top = getPaddingTop() + i * itemHeight;
            float dotCenterY = top + itemHeight / 2f;

            // Draw vertical line (skip after last item)
            if (i < events.size() - 1) {
                linePaint.setColor(lineColor);
                canvas.drawRect(dotCenterX - lineWidth / 2, dotCenterY + dotRadius,
                        dotCenterX + lineWidth / 2, dotCenterY + itemHeight - dotRadius,
                        linePaint);
            }

            // Draw dot
            dotPaint.setColor(event.color);
            canvas.drawCircle(dotCenterX, dotCenterY, dotRadius, dotPaint);

            // Dot outer glow
            dotOutlinePaint.setColor(withAlpha(event.color, 80));
            canvas.drawCircle(dotCenterX, dotCenterY, dotRadius + 3 * density, dotOutlinePaint);

            // Draw card background
            float cardTop = top + 8 * density;
            float cardBottom = top + itemHeight - 8 * density;
            cardRect.set(contentLeft, cardTop, contentRight, cardBottom);
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardPaint);

            // Draw title
            canvas.drawText(event.title, contentLeft + 12 * density,
                    cardTop + 24 * density, titlePaint);

            // Draw subtitle
            if (event.subtitle != null && !event.subtitle.isEmpty()) {
                canvas.drawText(event.subtitle, contentLeft + 12 * density,
                        cardTop + 44 * density, subtitlePaint);
            }
        }
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Data class for a single timeline event.
     */
    public static class TimelineEvent {
        public final String title;
        public final String subtitle;
        public final int color;
        public long timestamp;

        public TimelineEvent(String title, String subtitle, int color) {
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
        }

        public TimelineEvent(String title, String subtitle, int color, long timestamp) {
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
            this.timestamp = timestamp;
        }
    }
}
