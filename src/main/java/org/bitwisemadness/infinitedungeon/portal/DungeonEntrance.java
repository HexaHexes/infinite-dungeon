package org.bitwisemadness.infinitedungeon.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The dungeon-side entrance: where an arriving player materialises.
 *
 * <p><strong>Wave-1 placeholder, flagged for the plan.</strong> The plan
 * forbids visitor-triggered world edits (Chunky pregeneration parity: all
 * content is seed-derived at chunk-generation time), so this fixed,
 * position-deterministic entrance - built once, identically in every world,
 * at fixed coordinates - is a deliberate deviation pending a ruling; Wave 2's
 * lane geometry and structure sets generate entrances properly and supersede
 * it.</p>
 *
 * <p>Layout: a 5x5 platform of the configured frame block one block below the
 * entrance, a nether-portal-shaped frame on it, and the interior lit as a
 * dungeon portal. Idempotent: once the portal exists, it is left alone.</p>
 */
public final class DungeonEntrance {

    /** The Wave-1 entrance: platform below, frame spanning x 0..3, y 64..68. */
    public static final BlockPos ENTRANCE = new BlockPos(0, 64, 0);

    /** The block a player materialises in: the portal's lower-left interior cell. */
    private static final BlockPos ARRIVAL = new BlockPos(1, 65, 0);

    private DungeonEntrance() {
    }

    /**
     * Ensures the entrance exists and returns the position to materialise at.
     */
    public static BlockPos ensure(ServerLevel level) {
        if (level.getBlockState(ARRIVAL).getBlock() instanceof DungeonPortalBlock) {
            return ARRIVAL;
        }
        Block frame = DungeonConfigs.frameBlock();
        BlockState frameState = frame.defaultBlockState();

        // Platform: 5x5 under and around the frame.
        for (int x = -1; x <= 4; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 63, z), frameState);
            }
        }
        // Frame: bottom and top rows, side columns (corners optional, but built).
        for (int x = 0; x <= 3; x++) {
            level.setBlockAndUpdate(new BlockPos(x, 64, 0), frameState);
            level.setBlockAndUpdate(new BlockPos(x, 68, 0), frameState);
        }
        for (int y = 65; y <= 67; y++) {
            level.setBlockAndUpdate(new BlockPos(0, y, 0), frameState);
            level.setBlockAndUpdate(new BlockPos(3, y, 0), frameState);
        }
        // Interior: light it.
        BlockState portal = PortalBlocks.DUNGEON_PORTAL.get().defaultBlockState()
                .setValue(DungeonPortalBlock.AXIS, Direction.Axis.X);
        for (int x = 1; x <= 2; x++) {
            for (int y = 65; y <= 67; y++) {
                level.setBlockAndUpdate(new BlockPos(x, y, 0), portal);
            }
        }
        return ARRIVAL;
    }
}
