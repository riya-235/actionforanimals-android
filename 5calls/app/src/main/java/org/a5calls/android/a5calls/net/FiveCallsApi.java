package org.a5calls.android.a5calls.net;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.onesignal.OneSignal;

import org.a5calls.android.a5calls.BuildConfig;
import org.a5calls.android.a5calls.model.AccountManager;
import org.a5calls.android.a5calls.model.Contact;
import org.a5calls.android.a5calls.model.Issue;
import org.a5calls.android.a5calls.model.Outcome;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to handle server gets and posts.
 */
public class FiveCallsApi {
    private static final String TAG = "FiveCallsApi";

    // Set TESTING "true" to set a parameter to the count call request which marks it as a test
    // request on the server. This will only work on debug builds.
    protected static final boolean TESTING = true;

    private static final String GET_ISSUES_REQUEST = "https://getissues-2xumh6zxaa-uc.a.run.app";

    private static final String GET_REPORT = "https://reportcall-2xumh6zxaa-uc.a.run.app";

    // private static final String NEWSLETTER_SUBSCRIBE = "https://buttondown.com/api/emails/embed-subscribe/5calls";
    private static final String NEWSLETTER_SUBSCRIBE = "";

    public interface CallRequestListener {
        void onRequestError();

        void onJsonError();

        void onReportReceived(int count, boolean donateOn);

        void onCallReported();

        // Default implementation for issue count updates - can be overridden
        default void onIssueCountUpdated(String issueId, int updatedCount) {
            // Default: do nothing
        }
    }

    public interface IssuesRequestListener {
        void onRequestError();

        void onJsonError();

        void onIssuesReceived(List<Issue> issues);
    }

    public interface ContactsRequestListener {
        void onRequestError();

        void onJsonError();

        void onAddressError();

        void onContactsReceived(String locationName, boolean isLowAccuracy, String lowAccuracyMessage,
                               List<Contact> contacts, String city, String county, String state);
    }

    public interface NewsletterSubscribeCallback {
        void onSuccess();
        void onError();
    }


    private RequestQueue mRequestQueue;
    private Gson mGson;
    private List<CallRequestListener> mCallRequestListeners = new ArrayList<>();
    private List<IssuesRequestListener> mIssuesRequestListeners = new ArrayList<>();
    private List<ContactsRequestListener> mContactsRequestListeners = new ArrayList<>();


    private final String mCallerId;
    private final Context mContext;

    public FiveCallsApi(String callerId, RequestQueue requestQueue, Context context) {
        // TODO: Using OkHttpClient and OkHttpStack cause failures on multiple types of Samsung
        // Galaxy devices.
        mCallerId = callerId;
        mContext = context;
        //mRequestQueue = Volley.newRequestQueue(context, new OkHttpStack(new OkHttpClient()));
        mRequestQueue = requestQueue;
        mGson = new GsonBuilder()
                .serializeNulls()
                .registerTypeAdapter(Outcome.Status.class, new OutcomeStatusTypeAdapter())
                .create();
    }

    public void registerCallRequestListener(CallRequestListener callRequestListener) {
        mCallRequestListeners.add(callRequestListener);
    }

    public void unregisterCallRequestListener(CallRequestListener callRequestListener) {
        mCallRequestListeners.remove(callRequestListener);
    }

    public void registerIssuesRequestListener(IssuesRequestListener issuesRequestListener) {
        mIssuesRequestListeners.add(issuesRequestListener);
    }

    public void unregisterIssuesRequestListener(IssuesRequestListener issuesRequestListener) {
        mIssuesRequestListeners.remove(issuesRequestListener);
    }

    public void registerContactsRequestListener(ContactsRequestListener contactsRequestListener) {
        mContactsRequestListeners.add(contactsRequestListener);
    }

    public void unregisterContactsRequestListener(ContactsRequestListener contactsRequestListener) {
        mContactsRequestListeners.remove(contactsRequestListener);
    }

    public void onDestroy() {
        mRequestQueue.cancelAll(TAG);
        mRequestQueue.stop();
        mRequestQueue = null;
    }

    public void getIssues() {
        // Build URL with location parameters like iOS app
        String url = buildIssuesUrl();
        buildIssuesRequest(url, mIssuesRequestListeners);
    }

