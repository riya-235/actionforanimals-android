package org.a5calls.android.a5calls.controller;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.VisibleForTesting;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.FiveCallsApplication;
import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.adapter.OutcomeAdapter;
import org.a5calls.android.a5calls.databinding.ActivityRepCallBinding;
import org.a5calls.android.a5calls.model.AccountManager;
import org.a5calls.android.a5calls.model.AchievementManager;
import org.a5calls.android.a5calls.model.Contact;
import org.a5calls.android.a5calls.model.DatabaseHelper;
import org.a5calls.android.a5calls.model.FieldOffice;
import org.a5calls.android.a5calls.model.Issue;
import org.a5calls.android.a5calls.model.Outcome;
import org.a5calls.android.a5calls.model.Target;
import org.a5calls.android.a5calls.net.FiveCallsApi;
import org.a5calls.android.a5calls.util.AnalyticsManager;
import org.a5calls.android.a5calls.util.ScriptReplacements;
import org.a5calls.android.a5calls.util.MarkdownUtil;
import org.a5calls.android.a5calls.view.GridItemDecoration;
import org.a5calls.android.a5calls.view.AnimalsCounterView;
import org.a5calls.android.a5calls.view.AchievementCelebrationView;

import java.util.ArrayList;
import java.util.List;

import static org.a5calls.android.a5calls.controller.IssueActivity.KEY_ISSUE;

/**
 * Activity for corporate call campaigns - similar to RepCallActivity but for corporate targets
 */
public class CorporateCallActivity extends AppCompatActivity {
    private static final String TAG = "CorporateCallActivity";

    public static final String KEY_ADDRESS = "key_address";
    public static final String KEY_LOCATION_NAME = "key_location_name";
    public static final String KEY_ACTIVE_TARGET_INDEX = "active_target_index";

    private FiveCallsApi.CallRequestListener mStatusListener;
    private Issue mIssue;
    private int mActiveTargetIndex;
    private OutcomeAdapter outcomeAdapter;
    private Handler mainHandler;

    private ActivityRepCallBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRepCallBinding.inflate(getLayoutInflater());

        final String address = getIntent().getStringExtra(KEY_ADDRESS);
        mActiveTargetIndex = getIntent().getIntExtra(KEY_ACTIVE_TARGET_INDEX, 0);
        mIssue = getIntent().getParcelableExtra(KEY_ISSUE);
        mainHandler = new Handler(Looper.getMainLooper());

        if (mIssue == null || mIssue.targets == null || mIssue.targets.isEmpty()) {
            finish();
            return;
        }

