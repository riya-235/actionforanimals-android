package org.a5calls.android.a5calls.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.a5calls.android.a5calls.R;

/**
 * Achievement Celebration Dialog - shows when user unlocks an achievement
 * Matches the iOS achievement celebration functionality
 */
public class AchievementCelebrationDialog extends DialogFragment {
    private static final String ARG_TITLE = "achievement_title";
    private static final String ARG_SUBTITLE = "achievement_subtitle";
    private static final String ARG_ICON = "achievement_icon";

    private String achievementTitle;
    private String achievementSubtitle;
    private String achievementIcon;
    private android.media.MediaPlayer achievementMediaPlayer;

    public static AchievementCelebrationDialog newInstance(String title, String subtitle, String icon) {
        AchievementCelebrationDialog dialog = new AchievementCelebrationDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        args.putString(ARG_ICON, icon);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            achievementTitle = getArguments().getString(ARG_TITLE, "Achievement Unlocked!");
            achievementSubtitle = getArguments().getString(ARG_SUBTITLE, "You've reached a milestone");
            achievementIcon = getArguments().getString(ARG_ICON, "🏆");
        }

        // Set dialog style early (before adding content)
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_achievement_celebration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Make dialog transparent
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Set up achievement content
        ImageView iconView = view.findViewById(R.id.achievement_icon);
        TextView titleView = view.findViewById(R.id.achievement_title);
        TextView subtitleView = view.findViewById(R.id.achievement_subtitle);

        // Set the achievement icon by drawable name
        int iconResId = getResources().getIdentifier(achievementIcon, "drawable", getContext().getPackageName());
        if (iconResId != 0) {
            iconView.setImageResource(iconResId);
        } else {
            // Fallback to default achievement icon
            iconView.setImageResource(R.drawable.badge_first_call);
        }

        titleView.setText(achievementTitle);
        subtitleView.setText(achievementSubtitle);

        // Play achievement celebration sound (different from regular counter sound)
        playAchievementSound();

        // Animate the celebration
        animateCelebration(view);

        // Dismiss on tap (stays until user taps)
        view.setOnClickListener(v -> dismiss());
    }

    private void animateCelebration(View view) {
        // Find the card container (LinearLayout) for more precise animation
        View cardContainer = view.findViewById(android.R.id.content) != null ?
            ((ViewGroup) view).getChildAt(0) : view;

        // iOS-like spring animation with bounce effect
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(cardContainer, "scaleX", 0.5f, 1.1f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(cardContainer, "scaleY", 0.5f, 1.1f, 1.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(cardContainer, "alpha", 0.0f, 1.0f);

        // Background fade in
        ObjectAnimator backgroundAlpha = ObjectAnimator.ofFloat(view, "alpha", 0.0f, 1.0f);

        scaleX.setDuration(600);
        scaleY.setDuration(600);
        alpha.setDuration(400);
        backgroundAlpha.setDuration(300);

        // Use spring-like interpolator for iOS feel
        android.view.animation.OvershootInterpolator springInterpolator =
            new android.view.animation.OvershootInterpolator(1.2f);
        scaleX.setInterpolator(springInterpolator);
        scaleY.setInterpolator(springInterpolator);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, alpha, backgroundAlpha);
        animatorSet.start();
    }

    /**
     * Plays achievement celebration sound (different from regular counter sound)
     */
    private void playAchievementSound() {
        try {
            // Use a different system sound for achievements - try alarm sound first (more celebratory)
            android.media.MediaPlayer mediaPlayer = null;

            // First try default alarm sound (more special than notification)
            try {
                mediaPlayer = android.media.MediaPlayer.create(getContext(), android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
            } catch (Exception e) {
                android.util.Log.d("AchievementDialog", "Default alarm URI failed: " + e.getMessage());
            }

            // Fallback to ringtone if alarm failed
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = android.media.MediaPlayer.create(getContext(), android.provider.Settings.System.DEFAULT_RINGTONE_URI);
                } catch (Exception e) {
                    android.util.Log.d("AchievementDialog", "Default ringtone URI failed: " + e.getMessage());
                }
            }

            // Final fallback to notification
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = android.media.MediaPlayer.create(getContext(), android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
                } catch (Exception e) {
                    android.util.Log.d("AchievementDialog", "Default notification URI failed: " + e.getMessage());
                }
            }

            if (mediaPlayer != null) {
                // Store reference to stop when dialog dismisses
                achievementMediaPlayer = mediaPlayer;

                // Set volume lower for achievement sound
                mediaPlayer.setVolume(0.7f, 0.7f);
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    achievementMediaPlayer = null;
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    android.util.Log.e("AchievementDialog", "MediaPlayer error: " + what + ", " + extra);
                    mp.release();
                    achievementMediaPlayer = null;
                    return true;
                });
                mediaPlayer.start();
                android.util.Log.d("AchievementDialog", "Achievement celebration sound played");
            } else {
                android.util.Log.w("AchievementDialog", "No system sounds available for achievement");
            }
        } catch (Exception e) {
            android.util.Log.e("AchievementDialog", "Error playing achievement sound", e);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            // Make dialog full screen to show black overlay properly
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop and cleanup achievement sound when dialog is destroyed
        stopAchievementSound();
    }

    @Override
    public void dismiss() {
        // Stop sound before dismissing
        stopAchievementSound();
        super.dismiss();
    }

    private void stopAchievementSound() {
        try {
            if (achievementMediaPlayer != null) {
                if (achievementMediaPlayer.isPlaying()) {
                    achievementMediaPlayer.stop();
                    android.util.Log.d("AchievementDialog", "Achievement sound stopped on dialog dismiss");
                }
                achievementMediaPlayer.release();
                achievementMediaPlayer = null;
            }
        } catch (Exception e) {
            android.util.Log.e("AchievementDialog", "Error stopping achievement sound", e);
        }
    }
}