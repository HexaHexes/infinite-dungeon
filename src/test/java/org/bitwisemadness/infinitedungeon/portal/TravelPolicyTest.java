package org.bitwisemadness.infinitedungeon.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The portal walk's pure decision: bind and enter, return, or stay put. */
class TravelPolicyTest {

    @Test
    void outsideTheDungeonBindsAndEnters() {
        assertEquals(TravelPolicy.Action.BIND_AND_ENTER, TravelPolicy.decide(false, false));
        assertEquals(TravelPolicy.Action.BIND_AND_ENTER, TravelPolicy.decide(false, true));
    }

    @Test
    void insideWithAnAnchorReturnsToIt() {
        assertEquals(TravelPolicy.Action.RETURN_TO_ANCHOR, TravelPolicy.decide(true, true));
    }

    @Test
    void insideWithoutAnAnchorStaysPut() {
        // The missing-anchor fallback belongs to its own issue, not this one.
        assertEquals(TravelPolicy.Action.STAY_PUT, TravelPolicy.decide(true, false));
    }
}
