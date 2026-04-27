package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.R;

public class OverUsageAlertActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_over_usage_alert);

        Button btn5Min = findViewById(R.id.btn_5_min);
        Button btn15Min = findViewById(R.id.btn_15_min);
        Button btn30Min = findViewById(R.id.btn_30_min);
        Button btn1Hour = findViewById(R.id.btn_1_hour);
        TextView btnDismiss = findViewById(R.id.btn_dismiss);

        btn5Min.setOnClickListener(v -> grantTime(5));
        btn15Min.setOnClickListener(v -> grantTime(15));
        btn30Min.setOnClickListener(v -> grantTime(30));
        btn1Hour.setOnClickListener(v -> grantTime(60));
        
        btnDismiss.setOnClickListener(v -> {
            Toast.makeText(this, "Reminders disabled for today.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void grantTime(int minutes) {
        // Logic to add 'minutes' to the allowed time
        // This would interact with SharedPreferences or the UsageEngine
        Toast.makeText(this, "Granted " + minutes + " more minutes.", Toast.LENGTH_SHORT).show();
        
        // Return to home screen or resume the previous app
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        
        finish();
    }
    
    @Override
    public void onBackPressed() {
        // Force the user to choose an option, do not let them just press back to dismiss
        Toast.makeText(this, "Please select an option.", Toast.LENGTH_SHORT).show();
    }
}
