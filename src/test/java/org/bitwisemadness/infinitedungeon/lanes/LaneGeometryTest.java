package org.bitwisemadness.infinitedungeon.lanes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The lane grid: floor-division lanes, integer anchors, positive strides. */
class LaneGeometryTest {

    @Test
    void laneIndexIsFloorDivision() {
        assertEquals(0, LaneGeometry.laneIndex(0, 512));
        assertEquals(0, LaneGeometry.laneIndex(511, 512));
        assertEquals(1, LaneGeometry.laneIndex(512, 512));
        assertEquals(1, LaneGeometry.laneIndex(1023, 512));
        assertEquals(-1, LaneGeometry.laneIndex(-1, 512));
        assertEquals(-1, LaneGeometry.laneIndex(-512, 512));
        assertEquals(-2, LaneGeometry.laneIndex(-513, 512));
    }

    @Test
    void everyPositionMapsBackToItsAnchor() {
        int stride = 512;
        for (int x = -2000; x <= 2000; x += 17) {
            int lane = LaneGeometry.laneIndex(x, stride);
            int anchor = LaneGeometry.laneOriginX(lane, stride);
            org.junit.jupiter.api.Assertions.assertTrue(x >= anchor && x < anchor + stride,
                    x + " must sit in lane " + lane + " at " + anchor);
        }
    }

    @Test
    void laneOriginAnchorsLayerAtItsMultiple() {
        assertEquals(0, LaneGeometry.laneOriginX(0, 512));
        assertEquals(512, LaneGeometry.laneOriginX(1, 512));
        assertEquals(-512, LaneGeometry.laneOriginX(-1, 512));
        assertEquals(2048, LaneGeometry.laneOriginX(4, 512));
    }

    @Test
    void nonPositiveStrideIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> LaneGeometry.laneIndex(0, 0));
        assertThrows(IllegalArgumentException.class, () -> LaneGeometry.laneIndex(0, -512));
        assertThrows(IllegalArgumentException.class, () -> LaneGeometry.laneOriginX(1, 0));
    }
}
