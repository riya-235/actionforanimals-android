package org.a5calls.android.a5calls.controller;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.model.AccountManager;
import org.a5calls.android.a5calls.model.DatabaseHelper;
import org.a5calls.android.a5calls.model.AchievementManager;
import org.a5calls.android.a5calls.model.Issue;
import org.a5calls.android.a5calls.model.Target;
import org.a5calls.android.a5calls.util.ScriptReplacements;
import org.a5calls.android.a5calls.view.AnimalsCounterView;
import org.a5calls.android.a5calls.view.AchievementCelebrationView;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for composing emails for corporate campaigns.
 */
public class EmailComposerActivity extends AppCompatActivity {
    public static final String KEY_LOCATION_NAME = "key_location_name";
    public static final String KEY_ACTIVE_TARGET_INDEX = "active_target_index";

    private Issue mIssue;
    private List<Target> mTargets;
    private boolean mIsBatchEmail;
    private int mActiveTargetIndex;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_composer);
        
        mIssue = getIntent().getParcelableExtra("issue");
        mTargets = getIntent().getParcelableArrayListExtra("targets");
        mIsBatchEmail = getIntent().getBooleanExtra("is_batch_email", false);
        mActiveTargetIndex = getIntent().getIntExtra(KEY_ACTIVE_TARGET_INDEX, 0);
        
        setupActionBar();
        setupEmailContent();
        setupComposeButton();
        setupActionButtons();
    }
    
    private void setupActionBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.email_campaign_title));
        }
    }
    
    private void setupEmailContent() {
        TextView issueTitle = findViewById(R.id.issue_title);
        TextView bodyText = findViewById(R.id.email_body);
        ImageView contactIcon = findViewById(R.id.contact_icon);
        TextView contactName = findViewById(R.id.contact_name);
        TextView contactDetails = findViewById(R.id.contact_details);
        
        // Set issue title
        issueTitle.setText(mIssue.name);
        
        // Set contact/company info
        if (mIsBatchEmail) {
            // Batch email - show company info
            contactIcon.setImageResource(R.drawable.ic_business);
            if (mIssue.corporateInfo != null) {
                contactName.setText(mIssue.corporateInfo.company);
                if (mIssue.corporateInfo.industry != null) {
                    contactDetails.setText(mIssue.corporateInfo.industry);
                }
            }
        } else {
            // Individual email - show current target info
            contactIcon.setImageResource(R.drawable.ic_person);
            if (mTargets != null && !mTargets.isEmpty() && mActiveTargetIndex < mTargets.size()) {
                Target target = mTargets.get(mActiveTargetIndex);
                contactName.setText(target.name);
                if (target.department != null) {
                    contactDetails.setText(target.department);
                }
            }
        }
        
        // Set email body (processed template)
        String emailBody = getEmailBody();
        bodyText.setText(emailBody);
    }
    
    private void setupComposeButton() {
        TextView composeButton = findViewById(R.id.compose_email_button);
        composeButton.setOnClickListener(v -> launchEmailIntent());
    }
    
    private void setupActionButtons() {
        RecyclerView actionButtonsList = findViewById(R.id.action_buttons_list);
        
        // Create action buttons data
        List<ActionButton> actionButtons = new ArrayList<>();
        actionButtons.add(new ActionButton("sent", getString(R.string.sent_email_button)));
        actionButtons.add(new ActionButton("skip", getString(R.string.skip_button)));
        
        // Set up RecyclerView with GridLayoutManager (2 columns like calling UI)
        actionButtonsList.setLayoutManager(new GridLayoutManager(this, 2));
        actionButtonsList.setAdapter(new ActionButtonAdapter(actionButtons, this::onActionButtonClick));
    }
    
    private void onActionButtonClick(String action) {
        if ("sent".equals(action)) {
            trackEmailAction(action);
            navigateToNextTargetOrComplete(true); // Pulse for sent action
        } else {
            // Skip action
            navigateToNextTargetOrComplete(false); // No pulse for skip
        }
    }
    
    // Simple data class for action buttons
    private static class ActionButton {
        public String action;
        public String text;
        
        public ActionButton(String action, String text) {
            this.action = action;
            this.text = text;
        }
    }
    
    // Simple adapter for action buttons
    private static class ActionButtonAdapter extends RecyclerView.Adapter<ActionButtonAdapter.ViewHolder> {
        private List<ActionButton> buttons;
        private ActionButtonCallback callback;
        
        public interface ActionButtonCallback {
            void onActionButtonClick(String action);
        }
        
        public ActionButtonAdapter(List<ActionButton> buttons, ActionButtonCallback callback) {
            this.buttons = buttons;
            this.callback = callback;
        }
        
        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            return new ViewHolder(android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_outcome, parent, false));
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ActionButton button = buttons.get(position);
            holder.button.setText(button.text);
            holder.button.setOnClickListener(v -> callback.onActionButtonClick(button.action));
        }
        
        @Override
        public int getItemCount() {
            return buttons.size();
        }
        
        public static class ViewHolder extends RecyclerView.ViewHolder {
            Button button;
            
            public ViewHolder(android.view.View itemView) {
                super(itemView);
                button = itemView.findViewById(R.id.outcome_button);
            }
        }
    }
    
    private void launchEmailIntent() {
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("message/rfc822");
        
        // Set recipients based on campaign type
        if (mIsBatchEmail) {
            String[] allEmails = getAllTargetEmails();
            emailIntent.putExtra(Intent.EXTRA_EMAIL, allEmails);
        } else {
            // For individual emails, use the current target
            if (mTargets != null && !mTargets.isEmpty() && mActiveTargetIndex < mTargets.size()) {
                String[] singleEmail = {mTargets.get(mActiveTargetIndex).email};
                emailIntent.putExtra(Intent.EXTRA_EMAIL, singleEmail);
            }
        }
        
        // Set subject and body
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getEmailSubject());
        emailIntent.putExtra(Intent.EXTRA_TEXT, getEmailBody());
        
        startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email_chooser)));
    }
    
    private String getEmailSubject() {
        if (mIssue.actions != null && mIssue.actions.email != null) {
            return mIssue.actions.email.subject;
        }
        return getString(R.string.email_campaign_title);
    }
    
    private String getEmailBody() {
        String template;
        if (mIssue.actions != null && mIssue.actions.email != null) {
            template = mIssue.actions.email.template;
        } else {
            template = mIssue.reason;
        }
        
        // Process script replacements (same as call screen)
        String location = getIntent().getStringExtra(KEY_LOCATION_NAME);
        String userName = AccountManager.Instance.getUserName(this);
        
        return ScriptReplacements.replacing(this, template, null, location, userName);
    }
    
    private String[] getAllTargetEmails() {
        List<String> emails = new ArrayList<>();
        if (mTargets != null) {
            for (Target target : mTargets) {
                if (target.email != null && !target.email.isEmpty()) {
                    emails.add(target.email);
                }
            }
        }
        return emails.toArray(new String[0]);
    }
    
    private void trackEmailAction(String action) {
        if ("sent".equals(action)) {
            // Track the email action in the database (same as calls)
            String contactId;
            String contactName;
            
            if (mIsBatchEmail) {
                // For batch emails, use the first target's ID (matching iOS behavior)
                if (mTargets != null && !mTargets.isEmpty()) {
                    contactId = mTargets.get(0).id != null ? mTargets.get(0).id : mTargets.get(0).name;
                    contactName = mTargets.get(0).name;
                } else {
                    contactId = "company-" + mIssue.id;
                    contactName = mIssue.corporateInfo != null ? mIssue.corporateInfo.company : "Corporate Target";
                }
            } else {
                // For individual emails, use the current target ID
                if (mTargets != null && !mTargets.isEmpty() && mActiveTargetIndex < mTargets.size()) {
                    Target currentTarget = mTargets.get(mActiveTargetIndex);
                    contactId = currentTarget.id != null ? currentTarget.id : currentTarget.name;
                    contactName = currentTarget.name;
                } else {
                    contactId = "email_target";
                    contactName = "Email Target";
                }
            }
            
            String categories = DatabaseHelper.categoriesToString(mIssue.categories);
            AppSingleton.getInstance(getApplicationContext()).getDatabaseHelper().addCall(
                mIssue.id, mIssue.name, contactId, contactName, "contact", "email", mIssue.animalsHelpedPerAction, categories, DatabaseHelper.ActionTypes.EMAIL);

            // Check for newly unlocked achievements
            AchievementManager.getInstance(this).checkAfterAction("email", mIssue.animalsHelpedPerAction, categories);

            // Report to the server (same as calls)
            AppSingleton.getInstance(getApplicationContext()).getJsonController().reportCall(
                mIssue.id, contactId, "contact", "email");

            // Trigger haptic feedback and sound directly (like iOS)
            triggerAnimalsCounterIncrement();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("EmailComposerActivity", "onResume called");

        // Check for pending achievements (like iOS ImpactManager)
        checkPendingAchievements();

        // Check if we should pulse the counter (from previous action)
        boolean shouldPulse = getIntent().getBooleanExtra("SHOULD_PULSE_COUNTER", false);
        Log.d("EmailComposerActivity", "onResume: SHOULD_PULSE_COUNTER = " + shouldPulse);

        if (shouldPulse) {
            // Clear the flag so it doesn't pulse again
            getIntent().removeExtra("SHOULD_PULSE_COUNTER");

            Log.d("EmailComposerActivity", "onResume: Scheduling pulse animation");
            // Short delay to ensure menu is ready but feel connected to the action
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d("EmailComposerActivity", "onResume: Executing pulse animation");
                animateMenuCounter();
            }, 300);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_email_composer, menu);

        // Set up the animals counter in the action bar
        MenuItem counterItem = menu.findItem(R.id.menu_animals_counter);
        if (counterItem != null) {
            View actionView = counterItem.getActionView();
            if (actionView != null) {
                // Update the counter display
                updateAnimalsCounterInActionBar(actionView);

                // The action view is the animals counter layout
                actionView.setOnClickListener(v -> {
                    // Open Your Impact dialog when counter is clicked
                    YourImpactDialogFragment dialog = YourImpactDialogFragment.newInstance();
                    dialog.show(getSupportFragmentManager(), "YourImpactDialog");
                });
            }
        }

        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    /**
     * Updates the animals counter display in the action bar
     */
    private void updateAnimalsCounterInActionBar(View actionView) {
        try {
            TextView animalsCountTextView = actionView.findViewById(R.id.animals_count);
            if (animalsCountTextView != null) {
                DatabaseHelper db = AppSingleton.getInstance(this).getDatabaseHelper();
                int totalAnimalsHelped = db.getTotalAnimalsHelped();
                animalsCountTextView.setText(String.valueOf(totalAnimalsHelped));
            }
        } catch (Exception e) {
            Log.e("EmailComposerActivity", "Error updating animals counter", e);
        }
    }

    private void triggerAnimalsCounterIncrement() {
        Log.d("EmailComposerActivity", "triggerAnimalsCounterIncrement called");
        runOnUiThread(() -> {
            Log.d("EmailComposerActivity", "Triggering counter feedback directly");

            // Trigger haptic feedback
            triggerHapticFeedback();

            // Play success sound
            playSuccessSound();

            // Don't animate counter here since we're navigating away immediately
            // The destination screen will handle the pulse animation
        });
    }

    private void triggerHapticFeedback() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // Use impact feedback pattern similar to iOS success haptic
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    VibrationEffect effect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE);
                    vibrator.vibrate(effect);
                } else {
                    vibrator.vibrate(50);
                }
                Log.d("EmailComposerActivity", "Haptic feedback triggered");
            }
        } catch (Exception e) {
            Log.e("EmailComposerActivity", "Error triggering haptic feedback", e);
        }
    }

    private void playSuccessSound() {
        try {
            // Try multiple sound sources for better compatibility
            MediaPlayer mediaPlayer = null;

            // First try default notification sound
            try {
                mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            } catch (Exception e) {
                Log.d("EmailComposerActivity", "Default notification URI failed: " + e.getMessage());
            }

            // Fallback to ringtone sound if notification failed
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_RINGTONE_URI);
                } catch (Exception e) {
                    Log.d("EmailComposerActivity", "Default ringtone URI failed: " + e.getMessage());
                }
            }

            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e("EmailComposerActivity", "MediaPlayer error: " + what + ", " + extra);
                    mp.release();
                    return true; // Handle error
                });
                mediaPlayer.start();
                Log.d("EmailComposerActivity", "Success sound played");
            } else {
                Log.w("EmailComposerActivity", "No system sounds available, skipping sound");
            }
        } catch (Exception e) {
            Log.e("EmailComposerActivity", "Error playing success sound", e);
        }
    }

    private void animateMenuCounter() {
        try {
            Log.d("EmailComposerActivity", "animateMenuCounter: Starting animation");
            // Find the counter view in the menu
            if (getSupportActionBar() != null) {
                View actionView = findViewById(R.id.menu_animals_counter);
                Log.d("EmailComposerActivity", "animateMenuCounter: actionView = " + actionView);
                if (actionView != null) {
                    TextView counterText = actionView.findViewById(R.id.animals_count);
                    Log.d("EmailComposerActivity", "animateMenuCounter: counterText = " + counterText);
                    if (counterText != null) {
                        // More prominent pulse animation
                        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(counterText, "scaleX", 1.0f, 1.5f);
                        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(counterText, "scaleY", 1.0f, 1.5f);
                        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(counterText, "scaleX", 1.5f, 1.0f);
                        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(counterText, "scaleY", 1.5f, 1.0f);

                        scaleUpX.setDuration(500);
                        scaleUpY.setDuration(500);
                        scaleDownX.setDuration(500);
                        scaleDownY.setDuration(500);

                        scaleDownX.setInterpolator(new OvershootInterpolator());
                        scaleDownY.setInterpolator(new OvershootInterpolator());

                        AnimatorSet scaleUp = new AnimatorSet();
                        scaleUp.playTogether(scaleUpX, scaleUpY);

                        AnimatorSet scaleDown = new AnimatorSet();
                        scaleDown.playTogether(scaleDownX, scaleDownY);

                        AnimatorSet pulseAnimation = new AnimatorSet();
                        pulseAnimation.playSequentially(scaleUp, scaleDown);
                        pulseAnimation.start();

                        Log.d("EmailComposerActivity", "Menu counter animation triggered");
                    }
                }
            }
        } catch (Exception e) {
            Log.e("EmailComposerActivity", "Error animating menu counter", e);
        }
    }

    /**
     * Check for pending achievements and show celebration dialog (like iOS ImpactManager)
     */
    private void checkPendingAchievements() {
        AchievementManager.PendingAchievement pending = AchievementManager.getInstance().getPendingAchievement();
        if (pending != null) {
            AchievementCelebrationView.show(this, pending.title, pending.subtitle, pending.icon);
        }
    }

    private void navigateToNextTargetOrComplete(boolean shouldPulse) {
        if (mIsBatchEmail) {
            // For batch emails, always return to issue after action
            returnToIssueInternal(shouldPulse);
            return;
        }

        // For individual emails, find the next target
        int nextTargetIndex = findNextTarget();
        Log.d("EmailComposerActivity", "navigateToNextTargetOrComplete: nextTargetIndex = " + nextTargetIndex + ", shouldPulse = " + shouldPulse);

        if (nextTargetIndex != -1) {
            // Navigate to next target
            Log.d("EmailComposerActivity", "navigateToNextTargetOrComplete: Launching next target at index " + nextTargetIndex + " with shouldPulse = " + shouldPulse);
            launchNextTarget(nextTargetIndex, shouldPulse);
        } else {
            // No more targets, return to issue list
            Log.d("EmailComposerActivity", "navigateToNextTargetOrComplete: No more targets, calling returnToIssueInternal with shouldPulse = " + shouldPulse);
            returnToIssueInternal(shouldPulse);
        }
    }

    private int findNextTarget() {
        // Simply go to the next target in the list
        int nextIndex = mActiveTargetIndex + 1;
        if (nextIndex < mTargets.size()) {
            return nextIndex;
        }

        // No more targets after current one
        return -1;
    }

    private void launchNextTarget(int nextTargetIndex, boolean shouldPulse) {
        Log.d("EmailComposerActivity", "launchNextTarget: Starting next EmailComposerActivity with shouldPulse = " + shouldPulse);
        Intent intent = new Intent(this, EmailComposerActivity.class);
        intent.putExtra("issue", mIssue);
        intent.putParcelableArrayListExtra("targets", (ArrayList) mTargets);
        intent.putExtra("is_batch_email", mIsBatchEmail);
        intent.putExtra(KEY_ACTIVE_TARGET_INDEX, nextTargetIndex);
        intent.putExtra("SHOULD_PULSE_COUNTER", shouldPulse);
        intent.putExtra(KEY_LOCATION_NAME, getIntent().getStringExtra(KEY_LOCATION_NAME));

        // Use startActivityForResult instead of startActivity so we can forward the result
        startActivityForResult(intent, 100);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100) { // Request code from launchNextTarget
            Log.d("EmailComposerActivity", "onActivityResult: Got result " + resultCode + " from next EmailComposerActivity");

            // Forward the result from the next activity back to IssueActivity
            if (data != null) {
                setResult(resultCode, data);
                Log.d("EmailComposerActivity", "onActivityResult: Forwarding result " + resultCode + " with data to IssueActivity");
            } else {
                setResult(resultCode);
                Log.d("EmailComposerActivity", "onActivityResult: Forwarding result " + resultCode + " without data to IssueActivity");
            }

            // Finish this activity so the result propagates
            finish();
        }
    }

    private void returnToIssueInternal(boolean shouldPulse) {
        if (isFinishing()) {
            return;
        }

        // Return to existing IssueActivity with result data
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SHOULD_PULSE_COUNTER", shouldPulse);
        Log.d("EmailComposerActivity", "returnToIssueInternal: Setting SHOULD_PULSE_COUNTER = " + shouldPulse + " in result");

        setResult(RESULT_OK, resultIntent);
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
