package org.bitwisemadness.infinitedungeon.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The dungeon-side entrance: where an arriving player materialises.
 *
 * <p><strong>Generated at chunk time since issue #131</strong> - the Wave-1
 * visitor-triggered build is retired: {@code LobbyEntranceFeature} generates
 * this exact shape at the fixed origin chunk, so pregenerated and organic
 * worlds are identical from the first tick (the recorded Chunky deviation is
 * closed and the design record amended). What remains here is the lookup
 * contract the travel and death-return paths already depend on: the position
 * is fixed, the blocks are the feature's job, and a missing lobby yields
 * {@code null} - callers keep their documented fallbacks (stay where you
 * stand, ordinary respawn) rather than this class rebuilding anything.</p>
 *
 * <p>Layout (unchanged coordinates): a platform of the configured frame
 * block below (x 0..4, z 0..2 at y 63, inside the fixed chunk so generation
 * never writes across a chunk border), then the shared minimal portal from
 * {@link PortalFrameBuilder#build}.</p>
 */
public final class DungeonEntrance {

    /** The entrance: platform below, frame spanning x 0..3, y 64..68. */
    public static final BlockPos ENTRANCE = new BlockPos(0, 64, 0);

    /** The block a player materialises in: the portal's lower-left interior cell. */
    static final BlockPos ARRIVAL = new BlockPos(1, 65, 0);

    /**
     * Where the lobby return lands: on the platform in front of the portal,
     * never inside a portal cell - a landing there would read as an exit on
     * the cooldown's expiry.
     */
    static final BlockPos LOBBY_SPOT = new BlockPos(1, 64, 1);

    private DungeonEntrance() {
    }

    /**
     * The arrival position when the generated entrance is present, else
     * {@code null} - never builds; the lobby is the feature's product and its
     * absence is a bug to report, not a trigger to reconstruct.
     */
    public static BlockPos ensure(ServerLevel level) {
        return level.getBlockState(ARRIVAL).getBlock() instanceof DungeonPortalBlock ? ARRIVAL : null;
    }

    /**
     * The lobby return's landing spot when the generated lobby is present,
     * else {@code null} - the respawn path then stays at the ordinary respawn
     * point instead of dropping the player into the void.
     */
    public static BlockPos lobby(ServerLevel level) {
        return level.getBlockState(ARRIVAL).getBlock() instanceof DungeonPortalBlock ? LOBBY_SPOT : null;
    }
}
