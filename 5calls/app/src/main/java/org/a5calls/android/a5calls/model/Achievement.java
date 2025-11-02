package org.a5calls.android.a5calls.model;

/**
 * Achievement system matching iOS implementation
 * Based on AchievementRegistry.swift
 */
public class Achievement {

    public enum Type {
        // Action Achievements
        FIRST_CALL("first_call", "First Call", "Completed your first phone call.", "First phone call", "badge_first_call", Category.ACTION),
        CALL_CHAMPION("call_champion", "Call Champion", "Completed 10 phone calls.", "10 phone calls", "badge_call_champion", Category.ACTION),
        EMAIL_ADVOCATE("email_advocate", "Email Advocate", "Completed your first email action.", "First email action", "badge_email_advocate", Category.ACTION),

        // Animal Category Achievements (in iOS order: Farm → Wildlife → Entertainment → Companion)
        FARM_FRIEND("farm_friend", "Farm Friend", "5 actions for farmed animals.", "5 farmed animal actions", "badge_farmed", Category.ANIMAL),
        WILDLIFE_WARRIOR("wildlife_warrior", "Wildlife Warrior", "5 actions for wildlife.", "5 wildlife actions", "badge_wildlife", Category.ANIMAL),
        FREEDOM_FIGHTER("freedom_fighter", "Freedom Fighter", "3 actions for entertainment.", "3 entertainment actions", "badge_entertainment", Category.ANIMAL),
        RESCUE_ALLY("rescue_ally", "Rescue Ally", "3 companion-animal actions.", "3 companion actions", "badge_companion", Category.ANIMAL),

        // Milestone Achievements
        GOAL_CRUSHER("goal_crusher", "Goal Crusher", "Completed 10 total actions.", "10 total actions", "badge_goal_crusher", Category.MILESTONE),
        CENTURY_ADVOCATE("century_advocate", "Century Advocate", "Helped 100 animals.", "100 animals helped", "badge_century_advocate", Category.MILESTONE),
        PEACE_FOR_ALL_BEINGS("peace_for_all_beings", "Peace for All Beings", "Lifetime milestone — 500 animals helped.", "500 animals helped", "badge_peace", Category.MILESTONE),
        HOT_STREAK("hot_streak", "Hot Streak", "Maintained a 4-week streak.", "4-week streak", "badge_hot_streak", Category.MILESTONE),

        // Settings Achievements
        STAY_INFORMED("stay_informed", "Stay Informed", "Signed up for notifications.", "Notifications enabled", "badge_notification", Category.SETTINGS),
        REMIND_ME_LATER("remind_me_later", "Remind Me Later", "Enabled reminders for actions.", "Reminders enabled", "badge_reminders", Category.SETTINGS);

        private final String id;
        private final String title;
        private final String subtitle;
        private final String compactSubtitle;
        private final String icon;
        private final Category category;

        Type(String id, String title, String subtitle, String compactSubtitle, String icon, Category category) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.compactSubtitle = compactSubtitle;
            this.icon = icon;
            this.category = category;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public String getCompactSubtitle() { return compactSubtitle; }
        public String getIcon() { return icon; }
        public Category getCategory() { return category; }
    }

    public enum Category {
        ACTION, ANIMAL, MILESTONE, SETTINGS
    }

    // Achievement thresholds (matching iOS AchievementThresholds)
    public static class Thresholds {
        public static final int FIRST_CALL = 1;
        public static final int CALL_CHAMPION = 10;
        public static final int FIRST_EMAIL = 1;
        public static final int GOAL_CRUSHER = 10;
        public static final int CENTURY_ADVOCATE = 100;
        public static final int PEACE_MILESTONE = 500;
        public static final int ANIMAL_CATEGORY_MAJOR = 5;  // Farm, Wildlife
        public static final int ANIMAL_CATEGORY_MINOR = 3;  // Entertainment, Companion
        public static final int HOT_STREAK_WEEKS = 4;
    }

    private final Type type;
    private final boolean isUnlocked;
    private final long unlockedDate;

    public Achievement(Type type, boolean isUnlocked, long unlockedDate) {
        this.type = type;
        this.isUnlocked = isUnlocked;
        this.unlockedDate = unlockedDate;
    }

    // Getters
    public Type getType() { return type; }
    public boolean isUnlocked() { return isUnlocked; }
    public long getUnlockedDate() { return unlockedDate; }
    public String getId() { return type.getId(); }
    public String getTitle() { return type.getTitle(); }
    public String getSubtitle() { return type.getSubtitle(); }
    public String getCompactSubtitle() { return type.getCompactSubtitle(); }
    public String getIcon() { return type.getIcon(); }
    public Category getCategory() { return type.getCategory(); }
}