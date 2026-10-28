package org.bitwisemadness.infinitedungeon.portal;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * Ignites dungeon portals: scanning a lit frame and filling it with portal
 * blocks.
 *
 * <p>Two triggers cover the plan's "flint and steel or fire" wording: a
 * flint-and-steel use against a frame block (cancelled so vanilla never places
 * its own fire inside a frame the dungeon is claiming), and a fire block an
 * entity placed inside the frame. Both funnel into the same scan-and-fill,
 * which works in every dimension - including the dungeon itself, where vanilla
 * refuses to create portals.</p>
 */
public final class PortalIgnition {

    private PortalIgnition() {
    }

    /**
     * Scans both portal planes from the given cell and fills any valid frame
     * with portal blocks.
     *
     * @param start an interior cell of the frame (where the fire would sit)
     * @return whether a portal was created
     */
    public static boolean tryIgnite(Level level, BlockPos start) {
        for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
            Optional<PortalFrameScan.Layout> layout = PortalFrameScan.scan(
                    along(start, axis), start.getY(), cellTest(level, axis, fixed(start, axis)));
            if (layout.isPresent()) {
                fill(level, axis, fixed(start, axis), layout.get());
                return true;
            }
        }
        return false;
    }

    /**
     * Flint and steel against a frame block: ignite before vanilla places its
     * fire, so no nether portal can form inside a frame the dungeon is
     * claiming.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(net.minecraft.world.item.Items.FLINT_AND_STEEL)) {
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(DungeonConfigs.frameBlock())) {
            return;
        }
        BlockPos start = event.getPos().relative(event.getFace());
        if (tryIgnite(event.getLevel(), start)) {
            event.setCanceled(true);
        }
    }

    /**
     * Fire an entity placed inside a frame: ignite in place, replacing the
     * fire with portal blocks.
     */
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(Blocks.FIRE)) {
            return;
        }
        if (event.getLevel() instanceof Level level) {
            tryIgnite(level, event.getPos());
        }
    }

    private static void fill(Level level, Direction.Axis axis, int fixed,
                             PortalFrameScan.Layout layout) {
        BlockState portal = PortalBlocks.DUNGEON_PORTAL.get().defaultBlockState()
                .setValue(DungeonPortalBlock.AXIS, axis);
        for (int[] cell : layout.interiorCells()) {
            level.setBlock(cellPos(cell[0], cell[1], fixed, axis), portal, 3);
        }
    }

    private static PortalFrameScan.CellTest cellTest(Level level, Direction.Axis axis, int fixed) {
        return (along, y) -> {
            BlockState state = level.getBlockState(cellPos(along, y, fixed, axis));
            if (state.is(DungeonConfigs.frameBlock())) {
                return PortalFrameScan.Cell.FRAME;
            }
            if (state.isAir() || state.is(Blocks.FIRE)) {
                return PortalFrameScan.Cell.EMPTY;
            }
            return PortalFrameScan.Cell.OTHER;
        };
    }

    private static int along(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.X ? pos.getX() : pos.getZ();
    }

    private static int fixed(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.X ? pos.getZ() : pos.getX();
    }

    private static BlockPos cellPos(int along, int y, int fixed, Direction.Axis axis) {
        return axis == Direction.Axis.X
                ? new BlockPos(along, y, fixed)
                : new BlockPos(fixed, y, along);
    }
}
