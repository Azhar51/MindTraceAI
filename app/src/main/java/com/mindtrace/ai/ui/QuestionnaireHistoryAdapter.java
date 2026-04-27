package com.mindtrace.ai.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mindtrace.ai.R;
import com.mindtrace.ai.database.entity.QuestionnaireResponse;
import com.mindtrace.ai.util.MoodMapper;

import java.util.ArrayList;
import java.util.List;

public class QuestionnaireHistoryAdapter extends RecyclerView.Adapter<QuestionnaireHistoryAdapter.ViewHolder> {
    private final List<QuestionnaireResponse> responses = new ArrayList<>();

    public void setResponses(List<QuestionnaireResponse> items) {
        responses.clear();
        if (items != null) {
            responses.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_questionnaire_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuestionnaireResponse response = responses.get(position);
        String mood = response.mood != null ? response.mood : MoodMapper.MOOD_NEUTRAL;

        // ── Mood display with emoji + color ──
        holder.tvMood.setText(MoodMapper.getMoodEmoji(mood) + " " + mood);
        holder.tvMood.setTextColor(MoodMapper.getMoodColor(mood));

        holder.tvTimestamp.setText(UiFormatting.formatFullDate(response.timestamp));

        // ── Primary line: risk-aware metrics with level emojis ──
        float risk = MoodMapper.computeEmotionalRisk(mood, response.energyLevel, response.focusLevel);
        holder.tvPrimary.setText(
                "Stress " + response.stressLevel + "/5 · "
                        + "Loneliness " + response.lonelinessLevel + "/5 · "
                        + MoodMapper.riskToLabel(risk) + " risk"
        );

        // ── Secondary line: sleep + level emojis + coping hint ──
        holder.tvSecondary.setText(
                UiFormatting.formatSleep(response.sleepHours)
                        + " · Focus " + MoodMapper.getLevelEmoji(response.focusLevel)
                        + " · Energy " + MoodMapper.getLevelEmoji(response.energyLevel)
        );
    }

    @Override
    public int getItemCount() {
        return responses.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMood;
        final TextView tvTimestamp;
        final TextView tvPrimary;
        final TextView tvSecondary;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMood = itemView.findViewById(R.id.tv_history_mood);
            tvTimestamp = itemView.findViewById(R.id.tv_history_timestamp);
            tvPrimary = itemView.findViewById(R.id.tv_history_primary);
            tvSecondary = itemView.findViewById(R.id.tv_history_secondary);
        }
    }
}

