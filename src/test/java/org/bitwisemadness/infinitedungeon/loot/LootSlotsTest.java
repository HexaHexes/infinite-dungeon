package org.bitwisemadness.infinitedungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The slot-resolution precedence (issue #256). */
class LootSlotsTest {

    private static LootSlotMapping mapping(String slot, String theme,
            int minLayer, int maxLayer, String table) {
        return new LootSlotMapping(slot, theme, minLayer, maxLayer, table);
    }

    @Test
    void anExactThemeBeatsTheWildcard() {
        List<LootSlotMapping> all = List.of(
                mapping("s", LootSlotMapping.ANY_THEME, 1, 9, "wild"),
                mapping("s", "theme", 1, 9, "exact"));
        assertEquals(Optional.of("exact"), LootSlots.resolveFor(all, "s", "theme", 3),
                "the exact theme wins");
        assertEquals(Optional.of("wild"), LootSlots.resolveFor(all, "s", "other", 3),
                "an unserved theme falls back to the wildcard");
    }

    @Test
    void theNarrowestBandWinsWithinATheme() {
        List<LootSlotMapping> all = List.of(
                mapping("s", "t", 1, 9, "wide"),
                mapping("s", "t", 3, 4, "narrow"));
        assertEquals(Optional.of("narrow"), LootSlots.resolveFor(all, "s", "t", 3),
                "the narrower band wins inside it");
        assertEquals(Optional.of("wide"), LootSlots.resolveFor(all, "s", "t", 8),
                "outside the narrow band the wide one serves");
    }

    @Test
    void tiesAndMissesFallBack() {
        List<LootSlotMapping> all = List.of(
                mapping("s", "t", 1, 3, "first"),
                mapping("s", "t", 1, 3, "second"));
        assertEquals(Optional.of("first"), LootSlots.resolveFor(all, "s", "t", 2),
                "load order breaks a tie");
        assertTrue(LootSlots.resolveFor(all, "s", "t", 5).isEmpty(),
                "outside the band is an empty answer");
        assertTrue(LootSlots.resolveFor(all, "other", "t", 2).isEmpty(),
                "an unknown slot is an empty answer");
    }
}
