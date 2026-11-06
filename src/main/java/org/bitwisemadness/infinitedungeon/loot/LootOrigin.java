package org.bitwisemadness.infinitedungeon.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.bitwisemadness.infinitedungeon.depth.DifficultyCurve;
import org.bitwisemadness.infinitedungeon.depth.DungeonLayout;
import org.bitwisemadness.infinitedungeon.depth.DungeonPosition;
import org.bitwisemadness.infinitedungeon.depth.LayoutParameters;
import org.bitwisemadness.infinitedungeon.depth.ProfileCurves;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;

/**
 * Resolves the difficulty D at a loot context's origin (issue #51), the shared
 * fact the depth-scaled function and the depth-chance condition read.
 *
 * <p>Returns {@link Double#NaN} when the origin is not inside the dungeon
 * dimension (or is west of the dungeon), so a table using these pieces stays a
 * harmless no-op anywhere else.</p>
 */
final class LootOrigin {

    private LootOrigin() {
    }

    static double difficultyAt(LootContext context) {
        ServerLevel level = context.getLevel();
        if (!DungeonDimensions.isDungeon(level)) {
            return Double.NaN;
        }
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return Double.NaN;
        }
        BlockPos position = BlockPos.containing(origin);
        LayoutParameters parameters = LayoutParameters.forServer(level);
        if (!DungeonLayout.inDungeon(parameters, position.getX())) {
            return Double.NaN;
        }
        DungeonPosition depth = DungeonLayout.derive(parameters, position.getX(), position.getY());
        double[] coefficients =
                ProfileCurves.difficulty(level.dimension().location().toString());
        return DifficultyCurve.difficulty(depth.layerIndex(), depth.floorInLayer(),
                coefficients[0], coefficients[1], coefficients[2]);
    }
}
