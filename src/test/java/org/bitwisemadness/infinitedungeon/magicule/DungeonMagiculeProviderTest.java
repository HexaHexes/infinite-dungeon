package org.bitwisemadness.infinitedungeon.magicule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.bitwisemadness.infinitedungeon.depth.DungeonLayout;
import org.bitwisemadness.infinitedungeon.depth.DungeonPosition;
import org.bitwisemadness.infinitedungeon.depth.LayoutParameters;
import org.bitwisemadness.infinitedungeon.depth.MagiculeCurve;
import org.junit.jupiter.api.Test;

/**
 * The provider's pure core (plan section 7; issue #201): the composition is
 * depth derivation followed by the M curve - both individually tested, pinned
 * here as one function, plus the decline cases that make the service fall
 * through to COLD.
 */
public class DungeonMagiculeProviderTest {

    /** The same test world DungeonLayoutTest pins the derivation against. */
    private static final LayoutParameters PARAMS =
            new LayoutParameters(1234L, 512, -64, 2048, 5, 10);

    private static final double EPS = 1e-9;

    @Test
    void westOfLaneZeroHasNoAnswer() {
        assertTrue(DungeonMagiculeProvider.concentrationAt(PARAMS, -1, 0, 0.1, 0.2, 0.04).isEmpty(),
                "positions outside the lanes are not this provider's to answer for");
        assertTrue(DungeonMagiculeProvider.concentrationAt(PARAMS, -512, 400, 0.1, 0.2, 0.04).isEmpty());
    }

    @Test
    void lobbyReportsTheOriginValueAtAnyHeight() {
        // Layer 0 is fixed construction: floor 0 for any Y (the DungeonLayout
        // contract), so the whole lobby answers exactly the curve's base.
        OptionalDouble low = DungeonMagiculeProvider.concentrationAt(PARAMS, 0, PARAMS.minY(), 2.5, 9, 9);
        OptionalDouble high = DungeonMagiculeProvider.concentrationAt(PARAMS, 400, 1900, 2.5, 9, 9);
        assertTrue(low.isPresent() && high.isPresent());
        assertEquals(2.5, low.getAsDouble(), EPS);
        assertEquals(2.5, high.getAsDouble(), EPS);
    }

    @Test
    void composesDerivationWithTheCurve() {
        // The whole provider contract in one pin: derive the depth the way the
        // depth API does, evaluate M with the same coefficients - the provider
        // must agree exactly.
        int[][] probes = { { 600, 400 }, { 600, -64 }, { 900, 1983 }, { 512, 0 } };
        for (int[] probe : probes) {
            int x = probe[0];
            int y = probe[1];
            DungeonPosition derived = DungeonLayout.derive(PARAMS, x, y);
            OptionalDouble got = DungeonMagiculeProvider.concentrationAt(PARAMS, x, y, 0.1, 0.2, 0.04);
            assertTrue(got.isPresent(), "in-lane position must answer: (" + x + "," + y + ")");
            assertEquals(MagiculeCurve.concentration(derived.layerIndex(), derived.floorInLayer(),
                    0.1, 0.2, 0.04), got.getAsDouble(), EPS);
        }
        // The provider reads real depth rather than a constant: opposite ends
        // of lane 1 (deepest floor vs entry band) must differ, deeper = more.
        double deepest = DungeonMagiculeProvider.concentrationAt(
                PARAMS, 600, PARAMS.minY(), 0.1, 0.2, 0.04).getAsDouble();
        double entryBand = DungeonMagiculeProvider.concentrationAt(
                PARAMS, 600, PARAMS.minY() + PARAMS.availableHeight() - 1, 0.1, 0.2, 0.04)
                .getAsDouble();
        assertTrue(deepest > entryBand,
                "the deepest floor must read more than the entry band: " + deepest + " vs " + entryBand);
    }
}
