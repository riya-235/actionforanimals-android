package org.a5calls.android.a5calls.model;

import android.content.Context;
import com.onesignal.OneSignal;
import java.util.ArrayList;
import java.util.List;

/**
 * Achievement registry matching iOS implementation
 * No persistence - calculates achievements dynamically based on current data
 */
public class AchievementRegistry {

    public interface AchievementChecker {
        boolean checkUnlocked(DatabaseHelper db, Context context);
    }

    public interface NewlyUnlockedChecker {
        boolean checkNewlyUnlocked(DatabaseHelper db, Context context, String actionType, int animalsFromThisAction);
    }

    public static class AchievementDefinition {
        public final Achievement.Type type;
        public final AchievementChecker checkUnlocked;
        public final NewlyUnlockedChecker checkNewlyUnlocked;

        public AchievementDefinition(Achievement.Type type, AchievementChecker checkUnlocked, NewlyUnlockedChecker checkNewlyUnlocked) {
            this.type = type;
            this.checkUnlocked = checkUnlocked;
            this.checkNewlyUnlocked = checkNewlyUnlocked;
        }
    }

    private static final List<AchievementDefinition> ALL_ACHIEVEMENTS = new ArrayList<AchievementDefinition>() {{
        // Action Achievements
        add(new AchievementDefinition(
            Achievement.Type.FIRST_CALL,
            (db, context) -> db.getActionCount(DatabaseHelper.ActionTypes.CALL) >= Achievement.Thresholds.FIRST_CALL,
            (db, context, actionType, animalsFromThisAction) ->
                "call".equals(actionType) && db.getActionCount(DatabaseHelper.ActionTypes.CALL) == Achievement.Thresholds.FIRST_CALL
        ));

        add(new AchievementDefinition(
            Achievement.Type.CALL_CHAMPION,
            (db, context) -> db.getActionCount(DatabaseHelper.ActionTypes.CALL) >= Achievement.Thresholds.CALL_CHAMPION,
            (db, context, actionType, animalsFromThisAction) ->
                "call".equals(actionType) && db.getActionCount(DatabaseHelper.ActionTypes.CALL) == Achievement.Thresholds.CALL_CHAMPION
        ));

        add(new AchievementDefinition(
            Achievement.Type.EMAIL_ADVOCATE,
            (db, context) -> db.getActionCount(DatabaseHelper.ActionTypes.EMAIL) >= Achievement.Thresholds.FIRST_EMAIL,
            (db, context, actionType, animalsFromThisAction) ->
                "email".equals(actionType) && db.getActionCount(DatabaseHelper.ActionTypes.EMAIL) == Achievement.Thresholds.FIRST_EMAIL
        ));

        // Animal Achievements
        add(new AchievementDefinition(
            Achievement.Type.FARM_FRIEND,
            (db, context) -> db.getCategoryActionCount("Farmed") >= Achievement.Thresholds.ANIMAL_CATEGORY_MAJOR,
            (db, context, actionType, animalsFromThisAction) ->
                db.getCategoryActionCount("Farmed") == Achievement.Thresholds.ANIMAL_CATEGORY_MAJOR
        ));

        add(new AchievementDefinition(
            Achievement.Type.WILDLIFE_WARRIOR,
            (db, context) -> db.getCategoryActionCount("Wildlife") >= Achievement.Thresholds.ANIMAL_CATEGORY_MAJOR,
            (db, context, actionType, animalsFromThisAction) ->
                db.getCategoryActionCount("Wildlife") == Achievement.Thresholds.ANIMAL_CATEGORY_MAJOR
        ));

        add(new AchievementDefinition(
            Achievement.Type.FREEDOM_FIGHTER,
            (db, context) -> db.getCategoryActionCount("Entertainment") >= Achievement.Thresholds.ANIMAL_CATEGORY_MINOR,
            (db, context, actionType, animalsFromThisAction) ->
                db.getCategoryActionCount("Entertainment") == Achievement.Thresholds.ANIMAL_CATEGORY_MINOR
        ));

        add(new AchievementDefinition(
            Achievement.Type.RESCUE_ALLY,
            (db, context) -> db.getCategoryActionCount("Companion") >= Achievement.Thresholds.ANIMAL_CATEGORY_MINOR,
            (db, context, actionType, animalsFromThisAction) ->
                db.getCategoryActionCount("Companion") == Achievement.Thresholds.ANIMAL_CATEGORY_MINOR
        ));

        // Milestone Achievements
        add(new AchievementDefinition(
            Achievement.Type.GOAL_CRUSHER,
            (db, context) -> (db.getActionCount(DatabaseHelper.ActionTypes.CALL) + db.getActionCount(DatabaseHelper.ActionTypes.EMAIL)) >= Achievement.Thresholds.GOAL_CRUSHER,
            (db, context, actionType, animalsFromThisAction) ->
                (db.getActionCount(DatabaseHelper.ActionTypes.CALL) + db.getActionCount(DatabaseHelper.ActionTypes.EMAIL)) == Achievement.Thresholds.GOAL_CRUSHER
        ));

        add(new AchievementDefinition(
            Achievement.Type.CENTURY_ADVOCATE,
            (db, context) -> db.getTotalAnimalsHelped() >= Achievement.Thresholds.CENTURY_ADVOCATE,
            (db, context, actionType, animalsFromThisAction) ->
                db.getTotalAnimalsHelped() >= Achievement.Thresholds.CENTURY_ADVOCATE &&
                db.getTotalAnimalsHelped() - animalsFromThisAction < Achievement.Thresholds.CENTURY_ADVOCATE
        ));

        add(new AchievementDefinition(
            Achievement.Type.PEACE_FOR_ALL_BEINGS,
            (db, context) -> db.getTotalAnimalsHelped() >= Achievement.Thresholds.PEACE_MILESTONE,
            (db, context, actionType, animalsFromThisAction) ->
                db.getTotalAnimalsHelped() >= Achievement.Thresholds.PEACE_MILESTONE &&
                db.getTotalAnimalsHelped() - animalsFromThisAction < Achievement.Thresholds.PEACE_MILESTONE
        ));

        add(new AchievementDefinition(
            Achievement.Type.HOT_STREAK,
            (db, context) -> AchievementManager.getInstance(context).getWeeklyStreak() >= Achievement.Thresholds.HOT_STREAK_WEEKS,
            (db, context, actionType, animalsFromThisAction) ->
                AchievementManager.getInstance(context).getWeeklyStreak() == Achievement.Thresholds.HOT_STREAK_WEEKS
        ));

        // Settings Achievements
        add(new AchievementDefinition(
            Achievement.Type.STAY_INFORMED,
            (db, context) -> {
                try {
                    // Check both system permission AND OneSignal opt-in status
                    boolean permission = com.onesignal.OneSignal.getNotifications().getPermission();
                    boolean optedIn = com.onesignal.OneSignal.getUser().getPushSubscription().getOptedIn();

                    // Achievement requires BOTH system permission AND app opt-in
                    return permission && optedIn;
                } catch (Exception e) {
                    // Fallback to system notification check if OneSignal fails
                    try {
                        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled();
                    } catch (Exception e2) {
                        return false;
                    }
                }
            },
            (db, context, actionType, animalsFromThisAction) -> false // Not triggered by actions
        ));

        add(new AchievementDefinition(
            Achievement.Type.REMIND_ME_LATER,
            (db, context) -> AccountManager.Instance.getAllowReminders(context),
            (db, context, actionType, animalsFromThisAction) -> false // Not triggered by actions
        ));
    }};

