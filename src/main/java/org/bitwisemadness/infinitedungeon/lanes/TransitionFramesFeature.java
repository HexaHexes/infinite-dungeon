package org.bitwisemadness.infinitedungeon.lanes;

import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.portal.PortalFrameBuilder;

/**
 * The layer-transition portal frames (plan section 6, amendment C2c/#74 -
 * issue #31): at every lane cell's candidate chunk - the same filter the
 * fallback hall uses, minus the dungeon-presence skip, because transitions
 * exist wherever the cell does - this builds the pair's two frames unlit:
 * the departure on the east edge (deepest floor) and the arrival on the west
 * edge (entry band). The sealed/unsealed ignition gate owns the first light.
 *
 * <p>Frames stay fully inside the candidate chunk (the builder's ring reaches
 * x0..x15 from the interior offsets), so generation never crosses a chunk
 * border, and both interiors sit at least one ring-block above the world
 * bottom. Lane 0 stays reserved for the lobby (#131), which owns the lobby's
 * own exits - layer 1's arrival exists but its back-target waits for them.</p>
 */
public final class TransitionFramesFeature extends Feature<NoneFeatureConfiguration> {

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, InfiniteDungeon.MOD_ID);

    /** The registered feature: {@code infinite_dungeon:transition_frames}. */
    public static final Supplier<TransitionFramesFeature> TRANSITION_FRAMES =
            FEATURES.register("transition_frames", TransitionFramesFeature::new);

    private TransitionFramesFeature() {
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
        int stride = LaneGrid.laneStride();
        var level = context.level();
        int minBuild = level.getMinBuildHeight();
        if (minBuild + 45 > level.getMaxBuildHeight()) {
            // The same build-height condition that gates the dungeons these
            // frames connect (#174's ruling): no room, no transitions.
            return false;
        }

        var origin = context.origin();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        var candidate = placement.getPotentialStructureChunk(level.getSeed(), chunkX, chunkZ);
        if (candidate.x != chunkX || candidate.z != chunkZ) {
            return false;
        }
        int lane = LaneGeometry.laneIndex(candidate.getMiddleBlockX(), stride);
        if (lane < 1) {
            return false;
        }
        int available = level.getHeight();
        int floors = FloorSchedule.floorsInLayer(level.getSeed(), lane,
                DungeonConfigs.floorsPerLayerMin(), DungeonConfigs.floorsPerLayerMax());

        boolean departure = PortalFrameBuilder.build(level,
                LaneTransitions.departureInterior(minBuild, candidate),
                Direction.Axis.X, false);
        boolean arrival = PortalFrameBuilder.build(level,
                LaneTransitions.arrivalInterior(minBuild, available, floors, candidate),
                Direction.Axis.X, false);
        return departure || arrival;
    }
}
