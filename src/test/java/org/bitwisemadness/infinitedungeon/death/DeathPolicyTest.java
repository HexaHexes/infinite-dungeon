package org.bitwisemadness.infinitedungeon.death;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Which deaths continue at the lobby: dungeon deaths and nothing else. */
class DeathPolicyTest {

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
