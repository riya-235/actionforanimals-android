package org.a5calls.android.a5calls.manager;

import android.util.Log;

/**
 * Achievement Manager - singleton for managing pending achievement celebrations
 * Based on iOS ImpactManager pattern - stores achievements until they can be displayed
 */
public class AchievementManager {
    private static final String TAG = "AchievementManager";
    private static AchievementManager instance;

    private PendingAchievement pendingCelebration = null;

    private AchievementManager() {
        // Private constructor for singleton
    }

    public static synchronized AchievementManager getInstance() {
        if (instance == null) {
            instance = new AchievementManager();
        }
        return instance;
    }

    /**
     * Set a pending achievement for celebration (like iOS showAchievementCelebration)
     * Only stores if no achievement is currently pending (matches iOS taking .first)
     */
    public void setPendingAchievement(String title, String subtitle, String icon) {
        if (pendingCelebration == null) {
            pendingCelebration = new PendingAchievement(title, subtitle, icon);
            Log.d(TAG, "Set pending achievement: " + title);
        } else {
            Log.d(TAG, "Achievement already pending, ignoring: " + title);
        }
    }

    /**
     * Get and clear the pending achievement (consume once)
     */
    public PendingAchievement getPendingAchievement() {
        PendingAchievement result = pendingCelebration;
        pendingCelebration = null; // Clear after getting
        if (result != null) {
            Log.d(TAG, "Retrieved pending achievement: " + result.title);
        }
        return result;
    }

    /**
     * Check if there's a pending achievement
     */
    public boolean hasPendingAchievement() {
        return pendingCelebration != null;
    }

    /**
     * Simple data class for pending achievements
     */
    public static class PendingAchievement {
        public final String title;
        public final String subtitle;
        public final String icon;

        public PendingAchievement(String title, String subtitle, String icon) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
        }
    }
}