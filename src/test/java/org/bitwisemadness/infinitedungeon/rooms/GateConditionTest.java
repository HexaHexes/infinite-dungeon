package org.bitwisemadness.infinitedungeon.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How a gate opens (issue #252): the two shipped words, and the loud failure. */
class GateConditionTest {

    @Test
    void theTwoShippedWordsParse() {
        assertEquals(GateCondition.KEY,
                GateCondition.parse("key", "gates/treasure_door"));
        assertEquals(GateCondition.LAYER_DEFEATED,
                GateCondition.parse("layer_defeated", "gates/boss_door"));
    }

    @Test
    void parsingIsCaseAndWhitespaceInsensitive() {
        assertEquals(GateCondition.KEY, GateCondition.parse("  KEY ", "gates/a"));
        assertEquals(GateCondition.LAYER_DEFEATED,
                GateCondition.parse("Layer_Defeated", "gates/b"));
    }

    @Test
    void anUnknownWordNamesTheValueAndWhereItWasDeclared() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> GateCondition.parse("keyy", "gates/treasure_door"));
        assertTrue(failure.getMessage().contains("keyy"), failure.getMessage());
        assertTrue(failure.getMessage().contains("gates/treasure_door"), failure.getMessage());
        assertTrue(failure.getMessage().contains("key or layer_defeated"), failure.getMessage());
    }

    @Test
    void aNullConditionNamesWhereItWasDeclared() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> GateCondition.parse(null, "gates/missing"));
        assertTrue(failure.getMessage().contains("gates/missing"), failure.getMessage());
        assertTrue(failure.getMessage().contains("got null"), failure.getMessage());
    }
}
