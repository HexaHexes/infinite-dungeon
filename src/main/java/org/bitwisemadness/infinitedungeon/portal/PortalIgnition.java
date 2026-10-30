package org.bitwisemadness.infinitedungeon.portal;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.bitwisemadness.infinitedungeon.announce.Announcement;
import org.bitwisemadness.infinitedungeon.announce.DungeonAnnouncements;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.LaneTransitions;
import org.bitwisemadness.infinitedungeon.lanes.LayerUnlocks;

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
        // The construction toggle and the dimension gate share one refusal:
        // the whole interaction is cancelled and the player is told why.
        if (!ConstructionPolicy.mayIgnite(
                DungeonConfigs.portalAllowConstruction(),
                DungeonConfigs.portalBuildableDimensions()
                        .allows(event.getLevel().dimension().location().toString()))) {
            event.setCanceled(true);
            event.setCancellationResult(
                    InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            notifyRefusal(event.getEntity());
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(DungeonConfigs.frameBlock())) {
            return;
        }
        Direction face = event.getFace();
        if (face == null) {
            return;
        }
        BlockPos start = event.getPos().relative(face);
        if (ignitionSealed(event.getLevel(), start)) {
            // A sealed layer transition refuses its first light - same
            // cancel-and-tell shape as the construction gate above.
            event.setCanceled(true);
            event.setCancellationResult(
                    InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            notifySealed(event.getEntity());
            return;
        }
        if (tryIgnite(event.getLevel(), start)) {
            event.setCanceled(true);
        }
    }

    /**
     * Fire an entity placed inside a frame: ignite in place, replacing the
     * fire with portal blocks - unless the dimension allows no portal.
     */
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(Blocks.FIRE)) {
            return;
        }
        if (event.getLevel() instanceof Level level) {
            if (!ConstructionPolicy.mayIgnite(
                    DungeonConfigs.portalAllowConstruction(),
                    DungeonConfigs.portalBuildableDimensions()
                            .allows(level.dimension().location().toString()))) {
                notifyRefusal(event.getEntity());
                return;
            }
            if (ignitionSealed(level, event.getPos())) {
                notifySealed(event.getEntity());
                return;
            }
            tryIgnite(level, event.getPos());
        }
    }

    /**
     * Whether a lane-transition frame's first light is refused: the position
     * is a transition slot of this dimension and its layer is sealed
     * (assigned guardian, not yet unlocked). Server-side by construction - the
     * seal lives in world saved data, which never syncs to the client, so the
     * server's cancel is authoritative and the fire simply never lands.
     */
    public static boolean ignitionSealed(Level level, BlockPos interior) {
        if (!(level instanceof ServerLevel server)
                || !DungeonDimensions.LEVEL.equals(server.dimension())) {
            return false;
        }
        return sealedAt(server, interior);
    }

    /**
     * The seal-at-position check without the dimension gate - the same split
     * as handle/laneHop: the gate belongs to the production entry points, so
     * this core stays drivable in the harness (which can never host the
     * dungeon dimension).
     */
    public static boolean sealedAt(ServerLevel server, BlockPos interior) {
        if (LaneTransitions.pairTarget(server, interior).isEmpty()) {
            return false;
        }
        return LayerUnlocks.isSealed(server,
                LaneGeometry.laneIndex(interior.getX(), LaneGrid.laneStride()));
    }

    /** The shared refusal message: the dimension gate and the construction toggle both use it. */
    private static void notifyRefusal(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            DungeonAnnouncements.send(player, Announcement.PORTAL_NOT_ALLOWED);
        }
    }

    /** The sealed-transition refusal message. */
    private static void notifySealed(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            DungeonAnnouncements.send(player, Announcement.LANE_SEALED);
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
