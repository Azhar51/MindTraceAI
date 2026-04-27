package com.mindtrace.ai.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.mindtrace.ai.ui.theme.ColorSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SparklineView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> points = new ArrayList<>();

    public SparklineView(Context context) {
        this(context, null);
    }

    public SparklineView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SparklineView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeWidth(dp(3));
        linePaint.setColor(ColorSystem.PRIMARY);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(adjustAlpha(ColorSystem.PRIMARY, 0.18f));

        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(ColorSystem.TEXT_PRIMARY);
    }

    public void setLineColor(int color) {
        linePaint.setColor(color);
        fillPaint.setColor(adjustAlpha(color, 0.18f));
        invalidate();
    }

    public void setData(@Nullable List<Float> values) {
        points.clear();
        if (values != null) {
            points.addAll(values);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        float contentLeft = getPaddingLeft() + dp(4);
        float contentTop = getPaddingTop() + dp(6);
        float contentRight = getWidth() - getPaddingRight() - dp(4);
        float contentBottom = getHeight() - getPaddingBottom() - dp(8);
        float min = Collections.min(points);
        float max = Collections.max(points);
        float range = Math.max(1f, max - min);

        Path linePath = new Path();
        Path fillPath = new Path();

        for (int i = 0; i < points.size(); i++) {
            float x = points.size() == 1
                    ? (contentLeft + contentRight) / 2f
                    : contentLeft + ((contentRight - contentLeft) * i / (points.size() - 1f));
            float normalized = (points.get(i) - min) / range;
            float y = contentBottom - normalized * (contentBottom - contentTop);

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, contentBottom);
                fillPath.lineTo(x, y);
            } else {
                float previousX = contentLeft + ((contentRight - contentLeft) * (i - 1) / (points.size() - 1f));
                float previousNormalized = (points.get(i - 1) - min) / range;
                float previousY = contentBottom - previousNormalized * (contentBottom - contentTop);
                float controlX = (previousX + x) / 2f;
                linePath.quadTo(controlX, previousY, x, y);
                fillPath.quadTo(controlX, previousY, x, y);
            }
        }

        float lastX = points.size() == 1 ? (contentLeft + contentRight) / 2f : contentRight;
        fillPath.lineTo(lastX, contentBottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        float radius = dp(3);
        float firstNormalized = (points.get(0) - min) / range;
        float firstY = contentBottom - firstNormalized * (contentBottom - contentTop);
        float lastNormalized = (points.get(points.size() - 1) - min) / range;
        float lastY = contentBottom - lastNormalized * (contentBottom - contentTop);
        canvas.drawCircle(contentLeft, firstY, radius, pointPaint);
        canvas.drawCircle(lastX, lastY, radius, pointPaint);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(android.graphics.Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
