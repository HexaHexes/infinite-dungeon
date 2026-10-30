package org.bitwisemadness.infinitedungeon.depth;

import net.minecraft.server.level.ServerLevel;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;

/**
 * Everything the depth derivation needs, gathered once per server (plan
 * section 3: lanes follow the effective placement, floors follow the config,
 * bands follow the dimension).
 *
 * <p>The pure derivation takes this record rather than reaching for world or
 * config state, so {@link DungeonLayout} stays a pure function of its inputs -
 * the same reason the curves are pure (section 7). Downstream waves build it
 * once through {@link #forServer} and pass it along.</p>
 *
 * @param worldSeed the world seed; floors derive from it with the layer index
 * @param laneStride the effective lane stride in blocks (from {@link LaneGrid})
 * @param minY the dimension's lowest block (locked at -64 by the plan)
 * @param availableHeight the dimension's build height (locked at 2048)
 * @param floorsMin the configured fewest floors per layer
 * @param floorsMax the configured most floors per layer
 */
public record LayoutParameters(long worldSeed, int laneStride, int minY, int availableHeight,
        int floorsMin, int floorsMax) {

    public LayoutParameters {
        if (laneStride < 1) {
            throw new IllegalArgumentException("laneStride must be positive, got " + laneStride);
        }
        if (availableHeight < 1) {
            throw new IllegalArgumentException("availableHeight must be positive, got " + availableHeight);
        }
        if (floorsMin < 1 || floorsMax < 1) {
            throw new IllegalArgumentException(
                    "floor counts must be at least 1, got [" + floorsMin + ", " + floorsMax + "]");
        }
    }

    /**
     * The parameters of a running server: the world seed, the effective lane
     * stride, the dimension's own vertical extents, and the floor range from
     * the config. Every later wave resolves its layout facts through this.
     */
    public static LayoutParameters forServer(ServerLevel level) {
        return new LayoutParameters(
                level.getSeed(),
                LaneGrid.laneStride(),
                level.getMinBuildHeight(),
                level.getHeight(),
                DungeonConfigs.floorsPerLayerMin(),
                DungeonConfigs.floorsPerLayerMax());
    }
}
