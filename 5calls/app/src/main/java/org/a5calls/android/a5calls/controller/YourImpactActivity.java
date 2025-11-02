package org.a5calls.android.a5calls.controller;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.FiveCallsApplication;
import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.model.DatabaseHelper;

/**
 * Your Impact Activity - shows user's impact statistics
 * Matches the iOS YourImpact screen functionality
 */
public class YourImpactActivity extends AppCompatActivity {
    private static final String TAG = "YourImpactActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "YourImpactActivity onCreate started");

        // For now, just show a simple placeholder
        // TODO: Create proper layout with impact statistics, achievements, etc.
        setContentView(R.layout.activity_your_impact);
        Log.d(TAG, "Layout set successfully");

        // Hide the ActionBar to use our custom header
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setupContent();
        setupDoneButton();

        // Track analytics
        FiveCallsApplication.analyticsManager().trackPageview("/impact/", this);
    }

    private void setupContent() {
        DatabaseHelper db = AppSingleton.getInstance(this).getDatabaseHelper();
        int totalAnimalsHelped = db.getTotalAnimalsHelped();
        TextView animalsHelpedText = findViewById(R.id.animals_helped_text);
        TextView impactMessageText = findViewById(R.id.impact_message_text);

        if (animalsHelpedText != null) {
            animalsHelpedText.setText(String.valueOf(totalAnimalsHelped));
        }

        // Set dynamic impact message like iOS
        if (impactMessageText != null) {
            impactMessageText.setText(getImpactMessage(totalAnimalsHelped));
        }
    }

    private void setupDoneButton() {
        android.widget.FrameLayout doneButton = findViewById(R.id.done_button);
        Log.d(TAG, "Done button found: " + (doneButton != null));
        if (doneButton != null) {
            doneButton.setOnClickListener(v -> {
                Log.d(TAG, "Done button clicked!");
                finish();
            });
        }
    }

    private String getImpactMessage(int count) {
        if (count == 0) {
            return "Ready to make your first impact?";
        } else if (count <= 10) {
            return "Every action makes a difference";
        } else if (count <= 100) {
            return "You've helped an entire sanctuary";
        } else if (count <= 500) {
            return "You're changing the world for animals";
        } else {
            return "You're an animal advocacy champion";
        }
    }


    @Override
    public void finish() {
        super.finish();
        // Apply slide-out animation from top to bottom when closing
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }
}