package org.a5calls.android.a5calls.controller;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.FiveCallsApplication;
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

import java.util.ArrayList;
import java.util.List;

/**
 * iOS-style issue detail screen that matches AnimalPolicyDetail.swift
 * Simplified version for political campaigns only (calling representatives)
 */
public class IssueActivity extends AppCompatActivity {
    private static final String TAG = "IssueActivity";
    public static final String KEY_ISSUE = "key_issue";
    public static final String KEY_IS_LOW_ACCURACY = "key_is_low_accuracy";
    public static final String KEY_DONATE_IS_ON = "key_donate_is_on";
    public static final int RESULT_OK = 1;
    public static final int RESULT_SERVER_ERROR = 2;

    private Issue mIssue;
    private boolean mIsLowAccuracy = false;
    private boolean mDonateIsOn = false;
    private final AccountManager accountManager = AccountManager.Instance;

    private ActivityIssueBinding binding;
    private List<Contact> mContacts = new ArrayList<>();

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
        
        // Clear the content change indicator when user views the issue
        ContentChangeManager contentChangeManager = new ContentChangeManager(this);
        contentChangeManager.clearIssueChanged(mIssue.id);

        // Track analytics
        FiveCallsApplication.analyticsManager().trackPageview("/issue/" + mIssue.slug + "/", this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh all sections when returning to this activity
        setupLocationSection();
        setupContactsSection();
        setupActionButtons();
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
        binding.actionButtonsSection.setVisibility(View.VISIBLE);
        android.util.Log.d("IssueActivity", "Showing action buttons - location available");

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
        
        startActivity(intent);
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
        
        startActivity(intent);
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
                
                startActivity(intent);
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

        if (mIssue.contacts == null || mIssue.contacts.isEmpty()) {
            // Show message when no contacts available
            TextView noContacts = new TextView(this);
            noContacts.setText("No representatives found for your location.");
            noContacts.setTextColor(getResources().getColor(R.color.text_secondary_modern, null));
            noContacts.setPadding(16, 16, 16, 16);
            contactsContainer.addView(noContacts);
            return;
        }

        // Get categorized contacts using new Issue methods
        List<Contact> targetedContacts = mIssue.getTargetedContacts();
        List<Contact> irrelevantContacts = mIssue.getIrrelevantContacts();
        List<String> vacantAreas = mIssue.getVacantAreas();


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
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void showLocationBottomSheet() {
        LocationBottomSheetFragment locationBottomSheet = LocationBottomSheetFragment.newInstance();
        locationBottomSheet.setLocationSetListener(location -> {
            // Refresh the screen when location is set
            recreate();
        });
        locationBottomSheet.show(getSupportFragmentManager(), "LocationBottomSheet");
    }

    private void launchContactDetail() {
        Intent intent = new Intent(this, RepCallActivity.class);
        intent.putExtra(KEY_ISSUE, mIssue);
        // Pass other required extras from the original activity
        intent.putExtra(RepCallActivity.KEY_ADDRESS, getIntent().getStringExtra(RepCallActivity.KEY_ADDRESS));
        intent.putExtra(RepCallActivity.KEY_LOCATION_NAME, getIntent().getStringExtra(RepCallActivity.KEY_LOCATION_NAME));
        intent.putExtra(KEY_IS_LOW_ACCURACY, mIsLowAccuracy);
        intent.putExtra(KEY_DONATE_IS_ON, mDonateIsOn);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_issue, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (item.getItemId() == R.id.menu_share) {
            sendShare();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void sendShare() {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(
                R.string.issue_share_subject));
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                String.format(getResources().getString(R.string.issue_share_content), mIssue.name,
                        mIssue.slug));
        shareIntent.setType("text/plain");
        startActivity(Intent.createChooser(shareIntent, getResources().getString(
                R.string.share_chooser_title)));
    }
}