package org.a5calls.android.a5calls.controller;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.FiveCallsApplication;
import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.model.Achievement;
import org.a5calls.android.a5calls.model.DatabaseHelper;

public class YourImpactDialogFragment extends DialogFragment {
    private static final String TAG = "YourImpactDialog";

    public static YourImpactDialogFragment newInstance() {
        return new YourImpactDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        // Remove title bar
        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
        }

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                // Make dialog full width but not full height - like iOS bottom sheet
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.gravity = android.view.Gravity.BOTTOM;
                window.setAttributes(params);

                // Add slide up animation
                window.setWindowAnimations(R.style.DialogSlideAnimation);

                // Set transparent background for rounded corners
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_your_impact, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupContent(view);
        setupWeeklyStreak(view);
        setupAchievements(view);
        setupDoneButton(view);

        // Track analytics
        FiveCallsApplication.analyticsManager().trackPageview("/impact/", getActivity());
    }

    private void setupContent(View view) {
        DatabaseHelper db = AppSingleton.getInstance(getContext()).getDatabaseHelper();
        int totalAnimalsHelped = db.getTotalAnimalsHelped();

        TextView animalsHelpedText = view.findViewById(R.id.animals_helped_text);
        TextView impactMessageText = view.findViewById(R.id.impact_message_text);

        if (animalsHelpedText != null) {
            animalsHelpedText.setText(String.valueOf(totalAnimalsHelped));
        }

        if (impactMessageText != null) {
            impactMessageText.setText(getImpactMessage(totalAnimalsHelped));
        }
    }

    private void setupDoneButton(View view) {
        View doneButton = view.findViewById(R.id.done_button);
        Log.d(TAG, "Done button found: " + (doneButton != null));
        if (doneButton != null) {
            doneButton.setOnClickListener(v -> {
                Log.d(TAG, "Done button clicked!");
                dismiss();
            });
        }
    }

    private String getImpactMessage(int count) {
        if (count == 0) {
            return "Ready to make your first impact?";
        } else if (count <= 10) {
            return "Every action makes a difference";
        } else if (count <= 100) {
            return "You've helped an entire sanctuary";
        } else if (count <= 500) {
            return "You're changing the world for animals";
        } else {
            return "You're an animal advocacy champion";
        }
    }

    private void setupWeeklyStreak(View view) {
        DatabaseHelper db = AppSingleton.getInstance(getContext()).getDatabaseHelper();

        // Get real streak count from persistent storage
        boolean hasActionThisWeek = db.hasActionThisWeek();
        int weeklyStreakCount = db.getWeeklyStreak();

        Set<Integer> actionDays = db.getActionDaysThisWeek();
        int currentDay = db.getCurrentDayOfWeek();

        // Update streak count
        TextView streakCountText = view.findViewById(R.id.weekly_streak_count);
        if (streakCountText != null) {
            streakCountText.setText(String.valueOf(weeklyStreakCount));
        }

        // Show/hide action completed status
        LinearLayout actionStatusLayout = view.findViewById(R.id.action_status_layout);
        if (actionStatusLayout != null) {
            actionStatusLayout.setVisibility(hasActionThisWeek ? View.VISIBLE : View.GONE);
        }

        // Setup day views
        int[] dayIds = {
            R.id.day_monday,    // 0
            R.id.day_tuesday,   // 1
            R.id.day_wednesday, // 2
            R.id.day_thursday,  // 3
            R.id.day_friday,    // 4
            R.id.day_saturday,  // 5
            R.id.day_sunday     // 6
        };

        for (int i = 0; i < dayIds.length; i++) {
            TextView dayView = view.findViewById(dayIds[i]);
            if (dayView != null) {
                boolean hasAction = actionDays.contains(i);
                boolean isToday = (i == currentDay);

                // Set background and text color based on state
                if (hasAction && isToday) {
                    dayView.setBackgroundResource(R.drawable.week_day_active_today);
                    dayView.setTextColor(getResources().getColor(android.R.color.white));
                } else if (hasAction) {
                    dayView.setBackgroundResource(R.drawable.week_day_active);
                    dayView.setTextColor(getResources().getColor(android.R.color.white));
                } else if (isToday) {
                    dayView.setBackgroundResource(R.drawable.week_day_today);
                    dayView.setTextColor(getResources().getColor(R.color.text_primary_modern));
                } else {
                    dayView.setBackgroundResource(R.drawable.week_day_inactive);
                    dayView.setTextColor(getResources().getColor(R.color.text_primary_modern));
                }
            }
        }
    }

    private void setupAchievements(View view) {
        DatabaseHelper db = AppSingleton.getInstance(getContext()).getDatabaseHelper();

        // Setup Action Achievements (includes settings achievements like iOS)
        setupAchievementSection(view, R.id.action_achievements_container, R.id.action_achievements_count,
            getActionAndSettingsAchievements(db));

        // Setup Animal Achievements
        setupAchievementSection(view, R.id.animal_achievements_container, R.id.animal_achievements_count,
            db.getAchievementsByCategory(Achievement.Category.ANIMAL));

        // Setup Milestone Achievements
        setupAchievementSection(view, R.id.milestone_achievements_container, R.id.milestone_achievements_count,
            db.getAchievementsByCategory(Achievement.Category.MILESTONE));
    }

    private List<Achievement> getActionAndSettingsAchievements(DatabaseHelper db) {
        List<Achievement> actionAchievements = db.getAchievementsByCategory(Achievement.Category.ACTION);
        List<Achievement> settingsAchievements = db.getAchievementsByCategory(Achievement.Category.SETTINGS);

        // Combine like iOS (action + settings)
        actionAchievements.addAll(settingsAchievements);
        return actionAchievements;
    }

    private void setupAchievementSection(View parentView, int containerId, int countId, List<Achievement> achievements) {
        LinearLayout container = parentView.findViewById(containerId);
        TextView countText = parentView.findViewById(countId);

        if (container == null || countText == null) return;

        // Clear existing views
        container.removeAllViews();

        // Count unlocked achievements
        int unlockedCount = 0;
        for (Achievement achievement : achievements) {
            if (achievement.isUnlocked()) {
                unlockedCount++;
            }
        }

        // Update count text (like iOS "X of Y")
        countText.setText(String.format("%d of %d", unlockedCount, achievements.size()));

        // Sort achievements: unlocked first, then locked (maintaining relative order within each group)
        List<Achievement> sortedAchievements = new ArrayList<>();

        // Add unlocked achievements first (maintaining original order)
        for (Achievement achievement : achievements) {
            if (achievement.isUnlocked()) {
                sortedAchievements.add(achievement);
            }
        }

        // Add locked achievements after (maintaining original order)
        for (Achievement achievement : achievements) {
            if (!achievement.isUnlocked()) {
                sortedAchievements.add(achievement);
            }
        }

        // Add achievement cards in sorted order
        LayoutInflater inflater = getLayoutInflater();
        for (Achievement achievement : sortedAchievements) {
            View cardView = inflater.inflate(R.layout.achievement_card, container, false);
            setupAchievementCard(cardView, achievement);
            container.addView(cardView);
        }
    }

    private void setupAchievementCard(View cardView, Achievement achievement) {
        ImageView iconView = cardView.findViewById(R.id.achievement_icon);
        ImageView lockView = cardView.findViewById(R.id.achievement_lock);
        TextView titleView = cardView.findViewById(R.id.achievement_title);
        TextView subtitleView = cardView.findViewById(R.id.achievement_subtitle);

        // Set badge icon
        String iconName = achievement.getIcon(); // Already using underscores: badge_first_call
        int iconResId = getResources().getIdentifier(iconName, "drawable", getContext().getPackageName());
        if (iconResId != 0) {
            iconView.setImageResource(iconResId);
        }

        // Set texts
        titleView.setText(achievement.getTitle());
        subtitleView.setText(achievement.getCompactSubtitle());

        // Set visual state (like iOS)
        if (achievement.isUnlocked()) {
            // Unlocked: full opacity, green border, no lock
            cardView.setSelected(true);
            cardView.setAlpha(1.0f);
            lockView.setVisibility(View.GONE);
            titleView.setTextColor(getResources().getColor(R.color.text_primary_modern));
        } else {
            // Locked: 60% opacity, gray border, show lock
            cardView.setSelected(false);
            cardView.setAlpha(0.6f);
            lockView.setVisibility(View.VISIBLE);
            titleView.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }
}