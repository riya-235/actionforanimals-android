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
import android.text.method.LinkMovementMethod;
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
import org.a5calls.android.a5calls.model.Contact;
import org.a5calls.android.a5calls.model.DatabaseHelper;
import org.a5calls.android.a5calls.model.FieldOffice;
import org.a5calls.android.a5calls.model.Issue;
import org.a5calls.android.a5calls.model.Outcome;
import org.a5calls.android.a5calls.net.FiveCallsApi;
import org.a5calls.android.a5calls.util.AnalyticsManager;
import org.a5calls.android.a5calls.util.ScriptReplacements;
import org.a5calls.android.a5calls.util.MarkdownUtil;
import org.a5calls.android.a5calls.view.GridItemDecoration;
import org.a5calls.android.a5calls.view.AnimalsCounterView;
import org.a5calls.android.a5calls.view.AchievementCelebrationView;
import org.a5calls.android.a5calls.manager.AchievementManager;

import java.util.ArrayList;
import java.util.List;

import static org.a5calls.android.a5calls.controller.IssueActivity.KEY_ISSUE;

/**
 * iOS-style activity for showing a script and logging calls - matches AnimalPolicyContactDetail.swift
 */
public class RepCallActivity extends AppCompatActivity {
    private static final String TAG = "RepCallActivity";

    public static final String KEY_ADDRESS = "key_address";
    public static final String KEY_LOCATION_NAME = "key_location_name";
    public static final String KEY_ACTIVE_CONTACT_INDEX = "active_contact_index";
    private static final String KEY_LOCAL_OFFICES_EXPANDED = "local_offices_expanded";

    private FiveCallsApi.CallRequestListener mStatusListener;
    private Issue mIssue;
    private int mActiveContactIndex;
    private OutcomeAdapter outcomeAdapter;
    private Handler mainHandler;
    private PopupWindow fieldOfficePopup;

    private ActivityRepCallBinding binding;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRepCallBinding.inflate(getLayoutInflater());

        final String address = getIntent().getStringExtra(KEY_ADDRESS);
        mActiveContactIndex = getIntent().getIntExtra(KEY_ACTIVE_CONTACT_INDEX, 0);
        mIssue = getIntent().getParcelableExtra(KEY_ISSUE);
        mainHandler = new Handler(Looper.getMainLooper());

