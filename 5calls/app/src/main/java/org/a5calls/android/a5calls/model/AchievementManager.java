package org.a5calls.android.a5calls.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.AppSingleton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AchievementManager {
    private static AchievementManager instance;
    private Context context;
    private DatabaseHelper db;
    private PendingAchievement pendingCelebration = null;

    // Weekly streak tracking (moved from DatabaseHelper)
    private static final String PREFS_NAME = "weekly_streak_prefs";
    private static final String KEY_WEEKLY_STREAK = "weekly_streak_count";
    private static final String KEY_LAST_ACTION_WEEK = "last_action_week";

    private AchievementManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppSingleton.getInstance(this.context).getDatabaseHelper();
    }

    public static synchronized AchievementManager getInstance(Context context) {
        if (instance == null) {
            instance = new AchievementManager(context);
        }
        return instance;
    }

    // Compatibility method for activities that don't have context
    public static AchievementManager getInstance() {
        return instance; // Will be null if never initialized with context
    }

    public void checkAfterAction(String actionType, int animalsFromThisAction, String categoriesString) {
        // Update weekly streak and check if it changed
        boolean streakChanged = updateWeeklyStreak();

        // Check which categories this action affects
        boolean farmedChanged = categoriesString != null && categoriesString.contains("Farmed");
        boolean wildlifeChanged = categoriesString != null && categoriesString.contains("Wildlife");
        boolean entertainmentChanged = categoriesString != null && categoriesString.contains("Entertainment");
        boolean companionChanged = categoriesString != null && categoriesString.contains("Companion");

        // Check all achievements
        List<Achievement.Type> newlyUnlocked = AchievementRegistry.checkNewlyUnlockedAchievements(
            db, context, actionType, animalsFromThisAction);

        // Filter achievements that didn't actually change
        newlyUnlocked.removeIf(type -> {
            switch (type) {
                case HOT_STREAK: return !streakChanged;
                case FARM_FRIEND: return !farmedChanged;
                case WILDLIFE_WARRIOR: return !wildlifeChanged;
                case FREEDOM_FIGHTER: return !entertainmentChanged;
                case RESCUE_ALLY: return !companionChanged;
                default: return false; // Don't filter other achievements
            }
        });

        for (Achievement.Type type : newlyUnlocked) {
            showAchievementCelebration(type);
        }
    }

    public void checkSettingsAchievements() {
        // Check settings achievements without celebration
        Achievement.Type[] settingsTypes = {
            Achievement.Type.STAY_INFORMED,
            Achievement.Type.REMIND_ME_LATER
        };

        for (Achievement.Type type : settingsTypes) {
            // Just checking if unlocked, no celebration for settings changes
            AchievementRegistry.createAchievement(type, db, context);
        }
    }

    private void showAchievementCelebration(Achievement.Type achievementType) {
        Achievement achievement = AchievementRegistry.createAchievement(achievementType, db, context);

        // Store achievement for later celebration (like iOS ImpactManager)
        // Sound and UI are handled by AchievementCelebrationView when shown
        setPendingAchievement(achievement.getTitle(), achievement.getSubtitle(), achievement.getIcon());
    }

    // === PENDING ACHIEVEMENT METHODS (for iOS-style delayed celebrations) ===

    public void setPendingAchievement(String title, String subtitle, String icon) {
        if (pendingCelebration == null) {
            pendingCelebration = new PendingAchievement(title, subtitle, icon);
        }
    }

    public PendingAchievement getPendingAchievement() {
        PendingAchievement result = pendingCelebration;
        pendingCelebration = null; // Clear after getting
        return result;
    }

    public boolean hasPendingAchievement() {
        return pendingCelebration != null;
    }

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

    // === WEEKLY STREAK METHODS (moved from DatabaseHelper) ===

    /**
     * Gets the current weekly streak count
     */
    public int getWeeklyStreak() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_WEEKLY_STREAK, 0);
    }

    /**
     * Updates weekly streak when user takes an action
     * @return true if the streak value actually changed, false if same week
     */
    private boolean updateWeeklyStreak() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String currentWeek = getCurrentWeekString();
        String lastActionWeek = prefs.getString(KEY_LAST_ACTION_WEEK, null);
        int currentStreak = prefs.getInt(KEY_WEEKLY_STREAK, 0);

        if (lastActionWeek == null) {
            // First action ever
            currentStreak = 1;
        } else if (currentWeek.equals(lastActionWeek)) {
            // Same week, no change to streak
            return false;
        } else {
            // Check if it's consecutive weeks
            if (isConsecutiveWeek(lastActionWeek, currentWeek)) {
                currentStreak++;
            } else {
                // Gap in weeks, reset streak
                currentStreak = 1;
            }
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_WEEKLY_STREAK, currentStreak);
        editor.putString(KEY_LAST_ACTION_WEEK, currentWeek);
        editor.apply();
        return true; // Streak changed
    }

    private String getCurrentWeekString() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        int year = cal.get(Calendar.YEAR);
        int week = cal.get(Calendar.WEEK_OF_YEAR);
        return year + "-" + week;
    }

    private boolean isConsecutiveWeek(String lastWeek, String currentWeek) {
        try {
            String[] lastParts = lastWeek.split("-");
            String[] currentParts = currentWeek.split("-");

            int lastYear = Integer.parseInt(lastParts[0]);
            int lastWeekNum = Integer.parseInt(lastParts[1]);
            int currentYear = Integer.parseInt(currentParts[0]);
            int currentWeekNum = Integer.parseInt(currentParts[1]);

            if (currentYear == lastYear) {
                return currentWeekNum == lastWeekNum + 1;
            } else if (currentYear == lastYear + 1) {
                // Check if it's week 1 of new year following last week of previous year
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.YEAR, lastYear);
                int weeksInLastYear = cal.getActualMaximum(Calendar.WEEK_OF_YEAR);
                return lastWeekNum == weeksInLastYear && currentWeekNum == 1;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

}