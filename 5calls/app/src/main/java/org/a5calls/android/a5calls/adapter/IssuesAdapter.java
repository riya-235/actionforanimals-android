package org.a5calls.android.a5calls.adapter;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.model.Category;
import org.a5calls.android.a5calls.model.Contact;
import org.a5calls.android.a5calls.model.DatabaseHelper;
import org.a5calls.android.a5calls.model.Issue;
import org.a5calls.android.a5calls.model.IssueStats;
import org.a5calls.android.a5calls.model.Target;
import org.a5calls.android.a5calls.util.ContentChangeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

public class IssuesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    // TODO: Use an enum.
    public static final int NO_ERROR = 10;
    public static final int ERROR_REQUEST = 11;
    public static final int ERROR_ADDRESS = 12;
    public static final int NO_ISSUES_YET = 13;
    public static final int ERROR_SEARCH_NO_MATCH = 14;

    private static final int VIEW_TYPE_EMPTY_REQUEST = 0;
    private static final int VIEW_TYPE_ISSUE = 1;
    private static final int VIEW_TYPE_EMPTY_ADDRESS = 2;
    private static final int VIEW_TYPE_NO_SEARCH_MATCH = 3;

    private List<Issue> mIssues = new ArrayList<>();
    private List<Issue> mAllIssues = new ArrayList<>();
    private int mErrorType = NO_ISSUES_YET;
    private int mAddressErrorType = NO_ISSUES_YET;

    private List<Contact> mContacts = new ArrayList<>();
    private final Activity mActivity;
    private final Callback mCallback;

    public interface Callback {

        void refreshIssues();

        void showLocationBottomSheet();

        void launchSearchDialog();

        void startIssueActivity(Context context, Issue issue);
    }

    public IssuesAdapter(Activity activity, Callback callback) {
        mActivity = activity;
        mCallback = callback;
    }

    // Action For Animals: Not sure why this was added by commit af3e4f03d8cff8c9ea68c02ad54c01636eb355a2
    // public void setIssues(List<Issue> issues) {
    //     this.mIssues = issues;  // or whatever the internal issue list is called
    //     notifyDataSetChanged();
    // }

    /**
     * Sets the full list of available issues. Does not update the visible list unless there
     * is an error; {@code #setFilterAndSearch} should be called separately to update the
     * visible list.
     * @param issues The full list of available issues.
     * @param errorType The error, if there is one.
     */
    public void setAllIssues(List<Issue> issues, int errorType) {
        mAllIssues = issues;
        mErrorType = errorType;
        if (mErrorType != NO_ERROR) {
            mIssues.clear();
            notifyDataSetChanged();
        }
    }

    public void setAddressError(int error) {
        mAddressErrorType = error;
        mContacts.clear();
        if (!mAllIssues.isEmpty()) {
            notifyDataSetChanged();
        }
    }

    public void setContacts(List<Contact> contacts, int error) {
        // Check if the contacts have returned after the issues list. If so, notify data set
        // changed.
        mAddressErrorType = error;
        boolean notify = false;
        if (!mAllIssues.isEmpty() && mContacts.isEmpty()) {
            notify = true;
        }
        mContacts = contacts;
        if (notify) {
            notifyDataSetChanged();
        }
    }

    public void setFilterAndSearch(String filterText, String searchText) {
        if (mErrorType == ERROR_SEARCH_NO_MATCH) {
            // If we previously had a search error, reset it: this is a new
            // filter or search.
            mErrorType = NO_ERROR;
        }

        // Start with all issues or filter by category first
        List<Issue> filteredIssues;
        if (TextUtils.equals(filterText,
                mActivity.getResources().getString(R.string.all_issues_filter))) {
            // Include everything
            filteredIssues = mAllIssues;
        } else if (TextUtils.equals(filterText,
                mActivity.getResources().getString(R.string.top_issues_filter))) {
            filteredIssues = filterActiveIssues();
        } else {
            // Filter by the category string
            filteredIssues = filterIssuesByCategory(filterText);
        }

        // Then apply search text filter if needed
        if (!TextUtils.isEmpty(searchText)) {
            mIssues = filterIssuesBySearchText(searchText, filteredIssues);
            // If there's no other error, show a search error.
            if (mIssues.isEmpty() && mErrorType == NO_ERROR) {
                mErrorType = ERROR_SEARCH_NO_MATCH;
            }
        } else {
            // No search text, use category-filtered results
            mIssues = (ArrayList<Issue>) filteredIssues;
        }

        notifyDataSetChanged();
    }

    public void setFilterAndSearchWithCategories(String filterText, String searchText, Set<String> selectedCategories) {
        if (mErrorType == ERROR_SEARCH_NO_MATCH) {
            // If we previously had a search error, reset it: this is a new
            // filter or search.
            mErrorType = NO_ERROR;
        }

        // Start with all issues or filter by category first
        List<Issue> filteredIssues;
        if (selectedCategories.isEmpty()) {
            // No categories selected - show all or apply other filters
            if (TextUtils.equals(filterText,
                    mActivity.getResources().getString(R.string.all_issues_filter))) {
                filteredIssues = mAllIssues;
            } else if (TextUtils.equals(filterText,
                    mActivity.getResources().getString(R.string.top_issues_filter))) {
                filteredIssues = filterActiveIssues();
            } else {
                filteredIssues = mAllIssues;
            }
        } else {
            // Filter by selected categories (multi-select)
            filteredIssues = filterIssuesByMultipleCategories(selectedCategories);
        }
        
        // Then apply search text filter if needed
        if (!TextUtils.isEmpty(searchText)) {
            mIssues = filterIssuesBySearchText(searchText, filteredIssues);
            // If there's no other error, show a search error.
            if (mIssues.isEmpty() && mErrorType == NO_ERROR) {
                mErrorType = ERROR_SEARCH_NO_MATCH;
            }
        } else {
            // No search text, use category-filtered results
            mIssues = (ArrayList<Issue>) filteredIssues;
        }
        
        notifyDataSetChanged();
    }

    @VisibleForTesting
    public static ArrayList<Issue> filterIssuesBySearchText(String searchText, List<Issue> allIssues) {
        ArrayList<Issue> tempIssues = new ArrayList<>();
        // Should we .trim() the whitespace?
        String lowerSearchText = searchText.toLowerCase();
        for (Issue issue : allIssues) {
            // Search the name and the categories for the search term.
            if (issue.name.toLowerCase().contains(lowerSearchText)) {
                tempIssues.add(issue);
            } else {
                boolean found = false;
                for (int i = 0; i < issue.categories.length; i++) {
                    if (issue.categories[i].name.toLowerCase().contains(lowerSearchText)) {
                        tempIssues.add(issue);
                        found = true;
                        break;
                    }
                }
                if (found) {
                    continue;
                }
                // Search through the issue's reason for words that start with the
                // search text. This is better than substring matching so that text
                // like "ice" doesn't match "averice" but just ICE.
                Pattern pattern = Pattern.compile("\\s" + searchText,
                        Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(issue.reason).find()) {
                    tempIssues.add(issue);
                }
            }
        }
        return tempIssues;
    }

    private ArrayList<Issue> filterActiveIssues() {
        // Add only the active ones.
        ArrayList<Issue> tempIssues = new ArrayList<>();
        for (Issue issue : mAllIssues) {
            if (issue.isActive()) {
                tempIssues.add(issue);
            }
        }
        return tempIssues;
    }

    private ArrayList<Issue> filterIssuesByMultipleCategories(Set<String> selectedCategories) {
        ArrayList<Issue> tempIssues = new ArrayList<>();
        for (Issue issue : mAllIssues) {
            // Check if issue matches any of the selected categories
            for (String category : selectedCategories) {
                if (issueMatchesCategory(issue, category)) {
                    tempIssues.add(issue);
                    break; // Don't add the same issue multiple times
                }
            }
        }
        return tempIssues;
    }


    private ArrayList<Issue> filterIssuesByCategory(String activeCategory) {
        ArrayList<Issue> tempIssues = new ArrayList<>();
        for (Issue issue : mAllIssues) {
            if (issueMatchesCategory(issue, activeCategory)) {
                tempIssues.add(issue);
            }
        }
        return tempIssues;
    }
    
    private boolean issueMatchesCategory(Issue issue, String filterCategory) {
        for (Category category : issue.categories) {
            String categoryName = category.name.toLowerCase();
            String filter = filterCategory.toLowerCase();
            
            // Map filter names to actual category names (like iOS CategoryHelper)
            switch (filter) {
                case "wildlife":
                    if (categoryName.contains("wildlife")) return true;
                    break;
                case "testing":
                    if (categoryName.contains("testing") || categoryName.contains("animal testing")) return true;
                    break;
                case "companion":
                    if (categoryName.contains("companion") || categoryName.contains("companion animals")) return true;
                    break;
                case "entertainment":
                    if (categoryName.contains("entertainment")) return true;
                    break;
                case "climate":
                    if (categoryName.contains("climate")) return true;
                    break;
                case "farmed":
                    if (categoryName.contains("farmed") || categoryName.contains("farm")) return true;
                    break;
                default:
                    // Fallback to exact match for other categories
                    if (TextUtils.equals(filterCategory, category.name)) return true;
                    break;
            }
        }
        return false;
    }

    public void updateIssue(Issue issue) {
        for (int i = 0; i < mIssues.size(); i++) {
            if (TextUtils.equals(issue.id, mIssues.get(i).id)) {
                mIssues.set(i, issue);
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void updateIssueCount(String issueId, int updatedCount) {
        Log.d("IssuesAdapter", "updateIssueCount called for issue: " + issueId + " with count: " + updatedCount);

        // Update in both mAllIssues and mIssues lists
        for (Issue issue : mAllIssues) {
            if (TextUtils.equals(issue.id, issueId)) {
                if (issue.stats == null) {
                    issue.stats = new IssueStats();
                }
                issue.stats.total_actions = updatedCount;
                Log.d("IssuesAdapter", "Updated issue " + issueId + " count to " + updatedCount + " in mAllIssues");
                break;
            }
        }

        // Update the filtered list
        for (int i = 0; i < mIssues.size(); i++) {
            if (TextUtils.equals(mIssues.get(i).id, issueId)) {
                Issue issue = mIssues.get(i);
                if (issue.stats == null) {
                    issue.stats = new IssueStats();
                }
                issue.stats.total_actions = updatedCount;
                Log.d("IssuesAdapter", "Updated issue " + issueId + " count to " + updatedCount + " in mIssues at position " + i);
                return;
            }
        }
    }

    public boolean hasContacts() {
        return !mContacts.isEmpty();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_EMPTY_REQUEST) {
            View empty = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.empty_issues_view, parent, false);
            return new EmptyRequestViewHolder(empty);
        } else if (viewType == VIEW_TYPE_EMPTY_ADDRESS) {
            View empty = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.empty_issues_address_view, parent, false);
            return new EmptyAddressViewHolder(empty);
        } else if (viewType == VIEW_TYPE_NO_SEARCH_MATCH) {
            View empty = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.empty_issues_search_view, parent, false);
            return new EmptySearchViewHolder(empty);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.issue_view, parent, false);
            return new IssueViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        int type = getItemViewType(position);
        if (type == VIEW_TYPE_ISSUE) {
            IssueViewHolder vh = (IssueViewHolder) holder;
            final Issue issue = mIssues.get(position);
            vh.name.setText(issue.name);

            // Apply status-based styling like iOS
            applyStatusStyling(issue, vh);
            
            // Show/hide content change indicator
            ContentChangeManager contentChangeManager = new ContentChangeManager(mActivity);
            boolean hasContentChanged = contentChangeManager.hasIssueChanged(issue.id);
            vh.contentChangeIndicator.setVisibility(hasContentChanged ? View.VISIBLE : View.INVISIBLE);

            vh.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mCallback.startIssueActivity(holder.itemView.getContext(), issue);
                }
            });

            if (mAddressErrorType != NO_ERROR) {
                // If there was an address error, clear the number of calls to make.
                vh.numCalls.setText("");
                vh.previousCallStats.setVisibility(View.GONE);
                return;
            }

            // Sometimes an issue is shown with no contact areas in order to
            // inform users that a major vote or change has happened.
            if (issue.contactAreas.isEmpty()) {
                vh.numCalls.setText(
                        mActivity.getResources().getString(R.string.no_contact_areas_message));
                vh.previousCallStats.setVisibility(View.GONE);
                return;
            }

            // Use centralized filtering logic from Issue model
            issue.filterContactsByAreas(mContacts);
            
            // Setup category icon and completion status
            setupCategoryIcon(issue, vh);
            
            // Create circular avatars and action count (iOS-style)
            setupContactAvatars(issue, vh);
            setupActionCount(issue, vh);
            
            // Hide old call stats - we're using the new iOS-style action count
            vh.numCalls.setVisibility(View.GONE);
            vh.previousCallStats.setVisibility(View.GONE);
            // displayPreviousCallStats(issue, vh); // Disabled - using new iOS-style display
        } else if (type == VIEW_TYPE_EMPTY_REQUEST) {
            EmptyRequestViewHolder vh = (EmptyRequestViewHolder) holder;
            vh.refreshButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mCallback.refreshIssues();
                }
            });
        } else if (type == VIEW_TYPE_EMPTY_ADDRESS) {
            EmptyAddressViewHolder vh = (EmptyAddressViewHolder) holder;
            vh.locationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mCallback.showLocationBottomSheet();
                }
            });
        } else if (type == VIEW_TYPE_NO_SEARCH_MATCH) {
            EmptySearchViewHolder vh = (EmptySearchViewHolder) holder;
            vh.searchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mCallback.launchSearchDialog();
                }
            });
        }
    }

    @Override
    public void onViewRecycled(RecyclerView.ViewHolder holder) {
        if (holder instanceof IssueViewHolder) {
            holder.itemView.setOnClickListener(null);
        } else if (holder instanceof EmptyRequestViewHolder) {
            ((EmptyRequestViewHolder) holder).refreshButton.setOnClickListener(null);
        } else if (holder instanceof EmptyAddressViewHolder) {
            ((EmptyAddressViewHolder) holder).locationButton.setOnClickListener(null);
        } else if (holder instanceof EmptySearchViewHolder) {
            ((EmptySearchViewHolder) holder).searchButton.setOnClickListener(null);
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        if (mErrorType == ERROR_REQUEST || mErrorType == ERROR_SEARCH_NO_MATCH) {
            // For these special types of errors, we will hide the issues.
            return 1;
        }
        return mIssues.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (mIssues.isEmpty() && position == 0) {
            if (mErrorType == ERROR_REQUEST) {
                return VIEW_TYPE_EMPTY_REQUEST;
            }
            if (mErrorType == ERROR_SEARCH_NO_MATCH) {
                return VIEW_TYPE_NO_SEARCH_MATCH;
            }
        }
        return VIEW_TYPE_ISSUE;
    }

    private void applyStatusStyling(Issue issue, IssueViewHolder vh) {
        boolean isActive = issue.isActive();

        if (!isActive) {
            // Gray out inactive issues
            vh.itemView.setAlpha(0.5f);
            vh.name.setAlpha(0.5f);
        } else {
            // Active issues - normal appearance
            vh.itemView.setAlpha(1.0f);
            vh.name.setAlpha(1.0f);
        }
    }

    private void displayPreviousCallStats(Issue issue, IssueViewHolder vh) {
        DatabaseHelper dbHelper =  AppSingleton.getInstance(mActivity.getApplicationContext())
                .getDatabaseHelper();
        // Calls ever made.
        int totalUserCalls = dbHelper.getTotalCallsForIssueAndContacts(issue.id, issue.contacts);

        // Calls today only.
        int callsLeft = issue.contacts.size();
        for (Contact contact : issue.contacts) {
            if(dbHelper.hasContacted(issue.id, contact.id)) {
                callsLeft--;
            }
        }
        if (totalUserCalls == 0) {
            // The user has never called on this issue before. Show a simple number of calls
            // text, without the word "today".
            vh.previousCallStats.setVisibility(View.GONE);
            if (callsLeft == 1) {
                vh.numCalls.setText(
                        mActivity.getResources().getString(R.string.call_count_one));
            } else {
                vh.numCalls.setText(String.format(
                        mActivity.getResources().getString(R.string.call_count), callsLeft));
            }
        } else {
            vh.previousCallStats.setVisibility(View.VISIBLE);

            // Previous call stats
            if (totalUserCalls == 1) {
                vh.previousCallStats.setText(mActivity.getResources().getString(
                        R.string.previous_call_count_one));
            } else {
                vh.previousCallStats.setText(
                        mActivity.getResources().getString(
                                R.string.previous_call_count_many, totalUserCalls));
            }

            // Calls to make today.
            if (callsLeft == 0) {
                vh.numCalls.setText(
                        mActivity.getResources().getString(R.string.call_count_today_done));
            } else if (callsLeft == 1) {
                vh.numCalls.setText(
                        mActivity.getResources().getString(R.string.call_count_today_one));
            } else {
                vh.numCalls.setText(String.format(
                        mActivity.getResources().getString(R.string.call_count_today), callsLeft));
            }
        }
    }

    private void setupCategoryIcon(Issue issue, IssueViewHolder vh) {
        // Get the primary category for the issue
        String categoryName = getPrimaryCategoryName(issue);

        // Set the category icon (remove color filter to show original icon)
        int iconResource = getCategoryIconResource(categoryName);
        vh.categoryIcon.setImageResource(iconResource);

        // Show checkmark for success status (like iOS)
        if (issue.isSuccess()) {
            vh.categoryCheckmark.setVisibility(View.VISIBLE);
            // Don't override the src - it's already set to checked_small (white checkmark)
            // Don't override the background - it's already set to green_checkmark_circle
        } else {
            vh.categoryCheckmark.setVisibility(View.GONE);
        }
    }
    
    private String getPrimaryCategoryName(Issue issue) {
        if (issue.categories != null && issue.categories.length > 0) {
            return issue.categories[0].name.toLowerCase();
        }
        return "generic";
    }
    
    private int getCategoryIconResource(String categoryName) {
        // Map category names to drawable resources (using underscores for Android)
        switch (categoryName.toLowerCase()) {
            case "wildlife":
                return R.drawable.category_wildlife;
            case "animal testing":
            case "testing":
                return R.drawable.category_testing;
            case "companion animals":
            case "companion":
                return R.drawable.category_companion;
            case "entertainment":
                return R.drawable.category_entertainment;
            case "climate":
                return R.drawable.category_climate;
            case "farmed animals":
            case "farmed":
                return R.drawable.category_farmed;
            default:
                return R.drawable.category_generic;
        }
    }
    
    private int getCategoryColor(String categoryName) {
        // Generate colors for different categories
        switch (categoryName.toLowerCase()) {
            case "wildlife":
                return 0xFF4CAF50; // Green
            case "animal testing":
            case "testing":
                return 0xFF9C27B0; // Purple
            case "companion animals":
            case "companion":
                return 0xFFFF9800; // Orange
            case "entertainment":
                return 0xFFE91E63; // Pink
            case "climate":
                return 0xFF2196F3; // Blue
            case "farmed animals":
            case "farmed":
                return 0xFF795548; // Brown
            default:
                return 0xFF607D8B; // Blue Grey
        }
    }
    
    private boolean isCampaignCompleted(Issue issue) {
        if (issue.contacts == null || issue.contacts.isEmpty()) {
            return false;
        }
        
        DatabaseHelper dbHelper = AppSingleton.getInstance(mActivity).getDatabaseHelper();
        for (Contact contact : issue.contacts) {
            if (!dbHelper.hasContacted(issue.id, contact.id)) {
                return false; // At least one contact hasn't been called
            }
        }
        return true; // All contacts have been called
    }
    
    private boolean isBatchEmailCampaign(Issue issue) {
        // Check if this is a batch email campaign
        if (issue.actions != null && issue.actions.email != null) {
            String distributionMethod = issue.actions.email.distributionMethod;
            // If distributionMethod is null or "batch", it's a batch email
            return distributionMethod == null || "batch".equals(distributionMethod);
        }
        // Default to batch if no email action is defined
        return true;
    }
    
    private boolean isCorporateCampaignCompleted(Issue issue) {
        // Check if this corporate campaign has been completed
        DatabaseHelper dbHelper = AppSingleton.getInstance(mActivity).getDatabaseHelper();
        
        if (isBatchEmailCampaign(issue)) {
            // For batch emails, check if any email action has been taken
            // Always use the first target's ID and warn if it's not there
            if (issue.targets == null || issue.targets.isEmpty()) {
                android.util.Log.w("IssuesAdapter", "Corporate campaign " + issue.id + " has no targets for batch email completion check");
                return false;
            }
            
            Target firstTarget = issue.targets.get(0);
            String contactId = firstTarget.id != null ? firstTarget.id : firstTarget.name;
            
            if (contactId == null) {
                android.util.Log.w("IssuesAdapter", "First target in corporate campaign " + issue.id + " has no ID or name");
                return false;
            }
            
            return dbHelper.hasContacted(issue.id, contactId);
        } else {
            // For individual emails, check if all targets have been contacted
            if (issue.targets == null || issue.targets.isEmpty()) {
                android.util.Log.w("IssuesAdapter", "Corporate campaign " + issue.id + " has no targets for individual email completion check");
                return false;
            }
            
            for (Target target : issue.targets) {
                String contactId = target.id != null ? target.id : target.name;
                if (contactId == null) {
                    android.util.Log.w("IssuesAdapter", "Target in corporate campaign " + issue.id + " has no ID or name");
                    return false;
                }
                if (!dbHelper.hasContacted(issue.id, contactId)) {
                    return false; // At least one target hasn't been contacted
                }
            }
            return true; // All targets have been contacted
        }
    }

    private void setupContactAvatars(Issue issue, IssueViewHolder vh) {
        vh.avatarsContainer.removeAllViews();
        
        // Check if this is a corporate campaign
        boolean isCorporateCampaign = "CORPORATE".equals(issue.contactType);
        
        if (isCorporateCampaign) {
            // For corporate campaigns, show business/person icon based on distribution method
            setupCorporateAvatars(issue, vh);
        } else {
            // For regular campaigns, show representative avatars
            setupRepresentativeAvatars(issue, vh);
        }
        
        // Set action type tag based on campaign actions
        setupActionTypeTag(issue, vh);
    }
    
    private void setupCorporateAvatars(Issue issue, IssueViewHolder vh) {
        if (issue.targets == null || issue.targets.isEmpty()) {
            vh.avatarsContainer.setVisibility(View.GONE);
            return;
        }
        
        vh.avatarsContainer.setVisibility(View.VISIBLE);
        
        // Show up to 3 targets with overlapping avatars
        int maxAvatars = Math.min(3, issue.targets.size());
        int avatarSize = 32; // dp
        int overlapOffset = 8; // dp
        
        boolean isBatchEmail = isBatchEmailCampaign(issue);
        
        for (int i = 0; i < maxAvatars; i++) {
            Target target = issue.targets.get(i);
            View avatarView = LayoutInflater.from(mActivity).inflate(R.layout.contact_avatar, vh.avatarsContainer, false);
            
            ImageView avatarImage = avatarView.findViewById(R.id.avatar_image);
            TextView initials = avatarView.findViewById(R.id.avatar_initials);
            ImageView checkmarkOverlay = avatarView.findViewById(R.id.checkmark_overlay);
            
            // Position avatars with overlap (iOS-style)
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                (int) (avatarSize * mActivity.getResources().getDisplayMetrics().density),
                (int) (avatarSize * mActivity.getResources().getDisplayMetrics().density)
            );
            params.leftMargin = (int) (i * (avatarSize - overlapOffset) * mActivity.getResources().getDisplayMetrics().density);
            avatarView.setLayoutParams(params);
            
            // Set z-order so later avatars appear on top
            avatarView.setTranslationZ(maxAvatars - i);
            
            // Check if this target has been completed
            boolean hasBeenCompleted = false;
            if (isBatchEmail) {
                // For batch emails, check if any email action has been taken
                hasBeenCompleted = isCorporateCampaignCompleted(issue);
            } else {
                // For individual emails, check if this specific target has been contacted
                String contactId = target.id != null ? target.id : target.name;
                if (contactId != null) {
                    DatabaseHelper dbHelper = AppSingleton.getInstance(mActivity).getDatabaseHelper();
                    hasBeenCompleted = dbHelper.hasContacted(issue.id, contactId);
                }
            }
            
            if (hasBeenCompleted) {
                // Show green checkmark instead of icon
                avatarImage.setVisibility(View.GONE);
                initials.setVisibility(View.GONE);
                checkmarkOverlay.setVisibility(View.VISIBLE);
            } else {
                // Show normal avatar (colored circle with no icon)
                checkmarkOverlay.setVisibility(View.GONE);
                avatarImage.setVisibility(View.VISIBLE);
                
                // Clear any existing image and show just a colored circle
                avatarImage.setImageDrawable(null);
                
                // Generate a consistent color for the circle
                String targetName = target.name != null ? target.name : "Target";
                int avatarColor = generateAvatarColor(targetName);
                avatarImage.setColorFilter(avatarColor, PorterDuff.Mode.SRC_IN);
            }
            
            vh.avatarsContainer.addView(avatarView);
        }
        
        // Update container width to fit all avatars
        int totalWidth = (int) ((maxAvatars * (avatarSize - overlapOffset) + overlapOffset) * mActivity.getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams containerParams = vh.avatarsContainer.getLayoutParams();
        containerParams.width = totalWidth;
        vh.avatarsContainer.setLayoutParams(containerParams);
    }
    
    private void setupRepresentativeAvatars(Issue issue, IssueViewHolder vh) {
        if (issue.contacts == null || issue.contacts.isEmpty()) {
            vh.avatarsContainer.setVisibility(View.GONE);
            return;
        }
        
        vh.avatarsContainer.setVisibility(View.VISIBLE);
        
        // Show up to 3 contacts with overlapping avatars
        int maxAvatars = Math.min(3, issue.contacts.size());
        int avatarSize = 32; // dp
        int overlapOffset = 8; // dp
        
        for (int i = 0; i < maxAvatars; i++) {
            Contact contact = issue.contacts.get(i);
            View avatarView = LayoutInflater.from(mActivity).inflate(R.layout.contact_avatar, vh.avatarsContainer, false);
            
            ImageView avatarImage = avatarView.findViewById(R.id.avatar_image);
            TextView initials = avatarView.findViewById(R.id.avatar_initials);
            ImageView checkmarkOverlay = avatarView.findViewById(R.id.checkmark_overlay);
            
            // Position avatars with overlap (iOS-style)
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                (int) (avatarSize * mActivity.getResources().getDisplayMetrics().density),
                (int) (avatarSize * mActivity.getResources().getDisplayMetrics().density)
            );
            params.leftMargin = (int) (i * (avatarSize - overlapOffset) * mActivity.getResources().getDisplayMetrics().density);
            avatarView.setLayoutParams(params);
            
            // Set z-order so later avatars appear on top
            avatarView.setTranslationZ(maxAvatars - i);
            
            // Check if user has called this representative today
            DatabaseHelper dbHelper = AppSingleton.getInstance(mActivity).getDatabaseHelper();
            boolean hasCalledToday = dbHelper.hasContacted(issue.id, contact.id);
            
            if (hasCalledToday) {
                // Show green checkmark instead of photo
                avatarImage.setVisibility(View.GONE);
                initials.setVisibility(View.GONE);
                checkmarkOverlay.setVisibility(View.VISIBLE);
            } else {
                // Show normal avatar (photo or initials)
                checkmarkOverlay.setVisibility(View.GONE);
                avatarImage.setVisibility(View.VISIBLE);
                
                // Load representative photo using Glide
                if (contact.photoURL != null && !TextUtils.isEmpty(contact.photoURL.toString())) {
                    // Load actual representative photo
                    com.bumptech.glide.Glide.with(mActivity)
                        .load(contact.photoURL)
                        .circleCrop()
                        .placeholder(generateAvatarColor(contact.name))
                        .error(generateAvatarColor(contact.name))
                        .into(avatarImage);
                } else {
                    // Fallback to colored background with initials
                    String contactInitials = getContactInitials(contact.name);
                    int avatarColor = generateAvatarColor(contact.name);
                    
                    initials.setText(contactInitials);
                    initials.setVisibility(View.VISIBLE);
                    avatarImage.setColorFilter(avatarColor, PorterDuff.Mode.SRC_IN);
                }
            }
            
            vh.avatarsContainer.addView(avatarView);
        }
        
        // Update container width to fit all avatars
        int totalWidth = (int) ((maxAvatars * (avatarSize - overlapOffset) + overlapOffset) * mActivity.getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams containerParams = vh.avatarsContainer.getLayoutParams();
        containerParams.width = totalWidth;
        vh.avatarsContainer.setLayoutParams(containerParams);
    }
    
    private void setupActionTypeTag(Issue issue, IssueViewHolder vh) {
        TextView actionTag = vh.itemView.findViewById(R.id.action_type_tag);
        
        // Check if this is a corporate campaign
        boolean isCorporateCampaign = "CORPORATE".equals(issue.contactType);
        
        if (isCorporateCampaign) {
            // For corporate campaigns, check what actions are enabled
            boolean emailEnabled = issue.actions != null && 
                                  issue.actions.email != null && 
                                  issue.actions.email.enabled;
            boolean callEnabled = issue.actions != null && 
                                 issue.actions.call != null && 
                                 issue.actions.call.enabled;
            
            if (emailEnabled && callEnabled) {
                // Both enabled - show "Email" (email takes priority)
                actionTag.setText("Email");
                actionTag.setBackgroundResource(R.drawable.action_tag_email_background);
                actionTag.setTextColor(mActivity.getResources().getColor(R.color.badge_email_text, null));
                actionTag.setVisibility(View.VISIBLE);
            } else if (emailEnabled) {
                // Only email enabled
                actionTag.setText("Email");
                actionTag.setBackgroundResource(R.drawable.action_tag_email_background);
                actionTag.setTextColor(mActivity.getResources().getColor(R.color.badge_email_text, null));
                actionTag.setVisibility(View.VISIBLE);
            } else if (callEnabled) {
                // Only call enabled
                actionTag.setText("Call");
                actionTag.setBackgroundResource(R.drawable.action_tag_call_background);
                actionTag.setTextColor(mActivity.getResources().getColor(R.color.badge_call_text, null));
                actionTag.setVisibility(View.VISIBLE);
            } else {
                // No actions enabled
                actionTag.setVisibility(View.GONE);
            }
        } else {
            // For representative campaigns, always show "Call"
            actionTag.setText("Call");
            actionTag.setBackgroundResource(R.drawable.action_tag_call_background);
            actionTag.setTextColor(mActivity.getResources().getColor(R.color.badge_call_text, null));
            actionTag.setVisibility(View.VISIBLE);
        }
    }
    
    private void setupActionCount(Issue issue, IssueViewHolder vh) {
        // Use the total campaign actions from issue stats
        int totalActions = 0;
        if (issue.stats != null) {
            totalActions = issue.stats.total_actions;
        }

        
        String actionText;
        if (totalActions == 0) {
            actionText = mActivity.getString(R.string.no_actions_taken);
        } else if (totalActions >= 1000) {
            double thousands = totalActions / 1000.0;
            actionText = mActivity.getString(R.string.actions_taken_thousands, thousands);
        } else {
            actionText = mActivity.getString(R.string.actions_taken, totalActions);
        }
        
        vh.actionsTaken.setText(actionText);
    }
    
    private String getContactInitials(String name) {
        if (TextUtils.isEmpty(name)) return "?";
        
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        } else {
            String initials = "";
            if (parts.length >= 1 && !parts[0].isEmpty()) {
                initials += parts[0].charAt(0);
            }
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                initials += parts[1].charAt(0);
            }
            return initials.toUpperCase();
        }
    }
    
    private int generateAvatarColor(String name) {
        // Generate a consistent color based on the contact name
        int[] colors = {
            0xFF3498DB, // Blue
            0xFF9B59B6, // Purple  
            0xFFE91E63, // Pink
            0xFF4CAF50, // Green
            0xFFFF9800, // Orange
            0xFFF44336, // Red
            0xFF607D8B, // Blue Grey
            0xFF795548  // Brown
        };
        
        int hash = name.hashCode();
        int index = Math.abs(hash) % colors.length;
        return colors[index];
    }

    private static class IssueViewHolder extends RecyclerView.ViewHolder {
    public TextView name;
    public TextView numCalls;
    public TextView previousCallStats;
    public FrameLayout avatarsContainer;
    public TextView actionsTaken;
    public ImageView categoryIcon;
    public ImageView categoryCheckmark;
    public View contentChangeIndicator;

    public IssueViewHolder(View itemView) {
        super(itemView);
        name = (TextView) itemView.findViewById(R.id.issue_name);
        numCalls = (TextView) itemView.findViewById(R.id.issue_call_count);
        previousCallStats = (TextView) itemView.findViewById(R.id.previous_call_stats);
        avatarsContainer = (FrameLayout) itemView.findViewById(R.id.avatars_container);
        actionsTaken = (TextView) itemView.findViewById(R.id.actions_taken);
        categoryIcon = (ImageView) itemView.findViewById(R.id.category_icon);
        categoryCheckmark = (ImageView) itemView.findViewById(R.id.category_checkmark);
        contentChangeIndicator = itemView.findViewById(R.id.content_change_indicator);
    }
}

