package org.bitwisemadness.infinitedungeon.death;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Which deaths continue at the lobby: dungeon deaths and nothing else. */
class DeathPolicyTest {

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
    void aDeathInTheDungeonContinuesAtTheLobby() {
        assertTrue(DeathPolicy.returnsToLobby("infinite_dungeon:dungeon"));
    }

    @Test
    void deathsElsewhereStayOrdinary() {
        assertFalse(DeathPolicy.returnsToLobby("minecraft:overworld"));
        assertFalse(DeathPolicy.returnsToLobby("minecraft:the_nether"));
        assertFalse(DeathPolicy.returnsToLobby(null));
    }
}
