package org.bitwisemadness.infinitedungeon.depth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bitwisemadness.infinitedungeon.lanes.FloorSchedule;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;
import org.junit.jupiter.api.Test;

/** The depth derivation: lobby at zero, no gaps, no invented layers. */
class DungeonLayoutTest {

    private static final LayoutParameters PARAMS =
            new LayoutParameters(1234L, 512, -64, 2048, 5, 10);

    @Test
    void lobbyIsAlwaysDepthZeroFloorZero() {
        for (int y = -64; y <= 1984; y += 97) {
            assertEquals(new DungeonPosition(0, 0, 0), DungeonLayout.derive(PARAMS, 0, y));
            assertEquals(new DungeonPosition(0, 0, 0), DungeonLayout.derive(PARAMS, 511, y));
        }
    }

    @Test
    void depthHasNoGapsAcrossLayerBoundaries() {
        for (int layer = 1; layer <= 20; layer++) {
            int floors = FloorSchedule.floorsInLayer(PARAMS.worldSeed(), layer,
                    PARAMS.floorsMin(), PARAMS.floorsMax());
            DungeonPosition last = DungeonLayout.derive(PARAMS,
                    LaneGeometry.laneOriginX(layer, PARAMS.laneStride()),
                    PARAMS.minY() + PARAMS.availableHeight() - 1);
            DungeonPosition first = DungeonLayout.derive(PARAMS,
                    LaneGeometry.laneOriginX(layer + 1, PARAMS.laneStride()),
                    PARAMS.minY());
            assertEquals(floors - 1, last.floorInLayer(), "layer " + layer + " top floor");
            assertEquals(0, first.floorInLayer(), "layer " + (layer + 1) + " bottom floor");
            assertEquals(last.depth() + 1, first.depth(),
                    "depth must continue without gaps at the layer " + layer + " boundary");
        }
    }

    @Test
    void floorsBeforeSumsTheDeeperLayers() {
        int expected = 0;
        for (int layer = 1; layer <= 30; layer++) {
            assertEquals(expected, DungeonLayout.floorsBefore(PARAMS, layer));
            expected += FloorSchedule.floorsInLayer(PARAMS.worldSeed(), layer,
                    PARAMS.floorsMin(), PARAMS.floorsMax());
        }
        assertEquals(0, DungeonLayout.floorsBefore(PARAMS, 0));
        assertEquals(0, DungeonLayout.floorsBefore(PARAMS, -5));
    }

    @Test
    void depthIsMonotoneWithTheLayer() {
        int previous = -1;
        for (int layer = 0; layer <= 30; layer++) {
            DungeonPosition position = DungeonLayout.derive(PARAMS,
                    LaneGeometry.laneOriginX(layer, PARAMS.laneStride()), PARAMS.minY());
            assertTrue(position.depth() > previous,
                    "depth must strictly increase with the layer: " + position);
            previous = position.depth();
        }
    }

    @Test
    void everyFloorOfALayerIsReachable() {
        int layer = 7;
        int floors = FloorSchedule.floorsInLayer(PARAMS.worldSeed(), layer,
                PARAMS.floorsMin(), PARAMS.floorsMax());
        int stride = PARAMS.availableHeight() / floors;
        int seen = 0;
        for (int floor = 0; floor < floors; floor++) {
            DungeonPosition position = DungeonLayout.derive(PARAMS,
                    LaneGeometry.laneOriginX(layer, PARAMS.laneStride()),
                    PARAMS.minY() + floor * stride);
            assertEquals(floor, position.floorInLayer());
            seen++;
        }
        assertEquals(floors, seen);
    }

    @Test
    void westOfTheFirstLaneIsRefused() {
        assertFalse(DungeonLayout.inDungeon(PARAMS, -1));
        assertTrue(DungeonLayout.inDungeon(PARAMS, 0));
        assertThrows(IllegalArgumentException.class, () -> DungeonLayout.derive(PARAMS, -1, 0));
    }

    @Test
    void parameterValidationIsLoud() {
        assertThrows(IllegalArgumentException.class,
                () -> new LayoutParameters(1L, 0, -64, 2048, 5, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new LayoutParameters(1L, 512, -64, 0, 5, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new LayoutParameters(1L, 512, -64, 2048, 0, 10));
    }
}
