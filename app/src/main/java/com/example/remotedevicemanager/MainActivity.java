package com.example.remotedevicemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String PREFS =
            "remote_device_manager";

    private static final String KEY_TOKEN =
            "idToken";

    private static final String KEY_UID =
            "uid";

    private static final String KEY_DEVICE_ID =
            "deviceId";

    private static final long PAIRING_CHECK_INTERVAL =
            5000L;

    private EditText emailInput;
    private EditText passwordInput;

    private Button loginButton;
    private Button createAccountButton;

    private TextView statusText;
    private TextView deviceText;

    private SharedPreferences preferences;

    private final Handler pairingHandler =
            new Handler(Looper.getMainLooper());

    private final Set<String> shownRequestIds =
            new HashSet<>();

    private boolean pairingCheckRunning = false;

    private final Runnable pairingChecker =
            new Runnable() {

                @Override
                public void run() {

                    if (!isLoggedIn()) {
                        pairingCheckRunning = false;
                        return;
                    }

                    checkPairingRequests();

                    pairingHandler.postDelayed(
                            this,
                            PAIRING_CHECK_INTERVAL
                    );
                }
            };

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        preferences =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        if (isLoggedIn()) {

            showDeviceScreen();

        } else {

            showLoginScreen();
        }
    }

    private boolean isLoggedIn() {

        String token =
                preferences.getString(
                        KEY_TOKEN,
                        ""
                );

        return token != null
                && !token.isEmpty();
    }

    private void showLoginScreen() {

        stopPairingChecker();

        LinearLayout root =
                createRoot();

        TextView title =
                createTitle(
                        "REMOTE DEVICE MANAGER"
                );

        TextView subtitle =
                createText(
                        "PHONE B • Secure Login"
                );

        emailInput =
                createInput("Email");

        passwordInput =
                createInput("Password");

        passwordInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        loginButton =
                new Button(this);

        loginButton.setText("LOGIN");

        createAccountButton =
                new Button(this);

        createAccountButton.setText(
                "CREATE ACCOUNT"
        );

        statusText =
                createText("Ready");

        root.addView(title);
        root.addView(subtitle);
        root.addView(emailInput);
        root.addView(passwordInput);
        root.addView(loginButton);
        root.addView(createAccountButton);
        root.addView(statusText);

        loginButton.setOnClickListener(
                v -> login(false)
        );

        createAccountButton.setOnClickListener(
                v -> login(true)
        );
    }

    private void login(
            boolean createAccount
    ) {

        String email =
                emailInput
                        .getText()
                        .toString()
                        .trim();

        String password =
                passwordInput
                        .getText()
                        .toString();

        if (email.isEmpty()) {

            showStatus("Email দাও।");
            return;
        }

        if (password.length() < 6) {

            showStatus(
                    "Password কমপক্ষে 6 characters হওয়া দরকার।"
            );

            return;
        }

        if (!firebaseConfigured()) {

            showStatus(
                    "FirebaseConfig.java-তে API_KEY এবং PROJECT_ID বসাও।"
            );

            return;
        }

        loginButton.setEnabled(false);
        createAccountButton.setEnabled(false);

        showStatus(
                createAccount
                        ? "Account তৈরি হচ্ছে..."
                        : "Login হচ্ছে..."
        );

        ApiClient.Callback callback =
                new ApiClient.Callback() {

                    @Override
                    public void onSuccess(
                            JSONObject result
                    ) {

                        runOnUiThread(() -> {

                            saveLogin(result);

                            showDeviceScreen();
                        });
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        runOnUiThread(() -> {

                            loginButton.setEnabled(true);
                            createAccountButton.setEnabled(true);

                            showStatus(message);
                        });
                    }
                };

        if (createAccount) {

            ApiClient.signUp(
                    email,
                    password,
                    callback
            );

        } else {

            ApiClient.signIn(
                    email,
                    password,
                    callback
            );
        }
    }

    private void saveLogin(
            JSONObject result
    ) {

        preferences
                .edit()
                .putString(
                        KEY_TOKEN,
                        result.optString(
                                "idToken",
                                ""
                        )
                )
                .putString(
                        KEY_UID,
                        result.optString(
                                "localId",
                                ""
                        )
                )
                .apply();
    }

    private void showDeviceScreen() {

        LinearLayout root =
                createRoot();

        TextView title =
                createTitle(
                        "PHONE B"
                );

        TextView subtitle =
                createText(
                        "Remote Device Manager"
                );

        deviceText =
                createText("");

        statusText =
                createText(
                        "Registering device..."
                );

        Button refreshButton =
                new Button(this);

        refreshButton.setText(
                "CHECK PAIRING"
        );

        Button logoutButton =
                new Button(this);

        logoutButton.setText(
                "LOGOUT"
        );

        root.addView(title);
        root.addView(subtitle);
        root.addView(deviceText);
        root.addView(statusText);
        root.addView(refreshButton);
        root.addView(logoutButton);

        refreshButton.setOnClickListener(
                v -> checkPairingRequests()
        );

        logoutButton.setOnClickListener(
                v -> logout()
        );

        registerDevice();
    }

    private void registerDevice() {

        String token =
                preferences.getString(
                        KEY_TOKEN,
                        ""
                );

        String uid =
                preferences.getString(
                        KEY_UID,
                        ""
                );

        String deviceId =
                getLocalDeviceId();

        String deviceName =
                "Phone B - "
                        + Build.MANUFACTURER
                        + " "
                        + Build.MODEL;

        String now =
                currentTime();

        deviceText.setText(
                "Device ID:\n"
                        + deviceId
                        + "\n\nDevice:\n"
                        + deviceName
                        + "\n\nOwner UID:\n"
                        + uid
        );

        ApiClient.createOrUpdateDevice(
                token,
                deviceId,
                uid,
                deviceName,
                "Android",
                "online",
                now,
                now,
                new ApiClient.Callback() {

                    @Override
                    public void onSuccess(
                            JSONObject result
                    ) {

                        runOnUiThread(() -> {

                            showStatus(
                                    "✓ Phone B registered successfully."
                            );

                            startPairingChecker();
                        });
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        runOnUiThread(() ->
                                showStatus(
                                        "Device registration failed:\n"
                                                + message
                                )
                        );
                    }
                }
        );
    }

    private void startPairingChecker() {

        if (pairingCheckRunning) {
            return;
        }

        pairingCheckRunning = true;

        pairingHandler.removeCallbacks(
                pairingChecker
        );

        pairingHandler.post(
                pairingChecker
        );
    }

    private void stopPairingChecker() {

        pairingCheckRunning = false;

        pairingHandler.removeCallbacks(
                pairingChecker
        );
    }

    private void checkPairingRequests() {

        String token =
                preferences.getString(
                        KEY_TOKEN,
                        ""
                );

        String uid =
                preferences.getString(
                        KEY_UID,
                        ""
                );

        if (token.isEmpty()
                || uid.isEmpty()) {

            return;
        }

        ApiClient.getPendingPairingRequests(
                token,
                uid,
                new ApiClient.Callback() {

                    @Override
                    public void onSuccess(
                            JSONObject result
                    ) {

                        runOnUiThread(() ->
                                handlePairingResults(
                                        result
                                )
                        );
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        runOnUiThread(() -> {

                            if (message != null
                                    && !message.isEmpty()) {

                                statusText.setText(
                                        "Pairing check:\n"
                                                + message
                                );
                            }
                        });
                    }
                }
        );
    }

    private void handlePairingResults(
            JSONObject result
    ) {

        JSONArray requests =
                result.optJSONArray(
                        "requests"
                );

        if (requests == null
                || requests.length() == 0) {

            statusText.setText(
                    "✓ Device online\n"
                            + "No pending pairing request."
            );

            return;
        }

        for (int i = 0;
             i < requests.length();
             i++) {

            JSONObject request =
                    requests.optJSONObject(i);

            if (request == null) {
                continue;
            }

            String requestId =
                    request.optString(
                            "requestId",
                            ""
                    );

            if (requestId.isEmpty()) {
                continue;
            }

            if (shownRequestIds.contains(
                    requestId
            )) {
                continue;
            }

            shownRequestIds.add(requestId);

            showPairingDialog(request);

            break;
        }
    }

    private void showPairingDialog(
            JSONObject request
    ) {

        String requestId =
                request.optString(
                        "requestId",
                        ""
                );

        String deviceId =
                request.optString(
                        "deviceId",
                        ""
                );

        String controllerUid =
                request.optString(
                        "controllerUid",
                        ""
                );

        String message =
                "একটি Controller এই Phone B-এর সাথে "
                        + "pair করতে চাইছে।\n\n"
                        + "Controller UID:\n"
                        + controllerUid
                        + "\n\nDevice ID:\n"
                        + deviceId
                        + "\n\n"
                        + "তুমি অনুমতি দিলে Controller "
                        + "এই device-এর authorized access পাবে।";

        new AlertDialog.Builder(this)
                .setTitle(
                        "PAIRING REQUEST"
                )
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton(
                        "CANCEL",
                        (dialog, which) -> {

                            shownRequestIds.remove(
                                    requestId
                            );

                            dialog.dismiss();
                        }
                )
                .setPositiveButton(
                        "APPROVE",
                        (dialog, which) -> {

                            approvePairing(request);
                        }
                )
                .show();
    }

    private void approvePairing(
            JSONObject request
    ) {

        String token =
                preferences.getString(
                        KEY_TOKEN,
                        ""
                );

        String ownerUid =
                preferences.getString(
                        KEY_UID,
                        ""
                );

        String requestId =
                request.optString(
                        "requestId",
                        ""
                );

        String deviceId =
                request.optString(
                        "deviceId",
                        ""
                );

        String controllerUid =
                request.optString(
                        "controllerUid",
                        ""
                );

        String createdAt =
                request.optString(
                        "createdAt",
                        currentTime()
                );

        String approvedAt =
                currentTime();

        if (requestId.isEmpty()
                || deviceId.isEmpty()
                || controllerUid.isEmpty()
                || ownerUid.isEmpty()
                || token.isEmpty()) {

            showStatus(
                    "Pairing data incomplete."
            );

            return;
        }

        showStatus(
                "Pairing approve হচ্ছে..."
        );

        ApiClient.approvePairingRequest(
                token,
                requestId,
                deviceId,
                controllerUid,
                ownerUid,
                createdAt,
                approvedAt,
                new ApiClient.Callback() {

                    @Override
                    public void onSuccess(
                            JSONObject result
                    ) {

                        runOnUiThread(() -> {

                            showStatus(
                                    "✓ Pairing approved successfully."
                            );

                            Toast.makeText(
                                    MainActivity.this,
                                    "Controller authorized.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        runOnUiThread(() -> {

                            shownRequestIds.remove(
                                    requestId
                            );

                            showStatus(
                                    "Pairing approval failed:\n"
                                            + message
                            );
                        });
                    }
                }
        );
    }

    private String getLocalDeviceId() {

        String saved =
                preferences.getString(
                        KEY_DEVICE_ID,
                        ""
                );

        if (!saved.isEmpty()) {
            return saved;
        }

        String androidId =
                Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.ANDROID_ID
                );

        String deviceId;

        if (androidId != null
                && !androidId.isEmpty()) {

            deviceId =
                    "android_"
                            + androidId;

        } else {

            deviceId =
                    "device_"
                            + UUID.randomUUID()
                            .toString()
                            .replace(
                                    "-",
                                    ""
                            );
        }

        preferences
                .edit()
                .putString(
                        KEY_DEVICE_ID,
                        deviceId
                )
                .apply();

        return deviceId;
    }

    private void logout() {

        stopPairingChecker();

        preferences
                .edit()
                .clear()
                .apply();

        shownRequestIds.clear();

        showLoginScreen();
    }

    private boolean firebaseConfigured() {

        return !FirebaseConfig.API_KEY
                .contains("PASTE_YOUR")
                && !FirebaseConfig.PROJECT_ID
                .contains("PASTE_YOUR");
    }

    private String currentTime() {

        return new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.US
        ).format(
                new Date()
        );
    }


    private LinearLayout createRoot() {

        ScrollView scrollView =
                new ScrollView(this);

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                40,
                50,
                40,
                40
        );

        root.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        scrollView.addView(root);

        setContentView(scrollView);

        return root;
    }

    private TextView createTitle(
            String text
    ) {

        TextView view =
                createText(text);

        view.setTextSize(26);

        view.setGravity(
                Gravity.CENTER
        );

        return view;
    }

    private TextView createText(
            String text
    ) {

        TextView view =
                new TextView(this);

        view.setText(text);
        view.setTextSize(16);

        view.setPadding(
                0,
                15,
                0,
                15
        );

        return view;
    }

    private EditText createInput(
            String hint
    ) {

        EditText input =
                new EditText(this);

        input.setHint(hint);
        input.setSingleLine(true);

        input.setPadding(
                20,
                15,
                20,
                15
        );

        return input;
    }

    private void showStatus(
            String message
    ) {

        if (statusText != null) {

            statusText.setText(
                    message
            );
        }

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onDestroy() {

        stopPairingChecker();

        super.onDestroy();
    }
}
