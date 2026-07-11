package com.project.ongil.decision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SafetyDecisionEngineTest {
    @Test
    public void recommendsLeastCongestedPickupZone() {
        SafetyDecisionEngine.PickupZone zone =
                SafetyDecisionEngine.recommendPickupZone(0.2);

        assertEquals("B 픽업존", zone.name);
        assertEquals(2, zone.waitingCars);
    }

    @Test
    public void safeRouteScoresAboveFastDarkRoute() {
        SafetyDecisionEngine.SafeRoute safe = SafetyDecisionEngine.safeRoute(0.3);
        SafetyDecisionEngine.SafeRoute fast = SafetyDecisionEngine.fastRoute(0.3);

        assertTrue(safe.safetyScore > fast.safetyScore);
        assertTrue(safe.lightingPercent > fast.lightingPercent);
        assertTrue(safe.safetySpotCount > fast.safetySpotCount);
    }
}
