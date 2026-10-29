package org.bitwisemadness.infinitedungeon.death;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The sweep removes only what the config allows, and only when empty. */
class CorpsePolicyTest {

    @Test
    void anEmptiedCorpseIsRemovedWhenTheConfigAllows() {
        assertTrue(CorpsePolicy.shouldRemove(true, true));
    }

    @Test
    void aCorpseStillHoldingItemsAlwaysStays() {
        assertFalse(CorpsePolicy.shouldRemove(true, false));
    }

    @Test
    void withTheConfigOffNothingIsRemoved() {
        assertFalse(CorpsePolicy.shouldRemove(false, true));
        assertFalse(CorpsePolicy.shouldRemove(false, false));
    }
}
