package org.a5calls.android.a5calls.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.model.DatabaseHelper;

public class AnimalsCounterView extends TextView {

    // Static list to track all instances for animation
    private static final List<AnimalsCounterView> instances = new ArrayList<>();

    public AnimalsCounterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d("AnimalsCounterView", "Counter created!");

        // Add to instances for animation tracking
        instances.add(this);

        setupUI();
        updateCount();

        setOnClickListener(v -> {
            Log.d("AnimalsCounterView", "Counter clicked!");
            // Default click handler - should be overridden by parent activities
            // MainActivity overrides this with YourImpactDialogFragment
            Log.d("AnimalsCounterView", "Default click handler - no action");
        });
    }

    private void setupUI() {
        // White text on green background for the number
        setTextSize(14);
        setTextColor(0xFFFFFFFF); // White text
        setPadding(24, 12, 24, 12);
        setBackgroundResource(R.drawable.animals_count_background); // Green background

        // Add stars logo
        try {
            Drawable starsDrawable = ContextCompat.getDrawable(getContext(), R.drawable.afa_stars);
            if (starsDrawable != null) {
                int size = (int) (24 * getContext().getResources().getDisplayMetrics().density);
                starsDrawable.setBounds(0, 0, size, size);
                setCompoundDrawables(null, null, starsDrawable, null);
                setCompoundDrawablePadding((int) (8 * getContext().getResources().getDisplayMetrics().density));
            }
        } catch (Exception e) {
            Log.e("AnimalsCounterView", "Error setting stars drawable", e);
        }
    }

    private void updateCount() {
        try {
            DatabaseHelper db = AppSingleton.getInstance(getContext()).getDatabaseHelper();
            int totalAnimalsHelped = db.getTotalAnimalsHelped();
            setText(String.valueOf(totalAnimalsHelped));
            Log.d("AnimalsCounterView", "Updated count to: " + totalAnimalsHelped);
        } catch (Exception e) {
            Log.e("AnimalsCounterView", "Error updating count", e);
            setText("0");
        }
    }

    /**
     * Triggers pulse animation with haptic and sound feedback on all counter instances (like iOS)
     */
    public static void triggerIncrementAnimation() {
        Log.d("AnimalsCounterView", "triggerIncrementAnimation called");
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            // Clean up null/invalid instances
            instances.removeIf(instance -> instance == null || instance.getContext() == null);

            Log.d("AnimalsCounterView", "Found " + instances.size() + " active instances");

            for (AnimalsCounterView instance : instances) {
                Log.d("AnimalsCounterView", "Triggering animations on instance: " + instance);
                instance.refreshCount();
                instance.animatePulse();
                instance.triggerHapticFeedback();
                instance.playSuccessSound();
            }
        });
    }



    /**
     * Refreshes the counter display
     */
    public void refreshCount() {
        Log.d("AnimalsCounterView", "refreshCount called");
        updateCount();
    }

    /**
     * Animates a pulse effect like iOS
     */
    private void animatePulse() {
        // Scale up animation
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(this, "scaleX", 1.0f, 1.3f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(this, "scaleY", 1.0f, 1.3f);

        // Scale down animation
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(this, "scaleX", 1.3f, 1.0f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(this, "scaleY", 1.3f, 1.0f);

        // Set animation properties
        scaleUpX.setDuration(150);
        scaleUpY.setDuration(150);
        scaleDownX.setDuration(150);
        scaleDownY.setDuration(150);

        // Use overshoot interpolator for bouncy effect
        scaleDownX.setInterpolator(new OvershootInterpolator());
        scaleDownY.setInterpolator(new OvershootInterpolator());

        // Chain animations
        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.playTogether(scaleUpX, scaleUpY);

        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.playTogether(scaleDownX, scaleDownY);

        AnimatorSet pulseAnimation = new AnimatorSet();
        pulseAnimation.playSequentially(scaleUp, scaleDown);
        pulseAnimation.start();
    }

    /**
     * Triggers haptic feedback like iOS success feedback
     */
    private void triggerHapticFeedback() {
        try {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // Use impact feedback pattern similar to iOS success haptic
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    VibrationEffect effect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE);
                    vibrator.vibrate(effect);
                } else {
                    vibrator.vibrate(50);
                }
            }
        } catch (Exception e) {
            Log.e("AnimalsCounterView", "Error triggering haptic feedback", e);
        }
    }

    /**
     * Plays success sound like iOS
     */
    private void playSuccessSound() {
        try {
            // Try multiple sound sources for better compatibility
            MediaPlayer mediaPlayer = null;

            // First try default notification sound
            try {
                mediaPlayer = MediaPlayer.create(getContext(), android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            } catch (Exception e) {
                Log.d("AnimalsCounterView", "Default notification URI failed: " + e.getMessage());
            }

            // Fallback to ringtone sound if notification failed
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = MediaPlayer.create(getContext(), android.provider.Settings.System.DEFAULT_RINGTONE_URI);
                } catch (Exception e) {
                    Log.d("AnimalsCounterView", "Default ringtone URI failed: " + e.getMessage());
                }
            }

            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e("AnimalsCounterView", "MediaPlayer error: " + what + ", " + extra);
                    mp.release();
                    return true; // Handle error
                });
                mediaPlayer.start();
            } else {
                Log.w("AnimalsCounterView", "No system sounds available, skipping sound");
            }
        } catch (Exception e) {
            Log.e("AnimalsCounterView", "Error playing success sound", e);
        }
    }
}