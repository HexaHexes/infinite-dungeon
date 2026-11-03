package org.bitwisemadness.infinitedungeon.spawn;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * Interior bounds end to end (issue #254). One test owns the
 * {@link InteriorSpawnGate} scope seam and the config toggle - both static,
 * and vanilla runs batch tests concurrently - so every leg and the restore
 * live here together. Everything is synchronous: the listener is driven with
 * synthetic {@code PositionCheck} events (the {@code EventSpawnSanity}
 * construction), and the ambient pick is called directly, so no delayed chain
 * and no succeed-before-legs hazard (the #252 lesson).
 *
 * <p>The harness dimension is not the dungeon dimension, so the scope seam
 * points at the harness; the production path (the real constant) is
 * covered by the JUnit rule test's dimension leg. The invented box and the
 * probe mob are removed on the happy path; the start-premise restores both
 * seams before anything else.</p>
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InteriorSpawnGameTests {

    private InteriorSpawnGameTests() {
    }

    @GameTest(template = "empty")
    public static void interior_bounds_deny_only_exterior_natural_spawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Start-premise: a prior failed run's leftovers must not decide this run.
        InteriorSpawnGate.setScopeForTests(null);
        DungeonConfigs.setSpawnsInteriorOnly(true);
        Mob mob = EntityType.ZOMBIE.create(level);
        helper.assertTrue(mob != null, "the probe mob must construct");
        BlockPos floorRel = new BlockPos(4, 4, 4);
        BlockPos interiorRel = floorRel.above();
        BlockPos roofRel = floorRel.above(4);
        BlockPos ceilingRel = floorRel.above(3);
        try {
            InteriorSpawnGate.setScopeForTests(level.dimension());
            helper.setBlock(floorRel, Blocks.STONE);
            helper.setBlock(ceilingRel, Blocks.STONE);

            // 1) Interior (sky-hidden, supported): untouched.
            place(helper, mob, interiorRel);
            MobSpawnEvent.PositionCheck interior = event(mob, level, MobSpawnType.NATURAL);
            BlockPos at = helper.absolutePos(interiorRel);
            helper.assertTrue(InteriorSpawnGate.inScope(level),
                    "the scope seam must read the harness as in scope");
            helper.assertTrue(DungeonConfigs.spawnsInteriorOnly(), "the toggle must be on");
            helper.assertTrue(Math.floor(interior.getX()) == at.getX()
                            && Math.floor(interior.getY()) == at.getY(),
                    "the event must carry the mob's position; event=" + interior.getX() + ","
                            + interior.getY() + " expected=" + at);
            helper.assertTrue(level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ())
                            > at.getY(),
                    "the box interior must sit below the column surface; surface="
                            + level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()));
            helper.assertTrue(level.getBlockState(at.below()).isFaceSturdy(level, at.below(),
                            Direction.UP),
                    "the box interior must be supported; below=" + level.getBlockState(at.below()));
            InteriorSpawnGate.onPositionCheck(interior);
            helper.assertTrue(interior.getResult() != MobSpawnEvent.PositionCheck.Result.FAIL,
                    "an interior natural spawn must pass; got " + interior.getResult());

            // 2) Roof (sky-visible, supported): denied.
            place(helper, mob, roofRel);
            MobSpawnEvent.PositionCheck roof = event(mob, level, MobSpawnType.NATURAL);
            InteriorSpawnGate.onPositionCheck(roof);
            helper.assertTrue(roof.getResult() == MobSpawnEvent.PositionCheck.Result.FAIL,
                    "a roof natural spawn must be denied; got " + roof.getResult());

            // 3) EVENT at the roof: never denied (scripted raids are code-placed).
            MobSpawnEvent.PositionCheck scripted = event(mob, level, MobSpawnType.EVENT);
            InteriorSpawnGate.onPositionCheck(scripted);
            helper.assertTrue(scripted.getResult() != MobSpawnEvent.PositionCheck.Result.FAIL,
                    "an event spawn must never be denied; got " + scripted.getResult());

            // 4) Toggle off: untouched (the vanilla baseline).
            DungeonConfigs.setSpawnsInteriorOnly(false);
            MobSpawnEvent.PositionCheck off = event(mob, level, MobSpawnType.NATURAL);
            InteriorSpawnGate.onPositionCheck(off);
            helper.assertTrue(off.getResult() != MobSpawnEvent.PositionCheck.Result.FAIL,
                    "the toggle off must restore vanilla behaviour");
            DungeonConfigs.setSpawnsInteriorOnly(true);

            // 5) Out of scope (another dimension): untouched. The harness runs
            // in the overworld, so point the scope at a dimension the harness
            // is not - and name the harness dimension if that ever fails.
            InteriorSpawnGate.setScopeForTests(Level.NETHER);
            helper.assertTrue(!InteriorSpawnGate.inScope(level),
                    "the harness must not be the nether; dimension="
                            + level.dimension().location());
            MobSpawnEvent.PositionCheck elsewhere = event(mob, level, MobSpawnType.NATURAL);
            InteriorSpawnGate.onPositionCheck(elsewhere);
            helper.assertTrue(elsewhere.getResult() != MobSpawnEvent.PositionCheck.Result.FAIL,
                    "another dimension must be untouched; dimension="
                            + level.dimension().location());
            InteriorSpawnGate.setScopeForTests(level.dimension());

            // 6) The ambient pick finds the interior floor air, not the roof.
            BlockPos column = helper.absolutePos(interiorRel);
            OptionalInt picked = AmbientSpawner.interiorSpawnY(level, column.getX(), column.getZ());
            helper.assertTrue(picked.isPresent() && picked.getAsInt() == column.getY(),
                    "the ambient pick must choose the interior floor; got " + picked);

            // 7) A bare column has no interior: it spawns nothing.
            BlockPos bare = helper.absolutePos(new BlockPos(12, 4, 12));
            OptionalInt none = AmbientSpawner.interiorSpawnY(level, bare.getX(), bare.getZ());
            helper.assertTrue(none.isEmpty(),
                    "a roof-less column must yield no spawn height; got " + none);

            // Happy-path restore.
            helper.setBlock(floorRel, Blocks.AIR);
            helper.setBlock(ceilingRel, Blocks.AIR);
            mob.discard();
        } finally {
            InteriorSpawnGate.setScopeForTests(null);
            DungeonConfigs.setSpawnsInteriorOnly(true);
        }
        helper.succeed();
    }

    /** Positions the probe mob at an absolute height for the relative cell. */
    private static void place(GameTestHelper helper, Mob mob, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        mob.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
    }

    /** The synthetic position-check event, the scripted-spawn construction. */
    private static MobSpawnEvent.PositionCheck event(Mob mob, ServerLevel level, MobSpawnType type) {
        return new MobSpawnEvent.PositionCheck(mob, level, type, null);
    }
}
