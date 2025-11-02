package org.a5calls.android.a5calls.controller;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.FiveCallsApplication;
import org.a5calls.android.a5calls.net.FiveCallsApi;
import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.databinding.ActivityIssueBinding;
import org.a5calls.android.a5calls.model.AccountManager;
import org.a5calls.android.a5calls.model.Contact;
import org.a5calls.android.a5calls.model.DatabaseHelper;
import org.a5calls.android.a5calls.model.Issue;
import org.a5calls.android.a5calls.model.Target;
import org.a5calls.android.a5calls.util.MarkdownUtil;
import org.a5calls.android.a5calls.util.ContentChangeManager;
import org.a5calls.android.a5calls.view.ContactListItemView;
import org.a5calls.android.a5calls.view.AchievementCelebrationView;
import org.a5calls.android.a5calls.manager.AchievementManager;

import java.util.ArrayList;
import java.util.List;

/**
 * iOS-style issue detail screen that matches AnimalPolicyDetail.swift
 * Simplified version for political campaigns only (calling representatives)
 */
public class IssueActivity extends AppCompatActivity implements FiveCallsApi.ContactsRequestListener {
    private static final String TAG = "IssueActivity";
    public static final String KEY_ISSUE = "key_issue";
    public static final String KEY_IS_LOW_ACCURACY = "key_is_low_accuracy";
    public static final String KEY_DONATE_IS_ON = "key_donate_is_on";
    public static final int RESULT_OK = 1;
    public static final int RESULT_SERVER_ERROR = 2;

    private Issue mIssue;
    private Menu currentMenu; // Store menu reference for counter animation
    private boolean mIsLowAccuracy = false;
    private boolean mDonateIsOn = false;
    private final AccountManager accountManager = AccountManager.Instance;
    private LocationBottomSheetFragment currentLocationBottomSheet;

    private ActivityIssueBinding binding;
    private List<Contact> mContacts = new ArrayList<>();
    private String mOriginalCity; // Store original location when issue loaded
    private String mOriginalCounty;
    private String mOriginalState;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIssueBinding.inflate(getLayoutInflater());

        mIssue = getIntent().getParcelableExtra(KEY_ISSUE);
        if (mIssue == null) {
            finish();
            return;
        }
        mIsLowAccuracy = getIntent().getBooleanExtra(KEY_IS_LOW_ACCURACY, false);
        mDonateIsOn = getIntent().getBooleanExtra(KEY_DONATE_IS_ON, false);

        setContentView(binding.getRoot());

        setupActionBar();
        setupContent();
        setupLocationSection();
        setupContactsSection();
        setupActionButtons();

        // Store original location when issue loads for comparison later
        mOriginalCity = accountManager.getLocationCity(this);
        mOriginalCounty = accountManager.getLocationCounty(this);
        mOriginalState = accountManager.getLocationState(this);

        // Clear the content change indicator when user views the issue
        ContentChangeManager contentChangeManager = new ContentChangeManager(this);
        contentChangeManager.clearIssueChanged(mIssue.id);