        setContentView(binding.getRoot());

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(""); // Clean like iOS
        }

        // Set up API listener
        setupApiListener();

        // Set up UI
        setupIssueTitle();
        setupContactCard();
        setupPhoneSection();
        setupScript();
        setupOutcomeButtons(address);

        // Focus management
        binding.scrollView.setFocusableInTouchMode(true);
        binding.scrollView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        // Analytics
        Target target = mIssue.targets.get(mActiveTargetIndex);
        FiveCallsApplication.analyticsManager().trackPageview(String.format("/issue/%s/%s/", mIssue.slug, target.id), this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_rep_call, menu);

        // Get the Animals Counter view from the menu and refresh it
        MenuItem animalsCounterItem = menu.findItem(R.id.action_animals_counter);
        if (animalsCounterItem != null) {
            View actionView = animalsCounterItem.getActionView();
            if (actionView != null) {
                // Update the counter display
                updateAnimalsCounterInActionBar(actionView);

                // Set click listener for Your Impact dialog
                actionView.setOnClickListener(v -> {
                    YourImpactDialogFragment dialog = YourImpactDialogFragment.newInstance();
                    dialog.show(getSupportFragmentManager(), "YourImpactDialog");
                });
            }
        }

        return true;
    }

    @Override
    protected void onDestroy() {
        AppSingleton.getInstance(getApplicationContext()).getJsonController()
                .unregisterCallRequestListener(mStatusListener);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_ISSUE, mIssue);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check for pending achievements (like iOS ImpactManager)
        checkPendingAchievements();

        // Check if we should pulse the counter (from previous action)
        if (getIntent().getBooleanExtra("SHOULD_PULSE_COUNTER", false)) {
            // Clear the flag so it doesn't pulse again
            getIntent().removeExtra("SHOULD_PULSE_COUNTER");

            // Short delay to ensure menu is ready but feel connected to the action
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                animateMenuCounter();
            }, 300);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                returnToIssueWithoutPulse(); // Skip - no action taken
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100) { // Request code from launchNextTarget
            // Forward the result from the next activity back to IssueActivity
            if (data != null) {
                setResult(resultCode, data);
            } else {
                setResult(resultCode);
            }

            // Finish this activity so the result propagates
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        returnToIssueWithoutPulse(); // Skip - no action taken
    }

    private void setupApiListener() {
        mStatusListener = new FiveCallsApi.CallRequestListener() {
            @Override
            public void onRequestError() {
                returnToIssueWithServerError();
            }

            @Override
            public void onJsonError() {
                returnToIssueWithServerError();
            }

            @Override
            public void onReportReceived(int count, boolean donateOn) {
                // unused
            }

            @Override
            public void onCallReported() {
                // Don't automatically return to issue - let navigation logic handle it
            }
        };
        AppSingleton.getInstance(getApplicationContext())
                .getJsonController().registerCallRequestListener(mStatusListener);
    }

    private void triggerAnimalsCounterIncrement() {
        runOnUiThread(() -> {
            // Trigger haptic feedback
            triggerHapticFeedback();

            // Play success sound
            playSuccessSound();

            // Don't animate counter here since we're navigating away immediately
            // The destination screen will handle the pulse animation

            // Check for achievements based on total animals helped
            checkForAchievements();
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
            }
        } catch (Exception e) {
            // Silently handle haptic feedback errors
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
                // Try fallback sound
            }

            // Fallback to ringtone sound if notification failed
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_RINGTONE_URI);
                } catch (Exception e) {
                    // No fallback available
                }
            }

            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    mp.release();
                    return true; // Handle error
                });
                mediaPlayer.start();
            } else {
                // No system sounds available
            }
        } catch (Exception e) {
            // Silently handle sound errors
        }
    }

    private void animateMenuCounter() {
        try {
            // Find the counter view in the menu
            if (getSupportActionBar() != null) {
                View actionView = findViewById(R.id.action_animals_counter);
                if (actionView != null) {
                    TextView counterText = actionView.findViewById(R.id.animals_count);
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
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle animation errors
        }
    }

    private void checkForAchievements() {
        // Achievement checking now handled by AchievementManager after database operations
        // Celebrations are triggered automatically when achievements are unlocked
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
            // Silently handle counter update errors
        }
    }

    private void setupIssueTitle() {
        binding.issueTitle.setText(mIssue.name);
    }

    private void setupContactCard() {
        Target target = mIssue.targets.get(mActiveTargetIndex);
        
        // Set contact name
        binding.contactName.setText(target.name);
        
        // Set contact details (department and company)
        StringBuilder details = new StringBuilder();
        if (target.department != null && !target.department.isEmpty()) {
            details.append(target.department);
        }
        if (mIssue.corporateInfo != null && mIssue.corporateInfo.company != null) {
            if (details.length() > 0) {
                details.append(" • ");
            }
            details.append(mIssue.corporateInfo.company);
        }
        binding.contactDetails.setText(details.toString());

        // Set up avatar - use business icon for corporate targets
        binding.repImage.setImageResource(R.drawable.ic_business);
        binding.contactInitials.setVisibility(View.GONE);
    }

    private void setupPhoneSection() {
        Target target = mIssue.targets.get(mActiveTargetIndex);
        
        if (target.phone != null && !target.phone.isEmpty()) {
            binding.phoneNumber.setText(target.phone);
            binding.phoneNumber.setOnClickListener(v -> callPhoneNumber(target.phone));
        } else {
            // Hide phone section if no phone number
            binding.phoneSection.setVisibility(View.GONE);
        }
        
        // Hide local office section for corporate targets
        binding.fieldOfficeSection.setVisibility(View.GONE);
    }

    private void callPhoneNumber(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(android.net.Uri.parse("tel:" + phoneNumber));
        startActivity(intent);
    }

    private void setupScript() {
        String script = "";
        if (mIssue.actions != null && mIssue.actions.call != null) {
            script = mIssue.actions.call.script;
        } else {
            script = mIssue.reason;
        }

        String location = getIntent().getStringExtra(KEY_LOCATION_NAME);
        String userName = AccountManager.Instance.getUserName(this);

        // Use ScriptReplacements for corporate targets (no Contact object)
        String processedScript = ScriptReplacements.replacing(this, script, null, location, userName);
        
        // Process markdown using the same method as RepCallActivity
        MarkdownUtil.setUpScript(binding.callScript, processedScript, getApplicationContext());
        binding.callScript.setMovementMethod(LinkMovementMethod.getInstance());
        binding.callScript.setTextSize(AccountManager.Instance.getScriptTextSize(getApplicationContext()));
        
        // Make phone numbers and URLs clickable
        Linkify.addLinks(binding.callScript, Patterns.PHONE, "tel:");
        Linkify.addLinks(binding.callScript, Patterns.WEB_URL, "");
    }

    private void setupOutcomeButtons(String address) {
        // Always include Skip button regardless of backend outcomes
        List<Outcome> issueOutcomes = new ArrayList<>();
        if (mIssue.outcomeModels != null && !mIssue.outcomeModels.isEmpty()) {
            issueOutcomes.addAll(mIssue.outcomeModels);
        } else {
            // Add the standard outcomes if none provided by backend
            issueOutcomes.add(new Outcome(Outcome.Status.UNAVAILABLE));
            issueOutcomes.add(new Outcome(Outcome.Status.VOICEMAIL));
            issueOutcomes.add(new Outcome(Outcome.Status.CONTACT));
        }
        // Always add Skip as the last option
        issueOutcomes.add(new Outcome(Outcome.Status.SKIP));

        outcomeAdapter = new OutcomeAdapter(issueOutcomes, new OutcomeAdapter.Callback() {
            @Override
            public void onOutcomeClicked(Outcome outcome) {
                if (outcome.status == Outcome.Status.SKIP) {
                    // Skip doesn't send anything to backend, just navigate to next without pulsing
                    navigateToNextTargetOrComplete(false);
                } else {
                    // Normal outcomes report the call and then navigate with pulsing
                    reportCall(outcome, address);
                    navigateToNextTargetOrComplete(true);
                }
            }
        });

        binding.outcomeList.setLayoutManager(
                new GridLayoutManager(this, 2)); // 2 columns like iOS
        binding.outcomeList.setAdapter(outcomeAdapter);

        int gridPadding = (int) getResources().getDimension(R.dimen.grid_padding);
        binding.outcomeList.addItemDecoration(new GridItemDecoration(gridPadding, 2));
    }

    private void reportCall(Outcome outcome, String address) {
        // Trigger immediate feedback (pulse, haptic, sound) before server call
        triggerAnimalsCounterIncrement();

        outcomeAdapter.setEnabled(false);
        Target target = mIssue.targets.get(mActiveTargetIndex);
        String contactId = target.id != null ? target.id : target.name;
        String contactName = target.name;

        String categories = DatabaseHelper.categoriesToString(mIssue.categories);
        AppSingleton.getInstance(getApplicationContext()).getDatabaseHelper().addCall(mIssue.id,
                mIssue.name, contactId, contactName, outcome.status.toString(), address, mIssue.animalsHelpedPerAction, categories, DatabaseHelper.ActionTypes.CALL);

        // Check for newly unlocked achievements
        AchievementManager.getInstance(this).checkAfterAction("call", mIssue.animalsHelpedPerAction, categories);

        AppSingleton.getInstance(getApplicationContext()).getJsonController().reportCall(
                mIssue.id, contactId, outcome.label, address);
    }

    private void navigateToNextTargetOrComplete(boolean shouldPulse) {
        // Find the next target in the list
        int nextTargetIndex = findNextTarget();

        if (nextTargetIndex != -1) {
            // Navigate to next target
            launchNextTarget(nextTargetIndex, shouldPulse);
        } else {
            // No more targets, return to issue list
            returnToIssueInternal(shouldPulse);
        }
    }

    private int findNextTarget() {
        // Simply go to the next target in the list, regardless of call history
        int nextIndex = mActiveTargetIndex + 1;
        if (nextIndex < mIssue.targets.size()) {
            return nextIndex;
        }

        // No more targets after current one
        return -1;
    }

    private void launchNextTarget(int nextTargetIndex, boolean shouldPulse) {
        Intent intent = new Intent(this, CorporateCallActivity.class);
        intent.putExtra(KEY_ISSUE, mIssue);
        intent.putExtra(KEY_ACTIVE_TARGET_INDEX, nextTargetIndex);
        intent.putExtra(KEY_ADDRESS, getIntent().getStringExtra(KEY_ADDRESS));
        intent.putExtra(KEY_LOCATION_NAME, getIntent().getStringExtra(KEY_LOCATION_NAME));
        intent.putExtra("SHOULD_PULSE_COUNTER", shouldPulse);

        // Copy over other extras that might be needed
        if (getIntent().hasExtra(IssueActivity.KEY_IS_LOW_ACCURACY)) {
            intent.putExtra(IssueActivity.KEY_IS_LOW_ACCURACY,
                    getIntent().getBooleanExtra(IssueActivity.KEY_IS_LOW_ACCURACY, false));
        }
        if (getIntent().hasExtra(IssueActivity.KEY_DONATE_IS_ON)) {
            intent.putExtra(IssueActivity.KEY_DONATE_IS_ON,
                    getIntent().getBooleanExtra(IssueActivity.KEY_DONATE_IS_ON, false));
        }

        // Use startActivityForResult instead of startActivity so we can forward the result
        startActivityForResult(intent, 100);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void returnToIssue() {
        returnToIssueInternal(true); // Pulse when action was taken
    }

    private void returnToIssueWithoutPulse() {
        returnToIssueInternal(false); // No pulse when skipping/backing out
    }

    private void returnToIssueInternal(boolean shouldPulse) {
        if (isFinishing()) {
            return;
        }

        // Return to existing IssueActivity with result data
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SHOULD_PULSE_COUNTER", shouldPulse);

        setResult(RESULT_OK, resultIntent);
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void returnToIssueWithServerError() {
        if (isFinishing()) {
            return;
        }
        Snackbar.make(binding.getRoot(), "Error reporting call. Please try again.", Snackbar.LENGTH_LONG).show();
        returnToIssue();
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
}