    private String buildIssuesUrl() {
        StringBuilder urlBuilder = new StringBuilder(GET_ISSUES_REQUEST);
        AccountManager accountManager = AccountManager.Instance;

        // Get stored location metadata
        String city = accountManager.getLocationCity(mContext);
        String county = accountManager.getLocationCounty(mContext);
        String state = accountManager.getLocationState(mContext);

        boolean hasParams = false;

        // Add location parameters for geographic filtering (same as iOS)
        if (!TextUtils.isEmpty(state)) {
            urlBuilder.append(hasParams ? "&" : "?").append("state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
            hasParams = true;
            Log.d(TAG, "Adding state query param: " + state);
        }

        if (!TextUtils.isEmpty(county)) {
            urlBuilder.append(hasParams ? "&" : "?").append("county=").append(URLEncoder.encode(county, StandardCharsets.UTF_8));
            hasParams = true;
            Log.d(TAG, "Adding county query param: " + county);
        }

        if (!TextUtils.isEmpty(city)) {
            urlBuilder.append(hasParams ? "&" : "?").append("city=").append(URLEncoder.encode(city, StandardCharsets.UTF_8));
            hasParams = true;
            Log.d(TAG, "Adding city query param: " + city);
        }

        String finalUrl = urlBuilder.toString();
        Log.d(TAG, "Final getIssues URL: " + finalUrl);
        return finalUrl;
    }

    public void getContacts(String address) {
        // Use Firebase Functions instead of direct HTTP call
        FirebaseFunctions functions = FirebaseFunctions.getInstance();

        // Create parameters map
        Map<String, Object> data = new HashMap<>();

        // Check if address is a zipcode (5 digits or zipcode+4) or regular address
        if (address.matches("^\\d{5}$") || address.matches("^\\d{5}-\\d{4}$")) {
            data.put("zipcode", address);
        } else {
            data.put("address", address);
        }

        Log.d(TAG, "Calling getOfficialsCallable with: " + data.toString());

        functions.getHttpsCallable("getOfficialsCallable")
                .call(data)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        HttpsCallableResult result = task.getResult();
                        Object resultData = result.getData();

                        if (resultData instanceof Map) {
                            handleFirebaseContactsResponse((Map<String, Object>) resultData);
                        } else {
                            Log.e(TAG, "Invalid response format from getOfficialsCallable");
                            for (ContactsRequestListener listener : mContactsRequestListeners) {
                                listener.onJsonError();
                            }
                        }
                    } else {
                        Log.e(TAG, "getOfficialsCallable failed", task.getException());
                        for (ContactsRequestListener listener : mContactsRequestListeners) {
                            listener.onRequestError();
                        }
                    }
                });
    }

    private void handleFirebaseContactsResponse(Map<String, Object> response) {
        try {
            String locationName = "";
            boolean lowAccuracy = false;
            String lowAccuracyMessage = null;

            if (response.containsKey("location")) {
                locationName = (String) response.get("location");
            }
            if (response.containsKey("lowAccuracy")) {
                lowAccuracy = (Boolean) response.get("lowAccuracy");
            }
            if (response.containsKey("message")) {
                lowAccuracyMessage = (String) response.get("message");
            }

            // Extract location metadata for passing to listener
            String city = response.containsKey("location") ? (String) response.get("location") : null;
            String county = response.containsKey("county") ? (String) response.get("county") : null;
            String state = response.containsKey("state") ? (String) response.get("state") : null;

            List<Contact> contacts = new ArrayList<>();
            if (response.containsKey("representatives")) {
                Object repsObj = response.get("representatives");
                if (repsObj instanceof List) {
                    List<Map<String, Object>> reps = (List<Map<String, Object>>) repsObj;
                    for (Map<String, Object> rep : reps) {
                        Contact contact = parseContactFromMap(rep);
                        if (contact != null) {
                            contacts.add(contact);
                        }
                    }
                }
            }

            // Notify listeners with location metadata
            for (ContactsRequestListener listener : mContactsRequestListeners) {
                listener.onContactsReceived(locationName, lowAccuracy, lowAccuracyMessage, contacts, city, county, state);
            }

            // Handle OneSignal tags (same as original implementation)
            if (response.containsKey("state") && response.containsKey("district")) {
                String stateCode = (String) response.get("state");
                String district = (String) response.get("district");
                if (!TextUtils.isEmpty(stateCode) && !TextUtils.isEmpty(district)) {
                    if (OneSignal.isInitialized()) {
                        OneSignal.getUser().addTag("districtID", stateCode + "-" + district);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing Firebase contacts response", e);
            for (ContactsRequestListener listener : mContactsRequestListeners) {
                listener.onJsonError();
            }
        }
    }

    private Contact parseContactFromMap(Map<String, Object> contactMap) {
        // This method converts the Map representation to a Contact object
        // You'll need to implement this based on your Contact class structure
        try {
            Gson gson = mGson;
            String jsonString = gson.toJson(contactMap);
            return gson.fromJson(jsonString, Contact.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing contact from map", e);
            return null;
        }
    }


    private void buildIssuesRequest(String url, final List<IssuesRequestListener> listeners) {
        // Request a JSON Object response from the provided URL.
        JsonArrayRequest issuesRequest = new JsonArrayRequest(
                Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                if (response == null) {
                    for (IssuesRequestListener listener : listeners) {
                        listener.onJsonError();
                    }
                    return;
                }
                Type listType = new TypeToken<ArrayList<Issue>>(){}.getType();
                List<Issue> issues = mGson.fromJson(response.toString(),
                        listType);

                issues = Outcome.filterSkipOutcomes(issues);

                // TODO: Sanitize contact IDs here
                for (IssuesRequestListener listener : listeners) {
                    listener.onIssuesReceived(issues);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                for (IssuesRequestListener listener : listeners) {
                    listener.onRequestError();
                }
            }
        });
        issuesRequest.setTag(TAG);
        // Add the request to the RequestQueue.
        mRequestQueue.add(issuesRequest);
    }

    private void buildContactsRequest(String url, final List<ContactsRequestListener> listeners) {
            // Request a JSON Object response from the provided URL.
            JsonObjectRequest contactsRequest = new JsonObjectRequest(
                    Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    if (response != null) {
                        String locationName = "";
                        boolean lowAccuracy = false;
                        String lowAccuracyMessage = null;
                        try {
                            locationName = response.getString("location");
                            if (response.has("lowAccuracy")) {
                                lowAccuracy = response.getBoolean("lowAccuracy");
                            }
                            if (response.has("message")) {
                                lowAccuracyMessage = response.getString("message");
                            }
                        } catch (JSONException e) {
                            for (ContactsRequestListener listener : listeners) {
                                listener.onJsonError();
                            }
                        }
                        JSONArray jsonArray = response.optJSONArray("representatives");
                        if (jsonArray == null) {
                            for (ContactsRequestListener listener : listeners) {
                                listener.onJsonError();
                            }
                            return;
                        }
                        Type listType = new TypeToken<ArrayList<Contact>>(){}.getType();
                        List<Contact> contacts = mGson.fromJson(jsonArray.toString(), listType);

                        // Extract location metadata for passing to listener
                        String city = response.optString("location", null);
                        String county = response.optString("county", null);
                        String state = response.optString("state", null);

                        for (ContactsRequestListener listener : listeners) {
                            listener.onContactsReceived(locationName, lowAccuracy, lowAccuracyMessage, contacts, city, county, state);
                        }

                        try {
                            String stateCode = response.getString("state");
                            String district = response.getString("district");
                            if (!TextUtils.isEmpty(stateCode) && !TextUtils.isEmpty(district)) {
                                if (OneSignal.isInitialized()) {
                                    OneSignal.getUser().addTag("districtID", stateCode + "-" + district);
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 400) {
                        // Address error. We reached the server but it couldn't create a response.
                        for (ContactsRequestListener listener : listeners) {
                            listener.onAddressError();
                        }
                        return;
                    }
                    for (ContactsRequestListener listener : listeners) {
                        listener.onRequestError();
                    }
                }
            });
            contactsRequest.setTag(TAG);
            // Add the request to the RequestQueue.
            mRequestQueue.add(contactsRequest);
    }

    public void getReport() {
        JsonObjectRequest reportRequest = new JsonObjectRequest(
                Request.Method.GET, GET_REPORT, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    int count = response.getInt("count");
                    boolean donateOn = response.getBoolean("donateOn");
                    for (CallRequestListener listener : mCallRequestListeners) {
                        listener.onReportReceived(count, donateOn);
                    }
                } catch (JSONException e) {
                    for (CallRequestListener listener : mCallRequestListeners) {
                        listener.onJsonError();
                    }
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                onRequestError(error);
            }
        });
        reportRequest.setTag(TAG); // TODO: same tag OK?
        // Add the request to the RequestQueue.
        mRequestQueue.add(reportRequest);
    }

    // Result is "VOICEMAIL", "unavailable", or "contacted"
    // https://github.com/5calls/5calls/blob/master/static/js/main.js#L221
    public void reportCall(final String issueId, final String contactId, final String result,
                           final String zip) {
        String getReport = GET_REPORT;
        StringRequest request = new StringRequest(Request.Method.POST, getReport,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "reportCall response: " + response);

                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            int issueCount = jsonResponse.getInt("issueCount");
                            Log.d(TAG, "Parsed issueCount: " + issueCount + " for issue: " + issueId);

                            // Directly update the issue data in all listeners
                            for (CallRequestListener listener : mCallRequestListeners) {
                                listener.onIssueCountUpdated(issueId, issueCount);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Failed to parse reportCall response JSON", e);
                        }

                        for (CallRequestListener listener : mCallRequestListeners) {
                            listener.onCallReported();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                onRequestError(error);
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("issueid", issueId);
                params.put("result", result);
                params.put("contactid", contactId);
                params.put("via", (BuildConfig.DEBUG && TESTING) ? "test" : "android");
                params.put("callerid", mCallerId);

                return params;
            }


            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("Content-Type", "application/x-www-form-urlencoded");
                return params;
            }
        };
        request.setTag(TAG);
        // Add the request to the RequestQueue.
        mRequestQueue.add(request);
    }

    public void newsletterSubscribe(String email, NewsletterSubscribeCallback callback) {
        StringRequest request = new StringRequest(Request.Method.POST, NEWSLETTER_SUBSCRIBE,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        callback.onSuccess();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                callback.onError();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("email", email);
                params.put("tag", "android");
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("Content-Type", "application/x-www-form-urlencoded");
                return params;
            }
        };
        request.setTag(TAG);
        // Add the request to the RequestQueue.
        mRequestQueue.add(request);
    }

    private void onRequestError(VolleyError error) {
        for (CallRequestListener listener : mCallRequestListeners) {
            listener.onRequestError();
        }
        if (error.getMessage() == null) {
            Log.d("Error", "no message");
        } else {
            Log.d("Error", error.getMessage());
        }
    }

}