// TODO: Combine EmptyRequestViewHolder and EmptyAddressViewHolder, change strings dynamically.
private static class EmptyRequestViewHolder extends RecyclerView.ViewHolder {
    public Button refreshButton;

    public EmptyRequestViewHolder(View itemView) {
        super(itemView);
        refreshButton = (Button) itemView.findViewById(R.id.refresh_btn);
        // Tinting the compound drawable only works API 23+, so do this manually.
        refreshButton.getCompoundDrawablesRelative()[0].mutate().setColorFilter(
                refreshButton.getResources().getColor(R.color.colorAccent),
                PorterDuff.Mode.MULTIPLY);
    }
}

private static class EmptyAddressViewHolder extends RecyclerView.ViewHolder {
    public Button locationButton;

    public EmptyAddressViewHolder(View itemView) {
        super(itemView);
        locationButton = (Button) itemView.findViewById(R.id.location_btn);
        // Tinting the compound drawable only works API 23+, so do this manually.
        locationButton.getCompoundDrawablesRelative()[0].mutate().setColorFilter(
                locationButton.getResources().getColor(R.color.colorAccent),
                PorterDuff.Mode.MULTIPLY);
    }
}

private static class EmptySearchViewHolder extends RecyclerView.ViewHolder {
    public Button searchButton;

    public EmptySearchViewHolder(View itemView) {
        super(itemView);
        searchButton = (Button) itemView.findViewById(R.id.search_btn);
        // Tinting the compound drawable only works API 23+, so do this manually.
        searchButton.getCompoundDrawablesRelative()[0].mutate().setColorFilter(
                searchButton.getResources().getColor(R.color.colorAccent),
                PorterDuff.Mode.MULTIPLY);
    }
}

}
