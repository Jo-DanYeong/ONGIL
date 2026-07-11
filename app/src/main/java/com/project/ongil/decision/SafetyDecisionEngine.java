package com.project.ongil.decision;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps ON:GIL's product decision rules independent from Android views.
 * Check-in counts are simulated for the hackathon and can later be replaced by server data.
 */
public final class SafetyDecisionEngine {
    public enum ReturnMode { PICKUP, SOLO }

    public static final class PickupZone {
        public final String name;
        public final int waitingCars;
        public final int studentWalkMinutes;
        public final double roadTrafficRisk;
        public final int score;

        private PickupZone(String name, int waitingCars, int studentWalkMinutes,
                           double roadTrafficRisk) {
            this.name = name;
            this.waitingCars = waitingCars;
            this.studentWalkMinutes = studentWalkMinutes;
            this.roadTrafficRisk = roadTrafficRisk;
            this.score = (int) Math.round(
                    Math.min(1.0, waitingCars / 10.0) * 35
                            + roadTrafficRisk * 50
                            + Math.min(1.0, studentWalkMinutes / 10.0) * 15
            );
        }

        public String trafficLabel() {
            if (roadTrafficRisk < 0.35) return "원활";
            if (roadTrafficRisk < 0.65) return "서행";
            return "혼잡";
        }
    }

    public static final class SafeRoute {
        public final String name;
        public final int minutes;
        public final int safetyScore;
        public final int lightingPercent;
        public final int safetySpotCount;
        public final int darkAlleyCount;

        private SafeRoute(String name, int minutes, double lightingCoverage,
                          int safetySpotCount, int darkAlleyCount,
                          double timeRisk, double trafficRisk) {
            this.name = name;
            this.minutes = minutes;
            this.lightingPercent = (int) Math.round(lightingCoverage * 100);
            this.safetySpotCount = safetySpotCount;
            this.darkAlleyCount = darkAlleyCount;

            double risk = (1.0 - lightingCoverage) * 35
                    + Math.max(0, 4 - safetySpotCount) * 8
                    + darkAlleyCount * 12
                    + timeRisk * 20
                    + trafficRisk * 15;
            this.safetyScore = 100 - (int) Math.round(Math.min(100, risk));
        }
    }

    private SafetyDecisionEngine() {}

    public static PickupZone recommendPickupZone(double liveTrafficRisk) {
        double trafficAdjustment = Math.max(0, Math.min(1, liveTrafficRisk)) * 0.20;
        List<PickupZone> zones = Arrays.asList(
                new PickupZone("A 픽업존", 7, 2, Math.min(1, 0.46 + trafficAdjustment)),
                new PickupZone("B 픽업존", 2, 4, Math.min(1, 0.14 + trafficAdjustment)),
                new PickupZone("C 픽업존", 5, 6, Math.min(1, 0.31 + trafficAdjustment))
        );
        return Collections.min(zones, Comparator.comparingInt(zone -> zone.score));
    }

    public static SafeRoute safeRoute(double trafficRisk) {
        return new SafeRoute("플랜 A · 야간 안전 우선", 15, 0.84,
                4, 0, 0.25, trafficRisk);
    }

    public static SafeRoute fastRoute(double trafficRisk) {
        return new SafeRoute("플랜 B · 빠른 길", 10, 0.48,
                1, 2, 0.50, trafficRisk);
    }
}
