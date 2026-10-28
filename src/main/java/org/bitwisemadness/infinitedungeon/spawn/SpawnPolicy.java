package org.bitwisemadness.infinitedungeon.spawn;

/**
 * The spawn-point invariant, as pure logic: the player's actual spawn point is
 * never settable inside the dungeon dimension.
 *
 * <p>Every path that can set a respawn point consults this policy by dimension
 * id - sleep, the spawn-point command, and the at-death sweep of stored
 * respawn targets. Respawn anchors need no policy: the shipped dimension type
 * already refuses them. Kept free of Minecraft types so the invariant is
 * unit-testable without a game runtime.</p>
 */
public final class SpawnPolicy {

    /** The dungeon dimension id; must stay aligned with DungeonDimensions. */
    public static final String DUNGEON_DIMENSION = "infinite_dungeon:dungeon";

    private SpawnPolicy() {
    }

    /**
     * Whether sleep is denied in this dimension. Sleep is denied in the
     * dungeon: vanilla only sets a respawn position once a player actually
     * starts sleeping, so denying it at the gate keeps beds decorative.
     */
    public static boolean deniesSleep(String dimensionId) {
        return isDungeon(dimensionId);
    }

    /** Whether the spawn-point command is refused while standing in this dimension. */
    public static boolean refusesSpawnpointCommand(String dimensionId) {
        return isDungeon(dimensionId);
    }

    /**
     * Whether a respawn target stored in this dimension must be cleared. This
     * is the safety net: whatever set it - a mod, a datapack - it is wiped at
     * death, before any respawn could land there.
     */
    public static boolean clearsRespawn(String respawnDimensionId) {
        return isDungeon(respawnDimensionId);
    }

    private static boolean isDungeon(String dimensionId) {
        return DUNGEON_DIMENSION.equals(dimensionId);
    }
}
