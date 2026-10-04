package com.example.remotedevicemanager;

import org.json.JSONArray;
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

                URL url =
                        new URL(urlString);

                connection =
                        (HttpURLConnection)
                                url.openConnection();

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

                connection.setRequestMethod("PATCH");

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

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

    /*
     * PHONE B
     *
     * শুধু নিজের deviceOwnerUid এবং pending status-এর
     * pairing request খোঁজা হবে।
     */
    public static void getPendingPairingRequests(
            String idToken,
            String deviceOwnerUid,
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
                                + "/documents:runQuery";

                URL url =
                        new URL(urlString);

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setRequestMethod("POST");

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + idToken
                );

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );

                connection.setDoOutput(true);

                JSONObject ownerValue =
                        new JSONObject();

                ownerValue.put(
                        "stringValue",
                        deviceOwnerUid
                );

                JSONObject statusValue =
                        new JSONObject();

                statusValue.put(
                        "stringValue",
                        "pending"
                );

                JSONObject ownerField =
                        new JSONObject();

                ownerField.put(
                        "fieldPath",
                        "deviceOwnerUid"
                );

                JSONObject statusField =
                        new JSONObject();

                statusField.put(
                        "fieldPath",
                        "status"
                );

                JSONObject ownerFilter =
                        new JSONObject();

                ownerFilter.put(
                        "fieldFilter",
                        new JSONObject()
                                .put(
                                        "field",
                                        ownerField
                                )
                                .put(
                                        "op",
                                        "EQUAL"
                                )
                                .put(
                                        "value",
                                        ownerValue
                                )
                );

                JSONObject statusFilter =
                        new JSONObject();

                statusFilter.put(
                        "fieldFilter",
                        new JSONObject()
                                .put(
                                        "field",
                                        statusField
                                )
                                .put(
                                        "op",
                                        "EQUAL"
                                )
                                .put(
                                        "value",
                                        statusValue
                                )
                );

                JSONArray filters =
                        new JSONArray();

                filters.put(ownerFilter);
                filters.put(statusFilter);

                JSONObject compositeFilter =
                        new JSONObject();

                compositeFilter.put(
                        "op",
                        "AND"
                );

                compositeFilter.put(
                        "filters",
                        filters
                );

                JSONObject where =
                        new JSONObject();

                where.put(
                        "compositeFilter",
                        compositeFilter
                );

                JSONObject structuredQuery =
                        new JSONObject();

                structuredQuery.put(
                        "from",
                        new JSONArray()
                                .put(
                                        new JSONObject()
                                                .put(
                                                        "collectionId",
                                                        "pairingRequests"
                                                )
                                )
                );

                structuredQuery.put(
                        "where",
                        where
                );

                JSONObject body =
                        new JSONObject();

                body.put(
                        "structuredQuery",
                        structuredQuery
                );

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

                if (responseCode < 200
                        || responseCode >= 300) {

                    JSONObject errorJson =
                            new JSONObject(response);

                    callback.onError(
                            firestoreError(errorJson)
                    );

                    return;
                }

                JSONArray results =
                        new JSONArray(response);

                JSONArray requests =
                        new JSONArray();

                for (int i = 0;
                     i < results.length();
                     i++) {

                    JSONObject item =
                            results.optJSONObject(i);

                    if (item == null) {
                        continue;
                    }

                    JSONObject document =
                            item.optJSONObject(
                                    "document"
                            );

                    if (document == null) {
                        continue;
                    }

                    JSONObject fields =
                            document.optJSONObject(
                                    "fields"
                            );

                    if (fields == null) {
                        continue;
                    }

                    String requestId =
                            lastPathPart(
                                    document.optString(
                                            "name",
                                            ""
                                    )
                            );

                    JSONObject request =
                            new JSONObject();

                    request.put(
                            "requestId",
                            requestId
                    );

                    request.put(
                            "deviceId",
                            fieldString(
                                    fields,
                                    "deviceId"
                            )
                    );

                    request.put(
                            "controllerUid",
                            fieldString(
                                    fields,
                                    "controllerUid"
                            )
                    );

                    request.put(
                            "deviceOwnerUid",
                            fieldString(
                                    fields,
                                    "deviceOwnerUid"
                            )
                    );

                    request.put(
                            "status",
                            fieldString(
                                    fields,
                                    "status"
                            )
                    );

                    request.put(
                            "createdAt",
                            fieldString(
                                    fields,
                                    "createdAt"
                            )
                    );

                    requests.put(request);
                }

                JSONObject result =
                        new JSONObject();

                result.put(
                        "requests",
                        requests
                );

                callback.onSuccess(result);

            } catch (Exception e) {

                callback.onError(
                        "Pairing check error: "
                                + e.getMessage()
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    /*
     * PHONE B owner pairing request approve করবে।
     */
    public static void approvePairingRequest(
            String idToken,
            String requestId,
            String deviceId,
            String controllerUid,
            String ownerUid,
            String createdAt,
            String approvedAt,
            Callback callback
    ) {

        new Thread(() -> {

            HttpURLConnection accessConnection = null;
            HttpURLConnection requestConnection = null;

            try {

                String accessId =
                        deviceId
                                + "_"
                                + controllerUid;

                String accessUrl =
                        FIRESTORE_BASE
                                + FirebaseConfig.PROJECT_ID
                                + "/databases/"
                                + FirebaseConfig.FIRESTORE_DATABASE
                                + "/documents/deviceAccess/"
                                + accessId;

                URL accessUrlObject =
                        new URL(accessUrl);

                accessConnection =
                        (HttpURLConnection)
                                accessUrlObject.openConnection();

                accessConnection.setRequestMethod(
                        "PATCH"
                );

                accessConnection.setConnectTimeout(
                        15000
                );

                accessConnection.setReadTimeout(
                        20000
                );

                accessConnection.setRequestProperty(
                        "Authorization",
                        "Bearer " + idToken
                );

                accessConnection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );

                accessConnection.setDoOutput(true);

                JSONObject accessFields =
                        new JSONObject();

                accessFields.put(
                        "deviceId",
                        stringField(deviceId)
                );

                accessFields.put(
                        "controllerUid",
                        stringField(controllerUid)
                );

                accessFields.put(
                        "ownerUid",
                        stringField(ownerUid)
                );

                accessFields.put(
                        "status",
                        stringField("active")
                );

                accessFields.put(
                        "createdAt",
                        stringField(createdAt)
                );

                JSONObject accessDocument =
                        new JSONObject();

                accessDocument.put(
                        "fields",
                        accessFields
                );

                writeBody(
                        accessConnection,
                        accessDocument.toString()
                );

                int accessResponseCode =
                        accessConnection.getResponseCode();
                
                int accessResponseCode =
                        accessConnection.getResponseCode();

                String accessResponse =
                        readResponse(
                                accessConnection,
                                accessResponseCode
                        );

                if (accessResponseCode < 200
                        || accessResponseCode >= 300) {

                    JSONObject accessJson =
                            new JSONObject(
                                    accessResponse
                            );

                    callback.onError(
                            firestoreError(accessJson)
                    );

                    return;
                }

                String requestUrl =
                        FIRESTORE_BASE
                                + FirebaseConfig.PROJECT_ID
                                + "/databases/"
                                + FirebaseConfig.FIRESTORE_DATABASE
                                + "/documents/pairingRequests/"
                                + requestId;

                URL requestUrlObject =
                        new URL(requestUrl);

                requestConnection =
                        (HttpURLConnection)
                                requestUrlObject.openConnection();

                requestConnection.setRequestMethod(
                        "PATCH"
                );

                requestConnection.setConnectTimeout(
                        15000
                );

                requestConnection.setReadTimeout(
                        20000
                );

                requestConnection.setRequestProperty(
                        "Authorization",
                        "Bearer " + idToken
                );

                requestConnection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                );

                requestConnection.setDoOutput(true);

                JSONObject requestFields =
                        new JSONObject();

                requestFields.put(
                        "deviceId",
                        stringField(deviceId)
                );

                requestFields.put(
                        "controllerUid",
                        stringField(controllerUid)
                );

                requestFields.put(
                        "deviceOwnerUid",
                        stringField(ownerUid)
                );

                requestFields.put(
                        "status",
                        stringField("approved")
                );

                requestFields.put(
                        "createdAt",
                        stringField(createdAt)
                );

                requestFields.put(
                        "approvedAt",
                        stringField(approvedAt)
                );

                JSONObject requestDocument =
                        new JSONObject();

                requestDocument.put(
                        "fields",
                        requestFields
                );

                writeBody(
                        requestConnection,
                        requestDocument.toString()
                );

                int requestResponseCode =
                        requestConnection.getResponseCode();

                String requestResponse =
                        readResponse(
                                requestConnection,
                                requestResponseCode
                        );

                JSONObject requestJson =
                        new JSONObject(
                                requestResponse
                        );

                if (requestResponseCode >= 200
                        && requestResponseCode < 300) {

                    callback.onSuccess(
                            requestJson
                    );

                } else {

                    callback.onError(
                            firestoreError(requestJson)
                    );
                }

            } catch (Exception e) {

                callback.onError(
                        "Pairing approval error: "
                                + e.getMessage()
                );

            } finally {

                if (accessConnection != null) {
                    accessConnection.disconnect();
                }

                if (requestConnection != null) {
                    requestConnection.disconnect();
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

    private static String fieldString(
            JSONObject fields,
            String name
    ) {

        JSONObject field =
                fields.optJSONObject(name);

        if (field == null) {
            return "";
        }

        return field.optString(
                "stringValue",
                ""
        );
    }

    private static String lastPathPart(
            String path
    ) {

        if (path == null
                || path.isEmpty()) {

            return "";
        }

        int index =
                path.lastIndexOf('/');

        if (index < 0
                || index == path.length() - 1) {

            return path;
        }

        return path.substring(
                index + 1
        );
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
