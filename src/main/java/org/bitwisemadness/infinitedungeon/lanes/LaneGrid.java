package org.bitwisemadness.infinitedungeon.lanes;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * The lane stride, read at startup from the effective registry values.
 *
 * <p>The lanes follow Dungeon Crawl's natural placement grid: the lane set's
 * spacing in chunks, in blocks. The value is read from the FROZEN registry
 * rather than hardcoded, so a load-time rewrite by a structure mod (Sparse
 * Structures scales spacing by its config factor on datapack-registry load -
 * section 16, issue #155) cannot misalign the lanes: whatever spacing the
 * lane set actually generates with becomes the stride every consumer derives
 * from.</p>
 *
 * <p>The read runs on {@code ServerStartedEvent}, after datapack registries
 * are frozen, and falls back to the shipped grid with a loud warning if the
 * set is missing or rewritten to a non-random-spread placement - the lanes
 * must never silently diverge from where dungeons actually generate.</p>
 */
public final class LaneGrid {

    /** The lane set this mod ships (data/infinite_dungeon/worldgen/structure_set/lanes.json). */
    public static final ResourceLocation LANE_SET_ID =
            ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, "lanes");

    /**
     * The shipped grid, spacing 32 chunks (Dungeon Crawl 2.3.17's placement,
     * per the Phase 0 spike): 512 blocks. Fallback and log reference only -
     * the effective value always comes from the registry read.
     */
    public static final int SHIPPED_LANE_STRIDE = 512;

    private static volatile int laneStride = SHIPPED_LANE_STRIDE;

    /**
     * The full effective placement (spacing, separation, salt), kept so the
     * fallback-hall filter computes the exact cell candidates Dungeon Crawl's
     * structure would. Null when the set is missing or non-random-spread -
     * consumers skip rather than guess.
     */
    private static volatile RandomSpreadStructurePlacement placement;

    private LaneGrid() {
    }

    /** Wires the startup read; called from the mod constructor. */
    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(LaneGrid::onServerStarted);
    }

    /** The effective lane stride in blocks for the running server. */
    public static int laneStride() {
        return laneStride;
    }

    /** The effective lane placement, or null if it could not be read. */
    public static RandomSpreadStructurePlacement placement() {
        return placement;
    }

    /** The startup read: the frozen registry is the source of truth. */
    public static void onServerStarted(ServerStartedEvent event) {
        update(event.getServer().registryAccess()
                .registryOrThrow(Registries.STRUCTURE_SET)
                .get(LANE_SET_ID));
    }

    /**
     * Reads the effective stride from the lane set's placement. Public so the
     * gametest can drive it directly (the harness's server lifecycle is not
     * assumed to fire the same events a dedicated server does).
     */
    public static void update(StructureSet laneSet) {
        if (laneSet != null && laneSet.placement() instanceof RandomSpreadStructurePlacement spread) {
            placement = spread;
            laneStride = spread.spacing() * 16;
            InfiniteDungeon.LOGGER.info("Lane stride set from the effective lane placement: {} blocks", laneStride);
            return;
        }
        placement = null;
        laneStride = SHIPPED_LANE_STRIDE;
        InfiniteDungeon.LOGGER.warn(
                "The lane set is missing or no longer random_spread; lanes fall back to the shipped stride of {} blocks",
                SHIPPED_LANE_STRIDE);
    }
}
