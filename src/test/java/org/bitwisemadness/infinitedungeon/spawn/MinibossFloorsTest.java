package org.bitwisemadness.infinitedungeon.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The miniboss floor assignment and stat stack (issue #41): start-layer and
 * floor-0 boundaries, divisibility, the degenerate-interval refusal, and the
 * plan section 7 stack order - pure, no Minecraft (the #200 rule).
 */
public class MinibossFloorsTest {

    @Test
    void assignmentHonoursTheStartLayer() {
        assertFalse(MinibossFloors.assigned(2, 3, 3, 3),
                "layers below startLayer never field minibosses");
        assertTrue(MinibossFloors.assigned(3, 3, 3, 3),
                "startLayer itself is included (from = inclusive)");
        assertTrue(MinibossFloors.assigned(9, 6, 3, 3));
    }

    @Test
    void floorZeroNeverFieldsOne() {
        assertFalse(MinibossFloors.assigned(3, 0, 3, 3),
                "entrances are safe rooms (plan section 11)");
        assertFalse(MinibossFloors.assigned(5, 0, 3, 3));
    }

    @Test
    void assignmentIsDivisibilityWithinTheLayer() {
        assertTrue(MinibossFloors.assigned(3, 3, 3, 3));
        assertTrue(MinibossFloors.assigned(3, 6, 3, 3));
        assertFalse(MinibossFloors.assigned(3, 1, 3, 3));
        assertFalse(MinibossFloors.assigned(3, 4, 3, 3));
        // everyN = 1: every floor above 0 fields one.
        assertTrue(MinibossFloors.assigned(3, 1, 3, 1));
        assertTrue(MinibossFloors.assigned(3, 7, 3, 1));
    }

    @Test
    void degenerateIntervalsAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> MinibossFloors.assigned(3, 3, 3, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MinibossFloors.assigned(3, 3, 3, -1));
    }

    @Test
    void theMinibossMultiplierStacksLast() {
        // Plan section 7: (1 + D * pp) * party, THEN the miniboss factor.
        assertEquals(6.0, MinibossFloors.stacked(3.0, 2.0), 1e-9);
        assertEquals(3.0, MinibossFloors.stacked(3.0, 1.0), 1e-9);
        assertEquals(0.0, MinibossFloors.stacked(3.0, 0.0), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> MinibossFloors.stacked(-1.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> MinibossFloors.stacked(2.0, -1.0));
    }
}
