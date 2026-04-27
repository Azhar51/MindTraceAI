package com.mindtrace.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mindtrace.ai.R;

public class FocusBlockActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_focus_block);

        String packageName = getIntent().getStringExtra("BLOCKED_PACKAGE");
        
        TextView tvBlockedApp = findViewById(R.id.tv_blocked_app);
        if (packageName != null) {
            tvBlockedApp.setText("You are trying to open a restricted app:\n" + packageName);
        }

        Button btnBackToWork = findViewById(R.id.btn_back_to_work);
        btnBackToWork.setOnClickListener(v -> {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // Prevent going back to the blocked app
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
        finish();
    }
}
