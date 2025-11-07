package org.a5calls.android.a5calls.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.a5calls.android.a5calls.R;

/**
 * Achievement Celebration View - shows achievement celebration directly on screen like iOS
 * Replaces AchievementCelebrationDialog for more native iOS-like behavior
 */
public class AchievementCelebrationView extends FrameLayout {
    private static final String TAG = "AchievementView";

    private MediaPlayer achievementMediaPlayer;
    private String achievementTitle;
    private String achievementSubtitle;
    private String achievementIcon;

    public AchievementCelebrationView(Context context) {
        super(context);
        init();
    }

    public AchievementCelebrationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Inflate the layout
        LayoutInflater.from(getContext()).inflate(R.layout.view_achievement_celebration, this, true);

        // Auto-dismiss after 4.5 seconds like iOS
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            dismiss();
        }, 4500);
    }

    /**
     * Show achievement celebration like iOS - adds directly to activity
     */
    public static void show(AppCompatActivity activity, String title, String subtitle, String icon) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        // Create the celebration view
        AchievementCelebrationView celebrationView = new AchievementCelebrationView(activity);
        celebrationView.setAchievementData(title, subtitle, icon);

        // Add to activity's root view
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.addView(celebrationView);
        }
    }

    private void setAchievementData(String title, String subtitle, String icon) {
        this.achievementTitle = title != null ? title : "Achievement Unlocked!";
        this.achievementSubtitle = subtitle != null ? subtitle : "You've reached a milestone";
        this.achievementIcon = icon != null ? icon : "badge_first_call";

        setupContent();
        playAchievementSound();
        animateCelebration();
    }

    private void setupContent() {
        // Set up achievement content with iOS hierarchy:
        // 1. 🎉 (already in layout)
        // 2. "Achievement Unlocked!" (already in layout)
        // 3. Achievement icon with glow
        // 4. Achievement title (e.g., "Rescue Ally")
        // 5. Achievement subtitle (e.g., "3 companion animal actions")

        ImageView iconView = findViewById(R.id.achievement_icon);
        TextView titleView = findViewById(R.id.achievement_title);
        TextView subtitleView = findViewById(R.id.achievement_subtitle);

        // Set the achievement icon by drawable name
        int iconResId = getResources().getIdentifier(achievementIcon, "drawable", getContext().getPackageName());
        if (iconResId != 0) {
            iconView.setImageResource(iconResId);
        } else {
            // Fallback to default achievement icon
            iconView.setImageResource(R.drawable.badge_first_call);
        }

        // achievementTitle is the actual achievement name (e.g., "Rescue Ally")
        // achievementSubtitle is the description (e.g., "3 companion animal actions")
        titleView.setText(achievementTitle);
        subtitleView.setText(achievementSubtitle);
    }

    private void animateCelebration() {
        // Find the card container for animation
        View cardContainer = findViewById(R.id.achievement_card);
        if (cardContainer == null) return;

        // iOS-like spring animation with bounce effect
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(cardContainer, "scaleX", 0.5f, 1.1f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(cardContainer, "scaleY", 0.5f, 1.1f, 1.0f);
        ObjectAnimator cardAlpha = ObjectAnimator.ofFloat(cardContainer, "alpha", 0.0f, 1.0f);

        // Background fade in
        ObjectAnimator backgroundAlpha = ObjectAnimator.ofFloat(this, "alpha", 0.0f, 1.0f);

        scaleX.setDuration(600);
        scaleY.setDuration(600);
        cardAlpha.setDuration(400);
        backgroundAlpha.setDuration(300);

        // Use spring-like interpolator for iOS feel
        OvershootInterpolator springInterpolator = new OvershootInterpolator(1.2f);
        scaleX.setInterpolator(springInterpolator);
        scaleY.setInterpolator(springInterpolator);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, cardAlpha, backgroundAlpha);
        animatorSet.start();
    }

    private void playAchievementSound() {
        try {
            // Try multiple sound sources for better compatibility
            MediaPlayer mediaPlayer = null;

            // First try default alarm sound (more celebratory)
            try {
                mediaPlayer = MediaPlayer.create(getContext(), android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
            } catch (Exception e) {
                Log.d(TAG, "Default alarm URI failed: " + e.getMessage());
            }

            // Fallback to ringtone if alarm failed
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = MediaPlayer.create(getContext(), android.provider.Settings.System.DEFAULT_RINGTONE_URI);
                } catch (Exception e) {
                    Log.d(TAG, "Default ringtone URI failed: " + e.getMessage());
                }
            }

            // Final fallback to notification
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = MediaPlayer.create(getContext(), android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
                } catch (Exception e) {
                    Log.d(TAG, "Default notification URI failed: " + e.getMessage());
                }
            }

            if (mediaPlayer != null) {
                // Store reference to stop when view dismisses
                achievementMediaPlayer = mediaPlayer;

                // Set volume lower for achievement sound
                mediaPlayer.setVolume(0.7f, 0.7f);
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    achievementMediaPlayer = null;
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                    mp.release();
                    achievementMediaPlayer = null;
                    return true;
                });
                mediaPlayer.start();
            } else {
                Log.w(TAG, "No system sounds available for achievement");
            }
        } catch (Exception e) {
        }
    }

    private void dismiss() {
        // Stop sound
        stopAchievementSound();

        // Slow fade out animation like iOS
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1.0f, 0.0f);
        fadeOut.setDuration(800); // Slower fade out (was 250ms, now 800ms)
        fadeOut.setInterpolator(new android.view.animation.DecelerateInterpolator()); // Smooth deceleration
        fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                removeFromParent();
            }
        });
        fadeOut.start();
    }

    private void removeFromParent() {
        if (getParent() != null && getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    private void stopAchievementSound() {
        try {
            if (achievementMediaPlayer != null) {
                if (achievementMediaPlayer.isPlaying()) {
                    achievementMediaPlayer.stop();
                }
                achievementMediaPlayer.release();
                achievementMediaPlayer = null;
            }
        } catch (Exception e) {
        }
    }
}