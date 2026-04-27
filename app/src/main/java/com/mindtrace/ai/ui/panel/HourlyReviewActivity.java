package com.mindtrace.ai.ui.panel;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mindtrace.ai.R;
import com.mindtrace.ai.ai.AppCategoryMapper;
import com.mindtrace.ai.viewmodel.UsageViewModel;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hourly usage review screen showing per-hour breakdowns of app usage.
 *
 * <p>Phase 5 refactor: Business logic (data loading, hour aggregation, classification)
 * extracted to {@link UsageViewModel}. This Activity is now a thin UI layer that
 * observes LiveData and renders the adapter.</p>
 */
public class HourlyReviewActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private UsageViewModel usageViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hourly_review);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.recycler_hourly_review);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        // ── Phase 5: ViewModel-driven data loading ──
        usageViewModel = new ViewModelProvider(this).get(UsageViewModel.class);
        usageViewModel.getHourlyData().observe(this, hourBuckets -> {
            if (hourBuckets != null) {
                recycler.setAdapter(new HourlyReviewAdapter(hourBuckets));
            }
        });
        usageViewModel.loadTodayHourlyData();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADAPTER (UI-only — all business logic lives in UsageViewModel)
    // ═══════════════════════════════════════════════════════════════════

    private class HourlyReviewAdapter extends RecyclerView.Adapter<HourlyReviewAdapter.ViewHolder> {

        private final List<UsageViewModel.HourBucket> items;

        HourlyReviewAdapter(List<UsageViewModel.HourBucket> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hourly_review, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UsageViewModel.HourBucket data = items.get(position);

            String amPmStart = data.hour >= 12 ? "PM" : "AM";
            int h12Start = data.hour % 12;
            if (h12Start == 0) h12Start = 12;

            int endHour = data.hour + 1;
            String amPmEnd = endHour >= 12 && endHour < 24 ? "PM" : "AM";
            int h12End = endHour % 12;
            if (h12End == 0) h12End = 12;

            holder.tvHourLabel.setText(String.format(Locale.getDefault(), "%02d:00 %s - %02d:00 %s", h12Start, amPmStart, h12End, amPmEnd));
            holder.tvTotalDuration.setText((data.getTotalMs() / 60000L) + "m");

            PackageManager pm = holder.itemView.getContext().getPackageManager();

            // Resolve top app label for insight text
            String topAppLabel;
            try {
                ApplicationInfo info = pm.getApplicationInfo(data.getTopPackage(), 0);
                topAppLabel = pm.getApplicationLabel(info).toString();
            } catch (Exception e) {
                topAppLabel = AppCategoryMapper.getAppName(data.getTopPackage());
            }
            holder.tvInsight.setText(data.getInsight(topAppLabel));

            // Indicator color based on classification
            GradientDrawable bg = (GradientDrawable) holder.viewIndicator.getBackground();
            switch (data.getClassification()) {
                case UsageViewModel.HourBucket.TYPE_PASSIVE:
                    bg.setColor(Color.parseColor("#FF6B6B"));
                    break;
                case UsageViewModel.HourBucket.TYPE_PRODUCTIVE:
                    bg.setColor(Color.parseColor("#4DEEEA"));
                    break;
                default:
                    bg.setColor(Color.parseColor("#D4A843"));
                    break;
            }

            holder.containerApps.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
            for (Map.Entry<String, Long> entry : data.appTimes) {
                long mins = entry.getValue() / 60000L;
                if (mins <= 0) continue;

                View row = inflater.inflate(R.layout.item_hourly_app_row, holder.containerApps, false);
                ImageView ivIcon = row.findViewById(R.id.iv_app_icon);
                TextView tvName = row.findViewById(R.id.tv_app_name);
                TextView tvTime = row.findViewById(R.id.tv_app_time);
                ProgressBar progressApp = row.findViewById(R.id.progress_app);

                try {
                    Drawable icon = pm.getApplicationIcon(entry.getKey());
                    ivIcon.setImageDrawable(icon);
                    ApplicationInfo info = pm.getApplicationInfo(entry.getKey(), 0);
                    tvName.setText(pm.getApplicationLabel(info));
                } catch (Exception e) {
                    ivIcon.setImageResource(R.mipmap.ic_launcher);
                    tvName.setText(AppCategoryMapper.getAppName(entry.getKey()));
                }

                tvTime.setText(mins + "m");
                progressApp.setMax(60);
                progressApp.setProgress((int) mins);

                holder.containerApps.addView(row);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View viewIndicator;
            TextView tvHourLabel;
            TextView tvTotalDuration;
            TextView tvInsight;
            LinearLayout containerApps;

            ViewHolder(View itemView) {
                super(itemView);
                viewIndicator = itemView.findViewById(R.id.view_indicator);
                tvHourLabel = itemView.findViewById(R.id.tv_hour_label);
                tvTotalDuration = itemView.findViewById(R.id.tv_total_duration);
                tvInsight = itemView.findViewById(R.id.tv_insight);
                containerApps = itemView.findViewById(R.id.container_apps);
            }
        }
    }
}
