package com.example.remotedevicemanager;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "rdm_prefs";
    private static final String KEY_ID_TOKEN = "id_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_UID = "uid";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_TOKEN_TIME = "token_time";
    private static final String KEY_LOCAL_DEVICE_ID = "local_device_id";

    private static final long TOKEN_REFRESH_MS = 50L * 60L * 1000L;
    private static final long PAIRING_POLL_MS = 5000L;
    private static final long ONLINE_UPDATE_MS = 30000L;

    private SharedPreferences prefs;

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private Button signUpButton;
    private Button logoutButton;

    private LinearLayout authPanel;
    private LinearLayout mainPanel;

    private TextView deviceIdText;
    private TextView ownerUidText;
    private TextView statusText;
    private TextView pairingText;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String idToken;
    private String refreshToken;
    private String ownerUid;
    private String email;

    private String androidId;
    private String deviceId;

    private boolean pollingStarted = false;
    private final Set<String> shownPairingRequests = new HashSet<>();
    private AlertDialog currentPairingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        androidId = getStableAndroidId();
        deviceId = "android_" + androidId;

        buildUi();
        loadAuth();
        updateAuthUI();

        if (idToken != null && ownerUid != null) {
            registerDevice();
            startPolling();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            executor.shutdownNow();
        } catch (Exception ignored) {
        }
        try {
            poller.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = createRoot();
        scroll.addView(root);
        setContentView(scroll);

        createTitle(root, "RemoteDeviceManager");

        authPanel = new LinearLayout(this);
        authPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(authPanel);

        emailInput = createInput(
                authPanel,
                "Email",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        passwordInput = createInput(
                authPanel,
                "Password",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        loginButton = new Button(this);
        loginButton.setText("Login");
        authPanel.addView(loginButton);

        signUpButton = new Button(this);
        signUpButton.setText("Create account");
        authPanel.addView(signUpButton);

        mainPanel = new LinearLayout(this);
        mainPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(mainPanel);

        deviceIdText = createText(mainPanel, "Device ID: -");
        ownerUidText = createText(mainPanel, "Owner UID: -");
        statusText = createText(mainPanel, "Status: -");
        pairingText = createText(mainPanel, "Pairing: -");

        logoutButton = new Button(this);
        logoutButton.setText("Logout");
        mainPanel.addView(logoutButton);

        loginButton.setOnClickListener(v -> loginUser());
        signUpButton.setOnClickListener(v -> signUpUser());
        logoutButton.setOnClickListener(v -> logout());
    }

    private LinearLayout createRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    private TextView createTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(22);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setPadding(0, 0, 0, dp(12));
        parent.addView(tv);
        return tv;
    }

    private TextView createText(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setPadding(0, dp(4), 0, dp(4));
        parent.addView(tv);
        return tv;
    }

    private EditText createInput(LinearLayout parent, String hint, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setSingleLine(true);
        parent.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return et;
    }

    private void setPairingText(String text) {
        if (pairingText != null) {
            pairingText.setText("Pairing: " + text);
        }
    }

    private void showStatus(String text) {
        if (statusText != null) {
            statusText.setText("Status: " + text);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String getStableAndroidId() {
        String value = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        if (value == null || value.trim().isEmpty()) {
            value = prefs.getString(KEY_LOCAL_DEVICE_ID, null);
            if (value == null) {
                value = UUID.randomUUID().toString().replace("-", "");
                prefs.edit().putString(KEY_LOCAL_DEVICE_ID, value).apply();
            }
        } else {
            prefs.edit().putString(KEY_LOCAL_DEVICE_ID, value).apply();
        }

        return value;
    }

    private void loadAuth() {
        idToken = prefs.getString(KEY_ID_TOKEN, null);
        refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null);
        ownerUid = prefs.getString(KEY_UID, null);
        email = prefs.getString(KEY_EMAIL, null);
    }

    private void saveAuth(String idToken, String refreshToken, String uid, String email) {
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.ownerUid = uid;
        this.email = email;

        prefs.edit()
                .putString(KEY_ID_TOKEN, idToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_UID, uid)
                .putString(KEY_EMAIL, email)
                .putLong(KEY_TOKEN_TIME, System.currentTimeMillis())
                .apply();
    }

    private void updateAuthUI() {
        boolean loggedIn = idToken != null && ownerUid != null;

        if (loggedIn) {
            authPanel.setVisibility(View.GONE);
            mainPanel.setVisibility(View.VISIBLE);
            deviceIdText.setText("Device ID: " + deviceId);
            ownerUidText.setText("Owner UID: " + ownerUid);
        } else {
            authPanel.setVisibility(View.VISIBLE);
            mainPanel.setVisibility(View.GONE);
        }
    }

    private void loginUser() {
        String emailValue = emailInput.getText().toString().trim();
        String passwordValue = passwordInput.getText().toString();

        if (emailValue.isEmpty() || passwordValue.isEmpty()) {
            showStatus("Email and password required");
            return;
        }

        showStatus("Logging in...");

        executor.execute(() -> {
            try {
                String response = authRequest("signInWithPassword", emailValue, passwordValue);
                JSONObject obj = new JSONObject(response);

                String newIdToken = obj.getString("idToken");
                String newRefreshToken = obj.getString("refreshToken");
                String localId = obj.getString("localId");
                String emailResp = obj.optString("email", emailValue);

                saveAuth(newIdToken, newRefreshToken, localId, emailResp);

                mainHandler.post(() -> {
                    updateAuthUI();
                    registerDevice();
                    startPolling();
                    showStatus("Logged in");
                });
            } catch (Exception e) {
                mainHandler.post(() -> showStatus("Login failed: " + e.getMessage()));
            }
        });
    }

    private void signUpUser() {
        String emailValue = emailInput.getText().toString().trim();
        String passwordValue = passwordInput.getText().toString();

        if (emailValue.isEmpty() || passwordValue.isEmpty()) {
            showStatus("Email and password required");
            return;
        }

        showStatus("Creating account...");

        executor.execute(() -> {
            try {
                String response = authRequest("signUp", emailValue, passwordValue);
                JSONObject obj = new JSONObject(response);

                String newIdToken = obj.getString("idToken");
                String newRefreshToken = obj.getString("refreshToken");
                String localId = obj.getString("localId");
                String emailResp = obj.optString("email", emailValue);

                saveAuth(newIdToken, newRefreshToken, localId, emailResp);

                mainHandler.post(() -> {
                    updateAuthUI();
                    registerDevice();
                    startPolling();
                    showStatus("Account created");
                });
            } catch (Exception e) {
                mainHandler.post(() -> showStatus("Sign up failed: " + e.getMessage()));
            }
        });
    }

    private void logout() {
        prefs.edit()
                .remove(KEY_ID_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_UID)
                .remove(KEY_EMAIL)
                .remove(KEY_TOKEN_TIME)
                .apply();

        idToken = null;
        refreshToken = null;
        ownerUid = null;
        email = null;

        shownPairingRequests.clear();
        pollingStarted = false;

        try {
            poller.shutdownNow();
        } catch (Exception ignored) {
        }
        poller = Executors.newSingleThreadScheduledExecutor();

        updateAuthUI();
        showStatus("Logged out");
    }

    private String authRequest(String endpoint, String emailValue, String passwordValue) throws Exception {
        String url = "https://identitytoolkit.googleapis.com/v1/accounts:"
                + endpoint
                + "?key="
                + FirebaseConfig.API_KEY;

        JSONObject body = new JSONObject();
        body.put("email", emailValue);
        body.put("password", passwordValue);
        body.put("returnSecureToken", true);

        return httpRequest("POST", url, body.toString(), null, "application/json");
    }

    private synchronized boolean ensureToken() {
        if (idToken == null) {
            return false;
        }

        long tokenTime = prefs.getLong(KEY_TOKEN_TIME, 0L);
        if (System.currentTimeMillis() - tokenTime < TOKEN_REFRESH_MS) {
            return true;
        }

        return refreshIdToken();
    }

    private boolean refreshIdToken() {
        if (refreshToken == null) {
            return false;
        }

        try {
            String url = "https://securetoken.googleapis.com/v1/token?key="
                    + FirebaseConfig.API_KEY;

            String body = "grant_type=refresh_token&refresh_token="
                    + urlEncode(refreshToken);

            String response = httpRequest(
                    "POST",
                    url,
                    body,
                    null,
                    "application/x-www-form-urlencoded"
            );

            JSONObject obj = new JSONObject(response);

            String newIdToken = obj.getString("id_token");
            String newRefreshToken = obj.getString("refresh_token");
            String userId = obj.getString("user_id");

            saveAuth(newIdToken, newRefreshToken, userId, email);
            return true;
        } catch (Exception e) {
            mainHandler.post(() -> showStatus("Token refresh failed: " + e.getMessage()));
            return false;
        }
    }

    private void registerDevice() {
        executor.execute(() -> {
            try {
                if (!ensureToken()) {
                    throw new Exception("Not logged in");
                }

                String path = "devices/" + urlEncode(deviceId);
                String url = firestoreDocUrl(path);

                JSONObject fields = new JSONObject();
                fields.put("deviceId", stringValue(deviceId));
                fields.put("ownerUid", stringValue(ownerUid));
                fields.put("online", boolValue(true));
                fields.put("lastSeen", timestampValue(nowIso()));
                fields.put("model", stringValue(Build.MANUFACTURER + " " + Build.MODEL));
                fields.put("androidId", stringValue(androidId));

                JSONObject body = new JSONObject();
                body.put("fields", fields);

                firestoreRequest("PATCH", url, body.toString());

                mainHandler.post(() -> {
                    deviceIdText.setText("Device ID: " + deviceId);
                    ownerUidText.setText("Owner UID: " + ownerUid);
                    showStatus("Device registered");
                });
            } catch (Exception e) {
                mainHandler.post(() -> showStatus("Register failed: " + e.getMessage()));
            }
        });
    }

    private void startPolling() {
        if (pollingStarted) {
            return;
        }
        pollingStarted = true;

        poller.scheduleWithFixedDelay(() -> {
            try {
                if (idToken != null && ownerUid != null && ensureToken()) {
                    checkPendingPairingRequests();
                }
            } catch (Exception e) {
                mainHandler.post(() -> showStatus("Pairing poll error: " + e.getMessage()));
            }
        }, 0, PAIRING_POLL_MS, TimeUnit.MILLISECONDS);

        poller.scheduleWithFixedDelay(() -> {
            try {
                if (idToken != null && ownerUid != null && ensureToken()) {
                    updateDeviceOnline();
                }
            } catch (Exception e) {
                mainHandler.post(() -> showStatus("Online update error: " + e.getMessage()));
            }
        }, 0, ONLINE_UPDATE_MS, TimeUnit.MILLISECONDS);
                }
        private void updateDeviceOnline() throws Exception {
        String url = firestoreDocUrl("devices/" + urlEncode(deviceId));

        JSONObject fields = new JSONObject();
        fields.put("online", boolValue(true));
        fields.put("lastSeen", timestampValue(nowIso()));

        JSONObject body = new JSONObject();
        body.put("fields", fields);

        firestoreRequest(
                "PATCH",
                url + "?updateMask.fieldPaths=online&updateMask.fieldPaths=lastSeen",
                body.toString()
        );
    }

    private void checkPendingPairingRequests() throws Exception {
        JSONObject query = new JSONObject();
        JSONObject structuredQuery = new JSONObject();

        JSONArray from = new JSONArray();
        JSONObject collection = new JSONObject();
        collection.put("collectionId", "pairingRequests");
        from.put(collection);
        structuredQuery.put("from", from);

        JSONObject composite = new JSONObject();
        composite.put("op", "AND");

        JSONArray filters = new JSONArray();
        filters.put(fieldFilter("deviceOwnerUid", "EQUAL", stringValue(ownerUid)));
        filters.put(fieldFilter("deviceId", "EQUAL", stringValue(deviceId)));
        filters.put(fieldFilter("status", "EQUAL", stringValue("pending")));
        composite.put("filters", filters);

        JSONObject where = new JSONObject();
        where.put("compositeFilter", composite);
        structuredQuery.put("where", where);

        query.put("structuredQuery", structuredQuery);

        String response = httpRequest(
                "POST",
                firestoreRunQueryUrl(),
                query.toString(),
                idToken,
                "application/json"
        );

        JSONArray arr = new JSONArray(response);

        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null || !item.has("document")) {
                continue;
            }

            JSONObject doc = item.getJSONObject("document");
            String docName = doc.getString("name");
            JSONObject fields = doc.optJSONObject("fields");

            String status = getStringField(fields, "status");
            if (!"pending".equals(status)) {
                continue;
            }

            String controllerUid = getStringField(fields, "controllerUid");
            if (controllerUid == null) {
                controllerUid = getStringField(fields, "controllerUID");
            }

            if (controllerUid == null) {
                continue;
            }

            final String finalDocName = docName;
            final String finalControllerUid = controllerUid;

            if (shownPairingRequests.contains(finalDocName)) {
                continue;
            }

            mainHandler.post(() -> showPairingDialog(finalDocName, finalControllerUid));
            break;
        }
    }

    private JSONObject fieldFilter(String field, String op, JSONObject value) throws JSONException {
        JSONObject fieldObj = new JSONObject();
        fieldObj.put("fieldPath", field);

        JSONObject filter = new JSONObject();
        filter.put("field", fieldObj);
        filter.put("op", op);
        filter.put("value", value);

        JSONObject result = new JSONObject();
        result.put("fieldFilter", filter);
        return result;
    }

    private void showPairingDialog(String requestDocName, String controllerUid) {
        if (currentPairingDialog != null && currentPairingDialog.isShowing()) {
            return;
        }

        if (shownPairingRequests.contains(requestDocName)) {
            return;
        }

        shownPairingRequests.add(requestDocName);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pairing request");
        builder.setMessage(
                "Controller UID:\n" + controllerUid
                        + "\n\nDevice ID:\n" + deviceId
        );

        builder.setPositiveButton("APPROVE", (dialog, which) ->
                approvePairing(requestDocName, controllerUid)
        );

        builder.setNegativeButton("DENY", (dialog, which) ->
                denyPairing(requestDocName, controllerUid)
        );

        builder.setCancelable(false);

        currentPairingDialog = builder.create();
        currentPairingDialog.show();

        setPairingText("Pending approval from " + controllerUid);
    }

    private void approvePairing(String requestDocName, String controllerUid) {
        setPairingText("Approving...");

        executor.execute(() -> {
            try {
                if (!ensureToken()) {
                    throw new Exception("Not logged in");
                }

                String accessDocId = deviceId + "_" + controllerUid;
                String accessUrl = firestoreDocUrl("deviceAccess/" + urlEncode(accessDocId));

                JSONObject accessFields = new JSONObject();
                accessFields.put("deviceId", stringValue(deviceId));
                accessFields.put("controllerUid", stringValue(controllerUid));
                accessFields.put("ownerUid", stringValue(ownerUid));
                accessFields.put("status", stringValue("active"));
                accessFields.put("createdAt", timestampValue(nowIso()));

                JSONObject accessBody = new JSONObject();
                accessBody.put("fields", accessFields);

                firestoreRequest("PATCH", accessUrl, accessBody.toString());

                updatePairingStatus(requestDocName, "approved");

                mainHandler.post(() -> {
                    setPairingText("Approved " + controllerUid);
                    showStatus("Pairing approved");
                });
            } catch (Exception e) {
                mainHandler.post(() -> showStatus("Approve failed: " + e.getMessage()));
            }
        });
    }

    private void denyPairing(String requestDocName, String controllerUid) {
        setPairingText("Denying...");

        executor.execute(() -> {
            try {
                if (!ensureToken()) {
                    throw new Exception("Not logged in");
                }

                updatePairingStatus(requestDocName, "denied");

                mainHandler.post(() -> {
                    setPairingText("Denied " + controllerUid);
                    showStatus("Pairing denied");
                });
            } catch (Exception e) {
                mainHandler.post(() -> showStatus("Deny failed: " + e.getMessage()));
            }
        });
    }

    private void updatePairingStatus(String requestDocName, String status) throws Exception {
        String url = "https://firestore.googleapis.com/v1/"
                + requestDocName
                + "?updateMask.fieldPaths=status";

        JSONObject fields = new JSONObject();
        fields.put("status", stringValue(status));

        JSONObject body = new JSONObject();
        body.put("fields", fields);

        firestoreRequest("PATCH", url, body.toString());
    }

    private String firestoreDocUrl(String path) {
        return "https://firestore.googleapis.com/v1/projects/"
                + FirebaseConfig.PROJECT_ID
                + "/databases/"
                + FirebaseConfig.FIRESTORE_DATABASE
                + "/documents/"
                + path;
    }

    private String firestoreRunQueryUrl() {
        return "https://firestore.googleapis.com/v1/projects/"
                + FirebaseConfig.PROJECT_ID
                + "/databases/"
                + FirebaseConfig.FIRESTORE_DATABASE
                + "/documents:runQuery";
    }

    private String firestoreRequest(String method, String url, String body) throws Exception {
        return httpRequest(method, url, body, idToken, "application/json");
    }

    private String httpRequest(
            String method,
            String urlString,
            String body,
            String bearerToken,
            String contentType
    ) throws Exception {
        HttpURLConnection conn = null;

        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoInput(true);

            if (body != null) {
                conn.setDoOutput(true);
            }

            if (contentType != null) {
                conn.setRequestProperty("Content-Type", contentType);
            }

            if (bearerToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }

            conn.setRequestProperty("Accept", "application/json");

            if (body != null) {
                writeBody(conn, body);
            }

            int code = conn.getResponseCode();
            String response = readResponse(conn);

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + response);
            }

            return response;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
        private void writeBody(HttpURLConnection conn, String body) throws Exception {
        OutputStream os = conn.getOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8");
        writer.write(body);
        writer.flush();
        writer.close();
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream is;

        if (code >= 200 && code < 300) {
            is = conn.getInputStream();
        } else {
            is = conn.getErrorStream();
        }

        if (is == null) {
            return "";
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        return sb.toString();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private String nowIso() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US
        );
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private JSONObject stringValue(String value) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("stringValue", value == null ? "" : value);
        return obj;
    }

    private JSONObject boolValue(boolean value) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("booleanValue", value);
        return obj;
    }

    private JSONObject timestampValue(String value) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("timestampValue", value);
        return obj;
    }

    private String getStringField(JSONObject fields, String key) {
        if (fields == null) {
            return null;
        }

        JSONObject value = fields.optJSONObject(key);
        if (value == null) {
            return null;
        }

        if (value.has("stringValue")) {
            return value.optString("stringValue", null);
        }

        if (value.has("timestampValue")) {
            return value.optString("timestampValue", null);
        }

        return null;
    }
}
