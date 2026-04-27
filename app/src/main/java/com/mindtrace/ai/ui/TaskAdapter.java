package com.mindtrace.ai.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.InterventionTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Premium task card adapter with category-colored strips, XP badges,
 * "why this task" expandable text, skip/complete actions, and micro-intervention badges.
 */
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<InterventionTask> tasks = new ArrayList<>();
    private OnTaskActionListener listener;

    // ─────────────────────────────────────────────────────────────────────
    // LISTENER
    // ─────────────────────────────────────────────────────────────────────

    public interface OnTaskActionListener {
        void onCompleteClick(InterventionTask task);
        default void onSkipClick(InterventionTask task) {}
    }

    // Legacy compatibility
    public interface OnTaskClickListener {
        void onCompleteClick(InterventionTask task);
    }

    public void setTasks(List<InterventionTask> tasks, @Nullable OnTaskClickListener listener) {
        this.tasks = tasks == null ? new ArrayList<>() : tasks;
        if (listener != null) {
            this.listener = new OnTaskActionListener() {
                @Override
                public void onCompleteClick(InterventionTask task) {
                    listener.onCompleteClick(task);
                }
            };
        } else {
            this.listener = null;
        }
        notifyDataSetChanged();
    }

    public void setTasks(List<InterventionTask> tasks, @Nullable OnTaskActionListener listener) {
        this.tasks = tasks == null ? new ArrayList<>() : tasks;
        this.listener = listener;
        notifyDataSetChanged();
    }

    // ─────────────────────────────────────────────────────────────────────
    // ADAPTER
    // ─────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        InterventionTask task = tasks.get(position);
        UiMotion.animateCardEntry(holder.itemView, position);

        // Title + Description
        holder.tvTitle.setText(task.title);
        holder.tvDesc.setText(task.description);

        // Category icon
        holder.tvIcon.setText(task.getCategoryIcon());

        // Category color strip
        holder.viewCategoryStrip.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.getContext(), getCategoryColor(task.category)));

        // XP badge
        int xp = task.getEffectiveXp();
        holder.tvXp.setText("+" + xp + " XP");

        // Meta row
        String diff = task.difficulty != null ? task.difficulty : "EASY";
        String category = task.category != null && !task.category.isEmpty() ? task.category : "Wellbeing";
        holder.tvMeta.setText(category + " | " + diff + " | " + task.durationMinutes + " min");
        holder.tvMeta.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), getCategoryColor(task.category)));

        // Micro-intervention badge
        if (task.isMicroIntervention) {
            holder.tvMicro.setVisibility(View.VISIBLE);
        } else {
            holder.tvMicro.setVisibility(View.GONE);
        }

        // "Why this task" — always visible trigger reason (§2E1.5)
        if (task.whyThisTask != null && !task.whyThisTask.isEmpty()) {
            holder.tvWhy.setText("Why: " + task.whyThisTask);
            holder.tvWhy.setVisibility(View.VISIBLE);
        } else {
            holder.tvWhy.setVisibility(View.GONE);
        }

        // Actions
        boolean canAct = listener != null && task.isActionable();
        holder.layoutActions.setVisibility(canAct ? View.VISIBLE : View.GONE);

        if (canAct) {
            holder.btnComplete.setOnClickListener(v ->
                    UiMotion.animateCompletion(holder.itemView,
                            () -> listener.onCompleteClick(task)));
            holder.btnSkip.setOnClickListener(v ->
                    UiMotion.animateSkip(holder.itemView,
                            () -> listener.onSkipClick(task)));
            UiMotion.attachPressAnimation(holder.btnComplete);
            UiMotion.attachPressAnimation(holder.btnSkip);
        }

        // Completed tasks: dim + hide actions
        if ("COMPLETED".equals(task.status)) {
            holder.itemView.setAlpha(0.6f);
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvMeta.setText(category + " | Completed ✓");
            holder.tvMeta.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.calm_green));
        } else if ("SKIPPED".equals(task.status)) {
            holder.itemView.setAlpha(0.5f);
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvMeta.setText(category + " | Skipped");
        } else {
            holder.itemView.setAlpha(1.0f);
        }
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    // ─────────────────────────────────────────────────────────────────────
    // CATEGORY COLOR MAPPING
    // ─────────────────────────────────────────────────────────────────────

    private int getCategoryColor(String category) {
        if (category == null) return R.color.primary;
        switch (category) {
            case "Mindfulness": return R.color.cat_mindfulness;
            case "Journaling":  return R.color.cat_journaling;
            case "Social":      return R.color.cat_social;
            case "Purpose":     return R.color.cat_purpose;
            case "Detox":       return R.color.cat_detox;
            case "Recovery":    return R.color.cat_recovery;
            case "Focus":       return R.color.cat_focus;
            default:            return R.color.primary;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // VIEW HOLDER
    // ─────────────────────────────────────────────────────────────────────

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        View viewCategoryStrip;
        TextView tvIcon, tvTitle, tvXp, tvDesc, tvWhy, tvMeta, tvMicro;
        MaterialButton btnComplete, btnSkip;
        View layoutActions;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            viewCategoryStrip = itemView.findViewById(R.id.view_category_strip);
            tvIcon = itemView.findViewById(R.id.tv_task_icon);
            tvTitle = itemView.findViewById(R.id.tv_task_title);
            tvXp = itemView.findViewById(R.id.tv_task_xp);
            tvDesc = itemView.findViewById(R.id.tv_task_desc);
            tvWhy = itemView.findViewById(R.id.tv_task_why);
            tvMeta = itemView.findViewById(R.id.tv_task_meta);
            tvMicro = itemView.findViewById(R.id.tv_task_micro);
            btnComplete = itemView.findViewById(R.id.btn_complete);
            btnSkip = itemView.findViewById(R.id.btn_skip);
            layoutActions = itemView.findViewById(R.id.layout_actions);
        }
    }
}