    public static Achievement createAchievement(Achievement.Type type, DatabaseHelper db, Context context) {
        AchievementDefinition definition = getDefinition(type);
        boolean isUnlocked = definition != null ? definition.checkUnlocked.checkUnlocked(db, context) : false;
        return new Achievement(type, isUnlocked, 0); // No unlock date needed
    }

    public static List<Achievement> getAchievementsByCategory(Achievement.Category category, DatabaseHelper db, Context context) {
        List<Achievement> achievements = new ArrayList<>();
        for (AchievementDefinition definition : ALL_ACHIEVEMENTS) {
            if (definition.type.getCategory() == category) {
                achievements.add(createAchievement(definition.type, db, context));
            }
        }
        return achievements;
    }

    public static List<Achievement> getAllAchievements(DatabaseHelper db, Context context) {
        List<Achievement> achievements = new ArrayList<>();
        for (AchievementDefinition definition : ALL_ACHIEVEMENTS) {
            achievements.add(createAchievement(definition.type, db, context));
        }
        return achievements;
    }

    public static AchievementDefinition getDefinition(Achievement.Type type) {
        for (AchievementDefinition definition : ALL_ACHIEVEMENTS) {
            if (definition.type == type) {
                return definition;
            }
        }
        return null;
    }

    public static List<Achievement.Type> checkNewlyUnlockedAchievements(DatabaseHelper db, Context context, String actionType, int animalsFromThisAction) {
        List<Achievement.Type> newlyUnlocked = new ArrayList<>();
        for (AchievementDefinition definition : ALL_ACHIEVEMENTS) {
            if (definition.checkNewlyUnlocked.checkNewlyUnlocked(db, context, actionType, animalsFromThisAction)) {
                newlyUnlocked.add(definition.type);
            }
        }
        return newlyUnlocked;
    }
}