package org.bitwisemadness.infinitedungeon.mixins;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xiroc.dungeoncrawl.dungeon.Dungeon;

/**
 * Lets Dungeon Crawl's dungeon structure generate in void worlds (maintainer
 * ruling, issue #174; the one sanctioned mixin exception in agents/REPO.md).
 *
 * <p>Dungeon Crawl rejects any spot with less than 45 blocks of terrain room
 * between the surface and the world bottom - its dungeon embeds 24 blocks
 * below the lowest corner. In a void dimension every column measures zero
 * room, so no dungeon ever generated and the lanes carried nothing. The two
 * redirects below clamp the measured heights up to {@code minBuildHeight +
 * 45}, Dungeon Crawl's own constant, which reproduces flat-overworld geometry
 * at the world bottom: ground anchor and lowest corner both at {@code minY +
 * 45}, the dungeon's base at {@code minY + 21}, room exactly 45. Everything
 * lands inside the dimension's build height, which is the ruling's condition -
 * enforced by the guard: a dimension shorter than 45 blocks keeps the original
 * rejection instead.</p>
 *
 * <p>Provable no-op everywhere else: only ground below {@code minY + 45} is
 * clamped, so the overworld, the Nether (its surfaces sit far above the
 * threshold and the check passes today) and End islands keep their exact
 * current placement. The only affected columns are genuinely void ones,
 * including the End's off-island void, where a floating dungeon is in-bounds
 * and End-flavoured - accepted on the ruling issue.</p>
 *
 * <p>The mixin config sets {@code defaultRequire: 1}, so a Dungeon Crawl
 * refactor that renames either target method fails the build loudly instead
 * of silently regressing the lanes.</p>
 */
@Mixin(Dungeon.class)
public abstract class DungeonVoidPlacement {

    /** Dungeon Crawl's own minimum room between surface and world bottom. */
    private static final int MIN_GROUND_ABOVE_BOTTOM = 45;

    /**
     * The ground anchor: in a void column the surface measures at (or below)
     * the world bottom, so lift it - and with it the dungeon's entrance - to
     * the minimum viable ground inside the build height.
     */
    @Redirect(
            method = "findGenerationPoint",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;"
                            + "getFirstOccupiedHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;"
                            + "Lnet/minecraft/world/level/LevelHeightAccessor;"
                            + "Lnet/minecraft/world/level/levelgen/RandomState;)I"))
    private int infiniteDungeon$clampGround(ChunkGenerator generator, int x, int z,
            Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        return clampToVoidGround(generator.getFirstOccupiedHeight(x, z, type, height, random), height);
    }

    /**
     * The lowest corner that drives the room check: clamping it makes the
     * {@code room < 45} rejection pass naturally in void columns, with no
     * branch or constant touched, and keeps the dungeon's base inside the
     * world ({@code corner - 24 >= minY}).
     */
    @Redirect(
            method = "lowestCornerHeight",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;"
                            + "getFirstFreeHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;"
                            + "Lnet/minecraft/world/level/LevelHeightAccessor;"
                            + "Lnet/minecraft/world/level/levelgen/RandomState;)I"))
    private static int infiniteDungeon$clampCorner(ChunkGenerator generator, int x, int z,
            Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        return clampToVoidGround(generator.getFirstFreeHeight(x, z, type, height, random), height);
    }

    private static int clampToVoidGround(int measured, LevelHeightAccessor height) {
        int floor = height.getMinBuildHeight() + MIN_GROUND_ABOVE_BOTTOM;
        if (floor > height.getMaxBuildHeight()) {
            // The dimension itself is shorter than the dungeon's room: the
            // ruling's condition fails, so the original rejection stands.
            return measured;
        }
        return Math.max(measured, floor);
    }
}