        if (mIssue == null) {
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
        setupLocalOfficesSection(savedInstanceState);
        setupScript();
        setupOutcomeButtons(address);

        // Focus management
        binding.scrollView.setFocusableInTouchMode(true);
        binding.scrollView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        // Analytics
        Contact contact = mIssue.contacts.get(mActiveContactIndex);
        FiveCallsApplication.analyticsManager().trackPageview(String.format("/issue/%s/%s/", mIssue.slug, contact.id), this);
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

        // Clean up popup
        if (fieldOfficePopup != null && fieldOfficePopup.isShowing()) {
            fieldOfficePopup.dismiss();
        }

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_ISSUE, mIssue);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                returnToIssue();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        returnToIssue();
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
    protected void onPause() {
        super.onPause();
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
        Log.d("RepCallActivity", "triggerAnimalsCounterIncrement called");
        runOnUiThread(() -> {
            Log.d("RepCallActivity", "Triggering counter feedback directly");

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
                Log.d("RepCallActivity", "Haptic feedback triggered");
            }
        } catch (Exception e) {
            Log.e("RepCallActivity", "Error triggering haptic feedback", e);
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
                Log.d("RepCallActivity", "Default notification URI failed: " + e.getMessage());
            }

            // Fallback to ringtone sound if notification failed
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_RINGTONE_URI);
                } catch (Exception e) {
                    Log.d("RepCallActivity", "Default ringtone URI failed: " + e.getMessage());
                }
            }

            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e("RepCallActivity", "MediaPlayer error: " + what + ", " + extra);
                    mp.release();
                    return true; // Handle error
                });
                mediaPlayer.start();
                Log.d("RepCallActivity", "Success sound played");
            } else {
                Log.w("RepCallActivity", "No system sounds available, skipping sound");
            }
        } catch (Exception e) {
            Log.e("RepCallActivity", "Error playing success sound", e);
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

                        Log.d("RepCallActivity", "Menu counter animation triggered");
                    }
                }
            }
        } catch (Exception e) {
            Log.e("RepCallActivity", "Error animating menu counter", e);
        }
    }

    private void checkForAchievements() {
        // Achievement checking is now handled automatically in DatabaseHelper.addCall()
        // The achievement system will show celebrations for newly unlocked achievements
        Log.d("RepCallActivity", "Achievement checking handled by DatabaseHelper achievement system");
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
            Log.e("RepCallActivity", "Error updating animals counter", e);
        }
    }

    private void setupIssueTitle() {
        binding.issueTitle.setText(mIssue.name);
    }

    private void setupContactCard() {
        Contact contact = mIssue.contacts.get(mActiveContactIndex);

        // Set contact name
        binding.contactName.setText(contact.name);

        // Set contact details/description
        String contactDetails = contact.getDescription(getResources());
        if (TextUtils.isEmpty(contactDetails)) {
            binding.contactDetails.setVisibility(View.GONE);
        } else {
            binding.contactDetails.setText(contactDetails);
            binding.contactDetails.setVisibility(View.VISIBLE);
        }

        // Load contact photo
        loadContactPhoto(contact);
    }

    private void loadContactPhoto(Contact contact) {
        if (!TextUtils.isEmpty(contact.photoURL)) {
            // Load photo from URL
            binding.contactInitials.setVisibility(View.GONE);

            Glide.with(getApplicationContext())
                    .load(contact.photoURL)
                    .centerCrop()
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.baseline_person_52)
                    .into(binding.repImage);
        } else {
            // Show initials fallback
            String initials = getContactInitials(contact.name);
            if (!TextUtils.isEmpty(initials)) {
                binding.contactInitials.setText(initials);
                binding.contactInitials.setVisibility(View.VISIBLE);

                // Hide the photo ImageView's content
                binding.repImage.setImageDrawable(null);
            }
        }
    }

    private String getContactInitials(String name) {
        if (TextUtils.isEmpty(name)) {
            return "?";
        }

        String[] words = name.trim().split("\\s+");
        if (words.length == 1) {
            return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase();
        } else {
            String initials = "";
            for (int i = 0; i < Math.min(2, words.length); i++) {
                if (!words[i].isEmpty()) {
                    initials += words[i].charAt(0);
                }
            }
            return initials.toUpperCase();
        }
    }

    private void setupPhoneSection() {
        Contact contact = mIssue.contacts.get(mActiveContactIndex);

        // Set phone number
        binding.phoneNumber.setText(contact.phone);

        // Make phone number clickable
        binding.phoneNumber.setOnClickListener(v -> callPhoneNumber(contact.phone));
        binding.phoneNumber.setOnLongClickListener(v -> {
            copyPhoneNumber(contact.phone);
            return true;
        });

        // Set up local office button (iOS-style ellipsis menu)
        if (contact.field_offices == null || contact.field_offices.length == 0) {
            binding.localOfficeButton.setVisibility(View.GONE);
        } else {
            binding.localOfficeButton.setVisibility(View.VISIBLE);
            binding.localOfficeButton.setOnClickListener(v -> {
                showLocalOfficePopupMenu(v, contact);
            });
        }
    }

    private void setupLocalOfficesSection(@Nullable Bundle savedInstanceState) {
        boolean expandLocalOffices = false;
        if (savedInstanceState != null) {
            expandLocalOffices = savedInstanceState.getBoolean(KEY_LOCAL_OFFICES_EXPANDED, false);
        }

        // Local offices are now handled via popup menu, no inline expansion needed
        binding.fieldOfficeSection.setVisibility(View.GONE);
    }

    private void showLocalOfficePopupMenu(View anchor, Contact contact) {
        // Dismiss any existing popup
        if (fieldOfficePopup != null && fieldOfficePopup.isShowing()) {
            fieldOfficePopup.dismiss();
        }

        // Create popup content view
        View popupView = getLayoutInflater().inflate(R.layout.dialog_field_offices, null);
        LinearLayout officesList = popupView.findViewById(R.id.offices_list);

        // Add each field office with iOS-style layout
        for (int i = 0; i < contact.field_offices.length; i++) {
            FieldOffice office = contact.field_offices[i];

            View officeItemView = getLayoutInflater().inflate(R.layout.item_field_office_menu, null);

            // Set city name
            TextView cityText = officeItemView.findViewById(R.id.office_city);
            cityText.setText(office.city);

            // Set up call button
            View callButton = officeItemView.findViewById(R.id.call_button);
            callButton.setOnClickListener(v -> {
                callPhoneNumber(office.phone);
                fieldOfficePopup.dismiss();
            });

            // Set up copy button
            View copyButton = officeItemView.findViewById(R.id.copy_button);
            copyButton.setOnClickListener(v -> {
                copyPhoneNumber(office.phone);
                fieldOfficePopup.dismiss();
            });

            // Hide separator for last item
            View separator = officeItemView.findViewById(R.id.separator);
            if (i == contact.field_offices.length - 1) {
                separator.setVisibility(View.GONE);
            }

            officesList.addView(officeItemView);
        }

        // Measure the popup content to get proper width
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();

        // Create PopupWindow wi1th exact content size
        fieldOfficePopup = new PopupWindow(popupView,
            Math.max(popupWidth, (int) getResources().getDimension(R.dimen.field_office_popup_min_width)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true);

        // Set popup properties
        fieldOfficePopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        fieldOfficePopup.setOutsideTouchable(true);
        fieldOfficePopup.setFocusable(true);

        // Show popup positioned relative to anchor
        fieldOfficePopup.showAsDropDown(anchor, 0, 8);
    }

    private void setupScript() {
        Contact contact = mIssue.contacts.get(mActiveContactIndex);

        // Set call script
        String script = ScriptReplacements.replacing(
                this,
                mIssue.actions != null && mIssue.actions.call != null ? mIssue.actions.call.script : "",
                contact,
                getIntent().getStringExtra(KEY_LOCATION_NAME),
                AccountManager.Instance.getUserName(this)
        );
        MarkdownUtil.setUpScript(binding.callScript, script, getApplicationContext());
        binding.callScript.setMovementMethod(LinkMovementMethod.getInstance());
        binding.callScript.setTextSize(AccountManager.Instance.getScriptTextSize(getApplicationContext()));
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
                    // Skip doesn't send anything to backend, just navigate to next
                    navigateToNextContactOrComplete();
                } else {
                    // Normal outcomes report the call and then navigate
                    reportCall(outcome, address);
                    navigateToNextContactOrComplete();
                }
            }
        });

        binding.outcomeList.setLayoutManager(
                new GridLayoutManager(this, 2)); // 2 columns like iOS
        binding.outcomeList.setAdapter(outcomeAdapter);

        int gridPadding = (int) getResources().getDimension(R.dimen.grid_padding);
        binding.outcomeList.addItemDecoration(new GridItemDecoration(gridPadding, 2));
    }

    private void callPhoneNumber(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(android.net.Uri.parse("tel:" + phoneNumber));
        startActivity(intent);
    }

    private void copyPhoneNumber(String phoneNumber) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Phone", phoneNumber);
        clipboard.setPrimaryClip(clip);

        // Show feedback like iOS
        binding.copiedPhoneFeedback.setText("Copied: " + phoneNumber);
        binding.copiedPhoneFeedback.setVisibility(View.VISIBLE);

        // Hide after 3 seconds
        mainHandler.postDelayed(() -> {
            binding.copiedPhoneFeedback.setVisibility(View.GONE);
        }, 3000);
    }



    private void reportCall(Outcome outcome, String address) {
        // Trigger immediate feedback (pulse, haptic, sound) before server call
        triggerAnimalsCounterIncrement();

        outcomeAdapter.setEnabled(false);
        String categories = DatabaseHelper.categoriesToString(mIssue.categories);
        AppSingleton.getInstance(getApplicationContext()).getDatabaseHelper().addCall(mIssue.id,
                mIssue.name, mIssue.contacts.get(mActiveContactIndex).id,
                mIssue.contacts.get(mActiveContactIndex).name, outcome.status.toString(), address, mIssue.animalsHelpedPerAction, categories, DatabaseHelper.ActionTypes.CALL);
        AppSingleton.getInstance(getApplicationContext()).getJsonController().reportCall(
                mIssue.id, mIssue.contacts.get(mActiveContactIndex).id, outcome.label, address);
    }

    private void navigateToNextContactOrComplete() {
        // Find the next contact in the list
        int nextContactIndex = findNextContact();
        Log.d("RepCallActivity", "navigateToNextContactOrComplete: nextContactIndex = " + nextContactIndex);

        if (nextContactIndex != -1) {
            // Navigate to next contact
            Log.d("RepCallActivity", "navigateToNextContactOrComplete: Launching next contact at index " + nextContactIndex);
            launchNextContact(nextContactIndex);
        } else {
            // No more contacts, return to issue list
            Log.d("RepCallActivity", "navigateToNextContactOrComplete: No more contacts, calling returnToIssue()");
            returnToIssue();
        }
    }

    private int findNextContact() {
        // Simply go to the next contact in the list, regardless of call history
        int nextIndex = mActiveContactIndex + 1;
        if (nextIndex < mIssue.contacts.size()) {
            return nextIndex;
        }

        // No more contacts after current one
        return -1;
    }

    private void launchNextContact(int nextContactIndex) {
        Intent intent = new Intent(this, RepCallActivity.class);
        intent.putExtra(KEY_ISSUE, mIssue);
        intent.putExtra(KEY_ACTIVE_CONTACT_INDEX, nextContactIndex);
        intent.putExtra(KEY_ADDRESS, getIntent().getStringExtra(KEY_ADDRESS));
        intent.putExtra(KEY_LOCATION_NAME, getIntent().getStringExtra(KEY_LOCATION_NAME));
        intent.putExtra("SHOULD_PULSE_COUNTER", true);

        // Copy over other extras that might be needed
        if (getIntent().hasExtra(IssueActivity.KEY_IS_LOW_ACCURACY)) {
            intent.putExtra(IssueActivity.KEY_IS_LOW_ACCURACY,
                    getIntent().getBooleanExtra(IssueActivity.KEY_IS_LOW_ACCURACY, false));
        }
        if (getIntent().hasExtra(IssueActivity.KEY_DONATE_IS_ON)) {
            intent.putExtra(IssueActivity.KEY_DONATE_IS_ON,
                    getIntent().getBooleanExtra(IssueActivity.KEY_DONATE_IS_ON, false));
        }

        // Clear the current activity from the stack and start the new one
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void returnToIssue() {
        if (isFinishing()) {
            return;
        }

        // Launch IssueActivity with pulse flag (same pattern as launchNextContact)
        Intent intent = new Intent(this, IssueActivity.class);
        intent.putExtra(IssueActivity.KEY_ISSUE, mIssue);
        intent.putExtra("SHOULD_PULSE_COUNTER", true);
        Log.d("RepCallActivity", "returnToIssue: Setting SHOULD_PULSE_COUNTER = true in intent");

        // Copy important flags
        if (getIntent().hasExtra(IssueActivity.KEY_IS_LOW_ACCURACY)) {
            intent.putExtra(IssueActivity.KEY_IS_LOW_ACCURACY,
                    getIntent().getBooleanExtra(IssueActivity.KEY_IS_LOW_ACCURACY, false));
        }
        if (getIntent().hasExtra(IssueActivity.KEY_DONATE_IS_ON)) {
            intent.putExtra(IssueActivity.KEY_DONATE_IS_ON,
                    getIntent().getBooleanExtra(IssueActivity.KEY_DONATE_IS_ON, false));
        }

        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void returnToIssueWithServerError() {
        if (isFinishing()) {
            return;
        }
        Intent upIntent = NavUtils.getParentActivityIntent(this);
        if (upIntent == null) {
            return;
        }
        upIntent.putExtra(IssueActivity.KEY_ISSUE, mIssue);
        setResult(IssueActivity.RESULT_SERVER_ERROR, upIntent);
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private static void linkPhoneNumber(TextView textView, String phoneNumber) {
        textView.setText(phoneNumber);
        Linkify.addLinks(textView, Patterns.PHONE, "tel:",
                Linkify.sPhoneNumberMatchFilter,
                Linkify.sPhoneNumberTransformFilter);
    }

    /**
     * Check for pending achievements and show celebration dialog (like iOS ImpactManager)
     */
    private void checkPendingAchievements() {
        AchievementManager.PendingAchievement pending = AchievementManager.getInstance().getPendingAchievement();
        if (pending != null) {
            Log.d("RepCallActivity", "Showing pending achievement: " + pending.title);
            AchievementCelebrationView.show(this, pending.title, pending.subtitle, pending.icon);
        }
    }
}