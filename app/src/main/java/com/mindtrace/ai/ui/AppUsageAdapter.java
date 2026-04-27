package com.mindtrace.ai.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mindtrace.ai.AppUsageModel;
import com.mindtrace.ai.R;
import com.mindtrace.ai.ui.AppDetailActivity;

import java.util.ArrayList;
import java.util.List;

public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.ViewHolder> {
    private static final long OVERUSE_THRESHOLD_MILLIS = 2L * 60L * 60L * 1000L;

    private List<AppUsageModel> appList = new ArrayList<>();
    private boolean privacyModeEnabled;

    public void setData(List<AppUsageModel> list) {
        this.appList = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    public void setPrivacyModeEnabled(boolean privacyModeEnabled) {
        this.privacyModeEnabled = privacyModeEnabled;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_usage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppUsageModel app = appList.get(position);

        holder.tvName.setText(privacyModeEnabled ? maskAppName(app.appName) : app.appName);
        holder.ivIcon.setImageDrawable(app.appIcon != null
                ? app.appIcon
                : ContextCompat.getDrawable(holder.itemView.getContext(), android.R.drawable.sym_def_app_icon));
        holder.tvUsageTime.setText(buildUsageLine(app));
        holder.tvUsagePercentage.setText(buildMetaLine(app));
        holder.progressUsage.setProgress(app.usagePercentage);
        UiMotion.animateCardEntry(holder.itemView, position);

        boolean isOverused = app.usageTime >= OVERUSE_THRESHOLD_MILLIS;
        holder.tvWarning.setVisibility(isOverused ? View.VISIBLE : View.GONE);

        int indicatorColor = ContextCompat.getColor(
                holder.itemView.getContext(),
                isOverused ? R.color.warning_red : R.color.primary
        );
        holder.progressUsage.setIndicatorColor(indicatorColor);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), AppDetailActivity.class);
            intent.putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, app.packageName);
            intent.putExtra(AppDetailActivity.EXTRA_APP_NAME, app.appName);
            intent.putExtra(AppDetailActivity.EXTRA_USAGE_TIME, app.usageTime);
            intent.putExtra(AppDetailActivity.EXTRA_SESSIONS, app.foregroundSessions);
            intent.putExtra(AppDetailActivity.EXTRA_CATEGORY, app.appCategory);
            intent.putExtra(AppDetailActivity.EXTRA_FIRST_OPEN, app.firstOpenedTimestamp);
            intent.putExtra(AppDetailActivity.EXTRA_LAST_USED, app.lastUsedTimestamp);

            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    (Activity) v.getContext(),
                    Pair.create(holder.ivIcon, "transition_app_icon"),
                    Pair.create(holder.tvName, "transition_app_name")
            );

            v.getContext().startActivity(intent, options.toBundle());
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    private String formatUsageTime(long time) {
        long hours = time / (1000L * 60L * 60L);
        long minutes = (time % (1000L * 60L * 60L)) / (1000L * 60L);

        if (hours > 0) {
            return hours + "h " + minutes + "m today";
        }
        return minutes + " min today";
    }

    private String buildUsageLine(AppUsageModel app) {
        StringBuilder builder = new StringBuilder(formatUsageTime(app.usageTime));
        if (app.foregroundSessions > 0) {
            builder.append(" | ")
                    .append(app.foregroundSessions)
                    .append(app.foregroundSessions == 1 ? " session" : " sessions");
        }
        if (app.lastUsedTimestamp > 0L) {
            builder.append(" | Last used ")
                    .append(UiFormatting.formatTimeLabel(app.lastUsedTimestamp));
        }
        return builder.toString();
    }

    private String buildMetaLine(AppUsageModel app) {
        StringBuilder builder = new StringBuilder();
        int dayShare = app.percentOfTotalUsage > 0 ? app.percentOfTotalUsage : app.usagePercentage;
        builder.append(dayShare).append("% of today's usage");
        if (app.launchCount > 0) {
            builder.append(" | ")
                    .append(app.launchCount)
                    .append(app.launchCount == 1 ? " launch" : " launches");
        }
        if (app.appCategory != null && !app.appCategory.trim().isEmpty()) {
            builder.append(" | ").append(app.appCategory);
        }
        return builder.toString();
    }

    private String maskAppName(String name) {
        if (name == null || name.trim().isEmpty() || name.length() <= 2) {
            return "Hidden app";
        }
        return name.substring(0, 1) + "...";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvUsageTime;
        TextView tvUsagePercentage;
        TextView tvWarning;
        ImageView ivIcon;
        LinearProgressIndicator progressUsage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_app_name);
            tvUsageTime = itemView.findViewById(R.id.tv_usage_time);
            tvUsagePercentage = itemView.findViewById(R.id.tv_usage_percentage);
            tvWarning = itemView.findViewById(R.id.tv_warning);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            progressUsage = itemView.findViewById(R.id.progress_usage);
        }
    }
}
