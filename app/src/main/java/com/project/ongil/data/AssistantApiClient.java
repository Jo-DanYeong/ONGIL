package com.project.ongil.data;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Calls the server-side assistant router. Gemini keys and personal location never enter the APK. */
public final class AssistantApiClient implements AutoCloseable {
    public static final String OPEN_PICKUP_ZONES = "OPEN_PICKUP_ZONES";
    public static final String OPEN_SAFE_ROUTES = "OPEN_SAFE_ROUTES";
    public static final String REQUEST_LOCATION_SHARE = "REQUEST_LOCATION_SHARE";
    public static final String NONE = "NONE";

    public interface Callback {
        void onSuccess(Result result);
        void onError(Exception error);
    }

    public static final class Result {
        public final String reply;
        public final String action;
        public final boolean requiresConfirmation;

        public Result(String reply, String action, boolean requiresConfirmation) {
            this.reply = reply;
            this.action = action;
            this.requiresConfirmation = requiresConfirmation;
        }
    }

    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AssistantApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public void chat(String message, String role, String timeBucket, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(
                        baseUrl + "/api/assistant/chat").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(8_000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("message", message);
                body.put("role", role);
                body.put("timeBucket", timeBucket);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String response = readAll(stream);
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("Assistant API failed with HTTP " + status);
                }
                JSONObject json = new JSONObject(response);
                Result result = new Result(
                        json.optString("reply", "귀가 상황을 다시 알려줘."),
                        json.optString("action", NONE),
                        json.optBoolean("requiresConfirmation", false)
                );
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
