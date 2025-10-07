package org.a5calls.android.a5calls.controller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.util.Log;

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
import org.a5calls.android.a5calls.model.Target;
import org.a5calls.android.a5calls.net.FiveCallsApi;
import org.a5calls.android.a5calls.util.AnalyticsManager;
import org.a5calls.android.a5calls.util.ScriptReplacements;
import org.a5calls.android.a5calls.util.MarkdownUtil;
import org.a5calls.android.a5calls.view.GridItemDecoration;

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
                    // Skip doesn't send anything to backend, just navigate to next
                    navigateToNextTargetOrComplete();
                } else {
                    // Normal outcomes report the call and then navigate
                    reportCall(outcome, address);
                    navigateToNextTargetOrComplete();
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
        outcomeAdapter.setEnabled(false);
        Target target = mIssue.targets.get(mActiveTargetIndex);
        String contactId = target.id != null ? target.id : target.name;
        String contactName = target.name;
        
        AppSingleton.getInstance(getApplicationContext()).getDatabaseHelper().addCall(mIssue.id,
                mIssue.name, contactId, contactName, outcome.status.toString(), address);
        AppSingleton.getInstance(getApplicationContext()).getJsonController().reportCall(
                mIssue.id, contactId, outcome.label, address);
    }

    private void navigateToNextTargetOrComplete() {
        // Find the next target in the list
        int nextTargetIndex = findNextTarget();

        if (nextTargetIndex != -1) {
            // Navigate to next target
            launchNextTarget(nextTargetIndex);
        } else {
            // No more targets, return to issue list
            returnToIssue();
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

    private void launchNextTarget(int nextTargetIndex) {
        Intent intent = new Intent(this, CorporateCallActivity.class);
        intent.putExtra(KEY_ISSUE, mIssue);
        intent.putExtra(KEY_ACTIVE_TARGET_INDEX, nextTargetIndex);
        intent.putExtra(KEY_ADDRESS, getIntent().getStringExtra(KEY_ADDRESS));
        intent.putExtra(KEY_LOCATION_NAME, getIntent().getStringExtra(KEY_LOCATION_NAME));

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
        Intent upIntent = NavUtils.getParentActivityIntent(this);
        if (upIntent == null) {
            return;
        }
        upIntent.putExtra(IssueActivity.KEY_ISSUE, mIssue);
        setResult(IssueActivity.RESULT_OK, upIntent);
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
}
