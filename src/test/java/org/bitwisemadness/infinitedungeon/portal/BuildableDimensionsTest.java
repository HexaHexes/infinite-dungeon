package org.bitwisemadness.infinitedungeon.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The construction gate: which dimensions may have portals built in them. */
class BuildableDimensionsTest {

    @Test
    void shippedDefaultIsOverworldAndDungeonWhitelist() {
        BuildableDimensions defaults = BuildableDimensions.DEFAULT;
        assertEquals(BuildableDimensions.Mode.WHITELIST, defaults.mode());
        assertTrue(defaults.allows("minecraft:overworld"));
        assertTrue(defaults.allows("infinite_dungeon:dungeon"));
        assertFalse(defaults.allows("minecraft:the_nether"));
        assertFalse(defaults.allows("minecraft:the_end"));
        assertFalse(defaults.allows("twilightforest:twilight_forest"));
    }

    @Test
    void blacklistAllowsEverythingButTheList() {
        BuildableDimensions blacklist = new BuildableDimensions(
                BuildableDimensions.Mode.BLACKLIST, Set.of("minecraft:the_nether"));
        assertTrue(blacklist.allows("minecraft:overworld"));
        assertFalse(blacklist.allows("minecraft:the_nether"));
    }

    @Test
    void emptyListDeniesOnWhitelistAndAllowsOnBlacklist() {
        BuildableDimensions whitelist = new BuildableDimensions(
                BuildableDimensions.Mode.WHITELIST, Set.of());
        BuildableDimensions blacklist = new BuildableDimensions(
                BuildableDimensions.Mode.BLACKLIST, Set.of());
        assertFalse(whitelist.allows("minecraft:overworld"));
        assertTrue(blacklist.allows("minecraft:overworld"));
    }

    @Test
    void unknownAndMissingDimensionIdsAreNeverWhitelisted() {
        assertFalse(BuildableDimensions.DEFAULT.allows(null));
        assertFalse(BuildableDimensions.DEFAULT.allows(""));
    }

    @Test
    void listEntriesAreTrimmedAndBlanksDropped() {
        BuildableDimensions trimmed = new BuildableDimensions(
                BuildableDimensions.Mode.WHITELIST,
                new java.util.LinkedHashSet<>(List.of("  minecraft:the_end  ", "", "   ", "minecraft:overworld")));
        assertTrue(trimmed.allows("minecraft:the_end"));
        assertTrue(trimmed.allows("minecraft:overworld"));
        assertFalse(trimmed.allows(""));
        assertFalse(trimmed.allows("   "));
    }

    @Test
    void modeParsingDefaultsToTheWhitelist() {
        assertEquals(BuildableDimensions.Mode.BLACKLIST, BuildableDimensions.parseMode("blacklist"));
        assertEquals(BuildableDimensions.Mode.BLACKLIST, BuildableDimensions.parseMode(" BlackList "));
        assertEquals(BuildableDimensions.Mode.WHITELIST, BuildableDimensions.parseMode("whitelist"));
        assertEquals(BuildableDimensions.Mode.WHITELIST, BuildableDimensions.parseMode("nonsense"));
        assertEquals(BuildableDimensions.Mode.WHITELIST, BuildableDimensions.parseMode(null));
    }
}
