package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.mindtrace.ai.R;

public class FocusPanelActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_focus_panel);

        CardView cardA11y = findViewById(R.id.card_a11y_permission);
        
        cardA11y.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open Accessibility Settings
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        });
    }
}
