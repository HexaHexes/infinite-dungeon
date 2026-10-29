package org.bitwisemadness.infinitedungeon.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bitwisemadness.infinitedungeonapi.profile.PortalReturnMode;
import org.junit.jupiter.api.Test;

/** The exit policy of section 4: return, reform, or fall back - all pure. */
class TravelPolicyTest {

    @Test
    void outsideTheDungeonBindsAndEnters() {
        assertEquals(TravelPolicy.Action.BIND_AND_ENTER,
                TravelPolicy.decide(false, false, PortalReturnMode.ENTRY_PORTAL));
        assertEquals(TravelPolicy.Action.BIND_AND_ENTER,
                TravelPolicy.decide(false, true, PortalReturnMode.WORLD_SPAWN));
    }

    @Test
    void insideReturnsToYourOwnPortalByDefault() {
        assertEquals(TravelPolicy.Action.RETURN_TO_ANCHOR,
                TravelPolicy.decide(true, true, PortalReturnMode.ENTRY_PORTAL));
    }

    @Test
    void configuredWorldSpawnSkipsTheAnchorWithoutAMessage() {
        TravelPolicy.Action action = TravelPolicy.decide(true, true, PortalReturnMode.WORLD_SPAWN);
        assertEquals(TravelPolicy.Action.WORLD_SPAWN_BY_CONFIG, action);
        assertFalse(TravelPolicy.announcesFallback(action),
                "the configured mode is not a destroyed-anchor message");
    }

    @Test
    void noAnchorFallsBackToWorldSpawnWithAMessage() {
        // Checked first, so it applies whatever the return mode says.
        TravelPolicy.Action action = TravelPolicy.decide(true, false, PortalReturnMode.WORLD_SPAWN);
        assertEquals(TravelPolicy.Action.WORLD_SPAWN_NO_ANCHOR, action);
        assertTrue(TravelPolicy.announcesFallback(action));
    }

    @Test
    void reformSuccessReturnsAndFailureFallsBack() {
        assertEquals(TravelPolicy.Action.RETURN_TO_ANCHOR,
                TravelPolicy.afterReformAttempt(true, true));
        assertEquals(TravelPolicy.Action.WORLD_SPAWN_DESTROYED,
                TravelPolicy.afterReformAttempt(true, false));
        assertEquals(TravelPolicy.Action.WORLD_SPAWN_DESTROYED,
                TravelPolicy.afterReformAttempt(false, true));
        assertTrue(TravelPolicy.announcesFallback(TravelPolicy.Action.WORLD_SPAWN_DESTROYED));
    }

    @Test
    void onlyTheAnchorFallbacksAnnounce() {
        assertTrue(TravelPolicy.announcesFallback(TravelPolicy.Action.WORLD_SPAWN_NO_ANCHOR));
        assertTrue(TravelPolicy.announcesFallback(TravelPolicy.Action.WORLD_SPAWN_DESTROYED));
        assertFalse(TravelPolicy.announcesFallback(TravelPolicy.Action.BIND_AND_ENTER));
        assertFalse(TravelPolicy.announcesFallback(TravelPolicy.Action.RETURN_TO_ANCHOR));
        assertFalse(TravelPolicy.announcesFallback(TravelPolicy.Action.WORLD_SPAWN_BY_CONFIG));
    }
}
