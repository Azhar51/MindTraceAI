package com.mindtrace.ai.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UsageTrendGraphView extends View {

    public static class TrendData {
        public String label;
        public float productiveMins;
        public float passiveMins;
        public boolean isCurrent;

        public TrendData(String label, float productiveMins, float passiveMins, boolean isCurrent) {
            this.label = label;
            this.productiveMins = productiveMins;
            this.passiveMins = passiveMins;
            this.isCurrent = isCurrent;
        }

        public float getTotalMins() {
            return productiveMins + passiveMins;
        }
    }

    private final Paint paintProd = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintAxis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCurrentBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectTemp = new RectF();

    private List<TrendData> dataList = new ArrayList<>();
    private float maxMins = 60f;

    private float animProgress = 0f;
    private ValueAnimator animator;

    public UsageTrendGraphView(Context context) {
        super(context);
        init();
    }

    public UsageTrendGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintProd.setColor(Color.parseColor("#4DEEEA")); // Cyan
        paintProd.setStyle(Paint.Style.FILL);

        paintPass.setColor(Color.parseColor("#FF6B6B")); // Red
        paintPass.setStyle(Paint.Style.FILL);

        paintText.setColor(Color.parseColor("#A8B2C1")); // Lighter text
        paintText.setTextSize(dp(11f));
        paintText.setTextAlign(Paint.Align.CENTER);

        paintAxis.setColor(Color.parseColor("#4A5568")); // outline
        paintAxis.setStrokeWidth(dp(1f));

        paintCurrentBg.setColor(Color.parseColor("#154DEEEA"));
        paintCurrentBg.setStyle(Paint.Style.FILL);

        paintTrack.setColor(Color.parseColor("#08FFFFFF"));
        paintTrack.setStyle(Paint.Style.FILL);

        paintGrid.setColor(Color.parseColor("#10FFFFFF"));
        paintGrid.setStrokeWidth(dp(1f));
        
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setData(List<TrendData> dataList) {
        this.dataList = dataList;
        float actualMax = 0f;
        for (TrendData d : dataList) {
            if (d.getTotalMins() > actualMax) {
                actualMax = d.getTotalMins();
            }
        }
        // Round up to nearest hour, min 1 hour (60 mins)
        int hours = (int) Math.ceil(actualMax / 60f);
        if (hours == 0) hours = 1;
        this.maxMins = hours * 60f;
        
        animateGraph();
    }

    private void animateGraph() {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800);
        animator.setInterpolator(new OvershootInterpolator(1.2f));
        animator.addUpdateListener(animation -> {
            animProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataList.isEmpty()) return;

        float width = getWidth();
        float height = getHeight();

        float paddingBottom = dp(24f);
        float paddingTop = dp(20f);
        float paddingLeft = dp(32f); // Space for Y-axis labels
        float graphHeight = height - paddingBottom - paddingTop;

        float availableWidth = width - paddingLeft - dp(16f);
        int minCount = Math.max(7, dataList.size());
        float barWidth = availableWidth / minCount * 0.55f;
        
        // Cap bar width to look normal
        if (barWidth > dp(24f)) {
            barWidth = dp(24f);
        }
        
        int count = dataList.size();
        float totalGraphWidth = count * barWidth + (count - 1) * (availableWidth / minCount * 0.45f);
        float startX = paddingLeft + (availableWidth - totalGraphWidth) / 2f; 
        if (count > 0) {
            float gap = availableWidth / minCount * 0.45f;
            startX = paddingLeft + gap/2f;
        }

        // Draw horizontal grid lines and Y-axis labels
        int maxHours = (int) (maxMins / 60);
        int gridLines = Math.min(maxHours, 4); 
        if (gridLines == 0) gridLines = 1;
        float stepH = graphHeight / gridLines;
        float stepMins = maxMins / gridLines;

        paintText.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= gridLines; i++) {
            float y = height - paddingBottom - (i * stepH);
            canvas.drawLine(paddingLeft, y, width, y, paintGrid);
            int hourVal = (int) ((i * stepMins) / 60);
            if (hourVal > 0) {
                canvas.drawText(hourVal + "h", paddingLeft - dp(6f), y + dp(4f), paintText);
            }
        }

        // Draw X-axis line
        canvas.drawLine(paddingLeft, height - paddingBottom, width, height - paddingBottom, paintAxis);

        paintText.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < count; i++) {
            TrendData data = dataList.get(i);
            float gap = availableWidth / minCount * 0.45f;
            float cx = startX + i * (barWidth + gap) + barWidth / 2f;
            float bottom = height - paddingBottom;
            float top = paddingTop;

            // Draw label
            canvas.drawText(data.label, cx, height - dp(6f), paintText);

            if (data.isCurrent) {
                rectTemp.set(cx - barWidth/2f - dp(6f), top - dp(10f), cx + barWidth/2f + dp(6f), bottom + dp(4f));
                canvas.drawRoundRect(rectTemp, dp(8f), dp(8f), paintCurrentBg);
                paintText.setColor(Color.parseColor("#4DEEEA"));
                paintText.setFakeBoldText(true);
                canvas.drawText(data.label, cx, height - dp(6f), paintText);
                paintText.setColor(Color.parseColor("#8E99AF"));
                paintText.setFakeBoldText(false);
            }

            // Draw background track for the bar
            rectTemp.set(cx - barWidth / 2f, top, cx + barWidth / 2f, bottom);
            canvas.drawRoundRect(rectTemp, barWidth / 2f, barWidth / 2f, paintTrack);

            if (data.getTotalMins() > 0) {
                float totalH = (data.getTotalMins() / maxMins) * graphHeight * animProgress;
                float prodH = (data.productiveMins / data.getTotalMins()) * totalH;
                float passH = totalH - prodH;

                // Configure gradients
                android.graphics.LinearGradient passGradient = new android.graphics.LinearGradient(
                        0, bottom - totalH, 0, bottom,
                        new int[]{Color.parseColor("#FF8A8A"), Color.parseColor("#FF5252")},
                        null, android.graphics.Shader.TileMode.CLAMP
                );
                paintPass.setShader(passGradient);
                paintPass.setShadowLayer(dp(4f), 0, dp(2f), Color.parseColor("#40FF5252"));

                android.graphics.LinearGradient prodGradient = new android.graphics.LinearGradient(
                        0, bottom - prodH, 0, bottom,
                        new int[]{Color.parseColor("#4DEEEA"), Color.parseColor("#00B4DB")},
                        null, android.graphics.Shader.TileMode.CLAMP
                );
                paintProd.setShader(prodGradient);
                paintProd.setShadowLayer(dp(4f), 0, dp(2f), Color.parseColor("#4000B4DB"));

                // Draw Passive (Bottom, full height of total)
                rectTemp.set(cx - barWidth / 2f, bottom - totalH, cx + barWidth / 2f, bottom);
                canvas.drawRoundRect(rectTemp, barWidth / 2f, barWidth / 2f, paintPass);
                
                // Draw Productive (Bottom, partial height)
                if (prodH > 0) {
                    rectTemp.set(cx - barWidth / 2f, bottom - prodH, cx + barWidth / 2f, bottom);
                    if (prodH >= barWidth) { 
                        // If it fills enough, draw round rect
                        if (prodH >= totalH - 1f) {
                            // Only productive, fully round
                            canvas.drawRoundRect(rectTemp, barWidth / 2f, barWidth / 2f, paintProd);
                        } else {
                            // Stacked. Make bottom round, top flat where it touches passive
                            canvas.drawRoundRect(rectTemp, barWidth / 2f, barWidth / 2f, paintProd);
                            canvas.drawRect(cx - barWidth/2f, bottom - prodH, cx + barWidth/2f, bottom - prodH + barWidth/2f, paintProd);
                        }
                    } else {
                        // Very tiny dot at bottom
                        canvas.drawCircle(cx, bottom - barWidth/2f, barWidth/2f, paintProd);
                    }
                }
            }
        }
    }

    private float dp(float px) {
        return px * getResources().getDisplayMetrics().density;
    }
}
