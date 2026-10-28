package org.bitwisemadness.infinitedungeon.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The clock policy: defaults stay frozen at noon, and ticks wrap into the day. */
class TimePolicyTest {

    @Test
    void shippedDefaultFlowsTheClock() {
        assertTrue(TimePolicy.DEFAULT.flows());
        assertEquals(6000, TimePolicy.DEFAULT.fixedTick());
    }

    @Test
    void fixedTickWrapsIntoTheDay() {
        assertEquals(0, new TimePolicy(true, TimePolicy.DAY_LENGTH).fixedTick());
        assertEquals(TimePolicy.DAY_LENGTH - 1, new TimePolicy(true, -1).fixedTick());
        assertEquals(6000, new TimePolicy(true, 6000).fixedTick());
    }

    @Test
    void flowsIsPreserved() {
        assertTrue(new TimePolicy(true, 0).flows());
        assertFalse(new TimePolicy(false, 0).flows());
    }
}
