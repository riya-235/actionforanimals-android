package org.a5calls.android.a5calls.controller;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.core.view.GravityCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
// Removed unused imports: AdapterView, ArrayAdapter (filter functionality removed)
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ImageView;

import java.util.HashSet;
import java.util.Set;

import com.google.firebase.auth.FirebaseAuth;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.FiveCallsApplication;
import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.adapter.IssuesAdapter;
import org.a5calls.android.a5calls.databinding.ActivityMainBinding;
import org.a5calls.android.a5calls.model.AccountManager;
import org.a5calls.android.a5calls.model.Category;
import org.a5calls.android.a5calls.model.Contact;
import org.a5calls.android.a5calls.net.FiveCallsApi;
import org.a5calls.android.a5calls.model.Issue;
import org.a5calls.android.a5calls.util.AnalyticsManager;
import org.a5calls.android.a5calls.util.CustomTabsUtil;
import org.a5calls.android.a5calls.util.ContentChangeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static android.view.View.VISIBLE;

/**
 * The activity which handles zip code lookup and showing the issues list.
 */
public class MainActivity extends AppCompatActivity implements IssuesAdapter.Callback {
    private static final String TAG = "MainActivity";
    private static final int ISSUE_DETAIL_REQUEST = 1;
    public static final int NOTIFICATION_REQUEST = 2;
    public static final String EXTRA_FROM_NOTIFICATION = "extraFromNotification";
    private static final String KEY_SEARCH_TEXT = "searchText";
    private static final String KEY_SHOW_LOW_ACCURACY_WARNING = "showLowAccuracyWarning";
    private final AccountManager accountManager = AccountManager.Instance;

    // Removed filter functionality
    private String mSearchText = "";
    private Set<String> mSelectedCategories = new HashSet<>(); // Multi-select categories
    private IssuesAdapter mIssuesAdapter;
    private FiveCallsApi.IssuesRequestListener mIssuesRequestListener;
    private FiveCallsApi.ContactsRequestListener mContactsRequestListener;
    private FiveCallsApi.CallRequestListener mReportListener;
    private OnBackPressedCallback mOnBackPressedCallback;
    private String mAddress;
    private String mLatitude;
    private String mLongitude;
    private String mLocationName;
    private boolean mIsLowAccuracy = false;
    private boolean mShowLowAccuracyWarning = true;
    private boolean mDonateIsOn = false;
    private FirebaseAuth mAuth = null;
    private boolean wasInBackground = true; // Start as true so first launch refreshes from server

    private ActivityMainBinding binding;
    private LocationBottomSheetFragment currentLocationBottomSheet;

    private Snackbar mSnackbar;

