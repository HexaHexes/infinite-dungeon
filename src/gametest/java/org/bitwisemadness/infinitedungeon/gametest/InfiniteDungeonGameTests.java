package org.bitwisemadness.infinitedungeon.gametest;

import de.maxhenkel.corpse.corelib.death.Death;
import de.maxhenkel.corpse.entities.CorpseEntity;
import java.util.Map;
import net.blay09.mods.waystones.block.WaystoneBlock;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.announce.Announcement;
import org.bitwisemadness.infinitedungeon.announce.DungeonAnnouncements;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.death.CorpseCleanup;
import org.bitwisemadness.infinitedungeon.death.LobbyReturn;
import org.bitwisemadness.infinitedungeon.depth.DungeonLayout;
import org.bitwisemadness.infinitedungeon.depth.LayoutParameters;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeon.lanes.FallbackHallFeature;
import org.bitwisemadness.infinitedungeon.lanes.FloorSchedule;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.LaneTransitions;
import org.bitwisemadness.infinitedungeon.lanes.LayerUnlocks;
import org.bitwisemadness.infinitedungeon.lanes.TransitionFramesFeature;
import org.bitwisemadness.infinitedungeon.lanes.WaystoneEntranceFeature;
import org.bitwisemadness.infinitedungeon.lock.DungeonLock;
import org.bitwisemadness.infinitedungeon.magicule.DungeonMagiculeProvider;
import org.bitwisemadness.infinitedungeon.portal.DungeonEntrance;
import org.bitwisemadness.infinitedungeon.portal.DungeonPortalBlock;
import org.bitwisemadness.infinitedungeon.portal.DungeonPortalTravel;
import org.bitwisemadness.infinitedungeon.portal.EntryAnchor;
import org.bitwisemadness.infinitedungeon.portal.EntryAnchors;
import org.bitwisemadness.infinitedungeon.portal.LobbyEntranceFeature;
import org.bitwisemadness.infinitedungeon.portal.PortalBlocks;
import org.bitwisemadness.infinitedungeon.portal.PortalFrameBuilder;
import org.bitwisemadness.infinitedungeon.portal.PortalIgnition;
import org.bitwisemadness.infinitedungeon.portal.TravelPolicy;
import org.bitwisemadness.infinitedungeonapi.magicule.AmbientMagicule;
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
     * The Dungeon Crawl biome gate: our biome loads from the mod datapack, the
     * additive tag override (replace false) puts it inside Dungeon Crawl's
     * has_structure/dungeon gate, and the dungeon dimension parses against the
     * biome registry (LevelStem codec - an unresolvable biome reference fails
     * the parse). The gate makes Dungeon Crawl's dungeons eligible in our
     * dimension; placement itself arrives with the lane structure set (issue
     * #27). The tag loads even without Dungeon Crawl installed - tags are
     * pure datapack data, so the override is testable in this harness. The
     * stem never appears in the harness's own dimension registry (it bakes
     * its level stems from the flat world preset), so the dimension is
     * verified by codec parse here rather than by registry lookup.
     */
    @GameTest(template = "empty")
    public static void dungeon_biome_and_gate_load(GameTestHelper helper) {
        var registries = helper.getLevel().getServer().registryAccess();

        var biomes = registries.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        helper.assertTrue(biomes.get(DungeonDimensions.ID) != null,
                "the dungeon biome must load from the mod datapack");

        var gate = biomes.getTag(TagKey.create(
                net.minecraft.core.registries.Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("dungeoncrawl", "has_structure/dungeon")));
        helper.assertTrue(gate.isPresent(),
                "the Dungeon Crawl biome gate tag must load from our datapack override");
        var holder = biomes.getHolder(ResourceKey.create(
                net.minecraft.core.registries.Registries.BIOME, DungeonDimensions.ID));
        helper.assertTrue(holder.isPresent() && gate.get().contains(holder.get()),
                "the dungeon biome must sit inside the has_structure/dungeon gate");

        var dimension = helper.getLevel().getServer().getResourceManager()
                .getResource(ResourceLocation.fromNamespaceAndPath("infinite_dungeon", "dimension/dungeon.json"));
        helper.assertTrue(dimension.isPresent(), "the dimension json must ship in the mod datapack");
        try {
            var json = net.minecraft.util.GsonHelper.parse(
                    new String(dimension.get().open().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            var ops = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var stem = net.minecraft.world.level.dimension.LevelStem.CODEC.parse(ops, json);
            helper.assertTrue(stem.result().isPresent(),
                    "the dungeon dimension must parse with its biome reference: " + stem.error());

            // The 2026-10-30 ruling: Dungeon Crawl's natural generation stays
            // disabled on this dimension - our lane set is the sole placement
            // authority, so we control what generates and when while still
            // reusing dungeoncrawl:dungeon through our own set.
            var settings = net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings.CODEC
                    .parse(ops, json.getAsJsonObject().get("generator")
                            .getAsJsonObject().get("settings"))
                    .getOrThrow();
            helper.assertTrue(settings.structureOverrides().isPresent(),
                    "the dimension must pin its structure sets explicitly");
            var sets = settings.structureOverrides().get();
            java.util.List<String> keys = new java.util.ArrayList<>();
            sets.stream().forEach(entry -> entry.unwrapKey()
                    .ifPresent(key -> keys.add(key.location().toString())));
            helper.assertTrue(keys.size() == 1 && keys.contains("infinite_dungeon:lanes"),
                    "our lane set must be the sole placement authority: " + keys);
            helper.assertTrue(!keys.contains("dungeoncrawl:dungeons"),
                    "Dungeon Crawl's natural generation must stay disabled here: " + keys);
        } catch (Exception e) {
            helper.assertTrue(false, "the dungeon dimension json must parse: " + e);
        }
        helper.succeed();
    }

    /**
     * The lane structure set: our own set on Dungeon Crawl's placement grid
     * (spacing 32, separation 12, salt 10387313 - the Phase 0 spike values),
     * referencing dungeoncrawl:dungeon directly. The structure holder must
     * resolve - the gametest runtime carries Dungeon Crawl through devRuntime -
     * proving the reference is safe to ship (a dangling one fails datapack load
     * on every real server). Placement values are read from the registry as
     * effective post-load values, the same startup read issue #28 builds lane
     * geometry on.
     */
    @GameTest(template = "empty")
    public static void lane_structure_set_loads(GameTestHelper helper) {
        var registries = helper.getLevel().getServer().registryAccess();
        var sets = registries.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
        var laneSet = sets.get(ResourceLocation.fromNamespaceAndPath("infinite_dungeon", "lanes"));
        helper.assertTrue(laneSet != null, "the lane structure set must load from the mod datapack");

        helper.assertTrue(laneSet.placement()
                        instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement spread
                        && spread.spacing() == 32 && spread.separation() == 12,
                "the lane grid must match Dungeon Crawl: spacing 32, separation 12");
        // salt() is protected, so the salt is asserted through the codec's own
        // encode path - the same field the datapack wrote. Holder encoding
        // needs registry context, so the encode runs against registry ops just
        // like the dimension parse above.
        var encoded = net.minecraft.world.level.levelgen.structure.StructureSet.DIRECT_CODEC
                .encodeStart(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                        laneSet)
                .getOrThrow();
        helper.assertTrue(encoded.getAsJsonObject().getAsJsonObject("placement")
                        .get("salt").getAsLong() == 10387313L,
                "the lane salt must be the spike value 10387313: "
                        + encoded.getAsJsonObject().getAsJsonObject("placement"));

        helper.assertTrue(laneSet.structures().size() == 1, "the lane set holds exactly one entry");
        var reference = laneSet.structures().get(0).structure().unwrapKey();
        helper.assertTrue(reference.isPresent()
                        && reference.get().location().equals(
                                ResourceLocation.fromNamespaceAndPath("dungeoncrawl", "dungeon")),
                "the lane set must reference dungeoncrawl:dungeon with a resolved holder: " + reference);
        helper.succeed();
    }

    /**
     * The remaining runtime check the Dungeon Crawl spike (issue #2) folded
     * into the implementation issues: the overworld suppression datapack
     * empties Dungeon Crawl's structure-set structures list, so that shape
     * must stay loadable. Datapack registry load is exactly this codec decode,
     * so parsing the emptied file's JSON here proves the suppression pack can
     * never fail dimension load - it can only stop generating wild dungeons.
     * Decode needs no registry context: an empty list holds no references to
     * resolve.
     */
    @GameTest(template = "empty")
    public static void suppression_empty_set_stays_loadable(GameTestHelper helper) {
        var emptied = net.minecraft.util.GsonHelper.parse("""
                {
                  "placement": {
                    "type": "minecraft:random_spread",
                    "salt": 10387313,
                    "separation": 12,
                    "spacing": 32
                  },
                  "structures": []
                }""");
        var set = net.minecraft.world.level.levelgen.structure.StructureSet.DIRECT_CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, emptied);
        helper.assertTrue(set.result().isPresent(),
                "an emptied structure set must stay loadable: " + set.error());
        helper.succeed();
    }

    /**
     * The lane stride mirrors the lane set's effective registry values at
     * startup (plan section 3): the harness carries no structure-mod rewrite,
     * so the effective spacing is the shipped one and the stride reads 512 -
     * the same grid the lane set test asserts. On a pack where a rewrite
     * scales the spacing, this read is what keeps the lanes aligned.
     */
    @GameTest(template = "empty")
    public static void lane_grid_mirrors_effective_placement(GameTestHelper helper) {
        var registries = helper.getLevel().getServer().registryAccess();
        var laneSet = registries.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET)
                .get(LaneGrid.LANE_SET_ID);
        LaneGrid.update(laneSet);
        helper.assertTrue(LaneGrid.laneStride() == LaneGrid.SHIPPED_LANE_STRIDE,
                "without a rewrite the effective stride must be the shipped grid of 512: "
                        + LaneGrid.laneStride());
        // The pure geometry composes on top: layer L anchors at L * stride and
        // positions map back to their lane by floor division.
        helper.assertTrue(LaneGrid.laneStride() == 32 * 16,
                "the shipped stride follows Dungeon Crawl's 32-chunk spacing");
        helper.succeed();
    }

    /**
     * The depth derivation wired to a real server: LayoutParameters.forServer
     * gathers the world seed, the effective lane stride, the dimension's own
     * vertical extents (the locked -64/2048 pair), and the configured floor
     * range - and the pure derivation composes on top. The lane-1 expectations
     * are computed through the same FloorSchedule the JUnit suites pin, so
     * this proves the wiring, not the math.
     */
    @GameTest(template = "empty")
    public static void depth_derives_from_live_parameters(GameTestHelper helper) {
        var level = helper.getLevel();
        var parameters = LayoutParameters.forServer(level);
        helper.assertTrue(parameters.laneStride() == 512,
                "the effective stride must reach the derivation: " + parameters.laneStride());
        // The factory must transmit its level's own vertical extents. The
        // harness hosts its own flat-preset level (not our dimension, which
        // never becomes a level here - see the dimension-type test), so the
        // locked -64/2048 pair is asserted on the datapack registry there;
        // on a real server the level IS the dungeon and the pair flows through
        // this exact path.
        helper.assertTrue(parameters.minY() == level.getMinBuildHeight()
                        && parameters.availableHeight() == level.getHeight(),
                "the level's vertical extents must reach the derivation: " + parameters.minY()
                        + "/" + parameters.availableHeight());
        helper.assertTrue(parameters.floorsMin() >= 1 && parameters.floorsMax() >= parameters.floorsMin(),
                "the configured floor range must reach the derivation: [" + parameters.floorsMin()
                        + ", " + parameters.floorsMax() + "]");

        var lobby = DungeonLayout.derive(parameters, 0, parameters.minY());
        helper.assertTrue(lobby.layerIndex() == 0 && lobby.floorInLayer() == 0 && lobby.depth() == 0,
                "the lobby layer must derive to (0, 0, 0): " + lobby);

        int laneOneX = parameters.laneStride() + 1;
        // Floor 0 is the entry band at the TOP of the layer's band (the
        // descend-into-danger axis, issue #183).
        var entry = DungeonLayout.derive(parameters, laneOneX,
                parameters.minY() + parameters.availableHeight() - 1);
        int layerOneFloors = FloorSchedule.floorsInLayer(
                parameters.worldSeed(), 1, parameters.floorsMin(), parameters.floorsMax());
        helper.assertTrue(entry.layerIndex() == 1 && entry.floorInLayer() == 0 && entry.depth() == 1,
                "lane 1's entry floor must derive to (1, 0, 1): " + entry
                        + " with layer one fielding " + layerOneFloors + " floors");

        helper.assertTrue(!DungeonLayout.inDungeon(parameters, -1),
                "west of the first lane must be outside the dungeon");
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

    /**
     * The Corpse cleanup: a corpse whose inventory has been fully reclaimed is
     * removed by the sweep, while a corpse still holding items and an
     * unrelated entity are left alone. The pure decision is covered in JUnit;
     * this proves the ModList-gated wiring against the real Corpse classes
     * (the gametest runtime carries Corpse). The sweep is invoked directly
     * because the harness cannot host the dungeon dimension - the dimension
     * gate is the same one-line check DungeonTime uses, in the tick listener.
     */
    @GameTest(template = "empty")
    public static void reclaimed_corpse_is_swept(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(owner != null, "a mock server player must be available");

        // A corpse with nothing left: the mock player carries no items, so
        // every inventory Death snapshots from them is empty.
        Death emptyDeath = Death.fromPlayer(owner);
        CorpseEntity empty = CorpseEntity.createFromDeath(owner, emptyDeath);
        BlockPos spot = helper.absolutePos(new BlockPos(2, 1, 2));
        empty.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        helper.assertTrue(helper.getLevel().addFreshEntity(empty),
                "the empty corpse must spawn in the level");

        // A corpse still holding its owner's items, from a second player so
        // the two corpses never share an identity.
        ServerPlayer ladenOwner = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(ladenOwner != null, "a second mock server player must be available");
        ladenOwner.getInventory().add(new ItemStack(Items.STONE, 5));
        Death ladenDeath = Death.fromPlayer(ladenOwner);
        CorpseEntity laden = CorpseEntity.createFromDeath(ladenOwner, ladenDeath);
        laden.setPos(spot.getX() + 2.5, spot.getY(), spot.getZ() + 0.5);
        helper.assertTrue(helper.getLevel().addFreshEntity(laden),
                "the laden corpse must spawn in the level");

        // An unrelated entity that must never be swept.
        var litter = helper.spawnItem(Items.STONE, new BlockPos(4, 1, 2));

        helper.assertTrue(!empty.isRemoved(), "the empty corpse must be present before the sweep");
        helper.assertTrue(!laden.isRemoved(), "the laden corpse must be present before the sweep");

        CorpseCleanup.scan(helper.getLevel());

        helper.assertTrue(empty.isRemoved(),
                "a fully reclaimed corpse must be removed by the sweep");
        helper.assertTrue(!laden.isRemoved(),
                "a corpse holding items must stay for its owner to reclaim");
        helper.assertTrue(!litter.isRemoved(), "an unrelated entity must never be swept");

        // Leftovers leave through the contained path the sweep uses (issue
        // #189): mock players never declare Corpse's particle channel, and a
        // corpse left ticking would age out mid-batch - Corpse's own tick
        // discard bypasses removeCorpse's catch and crashes the run.
        try {
            laden.discard();
        } catch (UnsupportedOperationException ex) {
            // Same contract as CorpseCleanup.removeCorpse: already removed
            // by the time the notification throws.
            helper.assertTrue(laden.isRemoved(),
                    "the corpse must be gone even when its notification fails");
        }
        litter.discard();
        helper.succeed();
    }

    /**
     * The void-placement mixin (issue #174's ruling): Dungeon Crawl's
     * structure generates in the dimension's shipped void settings, with its
     * anchor inside the build height - the condition of the ruling. The floor
     * control proves the clamp is a no-op where terrain already exists, i.e.
     * every terrain world keeps its current placement.
     */
    @GameTest(template = "empty")
    public static void void_dimension_allows_dungeon_generation(GameTestHelper helper) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var resource = level.getServer().getResourceManager()
                .getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "infinite_dungeon", "dimension/dungeon.json"))
                .orElseThrow();
        com.google.gson.JsonElement json;
        try {
            json = net.minecraft.util.GsonHelper.parse(new String(
                    resource.open().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        var ops = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var settingsJson = json.getAsJsonObject().get("generator").getAsJsonObject().get("settings");
        var voidSettings = net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings.CODEC
                .parse(ops, settingsJson).getOrThrow();
        var biomes = registries.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var biome = biomes.getHolder(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BIOME, DungeonDimensions.ID)).orElseThrow();
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(
                registries.registryOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                        .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD),
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE),
                level.getSeed());
        var templateManager = level.getServer().getStructureManager();
        var dungeon = new xiroc.dungeoncrawl.dungeon.Dungeon(
                new net.minecraft.world.level.levelgen.structure.Structure.StructureSettings(
                        net.minecraft.core.HolderSet.direct(biome)));

        var voidGenerator = new net.minecraft.world.level.levelgen.FlatLevelSource(voidSettings);
        var voidContext = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                registries, voidGenerator, new net.minecraft.world.level.biome.FixedBiomeSource(biome),
                randomState, templateManager, level.getSeed(), new net.minecraft.world.level.ChunkPos(0, 0),
                level, holder -> true);
        var voidPoint = dungeon.findGenerationPoint(voidContext);
        helper.assertTrue(voidPoint.isPresent(),
                "the mixin must let the dungeon generate in the void dimension");
        var anchor = voidPoint.get().position();
        helper.assertTrue(anchor.getY() >= level.getMinBuildHeight()
                        && anchor.getY() < level.getMaxBuildHeight(),
                "the void anchor must sit inside the build height: " + anchor);

        var floorSettings = voidSettings.withBiomeAndLayers(
                java.util.List.of(new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(
                        64, net.minecraft.world.level.block.Blocks.STONE)),
                voidSettings.structureOverrides(), biome);
        var floorContext = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                registries, new net.minecraft.world.level.levelgen.FlatLevelSource(floorSettings),
                new net.minecraft.world.level.biome.FixedBiomeSource(biome),
                randomState, templateManager, level.getSeed(), new net.minecraft.world.level.ChunkPos(0, 0),
                level, holder -> true);
        helper.assertTrue(dungeon.findGenerationPoint(floorContext).isPresent(),
                "terrain worlds must keep generating exactly as before");
        helper.succeed();
    }

    /**
     * The fallback hall (issue #30): guards exactly the lane cells whose
     * dungeon is absent. Four paths against the real placement read from
     * LaneGrid: a non-candidate chunk of a region is skipped, the lobby region
     * (lane 0) is skipped, the cell's actual candidate builds the hall, and
     * the room lands on the dungeon's own envelope - floor at the base plane,
     * ceiling just under the ground plane, both inside the build height. The
     * dungeon-present path cannot be staged here (the harness has no dungeon
     * starts); its logic is the same local start lookup documented on the
     * feature.
     */
    @GameTest(template = "empty")
    public static void fallback_hall_guards_the_dungeon_less_cell(GameTestHelper helper) {
        var level = helper.getLevel();
        var placement = LaneGrid.placement();
        helper.assertTrue(placement != null,
                "the lane placement must be read for the hall filter");
        long seed = level.getSeed();
        int minBuild = level.getMinBuildHeight();

        var generator = level.getChunkSource().getGenerator();
        var feature = FallbackHallFeature.FALLBACK_HALL.get();

        // Region (0,0) candidates all fall in lane 0 - the lobby layer is
        // reserved for issue #131, so the hall must refuse.
        var lobbyCell = placement.getPotentialStructureChunk(seed, 0, 0);
        helper.assertTrue(LaneGeometry.laneIndex(lobbyCell.getMiddleBlockX(),
                        LaneGrid.laneStride()) == 0,
                "region zero must map to the lobby lane by construction");
        var lobbyOrigin = new BlockPos(lobbyCell.x << 4, minBuild + 1, lobbyCell.z << 4);
        helper.assertTrue(!feature.place(context(level, generator, seed, lobbyOrigin)),
                "the lobby lane must never get a hall");

        // Region (1,0) candidates are lane 1+.
        var cell = placement.getPotentialStructureChunk(seed, placement.spacing(), 0);
        helper.assertTrue(LaneGeometry.laneIndex(cell.getMiddleBlockX(),
                        LaneGrid.laneStride()) >= 1,
                "region one must map past the lobby lane");

        // Negative control: a non-candidate chunk inside the same region.
        int regionX = Math.floorDiv(cell.x, placement.spacing());
        int regionZ = Math.floorDiv(cell.z, placement.spacing());
        ChunkPos other = null;
        for (int step = 0; step < placement.spacing(); step++) {
            var probe = new ChunkPos(regionX * placement.spacing() + step, regionZ * placement.spacing());
            if (probe.x != cell.x || probe.z != cell.z) {
                other = probe;
                break;
            }
        }
        helper.assertTrue(other != null, "the region must hold a non-candidate chunk");
        var otherOrigin = new BlockPos(other.x << 4, minBuild + 1, other.z << 4);
        helper.assertTrue(!feature.place(context(level, generator, seed, otherOrigin)),
                "a non-candidate chunk must stay empty");

        // Positive: the candidate cell itself builds the hall.
        var origin = new BlockPos(cell.x << 4, minBuild + 1, cell.z << 4);
        helper.assertTrue(feature.place(context(level, generator, seed, origin)),
                "the dungeon-less candidate cell must get the hall");
        helper.assertTrue(level.getBlockState(cell.getMiddleBlockPosition(minBuild + 21))
                        .is(Blocks.STONE_BRICKS),
                "the hall floor must sit on the dungeon's base plane");
        helper.assertTrue(level.getBlockState(cell.getMiddleBlockPosition(minBuild + 44))
                        .is(Blocks.STONE_BRICKS),
                "the hall ceiling must stop under the ground plane, in-bounds");
        helper.succeed();
    }

    private static FeaturePlaceContext<NoneFeatureConfiguration> context(
            net.minecraft.world.level.WorldGenLevel level,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            long seed, BlockPos origin) {
        return new FeaturePlaceContext<>(java.util.Optional.empty(), level, generator,
                RandomSource.create(seed), origin, NoneFeatureConfiguration.INSTANCE);
    }

    /**
     * The transition frames (issue #31): at the candidate cell both slots of
     * the pair build - departure on the east edge of the deepest floor,
     * arrival on the west edge of the entry band - and both interiors stay
     * unlit so the sealed ignition gate owns the first light.
     */
    @GameTest(template = "empty")
    public static void transition_frames_build_unlit_pairs(GameTestHelper helper) {
        var level = helper.getLevel();
        var placement = LaneGrid.placement();
        helper.assertTrue(placement != null, "the lane placement must be read");
        long seed = level.getSeed();
        int minBuild = level.getMinBuildHeight();

        var cell = placement.getPotentialStructureChunk(seed, placement.spacing(), 0);
        helper.assertTrue(LaneGeometry.laneIndex(cell.getMiddleBlockX(), LaneGrid.laneStride()) >= 1,
                "the probe cell must sit past the lobby lane");

        // The harness world is a flat preset with ground at the bottom, so
        // both slot volumes are cleared first - the same accommodation the
        // portal-frame tests make. In the dungeon's void they are air.
        int floors = FloorSchedule.floorsInLayer(seed, 1,
                DungeonConfigs.floorsPerLayerMin(), DungeonConfigs.floorsPerLayerMax());
        var departure = LaneTransitions.departureInterior(minBuild, cell);
        var arrival = LaneTransitions.arrivalInterior(minBuild, level.getHeight(), floors, cell);
        clearFootprint(level, departure);
        clearFootprint(level, arrival);

        var origin = new BlockPos(cell.x << 4, minBuild + 1, cell.z << 4);
        var feature = TransitionFramesFeature.TRANSITION_FRAMES.get();
        helper.assertTrue(feature.place(context(level, level.getChunkSource().getGenerator(), seed, origin)),
                "the candidate cell must get its transition frames");

        helper.assertTrue(level.getBlockState(departure).isAir(),
                "the departure interior must stay unlit");
        helper.assertTrue(level.getBlockState(departure.offset(-1, 0, 0)).is(Blocks.STONE_BRICKS),
                "the departure ring must be built of the frame block");
        helper.assertTrue(level.getBlockState(arrival).isAir(),
                "the arrival interior must stay unlit");
        helper.assertTrue(level.getBlockState(arrival.offset(-1, 0, 0)).is(Blocks.STONE_BRICKS),
                "the arrival ring must be built of the frame block");
        helper.succeed();
    }

    /**
     * Clears a frame's full footprint (ring included) to air - absolute
     * positions, since helper.setBlock addresses test-local space.
     */
    private static void clearFootprint(net.minecraft.world.level.WorldGenLevel level, BlockPos interior) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                level.setBlock(interior.offset(dx, dy, 0), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
    /**
     * The transition seal gate end to end (issue #31): one test owns the
     * guardian seam - vanilla runs batch tests concurrently, so a shared
     * set/reset from two tests would race - and locks its layers first,
     * because harness world data survives between runs. Unassigned layers are
     * open (the interim-alpha rule), assigned layers seal, ignition refuses
     * while sealed, travel refuses while sealed, and unlocking opens both.
     */
    @GameTest(template = "empty")
    public static void layer_transitions_seal_gate_and_travel(GameTestHelper helper) {
        var level = helper.getLevel();
        var placement = LaneGrid.placement();
        helper.assertTrue(placement != null, "the lane placement must be read");
        long seed = level.getSeed();
        int minBuild = level.getMinBuildHeight();

        var cell = placement.getPotentialStructureChunk(seed, placement.spacing(), 0);
        var departure = LaneTransitions.departureInterior(minBuild, cell);
        int lane = LaneGeometry.laneIndex(departure.getX(), LaneGrid.laneStride());
        var unlocks = LayerUnlocks.of(level);
        try {
            // Deterministic preconditions for any starting state.
            unlocks.lock(lane);
            unlocks.lock(lane + 1);
            LayerUnlocks.setGuardianAssigned(layer -> layer == lane);

            helper.assertTrue(!LayerUnlocks.isSealed(level, lane + 1),
                    "an unassigned layer must be open (interim alpha rule)");
            helper.assertTrue(LayerUnlocks.isSealed(level, lane),
                    "assigned and not unlocked must seal");
            helper.assertTrue(PortalIgnition.sealedAt(level, departure),
                    "a sealed departure must refuse its first light");

            ServerPlayer sealedPlayer = helper.makeMockServerPlayerInLevel();
            sealedPlayer.setPos(departure.getX() + 0.5, departure.getY() + 0.5, departure.getZ() + 0.5);
            DungeonPortalTravel.laneHop(level, departure, sealedPlayer);
            helper.assertTrue(sealedPlayer.blockPosition().equals(departure),
                    "a sealed layer must not move the player: " + sealedPlayer.blockPosition());

            unlocks.unlock(lane);
            helper.assertTrue(!LayerUnlocks.isSealed(level, lane),
                    "a fallen guardian must open the layer");
            unlocks.unlock(lane);
            helper.assertTrue(!LayerUnlocks.isSealed(level, lane), "unlocking is idempotent");
            helper.assertTrue(!PortalIgnition.sealedAt(level, departure),
                    "an unlocked departure must allow ignition");

            var target = LaneTransitions.pairTarget(level, departure);
            helper.assertTrue(target.isPresent(), "a departure slot must pair to the next lane");
            helper.assertTrue(LaneGeometry.laneIndex(target.get().getX(), LaneGrid.laneStride()) == lane + 1,
                    "the pair must land in the next lane");

            ServerPlayer openPlayer = helper.makeMockServerPlayerInLevel();
            openPlayer.setPos(departure.getX() + 0.5, departure.getY() + 0.5, departure.getZ() + 0.5);
            DungeonPortalTravel.laneHop(level, departure, openPlayer);
            helper.assertTrue(openPlayer.blockPosition().equals(target.get()),
                    "an open departure must teleport to its arrival: " + openPlayer.blockPosition());
        } finally {
            LayerUnlocks.setGuardianAssigned(layer -> false);
            unlocks.lock(lane);
        }
        helper.succeed();
    }
    /**
     * The layer-entrance waystone (issue #68): exactly one per layer - at the
     * rz-0 canonical cell of a lane past the lobby, named "Layer N" so the
     * fast-travel list of amendment C2c carries no duplicates - and none on
     * any other cell of the same lane. The name persists through the block
     * entity, so it rides the chunk.
     */
    @GameTest(template = "empty")
    public static void waystone_entrance_places_one_per_layer(GameTestHelper helper) {
        var level = helper.getLevel();
        var placement = LaneGrid.placement();
        helper.assertTrue(placement != null, "the lane placement must be read");
        long seed = level.getSeed();
        int minBuild = level.getMinBuildHeight();
        var generator = level.getChunkSource().getGenerator();
        var feature = WaystoneEntranceFeature.WAYSTONE_ENTRANCE.get();

        // Positive: the rz-0 candidate of lane 1 (both hold by construction
        // of getPotentialStructureChunk on these inputs - asserted as record).
        var cell = placement.getPotentialStructureChunk(seed, placement.spacing(), 0);
        int lane = LaneGeometry.laneIndex(cell.getMiddleBlockX(), LaneGrid.laneStride());
        helper.assertTrue(lane >= 1, "the probe cell must sit past the lobby lane");
        helper.assertTrue(Math.floorDiv(cell.z, placement.spacing()) == 0,
                "the probe cell must be the rz-0 entrance region");
        var origin = new BlockPos(cell.x << 4, minBuild + 1, cell.z << 4);
        // The harness world persists between runs (its chunks survive in
        // run/world), and this spot is seed-derived - so a waystone from a
        // previous run can still stand there. Production refuses to overwrite
        // a standing waystone BY DESIGN; the test clears the two blocks
        // first, the same "not guaranteed empty" accommodation the other
        // world-writing tests make.
        int clearFloors = FloorSchedule.floorsInLayer(seed, lane,
                DungeonConfigs.floorsPerLayerMin(), DungeonConfigs.floorsPerLayerMax());
        BlockPos spotToClear = LaneTransitions.arrivalInterior(minBuild, level.getHeight(), clearFloors, cell)
                .offset(0, 0, 3);
        level.setBlock(spotToClear, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(spotToClear.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(feature.place(context(level, generator, seed, origin)),
                "the canonical entrance cell must get its waystone");

        int floors = FloorSchedule.floorsInLayer(seed, lane,
                DungeonConfigs.floorsPerLayerMin(), DungeonConfigs.floorsPerLayerMax());
        var spot = LaneTransitions.arrivalInterior(minBuild, level.getHeight(), floors, cell)
                .offset(0, 0, 3);
        helper.assertTrue(level.getBlockState(spot).getBlock() instanceof WaystoneBlock,
                "the waystone must stand three blocks south of the arrival interior");
        if (!(level.getBlockEntity(spot) instanceof WaystoneBlockEntityBase entity)) {
            helper.assertTrue(false, "the waystone block entity must exist");
            return;
        }
        var waystone = entity.getWaystone();
        helper.assertTrue(waystone != null, "the block entity must expose a waystone");
        String name = waystone.getName().getString();
        helper.assertTrue(name.equals("Layer " + lane),
                "the waystone must read Layer N: " + name);

        // Negative: the same lane, next Z region - no waystone anywhere near
        // where its arrival would stand.
        var other = placement.getPotentialStructureChunk(seed,
                placement.spacing(), placement.spacing());
        helper.assertTrue(Math.floorDiv(other.z, placement.spacing()) >= 1,
                "the negative probe must sit past rz-0");
        var otherOrigin = new BlockPos(other.x << 4, minBuild + 1, other.z << 4);
        helper.assertTrue(!feature.place(context(level, generator, seed, otherOrigin)),
                "a non-entrance cell must get no waystone");
        int otherLane = LaneGeometry.laneIndex(other.getMiddleBlockX(), LaneGrid.laneStride());
        int otherFloors = FloorSchedule.floorsInLayer(seed, otherLane,
                DungeonConfigs.floorsPerLayerMin(), DungeonConfigs.floorsPerLayerMax());
        var otherSpot = LaneTransitions.arrivalInterior(minBuild, level.getHeight(), otherFloors, other)
                .offset(0, 0, 3);
        helper.assertTrue(!(level.getBlockState(otherSpot).getBlock() instanceof WaystoneBlock),
                "only the layer's canonical entrance may hold the waystone");
        helper.succeed();
    }    /**
     * The lobby skeleton (issue #131): at the fixed origin chunk the feature
     * builds the recorded entrance shape - the platform at y 63 and the lit
     * portal - and the travel lookups find it without ever building: break
     * the portal and ensure/lobby report the absence instead of restoring
     * it. Re-driving the feature restores the lobby, because generation is
     * its owner and the lookups are contractually mute.
     */
    @GameTest(template = "empty")
    public static void lobby_skeleton_generates_at_fixed_origin(GameTestHelper helper) {
        var level = helper.getLevel();
        long seed = level.getSeed();
        var generator = level.getChunkSource().getGenerator();
        var origin = new BlockPos(0, 64, 0);
        var feature = LobbyEntranceFeature.LOBBY_ENTRANCE.get();

        helper.assertTrue(feature.place(context(level, generator, seed, origin)),
                "the fixed origin chunk must get its lobby");
        helper.assertTrue(level.getBlockState(new BlockPos(0, 63, 0)).is(Blocks.STONE_BRICKS),
                "the platform must stand at y 63");
        helper.assertTrue(level.getBlockState(new BlockPos(4, 63, 2)).is(Blocks.STONE_BRICKS),
                "the platform must span x 0..4, z 0..2");
        helper.assertTrue(level.getBlockState(new BlockPos(1, 65, 0))
                        .getBlock() instanceof DungeonPortalBlock,
                "the arrival cell must hold the lit portal");
        helper.assertTrue(DungeonEntrance.ensure(level) != null,
                "ensure must find the generated entrance");
        helper.assertTrue(DungeonEntrance.lobby(level) != null,
                "lobby must find the generated entrance");

        // The no-build contract: break the portal and both lookups report it
        // without restoring a single block.
        level.setBlock(new BlockPos(1, 65, 0), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(DungeonEntrance.ensure(level) == null,
                "ensure must not rebuild the entrance");
        helper.assertTrue(DungeonEntrance.lobby(level) == null,
                "lobby must not rebuild the entrance");
        helper.assertTrue(level.getBlockState(new BlockPos(1, 65, 0)).isAir(),
                "the broken portal must stay broken after the lookups");

        // Generation owns restoration: clear the interior (the ring is the
        // builder's own replaceable frame) and re-drive the feature.
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlock(new BlockPos(1 + dx, 65 + dy, 0), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.assertTrue(feature.place(context(level, generator, seed, origin)),
                "re-driving the feature must restore the lobby");
        helper.assertTrue(level.getBlockState(new BlockPos(1, 65, 0))
                        .getBlock() instanceof DungeonPortalBlock,
                "the restored entrance must hold the portal again");
        helper.assertTrue(DungeonEntrance.ensure(level) != null,
                "ensure must find the restored entrance");
        helper.succeed();
    }

    /**
     * The lobby's doorways (issue #131): lane 0's candidate chunk builds the
     * departure frame only - there is no lane -1 to serve an arrival - and
     * the pair derives both ways with no new math: the doorway leads into
     * layer 1's arrival, and a layer-1 arrival walks back onto that doorway.
     * The #31 back-travel caveat lifts here.
     */
    @GameTest(template = "empty")
    public static void lobby_lane_doorways_pair_both_ways(GameTestHelper helper) {
        var level = helper.getLevel();
        var placement = LaneGrid.placement();
        helper.assertTrue(placement != null, "the lane placement must be read");
        long seed = level.getSeed();
        int minBuild = level.getMinBuildHeight();
        int stride = LaneGrid.laneStride();

        var cell = placement.getPotentialStructureChunk(seed, 0, 0);
        int lane = LaneGeometry.laneIndex(cell.getMiddleBlockX(), stride);
        helper.assertTrue(lane == 0, "the probe cell must sit in the lobby lane: " + lane);

        // The doorway stands on the world floor plane, where the flat harness
        // world has ground - clear the footprint first (absolute positions).
        var departure = LaneTransitions.departureInterior(minBuild, cell);
        clearFootprint(level, departure);
        var origin = new BlockPos(cell.x << 4, minBuild + 1, cell.z << 4);
        var feature = TransitionFramesFeature.TRANSITION_FRAMES.get();
        helper.assertTrue(feature.place(context(level, level.getChunkSource().getGenerator(), seed, origin)),
                "the lobby lane cell must get its doorway");
        helper.assertTrue(level.getBlockState(departure).isAir(),
                "the doorway interior must stay unlit");
        helper.assertTrue(level.getBlockState(departure.offset(-1, 0, 0)).is(Blocks.STONE_BRICKS),
                "the doorway ring must be built of the frame block");

        // Lane 0 builds no arrival: the floor schedule refuses layer 0
        // outright, so the lane-0 arrival slot is uncomputable - the
        // invariant in its strongest form, and the coupling this test pins:
        // were the refusal ever relaxed, this fails and forces the frames
        // feature's lane-0 branch back under review. (JUnit covers the
        // refusal itself.)
        boolean layer0Refused = false;
        try {
            FloorSchedule.floorsInLayer(seed, 0,
                    DungeonConfigs.floorsPerLayerMin(), DungeonConfigs.floorsPerLayerMax());
        } catch (IllegalArgumentException ex) {
            layer0Refused = true;
        }
        helper.assertTrue(layer0Refused,
                "the floor schedule must refuse layer 0 - no lane-0 arrival can exist");

        // Forward: the doorway leads into layer 1's arrival.
        var forward = LaneTransitions.pairTarget(level, departure);
        helper.assertTrue(forward.isPresent(), "the doorway must pair into layer 1");
        helper.assertTrue(LaneGeometry.laneIndex(forward.get().getX(), stride) == 1,
                "the doorway's target must sit in layer 1");

        // Back: layer 1's arrival walks back onto this doorway.
        var lane1Cell = placement.getPotentialStructureChunk(seed, placement.spacing(), cell.z);
        int lane1Floors = FloorSchedule.floorsInLayer(seed, 1,
                DungeonConfigs.floorsPerLayerMin(), DungeonConfigs.floorsPerLayerMax());
        var lane1Arrival = LaneTransitions.arrivalInterior(minBuild, level.getHeight(), lane1Floors, lane1Cell);
        var back = LaneTransitions.pairTarget(level, lane1Arrival);
        helper.assertTrue(back.isPresent(), "a layer-1 arrival must pair back");
        helper.assertTrue(back.get().equals(departure),
                "the layer-1 arrival must land on the lobby doorway: " + back.get());

        // End to end: an open walk through the doorway teleports into layer
        // 1 - lane 0 has no guardian, so its doorways are never sealed.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(departure.getX() + 0.5, departure.getY() + 0.5, departure.getZ() + 0.5);
        DungeonPortalTravel.laneHop(level, departure, player);
        helper.assertTrue(player.blockPosition().equals(forward.get()),
                "the doorway walk must land in layer 1: " + player.blockPosition());
        helper.succeed();
    }
    /**
     * The ambient magicule wiring (issue #201): the provider registers at mod
     * construction, and outside the dungeon dimension it declines - so the
     * harness level (which can never BE the dungeon dimension) reads the
     * service's documented cold value. The dungeon-side answer is pinned by
     * DungeonMagiculeProviderTest's composition suite; the dimension gate
     * itself cannot run here, the known harness limitation.
     */
    @GameTest(template = "empty")
    public static void ambient_magicule_service_reads_cold(GameTestHelper helper) {
        helper.assertTrue(AmbientMagicule.providers().contains(DungeonMagiculeProvider.PROVIDER),
                "the dungeon provider must be registered at mod construction");
        double q = AmbientMagicule.query(helper.getLevel(), new BlockPos(0, 64, 0));
        helper.assertTrue(q == AmbientMagicule.COLD,
                "outside the dungeon the service must answer COLD, got " + q);
        helper.succeed();
    }
}
