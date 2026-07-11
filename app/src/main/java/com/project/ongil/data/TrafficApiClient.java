package com.project.ongil.data;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Calls the ON:GIL backend instead of exposing the Seoul Open Data API key in the APK.
 * A production baseUrl must use HTTPS.
 */
public final class TrafficApiClient implements AutoCloseable {
    public interface Callback {
        void onSuccess(Assessment assessment);
        void onError(Exception error);
    }

    public interface PickupCallback {
        void onSuccess(PickupRecommendation recommendation);
        void onError(Exception error);
    }

    public static final class Assessment {
        public final double averageSpeedKph;
        public final double trafficRisk;
        public final String pickupZoneRecommendation;
        public final List<String> reasons;
        public final boolean usesLiveData;

        private Assessment(double averageSpeedKph, double trafficRisk,
                           String pickupZoneRecommendation, List<String> reasons,
                           boolean usesLiveData) {
            this.averageSpeedKph = averageSpeedKph;
            this.trafficRisk = trafficRisk;
            this.pickupZoneRecommendation = pickupZoneRecommendation;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
            this.usesLiveData = usesLiveData;
        }
    }

    public static final class PickupRecommendation {
        public final String name;
        public final int distanceMeters;
        public final int waitingVehicles;
        public final int capacity;
        public final int totalScore;
        public final int safetyScore;
        public final int stoppingScore;
        public final String trafficLevel;
        public final double trafficRisk;
        public final List<String> reasons;
        public final boolean usesLiveData;

        private PickupRecommendation(String name, int distanceMeters, int waitingVehicles,
                                     int capacity, int totalScore, int safetyScore,
                                     int stoppingScore, String trafficLevel,
                                     double trafficRisk, List<String> reasons,
                                     boolean usesLiveData) {
            this.name = name;
            this.distanceMeters = distanceMeters;
            this.waitingVehicles = waitingVehicles;
            this.capacity = capacity;
            this.totalScore = totalScore;
            this.safetyScore = safetyScore;
            this.stoppingScore = stoppingScore;
            this.trafficLevel = trafficLevel;
            this.trafficRisk = trafficRisk;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
            this.usesLiveData = usesLiveData;
        }
    }

    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TrafficApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public void assessLinks(List<String> linkIds, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(baseUrl + "/traffic/assess-links");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(7_000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("linkIds", new JSONArray(linkIds));
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String response = readAll(stream);
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("Traffic API failed with HTTP " + status);
                }
                Assessment assessment = parseAssessment(new JSONObject(response));
                mainHandler.post(() -> callback.onSuccess(assessment));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    /**
     * Sends three hackathon pickup candidates. Distance/safety/check-in fields are demo data;
     * each candidate's road traffic and incidents are fetched live by the backend using linkIds.
     */
    public void recommendPickupZone(List<String> linkIds, PickupCallback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                if (linkIds.size() < 2) {
                    throw new IllegalArgumentException("At least two pickup-zone LINK_IDs are required");
                }
                URL url = new URL(baseUrl + "/pickup-zones/recommend");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(10_000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                int count = Math.min(3, linkIds.size());
                int[] distances = {180, 320, 450};
                double[] lighting = {0.74, 0.88, 0.80};
                int[] safetySpots = {2, 3, 2};
                double[] stopping = {0.55, 0.90, 0.72};
                int[] waiting = {7, 2, 5};
                JSONArray candidates = new JSONArray();
                for (int index = 0; index < count; index++) {
                    String id = String.valueOf((char) ('A' + index));
                    JSONObject candidate = new JSONObject();
                    candidate.put("id", id);
                    candidate.put("name", id + " 픽업존");
                    candidate.put("linkId", linkIds.get(index));
                    candidate.put("distanceMeters", distances[index]);
                    candidate.put("lightingCoverage", lighting[index]);
                    candidate.put("safetySpotCount", safetySpots[index]);
                    candidate.put("hasSidewalk", true);
                    candidate.put("hasCrosswalk", index != 2);
                    candidate.put("legalStopAllowed", true);
                    candidate.put("stoppingSuitability", stopping[index]);
                    candidate.put("waitingVehicles", waiting[index]);
                    candidate.put("capacity", 10);
                    candidates.put(candidate);
                }
                JSONObject body = new JSONObject();
                body.put("maxWalkingMeters", 800);
                body.put("candidates", candidates);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String response = readAll(stream);
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("Pickup API failed with HTTP " + status);
                }
                JSONObject root = new JSONObject(response);
                PickupRecommendation recommendation = parsePickupRecommendation(
                        root.getJSONObject("recommended"), root.optBoolean("usesLiveData", false));
                mainHandler.post(() -> callback.onSuccess(recommendation));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private Assessment parseAssessment(JSONObject json) {
        JSONArray reasonArray = json.optJSONArray("reasons");
        List<String> reasons = new ArrayList<>();
        if (reasonArray != null) {
            for (int index = 0; index < reasonArray.length(); index++) {
                reasons.add(reasonArray.optString(index));
            }
        }
        return new Assessment(
                json.optDouble("averageSpeedKph", 0),
                json.optDouble("trafficRisk", 0),
                json.optString("pickupZoneRecommendation", "정보 없음"),
                reasons,
                json.optBoolean("usesLiveData", false)
        );
    }

    private PickupRecommendation parsePickupRecommendation(JSONObject json, boolean usesLiveData) {
        JSONArray reasonArray = json.optJSONArray("reasons");
        List<String> reasons = new ArrayList<>();
        if (reasonArray != null) {
            for (int index = 0; index < reasonArray.length(); index++) {
                reasons.add(reasonArray.optString(index));
            }
        }
        return new PickupRecommendation(
                json.optString("name", "추천 픽업존"),
                json.optInt("distanceMeters", 0),
                json.optInt("waitingVehicles", 0),
                json.optInt("capacity", 0),
                json.optInt("totalScore", 0),
                json.optInt("safetyScore", 0),
                json.optInt("stoppingScore", 0),
                json.optString("trafficLevel", "정보 없음"),
                json.optDouble("trafficRisk", 0),
                reasons,
                usesLiveData
        );
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