        // Track analytics
        FiveCallsApplication.analyticsManager().trackPageview("/issue/" + mIssue.slug + "/", this);
    }

    /**
     * Add iOS-style low accuracy warning view to contacts container
     */
    private void addLowAccuracyWarningView(LinearLayout container) {
        // Create main container with vertical orientation and center alignment
        LinearLayout warningView = new LinearLayout(this);
        warningView.setOrientation(LinearLayout.VERTICAL);
        warningView.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        warningView.setPadding(40, 32, 40, 32);

        // Location pin icon in light blue circle (like iOS)
        FrameLayout iconContainer = new FrameLayout(this);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(
                (int) (60 * getResources().getDisplayMetrics().density),
                (int) (60 * getResources().getDisplayMetrics().density)
        );
        iconContainerParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        iconContainer.setLayoutParams(iconContainerParams);

        // Blue circle background
        android.graphics.drawable.GradientDrawable circleDrawable = new android.graphics.drawable.GradientDrawable();
        circleDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circleDrawable.setColor(0x1A007AFF); // Light blue with alpha (like iOS)
        iconContainer.setBackground(circleDrawable);

        // Location icon
        ImageView locationIcon = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                (int) (24 * getResources().getDisplayMetrics().density),
                (int) (24 * getResources().getDisplayMetrics().density)
        );
        iconParams.gravity = android.view.Gravity.CENTER;
        locationIcon.setLayoutParams(iconParams);
        locationIcon.setImageResource(R.drawable.ic_location_on);
        locationIcon.setColorFilter(0xFF007AFF); // Blue color
        iconContainer.addView(locationIcon);
        warningView.addView(iconContainer);

        // Main heading - use iOS message (not server message)
        TextView heading = new TextView(this);
        heading.setText("We need a more specific address");
        heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        heading.setTextColor(getResources().getColor(R.color.text_primary_modern, null));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headingParams.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
        heading.setLayoutParams(headingParams);
        warningView.addView(heading);

        // Update Location button (like iOS blue button)
        Button updateLocationButton = new Button(this);
        updateLocationButton.setText("Update Location");
        updateLocationButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        updateLocationButton.setTextColor(android.graphics.Color.WHITE);
        updateLocationButton.setTypeface(null, android.graphics.Typeface.BOLD);
        updateLocationButton.setPadding(
                (int) (24 * getResources().getDisplayMetrics().density),
                (int) (12 * getResources().getDisplayMetrics().density),
                (int) (24 * getResources().getDisplayMetrics().density),
                (int) (12 * getResources().getDisplayMetrics().density)
        );

        // Blue rounded background
        android.graphics.drawable.GradientDrawable buttonDrawable = new android.graphics.drawable.GradientDrawable();
        buttonDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        buttonDrawable.setColor(0xFF007AFF); // iOS blue
        buttonDrawable.setCornerRadius(20 * getResources().getDisplayMetrics().density);
        updateLocationButton.setBackground(buttonDrawable);

        // Button click handler - open location bottom sheet
        updateLocationButton.setOnClickListener(v -> {
            showLocationBottomSheet();
        });

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        updateLocationButton.setLayoutParams(buttonParams);
        warningView.addView(updateLocationButton);

        container.addView(warningView);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check for pending achievements (like iOS ImpactManager)
        checkPendingAchievements();

        // Refresh the low accuracy flag from persistent storage (like MainActivity does)
        mIsLowAccuracy = accountManager.getLocationLowAccuracy(this);

        // Refresh all sections when returning to this activity
        setupLocationSection();
        setupContactsSection();
        setupActionButtons();

        // Refresh animals counter in action bar when returning from action
        refreshAnimalsCounterInActionBar();

        // Check if we should pulse the counter (same pattern as contact → contact)
        boolean shouldPulse = getIntent().getBooleanExtra("SHOULD_PULSE_COUNTER", false);
        Log.d("IssueActivity", "onResume: SHOULD_PULSE_COUNTER = " + shouldPulse);

        if (shouldPulse) {
            // Clear the flag so it doesn't pulse again
            getIntent().removeExtra("SHOULD_PULSE_COUNTER");
            Log.d("IssueActivity", "onResume: Triggering pulse animation");

            // Short delay to ensure menu is ready but feel connected to the action
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d("IssueActivity", "onResume: Calling animateMenuCounter()");
                animateMenuCounter();
            }, 300);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d("IssueActivity", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + ", data=" + (data != null));
        if (data != null) {
            Log.d("IssueActivity", "onActivityResult: SHOULD_PULSE_COUNTER = " + data.getBooleanExtra("SHOULD_PULSE_COUNTER", false));
        }

        // Check if returning from any activity (EmailComposer, RepCall, etc.) with pulse flag
        if (resultCode == android.app.Activity.RESULT_OK && data != null && data.getBooleanExtra("SHOULD_PULSE_COUNTER", false)) {
            Log.d("IssueActivity", "onActivityResult: Triggering pulse animation");
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


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up listener to avoid memory leaks
        FiveCallsApi api = AppSingleton.getInstance(this).getJsonController();
        api.unregisterContactsRequestListener(this);
    }
    
    private void setupActionBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(""); // Remove title to match iOS
        }
    }

    private void setupContent() {
        // Set issue title and description
        binding.issueName.setText(mIssue.name);
        MarkdownUtil.setUpScript(binding.issueDescription, mIssue.reason, getApplicationContext());
        // Enable link clicking for markdown links
        binding.issueDescription.setMovementMethod(LinkMovementMethod.getInstance());

        // Setup impact preview section
        setupImpactPreview();
    }

    private void setupImpactPreview() {
        TextView impactText = findViewById(R.id.impact_preview_text);
        ImageView infoIcon = findViewById(R.id.impact_info_icon);

        if (impactText != null) {
            String text = "Take action, help save " + mIssue.animalsHelpedPerAction + " animals";
            impactText.setText(text);
        }

        if (infoIcon != null) {
            infoIcon.setOnClickListener(v -> showImpactInfoDialog());
        }
    }

    private void showImpactInfoDialog() {
        // Create a custom popover-style dialog like iOS
        View popoverView = getLayoutInflater().inflate(R.layout.impact_info_popover, null);

        // Measure the popup to get its actual dimensions
        popoverView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popoverView.getMeasuredWidth();
        int popupHeight = popoverView.getMeasuredHeight();

        // Force specific width to match iOS (180 points)
        int fixedWidth = (int)(180 * getResources().getDisplayMetrics().density); // 180dp in pixels

        android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(
            popoverView,
            fixedWidth,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            true // focusable
        );

        // Set background to enable dismissal when clicking outside
        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);

        // Find the info icon to position the popup relative to it
        View anchorView = findViewById(R.id.impact_info_icon);
        if (anchorView != null) {
            // Use showAsDropDown with calculated offsets - simpler and more reliable
            float density = getResources().getDisplayMetrics().density;
            int popup180dp = (int)(180 * density); // 180dp in pixels (matching iOS)
            int iconSize = (int)(32 * density); // Icon size in pixels

            // Calculate offsets relative to the anchor view
            int offsetX = iconSize - popup180dp - (int)(20 * density); // Move left more to align properly
            int offsetY = -(popupHeight > 0 ? popupHeight : (int)(90 * density)) - (int)(60 * density); // Move up more to touch icon exactly

            Log.d("IssueActivity", "Popup positioning with showAsDropDown: offsetX=" + offsetX + ", offsetY=" + offsetY +
                  ", popupWidth=" + popup180dp + ", estimatedHeight=" + (popupHeight > 0 ? popupHeight : (int)(90 * density)));

            // Show using dropDown positioning (more reliable)
            popupWindow.showAsDropDown(anchorView, offsetX, offsetY);
        }
    }

    private void setupLocationSection() {
        // Show location setup section if no location is set
        boolean hasLocation = accountManager.hasLocation(this);

        if (!hasLocation) {
            binding.locationSetupSection.setVisibility(View.VISIBLE);
            binding.setLocationButton.setOnClickListener(v -> showLocationBottomSheet());
        } else {
            binding.locationSetupSection.setVisibility(View.GONE);
        }
    }

    private void setupContactsSection() {
        // Check if this is a corporate campaign
        boolean isCorporateCampaign = "CORPORATE".equals(mIssue.contactType);
        
        if (isCorporateCampaign) {
            // For corporate campaigns, show targets section
            binding.contactsSection.setVisibility(View.VISIBLE);
            binding.contactsHeader.setText(getString(R.string.targets_header));
            populateCorporateTargets();
        } else {
            // For political campaigns, require location
            if (!accountManager.hasLocation(this)) {
                // Hide contacts section when location is not set
                binding.contactsSection.setVisibility(View.GONE);
                return;
            }

            binding.contactsSection.setVisibility(View.VISIBLE);

            // Set section header - matches iOS contactsSectionHeader logic
            String headerText = getContactsSectionHeader();
            binding.contactsHeader.setText(headerText);

            // Populate contacts with proper sectioning
            populateContacts();
        }
    }

    private String getContactsSectionHeader() {
        // This matches the iOS contactsSectionHeader computed property
        // For now, we only support political campaigns (representatives)
        // In the future, this could support corporate campaigns too
        return getString(R.string.reps_list_header);
    }

    private void setupActionButtons() {
        boolean hasLocation = accountManager.hasLocation(this);
        String address = accountManager.getAddress(this);
        String lat = accountManager.getLat(this);
        String lng = accountManager.getLng(this);

        android.util.Log.d("IssueActivity", "setupActionButtons: hasLocation = " + hasLocation);
        android.util.Log.d("IssueActivity", "address = '" + address + "'");
        android.util.Log.d("IssueActivity", "lat = '" + lat + "'");
        android.util.Log.d("IssueActivity", "lng = '" + lng + "'");

        // Check if this is a corporate campaign
        boolean isCorporateCampaign = "CORPORATE".equals(mIssue.contactType);
        
        if (isCorporateCampaign) {
            // For corporate campaigns, show email/call buttons regardless of location
            setupCorporateActionButtons();
        } else {
            // For political campaigns, require location
            if (!hasLocation) {
                // Hide action buttons when location is not set
                binding.actionButtonsSection.setVisibility(View.GONE);
                android.util.Log.d("IssueActivity", "Hiding action buttons - no location");
                return;
            }
            setupPoliticalActionButtons();
        }
    }
    
    private void setupPoliticalActionButtons() {
        // Check if we have any targeted contacts to call
        List<Contact> targetedContacts = (mIssue.contacts != null) ? mIssue.getTargetedContacts() : new ArrayList<>();

        if (targetedContacts.isEmpty()) {
            // Hide action buttons when no targeted contacts available
            binding.actionButtonsSection.setVisibility(View.GONE);
            android.util.Log.d("IssueActivity", "Hiding action buttons - no targeted contacts available");
            return;
        }

        binding.actionButtonsSection.setVisibility(View.VISIBLE);
        android.util.Log.d("IssueActivity", "Showing action buttons - targeted contacts available");

        // Setup primary action button for calling representatives
        binding.primaryActionButton.setText(R.string.make_calls_button);
        binding.primaryActionButton.setVisibility(View.VISIBLE);
        android.util.Log.d("IssueActivity", "Button text set and visibility set to VISIBLE");

        binding.primaryActionButton.setOnClickListener(v -> {
            // Launch contact detail screen for calling
            android.util.Log.d("IssueActivity", "Make Calls button clicked!");
            launchContactDetail();
        });

        // Hide secondary buttons for political campaigns
        binding.secondaryButtonsContainer.setVisibility(View.GONE);
        android.util.Log.d("IssueActivity", "setupActionButtons completed");
    }
    
    private void setupCorporateActionButtons() {
        // Check if we have any targets for corporate campaigns
        boolean hasTargets = mIssue.targets != null && !mIssue.targets.isEmpty();

        if (!hasTargets) {
            // Hide action buttons when no targets available
            binding.actionButtonsSection.setVisibility(View.GONE);
            android.util.Log.d("IssueActivity", "Hiding action buttons - no corporate targets available");
            return;
        }

        binding.actionButtonsSection.setVisibility(View.VISIBLE);

        // Check if email is enabled
        boolean emailEnabled = mIssue.actions != null &&
                              mIssue.actions.email != null &&
                              mIssue.actions.email.enabled;

        // Check if call is enabled
        boolean callEnabled = mIssue.actions != null &&
                             mIssue.actions.call != null &&
                             mIssue.actions.call.enabled;

        if (emailEnabled) {
            // Email campaign - show email button
            binding.primaryActionButton.setText(getString(R.string.send_email_button));
            binding.primaryActionButton.setVisibility(View.VISIBLE);
            binding.primaryActionButton.setOnClickListener(v -> launchEmailComposer());
            
            binding.secondaryButtonsContainer.setVisibility(View.GONE);
        } else if (callEnabled) {
            // Call campaign - show call button
            binding.primaryActionButton.setText(getString(R.string.make_calls_button));
            binding.primaryActionButton.setVisibility(View.VISIBLE);
            binding.primaryActionButton.setOnClickListener(v -> launchCorporateCall());
            
            binding.secondaryButtonsContainer.setVisibility(View.GONE);
        } else {
            // No actions enabled
            binding.actionButtonsSection.setVisibility(View.GONE);
        }
    }
    
    private void launchEmailComposer() {
        Intent intent = new Intent(this, EmailComposerActivity.class);
        intent.putExtra("issue", mIssue);
        intent.putParcelableArrayListExtra("targets", (ArrayList) mIssue.targets);
        
        // Check if this is batch email
        boolean isBatchEmail = mIssue.actions != null && 
                              mIssue.actions.email != null && 
                              "batch".equals(mIssue.actions.email.distributionMethod);
        intent.putExtra("is_batch_email", isBatchEmail);
        
        // Pass location for script replacements (same as RepCallActivity)
        intent.putExtra(EmailComposerActivity.KEY_LOCATION_NAME, 
                       getIntent().getStringExtra(RepCallActivity.KEY_LOCATION_NAME));
        
        startActivityForResult(intent, 1001); // Request code for RepCallActivity
    }
    
    private void launchCorporateCall() {
        // Always start with the first target
        launchCorporateCallForTarget(mIssue.targets.get(0), 0);
    }
    
    private void launchCorporateCallForTarget(Target target) {
        // Find the index of this target
        int targetIndex = mIssue.targets.indexOf(target);
        launchCorporateCallForTarget(target, targetIndex);
    }
    
    private void launchCorporateCallForTarget(Target target, int targetIndex) {
        Intent intent = new Intent(this, CorporateCallActivity.class);
        intent.putExtra(KEY_ISSUE, mIssue);
        intent.putExtra(CorporateCallActivity.KEY_ACTIVE_TARGET_INDEX, targetIndex);
        intent.putExtra(CorporateCallActivity.KEY_ADDRESS, getIntent().getStringExtra(RepCallActivity.KEY_ADDRESS));
        intent.putExtra(CorporateCallActivity.KEY_LOCATION_NAME, getIntent().getStringExtra(RepCallActivity.KEY_LOCATION_NAME));
        
        startActivityForResult(intent, 1001); // Request code for RepCallActivity
    }
    
    private void populateCorporateTargets() {
        LinearLayout contactsContainer = binding.contactsContainer;
        contactsContainer.removeAllViews();
        
        if (mIssue.targets == null || mIssue.targets.isEmpty()) {
            // Show message when no targets available
            TextView noTargets = new TextView(this);
            noTargets.setText(getString(R.string.no_targets_found));
            noTargets.setPadding(16, 16, 16, 16);
            contactsContainer.addView(noTargets);
            return;
        }
        
        // Check if this is batch email
        boolean isBatchEmail = mIssue.actions != null && 
                              mIssue.actions.email != null && 
                              "batch".equals(mIssue.actions.email.distributionMethod);
        
        android.util.Log.d("IssueActivity", "isBatchEmail: " + isBatchEmail);
        if (mIssue.actions != null && mIssue.actions.email != null) {
            android.util.Log.d("IssueActivity", "distributionMethod: " + mIssue.actions.email.distributionMethod);
        }
        
        if (isBatchEmail) {
            // Show single company target with batch email button
            View targetView = getLayoutInflater().inflate(R.layout.corporate_target_item, contactsContainer, false);
            setupBatchTargetView(targetView);
            contactsContainer.addView(targetView);
        } else {
            // Show individual targets with individual email buttons
            for (Target target : mIssue.targets) {
                View targetView = getLayoutInflater().inflate(R.layout.corporate_target_item, contactsContainer, false);
                setupIndividualTargetView(targetView, target);
                contactsContainer.addView(targetView);
            }
        }
    }
    
    private void setupBatchTargetView(View targetView) {
        // For batch email, show company info and make entire card clickable
        ImageView icon = targetView.findViewById(R.id.target_icon);
        ImageView checkmark = targetView.findViewById(R.id.target_checkmark);
        TextView name = targetView.findViewById(R.id.target_name);
        TextView department = targetView.findViewById(R.id.target_department);
        
        icon.setImageResource(R.drawable.ic_business);
        
        android.util.Log.d("IssueActivity", "setupBatchTargetView - corporateInfo: " + (mIssue.corporateInfo != null));
        if (mIssue.corporateInfo != null) {
            android.util.Log.d("IssueActivity", "Company: " + mIssue.corporateInfo.company);
            name.setText(mIssue.corporateInfo.company);
            if (mIssue.corporateInfo.industry != null) {
                department.setText(mIssue.corporateInfo.industry);
            }
        } else {
            android.util.Log.d("IssueActivity", "corporateInfo is null, using fallback");
            name.setText("Company");
            department.setText("Corporate Campaign");
        }
        
        // Show checkmark if batch email has been completed
        boolean hasBeenCompleted = hasBatchTargetBeenCompleted();
        if (hasBeenCompleted) {
            checkmark.setVisibility(View.VISIBLE);
        } else {
            checkmark.setVisibility(View.GONE);
        }
        
        // Make entire card clickable to launch appropriate action
        targetView.setOnClickListener(v -> {
            // Check if email or call is enabled
            boolean emailEnabled = mIssue.actions != null && 
                                  mIssue.actions.email != null && 
                                  mIssue.actions.email.enabled;
            boolean callEnabled = mIssue.actions != null && 
                                 mIssue.actions.call != null && 
                                 mIssue.actions.call.enabled;
            
            if (emailEnabled) {
                // Email campaign - launch email composer
                launchEmailComposer();
            } else if (callEnabled) {
                // Call campaign - launch call for this specific target
                launchCorporateCallForTarget(mIssue.targets.get(0)); // For batch, use first target
            }
        });
    }
    
    private void setupIndividualTargetView(View targetView, Target target) {
        // For individual email, show target info and make entire card clickable
        ImageView icon = targetView.findViewById(R.id.target_icon);
        ImageView checkmark = targetView.findViewById(R.id.target_checkmark);
        TextView name = targetView.findViewById(R.id.target_name);
        TextView department = targetView.findViewById(R.id.target_department);
        
        icon.setImageResource(R.drawable.ic_person);
        name.setText(target.name);
        if (target.department != null) {
            department.setText(target.department);
        }
        
        // Show checkmark if this individual target has been completed
        boolean hasBeenCompleted = hasIndividualTargetBeenCompleted(target);
        if (hasBeenCompleted) {
            checkmark.setVisibility(View.VISIBLE);
        } else {
            checkmark.setVisibility(View.GONE);
        }
        
        // Make entire card clickable to launch appropriate action for this specific target
        targetView.setOnClickListener(v -> {
            // Check if email or call is enabled
            boolean emailEnabled = mIssue.actions != null && 
                                  mIssue.actions.email != null && 
                                  mIssue.actions.email.enabled;
            boolean callEnabled = mIssue.actions != null && 
                                 mIssue.actions.call != null && 
                                 mIssue.actions.call.enabled;
            
            if (emailEnabled) {
                // Email campaign - launch email composer for this specific target
                Intent intent = new Intent(this, EmailComposerActivity.class);
                intent.putExtra("issue", mIssue);
                ArrayList<Target> singleTarget = new ArrayList<>();
                singleTarget.add(target);
                intent.putParcelableArrayListExtra("targets", singleTarget);
                intent.putExtra("is_batch_email", false);
                
                // Pass location for script replacements (same as RepCallActivity)
                intent.putExtra(EmailComposerActivity.KEY_LOCATION_NAME, 
                               getIntent().getStringExtra(RepCallActivity.KEY_LOCATION_NAME));
                
                startActivityForResult(intent, 1001); // Request code for RepCallActivity
            } else if (callEnabled) {
                // Call campaign - launch corporate call for this specific target
                launchCorporateCallForTarget(target);
            }
        });
    }

    private void populateContacts() {
        LinearLayout contactsContainer = binding.contactsContainer;
        contactsContainer.removeAllViews();

        // Debug logging to see what data we have
        android.util.Log.d("IssueActivity", "*** Issue contactAreas: " +
            (mIssue.contactAreas != null ? mIssue.contactAreas.toString() : "null"));
        if (mIssue.contacts != null) {
            for (Contact contact : mIssue.contacts) {
                android.util.Log.d("IssueActivity", "*** Contact: " + contact.name +
                    " - Area: " + contact.area);
            }
        }

        // Get categorized contacts using new Issue methods first
        List<Contact> targetedContacts = (mIssue.contacts != null) ? mIssue.getTargetedContacts() : new ArrayList<>();
        List<Contact> irrelevantContacts = mIssue.getIrrelevantContacts();
        List<String> vacantAreas = mIssue.getVacantAreas();

        // Check if we should show low accuracy warning instead of empty contact list (like iOS)
        boolean isPoliticalCampaign = !"CORPORATE".equals(mIssue.contactType);
        boolean shouldShowLowAccuracyWarning = isPoliticalCampaign && targetedContacts.isEmpty() && mIsLowAccuracy;

        if (shouldShowLowAccuracyWarning) {
            // Show iOS-style location accuracy warning with update button
            addLowAccuracyWarningView(contactsContainer);
            return;
        }

        if (mIssue.contacts == null || mIssue.contacts.isEmpty()) {
            // Show message when no contacts available (and not low accuracy case)
            TextView noContacts = new TextView(this);
            noContacts.setText("No representatives found for your location.");
            noContacts.setTextColor(getResources().getColor(R.color.text_secondary_modern, null));
            noContacts.setPadding(16, 16, 16, 16);
            contactsContainer.addView(noContacts);
            return;
        }

        // Add targeted contacts section
        if (!targetedContacts.isEmpty()) {
            addContactsSection(contactsContainer, targetedContacts, null, false);
        }

        // Add irrelevant contacts section with divider
        if (!irrelevantContacts.isEmpty()) {
            addDivider(contactsContainer);
            addContactsSection(contactsContainer, irrelevantContacts, "Not targeted by this campaign", true);
        }

        // Add vacant areas section with divider
        if (!vacantAreas.isEmpty()) {
            addDivider(contactsContainer);
            addVacantAreasSection(contactsContainer, vacantAreas);
        }
    }

    private void addContactsSection(LinearLayout container, List<Contact> contacts, String noteText, boolean isIrrelevant) {
        for (int i = 0; i < contacts.size(); i++) {
            final Contact contact = contacts.get(i);
            // Find the original index in the full contacts list for navigation
            final int originalIndex = mIssue.contacts.indexOf(contact);

            ContactListItemView contactItem = new ContactListItemView(this);
            boolean hasBeenCalled = hasContactBeenCalled(contact);

            // Set contact with note text for irrelevant contacts
            if (noteText != null) {
                contactItem.setContact(contact, hasBeenCalled, noteText);
            } else {
                contactItem.setContact(contact, hasBeenCalled);
            }

            // Set opacity for irrelevant contacts (matches iOS .opacity(0.4))
            if (isIrrelevant) {
                contactItem.setIrrelevant(true);
            }

            // Set click listener to navigate to call screen
            contactItem.setOnClickListener(v -> {
                launchContactDetailForContact(contact, originalIndex);
            });

            container.addView(contactItem);
        }
    }

    private void addVacantAreasSection(LinearLayout container, List<String> vacantAreas) {
        for (String area : vacantAreas) {
            // Create a placeholder contact for vacant areas
            Contact vacantContact = new Contact();
            vacantContact.name = "Vacant Seat";
            vacantContact.area = area;

            ContactListItemView contactItem = new ContactListItemView(this);
            contactItem.setContact(vacantContact, false, "This position is currently vacant");
            contactItem.setIrrelevant(true); // Show dimmed like iOS

            // Make vacant contacts non-clickable
            contactItem.setOnClickListener(null);
            contactItem.setClickable(false);

            container.addView(contactItem);
        }
    }

    private void addDivider(LinearLayout container) {
        // Create a divider view to separate sections
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            1
        ));
        divider.setBackgroundColor(getResources().getColor(R.color.text_secondary_modern, null));
        divider.getLayoutParams().height = (int) (0.5 * getResources().getDisplayMetrics().density);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) divider.getLayoutParams();
        params.setMargins(16, 0, 16, 0);

        container.addView(divider);
    }

    private boolean hasContactBeenCalled(Contact contact) {
        DatabaseHelper dbHelper = AppSingleton.getInstance(this).getDatabaseHelper();
        return dbHelper.hasCalledToday(mIssue.id, contact.id);
    }
    
    private boolean hasBatchTargetBeenCompleted() {
        // For batch emails, check if any email action has been taken
        // Always use the first target's ID and warn if it's not there
        if (mIssue.targets == null || mIssue.targets.isEmpty()) {
            android.util.Log.w("IssueActivity", "Corporate campaign " + mIssue.id + " has no targets for batch email completion check");
            return false;
        }
        
        Target firstTarget = mIssue.targets.get(0);
        String contactId = firstTarget.id != null ? firstTarget.id : firstTarget.name;
        
        if (contactId == null) {
            android.util.Log.w("IssueActivity", "First target in corporate campaign " + mIssue.id + " has no ID or name");
            return false;
        }
        
        DatabaseHelper dbHelper = AppSingleton.getInstance(this).getDatabaseHelper();
        return dbHelper.hasCalledToday(mIssue.id, contactId);
    }
    
    private boolean hasIndividualTargetBeenCompleted(Target target) {
        // For individual emails, check if this specific target has been contacted
        String contactId = target.id != null ? target.id : target.name;
        
        if (contactId == null) {
            android.util.Log.w("IssueActivity", "Target in corporate campaign " + mIssue.id + " has no ID or name");
            return false;
        }
        
        DatabaseHelper dbHelper = AppSingleton.getInstance(this).getDatabaseHelper();
        return dbHelper.hasCalledToday(mIssue.id, contactId);
    }

    private void launchContactDetailForContact(Contact contact, int contactIndex) {
        Intent intent = new Intent(this, RepCallActivity.class);
        intent.putExtra(KEY_ISSUE, mIssue);
        intent.putExtra(RepCallActivity.KEY_ACTIVE_CONTACT_INDEX, contactIndex);
        intent.putExtra(RepCallActivity.KEY_ADDRESS, getIntent().getStringExtra(RepCallActivity.KEY_ADDRESS));
        intent.putExtra(RepCallActivity.KEY_LOCATION_NAME, getIntent().getStringExtra(RepCallActivity.KEY_LOCATION_NAME));
        intent.putExtra(KEY_IS_LOW_ACCURACY, mIsLowAccuracy);
        intent.putExtra(KEY_DONATE_IS_ON, mDonateIsOn);
        startActivityForResult(intent, 1001); // Request code for RepCallActivity
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void showLocationBottomSheet() {
        LocationBottomSheetFragment locationBottomSheet = LocationBottomSheetFragment.newInstance();
        locationBottomSheet.setLocationSetListener(location -> {
            android.util.Log.d(TAG, "Location updated: " + location);
            // Save the location like MainActivity does
            AccountManager.Instance.setAddress(this, location);
            AccountManager.Instance.setLat(this, null);
            AccountManager.Instance.setLng(this, null);

            // Update the global app state by making the same call MainActivity makes
            FiveCallsApi api = AppSingleton.getInstance(this).getJsonController();
            api.registerContactsRequestListener(this); // Listen for the response
            android.util.Log.d(TAG, "Making API call for location: " + location);
            api.getContacts(location);

            // recreate() will be called in onContactsReceived() when data arrives
        });
        locationBottomSheet.show(getSupportFragmentManager(), "LocationBottomSheet");
    }

    // ContactsRequestListener implementation - only for location updates
    @Override
    public void onContactsReceived(String locationName, boolean isLowAccuracy, String lowAccuracyMessage,
                                   List<Contact> contacts, String city, String county, String state) {
        android.util.Log.d(TAG, "Location updated, checking if location changed significantly");

        AccountManager accountManager = AccountManager.Instance;

        boolean locationChanged = false;

        // Compare new location with original location when issue was loaded
        // Check state first to handle cases like Portland, OR vs Portland, ME
        if (!TextUtils.isEmpty(mOriginalState) && !TextUtils.isEmpty(state) && !TextUtils.equals(mOriginalState, state)) {
            locationChanged = true;
        }
        // If state is the same or empty, check city
        else if (!TextUtils.isEmpty(mOriginalCity) && !TextUtils.isEmpty(city) && !TextUtils.equals(mOriginalCity, city)) {
            locationChanged = true;
        }
        // If state and city are same/empty, check county (skip if county is empty as it can be missing for low accuracy)
        else if (!TextUtils.isEmpty(mOriginalCounty) && !TextUtils.isEmpty(county) && !TextUtils.equals(mOriginalCounty, county)) {
            locationChanged = true;
        }

        // Update location metadata
        if (!TextUtils.isEmpty(city)) {
            accountManager.setLocationCity(this, city);
        }
        if (!TextUtils.isEmpty(county)) {
            accountManager.setLocationCounty(this, county);
        }
        if (!TextUtils.isEmpty(state)) {
            accountManager.setLocationState(this, state);
        }
        accountManager.setLocationLowAccuracy(this, isLowAccuracy);
        if (!TextUtils.isEmpty(lowAccuracyMessage)) {
            accountManager.setLocationLowAccuracyMessage(this, lowAccuracyMessage);
        }

        if (locationChanged) {
            // Location changed significantly - go back to MainActivity to refresh issues for new location
            android.util.Log.d(TAG, "Location changed significantly, navigating back to MainActivity");
            finish(); // This will take user back to MainActivity which will refresh with new location
        } else {
            // Same location, just update accuracy - recreate to refresh UI
            android.util.Log.d(TAG, "Same location, just updating accuracy and recreating");

            // Update the global contact pool
            mContacts = contacts;

            // Use the same filtering logic as IssuesAdapter
            mIssue.filterContactsByAreas(contacts);
            mIsLowAccuracy = isLowAccuracy;

            // Recreate the activity to refresh with new contact data
            recreate();
        }
    }

    @Override
    public void onRequestError() {
        android.util.Log.d(TAG, "onRequestError - keeping current UI");
        // Don't recreate - just log the error and keep current state
    }

    @Override
    public void onJsonError() {
        android.util.Log.d(TAG, "onJsonError - keeping current UI");
        // Don't recreate - just log the error and keep current state
    }

    @Override
    public void onAddressError() {
        // Address error - still recreate to show any partial data
        recreate();
    }

    private void launchContactDetail() {
        Intent intent = new Intent(this, RepCallActivity.class);
        intent.putExtra(KEY_ISSUE, mIssue);
        // Pass other required extras from the original activity
        intent.putExtra(RepCallActivity.KEY_ADDRESS, getIntent().getStringExtra(RepCallActivity.KEY_ADDRESS));
        intent.putExtra(RepCallActivity.KEY_LOCATION_NAME, getIntent().getStringExtra(RepCallActivity.KEY_LOCATION_NAME));
        intent.putExtra(KEY_IS_LOW_ACCURACY, mIsLowAccuracy);
        intent.putExtra(KEY_DONATE_IS_ON, mDonateIsOn);
        startActivityForResult(intent, 1001); // Request code for RepCallActivity
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_issue, menu);

        // Store menu reference for counter animation
        currentMenu = menu;

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
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
            Log.e("IssueActivity", "Error updating animals counter", e);
        }
    }

    /**
     * Refreshes the animals counter when returning to this activity
     */
    private void refreshAnimalsCounterInActionBar() {
        try {
            // Find the counter in the options menu and refresh it
            if (getMenuInflater() != null) {
                // Force menu recreation to update counter
                invalidateOptionsMenu();
            }
        } catch (Exception e) {
            Log.e("IssueActivity", "Error refreshing animals counter", e);
        }
    }

    private void animateMenuCounter() {
        try {
            Log.d("IssueActivity", "animateMenuCounter: Starting animation");
            // Find the counter view in the menu using stored menu reference
            if (getSupportActionBar() != null && currentMenu != null) {
                Log.d("IssueActivity", "animateMenuCounter: ActionBar and menu found");

                MenuItem counterItem = currentMenu.findItem(R.id.menu_animals_counter);
                View actionView = null;
                if (counterItem != null) {
                    actionView = counterItem.getActionView();
                    Log.d("IssueActivity", "animateMenuCounter: Got action view from menu item");
                } else {
                    Log.w("IssueActivity", "animateMenuCounter: Menu item not found");
                }
                if (actionView != null) {
                    Log.d("IssueActivity", "animateMenuCounter: actionView found");
                    TextView counterText = actionView.findViewById(R.id.animals_count);
                    if (counterText != null) {
                        Log.d("IssueActivity", "animateMenuCounter: counterText found, current text: " + counterText.getText());
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

                        Log.d("IssueActivity", "Menu counter animation STARTED - should be visible now");
                    } else {
                        Log.w("IssueActivity", "animateMenuCounter: counterText NOT found");
                    }
                } else {
                    Log.w("IssueActivity", "animateMenuCounter: actionView NOT found");
                }
            } else {
                Log.w("IssueActivity", "animateMenuCounter: ActionBar NOT found");
            }
        } catch (Exception e) {
            Log.e("IssueActivity", "Error animating menu counter", e);
        }
    }

    /**
     * Check for pending achievements and show celebration dialog (like iOS ImpactManager)
     */
    private void checkPendingAchievements() {
        AchievementManager.PendingAchievement pending = AchievementManager.getInstance().getPendingAchievement();
        if (pending != null) {
            Log.d("IssueActivity", "Showing pending achievement: " + pending.title);
            AchievementCelebrationView.show(this, pending.title, pending.subtitle, pending.icon);
        }
    }
}