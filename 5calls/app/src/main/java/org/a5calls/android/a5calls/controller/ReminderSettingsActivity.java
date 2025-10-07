package org.a5calls.android.a5calls.controller;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.app.TimePickerDialog;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.model.AccountManager;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reminders settings screen
 */
public class ReminderSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make window semi-transparent for floating effect
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        setContentView(R.layout.activity_reminder_settings_custom);

        // Use standard ActionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle("Reminder Settings");
            actionBar.setHomeButtonEnabled(false);
            
            // Center the title
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.actionbar_title_centered);
            
            // Set the title text in the custom view
            View customView = actionBar.getCustomView();
            if (customView != null) {
                TextView titleView = customView.findViewById(R.id.action_bar_title);
                if (titleView != null) {
                    titleView.setText("Reminder Settings");
                }
            }
        }

        initializeCustomViews();

        // Apply slide-in animation from bottom to top
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_bottom);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_done) {
            finish();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        // Schedule reminders before finishing
        AccountManager accountManager = AccountManager.Instance;
        SettingsActivity.turnOnReminders(this, accountManager);
        
        super.finish();
        // Apply slide-out animation from top to bottom when closing
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }
    
    private void initializeCustomViews() {
        // Load saved values first
        loadSavedSettings();
        
        // Set up time picker
        TextView timeDisplay = findViewById(R.id.time_display);
        if (timeDisplay != null) {
            timeDisplay.setOnClickListener(v -> showTimePicker());
        }
        
        // Set up day selection
        setupDaySelection();
        
        // Set up reminder switch
        setupReminderSwitch();
    }
    
    private void loadSavedSettings() {
        AccountManager accountManager = AccountManager.Instance;
        
        // Load reminder enabled state
        boolean isEnabled = accountManager.getAllowReminders(this);
        android.widget.Switch reminderSwitch = findViewById(R.id.reminder_switch);
        if (reminderSwitch != null) {
            reminderSwitch.setChecked(isEnabled);
        }
        
        // Load reminder time
        int reminderMinutes = accountManager.getReminderMinutes(this);
        if (reminderMinutes == 0) {
            reminderMinutes = 19 * 60; // Default to 7:00 PM
            accountManager.setReminderMinutes(this, reminderMinutes);
        }
        
        int hour = reminderMinutes / 60;
        int minute = reminderMinutes % 60;
        String timeString = formatTime(hour, minute);
        TextView timeDisplay = findViewById(R.id.time_display);
        if (timeDisplay != null) {
            timeDisplay.setText(timeString);
        }
        
        // Load reminder days
        Set<String> savedDays = accountManager.getReminderDays(this);
        // Convert string set to integer set for the UI
        Set<Integer> selectedDays = new HashSet<>();
        for (String day : savedDays) {
            try {
                selectedDays.add(Integer.parseInt(day));
            } catch (NumberFormatException e) {
                // Skip invalid day values
            }
        }
        
        // Update UI state
        updateReminderStatus(isEnabled);
    }
    
    private void updateReminderStatus(boolean isEnabled) {
        TextView reminderStatus = findViewById(R.id.reminder_status);
        if (reminderStatus != null) {
            reminderStatus.setText(isEnabled ? "Active" : "Disabled");
        }
        
        TextView timeDisplay = findViewById(R.id.time_display);
        if (timeDisplay != null) {
            timeDisplay.setEnabled(isEnabled);
            timeDisplay.setAlpha(isEnabled ? 1.0f : 0.5f);
        }
        
        LinearLayout daysContainer = findViewById(R.id.days_container);
        if (daysContainer != null) {
            daysContainer.setEnabled(isEnabled);
            daysContainer.setAlpha(isEnabled ? 1.0f : 0.5f);
            
            // Enable/disable all day buttons
            for (int i = 0; i < daysContainer.getChildCount(); i++) {
                daysContainer.getChildAt(i).setEnabled(isEnabled);
            }
        }
        
        updatePreview();
    }
    
    private void showTimePicker() {
        // Load current saved time
        AccountManager accountManager = AccountManager.Instance;
        int currentMinutes = accountManager.getReminderMinutes(this);
        int hour = currentMinutes / 60;
        int minute = currentMinutes % 60;
        
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minuteOfHour) -> {
                    // Save the new time
                    int reminderMinutes = hourOfDay * 60 + minuteOfHour;
                    accountManager.setReminderMinutes(this, reminderMinutes);
                    
                    // Update UI
                    String timeString = formatTime(hourOfDay, minuteOfHour);
                    TextView timeDisplay = findViewById(R.id.time_display);
                    if (timeDisplay != null) {
                        timeDisplay.setText(timeString);
                    }
                    
                    // Schedule reminders immediately when time changes
                    SettingsActivity.turnOnReminders(this, accountManager);
                    
                    updatePreview();
                }, hour, minute, DateFormat.is24HourFormat(this));
        
        timePickerDialog.show();
    }
    
    private String formatTime(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        return DateFormat.format("h:mm a", calendar).toString();
    }
    
    private void setupDaySelection() {
        LinearLayout daysContainer = findViewById(R.id.days_container);
        if (daysContainer == null) return;
        
        String[] days = {"S", "M", "T", "W", "T", "F", "S"};
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        
        // Load saved days
        AccountManager accountManager = AccountManager.Instance;
        Set<String> savedDays = accountManager.getReminderDays(this);
        Set<Integer> selectedDays = new HashSet<>();
        for (String day : savedDays) {
            try {
                selectedDays.add(Integer.parseInt(day));
            } catch (NumberFormatException e) {
                // Skip invalid day values
            }
        }
        
        // If no days are saved, default to weekdays
        if (selectedDays.isEmpty()) {
            selectedDays.add(1); // Monday
            selectedDays.add(2); // Tuesday
            selectedDays.add(3); // Wednesday
            selectedDays.add(4); // Thursday
            selectedDays.add(5); // Friday
            // Save the default
            Set<String> defaultDays = new HashSet<>();
            for (int day : selectedDays) {
                defaultDays.add(String.valueOf(day));
            }
            accountManager.setReminderDays(this, defaultDays);
        }
        
        for (int i = 0; i < days.length; i++) {
            TextView dayButton = new TextView(this);
            dayButton.setText(days[i]);
            dayButton.setTextSize(16);
            dayButton.setPadding(16, 12, 16, 12);
            dayButton.setGravity(android.view.Gravity.CENTER);
            dayButton.setBackgroundResource(android.R.drawable.btn_default);
            dayButton.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            
            final int dayIndex = i;
            final boolean isSelected = selectedDays.contains(i);
            updateDayButton(dayButton, isSelected);
            
                dayButton.setOnClickListener(v -> {
                if (selectedDays.contains(dayIndex)) {
                    selectedDays.remove(dayIndex);
                } else {
                    selectedDays.add(dayIndex);
                }
                updateDayButton(dayButton, selectedDays.contains(dayIndex));
                updateDaysSummary(selectedDays);
                
                // Save the updated days
                Set<String> daysToSave = new HashSet<>();
                for (int day : selectedDays) {
                    daysToSave.add(String.valueOf(day));
                }
                accountManager.setReminderDays(this, daysToSave);
                
                // Schedule reminders immediately when days change
                SettingsActivity.turnOnReminders(this, accountManager);
                
                updatePreview();
            });
            
            daysContainer.addView(dayButton);
        }
        
        updateDaysSummary(selectedDays);
    }
    
    private void updateDayButton(TextView button, boolean isSelected) {
        if (isSelected) {
            // Use the same blue color as category filters when selected (#007AFF)
            button.setBackgroundColor(0xFF007AFF);
            button.setTextColor(getResources().getColor(android.R.color.white));
        } else {
            // Use the same light gray background as category filters when unselected (#F2F2F7)
            button.setBackgroundColor(0xFFF2F2F7);
            button.setTextColor(getResources().getColor(android.R.color.black));
        }
    }
    
    private void updateDaysSummary(Set<Integer> selectedDays) {
        TextView daysSummary = findViewById(R.id.days_summary);
        if (daysSummary == null) return;
        
        if (selectedDays.isEmpty()) {
            daysSummary.setText("No days selected");
        } else if (selectedDays.size() == 7) {
            daysSummary.setText("Every day");
        } else if (selectedDays.size() == 5 && selectedDays.contains(1) && selectedDays.contains(2) && 
                   selectedDays.contains(3) && selectedDays.contains(4) && selectedDays.contains(5)) {
            daysSummary.setText("Weekdays");
        } else if (selectedDays.size() == 2 && selectedDays.contains(0) && selectedDays.contains(6)) {
            daysSummary.setText("Weekends");
        } else {
            daysSummary.setText(selectedDays.size() + " days selected");
        }
    }
    
    private void setupReminderSwitch() {
        android.widget.Switch reminderSwitch = findViewById(R.id.reminder_switch);
        
        if (reminderSwitch != null) {
            reminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Save the reminder state
                AccountManager accountManager = AccountManager.Instance;
                accountManager.setAllowReminders(this, isChecked);
                
                // Update UI
                updateReminderStatus(isChecked);
                
                // Schedule reminders immediately when toggled
                SettingsActivity.turnOnReminders(this, accountManager);
            });
        }
    }
    
    private void updatePreview() {
        TextView previewText = findViewById(R.id.preview_text);
        if (previewText == null) return;
        
        android.widget.Switch reminderSwitch = findViewById(R.id.reminder_switch);
        boolean isEnabled = reminderSwitch != null && reminderSwitch.isChecked();
        
        if (!isEnabled) {
            previewText.setText("Reminder is disabled. Enable the switch above to set up notifications.");
        } else {
            TextView timeDisplay = findViewById(R.id.time_display);
            String time = timeDisplay != null ? timeDisplay.getText().toString() : "9:00 AM";
            
            TextView daysSummary = findViewById(R.id.days_summary);
            String days = daysSummary != null ? daysSummary.getText().toString() : "weekdays";
            
            previewText.setText("\"Time to check your app!\" at " + time + " on " + days.toLowerCase() + ".");
        }
    }

    public static class ReminderSettingsFragment extends PreferenceFragmentCompat implements
            SharedPreferences.OnSharedPreferenceChangeListener {
        private final AccountManager accountManager = AccountManager.Instance;
        private ActivityResultLauncher<String> mNotificationPermissionRequest;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mNotificationPermissionRequest = SettingsActivity.createNotificationPermissionRequest(this, (isGranted) -> {
                accountManager.setAllowReminders(getActivity(), isGranted);
                if (!isGranted) {
                    SwitchPreference remindersPref = findPreference(AccountManager.KEY_ALLOW_REMINDERS);
                    if (remindersPref != null) {
                        remindersPref.setChecked(false);
                        remindersPref.setSummary(R.string.reminders_disabled_summary);
                    }
                }
            });
        }

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            addPreferencesFromResource(R.xml.reminder_settings);

            boolean hasReminders = accountManager.getAllowReminders(getActivity());
            SwitchPreference remindersPref = findPreference(AccountManager.KEY_ALLOW_REMINDERS);
            if (remindersPref != null) {
                // Ensure the preference is enabled
                remindersPref.setEnabled(true);
                remindersPref.setChecked(hasReminders);

                remindersPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    accountManager.setAllowReminders(getActivity(), enabled);

                    // Update dependent preferences visibility
                    updateDependentPreferences(enabled);

                    // Handle notification permission if needed
                    if (enabled &&
                            !NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) {
                        if (mNotificationPermissionRequest != null
                                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mNotificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS);
                        }
                    }
                    return true;
                });
            }

            // Set up days selection
            Set<String> reminderDays = accountManager.getReminderDays(getActivity());
            MultiSelectListPreference daysPreference = findPreference(AccountManager.KEY_REMINDER_DAYS);
            if (daysPreference != null) {
                daysPreference.setValues(reminderDays);
                updateReminderDaysSummary(daysPreference, reminderDays);

                daysPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    Set<String> selectedDays = (Set<String>) newValue;
                    accountManager.setReminderDays(getActivity(), selectedDays);
                    updateReminderDaysSummary(daysPreference, selectedDays);
                    return true;
                });
            }

            // Set up time picker
            Preference timePreference = findPreference("reminder_time");
            if (timePreference != null) {
                timePreference.setOnPreferenceClickListener(preference -> {
                    final TimePickerFragment dialog = new TimePickerFragment();
                    dialog.setCallback((hourOfDay, minute) -> {
                        final int reminderMinutes = hourOfDay * 60 + minute;
                        accountManager.setReminderMinutes(requireContext(), reminderMinutes);
                        updateReminderTimeSummary(timePreference);
                    });
                    dialog.show(getParentFragmentManager(), "timePicker");
                    return true;
                });
                updateReminderTimeSummary(timePreference);
            }
            
            // Set initial state of dependent preferences
            updateDependentPreferences(hasReminders);
        }
        
        private void updateDependentPreferences(boolean enabled) {
            // Update days preference
            MultiSelectListPreference daysPreference = findPreference(AccountManager.KEY_REMINDER_DAYS);
            if (daysPreference != null) {
                daysPreference.setEnabled(enabled);
                if (enabled) {
                    daysPreference.setSummary("Select which days to receive reminders");
                } else {
                    daysPreference.setSummary("Enable reminders first");
                }
            }
            
            // Update time preference
            Preference timePreference = findPreference("reminder_time");
            if (timePreference != null) {
                timePreference.setEnabled(enabled);
                if (enabled) {
                    updateReminderTimeSummary(timePreference);
                } else {
                    timePreference.setSummary("Enable reminders first");
                }
            }
            
            // Visual feedback is handled by the preference system automatically
            // when we call setEnabled() on the preferences
        }

        private void updateReminderDaysSummary(MultiSelectListPreference daysPreference,
                                               Set<String> savedValues) {
            if (savedValues == null || savedValues.size() == 0) {
                daysPreference.setSummary("No days selected");
                return;
            }
            List<String> daysEntries = Arrays.asList(getActivity().getResources()
                    .getStringArray(R.array.reminder_days_titles));
            List<String> daysEntriesValues = Arrays.asList(getActivity().getResources()
                    .getStringArray(R.array.reminder_days_values));
            
            // Smart summary based on selection
            if (savedValues.size() == 7) {
                daysPreference.setSummary("Every day");
            } else if (savedValues.size() == 5 && 
                      savedValues.contains("2") && savedValues.contains("3") && 
                      savedValues.contains("4") && savedValues.contains("5") && 
                      savedValues.contains("6")) {
                daysPreference.setSummary("Weekdays");
            } else if (savedValues.size() == 2 && 
                      savedValues.contains("1") && savedValues.contains("7")) {
                daysPreference.setSummary("Weekends");
            } else {
                String summary = "";
                for (int i = 0; i < daysEntriesValues.size(); i++) {
                    if (savedValues.contains(daysEntriesValues.get(i))) {
                        if (!TextUtils.isEmpty(summary)) {
                            summary += ", ";
                        }
                        summary += daysEntries.get(i);
                    }
                }
                daysPreference.setSummary(summary);
            }
        }

        private void updateReminderTimeSummary(Preference timePreference) {
            Calendar c = Calendar.getInstance();
            int storedMinutes = accountManager.getReminderMinutes(requireContext());
            
            // Set smart default if no time is set (7:00 PM)
            if (storedMinutes == 0) {
                storedMinutes = 19 * 60; // 7:00 PM
                accountManager.setReminderMinutes(requireContext(), storedMinutes);
            }
            
            int hour = storedMinutes / 60;
            int minutes = storedMinutes % 60;

            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minutes);

            final SimpleDateFormat dateFormat;
            if (DateFormat.is24HourFormat(requireContext())) {
                dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            } else {
                dateFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            }
            
            // Add smart context to the summary
            String timeString = dateFormat.format(c.getTime());
            String context = getSmartTimeContext(hour);
            timePreference.setSummary(timeString + " " + context);
        }
        
        private String getSmartTimeContext(int hour) {
            if (hour >= 6 && hour < 12) {
                return "(Morning)";
            } else if (hour >= 12 && hour < 17) {
                return "(Afternoon)";
            } else if (hour >= 17 && hour < 21) {
                return "(Evening)";
            } else {
                return "(Night)";
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onStop() {
            SettingsActivity.turnOnReminders(getActivity(), accountManager);
            super.onStop();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (TextUtils.equals(key, AccountManager.KEY_REMINDER_DAYS)) {
                Set<String> result = sharedPreferences.getStringSet(key,
                        AccountManager.DEFAULT_REMINDER_DAYS);
                MultiSelectListPreference daysPreference = findPreference(AccountManager.KEY_REMINDER_DAYS);
                if (daysPreference != null) {
                    updateReminderDaysSummary(daysPreference, result);
                }
            }
        }

    }
}