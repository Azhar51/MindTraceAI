package com.mindtrace.ai.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.CrisisEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Crisis Timeline adapter — visualizes crisis events chronologically
 * with distress reduction badges, resolution methods, and connecting lines.
 */
public class CrisisTimelineAdapter extends RecyclerView.Adapter<CrisisTimelineAdapter.TimelineViewHolder> {

    private List<CrisisEvent> events = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.US);

    public void setEvents(List<CrisisEvent> events) {
        this.events = events != null ? events : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TimelineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_crisis_timeline, parent, false);
        return new TimelineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimelineViewHolder h, int position) {
        CrisisEvent event = events.get(position);
        UiMotion.animateCardEntry(h.itemView, position);

        // Title with severity icon
        String icon;
        int dotColor;
        switch (event.crisisLevel != null ? event.crisisLevel : "") {
            case "CRITICAL":
                icon = "🆘";
                dotColor = R.color.crisis_alert;
                break;
            case "URGENT":
                icon = "🟠";
                dotColor = R.color.streak_fire;
                break;
            case "ELEVATED":
                icon = "🟡";
                dotColor = R.color.xp_gold;
                break;
            default:
                icon = "🔵";
                dotColor = R.color.primary;
        }
        h.tvTitle.setText(icon + " " + (event.crisisLevel != null ? event.crisisLevel : "Event"));

        // Timestamp
        h.tvTime.setText(dateFormat.format(new Date(event.createdAt > 0 ? event.createdAt : event.timestamp)));

        // Distress badge (pre → post)
        if (event.preDistressLevel > 0) {
            if (event.postDistressLevel > 0) {
                int reduction = event.preDistressLevel - event.postDistressLevel;
                String arrow = reduction > 0 ? " ↓" + reduction : (reduction < 0 ? " ↑" + (-reduction) : "");
                h.tvDistressBadge.setText(event.preDistressLevel + " → " + event.postDistressLevel + arrow);
                h.tvDistressBadge.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                        reduction > 0 ? R.color.calm_green : R.color.crisis_alert));
            } else {
                h.tvDistressBadge.setText("⚡ " + event.preDistressLevel);
            }
            h.tvDistressBadge.setVisibility(View.VISIBLE);
        } else {
            h.tvDistressBadge.setVisibility(View.GONE);
        }

        // Dot color
        h.timelineDot.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(h.itemView.getContext(), dotColor)));

        // Resolution method
        if (event.resolutionMethod != null && !"ACTIVE".equals(event.status)) {
            String method = event.resolutionMethod.replace("_", " ");
            h.tvResolution.setText("✅ Resolved via " + method);
            h.tvResolution.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.calm_green));
        } else if ("ACTIVE".equals(event.status)) {
            h.tvResolution.setText("🔴 Active — in progress");
            h.tvResolution.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.crisis_alert));
        } else {
            h.tvResolution.setText("⬜ Unresolved");
            h.tvResolution.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.text_tertiary));
        }

        // Active signals
        if (event.triggerSignalsJson != null && !event.triggerSignalsJson.isEmpty()) {
            h.tvSignals.setText("Signals: " + event.triggerSignalsJson.replace("|", ", "));
            h.tvSignals.setVisibility(View.VISIBLE);
        } else {
            h.tvSignals.setVisibility(View.GONE);
        }

        // Duration (if resolved)
        if (event.resolvedAt > 0 && event.createdAt > 0) {
            long durationMin = TimeUnit.MILLISECONDS.toMinutes(event.resolvedAt - event.createdAt);
            h.tvDuration.setText("Duration: " + (durationMin > 0 ? durationMin + " min" : "<1 min"));
            h.tvDuration.setVisibility(View.VISIBLE);
        } else {
            h.tvDuration.setVisibility(View.GONE);
        }

        // Hide connector for last item
        h.connector.setVisibility(position == events.size() - 1 ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class TimelineViewHolder extends RecyclerView.ViewHolder {
        final View timelineDot, connector;
        final TextView tvTitle, tvTime, tvDistressBadge, tvResolution, tvSignals, tvDuration;

        TimelineViewHolder(View v) {
            super(v);
            timelineDot = v.findViewById(R.id.timeline_dot);
            connector = v.findViewById(R.id.timeline_connector);
            tvTitle = v.findViewById(R.id.tv_event_title);
            tvTime = v.findViewById(R.id.tv_event_time);
            tvDistressBadge = v.findViewById(R.id.tv_distress_badge);
            tvResolution = v.findViewById(R.id.tv_resolution_method);
            tvSignals = v.findViewById(R.id.tv_signals);
            tvDuration = v.findViewById(R.id.tv_duration);
        }
    }
}
