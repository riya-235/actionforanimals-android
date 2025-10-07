package org.a5calls.android.a5calls.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
import org.a5calls.android.a5calls.model.Issue;
import org.a5calls.android.a5calls.model.Target;
import org.a5calls.android.a5calls.util.ScriptReplacements;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for composing emails for corporate campaigns.
 */
public class EmailComposerActivity extends AppCompatActivity {
    public static final String KEY_LOCATION_NAME = "key_location_name";
    
    private Issue mIssue;
    private List<Target> mTargets;
    private boolean mIsBatchEmail;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_composer);
        
        mIssue = getIntent().getParcelableExtra("issue");
        mTargets = getIntent().getParcelableArrayListExtra("targets");
        mIsBatchEmail = getIntent().getBooleanExtra("is_batch_email", false);
        
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
            // Individual email - show target info
            contactIcon.setImageResource(R.drawable.ic_person);
            if (mTargets != null && !mTargets.isEmpty()) {
                Target target = mTargets.get(0);
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
        trackEmailAction(action);
        finish();
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
            // For individual emails, this would be called per target
            if (mTargets != null && !mTargets.isEmpty()) {
                String[] singleEmail = {mTargets.get(0).email};
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
                // For individual emails, use the actual target ID
                if (mTargets != null && !mTargets.isEmpty()) {
                    contactId = mTargets.get(0).id != null ? mTargets.get(0).id : mTargets.get(0).name;
                    contactName = mTargets.get(0).name;
                } else {
                    contactId = "email_target";
                    contactName = "Email Target";
                }
            }
            
            AppSingleton.getInstance(getApplicationContext()).getDatabaseHelper().addCall(
                mIssue.id, mIssue.name, contactId, contactName, "contact", "email");
            
            // Report to the server (same as calls)
            AppSingleton.getInstance(getApplicationContext()).getJsonController().reportCall(
                mIssue.id, contactId, "contact", "email");
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
