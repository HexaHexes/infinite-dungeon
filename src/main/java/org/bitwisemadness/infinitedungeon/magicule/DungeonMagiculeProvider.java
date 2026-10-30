package org.bitwisemadness.infinitedungeon.magicule;

import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.depth.DungeonLayout;
import org.bitwisemadness.infinitedungeon.depth.DungeonPosition;
import org.bitwisemadness.infinitedungeon.depth.LayoutParameters;
import org.bitwisemadness.infinitedungeon.depth.MagiculeCurve;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeonapi.magicule.MagiculeProvider;

/**
 * The dungeon dimension's ambient magicule provider (plan section 7, issue
 * #201): the M(L,f) curve registered with the API suite's ambient magicule
 * service, so every consumer - the spawn-safety module, future addons, our own
 * particles (#200) and elevation (#218) - reads concentration through the API
 * contract instead of calling into this mod.
 *
 * <p>Answers only for its own dimension and only server-side, per the
 * {@link MagiculeProvider} contract; everywhere else it returns empty so the
 * service falls through to {@code AmbientMagicule.COLD}. Inside, the answer is
 * depth derivation ({@link DungeonLayout}) composed with the curve
 * ({@link MagiculeCurve}) - both already unit-tested; the composition itself
 * is pinned by {@link DungeonMagiculeProviderTest} through the coefficient-free
 * {@link #concentrationAt} core, keeping config access at the edge (the
 * TravelPolicy rule).</p>
 *
 * <p>Registered once at mod construction (the provider is the mod's, so it
 * lives as long as the mod does); dimension-profile curve overrides ride the
 * same core when profile resolution (#162) lands.</p>
 */
public final class DungeonMagiculeProvider implements MagiculeProvider {

    /** The core's provider instance, registered at mod construction. */
    public static final DungeonMagiculeProvider PROVIDER = new DungeonMagiculeProvider();

    private DungeonMagiculeProvider() {
    }

    @Override
    public OptionalDouble at(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            // The contract is server-side; a client-level query has no answer.
            return OptionalDouble.empty();
        }
        if (!DungeonDimensions.LEVEL.equals(server.dimension())) {
            return OptionalDouble.empty();
        }
        return concentrationAt(LayoutParameters.forServer(server), pos.getX(), pos.getY(),
                DungeonConfigs.magiculeBase(),
                DungeonConfigs.magiculeLinearPerFloor(),
                DungeonConfigs.magiculeQuadraticPerLayer());
    }

    /**
     * The pure core: an in-dungeon position's M(L,f), or empty when the
     * position is not the dungeon's to answer for (west of lane 0, the
     * void-lane rule from {@link DungeonLayout#inDungeon}).
     *
     * @param parameters the layout parameters for the world
     * @param x the world X the lane/depth derivation reads
     * @param y the world Y the floor band derivation reads
     * @param base the curve's origin value ({@code magicule.base})
     * @param linearPerFloor the per-floor increment ({@code magicule.linearPerFloor})
     * @param quadraticPerLayer the per-layer squared increment ({@code magicule.quadraticPerLayer})
     * @return M at the position, or empty when the position is outside the lanes
     */
    public static OptionalDouble concentrationAt(LayoutParameters parameters, int x, int y,
            double base, double linearPerFloor, double quadraticPerLayer) {
        if (!DungeonLayout.inDungeon(parameters, x)) {
            return OptionalDouble.empty();
        }
        DungeonPosition at = DungeonLayout.derive(parameters, x, y);
        return OptionalDouble.of(MagiculeCurve.concentration(
                at.layerIndex(), at.floorInLayer(), base, linearPerFloor, quadraticPerLayer));
    }
}
