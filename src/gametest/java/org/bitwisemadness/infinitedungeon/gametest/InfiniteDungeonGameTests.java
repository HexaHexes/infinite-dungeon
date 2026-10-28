package org.bitwisemadness.infinitedungeon.gametest;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.announce.Announcement;
import org.bitwisemadness.infinitedungeon.announce.DungeonAnnouncements;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeon.portal.DungeonPortalBlock;
import org.bitwisemadness.infinitedungeon.portal.PortalBlocks;
import org.bitwisemadness.infinitedungeon.portal.PortalIgnition;

/**
 * Smoke test: proves the mod loads and the GameTest harness runs.
 *
 * <p>Keep the {@code expectedTests} count in {@code build.gradle} in sync with
 * the number of {@code @GameTest} methods here, or the harness fails the
 * run.</p>
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfiniteDungeonGameTests {

    private InfiniteDungeonGameTests() {
    }

    /** The mod is loaded and the empty structure template resolves. */
    @GameTest(template = "empty")
    public static void mod_loads(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * The dungeon dimension's type loads from the mod datapack with the values
     * the plan locks in: a fixed time, no ceiling, and the min_y/height pair
     * that must never change (changing them later is destructive to existing
     * worlds).
     *
     * <p>The level itself cannot be asserted here: the GameTest harness
     * hardcodes the flat world preset and bakes its level stems from that
     * preset, so datapack dimensions never become levels in this harness. The
     * dimension loads on real servers and clients; that part is proven by the
     * user's in-game check.</p>
     */
    @GameTest(template = "empty")
    public static void dungeon_dimension_type_loads(GameTestHelper helper) {
        DimensionType type = helper.getLevel().getServer().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
                .get(DungeonDimensions.TYPE);
        helper.assertTrue(type != null,
                "the dungeon dimension_type must load from the mod datapack");
        helper.assertTrue(type.fixedTime().isEmpty(),
                "the type must carry no fixed time: the mod holds the clock so a server can let it flow");
        helper.assertTrue(type.bedWorks(), "beds must not explode in the dungeon");
        helper.assertTrue(!type.hasCeiling(), "the dungeon must be open: no ceiling");
        helper.assertTrue(type.hasSkyLight(), "the dungeon must be open: skylight on");
        helper.assertTrue(type.minY() == -64, "min_y is locked at -64 by the plan");
        helper.assertTrue(type.height() == 2048, "height is locked at 2048 by the plan");
        helper.succeed();
    }

    /**
     * The announcement system sends to a player and broadcasts to a level. The
     * mock server player stands in for a real one, and the rendered text proves
     * the config-driven wording comes through as-is.
     */
    @GameTest(template = "empty")
    public static void announcements_reach_players(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(player != null, "a mock server player must be available");

        Component rendered = DungeonAnnouncements.render(
                Announcement.BOSS_AWAKENING, Map.of("floor", "7"));
        helper.assertTrue("The guardian of this floor awakens.".equals(rendered.getString()),
                "the config text must render unchanged: " + rendered.getString());

        DungeonAnnouncements.send(player, Announcement.ADVENTURE_LOCK);
        DungeonAnnouncements.broadcast(helper.getLevel(), Announcement.BOSS_AWAKENING);
        helper.succeed();
    }

    /**
     * The safety net of the spawn-point invariant: a respawn position stored
     * in the dungeon is cleared the moment the player dies. The mock player
     * makes this exercisable in the harness because the net keys off the
     * stored respawn dimension, not where the death happens.
     */
    @GameTest(template = "empty")
    public static void dungeon_respawn_is_cleared_at_death(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(player != null, "a mock server player must be available");

        player.setRespawnPosition(DungeonDimensions.LEVEL,
                helper.absolutePos(BlockPos.ZERO), 0.0F, true, false);
        helper.assertTrue(DungeonDimensions.LEVEL.equals(player.getRespawnDimension()),
                "the dungeon respawn must be stored before death");

        player.kill();

        helper.assertTrue(!DungeonDimensions.LEVEL.equals(player.getRespawnDimension()),
                "a stored dungeon respawn must be cleared at death");
        helper.succeed();
    }

    /**
     * A stone-brick frame in nether-portal geometry ignites into a dungeon
     * portal: the interior fills with portal blocks on the frame's axis, and
     * the frame itself is untouched.
     */
    @GameTest(template = "empty")
    public static void portal_frame_ignites(GameTestHelper helper) {
        for (int x = 2; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.STONE_BRICKS);
            helper.setBlock(new BlockPos(x, 6, 1), Blocks.STONE_BRICKS);
        }
        for (int y = 2; y <= 6; y++) {
            helper.setBlock(new BlockPos(2, y, 1), Blocks.STONE_BRICKS);
            helper.setBlock(new BlockPos(5, y, 1), Blocks.STONE_BRICKS);
        }

        boolean ignited = PortalIgnition.tryIgnite(helper.getLevel(),
                helper.absolutePos(new BlockPos(3, 3, 1)));
        helper.assertTrue(ignited, "a valid stone-brick frame must ignite");

        BlockState expected = PortalBlocks.DUNGEON_PORTAL.get().defaultBlockState()
                .setValue(DungeonPortalBlock.AXIS, Direction.Axis.X);
        for (int x = 3; x <= 4; x++) {
            for (int y = 3; y <= 5; y++) {
                BlockState state = helper.getBlockState(new BlockPos(x, y, 1));
                helper.assertTrue(expected.equals(state),
                        "the interior must be portal blocks on the X axis: " + state);
            }
        }
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 1)).is(Blocks.STONE_BRICKS),
                "the frame must stay untouched");
        helper.succeed();
    }

    /** A frame too small for vanilla's portal bounds refuses to ignite. */
    @GameTest(template = "empty")
    public static void portal_frame_rejects_bad_shape(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.STONE_BRICKS);
            helper.setBlock(new BlockPos(x, 4, 1), Blocks.STONE_BRICKS);
        }
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(2, y, 1), Blocks.STONE_BRICKS);
            helper.setBlock(new BlockPos(4, y, 1), Blocks.STONE_BRICKS);
        }

        boolean ignited = PortalIgnition.tryIgnite(helper.getLevel(),
                helper.absolutePos(new BlockPos(3, 3, 1)));
        helper.assertTrue(!ignited, "a 1x1 interior is below vanilla's portal bounds");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 1)).isAir(),
                "nothing may be placed in a rejected frame");
        helper.succeed();
    }
}
