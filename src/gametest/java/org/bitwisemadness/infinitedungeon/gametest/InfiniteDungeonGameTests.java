package org.bitwisemadness.infinitedungeon.gametest;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.announce.Announcement;
import org.bitwisemadness.infinitedungeon.announce.DungeonAnnouncements;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeon.death.LobbyReturn;
import org.bitwisemadness.infinitedungeon.lock.DungeonLock;
import org.bitwisemadness.infinitedungeon.portal.DungeonPortalBlock;
import org.bitwisemadness.infinitedungeon.portal.EntryAnchor;
import org.bitwisemadness.infinitedungeon.portal.EntryAnchors;
import org.bitwisemadness.infinitedungeon.portal.PortalBlocks;
import org.bitwisemadness.infinitedungeon.portal.PortalFrameBuilder;
import org.bitwisemadness.infinitedungeon.portal.PortalIgnition;
import org.bitwisemadness.infinitedungeon.portal.TravelPolicy;
import org.bitwisemadness.infinitedungeonapi.profile.PortalReturnMode;

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
        // The world around a random test position is not guaranteed empty:
        // clear the interior so the scan sees a genuine portal space.
        for (int x = 3; x <= 4; x++) {
            for (int y = 3; y <= 5; y++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.AIR);
            }
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
        // Clear the interior: whatever the world holds there is not part of
        // this test, and the postcondition is "stays air".
        helper.setBlock(new BlockPos(3, 3, 1), Blocks.AIR);

        boolean ignited = PortalIgnition.tryIgnite(helper.getLevel(),
                helper.absolutePos(new BlockPos(3, 3, 1)));
        helper.assertTrue(!ignited, "a 1x1 interior is below vanilla's portal bounds");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 1)).isAir(),
                "nothing may be placed in a rejected frame");
        helper.succeed();
    }

    /**
     * The entry anchor binds to a player and persists into their attachment
     * data - the payload that travels in the player's own NBT. The travel
     * decision itself is pure and covered in JUnit; the dungeon-side teleport
     * needs the dungeon level, which the GameTest harness cannot host.
     */
    @GameTest(template = "empty")
    public static void entry_anchor_binds_and_persists(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(player != null, "a mock server player must be available");
        helper.assertTrue(!EntryAnchors.has(player), "a fresh player has no anchor");

        helper.assertTrue(TravelPolicy.decide(false, EntryAnchors.has(player), PortalReturnMode.ENTRY_PORTAL)
                        == TravelPolicy.Action.BIND_AND_ENTER,
                "walking in from a normal dimension binds and enters");

        ResourceKey<Level> home = helper.getLevel().dimension();
        BlockPos portal = helper.absolutePos(BlockPos.ZERO);
        EntryAnchors.bind(player, home, portal);

        helper.assertTrue(EntryAnchors.has(player), "the anchor must bind");
        EntryAnchor anchor = EntryAnchors.get(player).orElseThrow();
        helper.assertTrue(anchor.dimension().equals(home), "the anchor keeps the entry dimension");
        helper.assertTrue(anchor.position().equals(portal), "the anchor keeps the entry position");
        helper.assertTrue(TravelPolicy.decide(true, EntryAnchors.has(player), PortalReturnMode.ENTRY_PORTAL)
                        == TravelPolicy.Action.RETURN_TO_ANCHOR,
                "walking out inside the dungeon returns to the anchor");

        // Persistence: the attachments payload exists only once data is set,
        // which is what gets written into the player's NBT on save.
        helper.assertTrue(helper.getLevel().registryAccess() != null, "the server exposes registries");
        CompoundTag empty = player.serializeAttachments(helper.getLevel().registryAccess());
        helper.assertTrue(!empty.isEmpty(), "the bound anchor must serialize with the player");

        EntryAnchors.clear(player);
        helper.assertTrue(!EntryAnchors.has(player), "the anchor must be clearable");
        helper.succeed();
    }

    /**
     * Re-forming a destroyed anchor: an empty space accepts the whole portal,
     * a blocked frame ring refuses the build outright - nothing half-built,
     * which is what lets the travel code fall back to the world spawn.
     */
    @GameTest(template = "empty")
    public static void reform_rebuilds_a_destroyed_anchor_portal(GameTestHelper helper) {
        // 1. A genuinely empty space accepts a re-formed portal: clear the
        // whole footprint first, since the world's own stone below the
        // template would - correctly - refuse the build.
        for (int x = 1; x <= 4; x++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.AIR);
            }
        }
        BlockPos cell = new BlockPos(2, 3, 1);
        boolean built = PortalFrameBuilder.build(helper.getLevel(), helper.absolutePos(cell),
                Direction.Axis.X);
        helper.assertTrue(built, "an empty space must accept a re-formed portal");
        helper.assertTrue(helper.getBlockState(cell).getBlock() instanceof DungeonPortalBlock,
                "the anchor cell must hold a lit portal");
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 3, 1)).is(Blocks.STONE_BRICKS),
                "the frame's left column must be rebuilt");
        helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 1)).is(Blocks.STONE_BRICKS),
                "the frame's bottom row must be rebuilt");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 5, 1)).getBlock()
                        instanceof DungeonPortalBlock,
                "the whole 2x3 interior must be lit");

        // 2. A solid block in the frame ring refuses the whole build: clear
        // the footprint, then block the ring with a block that is not the
        // frame material (stone bricks ARE the frame, so they would pass).
        for (int x = 7; x <= 10; x++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.AIR);
            }
        }
        helper.setBlock(new BlockPos(7, 3, 1), Blocks.STONE);
        BlockPos blocked = new BlockPos(8, 3, 1);
        boolean refused = PortalFrameBuilder.build(helper.getLevel(), helper.absolutePos(blocked),
                Direction.Axis.X);
        helper.assertTrue(!refused, "a blocked frame ring must refuse the build");
        helper.assertTrue(helper.getBlockState(blocked).isAir(),
                "nothing may be half-built: the interior stays untouched");
        helper.succeed();
    }

    /**
     * The adventure lock's swing: applying swaps the player to adventure and
     * remembers what they were; releasing restores exactly that and forgets
     * the snapshot. The dimension scoping and the break whitelist are pure and
     * covered in JUnit.
     */
    @GameTest(template = "empty")
    public static void adventure_lock_swaps_and_restores(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(player != null, "a mock server player must be available");
        GameType before = player.gameMode.getGameModeForPlayer();
        helper.assertTrue(!DungeonLock.holdsSnapshot(player), "a fresh player holds no lock snapshot");

        DungeonLock.apply(player);
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                "the lock must swap the player into adventure mode");
        helper.assertTrue(DungeonLock.holdsSnapshot(player),
                "the previous gamemode must be remembered while locked");

        DungeonLock.release(player);
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == before,
                "release must restore the gamemode exactly");
        helper.assertTrue(!DungeonLock.holdsSnapshot(player),
                "release must forget the snapshot");
        helper.succeed();
    }

    /**
     * The lobby-return marker: set on a dungeon death, carried through respawn
     * (the attachment copies on death), consumed afterwards. The teleport
     * itself needs the dungeon level, which the harness cannot host.
     */
    @GameTest(template = "empty")
    public static void lobby_marker_marks_and_clears(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(player != null, "a mock server player must be available");
        helper.assertTrue(!LobbyReturn.holdsMarker(player), "a fresh player carries no marker");

        LobbyReturn.mark(player);
        helper.assertTrue(LobbyReturn.holdsMarker(player), "a dungeon death must be marked");

        LobbyReturn.clear(player);
        helper.assertTrue(!LobbyReturn.holdsMarker(player), "the marker must be consumed at respawn");
        helper.succeed();
    }
}
