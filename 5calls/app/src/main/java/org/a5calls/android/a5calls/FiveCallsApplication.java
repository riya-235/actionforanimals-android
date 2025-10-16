package org.a5calls.android.a5calls;

/*
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.Firebase;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

import com.onesignal.OneSignal;

import org.a5calls.android.a5calls.controller.SettingsActivity;
import org.a5calls.android.a5calls.model.AccountManager;
import org.a5calls.android.a5calls.model.NotificationUtils;
import org.a5calls.android.a5calls.util.AnalyticsManager;

import java.util.UUID;

/**
 * This is a subclass of {@link Application} used to provide shared objects for this app.
 */
public class FiveCallsApplication extends Application {
    private int mRunningActivities;
    private static AnalyticsManager mAnalyticsManager = new AnalyticsManager();

    public static AnalyticsManager analyticsManager() {
        return mAnalyticsManager;
    }

    private static final String ONESIGNAL_APP_ID = "c7e0d1df-299f-4ec5-91f4-a7aed0d9dd09";

    public FiveCallsApplication() {
        super();
        mRunningActivities = 0;
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

            }

            @Override
            public void onActivityStarted(Activity activity) {
                mRunningActivities++;
            }

            @Override
            public void onActivityResumed(Activity activity) {
                // Clear reminder notifications whenever the app is re-opened.
                NotificationUtils.clearNotifications(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {
                mRunningActivities--;
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase and App Check
        FirebaseApp.initializeApp(this);

        // Enable debug logging for App Check
        Log.d("FiveCallsApplication", "Initializing App Check with Play Integrity");

        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();

        if (BuildConfig.DEBUG) {
            // Use debug provider for debug builds - works immediately
            Log.d("FiveCallsApplication", "Using Debug App Check Provider for development");

            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            );
        } else {
            // Use Play Integrity for release builds
            Log.d("FiveCallsApplication", "Using Play Integrity App Check Provider for release");
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            );
        }

        // Add token listener for debugging
        firebaseAppCheck.addAppCheckListener(token -> {
            Log.d("AppCheck", "App Check token received: " + (token != null ? "valid" : "null"));
            if (token != null) {
                Log.d("AppCheck", "Token length: " + token.getToken().length());
                Log.d("AppCheck", "Token prefix: " + token.getToken().substring(0, Math.min(20, token.getToken().length())));
            }
        });

        // Set up OneSignal.
        OneSignal.initWithContext(this, ONESIGNAL_APP_ID);

        String callerID = AccountManager.Instance.getCallerID(this);
        if (callerID.isEmpty()) {
            callerID = UUID.randomUUID().toString();
            AccountManager.Instance.setCallerID(this, callerID);
        }
        OneSignal.login(callerID);

        // Check if notification permission has been revoked outside of the app since the last run
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            // Reset the notifications preference
            SettingsActivity.updateNotificationsPreference(
                    this,
                    AccountManager.Instance,
                    AccountManager.DEFAULT_NOTIFICATION_SELECTION
            );
        }
    }

    public boolean isRunning() {
        return mRunningActivities > 0;
    }
}
