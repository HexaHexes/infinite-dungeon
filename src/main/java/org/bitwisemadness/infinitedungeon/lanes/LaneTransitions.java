package org.bitwisemadness.infinitedungeon.lanes;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The physical layer transition (plan section 6; amendment C2c/#74: portals
 * are the traversal backbone - issue #31).
 *
 * <p>Each lane cell whose dungeon generated carries a portal pair on its
 * candidate chunk: a departure frame on the chunk's east edge standing on the
 * layer's deepest floor, and an arrival frame on the west edge standing on the
 * layer's entry band (floor 0, top-anchored - issue #183's axis, so the dive
 * loop reads: descend a layer, transition east, arrive at the next layer's
 * entrance, descend again). Both slots derive from the effective placement and
 * the world seed - stateless, seed-derived, Chunky-identical (section 16) - so
 * a lane hop is a position-to-position lookup with no per-player state: the
 * overworld anchor system (section 4) is never touched.</p>
 *
 * <p>The ring of a frame reaches one block below its interior, so both
 * interior bases stay at least one block above the world bottom. Sealing is
 * the callers' job ({@link LayerUnlocks}); this class stays seal-agnostic so
 * travel, ignition, and tests share one classifier. Slot interiors sit fully
 * inside the candidate chunk (x offsets 1..14 plus the ring stay within the
 * 16-block chunk), so generation never writes across chunk borders.</p>
 */
public final class LaneTransitions {

    /** Interior lower-left cells, as offsets in the candidate chunk. */
    private static final int ARRIVAL_INTERIOR_X = 1;
    private static final int DEPARTURE_INTERIOR_X = 13;
    private static final int INTERIOR_Z = 8;

    /** Interior span: 2 wide along X, 3 tall (the frame builder's minimum). */
    private static final int INTERIOR_WIDTH = 2;
    private static final int INTERIOR_HEIGHT = 3;

    private LaneTransitions() {
    }

    /**
     * The portal position a lane-portal walk leads to, or empty when the
     * position is not a transition slot or the placement is unreadable.
     * Departures pair forward - layer 1's departure leads into layer 2, and
     * so on down - and arrivals pair back: layer L's arrival returns to layer
     * L-1's departure.
     *
     * <p>Layer 1's arrival is deliberately <b>not</b> a pair (issue #246):
     * the lobby is the Overworld entrance site, so there is no lane 0 to
     * pair back to. The empty target routes that walk onto the anchor policy
     * - the dimension's exit gate, back out the way the player came in -
     * which also keeps it out of {@link LayerUnlocks}' seal, exactly as the
     * pre-#246 lobby portal (never a transition slot) was. Lane 0 has no
     * arrival slot at all.</p>
     *
     * <p>Position math only: callers gate on the dungeon dimension first
     * (travel and ignition both do) - the gate lives there so the derivation
     * itself stays testable in the GameTest harness, which can never host the
     * dungeon dimension.</p>
     */
    public static Optional<BlockPos> pairTarget(ServerLevel level, BlockPos portalPos) {
        var placement = LaneGrid.placement();
        if (placement == null) {
            return Optional.empty();
        }
        int chunkX = portalPos.getX() >> 4;
        int chunkZ = portalPos.getZ() >> 4;
        long seed = level.getSeed();
        ChunkPos cell = placement.getPotentialStructureChunk(seed, chunkX, chunkZ);
        if (cell.x != chunkX || cell.z != chunkZ) {
            return Optional.empty();
        }
        int lane = LaneGeometry.laneIndex(cell.getMiddleBlockX(), LaneGrid.laneStride());
        if (lane < 0) {
            return Optional.empty();
        }
        int minBuild = level.getMinBuildHeight();
        int available = level.getHeight();

        var departure = departureInterior(minBuild, cell);
        if (insideInterior(portalPos, departure)) {
            return partnerCell(placement, seed, lane + 1, cell)
                    .map(partner -> arrivalInterior(minBuild, available,
                            floorsOf(seed, lane + 1), partner));
        }
        // Layer 1's arrival is the exit gate (#246), not a lane pair - only
        // layers 2+ serve arrival-side walks back to their predecessor.
        if (lane >= 2) {
            var arrival = arrivalInterior(minBuild, available, floorsOf(seed, lane), cell);
            if (insideInterior(portalPos, arrival)) {
                return partnerCell(placement, seed, lane - 1, cell)
                        .map(partner -> departureInterior(minBuild, partner));
            }
        }
        return Optional.empty();
    }

    /**
     * Where an Overworld portal walk materialises the visitor: layer 1's
     * canonical (rz-0) arrival interior - the same slot a lane hop targets,
     * since issue #246 makes the lobby the Overworld entrance site and the
     * dimension's content starts at layer 1. Empty when the placement is
     * unreadable; callers then leave the player standing rather than dropping
     * them into the void.
     *
     * <p>Like every lane-hop landing, the target sits inside the arrival
     * portal plane (portal cooldown guards the re-trigger); the frame is
     * built lit for layer 1 so the way home never needs a first light.</p>
     */
    public static Optional<BlockPos> descentTarget(ServerLevel level) {
        var placement = LaneGrid.placement();
        if (placement == null) {
            return Optional.empty();
        }
        // Layer 1, first Z-region: the rz-0 canonical cell the waystone uses.
        ChunkPos cell = placement.getPotentialStructureChunk(
                level.getSeed(), placement.spacing(), 0);
        return Optional.of(arrivalInterior(level.getMinBuildHeight(),
                level.getHeight(), floorsOf(level.getSeed(), 1), cell));
    }

    /** The departure slot's interior base: one ring block above the floor plane. */
    public static BlockPos departureInterior(int minBuild, ChunkPos cell) {
        return new BlockPos(cell.getBlockX(DEPARTURE_INTERIOR_X),
                Math.max(minBuild + 1, FloorSchedule.deepestFloorBaseY(minBuild)),
                cell.getBlockZ(INTERIOR_Z));
    }

    /** The arrival slot's interior base: the entry band of the arriving layer. */
    public static BlockPos arrivalInterior(int minBuild, int available, int floors, ChunkPos cell) {
        return new BlockPos(cell.getBlockX(ARRIVAL_INTERIOR_X),
                Math.max(minBuild + 1, FloorSchedule.entryFloorBaseY(minBuild, available, floors)),
                cell.getBlockZ(INTERIOR_Z));
    }

    /** Whether pos sits inside the given slot's 2x3 interior plane. */
    public static boolean insideInterior(BlockPos pos, BlockPos interior) {
        int dx = pos.getX() - interior.getX();
        int dy = pos.getY() - interior.getY();
        return dx >= 0 && dx < INTERIOR_WIDTH
                && dy >= 0 && dy < INTERIOR_HEIGHT
                && pos.getZ() == interior.getZ();
    }

    /** The candidate cell of the neighbouring lane at the same Z region. */
    private static Optional<ChunkPos> partnerCell(
            net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement placement,
            long seed, int partnerLane, ChunkPos cell) {
        int spacing = placement.spacing();
        ChunkPos partner = placement.getPotentialStructureChunk(
                seed, partnerLane * spacing, cell.z);
        // partnerLane is already a region index (lanes ARE region columns),
        // so the floored lookup lands exactly there.
        return Optional.of(partner);
    }

    private static int floorsOf(long seed, int layer) {
        return FloorSchedule.floorsInLayer(seed, layer,
                DungeonConfigs.floorsPerLayerMin(), DungeonConfigs.floorsPerLayerMax());
    }
}
