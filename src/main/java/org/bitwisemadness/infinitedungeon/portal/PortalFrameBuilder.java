package org.bitwisemadness.infinitedungeon.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * Builds a lit dungeon portal around an interior cell - the shared shape
 * behind both the first-arrival entrance and nether-style re-formation of a
 * destroyed anchor.
 *
 * <p>The minimal portal: a 2-wide, 3-tall interior with its frame ring
 * (corners included). Every cell is validated before any block is placed, so
 * a blocked space is refused whole - never half-built - and the caller falls
 * back to the world spawn instead. The anchor stores only the entry position
 * (section 4), so re-formed portals use the X axis.</p>
 */
public final class PortalFrameBuilder {

    private PortalFrameBuilder() {
    }

    /**
     * Builds a lit portal whose lower-left interior cell is {@code interior}.
     *
     * @param axis the portal's axis; X spans world X, Z spans world Z
     * @return whether the portal was built (false when the space is blocked)
     */
    public static boolean build(CommonLevelAccessor level, BlockPos interior, Direction.Axis axis) {
        return build(level, interior, axis, true);
    }

    /**
     * Builds the frame around {@code interior}, optionally lighting it. Layer
     * transitions build unlit (issue #31): the sealed/unsealed ignition gate
     * owns the first light, while arrivals and anchor re-formation keep the
     * lit behavior of the three-argument form.
     *
     * @param lit whether the interior is filled with portal blocks
     * @return whether the frame was built (false when the space is blocked)
     */
    public static boolean build(CommonLevelAccessor level, BlockPos interior, Direction.Axis axis, boolean lit) {
        boolean alongX = axis != Direction.Axis.Z;

        // Phase 1: validate the whole shape. The frame ring may already be
        // frame (idempotent rebuild); every interior cell must be clear.
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                if (isInterior(dx, dy)) {
                    continue;
                }
                BlockPos pos = offset(interior, dx, dy, alongX);
                if (!isFrameOrReplaceable(level, pos)) {
                    return false;
                }
            }
        }
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos pos = offset(interior, dx, dy, alongX);
                if (!isReplaceable(level, pos)) {
                    return false;
                }
            }
        }

        // Phase 2: place frame, then light the interior.
        Block frameState = DungeonConfigs.frameBlock();
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                if (isInterior(dx, dy)) {
                    continue;
                }
                level.setBlock(offset(interior, dx, dy, alongX), frameState.defaultBlockState(), 3);
            }
        }
        if (!lit) {
            return true;
        }
        BlockState portal = PortalBlocks.DUNGEON_PORTAL.get().defaultBlockState()
                .setValue(DungeonPortalBlock.AXIS, axis);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlock(offset(interior, dx, dy, alongX), portal, 3);
            }
        }
        return true;
    }

    private static boolean isInterior(int dx, int dy) {
        return dx >= 0 && dx <= 1 && dy >= 0 && dy <= 2;
    }

    private static BlockPos offset(BlockPos interior, int dx, int dy, boolean alongX) {
        return alongX ? interior.offset(dx, dy, 0) : interior.offset(0, dy, dx);
    }

    private static boolean isFrameOrReplaceable(CommonLevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(DungeonConfigs.frameBlock()) || isReplaceable(level, pos);
    }

    private static boolean isReplaceable(CommonLevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced()
                || state.getBlock() instanceof DungeonPortalBlock;
    }
}
