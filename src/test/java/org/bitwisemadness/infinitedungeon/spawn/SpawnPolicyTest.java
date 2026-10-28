package org.bitwisemadness.infinitedungeon.spawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The spawn-point invariant: every decision keys off the dimension, and only
 * the dungeon dimension ever trips.
 */
class SpawnPolicyTest {

    @Test
    void sleepPreservesTheSpawnPointInTheDungeonOnly() {
        assertTrue(SpawnPolicy.preservesRespawn(SpawnPolicy.DUNGEON_DIMENSION));
        assertFalse(SpawnPolicy.preservesRespawn("minecraft:overworld"));
        assertFalse(SpawnPolicy.preservesRespawn(null));
    }

    @Test
    void spawnpointCommandIsRefusedInTheDungeonOnly() {
        assertTrue(SpawnPolicy.refusesSpawnpointCommand(SpawnPolicy.DUNGEON_DIMENSION));
        assertFalse(SpawnPolicy.refusesSpawnpointCommand("minecraft:overworld"));
        assertFalse(SpawnPolicy.refusesSpawnpointCommand(null));
    }

    @Test
    void dungeonRespawnTargetsAreCleared() {
        assertTrue(SpawnPolicy.clearsRespawn(SpawnPolicy.DUNGEON_DIMENSION));
        assertFalse(SpawnPolicy.clearsRespawn("minecraft:overworld"));
        assertFalse(SpawnPolicy.clearsRespawn(null));
    }

    @Test
    void theDungeonIdMatchesTheDimensionsClass() {
        // SpawnPolicy stays Minecraft-free, so the id is spelled out here; the
        // guard feeds it from DungeonDimensions, and this test pins the string.
        assertTrue(SpawnPolicy.DUNGEON_DIMENSION.startsWith("infinite_dungeon:"));
    }
}
