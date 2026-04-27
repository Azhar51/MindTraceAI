package com.mindtrace.ai.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatTextView;

import com.mindtrace.ai.ui.theme.ColorSystem;

import java.util.Locale;

public class DeviationView extends LinearLayout {
    private final AppCompatTextView arrowView;
    private final AppCompatTextView textView;

    public DeviationView(Context context) {
        this(context, null);
    }

    public DeviationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeviationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        arrowView = new AppCompatTextView(context);
        arrowView.setTextSize(14f);
        arrowView.setTypeface(arrowView.getTypeface(), android.graphics.Typeface.BOLD);
        LayoutParams arrowParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        addView(arrowView, arrowParams);

        textView = new AppCompatTextView(context);
        textView.setTextSize(13f);
        LayoutParams textParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = dp(6);
        addView(textView, textParams);

        showNeutral("Learning your behavior...");
    }

    public void setDeviation(double deviation) {
        if (Double.isNaN(deviation)) {
            showNeutral("Learning your behavior...");
            return;
        }

        double absolute = Math.abs(deviation) * 100d;
        if (absolute < 5d) {
            showNeutral("Close to your weekly baseline");
            return;
        }

        if (deviation > 0d) {
            arrowView.setText("↑");
            arrowView.setTextColor(ColorSystem.RED);
            textView.setTextColor(ColorSystem.RED);
            textView.setText(String.format(Locale.getDefault(), "%.0f%% above your baseline", absolute));
        } else {
            arrowView.setText("↓");
            arrowView.setTextColor(ColorSystem.GREEN);
            textView.setTextColor(ColorSystem.GREEN);
            textView.setText(String.format(Locale.getDefault(), "%.0f%% below your baseline", absolute));
        }
    }

    public void showNeutral(String text) {
        arrowView.setText("•");
        arrowView.setTextColor(ColorSystem.TEXT_SECONDARY);
        textView.setTextColor(ColorSystem.TEXT_SECONDARY);
        textView.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
