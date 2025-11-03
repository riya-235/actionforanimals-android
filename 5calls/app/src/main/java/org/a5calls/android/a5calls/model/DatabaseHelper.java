package org.a5calls.android.a5calls.model;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Local database helper. I believe this is already "thread-safe" and such because SQLiteOpenHelper
 * handles all of that for us. As long as we just use one SQLiteOpenHelper from AppSingleton
 * we should be safe!
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";

    private static final int DATABASE_VERSION = 4;

    // Weekly streak tracking moved to AchievementManager



    private Context mContext;
    @VisibleForTesting
    protected static final String CALLS_TABLE_NAME = "UserCallsDatabase";
    @VisibleForTesting
    protected static final String ISSUES_TABLE_NAME = "UserIssuesTable";
    @VisibleForTesting
    protected static final String CONTACTS_TABLE_NAME = "UserContactsTable";

    // Can be used to control time in tests.
    private TimeProvider mTimeProvider;
    public interface TimeProvider {
        public long currentTimeMillis();
        public Calendar getCalendar();
    }

    private static class DefaultTimeProvider implements TimeProvider {

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public Calendar getCalendar() {
            return Calendar.getInstance();
        }
    }

    private static class CallsColumns {
        public static String TIMESTAMP = "timestamp";
        public static String CONTACT_ID = "contactid";
        public static String ISSUE_ID = "issueid";
        public static String LOCATION = "location";
        public static String RESULT = "result";
        public static String ANIMALS_HELPED_PER_ACTION = "animalshelped";
        public static String CATEGORIES = "categories";
        public static String ACTION_TYPE = "actiontype";
    }

    // Action type constants
    public static class ActionTypes {
        public static final String CALL = "call";
        public static final String EMAIL = "email";
    }

    private static final String CALLS_TABLE_CREATE =
            "CREATE TABLE " + CALLS_TABLE_NAME + " (" +
                CallsColumns.TIMESTAMP + " INTEGER, " + CallsColumns.CONTACT_ID + " STRING, " +
                    CallsColumns.ISSUE_ID + " STRING, " + CallsColumns.LOCATION + " STRING, " +
                    CallsColumns.RESULT + " STRING, " + CallsColumns.ANIMALS_HELPED_PER_ACTION + " INTEGER DEFAULT 1, " +
                    CallsColumns.CATEGORIES + " TEXT, " + CallsColumns.ACTION_TYPE + " TEXT);";

    private static class IssuesColumns {
        public static String ISSUE_ID = "issueid";
        public static String ISSUE_NAME = "issuename";
    }

    private static final String ISSUES_TABLE_CREATE =
            "CREATE TABLE " + ISSUES_TABLE_NAME + " (" + IssuesColumns.ISSUE_ID + " STRING, " +
                    IssuesColumns.ISSUE_NAME + " STRING);";

    public static class ContactColumns {
        public static String CONTACT_ID = "contactid";
        public static String CONTACT_NAME = "contactname";
    }

    private static final String CONTACTS_TABLE_CREATE =
            "CREATE TABLE " + CONTACTS_TABLE_NAME + " (" + ContactColumns.CONTACT_ID + " STRING, " +
                    ContactColumns.CONTACT_NAME + " STRING);";

    public DatabaseHelper(Context context) {
        this(context, new DefaultTimeProvider());
    }

    public DatabaseHelper(Context context, TimeProvider timeProvider) {
        super(context, CALLS_TABLE_NAME, null, DATABASE_VERSION);
        mContext = context;
        mTimeProvider = timeProvider;

        Log.d(TAG, "DatabaseHelper initialized");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CALLS_TABLE_CREATE);
        db.execSQL(ISSUES_TABLE_CREATE);
        db.execSQL(CONTACTS_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        int currentDbVersion = oldVersion;

        if (oldVersion < 2 && currentDbVersion < newVersion) {
            db.execSQL(ISSUES_TABLE_CREATE);
            db.execSQL(CONTACTS_TABLE_CREATE);
            currentDbVersion = 2;
        }

        if (oldVersion < 3 && currentDbVersion < newVersion) {
            ContentValues contactContentValues = new ContentValues();
            contactContentValues.put(CallsColumns.RESULT, Outcome.Status.CONTACT.toString());
            db.update(CALLS_TABLE_NAME,
                    contactContentValues,
                    CallsColumns.RESULT + " = ?",
                    new String[]{Outcome.Status.CONTACTED.toString()});

            ContentValues voicemailContentValues = new ContentValues();
            voicemailContentValues.put(CallsColumns.RESULT, Outcome.Status.VOICEMAIL.toString());
            db.update(CALLS_TABLE_NAME,
                    voicemailContentValues,
                    CallsColumns.RESULT + " = ?",
                    new String[]{Outcome.Status.VM.toString()});
            currentDbVersion = 3;
        }

        if (oldVersion < 4 && currentDbVersion < newVersion) {
            // Add animals_helped_per_action column to calls table
            db.execSQL("ALTER TABLE " + CALLS_TABLE_NAME + " ADD COLUMN " +
                      CallsColumns.ANIMALS_HELPED_PER_ACTION + " INTEGER DEFAULT 1");
            // Add categories column to calls table for achievement tracking
            db.execSQL("ALTER TABLE " + CALLS_TABLE_NAME + " ADD COLUMN " +
                      CallsColumns.CATEGORIES + " TEXT");
            // Add action_type column to explicitly track call vs email
            db.execSQL("ALTER TABLE " + CALLS_TABLE_NAME + " ADD COLUMN " +
                      CallsColumns.ACTION_TYPE + " TEXT");

            currentDbVersion = 4;
        }
    }


    /**
     * Adds a successful call to the user's local database with animals helped count
     */
    public void addCall(String issueId, String issueName, String contactId, String contactName,
                        String result, String location, int animalsHelpedPerAction, String categories, String actionType) {
        Log.d(TAG, "addCall() called with animalsHelpedPerAction: " + animalsHelpedPerAction);
        Log.d(TAG, "  issueId: " + issueId + ", issueName: " + issueName);
        Log.d(TAG, "  contactId: " + contactId + ", result: " + result);

        addCall(issueId, contactId, result, location, animalsHelpedPerAction, categories, actionType);
        addIssue(issueId, issueName);
        addContact(contactId, contactName);

        // Achievement checking (including weekly streak) moved to AchievementManager
    }

    private void addCall(String issueId, String contactId, String result, String location, int animalsHelpedPerAction, String categories, String actionType) {
        Log.d(TAG, "Private addCall() storing to database with animalsHelpedPerAction: " + animalsHelpedPerAction);
        Log.d(TAG, "  actionType: '" + actionType + "' (expecting '" + ActionTypes.CALL + "' for calls or '" + ActionTypes.EMAIL + "' for emails)");
        Log.d(TAG, "  categories: '" + categories + "'");

        ContentValues values = new ContentValues();
        values.put(CallsColumns.TIMESTAMP, mTimeProvider.currentTimeMillis());
        values.put(CallsColumns.CONTACT_ID, contactId);
        values.put(CallsColumns.ISSUE_ID, issueId);
        values.put(CallsColumns.LOCATION, location);
        values.put(CallsColumns.RESULT, result);
        values.put(CallsColumns.ANIMALS_HELPED_PER_ACTION, animalsHelpedPerAction);
        values.put(CallsColumns.CATEGORIES, categories);
        values.put(CallsColumns.ACTION_TYPE, actionType);

        Log.d(TAG, "ContentValues: " + values.toString());

        long rowId = getWritableDatabase().insert(CALLS_TABLE_NAME, null, values);
        Log.d(TAG, "Inserted call with row ID: " + rowId);

        // Verify the insert by querying the last row
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT " + CallsColumns.ANIMALS_HELPED_PER_ACTION + " FROM " + CALLS_TABLE_NAME +
            " WHERE rowid = " + rowId, null);
        if (c.moveToFirst()) {
            int storedValue = c.getInt(0);
            Log.d(TAG, "Verified: stored animals_helped_per_action = " + storedValue);
        } else {
            Log.e(TAG, "ERROR: Could not verify inserted row!");
        }
        c.close();
    }

    private void addIssue(String issueId, String issueName) {
        ContentValues values = new ContentValues();
        values.put(IssuesColumns.ISSUE_ID, issueId);
        values.put(IssuesColumns.ISSUE_NAME, issueName);
        // Insert it into the database if it isn't there yet.
        getWritableDatabase().insertWithOnConflict(ISSUES_TABLE_NAME, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void addContact(String contactId, String contactName) {
        ContentValues values = new ContentValues();
        values.put(ContactColumns.CONTACT_ID, contactId);
        values.put(ContactColumns.CONTACT_NAME, contactName);
        // Insert it into the database if it isn't there yet.
        getWritableDatabase().insertWithOnConflict(CONTACTS_TABLE_NAME, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    public String getIssueName(String issueId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT " + IssuesColumns.ISSUE_NAME + " FROM " +
                ISSUES_TABLE_NAME + " WHERE " + IssuesColumns.ISSUE_ID + " = '" + issueId + "'",
                null);
        String result = "";
        if (c.moveToNext()) {
            result = c.getString(0);
        }
        c.close();
        return result;
    }

    public String getContactName(String contactId) {
        contactId = sanitizeContactId(contactId);
        Cursor c = getReadableDatabase().rawQuery("SELECT " + ContactColumns.CONTACT_NAME +
                " FROM " + CONTACTS_TABLE_NAME + " WHERE " + ContactColumns.CONTACT_ID + " = '" +
                contactId + "'", null);
        String result = "";
        if (c.moveToNext()) {
            result = c.getString(0);
        }
        c.close();
        return result;
    }

    /**
     * Gets the total number of calls made for a given issue and list of contacts.
     * @param issueId
     * @param contacts
     * @return total calls
     */
    public int getTotalCallsForIssueAndContacts(String issueId, List<Contact> contacts) {
        String[] contactIdList = new String[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) {
            contactIdList[i] = "'" + sanitizeContactId(contacts.get(i).id) + "'";
        }
        String contactIds = "(" + TextUtils.join(",", contactIdList) + ")";
        String query = "SELECT " + CallsColumns.CONTACT_ID + " FROM " +
                CALLS_TABLE_NAME + " WHERE " + CallsColumns.ISSUE_ID + " = ? AND " +
                CallsColumns.CONTACT_ID + " IN " + contactIds;
        Cursor c = getReadableDatabase().rawQuery(query, new String[] {issueId});
        int result = 0;
        while (c.moveToNext()) {
            result++;
        }
        c.close();
        return result;
    }

    /**
     * Whether a contact has been called for a particular issue and contact.
     * @param issueId
     * @param contactId
     */
    public boolean hasCalled(String issueId, String contactId) {
        contactId = sanitizeContactId(contactId);
        Cursor c = getReadableDatabase().rawQuery("SELECT " + CallsColumns.TIMESTAMP + " FROM " +
                CALLS_TABLE_NAME + " WHERE " + CallsColumns.ISSUE_ID + " = ? AND " +
                CallsColumns.CONTACT_ID + " = ?", new String[] {issueId, contactId});
        boolean result = c.getCount() > 0;
        c.close();
        return result;
    }

    /**
     * The results for calls made for a particular issue and contact.
     * @param issueId
     * @param contactId
     * @return A list of the call results for this issue and contact.
     */
    public List<String> getCallResults(String issueId, String contactId) {
        contactId = sanitizeContactId(contactId);
        Cursor c = getReadableDatabase().rawQuery("SELECT " + CallsColumns.RESULT + " FROM " +
                CALLS_TABLE_NAME + " WHERE " + CallsColumns.ISSUE_ID + " = ? AND " +
                CallsColumns.CONTACT_ID + " = ? ORDER BY " + CallsColumns.TIMESTAMP,
                new String[] {issueId, contactId});
        List<String> result = new ArrayList<>();
        while (c.moveToNext()) {
            result.add(c.getString(0));
        }
        c.close();
        return result;
    }

    /**
     * Whether a call has been made "today" (local time) for a particular issue and contact.
     * @param issueId
     * @param contactId
     * @return True if so, false otherwise.
     */
    public boolean hasCalledToday(String issueId, String contactId) {
        contactId = sanitizeContactId(contactId);
        Calendar rightNow = mTimeProvider.getCalendar();
        rightNow.set(Calendar.HOUR_OF_DAY, 0);
        rightNow.set(Calendar.MINUTE, 0);
        rightNow.set(Calendar.SECOND, 0);
        String[] selectionArgs = new String[] {issueId, contactId, "" + rightNow.getTimeInMillis()};
        Cursor c = getReadableDatabase().rawQuery("SELECT " + CallsColumns.TIMESTAMP + " FROM " +
                        CALLS_TABLE_NAME + " WHERE " + CallsColumns.ISSUE_ID + " = ? AND " +
                        CallsColumns.CONTACT_ID + " = ? AND " + CallsColumns.TIMESTAMP +
                        " >= ? GROUP BY " + CallsColumns.RESULT, selectionArgs);
        boolean result = c.getCount() > 0;
        c.close();
        return result;
    }

    /**
     * Gets the total number of calls this user has made
     */

    /**
     * Gets the list of timestamps of calls of a particular type (voicemail, unavailable, contact)
     * that this user has made
     */
    public List<Long> getCallTimestampsForType(Outcome.Status status) {
        String statusName = status.toString();

        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + CallsColumns.TIMESTAMP + " FROM " + CALLS_TABLE_NAME + " WHERE " +
                CallsColumns.RESULT + " = '" + statusName + "'", null);
        List<Long> result = new ArrayList<>();
        while (c.moveToNext()) {
            result.add(c.getLong(0));
        }
        c.close();
        return result;
    }

    /**
     * Gets a list of pairs of the contacts called and call count for that contact.
     */
    public List<Pair<String, Integer>> getCallCountsByContact() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + CallsColumns.CONTACT_ID + ", COUNT(*) as COUNT FROM " +
                        CALLS_TABLE_NAME + " GROUP BY " + CallsColumns.CONTACT_ID +
                        " ORDER BY count desc", null);
        List<Pair<String, Integer>> result = new ArrayList<>();
        while (c.moveToNext()) {
            Pair<String, Integer> next = new Pair<>(c.getString(0), c.getInt(1));
            result.add(next);
        }
        c.close();
        return result;
    }

    /**
     * Gets a list of pairs of the issues called and call count for that issue.
     */
    public List<Pair<String, Integer>> getCallCountsByIssue() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + CallsColumns.ISSUE_ID + ", COUNT(*) as COUNT FROM " +
                        CALLS_TABLE_NAME + " GROUP BY " + CallsColumns.ISSUE_ID +
                        " ORDER BY count desc", null);
        List<Pair<String, Integer>> result = new ArrayList<>();
        while (c.moveToNext()) {
            Pair<String, Integer> next = new Pair(c.getString(0), c.getInt(1));
            result.add(next);
        }
        c.close();
        return result;
    }

    /**
     * Gets the total number of animals helped (saved) by the user
     * Sums the animals_helped_per_action from all successful calls
     */
    public int getTotalAnimalsHelped() {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT SUM(" + CallsColumns.ANIMALS_HELPED_PER_ACTION + ") FROM " + CALLS_TABLE_NAME +
                      " WHERE " + CallsColumns.ACTION_TYPE + " IS NOT NULL";

        Cursor c = db.rawQuery(query, null);
        int result = 0;
        if (c.moveToFirst()) {
            result = c.getInt(0);
        }
        c.close();
        return result;
    }

    /**
     * Gets the days of the current week (Monday = 0, Sunday = 6) where user has taken actions
     * Used for weekly streak calendar display
     */
    public Set<Integer> getActionDaysThisWeek() {
        Calendar calendar = mTimeProvider.getCalendar();
        calendar.setFirstDayOfWeek(Calendar.MONDAY); // Week starts Monday like iOS

        // Get start of current week (Monday)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long weekStart = calendar.getTimeInMillis();

        // Get end of current week (Sunday)
        calendar.add(Calendar.DAY_OF_WEEK, 6);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        long weekEnd = calendar.getTimeInMillis();

        Set<Integer> actionDays = new HashSet<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(CALLS_TABLE_NAME,
            new String[]{CallsColumns.TIMESTAMP},
            CallsColumns.TIMESTAMP + " >= ? AND " + CallsColumns.TIMESTAMP + " <= ?",
            new String[]{String.valueOf(weekStart), String.valueOf(weekEnd)},
            null, null, null);

        while (c.moveToNext()) {
            long timestamp = c.getLong(c.getColumnIndexOrThrow(CallsColumns.TIMESTAMP));
            Calendar actionCal = Calendar.getInstance();
            actionCal.setTimeInMillis(timestamp);
            actionCal.setFirstDayOfWeek(Calendar.MONDAY);

            // Convert to Monday=0, Tuesday=1, ... Sunday=6
            int dayOfWeek = actionCal.get(Calendar.DAY_OF_WEEK);
            int mondayBasedDay = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
            actionDays.add(mondayBasedDay);
        }
        c.close();
        return actionDays;
    }

    /**
     * Checks if user has taken any action this week
     */
    public boolean hasActionThisWeek() {
        return !getActionDaysThisWeek().isEmpty();
    }

    /**
     * Gets current day of week (Monday = 0, Sunday = 6) for highlighting "today"
     */
    public int getCurrentDayOfWeek() {
        Calendar calendar = mTimeProvider.getCalendar();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        return (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
    }



    // Weekly streak getter moved to AchievementManager (SharedPreferences, not DB)

    // Weekly streak update methods moved to AchievementManager

    // ==================== ACHIEVEMENT SYSTEM ====================


    /**
     * Get count of actions for a specific category (only counting new actions with ACTION_TYPE set)
     */
    public int getCategoryActionCount(String category) {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + CALLS_TABLE_NAME +
                      " WHERE " + CallsColumns.CATEGORIES + " LIKE ? AND " +
                      CallsColumns.ACTION_TYPE + " IS NOT NULL";
        String[] args = {"%" + category + "%"};

        Cursor c = db.rawQuery(query, args);
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        Log.d(TAG, "getCategoryActionCount('" + category + "') returning: " + count);
        return count;
    }

    /**
     * Get count of actions by type (only counting actions with ACTION_TYPE set)
     */
    public int getActionCount(String actionType) {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + CALLS_TABLE_NAME +
                      " WHERE " + CallsColumns.ACTION_TYPE + " = ?";
        String[] args = {actionType};

        Cursor c = db.rawQuery(query, args);
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        Log.d(TAG, "getActionCount('" + actionType + "') returning: " + count);
        return count;
    }



    /**
     * Convert Issue categories to comma-separated string for storage
     */
    public static String categoriesToString(Category[] categories) {
        if (categories == null || categories.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < categories.length; i++) {
            if (categories[i] != null && categories[i].name != null) {
                sb.append(categories[i].name);
                if (i < categories.length - 1) {
                    sb.append(",");
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // Achievement display methods removed - call AchievementRegistry directly



    @VisibleForTesting
    public static String sanitizeContactId(String contactId) {
        // TODO this only works on single quotes and not double quotes. Triple quotes are still
        // an unknown issue. Should use a regex or something.
        if (contactId.contains("'") && !contactId.contains("''")) {
            return contactId.replace("'", "''");
        }
        return contactId;
    }
}
