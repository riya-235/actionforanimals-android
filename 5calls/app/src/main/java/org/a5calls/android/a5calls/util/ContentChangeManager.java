package org.a5calls.android.a5calls.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.a5calls.android.a5calls.model.Issue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages content change detection for issues, similar to iOS app functionality.
 * Tracks which issues have changed content and persists the state.
 */
public class ContentChangeManager {
    private static final String TAG = "ContentChangeManager";
    private static final String PREFS_NAME = "content_changes";
    private static final String KEY_CHANGED_IDS = "changed_issue_ids";
    private static final String KEY_LAST_ISSUES_HASH = "last_issues_hash";
    private static final String KEY_ISSUE_REASONS = "issue_reasons";
    
    private final SharedPreferences prefs;
    
    public ContentChangeManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Check for content changes in issues and update the changed IDs list.
     * This should be called when new issues are fetched from the server.
     * Preserves existing green dots that haven't been manually cleared by the user.
     */
    public void updateChangedIssues(List<Issue> newIssues) {
        
        // Start with existing changed IDs (preserves user-viewed state)
        Set<String> changedIds = getChangedIds();

        // Get previously saved issue reasons
        String savedReasons = prefs.getString(KEY_ISSUE_REASONS, "");

        // If this is the first run (no previous data), don't mark anything as changed
        if (savedReasons.isEmpty()) {
            saveIssueReasons(newIssues);
            // Keep existing changedIds as-is (don't overwrite)
            return;
        }

        // Parse saved reasons into a map of issueId -> reason
        Map<String, String> oldReasons = parseIssueReasons(savedReasons);

        // Compare each new issue with saved reasons
        for (Issue newIssue : newIssues) {
            String oldReason = oldReasons.get(newIssue.id);

            if (oldReason == null) {
                // New issue - mark as changed
                changedIds.add(newIssue.id);
            } else if (!oldReason.equals(newIssue.reason)) {
                // Content changed - mark as changed (even if previously cleared)
                changedIds.add(newIssue.id);
            }
            // If content is the same, preserve existing state (don't add or remove)
        }

        // Remove any changed IDs for issues that no longer exist
        Set<String> currentIssueIds = new HashSet<>();
        for (Issue issue : newIssues) {
            currentIssueIds.add(issue.id);
        }
        changedIds.retainAll(currentIssueIds);

        // Save the new issue reasons
        saveIssueReasons(newIssues);
        saveChangedIds(changedIds);
    }
    
    /**
     * Check if an issue has content changes (green dot should be shown).
     */
    public boolean hasIssueChanged(String issueId) {
        Set<String> changedIds = getChangedIds();
        return changedIds.contains(issueId);
    }
    
    /**
     * Clear the changed indicator for an issue (when user views the issue detail).
     */
    public void clearIssueChanged(String issueId) {
        Set<String> changedIds = getChangedIds();
        if (changedIds.remove(issueId)) {
            saveChangedIds(changedIds);
        }
    }
    
    /**
     * Get all currently changed issue IDs.
     */
    public Set<String> getChangedIds() {
        Set<String> originalSet = prefs.getStringSet(KEY_CHANGED_IDS, new HashSet<>());
        // Create a defensive copy to avoid SharedPreferences StringSet modification issues
        return new HashSet<>(originalSet);
    }
    
    
    /**
     * Save issue reasons for comparison.
     */
    private void saveIssueReasons(List<Issue> issues) {
        StringBuilder sb = new StringBuilder();
        for (Issue issue : issues) {
            sb.append(issue.id).append(":").append(issue.reason).append("|");
        }
        String reasonsString = sb.toString();
        prefs.edit().putString(KEY_ISSUE_REASONS, reasonsString).apply();
    }
    
    /**
     * Parse saved issue reasons into a map.
     */
    private Map<String, String> parseIssueReasons(String savedReasons) {
        Map<String, String> reasons = new HashMap<>();
        if (savedReasons.isEmpty()) {
            return reasons;
        }

        String[] entries = savedReasons.split("\\|");

        for (String entry : entries) {
            if (entry.contains(":")) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    reasons.put(parts[0], parts[1]);
                }
            }
        }
        return reasons;
    }
    
    /**
     * Save the changed issue IDs.
     */
    private void saveChangedIds(Set<String> changedIds) {
        // Create a new HashSet to avoid SharedPreferences.putStringSet() issues
        Set<String> newSet = new HashSet<>(changedIds);
        prefs.edit().putStringSet(KEY_CHANGED_IDS, newSet).apply();
    }
    
}
