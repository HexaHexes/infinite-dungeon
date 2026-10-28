package org.bitwisemadness.infinitedungeon.spawn;

/**
 * The spawn-point invariant, as pure logic: the player's actual spawn point is
 * never settable inside the dungeon dimension.
 *
 * <p>Every path that can set a respawn point consults this policy by dimension
 * id - sleep (which is allowed as flavour and must preserve the stored
 * respawn), the spawn-point command, and the at-death sweep of stored respawn
 * targets. Respawn anchors need no policy: the shipped dimension type already
 * refuses them. Kept free of Minecraft types so the invariant is unit-testable
 * without a game runtime.</p>
 */
public final class SpawnPolicy {

    /** The dungeon dimension id; must stay aligned with DungeonDimensions. */
    public static final String DUNGEON_DIMENSION = "infinite_dungeon:dungeon";

    private SpawnPolicy() {
    }

    /**
     * Whether sleeping in this dimension must preserve the player's stored
     * respawn target. Vanilla writes the respawn position the moment a player
     * actually lies down, so in the dungeon the guard snapshots the previous
     * target first and restores it afterwards: the nap is flavour and touches
     * nothing.
     */
    public static boolean preservesRespawn(String dimensionId) {
        return isDungeon(dimensionId);
    }

    /** Whether the spawn-point command is refused while standing in this dimension. */
    public static boolean refusesSpawnpointCommand(String dimensionId) {
        return isDungeon(dimensionId);
    }

    /**
     * Whether a respawn target stored in this dimension must be undone. This is
     * the safety net: whatever set it - a mod, a datapack, a nap - it is put
     * back the way it was, or cleared if the player had none.
     */
    public static boolean clearsRespawn(String respawnDimensionId) {
        return isDungeon(respawnDimensionId);
    }

    private static boolean isDungeon(String dimensionId) {
        return DUNGEON_DIMENSION.equals(dimensionId);
    }
}
