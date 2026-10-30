package org.bitwisemadness.infinitedungeon.lanes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The seed-derived floor rule: deterministic, range-bound, lobby-exempt. */
class FloorScheduleTest {

    @Test
    void sameInputsAlwaysSameCount() {
        for (int layer = 1; layer <= 50; layer++) {
            int first = FloorSchedule.floorsInLayer(123456789L, layer, 5, 10);
            for (int repeat = 0; repeat < 5; repeat++) {
                assertEquals(first, FloorSchedule.floorsInLayer(123456789L, layer, 5, 10),
                        "layer " + layer + " must be stable across calls");
            }
        }
    }

    @Test
    void countsStayInsideTheConfiguredRange() {
        for (long seed : new long[] {0L, 1L, -1L, 42L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (int layer = 1; layer <= 2000; layer++) {
                int floors = FloorSchedule.floorsInLayer(seed, layer, 5, 10);
                assertTrue(floors >= 5 && floors <= 10,
                        "seed " + seed + " layer " + layer + " out of range: " + floors);
            }
        }
    }

    @Test
    void everyCountInARangeOccurs() {
        Set<Integer> seen = new HashSet<>();
        for (int layer = 1; layer <= 500; layer++) {
            seen.add(FloorSchedule.floorsInLayer(987654321L, layer, 5, 10));
        }
        assertEquals(Set.of(5, 6, 7, 8, 9, 10), seen,
                "the default range must be fully reachable across layers");
    }

    @Test
    void seedsSpread() {
        Map<Integer, Integer> perCount = new HashMap<>();
        for (long seed = 0; seed < 600; seed++) {
            perCount.merge(FloorSchedule.floorsInLayer(seed, 1, 5, 10), 1, Integer::sum);
        }
        assertTrue(perCount.size() >= 5,
                "different seeds must field different counts at the same layer: " + perCount);
    }

    @Test
    void minEqualsMaxIsConstant() {
        for (int layer = 1; layer <= 100; layer++) {
            assertEquals(7, FloorSchedule.floorsInLayer(1L, layer, 7, 7));
        }
    }

    @Test
    void invertedRangeNormalizesInsteadOfCrashing() {
        assertEquals(5, FloorSchedule.floorsInLayer(42L, 3, 10, 5));
        assertTrue(FloorSchedule.floorsInLayer(42L, 3, 10, 5)
                == FloorSchedule.floorsInLayer(42L, 3, 5, 10));
    }

    @Test
    void lobbyAndNegativeLayersAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> FloorSchedule.floorsInLayer(1L, 0, 5, 10));
        assertThrows(IllegalArgumentException.class, () -> FloorSchedule.floorsInLayer(1L, -1, 5, 10));
    }

    @Test
    void zeroFloorsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> FloorSchedule.floorsInLayer(1L, 1, 0, 10));
    }

    @Test
    void bandAlwaysFitsTheDimension() {
        int available = 2048;
        for (int floors = 1; floors <= 10; floors++) {
            int height = FloorSchedule.floorHeight(available, floors);
            assertTrue(height >= 1, "each floor must be at least a block tall");
            assertTrue(floors * height <= available, "the band must fit the dimension");
        }
        assertThrows(IllegalArgumentException.class, () -> FloorSchedule.floorHeight(3, 4));
        assertThrows(IllegalArgumentException.class, () -> FloorSchedule.floorHeight(2048, 0));
    }

    @Test
    void floorIndexClampsToTheBand() {
        // 2048 over 8 floors: 256-block bands, floor 0 at the TOP of the
        // band and the index growing downward (issue #183).
        assertEquals(7, FloorSchedule.floorIndexInLayer(-64, -64, 2048, 8));
        assertEquals(7, FloorSchedule.floorIndexInLayer(191, -64, 2048, 8));
        assertEquals(6, FloorSchedule.floorIndexInLayer(192, -64, 2048, 8));
        assertEquals(0, FloorSchedule.floorIndexInLayer(1983, -64, 2048, 8));
        // Outside the band clamps in: below is deeper, above is shallower.
        assertEquals(7, FloorSchedule.floorIndexInLayer(-100000, -64, 2048, 8));
        assertEquals(0, FloorSchedule.floorIndexInLayer(100000, -64, 2048, 8));
    }

    @Test
    void bandBasesForTransitions() {
        // The deepest floor stands on the world floor plane...
        assertEquals(-64, FloorSchedule.deepestFloorBaseY(-64));
        // ...and the entry floor on the top band: 8 floors over 2048 means
        // 256-block bands, so floor 0 starts 7 bands above the bottom.
        assertEquals(-64 + 7 * 256, FloorSchedule.entryFloorBaseY(-64, 2048, 8));
        assertEquals(-64 + 1 * 1024, FloorSchedule.entryFloorBaseY(-64, 2048, 2));
        // A single-floor layer shares the world floor.
        assertEquals(-64, FloorSchedule.entryFloorBaseY(-64, 2048, 1));
        // The height guard still applies through the helper.
        assertThrows(IllegalArgumentException.class,
                () -> FloorSchedule.entryFloorBaseY(-64, 2048, 0));
    }
}
