package org.bitwisemadness.infinitedungeon.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.bitwisemadness.infinitedungeon.announce.Announcement;
import org.bitwisemadness.infinitedungeon.announce.DungeonAnnouncements;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.LaneTransitions;
import org.bitwisemadness.infinitedungeon.lanes.LayerUnlocks;

/**
 * Walking into a dungeon portal: bind or read the anchor, then teleport.
 *
 * <p>The trips of section 4. Entering from any dimension stores the portal you
 * used as your anchor and drops you at layer 1's arrival - the dungeon's first
 * layer since issue #246, which made the lobby the Overworld entrance site
 * (Reading B). Walking out applies the exit policy: your own portal when it is
 * intact; a re-formed portal at the old coordinates when the frame was
 * destroyed (`portal.reformMissingPortal`, default on); the overworld world
 * spawn otherwise - with the announcement when the anchor was missing or
 * destroyed, and configured mode for `portal.returnMode`. A portal cooldown
 * after every teleport stops ping-pong between facing portals.</p>
 */
public final class DungeonPortalTravel {

    private DungeonPortalTravel() {
    }

    /** One portal step, from the portal block the entity is standing in. */
    public static void handle(Level level, BlockPos portalPos, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (player.isOnPortalCooldown() || !player.isAlive() || player.isPassenger()) {
            return;
        }
        // Lane transitions (issue #31): a hop between layers inside the
        // dungeon resolves from the position alone - before the anchor logic
        // runs, so a lane portal can never bind or consume the overworld
        // return anchor. The dimension gate lives here; the hop itself is
        // public for the harness, which can never host this dimension.
        if (DungeonDimensions.LEVEL.equals(serverLevel.dimension())
                && laneHop(serverLevel, portalPos, player)) {
            return;
        }
        boolean inDungeon = DungeonDimensions.LEVEL.equals(serverLevel.dimension());
        TravelPolicy.Action action = TravelPolicy.decide(
                inDungeon, EntryAnchors.has(player), DungeonConfigs.portalReturnMode());

        switch (action) {
            case BIND_AND_ENTER -> enterDungeon(player, serverLevel, portalPos);
            case RETURN_TO_ANCHOR -> returnToAnchor(player, serverLevel);
            case WORLD_SPAWN_BY_CONFIG -> worldSpawn(player, false);
            case WORLD_SPAWN_NO_ANCHOR, WORLD_SPAWN_DESTROYED -> worldSpawn(player, true);
        }
    }

    /**
     * One lane hop: pair the position, refuse while the layer is sealed, or
     * teleport. Public so the harness can drive it - the GameTest level can
     * never be the dungeon dimension, which {@link #handle} gates on first.
     *
     * @return whether the position was a transition (handled here either way)
     */
    public static boolean laneHop(ServerLevel level, BlockPos portalPos, ServerPlayer player) {
        var target = LaneTransitions.pairTarget(level, portalPos);
        if (target.isEmpty()) {
            return false;
        }
        int layer = LaneGeometry.laneIndex(portalPos.getX(), LaneGrid.laneStride());
        if (LayerUnlocks.isSealed(level, layer)) {
            DungeonAnnouncements.send(player, Announcement.LANE_SEALED);
            player.setPortalCooldown();
            return true;
        }
        teleport(player, level, target.get());
        return true;
    }

    private static void enterDungeon(ServerPlayer player, ServerLevel origin, BlockPos portalPos) {
        EntryAnchors.bind(player, origin.dimension(), portalPos);
        ServerLevel dungeon = origin.getServer().getLevel(DungeonDimensions.LEVEL);
        if (dungeon == null) {
            return;
        }
        // Issue #246: the lobby is the Overworld entrance site, so the
        // dimension's content starts at layer 1. The descent lands at layer
        // 1's canonical arrival interior - the same slot a lane hop targets,
        // inside the portal plane with the cooldown guarding the re-trigger.
        // The lookup forces the chunk, so an absent frame leaves the player
        // where they stand rather than dropping them into the void.
        var arrival = LaneTransitions.descentTarget(dungeon);
        if (arrival.isPresent()
                && dungeon.getBlockState(arrival.get()).getBlock() instanceof DungeonPortalBlock) {
            teleport(player, dungeon, arrival.get());
        }
    }

    private static void returnToAnchor(ServerPlayer player, ServerLevel origin) {
        EntryAnchor anchor = EntryAnchors.get(player).orElseThrow();
        ServerLevel home = origin.getServer().getLevel(anchor.dimension());
        if (home == null) {
            // The anchor's dimension no longer exists: world spawn, announced.
            worldSpawn(player, true);
            return;
        }
        BlockPos anchorPos = anchor.position();
        if (!(home.getBlockState(anchorPos).getBlock() instanceof DungeonPortalBlock)) {
            // The anchor frame was destroyed: nether-style re-formation.
            boolean rebuilt = PortalFrameBuilder.build(home, anchorPos, Direction.Axis.X);
            if (TravelPolicy.afterReformAttempt(DungeonConfigs.reformMissingPortal(), rebuilt)
                    == TravelPolicy.Action.RETURN_TO_ANCHOR) {
                teleport(player, home, anchorPos);
            } else {
                worldSpawn(player, true);
            }
            return;
        }
        teleport(player, home, anchorPos);
    }

    /**
     * The world-spawn fallback: the overworld's shared spawn, optionally
     * announced. Public because the death-return (issue #246) shares this
     * exact shape - a missing or gone anchor lands you at world spawn either
     * way, walking out or respawning.
     */
    public static void worldSpawn(ServerPlayer player, boolean announce) {
        ServerLevel overworld = player.level().getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld,
                spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.setPortalCooldown();
        if (announce) {
            DungeonAnnouncements.send(player, Announcement.ANCHOR_FALLBACK);
        }
    }

    private static void teleport(ServerPlayer player, ServerLevel target, BlockPos destination) {
        player.teleportTo(target,
                destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.setPortalCooldown();
    }
}
