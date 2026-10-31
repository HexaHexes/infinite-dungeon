package org.bitwisemadness.infinitedungeon.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The protection lifetime rule (issue #251): parsing, truth table, messages. */
class ProtectedBlocksTest {

    @Test
    void lifetimesParseFromTheirThreeWords() {
        assertEquals(ProtectedBlocks.Until.DEFEATED,
                ProtectedBlocks.Until.parse("defeated", "rooms/treasure"));
        assertEquals(ProtectedBlocks.Until.ALWAYS,
                ProtectedBlocks.Until.parse("Always", "rooms/safe"));
        assertEquals(ProtectedBlocks.Until.NEVER,
                ProtectedBlocks.Until.parse("never", "rooms/open"));
    }

    @Test
    void anUnknownLifetimeNamesTheValueAndItsLocation() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> ProtectedBlocks.Until.parse("defeatd", "rooms/treasure/door"));
        assertTrue(failure.getMessage().contains("defeatd"), failure.getMessage());
        assertTrue(failure.getMessage().contains("rooms/treasure/door"), failure.getMessage());
        assertTrue(failure.getMessage().contains("defeated, always or never"), failure.getMessage());
    }

    @Test
    void protectionEndsExactlyWhenTheLayerDefeats() {
        assertTrue(ProtectedBlocks.protects(ProtectedBlocks.Until.DEFEATED, false),
                "the defeated lifetime must protect while the guardian stands");
        assertFalse(ProtectedBlocks.protects(ProtectedBlocks.Until.DEFEATED, true),
                "the guardian's fall must release protection");
        assertTrue(ProtectedBlocks.protects(ProtectedBlocks.Until.ALWAYS, false),
                "always must protect before any guardian falls");
        assertTrue(ProtectedBlocks.protects(ProtectedBlocks.Until.ALWAYS, true),
                "always must survive a defeated layer");
        assertFalse(ProtectedBlocks.protects(ProtectedBlocks.Until.NEVER, false),
                "never is the explicit opt-out");
        assertFalse(ProtectedBlocks.protects(ProtectedBlocks.Until.NEVER, true),
                "never is the explicit opt-out");
    }
}
