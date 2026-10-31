package org.bitwisemadness.infinitedungeon.lock;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.LayerUnlocks;

/**
 * The four enforcement hooks end to end (issue #251). One test owns the seam:
 * the protection registry is static and vanilla runs batch tests concurrently,
 * so registration, every hook, and the layer unlock flag are exercised - and
 * cleaned up - here alone, the pattern the transition-seal test established.
 *
 * <p>Layer 99 carries the {@code defeated} lifetime so no other test's seal
 * state is touched, and it is locked first because harness saved data survives
 * between runs. Every probe drives the real NeoForge event objects against
 * real blocks rather than asserting on mocks.</p>
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {

    private ProtectionGameTests() {
    }

    /** Distinct layer used only here; no other test seals or unlocks it. */
    private static final int OWN_LAYER = 99;

    @GameTest(template = "empty")
    public static void protection_holds_all_four_hooks_until_the_layer_defeats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ProtectedBlocks.clear();
        LayerUnlocks.of(level).lock(OWN_LAYER);
        try {
            BlockPos blasted = helper.absolutePos(new BlockPos(2, 4, 2));
            BlockPos pistonTarget = helper.absolutePos(new BlockPos(6, 4, 2));
            BlockPos breakTarget = helper.absolutePos(new BlockPos(2, 4, 6));
            carve(level, blasted);
            carve(level, pistonTarget);
            carve(level, breakTarget);
            ProtectedBlocks.register(level.dimension(),
                    List.of(blasted, pistonTarget, breakTarget), ProtectedBlocks.Until.ALWAYS);

            probeExplosion(helper, level, blasted);
            probePiston(helper, level, pistonTarget);
            probeBreak(helper, level, breakTarget);
            probeMobGrief(helper, level);
            probeDefeatedLifetime(helper, level);
            helper.succeed();
        } finally {
            ProtectedBlocks.clear();
            LayerUnlocks.of(level).lock(OWN_LAYER);
        }
    }

    /** Clears the probe's footprint so blasts and pushes have real space. */
    private static void carve(ServerLevel level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void probeExplosion(GameTestHelper helper, ServerLevel level, BlockPos guarded) {
        BlockPos control = guarded.offset(2, 0, 0);
        level.setBlock(guarded, Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        level.setBlock(control, Blocks.DIRT.defaultBlockState(), 3);

        // Center in air between both blocks, interaction BLOCK: NONE keeps
        // blocks, and a center inside a solid occludes its neighbours through
        // vanilla seen-percent - either one alone let the control survive.
        var blast = level.explode(null, guarded.getX() + 1.5, guarded.getY() + 0.5, guarded.getZ() + 0.5,
                2.0f, false, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(!blast.getToBlow().contains(guarded),
                "the protected block must be removed from the blast, still listed");
        helper.assertTrue(level.getBlockState(guarded).is(Blocks.AMETHYST_BLOCK),
                "the protected block must survive the explosion");
        helper.assertTrue(level.getBlockState(control).isAir(),
                "the unprotected control must be destroyed - harness blast proven");
    }

    private static void probePiston(GameTestHelper helper, ServerLevel level, BlockPos guarded) {
        BlockPos piston = guarded.offset(1, 0, 0);
        level.setBlock(guarded, Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        level.setBlock(piston, Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.WEST), 3);

        var refused = new PistonEvent.Pre(level, piston, Direction.WEST, PistonEvent.PistonMoveType.EXTEND);
        ProtectionListeners.onPiston(refused);
        helper.assertTrue(refused.isCanceled(),
                "a piston push against protected content must be refused");

        BlockPos free = helper.absolutePos(new BlockPos(6, 4, 6));
        carve(level, free);
        level.setBlock(free, Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        level.setBlock(free.offset(1, 0, 0), Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.FACING, Direction.WEST), 3);
        var allowed = new PistonEvent.Pre(level, free.offset(1, 0, 0), Direction.WEST,
                PistonEvent.PistonMoveType.EXTEND);
        ProtectionListeners.onPiston(allowed);
        helper.assertTrue(!allowed.isCanceled(),
                "an unprotected push must pass - control");
    }

    private static void probeBreak(GameTestHelper helper, ServerLevel level, BlockPos guarded) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        level.setBlock(guarded, Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);

        var refused = new BlockEvent.BreakEvent(level, guarded, level.getBlockState(guarded), player);
        ProtectionListeners.onBreak(refused);
        helper.assertTrue(refused.isCanceled(),
                "a player break of protected content must be refused even with a whitelist");

        BlockPos free = helper.absolutePos(new BlockPos(6, 4, 10));
        carve(level, free);
        level.setBlock(free, Blocks.DIRT.defaultBlockState(), 3);
        var allowed = new BlockEvent.BreakEvent(level, free, level.getBlockState(free), player);
        ProtectionListeners.onBreak(allowed);
        helper.assertTrue(!allowed.isCanceled(),
                "an unprotected break must pass - control");
    }

    private static void probeMobGrief(GameTestHelper helper, ServerLevel level) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var event = new EntityMobGriefingEvent(level, player);
        helper.assertTrue(event.canGrief(),
                "mob griefing must start at the gamerule's own value - control");
        ProtectionListeners.onMobGrief(event);
        helper.assertTrue(!event.canGrief(),
                "mob griefing must be refused while protection is declared for the dimension");
    }

    private static void probeDefeatedLifetime(GameTestHelper helper, ServerLevel level) {
        int stride = LaneGrid.laneStride();
        BlockPos guarded = new BlockPos(OWN_LAYER * stride + 10, 64, 10);
        BlockPos laneZero = new BlockPos(10, 64, 10);
        ProtectedBlocks.register(level.dimension(),
                List.of(guarded, laneZero), ProtectedBlocks.Until.DEFEATED);

        helper.assertTrue(ProtectedBlocks.isProtected(level, guarded),
                "the defeated lifetime must hold while the guardian stands");
        LayerUnlocks.of(level).unlock(OWN_LAYER);
        helper.assertTrue(!ProtectedBlocks.isProtected(level, guarded),
                "the guardian's fall must release protection");
        helper.assertTrue(ProtectedBlocks.isProtected(level, laneZero),
                "lane 0 has no guardian ever - protection there must hold");
    }
}