    // Debounced search runnable
    private final Runnable searchRunnable = new Runnable() {
        @Override
        public void run() {
            if (!binding.swipeContainer.isRefreshing()) {
                applyFilters();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: Consider using fragments
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());


        try {
            mAuth = FirebaseAuth.getInstance();
        } catch (RuntimeException ex) {
            Log.e(TAG, ex.getMessage());
            mAuth = null;
        }

        // See if we've had this user before. If not, start them at welcome page.
        if (!accountManager.isTutorialSeen(this)) {
            Intent intent = new Intent(this, WelcomeActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Confirm the user has set a location.
        if (!accountManager.hasLocation(this)) {
            // No location set, show bottom sheet to set location
            // Will be shown after the activity is fully initialized
        }

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null &&
                intent.getExtras().getBoolean(EXTRA_FROM_NOTIFICATION, false)) {
            FiveCallsApplication.analyticsManager().trackPageviewWithProps("/", this,
                    Map.of("fromNotification", "true"));
        } else {
            FiveCallsApplication.analyticsManager().trackPageview("/", this);
        }

        setContentView(binding.getRoot());

        // iOS-style header - no action bar needed
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // Setup iOS-style header interactions
        setupIOSHeader();

        setupDrawerContent(binding.navigationView);

        if (!accountManager.isNewsletterPromptDone(this)) {
            binding.newsletterSignupView.setVisibility(View.GONE);
            binding.newsletterView.newsletterDeclineButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    accountManager.setNewsletterPromptDone(v.getContext(), true);
                    findViewById(R.id.newsletter_card).setVisibility(View.GONE);
                    findViewById(R.id.newsletter_card_result_decline).setVisibility(VISIBLE);
                }
            });
            binding.newsletterView.newsletterSignupButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String email = binding.newsletterView.newsletterEmail.getText().toString();
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        binding.newsletterView.newsletterEmail.setError(
                                getResources().getString(R.string.error_email_format));
                        return;
                    }
                    binding.newsletterView.newsletterSignupButton.setEnabled(false);
                    binding.newsletterView.newsletterDeclineButton.setEnabled(false);
                    FiveCallsApi api =
                            AppSingleton.getInstance(getApplicationContext()).getJsonController();
                    api.newsletterSubscribe(email, new FiveCallsApi.NewsletterSubscribeCallback() {
                        @Override
                        public void onSuccess() {
                            accountManager.setNewsletterPromptDone(v.getContext(), true);
                            accountManager.setNewsletterSignUpCompleted(v.getContext(), true);
                            findViewById(R.id.newsletter_card).setVisibility(View.GONE);
                            findViewById(R.id.newsletter_card_result_success).setVisibility(VISIBLE);
                        }

                        @Override
                        public void onError() {
                            binding.newsletterView.newsletterSignupButton.setEnabled(true);
                            binding.newsletterView.newsletterDeclineButton.setEnabled(true);
                            showSnackbar(R.string.newsletter_signup_error, Snackbar.LENGTH_LONG);
                        }
                    });
                }
            });
        }

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        binding.issuesRecyclerView.setLayoutManager(layoutManager);
        // Removed DividerItemDecoration - using custom iOS-style separators in layout
        mIssuesAdapter = new IssuesAdapter(this, this);
        binding.issuesRecyclerView.setAdapter(mIssuesAdapter);

        // Setup app lifecycle observer for true backgrounding detection
        setupAppLifecycleObserver();

        // Setup always-visible search functionality
        if (savedInstanceState != null) {
            mSearchText = savedInstanceState.getString(KEY_SEARCH_TEXT);
            if (mSearchText == null) mSearchText = "";
            mShowLowAccuracyWarning = savedInstanceState.getBoolean(KEY_SHOW_LOW_ACCURACY_WARNING);
            if (!TextUtils.isEmpty(mSearchText)) {
                binding.searchInput.setText(mSearchText);
            }
        } else {
            mSearchText = ""; // Initialize to empty string
        }
        
        // Setup real-time search as user types
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String searchText = s.toString().trim();
                if (!TextUtils.equals(mSearchText, searchText)) {
                    mSearchText = searchText;
                    updateOnBackPressedCallbackEnabled();
                    // Debounce search - only search after user stops typing for 300ms
                    binding.searchInput.removeCallbacks(searchRunnable);
                    binding.searchInput.postDelayed(searchRunnable, 300);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Setup category filter chips
        setupCategoryFilters();

        registerOnBackPressedCallback();

        registerApiListener();

        // Refresh the "donateOn" information. This doesn't change much so it's sufficient
        // to do it just once in the activity's lifecycle.
        AppSingleton.getInstance(getApplicationContext()).getJsonController().getReport();

        binding.swipeContainer.setColorSchemeResources(R.color.colorPrimary);
        binding.swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshIssues();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mAuth != null) {
            mAuth.signInAnonymously();
        }
    }

    @Override
    protected void onDestroy() {
        FiveCallsApi api = AppSingleton.getInstance(getApplicationContext()).getJsonController();
        api.unregisterIssuesRequestListener(mIssuesRequestListener);
        api.unregisterContactsRequestListener(mContactsRequestListener);
        api.unregisterCallRequestListener(mReportListener);
        super.onDestroy();
    }

    // onStop removed - using ProcessLifecycleOwner instead for true backgrounding detection

    @Override
    protected void onResume() {
        super.onResume();

        binding.drawerLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                int supportActionBarHeight =
                        getSupportActionBar() != null ? getSupportActionBar().getHeight() : 0;
                int searchHeight = binding.searchContainer.getHeight();
                binding.swipeContainer.getLayoutParams().height = (int)
                        (getResources().getConfiguration().screenHeightDp * displayMetrics.density -
                                searchHeight - supportActionBarHeight);
                binding.searchContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        loadStats();

        mAddress = accountManager.getAddress(this);
        mLatitude = accountManager.getLat(this);
        mLongitude = accountManager.getLng(this);

        if (accountManager.isNewsletterPromptDone(this) ||
                accountManager.isNewsletterSignUpCompleted(this)) {
            binding.newsletterSignupView.setVisibility(View.GONE);
        }

        // Only refresh from server if app was actually backgrounded OR if we have no data
        int itemCount = mIssuesAdapter.getItemCount();
        Log.d("MainActivity", "onResume - wasInBackground: " + wasInBackground + ", itemCount: " + itemCount);

        if (wasInBackground || itemCount == 0) {
            // Refresh from server when returning from background or on first load
            Log.d("MainActivity", "DECISION: Refreshing from SERVER (wasInBackground=" + wasInBackground + ", itemCount=" + itemCount + ")");
            binding.swipeContainer.post(new Runnable() {
                @Override
                public void run() {
                    binding.swipeContainer.setRefreshing(true);
                    refreshIssues();
                }
            });
            wasInBackground = false;
            Log.d("MainActivity", "wasInBackground reset to false");
        } else {
            // Just refresh UI (green dots, filters) when navigating between screens
            Log.d("MainActivity", "DECISION: UI-only refresh (wasInBackground=" + wasInBackground + ", itemCount=" + itemCount + ")");
            refreshUIOnly();
        }

        // No need to reset flags - ProcessLifecycleOwner handles backgrounding detection

        // Check if location needs to be set and show bottom sheet if needed
        if (!accountManager.hasLocation(this)) {
            binding.getRoot().post(() -> {
                showLocationBottomSheet();
            });
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(KEY_SEARCH_TEXT, mSearchText);
        outState.putBoolean(KEY_SHOW_LOW_ACCURACY_WARNING, mShowLowAccuracyWarning);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            binding.drawerLayout.openDrawer(GravityCompat.START);
            return true;
        } else if (item.getItemId() == R.id.menu_refresh) {
            binding.swipeContainer.post(new Runnable() {
                @Override
                public void run() {
                    binding.swipeContainer.setRefreshing(true);
                    refreshIssues();
                }
            });
            return true;
        } else if (item.getItemId() == R.id.menu_search) {
            // Focus on the search input instead of launching dialog
            binding.searchInput.requestFocus();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void launchSearchDialog() {
        DialogFragment searchIssuesDialog = SearchIssuesDialog.newInstance(mSearchText);
        getSupportFragmentManager().beginTransaction().add(searchIssuesDialog,
                SearchIssuesDialog.TAG).commit();
    }

    @Override
    public void startIssueActivity(Context context, Issue issue) {
        Intent issueIntent = new Intent(context, IssueActivity.class);
        issueIntent.putExtra(IssueActivity.KEY_ISSUE, issue);
        issueIntent.putExtra(RepCallActivity.KEY_ADDRESS, getLocationString());
        issueIntent.putExtra(RepCallActivity.KEY_LOCATION_NAME, mLocationName);
        issueIntent.putExtra(IssueActivity.KEY_IS_LOW_ACCURACY, mIsLowAccuracy);
        issueIntent.putExtra(IssueActivity.KEY_DONATE_IS_ON, mDonateIsOn);
        startActivityForResult(issueIntent, ISSUE_DETAIL_REQUEST);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        binding.drawerLayout.closeDrawers();

                        if (item.getItemId() == R.id.menu_settings) {
                            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                            startActivity(intent);
                            return true;
                        }

                        return true;
                    }
                });
    }


    private void registerApiListener() {
        mIssuesRequestListener = new FiveCallsApi.IssuesRequestListener() {
            @Override
            public void onRequestError() {
                showSnackbar(R.string.request_error, Snackbar.LENGTH_LONG);
                // Our only type of request in MainActivity is a GET. If it doesn't work, clear the
                // active issues list to avoid showing a stale list.
                mIssuesAdapter.setAllIssues(Collections.<Issue>emptyList(),
                        IssuesAdapter.ERROR_REQUEST);
                binding.swipeContainer.setRefreshing(false);
            }

            @Override
            public void onJsonError() {
                showSnackbar(R.string.json_error, Snackbar.LENGTH_LONG);
                // Our only type of request in MainActivity is a GET. If it doesn't work, clear the
                // active issues list to avoid showing a stale list.
                mIssuesAdapter.setAllIssues(Collections.<Issue>emptyList(),
                        IssuesAdapter.ERROR_REQUEST);
                binding.swipeContainer.setRefreshing(false);
            }

            @Override
            public void onIssuesReceived(List<Issue> issues) {
                // Check for content changes and update changed issue IDs
                ContentChangeManager contentChangeManager = new ContentChangeManager(MainActivity.this);
                contentChangeManager.updateChangedIssues(issues);
                
                mIssuesAdapter.setAllIssues(issues, IssuesAdapter.NO_ERROR);
                mIssuesAdapter.setFilterAndSearch(getString(R.string.all_issues_filter), mSearchText); // Show all issues by default
                binding.swipeContainer.setRefreshing(false);
            }
        };

        mContactsRequestListener = new FiveCallsApi.ContactsRequestListener() {
            @Override
            public void onRequestError() {
                showSnackbar(R.string.request_error, Snackbar.LENGTH_LONG);
                // Our only type of request in MainActivity is a GET. If it doesn't work, clear the
                // active issues list to avoid showing a stale list.
                mIssuesAdapter.setAddressError(IssuesAdapter.ERROR_REQUEST);
                binding.swipeContainer.setRefreshing(false);
            }

            @Override
            public void onJsonError() {
                // If we have an open location bottom sheet, show error there
                if (currentLocationBottomSheet != null && currentLocationBottomSheet.isVisible()) {
                    currentLocationBottomSheet.onLocationValidationError("Invalid or no data for this location. Try again later.");
                } else {
                    showSnackbar(R.string.json_error, Snackbar.LENGTH_LONG);
                }
                // Our only type of request in MainActivity is a GET. If it doesn't work, clear the
                // active issues list to avoid showing a stale list.
                mIssuesAdapter.setAddressError(IssuesAdapter.ERROR_REQUEST);
                binding.swipeContainer.setRefreshing(false);
            }

            @Override
            public void onAddressError() {
                showAddressErrorSnackbar();
                mIssuesAdapter.setAddressError(IssuesAdapter.ERROR_ADDRESS);
                binding.swipeContainer.setRefreshing(false);
            }

            @Override
            public void onContactsReceived(String locationName, boolean isLowAccuracy,
                                           List<Contact> contacts) {
                // Always update with server-provided location name (server does the geocoding)
                mLocationName = TextUtils.isEmpty(locationName) ?
                        getResources().getString(R.string.unknown_location) : locationName;
                updateLocationHeader();
                mIssuesAdapter.setContacts(contacts, IssuesAdapter.NO_ERROR);
                mIsLowAccuracy = isLowAccuracy;

                hideSnackbars();

                // If we have an open location bottom sheet, dismiss it on successful validation
                if (currentLocationBottomSheet != null && currentLocationBottomSheet.isVisible()) {
                    currentLocationBottomSheet.onLocationValidationSuccess();
                    currentLocationBottomSheet = null; // Clear reference
                }

                if (mShowLowAccuracyWarning) {
                    // Check if this is a split district by seeing if there are >2 reps in the house.
                    int houseCount = 0;
                    for (Contact contact : contacts) {
                        if (TextUtils.equals(contact.area, "US House")) {
                            houseCount++;
                        }
                    }
                    if (houseCount > 1 || mIsLowAccuracy) {
                        int warning = houseCount > 1 ? R.string.split_district_warning :
                                R.string.low_accuracy_warning;
                        mSnackbar = Snackbar.make(binding.drawerLayout, warning,
                                        Snackbar.LENGTH_INDEFINITE)
                                .setAction(R.string.update, view -> showLocationBottomSheet());
                        mSnackbar.setActionTextColor(getResources().getColor(
                                R.color.colorAccentLight));
                        mSnackbar.addCallback(new BaseTransientBottomBar.BaseCallback<>() {
                            @Override
                            public void onDismissed(Snackbar transientBottomBar, int event) {
                                super.onDismissed(transientBottomBar, event);
                                mSnackbar = null;
                            }
                        });
                        // https://stackoverflow.com/questions/30705607/android-multiline-snackbar
                        TextView textView = mSnackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
                        textView.setMaxLines(5);
                        mSnackbar.show();
                        // Only show it once.
                        mShowLowAccuracyWarning = false;
                    }
                }
            }
        };

        mReportListener = new FiveCallsApi.CallRequestListener() {
            @Override
            public void onRequestError() {
            }

            @Override
            public void onJsonError() {
            }

            @Override
            public void onReportReceived(int count, boolean donateOn) {
                mDonateIsOn = donateOn;
            }

            @Override
            public void onCallReported() {
            }

            @Override
            public void onIssueCountUpdated(String issueId, int updatedCount) {
                if (mIssuesAdapter != null) {
                    mIssuesAdapter.updateIssueCount(issueId, updatedCount);
                }
            }
        };

        FiveCallsApi api = AppSingleton.getInstance(getApplicationContext()).getJsonController();
        api.registerIssuesRequestListener(mIssuesRequestListener);
        api.registerContactsRequestListener(mContactsRequestListener);
        api.registerCallRequestListener(mReportListener);
    }

    // Registers a callback that handles back presses. This should be active
    // only when filtering or searching, so that it can do a one-time clear of
    // the active filter or search back to the default main activity state.
    private void registerOnBackPressedCallback() {
        mOnBackPressedCallback = new OnBackPressedCallback(/* enabled= */ false) {
            @Override
            public void handleOnBackPressed() {
                // Clear the search field
                binding.searchInput.setText("");
                // This will trigger the TextWatcher and clear search
            }
        };
        updateOnBackPressedCallbackEnabled();
        getOnBackPressedDispatcher().addCallback(mOnBackPressedCallback);
    }

    // Should be called whenever search state changes.
    private void updateOnBackPressedCallbackEnabled() {
        boolean isSearching = !TextUtils.isEmpty(mSearchText);
        mOnBackPressedCallback.setEnabled(isSearching);
    }

    // Removed populateFilterAdapterIfNeeded method - no longer using filters

    private void loadStats() {
        // iOS-style header doesn't show personal call count in header
        // Call count tracking still works in the background for individual issues
    }


    @Override
    public void refreshIssues() {
        FiveCallsApi api = AppSingleton.getInstance(getApplicationContext()).getJsonController();

        if (!mIssuesAdapter.hasContacts()) {
            String location = getLocationString();
            if (!TextUtils.isEmpty(location)) {
                api.getContacts(location);
            }
        }
        api.getIssues();
    }

    /**
     * Refresh the UI without hitting the server - updates green dots, filters, etc.
     */
    private void refreshUIOnly() {
        Log.d("MainActivity", "refreshUIOnly called");
        if (mIssuesAdapter != null) {
            // Re-apply current filters and search to refresh green dot indicators
            applyFilters();

            // Update location header in case it changed
            updateLocationHeader();
            Log.d("MainActivity", "refreshUIOnly completed - applied filters and updated header");
        } else {
            Log.d("MainActivity", "refreshUIOnly - mIssuesAdapter is null!");
        }
    }

    private void updateIssueCount(String issueId, int updatedCount) {
        Log.d("MainActivity", "updateIssueCount called for issue: " + issueId + " with count: " + updatedCount);
        if (mIssuesAdapter != null) {
            mIssuesAdapter.updateIssueCount(issueId, updatedCount);
        }
    }

    @Override
    public void showLocationBottomSheet() {
        currentLocationBottomSheet = LocationBottomSheetFragment.newInstance();
        currentLocationBottomSheet.setLocationSetListener(location -> {
            // Handle location input from bottom sheet
            handleLocationInput(location);
        });
        currentLocationBottomSheet.show(getSupportFragmentManager(), "LocationBottomSheet");
    }

    private String getLocationString() {
        if (!TextUtils.isEmpty(mLatitude) && !TextUtils.isEmpty(mLongitude)) {
            return mLatitude + "," + mLongitude;

        } else if (!TextUtils.isEmpty(mAddress)) {
            return mAddress;
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ISSUE_DETAIL_REQUEST && resultCode == RESULT_OK) {
            Issue issue = data.getExtras().getParcelable(IssueActivity.KEY_ISSUE);
            mIssuesAdapter.updateIssue(issue);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // Method for search dialog compatibility (though dialog is rarely used now)
    public void onIssueSearchSet(String searchText) {
        binding.searchInput.setText(searchText);
        // TextWatcher will handle the actual search
    }

    public void onIssueSearchCleared() {
        binding.searchInput.setText("");
        // TextWatcher will handle clearing the search
    }

    private void hideSnackbars() {
        // Hide any existing snackbars.
        if (mSnackbar != null) {
            mSnackbar.dismiss();
            mSnackbar = null;
        }
    }

    private void showSnackbar(int message, int length) {
        if (mSnackbar == null) {
            constructSnackbar(message, length);
            mSnackbar.show();
        }
    }

    private void showAddressErrorSnackbar() {
        hideSnackbars();
        constructSnackbar(R.string.error_address_invalid, Snackbar.LENGTH_INDEFINITE);
        mSnackbar.setAction(R.string.update, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLocationBottomSheet();
            }
        });
        mSnackbar.show();
    }

    private void constructSnackbar(int message, int length) {
        mSnackbar = Snackbar.make(binding.drawerLayout,
                getResources().getString(message),
                length);
        mSnackbar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                super.onDismissed(transientBottomBar, event);
                mSnackbar = null;
            }
        });
    }

    private void setupCategoryFilters() {
        LinearLayout categoryContainer = binding.categoryChipsContainer;
        categoryContainer.removeAllViews(); // Clear any existing chips
        
        // Initialize with no categories selected (show all by default)
        mSelectedCategories.clear();
        
        // Add "All" chip (always first) - selected when no categories are chosen
        addCategoryChip("All", true, false); // not last
        
        // Add category chips for the 3 main categories
        String[] categories = {"Farmed", "Wildlife", "Companion"};
        for (int i = 0; i < categories.length; i++) {
            boolean isLast = (i == categories.length - 1);
            addCategoryChip(categories[i], false, isLast);
        }
    }
    
    private void addCategoryChip(String categoryName, boolean isSelected, boolean isLast) {
        Button chip = new Button(this);
        chip.setText(categoryName);
        chip.setBackground(getDrawable(R.drawable.category_chip_background));
        chip.setTextColor(getColorStateList(R.color.category_chip_text_color));
        chip.setSelected(isSelected);
        chip.setAllCaps(false);
        chip.setTextSize(14);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.NORMAL);
        
        // Set padding and margins for iOS-style appearance (more horizontal padding)
        chip.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        // Don't add end margin to the last chip
        if (!isLast) {
            params.setMarginEnd(12);
        }
        chip.setLayoutParams(params);
        
        // Handle chip selection
        chip.setOnClickListener(v -> {
            selectCategoryChip(categoryName);
        });
        
        binding.categoryChipsContainer.addView(chip);
    }
    
    private void selectCategoryChip(String categoryName) {
        if (categoryName.equals("All")) {
            // "All" button - clear all category selections
            mSelectedCategories.clear();
        } else {
            // Category button - toggle selection
            if (mSelectedCategories.contains(categoryName)) {
                mSelectedCategories.remove(categoryName);
            } else {
                mSelectedCategories.add(categoryName);
            }
        }
        
        // Update chip selection states
        updateChipSelectionStates();
        
        // Apply the new filter
        applyFilters();
    }
    
    private void updateChipSelectionStates() {
        LinearLayout container = binding.categoryChipsContainer;
        for (int i = 0; i < container.getChildCount(); i++) {
            Button chip = (Button) container.getChildAt(i);
            String chipText = chip.getText().toString();
            
            if (chipText.equals("All")) {
                // "All" is selected when no specific categories are chosen
                chip.setSelected(mSelectedCategories.isEmpty());
            } else {
                // Category chips are selected based on the set
                chip.setSelected(mSelectedCategories.contains(chipText));
            }
        }
    }
    
    private void applyFilters() {
        if (mIssuesAdapter != null) {
            // Pass the selected categories set to the adapter
            if (mSelectedCategories.isEmpty()) {
                // No categories selected - show all issues
                mIssuesAdapter.setFilterAndSearchWithCategories(
                    getString(R.string.all_issues_filter), mSearchText, mSelectedCategories);
            } else {
                // Categories selected - filter by them
                mIssuesAdapter.setFilterAndSearchWithCategories(
                    "", mSearchText, mSelectedCategories);
            }
        }
    }
    
    private void setupIOSHeader() {
        // Setup settings gear click - directly open settings
        binding.settingsGear.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        
        // Setup location header click - show iOS-style bottom sheet
        binding.locationHeader.setOnClickListener(v -> {
            showLocationBottomSheet();
        });
        
        // Initialize location display
        updateLocationHeader();
    }
    
    private void updateLocationHeader() {
        TextView locationDescription = binding.locationDescription;
        TextView locationText = binding.locationText;
        ImageView locationIcon = binding.locationIcon;
        
        if (TextUtils.isEmpty(mLocationName) || mLocationName.equals(getString(R.string.unknown_location))) {
            // No location set - single line
            locationDescription.setVisibility(View.GONE);
            locationIcon.setVisibility(View.GONE);
            locationText.setText("Set Your Location");
            locationText.setTextSize(16); // Smaller when no location set
        } else {
            // Location set - two lines like iOS
            locationDescription.setVisibility(View.VISIBLE);
            locationIcon.setVisibility(View.VISIBLE);
            locationDescription.setText("Your location is:");
            locationText.setText(mLocationName);
            locationText.setTextSize(18); // Larger for the city name
        }
    }
    
    private void handleLocationInput(String location) {
        // Handle both manual input and GPS detection
        // Don't set mLocationName here - let server response set it to avoid flicker
        
        // For manual input, set the address
        if (!"Current Location".equals(location)) {
            accountManager.setAddress(this, location);
        }
        // For GPS detection, the coordinates and address are already set by the bottom sheet
        
        // Update member variables immediately so getLocationString() uses the new location
        mAddress = accountManager.getAddress(this);
        mLatitude = accountManager.getLat(this);
        mLongitude = accountManager.getLng(this);
        
        // Clear existing contacts so new ones will be fetched for the new location
        mIssuesAdapter.setContacts(new ArrayList<Contact>(), IssuesAdapter.NO_ERROR);
        // We can show the warning again next time, because the location may have changed.
        mShowLowAccuracyWarning = true;
        
        // Don't update header here - let server response update it to avoid flicker
        refreshIssues();
    }

    /**
     * Setup ProcessLifecycleOwner to detect true app backgrounding vs screen navigation
     */
    private void setupAppLifecycleObserver() {
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
            public void onAppBackgrounded() {
                Log.d("MainActivity", "ProcessLifecycleOwner: App backgrounded - wasInBackground set to true");
                wasInBackground = true;
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            public void onAppForegrounded() {
                Log.d("MainActivity", "ProcessLifecycleOwner: App foregrounded");
                // Don't set wasInBackground = false here, let onResume handle the logic
            }
        });
    }
}
