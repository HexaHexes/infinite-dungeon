package org.bitwisemadness.infinitedungeon.portal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The ignition gate: the admin toggle and the dimension filter must both allow. */
class ConstructionPolicyTest {

    @Test
    void bothAllowingMeansIgnitionProceeds() {
        assertTrue(ConstructionPolicy.mayIgnite(true, true));
    }

    @Test
    void theToggleRefusesEvenInAnAllowedDimension() {
        // portal.allowConstruction off: entrances are admin-placed fixtures.
        assertFalse(ConstructionPolicy.mayIgnite(false, true));
    }

    @Test
    void aDisallowedDimensionRefusesEvenWithTheToggleOn() {
        assertFalse(ConstructionPolicy.mayIgnite(true, false));
        assertFalse(ConstructionPolicy.mayIgnite(false, false));
    }
}
