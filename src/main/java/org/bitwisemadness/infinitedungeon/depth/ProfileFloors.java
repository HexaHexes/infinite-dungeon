package org.bitwisemadness.infinitedungeon.depth;

import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.lanes.FloorSchedule;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.profile.FloorsPerLayer;

/**
 * The effective floor-count bounds for a dimension (issue #81, Wave 2:
 * every gameworld system resolves its settings from the profile of the
 * current dimension, config values supplying the global defaults).
 *
 * <p>This is THE read path for floors-per-layer: a registered profile's
 * {@code floorsPerLayer} wins, and the config pair
 * ({@link DungeonConfigs#floorsPerLayerMin}/{@code floorsPerLayerMax}) is the
 * default when the dimension has no profile (or its profile does not declare
 * floors). The core dimension currently ships no floors declaration, so this
 * changes nothing until an addon dimension opts in; {@link FloorSchedule}
 * stays the pure (seed, layer) derivation it always was.</p>
 */
public final class ProfileFloors {

    private ProfileFloors() {
    }

    /** The effective bounds for a dimension, config-defaulted when absent. */
    public static FloorsPerLayer of(String dimensionId) {
        return DimensionProfiles.get(dimensionId)
                .flatMap(DimensionProfile::floorsPerLayer)
                .orElseGet(ProfileFloors::configDefaults);
    }

    /**
     * The layer's floor count for a dimension, with the bounds resolved
     * profile-over-config before the seed derivation runs.
     */
    public static int floorsInLayer(String dimensionId, long seed, int layer) {
        FloorsPerLayer bounds = of(dimensionId);
        return FloorSchedule.floorsInLayer(seed, layer, bounds.min(), bounds.max());
    }

    private static FloorsPerLayer configDefaults() {
        return new FloorsPerLayer(DungeonConfigs.floorsPerLayerMin(),
                DungeonConfigs.floorsPerLayerMax());
    }
}
