package org.bitwisemadness.infinitedungeon.spawn;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.slf4j.Logger;

/**
 * The interior-bounds gate (issue #254, spike #230's verified seam): natural
 * spawns in the dungeon dimension are denied when their position sits at or
 * above its column's surface or lacks a sturdy floor - the rule's why lives
 * on {@link InteriorRule}.
 *
 * <p>The listener is a thin adapter: it reads the facts (config, spawn type,
 * dimension, exposure, support), hands them to the pure rule, and sets
 * {@code FAIL} only when the rule denies. Every other path - event spawns,
 * other dimensions, the toggle off - leaves the result untouched, so scripted
 * raids ({@link EventSpawnSanity}) and vanilla coexistence are intact by
 * construction.</p>
 */
public final class InteriorSpawnGate {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Test seam: forces the scope check to a dimension. Null (production)
     * reads {@link DungeonDimensions#LEVEL}.
     */
    private static ResourceKey<Level> scopeOverrideForTests;

    private InteriorSpawnGate() {
    }

    /** One spawn's position check, on the server event bus. */
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        ServerLevel level = event.getLevel().getLevel();
        boolean natural = event.getSpawnType() == MobSpawnType.NATURAL;
        BlockPos position = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        BlockPos below = position.below();
        // Exposure is the surface-heightmap comparison, not light-engine sky
        // visibility: heightmaps update synchronously with the block that
        // caused them, so a fresh roof is respected on the same tick.
        boolean exposed = position.getY()
                >= level.getHeight(Heightmap.Types.WORLD_SURFACE, position.getX(), position.getZ());
        boolean supported = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
        if (!InteriorRule.denies(DungeonConfigs.spawnsInteriorOnly(), natural, inScope(level),
                exposed, supported)) {
            return;
        }
        event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        LOGGER.debug("Denied an exterior natural spawn at {} in {}: {}",
                position, level.dimension().location(),
                exposed ? "at or above the column surface" : "no sturdy floor below");
    }

    /** Whether this level is the gated dimension (the test seam wins when set). */
    static boolean inScope(ServerLevel level) {
        ResourceKey<Level> forced = scopeOverrideForTests;
        return forced != null
                ? forced.equals(level.dimension())
                : DungeonDimensions.isDungeon(level);
    }

    /**
     * Forces the scope check to a dimension (gametests; the harness is not the
     * dungeon dimension). Null restores the real constant.
     */
    static void setScopeForTests(ResourceKey<Level> dimension) {
        scopeOverrideForTests = dimension;
    }
}
