package org.bitwisemadness.infinitedungeon.lanes;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * The fallback hall: an anti-dead-end guard for lane cells whose Dungeon
 * Crawl dungeon did not generate (plan section 3, issue #30).
 *
 * <p>Placement is a worldgen feature for one structural reason: features run
 * after structure starts are computed, so at the cell's candidate chunk - the
 * one deterministic random_spread position of the lane set - the check sees
 * the cell's own state locally, with no cross-chunk visibility problems and
 * no visitor-triggered edits (section 16: generation-time, Chunky-identical).
 * Every chunk passes through; only the cell's candidate chunk proceeds, lane 0
 * stays reserved for the fixed-construction lobby (#131), and a present,
 * valid dungeon start means the lane has its dungeon already - nothing to
 * guard.</p>
 *
 * <p>The hall occupies the envelope a dungeon would have embedded into: a 9x9
 * stone-brick room whose floor sits at the base plane (world bottom + 21,
 * where the void mixin puts Dungeon Crawl's base) and whose ceiling stops one
 * block below the ground plane (world bottom + 45) - inside the build height
 * by the same condition that gates dungeon generation. One seeded side gets a
 * doorway; vanilla blocks only (the art rule), single piece, no loot (section
 * 11: halls are guards, not content).</p>
 */
public final class FallbackHallFeature extends Feature<NoneFeatureConfiguration> {

    /** The structure whose absence this guard answers. */
    private static final ResourceLocation DUNGEON_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath("dungeoncrawl", "dungeon");

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, InfiniteDungeon.MOD_ID);

    /** The registered feature: {@code infinite_dungeon:fallback_hall}. */
    public static final Supplier<FallbackHallFeature> FALLBACK_HALL =
            FEATURES.register("fallback_hall", FallbackHallFeature::new);

    /** Dungeon Crawl's own constants: ground = bottom + 45, base = ground - 24. */
    private static final int GROUND_ABOVE_BOTTOM = 45;
    private static final int DUNGEON_EMBED_DEPTH = 24;

    /** The room spans [center-4, center+4] on both axes (9x9). */
    private static final int HALF_SIZE = 4;

    private FallbackHallFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** Registers the feature on the mod bus; called from the mod constructor. */
    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var lanePlacement = LaneGrid.placement();
        if (lanePlacement == null) {
            return false;
        }
        int stride = LaneGrid.laneStride();
        var level = context.level();
        int minBuild = level.getMinBuildHeight();
        int ground = minBuild + GROUND_ABOVE_BOTTOM;
        if (ground > level.getMaxBuildHeight()) {
            // The dimension is shorter than the room the guard protects -
            // same condition as the void mixin's ruling (#174): no generation.
            return false;
        }

        // The cell's candidate chunk, exactly as the lane set computes it.
        var origin = context.origin();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        // Vanilla passes the chunk coords themselves; the call floors them
        // into regions internally (RandomSpreadStructurePlacement bytecode).
        var candidate = lanePlacement.getPotentialStructureChunk(
                level.getSeed(), chunkX, chunkZ);
        if (candidate.x != chunkX || candidate.z != chunkZ) {
            return false;
        }

        // Lane 0 is the lobby layer (issue #131); halls never generate there.
        if (LaneGeometry.laneIndex(candidate.getMiddleBlockX(), stride) < 1) {
            return false;
        }

        // The cell's dungeon: starts are computed before features run, and the
        // candidate chunk is where its start lands - so this is a local check.
        var chunk = level.getChunk(candidate.x, candidate.z);
        var dungeon = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(DUNGEON_STRUCTURE);
        if (dungeon != null) {
            var start = chunk.getStartForStructure(dungeon);
            if (start != null && start.isValid()) {
                return false;
            }
        }

        build(level, candidate, level.getSeed(), minBuild + GROUND_ABOVE_BOTTOM - DUNGEON_EMBED_DEPTH, ground);
        return true;
    }

    /** The room: full floor and ceiling, perimeter walls, one seeded doorway. */
    private static void build(WorldGenLevel level, ChunkPos cell, long seed, int floorY, int ground) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        int centerX = cell.getMiddleBlockX();
        int centerZ = cell.getMiddleBlockZ();
        // Seeded but stable across JVMs: plain long mixing, no library hash.
        long mixed = seed + cell.x * 0x9E3779B97F4A7C15L + cell.z * 0xC2B2AE3D27D4EB4FL;
        int side = (int) Math.floorMod(mixed, 4L); // 0 north, 1 east, 2 south, 3 west

        for (int dx = -HALF_SIZE; dx <= HALF_SIZE; dx++) {
            for (int dz = -HALF_SIZE; dz <= HALF_SIZE; dz++) {
                boolean wall = Math.abs(dx) == HALF_SIZE || Math.abs(dz) == HALF_SIZE;
                int x = centerX + dx;
                int z = centerZ + dz;
                level.setBlock(new BlockPos(x, floorY, z), stone, 2);
                level.setBlock(new BlockPos(x, ground - 1, z), stone, 2);
                if (!wall) {
                    continue;
                }
                boolean doorway = isDoorwayCell(dx, dz, side);
                for (int y = floorY + 1; y <= ground - 2; y++) {
                    if (doorway && y <= floorY + 3) {
                        continue; // the seeded side stays open
                    }
                    level.setBlock(new BlockPos(x, y, z), stone, 2);
                }
            }
        }
    }

    private static boolean isDoorwayCell(int dx, int dz, int side) {
        return switch (side) {
            case 0 -> dz == -HALF_SIZE && dx == 0; // north face, center
            case 1 -> dx == HALF_SIZE && dz == 0;  // east face, center
            case 2 -> dz == HALF_SIZE && dx == 0;  // south face, center
            default -> dx == -HALF_SIZE && dz == 0; // west face, center
        };
    }
}
