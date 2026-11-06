package org.bitwisemadness.infinitedungeon.lock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The adventure lock's two rules: where it holds, and what may be broken. */
class AdventureLockTest {

    @BeforeAll
    static void seedRegistry() {
        // The gates resolve dungeon membership through the profile
        // registry (issue #162); the plain-JVM suite seeds the core
        // dimension's minimal profile directly.
        DimensionProfiles.register(
                DimensionProfile.forDimension("infinite_dungeon:dungeon"));
    }

    @AfterAll
    static void clearRegistry() {
        DimensionProfiles.reset();
    }

    @Test
    void theLockHoldsOnlyInDungeon() {
        assertTrue(AdventureLock.applies("infinite_dungeon:dungeon"));
        assertFalse(AdventureLock.applies("minecraft:overworld"));
        assertFalse(AdventureLock.applies("minecraft:the_nether"));
        assertFalse(AdventureLock.applies(null));
    }

    @Test
    void nothingIsBreakableWithoutAWhitelistEntry() {
        assertFalse(AdventureLock.isBreakable("minecraft:stone", Set.of()));
        assertFalse(AdventureLock.isBreakable("minecraft:stone", List.of()));
        assertFalse(AdventureLock.isBreakable("minecraft:stone", null));
    }

    @Test
    void whitelistedBlocksMatchAsFullTrimmedIds() {
        Set<String> whitelist = Set.of("minecraft:oak_planks", "infinite_dungeon:portal_frame");
        assertTrue(AdventureLock.isBreakable("minecraft:oak_planks", whitelist));
        assertTrue(AdventureLock.isBreakable(" infinite_dungeon:portal_frame ", whitelist));
        assertFalse(AdventureLock.isBreakable("minecraft:stone", whitelist));
        // A bare entry never matches a namespaced id: packs name ids exactly.
        assertFalse(AdventureLock.isBreakable("oak_planks", whitelist));
    }

    @Test
    void missingBlockIdsNeverBreak() {
        assertFalse(AdventureLock.isBreakable(null, Set.of("minecraft:stone")));
        assertFalse(AdventureLock.isBreakable("", Set.of("minecraft:stone")));
    }
}
