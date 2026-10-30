package org.bitwisemadness.infinitedungeon.portal;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The lobby skeleton (issue #131): the fixed-origin entrance, now generated
 * at chunk time instead of built on first arrival.
 *
 * <p>Filter: the origin chunk must be the fixed chunk (chunkX 0, chunkZ 0) -
 * fixed construction means identical in every world, so the shape is
 * seed-independent by definition. There the feature builds exactly what
 * {@link DungeonEntrance} names: the platform of the configured frame block
 * at y 63 (x 0..4, z 0..2), then the shared minimal portal, lit, at the
 * arrival cell. This closes the recorded Wave-1 deviation: pregenerated and
 * organic worlds carry the lobby from the first tick, and the travel and
 * death-return paths find it by lookup ({@link DungeonEntrance#ensure} and
 * {@link DungeonEntrance#lobby} never build).</p>
 *
 * <p>Every write stays inside the fixed chunk - the platform extends south
 * of the portal plane only - because worldgen features must not write across
 * chunk borders (the same principle the transition frames document); the
 * arrival and landing constants are untouched by the bound. The frame
 * builder validates before placing and refuses whole when the space is
 * blocked - never half-built - in which case the lobby is absent and the
 * callers' documented fallbacks apply.</p>
 */
public final class LobbyEntranceFeature extends Feature<NoneFeatureConfiguration> {

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, InfiniteDungeon.MOD_ID);

    /** The registered feature: {@code infinite_dungeon:lobby_entrance}. */
    public static final Supplier<LobbyEntranceFeature> LOBBY_ENTRANCE =
            FEATURES.register("lobby_entrance", LobbyEntranceFeature::new);

    private LobbyEntranceFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** Registers the feature on the mod bus; called from the mod constructor. */
    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var origin = context.origin();
        if ((origin.getX() >> 4) != 0 || (origin.getZ() >> 4) != 0) {
            return false; // the lobby exists exactly once: the fixed chunk
        }
        var level = context.level();
        var frame = DungeonConfigs.frameBlock().defaultBlockState();
        // Platform: x 0..4, z 0..2 at y 63 - the Wave-1 shape shifted to stay
        // inside the fixed chunk, so the travel constants keep working and
        // generation never writes across a chunk border.
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 2; z++) {
                BlockPos under = new BlockPos(x, 63, z);
                if (level.getBlockState(under).isAir()) {
                    level.setBlock(under, frame, 3);
                }
            }
        }
        return PortalFrameBuilder.build(level, DungeonEntrance.ARRIVAL, Direction.Axis.X);
    }
}
