package org.bitwisemadness.infinitedungeon.depth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The depth-transition classification (issue #238): outside/entry/layer/floor
 * edges, the forgotten-player rule, and malformed state - pure, no Minecraft
 * (the #200 rule).
 */
public class DepthTransitionTest {

    @Test
    void outsideWinsWhateverTheCoordinatesSay() {
        // Void and leavers count as gone, so the next crossing re-enters.
        assertEquals(DepthTransition.Kind.OUTSIDE,
                DepthTransition.classify(new int[] {3, 4}, false, 3, 4));
        assertEquals(DepthTransition.Kind.OUTSIDE,
                DepthTransition.classify(null, false, -1, -1));
    }

    @Test
    void firstObservationIsEntry() {
        assertEquals(DepthTransition.Kind.ENTRY,
                DepthTransition.classify(null, true, 0, 0));
        assertEquals(DepthTransition.Kind.ENTRY,
                DepthTransition.classify(new int[] {0}, true, 0, 0));
        assertEquals(DepthTransition.Kind.ENTRY,
                DepthTransition.classify(new int[] {}, true, 2, 5));
    }

    @Test
    void layerBeatsFloorWhenBothMove() {
        assertEquals(DepthTransition.Kind.LAYER,
                DepthTransition.classify(new int[] {1, 9}, true, 2, 0));
        // A pure layer hop keeps announcing even if the floor index coincides.
        assertEquals(DepthTransition.Kind.LAYER,
                DepthTransition.classify(new int[] {1, 3}, true, 2, 3));
    }

    @Test
    void floorMovesWithinTheSameLayer() {
        assertEquals(DepthTransition.Kind.FLOOR,
                DepthTransition.classify(new int[] {4, 1}, true, 4, 2));
        assertEquals(DepthTransition.Kind.SAME,
                DepthTransition.classify(new int[] {4, 2}, true, 4, 2));
    }
}
