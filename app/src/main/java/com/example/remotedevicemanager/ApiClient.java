package com.example.remotedevicemanager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ApiClient {

    public interface Callback {
        void onSuccess(JSONObject result);
        void onError(String message);
    }

    private static final String AUTH_BASE =
            "https://identitytoolkit.googleapis.com/v1/";

    private static final String FIRESTORE_BASE =
            "https://firestore.googleapis.com/v1/projects/";

    public static void signIn(
            String email,
            String password,
            Callback callback
    ) {
        authRequest(
                "accounts:signInWithPassword",
                email,
                password,
                callback
        );
    }

    public static void signUp(
            String email,
            String password,
            Callback callback
    ) {
        authRequest(
                "accounts:signUp",
                email,
                password,
                callback
        );
    }

    private static void authRequest(
            String endpoint,
            String email,
            String password,
            Callback callback
    ) {

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                String urlString =
                        AUTH_BASE
                                + endpoint
                                + "?key="
                                + FirebaseConfig.API_KEY;

                URL url = new URL(urlString);

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );

                connection.setDoOutput(true);

                JSONObject body =
                        new JSONObject();

                body.put("email", email);
                body.put("password", password);
                body.put("returnSecureToken", true);

                writeBody(
                        connection,
                        body.toString()
                );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        readResponse(
                                connection,
                                responseCode
                        );

                JSONObject json =
                        new JSONObject(response);

                if (responseCode >= 200
                        && responseCode < 300) {

                    callback.onSuccess(json);

                } else {

                    callback.onError(
                            firebaseError(json)
                    );
                }

            } catch (Exception e) {

                callback.onError(
                        "Network error: "
                                + e.getMessage()
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    public static void createOrUpdateDevice(
            String idToken,
            String deviceId,
            String ownerUid,
            String deviceName,
            String platform,
            String status,
            String createdAt,
            String lastSeen,
            Callback callback
    ) {

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                String urlString =
                        FIRESTORE_BASE
                                + FirebaseConfig.PROJECT_ID
                                + "/databases/"
                                + FirebaseConfig.FIRESTORE_DATABASE
                                + "/documents/devices/"
                                + deviceId;

                URL url =
                        new URL(urlString);

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setRequestMethod(
                        "PATCH"
                );

                connection.setConnectTimeout(
                        15000
                );

                connection.setReadTimeout(
                        20000
                );

                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + idToken
                );

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );

                connection.setDoOutput(true);

                JSONObject fields =
                        new JSONObject();

                fields.put(
                        "ownerUid",
                        stringField(ownerUid)
                );

                fields.put(
                        "deviceName",
                        stringField(deviceName)
                );

                fields.put(
                        "platform",
                        stringField(platform)
                );

                fields.put(
                        "status",
                        stringField(status)
                );

                fields.put(
                        "createdAt",
                        stringField(createdAt)
                );

                fields.put(
                        "lastSeen",
                        stringField(lastSeen)
                );

                JSONObject document =
                        new JSONObject();

                document.put(
                        "fields",
                        fields
                );

                writeBody(
                        connection,
                        document.toString()
                );

                int responseCode =
                        connection.getResponseCode();

                String response =
                        readResponse(
                                connection,
                                responseCode
                        );

                JSONObject json =
                        new JSONObject(response);

                if (responseCode >= 200
                        && responseCode < 300) {

                    callback.onSuccess(json);

                } else {

                    callback.onError(
                            firestoreError(json)
                    );
                }

            } catch (Exception e) {

                callback.onError(
                        "Firestore error: "
                                + e.getMessage()
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    private static JSONObject stringField(
            String value
    ) throws Exception {

        JSONObject field =
                new JSONObject();

        field.put(
                "stringValue",
                value == null ? "" : value
        );

        return field;
    }

    private static void writeBody(
            HttpURLConnection connection,
            String body
    ) throws Exception {

        byte[] data =
                body.getBytes(
                        StandardCharsets.UTF_8
                );

        try (OutputStream output =
                     connection.getOutputStream()) {

            output.write(data);
            output.flush();
        }
    }

    private static String readResponse(
            HttpURLConnection connection,
            int responseCode
    ) throws Exception {

        InputStream input;

        if (responseCode >= 200
                && responseCode < 400) {

            input =
                    connection.getInputStream();

        } else {

            input =
                    connection.getErrorStream();
        }

        if (input == null) {
            return "{}";
        }

        StringBuilder result =
                new StringBuilder();

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     input,
                                     StandardCharsets.UTF_8
                             )
                     )) {

            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }

    private static String firebaseError(
            JSONObject json
    ) {

        try {

            JSONObject error =
                    json.getJSONObject("error");

            String message =
                    error.optString(
                            "message",
                            "Authentication failed"
                    );

            switch (message) {

                case "EMAIL_EXISTS":
                    return "এই email আগে থেকেই আছে। Login করো।";

                case "EMAIL_NOT_FOUND":
                    return "এই email-এর account পাওয়া যায়নি।";

                case "INVALID_PASSWORD":
                    return "Password ভুল।";

                case "INVALID_EMAIL":
                    return "Email ঠিক নয়।";

                case "USER_DISABLED":
                    return "এই account disabled।";

                case "OPERATION_NOT_ALLOWED":
                    return "Firebase Email/Password Login চালু নেই।";

                default:
                    return message;
            }

        } catch (Exception e) {

            return "Authentication failed";
        }
    }

    private static String firestoreError(
            JSONObject json
    ) {

        try {

            JSONObject error =
                    json.getJSONObject("error");

            return error.optString(
                    "message",
                    "Firestore request failed"
            );

        } catch (Exception e) {

            return "Firestore request failed";
        }
    }
    }
