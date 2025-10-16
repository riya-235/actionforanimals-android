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
        Log.d(TAG, "=== updateChangedIssues START ===");
        Log.d(TAG, "Incoming issues count: " + newIssues.size());
        
        // Start with existing changed IDs (preserves user-viewed state)
        Set<String> changedIds = getChangedIds();
        Log.d(TAG, "Existing changed IDs: " + changedIds);

        // Get previously saved issue reasons
        String savedReasons = prefs.getString(KEY_ISSUE_REASONS, "");
        Log.d(TAG, "Saved reasons string length: " + savedReasons.length());
        Log.d(TAG, "Saved reasons preview: " + (savedReasons.length() > 100 ? savedReasons.substring(0, 100) + "..." : savedReasons));

        // If this is the first run (no previous data), don't mark anything as changed
        if (savedReasons.isEmpty()) {
            Log.d(TAG, "First run - no previous issues to compare");
            saveIssueReasons(newIssues);
            Log.d(TAG, "Saved " + newIssues.size() + " issue reasons for first run");
            // Keep existing changedIds as-is (don't overwrite)
            return;
        }

        // Parse saved reasons into a map of issueId -> reason
        Map<String, String> oldReasons = parseIssueReasons(savedReasons);
        Log.d(TAG, "Parsed old reasons count: " + oldReasons.size());

        // Compare each new issue with saved reasons
        int newIssueCount = 0;
        int changedIssueCount = 0;
        int unchangedIssueCount = 0;
        
        for (Issue newIssue : newIssues) {
            String oldReason = oldReasons.get(newIssue.id);
            Log.d(TAG, "Processing issue " + newIssue.id + " - has old reason: " + (oldReason != null));

            if (oldReason == null) {
                // New issue - mark as changed
                changedIds.add(newIssue.id);
                newIssueCount++;
                Log.d(TAG, "New issue detected: " + newIssue.id);
            } else if (!oldReason.equals(newIssue.reason)) {
                // Content changed - mark as changed (even if previously cleared)
                changedIds.add(newIssue.id);
                changedIssueCount++;
                Log.d(TAG, "Content changed for issue: " + newIssue.id);
                Log.d(TAG, "Old reason length: " + oldReason.length() + ", new reason length: " + newIssue.reason.length());
            } else {
                unchangedIssueCount++;
                Log.d(TAG, "No change for issue: " + newIssue.id);
            }
            // If content is the same, preserve existing state (don't add or remove)
        }

        Log.d(TAG, "Summary - New: " + newIssueCount + ", Changed: " + changedIssueCount + ", Unchanged: " + unchangedIssueCount);

        // Remove any changed IDs for issues that no longer exist
        Set<String> currentIssueIds = new HashSet<>();
        for (Issue issue : newIssues) {
            currentIssueIds.add(issue.id);
        }
        int beforeRetain = changedIds.size();
        changedIds.retainAll(currentIssueIds);
        int afterRetain = changedIds.size();
        Log.d(TAG, "Retained changed IDs: " + beforeRetain + " -> " + afterRetain);

        // Save the new issue reasons
        saveIssueReasons(newIssues);
        saveChangedIds(changedIds);

        Log.d(TAG, "Final changed IDs: " + changedIds);
        Log.d(TAG, "Updated changed issues: " + changedIds.size() + " issues changed");
        Log.d(TAG, "=== updateChangedIssues END ===");
    }
    
    /**
     * Check if an issue has content changes (green dot should be shown).
     */
    public boolean hasIssueChanged(String issueId) {
        Set<String> changedIds = getChangedIds();
        boolean hasChanged = changedIds.contains(issueId);
        Log.d(TAG, "Checking issue " + issueId + " - hasChanged: " + hasChanged + " (changed IDs: " + changedIds + ")");
        return hasChanged;
    }
    
    /**
     * Clear the changed indicator for an issue (when user views the issue detail).
     */
    public void clearIssueChanged(String issueId) {
        Set<String> changedIds = getChangedIds();
        Log.d(TAG, "Before clearing - changed IDs: " + changedIds);
        if (changedIds.remove(issueId)) {
            saveChangedIds(changedIds);
            Log.d(TAG, "Cleared change indicator for issue: " + issueId);
            Log.d(TAG, "After clearing - changed IDs: " + changedIds);
        } else {
            Log.d(TAG, "Issue " + issueId + " was not in changed list");
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
        Log.d(TAG, "Saving issue reasons - length: " + reasonsString.length() + ", issues: " + issues.size());
        Log.d(TAG, "Reasons preview: " + (reasonsString.length() > 200 ? reasonsString.substring(0, 200) + "..." : reasonsString));
        prefs.edit().putString(KEY_ISSUE_REASONS, reasonsString).apply();
    }
    
    /**
     * Parse saved issue reasons into a map.
     */
    private Map<String, String> parseIssueReasons(String savedReasons) {
        Map<String, String> reasons = new HashMap<>();
        if (savedReasons.isEmpty()) {
            Log.d(TAG, "parseIssueReasons: empty savedReasons");
            return reasons;
        }
        
        Log.d(TAG, "parseIssueReasons: parsing " + savedReasons.length() + " chars");
        String[] entries = savedReasons.split("\\|");
        Log.d(TAG, "parseIssueReasons: split into " + entries.length + " entries");
        
        for (String entry : entries) {
            if (entry.contains(":")) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    reasons.put(parts[0], parts[1]);
                    Log.d(TAG, "parseIssueReasons: added issue " + parts[0] + " with reason length " + parts[1].length());
                }
            }
        }
        Log.d(TAG, "parseIssueReasons: final map size " + reasons.size());
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
