package org.bitwisemadness.infinitedungeon.depth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bitwisemadness.infinitedungeon.lanes.FloorSchedule;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;
import org.junit.jupiter.api.Test;

/** The depth derivation: the lobby is Overworld-resident, no gaps, no invented layers. */
class DungeonLayoutTest {

    private static final LayoutParameters PARAMS =
            new LayoutParameters(1234L, 512, -64, 2048, 5, 10);

    @Test
    void laneZeroIsRefusedAsTheOverworldLobby() {
        // Issue #246: the lobby is the Overworld entrance site, so the
        // dungeon dimension's layer stack starts at layer 1 - lane 0's whole
        // region is refused rather than invented as a lobby depth.
        for (int y = -64; y <= 1984; y += 97) {
            int probe = y;
            assertThrows(IllegalArgumentException.class,
                    () -> DungeonLayout.derive(PARAMS, 0, probe),
                    "lane 0 must be refused at y=" + probe);
            assertThrows(IllegalArgumentException.class,
                    () -> DungeonLayout.derive(PARAMS, 511, probe),
                    "lane 0's whole span must be refused at y=" + probe);
        }
        assertFalse(DungeonLayout.inDungeon(PARAMS, 0));
        assertFalse(DungeonLayout.inDungeon(PARAMS, 511));
    }

    @Test
    void depthHasNoGapsAcrossLayerBoundaries() {
        for (int layer = 1; layer <= 20; layer++) {
            int floors = FloorSchedule.floorsInLayer(PARAMS.worldSeed(), layer,
                    PARAMS.floorsMin(), PARAMS.floorsMax());
            // The deepest floor of a layer is its bottom band; the next
            // layer is entered at its top band (issue #183's direction).
            DungeonPosition deepest = DungeonLayout.derive(PARAMS,
                    LaneGeometry.laneOriginX(layer, PARAMS.laneStride()),
                    PARAMS.minY());
            DungeonPosition entry = DungeonLayout.derive(PARAMS,
                    LaneGeometry.laneOriginX(layer + 1, PARAMS.laneStride()),
                    PARAMS.minY() + PARAMS.availableHeight() - 1);
            assertEquals(floors - 1, deepest.floorInLayer(),
                    "layer " + layer + " deepest floor must be its bottom band");
            assertEquals(0, entry.floorInLayer(),
                    "layer " + (layer + 1) + " entry floor must be its top band");
            assertEquals(deepest.depth() + 1, entry.depth(),
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
        // Starts at layer 1: lane 0 is the Overworld lobby's home (#246) and
        // derives nowhere in the dungeon dimension.
        int previous = -1;
        for (int layer = 1; layer <= 30; layer++) {
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
            // Top-anchored: floor 0 sits at the top of the band, so the Y
            // that holds floor f counts down from the band's top.
            DungeonPosition position = DungeonLayout.derive(PARAMS,
                    LaneGeometry.laneOriginX(layer, PARAMS.laneStride()),
                    PARAMS.minY() + (floors - 1 - floor) * stride);
            assertEquals(floor, position.floorInLayer());
            seen++;
        }
        assertEquals(floors, seen);
    }

    @Test
    void laneZeroAndWestAreRefused() {
        assertFalse(DungeonLayout.inDungeon(PARAMS, -1));
        assertFalse(DungeonLayout.inDungeon(PARAMS, 0));
        assertTrue(DungeonLayout.inDungeon(PARAMS, 512));
        assertThrows(IllegalArgumentException.class, () -> DungeonLayout.derive(PARAMS, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> DungeonLayout.derive(PARAMS, 0, 0));
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
