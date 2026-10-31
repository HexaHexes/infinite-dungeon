package org.bitwisemadness.infinitedungeon.lanes;

import java.util.function.Supplier;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The layer-entrance Waystone (plan section 6, issue #68): one per layer,
 * named "Layer N", placed during chunk generation - seed-derived at the
 * candidate cell, Chunky-identical (section 16), no visitor edits.
 *
 * <p>Exactly one per layer, because the waystones are the fast-travel anchors
 * of amendment C2c and the teleport list must not show duplicate "Layer N"
 * entries: the chosen cell is the layer's rz-0 canonical entrance (the first
 * Z-region's candidate), while every cell's transition arrival remains a
 * walkable entrance without one. Placement sits three blocks south of the
 * arrival interior - where a portal traveler steps out - clear of the
 * planar frame.</p>
 *
 * <p>{@code WaystonesAPI.placeWaystone} is chunk-generation-safe by
 * construction (bytecode-verified on the pinned jar: two setBlocks and
 * block-entity init, no manager/database/packet writes - see #66), and the
 * name lands through {@link MutableWaystone#setName} into the block entity,
 * so it persists with the chunk itself.</p>
 */
public final class WaystoneEntranceFeature extends Feature<NoneFeatureConfiguration> {

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, InfiniteDungeon.MOD_ID);

    /** The registered feature: {@code infinite_dungeon:waystone_entrance}. */
    public static final Supplier<WaystoneEntranceFeature> WAYSTONE_ENTRANCE =
            FEATURES.register("waystone_entrance", WaystoneEntranceFeature::new);

    /** Where the waystone stands relative to the arrival interior. */
    private static final int OFFSET_Z = 3;

    private WaystoneEntranceFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** Registers the feature on the mod bus; called from the mod constructor. */
    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var placement = LaneGrid.placement();
        if (placement == null) {
            return false;
        }
        var level = context.level();
        int minBuild = level.getMinBuildHeight();
        if (minBuild + 45 > level.getMaxBuildHeight()) {
            // Same build-height condition as the dungeons and frames (#174).
            return false;
        }

        var origin = context.origin();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        var candidate = placement.getPotentialStructureChunk(level.getSeed(), chunkX, chunkZ);
        if (candidate.x != chunkX || candidate.z != chunkZ) {
            return false;
        }
        int stride = LaneGrid.laneStride();
        int lane = LaneGeometry.laneIndex(candidate.getMiddleBlockX(), stride);
        if (lane < 1) {
            return false; // lane 0 is the lobby's home (#246: now Overworld)
        }
        if (Math.floorDiv(candidate.z, placement.spacing()) != 0) {
            return false; // one waystone per layer: the rz-0 entrance cell
        }

        int floors = FloorSchedule.floorsInLayer(level.getSeed(), lane,
                DungeonConfigs.floorsPerLayerMin(), DungeonConfigs.floorsPerLayerMax());
        var spot = LaneTransitions.arrivalInterior(minBuild, level.getHeight(), floors, candidate)
                .offset(0, 0, OFFSET_Z);
        // The recipe writes unconditionally, so the two-block space is
        // validated first - never half-overwrite what stands there.
        if (!level.getBlockState(spot).canBeReplaced()
                || !level.getBlockState(spot.above()).canBeReplaced()) {
            return false;
        }
        // Mirrors WaystonesAPI.placeWaystone's recipe - its bytecode, member
        // for member - because the API takes a Level while features carry a
        // WorldGenLevel, and every member touched here is public: a Waystones
        // refactor that renames one fails this compile, never silently. The
        // origin differs from the API's hardcoded PLAYER on purpose - these
        // are dungeon-generated entrances, what WaystoneOrigin.DUNGEON exists
        // for (and the chunk-entity init is what registers the waystone;
        // #66 verified placeWaystone itself does no database writes).
        Block block = BuiltInRegistries.BLOCK.get(WaystoneStyles.DEFAULT.getBlockRegistryName());
        level.setBlock(spot, block.defaultBlockState()
                .setValue(WaystoneBlockBase.HALF, DoubleBlockHalf.LOWER)
                .setValue(WaystoneBlockBase.ORIGIN, WaystoneOrigin.DUNGEON), 3);
        level.setBlock(spot.above(), block.defaultBlockState()
                .setValue(WaystoneBlockBase.HALF, DoubleBlockHalf.UPPER)
                .setValue(WaystoneBlockBase.ORIGIN, WaystoneOrigin.DUNGEON), 3);
        if (!(level.getBlockEntity(spot) instanceof WaystoneBlockEntityBase base)) {
            return false;
        }
        base.initializeWaystone(level, null, WaystoneOrigin.DUNGEON);
        if (level.getBlockEntity(spot.above()) instanceof WaystoneBlockEntityBase upperEntity) {
            upperEntity.initializeFromBase(base);
        }
        if (base.getWaystone() instanceof MutableWaystone mutable) {
            mutable.setName(Component.literal("Layer " + lane));
        }
        return true;
    }
}
