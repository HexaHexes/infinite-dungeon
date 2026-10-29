package org.bitwisemadness.infinitedungeon.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The dungeon-side entrance: where an arriving player materialises.
 *
 * <p><strong>Wave-1 placeholder, recorded in the plan.</strong> The plan
 * forbids visitor-triggered world edits (Chunky pregeneration parity: all
 * content is seed-derived at chunk-generation time), so this fixed,
 * position-deterministic entrance - built once, identically in every world, at
 * fixed coordinates - is a recorded deviation; Wave 2's lane geometry and
 * structure sets generate entrances properly and replace it.</p>
 *
 * <p>Layout: a 5x5 platform of the configured frame block below, then the
 * shared minimal portal from {@link PortalFrameBuilder#build}. Idempotent:
 * once the portal exists it is left alone. Returns {@code null} when the
 * space is blocked, and the travel code leaves the player where they stand.</p>
 */
public final class DungeonEntrance {

    /** The Wave-1 entrance: platform below, frame spanning x 0..3, y 64..68. */
    public static final BlockPos ENTRANCE = new BlockPos(0, 64, 0);

    /** The block a player materialises in: the portal's lower-left interior cell. */
    private static final BlockPos ARRIVAL = new BlockPos(1, 65, 0);

    private DungeonEntrance() {
    }

    /** Ensures the entrance exists and returns the position to materialise at, or null when blocked. */
    public static BlockPos ensure(ServerLevel level) {
        if (level.getBlockState(ARRIVAL).getBlock() instanceof DungeonPortalBlock) {
            return ARRIVAL;
        }
        // Platform: 5x5 under and around the frame.
        for (int x = -1; x <= 4; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos under = new BlockPos(x, 63, z);
                if (level.getBlockState(under).isAir()) {
                    level.setBlockAndUpdate(under, DungeonConfigs.frameBlock().defaultBlockState());
                }
            }
        }
        return PortalFrameBuilder.build(level, ARRIVAL, Direction.Axis.X) ? ARRIVAL : null;
    }
}
